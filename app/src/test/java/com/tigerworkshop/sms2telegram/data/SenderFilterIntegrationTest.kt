package com.tigerworkshop.sms2telegram.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SenderFilterIntegrationTest {
    private lateinit var context: Context
    private lateinit var repository: SettingsRepository
    private lateinit var outbox: PendingMessageOutbox
    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("sms_forwarder_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        repository = SettingsRepository(context)
        outbox = PendingMessageOutbox(context)
        outbox.clear()
    }
    @Test fun settingsPersistAddRemoveAndEnabledEmptyList() {
        assertFalse(repository.loadSenderAllowlist().enabled)
        repository.saveSenderAllowlist(SenderAllowlist(true, setOf(" ExampleService ", "exampleservice", "12345")))
        val reloaded = SettingsRepository(context).loadSenderAllowlist()
        assertEquals(setOf("ExampleService", "12345"), reloaded.senders)
        assertTrue(reloaded.enabled)
        repository.saveSenderAllowlist(reloaded.copy(senders = emptySet()))
        assertFalse(SettingsRepository(context).loadSenderAllowlist().allows("ExampleService"))
    }
    @Test fun removedSendersCannotEscapeViaOfflineQueue() = runBlocking {
        repository.saveSettings("test-token-never-sent", "123")
        repository.saveSenderAllowlist(SenderAllowlist(true, setOf("ExampleService")))
        outbox.enqueue("OtherSender", "TEST BLOCKED")
        outbox.enqueue("FormerSender", "TEST BLOCKED")
        val result = TestListenableWorkerBuilder<TelegramDeliveryWorker>(context).build().doWork()
        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(0, outbox.pendingCount())
    }
    @Test fun removingLastSenderDropsPreviouslyQueuedMessages() = runBlocking {
        repository.saveSettings("test-token-never-sent", "123")
        repository.saveSenderAllowlist(SenderAllowlist(true, setOf("ExampleService")))
        outbox.enqueue("ExampleService", "TEST BLOCKED AFTER REMOVAL")
        repository.saveSenderAllowlist(SenderAllowlist(true))
        val result = TestListenableWorkerBuilder<TelegramDeliveryWorker>(context).build().doWork()
        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(0, outbox.pendingCount())
    }
}
