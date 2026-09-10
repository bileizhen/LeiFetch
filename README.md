# LeiFetch

Android 下载接管模块 · bileizhen

使用 LSPosed Hook 插件捕获应用下载，交给 Kotlin 重写的 NSFX 内核执行。界面采用 Miuix + Jetpack Compose，仅借鉴 Motrix 的任务组织方式，保留 Android 原生交互。

## 功能

- 自适应四栏：手机使用底部导航，宽屏使用侧栏；保留 Miuix 悬浮底栏和外观选项。
- NSFX 内核主卡：线程上限、任务并行与动态拆分配置，待机呼吸、传输分流动效；速度曲线使用真实采样。
- 仪表盘集中展示实时速度、任务计数、已完成文件大小和最近任务。
- 下载按进行中、已停止、已完成、全部分类，支持搜索、按当前筛选批量开始和暂停；点击文件展开详情与操作。
- 统一的新建下载入口，链接校验后创建待确认任务；插件的应用范围与说明按需展开。
- NSFX：任务队列、直接分段写入、断点恢复、动态尾段拆分、指数退避、主机并发降级、全局连接和速度预算。
- 插件：通用 DownloadManager / OkHttp / HttpURLConnection / WebView 捕获，以及 Firefox GeckoView 适配。
- ColorOS / 一加 Android 16 实时卡片：待确认（确认下载 / 忽略）、下载进度，以及完成卡片上的打开、分享动作。
  卡片按 OPPO 模板规范的信息层级调整：状态与百分比优先，文件名单独显示，辅助信息最多两项；仅传输中展示进度条。[设计说明与接入边界](docs/FLUID_CLOUD_DESIGN.md)。
- 关于页（移植自 XBlocker / SukiSU）：动效背景、版本信息、GitHub 与 NSFX 上游链接、内置开源许可全文与隐私说明。
- Miuix 浅色、深色、Monet；SAF 目录授权；DataStore 设置和只读 RemotePreferences 镜像。

## 接管范围

通用网络请求保留原应用的返回值和回调，进入 LeiFetch 待确认列表。它们不是透明的下载虚拟化：确认重新下载前，应取消原任务。

WebView 在请求可靠入库后替代原下载监听器；上报失败则回到原监听器。Firefox 插件观察 GeckoView 的外部响应（`onExternalResponse` 收到的 `WebResponse` 含完整响应头），独立探测公开 HTTP(S) 文件后进入待确认列表。普通 200 响应、未知大小、缺失或弱 ETag 均可接管；支持最多 5 次重定向，禁止 HTTPS 降级。仅在两端都提供版本或长度时比对，不再将分段能力作为接管条件。服务器拒绝 Range 时，探测与 NSFX 内核均回退普通 GET / 单连接下载。探测不会消费完整文件正文。

Firefox 适配暂不提取浏览器 Cookie、Referer 或 POST 请求体；需要这些信息的资源、独立请求返回错误/登录页的资源，以及 blob/data URL 仍保留 Firefox 流程。日志会记录具体回退原因，不记录可能含凭据的完整下载 URL。浙大测速链接是早期实链路测试样例，生产代码没有域名白名单。

## 使用

1. 安装 APK；在 LSPosed 中启用 LeiFetch，并勾选要适配的应用。
2. 打开插件页，开启下载接管和对应插件；通用插件需填写应用包名。
3. 重启目标应用。发现的下载会进入下载页，点击确认下载。
4. 为 LeiFetch 允许通知和系统的实时通知权限；ColorOS 的流体云呈现由系统决定。
5. 在设置中选择保存目录。默认应用内部文件会随卸载删除。

## 构建

需要 JDK 17 或 21、Android SDK 37、Build Tools 35，以及网络访问。

```powershell
.\build-local.ps1 :app:assembleDebug
.\build-local.ps1 :app:testDebugUnitTest :app:lintDebug
```

Windows 脚本为当前工程建立 ASCII 路径联接并使用短临时目录，避免中文路径与 Java Unix-domain socket 问题。其他环境可用 `./gradlew :app:assembleDebug`，并在 `local.properties` 配置本机 SDK。

APK：`app/build/outputs/apk/debug/app-debug.apk`。

下载运行时不依赖第三方下载库，也不依赖 OkHttp。MockWebServer / OkHttp 仅用于测试夹具。

## 真机测试

```powershell
.\build-local.ps1 :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell pm grant io.github.bileizhen.leifetch android.permission.POST_NOTIFICATIONS
adb shell am instrument -w io.github.bileizhen.leifetch.test/androidx.test.runner.AndroidJUnitRunner
```

测试使用手机本机 HTTP 服务；`FirefoxProbeZjuTest` 仅对 speedtest.zju.edu.cn 发起 1 字节 Range 探测，设备无法解析该域名时自动跳过。CloudProbeTest 展示 30 秒完成卡片，供检查流体云动作。测试授权仅限 LeiFetch 的通知权限。

详细验证结果见 [验证记录](docs/VALIDATION.md)，完整方案和 Kotlin 代码见 [开发方案](docs/DEVELOPMENT_PLAN.md)。

## 功能截图占位

| 仪表盘 | 下载 | 插件 | 设置 | 流体云展开卡片 |
|---|---|---|---|---|
| 待补充仪表盘截图 | 待补充下载进度截图 | 待补充插件开关截图 | 待补充设置截图 | 待补充含打开 / 分享按钮的真机截图 |

## 兼容性与实现边界

- 声明 minSdk 23，实际测试设备为一加 PLR110、Android 16 / API 36、Oplus ROM V16.1.0。
- 流体云属于 OPPO / ColorOS；不使用小米 `miui.focus.*` 超级岛协议。
- 低于 Android 16 或系统不接受实时卡片时，只能使用系统可用的通知显示方式；不能把普通通知当作已验证的流体云。
- ColorOS 需允许 LeiFetch 后台运行：Firefox 进程内读取接管配置依赖 LeiFetch 的偏好提供者，进程被深度冻结或强停时配置读取失败，接管自动退回 Firefox 自身下载。
- 原 NSFX 桌面版的 HTTP RPC、浏览器扩展服务器、Rust/rhttp 与 HTTP/3、桌面手动代理界面不在 Android 移植范围。没有校验器的资源采用单连接重下。
- 前台服务不是永久保活；强制停止、系统超时或厂商回收后，下次打开应用可继续有强校验器的下载。
- SAF 文档提供者的创建、写入、重命名不具备统一的文件系统事务保证；异常时记录发布状态并清理未完成文档。

## 许可证

NSFX 基准提交：`5df83d35431c60be83a3a6527c4c3caa2b070b7b`。

GPLv3。上游及组件归属见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)；完整许可证见 [LICENSE](LICENSE)。
