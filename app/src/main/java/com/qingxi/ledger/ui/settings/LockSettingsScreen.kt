package com.qingxi.ledger.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qingxi.ledger.ui.lock.MAX_FAILED_ATTEMPTS
import com.qingxi.ledger.ui.lock.PIN_LENGTH
import com.qingxi.ledger.ui.lock.biometricAvailable
import com.qingxi.ledger.ui.lock.biometricVerify
import kotlinx.coroutines.launch

/** 应用锁设置：指纹/面容由系统生物识别提供，这里配置 PIN 备用；验证只发生在每次打开应用时。 */
@Composable
fun LockSettingsScreen(onBack: () -> Unit) {
    val manager = rememberLockManager()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val state by manager.state.collectAsStateWithLifecycle(initialValue = null)
    val biometricReady = remember { biometricAvailable(context) }

    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    val enabled = state?.enabled == true
    val hasPin = state?.hasPin == true
    val coolingDown = (state?.lockedUntilMillis ?: 0L) > System.currentTimeMillis()

    SubPageShell(title = "应用锁", onBack = onBack, snackbarHostState = snackbar) {
        SectionCard(
            title = "开关",
            subtitle = if (enabled) "已开启" else "未开启"
        ) {
            ToggleRow(
                label = "启用应用锁",
                hint = if (hasPin) "每次打开应用时验证指纹/面容或 PIN，后台返回不再验证" else "需要先设置 $PIN_LENGTH 位数字 PIN",
                checked = enabled,
                onCheckedChange = { want ->
                    scope.launch {
                        when {
                            want && !hasPin -> snackbar.showSnackbar("请先设置 $PIN_LENGTH 位 PIN")
                            want -> manager.setEnabled(true)
                            else -> {
                                manager.resetLock()
                                pin = ""
                                confirm = ""
                                snackbar.showSnackbar("已关闭应用锁并清除 PIN")
                            }
                        }
                    }
                }
            )
            ToggleRow(
                label = "指纹 / 面容解锁",
                hint = when {
                    !biometricReady -> "本机暂无可用生物识别，请先在系统设置里录入指纹或面容"
                    else -> "上锁后优先弹出系统验证框，验证失败或取消时改用 PIN"
                },
                checked = state?.biometricEnabled ?: true,
                enabled = enabled && biometricReady,
                onCheckedChange = { value -> scope.launch { manager.setBiometricEnabled(value) } }
            )
            if (biometricReady && enabled) {
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        scope.launch {
                            when (biometricVerify(context)) {
                                true -> snackbar.showSnackbar("生物识别验证通过")
                                false -> snackbar.showSnackbar("无法发起生物识别，请稍后重试")
                                else -> snackbar.showSnackbar("已取消验证，可改用 PIN 解锁")
                            }
                        }
                    }
                ) { Text("测试指纹 / 面容解锁") }
            }
            ToggleRow(
                label = "隐藏最近任务缩略图",
                hint = "开启后系统任务卡片显示空白，同时禁止截屏（FLAG_SECURE）",
                checked = state?.hideContent ?: false,
                enabled = enabled,
                onCheckedChange = { value -> scope.launch { manager.setHideContent(value) } }
            )
            if (coolingDown) {
                NoteText(
                    text = "验证失败次数过多，已临时锁定，1 分钟后可再试。",
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        SectionCard(title = if (hasPin) "修改 PIN" else "设置 PIN") {
            NoteText(text = "PIN 用 PBKDF2 派生后保存，应用只比对哈希，不保存明文。失败 $MAX_FAILED_ATTEMPTS 次会锁定 1 分钟。")
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it.filter { c -> c.isDigit() }.take(PIN_LENGTH) },
                label = { Text("新 PIN（$PIN_LENGTH 位数字）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = confirm,
                onValueChange = { confirm = it.filter { c -> c.isDigit() }.take(PIN_LENGTH) },
                label = { Text("再次输入确认") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        scope.launch {
                            when {
                                pin.length != PIN_LENGTH -> snackbar.showSnackbar("PIN 需要 $PIN_LENGTH 位数字")
                                pin != confirm -> snackbar.showSnackbar("两次输入的 PIN 不一致")
                                !manager.setPin(pin) -> snackbar.showSnackbar("PIN 保存失败，请重试")
                                else -> {
                                    pin = ""
                                    confirm = ""
                                    snackbar.showSnackbar("PIN 已保存，应用锁已开启")
                                }
                            }
                        }
                    }
                ) { Text(if (hasPin) "更新 PIN" else "保存并开启") }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        pin = ""
                        confirm = ""
                    }
                ) { Text("清空") }
            }
        }

        Spacer(Modifier.height(12.dp))
        NoteText(
            text = "忘记 PIN 只能清除应用数据重新记账，请务必先导出备份。生物识别由系统托管，应用不接触指纹或面容信息。",
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
private fun ToggleRow(
    label: String,
    hint: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = 0.45f)
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}
