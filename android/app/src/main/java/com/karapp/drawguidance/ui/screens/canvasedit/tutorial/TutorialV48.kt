package com.karapp.drawguidance.ui.screens.canvasedit.tutorial

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NewReleases
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.ui.screens.canvasedit.EditMode

/*
 * v47–v49 eğitimlerin yenilikleri: değişen menüleri tanıtır.
 * - Gelişmiş modlar: mod menüsü Seç / Çiz / Ekle / Düzenle; "Fırça ayarları" artık "Özellikler" (seçili aracın
 *   ayarları); Taşı ve Genişlet ayrı iki düğme (Taşı eleman seçiliyken, Genişlet tuval seçiliyken).
 * - Basit modlar: dikey ekranda üç menü (Ekle / Çiz / Seç ve düzenle); her menü tek satır, sığmazsa yatay kaydırılır.
 * - Çocuk modu: araçların sırası değişti (Kalem, Silgi, Boya, Şekiller, Görev, Kalıp, Yazı); 3B kaldırıldı.
 *
 * Adımlar yalnız değişen düğmeyi vurgular (gösterim yok): kısa ve her ekran boyutunda güvenli.
 */
internal object TutorialV48 {

    private fun step(anchor: String, title: Int, body: Int, mode: EditMode? = null) =
        TutorialStep(anchor, title, body, mode = mode)

    /** Gelişmiş modlar (resim, not alma, çizgi roman): yeni mod menüsü, Özellikler, Taşı/Genişlet. */
    fun advanced(kind: CanvasKind): TutorialGroup = TutorialGroup(
        id = "v48_advanced_${kind.name.lowercase()}", kind = kind,
        title = R.string.v48_tut_title, summary = R.string.v48_tut_summary,
        icon = Icons.Filled.NewReleases, ai = false,
        steps = listOf(
            step(TutorialAnchor.mode(EditMode.SELECT), R.string.v48_tut_modes_t, R.string.v48_tut_modes_b, mode = EditMode.SELECT),
            step(TutorialAnchor.BRUSH_SETTINGS, R.string.v48_tut_props_t, R.string.v48_tut_props_b, mode = EditMode.DRAW),
            step(TutorialAnchor.mode(EditMode.EDIT_PICK), R.string.v48_tut_move_t, R.string.v48_tut_move_b, mode = EditMode.EDIT_PICK),
        ),
    )

    /** Basit resim ve basit not alma: üç menü ve Özellikler. */
    fun simple(kind: CanvasKind): TutorialGroup = TutorialGroup(
        id = "v48_simple_${kind.name.lowercase()}", kind = kind,
        title = R.string.v48_tut_title, summary = R.string.v48_tut_summary,
        icon = Icons.Filled.NewReleases, ai = false,
        steps = listOf(
            step(TutorialAnchor.simple("shape"), R.string.v48_tut_three_t, R.string.v48_tut_three_b),
            step(TutorialAnchor.simple("brush_settings"), R.string.v48_tut_props_t, R.string.v48_tut_props_b),
        ),
    )

    /** Çocuk modu: yeni sıra ve 3B'nin kaldırılması. */
    val kids: TutorialGroup by lazy {
        TutorialGroup(
            id = "v48_kids", kind = CanvasKind.KIDS,
            title = R.string.v48_tut_title, summary = R.string.v48_tut_summary,
            icon = Icons.Filled.NewReleases, ai = false,
            steps = listOf(
                step(TutorialAnchor.simple("shape"), R.string.v48_tut_kids_t, R.string.v48_tut_kids_b),
                step(TutorialAnchor.simple("challenge"), R.string.v48_tut_kids2_t, R.string.v48_tut_kids2_b),
            ),
        )
    }
}
