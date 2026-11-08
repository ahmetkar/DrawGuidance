package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ScatterPlot
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.content.graph.GraphFormula
import com.karapp.drawguidance.content.graph.GraphParams
import com.karapp.drawguidance.content.graph.Graphs
import com.karapp.drawguidance.content.library.science.MolView
import com.karapp.drawguidance.content.library.science.ParamError
import com.karapp.drawguidance.content.library.science.ParamKind
import com.karapp.drawguidance.content.library.science.ParamModel
import com.karapp.drawguidance.content.library.science.chem.ChemError
import com.karapp.drawguidance.content.math.MathError
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.ui.components.NeuButton
import com.karapp.drawguidance.ui.components.NeuChip
import com.karapp.drawguidance.ui.components.NeuIconButton
import com.karapp.drawguidance.ui.theme.NeuPillShape
import com.karapp.drawguidance.ui.components.NeuSurface
import com.karapp.drawguidance.ui.components.TextInputSheet
import com.karapp.drawguidance.ui.screens.canvasedit.CanvasEditViewModel
import com.karapp.drawguidance.ui.screens.canvasedit.ShapeTool
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ViewportState
import com.karapp.drawguidance.ui.theme.NeuTheme

/*
 * v45 parametreli şekillerin ve grafik formülünün düzenleme arayüzü (CanvasEditScreen'in mod çubuğu üstündeki ek
 * alan): değer adımlayıcıları, formül alanı (hatada kırmızı ileti; şekil son geçerli hâlinde kalır), molekül
 * görünümü ve "Vektör düzenle". Şekil üstündeki tutamaklar [ParamHandlesLayer] ile çizilir.
 */

/** Matematik ayrıştırma/hesap hatasının iletisi. */
@Composable
fun mathErrorText(e: MathError): String {
    val at = (e.pos + 1).coerceAtLeast(1)
    return when (e.code) {
        MathError.Code.EMPTY -> stringResource(R.string.v45_math_empty)
        MathError.Code.UNEXPECTED -> stringResource(R.string.v45_math_unexpected, e.detail, at)
        MathError.Code.PARENTHESIS -> stringResource(R.string.v45_math_parenthesis, at)
        MathError.Code.MISSING_OPERAND -> stringResource(R.string.v45_math_missing, at)
        MathError.Code.UNKNOWN_NAME -> stringResource(R.string.v45_math_unknown, e.detail)
        MathError.Code.ARGUMENTS -> stringResource(R.string.v45_math_arguments, e.detail)
        MathError.Code.DIVISION_BY_ZERO -> stringResource(R.string.v45_math_div_zero)
        MathError.Code.DOMAIN -> stringResource(R.string.v45_math_domain, e.detail)
        MathError.Code.RELATION -> stringResource(R.string.v45_math_relation)
        MathError.Code.OVERFLOW -> stringResource(R.string.v45_math_overflow)
    }
}

/** Kimya okuma hatasının iletisi ([ParamError.detail] "KOD:arg" biçiminde). */
@Composable
fun chemErrorText(detail: String): String {
    val code = runCatching { ChemError.Code.valueOf(detail.substringBefore(':')) }.getOrNull()
    val arg = detail.substringAfter(':', "")
    return when (code) {
        ChemError.Code.EMPTY -> stringResource(R.string.v45_chem_empty)
        ChemError.Code.UNKNOWN_ELEMENT -> stringResource(R.string.v45_chem_unknown_element, arg)
        ChemError.Code.VALENCE -> stringResource(R.string.v45_chem_valence, arg)
        ChemError.Code.HYDROGEN_COUNT -> stringResource(R.string.v45_chem_hydrogen)
        ChemError.Code.TOO_LARGE -> stringResource(R.string.v45_chem_too_large, arg)
        ChemError.Code.IONIC -> stringResource(R.string.v45_chem_ionic, arg)
        ChemError.Code.PARENTHESIS -> stringResource(R.string.v45_chem_parenthesis)
        ChemError.Code.RING -> stringResource(R.string.v45_chem_ring, arg)
        ChemError.Code.SYNTAX, null -> stringResource(R.string.v45_chem_syntax)
    }
}

/** Parametreli şekil formülünün hata iletisi. */
@Composable
fun paramErrorText(e: ParamError): String = when (e.code) {
    ParamError.Code.SYNTAX -> e.math?.let { mathErrorText(it) } ?: stringResource(R.string.v45_math_empty)
    ParamError.Code.UNKNOWN_SYMBOL -> stringResource(R.string.v45_math_unknown, e.detail)
    ParamError.Code.OUT_OF_RANGE -> stringResource(R.string.v45_param_out_of_range, e.detail)
    ParamError.Code.NO_MATCH -> stringResource(R.string.v45_param_no_match)
    ParamError.Code.NOT_NUMBER -> e.math?.let { mathErrorText(it) } ?: stringResource(R.string.v45_math_div_zero)
    ParamError.Code.CHEMISTRY -> chemErrorText(e.detail)
}

@Composable
private fun ErrorLine(text: String, tag: String) {
    Text(
        text, Modifier.padding(horizontal = 8.dp).testTag(tag),
        color = NeuTheme.colors.danger, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun ParamStepper(label: String, onMinus: () -> Unit, onPlus: () -> Unit, tag: String) {
    NeuSurface(shape = NeuPillShape, elevation = 3.dp, modifier = Modifier.testTag(tag)) {
        Row(Modifier.padding(horizontal = 4.dp).height(40.dp), verticalAlignment = Alignment.CenterVertically) {
            NeuIconButton(Icons.Filled.Remove, stringResource(R.string.v45_param_less, label), onMinus, size = 32.dp)
            Text(label, Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.labelLarge, color = NeuTheme.colors.content, maxLines = 1)
            NeuIconButton(Icons.Filled.Add, stringResource(R.string.v45_param_more, label), onPlus, size = 32.dp)
        }
    }
}

@Composable
private fun molViewLabel(view: String): String = stringResource(
    when (view) {
        MolView.CONDENSED -> R.string.v45_mol_condensed
        MolView.SKELETAL -> R.string.v45_mol_skeletal
        MolView.BALL -> R.string.v45_mol_3d
        else -> R.string.v45_mol_structural
    },
)

/** Sayının kısa gösterimi (adım düğmesinin yazısında). */
private fun short(v: Double): String = ParamModel.num(v, 2)

/**
 * Parametreli şeklin düzenleyicisi: formül (dokununca yazma penceresi), her değerin adımlayıcısı, metin seçenekleri
 * (molekül görünümü) ve "Vektör düzenle" (şekil vektör noktalarıyla düzenlenir).
 */
@Composable
fun ParamControls(viewModel: CanvasEditViewModel, selected: Stroke?, modifier: Modifier = Modifier) {
    val ctl = viewModel.paramShapes
    val model = ctl.model(selected) ?: return
    val raw = selected?.shape?.params.orEmpty()
    val error by ctl.error.collectAsState()
    var editing by remember { mutableStateOf(false) }
    val values = model.values(raw)
    val formulaText = model.editText(raw)
    Column(modifier.testTag("param_controls"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            NeuChip(formulaText, false, { editing = true }, Modifier.widthIn(max = 320.dp).testTag("param_formula"), icon = Icons.Filled.Functions)
            for (d in model.defs) {
                when {
                    d.kind == ParamKind.TEXT && d.choices.isNotEmpty() -> {
                        val cur = values.text(d.key)
                        for (c in d.choices) NeuChip(molViewLabel(c), cur == c, { ctl.setText(d.key, c) }, Modifier.testTag("param_choice_$c"))
                    }
                    d.kind == ParamKind.TEXT || d.advanced -> Unit
                    else -> {
                        val v = values[d.key]
                        val unit = if (d.unit.isEmpty() || d.unit == "°") d.unit else " ${d.unit}"
                        ParamStepper(
                            "${d.symbol} = ${short(v)}$unit",
                            onMinus = { ctl.setValue(d.key, v - d.step) },
                            onPlus = { ctl.setValue(d.key, v + d.step) },
                            tag = "param_step_${d.key}",
                        )
                    }
                }
            }
            NeuButton(
                stringResource(R.string.v45_vector_edit), { ctl.clearError(); viewModel.selectShapeTool(ShapeTool.NODES) },
                Modifier.testTag("param_vector_edit"), icon = Icons.Filled.ScatterPlot,
            )
        }
        error?.let { ErrorLine(paramErrorText(it), "param_error") }
    }
    if (editing) {
        TextInputSheet(
            title = stringResource(R.string.v45_param_formula), initial = formulaText, label = stringResource(R.string.v45_param_formula),
            onConfirm = { text -> editing = false; ctl.applyFormula(text) },
            onDismiss = { editing = false },
            hint = stringResource(R.string.v45_param_formula_hint), tag = "param_formula_sheet",
        )
    }
}

/** Şekil üstündeki tutamaklar (tuval koordinatında, görünümle birlikte). */
@Composable
fun ParamHandlesLayer(viewModel: CanvasEditViewModel, selected: Stroke?, viewport: ViewportState) {
    val handles = remember(selected) { viewModel.paramShapes.handles(selected) }
    if (handles.isEmpty()) return
    val accent = NeuTheme.colors.accent
    Canvas(Modifier.fillMaxSize().testTag("param_handles")) {
        val sc = viewport.scale
        for ((_, p) in handles) {
            val s = Offset(viewport.offset.x + p.x * sc, viewport.offset.y + p.y * sc)
            drawCircle(Color.White, radius = 11.dp.toPx(), center = s)
            drawCircle(accent, radius = 11.dp.toPx(), center = s, style = DrawStroke(3.dp.toPx()))
            drawCircle(accent, radius = 4.dp.toPx(), center = s)
        }
    }
}

/**
 * Grafiğin formül satırı: dokununca yazma penceresi; geçersiz formül uygulanmaz (son geçerli eğri kalır), hata kırmızı
 * yazılır. Eğri ve yüzey grafiklerinde derece/radyan seçimi.
 */
@Composable
fun GraphFormulaRow(params: GraphParams, onChange: ((GraphParams) -> GraphParams) -> Unit, modifier: Modifier = Modifier) {
    if (!GraphFormula.accepts(params.kind)) return
    var editing by remember { mutableStateOf(false) }
    var error by remember(params.kind) { mutableStateOf<MathError?>(null) }
    val meaning = GraphFormula.meaning(params.kind)
    val text = Graphs.formulaText(params)
    Column(modifier.testTag("graph_formula_row"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            NeuChip(
                text.ifBlank { stringResource(R.string.v45_graph_formula) }, false, { editing = true },
                Modifier.widthIn(max = 320.dp).testTag("graph_formula"), icon = Icons.Filled.Functions,
            )
            if (meaning == GraphFormula.Meaning.CURVE || meaning == GraphFormula.Meaning.SURFACE || meaning == GraphFormula.Meaning.SAMPLED) {
                NeuChip(stringResource(R.string.v45_graph_degrees), params.angleDeg, { onChange { it.copy(angleDeg = !it.angleDeg) } }, Modifier.testTag("graph_degrees"))
            }
        }
        error?.let { ErrorLine(mathErrorText(it), "graph_formula_error") }
    }
    if (editing) {
        val hint = stringResource(
            when (meaning) {
                GraphFormula.Meaning.SURFACE -> R.string.v45_graph_hint_surface
                GraphFormula.Meaning.SET -> R.string.v45_graph_hint_set
                GraphFormula.Meaning.SAMPLED -> R.string.v45_graph_hint_sampled
                else -> R.string.v45_graph_hint_curve
            },
        )
        TextInputSheet(
            title = stringResource(R.string.v45_graph_formula), initial = text, label = stringResource(R.string.v45_graph_formula),
            onConfirm = { t ->
                editing = false
                val e = if (t.isBlank()) null else GraphFormula.error(params.kind, t)
                error = e
                if (e == null) onChange { Graphs.withFormula(it, t) }
            },
            onDismiss = { editing = false }, hint = hint, allowBlank = true, tag = "graph_formula_sheet",
        )
    }
}

/** Formül düzenlenirken sayılarla hesap yapılamadıysa (sıfıra bölme, tanım dışı) kırmızı ileti. */
@Composable
fun FormulaEvalNotice(viewModel: CanvasEditViewModel) {
    val e by viewModel.formulaEvalError.collectAsState()
    val err = e ?: return
    NeuSurface(shape = NeuPillShape, elevation = 4.dp, modifier = Modifier.padding(12.dp).testTag("formula_eval_error")) {
        Text(
            stringResource(R.string.v45_formula_not_computed, mathErrorText(err)), Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            color = NeuTheme.colors.danger, style = MaterialTheme.typography.bodyMedium,
        )
    }
}
