package com.karapp.drawguidance.data.print

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.Locale
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream

/**
 * v40 elle yazılan küçük PDF (1.4): her sayfada tek resim XObject'i (DeviceCMYK ya da DeviceRGB, FlateDecode).
 * Android'in PdfDocument'i yalnız RGB yazdığından baskıya hazır (CMYK) PDF bununla üretilir. Sayfalar akışa sırayla
 * yazılır (resimler sayfa sayfa sıkıştırılır), sonunda xref tablosu ve trailer eklenir.
 */
class MiniPdfWriter(out: OutputStream, private val title: String? = null) {
    private val out = CountingStream(out)
    private val offsets = ArrayList<Long>()
    private val pageIds = ArrayList<Int>()
    /** 1: katalog, 2: sayfalar (sona yazılır); sayfa nesneleri 3'ten başlar. */
    private var nextId = 3
    private var finished = false

    init {
        write("%PDF-1.4\n")
        // İkili dosya olduğunu belirten yorum satırı
        this.out.write(byteArrayOf('%'.code.toByte(), 0xE2.toByte(), 0xE3.toByte(), 0xCF.toByte(), 0xD3.toByte(), '\n'.code.toByte()))
    }

    private fun write(s: String) = out.write(s.toByteArray(Charsets.ISO_8859_1))

    private fun begin(id: Int) {
        while (offsets.size < id) offsets.add(-1L)
        offsets[id - 1] = out.count
        write("$id 0 obj\n")
    }

    /**
     * Sayfa ekler: [pageWidth]×[pageHeight] nokta; resim sayfada ([left], [top]) köşesinden [drawWidth]×[drawHeight]
     * alana çizilir. [source] CMYK ise DeviceCMYK, [rgb] verilirse DeviceRGB yazılır.
     */
    fun addPage(
        pageWidth: Float, pageHeight: Float,
        source: CmykSource,
        left: Float = 0f, top: Float = 0f,
        drawWidth: Float = pageWidth, drawHeight: Float = pageHeight,
    ) = addImagePage(pageWidth, pageHeight, source.width, source.height, "/DeviceCMYK", left, top, drawWidth, drawHeight) { sink ->
        val band = ByteArray(source.width * 16 * 4)
        var y = 0
        while (y < source.height) {
            val rows = minOf(16, source.height - y)
            source.rows(y, rows, band)
            sink.write(band, 0, rows * source.width * 4)
            y += rows
        }
    }

    /** RGB resimli sayfa ([rgbRows] satır başına 3 bayt yazar). */
    fun addRgbPage(
        pageWidth: Float, pageHeight: Float, width: Int, height: Int,
        left: Float = 0f, top: Float = 0f, drawWidth: Float = pageWidth, drawHeight: Float = pageHeight,
        rgbRows: (OutputStream) -> Unit,
    ) = addImagePage(pageWidth, pageHeight, width, height, "/DeviceRGB", left, top, drawWidth, drawHeight, rgbRows)

    private fun addImagePage(
        pageWidth: Float, pageHeight: Float, width: Int, height: Int, colorSpace: String,
        left: Float, top: Float, drawWidth: Float, drawHeight: Float,
        pixels: (OutputStream) -> Unit,
    ) {
        check(!finished)
        val pageId = nextId++
        val contentId = nextId++
        val imageId = nextId++
        val bos = ByteArrayOutputStream()
        DeflaterOutputStream(bos, Deflater(6)).use { pixels(it) }
        val image = bos.toByteArray()
        val bottom = pageHeight - top - drawHeight
        val content = "q ${n(drawWidth)} 0 0 ${n(drawHeight)} ${n(left)} ${n(bottom)} cm /Im0 Do Q\n".toByteArray(Charsets.ISO_8859_1)

        begin(pageId)
        write("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${n(pageWidth)} ${n(pageHeight)}] ")
        write("/Resources << /XObject << /Im0 $imageId 0 R >> >> /Contents $contentId 0 R >>\nendobj\n")
        begin(contentId)
        write("<< /Length ${content.size} >>\nstream\n"); out.write(content); write("\nendstream\nendobj\n")
        begin(imageId)
        write("<< /Type /XObject /Subtype /Image /Width $width /Height $height /ColorSpace $colorSpace ")
        write("/BitsPerComponent 8 /Filter /FlateDecode /Length ${image.size} >>\nstream\n")
        out.write(image)
        write("\nendstream\nendobj\n")
        pageIds += pageId
    }

    /** Katalog, sayfa ağacı, bilgi sözlüğü, xref ve trailer. */
    fun finish() {
        check(!finished)
        finished = true
        begin(1)
        write("<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")
        begin(2)
        write("<< /Type /Pages /Kids [${pageIds.joinToString(" ") { "$it 0 R" }}] /Count ${pageIds.size} >>\nendobj\n")
        val infoId = nextId++
        begin(infoId)
        write("<< /Producer (DrawGuidance)")
        title?.let { write(" /Title ${pdfString(it)}") }
        write(" >>\nendobj\n")
        val xref = out.count
        write("xref\n0 ${offsets.size + 1}\n")
        write("0000000000 65535 f \n")
        offsets.forEach { write(String.format(Locale.US, "%010d 00000 n \n", it)) }
        write("trailer\n<< /Size ${offsets.size + 1} /Root 1 0 R /Info $infoId 0 R >>\nstartxref\n$xref\n%%EOF\n")
        out.flush()
    }

    val pageCount: Int get() = pageIds.size

    private fun n(v: Float): String = if (v == v.toInt().toFloat()) v.toInt().toString() else String.format(Locale.US, "%.3f", v)

    companion object {
        /** PDF metni: ASCII ise (…) kaçışlı, değilse UTF-16BE onaltılık. */
        fun pdfString(s: String): String =
            if (s.all { it.code in 32..126 }) "(" + s.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)") + ")"
            else "<FEFF" + s.toByteArray(Charsets.UTF_16BE).joinToString("") { String.format(Locale.US, "%02X", it) } + ">"
    }
}
