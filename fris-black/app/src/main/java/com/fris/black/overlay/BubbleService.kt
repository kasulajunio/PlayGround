package com.fris.black.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import com.fris.black.R
import com.fris.black.ui.MainActivity

// Bolinha flutuante: pausar / continuar / abrir painel / desativar.
// Só aparece se o usuário pedir (SYSTEM_ALERT_WINDOW) — nunca sozinha.
class BubbleService : Service() {

    companion object {
        const val CH = "fris_bubble"
        @Volatile var running = false
        @Volatile var paused = false

        fun start(ctx: Context) {
            if (!Settings.canDrawOverlays(ctx)) return
            ctx.startForegroundService(Intent(ctx, BubbleService::class.java))
        }

        fun stop(ctx: Context) { ctx.stopService(Intent(ctx, BubbleService::class.java)) }
    }

    private var wm: WindowManager? = null
    private var bubble: LinearLayout? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        startFg()
        showBubble()
    }

    override fun onDestroy() {
        running = false
        bubble?.let { wm?.removeView(it) }
        super.onDestroy()
    }

    private fun startFg() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CH, "Fris Black", NotificationManager.IMPORTANCE_MIN)
            )
        }
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CH)
                .setContentTitle("Fris Black ativo")
                .setContentText(if (paused) "Pausado" else "Automação rodando")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pi).build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Fris Black ativo")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pi).build()
        }
        startForeground(1, n)
    }

    private fun showBubble() {
        if (!Settings.canDrawOverlays(this)) return
        wm = getSystemService(WindowManager::class.java)
        val type = if (Build.VERSION.SDK_INT >= 26)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.END; x = 16; y = 200 }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val btnPause = Button(this).apply { text = if (paused) "▶" else "⏸" }
        val btnOpen = Button(this).apply { text = "⚙" }
        btnPause.setOnClickListener {
            paused = !paused
            btnPause.text = if (paused) "▶" else "⏸"
            startFg()
        }
        btnOpen.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
        // arrastar a bolinha
        var sx = 0f; var sy = 0f; var px = 0; var py = 0
        layout.setOnTouchListener { _, e ->
            when (e.action) {
                android.view.MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; px = params.x; py = params.y; false }
                android.view.MotionEvent.ACTION_MOVE -> {
                    params.x = px - (e.rawX - sx).toInt()
                    params.y = py + (e.rawY - sy).toInt()
                    wm?.updateViewLayout(layout, params); true
                }
                else -> false
            }
        }
        layout.addView(btnPause)
        layout.addView(btnOpen)
        bubble = layout
        wm?.addView(layout, params)
    }
}
