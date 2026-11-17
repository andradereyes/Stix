package cl.andradereyes.stix.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

object WhatsApp {
    private val PACKAGES = listOf("com.whatsapp", "com.whatsapp.w4b")

    fun authority(ctx: Context) = ctx.packageName + ".stickercontentprovider"

    private fun installed(ctx: Context): List<String> = PACKAGES.filter {
        try {
            ctx.packageManager.getPackageInfo(it, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /** Intent oficial para agregar (o actualizar) un pack. null si WhatsApp no está instalado. */
    fun addIntent(ctx: Context, pack: Pack): Intent? {
        val pkg = installed(ctx).firstOrNull() ?: return null
        return Intent("com.whatsapp.intent.action.ENABLE_STICKER_PACK").apply {
            putExtra("sticker_pack_id", pack.id)
            putExtra("sticker_pack_authority", authority(ctx))
            putExtra("sticker_pack_name", pack.name)
            setPackage(pkg)
        }
    }

    /** Pregunta a WhatsApp si el pack ya está agregado. */
    fun isAdded(ctx: Context, pack: Pack): Boolean {
        for (pkg in installed(ctx)) {
            val uri = Uri.Builder()
                .scheme("content")
                .authority("$pkg.provider.sticker_whitelist_check")
                .appendPath("is_whitelisted")
                .appendQueryParameter("authority", authority(ctx))
                .appendQueryParameter("identifier", pack.id)
                .build()
            val added = runCatching {
                ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                    c.moveToFirst() && c.getInt(c.getColumnIndexOrThrow("result")) == 1
                } ?: false
            }.getOrDefault(false)
            if (added) return true
        }
        return false
    }
}
