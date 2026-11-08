package com.karapp.drawguidance.ui.screens.canvasedit.components

import android.graphics.Paint
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.ensureActive
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import com.karapp.drawguidance.ui.drawing.LiveStrokeLayer
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.withContext
import java.util.UUID
import androidx.compose.runtime.collectAsState
import com.karapp.drawguidance.data.images.CanvasImageCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.isObject
import com.karapp.drawguidance.data.model.CanvasUi
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.ui.common.hexToColor
import com.karapp.drawguidance.ui.components.BrushIcons
import com.karapp.drawguidance.ui.components.brushLabel
import com.karapp.drawguidance.ui.drawing.StrokeRenderer
import com.karapp.drawguidance.ui.drawing.inkWidth
import com.karapp.drawguidance.ui.drawing.StrokeRenderer.drawStroke
import com.karapp.drawguidance.ui.drawing.StrokeRenderer.withStrokeLayer
import com.karapp.drawguidance.ui.drawing.StrokeRenderer.drawStrokeList
import com.karapp.drawguidance.ui.screens.canvasedit.BrushSettings
import com.karapp.drawguidance.ui.screens.canvasedit.CanvasEditViewModel
import com.karapp.drawguidance.ui.screens.canvasedit.EditMode
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementIndex
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementOps
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ViewportState
import com.karapp.drawguidance.ui.screens.canvasedit.ruler.guideGestures
import com.karapp.drawguidance.ui.theme.NeuColors
import com.karapp.drawguidance.ui.theme.NeuTheme
import com.karapp.drawguidance.ui.theme.neumorphic
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Çizim alanında yapılan hareketlerin ViewModel'e iletildiği geri çağrılar. */
class CanvasActions(
    val onStrokeFinished: (List<StrokePoint>, String) -> Unit,
    /** Dokunulan elemanı seçer (seçime ekler); eleman yoksa false. */
    val onSelectAt: (Offset, Float) -> Boolean,
    /** Dokunulan elemanı seçimden çıkarır; eleman yoksa false. */
    val onDeselectAt: (Offset, Float) -> Boolean,
    val onMove: (dx: Float, dy: Float) -> Unit,
    val onRotate: (degrees: Float, pivot: Offset) -> Unit,
    val onScale: (sx: Float, sy: Float, pivot: Offset) -> Unit,
    val onResize: (left: Int, top: Int, right: Int, bottom: Int) -> Unit,
    /** Elemana 2 sn basılı tutuldu: seçildiyse true. */
    val onLongPressSelect: (Offset, Float) -> Boolean = { _, _ -> false },
    /** v37 kağıdın kenarına 2 sn basılı tutuldu: genişletme modu açıldıysa true (basit çizimde, basit not dışında). */
    val onEdgeHold: () -> Boolean = { false },
    /** Moddan çıkmadan dokunulan elemanı seçer (Taşı/Döndür/Ölçekle'de seçim yokken). */
    val onPickAt: (Offset, Float) -> Boolean = { _, _ -> false },
    /** v16: Taşı/Döndür/Ölçekle'de seçiliyken dokunuş: seçili formülün yuvasıysa formül üstünde düzenlenir. */
    val onSlotTap: (Offset) -> Boolean = { false },
    /** Sürüklenen şeklin çizgileri (önizleme ve ekleme aynı kimliklerle). */
    val buildShape: (gestureId: String, start: Offset, end: Offset) -> List<Stroke> = { _, _, _ -> emptyList() },
    val onShapeFinished: (gestureId: String, start: Offset, end: Offset) -> Unit = { _, _, _ -> },
    val onShapeTap: (gestureId: String, point: Offset) -> Unit = { _, _ -> },
    val onDiagramFinished: (gestureId: String, start: Offset, end: Offset) -> Unit = { _, _, _ -> },
    val onDiagramTap: (gestureId: String, point: Offset) -> Unit = { _, _ -> },
    val onShapeEditTap: (point: Offset, tolerance: Float) -> Unit = { _, _ -> },
    /** v33 Şekil boyama: dokunulan şeklin alanını boyar. */
    val onPaintTap: (point: Offset) -> Unit = { },
    /** v37 çocuk boyamasında kovanın yeri (null: gizli) ve basılı tutmanın sırası (-1: halka yok). */
    val onPaintHover: (point: Offset?, hold: Int) -> Unit = { _, _ -> },
    /** v37 çocuk boyamasında 2 sn basılı tutma: bütün şekil boyanır. */
    val onPaintWhole: (point: Offset) -> Boolean = { false },
    /** v45 çocuk boyamasında boya fırçası seçili mi; darbenin başı (başladıysa true), noktaları, sonu ve bırakılması. */
    val paintBrush: () -> Boolean = { false },
    val onBrushDown: (point: Offset) -> Boolean = { false },
    val onBrushMove: (point: Offset) -> Unit = {},
    val onBrushUp: (point: Offset?) -> Boolean = { false },
    val onBrushCancel: () -> Unit = {},
    /** Şekil düzenlemede sürükleme: ne yapılacağı, önizleme ve sonuç. */
    val onShapeDragBegin: (point: Offset, tolerance: Float, handleDistance: Float) -> com.karapp.drawguidance.ui.screens.canvasedit.ShapeDrag =
        { _, _, _ -> com.karapp.drawguidance.ui.screens.canvasedit.ShapeDrag.NONE },
    val onShapeDragPreview: (kind: com.karapp.drawguidance.ui.screens.canvasedit.ShapeDrag, from: Offset, to: Offset, path: List<Offset>, tolerance: Float) -> Stroke? =
        { _, _, _, _, _ -> null },
    val onShapeDragEnd: (kind: com.karapp.drawguidance.ui.screens.canvasedit.ShapeDrag, from: Offset, to: Offset, path: List<Offset>, tolerance: Float) -> Unit =
        { _, _, _, _, _ -> },
    /** Perspektif ayarında kaçış noktası sürükleme: kağıdın 0..1 oranında konum. */
    val onVanishMove: (index: Int, fx: Float, fy: Float, done: Boolean) -> Unit = { _, _, _, _ -> },
    /** Tuval üstü diyagram düzenlemede dokunuş. */
    val onDiagramEditTap: (point: Offset, tolerance: Float) -> Unit = { _, _ -> },
    val onTextTap: (Offset, Float) -> Unit = { _, _ -> },
    /** v37 Yazı modunda sürüklenerek çizilen kutu (başlangıç, bitiş, dokunma payı). */
    val onTextBox: (Offset, Offset, Float) -> Unit = { _, _, _ -> },
    /** v37 açık yazı var mı (varsa sürükleme görünümü kaydırır). */
    val textDrafting: () -> Boolean = { false },
    val onCropChange: (Rect) -> Unit = {},
    /** Seç'te boş yere çift dokunma: kağıdı sığdır. */
    val onFit: () -> Unit = {},
    /** v17 Seç'te boş yere dokunma: seçim kalkar. */
    val onClearSelection: () -> Unit = {},
    /** v41 Seç'te boş yere dokunma: arka plan eleman gibi seçilir (seçildiyse true; değilse seçim kalkar). */
    val onEmptyTap: (Offset) -> Boolean = { false },
    /** v17 Ekle modunda dokunma: o noktada "buraya ekle" menüsü (ekran ve tuval konumu). */
    val onAddTap: (screen: Offset, point: Offset) -> Unit = { _, _ -> },
    /** v17 Düzenle modunda dokunma: dokunulan elemanın düzenleyicisi açılır. */
    val onEditAt: (Offset, Float) -> Unit = { _, _ -> },
    /** v17 iki parmakla uzun basma: bağlam menüsü (ekran ve tuval konumu). */
    val onContextMenu: (screen: Offset, point: Offset) -> Unit = { _, _ -> },
    /** v20 üç parmakla yana kaydırma: sola geri al (-1), sağa yinele (+1). */
    val onThreeFingerSwipe: (direction: Int) -> Unit = {},
    /** Diyagram düzenlemede sürükleme bu noktada bir ok tutamağından mı başlıyor. */
    val onDiagramHasHandle: (Offset, Float) -> Boolean = { _, _ -> false },
    /** Diyagram düzenlemede tutamaktan çekilen ok bırakıldı. */
    val onDiagramLinkDrag: (from: Offset, to: Offset, tolerance: Float) -> Unit = { _, _, _ -> },
    /** Formül yerleştirme (sürüklenen kutuya ya da dokunulan yere). */
    val onFormulaFinished: (gestureId: String, start: Offset, end: Offset) -> Unit = { _, _, _ -> },
    val onFormulaTap: (gestureId: String, point: Offset) -> Unit = { _, _ -> },
    /** Kalem aracı: dokunma köşe noktası, sürükleme eğri noktası ([done] parmak kalktı). */
    val onPenTap: (point: Offset, tolerance: Float) -> Unit = { _, _ -> },
    val onPenDrag: (start: Offset, current: Offset, done: Boolean) -> Unit = { _, _, _ -> },
    /** v44 kalem yolunda iki nokta arasındaki çizgi tutuldu mu (tutulduysa sürükleme onu eğer). */
    val onPenGrab: (point: Offset, tolerance: Float) -> Boolean = { _, _ -> false },
    /** v43 damlalık ve alanla seçim. */
    val canvasPick: () -> CanvasPick = { CanvasPick.NONE },
    val onEyedrop: (Offset) -> Unit = {},
    val onRectSelect: (Offset, Offset) -> Unit = { _, _ -> },
    val onLassoSelect: (List<Offset>) -> Unit = {},
    val onPenEditDrag: (point: Offset, done: Boolean) -> Unit = { _, _ -> },
    /** v18 kalemin silgi ucuyla (ya da yan tuşla) çizilen çizgi: silgi olarak kaydedilir. */
    val onPenEraserFinished: (List<StrokePoint>, String) -> Unit = { p, id -> onStrokeFinished(p, id) },
    /** v18 kalemin silgi ucunun boyu (son kullanılan silgi boyu). */
    val eraserSize: () -> Float = { 24f },
    /** v21 3B çizim modu açıksa düzlem ve derinlik (kapalıysa null). */
    val draw3d: () -> com.karapp.drawguidance.data.model.Depth3D? = { null },
    /** v22 döndürülebilir 3B sahnenin kamerası (3B çizim modu kapalıysa null). */
    val scene3d: () -> com.karapp.drawguidance.ui.drawing.SceneCamera? = { null },
    /** v23 bir eleman yeni haliyle değişti (yazı kutusu tutamakla boyutlandırıldı). */
    val onReplaceStroke: (Stroke) -> Unit = {},
    /** v40 elemana 5 sn basılı tutuldu: eleman seçilip Taşı moduna geçildiyse true (bkz. [HoldToMove]). */
    val onHoldMove: (Offset, Float) -> Boolean = { _, _ -> false },
    /** v40 cetvel ve şekil kalıbı (açık değilse null): kenara yapışma ve cetvelin taşınması/döndürülmesi. */
    val ruler: () -> com.karapp.drawguidance.ui.screens.canvasedit.ruler.RulerController? = { null },
)

/**
 * Modlara özgü tuval üstü yardımcılar tek parametrede: kalem aracının noktaları, seçili formülün düzenlenebilir
 * yuvaları. Yeni bir yardımcı eklemek DrawingCanvas'a yeni parametre eklemeyi gerektirmez.
 */
data class CanvasOverlay(
    val penAnchors: List<com.karapp.drawguidance.ui.screens.canvasedit.state.PenAnchor> = emptyList(),
    val penPending: com.karapp.drawguidance.ui.screens.canvasedit.state.PenAnchor? = null,
    /** Seçili formülün yuvaları vurgulansın (düzenleme penceresi açıkken odaktaki yuva belirgin). */
    val formulaSlots: Boolean = false,
    val formulaSlot: String? = null,
    /** v16 şekil düzenleme: vektör seçimi (null: vektör noktaları gösterilmez, ör. grafik aracında). */
    val vector: com.karapp.drawguidance.ui.screens.canvasedit.VectorSelection? = null,
    /** v17 çizim düzeltme önerisinin önizlemesi (düzeltilmiş çizgiler, tuval koordinatı; kesik vurgu çizgisiyle). v21: çok parçalı. */
    val correction: List<List<Offset>>? = null,
)

/** Önizleme hangi çizim listesi/tuval boyutu üzerinde başladıysa yalnızca onlara uygulanır. */
private class PreviewState(val strokes: List<Stroke>, val width: Int, val height: Int, val preview: Preview)

/**
 * Ana çizim alanı. Kağıt yakınlaştırılabilir/kaydırılabilir bir görünümde gösterilir
 * ([ViewportState]); dokunuşlar tuval koordinatlarına çevrilir.
 *
 * - İki parmak her modda yakınlaştırır ve kaydırır; iki parmakla kıpırdamadan uzun basmak bağlam menüsünü açar (v17).
 * - Çiz: sürükleme yeni çizim, dokunma nokta. Seç: dokunma seçer, çift dokunma seçimi kaldırır.
 * - 2 sn hareketsiz basılı tutma: altındaki eleman seçilir ve Seç moduna geçilir.
 * - Şekil: sürükleyerek seçili şekli çizer. Yazı: dokunulan yere yazı ekler. Kırp: çerçeveyi daraltır.
 * - Taşı / Döndür / Ölçekle: seçili elemanları dönüştürür. Genişlet: kağıt kenarlarını sürükler.
 *
 * Tamamlanmış çizimler görünüm boyutunda tek bir bitmap önbelleğinde tutulur.
 */
@Composable
fun DrawingCanvas(
    canvas: CanvasUi,
    brushSettings: BrushSettings,
    mode: EditMode,
    elements: ElementIndex,
    selectedIds: Set<String>,
    viewport: ViewportState,
    actions: CanvasActions,
    visualVersion: Long,
    modifier: Modifier = Modifier,
    cropRect: Rect? = null,
    hiddenIds: Set<String> = emptySet(),
    /** Eleman kırpmasında elemanın tüm alanı (null: tuval kırpması). */
    cropArea: Rect? = null,
    /** Tuval üstü diyagram düzenlemede odaktaki adım/ok. */
    diagramFocus: com.karapp.drawguidance.ui.screens.canvasedit.DiagramFocus? = null,
    diagramLinking: Boolean = false,
    shapeTool: com.karapp.drawguidance.ui.screens.canvasedit.ShapeTool = com.karapp.drawguidance.ui.screens.canvasedit.ShapeTool.NODES,
    /** Diyagramda ok ekleme sürüyorsa kaynağı ve çıkış tarafı. */
    diagramLink: com.karapp.drawguidance.ui.screens.canvasedit.state.DiagramLink? = null,
    overlay: CanvasOverlay = CanvasOverlay(),
) {
    val colors = NeuTheme.colors
    val density = LocalDensity.current
    val strokes = canvas.strokes
    // Gizli katmanlardaki elemanlar çizilmez
    val drawStrokes = remember(strokes, canvas.layers) { com.karapp.drawguidance.data.model.Layers.visible(strokes, canvas.layers) }
    // Silgi sürerken nesneler (silgiden etkilenmediği için) canlı katmanın üstünde yeniden çizilir
    val objectStrokes = remember(drawStrokes) { drawStrokes.filter { it.brushType.isObject } }
    val textMeasurer = rememberTextMeasurer()
    val brushNames = BrushType.entries.associateWith { brushLabel(it) }

    // Parmakla çizilen çizgi: kesinleşen parçalar ekran karolarına bir kez yazılır, her karede yalnızca kuyruk çizilir
    val live = remember { LiveStrokeLayer() }
    var liveTick by remember { mutableIntStateOf(0) }
    val finger = remember { mutableStateOf<Offset?>(null) }
    var previewState by remember { mutableStateOf<PreviewState?>(null) }
    // Çift dokunma (Seç: elemanı seçimden çıkar ya da kağıdı sığdır) hareketler arasında hatırlanır
    val taps = remember { TapMemory() }

    val imagesVersion by CanvasImageCache.version.collectAsState()
    val currentCanvas by rememberUpdatedState(canvas)
    val currentSelected by rememberUpdatedState(selectedIds)
    val currentActions by rememberUpdatedState(actions)
    val currentBrush by rememberUpdatedState(brushSettings)
    val currentCrop by rememberUpdatedState(cropRect)
    val currentShapeTool by rememberUpdatedState(shapeTool)
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    // v40 5 sn basılı tutunca Taşı (mod değişse de süren ayrı dinleyici)
    val holdMove = remember { HoldMoveState() }
    // v40 cetvel/kalıp hareketi (tutamak ya da gövde) çizim sayılmaz
    val guideGrab = remember { com.karapp.drawguidance.ui.screens.canvasedit.ruler.GuideGrab() }
    val currentMode by rememberUpdatedState(mode)

    // v18 tablet kalemi: havada imleç (kalem ekrana değmeden fırça boyu), hareket tahmini, düşük gecikmeli çizim
    val hostView = androidx.compose.ui.platform.LocalView.current
    val hover = remember { mutableStateOf<Offset?>(null) }
    val pen = remember { PenSession() }
    val predicted = remember { mutableStateOf<List<Offset>>(emptyList()) }
    val stylusState by com.karapp.drawguidance.platform.Stylus.state.collectAsState()
    val lowLatency by com.karapp.drawguidance.platform.Stylus.lowLatency.collectAsState()
    val frontInk = remember { mutableStateOf<Any?>(null) }

    BoxWithConstraints(modifier = modifier.fillMaxSize().clipToBounds()) {
        val viewW = constraints.maxWidth
        val viewH = constraints.maxHeight
        val marginPx = with(density) { 24.dp.toPx() }
        viewport.layout(IntSize(viewW, viewH), canvas.width, canvas.height, marginPx)

        // v18 düşük gecikmeli kalem çizimi (Android 10+): yalnızca kalem algılandıysa ve Çiz modunda kurulur
        if (Build.VERSION.SDK_INT >= 29 && com.karapp.drawguidance.platform.Stylus.lowLatencySupported && lowLatency &&
            (stylusState.used || stylusState.attached) && mode == EditMode.DRAW
        ) {
            FrontBufferSurface(frontInk)
        }

        val activePreview = previewState?.takeIf {
            it.strokes === strokes && it.width == canvas.width && it.height == canvas.height
        }?.preview

        // v37 seçili çizimler bir kez süzülür; çizim sırasında (her karede) bütün çizimler taranmaz
        val selectedStrokes = remember(strokes, selectedIds) {
            if (selectedIds.isEmpty()) emptyList() else strokes.filter { it.id in selectedIds }
        }
        // Şekil düzenlemede düzenlenen şekil önbelleğin dışında canlı çizilir (sürüklerken önizleme yerine geçer)
        val editedShapeId = remember(mode, selectedStrokes) {
            if (mode != EditMode.SHAPE_EDIT) null else selectedStrokes.firstOrNull { it.shape != null }?.id
        }
        // v40 Seç'te tutamakla boyutlanan yazı kutusu (ya da sürüklenen balon kuyruğu) önbelleğin dışında canlı çizilir
        val reshapingId = if (mode == EditMode.SELECT) (activePreview as? Preview.ShapeEdit)?.stroke?.id else null
        val excluded = remember(mode, selectedIds, hiddenIds, editedShapeId, reshapingId) {
            val transforming = when {
                mode.transformsSelection -> selectedIds
                editedShapeId != null -> setOf(editedShapeId)
                reshapingId != null -> setOf(reshapingId)
                else -> emptySet()
            }
            if (hiddenIds.isEmpty()) transforming else transforming + hiddenIds
        }
        val cacheTarget = remember(drawStrokes, excluded) { if (excluded.isEmpty()) drawStrokes else drawStrokes.filterNot { it.id in excluded } }
        val cacheKey = CacheKey(viewW, viewH, viewport.settledScale, viewport.settledOffset, canvas.width, canvas.height, excluded, imagesVersion)
        val holder = remember { StrokeCache() }
        var cacheTick by remember { mutableIntStateOf(0) }
        // Önbelleği güncel tut: görüntü değişmediyse hiçbir şey çizme, yalnızca yeni çizim eklendiyse
        // sadece onu mevcut bitmap'e çiz, aksi halde arka planda baştan üret (bu sırada eski görüntü + yeni çizimler gösterilir).
        run {
            val bitmap = holder.bitmap
            if (bitmap != null && holder.key == cacheKey) {
                when {
                    holder.version == visualVersion -> holder.drawn = cacheTarget
                    // Silgi eklendiyse görüntü baştan üretilir: silgi yalnızca kendi katmanındaki fırça izlerini silmeli
                    isAppend(holder.drawn, cacheTarget) &&
                        cacheTarget.subList(holder.drawn.size, cacheTarget.size).none { it.brushType == BrushType.ERASER } -> {
                        val added = cacheTarget.subList(holder.drawn.size, cacheTarget.size)
                        val handoff = live.finished && added.size == 1 && added[0].id == live.strokeId &&
                            live.matches(cacheKey.scale, cacheKey.offset.x, cacheKey.offset.y)
                        if (handoff) {
                            // Parmakla çizilen karolar önbelleğe kopyalanır: çizgi yeniden çizilmez
                            val clip = cacheKey.clip()
                            live.blitInto(android.graphics.Canvas(bitmap.asAndroidBitmap()), android.graphics.RectF(clip.left, clip.top, clip.right, clip.bottom))
                        } else {
                            StrokeRenderer.drawIntoViewportCache(bitmap, added, cacheKey.scale, cacheKey.offset, cacheKey.clip())
                        }
                        holder.drawn = cacheTarget
                        holder.version = visualVersion
                    }
                    else -> holder.requests.value = RenderRequest(cacheKey, cacheTarget, visualVersion)
                }
            } else {
                holder.requests.value = RenderRequest(cacheKey, cacheTarget, visualVersion)
            }
        }
        // Parmak kalktıktan sonra önbellek güncellendiyse canlı katman bırakılır
        if (live.finished && holder.drawn === cacheTarget) live.release()
        LaunchedEffect(holder) {
            // Yeni bir görünüm istendiğinde süren çizim iptal edilir (eski görünüm için boşuna çalışılmaz)
            holder.requests.filterNotNull().collectLatest { request ->
                if (holder.key == request.key && holder.version == request.version) return@collectLatest
                val key = request.key
                val bitmap = withContext(Dispatchers.Default) {
                    StrokeRenderer.renderViewportCache(request.target, key.width, key.height, key.scale, key.offset, key.clip()) { ensureActive() }
                }
                holder.bitmap = bitmap
                holder.key = key
                holder.drawn = request.target
                holder.version = request.version
                cacheTick++
            }
        }
        val movingStrokes = remember(drawStrokes, excluded, hiddenIds) { drawStrokes.filter { it.id in excluded && it.id !in hiddenIds } }
        // Düzenlenen şeklin vektör görünümü (sürüklerken önizlemedeki şeklinki)
        val editedForOverlay = if (editedShapeId == null || overlay.vector == null) null
        else (activePreview as? Preview.ShapeEdit)?.stroke?.takeIf { it.id == editedShapeId } ?: strokes.firstOrNull { it.id == editedShapeId }
        // v36 basit çizim türlerinde sade (az noktalı) görünüm; düzenleyiciyle aynı noktalar
        val coarseVector = canvas.kind.simple
        val vectorGraph = remember(editedForOverlay, coarseVector) { editedForOverlay?.let { com.karapp.drawguidance.ui.drawing.VectorEdit.graph(it, coarseVector) } }
        val selectedElements = remember(elements, selectedIds) { elements.selectedElements(selectedIds) }
        val elementBoxes = remember(selectedElements, strokes) {
            // Tek geçişte kimlik → çizim eşlemesi: büyük çizimlerde seçim kutuları hızlı
            val byId = if (selectedElements.isEmpty()) emptyMap() else strokes.associateBy { it.id }
            selectedElements.mapNotNull { element ->
                ElementOps.bounds(element.strokeIds.mapNotNull { byId[it] })?.let { it to "${brushNames.getValue(element.brushType)} ${element.number}" }
            }
        }
        val selectionBounds = remember(strokes, selectedIds) { ElementOps.selectionBounds(strokes, selectedIds) }
        // v27 tek yazı seçili mi (Ölçekle'de kare tutamaklar yerine yazı kutusu tutamakları)
        val singleTextSelected = remember(strokes, selectedIds) {
            selectedIds.size == 1 && strokes.any { it.id in selectedIds && it.brushType == BrushType.TEXT }
        }
        // v24 açık sayfanın kağıt rengi (not tuvalinde sayfaların kendi rengi olabilir)
        val pagePaper = canvas.pagePaperHex
        val paperPaints = remember(colors, pagePaper) { PaperPaints(colors, hexToColor(pagePaper), density.density) }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("drawing_canvas")
                .pointerInput(Unit) {
                    // Kalem ekrana değmeden (havada) fırça boyu imleci; kalem yakındayken parmak dokunuşları yok sayılır
                    awaitPointerEventScope {
                        while (true) {
                            val e = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                            val c = e.changes.firstOrNull() ?: continue
                            val penType = c.type == androidx.compose.ui.input.pointer.PointerType.Stylus ||
                                c.type == androidx.compose.ui.input.pointer.PointerType.Eraser
                            if (!penType) continue
                            com.karapp.drawguidance.platform.Stylus.onStylusInput()
                            val hovering = !c.pressed && (e.type == androidx.compose.ui.input.pointer.PointerEventType.Move ||
                                e.type == androidx.compose.ui.input.pointer.PointerEventType.Enter)
                            hover.value = if (hovering) c.position else null
                            if (hovering) pen.lastHover = c.uptimeMillis
                        }
                    }
                }
                .pointerInput(Unit) {
                    guideGestures({ currentActions.ruler() }, { currentMode == EditMode.DRAW }, viewport, guideGrab)
                }
                .pointerInput(Unit) {
                    holdToMove(
                        holdMove, viewport, mode = { currentMode }, kind = { currentCanvas.kind },
                        onHold = { p, tolerance -> !guideGrab.active && currentActions.onHoldMove(p, tolerance) },
                        setPreview = { p -> previewState = p?.let { PreviewState(currentCanvas.strokes, currentCanvas.width, currentCanvas.height, it) } },
                        onMove = { dx, dy -> currentActions.onMove(dx, dy) },
                        onFired = { haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress) },
                    )
                }
                .pointerInput(mode) {
                    val slop = viewConfiguration.touchSlop
                    val handleTolerance = 30.dp.toPx()
                    val edgeTolerance = 36.dp.toPx()
                    val hitTolerancePx = 14.dp.toPx()
                    val threeFingerDistance = 60.dp.toPx()

                    /**
                     * v18 kalemle çizim: kalem değdiği anda çizmeye başlar (dokunma eşiği beklenmez), ara noktalar
                     * (yüksek örnekleme), basınç ve eğim kullanılır; kalem kalkınca diğer parmaklar (avuç) hâlâ
                     * değiyorsa yeni kalem dokunuşu beklenir. Avuç ve parmaklar bu sırada yok sayılır.
                     */
                    suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.penSession(
                        firstPen: androidx.compose.ui.input.pointer.PointerInputChange,
                        firstEvent: androidx.compose.ui.input.pointer.PointerEvent,
                    ) {
                        var penDown = firstPen
                        var penEvent = firstEvent
                        while (true) {
                            penStroke(penDown, penEvent, hostView, currentCanvas, currentSelected, currentActions, currentBrush, currentCrop,
                                currentShapeTool, live, viewW, viewH, hitTolerancePx = 14.dp.toPx(), taps = taps, pen = pen,
                                frontInk = frontInk.value, setPreview = { p -> previewState = p?.let { PreviewState(currentCanvas.strokes, currentCanvas.width, currentCanvas.height, it) } },
                                refreshLive = { liveTick++ }, predicted = predicted, viewport = viewport)
                            // Kalem kalktı: başka dokunuş yoksa oturum biter (avuç hâlâ değiyorsa yeni kalem beklenir)
                            if (currentEvent.changes.none { it.pressed }) return
                            while (true) {
                                val e = awaitPointerEvent()
                                e.changes.forEach { it.consume() }
                                val next = e.changes.firstOrNull { it.isPen && it.pressed && !it.previousPressed }
                                if (next != null) { penDown = next; penEvent = e; break }
                                if (e.changes.none { it.pressed }) return
                            }
                        }
                    }

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // v40 cetvel/kalıp bu hareketi aldı: parmak kalkana kadar çizim alanı bir şey yapmaz
                        if (guideGrab.active) {
                            do { val e = awaitPointerEvent() } while (e.changes.any { it.pressed })
                            return@awaitEachGesture
                        }
                        if (down.isPen) com.karapp.drawguidance.platform.Stylus.onStylusInput()
                        if (down.isPen && mode == EditMode.DRAW) {
                            penSession(down, currentEvent)
                            return@awaitEachGesture
                        }
                        // Kalem yakınken (havada) gelen parmak/avuç dokunuşu yok sayılır; kalem değerse çizer
                        val penNear = mode == EditMode.DRAW && down.uptimeMillis - pen.lastHover < PEN_PROXIMITY_MS
                        if (penNear) {
                            while (true) {
                                val e = awaitPointerEvent()
                                e.changes.forEach { it.consume() }
                                val p = e.changes.firstOrNull { it.isPen && it.pressed && !it.previousPressed }
                                if (p != null) { com.karapp.drawguidance.platform.Stylus.onStylusInput(); penSession(p, e); return@awaitEachGesture }
                                if (e.changes.none { it.pressed }) return@awaitEachGesture
                            }
                        }
                        val start = down.position
                        var multiTouch = false
                        var dragging = false
                        var upTime = down.uptimeMillis
                        val base = currentCanvas
                        // v18 kalem modu: kalem kullanıldıysa Çiz'de parmak çizmez, kaydırır/yakınlaştırır
                        val fingerPans = mode == EditMode.DRAW && com.karapp.drawguidance.platform.Stylus.fingerShouldPan
                        // Moda göre dokunuş davranışı (Strategy): her hareket kendi aracını oluşturur
                        val tool = if (fingerPans) PanTool() else CanvasTools.create(mode, currentActions.canvasPick(), currentActions.paintBrush())
                        val ctx = GestureContext(
                            viewport = viewport, base = base, selected = currentSelected, actions = currentActions,
                            brush = currentBrush, crop = currentCrop, shapeTool = currentShapeTool, live = live,
                            viewW = viewW, viewH = viewH, start = start, startCanvas = viewport.toCanvas(start),
                            handleTolerance = handleTolerance, edgeTolerance = edgeTolerance, hitTolerancePx = hitTolerancePx,
                            taps = taps,
                            setPreview = { p -> previewState = p?.let { PreviewState(base.strokes, base.width, base.height, it) } },
                            setFinger = { finger.value = it },
                            refreshLive = { liveTick++ },
                        )
                        var panOnly = false
                        // 2 sn hareketsiz basılı tutunca eleman seçilir: yalnızca çizim, seçim ve dönüşüm modlarında
                        // v37 basit çizimde kağıdın kenarına basılı tutunca genişletme modu açılır
                        val atEdge = mode != EditMode.EXPAND && paperEdgesAt(viewport, base.width, base.height, start, edgeTolerance).any { it }
                        var longPressPending = tool.longPressSelects || atEdge
                        var longPressed = false

                        fun dragTo(position: Offset, delta: Offset) {
                            if (panOnly) viewport.panBy(delta) else tool.move(ctx, position, viewport.toCanvas(position))
                        }

                        var lastUptime = down.uptimeMillis
                        // v17: iki parmakla kıpırdamadan uzun basma bağlam menüsünü açar
                        var twoFingerPending = false
                        var twoFingerStart = 0L
                        var twoFingerCentroid = Offset.Zero
                        var twoFingerMoved = 0f
                        var twoFingerZoom = 1f
                        // v20 üç parmak: yana kaydırınca geri al / yinele (hareket başına bir kez)
                        var threeFinger = false
                        var threeStart = Offset.Zero
                        var threeDone = false
                        ctx.eventTime = down.uptimeMillis
                        tool.down(ctx)
                        while (true) {
                            // v20 çizim hızı: parmak dururken de mürekkep yetişsin diye kısa aralıklarla zaman ilerler
                            val ticking = dragging && !panOnly && !multiTouch && tool.needsTicks
                            val event = when {
                                twoFingerPending -> {
                                    val remaining = TWO_FINGER_MENU_MS - (lastUptime - twoFingerStart)
                                    withTimeoutOrNull(remaining.coerceAtLeast(1L)) { awaitPointerEvent() }
                                }
                                longPressPending && !dragging && !multiTouch -> {
                                    val remaining = LONG_PRESS_MS - (lastUptime - down.uptimeMillis)
                                    withTimeoutOrNull(remaining.coerceAtLeast(1L)) { awaitPointerEvent() }
                                }
                                ticking -> withTimeoutOrNull(TICK_MS) { awaitPointerEvent() }
                                else -> awaitPointerEvent()
                            }
                            if (event == null && ticking) {
                                lastUptime += TICK_MS
                                tool.tick(ctx, lastUptime)
                                continue
                            }
                            if (event == null) {
                                if (twoFingerPending) {
                                    // İki parmak basılı ve kıpırdamadı: bağlam menüsü (parmak kalkana kadar başka iş yok)
                                    twoFingerPending = false
                                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    currentActions.onContextMenu(twoFingerCentroid, viewport.toCanvas(twoFingerCentroid))
                                    longPressed = true
                                    continue
                                }
                                // Parmak hareket etmeden basılı kaldı: kağıdın kenarındaysa genişletme, değilse altındaki eleman seçilir
                                longPressPending = false
                                if (atEdge && currentActions.onEdgeHold()) {
                                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    longPressed = true
                                } else if (tool.longPressSelects && tool.longPress(ctx)) {
                                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    longPressed = true
                                }
                                continue
                            }
                            lastUptime = maxOf(lastUptime, event.changes.firstOrNull()?.uptimeMillis ?: lastUptime)
                            ctx.eventTime = lastUptime
                            if (mode == EditMode.DRAW) {
                                val penNow = event.changes.firstOrNull { it.isPen && it.pressed && !it.previousPressed }
                                if (penNow != null) {
                                    if (dragging && !panOnly) tool.cancel(ctx)
                                    if (multiTouch || panOnly) viewport.settle()
                                    com.karapp.drawguidance.platform.Stylus.onStylusInput()
                                    penSession(penNow, event)
                                    return@awaitEachGesture
                                }
                            }
                            // v40 5 sn basılı tutmayla Taşı'ya geçildi: hareketin kalanı o dinleyicinin
                            if (holdMove.engaged && !longPressed) {
                                if (dragging && !panOnly) tool.cancel(ctx)
                                longPressed = true
                            }
                            if (longPressed) {
                                // Seçime geçildi: parmak kalkana kadar başka bir şey yapılmaz
                                event.changes.forEach { it.consume() }
                                if (event.changes.none { it.pressed }) break
                                continue
                            }
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.size < 2) twoFingerPending = false
                            if (pressed.isEmpty()) {
                                upTime = event.changes.firstOrNull()?.uptimeMillis ?: upTime
                                break
                            }
                            if (pressed.size >= 3 || threeFinger) {
                                // v20 üç parmakla yana kaydırma: kaydırma/yakınlaştırma yapılmaz
                                val centroid = event.calculateCentroid(useCurrent = true)
                                if (!threeFinger) {
                                    threeFinger = true
                                    twoFingerPending = false
                                    if (dragging && !panOnly) tool.cancel(ctx)
                                    if (multiTouch) viewport.settle()
                                    multiTouch = true
                                    threeStart = centroid
                                }
                                if (!threeDone && pressed.size >= 3 && centroid != Offset.Unspecified) {
                                    val d = centroid - threeStart
                                    if (kotlin.math.abs(d.x) > threeFingerDistance && kotlin.math.abs(d.x) > kotlin.math.abs(d.y) * 1.5f) {
                                        threeDone = true
                                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                        currentActions.onThreeFingerSwipe(if (d.x < 0) -1 else 1)
                                    }
                                }
                                event.changes.forEach { it.consume() }
                            } else if (pressed.size >= 2) {
                                val centroid = event.calculateCentroid(useCurrent = true)
                                if (!multiTouch) {
                                    multiTouch = true
                                    if (dragging && !panOnly) tool.cancel(ctx)
                                    // Tek parmak henüz sürüklemediyse iki parmakla uzun basma sayılabilir
                                    if (!dragging && pressed.size == 2 && centroid != Offset.Unspecified) {
                                        twoFingerPending = true
                                        twoFingerStart = lastUptime
                                        twoFingerCentroid = centroid
                                        twoFingerMoved = 0f
                                        twoFingerZoom = 1f
                                    }
                                } else if (pressed.size > 2) twoFingerPending = false
                                val pan = event.calculatePan()
                                val zoom = event.calculateZoom()
                                if (twoFingerPending) {
                                    twoFingerMoved += pan.getDistance()
                                    twoFingerZoom *= zoom
                                    if (twoFingerMoved > slop || kotlin.math.abs(twoFingerZoom - 1f) > 0.08f) twoFingerPending = false
                                }
                                viewport.panBy(pan)
                                if (centroid != Offset.Unspecified) viewport.zoomBy(zoom, centroid)
                                event.changes.forEach { it.consume() }
                            } else if (!multiTouch) {
                                val change = pressed.first()
                                if (!dragging && (change.position - start).getDistance() > slop) {
                                    dragging = true
                                    panOnly = !tool.begin(ctx)
                                    dragTo(change.position, change.position - start)
                                } else if (dragging) {
                                    // v18: çizerken ara noktalar da (hızlı çizgide köşeler oluşmasın)
                                    if (!panOnly && tool is DrawTool) change.historical.forEach { h ->
                                        ctx.eventTime = maxOf(ctx.eventTime, h.uptimeMillis)
                                        tool.move(ctx, h.position, viewport.toCanvas(h.position))
                                    }
                                    ctx.eventTime = lastUptime
                                    dragTo(change.position, change.positionChange())
                                }
                                if (dragging) change.consume()
                            }
                        }

                        when {
                            threeFinger -> Unit
                            longPressed -> if (multiTouch) viewport.settle()
                            multiTouch -> viewport.settle()
                            dragging && panOnly -> viewport.settle()
                            dragging -> tool.end(ctx, previewState?.takeIf { it.strokes === base.strokes }?.preview)
                            else -> tool.tap(ctx, viewport.toCanvas(start), upTime)
                        }
                        tool.up(ctx)
                    }
                },
        ) {
            val scale = viewport.scale
            val offset = viewport.offset
            val expandRect = (activePreview as? Preview.Expand)?.rect
            val paperCanvas = expandRect ?: Rect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat())
            val paper = viewport.toScreen(paperCanvas)

            drawPaper(paper, paperPaints)
            // v23 arka plan resmi ve deseni (kağıt renginin üstünde, kılavuz ve çizimlerin altında)
            val decor = canvas.pageBackground
            if (!decor.isEmpty && imagesVersion >= 0) drawIntoCanvas { c ->
                val native = c.nativeCanvas
                val save = native.save()
                native.clipPath(android.graphics.Path().apply {
                    addRoundRect(android.graphics.RectF(paper.left, paper.top, paper.right, paper.bottom), paperPaints.radius, paperPaints.radius, android.graphics.Path.Direction.CW)
                })
                com.karapp.drawguidance.ui.drawing.BackgroundRenderer.drawCanvasBackground(
                    native, decor, paper.left, paper.top, paper.right, paper.bottom, scale, paperPaints.fill.color, vector = false,
                )
                native.restoreToCount(save)
            }
            with(com.karapp.drawguidance.ui.drawing.GuideRenderer) {
                drawGuide(canvas.guide, paper, scale, highlightVanish = mode == EditMode.PERSPECTIVE)
                // v21 3B çizim modunda izometrik ızgara (etkin düzlem belirgin)
                if (mode == EditMode.DRAW) drawDraw3d(canvas.guide, paper, scale, canvas.width, canvas.height)
            }
            // v40 sayfa numarası, üst bilgi ve alt bilgi (çıktılardaki yerleşimle)
            if (!canvas.pageSettings.isEmpty) drawIntoCanvas { c ->
                val origin = viewport.toScreen(Rect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat()))
                com.karapp.drawguidance.ui.drawing.PageDecorRenderer.draw(
                    c.nativeCanvas, canvas.pageSettings, canvas.currentPage, canvas.pageCount,
                    canvas.width.toFloat(), canvas.height.toFloat(), paperPaints.fill.color, origin.left, origin.top, scale,
                    cover = canvas.hasCover,
                )
            }

            val pendingAppended = if (holder.drawn !== cacheTarget && isAppend(holder.drawn, cacheTarget)) {
                // Bekleyen silgiler doğrudan çizilmez (nesneleri de silerdi); görüntü yenilenince görünür
                cacheTarget.subList(if (holder.bitmap == null) 0 else holder.drawn.size, cacheTarget.size)
                    .filter { it.brushType != BrushType.ERASER }
            } else emptyList()
            val liveVisible = liveTick >= 0 && live.isActive && !(live.finished && holder.drawn === cacheTarget)
            // Silgi yalnızca çizimleri silsin diye ayrı katman; gerekmedikçe (çoğu karede) açılmaz
            val needsLayer = movingStrokes.isNotEmpty() || (liveVisible && live.isEraser) ||
                pendingAppended.any { it.brushType == BrushType.ERASER }
            val paperLayer = Rect(paper.left, paper.top, paper.right, paper.bottom).intersect(Rect(Offset.Zero, size))
            if (needsLayer) drawIntoCanvas { it.saveLayer(paperLayer, androidx.compose.ui.graphics.Paint()) }
            clipRect(paper.left, paper.top, paper.right, paper.bottom) {
                val cached = holder.bitmap
                val cachedKey = holder.key
                if (cacheTick >= 0 && cached != null && cachedKey != null) {
                    val k = scale / cachedKey.scale
                    translate(offset.x - cachedKey.offset.x * k, offset.y - cachedKey.offset.y * k) {
                        scale(k, k, pivot = Offset.Zero) { drawImage(cached) }
                    }
                }
                translate(offset.x, offset.y) {
                    scale(scale, scale, pivot = Offset.Zero) {
                        // Önbellek arka planda yenilenirken sonradan eklenen çizimler doğrudan çizilir
                        pendingAppended.forEach { if (!(liveVisible && it.id == live.strokeId)) drawStroke(it) }
                        (activePreview as? Preview.Shape)?.let { shapePreview ->
                            currentActions.buildShape(shapePreview.gestureId, shapePreview.start, shapePreview.end).forEach { drawStroke(it) }
                        }
                        (activePreview as? Preview.Box)?.let { box ->
                            val r = Rect(
                                minOf(box.start.x, box.end.x), minOf(box.start.y, box.end.y),
                                maxOf(box.start.x, box.end.x), maxOf(box.start.y, box.end.y),
                            )
                            drawRect(
                                colors.accent, topLeft = r.topLeft, size = r.size,
                                style = DrawStroke(
                                    width = 2f / scale,
                                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f / scale, 8f / scale), 0f),
                                ),
                            )
                        }
                        (activePreview as? Preview.Lasso)?.let { lasso ->
                            if (lasso.points.size >= 2) {
                                val path = androidx.compose.ui.graphics.Path().apply {
                                    moveTo(lasso.points[0].x, lasso.points[0].y)
                                    for (i in 1 until lasso.points.size) lineTo(lasso.points[i].x, lasso.points[i].y)
                                    close()
                                }
                                drawPath(path, colors.accent.copy(alpha = 0.10f))
                                drawPath(
                                    path, colors.accent,
                                    style = DrawStroke(width = 2f / scale, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f / scale, 8f / scale), 0f)),
                                )
                            }
                        }
                        (activePreview as? Preview.Guideline)?.let { line ->
                            drawLine(
                                colors.accent, line.from, line.to, 2f / scale,
                                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(12f / scale, 8f / scale), 0f),
                            )
                        }
                        if (movingStrokes.isNotEmpty()) {
                            // v37 Taşı/Döndür önizlemesi tuval dönüşümüyle çizilir: her karede bütün noktalar yeniden
                            // hesaplanmaz, çizimlerin önbellekteki resimleri de bozulmaz
                            val move = activePreview as? Preview.Move
                            val turn = activePreview as? Preview.Rotate
                            val shown = if (move != null || turn != null) movingStrokes else applyPreview(movingStrokes, activePreview)
                            // Seçimdeki silgiler yalnızca seçili çizimleri silsin diye ayrı katman
                            withStrokeLayer(Rect(-1e5f, -1e5f, 1e5f, 1e5f)) {
                                when {
                                    move != null -> translate(move.delta.x, move.delta.y) { drawStrokeList(shown) }
                                    turn != null -> rotate(turn.degrees, turn.pivot) { drawStrokeList(shown) }
                                    else -> drawStrokeList(shown)
                                }
                            }
                            // Şekil düzenleme (v16): vektör noktaları, seçili noktalar/çizgi ve çizginin tutamakları
                            if (vectorGraph != null && overlay.vector != null) {
                                // Tutamak uzaklığı ShapeEditTool ile aynı: dokunma payının 1,3 katı (39 dp)
                                drawVectorEditing(
                                    vectorGraph, shapeTool, overlay.vector, scale, colors, 14.dp.toPx(), 39.dp.toPx(),
                                    dragging = activePreview is Preview.ShapeEdit,
                                )
                            }
                        }
                        // Seçili formülün düzenlenebilir yuvaları (katsayılar, bilinmeyenler)
                        if (overlay.formulaSlots) {
                            selectedStrokes.firstOrNull { it.brushType == BrushType.FORMULA }?.let { f ->
                                drawIntoCanvas { com.karapp.drawguidance.ui.drawing.FormulaRenderer.drawSlots(it.nativeCanvas, f, overlay.formulaSlot, colors.accent.toArgb()) }
                            }
                        }
                        if (mode == EditMode.PEN) drawPenPath(overlay, scale, colors, 14.dp.toPx())
                        overlay.correction?.filter { it.size >= 2 }?.takeIf { it.isNotEmpty() }?.let { parts ->
                            val path = androidx.compose.ui.graphics.Path().apply {
                                parts.forEach { pts ->
                                    moveTo(pts[0].x, pts[0].y)
                                    for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
                                }
                            }
                            drawPath(path, colors.background.copy(alpha = 0.85f), style = DrawStroke(width = 6f / scale, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                            drawPath(
                                path, colors.accent,
                                style = DrawStroke(
                                    width = 3f / scale, cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(12f / scale, 7f / scale), 0f),
                                ),
                            )
                        }
                    }
                }
                if (liveVisible) drawIntoCanvas { live.drawTo(it.nativeCanvas) }
                // v18 hareket tahmini: canlı çizginin ucundan tahmin edilen yere geçici kuyruk (kaydedilmez)
                val prediction = predicted.value
                if (liveVisible && !live.finished && !live.isEraser && prediction.isNotEmpty()) {
                    live.lastPoint()?.let { last ->
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(last.x, last.y)
                            prediction.forEach { lineTo(it.x, it.y) }
                        }
                        translate(offset.x, offset.y) {
                            scale(scale, scale, pivot = Offset.Zero) {
                                drawPath(
                                    path, hexToColor(brushSettings.colorHex).copy(alpha = brushSettings.opacity.coerceIn(0.1f, 1f)),
                                    style = DrawStroke(width = live.inkWidth * last.pressure.coerceIn(0.35f, 1f), cap = androidx.compose.ui.graphics.StrokeCap.Round),
                                )
                            }
                        }
                    }
                }
                if (liveVisible && live.isEraser && objectStrokes.isNotEmpty()) {
                    // Silgi nesneleri silmez: silerken nesneler canlı silginin üstünde gösterilir
                    val visibleRect = Rect(viewport.toCanvas(paper.topLeft), viewport.toCanvas(paper.bottomRight))
                    translate(offset.x, offset.y) {
                        scale(scale, scale, pivot = Offset.Zero) {
                            StrokeRenderer.visibleIn(objectStrokes, visibleRect).forEach { drawStroke(it) }
                        }
                    }
                }
            }
            if (needsLayer) drawIntoCanvas { it.restore() }

            // v18 kalem havadayken fırça boyu imleci
            val hoverAt = hover.value
            if (hoverAt != null && mode == EditMode.DRAW) {
                val eraser = brushSettings.brushType == BrushType.ERASER
                val w = brushSettings.brushType.inkWidth(brushSettings.sizePx, brushSettings.strength)
                val r = max(3.dp.toPx(), w * scale / 2f)
                drawCircle(Color.White.copy(alpha = 0.9f), r + 1f, hoverAt, style = DrawStroke(width = 3f))
                drawCircle(if (eraser) colors.content else hexToColor(brushSettings.colorHex), r, hoverAt, style = DrawStroke(width = 1.5f))
            }

            // Seçili elemanlar (Elemanlar panelinden seçilenler dahil, kırpma/genişletme dışında her modda):
            // her birinin kutusu ve köşesinde adı
            val transformPreview = activePreview?.takeIf { mode.transformsSelection }
            if (mode != EditMode.CROP && mode != EditMode.EXPAND) {
                elementBoxes.forEach { (bounds, name) ->
                    drawElementBox(bounds, name, transformPreview, viewport, colors, textMeasurer)
                }
            }
            // v23 tek yazı seçiliyken kutu tutamakları: sağ kenar en, alt kenar boy, köşe ikisi
            // (v27: Taşı yalnız taşır; tutamaklar Düzenle → Ölçekle'de)
            // v40 basit çizim türlerinde Seç ve Taşı'da da (yazı kutusu boyutu)
            if (singleTextSelected && SimpleTextResize.handlesShown(mode, canvas.kind)) {
                val text = selectedStrokes.firstOrNull { it.brushType == BrushType.TEXT }
                if (text != null) {
                    val shown = (activePreview as? Preview.ShapeEdit)?.stroke?.takeIf { it.id == text.id } ?: text
                    // Tutamaklar kutunun biraz dışına çizilir (son harfin ya da alt satırın üstünü örtmesin)
                    val sq = shown.points.map { viewport.toScreen(Offset(it.x, it.y)) }
                    fun unit(a: Offset, b: Offset): Offset = (b - a).let { d -> val l = d.getDistance(); if (l < 1e-3f) Offset.Zero else d / l }
                    val outU = if (sq.size >= 4) unit((sq[0] + sq[3]) / 2f, (sq[1] + sq[2]) / 2f) else Offset.Zero
                    val outV = if (sq.size >= 4) unit((sq[0] + sq[1]) / 2f, (sq[3] + sq[2]) / 2f) else Offset.Zero
                    com.karapp.drawguidance.ui.drawing.TextBoxResize.handles(shown).forEach { (p, kind) ->
                        val r = 7.dp.toPx()
                        val gap = r * 0.9f + 2.dp.toPx()
                        val c = viewport.toScreen(p) + when (kind) {
                            com.karapp.drawguidance.ui.drawing.TextBoxResize.WIDTH -> outU * gap
                            com.karapp.drawguidance.ui.drawing.TextBoxResize.HEIGHT -> outV * gap
                            else -> (outU + outV) * (gap * 0.7f)
                        }
                        if (kind == com.karapp.drawguidance.ui.drawing.TextBoxResize.BOTH) {
                            drawCircle(colors.background, r + 2.dp.toPx(), c)
                            drawCircle(colors.accent, r, c)
                        } else {
                            val horizontal = kind == com.karapp.drawguidance.ui.drawing.TextBoxResize.HEIGHT
                            val w = if (horizontal) r * 3.2f else r * 1.3f
                            val h = if (horizontal) r * 1.3f else r * 3.2f
                            drawRoundRect(colors.background, c - Offset(w / 2 + 2f, h / 2 + 2f), Size(w + 4f, h + 4f), androidx.compose.ui.geometry.CornerRadius(h))
                            drawRoundRect(colors.accent, c - Offset(w / 2, h / 2), Size(w, h), androidx.compose.ui.geometry.CornerRadius(h))
                        }
                    }
                }
            }

            // v43 basit türlerde seçili tek şekil/resmin köşe tutamakları (Seç ve Taşı'da; köşeden sürükleyince boyutlanır)
            if (selectedIds.size == 1 && SimpleElementResize.handlesShown(mode, canvas.kind)) {
                val el = selectedStrokes.firstOrNull()?.takeIf { SimpleElementResize.eligible(it) }
                if (el != null) {
                    val shown = (activePreview as? Preview.ShapeEdit)?.stroke?.takeIf { it.id == el.id } ?: el
                    SimpleElementResize.bounds(shown)?.let { b ->
                        val r = 7.dp.toPx()
                        SimpleElementResize.corners(b).forEach { (p, _) ->
                            val c = viewport.toScreen(p)
                            drawCircle(colors.background, r + 2.dp.toPx(), c)
                            drawCircle(colors.accent, r, c)
                        }
                    }
                }
            }
            // v27 seçili balonun kuyruk uçları (Seç ve Ölçekle'de sürüklenir)
            if ((mode == EditMode.SELECT || mode == EditMode.SCALE) && selectedIds.size == 1) {
                val balloon = selectedStrokes.firstOrNull { it.text?.box?.balloon != null }
                if (balloon != null) {
                    val shown = (activePreview as? Preview.ShapeEdit)?.stroke?.takeIf { it.id == balloon.id } ?: balloon
                    com.karapp.drawguidance.ui.drawing.BalloonTails.positions(shown).forEach { p ->
                        val c = viewport.toScreen(p)
                        drawCircle(colors.background, 9.dp.toPx(), c)
                        drawCircle(Color(0xFFFFB300), 7.dp.toPx(), c)
                        drawCircle(colors.content, 7.dp.toPx(), c, style = DrawStroke(width = 1.5.dp.toPx()))
                    }
                }
            }
            if (mode.transformsSelection) {
                selectionBounds?.let { drawTransformFrame(it, mode, transformPreview, viewport, colors, textMeasurer, squareHandles = !singleTextSelected) }
            }
            if (mode == EditMode.EXPAND) {
                drawExpandFrame(paper, paperCanvas, colors, textMeasurer)
            }
            // Tuval üstü diyagram düzenleme: dokunulabilir adımlar ince, odaktaki adım/ok kalın vurgulanır;
            // odaktaki adımın (ya da ok kaynağının) dört tarafında ok tutamakları, ok eklerken hedef taraflar
            if (mode == EditMode.DIAGRAM_EDIT) {
                selectedStrokes.firstOrNull { it.diagram != null }?.let { d ->
                    drawDiagramEditing(d, diagramFocus, diagramLink, viewport, colors)
                }
            }
            if (mode == EditMode.CROP && cropRect != null) {
                val box = viewport.toScreen(cropRect)
                // Kırpılınca gizlenecek kısım koyulaştırılır (eleman kırpmasında yalnızca elemanın alanı)
                val area = cropArea?.let { viewport.toScreen(it) } ?: paper
                val shade = Color.Black.copy(alpha = if (cropArea != null) 0.35f else 0.45f)
                drawRect(shade, topLeft = area.topLeft, size = Size(area.width, (box.top - area.top).coerceAtLeast(0f)))
                drawRect(shade, topLeft = Offset(area.left, box.bottom), size = Size(area.width, (area.bottom - box.bottom).coerceAtLeast(0f)))
                drawRect(shade, topLeft = Offset(area.left, box.top), size = Size((box.left - area.left).coerceAtLeast(0f), box.height))
                drawRect(shade, topLeft = Offset(box.right, box.top), size = Size((area.right - box.right).coerceAtLeast(0f), box.height))
                if (cropArea != null) {
                    drawRect(
                        colors.accent.copy(alpha = 0.6f), topLeft = area.topLeft, size = area.size,
                        style = DrawStroke(width = 1.5f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)),
                    )
                }
                drawExpandFrame(box, cropRect, colors, textMeasurer)
            }
        }

        // Çizim sırasında parmağın yanında seçili fırçanın ikonu (yalnızca bu küçük parça yeniden oluşturulur)
        FingerBubble(finger, brushSettings.brushType, brushSettings.colorHex, colors)
        HoldMoveRing(holdMove)
    }
}

@Composable
private fun FingerBubble(finger: androidx.compose.runtime.State<Offset?>, brushType: BrushType, colorHex: String, colors: NeuColors) {
    if (finger.value == null) return
    val density = LocalDensity.current
    val bubble = with(density) { 34.dp.toPx() }
    Box(
        modifier = Modifier
            .offset {
                val position = finger.value ?: Offset.Zero
                IntOffset((position.x + bubble * 0.45f).roundToInt(), (position.y - bubble * 1.5f).roundToInt())
            }
            .size(34.dp)
            .neumorphic(colors, CircleShape, elevation = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = BrushIcons.of(brushType),
            contentDescription = null,
            tint = if (brushType == BrushType.ERASER) colors.content else hexToColor(colorHex),
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Elemanı seçmek için basılı tutma süresi. */
const val LONG_PRESS_MS = 2000L

/** v18: kalem en son bu kadar ms önce havadaysa parmak dokunuşu avuç sayılır. */
const val PEN_PROXIMITY_MS = 350L

/** v17: iki parmakla bu kadar kıpırdamadan basılı tutunca bağlam menüsü açılır. */
const val TWO_FINGER_MENU_MS = 550L

/** Çizim önbelleğinin hangi görünüm ve çizim kümesi için üretildiği. */
private data class CacheKey(
    val width: Int,
    val height: Int,
    val scale: Float,
    val offset: Offset,
    val canvasWidth: Int,
    val canvasHeight: Int,
    val excluded: Set<String>,
    val imagesVersion: Int,
) {
    fun clip() = Rect(offset, Size(canvasWidth * scale, canvasHeight * scale))
}

private class RenderRequest(val key: CacheKey, val target: List<Stroke>, val version: Long)

private class StrokeCache {
    var bitmap: androidx.compose.ui.graphics.ImageBitmap? = null
    var key: CacheKey? = null
    var drawn: List<Stroke> = emptyList()
    var version: Long = Long.MIN_VALUE
    val requests = MutableStateFlow<RenderRequest?>(null)
}

/** [next], [previous]'ın sonuna yeni çizimler eklenmiş hali mi (kimlik karşılaştırması)? */
private fun isAppend(previous: List<Stroke>, next: List<Stroke>): Boolean {
    if (next.size < previous.size) return false
    for (i in previous.indices) if (previous[i] !== next[i]) return false
    return true
}

/**
 * v16 şekil düzenleme (tuval koordinatlarında, ekranda sabit boyut): vektör noktaları, seçili noktalar, Çizgi çiz'de
 * çizginin sürdüğü nokta; Çizgiler aracında çizgiler ince vurgulu, seçili çizgi kalın, ortasında kavis tutamağı ve
 * ucunun ötesinde eğim (döndürme) tutamağı.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawVectorEditing(
    g: com.karapp.drawguidance.ui.drawing.VectorEdit.Graph,
    tool: com.karapp.drawguidance.ui.screens.canvasedit.ShapeTool,
    sel: com.karapp.drawguidance.ui.screens.canvasedit.VectorSelection,
    scale: Float,
    colors: NeuColors,
    tolerancePx: Float,
    handlePx: Float,
    dragging: Boolean,
) {
    val px = tolerancePx / 14f / scale
    val lines = tool == com.karapp.drawguidance.ui.screens.canvasedit.ShapeTool.LINES
    val round = androidx.compose.ui.graphics.StrokeCap.Round
    if (lines) {
        // Her çizgi ayrı görünsün: ince vurgu
        g.edges.forEach { e ->
            for (k in 1 until e.points.size) drawLine(colors.accent.copy(alpha = 0.35f), e.points[k - 1], e.points[k], 2.5f * px, cap = round)
        }
    }
    val edge = sel.edge?.let { (a, b) -> g.edgeBetween(a, b) }
    if (edge != null) {
        for (k in 1 until edge.points.size) drawLine(colors.accent.copy(alpha = 0.25f), edge.points[k - 1], edge.points[k], 12f * px, cap = round)
        for (k in 1 until edge.points.size) drawLine(colors.accent, edge.points[k - 1], edge.points[k], 4f * px, cap = round)
    }
    val selected = if (dragging && !lines) emptySet() else sel.nodes.mapNotNullTo(HashSet()) { g.nodeNear(it)?.id }
    val nodeR = if (lines) 3.5f * px else 5.5f * px
    g.nodes.forEach { n ->
        if (n.id in selected) return@forEach
        val free = g.isFree(n.id)
        drawCircle(colors.background, nodeR, n.point)
        drawCircle(
            if (free) colors.contentMuted else colors.accent, nodeR, n.point,
            style = DrawStroke(width = 2f * px, pathEffect = if (free) androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(3f * px, 2f * px), 0f) else null),
        )
    }
    selected.forEach { id ->
        val p = g.nodes[id].point
        drawCircle(colors.accent.copy(alpha = 0.25f), 11f * px, p)
        drawCircle(colors.accent, 7f * px, p)
        drawCircle(colors.background, 2.5f * px, p)
    }
    sel.linkFrom?.let { from ->
        drawCircle(colors.accent, 13f * px, from, style = DrawStroke(width = 2.5f * px))
    }
    if (edge != null && lines) {
        // Kavis tutamağı: çizginin ortası
        val mid = com.karapp.drawguidance.ui.drawing.VectorEdit.bendHandle(edge)
        drawCircle(colors.background, 9f * px, mid)
        drawCircle(colors.accent, 9f * px, mid, style = DrawStroke(width = 2.5f * px))
        drawCircle(colors.accent, 3.5f * px, mid)
        if (!dragging) {
            // Eğim tutamağı: bitiş ucunun ötesinde (ShapeController.turnHandle ile aynı yer)
            val handle = com.karapp.drawguidance.ui.drawing.VectorEdit.turnHandle(edge, handlePx / scale)
            drawLine(colors.accent, edge.end, handle, 1.5f * px)
            drawCircle(colors.background, 11f * px, handle)
            drawCircle(colors.accent, 11f * px, handle, style = DrawStroke(width = 2.5f * px))
            drawArc(
                colors.accent, startAngle = -60f, sweepAngle = 250f, useCenter = false,
                topLeft = Offset(handle.x - 5.5f * px, handle.y - 5.5f * px), size = Size(11f * px, 11f * px),
                style = DrawStroke(width = 2f * px),
            )
        }
    }
}

/** Kalem aracının yolu: eklenen noktalar, eğri tutamakları ve sürüklenen nokta; ilk nokta (kapatma yeri) büyük. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPenPath(overlay: CanvasOverlay, scale: Float, colors: NeuColors, tolerancePx: Float) {
    val anchors = overlay.penAnchors + listOfNotNull(overlay.penPending)
    if (anchors.isEmpty()) return
    val px = tolerancePx / 14f / scale
    val pts = com.karapp.drawguidance.ui.screens.canvasedit.state.PenPath.sample(anchors, false)
    for (i in 1 until pts.size) drawLine(colors.accent, pts[i - 1], pts[i], 2f * px, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    anchors.forEachIndexed { i, a ->
        a.handle?.let { h ->
            val back = a.point * 2f - h
            drawLine(colors.accent.copy(alpha = 0.6f), back, h, 1.2f * px)
            drawCircle(colors.accent, 3f * px, h)
            drawCircle(colors.accent, 3f * px, back)
        }
        val size = if (i == 0 && anchors.size > 2) 7f * px else 5f * px
        drawRect(colors.background, topLeft = a.point - Offset(size, size), size = Size(size * 2, size * 2))
        drawRect(colors.accent, topLeft = a.point - Offset(size, size), size = Size(size * 2, size * 2), style = DrawStroke(width = 1.8f * px))
    }
}

private fun applyPreview(strokes: List<Stroke>, preview: Preview?): List<Stroke> {
    val ids = strokes.map { it.id }.toSet()
    if (preview is Preview.ShapeEdit) return strokes.map { if (it.id == preview.stroke.id) preview.stroke else it }
    return when (preview) {
        is Preview.Move -> ElementOps.translate(strokes, ids, preview.delta.x, preview.delta.y)
        is Preview.Rotate -> ElementOps.rotate(strokes, ids, preview.degrees, preview.pivot)
        is Preview.Scale -> ElementOps.scale(strokes, ids, preview.sx, preview.sy, preview.pivot)
        else -> strokes
    }
}

/** Dikdörtgenin önizleme dönüşümünden sonraki hali (döndürme hariç; o çizimde uygulanır). */
private fun transformRect(rect: Rect, preview: Preview?): Rect = when (preview) {
    is Preview.Move -> rect.translate(preview.delta)
    is Preview.Scale -> {
        val a = Offset(preview.pivot.x + (rect.left - preview.pivot.x) * preview.sx, preview.pivot.y + (rect.top - preview.pivot.y) * preview.sy)
        val b = Offset(preview.pivot.x + (rect.right - preview.pivot.x) * preview.sx, preview.pivot.y + (rect.bottom - preview.pivot.y) * preview.sy)
        Rect(min(a.x, b.x), min(a.y, b.y), max(a.x, b.x), max(a.y, b.y))
    }
    else -> rect
}

private inline fun DrawScope.withPreviewRotation(preview: Preview?, viewport: ViewportState, block: DrawScope.() -> Unit) {
    if (preview is Preview.Rotate) rotate(preview.degrees, viewport.toScreen(preview.pivot)) { block() } else block()
}

private class PaperPaints(colors: NeuColors, background: Color, density: Float) {
    val radius = 6f * density
    val shadows = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = background.toArgb() }
    val light = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = background.toArgb()
        setShadowLayer(20f * density, -10f * density, -10f * density, colors.lightShadow.toArgb())
    }
    val dark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = background.toArgb()
        setShadowLayer(20f * density, 10f * density, 10f * density, colors.darkShadow.toArgb())
    }
    val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
        color = colors.darkShadow.copy(alpha = 0.5f).toArgb()
    }
}

private fun DrawScope.drawPaper(paper: Rect, paints: PaperPaints) {
    drawIntoCanvas { canvas ->
        val native = canvas.nativeCanvas
        if (paints.shadows) {
            native.drawRoundRect(paper.left, paper.top, paper.right, paper.bottom, paints.radius, paints.radius, paints.light)
            native.drawRoundRect(paper.left, paper.top, paper.right, paper.bottom, paints.radius, paints.radius, paints.dark)
        }
        native.drawRoundRect(paper.left, paper.top, paper.right, paper.bottom, paints.radius, paints.radius, paints.fill)
        if (!paints.shadows) native.drawRoundRect(paper.left, paper.top, paper.right, paper.bottom, paints.radius, paints.radius, paints.border)
    }
}

private fun DrawScope.drawChip(
    textMeasurer: TextMeasurer,
    text: String,
    anchor: Offset,
    background: Color,
    content: Color,
    above: Boolean = true,
    centered: Boolean = false,
) {
    val layout = textMeasurer.measure(text, TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = content))
    val padX = 6.dp.toPx()
    val padY = 3.dp.toPx()
    val w = layout.size.width + padX * 2
    val h = layout.size.height + padY * 2
    val left = if (centered) anchor.x - w / 2f else anchor.x
    val top = if (above) anchor.y - h - 3.dp.toPx() else anchor.y + 3.dp.toPx()
    drawRoundRect(background, topLeft = Offset(left, top), size = Size(w, h), cornerRadius = CornerRadius(h / 2f))
    drawText(layout, topLeft = Offset(left + padX, top + padY))
}

private fun DrawScope.drawElementBox(
    bounds: Rect,
    name: String,
    preview: Preview?,
    viewport: ViewportState,
    colors: NeuColors,
    textMeasurer: TextMeasurer,
) {
    val box = viewport.toScreen(transformRect(bounds, preview)).inflate(3.dp.toPx())
    withPreviewRotation(preview, viewport) {
        drawRect(
            color = colors.accent,
            topLeft = box.topLeft,
            size = box.size,
            style = DrawStroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))),
        )
        drawChip(textMeasurer, name, box.topLeft, colors.accent, colors.onAccent)
    }
}

private fun DrawScope.drawTransformFrame(
    bounds: Rect,
    mode: EditMode,
    preview: Preview?,
    viewport: ViewportState,
    colors: NeuColors,
    textMeasurer: TextMeasurer,
    squareHandles: Boolean = true,
) {
    val box = viewport.toScreen(transformRect(bounds, preview)).inflate(8.dp.toPx())
    val handle = 10.dp.toPx()
    withPreviewRotation(preview, viewport) {
        drawRect(colors.accent.copy(alpha = 0.9f), topLeft = box.topLeft, size = box.size, style = DrawStroke(width = 1.5.dp.toPx()))
        when (mode) {
            EditMode.SCALE -> if (squareHandles) listOf(
                box.topLeft, box.topCenter, box.topRight, box.centerLeft,
                box.centerRight, box.bottomLeft, box.bottomCenter, box.bottomRight,
            ).forEach { c ->
                drawRect(colors.background, topLeft = c - Offset(handle / 2, handle / 2), size = Size(handle, handle))
                drawRect(colors.accent, topLeft = c - Offset(handle / 2, handle / 2), size = Size(handle, handle), style = DrawStroke(width = 2.dp.toPx()))
            }
            EditMode.ROTATE -> {
                val knob = box.topCenter - Offset(0f, 30.dp.toPx())
                drawLine(colors.accent, box.topCenter, knob, strokeWidth = 1.5.dp.toPx())
                drawCircle(colors.background, radius = 8.dp.toPx(), center = knob)
                drawCircle(colors.accent, radius = 8.dp.toPx(), center = knob, style = DrawStroke(width = 2.dp.toPx()))
                drawCircle(colors.accent, radius = 3.dp.toPx(), center = box.center)
            }
            else -> Unit
        }
    }
    val label = when (preview) {
        is Preview.Rotate -> "${preview.degrees.roundToInt()}°"
        is Preview.Scale -> if (preview.sx == preview.sy) "%${(preview.sx * 100).roundToInt()}"
        else "%${(preview.sx * 100).roundToInt()} × %${(preview.sy * 100).roundToInt()}"
        else -> null
    }
    if (label != null) drawChip(textMeasurer, label, box.bottomCenter + Offset(0f, 10.dp.toPx()), colors.content, colors.background, above = false, centered = true)
}

private fun DrawScope.drawExpandFrame(paper: Rect, paperCanvas: Rect, colors: NeuColors, textMeasurer: TextMeasurer) {
    drawRect(
        color = colors.accent,
        topLeft = paper.topLeft,
        size = paper.size,
        style = DrawStroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 10f))),
    )
    val long = 34.dp.toPx()
    val thick = 8.dp.toPx()
    listOf(paper.centerLeft, paper.centerRight).forEach { c ->
        drawRoundRect(colors.accent, topLeft = c - Offset(thick / 2, long / 2), size = Size(thick, long), cornerRadius = CornerRadius(thick / 2))
    }
    listOf(paper.topCenter, paper.bottomCenter).forEach { c ->
        drawRoundRect(colors.accent, topLeft = c - Offset(long / 2, thick / 2), size = Size(long, thick), cornerRadius = CornerRadius(thick / 2))
    }
    listOf(paper.topLeft, paper.topRight, paper.bottomLeft, paper.bottomRight).forEach { c ->
        drawCircle(colors.background, radius = 8.dp.toPx(), center = c)
        drawCircle(colors.accent, radius = 8.dp.toPx(), center = c, style = DrawStroke(width = 2.5.dp.toPx()))
    }
    val sizeText = "${paperCanvas.width.roundToInt()} × ${paperCanvas.height.roundToInt()}"
    drawChip(textMeasurer, sizeText, paper.topCenter - Offset(0f, 14.dp.toPx()), colors.content, colors.background, centered = true)
}

/** Diyagram düzenleme katmanı: adım çerçeveleri, odaktaki ok, ok tutamakları ve ok eklerken hedef taraflar. */
private fun DrawScope.drawDiagramEditing(
    d: Stroke,
    focus: com.karapp.drawguidance.ui.screens.canvasedit.DiagramFocus?,
    link: com.karapp.drawguidance.ui.screens.canvasedit.state.DiagramLink?,
    viewport: ViewportState,
    colors: NeuColors,
) {
    val spec = d.diagram ?: return
    val renderer = com.karapp.drawguidance.ui.drawing.DiagramRenderer
    val dash = PathEffect.dashPathEffect(floatArrayOf(7f, 6f), 0f)
    val focusedNode = (focus as? com.karapp.drawguidance.ui.screens.canvasedit.DiagramFocus.Node)?.id
    val sourceId = link?.fromId
    spec.nodes.forEach { node ->
        val corners = renderer.nodeCorners(d, node).map { viewport.toScreen(it) }
        if (corners.size < 4) return@forEach
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(corners[0].x, corners[0].y); corners.drop(1).forEach { lineTo(it.x, it.y) }; close()
        }
        val strong = node.id == focusedNode || node.id == sourceId
        drawPath(
            path, colors.accent.copy(alpha = if (strong) 1f else 0.45f),
            style = DrawStroke(width = if (strong) 4f else 1.5f, pathEffect = if (strong) null else dash),
        )
    }
    (focus as? com.karapp.drawguidance.ui.screens.canvasedit.DiagramFocus.Edge)?.let { f ->
        val edge = spec.edges.getOrNull(f.index) ?: return@let
        val a = spec.nodes.firstOrNull { it.id == edge.from } ?: return@let
        val b = spec.nodes.firstOrNull { it.id == edge.to } ?: return@let
        val route = renderer.edgePath(d, edge, a, b).map { viewport.toScreen(it) }
        if (route.size < 2) return@let
        val line = androidx.compose.ui.graphics.Path().apply {
            moveTo(route[0].x, route[0].y); route.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(line, colors.accent.copy(alpha = 0.5f), style = DrawStroke(width = 10f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
    val knob = 9.dp.toPx()
    // Ok tutamakları: odaktaki adımın (ok eklerken kaynağın) dört tarafında; seçili çıkış tarafı dolu
    val handleNode = spec.nodes.firstOrNull { it.id == (sourceId ?: focusedNode) }
    if (handleNode != null) {
        renderer.Sides.forEach { side ->
            val p = renderer.portPoint(d, handleNode, side)?.let { viewport.toScreen(it) } ?: return@forEach
            val chosen = link != null && link.fromSide == side
            drawCircle(if (chosen) colors.accent else colors.background, knob, p)
            drawCircle(colors.accent, knob, p, style = DrawStroke(width = 2.5f.dp.toPx()))
            if (!chosen) {
                // "+" işareti: buradan ok çekilir
                val arm = knob * 0.5f
                drawLine(colors.accent, p - Offset(arm, 0f), p + Offset(arm, 0f), 2.dp.toPx())
                drawLine(colors.accent, p - Offset(0f, arm), p + Offset(0f, arm), 2.dp.toPx())
            }
        }
    }
    // Ok eklerken hedef olabilecek adımların tarafları küçük noktalarla gösterilir
    if (sourceId != null) {
        spec.nodes.forEach { node ->
            if (node.id == sourceId) return@forEach
            renderer.Sides.forEach { side ->
                val p = renderer.portPoint(d, node, side)?.let { viewport.toScreen(it) } ?: return@forEach
                drawCircle(colors.background, knob * 0.55f, p)
                drawCircle(colors.accent.copy(alpha = 0.8f), knob * 0.55f, p, style = DrawStroke(width = 2.dp.toPx()))
            }
        }
    }
}
