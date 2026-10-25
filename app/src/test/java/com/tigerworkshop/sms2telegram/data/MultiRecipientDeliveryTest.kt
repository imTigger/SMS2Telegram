package com.tigerworkshop.sms2telegram.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.runBlocking
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MultiRecipientDeliveryTest {
    private lateinit var context: Context
    private lateinit var repository: SettingsRepository
    private lateinit var outbox: PendingMessageOutbox
    private val calls = mutableListOf<String>()

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("sms_forwarder_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        repository = SettingsRepository(context)
        repository.saveSettings("fixture-not-a-real-token", "111,222")
        outbox = PendingMessageOutbox(context)
        outbox.clear()
    }

    // Intercept every HTTP call locally: no requests reach Telegram.
    private fun forwarder(status: (String) -> Int): TelegramForwarder = TelegramForwarder(
        OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val form = request.body as FormBody
            val chatId = (0 until form.size).first { form.name(it) == "chat_id" }.let { form.value(it) }
            calls.add(chatId)
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(status(chatId)).message("test response")
                .body("{}".toResponseBody()).build()
        }.build()
    )

    private suspend fun deliver(status: (String) -> Int): ListenableWorker.Result {
        val worker = TestListenableWorkerBuilder<TelegramDeliveryWorker>(context).build()
        worker.telegramForwarder = forwarder(status)
        return worker.doWork()
    }

    @Test fun settingsPersistMultipleIdsAndMigrateSingleId() {
        assertEquals(listOf("111", "222"), SettingsRepository(context).loadSettings()!!.chatIds)
        context.getSharedPreferences("sms_forwarder_prefs", Context.MODE_PRIVATE).edit().putString("chat_id", "333").commit()
        assertEquals(listOf("333"), SettingsRepository(context).loadSettings()!!.chatIds)
    }

    @Test fun sendsEachAllowedMessageToEveryRecipient() = runBlocking {
        outbox.enqueue("ExampleSender", "TEST", chatIds = listOf("111", "222"))
        assertEquals(ListenableWorker.Result.success(), deliver { 200 })
        assertEquals(listOf("111", "222"), calls)
        assertEquals(0, outbox.pendingCount())
    }

    @Test fun partialFailureRetriesOnlyTheRemainingRecipient() = runBlocking {
        outbox.enqueue("ExampleSender", "TEST", chatIds = listOf("111", "222"))
        assertEquals(ListenableWorker.Result.retry(), deliver { if (it == "222") 500 else 200 })
        assertEquals(listOf("222"), outbox.peekOldest()!!.pendingChatIds)
        assertEquals(ListenableWorker.Result.success(), deliver { 200 })
        assertEquals(listOf("111", "222", "222"), calls)
        assertEquals(0, outbox.pendingCount())
    }

    @Test fun permanentFailureDoesNotBlockOtherRecipients() = runBlocking {
        outbox.enqueue("ExampleSender", "TEST", chatIds = listOf("111", "222"))
        assertEquals(ListenableWorker.Result.success(), deliver { if (it == "111") 403 else 200 })
        assertEquals(listOf("111", "222"), calls)
        assertEquals(listOf("111"), outbox.peekOldest()!!.pendingChatIds)
    }

    @Test fun removedRecipientsAreSkippedAndNewRecipientsDoNotReceiveOldSms() = runBlocking {
        outbox.enqueue("ExampleSender", "TEST", chatIds = listOf("111", "222"))
        repository.saveSettings("fixture-not-a-real-token", "111,333")
        deliver { 200 }
        assertEquals(listOf("111"), calls)
        assertEquals(0, outbox.pendingCount())
    }

    @Test fun legacyUnboundQueueEntriesUseCurrentRecipients() = runBlocking {
        outbox.enqueue("ExampleSender", "TEST")
        deliver { 200 }
        assertEquals(listOf("111", "222"), calls)
    }

    @Test fun disablingForwardingStopsTheNextRecipient() = runBlocking {
        outbox.enqueue("ExampleSender", "TEST", chatIds = listOf("111", "222"))
        deliver { repository.setForwardingEnabled(false); 200 }
        assertEquals(listOf("111"), calls)
        assertEquals(listOf("222"), outbox.peekOldest()!!.pendingChatIds)
    }

    @Test fun manualTestReportsPartialFailureButStillTriesEveryChat() = runBlocking {
        val result = forwarder { if (it == "111") 403 else 200 }
            .sendToChats("fixture-not-a-real-token", listOf("111", "222"), "TEST")
        assertTrue(result.isFailure)
        assertEquals(listOf("111", "222"), calls)
    }

    @Test fun senderFilterStillBlocksAllRecipients() = runBlocking {
        repository.saveSenderAllowlist(SenderAllowlist(true, setOf("Allowed")))
        outbox.enqueue("Other", "TEST", chatIds = listOf("111", "222"))
        deliver { 200 }
        assertTrue(calls.isEmpty())
        assertEquals(0, outbox.pendingCount())
    }
    @Test fun addingRecipientsDoesNotShareLegacyQueuedSmsWithThem() = runBlocking {
        repository.saveSettings("fixture-not-a-real-token", "111")
        outbox.enqueue("ExampleSender", "LEGACY TEST")
        repository.saveSettings("fixture-not-a-real-token", "111,222")
        assertEquals(listOf("111"), outbox.peekOldest()!!.pendingChatIds)
        deliver { 200 }
        assertEquals(listOf("111"), calls)
    }

}
