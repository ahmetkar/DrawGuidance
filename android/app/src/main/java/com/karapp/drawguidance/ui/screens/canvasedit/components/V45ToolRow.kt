package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.ui.theme.NeuTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * v45 üst menülerde düğmeler dörderli gruplanıyordu. v48 menülerde gruplama yok (ayraç konmaz); 4 yalnız iki parçalı
 * menünün bölme noktasının hesabında kullanılır ([ToolRowSplit]).
 */
const val TOOL_GROUP_SIZE = 4

/** v48 menülerde grup ayracı yok. */
const val TOOL_GROUPING = false

/** v45 grup ayracının test etiketi. */
const val TOOL_GROUP_DIVIDER = "tool_group_divider"

/**
 * v45 üst menüde bir düğme yeri. Menüler düğmelerini bu yerlerin listesi olarak verir; gruplama ve ayraçlar
 * listeden otomatik çıkar (yeni bir düğme eklemek için listeye bir yer eklemek yeter). [key] satırın hizalanacağı
 * seçili düğmeyi tanır.
 */
class ToolSlot(val key: String, val selected: Boolean = false, val content: @Composable () -> Unit)

/**
 * v46 üst menünün ikiye bölünmesi: yok, alt alta (dikey ekran, sığmazsa) ya da yan yana (yatay/geniş ekran).
 * v47 [STACK_ALWAYS]: basit modların dikey ekranında menü her zaman iki satır, alt alta.
 */
enum class ToolRowSplitMode { NONE, STACK, SIDE, STACK_ALWAYS }

/** v46 ekranın üst menüyü nasıl böleceği (düzenleyici sağlar; öntanımlı bölünmez). */
val LocalToolRowSplit = androidx.compose.runtime.staticCompositionLocalOf { ToolRowSplitMode.NONE }

/** v46 iki menü parçası arasındaki ayracın test etiketi. */
const val TOOL_PART_DIVIDER = "tool_part_divider"

/** v46 bölme noktası (saf; testlerde de kullanılır). */
object ToolRowSplit {
    /**
     * [count] düğmeli menünün ikinci parçasının başladığı sıra. 4 ve daha az düğme bölünmez ([count]). İki parça
     * dengeli (ilki en çok bir fazla); dörtlü grup sınırı yarıya bir düğme yakınsa oradan bölünür (grup bölünmesin;
     * iki parçada da en az iki düğme kalır).
     */
    fun index(count: Int, group: Int = TOOL_GROUP_SIZE): Int {
        if (count <= group) return count
        val half = (count + 1) / 2
        val boundary = (half / group) * group
        val next = boundary + group
        // Sınıra kayma iki parçada da en az iki düğme bırakıyorsa
        return when {
            boundary >= 2 && half - boundary <= 1 && count - boundary >= 2 -> boundary
            count - next >= 2 && next - half <= 1 -> next
            else -> half
        }
    }
}

/** v45 gruplama kuralları (saf; testlerde de kullanılır). */
object ToolGroups {
    /** [count] düğmeli satırda ayracın önüne geldiği düğme sıraları: 4, 8, 12… */
    fun dividerBefore(count: Int, size: Int = TOOL_GROUP_SIZE): List<Int> = if (!TOOL_GROUPING || size <= 0) emptyList() else (size until count step size).toList()

    /** [count] düğmeli satırdaki ayraç sayısı. */
    fun dividerCount(count: Int, size: Int = TOOL_GROUP_SIZE): Int = dividerBefore(count, size).size
}

/** v45 kaydırılan satırın seçili düğmeye hizalanması (saf hesap). */
object ToolRowAlign {
    /**
     * Seçili düğme görünür alanda tam görünüyorsa null (satır kıpırdamaz: döndürmede ve yeniden çizimde sıçrama yok);
     * görünmüyorsa düğmeyi taştığı kenara [margin] payla getiren en küçük kaydırma değeri ([max] ile sınırlı). [left]/[right]
     * düğmenin görünür alana göre konumu (piksel).
     */
    fun target(left: Float, right: Float, viewport: Float, value: Int, max: Int, margin: Float, rtl: Boolean): Int? {
        if (viewport <= 0f) return null
        if (left >= -0.5f && right <= viewport + 0.5f) return null
        // En az kaydırma: düğme hangi kenardan taşıyorsa o kenara (paylı) gelir; satırdan genişse başlangıç kenarına
        val startHidden = if (rtl) right > viewport + 0.5f else left < -0.5f
        val endHidden = if (rtl) left < -0.5f else right > viewport + 0.5f
        val delta = when {
            startHidden -> if (rtl) viewport - margin - right else left - margin
            endHidden -> if (rtl) margin - left else right - viewport + margin
            else -> return null
        }
        val wanted = (value + delta).roundToInt().coerceIn(0, max.coerceAtLeast(0))
        return wanted.takeIf { it != value }
    }
}

/**
 * v45 satır hizalayıcı: bir düğme seçilince (elle ya da program seçince) satır o düğme görünecek biçimde kayar.
 * Kullanıcı satırı parmakla kaydırınca hizalama bir sonraki seçime kadar durur. Durum döndürmede korunur.
 */
@Stable
class ToolRowAligner internal constructor(
    val scroll: ScrollState,
    private val scope: CoroutineScope,
    private val marginPx: Float,
    private val rtl: Boolean,
    userScrolledInitial: Boolean,
) {
    /** Kaydırılan alanın (görünür pencere) yerleşimi. */
    var viewport: LayoutCoordinates? = null

    /** Düğme yerlerinin yerleşimleri (anahtara göre). */
    val slots = HashMap<String, LayoutCoordinates>()

    /** Seçili düğmenin anahtarı (seçili düğme yoksa null). */
    var selectedKey: String? = null

    /** Seçili düğmenin yerleşimi. */
    val selected: LayoutCoordinates? get() = selectedKey?.let { slots[it] }

    /** Kullanıcı satırı elle kaydırdı: sonraki seçime kadar kendiliğinden hizalanmaz. */
    var userScrolled by mutableStateOf(userScrolledInitial)
        internal set

    private var job: Job? = null

    /**
     * Bu seçim için hizalama henüz yapılmadı. Hizalama her seçimde (ve ilk yerleşimde) bir kez yapılır; sonraki
     * yerleşimler (kaydırma, yeniden çizim, erişilebilirlik kaydırması) satırı oynatmaz.
     */
    private var pending = true

    /** Gereken kaydırma (hizalı ya da hesaplanamıyorsa null). */
    fun target(): Int? {
        val vp = viewport?.takeIf { it.isAttached } ?: return null
        val sel = selected?.takeIf { it.isAttached } ?: return null
        val box = vp.localBoundingBoxOf(sel, clipBounds = false)
        return ToolRowAlign.target(box.left, box.right, vp.size.width.toFloat(), scroll.value, scroll.maxValue, marginPx, rtl)
    }

    /** Bu seçim için hizalama bekliyorsa, kullanıcı kaydırmadıysa ve seçili düğme tam görünmüyorsa satırı hizalar. */
    fun requestAlign() {
        if (!pending || userScrolled || job?.isActive == true) return
        // Yerleşim henüz yoksa sonraki yerleşimde yeniden denenir
        if (viewport?.isAttached != true || selected?.isAttached != true) return
        pending = false
        val t = target() ?: return
        job = scope.launch { scroll.animateScrollTo(t) }
    }

    internal fun onUserDrag() {
        userScrolled = true
        job?.cancel()
    }

    internal fun onSelection() {
        userScrolled = false
        pending = true
        job?.cancel()
        requestAlign()
    }
}

/**
 * v45 [selectionKey] değişince (yeni seçim) hizalama yeniden açılır. Seçim anahtarı ve "elle kaydırıldı" bilgisi
 * döndürmede saklanır: döndürmeden sonra aynı seçim için satır kıpırdamaz.
 */
@Composable
fun rememberToolRowAligner(scroll: ScrollState, selectionKey: String?): ToolRowAligner {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var savedUser by rememberSaveable { mutableStateOf(false) }
    var savedKey by rememberSaveable { mutableStateOf(selectionKey) }
    val aligner = remember(scroll, rtl) { ToolRowAligner(scroll, scope, with(density) { ToolRowAlignMargin.toPx() }, rtl, savedUser) }
    LaunchedEffect(aligner) {
        scroll.interactionSource.interactions.collect { if (it is DragInteraction.Start) { aligner.onUserDrag(); savedUser = true } }
    }
    aligner.selectedKey = selectionKey
    LaunchedEffect(aligner, selectionKey) {
        if (selectionKey != savedKey) {
            savedKey = selectionKey
            savedUser = false
            aligner.onSelection()
        } else aligner.requestAlign()
    }
    return aligner
}

/** Hizalanan düğmenin satır başına uzaklığı (önündeki düğmeden bir parça görünsün). */
private val ToolRowAlignMargin = 16.dp

/**
 * v45 düğme yerlerini sırayla yerleştirir; her [groupSize] düğmede bir ayraç koyar. Seçili düğmenin yeri
 * hizalayıcıya bildirilir. [vertical] (dikey ray) iken ayraç yatay çizgidir.
 */
@Composable
fun GroupedToolSlots(slots: List<ToolSlot>, aligner: ToolRowAligner?, groupSize: Int = TOOL_GROUP_SIZE, vertical: Boolean = false) {
    val dividers = ToolGroups.dividerBefore(slots.size, groupSize).toSet()
    slots.forEachIndexed { i, slot ->
        if (i in dividers) ToolGroupDivider(vertical)
        key(slot.key) {
            Box(
                if (aligner == null) Modifier else Modifier.onGloballyPositioned { c ->
                    aligner.slots[slot.key] = c
                    if (slot.key == aligner.selectedKey) aligner.requestAlign()
                },
            ) { slot.content() }
        }
    }
}

/** v45 iki düğme grubu arasındaki ayraç (yatay satırda dikey çizgi, dikey rayda yatay çizgi); boşluk dahil. */
@Composable
fun ToolGroupDivider(vertical: Boolean = false) {
    val color = NeuTheme.colors.contentMuted.copy(alpha = 0.4f)
    Box(
        (if (vertical) Modifier.padding(vertical = 5.dp).height(1.5.dp).width(30.dp) else Modifier.padding(horizontal = 5.dp).width(1.5.dp).height(26.dp))
            .background(color)
            .testTag(TOOL_GROUP_DIVIDER),
    )
}
