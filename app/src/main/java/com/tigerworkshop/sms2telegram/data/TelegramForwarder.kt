package com.tigerworkshop.sms2telegram.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import org.json.JSONObject

class TelegramForwarder(
    private val client: OkHttpClient = defaultClient
) {

    suspend fun sendMessage(
        token: String,
        chatId: String,
        message: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val safeMessage = message.take(MAX_MESSAGE_LENGTH)
        val url = "https://api.telegram.org/bot${token}/sendMessage"
        val body = FormBody.Builder()
            .add("chat_id", chatId)
            .add("text", safeMessage)
            .build()

        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        return@withContext try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(IOException("Telegram API error ${response.code}"))
                }
            }
        } catch (ioe: IOException) {
            Result.failure(ioe)
        }
    }

    suspend fun fetchUpdates(token: String): Result<List<TelegramChatInfo>> = withContext(Dispatchers.IO) {
        val url = "https://api.telegram.org/bot${token}/getUpdates"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        return@withContext try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use Result.failure(IOException("Telegram API error ${response.code}"))
                }

                val bodyString = response.body?.string().orEmpty()
                val json = JSONObject(bodyString)
                if (!json.optBoolean("ok")) {
                    return@use Result.failure(IOException("Telegram API returned ok=false"))
                }
                val results = json.optJSONArray("result") ?: return@use Result.success(emptyList())
                val chats = mutableListOf<TelegramChatInfo>()
                val seenIds = mutableSetOf<Long>()
                for (i in 0 until results.length()) {
                    val update = results.optJSONObject(i) ?: continue
                    val message = update.optJSONObject("message") ?: continue
                    val chat = message.optJSONObject("chat") ?: continue
                    val chatId = chat.optLong("id")
                    if (!seenIds.add(chatId)) continue
                    val firstName = chat.optString("first_name")
                    val username = chat.optString("username")
                    val title = chat.optString("title")
                    chats.add(
                        TelegramChatInfo(
                            id = chatId,
                            firstName = firstName,
                            username = username,
                            title = title
                        )
                    )
                }
                Result.success(chats)
            }
        } catch (ioe: IOException) {
            Result.failure(ioe)
        }
    }

    companion object {
        private const val MAX_MESSAGE_LENGTH = 3900

        private val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .callTimeout(30, TimeUnit.SECONDS)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
        }
    }
}

data class TelegramChatInfo(
    val id: Long,
    val firstName: String?,
    val username: String?,
    val title: String?
)
