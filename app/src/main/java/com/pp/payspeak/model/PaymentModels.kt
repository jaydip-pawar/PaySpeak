package com.pp.payspeak.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class PaymentSource(val priority: Int) {
    NOTIFICATION(1),
    ACCESSIBILITY(2),
    SMS(3),
    UNKNOWN(4)
}

enum class PaymentApp {
    PAYTM,
    GOOGLE_PAY,
    PHONEPE,
    BHIM,
    SUPER_MONEY,
    CRED,
    AMAZON_PAY,
    JUPITER,
    FI_MONEY,
    KIWI,
    SLICE,
    TATA_NEU,
    MOBIKWIK,
    AIRTEL_THANKS,
    WHATSAPP,
    SBI_PAY,
    HDFC_BANK,
    ICICI_IMOBILE,
    AXIS_PAY,
    KOTAK_BANK,
    BANK_SMS,
    UNKNOWN
}

data class PaymentEvent(
    val amount: Long,
    val sender: String,
    val appName: PaymentApp,
    val success: Boolean,
    val source: PaymentSource,
    val confidenceScore: Float,
    val rawText: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun getAmountInRupees(): Double = amount.toDouble() / 100
    fun getAmountAsString(): String = String.format("%.2f", getAmountInRupees())
}

@Entity(tableName = "announced_payments")
data class AnnouncedPayment(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amount: Long,
    val sender: String,
    val appName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val language: String = "en"
)

data class DetectionResult(
    val event: PaymentEvent,
    val detectedAt: Long = System.currentTimeMillis(),
    val shouldAnnounce: Boolean = true
)
