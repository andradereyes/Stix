package cl.andradereyes.stix.data

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.FileNotFoundException

/**
 * Contrato oficial de WhatsApp para packs de stickers de terceros
 * (mismo esquema que el ejemplo oficial github.com/WhatsApp/stickers).
 */
class StickerContentProvider : ContentProvider() {

    private lateinit var store: StickerStore
    private lateinit var authority: String
    private val matcher = UriMatcher(UriMatcher.NO_MATCH)

    override fun onCreate(): Boolean {
        val ctx = context ?: return false
        authority = WhatsApp.authority(ctx)
        store = StickerStore(ctx)
        matcher.addURI(authority, METADATA, CODE_METADATA)
        matcher.addURI(authority, "$METADATA/*", CODE_METADATA_SINGLE)
        matcher.addURI(authority, "$STICKERS/*", CODE_STICKERS)
        matcher.addURI(authority, "$STICKERS_ASSET/*/*", CODE_ASSET)
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        return when (matcher.match(uri)) {
            CODE_METADATA -> packsCursor(store.packs())
            CODE_METADATA_SINGLE -> packsCursor(store.packs().filter { it.id == uri.lastPathSegment })
            CODE_STICKERS -> stickersCursor(store.packs().firstOrNull { it.id == uri.lastPathSegment })
            else -> throw IllegalArgumentException("URI desconocida: $uri")
        }
    }

    private fun packsCursor(packs: List<Pack>): Cursor {
        val c = MatrixCursor(
            arrayOf(
                "sticker_pack_identifier",
                "sticker_pack_name",
                "sticker_pack_publisher",
                "sticker_pack_icon",
                "android_play_store_link",
                "ios_app_download_link",
                "sticker_pack_publisher_email",
                "sticker_pack_publisher_website",
                "sticker_pack_privacy_policy_website",
                "sticker_pack_license_agreement_website",
                "image_data_version",
                "whatsapp_will_not_cache_stickers",
                "animated_sticker_pack",
            )
        )
        packs.forEach { p ->
            c.addRow(
                arrayOf<Any>(
                    p.id, p.name, "Stix", StickerStore.TRAY,
                    "", "", "", "", "", "",
                    p.version.toString(), 0, if (p.animated) 1 else 0,
                )
            )
        }
        return c
    }

    private fun stickersCursor(pack: Pack?): Cursor {
        val c = MatrixCursor(arrayOf("sticker_file_name", "sticker_emoji", "sticker_accessibility_text"))
        pack?.stickers?.forEach { c.addRow(arrayOf<Any>(it, "✨", "Sticker")) }
        return c
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor {
        if (matcher.match(uri) != CODE_ASSET) throw FileNotFoundException(uri.toString())
        val seg = uri.pathSegments
        val packId = seg[seg.size - 2]
        val name = seg.last()
        if (packId.contains("..") || name.contains("..")) throw FileNotFoundException(uri.toString())
        val file = if (name == StickerStore.TRAY) store.trayFile(packId) else store.stickerFile(packId, name)
        if (!file.exists()) throw FileNotFoundException(uri.toString())
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return AssetFileDescriptor(pfd, 0, AssetFileDescriptor.UNKNOWN_LENGTH)
    }

    override fun getType(uri: Uri): String? = when (matcher.match(uri)) {
        CODE_METADATA -> "vnd.android.cursor.dir/vnd.$authority.$METADATA"
        CODE_METADATA_SINGLE -> "vnd.android.cursor.item/vnd.$authority.$METADATA"
        CODE_STICKERS -> "vnd.android.cursor.dir/vnd.$authority.$STICKERS"
        CODE_ASSET -> if (uri.lastPathSegment == StickerStore.TRAY) "image/png" else "image/webp"
        else -> null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()

    companion object {
        private const val METADATA = "metadata"
        private const val STICKERS = "stickers"
        private const val STICKERS_ASSET = "stickers_asset"
        private const val CODE_METADATA = 1
        private const val CODE_METADATA_SINGLE = 2
        private const val CODE_STICKERS = 3
        private const val CODE_ASSET = 4
    }
}
