package com.karapp.drawguidance.ui.screens.canvasedit.tutorial

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.karapp.drawguidance.R
import com.karapp.drawguidance.ui.components.MascotButtonKind
import com.karapp.drawguidance.ui.components.MascotRoundButton
import com.karapp.drawguidance.ui.components.MascotSpeaker

/**
 * v37 çocuk eğitiminde açıklama kutusunun yerine maskot: adımın açıklaması başının üstündeki balonda harf harf yazılır
 * (yazarken ağzı oynar); balonda ok simgeli Tamam (sonraki adım; son adımda eğitimi bitirir) ve çarpı simgeli
 * Sonlandır (eğitimi iptal eder) düğmeleri. Her adımda maskot başka bir yüz ifadesine ve el hareketine bürünür.
 */
@Composable
internal fun KidsTutorialMascot(ui: TutorialUi, onNext: () -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val title = stringResource(R.string.tut_step_of, ui.index + 1, ui.count) + " · " + stringResource(ui.step.title)
    MascotSpeaker(
        look = TutorialKids.lookOf(ui.step),
        text = stringResource(ui.step.body),
        title = title,
        mascotDescription = stringResource(R.string.kids_mascot_cd),
        modifier = modifier.testTag("kids_tutorial"),
    ) {
        MascotRoundButton(Icons.Filled.Close, stringResource(R.string.kids_mascot_end), "mascot_close", MascotButtonKind.CLOSE, onCancel)
        MascotRoundButton(
            Icons.AutoMirrored.Filled.ArrowForward, stringResource(if (ui.last) R.string.tut_finish else R.string.kids_mascot_next),
            "mascot_next", MascotButtonKind.PRIMARY, onNext,
        )
    }
}
