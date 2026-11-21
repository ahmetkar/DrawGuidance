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
    onOpenSubscription: () -> Unit,
    /** v51 notta resim düzenleme: geçici basit resim tuvali açılır (bu düzenleyicinin üstünde). */
    onOpenCanvas: (String) -> Unit = {},
    /** v51 başka bir tuvale geç (yeni alıştırma yeni tuvalde; bu düzenleyici kapanır). */
    onSwitchCanvas: (localId: String) -> Unit = {},
    /** v52 Diğer menüsünden paylaşım ve canlı yayın sayfaları. */
    onOpenShare: () -> Unit = {},
    onOpenLiveShare: () -> Unit = {},
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
    val liveShare = koinInject<com.karapp.drawguidance.data.share.ShareManager>()
    // v56 bu düzenleyici örneğinin yayın sahip kimliği: yayın, tuval hiçbir düzenleyicide açık değilse ya da bu örnek
    // ara verdiyse durur; eski bir düzenleyicinin geç kapanışı yeni düzenleyicinin yayınını durduramaz
    val editorOwner = androidx.compose.runtime.remember { java.util.UUID.randomUUID().toString() }
    androidx.compose.runtime.DisposableEffect(canvasLocalId, editorOwner) {
        liveShare.attachEditor(canvasLocalId, editorOwner)
        onDispose { liveShare.detachEditor(canvasLocalId, editorOwner) }
    }
    androidx.compose.runtime.LaunchedEffect(tempRequest) {
        val id = tempRequest ?: return@LaunchedEffect
        vm.consumeTempCanvasRequest()
        if (id.isEmpty()) vm.emit(com.karapp.drawguidance.ui.screens.canvasedit.EditEvent.Message(context.getString(com.karapp.drawguidance.R.string.v52_temp_edit_failed)))
        else {
            // v52 yayın yapılan notta resim/şekil başka tuvalde düzenlenirken yayına ara verilir; nota dönülünce sürer
            liveShare.pauseLive(canvasLocalId, editorOwner)
            onOpenCanvas(id)
        }
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
        val hint = androidx.compose.ui.res.stringResource(if (vm.isTempShapeEdit) com.karapp.drawguidance.R.string.v52_temp_shape_hint else com.karapp.drawguidance.R.string.v51_temp_image_hint)
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
            onOpenShare = if (temp) null else onOpenShare,
            onOpenLiveShare = if (temp || !com.karapp.drawguidance.ui.screens.share.liveAllowed(kind)) null else onOpenLiveShare,
        )
    }
    }
    }
    // v51 canlı yayın: bu tuval yayındaysa düzenleyicideki her değişiklik karşı cihaza gönderilir
    LiveBroadcastBridge(canvasLocalId, vm, editorOwner)
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.LiveBroadcastBridge(canvasLocalId: String, vm: CanvasEditViewModel, owner: String) {
    val share = koinInject<com.karapp.drawguidance.data.share.ShareManager>()
    val liveOut by share.liveOut.collectAsState()
    val live = liveOut?.takeIf { it.canvasId == canvasLocalId } ?: return
    androidx.compose.runtime.LaunchedEffect(canvasLocalId, live.peerKey) {
        // v52 düzenleyiciye dönüldü: bu örneğin verdiği ara kalkar (son hali hemen gönderilir)
        share.resumeLive(canvasLocalId, owner)
        vm.canvas.collect { c -> if (c != null) share.publishLive(c) }
    }
    // v56 canlı mürekkep: çizilmekte olan çizgi karşı cihaza küçük paketlerle gider
    androidx.compose.runtime.LaunchedEffect(canvasLocalId, live.peerKey) {
        vm.inkProgress.collect { ink ->
            val b = vm.brushSettings.value
            share.publishInk(canvasLocalId, ink.strokeId, b.brushType.name, b.colorHex, b.sizePx, b.opacity, ink.points, vm.canvas.value?.currentPage ?: -1)
        }
    }
    // v56 düzenleyiciden ayrılınca ara, sahip kaydının silinmesiyle (detachEditor) kendiliğinden verilir; uygulama
    // arka plana alınınca bu örnek ara verir, öne gelince kaldırır
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.DisposableEffect(lifecycle, canvasLocalId) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, e ->
            when (e) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> share.pauseLive(canvasLocalId, owner)
                androidx.lifecycle.Lifecycle.Event.ON_START -> { share.resumeLive(canvasLocalId, owner); vm.canvas.value?.let(share::publishLive) }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    // v52 foto düzenleme açıkken (tam ekran ayrı düzenleyici) yayına ara verilir, kapanınca sürer
    val photoOpen by vm.photo.open.collectAsState()
    androidx.compose.runtime.LaunchedEffect(photoOpen != null) {
        if (photoOpen != null) share.pauseLive(canvasLocalId, "$owner:photo")
        else { share.resumeLive(canvasLocalId, "$owner:photo"); vm.canvas.value?.let(share::publishLive) }
    }
    val canvas by vm.canvas.collectAsState()
    com.karapp.drawguidance.ui.screens.share.LiveBroadcastBadge(
        canvasName = canvas?.name.orEmpty(),
        peerName = live.peerName,
        paused = live.paused,
        startedAt = live.startedAt,
        onStop = share::stopLive,
        modifier = androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
            .navigationBarsPadding().padding(bottom = 6.dp),
    )
}
