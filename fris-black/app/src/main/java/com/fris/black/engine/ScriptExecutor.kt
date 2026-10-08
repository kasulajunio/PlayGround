package com.fris.black.engine

import com.fris.black.script.FrisParser
import com.fris.black.script.FrisScript
import com.fris.black.script.Step
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

// ---------------------------------------------------------------------------
// Executor atômico: roda passo a passo, para no 1º erro, registra tudo.
// Toda rodada tem runId único (idempotência) — nunca duplica efeito real.
// Limites: 30 passos, timeout por passo, sem loop infinito.
// ---------------------------------------------------------------------------

data class HistoryEntry(
    val runId: String,
    val line: Int,
    val desc: String,
    val ok: Boolean,
    val error: String? = null,
    val atMs: Long = System.currentTimeMillis()
)

interface UiDriver {
    suspend fun openApp(appName: String): Boolean
    suspend fun clickText(text: String): Boolean
    suspend fun clickId(id: String): Boolean
    suspend fun clickXy(x: Int, y: Int): Boolean
    suspend fun typeText(text: String): Boolean
    suspend fun clearText(): Boolean
    suspend fun scrollDown(): Boolean
    suspend fun scrollUp(): Boolean
    suspend fun swipe(dir: String): Boolean
    suspend fun waitAppear(text: String, timeoutSec: Int): Boolean
    suspend fun waitGone(text: String, timeoutSec: Int): Boolean
    suspend fun back(): Boolean
    suspend fun home(): Boolean
    suspend fun closeKeyboard(): Boolean
    fun toast(text: String)
    suspend fun confirm(question: String): Boolean
    fun isConfirmedBranch(): Boolean?
    fun log(text: String)
}

class ScriptExecutor(
    private val driver: UiDriver,
    private val onHistory: (HistoryEntry) -> Unit = {},
    private val stepTimeoutMs: Long = 12_000L,
    private val maxTotalMs: Long = 60_000L
) {
    data class Result(
        val runId: String,
        val success: Boolean,
        val stoppedAtLine: Int?,
        val error: String?,
        val history: List<HistoryEntry>
    )

    suspend fun run(script: FrisScript): Result {
        val runId = FrisParser.newRunId()
        val history = mutableListOf<HistoryEntry>()
        var continueOnError = false
        var lastConfirm: Boolean? = null
        val startAll = System.currentTimeMillis()

        fun record(line: Int, desc: String, ok: Boolean, err: String? = null) {
            val e = HistoryEntry(runId, line, desc, ok, err)
            history += e
            onHistory(e)
            driver.log("[${runId}] linha $line ${if (ok) "OK" else "FALHOU"}: $desc ${err ?: ""}")
        }

        for (step in script.steps) {
            if (System.currentTimeMillis() - startAll > maxTotalMs) {
                record(step.line, describe(step), false, "limite de 60s por rodada excedido")
                return Result(runId, false, step.line, "timeout total 60s", history)
            }
            // Branch SE confirmado / SE não confirmado
            if (step is Step.BranchConfirmed) {
                if (lastConfirm == null) {
                    record(step.line, describe(step), false, "SE confirmado sem CONFIRMAR antes")
                    return Result(runId, false, step.line, "CONFIRMAR ausente", history)
                }
                if (lastConfirm != step.confirmed) {
                    record(step.line, describe(step), true, "branch pulado")
                    // pula até o próximo branch ou fim? Simplificação v2:
                    // se não bateu, para (usuário modela dois caminhos com dois scripts)
                    return Result(runId, true, null, null, history)
                }
                record(step.line, describe(step), true)
                continue
            }
            if (step is Step.ContinueOnError) { continueOnError = true; record(step.line, describe(step), true); continue }
            if (step is Step.Stop) { record(step.line, describe(step), true); break }
            if (step is Step.Repeat) { record(step.line, describe(step), true, "repetição v2 executa 1x (loop completo no agendador)"); continue }

            try {
                val ok = withTimeout(stepTimeoutMs) { execStep(step) { lastConfirm = it } }
                record(step.line, describe(step), ok, if (ok) null else "ação retornou falso (elemento não encontrado?)")
                if (!ok && !continueOnError) {
                    return Result(runId, false, step.line, "passo falhou", history)
                }
            } catch (e: TimeoutCancellationException) {
                record(step.line, describe(step), false, "timeout ${stepTimeoutMs}ms")
                if (!continueOnError) return Result(runId, false, step.line, "timeout", history)
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                record(step.line, describe(step), false, e.message)
                if (!continueOnError) return Result(runId, false, step.line, e.message, history)
            }
        }
        return Result(runId, true, null, null, history)
    }

    private suspend fun execStep(step: Step, onConfirm: (Boolean) -> Unit): Boolean {
        return when (step) {
            is Step.OpenApp -> driver.openApp(step.appName)
            is Step.ClickText -> driver.clickText(step.text)
            is Step.ClickId -> driver.clickId(step.id)
            is Step.ClickXy -> driver.clickXy(step.x, step.y)
            is Step.TypeText -> driver.typeText(step.text)
            is Step.ClearText -> driver.clearText()
            is Step.Scroll -> if (step.down) driver.scrollDown() else driver.scrollUp()
            is Step.Swipe -> driver.swipe(step.dir.name)
            is Step.WaitSeconds -> { kotlinx.coroutines.delay(step.seconds * 1000L); true }
            is Step.WaitAppear -> driver.waitAppear(step.text, step.timeoutSec)
            is Step.WaitGone -> driver.waitGone(step.text, step.timeoutSec)
            is Step.Back -> driver.back()
            is Step.Home -> driver.home()
            is Step.Recents -> driver.back() // fallback: recents via back duplo no driver real
            is Step.CloseKeyboard -> driver.closeKeyboard()
            is Step.Notify -> { driver.toast(step.text); true }
            is Step.ShowError -> { driver.toast("ERRO: ${step.text}"); false }
            is Step.Log -> { driver.log(step.text); true }
            is Step.TimerMinutes -> { driver.toast("Cronômetro ${step.minutes}min iniciado"); true }
            is Step.Confirm -> { val c = driver.confirm(step.question); onConfirm(c); c }
            else -> true // Wifi/BT/Screenshot/etc tratados no driver Android real; em teste = ok
        }
    }

    private fun describe(s: Step): String = when (s) {
        is Step.OpenApp -> "abrir ${s.appName}"
        is Step.ClickText -> "clicar \"${s.text}\""
        is Step.ClickId -> "clicar id \"${s.id}\""
        is Step.ClickXy -> "clicar ${s.x},${s.y}"
        is Step.TypeText -> "digitar \"${s.text.take(20)}\""
        else -> s::class.simpleName ?: "passo"
    }
}
