package com.karapp.drawguidance

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.ui.screens.canvasedit.challenge.PracticeLevels
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.annotation.Config

/** v52 ödüllü reklamla açılan alıştırmalar arttı: kimlikler tekil, her hedef çizilebilir. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V52PracticeLevelsTest {
    @After fun tearDown() { stopKoin() }

    @Test fun moreRewardLevels() {
        val all = PracticeLevels.all
        assertEquals("kimlikler tekil", all.size, all.map { it.id }.toSet().size)
        assertTrue("en az 30 ödüllü alıştırma", all.count { it.reward } >= 30)
        assertTrue("basit resimde de ödüllü alıştırma var", PracticeLevels.forCanvas(com.karapp.drawguidance.data.model.CanvasKind.SIMPLE_ART).count { it.reward } >= 5)
        for (l in all) {
            val t = PracticeLevels.target(l, 1080, 1440)
            assertTrue("${l.id} hedefi boş", t.lines.isNotEmpty())
        }
    }
}
