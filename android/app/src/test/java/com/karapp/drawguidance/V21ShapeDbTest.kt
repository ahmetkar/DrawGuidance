package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.content.shapes.MatchMode
import com.karapp.drawguidance.content.shapes.ShapeIndex
import com.karapp.drawguidance.content.shapes.ShapeSignatures
import com.karapp.drawguidance.content.shapes.ShapeTemplates
import com.karapp.drawguidance.content.shapes.TemplateFit
import com.karapp.drawguidance.content.vector.P
import com.karapp.drawguidance.data.local.AppDatabase
import com.karapp.drawguidance.data.model.BrushParams
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.Depth3D
import com.karapp.drawguidance.data.model.Plane3D
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.data.repository.ShapeTemplateRepository
import com.karapp.drawguidance.ui.drawing.Iso3D
import com.karapp.drawguidance.ui.screens.canvasedit.state.TemplateSuggester
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Elle çizilmiş gibi: dalgalı, titrek, yönü ve sırası karışık çizgiler (tuval koordinatı). */
internal fun handDrawnTemplate(key: String, cx: Float, cy: Float, size: Float, seed: Long): List<List<P>> {
    val rnd = java.util.Random(seed)
    val t = ShapeTemplates.byKey(key)!!
    val norm = ShapeSignatures.normalize(t.strokes)
    return norm.map { s ->
        val pts = TemplateFit.densify(s.map { P(cx + (it.x - 0.5f) * size, cy + (it.y - 0.5f) * size) }, 4f)
        val ph = rnd.nextFloat() * 6.28f; val f = 1f + rnd.nextFloat() * 2f
        var len = 0f
        val out = pts.mapIndexed { i, p ->
            if (i > 0) len += hypot(p.x - pts[i - 1].x, p.y - pts[i - 1].y)
            val w = size * 0.01f
            P(p.x + w * sin(f * len / size * 6.28f + ph) + (rnd.nextFloat() - 0.5f), p.y + w * cos(f * len / size * 6.28f + ph) + (rnd.nextFloat() - 0.5f))
        }
        if (rnd.nextBoolean()) out.reversed() else out
    }.shuffled(rnd)
}

/** 3B çizimde olduğu gibi: her iz kendi ilk noktası etrafında düzleme yatırılır. */
internal fun projectLikeDrawTool(strokes: List<List<P>>, plane: Plane3D): List<List<P>> = strokes.map { s ->
    val a = Offset(s[0].x, s[0].y)
    s.map { val o = Iso3D.project(plane, a, it.x, it.y); P(o.x, o.y) }
}

internal fun inkStrokes(strokes: List<List<P>>, params: BrushParams? = null, brush: BrushType = BrushType.MARKER): List<Stroke> =
    strokes.mapIndexed { i, s ->
        Stroke(
            id = "s$i", points = s.map { StrokePoint(it.x, it.y) }, colorHex = "#1E1E1E", brushType = brush, sizePx = 6f,
            opacity = 1f, order = i, params = params,
        )
    }

/**
 * v21 şekil veritabanı: SQLite tablosunun doldurulması, sayaçların ve kullanıcı örneklerinin korunması, öğrenme;
 * öneride tek çizgi ve çok elemanlı arama, 3B düzlemde geri açma ve düzleme yatırılmış değiştirme.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V21ShapeDbTest {

    private val index by lazy { ShapeIndex.fromCatalog() }

    /** v38 öneri akışı: son çizilenle bir arada duran grup (yakın 4+ eleman) ve şablon araması. */
    private fun suggest(all: List<Stroke>, last: Stroke) =
        TemplateSuggester.search(index, TemplateSuggester.group(all, last) { true })

    /** v38 şekil önerisi 4+ elemanda: az çizgili şablon parçalara bölünür (çizen kişi gibi). */
    private fun four(strokes: List<List<P>>, seed: Long) =
        com.karapp.drawguidance.content.shapes.ShapeAugment.atLeast(strokes, com.karapp.drawguidance.content.shapes.SuggestRules.SHAPE_MIN, java.util.Random(seed))

    @org.junit.After
    fun tearDown() = org.koin.core.context.stopKoin()

    @Test
    fun databaseSeedsLearnsAndKeepsCountersOnCatalogUpdate() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val dao = db.shapeTemplateDao()
        val repo = ShapeTemplateRepository(dao, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        val t0 = System.currentTimeMillis()
        val idx = repo.load()
        println("şekil veritabanı dolduruldu ve yüklendi: ${System.currentTimeMillis() - t0} ms, ${dao.count()} kayıt")
        assertEquals(ShapeTemplates.catalog.size, dao.count())
        assertEquals(ShapeTemplates.catalog.size, idx.templateCount)
        val cats = dao.categoryCounts()
        cats.forEach { println("  ${it.category}: ${it.count}") }
        assertTrue(cats.size >= 15)
        // Ön eleme sütunları (bileşik dizin): tek çizgili, kareye yakın kayıtlar
        assertTrue(dao.keysByShape(1, 1, 3, 4).isNotEmpty())

        // Veritabanından yüklenen dizin katalogla aynı sonucu verir
        val q = handDrawnTemplate("mobilya.sandalye.onden", 300f, 300f, 240f, 4)
        val fromDb = idx.search(q, MatchMode.GROUP, 1).first()
        assertEquals("mobilya.sandalye.onden", fromDb.key)

        // Kabul: sayaç ve kullanıcı örneği
        repo.accepted("mobilya.sandalye.onden", q)
        withTimeout(10_000) { while (dao.userCount("mobilya.sandalye.onden") == 0) delay(20) }
        withTimeout(10_000) { while (repo.indexFlow.value?.userExemplars("mobilya.sandalye.onden") != 1) delay(20) }
        assertEquals(1, repo.indexFlow.value!!.usageOf("mobilya.sandalye.onden"))
        repo.rejected(listOf("meyve.elma"))
        withTimeout(10_000) { while (dao.catalogCounts().first { it.key == "meyve.elma" }.rejects == 0) delay(20) }

        // Katalog sürümü değişince katalog yeniden yazılır; sayaçlar ve kullanıcı örneği korunur
        db.openHelper.writableDatabase.execSQL("UPDATE shape_templates SET catalogVersion = 0 WHERE source = 'catalog'")
        val repo2 = ShapeTemplateRepository(dao, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        val idx2 = repo2.load()
        assertEquals(ShapeTemplates.catalog.size + 1, dao.count())
        assertEquals(1, idx2.usageOf("mobilya.sandalye.onden"))
        assertEquals(1, idx2.rejectsOf("meyve.elma"))
        assertEquals(1, idx2.userExemplars("mobilya.sandalye.onden"))
        db.close()
    }

    @Test
    fun groupAndSingleSuggestions() {
        // Çok elemanlı: ev (gövde, çatı, kapı, pencere)
        val house = inkStrokes(handDrawnTemplate("yapi.ev.besik", 400f, 400f, 300f, 1))
        val fixes = suggest(house, house.last())
        println("ev: " + fixes.joinToString { "${it.key} ${"%.2f".format(it.confidence)} ${it.mode}" })
        val ev = fixes.first()
        assertEquals("yapi.ev.besik", ev.key)
        assertEquals(MatchMode.GROUP, ev.mode)
        assertEquals(house.map { it.id }.toSet(), ev.strokeIds)
        assertEquals(4, ev.strokes.size)
        // Uzaktaki başka bir çizim gruba katılmaz
        val far = inkStrokes(handDrawnTemplate("meyve.elma", 1400f, 1400f, 200f, 2)).mapIndexed { i, s -> s.copy(id = "far$i") }
        val withFar = far + house
        val fixes2 = suggest(withFar, withFar.last())
        assertEquals("yapi.ev.besik", fixes2.first().key)
        assertTrue(fixes2.first().strokeIds.none { it.startsWith("far") })

        // v38: tek çizgiye ve 2–3 elemana şekil önerisi yapılmaz (çizgi önerisinindir)
        val eye = inkStrokes(handDrawnTemplate("yuz.goz.cekik", 300f, 300f, 160f, 3))
        assertTrue(eye.size < 4)
        assertTrue(suggest(eye, eye.last()).isEmpty())
        val three = house.take(3)
        assertTrue(suggest(three, three.last()).isEmpty())
        assertTrue(TemplateSuggester.search(index, three).isEmpty())
    }

    @Test
    fun suggestionsWorkOnThe3dPlanes() {
        for (plane in listOf(Plane3D.FRONT, Plane3D.SIDE, Plane3D.TOP)) {
            val params = BrushParams(depth3d = Depth3D(plane, 0.4f))
            val flat = four(handDrawnTemplate("mobilya.masa", 500f, 500f, 260f, 9), 9)
            val strokes = inkStrokes(projectLikeDrawTool(flat, plane), params)
            val fixes = suggest(strokes, strokes.last())
            println("$plane masa: " + fixes.joinToString { "${it.key} ${"%.2f".format(it.confidence)}" })
            val fix = fixes.first()
            assertEquals("mobilya.masa", fix.key)
            // Değiştirme düzleme yatırılmış ve çizimin yerinde: kutu merkezleri yakın, eğiklik aynı düzlemde
            val px = strokes.flatMap { s -> s.points.map { it.x } }; val py = strokes.flatMap { s -> s.points.map { it.y } }
            val rx = fix.strokes.flatten().map { it.x }; val ry = fix.strokes.flatten().map { it.y }
            assertEquals((px.min() + px.max()) / 2f, (rx.min() + rx.max()) / 2f, 3f)
            assertEquals((py.min() + py.max()) / 2f, (ry.min() + ry.max()) / 2f, 3f)
            // Masanın tablası düzlemde: ön yüzde sağa-aşağı (30°), yan yüzde sağa-yukarı eğik
            val top = fix.strokes.first()
            val dx = top[10].x - top[0].x; val dy = top[10].y - top[0].y
            if (plane == Plane3D.FRONT || plane == Plane3D.SIDE) {
                val ang = Math.toDegrees(kotlin.math.atan2(abs(dy).toDouble(), abs(dx).toDouble()))
                assertTrue("$plane tabla eğimi $ang", ang in 15.0..45.0)
            }
            // Karşılaştırma için düz çizim de aynı şablonu verir
            assertEquals(fix.key, inkStrokes(flat).let { suggest(it, it.last()) }.first().key)
        }
    }

    @Test
    fun solidTemplatesOfferShadedSolidIn3dMode() {
        val params = BrushParams(depth3d = Depth3D(Plane3D.FREE, 0.3f))
        val cube = inkStrokes(four(handDrawnTemplate("cisim.kup", 400f, 400f, 240f, 5), 5), params)
        val fixes = suggest(cube, cube.last())
        println("küp: " + fixes.joinToString { "${it.key} ${it.solid} ${"%.3f".format(it.confidence)}" })
        val first = fixes.first()
        assertEquals("cisim.kup", first.key)
        // 3B çizimde gölgeli cisim seçeneği önce
        assertEquals("SOLID_CUBE", first.solid)
        assertNotNull(fixes.firstOrNull { it.key == "cisim.kup" && it.solid == null })
        // Silindir
        val cyl = inkStrokes(four(handDrawnTemplate("cisim.silindir", 400f, 400f, 240f, 6), 6))
        val c = suggest(cyl, cyl.last())
        assertTrue(c.any { it.key == "cisim.silindir" && it.solid == "SOLID_CYLINDER" })
    }

    @Test
    fun speedOnTypicalQueries() {
        index.search(handDrawnTemplate("yapi.ev.besik", 0f, 0f, 300f, 1), MatchMode.GROUP)
        val rnd = java.util.Random(4)
        val keys = ShapeTemplates.all.map { it.key }
        var total = 0L
        repeat(60) {
            val k = keys[rnd.nextInt(keys.size)]
            val strokes = inkStrokes(four(handDrawnTemplate(k, 400f, 400f, 250f, it.toLong()), it.toLong()))
            val t0 = System.nanoTime()
            suggest(strokes, strokes.last())
            total += System.nanoTime() - t0
        }
        val avgMs = total / 60 / 1e6
        println("öneri (tek + grup arama, uyarlama) ortalama: ${"%.2f".format(avgMs)} ms")
        assertTrue(avgMs < 40.0)
    }
}
