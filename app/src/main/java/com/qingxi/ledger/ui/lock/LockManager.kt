package com.qingxi.ledger.ui.lock

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.lockStore by preferencesDataStore(name = "qingji_lock")

const val PIN_LENGTH = 6
const val MAX_FAILED_ATTEMPTS = 5
const val LOCK_OUT_MILLIS = 60_000L

/** MainActivity 按 hideContent 决定 FLAG_SECURE，按 enabled 决定是否要挂 AppLockGate。 */
data class LockState(
    val enabled: Boolean = false,
    val hasPin: Boolean = false,
    val biometricEnabled: Boolean = true,
    val hideContent: Boolean = false,
    val failedAttempts: Int = 0,
    val lockedUntilMillis: Long = 0L
)

sealed class UnlockResult {
    data object Success : UnlockResult()
    data class WrongPin(val remainingAttempts: Int) : UnlockResult()
    data class TooManyAttempts(val remainSeconds: Int) : UnlockResult()
}

class LockManager(context: Context) {

    private val appContext = context.applicationContext
    private val store get() = appContext.lockStore
    private val random = SecureRandom()

    private val keyEnabled = booleanPreferencesKey("lock_enabled")
    private val keyBiometricEnabled = booleanPreferencesKey("lock_biometric_enabled")
    private val keyPinHash = stringPreferencesKey("lock_pin_hash")
    private val keyPinSalt = stringPreferencesKey("lock_pin_salt")
    private val keyHideContent = booleanPreferencesKey("lock_hide_content")
    private val keyFailedAttempts = intPreferencesKey("lock_failed_attempts")
    private val keyLockedUntil = longPreferencesKey("lock_locked_until")

    val state: Flow<LockState> = store.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            LockState(
                enabled = prefs[keyEnabled] ?: false,
                hasPin = !prefs[keyPinHash].isNullOrEmpty(),
                biometricEnabled = prefs[keyBiometricEnabled] ?: true,
                hideContent = prefs[keyHideContent] ?: false,
                failedAttempts = prefs[keyFailedAttempts] ?: 0,
                lockedUntilMillis = prefs[keyLockedUntil] ?: 0L
            )
        }

    suspend fun currentState(): LockState = state.first()

    suspend fun setEnabled(enabled: Boolean) {
        store.edit { it[keyEnabled] = enabled }
    }

    suspend fun setBiometricEnabled(value: Boolean) {
        store.edit { it[keyBiometricEnabled] = value }
    }

    suspend fun setHideContent(value: Boolean) {
        store.edit { it[keyHideContent] = value }
    }

    /** 设置 6 位数字 PIN，成功返回 true；口令非法时不写入任何数据。 */
    suspend fun setPin(pin: String): Boolean {
        if (!isLegalPin(pin)) return false
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val hash = derive(pin, salt)
        store.edit {
            it[keyPinSalt] = Base64.encodeToString(salt, Base64.NO_WRAP)
            it[keyPinHash] = Base64.encodeToString(hash, Base64.NO_WRAP)
            it[keyEnabled] = true
            it[keyFailedAttempts] = 0
            it[keyLockedUntil] = 0L
        }
        return true
    }

    suspend fun attemptUnlock(pin: String): UnlockResult {
        val current = currentState()
        val now = System.currentTimeMillis()
        if (current.lockedUntilMillis > now) {
            return UnlockResult.TooManyAttempts(secondsLeft(current.lockedUntilMillis, now))
        }
        val prefs = store.data.catch { emit(emptyPreferences()) }.first()
        val saltText = prefs[keyPinSalt]
        val hashText = prefs[keyPinHash]
        if (saltText.isNullOrEmpty() || hashText.isNullOrEmpty()) {
            return UnlockResult.WrongPin(MAX_FAILED_ATTEMPTS)
        }
        val salt = Base64.decode(saltText, Base64.NO_WRAP)
        val expected = Base64.decode(hashText, Base64.NO_WRAP)
        val matched = runCatching { MessageDigest.isEqual(expected, derive(pin, salt)) }.getOrDefault(false)
        if (matched) {
            store.edit {
                it[keyFailedAttempts] = 0
                it[keyLockedUntil] = 0L
            }
            return UnlockResult.Success
        }
        return recordFailure()
    }

    private suspend fun recordFailure(): UnlockResult {
        val attempts = currentState().failedAttempts + 1
        val now = System.currentTimeMillis()
        return if (attempts >= MAX_FAILED_ATTEMPTS) {
            store.edit {
                it[keyFailedAttempts] = 0
                it[keyLockedUntil] = now + LOCK_OUT_MILLIS
            }
            UnlockResult.TooManyAttempts((LOCK_OUT_MILLIS / 1000L).toInt())
        } else {
            store.edit { it[keyFailedAttempts] = attempts }
            UnlockResult.WrongPin(MAX_FAILED_ATTEMPTS - attempts)
        }
    }

    /** 关闭应用锁并抹掉 PIN，失败计数与冷却时间一并清零。 */
    suspend fun resetLock() {
        store.edit {
            it.remove(keyPinHash)
            it.remove(keyPinSalt)
            it.remove(keyFailedAttempts)
            it.remove(keyLockedUntil)
            it[keyEnabled] = false
        }
    }

    private fun secondsLeft(untilMillis: Long, now: Long): Int =
        ((untilMillis - now + 999L) / 1000L).toInt().coerceAtLeast(0)

    private fun isLegalPin(pin: String): Boolean =
        pin.length == PIN_LENGTH && pin.all { it.isDigit() }

    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_BITS)
        return SecretKeyFactory.getInstance(PBKDF2_ALGORITHM).generateSecret(spec).encoded
    }

    private companion object {
        const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        const val PBKDF2_ITERATIONS = 120_000
        const val KEY_BITS = 256
        const val SALT_BYTES = 16
    }
}
