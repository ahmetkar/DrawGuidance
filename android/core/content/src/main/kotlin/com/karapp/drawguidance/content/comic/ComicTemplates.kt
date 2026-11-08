package com.karapp.drawguidance.content.comic

/** v36 şablon grupları. */
enum class TemplateGroup(val id: String, val title: String) {
    BASIC("basic", "Temel"),
    WEBTOON("webtoon", "Webtoon"),
    MANGA("manga", "Manga"),
    GENRE("genre", "Türler"),
}

/**
 * v36 çizgi roman sayfa şablonu: panel düzeni ([page]), adı ve grubu. [line] panel çizgisinin kalınlık çarpanı
 * (korkuda kalın). Şablonlar özgün düzenlerdir (yaygın sayfa kurgularından esinlenen geometri; görsel içermez).
 */
class ComicTemplate(val id: String, val title: String, val group: TemplateGroup, val tags: String, val page: PanelPage, val line: Float = 1f) {
    val panelCount: Int get() = page.leafIds().size
}

object ComicTemplates {

    /** Kimlikleri sırayla veren küçük kurucu (ağaçtaki her panel ayrı kimlik). */
    private class B {
        var next = 0
        fun p(round: Float = 0f, bevel: Float = 0f) = PanelNode.Leaf(next++, round, bevel)
        val e = PanelNode.Space
        fun R(vararg w: Float, gap: Float = 1f, skews: FloatArray? = null, c: () -> List<PanelNode>) = split(true, w, gap, skews, c())
        fun C(vararg w: Float, gap: Float = 1f, skews: FloatArray? = null, c: () -> List<PanelNode>) = split(false, w, gap, skews, c())
        private fun split(rows: Boolean, w: FloatArray, gap: Float, skews: FloatArray?, children: List<PanelNode>): PanelNode.Split {
            require(w.size == children.size)
            return PanelNode.Split(rows, children, w.toList(), skews?.toList() ?: List(children.size - 1) { 0f }, gap)
        }
    }

    private fun t(
        id: String, title: String, group: TemplateGroup, tags: String,
        margin: Float = PanelPage.DEFAULT_MARGIN, gutter: Float = PanelPage.DEFAULT_GUTTER, rtl: Boolean = false, line: Float = 1f,
        insets: List<PanelInset> = emptyList(), root: B.() -> PanelNode,
    ) = ComicTemplate(id, title, group, tags, PanelPage(B().root(), margin, gutter, rtl, insets), line)

    val all: List<ComicTemplate> = listOf(
        // Temel düzenler
        t("single", "Tek büyük panel", TemplateGroup.BASIC, "tek sayfa kapak splash") { p() },
        t("two_rows", "İki panel (üst üste)", TemplateGroup.BASIC, "iki yatay") { R(1f, 1f) { listOf(p(), p()) } },
        t("two_cols", "İki panel (yan yana)", TemplateGroup.BASIC, "iki dikey sütun") { C(1f, 1f) { listOf(p(), p()) } },
        t("three_rows", "Üç panel (şerit)", TemplateGroup.BASIC, "üç yatay şerit") { R(1f, 1f, 1f) { listOf(p(), p(), p()) } },
        t("three_big", "Üç panel (büyük üstte)", TemplateGroup.BASIC, "üç büyük") { R(1.4f, 1f) { listOf(p(), C(1f, 1f) { listOf(p(), p()) }) } },
        // v37 eski "Panel düzeni"ndeki "İki + büyük" şablonlara katıldı
        t("three_big_bottom", "Üç panel (büyük altta)", TemplateGroup.BASIC, "üç büyük alt") { R(1f, 1.4f) { listOf(C(1f, 1f) { listOf(p(), p()) }, p()) } },
        t("three_cols", "Üç panel (gazete şeridi)", TemplateGroup.BASIC, "üç sütun gazete şerit") { C(1f, 1f, 1f) { listOf(p(), p(), p()) } },
        t("four_grid", "Dört panel (ızgara)", TemplateGroup.BASIC, "dört ızgara kare") { R(1f, 1f) { listOf(C(1f, 1f) { listOf(p(), p()) }, C(1f, 1f) { listOf(p(), p()) }) } },
        t("four_mix", "Dört panel (geniş alt)", TemplateGroup.BASIC, "dört geniş") {
            R(1f, 1f, 1.25f) { listOf(p(), C(1f, 1f) { listOf(p(), p()) }, p()) }
        },
        t("six_grid", "Altı panel (ızgara)", TemplateGroup.BASIC, "altı ızgara") {
            R(1f, 1f, 1f) { List(3) { C(1f, 1f) { listOf(p(), p()) } } }
        },
        t("six_mix", "Altı panel (karışık)", TemplateGroup.BASIC, "altı karışık") {
            R(1f, 1.25f, 1f) { listOf(C(1f, 1f, 1f) { listOf(p(), p(), p()) }, p(), C(1.4f, 1f) { listOf(p(), p()) }) }
        },
        // Dikey webtoon: tam genişlik, geniş dikey aralıklar, kaydırılmış paneller
        t("webtoon", "Dikey webtoon", TemplateGroup.WEBTOON, "webtoon dikey kaydırma telefon", margin = 0.025f, gutter = 0.02f) {
            R(1f, 1.6f, 0.8f, 1.3f, gap = 3.5f) { listOf(p(), p(), C(0.3f, 1f) { listOf(e, p()) }, p()) }
        },
        t("webtoon_scene", "Webtoon geniş sahne", TemplateGroup.WEBTOON, "webtoon sahne dikey", margin = 0.025f, gutter = 0.02f) {
            R(0.9f, 1.8f, 0.9f, gap = 3f) { listOf(C(1f, 0.35f) { listOf(p(), e) }, p(), C(0.35f, 1f) { listOf(e, p()) }) }
        },
        t("webtoon_flow", "Webtoon akış", TemplateGroup.WEBTOON, "webtoon akış dikey", margin = 0.025f, gutter = 0.02f) {
            R(1f, 0.8f, 1.2f, 0.9f, gap = 2.5f) { listOf(p(), C(0.2f, 1f, 0.2f) { listOf(e, p(), e) }, p(), C(1f, 1f) { listOf(p(), p()) }) }
        },
        // Manga: sağdan sola okunur
        t("manga", "Manga klasik", TemplateGroup.MANGA, "manga sağdan sola", rtl = true) {
            R(0.9f, 1.1f, 1f) { listOf(p(), C(1.6f, 1f) { listOf(p(), p()) }, C(1f, 1.6f) { listOf(p(), p()) }) }
        },
        t("manga_action", "Manga aksiyon", TemplateGroup.MANGA, "manga aksiyon eğik", rtl = true) {
            R(1f, 1.3f, 1f, skews = floatArrayOf(0.04f, -0.05f)) {
                listOf(C(1f, 1f, skews = floatArrayOf(0.12f)) { listOf(p(), p()) }, p(), C(1f, 1f, 1f, skews = floatArrayOf(0.1f, -0.1f)) { listOf(p(), p(), p()) })
            }
        },
        t("yonkoma", "4-koma (yonkoma)", TemplateGroup.MANGA, "manga yonkoma 4 koma dört şerit", rtl = true, gutter = 0.03f) {
            C(1f, 1f, gap = 1.6f) { List(2) { R(1f, 1f, 1f, 1f) { List(4) { p() } } } }
        },
        // Türlere göre sayfa düzenleri
        t("action", "Aksiyon", TemplateGroup.GENRE, "aksiyon dövüş eğik darbe") {
            R(1f, 1.6f, 0.9f, skews = floatArrayOf(0.06f, -0.08f)) {
                listOf(C(1f, 1f, skews = floatArrayOf(0.2f)) { listOf(p(), p()) }, p(), C(1f, 1f, 1f, skews = floatArrayOf(-0.15f, 0.15f)) { listOf(p(), p(), p()) })
            }
        },
        t("comedy", "Komedi", TemplateGroup.GENRE, "komedi mizah espri ritim") {
            R(1f, 1f, 1.1f) { listOf(C(1f, 1f) { listOf(p(), p()) }, C(1f, 1f) { listOf(p(), p()) }, p()) }
        },
        t(
            "romance", "Romantizm", TemplateGroup.GENRE, "romantizm aşk yumuşak", gutter = 0.03f,
            insets = listOf(PanelInset(3, 0.6f, 0.3f, 0.95f, 0.56f, oval = true, after = 0)),
        ) {
            R(1.6f, 1f) { listOf(p(round = 0.03f), C(1f, 1f) { listOf(p(round = 0.03f), p(round = 0.03f)) }) }
        },
        t("horror", "Korku", TemplateGroup.GENRE, "korku gerilim karanlık", margin = 0.06f, line = 1.6f) {
            R(1.1f, 1.4f, skews = floatArrayOf(0.03f)) { listOf(C(1f, 0.5f, 1f, skews = floatArrayOf(0.04f, -0.04f)) { listOf(p(), p(), p()) }, p()) }
        },
        t("fantasy", "Fantastik", TemplateGroup.GENRE, "fantastik büyü macera sahne") {
            R(1.8f, 1f) { listOf(p(bevel = 0.015f), C(1f, 1.3f, 1f) { listOf(p(), p(), p()) }) }
        },
        t("scifi", "Bilim kurgu", TemplateGroup.GENRE, "bilim kurgu uzay gelecek sinematik") {
            R(0.7f, 0.7f, 1.2f, 0.7f) { listOf(p(bevel = 0.03f), p(bevel = 0.03f), C(1f, 1f) { listOf(p(bevel = 0.03f), p(bevel = 0.03f)) }, p(bevel = 0.03f)) }
        },
    )

    private val byId = all.associateBy { it.id }

    fun byId(id: String): ComicTemplate? = byId[id]

    /** Gruptaki şablonlar (null: hepsi). */
    fun inGroup(group: TemplateGroup?): List<ComicTemplate> = if (group == null) all else all.filter { it.group == group }
}
