package com.karapp.drawguidance.data.repository

import com.karapp.drawguidance.content.shapes.ShapeIndex
import com.karapp.drawguidance.content.shapes.ShapeMode
import com.karapp.drawguidance.content.shapes.ShapeModeModels
import com.karapp.drawguidance.content.shapes.ShapeScope
import com.karapp.drawguidance.content.vector.P
import com.karapp.drawguidance.data.model.CanvasKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/** v29 tuval türünün tahmin modu. */
fun CanvasKind.shapeMode(): ShapeMode = when (this) {
    // v33 basit türler temel türün moduyla tahmin yapar; v34 çocuklar için kendi modu (çocuk şablonları ve ağı)
    CanvasKind.SIMPLE_ART -> ShapeMode.ART
    CanvasKind.KIDS -> ShapeMode.KIDS
    CanvasKind.SIMPLE_NOTES -> ShapeMode.NOTES
    CanvasKind.ART -> ShapeMode.ART
    CanvasKind.NOTES -> ShapeMode.NOTES
    CanvasKind.COMIC -> ShapeMode.COMIC
}

/**
 * v29 açık tuvalin moduna özel şekil kaynağı: aramalar modun kapsamında (modun şablonları, sinir ağı ve sayaçları)
 * yapılır; kabul ve retler hem şekil veritabanına (genel sayaçlar, kabul edilen çizim örnekleri) hem de **yalnız bu
 * modun** sinir ağına (cihazda eğitim) gider. Mod bilinmiyorsa (tuval yüklenmeden) genel arama yapılır.
 */
class ModeTemplateSource(
    private val base: ShapeTemplateRepository,
    private val models: ShapeModeModels,
    private val mode: () -> ShapeMode?,
    private val worker: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    /** v34 tuval türü: basit resimde resim modunun kapsamı sade konulara daraltılır. */
    private val kind: () -> CanvasKind? = { null },
) : TemplateSource {
    private val warming = AtomicBoolean(false)

    override val index: ShapeIndex? get() = base.index

    override val searchScope: ShapeScope?
        get() {
            val m = mode() ?: return null
            val idx = base.index ?: return null
            if (!models.isReady(m)) warm(m, idx)
            val s = models.scopeIfReady(m, idx)
            return if (kind() == CanvasKind.SIMPLE_ART) simpleArt(s, idx) else s
        }

    override val canvasKind: CanvasKind? get() = kind()

    private var narrowed: Pair<Set<String>, ShapeScope>? = null

    /** v34 basit resim: parça/teknik şablonlar aranmaz (öneriler sade konulardan; ağ ve sayaçlar aynı). */
    private fun simpleArt(s: ShapeScope, idx: ShapeIndex): ShapeScope {
        narrowed?.let { (keys, scope) -> if (keys === s.keys && scope.net === s.net) return scope }
        val allowed = idx.metas.filter { com.karapp.drawguidance.content.shapes.ModeCatalog.allowsSimpleArt(it.key, it.category) }.mapTo(HashSet()) { it.key }
        val scope = ShapeScope(s.mode, s.keys.filterTo(LinkedHashSet()) { it in allowed }, s.net, s.netWeight, s.usage, s.rejects)
        narrowed = s.keys to scope
        return scope
    }

    /** Modun modeli arka planda hazırlanır (dosyadan okunur ya da ilk seferde kurulur). */
    fun warm() {
        val m = mode() ?: return
        worker.launch {
            val idx = runCatching { base.load() }.getOrNull() ?: return@launch
            runCatching { models.model(m, idx) }
        }
    }

    private fun warm(m: ShapeMode, idx: ShapeIndex) {
        if (!warming.compareAndSet(false, true)) return
        worker.launch {
            try { runCatching { models.model(m, idx) } } finally { warming.set(false) }
        }
    }

    override fun accepted(key: String, drawn: List<List<P>>) {
        base.accepted(key, drawn)
        train { m, idx -> models.accepted(m, idx, key, drawn) }
    }

    override fun acceptedPart(key: String, drawn: List<List<P>>) {
        base.accepted(key, emptyList())
        train { m, idx -> models.accepted(m, idx, key, drawn) }
    }

    override fun rejected(keys: List<String>, drawn: List<List<P>>) {
        base.rejected(keys)
        if (keys.isNotEmpty()) train { m, idx -> models.rejected(m, idx, keys, drawn) }
    }

    private fun train(block: (ShapeMode, ShapeIndex) -> Unit) {
        val m = mode() ?: return
        val idx = base.index ?: return
        worker.launch { runCatching { block(m, idx) } }
    }
}
