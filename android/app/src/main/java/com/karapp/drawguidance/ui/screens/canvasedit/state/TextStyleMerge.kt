package com.karapp.drawguidance.ui.screens.canvasedit.state

import com.karapp.drawguidance.data.model.TextBox
import com.karapp.drawguidance.data.model.TextEffects
import com.karapp.drawguidance.data.model.TextSpec
import com.karapp.drawguidance.ui.screens.canvasedit.TextSettings

/**
 * v29 yazı biçiminde yalnız kullanıcının değiştirdiği alanlar uygulanır: [before] → [after] arasında değişen alanlar
 * (yazı tipi, kalın, eğik, altı çizili, hizalama, paragraf, harf kenarlığı, kutu görünümü, gölge, gradyan, büyük
 * harf, harf aralığı, boyut) [target]'a geçer; değişmeyenler hedefin kendi değerinde kalır.
 *
 * Böylece özel biçimli bir yazı (çizgi roman balonu, efekt yazısı) açılınca ya da seçilince onun biçimi "son ayar"
 * olmaz; kullanıcı yalnız yazı tipini değiştirdiyse son ayara yalnız yazı tipi geçer. Birden çok yazı seçiliyken de
 * her yazının diğer özellikleri korunur.
 */
object TextStyleMerge {

    /** Kutunun paylaşılan görünümü (eni, boyu, balonu ve efektleri hariç). */
    fun boxStyle(box: TextBox?): TextBox? = box?.copy(width = null, height = null, balloon = null, effects = null)?.orNull()

    fun merge(target: TextSettings, before: TextSettings, after: TextSettings): TextSettings {
        val t = target.spec
        val b = before.spec
        val a = after.spec
        fun <T> pick(f: (TextSpec) -> T): T = if (f(a) != f(b)) f(a) else f(t)
        val style = if (boxStyle(a.box) != boxStyle(b.box)) boxStyle(a.box) else boxStyle(t.box)
        val effects = mergeEffects(t.box?.effects, b.box?.effects, a.box?.effects)
        val box = (style ?: TextBox()).copy(
            width = t.box?.width, height = t.box?.height, balloon = t.box?.balloon, effects = effects,
        ).orNull()
        // Harf kenarlığı rengi ve kalınlığı birlikte (biri değişince ikisi de geçer)
        val outlineChanged = a.outlineColorHex != b.outlineColorHex || a.outlineWidth != b.outlineWidth
        return TextSettings(
            t.copy(
                font = pick { it.font },
                bold = pick { it.bold },
                italic = pick { it.italic },
                underline = pick { it.underline },
                align = pick { it.align },
                paragraph = pick { it.paragraph },
                outlineColorHex = if (outlineChanged) a.outlineColorHex else t.outlineColorHex,
                outlineWidth = if (outlineChanged) a.outlineWidth else t.outlineWidth,
                box = box,
            ),
            if (after.sizePx != before.sizePx) after.sizePx else target.sizePx,
        )
    }

    private fun mergeEffects(target: TextEffects?, before: TextEffects?, after: TextEffects?): TextEffects? {
        if (before == after) return target
        val t = target ?: TextEffects()
        val b = before ?: TextEffects()
        val a = after ?: TextEffects()
        val shadowChanged = a.shadowHex != b.shadowHex || a.shadowDx != b.shadowDx || a.shadowDy != b.shadowDy || a.shadowBlur != b.shadowBlur
        val merged = TextEffects(
            shadowHex = if (shadowChanged) a.shadowHex else t.shadowHex,
            shadowDx = if (shadowChanged) a.shadowDx else t.shadowDx,
            shadowDy = if (shadowChanged) a.shadowDy else t.shadowDy,
            shadowBlur = if (shadowChanged) a.shadowBlur else t.shadowBlur,
            gradient = if (a.gradient != b.gradient) a.gradient else t.gradient,
            caps = if (a.caps != b.caps) a.caps else t.caps,
            letterSpacing = if (a.letterSpacing != b.letterSpacing) a.letterSpacing else t.letterSpacing,
        )
        return merged.takeUnless { it.isEmpty }
    }
}
