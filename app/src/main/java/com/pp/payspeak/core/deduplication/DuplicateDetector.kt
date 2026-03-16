package com.pp.payspeak.core.deduplication

import android.util.Log
import com.pp.payspeak.database.PaySpeakDatabase
import com.pp.payspeak.model.AnnouncedPayment
import com.pp.payspeak.model.PaymentApp
import com.pp.payspeak.model.PaymentEvent
import com.pp.payspeak.model.PaymentSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

private const val TAG = "DuplicateDetector"

private data class RecentEvent(
    val keyHash: String,
    val amount: Long,
    val senderNorm: String,
    val app: PaymentApp,
    val source: PaymentSource,
    val rawHash: String,
    val utrs: Set<String>,
    val timestamp: Long
)

class PaymentEventManager(private val database: PaySpeakDatabase) {
    private val eventWindowMs = 20_000L          // strict same-event suppression
    private val txnWindowMs = 5 * 60_000L        // cross-source correlation window
    private val retainWindowMs = 60 * 60_000L    // retention for diagnostics

    private val sourcePriority = mapOf(
        PaymentSource.NOTIFICATION to 3,
        PaymentSource.ACCESSIBILITY to 2,
        PaymentSource.SMS to 1,
        PaymentSource.UNKNOWN to 0
    )

    companion object {
        private val recent = ArrayDeque<RecentEvent>()
        private val lock = Any()
    }

    suspend fun procesPaymentEvent(event: PaymentEvent, currentLanguage: String = "en"): PaymentEvent? = withContext(Dispatchers.Default) {
        val now = System.currentTimeMillis()
        val senderNorm = normalize(event.sender)
        val rawHash = sha1(event.rawText)
        val utrs = extractUtrs(event.rawText)
        val evPriority = sourcePriority[event.source] ?: 0

        val result: PaymentEvent? = synchronized(lock) {
            prune(now)

            val sameEvent = recent.firstOrNull {
                it.source == event.source && it.rawHash == rawHash && (now - it.timestamp) <= eventWindowMs
            }
            if (sameEvent != null) {
                Log.d(TAG, "Strict duplicate suppressed: same source/raw within $eventWindowMs ms")
                return@synchronized null
            }

            var bestScore = 0.0
            var bestMatch: RecentEvent? = null
            recent.forEach { prev ->
                val timeDelta = now - prev.timestamp
                if (timeDelta > txnWindowMs) return@forEach
                if (prev.amount != event.amount) return@forEach

                var score = 0.0
                if (prev.rawHash == rawHash) score += 0.6
                val utrOverlap = prev.utrs.intersect(utrs)
                if (utrOverlap.isNotEmpty()) score += 0.6 else if (utrs.isNotEmpty() && prev.utrs.isNotEmpty()) score += 0.2
                val senderOverlap = tokenOverlap(prev.senderNorm, senderNorm)
                if (senderOverlap >= 0.3) score += 0.15 else if (senderOverlap >= 0.15) score += 0.1
                if (prev.app == event.appName) score += 0.05
                score += when {
                    timeDelta <= 30_000L -> 0.1
                    timeDelta <= 120_000L -> 0.1
                    timeDelta <= 300_000L -> 0.05
                    else -> 0.0
                }
                if ((sourcePriority[prev.source] ?: 0) >= evPriority) score += 0.05

                if (score > bestScore) {
                    bestScore = score
                    bestMatch = prev
                }
            }

            // fallback rule: if higher-priority seen, same amount, time < 2 min, and sender overlap exists, suppress
            val shouldSuppressLowPriority = bestMatch?.let { prev ->
                val higher = (sourcePriority[prev.source] ?: 0) > evPriority
                val timeDelta = now - prev.timestamp
                val overlap = tokenOverlap(prev.senderNorm, senderNorm)
                higher && timeDelta <= 120_000L && (overlap >= 0.1 || prev.amount == event.amount)
            } ?: false

            // fallback rule: suppress any subsequent event with same amount within 2 minutes if we already saw one
            val shouldSuppressRecentAmount = bestMatch?.let { prev ->
                val timeDelta = now - prev.timestamp
                timeDelta in 0..120_000
            } ?: false

            if (bestScore >= 0.7 || shouldSuppressLowPriority || shouldSuppressRecentAmount) {
                Log.d(TAG, "Fuzzy duplicate suppressed: score=$bestScore match=$bestMatch suppressLP=$shouldSuppressLowPriority suppressRecentAmt=$shouldSuppressRecentAmount")
                null
            } else {
                val recentEvent = RecentEvent(
                    keyHash = "$rawHash:${event.source}:${event.appName}",
                    amount = event.amount,
                    senderNorm = senderNorm,
                    app = event.appName,
                    source = event.source,
                    rawHash = rawHash,
                    utrs = utrs,
                    timestamp = now
                )
                recent.addFirst(recentEvent)
                Log.d(TAG, "Event cleared dedup — amount=${event.amount}p, source=${event.source}, sender=$senderNorm")
                event
            }
        }

        result?.also { storeForHistory(it, currentLanguage) }
    }

    suspend fun cleanupDatabase() = withContext(Dispatchers.IO) {
        try {
            val cutoff = System.currentTimeMillis() - retainWindowMs
            database.announcedPaymentDao().deleteOldPayments(cutoff)
            Log.d(TAG, "Cleaned up old payment records")
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up old payments", e)
        }
    }

    private fun prune(now: Long) {
        while (recent.isNotEmpty()) {
            val last = recent.last()
            if (now - last.timestamp > retainWindowMs) recent.removeLast() else break
        }
    }

    private suspend fun storeForHistory(event: PaymentEvent, language: String) {
        withContext(Dispatchers.IO) {
            runCatching {
                val announcedPayment = AnnouncedPayment(
                    amount = event.amount,
                    sender = event.sender,
                    appName = event.appName.name,
                    timestamp = event.timestamp,
                    language = language
                )
                database.announcedPaymentDao().insertAnnouncedPayment(announcedPayment)
            }.onFailure { Log.e(TAG, "Error storing announced payment", it) }
        }
    }

    private fun normalize(input: String): String = input.lowercase().replace("\\s+".toRegex(), "").take(128)

    private fun sha1(text: String): String = try {
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    } catch (e: Exception) {
        Log.w(TAG, "SHA-1 failed — falling back to hashCode (dedup may be less accurate)", e)
        text.hashCode().toString()
    }

    private fun extractUtrs(text: String): Set<String> {
        val utrRegex = Regex("""[A-Z0-9]{9,22}""", RegexOption.IGNORE_CASE)
        return utrRegex.findAll(text).map { it.value }.toSet()
    }

    private fun tokenOverlap(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val toksA = a.chunked(3).toSet()
        val toksB = b.chunked(3).toSet()
        if (toksA.isEmpty() || toksB.isEmpty()) return 0.0
        val inter = toksA.intersect(toksB).size.toDouble()
        val union = (toksA.size + toksB.size - inter).toDouble()
        return if (union == 0.0) 0.0 else inter / union
    }
}
