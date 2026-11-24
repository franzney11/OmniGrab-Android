# OmniGrab Android

Aplicación nativa de Android (**Kotlin + Jetpack Compose**) para descargar vídeo (`.mp4`) y audio (`.mp3`) de forma 100% local en el dispositivo mediante `yt-dlp` y `FFmpeg` (`youtubedl-android`), sin servidores externos ni historial de enlaces.

## Cómo probarlo en un emulador (Android Studio)

1. Descomprime este ZIP y abre la carpeta **`OmniGrab-Android`** en **Android Studio** (*File → Open*).
2. Espera a que Gradle sincronice y descargue las dependencias automáticamente (incluye soporte `x86_64` para el emulador y `arm64-v8a` para móviles reales).
3. Selecciona un dispositivo virtual en el *Device Manager* (por ejemplo, Pixel con API 34/35 `x86_64`) y pulsa **Run** (`Shift + F10`).
