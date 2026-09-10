package io.github.bileizhen.leifetch

import org.junit.Assert.*
import org.junit.Test

class SystemDownloadsTest {
    @Test fun 作用域覆盖提供器与界面两个包() {
        assertEquals(setOf("com.android.providers.downloads", "com.android.providers.downloads.ui"), SystemDownloads.packages)
    }

    @Test fun 接管入口覆盖查看下载与通知点击() {
        assertEquals(setOf(
            "android.intent.action.VIEW_DOWNLOADS",
            "android.intent.action.DOWNLOAD_NOTIFICATION_CLICKED",
        ), SystemDownloads.takeOverActions)
    }

    @Test fun 识别带标题的入队() {
        assertEquals("https://example.com/a/file.zip" to "年度报告.zip",
            SystemDownloads.insertRequest("https://example.com/a/file.zip", "年度报告.zip", null))
    }

    @Test fun 标题缺省时用文件名提示列() {
        assertEquals("http://example.com/dl" to "setup.exe",
            SystemDownloads.insertRequest("http://example.com/dl", " ", "/storage/emulated/0/Download/setup.exe"))
    }

    @Test fun 标题与提示缺省时从地址推断文件名() {
        assertEquals("https://cdn.example.com/path/app.apk?token=1#frag" to "app.apk",
            SystemDownloads.insertRequest("https://cdn.example.com/path/app.apk?token=1#frag", null, null))
    }

    @Test fun 地址末段为空时回退通用文件名() {
        assertEquals("https://example.com/" to "download.bin",
            SystemDownloads.insertRequest("https://example.com/", null, null))
    }

    @Test fun 非下载行或非HTTP地址不捕获() {
        assertNull(SystemDownloads.insertRequest(null, "t", "h"))
        assertNull(SystemDownloads.insertRequest("", "t", "h"))
        assertNull(SystemDownloads.insertRequest("ftp://example.com/file", null, null))
        assertNull(SystemDownloads.insertRequest("content://downloads/my_downloads", null, null))
    }

    @Test fun 大小写协议前缀仍可识别() {
        assertNotNull(SystemDownloads.insertRequest("HTTPS://example.com/file.apk", null, null))
    }
}
