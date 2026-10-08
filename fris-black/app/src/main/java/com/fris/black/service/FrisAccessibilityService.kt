package com.fris.black.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume

// ---------------------------------------------------------------------------
// Motor nível 2 — AccessibilityService.
// Baseado nas melhores práticas de AutoJs6 / AutoTask / OpenRing / GhostUser:
// - busca por texto/id/descrição (BFS), nunca só coordenada
// - dispatchGesture para swipe/scroll (human-like, 400ms)
// - suspend functions: o executor espera o gesto terminar antes do próximo passo
// ---------------------------------------------------------------------------

class FrisAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile var instance: FrisAccessibilityService? = null
            private set
        fun isOn(): Boolean = instance != null
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val gestureMutex = Mutex()

    override fun onServiceConnected() {
        instance = this
        super.onServiceConnected()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    // ---- busca ----

    fun findByText(text: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        // 1) API direta
        root.findAccessibilityNodeInfosByText(text).firstOrNull()?.let { return it }
        // 2) BFS em contentDescription + text (case-insensitive)
        val q = text.lowercase()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            val t = (n.text?.toString() ?: "") + " " + (n.contentDescription?.toString() ?: "")
            if (t.lowercase().contains(q) && (n.isClickable || n.isFocusable || parentClickable(n))) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return null
    }

    private fun parentClickable(n: AccessibilityNodeInfo): Boolean {
        var p = n.parent
        var d = 0
        while (p != null && d < 3) { if (p.isClickable) return true; p = p.parent; d++ }
        return false
    }

    fun findById(viewId: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        // tenta id completo e sufixo
        root.findAccessibilityNodeInfosByViewId(viewId).firstOrNull()?.let { return it }
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            if ((n.viewIdResourceName ?: "").endsWith(viewId)) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return null
    }

    fun clickNode(n: AccessibilityNodeInfo): Boolean {
        var node: AccessibilityNodeInfo? = n
        var d = 0
        while (node != null && d < 4) {
            if (node.isClickable) return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            node = node.parent; d++
        }
        return false
    }

    fun focusedEditable(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val f = findFocused(root) ?: root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        return f ?: bfsEditable(root)
    }

    private fun findFocused(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (n.isFocused && n.isEditable) return n
        for (i in 0 until n.childCount) {
            val c = n.getChild(i) ?: continue
            findFocused(c)?.let { return it }
        }
        return null
    }

    private fun bfsEditable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val q = ArrayDeque<AccessibilityNodeInfo>(); q.add(root)
        while (q.isNotEmpty()) {
            val n = q.removeFirst()
            if (n.isEditable && (n.isFocused || n.isFocusable)) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let { q.add(it) }
        }
        return null
    }

    fun setText(node: AccessibilityNodeInfo, text: String): Boolean {
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    // ---- gestos (suspend: espera callback) ----

    suspend fun tap(x: Float, y: Float): Boolean = gestureMutex.withLock {
        suspendCancellableCoroutine { cont ->
            val p = Path().apply { moveTo(x, y) }
            val g = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(p, 0, 80)).build()
            dispatchGesture(g, object : GestureResultCallback() {
                override fun onCompleted(g: GestureDescription?) { cont.resume(true) }
                override fun onCancelled(g: GestureDescription?) { cont.resume(false) }
            }, null)
        }
    }

    suspend fun swipe(dir: String): Boolean {
        val b = Rect()
        rootInActiveWindow?.getBoundsInScreen(b)
        val w = if (b.width() > 0) b.width() else 1080
        val h = if (b.height() > 0) b.height() else 1920
        val cx = w / 2f
        val cy = h / 2f
        val d = (minOf(w, h) * 0.45f)
        val (x0, y0, x1, y1) = when (dir.uppercase()) {
            "CIMA" -> floatArrayOf(cx, cy + d, cx, cy - d)
            "BAIXO" -> floatArrayOf(cx, cy - d, cx, cy + d)
            "ESQUERDA" -> floatArrayOf(cx + d, cy, cx - d, cy)
            else -> floatArrayOf(cx - d, cy, cx + d, cy)
        }.let { Quad(it[0], it[1], it[2], it[3]) }
        return gestureMutex.withLock {
            suspendCancellableCoroutine { cont ->
                val p = Path().apply { moveTo(x0, y0); lineTo(x1, y1) }
                val g = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(p, 0, 400)).build()
                dispatchGesture(g, object : GestureResultCallback() {
                    override fun onCompleted(g: GestureDescription?) { cont.resume(true) }
                    override fun onCancelled(g: GestureDescription?) { cont.resume(false) }
                }, null)
            }
        }
    }

    fun scroll(node: AccessibilityNodeInfo?, down: Boolean): Boolean {
        val target = node ?: rootInActiveWindow ?: return false
        var n: AccessibilityNodeInfo? = target
        var d = 0
        while (n != null && d < 5) {
            if (n.isScrollable) {
                return n.performAction(
                    if (down) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                    else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                )
            }
            n = n.parent; d++
        }
        return false
    }

    private data class Quad(val a: Float, val b: Float, val c: Float, val d: Float)
}
