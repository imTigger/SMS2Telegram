package com.tigerworkshop.sms2telegram.data

/** Matches complete SMS originating addresses; message bodies are never checked. */
data class SenderAllowlist(val enabled: Boolean = false, val senders: Set<String> = emptySet()) {
    fun allows(sender: String?): Boolean {
        if (!enabled) return true
        val address = sender?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        return senders.any { it.trim().equals(address, ignoreCase = true) }
    }

    companion object {
        fun clean(senders: Collection<String>): Set<String> = senders.map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase(java.util.Locale.ROOT) }
            .toSet()
    }
}
