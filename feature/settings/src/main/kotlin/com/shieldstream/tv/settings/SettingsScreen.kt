package com.shieldstream.tv.settings

import androidx.compose.foundation.layout.widthIn

import androidx.compose.foundation.layout.heightIn

import androidx.compose.ui.draw.drawBehind

import androidx.compose.foundation.focusGroup

import androidx.compose.ui.focus.focusProperties

import androidx.compose.ui.draw.clip

import com.shieldstream.tv.ui.theme.TypeScale

import androidx.compose.foundation.layout.fillMaxHeight

import androidx.compose.runtime.key

import com.shieldstream.tv.ui.component.ValueSelector

import com.shieldstream.tv.ui.component.AppIcons

import androidx.compose.material3.Icon

import com.shieldstream.tv.ui.theme.FocusStyle

import com.shieldstream.tv.ui.theme.AppColors

import androidx.lifecycle.ViewModel
import com.shieldstream.tv.domain.DownloadsRepository
import com.shieldstream.tv.model.StorageOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject
import com.shieldstream.tv.ui.theme.AppShapes

import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.localizedStringResource
import com.shieldstream.tv.strings.keyNameResource
import com.shieldstream.tv.strings.sectionResource
import com.shieldstream.tv.strings.titleResource
import com.shieldstream.tv.strings.usedForResource
import com.shieldstream.tv.strings.whereToGetResource
import com.shieldstream.tv.strings.labelResource
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.shieldstream.tv.domain.SourceProvider
import com.shieldstream.tv.ui.component.restartApp
import com.shieldstream.tv.model.AppLanguage
import com.shieldstream.tv.model.AudioCodec
import com.shieldstream.tv.model.DeviceCapabilities
import com.shieldstream.tv.model.DeviceOptimization
import com.shieldstream.tv.model.HdrType
import com.shieldstream.tv.model.MemoryProfile
import com.shieldstream.tv.model.Quality
import com.shieldstream.tv.model.SourceExclusions
import com.shieldstream.tv.model.SubtitleAppearance
import com.shieldstream.tv.model.SubtitleColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import com.shieldstream.tv.settings.update.UpdateCard
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shieldstream.tv.domain.ApiService
import com.shieldstream.tv.domain.DebridService
import com.shieldstream.tv.domain.SettingsRepository
import com.shieldstream.tv.ui.component.ProvideMinimalScroll
import com.shieldstream.tv.ui.component.TvButton
import com.shieldstream.tv.ui.component.onBackKey
import com.shieldstream.tv.ui.component.tvClickable
import com.shieldstream.tv.ui.theme.DisplayFont

private val Background = Brush.verticalGradient(listOf(AppColors.BackgroundRaised, Color.Black))
private val CardColor = AppColors.Surface
private val CardFocused = AppColors.SurfaceFocused
private val CardShape = RoundedCornerShape(14.dp)
private val Muted = AppColors.TextSecondary
private val Green = AppColors.Success
private val Amber = AppColors.Warning
private val Red = AppColors.Error

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenSetup: () -> Unit = {},
    onOpenPhoneConfig: () -> Unit = {},
    onOpenAddons: () -> Unit = {},
    onOpenRequests: () -> Unit = {},
    onOpenErrorLog: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val ready = state as? SettingsUiState.Ready
    val context = LocalContext.current
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val sourceExclusions by viewModel.sourceExclusions.collectAsStateWithLifecycle()
    val brokenSourceCount by viewModel.brokenSourceCount.collectAsStateWithLifecycle()
    val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()
    val pendingRequestCount by viewModel.pendingRequestCount.collectAsStateWithLifecycle()
    val subtitleAppearance by viewModel.subtitleAppearance.collectAsStateWithLifecycle()
    val matchContentFrameRate by viewModel.matchContentFrameRate.collectAsStateWithLifecycle()
    Box(
        Modifier
            .fillMaxSize()
            .background(Background)
            .onBackKey(enabled = ready?.editor == null) { onBack() },
    ) {
        when (val s = state) {
            SettingsUiState.Loading -> Unit
            is SettingsUiState.Ready -> {
                SettingsList(
                    state = s,
                    onEditKey = { service ->
                        if (service == ApiService.AIOSTREAMS) onOpenAddons() else viewModel.openEditor(service)
                    },
                    onBuffer = viewModel::changeBuffer,
                    onSeekMode = viewModel::setSeekThumbnails,
                    onDebrid = viewModel::setDebrid,
                    onDeviceOptimization = viewModel::setDeviceOptimization,
                    onExperimentalSync = viewModel::setExperimentalSubtitleSync,
                    matchContentFrameRate = matchContentFrameRate,
                    onMatchContentFrameRate = viewModel::setMatchContentFrameRate,
                    subtitleAppearance = subtitleAppearance,
                    onSubtitleAppearance = viewModel::setSubtitleAppearance,
                    onOpenSetup = onOpenSetup,
                    onOpenPhoneConfig = onOpenPhoneConfig,
                    onOpenRequests = onOpenRequests,
                    onOpenErrorLog = onOpenErrorLog,
                    showRequests = isAdmin,
                    pendingRequestCount = pendingRequestCount,
                    onLanguage = { language -> viewModel.setLanguage(language) { restartApp(context) } },
                    providers = providers,
                    onToggleProvider = viewModel::toggleProvider,
                    sourceExclusions = sourceExclusions,
                    brokenSourceCount = brokenSourceCount,
                    onRestoreBrokenSources = viewModel::restoreBrokenSources,
                    onToggleQuality = viewModel::toggleExcludedQuality,
                    onToggleHdrType = viewModel::toggleExcludedHdrType,
                    onToggleAudioCodec = viewModel::toggleExcludedAudioCodec,
                )
                s.editor?.let { editor ->
                    KeyEditorDialog(
                        editor = editor,
                        onSave = viewModel::saveKey,
                        onRemove = viewModel::removeKey,
                        onClose = viewModel::closeEditor,
                        onAllDebridConnected = viewModel::completeAllDebridConnect,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsList(
    state: SettingsUiState.Ready,
    onEditKey: (ApiService) -> Unit,
    onBuffer: (Int) -> Unit,
    onSeekMode: (Boolean) -> Unit,
    onDebrid: (DebridService) -> Unit,
    onDeviceOptimization: (DeviceOptimization) -> Unit,
    onExperimentalSync: (Boolean) -> Unit,
    matchContentFrameRate: Boolean,
    onMatchContentFrameRate: (Boolean) -> Unit,
    subtitleAppearance: SubtitleAppearance,
    onSubtitleAppearance: (SubtitleAppearance) -> Unit,
    onOpenSetup: () -> Unit,
    onOpenPhoneConfig: () -> Unit,
    onOpenRequests: () -> Unit,
    onOpenErrorLog: () -> Unit,
    showRequests: Boolean,
    pendingRequestCount: Int,
    onLanguage: (AppLanguage) -> Unit,
    providers: Set<SourceProvider>,
    onToggleProvider: (SourceProvider) -> Unit,
    sourceExclusions: SourceExclusions,
    brokenSourceCount: Int,
    onRestoreBrokenSources: () -> Unit,
    onToggleQuality: (Quality) -> Unit,
    onToggleHdrType: (HdrType) -> Unit,
    onToggleAudioCodec: (AudioCodec) -> Unit,
) {
    val requesters = remember { ApiService.entries.associateWith { FocusRequester() } }
    val debridRequester = remember { FocusRequester() }
    var lastEdited by rememberSaveable { mutableStateOf<ApiService?>(null) }
    var qualityExclusionsExpanded by rememberSaveable { mutableStateOf(false) }
    var videoExclusionsExpanded by rememberSaveable { mutableStateOf(false) }
    var audioExclusionsExpanded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.editor == null) {
        if (state.editor == null) {
            withFrameNanos { }
            runCatching { (lastEdited?.let { requesters.getValue(it) } ?: debridRequester).requestFocus() }
        }
    }
    val debridUi = state.services.first { it.service == state.debrid.apiService }
    val otherServices = state.services.filter { !it.service.isDebrid }
    val providerSections = SourceProvider.entries
        .map { it to localizedStringResource(it.sectionResource) }
    // Pepino+: menú lateral por apartados; a la derecha, solo las opciones del apartado elegido.
    var section by rememberSaveable { mutableStateOf(SettingsSection.SERVICES) }
    Row(Modifier.fillMaxSize()) {
    SettingsSideMenu(
        title = localizedStringResource(StringsR.string.settings_settings),
        current = section,
        onSelect = { section = it },
    )
    key(section) {
    ProvideMinimalScroll(margin = 56.dp) {
        LazyColumn(
            contentPadding = PaddingValues(start = 48.dp, end = 64.dp, top = 44.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) {
            item {
                Column(Modifier.padding(start = 4.dp, bottom = 6.dp)) {
                    Text(section.title(), style = TypeScale.ScreenTitle, color = Color.White)
                    Spacer(Modifier.height(6.dp))
                    Text(section.description(), style = MaterialTheme.typography.bodyLarge, color = Muted)
                }
            }
            if (section == SettingsSection.SERVICES) {
            item { SectionTitle(localizedStringResource(StringsR.string.settings_debrid_service)) }
            item {
                DebridSelectorCard(
                    active = state.debrid,
                    onChange = onDebrid,
                    modifier = Modifier.focusRequester(debridRequester),
                )
            }
            item(key = "debrid_key") {
                ServiceCard(
                    ui = debridUi,
                    onClick = {
                        lastEdited = debridUi.service
                        onEditKey(debridUi.service)
                    },
                    modifier = Modifier.focusRequester(requesters.getValue(debridUi.service)),
                )
            }
            item {
                Text(
                    if (state.debrid.viaTorrentio && state.debrid.supportsDirectMagnetResolution) {
                        localizedStringResource(StringsR.string.settings_native_debrid_explanation, state.debrid.title)
                    } else if (state.debrid.viaTorrentio) {
                        localizedStringResource(StringsR.string.settings_torrentio_debrid_explanation, state.debrid.title)
                    } else {
                        localizedStringResource(StringsR.string.settings_real_debrid_connects_directly_from_the_shield)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextTertiary,
                    modifier = Modifier.padding(start = 6.dp, top = 2.dp),
                )
            }
            item { SectionTitle(localizedStringResource(StringsR.string.settings_other_services)) }
            items(otherServices, key = { it.service.name }) { ui ->
                ServiceCard(
                    ui = ui,
                    onClick = {
                        lastEdited = ui.service
                        onEditKey(ui.service)
                    },
                    modifier = Modifier.focusRequester(requesters.getValue(ui.service)),
                )
            }
            item {
                Text(
                    localizedStringResource(StringsR.string.settings_torrentio_sources_opensubtitles_and_podnapisi_subtitles_don),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextTertiary,
                    modifier = Modifier.padding(start = 6.dp, top = 2.dp),
                )
            }
            }
            if (section == SettingsSection.SOURCES) {
            item { SectionTitle(localizedStringResource(StringsR.string.settings_source_finders)) }
            items(providerSections.distinctBy { it.second }) { (provider, section) ->
                val enabled = providerSections.filter { it.second == section }.all { it.first in providers }
                ProviderCard(provider, enabled = enabled, onClick = { onToggleProvider(provider) })
            }
            if (brokenSourceCount > 0) {
                item {
                    BrokenSourcesCard(count = brokenSourceCount, onRestore = onRestoreBrokenSources)
                }
            }
            item { SectionTitle(localizedStringResource(StringsR.string.settings_source_exclusions)) }
            item {
                Text(
                    localizedStringResource(StringsR.string.settings_source_exclusions_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextTertiary,
                    modifier = Modifier.padding(start = 6.dp, top = 2.dp),
                )
            }
            item(key = "excluded-quality-header") {
                SourceExclusionGroupCard(
                    title = localizedStringResource(StringsR.string.settings_quality),
                    summary = acceptedSummary(
                        acceptedLabels = Quality.entries.filterNot { it in sourceExclusions.qualities }.map { qualityLabel(it) },
                        acceptsAll = sourceExclusions.qualities.isEmpty(),
                    ),
                    expanded = qualityExclusionsExpanded,
                    onClick = { qualityExclusionsExpanded = !qualityExclusionsExpanded },
                )
            }
            if (qualityExclusionsExpanded) {
                items(Quality.entries, key = { "excluded-quality:${it.name}" }) { quality ->
                    SourceExclusionCard(
                        label = qualityLabel(quality),
                        excluded = quality in sourceExclusions.qualities,
                        onClick = { onToggleQuality(quality) },
                    )
                }
            }
            item(key = "excluded-hdr-header") {
                SourceExclusionGroupCard(
                    title = localizedStringResource(StringsR.string.settings_video_features),
                    summary = acceptedSummary(
                        acceptedLabels = HdrType.entries.filterNot { it in sourceExclusions.hdrTypes }.map { it.label },
                        acceptsAll = sourceExclusions.hdrTypes.isEmpty(),
                    ),
                    expanded = videoExclusionsExpanded,
                    onClick = { videoExclusionsExpanded = !videoExclusionsExpanded },
                )
            }
            if (videoExclusionsExpanded) {
                items(HdrType.entries, key = { "excluded-hdr:${it.name}" }) { hdrType ->
                    SourceExclusionCard(
                        label = hdrType.label,
                        excluded = hdrType in sourceExclusions.hdrTypes,
                        onClick = { onToggleHdrType(hdrType) },
                    )
                }
            }
            item(key = "excluded-audio-header") {
                SourceExclusionGroupCard(
                    title = localizedStringResource(StringsR.string.settings_audio_codecs),
                    summary = acceptedSummary(
                        acceptedLabels = AudioCodec.entries.filterNot { it in sourceExclusions.audioCodecs }.map { it.label },
                        acceptsAll = sourceExclusions.audioCodecs.isEmpty(),
                    ),
                    expanded = audioExclusionsExpanded,
                    onClick = { audioExclusionsExpanded = !audioExclusionsExpanded },
                )
            }
            item {
                Text(
                    localizedStringResource(StringsR.string.settings_audio_codec_exclusions_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextTertiary,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            if (audioExclusionsExpanded) {
                items(AudioCodec.entries, key = { "excluded-audio:${it.name}" }) { codec ->
                    SourceExclusionCard(
                        label = codec.label,
                        excluded = codec in sourceExclusions.audioCodecs,
                        onClick = { onToggleAudioCodec(codec) },
                    )
                }
            }
            }
            if (section == SettingsSection.LOOK) {
            item { SectionTitle(localizedStringResource(StringsR.string.settings_home)) }
            item { HomeRowLimitCard() }
            item { LightweightModeCard() }
            item { SoftBackdropsCard() }
            item { HighResBackdropsCard() }
            item { SectionTitle("Idioma · Language") }
            item { LanguageCard(current = state.language, onApply = onLanguage) }
            }
            if (section == SettingsSection.PLAYBACK) {
            item { SectionTitle(localizedStringResource(StringsR.string.settings_playback)) }
            item { PlaybackPriorityCard() }
            item { PreferredQualityCard() }
            item { SwitchPickedSourceCard() }
            item { MatchContentFrameRateCard(enabled = matchContentFrameRate, onChange = onMatchContentFrameRate) }
            item { ExperimentalSyncCard(enabled = state.experimentalSubtitleSync, onChange = onExperimentalSync) }
            item {
                DeviceOptimizationCard(
                    optimization = state.optimization,
                    capabilities = state.capabilities,
                    memoryProfile = state.memoryProfile,
                    onChange = onDeviceOptimization,
                )
            }
            item {
                SeekModeCard(
                    thumbnails = state.seekThumbnails,
                    enabled = state.optimization == DeviceOptimization.CUSTOM,
                    onChange = onSeekMode,
                )
            }
            item { AutoNextCard() }
            item { AutoSkipIntroCard() }
            item { ShowCastCard() }
            item { TracksPanelStyleCard() }
            item { DownloadStorageCard() }
            item { EpisodeNotificationsCard() }
            item {
                BufferCard(
                    seconds = state.bufferSeconds,
                    enabled = state.optimization == DeviceOptimization.CUSTOM,
                    onChange = onBuffer,
                )
            }
            }
            if (section == SettingsSection.SYSTEM) {
            item { SectionTitle(localizedStringResource(StringsR.string.settings_about)) }
            item { AboutCard() }
            item { UpdateCard() }
            if (showRequests) {
                item { FamilyRequestsCard(count = pendingRequestCount, onClick = onOpenRequests) }
            }
            item { PhoneConfigCard(onClick = onOpenPhoneConfig) }
            item { ErrorLogCard(onClick = onOpenErrorLog) }
            item { SetupGuideCard(onClick = onOpenSetup) }
            }
        }
    }
    }
    }
}

/** Apartados de Ajustes, en el orden del menú lateral. */
private enum class SettingsSection { SERVICES, SOURCES, LOOK, PLAYBACK, SYSTEM }

private fun SettingsSection.icon(): androidx.compose.ui.graphics.vector.ImageVector = when (this) {
    SettingsSection.SERVICES -> AppIcons.Tv
    SettingsSection.SOURCES -> AppIcons.List
    SettingsSection.LOOK -> AppIcons.Home
    SettingsSection.PLAYBACK -> AppIcons.Play
    SettingsSection.SYSTEM -> AppIcons.Settings
}

private fun SettingsSection.title(): String {
    val en = com.shieldstream.tv.model.Lang.isEnglish
    return when (this) {
        SettingsSection.SERVICES -> if (en) "Services" else "Servicios"
        SettingsSection.SOURCES -> if (en) "Sources" else "Fuentes"
        SettingsSection.LOOK -> if (en) "Home and look" else "Inicio y aspecto"
        SettingsSection.PLAYBACK -> if (en) "Playback" else "Reproducción"
        SettingsSection.SYSTEM -> if (en) "System" else "Sistema"
    }
}

private fun SettingsSection.description(): String {
    val en = com.shieldstream.tv.model.Lang.isEnglish
    return when (this) {
        SettingsSection.SERVICES -> if (en) "Your debrid account and the keys of the services you use." else "Tu cuenta debrid y las claves de los servicios que usas."
        SettingsSection.SOURCES -> if (en) "Where links are searched and which ones are hidden." else "Dónde se buscan los enlaces y cuáles se descartan."
        SettingsSection.LOOK -> if (en) "Home rows, backdrops and language." else "Filas del Inicio, fondos e idioma."
        SettingsSection.PLAYBACK -> if (en) "Quality, subtitles, autoplay and buffer." else "Calidad, subtítulos, reproducción automática y búfer."
        SettingsSection.SYSTEM -> if (en) "Version, updates, phone setup and error log." else "Versión, actualizaciones, configuración desde el móvil y registro."
    }
}

@Suppress("unused")
private fun SettingsSection.subtitle(): String {
    val en = com.shieldstream.tv.model.Lang.isEnglish
    return when (this) {
        SettingsSection.SERVICES -> if (en) "Debrid and keys" else "Debrid y claves"
        SettingsSection.SOURCES -> if (en) "Finders and exclusions" else "Buscadores y exclusiones"
        SettingsSection.LOOK -> if (en) "Rows, backdrops, language" else "Filas, fondos, idioma"
        SettingsSection.PLAYBACK -> if (en) "Quality, subtitles, buffer" else "Calidad, subtítulos, búfer"
        SettingsSection.SYSTEM -> if (en) "Version, phone, error log" else "Versión, móvil, registro"
    }
}

/**
 * Menú lateral: al moverse por él cambia el apartado de la derecha (como los ajustes de Android TV); ▶ entra en las
 * opciones. El apartado abierto se marca con el color del tema; el foco, en blanco.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun SettingsSideMenu(title: String, current: SettingsSection, onSelect: (SettingsSection) -> Unit) {
    val requesters = remember { SettingsSection.entries.associateWith { FocusRequester() } }
    Column(
        Modifier
            // Al volver al menú desde las opciones (◀), el foco cae en el apartado abierto, no en el que quede al lado.
            .focusProperties {
                enter = { requesters.getValue(current) }
            }
            .focusGroup()
            .width(290.dp)
            .fillMaxHeight()
            .background(AppColors.BackgroundRaised)
            .padding(start = 20.dp, end = 16.dp, top = 40.dp),
    ) {
        // Cabecera como Pepino IPTV: la rodaja y «Ajustes».
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp)) {
            com.shieldstream.tv.ui.component.BrandMark(40.dp)
            Spacer(Modifier.width(14.dp))
            Text(
                if (com.shieldstream.tv.model.Lang.isEnglish) "Settings" else "Ajustes",
                style = TypeScale.ScreenTitle,
                color = Color.White,
            )
        }
        Spacer(Modifier.height(34.dp))
        SettingsSection.entries.forEach { item ->
            var focused by remember { mutableStateOf(false) }
            val selected = item == current
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .focusRequester(requesters.getValue(item))
                    .onFocusChanged {
                        focused = it.isFocused
                        if (it.isFocused) onSelect(item)
                    }
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (focused) Color.White.copy(alpha = 0.06f) else Color.Transparent)
                    .tvClickable(onClick = { onSelect(item) })
                    .padding(horizontal = 8.dp, vertical = 14.dp),
            ) {
                // Como Pepino IPTV: el apartado abierto lleva la raya verde y el icono y el título en verde.
                Box(
                    Modifier
                        .width(4.dp)
                        .height(30.dp)
                        .background(if (selected || focused) AppColors.Accent else Color.Transparent, RoundedCornerShape(2.dp)),
                )
                Spacer(Modifier.width(14.dp))
                Icon(
                    item.icon(),
                    contentDescription = null,
                    tint = if (selected || focused) AppColors.Accent else AppColors.TextSecondary,
                    modifier = Modifier.size(26.dp),
                )
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        item.title(),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = if (selected || focused) FontWeight.ExtraBold else FontWeight.Medium,
                        ),
                        color = if (selected || focused) AppColors.Accent else Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun FamilyRequestsCard(count: Int, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (focused) CardFocused else CardColor, CardShape)
            .border(if (focused) FocusStyle.Border else 1.dp, if (focused) FocusStyle.Frame else Color.White.copy(alpha = 0.08f), CardShape)
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 18.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                localizedStringResource(StringsR.string.settings_family_requests),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                localizedStringResource(StringsR.string.settings_family_requests_pending, count),
                style = MaterialTheme.typography.bodyMedium,
                color = Muted,
                maxLines = summaryMaxLines(),
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text("OK ›", color = Muted, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun BrokenSourcesCard(count: Int, onRestore: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (focused) CardFocused else CardColor, CardShape)
            .border(if (focused) FocusStyle.Border else 1.dp, if (focused) FocusStyle.Frame else Color.White.copy(alpha = 0.08f), CardShape)
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onClick = onRestore)
            .padding(horizontal = 24.dp, vertical = 18.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                localizedStringResource(StringsR.string.settings_broken_sources, count),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                localizedStringResource(StringsR.string.settings_broken_sources_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = Muted,
                maxLines = summaryMaxLines(),
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(localizedStringResource(StringsR.string.settings_restore), color = Muted, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun SectionTitle(text: String) {
    // Etiqueta de grupo (como Pepino IPTV): pequeña, en mayúsculas y espaciada.
    Text(
        text = text.uppercase(com.shieldstream.tv.model.Lang.locale),
        fontSize = 14.sp, lineHeight = 18.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp,
        color = AppColors.TextTertiary,
        modifier = Modifier.padding(top = 20.dp, bottom = 2.dp, start = 6.dp),
    )
}

@Composable
private fun FocusCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable (focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    // Pepino+ (como Pepino IPTV): sin marco ni relleno blanco. Con el cursor, raya verde a la izquierda y el
    // título en verde; el fondo de la fila apenas se aclara.
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .background(if (focused) CardFocused else CardColor, CardShape)
            .drawBehind {
                if (focused) {
                    val w = 4.dp.toPx()
                    drawRoundRect(
                        AppColors.Accent,
                        topLeft = androidx.compose.ui.geometry.Offset(0f, size.height * 0.2f),
                        size = androidx.compose.ui.geometry.Size(w, size.height * 0.6f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(w / 2),
                    )
                }
            }
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onClick = onClick ?: {})
            .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(LocalCardFocused provides focused) {
            content(focused)
        }
    }
}

private val LocalCardFocused = androidx.compose.runtime.compositionLocalOf { false }

/** Las explicaciones de cada opción ocupan una línea; con el cursor encima se ven enteras. */
@Composable
private fun summaryMaxLines(): Int = if (LocalCardFocused.current) 6 else 1

/** Color del título de una fila de Ajustes: verde con el cursor encima, blanco si no. */
@Composable
private fun cardTitleColor(): Color = if (LocalCardFocused.current) AppColors.Accent else Color.White

/** Interruptor (como Pepino IPTV): pista verde con la bola a la derecha si está activado; gris si no. */
@Composable
private fun SettingSwitch(on: Boolean) {
    androidx.compose.foundation.Canvas(Modifier.size(width = 56.dp, height = 32.dp)) {
        val h = size.height
        drawRoundRect(
            if (on) AppColors.Accent.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.16f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2),
        )
        val r = h / 2 - 4.dp.toPx()
        drawCircle(
            if (on) AppColors.Accent else Color.White.copy(alpha = 0.85f),
            radius = r,
            center = androidx.compose.ui.geometry.Offset(if (on) size.width - h / 2 else h / 2, h / 2),
        )
    }
}

private fun SubtitleColor.shift(colors: List<SubtitleColor>, delta: Int): SubtitleColor {
    val index = colors.indexOf(this).coerceAtLeast(0)
    return colors[(index + delta + colors.size) % colors.size]
}

@Composable
internal fun ServiceCard(ui: ServiceUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // Fila compacta (como Pepino IPTV): logo pequeño, nombre y para qué sirve; el estado a la derecha con «›».
    // La clave enmascarada solo se enseña con el cursor encima.
    FocusCard(modifier = modifier, onClick = onClick) { focused ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            ServiceLogo(ui.service)
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        localizedStringResource(ui.service.titleResource),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = cardTitleColor(),
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        when {
                            ui.service.isDebrid -> localizedStringResource(StringsR.string.settings_in_use)
                            ui.service.required -> localizedStringResource(StringsR.string.settings_required)
                            else -> localizedStringResource(StringsR.string.settings_optional)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = AppColors.TextTertiary,
                    )
                }
                Text(
                    localizedStringResource(ui.service.usedForResource),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                    maxLines = if (focused) 4 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (focused && ui.maskedKey != null) {
                    Text(
                        ui.maskedKey,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = AppColors.TextTertiary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            StatusPill(ui.status)
            Spacer(Modifier.width(8.dp))
            Icon(AppIcons.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.45f), modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun ServiceLogo(service: ApiService) {
    val (initials, color) = when (service) {
        ApiService.REAL_DEBRID -> "RD" to Color(0xFF6DB33F)
        ApiService.TORBOX -> "TB" to Color(0xFF7C5CFF)
        ApiService.PREMIUMIZE -> "PM" to Color(0xFFE5533D)
        ApiService.ALLDEBRID -> "AD" to Color(0xFFE9A21B)
        ApiService.DEBRID_LINK -> "DL" to Color(0xFF2F7DE1)
        ApiService.OFFCLOUD -> "OC" to Color(0xFF1FA58E)
        ApiService.TMDB -> "TM" to Color(0xFF01B4E4)
        ApiService.SUBDL -> "SD" to Color(0xFFF08A24)
        ApiService.AIOSTREAMS -> "+" to Color(0xFF8E5CF7)
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(38.dp).background(color, CircleShape),
    ) {
        Text(initials, fontFamily = DisplayFont, fontSize = 15.sp, lineHeight = 18.sp, color = Color.White)
    }
}

@Composable
private fun StatusPill(status: ServiceStatus) {
    val (color, text) = when (status) {
        ServiceStatus.Missing -> Amber to localizedStringResource(StringsR.string.settings_not_set_up)
        ServiceStatus.Checking -> Muted to localizedStringResource(StringsR.string.settings_checking)
        is ServiceStatus.Ok -> Green to (status.detail ?: localizedStringResource(StringsR.string.settings_connected))
        is ServiceStatus.Bad -> Red to status.message
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.widthIn(max = 260.dp)) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PhoneConfigCard(onClick: () -> Unit) {
    FocusCard(onClick = onClick) { focused ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    localizedStringResource(StringsR.string.settings_configure_from_phone),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = cardTitleColor(),
                )
                Text(
                    localizedStringResource(StringsR.string.settings_phone_config_card_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                    maxLines = summaryMaxLines(),
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                localizedStringResource(StringsR.string.settings_ok_to_open),
                style = MaterialTheme.typography.labelLarge,
                color = if (focused) FocusStyle.Content else Color.Transparent,
            )
        }
    }
}

@Composable
private fun ErrorLogCard(onClick: () -> Unit) {
    FocusCard(onClick = onClick) { focused ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (com.shieldstream.tv.model.Lang.isEnglish) "Error log" else "Registro de errores",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = cardTitleColor(),
                )
                Text(
                    if (com.shieldstream.tv.model.Lang.isEnglish) "Why Pepino+ closed unexpectedly" else "Por qué se ha cerrado Pepino+ de repente",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                    maxLines = summaryMaxLines(),
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                localizedStringResource(StringsR.string.settings_ok_to_open),
                style = MaterialTheme.typography.labelLarge,
                color = if (focused) FocusStyle.Content else Color.Transparent,
            )
        }
    }
}

@Composable
private fun SetupGuideCard(onClick: () -> Unit) {
    FocusCard(onClick = onClick) { focused ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    localizedStringResource(StringsR.string.settings_setup_guide),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = cardTitleColor(),
                )
                Text(
                    localizedStringResource(StringsR.string.settings_repeat_the_first_run_setup_step_by),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                    maxLines = summaryMaxLines(),
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                localizedStringResource(StringsR.string.settings_ok_to_open),
                style = MaterialTheme.typography.labelLarge,
                color = if (focused) FocusStyle.Content else Color.Transparent,
            )
        }
    }
}

/** Servicio debrid en uso: ◀ ▶ (u OK) recorren Real-Debrid, TorBox, Premiumize, AllDebrid, Debrid-Link y Offcloud. */
@Composable
internal fun DebridSelectorCard(active: DebridService, onChange: (DebridService) -> Unit, modifier: Modifier = Modifier) {
    val all = DebridService.entries
    fun shift(delta: Int) = onChange(all[(all.indexOf(active) + delta + all.size) % all.size])
    FocusCard(
        onClick = { shift(1) },
        modifier = modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionLeft -> { shift(-1); true }
                Key.DirectionRight -> { shift(1); true }
                else -> false
            }
        },
    ) { focused ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    localizedStringResource(StringsR.string.settings_service_in_use),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = cardTitleColor(),
                )
                Text(
                    if (active.viaTorrentio) localizedStringResource(StringsR.string.settings_sources_and_links_through_torrentio) else localizedStringResource(StringsR.string.settings_direct_connection_to_real_debrid),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                    maxLines = summaryMaxLines(),
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(24.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Arrow("◀", enabled = focused)
                    Text(
                        active.title,
                        fontFamily = DisplayFont,
                        fontSize = 24.sp, lineHeight = 28.sp,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 18.dp),
                    )
                    Arrow("▶", enabled = focused)
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    all.forEach { service ->
                        Box(
                            Modifier
                                .size(6.dp)
                                .background(
                                    if (service == active) Color.White else Color.White.copy(alpha = 0.25f),
                                    CircleShape,
                                ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeRowLimitCard(viewModel: SettingsViewModel = hiltViewModel()) {
    val limit by viewModel.homeRowItemLimit.collectAsStateWithLifecycle()
    FocusCard(
        onClick = { viewModel.changeHomeRowItemLimit(SettingsRepository.HOME_ROW_ITEM_LIMIT_STEP) },
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionLeft -> {
                    if (limit > SettingsRepository.MIN_HOME_ROW_ITEM_LIMIT) {
                        viewModel.changeHomeRowItemLimit(-SettingsRepository.HOME_ROW_ITEM_LIMIT_STEP)
                    }
                    true
                }
                Key.DirectionRight -> {
                    if (limit < SettingsRepository.MAX_HOME_ROW_ITEM_LIMIT) {
                        viewModel.changeHomeRowItemLimit(SettingsRepository.HOME_ROW_ITEM_LIMIT_STEP)
                    }
                    true
                }
                else -> false
            }
        },
    ) { focused ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    localizedStringResource(StringsR.string.settings_items_per_home_row),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = cardTitleColor(),
                )
                Text(
                    localizedStringResource(StringsR.string.settings_items_per_home_row_explanation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                    maxLines = summaryMaxLines(),
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Arrow("◀", enabled = focused && limit > SettingsRepository.MIN_HOME_ROW_ITEM_LIMIT)
                Text(
                    "$limit",
                    fontFamily = DisplayFont,
                    fontSize = 30.sp, lineHeight = 35.sp,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
                Arrow("▶", enabled = focused && limit < SettingsRepository.MAX_HOME_ROW_ITEM_LIMIT)
            }
        }
    }
}

/** Reduce image decoding, background changes and focus animations on low-power TV boxes. */
@Composable
private fun LightweightModeCard(viewModel: SettingsViewModel = hiltViewModel()) {
    val enabled by viewModel.lightweightUi.collectAsStateWithLifecycle()
    FocusCard(
        onClick = { viewModel.setLightweightUi(!enabled) },
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionLeft -> { viewModel.setLightweightUi(true); true }
                Key.DirectionRight -> { viewModel.setLightweightUi(false); true }
                else -> false
            }
        },
    ) { _ ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    localizedStringResource(StringsR.string.settings_lightweight_ui),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = cardTitleColor(),
                )
                Text(
                    localizedStringResource(StringsR.string.settings_smaller_backdrops_no_automatic_rotation_or_focus),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                    maxLines = summaryMaxLines(),
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(24.dp))
            SettingSwitch(enabled)
        }
    }
}

/** Blur the backdrop behind Home rows so the cards stand out. */
@Composable
private fun SoftBackdropsCard(viewModel: SettingsViewModel = hiltViewModel()) {
    val enabled by viewModel.softBackdrops.collectAsStateWithLifecycle()
    OnOffCard(
        title = localizedStringResource(StringsR.string.settings_soft_backdrops),
        summary = localizedStringResource(StringsR.string.settings_soft_backdrops_summary),
        enabled = enabled,
        onChange = viewModel::setSoftBackdrops,
    )
}

/** Intro de Bambi: siempre al abrir, o solo cuando hace falta (arranque o Inicio por recargar). */
@Composable
private fun IntroAlwaysCard(viewModel: SettingsViewModel = hiltViewModel()) {
    val enabled by viewModel.introAlways.collectAsStateWithLifecycle()
    OnOffCard(
        title = localizedStringResource(StringsR.string.settings_intro_always),
        summary = localizedStringResource(StringsR.string.settings_intro_always_summary),
        enabled = enabled,
        onChange = viewModel::setIntroAlways,
    )
}

/** Load the featured carousel and sharp Home backdrops at TMDB's original size. */
@Composable
private fun HighResBackdropsCard(viewModel: SettingsViewModel = hiltViewModel()) {
    val enabled by viewModel.highResBackdrops.collectAsStateWithLifecycle()
    OnOffCard(
        title = localizedStringResource(StringsR.string.settings_high_res_backdrops),
        summary = localizedStringResource(StringsR.string.settings_high_res_backdrops_summary),
        enabled = enabled,
        onChange = viewModel::setHighResBackdrops,
    )
}

/** On/Off setting row: OK toggles, Left selects On, Right selects Off. */
@Composable
private fun OnOffCard(title: String, summary: String, enabled: Boolean, onChange: (Boolean) -> Unit) {
    FocusCard(
        onClick = { onChange(!enabled) },
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionLeft -> { onChange(true); true }
                Key.DirectionRight -> { onChange(false); true }
                else -> false
            }
        },
    ) { _ ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = cardTitleColor(),
                )
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                    maxLines = summaryMaxLines(),
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(24.dp))
            SettingSwitch(enabled)
        }
    }
}

@Composable
private fun AutoNextCard(viewModel: SettingsViewModel = hiltViewModel()) {
    val enabled by viewModel.autoNextEpisode.collectAsStateWithLifecycle()
    FocusCard(
        onClick = { viewModel.setAutoNextEpisode(!enabled) },
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionLeft -> { viewModel.setAutoNextEpisode(true); true }
                Key.DirectionRight -> { viewModel.setAutoNextEpisode(false); true }
                else -> false
            }
        },
    ) { _ ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    localizedStringResource(StringsR.string.settings_autoplay_next_episode),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = cardTitleColor(),
                )
                Text(
                    localizedStringResource(StringsR.string.settings_shows_during_the_credits_next_episode_appears),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                    maxLines = summaryMaxLines(),
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(24.dp))
            SettingSwitch(enabled)
        }
    }
}

@Composable
private fun SwitchPickedSourceCard(viewModel: SettingsViewModel = hiltViewModel()) {
    val enabled by viewModel.switchFromPickedSource.collectAsStateWithLifecycle()
    FocusCard(
        onClick = { viewModel.setSwitchFromPickedSource(!enabled) },
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionLeft -> { viewModel.setSwitchFromPickedSource(true); true }
                Key.DirectionRight -> { viewModel.setSwitchFromPickedSource(false); true }
                else -> false
            }
        },
    ) { _ ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    localizedStringResource(StringsR.string.settings_switch_picked_source),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = cardTitleColor(),
                )
                Text(
                    localizedStringResource(StringsR.string.settings_switch_picked_source_detail),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                    maxLines = summaryMaxLines(),
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(24.dp))
            SettingSwitch(enabled)
        }
    }
}

@Composable
private fun ShowCastCard(viewModel: SettingsViewModel = hiltViewModel()) {
    val enabled by viewModel.showCast.collectAsStateWithLifecycle()
    FocusCard(
        onClick = { viewModel.setShowCast(!enabled) },
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionLeft -> { viewModel.setShowCast(true); true }
                Key.DirectionRight -> { viewModel.setShowCast(false); true }
                else -> false
            }
        },
    ) { _ ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    localizedStringResource(StringsR.string.settings_show_cast),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = cardTitleColor(),
                )
                Text(
                    localizedStringResource(StringsR.string.settings_show_cast_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                    maxLines = summaryMaxLines(),
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(24.dp))
            SettingSwitch(enabled)
        }
    }
}

@Composable
private fun TracksPanelStyleCard(viewModel: SettingsViewModel = hiltViewModel()) {
    val enabled by viewModel.tracksPanelCard.collectAsStateWithLifecycle()
    FocusCard(
        onClick = { viewModel.setTracksPanelCard(!enabled) },
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionLeft -> { viewModel.setTracksPanelCard(false); true }
                Key.DirectionRight -> { viewModel.setTracksPanelCard(true); true }
                else -> false
            }
        },
    ) { _ ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    localizedStringResource(StringsR.string.settings_tracks_panel),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = cardTitleColor(),
                )
                Text(
                    localizedStringResource(StringsR.string.settings_tracks_panel_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                    maxLines = summaryMaxLines(),
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(24.dp))
            Row(
                Modifier
                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(50))
                    .padding(4.dp),
            ) {
                SegmentOption(localizedStringResource(StringsR.string.settings_tracks_panel_transparent), selected = !enabled)
                SegmentOption(localizedStringResource(StringsR.string.settings_tracks_panel_card), selected = enabled)
            }
        }
    }
}

@Composable
private fun AutoSkipIntroCard(viewModel: SettingsViewModel = hiltViewModel()) {
    val enabled by viewModel.autoSkipIntro.collectAsStateWithLifecycle()
    FocusCard(
        onClick = { viewModel.setAutoSkipIntro(!enabled) },
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionLeft -> { viewModel.setAutoSkipIntro(true); true }
                Key.DirectionRight -> { viewModel.setAutoSkipIntro(false); true }
                else -> false
            }
        },
    ) { _ ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    localizedStringResource(StringsR.string.settings_auto_skip_intro),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = cardTitleColor(),
                )
                Text(
                    localizedStringResource(StringsR.string.settings_auto_skip_intro_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                    maxLines = summaryMaxLines(),
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(24.dp))
            SettingSwitch(enabled)
        }
    }
}

@Composable
private fun EpisodeNotificationsCard(viewModel: SettingsViewModel = hiltViewModel()) {
    val enabled by viewModel.episodeNotifications.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.setEpisodeNotifications(true)
    }
    fun enable() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.setEpisodeNotifications(true)
        }
    }
    FocusCard(
        onClick = { if (enabled) viewModel.setEpisodeNotifications(false) else enable() },
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionLeft -> { enable(); true }
                Key.DirectionRight -> { viewModel.setEpisodeNotifications(false); true }
                else -> false
            }
        },
    ) { _ ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    localizedStringResource(StringsR.string.settings_new_episode_notifications),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = cardTitleColor(),
                )
                Text(
                    localizedStringResource(StringsR.string.settings_followed_shows_only_checks_for_releases_every),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                    maxLines = summaryMaxLines(),
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(24.dp))
            Row(Modifier.background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(50)).padding(4.dp)) {
                SegmentOption(localizedStringResource(StringsR.string.settings_on), selected = enabled)
                SegmentOption(localizedStringResource(StringsR.string.settings_off_9847b4), selected = !enabled)
            }
        }
    }
}

@Composable
private fun MatchContentFrameRateCard(enabled: Boolean, onChange: (Boolean) -> Unit) {
    FocusCard(
        onClick = { onChange(!enabled) },
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionLeft -> { onChange(true); true }
                Key.DirectionRight -> { onChange(false); true }
                else -> false
            }
        },
    ) { _ ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    localizedStringResource(StringsR.string.settings_match_content_frame_rate),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = cardTitleColor(),
                )
                Text(
                    localizedStringResource(StringsR.string.settings_match_content_frame_rate_explanation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                    maxLines = summaryMaxLines(),
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(24.dp))
            SettingSwitch(enabled)
        }
    }
}

@Composable
private fun ExperimentalSyncCard(enabled: Boolean, onChange: (Boolean) -> Unit) {
    FocusCard(
        onClick = { onChange(!enabled) },
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionLeft -> { onChange(true); true }
                Key.DirectionRight -> { onChange(false); true }
                else -> false
            }
        },
    ) { _ ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    localizedStringResource(StringsR.string.settings_experimental_subtitle_sync),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = cardTitleColor(),
                )
                Text(
                    localizedStringResource(StringsR.string.settings_sync_demux_explanation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                    maxLines = summaryMaxLines(),
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(24.dp))
            SettingSwitch(enabled)
        }
    }
}

@Composable
private fun DeviceOptimizationCard(
    optimization: DeviceOptimization,
    capabilities: DeviceCapabilities,
    memoryProfile: MemoryProfile,
    onChange: (DeviceOptimization) -> Unit,
) {
    FocusCard(
        onClick = {
            onChange(if (optimization == DeviceOptimization.AUTOMATIC) DeviceOptimization.CUSTOM else DeviceOptimization.AUTOMATIC)
        },
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionLeft -> { onChange(DeviceOptimization.AUTOMATIC); true }
                Key.DirectionRight -> { onChange(DeviceOptimization.CUSTOM); true }
                else -> false
            }
        },
    ) { _ ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    localizedStringResource(StringsR.string.settings_device_optimization),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = cardTitleColor(),
                )
                Text(
                    if (optimization == DeviceOptimization.AUTOMATIC) {
                        localizedStringResource(StringsR.string.settings_bambi_adapts_memory_buffering_thumbnails_and_sources)
                    } else {
                        localizedStringResource(StringsR.string.settings_uses_your_manual_settings_and_shield_compatible)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                    maxLines = summaryMaxLines(),
                    overflow = TextOverflow.Ellipsis,
                )
                val codecs = capabilities.hardwareVideoCodecs
                    .sortedBy { it.ordinal }
                    .joinToString(" · ") { it.label }
                    .ifBlank { localizedStringResource(StringsR.string.settings_none_detected) }
                val hdr = capabilities.supportedHdr
                    .sortedBy { it.rank }
                    .joinToString(" · ") { it.label }
                    .ifBlank { localizedStringResource(StringsR.string.settings_no_hdr_detected) }
                val memory = when {
                    optimization == DeviceOptimization.AUTOMATIC && memoryProfile == MemoryProfile.CONSTRAINED ->
                        " · " + localizedStringResource(StringsR.string.settings_constrained_memory)
                    else -> ""
                }
                Text(
                    "${capabilities.model} · ${capabilities.heapLimitBytes / (1024 * 1024)} MB$memory\n$codecs\n$hdr",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextTertiary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Spacer(Modifier.width(24.dp))
            Row(
                Modifier
                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(50))
                    .padding(4.dp),
            ) {
                SegmentOption(localizedStringResource(StringsR.string.settings_automatic), selected = optimization == DeviceOptimization.AUTOMATIC)
                SegmentOption(localizedStringResource(StringsR.string.settings_custom), selected = optimization == DeviceOptimization.CUSTOM)
            }
        }
    }
}

@Composable
private fun SeekModeCard(thumbnails: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    FocusCard(
        onClick = { if (enabled) onChange(!thumbnails) },
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (!enabled || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionLeft -> { onChange(true); true }
                Key.DirectionRight -> { onChange(false); true }
                else -> false
            }
        },
    ) { _ ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    localizedStringResource(StringsR.string.settings_seeking_in_the_player),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = cardTitleColor(),
                )
                Text(
                    if (!enabled) {
                        localizedStringResource(StringsR.string.settings_chosen_automatically_for_this_device)
                    } else if (thumbnails) {
                        localizedStringResource(StringsR.string.settings_pause_and_show_thumbnails_of_the_chosen)
                    } else {
                        localizedStringResource(StringsR.string.settings_skip_forward_or_back_10_s_instantly)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                    maxLines = summaryMaxLines(),
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(24.dp))
            Row(
                Modifier
                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(50))
                    .padding(4.dp),
            ) {
                SegmentOption(localizedStringResource(StringsR.string.settings_thumbnails), selected = thumbnails)
                SegmentOption(localizedStringResource(StringsR.string.settings_10_s_skips), selected = !thumbnails)
            }
        }
    }
}

@Composable
private fun LanguageCard(current: AppLanguage, onApply: (AppLanguage) -> Unit) {
    var pending by remember(current) { mutableStateOf(current) }
    FocusCard(
        onClick = { if (pending != current) onApply(pending) },
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionLeft -> { pending = AppLanguage.ES; true }
                Key.DirectionRight -> { pending = AppLanguage.EN; true }
                else -> false
            }
        },
    ) { focused ->
        LaunchedEffect(focused) { if (!focused) pending = current }
        val changing = pending != current
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    localizedStringResource(StringsR.string.settings_app_language),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = cardTitleColor(),
                )
                Text(
                    if (changing) {
                        if (pending == AppLanguage.EN) "OK to switch to English: the app will restart." else "OK para cambiar a español: la app se reiniciará."
                    } else {
                        localizedStringResource(StringsR.string.settings_menus_titles_and_synopses_and_preferred_audio)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (changing) Amber else Muted,
                )
            }
            Spacer(Modifier.width(24.dp))
            Row(
                Modifier
                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(50))
                    .padding(4.dp),
            ) {
                AppLanguage.entries.forEach { SegmentOption(it.label, selected = it == pending) }
            }
        }
    }
}

@Composable
private fun qualityLabel(quality: Quality): String = if (quality == Quality.UNKNOWN) {
    localizedStringResource(StringsR.string.settings_unknown_quality)
} else {
    quality.label
}

@Composable
private fun acceptedSummary(acceptedLabels: List<String>, acceptsAll: Boolean): String = when {
    acceptedLabels.isEmpty() -> localizedStringResource(StringsR.string.settings_accepts_none)
    acceptsAll -> localizedStringResource(StringsR.string.settings_accepts_all)
    else -> localizedStringResource(StringsR.string.settings_accepts_values, acceptedLabels.joinToString(" · "))
}

@Composable
private fun SourceExclusionGroupCard(title: String, summary: String, expanded: Boolean, onClick: () -> Unit) {
    FocusCard(onClick = onClick) { focused ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = cardTitleColor(),
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(24.dp))
            Icon(
                if (expanded) AppIcons.ExpandMore else AppIcons.ChevronRight,
                contentDescription = null,
                tint = if (focused) FocusStyle.Content else Muted,
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

@Composable
private fun SourceExclusionCard(label: String, excluded: Boolean, onClick: () -> Unit) {
    FocusCard(onClick = onClick) { _ ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = cardTitleColor(),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(24.dp))
            SegmentOption(
                localizedStringResource(
                    if (excluded) StringsR.string.settings_excluded else StringsR.string.settings_shown,
                ),
                selected = excluded,
            )
        }
    }
}

@Composable
private fun ProviderCard(provider: SourceProvider, enabled: Boolean, onClick: () -> Unit) {
    FocusCard(onClick = onClick) { _ ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(localizedStringResource(provider.sectionResource), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = cardTitleColor())
                Text(providerNote(provider), style = MaterialTheme.typography.bodyMedium, color = Muted, maxLines = summaryMaxLines(), overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(24.dp))
            SegmentOption(if (enabled) localizedStringResource(StringsR.string.settings_on_5f22c1) else localizedStringResource(StringsR.string.settings_off_25e189), selected = enabled)
        }
    }
}

@Composable
private fun providerNote(p: SourceProvider): String = when (p) {
    SourceProvider.CASTELLANO -> localizedStringResource(StringsR.string.settings_castellano_finder)
    SourceProvider.TORRENTIO -> localizedStringResource(StringsR.string.settings_the_main_finder_like_in_stremio)
    SourceProvider.PEERFLIX -> localizedStringResource(StringsR.string.settings_spanish_torrents_from_spanish_sites_dontorrent_mejortorrent)
    SourceProvider.KNABEN -> localizedStringResource(StringsR.string.settings_bambi_s_own_finders_together_the_pirate)
    SourceProvider.THEPIRATEBAY -> localizedStringResource(StringsR.string.settings_bambi_s_own_finders_together_the_pirate)
    SourceProvider.NYAA -> localizedStringResource(StringsR.string.settings_own_anime_finder_cocoscrapers_real_debrid_only)
    SourceProvider.COMET -> localizedStringResource(StringsR.string.settings_comet_with_your_real_debrid_token_sent)
    SourceProvider.AIOSTREAMS -> localizedStringResource(StringsR.string.settings_any_stremio_addon_aiostreams_comet_mediafusion_paste)
}

@Composable
private fun SegmentOption(text: String, selected: Boolean) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        // La opción elegida, en verde (como el estado en Pepino IPTV), no en blanco.
        color = if (selected) AppColors.Accent else AppColors.TextSecondary,
        modifier = Modifier
            .background(if (selected) AppColors.Accent.copy(alpha = 0.18f) else Color.Transparent, RoundedCornerShape(50))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun BufferCard(seconds: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    FocusCard(
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (!enabled || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionLeft -> {
                    if (seconds > MIN_BUFFER) onChange(-10)
                    true
                }
                Key.DirectionRight -> {
                    if (seconds < MAX_BUFFER) onChange(10)
                    true
                }
                else -> false
            }
        },
    ) { focused ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    localizedStringResource(StringsR.string.settings_player_buffer),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = cardTitleColor(),
                )
                Text(
                    when {
                        !enabled -> localizedStringResource(StringsR.string.settings_chosen_automatically_from_the_available_memory)
                        seconds <= 40 -> localizedStringResource(StringsR.string.settings_lightweight_5_s_back_buffer_up_to)
                        else -> localizedStringResource(StringsR.string.settings_seconds_of_video_buffered_ahead_use_to)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                    maxLines = summaryMaxLines(),
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(24.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Arrow("◀", enabled = enabled && focused && seconds > MIN_BUFFER)
                    Text(
                        "$seconds s",
                        fontFamily = DisplayFont,
                        fontSize = 30.sp, lineHeight = 35.sp,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 18.dp),
                    )
                    Arrow("▶", enabled = enabled && focused && seconds < MAX_BUFFER)
                }
                Spacer(Modifier.height(6.dp))
                if (enabled) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        (MIN_BUFFER..MAX_BUFFER step 10).forEach { step ->
                            Box(
                                Modifier
                                    .width(22.dp)
                                    .height(4.dp)
                                    .background(
                                        if (step <= seconds) Color.White else Color.White.copy(alpha = 0.18f),
                                        RoundedCornerShape(2.dp),
                                    ),
                            )
                        }
                    }
                } else {
                    Text(localizedStringResource(StringsR.string.settings_automatic_8e7638), style = MaterialTheme.typography.bodySmall, color = Muted)
                }
            }
        }
    }
}

@Composable
private fun Arrow(symbol: String, enabled: Boolean) {
    // Pepino+: icono vectorial en lugar del carácter ◀ ▶ (se ve igual en todas las teles).
    Icon(
        if (symbol == "◀") AppIcons.ChevronLeft else AppIcons.ChevronRight,
        contentDescription = null,
        tint = if (enabled) Color.White else Color.White.copy(alpha = 0.2f),
        modifier = Modifier.size(28.dp),
    )
}

@Composable
private fun AboutCard() {
    val context = LocalContext.current
    val version = remember {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"
    }
    val testing = context.packageName.endsWith(".debug")
    FocusCard { _ ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (testing) localizedStringResource(StringsR.string.settings_bambi_testing) else "Pepino+",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = cardTitleColor(),
                )
                Text(
                    localizedStringResource(StringsR.string.settings_catalog_by_tmdb_sources_from_torrentio_playback),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                    maxLines = summaryMaxLines(),
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(localizedStringResource(StringsR.string.settings_version, version), style = MaterialTheme.typography.titleMedium, color = Muted)
        }
    }
}

@Composable
private fun KeyEditorDialog(
    editor: KeyEditor,
    onSave: (String) -> Unit,
    onRemove: () -> Unit,
    onClose: () -> Unit,
    onAllDebridConnected: () -> Unit,
) {
    var input by rememberSaveable(editor.service) { mutableStateOf("") }
    val fieldRequester = remember { FocusRequester() }
    val saveRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var fieldFocused by remember { mutableStateOf(false) }
    LaunchedEffect(editor.service) {
        withFrameNanos { }
        runCatching { fieldRequester.requestFocus() }
    }
    LaunchedEffect(editor.busy, editor.error) {
        if (!editor.busy && editor.error != null) {
            withFrameNanos { }
            runCatching { saveRequester.requestFocus() }
        }
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .onBackKey(enabled = true) { onClose() },
    ) {
        Column(
            Modifier
                .width(if (editor.service == ApiService.ALLDEBRID) 1040.dp else 720.dp)
                .background(AppColors.Surface, RoundedCornerShape(20.dp))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
                .padding(36.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ServiceLogo(editor.service)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        localizedStringResource(editor.service.titleResource),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                    )
                    Text(
                        if (editor.service == ApiService.ALLDEBRID) {
                            localizedStringResource(StringsR.string.settings_alldebrid_qr_or_manual)
                        } else {
                            localizedStringResource(
                                StringsR.string.settings_get_key_at,
                                localizedStringResource(editor.service.keyNameResource),
                                localizedStringResource(editor.service.whereToGetResource),
                            )
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Muted,
                        maxLines = summaryMaxLines(),
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
            BasicTextField(
                value = input,
                onValueChange = { input = it.replace("\n", "") },
                singleLine = true,
                enabled = !editor.busy,
                textStyle = TextStyle(color = Color.White, fontSize = 20.sp, fontFamily = FontFamily.Monospace),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = {
                    keyboard?.hide()
                    onSave(input)
                }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(fieldRequester)
                    .onFocusChanged { fieldFocused = it.isFocused }
                    .onPreviewKeyEvent { event ->
                        when (event.key) {
                            Key.DirectionCenter, Key.Enter -> {
                                if (event.type == KeyEventType.KeyUp) keyboard?.show()
                                true
                            }
                            Key.DirectionDown -> {
                                if (event.type == KeyEventType.KeyDown) runCatching { saveRequester.requestFocus() }
                                true
                            }
                            else -> false
                        }
                    },
                decorationBox = { field ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(AppColors.Field, RoundedCornerShape(10.dp))
                            .border(
                                width = if (fieldFocused) 2.dp else 1.dp,
                                color = if (fieldFocused) FocusStyle.Frame else Color.White.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(10.dp),
                            )
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                    ) {
                        if (input.isEmpty()) {
                            Text(
                                if (fieldFocused) localizedStringResource(StringsR.string.settings_press_ok_to_type_the_new_key) else localizedStringResource(StringsR.string.settings_new_key),
                                fontSize = 18.sp,
                                color = Color.White.copy(alpha = 0.35f),
                            )
                        }
                        field()
                    }
                },
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = when {
                    editor.busy -> localizedStringResource(StringsR.string.settings_checking_with_service, localizedStringResource(editor.service.titleResource))
                    editor.error != null -> editor.error
                    else -> localizedStringResource(StringsR.string.settings_key_checked_before_saving, localizedStringResource(editor.service.titleResource))
                },
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    editor.busy -> Muted
                    editor.error != null -> Red
                    else -> Muted
                },
            )
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton(
                    text = localizedStringResource(StringsR.string.settings_check_and_save),
                    onClick = { onSave(input) },
                    enabled = !editor.busy,
                    modifier = Modifier.focusRequester(saveRequester),
                )
                if (!editor.service.required && editor.hasKey) {
                    TvButton(text = localizedStringResource(StringsR.string.settings_remove_key), onClick = onRemove, enabled = !editor.busy)
                }
                TvButton(text = localizedStringResource(StringsR.string.settings_cancel), onClick = onClose, enabled = !editor.busy)
            }
                }
                if (editor.service == ApiService.ALLDEBRID) {
                    Spacer(Modifier.width(24.dp))
                    AllDebridQrPanel(onConnected = onAllDebridConnected)
                }
            }
        }
    }
}

private const val MIN_BUFFER = 20
private const val MAX_BUFFER = 100

@HiltViewModel
class DownloadStorageViewModel @Inject constructor(
    private val downloads: DownloadsRepository,
) : ViewModel() {
    private val _options = MutableStateFlow(downloads.storageOptions())
    val options: StateFlow<List<StorageOption>> = _options.asStateFlow()
    val selected: StateFlow<String?> = downloads.storagePath

    /** Vuelve a mirar qué discos hay (por si se acaba de enchufar o quitar un USB). */
    fun refresh() {
        _options.value = downloads.storageOptions()
    }

    fun shift(delta: Int) {
        refresh()
        val options = _options.value
        if (options.isEmpty()) return
        val index = options.indexOfFirst { it.path == selected.value }.coerceAtLeast(0)
        downloads.selectStorage(options[((index + delta) % options.size + options.size) % options.size].path)
    }
}

/** Ajustes → Carpeta de descargas: memoria interna o un USB/tarjeta, con el espacio libre de cada uno. */
@Composable
internal fun DownloadStorageCard(viewModel: DownloadStorageViewModel = hiltViewModel()) {
    val options by viewModel.options.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }
    val current = options.firstOrNull { it.path == selected } ?: options.firstOrNull()
    FocusCard(
        onClick = { viewModel.shift(1) },
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionLeft -> { viewModel.shift(-1); true }
                Key.DirectionRight -> { viewModel.shift(1); true }
                else -> false
            }
        },
    ) { _ ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    localizedStringResource(StringsR.string.downloads_storage),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = cardTitleColor(),
                )
                Text(
                    localizedStringResource(StringsR.string.downloads_storage_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                    maxLines = summaryMaxLines(),
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    localizedStringResource(StringsR.string.downloads_storage_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted.copy(alpha = 0.8f),
                )
            }
            Spacer(Modifier.width(24.dp))
            Column(horizontalAlignment = Alignment.End) {
                ValueSelector(current?.label.orEmpty(), showArrows = options.size > 1)
                current?.let {
                    Text(
                        localizedStringResource(StringsR.string.downloads_storage_free, storageGb(it.freeBytes), storageGb(it.totalBytes)),
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

private fun storageGb(bytes: Long): String = String.format(Locale.getDefault(), "%.1f GB", bytes / 1_073_741_824.0)

/** Ajustes → Al pulsar Reproducir: calidad primero, castellano primero o solo castellano (del perfil activo). */
@Composable
private fun PlaybackPriorityCard(viewModel: SettingsViewModel = hiltViewModel()) {
    val priority by viewModel.playbackPriority.collectAsStateWithLifecycle()
    val all = com.shieldstream.tv.model.PlaybackPriority.entries
    CyclingSettingCard(
        title = localizedStringResource(StringsR.string.settings_play_priority),
        hint = localizedStringResource(StringsR.string.settings_play_priority_hint),
        value = localizedStringResource(
            when (priority) {
                com.shieldstream.tv.model.PlaybackPriority.QUALITY_FIRST -> StringsR.string.settings_play_priority_quality
                com.shieldstream.tv.model.PlaybackPriority.SPANISH_FIRST -> StringsR.string.settings_play_priority_spanish
                com.shieldstream.tv.model.PlaybackPriority.SPANISH_ONLY -> StringsR.string.settings_play_priority_spanish_only
            },
        ),
        onShift = { d -> viewModel.setPlaybackPriority(all[((all.indexOf(priority) + d) % all.size + all.size) % all.size]) },
    )
}

/** Ajustes → Calidad preferida: la mejor que no pase de este tope va primero. */
@Composable
private fun PreferredQualityCard(viewModel: SettingsViewModel = hiltViewModel()) {
    val quality by viewModel.preferredQuality.collectAsStateWithLifecycle()
    val all = com.shieldstream.tv.model.PreferredQuality.entries
    CyclingSettingCard(
        title = localizedStringResource(StringsR.string.settings_preferred_quality),
        hint = localizedStringResource(StringsR.string.settings_preferred_quality_hint),
        value = localizedStringResource(
            when (quality) {
                com.shieldstream.tv.model.PreferredQuality.DV_4K -> StringsR.string.settings_quality_dv_4k
                com.shieldstream.tv.model.PreferredQuality.HDR_4K -> StringsR.string.settings_quality_hdr_4k
                com.shieldstream.tv.model.PreferredQuality.UHD_SDR -> StringsR.string.settings_quality_4k_sdr
                com.shieldstream.tv.model.PreferredQuality.FHD -> StringsR.string.settings_quality_1080
                com.shieldstream.tv.model.PreferredQuality.HD -> StringsR.string.settings_quality_720
            },
        ),
        onShift = { d -> viewModel.setPreferredQuality(all[((all.indexOf(quality) + d) % all.size + all.size) % all.size]) },
    )
}

@Composable
private fun CyclingSettingCard(title: String, hint: String, value: String, onShift: (Int) -> Unit) {
    FocusCard(
        onClick = { onShift(1) },
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionLeft -> { onShift(-1); true }
                Key.DirectionRight -> { onShift(1); true }
                else -> false
            }
        },
    ) { _ ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = cardTitleColor())
                Text(hint, style = MaterialTheme.typography.bodyMedium, color = Muted, maxLines = summaryMaxLines(), overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(24.dp))
            ValueSelector(value)
        }
    }
}
