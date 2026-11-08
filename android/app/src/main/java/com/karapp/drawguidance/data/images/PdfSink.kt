package com.karapp.drawguidance.data.images

/**
 * PDF'e sayfa sayfa çizen yazıcı. Uygulamada Android'in [android.graphics.pdf.PdfDocument]'i ([AndroidPdfSink]);
 * testlerde sayfalar resme çizilir (Robolectric PdfDocument'i çalıştıramaz).
 */
interface PdfSink {
    /** Yeni sayfa açar (nokta cinsinden boyut; [number] 1'den başlar) ve çizim yüzeyini döner. */
    fun startPage(width: Int, height: Int, number: Int): android.graphics.Canvas
    fun finishPage()
    /** Belgenin baytları. */
    fun finish(): ByteArray
    fun close()
}

class AndroidPdfSink : PdfSink {
    private val document = android.graphics.pdf.PdfDocument()
    private var page: android.graphics.pdf.PdfDocument.Page? = null

    override fun startPage(width: Int, height: Int, number: Int): android.graphics.Canvas {
        val info = android.graphics.pdf.PdfDocument.PageInfo.Builder(width, height, number).create()
        return document.startPage(info).also { page = it }.canvas
    }

    override fun finishPage() {
        page?.let(document::finishPage)
        page = null
    }

    override fun finish(): ByteArray = java.io.ByteArrayOutputStream().use { document.writeTo(it); it.toByteArray() }

    override fun close() = document.close()
}
