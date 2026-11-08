package com.karapp.drawguidance

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.settings.ColorPaletteStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class ColorPaletteStoreTest {

    @After fun tearDown() = stopKoin()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun savedColorsAreUniqueNewestFirstLimitedAndPersisted() {
        val store = ColorPaletteStore(context)
        store.add("#ff0000")
        store.add("#00FF00")
        store.add("#FF0000")
        assertEquals(listOf("#FF0000", "#00FF00"), store.colors.value)

        repeat(30) { store.add(String.format("#%06X", it)) }
        assertEquals(ColorPaletteStore.MAX_COLORS, store.colors.value.size)
        assertEquals("#00001D", store.colors.value.first())

        store.remove("#00001D")
        // Yeni bir örnek aynı kayıtları okur (uygulama yeniden açılınca kaybolmaz)
        assertEquals(store.colors.value, ColorPaletteStore(context).colors.value)
        assertEquals("#00001C", ColorPaletteStore(context).colors.value.first())
    }
}
