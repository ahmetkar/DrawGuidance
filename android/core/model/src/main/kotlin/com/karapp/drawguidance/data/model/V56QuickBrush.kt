package com.karapp.drawguidance.data.model

/**
 * v56 hızlı fırça seçici (planın 2. bölümü, saf): son kullanılan en çok [MAX] fırça ve en çok [MAX] sabitlenmiş favori,
 * mod başına ayrı. Kayıt biçimi Android ve iOS'ta aynı: `"recent=PENCIL,MARKER;fav=INK"`.
 */
data class QuickBrushLists(val recent: List<BrushType> = emptyList(), val favorites: List<BrushType> = emptyList()) {

    /** [type] kullanıldı: başa alınır, en çok [MAX]. Silgi listeye girmez (şeritte ayrı kısayolu var). */
    fun used(type: BrushType): QuickBrushLists {
        if (type == BrushType.ERASER || type.isObject || type == BrushType.LASSO_FILL) return this
        return copy(recent = (listOf(type) + recent.filter { it != type }).take(MAX))
    }

    /** Sabitle / kaldır. Doluysa (en çok [MAX]) yeni favori eklenmez; false döner. */
    fun toggleFavorite(type: BrushType): Pair<QuickBrushLists, Boolean> = when {
        type in favorites -> copy(favorites = favorites - type) to true
        favorites.size >= MAX -> this to false
        type == BrushType.ERASER || type.isObject -> this to false
        else -> copy(favorites = favorites + type) to true
    }

    /** Son iki fırça arasında geçiş (iki parmakla dokunma): şu anki fırça [current] ise önceki döner. */
    fun previous(current: BrushType): BrushType? = recent.firstOrNull { it != current }

    /** Şeritte gösterilecek sıra: önce favoriler, sonra favori olmayan son kullanılanlar. */
    fun strip(): List<BrushType> = favorites + recent.filter { it !in favorites }

    fun encode(): String = "recent=" + recent.joinToString(",") { it.name } + ";fav=" + favorites.joinToString(",") { it.name }

    companion object {
        const val MAX = 6

        fun decode(text: String?): QuickBrushLists {
            if (text.isNullOrBlank()) return QuickBrushLists()
            fun list(key: String): List<BrushType> = text.split(';').firstOrNull { it.startsWith("$key=") }
                ?.substringAfter('=')?.split(',')?.mapNotNull { n -> BrushType.entries.firstOrNull { it.name == n } }
                ?.distinct()?.take(MAX).orEmpty()
            return QuickBrushLists(list("recent"), list("fav"))
        }
    }
}

/** Hızlı fırça düğmesinin köşesi. */
enum class QuickCorner { TOP_START, TOP_END, BOTTOM_START, BOTTOM_END }

/** v56 hızlı fırça yerleşim kuralı (Android ve iOS aynı, testli). */
object QuickBrushLayout {
    /** Düğme çapı (dp/pt). */
    const val BUTTON = 44f
    /** Sol üst yuva: düğme + 8 boşluk + üst kenar boşluğu. */
    const val SLOT = 60f

    /**
     * Yatay ekranda sol (RTL'de sağ) mod menüsünün üst boşluğu: hızlı fırça menünün tarafındaki üst köşedeyse menü yuvanın
     * altından başlar ([SLOT]); değilse null (menü eskisi gibi ortalanır). Dikey ekranda sol menü yok: null.
     * [quickCorner] mantıksal köşedir (START = LTR'de sol, RTL'de sağ); mod menüsü de her zaman START tarafında.
     */
    fun railTopInset(landscape: Boolean, quickCorner: QuickCorner?, @Suppress("UNUSED_PARAMETER") rtl: Boolean): Float? =
        if (landscape && quickCorner == QuickCorner.TOP_START) SLOT else null

    /** Tablette sürüklenen mod menüsü yuvanın içine bırakıldıysa yuvanın altına itilir (üst kenar, dp). */
    fun pushBelowSlot(menuTop: Float, quickCorner: QuickCorner?): Float =
        if (quickCorner == QuickCorner.TOP_START && menuTop < SLOT) SLOT else menuTop

    /** Ekran (görünür tuval) köşesi: mantıksal köşe RTL'de aynalanır. Dönen: (sağda mı, altta mı). */
    fun physical(corner: QuickCorner, rtl: Boolean): Pair<Boolean, Boolean> {
        val end = corner == QuickCorner.TOP_END || corner == QuickCorner.BOTTOM_END
        val bottom = corner == QuickCorner.BOTTOM_START || corner == QuickCorner.BOTTOM_END
        return (end != rtl) to bottom
    }

    /** Bırakılan noktaya ([x], [y]; alanın [w]×[h]) en yakın mantıksal köşe. */
    fun nearest(x: Float, y: Float, w: Float, h: Float, rtl: Boolean): QuickCorner {
        val right = x > w / 2f
        val bottom = y > h / 2f
        val end = right != rtl
        return when {
            !end && !bottom -> QuickCorner.TOP_START
            end && !bottom -> QuickCorner.TOP_END
            !end -> QuickCorner.BOTTOM_START
            else -> QuickCorner.BOTTOM_END
        }
    }

    /**
     * Kalem düğmenin üstünden geçerse düğme kenara kayar: kalem noktası ([px], [py]) düğme kutusuna ([bx], [by] merkez,
     * [r] yarıçap) [margin] kadar yakınsa düğmenin yatay kayması (dp; köşeden içeri değil, kenar boyunca aşağı).
     */
    fun dodge(px: Float, py: Float, bx: Float, by: Float, r: Float, margin: Float = 12f): Float {
        val dx = px - bx
        val dy = py - by
        val near = r + margin
        return if (dx * dx + dy * dy < near * near) near * 2f else 0f
    }
}

/** v56 damlalığın canlı renk dairesi yerleşimi (2D-5, saf). */
object EyedropLoupe {
    /** Halka yarıçapı (dp); çocuk modunda daha büyük. */
    fun radius(kids: Boolean): Float = if (kids) 56f else 40f
    /** Parmağın görmediği yer: dokunma noktasının bu kadar yukarısı (dp). */
    const val LIFT = 60f

    /**
     * Halkanın merkezi: dokunma noktasının [LIFT] yukarısı; ekran ([w]×[h]) dışına taşmayacak şekilde kayar. Üstte yer yoksa
     * dokunmanın altına iner.
     */
    fun center(x: Float, y: Float, w: Float, h: Float, r: Float, lift: Float = LIFT): Pair<Float, Float> {
        val cx = x.coerceIn(r, (w - r).coerceAtLeast(r))
        var cy = y - lift
        if (cy - r < 0f) cy = y + lift
        cy = cy.coerceIn(r, (h - r).coerceAtLeast(r))
        return cx to cy
    }

    /** Bırakılan nokta sayfanın ([pageW]×[pageH]) dışındaysa renk alınmaz (iptal). */
    fun inside(x: Float, y: Float, pageW: Float, pageH: Float): Boolean = x in 0f..pageW && y in 0f..pageH
}
