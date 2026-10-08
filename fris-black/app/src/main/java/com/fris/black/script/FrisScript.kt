package com.fris.black.script

import java.util.UUID

// ---------------------------------------------------------------------------
// FrisScript v2 — modelo
// Inspirado em: AutoJs6 (seletores), AutoTask (dual engine),
// OpenRing (executor atômico), GhostUser (gestos), Tasker/MacroDroid (triggers)
// ---------------------------------------------------------------------------

data class FrisScript(
    val targetApp: String?,
    val trigger: Trigger,
    val conditions: List<Condition>,
    val steps: List<Step>,
    val rawLines: List<String> = emptyList()
)

sealed interface Trigger {
    data object Manual : Trigger
    data class AppOpen(val appName: String) : Trigger
    data class AtTime(val hh: Int, val mm: Int) : Trigger
    data object OnBoot : Trigger
    data class OnNotification(val appName: String, val contains: String?) : Trigger
    data class OnWifi(val ssid: String?, val connected: Boolean) : Trigger
    data class BatteryBelow(val pct: Int) : Trigger
}

sealed interface Condition {
    data class AfterTime(val hh: Int, val mm: Int) : Condition
    data class BeforeTime(val hh: Int, val mm: Int) : Condition
    data class Weekdays(val days: List<String>) : Condition
    data class BatteryBelow(val pct: Int) : Condition
    data class WifiConnected(val ssid: String?) : Condition
}

sealed interface Step {
    val line: Int
    data class OpenApp(override val line: Int, val appName: String) : Step
    data class ClickText(override val line: Int, val text: String) : Step
    data class ClickId(override val line: Int, val id: String) : Step
    data class ClickXy(override val line: Int, val x: Int, val y: Int) : Step
    data class TypeText(override val line: Int, val text: String) : Step
    data class ClearText(override val line: Int) : Step
    data class Scroll(override val line: Int, val down: Boolean) : Step
    data class Swipe(override val line: Int, val dir: SwipeDir) : Step
    data class WaitSeconds(override val line: Int, val seconds: Int) : Step
    data class WaitAppear(override val line: Int, val text: String, val timeoutSec: Int) : Step
    data class WaitGone(override val line: Int, val text: String, val timeoutSec: Int) : Step
    data class Back(override val line: Int) : Step
    data class Home(override val line: Int) : Step
    data class Recents(override val line: Int) : Step
    data class CloseKeyboard(override val line: Int) : Step
    data class Notify(override val line: Int, val text: String) : Step
    data class ShowError(override val line: Int, val text: String) : Step
    data class Log(override val line: Int, val text: String) : Step
    data class TimerMinutes(override val line: Int, val minutes: Int) : Step
    data class Confirm(override val line: Int, val question: String) : Step
    data class BranchConfirmed(override val line: Int, val confirmed: Boolean) : Step
    data class Stop(override val line: Int) : Step
    data class ContinueOnError(override val line: Int) : Step
    data class Repeat(override val line: Int, val times: Int) : Step
    data class Screenshot(override val line: Int) : Step
    data class LockScreen(override val line: Int) : Step
    data class Wifi(override val line: Int, val on: Boolean) : Step
    data class Bluetooth(override val line: Int, val on: Boolean) : Step
    data class Flashlight(override val line: Int, val on: Boolean) : Step
    data class Volume(override val line: Int, val level: Int) : Step
    data class Brightness(override val line: Int, val level: Int) : Step
    data class OpenUrl(override val line: Int, val url: String) : Step
}

enum class SwipeDir { CIMA, BAIXO, ESQUERDA, DIREITA }

data class ParseError(val line: Int, val message: String) {
    override fun toString(): String = "linha $line: $message"
}

data class ParseResult(val script: FrisScript?, val errors: List<ParseError>)

object FrisParser {
    const val MAX_STEPS = 30
    const val MAX_WAIT_SEC = 60
    const val MAX_REPEAT = 5
    const val MAX_WAIT_FOR_SEC = 30
    const val DEFAULT_WAIT_FOR_SEC = 10

    private val DANGEROUS = listOf(
        "enviar", "apagar", "excluir", "deletar", "comprar", "pagar",
        "pix", "transferir", "senha", "desinstalar", "formatar"
    )

    private val WEEKDAYS = mapOf(
        "segunda" to "segunda", "terça" to "terca", "terca" to "terca",
        "quarta" to "quarta", "quinta" to "quinta", "sexta" to "sexta",
        "sábado" to "sabado", "sabado" to "sabado", "domingo" to "domingo"
    )

    fun parse(source: String): ParseResult {
        val errors = mutableListOf<ParseError>()
        val rawLines = source.lines()
        var targetApp: String? = null
        var trigger: Trigger = Trigger.Manual
        var triggerSet = false
        val conditions = mutableListOf<Condition>()
        val steps = mutableListOf<Step>()
        var confirmSeen = false
        var stepCount = 0

        fun err(line: Int, msg: String) { errors += ParseError(line, msg) }

        // remove comentários // mas preserva dentro de aspas
        fun stripComment(s: String): String {
            var inQ = false
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '"') inQ = !inQ
                if (!inQ && c == '/' && i + 1 < s.length && s[i + 1] == '/') {
                    return s.substring(0, i)
                }
                i++
            }
            return s
        }

        fun quoted(s: String): String? {
            val m = Regex("\"([^\"]*)\"").find(s)
            return m?.groupValues?.get(1)
        }

        fun timeHHMM(s: String): Pair<Int, Int>? {
            val m = Regex("(\\d{1,2}):(\\d{2})").find(s) ?: return null
            val h = m.groupValues[1].toIntOrNull() ?: return null
            val mm = m.groupValues[2].toIntOrNull() ?: return null
            if (h !in 0..23 || mm !in 0..59) return null
            return h to mm
        }

        rawLines.forEachIndexed { idx, raw ->
            val lineNo = idx + 1
            var line = stripComment(raw).trim()
            if (line.isEmpty()) return@forEachIndexed
            val upper = line.uppercase()

            when {
                upper.startsWith("ALVO ") -> {
                    val name = line.substringAfter(" ", "").trim()
                        .removeSurrounding("\"").trim()
                    if (name.isEmpty()) err(lineNo, "ALVO precisa do nome do app. Ex: ALVO WhatsApp")
                    else targetApp = name
                }
                upper.startsWith("QUANDO ") -> {
                    val rest = line.substring(7).trim()
                    val r = rest.lowercase()
                    try {
                        trigger = when {
                            r.startsWith("abrir ") -> {
                                val app = rest.substringAfter("abrir ").trim().removeSurrounding("\"")
                                if (app.isEmpty()) { err(lineNo, "QUANDO abrir precisa do nome do app"); Trigger.Manual }
                                else Trigger.AppOpen(app)
                            }
                            r.startsWith("chegar às ") || r.startsWith("chegar as ") -> {
                                val t = timeHHMM(rest)
                                    ?: run { err(lineNo, "horário inválido. Use HH:MM. Ex: QUANDO chegar às 08:00"); null }
                                if (t == null) Trigger.Manual else Trigger.AtTime(t.first, t.second)
                            }
                            r.startsWith("horário ") || r.startsWith("horario ") -> {
                                val t = timeHHMM(rest)
                                    ?: run { err(lineNo, "horário inválido. Use HH:MM"); null }
                                if (t == null) Trigger.Manual else Trigger.AtTime(t.first, t.second)
                            }
                            r == "ligar o celular" || r == "iniciar o celular" || r == "reiniciar" ->
                                Trigger.OnBoot
                            r.startsWith("receber notificação de ") || r.startsWith("receber notificacao de ") -> {
                                val app = quoted(rest) ?: rest.substringAfterLast("de ").trim()
                                Trigger.OnNotification(app, quoted(rest)?.takeIf { rest.lowercase().contains("contendo") })
                            }
                            r.startsWith("conectar wifi") || r.startsWith("conectar wi-fi") -> {
                                val ssid = quoted(rest)
                                Trigger.OnWifi(ssid, true)
                            }
                            r.startsWith("desconectar wifi") -> Trigger.OnWifi(null, false)
                            r.startsWith("bateria abaixo de ") -> {
                                val pct = Regex("(\\d{1,3})").find(rest)?.groupValues?.get(1)?.toIntOrNull()
                                if (pct == null || pct !in 1..100) { err(lineNo, "porcentagem inválida (1-100)"); Trigger.Manual }
                                else Trigger.BatteryBelow(pct)
                            }
                            r.startsWith("tocar em executar") || r.startsWith("eu mandar") || r == "manual" ->
                                Trigger.Manual
                            else -> { err(lineNo, "gatilho desconhecido: \"$rest\". Use: abrir <app> | chegar às HH:MM | ligar o celular | receber notificação de <app> | conectar wifi | bateria abaixo de N%"); Trigger.Manual }
                        }
                        triggerSet = true
                    } catch (e: Exception) {
                        err(lineNo, "gatilho inválido: ${e.message}")
                    }
                }
                upper.startsWith("SE ") -> {
                    val rest = line.substring(3).trim()
                    val r = rest.lowercase()
                    val cond: Condition? = when {
                        r.startsWith("for depois das ") || r.startsWith("depois das ") -> {
                            val t = timeHHMM(rest)
                            if (t == null) { err(lineNo, "horário inválido. Ex: SE for depois das 23:00"); null }
                            else Condition.AfterTime(t.first, t.second)
                        }
                        r.startsWith("for antes das ") || r.startsWith("antes das ") -> {
                            val t = timeHHMM(rest)
                            if (t == null) { err(lineNo, "horário inválido. Ex: SE for antes das 06:00"); null }
                            else Condition.BeforeTime(t.first, t.second)
                        }
                        r.startsWith("for ") && WEEKDAYS.keys.any { r.contains(it) } -> {
                            val found = WEEKDAYS.filter { r.contains(it.key) }.values.distinct()
                            if (found.isEmpty()) { err(lineNo, "dia da semana inválido"); null }
                            else Condition.Weekdays(found)
                        }
                        r.startsWith("bateria abaixo de ") || r.startsWith("bateria abaixo ") -> {
                            val pct = Regex("(\\d{1,3})").find(rest)?.groupValues?.get(1)?.toIntOrNull()
                            if (pct == null || pct !in 1..100) { err(lineNo, "porcentagem inválida (1-100)"); null }
                            else Condition.BatteryBelow(pct)
                        }
                        r.startsWith("wifi conectado") || r.startsWith("wi-fi conectado") -> {
                            Condition.WifiConnected(quoted(rest))
                        }
                        r == "confirmado" || r == "não confirmado" || r == "nao confirmado" -> {
                            steps += Step.BranchConfirmed(lineNo, r == "confirmado")
                            null
                        }
                        else -> { err(lineNo, "condição desconhecida: \"$rest\""); null }
                    }
                    if (cond != null) conditions += cond
                }
                upper.startsWith("ENTÃO ") || upper.startsWith("ENTAO ") || upper.startsWith("E ") -> {
                    val rest = if (upper.startsWith("E ")) line.substring(2).trim()
                               else line.substringAfter(" ").trim()
                    val r = rest.lowercase()
                    stepCount++
                    if (stepCount > MAX_STEPS) {
                        err(lineNo, "limite de $MAX_STEPS passos excedido (proteção anti-travamento)")
                        return@forEachIndexed
                    }
                    val q = quoted(rest) ?: ""
                    val step: Step? = when {
                        r.startsWith("abrir ") -> {
                            val app = rest.substringAfter("abrir ").trim().removeSurrounding("\"")
                            if (app.isEmpty()) { err(lineNo, "abrir precisa do nome do app"); null }
                            else Step.OpenApp(lineNo, app)
                        }
                        r.startsWith("clicar em coordenada ") || r.startsWith("clicar na coordenada ") -> {
                            val nums = Regex("(\\d+)\\s*,\\s*(\\d+)").find(rest)
                            if (nums == null) { err(lineNo, "coordenada inválida. Ex: clicar em coordenada 500, 800"); null }
                            else Step.ClickXy(lineNo, nums.groupValues[1].toInt(), nums.groupValues[2].toInt())
                        }
                        r.startsWith("clicar por id ") -> {
                            if (q.isEmpty()) { err(lineNo, "faltou o id entre aspas. Ex: clicar por id \"btn_login\""); null }
                            else Step.ClickId(lineNo, q)
                        }
                        r.startsWith("clicar ") -> {
                            if (q.isEmpty()) { err(lineNo, "faltou o texto entre aspas. Ex: clicar em \"Pesquisar\""); null }
                            else Step.ClickText(lineNo, q)
                        }
                        r.startsWith("digitar ") -> {
                            if (q.isEmpty() && !r.contains("\"\"")) { err(lineNo, "faltou o texto entre aspas. Ex: digitar \"olá\""); null }
                            else Step.TypeText(lineNo, q)
                        }
                        r.startsWith("limpar texto") -> Step.ClearText(lineNo)
                        r.startsWith("rolar para baixo") -> Step.Scroll(lineNo, true)
                        r.startsWith("rolar para cima") -> Step.Scroll(lineNo, false)
                        r.startsWith("deslizar ") -> {
                            val dir = when {
                                r.contains("cima") -> SwipeDir.CIMA
                                r.contains("baixo") -> SwipeDir.BAIXO
                                r.contains("esquerda") -> SwipeDir.ESQUERDA
                                r.contains("direita") -> SwipeDir.DIREITA
                                else -> null
                            }
                            if (dir == null) { err(lineNo, "direção inválida. Use cima|baixo|esquerda|direita"); null }
                            else Step.Swipe(lineNo, dir)
                        }
                        r.startsWith("esperar até aparecer ") || r.startsWith("esperar ate aparecer ") -> {
                            if (q.isEmpty()) { err(lineNo, "faltou o texto entre aspas"); null }
                            else {
                                val t = Regex("em\\s+(\\d+)\\s*s").find(r)?.groupValues?.get(1)?.toIntOrNull()
                                    ?: DEFAULT_WAIT_FOR_SEC
                                if (t > MAX_WAIT_FOR_SEC) { err(lineNo, "timeout máximo ${MAX_WAIT_FOR_SEC}s"); null }
                                else Step.WaitAppear(lineNo, q, t)
                            }
                        }
                        r.startsWith("esperar até ") || r.startsWith("esperar ate ") -> {
                            // esperar até "X" desaparecer
                            if (q.isEmpty()) { err(lineNo, "faltou o texto entre aspas"); null }
                            else if (r.contains("desaparecer")) Step.WaitGone(lineNo, q, DEFAULT_WAIT_FOR_SEC)
                            else Step.WaitAppear(lineNo, q, DEFAULT_WAIT_FOR_SEC)
                        }
                        r.startsWith("esperar ") -> {
                            val n = Regex("(\\d+)").find(rest)?.groupValues?.get(1)?.toIntOrNull()
                            if (n == null) { err(lineNo, "use: esperar <n> segundos. Ex: esperar 2 segundos"); null }
                            else if (n > MAX_WAIT_SEC) { err(lineNo, "espera máxima ${MAX_WAIT_SEC}s por passo"); null }
                            else Step.WaitSeconds(lineNo, n)
                        }
                        r == "voltar" -> Step.Back(lineNo)
                        r == "início" || r == "inicio" || r == "ir para início" -> Step.Home(lineNo)
                        r == "recentes" -> Step.Recents(lineNo)
                        r == "fechar teclado" -> Step.CloseKeyboard(lineNo)
                        r.startsWith("mostrar aviso ") -> {
                            if (q.isEmpty()) { err(lineNo, "faltou o texto. Ex: mostrar aviso \"Pronto!\""); null }
                            else Step.Notify(lineNo, q)
                        }
                        r.startsWith("mostrar erro ") -> {
                            if (q.isEmpty()) { err(lineNo, "faltou o texto. Ex: mostrar erro \"falhou\""); null }
                            else Step.ShowError(lineNo, q)
                        }
                        r.startsWith("registrar ") -> {
                            if (q.isEmpty()) { err(lineNo, "faltou o texto. Ex: registrar \"passo ok\""); null }
                            else Step.Log(lineNo, q)
                        }
                        r.startsWith("iniciar cronômetro de ") || r.startsWith("iniciar cronometro de ") -> {
                            val n = Regex("(\\d+)").find(rest)?.groupValues?.get(1)?.toIntOrNull()
                            if (n == null) { err(lineNo, "use: iniciar cronômetro de <n> minutos"); null }
                            else Step.TimerMinutes(lineNo, n)
                        }
                        r.startsWith("confirmar ") || r.startsWith("confirmar diálogo ") || r.startsWith("confirmar dialogo ") -> {
                            if (q.isEmpty()) { err(lineNo, "faltou a pergunta. Ex: confirmar \"Enviar mensagem?\""); null }
                            else { confirmSeen = true; Step.Confirm(lineNo, q) }
                        }
                        r == "parar script" || r == "parar" -> Step.Stop(lineNo)
                        r.startsWith("continuar mesmo se falhar") -> Step.ContinueOnError(lineNo)
                        r.startsWith("repetir bloco até ") || r.startsWith("repetir bloco ate ") || r.startsWith("repetir até ") || r.startsWith("repetir ate ") -> {
                            val n = Regex("(\\d+)").find(rest)?.groupValues?.get(1)?.toIntOrNull()
                            if (n == null) { err(lineNo, "use: repetir bloco até <n> vezes (máx $MAX_REPEAT)"); null }
                            else if (n > MAX_REPEAT) { err(lineNo, "repetição máxima ${MAX_REPEAT}x (anti loop-infinito)"); null }
                            else Step.Repeat(lineNo, n)
                        }
                        r == "tirar screenshot" || r == "capturar tela" -> Step.Screenshot(lineNo)
                        r == "bloquear tela" -> Step.LockScreen(lineNo)
                        r.startsWith("ativar wifi") || r == "ligar wifi" -> Step.Wifi(lineNo, true)
                        r.startsWith("desativar wifi") || r == "desligar wifi" -> Step.Wifi(lineNo, false)
                        r.startsWith("ativar bluetooth") -> Step.Bluetooth(lineNo, true)
                        r.startsWith("desativar bluetooth") -> Step.Bluetooth(lineNo, false)
                        r.startsWith("ativar lanterna") || r.startsWith("ligar lanterna") -> Step.Flashlight(lineNo, true)
                        r.startsWith("desativar lanterna") || r.startsWith("desligar lanterna") -> Step.Flashlight(lineNo, false)
                        r.startsWith("volume ") -> {
                            val n = Regex("(\\d+)").find(rest)?.groupValues?.get(1)?.toIntOrNull()
                            if (n == null || n !in 0..100) { err(lineNo, "volume 0-100"); null }
                            else Step.Volume(lineNo, n)
                        }
                        r.startsWith("brilho ") -> {
                            val n = Regex("(\\d+)").find(rest)?.groupValues?.get(1)?.toIntOrNull()
                            if (n == null || n !in 0..100) { err(lineNo, "brilho 0-100"); null }
                            else Step.Brightness(lineNo, n)
                        }
                        r.startsWith("abrir url ") || r.startsWith("abrir link ") -> {
                            if (q.isEmpty()) { err(lineNo, "faltou a URL. Ex: abrir url \"https://...\""); null }
                            else Step.OpenUrl(lineNo, q)
                        }
                        else -> { err(lineNo, "comando desconhecido: \"$rest\""); null }
                    }
                    if (step != null) {
                        // checagem ação perigosa sem confirmação prévia
                        val low = rest.lowercase()
                        if (DANGEROUS.any { low.contains(it) } && !confirmSeen &&
                            step is Step.ClickText || (DANGEROUS.any { low.contains(it) } && !confirmSeen && step is Step.TypeText)
                        ) {
                            // avisa mas não bloqueia: exige confirmar antes
                            err(lineNo, "ação sensível (\"$rest\") exige um passo CONFIRMAR antes — proteção obrigatória")
                        }
                        steps += step
                    }
                }
                else -> err(lineNo, "inicie a linha com ALVO, QUANDO, SE ou ENTÃO. Encontrado: \"${line.take(30)}\"")
            }
        }

        if (!triggerSet) errors += ParseError(0, "falta o gatilho QUANDO. Ex: QUANDO chegar às 08:00")
        if (steps.isEmpty() && errors.isEmpty()) errors += ParseError(0, "o script não tem nenhum passo ENTÃO")

        val script = if (errors.isEmpty()) FrisScript(targetApp, trigger, conditions, steps, rawLines) else null
        return ParseResult(script, errors)
    }

    fun newRunId(): String = UUID.randomUUID().toString().take(8)
}
