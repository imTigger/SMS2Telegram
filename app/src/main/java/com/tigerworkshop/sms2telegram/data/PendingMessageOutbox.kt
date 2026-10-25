package com.tigerworkshop.sms2telegram.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class PendingMessageOutbox(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun enqueue(sender: String, message: String, queuedAtMillis: Long = System.currentTimeMillis(), chatIds: List<String>? = null): PendingMessage {
        val pendingMessage = PendingMessage(
            id = UUID.randomUUID().toString(),
            sender = sender,
            message = message,
            queuedAtMillis = queuedAtMillis,
            pendingChatIds = chatIds?.distinct()
        )

        synchronized(lock) {
            val queue = loadQueueLocked()
            queue.put(pendingMessage.toJson())
            saveQueueLocked(queue)
        }

        return pendingMessage
    }

    fun peekOldest(): PendingMessage? = synchronized(lock) {
        val queue = loadQueueLocked()
        for (index in 0 until queue.length()) {
            val item = queue.optJSONObject(index) ?: continue
            return@synchronized item.toPendingMessage()
        }
        null
    }

    fun remove(messageId: String): Boolean = synchronized(lock) {
        val queue = loadQueueLocked()
        val updatedQueue = JSONArray()
        var removed = false

        for (index in 0 until queue.length()) {
            val item = queue.optJSONObject(index) ?: continue
            if (!removed && item.optString(KEY_ID) == messageId) {
                removed = true
                continue
            }
            updatedQueue.put(item)
        }

        if (removed) {
            saveQueueLocked(updatedQueue)
        }
        removed
    }

    /** Atomically record remaining recipients, removing a fully delivered message. */
    fun setPendingChatIds(messageId: String, chatIds: List<String>) = synchronized(lock) {
        val queue = loadQueueLocked()
        val updatedQueue = JSONArray()
        for (index in 0 until queue.length()) {
            val item = queue.optJSONObject(index) ?: continue
            if (item.optString(KEY_ID) == messageId) {
                if (chatIds.isEmpty()) continue
                item.put(KEY_PENDING_CHAT_IDS, JSONArray(chatIds.distinct()))
            }
            updatedQueue.put(item)
        }
        saveQueueLocked(updatedQueue)
    }

    fun pendingCount(): Int = synchronized(lock) {
        loadQueueLocked().length()
    }

    fun listAll(): List<PendingMessage> = synchronized(lock) {
        val queue = loadQueueLocked()
        buildList {
            for (index in 0 until queue.length()) {
                val item = queue.optJSONObject(index) ?: continue
                add(item.toPendingMessage())
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            saveQueueLocked(JSONArray())
        }
    }

    private fun loadQueueLocked(): JSONArray {
        val raw = prefs.getString(KEY_PENDING_MESSAGES, null).orEmpty()
        if (raw.isBlank()) {
            return JSONArray()
        }

        return try {
            JSONArray(raw)
        } catch (_: Exception) {
            JSONArray()
        }
    }

    private fun saveQueueLocked(queue: JSONArray) {
        prefs.edit(commit = true) {
            putString(KEY_PENDING_MESSAGES, queue.toString())
        }
    }

    private fun PendingMessage.toJson(): JSONObject {
        return JSONObject()
            .put(KEY_ID, id)
            .put(KEY_SENDER, sender)
            .put(KEY_MESSAGE, message)
            .put(KEY_QUEUED_AT, queuedAtMillis)
            .apply { pendingChatIds?.let { put(KEY_PENDING_CHAT_IDS, JSONArray(it)) } }
    }

    private fun JSONObject.toPendingMessage(): PendingMessage {
        return PendingMessage(
            id = optString(KEY_ID),
            sender = optString(KEY_SENDER, "Unknown"),
            message = optString(KEY_MESSAGE),
            queuedAtMillis = optLong(KEY_QUEUED_AT),
            pendingChatIds = optJSONArray(KEY_PENDING_CHAT_IDS)?.let { ids ->
                (0 until ids.length()).map { ids.getString(it) }
            }
        )
    }

    companion object {
        private const val PREFS_NAME = "sms_forwarder_outbox"
        private const val KEY_PENDING_MESSAGES = "pending_messages"
        private const val KEY_ID = "id"
        private const val KEY_SENDER = "sender"
        private const val KEY_MESSAGE = "message"
        private const val KEY_PENDING_CHAT_IDS = "pending_chat_ids"
        private const val KEY_QUEUED_AT = "queued_at"

        private val lock = Any()
    }
}

data class PendingMessage(
    val id: String,
    val sender: String,
    val message: String,
    val queuedAtMillis: Long,
    val pendingChatIds: List<String>? = null
)
