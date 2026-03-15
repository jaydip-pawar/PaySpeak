package com.pp.payspeak.core.language

import android.util.Log
import java.util.Locale

private const val TAG = "LanguageManager"

enum class Language(val code: String, val displayName: String) {
    ENGLISH("en", "English"),
    HINDI("hi", "हिंदी"),
    MARATHI("mr", "मराठी"),
    HINGLISH("hi_en", "Hinglish")
}

interface TextFormatter {
    fun formatPaymentAnnouncement(amountPaise: Long, appName: String): String
    fun formatSmsAnnouncement(amountPaise: Long): String
}

private data class AmountParts(val rupees: Long, val paise: Long)

private fun splitAmount(amountPaise: Long): AmountParts {
    val rupees = amountPaise / 100
    val paise = amountPaise % 100
    return AmountParts(rupees, paise)
}

private fun formatRupeePaise(parts: AmountParts, rupeeLabel: String, paiseLabel: String): String {
    return if (parts.paise == 0L) {
        "$rupeeLabel ${parts.rupees}"
    } else {
        "$rupeeLabel ${parts.rupees} $paiseLabel ${parts.paise}"
    }
}

class EnglishFormatter : TextFormatter {
    override fun formatPaymentAnnouncement(amountPaise: Long, appName: String): String {
        val parts = splitAmount(amountPaise)
        val amountText = if (parts.paise == 0L) "${parts.rupees}" else "${parts.rupees} and ${parts.paise} cents"
        val appLabel = appName.ifBlank { "app" }
        return "Received Rs $amountText on $appLabel"
    }
    override fun formatSmsAnnouncement(amountPaise: Long): String {
        val parts = splitAmount(amountPaise)
        val amountText = if (parts.paise == 0L) "${parts.rupees}" else "${parts.rupees} and ${parts.paise} cents"
        return "Payment of Rs $amountText received"
    }
}

class HindiFormatter : TextFormatter {
    override fun formatPaymentAnnouncement(amountPaise: Long, appName: String): String {
        val parts = splitAmount(amountPaise)
        val amountText = formatRupeePaise(parts, "रुपये", "पैसे")
        val appLabel = appName.ifBlank { "ऐप" }
        return "$appLabel पर $amountText प्राप्त हुए"
    }
    override fun formatSmsAnnouncement(amountPaise: Long): String {
        val parts = splitAmount(amountPaise)
        val amountText = formatRupeePaise(parts, "रुपये", "पैसे")
        return "$amountText प्राप्त हुए"
    }
}

class MarathiFormatter : TextFormatter {
    override fun formatPaymentAnnouncement(amountPaise: Long, appName: String): String {
        val parts = splitAmount(amountPaise)
        val amountText = formatRupeePaise(parts, "रुपये", "पैसे")
        val appLabel = appName.ifBlank { "अ‍ॅप" }
        return "$appLabel वर $amountText प्राप्त झाले"
    }
    override fun formatSmsAnnouncement(amountPaise: Long): String {
        val parts = splitAmount(amountPaise)
        val amountText = formatRupeePaise(parts, "रुपये", "पैसे")
        return "$amountText प्राप्त झाले"
    }
}

class HinglishFormatter : TextFormatter {
    override fun formatPaymentAnnouncement(amountPaise: Long, appName: String): String {
        val parts = splitAmount(amountPaise)
        val amountText = formatRupeePaise(parts, "rupaye", "paise")
        val appLabel = appName.ifBlank { "app" }
        return "$appLabel par $amountText prapt hue"
    }
    override fun formatSmsAnnouncement(amountPaise: Long): String {
        val parts = splitAmount(amountPaise)
        val amountText = formatRupeePaise(parts, "rupaye", "paise")
        return "$amountText receive hue"
    }
}

class LanguageManager {
    private var currentLanguage: Language = Language.ENGLISH
    private var formatter: TextFormatter = EnglishFormatter()

    init { setLanguage(detectSystemLanguage()) }

    fun setLanguage(language: Language) {
        Log.d(TAG, "Setting language to: ${language.displayName}")
        currentLanguage = language
        formatter = when (language) {
            Language.ENGLISH -> EnglishFormatter()
            Language.HINDI -> HindiFormatter()
            Language.MARATHI -> MarathiFormatter()
            Language.HINGLISH -> HinglishFormatter()
        }
    }

    fun getCurrentLanguage(): Language = currentLanguage

    fun formatAnnouncement(amountPaise: Long, appName: String): String {
        return formatter.formatPaymentAnnouncement(amountPaise, appName)
    }

    fun formatSmsAnnouncement(amountPaise: Long): String {
        return formatter.formatSmsAnnouncement(amountPaise)
    }

    fun getAppNameInCurrentLanguage(appName: String): String {
        val upper = appName.uppercase()
        val mapEn = mapOf(
            "PAYTM" to "Paytm",
            "GOOGLE_PAY" to "Google Pay",
            "PHONEPE" to "PhonePe",
            "BHIM" to "BHIM",
            "SUPER_MONEY" to "Super Money",
            "CRED" to "Cred",
            "AMAZON_PAY" to "Amazon Pay",
            "JUPITER" to "Jupiter",
            "FI_MONEY" to "Fi Money",
            "KIWI" to "Kiwi",
            "SLICE" to "Slice",
            "TATA_NEU" to "Tata Neu",
            "MOBIKWIK" to "MobiKwik",
            "AIRTEL_THANKS" to "Airtel Thanks",
            "WHATSAPP" to "WhatsApp",
            "SBI_PAY" to "SBI Pay",
            "HDFC_BANK" to "HDFC Bank",
            "ICICI_IMOBILE" to "ICICI iMobile",
            "AXIS_PAY" to "Axis Pay",
            "KOTAK_BANK" to "Kotak Bank"
        )
        val mapHiMr = mapOf(
            "PAYTM" to "पेटीएम",
            "GOOGLE_PAY" to "गूगल पे",
            "PHONEPE" to "फोन पे",
            "BHIM" to "भीम",
            "SUPER_MONEY" to "सुपर मनी",
            "CRED" to "क्रेड",
            "AMAZON_PAY" to "अमेज़न पे",
            "JUPITER" to "ज्युपिटर",
            "FI_MONEY" to "फाई मनी",
            "KIWI" to "कीवी",
            "SLICE" to "स्लाइस",
            "TATA_NEU" to "टाटा न्यू",
            "MOBIKWIK" to "मोबीक्विक",
            "AIRTEL_THANKS" to "एयरटेल थैंक्स",
            "WHATSAPP" to "व्हाट्सएप",
            "SBI_PAY" to "एसबीआई पे",
            "HDFC_BANK" to "एचडीएफसी बैंक",
            "ICICI_IMOBILE" to "आईसीआईसीआई मोबाइल",
            "AXIS_PAY" to "एक्सिस पे",
            "KOTAK_BANK" to "कोटक बैंक"
        )
        return when (currentLanguage) {
            Language.ENGLISH, Language.HINGLISH -> mapEn[upper] ?: appName
            Language.HINDI, Language.MARATHI -> mapHiMr[upper] ?: appName
        }
    }

    companion object {
        fun detectSystemLanguage(): Language {
            val systemLanguage = Locale.getDefault().language
            return when (systemLanguage) {
                "hi" -> Language.HINDI
                "mr" -> Language.MARATHI
                else -> Language.ENGLISH
            }
        }
    }
}

private fun formatAmount(amountPaise: Long): String {
    val parts = splitAmount(amountPaise)
    return if (parts.paise == 0L) parts.rupees.toString() else "%s.%02d".format(parts.rupees, parts.paise)
}
