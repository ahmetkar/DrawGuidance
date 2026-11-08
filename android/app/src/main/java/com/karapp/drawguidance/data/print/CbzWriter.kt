package com.karapp.drawguidance.data.print

import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * v40 çizgi roman arşivi (.cbz): sayfa resimleri (00001.png, 00002.png …) sıkıştırmadan (STORED; resimler zaten
 * sıkıştırılmış) ve ComicInfo.xml (Anansi şeması: başlık, sayfa sayısı, sayfa listesi, kapak). Sayfalar akışa
 * sırayla yazılır, bin sayfalık çizgi roman da belleğe sığar.
 */
class CbzWriter(out: OutputStream, private val title: String, private val language: String? = null) {
    private val zip = ZipOutputStream(out)
    private val pages = ArrayList<Triple<Int, Int, Long>>()

    /** Sayfa resmi ekler ([extension]: png ya da jpg). */
    fun addPage(bytes: ByteArray, width: Int, height: Int, extension: String) {
        val name = entryName(pages.size + 1, extension)
        val crc = CRC32().apply { update(bytes) }
        val entry = ZipEntry(name).apply {
            method = ZipEntry.STORED
            size = bytes.size.toLong()
            compressedSize = bytes.size.toLong()
            this.crc = crc.value
        }
        zip.putNextEntry(entry)
        zip.write(bytes)
        zip.closeEntry()
        pages += Triple(width, height, bytes.size.toLong())
    }

    fun finish(date: Date = Date()) {
        zip.putNextEntry(ZipEntry(COMIC_INFO))
        zip.write(comicInfo(title, pages, date, language).toByteArray(Charsets.UTF_8))
        zip.closeEntry()
        zip.finish()
        zip.flush()
    }

    val pageCount: Int get() = pages.size

    companion object {
        const val COMIC_INFO = "ComicInfo.xml"

        fun entryName(number: Int, extension: String): String = String.format(Locale.US, "%05d.%s", number, extension)

        fun comicInfo(title: String, pages: List<Triple<Int, Int, Long>>, date: Date, language: String?): String = buildString {
            val y = SimpleDateFormat("yyyy", Locale.US).format(date)
            val m = SimpleDateFormat("M", Locale.US).format(date)
            val d = SimpleDateFormat("d", Locale.US).format(date)
            append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
            append("<ComicInfo xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\">\n")
            append("  <Title>").append(xml(title)).append("</Title>\n")
            append("  <Series>").append(xml(title)).append("</Series>\n")
            append("  <Year>$y</Year>\n  <Month>$m</Month>\n  <Day>$d</Day>\n")
            append("  <PageCount>${pages.size}</PageCount>\n")
            if (!language.isNullOrBlank()) append("  <LanguageISO>").append(xml(language)).append("</LanguageISO>\n")
            append("  <Notes>DrawGuidance</Notes>\n")
            append("  <Pages>\n")
            pages.forEachIndexed { i, (w, h, size) ->
                append("    <Page Image=\"$i\"")
                if (i == 0) append(" Type=\"FrontCover\"")
                append(" ImageSize=\"$size\" ImageWidth=\"$w\" ImageHeight=\"$h\" />\n")
            }
            append("  </Pages>\n")
            append("</ComicInfo>\n")
        }

        private fun xml(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    }
}
