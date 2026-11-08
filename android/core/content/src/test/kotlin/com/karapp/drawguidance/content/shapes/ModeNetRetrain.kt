package com.karapp.drawguidance.content.shapes

import com.karapp.drawguidance.content.Preview
import com.karapp.drawguidance.content.vector.P
import org.junit.Assume
import org.junit.Test
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import java.io.File

/**
 * v37 modların ağlarının yeniden eğitimi (tek komut, tekrarlanabilir):
 *
 * ```
 * ./gradlew :core:content:test --tests '*ModeNetRetrain*' -PretrainNets=1
 * ```
 *
 * 1. Kütüphane şablonları ([LibraryLineArt]): v36 nesneleri, çizgi roman karakterleri ve efektleri çizgi haline
 *    çevrilip `src/main/resources/shapes/library_templates.bin` yazılır (karakterler değişince komut yeniden çalıştırılır).
 * 2. ART ve COMIC ağları v1 ağlarından (Quick, Draw! ve şablon benzetimleriyle eğitilmiş; test kaynaklarında
 *    `/shapenet-v1/`) başlar; NOTES ağı eski not ağının verisi kullanılmadan resim ağının v1 gizli katmanından başlar
 *    (yalnız şekil düzeltme). Yeni şablonlar el çizimi benzetimleriyle (karakterlerde bütün pozlar) öğrenilir, bilinen
 *    şablonlar unutulmasın diye tekrar edilir. Sonuç int8 olarak `src/main/resources/shapenet/mode_<mod>.bin`.
 * 3. Ayrılmış benzetimlerde (farklı tohum) önce/sonra doğrulukları yazılır. "Önce": v1 ağı + cihazdaki kurulum
 *    (bilinmeyen şablonlar 20 benzetim × 6 tur), yani yeniden eğitim olmasaydı cihazın kuracağı ağ.
 *
 * Ağ değişince [ShapeNet.modeNetVersion] artırılır (NOTES'ta eski öğrenilenler silinir, bkz. [ShapeNet.RESET_BELOW]).
 *
 * v38: uygulamayla gelen ağlar bunun üstüne [ModeNetsV38Training] ile 4+ elemanlı çizimlerle yeniden eğitildi. Bu komut
 * v37 ağlarını yeniden üretir (v38 ağlarının üstüne yazar); ardından ModeNetsV38Training yeniden çalıştırılmalıdır
 * (v37 başlangıç ağları test kaynaklarında `/shapenet-v37/`).
 */
class ModeNetRetrain {
    private fun enabled(): Boolean = System.getProperty("retrainNets") == "1" || System.getenv("MODENET_RETRAIN") == "1"

    @Test
    fun previewLibraryTemplates() {
        val dir = Preview.dir ?: return
        val entries = LibraryLineArt.all()
        val cols = 12; val cell = 120
        val rows = (entries.size + cols - 1) / cols
        val img = BufferedImage(cols * cell, rows * cell, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color.WHITE; g.fillRect(0, 0, img.width, img.height)
        entries.forEachIndexed { i, e ->
            val ox = (i % cols) * cell + 10f; val oy = (i / cols) * cell + 6f
            g.color = Color(0x1E1E1E); g.stroke = BasicStroke(1.4f)
            for (s in e.template.strokes) {
                val path = Path2D.Float()
                s.forEachIndexed { k, p -> if (k == 0) path.moveTo(ox + p.x * 96f, oy + p.y * 96f) else path.lineTo(ox + p.x * 96f, oy + p.y * 96f) }
                g.draw(path)
            }
            g.font = Font(Font.SANS_SERIF, Font.PLAIN, 10); g.color = Color(0x2563EB)
            g.drawString("${e.template.key.substringAfterLast('.')} (${e.template.strokeCount})", ox - 6f, oy + 110f)
        }
        g.dispose()
        javax.imageio.ImageIO.write(img, "png", File(dir, "v37-library-templates.png"))
        println("kütüphane şablonları: ${entries.size}, çizgi sayısı ort. ${"%.1f".format(entries.map { it.template.strokeCount }.average())}")
    }

    @Test
    fun exportLibraryTemplates() {
        Assume.assumeTrue(enabled())
        val entries = LibraryLineArt.all()
        val file = File("src/main/resources/shapes/library_templates.bin")
        file.parentFile.mkdirs()
        file.writeBytes(LibraryTemplates.encode(entries))
        println("kütüphane şablonları: ${entries.size} (${file.length() / 1024} KB)")
    }

    /** v1 ağları (test kaynakları): ART ve COMIC'in yeniden eğitimden önceki ağları. */
    private fun v1(name: String): ShapeNet =
        ShapeNet.decode(ModeNetRetrain::class.java.getResourceAsStream("/shapenet-v1/mode_$name.bin")!!.readBytes())!!

    /**
     * Tek komut: kütüphane şablonları + ART, COMIC, NOTES ağları. İsteğe bağlı MODENET_NOTES_V1=<dosya>: eski not ağı
     * verilirse NOTES için "önce" doğruluğu da ölçülür (eski ağ depoda tutulmaz).
     */
    @Test
    fun retrain() {
        Assume.assumeTrue(enabled())
        val entries = LibraryLineArt.all()
        File("src/main/resources/shapes").mkdirs()
        File("src/main/resources/shapes/library_templates.bin").writeBytes(LibraryTemplates.encode(entries))
        // Katalog: kaynak dosyası yeni yazıldığı için kütüphane şablonları buradan (ShapeTemplates eski dosyayı okumuş olabilir)
        // sınıf yolundaki (eski) kütüphane kaynağının şablonları atılır, yerine şimdi üretilenler girer
        val stale = LibraryTemplates.all.map { it.key }.toSet()
        val catalog = ShapeTemplates.all + ShapeTemplates.modeOnly.filterNot { it.key in stale || LibraryTemplates.isLibraryKey(it.key) } +
            entries.map { it.template }
        check(catalog.map { it.key }.toSet().size == catalog.size) { "yinelenen şablon anahtarı" }
        // Karakterler: bütün pozlar (sakin yüz ve poza göre dönen bir ifade) — ağ türü pozdan ve ifadeden bağımsız tanısın.
        // Sınama: her poz, eğitimde o pozla görülmemiş bir ifadeyle
        val variants = HashMap<String, List<List<List<P>>>>()
        val heldVariants = HashMap<String, List<List<List<P>>>>()
        val exprs = com.karapp.drawguidance.content.comic.CharExpression.entries
        for (k in com.karapp.drawguidance.content.comic.CharKind.entries) {
            val poses = com.karapp.drawguidance.content.comic.CharPose.entries
            variants[LibraryTemplates.CHARACTER_PREFIX + k.id] = poses.flatMap { p ->
                listOf(exprs[0], exprs[1 + p.ordinal % (exprs.size - 1)]).distinct().map { e -> LibraryLineArt.character(k, p, e) }
            }
            heldVariants[LibraryTemplates.CHARACTER_PREFIX + k.id] = poses.map { p ->
                LibraryLineArt.character(k, p, exprs[1 + (p.ordinal + exprs.size / 2) % (exprs.size - 1)])
            }
        }
        println("karakter biçimleri: ${variants.values.sumOf { it.size }} eğitim, ${heldVariants.values.sumOf { it.size }} sınama " +
            "(${com.karapp.drawguidance.content.comic.CharPose.entries.size} poz, ${exprs.size} ifade)")
        val notesOld = System.getenv("MODENET_NOTES_V1")?.let { ShapeNet.decode(File(it).readBytes()) }
        val jobs = listOf(
            ShapeMode.ART to Triple(v1("art"), v1("art"), "art"),
            ShapeMode.COMIC to Triple(v1("comic"), v1("comic"), "comic"),
            ShapeMode.NOTES to Triple(v1("art"), notesOld, "notes"),
        )
        val pool = java.util.concurrent.Executors.newFixedThreadPool(3)
        val reports = jobs.map { (mode, t) -> pool.submit<String> { trainMode(mode, catalog, t.first, t.second, t.third, variants, heldVariants) } }
        reports.forEach { println(it.get()) }
        pool.shutdown()
        println("kütüphane şablonları: ${entries.size} (nesne ${entries.count { it.template.key.startsWith(LibraryTemplates.OBJECT_PREFIX) }}, " +
            "karakter ${entries.count { it.template.key.startsWith(LibraryTemplates.CHARACTER_PREFIX) }}, efekt ${entries.count { it.template.key.startsWith(LibraryTemplates.EFFECT_PREFIX) }})")
    }

    /**
     * Bir modun ağı: [base] gizli katmanı ve bilinen şablonların satırları; yeni şablon başına 160 tam + 40 eksik
     * benzetim, bilinen şablon başına 30 benzetim; 16 tur (gizli katman yavaş). [before]: yeniden eğitim olmasaydı
     * cihazın kuracağı ağın kaynağı (null: ölçülmez).
     */
    private fun trainMode(
        mode: ShapeMode, catalog: List<ShapeTemplate>, base: ShapeNet, before: ShapeNet?, name: String,
        variants: Map<String, List<List<List<P>>>>,
        heldVariants: Map<String, List<List<List<P>>>> = emptyMap(),
    ): String {
        val keys = catalog.filter { ModeCatalog.allows(mode, it.key, it.category) }.map { it.key }
        val byKey = catalog.associateBy { it.key }
        val baseKeys = base.keys.toHashSet()
        val oldKeys = before?.keys?.toHashSet() ?: baseKeys
        val rnd = java.util.Random(3700L + mode.ordinal)
        val data = ArrayList<Pair<Int, FloatArray>>()
        for ((k, key) in keys.withIndex()) {
            val t = byKey.getValue(key)
            val known = key in baseKeys
            val forms = variants[key] ?: listOf(t.strokes)
            repeat(if (known) 30 else 160) { i ->
                val src = if (i % 3 == 0) t.strokes else forms[rnd.nextInt(forms.size)]
                ShapeNet.inputOf(ShapeAugment.handDrawn(src, rnd, amount = 0.6f + rnd.nextFloat() * 0.9f, strong = i % 3 == 2))?.let { data += k to it }
            }
            if (!known && t.strokes.size > 3) repeat(40) {
                val src = forms[rnd.nextInt(forms.size)].takeIf { it.size > 3 } ?: t.strokes
                val part = src.take(3 + rnd.nextInt(src.size - 2))
                ShapeNet.inputOf(ShapeAugment.handDrawn(part, rnd, amount = 1f))?.let { data += k to it }
            }
        }
        // Ayrılmış benzetimler (farklı tohum): şablon başına 8; karakterlerde ayrıca her poz (eğitimde o pozla görülmemiş ifade)
        val vr = java.util.Random(77)
        val valSet = keys.withIndex().flatMap { (k, key) ->
            List(8) { ShapeNet.inputOf(ShapeAugment.handDrawn(byKey.getValue(key).strokes, vr))?.let { k to it } }.filterNotNull()
        }
        val pr = java.util.Random(78)
        val poseSet = keys.withIndex().filter { heldVariants.containsKey(it.value) }.flatMap { (k, key) ->
            heldVariants.getValue(key).mapNotNull { v -> ShapeNet.inputOf(ShapeAugment.handDrawn(v, pr))?.let { k to it } }
        }
        fun acc(c: ShapeClassifier, set: List<Pair<Int, FloatArray>>, filter: (String) -> Boolean): String {
            val s = set.filter { filter(keys[it.first]) }
            if (s.isEmpty()) return "-"
            var t1 = 0; var t3 = 0
            for ((k, x) in s) {
                val p = c.probs(x)
                val ki = c.indexOf(keys[k])
                if (ki < 0) continue
                val o = p.indices.sortedByDescending { p[it] }
                if (o[0] == ki) t1++
                if (ki in o.take(3)) t3++
            }
            return "ilk-1 %.1f%% ilk-3 %.1f%% (%d)".format(100f * t1 / s.size, 100f * t3 / s.size, s.size)
        }
        val sb = StringBuilder()
        val newCount = keys.count { it !in oldKeys }
        sb.append("$mode: ${keys.size} sınıf (eski ağda ${oldKeys.size}, yeni ${newCount}: nesne ${keys.count { it.startsWith(LibraryTemplates.OBJECT_PREFIX) }}, " +
            "karakter ${keys.count { it.startsWith(LibraryTemplates.CHARACTER_PREFIX) }}, efekt ${keys.count { it.startsWith(LibraryTemplates.EFFECT_PREFIX) }}), eğitim ${data.size}\n")
        if (before != null) {
            val dev = ModeModel(mode, ModeNet.init(keys, before)).also { m ->
                m.bootstrap(keys.filter { it !in before.keys.toHashSet() }.map { byKey.getValue(it) }, keys.filter { it in before.keys.toHashSet() }.map { byKey.getValue(it) })
            }.net
            sb.append("  önce (v1 ağı + cihazda kurulum): eski sınıflar ${acc(dev, valSet) { it in oldKeys }}, yeni sınıflar ${acc(dev, valSet) { it !in oldKeys }}, " +
                "karakter pozları ${acc(dev, poseSet) { true }}\n")
        }
        val net = ModeNet.init(keys, base)
        val epochs = 16
        repeat(epochs) { e ->
            data.shuffle(rnd)
            val lr = 0.08f * (1f - e / (epochs + 1f))
            for ((k, x) in data) net.train(x, k, lr, 0.0015f)
        }
        // Sıcaklık ayarı: yapay benzetimlerde aşırı emin ağ (olasılık ≈ 1) aramadaki aday karşılaştırmasını (p / maxP)
        // ve cihazdaki ret/kabul öğrenmesini etkisiz kılar. Çıkış katmanı, ayrılmış benzetimlerde ortalama en yüksek
        // olasılık v1 ağınınkine (v1'in bildiği sınıflarda) eşit olacak biçimde ölçeklenir; sıralama değişmez.
        val known = valSet.filter { base.indexOf(keys[it.first]) >= 0 }
        fun meanTop(c: ShapeClassifier) = known.sumOf { c.probs(it.second).max().toDouble() } / known.size.coerceAtLeast(1)
        val target = meanTop(base)
        val raw = meanTop(net)
        val w2 = net.w2.copyOf(); val b2 = net.b2.copyOf()
        fun scaleTo(s: Float) { for (i in w2.indices) net.w2[i] = w2[i] * s; for (i in b2.indices) net.b2[i] = b2[i] * s }
        var temp = 1f
        if (raw > target) {
            var lo = 0.01f; var hi = 1f
            repeat(24) { val mid = (lo + hi) / 2; scaleTo(mid); if (meanTop(net) > target) hi = mid else lo = mid }
            temp = (lo + hi) / 2
            scaleTo(temp)
        }
        val calibrated = meanTop(net)
        val q = ShapeNet.quantize(net.keys, net.inDim, net.hidden, net.w1, net.b1, net.w2, net.b2)
        val file = File("src/main/resources/shapenet/mode_$name.bin")
        file.writeBytes(q.encode())
        sb.append("  sıcaklık ölçeği %.3f: ortalama en yüksek olasılık %.3f → %.3f (v1 ağı %.3f)\n".format(temp, raw, calibrated, target))
        sb.append("  sonra (int8, ${file.length() / 1024} KB): eski sınıflar ${acc(q, valSet) { it in oldKeys }}, yeni sınıflar ${acc(q, valSet) { it !in oldKeys }}, " +
            "karakter pozları ${acc(q, poseSet) { true }}, tümü ${acc(q, valSet) { true }}")
        return sb.toString()
    }
}
