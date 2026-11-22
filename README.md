# Stix

App Android para hacer stickers de WhatsApp sin borde blanco, desde un link o desde la galería.

- Links de X/Twitter, Instagram, TikTok, Reddit, Bluesky o cualquier página que tenga video o imagen.
- Fotos, GIF y videos del teléfono.
- Editor: mover, zoom, girar, voltear, ajustar o llenar, y recortar el clip.
- Sticker animado o fijo (a partir de cualquier fotograma).
- Los packs se agregan a WhatsApp con el sistema oficial de stickers de terceros.

## Instalar

Descarga el APK desde **[Releases → latest](../../releases/latest/download/Stix.apk)** y ábrelo en el teléfono.
Cada push a `main` compila uno nuevo con GitHub Actions y se instala encima del anterior.

## Cómo se usa

1. En X (u otra app), toca Compartir → Stix. O pega el link en la app.
2. Ajusta el sticker y toca **Listo**.
3. Cuando el pack tenga 3 stickers (mínimo de WhatsApp), toca **WhatsApp**.

Los stickers nuevos de un pack ya agregado aparecen solos en WhatsApp.

## Detalles técnicos

- Kotlin + Jetpack Compose, minSdk 30.
- Tweets vía la API pública de FxTwitter; otras redes vía sus servicios de embeds o etiquetas `og:video`.
- WebP animado armado a mano (`media/AnimatedWebp.kt`) a partir de fotogramas de `Bitmap.compress`, ajustando calidad y fps hasta quedar bajo los 500 KB que pide WhatsApp.
- Las APK se firman con una llave que no está en el repositorio: GitHub Actions la toma de los secretos `KEYSTORE_BASE64` y `KEYSTORE_PASSWORD`.
