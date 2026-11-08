package com.karapp.drawguidance.content.library.kids

import com.karapp.drawguidance.content.library.Audience
import com.karapp.drawguidance.content.library.Fit
import com.karapp.drawguidance.content.library.LibCategory
import com.karapp.drawguidance.content.library.LibShape
import com.karapp.drawguidance.content.vector.P
import com.karapp.drawguidance.content.vector.PathBuilder
import com.karapp.drawguidance.content.vector.VectorBuilder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * "Çocuklar için çizim" (v47: 6–9 yaş) boyama kitabı çizimleri: kalın, tek tip koyu kenar; her parça beyaz dolgulu kapalı
 * bir yol, böylece çocuk "Alan boya" ile her parçaya dokunup boyayabilir. Tüm çizimler özgündür (marka/karakter yok).
 *
 * Alan boya ([com.karapp.drawguidance.content.fill.RegionFill]) görünürlüğe bakmaz: bir parçanın dolgusunun altında
 * kalan kenar da sınır sayılır ve üstteki parçanın alanını ikiye böler. Bu yüzden üst üste binen parçalar birbirinden
 * kesilir ([KidPen.cut]: arkadaki parçadan öndeki çıkarılır) ya da öndeki parça arkadakinin tamamen içinde kalır
 * ([KidPen.clip] ile içine kırpılır). Sonuçta görünen her çizgi gerçek bir sınırdır, gizli çizgi kalmaz.
 */

/** Çocuk çizimlerinin kalem (kenar) rengi. */
internal const val KID_INK = "#2A2A33"

/** Parça dolgusu: boyanmayı bekleyen beyaz. */
internal const val KID_PAPER = "#FFFFFF"

/**
 * Kenar kalınlığı çarpanı (elemanın kalem kalınlığına göre): hazır çizimlerin ana kenarı 1×, iç ayrıntıları 0,5–0,8×;
 * çocuk çizimleri belirgin biçimde kalın, boyama kitabı çizgisi.
 */
internal const val KID_OUTLINE = 1.9f

/** Açık ayrıntı çizgileri (gülümseme, bıyık, damar). */
internal const val KID_DETAIL = 1.4f

internal typealias Poly = List<P>

/** Tasarım biriminde eğri örnekleme adımı (100 birimlik kutuda ~1 birim). */
private const val STEP = 1f

/**
 * Çocuk çizimi: [w]×[h] tasarım biriminde çizilir, en/boy oranı korunarak kutuya eşlenir. Kimlik "kid_<ad>".
 */
internal fun kid(name: String, title: String, w: Float, h: Float, tags: List<String>, body: KidPen.() -> Unit): LibShape =
    LibShape("kid_$name", title, Fit.BOX, aspect = w / h, tags = tags + listOf("çocuk", "boyama", "kids", "coloring")) {
        // v47 6–9 yaş: büyük parçaların içine boyanacak desen (benek, şerit, dalga, yıldız, kare) eklenir
        inBox(0f, 0f, 1f / w, 1f / h, 0f) { KidPen(this).apply { body() }.flush(KidDetail.pattern(name, tags), w, h, name) }
    }

/**
 * Çocuk çizimi kalemi: biçimleri nokta listesi olarak üretir (birbirinden kesilebilsin diye), sonra parça olarak çizer.
 * Kesilen parçaların ortak sınırı aynı noktalardan geçer: iki çizgi üst üste düşer, arada ince şerit kalmaz.
 */
internal class KidPen(private val v: VectorBuilder) {

    /**
     * v47 çizim işlemleri önce sırayla toplanır, [flush] ile çizilir: desenler bağlı oldukları parçanın hemen üstüne
     * (sonraki parçaların altına) yerleşir.
     */
    private sealed class Op(val p: Poly) {
        class Part(p: Poly) : Op(p)
        class Ink(p: Poly) : Op(p)
        class Glint(p: Poly) : Op(p)
        class Line(p: Poly) : Op(p)
        class Detail(p: Poly) : Op(p)
    }

    private val ops = ArrayList<Op>()

    // --- Biçimler ---

    fun oval(cx: Float, cy: Float, rx: Float, ry: Float, rot: Float = 0f): Poly {
        val n = ceil(PI * (rx + ry) / STEP).toInt().coerceIn(24, 400)
        val r = Math.toRadians(rot.toDouble())
        val cs = cos(r).toFloat(); val sn = sin(r).toFloat()
        return List(n) { i ->
            val a = 2 * PI * i / n
            val ex = rx * cos(a).toFloat(); val ey = ry * sin(a).toFloat()
            P(cx + ex * cs - ey * sn, cy + ex * sn + ey * cs)
        }
    }

    fun circle(cx: Float, cy: Float, r: Float): Poly = oval(cx, cy, r, r)

    /** Elips yayı (uçlar dahil): [start]..[start]+[sweep] derece, 0° sağ, y aşağı. */
    fun arc(cx: Float, cy: Float, rx: Float, ry: Float, start: Float, sweep: Float, rot: Float = 0f): Poly {
        val n = ceil(abs(sweep) / 360f * PI * (rx + ry) / STEP).toInt().coerceIn(6, 400)
        val r = Math.toRadians(rot.toDouble())
        val cs = cos(r).toFloat(); val sn = sin(r).toFloat()
        return List(n + 1) { i ->
            val a = Math.toRadians((start + sweep * i / n).toDouble())
            val ex = rx * cos(a).toFloat(); val ey = ry * sin(a).toFloat()
            P(cx + ex * cs - ey * sn, cy + ex * sn + ey * cs)
        }
    }

    fun rect(x: Float, y: Float, w: Float, h: Float): Poly = listOf(P(x, y), P(x + w, y), P(x + w, y + h), P(x, y + h))

    fun rrect(x: Float, y: Float, w: Float, h: Float, r: Float): Poly = rounded(rect(x, y, w, h), r)

    fun poly(vararg xy: Float): Poly = List(xy.size / 2) { P(xy[it * 2], xy[it * 2 + 1]) }

    /** Köşeleri [r] yarıçapla yuvarlatılmış çokgen. */
    fun rpoly(r: Float, vararg xy: Float): Poly = rounded(poly(*xy), r)

    /** Noktalardan geçen yumuşak kapalı biçim. */
    fun blob(vararg xy: Float): Poly = clean(VectorBuilder.catmullRom(poly(*xy), true, 0.5f, 10))

    /** Yol kurucuyla kapalı biçim (moveTo/lineTo/quadTo/cubicTo/arcTo). */
    fun shape(block: PathBuilder.() -> Unit): Poly = clean(PathBuilder(v).apply(block).points)

    /** Yol kurucuyla açık çizgi noktaları. */
    fun track(block: PathBuilder.() -> Unit): Poly = PathBuilder(v).apply(block).points

    /** İki uç arası kapsül (yuvarlak uçlu kalın çubuk). */
    fun capsule(ax: Float, ay: Float, bx: Float, by: Float, r: Float): Poly {
        val ang = Math.toDegrees(kotlin.math.atan2((by - ay).toDouble(), (bx - ax).toDouble())).toFloat()
        return clean(arc(bx, by, r, r, ang - 90f, 180f) + arc(ax, ay, r, r, ang + 90f, 180f))
    }

    /** Kalınlığı [w0]'dan [w1]'e incelen, iki ucu yuvarlak şerit (kuyruk, dokunaç, sap); merkez çizgisi yumuşatılır. */
    fun tube(w0: Float, w1: Float, vararg xy: Float): Poly {
        val c = VectorBuilder.catmullRom(poly(*xy), false, 0.5f, 10)
        val n = c.size
        val left = ArrayList<P>(n); val right = ArrayList<P>(n)
        val tan = ArrayList<P>(n)
        for (i in 0 until n) {
            val a = c[max(0, i - 1)]; val b = c[min(n - 1, i + 1)]
            val len = hypot(b.x - a.x, b.y - a.y).coerceAtLeast(1e-5f)
            val tx = (b.x - a.x) / len; val ty = (b.y - a.y) / len
            tan += P(tx, ty)
            val w = (w0 + (w1 - w0) * i / (n - 1f)) / 2f
            left += P(c[i].x - ty * w, c[i].y + tx * w)
            right += P(c[i].x + ty * w, c[i].y - tx * w)
        }
        val out = ArrayList<P>()
        // Baş kapağı (sağdan sola yarım daire, geriye doğru)
        val t0 = tan[0]; val s = c[0]; val hw0 = w0 / 2f
        for (k in 0..8) {
            val th = PI * k / 8
            val cs = cos(th).toFloat(); val sn = sin(th).toFloat()
            // right[0] → geri → left[0]
            out += P(s.x + (t0.y * cs - t0.x * sn) * hw0, s.y + (-t0.x * cs - t0.y * sn) * hw0)
        }
        out += left
        val te = tan[n - 1]; val e = c[n - 1]; val hw1 = w1 / 2f
        for (k in 1..7) {
            val th = PI * k / 8
            val cs = cos(th).toFloat(); val sn = sin(th).toFloat()
            out += P(e.x + (-te.y * cs + te.x * sn) * hw1, e.y + (te.x * cs + te.y * sn) * hw1)
        }
        out += right.asReversed()
        return clean(out)
    }

    /** Yıldız: [n] uçlu, köşeleri [round] ile yuvarlatılmış. */
    fun star(cx: Float, cy: Float, ro: Float, ri: Float, n: Int = 5, rot: Float = 0f, round: Float = 0f): Poly {
        val pts = List(n * 2) { i ->
            val a = Math.toRadians(-90.0 + rot + 180.0 * i / n)
            val r = if (i % 2 == 0) ro else ri
            P(cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat())
        }
        return if (round > 0f) rounded(pts, round) else pts
    }

    /** Düz kenarı üstte/altta olan altıgen. */
    fun hexagon(cx: Float, cy: Float, r: Float, round: Float = 0f): Poly {
        val pts = List(6) { i ->
            val a = Math.toRadians(60.0 * i)
            P(cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat())
        }
        return if (round > 0f) rounded(pts, round) else pts
    }

    /** Kabarık kenarlı kapalı biçim (yele, ağaç tacı): elips üzerinde [bumps] tümsek. */
    fun puff(cx: Float, cy: Float, rx: Float, ry: Float, bumps: Int, bulge: Float = 0.3f, phase: Float = -90f): Poly {
        val pts = List(bumps) { i ->
            val a = Math.toRadians(phase + 360.0 * i / bumps)
            P(cx + rx * cos(a).toFloat(), cy + ry * sin(a).toFloat())
        }
        val out = ArrayList<P>()
        for (i in pts.indices) {
            val a = pts[i]; val b = pts[(i + 1) % pts.size]
            val mx = (a.x + b.x) / 2f; val my = (a.y + b.y) / 2f
            val dx = b.x - a.x; val dy = b.y - a.y
            val len = hypot(dx, dy).coerceAtLeast(1e-5f)
            var nx = -dy / len; var ny = dx / len
            if (nx * (mx - cx) + ny * (my - cy) < 0f) { nx = -nx; ny = -ny }
            val c = P(mx + nx * len * bulge * 2f, my + ny * len * bulge * 2f)
            val steps = ceil(len * 1.6f / STEP).toInt().coerceIn(6, 40)
            for (s in 0 until steps) out += quad(a, c, b, s / steps.toFloat())
        }
        return clean(out)
    }

    /** Altı düz bulut: [w]×[h] kutuya ortalanır (daire birleşimi). */
    fun cloud(cx: Float, cy: Float, w: Float, h: Float): Poly {
        val b = cy + h / 2f
        val rl = h * 0.36f; val rr = h * 0.32f
        val circles = listOf(
            floatArrayOf(cx - w / 2f + rl, b - rl, rl),
            floatArrayOf(cx - w * 0.14f, cy - h / 2f + h * 0.46f, h * 0.46f),
            floatArrayOf(cx + w * 0.16f, cy - h / 2f + h * 0.58f, h * 0.4f),
            floatArrayOf(cx + w / 2f - rr, b - rr, rr),
        )
        fun deg(c: FloatArray, x: Float, y: Float) = Math.toDegrees(kotlin.math.atan2((y - c[1]).toDouble(), (x - c[0]).toDouble())).toFloat()
        fun upperMeet(a: FloatArray, q: FloatArray): P {
            val dx = q[0] - a[0]; val dy = q[1] - a[1]
            val d = hypot(dx, dy)
            val l = (a[2] * a[2] - q[2] * q[2] + d * d) / (2f * d)
            val hh = kotlin.math.sqrt((a[2] * a[2] - l * l).coerceAtLeast(0f))
            val mx = a[0] + dx * l / d; val my = a[1] + dy * l / d
            val p1 = P(mx - dy * hh / d, my + dx * hh / d)
            val p2 = P(mx + dy * hh / d, my - dx * hh / d)
            return if (p1.y < p2.y) p1 else p2
        }
        fun norm(x0: Float): Float { var x = x0; while (x <= 0f) x += 360f; while (x > 360f) x -= 360f; return x }
        val out = ArrayList<P>()
        out += P(circles[0][0], b)
        var inAngle = 90f
        for (i in circles.indices) {
            val c = circles[i]
            val outAngle = if (i == circles.size - 1) 90f else { val m = upperMeet(c, circles[i + 1]); deg(c, m.x, m.y) }
            out += arc(c[0], c[1], c[2], c[2], inAngle, norm(outAngle - inAngle))
            if (i < circles.size - 1) { val m = upperMeet(c, circles[i + 1]); inAngle = deg(circles[i + 1], m.x, m.y) }
        }
        return clean(out)
    }

    /** Damla: sivri ucu yukarıda, [cx],[cy] alt yuvarlağın merkezi. */
    fun drop(cx: Float, cy: Float, r: Float): Poly = shape {
        moveTo(cx, cy - r * 2.4f)
        quadTo(cx + r * 1.1f, cy - r * 0.8f, cx + r, cy)
        arcTo(cx, cy, r, r, 0f, 180f)
        quadTo(cx - r * 1.1f, cy - r * 0.8f, cx, cy - r * 2.4f)
    }

    /** Badem yaprağı: [x1],[y1] sapı, [x2],[y2] ucu; [bend] genişlik (kiriş boyuna oran). */
    fun leaf(x1: Float, y1: Float, x2: Float, y2: Float, bend: Float = 0.35f): Poly {
        val mx = (x1 + x2) / 2f; val my = (y1 + y2) / 2f
        val nx = -(y2 - y1) * bend; val ny = (x2 - x1) * bend
        return shape { moveTo(x1, y1); quadTo(mx + nx, my + ny, x2, y2); quadTo(mx - nx, my - ny, x1, y1) }
    }

    /** Fiyonk (iki üçgen, ortası boğum): [cx],[cy] ortası. */
    fun bow(cx: Float, cy: Float, w: Float, h: Float, rot: Float = 0f): Poly =
        rotate(rpoly(1f, cx - w, cy - h, cx, cy - h * 0.35f, cx + w, cy - h, cx + w, cy + h, cx, cy + h * 0.35f, cx - w, cy + h), rot, cx, cy)

    /** Çimen şeridi: [x0]..[x1] arası, tabanı [base], [blades] yaprak ucu [top] yüksekliğinde. */
    fun grass(x0: Float, x1: Float, base: Float, top: Float, valley: Float, blades: Int): Poly {
        val pts = ArrayList<P>()
        pts += P(x0, base)
        val step = (x1 - x0) / blades
        pts += P(x0, valley)
        for (i in 0 until blades) {
            pts += P(x0 + step * (i + 0.5f), top + if (i % 2 == 0) 0f else (valley - top) * 0.25f)
            pts += P(x0 + step * (i + 1), valley)
        }
        pts += P(x1, base)
        return rounded(clean(pts), 1.2f)
    }

    fun rotate(p: Poly, deg: Float, px: Float, py: Float): Poly {
        val r = Math.toRadians(deg.toDouble())
        val cs = cos(r).toFloat(); val sn = sin(r).toFloat()
        return p.map { P(px + (it.x - px) * cs - (it.y - py) * sn, py + (it.x - px) * sn + (it.y - py) * cs) }
    }

    /** [cx] dikey eksenine göre ayna. */
    fun mirror(p: Poly, cx: Float): Poly = p.map { P(2f * cx - it.x, it.y) }.asReversed()

    /** [cy] yatay eksenine göre ayna. */
    fun flipY(p: Poly, cy: Float): Poly = p.map { P(it.x, 2f * cy - it.y) }.asReversed()

    fun scaleY(p: Poly, s: Float, cy: Float): Poly = p.map { P(it.x, cy + (it.y - cy) * s) }

    // --- Kesme ---

    /** [a]'dan sırayla [b] biçimlerini çıkarır (öndeki parçalar arkadakinden kesilir); en büyük parça. */
    fun cut(a: Poly, vararg b: Poly): Poly = largest(cutAll(a, *b), "cut")

    /** [a]'dan [b] biçimleri çıkınca kalan tüm parçalar. */
    fun cutAll(a: Poly, vararg b: Poly): List<Poly> {
        var cur = listOf(a)
        for (x in b) cur = cur.flatMap { PolyClip.run(it, x, PolyClip.Op.MINUS) }
        return cur
    }

    /** [a]'nın [b] içinde kalan en büyük parçası. */
    fun clip(a: Poly, b: Poly): Poly = largest(clipAll(a, b), "clip")

    fun clipAll(a: Poly, b: Poly): List<Poly> = PolyClip.run(a, b, PolyClip.Op.AND)

    /** İki biçimin birleşimi (tek dış sınır). */
    fun union(a: Poly, b: Poly): Poly = largest(PolyClip.run(a, b, PolyClip.Op.OR), "union")

    private fun largest(list: List<Poly>, what: String): Poly {
        val best = list.maxByOrNull { PolyClip.area(it) } ?: run { dropped += "$what: boş"; return emptyList() }
        val a = PolyClip.area(best)
        list.forEach { if (it !== best && PolyClip.area(it) > a * 0.01f) dropped += "$what: atılan parça ${PolyClip.area(it)}/$a" }
        return best
    }

    // --- Çizim ---

    /** Boyanacak parça: beyaz dolgu, kalın koyu kenar. */
    fun part(p: Poly) {
        if (p.size < 3) { dropped += "part: boş"; return }
        ops += Op.Part(p)
    }

    fun parts(ps: List<Poly>) = ps.forEach { part(it) }

    /** v34 kesilince kalan parçalardan yalnız görünür büyüklüktekiler (kesişim kırıntıları atılır). */
    fun bigParts(ps: List<Poly>, min: Float = 24f) = ps.filter { kotlin.math.abs(PolyClip.area(it)) >= min }.forEach { part(it) }

    /** Koyu dolu ayrıntı (burun, göz bebeği, çekirdek): kenarsız. */
    fun ink(p: Poly) {
        if (p.size < 3) return
        ops += Op.Ink(p)
    }

    fun dot(cx: Float, cy: Float, r: Float) = ink(circle(cx, cy, r))

    /** Göz: koyu nokta ve küçük beyaz parıltı. */
    fun eye(cx: Float, cy: Float, r: Float) {
        dot(cx, cy, r)
        ops += Op.Glint(circle(cx + r * 0.32f, cy - r * 0.34f, r * 0.36f))
    }

    /** Açık ayrıntı çizgisi (yol kurucuyla). */
    fun line(block: PathBuilder.() -> Unit) = stroke(track(block))

    /** Noktalardan geçen yumuşak açık çizgi. */
    fun curve(vararg xy: Float) = stroke(VectorBuilder.catmullRom(poly(*xy), false, 0.5f, 10))

    fun seg(x1: Float, y1: Float, x2: Float, y2: Float) = stroke(listOf(P(x1, y1), P(x2, y2)))

    /** Gülümseme: [cx] ortası, [w] genişlik, [d] derinlik. */
    fun smile(cx: Float, cy: Float, w: Float, d: Float) = line { moveTo(cx - w / 2f, cy); quadTo(cx, cy + d * 2f, cx + w / 2f, cy) }

    fun stroke(p: Poly) {
        if (p.size < 2) return
        ops += Op.Line(p)
    }

    /**
     * v47 toplanan işlemleri çizer. [pattern] varsa en büyük (yüz olmayan) parçalara desen eklenir: desen parçanın
     * içine kırpılır, sonradan çizilen parçalardan ve koyu ayrıntılardan kesilir (gizli çizgi kalmaz), kırıntılar atılır.
     */
    fun flush(pattern: KidDetail.Pattern?, w: Float, h: Float, key: String? = null) {
        // Desenler tasarım biriminde: çizim her boyutta aynı, bir kez hesaplanır (seçicideki küçük resimler hızlı)
        val compute = { KidDetail.decorate(this, ops.map { it.p }, ops.map { it is Op.Part }, ops.map { it is Op.Ink || it is Op.Glint }, ops.map { it is Op.Line }, pattern!!, w, h) }
        val details = when {
            pattern == null -> emptyMap()
            key != null -> detailCache.getOrPut(key) { compute() }
            else -> compute()
        }
        ops.forEachIndexed { i, op ->
            emit(op)
            details[i]?.forEach { emit(Op.Detail(it)) }
        }
        ops.clear()
    }

    private fun emit(op: Op) {
        when (op) {
            is Op.Part -> v.points(op.p, closed = true).fill(KID_PAPER).stroke(KID_INK).width(KID_OUTLINE)
            is Op.Detail -> v.points(op.p, closed = true).fill(KID_PAPER).stroke(KID_INK).width(KID_DETAIL).tag(KidDetail.TAG)
            is Op.Ink -> v.points(op.p, closed = true).fill(KID_INK).stroke(KID_INK).noStroke()
            is Op.Glint -> v.points(op.p, closed = true).fill(KID_PAPER).stroke(KID_INK).noStroke()
            is Op.Line -> v.points(op.p, closed = false).fill(null).stroke(KID_INK).width(KID_DETAIL)
        }
    }

    /** Çizginin yalnız [area] içinde kalan kısımları (ör. külah örgüsü). */
    fun strokeInside(p: Poly, area: Poly) = PolyClip.lineParts(p, area, inside = true).forEach { stroke(it) }

    /** Çizginin [covers] dışında kalan kısımları (ör. ip, fiyonkların arasında). */
    fun strokeOutside(p: Poly, vararg covers: Poly) {
        var cur = listOf(p)
        for (c in covers) cur = cur.flatMap { PolyClip.lineParts(it, c, inside = false) }
        cur.forEach { stroke(it) }
    }

    companion object {
        /** v47 çizim adına göre hesaplanmış desenler. */
        private val detailCache = java.util.concurrent.ConcurrentHashMap<String, Map<Int, List<Poly>>>()

        /** Kurulumda atılan/boş kalan parçalar (testler boş olmasını bekler; uygulamada çizim yine çizilir). */
        val dropped: MutableList<String> = java.util.Collections.synchronizedList(ArrayList())

        fun quad(a: P, c: P, b: P, t: Float): P {
            val m = 1 - t
            return P(m * m * a.x + 2 * m * t * c.x + t * t * b.x, m * m * a.y + 2 * m * t * c.y + t * t * b.y)
        }

        /** Köşeleri yuvarlatır (her köşe komşu kenarların en çok %45'i kadar). */
        fun rounded(p: Poly, r: Float): Poly {
            if (r <= 0f || p.size < 3) return p
            val out = ArrayList<P>()
            val n = p.size
            for (i in 0 until n) {
                val a = p[(i - 1 + n) % n]; val v = p[i]; val b = p[(i + 1) % n]
                val la = hypot(a.x - v.x, a.y - v.y); val lb = hypot(b.x - v.x, b.y - v.y)
                if (la < 1e-4f || lb < 1e-4f) { out += v; continue }
                val rr = min(r, min(la, lb) * 0.45f)
                val p1 = P(v.x + (a.x - v.x) / la * rr, v.y + (a.y - v.y) / la * rr)
                val p2 = P(v.x + (b.x - v.x) / lb * rr, v.y + (b.y - v.y) / lb * rr)
                val steps = ceil(rr * 1.5f / STEP).toInt().coerceIn(3, 16)
                for (s in 0..steps) out += quad(p1, v, p2, s / steps.toFloat())
            }
            return clean(out)
        }

        /** Ardışık çakışan noktaları ve kapanıştaki yineleneni atar. */
        fun clean(p: Poly): Poly {
            if (p.size < 2) return p
            val out = ArrayList<P>(p.size)
            for (q in p) if (out.isEmpty() || hypot(q.x - out.last().x, q.y - out.last().y) > 1e-3f) out += q
            while (out.size > 2 && hypot(out.first().x - out.last().x, out.first().y - out.last().y) <= 1e-3f) out.removeAt(out.size - 1)
            return out
        }
    }
}

/**
 * Basit çokgenler arasında kesişim, fark ve birleşim (Greiner–Hormann). Kenar bir köşeye tam değerse ya da kenarlar
 * çakışırsa ikinci çokgen çok az kaydırılıp yeniden denenir (gözle görülmez).
 */
internal object PolyClip {

    enum class Op { AND, MINUS, OR }

    fun run(a: Poly, b: Poly, op: Op): List<Poly> {
        val s = KidPen.clean(a)
        var c = KidPen.clean(b)
        if (s.size < 3) return emptyList()
        if (c.size < 3) return if (op == Op.AND) emptyList() else listOf(s)
        for (attempt in 0 until 10) {
            val r = attempt(s, c, op)
            if (r != null) return r.map { KidPen.clean(it) }.filter { it.size >= 3 && area(it) > 1e-4f }
            val d = 0.0007f * (attempt + 1)
            c = c.map { P(it.x + d, it.y + d * 0.61f) }
        }
        KidPen.dropped += "PolyClip: dejenere"
        return listOf(s)
    }

    fun area(p: Poly): Float {
        var a = 0.0
        for (i in p.indices) { val q = p[(i + 1) % p.size]; a += p[i].x.toDouble() * q.y - q.x.toDouble() * p[i].y }
        return abs(a / 2.0).toFloat()
    }

    private class X(val i: Int, val ta: Double, val j: Int, val tb: Double, val x: Double, val y: Double)
    private class N(val x: Double, val y: Double, val id: Int)

    private fun attempt(s: Poly, c: Poly, op: Op): List<Poly>? {
        val ns = s.size; val nc = c.size
        val xs = ArrayList<X>()
        for (i in 0 until ns) {
            val ax = s[i].x.toDouble(); val ay = s[i].y.toDouble()
            val bx = s[(i + 1) % ns].x.toDouble(); val by = s[(i + 1) % ns].y.toDouble()
            val loX = min(ax, bx) - 1e-6; val hiX = max(ax, bx) + 1e-6
            val loY = min(ay, by) - 1e-6; val hiY = max(ay, by) + 1e-6
            val rx = bx - ax; val ry = by - ay
            for (j in 0 until nc) {
                val cx = c[j].x.toDouble(); val cy = c[j].y.toDouble()
                val dx = c[(j + 1) % nc].x.toDouble(); val dy = c[(j + 1) % nc].y.toDouble()
                if (max(cx, dx) < loX || min(cx, dx) > hiX || max(cy, dy) < loY || min(cy, dy) > hiY) continue
                val qx = dx - cx; val qy = dy - cy
                val den = rx * qy - ry * qx
                val wx = cx - ax; val wy = cy - ay
                val lr = hypot(rx, ry); val lq = hypot(qx, qy)
                if (abs(den) < 1e-12 * (lr * lq + 1e-12)) {
                    // Paralel: aynı doğru üzerinde örtüşüyorsa dejenere
                    if (lr > 1e-9 && abs(wx * ry - wy * rx) / lr < 1e-6) {
                        val t0 = (wx * rx + wy * ry) / (lr * lr)
                        val t1 = ((dx - ax) * rx + (dy - ay) * ry) / (lr * lr)
                        if (max(t0, t1) > -1e-9 && min(t0, t1) < 1 + 1e-9) return null
                    }
                    continue
                }
                val t = (wx * qy - wy * qx) / den
                val u = (wx * ry - wy * rx) / den
                if (t < -1e-9 || t > 1 + 1e-9 || u < -1e-9 || u > 1 + 1e-9) continue
                if (t < 1e-7 || t > 1 - 1e-7 || u < 1e-7 || u > 1 - 1e-7) return null
                xs += X(i, t, j, u, ax + rx * t, ay + ry * t)
            }
        }
        val sIn = inside(c, s[0].x.toDouble(), s[0].y.toDouble()) ?: return null
        val cIn = inside(s, c[0].x.toDouble(), c[0].y.toDouble()) ?: return null
        if (xs.isEmpty()) {
            return when (op) {
                Op.AND -> if (sIn) listOf(s) else if (cIn) listOf(c) else emptyList()
                // c tamamen s içindeyse delik çizilmez: c zaten üstte ayrı parça olarak çizilir
                Op.MINUS -> if (sIn) emptyList() else listOf(s)
                Op.OR -> if (sIn) listOf(c) else if (cIn) listOf(s) else listOf(s, c)
            }
        }
        val k = xs.size
        if (k % 2 == 1) return null
        val sl = ArrayList<N>(ns + k); val sPos = IntArray(k)
        val byI = HashMap<Int, MutableList<Int>>()
        xs.forEachIndexed { idx, x -> byI.getOrPut(x.i) { ArrayList() } += idx }
        for (i in 0 until ns) {
            sl += N(s[i].x.toDouble(), s[i].y.toDouble(), -1)
            byI[i]?.sortedBy { xs[it].ta }?.forEach { idx -> sPos[idx] = sl.size; sl += N(xs[idx].x, xs[idx].y, idx) }
        }
        val cl = ArrayList<N>(nc + k); val cPos = IntArray(k)
        val byJ = HashMap<Int, MutableList<Int>>()
        xs.forEachIndexed { idx, x -> byJ.getOrPut(x.j) { ArrayList() } += idx }
        for (j in 0 until nc) {
            cl += N(c[j].x.toDouble(), c[j].y.toDouble(), -1)
            byJ[j]?.sortedBy { xs[it].tb }?.forEach { idx -> cPos[idx] = cl.size; cl += N(xs[idx].x, xs[idx].y, idx) }
        }
        // Giriş/çıkış: kesişimden ileri gidince öbür çokgenin içine mi giriliyor
        val entS = BooleanArray(k); var st = sIn
        for (nd in sl) if (nd.id >= 0) { entS[nd.id] = !st; st = !st }
        val entC = BooleanArray(k); st = cIn
        for (nd in cl) if (nd.id >= 0) { entC[nd.id] = !st; st = !st }
        if (op == Op.MINUS || op == Op.OR) for (q in 0 until k) entS[q] = !entS[q]
        if (op == Op.OR) for (q in 0 until k) entC[q] = !entC[q]
        val visited = BooleanArray(k)
        val out = ArrayList<Poly>()
        for (k0 in 0 until k) {
            if (visited[k0]) continue
            val poly = ArrayList<P>()
            var cur = k0; var onS = true; var guard = 0
            do {
                visited[cur] = true
                val list = if (onS) sl else cl
                var idx = if (onS) sPos[cur] else cPos[cur]
                val fwd = if (onS) entS[cur] else entC[cur]
                poly += P(list[idx].x.toFloat(), list[idx].y.toFloat())
                while (true) {
                    idx = if (fwd) (idx + 1) % list.size else (idx - 1 + list.size) % list.size
                    val nd = list[idx]
                    if (nd.id >= 0) { cur = nd.id; break }
                    poly += P(nd.x.toFloat(), nd.y.toFloat())
                }
                onS = !onS
                if (++guard > 2 * k + 2) return null
            } while (cur != k0)
            out += poly
        }
        return out
    }

    /** Nokta çokgenin içinde mi; sınırın çok yakınındaysa null (dejenere). */
    private fun inside(p: Poly, x: Double, y: Double): Boolean? {
        var c = false
        var j = p.size - 1
        for (i in p.indices) {
            val ax = p[i].x.toDouble(); val ay = p[i].y.toDouble()
            val bx = p[j].x.toDouble(); val by = p[j].y.toDouble()
            if (segDist(x, y, ax, ay, bx, by) < 1e-5) return null
            if ((ay > y) != (by > y) && x < (bx - ax) * (y - ay) / (by - ay) + ax) c = !c
            j = i
        }
        return c
    }

    private fun segDist(x: Double, y: Double, ax: Double, ay: Double, bx: Double, by: Double): Double {
        val vx = bx - ax; val vy = by - ay
        val l2 = vx * vx + vy * vy
        val t = if (l2 < 1e-18) 0.0 else (((x - ax) * vx + (y - ay) * vy) / l2).coerceIn(0.0, 1.0)
        return hypot(x - (ax + vx * t), y - (ay + vy * t))
    }

    /** Açık çizginin [area] içinde ([inside]) ya da dışında kalan parçaları. */
    fun lineParts(line: Poly, area: Poly, inside: Boolean): List<Poly> {
        val pieces = ArrayList<Poly>()
        var cur = ArrayList<P>()
        val n = area.size
        for (i in 0 until line.size - 1) {
            val a = line[i]; val b = line[i + 1]
            val ts = ArrayList<Double>()
            ts += 0.0; ts += 1.0
            val rx = (b.x - a.x).toDouble(); val ry = (b.y - a.y).toDouble()
            for (j in 0 until n) {
                val c = area[j]; val d = area[(j + 1) % n]
                val qx = (d.x - c.x).toDouble(); val qy = (d.y - c.y).toDouble()
                val den = rx * qy - ry * qx
                if (abs(den) < 1e-12) continue
                val wx = (c.x - a.x).toDouble(); val wy = (c.y - a.y).toDouble()
                val t = (wx * qy - wy * qx) / den
                val u = (wx * ry - wy * rx) / den
                if (t in 0.0..1.0 && u in 0.0..1.0) ts += t
            }
            ts.sort()
            for (k in 0 until ts.size - 1) {
                val t0 = ts[k]; val t1 = ts[k + 1]
                if (t1 - t0 < 1e-9) continue
                val tm = (t0 + t1) / 2
                val mx = a.x + rx * tm; val my = a.y + ry * tm
                val isIn = containsPoint(area, mx, my)
                val p0 = P((a.x + rx * t0).toFloat(), (a.y + ry * t0).toFloat())
                val p1 = P((a.x + rx * t1).toFloat(), (a.y + ry * t1).toFloat())
                if (isIn == inside) {
                    if (cur.isEmpty()) cur += p0
                    cur += p1
                } else if (cur.isNotEmpty()) {
                    if (cur.size >= 2) pieces += cur
                    cur = ArrayList()
                }
            }
        }
        if (cur.size >= 2) pieces += cur
        return pieces
    }

    private fun containsPoint(p: Poly, x: Double, y: Double): Boolean {
        var c = false
        var j = p.size - 1
        for (i in p.indices) {
            val ax = p[i].x.toDouble(); val ay = p[i].y.toDouble()
            val bx = p[j].x.toDouble(); val by = p[j].y.toDouble()
            if ((ay > y) != (by > y) && x < (bx - ax) * (y - ay) / (by - ay) + ax) c = !c
            j = i
        }
        return c
    }
}

/**
 * "Çocuklar için çizim" modunun şekil kataloğu: v33 7 kategoride 50, v34 10 kategoride 86 boyama çizimi (hayvanlar, deniz, taşıtlar, meyve ve
 * yiyecek, doğa ve gökyüzü, uzay, oyuncak ve eşyalar). Konular okul öncesi ve ilkokul boyama sayfalarında en sık
 * görülen, kolay tanınan nesnelerden seçildi; her çizim özgün ve sade: yuvarlak, sevimli oranlar, az ayrıntı, yazı yok.
 */
internal object KidsShapes {

    const val SECTION = "Çocuklar"

    val categories: List<LibCategory> by lazy {
        listOf(
            LibCategory("kids_animals", "Hayvanlar", Audience.KIDS, KidAnimals.shapes, SECTION),
            LibCategory("kids_sea", "Deniz", Audience.KIDS, KidSea.shapes, SECTION),
            LibCategory("kids_vehicles", "Taşıtlar", Audience.KIDS, KidVehicles.shapes, SECTION),
            LibCategory("kids_food", "Meyve ve yiyecek", Audience.KIDS, KidFood.shapes, SECTION),
            LibCategory("kids_nature", "Doğa ve gökyüzü", Audience.KIDS, KidNature.shapes, SECTION),
            LibCategory("kids_space", "Uzay", Audience.KIDS, KidSpace.shapes, SECTION),
            LibCategory("kids_toys", "Oyuncak ve eşyalar", Audience.KIDS, KidToys.shapes, SECTION),
            // v34 çizgi filmlerde sık görülen türlerden özgün karakterler, masal/macera yerleri ve sihirli nesneler
            LibCategory("kids_characters", "Çizgi film karakterleri", Audience.KIDS, KidCharacters.shapes, SECTION),
            LibCategory("kids_places", "Masal ve macera yerleri", Audience.KIDS, KidPlaces.shapes, SECTION),
            LibCategory("kids_magic", "Sihirli nesneler", Audience.KIDS, KidMagic.shapes, SECTION),
        )
    }
}

// =====================================================================================================================
// Hayvanlar
// =====================================================================================================================

private object KidAnimals {

    val shapes: List<LibShape> by lazy { listOf(cat(), dog(), rabbit(), lion(), elephant(), cow(), chick(), owl(), dino()) }

    private fun cat() = kid("cat", "Kedi", 100f, 100f, listOf("kedi", "hayvan", "evcil", "cat", "kitten", "pet")) {
        val body = blob(50f, 52f, 68f, 58f, 74f, 74f, 70f, 90f, 50f, 95f, 30f, 90f, 26f, 74f, 32f, 58f)
        val head = circle(50f, 38f, 23f)
        val pawL = oval(40f, 92f, 9f, 6f)
        val pawR = oval(60f, 92f, 9f, 6f)
        val tail = shape {
            moveTo(66f, 84f); cubicTo(88f, 84f, 96f, 66f, 90f, 50f); cubicTo(88f, 44f, 80f, 44f, 81f, 51f)
            cubicTo(85f, 64f, 80f, 74f, 64f, 75f)
        }
        part(cut(tail, body, pawR))
        part(cut(body, head, pawL, pawR))
        part(pawL); part(pawR)
        part(oval(50f, 74f, 10f, 11f))
        val earL = rpoly(3f, 28f, 30f, 27f, 6f, 48f, 18f)
        val earR = mirror(earL, 50f)
        val eL = cut(earL, head); val eR = cut(earR, head)
        part(eL); part(eR)
        part(clip(rpoly(2f, 31.5f, 25f, 31f, 13f, 41f, 19f), eL))
        part(clip(mirror(rpoly(2f, 31.5f, 25f, 31f, 13f, 41f, 19f), 50f), eR))
        part(head)
        eye(41f, 36f, 3.3f); eye(59f, 36f, 3.3f)
        ink(rpoly(1f, 46.5f, 43.5f, 53.5f, 43.5f, 50f, 47.5f))
        line { moveTo(50f, 47.5f); quadTo(48f, 51.5f, 44.5f, 50f) }
        line { moveTo(50f, 47.5f); quadTo(52f, 51.5f, 55.5f, 50f) }
        seg(31f, 43f, 40f, 45f); seg(31f, 49f, 40f, 48f)
        seg(69f, 43f, 60f, 45f); seg(69f, 49f, 60f, 48f)
    }

    private fun dog() = kid("dog", "Köpek", 100f, 100f, listOf("köpek", "kuçu kuçu", "hayvan", "evcil", "dog", "puppy", "pet")) {
        val body = blob(50f, 58f, 70f, 64f, 76f, 80f, 70f, 94f, 50f, 97f, 30f, 94f, 24f, 80f, 30f, 64f)
        val head = oval(50f, 38f, 26f, 22f)
        val earL = oval(24f, 42f, 8.5f, 17f, 15f)
        val earR = mirror(earL, 50f)
        val tail = tube(7f, 5f, 70f, 80f, 82f, 72f, 88f, 60f)
        val tag = circle(50f, 68.5f, 3.6f)
        val pawL = oval(39f, 94f, 9f, 5.5f)
        val pawR = oval(61f, 94f, 9f, 5.5f)
        part(cut(tail, body))
        val bodyV = cut(body, head, earL, earR, pawL, pawR, tag)
        part(bodyV)
        part(cut(clip(rect(10f, 59f, 80f, 7f), bodyV), tag))
        part(tag); part(pawL); part(pawR)
        part(cut(head, earL, earR)); part(earL); part(earR)
        part(oval(50f, 47f, 12f, 8.5f))
        part(cut(blob(56f, 25f, 63f, 22.5f, 68f, 27f, 68.5f, 34f, 63.5f, 39f, 57f, 37.5f, 54f, 31f), earR))
        eye(40f, 31f, 3.3f); eye(61.5f, 31f, 3.3f)
        ink(oval(50f, 42.5f, 5f, 3.5f))
        line { moveTo(50f, 46f); lineTo(50f, 50f) }
        line { moveTo(43.5f, 49.5f); quadTo(46.5f, 53f, 50f, 50f); quadTo(53.5f, 53f, 56.5f, 49.5f) }
    }

    private fun rabbit() = kid("rabbit", "Tavşan", 100f, 100f, listOf("tavşan", "hayvan", "rabbit", "bunny")) {
        val head = circle(50f, 50f, 19f)
        val earL = oval(39f, 24f, 8.5f, 21f, -10f)
        val earR = mirror(earL, 50f)
        val body = oval(50f, 82f, 21f, 15f)
        val footL = oval(36f, 94f, 9f, 4.5f)
        val footR = mirror(footL, 50f)
        part(cut(body, head, footL, footR)); part(footL); part(footR)
        val eL = cut(earL, head); val eR = cut(earR, head)
        part(eL); part(eR)
        part(clip(oval(39f, 24f, 4.2f, 14f, -10f), eL))
        part(clip(oval(61f, 24f, 4.2f, 14f, 10f), eR))
        part(head)
        part(oval(38.5f, 55f, 3.5f, 2.5f)); part(oval(61.5f, 55f, 3.5f, 2.5f))
        eye(43f, 46f, 3f); eye(57f, 46f, 3f)
        ink(rpoly(1f, 47.5f, 52.5f, 52.5f, 52.5f, 50f, 55.5f))
        line { moveTo(50f, 55.5f); lineTo(50f, 57.5f) }
        line { moveTo(44.5f, 56.5f); quadTo(47f, 59.5f, 50f, 57.5f); quadTo(53f, 59.5f, 55.5f, 56.5f) }
        part(rrect(45.5f, 58.4f, 9f, 7.5f, 1.8f))
        seg(50f, 58.4f, 50f, 65.9f)
    }

    private fun lion() = kid("lion", "Aslan", 100f, 100f, listOf("aslan", "yele", "hayvan", "orman", "lion")) {
        val face = circle(50f, 54f, 25f)
        part(puff(50f, 50f, 40f, 40f, 14, 0.3f))
        val earL = cut(circle(31f, 33f, 8f), face)
        val earR = cut(circle(69f, 33f, 8f), face)
        part(earL); part(earR)
        part(clip(circle(31f, 33f, 4f), earL)); part(clip(circle(69f, 33f, 4f), earR))
        part(face)
        part(union(circle(44f, 64f, 8.5f), circle(56f, 64f, 8.5f)))
        ink(rpoly(2f, 44.5f, 56f, 55.5f, 56f, 50f, 62f))
        line { moveTo(50f, 62f); lineTo(50f, 66f) }
        eye(41f, 47f, 3.3f); eye(59f, 47f, 3.3f)
    }

    private fun elephant() = kid("elephant", "Fil", 110f, 90f, listOf("fil", "hortum", "hayvan", "elephant")) {
        val body = oval(66f, 48f, 34f, 26f)
        val head = circle(30f, 40f, 19f)
        val ear = oval(48f, 40f, 13f, 17f, -10f)
        val trunk = tube(13f, 7f, 22f, 46f, 15f, 58f, 12f, 70f, 18f, 80f)
        for (x in floatArrayOf(38f, 54f, 72f)) part(cut(rrect(x, 52f, 14f, 34f, 5f), body, head))
        part(cut(rrect(87f, 52f, 12f, 34f, 5f), body))
        line { moveTo(99.5f, 46f); quadTo(105f, 50f, 104f, 58f) }
        part(oval(104f, 61f, 3f, 4f))
        part(cut(body, head, ear))
        part(cut(trunk, head))
        part(cut(head, ear))
        part(ear)
        part(clip(oval(49f, 41f, 7.5f, 11f, -10f), ear))
        eye(24f, 35f, 3f)
        line { moveTo(22f, 49f); quadTo(27f, 53f, 32f, 49f) }
    }

    private fun cow() = kid("cow", "İnek", 120f, 90f, listOf("inek", "çiftlik", "hayvan", "süt", "cow", "farm")) {
        val body = rrect(36f, 28f, 76f, 38f, 17f)
        val head = oval(26f, 38f, 14f, 16f)
        val muzzle = oval(26f, 52f, 12f, 8.5f)
        val earL = oval(10f, 29f, 7f, 3.8f, 20f)
        val earR = oval(42f, 29f, 7f, 3.8f, -20f)
        val hoofBand = rect(0f, 80f, 120f, 12f)
        for (x in floatArrayOf(44f, 58f, 84f, 98f)) {
            val leg = cut(rrect(x, 56f, 11f, 31f, 4f), body)
            part(cut(leg, hoofBand)); ink(clip(leg, hoofBand))
        }
        part(cut(oval(76.5f, 66f, 6.5f, 6f), body))
        line { moveTo(111f, 40f); quadTo(118f, 46f, 116f, 60f) }
        ink(oval(116f, 63f, 2.6f, 3.6f))
        val bodyV = cut(body, head, earR, muzzle)
        part(bodyV)
        part(clip(blob(60f, 24f, 76f, 26f, 78f, 40f, 68f, 47f, 57f, 40f), bodyV))
        part(clip(blob(92f, 46f, 102f, 44f, 106f, 54f, 98f, 60f, 88f, 56f), bodyV))
        part(cut(earL, head)); part(cut(earR, head))
        part(cut(head, muzzle))
        part(muzzle)
        dot(21f, 52f, 1.8f); dot(31f, 52f, 1.8f)
        eye(21f, 36f, 2.8f); eye(31f, 36f, 2.8f)
    }

    private fun chick() = kid("chick", "Civciv", 100f, 100f, listOf("civciv", "yumurta", "kuş", "hayvan", "chick", "egg")) {
        val body = circle(50f, 46f, 27f)
        val shell = clip(
            oval(50f, 72f, 33f, 25f),
            poly(0f, 100f, 0f, 60f, 17f, 60f, 25f, 53f, 33f, 61f, 41f, 53f, 50f, 61f, 59f, 53f, 67f, 61f, 75f, 53f, 83f, 60f, 100f, 60f, 100f, 100f),
        )
        val wingL = oval(22f, 48f, 6.5f, 11f, 30f)
        val wingR = mirror(wingL, 50f)
        val feathers = listOf(oval(44f, 16f, 3f, 6.5f, -25f), oval(50f, 13f, 3.2f, 7.5f, 0f), oval(56f, 16f, 3f, 6.5f, 25f))
        feathers.forEachIndexed { i, f -> part(cut(f, body, *feathers.drop(i + 1).toTypedArray())) }
        part(cut(body, shell, wingL, wingR))
        part(shell); part(wingL); part(wingR)
        part(rpoly(1.5f, 43f, 45f, 50f, 39f, 57f, 45f, 50f, 51f))
        seg(43.4f, 45f, 56.6f, 45f)
        eye(40f, 34f, 3.3f); eye(60f, 34f, 3.3f)
        part(oval(35f, 43f, 3.8f, 2.6f)); part(oval(65f, 43f, 3.8f, 2.6f))
    }

    private fun owl() = kid("owl", "Baykuş", 100f, 100f, listOf("baykuş", "kuş", "gece", "hayvan", "owl")) {
        val tuftL = rpoly(3f, 22f, 32f, 23f, 8f, 42f, 22f)
        val body = union(union(oval(50f, 56f, 32f, 38f), tuftL), mirror(tuftL, 50f))
        val wingL = oval(20f, 62f, 8f, 18f, 12f)
        val wingR = mirror(wingL, 50f)
        val branch = rrect(6f, 88f, 88f, 9f, 4.5f)
        val footL = rrect(36f, 84f, 10f, 7f, 3.5f)
        val footR = rrect(54f, 84f, 10f, 7f, 3.5f)
        val beak = rpoly(1.5f, 46f, 52f, 54f, 52f, 50f, 60f)
        part(cut(body, wingL, wingR, branch, footL, footR))
        part(cut(oval(50f, 72f, 17f, 18f), beak, footL, footR, branch))
        for (p in listOf(44f to 67f, 56f to 67f, 50f to 76f)) {
            line { moveTo(p.first - 3.2f, p.second - 1.8f); lineTo(p.first, p.second + 1.8f); lineTo(p.first + 3.2f, p.second - 1.8f) }
        }
        part(cut(wingL, branch)); part(cut(wingR, branch))
        part(cut(branch, footL, footR)); part(footL); part(footR)
        seg(39.3f, 91f, 39.3f, 87.5f); seg(42.7f, 91f, 42.7f, 87.5f)
        seg(57.3f, 91f, 57.3f, 87.5f); seg(60.7f, 91f, 60.7f, 87.5f)
        part(circle(37f, 42f, 10.5f)); part(circle(63f, 42f, 10.5f))
        eye(39f, 43f, 5f); eye(61f, 43f, 5f)
        part(beak)
    }

    private fun dino() = kid("dino", "Dinozor", 120f, 90f, listOf("dinozor", "uzun boyun", "hayvan", "dinosaur", "dino")) {
        val body = oval(52f, 56f, 30f, 19f)
        val neck = tube(16f, 11f, 68f, 52f, 79f, 38f, 86f, 24f)
        val head = oval(93f, 20f, 14f, 10f, -10f)
        val tail = tube(18f, 3f, 28f, 58f, 14f, 66f, 4f, 62f)
        part(cut(tail, body))
        for (x in floatArrayOf(28f, 43f, 57f, 70f)) part(cut(rrect(x, 58f, 11f, 28f, 5f), body))
        val plates = listOf(rpoly(2f, 31f, 45f, 37f, 30f, 45f, 41f), rpoly(2f, 44f, 40f, 51f, 25f, 58f, 38f), rpoly(2f, 57f, 40f, 63f, 28f, 69f, 42f))
        for (p in plates) part(cut(p, body, neck))
        part(cut(neck, body, head))
        part(body)
        part(head)
        part(oval(46f, 61f, 6f, 4.5f, -10f))
        eye(96f, 16.5f, 2.6f)
        line { moveTo(88f, 24.5f); quadTo(95f, 28.5f, 103f, 23f) }
    }
}

// =====================================================================================================================
// Deniz
// =====================================================================================================================

private object KidSea {

    val shapes: List<LibShape> by lazy { listOf(fish(), octopus(), whale(), crab(), starfish(), jellyfish(), turtle()) }

    private fun fish() = kid("fish", "Balık", 110f, 90f, listOf("balık", "deniz", "su", "fish", "sea")) {
        val body = blob(10f, 46f, 30f, 24f, 60f, 20f, 80f, 32f, 88f, 46f, 80f, 60f, 60f, 72f, 30f, 68f)
        part(cut(rpoly(4f, 78f, 46f, 104f, 20f, 97f, 46f, 104f, 72f), body))
        part(cut(shape { moveTo(34f, 30f); cubicTo(38f, 12f, 54f, 4f, 72f, 9f); cubicTo(66f, 17f, 67f, 25f, 72f, 32f) }, body))
        part(cut(rpoly(3f, 48f, 66f, 58f, 84f, 68f, 66f), body))
        part(body)
        parts(clipAll(shape { moveTo(46f, 6f); quadTo(40f, 46f, 46f, 86f); lineTo(58f, 86f); quadTo(52f, 46f, 58f, 6f) }, body))
        part(circle(26f, 40f, 6.5f))
        eye(24.8f, 40.5f, 3.2f)
        line { moveTo(16f, 51f); quadTo(20f, 55f, 25f, 52f) }
        part(circle(8f, 24f, 4.5f)); part(circle(16f, 12f, 3.2f))
    }

    private fun octopus() = kid("octopus", "Ahtapot", 100f, 100f, listOf("ahtapot", "deniz", "octopus", "sea")) {
        val head = oval(50f, 36f, 28f, 27f)
        val tents = listOf(
            floatArrayOf(29f, 48f, 20f, 58f, 10f, 62f, 5f, 56f),
            floatArrayOf(37f, 52f, 30f, 68f, 24f, 80f, 16f, 84f),
            floatArrayOf(45f, 54f, 42f, 72f, 40f, 86f, 34f, 92f),
            floatArrayOf(55f, 54f, 58f, 72f, 60f, 86f, 66f, 92f),
            floatArrayOf(63f, 52f, 70f, 68f, 76f, 80f, 84f, 84f),
            floatArrayOf(71f, 48f, 80f, 58f, 90f, 62f, 95f, 56f),
        ).map { tube(11f, 6f, *it) }
        tents.forEachIndexed { i, t -> part(cut(t, head, *tents.drop(i + 1).toTypedArray())) }
        part(head)
        part(circle(36f, 19f, 4f)); part(circle(63f, 18f, 3.2f))
        part(oval(40f, 36f, 7f, 8f)); part(oval(60f, 36f, 7f, 8f))
        eye(41f, 37.5f, 3.6f); eye(59f, 37.5f, 3.6f)
        smile(50f, 49f, 14f, 3.5f)
    }

    private fun whale() = kid("whale", "Balina", 120f, 90f, listOf("balina", "deniz", "whale", "sea")) {
        val body = blob(6f, 54f, 14f, 34f, 38f, 22f, 66f, 26f, 88f, 36f, 104f, 40f, 100f, 50f, 84f, 62f, 56f, 72f, 24f, 72f)
        val fluke = blob(98f, 44f, 100f, 32f, 104f, 18f, 110f, 28f, 117f, 26f, 114f, 38f, 104f, 46f)
        val flipper = leaf(38f, 59f, 56f, 77f, 0.3f)
        part(cut(fluke, body))
        parts(cutAll(body, flipper))
        parts(cutAll(clip(oval(48f, 83f, 48f, 21f), body), flipper))
        part(flipper)
        eye(26f, 46f, 2.8f)
        line { moveTo(10f, 56f); quadTo(18f, 61f, 28f, 58f) }
        part(drop(30f, 14f, 3.6f))
        part(rotate(drop(21f, 17f, 3f), -35f, 21f, 17f))
        part(rotate(drop(39f, 17f, 3f), 35f, 39f, 17f))
    }

    private fun crab() = kid("crab", "Yengeç", 110f, 90f, listOf("yengeç", "deniz", "kum", "crab", "beach")) {
        val body = oval(55f, 56f, 28f, 18f)
        val clawL = cut(circle(16f, 26f, 11f), poly(16f, 27f, 5f, 6f, 25f, 6f))
        val clawR = mirror(clawL, 55f)
        val armL = capsule(32f, 50f, 18f, 34f, 4.5f)
        val armR = mirror(armL, 55f)
        part(cut(armL, body, clawL)); part(cut(armR, body, clawR))
        part(clawL); part(clawR)
        val legs = listOf(capsule(31f, 62f, 14f, 70f, 3.4f), capsule(37f, 67f, 22f, 82f, 3.4f))
        for (l in legs) { part(cut(l, body)); part(cut(mirror(l, 55f), body)) }
        part(body)
        seg(46f, 40f, 44f, 28f); seg(64f, 40f, 66f, 28f)
        part(circle(44f, 23f, 5.5f)); part(circle(66f, 23f, 5.5f))
        eye(44.5f, 23.5f, 2.6f); eye(65.5f, 23.5f, 2.6f)
        smile(55f, 60f, 14f, 3f)
    }

    private fun starfish() = kid("starfish", "Deniz yıldızı", 100f, 100f, listOf("deniz yıldızı", "deniz", "kum", "starfish", "sea star")) {
        part(star(50f, 54f, 46f, 21f, 5, 0f, 7f))
        for (k in 0 until 5) {
            val a = Math.toRadians(-90.0 + 72.0 * k)
            part(circle(50f + 28f * cos(a).toFloat(), 54f + 28f * sin(a).toFloat(), 3.6f))
        }
        part(oval(39f, 59f, 3.2f, 2.2f)); part(oval(61f, 59f, 3.2f, 2.2f))
        eye(43f, 50f, 3f); eye(57f, 50f, 3f)
        smile(50f, 60f, 12f, 3.5f)
    }

    private fun jellyfish() = kid("jellyfish", "Denizanası", 100f, 100f, listOf("denizanası", "deniz", "jellyfish", "sea")) {
        val dome = shape { moveTo(16f, 46f); cubicTo(16f, 12f, 84f, 12f, 84f, 46f) }
        val frill = shape {
            moveTo(16f, 46f); lineTo(84f, 46f); lineTo(84f, 50f)
            val xs = floatArrayOf(84f, 67f, 50f, 33f, 16f)
            for (i in 0 until 4) quadTo((xs[i] + xs[i + 1]) / 2f, 60f, xs[i + 1], 50f)
        }
        for (x in floatArrayOf(24.5f, 41.5f, 58.5f, 75.5f)) part(cut(tube(8f, 5f, x, 52f, x - 4f, 64f, x + 3.5f, 76f, x - 2f, 90f), frill))
        part(frill)
        part(dome)
        part(circle(30f, 34f, 3f)); part(circle(70f, 34f, 3f))
        eye(42f, 32f, 3f); eye(58f, 32f, 3f)
        smile(50f, 38f, 10f, 1.8f)
    }

    private fun turtle() = kid("turtle", "Kaplumbağa", 110f, 80f, listOf("kaplumbağa", "deniz", "hayvan", "turtle", "tortoise")) {
        val shell = shape { moveTo(18f, 56f); cubicTo(18f, 14f, 86f, 14f, 86f, 56f) }
        val rim = rrect(12f, 52f, 80f, 10f, 5f)
        val head = circle(97f, 38f, 9.5f)
        part(cut(rpoly(2f, 24f, 50f, 1f, 58f, 24f, 62f), rim, shell))
        part(cut(rrect(22f, 58f, 12f, 14f, 5f), rim))
        part(cut(rrect(70f, 58f, 12f, 14f, 5f), rim))
        part(cut(capsule(84f, 55f, 96f, 41f, 6.5f), rim, head, shell))
        part(cut(shell, rim)); part(rim); part(head)
        part(hexagon(52f, 35f, 8.5f, 2f)); part(hexagon(32f, 43f, 6f, 1.5f)); part(hexagon(72f, 43f, 6f, 1.5f))
        eye(100f, 35f, 2.2f)
        line { moveTo(96f, 42f); quadTo(100.5f, 45f, 105f, 41f) }
    }
}

// =====================================================================================================================
// Taşıtlar
// =====================================================================================================================

private object KidVehicles {

    val shapes: List<LibShape> by lazy { listOf(car(), bus(), train(), plane(), sailboat(), helicopter(), tractor()) }

    private fun car() = kid("car", "Araba", 120f, 80f, listOf("araba", "otomobil", "taşıt", "car", "vehicle")) {
        val body = rrect(8f, 36f, 104f, 26f, 9f)
        val wheelL = circle(32f, 62f, 11f)
        val wheelR = circle(88f, 62f, 11f)
        val cabin = cut(rpoly(6f, 30f, 40f, 40f, 14f, 78f, 14f, 92f, 40f), body)
        part(cabin)
        part(clip(rpoly(3f, 38f, 42f, 45.5f, 19.5f, 58f, 19.5f, 58f, 42f), cabin))
        part(clip(rpoly(3f, 63f, 42f, 63f, 19.5f, 74.5f, 19.5f, 84f, 42f), cabin))
        val bodyV = cut(body, wheelL, wheelR)
        part(bodyV)
        part(clip(oval(110f, 45f, 6.5f, 5f), bodyV))
        seg(64f, 44f, 71f, 44f)
        part(wheelL); part(wheelR)
        part(circle(32f, 62f, 4.5f)); part(circle(88f, 62f, 4.5f))
    }

    private fun bus() = kid("bus", "Otobüs", 120f, 80f, listOf("otobüs", "okul", "taşıt", "bus", "school bus")) {
        val body = rrect(6f, 12f, 108f, 52f, 9f)
        val wheelL = circle(30f, 64f, 10f)
        val wheelR = circle(84f, 64f, 10f)
        val door = rrect(96f, 22f, 12f, 34f, 3f)
        val bodyV = cut(body, wheelL, wheelR)
        part(bodyV)
        part(clip(rect(0f, 42f, 96f, 6f), bodyV))
        for (x in floatArrayOf(14f, 34f, 54f, 74f)) part(rrect(x, 21f, 15f, 15f, 3f))
        part(door)
        seg(102f, 22f, 102f, 56f)
        part(clip(oval(114f, 59f, 6f, 4f), bodyV))
        part(wheelL); part(wheelR)
        part(circle(30f, 64f, 4f)); part(circle(84f, 64f, 4f))
    }

    private fun train() = kid("train", "Tren", 120f, 90f, listOf("tren", "lokomotif", "taşıt", "train", "locomotive")) {
        val roof = rrect(6f, 16f, 38f, 9f, 4f)
        val cab = rrect(10f, 22f, 30f, 48f, 4f)
        val boiler = rrect(36f, 36f, 64f, 34f, 10f)
        val base = rrect(6f, 68f, 108f, 8f, 4f)
        val w1 = circle(24f, 76f, 10f)
        val w2 = circle(56f, 78f, 8f)
        val w3 = circle(82f, 78f, 8f)
        part(cut(rpoly(2f, 74f, 40f, 73f, 24f, 68f, 14f, 90f, 14f, 85f, 24f, 84f, 40f), boiler))
        part(cut(boiler, cab, base))
        part(cut(cab, roof, base, w1))
        part(roof)
        part(rrect(16f, 30f, 18f, 15f, 3f))
        parts(cutAll(base, w1, w2, w3))
        part(w1); part(w2); part(w3)
        part(circle(24f, 76f, 4.5f))
        part(puff(56f, 7.5f, 10f, 5.5f, 7, 0.3f))
    }

    private fun plane() = kid("plane", "Uçak", 120f, 80f, listOf("uçak", "gökyüzü", "taşıt", "airplane", "plane")) {
        val fus = rrect(8f, 32f, 104f, 20f, 10f)
        val stab = rpoly(2.5f, 16f, 42f, 36f, 42f, 26f, 60f, 12f, 60f)
        val wing = rpoly(4f, 42f, 41f, 66f, 41f, 52f, 74f, 36f, 74f)
        part(cut(rpoly(3f, 10f, 36f, 14f, 8f, 26f, 8f, 38f, 36f), fus))
        val fusV = cut(fus, stab, wing)
        part(fusV)
        part(clip(poly(100f, 26f, 125f, 26f, 125f, 41f, 100f, 41f), fusV))
        for (x in floatArrayOf(72f, 81f, 90f)) part(circle(x, 40f, 3.3f))
        part(stab); part(wing)
    }

    private fun sailboat() = kid("sailboat", "Yelkenli", 100f, 100f, listOf("yelkenli", "gemi", "tekne", "deniz", "taşıt", "sailboat", "boat")) {
        val water = shape {
            moveTo(2f, 80f); quadTo(13f, 72f, 24f, 80f); quadTo(35f, 88f, 46f, 80f); quadTo(57f, 72f, 68f, 80f)
            quadTo(79f, 88f, 90f, 80f); quadTo(95f, 76f, 98f, 78f); lineTo(98f, 96f); lineTo(2f, 96f)
        }
        val hull = cut(rpoly(4f, 10f, 64f, 90f, 64f, 78f, 84f, 22f, 84f), water)
        val mast = cut(rect(48f, 10f, 5f, 56f), hull)
        part(mast)
        part(cut(rpoly(1.5f, 51f, 10f, 66f, 14f, 51f, 18f), mast))
        part(rpoly(2f, 45f, 15f, 45f, 58f, 14f, 58f))
        part(rpoly(2f, 56f, 20f, 56f, 58f, 82f, 58f))
        part(hull)
        for (x in floatArrayOf(34f, 50f, 66f)) part(circle(x, 70f, 3f))
        part(water)
    }

    private fun helicopter() = kid("helicopter", "Helikopter", 120f, 80f, listOf("helikopter", "pervane", "taşıt", "helicopter")) {
        val cabin = blob(14f, 44f, 24f, 26f, 50f, 22f, 70f, 30f, 74f, 46f, 66f, 60f, 40f, 64f, 20f, 60f)
        val boom = tube(12f, 6f, 64f, 40f, 88f, 36f, 106f, 32f)
        val rotorT = circle(111f, 15f, 5f)
        val rotor = rrect(6f, 6f, 78f, 6f, 3f)
        val rail = rrect(14f, 70f, 56f, 5f, 2.5f)
        part(cut(rpoly(2f, 103f, 34f, 108f, 14f, 115f, 16f, 113f, 36f), boom, rotorT))
        part(cut(boom, cabin))
        part(rotorT)
        part(cut(rect(41f, 9f, 6f, 17f), cabin, rotor))
        part(rotor)
        part(cut(rect(28f, 58f, 5f, 14f), cabin, rail)); part(cut(rect(54f, 58f, 5f, 14f), cabin, rail))
        part(rail)
        part(cabin)
        part(clip(rpoly(4f, 0f, 20f, 44f, 20f, 44f, 44f, 0f, 44f), cabin))
    }

    private fun tractor() = kid("tractor", "Traktör", 120f, 90f, listOf("traktör", "çiftlik", "taşıt", "tractor", "farm")) {
        val rearW = circle(34f, 64f, 22f)
        val frontW = circle(94f, 74f, 12f)
        val hood = rrect(50f, 44f, 54f, 26f, 6f)
        val roof = rrect(18f, 6f, 42f, 8f, 3f)
        val cap = rrect(80f, 22f, 10f, 5f, 2f)
        part(cut(rrect(82f, 26f, 6f, 20f, 2f), hood, cap)); part(cap)
        part(cut(rrect(22f, 12f, 34f, 42f, 4f), roof, rearW, hood)); part(roof)
        part(rrect(28f, 18f, 22f, 17f, 3f))
        val hoodV = cut(hood, rearW, frontW)
        part(hoodV)
        part(clip(oval(104f, 53f, 6f, 6.5f), hoodV))
        part(rearW); part(circle(34f, 64f, 9f))
        part(frontW); part(circle(94f, 74f, 5f))
    }
}

// =====================================================================================================================
// Meyve ve yiyecek
// =====================================================================================================================

private object KidFood {

    val shapes: List<LibShape> by lazy { listOf(apple(), banana(), strawberry(), watermelon(), grapes(), iceCream(), cupcake()) }

    private fun apple() = kid("apple", "Elma", 100f, 100f, listOf("elma", "meyve", "apple", "fruit")) {
        val apple = shape {
            moveTo(50f, 30f); cubicTo(60f, 20f, 90f, 22f, 90f, 52f); cubicTo(90f, 80f, 70f, 94f, 58f, 92f)
            cubicTo(54f, 91f, 46f, 91f, 42f, 92f); cubicTo(30f, 94f, 10f, 80f, 10f, 52f); cubicTo(10f, 22f, 40f, 20f, 50f, 30f)
        }
        val leaf = shape { moveTo(54f, 22f); quadTo(62f, 6f, 82f, 10f); quadTo(74f, 26f, 54f, 22f) }
        part(cut(tube(6f, 5f, 50f, 34f, 51f, 20f, 55f, 10f), apple, leaf))
        part(apple)
        part(leaf)
        line { moveTo(58f, 20f); quadTo(68f, 15f, 77f, 12f) }
        part(oval(28f, 48f, 5f, 9f, 20f))
    }

    private fun banana() = kid("banana", "Muz", 100f, 100f, listOf("muz", "meyve", "banana", "fruit")) {
        // Gülümseyen hilal biçimli muz: alt (dış) ve üst (iç) yüz, sap; ucu koyu
        val stem = rpoly(1.5f, 79f, 32f, 85f, 15f, 93f, 18f, 92f, 37f)
        val banana = cut(shape {
            moveTo(8f, 36f); cubicTo(8f, 92f, 68f, 104f, 90f, 36f); lineTo(83f, 27f)
            cubicTo(66f, 76f, 26f, 76f, 15f, 31f)
        }, stem)
        val above = shape { moveTo(3f, 41f); cubicTo(22f, 82f, 62f, 94f, 95f, 30f); lineTo(100f, 0f); lineTo(0f, 0f) }
        part(cut(banana, above))
        part(clip(banana, above))
        ink(clip(circle(10f, 33f, 4.2f), banana))
        part(stem)
    }

    private fun strawberry() = kid("strawberry", "Çilek", 100f, 100f, listOf("çilek", "meyve", "strawberry", "fruit")) {
        val berry = shape {
            moveTo(50f, 94f); cubicTo(28f, 88f, 10f, 58f, 16f, 40f); cubicTo(20f, 26f, 40f, 24f, 50f, 30f)
            cubicTo(60f, 24f, 80f, 26f, 84f, 40f); cubicTo(90f, 58f, 72f, 88f, 50f, 94f)
        }
        val calyx = scaleY(star(50f, 30f, 22f, 8f, 5, 180f, 2.5f), 0.62f, 30f)
        part(cut(berry, calyx))
        part(cut(tube(5f, 4f, 50f, 28f, 51f, 16f, 55f, 8f), calyx))
        part(calyx)
        part(oval(30f, 52f, 3.5f, 7f, 20f))
        for (s in listOf(41f to 52f, 59f to 52f, 72f to 54f, 50f to 63f, 36f to 66f, 64f to 66f, 44f to 76f, 56f to 76f, 50f to 86f)) {
            ink(oval(s.first, s.second, 1.4f, 2.3f, (s.first - 50f) * -0.8f))
        }
    }

    private fun watermelon() = kid("watermelon", "Karpuz", 110f, 60f, listOf("karpuz", "meyve", "dilim", "watermelon", "fruit")) {
        val cx = 55f; val cy = 8f
        val a1 = arc(cx, cy, 47f, 47f, 0f, 180f)
        val a2 = arc(cx, cy, 41f, 41f, 0f, 180f)
        val a3 = arc(cx, cy, 36f, 36f, 0f, 180f)
        part(a1 + a2.asReversed())
        part(a2 + a3.asReversed())
        part(a3)
        for (s in listOf(30f to 25f, 60f to 25f, 90f to 25f, 120f to 25f, 150f to 25f, 60f to 13f, 120f to 13f)) {
            val a = Math.toRadians(s.first.toDouble())
            ink(oval(cx + s.second * cos(a).toFloat(), cy + s.second * sin(a).toFloat(), 1.8f, 2.8f, s.first - 90f))
        }
    }

    private fun grapes() = kid("grapes", "Üzüm", 100f, 100f, listOf("üzüm", "salkım", "meyve", "grapes", "fruit")) {
        val centers = listOf(26f to 36f, 42f to 36f, 58f to 36f, 74f to 36f, 34f to 51f, 50f to 51f, 66f to 51f, 42f to 66f, 58f to 66f, 50f to 81f)
        val gr = centers.map { circle(it.first, it.second, 9f) }
        val leaf = shape { moveTo(53f, 20f); quadTo(62f, 4f, 84f, 8f); quadTo(78f, 26f, 53f, 20f) }
        part(cut(tube(5f, 4f, 50f, 30f, 49f, 16f, 53f, 7f), leaf, *gr.toTypedArray()))
        gr.forEachIndexed { i, g -> part(cut(g, *gr.drop(i + 1).toTypedArray(), leaf)) }
        part(leaf)
        line { moveTo(57f, 18f); quadTo(68f, 14f, 79f, 10f) }
    }

    private fun iceCream() = kid("ice_cream", "Dondurma", 80f, 112f, listOf("dondurma", "külah", "tatlı", "ice cream", "dessert")) {
        val scoop1 = shape {
            moveTo(12f, 62f); cubicTo(8f, 40f, 72f, 40f, 68f, 62f)
            quadTo(66f, 68f, 61f, 65f); quadTo(58f, 72f, 52f, 66f); quadTo(47f, 70f, 42f, 66f)
            quadTo(36f, 73f, 31f, 65f); quadTo(26f, 70f, 21f, 66f); quadTo(14f, 68f, 12f, 62f)
        }
        val scoop2 = circle(40f, 36f, 18f)
        val cherry = circle(47f, 16f, 6.5f)
        val cone = cut(rpoly(3f, 17f, 58f, 63f, 58f, 40f, 108f), scoop1)
        part(cone)
        for (x0 in floatArrayOf(28f, 44f)) { val t = (63f - x0) / 46f; strokeInside(listOf(P(x0, 58f), P(x0 + 23f * t, 58f + 50f * t)), cone) }
        for (x0 in floatArrayOf(52f, 36f)) { val t = (x0 - 17f) / 46f; strokeInside(listOf(P(x0, 58f), P(x0 - 23f * t, 58f + 50f * t)), cone) }
        part(cut(scoop1, scoop2))
        part(cut(scoop2, cherry))
        part(cherry)
        line { moveTo(48f, 10f); quadTo(50f, 4f, 57f, 2.5f) }
        ink(capsule(31f, 30f, 34f, 27f, 0.9f)); ink(capsule(44f, 26f, 47f, 28f, 0.9f))
        ink(capsule(36f, 42f, 39f, 44f, 0.9f)); ink(capsule(48f, 38f, 50f, 35f, 0.9f))
    }

    private fun cupcake() = kid("cupcake", "Kek", 100f, 100f, listOf("kek", "cupcake", "pasta", "tatlı", "muffin", "dessert")) {
        val tier1 = rrect(14f, 44f, 72f, 18f, 9f)
        val tier2 = rrect(22f, 30f, 56f, 18f, 9f)
        val tier3 = shape { moveTo(32f, 36f); cubicTo(30f, 22f, 46f, 16f, 50f, 10f); cubicTo(54f, 16f, 70f, 22f, 68f, 36f) }
        val cherry = circle(50f, 11f, 6.5f)
        val wrapper = cut(rpoly(3f, 20f, 58f, 80f, 58f, 72f, 94f, 28f, 94f), tier1)
        part(wrapper)
        for (xt in floatArrayOf(32f, 44f, 56f, 68f)) strokeInside(listOf(P(xt, 58f), P(28f + (xt - 20f) * 44f / 60f, 94.5f)), wrapper)
        part(cut(tier1, tier2))
        part(cut(tier2, tier3))
        part(cut(tier3, cherry))
        part(cherry)
        line { moveTo(51f, 5f); quadTo(54f, 1.5f, 59f, 2f) }
        ink(capsule(24f, 52f, 27f, 50f, 0.9f)); ink(capsule(40f, 55f, 43f, 56f, 0.9f)); ink(capsule(60f, 54f, 63f, 52f, 0.9f))
        ink(capsule(74f, 51f, 76f, 54f, 0.9f)); ink(capsule(32f, 40f, 35f, 39f, 0.9f)); ink(capsule(64f, 39f, 67f, 41f, 0.9f))
    }
}

// =====================================================================================================================
// Doğa ve gökyüzü
// =====================================================================================================================

private object KidNature {

    val shapes: List<LibShape> by lazy { listOf(sun(), rainbow(), flower(), tree(), mushroom(), butterfly(), ladybug()) }

    private fun sun() = kid("sun", "Güneş", 100f, 100f, listOf("güneş", "gökyüzü", "hava", "sun", "sky")) {
        part(circle(50f, 50f, 23f))
        for (k in 0 until 8) {
            val a = -90.0 + 45.0 * k
            fun pt(r: Float, d: Double) = P(50f + r * cos(Math.toRadians(a + d)).toFloat(), 50f + r * sin(Math.toRadians(a + d)).toFloat())
            val b1 = pt(28.5f, -13.0); val b2 = pt(28.5f, 13.0); val t = pt(46f, 0.0)
            part(rounded(listOf(b1, t, b2), 2.5f))
        }
        part(oval(35f, 54f, 3.6f, 2.5f)); part(oval(65f, 54f, 3.6f, 2.5f))
        eye(42f, 45f, 3f); eye(58f, 45f, 3f)
        smile(50f, 56f, 16f, 4f)
    }

    private fun rounded(p: List<P>, r: Float) = KidPen.rounded(p, r)

    private fun rainbow() = kid("rainbow", "Gökkuşağı", 120f, 80f, listOf("gökkuşağı", "bulut", "gökyüzü", "rainbow", "cloud")) {
        val radii = floatArrayOf(52f, 45.5f, 39f, 32.5f, 26f, 19.5f)
        val arcs = radii.map { arc(60f, 64f, it, it, 180f, 180f) }
        val cloudL = cloud(24f, 66f, 46f, 22f)
        val cloudR = cloud(96f, 66f, 46f, 22f)
        for (i in 0 until 5) part(cut(arcs[i] + arcs[i + 1].asReversed(), cloudL, cloudR))
        part(cloudL); part(cloudR)
    }

    private fun flower() = kid("flower", "Çiçek", 100f, 110f, listOf("çiçek", "papatya", "bahçe", "flower", "garden")) {
        val center = circle(50f, 37f, 11.5f)
        val petals = (0 until 6).map { k ->
            val a = -90.0 + 60.0 * k
            oval(50f + 21f * cos(Math.toRadians(a)).toFloat(), 37f + 21f * sin(Math.toRadians(a)).toFloat(), 11f, 7.5f, a.toFloat())
        }
        val leafL = shape { moveTo(48f, 88f); quadTo(36f, 68f, 18f, 72f); quadTo(26f, 90f, 48f, 88f) }
        val leafR = shape { moveTo(52f, 78f); quadTo(64f, 58f, 82f, 62f); quadTo(74f, 80f, 52f, 78f) }
        part(cut(rrect(47f, 56f, 6f, 48f, 3f), *petals.toTypedArray(), leafL, leafR))
        part(leafL); part(leafR)
        line { moveTo(45f, 86f); quadTo(34f, 78f, 24f, 75f) }
        line { moveTo(55f, 76f); quadTo(66f, 68f, 76f, 65f) }
        petals.forEachIndexed { i, p -> part(cut(p, center, *petals.drop(i + 1).toTypedArray())) }
        part(center)
    }

    private fun tree() = kid("tree", "Ağaç", 100f, 110f, listOf("ağaç", "elma ağacı", "orman", "doğa", "tree")) {
        val crown = puff(50f, 40f, 38f, 30f, 9, 0.32f)
        val grass = grass(14f, 86f, 107f, 95f, 102f, 9)
        part(cut(shape { moveTo(38f, 104f); quadTo(44f, 86f, 43f, 62f); lineTo(57f, 62f); quadTo(56f, 86f, 62f, 104f) }, crown, grass))
        part(oval(51f, 84f, 3f, 4.5f))
        part(grass)
        part(crown)
        for (p in listOf(32f to 30f, 60f to 22f, 68f to 46f, 40f to 52f)) part(circle(p.first, p.second, 4.8f))
    }

    private fun mushroom() = kid("mushroom", "Mantar", 100f, 100f, listOf("mantar", "orman", "doğa", "mushroom")) {
        val cap = shape {
            moveTo(10f, 52f); cubicTo(10f, 8f, 90f, 8f, 90f, 52f)
            quadTo(80f, 58f, 70f, 54f); quadTo(60f, 60f, 50f, 55f); quadTo(40f, 60f, 30f, 54f); quadTo(20f, 58f, 10f, 52f)
        }
        val capS = shape { moveTo(70f, 80f); cubicTo(70f, 64f, 94f, 64f, 94f, 80f); quadTo(82f, 84f, 70f, 80f) }
        val grass = grass(6f, 94f, 98f, 84f, 91f, 10)
        part(cut(rrect(36f, 48f, 28f, 42f, 12f), cap, grass))
        part(cut(rrect(77f, 76f, 10f, 16f, 4f), capS, grass))
        part(cap)
        for (s in listOf(floatArrayOf(26f, 37f, 6f), floatArrayOf(50f, 30f, 6.5f), floatArrayOf(73f, 36f, 6f), floatArrayOf(40f, 47f, 3.6f))) part(circle(s[0], s[1], s[2]))
        part(capS)
        part(grass)
        eye(44f, 64f, 2.6f); eye(56f, 64f, 2.6f)
        smile(50f, 70f, 8f, 2.5f)
    }

    private fun butterfly() = kid("butterfly", "Kelebek", 110f, 90f, listOf("kelebek", "böcek", "bahçe", "butterfly", "insect")) {
        val body = capsule(55f, 30f, 55f, 80f, 5f)
        val head = circle(55f, 22f, 7.5f)
        val upL = blob(51f, 36f, 36f, 14f, 14f, 12f, 6f, 30f, 14f, 48f, 51f, 52f)
        val loL = blob(51f, 54f, 26f, 54f, 14f, 68f, 22f, 84f, 42f, 80f)
        val upR = mirror(upL, 55f)
        val loR = mirror(loL, 55f)
        part(cut(loL, body, upL)); part(cut(loR, body, upR))
        part(cut(upL, body, head)); part(cut(upR, body, head))
        part(circle(28f, 30f, 7f)); part(circle(82f, 30f, 7f))
        part(circle(30f, 68f, 5f)); part(circle(80f, 68f, 5f))
        part(cut(body, head)); part(head)
        line { moveTo(52f, 16f); quadTo(46f, 6f, 40f, 6f) }; dot(40f, 6f, 2.4f)
        line { moveTo(58f, 16f); quadTo(64f, 6f, 70f, 6f) }; dot(70f, 6f, 2.4f)
        dot(52.5f, 21f, 1.5f); dot(57.5f, 21f, 1.5f)
    }

    private fun ladybug() = kid("ladybug", "Uğur böceği", 100f, 100f, listOf("uğur böceği", "böcek", "bahçe", "ladybug", "insect")) {
        val left = arc(50f, 56f, 36f, 36f, 90f, 180f)
        val right = arc(50f, 56f, 36f, 36f, 270f, 180f)
        val shell = KidPen.clean(left + right)
        for (l in listOf(floatArrayOf(16f, 46f, 7f, 40f), floatArrayOf(14.5f, 58f, 5f, 58f), floatArrayOf(17f, 72f, 8f, 78f))) {
            seg(l[0], l[1], l[2], l[3]); seg(100f - l[0], l[1], 100f - l[2], l[3])
        }
        line { moveTo(45f, 7f); quadTo(40f, 1.5f, 34f, 3f) }
        line { moveTo(55f, 7f); quadTo(60f, 1.5f, 66f, 3f) }
        part(cut(circle(50f, 20f, 15f), shell))
        part(left); part(right)
        for (s in listOf(floatArrayOf(36f, 44f, 5.5f), floatArrayOf(30f, 62f, 5f), floatArrayOf(40f, 78f, 5f))) {
            part(circle(s[0], s[1], s[2])); part(circle(100f - s[0], s[1], s[2]))
        }
        part(circle(44f, 13f, 3.2f)); part(circle(56f, 13f, 3.2f))
        dot(44.5f, 13.5f, 1.6f); dot(55.5f, 13.5f, 1.6f)
    }
}

// =====================================================================================================================
// Uzay
// =====================================================================================================================

private object KidSpace {

    val shapes: List<LibShape> by lazy { listOf(rocket(), planet(), moon(), ufo(), astronaut(), earth()) }

    private fun rocket() = kid("rocket", "Roket", 90f, 110f, listOf("roket", "uzay", "uzay gemisi", "rocket", "space")) {
        val full = shape {
            moveTo(45f, 6f); cubicTo(62f, 18f, 66f, 40f, 64f, 62f); lineTo(62f, 84f); lineTo(28f, 84f); lineTo(26f, 62f)
            cubicTo(24f, 40f, 28f, 18f, 45f, 6f)
        }
        val top = rect(0f, 0f, 90f, 30f)
        val nozzle = cut(rpoly(2f, 34f, 82f, 56f, 82f, 59f, 92f, 31f, 92f), full)
        val flame = cut(flipY(drop(45f, 92f, 7f), 92f), nozzle, full)
        part(flame)
        part(cut(flipY(drop(45f, 92f, 3.6f), 92f), nozzle, full))
        part(nozzle)
        val finL = rpoly(3f, 30f, 56f, 9f, 80f, 9f, 94f, 32f, 82f)
        part(cut(finL, full)); part(cut(mirror(finL, 45f), full))
        part(clip(full, top))
        part(cut(full, top))
        part(circle(45f, 50f, 10.5f)); part(circle(45f, 50f, 6.5f))
        part(star(14f, 22f, 7f, 3f, 5, 0f, 1.2f)); part(star(77f, 36f, 5.5f, 2.4f, 5, 0f, 1f))
    }

    private fun planet() = kid("planet", "Gezegen", 120f, 90f, listOf("gezegen", "satürn", "halka", "uzay", "planet", "saturn")) {
        val cx = 60f; val cy = 46f
        val planet = circle(cx, cy, 25f)
        val rot = -14f
        val oF = arc(cx, cy, 54f, 15f, 0f, 180f, rot); val iF = arc(cx, cy, 37f, 8.5f, 0f, 180f, rot)
        val oB = arc(cx, cy, 54f, 15f, 180f, 180f, rot); val iB = arc(cx, cy, 37f, 8.5f, 180f, 180f, rot)
        val front = oF + iF.asReversed()
        parts(cutAll(oB + iB.asReversed(), planet))
        parts(cutAll(planet, front))
        part(front)
        part(circle(16f, 16f, 7f))
        part(star(104f, 14f, 6f, 2.6f, 5, 0f, 1.2f)); part(star(101f, 77f, 6f, 2.7f, 5, 0f, 1f))
    }

    private fun moon() = kid("moon", "Ay", 100f, 100f, listOf("ay", "hilal", "gece", "yıldız", "uzay", "moon", "night")) {
        val cloud = cloud(72f, 74f, 38f, 20f)
        part(cut(cut(circle(46f, 50f, 36f), circle(62f, 40f, 30f)), cloud))
        part(cloud)
        line { moveTo(19f, 44f); quadTo(23f, 48f, 27f, 44f) }
        part(oval(24f, 54f, 3.6f, 2.5f))
        smile(31f, 62f, 10f, 2f)
        part(star(80f, 18f, 7f, 3f, 5, 0f, 1.2f)); part(star(92f, 46f, 4.5f, 2f, 5, 0f, 1f)); part(star(14f, 88f, 5f, 2.2f, 5, 0f, 1f))
    }

    private fun ufo() = kid("ufo", "Uçan daire", 110f, 80f, listOf("uçan daire", "ufo", "uzaylı", "uzay", "flying saucer", "alien")) {
        val saucer = oval(55f, 50f, 48f, 15f)
        val band = rect(0f, 51f, 110f, 20f)
        part(cut(poly(40f, 60f, 70f, 60f, 84f, 78f, 26f, 78f), saucer))
        part(cut(shape { moveTo(31f, 46f); cubicTo(31f, 8f, 79f, 8f, 79f, 46f) }, saucer))
        part(cut(oval(55f, 29f, 7.5f, 7f), saucer))
        dot(52f, 28f, 1.8f); dot(58f, 28f, 1.8f)
        smile(55f, 31.5f, 5f, 0.8f)
        part(cut(saucer, band))
        part(clip(saucer, band))
        for (p in listOf(31f to 57f, 55f to 58f, 79f to 57f)) part(circle(p.first, p.second, 3f))
    }

    private fun astronaut() = kid("astronaut", "Astronot", 100f, 110f, listOf("astronot", "uzay", "kask", "astronaut", "space")) {
        val helmet = circle(50f, 28f, 21f)
        val body = rrect(30f, 44f, 40f, 34f, 12f)
        val gloveL = circle(14f, 35f, 7f)
        val gloveR = circle(84f, 72f, 7f)
        val bootL = rrect(30f, 88f, 17f, 11f, 5f)
        val bootR = rrect(53f, 88f, 17f, 11f, 5f)
        part(cut(rrect(34f, 72f, 13f, 20f, 5f), body, bootL)); part(cut(rrect(53f, 72f, 13f, 20f, 5f), body, bootR))
        part(bootL); part(bootR)
        part(cut(capsule(34f, 54f, 16f, 38f, 6.5f), body, gloveL)); part(cut(capsule(66f, 54f, 82f, 70f, 6.5f), body, gloveR))
        part(gloveL); part(gloveR)
        part(cut(body, helmet))
        part(rrect(40f, 54f, 20f, 14f, 3f))
        dot(45f, 61f, 2f); dot(55f, 61f, 2f)
        part(helmet)
        part(rrect(36f, 19f, 28f, 18f, 9f))
        line { moveTo(41f, 25f); quadTo(42.5f, 22f, 47f, 22f) }
    }

    private fun earth() = kid("earth", "Dünya", 100f, 100f, listOf("dünya", "gezegen", "kıta", "uzay", "earth", "globe")) {
        val globe = circle(50f, 50f, 40f)
        part(globe)
        part(clip(blob(12f, 30f, 28f, 14f, 46f, 16f, 44f, 30f, 34f, 38f, 30f, 50f, 16f, 50f), globe))
        part(clip(blob(60f, 22f, 78f, 18f, 92f, 38f, 84f, 52f, 72f, 50f, 64f, 36f), globe))
        part(clip(blob(38f, 70f, 56f, 66f, 68f, 76f, 60f, 92f, 46f, 94f, 38f, 84f), globe))
        part(blob(76f, 64f, 83f, 62f, 85f, 70f, 78f, 73f))
        eye(42f, 45f, 2.8f); eye(56f, 45f, 2.8f)
        smile(49f, 53f, 12f, 2.5f)
        part(circle(88f, 12f, 6f))
        part(star(12f, 88f, 5.5f, 2.4f, 5, 0f, 1f))
    }
}

// =====================================================================================================================
// Oyuncak ve eşyalar
// =====================================================================================================================

private object KidToys {

    val shapes: List<LibShape> by lazy { listOf(teddy(), ball(), kite(), balloons(), robot(), house(), umbrella()) }

    private fun teddy() = kid("teddy", "Oyuncak ayı", 100f, 100f, listOf("oyuncak ayı", "ayıcık", "oyuncak", "teddy bear", "toy")) {
        val head = circle(50f, 32f, 20f)
        val armL = oval(26f, 64f, 7f, 13f, 25f)
        val armR = mirror(armL, 50f)
        val legL = oval(34f, 88f, 10f, 8f)
        val legR = mirror(legL, 50f)
        part(cut(oval(50f, 70f, 22f, 22f), head, armL, armR, legL, legR))
        part(oval(50f, 71f, 11f, 11f))
        part(armL); part(armR); part(legL); part(legR)
        part(oval(34f, 89f, 5f, 4.5f)); part(oval(66f, 89f, 5f, 4.5f))
        part(cut(circle(33f, 16f, 8f), head)); part(cut(circle(67f, 16f, 8f), head))
        part(head)
        part(oval(50f, 40f, 9f, 7f))
        ink(oval(50f, 36.5f, 3.6f, 2.6f))
        line { moveTo(50f, 39f); lineTo(50f, 42f) }
        line { moveTo(45.5f, 42f); quadTo(48f, 45f, 50f, 42f); quadTo(52f, 45f, 54.5f, 42f) }
        eye(42f, 28f, 2.8f); eye(58f, 28f, 2.8f)
    }

    private fun ball() = kid("ball", "Top", 100f, 100f, listOf("top", "plaj topu", "oyuncak", "ball", "beach ball", "toy")) {
        val r = 42f
        val e = r * 0.7071f
        val cL = arc(50f, 50f, r, r, 90f, 180f)
        val cR = arc(50f, 50f, r, r, 270f, 180f)
        val eL = arc(50f, 50f, e, r, 90f, 180f)
        val eR = arc(50f, 50f, e, r, 270f, 180f)
        val gores = listOf(cL + eL.asReversed(), eL, eR, cR + eR.asReversed()).map { rotate(KidPen.clean(it), -25f, 50f, 50f) }
        val cap = rotate(circle(50f, 50f - r, 7f), -25f, 50f, 50f)
        for (g in gores) part(cut(g, cap))
        part(cap)
        part(rotate(oval(36f, 30f, 3.5f, 7f), -25f, 50f, 50f))
    }

    private fun kite() = kid("kite", "Uçurtma", 100f, 110f, listOf("uçurtma", "rüzgâr", "oyuncak", "kite", "toy")) {
        part(poly(50f, 4f, 50f, 28f, 20f, 28f)); part(poly(50f, 4f, 80f, 28f, 50f, 28f))
        part(poly(50f, 28f, 80f, 28f, 50f, 64f)); part(poly(50f, 28f, 50f, 64f, 20f, 28f))
        val string = VectorBuilder.catmullRom(poly(50f, 64f, 58f, 73f, 46f, 85f, 58f, 97f, 52f, 107f), false, 0.5f, 12)
        val bows = listOf(bow(58f, 73f, 7f, 4.5f, 12f), bow(46f, 85f, 7f, 4.5f, -12f), bow(58f, 97f, 7f, 4.5f, 12f))
        strokeOutside(string, *bows.toTypedArray())
        parts(bows)
    }

    private fun balloons() = kid("balloons", "Balonlar", 100f, 110f, listOf("balon", "balonlar", "parti", "doğum günü", "balloons", "party")) {
        // Düğüm balonla tek parça (ayrı boyanamayacak kadar küçük)
        val b1 = union(oval(29f, 34f, 18f, 22f), rpoly(1.2f, 25f, 61f, 33f, 61f, 29f, 52f))
        val b2 = union(oval(71f, 32f, 18f, 22f), rpoly(1.2f, 67f, 59f, 75f, 59f, 71f, 50f))
        val b3 = union(oval(50f, 44f, 19f, 23f), rpoly(1.2f, 46f, 72f, 54f, 72f, 50f, 63f))
        val bowP = bow(50f, 97f, 7.5f, 5f)
        val covers = arrayOf(bowP, b1, b2, b3)
        strokeOutside(VectorBuilder.catmullRom(poly(29f, 60f, 34f, 77f, 46f, 94f), false, 0.5f, 12), *covers)
        strokeOutside(VectorBuilder.catmullRom(poly(71f, 58f, 66f, 77f, 54f, 94f), false, 0.5f, 12), *covers)
        strokeOutside(VectorBuilder.catmullRom(poly(50f, 71f, 52f, 83f, 50f, 93f), false, 0.5f, 12), *covers)
        part(cut(b1, b3)); part(cut(b2, b3))
        part(b3)
        part(bowP)
        line { moveTo(18f, 34f); quadTo(18f, 22f, 26f, 17f) }
        line { moveTo(82f, 32f); quadTo(82f, 20f, 74f, 15f) }
        line { moveTo(39f, 42f); quadTo(39f, 31f, 46f, 26f) }
    }

    private fun robot() = kid("robot", "Robot", 100f, 110f, listOf("robot", "oyuncak", "makine", "robot", "toy")) {
        val head = rrect(28f, 16f, 44f, 32f, 8f)
        val body = rrect(24f, 54f, 52f, 34f, 8f)
        part(circle(50f, 7f, 4.5f))
        seg(50f, 11.5f, 50f, 17f)
        part(cut(rect(43f, 44f, 14f, 14f), head, body))
        val armL = capsule(26f, 62f, 12f, 82f, 5f)
        part(cut(armL, body)); part(cut(mirror(armL, 50f), body))
        part(cut(rrect(32f, 86f, 13f, 18f, 4f), body)); part(cut(rrect(55f, 86f, 13f, 18f, 4f), body))
        part(body)
        part(rrect(36f, 61f, 28f, 18f, 4f))
        dot(44f, 70f, 2.2f); dot(50f, 70f, 2.2f); dot(56f, 70f, 2.2f)
        part(head)
        part(circle(40f, 29f, 6f)); part(circle(60f, 29f, 6f))
        eye(41f, 29.5f, 2.8f); eye(59f, 29.5f, 2.8f)
        val mouth = rrect(38f, 38f, 24f, 5.5f, 2.75f)
        part(mouth)
        for (x in floatArrayOf(44f, 50f, 56f)) strokeInside(listOf(P(x, 36f), P(x, 46f)), mouth)
    }

    private fun house() = kid("house", "Ev", 100f, 100f, listOf("ev", "yuva", "çatı", "house", "home")) {
        val roof = rpoly(3f, 8f, 48f, 50f, 10f, 92f, 48f)
        part(cut(rect(64f, 16f, 10f, 22f), roof))
        part(cut(rect(18f, 44f, 64f, 50f), roof))
        part(roof)
        part(circle(50f, 33f, 5.5f))
        part(shape { moveTo(42f, 94f); lineTo(42f, 74f); arcTo(50f, 74f, 8f, 8f, 180f, 180f); lineTo(58f, 94f) })
        dot(54.5f, 84f, 1.6f)
        for (wx in floatArrayOf(24f, 62f)) {
            part(rect(wx, 56f, 14f, 14f))
            seg(wx + 7f, 56f, wx + 7f, 70f); seg(wx, 63f, wx + 14f, 63f)
        }
    }

    private fun umbrella() = kid("umbrella", "Şemsiye", 100f, 110f, listOf("şemsiye", "yağmur", "hava", "umbrella", "rain")) {
        val tip = P(50f, 14f)
        val bx = floatArrayOf(8f, 29f, 50f, 71f, 92f)
        val ctrl = listOf(P(12f, 16f), P(31f, 20f), P(50f, 33f), P(69f, 20f), P(88f, 16f))
        val ribs = bx.indices.map { k -> List(25) { s -> KidPen.quad(tip, ctrl[k], P(bx[k], 52f), s / 24f) } }
        val scallops = (0 until 4).map { k -> List(17) { s -> KidPen.quad(P(bx[k], 52f), P((bx[k] + bx[k + 1]) / 2f, 44f), P(bx[k + 1], 52f), s / 16f) } }
        val panels = (0 until 4).map { k -> KidPen.clean(ribs[k] + scallops[k] + ribs[k + 1].asReversed()) }
        val canopy = KidPen.clean(ribs[0] + scallops.flatten() + ribs[4].asReversed())
        val finial = circle(50f, 11f, 4f)
        part(cut(tube(6f, 6f, 50f, 46f, 50f, 90f, 49f, 98f, 43f, 100f, 38f, 95f), canopy))
        for (p in panels) part(cut(p, finial))
        part(finial)
        part(drop(14f, 76f, 3.6f)); part(drop(86f, 70f, 3.6f)); part(drop(80f, 96f, 3.2f)); part(drop(22f, 100f, 3.2f))
    }
}
