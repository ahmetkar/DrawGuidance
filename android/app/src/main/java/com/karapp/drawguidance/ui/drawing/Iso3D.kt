package com.karapp.drawguidance.ui.drawing

import androidx.compose.ui.geometry.Offset
import com.karapp.drawguidance.data.model.Depth3D
import com.karapp.drawguidance.data.model.Plane3D
import kotlin.math.sqrt

/**
 * v21 3B çizim modunun geometrisi (izometrik görünüm, 30°): ekranda X ekseni sağ-aşağı (cos30, sin30), Z ekseni
 * sol-aşağı (−cos30, sin30), Y ekseni yukarı. Bir düzlemde çizilen şekil, düzlemin iki ekseni boyunca eğilir:
 * - Ön (X-Y): yatay hareket X eksenine, dikey hareket dikeye gider (sağa bakan dikey yüz).
 * - Yan (Z-Y): yatay hareket −Z eksenine (sağ-yukarı), dikey hareket dikeye gider (sola bakan dikey yüz).
 * - Üst (X-Z): yatay hareket X eksenine, dikey hareket Z eksenine gider (yatay zemin; daire elips olur).
 * İzdüşüm çizginin ilk noktası etrafındadır (çizgi parmağın değdiği yerden başlar). Derinlik (kabarma) düzlemin
 * normali yönündedir: ön yüzde Z, yan yüzde X, zeminde aşağı (Y).
 */
object Iso3D {
    private const val C = 0.8660254f
    private const val S = 0.5f

    /** Düzlemin 2×2 matrisi: (a, b, c, d) → x' = a·x + c·y, y' = b·x + d·y. */
    fun matrix(plane: Plane3D): FloatArray = when (plane) {
        Plane3D.FREE -> floatArrayOf(1f, 0f, 0f, 1f)
        Plane3D.FRONT -> floatArrayOf(C, S, 0f, 1f)
        Plane3D.SIDE -> floatArrayOf(C, -S, 0f, 1f)
        Plane3D.TOP -> floatArrayOf(C, S, -C, S)
    }

    /** [p] noktası [anchor] etrafında düzleme yatırılır. */
    fun project(plane: Plane3D, anchor: Offset, x: Float, y: Float): Offset {
        if (plane == Plane3D.FREE) return Offset(x, y)
        val m = matrix(plane)
        val dx = x - anchor.x; val dy = y - anchor.y
        return Offset(anchor.x + m[0] * dx + m[2] * dy, anchor.y + m[1] * dx + m[3] * dy)
    }

    /** Düzlemdeki çizimi düz (önden bakış) hâle getirir (öneride şablonla karşılaştırmak için). */
    fun unproject(plane: Plane3D, anchor: Offset, x: Float, y: Float): Offset {
        if (plane == Plane3D.FREE) return Offset(x, y)
        val m = matrix(plane)
        val det = m[0] * m[3] - m[2] * m[1]
        val dx = x - anchor.x; val dy = y - anchor.y
        return Offset(anchor.x + (m[3] * dx - m[2] * dy) / det, anchor.y + (-m[1] * dx + m[0] * dy) / det)
    }

    /** Kabarmanın ekrandaki birim yönü (ışık sol üstten; katı gövde ışığın tersine uzar). */
    fun extrudeDirection(plane: Plane3D): Offset = when (plane) {
        Plane3D.FREE -> Offset(0.7071f, 0.7071f)
        Plane3D.FRONT -> Offset(-C, S)
        Plane3D.SIDE -> Offset(C, S)
        Plane3D.TOP -> Offset(0f, 1f)
    }

    /**
     * v22 kabarmanın ekrandaki vektörü (birim uzunluk başına): 3B sahnede kameranın o anki görünümüne göre saklanan
     * ([Depth3D.ex], [Depth3D.ey]); yoksa v21 izometrik yönü.
     */
    fun extrudeVector(depth: Depth3D): Offset {
        val x = depth.ex; val y = depth.ey
        return if (x != null && y != null) Offset(x, y) else extrudeDirection(depth.plane)
    }

    /** Kabarmanın ekranda ulaştığı en uzak mesafe (sınır kutusu payı). */
    fun extrudeReach(depth: Depth3D?, inkWidth: Float): Float {
        val len = extrudeLength(depth, inkWidth)
        if (len <= 0f || depth == null) return 0f
        return len * kotlin.math.max(1f, length(extrudeVector(depth)))
    }

    /** Kabarmanın uzunluğu (tuval birimi): derinlik × (10 + 1.2 × iz genişliği), en çok 160. */
    fun extrudeLength(depth: Depth3D?, inkWidth: Float): Float =
        if (depth == null || depth.depth <= 0.01f) 0f else (depth.depth.coerceIn(0f, 1f) * (10f + 1.2f * inkWidth)).coerceAtMost(160f)

    /** İzometrik ızgara çizgilerinin yönleri (kılavuz için): X, Z ve dikey. */
    val gridDirections: List<Offset> = listOf(Offset(C, S), Offset(-C, S), Offset(0f, 1f))

    fun length(o: Offset): Float = sqrt(o.x * o.x + o.y * o.y)
}
