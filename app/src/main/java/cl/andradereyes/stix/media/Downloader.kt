package cl.andradereyes.stix.media

import android.net.Uri
import cl.andradereyes.stix.UserError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Convierte un link (tweet, post, página o archivo directo) en un archivo local.
 *
 * - X / Twitter: API pública de FxTwitter (api.fxtwitter.com), con d.fixupx.com de respaldo.
 * - Instagram, TikTok, Reddit, Bluesky: se prueba primero el servicio "fix" de embeds de cada red.
 * - Cualquier otra página: se busca og:video / og:image en el HTML.
 * - Links directos a imagen o video: se descargan tal cual.
 */
object Downloader {

    private val URL_REGEX = Regex("""https?://[^\s<>"']+""")
    private const val UA_BROWSER =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"

    // Los servicios de embeds devuelven las etiquetas og:video a los bots de vista previa.
    private const val UA_PREVIEW = "Mozilla/5.0 (compatible; Discordbot/2.0; +https://discordapp.com)"
    private const val MAX_BYTES = 60L * 1024 * 1024

    private val TWITTER_HOSTS = listOf(
        "twitter.com", "x.com", "fxtwitter.com", "fixupx.com", "vxtwitter.com", "fixvx.com",
    )
    private val FIXERS = mapOf(
        "instagram.com" to "kkinstagram.com",
        "tiktok.com" to "tnktok.com",
        "reddit.com" to "rxddit.com",
        "bsky.app" to "fxbsky.app",
    )

    fun extractUrl(text: String): String? =
        URL_REGEX.find(text)?.value?.trimEnd('.', ',', ')', ']', '!', '?')

    suspend fun fetch(url: String, dir: File): File = withContext(Dispatchers.IO) {
        val uri = Uri.parse(url)
        val host = (uri.host ?: throw UserError("Ese link no es válido"))
            .lowercase().removePrefix("www.").removePrefix("mobile.").removePrefix("m.")
        val media = if (TWITTER_HOSTS.any { host == it || host.endsWith(".$it") }) {
            twitterMedia(uri)
        } else {
            genericMedia(uri, host)
        }
        download(media, dir)
    }

    private fun twitterMedia(uri: Uri): String {
        val path = uri.path ?: ""
        val id = Regex("""/status(?:es)?/(\d+)""").find(path)?.groupValues?.get(1)
            ?: throw UserError("No encontré el tweet en ese link")
        val index = Regex("""/(?:photo|video)/(\d+)""").find(path)?.groupValues?.get(1)?.toIntOrNull()
        // API v2 (respuesta en "status") y v1 (respuesta en "tweet"), por si una de las dos cambia.
        var sawPost = false
        for (api in listOf("https://api.fxtwitter.com/2/status/$id", "https://api.fxtwitter.com/status/$id")) {
            val json = runCatching { JSONObject(readText(api)) }.getOrNull() ?: continue
            val post = json.optJSONObject("status") ?: json.optJSONObject("tweet") ?: continue
            sawPost = true
            val urls = mediaUrls(post).ifEmpty {
                // Si el tweet cita a otro que sí tiene el video, se usa ese.
                post.optJSONObject("quote")?.let { mediaUrls(it) } ?: emptyList()
            }
            if (urls.isNotEmpty()) return urls[((index ?: 1) - 1).coerceIn(0, urls.size - 1)]
        }
        if (sawPost) throw UserError("Ese tweet no tiene fotos ni videos")
        return "https://d.fixupx.com$path"
    }

    private fun mediaUrls(post: JSONObject): List<String> {
        val media = post.optJSONObject("media") ?: return emptyList()
        val arrays = listOfNotNull(
            media.optJSONArray("all"),
            media.optJSONArray("videos"),
            media.optJSONArray("photos"),
        )
        for (arr in arrays) {
            val urls = (0 until arr.length()).mapNotNull { i ->
                val item = arr.optJSONObject(i) ?: return@mapNotNull null
                bestUrl(item)
            }
            if (urls.isNotEmpty()) return urls
        }
        return emptyList()
    }

    private fun bestUrl(item: JSONObject): String? {
        // Para videos, prefiere la variante MP4 de mayor calidad si viene la lista.
        val variants = item.optJSONArray("variants") ?: item.optJSONArray("formats")
        if (variants != null) {
            val best = (0 until variants.length())
                .mapNotNull { variants.optJSONObject(it) }
                .filter { (it.optString("content_type") + it.optString("container") + it.optString("url")).contains("mp4") }
                .maxByOrNull { it.optLong("bitrate", 0) }
                ?.optString("url")
            if (!best.isNullOrBlank()) return best
        }
        return item.optString("url").takeIf { it.startsWith("http") }
    }

    private fun genericMedia(uri: Uri, host: String): String {
        val candidates = buildList {
            FIXERS.entries.firstOrNull { host == it.key || host.endsWith("." + it.key) }?.let {
                add(uri.buildUpon().authority(it.value).build().toString())
            }
            add(uri.toString())
        }
        for (c in candidates) {
            val found = runCatching { probe(c) }.getOrNull()
            if (found != null) return found
        }
        throw UserError("No encontré un video o una imagen en ese link")
    }

    /** Devuelve la URL del medio si la página lo es o lo declara en sus etiquetas og. */
    private fun probe(url: String): String? {
        val conn = open(url, UA_PREVIEW)
        try {
            val type = conn.contentType?.lowercase() ?: ""
            if (type.startsWith("image/") || type.startsWith("video/")) return conn.url.toString()
            if (!type.contains("html")) return null
            val html = conn.inputStream.use { String(readLimited(it, 1_500_000)) }
            return findMeta(html, conn.url)
        } finally {
            conn.disconnect()
        }
    }

    private val META = Regex("""<meta\s[^>]*>""", RegexOption.IGNORE_CASE)
    private val ATTR = Regex("""([a-zA-Z:_-]+)\s*=\s*(?:"([^"]*)"|'([^']*)')""")

    private fun findMeta(html: String, base: URL): String? {
        val tags = HashMap<String, String>()
        META.findAll(html).forEach { m ->
            val attrs = ATTR.findAll(m.value).associate {
                it.groupValues[1].lowercase() to it.groupValues[2].ifEmpty { it.groupValues[3] }
            }
            val key = (attrs["property"] ?: attrs["name"])?.lowercase() ?: return@forEach
            val content = attrs["content"]?.takeIf { it.isNotBlank() } ?: return@forEach
            tags.putIfAbsent(key, content.replace("&amp;", "&"))
        }
        val order = listOf(
            "og:video:secure_url", "og:video:url", "og:video", "twitter:player:stream",
            "og:image:secure_url", "og:image", "twitter:image",
        )
        val found = order.firstNotNullOfOrNull { tags[it] } ?: return null
        return URL(base, found).toString()
    }

    private fun download(url: String, dir: File): File {
        val conn = open(url, UA_BROWSER)
        try {
            val type = conn.contentType?.lowercase() ?: ""
            if (type.contains("html")) throw UserError("Ese link no lleva a un video o una imagen")
            if (conn.contentLengthLong > MAX_BYTES) throw UserError("El archivo es demasiado grande")
            val file = File(dir, "in_${System.currentTimeMillis()}")
            conn.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_BYTES) throw UserError("El archivo es demasiado grande")
                        out.write(buf, 0, n)
                    }
                }
            }
            return file
        } finally {
            conn.disconnect()
        }
    }

    private fun readText(url: String): String {
        val conn = open(url, "Stix/1.0 (Android)")
        try {
            return conn.inputStream.use { String(readLimited(it, 2_000_000)) }
        } finally {
            conn.disconnect()
        }
    }

    private fun readLimited(input: InputStream, limit: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (out.size() < limit) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    /** Abre la conexión siguiendo redirecciones (también de http a https). */
    private fun open(url: String, ua: String): HttpURLConnection {
        var current = URL(url)
        repeat(6) {
            val c = (current.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", ua)
                setRequestProperty("Accept", "*/*")
            }
            val code = c.responseCode
            if (code in 300..399) {
                val location = c.getHeaderField("Location")
                c.disconnect()
                if (location == null) throw UserError("El sitio respondió algo raro")
                current = URL(current, location)
                return@repeat
            }
            if (code >= 400) {
                c.disconnect()
                throw UserError("El sitio respondió con error $code")
            }
            return c
        }
        throw UserError("Demasiadas redirecciones")
    }
}
