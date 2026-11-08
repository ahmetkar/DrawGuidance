package com.karapp.drawguidance

import android.os.Looper
import androidx.activity.compose.setContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.content.model3d.Brush3Kind
import com.karapp.drawguidance.content.model3d.DrawPlane
import com.karapp.drawguidance.content.model3d.Mesh
import com.karapp.drawguidance.content.model3d.ModelEdit
import com.karapp.drawguidance.content.model3d.Part
import com.karapp.drawguidance.content.model3d.PrimitiveKind
import com.karapp.drawguidance.content.model3d.Solid3
import com.karapp.drawguidance.content.model3d.Transform3
import com.karapp.drawguidance.content.model3d.V3
import com.karapp.drawguidance.data.images.CanvasAssetStore
import com.karapp.drawguidance.data.images.ImageExporter
import com.karapp.drawguidance.data.images.ImagesRepository
import com.karapp.drawguidance.data.images.SavedImageDao
import com.karapp.drawguidance.data.library.Model3DRepository
import com.karapp.drawguidance.data.library.Model3DResults
import com.karapp.drawguidance.data.library.TextureRepository
import com.karapp.drawguidance.data.local.AppDatabase
import com.karapp.drawguidance.platform.DeviceCaps
import com.karapp.drawguidance.ui.screens.model3d.Budget3D
import com.karapp.drawguidance.ui.screens.model3d.FaceMode
import com.karapp.drawguidance.ui.screens.model3d.ImageExport
import com.karapp.drawguidance.ui.screens.model3d.Model3DEditorScreen
import com.karapp.drawguidance.ui.screens.model3d.Model3DEditorViewModel
import com.karapp.drawguidance.ui.screens.model3d.OpKind
import com.karapp.drawguidance.ui.screens.model3d.SliceAxis
import com.karapp.drawguidance.ui.screens.model3d.Tool3
import com.karapp.drawguidance.ui.theme.DrawGuidanceTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** v25 testlerinde ortak geometri yardımcıları (ekran noktaları düzenleyicinin görünümünden). */
internal object V25Geo {
    fun volume(m: Mesh): Double {
        var v = 0.0
        val p = m.pos
        for (f in 0 until m.faceCount) {
            val face = m.faces[f]; val t = m.triangles[f]
            var k = 0
            while (k + 2 < t.size) {
                val a = face[t[k]] * 3; val b = face[t[k + 1]] * 3; val c = face[t[k + 2]] * 3
                v += (p[a] * (p[b + 1] * p[c + 2] - p[b + 2] * p[c + 1]) - p[a + 1] * (p[b] * p[c + 2] - p[b + 2] * p[c]) + p[a + 2] * (p[b] * p[c + 1] - p[b + 1] * p[c])).toDouble()
                k += 3
            }
        }
        return v / 6.0
    }

    fun worldVolume(p: Part): Double {
        val t = Transform3.of(p)
        return volume(p.mesh.mapPositions { t.apply(it) })
    }

    fun world(p: Part, v: Int): V3 = Transform3.of(p).apply(p.mesh.vertex(v))

    fun screen(vm: Model3DEditorViewModel, w: V3): Offset = vm.toScreen(w)!!.let { Offset(it.first, it.second) }

    /** Bakana en yakın köşe. */
    fun nearestCorner(vm: Model3DEditorViewModel, p: Part): Int {
        val tv = vm.currentView()!!.toViewer
        return (0 until p.mesh.vertexCount).maxBy { world(p, it) dot tv }
    }

    fun neighbors(m: Mesh, v: Int): List<Int> {
        val out = ArrayList<Int>()
        val e = m.edges
        var i = 0
        while (i < e.size) {
            if (e[i] == v) out += e[i + 1] else if (e[i + 1] == v) out += e[i]
            i += 4
        }
        return out
    }

    fun edgeMid(vm: Model3DEditorViewModel, p: Part, a: Int, b: Int): Offset = screen(vm, (world(p, a) + world(p, b)) * 0.5f)

    /** Bakana en çok dönük yüz ([accept] dünya normaline göre süzer). */
    fun facingFace(vm: Model3DEditorViewModel, p: Part, accept: (V3) -> Boolean): Int {
        val tv = vm.currentView()!!.toViewer
        val t = Transform3.of(p)
        val out = FloatArray(3)
        return (0 until p.mesh.faceCount).filter { f ->
            val n = p.mesh.faceNormal(f); t.normal(n.x, n.y, n.z, out, 0); accept(V3(out[0], out[1], out[2]))
        }.maxBy { f -> val n = p.mesh.faceNormal(f); t.normal(n.x, n.y, n.z, out, 0); V3(out[0], out[1], out[2]) dot tv }
    }

    fun worldNormal(p: Part, f: Int): V3 {
        val out = FloatArray(3)
        val n = p.mesh.faceNormal(f)
        Transform3.of(p).normal(n.x, n.y, n.z, out, 0)
        return V3(out[0], out[1], out[2])
    }

    fun faceCenter(vm: Model3DEditorViewModel, p: Part, f: Int): Offset = screen(vm, Transform3.of(p).apply(p.mesh.faceCenter(f)))
}

/**
 * v25 (1): 3B düzenleyicinin ViewModel'i: Nokta aracında kenara dokunarak nokta ekleme, noktaya dokunarak seçme, silme,
 * birleştirme (kenar) ve halkadan kesme (tek geri alma adımı); Yüz aracında yüzey seçimi, ayrı parça ve delik; katı cisim
 * işlemleri (birleştir, çıkar, kesişim), parçalama, gruplama ve ayırma; 3B fırça cisimleri; resim olarak dışa aktarma.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V25Model3DViewModelTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()

    @After
    fun close() {
        db.close()
        org.koin.core.context.stopKoin()
    }

    private fun idle() = repeat(5) { shadowOf(Looper.getMainLooper()).idle() }

    private fun vm(): Model3DEditorViewModel {
        val tex = TextureRepository(db.texture3DDao())
        val repo = Model3DRepository(context, db.model3DDao(), tex)
        val images = ImagesRepository(db.savedImageDao(), ImageExporter(context, useGallery = false), CanvasAssetStore(db.canvasAssetDao()))
        val vm = Model3DEditorViewModel(repo, tex, Model3DResults(), null, null, null, images = images)
        idle()
        vm.setViewport(900, 900)
        return vm
    }

    /** Arka plandaki ağır işlemin (katı cisim, fırça cismi) bitmesini bekler. */
    private fun waitHeavy(vm: Model3DEditorViewModel) {
        val end = System.currentTimeMillis() + 90_000
        while (System.currentTimeMillis() < end) {
            shadowOf(Looper.getMainLooper()).idle()
            if (vm.busy.value == null) return
            Thread.sleep(15)
        }
        fail("işlem bitmedi")
    }

    private fun part(vm: Model3DEditorViewModel, id: String) = vm.model.value!!.part(id)!!

    @Test
    fun pointToolAddDeleteConnectAndCut() {
        val vm = vm()
        val cube = vm.model.value!!.parts[0]
        vm.selectPart(cube.id)
        vm.setTool(Tool3.POINT)
        val corner = V25Geo.nearestCorner(vm, cube)
        val nbrs = V25Geo.neighbors(cube.mesh, corner)
        assertEquals(3, nbrs.size)

        // Kenara dokunmak (kenar seçmeden) oraya nokta ekler ve seçer
        val mid = V25Geo.edgeMid(vm, cube, corner, nbrs[0])
        vm.tapAt(mid.x, mid.y, 30f)
        assertEquals(9, part(vm, cube.id).mesh.vertexCount)
        assertEquals(listOf(8), vm.selectedPoints())
        // Noktaya dokunmak seçimi bırakır / yeniden seçer
        val p8 = V25Geo.screen(vm, V25Geo.world(part(vm, cube.id), 8))
        vm.tapAt(p8.x, p8.y, 30f)
        assertTrue(vm.selectedPoints().isEmpty())
        vm.tapAt(p8.x, p8.y, 30f)
        assertEquals(listOf(8), vm.selectedPoints())
        // Sil: kenar üstündeki nokta iz bırakmaz
        vm.deleteSelectedPoints()
        val back = part(vm, cube.id).mesh
        assertEquals(8, back.vertexCount)
        assertEquals(6, back.faceCount)
        assertTrue(back.closed)

        // Birleştir: aynı yüzün karşılıklı iki köşesi → yüz ikiye bölünür
        val front = V25Geo.facingFace(vm, part(vm, cube.id)) { true }
        val fv = part(vm, cube.id).mesh.faces[front]
        listOf(fv[0], fv[2]).forEach { v -> val s = V25Geo.screen(vm, V25Geo.world(part(vm, cube.id), v)); vm.tapAt(s.x, s.y, 30f) }
        assertEquals(listOf(fv[0], fv[2]), vm.selectedPoints())
        assertTrue(vm.connectSelectedPoints())
        assertEquals(7, part(vm, cube.id).mesh.faceCount)
        assertTrue(part(vm, cube.id).mesh.closed)
        vm.undo()
        assertEquals(6, part(vm, cube.id).mesh.faceCount)

        // Kes: köşeyi çeviren üç kenara nokta → halka → köşe ayrı parça, iki taraf kapalı, tek geri alma adımı
        vm.selectPart(cube.id)
        val c0 = part(vm, cube.id)
        val cubeVolume = V25Geo.worldVolume(c0)
        for (n in nbrs) {
            val m = V25Geo.edgeMid(vm, part(vm, cube.id), corner, n)
            vm.tapAt(m.x, m.y, 30f)
        }
        assertEquals(listOf(8, 9, 10), vm.selectedPoints())
        val beforeCut = part(vm, cube.id).mesh
        vm.cutSelectedLoop()
        val m = vm.model.value!!
        assertEquals(2, m.parts.size)
        assertTrue("iki taraf kapalı", m.parts.all { it.mesh.closed })
        val piece = m.part(vm.selection.value.primary!!)!!
        assertEquals("kesilen köşe dört noktalı", 4, piece.mesh.vertexCount)
        val sum = m.parts.sumOf { V25Geo.worldVolume(it) }
        assertEquals(cubeVolume, sum, cubeVolume * 1e-3)
        assertTrue(V25Geo.worldVolume(piece) < cubeVolume * 0.1)
        // Kesme (eksik kenarların birleştirilmesi dahil) tek geri alma adımı
        vm.undo()
        assertEquals(1, vm.model.value!!.parts.size)
        assertEquals(beforeCut.faceCount, vm.model.value!!.parts[0].mesh.faceCount)
        assertEquals(11, vm.model.value!!.parts[0].mesh.vertexCount)
    }

    @Test
    fun faceSurfaceDetachAndHole() {
        val vm = vm()
        vm.selectPart(vm.model.value!!.parts[0].id)
        vm.deleteSelected()
        vm.addPart(PrimitiveKind.CYLINDER)
        vm.fitView()
        val cyl = vm.model.value!!.parts.single()
        vm.setTool(Tool3.FACE)
        vm.setFaceMode(FaceMode.SURFACE)
        // Yan yüzeye dokunmak bütün yan yüzeyi seçer
        val side = V25Geo.facingFace(vm, cyl) { abs(it.y) < 0.2f }
        val c = V25Geo.faceCenter(vm, cyl, side)
        vm.tapAt(c.x, c.y, 30f)
        val faces = vm.selection.value.faces
        assertTrue("yan yüzey: ${faces.size}", faces.size >= 8 && side in faces)
        faces.forEach { assertTrue(abs(V25Geo.worldNormal(cyl, it).y) < 0.3f) }
        // Ayrı parça: seçili yüzey yeni parça, kalan yerinde
        vm.detachFaces()
        val m = vm.model.value!!
        assertEquals(2, m.parts.size)
        assertEquals(faces.size, m.part(vm.selection.value.primary!!)!!.mesh.faceCount)
        vm.undo()
        assertEquals(1, vm.model.value!!.parts.size)

        // Delik: üst yüzey seçilir, kaydırıcıyla genişlik, Tamam ile açılır (arka planda)
        vm.selectPart(cyl.id)
        val top = V25Geo.facingFace(vm, cyl) { it.y > 0.9f }
        val t = V25Geo.faceCenter(vm, cyl, top)
        vm.tapAt(t.x, t.y, 30f)
        assertTrue(vm.selection.value.faces.isNotEmpty())
        vm.startOp(OpKind.HOLE)
        vm.setOpAmount(0.5f)
        val preview = vm.holePreview()
        assertNotNull(preview)
        assertTrue("delik ekseni dikey", preview!!.second.y > 0.9f)
        val before = V25Geo.worldVolume(cyl)
        vm.applyOp()
        waitHeavy(vm)
        val holed = vm.model.value!!.parts.single()
        assertTrue(holed.mesh.closed)
        val after = V25Geo.worldVolume(holed)
        assertTrue("delik hacmi: $before → $after", after < before * 0.85 && after > before * 0.6)
        assertEquals(holed.position, cyl.position)
    }

    @Test
    fun booleansSliceGroupAndSeparate() {
        val vm = vm()
        val cube = vm.model.value!!.parts[0]
        vm.addPart(PrimitiveKind.SPHERE)
        val sphere = vm.model.value!!.parts[1]
        // Küre küpün köşesine bindirilir
        val cb = ModelEdit.worldBounds(cube)
        val sb = ModelEdit.worldBounds(sphere)
        val offset = sb.center - sphere.position
        vm.setTransform(sphere.id, position = cb.center + cb.size * 0.4f - offset)
        val vc = V25Geo.worldVolume(part(vm, cube.id))
        val vs = V25Geo.worldVolume(part(vm, sphere.id))
        fun both() { vm.selectPart(cube.id); vm.selectPart(sphere.id, add = true) }
        fun boolOp(op: Solid3.Op): Double {
            both()
            vm.booleanSelected(op)
            waitHeavy(vm)
            val r = vm.model.value!!.parts.single()
            assertTrue("$op kapalı", r.mesh.closed)
            assertEquals(r.id, vm.selection.value.primary)
            return V25Geo.worldVolume(r).also { vm.undo(); assertEquals(2, vm.model.value!!.parts.size) }
        }
        val union = boolOp(Solid3.Op.UNION)
        assertTrue("birleşim: $union", union > max(vc, vs) && union < vc + vs - 1e-3)
        val sub = boolOp(Solid3.Op.SUBTRACT)
        assertTrue("çıkarma: $sub", sub < vc - 1e-3 && sub > vc * 0.5)
        val inter = boolOp(Solid3.Op.INTERSECT)
        assertTrue("kesişim: $inter", inter > 1e-3 && inter < min(vc, vs))
        assertEquals("birleşim = a + b − kesişim", vc + vs - inter, union, 0.03 * union)

        // Parçala: küp Y düzlemiyle ortadan
        vm.selectPart(cube.id)
        vm.setSlice(SliceAxis.Y, 0f)
        assertNotNull(vm.slicePlane())
        vm.sliceSelected()
        waitHeavy(vm)
        val m = vm.model.value!!
        assertEquals(3, m.parts.size)
        val halves = vm.selection.value.parts.map { m.part(it)!! }
        assertEquals(2, halves.size)
        halves.forEach {
            assertTrue(it.mesh.closed)
            assertEquals(vc / 2, V25Geo.worldVolume(it), vc * 0.01)
        }
        // Grupla (tek parça, iki kopuk kısım) → Ayır
        vm.mergeSelected()
        assertEquals(2, vm.model.value!!.parts.size)
        vm.separateSelected()
        assertEquals(3, vm.model.value!!.parts.size)
        assertEquals(2, vm.selection.value.parts.size)
    }

    @Test
    fun brushDrawsLinesAndSolids() {
        val vm = vm()
        vm.setTool(Tool3.DRAW)
        vm.setPlane(DrawPlane.FRONT)
        val plane = vm.drawPlane()!!
        fun path(points: List<Offset>): FloatArray =
            points.mapNotNull { vm.planePoint(plane, it.x, it.y) }.flatMap { listOf(it.x, it.y, it.z) }.toFloatArray()
        val wave = (0..30).map { Offset(250f + it * 14f, 260f + 40f * sin(it / 4f)) }
        val ring = (0..40).map { val a = it / 40.0 * 2 * Math.PI; Offset(450f + 120f * cos(a).toFloat(), 600f + 90f * sin(a).toFloat()) }
        val profile = listOf(Offset(450f, 750f), Offset(520f, 740f), Offset(540f, 690f), Offset(500f, 620f), Offset(520f, 560f), Offset(450f, 540f))

        // Çizgi: 3B çizgi (parça değil)
        val strokes = vm.model.value!!.strokes.size
        vm.finishBrush(path(wave), null, plane.second)
        assertEquals(strokes + 1, vm.model.value!!.strokes.size)

        vm.setBrushWidth(24f)
        val kinds = listOf(Brush3Kind.TUBE to wave, Brush3Kind.RIBBON to wave, Brush3Kind.WALL to ring, Brush3Kind.LATHE to profile, Brush3Kind.INFLATE to ring)
        for ((kind, pts) in kinds) {
            vm.setBrush(vm.brush.value.copy(kind = kind, height = 0.6f))
            val count = vm.model.value!!.parts.size
            val pressures = if (kind == Brush3Kind.TUBE) FloatArray(pts.size) { 0.3f + it / 40f } else null
            vm.finishBrush(path(pts), pressures, plane.second)
            waitHeavy(vm)
            val m = vm.model.value!!
            assertEquals("$kind yeni parça", count + 1, m.parts.size)
            val p = m.parts.last()
            assertEquals(p.id, vm.selection.value.primary)
            assertEquals(Model3DEditorViewModel.brushName(kind), p.name)
            assertTrue("$kind kapalı cisim", p.mesh.closed)
            assertTrue("$kind hacim", V25Geo.worldVolume(p) > 1e-4)
            assertEquals(vm.brushColor.value, p.material.color)
        }
    }

    @Test
    fun imageExportRendersAndSaves() {
        val vm = vm()
        val png = runBlocking { vm.renderImage(ImageExport(size = 400, aspect = 4f / 3f, background = 0)) }!!
        assertEquals(400, png.width)
        assertEquals(300, png.height)
        assertEquals("saydam köşe", 0, android.graphics.Color.alpha(png.getPixel(2, 2)))
        assertTrue("ortada model", android.graphics.Color.alpha(png.getPixel(200, 150)) > 200)
        val tall = runBlocking { vm.renderImage(ImageExport(size = 400, aspect = 0.75f, jpeg = true, ground = false)) }!!
        assertEquals(300, tall.width)
        assertEquals(400, tall.height)
        assertEquals("JPEG arka planı beyaz", android.graphics.Color.WHITE, tall.getPixel(1, 1))
        // Cihaz sınırı: en uzun kenar bütçeyi aşmaz
        val big = runBlocking { vm.renderImage(ImageExport(size = 10_000), previewSide = null) }!!
        assertTrue(max(big.width, big.height) <= Budget3D.exportMaxSide)
        // Resimlerim'e kayıt (kaynağı 3B model)
        val uri = runBlocking { vm.saveImage(ImageExport(size = 512)) }
        assertNotNull(uri)
        val list = runBlocking { db.savedImageDao().observeAll().first() }
        assertEquals(1, list.size)
        assertEquals("model3d:${vm.model.value!!.id}", list[0].canvasLocalId)
        assertEquals(512, list[0].width)
    }

    @Test
    fun deviceTierBudgets() {
        // Robolectric: bellek bilinmiyor → orta sınıf
        assertEquals(DeviceCaps.Tier.MID, DeviceCaps.tier)
        assertEquals(12_000, Budget3D.csgTriangles)
        assertTrue(Budget3D.exportMaxSide in 1..2048)
        assertEquals(256, Budget3D.shadowSize)
    }
}

/**
 * v25 (2): 3B düzenleyici arayüzü: Nokta aracı (kenara dokunarak nokta, sırayla numaralı seçim, Kes), Yüz aracında Yüzey
 * kipi, İşlemler paneli (birleştir; parçalama düzlemi sahnede), 3B fırça türleri ve resim olarak dışa aktarma penceresi
 * (ekran görüntüleriyle).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V25Model3DEditorUiTest : V23EditorBase() {

    private lateinit var vm: Model3DEditorViewModel

    private fun open() {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        val k = GlobalContext.get()
        vm = Model3DEditorViewModel(k.get(), k.get(), k.get(), null, null, null, k.get(), images = k.get())
        compose.runOnUiThread {
            compose.activity.setContent { DrawGuidanceTheme(darkTheme = false) { Model3DEditorScreen(null, null, null, onBack = {}, vm = vm) } }
        }
        waitFor { exists("model3d_editor") && vm.currentView() != null }
        settleFor(800)
    }


    private fun tapScene(p: Offset) {
        compose.onNodeWithTag("model3d_viewport").performTouchInput { click(p) }
        compose.waitForIdle()
    }

    private fun idleHeavy() = waitFor(90_000) { vm.busy.value == null }

    @Test
    fun pointFaceOpsBrushAndImage() {
        open()
        val cube = vm.model.value!!.parts[0]
        compose.runOnUiThread { vm.selectPart(cube.id) }
        tool3("Nokta")
        val corner = V25Geo.nearestCorner(vm, cube)
        val nbrs = V25Geo.neighbors(cube.mesh, corner)
        for (n in nbrs) tapScene(V25Geo.edgeMid(vm, vm.model.value!!.part(cube.id)!!, corner, n))
        waitFor { vm.selectedPoints().size == 3 }
        // v27 seçim durumu: "3 nokta seçili"
        waitForText("3 nokta", substring = true)
        settleFor(600)
        shot("v25-01-point-tool")
        action3("Kes")
        waitFor { vm.model.value!!.parts.size == 2 }
        settleFor(800)
        shot("v25-02-cut-piece")

        // Yüz aracı: Yüzey kipi
        tool3("Yüz")
        action3("Yüzey")
        val rest = vm.model.value!!.parts[0]
        compose.runOnUiThread { vm.selectPart(rest.id) }
        val f = V25Geo.facingFace(vm, rest) { it.y > 0.9f }
        tapScene(V25Geo.faceCenter(vm, rest, f))
        waitFor { vm.selection.value.faces.isNotEmpty() }
        assertTrue(compose.onAllNodesWithText("Delik aç").fetchSemanticsNodes().isNotEmpty())
        settleFor(600)
        shot("v25-03-face-surface")

        // İşlemler paneli: iki parça seçili, parçalama düzlemi sahnede
        val ids = vm.model.value!!.parts.map { it.id }
        compose.runOnUiThread { vm.selectPart(ids[0]); vm.selectPart(ids[1], add = true) }
        mode3("Seç")
        action3("İşlemler")
        waitFor { exists("model3d_panel_ops") }
        settleFor(800)
        shot("v25-04-ops-panel")
        compose.onNode(hasText("Birleştir") and hasAnyAncestor(hasTestTag("model3d_panel_ops"))).performScrollTo().performClick()
        idleHeavy()
        waitFor { vm.model.value!!.parts.size == 1 }
        assertTrue(vm.model.value!!.parts.single().mesh.closed)
        compose.onNodeWithTag("model3d_panel_close").performClick()

        // 3B fırça: Tüp
        tool3("Çiz")
        waitFor { exists("model3d_panel_brush") }
        compose.onNode(hasText("Tüp") and hasAnyAncestor(hasTestTag("model3d_brush_kinds"))).performClick()
        waitFor { vm.brush.value.kind == Brush3Kind.TUBE }
        settleFor(400)
        shot("v25-05-brush-panel")
        val vp = compose.onNodeWithTag("model3d_viewport").fetchSemanticsNode().size
        compose.onNodeWithTag("model3d_viewport").performTouchInput {
            down(Offset(vp.width * 0.15f, vp.height * 0.3f))
            for (i in 1..30) moveTo(Offset(vp.width * (0.15f + i * 0.022f), vp.height * (0.3f + 0.08f * sin(i / 4.0).toFloat())))
            up()
        }
        idleHeavy()
        waitFor { vm.model.value!!.parts.size == 2 }
        assertEquals("Tüp", vm.model.value!!.parts.last().name)
        settleFor(800)
        shot("v25-06-brush-tube")

        // Resim olarak dışa aktar
        compose.onNodeWithContentDescription("Diğer seçenekler").performClick()
        waitForText("Resim olarak dışa aktar")
        compose.onNodeWithText("Resim olarak dışa aktar").clickInPopup()
        waitFor { exists("model3d_image_export") }
        settleFor(1500)
        shotAll("v25-07-image-export")
        compose.onNodeWithText("Kaydedilenlere kaydet").performScrollTo().performClick()
        waitFor(30_000) { runBlocking { GlobalContext.get().get<SavedImageDao>().observeAll().first() }.isNotEmpty() }
        val saved = runBlocking { GlobalContext.get().get<SavedImageDao>().observeAll().first() }.single()
        assertEquals("model3d:${vm.model.value!!.id}", saved.canvasLocalId)
        waitFor { !exists("model3d_image_export") }
    }
}

/** v25 (3): tuvalde Ekle menüsünde ayrı "3B şekil ekle": pencerede şekil seçilir, alttaki "3B şekil ekle" düğmesi ekler. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V25CanvasAdd3DTest : V23EditorBase() {
    @Test
    fun addMenuSelectThenAdd() {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        createCanvasAndOpen("3B ekle")
        if (compose.onAllNodesWithContentDescription("Ekle menüsü").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithContentDescription("Ekle menüsü").performClick()
            waitForText("3B şekil ekle")
            settleFor(300)
            shotAll("v25-08-add-menu")
            compose.onNodeWithText("3B şekil ekle").clickInPopup()
        } else addMenu("3B şekil ekle")
        waitFor { exists("model3d_picker") }
        // Seçmeden düğme pasif
        compose.onNodeWithTag("model3d_add_button").assertIsNotEnabled()
        assertTrue(compose.onAllNodesWithText("Henüz şekil seçilmedi").fetchSemanticsNodes().isNotEmpty())
        // Hazır şekillerden biri seçilir (dokunmak eklemez)
        val item = com.karapp.drawguidance.content.model3d.Catalog3D.all.first { it.category == com.karapp.drawguidance.content.model3d.Catalog3D.categories.first() }
        compose.onNode(hasContentDescription(item.name) and hasAnyAncestor(hasTestTag("model3d_catalog"))).performScrollTo().performClick()
        waitForText("Seçili: ${item.name}")
        settleFor(400)
        assertTrue(canvas().strokes.none { it.model != null })
        compose.onNodeWithTag("model3d_add_button").assertIsEnabled()
        shotAll("v25-09-picker-selected")
        compose.onNodeWithTag("model3d_add_button").performClick()
        waitFor(30_000) { canvas().strokes.any { it.model != null } }
        waitFor { !exists("model3d_picker") }
    }
}
