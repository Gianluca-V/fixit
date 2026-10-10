package ar.edu.unaj.fixit.core.domain.rules

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneFormatterTest {
    @Test
    fun `given a local Argentine number when normalizing then 549 is prepended`() {
        assertEquals("5491155551234", PhoneFormatter.whatsAppDigits("11 5555-1234"))
    }

    @Test
    fun `given an international number with plus and separators when normalizing then its digits are kept as they are`() {
        assertEquals("5492215551234", PhoneFormatter.whatsAppDigits("+54 9 221 555 1234"))
    }

    @Test
    fun `given an already normalized number when normalizing then it only loses the plus`() {
        assertEquals("5491155551234", PhoneFormatter.whatsAppDigits("+5491155551234"))
    }

    @Test
    fun `given spaces and parentheses before the plus when normalizing then the plus is still detected`() {
        assertEquals("5491155551234", PhoneFormatter.whatsAppDigits(" (+54) 9 11 5555-1234 "))
    }

    @Test
    fun `given a foreign number with plus when normalizing then no Argentine prefix is added`() {
        assertEquals("14155552671", PhoneFormatter.whatsAppDigits("+1 (415) 555-2671"))
    }

    @Test
    fun `given a number that already starts with 54 and no plus when normalizing then no second prefix is added`() {
        assertEquals("5491155554444", PhoneFormatter.whatsAppDigits("5491155554444"))
    }

    @Test
    fun `given a local number when building the WhatsApp url then it is the wa_me link without text parameter`() {
        val url = PhoneFormatter.whatsAppUrl("11 5555-1234")

        assertEquals("https://wa.me/5491155551234", url)
    }

    @Test
    fun `given a number that already starts with 54 when building the WhatsApp url then the link has a single prefix`() {
        assertEquals("https://wa.me/5491155554444", PhoneFormatter.whatsAppUrl("5491155554444"))
    }
}
