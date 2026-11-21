package com.karapp.drawguidance.data.images

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.karapp.drawguidance.data.export.DocFormat
import com.karapp.drawguidance.data.export.DxfWriter
import com.karapp.drawguidance.data.export.ExportDoc
import com.karapp.drawguidance.data.export.PsdWriter
import com.karapp.drawguidance.data.export.SvgWriter
import com.karapp.drawguidance.data.model.CanvasUi
import com.karapp.drawguidance.data.model.Layer
import com.karapp.drawguidance.data.model.Layers
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.ui.common.hexToColor
import com.karapp.drawguidance.ui.drawing.StrokeRenderer.drawCanvasContent

/**
 * v56 aşama 9 (2D-3): açık sayfayı SVG (katmanlar grup; vektöre çevrilemeyenler gömülü PNG), katmanlı PSD ya da DXF
 * olarak kaydeder. Tuval boyutunda (1x) çalışır.
 */
object V56DocExport {
    data class Result(val file: ExportedFile, val skipped: Int)

    fun doc(canvas: CanvasUi): ExportDoc = ExportDoc(
        canvas.width, canvas.height, canvas.paperOf(canvas.currentPage), canvas.layers,
        canvas.pages.getOrElse(canvas.currentPage) { canvas.strokes }, canvas.name,
    )

    /** Çizimleri saydam zeminde, katman etkisi olmadan (tek katman gibi) çizer. */
    fun renderStrokes(canvas: CanvasUi, strokes: List<Stroke>): Bitmap {
        val w = canvas.width.coerceAtLeast(1); val h = canvas.height.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap.asImageBitmap()), Size(w.toFloat(), h.toFloat())) {
            drawCanvasContent(Color.Transparent, strokes.map { it.copy(layerId = null) }, w.toFloat(), h.toFloat())
        }
        return bitmap
    }

    private fun png(b: Bitmap): ByteArray = java.io.ByteArrayOutputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray() }

    private fun pixels(b: Bitmap): IntArray = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }

    fun svg(canvas: CanvasUi): String = SvgWriter.write(doc(canvas)) { run ->
        val b = renderStrokes(canvas, run)
        try { png(b) } finally { b.recycle() }
    }

    fun psd(exporter: ImageExporter, canvas: CanvasUi): ByteArray {
        val d = doc(canvas)
        val layers = canvas.layers.ifEmpty { listOf(Layer.Default) }
        val out = ArrayList<PsdWriter.PsdLayer>()
        val paper = hexToColor(d.paperHex).let { c -> (0xFF shl 24) or (c.toArgbRgb()) }
        out += PsdWriter.PsdLayer("Arka plan", IntArray(d.width * d.height) { paper })
        layers.forEach { l ->
            val own = d.strokes.filter { Layers.of(it, layers) == l.id && !it.hidden }
            val b = renderStrokes(canvas, own)
            try {
                out += PsdWriter.PsdLayer(l.name, pixels(b), l.visible, l.opacity, l.blend, l.clipToBelow, l.alphaLock)
            } finally { b.recycle() }
        }
        val merged = exporter.render(canvas, 1f, includeGuides = false)
        val composite = try {
            if (merged.width == d.width && merged.height == d.height) pixels(merged)
            else Bitmap.createScaledBitmap(merged, d.width, d.height, true).let { s -> pixels(s).also { if (s !== merged) s.recycle() } }
        } finally { merged.recycle() }
        return PsdWriter.write(d.width, d.height, out, composite)
    }

    private fun Color.toArgbRgb(): Int =
        ((red * 255f + 0.5f).toInt() shl 16) or ((green * 255f + 0.5f).toInt() shl 8) or (blue * 255f + 0.5f).toInt()

    fun export(exporter: ImageExporter, canvas: CanvasUi, format: DocFormat, fileName: String, target: SaveTarget = SaveTarget.Default): Result {
        val name = ImageExporter.safeName(fileName.ifBlank { ImageExporter.defaultFileName(canvas.name) }) + "." + format.extension
        var skipped = 0
        val bytes = when (format) {
            DocFormat.SVG -> svg(canvas).toByteArray(Charsets.UTF_8)
            DocFormat.PSD -> psd(exporter, canvas)
            DocFormat.DXF -> DxfWriter.write(doc(canvas)).also { skipped = it.skipped }.text.toByteArray(Charsets.UTF_8)
        }
        return Result(exporter.writeDocument(name, format.mime, target) { it.write(bytes) }, skipped)
    }
}
