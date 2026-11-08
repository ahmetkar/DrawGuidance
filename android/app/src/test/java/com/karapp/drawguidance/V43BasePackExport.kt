package com.karapp.drawguidance

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.comicassets.ComicAssets
import com.karapp.drawguidance.data.model.BalloonSpec
import com.karapp.drawguidance.data.model.BalloonStyle
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.DiagramKind
import com.karapp.drawguidance.data.model.DiagramSpec
import com.karapp.drawguidance.data.model.ShapeStyle
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.data.model.TextAlignKind
import com.karapp.drawguidance.data.model.TextBox
import com.karapp.drawguidance.data.model.TextFont
import com.karapp.drawguidance.data.model.TextSpec
import com.karapp.drawguidance.ui.drawing.BalloonRenderer
import com.karapp.drawguidance.ui.drawing.DiagramCatalog
import com.karapp.drawguidance.ui.drawing.DiagramRenderer
import com.karapp.drawguidance.ui.drawing.ShapeFactory
import com.karapp.drawguidance.ui.drawing.ShapeFit
import com.karapp.drawguidance.ui.drawing.ShapeRegistry
import com.karapp.drawguidance.ui.drawing.ShapeRenderer
import com.karapp.drawguidance.ui.drawing.TextRenderer
import com.karapp.drawguidance.ui.screens.canvasedit.components.balloonLabelRes
import com.karapp.drawguidance.ui.screens.canvasedit.components.diagramLabelRes
import com.karapp.drawguidance.ui.screens.canvasedit.components.shapeCategoryLabelRes
import com.karapp.drawguidance.ui.screens.canvasedit.components.shapeLabelRes
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * v43 (1.1) taban paketlerin dışa aktarımı: uygulamadaki bütün pencere resimleri sunucunun paket mimarisine göre
 * dosyalanır (`-PbasePackOut=<klasör>` verilince çalışır; sunucu `taban-paket --klasor <klasör> --yukle` ile Cloud
 * Storage'a yükler ve kaydeder):
 *
 * - `base-comic`: nesneler, efektler, mekân arka planları, karakterler (uygulamadaki WebP'ler), diyalog kutuları ve
 *   şekiller (vektörden PNG),
 * - `base-notes`: şekiller ve diyagramlar (PNG), `base-art`: şekiller (PNG).
 *
 * Çıktı: `manifest.json` (`{packages: [{id, name, mode, items: [{id, window, title, category, file}]}]}`) ve dosyalar.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V43BasePackExport {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private class Item(val id: String, val window: String, val title: String, val category: String?, val file: String)

    @Test
    fun export() {
        val outPath = System.getProperty("basePackOut")
        assumeTrue("basePackOut verilmedi", !outPath.isNullOrBlank())
        val out = File(outPath!!).apply { deleteRecursively(); mkdirs() }
        val packages = listOf(
            pkg("base-comic", "Çizgi roman temel paketi", "COMIC", comic(out)),
            pkg("base-notes", "Not alma temel paketi", "NOTES", shapes(out, "notes", CanvasKind.NOTES) + diagrams(out)),
            pkg("base-art", "Resim temel paketi", "ART", shapes(out, "art", CanvasKind.ART)),
        )
        File(out, "manifest.json").writeText(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), JsonObject(mapOf("packages" to JsonArray(packages)))))
        println("taban paketler: " + packages.joinToString { (it["id"] as JsonPrimitive).content + "=" + (it["items"] as JsonArray).size })
    }

    private fun pkg(id: String, name: String, mode: String, items: List<Item>): JsonObject {
        val seen = HashSet<String>()
        items.forEach { require(seen.add(it.window + "/" + it.id)) { "yinelenen öğe ${it.window}/${it.id}" } }
        return JsonObject(
            mapOf(
                "id" to JsonPrimitive(id), "name" to JsonPrimitive(name), "mode" to JsonPrimitive(mode),
                "items" to JsonArray(items.map { i ->
                    JsonObject(buildMap {
                        put("id", JsonPrimitive(i.id)); put("window", JsonPrimitive(i.window)); put("title", JsonPrimitive(i.title))
                        i.category?.let { put("category", JsonPrimitive(it)) }
                        put("file", JsonPrimitive(i.file))
                    })
                }),
            ),
        )
    }

    // ------------------------------------------------------------------ çizgi roman

    private fun comic(out: File): List<Item> {
        val c = ComicAssets.bundled
        val items = ArrayList<Item>()
        fun copy(asset: String, rel: String) {
            val f = File(out, rel)
            f.parentFile?.mkdirs()
            ctx.assets.open(asset).use { input -> f.outputStream().use { input.copyTo(it) } }
        }
        val objCats = c.objectCategories.associate { it.id to ComicAssets.name(it.names, "tr") }
        val fxCats = c.effectCategories.associate { it.id to ComicAssets.name(it.names, "tr") }
        val bgCats = c.bgCategories.associate { it.id to ComicAssets.name(it.names, "tr") }
        val themes = c.themes.associate { it.id to ComicAssets.name(it.names, "tr") }
        // Nesne ve efektlerin adı yok: kategori adı ve sırası
        for ((list, window, cats) in listOf(Triple(c.objects, "objects", objCats), Triple(c.effects, "effects", fxCats))) {
            val counter = HashMap<String, Int>()
            for (p in list) {
                val n = counter.merge(p.category, 1, Int::plus)!!
                val rel = "comic/$window/${p.id}.webp"
                copy(p.file, rel)
                items += Item(safe(p.id), window, "${cats[p.category] ?: p.category} $n", cats[p.category], rel)
            }
        }
        for (b in c.backgrounds) {
            val rel = "comic/backgrounds/${b.id}.webp"
            copy(b.file, rel)
            val style = if (b.style == "manga") " (manga)" else ""
            items += Item(safe(b.id), "backgrounds", ComicAssets.name(b.names, "tr") + style, bgCats[b.category], rel)
        }
        val groups = mapOf("ifade" to "İfade", "poz" to "Poz", "aksiyon" to "Aksiyon", "gunluk" to "Günlük")
        for (ch in c.characters) {
            val counter = HashMap<String, Int>()
            for (v in ch.variants) {
                val n = counter.merge(v.group, 1, Int::plus)!!
                val rel = "comic/characters/${ch.id}/${v.id}.webp"
                copy(v.file, rel)
                items += Item(safe(ch.id + "-" + v.id), "characters", "${ComicAssets.name(ch.names, "tr")} – ${groups[v.group] ?: v.group} $n", themes[ch.theme], rel)
            }
        }
        // Diyalog kutuları: her balon biçimi boş (yazısız) olarak
        for (style in BalloonStyle.entries) {
            val rel = "comic/balloons/${style.name.lowercase()}.png"
            val bmp = balloon(style) ?: continue
            save(bmp, File(out, rel))
            items += Item("balloon-" + style.name.lowercase(), "balloons", ctx.getString(balloonLabelRes(style)), "Diyalog kutuları", rel)
        }
        items += shapes(out, "comic", CanvasKind.COMIC)
        return items
    }

    private fun balloon(style: BalloonStyle): Bitmap? = runCatching {
        val size = 34f
        // Serbest yazının kutusu yok (pakette anlamsız)
        if (style == BalloonStyle.FREE) return@runCatching null
        val tails = when (style) {
            BalloonStyle.NARRATION -> emptyList()
            else -> BalloonRenderer.defaultTails(1)
        }
        val spec = TextSpec(
            content = "                 \n                 ", font = TextFont.COMIC_RELIEF, align = TextAlignKind.CENTER,
            box = TextBox(width = size * 9f, balloon = BalloonSpec(style = style, tails = tails, fillHex = if (style == BalloonStyle.NARRATION) "#FFF59D" else "#FFFFFF", strokeHex = "#111111")),
        )
        val (w, h) = TextRenderer.measure(spec, size)
        val pad = size * 3f
        val bw = (w + pad * 2).toInt(); val bh = (h + pad * 2).toInt()
        val points = TextRenderer.quadAt(spec, size, pad, pad).map { StrokePoint(it.x, it.y) }
        val stroke = Stroke(id = "b", points = points, colorHex = "#111111", brushType = BrushType.TEXT, sizePx = size, opacity = 1f, order = 0, text = spec)
        Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888).also { bmp ->
            // Yazı boşluk: yalnız kutu ve kuyruk görünür (iki satırlık boyutta)
            TextRenderer.draw(Canvas(bmp), stroke, 0x00000000, 0f, 0f)
        }.let { trim(it) }
    }.getOrNull()

    // ------------------------------------------------------------------ şekiller ve diyagramlar

    private fun shapes(out: File, dir: String, kind: CanvasKind): List<Item> {
        val items = ArrayList<Item>()
        val seen = HashSet<String>()
        for (c in ShapeRegistry.categories(kind)) {
            val catTitle = c.builtin?.let { ctx.getString(shapeCategoryLabelRes(it)) } ?: c.title
            for (id in c.items) {
                val key = safe(id)
                if (!seen.add(key)) continue
                val bmp = shape(id) ?: continue
                val rel = "$dir/shapes/$key.png"
                save(bmp, File(out, rel))
                val title = ShapeRegistry.builtin(id)?.let { ctx.getString(shapeLabelRes(it)) } ?: ShapeRegistry.title(id) ?: id
                items += Item(key, "shapes", title, catTitle, rel)
            }
        }
        return items
    }

    private fun shape(id: String): Bitmap? = runCatching {
        val w = 384; val h = 384; val pad = 24f
        val aspect = if (ShapeRegistry.fit(id) == ShapeFit.SQUARE) 1f else ShapeRegistry.aspect(id)
        val availW = w - pad * 2; val availH = h - pad * 2
        val (boxW, boxH) = if (availW / availH > aspect) availH * aspect to availH else availW to availW / aspect
        val left = (w - boxW) / 2f; val top = (h - boxH) / 2f
        val line = ShapeRegistry.fit(id) == ShapeFit.LINE
        val start = if (line) Offset(left, h / 2f) else Offset(left, top)
        val end = if (line) Offset(left + boxW, h / 2f) else Offset(left + boxW, top + boxH)
        val stroke = ShapeFactory.build(id, start, end, ShapeStyle(strokeColorHex = "#1E1E1E", strokeWidth = 5f), id = "x", order = 0) ?: return@runCatching null
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { ShapeRenderer.draw(Canvas(it), stroke, cache = false) }
            .takeIf { hasInk(it) }
    }.getOrNull()

    private fun diagrams(out: File): List<Item> {
        val items = ArrayList<Item>()
        for (kind in DiagramKind.entries) {
            val bmp = runCatching {
                val aspect = DiagramCatalog.aspect(kind) ?: 1.25f
                val w = 900f; val h = w / aspect
                val nodes = DiagramRenderer.layout(kind, DiagramRenderer.defaultNodes(kind))
                val spec = DiagramSpec(kind = kind, nodes = nodes, edges = DiagramRenderer.defaultEdges(kind, nodes), style = ShapeStyle(strokeColorHex = "#1E293B", strokeWidth = 3f),
                    manual = DiagramCatalog.template(kind)?.manual ?: false)
                val pad = 30f
                val stroke = Stroke(
                    id = "d", points = listOf(StrokePoint(pad, pad), StrokePoint(pad + w, pad), StrokePoint(pad + w, pad + h), StrokePoint(pad, pad + h)),
                    colorHex = spec.style.strokeColorHex, brushType = BrushType.DIAGRAM, sizePx = spec.style.strokeWidth, opacity = 1f, order = 0, diagram = spec,
                )
                Bitmap.createBitmap((w + pad * 2).toInt(), (h + pad * 2).toInt(), Bitmap.Config.ARGB_8888).also {
                    it.eraseColor(android.graphics.Color.WHITE)
                    DiagramRenderer.draw(Canvas(it), stroke, 0f, 0f)
                }
            }.getOrNull() ?: continue
            val rel = "notes/diagrams/${kind.name.lowercase()}.png"
            save(bmp, File(out, rel))
            items += Item("diagram-" + kind.name.lowercase(), "diagrams", ctx.getString(diagramLabelRes(kind)), "Diyagramlar", rel)
        }
        return items
    }

    // ------------------------------------------------------------------ yardımcılar

    private fun hasInk(b: Bitmap): Boolean {
        val px = IntArray(b.width * b.height)
        b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
        return px.count { (it ushr 24) > 40 } > 30
    }

    /** Saydam kenarları kırpar (8 px pay bırakır). */
    private fun trim(b: Bitmap): Bitmap {
        val px = IntArray(b.width * b.height)
        b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
        var l = b.width; var t = b.height; var r = -1; var bo = -1
        for (y in 0 until b.height) for (x in 0 until b.width) if ((px[y * b.width + x] ushr 24) > 8) {
            if (x < l) l = x; if (x > r) r = x; if (y < t) t = y; if (y > bo) bo = y
        }
        if (r < 0) return b
        l = (l - 8).coerceAtLeast(0); t = (t - 8).coerceAtLeast(0); r = (r + 8).coerceAtMost(b.width - 1); bo = (bo + 8).coerceAtMost(b.height - 1)
        return Bitmap.createBitmap(b, l, t, r - l + 1, bo - t + 1)
    }

    private fun save(b: Bitmap, f: File) {
        f.parentFile?.mkdirs()
        f.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun safe(id: String): String = com.karapp.drawguidance.data.inbox.BasePacks.itemId(id)
}
