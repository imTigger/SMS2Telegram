package com.tigerworkshop.sms2telegram.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class TelegramDeliveryWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val settingsRepository = SettingsRepository(appContext)
    private val outbox = PendingMessageOutbox(appContext)
    internal var telegramForwarder = TelegramForwarder()
    private val timeFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ssZ", Locale.US)

    override suspend fun doWork(): Result {
        if (!settingsRepository.isForwardingEnabled() || settingsRepository.loadSettings() == null) {
            saveStatus("${timestamp()} - Forwarding paused or Telegram not configured. ${outbox.pendingCount()} pending message(s).")
            return Result.success()
        }
        var retryNeeded = false
        var delivered = 0
        val failedChats = mutableSetOf<String>()
        val processed = mutableSetOf<String>()
        while (true) {
            val pending = outbox.listAll().firstOrNull { it.id !in processed } ?: break
            processed.add(pending.id)
            if (!settingsRepository.loadSenderAllowlist().allows(pending.sender)) {
                outbox.remove(pending.id)
                continue
            }
            val settings = settingsRepository.loadSettings() ?: return Result.success()
            // New SMS snapshot their recipients when received. Old queue entries bind here.
            // Removing a recipient cancels their pending deliveries; adding one does not
            // retroactively share messages that already have a recipient snapshot.
            val remaining = (pending.pendingChatIds ?: settings.chatIds)
                .filter { it in settings.chatIds }.distinct().toMutableList()
            outbox.setPendingChatIds(pending.id, remaining)
            for (chatId in remaining.toList()) {
                if (!settingsRepository.isForwardingEnabled()) return Result.success()
                if (!settingsRepository.loadSenderAllowlist().allows(pending.sender)) {
                    outbox.remove(pending.id)
                    break
                }
                val current = settingsRepository.loadSettings() ?: return Result.success()
                if (chatId !in current.chatIds) {
                    remaining.remove(chatId)
                    outbox.setPendingChatIds(pending.id, remaining)
                    continue
                }
                val result = telegramForwarder.sendMessage(current.apiToken, chatId, pending.message)
                if (result.isSuccess) {
                    delivered++
                    remaining.remove(chatId)
                    outbox.setPendingChatIds(pending.id, remaining)
                } else {
                    failedChats.add(chatId)
                    retryNeeded = retryNeeded || TelegramForwarder.shouldRetry(result.exceptionOrNull())
                }
            }
        }
        val failures = if (failedChats.isEmpty()) "" else " Failed chat(s): ${failedChats.joinToString()}."
        saveStatus("${timestamp()} - $delivered delivery(s) completed. ${outbox.pendingCount()} pending message(s).$failures")
        return if (retryNeeded) Result.retry() else Result.success()
    }

    private fun saveStatus(status: String) {
        settingsRepository.saveLastForwardStatus(status)
        notifyStatusUpdated()
    }

    private fun notifyStatusUpdated() {
        StatusUpdateBus.notifyUpdated()
    }

    private fun timestamp(): String = timeFormatter.format(Date())

    companion object {
        private const val UNIQUE_WORK_NAME = "telegram_delivery"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<TelegramDeliveryWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    10,
                    TimeUnit.SECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }
}
