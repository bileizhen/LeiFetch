# 来源与许可证

LeiFetch 的 NSFX Kotlin 移植代码依据 Hanabi-Download-Manager-X 编写。

- 项目：https://github.com/buaoyezz/Hanabi-Download-Manager-X
- 基准提交：`5df83d35431c60be83a3a6527c4c3caa2b070b7b`
- 作者：buaoyezz 与该项目贡献者。
- 许可证：GNU GPL version 3，全文见本项目 LICENSE。
- 本次修改：将 Dart 调度、分段策略、重试、随机写入与断点恢复改写为 Kotlin Coroutine / Android 实现；提供 Miuix UI、LSPosed 捕获与 Android 16 实时通知集成。

主要参考文件：`nsfx_kernel.dart`、`config/download_config.dart`、`models/segment.dart`、`downloader/dynamic_segment_policy.dart`、`downloader/download_engine.dart`、`downloader/http_client.dart`、`downloader/download_header_builder.dart`、`storage/task_storage.dart`。

界面 API 用法参考 XBlocker（https://github.com/bileizhen/XBlocker）和 Miuix 官方仓库（https://github.com/compose-miuix-ui/miuix）；本项目未复制 XBlocker 的小米通知实现。

## 界面移植（2026-09-09）

- [SukiSU-Ultra v4.1.3](https://github.com/SukiSU-Ultra/SukiSU-Ultra/tree/v4.1.3)，提交 `0ca744a`，GPL-3.0：`ui/component/FloatingBottomBar.kt`、`ui/component/liquid/*.kt`、`ui/component/miuix/animation/{DampedDragAnimation,InteractiveHighlight}.kt`、`ui/component/miuix/modifier/DragGestureInspector.kt`、`ui/component/miuix/effect/*.kt`（关于页动效背景）、`ui/util/BlurExt.kt` 移植于 `app/src/main/java/io/github/bileizhen/leifetch/ui/`；`OverviewStatus` 改编自 `HomeMiuix.kt` 的状态/指标卡，外观页与主题预览卡改编自 `ColorPaletteScreenMiuix.kt`；关于页 `ui/AboutScreen.kt`（含许可/隐私文档页）经 [XBlocker](https://github.com/bileizhen/XBlocker)（同一作者，授权移植）取自其 AboutScreen/AboutDocuments；`MainActivity.kt` 采用其 Navigation 3 返回栈 / `NavDisplay` 集成，手势跟随、取消回弹及转场使用与 XBlocker 一致的 `miuix-navigation3-ui-android:0.9.3`（Apache-2.0）。
- 悬浮底栏与液态玻璃辅助代码标识其来源为 [compose-miuix-ui 示例](https://github.com/compose-miuix-ui/miuix)与 [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)，Apache-2.0。原始来源说明保留在对应文件中。SukiSU 相关改编按上游 GPL-3.0 条款并入。
- Selected Material Icons 1.7.8 矢量源文件从官方 `androidx.compose.material:material-icons-extended-android:1.7.8:sources` 构件复制到 `app/src/main/java/androidx/compose/material/icons/`（仅含 UI 用到的图标，避免打包整个扩展图标库），保留其 Android 开源项目版权与 Apache-2.0 声明。
- LeiFetch 改动：包名/导入适配；四标签底栏；下载状态英雄卡与真实任务/速度数据；中文文案；无 i18n 资源层。使用本项目自己的名称与图标，不含 SukiSU 品牌资产。

Miuix、AndroidX、Kotlin Coroutines、Gradle Wrapper 的原始版权及许可证仍适用；发布 APK 时应同时分发对应许可证与本项目完整源代码、构建文件和修改说明。MockWebServer / OkHttp 仅供测试使用，不是应用下载运行时。
