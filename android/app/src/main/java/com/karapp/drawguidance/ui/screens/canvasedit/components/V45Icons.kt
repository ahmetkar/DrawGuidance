package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** v45 uygulamada başka yerde kullanılmayan (benzersiz, çizilerek üretilen) simgeler. */
object V45Icons {
    /**
     * Panel ekle (not alma): kıvrık köşeli bir not kâğıdı; içinde yuvarlak köşeli üç alan (solda uzun sütun, sağda
     * üst üste iki kutu) ve sağ altta küçük artı. Hazır Material simgelerinin hiçbirine benzemez.
     */
    val NotesPanels: ImageVector by lazy {
        ImageVector.Builder("NotesPanels", 24.dp, 24.dp, 24f, 24f).apply {
            val ink = SolidColor(Color.Black)
            // Kâğıt: sağ üst köşesi kıvrık
            path(fill = null, stroke = ink, strokeLineWidth = 1.6f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(4f, 3f)
                lineTo(16f, 3f)
                lineTo(20f, 7f)
                lineTo(20f, 13.5f)
                moveTo(13.5f, 21f)
                lineTo(4f, 21f)
                lineTo(4f, 3f)
                moveTo(16f, 3f)
                lineTo(16f, 7f)
                lineTo(20f, 7f)
            }
            // Sol uzun alan (yuvarlak köşeli, dolu)
            path(fill = ink) {
                moveTo(7f, 6f)
                lineTo(10f, 6f)
                quadTo(11f, 6f, 11f, 7f)
                lineTo(11f, 17f)
                quadTo(11f, 18f, 10f, 18f)
                lineTo(7f, 18f)
                quadTo(6f, 18f, 6f, 17f)
                lineTo(6f, 7f)
                quadTo(6f, 6f, 7f, 6f)
                close()
            }
            // Sağ üst alan
            path(fill = ink) {
                moveTo(13f, 9f)
                lineTo(17f, 9f)
                quadTo(18f, 9f, 18f, 10f)
                lineTo(18f, 11.2f)
                quadTo(18f, 12.2f, 17f, 12.2f)
                lineTo(13f, 12.2f)
                quadTo(12f, 12.2f, 12f, 11.2f)
                lineTo(12f, 10f)
                quadTo(12f, 9f, 13f, 9f)
                close()
            }
            // Sağ alt alan (çerçeve; artıya yer açar)
            path(fill = null, stroke = ink, strokeLineWidth = 1.2f, strokeLineJoin = StrokeJoin.Round) {
                moveTo(12.6f, 13.8f)
                lineTo(15.5f, 13.8f)
                moveTo(12.6f, 13.8f)
                lineTo(12.6f, 17.4f)
                lineTo(13.5f, 17.4f)
            }
            // Artı (ekle)
            path(fill = null, stroke = ink, strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round) {
                moveTo(18.5f, 15f)
                lineTo(18.5f, 22f)
                moveTo(15f, 18.5f)
                lineTo(22f, 18.5f)
            }
        }.build()
    }
}
