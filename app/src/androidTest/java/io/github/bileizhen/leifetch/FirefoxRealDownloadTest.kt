package io.github.bileizhen.leifetch

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.bileizhen.leifetch.nsfx.NsfxConfig
import io.github.bileizhen.leifetch.nsfx.NsfxKernel
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipFile
import android.util.Base64

/** Opt-in real-network test. URL stays in a device file; no signed URL is logged or committed. */
@RunWith(AndroidJUnit4::class)
class FirefoxRealDownloadTest {
    @Test fun downloadUserProvidedApk() = runBlocking {
        val urlFile = InstrumentationRegistry.getArguments().getString("urlFile")
        val argUrl = InstrumentationRegistry.getArguments().getString("url")
        val encodedUrl = InstrumentationRegistry.getArguments().getString("urlBase64")
        assumeTrue("Pass a user-authorized URL, base64 URL, or URL file to run a real download", urlFile != null || argUrl != null || encodedUrl != null)
        val c = InstrumentationRegistry.getInstrumentation().targetContext
        c.app.ready.await()
        val rawUrl = (argUrl ?: encodedUrl?.let { String(Base64.decode(it, Base64.DEFAULT), Charsets.UTF_8) }
            ?: File(urlFile!!).readText()).trim()
        val reportFile = File(c.filesDir, "firefox-real-download-report.json")
        val report = JSONObject().put("stage", "response")
        fun reportStage(stage: String) { report.put("stage", stage); reportFile.writeText(report.toString(2)) }
        reportStage("response")
        // Obtain download response metadata without consuming the APK. This exercises the same
        // admission function as the hook, but does not pretend to drive Firefox's actual UI.
        val connection = URL(rawUrl).openConnection() as HttpURLConnection
        val headers = try {
            connection.connectTimeout = 12_000
            connection.readTimeout = 12_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android 16; Mobile; rv:155.0) Gecko/155.0 Firefox/155.0")
            connection.setRequestProperty("Accept-Encoding", "identity")
            val code = connection.responseCode
            report.put("httpStatus", code)
            val metadata = listOf("ETag", "Content-Length", "Content-Type", "Content-Disposition", "Accept-Ranges")
                .mapNotNull { key -> connection.getHeaderField(key)?.let { key to it } }.toMap()
            report.put("responseHeaders", JSONObject(metadata))
            reportStage("admission")
            if (code in 200..299) metadata else emptyMap()
        } catch (e: Exception) {
            report.put("errorType", e.javaClass.simpleName)
            reportStage("response-failed")
            throw AssertionError("Download endpoint connection failed: ${e.javaClass.simpleName}")
        } finally { connection.disconnect() }
        var rejection = ""
        val verified = FirefoxProbe.verify(rawUrl, headers, 12_000, onRejected = { rejection = it })
        if (verified == null) {
            report.put("reason", rejection)
            reportStage("admission-rejected")
            fail("Firefox admission: $rejection")
        }
        report.put("expectedBytes", verified!!.totalLength)
        val task = Task(url = verified.url, name = "QQ_9.3.60_23e3f34e30110797.apk", source = "Firefox 真实链接验证")
        c.app.store.put(task)
        report.put("taskId", task.id)
        val kernel = withContext(Dispatchers.Main) {
            NsfxKernel(c, NsfxConfig(threads = 4, maxRetries = 2)) {}.also { it.startDownload(task.id) }
        }
        try {
            withTimeout(10 * 60_000) {
                while (true) {
                    val current = c.app.store.get(task.id)!!
                    report.put("downloadState", current.state).put("done", current.done).put("total", current.total).put("speed", current.speed)
                    reportStage("download")
                    if (current.state == "已完成") break
                    if (current.state == "失败") {
                        reportStage("download-failed")
                        fail("NSFX real download failed; inspect the task's error in LeiFetch")
                    }
                    delay(1000)
                }
            }
            val completed = c.app.store.get(task.id)!!
            if (verified.totalLength >= 0) assertEquals(verified.totalLength, completed.done)
            val file = File(c.filesDir, "downloads/${task.id}/${task.name}")
            assertEquals(completed.done, file.length())
            val digest = MessageDigest.getInstance("SHA-256")
            c.contentResolver.openInputStream(Uri.parse(completed.uri))!!.use { stream ->
                val buffer = ByteArray(128 * 1024)
                while (true) { val n = stream.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
            }
            ZipFile(file).use { zip -> assertNotNull("Downloaded file must be an APK", zip.getEntry("AndroidManifest.xml")) }
            val archive = c.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
            assertNotNull("Android must recognize the APK", archive)
            assertEquals("com.tencent.mobileqq", archive!!.packageName)
            report.put("sha256", digest.digest().joinToString("") { "%02x".format(it) })
                .put("packageName", archive.packageName).put("versionName", archive.versionName)
                .put("filePath", file.absolutePath)
            reportStage("verified")
        } finally { withContext(Dispatchers.Main) { kernel.stop() } }
    }
}
