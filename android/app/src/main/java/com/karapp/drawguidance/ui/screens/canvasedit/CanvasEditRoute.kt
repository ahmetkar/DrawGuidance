package com.karapp.drawguidance.ui.screens.canvasedit

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.unit.dp
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
) {
    val vm: CanvasEditViewModel = koinViewModel(parameters = { parametersOf(canvasLocalId) })
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
            onBack = {
                context.findActivity()?.let { ads.onCanvasClosed(it, canvas?.kind) }
                onBack()
            },
            viewModel = vm,
            onOpenModel3d = onOpenModel3d,
        )
    }
    }
    }
    // v51 canlı yayın: bu tuval yayındaysa düzenleyicideki her değişiklik karşı cihaza gönderilir
    LiveBroadcastBridge(canvasLocalId, vm)
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.LiveBroadcastBridge(canvasLocalId: String, vm: CanvasEditViewModel) {
    val share = koinInject<com.karapp.drawguidance.data.share.ShareManager>()
    val liveOut by share.liveOut.collectAsState()
    val live = liveOut?.takeIf { it.canvasId == canvasLocalId } ?: return
    androidx.compose.runtime.LaunchedEffect(canvasLocalId, live.peerKey) {
        vm.canvas.collect { c -> if (c != null) share.publishLive(c) }
    }
    val canvas by vm.canvas.collectAsState()
    com.karapp.drawguidance.ui.screens.share.LiveBroadcastBadge(
        canvasName = canvas?.name.orEmpty(),
        peerName = live.peerName,
        onStop = share::stopLive,
        modifier = androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
            .navigationBarsPadding().padding(bottom = 6.dp),
    )
}
