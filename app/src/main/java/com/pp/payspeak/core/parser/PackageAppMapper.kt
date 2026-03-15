package com.pp.payspeak.core.parser

import com.pp.payspeak.model.PaymentApp

object PackageAppMapper {
    private val map: List<Pair<String, PaymentApp>> = listOf(
        "net.one97.paytm" to PaymentApp.PAYTM,
        "com.paytm" to PaymentApp.PAYTM,
        "com.google.android.apps.nbu.paisa.user" to PaymentApp.GOOGLE_PAY,
        "com.phonepe.app" to PaymentApp.PHONEPE,
        "in.org.npci.upiapp" to PaymentApp.BHIM,
        "money.super.payments" to PaymentApp.SUPER_MONEY,
        "com.dreamplug.androidapp" to PaymentApp.CRED,
        "in.amazon.mShop.android.shopping" to PaymentApp.AMAZON_PAY,
        "money.jupiter" to PaymentApp.JUPITER,
        "com.epifi.paisa" to PaymentApp.FI_MONEY,
        "in.gokiwi.kiwitpap" to PaymentApp.KIWI,
        "indwin.c3.shareapp" to PaymentApp.SLICE,
        "com.tatadigital.tcp" to PaymentApp.TATA_NEU,
        "com.mobikwik_new" to PaymentApp.MOBIKWIK,
        "com.myairtelapp" to PaymentApp.AIRTEL_THANKS,
        "com.whatsapp" to PaymentApp.WHATSAPP,
        "com.sbi.upi" to PaymentApp.SBI_PAY,
        "com.snapwork.hdfc" to PaymentApp.HDFC_BANK,
        "com.csam.icici.bank.imobile" to PaymentApp.ICICI_IMOBILE,
        "com.upi.axispay" to PaymentApp.AXIS_PAY,
        "com.msf.kbank.mobile" to PaymentApp.KOTAK_BANK
    )

    fun mapPackage(packageName: String): PaymentApp {
        return map.firstOrNull { packageName.startsWith(it.first) }?.second ?: PaymentApp.UNKNOWN
    }

    fun isSupported(packageName: String): Boolean = mapPackage(packageName) != PaymentApp.UNKNOWN
}

