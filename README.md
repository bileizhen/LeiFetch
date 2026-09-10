<div align="center">
  <h1>LeiFetch</h1>
  <p>把应用里的下载,接管成多线程下载。</p>

  <p><strong>简体中文</strong></p>

  [![License](https://img.shields.io/badge/License-GPL--3.0-blue.svg)](LICENSE)
  [![Android 6.0+](https://img.shields.io/badge/Android-6.0%2B-blue.svg)](https://developer.android.com/about/versions/marshmallow)
  [![LSPosed](https://img.shields.io/badge/LSPosed-API_93_+_libxposed_101-orange.svg)](https://lsposed.org)
  [![GitHub](https://img.shields.io/badge/作者-bileizhen-blue)](https://github.com/bileizhen)

  <p>
    <a href="https://github.com/bileizhen/LeiFetch/releases/latest">下载最新版</a>
  </p>
</div>

LeiFetch 是一个 Android 下载接管模块。它通过 LSPosed Hook 捕获应用内的下载请求,交给 Kotlin 移植的 NSFX 内核执行多线程下载;界面采用 Miuix + Jetpack Compose,任务组织方式参考 Motrix,交互保持 Android 原生。

## 特性

- **NSFX 多线程内核**:单任务最多 16 线程、动态尾段拆分、断点恢复、指数退避、主机并发降级与全局连接 / 速度预算;每次跳转都更换签名 URL 的 CDN(如腾讯 cdntips)不会误报资源变化,断点可跨会话续传。
- **GitHub 镜像加速**:自动识别 GitHub 直链下载(releases、archive、raw 等),经「镜像前缀 + 原链接」转发;每次下载开始时用下载地址本身并行实测各镜像,校验必须返回 206 分段响应且文件总长与直连一致(避免镜像返回错误页或旧文件),再取最快合格者;内置 5 个镜像站并支持自定义与手动指定。
- **应用下载接管**:通用 DownloadManager / OkHttp / HttpURLConnection / WebView 捕获、Firefox GeckoView 适配,以及系统下载器插件(在 DownloadProvider 内集中捕获所有应用的 DownloadManager 入队,系统下载列表与通知入口改由 LeiFetch 呈现);发现的下载进入待确认列表,由你决定是否接管。
- **任务详情三视图**:信息卡(传输 / 进度 / 连接 / 常规)、Motrix 式分段点阵(默认 1 MB 每片,绿色渐进填充,图例计数)和速度曲线(会话 60 秒 / 生命周期切换,均值、峰值、活跃时长)。
- **实时卡片**:待确认(确认下载 / 忽略)、下载进度与完成(打开 / 分享)走 Android 16 原生实时通知接口,ColorOS 流体云等呈现形态由系统决定。
- **传输工作台**:仪表盘实时速度曲线与任务统计;下载按进行中、已停止、已完成、全部分类,支持搜索与按筛选批量开始、暂停。
- **动效细节**:NSFX 标题渐变随下载状态变速流动,引擎调度示意图在传输结束时收尾滑行;纯装饰,不冒充实测数据。
- **Miuix 界面**:浅色、深色、Monet、模糊、液态玻璃、预测性返回与全局缩放;手机底部导航、宽屏侧栏自适应。
- **本地优先**:设置与任务存于本机 DataStore / SQLite,远程偏好仅只读镜像;下载运行时不依赖任何第三方下载库。

## 兼容性

| 项目 | 支持情况 |
| --- | --- |
| Android | 6.0(API 23)及以上 |
| 框架 | LSPosed(legacy Xposed 93 与 libxposed API 101 双入口;作用域自动申请需支持 API 100+ 的框架) |
| 已验证设备 | 一加 PLR110 · Android 16 / API 36 · Oplus ROM V16.1.0 |

通用网络请求捕获保留原应用的返回值和回调;接管不是透明的下载虚拟化,确认重新下载前应先取消原任务。系统下载器插件在 `com.android.providers.downloads` 内识别 DownloadManager 入队(原任务保留),已由通用插件在应用内上报的入队不会重复捕获;`com.android.providers.downloads.ui` 的下载列表与通知入口(查看下载、通知点击)重定向到 LeiFetch 下载页,打开单个已完成文件的入口保持系统行为。系统下载器插件需在 LSPosed 勾选上述两个系统包并重启后生效。Firefox 插件观察 GeckoView 外部响应后独立探测公开 HTTP(S) 文件;普通 200 响应、未知大小、缺失或弱 ETag 均可接管,最多跟随 5 次重定向并禁止 HTTPS 降级。服务器拒绝 Range 时自动回退单连接。Firefox 适配暂不提取 Cookie、Referer 或 POST 请求体,需要这些信息的资源与 blob/data URL 保留浏览器自身流程。

## 安装

1. 从 [Releases](https://github.com/bileizhen/LeiFetch/releases) 下载并安装 APK。
2. 在 LSPosed 中启用 **LeiFetch**。开启插件或下载接管总开关时会自动向 LSPosed 申请所需作用域(在弹窗中确认即可):Firefox 插件申请浏览器包名,系统下载器插件申请 `com.android.providers.downloads` 与 `com.android.providers.downloads.ui`,授权后重启生效;通用插件还需在插件页填写目标应用包名。若申请弹窗未出现或被拒绝,可在插件卡片展开区重新申请,或到 LSPosed 手动勾选。
3. 为 LeiFetch 允许通知权限;实时卡片(流体云等)的呈现由系统决定。
4. 重启目标应用。发现的下载会进入下载页,点击确认后开始传输。
5. 可选:在设置中选择 SAF 保存目录。默认保存在应用内部,卸载时会一并删除。

ColorOS 需允许 LeiFetch 后台运行:Firefox 进程内读取接管配置依赖 LeiFetch 的偏好提供者,进程被深度冻结或强停时配置读取失败,接管自动退回 Firefox 自身下载。

## 隐私

- 下载探测不消费完整文件正文;日志记录回退原因,但不记录可能含凭据的完整下载 URL。
- 关于页的开发组与贡献者名单会从腾讯 QQ 头像 CDN 加载对应头像;不发生其它请求。
- 启用 GitHub 镜像加速时,对应任务的 GitHub 地址会经所选镜像站转发;镜像测速只请求 Range 首字节,不消费文件正文。
- 规则与配置在本机匹配和存储,不上传任务列表、URL 或站点信息。
- 网络仅用于你主动发起的下载与探测,没有遥测和统计上报。

## 从源码构建

需要 JDK 17 或 21、Android SDK 37、Build Tools 35。创建本地 `local.properties` 后执行:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
```

真机测试:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
adb shell pm grant io.github.bileizhen.leifetch android.permission.POST_NOTIFICATIONS
adb shell am instrument -w io.github.bileizhen.leifetch.test/androidx.test.runner.AndroidJUnitRunner
```

Windows 中文路径下如遇 Java Unix-domain socket 报错,可把工程联接到 ASCII 路径后构建。正式发布请使用自己的签名密钥。

## 许可

NSFX 内核移植自 [Hanabi-Download-Manager-X](https://github.com/buaoyezz/Hanabi-Download-Manager-X)(基准提交 `5df83d3`),界面部分参考 SukiSU-Ultra 与 XBlocker 的关于页,完整应用按 GPL-3.0 分发,详见 [LICENSE](LICENSE)。上游及组件归属见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

LeiFetch 与 NSFX 上游、LSPosed 及各被适配应用没有隶属关系。

## 浏览量

<div align="center">

![:shell](https://count.getloli.com/@bileizhen_LeiFetch?name=bileizhen_LeiFetch&theme=original-new&padding=7&offset=0&align=center&scale=1&pixelated=1&darkmode=auto)

</div>
