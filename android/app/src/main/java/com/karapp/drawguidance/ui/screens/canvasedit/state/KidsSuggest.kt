package com.karapp.drawguidance.ui.screens.canvasedit.state

import androidx.annotation.StringRes
import com.karapp.drawguidance.R
import com.karapp.drawguidance.ui.components.MascotFace
import com.karapp.drawguidance.ui.components.MascotGesture
import com.karapp.drawguidance.ui.components.MascotLook
import kotlin.random.Random

/**
 * v37 çocuk modunda maskotun söylediği önerilerin konuları. v38 üç ayrı tür: şekil ([TEMPLATE], birbirine yakın 4+
 * eleman), yazı ([TEXT], 2+ eleman) ve çizgi ([LINE], 2+ çizgi); tek çizgiye öneri yok.
 */
enum class KidsTopic { TEMPLATE, TEXT, LINE }

/**
 * Maskotun bir cümle kalıbı: metin kaynağı (ad için `%1$s`, sayı için `%1$d`/`%2$d`) ve o cümlede bürüneceği yüz
 * ifadesi ile el hareketi.
 */
class KidsLine(@StringRes val text: Int, val look: MascotLook)

private fun line(@StringRes text: Int, face: MascotFace, gesture: MascotGesture) = KidsLine(text, MascotLook(face, gesture))

/**
 * v37 maskotun cümleleri: her öneri türü için birkaç kalıp (biri rastgele ama öneriye göre hep aynı seçilir: aynı
 * öneri yeniden çizildiğinde cümle değişmez, testte tohumla sınanır), selamlama, ipuçları ve övgüler.
 */
object KidsLines {
    val suggestions: Map<KidsTopic, List<KidsLine>> = mapOf(
        KidsTopic.TEMPLATE to listOf(
            line(R.string.kids_say_template_1, MascotFace.EXCITED, MascotGesture.THUMBS_UP),
            line(R.string.kids_say_template_2, MascotFace.SURPRISED, MascotGesture.CHEER),
            line(R.string.kids_say_template_3, MascotFace.THINKING, MascotGesture.THINK),
        ),
        KidsTopic.TEXT to listOf(
            line(R.string.kids_say_text_1, MascotFace.WINKING, MascotGesture.POINT),
            line(R.string.kids_say_text_2, MascotFace.HAPPY, MascotGesture.BRUSH),
        ),
        KidsTopic.LINE to listOf(
            line(R.string.kids_say_line_1, MascotFace.EXCITED, MascotGesture.CHEER),
            line(R.string.kids_say_line_2, MascotFace.EXPLAINING, MascotGesture.BRUSH),
        ),
    )

    val greeting = line(R.string.kids_mascot_hello, MascotFace.HAPPY, MascotGesture.WAVE)

    val tips: List<KidsLine> = listOf(
        line(R.string.kids_tip_1, MascotFace.WINKING, MascotGesture.THUMBS_UP),
        line(R.string.kids_tip_2, MascotFace.EXCITED, MascotGesture.POINT),
        line(R.string.kids_tip_3, MascotFace.THINKING, MascotGesture.THINK),
        line(R.string.kids_tip_4, MascotFace.EXPLAINING, MascotGesture.BRUSH),
        line(R.string.kids_tip_5, MascotFace.SURPRISED, MascotGesture.CHEER),
    )

    val praise: List<KidsLine> = listOf(
        line(R.string.kids_praise_1, MascotFace.PROUD, MascotGesture.CHEER),
        line(R.string.kids_praise_2, MascotFace.EXCITED, MascotGesture.THUMBS_UP),
        line(R.string.kids_praise_3, MascotFace.WINKING, MascotGesture.THUMBS_UP),
    )

    /** [list]ten [seed] tohumuyla bir kalıp (aynı tohum hep aynı kalıbı verir). */
    fun pick(list: List<KidsLine>, seed: Long): KidsLine = list[Random(seed).nextInt(list.size)]

    fun pick(topic: KidsTopic, seed: Long): KidsLine = pick(suggestions.getValue(topic), seed)
}

/**
 * v37 çocuk modunda öneri balonu yerine maskot konuşur: her öneriden tek seçenek (büyüklerin balonunun ilk seçeneği)
 * sunulur, ✓ ile uygulanır. v38 aynı anda birden çok öneri varsa önce şekil, sonra yazı, sonra çizgi önerisi söylenir
 * (biri uygulanınca ya da kapatılınca sıradaki).
 */
object KidsSuggest {
    /** Önerinin tohumu: cümle kalıbı öneriye göre sabit kalır. */
    fun seed(s: ShapeSuggestion): Long =
        (s.strokeIds.sorted().joinToString() + "|" + s.templates.firstOrNull()?.key).hashCode().toLong()

    fun seed(s: TextSuggestion): Long = s.strokeIds.sorted().joinToString().hashCode().toLong()

    fun seed(s: LineSuggestion): Long = ("line|" + s.strokeIds.sorted().joinToString()).hashCode().toLong()
}
