<div align="center">

<img src=".github/img/icon-256.png" width="96" alt="LeiFetch">

# [LeiFetch](https://github.com/bileizhen/LeiFetch)

Taking over in-app downloads with a multi-threaded engine

<p>
  <a href="https://github.com/bileizhen/LeiFetch/stargazers"><img src="https://img.shields.io/github/stars/bileizhen/LeiFetch" alt="GitHub Stars"></a>
  <a href="https://github.com/bileizhen/LeiFetch/issues"><img src="https://img.shields.io/github/issues/bileizhen/LeiFetch" alt="GitHub Issues"></a>
  <a href="./LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-blue.svg" alt="GPL-3.0 License"></a>
  <a href="https://developer.android.com/about/versions/marshmallow"><img src="https://img.shields.io/badge/Android-6.0%2B-blue.svg" alt="Android 6.0+"></a>
  <a href="https://lsposed.org"><img src="https://img.shields.io/badge/LSPosed-API_101-orange.svg" alt="LSPosed API 101"></a>
  <a href="https://github.com/bileizhen/LeiFetch/releases"><img src="https://img.shields.io/github/v/tag/bileizhen/LeiFetch?label=release" alt="Latest Release"></a>
  <a href="https://github.com/bileizhen/LeiFetch/releases"><img src="https://img.shields.io/github/downloads/bileizhen/LeiFetch/total" alt="Downloads"></a>
</p>

<p><a href="README.md">简体中文</a> | <strong>English</strong></p>

</div>

## Overview

LeiFetch is an Android download-interception module. It hooks download requests inside apps through LSPosed and hands them to a Kotlin port of the NSFX engine for multi-threaded downloading; probing, segmentation and writing all happen on-device.

The UI is built with Miuix + Jetpack Compose, tasks are organized in the spirit of Motrix, and the interaction stays native to Android. Downloads do not depend on any third-party download library at runtime.

The code is hosted on [GitHub](https://github.com/bileizhen/LeiFetch), and packaged builds are distributed through [Releases](https://github.com/bileizhen/LeiFetch/releases).

> [!IMPORTANT]
> Generic network interception preserves the host app's return values and callbacks. Interception is not transparent download virtualization — cancel the original task before confirming a re-download. LeiFetch is not affiliated with the NSFX upstream, LSPosed, or any adapted app.

## Features

### NSFX multi-threaded engine

- Up to 16 threads per task, dynamic tail segmentation, resume, exponential backoff, per-host concurrency degradation and global connection / speed budgets
- CDNs that re-sign URLs on every redirect (such as Tencent cdntips) are not mistaken for changed resources, so resumes survive across sessions
- Falls back to a single connection when the server rejects Range requests, instead of failing the task

### GitHub mirror acceleration

- Detects GitHub direct downloads automatically (releases, archive, raw and more) and forwards them as mirror prefix + original URL
- When a download starts, every mirror is benchmarked in parallel with the download URL itself; a mirror only qualifies if it returns a 206 partial response whose total length matches the direct connection, which rules out error pages and stale files, and the fastest qualifying mirror wins
- Ships with 5 mirrors and supports custom and manually specified ones

### Download interception

- Generic DownloadManager / OkHttp / HttpURLConnection / WebView interception, plus a Firefox GeckoView adapter
- The system downloader plugin captures DownloadManager enqueues from every app inside DownloadProvider, so the system download list and notification entry points are presented by LeiFetch instead
- Discovered downloads land in a pending list and you decide whether to take them over
- Implements the Via third-party downloader contract (download URL passed via ACTION_SEND), so a text link shared from any app can be handed to LeiFetch and started immediately, with no hook needed

### Transfer workspace

- Dashboard with a live speed chart and task statistics; downloads are grouped into active, stopped, completed and all, with search
- Batch start / pause apply to the current filter and search results, labelled “Start group” / “Pause group”
- Swipe right on a task row to multi-select (select all / batch delete), swipe left to delete (including the saved file); the panel tracks your finger and springs back on release, and deletion is a single animation played forwards and backwards
- Three views for task details: an info card (transfer / progress / connections / general), a Motrix-style segment matrix (1 MB per cell by default, green progressive fill, counted in the legend) and a speed chart (60-second session / lifetime toggle, with average, peak and active duration)

### Live cards

- Pending confirmation (download / ignore), download progress and completion (open / share) go through the Android 16 native live notification API
- The system decides how the capsule and expanded state are rendered — ColorOS Fluid Cloud, Xiaomi Super Island and similar are system behaviours
- Only real transfers use a continuous progress bar; confirmation, queuing, saving and completion use text templates and never show a fake 0% / 100% bar
- When the speed is zero only downloaded / total size is shown, with no fake ETA

### Interface and motion

- Miuix UI with light, dark, Monet, blur, liquid glass, predictive back and global scaling
- Bottom navigation on phones, switching to a side rail once the window reaches 840 dp
- The NSFX title gradient flows at a speed that follows the download state, and the engine scheduling illustration glides to a stop when a transfer ends; both are decorative and do not stand in for measurements

### Local first

- Settings and tasks live in on-device DataStore / SQLite; remote preferences are read-only mirrors
- Interception config is read across processes through a preference provider, and interception falls back to the host app's own downloader when the process is frozen
- The network is used only for downloads and probes you start; there is no telemetry or analytics

## Compatibility

| Item | Support |
| --- | --- |
| Android | 6.0 (API 23) and above |
| Framework | LSPosed (both the legacy Xposed 93 and libxposed API 101 entry points; automatic scope requests need a framework that supports API 100+) |
| Verified device | OnePlus PLR110 · Android 16 / API 36 · Oplus ROM V16.1.0 |

The system downloader plugin recognises DownloadManager enqueues inside `com.android.providers.downloads` (the original task is kept), and enqueues already reported in-app by the generic plugin are not captured twice; the download list and notification entry points of `com.android.providers.downloads.ui` (view downloads, notification taps) are redirected to the LeiFetch download page, while opening a single completed file keeps the system behaviour. The system downloader plugin needs both system packages ticked in LSPosed and a reboot to take effect.

The Firefox plugin watches GeckoView external responses and then probes public HTTP(S) files on its own; plain 200 responses, unknown sizes and missing or weak ETags are all accepted, with at most 5 redirects and no HTTPS downgrade. The Firefox adapter does not extract cookies, referrers or POST bodies yet, so resources and blob / data URLs that need them keep using the browser's own flow.

## Installation

1. Download and install the APK from [Releases](https://github.com/bileizhen/LeiFetch/releases).
2. Enable **LeiFetch** in LSPosed. Turning on a plugin or the download-interception master switch requests the needed scopes from LSPosed automatically (just confirm the dialog): the Firefox plugin requests the browser package, the system downloader plugin requests `com.android.providers.downloads` and `com.android.providers.downloads.ui`, and both take effect after a reboot; the generic plugin still needs the target package name entered on its plugin page. If the request dialog never appears or you decline it, re-request from the expanded plugin card or tick the scopes manually in LSPosed.
3. Grant LeiFetch the notification permission; the system decides how live cards are rendered.
4. Restart the target app. Discovered downloads appear on the download page and start transferring once you confirm.
5. Optional: pick a SAF save directory in settings. By default files are stored in app-internal storage and are deleted on uninstall.

> [!NOTE]
> ColorOS needs to allow LeiFetch to run in the background: reading the interception config inside the Firefox process relies on LeiFetch's preference provider, so when the process is deeply frozen or force-stopped the read fails and interception falls back to Firefox's own downloader.

## FAQ

### Why can't I pick LeiFetch inside Via?

Via's “third-party downloader” uses a built-in allowlist maintained by its author: when selected, Via explicitly launches the download component of an allowlisted app with ACTION_SEND + text/plain, passing only the download URL and no UA / cookies / filename, and the list cannot be extended from the downloader side (see the Via author's explanation in [gopeed#412](https://github.com/GopeedLab/gopeed/issues/412)). LeiFetch already implements that contract (the exported `DownloaderActivity` reads `EXTRA_TEXT`, queues it and starts downloading immediately), so you can share a link to LeiFetch from Via or any other app, or copy it and open LeiFetch to let clipboard detection take over.

To select LeiFetch directly in Via's settings, its author has to add LeiFetch to the allowlist — you can file a request at [tuyafeng/Via](https://github.com/tuyafeng/Via/issues) with these three definitions: `io.github.bileizhen.leifetch`, `io.github.bileizhen.leifetch.DownloaderActivity` and `io.github.bileizhen.leifetch.MainActivity`.

### The system downloader plugin is enabled but nothing happens?

That plugin needs both `com.android.providers.downloads` and `com.android.providers.downloads.ui` ticked in LSPosed, and only takes effect after a reboot. With just the former, enqueues are captured but the system download list and notification entry points are not redirected; with just the latter there is nothing to capture from.

### Why do some downloads fail to be intercepted?

The generic plugin hooks the network layer and gets the full context of the original request, while the Firefox plugin only observes GeckoView external responses and probes public HTTP(S) files on its own, so it does not extract cookies, referrers or POST bodies — resources and blob / data URLs that need them keep using the browser's own flow. Capture is also limited by scopes: the generic plugin needs the target package name entered on its plugin page, and apps without a ticked scope are never intercepted.

### The original download keeps running after I confirm?

Generic network interception preserves the host app's return values and callbacks, and interception is not transparent download virtualization. Cancel the original task before confirming a re-download, otherwise you end up with two copies of the file.

### Are downloaded files kept after uninstalling?

By default they are stored in app-internal storage and deleted on uninstall. Pick a SAF save directory in settings to keep files in an external location that survives uninstallation.

## Privacy

- Download probing never consumes the full file body; logs record fallback reasons but never the full download URL, which may carry credentials.
- The developer and contributor roster on the about page loads avatars from Tencent's QQ avatar CDN; no other request is made.
- With GitHub mirror acceleration enabled, the GitHub URLs of the affected tasks are forwarded through the selected mirror; mirror benchmarking only requests the first byte via Range and never consumes the file body.
- Rules and configuration are matched and stored on-device; task lists, URLs and site information are never uploaded.
- The network is used only for downloads and probes you start; there is no telemetry or analytics.

## Building from source

JDK 17 or 21, Android SDK 37 and Build Tools 35 are required. Create a local `local.properties`, then run:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
```

On-device tests:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
adb shell pm grant io.github.bileizhen.leifetch android.permission.POST_NOTIFICATIONS
adb shell am instrument -w io.github.bileizhen.leifetch.test/androidx.test.runner.AndroidJUnitRunner
```

### Dependency repositories and network

The mirror setup is committed with the project, so syncing works out of the box on mainland Chinese networks with no Gradle configuration on your machine:

- `settings.gradle.kts` and the root `build.gradle.kts` put Aliyun mirrors (`public` / `google` / `gradle-plugin`) first with `google()` and `mavenCentral()` as fallbacks, so missing artifacts fall through automatically and non-Chinese networks work just as well. To force the official sources, comment out the corresponding `maven(...)` lines.
- The distribution URL in `gradle/wrapper/gradle-wrapper.properties` points at the Tencent mirror `mirrors.cloud.tencent.com/gradle/` to avoid `services.gradle.org` timing out in mainland China. To go back to the official source, restore `https\://services.gradle.org/distributions/gradle-8.13-bin.zip`.
- `api.xposed.info` (Xposed API 82) has no Chinese mirror and must be reached directly; when that repository is unreachable, syncing stalls on `de.robv.android.xposed:api:82`, and you can temporarily substitute a local `jar` through `compileOnly`.

If your network is still restricted, configure a proxy in `~/.gradle/gradle.properties` on your machine (never commit it):

```properties
systemProp.http.proxyHost=127.0.0.1
systemProp.http.proxyPort=7890
systemProp.https.proxyHost=127.0.0.1
systemProp.https.proxyPort=7890
```

Other common sync failures: an Android Studio older than what AGP 8.13.2 requires (use a recent Studio; its bundled JBR 21 is fine), and a missing Android SDK Platform 37 (tick it in the SDK Manager, or let AGP download it).

> [!NOTE]
> On Windows, if a Chinese path triggers a Java Unix-domain socket error, build the project from an ASCII path. Use your own signing key for real releases.

## Contributing

- [Third-party dependencies and licences](THIRD_PARTY_NOTICES.md)
- [Full GPL-3.0 text](LICENSE)

## License

The NSFX engine is ported from [Hanabi-Download-Manager-X](https://github.com/buaoyezz/Hanabi-Download-Manager-X) (baseline commit `5df83d3`), and parts of the UI reference the about pages of SukiSU-Ultra and XBlocker; the complete application is distributed under GPL-3.0, see [LICENSE](LICENSE). Upstream and component attribution is documented in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Acknowledgements

- [LSPosed](https://github.com/LSPosed/LSPosed): module runtime framework
- [Hanabi-Download-Manager-X](https://github.com/buaoyezz/Hanabi-Download-Manager-X): source of the NSFX multi-threaded engine
- [SukiSU-Ultra](https://github.com/SukiSU-Ultra/SukiSU-Ultra): source of the UI and some components
- [XBlocker](https://github.com/bileizhen/XBlocker): source of the UI shell and the LSPosed scope service
- [Miuix](https://github.com/compose-miuix-ui/miuix): UI component library
- [libxposed/service](https://github.com/libxposed/service): reference for the module service binder protocol

## Views

<div align="center">

![:shell](https://count.getloli.com/@bileizhen_LeiFetch?name=bileizhen_LeiFetch&theme=original-new&padding=7&offset=0&align=center&scale=1&pixelated=1&darkmode=auto)

</div>
