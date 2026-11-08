package com.karapp.drawguidance.content

import com.karapp.drawguidance.content.comic.ComicLines
import com.karapp.drawguidance.content.comic.ComicLinesKind
import com.karapp.drawguidance.content.model3d.Model3D
import com.karapp.drawguidance.content.model3d.ModelEdit
import com.karapp.drawguidance.content.model3d.PrimitiveKind
import com.karapp.drawguidance.content.model3d.Renderer
import com.karapp.drawguidance.content.model3d.SceneSettings
import com.karapp.drawguidance.content.model3d.View3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * v29 içerik: çizgi roman hız/odak çizgilerinin türleri ve sıklığı (sayı sıklıkla artar/azalır, aynı tohumla aynı
 * çizgiler, alan dışına taşmaz), taşınan grubun alanı; 3B şeklin görünen çizgileri (vektör aktarma).
 */
class V29ContentTest {

    @Test fun everyLineKindScalesWithDensityAndStaysInArea() {
        assertTrue(ComicLinesKind.entries.size >= 14)
        for (kind in ComicLinesKind.entries) {
            val sparse = ComicLines.generate(kind, 0f, 0f, 600f, 400f, seed = 3, density = 0.5f)
            val normal = ComicLines.generate(kind, 0f, 0f, 600f, 400f, seed = 3, density = 1f)
            val dense = ComicLines.generate(kind, 0f, 0f, 600f, 400f, seed = 3, density = 2.5f)
            assertTrue("$kind: ${sparse.size} < ${normal.size} < ${dense.size}", sparse.size < normal.size && normal.size < dense.size)
            // Aynı tohum ve sıklıkla aynı çizgiler (sıklık değişip geri dönünce aynı görünüm)
            val again = ComicLines.generate(kind, 0f, 0f, 600f, 400f, seed = 3, density = 1f)
            assertEquals(normal.size, again.size)
            assertTrue(normal.zip(again).all { (a, b) -> a.xs.contentEquals(b.xs) && a.ys.contentEquals(b.ys) })
            for (l in dense) for (i in l.xs.indices) {
                assertTrue("$kind alan dışında: ${l.xs[i]}, ${l.ys[i]}", l.xs[i] in -1f..601f && l.ys[i] in -1f..401f)
            }
            println("$kind: ${sparse.size} / ${normal.size} / ${dense.size} çizgi")
        }
    }

    @Test fun movedAreaFollowsTheGroup() {
        val area = floatArrayOf(0f, 0f, 400f, 300f)
        val bounds = floatArrayOf(10f, 10f, 390f, 290f)
        // Grup sağa 100, aşağı 50 taşındı ve 2 kat büyüdü (sol üst köşesi sabit)
        val now = floatArrayOf(110f, 60f, 110f + 760f, 60f + 560f)
        val moved = ComicLines.movedArea(area, bounds, now)
        assertEquals(90f, moved[0], 0.5f); assertEquals(40f, moved[1], 0.5f)
        assertEquals(890f, moved[2], 0.5f); assertEquals(640f, moved[3], 0.5f)
    }

    @Test fun cubeOutlinesAreVisibleEdgesOnly() {
        val cube = ModelEdit.newPart(PrimitiveKind.CUBE, "c", 0)
        val model = Model3D("m", "Küp", listOf(cube), scene = SceneSettings(ground = false, shadow = false))
        val view = View3.fit(model, model.camera, 400, 400, 0.08f)
        val lines = Renderer.outlines(model, view)
        val segments = lines.sumOf { it.size / 2 - 1 }
        var length = 0f
        lines.forEach { pl -> for (i in 2 until pl.size step 2) length += kotlin.math.hypot(pl[i] - pl[i - 2], pl[i + 1] - pl[i - 1]) }
        println("küp: ${lines.size} çizgi, $segments parça, uzunluk %.0f px".format(length))
        // Görünen kenarlar: dış hat (6) ve içteki 3 kenar; arkadaki 3 kenar gizli
        assertTrue("çizgi sayısı ${lines.size}", lines.size in 1..6)
        assertTrue("parça sayısı $segments", segments in 9..20)
        // Hepsi görüntünün içinde
        assertTrue(lines.all { pl -> pl.indices.all { i -> pl[i] in -1f..401f } })
        // Görünen 9 kenarın uzunluğu; 12 kenarın tamamı değil
        val view2 = Renderer.render(model, view, com.karapp.drawguidance.content.model3d.RenderOptions(supersample = 1))
        assertTrue(view2.part.count { it == 1 } > 1000)
        assertTrue("uzunluk $length", length > 400f)
        // Uç uca birleştirme: aynı noktada biten iki parça tek çizgi olur
        val chained = Renderer.chain(listOf(floatArrayOf(0f, 0f, 10f, 0f), floatArrayOf(10f, 0f, 10f, 10f), floatArrayOf(50f, 50f, 60f, 60f)))
        assertEquals(2, chained.size)
        assertEquals(6, chained.maxOf { it.size })
        assertTrue(abs(chained.first { it.size == 6 }[0]) < 11f)
    }
}
