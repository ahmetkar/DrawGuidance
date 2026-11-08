package com.karapp.drawguidance.data.model

import kotlinx.serialization.Serializable

/** v40 sayfa yazılarının yatay hizası. */
@Serializable
enum class PageAlign { LEFT, CENTER, RIGHT }

/** v40 sayfa numarası biçimi: "3" ya da "3 / 12". */
@Serializable
enum class PageNumberFormat { NUMBER, NUMBER_OF_TOTAL }

/** v40 sayfa yazılarının boyu (sayfanın kısa kenarına oranı). */
@Serializable
enum class PageTextSize(val ratio: Float) { SMALL(0.016f), MEDIUM(0.021f), LARGE(0.028f) }

/**
 * v40 sayfa ayarları (not alma ve çizgi roman; tuval başına): her sayfanın altında (ya da üstünde) sayfa numarası,
 * sayfanın üstünde üst bilgi ve altında alt bilgi yazısı. Tuvalde, resim/PDF/CBZ çıktılarında aynı çizilir.
 * Kayıtta tuvalin arka plan JSON'unda "pageSettings" anahtarındadır (eski sürümler yok sayar).
 */
@Serializable
data class PageSettings(
    val pageNumbers: Boolean = false,
    val numberAlign: PageAlign = PageAlign.CENTER,
    val numberAtTop: Boolean = false,
    val numberFormat: PageNumberFormat = PageNumberFormat.NUMBER,
    val header: String = "",
    val headerAlign: PageAlign = PageAlign.CENTER,
    val headerSize: PageTextSize = PageTextSize.MEDIUM,
    val footer: String = "",
    val footerAlign: PageAlign = PageAlign.CENTER,
    val footerSize: PageTextSize = PageTextSize.MEDIUM,
) {
    val isEmpty: Boolean get() = !pageNumbers && header.isBlank() && footer.isBlank()

    /**
     * [page] (0'dan) sayfasının numara yazısı. v45 [cover] iken ilk sayfa kapaktır: kapaktan sonraki sayfa 1 olur,
     * toplam kapaksız sayfa sayısıdır (kapağın kendisi için "" döner).
     */
    fun numberText(page: Int, count: Int, cover: Boolean = false): String {
        val n = PageNumbering.number(page, cover) ?: return ""
        return when (numberFormat) {
            PageNumberFormat.NUMBER -> "$n"
            PageNumberFormat.NUMBER_OF_TOTAL -> "$n / ${PageNumbering.count(count, cover).coerceAtLeast(1)}"
        }
    }

    /** Bir sayfa yazısı satırı: [top] üst şeritte mi, hizası, boyu ve metni. */
    data class Line(val top: Boolean, val align: PageAlign, val size: PageTextSize, val text: String)

    /**
     * [page] sayfasında çizilecek satırlar. Numara üst/alt bilgiyle aynı şeritte ve aynı hizadaysa yazının yanına
     * eklenir (üst üste binmez). v45 [cover] iken ilk sayfa (kapak) boş kalır, numaralar ondan sonra 1'den başlar.
     */
    fun lines(page: Int, count: Int, cover: Boolean = false): List<Line> {
        if (isEmpty) return emptyList()
        // v45 kapak sayfası numara, üst bilgi ve alt bilgi almaz
        if (cover && page == 0) return emptyList()
        val out = ArrayList<Line>(3)
        val number = if (pageNumbers) numberText(page, count, cover) else null
        var numberUsed = number == null
        if (header.isNotBlank()) {
            val joined = if (!numberUsed && numberAtTop && numberAlign == headerAlign) { numberUsed = true; "${header.trim()}   $number" } else header.trim()
            out += Line(true, headerAlign, headerSize, joined)
        }
        if (footer.isNotBlank()) {
            val joined = if (!numberUsed && !numberAtTop && numberAlign == footerAlign) { numberUsed = true; "${footer.trim()}   $number" } else footer.trim()
            out += Line(false, footerAlign, footerSize, joined)
        }
        if (!numberUsed && number != null) {
            out += Line(numberAtTop, numberAlign, if (numberAtTop) headerSize else footerSize, number)
        }
        return out
    }
}

/**
 * v45 kapaklı çizgi romanda sayfa numaraları: kapak (ilk sayfa) numara almaz, sonraki sayfa 1'dir; toplamda kapak
 * sayılmaz. Kapaksız tuvallerde numara sayfa sırası + 1 (eski davranış).
 */
object PageNumbering {
    /** [page] (0'dan) sayfasının gösterilen numarası; kapaksa null. */
    fun number(page: Int, cover: Boolean): Int? = when {
        !cover -> page + 1
        page == 0 -> null
        else -> page
    }

    /** Numaralanan sayfa sayısı (kapak hariç). */
    fun count(pageCount: Int, cover: Boolean): Int = if (cover) (pageCount - 1).coerceAtLeast(0) else pageCount
}
