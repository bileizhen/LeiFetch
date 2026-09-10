package io.github.bileizhen.leifetch

import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

/**
 * libxposed API 101 入口：声明本模块支持新 API 后，框架才会把服务 binder 投递到模块应用
 * （XposedServiceProvider 的 SendBinder），作用域查询 / 申请依赖它。
 * API 101 仍允许 legacy 钩子调用，故直接复用 HookEntry；旧框架继续走 assets/xposed_init。
 * 方案与 XBlocker 的 ModernXHook 一致（同一作者授权移植，见 THIRD_PARTY_NOTICES.md）。
 */
class ModernHookEntry : XposedModule() {
    private val hook = HookEntry()

    override fun onPackageReady(param: PackageReadyParam) {
        hook.handle(param.packageName, param.classLoader)
    }
}
