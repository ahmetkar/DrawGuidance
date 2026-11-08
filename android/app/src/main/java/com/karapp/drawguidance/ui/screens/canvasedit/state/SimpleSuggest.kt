package com.karapp.drawguidance.ui.screens.canvasedit.state

import androidx.compose.ui.geometry.Rect
import com.karapp.drawguidance.content.library.Audience
import com.karapp.drawguidance.content.library.ShapeLibrary
import com.karapp.drawguidance.content.shapes.VectorShapeMatcher
import com.karapp.drawguidance.content.vector.P
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.ui.drawing.ShapeCategory
import com.karapp.drawguidance.ui.drawing.ShapeFit
import com.karapp.drawguidance.ui.drawing.ShapeRegistry
import java.util.concurrent.ConcurrentHashMap

/**
 * v34 basit çizim türlerinde (basit resim, basit not alma, çocuklar için çizim) yapay zekâ önerileri:
 * - Arama türün kapsamında yapılır: çocuklar için kendi modu ve ağı ([com.karapp.drawguidance.content.shapes.ShapeMode.KIDS]:
 *   86 çocuk şekli ve çocuklara uygun genel şablonlar), basit resimde parça/teknik şablonlar aranmaz.
 * - Balonda daha çok seçenek ([maxTemplates]: 8; gelişmiş türlerde 4).
 * - Çizim, türün **kendi kataloğundaki** hazır şekillerle de karşılaştırılır ([vectorFixes]): çocuk modunda çocuk boyama
 *   şekilleri (yuvarlak çizen çocuğa top, güneş, dünya…), basit resim ve notta türün şekilleri (üçgen çizene dik üçgen,
 *   eşkenar üçgen, piramit…). Öneri kabul edilince çizimin yerine o şekil konur.
 */
internal object SimpleSuggest {

    /** Basit türlerde benzerlik eşiği biraz düşük: daha çok (sıralı) öneri, en iyisi yine başta. */
    private const val MIN_CONFIDENCE = 0.42f

    fun isSimple(kind: CanvasKind?): Boolean = kind?.simple == true

    fun maxTemplates(kind: CanvasKind?): Int = if (isSimple(kind)) 8 else 4

    /** Türün kataloğundaki hazır şekiller (ilk kullanımda bir kez hesaplanır). */
    private val matchers = ConcurrentHashMap<CanvasKind, VectorShapeMatcher>()

    fun catalogIds(kind: CanvasKind): List<String> = when (kind) {
        CanvasKind.KIDS -> ShapeLibrary.forAudience(Audience.KIDS).flatMap { c -> c.shapes.map { ShapeLibrary.PREFIX + it.id } }
        // v42 basit resim: seçicideki temel ve genel şekiller (oklar, yıldızlar, afişler, konuşma kutuları, semboller)
        CanvasKind.SIMPLE_ART -> ShapeRegistry.categories(kind).flatMap { it.items }.distinct()
        else -> ShapeCategory.forKind(kind).flatMap { c -> c.shapes.map { it.name } }
    }

    fun matcher(kind: CanvasKind): VectorShapeMatcher = matchers.getOrPut(kind) {
        VectorShapeMatcher(catalogIds(kind).mapNotNull { id ->
            val aspect = if (ShapeRegistry.isLibrary(id)) ShapeRegistry.aspect(id) else 1f
            val frame = if (aspect >= 1f) Rect(0f, 0f, 100f, 100f / aspect) else Rect(0f, 0f, 100f * aspect, 100f)
            val outline = ShapeSuggester.outline(id, frame)
            if (outline.size < 1) null
            else VectorShapeMatcher.Item(id, outline.map { s -> s.map { P(it.x, it.y) } }, stretch = !ShapeRegistry.isLibrary(id) && ShapeRegistry.fit(id) == ShapeFit.BOX)
        })
    }

    /** [ink] çizgilerine benzeyen, türün kataloğundaki şekiller (en çok 4). */
    fun vectorFixes(kind: CanvasKind, ink: List<Stroke>, limit: Int = 4): List<TemplateFix> {
        if (!isSimple(kind)) return emptyList()
        val flat = ink.filter { it.space == null && TemplateSuggester.planeOf(it) == com.karapp.drawguidance.data.model.Plane3D.FREE }
        if (flat.isEmpty()) return emptyList()
        return ShapeSuggester.vectorFixes(flat, matcher(kind), limit, MIN_CONFIDENCE)
    }

    /** Şablonlar ve hazır şekiller tek listede: güvene göre, aynı öneri bir kez, en çok [max]. */
    fun merge(templates: List<TemplateFix>, vectors: List<TemplateFix>, max: Int): List<TemplateFix> =
        (templates + vectors).sortedByDescending { it.confidence }.distinctBy { it.key + "|" + it.solid }.take(max)
}
