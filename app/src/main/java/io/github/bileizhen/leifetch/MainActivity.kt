// 界面壳照抄 XBlocker（SukiSU-Ultra v4.1.3 / compose-miuix-ui 示例的移植结构），
// 见 THIRD_PARTY_NOTICES.md。GPL-3.0。
package io.github.bileizhen.leifetch

import android.Manifest
import android.app.Application
import android.content.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuOpen
import androidx.compose.material.icons.automirrored.rounded.Rule
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import io.github.bileizhen.leifetch.ui.AboutDocumentScreen
import io.github.bileizhen.leifetch.ui.AboutScreenActions
import io.github.bileizhen.leifetch.ui.AboutScreenMiuix
import io.github.bileizhen.leifetch.ui.AboutUiState
import io.github.bileizhen.leifetch.ui.PlainFloatingBar
import io.github.bileizhen.leifetch.ui.SuperSwitch
import io.github.bileizhen.leifetch.ui.component.FloatingBottomBar
import io.github.bileizhen.leifetch.ui.component.FloatingBottomBarItem
import io.github.bileizhen.leifetch.ui.theme.LocalDarkTheme
import io.github.bileizhen.leifetch.ui.util.BlurredBar
import io.github.bileizhen.leifetch.ui.util.rememberBlurBackdrop
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.overlay.OverlayDialog as SuperDialog
import top.yukonga.miuix.kmp.preference.*
import top.yukonga.miuix.kmp.theme.*
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as LeiFetchApp
    val tasks = app.store.tasks
    val config = app.settings.state
    val error = MutableStateFlow("")
    val speedHistory = MutableStateFlow(List(40) { 0L })
    init {
        viewModelScope.launch {
            while (isActive) {
                delay(1000)
                speedHistory.value = (speedHistory.value + tasks.value.sumOf { it.speed }).takeLast(40)
            }
        }
    }
    private fun work(action: suspend () -> Unit) {
        viewModelScope.launch {
            try { app.ready.await(); action() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error.value = e.message ?: e.javaClass.simpleName }
        }
    }
    fun edit(change: (Config) -> Config) = work { app.settings.edit(change) }

    // GitHub 镜像加速：测速结果与编辑操作。用 compose 状态而非 StateFlow，
    // 镜像列表页要在 LazyColumn 内容层按延迟排序，需要快照订阅。
    var mirrorResults by mutableStateOf<Map<String, Long?>>(emptyMap())
        private set
    var mirrorTesting by mutableStateOf(false)
        private set
    fun testMirrors(target: String = GithubMirrors.speedTestTarget) = viewModelScope.launch {
        if (mirrorTesting) return@launch
        mirrorTesting = true
        mirrorResults = emptyMap()
        try {
            mirrorResults = GithubMirrors.measure(
                GithubMirrors.effectiveList(app.settings.state.value.githubMirrors), target)
        } finally { mirrorTesting = false }
    }
    fun selectMirror(mirror: String) = edit { it.copy(githubMirrorPick = mirror) }
    fun addMirror(raw: String) = edit {
        it.copy(githubMirrors = GithubMirrors.parseCustom(it.githubMirrors + "\n" + raw).joinToString("\n"))
    }
    fun removeMirror(mirror: String) = edit {
        it.copy(githubMirrors = GithubMirrors.parseCustom(it.githubMirrors).filter { m -> m != mirror }.joinToString("\n"))
    }

    /** 通过 libxposed 服务向 LSPosed 申请作用域；结果以 Toast 提示（回调已回到主线程）。 */
    fun requestScope(packages: List<String>) {
        if (packages.isEmpty()) return
        if (!XposedServiceClient.connected()) {
            error.value = "LSPosed 服务未连接：请先在 LSPosed 启用 LeiFetch 并重新打开本应用，或手动勾选作用域"
            return
        }
        error.value = "已发起作用域申请（${packages.size} 个），请在 LSPosed 弹窗中确认"
        XposedServiceClient.requestScope(packages) { approved, failure ->
            error.value = if (approved != null) "已授权 ${approved.size} 个作用域，重启相关应用后生效"
            else "作用域授权未完成：${failure?.take(80) ?: "可在 LSPosed 中手动勾选"}"
        }
    }
    /** 剪贴板下载链接识别：等待数据就绪后读取剪贴板，发现链接时供弹窗询问是否下载。 */
    val clipboardSuggest = MutableStateFlow<String?>(null)
    fun checkClipboard() = viewModelScope.launch {
        if (clipboardSuggest.value != null) return@launch
        app.ready.await()
        if (!config.value.clipboardDetect) return@launch
        val cm = app.getSystemService(ClipboardManager::class.java) ?: return@launch
        // Android 10+ 仅允许有焦点的窗口读取剪贴板；调用方在获得焦点时触发，
        // 但刚获得焦点的一瞬仍可能读到 null（焦点竞态），短暂重试几次。
        var text: String? = null
        for (attempt in 1..4) {
            text = runCatching {
                cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(app)?.toString()
            }.getOrNull()
            if (!text.isNullOrEmpty()) break
            if (attempt < 4) delay(200)
        }
        val url = clipboardUrl(text ?: "") ?: return@launch
        // 已询问过的链接持久去重：划掉后台、进程被杀、强停都不重置，避免剪贴板里的
        // 旧链接每次打开都弹；只保留最近 64 条。已在任务列表中的活跃链接也不再询问。
        val seen = config.value.clipboardSeen.lines().filter { it.isNotEmpty() }
        if (url in seen) return@launch
        runCatching {
            app.settings.edit { it.copy(clipboardSeen = ((seen + url).takeLast(64)).joinToString("\n")) }
        }
        if (app.store.tasks.value.any { it.url == url && it.state !in setOf("已完成", "已取消") }) return@launch
        clipboardSuggest.value = url
    }
    fun add(url: String, onAdded: (Task) -> Unit = {}) = work {
        val u = Uri.parse(url.trim())
        require(u.scheme in setOf("http", "https") && !u.host.isNullOrEmpty()) { "请输入有效 HTTP(S) 下载地址" }
        val task = withContext(Dispatchers.IO) {
            app.store.add(Task(url = url.trim(), name = safeName(u.lastPathSegment ?: "download.bin"), tree = config.value.tree))
        }
        onAdded(task)
    }
    fun start(id: String) = work {
        ContextCompat.startForegroundService(app, Intent(app, DownloadService::class.java).putExtra("id", id).putExtra("op", "start"))
    }
    fun control(task: Task, cancel: Boolean) = work {
        if (task.state in setOf("下载中", "排队中", "保存中")) {
            app.startService(Intent(app, DownloadService::class.java).putExtra("id", task.id)
                .putExtra("op", if (cancel) "cancel" else "pause"))
        } else withContext(Dispatchers.IO) {
            if (cancel) {
                workDir(app, task.id).deleteRecursively()
                app.store.update(task.id) { it.copy(state = "已取消", headers = emptyMap(), url = "", speed = 0) }
            }
        }
        Notices.refreshCandidate(app)
    }
    fun delete(task: Task) = work {
        withContext(Dispatchers.IO) {
            if (task.uri.isNotEmpty()) {
                val uri = Uri.parse(task.uri)
                if (uri.authority == "$PKG.files") {
                    require(app.contentResolver.delete(uri, null, null) > 0) { "删除文件失败" }
                } else {
                    val doc = androidx.documentfile.provider.DocumentFile.fromSingleUri(app, uri)
                    require(doc == null || !doc.exists() || doc.delete()) { "删除失败，请检查目录授权" }
                }
            }
            workDir(app, task.id).deleteRecursively()
            app.store.remove(task.id)
        }
        Notices.refreshCandidate(app)
    }
}

class MainActivity : ComponentActivity() {
    // 系统下载器接管等外部入口指定落地页（1 = 下载页）；onNewIntent 时更新。
    private var startPage by mutableStateOf(0)
    // internal：androidTest（friend module）端到端测试需要读取剪贴板提示状态。
    internal val vm by viewModels<MainViewModel>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        startPage = intent.getIntExtra("page", 0)
        handle(intent)
        setContent {
            LeiFetchApp(vm, startPage)
        }
    }
    // onWindowFocusChanged(true) 晚于 onResume，是 Android 10+ 剪贴板可读的最早可靠时机。
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) vm.checkClipboard()
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent); handle(intent)
        intent.getIntExtra("page", -1).takeIf { it >= 0 }?.let { startPage = it }
    }
    private fun handle(intent: Intent) {
        val op = intent.getStringExtra("op") ?: return
        if (op !in setOf("open", "share")) return
        val id = intent.getStringExtra("id") ?: return
        intent.removeExtra("op")
        lifecycleScope.launch {
            app.ready.await()
            app.store.get(id)?.takeIf { it.state == "已完成" }?.let { fileAction(this@MainActivity, it, op == "share") }
        }
    }
}

// 限定 URL 合法字符集，剪贴板文本里紧跟链接的中文说明不会被吞进匹配结果。
private val clipboardUrlPattern =
    Regex("""https?://[A-Za-z0-9\-._~:/?#\[\]@!$&'()*+,;=%]+""", RegexOption.IGNORE_CASE)

/** 从剪贴板文本提取第一个有效的 HTTP(S) 链接；没有则返回 null。 */
private fun clipboardUrl(text: String): String? {
    val url = clipboardUrlPattern.find(text)?.value
        ?.trimEnd('.', ',', ';', ':', '(', ')', '"', '\'', '，', '。', '；', '：', '）', '”', '」', '》')
        ?: return null
    val uri = Uri.parse(url)
    return if (uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank()) url else null
}

fun fileAction(context: Context, task: Task, share: Boolean) {
    runCatching {
        require(task.uri.isNotEmpty())
        val uri = Uri.parse(task.uri)
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(task.name.substringAfterLast('.', "").lowercase()) ?: "application/octet-stream"
        val intent = if (share) Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri)
        else Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.clipData = ClipData.newRawUri(task.name, uri)
        context.startActivity(Intent.createChooser(intent, if (share) "分享文件" else "打开文件"))
        context.getSystemService(android.app.NotificationManager::class.java).cancel(task.id, 1)
    }.onFailure { Toast.makeText(context, "无法打开文件：${it.javaClass.simpleName}", Toast.LENGTH_LONG).show() }
}

/** 与 XBlocker 相同的 ColorMode：0/3 跟随系统，1/4 浅色，2/5 深色。 */
private fun isDarkMode(colorMode: Int, systemDark: Boolean): Boolean = when (colorMode) {
    1, 4 -> false
    2, 5 -> true
    else -> systemDark
}

private fun appDarkColors() = darkColorScheme(
    background = Color(0xFF111214), surface = Color(0xFF111214),
    surfaceContainer = Color(0xFF1D1F22),
)

@Composable
private fun LeiFetchApp(vm: MainViewModel, startPage: Int = 0) {
    val config by vm.config.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    val darkTheme = isDarkMode(config.colorMode, systemDark)
    // ThemeController 字段只读，模式变化时重建 —— 与 SukiSU 在组合内构建的方式一致。
    val controller = ThemeController(
        colorSchemeMode = when (config.colorMode) {
            1 -> ColorSchemeMode.Light
            2 -> ColorSchemeMode.Dark
            3 -> ColorSchemeMode.MonetSystem
            4 -> ColorSchemeMode.MonetLight
            5 -> ColorSchemeMode.MonetDark
            else -> ColorSchemeMode.System
        },
        darkColors = appDarkColors(),
        lightColors = lightColorScheme(),
        isDark = darkTheme,
    )
    val context = LocalContext.current
    LaunchedEffect(darkTheme) {
        val window = (context as? ComponentActivity)?.window ?: return@LaunchedEffect
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDarkTheme provides darkTheme,
        LocalDensity provides Density(density.density * config.scale, density.fontScale),
    ) {
        MiuixTheme(controller = controller) { LeiFetchScreen(vm, startPage) }
    }
}

@Composable
private fun LeiFetchScreen(vm: MainViewModel, startPage: Int = 0) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val config by vm.config.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var selectedPage by rememberSaveable { mutableIntStateOf(0) }
    // 外部入口（系统下载器接管）指定落地页时切换一次，之后仍由用户自由导航。
    var handledStartPage by rememberSaveable { mutableIntStateOf(Int.MIN_VALUE) }
    LaunchedEffect(startPage) {
        if (startPage in 1..3 && startPage != handledStartPage) { selectedPage = startPage; handledStartPage = startPage }
    }
    var downloadFilter by rememberSaveable { mutableIntStateOf(0) }
    // SukiSU NavDisplay 模式：主标签共享一个根 entry，外观页压栈；重建后保持。
    var backStack by rememberSaveable { mutableStateOf(listOf(0)) }
    fun navigateBack() {
        if (backStack.size > 1) backStack = backStack.dropLast(1)
    }
    var confirmDelete by rememberSaveable { mutableStateOf("") }
    var showNewDownload by rememberSaveable { mutableStateOf(false) }
    var expandedTask by rememberSaveable { mutableStateOf("") }
    val search = rememberTextFieldState()
    var showSearch by rememberSaveable { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !NotificationManagerCompat.from(context).areNotificationsEnabled())
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            vm.edit { it.copy(tree = uri.toString()) }
        }.onFailure { vm.error.value = "无法保留目录访问权限" }
    }
    LaunchedEffect(error) {
        if (error.isNotEmpty()) { Toast.makeText(context, error, Toast.LENGTH_LONG).show(); vm.error.value = "" }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val wideLayout = maxWidth >= 840.dp
    val pages = listOf("仪表盘", "下载", "插件", "设置", "外观", "GitHub 镜像")
    val icons = listOf(TransferIcons.Dashboard, TransferIcons.Download, TransferIcons.Plugins, TransferIcons.Settings)
    val usePredictiveBack = config.predictiveBack && Build.VERSION.SDK_INT >= 34
    val listStates = List(pages.size) { rememberLazyListState() }
    val scrollBehaviors = List(pages.size) { MiuixScrollBehavior() }
    val pageContent: @Composable (Int, Modifier) -> Unit = { page, pageModifier ->
        val scrollBehavior = scrollBehaviors[page]
        val backdrop = rememberBlurBackdrop(config.blur)
        val surfaceColor = MiuixTheme.colorScheme.surface
        val glassBackdrop = rememberLayerBackdrop { drawRect(surfaceColor); drawContent() }
        val glassEnabled = config.blur && Build.VERSION.SDK_INT >= 33
        Scaffold(
            modifier = pageModifier,
            topBar = {
                BlurredBar(backdrop) {
                    SmallTopAppBar(
                        title = if (page == 0) "LeiFetch" else pages[page],
                        color = if (backdrop != null) Color.Transparent else surfaceColor,
                        scrollBehavior = scrollBehavior,
                            navigationIcon = {
                            if (page >= 4) IconButton(onClick = { navigateBack() }) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = MiuixTheme.colorScheme.onSurface)
                            }
                        },
                        actions = {
                            if (page == 1) IconButton(onClick = { showSearch = !showSearch; if (!showSearch) search.edit { replace(0, length, "") } }) {
                                Icon(if (showSearch) Icons.Rounded.Close else Icons.Rounded.Search, if (showSearch) "关闭搜索" else "搜索下载")
                            }
                            if (page < 2) IconButton(onClick = { showNewDownload = true },
                                modifier = Modifier.padding(end = 8.dp).size(48.dp).clip(RoundedCornerShape(16.dp))
                                    .background(MiuixTheme.colorScheme.primary)) {
                                Icon(Icons.Rounded.Add, "新建下载", tint = MiuixTheme.colorScheme.onPrimary)
                            }
                        },
                    )
                }
            },
            bottomBar = {
                if (page < 4 && !wideLayout) {
                    if (config.floatingBar) {
                        Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 26.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                            if (Build.VERSION.SDK_INT < 33) {
                                PlainFloatingBar(page, pages, icons) { selectedPage = it }
                            } else {
                                FloatingBottomBar(
                                    modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                                    selectedIndex = { page }, onSelected = { selectedPage = it },
                                    backdrop = glassBackdrop, tabsCount = 4, isBlurEnabled = glassEnabled, isGlassEnabled = config.liquidGlass,
                                ) {
                                    icons.forEachIndexed { index, icon ->
                                        FloatingBottomBarItem(
                                            onClick = { selectedPage = index },
                                            modifier = Modifier.semantics { selected = page == index },
                                        ) {
                                            // 玻璃效果在滑动药丸下渲染一层着色副本。
                                            val tint = if (!glassEnabled && page == index) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface
                                            Icon(icon, null, tint = tint)
                                            Text(pages[index], fontSize = 11.sp, lineHeight = 14.sp, color = tint, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        BlurredBar(backdrop) {
                            NavigationBar(color = if (backdrop != null) Color.Transparent else surfaceColor) {
                                icons.forEachIndexed { index, icon ->
                                    NavigationBarItem(modifier = Modifier.weight(1f), selected = page == index, onClick = { selectedPage = index }, icon = icon, label = pages[index])
                                }
                            }
                        }
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize()
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
                .then(if (config.floatingBar && glassEnabled) Modifier.layerBackdrop(glassBackdrop) else Modifier),
                contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    state = listStates[page],
                    modifier = Modifier.widthIn(max = if (page < 2) 1100.dp else 760.dp).fillMaxSize().background(MiuixTheme.colorScheme.surface)
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                        .padding(horizontal = if (wideLayout) 28.dp else 20.dp),
                    contentPadding = PaddingValues(top = padding.calculateTopPadding() + 16.dp, bottom = padding.calculateBottomPadding() + 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    when (page) {
                        0 -> overviewItems(tasks, config, vm,
                            onDownloads = { selectedPage = 1; downloadFilter = 0 },
                            onCompleted = { selectedPage = 1; downloadFilter = 2 },
                            onAllDownloads = { selectedPage = 1; downloadFilter = 3 },
                            onNew = { showNewDownload = true },
                            onPlugins = { selectedPage = 2 },
                            onTask = { id -> selectedPage = 1; downloadFilter = 3; expandedTask = id
                                search.edit { replace(0, length, "") }; showSearch = false })
                        1 -> {
                            transferItems(tasks, vm, downloadFilter, { downloadFilter = it },
                                showSearch, search, expandedTask,
                                onExpand = { expandedTask = if (expandedTask == it) "" else it },
                                onDelete = { confirmDelete = it }, onNew = { showNewDownload = true })
                        }
                        2 -> pluginItems(config, vm)
                        3 -> settingsItems(config, vm,
                            onOpenAppearance = { if (backStack.size == 1) backStack = backStack + 4 },
                            onOpenAbout = { if (backStack.size == 1) backStack = backStack + 5 },
                            onOpenMirrors = { if (backStack.size == 1) backStack = backStack + 8 },
                            pickTree = { treePicker.launch(null) })
                        4 -> appearanceItems(config, vm)
                        5 -> mirrorItems(config, vm)
                    }
                }
            }
        }
    }
    // 单一弹层宿主跨越导航 entry，对话框不会随转场重复挂载。
    Scaffold { _ ->
        // 与 XBlocker 一致，由 Miuix NavDisplay 处理手势跟随、取消回弹、裁剪和遮罩。
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface),
            entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
            onBack = ::navigateBack,
            entryProvider = entryProvider {
                entry(0) {
                    Row(Modifier.fillMaxSize()) {
                        if (wideLayout) TransferSidebar(selectedPage, pages.take(4), icons,
                            tasks.count { it.state == "待确认" }, onSelect = { selectedPage = it },
                            onNew = { showNewDownload = true })
                        pageContent(selectedPage, Modifier.weight(1f).fillMaxHeight())
                    }
                }
                entry(4) { pageContent(4, Modifier.fillMaxSize()) }
                entry(8) { pageContent(5, Modifier.fillMaxSize()) }
                entry(5) {
                    AboutScreenMiuix(
                        state = AboutUiState(),
                        actions = AboutScreenActions(onBack = ::navigateBack, onOpenLink = { link ->
                            when (link) {
                                "leifetch:licenses" -> if (backStack.last() == 5) backStack = backStack + 6
                                "leifetch:privacy" -> if (backStack.last() == 5) backStack = backStack + 7
                                else -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) }
                            }
                        }),
                        enableBlur = config.blur,
                    )
                }
                entry(6) { AboutDocumentScreen(privacy = false, onBack = ::navigateBack) }
                entry(7) { AboutDocumentScreen(privacy = true, onBack = ::navigateBack) }
            },
        )
        // 在 NavDisplay 之后注册：禁用预测时手势先被消费，完成后正常出栈。
        NavigationBackHandler(
            state = rememberNavigationEventState(NavigationEventInfo.None),
            isBackEnabled = backStack.size > 1 && !usePredictiveBack && confirmDelete.isEmpty(),
            onBackCompleted = ::navigateBack,
        )
        val deleting = tasks.firstOrNull { it.id == confirmDelete }
        NewDownloadDialog(showNewDownload, onDismiss = { showNewDownload = false },
            onAdd = { url -> vm.add(url) { showNewDownload = false; selectedPage = 1; downloadFilter = 0 } })
        val clipLink by vm.clipboardSuggest.collectAsStateWithLifecycle()
        SuperDialog(show = clipLink != null, title = "发现下载链接", onDismissRequest = { vm.clipboardSuggest.value = null }) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("剪贴板中发现了下载链接，是否添加下载任务？", fontSize = 14.sp)
                Text(clipLink.orEmpty(), fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(MiuixTheme.colorScheme.onSurface.copy(alpha = .05f)).padding(10.dp))
                Text("将保存为 ${safeName(Uri.parse(clipLink.orEmpty()).lastPathSegment ?: "")}",
                    fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton("取消", onClick = { vm.clipboardSuggest.value = null }, modifier = Modifier.weight(1f))
                    // 弹窗语义是"是否下载"：确认即入库并立即开始下载，无需再去下载页手动开始。
                    TextButton("下载", enabled = clipLink != null, onClick = {
                        clipLink?.let { url -> vm.add(url) { task ->
                            vm.clipboardSuggest.value = null; selectedPage = 1; downloadFilter = 0; vm.start(task.id)
                        } }
                    }, modifier = Modifier.weight(1f))
                }
            }
        }
        SuperDialog(show = confirmDelete.isNotEmpty(), title = "删除文件？", onDismissRequest = { confirmDelete = "" }) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(deleting?.name.orEmpty(), fontSize = 14.sp)
                Text("同时删除已保存的文件与下载记录。", fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton("取消", onClick = { confirmDelete = "" }, modifier = Modifier.weight(1f))
                    TextButton("删除", onClick = { deleting?.let(vm::delete); confirmDelete = "" }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
    }
}

private val sectionTitleMargin = PaddingValues(horizontal = 16.dp, vertical = 8.dp)

private fun LazyListScope.pluginItems(config: Config, vm: MainViewModel) {
    item {
        Card {
            SuperSwitch(title = "下载接管", summary = "插件运行在 LSPosed 选中的目标应用内",
                checked = config.enabled, onCheckedChange = { v ->
                    vm.edit { it.copy(enabled = v) }
                    // 总开关打开时为已启用的固定作用域插件一次性申请（LSPosed 只弹一次确认）。
                    if (v) vm.requestScope(HookPlugins.scopeRequestFor(config))
                })
        }
    }
    item { SmallTitle("已安装插件", insideMargin = sectionTitleMargin) }
    items(HookPlugins.all, key = { it.id }) { plugin ->
        val on = plugin.id in HookPlugins.enabled(config.plugins)
        var expanded by rememberSaveable(plugin.id) { mutableStateOf(false) }
        Card {
            SuperSwitch(title = plugin.name, summary = plugin.blurb, checked = on,
                onCheckedChange = { value ->
                    vm.edit { old ->
                        val ids = HookPlugins.enabled(old.plugins).toMutableSet()
                        if (value) ids.add(plugin.id) else ids.remove(plugin.id)
                        old.copy(plugins = ids.sorted().joinToString(","))
                    }
                    // 开启带固定作用域的插件时自动向 LSPosed 申请，免去手动勾选。
                    if (value && plugin.packages.isNotEmpty()) vm.requestScope(plugin.packages.toList())
                })
            BasicComponent(title = "应用范围与说明", summary = if (expanded) "收起配置" else "查看支持范围和接入方式",
                onClick = { expanded = !expanded }, endActions = {
                    Icon(if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, null,
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                })
            androidx.compose.animation.AnimatedVisibility(expanded) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(plugin.summary, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Text("内置插件 · v${plugin.version}", fontSize = 12.sp, color = MiuixTheme.colorScheme.primary)
                if (plugin.packages.isNotEmpty()) {
                    Text("LSPosed 作用域：${plugin.packages.joinToString("、")}", fontSize = 12.sp)
                    TextButton("申请 LSPosed 作用域", onClick = { vm.requestScope(plugin.packages.toList()) },
                        modifier = Modifier.fillMaxWidth())
                } else {
                    val packages = rememberTextFieldState(config.packages)
                    TextField(state = packages, label = "应用包名，用空格或逗号分隔", modifier = Modifier.fillMaxWidth())
                    TextButton("保存应用范围", onClick = { vm.edit { it.copy(packages = packages.text.toString()) } }, modifier = Modifier.fillMaxWidth())
                }
            }
            }
        }
    }
    item { Notice("开启插件或总开关时会自动向 LSPosed 申请所需作用域（需已在 LSPosed 启用 LeiFetch）；若弹窗未出现或被拒绝，可展开卡片重新申请，或到 LSPosed 手动勾选并重启目标应用。插件开关仅控制接管规则，不会自动更改 LSPosed 作用域。") }
}

@Composable
private fun Notice(text: String) {
    Text(text, modifier = Modifier.padding(horizontal = 6.dp), fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
}

@Composable
private fun SettingsIcon(icon: ImageVector) {
    Icon(icon, contentDescription = null, modifier = Modifier.padding(end = 6.dp), tint = MiuixTheme.colorScheme.onBackground)
}

private fun LazyListScope.settingsItems(config: Config, vm: MainViewModel, onOpenAppearance: () -> Unit,
                                        onOpenAbout: () -> Unit, onOpenMirrors: () -> Unit, pickTree: () -> Unit) {
    item { SmallTitle("NSFX 下载内核", insideMargin = sectionTitleMargin) }
    item {
        Card {
            OverlaySpinnerPreference(title = "下载线程数", summary = "单任务并行连接数；不支持分段时自动单连接",
                startAction = { SettingsIcon(Icons.Rounded.Download) },
                items = (1..16).map { DropdownItem("$it 线程") }, selectedIndex = config.threads - 1,
                onSelectedIndexChange = { index -> vm.edit { it.copy(threads = index + 1) } })
            OverlaySpinnerPreference(title = "同时下载任务数", summary = "内核参数在下载服务下次启动时生效",
                startAction = { SettingsIcon(Icons.Rounded.CallToAction) },
                items = (1..8).map { DropdownItem("$it 个任务") }, selectedIndex = config.maxTasks - 1,
                onSelectedIndexChange = { index -> vm.edit { it.copy(maxTasks = index + 1) } })
            OverlaySpinnerPreference(title = "全局连接预算", startAction = { SettingsIcon(Icons.Rounded.Language) },
                items = listOf(4, 8, 16, 32, 64).map { DropdownItem("$it 个连接") },
                selectedIndex = listOf(4, 8, 16, 32, 64).indexOf(config.connections).coerceAtLeast(0),
                onSelectedIndexChange = { index -> vm.edit { it.copy(connections = listOf(4, 8, 16, 32, 64)[index]) } })
            SwitchPreference(title = "NSFX 动态拆分", summary = "空闲线程接手缓慢尾段",
                startAction = { SettingsIcon(Icons.Rounded.RocketLaunch) },
                checked = config.dynamic, onCheckedChange = { v -> vm.edit { it.copy(dynamic = v) } })
            OverlaySpinnerPreference(title = "全局速度上限", startAction = { SettingsIcon(Icons.Filled.Update) },
                items = listOf("不限速", "1 MB/s", "5 MB/s", "10 MB/s").map { DropdownItem(it) },
                selectedIndex = listOf(0L, 1048576L, 5242880L, 10485760L).indexOf(config.speedLimit).coerceAtLeast(0),
                onSelectedIndexChange = { index -> vm.edit { it.copy(speedLimit = listOf(0L, 1048576L, 5242880L, 10485760L)[index]) } })
        }
    }
    item { SmallTitle("保存", insideMargin = sectionTitleMargin) }
    item {
        Card {
            ArrowPreference(title = "保存目录", summary = displayTree(config.tree).ifEmpty { "应用内 downloads 目录" },
                startAction = { SettingsIcon(Icons.Rounded.Save) }, onClick = pickTree)
            SuperSwitch(title = "应用内保存", summary = "对新任务生效；卸载应用会删除应用内文件",
                checked = config.tree.isEmpty(), onCheckedChange = { v -> if (v) vm.edit { it.copy(tree = "") } })
        }
    }
    item { SmallTitle("GitHub 加速", insideMargin = sectionTitleMargin) }
    item {
        Card {
            SuperSwitch(title = "镜像加速", summary = "自动识别 GitHub 直链下载，经镜像站转发",
                startAction = { SettingsIcon(Icons.Rounded.Language) },
                checked = config.githubMirror, onCheckedChange = { v -> vm.edit { it.copy(githubMirror = v) } })
            ArrowPreference(title = "镜像站列表", summary = if (config.githubMirrorPick == "auto")
                "自动选择最快 · 共 ${GithubMirrors.effectiveList(config.githubMirrors).size} 个镜像站"
                else config.githubMirrorPick.removePrefix("https://").removePrefix("http://"),
                startAction = { SettingsIcon(Icons.Rounded.Download) }, onClick = onOpenMirrors)
        }
    }
    item { SmallTitle("通知", insideMargin = sectionTitleMargin) }
    item {
        val context = LocalContext.current
        Card {
            SuperSwitch(title = "事件通知", summary = "待确认和下载完成通知；下载前台通知始终保留",
                startAction = { SettingsIcon(Icons.Rounded.NotificationsActive) },
                checked = config.notices, onCheckedChange = { v -> vm.edit { it.copy(notices = v) } })
            SuperSwitch(title = "ColorOS 流体云", summary = "Android 16 实时进度通知，由系统决定呈现",
                startAction = { SettingsIcon(Icons.Rounded.WaterDrop) },
                checked = config.fluid, onCheckedChange = { v -> vm.edit { it.copy(fluid = v) } })
            ArrowPreference(title = "实时通知权限", summary = Notices.diagnostic(context),
                startAction = { SettingsIcon(Icons.Rounded.Troubleshoot) }, onClick = { Notices.openSettings(context) })
            ArrowPreference(title = "电池与后台运行", summary = "按需允许后台活动和自启动",
                startAction = { SettingsIcon(Icons.Rounded.Security) },
                onClick = { context.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$PKG"))) })
        }
    }
    item { SmallTitle("通用", insideMargin = sectionTitleMargin) }
    item {
        Card {
            SuperSwitch(title = "剪贴板识别", summary = "打开应用时发现剪贴板中的下载链接将询问是否下载",
                startAction = { SettingsIcon(Icons.AutoMirrored.Rounded.Rule) },
                checked = config.clipboardDetect, onCheckedChange = { v -> vm.edit { it.copy(clipboardDetect = v) } })
            ArrowPreference(title = "外观", summary = "主题颜色与界面效果",
                startAction = { SettingsIcon(Icons.Rounded.Palette) }, onClick = onOpenAppearance)
            ArrowPreference(title = "关于", summary = "LeiFetch ${BuildConfig.VERSION_NAME} · bileizhen",
                startAction = { SettingsIcon(Icons.Rounded.ContactPage) }, onClick = onOpenAbout)
        }
    }
}

// 移植自 XBlocker 外观页（SukiSU-Ultra ColorPalette）：预览卡 + 分组开关 + 缩放。
private fun LazyListScope.appearanceItems(config: Config, vm: MainViewModel) {
    item {
        val monet = config.colorMode >= 3
        Spacer(Modifier.height(12.dp))
        ThemePreviewCardMiuix(
            isDark = LocalDarkTheme.current, miuixMonet = monet,
            enableFloatingBottomBar = config.floatingBar,
            enableFloatingBottomBarBlur = config.liquidGlass && config.blur && Build.VERSION.SDK_INT >= 33,
        )
        Spacer(Modifier.height(28.dp))
        TabRow(
            tabs = listOf("跟随系统", "浅色", "深色"),
            selectedTabIndex = config.colorMode % 3,
            onTabSelected = { index -> vm.edit { it.copy(colorMode = index + if (monet) 3 else 0) } },
            height = 48.dp,
        )
        Card(Modifier.padding(top = 12.dp).fillMaxWidth()) {
            SuperSwitch(
                title = "Monet 动态颜色", checked = monet,
                enabled = Build.VERSION.SDK_INT >= 31,
                summary = if (Build.VERSION.SDK_INT < 31) "需要 Android 12 或更高版本" else null,
                startAction = { PreferenceIcon(Icons.Rounded.Wallpaper) },
                onCheckedChange = { on -> vm.edit { it.copy(colorMode = config.colorMode % 3 + if (on) 3 else 0) } },
            )
        }
        Card(Modifier.padding(top = 12.dp).fillMaxWidth()) {
            SuperSwitch(
                title = "模糊", summary = if (Build.VERSION.SDK_INT >= 33) "模糊顶栏和底栏背景" else "需要 Android 13 或更高版本",
                checked = config.blur, enabled = Build.VERSION.SDK_INT >= 33,
                startAction = { PreferenceIcon(Icons.Rounded.BlurOn) },
                onCheckedChange = { v -> vm.edit { it.copy(blur = v) } },
            )
            SuperSwitch(
                title = "悬浮底栏", summary = "仪表盘、下载、插件与设置",
                checked = config.floatingBar,
                startAction = { PreferenceIcon(Icons.Rounded.CallToAction) },
                onCheckedChange = { v -> vm.edit { it.copy(floatingBar = v) } },
            )
            SuperSwitch(
                title = "液态玻璃", summary = "为悬浮底栏应用液态玻璃效果",
                checked = config.liquidGlass, enabled = config.floatingBar && config.blur && Build.VERSION.SDK_INT >= 33,
                startAction = { PreferenceIcon(Icons.Rounded.WaterDrop) },
                onCheckedChange = { v -> vm.edit { it.copy(liquidGlass = v) } },
            )
        }
        Card(Modifier.padding(top = 12.dp).fillMaxWidth()) {
            SuperSwitch(
                title = "预测性返回手势", summary = "启用预测性返回手势支持",
                checked = config.predictiveBack, enabled = Build.VERSION.SDK_INT >= 34,
                startAction = { PreferenceIcon(Icons.AutoMirrored.Rounded.MenuOpen) },
                onCheckedChange = { v -> vm.edit { it.copy(predictiveBack = v) } },
            )
            var sliderValue by remember(config.scale) { mutableFloatStateOf(config.scale) }
            var showScaleDialog by rememberSaveable { mutableStateOf(false) }
            BasicComponent(
                title = "显示缩放", summary = "调整界面整体缩放",
                startAction = { PreferenceIcon(Icons.Rounded.AspectRatio) },
                endActions = {
                    Text("${(sliderValue * 100).roundToInt()}%", color = MiuixTheme.colorScheme.onSurfaceVariantActions)
                    Icon(Icons.Rounded.ChevronRight, null, tint = MiuixTheme.colorScheme.onSurfaceVariantActions)
                },
                onClick = { showScaleDialog = true },
                bottomAction = {
                    Slider(
                        value = sliderValue, onValueChange = { sliderValue = it },
                        onValueChangeFinished = { vm.edit { it.copy(scale = sliderValue) } },
                        valueRange = 0.8f..1.1f, showKeyPoints = true,
                        keyPoints = listOf(0.8f, 0.9f, 1f, 1.1f), magnetThreshold = 0.01f,
                        hapticEffect = SliderDefaults.SliderHapticEffect.Step,
                    )
                },
            )
            SuperDialog(show = showScaleDialog, title = "显示缩放", summary = "80% - 110%", onDismissRequest = { showScaleDialog = false }) {
                var input by remember(showScaleDialog) { mutableStateOf((config.scale * 100).roundToInt().toString()) }
                TextField(value = input, onValueChange = { if (it.length <= 3 && it.all(Char::isDigit)) input = it }, singleLine = true)
                Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton("取消", onClick = { showScaleDialog = false }, modifier = Modifier.weight(1f))
                    TextButton("确定", enabled = input.toIntOrNull() in 80..110, onClick = {
                        input.toIntOrNull()?.let { value -> vm.edit { it.copy(scale = value.coerceIn(80, 110) / 100f) } }
                        showScaleDialog = false
                    }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
    if (Build.VERSION.SDK_INT < 33) item { Notice("模糊与液态玻璃需要 Android 13 或更高版本；当前使用普通悬浮底栏。") }
}

@Composable
private fun PreferenceIcon(icon: ImageVector) {
    Icon(icon, contentDescription = null, modifier = Modifier.padding(end = 6.dp), tint = MiuixTheme.colorScheme.onSurface)
}

// 移植自 XBlocker 主题预览卡（SukiSU-Ultra）：手机比例边框预览，LeiFetch 四个标签。
@Composable
private fun ThemePreviewCardMiuix(
    isDark: Boolean,
    miuixMonet: Boolean,
    enableFloatingBottomBar: Boolean = false,
    enableFloatingBottomBarBlur: Boolean = false,
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.toFloat()
    val screenHeight = configuration.screenHeightDp.toFloat()
    val screenRatio = (screenWidth / screenHeight).coerceIn(0.42f, 0.55f)

    val bgColor = MiuixTheme.colorScheme.surface
    val textColor = MiuixTheme.colorScheme.onBackground
    val accentCardColor = when {
        miuixMonet -> MiuixTheme.colorScheme.secondaryContainer
        isDark -> Color(0xFF1A3825)
        else -> Color(0xFFDFFAE4)
    }
    val cardColor = if (miuixMonet) MiuixTheme.colorScheme.surfaceContainerHighest else MiuixTheme.colorScheme.surfaceVariant
    val navBarColor = if (miuixMonet) MiuixTheme.colorScheme.surfaceContainer else MiuixTheme.colorScheme.surface
    val iconColor = MiuixTheme.colorScheme.primary
    val navSelectedColor = MiuixTheme.colorScheme.onSurfaceContainer
    val navUnselectedColor = MiuixTheme.colorScheme.onSurfaceContainer.copy(alpha = 0.5f)

    Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.TopCenter) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.4f)
                .aspectRatio(screenRatio)
                .clip(RoundedCornerShape(20.dp))
                .background(bgColor)
                .border(1.dp, MiuixTheme.colorScheme.outline, RoundedCornerShape(20.dp))
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .height(48.dp)
                        .fillMaxWidth()
                        .padding(start = 12.dp, top = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LeiFetch",
                        fontSize = 12.sp,
                        color = textColor
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(65.dp)
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(6.dp))
                            .background(accentCardColor)
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(cardColor)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(cardColor)
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.8f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(cardColor)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(.1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(cardColor)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(.1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(cardColor)
                    )
                }
            }

            if (enableFloatingBottomBar) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (enableFloatingBottomBarBlur) navBarColor.copy(alpha = 0.5f)
                                else navBarColor
                            )
                            .border(0.5.dp, textColor.copy(alpha = 0.1f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(4) {
                            Box(
                                modifier = Modifier
                                    .size(13.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(if (it == 0) iconColor else textColor)
                            )
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(textColor.copy(alpha = 0.1f))
                    )
                    Row(
                        modifier = Modifier
                            .height(36.dp)
                            .fillMaxWidth()
                            .background(navBarColor)
                            .padding(top = 2.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(4) {
                            Box(
                                modifier = Modifier
                                    .size(15.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (it == 0) navSelectedColor else navUnselectedColor)
                            )
                        }
                    }
                }
            }
        }
    }
}
