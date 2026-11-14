package com.karapp.drawguidance.ui.screens.canvasedit

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.karapp.drawguidance.data.ads.AdsCoordinator
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.ui.premium.PremiumScope
import com.karapp.drawguidance.ui.screens.subscription.findActivity
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

/**
 * v39 tuval düzenleyicinin gezinme sarmalayıcısı (CanvasEditScreen yöntem boyutu sınırına yakın, burada):
 * Premium kilitleri tuval türüne göre sağlanır (tür bilinmeden çizim tuvali sayılır: kilitler kapalı kalır) ve
 * tuval kapatılınca geçiş reklamı kurallara göre ([AdsCoordinator]) gösterilir.
 */
@Composable
fun CanvasEditRoute(
    canvasLocalId: String,
    onBack: () -> Unit,
    onOpenModel3d: (modelId: String?, strokeId: String?) -> Unit,
    onOpenSubscription: () -> Unit,
    /** v51 notta resim düzenleme: geçici basit resim tuvali açılır (bu düzenleyicinin üstünde). */
    onOpenCanvas: (String) -> Unit = {},
    /** v51 başka bir tuvale geç (yeni alıştırma yeni tuvalde; bu düzenleyici kapanır). */
    onSwitchCanvas: (localId: String) -> Unit = {},
) {
    val vm: CanvasEditViewModel = koinViewModel(parameters = { parametersOf(canvasLocalId) })
    androidx.compose.runtime.LaunchedEffect(vm) { vm.openCanvas.collect { onSwitchCanvas(it) } }
    val canvas by vm.canvas.collectAsState()
    val kind = canvas?.kind ?: CanvasKind.ART
    // v44 çocuk kilidinde yalnız çocuk tuvalleri açılır
    val kidsLocked = com.karapp.drawguidance.ui.kids.LocalKidsLocked.current
    val loadedKind = canvas?.kind
    if (kidsLocked && loadedKind != null && loadedKind != CanvasKind.KIDS) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onBack() }
        return
    }
    val ads = koinInject<AdsCoordinator>()
    val context = LocalContext.current
    // v51 notta resim düzenleme: geçici tuval açılır (açılamazsa bildirilir)
    val tempRequest by vm.tempCanvasRequest.collectAsState()
    androidx.compose.runtime.LaunchedEffect(tempRequest) {
        val id = tempRequest ?: return@LaunchedEffect
        vm.consumeTempCanvasRequest()
        if (id.isEmpty()) vm.emit(com.karapp.drawguidance.ui.screens.canvasedit.EditEvent.Message(context.getString(com.karapp.drawguidance.R.string.v51_temp_image_failed)))
        else onOpenCanvas(id)
    }
    // v51 geçici resim tuvali: Kaydet'le (resim nota aktarılır) ya da çıkışta (değişiklikler atılır) kapanır; reklam yok
    val temp = vm.isTempImageEdit
    val popped = androidx.compose.runtime.remember { booleanArrayOf(false) }
    val leave: () -> Unit = {
        if (temp) {
            if (!popped[0]) { popped[0] = true; vm.closeTempImage(); onBack() }
        } else {
            context.findActivity()?.let { ads.onCanvasClosed(it, canvas?.kind) }
            onBack()
        }
    }
    if (temp) {
        val closed by vm.tempClosed.collectAsState()
        androidx.compose.runtime.LaunchedEffect(closed) { if (closed) leave() }
        val hint = androidx.compose.ui.res.stringResource(com.karapp.drawguidance.R.string.v51_temp_image_hint)
        androidx.compose.runtime.LaunchedEffect(Unit) { vm.emit(com.karapp.drawguidance.ui.screens.canvasedit.EditEvent.Message(hint)) }
    }
    // v40 tuvalde geçirilen etkin süre (Ana sayfa: en çok vakit geçirdiklerin)
    val timeModifier = rememberEditTimeModifier(canvasLocalId)
    androidx.compose.foundation.layout.Box(timeModifier.then(androidx.compose.ui.Modifier.fillMaxSize())) {
    // v45 tablette düzenleyicinin alt pencereleri sürüklenebilir pencere (telefonda alt sayfa)
    val sheetWindow = com.karapp.drawguidance.ui.screens.canvasedit.components.rememberTabletWindow(com.karapp.drawguidance.ui.screens.canvasedit.components.TabletWindows.SHEET)
    // v50 dikey ekranda alt pencereler çizim alanının altına yerleşir (kalıcı değil)
    val dockedSheets = androidx.compose.runtime.remember { com.karapp.drawguidance.ui.screens.canvasedit.components.DockedSheetHost() }
    androidx.compose.runtime.CompositionLocalProvider(com.karapp.drawguidance.ui.screens.canvasedit.components.LocalDockedSheets provides dockedSheets) {
    com.karapp.drawguidance.ui.screens.canvasedit.components.FloatingSheetsScope(sheetWindow) {
    PremiumScope(kind, onOpenSubscription) {
        CanvasEditScreen(
            canvasLocalId = canvasLocalId,
            onBack = leave,
            viewModel = vm,
            onOpenModel3d = onOpenModel3d,
        )
    }
    }
    }
    }
}
