package io.github.bileizhen.leifetch

data class HookPlugin(val id: String, val name: String, val summary: String, val packages: Set<String>, val version: String, val blurb: String)

object HookPlugins {
    val all = listOf(
        HookPlugin("generic", "通用下载捕获", "DownloadManager、OkHttp、HttpURLConnection 与 WebView；手动指定应用范围。", emptySet(), "1.0",
            "捕获常见应用的下载请求"),
        HookPlugin("firefox", "Firefox 下载接管", "支持公开直链、重定向与单连接下载；需要浏览器登录状态的资源仍由 Firefox 下载。",
            setOf("org.mozilla.firefox", "org.mozilla.firefox_beta", "org.mozilla.fenix"), "1.1", "接管 Firefox 的公开文件下载"),
        HookPlugin("system", "系统下载器接管",
            "在系统下载提供器进程内集中捕获所有应用经 DownloadManager 入队的下载，无需逐个勾选应用；原任务保留，由你在待确认列表决定是否改用多线程。" +
                "系统下载列表与下载通知入口（查看下载）改为打开 LeiFetch 下载页，打开单个已完成文件的入口保持系统行为。已由通用插件在应用内上报的入队不会重复捕获。" +
                "开启时自动向 LSPosed 申请本插件作用域，授权后重启生效。",
            SystemDownloads.packages, "1.0", "集中接管系统 DownloadManager 与下载界面"),
    )
    fun enabled(ids: String) = ids.split(',').mapNotNull { it.trim().takeIf(String::isNotEmpty) }.toSet()
    fun packages(config: Config): Set<String> = config.packages.split(Regex("[\\s,;]+")).filter { it.isNotBlank() }.toSet() +
        all.filter { it.id in enabled(config.plugins) }.flatMap { it.packages }
    /** 启用插件时向 LSPosed 申请的作用域包名（固定作用域插件合并去重；通用插件为空，由用户手动填写）。 */
    fun scopeRequestFor(config: Config): List<String> =
        all.filter { it.id in enabled(config.plugins) && it.packages.isNotEmpty() }
            .flatMap { it.packages }.distinct()
}
