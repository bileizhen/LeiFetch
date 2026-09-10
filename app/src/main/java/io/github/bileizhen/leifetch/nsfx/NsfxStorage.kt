// SPDX-License-Identifier: GPL-3.0-only
package io.github.bileizhen.leifetch.nsfx

import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class Segment(val index: Int, val start: Long, @Volatile var end: Long, @Volatile var downloaded: Long = 0) {
    @Volatile var speed = 0.0
    @Volatile var lastProgressNs = System.nanoTime()
    var lastSplitNs = 0L
    val size get() = end - start
    val remaining get() = (size - downloaded).coerceAtLeast(0)
}

data class FileInfo(val url: String, val size: Long, val etag: String, val supportsRange: Boolean)

class NsfxStorage(val dir: File) {
    val partial = File(dir, "data.nsfx_partial")
    private val journal = AtomicFile(File(dir, "segments.json"))
    fun load(info: FileInfo): MutableList<Segment>? = runCatching {
        val j = JSONObject(String(journal.readFully()))
        require(info.etag.isNotEmpty() && info.supportsRange)
        require(j.getString("url") == info.url && j.getString("etag") == info.etag && j.getLong("size") == info.size)
        require(partial.exists() && partial.length() == info.size)
        val a = j.getJSONArray("segments")
        require(a.length() in 1..256)
        val list = (0 until a.length()).map { i ->
            val s = a.getJSONObject(i)
            Segment(s.getInt("id"), s.getLong("start"), s.getLong("end")).also { segment ->
                val offset = runCatching { String(marker(segment.index).readFully()).toLong() }.getOrDefault(0)
                segment.downloaded = offset.takeIf { it in 0..segment.size } ?: 0
            }
        }.toMutableList()
        verifyCoverage(list, info.size)
        list
    }.getOrNull()
    fun reset() { dir.listFiles()?.forEach { require(it.delete()) { "无法清理断点文件" } } }
    fun save(info: FileInfo, segments: List<Segment>) {
        verifyCoverage(segments, info.size)
        val a = JSONArray()
        for (segment in segments) a.put(JSONObject().put("id", segment.index).put("start", segment.start).put("end", segment.end))
        atomic(journal, JSONObject().put("url", info.url).put("etag", info.etag).put("size", info.size).put("segments", a).toString())
    }
    fun checkpoint(segment: Segment) = atomic(marker(segment.index), segment.downloaded.toString())
    private fun marker(id: Int) = AtomicFile(File(dir, "$id.offset"))
    companion object {
        fun verifyCoverage(segments: List<Segment>, size: Long) {
            require(segments.map { it.index }.distinct().size == segments.size)
            var position = 0L
            segments.sortedBy { it.start }.forEach {
                require(it.index in 0..255 && it.start == position && it.end > it.start && it.end <= size) { "分段存在重叠或缺口" }
                position = it.end
            }
            require(position == size)
        }
        fun atomic(file: AtomicFile, value: String) {
            val output = file.startWrite()
            try { output.write(value.toByteArray()); file.finishWrite(output) }
            catch (e: Throwable) { file.failWrite(output); throw e }
        }
    }
}
