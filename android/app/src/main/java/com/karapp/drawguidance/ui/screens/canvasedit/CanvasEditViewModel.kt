package com.karapp.drawguidance.ui.screens.canvasedit

import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karapp.drawguidance.data.images.CanvasAssetStore
import com.karapp.drawguidance.data.images.ExportOptions
import com.karapp.drawguidance.data.images.ImageImporter
import com.karapp.drawguidance.data.images.ImageIssue
import com.karapp.drawguidance.data.images.ImagesRepository
import com.karapp.drawguidance.data.images.PreparedImage
import com.karapp.drawguidance.data.model.BrushType
import kotlinx.coroutines.flow.first
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementOps
import com.karapp.drawguidance.data.model.CanvasUi
import com.karapp.drawguidance.data.model.DiagramBox
import com.karapp.drawguidance.data.model.DiagramKind
import com.karapp.drawguidance.data.model.DiagramSide
import com.karapp.drawguidance.data.model.DiagramSpec
import com.karapp.drawguidance.data.model.GraphSpec
import com.karapp.drawguidance.data.model.GuideKind
import com.karapp.drawguidance.data.model.GuideSpec
import com.karapp.drawguidance.data.model.ShapeStyle
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.data.model.TextAlignKind
import com.karapp.drawguidance.data.model.TextSpec
import com.karapp.drawguidance.data.repository.CanvasRepository
import com.karapp.drawguidance.data.repository.CanvasSaveQueue
import com.karapp.drawguidance.data.settings.ColorPaletteStore
import com.karapp.drawguidance.ui.drawing.ShapeKind
import com.karapp.drawguidance.ui.drawing.defaultSize
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementIndex
import com.karapp.drawguidance.ui.screens.canvasedit.state.BrushController
import com.karapp.drawguidance.ui.screens.canvasedit.state.ObjectSceneController
import com.karapp.drawguidance.ui.screens.canvasedit.state.DiagramController
import com.karapp.drawguidance.ui.screens.canvasedit.state.DiagramLink
import com.karapp.drawguidance.ui.screens.canvasedit.state.DrawingController
import com.karapp.drawguidance.ui.screens.canvasedit.state.EditorHost
import com.karapp.drawguidance.ui.screens.canvasedit.state.EditorStore
import com.karapp.drawguidance.ui.screens.canvasedit.state.FrameController
import com.karapp.drawguidance.ui.screens.canvasedit.state.GuideController
import com.karapp.drawguidance.ui.screens.canvasedit.state.ImageController
import com.karapp.drawguidance.ui.screens.canvasedit.state.PageController
import com.karapp.drawguidance.ui.screens.canvasedit.state.SelectionController
import com.karapp.drawguidance.ui.screens.canvasedit.state.ShapeController
import com.karapp.drawguidance.ui.screens.canvasedit.state.StyleController
import com.karapp.drawguidance.ui.screens.canvasedit.state.TextController
import com.karapp.drawguidance.ui.screens.canvasedit.state.ComicController
import com.karapp.drawguidance.ui.screens.canvasedit.state.FormulaController
import com.karapp.drawguidance.ui.screens.canvasedit.state.HandwritingController
import com.karapp.drawguidance.ui.screens.canvasedit.state.ImageEditController
import com.karapp.drawguidance.ui.screens.canvasedit.state.LayerController
import com.karapp.drawguidance.ui.screens.canvasedit.state.PdfExportState
import com.karapp.drawguidance.ui.screens.canvasedit.state.PenAnchor
import com.karapp.drawguidance.ui.screens.canvasedit.state.PenController
import com.karapp.drawguidance.content.formula.FormulaTemplate
import com.karapp.drawguidance.data.model.Layer
import com.karapp.drawguidance.data.settings.EditorPrefsStore
import com.karapp.drawguidance.data.repository.shapeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Çizim alanının yan panelinde ayarlanan fırça durumu. */
data class BrushSettings(
    val brushType: BrushType = BrushType.PENCIL,
    val sizePx: Float = BrushType.PENCIL.defaultSize(),
    val colorHex: String = "#1E1E1E",
    // Fırçanın opaklığı (0f..1f): tek çizimin ulaşabileceği en yüksek koyuluk.
    val opacity: Float = 1f,
    /** Akışkanlık: damga başına bırakılan boya (üst üste geçtikçe birikir). */
    val flow: Float = 1f,
    /** Çizim gücü: bastırma kuvveti (kalınlık, koyuluk, doku doluluğu). */
    val strength: Float = 1f,
    /** v20 fırça başına ayarlar: aralık, çizim hızı, sabitleme, eğim, 3B. */
    val tuning: com.karapp.drawguidance.data.settings.BrushTuning = com.karapp.drawguidance.data.settings.BrushTuning(),
) {
    /** Bu ayarlarla çizilen izin görünüm ayarları (hepsi varsayılansa null). */
    val params: com.karapp.drawguidance.data.model.BrushParams? get() = tuning.params(brushType)
}

enum class PanelTab { BRUSH, ELEMENTS, LAYERS }

/**
 * Çizim alanında dokunuşların ne yapacağı. [Toolbar] modları çizim alanının hemen üstündeki mod menüsündeki
 * düğmelerdir (v17: Çiz, Seç, Ekle, Düzenle, Taşı, Genişlet); Döndür/Ölçekle, Kırp ve eklenecek öğeler bir üstteki
 * araç menüsündedir — araç menüsü modun ailesine ([rowKind]) göre değişir. Yakınlaştırma her modda iki parmakla
 * yapılır.
 */
/** v52 resim düzenlemenin yapılacağı yer (not alma ve çizgi roman). */
enum class ImageEditChoice { ADVANCED, PHOTO, HERE }

enum class EditMode { DRAW, SELECT, MOVE, ROTATE, SCALE, EXPAND, CROP, SHAPE, TEXT, DIAGRAM, SHAPE_EDIT, GUIDE, PERSPECTIVE, DIAGRAM_EDIT,
    /** v15: formül yerleştirme (dokun ya da sürükle). */
    FORMULA,
    /** v15: seçili resmi düzenleme (kırp, vektörleştir, kalem aracı). */
    IMAGE_EDIT,
    /** v15: kalem aracı (dokunarak köşe, sürükleyerek eğri noktası; ilk noktaya dokununca kapanır). */
    PEN,
    /** v17 Ekle: üst menüde eklenecek öğeler; tuvale dokununca "buraya ekle" menüsü açılır. Sürükleme kaydırır. */
    ADD,
    /** v17 Düzenle: dokunulan elemanın düzenleyicisi açılır (şekil, yazı, formül, resim, diyagram, çizim). */
    EDIT_PICK,
    /** v33 Şekil boyama (basit çizim): dokunulan şeklin dokunulan alanı seçili renge boyanır (seçmeden, kolay boyama). */
    PAINT;

    val transformsSelection: Boolean get() = this == MOVE || this == ROTATE || this == SCALE

    /** Modun oturum politikası (bkz. [ModeKind]). */
    val kind: ModeKind
        get() = when (this) {
            DRAW, SELECT, ADD, EDIT_PICK, PAINT -> ModeKind.BASE
            SHAPE, DIAGRAM, FORMULA -> ModeKind.INSERT
            SHAPE_EDIT, DIAGRAM_EDIT, IMAGE_EDIT -> ModeKind.EDIT
            else -> ModeKind.TOOL
        }

    /**
     * Kalem ve silgi yalnızca temel modlarda etkindir; ekleme (şekil, diyagram, yazı, kılavuz) ve düzenleme
     * modlarında pasiftir, "Bitti" ile düzenleme bitince yeniden etkinleşir.
     */
    val allowsDrawTools: Boolean get() = kind == ModeKind.BASE && this != PAINT

    /**
     * v17: mod menüsünde hangi düğmenin altında sayılır (ör. Şekil ekleme → Ekle, Şekil düzenleme → Düzenle).
     * v18: Döndür, Ölçekle ve Kırp Düzenle'nin araçları.
     */
    val family: EditMode
        get() = when (this) {
            DRAW -> DRAW
            SELECT -> SELECT
            MOVE -> MOVE
            // v27 Kalem aracı Çiz modunun aracı (Ekle'den taşındı)
            PEN -> DRAW
            // v33 Şekil boyama Çiz ailesinde (basit menünün aracı)
            PAINT -> DRAW
            ADD, SHAPE, TEXT, DIAGRAM, FORMULA, GUIDE, PERSPECTIVE -> ADD
            EDIT_PICK, SHAPE_EDIT, DIAGRAM_EDIT, IMAGE_EDIT, ROTATE, SCALE, CROP -> EDIT_PICK
            EXPAND -> EXPAND
        }

    /** v17: üst araç menüsünün içeriği bu moda göre. */
    val rowKind: ToolRowKind
        get() = when (family) {
            SELECT -> ToolRowKind.SELECT
            MOVE -> ToolRowKind.MOVE
            ADD -> ToolRowKind.ADD
            EDIT_PICK -> ToolRowKind.EDIT
            EXPAND -> ToolRowKind.FRAME
            else -> ToolRowKind.DRAW
        }

    /**
     * v47 mod menüsünde seçili görünen düğme: Taşı ve Genişlet artık Düzenle'nin aracı ("Taşı ve genişlet" tek düğme),
     * mod menüsünde Düzenle seçili görünür; öteki modlar [family] ile aynı.
     */
    val menuFamily: EditMode
        get() = when (this) {
            MOVE, EXPAND -> EDIT_PICK
            else -> family
        }

    companion object {
        /**
         * Mod menüsündeki modlar. v47: dört mod bu sırayla — Seç, Çiz, Ekle, Düzenle (Taşı ve Genişlet tek düğme olarak
         * Düzenle satırında; Döndür/Ölçekle ve Kırp üst menüde, eklenecekler Ekle modunda üst menüde).
         */
        val Toolbar = listOf(SELECT, DRAW, ADD, EDIT_PICK)
    }
}

/**
 * Üst araç menüsünün türü (v18): [DRAW] çizim araçları (fırça, silgi, boya kutusu, elemanlar, fırça ayarları),
 * [SELECT] seçili elemanlar (kopyala, sil, elemanlar), [MOVE] taşıma (konum, ok tuşlarıyla kaydırma, ortalama),
 * [ADD] eklenecek öğeler, [EDIT] düzenleme (döndür, ölçekle, kırp; şekilde nokta/çizgi/alan boya; resimde
 * kırp/vektörleştir/kalem), [FRAME] kağıdı genişletme (boyut, kenarlardan genişletme).
 */
enum class ToolRowKind { DRAW, SELECT, MOVE, ADD, EDIT, FRAME }

/** v17: seçimin türü — bağlam menüsü ve alt çubuk seçenekleri buna göre (fırça çizimi = [INK]). */
enum class SelectionKind { NONE, TEXT, SHAPE, INK, FORMULA, IMAGE, DIAGRAM, MIXED }

/** v22 3B sahnenin hazır açıları. */

/**
 * Mod oturumu politikası — tüm mod geçişleri bu tabloya göre yapılır (özel durum yazmak yerine):
 * - [BASE] Çiz, Seç, Ekle, Düzenle: kullanıcının çalıştığı temel mod.
 * - [INSERT] şekil/diyagram/formül yerleştirme: girerken eski seçim kalkar; eklenince yeni eleman seçili olarak
 *   Taşı moduna geçilir.
 * - [EDIT] şekil/diyagram/resim düzenleme: düzenlenirken yalnızca o eleman seçilidir.
 * - [TOOL] taşı, döndür, ölçekle, genişlet, kırp, yazı, kılavuz, kalem (düzenlemenin içinden açıldıysa bitince o
 *   düzenlemeye dönülür).
 * v17: iş bitince (seçim kalkar) Seç moduna geçilir — Seç, eski İncele ile birleşti: elemana dokunmak seçer, boş
 * yerde sürükleme kaydırır, iki parmak yakınlaştırır.
 */
enum class ModeKind { BASE, INSERT, EDIT, TOOL }

/** Tuval üstü diyagram düzenlemede odaktaki öğe: bir adım (düğüm) ya da bir ok. */
sealed interface DiagramFocus {
    data class Node(val id: String) : DiagramFocus
    data class Edge(val index: Int) : DiagramFocus
}

/**
 * Şekil düzenleme araçları (v16: vektör düzenleme): [NODES] vektör noktalarını görüp seçme ve sürükleme, [ADD_NODE]
 * çizgiye ya da boş yere nokta ekleme, [CONNECT] noktalar arasına çizgi çizme, [LINES] çizgiyi seçip eğimini ve kavisini
 * değiştirme, [GRAPH] (yalnızca grafiklerde) sayılara dokunarak düzenleme, desen ve nokta sayısı.
 */
enum class ShapeTool {
    NODES, ADD_NODE, CONNECT, LINES, FILL, GRAPH,
    /** v45 parametreli şekil (fizik, geometri, molekül): değerler, formül ve tutamaklar. */
    PARAMS;

    /** v33: Noktalar düğmesinin alt araçları (nokta panelinde seçilir). */
    val nodeFamily: Boolean get() = this == NODES || this == ADD_NODE || this == CONNECT
}

/**
 * Vektör düzenlemede seçim (konumlarıyla; işlemlerden sonra da geçerli kalır): seçili noktalar, seçili çizginin uçları
 * ve Çizgi çiz aracında çizginin devam ettiği nokta.
 */
data class VectorSelection(
    val nodes: List<Offset> = emptyList(),
    val edge: Pair<Offset, Offset>? = null,
    val linkFrom: Offset? = null,
)

/** Parça özellikleri penceresinde değiştirilebilen biçim (null: değiştirme). */
data class PartStyle(
    val strokeColorHex: String? = null,
    /** Dolgu: null değiştirme, "" dolgusuz, "#RRGGBB" renk. */
    val fillColorHex: String? = null,
    val widthScale: Float? = null,
    /** "solid" / "dashed" / "dotted" / "none". */
    val line: String? = null,
    val alpha: Float? = null,
)

/** Şekil düzenlemede sürüklemenin yaptığı iş: noktaları taşı, çizgi çek, çizgiyi taşı / bük / döndür. */
enum class ShapeDrag { NONE, NODES, CONNECT, EDGE, BEND, TURN,
    /** v45 parametreli şeklin tutamağı. */
    HANDLE,
    /** v56 aşama 9C seçili düğümün Bezier tutamağı. */
    BEZIER }

/** Yeni yazıların (ve seçili yazıların) yazı tipi ayarları; yazı boyutu tuval birimindedir. */
data class TextSettings(val spec: TextSpec = TextSpec(""), val sizePx: Float = 48f)

/**
 * Açık yazı penceresi: [editingId] null ise [anchor] noktasına yeni yazı eklenir. v37 [boxWidth] paragraf kutusunun
 * eni (satırlar kutuda alt satıra geçer); null ise kısa yazı ya da başlık (not tuvalinde sağ kenarda alt satıra geçer).
 */
data class TextDraft(val anchor: Offset, val editingId: String?, val content: String, val boxWidth: Float? = null)

/** Resim ekleme akışının durumu (null = boşta). */
sealed interface ImageImportState {
    data object Checking : ImageImportState
    data class Rejected(val errors: List<ImageIssue>, val warnings: List<ImageIssue>) : ImageImportState
    class NeedsConfirmation(val warnings: List<ImageIssue>, val image: PreparedImage, val center: Offset) : ImageImportState
}

sealed interface EditEvent {
    /** v52 klon/onarma: kaynak noktası seçildi (ilk dokunuş kaynak olur), şimdi kopyalanacak yer boyanır. */
    data object CloneSourceSet : EditEvent
    data class ImageAdded(val storedBytes: Int, val originalBytes: Int) : EditEvent
    data class ErasedRemoved(val count: Int) : EditEvent
    data class GapsFilled(val count: Int) : EditEvent
    data object CommandNeedsSelection : EditEvent
    data object NoGapsFound : EditEvent
    /** Etkin katman gizli ya da kilitli: çizim/ekleme yapılmadı. */
    data object LayerLocked : EditEvent
    /** Kaydedildi (otomatik kayıt kapalıyken Kaydet). */
    data object Saved : EditEvent
    /** v51 geçici tuvalde düzenlenen resim nota aktarıldı. */
    data object TempImageApplied : EditEvent
    /** v52 gelişmiş tuvalde düzenlenen şekil nota aktarıldı. */
    data object TempShapeApplied : EditEvent
    /** v52 foto düzenlemede düzenlenen resim yerine kondu / düzenlenemedi. */
    data object PhotoSaved : EditEvent
    data object PhotoFailed : EditEvent
    /** v17 Alan boya: dokunulan yer çizgilerle çevrili kapalı bir alan değil. */
    data object RegionOpen : EditEvent
    /** v17: seçili çizim için düzeltme bulunamadı. */
    data object NoCorrection : EditEvent
    /** v20 üç parmakla kaydırma: geri alındı / yinelendi / yapılacak adım yok. */
    data class Swiped(val redo: Boolean, val done: Boolean) : EditEvent
    /** v20 çizim önerileri kapatıldı (balondan). */
    data object SuggestionsOff : EditEvent
    /** v23 yazı / şekil önerileri ayrı ayrı kapatıldı. */
    data object TextSuggestionsOff : EditEvent
    data object ShapeSuggestionsOff : EditEvent
    /** v27 seçimde kendiliğinden öneri kapatıldı. */
    data object SelectionSuggestionsOff : EditEvent
    /** v21 3B çizim modu açıldı / kapandı. */
    /** v27 hazır metinli bildirim (ör. GLB içe aktarma sonucu). */
    data class Message(val text: String) : EditEvent
    /** v29 aktarma sonucu: hedef tuvalin adı (null: aktarılamadı; [empty]: aktarılacak çizim yok). */
    data class Transferred(val name: String?, val empty: Boolean = false) : EditEvent
    /** v30 bu tuvalde yapay zekâ açıldı / kapalı (öneri istenince de bildirilir). */
    data object AiOn : EditEvent
    data object AiOff : EditEvent
    /** v30 elemanlar gizlendi ([hidden]) ya da yeniden gösterildi ([shown]). */
    data class HiddenChanged(val hidden: Int, val shown: Int) : EditEvent
    /** v30 resmin arka planı kaldırıldı (kaldırılan alanın yüzdesi). */
    data class BackgroundRemoved(val percent: Int) : EditEvent
    /** v32 eğitim bitti ([title] araç grubunun adı). */
    data class TutorialFinished(@androidx.annotation.StringRes val title: Int) : EditEvent
    /** v33 sayfa sınırına ulaşıldı. */
    data class PageLimit(val max: Int) : EditEvent
    /** v33 Şekil boyamada dokunulan yerde boyanacak kapalı şekil alanı yok. */
    data object PaintMiss : EditEvent
}

sealed interface ImageSaveState {
    data object Idle : ImageSaveState
    data object Saving : ImageSaveState
    data class Saved(val images: List<com.karapp.drawguidance.data.images.SavedImageEntity>) : ImageSaveState {
        val image get() = images.first()
    }
    data object Failed : ImageSaveState
}

/**
 * Çizim ekranının ViewModel'i: ekranın konuştuğu tek yüz. Durum ve işler denetleyicilere bölünmüştür
 * ([EditorStore]: tuval, seçim, geri al/yinele; [FrameController]: kırpma/genişletme/arka plan;
 * [StyleController]: dolgu, yazı biçimi, hizalama; [ShapeController], [DiagramController], [TextController],
 * [SelectionController], [DrawingController], [PageController], [GuideController], [ImageController],
 * [BrushController]). ViewModel modları yönetir ve mod/sayfa/geri alma değişimlerini denetleyicilere bildirir.
 */
class CanvasEditViewModel(
    private val repository: CanvasRepository,
    paletteStore: ColorPaletteStore,
    private val assetStore: CanvasAssetStore,
    imageImporter: ImageImporter,
    private val saveQueue: CanvasSaveQueue,
    imagesRepository: ImagesRepository,
    private val prefs: EditorPrefsStore,
    /** v21 şekil veritabanı (öneride değiştirme şablonları). */
    private val shapeDb: com.karapp.drawguidance.data.repository.ShapeTemplateRepository?,
    private val canvasLocalId: String,
    /** v23 3B şekiller (v56 aşama 10: kütüphane ve GLB; düzenleyiciye yol yok). */
    model3dRepository: com.karapp.drawguidance.data.library.Model3DRepository? = null,
    /** v29 not alma / çizgi roman tuvaline aktarma. */
    private val transfer: com.karapp.drawguidance.data.transfer.CanvasTransfer? = null,
    /** v29 moda özel şekil tahmini modelleri (her modun kendi sinir ağı). */
    private val shapeModels: com.karapp.drawguidance.content.shapes.ShapeModeModels? = null,
    /** v30 Ana sayfadaki "Kaldığın yerden devam et" için açılma/bırakılma zamanı. */
    private val recent: com.karapp.drawguidance.data.settings.RecentCanvasStore? = null,
    /** v32 eğitim modu: ilk girişte öneri ve tamamlanan araç grupları. */
    private val tutorialPrefs: com.karapp.drawguidance.data.settings.TutorialPrefs? = null,
    /** v51 not almada resim düzenleme: geçici basit resim tuvali. */
    private val tempImages: com.karapp.drawguidance.data.notes.TempImageEditor? = null,
) : ViewModel(), EditorHost {

    /** v29 bu tuvalin kendi son ayarları (renk, fırça, yazı biçimi, şekil dolgusu, Ekle menüsü). */
    private val canvasPrefs = prefs.canvas(canvasLocalId)

    /** v56 hızlı fırça seçicinin son kullanılanları, favorileri ve köşesi (mod başına). */
    val quickBrush = com.karapp.drawguidance.ui.screens.canvasedit.state.QuickBrushStore(
        load = prefs::quickBrush, save = prefs::setQuickBrush, loadCorner = prefs::quickCorner, saveCorner = prefs::setQuickCorner,
    )



    // --- v51 geçici resim düzenleme tuvali (not almadan açılır; otomatik kayıt kapalı, Kaydet nota aktarır) ---

    private val tempSession = com.karapp.drawguidance.data.notes.TempCanvases.session(canvasLocalId)
    /** Bu tuval nottaki bir resmin geçici düzenleme tuvali mi. */
    val isTempImageEdit: Boolean get() = tempSession != null
    /** v52 geçici tuval nottaki bir şekil için (resim değil). */
    val isTempShapeEdit: Boolean get() = tempSession?.shape == true
    private val tempAutosaveOff: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()
    private var tempFinished = false
    private val _tempClosed = MutableStateFlow(false)
    /** Geçici tuval kaydedildi ya da bırakıldı: düzenleyici kapanır. */
    val tempClosed: StateFlow<Boolean> = _tempClosed.asStateFlow()
    private val _tempCanvasRequest = MutableStateFlow<String?>(null)
    /** Notta açılacak geçici resim tuvali ("": açılamadı). */
    val tempCanvasRequest: StateFlow<String?> = _tempCanvasRequest.asStateFlow()
    fun consumeTempCanvasRequest() { _tempCanvasRequest.value = null }
    private val appliedTempResults = HashSet<String>()

    // --- Mod, panel ve olaylar ---

    private val _mode = MutableStateFlow(EditMode.DRAW)
    override val mode: StateFlow<EditMode> = _mode.asStateFlow()
    override val scope: CoroutineScope get() = viewModelScope

    private val _events = MutableSharedFlow<EditEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<EditEvent> = _events
    override fun emit(event: EditEvent) {
        _events.tryEmit(event)
    }

    /** Açık yan panel sekmesi; null = panel kapalı. */
    private val _panel = MutableStateFlow<PanelTab?>(null)
    val panel: StateFlow<PanelTab?> = _panel.asStateFlow()

    // --- Denetleyiciler ---

    private val store = EditorStore(viewModelScope, saveQueue, canvasLocalId).also { st ->
        // v24 sayfaların kendi arka planları ortak arka planla birlikte yazılır
        st.onPageBackgrounds = { c -> viewModelScope.launch { repository.saveBackgroundSpec(canvasLocalId, c.background, c.pageBackgrounds) } }
    }
    private val frame = FrameController(store, this)
    /** v40 arka plan resmini taşıma ve boyutlandırma (geri alınabilir). */
    val backgroundTransform = com.karapp.drawguidance.ui.screens.canvasedit.state.BackgroundTransformController(store, frame) { backgroundPerPage() }
    /** v40 cetvel ve şekil kalıbı (belge dışı katman; çizgiler kenara yapışır). */
    val ruler = com.karapp.drawguidance.ui.screens.canvasedit.ruler.RulerController { store.elements.value }
    private val style = StyleController(store, frame) { backgroundPerPage() }
    private lateinit var brush: BrushController
    private val shapes = ShapeController(store, this, { brush }, style)
    private val diagrams = DiagramController(store, this, shapes)
    // v23 not tuvalinde eklenen yazı seçili olarak Taşı moduna geçer
    private val texts = TextController(store, { brush }, autoWrap = { store.canvas.value?.kind?.isNotes == true }) { id ->
        if (store.canvas.value?.kind?.isNotes == true) inserted(setOf(id))
    }
    private val selection = SelectionController(store, this)
    /** v56 2C-3 sayfalar arası kes, kopyala, yapıştır. */
    private val clipboard = com.karapp.drawguidance.ui.screens.canvasedit.state.ClipboardController(store, this, assetStore)
    // v27 çizgi roman (balon, panel, efekt yazısı, hız çizgileri)
    private val comic by lazy { ComicController(store, this, texts) { brush } }
    private val drawing = DrawingController(store, this) { brush }
    private val pages = PageController(store, ::beforePageChange, onLimit = { emit(EditEvent.PageLimit(it)) })
    private val guides = GuideController(store, this, repository)
    private val images = ImageController(store, this, assetStore, imageImporter, imagesRepository)

    // --- v56 aşama 6: Dönüştür paneli, yansıma, sıvılaştır ---
    private fun kindKey(): String = store.canvas.value?.kind?.name ?: "ART"
    val transform = com.karapp.drawguidance.ui.screens.canvasedit.state.TransformController(
        store, loadSnap = { prefs.transformSnap(kindKey()) }, saveSnap = { prefs.setTransformSnap(kindKey(), it) },
        fitTarget = { cx, cy ->
            // Çizgi romanda seçimin bulunduğu panelin kutusu
            if (store.canvas.value?.kind == com.karapp.drawguidance.data.model.CanvasKind.COMIC) {
                comicPanelRects().values.firstOrNull { it.contains(androidx.compose.ui.geometry.Offset(cx, cy)) }
                    ?.let { floatArrayOf(it.left, it.top, it.right, it.bottom) }
            } else null
        },
    )
    val liquify = com.karapp.drawguidance.ui.screens.canvasedit.state.LiquifyController(store)
    /** v52 foto düzenleme modu. */
    val photo = com.karapp.drawguidance.ui.screens.canvasedit.state.PhotoEditController(store, this, assetStore)
    /** v40 sayfa ayarları (numara, üst/alt bilgi) ve CBZ dışa aktarma. */
    val v40 = com.karapp.drawguidance.ui.screens.canvasedit.state.V40ExportController(store, this, imagesRepository) { id, settings -> repository.savePageSettings(id, settings) }
    /** v45 çizgi roman kapağı (Kapak ekle) ve not alma panelleri (Panel ekle). */
    val v45 = com.karapp.drawguidance.ui.screens.canvasedit.state.V45NotesComicController(store, this, pages)
    private val formulas = FormulaController(store, this) { brush }
    /** v52 not almada tablo ekleme ve düzenleme. */
    val tables = com.karapp.drawguidance.ui.screens.canvasedit.state.TableController(store, this)
    private val pen = PenController(store, this, shapes)
    /** v43 Arkaplan ile Nesneyi Uyumla ve Nesneyi Mekâna Yerleştir. */
    private val scene = ObjectSceneController(store, this)
    private val layerOps = LayerController(store)
    private val imageEdit = ImageEditController(store, this)
    private val models3d = com.karapp.drawguidance.ui.screens.canvasedit.state.Model3DController(store, this, assetStore, model3dRepository)
    // v17: çizim düzeltme önerisi ayarsız, Çiz modunda hep açık
    // v20: öneriler Ayarlar'dan, ⋮ menüsünden ya da öneri balonundan kapatılabilir
    // v23 yazı ve şekil önerisi ayrı algoritmalar, ayrı balonlar ve ayrı ayarlar
    /**
     * v29 moda özel tahmin: aramalar açık tuvalin modunda (resim / not alma / çizgi roman) yapılır; geri bildirimle
     * yalnız o modun sinir ağı cihazda eğitilir.
     */
    private val modeTemplates: com.karapp.drawguidance.data.repository.TemplateSource? = shapeDb?.let { db ->
        if (shapeModels == null) db
        else com.karapp.drawguidance.data.repository.ModeTemplateSource(db, shapeModels, { store.canvas.value?.kind?.shapeMode() }, kind = { store.canvas.value?.kind })
    }

    /** v32 eğitim sürüyor mu (eğitimin önerileri modelleri eğitmez, kullanıcının öneri ayarlarına bakılmaz). */
    private fun inTutorial(): Boolean = tutorialOrNull?.active == true

    /** v54 eğitim sürerken (kalıbı eğitim kendisi seçer; kalıp uyarısı açılmaz). */
    fun tutorialRunning(): Boolean = inTutorial()

    /** v32 eğitimdeki kabul/retler kullanıcının modunu eğitmez (sayaçlar ve sinir ağı değişmez). */
    private val learningTemplates: com.karapp.drawguidance.data.repository.TemplateSource? = modeTemplates?.let { base ->
        object : com.karapp.drawguidance.data.repository.TemplateSource {
            override val index get() = base.index
            override val searchScope get() = base.searchScope
            override val canvasKind get() = store.canvas.value?.kind
            override fun accepted(key: String, drawn: List<List<com.karapp.drawguidance.content.vector.P>>) { if (!inTutorial()) base.accepted(key, drawn) }
            override fun acceptedPart(key: String, drawn: List<List<com.karapp.drawguidance.content.vector.P>>) { if (!inTutorial()) base.acceptedPart(key, drawn) }
            override fun rejected(keys: List<String>, drawn: List<List<com.karapp.drawguidance.content.vector.P>>) { if (!inTutorial()) base.rejected(keys, drawn) }
        }
    }

    /** v30 tuvalin "Yapay zekâ kullanılsın" ayarı: kapalıysa bu tuvalde hiçbir öneri (çizim, seçim, 3B) yapılmaz. */
    private fun aiOn(): Boolean = store.canvas.value?.aiEnabled == true

    /** v44 çocuk görevi açık mı (yalnız çocuk tuvalinde; görev denetleyicisi gereksiz yere kurulmaz). */
    private fun inChallenge(): Boolean =
        (store.canvas.value?.kind == com.karapp.drawguidance.data.model.CanvasKind.KIDS && challenge.active.value) ||
            // v50 yetişkin alıştırması sürerken de öneri verilmez (öneri çizimi hazır şekille değiştirirdi)
            (practiceCreated && practice.active) ||
            // v54 şekil kalıbıyla çizerken (kalıp seçilirken de) şekil, yazı ve çizgi önerisi verilmez
            onTemplateSession()

    /** v54 şekil kalıbı oturumu açık mı (kalıp seçimi ya da kalıpla çizim). */
    private fun onTemplateSession(): Boolean = ruler.session.value.let {
        it == com.karapp.drawguidance.ui.screens.canvasedit.ruler.GuideSession.TEMPLATE ||
            it == com.karapp.drawguidance.ui.screens.canvasedit.ruler.GuideSession.TEMPLATE_PICK
    }

    /** v50 alıştırma denetleyicisi kuruldu mu (öneri denetimi denetleyiciyi gereksiz yere kurmasın). */
    @Volatile private var practiceCreated = false

    private val handwriting = HandwritingController(
        store, this,
        // v32 eğitimde öneriler yalnız yapay zekâ adımlarında (tuvalin ve kullanıcının ayarlarından bağımsız)
        // v44 çocuk görevi (challenge) sürerken çizim önerisi verilmez
        { _mode.value == EditMode.DRAW && !inChallenge() && (if (inTutorial()) tutorialOrNull?.aiStep == true else prefs.handwriting.value && aiOn()) },
        learningTemplates,
        textEnabled = { inTutorial() || prefs.textSuggest.value }, shapeEnabled = { inTutorial() || prefs.shapeSuggest.value },
        // v38 çizgi önerisi (2+ çizgi; yazı ve şekil önerisinden ayrı)
        lineEnabled = { inTutorial() || prefs.lineSuggest.value },
        // v27 seçimde kendiliğinden öneri: tüm temel ve dönüşüm modlarında (Çiz, Seç, Taşı, Döndür, Ölçekle, Ekle,
        // Düzenle seçimi); bir elemanın içi düzenlenirken (yazı, şekil noktaları, kırpma, resim, kalem) araya girmez
        selectionEnabled = {
            !inTutorial() && !inChallenge() && aiOn() && prefs.handwriting.value && prefs.selectionSuggest.value &&
                (_mode.value.kind == ModeKind.BASE || _mode.value.transformsSelection)
        },
    )

    init {
        // v53 bellek düzeni: açık tuval sayısı (kapanınca çizim önbellekleri boşalır)
        com.karapp.drawguidance.platform.DeviceCaps.editorOpened()
        // v21: şekil veritabanı arka planda hazırlanır (ilk açılışta şablonlar yazılır)
        shapeDb?.warmUp()
        brush = BrushController(store, this, paletteStore, shapes::setDrawingWidth, canvasPrefs)
        if (tempSession != null) {
            // v51 geçici resim tuvalinde otomatik kayıt hep kapalı (Kaydet düzenlenen resmi nota aktarır)
            store.autosave = false
            com.karapp.drawguidance.data.notes.TempCanvases.setActive(canvasLocalId, true)
        } else {
            store.autosave = prefs.autosave.value
            viewModelScope.launch {
                prefs.autosave.collect { store.autosave = it }
            }
        }
        // Düzenlenen formül seçimden çıkınca formül üstündeki yazı alanı kapanır
        viewModelScope.launch {
            store.selectedIds.collect { formulas.onSelectionChanged(it); handwriting.onSelectionChanged(it) }
        }
        canvasPrefs.shapeFill?.let { shapes.setNewShapeFill(it.ifEmpty { null }) }
        viewModelScope.launch {
            // Hemen geri dönülüp tuval yeniden açıldıysa bekleyen kayıt önce yazılır (eski içerik yüklenmesin)
            withContext(Dispatchers.Default) { saveQueue.awaitIdle() }
            val loaded = repository.getCanvas(canvasLocalId)
            if (loaded != null) {
                if (tempSession == null) runCatching { recent?.opened(canvasLocalId) }
                assetStore.ensureLoaded(loaded)
                // Önceki oturumlarda silinmiş resimlerin verisi yer kaplamasın (arka plan resmi korunur; v51 geçici
                // tuvalden gelip henüz nota aktarılmamış resimler de korunur)
                assetStore.prune(
                    canvasLocalId, loaded.pages.flatten(),
                    listOfNotNull(loaded.background.assetId) + loaded.pageBackgrounds.mapNotNull { it?.spec?.assetId } +
                        com.karapp.drawguidance.data.notes.TempCanvases.pendingAssetIds(canvasLocalId) +
                        // v56 fırça ayarlarındaki resim uçları (henüz çizilmemiş olsa da)
                        brush.stampAssetIds(),
                )
                brush.adaptTo(loaded.kind)
                // v29 bu tuvalin modunun tahmin modeli arka planda hazırlanır
                (modeTemplates as? com.karapp.drawguidance.data.repository.ModeTemplateSource)?.warm()
                // Son seçilen fırça, renk ve yazı biçimi hatırlanır (v29: bu tuvalde seçilenler)
                val last = canvasPrefs.lastBrush?.let { n -> BrushType.entries.firstOrNull { it.name == n } }
                brush.restore(loaded.kind, last, canvasPrefs.lastColor)
                // v56 hızlı fırça seçici: bu modun son kullanılanları ve favorileri
                quickBrush.bind(loaded.kind)
                // v56 resim uçları bu tuvalde de bulunsun (başka tuvalde oluşturulduysa kopyalanır)
                ensureStampAssets(canvasLocalId)
                if (com.karapp.drawguidance.data.model.V56Flags.enabled(com.karapp.drawguidance.data.model.V56Flags.Feature.QUICK_BRUSH, loaded.kind)) quickBrush.used(brush.settings.value.brushType)
                texts.adaptTo(loaded.width, loaded.height)
                canvasPrefs.lastText?.let { (spec, size) -> texts.restore(spec, size.takeIf { it > 0f }) }
                formulas.adaptTo(loaded.width, loaded.height)
            }
            // v18: kağıt rengi başka yerde (tuval ayarları) değiştiyse grafikler açılışta kağıda uydurulur
            val fitted0 = loaded?.let { c ->
                // v24 her sayfa kendi kağıt rengine
                val pages = withContext(Dispatchers.Default) {
                    if (!c.hasPageBackgrounds) com.karapp.drawguidance.ui.drawing.GraphPaper.recolor(c.pages, c.backgroundColorHex)
                    else c.pages.mapIndexed { i, page -> com.karapp.drawguidance.ui.drawing.GraphPaper.recolor(listOf(page), c.paperOf(i))[0] }
                }
                if (pages === c.pages || pages == c.pages) c else c.copy(pages = pages)
            }
            // v23 3B çizim ve sahne döndürme tuvalden 3B düzenleyici penceresine taşındı: eski tuvallerde açık kalmışsa
            // kapanır (3B izler son görünümleriyle kalır)
            val fitted = fitted0?.let { c ->
                if (!c.guide.draw3d) c else c.copy(guide = c.guide.copy(draw3d = false)).also { repository.saveGuide(c.localId, it.guide) }
            }
            store.load(fitted)
            store.elements.value = withContext(store.worker) { store.indexer.update(fitted?.strokes.orEmpty()) }
            // v32 bu türe ilk girişte eğitim önerilir
            // v51 önceki tuvalden yeni alıştırmayla gelindiyse alıştırma hemen açılır (eğitim önerisi yerine)
            val handoff = fitted?.let { com.karapp.drawguidance.ui.screens.canvasedit.challenge.PracticeHandoff.take(canvasLocalId) }
            if (handoff == com.karapp.drawguidance.ui.screens.canvasedit.challenge.PracticeHandoff.LIST) openPracticeList.value = true
            else if (handoff != null) practice.open(handoff) else fitted?.let { tutorialOrNull?.maybeOffer(it.kind) }
            if (fitted != null) drawing.cleanOnOpen(fitted)
            // v51 notta: geçici tuvalde düzenlenen resimler nota aktarılır, açık kalmamış geçici tuvaller silinir
            if (fitted != null && (fitted.kind.isNotes || fitted.kind == com.karapp.drawguidance.data.model.CanvasKind.COMIC) && tempSession == null && tempImages != null) {
                viewModelScope.launch {
                    com.karapp.drawguidance.data.notes.TempCanvases.resultFlow.collect { if (it.sourceId == canvasLocalId) applyTempResult(it) }
                }
                com.karapp.drawguidance.data.notes.TempCanvases.pendingFor(canvasLocalId).forEach { applyTempResult(it) }
                tempImages.cleanup(sourceId = canvasLocalId)
            }
        }
    }

    private val _imageEditChoice = MutableStateFlow<String?>(null)
    /** v52 not almada ve çizgi romanda resim düzenlemenin nerede yapılacağı sorulur (resim elemanının kimliği). */
    val imageEditChoice: StateFlow<String?> = _imageEditChoice.asStateFlow()
    fun dismissImageEditChoice() { _imageEditChoice.value = null }

    /** v52 seçim penceresi: gelişmiş çizimde, foto düzenlemede ya da (çizgi romanda) burada düzenle. */
    fun chooseImageEdit(choice: ImageEditChoice) {
        val id = _imageEditChoice.value ?: return
        _imageEditChoice.value = null
        val image = store.canvas.value?.strokes?.firstOrNull { it.id == id && it.brushType == BrushType.IMAGE } ?: return
        when (choice) {
            ImageEditChoice.ADVANCED -> startTempImage(image)
            ImageEditChoice.PHOTO -> photo.open(image.id)
            ImageEditChoice.HERE -> { store.select(setOf(image.id)); startAction(EditMode.IMAGE_EDIT, setOf(image.id)) }
        }
    }

    /** v52 gelişmiş/basit çizimde Resmi düzenle satırındaki Foto düzenle. */
    fun openPhotoEditor() = photo.open()

    /** v52 geçici tuvalde düzenleme not almada ve çizgi romanda var. */
    private val tempEditKind: Boolean get() = store.canvas.value?.kind?.let { it.isNotes || it == com.karapp.drawguidance.data.model.CanvasKind.COMIC } == true

    /**
     * v51 notta seçili resim geçici gelişmiş çizim tuvalinde düzenlenir. v52 not almada ve çizgi romanda önce nerede
     * düzenleneceği sorulur (gelişmiş çizim / foto düzenleme / çizgi romanda burada).
     */
    override fun editImageElsewhere(image: Stroke): Boolean {
        if (tempImages == null || tempSession != null || !tempEditKind) return false
        _imageEditChoice.value = image.id
        return true
    }

    private fun startTempImage(image: Stroke) {
        val editor = tempImages ?: return
        if (tempSession != null) return
        viewModelScope.launch {
            // Not önce yazılır (düzenleyiciye dönülünce eski içerik yüklenmesin)
            if (store.autosave) saveQueue.flushNow()
            _tempCanvasRequest.value = editor.start(canvasLocalId, image) ?: ""
        }
    }

    override val canEditShapeElsewhere: Boolean get() = tempImages != null && tempSession == null && store.canvas.value?.kind?.isNotes == true

    /** v52 notta seçili düz şekil gelişmiş çizim tuvalinde düzenlenir; kaydedilince nota döner. */
    override fun editTable(id: String): Boolean { tables.edit(id); return true }

    override fun editShapeElsewhere(shape: Stroke): Boolean {
        if (!canEditShapeElsewhere) return false
        val editor = tempImages ?: return false
        viewModelScope.launch {
            if (store.autosave) saveQueue.flushNow()
            _tempCanvasRequest.value = editor.startShape(canvasLocalId, shape) ?: ""
        }
        return true
    }

    /** v51 geçici tuvalde düzenlenen resim nottaki resmin yerine geçer (tek geri alma adımı). */
    private fun applyTempResult(result: com.karapp.drawguidance.data.notes.TempCanvases.Result) {
        if (!appliedTempResults.add(result.tempId)) return
        viewModelScope.launch {
            var c = store.canvas.value ?: return@launch
            val page = c.pages.indexOfFirst { p -> p.any { it.id == result.strokeId } }
            if (page < 0) {
                com.karapp.drawguidance.data.notes.TempCanvases.consumed(result)
                return@launch
            }
            if (page != c.currentPage) {
                store.showPage(page)
                c = store.canvas.value ?: return@launch
            }
            val shapeData = result.strokes
            if (shapeData != null) {
                // v52 şekil: geçici tuvaldeki elemanlar şeklin yerine geçer
                val edited = com.karapp.drawguidance.data.notes.TempShapeGeometry.decode(shapeData)
                val assets = edited.mapNotNull { it.assetId }
                if (assets.isNotEmpty()) assetStore.ensureLoaded(emptyList(), assets)
                store.commit(com.karapp.drawguidance.data.notes.TempShapeGeometry.replace(c.strokes, result.strokeId, edited))
                com.karapp.drawguidance.data.notes.TempCanvases.consumed(result)
                emit(EditEvent.TempShapeApplied)
                return@launch
            }
            assetStore.ensureLoaded(emptyList(), listOf(result.assetId))
            store.commit(c.strokes.map { if (it.id == result.strokeId) com.karapp.drawguidance.data.notes.TempImageGeometry.replace(it, result) else it })
            com.karapp.drawguidance.data.notes.TempCanvases.consumed(result)
            emit(EditEvent.TempImageApplied)
        }
    }

    /** v51 geçici tuvali kaydeder: resim nota aktarılır, geçici tuval silinir, düzenleyici kapanır. */
    private fun commitTempImage() {
        val c = store.canvas.value ?: return
        if (tempFinished) return
        tempFinished = true
        tempImages?.commit(canvasLocalId, c)
        _tempClosed.value = true
    }

    /** v51 geçici tuvalden çıkılıyor: kaydedilmediyse geçici tuval silinir. */
    fun closeTempImage() {
        if (tempSession == null) return
        if (!tempFinished) {
            tempFinished = true
            tempImages?.discard(canvasLocalId)
        }
        _tempClosed.value = true
    }

    override fun onCleared() {
        if (tempSession != null) com.karapp.drawguidance.data.notes.TempCanvases.setActive(canvasLocalId, false)
        // v32 eğitim yarıda kaldıysa tuval eğitimden önceki haline döner (eğitim hiç kaydedilmez)
        if (tutorial.active) tutorial.cancel()
        // Ekran kapanırken bekleyen kayıt beklemeden yazılır
        imageEdit.cancelTrace()
        saveQueue.flushNow()
        if (store.canvas.value != null) runCatching { recent?.closed(canvasLocalId) }
        // v53 kapanan tuvalin çizim önbellekleri (resimler, çizim resimleri, yazı düzenleri, tonlar) bellekte tutulmaz
        com.karapp.drawguidance.platform.DeviceCaps.editorClosed()
        super.onCleared()
    }

    // --- Belge durumu ---

    val canvas: StateFlow<CanvasUi?> = store.canvas.asStateFlow()
    /** Çizimlerden türetilen elemanlar (değen çizimler tek eleman; silgiler görünmez). */
    val elements: StateFlow<ElementIndex> = store.elements.asStateFlow()
    /** Seçili çizim kimlikleri — her zaman elemanlarının ve gruplarının tüm üyelerini içerir. */
    val selectedIds: StateFlow<Set<String>> = store.selectedIds.asStateFlow()

    /** v54 şekil kalıbı için seçili elemanların (grupları dahil) görünür çizimleri; seçim yoksa boş. */
    fun selectedTemplateStrokes(): List<com.karapp.drawguidance.data.model.Stroke> {
        val ids = store.selectedIds.value
        if (ids.isEmpty()) return emptyList()
        val index = store.currentIndex()
        val all = index.expand(ids)
        return index.strokes.filter { it.id in all && !it.hidden && it.brushType != com.karapp.drawguidance.data.model.BrushType.ERASER }
    }
    val visualVersion: StateFlow<Long> = store.visualVersion.asStateFlow()
    val canUndo: StateFlow<Boolean> = store.canUndo.asStateFlow()
    val canRedo: StateFlow<Boolean> = store.canRedo.asStateFlow()

    // --- Mod ve panel ---

    fun togglePanel(tab: PanelTab) {
        _panel.value = if (_panel.value == tab) null else tab
    }

    fun closePanel() { _panel.value = null }

    /**
     * Eylem bitince dönülecek modlar (yığın): en altta eylemden önceki temel mod; düzenleme modunun içinden açılan
     * araç (ör. resim düzenlerken Kırp ya da Kalem) bitince o düzenlemeye dönülür.
     */
    private val returnStack = ArrayDeque<Pair<EditMode, Set<String>>>()

    override fun startAction(mode: EditMode, target: Set<String>) {
        val current = _mode.value
        when {
            current.kind == ModeKind.BASE -> { returnStack.clear(); returnStack.addLast(current to emptySet()) }
            // Düzenlemeye dönülünce düzenlenen eleman yeniden seçilir (araç başka şey seçmiş olabilir)
            current.kind == ModeKind.EDIT && mode.kind == ModeKind.TOOL -> returnStack.addLast(current to store.selection)
            else -> Unit
        }
        when (mode.kind) {
            ModeKind.INSERT -> store.select(emptySet())
            ModeKind.EDIT -> if (target.isNotEmpty()) store.select(store.currentIndex().expand(target))
            else -> Unit
        }
        applyMode(mode)
    }

    override fun continueAction(mode: EditMode) = applyMode(mode)

    /**
     * v17: iş bitince (hangi seçim/eylem olursa olsun) seçim kalkar ve Seç moduna geçilir (Seç = eski İncele: boş yerde
     * kaydır/yakınlaştır, elemana dokununca seç). Yalnızca düzenlemenin içinden açılan araç (resim düzenlerken
     * Kırp/Kalem) bitince o düzenlemeye dönülür. Temel modlarda: Seç'te seçim varsa seçim kalkar, yoksa Çiz'e; Ekle ve
     * Düzenle'den Seç'e dönülür.
     */
    override fun finishAction() {
        val current = _mode.value
        when (current.kind) {
            ModeKind.BASE -> when {
                current == EditMode.DRAW -> Unit
                // v33 boyama bitince çizime dönülür
                current == EditMode.PAINT -> setMode(EditMode.DRAW)
                current == EditMode.SELECT && store.selection.isNotEmpty() -> store.select(emptySet())
                current == EditMode.SELECT -> setMode(EditMode.DRAW)
                else -> toSelect()
            }
            ModeKind.EDIT -> toSelect()
            else -> {
                val (mode, selection) = returnStack.removeLastOrNull() ?: (EditMode.SELECT to emptySet())
                // v18: Düzenle modundan açılan araç (Döndür, Ölçekle, Kırp) bitince Düzenle'ye dönülür
                if (mode == EditMode.EDIT_PICK) {
                    store.select(emptySet())
                    return setMode(EditMode.EDIT_PICK)
                }
                if (mode.kind == ModeKind.EDIT) {
                    val existing = store.strokes.mapTo(HashSet()) { it.id }
                    val restored = selection.filterTo(HashSet()) { it in existing }
                    if (restored.isEmpty()) return toSelect()
                    store.select(restored)
                    setMode(mode)
                } else toSelect()
            }
        }
    }

    /** İş bitti: seçim kalkar, Seç moduna geçilir. */
    private fun toSelect() {
        store.select(emptySet())
        setMode(EditMode.SELECT)
    }

    /** Eylem sona erdi (ör. düzenlenen eleman kalmadı): seçimsiz Seç moduna geçilir. */
    override fun endAction() = toSelect()

    override fun inserted(ids: Set<String>) {
        store.select(store.currentIndex().expand(ids))
        continueAction(EditMode.MOVE)
    }

    override fun onInk(stroke: Stroke) {
        // v44 görev sırasında öneri yok (çizgi görevin değerlendirmesine kalır); v54 kalıpla çizilen çizgi sonra da
        // öneriye katılmaz
        if (inChallenge()) { if (onTemplateSession()) handwriting.exclude(stroke); return }
        // v43 şekil kalıbının üzerinde çizilene öneri yok (kalıbın dışındakilere her zamanki gibi)
        val p = stroke.points
        if (ruler.onTemplate(FloatArray(p.size) { p[it].x }, FloatArray(p.size) { p[it].y })) handwriting.exclude(stroke)
        else handwriting.onInk(stroke)
    }

    /**
     * Mod menüsündeki seçim: temel modlar (Çiz, Seç, Ekle, Düzenle) doğrudan, dönüşüm modları (Taşı, Döndür…) eylem
     * olarak açılır; açık eylemin kendi düğmesine yeniden basılırsa eylem biter.
     */
    /** v37 basit çizimde (basit not dışında) kağıdın kenarına basılı tutunca genişletme modu açılır. */
    fun holdPaperEdge(): Boolean {
        val kind = canvas.value?.kind ?: return false
        if (!kind.simple || kind.isNotes) return false
        chooseMode(EditMode.EXPAND)
        return true
    }

    fun chooseMode(mode: EditMode) {
        // v51 not almada kalem aracı (yol) yok
        if (mode == EditMode.PEN && canvas.value?.kind?.isNotes == true) return
        // v45 boyama modunu seçmek kovayı seçer (boya fırçası [choosePaintBrush] ile)
        if (mode == EditMode.PAINT) _paintBrush.value = false
        when {
            mode == _mode.value && mode.kind != ModeKind.BASE -> finishAction()
            mode.kind == ModeKind.BASE -> setMode(mode)
            else -> startAction(mode)
        }
    }

    /** Mod değiştirir; temel moda geçince eylem yığını boşalır. */
    override fun setMode(mode: EditMode) {
        if (mode.kind == ModeKind.BASE || mode.kind == ModeKind.EDIT) returnStack.clear()
        applyMode(mode)
    }

    /** Modu uygular (politikasız); çizim ve yazı dışındaki modlarda tuval tamamen görünsün diye panel kapanır. */
    private fun applyMode(mode: EditMode) {
        val from = _mode.value
        texts.onModeChange(mode)
        frame.onModeChange(from, mode)
        diagrams.onModeChange(mode)
        shapes.onModeChange(mode)
        pen.onModeChange(mode)
        formulas.onModeChange(mode)
        if (mode != from) handwriting.reset()
        // v56 aşama 9C bıçak yalnız Çiz'de, şekil oluşturucu yalnız Seç'te
        if (mode != EditMode.DRAW && drawing.knife != null) stopKnife()
        if (mode != EditMode.SELECT && builderActive) cancelShapeBuilder()
        // v43 alanla seçim yalnız Seç'te (Ölçekle'ye geçişte de kalır: Bitti ile Seç'e dönülür)
        if (mode != EditMode.SELECT && mode != EditMode.SCALE && mode != EditMode.DRAW) _canvasPick.value = com.karapp.drawguidance.ui.screens.canvasedit.components.CanvasPick.NONE
        if (mode == EditMode.DRAW && _canvasPick.value != com.karapp.drawguidance.ui.screens.canvasedit.components.CanvasPick.EYEDROPPER) _canvasPick.value = com.karapp.drawguidance.ui.screens.canvasedit.components.CanvasPick.NONE
        // v34 Düzenle'de bekleyen şekil aracı Düzenle'den çıkınca (şekil düzenlemeye geçiş dışında) unutulur
        if (mode != EditMode.EDIT_PICK && mode != EditMode.SHAPE_EDIT) _pendingShapeTool.value = null
        if (mode != EditMode.EDIT_PICK) _pendingSceneTool.value = null
        if (mode != from) scene.endAll()
        // v45 boya fırçası yalnız boyama modunda
        if (mode != EditMode.PAINT) { _paintBrush.value = false; kidsBrush.cancel() }
        _mode.value = mode
        // v47 Özellikler açık kalır (seçilen aracın ayarlarını gösterir; ayarı olmayan araçta boş)
        if (mode != EditMode.DRAW && mode != EditMode.TEXT && _panel.value != PanelTab.BRUSH) _panel.value = null
    }

    /**
     * Sayfa değişmeden önce: açık yazı kaydedilir; seçime bağlı modlardan (kırpma, dönüşüm, şekil/diyagram
     * düzenleme) çıkılır, geçici durumlar temizlenir.
     */
    private fun beforePageChange() {
        handwriting.reset()
        formulas.closeEditor()
        texts.commitOpenDraft()
        diagrams.reset()
        shapes.reset()
        when (_mode.value.kind) {
            ModeKind.EDIT -> endAction()
            ModeKind.TOOL -> if (_mode.value != EditMode.TEXT) finishAction()
            else -> Unit
        }
    }

    // --- Geri al / yinele ---

    fun undo() {
        scene.endAll()
        if (store.undo()) afterContentReplaced()
    }

    fun redo() {
        scene.endAll()
        if (store.redo()) afterContentReplaced()
    }

    // --- v43 nesne ve mekân ---

    val harmonyDraft: StateFlow<ObjectSceneController.HarmonyDraft?> get() = scene.harmony
    val placeDraft: StateFlow<ObjectSceneController.PlaceDraft?> get() = scene.place
    fun sceneToolsAvailable(): Boolean = scene.available(store.canvas.value?.kind)
    /** Seçili tek resim (nesne) var mı. */
    fun hasSceneTarget(): Boolean = scene.target() != null
    fun openHarmony(): Boolean = scene.openHarmony()
    fun setHarmonyOptions(o: com.karapp.drawguidance.ui.drawing.scene.SceneAnalyzer.Options) = scene.setHarmonyOptions(o)
    fun applyHarmony() = scene.applyHarmony()
    fun removeHarmony() = scene.removeHarmony()
    fun cancelHarmony() = scene.cancelHarmony()
    fun openPlace(): Boolean = scene.openPlace()
    fun setPlaceManual(horizonY: Float? = null, footY: Float? = placeDraft.value?.footY, scaleMul: Float? = null) = scene.setPlaceManual(horizonY, footY, scaleMul)
    fun applyPlace() = scene.applyPlace()
    fun cancelPlace() = scene.cancelPlace()

    /**
     * Düzenle menüsünde: seçili resim varsa hemen açılır; yoksa araç bekletilir ve dokunulan resimde açılır.
     */
    private val _pendingSceneTool = MutableStateFlow<String?>(null)
    val pendingSceneTool: StateFlow<String?> = _pendingSceneTool.asStateFlow()
    fun startSceneTool(tool: String) {
        val opened = if (tool == SCENE_PLACE) openPlace() else openHarmony()
        if (opened) { _pendingSceneTool.value = null; return }
        _pendingSceneTool.value = if (_pendingSceneTool.value == tool) null else tool
        if (_mode.value != EditMode.EDIT_PICK) setMode(EditMode.EDIT_PICK)
    }

    /** v20 üç parmakla yana kaydırma: sola ([direction] < 0) geri al, sağa yinele; sonuç bildirilir. */
    fun swipeUndo(direction: Int) {
        val redo = direction > 0
        val done = if (redo) store.redo() else store.undo()
        if (done) afterContentReplaced()
        emit(EditEvent.Swiped(redo, done))
    }

    /** Sürüklemeyle birleşen geri alma adımını bitirir (renk penceresi kapanınca). */
    fun endCoalesce() = store.endCoalesce()

    /**
     * Geri alma/yinelemeden sonra eski içeriğe bağlı geçici durumlar temizlenir: diyagram odağı, ok ekleme, şekil
     * bağlama noktası, artık olmayan yazının düzenlemesi; kırpma çerçevesi yeniden hesaplanır; düzenlenen
     * şekil/diyagram kalmadıysa Seç moduna dönülür.
     */
    private fun afterContentReplaced() {
        handwriting.reset()
        formulas.onContentReplaced()
        tables.onContentReplaced()
        diagrams.onContentReplaced()
        shapes.onContentReplaced()
        texts.onContentReplaced()
        frame.onContentReplaced()
    }

    // --- Fırça ---

    val brushSettings: StateFlow<BrushSettings> get() = brush.settings
    /** Silgiden dönülecek fırça (üst menüdeki Fırça Seç düğmesi bunu gösterir). */
    val drawingBrush: StateFlow<BrushType> get() = brush.drawingBrush
    val isBrushPickerOpen: StateFlow<Boolean> get() = brush.isPickerOpen
    val savedColors: StateFlow<List<String>> get() = brush.savedColors

    fun openBrushPicker() = brush.openPicker()
    /** v22 üst menüdeki Fırça düğmesi: silgiden son fırçaya döner, fırçadayken seçiciyi açar. */
    fun brushButton() = brush.brushButton()
    fun closeBrushPicker() = brush.closePicker()
    fun selectBrush(type: BrushType) {
        brush.select(type)
        if (type != BrushType.ERASER) canvasPrefs.lastBrush = type.name
        if (v56(com.karapp.drawguidance.data.model.V56Flags.Feature.QUICK_BRUSH)) quickBrush.used(type)
    }

    // --- v56 hızlı fırça seçici (2) ve damlalığın canlı renk dairesi (2D-5) ---

    /** Şeritten fırça seçimi (şerit açık kalır; çizmeye başlayınca kapanır). */
    fun quickSelect(type: BrushType) = selectBrush(type)

    /** İki parmakla dokunma: son iki fırça arasında geçiş. */
    fun quickSwap(): Boolean {
        if (!v56(com.karapp.drawguidance.data.model.V56Flags.Feature.QUICK_BRUSH) || !_mode.value.allowsDrawTools) return false
        val prev = quickBrush.lists.value.previous(brush.settings.value.brushType) ?: return false
        selectBrush(prev)
        return true
    }

    /** Çizim sürüyor mu (hızlı fırça şeridi kapanır, düğme saydamlaşır). */
    private val _drawingNow = MutableStateFlow(false)
    val drawingNow: StateFlow<Boolean> = _drawingNow.asStateFlow()
    fun drawingChanged(active: Boolean) {
        _drawingNow.value = active
        if (active) quickBrush.setOpen(false) else _penPoint.value = null
    }

    /** Canlı renk dairesi: tuvaldeki nokta, altındaki renk ve önceki (fırça) rengi. */
    data class Loupe(val point: Offset, val hex: String?, val previous: String, val inside: Boolean)
    private val _loupe = MutableStateFlow<Loupe?>(null)
    val loupe: StateFlow<Loupe?> = _loupe.asStateFlow()
    private var loupeSampleAt: Offset? = null

    fun eyedropLoupeOn(): Boolean = v56(com.karapp.drawguidance.data.model.V56Flags.Feature.EYEDROPPER_LOUPE)

    /** Damlalık sürüklenirken (null: bırakıldı ya da iptal). Renk her harekette alınır (2 px'ten az kıpırdamada değil). */
    fun eyedropHover(point: Offset?) {
        if (point == null || !eyedropLoupeOn()) { _loupe.value = null; loupeSampleAt = null; return }
        val c = store.canvas.value ?: return
        val inside = com.karapp.drawguidance.data.model.EyedropLoupe.inside(point.x, point.y, c.width.toFloat(), c.height.toFloat())
        val prev = _loupe.value
        val last = loupeSampleAt
        val hex = if (prev != null && last != null && (last - point).getDistance() < 2f) prev.hex
        else if (!inside) null
        else runCatching { com.karapp.drawguidance.ui.drawing.ColorSampler.sample(c, point.x, point.y) }.getOrNull().also { loupeSampleAt = point }
        _loupe.value = Loupe(point, hex, prev?.previous ?: brush.settings.value.colorHex, inside)
    }

    /** Renk penceresindeki damlalıktan açıldıysa renk alınınca pencere yeniden açılır. */
    private val _reopenColorSheet = MutableStateFlow(false)
    val reopenColorSheet: StateFlow<Boolean> = _reopenColorSheet.asStateFlow()

    fun eyedropFromColorSheet() {
        _reopenColorSheet.value = true
        if (_canvasPick.value != com.karapp.drawguidance.ui.screens.canvasedit.components.CanvasPick.EYEDROPPER) {
            toggleCanvasPick(com.karapp.drawguidance.ui.screens.canvasedit.components.CanvasPick.EYEDROPPER)
        }
    }

    fun colorSheetReopened() { _reopenColorSheet.value = false }

    // --- v56 aşama 5: gren, resimden fırça, doku dolgusu ---

    /** Resim uçları bu tuvalin varlıklarında yoksa kopyalanır; hiçbir yerde yoksa fırça kendi ucuna döner. */
    private fun ensureStampAssets(canvasLocalId: String) {
        val ids = brush.stampAssetIds()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val found = runCatching { assetStore.entities(ids) }.getOrDefault(emptyList()).associateBy { it.id }
            for (id in ids) {
                val e = found[id]
                when {
                    e == null -> brush.replaceStamp(id, null)
                    e.canvasLocalId != canvasLocalId -> runCatching { assetStore.insertCopy(e, canvasLocalId) }.getOrNull()
                        ?.let { brush.replaceStamp(id, it); assetStore.ensureLoaded(emptyList(), listOf(it)) }
                    else -> assetStore.ensureLoaded(emptyList(), listOf(id))
                }
            }
        }
    }

    /** Seçilen resimden fırça ucu: seçili fırça damgayla çizilemiyorsa keçeli kaleme geçilir. */
    fun createImageBrush(uri: android.net.Uri) {
        images.createImageBrush(uri) { id ->
            if (!com.karapp.drawguidance.data.settings.BrushTuning.supportsImageTip(brush.settings.value.brushType)) selectBrush(BrushType.MARKER)
            brush.updateTuning { it.copy(stampAssetId = id) }
        }
    }

    fun removeImageBrush() = brush.updateTuning { it.copy(stampAssetId = null) }

    /** Gren: doku seç (null: kapalı), ölçek, güç, kayar. */
    fun setGrainTexture(id: String?) = brush.updateTuning { t ->
        t.copy(grain = id?.let { (t.grain ?: com.karapp.drawguidance.data.model.GrainSpec(it)).copy(textureId = it) })
    }
    fun setGrain(change: (com.karapp.drawguidance.data.model.GrainSpec) -> com.karapp.drawguidance.data.model.GrainSpec) =
        brush.updateTuning { t -> t.copy(grain = t.grain?.let(change)) }

    /**
     * Dokuyla doldur: seçim alanı varsa alanın içi, yoksa ([wholePage] ya da alan yokken) etkin katmanın bütün sayfası
     * fırça rengi ve opaklığıyla, seçilen hazır dokuyla doldurulur. Tek geri alma adımı.
     */
    fun textureFill(textureId: String, wholePage: Boolean = false): Boolean {
        val c = store.canvas.value ?: return false
        val b = brushSettings.value
        val grain = com.karapp.drawguidance.data.model.GrainSpec(textureId)
        val area = store.area.value?.takeIf { !wholePage && !it.inverted }?.points
        val pts = area ?: com.karapp.drawguidance.data.model.V56Textures.pageRect(c.width.toFloat(), c.height.toFloat())
        val fill = com.karapp.drawguidance.data.model.V56Selection.lassoFill(
            java.util.UUID.randomUUID().toString(), pts, b.colorHex, b.opacity, c.strokes.size, grain = grain,
        )
        store.commit(c.strokes + fill, useArea = false)
        return true
    }

    /** v56 Dönüştür panelini açar (seçim gerekir). */
    fun openTransform(kind: com.karapp.drawguidance.data.model.TransformKind = com.karapp.drawguidance.data.model.TransformKind.UNIFORM): Boolean {
        if (!v56(com.karapp.drawguidance.data.model.V56Flags.Feature.TRANSFORM_PANEL)) return false
        liquify.cancel()
        return transform.open(kind)
    }

    /** v56 Yansıma: seçimin altına dikey çevrilmiş, dalgalı ve solan kopya (tek geri alma adımı). */
    fun reflectSelection(): Boolean {
        val c = store.canvas.value ?: return false
        val ids = store.selection
        if (ids.isEmpty()) return false
        val b = com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementOps.selectionBounds(c.strokes, ids) ?: return false
        val before = c.strokes.mapTo(HashSet()) { it.id }
        val out = com.karapp.drawguidance.data.model.Reflection.create(c.strokes, ids, b.left, b.top, b.right, b.bottom, 4f, { java.util.UUID.randomUUID().toString() })
        if (out.size == c.strokes.size) return false
        store.commit(out, useArea = false)
        store.select(out.filter { it.id !in before }.mapTo(HashSet()) { it.id })
        return true
    }

    /**
     * v56 Sıvılaştır: seçili resim (yoksa seçim ya da etkin katman resme çevrilerek) ağla deforme edilir.
     */
    fun startLiquify() {
        if (!v56(com.karapp.drawguidance.data.model.V56Flags.Feature.REFLECTION_LIQUIFY) || liquify.isOpen) return
        transform.cancel()
        val c = store.canvas.value ?: return
        val active = store.activeLayer.value
        val ids = store.selection.ifEmpty {
            c.strokes.filter { !it.hidden && com.karapp.drawguidance.data.model.Layers.of(it, c.layers) == active }.mapTo(HashSet()) { it.id }
        }
        if (ids.isEmpty()) return
        val single = c.strokes.singleOrNull { it.id in ids }?.takeIf { it.brushType == BrushType.IMAGE }
        if (single != null) { liquify.begin(single.id); return }
        viewModelScope.launch { images.rasterize(ids)?.let { liquify.begin(it) } }
    }

    // --- v56 aşama 6A (2E): hizala ve dağıt, vektör efektleri, dönüşümü tekrarla ---

    private fun unitBounds(s: com.karapp.drawguidance.data.model.Stroke): FloatArray? =
        com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementOps.bounds(s)?.let { floatArrayOf(it.left, it.top, it.right, it.bottom) }

    /** Ana nesne: seçimde en son dokunulan eleman (yoksa en büyük birim). */
    fun keyObjectId(): String? {
        val c = store.canvas.value ?: return null
        val sel = store.selection
        selection.lastPicked?.takeIf { it in sel }?.let { return it }
        return com.karapp.drawguidance.data.model.V56Arrange.units(c.strokes, sel, ::unitBounds).maxByOrNull { (it.r - it.l) * (it.b - it.t) }?.ids?.firstOrNull()
    }

    /** Nesne › Hizala: tek geri alma adımı; değişiklik olduysa true. */
    fun alignObjects(align: com.karapp.drawguidance.data.model.V56Arrange.Align, ref: com.karapp.drawguidance.data.model.V56Arrange.Ref): Boolean {
        val c = store.canvas.value ?: return false
        val units = com.karapp.drawguidance.data.model.V56Arrange.units(c.strokes, store.selection, ::unitBounds)
        if (units.isEmpty()) return false
        val off = com.karapp.drawguidance.data.model.V56Arrange.align(units, align, ref, c.width.toFloat(), c.height.toFloat(), keyObjectId())
        if (off.all { it.first == 0f && it.second == 0f }) return false
        store.commit(com.karapp.drawguidance.data.model.V56Arrange.applyOffsets(c.strokes, units, off), useArea = false)
        return true
    }

    /** Nesne › Dağıt (en az 3 birim): tek geri alma adımı. */
    fun distributeObjects(mode: com.karapp.drawguidance.data.model.V56Arrange.Distribute): Boolean {
        val c = store.canvas.value ?: return false
        val units = com.karapp.drawguidance.data.model.V56Arrange.units(c.strokes, store.selection, ::unitBounds)
        val off = com.karapp.drawguidance.data.model.V56Arrange.distribute(units, mode)
        if (off.all { it.first == 0f && it.second == 0f }) return false
        store.commit(com.karapp.drawguidance.data.model.V56Arrange.applyOffsets(c.strokes, units, off), useArea = false)
        return true
    }

    private var effectBase: List<com.karapp.drawguidance.data.model.Stroke>? = null

    /** Nesne › Efektler önizlemesi (geçmişe girmez). */
    fun previewEffect(effect: com.karapp.drawguidance.data.model.V56Arrange.Effect, amount: Float) {
        val base = effectBase ?: store.canvas.value?.strokes?.also { effectBase = it } ?: return
        store.previewStrokes(com.karapp.drawguidance.data.model.V56Arrange.effect(base, store.selection, effect, amount))
    }

    /** Efekti uygula: tek geri alma adımı. */
    fun applyEffect(effect: com.karapp.drawguidance.data.model.V56Arrange.Effect, amount: Float): Boolean {
        val base = effectBase ?: store.canvas.value?.strokes ?: return false
        store.endPreview(); effectBase = null
        val out = com.karapp.drawguidance.data.model.V56Arrange.effect(base, store.selection, effect, amount)
        if (out == base) return false
        store.commit(out, useArea = false)
        return true
    }

    fun cancelEffect() { if (effectBase != null) { store.endPreview(); effectBase = null } }

    /** Seçimde efekt uygulanabilecek vektör eleman var mı. */
    fun canEffect(): Boolean = store.canvas.value?.strokes?.any { it.id in store.selection && com.karapp.drawguidance.data.model.V56Arrange.canEffect(it) } == true

    /** Dönüşümü tekrarla / çoğalt (panel kapalıyken). */
    fun repeatTransform(duplicate: Boolean): Boolean = transform.applyLast(duplicate) { java.util.UUID.randomUUID().toString() }

    /** Katman menüsünden: o katman etkin yapılıp bütün sayfası dokuyla doldurulur. */
    fun textureFillLayer(layerId: String, textureId: String) {
        setActiveLayer(layerId)
        textureFill(textureId, wholePage = true)
    }
    fun toggleEraser() = brush.toggleEraser()
    fun updateBrushSize(sizePx: Float) = brush.updateSize(sizePx)
    fun updateBrushColor(colorHex: String) {
        brush.updateColor(colorHex)
        // v30 yazı düzenlenirken kalem rengi değişince düzenlenen yazının rengi de değişir (önceden yalnız yeni
        // yazılar kalem rengini alıyordu; düzenlenen yazı eski renginde kalıyordu)
        texts.setColor(colorHex)
        if (canvasPrefs.lastColor != brush.settings.value.colorHex) canvasPrefs.lastColor = brush.settings.value.colorHex
    }
    // --- v43 damlalık ve alanla seçim ---

    private val _canvasPick = MutableStateFlow(com.karapp.drawguidance.ui.screens.canvasedit.components.CanvasPick.NONE)
    val canvasPick: StateFlow<com.karapp.drawguidance.ui.screens.canvasedit.components.CanvasPick> = _canvasPick.asStateFlow()

    /** Aracı açar; açıksa kapatır. Alanla seçim Seç modunda çalışır (gerekirse Seç'e geçilir). */
    fun toggleCanvasPick(pick: com.karapp.drawguidance.ui.screens.canvasedit.components.CanvasPick) {
        val next = if (_canvasPick.value == pick) com.karapp.drawguidance.ui.screens.canvasedit.components.CanvasPick.NONE else pick
        _canvasPick.value = next
        if ((next == com.karapp.drawguidance.ui.screens.canvasedit.components.CanvasPick.RECT_SELECT ||
                next == com.karapp.drawguidance.ui.screens.canvasedit.components.CanvasPick.LASSO_SELECT ||
                next == com.karapp.drawguidance.ui.screens.canvasedit.components.CanvasPick.MAGIC_WAND ||
                next == com.karapp.drawguidance.ui.screens.canvasedit.components.CanvasPick.LASSO_FILL) && _mode.value != EditMode.SELECT) {
            setMode(EditMode.SELECT)
        }
    }

    /** Damlalık: noktadaki renk fırça rengi olur (bir kez; araç kapanır). Renk bulunduysa onu döndürür. */
    fun eyedrop(point: Offset): String? {
        val c = store.canvas.value ?: return null
        _canvasPick.value = com.karapp.drawguidance.ui.screens.canvasedit.components.CanvasPick.NONE
        _loupe.value = null
        loupeSampleAt = null
        // v56 parmak sayfanın dışında bırakıldıysa iptal (renk değişmez)
        if (eyedropLoupeOn() && !com.karapp.drawguidance.data.model.EyedropLoupe.inside(point.x, point.y, c.width.toFloat(), c.height.toFloat())) return null
        val hex = runCatching { com.karapp.drawguidance.ui.drawing.ColorSampler.sample(c, point.x, point.y) }.getOrNull() ?: return null
        updateBrushColor(hex)
        // v45 boyamada (kova ve boya fırçası) alınan renk boyanın rengi de olur
        if (_mode.value == EditMode.PAINT) shapes.setRegionColor(hex)
        return hex
    }

    /** Dikdörtgenle seçim; bir şey seçildiyse seçim elle ölçeklensin diye Ölçekle açılır. */
    fun rectSelect(a: Offset, b: Offset) {
        val r = com.karapp.drawguidance.ui.screens.canvasedit.editor.AreaSelect.rect(a, b)
        if (r.width < 1f || r.height < 1f) return
        store.lastArea = com.karapp.drawguidance.data.model.V56Selection.rect(r.left, r.top, r.right, r.bottom)
        if (selection.selectInArea(add = false) { r.contains(it) } > 0) startAction(EditMode.SCALE)
    }

    /** Kalemle çizilen kapalı çizginin içindekiler seçilir. */
    fun lassoSelect(points: List<Offset>) {
        if (!com.karapp.drawguidance.ui.screens.canvasedit.editor.AreaSelect.usable(points, 4f)) return
        store.lastArea = points.map { com.karapp.drawguidance.data.model.StrokePoint(it.x, it.y) }
        selection.selectInArea(add = false) { com.karapp.drawguidance.ui.screens.canvasedit.editor.AreaSelect.inside(points, it) }
    }

    fun updateBrushOpacity(opacity: Float) = brush.updateOpacity(opacity)
    fun updateBrushFlow(flow: Float) = brush.updateFlow(flow)
    fun updateBrushStrength(strength: Float) = brush.updateStrength(strength)
    /** v20 seçili fırçanın ayarları (aralık, çizim hızı, sabitleme, eğim, 3B). */
    fun updateBrushTuning(change: (com.karapp.drawguidance.data.settings.BrushTuning) -> com.karapp.drawguidance.data.settings.BrushTuning) = brush.updateTuning(change)
    fun resetBrushTuning() = brush.resetTuning()
    /** v51 klon/onarma kaynağı seçimi. */
    val cloneSourcePicking get() = brush.cloneSourcePicking
    val cloneSource get() = brush.cloneSource
    fun pickCloneSource() = brush.startPickCloneSource()
    fun saveCurrentColor() = brush.saveCurrentColor()
    fun saveColor(colorHex: String) = brush.saveColor(colorHex)
    fun removeSavedColor(colorHex: String) = brush.removeSavedColor(colorHex)

    // --- Çizim ve komutlar ---

    /** v56 çizilmekte olan çizgi (canlı yayında canlı mürekkep; yalnız yayın varken dinlenir). */
    data class InkProgress(val strokeId: String, val points: List<StrokePoint>)
    private val _inkProgress = kotlinx.coroutines.flow.MutableSharedFlow<InkProgress>(
        extraBufferCapacity = 1, onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )
    val inkProgress: kotlinx.coroutines.flow.SharedFlow<InkProgress> = _inkProgress
    fun onStrokeProgress(strokeId: String, points: List<StrokePoint>) {
        _inkProgress.tryEmit(InkProgress(strokeId, points))
        // v56 kalem hızlı fırça düğmesinin üstünden geçerse düğme kenara kayar
        points.lastOrNull()?.let { _penPoint.value = Offset(it.x, it.y) }
    }

    private val _penPoint = MutableStateFlow<Offset?>(null)
    /** v56 çizilmekte olan çizginin son noktası (tuval koordinatı; çizim bitince null). */
    val penPoint: StateFlow<Offset?> = _penPoint.asStateFlow()

    fun onStrokeFinished(points: List<StrokePoint>, strokeId: String? = null, penEraser: Boolean = false) =
        drawing.onStrokeFinished(points, strokeId, penEraser)

    /** v18: kalemin silgi ucunun boyu. */
    fun eraserSize(): Float = brush.eraserSize()
    /** v18: seçili çizgiler arasındaki küçük boşlukları kapatır (eski komut kutusundaki "boşlukları doldur"). */
    fun fillGaps() = drawing.fillGaps()

    // --- Seçim ve eleman işlemleri ---

    fun pickAt(x: Float, y: Float, tolerance: Float): Boolean = selection.pickAt(x, y, tolerance)
    fun selectOnlyAt(x: Float, y: Float, tolerance: Float): Boolean = selection.selectOnlyAt(x, y, tolerance)
    /** v40 elemana 5 sn basılı tutunca: yalnız o seçilir ve Taşı moduna geçilir. */
    fun holdMoveAt(x: Float, y: Float, tolerance: Float): Boolean =
        selection.holdMoveAt(x, y, tolerance).also { moved -> if (moved) kidsPaint.hover(null, -1) }
    fun toggleElement(elementId: String) = selection.toggleElement(elementId)
    fun toggleGroup(groupId: String) = selection.toggleGroup(groupId)
    fun selectAt(x: Float, y: Float, tolerance: Float): Boolean {
        // v56 aşama 9C şekil oluşturucu açıkken dokunuş bölge seçer
        if (_builder.value != null) return shapeBuilderTap(x, y)
        // v56 2E grup içindeyken dokunuş yalnız o elemanı seçer (gruptan dışarı dokununca çıkılır)
        if (v56(com.karapp.drawguidance.data.model.V56Flags.Feature.ADV_SELECTION)) selection.selectInGroupAt(x, y, tolerance)?.let { return it }
        return selection.selectAt(x, y, tolerance)
    }
    fun deselectAt(x: Float, y: Float, tolerance: Float): Boolean {
        // v56 aşama 9C şekil oluşturucu: çift dokunuş bölgeyi silinecek yapar
        if (_builder.value != null) return shapeBuilderTap(x, y, remove = true)
        // v56 2E gruplu elemana çift dokunuş grubun içine girer
        if (v56(com.karapp.drawguidance.data.model.V56Flags.Feature.ADV_SELECTION) && selection.enterGroupAt(x, y, tolerance)) return true
        return selection.deselectAt(x, y, tolerance)
    }

    /** v56 özellik bu tuvalin modunda açık mı ([com.karapp.drawguidance.data.model.V56Flags]). */
    fun v56(feature: com.karapp.drawguidance.data.model.V56Flags.Feature): Boolean =
        com.karapp.drawguidance.data.model.V56Flags.enabled(feature, store.canvas.value?.kind ?: com.karapp.drawguidance.data.model.CanvasKind.ART)

    // v56 seçim işlemleri ve seçim alanı
    val area: kotlinx.coroutines.flow.StateFlow<com.karapp.drawguidance.data.model.ClipMask?> get() = store.area
    private val _wandCriterion = kotlinx.coroutines.flow.MutableStateFlow(com.karapp.drawguidance.data.model.V56Selection.WandCriterion.COLOR)
    val wandCriterion: kotlinx.coroutines.flow.StateFlow<com.karapp.drawguidance.data.model.V56Selection.WandCriterion> = _wandCriterion
    private val _wandTolerance = kotlinx.coroutines.flow.MutableStateFlow(0.15f)
    val wandTolerance: kotlinx.coroutines.flow.StateFlow<Float> = _wandTolerance
    fun setWandCriterion(c: com.karapp.drawguidance.data.model.V56Selection.WandCriterion) { _wandCriterion.value = c }
    fun setWandTolerance(t: Float) { _wandTolerance.value = t.coerceIn(0f, 1f) }
    fun magicWand(point: Offset, tolerance: Float) { selection.magicWand(point.x, point.y, tolerance, _wandCriterion.value, _wandTolerance.value) }
    fun selectAll() { selection.selectAll() }
    fun invertSelection() { selection.invert() }
    fun paintInsideSelection() { if (selection.areaFromSelection()) setMode(EditMode.DRAW) }
    fun clearArea() = selection.clearArea()
    fun invertArea() = selection.invertArea()
    fun cycleAreaFeather(): Float = selection.cycleFeather()
    fun growArea(d: Float) = selection.growArea(d)
    fun lassoFill(points: List<Offset>? = null) {
        val b = brushSettings.value
        selection.lassoFill(points?.map { com.karapp.drawguidance.data.model.StrokePoint(it.x, it.y) }, b.colorHex, b.opacity)
    }
    fun canClipMask(): Boolean = selection.canClipMask()
    fun hasClipMask(): Boolean = selection.hasClipMask()
    fun clipMask() = selection.clipMask()
    fun releaseClipMask() = selection.releaseClipMask()
    fun clearSelection() = selection.clear()
    // v56 2C-3 pano (çocuk modunda yok)
    val clipboardContent get() = clipboard.content
    fun canUseClipboard(): Boolean = v56(com.karapp.drawguidance.data.model.V56Flags.Feature.CROSS_PAGE_PASTE)
    fun cutSelected() { clipboard.cut() }
    fun copySelected() { clipboard.copy() }
    fun paste(anchor: Offset? = null, visible: FloatArray? = null) {
        clipboard.paste(anchor, visible)
        // v56: yapıştırılan seçim Dönüştür panelinde açılır (planın 2A bölümü)
        if (store.selection.isNotEmpty()) openTransform()
    }

    /** v23 iki parmakla uzun basma: altındaki eleman seçilir (boş yerde seçim kalkar). */
    fun selectForContext(x: Float, y: Float, tolerance: Float): Boolean = selection.selectForContext(x, y, tolerance)

    /** v23 seçili elemanların sırası ([com.karapp.drawguidance.ui.screens.canvasedit.editor.ZOrder]). */
    fun reorderSelected(direction: Int) = selection.reorder(direction)

    /** v23 not tuvalinde seçili şekilden ok çıkar: sürüklenen/dokunulan yere ok çizilir. */
    fun arrowFromSelection() {
        val c = store.canvas.value ?: return
        val bounds = com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementOps.selectionBounds(c.strokes, store.selection) ?: return
        shapes.startArrowFrom(bounds)
    }
    val arrowFrom get() = shapes.arrowFrom
    fun groupSelected() = selection.group()
    fun ungroupSelected() = selection.ungroup()
    fun deleteSelected() = selection.delete()
    fun duplicateSelected() = selection.duplicate()

    /** v30 seçili elemanlardan istenenleri gizler ([elementIds] null: seçili elemanların hepsi). */
    fun hideElements(elementIds: Collection<String>? = null) {
        val n = if (elementIds == null) selection.hideSelected() else selection.hide(elementIds)
        if (n > 0) emit(EditEvent.HiddenChanged(hidden = n, shown = 0))
    }

    /** v30 gizlenen elemanları gösterir ([elementIds] null: hepsi). */
    fun showElements(elementIds: Collection<String>? = null) {
        val n = selection.show(elementIds)
        if (n > 0) emit(EditEvent.HiddenChanged(hidden = 0, shown = n))
    }
    fun moveSelected(dx: Float, dy: Float) = selection.move(dx, dy)

    // --- v18 Taşı: konum bilgisi, ok düğmeleriyle kaydırma, ortalama ---

    /** Seçimin sol üst köşesi (tuval pikseli); seçim yoksa null. */
    fun selectionOrigin(): Offset? =
        com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementOps.selectionBounds(store.strokes, store.selection)?.topLeft

    /** Seçimi, sol üst köşesi ([x], [y]) olacak şekilde taşır. */
    fun moveSelectionTo(x: Float, y: Float) {
        val origin = selectionOrigin() ?: return
        selection.move(x - origin.x, y - origin.y)
    }

    /** Seçimi sayfanın ortasına taşır. */
    fun centerSelection() {
        val current = store.canvas.value ?: return
        val bounds = com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementOps.selectionBounds(store.strokes, store.selection) ?: return
        selection.move(current.width / 2f - bounds.center.x, current.height / 2f - bounds.center.y)
    }

    // --- v18 Genişlet: kenardan genişletme ve boyut girme ---

    enum class Side { LEFT, TOP, RIGHT, BOTTOM }

    /** Kağıdı bir kenarından (o yöndeki boyutun %10'u, en az 10 px) dışarı genişletir. */
    fun expandSide(side: Side) {
        val current = store.canvas.value ?: return
        fun step(size: Int) = ((size * 0.1f / 10f).roundToInt() * 10).coerceAtLeast(10)
        when (side) {
            Side.LEFT -> frame.resizeCanvas(-step(current.width), 0, current.width, current.height)
            Side.RIGHT -> frame.resizeCanvas(0, 0, current.width + step(current.width), current.height)
            Side.TOP -> frame.resizeCanvas(0, -step(current.height), current.width, current.height)
            Side.BOTTOM -> frame.resizeCanvas(0, 0, current.width, current.height + step(current.height))
        }
    }

    /**
     * Kağıt boyutunu [width]×[height] yapar; [anchorX]/[anchorY] (0 = sol/üst, 0,5 = orta, 1 = sağ/alt) sabit kalan
     * yerdir: büyütünce kağıt bu yerden dışarı doğru genişler, küçültünce karşı kenarlardan kırpılır.
     */
    fun setCanvasSize(width: Int, height: Int, anchorX: Float, anchorY: Float) {
        val current = store.canvas.value ?: return
        val w = width.coerceIn(com.karapp.drawguidance.ui.screens.canvasedit.state.FrameController.MIN_CANVAS, com.karapp.drawguidance.ui.screens.canvasedit.state.FrameController.MAX_CANVAS)
        val h = height.coerceIn(com.karapp.drawguidance.ui.screens.canvasedit.state.FrameController.MIN_CANVAS, com.karapp.drawguidance.ui.screens.canvasedit.state.FrameController.MAX_CANVAS)
        val left = -((w - current.width) * anchorX.coerceIn(0f, 1f)).roundToInt()
        val top = -((h - current.height) * anchorY.coerceIn(0f, 1f)).roundToInt()
        frame.resizeCanvas(left, top, left + w, top + h)
    }
    fun rotateSelected(degrees: Float, pivot: Offset? = null) = selection.rotate(degrees, pivot)
    fun scaleSelected(sx: Float, sy: Float, pivot: Offset? = null) = selection.scale(sx, sy, pivot)

    // --- Kırpma, genişletme, arka plan ---

    val cropRect: StateFlow<Rect?> get() = frame.cropRect
    val cropTarget: StateFlow<Set<String>> get() = frame.cropTarget
    val cropArea: StateFlow<Rect?> get() = frame.cropArea
    fun updateCrop(rect: Rect) = frame.updateCrop(rect)
    fun resetCrop() = frame.resetCrop()
    fun applyCrop(): Offset? = frame.applyCrop()
    fun resizeCanvas(left: Int, top: Int, right: Int, bottom: Int) = frame.resizeCanvas(left, top, right, bottom)
    /**
     * v24 arka plan sayfa sayfa mı değişir: not tuvalinde birden çok sayfa varken açık sayfanın kendi arka planı olur
     * (pencerede "Tüm sayfalara uygula" ile hepsine); resim tuvalinde ve tek sayfada ortak arka plan değişir.
     */
    fun backgroundPerPage(): Boolean {
        val c = store.canvas.value ?: return false
        return c.kind.paged && c.pageCount > 1
    }

    fun setBackgroundColor(colorHex: String, coalesce: String? = null) = frame.setBackgroundColor(colorHex, coalesce, pageOnly = backgroundPerPage())

    /**
     * v23 arka plan resmi ve deseni. Ortak arka plan görünüm ayarı gibi hemen kaydedilir (geri alma geçmişine girmez);
     * v24 not tuvalinde birden çok sayfa varken yalnız açık sayfanınki değişir (geri alınabilir).
     */
    fun setBackgroundSpec(spec: com.karapp.drawguidance.data.model.BackgroundSpec) {
        val c = store.canvas.value ?: return
        if (backgroundPerPage()) {
            frame.setPageSpec(spec, coalesce = "page-background")
            return
        }
        frame.clearPageSpecs()
        if (c.background == spec) return
        store.updateCanvas { it.copy(background = spec) }
        persistBackgrounds()
    }

    /** v24 açık sayfanın arka planı (renk, desen, resim) tüm sayfalara uygulanır. */
    fun applyBackgroundToAllPages() {
        val spec = frame.applyPageToAll() ?: return
        store.updateCanvas { it.copy(background = spec) }
        persistBackgrounds()
    }

    private fun persistBackgrounds() {
        val c = store.canvas.value ?: return
        viewModelScope.launch { repository.saveBackgroundSpec(canvasLocalId, c.background, c.pageBackgrounds) }
    }

    /** v23 galeriden arka plan resmi (v24 açık sayfanın arka planına). */
    fun importBackground(uri: android.net.Uri) = images.importBackground(uri) { assetId ->
        val spec = store.canvas.value?.pageBackground ?: com.karapp.drawguidance.data.model.BackgroundSpec()
        setBackgroundSpec(spec.copy(imageId = com.karapp.drawguidance.data.model.BackgroundSpec.ASSET + assetId, imageFit = com.karapp.drawguidance.data.model.BoxFit.COVER))
    }

    /** v41 galeriden arka plan resmi taslak için: tuvalin varlıklarına eklenir, arka plana konmaz (pencerede Uygula). */
    fun importBackgroundAsset(uri: android.net.Uri, onReady: (String) -> Unit) = images.importBackground(uri) { assetId -> onReady(assetId) }

    /**
     * v41 arka plan penceresinde Uygula: kağıt rengi ve arka plan (eski arka planın yerine) tek geri alma adımıyla
     * konur; [allPages] not tuvalinde bütün sayfalara.
     */
    fun applyBackground(paperHex: String, spec: com.karapp.drawguidance.data.model.BackgroundSpec, allPages: Boolean) {
        val c = store.canvas.value ?: return
        // Kağıt rengi ve arka plan tek geri alma adımı olur
        val key = "background-apply-" + System.nanoTime()
        val paperChanged = !c.pagePaperHex.equals(paperHex, ignoreCase = true)
        val done = paperChanged && !backgroundTransform.onCurrentPage() &&
            frame.setBackgroundColor(paperHex, key, pageOnly = false, shared = spec)
        if (!done) {
            if (paperChanged) setBackgroundColor(paperHex, key)
            backgroundTransform.commit(spec, key)
        }
        if (allPages) applyBackgroundToAllPages()
    }

    /** v41 arka plan eleman gibi seçilebilir mi: açık sayfada arka plan (gradyan, desen ya da resim) var ve nokta kağıtta. */
    fun backgroundSelectable(x: Float, y: Float): Boolean {
        val c = store.canvas.value ?: return false
        return !c.pageBackground.isEmpty && x in 0f..c.width.toFloat() && y in 0f..c.height.toFloat()
    }

    /** v41 seçili arka planı kaldır (kağıt rengi kalır; geri alınabilir). */
    fun removeBackground() = backgroundTransform.commit(com.karapp.drawguidance.data.model.BackgroundSpec())

    // --- Biçim: dolgu, yazı, hizalama ---

    fun setTextFill(colorHex: String) = style.setTextFill(colorHex)

    /**
     * v29 yazı düzenlenirken harf rengi: düzenlenen yazının rengi değişir; yeni yazıda kalem rengi (yeni yazılar kalem
     * rengini alır).
     */
    fun setTextColor(colorHex: String) {
        if (texts.setColor(colorHex)) return
        if (texts.draft.value != null) updateBrushColor(colorHex) else setTextFill(colorHex)
    }
    fun setTextOutline(colorHex: String?, width: Float) = style.setTextOutline(colorHex, width)
    fun fillTargetIds(): Set<String> = style.fillTargetIds()
    fun currentFillColor(): String = style.currentFillColor()
    fun applyFill(colorHex: String) = style.applyFill(colorHex)
    fun currentFillGradient() = style.currentFillGradient()

    /** v27 boya kutusunda gradyan: seçili elemanlara ya da (seçim yoksa) kağıda. */
    fun applyFillGradient(gradient: com.karapp.drawguidance.data.model.GradientSpec?) = style.applyFillGradient(gradient) { g ->
        val c = store.canvas.value ?: return@applyFillGradient
        setBackgroundSpec(c.pageBackground.copy(gradient = g))
    }
    fun fillSelected(colorHex: String) = style.fillSelected(colorHex)
    fun recolorSelected(colorHex: String) = style.recolorSelected(colorHex)
    fun alignSelection(align: TextAlignKind) = style.alignSelection(align)

    // --- Sayfalar ---

    fun addPage() = pages.addPage()
    fun duplicatePage(index: Int) = pages.duplicatePage(index)
    fun deletePage(index: Int) = pages.deletePage(index)
    fun goToPage(index: Int) = pages.goToPage(index)

    // --- Kılavuz ve perspektif ---

    fun setGuide(spec: GuideSpec, persist: Boolean = true) = guides.setGuide(spec, persist)
    fun addGuide(kind: GuideKind) = guides.addGuide(kind)
    fun addPerspective(points: Int) = guides.addPerspective(points)
    fun removeGuide() = guides.removeGuide()
    fun clearGuides() = guides.clearGuides()
    fun removePerspective() = guides.removePerspective()
    fun moveVanishingPoint(index: Int, fx: Float, fy: Float, persist: Boolean) = guides.moveVanishingPoint(index, fx, fy, persist)
    /** v56 aşama 8 simetri kılavuzu. */
    fun setSymmetry(kind: com.karapp.drawguidance.data.model.SymmetryKind?) = guides.setSymmetry(kind)
    fun updateSymmetry(spec: com.karapp.drawguidance.data.model.SymmetrySpec, persist: Boolean = true) = guides.updateSymmetry(spec, persist)

    /** v56 aşama 8 "Desen yap": seçili elemanlar [gap] aralıkla tuval boyunca döşenir (tek geri alma adımı). */
    fun makePattern(gap: Float): Int {
        val c = store.canvas.value ?: return 0
        val sel = c.strokes.filter { it.id in store.selection && !it.hidden }
        val b = selectionBoundsArray(sel) ?: return 0
        val out = com.karapp.drawguidance.data.model.V56Symmetry.pattern(sel, b, c.width, c.height, gap) { java.util.UUID.randomUUID().toString() }
        if (out.isEmpty()) return 0
        val group = out.first().groupId
        val strokes = c.strokes.map { if (it.id in store.selection && it.groupId == null) it.copy(groupId = group) else it } + out
        store.commit(strokes, useArea = false)
        return out.size
    }

    /** Desen önizlemesi için kopya sayısı. */
    fun patternCount(gap: Float): Int {
        val c = store.canvas.value ?: return 0
        val b = selectionBoundsArray(c.strokes.filter { it.id in store.selection && !it.hidden }) ?: return 0
        return com.karapp.drawguidance.data.model.V56Symmetry.patternCount(b, c.width, c.height, gap)
    }

    private fun selectionBoundsArray(sel: List<Stroke>): FloatArray? {
        val r = sel.mapNotNull { com.karapp.drawguidance.ui.drawing.StrokeRenderer.paintBounds(it) }
            .reduceOrNull { a, b -> androidx.compose.ui.geometry.Rect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom)) } ?: return null
        return floatArrayOf(r.left, r.top, r.right, r.bottom)
    }

    // --- Şekiller ---

    /** Eklenecek şeklin kimliği (yerleşik, grafik ya da kütüphane). */
    val shape: StateFlow<String> get() = shapes.shape
    val isShapePickerOpen: StateFlow<Boolean> get() = shapes.isPickerOpen
    val shapeStyle: StateFlow<ShapeStyle> get() = shapes.shapeStyle
    val graphSpec: StateFlow<GraphSpec> get() = shapes.graphSpec
    /** Seçili tek vektör şekil (biçim ve şekil düzenleme bunu kullanır). */
    val selectedShape: StateFlow<Stroke?> get() = shapes.selectedShape
    val shapeTool: StateFlow<ShapeTool> get() = shapes.tool
    /** Bağla aracında ilk dokunulan nokta (ikinci dokunuşta ona bağlanır). */
    val shapeConnectFrom: StateFlow<Offset?> get() = shapes.connectFrom

    fun openShapePicker() = shapes.openPicker()
    fun openGraphPicker() = shapes.openGraphPicker()
    val shapePickerGraphsOnly: StateFlow<Boolean> get() = shapes.graphsOnly
    fun closeShapePicker() { addAnchor = null; shapes.closePicker() }
    fun selectShape(kind: ShapeKind) = selectShapeId(kind.name)
    fun selectShapeId(id: String) {
        shapes.selectShape(id)
        // "Buraya ekle" ile açıldıysa şekil dokunulan yere hemen eklenir
        takeAddAnchor()?.let { shapes.addShapeAt(java.util.UUID.randomUUID().toString(), it) }
    }
    fun setNewShapeFill(hex: String?) {
        shapes.setNewShapeFill(hex)
        canvasPrefs.shapeFill = hex
    }
    fun updateShapeStyle(style: ShapeStyle) = shapes.updateShapeStyle(style)

    // ---------------------------------------------------------------- v56 aşama 8A (2E görünüm, bağlı kopya, maske, Tekrarla)

    private fun newId() = java.util.UUID.randomUUID().toString()

    /** Seçili elemanların 2E görünümü (ilk seçili; yoksa öntanımlı). */
    fun selectedVectorStyle(): com.karapp.drawguidance.data.model.VectorStyle {
        val c = store.canvas.value ?: return com.karapp.drawguidance.data.model.VectorStyle()
        return c.strokes.firstOrNull { it.id in store.selection }?.style2 ?: com.karapp.drawguidance.data.model.VectorStyle()
    }

    /** Seçili elemanlara 2E görünüm değişikliği ([coalesce]: kaydırırken tek geri alma adımı). */
    fun updateVectorStyle(coalesce: String? = null, change: (com.karapp.drawguidance.data.model.VectorStyle) -> com.karapp.drawguidance.data.model.VectorStyle) {
        val c = store.canvas.value ?: return
        val ids = store.selection
        if (ids.isEmpty()) return
        var changed = false
        val out = c.strokes.map { s ->
            if (s.id !in ids) return@map s
            val next = change(s.style2 ?: com.karapp.drawguidance.data.model.VectorStyle()).takeUnless { it.isDefault }
            if (next == s.style2) s else { changed = true; s.copy(style2 = next) }
        }
        if (changed) store.commit(out, coalesce = coalesce?.let { "v2-$it" }, useArea = false)
    }

    /** Bağlı kopya (kaynak değişince güncellenir); kopyalar seçilir. */
    fun cloneSelection(): Int {
        val c = store.canvas.value ?: return 0
        val out = com.karapp.drawguidance.data.model.V56Vector.clone(c.strokes, store.selection, 24f, 24f, ::newId)
        val added = out.drop(c.strokes.size)
        if (added.isEmpty()) return 0
        store.commit(out, useArea = false)
        store.select(added.map { it.id }.toSet())
        return added.size
    }

    fun selectionHasClone(): Boolean = store.canvas.value?.strokes?.any { it.id in store.selection && it.cloneOf != null } == true

    fun unlinkClones() {
        val c = store.canvas.value ?: return
        val out = com.karapp.drawguidance.data.model.V56Vector.unlink(c.strokes, store.selection)
        if (out != c.strokes) store.commit(out, useArea = false)
    }

    /** Seçimdeki tekrar ayarı (kaynaktan ya da kopyadan). */
    fun selectedRepeat(): com.karapp.drawguidance.data.model.RepeatSpec? =
        store.canvas.value?.let { com.karapp.drawguidance.data.model.V56Vector.repeatOf(it.strokes, store.selection) }

    private fun repeatSources(strokes: List<Stroke>): Set<String> {
        val byId = strokes.associateBy { it.id }
        return store.selection.mapNotNull { id -> byId[id]?.let { s -> s.cloneOf?.takeIf { it.repeat }?.source ?: s.id } }.toSet()
    }

    /** Tekrar önizlemesi (geçmişe girmez). */
    fun previewRepeat(spec: com.karapp.drawguidance.data.model.RepeatSpec) {
        val base = (if (store.inPreview) store.endPreview() else store.canvas.value) ?: return
        val (out, _) = com.karapp.drawguidance.data.model.V56Vector.repeat(base.strokes, repeatSources(base.strokes), spec, ::newId)
        store.previewStrokes(out)
    }

    fun cancelRepeatPreview() { store.endPreview() }

    /** Tekrarı uygular (tek geri alma adımı). Dönen: kopya sayısı. */
    fun applyRepeat(spec: com.karapp.drawguidance.data.model.RepeatSpec): Int {
        store.endPreview()
        val c = store.canvas.value ?: return 0
        val sources = repeatSources(c.strokes)
        val (out, n) = com.karapp.drawguidance.data.model.V56Vector.repeat(c.strokes, sources, spec, ::newId)
        if (n == 0) return 0
        store.commit(out, useArea = false)
        store.select(sources)
        return n
    }

    fun removeRepeat() {
        store.endPreview()
        val c = store.canvas.value ?: return
        val out = com.karapp.drawguidance.data.model.V56Vector.removeRepeat(c.strokes, store.selection)
        if (out != c.strokes) { store.commit(out, useArea = false); store.select(store.selection.filter { id -> out.any { it.id == id } }.toSet()) }
    }

    /** Opaklık maskesi: seçimdeki en üstteki nesne alttakilerin maskesi olur. */
    fun makeOpacityMask(): Boolean {
        val c = store.canvas.value ?: return false
        val out = com.karapp.drawguidance.data.model.V56Vector.makeOpacityMask(c.strokes, store.selection)
        if (out == c.strokes) return false
        store.commit(out, useArea = false)
        store.select(store.selection.filter { id -> out.any { it.id == id } }.toSet())
        return true
    }

    fun selectionHasOpacityMask(): Boolean = store.canvas.value?.strokes?.any { it.id in store.selection && !it.style2?.opacityMask.isNullOrEmpty() } == true

    fun releaseOpacityMask() {
        val c = store.canvas.value ?: return
        val out = com.karapp.drawguidance.data.model.V56Vector.releaseOpacityMask(c.strokes, store.selection)
        if (out != c.strokes) store.commit(out, useArea = false)
    }

    // ---------------------------------------------------------------- v56 aşama 9C yol işlemleri, bıçak, efektler

    private fun pathCommit(out: List<Stroke>?, selectNew: Boolean = true): Boolean {
        val c = store.canvas.value ?: return false
        if (out == null || out == c.strokes) return false
        val before = c.strokes.mapTo(HashSet()) { it.id }
        store.commit(out, useArea = false)
        if (selectNew) store.select(out.filter { it.id !in before }.mapTo(HashSet()) { it.id }.ifEmpty { store.selection.filter { id -> out.any { it.id == id } }.toSet() })
        return true
    }

    /** Seçimde yol işlemine girebilen eleman sayısı. */
    fun pathOperableCount(): Int = store.canvas.value?.strokes?.count { it.id in store.selection && com.karapp.drawguidance.data.model.V56PathOps.operable(it) } ?: 0

    fun pathOp(op: com.karapp.drawguidance.data.model.V56PathOps.PathOp): Boolean {
        val c = store.canvas.value ?: return false
        return pathCommit(com.karapp.drawguidance.data.model.V56PathOps.apply(c.strokes, store.selection, op, ::newId))
    }

    fun outlineStroke(): Boolean = store.canvas.value?.let { pathCommit(com.karapp.drawguidance.data.model.V56PathOps.outlineStroke(it.strokes, store.selection, ::newId)) } == true
    fun separatePaths(): Boolean = store.canvas.value?.let { pathCommit(com.karapp.drawguidance.data.model.V56PathOps.separate(it.strokes, store.selection, ::newId)) } == true
    fun closeSelectedPaths(): Boolean = store.canvas.value?.let { pathCommit(com.karapp.drawguidance.data.model.V56PathOps.closePaths(it.strokes, store.selection), false) } == true
    fun reverseSelectedPaths(): Boolean = store.canvas.value?.let { pathCommit(com.karapp.drawguidance.data.model.V56PathOps.reversePaths(it.strokes, store.selection), false) } == true
    fun envelopeDistort(): Boolean = store.canvas.value?.let { pathCommit(com.karapp.drawguidance.data.model.V56PathOps.envelope(it.strokes, store.selection), false) } == true

    /** Seçimin efektleri (ilk efekt uygulanabilir elemanın). */
    fun selectedEffects(): List<com.karapp.drawguidance.data.model.VectorEffect> =
        store.canvas.value?.strokes?.firstOrNull { it.id in store.selection && com.karapp.drawguidance.data.model.V56PathOps.effectable(it) }?.style2?.effects.orEmpty()

    fun canApplyEffects(): Boolean = store.canvas.value?.strokes?.any { it.id in store.selection && com.karapp.drawguidance.data.model.V56PathOps.effectable(it) } == true

    private fun effected(strokes: List<Stroke>, effects: List<com.karapp.drawguidance.data.model.VectorEffect>): List<Stroke> = strokes.map { s ->
        if (s.id in store.selection && com.karapp.drawguidance.data.model.V56PathOps.effectable(s)) com.karapp.drawguidance.data.model.V56PathOps.withEffects(s, effects) else s
    }

    /** Efekt önizlemesi (geçmişe girmez). */
    fun previewEffects(effects: List<com.karapp.drawguidance.data.model.VectorEffect>) {
        val base = (if (store.inPreview) store.endPreview() else store.canvas.value) ?: return
        store.previewStrokes(effected(base.strokes, effects))
    }

    fun cancelEffectsPreview() { store.endPreview() }

    /** Efektleri uygular (tek geri alma adımı; liste boşsa efektler kaldırılır). */
    fun applyEffects(effects: List<com.karapp.drawguidance.data.model.VectorEffect>) {
        store.endPreview()
        val c = store.canvas.value ?: return
        val out = effected(c.strokes, effects)
        if (out != c.strokes) store.commit(out, useArea = false)
    }

    private val _knife = MutableStateFlow(false)
    /** Bıçak açık mı (çizilen çizgi kesme çizgisi olur). */
    val knife: StateFlow<Boolean> = _knife.asStateFlow()
    private var knifeIds: Set<String> = emptySet()

    /** Bıçağı açar: Çiz modunda çizilen her çizgi seçili (yoksa kilitsiz katmanlardaki tüm) şekilleri keser. */
    fun startKnife() {
        knifeIds = store.selection
        _knife.value = true
        setMode(EditMode.DRAW)
        drawing.knife = { pts -> applyKnife(pts); true }
    }

    fun stopKnife() { _knife.value = false; drawing.knife = null; knifeIds = emptySet() }

    fun applyKnife(points: List<com.karapp.drawguidance.data.model.StrokePoint>): Boolean {
        val c = store.canvas.value ?: return false
        val locked = c.layers.filter { it.locked || !it.visible }.mapTo(HashSet()) { it.id }
        val out = com.karapp.drawguidance.data.model.V56PathOps.knife(c.strokes, knifeIds.filter { id -> c.strokes.any { it.id == id } }.toSet(), points, ::newId, locked)
            ?: return false
        val before = c.strokes.mapTo(HashSet()) { it.id }
        store.commit(out, useArea = false)
        // Kesilen parçalar sonraki kesimlere de açık kalır
        if (knifeIds.isNotEmpty()) knifeIds = knifeIds.filter { id -> out.any { it.id == id } }.toSet() + out.filter { it.id !in before }.map { it.id }
        return true
    }

    /** Şekil oluşturucu durumu: bölgeler (tuval koordinatı), birleşecekler (sırayla), silinecekler. */
    data class ShapeBuilderUi(val regions: List<List<List<Offset>>>, val merge: List<Int> = emptyList(), val remove: Set<Int> = emptySet())

    private val _builder = MutableStateFlow<ShapeBuilderUi?>(null)
    val shapeBuilder: StateFlow<ShapeBuilderUi?> = _builder.asStateFlow()
    private var builderIds: Set<String> = emptySet()
    private var builderActive = false
    private var builderRegions: List<Pair<List<List<com.karapp.drawguidance.data.model.V56Bool.P>>, Stroke>> = emptyList()

    /** Şekil oluşturucuyu açar (seçimde en az iki yol işlemine girebilen eleman). */
    fun startShapeBuilder(): Boolean {
        val c = store.canvas.value ?: return false
        val sel = c.strokes.filter { it.id in store.selection && com.karapp.drawguidance.data.model.V56PathOps.operable(it) }
        if (sel.size < 2) return false
        builderIds = sel.map { it.id }.toSet()
        builderRegions = com.karapp.drawguidance.data.model.V56PathOps.builderRegions(sel)
        builderActive = true
        _builder.value = ShapeBuilderUi(builderRegions.map { (rs, _) -> rs.map { r -> r.map { Offset(it.x.toFloat(), it.y.toFloat()) } } })
        setMode(EditMode.SELECT)
        return true
    }

    /** Dokunuş: bölge birleşecekler listesine girer/çıkar; [remove] true: bölge silinecek olarak işaretlenir. */
    fun shapeBuilderTap(x: Float, y: Float, remove: Boolean = false): Boolean {
        val b = _builder.value ?: return false
        val i = com.karapp.drawguidance.data.model.V56PathOps.regionAt(builderRegions, x, y)
        if (i < 0) return true
        _builder.value = if (remove) b.copy(remove = if (i in b.remove) b.remove - i else b.remove + i, merge = b.merge - i)
        else b.copy(merge = if (i in b.merge) b.merge - i else b.merge + i, remove = b.remove - i)
        return true
    }

    fun applyShapeBuilder(): Boolean {
        val b = _builder.value ?: return false
        val c = store.canvas.value
        _builder.value = null
        builderActive = false
        if (c == null) return false
        return pathCommit(com.karapp.drawguidance.data.model.V56PathOps.build(c.strokes, builderIds, b.merge, b.remove, ::newId))
    }

    fun cancelShapeBuilder() { _builder.value = null; builderActive = false }

    /** v56 aşama 9C düğüm türü ve Bezier tutamakları (şekil düzenleme, Noktalar aracı). */
    fun selectedHandles(): List<Pair<Offset, Offset>> = shapes.selectedHandles()
    fun setNodeSmooth(smooth: Boolean) = shapes.setNodeSmooth(smooth)
    val smoothNodes: StateFlow<Boolean> get() = shapes.smoothNodes
    fun extendFromSelectedNode(): Boolean = shapes.extendFromSelectedNode()
    fun selectedNodeIsEnd(): Boolean = shapes.selectedNodeIsEnd()
    fun bezierOn(): Boolean = shapes.bezierOn()
    fun updateGraphSpec(graph: GraphSpec) = shapes.updateGraphSpec(graph)
    fun shapeStrokes(gestureId: String, start: Offset, end: Offset): List<Stroke> = shapes.shapeStrokes(gestureId, start, end)
    fun addShape(gestureId: String, start: Offset, end: Offset) = shapes.addShape(gestureId, start, end)
    fun addShapeAt(gestureId: String, center: Offset) = shapes.addShapeAt(gestureId, center)
    fun selectShapeTool(tool: ShapeTool) = shapes.selectTool(tool)
    /** v17: seçili şekli Alan boya aracıyla düzenlemeye açar. */
    fun editShapeRegions() {
        shapes.editSelected()
        if (_mode.value == EditMode.SHAPE_EDIT) shapes.selectTool(ShapeTool.FILL)
    }
    /** v17 Alan boya rengi (null: kalem rengi, "": temizle). */
    val regionColor: StateFlow<String?> get() = shapes.regionColor
    fun setRegionColor(hex: String?) = shapes.setRegionColor(hex)
    fun regionInk(): String = shapes.regionInk()
    fun editSelectedShape() = shapes.editSelected()
    /** v51 not almada düz şekillerde "Şekli düzenle" gösterilmez (vektör düzenleme yok). */
    fun canEditSelectedShape(): Boolean = shapes.canEditSelected()
    /** v45 parametreli şekillerin değer/formül düzenleyicisi ve tutamakları. */
    val paramShapes: com.karapp.drawguidance.ui.screens.canvasedit.state.ParamShapeController get() = shapes.paramShapes
    fun applyShapeTool(point: Offset, tolerance: Float = 12f) = shapes.applyTool(point, tolerance)
    /**
     * v33 Şekil boyama: dokunulan şeklin alanını boyar; boyanacak kapalı alan yoksa bildirir. v39 boyanan şekil önce
     * seçilir (kutusuyla görünür), sonra boyanır; boyama tek geri alma adımıdır.
     */
    fun paintAt(point: Offset) {
        if (!shapes.paintAt(point, store.lockedIds())) emit(EditEvent.PaintMiss)
        kidsPaint.refresh()
    }
    /** v37 çocuk modunda kova imleci: parmağın yeri, altındaki şekil ve boyanmaya hazır alan. */
    private val kidsPaint = com.karapp.drawguidance.ui.screens.canvasedit.state.KidsPaintController(viewModelScope) { p -> shapes.paintTarget(p, store.lockedIds()) }
    val paintCursor: StateFlow<com.karapp.drawguidance.ui.screens.canvasedit.state.PaintCursor?> get() = kidsPaint.cursor
    fun paintHover(point: Offset?, hold: Int) = kidsPaint.hover(point, hold)
    /** v37 çocuk modunda 2 sn basılı tutunca bütün şekil boyanır; boyanacak şekil yoksa bildirir. */
    fun paintWhole(point: Offset): Boolean {
        val ok = shapes.paintWhole(point, store.lockedIds())
        if (!ok) emit(EditEvent.PaintMiss)
        kidsPaint.refresh()
        return ok
    }
    // --- v45 çocuk modunda boya fırçası ---

    /** Boyama modunda boya fırçası mı (false: kova). Başka moda geçince kapanır. */
    private val _paintBrush by lazy { MutableStateFlow(false) }
    val paintBrush: StateFlow<Boolean> get() = _paintBrush

    /** Boya fırçasını seçer (boyama moduna geçilir; renk kovanınki, boy çocuk fırçasınınki). */
    fun choosePaintBrush() {
        setMode(EditMode.PAINT)
        _paintBrush.value = true
    }

    /** Boyanın rengi: boyama satırında seçilen renk, yoksa fırçanın rengi. */
    fun paintColor(): String = shapes.regionColor.value?.takeIf { it.isNotEmpty() } ?: shapes.regionInk()

    private val kidsBrush by lazy {
        com.karapp.drawguidance.ui.screens.canvasedit.state.KidsBrushController(
            regionAt = { p -> shapes.brushRegion(p, store.lockedIds()) },
            color = ::paintColor,
            size = { brush.settings.value.sizePx },
            commit = { region, points, size, color -> shapes.commitBrush(region, points, size, color) },
        )
    }
    /** Süren fırça darbesi (önizleme). */
    val brushLive: StateFlow<com.karapp.drawguidance.ui.screens.canvasedit.state.BrushLive?> get() = kidsBrush.live

    /** Fırça darbesi başladı: gizli ya da kilitli katmana boyanmaz. Başladıysa true. */
    fun brushDown(point: Offset): Boolean {
        val current = store.canvas.value ?: return false
        val active = current.layers.firstOrNull { it.id == store.activeLayer.value }
        if (active != null && (!active.visible || active.locked)) {
            emit(EditEvent.LayerLocked)
            return false
        }
        kidsBrush.begin(point)
        return true
    }
    fun brushMove(point: Offset) = kidsBrush.move(point)
    /** Fırça darbesi bitti: kaydedilir (tek geri alma adımı); kovanın imleci de yeni boyaya göre yenilenir. */
    fun brushUp(point: Offset? = null): Boolean = kidsBrush.end(point).also { kidsPaint.refresh() }
    fun brushCancel() = kidsBrush.cancel()

    /** v38 çocuk modunda çizim görevi (seviyeler, puan, ilerleme): seviye kurulurken çizim moduna geçilir. */
    val challenge by lazy {
        com.karapp.drawguidance.ui.screens.canvasedit.challenge.KidsChallengeController(
            store, viewModelScope, before = { chooseMode(EditMode.DRAW); handwriting.reset() }, after = ::afterContentReplaced,
        )
    }
    /** v50 yetişkin çizim alıştırmaları (basit ve gelişmiş resim): alıştırma kurulurken çizim moduna geçilir. */
    /** v52 ana sayfadaki Alıştırmalar ile açıldı: ekran alıştırma listesini açar ve bunu sıfırlar. */
    val openPracticeList = kotlinx.coroutines.flow.MutableStateFlow(false)

    val practice by lazy {
        practiceCreated = true
        com.karapp.drawguidance.ui.screens.canvasedit.challenge.PracticeController(
            store, viewModelScope, before = { chooseMode(EditMode.DRAW); handwriting.reset() }, after = ::afterContentReplaced,
            newCanvas = ::practiceInNewCanvas,
            adFree = {
                runCatching {
                    org.koin.core.context.GlobalContext.getOrNull()?.getOrNull<com.karapp.drawguidance.data.billing.SubscriptionRepository>()
                        ?.entitlements?.value?.adFree(System.currentTimeMillis())
                }.getOrNull() == true
            },
        )
    }

    private val _openCanvas = MutableSharedFlow<String>(extraBufferCapacity = 1)
    /** v51 düzenleyici bu tuvale geçsin (yeni alıştırma yeni tuvalde). */
    val openCanvas: SharedFlow<String> = _openCanvas

    /**
     * v51 başka bir alıştırmaya geçilirken önceki alıştırmada çizim vardı: bu tuval kaydedilir, aynı tür, boyut, kâğıt
     * ve klasörde yeni bir tuval oluşturulur ve alıştırma orada açılır ([PracticeHandoff]).
     */
    private fun practiceInNewCanvas(index: Int) {
        val c = store.canvas.value ?: return
        val level = com.karapp.drawguidance.ui.screens.canvasedit.challenge.PracticeLevels.forCanvas(c.kind).getOrNull(index) ?: return
        viewModelScope.launch {
            texts.commitOpenDraft()
            store.saveNow()
            saveQueue.flushNow()
            withContext(Dispatchers.Default) { saveQueue.awaitIdle() }
            val name = com.karapp.drawguidance.i18n.I18n.tr(level.title) + " · " + (index + 1)
            val created = runCatching {
                repository.createCanvas(
                    name, c.width, c.height, c.backgroundColorHex, c.aiEnabled, c.kind, 1, folderId = c.folderId, background = c.background,
                )
            }.getOrNull() ?: return@launch
            com.karapp.drawguidance.ui.screens.canvasedit.challenge.PracticeHandoff.put(created.localId, index)
            _openCanvas.emit(created.localId)
        }
    }
    /** Şekil düzenlemede seçime dahil parçalar (Özellik bunlara uygulanır; boşsa tüm şekle). */
    val shapeParts: StateFlow<Set<Int>> get() = shapes.parts
    /** v16: vektör düzenlemede seçili noktalar / çizgi. */
    val vectorSelection: StateFlow<VectorSelection> get() = shapes.vector
    fun shapeDragBegin(point: Offset, tolerance: Float, handleDistance: Float): ShapeDrag = shapes.beginDrag(point, tolerance, handleDistance)
    fun shapeDragPreview(kind: ShapeDrag, from: Offset, to: Offset, path: List<Offset>, tolerance: Float): Stroke? =
        shapes.dragPreview(kind, from, to, path, tolerance)
    fun shapeDragEnd(kind: ShapeDrag, from: Offset, to: Offset, path: List<Offset>, tolerance: Float) =
        shapes.endDrag(kind, from, to, path, tolerance)
    fun canCutVector(): Boolean = shapes.canCut()
    fun cutVector() = shapes.cutSelected()
    fun canDeleteVector(): Boolean = shapes.canDelete()
    fun deleteVector() = shapes.deleteSelected()

    /** v36 basit düzenleme: Noktaları azalt (seçili şeklin vektör noktaları en belirginlerine iner). */
    fun canSimplifyVector(): Boolean = shapes.canSimplify()
    /** v52 basit modda vektör düzenleme ayarı: ayrıntılı (bütün) noktalar. */
    val fineVectorNodes: StateFlow<Boolean> get() = shapes.fineNodes
    fun setFineVectorNodes(value: Boolean) = shapes.setFineNodes(value)
    fun simplifyVector() = shapes.simplifySelected()

    /**
     * v36 basit menüdeki Şekli düzenle: seçili şekil sade nokta düzenlemesinde açılır; şekil seçili değilse dokunulan
     * şekil açılır (düğme yeniden basılınca bekleme kalkar).
     */
    fun simpleShapeEdit() {
        when {
            _mode.value == EditMode.SHAPE_EDIT -> Unit
            // Bekleme yeniden basılınca kalkar; basit menüde Düzenle satırı olmadığından Seç'e dönülür
            _mode.value == EditMode.EDIT_PICK && _pendingShapeTool.value == ShapeTool.NODES -> setMode(EditMode.SELECT)
            else -> editShapeWith(ShapeTool.NODES)
        }
    }
    fun selectedEdgeAngle(): Float? = shapes.selectedEdgeAngle()
    fun selectedEdgeCurved(): Boolean = shapes.selectedEdgeCurved()
    fun turnSelectedEdge(degrees: Float) = shapes.turnSelectedEdge(degrees)
    fun setSelectedEdgeAngle(degrees: Float) = shapes.setSelectedEdgeAngle(degrees)
    fun straightenSelectedEdge() = shapes.straightenSelectedEdge()
    fun applyPartStyle(style: PartStyle) = shapes.applyPartStyle(style)
    fun partStyleOf(): PartStyle = shapes.partStyleOf()
    val graphEdit: StateFlow<ShapeController.GraphEditRequest?> get() = shapes.graphEdit
    fun applyGraphEdit(text: String) = shapes.applyGraphEdit(text)
    fun dismissGraphEdit() = shapes.dismissGraphEdit()
    fun updateSelectedGraph(block: (com.karapp.drawguidance.content.graph.GraphParams) -> com.karapp.drawguidance.content.graph.GraphParams) =
        shapes.updateSelectedGraph(block)
    fun selectedGraphParams(): com.karapp.drawguidance.content.graph.GraphParams? = shapes.selectedGraphParams()

    // --- Diyagramlar ---

    val diagramKind: StateFlow<DiagramKind> get() = diagrams.kind
    val isDiagramPickerOpen: StateFlow<Boolean> get() = diagrams.isPickerOpen
    /** Seçili tek diyagram elemanı. */
    val selectedDiagram: StateFlow<Stroke?> get() = diagrams.selected
    val diagramFocus: StateFlow<DiagramFocus?> get() = diagrams.focus
    val diagramLinking: StateFlow<Boolean> get() = diagrams.linking
    /** Ok ekleme sürüyorsa kaynağı ve çıkış tarafı. */
    val diagramLink: StateFlow<DiagramLink?> get() = diagrams.link

    fun openDiagramPicker() = diagrams.openPicker()
    fun closeDiagramPicker() { addAnchor = null; diagrams.closePicker() }
    fun selectDiagram(kind: DiagramKind) {
        diagrams.selectKind(kind)
        takeAddAnchor()?.let { diagrams.addAt(java.util.UUID.randomUUID().toString(), it) }
    }
    fun addDiagram(gestureId: String, start: Offset, end: Offset) = diagrams.add(gestureId, start, end)
    fun addDiagramAt(gestureId: String, center: Offset) = diagrams.addAt(gestureId, center)
    fun updateDiagram(strokeId: String, coalesce: String? = null, block: (DiagramSpec) -> DiagramSpec) = diagrams.update(strokeId, coalesce, block)
    fun addDiagramNode(strokeId: String, text: String) = diagrams.addNode(strokeId, text)
    fun addDiagramEdge(strokeId: String, from: String, to: String, label: String = "") = diagrams.addEdge(strokeId, from, to, label)
    fun removeDiagramNode(strokeId: String, nodeId: String) = diagrams.removeNode(strokeId, nodeId)
    fun removeDiagramEdge(strokeId: String, index: Int) = diagrams.removeEdge(strokeId, index)
    fun renameDiagramNode(strokeId: String, nodeId: String, text: String) = diagrams.renameNode(strokeId, nodeId, text)
    fun setDiagramNodeBox(strokeId: String, nodeId: String, box: DiagramBox) = diagrams.setNodeBox(strokeId, nodeId, box)
    fun editSelectedDiagram() = diagrams.editSelected()
    fun diagramTap(point: Offset, tolerance: Float) = diagrams.tap(point, tolerance)
    fun diagramHasHandleAt(point: Offset, tolerance: Float): Boolean = diagrams.hasHandleAt(point, tolerance)
    fun diagramDragLink(from: Offset, to: Offset, tolerance: Float) = diagrams.dragLink(from, to, tolerance)
    fun diagramAddStep() = diagrams.addStep()
    fun diagramStartLink() = diagrams.startLink()
    fun diagramRenameFocused(text: String) = diagrams.renameFocused(text)
    fun diagramSetFocusedBox(box: DiagramBox) = diagrams.setFocusedBox(box)
    fun diagramSetEdgeSides(fromSide: DiagramSide? = null, toSide: DiagramSide? = null) = diagrams.setFocusedEdgeSides(fromSide, toSide)
    fun diagramFlipEdge() = diagrams.flipFocusedEdge()
    fun diagramSetColor(part: DiagramController.ColorPart, hex: String?) = diagrams.setFocusedColor(part, hex)
    fun diagramColor(part: DiagramController.ColorPart): String? = diagrams.focusedColor(part)
    fun diagramDeleteFocused() = diagrams.deleteFocused()
    fun diagramToggleFocal() = diagrams.toggleFocusedFocal()
    val diagramTheme: StateFlow<String> get() = diagrams.theme
    fun setDiagramTheme(id: String) = diagrams.setTheme(id)

    // --- Yazı ---

    val textSettings: StateFlow<TextSettings> get() = texts.settings
    val textDraft: StateFlow<TextDraft?> get() = texts.draft
    /** v28 yazarken önizleme (tuvaldeki yerinde, kendi biçimiyle). */
    fun textPreview(content: String): com.karapp.drawguidance.data.model.Stroke? = texts.previewStroke(content)
    fun updateDraftText(text: String) = texts.updateDraftText(text)
    fun beginTextAt(x: Float, y: Float, tolerance: Float) = texts.beginAt(x, y, tolerance)
    /** v37 Yazı modunda sürüklenerek çizilen kutu: yazı oraya, kutunun eninde (paragraf) yazılır. */
    fun beginTextBox(from: Offset, to: Offset, tolerance: Float) = texts.beginBox(from, to, tolerance)
    /** v37 açık yazı kutusu tutamağından taşındı (tuval birimi). */
    fun moveTextDraft(dx: Float, dy: Float) = texts.moveDraft(dx, dy)
    /** v37 açık yazının kutu türü: paragraf (eni sabit) ya da kısa yazı/başlık (yazıya göre). */
    fun setTextParagraphBox(paragraph: Boolean) = texts.setDraftParagraph(paragraph)
    val isTextDrafting: Boolean get() = texts.draft.value != null
    fun editSelectedText() = texts.editSelected()
    fun cancelText() = texts.cancel()
    /** "Yazıyı bitir": yazı kaydedilir ve yazı eylemi biter (eylemden önceki moda dönülür). */
    fun confirmText(content: String) {
        texts.confirm(content)
        if (_mode.value == EditMode.TEXT) finishAction()
    }
    // --- v42 yol üzerinde yazı ---

    private val _textPathPick = MutableStateFlow<String?>(null)
    /** Yola yerleştirilmeyi bekleyen yazının kimliği (kullanıcı bir kalem yoluna dokunacak). */
    val textPathPick: StateFlow<String?> = _textPathPick.asStateFlow()

    /** Sayfada yazı yerleştirilebilecek yol (kalem yolu ya da vektör çizgi) var mı. */
    fun hasTextPathHosts(): Boolean = store.canvas.value?.strokes?.any(com.karapp.drawguidance.ui.drawing.TextOnPath::canHost) == true

    /**
     * "Yol üzerinde": yazılan metin kaydedilir; sayfada tek yol varsa yazı hemen onun üstüne yerleşir, birden çok yol
     * varsa kullanıcının dokunacağı yol beklenir ([pickTextPath]).
     */
    fun startTextOnPath(content: String) {
        val editing = texts.draft.value?.editingId
        confirmText(content)
        val id = editing ?: store.selectedIds.value.singleOrNull() ?: return
        startTextOnPathFor(id)
    }

    /** Seçili (ya da verilen) yazıyı yola yerleştirme. */
    fun startTextOnPathFor(id: String) {
        val current = store.canvas.value ?: return
        val text = current.strokes.firstOrNull { it.id == id && it.brushType == BrushType.TEXT } ?: return
        val hosts = current.strokes.filter(com.karapp.drawguidance.ui.drawing.TextOnPath::canHost)
        when {
            // Yol yoksa düğme kapalıdır (arayüz "önce kalem aracıyla bir yol çiz" der)
            hosts.isEmpty() -> Unit
            hosts.size == 1 -> attachText(text.id, hosts.single().id)
            else -> _textPathPick.value = text.id
        }
    }

    /** Yol seçimi: [p] noktasındaki yol bulunursa yazı onun üstüne yerleşir. */
    fun pickTextPath(p: Offset, tolerance: Float): Boolean {
        val textId = _textPathPick.value ?: return false
        val current = store.canvas.value ?: return false
        val hostStroke = com.karapp.drawguidance.ui.drawing.TextOnPath.hostAt(current.strokes, p, tolerance) ?: return false
        _textPathPick.value = null
        return attachText(textId, hostStroke.id)
    }

    fun cancelTextPathPick() { _textPathPick.value = null }

    private fun attachText(textId: String, hostId: String): Boolean {
        val current = store.canvas.value ?: return false
        val text = current.strokes.firstOrNull { it.id == textId } ?: return false
        val hostStroke = current.strokes.firstOrNull { it.id == hostId } ?: return false
        val updated = com.karapp.drawguidance.ui.drawing.TextOnPath.attach(text, hostStroke) ?: return false
        store.commit(current.strokes.map { if (it.id == textId) updated else it })
        store.select(setOf(textId))
        return true
    }

    /** Yol yazısının ayarları (başlangıç, yolun altında, ters yön); null olanlar değişmez. */
    fun adjustTextPath(id: String, start: Float? = null, below: Boolean? = null, reversed: Boolean? = null) {
        val current = store.canvas.value ?: return
        val text = current.strokes.firstOrNull { it.id == id } ?: return
        val updated = com.karapp.drawguidance.ui.drawing.TextOnPath.adjust(text, start, below, reversed) ?: return
        store.commit(current.strokes.map { if (it.id == id) updated else it })
    }

    /** Yazıyı yoldan çıkarır (düz yazı olur). */
    fun detachTextFromPath(id: String) {
        val current = store.canvas.value ?: return
        val text = current.strokes.firstOrNull { it.id == id } ?: return
        val updated = com.karapp.drawguidance.ui.drawing.TextOnPath.detach(text) ?: return
        store.commit(current.strokes.map { if (it.id == id) updated else it })
    }

    /** v23 seçili yazıların kutu eni/boyu (null: otomatik). */
    fun setTextBoxSize(width: Float?, height: Float?, setWidth: Boolean, setHeight: Boolean) =
        texts.setBoxSize(width, height, setWidth, setHeight)

    /** v23 kutu tutamağıyla boyutlandırılan yazı (sürükleme bitti). */
    fun replaceStroke(stroke: com.karapp.drawguidance.data.model.Stroke) {
        val current = store.canvas.value ?: return
        if (current.strokes.none { it.id == stroke.id }) return
        store.commit(current.strokes.map { if (it.id == stroke.id) stroke else it })
    }

    /**
     * Yazı biçimi değişti: [before] gösterilen biçim (seçili yazınınki ya da düzenlenen yazınınki; null ise yazı
     * düzenleyicisindeki biçim). v29 yalnız kullanıcının değiştirdiği alanlar bu tuvalin son ayarı olur.
     */
    fun updateTextSettings(settings: TextSettings, before: TextSettings? = null) {
        if (before != null) texts.updateSettings(settings, before) else texts.updateSettings(settings)
        val d = texts.defaults.value
        canvasPrefs.lastText = d.spec to d.sizePx
    }

    /** v29 yeni yazıların biçimi (bu tuvalde kullanıcının son seçtiği). */
    val textDefaults: StateFlow<TextSettings> get() = texts.defaults

    // --- v29 modlar arası aktarma ---

    /** Resim tuvalinden aktarılabilecek tuvaller: not alma ve çizgi roman (en son değişen önce). */
    val transferTargets: kotlinx.coroutines.flow.Flow<List<com.karapp.drawguidance.data.model.CanvasUi>> =
        transfer?.targets(setOf(com.karapp.drawguidance.data.model.CanvasKind.NOTES, com.karapp.drawguidance.data.model.CanvasKind.COMIC), canvasLocalId)
            ?: kotlinx.coroutines.flow.flowOf(emptyList())

    private val _transferring = MutableStateFlow(false)
    val transferring: StateFlow<Boolean> = _transferring.asStateFlow()

    /**
     * v29 çizimi (seçili elemanlar ya da açık sayfanın görünen çizimleri) başka tuvale aktarır: [vector] ise
     * düzenlenebilir elemanlar olarak, değilse saydam zeminli tek resim olarak.
     */
    fun transferTo(target: com.karapp.drawguidance.data.transfer.TransferTarget, vector: Boolean, selectionOnly: Boolean) {
        val t = transfer ?: return
        val c = store.canvas.value ?: return
        if (_transferring.value) return
        texts.commitOpenDraft()
        val visible = store.visibleStrokes()
        val ids = store.selection
        val items = if (selectionOnly && ids.isNotEmpty()) visible.filter { it.id in ids } else visible
        if (items.none { it.brushType != BrushType.ERASER }) {
            emit(EditEvent.Transferred(null, empty = true))
            return
        }
        _transferring.value = true
        viewModelScope.launch {
            val result = runCatching {
                if (vector) t.sendStrokes(target, c.name, items)
                else {
                    val bmp = withContext(Dispatchers.Default) { com.karapp.drawguidance.data.transfer.CanvasTransfer.render(items) }
                    bmp?.let { t.sendImage(target, c.name, it) }
                }
            }.onFailure { com.karapp.drawguidance.diagnostics.AppLog.e("Transfer", "Aktarılamadı", it, userVisible = true) }.getOrNull()
            _transferring.value = false
            emit(EditEvent.Transferred(result?.name))
        }
    }

    // --- Resimler ---

    val imageSave: StateFlow<ImageSaveState> get() = images.save
    val saveOptions: StateFlow<ExportOptions?> get() = images.saveOptions
    val imageSaveLocation: String get() = images.saveLocation
    val imageImport: StateFlow<ImageImportState?> get() = images.import
    fun openSaveDialog() = images.openSaveDialog()
    fun closeSaveDialog() { images.closeSaveDialog(); _saveTab.value = null }

    /** v42 birleşik Kaydet penceresinin sekmeleri: resim, PDF ve (çizgi romanda) CBZ. */
    enum class SaveTab { IMAGE, PDF, CBZ, SVG, PSD, DXF }

    private val _saveTab = MutableStateFlow<SaveTab?>(null)
    /** v42 açık birleşik Kaydet penceresinin sekmesi (null: kapalı). */
    val saveTab: StateFlow<SaveTab?> = _saveTab.asStateFlow()

    /** v42 bu tuval türünde pencerede bulunan sekmeler (çocuk tuvalinde yalnız resim). */
    fun saveTabs(): List<SaveTab> = when (canvas.value?.kind) {
        com.karapp.drawguidance.data.model.CanvasKind.COMIC -> listOf(SaveTab.PDF, SaveTab.CBZ, SaveTab.IMAGE)
        com.karapp.drawguidance.data.model.CanvasKind.NOTES, com.karapp.drawguidance.data.model.CanvasKind.SIMPLE_NOTES -> listOf(SaveTab.PDF, SaveTab.IMAGE)
        else -> listOf(SaveTab.IMAGE)
    } + (if (docTabsOn()) listOf(SaveTab.SVG, SaveTab.PSD, SaveTab.DXF) else emptyList())

    /** v56 aşama 9 SVG, PSD ve DXF sekmeleri (çocuk tuvalinde yok). */
    private fun docTabsOn(): Boolean = v56(com.karapp.drawguidance.data.model.V56Flags.Feature.EXPORT_SVG_PSD_DXF) &&
        canvas.value?.kind != com.karapp.drawguidance.data.model.CanvasKind.KIDS

    /**
     * v42 başlıktaki Kaydet düğmesi: kaydetme işlemlerinin hepsi tek pencerede (resim, PDF, çizgi romanda CBZ). Çizgi
     * roman ve not alma varsayılan olarak baskıya hazır CMYK PDF; resim modlarında RGB resim. Öteki birleşimler
     * pencerede seçilir.
     */
    fun openUnifiedSave() {
        val c = canvas.value ?: return
        images.openSaveDialog()
        // Resim modlarında (tek biçim) eski resim kaydetme penceresi açılır; v56 SVG/PSD/DXF varsa birleşik pencere
        if (!c.kind.paged) return
        images.openPdfDialog()
        val printReady = c.kind == com.karapp.drawguidance.data.model.CanvasKind.COMIC || c.kind == com.karapp.drawguidance.data.model.CanvasKind.NOTES ||
            c.kind == com.karapp.drawguidance.data.model.CanvasKind.SIMPLE_NOTES
        if (printReady) {
            images.adjustSaveDefaults(
                image = { o -> if (c.kind == com.karapp.drawguidance.data.model.CanvasKind.COMIC) o.copy(cmyk = o.cmyk ?: com.karapp.drawguidance.data.print.CmykOptions()) else o },
                pdf = { o -> o.copy(cmyk = o.cmyk ?: com.karapp.drawguidance.data.print.CmykOptions()) },
            )
        }
        if (c.kind == com.karapp.drawguidance.data.model.CanvasKind.COMIC) v40.openCbz()
        _saveTab.value = saveTabs().first()
    }

    fun selectSaveTab(tab: SaveTab) { if (_saveTab.value != null) _saveTab.value = tab }

    /** v56 aşama 9 resim kaydetme penceresinden SVG / PSD / DXF'e geçiş (birleşik pencere o sekmeyle açılır). */
    fun openDocTab(tab: SaveTab) { if (tab in saveTabs()) { if (images.saveOptions.value == null) images.openSaveDialog(); _saveTab.value = tab } }

    /** v42 birleşik Kaydet penceresi kapandı (ya da kaydedildi). */
    fun closeUnifiedSave() {
        _saveTab.value = null
        images.closeSaveDialog()
        images.closePdfDialog()
        v40.closeCbz()
    }
    fun imageOutputSize(scale: Float): Pair<Int, Int> = images.outputSize(scale)
    fun saveAsImage() = images.saveAsImage()
    fun saveAsImage(options: ExportOptions) = images.saveAsImage(options)
    fun dismissImageSave() = images.dismissSave()
    fun importImage(uri: Uri, center: Offset) = images.importImage(uri, center)

    /** v29 Resimlerim'deki kayıtlı resmi ekler. */
    fun importSavedImage(uri: Uri, center: Offset) = images.importSaved(uri, center)

    /** v29 Resimlerim (resim ekle → kayıtlı resimlerden seç). */
    val savedImages: kotlinx.coroutines.flow.Flow<List<com.karapp.drawguidance.data.images.SavedImageEntity>> = imagesRepository.observeImages()
    fun confirmImageImport() = images.confirmImport()
    fun dismissImageImport() = images.dismissImport()

    // --- PDF ---

    val pdfExport: StateFlow<PdfExportState> get() = images.pdf
    fun exportPdf(options: com.karapp.drawguidance.data.images.PdfOptions? = null) = images.exportPdf(options)
    val pdfOptions: StateFlow<com.karapp.drawguidance.data.images.PdfOptions?> get() = images.pdfOptions
    val pdfLocation: String get() = images.pdfLocation
    fun openPdfDialog() = images.openPdfDialog()
    fun closePdfDialog() = images.closePdfDialog()
    fun dismissPdf() = images.dismissPdf()
    /** v40 CMYK provası için tuvalin küçük resmi. */
    suspend fun cmykPreview(): android.graphics.Bitmap? = images.previewBitmap()

    // --- Kaydetme ---

    /** Otomatik kayıt açık mı (kapalıysa üst menüde Kaydet düğmesi görünür). */
    val autosave: StateFlow<Boolean> get() = if (tempSession != null) tempAutosaveOff else prefs.autosave
    /** Kaydedilmemiş değişiklik var mı (otomatik kayıt kapalıyken). */
    val dirty: StateFlow<Boolean> get() = store.dirty
    fun setAutosave(value: Boolean) { if (tempSession == null) prefs.setAutosave(value) }

    /** v20 çizim düzeltme önerileri açık mı. */
    val suggestions: StateFlow<Boolean> get() = prefs.handwriting
    fun setSuggestions(value: Boolean) {
        prefs.setHandwriting(value)
        if (!value) handwriting.dismiss()
    }

    /** v20 öneri balonundaki "Önerileri kapat". */
    fun turnOffSuggestions() {
        setSuggestions(false)
        emit(EditEvent.SuggestionsOff)
    }

    /** v23 yazı önerisi / şekil önerisi ayrı ayrı. */
    val textSuggestOn: StateFlow<Boolean> get() = prefs.textSuggest
    val shapeSuggestOn: StateFlow<Boolean> get() = prefs.shapeSuggest
    fun setTextSuggest(value: Boolean) {
        prefs.setTextSuggest(value)
        if (!value) handwriting.dismissText()
    }
    fun setShapeSuggest(value: Boolean) {
        prefs.setShapeSuggest(value)
        if (!value) handwriting.dismissShape()
    }
    /** Yazı balonundaki "Yazı önerilerini kapat". */
    fun turnOffTextSuggestions() {
        setTextSuggest(false)
        emit(EditEvent.TextSuggestionsOff)
    }
    /** Şekil balonundaki "Şekil önerilerini kapat". */
    fun turnOffShapeSuggestions() {
        setShapeSuggest(false)
        emit(EditEvent.ShapeSuggestionsOff)
    }
    /** v38 çizgi önerisi (ayrı ayar). */
    val lineSuggestOn: StateFlow<Boolean> get() = prefs.lineSuggest
    fun setLineSuggest(value: Boolean) {
        prefs.setLineSuggest(value)
        if (!value) handwriting.dismissLine()
    }
    /** Çizgi balonundaki "Çizgi önerilerini kapat" (ileti kodda: ekranın olay tablosu büyümesin). */
    fun turnOffLineSuggestions() {
        setLineSuggest(false)
        emit(EditEvent.Message(com.karapp.drawguidance.i18n.I18n.tr(LINE_SUGGEST_OFF)))
    }

    /** v28 öneri balonlarının sürüklenen yeri (çizim alanına göre oran); null: kendiliğinden. */
    var suggestPin: Pair<Float, Float>?
        get() = prefs.suggestPin
        set(value) { prefs.suggestPin = value }

    /** v27 seçimde kendiliğinden öneri (parça ve şekil). */
    val selectionSuggestOn: StateFlow<Boolean> get() = prefs.selectionSuggest
    fun setSelectionSuggest(value: Boolean) {
        prefs.setSelectionSuggest(value)
    }
    /** Parça balonundaki "Seçim önerilerini kapat". */
    fun turnOffSelectionSuggestions() {
        setSelectionSuggest(false)
        emit(EditEvent.SelectionSuggestionsOff)
    }
    fun saveNow() {
        texts.commitOpenDraft()
        // v51 geçici resim tuvalinde Kaydet resmi nota aktarır
        if (tempSession != null) { commitTempImage(); return }
        store.saveNow()
        emit(EditEvent.Saved)
    }

    // --- v23 3B şekiller; v56 aşama 10: kütüphane + GLB, açı seçimi, Açıyı değiştir ---

    val model3dBusy get() = models3d.busy

    /** Kütüphanedeki şeklin modeli (açı seçimi önizlemesi için). */
    fun catalogModel3d(catalogId: String): com.karapp.drawguidance.content.model3d.Model3D? = models3d.catalogModel(catalogId)
    /** GLB dosyasının modeli (ayrıntı düşürülmüş). */
    suspend fun glbModel3d(bytes: ByteArray, name: String, maxTriangles: Int) = models3d.glbModel(bytes, name, maxTriangles)
    /** Açı önizlemesi. */
    suspend fun previewModel3d(model: com.karapp.drawguidance.content.model3d.Model3D, yaw: Float, pitch: Float, size: Int) = models3d.preview(model, yaw, pitch, size)
    /** Modeli seçilen açıyla tuvale ekler ("buraya ekle" konumu varsa oraya). */
    fun insertModel3d(model: com.karapp.drawguidance.content.model3d.Model3D, yaw: Float, pitch: Float) = models3d.insert(model, yaw, pitch, takeAddAnchor())
    /** Seçili 3B şeklin modeli (Açıyı değiştir). */
    suspend fun selectedModel3dData() = models3d.selectedModel()
    /** Seçili 3B şeklin açısını değiştirir. */
    fun changeModel3dAngle(yaw: Float, pitch: Float) = models3d.changeAngle(yaw, pitch)

    // --- v53 ekran tonları, desenler ve hızlı yazı kutusu ---

    /** Seçimde tonlanabilir (kapalı alanlı) şekil var mı (paneller dahil). */
    fun selectionHasClosedShape(): Boolean {
        val sel = store.selection
        return store.canvas.value?.strokes?.any { it.id in sel && it.shape != null } == true
    }

    fun selectionHasTone(): Boolean {
        val sel = store.selection
        return store.canvas.value?.strokes?.any { it.id in sel && it.shape?.style?.tone != null } == true
    }

    /** Seçili şekillerin tonunu değiştirir; null tonu kaldırır. */
    fun applyToneToSelection(tone: com.karapp.drawguidance.data.model.ToneFill?) {
        val current = store.canvas.value ?: return
        val sel = store.selection
        var changed = false
        val updated = current.strokes.map { s ->
            val shape = s.shape
            if (s.id !in sel || shape == null || shape.style.tone == tone) s
            else { changed = true; s.copy(shape = shape.copy(style = shape.style.copy(tone = tone))) }
        }
        if (changed) store.commit(updated)
    }

    /** Görünen alanın ortasına tonla dolu bir alan ekler (kenarsız dikdörtgen; taşınır ve boyutlanır). */
    fun insertToneArea(tone: com.karapp.drawguidance.data.model.ToneFill, viewCenter: Offset) {
        val current = store.canvas.value ?: return
        val center = takeAddAnchor() ?: viewCenter
        val side = minOf(current.width, current.height) * 0.42f
        val id = java.util.UUID.randomUUID().toString()
        val stroke = com.karapp.drawguidance.ui.drawing.ShapeFactory.build(
            "RECTANGLE", Offset(center.x - side / 2, center.y - side * 0.35f), Offset(center.x + side / 2, center.y + side * 0.35f),
            com.karapp.drawguidance.data.model.ShapeStyle(strokeColorHex = tone.colorHex, strokeWidth = 0f, tone = tone),
            id = id, order = current.strokes.size,
        ) ?: return
        store.commit(current.strokes + stroke)
        inserted(setOf(id))
    }

    /** v53 hızlı yazı kutusu: yazıyı ekler; eklenen yazının alt kenarı döner. */
    fun insertQuickText(content: String, anchor: Offset, width: Float?): Float? = texts.insertQuick(content, anchor, width)

    /** v53 hızlı yazı kutusunun ilk yeri: son yazının altı (yoksa görünen alanın üstü). */
    fun quickTextStart(visibleTopLeft: Offset): Pair<Offset, Float>? {
        val current = store.canvas.value ?: return null
        if (!current.kind.isNotes) return null
        texts.commitOpenDraft()
        val size = texts.settings.value.sizePx
        val x = visibleTopLeft.x.coerceIn(current.width * 0.04f, current.width * 0.5f)
        val lastBottom = current.strokes.filter { it.brushType == com.karapp.drawguidance.data.model.BrushType.TEXT }.mapNotNull { s -> s.points.maxOfOrNull { it.y } }.maxOrNull()
        val y = (lastBottom?.let { it + size * 0.9f } ?: visibleTopLeft.y).coerceIn(size, current.height - size * 2)
        val width = (current.width * 0.86f - x).coerceIn(size * 4, current.width.toFloat())
        if (mode.value != EditMode.SELECT) setMode(EditMode.SELECT)
        store.select(emptySet())
        return Offset(x, y) to width
    }

    // --- v27 çizgi roman ---

    fun insertBalloon(
        style: com.karapp.drawguidance.data.model.BalloonStyle, speakers: Int,
        options: com.karapp.drawguidance.ui.screens.canvasedit.state.BalloonTextOptions = com.karapp.drawguidance.ui.screens.canvasedit.state.BalloonTextOptions(),
    ) = comic.insertBalloon(style, speakers, takeAddAnchor(), options)
    /** v36 seçili balonun yazısı (içerik, yazı tipi, boyut, renk, hizalama) ve kuyrukları; balon penceresinden ses yazısı. */
    fun updateBalloonText(
        content: String? = null, font: com.karapp.drawguidance.data.model.TextFont? = null, sizePx: Float? = null,
        colorHex: String? = null, align: com.karapp.drawguidance.data.model.TextAlignKind? = null,
    ) = comic.updateBalloonText(content, font, sizePx, colorHex, align)
    fun setBalloonTails(dir: com.karapp.drawguidance.ui.drawing.TailDirection, length: Float, curve: Float) = comic.setBalloonTails(dir, length, curve)
    fun insertSfxText(options: com.karapp.drawguidance.ui.screens.canvasedit.state.BalloonTextOptions) = comic.insertSfxText(options, takeAddAnchor())
    /** v36 Karakterler penceresinden karakter. */
    /**
     * v40 hazır karakter çeşidini resim elemanı olarak ekler: ifadeler (büst) sayfanın kısa kenarının %32'si, tam boy
     * çeşitler %55'i ([com.karapp.drawguidance.ui.screens.canvasedit.state.ComicController.CHARACTER_HEIGHT]) boyunda.
     */
    fun insertCharacter(variant: com.karapp.drawguidance.data.comicassets.ComicVariant) {
        val current = canvas.value ?: return
        val fraction = if (variant.group == com.karapp.drawguidance.data.comicassets.VariantGroup.EXPRESSION.id) 0.32f
        else com.karapp.drawguidance.ui.screens.canvasedit.state.ComicController.CHARACTER_HEIGHT
        val center = takeAddAnchor() ?: androidx.compose.ui.geometry.Offset(current.width / 2f, current.height / 2f)
        images.insertAssetImage(variant.file, fraction, center)
    }
    /**
     * v42 hazır nesne ya da efekt resmi: uzun kenarı sayfanın kısa kenarının nesnede %26'sı, efektte %34'ü olacak
     * şekilde, "buraya ekle" noktasına ya da sayfanın ortasına eklenir (resim elemanı: taşınır, döndürülür, kırpılır).
     */
    fun insertComicProp(kind: com.karapp.drawguidance.data.comicassets.ComicAssetKind, prop: com.karapp.drawguidance.data.comicassets.ComicProp) {
        val current = canvas.value ?: return
        val longSide = if (kind == com.karapp.drawguidance.data.comicassets.ComicAssetKind.EFFECT) 0.34f else 0.26f
        val fraction = longSide * prop.h / maxOf(prop.w, prop.h).coerceAtLeast(1)
        val center = takeAddAnchor() ?: androidx.compose.ui.geometry.Offset(current.width / 2f, current.height / 2f)
        images.insertAssetImage(prop.file, fraction, center)
    }

    /** v43 inmiş paketten resim (şekil, diyalog kutusu, diyagram): uzun kenarı sayfanın kısa kenarının ~%30'u. */
    fun insertPackImage(file: java.io.File, w: Int, h: Int) {
        val current = canvas.value ?: return
        val fraction = 0.30f * h.coerceAtLeast(1) / maxOf(w, h).coerceAtLeast(1)
        val center = takeAddAnchor() ?: androidx.compose.ui.geometry.Offset(current.width / 2f, current.height / 2f)
        images.insertAssetImage(file.absolutePath, fraction, center)
    }

    /** v41 sayfadaki şablonun panelleri: panel kimliği → sınırı (şablon yoksa boş). */
    fun comicPanelRects(): Map<Int, androidx.compose.ui.geometry.Rect> =
        comic.panelSet()?.strokes?.mapValues { (_, s) -> com.karapp.drawguidance.ui.screens.canvasedit.state.Backdrop.bounds(s) }.orEmpty()

    /** v41 [p] noktasındaki panel (yoksa null). */
    fun panelAt(p: androidx.compose.ui.geometry.Offset): Int? = comicPanelRects().entries.firstOrNull { it.value.contains(p) }?.key

    /**
     * v41 Mekân arka planı ekle: hazır arka plan [leaf] paneline sığar (panelin oranında ortadan kırpılır); [leaf] null
     * ya da panel yoksa bütün sayfayı kaplar. Bütün elemanların arkasına konur.
     * v49: panel dörtgen (düz ya da eğik) ise arka plan tam panelin dış hattına kırpılır (panelin dışına taşmaz).
     */
    fun insertLocationBackground(b: com.karapp.drawguidance.data.comicassets.ComicBackground, leaf: Int?, onDone: (String?) -> Unit = {}) {
        val c = canvas.value ?: return
        val panel = leaf?.let { comic.panelSet()?.strokes?.get(it) }
        val rect = panel?.let { com.karapp.drawguidance.ui.screens.canvasedit.state.Backdrop.bounds(it) }
            ?: androidx.compose.ui.geometry.Rect(0f, 0f, c.width.toFloat(), c.height.toFloat())
        images.insertBackdrop(b.file, rect, panel?.let { com.karapp.drawguidance.ui.screens.canvasedit.state.Backdrop.panelClip(it) }, onDone)
    }

    /**
     * v41 pencerede arka plan seçildi: "buraya ekle" ile bir panele dokunulduysa o panele, sayfada şablon yoksa bütün
     * sayfaya hemen konur (true); şablon varsa yerleştirme katmanı açılmalıdır (false).
     */
    fun placeLocationBackgroundDirectly(b: com.karapp.drawguidance.data.comicassets.ComicBackground): Boolean {
        val anchor = takeAddAnchor()
        val panels = comicPanelRects()
        val leaf = anchor?.let { a -> panels.entries.firstOrNull { it.value.contains(a) }?.key }
        if (leaf != null || panels.isEmpty()) { insertLocationBackground(b, leaf); return true }
        return false
    }

    fun selectedBalloon(): Stroke? = comic.selectedBalloon()
    fun updateBalloon(style: com.karapp.drawguidance.data.model.BalloonStyle? = null, speakers: Int? = null, fillHex: String? = null, strokeHex: String? = null) =
        comic.updateBalloon(style, speakers, fillHex, strokeHex)
    fun balloonWithTail(stroke: Stroke, index: Int, tail: com.karapp.drawguidance.data.model.BalloonTail): Stroke? = comic.withTail(stroke, index, tail)
    /** v36 Şablonlar: şablon ekleme ve sayfadaki şablonun panellerini düzenleme (boyut, sıra, aralık, ekleme, silme). */
    fun insertTemplate(templateId: String) = comic.insertTemplate(templateId)
    fun editPanels(coalesce: String? = null, edit: (com.karapp.drawguidance.content.comic.PanelPage) -> com.karapp.drawguidance.content.comic.PanelPage?) =
        comic.editPanels(coalesce, edit)
    /** Panelin yanına/altına ([rows]) ya da sayfanın sonuna ([leaf] null) yeni panel; yeni panelin kimliği. */
    fun addPanel(leaf: Int?, rows: Boolean): Int? {
        var added: Int? = null
        comic.editPanels { page ->
            val r = if (leaf == null) page.append() else page.split(leaf, rows)
            r?.let { added = it.second; it.first }
        }
        return added
    }
    fun insertSfx(preset: com.karapp.drawguidance.content.comic.SfxPreset) = comic.insertSfx(preset, takeAddAnchor())
    fun insertComicLines(kind: com.karapp.drawguidance.content.comic.ComicLinesKind, density: Float = 1f) = comic.insertLines(kind, density)
    /** v29 seçili hız/odak çizgisi grubunun sıklığı (değilse null) ve değiştirilmesi. */
    fun selectedLinesDensity(): Float? = comic.selectedLinesDensity()
    fun setLinesDensity(density: Float) = comic.setLinesDensity(density)
    /** v36 odak çizgileri: odak noktası, yön, uzunluk, kalınlık, renk ve opaklıkla ekleme; seçili grubun ayarları. */
    fun insertComicLines(kind: com.karapp.drawguidance.content.comic.ComicLinesKind, options: com.karapp.drawguidance.ui.screens.canvasedit.state.LinesOptions) =
        comic.insertLines(kind, options)
    fun selectedLinesOptions() = comic.selectedLinesOptions()
    fun setLinesOptions(options: com.karapp.drawguidance.ui.screens.canvasedit.state.LinesOptions) = comic.setLinesOptions(options)
    /** v36 efektler: "buraya ekle" yerine (yoksa sayfanın ortasına) ekleme; seçili efektin boyutu, açısı, rengi, opaklığı. */
    fun insertEffect(def: com.karapp.drawguidance.content.comic.EffectDef, options: com.karapp.drawguidance.ui.screens.canvasedit.state.EffectOptions) =
        comic.insertEffect(def, takeAddAnchor(), options)
    fun selectedEffect() = comic.selectedEffect()
    fun editEffect(scale: Float = 1f, rotate: Float = 0f, colorHex: String? = null, opacity: Float? = null) =
        comic.editEffect(scale, rotate, colorHex, opacity)

    /** Seçili tek 3B şekil. */
    fun selectedModel3d(): com.karapp.drawguidance.data.model.Stroke? = models3d.selected()

    /** Tuvali hemen kaydeder (3B düzenleyiciye geçmeden önce "Kaydet ve aç"). */
    fun saveBeforeLeaving() {
        texts.commitOpenDraft()
        store.saveNow()
    }

    // --- Ekle menüsü ---

    /** Ekle (+) menüsünde son seçilen öğe (menüde etkin gösterilir). */
    private val _lastAddItem = MutableStateFlow(canvasPrefs.lastAddItem)
    val lastAddItem: StateFlow<String?> = _lastAddItem.asStateFlow()
    fun rememberAddItem(id: String) {
        _lastAddItem.value = id
        canvasPrefs.lastAddItem = id
    }

    // --- v17: Ekle modu ("buraya ekle"), Düzenle modu, bağlam menüsü, çizim düzenleme ---

    /** "Buraya ekle" ile seçilen öğenin konacağı yer (seçiciden öğe seçilince kullanılır). */
    private var addAnchor: Offset? = null

    /** Bekleyen "buraya ekle" konumunu alır (bir kez). */
    fun takeAddAnchor(): Offset? = addAnchor.also { addAnchor = null }

    /**
     * Ekle modunda ya da bağlam menüsünde seçilen öğe dokunulan yere eklenir: yazı hemen orada yazılmaya başlar; şekil,
     * formül ve diyagramda seçici açılır, seçilen öğe o noktaya konur. Resim için konum [takeAddAnchor] ile alınır.
     */
    fun addHere(itemId: String, at: Offset) {
        rememberAddItem(itemId)
        addAnchor = null
        when (itemId) {
            "text" -> {
                startAction(EditMode.TEXT)
                texts.beginAt(at.x, at.y, 0f)
            }
            "shape" -> { addAnchor = at; shapes.openPicker() }
            "formula" -> { addAnchor = at; formulas.openPicker() }
            "diagram" -> { addAnchor = at; diagrams.openPicker() }
            "image" -> addAnchor = at
            "model3d" -> addAnchor = at
            // v27 çizgi roman: balon ve efekt yazısı dokunulan yere (v36 efektler penceresi de)
            "balloon", "sfx", "effects", "characters", "locations", "objects", "packs" -> addAnchor = at
            "pen" -> pen.start()
        }
    }

    /**
     * Düzenle modunda dokunulan elemanın düzenleyicisi açılır: şekil → şekil düzenleme, diyagram → tuval üstü diyagram
     * düzenleme, resim → resim düzenleme, yazı → yazı yerinde düzenleme, formül → formül üstünde düzenleme; fırça
     * çizimi seçilir (alt çubukta çizim düzenleme: renk, kalınlık, yumuşatma). Eleman yoksa false.
     */
    fun editAt(x: Float, y: Float, tolerance: Float): Boolean {
        val index = store.currentIndex()
        val element = ElementOps.hitTest(index, Offset(x, y), tolerance, store.lockedIds()) ?: return false
        store.select(index.expand(element.allIds))
        // v43 bekleyen nesne aracı (uyumla / mekâna yerleştir): dokunulan resimde açılır
        _pendingSceneTool.value?.let { tool ->
            if (element.brushType == BrushType.IMAGE) {
                _pendingSceneTool.value = null
                if (tool == SCENE_PLACE) openPlace() else openHarmony()
                return true
            }
        }
        when (kindOf(element.brushType)) {
            SelectionKind.SHAPE -> {
                val pending = _pendingShapeTool.value
                openShapeWith(pending)
                // v39 bekleyen Alan boya: şekil seçilip açıldıktan sonra aynı dokunuşla dokunulan alan boyanır
                if (pending == ShapeTool.FILL && _mode.value == EditMode.SHAPE_EDIT) shapes.paintSelectedAt(Offset(x, y))
            }
            SelectionKind.DIAGRAM -> diagrams.editSelected()
            SelectionKind.IMAGE -> imageEdit.editSelected()
            SelectionKind.TEXT -> {
                setMode(EditMode.SELECT)
                texts.editSelected()
            }
            SelectionKind.FORMULA -> {
                setMode(EditMode.SELECT)
                formulas.edit(null)
            }
            else -> setMode(EditMode.SELECT)
        }
        return true
    }

    /**
     * v21 Düzenle üst menüsündeki "Vektör düzenle": seçili elemanın düzenleyicisini (şekilde vektör noktaları) dokunmadan
     * açar. Fırça izinin vektör düzenleyicisi yok (alt çubukta kendi düzenlemeleri var).
     */
    fun editSelection(): Boolean {
        when (selectionKind()) {
            SelectionKind.SHAPE -> shapes.editSelected()
            SelectionKind.DIAGRAM -> diagrams.editSelected()
            SelectionKind.IMAGE -> imageEdit.editSelected()
            SelectionKind.TEXT -> {
                setMode(EditMode.SELECT)
                texts.editSelected()
            }
            SelectionKind.FORMULA -> {
                setMode(EditMode.SELECT)
                formulas.edit(null)
            }
            else -> return false
        }
        return true
    }

    // --- v34 Düzenle üst menüsünde Nokta düzenle / Çizgi düzenle / Alan boya ---

    private val _pendingShapeTool = MutableStateFlow<ShapeTool?>(null)
    /** Düzenle'de seçilen şekil aracı: dokunulan şekil doğrudan bu araçla açılır. */
    val pendingShapeTool: StateFlow<ShapeTool?> = _pendingShapeTool.asStateFlow()

    /**
     * v34 Düzenle menüsündeki şekil araçları: seçili şekil varsa şekil düzenleme doğrudan [tool] aracıyla açılır;
     * yoksa araç bekletilir (düğme seçili görünür) ve dokunulan şekil bu araçla açılır. Aynı düğme bekletmeyi kaldırır.
     */
    fun editShapeWith(tool: ShapeTool) {
        if (selectionKind() == SelectionKind.SHAPE) {
            openShapeWith(tool)
            return
        }
        _pendingShapeTool.value = if (_pendingShapeTool.value == tool) null else tool
        if (_mode.value != EditMode.EDIT_PICK) setMode(EditMode.EDIT_PICK)
    }

    private fun openShapeWith(tool: ShapeTool?) {
        shapes.editSelected()
        if (tool != null && _mode.value == EditMode.SHAPE_EDIT) shapes.selectTool(tool)
        _pendingShapeTool.value = null
    }

    /** Seçimin düzenleyicisi var mı (tek türden şekil, diyagram, resim, yazı ya da formül). */
    fun canEditSelection(ids: Set<String> = store.selection): Boolean = selectionKind(ids) in EDITABLE_KINDS

    private fun kindOf(type: BrushType): SelectionKind = when (type) {
        BrushType.TEXT -> SelectionKind.TEXT
        BrushType.SHAPE -> SelectionKind.SHAPE
        BrushType.FORMULA -> SelectionKind.FORMULA
        BrushType.IMAGE -> SelectionKind.IMAGE
        BrushType.DIAGRAM -> SelectionKind.DIAGRAM
        BrushType.ERASER -> SelectionKind.NONE
        else -> SelectionKind.INK
    }

    /** Seçimin türü (bağlam menüsü ve alt çubuk seçeneklerini buna göre kurar). */
    fun selectionKind(ids: Set<String> = store.selection): SelectionKind {
        if (ids.isEmpty()) return SelectionKind.NONE
        val kinds = store.strokes.asSequence().filter { it.id in ids }.map { kindOf(it.brushType) }.filter { it != SelectionKind.NONE }.toSet()
        return when (kinds.size) {
            0 -> SelectionKind.NONE
            1 -> kinds.first()
            else -> SelectionKind.MIXED
        }
    }

    /** Seçili fırça çizimlerinin (mürekkep) rengi. */
    fun recolorSelectedInk(colorHex: String) = inkEdit("ink-color") { it.copy(colorHex = colorHex) }

    /** Seçili fırça çizimlerini kalınlaştırır/inceltir ([factor] > 1 kalın). */
    fun scaleSelectedInkWidth(factor: Float) = inkEdit(null) { it.copy(sizePx = (it.sizePx * factor).coerceIn(0.5f, com.karapp.drawguidance.data.settings.BrushTuning.MAX_SIZE)) }

    /** Seçili fırça çizimlerini yumuşatır (titreşimler düzleşir; uç noktalar yerinde kalır). */
    fun smoothSelectedInk() = inkEdit(null) { s ->
        if (s.points.size < 4) s else s.copy(points = com.karapp.drawguidance.ui.screens.canvasedit.editor.InkSmoothing.smooth(s.points))
    }

    /** Seçili fırça çizimlerinin (ya da [text] ise yazının) ilk rengi (renk penceresinin başlangıcı). */
    fun selectedInkColor(text: Boolean = false): String {
        val want = if (text) SelectionKind.TEXT else SelectionKind.INK
        return store.strokes.firstOrNull { it.id in store.selection && kindOf(it.brushType) == want }?.colorHex ?: brush.settings.value.colorHex
    }

    // --- v21 3B çizim modu (v56 aşama 10: kılavuz kaldırıldı; eski tuvallerde açılışta kapanır) ---

    /** 3B çizim modu açıksa düzlem ve derinlik (çizim aracı ve yeni izler için). */
    fun draw3dFor(): com.karapp.drawguidance.data.model.Depth3D? = store.canvas.value?.guide?.draw3dSpec

    // --- v22 döndürülebilir 3B sahne ---

    /** 3B çizim modu açıksa sahnenin kamerası (çizim aracı düzleme bu görünümden yatırır). */
    fun scene3dFor(): com.karapp.drawguidance.ui.drawing.SceneCamera? {
        val c = store.canvas.value ?: return null
        if (!c.guide.draw3d) return null
        return com.karapp.drawguidance.ui.drawing.Scene3D.camera(c.guide, c.width, c.height)
    }

    /** v21: seçili fırça izleri için düzeltme önerisi verilebilir mi (fırçanın görünümünü bozmayacaksa). */
    /** v22 seçimdeki fırça izi sayısı (çoklu düzeltme önerisi için). */
    fun selectedInkCount(ids: Set<String> = store.selection): Int =
        store.strokes.count { it.id in ids && kindOf(it.brushType) == SelectionKind.INK }

    fun canSuggestForSelection(ids: Set<String> = store.selection): Boolean =
        aiOn() && handwriting.canSuggest(store.strokes.filter { it.id in ids })

    /** v17: seçili fırça çizimi için düzeltme önerisi (şekil ya da el yazısı; önizlemeyle, onaylanırsa uygulanır). */
    fun suggestInkCorrection() {
        if (!aiOn()) { emit(EditEvent.AiOff); return }
        handwriting.suggestForSelection { found -> if (!found) emit(EditEvent.NoCorrection) }
    }

    /** v30 açık tuvalin yapay zekâ ayarı (⋮ menüsünden açılıp kapatılır, tuvalle birlikte saklanır). */
    val aiEnabled: StateFlow<Boolean> = store.canvas.map { it?.aiEnabled == true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setAiEnabled(on: Boolean) {
        if (store.canvas.value == null || aiOn() == on) return
        store.updateCanvas { it.copy(aiEnabled = on) }
        if (!on) handwriting.reset()
        viewModelScope.launch { runCatching { repository.setAiEnabled(canvasLocalId, on) } }
        emit(if (on) EditEvent.AiOn else EditEvent.AiOff)
    }

    private fun inkEdit(coalesce: String?, block: (Stroke) -> Stroke) {
        val current = store.canvas.value ?: return
        val ids = store.selection
        if (ids.isEmpty()) return
        var changed = false
        val updated = current.strokes.map { s ->
            if (s.id in ids && kindOf(s.brushType) == SelectionKind.INK) block(s).also { if (it != s) changed = true } else s
        }
        if (changed) store.commit(updated, coalesce = coalesce?.let { "$it-${ids.hashCode()}" })
    }

    // --- Formüller ---

    val isFormulaPickerOpen: StateFlow<Boolean> get() = formulas.isPickerOpen
    val formulaTemplate: StateFlow<FormulaTemplate?> get() = formulas.template
    val selectedFormula: StateFlow<Stroke?> get() = formulas.selected
    val formulaEditing: StateFlow<String?> get() = formulas.editing
    /** v45 formülün sayılarla hesabındaki hata (sıfıra bölme, tanım dışı). */
    val formulaEvalError: StateFlow<com.karapp.drawguidance.content.math.MathError?> get() = formulas.evalError
    val formulaSlot: StateFlow<String?> get() = formulas.activeSlot
    fun openFormulaPicker() = formulas.openPicker()
    fun closeFormulaPicker() { addAnchor = null; formulas.closePicker() }
    fun selectFormula(template: FormulaTemplate) {
        formulas.selectTemplate(template)
        takeAddAnchor()?.let { formulas.addAt(java.util.UUID.randomUUID().toString(), it) }
    }
    fun addFormula(gestureId: String, start: Offset, end: Offset) = formulas.add(gestureId, start, end)
    fun addFormulaAt(gestureId: String, center: Offset) = formulas.addAt(gestureId, center)
    fun editFormula(slot: String? = null) = formulas.edit(slot)
    /** Formülün tamamını formülün altında açılan alanda yazmaya geçer. */
    fun editFormulaSource() = formulas.editSource()
    fun nextFormulaSlot() = formulas.nextSlot()
    fun closeFormulaEditor() = formulas.closeEditor()
    fun focusFormulaSlot(name: String?) = formulas.focusSlot(name)
    fun tapFormulaSlot(point: Offset): Boolean = formulas.tapSlot(point)
    fun setFormulaSlot(name: String, value: String) = formulas.setSlot(name, value)
    fun setFormulaSource(source: String) = formulas.setSource(source)

    // --- Kalem aracı ---

    val penAnchors: StateFlow<List<PenAnchor>> get() = pen.anchors
    val penPending: StateFlow<PenAnchor?> get() = pen.pending
    /** v51 not almada kalem aracı (yol) yok. */
    fun startPen() { if (canvas.value?.kind?.isNotes == true) return; pen.start() }
    fun penTap(point: Offset, tolerance: Float) = pen.tap(point, tolerance)
    fun penDrag(start: Offset, current: Offset, done: Boolean) = pen.drag(start, current, done)
    fun penUndo() = pen.undoAnchor()
    fun penFinish(closed: Boolean) = pen.finish(closed)
    fun penCancel() = pen.cancelPath()
    /** v44 kalem yolunda iki nokta arasındaki çizgiyi elle eğme. */
    fun penGrab(point: Offset, tolerance: Float): Boolean = pen.grab(point, tolerance)
    fun penEditDrag(point: Offset, done: Boolean) = pen.editDrag(point, done)
    fun penSample(anchors: List<PenAnchor>, closed: Boolean): List<Offset> = pen.sample(anchors, closed)

    // --- Katmanlar ---

    val layers: StateFlow<List<Layer>> = store.canvas.map { it?.layers ?: listOf(Layer.Default) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, listOf(Layer.Default))
    val activeLayer: StateFlow<String> get() = store.activeLayer
    fun setActiveLayer(id: String) = layerOps.setActive(id)
    fun addLayer() = layerOps.add()
    fun removeLayer(id: String) = layerOps.remove(id)
    fun renameLayer(id: String, name: String) = layerOps.rename(id, name)
    fun toggleLayerVisible(id: String) = layerOps.toggleVisible(id)
    fun toggleLayerLocked(id: String) = layerOps.toggleLocked(id)
    fun setLayerOpacity(id: String, opacity: Float) = layerOps.setOpacity(id, opacity)
    fun setLayerBlend(id: String, blend: com.karapp.drawguidance.data.model.LayerBlend) = layerOps.setBlend(id, blend)
    fun toggleLayerAlphaLock(id: String) = layerOps.toggleAlphaLock(id)
    fun toggleLayerClip(id: String) = layerOps.toggleClip(id)
    fun moveLayer(id: String, direction: Int) = layerOps.move(id, direction)
    fun moveSelectionToLayer(id: String) = layerOps.moveSelectionTo(id)

    // --- v56 aşama 7: katman çoğalt/birleştir, renk ayarları, hazır ayarlar ---

    fun duplicateLayer(id: String): String? = layerOps.duplicate(id)

    /** Birleştirmede görünüm için resme çevirmek gerekir mi (kullanıcıya bir kez sorulur). */
    fun layersNeedRaster(ids: Set<String>): Boolean =
        com.karapp.drawguidance.data.model.LayerOps.needsRaster(layers.value, ids)

    private val _merging = MutableStateFlow(false)
    val merging: StateFlow<Boolean> = _merging.asStateFlow()

    /**
     * Seçili katmanları birleştirir: hepsi Normal ve opaklık 1 ise vektör olarak; değilse görünüm korunsun diye her
     * sayfada seçili katmanlar tek resme çevrilir. Tek geri alma adımı.
     */
    fun mergeLayers(ids: Set<String>) {
        val current = layers.value
        if (current.count { it.id in ids } < 2 || _merging.value) return
        if (!layersNeedRaster(ids)) { layerOps.mergeVector(ids); return }
        val canvas = store.canvas.value ?: return
        _merging.value = true
        viewModelScope.launch {
            try {
                val target = current.last { it.id in ids }
                val imgs = canvas.pages.map { page ->
                    val part = page.filter { com.karapp.drawguidance.data.model.Layers.of(it, current) in ids }
                    if (part.isEmpty()) null else images.renderToImage(part, current, canvas.width, canvas.height, target.id)
                }
                layerOps.mergeRaster(ids, imgs)
            } finally { _merging.value = false }
        }
    }

    /** Aşağıyla birleştir (en alttaki katmanda etkisiz). */
    fun mergeLayerDown(id: String) {
        val below = com.karapp.drawguidance.data.model.LayerOps.below(layers.value, id) ?: return
        mergeLayers(setOf(id, below.id))
    }

    private var colorBase: List<Stroke>? = null

    /** Renk ayarının hedefi: [layerId] verilirse o katmanın (geçerli sayfadaki) elemanları, yoksa seçim. */
    private fun colorTargets(base: List<Stroke>, layerId: String?): Set<String> {
        if (layerId == null) return store.selection
        val current = layers.value
        if (current.firstOrNull { it.id == layerId }?.locked == true) return emptySet()
        return base.filter { com.karapp.drawguidance.data.model.Layers.of(it, current) == layerId }.map { it.id }.toSet()
    }

    /** Renk ayarı önizlemesi (vektör renkleri canlı; resim pikselleri Uygula'da işlenir). */
    fun previewColorAdjust(adj: com.karapp.drawguidance.data.model.ColorAdjust, layerId: String?) {
        val base = colorBase ?: store.canvas.value?.strokes?.also { colorBase = it } ?: return
        store.previewStrokes(com.karapp.drawguidance.data.model.ColorAdjustOps.mapStrokes(base, colorTargets(base, layerId), adj))
    }

    fun cancelColorAdjust() { if (colorBase != null) { store.endPreview(); colorBase = null } }

    private val _colorApplying = MutableStateFlow(false)
    val colorApplying: StateFlow<Boolean> = _colorApplying.asStateFlow()

    /** Renk ayarını uygula: vektör renkleri değişir, resimler piksel olarak işlenir; tek geri alma adımı. */
    fun applyColorAdjust(adj: com.karapp.drawguidance.data.model.ColorAdjust, layerId: String?) {
        val base = colorBase ?: store.canvas.value?.strokes ?: return
        store.endPreview(); colorBase = null
        if (adj.isIdentity) return
        val ids = colorTargets(base, layerId)
        if (ids.isEmpty()) return
        val mapped = com.karapp.drawguidance.data.model.ColorAdjustOps.mapStrokes(base, ids, adj)
        val imageIds = base.filter { it.id in ids && it.brushType == BrushType.IMAGE && it.assetId != null }
        if (imageIds.isEmpty()) { if (mapped != base) store.commit(mapped, useArea = false); return }
        _colorApplying.value = true
        viewModelScope.launch {
            try {
                val mapper = com.karapp.drawguidance.data.model.ColorAdjustOps.Mapper(adj)
                val newAssets = HashMap<String, String>()
                for (img in imageIds) {
                    val old = img.assetId ?: continue
                    if (old in newAssets) continue
                    images.transformAsset(old) { px -> synchronized(mapper) { mapper.mapPixels(px) } }?.let { newAssets[old] = it }
                }
                val out = mapped.map { s -> if (s.id in ids && s.brushType == BrushType.IMAGE) s.assetId?.let { a -> newAssets[a] }?.let { s.copy(assetId = it) } ?: s else s }
                if (out != base) store.commit(out, useArea = false)
            } finally { _colorApplying.value = false }
        }
    }

    /** Ekle › Işık katmanı / Gölge katmanı: yeni katman (mod, opaklık, ad) ve uygun yumuşak fırça. */
    fun addPresetLayer(kind: com.karapp.drawguidance.data.model.V56Presets.Kind): String {
        val p = com.karapp.drawguidance.data.model.V56Presets.layer(kind)
        val id = java.util.UUID.randomUUID().toString()
        val name = com.karapp.drawguidance.i18n.I18n.tr(if (kind == com.karapp.drawguidance.data.model.V56Presets.Kind.LIGHT) "Işık katmanı" else "Gölge katmanı")
        store.commitLayers(layers.value + Layer(id, name, blend = p.blend, opacity = p.opacity))
        store.activeLayer.value = id
        usePresetBrush(p)
        return id
    }

    /** Ekle › Parıltı fırçası. */
    fun useGlitterBrush() = usePresetBrush(com.karapp.drawguidance.data.model.V56Presets.glitter)

    private fun usePresetBrush(p: com.karapp.drawguidance.data.model.V56Presets.LayerPreset) {
        brush.select(p.brush)
        brush.updateColor(p.colorHex)
        brush.updateSize(p.size)
        brush.updateOpacity(p.brushOpacity)
        setMode(EditMode.DRAW)
    }

    // --- Resim düzenleme ---

    val selectedImage: StateFlow<Stroke?> get() = imageEdit.selected
    val tracing: StateFlow<Boolean> get() = imageEdit.tracing
    fun editSelectedImage() = imageEdit.editSelected()
    fun cropSelectedImage() = imageEdit.crop()
    /** v27 seçili resme çizgi roman filtresi. */
    fun filterSelectedImage(kind: com.karapp.drawguidance.content.comic.ComicFilter) = images.filterSelected(kind)
    val imageFiltering: StateFlow<Boolean> get() = images.filtering
    /** v30 resim efektleri ve arka plan kaldırma (tüm tuval türlerinde, resim düzenlenirken). */
    fun applyImageEffects(look: com.karapp.drawguidance.content.image.ImageLook, adjust: com.karapp.drawguidance.content.image.ImageAdjust) =
        images.effectsSelected(look, adjust)
    fun removeImageBackground(options: com.karapp.drawguidance.content.image.BackgroundOptions) = images.removeBackgroundSelected(options)
    suspend fun selectedImagePreview(maxSide: Int): android.graphics.Bitmap? = images.selectedPreviewSource(maxSide)
    val vectorizeEmpty: StateFlow<Boolean> get() = imageEdit.empty
    fun vectorizeSelectedImage(options: com.karapp.drawguidance.content.trace.EdgeOptions, lineWidth: Float, keepImage: Boolean) =
        imageEdit.vectorize(options, lineWidth, keepImage)
    fun cancelTrace() = imageEdit.cancelTrace()

    // --- v32 eğitim modu ---

    private var tutorialOrNull: com.karapp.drawguidance.ui.screens.canvasedit.tutorial.TutorialController? = null

    /** Eğitimin düzenleyiciden istedikleri (kayıt dışı "kum havuzunda"). */
    internal val tutorialHost = object : com.karapp.drawguidance.ui.screens.canvasedit.tutorial.TutorialHost {
        override val scope: CoroutineScope get() = viewModelScope
        override val shapeSuggestion get() = handwriting.shape
        override val textSuggestion get() = handwriting.text
        override val lineSuggestion get() = handwriting.line
        override fun canvas(): CanvasUi? = store.canvas.value
        override fun commitStroke(stroke: Stroke, suggest: Boolean) = drawing.commitDemo(stroke, suggest)
        override fun commitElements(strokes: List<Stroke>) {
            val current = store.canvas.value ?: return
            if (strokes.isEmpty()) return
            var order = current.strokes.size
            store.commit(current.strokes + strokes.map { it.copy(order = order++) })
        }
        override fun mutate(block: (List<Stroke>) -> List<Stroke>) {
            val current = store.canvas.value ?: return
            store.commit(block(current.strokes).mapIndexed { i, st -> if (st.order == i) st else st.copy(order = i) })
        }
        override fun setMode(mode: EditMode) {
            when {
                mode == _mode.value -> Unit
                mode.kind == ModeKind.BASE -> this@CanvasEditViewModel.setMode(mode)
                else -> this@CanvasEditViewModel.startAction(mode)
            }
        }
        override fun select(ids: Set<String>) = store.select(ids)
        override fun moveSelected(dx: Float, dy: Float) = this@CanvasEditViewModel.moveSelected(dx, dy)
        override fun scaleSelected(factor: Float) = this@CanvasEditViewModel.scaleSelected(factor, factor)
        override fun undo() = this@CanvasEditViewModel.undo()
        override fun redo() = this@CanvasEditViewModel.redo()
        override fun setPaperGradient(gradient: com.karapp.drawguidance.data.model.GradientSpec?) {
            // Kaydedilmez: eğitim bitince kâğıt eski haline döner
            store.updateCanvas { c -> c.copy(background = c.background.copy(gradient = gradient)) }
        }
        override fun balloon(style: com.karapp.drawguidance.data.model.BalloonStyle, speakers: Int, center: Offset, text: String) = comic.balloonStroke(style, speakers, center, text)
        override fun sfx(preset: com.karapp.drawguidance.content.comic.SfxPreset, center: Offset, size: Float) = comic.sfxStroke(preset, center, size)
        override fun comicLines(kind: com.karapp.drawguidance.content.comic.ComicLinesKind, density: Float, area: Rect, ink: String) = comic.linesStrokes(kind, density, area, ink)
        override fun acceptTemplate(position: Int) = handwriting.acceptTemplate(position)
        override fun acceptLines() = handwriting.acceptLines()
        override fun acceptText(text: String) = handwriting.accept(text)
        override fun dismissSuggestions() = handwriting.reset()
        override fun fit(stroke: Stroke) = com.karapp.drawguidance.ui.screens.canvasedit.state.LineSuggester().fit(stroke)
        override fun closePanel() {
            // Açık yazı taslağı eğitimden önce belgeye yazılır (eğitim boş sayfada başlar)
            texts.commitOpenDraft()
            this@CanvasEditViewModel.closePanel()
        }
        override fun finished(group: com.karapp.drawguidance.ui.screens.canvasedit.tutorial.TutorialGroup) = emit(EditEvent.TutorialFinished(group.title))
        // v37 çocuk eğitimi: kova imleci ve boyama (maskotun seçtiği renkle; kullanıcının boya rengi değişmez)
        override fun paintCursor(point: Offset?, hold: Int, color: String?) = kidsPaint.hover(point, hold, color)
        override fun paint(point: Offset, color: String, whole: Boolean): Boolean {
            val before = shapes.regionColor.value
            shapes.setRegionColor(color)
            val ok = if (whole) shapes.paintWhole(point, emptySet()) else shapes.paintAt(point, emptySet())
            shapes.setRegionColor(before)
            kidsPaint.refresh()
            return ok
        }
        // v40 cetvel ve kalıp, sayfa yazıları (kaydedilmez; eğitim bitince tuval eski haline döner)
        override val guides get() = ruler
        override fun setPageSettings(settings: com.karapp.drawguidance.data.model.PageSettings) = store.updateCanvas { it.copy(pageSettings = settings) }
        override suspend fun assetImage(path: String, target: Rect, fill: Boolean) = images.assetImageStroke(path, target, fill)
        // v50 "Resimden çizim" eğitimi: resmi düzenlemeye açma ve vektörleştirme (bitene kadar beklenir)
        override fun editImage(id: String) {
            store.select(setOf(id))
            startAction(EditMode.IMAGE_EDIT, setOf(id))
        }
        override suspend fun vectorizeImage(id: String, mode: com.karapp.drawguidance.content.trace.EdgeMode, lineWidth: Float, keepImage: Boolean): Stroke? {
            val image = store.canvas.value?.strokes?.firstOrNull { it.id == id && it.brushType == BrushType.IMAGE } ?: return null
            store.select(setOf(id))
            // Seçim akışı ve resmin önbelleğe yüklenmesi beklenir (resim tuvalde çizilince önbelleğe girer)
            kotlinx.coroutines.withTimeoutOrNull(8_000) {
                while (imageEdit.selected.value?.id != id || com.karapp.drawguidance.data.images.CanvasImageCache.get(image.assetId) == null) {
                    kotlinx.coroutines.delay(50)
                }
            } ?: return null
            val before = store.canvas.value?.strokes?.map { it.id }?.toSet().orEmpty()
            imageEdit.vectorize(com.karapp.drawguidance.content.trace.EdgeOptions(mode = mode), lineWidth, keepImage)
            kotlinx.coroutines.withTimeoutOrNull(30_000) { imageEdit.tracing.first { tracing -> !tracing } }
            return store.canvas.value?.strokes?.lastOrNull { it.id !in before && it.shape?.kind == com.karapp.drawguidance.ui.drawing.ShapeRegistry.TRACED }
        }
    }

    /** v32 eğitim modu: araç gruplarının adım adım eğitimi (çizim, not alma, çizgi roman). */
    val tutorial = com.karapp.drawguidance.ui.screens.canvasedit.tutorial.TutorialController(tutorialHost, store, tutorialPrefs)
        .also { tutorialOrNull = it; lastCreated = java.lang.ref.WeakReference(this) }

    // --- El yazısı önerisi ---

    /**
     * v23 yazı önerisi ve şekil önerisi, v38 çizgi önerisi (üç ayrı tür, ayrı balonlar; her biri dikkate alınan bütün
     * çizgileri tek düğmeyle ve tek geri alma adımıyla düzeltir).
     */
    val textSuggestion: StateFlow<com.karapp.drawguidance.ui.screens.canvasedit.state.TextSuggestion?> get() = handwriting.text
    val shapeSuggestion: StateFlow<com.karapp.drawguidance.ui.screens.canvasedit.state.ShapeSuggestion?> get() = handwriting.shape
    val lineSuggestion: StateFlow<com.karapp.drawguidance.ui.screens.canvasedit.state.LineSuggestion?> get() = handwriting.line
    fun acceptHandwriting(text: String) = handwriting.accept(text)
    fun acceptTemplate(position: Int) = handwriting.acceptTemplate(position)
    fun acceptLines() = handwriting.acceptLines()
    fun dismissHandwriting() = handwriting.dismiss()
    fun dismissTextSuggestion() = handwriting.dismissText()
    fun dismissShapeSuggestion() = handwriting.dismissShape()
    fun dismissLineSuggestion() = handwriting.dismissLine()

    companion object {
        const val MIN_TEXT_SIZE = TextController.MIN_TEXT_SIZE
        const val MAX_TEXT_SIZE = TextController.MAX_TEXT_SIZE
        const val MIN_CANVAS = FrameController.MIN_CANVAS
        const val MAX_CANVAS = FrameController.MAX_CANVAS
        /** Eleman kırpma çerçevesinin en küçük kenarı (tuval birimi). */
        const val ELEMENT_CROP_MIN = FrameController.ELEMENT_CROP_MIN
        /** v43 nesne araçları (Düzenle menüsü). */
        const val SCENE_HARMONY = "harmony"
        const val SCENE_PLACE = "place"

        fun roundCanvas(value: Float): Int = value.roundToInt()

        /** v38 çizgi önerileri kapatılınca gösterilen ileti (çevirisi i18n tablolarında). */
        const val LINE_SUGGEST_OFF = "Çizgi önerileri kapatıldı (⋮ menüsünden açılabilir)"

        private val EDITABLE_KINDS = setOf(SelectionKind.SHAPE, SelectionKind.DIAGRAM, SelectionKind.IMAGE, SelectionKind.TEXT, SelectionKind.FORMULA)

        /** v32 birim testleri için en son açılan düzenleyicinin modeli (eğitimin bellekteki çizimini görmek için). */
        @androidx.annotation.VisibleForTesting
        @Volatile internal var lastCreated: java.lang.ref.WeakReference<CanvasEditViewModel>? = null
    }
}
