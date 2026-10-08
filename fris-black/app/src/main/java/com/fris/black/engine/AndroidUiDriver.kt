package com.fris.black.engine

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.fris.black.data.AppResolver
import com.fris.black.service.FrisAccessibilityService
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

// Driver real: traduz passos FrisScript -> AccessibilityService + Intents.
// Se o serviço estiver desligado, ações de UI falham com mensagem clara
// (nunca "faz de qualquer jeito").
class AndroidUiDriver(
    private val ctx: Context,
    private val activityCtx: (() -> Context)? = null
) : UiDriver {

    private fun svc(): FrisAccessibilityService? = FrisAccessibilityService.instance

    override suspend fun openApp(appName: String): Boolean {
        return try {
            val pkg = AppResolver.resolve(ctx, appName) ?: return false
            val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
            delay(1200)
            true
        } catch (e: Exception) { false }
    }

    override suspend fun clickText(text: String): Boolean {
        val s = svc() ?: return false
        val n = s.findByText(text) ?: return false
        return s.clickNode(n)
    }

    override suspend fun clickId(id: String): Boolean {
        val s = svc() ?: return false
        val n = s.findById(id) ?: return false
        return s.clickNode(n)
    }

    override suspend fun clickXy(x: Int, y: Int): Boolean {
        val s = svc() ?: return false
        return s.tap(x.toFloat(), y.toFloat())
    }

    override suspend fun typeText(text: String): Boolean {
        val s = svc() ?: return false
        val f = s.focusedEditable()
            ?: s.findByText("") // força root
            ?: return false
        // Se o foco não é editável, tenta clicar primeiro num campo genérico
        val target = if (f.isEditable) f else return false
        return s.setText(target, text)
    }

    override suspend fun clearText(): Boolean {
        val s = svc() ?: return false
        val f = s.focusedEditable() ?: return false
        return s.setText(f, "")
    }

    override suspend fun scrollDown(): Boolean {
        val s = svc() ?: return false
        return s.scroll(null, true) || s.swipe("BAIXO")
    }

    override suspend fun scrollUp(): Boolean {
        val s = svc() ?: return false
        return s.scroll(null, false) || s.swipe("CIMA")
    }

    override suspend fun swipe(dir: String): Boolean {
        val s = svc() ?: return false
        return s.swipe(dir)
    }

    override suspend fun waitAppear(text: String, timeoutSec: Int): Boolean {
        val s = svc() ?: return false
        val end = System.currentTimeMillis() + timeoutSec * 1000L
        while (System.currentTimeMillis() < end) {
            if (s.findByText(text) != null) return true
            delay(500)
        }
        return false
    }

    override suspend fun waitGone(text: String, timeoutSec: Int): Boolean {
        val s = svc() ?: return false
        val end = System.currentTimeMillis() + timeoutSec * 1000L
        while (System.currentTimeMillis() < end) {
            if (s.findByText(text) == null) return true
            delay(500)
        }
        return false
    }

    override suspend fun back(): Boolean {
        return svc()?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK) ?: false
    }

    override suspend fun home(): Boolean {
        return svc()?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME) ?: false
    }

    override suspend fun closeKeyboard(): Boolean {
        svc()?.rootInActiveWindow?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLEAR_FOCUS)
        return back()
    }

    override fun toast(text: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(ctx, text, Toast.LENGTH_LONG).show()
        }
    }

    override suspend fun confirm(question: String): Boolean {
        val actx = try { activityCtx?.invoke() } catch (e: Exception) { null } ?: return false
        return suspendCancellableCoroutine { cont ->
            Handler(Looper.getMainLooper()).post {
                AlertDialog.Builder(actx)
                    .setTitle("Fris Black — confirmar")
                    .setMessage(question)
                    .setCancelable(false)
                    .setPositiveButton("Confirmar") { _, _ -> cont.resume(true) }
                    .setNegativeButton("Cancelar") { _, _ -> cont.resume(false) }
                    .show()
            }
        }
    }

    override fun isConfirmedBranch(): Boolean? = null
    override fun log(text: String) { android.util.Log.i("FrisBlack", text) }
}
