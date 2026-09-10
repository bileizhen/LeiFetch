package io.github.bileizhen.leifetch

data class HookPlugin(val id: String, val name: String, val summary: String, val packages: Set<String>, val version: String)

object HookPlugins {
    val all = listOf(
        HookPlugin("generic", "通用下载捕获", "DownloadManager、OkHttp、HttpURLConnection 与 WebView；手动指定应用范围。", emptySet(), "1.0"),
        HookPlugin("firefox", "Firefox 下载接管", "支持公开直链、重定向与单连接下载；需要浏览器登录状态的资源仍由 Firefox 下载。",
            setOf("org.mozilla.firefox", "org.mozilla.firefox_beta", "org.mozilla.fenix"), "1.1")
    )
    fun enabled(ids: String) = ids.split(',').filter { it.isNotBlank() }.toSet()
    fun packages(config: Config): Set<String> = config.packages.split(Regex("[\\s,;]+")).filter { it.isNotBlank() }.toSet() +
        all.filter { it.id in enabled(config.plugins) }.flatMap { it.packages }
}
