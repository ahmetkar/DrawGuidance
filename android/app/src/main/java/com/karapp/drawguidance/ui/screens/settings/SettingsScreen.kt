package com.karapp.drawguidance.ui.screens.settings

import androidx.compose.material.icons.filled.Edit
import android.content.Intent
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.karapp.drawguidance.data.settings.ExportSettings
import com.karapp.drawguidance.data.settings.ExportSettingsStore
import com.karapp.drawguidance.data.settings.ImageFormat
import com.karapp.drawguidance.ui.components.NeuChip
import com.karapp.drawguidance.ui.components.scaleLabel
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import com.karapp.drawguidance.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.ui.components.NeuButton
import com.karapp.drawguidance.ui.components.NeuIconButton
import com.karapp.drawguidance.ui.components.NeuSurface
import com.karapp.drawguidance.ui.components.NeuTextField
import com.karapp.drawguidance.ui.components.NeuTopBar
import com.karapp.drawguidance.ui.theme.NeuTheme
import org.koin.androidx.compose.koinViewModel

/** Resim kaydetme ayarları: kaydedilecek dizin, varsayılan çözünürlük ve biçim. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExportSettingsCard(viewModel: SettingsViewModel) {
    val colors = NeuTheme.colors
    val context = LocalContext.current
    val export by viewModel.export.collectAsState()
    var album by remember { mutableStateOf(export.albumName) }
    var albumSaved by remember { mutableStateOf(false) }
    var folderError by remember { mutableStateOf(false) }
    LaunchedEffect(export.albumName) { album = export.albumName }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val ok = runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
                val label = ExportSettingsStore.folderLabelOf(DocumentsContract.getTreeDocumentId(uri))
                viewModel.setExportFolder(uri.toString(), label)
            }.isSuccess
            folderError = !ok
        }
    }
    NeuSurface(modifier = Modifier.fillMaxWidth().testTag("export_settings_card"), elevation = 7.dp) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SettingsCardHeader(Icons.Filled.PhotoLibrary, stringResource(R.string.export_section_title))
            Text(stringResource(R.string.export_section_description), style = MaterialTheme.typography.bodyMedium, color = colors.contentMuted)

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.export_location_label), style = MaterialTheme.typography.labelMedium, color = colors.contentMuted)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Folder, null, tint = colors.content, modifier = Modifier.size(18.dp))
                    Text(export.locationLabel, style = MaterialTheme.typography.bodyLarge, color = colors.content, modifier = Modifier.testTag("export_location"))
                }
                if (folderError) Text(stringResource(R.string.export_folder_failed), style = MaterialTheme.typography.bodySmall, color = colors.danger)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                NeuButton(
                    stringResource(R.string.export_pick_folder),
                    onClick = { folderError = false; runCatching { pickFolder.launch(null) }.onFailure { folderError = true } },
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.FolderOpen,
                )
                NeuButton(
                    stringResource(R.string.export_use_gallery),
                    onClick = { folderError = false; viewModel.useGallery() },
                    modifier = Modifier.weight(1f),
                    enabled = export.folderUri != null,
                    icon = Icons.Filled.PhotoLibrary,
                )
            }
            if (export.folderUri == null) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    NeuTextField(
                        value = album,
                        onValueChange = { album = it.take(40); albumSaved = false },
                        label = stringResource(R.string.export_album_label),
                        supportingText = if (albumSaved) stringResource(R.string.export_album_saved) else null,
                        modifier = Modifier.weight(1f),
                    )
                    NeuButton(
                        stringResource(R.string.save_button),
                        onClick = { viewModel.setAlbumName(album); albumSaved = true },
                        enabled = album.trim() != export.albumName,
                        primary = true,
                    )
                }
            }

            Text(stringResource(R.string.export_default_resolution), style = MaterialTheme.typography.labelMedium, color = colors.contentMuted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExportSettings.Scales.forEach { scale ->
                    NeuChip(scaleLabel(scale), selected = export.defaultScale == scale, onClick = { viewModel.setDefaultScale(scale) })
                }
            }
            Text(stringResource(R.string.export_default_format), style = MaterialTheme.typography.labelMedium, color = colors.contentMuted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ImageFormat.entries.forEach { format ->
                    NeuChip(format.label, selected = export.defaultFormat == format, onClick = { viewModel.setDefaultFormat(format) })
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    bottomBar: @Composable () -> Unit = {},
    viewModel: SettingsViewModel = koinViewModel(),
    /** v33 hesap ayarları sayfası. */
    onOpenAccount: () -> Unit = {},
    accountViewModel: com.karapp.drawguidance.ui.screens.account.AccountViewModel = koinViewModel(),
    /** v39 abonelik sayfası. */
    onOpenSubscription: () -> Unit = {},
) {
    val colors = NeuTheme.colors
    // v36 ayarlar sekmelere bölünür; hesap başlığı en üstte, ekran genişliğinde ve beyaz zeminde
    var tab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(SettingsTab.GENERAL) }
    Scaffold(
        containerColor = colors.background,
        bottomBar = bottomBar,
        topBar = {
            NeuTopBar(
                title = stringResource(R.string.drawer_settings),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            com.karapp.drawguidance.ui.screens.account.AccountHeader(accountViewModel, onOpenAccount)
            // v39 abonelik satırı (hesap başlığının altında); v43 yalnız giriş yapılmışken
            val localUser by accountViewModel.user.collectAsState()
            if (localUser?.signedIn == true && !com.karapp.drawguidance.ui.kids.LocalKidsLocked.current) com.karapp.drawguidance.ui.screens.subscription.SubscriptionSettingsRow(onOpenSubscription)
            SettingsTabBar(tab, { tab = it; com.karapp.drawguidance.diagnostics.AppErrors.breadcrumb("ayarlar sekmesi: ${it.name}") })
            // Sekme değişince sayfa baştan görünür
            val scroll = remember(tab) { androidx.compose.foundation.ScrollState(0) }
            Column(
                // Geniş ekranlarda ayar kartları okunaklı bir genişlikte ortalanır
                modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 24.dp)
                    .wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 640.dp).testTag("settings_tab_content"),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                when (tab) {
                    SettingsTab.GENERAL -> {
                        // v44 çocuk kilidi (yaştan bağımsız açılıp kapanır; ebeveyn şifresiyle)
                        com.karapp.drawguidance.ui.kids.KidsLockSettingsCard()
                        // v34 uygulamanın dili (veritabanında saklanır; ilk açılışta sistemden algılanır)
                        LanguageSettingsCard()
                        // v36 tasarımın renk grubu
                        ThemePaletteCard()
                        // v40 bildirimler (güncelleme, hatırlatma; Android 13+ izni)
                        NotificationSettingsCard()
                        // v33 giriş yapılmışsa çıkış
                        com.karapp.drawguidance.ui.screens.account.SignOutButton(accountViewModel)
                    }
                    SettingsTab.DRAWING -> {
                        EditorSettingsCard(viewModel)
                        StylusSettingsCard()
                    }
                    SettingsTab.SAVING -> {
                        ExportSettingsCard(viewModel)
                        // v37 depolama kullanımı ve önbellek
                        StorageCard()
                    }
                    SettingsTab.CONNECTION -> {
                        // v39 sunucu eşitleme ve eşitleme ayarları tek kartta
                        val online by accountViewModel.online.collectAsState()
                        val lastRun by accountViewModel.lastSync.collectAsState()
                        SyncControlCard(lastSync = lastRun ?: accountViewModel.catalogLastCheck)
                    }
                    SettingsTab.ADVANCED -> {
                        // v37 hata raporları
                        ErrorReportsCard()
                        // v40 mod başına geri bildirim
                        FeedbackCard()
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorSettingsCard(viewModel: SettingsViewModel) {
    val colors = NeuTheme.colors
    val autosave by viewModel.autosave.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val textSuggest by viewModel.textSuggest.collectAsState()
    val shapeSuggest by viewModel.shapeSuggest.collectAsState()
    val lineSuggest by viewModel.lineSuggest.collectAsState()
    val selectionSuggest by viewModel.selectionSuggest.collectAsState()
    NeuSurface(modifier = Modifier.fillMaxWidth().testTag("editor_settings_card"), elevation = 7.dp) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SettingsCardHeader(Icons.Filled.Draw, stringResource(R.string.editor_settings_title))
            listOf(
                Triple(R.string.autosave, R.string.autosave_hint, autosave to viewModel::setAutosave),
                Triple(R.string.suggestions_setting, R.string.suggestions_setting_hint, suggestions to viewModel::setSuggestions),
            ) + (if (suggestions) listOf(
                // v23 yazı ve şekil önerisi ayrı algoritmalar: ayrı ayrı açılıp kapatılır
                Triple(R.string.text_suggest_setting, R.string.text_suggest_setting_hint, textSuggest to viewModel::setTextSuggest),
                Triple(R.string.shape_suggest_setting, R.string.shape_suggest_setting_hint, shapeSuggest to viewModel::setShapeSuggest),
                // v38 çizgi önerisi: üçüncü ayrı tür
                Triple(R.string.line_suggest_setting, R.string.line_suggest_setting_hint, lineSuggest to viewModel::setLineSuggest),
                // v27 seçimde kendiliğinden öneri
                Triple(R.string.selection_suggest_setting, R.string.selection_suggest_setting_hint, selectionSuggest to viewModel::setSelectionSuggest),
            ) else emptyList()).forEachIndexed { i, (title, hint, state) ->
                val (checked, onChange) = state
                SettingSwitchRow(stringResource(title), stringResource(hint), checked, onChange, divider = i > 0)
            }
            // v32 eğitim modu: her moda ilk girişteki eğitim önerisi yeniden gösterilir
            var tutorialsReset by remember { mutableStateOf(false) }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                com.karapp.drawguidance.ui.components.NeuButton(
                    stringResource(R.string.tut_settings_reset), { tutorialsReset = viewModel.resetTutorials() },
                    Modifier.fillMaxWidth().testTag("tutorials_reset"), icon = Icons.Filled.School,
                )
                Text(
                    stringResource(if (tutorialsReset) R.string.tut_settings_reset_done else R.string.tut_settings_reset_hint),
                    style = MaterialTheme.typography.bodySmall, color = if (tutorialsReset) colors.accent else colors.contentMuted,
                )
            }
        }
    }
}

/**
 * v18 tablet kalemi: algılama durumu (kalem destekli ekran, Bluetooth kalem, kullanılıyor) ve kalem ayarları
 * (parmak kaydırır, basınç, eğim, yan tuş, hareket tahmini, düşük gecikme).
 */
@Composable
private fun StylusSettingsCard() {
    val colors = NeuTheme.colors
    val stylus = com.karapp.drawguidance.platform.Stylus
    val state by stylus.state.collectAsState()
    val fingerPans by stylus.fingerPans.collectAsState()
    val pressure by stylus.pressure.collectAsState()
    val tilt by stylus.tilt.collectAsState()
    val sideButton by stylus.sideButtonErases.collectAsState()
    val prediction by stylus.prediction.collectAsState()
    val lowLatency by stylus.lowLatency.collectAsState()
    NeuSurface(modifier = Modifier.fillMaxWidth().testTag("stylus_settings_card"), elevation = 7.dp) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SettingsCardHeader(Icons.Filled.Edit, stringResource(R.string.stylus_title))
            val status = when {
                state.used -> stringResource(R.string.stylus_status_used)
                state.attached && state.bluetooth -> stringResource(R.string.stylus_status_bluetooth, state.name ?: "")
                state.attached -> stringResource(R.string.stylus_status_attached, state.name ?: "")
                else -> stringResource(R.string.stylus_status_none)
            }
            Text(status, style = MaterialTheme.typography.bodyMedium, color = if (state.used || state.attached) colors.accent else colors.contentMuted, modifier = Modifier.testTag("stylus_status"))
            // v21 kalem tanıma: uygulamanın kalemi tanıyıp tanımadığı ve elle ayarlanması
            val recognition by stylus.recognition.collectAsState()
            val recognized = stylus.isRecognized(state, recognition)
            Text(
                stringResource(if (recognized) R.string.stylus_recognized else R.string.stylus_not_recognized),
                style = MaterialTheme.typography.titleSmall,
                color = if (recognized) colors.accent else colors.contentMuted,
                modifier = Modifier.testTag("stylus_recognized"),
            )
            Text(stringResource(R.string.stylus_recognition_title), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    com.karapp.drawguidance.platform.Stylus.Recognition.AUTO to R.string.stylus_recognition_auto,
                    com.karapp.drawguidance.platform.Stylus.Recognition.ALWAYS to R.string.stylus_recognition_always,
                    com.karapp.drawguidance.platform.Stylus.Recognition.OFF to R.string.stylus_recognition_off,
                ).forEach { (mode, label) ->
                    com.karapp.drawguidance.ui.components.NeuChip(
                        stringResource(label), recognition == mode, { stylus.setRecognition(mode) },
                        Modifier.testTag("stylus_recognition_${mode.name.lowercase()}"),
                    )
                }
            }
            Text(
                stringResource(
                    when (recognition) {
                        com.karapp.drawguidance.platform.Stylus.Recognition.AUTO -> R.string.stylus_recognition_auto_hint
                        com.karapp.drawguidance.platform.Stylus.Recognition.ALWAYS -> R.string.stylus_recognition_always_hint
                        com.karapp.drawguidance.platform.Stylus.Recognition.OFF -> R.string.stylus_recognition_off_hint
                    },
                ),
                style = MaterialTheme.typography.bodySmall, color = colors.contentMuted,
            )
            // Kalem ayarları yalnız kalem tanındığında (tanınmıyorsa etkisizdir, gizlenir)
            if (!recognized) return@Column
            val supported = stylus.lowLatencySupported
            listOf(
                Triple(R.string.stylus_finger_pans, R.string.stylus_finger_pans_hint, stylus.fingerPans to fingerPans),
                Triple(R.string.stylus_pressure, R.string.stylus_pressure_hint, stylus.pressure to pressure),
                Triple(R.string.stylus_tilt, R.string.stylus_tilt_hint, stylus.tilt to tilt),
                Triple(R.string.stylus_side_button, R.string.stylus_side_button_hint, stylus.sideButtonErases to sideButton),
                Triple(R.string.stylus_prediction, R.string.stylus_prediction_hint, stylus.prediction to prediction),
                Triple(R.string.stylus_low_latency, if (supported) R.string.stylus_low_latency_hint else R.string.stylus_low_latency_unsupported, stylus.lowLatency to lowLatency),
            ).forEach { (title, hint, pair) ->
                val (flow, checked) = pair
                val enabled = flow !== stylus.lowLatency || supported
                SettingSwitchRow(stringResource(title), stringResource(hint), checked, { stylus.set(flow, it) }, enabled = enabled)
            }
        }
    }
}
