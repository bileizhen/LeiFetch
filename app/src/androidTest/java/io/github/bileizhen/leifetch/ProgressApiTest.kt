package io.github.bileizhen.leifetch

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.lang.reflect.Modifier

/** 设备固件上 Notification.ProgressStyle 的真实 API 签名探测（36.1 API 与 compileSdk 37 有差异）。 */
@RunWith(AndroidJUnit4::class)
class ProgressApiTest {
    @Test fun dumpProgressStyleApi() {
        val out = StringBuilder()
        val style = android.app.Notification.ProgressStyle::class.java
        out.append("methods:\n")
        style.declaredMethods.filter { !Modifier.isStatic(it.modifiers) }
            .sortedBy { it.name }.forEach { out.append("  ${it.returnType.simpleName} ${it.name}(${it.parameterTypes.joinToString { p -> p.simpleName }})\n") }
        for (name in listOf("ProgressTracker", "Point", "Segment")) {
            val cls = runCatching { Class.forName("android.app.Notification\$ProgressStyle\$$name") }.getOrNull()
            if (cls == null) { out.append("$name: MISSING\n"); continue }
            out.append("$name isInterface=${cls.isInterface}\n")
            cls.declaredConstructors.forEach { out.append("  ctor(${it.parameterTypes.joinToString { p -> p.name }})\n") }
            cls.declaredMethods.filter { it.declaringClass == cls }.forEach {
                out.append("  ${it.returnType.simpleName} ${it.name}(${it.parameterTypes.joinToString { p -> p.simpleName }})\n")
            }
        }
        val f = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "api_dump.txt")
        f.writeText(out.toString())
    }
}
