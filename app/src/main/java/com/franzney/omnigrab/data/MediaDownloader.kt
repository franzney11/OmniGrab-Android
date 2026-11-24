package com.franzney.omnigrab.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.franzney.omnigrab.model.AudioOption
import com.franzney.omnigrab.model.DownloadMode
import com.franzney.omnigrab.model.MediaMeta
import com.franzney.omnigrab.model.VideoOption
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import kotlin.math.min
import kotlin.math.pow

object MediaDownloader {
    private val LADDER = listOf(144, 240, 360, 480, 720, 1080, 1440, 2160)
    private val BITRATES = listOf(
        128 to "Estándar / Ligero",
        192 to "Alta calidad",
        256 to "Muy alta",
        320 to "Máxima calidad"
    )
    private val LABELS = mapOf(
        1440 to "1440p (2K)",
        2160 to "2160p (4K)"
    )

    fun analyzeUrl(rawUrl: String): MediaMeta {
        val url = rawUrl.trim()
        require(url.startsWith("http://") || url.startsWith("https://")) {
            "Introduce un enlace http(s) válido."
        }

        val request = YoutubeDLRequest(url).apply {
            addOption("--dump-single-json")
            addOption("--no-playlist")
            addOption("--no-warnings")
        }
        val response = YoutubeDL.getInstance().execute(request, null, null)
        var json = JSONObject(response.out)
        if (json.has("entries")) {
            val entries = json.optJSONArray("entries")
            if (entries != null && entries.length() > 0) {
                json = entries.getJSONObject(0)
            }
        }

        val title = json.optString("title").ifBlank { "video" }
        val thumbnail = json.optString("thumbnail").ifBlank { null }
        val duration = json.optLong("duration", 0L)
        val platform = json.optString("extractor_key")
            .ifBlank { json.optString("extractor").ifBlank { "Web" } }

        val formats = json.optJSONArray("formats")
        val sides = mutableSetOf<Int>()
        var portrait = false
        var maxAudioKbps = 0
        var bestAudioBytes: Long? = null
        var bestVideoSizeAtMax: Long? = null

        if (formats != null) {
            for (i in 0 until formats.length()) {
                val f = formats.getJSONObject(i)
                val vcodec = f.optString("vcodec", "none")
                val acodec = f.optString("acodec", "none")
                val w = f.optInt("width", 0)
                val h = f.optInt("height", 0)

                if (vcodec != "none" && vcodec.isNotBlank()) {
                    val shortSide = if (w > 0 && h > 0) min(w, h) else h
                    if (shortSide > 0) {
                        sides.add(shortSide)
                        if (w > 0 && h > 0 && w < h) portrait = true
                        val fs = f.optLong("filesize", f.optLong("filesize_approx", 0L))
                        if (fs > 0) bestVideoSizeAtMax = maxOf(bestVideoSizeAtMax ?: 0L, fs)
                    }
                } else if (acodec != "none" && acodec.isNotBlank()) {
                    val abr = f.optDouble("abr", f.optDouble("tbr", 0.0)).toInt()
                    if (abr > maxAudioKbps) {
                        maxAudioKbps = abr
                        val fs = f.optLong("filesize", f.optLong("filesize_approx", 0L))
                        bestAudioBytes = when {
                            fs > 0 -> fs
                            abr > 0 && duration > 0 -> (abr * 125L * duration)
                            else -> bestAudioBytes
                        }
                    }
                }
            }
        }

        if (sides.isEmpty()) {
            val topW = json.optInt("width", 0)
            val topH = json.optInt("height", 0)
            val s = if (topW > 0 && topH > 0) min(topW, topH) else topH
            if (s > 0) {
                sides.add(s)
                if (topW > 0 && topH > 0 && topW < topH) portrait = true
            }
        }

        val nativeMax = sides.maxOrNull() ?: 0
        val minSide = sides.minOrNull() ?: 0

        val videoOptions = if (nativeMax == 0) {
            listOf(VideoOption(0, "Mejor disponible", false, bestVideoSizeAtMax))
        } else {
            val freeHeights = (LADDER.filter { it in minSide..nativeMax } + nativeMax).distinct().sorted()
            val proHeights = LADDER.filter { it > nativeMax }
            val freeOpts = freeHeights.map { h ->
                val est = bestVideoSizeAtMax?.let {
                    ((it * (h.toDouble() / nativeMax.toDouble()).pow(1.6)).toLong() + (bestAudioBytes ?: 0L))
                }
                VideoOption(h, LABELS[h] ?: "${h}p", false, est)
            }
            val proOpts = proHeights.map { h ->
                val est = bestVideoSizeAtMax?.let {
                    ((it * (h.toDouble() / nativeMax.toDouble()).pow(2.0)).toLong() + (bestAudioBytes ?: 0L))
                }
                VideoOption(h, LABELS[h] ?: "${h}p", true, est)
            }
            freeOpts + proOpts
        }

        val audioOptions = BITRATES.map { (kbps, desc) ->
            val est = if (duration > 0) kbps * 125L * duration else null
            AudioOption(kbps, desc, est)
        }

        return MediaMeta(
            url = url,
            title = title,
            thumbnail = thumbnail,
            durationSec = duration,
            platform = platform,
            nativeMaxHeight = nativeMax,
            isPortrait = portrait,
            audioKbps = maxAudioKbps,
            videoOptions = videoOptions,
            audioOptions = audioOptions
        )
    }

    fun executeDownload(
        context: Context,
        jobId: String,
        url: String,
        title: String,
        mode: DownloadMode,
        height: Int,
        bitrate: Int,
        isUpscale: Boolean,
        isPortrait: Boolean,
        targetTreeUriString: String?,
        onStage: (String, Float, String, String) -> Unit
    ): Pair<Uri?, String> {
        val tempDir = File(context.cacheDir, "temp_media").apply { mkdirs() }
        tempDir.listFiles { f -> f.name.startsWith(jobId) }?.forEach { it.delete() }

        val outTemplate = File(tempDir, "$jobId.%(ext)s").absolutePath
        val request = YoutubeDLRequest(url).apply {
            addOption("--no-playlist")
            addOption("--no-warnings")
            addOption("-o", outTemplate)
        }

        when (mode) {
            DownloadMode.AUDIO_ONLY -> {
                request.addOption("-f", "bestaudio/best")
                request.addOption("-x")
                request.addOption("--audio-format", "mp3")
                request.addOption("--audio-quality", "${bitrate}K")
                request.addOption("--add-metadata")
            }
            DownloadMode.VIDEO_AUDIO, DownloadMode.VIDEO_ONLY -> {
                val dimKey = if (isPortrait) "width" else "height"
                val withAudio = mode == DownloadMode.VIDEO_AUDIO
                val formatSel = if (height > 0 && !isUpscale) {
                    if (withAudio) "bv*[$dimKey<=$height]+ba/b[$dimKey<=$height]/b"
                    else "bv*[$dimKey<=$height]/b[$dimKey<=$height]/b"
                } else {
                    if (withAudio) "bv*+ba/b" else "bv*/b"
                }
                request.addOption("-f", formatSel)
                request.addOption("--merge-output-format", "mp4")
            }
        }

        onStage("DOWNLOADING", 0f, "", "")
        YoutubeDL.getInstance().execute(request, jobId) { progress, etaInSeconds, line ->
            val p = progress.coerceIn(0f, 100f)
            val speedMatch = Regex("""([0-9.]+[KMG]i?B/s)""").find(line)?.groupValues?.get(1) ?: ""
            val etaStr = if (etaInSeconds > 0) formatEta(etaInSeconds) else ""
            onStage("DOWNLOADING", p, speedMatch, etaStr)
        }

        val wantedExt = if (mode == DownloadMode.AUDIO_ONLY) "mp3" else "mp4"
        val downloadedFiles = tempDir.listFiles { f ->
            f.name.startsWith("$jobId.") && !f.name.endsWith(".part") && !f.name.endsWith(".ytdl")
        }?.toList().orEmpty()

        var workingFile = downloadedFiles.firstOrNull { it.extension.equals(wantedExt, ignoreCase = true) }
            ?: downloadedFiles.firstOrNull()
            ?: throw IllegalStateException("No se encontró el archivo descargado.")

        if (isUpscale && mode != DownloadMode.AUDIO_ONLY && height > 0) {
            onStage("UPSCALING", 15f, "", "")
            val upscaledFile = File(tempDir, "${jobId}_up.mp4")
            val scale = if (isPortrait) "scale=$height:-2" else "scale=-2:$height"
            val vf = "$scale:flags=lanczos,unsharp=5:5:0.8:3:3:0.4"
            val args = mutableListOf(
                "-y",
                "-i", workingFile.absolutePath,
                "-vf", vf,
                "-c:v", "libx264",
                "-preset", "veryfast",
                "-crf", "18",
                "-pix_fmt", "yuv420p"
            )
            if (mode == DownloadMode.VIDEO_AUDIO) {
                args.addAll(listOf("-c:a", "aac", "-b:a", "192k"))
            } else {
                args.add("-an")
            }
            args.addAll(listOf("-movflags", "+faststart", upscaledFile.absolutePath))

            val ffmpegExitCode = executeFFmpeg(context, args)
            if (ffmpegExitCode != 0 || !upscaledFile.exists()) {
                throw IllegalStateException("FFmpeg falló al reescalar el vídeo.")
            }
            workingFile.delete()
            workingFile = upscaledFile
            onStage("UPSCALING", 95f, "", "")
        } else if (mode == DownloadMode.VIDEO_ONLY) {
            onStage("PROCESSING", 85f, "", "")
            val mutedFile = File(tempDir, "${jobId}_na.mp4")
            val args = arrayOf(
                "-y",
                "-i", workingFile.absolutePath,
                "-c", "copy",
                "-an",
                "-movflags", "+faststart",
                mutedFile.absolutePath
            )
            val ffmpegExitCode = executeFFmpeg(context, args.toList())
            if (ffmpegExitCode == 0 && mutedFile.exists()) {
                workingFile.delete()
                workingFile = mutedFile
            }
        }

        val safeTitle = title.replace(Regex("""[\\/:*?"<>|]+"""), "_").trim().take(100).ifBlank { "media" }
        val suffix = when {
            mode == DownloadMode.AUDIO_ONLY -> " [${bitrate}kbps]"
            height > 0 && mode == DownloadMode.VIDEO_ONLY -> " [${height}p sin audio]"
            height > 0 -> " [${height}p]"
            mode == DownloadMode.VIDEO_ONLY -> " [sin audio]"
            else -> ""
        }
        val finalName = "$safeTitle$suffix.${workingFile.extension}"
        val mime = if (mode == DownloadMode.AUDIO_ONLY) "audio/mpeg" else "video/mp4"

        val savedResult = saveToDestination(context, workingFile, finalName, mime, mode == DownloadMode.AUDIO_ONLY, targetTreeUriString)
        workingFile.delete()
        return savedResult
    }

    private fun executeFFmpeg(context: Context, args: List<String>): Int {
        val executable = File(context.applicationInfo.nativeLibraryDir, "libffmpeg.so")
        if (!executable.isFile) {
            throw IllegalStateException("No se encontró el ejecutable de FFmpeg.")
        }

        val ffmpegLibDir = File(context.noBackupFilesDir, "youtubedl-android/packages/ffmpeg/usr/lib")
        val process = ProcessBuilder(listOf(executable.absolutePath) + args).apply {
            environment()["LD_LIBRARY_PATH"] = ffmpegLibDir.absolutePath
            environment()["TMPDIR"] = context.cacheDir.absolutePath
            redirectErrorStream(true)
        }.start()

        process.inputStream.bufferedReader().use { reader ->
            while (reader.readLine() != null) {
                // Drain FFmpeg output to prevent the process from blocking on a full pipe.
            }
        }
        return process.waitFor()
    }

    private fun saveToDestination(
        context: Context,
        sourceFile: File,
        displayName: String,
        mimeType: String,
        isAudio: Boolean,
        targetTreeUriString: String?
    ): Pair<Uri?, String> {
        if (!targetTreeUriString.isNullOrBlank()) {
            try {
                val treeUri = Uri.parse(targetTreeUriString)
                val dir = DocumentFile.fromTreeUri(context, treeUri)
                if (dir != null && dir.canWrite()) {
                    val created = dir.createFile(mimeType, displayName)
                    if (created != null) {
                        context.contentResolver.openOutputStream(created.uri)?.use { out ->
                            FileInputStream(sourceFile).use { input -> input.copyTo(out) }
                        }
                        val folderPath = treeUri.lastPathSegment?.substringAfter(':', dir.name ?: "Carpeta") ?: displayName
                        return created.uri to "$folderPath/$displayName"
                    }
                }
            } catch (_: Exception) {
                // Respaldo automático a MediaStore si el permiso SAF expiró
            }
        }

        val relativeDir = if (isAudio) {
            "${Environment.DIRECTORY_MUSIC}/OmniGrab"
        } else {
            "${Environment.DIRECTORY_MOVIES}/OmniGrab"
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDir)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val collection = if (isAudio) {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val resolver = context.contentResolver
        val itemUri = resolver.insert(collection, values)
            ?: throw IllegalStateException("No se pudo crear el archivo en el almacenamiento.")

        resolver.openOutputStream(itemUri)?.use { out ->
            FileInputStream(sourceFile).use { input -> input.copyTo(out) }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(itemUri, values, null, null)
        }

        return itemUri to "$relativeDir/$displayName"
    }

    private fun formatEta(secs: Long): String {
        val h = secs / 3600
        val m = (secs % 3600) / 60
        val s = secs % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
    }
}
