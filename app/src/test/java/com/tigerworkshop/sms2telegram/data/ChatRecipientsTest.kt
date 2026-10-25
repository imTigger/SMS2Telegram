package com.tigerworkshop.sms2telegram.data

import org.junit.Assert.*
import org.junit.Test

class ChatRecipientsTest {
    @Test fun acceptsCommasNewlinesSpacesAndSemicolons() {
        assertEquals(listOf("123", "456", "-100789", "321"), ChatRecipients.parse("123,456\n-100789; 321"))
    }
    @Test fun removesDuplicatesAndNormalizesNumericIds() {
        assertEquals(listOf("123", "-456"), ChatRecipients.parse("00123,123,+123,-456,-0456"))
    }
    @Test fun blankListIsEmptyAndExistingSingleIdStillWorks() {
        assertTrue(ChatRecipients.parse("  ").isEmpty())
        assertEquals(listOf("123"), ChatRecipients.parse("123"))
    }
    @Test fun supportsExistingChannelUsernames() {
        assertEquals(listOf("@example_channel"), ChatRecipients.parse("@Example_Channel,@example_channel"))
    }
    @Test fun invalidIdsAreRejectedBeforeSending() {
        for (invalid in listOf("123,invalid", "0", "123.4", "999999999999999999999999999")) {
            assertThrows(IllegalArgumentException::class.java) { ChatRecipients.parse(invalid) }
        }
    }
    @Test fun addingAFoundChatKeepsExistingIdsWithoutDuplicates() {
        assertEquals("123\n456", ChatRecipients.append("123", "456"))
        assertEquals("123\n456", ChatRecipients.append("123,456", "123"))
    }
}
