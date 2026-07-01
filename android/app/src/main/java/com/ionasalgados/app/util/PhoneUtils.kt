package com.ionasalgados.app.util

object PhoneUtils {
    fun digitsOnly(value: String) = value.replace(Regex("\\D"), "")

    fun forDisplay(phone: String): String {
        val digits = digitsOnly(phone)
        return if (digits.length >= 12 && digits.startsWith("55")) digits.drop(2) else digits
    }

    fun formatInput(value: String) = digitsOnly(value).take(11)

    fun formatMasked(digits: String): String {
        val d = digitsOnly(digits).take(11)
        return when {
            d.length <= 2 -> d
            d.length <= 6 -> "(${d.take(2)}) ${d.drop(2)}"
            d.length <= 10 -> "(${d.take(2)}) ${d.drop(2).take(4)}-${d.drop(6)}"
            else -> "(${d.take(2)}) ${d.drop(2).take(5)}-${d.drop(7)}"
        }
    }

    /** DDD + número → formato internacional com 55 para o WhatsApp. */
    fun forWhatsAppApi(phone: String): String? {
        var digits = digitsOnly(phone)
        if (digits.length in 10..11 && !digits.startsWith("55")) {
            digits = "55$digits"
        }
        return if (digits.length in 12..13) digits else null
    }
}
