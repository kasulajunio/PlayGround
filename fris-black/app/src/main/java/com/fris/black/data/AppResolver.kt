package com.fris.black.data

import android.content.Context
import android.content.pm.PackageManager

// Resolve "WhatsApp" -> "com.whatsapp" etc.
// 1) busca por label instalada (case-insensitive, contém)
// 2) mapa de conhecidos
// 3) se já for package id, usa direto
object AppResolver {

    private val KNOWN = mapOf(
        "whatsapp" to "com.whatsapp",
        "whatsapp business" to "com.whatsapp.w4b",
        "youtube" to "com.google.android.youtube",
        "instagram" to "com.instagram.android",
        "tiktok" to "com.zhiliaoapp.musically",
        "facebook" to "com.facebook.katana",
        "telegram" to "org.telegram.messenger",
        "gmail" to "com.google.android.gm",
        "maps" to "com.google.android.apps.maps",
        "chrome" to "com.android.chrome",
        "spotify" to "com.spotify.music",
        "netflix" to "com.netflix.mediaclient",
        "configurações" to "com.android.settings",
        "configuracoes" to "com.android.settings",
        "câmera" to "com.android.camera2",
        "camera" to "com.android.camera2",
        "galeria" to "com.google.android.apps.photos",
        "fotos" to "com.google.android.apps.photos",
        "relógio" to "com.google.android.deskclock",
        "relogio" to "com.google.android.deskclock",
        "calculadora" to "com.google.android.calculator",
        "play store" to "com.android.vending"
    )

    fun resolve(ctx: Context, appName: String): String? {
        val clean = appName.trim().removeSurrounding("\"").trim()
        if (clean.contains(".")) {
            // parece package id — valida
            return try {
                ctx.packageManager.getPackageInfo(clean, 0)
                clean
            } catch (e: PackageManager.NameNotFoundException) { null }
        }
        val low = clean.lowercase()
        KNOWN[low]?.let { return it }
        KNOWN.entries.firstOrNull { low.contains(it.key) }?.let { return it.value }
        // busca nas instaladas
        return try {
            val pkgs = ctx.packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            pkgs.firstOrNull {
                val label = ctx.packageManager.getApplicationLabel(it).toString().lowercase()
                label == low || label.contains(low) || low.contains(label)
            }?.packageName
        } catch (e: Exception) { null }
    }

    fun installedLabels(ctx: Context, limit: Int = 200): List<Pair<String, String>> {
        return try {
            val pm = ctx.packageManager
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .mapNotNull {
                    try {
                        val label = pm.getApplicationLabel(it).toString()
                        val launch = pm.getLaunchIntentForPackage(it.packageName) ?: return@mapNotNull null
                        label to it.packageName
                    } catch (e: Exception) { null }
                }
                .sortedBy { it.first.lowercase() }
                .take(limit)
        } catch (e: Exception) { emptyList() }
    }
}
