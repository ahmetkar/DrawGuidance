package com.karapp.drawguidance.ui.drawing

/**
 * Diyagram renk temaları: kutular sırayla temanın renklerini alır, kenar rengi dolgunun koyu tonudur (ya da temanın
 * kenar rengi), yazı ve ok renkleri temadan gelir. Adıma elle verilen renkler temadan önce gelir. Boş kimlik: klasik
 * (kalem rengiyle çizgiler, dolgusuz).
 *
 * v28 editoryal temalar (diagram-design tarzı): kağıt zemin, mürekkep çizgiler, tek vurgu rengi (1–2 öğe), bağlantı
 * mavisi; gölge yok, kutular rolüne göre (odak, depo, dış, güvenlik, göbek…) ayrışır, ok yazıları eşaralıklı büyük
 * harf. Yeni diyagramların varsayılanı "editorial".
 */
object DiagramThemes {

    /** Editoryal renk dizgesi (ARGB onaltılık). */
    class Editorial(
        val paper: String,
        val ink: String,
        val muted: String,
        val soft: String,
        val rule: String,
        val accent: String,
        val link: String,
        /** v29 saydam zemin: kağıt kartı ve ok yazısı maskeleri çizilmez, kutular dolgusuz (tuvalin rengi görünür). */
        val clear: Boolean = false,
    )

    class Theme(
        val id: String,
        val title: String,
        val fills: List<String>,
        val text: String?,
        val edge: String?,
        val stroke: String? = null,
        val shadow: Boolean = true,
        val editorial: Editorial? = null,
    ) {
        fun fill(index: Int): String? = if (fills.isEmpty() || editorial != null) null else fills[index % fills.size]
    }

    const val EDITORIAL = "editorial"
    const val EDITORIAL_DARK = "editorial_dark"
    /** v29 editoryal, saydam zeminli. */
    const val EDITORIAL_CLEAR = "editorial_clear"

    val editorialLight = Editorial(
        paper = "#FAF8F4", ink = "#1C1B19", muted = "#6E6A63", soft = "#EFEBE4", rule = "#D8D2C8",
        accent = "#D9572B", link = "#2F6BD8",
    )
    val editorialDark = Editorial(
        paper = "#171614", ink = "#EDEAE4", muted = "#A29D94", soft = "#24221F", rule = "#3D3934",
        accent = "#F0784A", link = "#6C9BF0",
    )

    val editorialClear = Editorial(
        paper = editorialLight.paper, ink = editorialLight.ink, muted = editorialLight.muted, soft = editorialLight.soft,
        rule = editorialLight.rule, accent = editorialLight.accent, link = editorialLight.link, clear = true,
    )

    val all: List<Theme> = listOf(
        Theme(
            EDITORIAL, "Editoryal", listOf(editorialLight.paper, editorialLight.soft, editorialLight.accent, editorialLight.link),
            editorialLight.ink, editorialLight.ink, stroke = editorialLight.ink, shadow = false, editorial = editorialLight,
        ),
        Theme(
            EDITORIAL_DARK, "Editoryal koyu", listOf(editorialDark.paper, editorialDark.soft, editorialDark.accent, editorialDark.link),
            editorialDark.ink, editorialDark.ink, stroke = editorialDark.rule, shadow = false, editorial = editorialDark,
        ),
        Theme(
            EDITORIAL_CLEAR, "Editoryal saydam", listOf("#00FFFFFF", editorialClear.ink, editorialClear.accent, editorialClear.link),
            editorialClear.ink, editorialClear.ink, stroke = editorialClear.ink, shadow = false, editorial = editorialClear,
        ),
        Theme("", "Klasik", emptyList(), null, null, shadow = false),
        Theme("pastel", "Pastel", listOf("#FFE0B2", "#C8E6C9", "#BBDEFB", "#F8BBD0", "#D1C4E9", "#FFF59D"), "#263238", "#546E7A"),
        Theme("vivid", "Canlı", listOf("#EF5350", "#42A5F5", "#66BB6A", "#FFA726", "#AB47BC", "#26A69A"), "#FFFFFF", "#37474F"),
        Theme("ocean", "Okyanus", listOf("#E1F5FE", "#B3E5FC", "#81D4FA", "#4FC3F7", "#29B6F6"), "#01579B", "#0277BD"),
        Theme("forest", "Orman", listOf("#E8F5E9", "#C8E6C9", "#A5D6A7", "#81C784", "#66BB6A"), "#1B5E20", "#2E7D32"),
        Theme("sunset", "Gün batımı", listOf("#FFF3E0", "#FFE0B2", "#FFCC80", "#FFAB91", "#F8BBD0"), "#BF360C", "#D84315"),
        Theme("chalk", "Tebeşir", listOf("#2F3E46", "#354F52", "#3D5A5E", "#52796F"), "#F4F1DE", "#2F3E46", stroke = "#F4F1DE"),
        Theme("mono", "Sade", listOf("#ECEFF1", "#CFD8DC", "#F5F5F5"), "#263238", "#455A64"),
    )

    fun find(id: String): Theme? = all.firstOrNull { it.id == id && it.id.isNotEmpty() } ?: clearVariant(id)

    /** Tema editoryal mi (kağıt zemin, rol temelli kutular). */
    fun isEditorial(id: String): Boolean = id == EDITORIAL || id == EDITORIAL_DARK || id == EDITORIAL_CLEAR || find(id)?.editorial != null

    // --- v40 zeminli / zeminsiz (saydam) diyagram ---

    /** Zeminsiz tema kimliklerinin eki ("pastel_clear", klasik için "_clear"; editoryalde [EDITORIAL_CLEAR]). */
    const val CLEAR_SUFFIX = "_clear"

    /** Diyagramın zemini (kağıt kartı ve kutu dolguları) var mı. */
    fun hasBackground(id: String): Boolean = !id.endsWith(CLEAR_SUFFIX)

    /** Zeminli temel tema kimliği. */
    fun baseOf(id: String): String = id.removeSuffix(CLEAR_SUFFIX)

    /** Temanın zeminli ya da zeminsiz kimliği (renkler aynı kalır). */
    fun withBackground(id: String, background: Boolean): String = if (background) baseOf(id) else baseOf(id) + CLEAR_SUFFIX

    private val clearCache = java.util.concurrent.ConcurrentHashMap<String, Theme>()

    /**
     * Zeminsiz tema: editoryalde kağıt kartı ve maskeler yok; renkli temalarda kutular dolgusuz, kenar ve yazı temanın
     * kenar rengiyle (dolgu rengi yazısı saydam zeminde kaybolmasın diye).
     */
    private fun clearVariant(id: String): Theme? {
        if (!id.endsWith(CLEAR_SUFFIX) || id == CLEAR_SUFFIX) return null
        clearCache[id]?.let { return it }
        val base = all.firstOrNull { it.id == baseOf(id) && it.id.isNotEmpty() } ?: return null
        val e = base.editorial
        val theme = if (e != null) {
            val clear = Editorial(e.paper, e.ink, e.muted, e.soft, e.rule, e.accent, e.link, clear = true)
            Theme(id, base.title, base.fills, base.text, base.edge, base.stroke, shadow = false, editorial = clear)
        } else {
            val line = base.stroke ?: base.edge ?: base.text
            Theme(id, base.title, emptyList(), line, base.edge ?: line, line, shadow = false)
        }
        clearCache[id] = theme
        return theme
    }
}
