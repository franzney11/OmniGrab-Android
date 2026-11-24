package com.franzney.omnigrab.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.franzney.omnigrab.data.SettingsRepository
import com.franzney.omnigrab.model.*
import com.franzney.omnigrab.ui.theme.*

@Composable
fun OmniGrabScreen(viewModel: OmniGrabViewModel) {
    val context = LocalContext.current
    val urlInput by viewModel.urlInput.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val meta by viewModel.meta.collectAsState()
    val jobs by viewModel.jobs.collectAsState()
    val credits by viewModel.credits.collectAsState()
    val folderPrefs by viewModel.folderPrefs.collectAsState()
    val errorBanner by viewModel.errorBanner.collectAsState()
    val infoBanner by viewModel.infoBanner.collectAsState()
    val showProModal by viewModel.showProModal.collectAsState()
    val showUpdateModal by viewModel.showUpdateModal.collectAsState()
    val updateInfo by viewModel.updateInfo.collectAsState()
    val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsState()
    val updateProgress by viewModel.updateProgress.collectAsState()
    val updateStatusText by viewModel.updateStatusText.collectAsState()
    val askFolderRequest by viewModel.askFolderRequest.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Video, 1 = Audio, 2 = Descargas
    var selectedVideoHeight by remember { mutableStateOf<Int?>(null) }
    var selectedVideoMode by remember { mutableStateOf(DownloadMode.VIDEO_AUDIO) }
    var selectedVideoIsPro by remember { mutableStateOf(false) }
    var selectedBitrate by remember { mutableIntStateOf(192) }

    val videoDefaultFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
            viewModel.setVideoDefaultFolder(uri)
        }
    }

    val audioDefaultFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
            viewModel.setAudioDefaultFolder(uri)
        }
    }

    val askDownloadFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
        }
        viewModel.onFolderPickedForPendingDownload(uri)
    }

    LaunchedEffect(askFolderRequest) {
        val req = askFolderRequest ?: return@LaunchedEffect
        val initialUriStr = if (req.mode == DownloadMode.AUDIO_ONLY) {
            folderPrefs.audioTreeUri
        } else {
            folderPrefs.videoTreeUri
        }
        val initialUri = initialUriStr?.let { runCatching { Uri.parse(it) }.getOrNull() }
        askDownloadFolderLauncher.launch(initialUri)
    }

    LaunchedEffect(meta) {
        selectedVideoHeight = null
        selectedVideoIsPro = false
        if (meta != null && selectedTab == 2) selectedTab = 0
    }

    val activeJobsCount = jobs.count {
        it.stage in setOf(JobStage.QUEUED, JobStage.DOWNLOADING, JobStage.PROCESSING, JobStage.UPSCALING)
    }

    Scaffold(
        containerColor = ObsidianBg,
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF101014),
                contentColor = Color.White,
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Rounded.Movie, contentDescription = "Video") },
                    label = { Text("Video .mp4") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Rounded.MusicNote, contentDescription = "Audio") },
                    label = { Text("Audio .mp3") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (activeJobsCount > 0) {
                                    Badge(containerColor = RiftIndigo) {
                                        Text("$activeJobsCount", color = Color.White)
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = "Descargas")
                        }
                    },
                    label = { Text("Descargas") }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF17153B).copy(alpha = 0.45f), ObsidianBg, ObsidianBg)
                    )
                )
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Brush.linearGradient(listOf(RiftCyan, RiftViolet))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Download,
                            contentDescription = null,
                            tint = Color(0xFF09090B),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "OmniGrab",
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        onClick = { viewModel.checkForUpdates(manual = true) },
                        shape = CircleShape,
                        color = GlassSurface,
                        border = BorderStroke(1.dp, GlassBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.Refresh,
                                contentDescription = "Actualizar",
                                tint = RiftCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            if (isCheckingUpdate) {
                                Spacer(Modifier.width(4.dp))
                                Text("...", fontSize = 12.sp, color = Color.LightGray)
                            }
                        }
                    }

                    Surface(
                        onClick = { viewModel.openProModal() },
                        shape = CircleShape,
                        color = GlassSurface,
                        border = BorderStroke(1.dp, ProAmber.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.Bolt,
                                contentDescription = null,
                                tint = ProAmber,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "$credits PRO",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            if (selectedTab != 2) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = GlassSurface,
                    border = BorderStroke(1.dp, GlassBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = viewModel::onUrlChanged,
                            placeholder = { Text("Pega enlace (YouTube, TikTok, IG, X...)", fontSize = 13.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { viewModel.analyze() },
                            enabled = !isAnalyzing && urlInput.isNotBlank(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = RiftIndigo)
                        ) {
                            Text(if (isAnalyzing) "..." else "Analizar", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            errorBanner?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = Color(0xFFF87171), fontSize = 12.sp)
            }
            infoBanner?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = EmeraldGreen, fontSize = 12.sp)
            }

            Spacer(Modifier.height(12.dp))

            when (selectedTab) {
                0, 1 -> {
                    val currentMeta = meta
                    if (currentMeta == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "Pega un enlace o compártelo desde otra app para empezar.",
                                color = Color(0xFF71717A),
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        val activeJobForThisUrl = jobs.firstOrNull {
                            it.url == currentMeta.url && it.stage in setOf(
                                JobStage.QUEUED, JobStage.DOWNLOADING, JobStage.PROCESSING, JobStage.UPSCALING
                            )
                        }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            item {
                                MediaPreviewCard(currentMeta)
                            }

                            if (activeJobForThisUrl != null) {
                                item {
                                    ActiveLockCard(activeJobForThisUrl)
                                }
                            } else if (selectedTab == 0) {
                                item {
                                    Text(
                                        "🎬 VIDEO + AUDIO (.MP4)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFA1A1AA)
                                    )
                                }
                                item {
                                    QualityGrid(
                                        options = currentMeta.videoOptions,
                                        mode = DownloadMode.VIDEO_AUDIO,
                                        selectedHeight = if (selectedVideoMode == DownloadMode.VIDEO_AUDIO) selectedVideoHeight else null,
                                        onSelect = { opt ->
                                            selectedVideoMode = DownloadMode.VIDEO_AUDIO
                                            selectedVideoHeight = opt.height
                                            selectedVideoIsPro = opt.isPro
                                        }
                                    )
                                }
                                item {
                                    Text(
                                        "🔇 SOLO VIDEO · SIN AUDIO (.MP4)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFA1A1AA)
                                    )
                                }
                                item {
                                    QualityGrid(
                                        options = currentMeta.videoOptions,
                                        mode = DownloadMode.VIDEO_ONLY,
                                        selectedHeight = if (selectedVideoMode == DownloadMode.VIDEO_ONLY) selectedVideoHeight else null,
                                        onSelect = { opt ->
                                            selectedVideoMode = DownloadMode.VIDEO_ONLY
                                            selectedVideoHeight = opt.height
                                            selectedVideoIsPro = opt.isPro
                                        }
                                    )
                                }
                                item {
                                    val h = selectedVideoHeight
                                    Button(
                                        onClick = {
                                            if (h != null) {
                                                viewModel.requestDownload(selectedVideoMode, h, 192, selectedVideoIsPro)
                                            }
                                        },
                                        enabled = h != null,
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (selectedVideoIsPro) ProAmber else RiftIndigo
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(50.dp)
                                    ) {
                                        val label = when {
                                            h == null -> "Selecciona una resolución"
                                            selectedVideoIsPro -> "🔒 Reescalar a ${h}p · 1 crédito PRO"
                                            selectedVideoMode == DownloadMode.VIDEO_ONLY -> "🔇 Descargar ${if (h > 0) "${h}p" else "mejor"} sin audio (Gratis)"
                                            else -> "🎬 Descargar ${if (h > 0) "${h}p" else "mejor"} con audio (Gratis)"
                                        }
                                        Text(
                                            label,
                                            fontWeight = FontWeight.Bold,
                                            color = if (selectedVideoIsPro) Color.Black else Color.White
                                        )
                                    }
                                }
                            } else {
                                item {
                                    Text(
                                        "🎵 SOLO AUDIO · MP3 CON METADATOS",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFA1A1AA)
                                    )
                                }
                                item {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        currentMeta.audioOptions.forEach { opt ->
                                            val selected = selectedBitrate == opt.bitrate
                                            Surface(
                                                onClick = { selectedBitrate = opt.bitrate },
                                                shape = RoundedCornerShape(12.dp),
                                                color = if (selected) RiftIndigo.copy(alpha = 0.2f) else GlassSurface,
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (selected) RiftCyan else EmeraldGreen.copy(alpha = 0.4f)
                                                ),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(14.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column {
                                                        Text("🎵 ${opt.bitrate} kbps", fontWeight = FontWeight.SemiBold, color = Color.White)
                                                        Text(opt.subtitle, fontSize = 12.sp, color = Color(0xFFA1A1AA))
                                                    }
                                                    Column(horizontalAlignment = Alignment.End) {
                                                        Text(formatSize(opt.estimatedBytes), fontSize = 12.sp, color = Color.LightGray)
                                                        Text("GRATIS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = EmeraldGreen)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                item {
                                    Button(
                                        onClick = {
                                            viewModel.requestDownload(DownloadMode.AUDIO_ONLY, 0, selectedBitrate, false)
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = RiftIndigo),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(50.dp)
                                    ) {
                                        Text("🎵 Descargar MP3 ${selectedBitrate} kbps (Gratis)", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        item {
                            FolderSettingsPanel(
                                prefs = folderPrefs,
                                onPickVideoFolder = {
                                    val initUri = folderPrefs.videoTreeUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
                                    videoDefaultFolderLauncher.launch(initUri)
                                },
                                onToggleVideoAuto = viewModel::toggleVideoAutoSave,
                                onPickAudioFolder = {
                                    val initUri = folderPrefs.audioTreeUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
                                    audioDefaultFolderLauncher.launch(initUri)
                                },
                                onToggleAudioAuto = viewModel::toggleAudioAutoSave
                            )
                        }

                        if (jobs.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 36.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Todavía no hay descargas en esta sesión.", color = Color(0xFF71717A), fontSize = 13.sp)
                                }
                            }
                        } else {
                            items(jobs, key = { it.id }) { job ->
                                JobCard(job = job) {
                                    val uriStr = job.savedUriString ?: return@JobCard
                                    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(Uri.parse(uriStr), job.mimeType)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    runCatching { context.startActivity(Intent.createChooser(viewIntent, "Abrir con")) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showProModal) {
        AlertDialog(
            onDismissRequest = viewModel::dismissProModal,
            containerColor = GlassSurface,
            title = { Text("Desbloquear reescalado PRO", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Subir la resolución por encima de la original requiere reescalado con FFmpeg (1 crédito PRO por vídeo).",
                        fontSize = 13.sp,
                        color = Color(0xFFA1A1AA)
                    )
                    SettingsRepository.PACKS.forEach { pack ->
                        OutlinedButton(
                            onClick = { viewModel.buySimulatedPack(pack.credits) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(pack.label, color = Color.White)
                                Text("${String.format("%.2f", pack.priceEur)} €", color = ProAmber, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissProModal) { Text("Cerrar") }
            }
        )
    }

    val info = updateInfo
    if (showUpdateModal && info != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissUpdateModal,
            containerColor = GlassSurface,
            title = {
                Text("Nueva versión v${info.latestVersion}", fontWeight = FontWeight.Bold, color = Color.White)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Versión actual: v${info.currentVersion}\nNueva versión: v${info.latestVersion}",
                        fontSize = 13.sp,
                        color = Color(0xFFD4D4D8)
                    )
                    if (info.notes.isNotBlank()) {
                        Text(info.notes, fontSize = 12.sp, color = Color(0xFFA1A1AA), maxLines = 5, overflow = TextOverflow.Ellipsis)
                    }
                    updateProgress?.let { p ->
                        LinearProgressIndicator(
                            progress = { p },
                            modifier = Modifier.fillMaxWidth(),
                            color = RiftCyan
                        )
                    }
                    updateStatusText?.let { st ->
                        Text(st, fontSize = 12.sp, color = RiftCyan)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = viewModel::applyUpdate,
                    enabled = updateProgress == null,
                    colors = ButtonDefaults.buttonColors(containerColor = RiftIndigo)
                ) {
                    Text("Actualizar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = viewModel::dismissUpdateModal,
                    enabled = updateProgress == null
                ) {
                    Text("Ahora no", color = Color(0xFFA1A1AA))
                }
            }
        )
    }
}

@Composable
private fun MediaPreviewCard(meta: MediaMeta) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = GlassSurface,
        border = BorderStroke(1.dp, GlassBorder),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = meta.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 112.dp, height = 74.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF27272A))
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(shape = CircleShape, color = RiftIndigo.copy(alpha = 0.25f)) {
                        Text(
                            meta.platform,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            fontSize = 11.sp,
                            color = RiftCyan,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (meta.durationSec > 0) {
                        Text(formatDuration(meta.durationSec), fontSize = 11.sp, color = Color(0xFFA1A1AA))
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(meta.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Original máx: ${if (meta.nativeMaxHeight > 0) "${meta.nativeMaxHeight}p" else "desconocida"}",
                    fontSize = 12.sp,
                    color = Color(0xFFA1A1AA)
                )
            }
        }
    }
}

@Composable
private fun QualityGrid(
    options: List<VideoOption>,
    mode: DownloadMode,
    selectedHeight: Int?,
    onSelect: (VideoOption) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(2).forEach { rowItems ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { opt ->
                    val isSelected = selectedHeight == opt.height
                    val borderColor = when {
                        isSelected -> RiftCyan
                        opt.isPro -> ProAmber.copy(alpha = 0.45f)
                        else -> EmeraldGreen.copy(alpha = 0.4f)
                    }
                    Surface(
                        onClick = { onSelect(opt) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) RiftIndigo.copy(alpha = 0.22f) else GlassSurface,
                        border = BorderStroke(1.dp, borderColor),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "${if (mode == DownloadMode.VIDEO_ONLY) "🔇" else "🎬"} ${opt.label}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                            Text(formatSize(opt.estimatedBytes), fontSize = 11.sp, color = Color(0xFFA1A1AA))
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = if (opt.isPro) "🔒 PRO · UPSCALE" else "GRATIS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (opt.isPro) ProAmber else EmeraldGreen
                            )
                        }
                    }
                }
                if (rowItems.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ActiveLockCard(job: DownloadJob) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = RiftIndigo.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, RiftIndigo.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "${job.stage.label} (${job.progress.toInt()}%)",
                fontWeight = FontWeight.Bold,
                color = RiftCyan
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (job.progress / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = RiftIndigo
            )
            Spacer(Modifier.height(6.dp))
            Text(
                listOfNotNull(job.speed.takeIf { it.isNotBlank() }, job.eta.takeIf { it.isNotBlank() }?.let { "ETA $it" })
                    .joinToString(" · "),
                fontSize = 12.sp,
                color = Color(0xFFA1A1AA)
            )
        }
    }
}

@Composable
private fun FolderSettingsPanel(
    prefs: FolderPreferences,
    onPickVideoFolder: () -> Unit,
    onToggleVideoAuto: (Boolean) -> Unit,
    onPickAudioFolder: () -> Unit,
    onToggleAudioAuto: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = GlassSurface,
        border = BorderStroke(1.dp, GlassBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Rutas y guardado automático", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)

            MediaFolderRow(
                title = "🎬 Video (.mp4)",
                folderLabel = prefs.videoFolderLabel,
                autoSave = prefs.videoAutoSave,
                onPickFolder = onPickVideoFolder,
                onToggleAuto = onToggleVideoAuto
            )

            HorizontalDivider(color = GlassBorder)

            MediaFolderRow(
                title = "🎵 Audio (.mp3)",
                folderLabel = prefs.audioFolderLabel,
                autoSave = prefs.audioAutoSave,
                onPickFolder = onPickAudioFolder,
                onToggleAuto = onToggleAudioAuto
            )
        }
    }
}

@Composable
private fun MediaFolderRow(
    title: String,
    folderLabel: String,
    autoSave: Boolean,
    onPickFolder: () -> Unit,
    onToggleAuto: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFFE4E4E7))

        OutlinedButton(
            onClick = onPickFolder,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, GlassBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = RiftCyan, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Ruta por defecto: $folderLabel",
                color = Color.White,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1D1D22))
                .clickable { onToggleAuto(!autoSave) }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Descarga automática sin preguntar", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White)
                Text(
                    if (autoSave) "Activado: guarda directo en la ruta por defecto" else "Apagado: abre el explorador en esa ruta",
                    fontSize = 11.sp,
                    color = if (autoSave) EmeraldGreen else Color(0xFFA1A1AA)
                )
            }
            Switch(
                checked = autoSave,
                onCheckedChange = onToggleAuto,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = EmeraldGreen,
                    uncheckedThumbColor = Color(0xFFA1A1AA),
                    uncheckedTrackColor = Color(0xFF27272A)
                )
            )
        }
    }
}

@Composable
private fun JobCard(job: DownloadJob, onOpen: () -> Unit) {
    val statusColor = when (job.stage) {
        JobStage.DONE -> EmeraldGreen
        JobStage.ERROR -> Color(0xFFF87171)
        JobStage.UPSCALING -> ProAmber
        else -> RiftCyan
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = GlassSurface,
        border = BorderStroke(1.dp, GlassBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(
                    job.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "${job.stage.label}${if (job.stage != JobStage.DONE && job.stage != JobStage.ERROR) " ${job.progress.toInt()}%" else ""}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
            }
            Text(job.description, fontSize = 11.sp, color = Color(0xFFA1A1AA))
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (if (job.stage == JobStage.DONE) 1f else job.progress / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(CircleShape),
                color = statusColor
            )
            if (job.stage == JobStage.DONE) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        job.outputDisplay,
                        fontSize = 11.sp,
                        color = Color(0xFFA1A1AA),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onOpen) {
                        Text("Abrir", color = RiftCyan, fontSize = 12.sp)
                    }
                }
            } else if (job.stage == JobStage.ERROR) {
                Spacer(Modifier.height(6.dp))
                Text(job.errorMessage, fontSize = 11.sp, color = Color(0xFFF87171))
            }
        }
    }
}

private fun formatDuration(sec: Long): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%d:%02d", m, s)
}

private fun formatSize(bytes: Long?): String {
    if (bytes == null || bytes <= 0L) return "tamaño ?"
    return when {
        bytes >= 1_073_741_824L -> String.format("~%.2f GB", bytes / 1_073_741_824.0)
        bytes >= 1_048_576L -> "~${bytes / 1_048_576L} MB"
        else -> "~${maxOf(1L, bytes / 1024L)} KB"
    }
}
