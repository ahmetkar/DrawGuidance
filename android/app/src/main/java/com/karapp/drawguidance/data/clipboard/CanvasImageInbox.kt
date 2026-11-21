package com.karapp.drawguidance.data.clipboard

/**
 * v56 aşama 12 "Tuvale ekle": 3B Çizim ekranında üretilen resim (PNG) hedef tuvale bırakılır; tuval açılınca
 * ([take]) resim elemanı olarak eklenir. Uygulama açık kaldıkça durur, her tuval için en son bırakılan resim tutulur.
 */
object CanvasImageInbox {
    private val items = LinkedHashMap<String, ByteArray>()

    @Synchronized fun post(canvasId: String, png: ByteArray) { items[canvasId] = png }

    @Synchronized fun take(canvasId: String): ByteArray? = items.remove(canvasId)

    @Synchronized fun has(canvasId: String): Boolean = items.containsKey(canvasId)
}
