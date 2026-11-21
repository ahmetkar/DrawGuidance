package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.model.TransformFilter
import com.karapp.drawguidance.data.model.TransformKind
import com.karapp.drawguidance.data.model.TransformOps
import com.karapp.drawguidance.data.model.WarpMesh
import com.karapp.drawguidance.ui.components.NeuChip
import com.karapp.drawguidance.ui.components.NeuSlider
import com.karapp.drawguidance.ui.components.NeuSwitch
import com.karapp.drawguidance.ui.components.NeuTextField
import com.karapp.drawguidance.ui.i18n.Text
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ViewportState
import com.karapp.drawguidance.ui.screens.canvasedit.state.LiquifyController
import com.karapp.drawguidance.ui.screens.canvasedit.state.TransformController
import com.karapp.drawguidance.ui.theme.NeuTheme
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Tutamağın türü (sürükleme başında belirlenir). */
private sealed interface Grip {
    data class Corner(val i: Int) : Grip
    data class Edge(val e: Int) : Grip
    data object Rotate : Grip
    data class Mesh(val i: Int) : Grip
    data object Move : Grip
    data object MeshInside : Grip
}

/**
 * v56 Dönüştür paneli tuval katmanı (planın 2A bölümü): dörtgen ya da ağ, köşe/kenar/döndürme tutamaçları, yaslama
 * kılavuzları; sürükleme tutamağa göre ölçekler, döndürür, bozar, çarpıtır ya da taşır. Mıknatısla taşıma baskın eksene
 * kilitlenir.
 */
@Composable
fun V56TransformOverlay(controller: TransformController, viewport: ViewportState) {
    val state by controller.state.collectAsState()
    val guides by controller.guides.collectAsState()
    val snap by controller.snap.collectAsState()
    val s = state ?: return
    val accent = NeuTheme.colors.accent
    val density = LocalDensity.current
    val hit = with(density) { 22.dp.toPx() }
    val rotOff = with(density) { 32.dp.toPx() }
    Canvas(
        Modifier.fillMaxSize().testTag("v56_transform_overlay").pointerInput(Unit) {
            var grip: Grip? = null
            var total = Offset.Zero
            var applied = Offset.Zero
            var lastTime = 0L
            detectDragGestures(
                onDragStart = { p ->
                    val st = controller.state.value ?: return@detectDragGestures
                    total = Offset.Zero; applied = Offset.Zero
                    lastTime = System.currentTimeMillis()
                    val q = st.quad.map { viewport.toScreen(Offset(it.x, it.y)) }
                    grip = when {
                        st.kind == TransformKind.WARP && st.mesh != null -> {
                            val mesh = st.mesh!!
                            mesh.indexOfFirst { (viewport.toScreen(Offset(it.x, it.y)) - p).getDistance() < hit }.takeIf { it >= 0 }?.let { Grip.Mesh(it) }
                                ?: if (insideQuad(p, meshCorners(mesh).map { viewport.toScreen(Offset(it.x, it.y)) })) Grip.MeshInside else null
                        }
                        else -> {
                            val top = (q[0] + q[1]) / 2f
                            val n = normal(q[0], q[1], q[3])
                            when {
                                (top + n * rotOff - p).getDistance() < hit -> Grip.Rotate
                                q.indexOfFirst { (it - p).getDistance() < hit }.takeIf { it >= 0 } != null -> Grip.Corner(q.indexOfFirst { (it - p).getDistance() < hit })
                                (0 until 4).firstOrNull { e -> ((q[e] + q[(e + 1) % 4]) / 2f - p).getDistance() < hit } != null ->
                                    Grip.Edge((0 until 4).first { e -> ((q[e] + q[(e + 1) % 4]) / 2f - p).getDistance() < hit })
                                insideQuad(p, q) -> Grip.Move
                                else -> null
                            }
                        }
                    }
                },
                onDragEnd = { grip = null; controller.endDrag() },
                onDragCancel = { grip = null; controller.endDrag() },
                onDrag = { change, drag ->
                    change.consume()
                    val g = grip ?: return@detectDragGestures
                    val st = controller.state.value ?: return@detectDragGestures
                    val now = System.currentTimeMillis()
                    val dt = (now - lastTime).coerceAtLeast(1L)
                    lastTime = now
                    val speed = drag.getDistance() / dt * 1000f
                    val c = viewport.toCanvas(change.position)
                    val d = drag / viewport.scale
                    when (g) {
                        is Grip.Corner -> controller.dragCorner(g.i, c.x, c.y)
                        is Grip.Edge -> controller.dragEdge(g.e, d.x, d.y)
                        Grip.Rotate -> {
                            val cx = st.quad.sumOf { it.x.toDouble() }.toFloat() / 4f; val cy = st.quad.sumOf { it.y.toDouble() }.toFloat() / 4f
                            controller.rotateTo(Math.toDegrees(atan2((c.y - cy).toDouble(), (c.x - cx).toDouble())).toFloat() + 90f)
                        }
                        is Grip.Mesh -> controller.dragMesh(g.i, c.x, c.y)
                        Grip.MeshInside -> controller.dragMeshInside(c.x, c.y, d.x, d.y)
                        Grip.Move -> {
                            // Mıknatıs: toplam sürüklemenin baskın eksenine kilitli
                            total += d
                            val target = if (snap.magnets) {
                                if (kotlin.math.abs(total.x) >= kotlin.math.abs(total.y)) Offset(total.x, 0f) else Offset(0f, total.y)
                            } else total
                            val step = target - applied
                            applied = target
                            controller.translate(step.x, step.y, viewport.scale, speed)
                        }
                    }
                },
            )
        },
    ) {
        val line = Stroke(width = 2f)
        val mesh = s.mesh
        if (mesh != null) {
            val n = WarpMesh.sizeOf(mesh)
            // Ağ çizgileri eğri (yüzeyden örneklenir)
            for (k in 0 until n) {
                for (horizontal in listOf(true, false)) {
                    val path = Path()
                    for (j in 0..24) {
                        val t = j / 24f; val fixed = k.toFloat() / (n - 1)
                        val (x, y) = WarpMesh.eval(mesh, n, if (horizontal) t else fixed, if (horizontal) fixed else t)
                        val sp = viewport.toScreen(Offset(x, y))
                        if (j == 0) path.moveTo(sp.x, sp.y) else path.lineTo(sp.x, sp.y)
                    }
                    drawPath(path, accent.copy(alpha = 0.8f), style = line)
                }
            }
            mesh.forEach { p -> val sp = viewport.toScreen(Offset(p.x, p.y)); drawCircle(Color.White, 9f, sp); drawCircle(accent, 9f, sp, style = line) }
        } else {
            val q = s.quad.map { viewport.toScreen(Offset(it.x, it.y)) }
            val path = Path().apply { moveTo(q[0].x, q[0].y); for (i in 1 until 4) lineTo(q[i].x, q[i].y); close() }
            drawPath(path, accent, style = line)
            val top = (q[0] + q[1]) / 2f
            val n = normal(q[0], q[1], q[3])
            drawLine(accent, top, top + n * rotOff, strokeWidth = 2f)
            drawCircle(Color.White, 11f, top + n * rotOff); drawCircle(accent, 11f, top + n * rotOff, style = line)
            q.forEach { drawCircle(Color.White, 11f, it); drawCircle(accent, 11f, it, style = line) }
            for (e in 0 until 4) { val m = (q[e] + q[(e + 1) % 4]) / 2f; drawCircle(Color.White, 7f, m); drawCircle(accent, 7f, m, style = line) }
        }
        // Yaslama kılavuzları (mavi)
        val blue = Color(0xFF2F80ED)
        guides.first?.let { gx -> val x = viewport.toScreen(Offset(gx, 0f)).x; drawLine(blue, Offset(x, 0f), Offset(x, size.height), 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))) }
        guides.second?.let { gy -> val y = viewport.toScreen(Offset(0f, gy)).y; drawLine(blue, Offset(0f, y), Offset(size.width, y), 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))) }
    }
}

private fun meshCorners(m: List<com.karapp.drawguidance.data.model.StrokePoint>): List<com.karapp.drawguidance.data.model.StrokePoint> {
    val n = WarpMesh.sizeOf(m)
    return listOf(m[0], m[n - 1], m[n * n - 1], m[n * (n - 1)])
}

/** Üst kenara dik, dörtgenin dışına bakan birim vektör. */
private fun normal(a: Offset, b: Offset, d: Offset): Offset {
    val ex = b.x - a.x; val ey = b.y - a.y
    val len = hypot(ex, ey).coerceAtLeast(1e-3f)
    var n = Offset(ey / len, -ex / len)
    // İçe bakıyorsa çevir (alt-sol köşenin ters yönü dış)
    if ((d.x - a.x) * n.x + (d.y - a.y) * n.y > 0f) n = Offset(-n.x, -n.y)
    return n
}

private fun insideQuad(p: Offset, q: List<Offset>): Boolean {
    var inside = false
    var j = q.size - 1
    for (i in q.indices) {
        val a = q[i]; val b = q[j]
        if ((a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x) inside = !inside
        j = i
    }
    return inside
}

/**
 * v56 Dönüştür alt paneli: üst satırda dört kip (Çarpıtma'da "Gelişmiş ağ"); alt satırda yaslama, çevirme, 45° döndür
 * 90°, tuvale sığdır, ara değerleme, sayısal giriş, sıfırla, iptal, bitti. Düğme sırası RTL'de aynalanır.
 */
@Composable
fun V56TransformPanel(controller: TransformController, modifier: Modifier = Modifier) {
    val state by controller.state.collectAsState()
    val snap by controller.snap.collectAsState()
    val advanced by controller.advancedMesh.collectAsState()
    val skipped by controller.skipped.collectAsState()
    val s = state ?: return
    var snapOpen by remember { mutableStateOf(false) }
    var numericOpen by remember { mutableStateOf(false) }
    val colors = NeuTheme.colors
    Column(
        modifier.fillMaxWidth().padding(8.dp).background(colors.background, RoundedCornerShape(18.dp)).padding(8.dp).testTag("v56_transform_panel"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(
                TransformKind.FREE to R.string.v56_tf_free, TransformKind.UNIFORM to R.string.v56_tf_uniform,
                TransformKind.DISTORT to R.string.v56_tf_distort, TransformKind.WARP to R.string.v56_tf_warp,
            ).forEach { (k, label) ->
                NeuChip(stringResource(label), s.kind == k, { controller.setKind(k) }, Modifier.testTag("v56_tf_${k.name.lowercase()}"))
            }
            if (s.kind == TransformKind.WARP) {
                NeuChip(stringResource(R.string.v56_tf_advanced_mesh), advanced, { controller.setAdvancedMesh(!advanced) }, Modifier.testTag("v56_tf_advanced_mesh"))
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            NeuChip(stringResource(R.string.v56_tf_snapping), snap.snap || snap.magnets, { snapOpen = true }, Modifier.testTag("v56_tf_snapping"))
            NeuChip(stringResource(R.string.v56_tf_flip_h), false, { controller.flip(true) }, Modifier.testTag("v56_tf_flip_h"))
            NeuChip(stringResource(R.string.v56_tf_flip_v), false, { controller.flip(false) }, Modifier.testTag("v56_tf_flip_v"))
            NeuChip(stringResource(R.string.v56_tf_rotate45), false, { controller.rotateStep(45f) }, Modifier.testTag("v56_tf_rotate45"))
            NeuChip(stringResource(R.string.v56_tf_rotate90), false, { controller.rotateStep(90f) }, Modifier.testTag("v56_tf_rotate90"))
            NeuChip(stringResource(R.string.v56_tf_fit), false, { controller.fitToCanvas() }, Modifier.testTag("v56_tf_fit"))
            val filterLabel = stringResource(when (s.filter) { TransformFilter.NEAREST -> R.string.v56_tf_nearest; TransformFilter.BILINEAR -> R.string.v56_tf_bilinear; TransformFilter.BICUBIC -> R.string.v56_tf_bicubic })
            NeuChip(stringResource(R.string.v56_tf_filter, filterLabel), false, {
                controller.setFilter(TransformFilter.entries[(s.filter.ordinal + 1) % TransformFilter.entries.size])
            }, Modifier.testTag("v56_tf_filter"))
            NeuChip(stringResource(R.string.v56_tf_numeric), false, { numericOpen = true }, Modifier.testTag("v56_tf_numeric"))
            NeuChip(stringResource(R.string.v56_tf_reset), false, { controller.reset() }, Modifier.testTag("v56_tf_reset"))
            NeuChip(stringResource(R.string.cancel_button), false, { controller.cancel() }, Modifier.testTag("v56_tf_cancel"))
            NeuChip(stringResource(R.string.v56_tf_done), true, { controller.done() }, Modifier.testTag("v56_tf_done"))
        }
        if (skipped > 0 && (s.kind == TransformKind.DISTORT || s.kind == TransformKind.WARP)) {
            Text(stringResource(R.string.v56_tf_skipped, skipped), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted, modifier = Modifier.testTag("v56_tf_skipped"))
        }
    }
    if (snapOpen) SnapDialog(controller) { snapOpen = false }
    if (numericOpen) NumericDialog(controller) { numericOpen = false }
}

@Composable
private fun SnapDialog(controller: TransformController, onDismiss: () -> Unit) {
    val p by controller.snap.collectAsState()
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = NeuTheme.colors.background,
        title = { Text(stringResource(R.string.v56_tf_snapping)) },
        text = {
            Column(Modifier.testTag("v56_tf_snap_dialog"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.v56_tf_magnets))
                        Text(stringResource(R.string.v56_tf_magnets_hint), style = MaterialTheme.typography.bodySmall, color = NeuTheme.colors.contentMuted)
                    }
                    NeuSwitch(p.magnets, { controller.setSnap(p.copy(magnets = it)) }, Modifier.testTag("v56_tf_magnets"))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.v56_tf_snap), Modifier.weight(1f))
                    NeuSwitch(p.snap, { controller.setSnap(p.copy(snap = it)) }, Modifier.testTag("v56_tf_snap"))
                }
                Text(stringResource(R.string.v56_tf_snap_distance) + " · ${p.distance.roundToInt()}")
                NeuSlider(p.distance, { controller.setSnap(p.copy(distance = it)) }, 2f..40f, Modifier.widthIn(min = 200.dp).testTag("v56_tf_snap_distance"))
                Text(stringResource(R.string.v56_tf_snap_speed) + " · ${p.speed.roundToInt()}")
                NeuSlider(p.speed, { controller.setSnap(p.copy(speed = it)) }, 0f..6000f, Modifier.widthIn(min = 200.dp).testTag("v56_tf_snap_speed"))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun NumericDialog(controller: TransformController, onDismiss: () -> Unit) {
    val m = controller.metrics() ?: run { onDismiss(); return }
    var x by remember { mutableStateOf(m[0].roundToInt().toString()) }
    var y by remember { mutableStateOf(m[1].roundToInt().toString()) }
    var w by remember { mutableStateOf(m[2].roundToInt().toString()) }
    var h by remember { mutableStateOf(m[3].roundToInt().toString()) }
    var a by remember { mutableStateOf(m[4].roundToInt().toString()) }
    @Composable
    fun field(label: Int, value: String, tag: String, set: (String) -> Unit) =
        NeuTextField(value = value, onValueChange = set, label = stringResource(label), modifier = Modifier.testTag(tag),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = NeuTheme.colors.background,
        title = { Text(stringResource(R.string.v56_tf_numeric)) },
        text = {
            Column(Modifier.testTag("v56_tf_numeric_dialog"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                field(R.string.v56_tf_x, x, "v56_tf_num_x") { x = it }
                field(R.string.v56_tf_y, y, "v56_tf_num_y") { y = it }
                field(R.string.v56_tf_w, w, "v56_tf_num_w") { w = it }
                field(R.string.v56_tf_h, h, "v56_tf_num_h") { h = it }
                field(R.string.v56_tf_angle, a, "v56_tf_num_angle") { a = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                controller.numeric(
                    x.toFloatOrNull() ?: m[0], y.toFloatOrNull() ?: m[1], w.toFloatOrNull() ?: m[2], h.toFloatOrNull() ?: m[3], a.toFloatOrNull() ?: m[4],
                )
                onDismiss()
            }, modifier = Modifier.testTag("v56_tf_num_apply")) { Text(stringResource(R.string.ok_button)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_button)) } },
    )
}

/** v56 Sıvılaştır tuval katmanı: sürükleme seçili kipte ağı deforme eder; fırça dairesi gösterilir. */
@Composable
fun V56LiquifyOverlay(controller: LiquifyController, viewport: ViewportState) {
    val target by controller.target.collectAsState()
    val radius by controller.radius.collectAsState()
    if (target == null) return
    var cursor by remember { mutableStateOf<Offset?>(null) }
    val accent = NeuTheme.colors.accent
    Canvas(
        Modifier.fillMaxSize().testTag("v56_liquify_overlay").pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { cursor = it },
                onDragEnd = { cursor = null }, onDragCancel = { cursor = null },
                onDrag = { change, drag ->
                    change.consume()
                    cursor = change.position
                    val c = viewport.toCanvas(change.position)
                    controller.brush(c.x, c.y, drag.x / viewport.scale, drag.y / viewport.scale)
                },
            )
        },
    ) {
        cursor?.let { drawCircle(accent, radius * viewport.scale, it, style = Stroke(2f)) }
    }
}

/** v56 Sıvılaştır alt çubuğu: it, döndür, büzüştür, şişir; boyut, güç; iptal, bitti. */
@Composable
fun V56LiquifyBar(controller: LiquifyController, modifier: Modifier = Modifier) {
    val target by controller.target.collectAsState()
    val mode by controller.mode.collectAsState()
    val radius by controller.radius.collectAsState()
    val strength by controller.strength.collectAsState()
    if (target == null) return
    val colors = NeuTheme.colors
    Column(
        modifier.fillMaxWidth().padding(8.dp).background(colors.background, RoundedCornerShape(18.dp)).padding(8.dp).testTag("v56_liquify_bar"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                WarpMesh.Liquify.PUSH to R.string.v56_lq_push, WarpMesh.Liquify.TWIRL to R.string.v56_lq_twirl,
                WarpMesh.Liquify.PINCH to R.string.v56_lq_pinch, WarpMesh.Liquify.BLOAT to R.string.v56_lq_bloat,
            ).forEach { (m, label) -> NeuChip(stringResource(label), mode == m, { controller.setMode(m) }, Modifier.testTag("v56_lq_${m.name.lowercase()}")) }
            NeuChip(stringResource(R.string.cancel_button), false, { controller.cancel() }, Modifier.testTag("v56_lq_cancel"))
            NeuChip(stringResource(R.string.v56_tf_done), true, { controller.done() }, Modifier.testTag("v56_lq_done"))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.v56_lq_size), style = MaterialTheme.typography.labelMedium)
            NeuSlider(radius, controller::setRadius, 10f..400f, Modifier.weight(1f).testTag("v56_lq_size"))
            Text(stringResource(R.string.v56_lq_strength), style = MaterialTheme.typography.labelMedium)
            NeuSlider(strength, controller::setStrength, 0.05f..1f, Modifier.weight(1f).testTag("v56_lq_strength"))
        }
    }
}

@Suppress("unused")
private fun angleLabel(s: com.karapp.drawguidance.data.model.TransformState): Int = TransformOps.angleOf(s.quad).roundToInt()
