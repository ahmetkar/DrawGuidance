package com.karapp.drawguidance.ui.drawing

import android.content.Context
import android.graphics.Typeface
import android.util.Log
import androidx.compose.ui.text.font.FontFamily
import com.karapp.drawguidance.data.model.TextFont
import java.util.concurrent.ConcurrentHashMap

/**
 * Yazı tipleri: sistem aileleri ve uygulamayla gelen dosyalar (`assets/fonts`). Yüklenen yazı tipleri bir kez
 * oluşturulup saklanır; dosya açılamazsa aynı türdeki sistem ailesine düşülür. Uygulama açılırken [init] edilir.
 */
object FontRegistry {
    @Volatile private var app: Context? = null
    private val base = ConcurrentHashMap<TextFont, Typeface>()
    private val styled = ConcurrentHashMap<Pair<TextFont, Int>, Typeface>()
    private val families = ConcurrentHashMap<TextFont, FontFamily>()

    fun init(context: Context) {
        app = context.applicationContext
    }

    private fun load(font: TextFont): Typeface = base.getOrPut(font) {
        val asset = font.asset
        val context = app
        if (asset != null && context != null) {
            runCatching { Typeface.createFromAsset(context.assets, asset) }
                .onFailure { com.karapp.drawguidance.diagnostics.AppLog.w("DrawGuidance", "Yazı tipi yüklenemedi: $asset", it) }
                .getOrNull()
                ?.let { return@getOrPut it }
        }
        Typeface.create(font.family, Typeface.NORMAL)
    }

    /**
     * Uygulamayla gelen yazı tiplerini arka planda önceden yükler: ekranda ilk kullanıldıklarında ana iş
     * parçacığında dosya okunmaz (takılma ve StrictMode disk uyarısı olmaz).
     */
    fun preload() {
        TextFont.entries.forEach { if (it.asset != null) runCatching { load(it) } }
    }

    /** Yazı tipinin [style] (Typeface.BOLD/ITALIC/...) biçimi; dosyada yoksa sistem yapay kalın/eğik üretir. */
    fun typeface(font: TextFont, style: Int = Typeface.NORMAL): Typeface =
        if (style == Typeface.NORMAL) load(font) else styled.getOrPut(font to style) { Typeface.create(load(font), style) }

    /** Ekranda yazı tipinin kendisiyle gösterilmesi için Compose karşılığı. */
    fun family(font: TextFont): FontFamily = families.getOrPut(font) {
        when {
            font.asset != null -> FontFamily(load(font))
            font == TextFont.SANS -> FontFamily.SansSerif
            font == TextFont.SERIF -> FontFamily.Serif
            font == TextFont.MONO -> FontFamily.Monospace
            font == TextFont.HANDWRITING -> FontFamily.Cursive
            else -> FontFamily(load(font))
        }
    }
}
