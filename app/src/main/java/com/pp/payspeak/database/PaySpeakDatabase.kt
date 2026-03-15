package com.pp.payspeak.database

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.pp.payspeak.model.AnnouncedPayment

@Dao
interface AnnouncedPaymentDao {
    @Insert
    suspend fun insertAnnouncedPayment(payment: AnnouncedPayment)

    @Query("SELECT * FROM announced_payments WHERE timestamp > :sinceTimeMillis ORDER BY timestamp DESC")
    suspend fun getRecentPayments(sinceTimeMillis: Long): List<AnnouncedPayment>

    @Query("DELETE FROM announced_payments WHERE timestamp < :beforeTimeMillis")
    suspend fun deleteOldPayments(beforeTimeMillis: Long)

    @Query("SELECT COUNT(*) FROM announced_payments WHERE amount = :amount AND sender = :sender AND timestamp > :sinceTimeMillis")
    suspend fun countDuplicates(amount: Long, sender: String, sinceTimeMillis: Long): Int
}

@Database(entities = [AnnouncedPayment::class], version = 1)
abstract class PaySpeakDatabase : RoomDatabase() {
    abstract fun announcedPaymentDao(): AnnouncedPaymentDao

    companion object {
        @Volatile
        private var INSTANCE: PaySpeakDatabase? = null

        fun getInstance(context: Context): PaySpeakDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PaySpeakDatabase::class.java,
                    "payspeak_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

