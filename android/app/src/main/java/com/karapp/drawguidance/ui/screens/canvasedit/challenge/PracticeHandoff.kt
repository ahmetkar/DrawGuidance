package com.karapp.drawguidance.ui.screens.canvasedit.challenge

/**
 * v51 yeni alıştırma yeni tuvalde açılırken eski düzenleyiciden yenisine aktarılan alıştırma sırası (tuval kimliğine
 * bağlı; bir kez alınır).
 */
object PracticeHandoff {
    @Volatile private var pending: Pair<String, Int>? = null

    fun put(canvasLocalId: String, index: Int) {
        pending = canvasLocalId to index
    }

    /** [canvasLocalId] için bekleyen alıştırma sırası (alınınca silinir). */
    fun take(canvasLocalId: String): Int? {
        val p = pending ?: return null
        if (p.first != canvasLocalId) return null
        pending = null
        return p.second
    }
}
