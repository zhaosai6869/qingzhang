package com.qingxi.ledger.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.qingxi.ledger.data.category.CategoryDao
import com.qingxi.ledger.data.category.CategorySeeder
import com.qingxi.ledger.data.category.CategoryEntity
import com.qingxi.ledger.data.tx.TransactionDao
import com.qingxi.ledger.data.tx.TransactionEntity

@Database(
    entities = [CategoryEntity::class, TransactionEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao

    companion object {
        private const val DB_NAME = "qingji.db"

        lateinit var instance: AppDatabase
            private set

        fun init(context: Context): AppDatabase {
            if (!::instance.isInitialized) {
                instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                ).addCallback(CategorySeeder.callback())
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
            }
            return instance
        }
    }
}
