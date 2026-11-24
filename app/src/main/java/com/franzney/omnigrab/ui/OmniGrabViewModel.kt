package com.franzney.omnigrab.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.franzney.omnigrab.OmniGrabApp
import com.franzney.omnigrab.data.MediaDownloader
import com.franzney.omnigrab.data.SettingsRepository
import com.franzney.omnigrab.data.UpdateChecker
import com.franzney.omnigrab.model.DownloadJob
import com.franzney.omnigrab.model.DownloadMode
import com.franzney.omnigrab.model.FolderPreferences
import com.franzney.omnigrab.model.JobStage
import com.franzney.omnigrab.model.MediaMeta
import com.franzney.omnigrab.model.ReleaseUpdateInfo
import com.franzney.omnigrab.service.DownloadForegroundService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class PendingDownloadSelection(
    val mode: DownloadMode,
    val height: Int,
    val bitrate: Int,
    val isUpscale: Boolean
)

class OmniGrabViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as OmniGrabApp
    val settings = SettingsRepository(application)

    val credits: StateFlow<Int> = settings.creditsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val folderPrefs: StateFlow<FolderPreferences> = settings.folderPrefsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FolderPreferences())

    private val _urlInput = MutableStateFlow("")
    val urlInput: StateFlow<String> = _urlInput.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _meta = MutableStateFlow<MediaMeta?>(null)
    val meta: StateFlow<MediaMeta?> = _meta.asStateFlow()

    private val _jobs = MutableStateFlow<List<DownloadJob>>(emptyList())
    val jobs: StateFlow<List<DownloadJob>> = _jobs.asStateFlow()

    private val _errorBanner = MutableStateFlow<String?>(null)
    val errorBanner: StateFlow<String?> = _errorBanner.asStateFlow()

    private val _infoBanner = MutableStateFlow<String?>(null)
    val infoBanner: StateFlow<String?> = _infoBanner.asStateFlow()

    private val _showProModal = MutableStateFlow(false)
    val showProModal: StateFlow<Boolean> = _showProModal.asStateFlow()

    private val _updateInfo = MutableStateFlow<ReleaseUpdateInfo?>(null)
    val updateInfo: StateFlow<ReleaseUpdateInfo?> = _updateInfo.asStateFlow()

    private val _showUpdateModal = MutableStateFlow(false)
    val showUpdateModal: StateFlow<Boolean> = _showUpdateModal.asStateFlow()

    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate.asStateFlow()

    private val _updateProgress = MutableStateFlow<Float?>(null)
    val updateProgress: StateFlow<Float?> = _updateProgress.asStateFlow()

    private val _updateStatusText = MutableStateFlow<String?>(null)
    val updateStatusText: StateFlow<String?> = _updateStatusText.asStateFlow()

    private val _askFolderRequest = MutableStateFlow<PendingDownloadSelection?>(null)
    val askFolderRequest: StateFlow<PendingDownloadSelection?> = _askFolderRequest.asStateFlow()

    init {
        viewModelScope.launch {
            delay(800)
            checkForUpdates(manual = false)
        }
    }

    fun onUrlChanged(value: String) {
        _urlInput.value = value
    }

    fun clearBanners() {
        _errorBanner.value = null
        _infoBanner.value = null
    }

    fun analyze(overrideUrl: String? = null) {
        val targetUrl = (overrideUrl ?: _urlInput.value).trim()
        if (targetUrl.isBlank() || _isAnalyzing.value) return
        clearBanners()
        _isAnalyzing.value = true

        viewModelScope.launch {
            try {
                var waited = 0
                while (!app.engineReady && app.engineError == null && waited < 150) {
                    delay(100)
                    waited++
                }
                if (app.engineError != null) {
                    throw IllegalStateException(app.engineError)
                }

                val result = withContext(Dispatchers.IO) {
                    MediaDownloader.analyzeUrl(targetUrl)
                }
                _meta.value = result
            } catch (e: Exception) {
                _errorBanner.value = e.message?.take(220) ?: "No se pudo analizar el enlace."
            } finally {
                _urlInput.value = ""
                _isAnalyzing.value = false
            }
        }
    }

    fun requestDownload(mode: DownloadMode, height: Int, bitrate: Int, isUpscale: Boolean) {
        val currentMeta = _meta.value ?: return
        clearBanners()

        val hasActiveSameUrl = _jobs.value.any {
            it.url == currentMeta.url && it.stage in setOf(
                JobStage.QUEUED, JobStage.DOWNLOADING, JobStage.PROCESSING, JobStage.UPSCALING
            )
        }
        if (hasActiveSameUrl) {
            _errorBanner.value = "Ya hay una descarga activa para este enlace."
            return
        }

        viewModelScope.launch {
            if (isUpscale && settings.getCredits() < 1) {
                _showProModal.value = true
                return@launch
            }

            val prefs = folderPrefs.value
            val autoSave = if (mode == DownloadMode.AUDIO_ONLY) prefs.audioAutoSave else prefs.videoAutoSave
            if (autoSave) {
                val defaultUri = if (mode == DownloadMode.AUDIO_ONLY) prefs.audioTreeUri else prefs.videoTreeUri
                startDownloadJob(currentMeta, mode, height, bitrate, isUpscale, defaultUri)
            } else {
                _askFolderRequest.value = PendingDownloadSelection(mode, height, bitrate, isUpscale)
            }
        }
    }

    fun onFolderPickedForPendingDownload(uri: Uri?) {
        val pending = _askFolderRequest.value ?: return
        _askFolderRequest.value = null
        val currentMeta = _meta.value ?: return
        if (uri == null) return

        viewModelScope.launch {
            if (pending.mode == DownloadMode.AUDIO_ONLY) {
                settings.setAudioFolder(uri)
            } else {
                settings.setVideoFolder(uri)
            }
            startDownloadJob(
                currentMeta,
                pending.mode,
                pending.height,
                pending.bitrate,
                pending.isUpscale,
                uri.toString()
            )
        }
    }

    private fun startDownloadJob(
        currentMeta: MediaMeta,
        mode: DownloadMode,
        height: Int,
        bitrate: Int,
        isUpscale: Boolean,
        targetTreeUri: String?
    ) {
        viewModelScope.launch {
            if (isUpscale && !settings.consumeCredit()) {
                _showProModal.value = true
                return@launch
            }

            val jobId = UUID.randomUUID().toString().replace("-", "")
            val desc = when (mode) {
                DownloadMode.AUDIO_ONLY -> "MP3 ${bitrate} kbps"
                DownloadMode.VIDEO_ONLY -> "${if (height > 0) "${height}p" else "Mejor"} · sin audio${if (isUpscale) " · PRO" else ""}"
                DownloadMode.VIDEO_AUDIO -> "${if (height > 0) "${height}p" else "Mejor"} · con audio${if (isUpscale) " · PRO" else ""}"
            }

            val newJob = DownloadJob(
                id = jobId,
                url = currentMeta.url,
                title = currentMeta.title,
                description = desc,
                mode = mode,
                height = height,
                bitrate = bitrate,
                isUpscale = isUpscale,
                mimeType = if (mode == DownloadMode.AUDIO_ONLY) "audio/mpeg" else "video/mp4"
            )
            _jobs.update { listOf(newJob) + it }
            DownloadForegroundService.startOrUpdate(app, currentMeta.title, 0)

            try {
                val (savedUri, displayPath) = withContext(Dispatchers.IO) {
                    MediaDownloader.executeDownload(
                        context = app,
                        jobId = jobId,
                        url = currentMeta.url,
                        title = currentMeta.title,
                        mode = mode,
                        height = height,
                        bitrate = bitrate,
                        isUpscale = isUpscale,
                        isPortrait = currentMeta.isPortrait,
                        targetTreeUriString = targetTreeUri
                    ) { stageStr, progress, speed, eta ->
                        val stage = JobStage.valueOf(stageStr)
                        updateJob(jobId) {
                            it.copy(stage = stage, progress = progress, speed = speed, eta = eta)
                        }
                        DownloadForegroundService.startOrUpdate(
                            app,
                            "${stage.label} (${progress.toInt()}%): ${currentMeta.title}",
                            progress.toInt()
                        )
                    }
                }

                updateJob(jobId) {
                    it.copy(
                        stage = JobStage.DONE,
                        progress = 100f,
                        outputDisplay = displayPath,
                        savedUriString = savedUri?.toString()
                    )
                }
                _infoBanner.value = "✔ Guardado: $displayPath"
            } catch (e: Exception) {
                if (isUpscale) {
                    settings.addCredits(1)
                }
                updateJob(jobId) {
                    it.copy(
                        stage = JobStage.ERROR,
                        errorMessage = e.message?.take(200) ?: "Error en la descarga."
                    )
                }
                _errorBanner.value = e.message?.take(200) ?: "Error en la descarga."
            } finally {
                val stillActive = _jobs.value.any {
                    it.stage in setOf(JobStage.QUEUED, JobStage.DOWNLOADING, JobStage.PROCESSING, JobStage.UPSCALING)
                }
                if (!stillActive) {
                    DownloadForegroundService.stop(app)
                }
            }
        }
    }

    private fun updateJob(id: String, transform: (DownloadJob) -> DownloadJob) {
        _jobs.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    fun openProModal() {
        _showProModal.value = true
    }

    fun dismissProModal() {
        _showProModal.value = false
    }

    fun buySimulatedPack(creditsToAdd: Int) {
        viewModelScope.launch {
            delay(400)
            settings.addCredits(creditsToAdd)
            _showProModal.value = false
            _infoBanner.value = "⚡ +$creditsToAdd créditos PRO añadidos."
        }
    }

    fun setVideoDefaultFolder(uri: Uri) {
        viewModelScope.launch { settings.setVideoFolder(uri) }
    }

    fun setAudioDefaultFolder(uri: Uri) {
        viewModelScope.launch { settings.setAudioFolder(uri) }
    }

    fun toggleVideoAutoSave(enabled: Boolean) {
        viewModelScope.launch { settings.setVideoAutoSave(enabled) }
    }

    fun toggleAudioAutoSave(enabled: Boolean) {
        viewModelScope.launch { settings.setAudioAutoSave(enabled) }
    }

    fun checkForUpdates(manual: Boolean) {
        if (_isCheckingUpdate.value) return
        _isCheckingUpdate.value = true
        viewModelScope.launch {
            try {
                val info = withContext(Dispatchers.IO) {
                    UpdateChecker.checkLatestRelease()
                }
                _updateInfo.value = info
                if (info.available) {
                    _updateProgress.value = null
                    _updateStatusText.value = null
                    _showUpdateModal.value = true
                } else if (manual) {
                    _infoBanner.value = "✔ Ya tienes la última versión (v${info.currentVersion})"
                }
            } catch (e: Exception) {
                if (manual) {
                    _errorBanner.value = e.message ?: "No se pudo comprobar actualizaciones."
                }
            } finally {
                _isCheckingUpdate.value = false
            }
        }
    }

    fun dismissUpdateModal() {
        if (_updateProgress.value == null) {
            _showUpdateModal.value = false
        }
    }

    fun applyUpdate() {
        val info = _updateInfo.value ?: return
        if (_updateProgress.value != null) return
        _updateProgress.value = 0f
        _updateStatusText.value = "Descargando ${info.assetName}..."

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    UpdateChecker.downloadAndPromptInstall(app, info) { p ->
                        _updateProgress.value = p
                        _updateStatusText.value = "Descargando actualización (${(p * 100).toInt()}%)..."
                    }
                }
                _updateStatusText.value = "Abriendo instalador de Android..."
                delay(800)
                _updateProgress.value = null
                _showUpdateModal.value = false
            } catch (e: Exception) {
                _updateProgress.value = null
                _updateStatusText.value = "Error: ${e.message}"
            }
        }
    }
}
