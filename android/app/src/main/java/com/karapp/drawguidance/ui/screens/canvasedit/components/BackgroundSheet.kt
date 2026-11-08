package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.OpenWith
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import com.karapp.drawguidance.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.content.paper.PaperUsage
import com.karapp.drawguidance.data.model.BackgroundSpec
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.ui.common.CanvasBackgroundSwatches
import com.karapp.drawguidance.ui.common.hexToColor
import com.karapp.drawguidance.ui.components.BackgroundPicker
import com.karapp.drawguidance.ui.components.NeuSectionTitle
import com.karapp.drawguidance.ui.theme.NeuTheme
import com.karapp.drawguidance.ui.components.NeuSheet

/** v41 arka planın türleri (gradyan, desen, resim). */
internal enum class BackgroundPart { GRADIENT, PATTERN, IMAGE }

/**
 * v41 arka plan penceresinin taslağı. Pencere açılınca tuvalin arka planıyla başlar; bu pencerede yeni bir tür seçilince
 * (gradyan, desen ya da resim) eski arka planın bu pencerede seçilmeyen türleri taslaktan çıkar; böylece **Uygula** eski
 * arka planı kaldırıp yenisini koyar. Aynı pencerede birden çok tür seçilirse hepsi kalır (ör. resim ve üstünde desen).
 * Desen rengi, ölçek, örtücülük ya da resmin yerleşimi gibi ayarlar tür seçmek sayılmaz. Yeni resim seçilince eski
 * resmin taşıma/boyutlandırması sıfırlanır.
 */
internal data class BackgroundDraft(val spec: BackgroundSpec, val chosen: Set<BackgroundPart> = emptySet()) {
    fun pick(picked: BackgroundSpec): BackgroundDraft {
        val fresh = buildSet {
            if (picked.gradient != null && picked.gradient != spec.gradient) add(BackgroundPart.GRADIENT)
            if (picked.patternId != null && picked.patternId != spec.patternId) add(BackgroundPart.PATTERN)
            if (picked.imageId != null && picked.imageId != spec.imageId) add(BackgroundPart.IMAGE)
        }
        val all = chosen + fresh
        var next = picked
        if (BackgroundPart.IMAGE in fresh) next = next.copy(imageOffsetX = 0f, imageOffsetY = 0f, imageScale = 1f)
        if (all.isNotEmpty()) {
            if (BackgroundPart.GRADIENT !in all) next = next.copy(gradient = null)
            if (BackgroundPart.PATTERN !in all) next = next.copy(patternId = null)
            if (BackgroundPart.IMAGE !in all) next = next.copy(imageId = null, imageOffsetX = 0f, imageOffsetY = 0f, imageScale = 1f)
        }
        return BackgroundDraft(next, all)
    }
}

/**
 * v23 çizim ekranında arka plan penceresi: kağıt rengi, gradyan, desen (rengi/ölçeği/örtücülüğü) ve resim (hazır ya da
 * galeriden). v41: seçimler önce taslakta toplanır, **Uygula** ile tuvale konur (eski arka plan kaldırılır; tek geri alma
 * adımı); pencere uygulamadan kapanırsa tuval değişmez. Not tuvalinde birden çok sayfa varken "Tüm sayfalara uygula".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackgroundSheet(
    kind: CanvasKind,
    paperHex: String,
    spec: BackgroundSpec,
    /** Taslağın uygulanması: kağıt rengi, arka plan, bütün sayfalara mı. */
    onApply: (paperHex: String, spec: BackgroundSpec, allPages: Boolean) -> Unit,
    /** Galeriden seçilen resmi tuvalin varlıklarına ekler (arka plana koymaz); hazır olunca kimliğiyle döner. */
    onGallery: (android.net.Uri, (String) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    /** v24 not tuvalinde birden çok sayfa varken: "Bu sayfa (2/5)" bilgisi ve tüm sayfalara uygulama. */
    pageLabel: String? = null,
    allPages: Boolean = false,
    /** v40 arka plan resmini tuvalde taşıma/boyutlandırma (resim varken, not alma dışında; null: düğme yok). */
    onTransform: (() -> Unit)? = null,
) {
    val colors = NeuTheme.colors
    var draftPaper by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(paperHex) }
    var draftState by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(BackgroundDraft(spec)) }
    val draft = draftState.spec
    val dirty = draft != spec || !draftPaper.equals(paperHex, ignoreCase = true)
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onGallery(uri) { assetId ->
            draftState = draftState.pick(draftState.spec.copy(imageId = BackgroundSpec.ASSET + assetId, imageFit = com.karapp.drawguidance.data.model.BoxFit.COVER))
        }
    }
    NeuSheet(onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding()
                .padding(bottom = 20.dp).testTag("background_sheet"),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.bg_title), style = MaterialTheme.typography.titleLarge)
            // v41 Uygula: taslak tuvale konur, eski arka plan kalkar
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    pageLabel ?: stringResource(if (dirty) R.string.v41_bg_pending else R.string.v41_bg_hint),
                    Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted,
                )
                com.karapp.drawguidance.ui.components.NeuButton(
                    stringResource(R.string.v41_bg_apply), { onApply(draftPaper, draft, false); onDismiss() },
                    modifier = Modifier.testTag("bg_apply"), primary = true, enabled = dirty,
                    icon = androidx.compose.material.icons.Icons.Filled.Check,
                )
            }
            if (allPages) {
                com.karapp.drawguidance.ui.components.NeuButton(
                    stringResource(R.string.bg_apply_all), { onApply(draftPaper, draft, true); onDismiss() },
                    modifier = Modifier.fillMaxWidth().testTag("bg_apply_all"),
                )
            }
            if (onTransform != null && !dirty) {
                com.karapp.drawguidance.ui.components.NeuButton(
                    stringResource(R.string.bg_transform), onTransform, modifier = Modifier.testTag("bg_transform_open"),
                    icon = androidx.compose.material.icons.Icons.Filled.OpenWith,
                )
            }
            NeuSectionTitle(stringResource(R.string.bg_color))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                CanvasBackgroundSwatches.forEach { hex ->
                    val chosen = hex.equals(draftPaper, ignoreCase = true)
                    Box(
                        Modifier.size(36.dp).clip(CircleShape)
                            .border(if (chosen) 3.dp else 1.dp, if (chosen) colors.accent else colors.contentMuted.copy(alpha = 0.4f), CircleShape)
                            .padding(4.dp).background(hexToColor(hex), CircleShape)
                            .clickable { draftPaper = hex }
                            .semantics { contentDescription = hex; if (chosen) selected = true },
                    )
                }
            }
            BackgroundPicker(
                usage = if (kind.isNotes) PaperUsage.NOTE else PaperUsage.ART,
                paperHex = draftPaper,
                spec = draft,
                onChange = { draftState = draftState.pick(it) },
                onGallery = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                // v42 çizgi romanda resim yok: sayfa resimleri "Mekân arka planı" penceresinde
                showImages = kind != CanvasKind.COMIC,
            )
        }
    }
}
