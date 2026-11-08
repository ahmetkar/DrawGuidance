package com.karapp.drawguidance.ui.drawing

import com.karapp.drawguidance.data.model.Stroke

/**
 * v18: grafikler kağıt rengine uyar (formül kutusu, nokta içleri, pasta dilimi ayraçları kağıt renginde; ızgara ve
 * yardımcı çizgiler kağıttan biraz koyu, koyu kağıtta biraz açık). Renk grafiğin kaydına ([GraphSpec.paper]) yazılır;
 * kağıt rengi değişince ya da tuval açılınca uymayan grafikler yeniden kurulur.
 */
object GraphPaper {

    /** Kayıtta tutulan biçim: beyaz kağıt boş yazılır (eski kayıtlarla aynı). */
    fun normalize(hex: String?): String {
        val h = hex?.trim()?.uppercase().orEmpty()
        return if (h.isEmpty() || h == "#FFFFFF" || !Regex("^#[0-9A-F]{6}$").matches(h)) "" else h
    }

    /** Grafik şekli ve kağıdı [paper]'dan farklı mı? */
    fun needsUpdate(stroke: Stroke, paper: String): Boolean {
        val spec = stroke.shape ?: return false
        val graph = spec.graph ?: return false
        if (!ShapeRegistry.isGraph(ShapeRegistry.upgradeGraphKind(spec.kind))) return false
        return normalize(graph.paper) != normalize(paper)
    }

    /** Tek bir grafiği [paper] kağıdına göre yeniden kurar (grafik değilse ya da zaten uyuyorsa kendisi). */
    fun recolor(stroke: Stroke, paper: String): Stroke {
        if (!needsUpdate(stroke, paper)) return stroke
        val spec = stroke.shape ?: return stroke
        val graph = spec.graph ?: return stroke
        val rebuilt = runCatching { ShapeFactory.rebuild(stroke, spec.style, graph.copy(paper = normalize(paper))) }.getOrNull() ?: return stroke
        return rebuilt.copy(order = stroke.order)
    }

    /** Tüm sayfalardaki grafikleri [paper]'a uydurur; değişen yoksa aynı liste döner. */
    fun recolor(pages: List<List<Stroke>>, paper: String): List<List<Stroke>> {
        if (pages.none { page -> page.any { needsUpdate(it, paper) } }) return pages
        return pages.map { page -> if (page.none { needsUpdate(it, paper) }) page else page.map { recolor(it, paper) } }
    }
}
