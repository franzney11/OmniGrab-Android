package com.franzney.omnigrab.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.franzney.omnigrab.BuildConfig
import com.franzney.omnigrab.model.ReleaseUpdateInfo
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object UpdateChecker {
    private const val API_LATEST = "https://api.github.com/repos/franzney11/OmniGrab-Android/releases/latest"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    fun checkLatestRelease(): ReleaseUpdateInfo {
        val request = Request.Builder()
            .url(API_LATEST)
            .header("User-Agent", "OmniGrab-Android-Updater")
            .header("Accept", "application/vnd.github+json")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("No se pudo consultar GitHub Releases (${response.code})")
            }
            val body = response.body?.string().orEmpty()
            val json = JSONObject(body)
            val tag = json.optString("tag_name").trim()
            val latest = tag.removePrefix("v").removePrefix("V")
            val notes = json.optString("body").trim()
            val assets = json.optJSONArray("assets")

            var apkUrl = ""
            var apkName = "OmniGrab-Android.apk"
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkName = name
                        apkUrl = asset.optString("browser_download_url")
                        break
                    }
                }
            }

            if (apkUrl.isBlank() && tag.isNotBlank()) {
                apkUrl = "https://github.com/franzney11/OmniGrab-Android/releases/download/$tag/OmniGrab-Android.apk"
            }

            val current = BuildConfig.VERSION_NAME
            val available = latest.isNotBlank() && compareVersions(latest, current) > 0 && apkUrl.isNotBlank()

            return ReleaseUpdateInfo(
                currentVersion = current,
                latestVersion = latest.ifBlank { current },
                available = available,
                downloadUrl = apkUrl,
                assetName = apkName,
                notes = notes
            )
        }
    }

    fun downloadAndPromptInstall(
        context: Context,
        info: ReleaseUpdateInfo,
        onProgress: (Float) -> Unit
    ) {
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val apkFile = File(updatesDir, info.assetName)
        if (apkFile.exists()) apkFile.delete()

        val request = Request.Builder()
            .url(info.downloadUrl)
            .header("User-Agent", "OmniGrab-Android-Updater")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Error descargando APK (${response.code})")
            }
            val body = response.body ?: throw IllegalStateException("Respuesta vacía al descargar APK")
            val total = body.contentLength()
            var copied = 0L

            body.byteStream().use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        copied += read
                        if (total > 0) {
                            onProgress((copied.toFloat() / total.toFloat()).coerceIn(0f, 1f))
                        }
                    }
                }
            }
        }

        if (!apkFile.exists() || apkFile.length() < 1_000_000L) {
            throw IllegalStateException("La APK descargada está incompleta o dañada.")
        }

        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(installIntent)
    }

    private fun compareVersions(a: String, b: String): Int {
        val pa = Regex("""\d+""").findAll(a).map { it.value.toIntOrNull() ?: 0 }.toList()
        val pb = Regex("""\d+""").findAll(b).map { it.value.toIntOrNull() ?: 0 }.toList()
        val maxLen = maxOf(pa.size, pb.size, 3)
        for (i in 0 until maxLen) {
            val va = pa.getOrElse(i) { 0 }
            val vb = pb.getOrElse(i) { 0 }
            if (va != vb) return va.compareTo(vb)
        }
        return 0
    }
}
