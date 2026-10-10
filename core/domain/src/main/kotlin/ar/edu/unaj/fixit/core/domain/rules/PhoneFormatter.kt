package ar.edu.unaj.fixit.core.domain.rules

object PhoneFormatter {
    private const val ARGENTINA_CODE = "54"
    private const val ARGENTINA_MOBILE_PREFIX = "549"

    /**
     * Digits for a WhatsApp link. Trims and removes spaces, hyphens and parentheses first, then checks
     * for a leading '+' and keeps only digits. With '+' the digits are used as they are; without it,
     * a leading 54 is kept and anything else is assumed to be an Argentine mobile (549 prefix).
     * Known limitation: landlines, foreign numbers without '+' and 0xx 15 trunk formats yield invalid links.
     */
    fun whatsAppDigits(raw: String): String {
        val cleaned = raw.trim().filterNot { it == ' ' || it == '-' || it == '(' || it == ')' }
        val digits = cleaned.filter { it in '0'..'9' }
        return when {
            cleaned.startsWith('+') -> digits
            digits.startsWith(ARGENTINA_CODE) -> digits
            else -> ARGENTINA_MOBILE_PREFIX + digits
        }
    }

    /** `https://wa.me/<digits>`; never includes `?text` (the UI may append it when opening the intent). */
    fun whatsAppUrl(raw: String): String = "https://wa.me/" + whatsAppDigits(raw)
}
