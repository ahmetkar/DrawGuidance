package com.karapp.drawguidance.data.transfer

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.karapp.drawguidance.data.images.CanvasAssetStore
import com.karapp.drawguidance.data.images.PreparedImage
import com.karapp.drawguidance.data.local.ContentCodec
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.CanvasSizeTemplate
import com.karapp.drawguidance.data.model.CanvasUi
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.data.repository.CanvasRepository
import com.karapp.drawguidance.ui.drawing.StrokeRenderer.drawCanvasContent
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** v29 aktarmanın hedefi: kayıtlı bir tuval ya da bu türde yeni bir tuval. */
sealed interface TransferTarget {
    data class Existing(val canvasId: String) : TransferTarget
    data class New(val kind: CanvasKind) : TransferTarget
}

/** v29 aktarma sonucu: hedef tuvalin kimliği ve adı (başarısızsa null). */
data class TransferResult(val canvasId: String, val name: String)

/**
 * v29 modlar arası aktarma: tuval (resim) modundaki çizim not alma ya da çizgi roman tuvaline, 3B şekil tuvale ya da
 * çizgi romana **resim** (saydam zeminli PNG, tek eleman) ya da **vektör** (düzenlenebilir elemanlar) olarak aktarılır.
 * Hedefin açık sayfasının ortasına, sayfanın en fazla %70'ini kaplayacak şekilde, oranı korunarak yerleşir. Eklenen
 * resimler (resim elemanları, 3B şekillerin verisi) hedef tuvale kopyalanır.
 */
class CanvasTransfer(
    private val repository: CanvasRepository,
    private val assets: CanvasAssetStore,
) {
    /** Aktarılabilecek tuvaller ([kinds] türünde, [exclude] hariç; en son değişen önce). */
    fun targets(kinds: Set<CanvasKind>, exclude: String?): Flow<List<CanvasUi>> =
        repository.observeCanvases().map { list ->
            list.filter { it.kind in kinds && it.localId != exclude }.sortedByDescending { it.updatedAt }
        }

    private suspend fun resolve(target: TransferTarget, name: String): CanvasUi? = when (target) {
        is TransferTarget.Existing -> repository.getCanvas(target.canvasId)
        is TransferTarget.New -> {
            val t = target.kind.defaultSize
            repository.createCanvas(name, t.width, t.height, "#FFFFFF", aiEnabled = true, kind = target.kind)
        }
    }

    /**
     * Elemanları vektör olarak (kopya; yeni kimliklerle) hedefe ekler. [sourceCanvasId] verilirse resim ve 3B şekil
     * verileri hedefe kopyalanır. [group] true ise aktarılanlar tek grup olur (3B çizgileri).
     */
    suspend fun sendStrokes(
        target: TransferTarget,
        newName: String,
        strokes: List<Stroke>,
        group: Boolean = false,
    ): TransferResult? = withContext(Dispatchers.IO) {
        val items = strokes
        if (items.all { it.brushType == BrushType.ERASER }) return@withContext null
        val canvas = resolve(target, newName) ?: return@withContext null
        val bounds = ElementOps.bounds(items) ?: return@withContext null
        // Yeni kimlikler; grup kimlikleri de yenilenir (hedefteki gruplarla karışmasın)
        val groups = HashMap<String, String>()
        val shared = if (group) UUID.randomUUID().toString() else null
        val copied = items.map { s ->
            var c = s.copy(
                id = UUID.randomUUID().toString(),
                groupId = shared ?: s.groupId?.let { g -> groups.getOrPut(g) { UUID.randomUUID().toString() } },
                layerId = null,
                space = null,
            )
            s.assetId?.let { id -> c = c.copy(assetId = assets.copyTo(id, canvas.localId) ?: id) }
            s.model?.let { link -> c = c.copy(model = link.copy(dataAssetId = assets.copyTo(link.dataAssetId, canvas.localId) ?: link.dataAssetId)) }
            c
        }
        val placed = fit(copied, bounds, canvas.width, canvas.height)
        append(canvas, placed)
    }

    /** Resmi (saydam zeminli) hedefe tek resim elemanı olarak ekler. */
    suspend fun sendImage(target: TransferTarget, newName: String, bitmap: Bitmap): TransferResult? = withContext(Dispatchers.IO) {
        val canvas = resolve(target, newName) ?: return@withContext null
        val bytes = ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
        val image = PreparedImage(bytes, "image/png", bitmap.width, bitmap.height, bitmap, bytes.size, 0f)
        val assetId = assets.save(canvas.localId, image)
        val q = ContentCodec::quantize
        val stroke = Stroke(
            id = UUID.randomUUID().toString(),
            points = listOf(
                StrokePoint(q(0f), q(0f)), StrokePoint(q(bitmap.width.toFloat()), q(0f)),
                StrokePoint(q(bitmap.width.toFloat()), q(bitmap.height.toFloat())), StrokePoint(q(0f), q(bitmap.height.toFloat())),
            ),
            colorHex = "#000000", brushType = BrushType.IMAGE, sizePx = 1f, opacity = 1f, order = 0, assetId = assetId,
        )
        val placed = fit(listOf(stroke), Rect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat()), canvas.width, canvas.height)
        append(canvas, placed)
    }

    private suspend fun append(canvas: CanvasUi, added: List<Stroke>): TransferResult {
        val page = canvas.currentPage.coerceIn(0, max(0, canvas.pages.size - 1))
        val pages = canvas.pages.ifEmpty { listOf(emptyList()) }.toMutableList()
        val existing = pages[page]
        pages[page] = existing + added.mapIndexed { i, s -> s.copy(order = existing.size + i) }
        repository.saveStrokes(canvas.localId, pages, canvas.layers)
        return TransferResult(canvas.localId, canvas.name)
    }

    companion object {
        /** Sayfanın en fazla bu oranını kaplar. */
        const val FIT = 0.7f

        /** Elemanları sayfanın ortasına, oranı korunarak sığdırır (küçükse büyütülmez). */
        fun fit(strokes: List<Stroke>, bounds: Rect, width: Int, height: Int): List<Stroke> {
            val k = min(1f, min(width * FIT / bounds.width.coerceAtLeast(1f), height * FIT / bounds.height.coerceAtLeast(1f)))
            val ids = strokes.mapTo(HashSet()) { it.id }
            val scaled = if (k < 0.999f) ElementOps.scale(strokes, ids, k, k, bounds.center) else strokes
            return ElementOps.translate(scaled, ids, width / 2f - bounds.center.x, height / 2f - bounds.center.y)
        }

        /**
         * Elemanların resmi: saydam zeminde, kutularının çevresinde [pad] boşlukla; en uzun kenar en fazla [maxSide]
         * (küçük çizimler 2 kata kadar büyütülür). Resim elemanlarının görüntüleri yüklenmiş olmalı.
         */
        fun render(strokes: List<Stroke>, pad: Float = 12f, maxSide: Int = 2048): Bitmap? {
            val b = ElementOps.bounds(strokes) ?: return null
            val r = Rect(b.left - pad, b.top - pad, b.right + pad, b.bottom + pad)
            val k = min(2f, maxSide / max(r.width, r.height).coerceAtLeast(1f))
            val w = (r.width * k).roundToInt().coerceIn(1, maxSide)
            val h = (r.height * k).roundToInt().coerceIn(1, maxSide)
            // Elemanlar resmin köşesine taşınır (silgi katmanı resmin tamamını kapsasın)
            val moved = ElementOps.translate(strokes, strokes.mapTo(HashSet()) { it.id }, -r.left, -r.top)
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap.asImageBitmap()), Size(w.toFloat(), h.toFloat())) {
                scale(k, k, pivot = Offset.Zero) {
                    drawCanvasContent(Color.Transparent, moved, r.width, r.height)
                }
            }
            return bitmap
        }
    }
}
