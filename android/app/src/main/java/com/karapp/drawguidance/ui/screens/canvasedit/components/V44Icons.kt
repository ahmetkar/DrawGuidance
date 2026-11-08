package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pentagon
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Tonality
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * v44 uygulamada başka yerde kullanılmayan (benzersiz) simgeler: dikdörtgen seçim, serbest seçim (kement),
 * arka planla uyumla, mekâna yerleştir ve şekil kalıbı.
 */
object V44Icons {
    /** Dikdörtgen seçim: kesik çizgili seçim kutusu. */
    val RectSelect: ImageVector get() = Icons.Filled.SelectAll

    /** Arka planla uyumla: yarısı dolu ton dairesi (nesne ile arka planın tonu birleşir). */
    val Harmonize: ImageVector get() = Icons.Filled.Tonality

    /** Mekâna yerleştir: dağlar ve zemin (ufuk). */
    val PlaceInScene: ImageVector get() = Icons.Filled.Terrain

    /** Şekil kalıbı: beşgen kalıp. */
    val Template: ImageVector get() = Icons.Filled.Pentagon

    /** Serbest seçim: kement (kesik kapalı ilmek ve sapı). Çizilerek üretilir; başka hazır simgeyle karışmaz. */
    val FreeSelect: ImageVector by lazy {
        ImageVector.Builder("FreeSelect", 24.dp, 24.dp, 24f, 24f).apply {
            // İlmek
            path(
                fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 3.5f)
                curveTo(17.5f, 3.5f, 21f, 6f, 21f, 9.2f)
                curveTo(21f, 12.4f, 17.5f, 14.8f, 12.5f, 14.8f)
                curveTo(7.5f, 14.8f, 3f, 12.6f, 3f, 9.2f)
                curveTo(3f, 6f, 6.5f, 3.5f, 12f, 3.5f)
            }
            // Sap (ilmekten aşağı kıvrılan ip)
            path(
                fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(7.5f, 14.2f)
                curveTo(6.5f, 16.5f, 8.5f, 17.5f, 8f, 19.5f)
                curveTo(7.7f, 20.7f, 6.5f, 21.2f, 5.5f, 20.6f)
            }
            // Düğüm
            path(fill = SolidColor(Color.Black)) {
                moveTo(7.6f, 13.2f)
                lineTo(9.4f, 14.4f)
                lineTo(8.2f, 16.1f)
                lineTo(6.6f, 14.9f)
                close()
            }
        }.build()
    }
}
