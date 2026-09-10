package io.github.bileizhen.leifetch

import org.junit.Assert.*
import org.junit.Test

class HookPluginsTest {
    @Test fun 启用系统插件时作用域并入系统包() {
        val scope = HookPlugins.packages(Config(packages = "org.example.app", plugins = "generic,system"))
        assertTrue("com.android.providers.downloads" in scope)
        assertTrue("com.android.providers.downloads.ui" in scope)
        assertTrue("org.example.app" in scope)
    }

    @Test fun 未启用系统插件时作用域不含系统包() {
        val scope = HookPlugins.packages(Config(packages = "org.example.app", plugins = "generic"))
        assertFalse("com.android.providers.downloads" in scope)
        assertFalse("com.android.providers.downloads.ui" in scope)
    }

    @Test fun 系统插件声明的作用域与常量一致() {
        val system = HookPlugins.all.first { it.id == "system" }
        assertEquals(SystemDownloads.packages, system.packages)
    }

    @Test fun 插件开关解析过滤空白片段() {
        assertEquals(setOf("generic", "system"), HookPlugins.enabled("generic,, system,"))
        assertEquals(emptySet<String>(), HookPlugins.enabled(""))
    }

    @Test fun 每个插件都有卡片摘要() {
        assertTrue(HookPlugins.all.all { it.blurb.isNotBlank() })
    }

    @Test fun 作用域申请汇总固定作用域插件并去重() {
        val scopes = HookPlugins.scopeRequestFor(Config(plugins = "system,firefox"))
        assertTrue("com.android.providers.downloads" in scopes)
        assertTrue("com.android.providers.downloads.ui" in scopes)
        assertTrue("org.mozilla.firefox" in scopes)
        assertEquals(scopes.distinct().size, scopes.size)
    }

    @Test fun 仅通用插件时无需申请作用域() {
        assertTrue(HookPlugins.scopeRequestFor(Config(plugins = "generic")).isEmpty())
    }
}
