package com.karapp.drawguidance.data.images

import java.util.zip.Inflater

/**
 * v54 resim dosyasının yapı ve gömülü kod denetimi (çözücüye verilmeden önce; saf Kotlin, cihazdan bağımsız).
 *
 * - PNG: imzadan sonra parçalar (uzunluk, tür, veri, CRC) dosya içinde kalmalı; ilk parça IHDR, IEND bulunmalı.
 * - JPEG: SOI ile başlar; segment uzunlukları dosya içinde kalmalı (SOS sonrası sıkıştırılmış veri atlanır).
 * - WEBP: RIFF boyutu dosyayı aşmamalı; parçalar dosya içinde kalmalı.
 * - Metin/üst veri parçaları (PNG tEXt/zTXt/iTXt ve bilinmeyen yardımcı parçalar, JPEG APPn/COM, WEBP EXIF/XMP) ve
 *   bitiş işaretinden sonraki veriler betik, HTML/PHP, çalıştırılabilir dosya ve arşiv imzalarına karşı taranır.
 *
 * Bitişten sonra gelen zararsız veri (ör. bazı telefonların fotoğraf sonuna eklediği bölüm) reddedilmez; resim zaten
 * her zaman yeniden kodlandığından saklanmaz. Sıkıştırılmış piksel verisi (IDAT, JPEG tarama, VP8) taranmaz.
 */
object ImageSecurity {
    enum class Verdict { CLEAN, MALFORMED, SUSPICIOUS }

    /** Metin olarak aranan imzalar (küçük harfe çevrilmiş ASCII üzerinde). */
    private val TEXT_SIGNS = listOf(
        // En az 5 karakter: büyük ikili veride (ör. fotoğraf sonundaki video) rastlantıyla eşleşmesin
        "<script", "</script", "<?php", "<html", "<body", "<iframe", "<object", "<embed", "<svg ", "javascript:",
        "vbscript:", "#!/bin/", "#!/usr/", "eval(", "document.cookie", "powershell", "cmd.exe", "/bin/sh",
    )

    /** İkili imzalar (çalıştırılabilir, arşiv, belge). */
    private val BIN_SIGNS: List<ByteArray> = listOf(
        byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte()),
        byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 3, 4),
        "Rar!".toByteArray(Charsets.US_ASCII),
        "%PDF-".toByteArray(Charsets.US_ASCII),
        byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte()),
        byteArrayOf('d'.code.toByte(), 'e'.code.toByte(), 'x'.code.toByte(), '\n'.code.toByte()),
    )

    /** Taranacak en fazla açılmış metin (sıkıştırma bombasına karşı). */
    private const val MAX_INFLATE = 1 shl 20

    fun inspect(b: ByteArray, mime: String): Verdict = runCatching {
        when (mime) {
            "image/png" -> png(b)
            "image/jpeg" -> jpeg(b)
            "image/webp" -> webp(b)
            else -> Verdict.MALFORMED
        }
    }.getOrDefault(Verdict.MALFORMED)

    // MARK: PNG

    private fun png(b: ByteArray): Verdict {
        var at = 8
        var first = true
        while (true) {
            if (at + 12 > b.size) return Verdict.MALFORMED
            val len = be32(b, at)
            if (len < 0 || len > b.size - at - 12) return Verdict.MALFORMED
            val type = String(b, at + 4, 4, Charsets.ISO_8859_1)
            if (!type.all { it in 'A'..'Z' || it in 'a'..'z' }) return Verdict.MALFORMED
            if (first && type != "IHDR") return Verdict.MALFORMED
            first = false
            val data = at + 8
            when (type) {
                "IEND" -> return trailing(b, data + len + 4)
                "IDAT", "IHDR", "PLTE", "tRNS", "fdAT", "fcTL", "acTL" -> Unit
                "zTXt" -> {
                    // anahtar \0 yöntem sıkıştırılmış metin
                    val z = indexOf0(b, data, data + len)
                    if (z >= 0 && z + 2 <= data + len) {
                        if (suspicious(b, data, z - data)) return Verdict.SUSPICIOUS
                        val text = inflate(b, z + 2, data + len - z - 2) ?: return Verdict.MALFORMED
                        if (suspicious(text, 0, text.size)) return Verdict.SUSPICIOUS
                    }
                }
                "iTXt" -> {
                    // anahtar \0 bayrak yöntem dil \0 çeviri \0 metin
                    val z = indexOf0(b, data, data + len)
                    if (z >= 0 && z + 2 < data + len && b[z + 1].toInt() == 1) {
                        val l1 = indexOf0(b, z + 3, data + len)
                        val l2 = if (l1 >= 0) indexOf0(b, l1 + 1, data + len) else -1
                        if (l2 >= 0) {
                            if (suspicious(b, data, l2 + 1 - data)) return Verdict.SUSPICIOUS
                            val text = inflate(b, l2 + 1, data + len - l2 - 1) ?: return Verdict.MALFORMED
                            if (suspicious(text, 0, text.size)) return Verdict.SUSPICIOUS
                        } else if (suspicious(b, data, len)) return Verdict.SUSPICIOUS
                    } else if (suspicious(b, data, len)) return Verdict.SUSPICIOUS
                }
                // tEXt ve bilinmeyen yardımcı parçalar düz taranır
                else -> if (suspicious(b, data, len)) return Verdict.SUSPICIOUS
            }
            at = data + len + 4
        }
    }

    // MARK: JPEG

    private fun jpeg(b: ByteArray): Verdict {
        if (b.size < 4 || b[0] != 0xFF.toByte() || b[1] != 0xD8.toByte()) return Verdict.MALFORMED
        var at = 2
        while (at < b.size) {
            // Dolgu baytları (0xFF tekrarları)
            if (b[at] != 0xFF.toByte()) return Verdict.MALFORMED
            while (at < b.size && b[at] == 0xFF.toByte()) at++
            if (at >= b.size) return Verdict.MALFORMED
            val marker = b[at].toInt() and 0xFF
            at++
            when {
                marker == 0xD9 -> return trailing(b, at) // EOI
                marker == 0x01 || marker in 0xD0..0xD7 -> continue // TEM, RSTn: uzunluk yok
                else -> {
                    if (at + 2 > b.size) return Verdict.MALFORMED
                    val len = be16(b, at)
                    if (len < 2 || at + len > b.size) return Verdict.MALFORMED
                    // APPn ve COM üst veri: taranır
                    if (marker in 0xE0..0xEF || marker == 0xFE) {
                        if (suspicious(b, at + 2, len - 2)) return Verdict.SUSPICIOUS
                    }
                    at += len
                    if (marker == 0xDA) {
                        // SOS: sıkıştırılmış veri bir sonraki gerçek işarete kadar (FF00 ve RSTn veri içindedir)
                        while (at + 1 < b.size) {
                            if (b[at] == 0xFF.toByte()) {
                                val n = b[at + 1].toInt() and 0xFF
                                if (n != 0x00 && n !in 0xD0..0xD7 && n != 0xFF) break
                            }
                            at++
                        }
                        // EOI olmadan biten (kesik) JPEG: çözücü açabilirse kabul; sonrasında veri yok
                        if (at + 1 >= b.size) return Verdict.CLEAN
                    }
                }
            }
        }
        return Verdict.CLEAN
    }

    // MARK: WEBP

    private fun webp(b: ByteArray): Verdict {
        if (b.size < 20) return Verdict.MALFORMED
        val riff = le32(b, 4)
        if (riff < 4 || riff.toLong() + 8 > b.size) return Verdict.MALFORMED
        val end = 8 + riff
        var at = 12
        while (at + 8 <= end) {
            val tag = String(b, at, 4, Charsets.ISO_8859_1)
            val len = le32(b, at + 4)
            if (len < 0 || at + 8L + len > end) return Verdict.MALFORMED
            if (tag == "EXIF" || tag == "XMP " || tag !in setOf("VP8 ", "VP8L", "VP8X", "ALPH", "ANIM", "ANMF", "ICCP")) {
                if (suspicious(b, at + 8, len)) return Verdict.SUSPICIOUS
            }
            at += 8 + len + (len and 1)
        }
        return trailing(b, end)
    }

    // MARK: Ortak

    /**
     * Bitiş işaretinden sonraki veri: imza varsa şüpheli; yoksa (resim yeniden kodlanacağından) temiz. Büyük ekte ikili
     * imzalar yalnız ekin başında (eklenmiş dosyanın başlangıcı) ve ZIP dizin sonu dosyanın son 64 KB'ında aranır.
     */
    private fun trailing(b: ByteArray, from: Int): Verdict {
        if (from >= b.size) return Verdict.CLEAN
        // Ekin başında çalıştırılabilir (MZ, ELF…) ya da arşiv
        if (from + 1 < b.size && b[from] == 'M'.code.toByte() && b[from + 1] == 'Z'.code.toByte()) return Verdict.SUSPICIOUS
        val head = minOf(b.size, from + 64)
        for (sig in BIN_SIGNS) if (find(b, from, head, sig)) return Verdict.SUSPICIOUS
        if (find(b, maxOf(from, b.size - 65_557), b.size, ZIP_EOCD)) return Verdict.SUSPICIOUS
        return if (suspicious(b, from, b.size - from)) Verdict.SUSPICIOUS else Verdict.CLEAN
    }

    private val ZIP_EOCD = byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 5, 6)

    /** İkili imzaların her yerde arandığı en büyük bölüm (üst veri parçaları); büyüğünde yalnız metin imzaları. */
    private const val BIN_SCAN_MAX = 256 * 1024

    /** [b] içinde [off]'tan [len] baytta metin ya da ikili kod imzası var mı. */
    fun suspicious(b: ByteArray, off: Int, len: Int): Boolean {
        if (len <= 0) return false
        val end = minOf(b.size, off + len)
        if (end - off <= BIN_SCAN_MAX) for (sig in BIN_SIGNS) if (find(b, off, end, sig)) return true
        // Küçük harfli ASCII görünümü (yalnız harfler küçültülür)
        val lower = ByteArray(end - off) { i -> val c = b[off + i]; if (c in 'A'.code.toByte()..'Z'.code.toByte()) (c + 32).toByte() else c }
        for (sig in TEXT_SIGNS) if (find(lower, 0, lower.size, sig.toByteArray(Charsets.US_ASCII))) return true
        return false
    }

    private fun find(b: ByteArray, from: Int, end: Int, sig: ByteArray): Boolean {
        val last = end - sig.size
        var i = from
        val first = sig[0]
        while (i <= last) {
            if (b[i] == first) {
                var k = 1
                while (k < sig.size && b[i + k] == sig[k]) k++
                if (k == sig.size) return true
            }
            i++
        }
        return false
    }

    private fun inflate(b: ByteArray, off: Int, len: Int): ByteArray? {
        if (len <= 0) return ByteArray(0)
        val inf = Inflater()
        return try {
            inf.setInput(b, off, len)
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(8192)
            while (!inf.finished()) {
                val n = inf.inflate(buf)
                if (n == 0) { if (inf.needsInput() || inf.needsDictionary()) break }
                out.write(buf, 0, n)
                if (out.size() > MAX_INFLATE) break
            }
            out.toByteArray()
        } catch (_: Exception) {
            null
        } finally {
            inf.end()
        }
    }

    private fun indexOf0(b: ByteArray, from: Int, end: Int): Int {
        for (i in from until minOf(end, b.size)) if (b[i].toInt() == 0) return i
        return -1
    }

    private fun be32(b: ByteArray, at: Int): Int =
        ((b[at].toInt() and 0xFF) shl 24) or ((b[at + 1].toInt() and 0xFF) shl 16) or ((b[at + 2].toInt() and 0xFF) shl 8) or (b[at + 3].toInt() and 0xFF)

    private fun be16(b: ByteArray, at: Int): Int = ((b[at].toInt() and 0xFF) shl 8) or (b[at + 1].toInt() and 0xFF)

    private fun le32(b: ByteArray, at: Int): Int =
        (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8) or ((b[at + 2].toInt() and 0xFF) shl 16) or ((b[at + 3].toInt() and 0xFF) shl 24)
}
