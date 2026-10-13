package com.qingxi.ledger.data.category

import android.content.ContentValues
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

data class CategorySeed(val name: String, val icon: String)

private val EXPENSE_SEEDS = listOf(
    CategorySeed("餐饮", "🍚"),
    CategorySeed("交通", "🚌"),
    CategorySeed("购物", "🛍"),
    CategorySeed("日用", "🧻"),
    CategorySeed("居住", "🏠"),
    CategorySeed("娱乐", "🎮"),
    CategorySeed("医疗", "🏥"),
    CategorySeed("学习", "📚"),
    CategorySeed("出行", "✈"),
    CategorySeed("人情", "🎁"),
    CategorySeed("通讯", "📱"),
    CategorySeed("宠物", "🐶"),
    CategorySeed("其他", "📦")
)

object CategorySeeder {

    fun callback(): RoomDatabase.Callback = object : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            EXPENSE_SEEDS.forEachIndexed { index, seed ->
                db.insert("categories", android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, valuesOf(seed, index))
            }
        }
    }

    private fun valuesOf(seed: CategorySeed, index: Int): ContentValues = ContentValues().apply {
        put("name", seed.name)
        put("icon", seed.icon)
        put("kind", KIND_EXPENSE)
        put("sortOrder", index.toLong())
        put("isSystem", 1)
    }
}
