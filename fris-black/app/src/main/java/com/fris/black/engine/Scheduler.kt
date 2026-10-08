package com.fris.black.engine

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.fris.black.data.FrisStore
import com.fris.black.script.FrisParser
import com.fris.black.script.Trigger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar

// Agendador: QUANDO chegar às HH:MM + boot.
// Rate-limit: máx 20 rodadas/min (anti loop). Execução máx 60s (no Executor).
object Scheduler {
    private const val ACTION_RUN = "com.fris.black.RUN_SCRIPT"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lastRuns = ArrayDeque<Long>()

    fun scheduleAll(ctx: Context) {
        cancelAll(ctx)
        val all = FrisStore.loadAll(ctx)
        for ((name, o) in all) {
            if (!o.optBoolean("enabled", false)) continue
            val src = o.optString("source", "")
            val res = FrisParser.parse(src)
            val script = res.script ?: continue
            val trig = script.trigger
            if (trig is Trigger.AtTime) scheduleDaily(ctx, name, trig.hh, trig.mm)
        }
    }

    private fun scheduleDaily(ctx: Context, name: String, hh: Int, mm: Int) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hh); set(Calendar.MINUTE, mm); set(Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        val pi = pending(ctx, name)
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        } catch (e: SecurityException) {
            am.set(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        }
    }

    private fun pending(ctx: Context, name: String): PendingIntent {
        val i = Intent(ctx, TriggerReceiver::class.java).apply {
            action = ACTION_RUN
            putExtra("script", name)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        return PendingIntent.getBroadcast(ctx, name.hashCode(), i, flags)
    }

    private fun cancelAll(ctx: Context) {
        val all = FrisStore.loadAll(ctx)
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        for ((name, _) in all) { try { am.cancel(pending(ctx, name)) } catch (e: Exception) {} }
    }

    fun canRun(): Boolean {
        val now = System.currentTimeMillis()
        while (lastRuns.isNotEmpty() && now - lastRuns.first() > 60_000) lastRuns.removeFirst()
        if (lastRuns.size >= 20) return false
        lastRuns.addLast(now)
        return true
    }

    fun runNow(ctx: Context, name: String) {
        if (!canRun()) { Log.w("FrisBlack", "rate-limit 20/min"); return }
        scope.launch {
            try {
                val o = FrisStore.getScript(ctx, name) ?: return@launch
                val res = FrisParser.parse(o.optString("source", ""))
                val script = res.script ?: return@launch
                val driver = AndroidUiDriver(ctx.applicationContext)
                val exec = ScriptExecutor(driver, onHistory = { e ->
                    FrisStore.appendHistory(
                        ctx, "[$name] linha ${e.line} ${if (e.ok) "OK" else "ERRO: ${e.error}"} — ${e.desc}"
                    )
                })
                val r = exec.run(script)
                FrisStore.appendHistory(
                    ctx, "[$name] rodada ${r.runId} ${if (r.success) "CONCLUÍDA" else "PAROU linha ${r.stoppedAtLine}: ${r.error}"}"
                )
                // reagenda diária
                val trig = script.trigger
                if (trig is Trigger.AtTime) scheduleDaily(ctx, name, trig.hh, trig.mm)
            } catch (e: Exception) {
                Log.e("FrisBlack", "run falhou", e)
            }
        }
    }
}

class TriggerReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val name = intent.getStringExtra("script") ?: return
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Scheduler.scheduleAll(ctx)
            return
        }
        Scheduler.runNow(ctx.applicationContext, name)
    }
}
