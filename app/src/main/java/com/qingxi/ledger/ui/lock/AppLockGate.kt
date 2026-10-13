package com.qingxi.ledger.ui.lock

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine

private const val WINDOW_FOCUS_POLL_MILLIS = 50L
private const val WINDOW_FOCUS_POLL_TIMES = 40

private const val DEFAULT_PIN_HINT = "请输入 6 位数字 PIN"

private val BIOMETRIC_AUTHENTICATORS =
    BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.BIOMETRIC_STRONG

/**
 * 设备是否具备可用的生物识别（已录入指纹/面容且硬件可用）。
 * 需要在 FragmentActivity 上才能弹出系统验证框，因此这里一并校验宿主类型。
 */
fun biometricAvailable(context: Context): Boolean {
    val activity = context as? FragmentActivity ?: return false
    return runCatching {
        BiometricManager.from(activity).canAuthenticate(BIOMETRIC_AUTHENTICATORS) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }.getOrDefault(false)
}

/** 供设置页试验生物识别解锁。true 通过，false 无法发起验证，null 取消或系统不可用。 */
suspend fun biometricVerify(context: Context): Boolean? {
    val activity = context as? FragmentActivity ?: return null
    return biometricAuthenticate(activity)
}

/**
 * 应用锁遮罩：只在「打开应用」这件事上拦人——进程冷启动时未验证过就不绘制 content。
 * 应用退到后台再回来不重新验证，系统选择器（相册、文件）返回同样直接放行。
 * MainActivity 只需用它包住业务内容即可。
 */
@Composable
fun AppLockGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val manager = remember(context) { LockManager(context) }
    val lockState by manager.state.collectAsStateWithLifecycle(initialValue = null)

    // 初始为上锁状态：配置读出来之前不能把账单内容画到屏幕上。
    var locked by remember { mutableStateOf(true) }
    var verifiedThisSession by remember { mutableStateOf(false) }

    val enabled = lockState?.enabled == true
    val loaded = lockState != null

    // 只有「配置加载完成」和「启用开关变化」会重算：退到后台再回来不经过这里，
    // 所以本次进程验证过一次就一直放行到进程结束。
    LaunchedEffect(loaded, enabled) {
        if (enabled && !locked && !verifiedThisSession) {
            // 在设置页当场开启应用锁：本次前台继续放行，下一次打开软件才验证。
            verifiedThisSession = true
            return@LaunchedEffect
        }
        locked = when {
            lockState == null -> true
            !enabled -> false
            else -> !verifiedThisSession
        }
    }

    val state = lockState
    when {
        state == null -> Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        )
        enabled && locked -> LockScreen(
            manager = manager,
            biometricEnabled = lockState?.biometricEnabled ?: true,
            onUnlocked = {
                verifiedThisSession = true
                locked = false
            }
        )
        else -> content()
    }
}

@Composable
private fun LockScreen(
    manager: LockManager,
    biometricEnabled: Boolean,
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val biometricReady = biometricEnabled && remember(context) { biometricAvailable(context) }

    var pin by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf(DEFAULT_PIN_HINT) }
    var isError by remember { mutableStateOf(false) }
    var errorTick by remember { mutableIntStateOf(0) }
    var shakeOffset by remember { mutableFloatStateOf(0f) }
    var cooldownSeconds by remember { mutableIntStateOf(0) }
    // 每点一次「使用指纹」都要能重新发起验证，所以用自增计数而不是布尔值：
    // 布尔值在上一请求仍在挂起时会因 key 不变而被 Compose 忽略，表现为按钮没反应。
    var biometricTick by remember { mutableIntStateOf(if (biometricReady) 1 else 0) }

    LaunchedEffect(biometricTick) {
        if (biometricTick == 0 || activity == null || !biometricReady) return@LaunchedEffect
        // 冷启动时上锁状态和首帧组合几乎同时发生，此时窗口焦点可能还没落到本应用，
        // 直接弹系统面板会被立刻取消，用户只看到「什么都没发生」。
        if (!awaitWindowFocus(activity)) {
            hint = "系统验证窗口尚未就绪，请输入 PIN 或再点一次指纹"
            return@LaunchedEffect
        }
        when (biometricAuthenticate(activity)) {
            true -> onUnlocked()
            null -> hint = "已取消验证，请输入 PIN"
            else -> hint = "无法发起验证，请输入 PIN"
        }
    }

    LaunchedEffect(errorTick) {
        if (errorTick == 0) return@LaunchedEffect
        floatArrayOf(-16f, 14f, -10f, 8f, -4f, 0f).forEach { step ->
            shakeOffset = step
            delay(40L)
        }
    }

    LaunchedEffect(cooldownSeconds) {
        while (cooldownSeconds > 0) {
            delay(1000L)
            cooldownSeconds -= 1
        }
    }

    LaunchedEffect(pin) {
        if (pin.length != PIN_LENGTH) return@LaunchedEffect
        when (val result = manager.attemptUnlock(pin)) {
            UnlockResult.Success -> {
                pin = ""
                onUnlocked()
            }
            is UnlockResult.WrongPin -> {
                pin = ""
                isError = true
                errorTick += 1
                hint = "密码不正确，还可尝试 ${result.remainingAttempts} 次"
            }
            is UnlockResult.TooManyAttempts -> {
                pin = ""
                isError = true
                errorTick += 1
                cooldownSeconds = result.remainSeconds
                hint = "失败次数过多，请等待 60 秒"
            }
        }
    }

    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 30.dp)
        ) {
            Text(
                text = "轻账已上锁",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "验证通过后才显示账单内容",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.offset(x = shakeOffset.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(PIN_LENGTH) { index ->
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(
                                if (index < pin.length) colors.primary else colors.surfaceVariant
                            )
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                text = if (cooldownSeconds > 0) "请等待 ${cooldownSeconds} 秒后再试" else hint,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError || cooldownSeconds > 0) colors.error else colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.height(40.dp)
            )

            Spacer(Modifier.height(6.dp))
            PinKeypad(
                enabled = cooldownSeconds == 0,
                onDigit = { digit ->
                    if (pin.length < PIN_LENGTH && cooldownSeconds == 0) {
                        if (isError) {
                            isError = false
                            hint = DEFAULT_PIN_HINT
                        }
                        pin += digit
                    }
                },
                onBackspace = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
                onClear = { pin = "" }
            )

            if (biometricReady) {
                Spacer(Modifier.height(14.dp))
                BiometricButton(
                    enabled = cooldownSeconds == 0,
                    onClick = { biometricTick += 1 }
                )
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = "忘记 PIN 需要清除应用数据才能解锁",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                fontSize = 11.sp
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun PinKeypad(
    enabled: Boolean,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.45f),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        listOf("123", "456", "789").forEach { rowKeys ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowKeys.forEach { key ->
                    KeypadButton(label = key.toString(), modifier = Modifier.weight(1f)) { onDigit(key.toString()) }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KeypadButton(label = "清空", modifier = Modifier.weight(1f), onClick = onClear)
            KeypadButton(label = "0", modifier = Modifier.weight(1f)) { onDigit("0") }
            KeypadButton(label = "⌫", modifier = Modifier.weight(1f), onClick = onBackspace)
        }
    }
}

@Composable
private fun BiometricButton(enabled: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.45f)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.primary.copy(alpha = 0.12f))
            .clickable(enabled = enabled, onClickLabel = "使用指纹或面容解锁", onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "🔑", fontSize = 15.sp)
        Spacer(Modifier.width(6.dp))
        Text(
            text = "使用指纹 / 面容解锁",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = colors.primary
        )
    }
}

@Composable
private fun KeypadButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .height(58.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceVariant)
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface,
            fontSize = if (label.length == 1) 20.sp else 14.sp
        )
    }
}

/** true 验证通过，null 表示用户取消或系统不可用（改用 PIN）。 */
private suspend fun biometricAuthenticate(activity: FragmentActivity): Boolean? =
    suspendCancellableCoroutine { continuation ->
        val finished = AtomicBoolean(false)
        fun settle(value: Boolean?) {
            if (finished.compareAndSet(false, true)) continuation.resume(value)
        }

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("解锁轻账")
            .setSubtitle("使用指纹或面容验证")
            .setDescription("验证通过后才显示账单内容")
            .setNegativeButtonText("使用 PIN")
            .setAllowedAuthenticators(BIOMETRIC_AUTHENTICATORS)
            .build()

        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    settle(true)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    settle(null)
                }
            }
        )

        // 不覆写 onAuthenticationFailed：单次按压不匹配时系统面板仍开着等下一次尝试，
        // 提前结束协程会留下一个没人接收回调的孤儿面板，之后再点指纹会被系统直接拒绝。
        runCatching { prompt.authenticate(info) }.onFailure { settle(false) }
        continuation.invokeOnCancellation { runCatching { prompt.cancelAuthentication() } }
    }

/** 等到 Activity 真正拿到窗口焦点，避免系统验证面板刚弹出就被抢焦点的窗口取消。 */
private suspend fun awaitWindowFocus(activity: FragmentActivity): Boolean {
    repeat(WINDOW_FOCUS_POLL_TIMES) {
        if (activity.hasWindowFocus()) return true
        delay(WINDOW_FOCUS_POLL_MILLIS)
    }
    return activity.hasWindowFocus()
}
