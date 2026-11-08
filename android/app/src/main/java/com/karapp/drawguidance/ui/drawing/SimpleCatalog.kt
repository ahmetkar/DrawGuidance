package com.karapp.drawguidance.ui.drawing

import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.DiagramKind

/**
 * v33 basit çizimin sınırlı katalogları: yalnız en gerekli 10'ar diyagram, formül ve 3B şekil (şekiller
 * [ShapeCategory.forKind]: v34'te basit resimde 42 şekil; çocuklarda çocuk şekilleri; fırçalarda 3 kategori:
 * [BrushCategory.forKind]). Gelişmiş türlerde null: bütün katalog. v45 basit not alma gelişmiş not almanın bütün
 * şekil, grafik, diyagram, formül ve 3B şekil kataloğunu kullanır (null).
 */
object SimpleCatalog {
    /** Ders notunda en sık kullanılan 10 diyagram. */
    val diagrams: List<DiagramKind> = listOf(
        DiagramKind.FLOW, DiagramKind.PROCESS, DiagramKind.CYCLE, DiagramKind.MIND_MAP, DiagramKind.TIMELINE,
        DiagramKind.VENN, DiagramKind.PYRAMID, DiagramKind.TREE, DiagramKind.COMPARISON, DiagramKind.STEPS,
    )

    /** Okulda en sık kullanılan 10 formül (geometri, cebir, fizik). */
    val formulas: List<String> = listOf(
        "geometry.pythagoras", "geometry.circle_area", "geometry.circumference", "geometry.triangle_area",
        "geometry.rectangle_area", "algebra.quadratic", "algebra.square_of_sum", "algebra.ratio",
        "physics.average_speed", "physics.newton2",
    )

    /** Basit resim ve çocuk çiziminde 10 hazır 3B şekil. */
    private val artModels: List<String> = listOf(
        "geo.cuboid", "geo.sphere", "geo.cylinder", "geo.cone", "geo.pyramid",
        "geo.torus", "draw.house", "draw.tree", "draw.car", "draw.apple",
    )

    /** Basit not almada 10 hazır 3B şekil (geometri cisimleri, eksen, atom). */
    private val noteModels: List<String> = listOf(
        "geo.cuboid", "geo.pyramid", "geo.cylinder", "geo.cone", "geo.sphere",
        "geo.hemisphere", "geo.prism3", "geo.prism6", "geo.axes", "phy.atom",
    )

    /** v45 basit not almada diyagram kataloğunun tamamı (gelişmiş not almadaki gibi: null = süzgeç yok). */
    fun diagrams(kind: CanvasKind): List<DiagramKind>? = if (kind.simple && kind != CanvasKind.SIMPLE_NOTES) diagrams else null

    /**
     * v35 basit not almada formül kataloğunun tamamı kategorileriyle (gelişmiş not almadaki gibi: null = süzgeç yok);
     * öteki basit türlerde formül aracı yok, liste eski 10 temel formül.
     */
    fun formulas(kind: CanvasKind): List<String>? = if (kind.simple && kind != CanvasKind.SIMPLE_NOTES) formulas else null

    /** v45 basit not almada 3B şekillerin tamamı (gelişmiş not almadaki gibi); öteki basit türlerde 10 hazır 3B şekil. */
    fun models3d(kind: CanvasKind): List<String>? = when {
        !kind.simple -> null
        kind.isNotes -> null
        else -> artModels
    }

    /** v33–v44 basit not almadaki 10 hazır 3B şekil (v45'ten beri basit notta bütün katalog; testler için). */
    val legacyNoteModels: List<String> get() = noteModels
}
