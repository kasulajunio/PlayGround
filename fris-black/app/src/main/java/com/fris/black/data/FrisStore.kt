package com.fris.black.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

// Scripts + histórico salvos localmente (sem nuvem, privado).
// v2 usa SharedPreferences + JSON — simples, sem KSP/Room, build rápido.
object FrisStore {

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences("fris_black", Context.MODE_PRIVATE)

    // ---- scripts ----

    fun saveScript(ctx: Context, name: String, source: String, enabled: Boolean) {
        val all = loadAll(ctx).toMutableMap()
        all[name] = JSONObject().apply {
            put("source", source)
            put("enabled", enabled)
            put("updatedAt", System.currentTimeMillis())
        }
        prefs(ctx).edit().putString("scripts", JSONObject(all as Map<*, *>).toString()).apply()
    }

    fun loadAll(ctx: Context): Map<String, JSONObject> {
        val raw = prefs(ctx).getString("scripts", null) ?: return emptyMap()
        return try {
            val o = JSONObject(raw)
            o.keys().asSequence().associateWith { o.getJSONObject(it) }
        } catch (e: Exception) { emptyMap() }
    }

    fun getScript(ctx: Context, name: String): JSONObject? = loadAll(ctx)[name]

    fun deleteScript(ctx: Context, name: String) {
        val all = loadAll(ctx).toMutableMap()
        all.remove(name)
        prefs(ctx).edit().putString("scripts", JSONObject(all as Map<*, *>).toString()).apply()
    }

    // ---- histórico (máx 200 entradas) ----

    fun appendHistory(ctx: Context, text: String) {
        val p = prefs(ctx)
        val arr = try { JSONArray(p.getString("history", "[]")) } catch (e: Exception) { JSONArray() }
        arr.put(JSONObject().apply {
            put("at", System.currentTimeMillis())
            put("text", text)
        })
        while (arr.length() > 200) arr.remove(0)
        p.edit().putString("history", arr.toString()).apply()
    }

    fun loadHistory(ctx: Context): List<Pair<Long, String>> {
        return try {
            val arr = JSONArray(prefs(ctx).getString("history", "[]"))
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                o.getLong("at") to o.getString("text")
            }.reversed()
        } catch (e: Exception) { emptyList() }
    }
}
