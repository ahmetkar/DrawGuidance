package com.karapp.drawguidance.ui.screens.canvasedit

import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Draw
import androidx.compose.ui.platform.testTag
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinearScale
import kotlin.math.roundToInt
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.FormatPaint
import androidx.compose.material.icons.filled.HighlightAlt
import androidx.compose.material.icons.filled.BorderBottom
import androidx.compose.material.icons.filled.BorderRight
import androidx.compose.material.icons.filled.BorderTop
import androidx.compose.material.icons.filled.BorderLeft
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import com.karapp.drawguidance.ui.screens.canvasedit.components.ToolInfoChip
import com.karapp.drawguidance.ui.screens.canvasedit.components.ToolItem
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.widthIn
import com.karapp.drawguidance.ui.components.ImageSavedDialog
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material.icons.filled.ScatterPlot
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.StackedLineChart
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.LineWeight
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.FilterVintage
import androidx.compose.material.icons.filled.Flare
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.EmojiPeople
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.AutoAwesomeMosaic
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Delete
import com.karapp.drawguidance.ui.screens.canvasedit.components.FormulaInlineEditor
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.FlipToFront
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.automirrored.filled.ArrowRightAlt
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Architecture
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Polyline
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.key
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.platform.LocalConfiguration
import com.karapp.drawguidance.ui.screens.canvasedit.components.EditorToolRow
import com.karapp.drawguidance.ui.screens.canvasedit.tutorial.tutorialAnchor
import com.karapp.drawguidance.ui.screens.canvasedit.components.ModeSelector
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch
import androidx.compose.ui.geometry.Offset
import com.karapp.drawguidance.data.images.ImageIssue
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import com.karapp.drawguidance.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.karapp.drawguidance.ui.screens.canvasedit.components.tabletWindow
import com.karapp.drawguidance.ui.screens.canvasedit.components.windowDrag
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.ui.common.hexToColor
import com.karapp.drawguidance.ui.components.BrushIcons
import com.karapp.drawguidance.ui.components.NeuIconButton
import com.karapp.drawguidance.ui.components.NeuSurface
import com.karapp.drawguidance.ui.components.NeuTopBar
import com.karapp.drawguidance.ui.components.brushLabel
import com.karapp.drawguidance.ui.screens.canvasedit.components.BrushPanelContent
import com.karapp.drawguidance.ui.screens.canvasedit.components.BrushPickerModal
import com.karapp.drawguidance.ui.screens.canvasedit.components.CanvasActions
import com.karapp.drawguidance.ui.screens.canvasedit.components.DrawingCanvas
import com.karapp.drawguidance.ui.screens.canvasedit.components.EditorSidePanel
import com.karapp.drawguidance.ui.screens.canvasedit.components.ElementsPanelContent
import com.karapp.drawguidance.ui.screens.canvasedit.components.InlineTextEditor
import com.karapp.drawguidance.ui.screens.canvasedit.components.ModeBar
import com.karapp.drawguidance.ui.screens.canvasedit.components.RecolorSheet
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementOps
import com.karapp.drawguidance.ui.screens.canvasedit.editor.rememberViewportState
import com.karapp.drawguidance.ui.theme.NeuPillShape
import com.karapp.drawguidance.ui.theme.NeuTheme
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun CanvasEditScreen(
    canvasLocalId: String,
    onBack: () -> Unit,
    viewModel: CanvasEditViewModel = koinViewModel(parameters = { parametersOf(canvasLocalId) }),
    /** v23 3B düzenleyiciyi açar (model kimliği: null yeni; tuvaldeki şeklin kimliği: null ise kaydedince eklenir). */
    onOpenModel3d: (modelId: String?, strokeId: String?) -> Unit = { _, _ -> },
) {
    val colors = NeuTheme.colors
    val canvas by viewModel.canvas.collectAsState()
    // v42 yola yerleştirilecek yazı (yol seçimi bekleniyor)
    val textPathPick by viewModel.textPathPick.collectAsState()
    val elements by viewModel.elements.collectAsState()
    val brush by viewModel.brushSettings.collectAsState()
    val isBrushPickerOpen by viewModel.isBrushPickerOpen.collectAsState()
    val panel by viewModel.panel.collectAsState()
    val mode by viewModel.mode.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()
    // v36 seçili şablon paneli (bağlam menüsünden Şablonlar penceresinde düzenlenir)
    val selectedPanelLeaf = remember(canvas, selectedIds) {
        if (selectedIds.size != 1) null else canvas?.strokes?.firstOrNull { it.id in selectedIds }?.shape?.panel?.leaf
    }
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()
    val savedColors by viewModel.savedColors.collectAsState()
    val drawingBrush by viewModel.drawingBrush.collectAsState()
    val imageImport by viewModel.imageImport.collectAsState()
    val imageSave by viewModel.imageSave.collectAsState()
    val cropRect by viewModel.cropRect.collectAsState()
    val cropTarget by viewModel.cropTarget.collectAsState()
    val cropArea by viewModel.cropArea.collectAsState()
    val shape by viewModel.shape.collectAsState()
    val isShapePickerOpen by viewModel.isShapePickerOpen.collectAsState()
    val textSettings by viewModel.textSettings.collectAsState()
    val textDefaults by viewModel.textDefaults.collectAsState()
    val textDraft by viewModel.textDraft.collectAsState()
    val diagramKind by viewModel.diagramKind.collectAsState()
    val isDiagramPickerOpen by viewModel.isDiagramPickerOpen.collectAsState()
    val selectedDiagram by viewModel.selectedDiagram.collectAsState()
    val selectedShape by viewModel.selectedShape.collectAsState()
    val shapeTool by viewModel.shapeTool.collectAsState()
    var modeBarHeightPx by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val shapeStyle by viewModel.shapeStyle.collectAsState()
    val graphSpec by viewModel.graphSpec.collectAsState()
    // Boya kutusu penceresi: açıldığında hedefin (eleman dolgusu ya da arka plan) rengiyle başlar
    var fillInitial by rememberSaveable { mutableStateOf<String?>(null) }
    var guideOpen by rememberSaveable { mutableStateOf(false) }
    var backgroundOpen by rememberSaveable { mutableStateOf(false) }
    // v33 çocuklar için arka plan boyama: yalnız büyük renk paleti
    var simpleBackgroundOpen by rememberSaveable { mutableStateOf(false) }
    // v40 arka plan resmini taşıma/boyutlandırma (Arka plan aracı seçili kalır)
    var backgroundEdit by rememberSaveable { mutableStateOf(false) }
    // v23 3B şekiller: seçici, düzenleyiciyi açma onayı (model, tuvaldeki şekil)
    var models3dOpen by rememberSaveable { mutableStateOf(false) }
    var filterOpen by rememberSaveable { mutableStateOf(false) }
    // v30 resim araçları
    var bgRemoveOpen by rememberSaveable { mutableStateOf(false) }
    var effectsOpen by rememberSaveable { mutableStateOf(false) }
    // v27 çizgi roman pencereleri
    var balloonOpen by rememberSaveable { mutableStateOf(false) }
    // v36 Karakterler penceresi
    var charactersOpen by rememberSaveable { mutableStateOf(false) }
    // v41 mekân arka planı: pencere ve seçilen arka planın yerleştirilmesi (panele ya da bütün sayfaya)
    var locationsOpen by rememberSaveable { mutableStateOf(false) }
    // v42 hazır nesneler (tema paketine ve kategoriye göre)
    var objectsOpen by rememberSaveable { mutableStateOf(false) }
    // v43 sunucudan inmiş paket içerikleri (diyalog kutuları, şekiller, diyagramlar)
    var packsOpen by rememberSaveable { mutableStateOf(false) }
    val packStore = org.koin.compose.koinInject<com.karapp.drawguidance.data.inbox.PackStore>()
    val installedPacks by packStore.packs.collectAsState()
    var placingLocation by rememberSaveable { mutableStateOf<String?>(null) }
    // v36 Şablonlar penceresi; açılırken seçili olacak panel (tuvalde seçili panelden açılınca)
    var templatesOpen by rememberSaveable { mutableStateOf(false) }
    var templatesLeaf by rememberSaveable { mutableStateOf<Int?>(null) }
    var balloonEdit by rememberSaveable { mutableStateOf(false) }
    // v36 efektler ve odak çizgileri tek pencerede: açık sekme (null: kapalı)
    var comicFxOpen by rememberSaveable { mutableStateOf<com.karapp.drawguidance.ui.screens.canvasedit.components.EffectsTab?>(null) }
    var glbPending by remember { mutableStateOf<com.karapp.drawguidance.ui.screens.model3d.PendingGlb?>(null) }
    val glbFailText = stringResource(R.string.glb_import_failed)
    val glbPicker = com.karapp.drawguidance.ui.screens.model3d.rememberGlbPicker(
        onRead = { glbPending = it },
        onFail = { viewModel.emit(EditEvent.Message(glbFailText.format(it))) },
    )
    var open3dAsk by remember { mutableStateOf<Pair<String?, com.karapp.drawguidance.data.model.Stroke?>?>(null) }
    var edit3dAsk by remember { mutableStateOf<com.karapp.drawguidance.data.model.Stroke?>(null) }
    var perspectiveOpen by rememberSaveable { mutableStateOf(false) }
    val diagramFocus by viewModel.diagramFocus.collectAsState()
    val diagramLinking by viewModel.diagramLinking.collectAsState()
    val diagramLink by viewModel.diagramLink.collectAsState()
    val shapeParts by viewModel.shapeParts.collectAsState()
    val vectorSelection by viewModel.vectorSelection.collectAsState()
    // v15
    val layers by viewModel.layers.collectAsState()
    val activeLayer by viewModel.activeLayer.collectAsState()
    val autosave by viewModel.autosave.collectAsState()
    val suggestionsOn by viewModel.suggestions.collectAsState()
    val dirty by viewModel.dirty.collectAsState()
    val lastAddItem by viewModel.lastAddItem.collectAsState()
    val isFormulaPickerOpen by viewModel.isFormulaPickerOpen.collectAsState()
    val formulaTemplate by viewModel.formulaTemplate.collectAsState()
    val selectedFormula by viewModel.selectedFormula.collectAsState()
    val formulaEditing by viewModel.formulaEditing.collectAsState()
    val formulaSlot by viewModel.formulaSlot.collectAsState()
    val penAnchors by viewModel.penAnchors.collectAsState()
    val penPending by viewModel.penPending.collectAsState()
    val canvasPick by viewModel.canvasPick.collectAsState()
    val harmonyDraft by viewModel.harmonyDraft.collectAsState()
    val placeDraft by viewModel.placeDraft.collectAsState()
    val pendingSceneTool by viewModel.pendingSceneTool.collectAsState()
    val selectedImage by viewModel.selectedImage.collectAsState()
    val tracing by viewModel.tracing.collectAsState()
    val textSuggestion by viewModel.textSuggestion.collectAsState()
    val shapeSuggestion by viewModel.shapeSuggestion.collectAsState()
    val textSuggestOn by viewModel.textSuggestOn.collectAsState()
    val shapeSuggestOn by viewModel.shapeSuggestOn.collectAsState()
    val lineSuggestion by viewModel.lineSuggestion.collectAsState()
    // v28 öneri balonlarının sürüklenen yeri ve son konumları
    var suggestPin by remember { mutableStateOf(viewModel.suggestPin) }
    val balloonPositions = remember { androidx.compose.runtime.mutableStateMapOf<String, Offset>() }
    val selectionSuggestOn by viewModel.selectionSuggestOn.collectAsState()
    val pdfExport by viewModel.pdfExport.collectAsState()
    val graphEdit by viewModel.graphEdit.collectAsState()
    val diagramTheme by viewModel.diagramTheme.collectAsState()
    var partStyleOpen by remember { mutableStateOf(false) }
    var regionColorOpen by remember { mutableStateOf(false) }
    // v17: çizim alanında açılan menü (iki parmak uzun basma → bağlam menüsü, Ekle modunda dokunma → buraya ekle)
    // v47 Ekle modunda tuvale dokunulan yer: son seçilen eklenecek oraya konur (eski "buraya ekle" menüsü kaldırıldı)
    var addTap by remember { mutableStateOf<Offset?>(null) }
    // v45 iki parmakla basınca seçim menüsünün Düzenle listesi açılır (eski bağlam menüsünün yerine)
    var contextEdit by remember { mutableStateOf(false) }
    var inkColorOpen by remember { mutableStateOf(false) }
    // v17 bağlam menüsünden renk: "ink" fırça çizimi, "text" yazı rengi
    var colorSheet by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    fun ctxString(id: Int, vararg args: Any): String = context.getString(id, *args)
    /** v42 seçili tek yazının yol üzerinde yazı eylemleri: yola yerleştir ya da (yoldaysa) alta/üste, yön, çıkar. */
    fun textPathActions(): List<com.karapp.drawguidance.ui.screens.canvasedit.components.ModeAction> {
        val id = viewModel.selectedIds.value.singleOrNull() ?: return emptyList()
        val stroke = viewModel.canvas.value?.strokes?.firstOrNull { it.id == id } ?: return emptyList()
        val path = stroke.text?.path
        fun a(label: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) =
            com.karapp.drawguidance.ui.screens.canvasedit.components.ModeAction(ctxString(label), icon, onClick)
        return if (path == null) {
            if (viewModel.hasTextPathHosts()) listOf(a(R.string.v42_text_on_path, Icons.Filled.Gesture) { viewModel.startTextOnPathFor(id) }) else emptyList()
        } else listOf(
            a(if (path.below) R.string.v42_text_path_above else R.string.v42_text_path_below, Icons.Filled.SwapVert) { viewModel.adjustTextPath(id, below = !path.below) },
            a(R.string.v42_text_path_reverse, Icons.Filled.SwapHoriz) { viewModel.adjustTextPath(id, reversed = !path.reversed) },
            a(R.string.v42_text_path_detach, Icons.Filled.LinkOff) { viewModel.detachTextFromPath(id) },
        )
    }
    val regionColor by viewModel.regionColor.collectAsState()
    var vectorizeOpen by remember { mutableStateOf(false) }
    // v34 resim düzenlemede vektörleştirme türü üst menüden doğrudan seçilir (null: türler pencerede)
    var vectorizeMode by remember { mutableStateOf<com.karapp.drawguidance.content.trace.EdgeMode?>(null) }
    // v18 Taşı: konum girme penceresi ve ok düğmelerinin adımı (1 / 10 px); Genişlet: kağıt boyutu penceresi
    var positionOpen by remember { mutableStateOf(false) }
    var moveStep by rememberSaveable { mutableStateOf(1) }
    var sizeOpen by remember { mutableStateOf(false) }
    var unsavedOpen by remember { mutableStateOf(false) }
    var diagramThemeOpen by remember { mutableStateOf(false) }
    val selectedIsGraph = selectedShape?.shape?.let { com.karapp.drawguidance.ui.drawing.ShapeRegistry.isGraph(it.kind) && it.graph != null } == true
    var diagramColorPart by remember { mutableStateOf<com.karapp.drawguidance.ui.screens.canvasedit.state.DiagramController.ColorPart?>(null) }
    val kind = canvas?.kind ?: com.karapp.drawguidance.data.model.CanvasKind.ART
    val current0 = canvas
    // Seçili yazılar: ayarlar panelinde yazı tipi ayarları bunlara uygulanır
    val selectedTexts = remember(canvas?.strokes, selectedIds) {
        if (selectedIds.isEmpty()) emptyList() else canvas?.strokes.orEmpty().filter { it.id in selectedIds && it.brushType == BrushType.TEXT }
    }
    val snackbar = remember { SnackbarHostState() }
    // v19: durum sıfırlanınca yeniden başlayan LaunchedEffect bildirimi iptal etmesin diye ekranın kapsamında gösterilir
    val snackbarScope = androidx.compose.runtime.rememberCoroutineScope()
    val saveFailedText = stringResource(R.string.image_save_failed)
    LaunchedEffect(imageSave) {
        if (imageSave == ImageSaveState.Failed) {
            viewModel.dismissImageSave()
            snackbar.currentSnackbarData?.dismiss()
            snackbarScope.launch { snackbar.showSnackbar(saveFailedText) }
        }
    }
    // Başlık satırının en sağındaki "Resim olarak kaydet" düğmesi
    // v29 not alma / çizgi roman tuvaline aktarma
    var transferOpen by rememberSaveable { mutableStateOf(false) }
    val transferDoneText = stringResource(R.string.transfer_done)
    val hiddenDoneText = stringResource(R.string.hidden_done)
    val bgRemovedText = stringResource(R.string.bg_removed_done)
    val aiOnText = stringResource(R.string.ai_canvas_on_done)
    val aiOffText = stringResource(R.string.ai_canvas_off_done)
    val shownDoneText = stringResource(R.string.shown_done)
    val transferFailedText = stringResource(R.string.transfer_failed)
    val transferEmptyText = stringResource(R.string.transfer_empty)
    // v33 araç yardımı: dokununca ad, 2 sn basılı tutunca açıklama (çizim alanının köşesindeki kutuda)
    val toolHelp = com.karapp.drawguidance.ui.screens.canvasedit.components.rememberToolHelp()
    val saveImageButton: @Composable () -> Unit = { com.karapp.drawguidance.ui.screens.canvasedit.components.ToolHelpScope(toolHelp) {
        if (imageSave == ImageSaveState.Saving) {
            Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = colors.accent)
            }
        } else {
            // v42 sayfalı tuvallerde (not, çizgi roman) tek Kaydet penceresi: resim, PDF ve çizgi romanda CBZ
            val unified = kind.paged
            NeuIconButton(
                Icons.Filled.SaveAlt,
                stringResource(if (unified) R.string.v42_save_title else R.string.save_as_image),
                viewModel::openUnifiedSave, tint = colors.accent,
                label = stringResource(if (unified) R.string.v42_save_title else R.string.label_save_image),
                modifier = Modifier.tutorialAnchor(com.karapp.drawguidance.ui.screens.canvasedit.tutorial.TutorialAnchor.SAVE).testTag("header_save"),
            )
        }
    } }
    val viewport = rememberViewportState()
    // v40 cetvel ve şekil kalıbı (⋮ menüsü, basit menüde tek düğme)
    val rulerActions = com.karapp.drawguidance.ui.screens.canvasedit.ruler.rememberRulerActions(viewModel, viewport)
    val rulerSession by viewModel.ruler.session.collectAsState()
    val contextTolerancePx = with(androidx.compose.ui.platform.LocalDensity.current) { 22.dp.toPx() }
    val arrowFromBox by viewModel.arrowFrom.collectAsState()
    var recolorOpen by rememberSaveable { mutableStateOf(false) }
    val aiOn by viewModel.aiEnabled.collectAsState()
    // v32 eğitim modu
    val tutorialUi by viewModel.tutorial.ui.collectAsState()
    val tutorialLive by viewModel.tutorial.live.collectAsState()
    val tutorialOffer by viewModel.tutorial.offer.collectAsState()
    val tutorialCompleted by viewModel.tutorial.completed.collectAsState()
    val tutorialAnchors = remember { com.karapp.drawguidance.ui.screens.canvasedit.tutorial.TutorialAnchors() }
    androidx.compose.runtime.SideEffect { tutorialAnchors.focus = tutorialUi?.step?.anchor }
    var tutorialCallout by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    // v30 gizleme seçim penceresi
    var hideOpen by rememberSaveable { mutableStateOf(false) }
    // Başlıktaki ek düğmeler: Kaydet (otomatik kayıt kapalıyken), ⋮ (PDF, ayarlar); v19: Katmanlar üst menüde
    val headerActions: @Composable () -> Unit = { com.karapp.drawguidance.ui.screens.canvasedit.components.ToolHelpScope(toolHelp) {
        if (!autosave) {
            NeuIconButton(
                Icons.Filled.Save,
                stringResource(if (dirty) R.string.save_now_dirty else R.string.save_now),
                viewModel::saveNow,
                tint = if (dirty) colors.accent else null,
                selected = dirty,
            )
        }
        com.karapp.drawguidance.ui.screens.canvasedit.components.EditorMoreMenu(
            // v42 PDF olarak kaydet ⋮ menüsünde değil, birleşik Kaydet penceresinde
            showPdf = false,
            exportingPdf = pdfExport == com.karapp.drawguidance.ui.screens.canvasedit.state.PdfExportState.Exporting,
            autosave = autosave,
            onPdf = viewModel::openPdfDialog,
            onAutosave = viewModel::setAutosave,
            suggestions = suggestionsOn,
            onSuggestions = viewModel::setSuggestions,
            textSuggest = textSuggestOn,
            onTextSuggest = viewModel::setTextSuggest,
            shapeSuggest = shapeSuggestOn,
            onShapeSuggest = viewModel::setShapeSuggest,
            selectionSuggest = selectionSuggestOn,
            onSelectionSuggest = viewModel::setSelectionSuggest,
            lineSuggest = viewModel.lineSuggestOn.collectAsState().value,
            onLineSuggest = viewModel::setLineSuggest,
            onTransfer = if (kind == com.karapp.drawguidance.data.model.CanvasKind.ART) ({ transferOpen = true }) else null,
            aiEnabled = aiOn,
            onAiEnabled = viewModel::setAiEnabled,
            onTutorial = if (viewModel.tutorial.groups(kind).isNotEmpty()) viewModel.tutorial::openOffer else null,
            extraItems = { close ->
                com.karapp.drawguidance.ui.screens.canvasedit.components.V40MoreMenuItems(kind, viewModel, close)
                // v42 Cetvel ve Şekil kalıbı ⋮ menüsünde değil, Çiz satırında
            },
        )
    } }
    val styleLine: @Composable () -> Unit = {
        canvas?.let {
            com.karapp.drawguidance.ui.screens.canvasedit.components.EditorStyleLine(
                width = it.width, height = it.height,
                colorHex = brush.colorHex, text = textSettings,
                activeLayer = layers.takeIf { l -> l.size > 1 }?.firstOrNull { l -> l.id == activeLayer }?.name,
                onClick = { if (panel != PanelTab.BRUSH) viewModel.togglePanel(PanelTab.BRUSH) },
            )
        }
    }

    // v29 resim ekleme kaynağı (not alma ve çizgi roman): cihazdan ya da kayıtlı resimlerden
    var imageSourceOpen by rememberSaveable { mutableStateOf(false) }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        // "Buraya ekle" ile seçildiyse resim dokunulan yere konur
        val anchor = viewModel.takeAddAnchor()
        if (uri != null) {
            val size = viewport.viewSize
            val center = anchor ?: viewport.toCanvas(Offset(size.width / 2f, size.height / 2f))
            viewModel.importImage(uri, center)
        }
    }

    val imageAddedText = stringResource(R.string.image_added)
    val erasedRemovedText = stringResource(R.string.erased_removed)
    val gapsFilledText = stringResource(R.string.command_gaps_filled)
    val needsSelectionText = stringResource(R.string.command_needs_selection)
    val noGapsText = stringResource(R.string.command_no_gaps)
    val layerLockedText = stringResource(R.string.layer_locked_event)
    val savedText = stringResource(R.string.saved_event)
    val regionOpenText = stringResource(R.string.region_open)
    val noCorrectionText = stringResource(R.string.no_correction)
    val swipeTexts = listOf(
        stringResource(R.string.swipe_undone), stringResource(R.string.swipe_nothing_to_undo),
        stringResource(R.string.swipe_redone), stringResource(R.string.swipe_nothing_to_redo),
    )
    val suggestionsOffText = stringResource(R.string.suggestions_off_event)
    val textSuggestOffText = stringResource(R.string.text_suggest_off_event)
    val shapeSuggestOffText = stringResource(R.string.shape_suggest_off_event)
    val selectionSuggestOffText = stringResource(R.string.selection_suggest_off_event)
    val draw3dOnText = stringResource(R.string.draw3d_on_event)
    val draw3dOffText = stringResource(R.string.draw3d_off_event)
    val pdfSavedText = stringResource(R.string.pdf_saved)
    val pdfFailedText = stringResource(R.string.pdf_failed)
    LaunchedEffect(pdfExport) {
        val state = pdfExport
        val message = when (state) {
            is com.karapp.drawguidance.ui.screens.canvasedit.state.PdfExportState.Done -> pdfSavedText.format(state.file.location, formatBytes(state.file.sizeBytes))
            com.karapp.drawguidance.ui.screens.canvasedit.state.PdfExportState.Failed -> pdfFailedText
            else -> null
        }
        if (message != null) {
            viewModel.dismissPdf()
            snackbar.currentSnackbarData?.dismiss()
            snackbarScope.launch { snackbar.showSnackbar(com.karapp.drawguidance.i18n.I18n.tr(message)) }
        }
    }
    val visualVersion by viewModel.visualVersion.collectAsState()
    val configuration = LocalConfiguration.current
    // v18: çizerken ekranın en yüksek yenileme hızı (90/120 Hz) istenir; çıkınca önceki kipe dönülür
    val hostView = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(hostView) {
        val activity = generateSequence(hostView.context) { (it as? android.content.ContextWrapper)?.baseContext }
            .filterIsInstance<android.app.Activity>().firstOrNull()
        val previous = activity?.let { runCatching { com.karapp.drawguidance.platform.Compat.preferHighRefreshRate(it) }.getOrNull() }
        onDispose { activity?.let { runCatching { com.karapp.drawguidance.platform.Compat.restoreRefreshRate(it, previous) } } }
    }
    // Yatay ekran ve tabletlerde araçlar başlık satırına, modlar sol kenardaki dikey raya taşınır
    val wide = configuration.screenWidthDp >= 600 || configuration.screenWidthDp > configuration.screenHeightDp
    // Yatay telefon gibi alçak ekranlarda araçlar başlık satırına alınır; tuvale ve panele yer kalır
    val compactHeight = wide && configuration.screenHeightDp < 600
    // v38 yatay ekran: üst menüdeki düğmelerin adı simgelerinin altında sürekli görünür
    val landscape = configuration.screenWidthDp > configuration.screenHeightDp
    // v45 tablette sürüklenen pencereler (telefonda null: yerleşim değişmez)
    val topMenuWindow = com.karapp.drawguidance.ui.screens.canvasedit.components.rememberTabletWindow(com.karapp.drawguidance.ui.screens.canvasedit.components.TabletWindows.TOP_MENU)
    val modeMenuWindow = com.karapp.drawguidance.ui.screens.canvasedit.components.rememberTabletWindow(com.karapp.drawguidance.ui.screens.canvasedit.components.TabletWindows.MODE_MENU)
    val sidePanelWindow = com.karapp.drawguidance.ui.screens.canvasedit.components.rememberTabletWindow(com.karapp.drawguidance.ui.screens.canvasedit.components.TabletWindows.SIDE_PANEL)
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            val message = when (event) {
                is EditEvent.ImageAdded -> imageAddedText.format(formatBytes(event.storedBytes.toLong()), formatBytes(event.originalBytes.toLong()))
                is EditEvent.ErasedRemoved -> erasedRemovedText.format(event.count)
                is EditEvent.GapsFilled -> gapsFilledText.format(event.count)
                EditEvent.CommandNeedsSelection -> needsSelectionText
                EditEvent.NoGapsFound -> noGapsText
                EditEvent.LayerLocked -> layerLockedText
                EditEvent.Saved -> savedText
                EditEvent.RegionOpen -> regionOpenText
                EditEvent.NoCorrection -> noCorrectionText
                is EditEvent.Swiped -> swipeTexts[(if (event.redo) 2 else 0) + (if (event.done) 0 else 1)]
                EditEvent.SuggestionsOff -> suggestionsOffText
                EditEvent.TextSuggestionsOff -> textSuggestOffText
                EditEvent.ShapeSuggestionsOff -> shapeSuggestOffText
                EditEvent.SelectionSuggestionsOff -> selectionSuggestOffText
                EditEvent.Draw3dOn -> draw3dOnText
                EditEvent.Draw3dOff -> draw3dOffText
                is EditEvent.Message -> event.text
                is EditEvent.Transferred -> when {
                    event.empty -> transferEmptyText
                    event.name == null -> transferFailedText
                    else -> { transferOpen = false; transferDoneText.format(event.name) }
                }
                EditEvent.AiOn -> aiOnText
                EditEvent.AiOff -> aiOffText
                is EditEvent.BackgroundRemoved -> bgRemovedText.format(event.percent)
                is EditEvent.HiddenChanged -> if (event.hidden > 0) hiddenDoneText.format(event.hidden) else shownDoneText.format(event.shown)
                is EditEvent.TutorialFinished -> context.getString(R.string.tut_finished, context.getString(event.title))
                is EditEvent.PageLimit -> context.getString(R.string.page_limit_reached, event.max)
                EditEvent.PaintMiss -> context.getString(R.string.paint_miss)
            }
            // Yeni bildirim eskisini beklemeden onun yerine geçer
            snackbar.currentSnackbarData?.dismiss()
            launch { snackbar.showSnackbar(com.karapp.drawguidance.i18n.I18n.tr(message)) }
        }
    }

    // Geri tuşu önce açık paneli kapatır, sonra çizim moduna döner
    BackHandler(enabled = panel != null) { viewModel.closePanel() }
    BackHandler(enabled = panel == null && mode != EditMode.DRAW) { viewModel.finishAction() }
    // Otomatik kayıt kapalıyken kaydedilmemiş değişiklikle çıkılmak istenirse sorulur
    val needsSavePrompt = !autosave && dirty
    val requestBack: () -> Unit = { if (needsSavePrompt) unsavedOpen = true else onBack() }
    BackHandler(enabled = panel == null && mode == EditMode.DRAW && needsSavePrompt) { unsavedOpen = true }
    // v32 eğitim sürerken geri tuşu eğitimi iptal eder (tuval eğitimden önceki haline döner)
    BackHandler(enabled = tutorialUi != null) { viewModel.tutorial.cancel() }

    val selectedElementCount = remember(elements, selectedIds) { elements.selectedElements(selectedIds).size }
    val actions = remember(viewModel) {
        CanvasActions(
            onStrokeFinished = { points, id -> viewModel.onStrokeFinished(points, id) },
            // Seçili formülün düzenlenebilir kısmına dokunmak düzenlemeyi o kısımla açar
            onSelectAt = { p, tolerance -> viewModel.selectAt(p.x, p.y, tolerance) },
            onDeselectAt = { p, tolerance -> viewModel.deselectAt(p.x, p.y, tolerance) },
            onClearSelection = viewModel::clearSelection,
            // v41 seçim yokken boş yere (kağıtta) dokunmak arka planı eleman gibi seçer
            onEmptyTap = { p ->
                when {
                    viewModel.selectedIds.value.isNotEmpty() -> false
                    viewModel.backgroundSelectable(p.x, p.y) -> { backgroundEdit = true; true }
                    // v47 boş yere dokunmanın ekleme menüsü kaldırıldı (tuvalde yalnız saydam seçim menüsü kaldı)
                    else -> false
                }
            },
            onAddTap = { _, p -> addTap = p },
            onEditAt = { p, tolerance -> viewModel.editAt(p.x, p.y, tolerance) },
            // v45 iki parmakla uzun basma: elemanın üstünde eleman seçilir (Seç'e geçilir, köşesinde seçim menüsü);
            // v47 boş yerde menü açılmaz (yalnız saydam seçim menüsü kaldı)
            onContextMenu = { _, p ->
                if (viewModel.selectForContext(p.x, p.y, contextTolerancePx / viewport.scale.coerceAtLeast(0.01f)) || viewModel.selectedIds.value.isNotEmpty()) {
                    if (viewModel.mode.value != EditMode.SELECT) viewModel.setMode(EditMode.SELECT)
                    // Seçimin türüne özel işlemler (eski bağlam menüsündekiler) Düzenle listesinde açılır
                    contextEdit = viewModel.canvas.value?.kind != com.karapp.drawguidance.data.model.CanvasKind.KIDS
                }
            },
            onThreeFingerSwipe = viewModel::swipeUndo,
            draw3d = viewModel::draw3dFor,
            scene3d = viewModel::scene3dFor,
            onMove = viewModel::moveSelected,
            onRotate = { degrees, pivot -> viewModel.rotateSelected(degrees, pivot) },
            onScale = { sx, sy, pivot -> viewModel.scaleSelected(sx, sy, pivot) },
            onResize = viewModel::resizeCanvas,
            onLongPressSelect = { p, tolerance -> viewModel.selectOnlyAt(p.x, p.y, tolerance) },
            onHoldMove = { p, tolerance -> viewModel.holdMoveAt(p.x, p.y, tolerance) },
            ruler = { viewModel.ruler.takeIf { it.active } },
            onEdgeHold = viewModel::holdPaperEdge,
            onPickAt = { p, tolerance -> viewModel.pickAt(p.x, p.y, tolerance) },
            onReplaceStroke = viewModel::replaceStroke,
            onSlotTap = viewModel::tapFormulaSlot,
            buildShape = viewModel::shapeStrokes,
            onShapeFinished = viewModel::addShape,
            onShapeTap = viewModel::addShapeAt,
            onTextTap = { p, tolerance -> viewModel.beginTextAt(p.x, p.y, tolerance) },
            onTextBox = viewModel::beginTextBox,
            textDrafting = { viewModel.isTextDrafting },
            onCropChange = viewModel::updateCrop,
            onDiagramFinished = viewModel::addDiagram,
            onDiagramTap = viewModel::addDiagramAt,
            onShapeEditTap = { p, tolerance -> viewModel.applyShapeTool(p, tolerance) },
            onPaintTap = viewModel::paintAt, onPaintHover = viewModel::paintHover, onPaintWhole = viewModel::paintWhole,
            // v45 çocuk boya fırçası
            paintBrush = { viewModel.paintBrush.value }, onBrushDown = viewModel::brushDown, onBrushMove = viewModel::brushMove,
            onBrushUp = viewModel::brushUp, onBrushCancel = viewModel::brushCancel,
            onShapeDragBegin = { p, tolerance, handle -> viewModel.shapeDragBegin(p, tolerance, handle) },
            onShapeDragPreview = { kind, from, to, path, tolerance -> viewModel.shapeDragPreview(kind, from, to, path, tolerance) },
            onShapeDragEnd = { kind, from, to, path, tolerance -> viewModel.shapeDragEnd(kind, from, to, path, tolerance) },
            onVanishMove = { i, fx, fy, done -> viewModel.moveVanishingPoint(i, fx, fy, done) },
            onDiagramEditTap = { p, tolerance -> viewModel.diagramTap(p, tolerance) },
            // v45 çift dokunmanın ilk dokunuşu boş yerde ekleme menüsünü açmış olabilir: kağıt sığarken menü kapanır
            onFit = { viewport.fit() },
            onDiagramHasHandle = { p, tolerance -> viewModel.diagramHasHandleAt(p, tolerance) },
            onDiagramLinkDrag = { from, to, tolerance -> viewModel.diagramDragLink(from, to, tolerance) },
            onFormulaFinished = viewModel::addFormula,
            onFormulaTap = viewModel::addFormulaAt,
            onPenTap = viewModel::penTap,
            onPenDrag = viewModel::penDrag,
            onPenGrab = viewModel::penGrab,
            canvasPick = { viewModel.canvasPick.value },
            onEyedrop = { viewModel.eyedrop(it) },
            onRectSelect = viewModel::rectSelect,
            onLassoSelect = viewModel::lassoSelect,
            onPenEditDrag = viewModel::penEditDrag,
            onPenEraserFinished = { points, id -> viewModel.onStrokeFinished(points, id, penEraser = true) },
            eraserSize = viewModel::eraserSize,
        )
    }

    val notes = kind.isNotes
    val comic = kind == com.karapp.drawguidance.data.model.CanvasKind.COMIC
    // Ekle (+) menüsü: öğeler tek listede; seçilen öğe hatırlanır ve menüde etkin görünür
    val addItems = listOfNotNull(
        com.karapp.drawguidance.ui.screens.canvasedit.components.AddMenuItem("shape", stringResource(R.string.add_shape), Icons.Filled.Category) { viewModel.openShapePicker() },
        if (notes) com.karapp.drawguidance.ui.screens.canvasedit.components.AddMenuItem("formula", stringResource(R.string.add_formula), Icons.Filled.Functions) { viewModel.openFormulaPicker() } else null,
        com.karapp.drawguidance.ui.screens.canvasedit.components.AddMenuItem("text", stringResource(R.string.add_text), Icons.Filled.TextFields) { viewModel.startAction(EditMode.TEXT) },
        if (notes) com.karapp.drawguidance.ui.screens.canvasedit.components.AddMenuItem("diagram", stringResource(R.string.add_diagram), Icons.Filled.AccountTree) { viewModel.openDiagramPicker() } else null,
        // v45 not alma panelleri: çizim alanını çerçeveli bölümlere ayıran yerleşimler
        if (notes) com.karapp.drawguidance.ui.screens.canvasedit.components.AddMenuItem("panels", stringResource(R.string.v45_panels_add), com.karapp.drawguidance.ui.screens.canvasedit.components.V45Icons.NotesPanels) { viewModel.v45.openPanels() } else null,
        // v27 Kalem aracı (yol) yalnız Çiz modunun üst menüsünde
        // v27 çizgi roman: konuşma balonu, panel düzeni, efekt yazısı, hız/odak çizgileri
        if (comic) com.karapp.drawguidance.ui.screens.canvasedit.components.AddMenuItem("balloon", stringResource(R.string.add_balloon), Icons.Filled.ChatBubble) { balloonEdit = false; balloonOpen = true } else null,
        // v36 şablonlar: hazır sayfa düzenleri ve panel düzenleme (boyut, sıra, aralık, ekleme, silme)
        if (comic) com.karapp.drawguidance.ui.screens.canvasedit.components.AddMenuItem("templates", stringResource(R.string.add_templates), Icons.Filled.AutoAwesomeMosaic) {
            templatesLeaf = null; templatesOpen = true
        } else null,
        // v36 karakterler; v38 yalnız çizgi roman tuvalinde (resim tuvalinde düğmesi ve penceresi yok)
        if (comic) com.karapp.drawguidance.ui.screens.canvasedit.components.AddMenuItem("characters", stringResource(R.string.add_characters), Icons.Filled.EmojiPeople) { charactersOpen = true } else null,
        // v41 mekân arka planı (tuval oluşturma ekranından buraya taşındı): panele sığar ya da bütün sayfayı kaplar
        if (comic) com.karapp.drawguidance.ui.screens.canvasedit.components.AddMenuItem("locations", stringResource(R.string.v41_add_location), Icons.Filled.Landscape) { locationsOpen = true } else null,
        // v42 hazır nesneler (silah, fener, belge, hazine...) tema paketlerine göre
        if (comic) com.karapp.drawguidance.ui.screens.canvasedit.components.AddMenuItem("objects", stringResource(R.string.v42_add_objects), Icons.Filled.Inventory2) { objectsOpen = true } else null,
        // v36 efektler (aksiyon, büyü, duygu, atmosfer, ses yazıları) ve odak çizgileri tek düğmede
        if (comic) com.karapp.drawguidance.ui.screens.canvasedit.components.AddMenuItem("effects", stringResource(R.string.add_effects), Icons.Filled.FlashOn) {
            comicFxOpen = com.karapp.drawguidance.ui.screens.canvasedit.components.EffectsTab.EFFECTS
        } else null,
        // v43 inmiş paketlerin şekil, diyalog kutusu ve diyagram resimleri (paket varsa)
        if (com.karapp.drawguidance.ui.screens.canvasedit.components.PackWindows.hasAny(installedPacks, kind.packMode())) {
            com.karapp.drawguidance.ui.screens.canvasedit.components.AddMenuItem("packs", stringResource(R.string.v43_add_packs), Icons.Filled.Inventory2) { packsOpen = true }
        } else null,
        com.karapp.drawguidance.ui.screens.canvasedit.components.AddMenuItem("guide", stringResource(R.string.add_guide), Icons.Filled.GridOn) { guideOpen = true },
        // v25 "3B şekil ekle" ayrı seçenek (kendi simgesiyle); pencerede şekil seçilip "3B şekil ekle" düğmesiyle eklenir
        com.karapp.drawguidance.ui.screens.canvasedit.components.AddMenuItem("model3d", stringResource(R.string.add_model3d), Icons.Filled.ViewInAr) { models3dOpen = true },
        // v27 not tuvalinde perspektif çizgisi eklenmez, yalnız kılavuz çizgileri
        if (notes) null else com.karapp.drawguidance.ui.screens.canvasedit.components.AddMenuItem("perspective", stringResource(R.string.add_perspective), Icons.Filled.Architecture) { perspectiveOpen = true },
        com.karapp.drawguidance.ui.screens.canvasedit.components.AddMenuItem(
            "image", stringResource(R.string.add_image), Icons.Filled.AddPhotoAlternate, enabled = imageImport != ImageImportState.Checking,
        ) {
            // v29 not alma ve çizgi romanda iki seçenek: cihazdan ya da kayıtlı resimlerden
            if (comic || notes) imageSourceOpen = true
            else pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
    ).map { item -> item.copy(onClick = { viewModel.rememberAddItem(item.id); item.onClick() }) }
        .let { items -> if (notes) com.karapp.drawguidance.ui.screens.canvasedit.components.notesFirst(items) { it.id } else items }

    // v18: üst menü moda göre değişir (Çiz: çizim araçları; Seç: kopyala/sil; Taşı: konum ve kaydırma; Ekle:
    // eklenecekler; Düzenle: döndür/ölçekle/kırp ve düzenlenen elemanın araçları; Genişlet: boyut ve kenarlar)
    val hasSelection = selectedIds.isNotEmpty()
    // v30 gizleme: tek eleman doğrudan gizlenir; birden çok eleman seçiliyse hangilerinin gizleneceği sorulur
    val hiddenCount = remember(elements) { elements.elements.count { it.hidden } }
    val requestHide: () -> Unit = {
        val chosen = elements.selectedElements(selectedIds)
        if (chosen.size == 1) viewModel.hideElements() else if (chosen.isNotEmpty() || hiddenCount > 0) hideOpen = true
    }
    val onCropTool: () -> Unit = { if (mode == EditMode.CROP) viewModel.finishAction() else viewModel.startAction(EditMode.CROP) }
    val fillTool = ToolItem(Icons.Filled.FormatColorFill, stringResource(R.string.fill_color), { fillInitial = viewModel.currentFillColor() })
    val cropTool = ToolItem(Icons.Filled.Crop, stringResource(R.string.mode_crop), onCropTool, selected = mode == EditMode.CROP)
    // v24 arka plan (kağıt rengi, desen, resim): Düzenle ve Çiz üst menülerinde
    val backgroundTool = ToolItem(Icons.Filled.Wallpaper, stringResource(R.string.add_background), { backgroundOpen = true }, selected = backgroundOpen || backgroundEdit)
    // v47 Elemanlar üst satırlarda değil (mod menüsünün son grubunda ya da yatayda sağdaki rayda)
    // v48 Taşı ve Genişlet iki ayrı düğme (Düzenle satırında): Taşı bir eleman seçiliyken, Genişlet tuval (kağıt/arka
    // plan) seçiliyken etkin — eleman seçili değilse tuval seçili sayılır
    val canvasTarget = com.karapp.drawguidance.ui.screens.canvasedit.components.MoveExpandRules.canvasTarget(hasSelection, backgroundEdit)
    val moveTool = ToolItem(
        Icons.Filled.OpenWith, stringResource(R.string.mode_move), { viewModel.chooseMode(EditMode.MOVE) },
        selected = mode == EditMode.MOVE,
        enabled = com.karapp.drawguidance.ui.screens.canvasedit.components.MoveExpandRules.moveEnabled(hasSelection, mode == EditMode.MOVE),
        tag = com.karapp.drawguidance.ui.screens.canvasedit.tutorial.TutorialAnchor.MOVE_EXPAND,
    )
    val expandTool = ToolItem(
        Icons.Filled.AspectRatio, stringResource(R.string.mode_expand), { viewModel.chooseMode(EditMode.EXPAND) },
        selected = mode == EditMode.EXPAND,
        enabled = com.karapp.drawguidance.ui.screens.canvasedit.components.MoveExpandRules.expandEnabled(canvasTarget, mode == EditMode.EXPAND),
        tag = "expand_tool",
    )
    val activeAddId = when (mode) {
        EditMode.SHAPE -> "shape"
        EditMode.TEXT -> "text"
        EditMode.FORMULA -> "formula"
        EditMode.DIAGRAM -> "diagram"
        EditMode.PEN -> "pen"
        EditMode.GUIDE -> "guide"
        EditMode.PERSPECTIVE -> "perspective"
        // Ekle modunda henüz bir şey eklenmiyorken son seçilen öğe işaretli durur
        EditMode.ADD -> lastAddItem
        else -> null
    }
    // v33 üst menüde yalnız ana araçlar (Noktalar, Çizgiler, Alan boya, grafikte Grafik): Nokta ekle ve Çizgi çiz
    // Noktalar'ın alttaki nokta panelinde; böylece şekil düzenleme düğmelerinin hepsi kaydırmadan görünür
    val shapeToolList = listOfNotNull(
        // v45 parametreli şekilde Değerler aracı başta (Düzenle onunla açılır)
        if (com.karapp.drawguidance.ui.drawing.ShapeRegistry.paramModel(selectedShape?.shape?.kind.orEmpty()) != null) ShapeTool.PARAMS else null,
        ShapeTool.NODES, ShapeTool.LINES, ShapeTool.FILL, if (selectedIsGraph) ShapeTool.GRAPH else null,
    )
    val shapeToolLabels = shapeToolList.associateWith { com.karapp.drawguidance.ui.screens.canvasedit.components.shapeToolLabel(it) }
    val rotateText = stringResource(R.string.mode_rotate)
    val scaleText = stringResource(R.string.mode_scale)
    val duplicateText = stringResource(R.string.duplicate_selection)
    val deleteText = stringResource(R.string.delete_selection)
    val fitText = stringResource(R.string.zoom_fit)
    val themeText = stringResource(R.string.diagram_theme)
    // Düzenle: Döndür ve Ölçekle (v18'de Seç'ten taşındı) seçili elemana uygulanır
    val rotateTool = ToolItem(
        Icons.AutoMirrored.Filled.RotateRight, rotateText, { viewModel.chooseMode(EditMode.ROTATE) },
        selected = mode == EditMode.ROTATE, enabled = hasSelection || mode == EditMode.ROTATE,
    )
    val scaleTool = ToolItem(
        Icons.Filled.OpenInFull, scaleText, { viewModel.chooseMode(EditMode.SCALE) },
        selected = mode == EditMode.SCALE, enabled = hasSelection || mode == EditMode.SCALE,
    )
    // v34 Düzenle: şekil düzenlemenin araçları doğrudan (seçili şekil o araçla açılır; seçim yoksa dokunulan şekil)
    val pendingShapeTool by viewModel.pendingShapeTool.collectAsState()
    val shapeDirectTools = shapeDirectTools(pendingShapeTool, viewModel::editShapeWith)
    // Taşı: seçimin konumu, ok düğmeleri (1 ya da 10 px), sayfada ortala
    val selectionOrigin = remember(canvas?.strokes, selectedIds) { viewModel.selectionOrigin() }
    val step = moveStep.toFloat()
    val moveItems = listOf(
        ToolItem(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.move_nudge_left), { viewModel.moveSelected(-step, 0f) }, enabled = hasSelection),
        ToolItem(Icons.Filled.KeyboardArrowUp, stringResource(R.string.move_nudge_up), { viewModel.moveSelected(0f, -step) }, enabled = hasSelection),
        ToolItem(Icons.Filled.KeyboardArrowDown, stringResource(R.string.move_nudge_down), { viewModel.moveSelected(0f, step) }, enabled = hasSelection),
        ToolItem(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.move_nudge_right), { viewModel.moveSelected(step, 0f) }, enabled = hasSelection),
        ToolItem(Icons.Filled.CenterFocusStrong, stringResource(R.string.move_center), viewModel::centerSelection, enabled = hasSelection),
    )
    // Genişlet: kenarlardan (%10) genişletme ve kağıdı sığdırma
    val frameItems = listOf(
        ToolItem(Icons.Filled.BorderLeft, stringResource(R.string.expand_left), { viewModel.expandSide(CanvasEditViewModel.Side.LEFT) }),
        ToolItem(Icons.Filled.BorderTop, stringResource(R.string.expand_top), { viewModel.expandSide(CanvasEditViewModel.Side.TOP) }),
        ToolItem(Icons.Filled.BorderBottom, stringResource(R.string.expand_bottom), { viewModel.expandSide(CanvasEditViewModel.Side.BOTTOM) }),
        ToolItem(Icons.Filled.BorderRight, stringResource(R.string.expand_right), { viewModel.expandSide(CanvasEditViewModel.Side.RIGHT) }),
        ToolItem(Icons.Filled.ZoomOutMap, fitText, viewport::fit),
    )
    // v43 gelişmiş resim ve çizgi romanda Düzenle: nesneyi arka planla uyumla, mekâna yerleştir
    val sceneTools: List<ToolItem> = if (kind != com.karapp.drawguidance.data.model.CanvasKind.ART && kind != com.karapp.drawguidance.data.model.CanvasKind.COMIC) emptyList() else listOf(
        ToolItem(
            com.karapp.drawguidance.ui.screens.canvasedit.components.V44Icons.Harmonize, stringResource(R.string.v43_harmonize),
            { viewModel.startSceneTool(CanvasEditViewModel.SCENE_HARMONY) },
            selected = pendingSceneTool == CanvasEditViewModel.SCENE_HARMONY || harmonyDraft != null, tag = "scene_harmonize",
        ),
        ToolItem(
            com.karapp.drawguidance.ui.screens.canvasedit.components.V44Icons.PlaceInScene, stringResource(R.string.v43_place),
            { viewModel.startSceneTool(CanvasEditViewModel.SCENE_PLACE) },
            selected = pendingSceneTool == CanvasEditViewModel.SCENE_PLACE || placeDraft != null, tag = "scene_place",
        ),
    )
    val toolItems: List<ToolItem>? = when (mode.rowKind) {
        ToolRowKind.DRAW -> null
        ToolRowKind.SELECT -> listOf(
            // v43 dikdörtgenle ve kalemle alan seçimi, damlalık (tuvale çizmez)
            ToolItem(com.karapp.drawguidance.ui.screens.canvasedit.components.V44Icons.RectSelect, stringResource(R.string.v43_select_rect), { viewModel.toggleCanvasPick(com.karapp.drawguidance.ui.screens.canvasedit.components.CanvasPick.RECT_SELECT) }, selected = canvasPick == com.karapp.drawguidance.ui.screens.canvasedit.components.CanvasPick.RECT_SELECT),
            ToolItem(com.karapp.drawguidance.ui.screens.canvasedit.components.V44Icons.FreeSelect, stringResource(R.string.v43_select_lasso), { viewModel.toggleCanvasPick(com.karapp.drawguidance.ui.screens.canvasedit.components.CanvasPick.LASSO_SELECT) }, selected = canvasPick == com.karapp.drawguidance.ui.screens.canvasedit.components.CanvasPick.LASSO_SELECT),
            ToolItem(Icons.Filled.Colorize, stringResource(R.string.v43_eyedropper), { viewModel.toggleCanvasPick(com.karapp.drawguidance.ui.screens.canvasedit.components.CanvasPick.EYEDROPPER) }, selected = canvasPick == com.karapp.drawguidance.ui.screens.canvasedit.components.CanvasPick.EYEDROPPER),
            ToolItem(Icons.Filled.ContentCopy, duplicateText, viewModel::duplicateSelected, enabled = hasSelection),
            ToolItem(Icons.Filled.VisibilityOff, stringResource(R.string.hide_elements), requestHide, enabled = hasSelection || hiddenCount > 0),
            ToolItem(Icons.Filled.Delete, deleteText, viewModel::deleteSelected, enabled = hasSelection, danger = true),
        )
        // v48 Taşı (ok tuşları, ortala) ve Genişlet (kenarlardan genişlet, sığdır) ayrı satırlar
        ToolRowKind.MOVE -> moveItems
        ToolRowKind.FRAME -> frameItems
        ToolRowKind.ADD -> addItems.map { item ->
            ToolItem(item.icon, item.label, item.onClick, selected = item.id == activeAddId, enabled = item.enabled, tag = com.karapp.drawguidance.ui.screens.canvasedit.tutorial.TutorialAnchor.add(item.id))
        }
        ToolRowKind.EDIT -> when (mode) {
            EditMode.SHAPE_EDIT -> shapeToolList.map { t ->
                val family = t == ShapeTool.NODES && shapeTool.nodeFamily
                ToolItem(
                    shapeToolIcon(t), shapeToolLabels.getValue(t),
                    // Nokta ailesindeyken Noktalar'a dokunmak alt aracı (Nokta ekle / Çizgi çiz) korur
                    { if (!(t == ShapeTool.NODES && shapeTool.nodeFamily)) viewModel.selectShapeTool(t) },
                    selected = t == shapeTool || family,
                )
            } + listOf(rotateTool, scaleTool)
            EditMode.IMAGE_EDIT -> imageEditTools(
                rotateTool, scaleTool, viewModel::cropSelectedImage,
                vectorizeMode.takeIf { vectorizeOpen }, { m -> vectorizeMode = m; vectorizeOpen = true },
                filterOpen, { filterOpen = true }, bgRemoveOpen, { bgRemoveOpen = true }, effectsOpen, { effectsOpen = true },
            ) + sceneTools
            EditMode.DIAGRAM_EDIT -> listOf(
                ToolItem(Icons.Filled.Palette, themeText, { diagramThemeOpen = true }),
                fillTool,
            )
            // Düzenle (seçim yokken dokunulan elemanın düzenleyicisi açılır), v21: Vektör düzenle (seçili elemanı
            // dokunmadan düzenlemede açar), Döndür, Ölçekle, Kırp; v24 Arka plan (Ekle menüsünden taşındı)
            // v34: Nokta düzenle, Çizgi düzenle, Alan boya doğrudan (Vektör düzenle yerine); boya kutusu ve arka plan
            // Çiz satırında. Diyagram, resim, yazı ve formül dokununca kendi düzenleyicisinde açılır
            else -> shapeDirectTools + listOf(rotateTool, scaleTool, cropTool, moveTool, expandTool) + sceneTools
        }
    }
    val positionText = selectionOrigin?.let { stringResource(R.string.move_position_chip, it.x.roundToInt(), it.y.roundToInt()) }
        ?: stringResource(R.string.move_position_none)
    val positionDescription = stringResource(R.string.move_position_title) + ": " + positionText
    val stepText = stringResource(R.string.move_step, moveStep)
    val sizeText = canvas?.let { stringResource(R.string.canvas_size_chip, it.width, it.height) } ?: ""
    val sizeDescription = stringResource(R.string.canvas_size_title) + ": " + sizeText
    val positionHelp = stringResource(R.string.move_position_title)
    val stepHelp = stringResource(R.string.move_step_name)
    val sizeHelp = stringResource(R.string.canvas_size_title)
    // v18 Taşı/Genişlet bilgi çipleri: üst satır taşmasın diye tuvalin sol üstünde (Çiz'deki fırça göstergesinin yerinde)
    // v48 Taşı'da konum ve adım, Genişlet'te kağıt boyutu
    val infoChips: (@Composable () -> Unit)? = when (mode.rowKind) {
        ToolRowKind.MOVE -> {
            {
                ToolInfoChip(positionText, positionDescription, { if (hasSelection) positionOpen = true }, icon = Icons.Filled.OpenWith, modifier = Modifier.testTag("move_position_chip"), helpLabel = positionHelp)
                ToolInfoChip(stepText, stepText, { moveStep = if (moveStep == 1) 10 else 1 }, modifier = Modifier.testTag("move_step_chip"), helpLabel = stepHelp)
            }
        }
        ToolRowKind.FRAME -> {
            {
                ToolInfoChip(sizeText, sizeDescription, { sizeOpen = true }, icon = Icons.Filled.AspectRatio, modifier = Modifier.testTag("canvas_size_chip"), helpLabel = sizeHelp)
            }
        }
        else -> null
    }

    val toolRow: @Composable (Boolean) -> Unit = { fill ->
        com.karapp.drawguidance.ui.screens.canvasedit.components.ToolHelpScope(toolHelp) {
        EditorToolRow(
            panel = panel,
            brushType = brush.brushType,
            drawingBrush = drawingBrush,
            brushColorHex = brush.colorHex,
            canUndo = canUndo,
            onUndo = viewModel::undo,
            onOpenBrushPicker = viewModel::brushButton,
            onToggleEraser = viewModel::toggleEraser,
            onTogglePanel = viewModel::togglePanel,
            modifier = if (fill) Modifier.fillMaxWidth() else Modifier,
            fillWidth = fill,
            onFill = { fillInitial = viewModel.currentFillColor() },
            drawToolsEnabled = mode.allowsDrawTools,
            canRedo = canRedo,
            onRedo = viewModel::redo,
            items = toolItems,
            onBackground = { backgroundOpen = true },
            onPen = { if (mode == EditMode.PEN) viewModel.finishAction() else viewModel.startPen() },
            penActive = mode == EditMode.PEN,
            onRuler = rulerActions.toggleRuler,
            rulerActive = rulerSession == com.karapp.drawguidance.ui.screens.canvasedit.ruler.GuideSession.RULER,
            onTemplate = rulerActions.toggleTemplate,
            templateActive = rulerSession == com.karapp.drawguidance.ui.screens.canvasedit.ruler.GuideSession.TEMPLATE ||
                rulerSession == com.karapp.drawguidance.ui.screens.canvasedit.ruler.GuideSession.TEMPLATE_PICK,
            // Damlalık Çiz satırında yalnız geniş ekranda (dar telefonda satır Katmanlar'a kadar sığsın); her ekranda
            // Fırça ayarlarının renk bölümünde ve Seç satırında
            onEyedropper = if (wide) ({ viewModel.toggleCanvasPick(com.karapp.drawguidance.ui.screens.canvasedit.components.CanvasPick.EYEDROPPER) }) else null,
            eyedropperActive = canvasPick == com.karapp.drawguidance.ui.screens.canvasedit.components.CanvasPick.EYEDROPPER,
        )
        }
    }

    // v33 basit çizim (basit resim, basit not, çocuklar için): üstte tek menü; mod seçici yok
    val simple = kind.simple
    val kids = kind == com.karapp.drawguidance.data.model.CanvasKind.KIDS
    val eraserOn = brush.brushType == BrushType.ERASER
    val simpleTexts = SimpleTexts(
        brush = stringResource(R.string.brush_select), eraser = stringResource(R.string.brush_eraser),
        shape = stringResource(R.string.add_shape), image = stringResource(R.string.add_image),
        text = stringResource(R.string.add_text), model3d = stringResource(R.string.add_model3d),
        background = stringResource(R.string.simple_paint_background), backgroundFull = stringResource(R.string.add_background), paint = stringResource(R.string.mode_paint),
        elements = stringResource(R.string.tab_elements), select = stringResource(R.string.mode_select),
        diagram = stringResource(R.string.add_diagram), formula = stringResource(R.string.add_formula),
        shapeEdit = stringResource(R.string.simple_shape_edit), expand = stringResource(R.string.mode_expand),
    )
    var simpleLast by rememberSaveable { mutableStateOf<String?>(null) }
    val simpleMoveLabel = stringResource(R.string.mode_move)
    val rulerToolLabel = stringResource(R.string.v40_ruler)
    val templateToolLabel = stringResource(R.string.v40_template)
    val rectSelectLabel = stringResource(R.string.v43_select_rect)
    val rectSelectSimpleLabel = stringResource(R.string.v44_select_rect_simple)
    val eyedropperLabel = stringResource(R.string.v43_eyedropper)
    val brushSettingsLabel = stringResource(R.string.brush_settings_toggle)
    // v38 çocuk modunda çizim görevi (düğme, menü, seviyeler; bkz. challenge/)
    val challengeUi = com.karapp.drawguidance.ui.screens.canvasedit.challenge.rememberKidsChallengeUi()
    // v47 Elemanlar, Katmanlar ve Özellikler düğmeleri: yatay ekranda sağdaki rayda, dikeyde menülerde
    val panelRail = com.karapp.drawguidance.ui.screens.canvasedit.components.PanelMenu.railShown(landscape)
    // Açılır alanın düğmesine dokununca alt pencereler (arka plan, 3B) kapanır; etkin araç değişmez
    val panelTools = com.karapp.drawguidance.ui.screens.canvasedit.components.panelSimpleTools(panel) { tab ->
        backgroundOpen = false; simpleBackgroundOpen = false; backgroundEdit = false; models3dOpen = false
        viewModel.togglePanel(tab)
    }
    val simpleRaw: List<com.karapp.drawguidance.ui.screens.canvasedit.components.SimpleTool> = if (!simple) emptyList() else buildList {
        val drawing = mode == EditMode.DRAW
        add(com.karapp.drawguidance.ui.screens.canvasedit.components.SimpleTool("brush", BrushIcons.of(drawingBrush), simpleTexts.brush, drawing && !eraserOn, {
            if (!drawing) { viewModel.chooseMode(EditMode.DRAW); if (eraserOn) viewModel.brushButton() } else viewModel.brushButton()
        }))
        add(com.karapp.drawguidance.ui.screens.canvasedit.components.SimpleTool("eraser", BrushIcons.Eraser, simpleTexts.eraser, drawing && eraserOn, {
            if (!drawing) viewModel.chooseMode(EditMode.DRAW)
            if (!eraserOn) viewModel.toggleEraser()
        }))
        add(com.karapp.drawguidance.ui.screens.canvasedit.components.SimpleTool("shape", Icons.Filled.Category, simpleTexts.shape, mode == EditMode.SHAPE, viewModel::openShapePicker))
        if (kids) add(challengeUi.tool())
        // v36 basit resimde şekil düzenleme (az noktalı vektör düzenleme): seçili ya da dokunulan şekil açılır
        if (kind == com.karapp.drawguidance.data.model.CanvasKind.SIMPLE_ART) {
            add(com.karapp.drawguidance.ui.screens.canvasedit.components.SimpleTool(
                "shape_edit", shapeToolIcon(ShapeTool.NODES), simpleTexts.shapeEdit,
                mode == EditMode.SHAPE_EDIT || (mode == EditMode.EDIT_PICK && pendingShapeTool == ShapeTool.NODES), viewModel::simpleShapeEdit,
            ))
        }
        // v35 basit notta formül (bütün katalog) ve diyagram şeklin hemen ardında; arka plan her basit türde görünür yerde
        if (kind == com.karapp.drawguidance.data.model.CanvasKind.SIMPLE_NOTES) {
            add(com.karapp.drawguidance.ui.screens.canvasedit.components.SimpleTool("formula", Icons.Filled.Functions, simpleTexts.formula, mode == EditMode.FORMULA, viewModel::openFormulaPicker))
            add(com.karapp.drawguidance.ui.screens.canvasedit.components.SimpleTool("diagram", Icons.Filled.AccountTree, simpleTexts.diagram, mode == EditMode.DIAGRAM || mode == EditMode.DIAGRAM_EDIT, viewModel::openDiagramPicker))
            // v45 inmiş not paketlerinin şekilleri/diyagram resimleri basit notta da (gelişmiş not almadaki gibi; paket varsa)
            if (com.karapp.drawguidance.ui.screens.canvasedit.components.PackWindows.hasAny(installedPacks, kind.packMode())) add(com.karapp.drawguidance.ui.screens.canvasedit.components.SimpleTool("packs", Icons.Filled.Inventory2, stringResource(R.string.v43_add_packs), packsOpen, { packsOpen = true }))
            // v45 Panel ekle (not alma panelleri) diyagramın hemen ardında
            add(com.karapp.drawguidance.ui.screens.canvasedit.components.SimpleTool("panels", com.karapp.drawguidance.ui.screens.canvasedit.components.V45Icons.NotesPanels, stringResource(R.string.v45_panels_add), viewModel.v45.panelsOpen.collectAsState().value, viewModel.v45::openPanels))
        }
        add(com.karapp.drawguidance.ui.screens.canvasedit.components.SimpleTool("background", Icons.Filled.Wallpaper, if (kids) simpleTexts.background else simpleTexts.backgroundFull, backgroundOpen || simpleBackgroundOpen || backgroundEdit, {
            // Çocuklarda yalnız renk (büyük palet), öteki basit türlerde arka plan penceresi (renk, kağıt desenleri, resim)
            if (kids) simpleBackgroundOpen = true else backgroundOpen = true
        }))
        add(com.karapp.drawguidance.ui.screens.canvasedit.components.SimpleTool("image", Icons.Filled.AddPhotoAlternate, simpleTexts.image, mode == EditMode.IMAGE_EDIT, {
            if (kind.isPlainArt) pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) else imageSourceOpen = true
        }, enabled = imageImport != ImageImportState.Checking))
        add(com.karapp.drawguidance.ui.screens.canvasedit.components.SimpleTool("text", Icons.Filled.TextFields, simpleTexts.text, mode == EditMode.TEXT, { viewModel.chooseMode(EditMode.TEXT) }))
        // v48 çocuk modunda 3B şekil ekleme yok
        if (!kids) add(com.karapp.drawguidance.ui.screens.canvasedit.components.SimpleTool("model3d", Icons.Filled.ViewInAr, simpleTexts.model3d, models3dOpen, { models3dOpen = true }))
        // v37 çocuklarda boyama (kova simgesiyle) silginin hemen yanında
        com.karapp.drawguidance.ui.screens.canvasedit.components.SimpleTool("paint", if (kids) com.karapp.drawguidance.ui.screens.canvasedit.components.KidsPaintIcon else Icons.Filled.FormatPaint, simpleTexts.paint, mode == EditMode.PAINT, { viewModel.chooseMode(EditMode.PAINT) })
            .let { if (kids) add(indexOfFirst { t -> t.id == "eraser" } + 1, it) else add(it) }
        // v47 Elemanlar, Katmanlar, Özellikler: dikey ekranda menünün içinde; yatayda sağdaki rayda (menüde yok)
        if (!panelRail) addAll(panelTools)
        // v40 basit resimde Seç'in yanında Taşı (Taşı seçiliyken Seç seçili görünmez)
        val simpleMove = kind == com.karapp.drawguidance.data.model.CanvasKind.SIMPLE_ART
        add(com.karapp.drawguidance.ui.screens.canvasedit.components.SimpleTool(
            "select", Icons.Filled.HighlightAlt, simpleTexts.select,
            mode == EditMode.SELECT || (mode.transformsSelection && !(simpleMove && mode == EditMode.MOVE)) || (mode == EditMode.SHAPE_EDIT && kind != com.karapp.drawguidance.data.model.CanvasKind.SIMPLE_ART),
            { viewModel.chooseMode(EditMode.SELECT) },
        ))
        // v48 Taşı yalnız bir eleman seçiliyken etkin
        if (simpleMove) add(com.karapp.drawguidance.ui.screens.canvasedit.components.SimpleTool(
            "move", Icons.Filled.OpenWith, simpleMoveLabel, mode == EditMode.MOVE, { viewModel.chooseMode(EditMode.MOVE) },
            enabled = com.karapp.drawguidance.ui.screens.canvasedit.components.MoveExpandRules.moveEnabled(hasSelection, mode == EditMode.MOVE),
        ))
        // v44 basit modlarda dikdörtgen seçim, serbest seçim ve damlalık üst menüde (Seç penceresinde değil)
        if (!kids) {
            add(com.karapp.drawguidance.ui.screens.canvasedit.components.SimpleTool("rect_select", com.karapp.drawguidance.ui.screens.canvasedit.components.V44Icons.RectSelect, rectSelectSimpleLabel, canvasPick == com.karapp.drawguidance.ui.screens.canvasedit.components.CanvasPick.RECT_SELECT, { viewModel.toggleCanvasPick(com.karapp.drawguidance.ui.screens.canvasedit.components.CanvasPick.RECT_SELECT) }))
// v48 basit resim ve basit notta serbest seçim (kement) yok; dikdörtgen seçim kaldı
        }
        add(com.karapp.drawguidance.ui.screens.canvasedit.components.SimpleTool("eyedropper", Icons.Filled.Colorize, eyedropperLabel, canvasPick == com.karapp.drawguidance.ui.screens.canvasedit.components.CanvasPick.EYEDROPPER, { viewModel.toggleCanvasPick(com.karapp.drawguidance.ui.screens.canvasedit.components.CanvasPick.EYEDROPPER) }))
        // v40 cetvel ve şekil kalıbı ayrı düğmeler (dokununca açılır, yeniden dokununca kapanır)
        add(com.karapp.drawguidance.ui.screens.canvasedit.components.SimpleTool("ruler", Icons.Filled.Straighten, rulerToolLabel, rulerSession == com.karapp.drawguidance.ui.screens.canvasedit.ruler.GuideSession.RULER, rulerActions.toggleRuler))
        add(com.karapp.drawguidance.ui.screens.canvasedit.components.SimpleTool("template", com.karapp.drawguidance.ui.screens.canvasedit.components.V44Icons.Template, templateToolLabel, rulerSession == com.karapp.drawguidance.ui.screens.canvasedit.ruler.GuideSession.TEMPLATE || rulerSession == com.karapp.drawguidance.ui.screens.canvasedit.ruler.GuideSession.TEMPLATE_PICK, rulerActions.toggleTemplate))
        // v37 basit çizimde (basit not dışında) çizim alanını genişletme: kenarlar sürüklenir; yeniden dokununca çizime dönülür
        if (kind != com.karapp.drawguidance.data.model.CanvasKind.SIMPLE_NOTES) {
            // v48 Genişlet yalnız tuval seçiliyken (eleman seçili değilken) etkin
            add(com.karapp.drawguidance.ui.screens.canvasedit.components.SimpleTool("expand", Icons.Filled.AspectRatio, simpleTexts.expand, mode == EditMode.EXPAND, {
                viewModel.chooseMode(if (mode == EditMode.EXPAND) EditMode.DRAW else EditMode.EXPAND)
            }, enabled = com.karapp.drawguidance.ui.screens.canvasedit.components.MoveExpandRules.expandEnabled(canvasTarget, mode == EditMode.EXPAND)))
        }
    }
    // v45 çocuk menüsü: çocuk adları ve simgeleri, kovanın önünde boya fırçası
    val kidsLabels = com.karapp.drawguidance.ui.screens.canvasedit.components.rememberKidsLabels()
    val paintBrushOn by viewModel.paintBrush.collectAsState()
    val simpleItems = com.karapp.drawguidance.ui.screens.canvasedit.components.exclusiveSimpleTools(
        when {
            // v47 çocuk menüsünde Kalem, Silgi, Boya ve Kova'nın hemen ardında Şekiller, Görev, Kalıp, Yazı
            kids -> com.karapp.drawguidance.ui.screens.canvasedit.components.KidsMenu.order(
                com.karapp.drawguidance.ui.screens.canvasedit.components.KidsMenu.decorate(simpleRaw, kidsLabels, paintBrushOn, mode == EditMode.PAINT, viewModel::choosePaintBrush),
            ) { it.id }
            kind.isNotes -> com.karapp.drawguidance.ui.screens.canvasedit.components.notesFirst(simpleRaw) { it.id }
            // v40 basit resimde Seç ve Taşı menünün 5. ve 6. düğmesi
            kind == com.karapp.drawguidance.data.model.CanvasKind.SIMPLE_ART -> com.karapp.drawguidance.ui.screens.canvasedit.components.SimpleToolOrder.placeSelectAndMove(simpleRaw) { it.id }
            else -> simpleRaw
        }, simpleLast, SIMPLE_OVERLAYS, independent = SIMPLE_PANEL_TOOLS,
    ) { id ->
        simpleLast = id
        if (id != "background") { backgroundOpen = false; simpleBackgroundOpen = false; backgroundEdit = false }
        if (id != "model3d") models3dOpen = false
        // v47 açılır alan Elemanlar, Katmanlar ve Özellikler arasında geçer; başka bir araç alanı kapatmaz (Özellikler
        // seçilen aracın ayarlarını gösterir)
    }
    val simpleRow: @Composable (Boolean) -> Unit = { fill ->
        com.karapp.drawguidance.ui.screens.canvasedit.components.ToolHelpScope(toolHelp) {
            // v45 çocuk modunda renkli, büyük düğmeli menü; v48 basit resim/notun dikey ekranında üç menü (adlar simgenin yanında)
            if (!kids && !landscape) com.karapp.drawguidance.ui.screens.canvasedit.components.SimpleMenuColumn(
                items = simpleItems, canUndo = canUndo, onUndo = viewModel::undo, canRedo = canRedo, onRedo = viewModel::redo,
            ) else if (kids) com.karapp.drawguidance.ui.screens.canvasedit.components.KidsToolRow(
                items = simpleItems, labels = kidsLabels, canUndo = canUndo, onUndo = viewModel::undo, canRedo = canRedo, onRedo = viewModel::redo,
                modifier = if (fill) Modifier.fillMaxWidth() else Modifier.widthIn(max = 640.dp),
            ) else com.karapp.drawguidance.ui.screens.canvasedit.components.SimpleToolRow(
                items = simpleItems, canUndo = canUndo, onUndo = viewModel::undo, canRedo = canRedo, onRedo = viewModel::redo,
                modifier = if (fill) Modifier.fillMaxWidth() else Modifier.widthIn(max = 560.dp), fillWidth = fill,
            )
        }
    }
    // Basit çizimde üst menü tek satır (modlar ve moda özel satırlar yok)
    val topRow: @Composable (Boolean) -> Unit = { fill -> if (simple) simpleRow(fill) else toolRow(fill) }

    // Seç modunda seçili elemana özel düğmeler (resim ve formül düzenleme)
    val editImageText = stringResource(R.string.image_edit_open)
    val editFormulaText = stringResource(R.string.formula_edit)
    val selectionKind = remember(canvas?.strokes, selectedIds) { viewModel.selectionKind(selectedIds) }
    val selected3d = remember(canvas?.strokes, selectedIds) { viewModel.selectedModel3d() }
    val inkColorText = stringResource(R.string.ink_color)
    val inkThinText = stringResource(R.string.ink_thinner)
    val inkThickText = stringResource(R.string.ink_thicker)
    val inkSmoothText = stringResource(R.string.ink_smooth)
    val inkCorrectText = stringResource(R.string.ink_correct)
    // v22 çoklu seçimde düzeltme (grup şablonu, yazı ve tüm çizimlerin tek tek düzeltilmesi)
    val inkCorrectMultiText = stringResource(R.string.ink_correct_multi)
    val multiInk = remember(canvas?.strokes, selectedIds) { viewModel.selectedInkCount(selectedIds) >= 2 }
    // v21: seçili fırça izinin düzeltme önerisi (öneriler açıksa ve fırçanın görünümünü bozmayacaksa)
    val canSuggestInk = remember(canvas?.strokes, selectedIds, suggestionsOn) { suggestionsOn && viewModel.canSuggestForSelection(selectedIds) }
    val balloonText = stringResource(R.string.balloon_edit_title)
    val selectedBalloonNow = remember(canvas?.strokes, selectedIds) { viewModel.selectedBalloon() }
    val selectedLinesNow = remember(canvas?.strokes, selectedIds) { viewModel.selectedLinesDensity() != null }
    val linesDensityText = stringResource(R.string.lines_density_action)
    // v48 çizilen efektlerin düzenleme bölümü kaldırıldı (eski belgelerdeki efektler seçilip taşınır, ölçeklenir)
    val selectionActions = buildList {
        // v27 seçili konuşma balonu: biçim/kişi sayısı/renk (kuyruk uçları tuvalde sürüklenir)
        if (selectedBalloonNow != null) add(com.karapp.drawguidance.ui.screens.canvasedit.components.ModeAction(balloonText, Icons.Filled.ChatBubble, { balloonEdit = true; balloonOpen = true }))
        // v29 seçili hız/odak çizgileri: sıklık
        if (selectedLinesNow) add(com.karapp.drawguidance.ui.screens.canvasedit.components.ModeAction(linesDensityText, Icons.Filled.Flare, {
            comicFxOpen = com.karapp.drawguidance.ui.screens.canvasedit.components.EffectsTab.LINES
        }))
        if (selectedImage != null) add(com.karapp.drawguidance.ui.screens.canvasedit.components.ModeAction(editImageText, Icons.Filled.Image, viewModel::editSelectedImage))
        if (selectedFormula != null) add(com.karapp.drawguidance.ui.screens.canvasedit.components.ModeAction(editFormulaText, Icons.Filled.Functions, { viewModel.editFormula() }))
        // v17 çizim (fırça izi) düzenleme: renk, incelt, kalınlaştır, yumuşat
        if (selectionKind == SelectionKind.INK) {
            add(com.karapp.drawguidance.ui.screens.canvasedit.components.ModeAction(inkColorText, Icons.Filled.Palette, { inkColorOpen = true }))
            add(com.karapp.drawguidance.ui.screens.canvasedit.components.ModeAction(inkThinText, Icons.Filled.Remove, { viewModel.scaleSelectedInkWidth(0.8f) }))
            add(com.karapp.drawguidance.ui.screens.canvasedit.components.ModeAction(inkThickText, Icons.Filled.Add, { viewModel.scaleSelectedInkWidth(1.25f) }))
            add(com.karapp.drawguidance.ui.screens.canvasedit.components.ModeAction(inkSmoothText, Icons.Filled.Waves, viewModel::smoothSelectedInk))
            if (canSuggestInk) add(com.karapp.drawguidance.ui.screens.canvasedit.components.ModeAction(if (multiInk) inkCorrectMultiText else inkCorrectText, Icons.Filled.AutoFixHigh, viewModel::suggestInkCorrection))
        }
        // v22: karışık çoklu seçimde (çizim + şekil/yazı…) de seçilen çizimler için düzeltme önerisi
        if (selectionKind == SelectionKind.MIXED && canSuggestInk) {
            add(com.karapp.drawguidance.ui.screens.canvasedit.components.ModeAction(inkCorrectMultiText, Icons.Filled.AutoFixHigh, viewModel::suggestInkCorrection))
        }
    }
    // Yeni modların düğmeleri tek yerde: her mod kendi düğme listesini verir, "Bitti" politikaya göre biter
    val doneText = stringResource(R.string.move_done)
    val simpleMoveText = stringResource(R.string.mode_move)
    val penUndoText = stringResource(R.string.pen_undo)
    val penFinishText = stringResource(R.string.pen_finish)
    val changeFormulaText = stringResource(R.string.formula_change)
    val partStyleText = stringResource(R.string.part_style)
    val partStyleTitle = stringResource(R.string.part_style_title)
    val cutText = stringResource(R.string.vector_cut)
    val deleteVectorText = stringResource(R.string.vector_delete)
    val canCutVector = remember(vectorSelection, selectedShape) { mode == EditMode.SHAPE_EDIT && viewModel.canCutVector() }
    val canDeleteVector = remember(vectorSelection, selectedShape) { mode == EditMode.SHAPE_EDIT && viewModel.canDeleteVector() }
    val simplifyText = stringResource(R.string.vector_simplify)
    val canSimplifyVector = remember(selectedShape, mode) { simple && mode == EditMode.SHAPE_EDIT && viewModel.canSimplifyVector() }
    val done = com.karapp.drawguidance.ui.screens.canvasedit.components.ModeAction(doneText, Icons.Filled.Check, viewModel::finishAction, primary = true)
    val modeActions: List<com.karapp.drawguidance.ui.screens.canvasedit.components.ModeAction>? = when (mode) {
        EditMode.FORMULA -> listOf(
            com.karapp.drawguidance.ui.screens.canvasedit.components.ModeAction(changeFormulaText, Icons.Filled.Functions, viewModel::openFormulaPicker), done,
        )
        // v16 şekil düzenleme: parça özellikleri, seçili noktaları kes / seçili noktayı ya da çizgiyi sil
        EditMode.SHAPE_EDIT -> listOfNotNull(
            // v36 basit düzenlemede Noktaları azalt (yazılı noktası çok olan şekillerde)
            if (simple) com.karapp.drawguidance.ui.screens.canvasedit.components.ModeAction(simplifyText, Icons.Filled.Polyline, viewModel::simplifyVector, enabled = canSimplifyVector) else null,
            com.karapp.drawguidance.ui.screens.canvasedit.components.ModeAction(partStyleText, Icons.Filled.Palette, { partStyleOpen = true }, description = partStyleTitle),
            com.karapp.drawguidance.ui.screens.canvasedit.components.ModeAction(cutText, Icons.Filled.ContentCut, viewModel::cutVector, enabled = canCutVector),
            com.karapp.drawguidance.ui.screens.canvasedit.components.ModeAction(deleteVectorText, Icons.Filled.Delete, viewModel::deleteVector, enabled = canDeleteVector, danger = true),
            done,
        )
        // v17: resim düzenleme araçları (Kırp, Vektörleştir, Kalem) üst menüde
        EditMode.IMAGE_EDIT -> listOf(done)
        EditMode.PEN -> listOf(
            com.karapp.drawguidance.ui.screens.canvasedit.components.ModeAction(penUndoText, Icons.AutoMirrored.Filled.Undo, viewModel::penUndo, enabled = penAnchors.isNotEmpty()),
            com.karapp.drawguidance.ui.screens.canvasedit.components.ModeAction(penFinishText, Icons.Filled.Polyline, { viewModel.penFinish(false) }, enabled = penAnchors.size >= 2),
            done,
        )
        // v33 Şekil boyama: renk satırı üstte, burada yalnız Bitti
        EditMode.PAINT -> listOf(done)
        // v33 basit çizimde seçim çubuğu (üstte Seç menüsü yok): taşı, kopyala, sil, bitti
        EditMode.SELECT -> if (!simple) null else listOf(
            com.karapp.drawguidance.ui.screens.canvasedit.components.ModeAction(simpleMoveText, Icons.Filled.OpenWith, { viewModel.chooseMode(EditMode.MOVE) }, enabled = hasSelection),
            com.karapp.drawguidance.ui.screens.canvasedit.components.ModeAction(duplicateText, Icons.Filled.ContentCopy, viewModel::duplicateSelected, enabled = hasSelection),
            com.karapp.drawguidance.ui.screens.canvasedit.components.ModeAction(deleteText, Icons.Filled.Delete, viewModel::deleteSelected, enabled = hasSelection, danger = true),
            done,
        )
        else -> null
    }
    // Moda özel ek ayarlar (mod çubuğunun üstünde)
    val graphParams = if (mode == EditMode.SHAPE_EDIT && shapeTool == ShapeTool.GRAPH) remember(selectedShape) { viewModel.selectedGraphParams() } else null
    val modeExtra: (@Composable () -> Unit)? = when {
        graphParams != null -> {
            {
                // v45 grafiğin formülü (her grafik türünde; hatada kırmızı ileti, son geçerli eğri kalır)
                com.karapp.drawguidance.ui.screens.canvasedit.components.GraphFormulaRow(graphParams, viewModel::updateSelectedGraph)
                com.karapp.drawguidance.ui.screens.canvasedit.components.GraphControls(graphParams, viewModel::updateSelectedGraph)
            }
        }
        // v45 parametreli şekil: değerler, formül, "Vektör düzenle"
        mode == EditMode.SHAPE_EDIT && shapeTool == ShapeTool.PARAMS -> {
            { com.karapp.drawguidance.ui.screens.canvasedit.components.ParamControls(viewModel, selectedShape) }
        }
        // v33 nokta paneli: Taşı / seç, Nokta ekle, Çizgi çiz (Noktalar aracının alt araçları)
        mode == EditMode.SHAPE_EDIT && shapeTool.nodeFamily -> {
            {
                com.karapp.drawguidance.ui.screens.canvasedit.components.NodeToolControls(
                    tool = shapeTool, onTool = viewModel::selectShapeTool,
                    icon = ::shapeToolIcon,
                )
            }
        }
        // v43 Seç: dokunarak, dikdörtgenle ya da kalemle alan çizerek seçim; damlalık
        // v33 Şekil boyama: renk seçimi (çocuklarda büyük palet)
        mode == EditMode.PAINT -> {
            {
                // v45 boya fırçasında üstte fırça boyu
                if (kids) com.karapp.drawguidance.ui.screens.canvasedit.components.KidsPaintBar(
                    selectedHex = regionColor ?: viewModel.regionInk(), onColor = viewModel::setRegionColor,
                    brushOn = paintBrushOn, sizePx = brush.sizePx, onSize = viewModel::updateBrushSize,
                )
                else com.karapp.drawguidance.ui.screens.canvasedit.components.RegionFillControls(
                    colorHex = regionColor, inkHex = viewModel.regionInk(),
                    onColor = viewModel::setRegionColor, onCustom = { regionColorOpen = true },
                )
            }
        }
        // v17 Alan boya: renk satırı
        mode == EditMode.SHAPE_EDIT && shapeTool == ShapeTool.FILL -> {
            {
                com.karapp.drawguidance.ui.screens.canvasedit.components.RegionFillControls(
                    colorHex = regionColor, inkHex = viewModel.regionInk(),
                    onColor = viewModel::setRegionColor, onCustom = { regionColorOpen = true },
                )
            }
        }
        // Çizgiler aracında seçili çizginin eğimi ve kavisi
        mode == EditMode.SHAPE_EDIT && shapeTool == ShapeTool.LINES && vectorSelection.edge != null -> {
            val angle = remember(vectorSelection, selectedShape) { viewModel.selectedEdgeAngle() }
            val curved = remember(vectorSelection, selectedShape) { viewModel.selectedEdgeCurved() }
            if (angle == null) null else {
                {
                    com.karapp.drawguidance.ui.screens.canvasedit.components.EdgeControls(
                        angle = angle, curved = curved,
                        onTurn = viewModel::turnSelectedEdge,
                        onAngle = viewModel::setSelectedEdgeAngle,
                        onStraighten = viewModel::straightenSelectedEdge,
                    )
                }
            }
        }
        else -> null
    }

    androidx.compose.runtime.CompositionLocalProvider(com.karapp.drawguidance.ui.screens.canvasedit.tutorial.LocalTutorialAnchors provides tutorialAnchors) {
    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            // v46 üst menü iki parça: yatay/geniş ekranda yan yana, telefonun dikey ekranında sığmazsa alt alta
            // v47 basit modlarda (çocuk dahil) dikeyde her zaman iki satır alt alta, yatayda tek satır dörtlü gruplar
            // v48 gelişmiş modların yatay ekranında üstteki iki menü tek menü (tek satır, sığmazsa yana kayar)
            val splitMode = when {
                simple -> com.karapp.drawguidance.ui.screens.canvasedit.components.PanelMenu.simpleSplit(landscape)
                landscape -> com.karapp.drawguidance.ui.screens.canvasedit.components.ToolRowSplitMode.NONE
                compactHeight || wide -> com.karapp.drawguidance.ui.screens.canvasedit.components.ToolRowSplitMode.SIDE
                else -> com.karapp.drawguidance.ui.screens.canvasedit.components.ToolRowSplitMode.STACK
            }
            androidx.compose.runtime.CompositionLocalProvider(com.karapp.drawguidance.ui.screens.canvasedit.components.LocalToolRowSplit provides splitMode) {
            Column(
                Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal))
                    .padding(bottom = 8.dp),
            ) {
                if (compactHeight) {
                    Row(
                        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        NeuIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), requestBack)
                        Column(Modifier.widthIn(max = 150.dp)) {
                            Text(
                                canvas?.name ?: "", style = MaterialTheme.typography.titleMedium, color = colors.content,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            styleLine()
                        }
                        // v38 yatay ekranda üst menü düğmelerinin adı sürekli yazar; menü kalan yere sığmazsa kaydırılır
                        // (⋮ ve kaydet her zaman görünür)
                        androidx.compose.runtime.CompositionLocalProvider(com.karapp.drawguidance.ui.components.LocalButtonLabels provides landscape) {
                            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { topRow(false) }
                            headerActions()
                            saveImageButton()
                        }
                    }
                } else if (wide) {
                    NeuTopBar(
                        title = canvas?.name ?: "",
                        subtitleContent = styleLine,
                        titleStyle = MaterialTheme.typography.titleMedium,
                        navigation = { NeuIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), requestBack) },
                        actions = {
                            // v45 gelişmiş modların dikey ekranında (tablet) düğme adları düğmenin içinde; tablette menü sürüklenir
                            androidx.compose.runtime.CompositionLocalProvider(
                                com.karapp.drawguidance.ui.components.LocalButtonLabels provides landscape,
                                com.karapp.drawguidance.ui.components.LocalInlineLabels provides (!landscape && !simple),
                            ) {
                                Row(Modifier.weight(3f, fill = false).tabletWindow(topMenuWindow), verticalAlignment = Alignment.CenterVertically) {
                                    topMenuWindow?.let { com.karapp.drawguidance.ui.screens.canvasedit.components.WindowDragHandle(it) }
                                    Box(contentAlignment = Alignment.CenterEnd) { topRow(false) }
                                }
                            }
                            androidx.compose.runtime.CompositionLocalProvider(com.karapp.drawguidance.ui.components.LocalButtonLabels provides landscape) {
                                headerActions(); saveImageButton()
                            }
                        },
                        modifier = Modifier.padding(vertical = 0.dp),
                    )
                } else {
                    NeuTopBar(
                        title = canvas?.name ?: "",
                        subtitleContent = styleLine,
                        navigation = { NeuIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), requestBack) },
                        actions = { headerActions(); saveImageButton() },
                    )
                    // v45 gelişmiş modların dikey ekranında üst menülerdeki düğmelerin adı düğmenin içinde (simgenin yanında)
                    androidx.compose.runtime.CompositionLocalProvider(com.karapp.drawguidance.ui.components.LocalInlineLabels provides !simple) {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp).tabletWindow(topMenuWindow),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            topMenuWindow?.let { com.karapp.drawguidance.ui.screens.canvasedit.components.WindowDragHandle(it, Modifier.align(Alignment.CenterHorizontally)) }
                            topRow(true)
                            if (!simple) com.karapp.drawguidance.ui.screens.canvasedit.components.ToolHelpScope(toolHelp) {
                                // v47 dikey ekranda Elemanlar, Katmanlar, Özellikler mod menüsünün son grubu
                                ModeSelector(
                                    mode, viewModel::chooseMode, Modifier.fillMaxWidth(), modes = com.karapp.drawguidance.ui.screens.canvasedit.components.modeToolbar(kind),
                                    extra = com.karapp.drawguidance.ui.screens.canvasedit.components.panelModeSlots(panel, viewModel::togglePanel, vertical = false),
                                )
                            }
                        }
                    }
                }
            }
            }
        },
    ) { padding ->
        val current = canvas ?: return@Scaffold
        Row(Modifier.fillMaxSize().padding(padding)) {
            if (wide && !simple) com.karapp.drawguidance.ui.screens.canvasedit.components.ToolHelpScope(toolHelp) {
                // v45 tablette mod rayı sürüklenir (tutamağı üstünde); dikey ekranda her modun adı yazar
                Column(
                    Modifier.padding(start = 12.dp, top = 4.dp, bottom = 12.dp).align(Alignment.CenterVertically).tabletWindow(modeMenuWindow),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    modeMenuWindow?.let { com.karapp.drawguidance.ui.screens.canvasedit.components.WindowDragHandle(it, vertical = true) }
                    androidx.compose.runtime.CompositionLocalProvider(com.karapp.drawguidance.ui.components.LocalInlineLabels provides !landscape) {
                        ModeSelector(
                            mode, viewModel::chooseMode, vertical = true, modes = com.karapp.drawguidance.ui.screens.canvasedit.components.modeToolbar(kind),
                            // v47 tabletin dikey ekranında açılır alanın düğmeleri mod rayının son grubu (yatayda sağdaki rayda)
                            extra = if (panelRail) emptyList() else com.karapp.drawguidance.ui.screens.canvasedit.components.panelModeSlots(panel, viewModel::togglePanel, vertical = true),
                        )
                    }
                }
            }
        Column(Modifier.weight(1f).fillMaxHeight()) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().then(Modifier.tutorialAnchor(com.karapp.drawguidance.ui.screens.canvasedit.tutorial.TutorialAnchor.CANVAS))) {
            // v47 yatayda sağdaki ray (Elemanlar, Katmanlar, Özellikler) alttaki mod çubuğunun ve yönerge çipinin
            // üstüne binmesin: alan kapalıyken onlar rayın genişliği kadar soldan kalır
            val railAvoid = if (panelRail && panel == null) Modifier.padding(end = 88.dp) else Modifier
            // Eleman kırpılırken eleman kırpmasız gösterilir: gizlenmiş kısım da görünür, geri açılabilir
            val shown = remember(current, mode, cropTarget) {
                if (mode != EditMode.CROP || cropTarget.isEmpty()) current
                else current.copy(
                    pages = current.pages.mapIndexed { i, page ->
                        if (i != current.currentPage) page else page.map { if (it.id in cropTarget) it.copy(clip = null) else it }
                    },
                )
            }
            DrawingCanvas(
                canvas = shown,
                brushSettings = brush,
                mode = mode,
                elements = elements,
                selectedIds = selectedIds,
                viewport = viewport,
                actions = actions,
                visualVersion = visualVersion,
                cropRect = cropRect,
                hiddenIds = remember(textDraft) { setOfNotNull(textDraft?.editingId) },
                cropArea = cropArea,
                diagramFocus = diagramFocus,
                diagramLinking = diagramLinking,
                shapeTool = shapeTool,
                diagramLink = diagramLink,
                overlay = com.karapp.drawguidance.ui.screens.canvasedit.components.CanvasOverlay(
                    penAnchors = penAnchors, penPending = penPending,
                    // Seçili formülün yuvaları Seç'te ve eklemeden sonraki Taşı'da vurgulu (dokununca üstünde düzenlenir)
                    formulaSlots = selectedFormula != null && (mode == EditMode.SELECT || mode == EditMode.MOVE || formulaEditing != null),
                    formulaSlot = formulaSlot.takeIf { formulaEditing != null },
                    vector = vectorSelection.takeIf { mode == EditMode.SHAPE_EDIT && shapeTool != ShapeTool.GRAPH && shapeTool != ShapeTool.FILL },
                    correction = (shapeSuggestion?.preview?.takeIf { it.isNotEmpty() } ?: lineSuggestion?.preview?.takeIf { it.isNotEmpty() })
                        ?.map { s -> s.map { Offset(it.x, it.y) } },
                ),
            )
            // v32 eğitimde çizilmekte olan çizgiler
            if (tutorialUi != null) com.karapp.drawguidance.ui.screens.canvasedit.tutorial.TutorialLiveLayer(tutorialLive, viewport)
            // v37 çocuk boyamasında kova imleci ve boyanmaya hazır alan
            if (kids && mode == EditMode.PAINT) com.karapp.drawguidance.ui.screens.canvasedit.components.KidsPaintOverlay(viewModel, viewport)
            // v45 parametreli şeklin tutamakları
            if (mode == EditMode.SHAPE_EDIT && shapeTool == ShapeTool.PARAMS) com.karapp.drawguidance.ui.screens.canvasedit.components.ParamHandlesLayer(viewModel, selectedShape, viewport)
            com.karapp.drawguidance.ui.screens.canvasedit.ruler.RulerLayer(viewModel, viewport, rulerActions, kind)
            // v40 arka plan resmini taşıma/boyutlandırma (mod değişince kapanır)
            LaunchedEffect(mode) { backgroundEdit = false; addTap = null }
            // v41 bir eleman seçilince arka planın seçimi kalkar
            LaunchedEffect(selectedIds) { if (selectedIds.isNotEmpty()) backgroundEdit = false }
            // v41 arka plan eleman gibi seçili: değiştir, örtücülük, tüm sayfalara, sil; resim varsa taşı/boyutlandır
            if (backgroundEdit) com.karapp.drawguidance.ui.screens.canvasedit.components.BackgroundTransformOverlay(
                shown, viewport, !shown.pageBackground.isEmpty, viewModel.backgroundTransform::preview,
                viewModel.backgroundTransform::commit, viewModel.backgroundTransform::restore, onDone = { backgroundEdit = false },
                transformable = viewModel.backgroundTransform.available(),
                onChange = { backgroundEdit = false; backgroundOpen = true },
                onRemove = viewModel::removeBackground,
                onApplyAll = if (shown.kind.paged && shown.pageCount > 1) viewModel::applyBackgroundToAllPages else null,
            )
            // v41 seçilen mekân arka planı: panele bırakılır ya da bütün sayfaya konur
            placingLocation?.let { id ->
                val b = remember(id) { com.karapp.drawguidance.data.comicassets.ComicAssets.background(id) }
                if (b == null) LaunchedEffect(id) { placingLocation = null }
                else com.karapp.drawguidance.ui.screens.canvasedit.components.LocationPlaceOverlay(
                    b, remember(shown.strokes) { viewModel.comicPanelRects() }, viewport,
                    onPlace = { leaf -> placingLocation = null; viewModel.insertLocationBackground(b, leaf) },
                    onCancel = { placingLocation = null },
                )
            }
            // v42 yol üzerinde yazı: yazının yerleşeceği kalem yolu seçilir
            textPathPick?.let {
                com.karapp.drawguidance.ui.screens.canvasedit.components.TextPathPickOverlay(
                    hosts = remember(shown.strokes) { shown.strokes.filter(com.karapp.drawguidance.ui.drawing.TextOnPath::canHost) },
                    viewport = viewport,
                    onPick = viewModel::pickTextPath,
                    onCancel = viewModel::cancelTextPathPick,
                )
            }
            // v45 boş yere dokunma (Ekle'de her dokunuş, Seç'te seçim yokken, iki parmakla boş yere basma): dikey ekleme
            // menüsü. Seçili elemanın köşesinde yatay seçim menüsü (Taşı, Ölçekle, Kopyala, Sil, Düzenle). Eski
            // bağlam menüsünün eleman türüne özel seçenekleri Düzenle'nin listesinde.
            val addAt: (String, Offset) -> Unit = { id, at ->
                viewModel.addHere(id, at)
                if (id == "image") {
                    if (kind.isPlainArt) pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    else imageSourceOpen = true
                }
                if (id == "model3d") models3dOpen = true
                if (id == "balloon") { balloonEdit = false; balloonOpen = true }
                if (id == "effects") comicFxOpen = com.karapp.drawguidance.ui.screens.canvasedit.components.EffectsTab.EFFECTS
                if (id == "characters") charactersOpen = true
                if (id == "locations") locationsOpen = true
                if (id == "objects") objectsOpen = true
                if (id == "packs") packsOpen = true
            }
            fun action(label: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, danger: Boolean = false, onClick: () -> Unit) =
                com.karapp.drawguidance.ui.screens.canvasedit.components.ModeAction(ctxString(label), icon, onClick, danger = danger)
            // v47 Ekle modunda dokunulan yere son seçilen eklenecek konur (seçilmediyse yönerge çipi üst menüyü gösterir)
            addTap?.let { at ->
                LaunchedEffect(at) {
                    addTap = null
                    val id = lastAddItem
                    if (id != null && id in ADD_ANCHORED) addAt(id, at)
                }
            }
            if (!kids && mode == EditMode.SELECT && selectedIds.isNotEmpty() && textDraft == null && formulaEditing == null) {
                val orderItems = listOf(
                    action(R.string.order_forward, Icons.Filled.FlipToFront) { viewModel.reorderSelected(com.karapp.drawguidance.ui.screens.canvasedit.editor.ZOrder.FORWARD) },
                    action(R.string.order_backward, Icons.Filled.FlipToBack) { viewModel.reorderSelected(com.karapp.drawguidance.ui.screens.canvasedit.editor.ZOrder.BACKWARD) },
                    action(R.string.hide_elements, Icons.Filled.VisibilityOff) { requestHide() },
                )
                // Düzenle listesi: seçimin türüne özel işlemler (eski bağlam menüsündekiler) ve sıra/gizle
                val editItems = when (selectionKind) {
                    SelectionKind.NONE -> emptyList()
                    SelectionKind.TEXT -> listOfNotNull(
                        action(R.string.edit_text, Icons.Filled.Edit) { viewModel.editSelectedText() },
                        // v27 konuşma balonu: biçim, kişi sayısı ve renkler
                        if (selectedBalloonNow != null) action(R.string.balloon_edit_title, Icons.Filled.ChatBubble) { balloonEdit = true; balloonOpen = true } else null,
                        // v47 seçili yazının biçimi Elemanlar'da (Özellikler yalnız yeni yazının biçimi)
                        action(R.string.text_settings, Icons.Filled.Settings) { if (panel != PanelTab.ELEMENTS) viewModel.togglePanel(PanelTab.ELEMENTS) },
                        action(R.string.text_color, Icons.Filled.Palette) { colorSheet = "text" },
                    ) + textPathActions()
                    SelectionKind.SHAPE -> listOfNotNull(
                        // v36 çizgi roman paneli: Şablonlar penceresi bu panel seçili açılır (v45 Düzenle'nin başında)
                        selectedPanelLeaf?.let { leaf -> action(R.string.templates_edit_panel, Icons.Filled.AutoAwesomeMosaic) { templatesLeaf = leaf; templatesOpen = true } },
                        action(R.string.shape_edit_open, Icons.Filled.Edit) { viewModel.editSelectedShape() },
                        // v23 not tuvalinde şekilden ok çıkar
                        if (notes) action(R.string.arrow_from_shape, Icons.AutoMirrored.Filled.ArrowRightAlt) { viewModel.arrowFromSelection() } else null,
                        action(R.string.region_fill_open, Icons.Filled.FormatColorFill) { viewModel.editShapeRegions() },
                        action(R.string.shape_fill_color, Icons.Filled.Palette) { fillInitial = viewModel.currentFillColor() },
                        if (selectedLinesNow) action(R.string.lines_density_action, Icons.Filled.Flare) { comicFxOpen = com.karapp.drawguidance.ui.screens.canvasedit.components.EffectsTab.LINES } else null,
                        action(R.string.mode_rotate, Icons.AutoMirrored.Filled.RotateRight) { viewModel.chooseMode(EditMode.ROTATE) },
                    )
                    SelectionKind.INK -> listOfNotNull(
                        action(R.string.ink_color, Icons.Filled.Palette) { colorSheet = "ink" },
                        action(R.string.ink_thinner_long, Icons.Filled.Remove) { viewModel.scaleSelectedInkWidth(0.8f) },
                        action(R.string.ink_thicker_long, Icons.Filled.Add) { viewModel.scaleSelectedInkWidth(1.25f) },
                        action(R.string.ink_smooth, Icons.Filled.Waves) { viewModel.smoothSelectedInk() },
                        // v21: çizim önerileri açıksa ve fırçanın görünümünü bozmayacaksa
                        if (canSuggestInk) action(if (multiInk) R.string.ink_correct_multi else R.string.ink_correct, Icons.Filled.AutoFixHigh) { viewModel.suggestInkCorrection() } else null,
                        // v18: eski komut kutusundaki "boşlukları doldur"
                        action(R.string.fill_gaps, Icons.Filled.Link) { viewModel.fillGaps() },
                        action(R.string.mode_rotate, Icons.AutoMirrored.Filled.RotateRight) { viewModel.chooseMode(EditMode.ROTATE) },
                    )
                    SelectionKind.FORMULA -> listOf(
                        action(R.string.formula_edit_slots, Icons.Filled.Edit) { viewModel.editFormula() },
                        action(R.string.formula_write, Icons.Filled.Functions) { viewModel.editFormulaSource() },
                    )
                    SelectionKind.IMAGE -> listOfNotNull(
                        action(R.string.image_edit_open, Icons.Filled.Image) { viewModel.editSelectedImage() },
                        // v23 tuvaldeki 3B şekil: düzenleyici ayrı pencerede (önce kaydetme sorulur)
                        if (selected3d != null) action(R.string.model3d_edit, Icons.Filled.ViewInAr) { edit3dAsk = selected3d } else null,
                        action(R.string.image_vectorize, Icons.Filled.Polyline) { viewModel.editSelectedImage(); vectorizeMode = null; vectorizeOpen = true },
                        // v30 arka plan kaldırma ve efektler (3B şekil resminde yok)
                        if (selected3d == null) action(R.string.bg_remove, com.karapp.drawguidance.ui.screens.canvasedit.components.BackgroundRemoveIcon) { viewModel.editSelectedImage(); bgRemoveOpen = true } else null,
                        if (selected3d == null) action(R.string.image_effects, com.karapp.drawguidance.ui.screens.canvasedit.components.ImageEffectsIcon) { viewModel.editSelectedImage(); effectsOpen = true } else null,
                        action(R.string.mode_crop, Icons.Filled.Crop) { viewModel.startAction(EditMode.CROP) },
                        action(R.string.mode_rotate, Icons.AutoMirrored.Filled.RotateRight) { viewModel.chooseMode(EditMode.ROTATE) },
                    )
                    SelectionKind.DIAGRAM -> listOf(
                        action(R.string.diagram_edit, Icons.Filled.Edit) { viewModel.editSelectedDiagram() },
                        action(R.string.diagram_theme, Icons.Filled.Palette) { diagramThemeOpen = true },
                    )
                    SelectionKind.MIXED -> listOfNotNull(
                        action(R.string.group_selection, Icons.Filled.Workspaces) { viewModel.groupSelected() },
                        if (canSuggestInk) action(R.string.ink_correct_multi, Icons.Filled.AutoFixHigh) { viewModel.suggestInkCorrection() } else null,
                        action(R.string.fill_gaps, Icons.Filled.Link) { viewModel.fillGaps() },
                        action(R.string.mode_rotate, Icons.AutoMirrored.Filled.RotateRight) { viewModel.chooseMode(EditMode.ROTATE) },
                    )
                } + orderItems + action(R.string.clear_selection, Icons.Filled.Close) { viewModel.clearSelection() }
                val selectionBox = remember(current.strokes, selectedIds) {
                    com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementOps.selectionBounds(current.strokes, selectedIds)
                }
                com.karapp.drawguidance.ui.screens.canvasedit.components.SelectionMenu(
                    anchor = { selectionBox?.let { b -> androidx.compose.ui.geometry.Rect(viewport.toScreen(b.topLeft), viewport.toScreen(b.bottomRight)) } },
                    actions = com.karapp.drawguidance.ui.screens.canvasedit.components.selectionMenuActions(
                        onMove = { viewModel.chooseMode(EditMode.MOVE) }, onScale = { viewModel.chooseMode(EditMode.SCALE) },
                        onCopy = viewModel::duplicateSelected, onDelete = viewModel::deleteSelected,
                    ),
                    editItems = editItems,
                    resetKey = selectedIds,
                    bottomInset = { modeBarHeightPx },
                    openEdit = contextEdit,
                    onEditOpened = { contextEdit = false },
                )
            }
            // v23 3B şeklin resmi hazırlanırken
            val busy3d by viewModel.model3dBusy.collectAsState()
            if (busy3d) {
                Text(
                    stringResource(R.string.model3d_inserting), style = MaterialTheme.typography.labelMedium, color = colors.onAccent,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp).background(colors.accent, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            // Yakınlık göstergesi: dokununca kağıt ekrana sığar
            com.karapp.drawguidance.ui.screens.canvasedit.components.ToolHelpScope(toolHelp) {
            com.karapp.drawguidance.ui.screens.canvasedit.components.ZoomChip(
                zoomPercent = viewport.zoomPercent,
                onFit = viewport::fit,
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 16.dp, top = 4.dp),
            )
            }
            // v17 temel modlarda kısa yönerge: Seç (seçim yokken), Ekle, Düzenle
            val baseHint = when {
                textDraft != null || formulaEditing != null -> null
                mode == EditMode.SELECT && selectedIds.isEmpty() -> Icons.Filled.TouchApp to R.string.hint_select_empty
                mode == EditMode.ADD -> Icons.Filled.AddCircleOutline to R.string.hint_add
                mode == EditMode.EDIT_PICK && pendingSceneTool != null -> (if (pendingSceneTool == CanvasEditViewModel.SCENE_PLACE) com.karapp.drawguidance.ui.screens.canvasedit.components.V44Icons.PlaceInScene else com.karapp.drawguidance.ui.screens.canvasedit.components.V44Icons.Harmonize) to R.string.v43_pick_object
                mode == EditMode.EDIT_PICK -> Icons.Filled.Edit to R.string.hint_edit_pick
                else -> null
            }
            if (baseHint != null) {
                com.karapp.drawguidance.ui.screens.canvasedit.components.ModeHintChip(
                    baseHint.first, stringResource(baseHint.second),
                    modifier = Modifier.align(Alignment.BottomCenter).then(railAvoid).navigationBarsPadding().padding(16.dp),
                )
            }
            // Fırça göstergesi yalnızca çizerken (ekleme/düzenleme modlarında kalem pasif); altında v18 kalem modu çipi
            // (kalem kullanılınca parmak kaydırır; dokununca parmakla çizim açılır/kapanır)
            val stylus by com.karapp.drawguidance.platform.Stylus.state.collectAsState()
            val fingerPans by com.karapp.drawguidance.platform.Stylus.fingerPans.collectAsState()
            if (mode == EditMode.DRAW) com.karapp.drawguidance.ui.screens.canvasedit.components.ToolHelpScope(toolHelp) {
                Column(
                    Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 4.dp, end = 104.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BrushIndicator(
                        brushType = brush.brushType,
                        colorHex = brush.colorHex,
                        sizePx = brush.sizePx,
                        onClick = viewModel::openBrushPicker,
                    )
                    if (stylus.used) {
                        com.karapp.drawguidance.ui.screens.canvasedit.components.ToolInfoChip(
                            text = stringResource(if (fingerPans) R.string.pen_mode_finger_pans else R.string.pen_mode_finger_draws),
                            description = stringResource(R.string.pen_mode_toggle),
                            onClick = { com.karapp.drawguidance.platform.Stylus.set(com.karapp.drawguidance.platform.Stylus.fingerPans, !fingerPans) },
                            icon = Icons.Filled.Draw,
                            modifier = Modifier.testTag("pen_mode_chip"),
                        )
                    }
                }
            }
            infoChips?.let { chips -> com.karapp.drawguidance.ui.screens.canvasedit.components.ToolHelpScope(toolHelp) {
                Row(
                    // v47 Taşı ve Genişlet'in çipleri birlikte: dar ekranda yana kayar
                    Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 4.dp, end = 104.dp).then(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState())),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) { chips() }
            } }

            androidx.compose.animation.AnimatedVisibility(
                // Kılavuz/perspektif ve tuval üstü diyagram düzenlemenin kendi çubukları var
                // v16: yazı yazılırken mod çubuğu gizlenir (alttaki alçak yazı çubuğu tek başına durur)
                // v17: Seç'te yalnızca seçim varken; Ekle ve Düzenle'de yönerge çipi yeter
                visible = mode != EditMode.DRAW && mode != EditMode.GUIDE && mode != EditMode.PERSPECTIVE && textDraft == null &&
                    mode != EditMode.ADD && mode != EditMode.EDIT_PICK && !(mode == EditMode.SELECT && selectedIds.isEmpty()),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter).then(railAvoid)
                    // Tuval üstü paneller (diyagram adımı) alttaki çubuğun altında kalmasın diye yüksekliği izlenir
                    .onSizeChanged { modeBarHeightPx = it.height }
                    .then(if (kids) Modifier.tutorialAnchor(com.karapp.drawguidance.ui.screens.canvasedit.tutorial.TutorialAnchor.MODE_BAR) else Modifier)
                    .navigationBarsPadding().padding(16.dp).widthIn(max = if (compactHeight) 780.dp else 600.dp),
            ) {
                com.karapp.drawguidance.ui.screens.canvasedit.components.ToolHelpScope(toolHelp) {
                ModeBar(
                    mode = mode,
                    selectedCount = selectedElementCount,
                    canvasWidth = current.width,
                    canvasHeight = current.height,
                    zoomPercent = viewport.zoomPercent,
                    onModeChange = viewModel::chooseMode,
                    onDone = viewModel::finishAction,
                    onClearSelection = viewModel::clearSelection,
                    onRotateBy = { viewModel.rotateSelected(it) },
                    onScaleBy = { viewModel.scaleSelected(it, it) },
                    onZoomStep = viewport::zoomStep,
                    onFit = viewport::fit,
                    cropWidth = cropRect?.width?.toInt() ?: current.width,
                    cropHeight = cropRect?.height?.toInt() ?: current.height,
                    shapeName = com.karapp.drawguidance.ui.screens.canvasedit.components.shapeTitle(shape),
                    selectedTextCount = selectedTexts.size,
                    onApplyCrop = { viewModel.applyCrop()?.let { viewport.shiftForResize(it.x, it.y) } },
                    onResetCrop = viewModel::resetCrop,
                    onChangeShape = viewModel::openShapePicker,
                    onTextSettings = {
                        // v47 yazı aracında yeni yazının biçimi Özellikler'de, seçili yazınınki Elemanlar'da
                        val tab = if (mode == EditMode.TEXT && selectedTexts.isEmpty()) PanelTab.BRUSH else PanelTab.ELEMENTS
                        if (panel != tab) viewModel.togglePanel(tab)
                    },
                    onEditText = viewModel::editSelectedText,
                    onBackground = { fillInitial = viewModel.currentFillColor() },
                    shapeTool = shapeTool,
                    onShapeTool = viewModel::selectShapeTool,
                    diagramName = com.karapp.drawguidance.ui.screens.canvasedit.components.diagramLabel(diagramKind),
                    onChangeDiagram = viewModel::openDiagramPicker,
                    canEditShape = selectedShape != null,
                    onEditShape = viewModel::editSelectedShape,
                    canEditDiagram = selectedDiagram != null,
                    onEditDiagram = viewModel::editSelectedDiagram,
                    cropElement = cropTarget.isNotEmpty(),
                    diagramLinking = diagramLinking,
                    onDiagramAddStep = viewModel::diagramAddStep,
                    onDiagramLink = viewModel::diagramStartLink,
                    diagramLinkNeedsSource = diagramLink != null && diagramLink?.fromId == null,
                    onDeleteSelection = viewModel::deleteSelected,
                    onDuplicateSelection = viewModel::duplicateSelected,
                    compact = compactHeight,
                    vectorLinking = vectorSelection.linkFrom != null,
                    // Grafikte Grafik aracı başta (açılışta seçili olan araç görünür)
                    shapeTools = shapeToolList,
                    onPartStyle = { partStyleOpen = true },
                    selectionActions = selectionActions,
                    title = when (mode) {
                        EditMode.FORMULA -> formulaTemplate?.let { "${modeLabelText(mode)}: ${com.karapp.drawguidance.i18n.I18n.tr(it.title)}" }
                        EditMode.SHAPE -> if (arrowFromBox != null) stringResource(R.string.mode_hint_arrow_from) else null
                        else -> null
                    },
                    extra = modeExtra,
                    actions = modeActions,
                    onDiagramTheme = { diagramThemeOpen = true },
                )
                }
            }

            // v31 öneri balonları üstteki göstergelerden ve mod çubuğundan sonra çizilir (dokunuşları onlar yutmaz).
            // v23 yazı önerisi ve şekil önerisi ayrı balonlarda: yazının/şeklin hemen üstünde (ikisi birden varsa şekil
            // balonu bir sıra yukarıda)
            val density = androidx.compose.ui.platform.LocalDensity.current
            val lift = with(density) { 52.dp.toPx() }
            // v31 üstte yakınlık göstergesi ve fırça çipi için ayrılan şerit
            val topReserve = with(density) { 46.dp.roundToPx() }
            // v28 balonlar tutamaktan sürüklenebilir: sürüklenen yer (alana göre oran) hatırlanır; çift dokunuş geri alır
            val pin = suggestPin
            fun balloonAt(bounds: androidx.compose.ui.geometry.Rect, extra: Float) = Modifier.layout { measurable, c ->
                val anchor = viewport.toScreen(Offset(bounds.left, bounds.top))
                val p = measurable.measure(c.copy(minWidth = 0, minHeight = 0))
                layout(p.width, p.height) {
                    if (pin != null) {
                        // Sabitlenmiş yer: balonlar oradan aşağı doğru sıralanır
                        val x = (pin.first * c.maxWidth).toInt().coerceAtMost(c.maxWidth - p.width).coerceAtLeast(0)
                        val y = (pin.second * c.maxHeight + extra).toInt().coerceAtMost(c.maxHeight - modeBarHeightPx - p.height).coerceAtLeast(0)
                        p.place(x, y)
                    } else {
                        // v20: balon ekrana sığacak kadar sola kayar (kapatma düğmeleri ekran dışında kalmasın)
                        // v31 balon yakınlık göstergesinin (sağ üst) ve alttaki mod çubuğunun altında kalmaz: oraya
                        // dokunulunca seçenek değil üstteki düğme çalışıyordu ("Tümünü düzelt" bir şey yapmıyor sanılıyordu)
                        val bottom = (c.maxHeight - modeBarHeightPx - p.height).coerceAtLeast(topReserve)
                        p.place(anchor.x.toInt().coerceAtMost(c.maxWidth - p.width).coerceAtLeast(0), (anchor.y - lift - extra).toInt().coerceIn(topReserve, bottom))
                    }
                }
            }
            fun dragFor(id: String, extra: Float) = com.karapp.drawguidance.ui.screens.canvasedit.components.SuggestDrag(
                onDrag = { d ->
                    val w = constraints.maxWidth.toFloat().coerceAtLeast(1f); val h = constraints.maxHeight.toFloat().coerceAtLeast(1f)
                    val base = suggestPin?.let { Offset(it.first * w, it.second * h) }
                        ?: balloonPositions[id]?.let { Offset(it.x, it.y - extra) } ?: Offset(w * 0.05f, h * 0.2f)
                    val np = base + d
                    suggestPin = (np.x / w).coerceIn(0f, 0.95f) to (np.y / h).coerceIn(0f, 0.95f)
                },
                onEnd = { viewModel.suggestPin = suggestPin },
                onReset = { suggestPin = null; viewModel.suggestPin = null },
            )
            fun tracked(id: String) = Modifier.onGloballyPositioned { balloonPositions[id] = it.positionInParent() }
            textSuggestion?.takeIf { !kids }?.let { suggestion ->
                com.karapp.drawguidance.ui.screens.canvasedit.components.TextSuggestionChip(
                    suggestion = suggestion,
                    onAccept = viewModel::acceptHandwriting,
                    onDismiss = viewModel::dismissTextSuggestion,
                    onTurnOff = viewModel::turnOffTextSuggestions,
                    modifier = balloonAt(suggestion.bounds, 0f).then(tracked("text")),
                    drag = dragFor("text", 0f),
                )
            }
            // Sabitlenmiş yerde balonlar aşağı doğru, kendiliğinden yerde yukarı doğru sıralanır
            val shapeExtra = if (textSuggestion != null) (if (pin != null) lift * 1.1f else lift) else 0f
            val lineExtra = when {
                shapeSuggestion == null -> shapeExtra.takeIf { textSuggestion != null } ?: 0f
                pin != null -> shapeExtra + lift * 1.1f
                else -> shapeExtra + lift
            }
            shapeSuggestion?.takeIf { !kids }?.let { suggestion ->
                com.karapp.drawguidance.ui.screens.canvasedit.components.ShapeSuggestionChip(
                    suggestion = suggestion,
                    onAcceptTemplate = viewModel::acceptTemplate,
                    onDismiss = viewModel::dismissShapeSuggestion,
                    onTurnOff = viewModel::turnOffShapeSuggestions,
                    modifier = balloonAt(suggestion.bounds, shapeExtra).then(tracked("shape")),
                    drag = dragFor("shape", shapeExtra),
                )
            }
            // v38 çizgi önerisi (2+ çizgi; tek düğmeyle hepsi)
            lineSuggestion?.takeIf { !kids }?.let { suggestion ->
                com.karapp.drawguidance.ui.screens.canvasedit.components.LineSuggestionChip(
                    suggestion = suggestion,
                    onAccept = viewModel::acceptLines,
                    onDismiss = viewModel::dismissLineSuggestion,
                    onTurnOff = viewModel::turnOffLineSuggestions,
                    modifier = balloonAt(suggestion.bounds, lineExtra).then(tracked("line")),
                    drag = dragFor("line", lineExtra),
                )
            }
            // v37 çocuk tuvalinde öneriler maskotun balonunda (maskot isteğe göre köşede sürekli durur)
            if (kids) com.karapp.drawguidance.ui.screens.canvasedit.components.KidsMascotLayer(viewModel, mode, modeBarHeightPx, tutorialUi != null)
            if (kids) com.karapp.drawguidance.ui.screens.canvasedit.challenge.KidsChallengeLayer(viewModel, challengeUi, viewport, mode, modeBarHeightPx, tutorialUi != null)

            // Tuval üstü diyagram düzenleme: odaktaki adımın/okun yanında küçük panel
            if (mode == EditMode.DIAGRAM_EDIT) {
                val focus = diagramFocus
                val d = selectedDiagram
                val spec = d?.diagram
                if (focus != null && d != null && spec != null && !diagramLinking) {
                    val node = (focus as? DiagramFocus.Node)?.let { f -> spec.nodes.firstOrNull { it.id == f.id } }
                    val edge = (focus as? DiagramFocus.Edge)?.let { f -> spec.edges.getOrNull(f.index) }
                    val anchor: androidx.compose.ui.geometry.Rect? = when {
                        node != null -> com.karapp.drawguidance.ui.drawing.DiagramRenderer.nodeCorners(d, node)
                            .map { viewport.toScreen(it) }.takeIf { it.size == 4 }?.let { c ->
                                androidx.compose.ui.geometry.Rect(c.minOf { it.x }, c.minOf { it.y }, c.maxOf { it.x }, c.maxOf { it.y })
                            }
                        edge != null -> {
                            val a = spec.nodes.firstOrNull { it.id == edge.from }
                            val b = spec.nodes.firstOrNull { it.id == edge.to }
                            if (a == null || b == null) null else {
                                // Panel okun çizilen yolunun ortasına yerleşir (kırık oklarda da)
                                val path = com.karapp.drawguidance.ui.drawing.DiagramRenderer.edgePath(d, edge, a, b)
                                polylineMidpoint(path)?.let { mid ->
                                    val m = viewport.toScreen(mid)
                                    androidx.compose.ui.geometry.Rect(m.x - 1f, m.y - 1f, m.x + 1f, m.y + 1f)
                                }
                            }
                        }
                        else -> null
                    }
                    if (anchor != null) {
                        com.karapp.drawguidance.ui.screens.canvasedit.components.DiagramFocusPanel(
                            anchor = anchor,
                            // v47 yatayda sağdaki rayın altında kalmasın
                            areaWidthPx = constraints.maxWidth.toFloat() - (if (panelRail && panel == null) with(androidx.compose.ui.platform.LocalDensity.current) { 88.dp.toPx() } else 0f),
                            areaHeightPx = (constraints.maxHeight - modeBarHeightPx).toFloat().coerceAtLeast(0f),
                            fullHeightPx = constraints.maxHeight.toFloat(),
                            focusKey = focus,
                            text = node?.text ?: edge?.label.orEmpty(),
                            isNode = node != null,
                            box = node?.box,
                            linking = diagramLinking,
                            onText = viewModel::diagramRenameFocused,
                            onAddStep = viewModel::diagramAddStep,
                            onLink = viewModel::diagramStartLink,
                            onBox = viewModel::diagramSetFocusedBox,
                            onDelete = viewModel::diagramDeleteFocused,
                            // Vurgu (odak) düğmesi: yerleşim rolü olan adımlarda (aşama, satır…) yok
                            focal = node?.let { n -> if (com.karapp.drawguidance.ui.drawing.DiagramCatalog.isStructural(n.role)) null else n.role == "focal" },
                            onFocal = viewModel::diagramToggleFocal,
                            dataHint = node != null && com.karapp.drawguidance.ui.drawing.DiagramCatalog.isData(spec.kind),
                            fromSide = edge?.fromSide ?: com.karapp.drawguidance.data.model.DiagramSide.AUTO,
                            toSide = edge?.toSide ?: com.karapp.drawguidance.data.model.DiagramSide.AUTO,
                            onSides = { f, t -> viewModel.diagramSetEdgeSides(f, t) },
                            onFlip = viewModel::diagramFlipEdge,
                            onColor = {
                                diagramColorPart = if (node != null) com.karapp.drawguidance.ui.screens.canvasedit.state.DiagramController.ColorPart.FILL
                                else com.karapp.drawguidance.ui.screens.canvasedit.state.DiagramController.ColorPart.STROKE
                            },
                        )
                    }
                }
            }

            // Tuvalin içinde yazma alanı (pencere açılmaz): noktalı çerçeve + altta geçici font çubuğu
            textDraft?.let { draft ->
                val editingColor = draft.editingId?.let { id -> current.strokes.firstOrNull { it.id == id }?.colorHex }
                val topLeft = if (draft.editingId != null) draft.anchor else Offset(draft.anchor.x, draft.anchor.y - textSettings.sizePx * 0.7f)
                key(draft.editingId, draft.anchor) {
                    run {
                        InlineTextEditor(
                            draft = draft,
                            settings = textSettings,
                            colorHex = editingColor ?: if (brush.brushType == BrushType.ERASER) "#1E1E1E" else brush.colorHex,
                            anchorScreen = viewport.toScreen(topLeft),
                            scale = viewport.scale,
                            onSettingsChange = { viewModel.updateTextSettings(it) },
                            // v29 düzenlerken de yazı biçimi: harf rengi, kenarlık, efektler, kutu
                            onColor = viewModel::setTextColor,
                            onBoxSize = if (draft.editingId != null) viewModel::setTextBoxSize else null,
                            onConfirm = viewModel::confirmText,
                            onCancel = viewModel::cancelText,
                            onTextChange = viewModel::updateDraftText,
                            notes = kind.isNotes,
                            // Yazı alanı alttaki ayar çubuğunun altında kalırsa görünüm yukarı kayar
                            onAvoid = { dy -> viewport.panBy(Offset(0f, -dy)); viewport.settle() },
                            paperHex = current.pagePaperHex,
                            // v28 yazarken yazı kendi yerinde ve biçimiyle görünür
                            preview = viewModel::textPreview,
                            canvasOrigin = viewport.toScreen(Offset.Zero),
                            onMove = viewModel::moveTextDraft,
                            onParagraphBox = viewModel::setTextParagraphBox,
                            // v42 yol üzerinde yazı (kalem aracıyla çizilmiş yola)
                            onTextOnPath = viewModel::startTextOnPath,
                            textOnPathEnabled = remember(current.strokes) { viewModel.hasTextPathHosts() },
                        )
                    }
                }
            }

            // v16: formül formülün üstünde düzenlenir (katsayı/bilinmeyen yuvasının üstünde ya da formülün altında)
            val editingFormula = selectedFormula?.takeIf { it.id == formulaEditing }
            if (editingFormula != null && (mode == EditMode.SELECT || mode == EditMode.MOVE)) {
                key(editingFormula.id, formulaSlot) {
                    run {
                        FormulaInlineEditor(
                            formula = editingFormula,
                            slot = formulaSlot,
                            viewport = viewport,
                            areaWidthPx = constraints.maxWidth.toFloat() - (if (panelRail && panel == null) with(androidx.compose.ui.platform.LocalDensity.current) { 88.dp.toPx() } else 0f),
                            areaHeightPx = (constraints.maxHeight - modeBarHeightPx).toFloat().coerceAtLeast(constraints.maxHeight * 0.5f),
                            onSlot = viewModel::setFormulaSlot,
                            onSource = viewModel::setFormulaSource,
                            onNext = viewModel::nextFormulaSlot,
                            onWhole = viewModel::editFormulaSource,
                            onDone = viewModel::closeFormulaEditor,
                        )
                    }
                }
                // v45 hesaplanamayan formülün iletisi (sıfıra bölme, tanım dışı)
                com.karapp.drawguidance.ui.screens.canvasedit.components.FormulaEvalNotice(viewModel)
            }

            val panelWidth = minOf(maxWidth * 0.9f, 360.dp)
            // v43 yatay/geniş ekranda panel (Elemanlar, Katmanlar, Fırça) başlığından sürüklenir; bırakınca en yakın
            // kenara (sol ya da sağ) yerleşir ve orada sabit kalır
            var panelLeftSaved by rememberSaveable { mutableStateOf(false) }
            // v47 yatay ekranda alan hep sağda, solunda Elemanlar/Katmanlar/Özellikler rayı (sürüklenmez)
            val panelLeft = panelLeftSaved && !panelRail
            val panelWindow = sidePanelWindow.takeIf { !panelRail }
            val panelDrag = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
            val areaWidthPx = constraints.maxWidth.toFloat()
            val panelWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { panelWidth.toPx() }
            // v47 yatay ekranda sağda Elemanlar, Katmanlar, Özellikler rayı: alan açıkken onun soluna yapışık; dokunulan
            // düğme alanın içeriğini değiştirir (açık olanın düğmesi alanı kapatır)
            if (panelRail) {
                // Açılır alan açıkken ray alanın görünen kenarına yapışık (alanın 12 dp iç payının içine girer)
                val railEnd by androidx.compose.animation.core.animateDpAsState(if (panel != null) panelWidth - 10.dp else 10.dp, label = "panel_rail")
                com.karapp.drawguidance.ui.screens.canvasedit.components.ToolHelpScope(toolHelp) {
                    com.karapp.drawguidance.ui.screens.canvasedit.components.PanelRail(
                        panel, viewModel::togglePanel,
                        Modifier.align(Alignment.CenterEnd).padding(end = railEnd),
                    )
                }
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = panel != null,
                // v27 yalnız kayma (saydamlık geçişi tüm paneli her karede ekran dışı katmanda çizdiriyordu)
                enter = slideInHorizontally { if (panelLeft) -it else it },
                exit = slideOutHorizontally { if (panelLeft) -it else it },
                modifier = Modifier.align(if (panelLeft) Alignment.CenterStart else Alignment.CenterEnd)
                    // v45 tablette panel serbestçe sürüklenir (ekranın içinde; konumu saklanır); telefonda kenara yerleşir
                    .then(if (panelWindow != null) Modifier.tabletWindow(panelWindow) else Modifier.offset { androidx.compose.ui.unit.IntOffset(panelDrag.floatValue.toInt(), 0) })
                    .fillMaxHeight().width(panelWidth).padding(12.dp)
                    .testTag(if (panelLeft) "side_panel_left" else "side_panel_right"),
            ) {
                val tab = panel ?: PanelTab.BRUSH
                val dragModifier = if (!wide || panelRail) Modifier else Modifier.pointerInput(panelLeft, areaWidthPx) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val home = if (panelLeft) 0f else areaWidthPx - panelWidthPx
                            val center = home + panelDrag.floatValue + panelWidthPx / 2f
                            panelLeftSaved = com.karapp.drawguidance.ui.screens.canvasedit.components.SidePanelDock.dockLeft(center, areaWidthPx)
                            panelDrag.floatValue = 0f
                        },
                        onDragCancel = { panelDrag.floatValue = 0f },
                    ) { change, amount ->
                        change.consume()
                        panelDrag.floatValue = com.karapp.drawguidance.ui.screens.canvasedit.components.SidePanelDock.clampDrag(panelDrag.floatValue + amount, panelLeft, areaWidthPx, panelWidthPx)
                    }
                }
                EditorSidePanel(
                    tab = tab, onTabChange = viewModel::togglePanel, onClose = viewModel::closePanel,
                    dragHandle = if (panelWindow != null) Modifier.windowDrag(panelWindow) else if (wide && !panelRail) dragModifier else null,
                    onResetPosition = panelWindow?.takeIf { it.moved }?.let { w -> w::reset },
                ) {
                    when (tab) {
                        // v47 Özellikler: seçili aracın ayarları; yalnız ayarı olan araçlarda (fırça, silgi, kalem, yazı) dolu
                        PanelTab.BRUSH -> {
                            val properties = com.karapp.drawguidance.ui.screens.canvasedit.components.Properties.content(if (textDraft != null) EditMode.TEXT else mode, kids, paintBrushOn, selectedTexts.isNotEmpty())
                            // Yazı aracında üstte yeni yazıların biçimi (seçili yazının biçimi Elemanlar'da; tekrar yok), altında renk
                            val textSection: (@Composable () -> Unit)? = if (properties == com.karapp.drawguidance.ui.screens.canvasedit.components.PropertiesContent.TEXT && selectedTexts.isEmpty()) {
                                {
                                    NeuSurface(modifier = Modifier.fillMaxWidth(), elevation = 5.dp) {
                                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Text(stringResource(R.string.text_format_title), style = MaterialTheme.typography.titleSmall, color = colors.accent)
                                            Text(stringResource(R.string.text_settings_new), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
                                            com.karapp.drawguidance.ui.screens.canvasedit.components.TextFormatPanel(
                                                settings = textDefaults,
                                                onChange = { viewModel.updateTextSettings(it, textDefaults) },
                                                notes = kind.isNotes,
                                            )
                                        }
                                    }
                                }
                            } else null
                            if (properties == com.karapp.drawguidance.ui.screens.canvasedit.components.PropertiesContent.EMPTY) {
                                com.karapp.drawguidance.ui.screens.canvasedit.components.PropertiesEmpty()
                            } else Box(Modifier.testTag(if (properties == com.karapp.drawguidance.ui.screens.canvasedit.components.PropertiesContent.TEXT) "properties_text" else "properties_brush")) {
                                BrushPanelContent(
                            settings = brush,
                            savedColors = savedColors,
                            onOpenBrushPicker = viewModel::openBrushPicker,
                            onSizeChange = viewModel::updateBrushSize,
                            onOpacityChange = viewModel::updateBrushOpacity,
                            onFlowChange = viewModel::updateBrushFlow,
                            onStrengthChange = viewModel::updateBrushStrength,
                            onColorChange = viewModel::updateBrushColor,
                            onSaveColor = viewModel::saveCurrentColor,
                            onRemoveColor = viewModel::removeSavedColor,
                            onTuning = viewModel::updateBrushTuning,
                            onResetTuning = viewModel::resetBrushTuning,
                            onEyedropper = {
                                viewModel.closePanel()
                                viewModel.toggleCanvasPick(com.karapp.drawguidance.ui.screens.canvasedit.components.CanvasPick.EYEDROPPER)
                            },
                            // v33 çocuklar için sade fırça paneli
                            kids = kids,
                            textSection = textSection,
                            )
                            }
                        }
                        PanelTab.LAYERS -> {
                            val counts = remember(current.strokes, layers) {
                                current.strokes.filter { it.brushType != BrushType.ERASER }
                                    .groupingBy { com.karapp.drawguidance.data.model.Layers.of(it, layers) }.eachCount()
                            }
                            com.karapp.drawguidance.ui.screens.canvasedit.components.LayersPanelContent(
                                layers = layers,
                                active = activeLayer,
                                selectionCount = selectedElementCount,
                                counts = counts,
                                onSelect = viewModel::setActiveLayer,
                                onAdd = { viewModel.addLayer() },
                                onToggleVisible = viewModel::toggleLayerVisible,
                                onToggleLocked = viewModel::toggleLayerLocked,
                                onRename = viewModel::renameLayer,
                                onMove = viewModel::moveLayer,
                                onMoveSelection = viewModel::moveSelectionToLayer,
                                onDelete = viewModel::removeLayer,
                            )
                        }
                        PanelTab.ELEMENTS -> {
                            val spec = selectedShape?.shape
                            // Grafikler ve ucu açık şekillerde dolgu/köşe ayarı yok (grafik kendi parçalarını boyar)
                            val openEnded = spec != null && (
                                com.karapp.drawguidance.ui.drawing.GraphShapes.openEnded.any { it.name == spec.kind } ||
                                    com.karapp.drawguidance.ui.drawing.ShapeRegistry.isGraph(spec.kind)
                                )
                            val hasStyleTarget = selectedShape != null || selectedDiagram != null
                            val activeStyle = spec?.style ?: selectedDiagram?.diagram?.style ?: shapeStyle
                            val activeGraph = spec?.graph
                                ?: graphSpec.takeIf { !hasStyleTarget && com.karapp.drawguidance.ui.drawing.ShapeRegistry.isGraph(shape) && !com.karapp.drawguidance.ui.drawing.ShapeRegistry.isModernGraph(shape) }
                            ElementsPanelContent(
                                rows = remember(elements) { ElementOps.rows(elements) },
                                elements = elements,
                                selectedIds = selectedIds,
                                onToggleElement = viewModel::toggleElement,
                                onToggleGroup = viewModel::toggleGroup,
                                onGroup = viewModel::groupSelected,
                                onUngroup = viewModel::ungroupSelected,
                                onRecolor = { recolorOpen = true },
                                onMove = { viewModel.startAction(EditMode.MOVE) },
                                onDelete = viewModel::deleteSelected,
                                onClearSelection = viewModel::clearSelection,
                                onOrder = viewModel::reorderSelected,
                                onHide = requestHide,
                                onSetHidden = { ids, hide -> if (hide) viewModel.hideElements(ids) else viewModel.showElements(ids) },
                                onShowAll = { viewModel.showElements(null) },
                                hasBackground = canvas?.pageBackground?.isEmpty == false,
                                backgroundSelected = backgroundEdit,
                                // v41 arka planın işlemleri çizim alanının üstündeki çubukta: panel kapanır
                                onSelectBackground = { viewModel.clearSelection(); viewModel.closePanel(); backgroundEdit = true },
                                styleSection = if (hasStyleTarget || selectedTexts.isNotEmpty() || activeGraph != null) {
                                    {
                                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            if (hasStyleTarget || activeGraph != null) {
                                                com.karapp.drawguidance.ui.screens.canvasedit.components.ElementStyleSection(
                                                    style = activeStyle,
                                                    graph = activeGraph,
                                                    showStyle = hasStyleTarget && !openEnded,
                                                    showAlign = hasStyleTarget && !openEnded,
                                                    onStyleChange = viewModel::updateShapeStyle,
                                                    onGraphChange = viewModel::updateGraphSpec,
                                                    onAlign = viewModel::alignSelection,
                                                )
                                            }
                                            // v29 tek yazı biçimi alanı: yazı tipi, renk ve kenarlık, efektler, kutu ve yer (her ayar bir kez)
                                            if (selectedTexts.isNotEmpty()) {
                                                val first = selectedTexts.first()
                                                val shownText = TextSettings(first.text?.copy(content = "") ?: textSettings.spec, first.sizePx)
                                                NeuSurface(modifier = Modifier.fillMaxWidth().testTag("text_format_section"), elevation = 5.dp) {
                                                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                        Text(stringResource(R.string.text_format_title), style = MaterialTheme.typography.titleSmall, color = colors.accent)
                                                        Text(
                                                            stringResource(R.string.text_settings_selected, selectedTexts.size),
                                                            style = MaterialTheme.typography.bodySmall, color = colors.contentMuted,
                                                        )
                                                        com.karapp.drawguidance.ui.screens.canvasedit.components.TextFormatPanel(
                                                            settings = shownText,
                                                            onChange = { viewModel.updateTextSettings(it, shownText) },
                                                            notes = kind.isNotes,
                                                            fillColorHex = first.colorHex,
                                                            onFill = viewModel::setTextFill,
                                                            onBoxSize = viewModel::setTextBoxSize,
                                                            onPlace = if (!hasStyleTarget) viewModel::alignSelection else null,
                                                        )
                                                        if (selectedTexts.size == 1) {
                                                            com.karapp.drawguidance.ui.components.NeuButton(
                                                                stringResource(R.string.edit_text), viewModel::editSelectedText, Modifier.fillMaxWidth(),
                                                                icon = Icons.Filled.TextFields,
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                            if (selectedDiagram != null) {
                                                com.karapp.drawguidance.ui.components.NeuButton(
                                                    stringResource(R.string.diagram_edit), viewModel::editSelectedDiagram, Modifier.fillMaxWidth(),
                                                    icon = Icons.Filled.AccountTree,
                                                )
                                            }
                                            if (selectedShape != null) {
                                                com.karapp.drawguidance.ui.components.NeuButton(
                                                    stringResource(R.string.shape_edit_open), viewModel::editSelectedShape, Modifier.fillMaxWidth(),
                                                    icon = Icons.Filled.ContentCut,
                                                )
                                            }
                                        }
                                    }
                                } else null,
                            )
                        }
                    }
                }
            }
            // v33 araç açıklaması kutusu: çizim alanının sağ üst köşesinde, yakınlık göstergesinin altında (panel açıkken
            // sol üstte); panellerin ve öneri balonlarının da üstünde
            com.karapp.drawguidance.ui.screens.canvasedit.components.ToolHelpBox(
                toolHelp,
                Modifier.align(if (panel != null) Alignment.TopStart else Alignment.TopEnd)
                    .padding(start = 16.dp, end = 16.dp, top = 46.dp),
                // v45 gelişmiş modlarda köşede düğme adı yok (yalnız açıklama); basit modlar v33 gibi
                showNames = simple,
            )
        }
        // Kılavuz / perspektif genel ayarları: çizim alanının hemen altında
        if (mode == EditMode.GUIDE || mode == EditMode.PERSPECTIVE) {
            com.karapp.drawguidance.ui.screens.canvasedit.components.ToolHelpScope(toolHelp) {
            com.karapp.drawguidance.ui.screens.canvasedit.components.GuideSettingsBar(
                spec = current.guide,
                perspective = mode == EditMode.PERSPECTIVE,
                onChange = { viewModel.setGuide(it) },
                onRemove = { if (mode == EditMode.PERSPECTIVE) viewModel.removePerspective() else viewModel.removeGuide() },
                onDone = { viewModel.finishAction() },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
            }
        }
        // Çizim alanının hemen altındaki sayfa şeridi (çalışma/not tuvali; v27 çizgi roman da)
        if (kind.paged) com.karapp.drawguidance.ui.screens.canvasedit.components.ToolHelpScope(toolHelp) {
            com.karapp.drawguidance.ui.screens.canvasedit.components.PageStrip(
                pageCount = current.pageCount,
                currentPage = current.currentPage,
                onGoToPage = viewModel::goToPage,
                onAddPage = viewModel::addPage,
                onDuplicatePage = viewModel::duplicatePage,
                onDeletePage = viewModel::deletePage,
                maxPages = current.kind.maxPages,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp).navigationBarsPadding().tutorialAnchor(com.karapp.drawguidance.ui.screens.canvasedit.tutorial.TutorialAnchor.PAGES),
                // v45 çizgi roman kapağı: "Kapak" rozeti; kapaksız çizgi romanda sayfa menüsünde "Kapak ekle"
                cover = current.hasCover,
                onAddCover = if (comic && !current.hasCover) ({ viewModel.v45.addCover() }) else null,
            )
        }
        }
        }
    }

    }

    (imageSave as? ImageSaveState.Saved)?.let { saved ->
        ImageSavedDialog(saved.image, onDismiss = viewModel::dismissImageSave, totalCount = saved.images.size)
    }

    // v42 birleşik Kaydet penceresi (sayfalı tuvaller); açıkken ayrı resim/PDF pencereleri gösterilmez
    val unifiedSaveTab by viewModel.saveTab.collectAsState()
    if (unifiedSaveTab != null) current0?.let { c ->
        com.karapp.drawguidance.ui.screens.canvasedit.components.UnifiedSaveSheet(viewModel, c)
    }
    // Kaydet düğmesi: kaydetme ayarlarını soran pencere
    val saveOptions by viewModel.saveOptions.collectAsState()
    if (unifiedSaveTab == null) saveOptions?.let { options ->
        com.karapp.drawguidance.ui.components.ImageSaveOptionsDialog(
            initial = options,
            pageCount = current0?.pageCount ?: 1,
            hasGuides = current0?.guide?.isEmpty == false,
            locationLabel = viewModel.imageSaveLocation,
            outputSize = viewModel::imageOutputSize,
            onSave = viewModel::saveAsImage,
            onDismiss = viewModel::closeSaveDialog,
            preview = viewModel::cmykPreview,
            documentLocationLabel = viewModel.pdfLocation,
        )
    }

    // v19 PDF olarak kaydet penceresi
    val pdfOptions by viewModel.pdfOptions.collectAsState()
    if (unifiedSaveTab == null) pdfOptions?.let { options ->
        com.karapp.drawguidance.ui.components.PdfOptionsSheet(
            initial = options,
            canvasWidth = current0?.width ?: 1,
            canvasHeight = current0?.height ?: 1,
            pageCount = current0?.pageCount ?: 1,
            currentPage = current0?.currentPage ?: 0,
            hasGuides = current0?.guide?.isEmpty == false,
            locationLabel = viewModel.pdfLocation,
            onSave = { viewModel.exportPdf(it) },
            onDismiss = viewModel::closePdfDialog,
        )
    }

    // Fırça seçici örnekleri ekran açılınca (ve renk değişince) arka planda hazırlanır: seçici anında açılır
    val previewHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) { 46.dp.roundToPx() }
    val previewPaper = if (colors.isDark) androidx.compose.ui.graphics.Color(0xFFF4F1EA) else androidx.compose.ui.graphics.Color.White
    LaunchedEffect(brush.colorHex, previewHeightPx, previewPaper, kind) {
        com.karapp.drawguidance.ui.screens.canvasedit.components.BrushPreviewCache.prewarm(
            com.karapp.drawguidance.ui.drawing.pickerBrushTypes(kind), hexToColor(brush.colorHex), previewPaper, previewHeightPx,
        )
    }

    val imageBusy by viewModel.imageFiltering.collectAsState()
    // v37 pencereler ayrı bir işlevde derlenir: CanvasEditScreen JVM'in 64 KB yöntem sınırına dayanmıştı
    val sheets: @Composable () -> Unit = {
        // v40 sayfa ayarları, CBZ ve baskı dosyası bildirimleri
        com.karapp.drawguidance.ui.screens.canvasedit.components.V40EditorSheets(viewModel, snackbar, snackbarScope)
        if (isBrushPickerOpen) {
            BrushPickerModal(
                // v22: silgideyken son seçilen fırça seçili görünür
                selected = if (brush.brushType == com.karapp.drawguidance.data.model.BrushType.ERASER) drawingBrush else brush.brushType,
                colorHex = brush.colorHex,
                onDismiss = viewModel::closeBrushPicker,
                onSelect = viewModel::selectBrush,
                kind = kind,
            )
        }

        if (isShapePickerOpen) {
            com.karapp.drawguidance.ui.screens.canvasedit.components.ShapePickerModal(
                kind = kind,
                selected = if (mode == EditMode.SHAPE) shape else null,
                onDismiss = viewModel::closeShapePicker,
                onSelect = viewModel::selectShapeId,
                // v33 çocuk şekilleri beyaz dolgulu gelir (boyama için); dolgu seçeneği yok
                showFill = !kids,
                fillHex = shapeStyle.fillColorHex,
                onFillChange = viewModel::setNewShapeFill,
                // v25 3B şekiller artık Ekle menüsünde ayrı "3B şekil ekle" seçeneğinde
            )
        }

        if (isDiagramPickerOpen) {
            com.karapp.drawguidance.ui.screens.canvasedit.components.DiagramPickerModal(
                selected = if (mode == EditMode.DIAGRAM) diagramKind else null,
                onDismiss = viewModel::closeDiagramPicker,
                onSelect = viewModel::selectDiagram,
                theme = diagramTheme,
                onTheme = viewModel::setDiagramTheme,
                // v33 basit not almada 10 temel diyagram
                only = com.karapp.drawguidance.ui.drawing.SimpleCatalog.diagrams(kind),
            )
        }

        // v23 3B Şekillerim: tuvale ekle, düzenle, yeni
        if (models3dOpen) {
            val models3d by viewModel.models3dList.collectAsState()
            com.karapp.drawguidance.ui.screens.canvasedit.components.Model3DPickerSheet(
                models = models3d,
                onInsert = { id -> models3dOpen = false; viewModel.insertModel3d(id) },
                onInsertCatalog = { id -> models3dOpen = false; viewModel.insertCatalogModel3d(id) },
                onEdit = { id -> models3dOpen = false; open3dAsk = id to null },
                onNew = { models3dOpen = false; open3dAsk = null to null },
                onDismiss = { models3dOpen = false },
                // v33 basit çizimde GLB içe aktarma yok, hazır şekiller 10 tane
                onImportGlb = if (simple) null else glbPicker,
                catalogOnly = com.karapp.drawguidance.ui.drawing.SimpleCatalog.models3d(kind),
            )
        }
        if (imageSourceOpen) {
            val saved by viewModel.savedImages.collectAsState(initial = emptyList())
            com.karapp.drawguidance.ui.screens.canvasedit.components.ImageSourceSheet(
                savedImages = saved,
                onDevice = {
                    imageSourceOpen = false
                    pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onSaved = { image ->
                    imageSourceOpen = false
                    val anchor = viewModel.takeAddAnchor()
                    val size = viewport.viewSize
                    val center = anchor ?: viewport.toCanvas(Offset(size.width / 2f, size.height / 2f))
                    viewModel.importSavedImage(android.net.Uri.parse(image.uri), center)
                },
                onDismiss = { imageSourceOpen = false; viewModel.takeAddAnchor() },
            )
        }
        // v29 not alma / çizgi roman tuvaline aktarma
        if (transferOpen) {
            val targets by viewModel.transferTargets.collectAsState(initial = emptyList())
            val transferring by viewModel.transferring.collectAsState()
            com.karapp.drawguidance.ui.components.TransferSheet(
                title = stringResource(R.string.transfer_title_canvas),
                hint = stringResource(R.string.transfer_hint_canvas),
                targets = targets,
                newKinds = listOf(com.karapp.drawguidance.data.model.CanvasKind.NOTES, com.karapp.drawguidance.data.model.CanvasKind.COMIC),
                working = transferring,
                onTransfer = viewModel::transferTo,
                onDismiss = { transferOpen = false },
                selectionCount = selectedElementCount,
            )
        }
        // v27 resim filtreleri (çizgi roman görünümü)
        if (filterOpen) {
            com.karapp.drawguidance.ui.screens.canvasedit.components.ImageFilterSheet(
                onPick = { filterOpen = false; viewModel.filterSelectedImage(it) },
                onDismiss = { filterOpen = false },
            )
        }
        // v30 arka plan kaldırma ve efektler (seçili tek resim; işlenirken pencere kapanır)
        if (bgRemoveOpen) {
            com.karapp.drawguidance.ui.screens.canvasedit.components.BackgroundRemoveSheet(
                loadPreview = viewModel::selectedImagePreview,
                busy = imageBusy,
                onApply = { bgRemoveOpen = false; viewModel.removeImageBackground(it) },
                onDismiss = { bgRemoveOpen = false },
            )
        }
        if (effectsOpen) {
            com.karapp.drawguidance.ui.screens.canvasedit.components.ImageEffectsSheet(
                loadPreview = viewModel::selectedImagePreview,
                busy = imageBusy,
                onApply = { look, adjust -> effectsOpen = false; viewModel.applyImageEffects(look, adjust) },
                onDismiss = { effectsOpen = false },
            )
        }
        // v27 çizgi roman pencereleri
        if (balloonOpen) {
            val editing = if (balloonEdit) viewModel.selectedBalloon()?.text?.box?.balloon else null
            com.karapp.drawguidance.ui.screens.canvasedit.components.BalloonSheet(
                current = editing,
                onPick = { style, speakers ->
                    if (editing != null) viewModel.updateBalloon(style = style, speakers = speakers)
                    else { balloonOpen = false; viewModel.insertBalloon(style, speakers) }
                },
                onDismiss = { balloonOpen = false; viewModel.endCoalesce() },
                onColors = if (editing != null) { fill, line -> viewModel.updateBalloon(fillHex = fill, strokeHex = line) } else null,
                // v29 seçili balonun eni ve boyu
                box = if (balloonEdit) viewModel.selectedBalloon()?.text?.box else null,
                sizePx = viewModel.selectedBalloon()?.sizePx ?: textSettings.sizePx,
                onSize = if (editing != null) viewModel::setTextBoxSize else null,
                // v36 yazı, yazı tipi, boyut, renk, hizalama ve kuyruk; SFX yazısı
                stroke = if (editing != null) viewModel.selectedBalloon() else null,
                onInsert = { style, speakers, options -> balloonOpen = false; viewModel.insertBalloon(style, speakers, options) },
                onSfx = { options -> balloonOpen = false; viewModel.insertSfxText(options) },
                onText = { content, font, size, color, align -> viewModel.updateBalloonText(content, font, size, color, align) },
                onTails = { dir, length, curve -> viewModel.setBalloonTails(dir, length, curve) },
                defaultFont = textSettings.spec.font.takeIf { it.group == com.karapp.drawguidance.data.model.FontGroup.COMIC }
                    ?: com.karapp.drawguidance.data.model.TextFont.COMIC_RELIEF,
            )
        }
        if (locationsOpen) {
            com.karapp.drawguidance.ui.screens.canvasedit.components.LocationBackgroundsSheet(
                onPick = { b -> locationsOpen = false; if (!viewModel.placeLocationBackgroundDirectly(b)) placingLocation = b.id },
                onDismiss = { locationsOpen = false },
            )
        }
        // v43 Arkaplan ile Nesneyi Uyumla / Nesneyi Mekâna Yerleştir (önizleme tuvalde)
        harmonyDraft?.let { d ->
            com.karapp.drawguidance.ui.screens.canvasedit.components.HarmonizeSheet(
                draft = d, onOptions = viewModel::setHarmonyOptions,
                onApply = viewModel::applyHarmony, onCancel = viewModel::cancelHarmony, onRemove = viewModel::removeHarmony,
            )
        }
        placeDraft?.let { d ->
            com.karapp.drawguidance.ui.screens.canvasedit.components.PlaceSheet(
                draft = d, onManual = { hy, fy, sm -> viewModel.setPlaceManual(hy, fy, sm) },
                onApply = viewModel::applyPlace, onCancel = viewModel::cancelPlace,
            )
        }
        if (packsOpen) {
            com.karapp.drawguidance.ui.screens.canvasedit.components.PackItemsSheet(
                mode = kind.packMode(), store = packStore,
                onPick = { p, i -> packsOpen = false; viewModel.insertPackImage(p.file(i), i.w, i.h) },
                onDismiss = { packsOpen = false },
            )
        }
        if (objectsOpen) {
            com.karapp.drawguidance.ui.screens.canvasedit.components.ComicObjectsSheet(
                onPick = { p -> objectsOpen = false; viewModel.insertComicProp(com.karapp.drawguidance.data.comicassets.ComicAssetKind.OBJECT, p) },
                onDismiss = { objectsOpen = false },
            )
        }
        if (charactersOpen) {
            com.karapp.drawguidance.ui.screens.canvasedit.components.CharactersSheet(
                onInsert = { def -> charactersOpen = false; viewModel.insertCharacter(def) },
                onDismiss = { charactersOpen = false },
            )
        }
        if (templatesOpen) {
            val panelSet = remember(canvas?.strokes) { canvas?.strokes?.let { com.karapp.drawguidance.ui.screens.canvasedit.state.PanelSets.find(it) } }
            com.karapp.drawguidance.ui.screens.canvasedit.components.TemplatesSheet(
                pageWidth = canvas?.width ?: 1000, pageHeight = canvas?.height ?: 1414, panels = panelSet, initialLeaf = templatesLeaf,
                onPick = { viewModel.insertTemplate(it) },
                onEdit = { key, edit -> viewModel.editPanels(key, edit) },
                onAdd = { leaf, rows -> viewModel.addPanel(leaf, rows) },
                onEndCoalesce = { viewModel.endCoalesce() },
                onDismiss = { templatesOpen = false; viewModel.endCoalesce() },
            )
        }
        comicFxOpen?.let { tab ->
            val close: () -> Unit = { comicFxOpen = null; viewModel.endCoalesce() }
            com.karapp.drawguidance.ui.screens.canvasedit.components.EffectsSheet(
                initialTab = tab,
                // v29 seçili çizgi grubu (eklendikten sonra odak, yön, sıklık… değişir)
                selectedLines = remember(tab) { viewModel.selectedLinesOptions() },
                onLines = { k, options -> close(); viewModel.insertComicLines(k, options) },
                onSelectedLines = viewModel::setLinesOptions,
                onDismiss = close,
                onImageEffect = { p -> close(); viewModel.insertComicProp(com.karapp.drawguidance.data.comicassets.ComicAssetKind.EFFECT, p) },
            )
        }
        // v27 GLB içe aktarma: ayrıntı ve "3B Şekillerim'e ayrı şekil olarak kaydedilsin mi?" sorusu
        glbPending?.let { p ->
            com.karapp.drawguidance.ui.screens.model3d.GlbImportAskDialog(
                p.name, p.bytes.size,
                onImport = { tris, save -> glbPending = null; models3dOpen = false; viewModel.importGlbToCanvas(p.bytes, p.name, tris, save) },
                onDismiss = { glbPending = null },
            )
        }
        edit3dAsk?.let { stroke -> open3dAsk = stroke.model?.modelId to stroke; edit3dAsk = null }
        // 3B düzenleyici ayrı pencerede açılır: önce tuvalin kaydedilmesi sorulur
        open3dAsk?.let { (modelId, stroke) ->
            val open: () -> Unit = {
                open3dAsk = null
                if (stroke != null) viewModel.prepareModelEdit(stroke) { id -> onOpenModel3d(id, stroke.id) } else onOpenModel3d(modelId, null)
            }
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { open3dAsk = null },
                containerColor = colors.background,
                title = { Text(stringResource(R.string.model3d_open_title)) },
                text = { Text(stringResource(R.string.model3d_open_text)) },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = { viewModel.saveBeforeLeaving(); open() }) { Text(stringResource(R.string.model3d_open_save)) }
                },
                dismissButton = {
                    Row {
                        androidx.compose.material3.TextButton(onClick = { open3dAsk = null }) { Text(stringResource(R.string.cancel_button)) }
                        androidx.compose.material3.TextButton(onClick = open) { Text(stringResource(R.string.model3d_open_nosave)) }
                    }
                },
                modifier = Modifier.testTag("model3d_open_dialog"),
            )
        }

        // v23 arka plan: kağıt rengi, desen ve resim
        if (backgroundOpen) current0?.let { c ->
            // v24 not tuvalinde birden çok sayfa varken açık sayfanın arka planı değişir; "Tüm sayfalara uygula" hepsine
            val perPage = c.kind.paged && c.pageCount > 1
            com.karapp.drawguidance.ui.screens.canvasedit.components.BackgroundSheet(
                kind = c.kind,
                paperHex = c.pagePaperHex,
                spec = c.pageBackground,
                onApply = viewModel::applyBackground,
                onGallery = viewModel::importBackgroundAsset,
                onDismiss = { backgroundOpen = false },
                pageLabel = if (perPage) stringResource(R.string.bg_page_only, c.currentPage + 1, c.pageCount) else null,
                allPages = perPage,
                onTransform = if (viewModel.backgroundTransform.available()) { { backgroundOpen = false; backgroundEdit = true } } else null,
            )
        }

        // Kılavuz ve perspektif ayrı eklenir; pencerede yalnızca tür seçilir, genel ayarlar çizim alanının altında
        if (guideOpen) {
            val g0 = current0?.guide
            com.karapp.drawguidance.ui.screens.canvasedit.components.GuideTypeSheet(
                selected = g0?.kind ?: com.karapp.drawguidance.data.model.GuideKind.NONE,
                onPick = { guideOpen = false; viewModel.addGuide(it) },
                onDismiss = { guideOpen = false },
                // v27 mevcut kılavuz ve perspektif çizgilerini temizleme (her tuval türünde)
                canClear = g0 != null && (g0.kind != com.karapp.drawguidance.data.model.GuideKind.NONE || g0.perspective > 0),
                onClear = { guideOpen = false; viewModel.clearGuides() },
            )
        }
        if (perspectiveOpen) {
            val g0 = current0?.guide
            com.karapp.drawguidance.ui.screens.canvasedit.components.PerspectiveTypeSheet(
                selected = g0?.perspective ?: 0,
                onPick = { perspectiveOpen = false; viewModel.addPerspective(it) },
                onDismiss = { perspectiveOpen = false },
                canClear = g0 != null && (g0.kind != com.karapp.drawguidance.data.model.GuideKind.NONE || g0.perspective > 0),
                onClear = { perspectiveOpen = false; viewModel.clearGuides() },
            )
        }

        if (isFormulaPickerOpen) {
            com.karapp.drawguidance.ui.screens.canvasedit.components.FormulaPickerModal(
                selected = if (mode == EditMode.FORMULA) formulaTemplate?.id else null,
                onDismiss = viewModel::closeFormulaPicker,
                onSelect = viewModel::selectFormula,
                // v33 basit not almada 10 temel formül
                only = com.karapp.drawguidance.ui.drawing.SimpleCatalog.formulas(kind),
            )
        }
        if ((inkColorOpen || colorSheet != null) && selectedIds.isNotEmpty()) {
            val text = colorSheet == "text"
            com.karapp.drawguidance.ui.screens.canvasedit.components.PartColorSheet(
                title = stringResource(if (text) R.string.text_color else R.string.ink_color_title),
                colorHex = remember(selectedIds, colorSheet) { viewModel.selectedInkColor(text) },
                onColorChange = { if (text) viewModel.recolorSelected(it) else viewModel.recolorSelectedInk(it) },
                onDismiss = { inkColorOpen = false; colorSheet = null; viewModel.endCoalesce() },
            )
        }
        if (regionColorOpen && mode == EditMode.SHAPE_EDIT) {
            com.karapp.drawguidance.ui.screens.canvasedit.components.ColorPickSheet(
                initialColorHex = regionColor?.takeIf { it.isNotEmpty() } ?: viewModel.regionInk(),
                onColorChange = { viewModel.setRegionColor(it) },
                onDismiss = { regionColorOpen = false },
            )
        }
        if (partStyleOpen && mode == EditMode.SHAPE_EDIT) {
            com.karapp.drawguidance.ui.screens.canvasedit.components.PartStyleSheet(
                initial = remember { viewModel.partStyleOf() },
                partsCount = shapeParts.size,
                onChange = viewModel::applyPartStyle,
                onDismiss = { partStyleOpen = false; viewModel.endCoalesce() },
            )
        }
        graphEdit?.let { request ->
            com.karapp.drawguidance.ui.screens.canvasedit.components.GraphValueDialog(
                tag = request.tag, initial = request.initial,
                onApply = viewModel::applyGraphEdit,
                onDismiss = viewModel::dismissGraphEdit,
            )
        }
        // v18 Taşı: konum girme; Genişlet: kağıt boyutu
        if (positionOpen) {
            val origin = selectionOrigin
            if (origin == null) positionOpen = false else {
                com.karapp.drawguidance.ui.screens.canvasedit.components.PositionSheet(
                    x = origin.x.roundToInt(), y = origin.y.roundToInt(),
                    onApply = { x, y -> viewModel.moveSelectionTo(x.toFloat(), y.toFloat()) },
                    onDismiss = { positionOpen = false },
                )
            }
        }
        if (sizeOpen) {
            canvas?.let { c ->
                com.karapp.drawguidance.ui.screens.canvasedit.components.CanvasSizeSheet(
                    width = c.width, height = c.height,
                    onApply = { w, h, ax, ay -> viewModel.setCanvasSize(w, h, ax, ay) },
                    onDismiss = { sizeOpen = false },
                )
            }
        }
        val vectorizeEmpty by viewModel.vectorizeEmpty.collectAsState()
        if (vectorizeOpen && (selectedImage != null || tracing)) {
            com.karapp.drawguidance.ui.screens.canvasedit.components.VectorizeSheet(
                tracing = tracing,
                mode = vectorizeMode,
                onApply = { o, w, keep -> viewModel.vectorizeSelectedImage(o, w, keep) },
                onCancel = viewModel::cancelTrace,
                onDismiss = { vectorizeOpen = false },
                empty = vectorizeEmpty,
            )
        }
        // Vektörleştirme bitince pencere kapanır
        LaunchedEffect(tracing, mode) { if (!tracing && mode != EditMode.IMAGE_EDIT) vectorizeOpen = false }
        if (diagramThemeOpen && selectedDiagram != null) {
            com.karapp.drawguidance.ui.screens.canvasedit.components.DiagramThemeSheet(
                selected = selectedDiagram?.diagram?.theme ?: "",
                onSelect = viewModel::setDiagramTheme,
                onDismiss = { diagramThemeOpen = false },
            )
        }
        if (unsavedOpen) {
            com.karapp.drawguidance.ui.screens.canvasedit.components.UnsavedChangesDialog(
                onSave = { unsavedOpen = false; viewModel.saveNow(); onBack() },
                onDiscard = { unsavedOpen = false; onBack() },
                onDismiss = { unsavedOpen = false },
            )
        }

        // Diyagramda odaktaki adımın (dolgu/kenar/yazı) ya da okun rengi
        diagramColorPart?.let { part ->
            val isNode = diagramFocus is DiagramFocus.Node
            val parts = if (isNode) listOf(
                com.karapp.drawguidance.ui.screens.canvasedit.state.DiagramController.ColorPart.FILL.name to stringResource(R.string.diagram_color_fill),
                com.karapp.drawguidance.ui.screens.canvasedit.state.DiagramController.ColorPart.STROKE.name to stringResource(R.string.diagram_color_stroke),
                com.karapp.drawguidance.ui.screens.canvasedit.state.DiagramController.ColorPart.TEXT.name to stringResource(R.string.diagram_color_text),
            ) else emptyList()
            com.karapp.drawguidance.ui.screens.canvasedit.components.PartColorSheet(
                title = stringResource(if (isNode) R.string.diagram_color_node_title else R.string.diagram_color_edge_title),
                colorHex = viewModel.diagramColor(part) ?: "#1E1E1E",
                onColorChange = { viewModel.diagramSetColor(part, it) },
                onDismiss = { diagramColorPart = null; viewModel.endCoalesce() },
                parts = parts,
                selectedPart = part.name,
                onPartChange = { key -> diagramColorPart = com.karapp.drawguidance.ui.screens.canvasedit.state.DiagramController.ColorPart.valueOf(key) },
                onReset = { viewModel.diagramSetColor(part, null) },
            )
        }

        // v33 çocuklar için arka plan boyama: büyük palet, dokununca kağıt o renge boyanır
        if (simpleBackgroundOpen) {
            com.karapp.drawguidance.ui.screens.canvasedit.components.KidsBackgroundSheet(
                currentHex = canvas?.pagePaperHex ?: "#FFFFFF",
                onColor = { viewModel.setBackgroundColor(it) },
                onDismiss = { simpleBackgroundOpen = false },
                onTransform = if (viewModel.backgroundTransform.available()) { { simpleBackgroundOpen = false; backgroundEdit = true } } else null,
            )
        }
        fillInitial?.let { initial ->
            com.karapp.drawguidance.ui.screens.canvasedit.components.ColorPickSheet(
                initialColorHex = initial,
                onColorChange = viewModel::applyFill,
                onDismiss = { fillInitial = null; viewModel.endCoalesce() },
                // v27 gradyan dolgu (şekil, yazı ya da kağıt)
                initialGradient = remember(initial) { viewModel.currentFillGradient() },
                onGradientChange = viewModel::applyFillGradient,
            )
        }

        when (val state = imageImport) {
            is ImageImportState.Rejected -> AlertDialog(
                onDismissRequest = viewModel::dismissImageImport,
                containerColor = colors.background,
                title = { Text(stringResource(R.string.image_rejected_title)) },
                text = { IssueList(state.errors + state.warnings) },
                confirmButton = { TextButton(onClick = viewModel::dismissImageImport) { Text(stringResource(R.string.ok_button)) } },
            )
            is ImageImportState.NeedsConfirmation -> AlertDialog(
                onDismissRequest = viewModel::dismissImageImport,
                containerColor = colors.background,
                title = { Text(stringResource(R.string.image_warning_title)) },
                text = { IssueList(state.warnings) },
                confirmButton = { TextButton(onClick = viewModel::confirmImageImport) { Text(stringResource(R.string.image_add_anyway)) } },
                dismissButton = { TextButton(onClick = viewModel::dismissImageImport) { Text(stringResource(R.string.cancel_button)) } },
            )
            else -> Unit
        }

        if (hideOpen) {
            val selectedEls = remember(elements, selectedIds) { elements.selectedElements(selectedIds) }
            val hiddenEls = remember(elements) { elements.elements.filter { it.hidden }.asReversed() }
            if (selectedEls.isEmpty() && hiddenEls.isEmpty()) {
                LaunchedEffect(Unit) { hideOpen = false }
            } else {
                com.karapp.drawguidance.ui.screens.canvasedit.components.HideElementsSheet(
                    selected = selectedEls,
                    hidden = hiddenEls,
                    onHide = { ids -> viewModel.hideElements(ids); hideOpen = false },
                    onShow = { ids -> viewModel.showElements(ids); if (ids == null) hideOpen = false },
                    onDismiss = { hideOpen = false },
                )
            }
        }

        if (recolorOpen && selectedElementCount > 0) {
            // Renk Ver: verilen renk elemanın dolgu rengi olur (boya kutusuyla aynı)
            RecolorSheet(
                initialColorHex = remember { viewModel.currentFillColor() },
                subtitle = stringResource(R.string.recolor_fill_subtitle, selectedElementCount),
                selectedCount = selectedElementCount,
                savedColors = savedColors,
                onSaveColor = viewModel::saveColor,
                onRemoveColor = viewModel::removeSavedColor,
                onDismiss = { recolorOpen = false },
                onApply = { hex ->
                    viewModel.fillSelected(hex)
                    recolorOpen = false
                },
            )
        }
    }
    sheets()

    // v30 şeffaf yükleme perdesi: tuval açılırken, resim işlenirken, PDF hazırlanırken ve resim kaydedilirken
    val pdfBusy = pdfExport == com.karapp.drawguidance.ui.screens.canvasedit.state.PdfExportState.Exporting
    com.karapp.drawguidance.ui.components.LoadingOverlay(
        visible = canvas == null || imageBusy || pdfBusy || imageSave == ImageSaveState.Saving,
        message = stringResource(
            when {
                canvas == null -> R.string.loading_canvas
                imageBusy -> R.string.loading_image
                pdfBusy -> R.string.loading_pdf
                else -> R.string.loading_generic
            },
        ),
    )

    // v32 eğitim modu: araç grubu seçimi, aracın altındaki açıklama kutusu ve çizim
    if (tutorialOffer && canvas != null) {
        com.karapp.drawguidance.ui.screens.canvasedit.tutorial.TutorialOfferSheet(
            kind = kind,
            groups = remember(kind) { viewModel.tutorial.groups(kind) },
            completed = tutorialCompleted,
            onStart = viewModel.tutorial::start,
            onDismiss = viewModel.tutorial::closeOffer,
        )
    }
    tutorialUi?.let { ui ->
        com.karapp.drawguidance.ui.screens.canvasedit.tutorial.TutorialOverlay(
            ui = ui, live = tutorialLive, anchors = tutorialAnchors, viewport = viewport,
            onNext = viewModel.tutorial::next, onCancel = viewModel.tutorial::cancel, onReplay = viewModel.tutorial::replay,
            onCallout = { tutorialCallout = it }, mascot = kids,
        )
    }
    // Her adımda kâğıt açıklama kutusunun kapatmadığı bölgeye sığar; eğitim bitince eski görünüme döner
    val tutorialStepKey = tutorialUi?.let { it.group.id + ":" + it.index }
    val calloutReady = tutorialCallout != null
    val tutorialGapPx = with(androidx.compose.ui.platform.LocalDensity.current) { 10.dp.toPx() }
    // v37 çocuk eğitiminde maskot mod çubuğu açılınca yukarı kalkar: kâğıt yeni yerine göre yeniden sığar
    LaunchedEffect(tutorialStepKey, calloutReady, tutorialCallout?.top.takeIf { kids }) {
        if (tutorialStepKey == null) {
            tutorialCallout = null
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(140)
        val box = tutorialCallout ?: return@LaunchedEffect
        val area0 = tutorialAnchors.rects[com.karapp.drawguidance.ui.screens.canvasedit.tutorial.TutorialAnchor.CANVAS] ?: return@LaunchedEffect
        val gap = tutorialGapPx
        val top = box.top - area0.top; val bottom = box.bottom - area0.top
        val limit = area0.height - modeBarHeightPx
        // v37 çocuklarda maskot hep altta: kâğıt balonun üstüne sığar (mod çubuğu açıkken alan daha dar olabilir)
        val area = if (top > area0.height * 0.4f || kids) androidx.compose.ui.geometry.Rect(0f, 0f, area0.width, top - gap)
        else androidx.compose.ui.geometry.Rect(0f, (bottom + gap).coerceAtLeast(0f), area0.width, limit)
        if (area.height < area0.height * (if (kids) 0.2f else 0.3f) || area.width <= 0f) return@LaunchedEffect
        val (s1, o1) = viewport.fitTarget(area)
        val s0 = viewport.scale; val o0 = viewport.offset
        val anim = androidx.compose.animation.core.Animatable(0f)
        try {
            anim.animateTo(1f, androidx.compose.animation.core.tween(320)) {
                viewport.setView(s0 + (s1 - s0) * value, o0 + (o1 - o0) * value, settle = false)
            }
        } finally {
            viewport.settle()
        }
    }
    var tutorialWasActive by remember { mutableStateOf(false) }
    LaunchedEffect(tutorialUi != null) {
        if (tutorialUi != null) tutorialWasActive = true
        else if (tutorialWasActive) { tutorialWasActive = false; viewport.fit() }
    }
}

/** Çizim alanının köşesinde seçili fırçayı (ikon, ad, renk, boyut) gösteren gösterge. */
@Composable
private fun BrushIndicator(
    brushType: BrushType,
    colorHex: String,
    sizePx: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NeuTheme.colors
    val label = brushLabel(brushType)
    val description = stringResource(R.string.current_brush, label)
    NeuSurface(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = description },
        shape = NeuPillShape,
        elevation = 5.dp,
        onClick = onClick,
        // v33 basılı tutunca açıklama: fırça seçiciyi açar (Fırça Seç düğmesi gibi)
        helpLabel = stringResource(R.string.brush_select),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(BrushIcons.of(brushType), null, tint = colors.accent, modifier = Modifier.size(18.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
            if (brushType != BrushType.ERASER) {
                Box(Modifier.size(12.dp).background(hexToColor(colorHex), CircleShape))
            }
            Text("${sizePx.toInt()}px", style = MaterialTheme.typography.labelSmall, color = colors.contentMuted)
            Spacer(Modifier.width(2.dp))
        }
    }
}

@Composable
private fun modeLabelText(mode: EditMode): String = com.karapp.drawguidance.ui.screens.canvasedit.components.modeLabel(mode)

/** Resim kontrollerinde bulunan hata ve uyarıların madde listesi. */
@Composable
private fun IssueList(issues: List<ImageIssue>) {
    val colors = NeuTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        issues.forEach { issue ->
            Row(verticalAlignment = Alignment.Top) {
                Text("•", color = colors.accent, modifier = Modifier.padding(end = 8.dp))
                Text(issueText(issue), style = MaterialTheme.typography.bodyMedium, color = colors.content)
            }
        }
    }
}

@Composable
fun issueText(issue: ImageIssue): String = when (issue) {
    is ImageIssue.TooLarge -> stringResource(R.string.image_err_too_large, formatBytes(issue.bytes))
    ImageIssue.UnsupportedFormat -> stringResource(R.string.image_err_format)
    ImageIssue.Unreadable -> stringResource(R.string.image_err_unreadable)
    is ImageIssue.TooSmall -> stringResource(R.string.image_err_too_small, issue.width, issue.height)
    is ImageIssue.DimensionsTooLarge -> stringResource(R.string.image_err_dimensions, issue.width, issue.height)
    is ImageIssue.TooDetailed -> stringResource(R.string.image_err_detail, issue.percent)
    is ImageIssue.Detailed -> stringResource(R.string.image_warn_detail, issue.percent)
    is ImageIssue.WillDownscale -> stringResource(R.string.image_warn_downscale, issue.width, issue.height, issue.targetWidth, issue.targetHeight)
    is ImageIssue.NearSizeLimit -> stringResource(R.string.image_warn_near_limit, formatBytes(issue.bytes))
}

/** 1536 → "1,5 KB", 1048576 → "1,00 MB" */
fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format(java.util.Locale.forLanguageTag("tr"), "%.2f MB", bytes / (1024f * 1024f))
    bytes >= 1024L -> String.format(java.util.Locale.forLanguageTag("tr"), "%.1f KB", bytes / 1024f)
    else -> "$bytes B"
}

/** Çizgi yolunun uzunluk bakımından ortası. */
private fun polylineMidpoint(path: List<Offset>): Offset? {
    if (path.isEmpty()) return null
    if (path.size == 1) return path[0]
    val total = path.zipWithNext { a, b -> (b - a).getDistance() }.sum()
    var left = total / 2f
    for (i in 0 until path.size - 1) {
        val seg = (path[i + 1] - path[i]).getDistance()
        if (seg >= left && seg > 0f) return path[i] + (path[i + 1] - path[i]) * (left / seg)
        left -= seg
    }
    return path.last()
}


/** v17: üst menüde şekil düzenleme aracının simgesi. */
internal fun shapeToolIcon(tool: ShapeTool): androidx.compose.ui.graphics.vector.ImageVector = when (tool) {
    ShapeTool.NODES -> Icons.Filled.ScatterPlot
    ShapeTool.ADD_NODE -> Icons.Filled.AddCircle
    ShapeTool.CONNECT -> Icons.Filled.Timeline
    ShapeTool.LINES -> Icons.AutoMirrored.Filled.ShowChart
    ShapeTool.FILL -> Icons.Filled.FormatColorFill
    ShapeTool.GRAPH -> Icons.Filled.StackedLineChart
    ShapeTool.PARAMS -> Icons.Filled.Functions
}

/** v17: çizim alanında açılan menünün yeri (ekran) ve tuvaldeki karşılığı; [context] bağlam menüsü mü (değilse Ekle). */
/** v45 dokunulan yere konabilen eklenecekler (v47 Ekle modunda dokunulan yere son seçilen konur). */
private val ADD_ANCHORED = setOf("text", "shape", "formula", "diagram", "image", "pen", "model3d", "balloon", "effects", "characters", "locations", "objects", "packs")

/** v33 basit menünün düğme adları (bileşim dışında kullanılabilsin diye bir arada). */
private class SimpleTexts(
    val brush: String, val eraser: String, val shape: String, val image: String, val text: String, val model3d: String,
    val background: String, val paint: String, val elements: String, val select: String, val diagram: String, val formula: String,
    val backgroundFull: String = background,
    val shapeEdit: String = select,
    val expand: String = select,
)

/** v35 basit menüde pencere açan araçlar (seçimleri çizim modunun aracından sonra gelir). */
private val SIMPLE_OVERLAYS = setOf("background", "model3d")

/** v47 açılır alanın düğmeleri (basit menüde bağımsız seçili görünür). */
private val SIMPLE_PANEL_TOOLS = setOf("elements", "layers", "brush_settings")

/** v34 Düzenle satırında şekil düzenlemenin araçları doğrudan: Nokta düzenle, Çizgi düzenle, Alan boya. */
@Composable
private fun shapeDirectTools(pending: ShapeTool?, onTool: (ShapeTool) -> Unit): List<ToolItem> {
    val nodes = stringResource(R.string.edit_nodes)
    val lines = stringResource(R.string.edit_lines)
    val fill = stringResource(R.string.tool_region_fill)
    return listOf(
        ToolItem(shapeToolIcon(ShapeTool.NODES), nodes, { onTool(ShapeTool.NODES) }, selected = pending == ShapeTool.NODES, tag = "edit_nodes"),
        ToolItem(shapeToolIcon(ShapeTool.LINES), lines, { onTool(ShapeTool.LINES) }, selected = pending == ShapeTool.LINES, tag = "edit_lines"),
        ToolItem(shapeToolIcon(ShapeTool.FILL), fill, { onTool(ShapeTool.FILL) }, selected = pending == ShapeTool.FILL, tag = "edit_fill"),
    )
}

/**
 * Resmi düzenle satırı: Döndür, Ölçekle, Kırp; v34 Vektörleştir'in türleri doğrudan (Otomatik, Kenarları, Çizgileri
 * vektörleştir; ayarları alttaki pencerede); Filtre, Arka planı kaldır, Efektler. [vectorizing]: açık penceredeki tür.
 */
@Composable
private fun imageEditTools(
    rotateTool: ToolItem, scaleTool: ToolItem, onCrop: () -> Unit,
    vectorizing: com.karapp.drawguidance.content.trace.EdgeMode?, onVectorize: (com.karapp.drawguidance.content.trace.EdgeMode) -> Unit,
    filterOpen: Boolean, onFilter: () -> Unit, bgRemoveOpen: Boolean, onBgRemove: () -> Unit, effectsOpen: Boolean, onEffects: () -> Unit,
): List<ToolItem> {
    val modes = listOf(
        Triple(com.karapp.drawguidance.content.trace.EdgeMode.AUTO, Icons.Filled.Polyline, R.string.vectorize_direct_auto),
        Triple(com.karapp.drawguidance.content.trace.EdgeMode.EDGES, Icons.Filled.CropFree, R.string.vectorize_direct_edges),
        Triple(com.karapp.drawguidance.content.trace.EdgeMode.CENTERLINE, Icons.Filled.Timeline, R.string.vectorize_direct_lines),
    )
    return listOf(rotateTool, scaleTool, ToolItem(Icons.Filled.Crop, stringResource(R.string.image_crop), onCrop)) +
        modes.map { (m, icon, label) ->
            ToolItem(icon, stringResource(label), { onVectorize(m) }, selected = vectorizing == m, tag = "vectorize_${m.name.lowercase()}")
        } + listOf(
            // v27 Kalem aracı yalnız Çiz menüsünde; çizgi roman filtreleri
            ToolItem(Icons.Filled.FilterVintage, stringResource(R.string.image_filter), onFilter, selected = filterOpen),
            // v30 tüm tuval türlerinde: arka plan kaldırma ve genel efektler
            ToolItem(com.karapp.drawguidance.ui.screens.canvasedit.components.BackgroundRemoveIcon, stringResource(R.string.bg_remove), onBgRemove, selected = bgRemoveOpen),
            ToolItem(com.karapp.drawguidance.ui.screens.canvasedit.components.ImageEffectsIcon, stringResource(R.string.image_effects), onEffects, selected = effectsOpen),
        )
}
