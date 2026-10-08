package com.fris.black.ui

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.fris.black.data.FrisStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 64)
        }
        scroll.addView(root)
        setContentView(scroll)
        root.addView(TextView(this).apply { text = "📜 Histórico"; textSize = 22f })
        val fmt = SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault())
        val items = FrisStore.loadHistory(this)
        if (items.isEmpty()) root.addView(TextView(this).apply { text = "(vazio — execute um script)" })
        for ((at, text) in items) {
            root.addView(TextView(this).apply {
                this.text = "${fmt.format(Date(at))}  $text"
                textSize = 13f
            })
        }
    }
}
