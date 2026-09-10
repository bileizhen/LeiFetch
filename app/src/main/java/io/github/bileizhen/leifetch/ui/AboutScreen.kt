// Ported from XBlocker (same author, used with permission); original About screen
// adapted from SukiSU-Ultra v4.1.3 (0ca744a), GPL-3.0. See THIRD_PARTY_NOTICES.md.
// LeiFetch branding, local links, hardcoded Chinese strings.
package io.github.bileizhen.leifetch.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bileizhen.leifetch.BuildConfig
import io.github.bileizhen.leifetch.R
import io.github.bileizhen.leifetch.StaggeredEntrance
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
                        .requiredSize(145.dp)
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
                    painter = painterResource(id = R.drawable.ic_download),
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

            // 链接卡与名单同页连续排布；名单进入视口时才逐个弹出（视口判断用屏幕高度，首帧有效）。
            val memberCard: @Composable (TeamMember) -> Unit = { member ->
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
                    MemberRow(member)
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
                    Spacer(Modifier.height(18.dp))
                    SmallTitle("开发组", insideMargin = PaddingValues(horizontal = 20.dp, vertical = 8.dp))
                    teamMembers.forEachIndexed { index, member ->
                        StaggeredEntrance(index) {
                            Column {
                                memberCard(member)
                                if (index < teamMembers.lastIndex) Spacer(Modifier.height(10.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    SmallTitle("贡献者 · 内测", insideMargin = PaddingValues(horizontal = 20.dp, vertical = 8.dp))
                    betaTesters.forEachIndexed { index, member ->
                        StaggeredEntrance(index) {
                            Column {
                                memberCard(member)
                                if (index < betaTesters.lastIndex) Spacer(Modifier.height(10.dp))
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
    }
}

private fun licenses() = listOf(
    "第三方来源与修改" to "THIRD_PARTY_NOTICES.md",
    "GNU GPL v3" to "licenses/GPL-3.0.txt",
    "Miuix / AndroidX · Apache 2.0" to "licenses/Apache-2.0.txt",
)

// 开发组与贡献者（内测）名单：内容硬编码，头像按 QQ 号从腾讯头像 CDN 加载。
private data class TeamMember(val qq: String, val name: String, val role: String)

private val teamMembers = listOf(
    TeamMember("3140014249", "bileizhen", "开发 · 设计 · 维护"),
    TeamMember("2183396164", "加藤糊", "图标绘制"),
    TeamMember("2536843865", "Hutao_felicity", "镜像站 · ghfile.geekertao.top · gh.dpik.top"),
)

private val betaTesters = listOf(
    TeamMember("617498164", "ShiraM1zu", "内测用户"),
    TeamMember("442259851", "KelierAndes", "内测用户"),
    TeamMember("2468872022", "LinYe_2804", "内测用户"),
    TeamMember("3022513812", "DiceSKY", "内测用户"),
    TeamMember("165658800", "Matsuri", "内测用户"),
)

/** QQ 头像内存缓存；关于页每次打开最多加载几张 100 规格头像，无需落盘。
 *  不同网络下各端点可用性不一（实测 headimg_dl 在部分网络 400），按序回退。 */
private object QqAvatarCache {
    private val cache = object : LruCache<String, Bitmap>(24) {
        override fun sizeOf(key: String, value: Bitmap) = 1
    }
    private val endpoints = listOf(
        "https://q1.qlogo.cn/g?b=qq&nk=%s&s=100",
        "https://thirdqq.qlogo.cn/g?b=qq&nk=%s&s=100",
        "https://q1.qlogo.cn/headimg_dl?dstuin=%s&spec=100",
    )
    suspend fun load(qq: String): Bitmap? {
        cache.get(qq)?.let { return it }
        return withContext(Dispatchers.IO) {
            endpoints.firstNotNullOfOrNull { pattern ->
                runCatching {
                    val connection = URL(pattern.format(qq)).openConnection() as HttpURLConnection
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000
                    connection.setRequestProperty("User-Agent",
                        "Mozilla/5.0 (Linux; Android 16) LeiFetch/1.0")
                    try {
                        BitmapFactory.decodeStream(connection.inputStream)?.also { cache.put(qq, it) }
                    } finally {
                        connection.disconnect()
                    }
                }.getOrNull()
            }
        }
    }
}

@Composable
private fun QqAvatar(qq: String) {
    val bitmap by produceState<Bitmap?>(null, qq) { value = QqAvatarCache.load(qq) }
    Box(Modifier.padding(end = 6.dp).size(44.dp).clip(CircleShape)
        .background(colorScheme.onSurface.copy(alpha = 0.06f)), contentAlignment = Alignment.Center) {
        val loaded = bitmap
        if (loaded != null) Image(loaded.asImageBitmap(), contentDescription = null,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Text(qq.takeLast(2), fontSize = 12.sp, color = colorScheme.onSurfaceVariantSummary)
    }
}

@Composable
private fun MemberRow(member: TeamMember) {
    BasicComponent(title = member.name, summary = member.role, startAction = { QqAvatar(member.qq) })
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
                item { Card { BasicComponent(title = "本地处理", summary = "下载任务、进度与历史仅存于设备本地数据库；不上传任何数据，卸载即清除。") } }
                item { Card { BasicComponent(title = "网络访问", summary = "仅连接你要下载的目标服务器，以及接管前的 1 字节校验探测；无遥测、无第三方中转。") } }
                item { Card { BasicComponent(title = "凭据处理", summary = "捕获到的 Cookie / Authorization 等请求头只用于确认后的下载，不写入其它位置。") } }
                item { Card { BasicComponent(title = "通知与流体云", summary = "用于待确认、下载进度与完成提醒；完成卡片提供打开、分享动作。") } }
                item { Card { BasicComponent(title = "LSPosed 作用域", summary = "仅在手动勾选的应用内捕获下载；Firefox 接管前独立探测 ETag 一致，失败时保留浏览器自身下载。") } }
                item { Card { BasicComponent(title = "存储", summary = "文件只写入你选择的目录；应用内目录的文件随卸载删除。") } }
            } else {
                item { Card { BasicComponent(title = "LeiFetch", summary = "NSFX 内核 Kotlin 移植与 SukiSU 关于页面的组合应用；GPL-3.0。") } }
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
