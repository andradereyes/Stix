package cl.andradereyes.stix.media

import java.io.ByteArrayOutputStream

/**
 * Arma un WebP animado a partir de fotogramas WebP fijos (los que genera Bitmap.compress).
 * Android no trae codificador de WebP animado, pero el formato es solo un contenedor RIFF:
 * VP8X + ANIM + un ANMF por fotograma con los datos ALPH/VP8/VP8L de cada imagen.
 * Especificación: developers.google.com/speed/webp/docs/riff_container
 */
object AnimatedWebp {

    private class Chunk(val fourcc: String, val data: ByteArray)

    private fun parseChunks(webp: ByteArray): List<Chunk> {
        require(
            webp.size >= 12 &&
                String(webp, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                String(webp, 8, 4, Charsets.US_ASCII) == "WEBP"
        ) { "No es WebP" }
        val out = mutableListOf<Chunk>()
        var p = 12
        while (p + 8 <= webp.size) {
            val cc = String(webp, p, 4, Charsets.US_ASCII)
            val size = le32(webp, p + 4)
            val start = p + 8
            val end = (start + size).coerceAtMost(webp.size)
            out += Chunk(cc, webp.copyOfRange(start, end))
            p = start + size + (size and 1)
        }
        return out
    }

    fun assemble(frames: List<ByteArray>, width: Int, height: Int, frameMs: Int): ByteArray {
        val body = ByteArrayOutputStream()

        val vp8x = ByteArray(10)
        vp8x[0] = (0x10 or 0x02).toByte() // alfa + animación
        put24(vp8x, 4, width - 1)
        put24(vp8x, 7, height - 1)
        writeChunk(body, "VP8X", vp8x)

        writeChunk(body, "ANIM", ByteArray(6)) // fondo transparente, loop infinito

        for (frame in frames) {
            val payload = ByteArrayOutputStream()
            val header = ByteArray(16)
            put24(header, 6, width - 1)
            put24(header, 9, height - 1)
            put24(header, 12, frameMs)
            header[15] = 0x02 // sin mezcla: cada fotograma reemplaza al anterior
            payload.write(header)
            parseChunks(frame)
                .filter { it.fourcc == "ALPH" || it.fourcc == "VP8 " || it.fourcc == "VP8L" }
                .forEach { writeChunk(payload, it.fourcc, it.data) }
            writeChunk(body, "ANMF", payload.toByteArray())
        }

        val bytes = body.toByteArray()
        val out = ByteArrayOutputStream(bytes.size + 12)
        out.write("RIFF".toByteArray(Charsets.US_ASCII))
        out.write(le32Bytes(bytes.size + 4))
        out.write("WEBP".toByteArray(Charsets.US_ASCII))
        out.write(bytes)
        return out.toByteArray()
    }

    private fun writeChunk(out: ByteArrayOutputStream, fourcc: String, data: ByteArray) {
        out.write(fourcc.toByteArray(Charsets.US_ASCII))
        out.write(le32Bytes(data.size))
        out.write(data)
        if (data.size and 1 == 1) out.write(0)
    }

    private fun le32(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF) or
            ((b[o + 1].toInt() and 0xFF) shl 8) or
            ((b[o + 2].toInt() and 0xFF) shl 16) or
            ((b[o + 3].toInt() and 0xFF) shl 24)

    private fun le32Bytes(v: Int) = byteArrayOf(
        (v and 0xFF).toByte(),
        ((v shr 8) and 0xFF).toByte(),
        ((v shr 16) and 0xFF).toByte(),
        ((v shr 24) and 0xFF).toByte(),
    )

    private fun put24(b: ByteArray, o: Int, v: Int) {
        b[o] = (v and 0xFF).toByte()
        b[o + 1] = ((v shr 8) and 0xFF).toByte()
        b[o + 2] = ((v shr 16) and 0xFF).toByte()
    }
}
