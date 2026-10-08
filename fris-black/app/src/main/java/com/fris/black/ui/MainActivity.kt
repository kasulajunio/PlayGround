package com.fris.black.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.fris.black.data.FrisStore
import com.fris.black.engine.Scheduler
import com.fris.black.overlay.BubbleService
import com.fris.black.service.FrisAccessibilityService

class MainActivity : AppCompatActivity() {

    private lateinit var listBox: LinearLayout
    private lateinit var statusView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 64)
        }
        scroll.addView(root)
        setContentView(scroll)

        root.addView(TextView(this).apply {
            text = "⬛ FRIS BLACK v2"
            textSize = 26f
        })
        root.addView(TextView(this).apply {
            text = "Escolher app → script → validar → ativar → roda sozinho, passo a passo, sem travar."
            textSize = 14f
        })
        statusView = TextView(this).apply { textSize = 14f }
        root.addView(statusView)

        fun btn(t: String, fn: () -> Unit) {
            root.addView(Button(this).apply { text = t; setOnClickListener { fn() } })
        }

        btn("♿ Ativar Acessibilidade (nível 2 — clicar/digitar)") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, "Ative: Fris Black", Toast.LENGTH_LONG).show()
        }
        btn("🫧 Permitir bolha flutuante + ligar/desligar") {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } else {
                if (BubbleService.running) BubbleService.stop(this) else BubbleService.start(this)
                refresh()
            }
        }
        btn("➕ Novo script") {
            startActivity(Intent(this, EditorActivity::class.java))
        }
        btn("📜 Histórico") {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        btn("📥 Carregar exemplos") {
            loadSamples()
            refresh()
        }

        root.addView(TextView(this).apply { text = "\n— Meus scripts —"; textSize = 18f })
        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(listBox)
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun refresh() {
        val a11y = FrisAccessibilityService.isOn()
        val ov = Settings.canDrawOverlays(this)
        statusView.text = "\nStatus:\n" +
            (if (a11y) "✅ Acessibilidade LIGADA (nível 2)" else "❌ Acessibilidade DESLIGADA — ative para clicar/digitar") + "\n" +
            (if (ov) "✅ Sobrepor apps OK" else "❌ Bolha sem permissão") + "\n" +
            (if (BubbleService.running) "🫧 Bolha ativa" + if (BubbleService.paused) " (pausada)" else "" else "🫧 Bolha off")
        listBox.removeAllViews()
        val all = FrisStore.loadAll(this)
        if (all.isEmpty()) {
            listBox.addView(TextView(this).apply { text = "(nenhum script — toque em Novo ou Carregar exemplos)" })
        }
        for ((name, o) in all) {
            val en = o.optBoolean("enabled", false)
            val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 16, 0, 16) }
            row.addView(TextView(this).apply { text = "${if (en) "🟢" else "⚪"} $name"; textSize = 17f })
            val btns = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            fun small(t: String, fn: () -> Unit) {
                btns.addView(Button(this).apply { text = t; setOnClickListener { fn() } })
            }
            small(if (en) "Desativar" else "Ativar") {
                FrisStore.saveScript(this, name, o.optString("source", ""), !en)
                Scheduler.scheduleAll(this)
                refresh()
            }
            small("Editar") {
                startActivity(Intent(this, EditorActivity::class.java).putExtra("name", name))
            }
            small("▶") {
                Scheduler.runNow(this, name)
                Toast.makeText(this, "Executando $name… veja Histórico", Toast.LENGTH_SHORT).show()
            }
            small("🗑") {
                FrisStore.deleteScript(this, name)
                Scheduler.scheduleAll(this)
                refresh()
            }
            row.addView(btns)
            listBox.addView(row)
        }
    }

    private fun loadSamples() {
        FrisStore.saveScript(this, "Modo Noite YouTube", SAMPLE_NOITE, true)
        FrisStore.saveScript(this, "Bom Dia WhatsApp", SAMPLE_WPP, false)
        Scheduler.scheduleAll(this)
        Toast.makeText(this, "Exemplos carregados", Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val SAMPLE_NOITE = """ALVO YouTube

QUANDO chegar às 23:00
SE for depois das 22:00
ENTÃO abrir YouTube
ENTÃO esperar até aparecer "Pesquisar" em 10s
ENTÃO clicar em "Pesquisar"
ENTÃO digitar "música para dormir"
ENTÃO clicar em "Pesquisar"
ENTÃO esperar 3 segundos
ENTÃO rolar para baixo
ENTÃO mostrar aviso "Modo noite ativado"
ENTÃO registrar "modo noite ok"
"""
        const val SAMPLE_WPP = """ALVO WhatsApp

QUANDO chegar às 08:00
SE for segunda, terça, quarta, quinta, sexta
ENTÃO abrir WhatsApp
ENTÃO esperar até aparecer "Conversas" em 10s
ENTÃO clicar em "Novo chat"
ENTÃO digitar "João"
ENTÃO esperar até aparecer "João" em 10s
ENTÃO clicar em "João"
ENTÃO digitar "Bom dia! Está tudo certo?"
ENTÃO confirmar "Enviar mensagem para João?"
ENTÃO clicar em "Enviar"
ENTÃO registrar "Mensagem enviada"
"""
    }
}
