package com.karapp.drawguidance.ui.screens.sketch3d

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.karapp.drawguidance.content.model3d.Model3D
import com.karapp.drawguidance.content.model3d.Plane3
import com.karapp.drawguidance.content.model3d.PlaneSnap
import com.karapp.drawguidance.content.model3d.RenderOptions
import com.karapp.drawguidance.content.model3d.Renderer
import com.karapp.drawguidance.content.model3d.V3
import com.karapp.drawguidance.content.model3d.View3
import com.karapp.drawguidance.content.model3d.gl.GlOptions
import com.karapp.drawguidance.data.settings.Model3DPrefs
import com.karapp.drawguidance.ui.screens.model3d.Budget3D
import com.karapp.drawguidance.ui.screens.model3d.gl.Model3DGlView
import com.karapp.drawguidance.ui.theme.NeuTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

private data class SketchRender(val model: Model3D?, val selection: Set<String>, val size: IntSize, val interacting: Boolean, val quality: Int, val tick: Int)

/**
 * v56 3B Çizim sahnesi: model ekran kartıyla ([Model3DGlView]) ya da yazılım çizicisiyle çizilir; üstte görünür çizim
 * düzlemi (ızgara, yatıklıkta uyarı rengi, Düzlem modunda taşıma tutamacı), çizilen izin önizlemesi, silgi ucu ve
 * seçili çizgilerin vurgusu. Jestler: kalem çizer; tek parmak döndürür (Parmakla çiz açıkken çizer, döndürme iki
 * parmağa geçer); iki parmak kaydırır ve yakınlaştırır; iki parmakla dokunma geri alır. Çizerken parmak bir süre
 * durursa iz düz çizgiye döner.
 */
@Composable
fun Sketch3DViewport(vm: Sketch3DViewModel, model: Model3D?, modifier: Modifier = Modifier) {
    val colors = NeuTheme.colors
    val gpu by vm.gpu.collectAsState()
    val quality by vm.quality.collectAsState()
    val tick by vm.renderTick.collectAsState()
    val mode by vm.mode.collectAsState()
    val tool by vm.tool.collectAsState()
    val plane by vm.plane.collectAsState()
    val selection by vm.selection.collectAsState()
    val fingerDraw by vm.fingerDraw.collectAsState()
    val color by vm.color.collectAsState()
    val sizePx by vm.size.collectAsState()
    val eraserDp by vm.eraserSize.collectAsState()
    var size by remember { mutableStateOf(IntSize.Zero) }
    var interacting by remember { mutableStateOf(false) }
    val drawPath = remember { mutableStateListOf<Offset>() }
    var straight by remember { mutableStateOf(false) }
    var eraserAt by remember { mutableStateOf<Offset?>(null) }
    val currentMode by rememberUpdatedState(mode)
    val currentTool by rememberUpdatedState(tool)
    val currentFinger by rememberUpdatedState(fingerDraw)
    val bgColor = colors.background
    val shown = model?.visibleLayers()

    Box(modifier.fillMaxSize().onSizeChanged { size = it; vm.setViewport(it.width, it.height) }) {
        if (gpu && shown != null) {
            GpuSketch(vm, shown, selection, quality, tick, bgColor)
        } else {
            var image by remember { mutableStateOf<ImageBitmap?>(null) }
            LaunchedEffect(vm) {
                combine(vm.model, vm.selection, vm.renderTick, snapshotFlow { Triple(size, interacting, quality) }) { m, sel, tk, si ->
                    SketchRender(m?.visibleLayers(), sel, si.first, si.second, si.third, tk)
                }.collectLatest { req ->
                    val m = req.model ?: return@collectLatest
                    if (req.size.width <= 0 || req.size.height <= 0) return@collectLatest
                    val target = when (req.quality) {
                        Model3DPrefs.QUALITY_HIGH -> if (req.interacting) 520f else 1000f
                        Model3DPrefs.QUALITY_BALANCED -> if (req.interacting) 420f else 760f
                        else -> if (req.interacting) 320f else 560f
                    }
                    val k = min(1f, target * Budget3D.softwareScale / max(req.size.width, req.size.height))
                    val w = (req.size.width * k).toInt().coerceAtLeast(16)
                    val h = (req.size.height * k).toInt().coerceAtLeast(16)
                    image = withContext(Dispatchers.Default) {
                        val frame = Renderer.render(
                            m, View3.editor(m.camera, w, h),
                            RenderOptions(
                                textures = vm::texture,
                                supersample = if (req.interacting || req.quality == Model3DPrefs.QUALITY_FAST) 1 else 2,
                                bilinear = !req.interacting,
                                shadow = if (req.interacting) false else null,
                                selectedParts = req.selection,
                                selectedPart = req.selection.firstOrNull { id -> m.parts.any { it.id == id } },
                            ),
                        )
                        Bitmap.createBitmap(frame.color, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
                    }
                }
            }
            Canvas(Modifier.fillMaxSize()) {
                val bg = shown?.scene?.background ?: 0
                drawRect(if (bg != 0) Color(bg) else bgColor)
                image?.let { img -> drawImage(img, dstOffset = IntOffset.Zero, dstSize = IntSize(size.width, size.height), filterQuality = FilterQuality.Low) }
            }
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .testTag("sketch3d_viewport")
                .pointerInput(vm) {
                    val slop = viewConfiguration.touchSlop
                    val radius = 22.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val m = currentMode
                        val t = currentTool
                        val stylus = down.type == PointerType.Stylus
                        if (stylus) vm.stylusSeen()
                        val penInput = stylus || down.type == PointerType.Eraser || currentFinger
                        val drawing = m == SketchMode.DRAW && penInput
                        val start = down.position
                        var prev = start
                        var moved = false
                        var multi = false
                        var multiMoved = false
                        val multiStart = System.currentTimeMillis()
                        var live = false
                        var planeDrag = false
                        var orbitDrag = false
                        interacting = true
                        drawPath.clear(); straight = false
                        val xy = ArrayList<Float>()
                        val pressures = ArrayList<Float>()
                        var lastMoveAt = System.currentTimeMillis()
                        var lastMovePos = start
                        val eraserR = vm.eraserSize.value * density
                        fun addPoint(p: Offset, pressure: Float) {
                            xy += p.x; xy += p.y; pressures += pressure
                            drawPath += p
                        }
                        if (drawing && t == SketchTool.PEN) addPoint(start, down.pressure)
                        if (drawing && t == SketchTool.ERASER) { vm.beginErase(); vm.eraseAt(start.x, start.y, eraserR); eraserAt = start }
                        // Düzlem modunda düzlemin merkez tutamacından başlayan sürükleme düzlemi kaydırır
                        val grabbedPlane = m == SketchMode.PLANE && vm.currentView()?.let { v ->
                            val s = v.project(vm.plane.value.origin)
                            s[3] > 0f && (Offset(s[0], s[1]) - start).getDistance() < radius * 1.6f
                        } == true
                        val grabbedSel = m == SketchMode.SELECT && vm.selection.value.isNotEmpty() && vm.onSelection(start.x, start.y, radius)
                        while (true) {
                            val e = withTimeoutOrNull(120) { awaitPointerEvent() }
                            if (e == null) {
                                // Parmak kalemle durdu: düz çizgi
                                if (drawing && t == SketchTool.PEN && moved && !straight && xy.size >= 6 && System.currentTimeMillis() - lastMoveAt > HOLD_MS) {
                                    straight = true
                                    val first = drawPath.first(); val last = drawPath.last()
                                    drawPath.clear(); drawPath += first; drawPath += last
                                }
                                continue
                            }
                            val pressed = e.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            if (pressed.size >= 2) {
                                if (!multi) {
                                    if (live) { vm.endLive(); live = false }
                                    drawPath.clear(); xy.clear(); pressures.clear(); eraserAt = null
                                }
                                multi = true
                                val zoom = e.calculateZoom()
                                val pan = e.calculatePan()
                                if (pan.getDistance() > 0.5f || zoom != 1f) multiMoved = true
                                // Parmakla çizimde iki parmak döndürür; öteki durumlarda kaydırır
                                if (m == SketchMode.DRAW && currentFinger) vm.orbit(-pan.x * ORBIT, pan.y * ORBIT) else vm.pan(pan.x, pan.y)
                                if (zoom != 1f) vm.zoom(zoom)
                                e.changes.forEach { it.consume() }
                                continue
                            }
                            if (multi) continue
                            val c = pressed[0]
                            if (c.type == PointerType.Stylus) vm.stylusSeen()
                            val d = c.position - prev
                            prev = c.position
                            if (!moved && (c.position - start).getDistance() > slop) {
                                moved = true
                                when {
                                    drawing -> {}
                                    grabbedPlane -> planeDrag = true
                                    grabbedSel -> { vm.beginLive(); live = true }
                                    else -> orbitDrag = true
                                }
                            }
                            if (!moved) continue
                            c.consume()
                            if ((c.position - lastMovePos).getDistance() > 3f) { lastMoveAt = System.currentTimeMillis(); lastMovePos = c.position }
                            when {
                                orbitDrag -> vm.orbit(-d.x * ORBIT, d.y * ORBIT)
                                planeDrag -> vm.dragPlane(d.y)
                                live -> when (vm.xform.value) {
                                    SketchXform.MOVE -> vm.moveSelectedBy(d.x, d.y)
                                    SketchXform.ROTATE -> vm.rotateSelected(d.x * 0.5f)
                                    SketchXform.SCALE -> vm.scaleSelected(kotlin.math.exp((d.x - d.y) / 260f))
                                }
                                drawing && t == SketchTool.PEN -> if (!straight) addPoint(c.position, if (c.type == PointerType.Stylus) c.pressure else 1f)
                                    else { drawPath.clear(); drawPath += Offset(xy[0], xy[1]); drawPath += c.position; xy += c.position.x; xy += c.position.y; pressures += 1f }
                                drawing && t == SketchTool.ERASER -> { vm.eraseAt(c.position.x, c.position.y, eraserR); eraserAt = c.position }
                            }
                        }
                        interacting = false
                        eraserAt = null
                        if (drawing && t == SketchTool.ERASER) vm.endErase()
                        if (live) vm.endLive()
                        if (planeDrag) vm.endPlaneDrag()
                        // İki parmakla kısa dokunma: geri al
                        if (multi && !multiMoved && System.currentTimeMillis() - multiStart < 350) vm.undo()
                        if (!moved && !multi) when (m) {
                            SketchMode.PLANE -> vm.snapPlaneAt(start.x, start.y, radius)
                            SketchMode.SELECT -> vm.tapSelect(start.x, start.y, radius)
                            else -> {}
                        }
                        if (drawing && t == SketchTool.PEN && !multi && xy.size >= 4) {
                            val anyStylus = pressures.any { it != 1f }
                            vm.finishStroke(xy.toFloatArray(), if (anyStylus) pressures.toFloatArray() else null, straight)
                        }
                        drawPath.clear(); straight = false
                    }
                },
        ) {
            val view = vm.currentView() ?: return@Canvas
            val m = shown ?: return@Canvas
            if (mode == SketchMode.DRAW || mode == SketchMode.PLANE) drawPlane(view, plane, vm.planeHalf(), colors.accent, mode == SketchMode.PLANE)
            if (selection.isNotEmpty()) drawSelectedStrokes(m, view, selection, colors.accent)
            if (drawPath.size >= 2) {
                val path = Path().apply { moveTo(drawPath[0].x, drawPath[0].y); for (i in 1 until drawPath.size) lineTo(drawPath[i].x, drawPath[i].y) }
                drawPath(path, Color(color), style = Stroke(width = sizePx, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            eraserAt?.let { p ->
                val r = eraserDp.dp.toPx()
                drawCircle(Color.White, r, p, alpha = 0.5f)
                drawCircle(Color(0xFFE53935), r, p, style = Stroke(width = 2.dp.toPx()))
            }
            // Eksen göstergesi (sol alt)
            val o = Offset(32.dp.toPx(), this.size.height - 32.dp.toPx())
            val len = 20.dp.toPx()
            listOf(V3(1f, 0f, 0f) to Color(0xFFE53935), V3(0f, 1f, 0f) to Color(0xFF43A047), V3(0f, 0f, 1f) to Color(0xFF1E88E5)).forEach { (a, col) ->
                drawLine(col, o, o + Offset(a dot view.right, a dot view.down) * len, 3.dp.toPx(), StrokeCap.Round)
            }
        }
    }
}

/** Çizim düzlemi: yarı saydam dolgu, ızgara ve kenar; yatıksa uyarı rengi. Düzlem modunda merkez tutamacı. */
private fun DrawScope.drawPlane(view: View3, plane: Plane3, half: Float, accent: Color, handles: Boolean) {
    val tilted = PlaneSnap.tooTilted(plane, view)
    val col = if (tilted) Color(0xFFFF8F00) else accent
    val s = FloatArray(4)
    fun p(w: V3): Offset? { view.project(w.x, w.y, w.z, s); return if (s[3] > 0f) Offset(s[0], s[1]) else null }
    val corners = listOf(-1f to -1f, 1f to -1f, 1f to 1f, -1f to 1f).map { (a, b) -> p(plane.world(a * half, b * half)) ?: return }
    val path = Path().apply { moveTo(corners[0].x, corners[0].y); corners.drop(1).forEach { lineTo(it.x, it.y) }; close() }
    drawPath(path, col.copy(alpha = 0.07f))
    val step = when { half > 6f -> 1f; half > 2.5f -> 0.5f; else -> 0.25f }
    for ((a, b) in PlaneSnap.grid(plane, half, step)) {
        val pa = p(a) ?: continue; val pb = p(b) ?: continue
        drawLine(col.copy(alpha = 0.22f), pa, pb, 1.dp.toPx())
    }
    drawPath(path, col.copy(alpha = 0.8f), style = Stroke(width = 2.dp.toPx()))
    if (handles) {
        val c = p(plane.origin) ?: return
        val n = p(plane.origin + plane.normal * (half * 0.35f))
        if (n != null) drawLine(col, c, n, 3.dp.toPx(), StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 5.dp.toPx())))
        drawCircle(Color.White, 13.dp.toPx(), c)
        drawCircle(col, 10.dp.toPx(), c)
    }
}

private fun DrawScope.drawSelectedStrokes(m: Model3D, view: View3, selection: Set<String>, accent: Color) {
    val s = FloatArray(4)
    for (st in m.strokes) {
        if (st.id !in selection || st.count < 2) continue
        val path = Path()
        var started = false
        for (i in 0 until st.count) {
            view.project(st.points[3 * i], st.points[3 * i + 1], st.points[3 * i + 2], s)
            if (s[3] <= 0f) { started = false; continue }
            if (!started) { path.moveTo(s[0], s[1]); started = true } else path.lineTo(s[0], s[1])
        }
        drawPath(path, accent.copy(alpha = 0.45f), style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun GpuSketch(vm: Sketch3DViewModel, model: Model3D, selection: Set<String>, quality: Int, tick: Int, clear: Color) {
    val density = LocalDensity.current.density
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val samples = when (quality) { Model3DPrefs.QUALITY_HIGH -> 4; Model3DPrefs.QUALITY_BALANCED -> 2; else -> 0 }
    val scale = if (quality == Model3DPrefs.QUALITY_FAST) 0.75f else 1f
    key(quality) {
        val holder = remember { arrayOfNulls<Model3DGlView>(1) }
        AndroidView(
            factory = { ctx -> Model3DGlView(ctx, samples, scale, vm::gpuFailed, vm::gpuName).also { holder[0] = it } },
            modifier = Modifier.fillMaxSize(),
            update = { v ->
                v.submit(
                    Model3DGlView.Scene(
                        model,
                        GlOptions(
                            textures = vm::texture,
                            clearColor = clear.toArgb(),
                            selected = selection.filter { id -> model.parts.any { it.id == id } }.toSet(),
                            primary = selection.firstOrNull { id -> model.parts.any { it.id == id } },
                            lineScale = max(1f, density * 0.6f) * scale,
                        ),
                    ),
                )
                if (tick < 0) v.requestRender()
            },
        )
        DisposableEffect(lifecycle) {
            val observer = LifecycleEventObserver { _, e ->
                when (e) {
                    Lifecycle.Event.ON_PAUSE -> holder[0]?.onPause()
                    Lifecycle.Event.ON_RESUME -> holder[0]?.onResume()
                    else -> {}
                }
            }
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer) }
        }
    }
}

private const val ORBIT = 0.4f
private const val HOLD_MS = 450L
