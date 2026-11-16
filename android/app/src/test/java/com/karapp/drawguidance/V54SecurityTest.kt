package com.karapp.drawguidance

import com.karapp.drawguidance.data.images.ImageSecurity
import com.karapp.drawguidance.data.share.SharePairing
import com.karapp.drawguidance.ui.screens.canvasedit.components.SimpleMenus
import com.karapp.drawguidance.ui.screens.canvasedit.components.notesFirst
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.zip.CRC32

/**
 * v54 güvenlik: resim yapı/gömülü kod denetimi ([ImageSecurity]), şifreli paylaşımın anahtar türetimi ve şifrelemesi
 * ([SharePairing]; iOS `V54SecurityTests` aynı sabit değerleri doğrular), not almada yazı kutusu Ekle menüsünün başında.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class V54SecurityTest {
    @org.junit.After fun tearDown() = org.koin.core.context.stopKoin()

    private fun png(w: Int = 20, h: Int = 20): ByteArray = ImageImportValidatorTest.png(ImageImportValidatorTest.simpleDrawing(w, h))

    private fun jpeg(w: Int = 20, h: Int = 20): ByteArray = ImageImportValidatorTest.jpeg(ImageImportValidatorTest.simpleDrawing(w, h))

    private fun withText(png: ByteArray, text: String): ByteArray {
        val data = ("Comment\u0000$text").toByteArray(Charsets.ISO_8859_1)
        val chunk = java.nio.ByteBuffer.allocate(12 + data.size).putInt(data.size).put("tEXt".toByteArray()).put(data)
        val crc = CRC32().apply { update(chunk.array(), 4, 4 + data.size) }.value
        chunk.putInt(crc.toInt())
        val iend = png.size - 12
        return png.copyOfRange(0, iend) + chunk.array() + png.copyOfRange(iend, png.size)
    }

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    @Test
    fun cleanImagesPass() {
        assertEquals(ImageSecurity.Verdict.CLEAN, ImageSecurity.inspect(png(), "image/png"))
        assertEquals(ImageSecurity.Verdict.CLEAN, ImageSecurity.inspect(jpeg(), "image/jpeg"))
        assertEquals(ImageSecurity.Verdict.CLEAN, ImageSecurity.inspect(withText(png(), "Çizim Rehberi"), "image/png"))
        // Fotoğraf sonuna eklenmiş zararsız veri (ör. telefonun eklediği bölüm) reddedilmez; yeniden kodlanınca atılır
        assertEquals(ImageSecurity.Verdict.CLEAN, ImageSecurity.inspect(jpeg() + ByteArray(4096) { (it * 7).toByte() }, "image/jpeg"))
    }

    @Test
    fun embeddedCodeAndPolyglotsRejected() {
        assertEquals(ImageSecurity.Verdict.SUSPICIOUS, ImageSecurity.inspect(withText(png(), "<script>alert(1)</script>"), "image/png"))
        assertEquals(ImageSecurity.Verdict.SUSPICIOUS, ImageSecurity.inspect(withText(png(), "<?php echo 1; ?>"), "image/png"))
        assertEquals(ImageSecurity.Verdict.SUSPICIOUS, ImageSecurity.inspect(png() + "<html><body></body></html>".toByteArray(), "image/png"))
        assertEquals(ImageSecurity.Verdict.SUSPICIOUS, ImageSecurity.inspect(jpeg() + byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 3, 4, 20, 0), "image/jpeg"))
        assertEquals(ImageSecurity.Verdict.SUSPICIOUS, ImageSecurity.inspect(jpeg() + byteArrayOf('M'.code.toByte(), 'Z'.code.toByte(), 0, 0), "image/jpeg"))
        assertEquals(ImageSecurity.Verdict.SUSPICIOUS, ImageSecurity.inspect(jpeg() + "#!/bin/sh\nrm -rf".toByteArray(), "image/jpeg"))
        // Kesik ya da yapısı bozuk dosyalar
        val p = png()
        assertEquals(ImageSecurity.Verdict.MALFORMED, ImageSecurity.inspect(p.copyOfRange(0, p.size - 20), "image/png"))
        assertEquals(ImageSecurity.Verdict.MALFORMED, ImageSecurity.inspect(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x12, 0x34), "image/jpeg"))
    }

    @Test
    fun pairingDerivationMatchesFixedVector() {
        // iOS V54SecurityTests ile aynı sabit girdiler ve beklenen değerler (Python cryptography ile de doğrulandı)
        val z = ByteArray(32) { it.toByte() }
        val t = SharePairing.transcript(byteArrayOf(0x7F) + "hello-a".toByteArray(), byteArrayOf(0x7E) + "hello-b".toByteArray())
        assertEquals("b724697539edc0627144b6a820464b1df90df9bbb29f0310a73950cef6c76840", hex(t))
        val k = SharePairing.derive(z, "482913".toByteArray(), t)
        assertEquals("a036fa627d7181c72499206efece895a867d6cef4b7db8c25f0040ae355b2e42", hex(k.aToB))
        assertEquals("2935e19470b70101efe8f3489c0603587596547cc8c31904a5a36b94c9fff47d", hex(k.bToA))
        assertEquals("268fa0a55499ec5f69adfb29ae264bca1e004d5b04119701ef4b684b8db5a420", hex(k.trust))
        assertEquals("3bf6db88afdd5e4083930c3fc0d4f3c9e1a513f986be8cedaa62aeec6597c310", hex(SharePairing.confirmTag(k, "A")))
        val sealer = SharePairing.Sealer(k.aToB)
        assertEquals("6e7fc67cb9dfb7325b9919d289af22a8cdc692691effd0", hex(sealer.seal("merhaba".toByteArray())))
        assertEquals("1ba98262168071683254edf6d19e71eae421f592dc", hex(sealer.seal("dunya".toByteArray())))
        // Yanlış şifre başka anahtar
        assertNotEquals(hex(k.confirm), hex(SharePairing.derive(z, "482914".toByteArray(), t).confirm))
    }

    @Test
    fun pairingRoundTripBetweenTwoSides() {
        val a = SharePairing.newKeyPair()
        val b = SharePairing.newKeyPair()
        val za = SharePairing.sharedSecret(a, SharePairing.publicRaw(b))
        val zb = SharePairing.sharedSecret(b, SharePairing.publicRaw(a))
        assertArrayEquals(za, zb)
        assertEquals(65, SharePairing.publicRaw(a).size)
        val t = SharePairing.transcript(byteArrayOf(1), byteArrayOf(2))
        val code = SharePairing.newCode()
        assertEquals(6, code.length)
        val ka = SharePairing.derive(za, code.toByteArray(), t)
        val kb = SharePairing.derive(zb, code.toByteArray(), t)
        assertTrue(SharePairing.same(SharePairing.confirmTag(ka, "A"), SharePairing.confirmTag(kb, "A")))
        val sealer = SharePairing.Sealer(ka.aToB)
        val opener = SharePairing.Opener(kb.aToB)
        repeat(3) { i -> assertEquals("çerçeve $i", opener.open(sealer.seal("çerçeve $i".toByteArray())).decodeToString()) }
        // Değiştirilmiş çerçeve açılmaz
        val bad = sealer.seal("x".toByteArray()).also { it[0] = (it[0] + 1).toByte() }
        assertTrue(runCatching { opener.open(bad) }.isFailure)
        assertFalse(SharePairing.same(SharePairing.confirmTag(ka, "A"), SharePairing.confirmTag(SharePairing.derive(za, "000000".toByteArray(), t), "A")))
    }

    @Test
    fun notesTextboxFirstInAddMenu() {
        assertEquals(SimpleMenus.Menu.ADD, SimpleMenus.menuOf("textbox"))
        val ordered = notesFirst(listOf("shape", "formula", "text", "image", "textbox", "diagram")) { it }
        assertEquals("textbox", ordered.first())
        val add = SimpleMenus.split(ordered) { it }.getValue(SimpleMenus.Menu.ADD)
        assertEquals("textbox", add.first())
    }
}
