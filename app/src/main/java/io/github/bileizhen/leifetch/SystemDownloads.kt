package io.github.bileizhen.leifetch

/** 系统下载器接管插件的可测纯逻辑：作用域包名、下载入口 Action 与 DownloadProvider 行识别。 */
object SystemDownloads {
    const val PROVIDER = "com.android.providers.downloads"
    const val UI = "com.android.providers.downloads.ui"
    val packages = setOf(PROVIDER, UI)

    /** 系统打开下载列表 / 点击下载通知所用的入口 Action；接管后由 LeiFetch 下载页呈现。 */
    val takeOverActions = setOf(
        "android.intent.action.VIEW_DOWNLOADS",
        "android.intent.action.DOWNLOAD_NOTIFICATION_CLICKED",
    )

    /**
     * 从 DownloadProvider.insert 的行数据识别一次下载入队，返回 url 与展示文件名。
     * 非下载行（如请求头表插入没有 uri 列）或非 HTTP(S) 地址返回 null。
     * 列名取自 Downloads.Impl 的稳定契约：uri / title / hint。
     */
    fun insertRequest(url: String?, title: String?, hint: String?): Pair<String, String>? {
        if (url.isNullOrBlank()) return null
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) return null
        val name = title?.trim()?.takeIf { it.isNotEmpty() }
            ?: hint?.trim()?.takeIf { it.isNotEmpty() }?.substringAfterLast('/')
            ?: url.substringBefore('#').substringBefore('?').substringAfterLast('/').takeIf { it.isNotEmpty() }
            ?: "download.bin"
        return url to name
    }
}
