<div align="center">

<img src=".github/img/icon-512.png" width="96" alt="LeiFetch">

# [LeiFetch](https://github.com/bileizhen/LeiFetch)

把应用里的下载接管成多线程下载

<p>
  <a href="https://github.com/bileizhen/LeiFetch/stargazers"><img src="https://img.shields.io/github/stars/bileizhen/LeiFetch" alt="GitHub Stars"></a>
  <a href="https://github.com/bileizhen/LeiFetch/issues"><img src="https://img.shields.io/github/issues/bileizhen/LeiFetch" alt="GitHub Issues"></a>
  <a href="./LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-blue.svg" alt="GPL-3.0 License"></a>
  <a href="https://developer.android.com/about/versions/marshmallow"><img src="https://img.shields.io/badge/Android-6.0%2B-blue.svg" alt="Android 6.0+"></a>
  <a href="https://lsposed.org"><img src="https://img.shields.io/badge/LSPosed-API_101-orange.svg" alt="LSPosed API 101"></a>
  <a href="https://github.com/bileizhen/LeiFetch/releases"><img src="https://img.shields.io/github/v/tag/bileizhen/LeiFetch?label=release" alt="Latest Release"></a>
  <a href="https://github.com/bileizhen/LeiFetch/releases"><img src="https://img.shields.io/github/downloads/bileizhen/LeiFetch/total" alt="Downloads"></a>
</p>

<p><strong>简体中文</strong> | <a href="README.en.md">English</a></p>

</div>

## 项目简介

LeiFetch 是一个 Android 下载接管模块。它通过 LSPosed Hook 捕获应用内的下载请求，交给 Kotlin 移植的 NSFX 内核执行多线程下载，探测、分段和写入全部在设备本地完成。

界面采用 Miuix + Jetpack Compose，任务组织方式参考 Motrix，交互保持 Android 原生。下载运行时不依赖任何第三方下载库。

代码托管于 [GitHub](https://github.com/bileizhen/LeiFetch)，打包产物经 [Releases](https://github.com/bileizhen/LeiFetch/releases) 分发。

> [!IMPORTANT]
> 通用网络请求捕获保留原应用的返回值和回调。接管不是透明的下载虚拟化，确认重新下载前应先取消原任务。LeiFetch 与 NSFX 上游、LSPosed 及各被适配应用没有隶属关系。

## 功能特性

### NSFX 多线程内核

- 单任务最多 16 线程、动态尾段拆分、断点恢复、指数退避、主机并发降级与全局连接 / 速度预算
- 每次跳转都更换签名 URL 的 CDN（如腾讯 cdntips）不会误报资源变化，断点可跨会话续传
- 服务器拒绝 Range 时自动回退单连接，不因分段失败而中断任务

### GitHub 镜像加速

- 自动识别 GitHub 直链下载（releases、archive、raw 等），经「镜像前缀 + 原链接」转发
- 每次下载开始时用下载地址本身并行实测各镜像，校验必须返回 206 分段响应且文件总长与直连一致，避免镜像返回错误页或旧文件，再取最快合格者
- 内置 5 个镜像站，支持自定义与手动指定

### 下载接管

- 通用 DownloadManager / OkHttp / HttpURLConnection / WebView 捕获，以及 Firefox GeckoView 适配
- 系统下载器插件在 DownloadProvider 内集中捕获所有应用的 DownloadManager 入队，系统下载列表与通知入口改由 LeiFetch 呈现
- 发现的下载进入待确认列表，由你决定是否接管
- 按 Via 第三方下载器契约（ACTION_SEND 传入下载地址）提供接收入口，任何应用分享的文本链接都能转交 LeiFetch 立即下载，无需 Hook

### 传输工作台

- 仪表盘提供实时速度曲线与任务统计，下载按进行中、已停止、已完成、全部分类，支持搜索
- 按当前筛选批量开始、暂停，标注为「开始本组」「暂停本组」
- 任务行右滑多选（全选 / 批量删除）、左滑删除（连同已保存文件）；面板跟手、松手弹簧归位，删除是滑出与取消回弹同一段动画的正反播放
- 任务详情三视图：信息卡（传输 / 进度 / 连接 / 常规）、Motrix 式分段点阵（默认 1 MB 每片，绿色渐进填充，图例计数）和速度曲线（会话 60 秒 / 生命周期切换，均值、峰值、活跃时长）

### 实时卡片

- 待确认（确认下载 / 忽略）、下载进度与完成（打开 / 分享）走 Android 16 原生实时通知接口
- 胶囊与展开态的呈现形态由系统决定，ColorOS 流体云、小米超级岛等均为系统行为
- 仅实际传输使用连续进度条；确认、排队、保存和完成使用文字模板，不展示虚假的 0% / 100% 进度条
- 速度为零时只显示已下载 / 总大小，不提供虚假的预计剩余时间

### 界面与动效

- Miuix 界面，支持浅色、深色、Monet、模糊、液态玻璃、预测性返回和全局缩放
- 手机底部导航，窗口宽度达到 840 dp 时切换为侧栏
- NSFX 标题渐变随下载状态变速流动，引擎调度示意图在传输结束时收尾滑行；纯装饰，不冒充实测数据

### 本地优先

- 设置与任务存于本机 DataStore / SQLite，远程偏好仅只读镜像
- 捕获配置经偏好提供者跨进程读取，进程被冻结时接管自动退回宿主应用自身下载
- 网络仅用于你主动发起的下载与探测，没有遥测和统计上报

## 兼容性

| 项目 | 支持情况 |
| --- | --- |
| Android | 6.0（API 23）及以上 |
| 框架 | LSPosed（legacy Xposed 93 与 libxposed API 101 双入口；作用域自动申请需支持 API 100+ 的框架） |
| 已验证设备 | 一加 PLR110 · Android 16 / API 36 · Oplus ROM V16.1.0 |

系统下载器插件在 `com.android.providers.downloads` 内识别 DownloadManager 入队（原任务保留），已由通用插件在应用内上报的入队不会重复捕获；`com.android.providers.downloads.ui` 的下载列表与通知入口（查看下载、通知点击）重定向到 LeiFetch 下载页，打开单个已完成文件的入口保持系统行为。系统下载器插件需在 LSPosed 勾选上述两个系统包并重启后生效。

Firefox 插件观察 GeckoView 外部响应后独立探测公开 HTTP(S) 文件；普通 200 响应、未知大小、缺失或弱 ETag 均可接管，最多跟随 5 次重定向并禁止 HTTPS 降级。Firefox 适配暂不提取 Cookie、Referer 或 POST 请求体，需要这些信息的资源与 blob / data URL 保留浏览器自身流程。

## 安装

1. 从 [Releases](https://github.com/bileizhen/LeiFetch/releases) 下载并安装 APK。
2. 在 LSPosed 中启用 **LeiFetch**。开启插件或下载接管总开关时会自动向 LSPosed 申请所需作用域（在弹窗中确认即可）：Firefox 插件申请浏览器包名，系统下载器插件申请 `com.android.providers.downloads` 与 `com.android.providers.downloads.ui`，授权后重启生效；通用插件还需在插件页填写目标应用包名。若申请弹窗未出现或被拒绝，可在插件卡片展开区重新申请，或到 LSPosed 手动勾选。
3. 为 LeiFetch 允许通知权限；实时卡片的呈现由系统决定。
4. 重启目标应用。发现的下载会进入下载页，点击确认后开始传输。
5. 可选：在设置中选择 SAF 保存目录。默认保存在应用内部，卸载时会一并删除。

> [!NOTE]
> ColorOS 需允许 LeiFetch 后台运行：Firefox 进程内读取接管配置依赖 LeiFetch 的偏好提供者，进程被深度冻结或强停时配置读取失败，接管自动退回 Firefox 自身下载。

## 常见问题

### Via 浏览器里为什么选不到 LeiFetch？

Via 的「第三方下载器」采用作者维护的内置白名单：选中后 Via 以 ACTION_SEND + text/plain 显式调起白名单应用的下载组件，只传下载地址、不带 UA / Cookie / 文件名，名单无法由下载器一侧自行加入（参见 [gopeed#412](https://github.com/GopeedLab/gopeed/issues/412) 中 Via 作者的说明）。LeiFetch 已按该契约实现接收入口（`DownloaderActivity`，导出组件，读取 `EXTRA_TEXT` 入库并立即开始下载），因此现在可以在 Via 或任意应用中把链接「分享」到 LeiFetch，或复制链接后打开 LeiFetch 由剪贴板识别接手。

要在 Via 设置里直接选择 LeiFetch，需其作者把 LeiFetch 加入白名单，可到 [tuyafeng/Via](https://github.com/tuyafeng/Via/issues) 提交申请，所需三项定义为 `io.github.bileizhen.leifetch`、`io.github.bileizhen.leifetch.DownloaderActivity` 和 `io.github.bileizhen.leifetch.MainActivity`。

### 系统下载器插件勾了却没有反应？

该插件需要在 LSPosed 中同时勾选 `com.android.providers.downloads` 与 `com.android.providers.downloads.ui`，授权后重启设备才生效。只勾前者时能捕获入队，但系统下载列表和通知入口不会被重定向；只勾后者则没有捕获来源。

### 有些下载为什么接管不了？

通用插件走网络层捕获，能拿到原请求的完整上下文；Firefox 插件只观察 GeckoView 的外部响应，独立探测公开 HTTP(S) 文件，因此暂不提取 Cookie、Referer 或 POST 请求体，需要这些信息的资源与 blob / data URL 保留浏览器自身流程。捕获范围也受作用域限制：通用插件需在插件页填写目标应用包名，未勾选作用域的应用不会被接管。

### 确认下载后原来的下载还在跑？

通用网络请求捕获保留原应用的返回值和回调，接管不是透明的下载虚拟化。确认重新下载前应先取消原任务，否则会得到两份文件。

### 卸载后下载的文件还在吗？

默认保存在应用内部存储，卸载时会一并删除。在设置中选择 SAF 保存目录可把文件保存到外部位置，该位置不受卸载影响。

## 隐私

- 下载探测不消费完整文件正文；日志记录回退原因，但不记录可能含凭据的完整下载 URL。
- 关于页的开发组与贡献者名单会从腾讯 QQ 头像 CDN 加载对应头像；不发生其它请求。
- 启用 GitHub 镜像加速时，对应任务的 GitHub 地址会经所选镜像站转发；镜像测速只请求 Range 首字节，不消费文件正文。
- 规则与配置在本机匹配和存储，不上传任务列表、URL 或站点信息。
- 网络仅用于你主动发起的下载与探测，没有遥测和统计上报。

## 从源码构建

需要 JDK 17 或 21、Android SDK 37 和 Build Tools 35。创建本地 `local.properties` 后执行：

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
```

真机测试：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
adb shell pm grant io.github.bileizhen.leifetch android.permission.POST_NOTIFICATIONS
adb shell am instrument -w io.github.bileizhen.leifetch.test/androidx.test.runner.AndroidJUnitRunner
```

### 依赖仓库与网络

仓库镜像已随工程提交，国内网络开箱即可同步，无需在本机做任何 Gradle 配置：

- `settings.gradle.kts` 与根 `build.gradle.kts` 前置阿里云镜像（`public` / `google` / `gradle-plugin`），`google()`、`mavenCentral()` 作为兜底，镜像缺件时自动回落，非国内网络同样可用。需要强制官方源时，注释掉对应的 `maven(...)` 行即可。
- `gradle/wrapper/gradle-wrapper.properties` 的发行版地址指向腾讯镜像 `mirrors.cloud.tencent.com/gradle/`，避免 `services.gradle.org` 在国内超时。要改回官方源就恢复成 `https\://services.gradle.org/distributions/gradle-8.13-bin.zip`。
- `api.xposed.info`（Xposed API 82）没有国内镜像，必须直连；该仓库不可达时同步会卡在 `de.robv.android.xposed:api:82`，可先用 `compileOnly` 对应的本地 `jar` 顶替。

若所在网络仍受限，在本机 `~/.gradle/gradle.properties`（不要提交）配置代理即可：

```properties
systemProp.http.proxyHost=127.0.0.1
systemProp.http.proxyPort=7890
systemProp.https.proxyHost=127.0.0.1
systemProp.https.proxyPort=7890
```

其它常见同步失败原因：Android Studio 版本低于 AGP 8.13.2 所需版本（用较新的 Studio，JDK 直接用自带的 JBR 21 即可）；未安装 Android SDK Platform 37（在 SDK Manager 勾选，或交给 AGP 自动下载）。

> [!NOTE]
> Windows 中文路径下如遇 Java Unix-domain socket 报错，可把工程联接到 ASCII 路径后构建。正式发布请使用自己的签名密钥。

## 参与开发

- [第三方依赖与许可证](THIRD_PARTY_NOTICES.md)
- [GPL-3.0 完整协议](LICENSE)

## 开源协议

NSFX 内核移植自 [Hanabi-Download-Manager-X](https://github.com/buaoyezz/Hanabi-Download-Manager-X)（基准提交 `5df83d3`），界面部分参考 SukiSU-Ultra 与 XBlocker 的关于页，完整应用按 GPL-3.0 分发，详见 [LICENSE](LICENSE)。上游及组件归属见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

## 致谢

- [LSPosed](https://github.com/LSPosed/LSPosed)：模块运行框架
- [Hanabi-Download-Manager-X](https://github.com/buaoyezz/Hanabi-Download-Manager-X)：NSFX 多线程内核来源
- [SukiSU-Ultra](https://github.com/SukiSU-Ultra/SukiSU-Ultra)：界面与部分组件来源
- [XBlocker](https://github.com/bileizhen/XBlocker)：界面壳与 LSPosed 作用域服务来源
- [Miuix](https://github.com/compose-miuix-ui/miuix)：界面组件库
- [libxposed/service](https://github.com/libxposed/service)：模块服务 binder 协议参考

## 浏览量

<div align="center">

![:shell](https://count.getloli.com/@bileizhen_LeiFetch?name=bileizhen_LeiFetch&theme=original-new&padding=7&offset=0&align=center&scale=1&pixelated=1&darkmode=auto)

</div>
