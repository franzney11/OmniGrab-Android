package com.franzney.omnigrab.model

enum class DownloadMode(val id: String) {
    VIDEO_AUDIO("video_audio"),
    VIDEO_ONLY("video_only"),
    AUDIO_ONLY("audio_only")
}

enum class JobStage(val label: String) {
    QUEUED("En cola"),
    DOWNLOADING("Descargando"),
    PROCESSING("Procesando"),
    UPSCALING("Reescalando"),
    DONE("Completada"),
    ERROR("Error")
}

data class VideoOption(
    val height: Int,
    val label: String,
    val isPro: Boolean,
    val estimatedBytes: Long?
)

data class AudioOption(
    val bitrate: Int,
    val subtitle: String,
    val estimatedBytes: Long?
)

data class MediaMeta(
    val url: String,
    val title: String,
    val thumbnail: String?,
    val durationSec: Long,
    val platform: String,
    val nativeMaxHeight: Int,
    val isPortrait: Boolean,
    val audioKbps: Int,
    val videoOptions: List<VideoOption>,
    val audioOptions: List<AudioOption>
)

data class DownloadJob(
    val id: String,
    val url: String,
    val title: String,
    val description: String,
    val mode: DownloadMode,
    val height: Int,
    val bitrate: Int,
    val isUpscale: Boolean,
    val stage: JobStage = JobStage.QUEUED,
    val progress: Float = 0f,
    val speed: String = "",
    val eta: String = "",
    val outputDisplay: String = "",
    val savedUriString: String? = null,
    val mimeType: String = "video/mp4",
    val errorMessage: String = ""
)

data class FolderPreferences(
    val videoTreeUri: String? = null,
    val videoFolderLabel: String = "Movies/OmniGrab",
    val videoAutoSave: Boolean = true,
    val audioTreeUri: String? = null,
    val audioFolderLabel: String = "Music/OmniGrab",
    val audioAutoSave: Boolean = true
)

data class ReleaseUpdateInfo(
    val currentVersion: String,
    val latestVersion: String,
    val available: Boolean,
    val downloadUrl: String,
    val assetName: String,
    val notes: String
)

data class CreditPack(
    val id: String,
    val credits: Int,
    val priceEur: Double,
    val label: String
)
