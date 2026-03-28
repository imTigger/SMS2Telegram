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
    private val telegramForwarder = TelegramForwarder()
    private val timeFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ssZ", Locale.US)

    override suspend fun doWork(): Result {
        val pendingCount = outbox.pendingCount()
        if (pendingCount == 0) {
            notifyStatusUpdated()
            return Result.success()
        }

        if (!settingsRepository.isForwardingEnabled()) {
            saveStatus(
                "${timestamp()} - Forwarding disabled. $pendingCount pending message(s) waiting."
            )
            return Result.success()
        }

        val settings = settingsRepository.loadSettings()
        if (settings == null) {
            saveStatus(
                "${timestamp()} - Telegram not configured. $pendingCount pending message(s) waiting."
            )
            return Result.success()
        }

        while (true) {
            val pendingMessage = outbox.peekOldest() ?: break
            val sendResult = telegramForwarder.sendMessage(
                token = settings.apiToken,
                chatId = settings.chatId,
                message = pendingMessage.message
            )

            if (sendResult.isSuccess) {
                outbox.remove(pendingMessage.id)
                val remaining = outbox.pendingCount()
                saveStatus(
                    "${timestamp()} - From ${pendingMessage.sender} - Delivered. $remaining pending message(s)."
                )
                continue
            }

            val exception = sendResult.exceptionOrNull()
            val remaining = outbox.pendingCount()
            val errorMessage = exception?.localizedMessage ?: "Unknown Error"

            if (TelegramForwarder.shouldRetry(exception)) {
                saveStatus(
                    "${timestamp()} - From ${pendingMessage.sender} - Delivery deferred: $errorMessage. $remaining pending message(s)."
                )
                return Result.retry()
            }

            saveStatus(
                "${timestamp()} - From ${pendingMessage.sender} - Delivery paused: $errorMessage. $remaining pending message(s)."
            )
            return Result.success()
        }

        return Result.success()
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
                ExistingWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }
}
