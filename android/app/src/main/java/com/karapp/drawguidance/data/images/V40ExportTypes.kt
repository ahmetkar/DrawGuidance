package com.karapp.drawguidance.data.images

/**
 * v40 kaydetme yeri: her kaydetme penceresinde "Konum seç" ile değiştirilebilir.
 * - [Default]: bugünkü varsayılan yer (Ayarlar'daki klasör / Galeri albümü / İndirilenler)
 * - [Folder]: Android klasör seçiciyle (ACTION_OPEN_DOCUMENT_TREE) seçilen, kalıcı izinli ve hatırlanan klasör
 * - [Document]: "Farklı kaydet" ile (ACTION_CREATE_DOCUMENT) kullanıcının adını ve yerini seçtiği tek dosya
 */
sealed interface SaveTarget {
    data object Default : SaveTarget
    data class Folder(val treeUri: String, val label: String) : SaveTarget
    data class Document(val uri: String, val label: String) : SaveTarget
}

/** v40 baskıya hazır (CMYK) dosya türleri. */
enum class CmykFormat(val extension: String, val mime: String, val label: String) {
    /** Adobe APP14 işaretli CMYK JPEG (Resimlerim'de de görünür). */
    JPEG("jpg", "image/jpeg", "JPEG"),
    /** Kayıpsız CMYK TIFF (Deflate, 300 dpi). */
    TIFF("tif", "image/tiff", "TIFF"),
    /** DeviceCMYK resimli PDF (çok sayfalı tuvalde tüm sayfalar tek PDF'te). */
    PDF("pdf", "application/pdf", "PDF");

    /** Belge olarak kaydedilir (Resimlerim'e eklenmez; Android önizleyemez). */
    val isDocument: Boolean get() = this != JPEG
}

/** v40 çizgi roman arşivi (.cbz) ayarları. */
data class CbzOptions(
    val fileName: String,
    val pages: PdfPages = PdfPages.ALL,
    val fromPage: Int = 1,
    val toPage: Int = Int.MAX_VALUE,
    val format: com.karapp.drawguidance.data.settings.ImageFormat = com.karapp.drawguidance.data.settings.ImageFormat.JPEG,
    val quality: Int = 90,
    val scale: Float = 1f,
    val target: SaveTarget = SaveTarget.Default,
) {
    fun pageIndices(pageCount: Int, currentPage: Int): List<Int> =
        PdfOptions(fileName, pages = pages, fromPage = fromPage, toPage = toPage).pageIndices(pageCount, currentPage)
}
