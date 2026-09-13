// Ported from XBlocker (same author, used with permission); original About screen
// adapted from SukiSU-Ultra v4.1.3 (0ca744a), GPL-3.0. See THIRD_PARTY_NOTICES.md.
// LeiFetch branding, local links, hardcoded Chinese strings.
package io.github.bileizhen.leifetch.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.LruCache
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bileizhen.leifetch.BuildConfig
import io.github.bileizhen.leifetch.R
import io.github.bileizhen.leifetch.ui.component.miuix.effect.BgEffectBackground
import io.github.bileizhen.leifetch.ui.component.miuix.effect.ColorBlendToken
import io.github.bileizhen.leifetch.ui.theme.LocalDarkTheme
import io.github.bileizhen.leifetch.ui.util.BlurredBar
import io.github.bileizhen.leifetch.ui.util.rememberBlurBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.flow.onEach
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import androidx.compose.ui.graphics.BlendMode as ComposeBlendMode

@Immutable
data class AboutLink(val fullText: String, val url: String)

@Immutable
class AboutUiState {
    val title: String = "关于"
    val appName: String = "LeiFetch"
    val versionName: String = "v${BuildConfig.VERSION_NAME}"
    val links: List<AboutLink> = listOf(
        AboutLink("GitHub", "https://github.com/bileizhen/LeiFetch"),
        AboutLink("NSFX 下载核心", "https://github.com/buaoyezz/Hanabi-Download-Manager-X"),
        AboutLink("QQ 交流群", "https://qm.qq.com/q/ljvhVqrXCU"),
        AboutLink("开源许可", "leifetch:licenses"),
        AboutLink("隐私", "leifetch:privacy"),
    )
}

@Immutable
data class AboutScreenActions(val onBack: () -> Unit, val onOpenLink: (String) -> Unit)

@Composable
fun AboutScreenMiuix(
    state: AboutUiState,
    actions: AboutScreenActions,
    enableBlur: Boolean,
) {
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val lazyListState = rememberLazyListState()
    var logoHeightPx by remember { mutableIntStateOf(0) }

    val scrollProgress by remember {
        derivedStateOf {
            if (logoHeightPx <= 0) {
                0f
            } else {
                val index = lazyListState.firstVisibleItemIndex
                val offset = lazyListState.firstVisibleItemScrollOffset
                if (index > 0) 1f else (offset.toFloat() / logoHeightPx).coerceIn(0f, 1f)
            }
        }
    }

    val barBlurBackdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = barBlurBackdrop != null && scrollProgress == 1f
    val barColor = if (blurActive) {
        Color.Transparent
    } else {
        if (scrollProgress == 1f) colorScheme.surface else Color.Transparent
    }

    Scaffold(
        topBar = {
            BlurredBar(backdrop = barBlurBackdrop, blurActive = blurActive) {
                SmallTopAppBar(
                    title = state.title,
                    scrollBehavior = topAppBarScrollBehavior,
                    color = barColor,
                    titleColor = colorScheme.onSurface.copy(
                        alpha = ((scrollProgress - 0.35f) / 0.65f).coerceIn(0f, 1f),
                    ),
                    defaultWindowInsetsPadding = true,
                    navigationIcon = {
                        IconButton(
                            onClick = actions.onBack
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "返回",
                                tint = colorScheme.onBackground
                            )
                        }
                    },
                )
            }
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(modifier = if (barBlurBackdrop != null) Modifier.layerBackdrop(barBlurBackdrop) else Modifier) {
            AboutContent(
                state = state,
                actions = actions,
                innerPadding = innerPadding,
                topAppBarScrollBehavior = topAppBarScrollBehavior,
                lazyListState = lazyListState,
                scrollProgress = scrollProgress,
                onLogoHeightChanged = { logoHeightPx = it },
                enableBlur = enableBlur,
            )
        }
    }
}

@Composable
private fun AboutContent(
    state: AboutUiState,
    actions: AboutScreenActions,
    enableBlur: Boolean,
    innerPadding: PaddingValues,
    topAppBarScrollBehavior: ScrollBehavior,
    lazyListState: LazyListState,
    scrollProgress: Float,
    onLogoHeightChanged: (Int) -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current

    val backdrop = rememberLayerBackdrop()
    val blurEnabled = enableBlur && Build.VERSION.SDK_INT >= 33 && isRuntimeShaderSupported()

    val isInDark = LocalDarkTheme.current
    val effectBackground =
        remember(enableBlur) { isRuntimeShaderSupported() && enableBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM }

    val blendColors = remember(isInDark) {
        if (isInDark) ColorBlendToken.Overlay_Thin_Light
        else ColorBlendToken.Pured_Regular_Light
    }
    val logoBlend = remember(isInDark) {
        if (isInDark) {
            listOf(
                BlendColorEntry(Color(0xe6a1a1a1), BlurBlendMode.ColorDodge),
                BlendColorEntry(Color(0x4de6e6e6), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xff1af500), BlurBlendMode.Lab),
            )
        } else {
            listOf(
                BlendColorEntry(Color(0xcc4a4a4a), BlurBlendMode.ColorBurn),
                BlendColorEntry(Color(0xff4f4f4f), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xff1af200), BlurBlendMode.Lab),
            )
        }
    }

    // Logo parallax/fade tracking
    var logoHeightDp by remember { mutableStateOf(300.dp) }
    var logoAreaY by remember { mutableFloatStateOf(0f) }
    var iconY by remember { mutableFloatStateOf(0f) }
    var projectNameY by remember { mutableFloatStateOf(0f) }
    var versionCodeY by remember { mutableFloatStateOf(0f) }

    var iconProgress by remember { mutableFloatStateOf(0f) }
    var projectNameProgress by remember { mutableFloatStateOf(0f) }
    var versionCodeProgress by remember { mutableFloatStateOf(0f) }
    var initialLogoAreaY by remember { mutableFloatStateOf(0f) }

    // 点开的名单成员（null 表示弹窗关闭）；shownFocus 保留最后一次选择，供退场动画期间渲染。
    var selected by remember { mutableStateOf<MemberFocus?>(null) }
    val shownFocus = remember { mutableStateOf<MemberFocus?>(null) }
    LaunchedEffect(selected) { selected?.let { shownFocus.value = it } }

    LaunchedEffect(lazyListState) {
        snapshotFlow { lazyListState.firstVisibleItemScrollOffset }
            .onEach { offset ->
                if (lazyListState.firstVisibleItemIndex > 0) {
                    if (iconProgress != 1f) iconProgress = 1f
                    if (projectNameProgress != 1f) projectNameProgress = 1f
                    if (versionCodeProgress != 1f) versionCodeProgress = 1f
                    return@onEach
                }

                if (initialLogoAreaY == 0f && logoAreaY > 0f) {
                    initialLogoAreaY = logoAreaY
                }
                val refLogoAreaY = if (initialLogoAreaY > 0f) initialLogoAreaY else logoAreaY

                val stage1TotalLength = refLogoAreaY - versionCodeY
                val stage2TotalLength = versionCodeY - projectNameY
                val stage3TotalLength = projectNameY - iconY

                val versionCodeDelay = stage1TotalLength * 0.5f
                versionCodeProgress = ((offset.toFloat() - versionCodeDelay) / (stage1TotalLength - versionCodeDelay).coerceAtLeast(1f))
                    .coerceIn(0f, 1f)
                projectNameProgress = ((offset.toFloat() - stage1TotalLength) / stage2TotalLength.coerceAtLeast(1f))
                    .coerceIn(0f, 1f)
                iconProgress = ((offset.toFloat() - stage1TotalLength - stage2TotalLength) / stage3TotalLength.coerceAtLeast(1f))
                    .coerceIn(0f, 1f)
            }
            .collect { }
    }

    val scrollPadding = PaddingValues(
        top = innerPadding.calculateTopPadding(),
        start = innerPadding.calculateStartPadding(layoutDirection),
        end = innerPadding.calculateEndPadding(layoutDirection),
    )
    val logoPadding = PaddingValues(
        top = innerPadding.calculateTopPadding() + 40.dp,
        start = innerPadding.calculateStartPadding(layoutDirection),
        end = innerPadding.calculateEndPadding(layoutDirection),
    )

    BgEffectBackground(
        dynamicBackground = effectBackground,
        modifier = Modifier.fillMaxSize(),
        bgModifier = Modifier.layerBackdrop(backdrop),
        isFullSize = true,
        effectBackground = effectBackground,
        alpha = { 1f - scrollProgress },
    ) {
        // Logo area
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = logoPadding.calculateTopPadding() + 52.dp,
                    start = logoPadding.calculateStartPadding(layoutDirection),
                    end = logoPadding.calculateEndPadding(layoutDirection),
                )
                .onSizeChanged { size ->
                    with(density) { logoHeightDp = size.height.toDp() }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(100.dp)
                    .clipToBounds()
                    .graphicsLayer {
                        alpha = 1 - iconProgress
                        scaleX = 1 - (iconProgress * 0.05f)
                        scaleY = 1 - (iconProgress * 0.05f)
                    }
                    .onGloballyPositioned { coordinates ->
                        if (iconY != 0f) return@onGloballyPositioned
                        val y = coordinates.positionInWindow().y
                        val size = coordinates.size
                        iconY = y + size.height
                    },
            ) {
                Image(
                    modifier = Modifier
                        .requiredSize(100.dp)
                        .then(
                            if (blurEnabled) {
                                Modifier.textureBlur(
                                    backdrop = backdrop,
                                    shape = RoundedCornerShape(0.dp),
                                    blurRadius = 150f,
                                    colors = BlurColors(blendColors = logoBlend),
                                    contentBlendMode = ComposeBlendMode.DstIn,
                                    enabled = true,
                                )
                            } else Modifier
                        ),
                    painter = painterResource(id = R.drawable.ic_logo),
                    colorFilter = ColorFilter.tint(colorScheme.onBackground),
                    contentDescription = null,
                )
            }
            Text(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 5.dp)
                    .onGloballyPositioned { coordinates ->
                        if (projectNameY != 0f) return@onGloballyPositioned
                        val y = coordinates.positionInWindow().y
                        val size = coordinates.size
                        projectNameY = y + size.height
                    }
                    .graphicsLayer {
                        alpha = 1 - projectNameProgress
                        scaleX = 1 - (projectNameProgress * 0.05f)
                        scaleY = 1 - (projectNameProgress * 0.05f)
                    }
                    .then(
                        if (blurEnabled) {
                            Modifier.textureBlur(
                                backdrop = backdrop,
                                shape = RoundedCornerShape(0.dp),
                                blurRadius = 150f,
                                colors = BlurColors(blendColors = logoBlend),
                                contentBlendMode = ComposeBlendMode.DstIn,
                                enabled = true,
                            )
                        } else Modifier
                    ),
                text = state.appName,
                color = colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                fontSize = 35.sp,
            )
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = 1 - versionCodeProgress
                        scaleX = 1 - (versionCodeProgress * 0.05f)
                        scaleY = 1 - (versionCodeProgress * 0.05f)
                    }
                    .onGloballyPositioned { coordinates ->
                        if (versionCodeY != 0f) return@onGloballyPositioned
                        val y = coordinates.positionInWindow().y
                        val size = coordinates.size
                        versionCodeY = y + size.height
                    },
                color = colorScheme.onSurfaceVariantSummary,
                text = state.versionName,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
        }

        // Scrollable content
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = scrollPadding.calculateTopPadding(),
                start = scrollPadding.calculateStartPadding(layoutDirection),
                end = scrollPadding.calculateEndPadding(layoutDirection),
            ),
            overscrollEffect = null,
        ) {
            // Transparent spacer matching logo height
            item(key = "logoSpacer") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(
                            logoHeightDp + 52.dp + logoPadding.calculateTopPadding() - scrollPadding.calculateTopPadding() + 126.dp,
                        )
                        .onSizeChanged { size ->
                            onLogoHeightChanged(size.height)
                        }
                        .onGloballyPositioned { coordinates ->
                            val y = coordinates.positionInWindow().y
                            val size = coordinates.size
                            logoAreaY = y + size.height
                        },
                    contentAlignment = Alignment.TopCenter,
                    content = { },
                )
            }

            // 链接卡与名单同页连续排布；名单每条按 ReactBits AnimatedList 的节奏弹入。
            val memberCard: @Composable (TeamMember, String) -> Unit = { member, group ->
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .fillMaxWidth()
                        .then(
                            if (blurEnabled) {
                                Modifier.textureBlur(
                                    backdrop = backdrop,
                                    shape = RoundedCornerShape(16.dp),
                                    blurRadius = 60f,
                                    colors = BlurColors(blendColors = blendColors),
                                    enabled = true,
                                )
                            } else Modifier
                        ),
                    colors = CardDefaults.defaultColors(
                        if (blurEnabled) Color.Transparent else colorScheme.surfaceContainer,
                        Color.Transparent,
                    ),
                ) {
                    MemberRow(member, onClick = { selected = MemberFocus(member, group) })
                }
            }
            item(key = "about") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = innerPadding.calculateBottomPadding() + 12.dp),
                ) {
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .then(
                                if (blurEnabled) {
                                    Modifier.textureBlur(
                                        backdrop = backdrop,
                                        shape = RoundedCornerShape(16.dp),
                                        blurRadius = 60f,
                                        colors = BlurColors(blendColors = blendColors),
                                        enabled = true,
                                    )
                                } else Modifier
                            ),
                        colors = CardDefaults.defaultColors(
                            if (blurEnabled) Color.Transparent else colorScheme.surfaceContainer,
                            Color.Transparent,
                        ),
                    ) {
                        state.links.forEach {
                            ArrowPreference(
                                title = it.fullText,
                                onClick = {
                                    actions.onOpenLink(it.url)
                                }
                            )
                        }
                    }
                    teamSections.forEachIndexed { sectionIndex, section ->
                        // 首个分组的标题正好落在首屏下沿、只露出半截；多留一段空白把它整体压到屏幕外。
                        Spacer(Modifier.height(if (sectionIndex == 0) 40.dp else 18.dp))
                        SmallTitle(section.title, insideMargin = PaddingValues(horizontal = 20.dp, vertical = 8.dp))
                        section.members.forEachIndexed { index, member ->
                            AnimatedListItem(listState = lazyListState, hostKey = "about") {
                                Column {
                                    memberCard(member, section.title)
                                    if (index < section.members.lastIndex) Spacer(Modifier.height(10.dp))
                                }
                            }
                        }
                    }
                    Spacer(
                        Modifier.height(
                            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                                    WindowInsets.captionBar.asPaddingValues().calculateBottomPadding()
                        )
                    )
                }
            }
        }

        MemberDetailDialog(show = selected != null, focus = shownFocus.value, onDismiss = { selected = null })
    }
}

/** 发行物里实际包含的开源组件；仅测试期依赖与仅编译期引用的 API 也列出来，免得被误认为随包分发。 */
private val thirdPartyComponents = listOf(
    "NSFX 下载内核" to "GPL-3.0 · Hanabi-Download-Manager-X 的 Kotlin 移植",
    "界面壳与液态玻璃" to "GPL-3.0 · 移植自 SukiSU-Ultra 与 XBlocker",
    "Miuix" to "Apache-2.0 · Compose 组件、模糊与动画",
    "AndroidX" to "Apache-2.0 · Compose、Navigation3、DataStore、Activity",
    "Kotlin / kotlinx.coroutines" to "Apache-2.0 · 语言与协程运行时",
    "Material Icons" to "Apache-2.0 · 部分矢量源文件",
    "RemotePreferences" to "Apache-2.0 · 跨进程只读偏好",
    "libxposed / Xposed API" to "Apache-2.0 · 仅编译期引用",
    "OkHttp / MockWebServer" to "Apache-2.0 · 仅测试使用",
)

private fun licenses() = listOf(
    "第三方来源与修改" to "THIRD_PARTY_NOTICES.md",
    "GNU GPL v3" to "licenses/GPL-3.0.txt",
    "Miuix / AndroidX · Apache 2.0" to "licenses/Apache-2.0.txt",
)

// 开发组与贡献者（内测）名单：内容硬编码，头像按 QQ 号从腾讯头像 CDN 加载。
// detail 只写给需要补充贡献说明的成员，详情弹窗里有则多渲染一段。
private data class TeamMember(
    val qq: String,
    val name: String,
    val role: String,
    val detail: String? = null,
)

/** 名单分组；分组标题随成员一起记下，详情弹窗里显示所属分组。 */
private data class TeamSection(val title: String, val members: List<TeamMember>)

/** 详情弹窗的当前对象：成员 + 来自哪个分组。 */
private data class MemberFocus(val member: TeamMember, val group: String)

private val teamMembers = listOf(
    TeamMember(
        qq = "3140014249",
        name = "bileizhen",
        role = "开发 · 设计 · 维护",
        detail = listOf(
            "LeiFetch 的作者与技术设计者：从 NSFX 下载内核的移植到整套界面都由他独立完成，" +
                    "并负责长期的设计、维护与发布。",
            "",
            "· 内核：NSFX 内核 Kotlin 移植，16 线程、动态尾段拆分、断点跨会话续传、指数退避",
            "· 接管：LSPosed 通用捕获（DownloadManager / OkHttp / WebView）、Firefox 适配、系统下载器插件",
            "· 加速：GitHub 镜像前置转发，用下载地址并行测速，校验 206 与文件总长后择优",
            "· 界面：Miuix + Compose 全套 UI 与动效、分段点阵与速度曲线、实时卡片",
            "· 双入口：legacy Xposed 93 与 libxposed API 101，作用域自动申请；数据只存本机，无遥测",
        ).joinToString("\n"),
    ),
    TeamMember(
        qq = "2468872022",
        name = "LinYe_2804",
        role = "开发 · 图标绘制",
        detail = listOf(
            "对 LeiFetch 现用的 NSFX 下载内核做了大量优化与修改：把 NeoNSF 的功能迁移进来，为内核新增了这些特性。",
            "",
            "· 断点续传：合法的 Last-Modified 也参与安全分段与续传，仍优先强 ETag，兼容旧版仅存 ETag 的断点日志",
            "· 大小捕获：捕获 Firefox、WebView、OkHttp、HttpURLConnection 提供的文件大小，重复捕获任务时用后续结果补全已有任务",
            "· 小文件直连：已知小于 8 MiB 的文件直接单连接下载，省去一次 Range: bytes=0-0 探测；大小提示失效时自动清理临时数据并回退完整探测流程",
            "· 连接复用：完整读取响应后保留 HTTP 连接复用，异常、中断或未读完时仍主动断开，大幅提升下载连接稳定性",
            "",
            "全部改动只作功能性拓展与性能、体验优化，原有的动态尾部分片、全局连接预算、限速、重定向安全与原子检查点机制均保留。",
        ).joinToString("\n"),
    ),
    TeamMember(
        qq = "2536843865",
        name = "Hutao_felicity",
        role = "镜像站",
        detail = "本应用内置的 GitHub 下载加速镜像站 ghfile.geekertao.top 与 gh.dpik.top，均由他搭建。",
    ),
)

private val betaTesters = listOf(
    TeamMember("617498164", "ShiraM1zu", "内测用户"),
    TeamMember("2183396164", "加藤糊", "内测用户"),
    TeamMember("442259851", "KelierAndes", "内测用户"),
    TeamMember("3022513812", "DiceSKY", "内测用户"),
    TeamMember("165658800", "Matsuri", "内测用户"),
    TeamMember("1945826346", "Rcst20", "内测用户"),
    TeamMember("2070526365", "zyemmmm", "内测用户"),
)

private val teamSections = listOf(
    TeamSection("开发组", teamMembers),
    TeamSection("贡献者 · 内测", betaTesters),
)

/** QQ 头像内存缓存；关于页每次打开最多加载几张头像，无需落盘。
 *  不同网络下各端点可用性不一（实测 headimg_dl 在部分网络 400），按序回退。
 *  缓存键带规格：列表用 100，详情弹窗用 640。 */
private object QqAvatarCache {
    private val cache = object : LruCache<String, Bitmap>(24) {
        override fun sizeOf(key: String, value: Bitmap) = 1
    }
    private val endpoints = listOf(
        "https://q1.qlogo.cn/g?b=qq&nk=%s&s=%d",
        "https://thirdqq.qlogo.cn/g?b=qq&nk=%s&s=%d",
        "https://q1.qlogo.cn/headimg_dl?dstuin=%s&spec=%d",
    )
    suspend fun load(qq: String, spec: Int = 100): Bitmap? {
        val key = "$qq@$spec"
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            endpoints.firstNotNullOfOrNull { pattern ->
                runCatching {
                    val connection = URL(pattern.format(qq, spec)).openConnection() as HttpURLConnection
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000
                    connection.setRequestProperty("User-Agent",
                        "Mozilla/5.0 (Linux; Android 16) LeiFetch/1.0")
                    try {
                        BitmapFactory.decodeStream(connection.inputStream)?.also { cache.put(key, it) }
                    } finally {
                        connection.disconnect()
                    }
                }.getOrNull()
            }
        }
    }
}

@Composable
private fun QqAvatar(qq: String, size: Dp = 44.dp, spec: Int = 100, modifier: Modifier = Modifier) {
    val bitmap by produceState<Bitmap?>(null, qq, spec) { value = QqAvatarCache.load(qq, spec) }
    Box(modifier.size(size).clip(CircleShape)
        .background(colorScheme.onSurface.copy(alpha = 0.06f)), contentAlignment = Alignment.Center) {
        val loaded = bitmap
        if (loaded != null) Image(loaded.asImageBitmap(), contentDescription = null,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Text(qq.takeLast(2), fontSize = (size.value * 0.27f).sp, color = colorScheme.onSurfaceVariantSummary)
    }
}

@Composable
private fun MemberRow(member: TeamMember, onClick: () -> Unit) {
    BasicComponent(
        title = member.name,
        summary = member.role,
        startAction = { QqAvatar(member.qq, modifier = Modifier.padding(end = 6.dp)) },
        endActions = {
            // 与下方链接行同一箭头，提示这一行可以点开。
            Image(
                imageVector = MiuixIcons.Basic.ArrowRight,
                contentDescription = null,
                colorFilter = ColorFilter.tint(colorScheme.onSurfaceVariantActions),
                modifier = Modifier.size(width = 10.dp, height = 16.dp).align(Alignment.CenterVertically),
            )
        },
        onClick = onClick,
    )
}

/** ReactBits AnimatedList 的条目动效：条目自身超过一半进入屏幕后，从 scale 0.7 / 全透明
 *  淡入到 1；离开视口复位，再滚回来会重播（对应其 useInView 的 amount 0.5 与 once: false）。
 *  每条延时都是固定的 100ms，靠条目先后越过一半视口自然错开，而不是按序号累加延时。
 *
 *  可见性不能用 onGloballyPositioned 判断：滚动时 LazyColumn 只重新摆放已测量的条目、
 *  不会重新布局，位置回调不再触发，屏幕外的条目会永远停在透明态。所以位置回调只用来记下
 *  条目在宿主列表项内的偏移与自身高度（滚动中都是定值），可见比例改由滚动信息实时计算。 */
@Composable
private fun AnimatedListItem(
    listState: LazyListState,
    hostKey: String,
    content: @Composable () -> Unit,
) {
    var offsetInHost by remember { mutableStateOf(0f) }
    var heightPx by remember { mutableStateOf(0f) }

    val inView by remember(listState, hostKey) {
        derivedStateOf {
            val layout = listState.layoutInfo
            val host = layout.visibleItemsInfo.firstOrNull { it.key == hostKey }
            if (host == null || heightPx <= 0f) {
                false
            } else {
                val top = host.offset + offsetInHost
                val visible = (top + heightPx).coerceAtMost(layout.viewportEndOffset.toFloat()) -
                        top.coerceAtLeast(layout.viewportStartOffset.toFloat())
                visible >= heightPx * 0.5f
            }
        }
    }

    Box(
        Modifier.onGloballyPositioned { coordinates ->
            offsetInHost = coordinates.positionInParent().y
            heightPx = coordinates.size.height.toFloat()
        }
    ) {
        val progress by animateFloatAsState(
            targetValue = if (inView) 1f else 0f,
            animationSpec = tween(
                durationMillis = 200,
                delayMillis = 100,
                easing = FastOutSlowInEasing,
            ),
            label = "animatedListItem",
        )
        Box(
            Modifier.graphicsLayer {
                alpha = progress
                val scale = 0.7f + 0.3f * progress
                scaleX = scale
                scaleY = scale
            }
        ) { content() }
    }
}

/** 成员详情弹窗：头像、昵称、所属分组与分工；有 detail 的成员再补一段贡献说明。
 *  show 与 focus 分开传：关闭后 focus 仍保留最后一次选择，供退场动画期间继续渲染内容。 */
@Composable
private fun MemberDetailDialog(show: Boolean, focus: MemberFocus?, onDismiss: () -> Unit) {
    OverlayDialog(show = show, title = "成员信息", onDismissRequest = onDismiss) {
        focus?.let { current ->
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                QqAvatar(current.member.qq, size = 76.dp, spec = 640)
                Text(current.member.name, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 12.dp))
                Text(current.group, fontSize = 13.sp, color = colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = 4.dp))
                HorizontalDivider(Modifier.padding(vertical = 16.dp),
                    color = colorScheme.onSurface.copy(alpha = 0.08f))
                MemberInfoRow("分工", current.member.role)
            }
            current.member.detail?.let { detail ->
                // 详情可能有多行（作者的贡献说明是分条的），限高后可滚动，不挤走下面的关闭按钮。
                Text(detail, fontSize = 13.sp, lineHeight = 21.sp,
                    color = colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(top = 12.dp))
            }
            TextButton("关闭", onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp))
        }
    }
}

@Composable
private fun MemberInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(label, fontSize = 13.sp, color = colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.width(48.dp))
        Text(value, fontSize = 13.sp, modifier = Modifier.weight(1f))
    }
}


@Composable
internal fun AboutDocumentScreen(privacy: Boolean, onBack: () -> Unit) {
    var selected by rememberSaveable { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current
    val licenses = licenses()
    val document by produceState("", selected) {
        value = if (selected.isEmpty()) "" else withContext(Dispatchers.IO) {
            runCatching { context.assets.open(selected).bufferedReader().use { it.readText() } }
                .getOrElse { "无法读取许可证文件。" }
        }
    }
    Scaffold(topBar = {
        SmallTopAppBar(title = if (privacy) "隐私" else "开源许可", navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") }
        })
    }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        ) {
            if (privacy) {
                item { Card { BasicComponent(title = "本地处理", summary = "下载任务、进度与历史只存在本机的数据库与偏好里；没有账号体系，不上传任何数据，卸载即清除。") } }
                item { Card { BasicComponent(title = "网络访问", summary = "只连接你要下载的目标服务器，以及接管前的 1 字节 Range 校验探测；没有遥测与统计上报。") } }
                item { Card { BasicComponent(title = "GitHub 镜像", summary = "开启镜像加速后，该任务的 GitHub 地址会经所选镜像站转发；测速只请求 Range 首字节，不消费文件正文。可在设置中关闭。") } }
                item { Card { BasicComponent(title = "剪贴板", summary = "开启剪贴板识别后，仅在你打开应用时读取一次剪贴板文本用于识别下载链接；命中的链接只在本地记录用于去重，不保存剪贴板原文、不上传。") } }
                item { Card { BasicComponent(title = "凭据与日志", summary = "捕获到的 Cookie / Authorization 等请求头只用于对应任务的下载并随任务本地保存，取消接管后清除；日志只记回退原因，不记录可能含凭据的完整下载 URL。") } }
                item { Card { BasicComponent(title = "关于页头像", summary = "开发组与贡献者名单的头像按 QQ 号从腾讯头像 CDN 加载，仅用于展示；除此之外不发起其它请求。") } }
                item { Card { BasicComponent(title = "通知与流体云", summary = "用于待确认、下载进度与完成提醒；完成卡片提供打开、分享动作。") } }
                item { Card { BasicComponent(title = "LSPosed 作用域", summary = "仅在手动勾选的应用内捕获下载；Firefox 接管前独立探测 ETag 一致，失败时保留浏览器自身下载。") } }
                item { Card { BasicComponent(title = "存储", summary = "文件只写入你选择的目录；应用内目录的文件随卸载删除。") } }
            } else {
                item { Card { BasicComponent(title = "LeiFetch", summary = "NSFX 内核 Kotlin 移植、Miuix 界面与 LSPosed 捕获的组合；本项目以 GPL-3.0 发布，完整源码与构建文件随发行版提供。") } }
                item { SmallTitle("主要开源组件", insideMargin = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) }
                item { Card { thirdPartyComponents.forEach { (name, note) -> BasicComponent(title = name, summary = note) } } }
                item { SmallTitle("许可证全文", insideMargin = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) }
                item { Card { licenses.forEach { (title, asset) -> ArrowPreference(title = title, onClick = { selected = asset }) } } }
            }
        }
        top.yukonga.miuix.kmp.overlay.OverlayDialog(show = selected.isNotEmpty(), title = licenses.firstOrNull { it.second == selected }?.first ?: "开源许可", onDismissRequest = { selected = "" }) {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(document.ifEmpty { "加载中…" }, fontSize = 14.sp)
            }
            TextButton("关闭", onClick = { selected = "" }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
        }
    }
}
