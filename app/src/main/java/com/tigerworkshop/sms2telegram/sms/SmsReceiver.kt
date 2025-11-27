package com.tigerworkshop.sms2telegram.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.tigerworkshop.sms2telegram.data.SettingsRepository
import com.tigerworkshop.sms2telegram.data.TelegramForwarder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val repository = SettingsRepository(appContext)
        val timeFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ssZ")

        if (!repository.isForwardingEnabled()) {
            repository.saveLastForwardStatus("${timeFormatter.format(Date())}: Forwarding disabled, SMS ignored")
            pendingResult.finish()
            return
        }

        val settings = repository.loadSettings()

        if (settings == null) {
            repository.saveLastForwardStatus("${timeFormatter.format(Date())}: Incomplete settings: Missing API token or Chat ID")
            pendingResult.finish()
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent).orEmpty()
        if (messages.isEmpty()) {
            repository.saveLastForwardStatus("No SMS payload detected.")
            pendingResult.finish()
            return
        }

        val sender = messages.firstOrNull()?.displayOriginatingAddress ?: "Unknown"
        val body = messages.joinToString(separator = "\n") { it.displayMessageBody ?: "" }
        val formattedMessage = buildString {
            appendLine("From: $sender")
            appendLine("Time: ${timeFormatter.format(Date())}")
            appendLine()
            append(body)
        }

        repository.saveLastForwardStatus("${timeFormatter.format(Date())}  from $sender - Forwarding…")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = TelegramForwarder().sendMessage(
                    token = settings.apiToken,
                    chatId = settings.chatId,
                    message = formattedMessage
                )

                if (result.isSuccess) {
                    repository.saveLastForwardStatus("${timeFormatter.format(Date())} - From $sender - Success")
                } else {
                    repository.saveLastForwardStatus(
                        "${timeFormatter.format(Date())} from $sender - Failed: ${result.exceptionOrNull()?.localizedMessage ?: "Unknown Error"}"
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
