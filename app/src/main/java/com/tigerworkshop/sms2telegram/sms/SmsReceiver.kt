package com.tigerworkshop.sms2telegram.sms

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.tigerworkshop.sms2telegram.data.SettingsRepository
import com.tigerworkshop.sms2telegram.data.TelegramForwarder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date


class SmsReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_STATUS_UPDATED = "com.tigerworkshop.sms2telegram.ACTION_STATUS_UPDATED"
    }

    private fun getSimCarrierName(context: Context, repository: SettingsRepository, slotIndex: Int): String? {
        // Check if user has enabled "Show SIM Name" feature
        if (!repository.isShowSimNameEnabled()) {
            return null
        }

        // Check if we have READ_PHONE_STATE permission
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }

        // Only available on Android 5.1 (API 22) and above
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            return null
        }

        try {
            val subscriptionManager = getSystemService(context, SubscriptionManager::class.java)
            if (subscriptionManager != null) {
                val subscriptionInfoList = subscriptionManager.activeSubscriptionInfoList
                if (subscriptionInfoList != null) {
                    for (subscriptionInfo in subscriptionInfoList) {
                        if (subscriptionInfo.simSlotIndex == slotIndex) {
                            val carrierName = subscriptionInfo.carrierName?.toString()
                            if (!carrierName.isNullOrBlank()) {
                                return carrierName
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SmsReceiver", "Error getting SIM carrier name", e)
        }

        return null
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val repository = SettingsRepository(appContext)
        val timeFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ssZ")

        fun notifyStatusUpdated() {
            LocalBroadcastManager.getInstance(appContext)
                .sendBroadcast(Intent(ACTION_STATUS_UPDATED))
        }

        if (!repository.isForwardingEnabled()) {
            repository.saveLastForwardStatus("${timeFormatter.format(Date())}: Forwarding disabled, SMS ignored")
            notifyStatusUpdated()
            pendingResult.finish()
            return
        }

        val settings = repository.loadSettings()

        if (settings == null) {
            repository.saveLastForwardStatus("${timeFormatter.format(Date())}: Incomplete settings: Missing API token or Chat ID")
            notifyStatusUpdated()
            pendingResult.finish()
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent).orEmpty()
        if (messages.isEmpty()) {
            repository.saveLastForwardStatus("No SMS payload detected.")
            notifyStatusUpdated()
            pendingResult.finish()
            return
        }

        val simSlotIndex = intent.getExtras()!!.getInt("android.telephony.extra.SLOT_INDEX", -1)
        val simCarrierName = getSimCarrierName(appContext, repository, simSlotIndex)

        val sender = messages.firstOrNull()?.displayOriginatingAddress ?: "Unknown"
        val body = messages.joinToString(separator = "\n") { it.displayMessageBody ?: "" }
        val formattedMessage = buildString {
            appendLine("From: $sender")
            if (simCarrierName != null) {
                appendLine("SIM: #$simSlotIndex - $simCarrierName")
            } else {
                appendLine("SIM: #$simSlotIndex")
            }
            appendLine("Time: ${timeFormatter.format(Date())}")
            appendLine()
            append(body)
        }

        repository.saveLastForwardStatus("${timeFormatter.format(Date())}  from $sender - Forwarding…")
        notifyStatusUpdated()

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
                notifyStatusUpdated()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
