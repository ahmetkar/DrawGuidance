package com.karapp.drawguidance.content.paper

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * v23 hazır arka plan desenleri: not tuvali için 30, resim tuvali için 30, yazı kutusu (not defteri parçası) için 10.
 * Ölçüler tuval birimidir (A4 not sayfası 1240×1754: 1 mm ≈ 5,9 birim). Desen rengi kullanıcı tarafından seçilir; açık
 * ve koyu tonları ondan türetilir, böylece her renkte uyumlu kalır. Uygulama ilk açılışta desenleri sıkıştırarak
 * veritabanına yazar ve oradan okur.
 */
object PaperCatalog {

    const val VERSION = 1

    private val SQ3 = sqrt(3f)

    private fun p(
        id: String, name: String, usage: PaperUsage, tw: Float, th: Float,
        color: Int = 0xFF7C89A6.toInt(), accent: Int = 0xFFE57373.toInt(),
        inset: FloatArray = floatArrayOf(0f, 0f, 0f, 0f), tint: Int = 0, tintAlpha: Float = 0f,
        page: (PatternBuilder.() -> Unit)? = null,
        tile: PatternBuilder.() -> Unit,
    ): PaperPattern {
        val t = PatternBuilder().apply(tile).ops
        val pg = page?.let { PatternBuilder().apply(it).ops } ?: emptyList()
        return PaperPattern(id, name, usage, tw, th, t, pg, inset, color, accent, tint, tintAlpha)
    }

    private const val BLUE = 0xFF7C9CC9.toInt()
    private const val GREY = 0xFF8C95A3.toInt()
    private const val GREEN = 0xFF6FA287.toInt()
    private const val ORANGE = 0xFFD9935B.toInt()
    private const val WIDE = 2400f

    private fun PatternBuilder.ruled(h: Float, alpha: Float = 0.55f, w: Float = 1.6f, dash: Float = 0f) =
        line(0f, h - 1f, WIDE, h - 1f, w, 0, alpha, dash)

    private fun PatternBuilder.grid(s: Float, alpha: Float, w: Float = 1f) {
        line(0f, 0f, s, 0f, w, 0, alpha); line(0f, 0f, 0f, s, w, 0, alpha)
    }

    /** Sivri tepeli altıgen ızgaranın karosu: iki merkez, her merkezde yalnız kendi 3 kenarı (çift çizilmez). */
    private fun PatternBuilder.hexEdges(s: Float, w: Float, alpha: Float, slot: Int = 0, fillSlot: Int = -1, fillAlpha: Float = 0f) {
        val hw = SQ3 * s
        val centers = listOf(0f to 0f, hw / 2f to 1.5f * s)
        if (fillSlot >= 0) centers.forEach { (cx, cy) ->
            val pts = (0 until 6).map { k -> val a = Math.toRadians(30.0 + 60.0 * k); (cx + s * cos(a)).toFloat() to (cy + s * sin(a)).toFloat() }
            polyline(pts, fill = true, closed = true, slot = fillSlot, alpha = fillAlpha)
        }
        centers.forEach { (cx, cy) ->
            for (k in 0 until 3) {
                val a1 = Math.toRadians(30.0 + 60.0 * k); val a2 = Math.toRadians(30.0 + 60.0 * (k + 1))
                line((cx + s * cos(a1)).toFloat(), (cy + s * sin(a1)).toFloat(), (cx + s * cos(a2)).toFloat(), (cy + s * sin(a2)).toFloat(), w, slot, alpha)
            }
        }
    }

    // ------------------------------------------------------------------------------------------ not tuvali (30)

    val note: List<PaperPattern> by lazy {
        val n = PaperUsage.NOTE
        listOf(
            p("note.ruled_wide", "Geniş çizgili", n, WIDE, 52f, BLUE, inset = floatArrayOf(0f, 0.07f, 0f, 0.02f),
                page = { line(0.1f, 0f, 0.1f, 1f, 2f, PatOp.SLOT_ACCENT, 0.7f) }) { ruled(52f) },
            p("note.ruled_college", "Dar çizgili", n, WIDE, 42f, BLUE, inset = floatArrayOf(0f, 0.07f, 0f, 0.02f),
                page = { line(0.1f, 0f, 0.1f, 1f, 2f, PatOp.SLOT_ACCENT, 0.7f) }) { ruled(42f) },
            p("note.ruled_plain", "Kenarsız çizgili", n, WIDE, 48f, BLUE, inset = floatArrayOf(0f, 0.03f, 0f, 0f)) { ruled(48f) },
            p("note.grid5", "Kareli (5 mm)", n, 30f, 30f, GREY) { grid(30f, 0.35f) },
            p("note.grid10", "Kareli (1 cm)", n, 60f, 60f, GREY) { grid(60f, 0.45f, 1.3f) },
            p("note.millimeter", "Milimetrik", n, 60f, 60f, ORANGE) {
                for (k in 1 until 10) { val v = k * 6f; val a = if (k == 5) 0.32f else 0.16f; val w = if (k == 5) 0.9f else 0.6f; line(0f, v, 60f, v, w, 0, a); line(v, 0f, v, 60f, w, 0, a) }
                grid(60f, 0.5f, 1.3f)
            },
            p("note.dots", "Noktalı", n, 30f, 30f, GREY) { circle(0f, 0f, 1.7f, alpha = 0.6f) },
            p("note.dots_dense", "Sık noktalı", n, 20f, 20f, GREY) { circle(0f, 0f, 1.25f, alpha = 0.55f) },
            p("note.iso_dots", "İzometrik noktalı", n, 40f, 40f * SQ3, GREY) {
                circle(0f, 0f, 1.7f, alpha = 0.6f); circle(20f, 20f * SQ3, 1.7f, alpha = 0.6f)
            },
            p("note.iso_grid", "Üçgen ızgara", n, 40f, 40f * SQ3, GREY) {
                val h = 20f * SQ3; val a = 0.3f
                line(0f, 0f, 40f, 0f, 1f, 0, a); line(0f, h, 40f, h, 1f, 0, a)
                line(0f, 0f, 40f, 2 * h, 1f, 0, a); line(40f, 0f, 0f, 2 * h, 1f, 0, a)
                line(20f, 0f, 40f, h, 1f, 0, a); line(0f, h, 20f, 2 * h, 1f, 0, a)
                line(20f, 0f, 0f, h, 1f, 0, a); line(40f, h, 20f, 2 * h, 1f, 0, a)
            },
            p("note.hex", "Altıgen ızgara", n, SQ3 * 22f, 66f, GREY) { hexEdges(22f, 1f, 0.35f) },
            p("note.cornell", "Cornell not düzeni", n, WIDE, 44f, BLUE, inset = floatArrayOf(0f, 0.09f, 0f, 0.2f),
                page = {
                    line(0f, 0.08f, 1f, 0.08f, 2.4f, 0, 0.75f); line(0.3f, 0.08f, 0.3f, 0.8f, 2.4f, PatOp.SLOT_ACCENT, 0.75f)
                    line(0f, 0.8f, 1f, 0.8f, 2.4f, 0, 0.75f)
                }) { ruled(44f, 0.45f) },
            p("note.music", "Müzik portesi", n, WIDE, 150f, GREY, inset = floatArrayOf(0f, 0.04f, 0f, 0.02f)) {
                for (k in 0 until 5) line(0f, 30f + k * 13f, WIDE, 30f + k * 13f, 1.3f, 0, 0.7f)
            },
            p("note.handwriting", "Güzel yazı (dört çizgi)", n, WIDE, 120f, BLUE, inset = floatArrayOf(0f, 0.04f, 0f, 0f)) {
                line(0f, 20f, WIDE, 20f, 1.2f, 0, 0.45f); line(0f, 50f, WIDE, 50f, 1.2f, 0, 0.45f, dash = 10f)
                line(0f, 80f, WIDE, 80f, 1.9f, PatOp.SLOT_ACCENT, 0.7f); line(0f, 110f, WIDE, 110f, 1.2f, 0, 0.45f)
            },
            p("note.slant", "Eğik yazı kılavuzu", n, 42f, 60f, GREY) {
                line(0f, 0f, 42f, 0f, 1f, 0, 0.4f); line(0f, 60f, 42f, 0f, 0.9f, 0, 0.25f)
            },
            p("note.todo", "Yapılacaklar listesi", n, WIDE, 56f, BLUE, inset = floatArrayOf(0f, 0.06f, 0f, 0.02f)) {
                ruled(56f, 0.4f); rect(46f, 18f, 22f, 22f, fill = false, width = 1.8f, alpha = 0.7f)
            },
            p("note.weekly", "Haftalık plan", n, 0f, 0f, BLUE, page = {
                rect(0.06f, 0.05f, 0.88f, 0.9f, fill = false, width = 2.2f, alpha = 0.7f)
                for (k in 1 until 7) { val y = 0.05f + 0.9f * k / 7f; line(0.06f, y, 0.94f, y, 1.6f, 0, 0.55f) }
                line(0.2f, 0.05f, 0.2f, 0.95f, 1.6f, PatOp.SLOT_ACCENT, 0.6f)
            }) {},
            p("note.daily", "Günlük plan (saatli)", n, WIDE, 64f, BLUE, inset = floatArrayOf(0f, 0.08f, 0f, 0.03f),
                page = { line(0.16f, 0.06f, 0.16f, 0.98f, 2f, PatOp.SLOT_ACCENT, 0.7f); line(0f, 0.06f, 1f, 0.06f, 2.2f, 0, 0.7f) }) {
                ruled(64f, 0.5f); line(0f, 31f, WIDE, 31f, 0.9f, 0, 0.25f, dash = 8f)
            },
            p("note.storyboard", "Hikaye tahtası", n, 0f, 0f, GREY, page = {
                for (r in 0 until 3) for (c in 0 until 2) {
                    val x = 0.06f + c * 0.46f; val y = 0.05f + r * 0.31f
                    rect(x, y, 0.42f, 0.2f, fill = false, width = 2.2f, alpha = 0.75f)
                    line(x, y + 0.245f, x + 0.42f, y + 0.245f, 1.3f, 0, 0.45f)
                    line(x, y + 0.28f, x + 0.42f, y + 0.28f, 1.3f, 0, 0.45f)
                }
            }) {},
            p("note.story", "Resimli hikaye kağıdı", n, WIDE, 70f, BLUE, inset = floatArrayOf(0f, 0.52f, 0f, 0.02f),
                page = { rect(0.06f, 0.05f, 0.88f, 0.43f, fill = false, width = 2.2f, alpha = 0.7f) }) {
                ruled(70f, 0.55f); line(0f, 34f, WIDE, 34f, 1f, 0, 0.35f, dash = 9f)
            },
            p("note.math", "Matematik defteri", n, 25f, 25f, GREY, page = { line(0.09f, 0f, 0.09f, 1f, 2f, PatOp.SLOT_ACCENT, 0.7f) }) { grid(25f, 0.3f, 0.9f) },
            p("note.engineering", "Mühendislik kağıdı", n, 60f, 60f, GREEN) {
                for (k in 1 until 5) { val v = k * 12f; line(0f, v, 60f, v, 0.7f, 0, 0.25f); line(v, 0f, v, 60f, 0.7f, 0, 0.25f) }
                grid(60f, 0.55f, 1.4f)
            },
            p("note.polar", "Kutupsal ızgara", n, 0f, 0f, GREY, page = {
                for (k in 1..9) circle(0.5f, 0.5f, 0.05f * k, fill = false, width = if (k % 3 == 0) 1.6f else 0.9f, alpha = if (k % 3 == 0) 0.55f else 0.3f, centered = true)
                for (k in 0 until 24) {
                    val a = 2 * PI * k / 24
                    line(0.5f, 0.5f, (0.5 + 0.45 * cos(a)).toFloat(), (0.5 + 0.45 * sin(a)).toFloat(), if (k % 6 == 0) 1.4f else 0.8f, 0, if (k % 6 == 0) 0.55f else 0.28f, centered = true)
                }
            }) {},
            p("note.semilog", "Yarı logaritmik", n, 60f, 300f, ORANGE) {
                line(0f, 0f, 0f, 300f, 1f, 0, 0.35f)
                for (k in 1..9) { val y = 300f * (1f - kotlin.math.log10(k.toFloat())); line(0f, y, 60f, y, if (k == 1) 1.4f else 0.8f, 0, if (k == 1) 0.6f else 0.3f) }
            },
            p("note.crossdots", "Artı noktalı", n, 30f, 30f, GREY) {
                line(11f, 15f, 19f, 15f, 1.1f, 0, 0.55f); line(15f, 11f, 15f, 19f, 1.1f, 0, 0.55f)
            },
            p("note.dashed", "Kesik çizgili", n, WIDE, 46f, BLUE, inset = floatArrayOf(0f, 0.05f, 0f, 0.02f)) { ruled(46f, 0.5f, 1.4f, dash = 10f) },
            p("note.twocol", "İki sütunlu", n, WIDE, 44f, BLUE, inset = floatArrayOf(0f, 0.06f, 0f, 0.02f),
                page = { line(0.5f, 0.04f, 0.5f, 0.98f, 2f, PatOp.SLOT_ACCENT, 0.65f) }) { ruled(44f, 0.5f) },
            p("note.legal", "Sarı hukuk defteri", n, WIDE, 44f, 0xFF6F8FC6.toInt(), inset = floatArrayOf(0f, 0.08f, 0f, 0.02f),
                tint = 0xFFF7E58C.toInt(), tintAlpha = 0.55f,
                page = { line(0.12f, 0f, 0.12f, 1f, 1.8f, PatOp.SLOT_ACCENT, 0.75f); line(0.128f, 0f, 0.128f, 1f, 1.8f, PatOp.SLOT_ACCENT, 0.75f) }) { ruled(44f, 0.6f) },
            p("note.tianzige", "Hat ızgarası (田)", n, 90f, 90f, 0xFFC0605A.toInt()) {
                rect(0f, 0f, 90f, 90f, fill = false, width = 1.5f, alpha = 0.6f)
                line(45f, 0f, 45f, 90f, 1f, 0, 0.4f, dash = 6f); line(0f, 45f, 90f, 45f, 1f, 0, 0.4f, dash = 6f)
                line(0f, 0f, 90f, 90f, 0.8f, 0, 0.25f, dash = 5f); line(90f, 0f, 0f, 90f, 0.8f, 0, 0.25f, dash = 5f)
            },
            p("note.genko", "Kareli yazı (genkō)", n, 50f, 66f, 0xFF7FA37F.toInt()) {
                line(0f, 0f, 50f, 0f, 1.2f, 0, 0.55f); line(0f, 50f, 50f, 50f, 1.2f, 0, 0.55f); line(0f, 0f, 0f, 50f, 1.2f, 0, 0.55f)
            },
        )
    }

    // ------------------------------------------------------------------------------------------ resim tuvali (30)

    val art: List<PaperPattern> by lazy {
        val a = PaperUsage.ART
        val pink = 0xFFE8829B.toInt(); val teal = 0xFF4FA3A5.toInt(); val gold = 0xFFE0B04E.toInt(); val navy = 0xFF3F5E8C.toInt()
        val coral = 0xFFEF8354.toInt(); val leaf = 0xFF6BA368.toInt(); val plum = 0xFF8E6BBF.toInt(); val sky = 0xFF7DB7E0.toInt()
        listOf(
            p("art.polka", "Puantiye", a, 60f, 60f, pink) { circle(15f, 15f, 9f); circle(45f, 45f, 9f) },
            p("art.polka_small", "Küçük puantiye", a, 30f, 30f, navy) { circle(15f, 15f, 4f, alpha = 0.85f) },
            p("art.stripes_diag", "Çapraz şerit", a, 40f, 40f, teal) {
                poly(0f, 0f, 14f, 0f, 40f, 26f, 40f, 40f); poly(0f, 26f, 14f, 40f, 0f, 40f)
            },
            p("art.stripes_h", "Yatay şerit", a, 100f, 40f, coral) { rect(0f, 0f, 100f, 16f, alpha = 0.85f) },
            p("art.gingham", "Pötikare", a, 40f, 40f, 0xFFD0525F.toInt()) { rect(0f, 0f, 20f, 40f, alpha = 0.35f); rect(0f, 0f, 40f, 20f, alpha = 0.35f) },
            p("art.tartan", "Ekose", a, 80f, 80f, 0xFF2F6B47.toInt()) {
                rect(0f, 0f, 80f, 16f, alpha = 0.45f); rect(0f, 0f, 16f, 80f, alpha = 0.45f)
                rect(0f, 48f, 80f, 8f, slot = PatOp.SLOT_ACCENT, alpha = 0.45f); rect(48f, 0f, 8f, 80f, slot = PatOp.SLOT_ACCENT, alpha = 0.45f)
                line(0f, 34f, 80f, 34f, 2f, PatOp.SLOT_DARK, 0.6f); line(34f, 0f, 34f, 80f, 2f, PatOp.SLOT_DARK, 0.6f)
            },
            p("art.chevron", "Zikzak", a, 60f, 40f, gold) { poly(0f, 0f, 30f, 20f, 60f, 0f, 60f, 14f, 30f, 34f, 0f, 14f) },
            p("art.waves", "Dalga", a, 80f, 40f, sky) {
                polyline((0..32).map { k -> val x = 80f * k / 32; x to (20f + 8f * sin(2 * PI * k / 32).toFloat()) }, width = 3.5f)
            },
            p("art.scales", "Balık pulu", a, 40f, 20f, teal) {
                arc(20f, 0f, 20f, 0f, 180f, w = 2.2f); arc(0f, 10f, 20f, 0f, 180f, w = 2.2f); arc(40f, 10f, 20f, 0f, 180f, w = 2.2f)
            },
            p("art.honeycomb", "Bal peteği", a, SQ3 * 26f, 78f, gold) { hexEdges(26f, 2.6f, 0.9f, PatOp.SLOT_DARK, PatOp.SLOT_LIGHT, 0.55f) },
            p("art.argyle", "Baklava", a, 60f, 80f, plum) {
                poly(30f, 0f, 60f, 40f, 30f, 80f, 0f, 40f, slot = PatOp.SLOT_LIGHT, alpha = 0.8f)
                line(0f, 0f, 60f, 80f, 1.4f, PatOp.SLOT_DARK, 0.7f, dash = 6f); line(60f, 0f, 0f, 80f, 1.4f, PatOp.SLOT_DARK, 0.7f, dash = 6f)
            },
            p("art.stars", "Yıldızlar", a, 70f, 70f, gold) { star(20f, 20f, 11f); star(54f, 52f, 6.5f, slot = PatOp.SLOT_LIGHT) },
            p("art.hearts", "Kalpler", a, 70f, 70f, pink) { heart(20f, 22f, 20f); heart(54f, 54f, 12f, slot = PatOp.SLOT_LIGHT) },
            p("art.flowers", "Çiçekler", a, 80f, 80f, coral) {
                for (k in 0 until 5) { val an = 2 * PI * k / 5 - PI / 2; circle((24 + 9 * cos(an)).toFloat(), (24 + 9 * sin(an)).toFloat(), 7f, slot = PatOp.SLOT_LIGHT) }
                circle(24f, 24f, 5f, slot = PatOp.SLOT_DARK)
                for (k in 0 until 5) { val an = 2 * PI * k / 5 - PI / 2; circle((62 + 6 * cos(an)).toFloat(), (62 + 6 * sin(an)).toFloat(), 4.5f) }
                circle(62f, 62f, 3f, slot = PatOp.SLOT_ACCENT)
            },
            p("art.leaves", "Yapraklar", a, 80f, 80f, leaf) {
                fun leafAt(cx: Float, cy: Float, len: Float, ang: Double, slot: Int) {
                    val pts = (0..16).map { k ->
                        val t = k / 16.0; val s = sin(PI * t) * 0.32
                        val x = (t - 0.5) * len; val y = s * len
                        (cx + (x * cos(ang) - y * sin(ang)).toFloat()) to (cy + (x * sin(ang) + y * cos(ang)).toFloat())
                    } + (16 downTo 0).map { k ->
                        val t = k / 16.0; val s = -sin(PI * t) * 0.32
                        val x = (t - 0.5) * len; val y = s * len
                        (cx + (x * cos(ang) - y * sin(ang)).toFloat()) to (cy + (x * sin(ang) + y * cos(ang)).toFloat())
                    }
                    polyline(pts, fill = true, closed = true, slot = slot)
                    line((cx - len / 2 * cos(ang)).toFloat(), (cy - len / 2 * sin(ang)).toFloat(), (cx + len / 2 * cos(ang)).toFloat(), (cy + len / 2 * sin(ang)).toFloat(), 1.2f, PatOp.SLOT_DARK, 0.7f)
                }
                leafAt(22f, 24f, 30f, -0.6, 0); leafAt(60f, 58f, 24f, 0.9, PatOp.SLOT_LIGHT)
            },
            p("art.brick", "Tuğla örgü", a, 80f, 40f, 0xFFB5654A.toInt()) {
                line(0f, 0f, 80f, 0f, 2f, 0, 0.75f); line(0f, 20f, 80f, 20f, 2f, 0, 0.75f)
                line(0f, 0f, 0f, 20f, 2f, 0, 0.75f); line(40f, 20f, 40f, 40f, 2f, 0, 0.75f)
            },
            p("art.herringbone", "Balıksırtı", a, 40f, 40f, 0xFFA97B50.toInt()) {
                poly(0f, 10f, 20f, 0f, 20f, 10f, 0f, 20f, alpha = 0.85f); poly(0f, 30f, 20f, 20f, 20f, 30f, 0f, 40f, slot = PatOp.SLOT_LIGHT)
                poly(20f, 0f, 40f, 10f, 40f, 20f, 20f, 10f, slot = PatOp.SLOT_LIGHT); poly(20f, 20f, 40f, 30f, 40f, 40f, 20f, 30f, alpha = 0.85f)
            },
            p("art.basket", "Hasır örgü", a, 40f, 40f, 0xFFB08850.toInt()) {
                for (k in 1..3) {
                    val o = k * 5f
                    line(0f, o, 20f, o, 1.6f, 0, 0.8f); line(20f + o, 0f, 20f + o, 20f, 1.6f, 0, 0.8f)
                    line(o, 20f, o, 40f, 1.6f, 0, 0.8f); line(20f, 20f + o, 40f, 20f + o, 1.6f, 0, 0.8f)
                }
            },
            p("art.checker", "Dama", a, 40f, 40f, navy) { rect(0f, 0f, 20f, 20f, alpha = 0.8f); rect(20f, 20f, 20f, 20f, alpha = 0.8f) },
            p("art.confetti", "Konfeti", a, 120f, 120f, coral) {
                val rnd = java.util.Random(7)
                repeat(22) { k ->
                    val x = rnd.nextFloat() * 120f; val y = rnd.nextFloat() * 120f
                    val slot = listOf(0, PatOp.SLOT_LIGHT, PatOp.SLOT_DARK, PatOp.SLOT_ACCENT)[k % 4]
                    if (k % 3 == 0) circle(x, y, 2.5f + rnd.nextFloat() * 2f, slot = slot)
                    else {
                        val an = rnd.nextFloat() * PI; val l = 4f + rnd.nextFloat() * 4f; val w = 2f
                        val dx = (cos(an) * l).toFloat(); val dy = (sin(an) * l).toFloat(); val nx = (-sin(an) * w).toFloat(); val ny = (cos(an) * w).toFloat()
                        poly(x - dx - nx, y - dy - ny, x + dx - nx, y + dy - ny, x + dx + nx, y + dy + ny, x - dx + nx, y - dy + ny, slot = slot)
                    }
                }
            },
            p("art.seigaiha", "Japon dalgası", a, 60f, 30f, navy) {
                for ((cx, cy) in listOf(30f to 15f, 0f to 30f, 60f to 30f)) {
                    for ((i, r) in listOf(30f, 22f, 14f, 6f).withIndex()) {
                        arc(cx, cy, r, 180f, 360f, fill = true, slot = if (i % 2 == 0) PatOp.SLOT_PAPER else PatOp.SLOT_LIGHT, alpha = 1f)
                        arc(cx, cy, r, 180f, 360f, w = 1.8f, slot = 0, alpha = 0.9f)
                    }
                }
            },
            p("art.triangles", "Üçgen mozaik", a, 60f, 52f, teal) {
                poly(0f, 52f, 30f, 0f, 60f, 52f, alpha = 0.75f); poly(30f, 0f, 60f, 0f, 60f, 52f, slot = PatOp.SLOT_LIGHT)
                poly(0f, 0f, 30f, 0f, 0f, 52f, slot = PatOp.SLOT_LIGHT)
            },
            p("art.quatrefoil", "Dörtyaprak", a, 60f, 60f, plum) {
                for ((dx, dy) in listOf(11f to 0f, -11f to 0f, 0f to 11f, 0f to -11f)) circle(30f + dx, 30f + dy, 11f, fill = false, width = 2f, alpha = 0.85f)
                poly(0f, -6f, 6f, 0f, 0f, 6f, -6f, 0f, slot = PatOp.SLOT_DARK)
            },
            p("art.rings", "Halka zinciri", a, 50f, 50f, gold) { circle(0f, 0f, 22f, fill = false, width = 2.6f); circle(25f, 25f, 22f, fill = false, width = 2.6f, slot = PatOp.SLOT_DARK) },
            p("art.raindrops", "Yağmur damlaları", a, 60f, 60f, sky) {
                fun drop(cx: Float, cy: Float, s: Float, slot: Int) {
                    val pts = (0..20).map { k ->
                        val t = PI * 2 * k / 20
                        val x = sin(t) * (1 - cos(t)) * 0.5 * s
                        val y = -cos(t) * s
                        (cx + x.toFloat()) to (cy + y.toFloat())
                    }
                    polyline(pts, fill = true, closed = true, slot = slot)
                }
                drop(15f, 22f, 9f, 0); drop(45f, 50f, 7f, PatOp.SLOT_LIGHT)
            },
            p("art.clouds", "Bulutlar", a, 120f, 80f, sky) {
                circle(30f, 40f, 14f, slot = PatOp.SLOT_LIGHT); circle(46f, 32f, 17f, slot = PatOp.SLOT_LIGHT); circle(62f, 40f, 13f, slot = PatOp.SLOT_LIGHT)
                rect(30f, 40f, 32f, 13f, slot = PatOp.SLOT_LIGHT)
                circle(92f, 70f, 8f, slot = PatOp.SLOT_LIGHT); circle(101f, 65f, 10f, slot = PatOp.SLOT_LIGHT); circle(110f, 70f, 7f, slot = PatOp.SLOT_LIGHT)
            },
            p("art.snowflakes", "Kar taneleri", a, 80f, 80f, sky) {
                fun flake(cx: Float, cy: Float, r: Float) {
                    for (k in 0 until 6) {
                        val an = PI / 3 * k
                        val ex = (cx + r * cos(an)).toFloat(); val ey = (cy + r * sin(an)).toFloat()
                        line(cx, cy, ex, ey, 1.6f, 0, 0.85f)
                        val mx = (cx + r * 0.55 * cos(an)).toFloat(); val my = (cy + r * 0.55 * sin(an)).toFloat()
                        line(mx, my, (mx + r * 0.3 * cos(an + 0.7)).toFloat(), (my + r * 0.3 * sin(an + 0.7)).toFloat(), 1.2f, 0, 0.8f)
                        line(mx, my, (mx + r * 0.3 * cos(an - 0.7)).toFloat(), (my + r * 0.3 * sin(an - 0.7)).toFloat(), 1.2f, 0, 0.8f)
                    }
                }
                flake(20f, 20f, 11f); flake(60f, 58f, 7f)
            },
            p("art.moonstars", "Ay ve yıldızlar", a, 100f, 100f, gold) {
                circle(30f, 30f, 13f); circle(36f, 26f, 11f, slot = PatOp.SLOT_PAPER)
                star(75f, 22f, 5f); star(62f, 76f, 4f, slot = PatOp.SLOT_LIGHT); star(20f, 82f, 6f); circle(88f, 55f, 1.6f); circle(45f, 60f, 1.2f)
            },
            p("art.fan", "Art deco yelpaze", a, 60f, 30f, 0xFF2E6F73.toInt()) {
                for ((cx, cy) in listOf(30f to 15f, 0f to 30f, 60f to 30f)) {
                    arc(cx, cy, 28f, 180f, 360f, fill = true, slot = PatOp.SLOT_PAPER)
                    arc(cx, cy, 28f, 180f, 360f, w = 1.8f)
                    arc(cx, cy, 10f, 180f, 360f, w = 1.4f, slot = PatOp.SLOT_DARK)
                    for (k in 1 until 6) { val an = PI + PI * k / 6; line(cx + (10 * cos(an)).toFloat(), cy + (10 * sin(an)).toFloat(), cx + (28 * cos(an)).toFloat(), cy + (28 * sin(an)).toFloat(), 1.2f, 0, 0.8f) }
                }
            },
            p("art.terrazzo", "Mozaik taş", a, 140f, 140f, 0xFFC97B63.toInt()) {
                val rnd = java.util.Random(31)
                repeat(26) { k ->
                    val x = rnd.nextFloat() * 140f; val y = rnd.nextFloat() * 140f; val r = 2.5f + rnd.nextFloat() * 6f
                    val slot = listOf(0, PatOp.SLOT_LIGHT, PatOp.SLOT_DARK, PatOp.SLOT_ACCENT)[k % 4]
                    val pts = (0 until 5).map { j -> val an = 2 * PI * j / 5 + rnd.nextFloat(); val rr = r * (0.6f + 0.5f * rnd.nextFloat()); (x + (rr * cos(an)).toFloat()) to (y + (rr * sin(an)).toFloat()) }
                    polyline(pts, fill = true, closed = true, slot = slot, alpha = 0.85f)
                }
            },
        )
    }

    // ------------------------------------------------------------------------------------------ yazı kutusu (10)

    val box: List<PaperPattern> by lazy {
        val b = PaperUsage.BOX
        listOf(
            p("box.lined", "Çizgili not", b, WIDE, 34f, BLUE, page = { line(0.08f, 0f, 0.08f, 1f, 1.6f, PatOp.SLOT_ACCENT, 0.6f) }) { ruled(34f, 0.45f, 1.2f) },
            p("box.grid", "Kareli not", b, 24f, 24f, GREY) { grid(24f, 0.3f, 0.9f) },
            p("box.dots", "Noktalı not", b, 22f, 22f, GREY) { circle(11f, 11f, 1.3f, alpha = 0.5f) },
            p("box.gingham", "Pastel pötikare", b, 24f, 24f, 0xFFE89AA9.toInt()) { rect(0f, 0f, 12f, 24f, alpha = 0.16f); rect(0f, 0f, 24f, 12f, alpha = 0.16f) },
            p("box.stripes", "Pastel şerit", b, 40f, 40f, 0xFF8FC1D4.toInt()) { rect(0f, 0f, 14f, 40f, alpha = 0.22f) },
            p("box.polka", "Minik puantiye", b, 26f, 26f, 0xFFF0A35E.toInt()) { circle(6f, 6f, 2.8f, alpha = 0.32f); circle(19f, 19f, 2.8f, alpha = 0.32f) },
            p("box.washi", "Washi bantlı", b, 30f, 30f, 0xFF9FC5A8.toInt(), page = {
                rect(0f, 0f, 1f, 0.14f, slot = PatOp.SLOT_LIGHT, alpha = 0.85f)
                for (k in 0 until 12) line(k / 12f, 0f, k / 12f + 0.06f, 0.14f, 1.5f, 0, 0.35f)
            }) { circle(15f, 15f, 1.4f, alpha = 0.18f) },
            p("box.kraft", "Kraft benekli", b, 60f, 60f, 0xFF8A6A43.toInt(), tint = 0xFFC9A777.toInt(), tintAlpha = 0.35f) {
                val rnd = java.util.Random(5)
                repeat(9) { circle(rnd.nextFloat() * 60f, rnd.nextFloat() * 60f, 0.8f + rnd.nextFloat() * 1.2f, alpha = 0.35f) }
            },
            p("box.ticket", "Bilet / kupon", b, 0f, 0f, 0xFFD9935B.toInt(), page = {
                rect(0.04f, 0.08f, 0.92f, 0.84f, fill = false, width = 2f, alpha = 0.7f, dash = 9f)
                line(0.74f, 0.08f, 0.74f, 0.92f, 1.6f, 0, 0.6f, dash = 7f)
            }) {},
            p("box.index", "Dizin kartı", b, WIDE, 30f, BLUE, inset = floatArrayOf(0f, 0.26f, 0f, 0.02f),
                page = { line(0f, 0.2f, 1f, 0.2f, 2.2f, PatOp.SLOT_ACCENT, 0.75f) }) { ruled(30f, 0.4f, 1.1f) },
        )
    }

    val all: List<PaperPattern> by lazy { note + art + box }

    fun byId(id: String): PaperPattern? = all.firstOrNull { it.id == id }

    fun of(usage: PaperUsage): List<PaperPattern> = when (usage) {
        PaperUsage.NOTE -> note
        PaperUsage.ART -> art
        PaperUsage.BOX -> box
    }
}
