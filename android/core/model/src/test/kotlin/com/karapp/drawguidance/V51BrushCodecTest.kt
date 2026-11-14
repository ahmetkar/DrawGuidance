package com.karapp.drawguidance

import com.karapp.drawguidance.data.local.ContentCodec
import com.karapp.drawguidance.data.model.BrushParams
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.FxParams
import com.karapp.drawguidance.data.model.PenDynamics
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.data.model.TipParams
import com.karapp.drawguidance.data.model.TipShape
import org.junit.Assert.assertEquals
import org.junit.Test

/** v51 yeni fırçalar, uç/kalem ayarları ve gövde dönüşü/basıncı kayıtta kaybolmamalı. */
class V51BrushCodecTest {
    private fun q(v: Float) = ContentCodec.quantizeUnit(v)

    @Test fun newBrushTypesAndParamsRoundTrip() {
        val strokes = listOf(
            BrushType.ACRYLIC, BrushType.OIL_PAINT, BrushType.CLOUD_SMOKE, BrushType.FX_BLUR, BrushType.FX_SHARPEN, BrushType.FX_DODGE,
            BrushType.FX_BURN, BrushType.FX_SPONGE, BrushType.FX_CLONE, BrushType.FX_HEAL, BrushType.FX_MIXER,
        ).mapIndexed { i, t ->
            Stroke(
                id = "s$i", points = listOf(StrokePoint(ContentCodec.quantize(10f + i), ContentCodec.quantize(20f))),
                colorHex = "#336699", brushType = t, sizePx = 24f, opacity = 0.8f, order = i,
                params = BrushParams(
                    tip = TipParams(minSize = 0.3f, hardness = 0.6f, shape = TipShape.BRISTLE, wetness = 0.5f),
                    pen = PenDynamics(pressure = 0.7f, barrel = -0.4f),
                    fx = FxParams(mode = 2, srcDx = 12f, srcDy = -8f),
                ),
            )
        }
        assertEquals(strokes, ContentCodec.decode(ContentCodec.encode(strokes)))
    }

    @Test fun rollAndBarrelRoundTrip() {
        val pts = listOf(
            StrokePoint(1f, 2f, q(0.5f), q(0.2f), q(0.3f), q(0.25f), q(1f)),
            StrokePoint(3f, 4f, q(0.6f), 0f, 0f, 0f, q(0.5f)),
        )
        val s = Stroke(id = "r", points = pts, colorHex = "#000000", brushType = BrushType.PENCIL, sizePx = 4f, opacity = 1f, order = 0)
        assertEquals(listOf(s), ContentCodec.decode(ContentCodec.encode(listOf(s))))
    }
}
