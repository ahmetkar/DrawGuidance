package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.settings.KidsPrefs
import com.karapp.drawguidance.i18n.I18n
import com.karapp.drawguidance.ui.components.MascotButtonKind
import com.karapp.drawguidance.ui.components.MascotFigure
import com.karapp.drawguidance.ui.components.MascotLook
import com.karapp.drawguidance.ui.components.MascotRoundButton
import com.karapp.drawguidance.ui.components.MascotSpeaker
import com.karapp.drawguidance.ui.screens.canvasedit.CanvasEditViewModel
import com.karapp.drawguidance.ui.screens.canvasedit.EditMode
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ViewportState
import com.karapp.drawguidance.ui.screens.canvasedit.state.KidsLine
import com.karapp.drawguidance.ui.screens.canvasedit.state.KidsLines
import com.karapp.drawguidance.ui.screens.canvasedit.state.KidsSuggest
import com.karapp.drawguidance.ui.screens.canvasedit.state.KidsTopic
import com.karapp.drawguidance.diagnostics.AppErrors
import org.koin.compose.koinInject

/** v37 çocuk boyamasının tuval üstü katmanı: kova imleci, şekil vurgusu, hazır alan ve 2 sn halkası. */
@Composable
fun KidsPaintOverlay(viewModel: CanvasEditViewModel, viewport: ViewportState) {
    val cursor by viewModel.paintCursor.collectAsState()
    val canvas by viewModel.canvas.collectAsState()
    val region by viewModel.regionColor.collectAsState()
    val c = canvas ?: return
    // v45 boya fırçasının süren darbesi (alanın içinde kırpılmış önizleme)
    KidsBrushLayer(viewModel, viewport)
    if (cursor == null) return
    KidsPaintLayer(cursor, c, viewport, region?.takeIf { it.isNotEmpty() } ?: viewModel.regionInk())
}

/** Maskotun söyleyeceği öneri: cümle, ✓ ile uygulanacak iş ve ✕ ile reddetme. */
private class KidsSaid(val line: KidsLine, val text: String, val apply: () -> Unit, val dismiss: () -> Unit)

/** Mod çubuğunun göründüğü modlar dışındakiler (bkz. CanvasEditScreen: alttaki çubuğun görünürlüğü). */
private val NO_BAR = setOf(EditMode.DRAW, EditMode.GUIDE, EditMode.PERSPECTIVE, EditMode.ADD, EditMode.EDIT_PICK)

/**
 * v37 çocuk tuvalinin maskotu (Boyacık), sağ alt köşede (alttaki mod çubuğunun üstünde):
 * - yapay zekâ önerisi varsa (v38 şekil > yazı > çizgi) büyüklerin öneri balonu yerine maskot konuşur: öneri türüne göre
 *   birkaç kalıptan biri (öneriye göre hep aynı) ve o kalıbın yüz ifadesi; tik simgeli Uygula ve çarpı simgeli Kapat
 *   (öneriyi reddeder, maskotu ve balonu kapatır),
 * - öneri yokken maskot açıksa köşede durur (dokununca ipucu söyler; balonda ok simgeli Tamam ve çarpı simgeli Kapat),
 * - kapalıysa küçük yüz düğmesi: dokununca maskot açılır ve selam verir. Açık/kapalı tercihi hatırlanır.
 * Eğitim sürerken gizlenir (eğitimin kendi maskotu var).
 */
@Composable
fun KidsMascotLayer(viewModel: CanvasEditViewModel, mode: EditMode, barPx: Int, tutorial: Boolean) {
    if (tutorial) return
    val prefs = koinInject<KidsPrefs>()
    val mascotOn by prefs.mascotOn.collectAsState()
    val textSuggestion by viewModel.textSuggestion.collectAsState()
    val shapeSuggestion by viewModel.shapeSuggestion.collectAsState()
    val lineSuggestion by viewModel.lineSuggestion.collectAsState()
    val selected by viewModel.selectedIds.collectAsState()
    // v38 çizim görevi sürerken köşede görevin maskotu durur (öneriler yine burada söylenir)
    val challengeOn by viewModel.challenge.active.collectAsState()
    // v40 maskot çizim görevini öneriyorken (görevin katmanında) köşe ona bırakılır
    val challengeSuggest by viewModel.challenge.suggest.collectAsState()
    var talk by remember { mutableStateOf<KidsLine?>(null) }
    var tips by remember { mutableIntStateOf(0) }
    var praises by remember { mutableIntStateOf(0) }
    val locale = androidx.core.os.ConfigurationCompat.getLocales(androidx.compose.ui.platform.LocalConfiguration.current)[0] ?: java.util.Locale.getDefault()

    fun praise() {
        if (mascotOn) talk = KidsLines.praise[praises++ % KidsLines.praise.size]
    }

    // v38 söylenecek öneri (önce şekil, sonra yazı, sonra çizgi önerisi); her biri tek düğmeyle dikkate alınan bütün çizgileri düzeltir
    val said: KidsSaid? = when {
        shapeSuggestion?.templates?.isNotEmpty() == true -> shapeSuggestion?.let { s ->
            val line = KidsLines.pick(KidsTopic.TEMPLATE, KidsSuggest.seed(s))
            val fix = s.templates[0]
            val name = (if (fix.vector) shapeTitle(fix.solid ?: "") else I18n.tr(fix.name)).lowercase(locale)
            KidsSaid(line, stringResource(line.text, name), { viewModel.acceptTemplate(0); praise() }, viewModel::dismissShapeSuggestion)
        }
        textSuggestion != null -> textSuggestion?.let { s ->
            val line = KidsLines.pick(KidsTopic.TEXT, KidsSuggest.seed(s))
            KidsSaid(line, stringResource(line.text, s.text), { viewModel.acceptHandwriting(s.text); praise() }, viewModel::dismissTextSuggestion)
        }
        else -> lineSuggestion?.let { s ->
            val line = KidsLines.pick(KidsTopic.LINE, KidsSuggest.seed(s))
            KidsSaid(line, stringResource(line.text, s.strokeIds.size), { viewModel.acceptLines(); praise() }, viewModel::dismissLineSuggestion)
        }
    }

    val bottom = with(LocalDensity.current) {
        val bar = mode !in NO_BAR && !(mode == EditMode.SELECT && selected.isEmpty())
        (if (bar) barPx.toDp() else 16.dp) + 4.dp
    }
    Box(Modifier.fillMaxSize().testTag("kids_mascot_layer"), contentAlignment = Alignment.BottomEnd) {
        val place = Modifier.navigationBarsPadding().padding(end = 10.dp, bottom = bottom)
        val title = stringResource(R.string.kids_mascot_name)
        val mascotCd = stringResource(R.string.kids_mascot_cd)
        when {
            said != null -> MascotSpeaker(
                look = said.line.look, text = said.text, title = title, mascotDescription = mascotCd,
                modifier = place.testTag("kids_mascot_suggest"),
            ) {
                MascotRoundButton(Icons.Filled.Close, stringResource(R.string.kids_mascot_close), "mascot_close", MascotButtonKind.CLOSE, {
                    // Öneri reddedilir; maskot ve balonu kapanır (sonraki öneride yeniden gelir)
                    AppErrors.guard("kids.mascot", Unit) { said.dismiss() }
                    talk = null
                    prefs.setMascotOn(false)
                })
                MascotRoundButton(Icons.Filled.Check, stringResource(R.string.kids_mascot_apply), "mascot_apply", MascotButtonKind.PRIMARY, {
                    AppErrors.guard("kids.mascot", Unit) { said.apply() }
                })
            }
            challengeOn || challengeSuggest -> Unit
            mascotOn -> {
                val line = talk
                MascotSpeaker(
                    look = line?.look ?: MascotLook.Idle, text = line?.let { stringResource(it.text) }, title = title,
                    mascotDescription = mascotCd, modifier = place,
                    // Maskota dokununca sıradaki ipucu
                    onMascot = { talk = KidsLines.tips[tips++ % KidsLines.tips.size] },
                ) {
                    MascotRoundButton(Icons.Filled.Close, stringResource(R.string.kids_mascot_close), "mascot_close", MascotButtonKind.CLOSE, {
                        talk = null
                        prefs.setMascotOn(false)
                    })
                    MascotRoundButton(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.kids_mascot_next), "mascot_next", MascotButtonKind.PRIMARY, {
                        talk = null
                    })
                }
            }
            else -> MascotToggle(place) {
                prefs.setMascotOn(true)
                talk = KidsLines.greeting
            }
        }
    }
}

/** Maskot kapalıyken köşedeki küçük yüz düğmesi (dokununca maskot açılır). */
@Composable
private fun MascotToggle(modifier: Modifier, onClick: () -> Unit) {
    val description = stringResource(R.string.kids_mascot_open)
    Box(
        modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(Color(0xFFFFF4D6), Color(0xFFFFD98A))))
            .border(2.5.dp, Brush.linearGradient(listOf(Color(0xFFFFB938), Color(0xFFA78BFA))), CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .testTag("kids_mascot_toggle"),
        contentAlignment = Alignment.Center,
    ) {
        MascotFigure(MascotLook.Idle, Modifier.size(52.dp), faceOnly = true)
    }
}
