package io.github.bileizhen.leifetch

import org.junit.Assert.*
import org.junit.Test

class LogsTest {
    private fun entry(level: LogLevel = LogLevel.INFO, source: String = LogSource.DOWNLOAD,
                      message: String, time: Long = 0L) = LogEntry(time, level, source, message)

    @Test fun 超出容量后淘汰最旧的() {
        val buffer = LogBuffer(capacity = 3)
        repeat(5) { buffer.add(entry(message = "第 $it 条")) }
        assertEquals(3, buffer.size())
        assertEquals(listOf("第 2 条", "第 3 条", "第 4 条"), buffer.snapshot().map { it.message })
    }

    @Test fun 快照按写入顺序且不受后续写入影响() {
        val buffer = LogBuffer(capacity = 4)
        buffer.add(entry(message = "a"))
        val first = buffer.snapshot()
        buffer.add(entry(message = "b"))
        assertEquals(listOf("a"), first.map { it.message })
        assertEquals(listOf("a", "b"), buffer.snapshot().map { it.message })
    }

    @Test fun 清空后不再返回旧内容() {
        val buffer = LogBuffer(capacity = 4)
        buffer.add(entry(message = "a"))
        buffer.clear()
        assertEquals(0, buffer.size())
        assertTrue(buffer.snapshot().isEmpty())
        assertTrue(buffer.sources().isEmpty())
    }

    @Test fun 按级别过滤() {
        val entries = listOf(
            entry(LogLevel.INFO, message = "开始下载"),
            entry(LogLevel.WARN, message = "主机限流"),
            entry(LogLevel.ERROR, message = "下载失败"),
        )
        assertEquals(listOf("主机限流"), filterLogs(entries, level = LogLevel.WARN).map { it.message })
        assertEquals(listOf("下载失败"), filterLogs(entries, level = LogLevel.ERROR).map { it.message })
        assertEquals(3, filterLogs(entries, level = null).size)
    }

    @Test fun 按来源过滤() {
        val entries = listOf(
            entry(source = LogSource.DOWNLOAD, message = "a"),
            entry(source = LogSource.FIREFOX, message = "b"),
            entry(source = LogSource.SYSTEM, message = "c"),
        )
        assertEquals(listOf("b"), filterLogs(entries, source = LogSource.FIREFOX).map { it.message })
        assertEquals(3, filterLogs(entries, source = null).size)
    }

    // 关键字同时匹配正文与来源，忽略大小写：查「firefox」也能命中来源列。
    @Test fun 关键字同时匹配正文与来源() {
        val entries = listOf(
            entry(source = LogSource.DOWNLOAD, message = "新建任务：Kernel-Src.tar.gz"),
            entry(source = LogSource.FIREFOX, message = "已接管下载"),
            entry(source = LogSource.MIRROR, message = "经镜像加速：https://ghfast.top"),
        )
        assertEquals(listOf("新建任务：Kernel-Src.tar.gz"), filterLogs(entries, query = "kernel").map { it.message })
        assertEquals(listOf("已接管下载"), filterLogs(entries, query = "FIREFOX").map { it.message })
        assertEquals(3, filterLogs(entries, query = "   ").size)
        assertTrue(filterLogs(entries, query = "不存在的关键字").isEmpty())
    }

    @Test fun 级别与来源与关键字同时生效() {
        val entries = listOf(
            entry(LogLevel.WARN, LogSource.FIREFOX, "保留 Firefox 下载"),
            entry(LogLevel.WARN, LogSource.SYSTEM, "未找到 DownloadProvider"),
            entry(LogLevel.INFO, LogSource.FIREFOX, "已接管下载"),
        )
        val filtered = filterLogs(entries, level = LogLevel.WARN, source = LogSource.FIREFOX, query = "保留")
        assertEquals(listOf("保留 Firefox 下载"), filtered.map { it.message })
    }

    @Test fun 来源列表按首次出现顺序去重() {
        val buffer = LogBuffer(capacity = 8)
        buffer.add(entry(source = LogSource.DOWNLOAD, message = "a"))
        buffer.add(entry(source = LogSource.FIREFOX, message = "b"))
        buffer.add(entry(source = LogSource.DOWNLOAD, message = "c"))
        assertEquals(listOf(LogSource.DOWNLOAD, LogSource.FIREFOX), buffer.sources())
    }

    @Test fun 解析级别名() {
        assertEquals(LogLevel.INFO, LogLevel.of("INFO"))
        assertEquals(LogLevel.WARN, LogLevel.of("WARN"))
        assertEquals(LogLevel.ERROR, LogLevel.of("ERROR"))
        assertEquals(LogLevel.INFO, LogLevel.of("未知"))
        assertEquals(LogLevel.INFO, LogLevel.of(null))
    }

    // 隐私：日志里的地址只保留主机，查询串（可能带签名或凭据）不落进日志。
    @Test fun 地址只保留主机() {
        assertEquals("github.com", logHost("https://github.com/user/repo/releases/download/v1/app.apk"))
        assertEquals("release-assets.githubusercontent.com",
            logHost("https://release-assets.githubusercontent.com/x?sp=r&se=2026-09-10T16%3A40%3A04Z&sig=secret"))
        assertEquals("未知主机", logHost("not a url"))
        assertEquals("未知主机", logHost(""))
    }

    @Test fun 导出文本包含级别来源与正文() {
        val text = LogExport.text(listOf(
            LogEntry(1_700_000_000_000L, LogLevel.WARN, LogSource.FIREFOX, "保留 Firefox 下载"),
            LogEntry(1_700_000_001_000L, LogLevel.INFO, LogSource.DOWNLOAD, "新建任务：a.apk（github.com）"),
        ))
        assertTrue(text.contains("共 2 条") || text.contains("2 条"))
        assertTrue(text.contains("警告"))
        assertTrue(text.contains("保留 Firefox 下载"))
        assertTrue(text.contains("[Firefox]"))
        assertTrue(text.contains("新建任务：a.apk（github.com）"))
    }
}
