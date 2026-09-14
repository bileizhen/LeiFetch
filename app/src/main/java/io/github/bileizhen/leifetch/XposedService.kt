package io.github.bileizhen.leifetch

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 接收 LSPosed 守护进程推入模块进程的 libxposed 服务 binder（`<包名>.XposedService` 提供者，SendBinder）。
 * 移植自 XBlocker 的 XposedServiceProvider（同一作者授权，GPL-3.0，见 THIRD_PARTY_NOTICES.md）：
 * 用手写 binder Parcel 调用 IXposedService 查询 / 申请作用域，避免中文路径下 AGP 的 AIDL 解析问题。
 */
class XposedServiceProvider : ContentProvider() {
    override fun onCreate(): Boolean = true
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?): Int = 0

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        // 只有框架守护进程（root/system）可以注入服务 binder。
        if (method == XposedServiceClient.SEND_BINDER && Binder.getCallingUid() in SYSTEM_UIDS) {
            @Suppress("DEPRECATION") val binder = extras?.getBinder("binder")
            if (binder != null) XposedServiceClient.onBinder(binder)
            return Bundle.EMPTY
        }
        return null
    }

    private companion object {
        val SYSTEM_UIDS = setOf(0, 1000)
    }
}

object XposedServiceClient {
    private const val TAG = "LeiFetchScope"
    /** AIDL 方法号是相对 FIRST_CALL_TRANSACTION 的偏移，不是绝对 wire code。
     * https://github.com/libxposed/service/tree/master/interface/src/main/aidl/io/github/libxposed/service
     */
    private const val DESCRIPTOR = "io.github.libxposed.service.IXposedService"
    private const val CALLBACK_DESCRIPTOR = "io.github.libxposed.service.IXposedScopeCallback"
    private const val TRANSACTION_GET_SCOPE = IBinder.FIRST_CALL_TRANSACTION + 10
    private const val TRANSACTION_REQUEST_SCOPE = IBinder.FIRST_CALL_TRANSACTION + 11
    private const val CALLBACK_APPROVED = IBinder.FIRST_CALL_TRANSACTION + 1
    private const val CALLBACK_FAILED = IBinder.FIRST_CALL_TRANSACTION + 2
    const val SEND_BINDER = "SendBinder"

    private val main = Handler(Looper.getMainLooper())
    @Volatile private var binder: IBinder? = null
    private val waiting = CopyOnWriteArrayList<(IBinder) -> Unit>()
    // 与 libxposed 的回调生命周期一致：保留到完成，发送失败即释放。
    private val scopeCallbacks = ConcurrentHashMap.newKeySet<IBinder>()

    fun onBinder(raw: IBinder) {
        if (!raw.pingBinder()) return
        android.util.Log.i(TAG, "libxposed 服务 binder 已到达")
        Logs.i(LogSource.PLUGIN, "已连接 LSPosed 服务，可申请作用域")
        binder = raw
        val callbacks = waiting.toList()
        waiting.clear()
        for (callback in callbacks) runCatching { main.post { callback(raw) } }
    }

    fun connected(): Boolean = binder?.isBinderAlive == true

    /** 当前已授予的作用域包名；调用失败或框架不支持时返回 null。 */
    fun scope(): List<String>? {
        val target = binder ?: return null
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(DESCRIPTOR)
            if (!target.transact(TRANSACTION_GET_SCOPE, data, reply, 0)) return null
            reply.readException()
            reply.createStringArrayList().orEmpty()
        } catch (error: Exception) {
            android.util.Log.d(TAG, "scope: ${error.javaClass.simpleName}: ${error.message?.take(120)}")
            Logs.w(LogSource.PLUGIN, "读取作用域失败：${error.javaClass.simpleName}")
            null
        } finally {
            reply.recycle(); data.recycle()
        }
    }

    /**
     * 请求框架就给定包名弹出授权确认；oneway 结果经 [onResult] 回到主线程。
     * approved 非 null 表示用户同意（含实际批准的包名），error 为框架返回的失败说明。
     */
    fun requestScope(packages: List<String>, onResult: (approved: List<String>?, error: String?) -> Unit) {
        val target = binder
        if (target == null || !target.isBinderAlive) {
            main.post { onResult(null, "LSPosed 服务未连接") }
            return
        }
        val callback = object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean = when (code) {
                IBinder.INTERFACE_TRANSACTION -> {
                    reply?.writeString(CALLBACK_DESCRIPTOR)
                    true
                }
                CALLBACK_APPROVED -> {
                    data.enforceInterface(CALLBACK_DESCRIPTOR)
                    val approved = data.createStringArrayList().orEmpty()
                    if (scopeCallbacks.remove(this)) main.post {
                        Logs.i(LogSource.PLUGIN, "作用域已授权：${approved.joinToString("、").ifEmpty { "无" }}")
                        onResult(approved, null)
                    }
                    true
                }
                CALLBACK_FAILED -> {
                    data.enforceInterface(CALLBACK_DESCRIPTOR)
                    val message = data.readString() ?: "授权未完成"
                    if (scopeCallbacks.remove(this)) main.post {
                        Logs.w(LogSource.PLUGIN, "作用域授权未完成：$message")
                        onResult(null, message)
                    }
                    true
                }
                else -> super.onTransact(code, data, reply, flags)
            }
        }
        scopeCallbacks.add(callback)
        val data = Parcel.obtain()
        val sent = try {
            data.writeInterfaceToken(DESCRIPTOR)
            data.writeStringList(packages)
            data.writeStrongBinder(callback)
            target.transact(TRANSACTION_REQUEST_SCOPE, data, null, IBinder.FLAG_ONEWAY)
        } catch (error: Exception) {
            android.util.Log.d(TAG, "requestScope: ${error.javaClass.simpleName}: ${error.message?.take(120)}")
            Logs.e(LogSource.PLUGIN, "作用域请求发送失败：${error.javaClass.simpleName}")
            false
        } finally {
            data.recycle()
        }
        if (!sent && scopeCallbacks.remove(callback)) main.post { onResult(null, "请求发送失败") }
    }
}
