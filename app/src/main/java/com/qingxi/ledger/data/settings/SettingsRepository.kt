package com.qingxi.ledger.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "qingji_settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class ThemePalette(val label: String, val argb: Long) {
    BLUE("靛蓝", 0xFF2E6BFF),
    TEAL("青竹", 0xFF0F8C72),
    AMBER("暖橙", 0xFFD98416),
    PLUM("藕荷", 0xFF7A5AC8),
    SLATE("墨灰", 0xFF444C5A)
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val palette: ThemePalette = ThemePalette.BLUE,
    val lastCategoryId: Long = 0L,
    val weekStartsOnSunday: Boolean = false,
    val monthStartDay: Int = 1,
    val updateSourceUrl: String = ""
)

class SettingsRepository(private val context: Context) {

    private val keyThemeMode = stringPreferencesKey("theme_mode")
    private val keyPalette = stringPreferencesKey("palette")
    private val keyLastCategoryId = longPreferencesKey("last_category_id")
    private val keyWeekStartsOnSunday = booleanPreferencesKey("week_start_sunday")
    private val keyMonthStartDay = intPreferencesKey("month_start_day")
    private val keyUpdateSourceUrl = stringPreferencesKey("update_source_url")

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            themeMode = prefs[keyThemeMode]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            palette = prefs[keyPalette]?.let { name -> ThemePalette.entries.firstOrNull { it.name == name } } ?: ThemePalette.BLUE,
            lastCategoryId = prefs[keyLastCategoryId] ?: 0L,
            weekStartsOnSunday = prefs[keyWeekStartsOnSunday] ?: false,
            monthStartDay = (prefs[keyMonthStartDay] ?: 1).coerceIn(1, MAX_MONTH_START_DAY),
            updateSourceUrl = prefs[keyUpdateSourceUrl] ?: ""
        )
    }

    suspend fun setUpdateSourceUrl(url: String) {
        context.dataStore.edit { it[keyUpdateSourceUrl] = url.trim() }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[keyThemeMode] = mode.name }
    }

    suspend fun setPalette(palette: ThemePalette) {
        context.dataStore.edit { it[keyPalette] = palette.name }
    }

    suspend fun setWeekStartsOnSunday(startsOnSunday: Boolean) {
        context.dataStore.edit { it[keyWeekStartsOnSunday] = startsOnSunday }
    }

    suspend fun setMonthStartDay(day: Int) {
        context.dataStore.edit { it[keyMonthStartDay] = day.coerceIn(1, MAX_MONTH_START_DAY) }
    }

    suspend fun rememberLastCategory(categoryId: Long) {
        context.dataStore.edit { it[keyLastCategoryId] = categoryId }
    }

    companion object {
        /** 起始日限制在 1..28，避免月末天数不足时区间错乱。 */
        const val MAX_MONTH_START_DAY = 28
    }
}
