package io.github.bileizhen.leifetch

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** GitHub 镜像站列表页：按测速延迟升序排列，点选手动指定或保持自动择优，可增删自定义镜像站。 */
internal fun LazyListScope.mirrorItems(config: Config, vm: MainViewModel) {
    val custom = GithubMirrors.parseCustom(config.githubMirrors)
    val customSet = custom.toSet()
    item {
        // 打开页面与增删镜像后自动测速一次；测速不改变镜像列表，不会循环触发。
        LaunchedEffect(custom) {
            if (!vm.mirrorTesting && vm.mirrorResults.isEmpty()) vm.testMirrors()
        }
        Column(Modifier.padding(bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("GitHub 镜像站", fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
            Text("下载开始时自动识别 GitHub 直链并经镜像站加速。点击镜像手动指定；自动模式使用延迟最低的可用镜像，列表按测速延迟从低到高排序。",
                fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }
    item {
        Card {
            BasicComponent(title = "自动选择最快",
                summary = if (vm.mirrorTesting) "测速中…" else "下载时实测并使用延迟最低的镜像",
                onClick = { vm.selectMirror("auto") }, endActions = {
                    if (config.githubMirrorPick == "auto") Icon(Icons.Rounded.Check, "已选择", tint = MiuixTheme.colorScheme.primary)
                })
            BasicComponent(title = "重新测速", summary = "测试目标：${GithubMirrors.speedTestTarget}",
                onClick = { vm.testMirrors() })
        }
    }
    item { SmallTitle("镜像站 · 按延迟排序", insideMargin = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) }
    val ordered = (GithubMirrors.builtin + custom).sortedBy { vm.mirrorResults[it] ?: Long.MAX_VALUE }
    itemsIndexed(ordered, key = { _, mirror -> mirror }) { index, mirror ->
        Box(Modifier.animateItem()) {
            StaggeredEntrance(index, baseDelayMs = 350) { MirrorRow(mirror, config, vm, removable = mirror in customSet) }
        }
    }
    item { SmallTitle("自定义镜像站", insideMargin = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) }
    item {
        Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val input = rememberTextFieldState()
                TextField(state = input, label = "https://your-mirror.example.com", modifier = Modifier.fillMaxWidth())
                val valid = input.text.toString().trim().let { raw ->
                    raw.isNotEmpty() && GithubMirrors.parseCustom(raw).singleOrNull() != null
                }
                TextButton("添加镜像站", enabled = valid, onClick = {
                    vm.addMirror(input.text.toString())
                    input.edit { replace(0, length, "") }
                }, modifier = Modifier.fillMaxWidth())
                Text("镜像站需支持「前缀 + GitHub 原链接」的转发格式，多个镜像站换行分隔保存。",
                    fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
        }
    }
}

@Composable
private fun MirrorRow(mirror: String, config: Config, vm: MainViewModel, removable: Boolean) {
    val latency = vm.mirrorResults[mirror]
    val status = when {
        vm.mirrorTesting && latency == null -> "测速中…"
        latency != null -> "首字节 $latency ms"
        vm.mirrorResults.containsKey(mirror) -> "不可用"
        else -> "未测速"
    }
    BasicComponent(title = mirror.removePrefix("https://").removePrefix("http://"),
        summary = status, onClick = { vm.selectMirror(mirror) }, endActions = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (removable) TextButton("删除", onClick = { vm.removeMirror(mirror) })
                if (config.githubMirrorPick == mirror) Icon(Icons.Rounded.Check, "已选择", tint = MiuixTheme.colorScheme.primary)
            }
        })
}
