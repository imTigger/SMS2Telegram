package com.tigerworkshop.sms2telegram.data

/** Telegram destinations separated by commas, semicolons, spaces, or newlines. */
object ChatRecipients {
    fun parse(raw: String): List<String> = raw.trim().split(Regex("[,;\\s]+"))
        .filter { it.isNotBlank() }.map { value ->
            val number = value.toLongOrNull()
            when {
                number != null && number != 0L -> number.toString()
                value.matches(Regex("@[A-Za-z][A-Za-z0-9_]{4,31}")) -> value.lowercase(java.util.Locale.ROOT)
                else -> throw IllegalArgumentException("Use valid numeric chat IDs or @channel usernames, separated by commas or new lines.")
            }
        }.distinct()

    fun append(raw: String, chatId: String): String = try {
        (parse(raw) + parse(chatId)).distinct().joinToString("\n")
    } catch (_: IllegalArgumentException) {
        raw.trim() + "\n" + chatId
    }
}
