package com.karapp.drawguidance

import com.karapp.drawguidance.data.local.ContentCodec
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.PanelRef
import com.karapp.drawguidance.data.model.ShapeSpec
import com.karapp.drawguidance.data.model.ShapeStyle
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.data.model.SubPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID
import kotlin.random.Random

/**
 * v36 içerik biçimi hız ve boyut ölçümü: kalabalık bir çizgi roman sayfası (çok parçalı karakter/nesne şekilleri,
 * şablon panelleri, fırça izleri). Kodlama/çözme aynı içeriği verir; süre ve boyut yazdırılır. Biçim değişmeden
 * yapılan iyileştirmeler (şekil tanımında varsayılan alanların yazılmaması, kilitsiz bayt yazıcı, hızlı kimlik ve
 * renk denetimi) eski kayıtları ve eski sürümleri etkilemez.
 */
class V36CodecPerfTest {

    private fun page(seed: Int): List<Stroke> {
        val rnd = Random(seed)
        val out = ArrayList<Stroke>()
        fun q(v: Float) = ContentCodec.quantize(v)
        // 40 karakter/nesne: her biri 120 parça, parça başına 6-14 nokta
        repeat(40) { k ->
            val pts = ArrayList<StrokePoint>(); val subs = ArrayList<SubPath>()
            repeat(120) { j ->
                val n = 6 + rnd.nextInt(9)
                repeat(n) { pts += StrokePoint(q(rnd.nextFloat() * 1000f), q(rnd.nextFloat() * 1400f)) }
                subs += when (j % 4) {
                    0 -> SubPath(n, closed = true, fillColorHex = "#F2C9A0")
                    1 -> SubPath(n, closed = true, widthScale = 0f, fillColorHex = "@ink")
                    2 -> SubPath(n, closed = false, widthScale = 0.6f)
                    else -> SubPath(n, closed = true)
                }
            }
            out += Stroke(UUID.randomUUID().toString(), pts, "#1D1D24", BrushType.SHAPE, 2.75f, 1f, k, shape = ShapeSpec("lib.chr.hero.happy.stand", subs, ShapeStyle("#1D1D24", 2.75f)))
        }
        // 6 panel
        repeat(6) { k ->
            val pts = listOf(StrokePoint(45f, 45f + k * 200), StrokePoint(955f, 45f + k * 200), StrokePoint(955f, 230f + k * 200), StrokePoint(45f, 230f + k * 200))
            out += Stroke(UUID.randomUUID().toString(), pts, "#111111", BrushType.SHAPE, 6f, 1f, 40 + k,
                shape = ShapeSpec("comic.panel", listOf(SubPath(4, closed = true)), ShapeStyle("#111111", 6f), panel = PanelRef("set", k, "P1|0.045|0.022|0|R[1:p0,1:p1,1:p2,1:p3,1:p4,1:p5]|")))
        }
        // 300 fırça izi
        repeat(300) { k ->
            var x = rnd.nextFloat() * 1000f; var y = rnd.nextFloat() * 1400f
            val pts = List(60) { x += rnd.nextFloat() * 6 - 3; y += rnd.nextFloat() * 6 - 3; StrokePoint(q(x), q(y)) }
            out += Stroke(UUID.randomUUID().toString(), pts, "#33" + "%04X".format(k), BrushType.PENCIL, 4f, 1f, 46 + k)
        }
        return out
    }

    @Test
    fun roundTripAndTiming() {
        val pages = List(3) { page(it) }
        // Isınma
        repeat(3) { ContentCodec.decodePages(ContentCodec.encodePages(pages)) }
        var enc = 0L; var dec = 0L
        var bytes = ByteArray(0)
        val runs = 5
        repeat(runs) {
            val t0 = System.nanoTime()
            bytes = ContentCodec.encodePages(pages)
            val t1 = System.nanoTime()
            val back = ContentCodec.decodePages(bytes)
            val t2 = System.nanoTime()
            enc += t1 - t0; dec += t2 - t1
            assertEquals(pages, back)
        }
        println("V36 kodlama: ${bytes.size / 1024} KB, kodlama ${enc / runs / 1_000_000} ms, çözme ${dec / runs / 1_000_000} ms")
        // Panel bilgisi korunur
        val panels = ContentCodec.decodePages(bytes)[0].mapNotNull { it.shape?.panel }
        assertEquals(6, panels.size)
        assertTrue(bytes.size < 4_000_000)
    }
}
