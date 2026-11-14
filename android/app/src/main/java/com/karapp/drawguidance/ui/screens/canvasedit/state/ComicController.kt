package com.karapp.drawguidance.ui.screens.canvasedit.state

import androidx.compose.ui.geometry.Offset
import com.karapp.drawguidance.content.comic.ComicLines
import com.karapp.drawguidance.content.comic.ComicLinesKind
import com.karapp.drawguidance.content.comic.ComicTemplates
import com.karapp.drawguidance.content.comic.PanelPage
import com.karapp.drawguidance.content.comic.ComicSfx
import com.karapp.drawguidance.content.comic.SfxPreset
import com.karapp.drawguidance.data.local.ContentCodec
import com.karapp.drawguidance.data.model.BalloonSpec
import com.karapp.drawguidance.data.model.BalloonStyle
import com.karapp.drawguidance.data.model.BalloonTail
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.FontGroup
import com.karapp.drawguidance.data.model.GradientSpec
import com.karapp.drawguidance.data.model.ShapeStyle
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.data.model.TextAlignKind
import com.karapp.drawguidance.data.model.TextBox
import com.karapp.drawguidance.data.model.TextEffects
import com.karapp.drawguidance.data.model.TextFont
import com.karapp.drawguidance.data.model.TextSpec
import com.karapp.drawguidance.ui.drawing.BalloonRenderer
import com.karapp.drawguidance.ui.drawing.ShapeFactory
import com.karapp.drawguidance.ui.drawing.TailDirection
import com.karapp.drawguidance.ui.drawing.TextRenderer
import com.karapp.drawguidance.ui.screens.canvasedit.EditMode
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementOps
import java.util.UUID
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * v27 çizgi roman tuvali: konuşma balonları (yazı + balon biçimi + kuyruklar), sayfa panel düzenleri, efekt yazıları
 * (ses efektleri: kenarlık, gölge, gradyan) ve hız/odak/patlama çizgileri. Hepsi sıradan tuval elemanlarıdır:
 * taşınır, ölçeklenir, döndürülür, geri alınır.
 */
class ComicController(
    private val store: EditorStore,
    private val host: EditorHost,
    private val texts: TextController,
    private val brush: () -> BrushController,
) {
    private val q = ContentCodec::quantize

    /** Seçili tek konuşma balonu (yoksa null). */
    fun selectedBalloon(): Stroke? =
        store.selectedStroke { it.brushType == BrushType.TEXT && it.text?.box?.balloon != null }?.takeIf { store.selection.size == 1 }

    /**
     * Balon ekler: [style] biçiminde, [speakers] kuyruklu (0 anlatı kutusu gibi kuyruksuz), [center] çevresinde (yoksa
     * görünen sayfanın ortası). Eklenen balon seçilir. v36 [options] yazısı boşsa öntanımlı yazı hemen yazılmaya açılır;
     * yazı pencerede yazıldıysa balon o yazı, yazı tipi, boyut, renk, hizalama ve kuyrukla eklenir.
     */
    fun insertBalloon(style: BalloonStyle, speakers: Int, center: Offset?, options: BalloonTextOptions = BalloonTextOptions()) {
        val current = store.canvas.value ?: return
        val typed = options.content.isNotBlank()
        val stroke = balloonStroke(style, speakers, center, if (typed) options.content else com.karapp.drawguidance.i18n.I18n.tr(DEFAULT_TEXT), options) ?: return
        store.commit(current.strokes + stroke)
        host.setMode(EditMode.SELECT)
        store.select(setOf(stroke.id))
        // Yazı hemen yazılır (öntanımlı metin seçilip değiştirilir)
        if (!typed) texts.editSelected()
    }

    /** v32 balon elemanı ([content] yazısıyla; eklenmez). Eğitim modu da kullanır. v36 [options] yazı ve kuyruk ayarları. */
    fun balloonStroke(style: BalloonStyle, speakers: Int, center: Offset?, content: String, options: BalloonTextOptions? = null): Stroke? {
        val current = store.canvas.value ?: return null
        // v29 kullanıcının son seçtiği biçimden (düzenlenen başka bir balonun biçiminden değil)
        val settings = texts.defaults.value
        val size = options?.sizePx?.coerceIn(TextController.MIN_TEXT_SIZE, TextController.MAX_TEXT_SIZE) ?: settings.sizePx
        val c = center ?: Offset(current.width / 2f, current.height / 2f)
        val width = (min(current.width, current.height) * 0.36f).coerceIn(size * 6f, size * 14f)
        val tails = when {
            style == BalloonStyle.FREE -> emptyList()
            style == BalloonStyle.NARRATION && speakers == 0 -> emptyList()
            options != null && options.customTail -> BalloonRenderer.tailsToward(options.tail, speakers, options.length, options.curve)
            // v36 telefon mesajı: kısa, kıvrık kuyruk sağ altta
            style == BalloonStyle.PHONE -> BalloonRenderer.tailsToward(TailDirection.BOTTOM_RIGHT, speakers, 0.22f, 0.7f)
            else -> BalloonRenderer.defaultTails(speakers)
        }
        val balloon = BalloonSpec(
            style = style, tails = tails,
            fillHex = when (style) { BalloonStyle.NARRATION -> "#FFF59D"; BalloonStyle.PHONE -> PHONE_FILL; else -> "#FFFFFF" },
            strokeHex = if (style == BalloonStyle.PHONE) PHONE_BORDER else "#111111",
        )
        val font = options?.font ?: settings.spec.font.takeIf { it.group == FontGroup.COMIC } ?: TextFont.COMIC_RELIEF
        val spec = TextSpec(
            content = content, font = font, align = options?.align ?: TextAlignKind.CENTER,
            box = TextBox(width = width, balloon = balloon, effects = settings.spec.box?.effects),
        )
        val (w, h) = TextRenderer.measure(spec, size)
        val points = TextRenderer.quadAt(spec, size, c.x - w / 2f, c.y - h / 2f).map { StrokePoint(q(it.x), q(it.y)) }
        val ink = options?.colorHex?.uppercase() ?: if (style == BalloonStyle.PHONE) "#FFFFFF" else "#111111"
        return Stroke(
            id = UUID.randomUUID().toString(), points = points, colorHex = ink, brushType = BrushType.TEXT,
            sizePx = size, opacity = 1f, order = current.strokes.size, text = spec,
        )
    }

    /**
     * v36 seçili balonun yazısı: içerik, yazı tipi, boyut (balonun eni de aynı oranda büyür/küçülür, satırlar
     * korunur), harf rengi ve hizalama. Verilmeyen alan değişmez; boş içerik yok sayılır.
     */
    fun updateBalloonText(
        content: String? = null, font: TextFont? = null, sizePx: Float? = null, colorHex: String? = null, align: TextAlignKind? = null,
    ) {
        val s = selectedBalloon() ?: return
        val text = s.text ?: return
        val size = sizePx?.coerceIn(TextController.MIN_TEXT_SIZE, TextController.MAX_TEXT_SIZE) ?: s.sizePx
        val k = size / s.sizePx
        val box = text.box?.let { b ->
            if (k == 1f) b else b.copy(
                width = b.width?.let { (it * k).coerceIn(TextController.MIN_BOX, TextController.MAX_BOX) },
                height = b.height?.let { (it * k).coerceIn(TextController.MIN_BOX, TextController.MAX_BOX) },
            )
        }
        val spec = text.copy(
            content = content?.takeIf { it.isNotBlank() } ?: text.content, font = font ?: text.font, align = align ?: text.align, box = box,
        )
        val ink = colorHex?.uppercase() ?: s.colorHex
        if (spec == text && size == s.sizePx && ink == s.colorHex) return
        val current = store.canvas.value ?: return
        val updated = s.copy(
            text = spec, sizePx = size, colorHex = ink,
            points = TextRenderer.refit(s, spec, size).map { StrokePoint(q(it.x), q(it.y)) },
        )
        // Yazarken ve kaydırırken her alan tek geri alma adımı
        val field = when {
            content != null -> "content"; font != null -> "font"; sizePx != null -> "size"; colorHex != null -> "color"; else -> "align"
        }
        store.commit(current.strokes.map { if (it.id == s.id) updated else it }, coalesce = "balloon-text-$field-${s.id}")
    }

    /**
     * v36 seçili balonun kuyrukları [dir] yönünde, [length] uzaklıkta ve [curve] eğrilikte yeniden dizilir (kuyruk sayısı
     * korunur; kuyruksuz balona bir kuyruk eklenir). Serbest yazının kuyruğu olmaz.
     */
    fun setBalloonTails(dir: TailDirection, length: Float, curve: Float) {
        val s = selectedBalloon() ?: return
        val text = s.text ?: return
        val box = text.box ?: return
        val b = box.balloon ?: return
        if (b.style == BalloonStyle.FREE) return
        val tails = BalloonRenderer.tailsToward(dir, b.tails.size.coerceAtLeast(1), length, curve.coerceIn(-1f, 1f))
        if (tails == b.tails) return
        replaceText(s, text.copy(box = box.copy(balloon = b.copy(tails = tails))), "balloon-tail-${s.id}")
    }

    /** Seçili balonun biçimi/renkleri değişir (kuyruklar korunur; kuyruk sayısı [speakers] verilirse yeniden dizilir). */
    fun updateBalloon(style: BalloonStyle? = null, speakers: Int? = null, fillHex: String? = null, strokeHex: String? = null) {
        val s = selectedBalloon() ?: return
        val text = s.text ?: return
        val box = text.box ?: return
        val b = box.balloon ?: return
        var tails = b.tails
        if (speakers != null && speakers != tails.size) {
            tails = if (speakers < tails.size) tails.take(speakers) else tails + BalloonRenderer.defaultTails(speakers).drop(tails.size)
        }
        val nb = b.copy(style = style ?: b.style, tails = tails, fillHex = fillHex ?: b.fillHex, strokeHex = strokeHex ?: b.strokeHex)
        if (nb == b) return
        replaceText(s, text.copy(box = box.copy(balloon = nb)), "balloon-${s.id}")
    }

    /** Kuyruk ucu yeni yere (kutuya göre oran) taşındı. */
    fun withTail(stroke: Stroke, index: Int, tail: BalloonTail): Stroke? {
        val text = stroke.text ?: return null
        val box = text.box ?: return null
        val b = box.balloon ?: return null
        if (index !in b.tails.indices) return null
        val tails = b.tails.toMutableList().also { it[index] = tail }
        return stroke.copy(text = text.copy(box = box.copy(balloon = b.copy(tails = tails))))
    }

    private fun replaceText(s: Stroke, spec: TextSpec, coalesce: String?) {
        val current = store.canvas.value ?: return
        val updated = s.copy(text = spec, points = TextRenderer.refit(s, spec, s.sizePx).map { StrokePoint(q(it.x), q(it.y)) })
        store.commit(current.strokes.map { if (it.id == s.id) updated else it }, coalesce = coalesce)
    }

    /**
     * v36 şablon ekler: şablonun panelleri ayrı elemanlar olarak sayfaya kurulur (sayfada şablon varsa onun yerine,
     * aynı çizim sırasında; içindeki çizimler yerinde kalır). Paneller seçili olur; eklenen panel kimlikleri döner.
     */
    fun insertTemplate(templateId: String): Set<String> {
        val current = store.canvas.value ?: return emptySet()
        val template = ComicTemplates.byId(templateId) ?: return emptySet()
        val w = current.width.toFloat(); val h = current.height.toFloat()
        val style = ShapeStyle(strokeColorHex = PANEL_INK, strokeWidth = panelLine(w, h) * template.line)
        val existing = PanelSets.find(current.strokes)
        val old = existing?.strokeIds().orEmpty()
        val at = current.strokes.indexOfFirst { it.id in old }.takeIf { it >= 0 }
        val rest = current.strokes.filter { it.id !in old }
        val added = PanelSets.build(template.page, UUID.randomUUID().toString(), w, h, style, 0)
        if (added.isEmpty()) return emptySet()
        val merged = rest.toMutableList().also { it.addAll((at ?: rest.size).coerceAtMost(rest.size), added) }
        store.commit(merged.mapIndexed { i, s -> if (s.order == i) s else s.copy(order = i) })
        val ids = added.mapTo(HashSet()) { it.id }
        host.inserted(ids)
        return ids
    }

    /** v36 sayfadaki şablon (yoksa null). */
    fun panelSet(): PanelSets.Found? = store.canvas.value?.let { PanelSets.find(it.strokes) }

    /** v37 kaydırıcı sürüklenirken başlangıçtaki çizimler: her adım bu hâlden hesaplanır (küçült-büyüt kayıpsız). */
    private class PanelDrag(val key: String, val strokes: List<Stroke>, val found: PanelSets.Found) {
        var result: List<Stroke>? = null
    }
    private var panelDrag: PanelDrag? = null

    /**
     * v36 sayfadaki şablonun düzenini [edit] ile değiştirir ve panelleri yeniden kurar ([coalesce]: kaydırıcı
     * sürüklenirken tek geri alma adımı). Düzen boşalırsa paneller silinir. v37 panellerin içindeki çizimler her
     * değişiklikte (boyut, aralık, kenar boşluğu, sıra, ekleme) panelle birlikte yeni yerine taşınır ve ölçeklenir.
     * Değişiklik olmadıysa false.
     */
    fun editPanels(coalesce: String? = null, edit: (PanelPage) -> PanelPage?): Boolean {
        val current = store.canvas.value ?: return false
        val found = PanelSets.find(current.strokes) ?: return false
        val page = edit(found.page) ?: return false
        if (page == found.page) return false
        val w = current.width.toFloat(); val h = current.height.toFloat()
        // Aynı sürükleme sürüyorsa (arada başka değişiklik yoksa) başlangıç hâlinden yeniden hesaplanır
        val drag = coalesce?.let { key ->
            panelDrag?.takeIf { it.key == key && it.result === current.strokes } ?: PanelDrag(key, current.strokes, found)
        }
        panelDrag = drag
        val base = drag?.strokes ?: current.strokes
        val baseFound = drag?.found ?: found
        val updated = if (page.isEmpty) current.strokes.filter { it.id !in found.strokeIds() }
        else PanelSets.apply(base, baseFound, page, w, h, ShapeStyle(strokeColorHex = PANEL_INK, strokeWidth = panelLine(w, h)))
        store.commit(updated, coalesce = coalesce)
        drag?.result = store.canvas.value?.strokes
        return true
    }

    private fun panelLine(w: Float, h: Float) = (min(w, h) * 0.006f).coerceIn(2f, 14f)

    /**
     * Efekt yazısı ekler: [preset] yazısı, yazı tipi, gradyan dolgu, kalın kenarlık, gölge ve eğim; büyük harf. Boyu
     * normal yazının iki katından fazla.
     */
    fun insertSfx(preset: SfxPreset, center: Offset?) {
        val current = store.canvas.value ?: return
        val stroke = sfxStroke(preset, center) ?: return
        store.commit(current.strokes + stroke)
        host.inserted(setOf(stroke.id))
    }

    /**
     * v36 balon penceresinden ses efekti yazısı: [options] yazısı (boşsa "BAM!") ilk hazır efektin biçimiyle (gradyan,
     * kalın kenar, gölge, eğim, büyük harf; Türkçe büyük harf kurallarıyla); yazı tipi ve renk seçildiyse onlarla. Boyu
     * seçilen yazı boyunun 2,4 katı.
     */
    fun insertSfxText(options: BalloonTextOptions, center: Offset?) {
        val current = store.canvas.value ?: return
        val base = ComicSfx.presets.first()
        val color = options.colorHex?.uppercase()
        val preset = SfxPreset(
            options.content.trim().ifEmpty { base.text }, options.font?.name ?: base.font,
            color?.let { listOf(lighter(it), it) } ?: base.colors, color?.let { darker(it) } ?: base.outline, base.shadow, base.tilt,
        )
        val stroke = sfxStroke(preset, center, options.sizePx?.let { (it * 2.4f).coerceIn(24f, 400f) }) ?: return
        store.commit(current.strokes + stroke)
        host.inserted(setOf(stroke.id))
    }

    /** v32 efekt yazısı elemanı (eklenmez). [size] verilmezse yazı ayarlarından. Eğitim modu da kullanır. */
    fun sfxStroke(preset: SfxPreset, center: Offset?, size0: Float? = null): Stroke? {
        val current = store.canvas.value ?: return null
        val size = size0 ?: (texts.defaults.value.sizePx * 2.4f).coerceIn(24f, 400f)
        val c = center ?: Offset(current.width / 2f, current.height / 2f)
        val font = runCatching { TextFont.valueOf(preset.font) }.getOrDefault(TextFont.BANGERS)
        val shadow = preset.shadow.takeIf { it.length == 7 || !it.endsWith("00000000") }
        val spec = TextSpec(
            content = com.karapp.drawguidance.i18n.I18n.tr(preset.text), font = font, align = TextAlignKind.CENTER,
            outlineColorHex = preset.outline, outlineWidth = size * 0.07f,
            box = TextBox(effects = TextEffects(shadowHex = shadow, shadowDx = 0.07f, shadowDy = 0.08f, gradient = GradientSpec(preset.colors, 90f), caps = true, letterSpacing = 0.02f)),
        )
        val (w, h) = TextRenderer.measure(spec, size)
        val a = Math.toRadians(preset.tilt.toDouble())
        val ca = cos(a).toFloat(); val sa = sin(a).toFloat()
        fun rot(x: Float, y: Float) = StrokePoint(q(c.x + x * ca - y * sa), q(c.y + x * sa + y * ca))
        val points = listOf(rot(-w / 2, -h / 2), rot(w / 2, -h / 2), rot(w / 2, h / 2), rot(-w / 2, h / 2))
        return Stroke(
            id = UUID.randomUUID().toString(), points = points, colorHex = preset.colors.last(), brushType = BrushType.TEXT,
            sizePx = size, opacity = 1f, order = current.strokes.size, text = spec,
        )
    }

    /** v32 hız/odak çizgileri [area] alanında (eklenmez). Eğitim modu da kullanır. */
    fun linesStrokes(kind: ComicLinesKind, density: Float, area: androidx.compose.ui.geometry.Rect, ink: String): List<Stroke> {
        val current = store.canvas.value ?: return emptyList()
        val seed = current.strokes.size * 31 + kind.ordinal
        return buildLines(kind, LinesOptions(density = density, colorHex = ink), seed, floatArrayOf(area.left, area.top, area.right, area.bottom), UUID.randomUUID().toString(), ink, current.strokes.size)
    }

    /**
     * Hız/odak/patlama çizgileri: seçili eleman (ör. panel karesi) varsa onun alanına, yoksa sayfaya; tek grup (bir
     * eleman gibi taşınır ve silinir). İnce uçlu mürekkep kalemiyle. v29: üretim bilgisi her çizgide saklanır; grubun
     * sıklığı sonradan [setLinesDensity] ile değiştirilir. v36: odak noktası, yön, uzunluk, kalınlık, renk ve opaklık.
     */
    fun insertLines(kind: ComicLinesKind, density: Float = 1f) = insertLines(kind, LinesOptions(density = density))

    fun insertLines(kind: ComicLinesKind, options: LinesOptions) {
        val current = store.canvas.value ?: return
        val area = ElementOps.selectionBounds(current.strokes, store.selection)
            ?: androidx.compose.ui.geometry.Rect(0f, 0f, current.width.toFloat(), current.height.toFloat())
        val seed = current.strokes.size * 31 + kind.ordinal
        val added = buildLines(kind, options, seed, floatArrayOf(area.left, area.top, area.right, area.bottom), UUID.randomUUID().toString(), options.colorHex ?: brush().inkColor(), current.strokes.size)
        if (added.isEmpty()) return
        store.commit(current.strokes + added)
        host.inserted(added.mapTo(HashSet()) { it.id })
    }

    /** Türün [area] alanındaki olağan çizgi kalınlığı (kalınlık çarpanı 1). */
    private fun baseWidth(kind: ComicLinesKind, area: FloatArray): Float =
        (min(area[2] - area[0], area[3] - area[1]) * 0.006f).coerceIn(1.5f, 10f) * ComicLines.widthFactor(kind)

    private fun buildLines(
        kind: ComicLinesKind, o: LinesOptions, seed: Int, area: FloatArray, group: String, ink: String, order0: Int, layerId: String? = null,
    ): List<Stroke> {
        val lines = ComicLines.generate(kind, area[0], area[1], area[2], area[3], seed = seed, density = o.density, focusX = o.focusX, focusY = o.focusY, angle = o.angle, length = o.length)
        val b = ComicLines.bounds(lines) ?: return emptyList()
        val w = baseWidth(kind, area) * o.thickness.coerceIn(MIN_THICKNESS, MAX_THICKNESS)
        // Yumuşak anime: renk seçilmediyse her çizgi sırayla pastel renklerden biri
        val palette = kind == ComicLinesKind.ANIME_SOFT && o.colorHex == null
        val params = com.karapp.drawguidance.data.model.BrushParams(
            comicLines = com.karapp.drawguidance.data.model.ComicLinesParams(
                kind.name, o.density, seed, area.toList(), b.toList(), o.focusX, o.focusY, o.angle, o.length, palette,
            ),
        )
        var order = order0
        return lines.mapIndexed { n, l ->
            Stroke(
                id = UUID.randomUUID().toString(),
                points = List(l.xs.size) { i -> StrokePoint(q(l.xs[i]), q(l.ys[i]), l.pressures[i], 0f) },
                colorHex = if (palette) ComicLines.SOFT_COLORS[n % ComicLines.SOFT_COLORS.size] else ink,
                brushType = BrushType.INK_PEN, sizePx = max(w, 1f), opacity = o.opacity.coerceIn(0.05f, 1f), order = order++, groupId = group,
                params = params, layerId = layerId,
            )
        }
    }

    /** v29 seçili çizgi grubunun (yalnız hız/odak çizgileri seçiliyse) sıklığı; değilse null. */
    fun selectedLinesDensity(): Float? = selectedLines()?.first?.density

    private fun selectedLines(): Pair<com.karapp.drawguidance.data.model.ComicLinesParams, List<Stroke>>? {
        val current = store.canvas.value ?: return null
        val sel = current.strokes.filter { it.id in store.selection }
        if (sel.isEmpty()) return null
        val p = sel.first().params?.comicLines ?: return null
        val group = sel.first().groupId ?: return null
        if (sel.any { it.groupId != group || it.params?.comicLines == null }) return null
        return p to current.strokes.filter { it.groupId == group && it.params?.comicLines != null }
    }

    /** v36 seçili çizgi grubunun ayarları (yalnız hız/odak çizgileri seçiliyse); değilse null. */
    fun selectedLinesOptions(): Pair<ComicLinesKind, LinesOptions>? {
        val (p, group) = selectedLines() ?: return null
        val kind = runCatching { ComicLinesKind.valueOf(p.kind) }.getOrNull() ?: return null
        val first = group.first()
        val base = if (p.area.size == 4) baseWidth(kind, p.area.toFloatArray()) else first.sizePx
        return kind to LinesOptions(
            density = p.density, focusX = p.focusX, focusY = p.focusY, angle = p.angle, length = p.length,
            thickness = (first.sizePx / base).coerceIn(MIN_THICKNESS, MAX_THICKNESS),
            colorHex = if (p.palette) null else first.colorHex, opacity = first.opacity,
        )
    }

    /**
     * v29 seçili hız/odak çizgisi grubunun sıklığını değiştirir: aynı tür ve tohumla, grubun şimdiki yerine (taşındı ya
     * da ölçeklendiyse alan da taşınır) yeniden üretilir; renk, kalınlık, katman ve grup korunur. Kaydırıcı sürüklemesi
     * tek geri alma adımıdır.
     */
    fun setLinesDensity(density: Float) {
        val (_, o) = selectedLinesOptions() ?: return
        val d = density.coerceIn(ComicLines.MIN_DENSITY, ComicLines.MAX_DENSITY)
        if (kotlin.math.abs(d - o.density) < 1e-3f) return
        setLinesOptions(o.copy(density = d))
    }

    /**
     * v36 seçili çizgi grubunun odak noktası, yönü, uzunluğu, sıklığı, kalınlığı, rengi ve opaklığı: aynı tür ve tohumla
     * grubun şimdiki yerinde yeniden üretilir (katman ve grup korunur). Kaydırıcı sürüklemesi tek geri alma adımıdır.
     */
    fun setLinesOptions(options: LinesOptions) {
        val (p, group) = selectedLines() ?: return
        val current = store.canvas.value ?: return
        val kind = runCatching { ComicLinesKind.valueOf(p.kind) }.getOrNull() ?: return
        val o = options.copy(density = options.density.coerceIn(ComicLines.MIN_DENSITY, ComicLines.MAX_DENSITY))
        val now = ElementOps.selectionBounds(current.strokes, group.mapTo(HashSet()) { it.id }) ?: return
        val area = if (p.area.size == 4 && p.bounds.size == 4) {
            ComicLines.movedArea(p.area.toFloatArray(), p.bounds.toFloatArray(), floatArrayOf(now.left, now.top, now.right, now.bottom))
        } else floatArrayOf(now.left, now.top, now.right, now.bottom)
        val first = group.first()
        val index = current.strokes.indexOfFirst { it.groupId == first.groupId }
        val rebuilt = buildLines(kind, o, p.seed, area, first.groupId!!, o.colorHex ?: first.colorHex, 0, first.layerId)
        if (rebuilt.isEmpty()) return
        val ids = group.mapTo(HashSet()) { it.id }
        val kept = current.strokes.filter { it.id !in ids }
        val merged = ArrayList<Stroke>(kept.size + rebuilt.size)
        merged.addAll(kept.subList(0, index.coerceIn(0, kept.size)))
        merged.addAll(rebuilt)
        merged.addAll(kept.subList(index.coerceIn(0, kept.size), kept.size))
        store.commit(merged.mapIndexed { i, s -> if (s.order == i) s else s.copy(order = i) }, coalesce = "lines-density-" + first.groupId)
        store.select(rebuilt.mapTo(HashSet()) { it.id })
    }

    // --- v36 efektler (aksiyon, büyü, duygu, atmosfer): kütüphane çizimi olarak tek şekil eleman ---

    /**
     * Efekt ekler: [def] çizimi [center]da (yoksa sayfanın ortasında). Boyut sayfanın kısa kenarına oran, açı derece;
     * renk verilmezse efektin kendi rengi. Eklenen efekt sıradan şekildir: taşınır, kopyalanır, döndürülür, silinir.
     */
    fun insertEffect(def: com.karapp.drawguidance.content.comic.EffectDef, center: Offset?, options: EffectOptions) {
        val current = store.canvas.value ?: return
        val stroke = effectStroke(def, center, options) ?: return
        store.commit(current.strokes + stroke)
        host.inserted(setOf(stroke.id))
    }

    /** Efekt elemanı (eklenmez). */
    fun effectStroke(def: com.karapp.drawguidance.content.comic.EffectDef, center: Offset?, options: EffectOptions): Stroke? {
        val current = store.canvas.value ?: return null
        val c = center ?: Offset(current.width / 2f, current.height / 2f)
        val side = min(current.width, current.height) * options.size.coerceIn(MIN_EFFECT_SIZE, MAX_EFFECT_SIZE)
        val aspect = def.w / def.h
        val hw = if (aspect >= 1f) side / 2f else side / 2f * aspect
        val hh = if (aspect >= 1f) side / 2f / aspect else side / 2f
        val color = options.colorHex ?: def.color
        val stroke = ShapeFactory.build(
            com.karapp.drawguidance.content.library.ShapeLibrary.PREFIX + def.id, c - Offset(hw, hh), c + Offset(hw, hh),
            effectStyle(def, color, side), null, UUID.randomUUID().toString(), current.strokes.size, options.opacity.coerceIn(0.05f, 1f),
        ) ?: return null
        val a = options.angle % 360f
        return if (a == 0f) stroke else ElementOps.rotate(listOf(stroke), setOf(stroke.id), a, c).first()
    }

    /** Efektin biçimi: dolgu efekt rengi, kenar çizgi efektlerinde aynı renk, ötekilerde koyu tonu. */
    private fun effectStyle(def: com.karapp.drawguidance.content.comic.EffectDef, color: String, side: Float): ShapeStyle =
        ShapeStyle(strokeColorHex = if (def.lineOnly) color else darker(color), strokeWidth = (side * 0.012f).coerceIn(1.2f, 14f), fillColorHex = color)

    /** Seçili tek efekt ve tanımı; değilse null. */
    fun selectedEffect(): Pair<Stroke, com.karapp.drawguidance.content.comic.EffectDef>? {
        val current = store.canvas.value ?: return null
        val sel = store.selection
        if (sel.size != 1) return null
        val s = current.strokes.firstOrNull { it.id in sel } ?: return null
        val def = com.karapp.drawguidance.content.comic.ComicEffects.byId(s.shape?.kind ?: return null) ?: return null
        return s to def
    }

    /**
     * v36 seçili efekti düzenler: [scale] kat büyütür, [rotate] derece döndürür (ikisi de efektin ortası çevresinde),
     * [colorHex] rengini (tonları yeni renge göre), [opacity] opaklığını değiştirir. Kaydırıcı sürüklemesi tek geri alma
     * adımıdır.
     */
    fun editEffect(scale: Float = 1f, rotate: Float = 0f, colorHex: String? = null, opacity: Float? = null) {
        val current = store.canvas.value ?: return
        val (s, def) = selectedEffect() ?: return
        if (s.points.isEmpty()) return
        val ids = setOf(s.id)
        // Ağırlık merkezi dönüşte yerinde kalır (art arda küçük dönüşlerde efekt kaymaz)
        val pivot = Offset(s.points.sumOf { it.x.toDouble() }.toFloat() / s.points.size, s.points.sumOf { it.y.toDouble() }.toFloat() / s.points.size)
        var strokes = current.strokes
        if (scale != 1f) strokes = ElementOps.scale(strokes, ids, scale, scale, pivot)
        if (rotate % 360f != 0f) strokes = ElementOps.rotate(strokes, ids, rotate, pivot)
        if (colorHex != null || opacity != null) strokes = strokes.map {
            if (it.id != s.id) it
            else {
                val shape = it.shape!!
                val style = if (colorHex == null) shape.style
                else shape.style.copy(fillColorHex = colorHex, fillGradient = null, strokeColorHex = if (def.lineOnly) colorHex else darker(colorHex))
                it.copy(
                    shape = shape.copy(style = style), colorHex = style.strokeColorHex,
                    opacity = opacity?.coerceIn(0.05f, 1f) ?: it.opacity,
                )
            }
        }
        if (strokes === current.strokes) return
        store.commit(strokes, coalesce = "effect-" + s.id)
    }

    companion object {
        const val DEFAULT_TEXT = "Merhaba!"
        /** v36 telefon mesajı balonunun dolgu ve kenar rengi. */
        const val PHONE_FILL = "#3B82F6"
        const val PHONE_BORDER = "#2563EB"
        /** v36 şablon panellerinin çizgi rengi. */
        const val PANEL_INK = "#111111"
        /** v36 eklenen ayakta yetişkin karakterin boyu (sayfanın kısa kenarına oran). */
        const val CHARACTER_HEIGHT = 0.55f
        const val MIN_THICKNESS = 0.3f
        const val MAX_THICKNESS = 4f
        const val MIN_EFFECT_SIZE = 0.08f
        const val MAX_EFFECT_SIZE = 1f

        /** Rengin koyu tonu (efektlerin kenar çizgisi). */
        fun darker(hex: String): String {
            val v = hex.removePrefix("#").take(6).toIntOrNull(16) ?: return "#1E1E1E"
            fun ch(shift: Int) = ((v shr shift and 0xFF) * 0.38f).toInt().coerceIn(0, 255)
            return String.format("#%02X%02X%02X", ch(16), ch(8), ch(0))
        }

        /** v36 rengin açık tonu (rengin %45'i beyaza karışır). */
        fun lighter(hex: String): String {
            val v = hex.removePrefix("#").take(6).toIntOrNull(16) ?: return "#FFFFFF"
            fun ch(shift: Int) = ((v shr shift and 0xFF) + (255 - (v shr shift and 0xFF)) * 0.45f).toInt().coerceIn(0, 255)
            return String.format(java.util.Locale.ROOT, "#%02X%02X%02X", ch(16), ch(8), ch(0))
        }
    }
}

/**
 * v36 odak çizgisi ayarları (yeni eklenen ya da seçili grup): sıklık, odak noktası (alanın 0..1 oranı), yön (derece),
 * uzunluk ve kalınlık çarpanı, renk (null: kalem rengi; yumuşak animede pastel renkler) ve opaklık.
 */
data class LinesOptions(
    val density: Float = 1f,
    val focusX: Float = 0.5f,
    val focusY: Float = 0.5f,
    val angle: Float = 0f,
    val length: Float = 1f,
    val thickness: Float = 1f,
    val colorHex: String? = null,
    val opacity: Float = 1f,
)

/** v36 yeni efektin ayarları: boyut (sayfanın kısa kenarına oran), açı (derece), renk (null: efektin rengi), opaklık. */
data class EffectOptions(
    val size: Float = 0.4f,
    val angle: Float = 0f,
    val colorHex: String? = null,
    val opacity: Float = 1f,
)

/**
 * v36 balon penceresinin yazı ve kuyruk ayarları (yeni balon ya da ses yazısı için). [content] boşsa öntanımlı yazı;
 * [font], [sizePx] ve [colorHex] null ise yazı ayarlarından ve balon biçiminden (telefon mesajında beyaz). Kuyruk
 * ayarları öntanımlıdan farklıysa kuyruklar [tail] yönünde [length] uzaklıkta ve [curve] eğrilikte dizilir.
 */
data class BalloonTextOptions(
    val content: String = "",
    val font: TextFont? = null,
    val sizePx: Float? = null,
    val colorHex: String? = null,
    val align: TextAlignKind = TextAlignKind.CENTER,
    val tail: TailDirection = TailDirection.BOTTOM_LEFT,
    val length: Float = DEFAULT_TAIL_LENGTH,
    val curve: Float = DEFAULT_TAIL_CURVE,
) {
    val customTail: Boolean get() = tail != TailDirection.BOTTOM_LEFT || length != DEFAULT_TAIL_LENGTH || curve != DEFAULT_TAIL_CURVE

    companion object {
        const val DEFAULT_TAIL_LENGTH = 0.65f
        const val DEFAULT_TAIL_CURVE = 0.3f
    }
}
