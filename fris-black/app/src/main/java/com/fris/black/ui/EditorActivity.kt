package com.fris.black.ui

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.fris.black.data.AppResolver
import com.fris.black.data.FrisStore
import com.fris.black.engine.Scheduler
import com.fris.black.script.FrisParser

class EditorActivity : AppCompatActivity() {

    private lateinit var nameView: EditText
    private lateinit var appView: AutoCompleteTextView
    private lateinit var codeView: EditText
    private lateinit var resultView: TextView
    private var editingName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 64)
        }
        scroll.addView(root)
        setContentView(scroll)

        editingName = intent.getStringExtra("name")

        root.addView(TextView(this).apply { text = "📝 Editor FrisScript"; textSize = 22f })

        root.addView(TextView(this).apply { text = "Nome do script" })
        nameView = EditText(this).apply { hint = "Ex: Modo Noite" }
        root.addView(nameView)

        root.addView(TextView(this).apply { text = "\nApp alvo (escolha — vira o ALVO)" })
        appView = AutoCompleteTextView(this).apply { hint = "Digite: WhatsApp, YouTube…" }
        root.addView(appView)
        val labels = AppResolver.installedLabels(this).map { it.first }.toTypedArray()
        appView.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, labels))
        appView.threshold = 1
        val btnAlvo = Button(this).apply { text = "⬇ Inserir ALVO no topo" }
        root.addView(btnAlvo)
        btnAlvo.setOnClickListener {
            val app = appView.text.toString().trim()
            if (app.isEmpty()) { Toast.makeText(this, "Escolha o app primeiro", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            val cur = codeView.text.toString()
            val noAlvo = cur.lines().filter { !it.trim().uppercase().startsWith("ALVO ") }.joinToString("\n")
            codeView.setText("ALVO $app\n\n$noAlvo".trim())
        }

        root.addView(TextView(this).apply { text = "\nScript" })
        codeView = EditText(this).apply {
            hint = "QUANDO chegar às 08:00\nENTÃO abrir WhatsApp\n…"
            minLines = 10
            setSingleLine(false)
        }
        root.addView(codeView)
        resultView = TextView(this).apply { textSize = 14f }
        root.addView(resultView)

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val bValid = Button(this).apply { text = "✔ Validar" }
        val bSave = Button(this).apply { text = "💾 Salvar + Ativar" }
        val bRun = Button(this).apply { text = "▶ Testar agora" }
        row.addView(bValid); row.addView(bSave); row.addView(bRun)
        root.addView(row)

        root.addView(TextView(this).apply {
            text = "\nAjuda rápida:\n" +
                "ALVO <app> — o script assume esse app como alvo\n" +
                "QUANDO abrir <app> | chegar às HH:MM | ligar o celular | bateria abaixo de N%\n" +
                "SE for depois das HH:MM | SE for segunda, terça… | SE bateria abaixo de N%\n" +
                "ENTÃO clicar em \"texto\" | digitar \"…\" | rolar para baixo | esperar 2 segundos\n" +
                "ENTÃO esperar até aparecer \"X\" em 10s | voltar | confirmar \"…?\" | parar script\n" +
                "⚠ ações sensíveis (enviar/apagar/pagar/pix/senha) exigem CONFIRMAR antes."
            textSize = 13f
        })

        editingName?.let { n ->
            val o = FrisStore.getScript(this, n)
            nameView.setText(n)
            codeView.setText(o?.optString("source", "") ?: "")
        } ?: run {
            codeView.setText("QUANDO chegar às 08:00\nENTÃO abrir YouTube\nENTÃO esperar 2 segundos\nENTÃO mostrar aviso \"Pronto!\"\n")
        }

        bValid.setOnClickListener { validate() }
        bSave.setOnClickListener {
            val errs = validate()
            if (errs) return@setOnClickListener
            val name = nameView.text.toString().trim()
            if (name.isEmpty()) { Toast.makeText(this, "Dê um nome ao script", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            FrisStore.saveScript(this, name, codeView.text.toString(), true)
            Scheduler.scheduleAll(this)
            Toast.makeText(this, "Ativado! ✅", Toast.LENGTH_SHORT).show()
            finish()
        }
        bRun.setOnClickListener {
            val errs = validate()
            if (errs) return@setOnClickListener
            var name = nameView.text.toString().trim()
            if (name.isEmpty()) name = "tmp_${System.currentTimeMillis()}"
            FrisStore.saveScript(this, name, codeView.text.toString(), true)
            Scheduler.runNow(this, name)
            Toast.makeText(this, "Rodando… veja Histórico", Toast.LENGTH_SHORT).show()
        }
    }

    private fun validate(): Boolean {
        val res = FrisParser.parse(codeView.text.toString())
        return if (res.errors.isEmpty()) {
            val s = res.script!!
            resultView.text = "✅ Válido! Alvo=${s.targetApp ?: "—"} | passos=${s.steps.size} | gatilho=${s.trigger}"
            false.not().let { false } // sem erro
        } else {
            resultView.text = "❌ ${res.errors.size} erro(s):\n" + res.errors.joinToString("\n") { it.toString() }
            true // tem erro
        }
    }
}
