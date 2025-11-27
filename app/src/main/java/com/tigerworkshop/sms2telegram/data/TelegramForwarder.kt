package com.tigerworkshop.sms2telegram.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

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
