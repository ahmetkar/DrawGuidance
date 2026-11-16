package com.karapp.drawguidance

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.platform.DeviceCaps
import com.karapp.drawguidance.ui.drawing.ToneRenderer
import com.karapp.drawguidance.data.model.ToneFill
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * v53 bellek düzeni: kapanan tuvalin çizim önbellekleri yalnız başka tuval açık değilken (kayıt için kısa bir
 * gecikmeyle) boşaltılır; bellek uyarısı bütün önbellekleri boşaltır.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class V53MemoryTest {

    @Test
    fun editorCachesAreReleasedOnlyWhenNoCanvasIsOpen() {
        var released = 0
        DeviceCaps.onEditorClosed { released++ }
        DeviceCaps.editorOpened()
        DeviceCaps.editorOpened()
        DeviceCaps.editorClosed()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
        assertEquals("başka tuval açıkken boşaltılmamalı", 0, released)
        DeviceCaps.editorClosed()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertEquals("kayıt için beklenmeli", 0, released)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertEquals(1, released)
    }

    @Test
    fun toneCacheIsReusedAndTrimmed() {
        val tone = ToneFill("tone_dot", 0.4f)
        val a = ToneRenderer.prims(tone, 0f, 0f, 400f, 300f)
        val b = ToneRenderer.prims(tone, 1f, 0f, 401f, 300f)
        assertTrue("aynı kutu önbellekten gelmeli", a === b)
        DeviceCaps.trimMemory()
        val c = ToneRenderer.prims(tone, 0f, 0f, 400f, 300f)
        assertFalse("bellek uyarısından sonra yeniden üretilmeli", a === c)
        assertEquals(a.size, c.size)
    }

    @Test
    fun sfxOuterOutlineTakesRoomAroundTheLetters() {
        val p = com.karapp.drawguidance.content.comic.ComicSfx.presets.first()
        val spec = com.karapp.drawguidance.ui.screens.canvasedit.state.ComicController.sfxSpec(p, 100f)
        val plain = spec.copy(box = spec.box?.copy(effects = spec.box?.effects?.copy(outerHex = null)))
        assertTrue(com.karapp.drawguidance.ui.drawing.TextRenderer.pads(spec, 100f)[0] > com.karapp.drawguidance.ui.drawing.TextRenderer.pads(plain, 100f)[0])
        val bmp = android.graphics.Bitmap.createBitmap(600, 300, android.graphics.Bitmap.Config.ARGB_8888)
        val pts = com.karapp.drawguidance.ui.drawing.TextRenderer.quadAt(spec, 100f, 20f, 20f)
        val s = com.karapp.drawguidance.data.model.Stroke("s", pts, p.colors.last(), com.karapp.drawguidance.data.model.BrushType.TEXT, 100f, 1f, 0, text = spec)
        com.karapp.drawguidance.ui.drawing.TextRenderer.draw(android.graphics.Canvas(bmp), s, android.graphics.Color.BLACK)
    }
}
