package com.karapp.drawguidance.ui.screens.model3d

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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.karapp.drawguidance.content.model3d.Model3D
import com.karapp.drawguidance.content.model3d.Pick3
import com.karapp.drawguidance.content.model3d.RenderOptions
import com.karapp.drawguidance.content.model3d.Renderer
import com.karapp.drawguidance.content.model3d.Transform3
import com.karapp.drawguidance.content.model3d.V3
import com.karapp.drawguidance.content.model3d.View3
import com.karapp.drawguidance.content.model3d.gl.GlOptions
import com.karapp.drawguidance.data.settings.Model3DPrefs
import com.karapp.drawguidance.ui.screens.model3d.gl.Model3DGlView
import com.karapp.drawguidance.ui.theme.NeuTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/** Yazılım çizicisine çizim isteği. */
private data class RenderRequest(val model: Model3D?, val selection: Selection3, val tool: Tool3, val size: IntSize, val interacting: Boolean, val quality: Int, val tick: Int)

/**
 * 3B sahne görünümü. v24: sahne ekran kartıyla (OpenGL ES 2.0, [Model3DGlView]) çizilir: parçaların ağları bir kez
 * yüklenir, taşıma/döndürme/ölçekleme yalnız matrisi değiştirir, kare yalnız sahne değişince çizilir. Ekran kartı
 * yoksa (ya da Ayarlar'dan kapatıldıysa) yazılım çizicisi arka planda çizer (etkileşim sürerken düşük çözünürlükte ve
 * gölgesiz). Dokunuşlar ve işaretler (noktalar, seçili kenarlar, eksen göstergesi) üstteki katmandadır; dokunuşlar
 * görünümün izdüşümüyle ([Pick3]) parçaya, yüze, kenara ve noktaya çevrilir. İki parmak her araçta sahneyi döndürür
 * ve yakınlaştırır (Döndür aracında tek parmak döndürür, iki parmak kaydırır).
 */
@Composable
fun Model3DViewport(vm: Model3DEditorViewModel, model: Model3D?, selection: Selection3, tool: Tool3, modifier: Modifier = Modifier) {
    val colors = NeuTheme.colors
    val gpu by vm.gpu.collectAsState()
    val quality by vm.quality.collectAsState()
    val tick by vm.renderTick.collectAsState()
    var size by remember { mutableStateOf(IntSize.Zero) }
    var interacting by remember { mutableStateOf(false) }
    val drawPath = remember { mutableStateListOf<Offset>() }
    // v26 silgi izi (ekranda gösterilir) ve parmağın yeri
    val erasePath = remember { mutableStateListOf<Offset>() }
    val eraserDp by vm.eraserSize.collectAsState()
    val textMeasurer = rememberTextMeasurer()
    val currentTool by rememberUpdatedState(tool)
    val bgColor = colors.background
    // v25 işaretler: İşlemler panelinde parçalama düzlemi ve delik çemberi, Yüz aracında delik önizlemesi
    val panel by vm.panel.collectAsState()
    val op by vm.op.collectAsState()
    val slice by vm.slice.collectAsState()
    val holeRatio by vm.holeRatio.collectAsState()
    val busy by vm.busy.collectAsState()

    Box(modifier.fillMaxSize().onSizeChanged { size = it; vm.setViewport(it.width, it.height) }) {
        if (gpu && model != null) {
            GpuScene(vm, model, selection, tool, quality, tick, bgColor)
        } else {
            // Yazılım çizici: arka planda çizilip ekrana ölçeklenir
            var image by remember { mutableStateOf<ImageBitmap?>(null) }
            LaunchedEffect(vm) {
                combine(vm.model, vm.selection, vm.tool, vm.renderTick, snapshotFlow { Triple(size, interacting, quality) }) { m, sel, t, tk, si ->
                    RenderRequest(m, sel, t, si.first, si.second, si.third, tk)
                }.collectLatest { req ->
                    val m = req.model ?: return@collectLatest
                    if (req.size.width <= 0 || req.size.height <= 0) return@collectLatest
                    val target = when (req.quality) {
                        Model3DPrefs.QUALITY_HIGH -> if (req.interacting) 520f else 1000f
                        Model3DPrefs.QUALITY_BALANCED -> if (req.interacting) 420f else 760f
                        else -> if (req.interacting) 320f else 560f
                    }
                    // v25 giriş düzeyi cihazlarda daha düşük çözünürlük
                    val k = min(1f, target * Budget3D.softwareScale / max(req.size.width, req.size.height))
                    val w = (req.size.width * k).toInt().coerceAtLeast(16)
                    val h = (req.size.height * k).toInt().coerceAtLeast(16)
                    val bmp = withContext(Dispatchers.Default) {
                        val frame = Renderer.render(
                            m, View3.editor(m.camera, w, h),
                            RenderOptions(
                                textures = vm::texture,
                                supersample = if (req.interacting || req.quality == Model3DPrefs.QUALITY_FAST) 1 else 2,
                                bilinear = !req.interacting,
                                // Sürerken gölge çizilmez (en pahalı adım)
                                shadow = if (req.interacting) false else null,
                                selectedPart = req.selection.primary,
                                selectedParts = req.selection.parts.toSet(),
                                selectedFaces = if (req.tool == Tool3.FACE || req.tool == Tool3.PAINT) req.selection.faces else emptySet(),
                                wireframe = req.tool == Tool3.EDGE || req.tool == Tool3.POINT,
                            ),
                        )
                        Bitmap.createBitmap(frame.color, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
                    }
                    image = bmp
                }
            }
            Canvas(Modifier.fillMaxSize()) {
                val bg = model?.scene?.background ?: 0
                if (bg != 0) drawRect(Color(bg))
                else drawRect(Brush.verticalGradient(listOf(bgColor, bgColor.copy(alpha = 0.6f).compositeOverWhite())))
                image?.let { img -> drawImage(img, dstOffset = IntOffset.Zero, dstSize = IntSize(size.width, size.height), filterQuality = FilterQuality.Low) }
            }
        }

        // İşaretler ve dokunuşlar
        Canvas(
            Modifier
                .fillMaxSize()
                .testTag("model3d_viewport")
                .pointerInput(vm) {
                    val slop = viewConfiguration.touchSlop
                    val longPress = viewConfiguration.longPressTimeoutMillis
                    val radius = 22.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val t = currentTool
                        val start = down.position
                        var prev = start
                        var moved = false
                        var multi = false
                        var longFired = false
                        var longTried = false
                        var live = false
                        var vertexDrag = false
                        var orbitDrag = false
                        interacting = true
                        drawPath.clear()
                        val worldPts = ArrayList<Float>()
                        // v25 kalem basıncı (yalnız kalemle; parmakta basınç güvenilmez)
                        val pressures = ArrayList<Float>()
                        var stylus = down.type == PointerType.Stylus
                        val plane = if (t == Tool3.DRAW) vm.drawPlane() else null
                        // v26 parçanın üstünden başlayan sürükleme taşır/çevirir/ölçekler, boşluktan başlayan sahneyi döndürür
                        val grabbed = (t == Tool3.MOVE || t == Tool3.ROTATE || t == Tool3.SCALE) && vm.grabAt(start.x, start.y)
                        // v26 silgi: yarıçap (piksel) ve iz; sürerken parça parça uygulanır
                        val eraserR = vm.eraserSize.value * density
                        val ex = ArrayList<Float>(); val ey = ArrayList<Float>()
                        var applied = 0
                        var erasing = false
                        erasePath.clear()
                        fun eraseTo(p: Offset, final: Boolean) {
                            if (t != Tool3.ERASE) return
                            ex += p.x; ey += p.y
                            erasePath += p
                            if (!erasing) { vm.beginErase(); erasing = true }
                            // Son uygulamadan beri yeterince ilerlediyse (ya da bitişte) yeni parça silinir
                            val lastX = ex[maxOf(applied - 1, 0)]; val lastY = ey[maxOf(applied - 1, 0)]
                            val far = (p.x - lastX) * (p.x - lastX) + (p.y - lastY) * (p.y - lastY) > (eraserR * 0.5f) * (eraserR * 0.5f)
                            if (far || final || applied == 0) {
                                val from = maxOf(applied - 1, 0)
                                vm.eraseAlong(ex.subList(from, ex.size).toFloatArray(), ey.subList(from, ey.size).toFloatArray(), eraserR)
                                applied = ex.size
                            }
                        }
                        fun finishErase() {
                            if (!erasing) return
                            erasing = false
                            vm.endErase(ex.toFloatArray(), ey.toFloatArray(), eraserR)
                            ex.clear(); ey.clear(); applied = 0
                        }
                        fun addDrawPoint(p: Offset, pressure: Float) {
                            val pl = plane ?: return
                            val hit = vm.planePoint(pl, p.x, p.y) ?: return
                            worldPts += hit.x; worldPts += hit.y; worldPts += hit.z
                            pressures += pressure
                            drawPath += p
                        }
                        if (t == Tool3.DRAW) addDrawPoint(start, down.pressure)
                        // v27 Boya: parmak inince boyanacak yer (yüz, yüzey, parça ya da tümü) vurgulanır
                        if (t == Tool3.PAINT) vm.previewPaint(start.x, start.y)
                        var lastPaint = start
                        while (true) {
                            // Nokta/Kenar: kenarı basılı tutmak nokta ekler
                            val e = if (t == Tool3.EDGE && !moved && !multi && !longTried) {
                                withTimeoutOrNull(longPress) { awaitPointerEvent() } ?: run {
                                    longTried = true
                                    longFired = vm.addPointAt(start.x, start.y, radius)
                                    null
                                }
                            } else awaitPointerEvent()
                            if (e == null) continue
                            val pressed = e.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            if (pressed.size >= 2) {
                                if (!multi && live) { vm.endLive(); live = false }
                                if (!multi && vertexDrag) { vm.endVertexDrag(); vertexDrag = false }
                                if (!multi) { finishErase(); erasePath.clear() }
                                pressures.clear()
                                multi = true
                                val zoom = e.calculateZoom()
                                val pan = e.calculatePan()
                                // v26 tek parmağın boşlukta sahneyi döndürdüğü modlarda (Seç, Ekle, Düzenle) iki parmak kaydırır;
                                // tek parmağın çizdiği/boyadığı modlarda (Çiz, Boya) iki parmak döndürür
                                val pans = t == Tool3.VIEW || Mode3.of(t) == Mode3.SELECT || Mode3.of(t) == Mode3.EDIT
                                if (pans) vm.pan(pan.x, pan.y) else vm.orbit(-pan.x * ORBIT, pan.y * ORBIT)
                                if (zoom != 1f) vm.zoom(zoom)
                                e.changes.forEach { it.consume() }
                                drawPath.clear(); worldPts.clear()
                                continue
                            }
                            if (multi) continue
                            val c = pressed[0]
                            val d = c.position - prev
                            prev = c.position
                            if (!moved && (c.position - start).getDistance() > slop) {
                                moved = true
                                // Sürüklemenin ne yapacağı başta belirlenir
                                val sel = vm.selection.value
                                when (t) {
                                    Tool3.MOVE, Tool3.ROTATE, Tool3.SCALE -> if (grabbed && sel.parts.isNotEmpty()) { vm.beginLive(); live = true } else orbitDrag = true
                                    Tool3.ERASE -> eraseTo(start, false)
                                    Tool3.EDGE -> if (vm.beginVertexDrag(start.x, start.y, radius)) vertexDrag = true else orbitDrag = true
                                    // v25 Nokta: noktayı sürükler (seçim sırası korunur), boşlukta sahneyi döndürür
                                    Tool3.POINT -> if (vm.beginPointDrag(start.x, start.y, radius)) vertexDrag = true else orbitDrag = true
                                    Tool3.FACE -> if (vm.onSelectedFace(start.x, start.y)) { vm.beginLive(); live = true } else orbitDrag = true
                                    Tool3.PAINT -> { vm.beginLive(); live = true }
                                    Tool3.VIEW -> orbitDrag = true
                                    else -> {}
                                }
                            }
                            if (!moved) continue
                            c.consume()
                            if (orbitDrag) { vm.orbit(-d.x * ORBIT, d.y * ORBIT); continue }
                            when (t) {
                                Tool3.MOVE -> vm.dragDelta(d.x, d.y)?.let { vm.moveSelected(it) }
                                Tool3.ROTATE -> vm.rotateByDrag(d.x, d.y)
                                Tool3.SCALE -> vm.scaleByDrag(d.x, d.y)
                                Tool3.EDGE, Tool3.POINT -> if (vertexDrag) vm.dragVerticesBy(d.x, d.y)
                                Tool3.FACE -> vm.dragDelta(d.x, d.y)?.let { vm.moveFaces(it) }
                                Tool3.DRAW -> {
                                    if (c.type == PointerType.Stylus) stylus = true
                                    addDrawPoint(c.position, c.pressure)
                                }
                                Tool3.PAINT -> if ((c.position - lastPaint).getDistance() > 14f) {
                                    lastPaint = c.position
                                    vm.tapAt(c.position.x, c.position.y, radius)
                                }
                                Tool3.ERASE -> eraseTo(c.position, false)
                                Tool3.VIEW -> {}
                            }
                        }
                        interacting = false
                        // v26 silgi: dokunuşta o noktayı siler; bitişte iz boyunca kalanlar (şişir/prizma) oyulur
                        if (t == Tool3.ERASE && !multi) {
                            if (!moved) eraseTo(start, true) else if (ex.isNotEmpty()) eraseTo(Offset(ex.last(), ey.last()), true)
                            finishErase()
                        }
                        erasePath.clear()
                        if (vertexDrag) vm.endVertexDrag()
                        if (live) vm.endLive()
                        vm.endCoalesce()
                        if (!moved && !multi && !longFired && t != Tool3.ERASE) vm.tapAt(start.x, start.y, radius)
                        // v25 3B fırça: çizgi ya da izden cisim (tür Fırça panelinde)
                        if (t == Tool3.DRAW && worldPts.size >= 6 && !multi && plane != null) {
                            vm.finishBrush(worldPts.toFloatArray(), if (stylus && pressures.size * 3 == worldPts.size) pressures.toFloatArray() else null, plane.second)
                        }
                        drawPath.clear()
                    }
                },
        ) {
            val m = model ?: return@Canvas
            val view = vm.currentView() ?: return@Canvas
            if (gpu) maskCorners(bgColor, 18.dp.toPx())
            if (tool == Tool3.EDGE || tool == Tool3.POINT) drawEdgeMarks(m, view, selection, colors.accent)
            if (tool == Tool3.POINT) drawPointOrder(m, view, selection, colors.accent, textMeasurer)
            // v25 delik önizlemesi (Yüz aracında Delik aç) ve İşlemler panelinde parçalama düzlemi ile delik çemberi
            if (op?.kind == OpKind.HOLE) vm.holePreview()?.let { (c, n, r) -> drawCircle3(view, c, n, r, Color(0xFFFF7043), filled = true) }
            if (panel == Panel3.OPS && busy == null && selection.primary != null) {
                vm.slicePlane()?.let { (p, n) -> drawPlane3(view, p, n, slicePlaneSize(m, selection), colors.accent) }
                if (holeRatio >= 0f) vm.panelHoleFrame()?.let { (c, n, r) -> drawCircle3(view, c, n, r, Color(0xFFFF7043), filled = false) }
            }
            // v26 silgi izi ve silginin ucu
            if (erasePath.isNotEmpty()) {
                val r = eraserDp.dp.toPx()
                if (erasePath.size >= 2) {
                    val path = Path().apply {
                        moveTo(erasePath[0].x, erasePath[0].y)
                        for (i in 1 until erasePath.size) lineTo(erasePath[i].x, erasePath[i].y)
                    }
                    drawPath(path, Color(0x33E53935), style = Stroke(width = r * 2f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                }
                val last = erasePath.last()
                drawCircle(Color.White, r, last, alpha = 0.5f)
                drawCircle(Color(0xFFE53935), r, last, style = Stroke(width = 2.dp.toPx()))
            }
            // Çizilen çizginin önizlemesi
            if (drawPath.size >= 2) {
                val path = Path().apply {
                    moveTo(drawPath[0].x, drawPath[0].y)
                    for (i in 1 until drawPath.size) lineTo(drawPath[i].x, drawPath[i].y)
                }
                drawPath(path, Color(vm.brushColor.value), style = Stroke(width = vm.brushWidth.value, cap = StrokeCap.Round))
            }
            // Eksen göstergesi (sol alt): X kırmızı, Y yeşil, Z mavi
            val o = Offset(36.dp.toPx(), size.height - 36.dp.toPx())
            val len = 24.dp.toPx()
            listOf(Triple(V3(1f, 0f, 0f), Color(0xFFE53935), "X"), Triple(V3(0f, 1f, 0f), Color(0xFF43A047), "Y"), Triple(V3(0f, 0f, 1f), Color(0xFF1E88E5), "Z"))
                .forEach { (a, col, label) ->
                    val dx = a dot view.right; val dy = a dot view.down
                    val end = o + Offset(dx, dy) * len
                    drawLine(col, o, end, 3.dp.toPx(), StrokeCap.Round)
                    drawText(textMeasurer, label, end + Offset(dx, dy) * 8.dp.toPx() - Offset(4.dp.toPx(), 7.dp.toPx()), TextStyle(color = col, fontSize = 11.sp))
                }
        }
    }
}

/** Ekran kartı görünümü (kalite değişince yeniden kurulur: kenar yumuşatma yapılandırması kurulurken seçilir). */
@Composable
private fun GpuScene(vm: Model3DEditorViewModel, model: Model3D, selection: Selection3, tool: Tool3, quality: Int, tick: Int, clear: Color) {
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
                            selected = selection.parts.toSet(),
                            primary = selection.primary,
                            selectedFaces = if (tool == Tool3.FACE || tool == Tool3.PAINT) selection.faces else emptySet(),
                            wireframe = tool == Tool3.EDGE || tool == Tool3.POINT,
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

/** Ekran kartı yüzeyi yuvarlak köşeye kırpılmaz: köşeler arka plan rengiyle örtülür. */
private fun DrawScope.maskCorners(color: Color, r: Float) {
    val p = Path().apply {
        fillType = PathFillType.EvenOdd
        addRect(androidx.compose.ui.geometry.Rect(Offset.Zero, size))
        addRoundRect(RoundRect(androidx.compose.ui.geometry.Rect(Offset.Zero, size), CornerRadius(r, r)))
    }
    drawPath(p, color)
}

/**
 * Nokta/Kenar aracının işaretleri: seçili kenarlar kalın vurgu çizgisi; noktalar (az noktalı parçada bakana dönük tüm
 * noktalar, çok noktalıda yalnız seçili kenarların uçları ve seçili noktalar).
 */
private fun DrawScope.drawEdgeMarks(m: Model3D, view: View3, selection: Selection3, accent: Color) {
    val part = selection.primary?.let { m.part(it) } ?: return
    val mesh = part.mesh
    val w = Pick3.worldVertices(part)
    val s = FloatArray(4)
    fun screen(i: Int): Offset? {
        view.project(w[3 * i], w[3 * i + 1], w[3 * i + 2], s)
        return if (s[3] > 0f) Offset(s[0], s[1]) else null
    }
    for (e in selection.edges) {
        if (e.a >= mesh.vertexCount || e.b >= mesh.vertexCount) continue
        val a = screen(e.a) ?: continue; val b = screen(e.b) ?: continue
        drawLine(Color.White, a, b, 7.dp.toPx(), StrokeCap.Round)
        drawLine(accent, a, b, 4.dp.toPx(), StrokeCap.Round)
    }
    val shown = HashSet<Int>()
    selection.edges.forEach { shown += it.a; shown += it.b }
    shown += selection.vertices
    if (mesh.vertexCount <= MAX_DOTS) {
        // Bakana dönük yüzlerin noktaları
        val t = Transform3.of(part)
        val fn = mesh.faceNormals
        val n = FloatArray(3)
        val eye = if (view.perspective) view.eye else null
        val tv = view.toViewer
        for (f in 0 until mesh.faceCount) {
            t.normal(fn[3 * f], fn[3 * f + 1], fn[3 * f + 2], n, 0)
            val v0 = mesh.faces[f].firstOrNull() ?: continue
            val facing = if (eye != null) (eye.x - w[3 * v0]) * n[0] + (eye.y - w[3 * v0 + 1]) * n[1] + (eye.z - w[3 * v0 + 2]) * n[2] > 0f
            else n[0] * tv.x + n[1] * tv.y + n[2] * tv.z > 0f
            if (facing) mesh.faces[f].forEach { shown += it }
        }
    }
    // Başka parçanın arkasında kalan noktalar soluk (az noktada ışınla sınanır)
    val partIndex = m.parts.indexOf(part)
    val checkOcclusion = m.parts.size > 1 && shown.size.toLong() * m.visibleTriangles < 400_000L
    for (i in shown) {
        if (i >= mesh.vertexCount) continue
        val p = screen(i) ?: continue
        val chosen = i in selection.vertices
        val visible = !checkOcclusion || Pick3.visible(m, view, V3(w[3 * i], w[3 * i + 1], w[3 * i + 2]), partIndex)
        val a = if (visible) 1f else 0.35f
        drawCircle(Color.White, if (chosen) 8.dp.toPx() else 5.dp.toPx(), p, alpha = a)
        drawCircle(if (chosen) accent else Color(0xFF37474F), if (chosen) 6.dp.toPx() else 3.5f.dp.toPx(), p, alpha = a)
    }
}

/**
 * v25 Nokta aracında seçili noktaların sırası: numaralar ve sırayla birleştiren kesik çizgi (Birleştir bu sırayla
 * kenar oluşturur, Kes son noktayı ilkine bağlayarak halkayı kapatır).
 */
private fun DrawScope.drawPointOrder(m: Model3D, view: View3, selection: Selection3, accent: Color, measurer: androidx.compose.ui.text.TextMeasurer) {
    val part = selection.primary?.let { m.part(it) } ?: return
    val order = selection.vertices.toList().filter { it < part.mesh.vertexCount }
    if (order.isEmpty()) return
    val w = Pick3.worldVertices(part)
    val s = FloatArray(4)
    val pts = order.map { i ->
        view.project(w[3 * i], w[3 * i + 1], w[3 * i + 2], s)
        if (s[3] > 0f) Offset(s[0], s[1]) else null
    }
    val dash = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 6.dp.toPx()))
    for (k in 0 until pts.size - 1) {
        val a = pts[k] ?: continue; val b = pts[k + 1] ?: continue
        drawLine(accent, a, b, 2.5f.dp.toPx(), StrokeCap.Round, pathEffect = dash)
    }
    if (pts.size >= 3) {
        val a = pts.last(); val b = pts.first()
        if (a != null && b != null) drawLine(accent.copy(alpha = 0.45f), a, b, 2.dp.toPx(), StrokeCap.Round, pathEffect = dash)
    }
    pts.forEachIndexed { k, p ->
        p ?: return@forEachIndexed
        val label = "${k + 1}"
        val layout = measurer.measure(label, TextStyle(color = Color.White, fontSize = 10.sp))
        val at = p + Offset(9.dp.toPx(), -20.dp.toPx())
        drawCircle(accent, 8.dp.toPx(), at + Offset(layout.size.width / 2f, layout.size.height / 2f))
        drawText(layout, topLeft = at)
    }
}

/** Parçalama düzleminin çizim boyu: asıl parçanın sınır kutusundan biraz büyük. */
private fun slicePlaneSize(m: Model3D, selection: Selection3): Float =
    selection.primary?.let { m.part(it) }?.let { com.karapp.drawguidance.content.model3d.ModelEdit.worldBounds(it).radius * 1.25f } ?: 1f

/** Dünyadaki kare düzlem (merkez [p], normal [n], yarı kenar [half]): yarı saydam dolgu ve kenar çizgisi. */
private fun DrawScope.drawPlane3(view: View3, p: V3, n: V3, half: Float, color: Color) {
    val (t1, t2) = tangents(n)
    val s = FloatArray(4)
    val corners = listOf(-1f to -1f, 1f to -1f, 1f to 1f, -1f to 1f).map { (a, b) ->
        val w = p + t1 * (a * half) + t2 * (b * half)
        view.project(w.x, w.y, w.z, s)
        if (s[3] <= 0f) return
        Offset(s[0], s[1])
    }
    val path = Path().apply { moveTo(corners[0].x, corners[0].y); corners.drop(1).forEach { lineTo(it.x, it.y) }; close() }
    drawPath(path, color.copy(alpha = 0.16f))
    drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
}

/** Dünyadaki çember (delik önizlemesi): merkez [c], eksen [n], yarıçap [r]. */
private fun DrawScope.drawCircle3(view: View3, c: V3, n: V3, r: Float, color: Color, filled: Boolean) {
    val (t1, t2) = tangents(n)
    val s = FloatArray(4)
    val path = Path()
    for (k in 0..40) {
        val a = k / 40f * 2f * Math.PI.toFloat()
        val w = c + t1 * (kotlin.math.cos(a) * r) + t2 * (kotlin.math.sin(a) * r)
        view.project(w.x, w.y, w.z, s)
        if (s[3] <= 0f) return
        if (k == 0) path.moveTo(s[0], s[1]) else path.lineTo(s[0], s[1])
    }
    path.close()
    if (filled) drawPath(path, color.copy(alpha = 0.25f))
    drawPath(path, color, style = Stroke(width = 2.5f.dp.toPx(), pathEffect = if (filled) null else androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 5.dp.toPx()))))
}

private fun tangents(n: V3): Pair<V3, V3> {
    val nn = n.normalized()
    val ref = if (kotlin.math.abs(nn.y) < 0.9f) V3.UP else V3(1f, 0f, 0f)
    val t1 = (ref - nn * (ref dot nn)).normalized()
    return t1 to (nn cross t1).normalized()
}

private const val ORBIT = 0.4f
private const val MAX_DOTS = 160

private fun Color.compositeOverWhite(): Color = Color(
    red = red * alpha + (1f - alpha), green = green * alpha + (1f - alpha), blue = blue * alpha + (1f - alpha), alpha = 1f,
)
