package com.franzney.omnigrab

import android.app.Application
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OmniGrabApp : Application() {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    var engineReady: Boolean = false
        private set

    @Volatile
    var engineError: String? = null
        private set

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            try {
                YoutubeDL.getInstance().init(this@OmniGrabApp)
                FFmpeg.getInstance().init(this@OmniGrabApp)
                engineReady = true
            } catch (e: Exception) {
                Log.e("OmniGrabApp", "Error inicializando motor multimedia", e)
                engineError = e.message ?: "Error al iniciar yt-dlp/FFmpeg"
            }
        }
    }
}
