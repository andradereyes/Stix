package cl.andradereyes.stix.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Un pack de WhatsApp. WhatsApp no deja mezclar stickers animados y fijos en el mismo pack. */
data class Pack(
    val id: String,
    val name: String,
    val animated: Boolean,
    val version: Int,
    val stickers: List<String>,
    /** Versión que se mandó a WhatsApp la última vez. Si es menor que [version], hay que actualizar. */
    val sentVersion: Int = 0,
) {
    val needsUpdate get() = version > sentVersion
}

/**
 * Guarda los stickers en la memoria interna de la app:
 * files/packs/index.json y files/packs/<id>/<sticker>.webp + tray.png
 */
class StickerStore(context: Context) {

    private val root = File(context.filesDir, "packs").apply { mkdirs() }
    private val index = File(root, "index.json")

    fun packDir(id: String) = File(root, id)
    fun stickerFile(packId: String, name: String) = File(packDir(packId), name)
    fun trayFile(packId: String) = File(packDir(packId), TRAY)

    @Synchronized
    fun packs(): List<Pack> {
        if (!index.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(index.readText())
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                val st = o.getJSONArray("stickers")
                Pack(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    animated = o.getBoolean("animated"),
                    version = o.getInt("version"),
                    stickers = List(st.length()) { st.getString(it) },
                    sentVersion = o.optInt("sentVersion", 0),
                )
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun add(bytes: ByteArray, animated: Boolean): Pack {
        val packs = packs().toMutableList()
        var pack = packs.lastOrNull { it.animated == animated && it.stickers.size < MAX_PER_PACK }
        if (pack == null) {
            val n = packs.count { it.animated == animated } + 1
            pack = Pack(
                id = "stix_${if (animated) "a" else "s"}_${System.currentTimeMillis()}",
                name = if (animated) "Stix animados $n" else "Stix $n",
                animated = animated,
                version = 0,
                stickers = emptyList(),
            )
            packs += pack
        }
        packDir(pack.id).mkdirs()
        val name = "s${System.currentTimeMillis()}.webp"
        stickerFile(pack.id, name).writeBytes(bytes)

        val updated = pack.copy(version = pack.version + 1, stickers = pack.stickers + name)
        packs[packs.indexOfFirst { it.id == updated.id }] = updated
        if (!trayFile(updated.id).exists()) makeTray(updated)
        write(packs)
        return updated
    }

    @Synchronized
    fun delete(packId: String, name: String) {
        val packs = packs().toMutableList()
        val i = packs.indexOfFirst { it.id == packId }
        if (i < 0) return
        val pack = packs[i]
        stickerFile(pack.id, name).delete()
        val remaining = pack.stickers - name
        if (remaining.isEmpty()) {
            packDir(pack.id).deleteRecursively()
            packs.removeAt(i)
        } else {
            val updated = pack.copy(version = pack.version + 1, stickers = remaining)
            packs[i] = updated
            if (pack.stickers.firstOrNull() == name) makeTray(updated)
        }
        write(packs)
    }

    @Synchronized
    fun markSent(packId: String) {
        val packs = packs().toMutableList()
        val i = packs.indexOfFirst { it.id == packId }
        if (i < 0) return
        packs[i] = packs[i].copy(sentVersion = packs[i].version)
        write(packs)
    }

    /** Ícono del pack: primer fotograma del primer sticker, 96x96 PNG. */
    private fun makeTray(pack: Pack) {
        val first = pack.stickers.firstOrNull() ?: return
        runCatching {
            val bmp = ImageDecoder.decodeBitmap(ImageDecoder.createSource(stickerFile(pack.id, first))) { d, _, _ ->
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                d.setTargetSize(TRAY_SIZE, TRAY_SIZE)
            }
            trayFile(pack.id).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun write(packs: List<Pack>) {
        val arr = JSONArray()
        packs.forEach { p ->
            arr.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("animated", p.animated)
                    .put("version", p.version)
                    .put("stickers", JSONArray(p.stickers))
                    .put("sentVersion", p.sentVersion)
            )
        }
        val tmp = File(root, "index.json.tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(index)
    }

    companion object {
        const val TRAY = "tray.png"
        const val TRAY_SIZE = 96
        const val MAX_PER_PACK = 30
        const val MIN_PER_PACK = 3
    }
}
