// SPDX-License-Identifier: GPL-3.0-only
package io.github.bileizhen.leifetch.nsfx

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import io.github.bileizhen.leifetch.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.io.IOException

class FilePublisher(private val context: Context) {
    fun recover(task: Task): Pair<String, Long>? {
        val journal = AtomicFile(File(workDir(context, task.id), "publish.json"))
        val data = runCatching { JSONObject(String(journal.readFully())) }.getOrNull() ?: return null
        val uri = Uri.parse(data.getString("uri"))
        if (data.optBoolean("complete")) {
            val size = data.getLong("size")
            val actual = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
            if (actual == size || actual == -1L) return uri.toString() to size
            throw IOException("已发布文件长度已改变")
        }
        if (uri.authority == "$PKG.files") context.contentResolver.delete(uri, null, null)
        else DocumentFile.fromSingleUri(context, uri)?.let { if (it.exists()) require(it.delete()) }
        journal.delete()
        return null
    }
    suspend fun publish(task: Task, source: File): String = withContext(Dispatchers.IO) {
        val journal = AtomicFile(File(workDir(context, task.id), "publish.json"))
        var localFile: File? = null
        val uri = if (task.tree.isEmpty()) {
            localFile = File(context.filesDir, "downloads/${task.id}/${safeName(task.name)}")
            localFile.parentFile!!.mkdirs()
            FileProvider.getUriForFile(context, "$PKG.files", localFile)
        } else {
            val root = DocumentFile.fromTreeUri(context, Uri.parse(task.tree)) ?: throw IOException("目录不可用")
            root.createFile("application/octet-stream", safeName(task.name))?.uri ?: throw IOException("无法创建文件")
        }
        val record = JSONObject().put("uri", uri.toString()).put("size", source.length()).put("complete", false)
        NsfxStorage.atomic(journal, record.toString())
        try {
            if (localFile != null) {
                currentCoroutineContext().ensureActive()
                if (!source.renameTo(localFile)) throw IOException("文件移动失败")
            } else {
                context.contentResolver.openOutputStream(uri, "wt")!!.use { output ->
                    source.inputStream().use { input ->
                        val buffer = ByteArray(128 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                        }
                    }
                }
            }
            NsfxStorage.atomic(journal, record.put("complete", true).toString())
            Logs.i(LogSource.DOWNLOAD, "${task.name} 已保存到${if (task.tree.isEmpty()) "应用内 downloads 目录" else displayTree(task.tree)}")
            uri.toString()
        } catch (e: Throwable) {
            if (localFile != null) localFile.delete() else DocumentFile.fromSingleUri(context, uri)?.delete()
            journal.delete()
            throw e
        }
    }
}
