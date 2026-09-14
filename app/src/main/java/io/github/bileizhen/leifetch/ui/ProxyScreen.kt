package io.github.bileizhen.leifetch

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.preference.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 代理设置页：四种模式（不使用 / 系统 / 手动 / 自动）+ 手动配置表单 + 线路连通测试。
 * 下载、GitHub 镜像测速与 Firefox 探测共用这一份配置。
 */
internal fun LazyListScope.proxyItems(config: Config, vm: MainViewModel) {
    val proxy = config.proxy
    item {
        Column(Modifier.padding(bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("代理", fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
            Text("下载、镜像测速与 Firefox 探测共用同一套配置。直连名单内的地址始终直连，不受代理影响。",
                fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }
    item {
        Card {
            ProxyMode.all.forEach { mode ->
                BasicComponent(title = ProxyMode.label(mode), summary = ProxyMode.summary(mode),
                    onClick = { vm.edit { it.copy(proxy = it.proxy.copy(mode = mode)) } },
                    endActions = {
                        if (proxy.mode == mode) Icon(Icons.Rounded.Check, "已选择", tint = MiuixTheme.colorScheme.primary)
                    })
            }
        }
    }
    item { SmallTitle("手动配置", insideMargin = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) }
    item { ManualProxyCard(proxy, vm) }
    item { SmallTitle("连接测试", insideMargin = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) }
    item {
        Card {
            BasicComponent(
                title = if (vm.proxyTesting) "测试中…" else "测试当前线路",
                summary = "经当前代理访问 GitHub 上的小文件，只取首字节",
                onClick = { if (!vm.proxyTesting) vm.testProxy() })
            if (vm.proxyResult.isNotEmpty()) Text(vm.proxyResult,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                fontSize = 13.sp,
                color = if (vm.proxyOk) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }
    item {
        Notice("自动模式依次尝试：系统代理 → 本机常见代理端口（7890、7897、10809、10808、1080 等，覆盖 Clash、v2rayN、sing-box 等客户端的默认监听）→ 直连。" +
            "探测只做本机 TCP 连接与代理协议握手，不产生下载流量，结果缓存 5 分钟。")
    }
}

@Composable
private fun ManualProxyCard(settings: ProxySettings, vm: MainViewModel) {
    Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // 本地状态只在打字时变化，落盘后（字段值变化）重新对齐已保存的值；
            // 按字段而非整个对象记忆，切换模式不会把正在输入的内容冲掉。
            var host by remember(settings.host) { mutableStateOf(settings.host) }
            var port by remember(settings.port) { mutableStateOf(settings.port.takeIf { it > 0 }?.toString().orEmpty()) }
            var username by remember(settings.username) { mutableStateOf(settings.username) }
            var password by remember(settings.password) { mutableStateOf(settings.password) }
            var bypass by remember(settings.bypass) { mutableStateOf(settings.bypass) }
            val portValue = port.trim().toIntOrNull()
            val valid = host.isNotBlank() && portValue in 1..65535
            OverlaySpinnerPreference(title = "代理类型",
                items = listOf("HTTP", "SOCKS5").map { DropdownItem(it) },
                selectedIndex = if (settings.type == ProxyType.SOCKS) 1 else 0,
                onSelectedIndexChange = { index ->
                    vm.edit { it.copy(proxy = it.proxy.copy(type = if (index == 1) ProxyType.SOCKS else ProxyType.HTTP)) }
                })
            TextField(value = host, onValueChange = { host = it }, label = "服务器地址",
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            TextField(value = port,
                onValueChange = { next -> if (next.length <= 5 && next.all(Char::isDigit)) port = next },
                label = "端口", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            TextField(value = username, onValueChange = { username = it }, label = "用户名（可留空）", singleLine = true)
            TextField(value = password, onValueChange = { password = it }, label = "密码（可留空）",
                singleLine = true, visualTransformation = PasswordVisualTransformation())
            TextField(value = bypass, onValueChange = { bypass = it }, label = "直连名单（可留空）",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            TextButton("保存手动配置", enabled = valid, onClick = {
                vm.edit {
                    it.copy(proxy = it.proxy.copy(host = host.trim(), port = portValue ?: 0,
                        username = username.trim(), password = password, bypass = bypass))
                }
            }, modifier = Modifier.fillMaxWidth())
            Text("保存后「手动配置」模式才会生效。直连名单用逗号或空格分隔：填域名即匹配该域名及其子域，" +
                "`*` 表示全部直连，`<local>` 表示 localhost 与内网地址。",
                fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }
}
