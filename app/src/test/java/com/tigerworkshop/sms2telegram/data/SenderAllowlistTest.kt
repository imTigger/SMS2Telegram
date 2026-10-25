package com.tigerworkshop.sms2telegram.data

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class SenderAllowlistTest {
    @Test fun disabledFilterPreservesExistingBehavior() {
        assertTrue(SenderAllowlist().allows("OtherSender"))
        assertTrue(SenderAllowlist().allows(null))
    }
    @Test fun enabledEmptyListBlocksEverything() {
        assertFalse(SenderAllowlist(true).allows("ExampleService"))
    }
    @Test fun namesMatchExactlyIgnoringCaseAndOuterWhitespace() {
        val list = SenderAllowlist(true, setOf(" ExampleService "))
        assertTrue(list.allows("ExampleService"))
        assertTrue(list.allows(" exampleservice "))
        assertFalse(list.allows("ExampleService-Fake"))
        assertFalse(list.allows("OtherSender"))
        assertFalse(list.allows("Your ExampleService OTP is TEST"))
    }
    @Test fun missingAndBlankAddressesAreBlocked() {
        val list = SenderAllowlist(true, setOf("ExampleService", ""))
        assertFalse(list.allows(null))
        assertFalse(list.allows(" "))
    }
    @Test fun phoneNumbersRequireTheFullAddress() {
        val list = SenderAllowlist(true, setOf("+12025550123", "12345"))
        assertTrue(list.allows("+12025550123"))
        assertTrue(list.allows("12345"))
        assertFalse(list.allows("2025550123"))
        assertFalse(list.allows("+120255501234"))
    }
    @Test fun wildcardsAreNotInterpreted() {
        assertFalse(SenderAllowlist(true, setOf("Example*")).allows("ExampleService"))
    }
    @Test fun cleaningTrimsDropsBlanksAndDeduplicates() {
        assertEquals(setOf("ExampleService", "12345"), SenderAllowlist.clean(listOf(" ExampleService ", "exampleservice", "", " ", "12345")))
    }
    @Test fun matchingIsIndependentOfDeviceLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertTrue(SenderAllowlist(true, setOf("EXAMPLESERVICE")).allows("exampleservice"))
            assertEquals(1, SenderAllowlist.clean(listOf("EXAMPLESERVICE", "exampleservice")).size)
        } finally { Locale.setDefault(previous) }
    }
}
