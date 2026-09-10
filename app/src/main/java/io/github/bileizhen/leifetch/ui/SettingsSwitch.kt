package io.github.bileizhen.leifetch.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.Role
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Switch

/** Miuix 0.9 拆分了旧的 extra 包；保持设置行可用（移植自 XBlocker）。 */
@Composable
internal fun SuperSwitch(
    title: String,
    summary: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    startAction: (@Composable () -> Unit)? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    BasicComponent(
        title = title, summary = summary, enabled = enabled,
        startAction = startAction,
        onClick = { onCheckedChange(!checked) }, role = Role.Switch,
        endActions = {
            Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
        },
    )
}
