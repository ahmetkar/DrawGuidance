package com.karapp.drawguidance.data.library

import android.content.Context
import android.graphics.Bitmap
import com.karapp.drawguidance.content.model3d.Catalog3D
import com.karapp.drawguidance.content.model3d.Gltf3
import com.karapp.drawguidance.content.model3d.GltfImport
import com.karapp.drawguidance.content.model3d.GltfWriteOptions
import com.karapp.drawguidance.content.model3d.Model3D
import com.karapp.drawguidance.content.model3d.Model3DStore
import com.karapp.drawguidance.content.model3d.RenderOptions
import com.karapp.drawguidance.content.model3d.Renderer
import com.karapp.drawguidance.content.model3d.Samples3D
import com.karapp.drawguidance.content.model3d.Texture
import com.karapp.drawguidance.content.model3d.View3
import com.karapp.drawguidance.data.local.Model3DDao
import com.karapp.drawguidance.data.local.Model3DEntity
import com.karapp.drawguidance.data.local.Model3DInfo
import com.karapp.drawguidance.diagnostics.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * v23 "3B Şekillerim" deposu (`models3d`). v24: modeller GLB biçiminde ([Model3DStore]; glTF 2.0, düzenleme bilgileri
 * "extras"ta) ve küçük resimleriyle saklanır; eski (v23 "D3") kayıtlar ilk açılışta bir kez GLB'ye çevrilir. Dışa
 * aktarma dokuları gömülü GLB üretir; içe aktarma başka programların glTF/GLB dosyalarını ayrıntısını düşürerek alır
 * (dokular "İçe aktarılan" olarak saklanır). Hazır şekiller ([Catalog3D]) yeni kimlikle kopyalanarak açılır.
 */
class Model3DRepository(
    context: Context,
    private val dao: Model3DDao,
    private val textures: TextureRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val prefs = context.applicationContext.getSharedPreferences("models3d", Context.MODE_PRIVATE)

    fun observe(): Flow<List<Model3DInfo>> = dao.observeInfo()

    fun warmUp() {
        scope.launch {
            runCatching { migrateToGlb() }.onFailure { AppLog.e("Model3D", "3B modeller GLB'ye çevrilemedi", it) }
            runCatching {
                if (prefs.getInt(SAMPLES_KEY, 0) >= Samples3D.VERSION) return@runCatching
                val existing = dao.idsBySource(Model3DEntity.SAMPLE).toSet()
                val now = System.currentTimeMillis()
                Samples3D.all.forEachIndexed { i, m ->
                    if (m.id in existing) return@forEachIndexed
                    // Örnekler listede kendi sıralarıyla görünsün
                    upsert(m, Model3DEntity.SAMPLE, now - i * 1000L, now - i * 1000L)
                }
                prefs.edit().putInt(SAMPLES_KEY, Samples3D.VERSION).apply()
            }.onFailure { AppLog.e("Model3D", "Örnek modeller eklenemedi", it) }
        }
    }

    /** v24: eski biçimdeki kayıtları GLB'ye çevirir (bir kez; zaman ve küçük resim korunur). */
    suspend fun migrateToGlb() {
        if (prefs.getInt(FORMAT_KEY, 0) >= FORMAT_GLB) return
        var converted = 0
        for (id in dao.allIds()) {
            val row = dao.get(id) ?: continue
            if (!Model3DStore.isLegacy(row.data)) continue
            val model = runCatching { Model3DStore.decode(row.data) }.getOrNull() ?: continue
            dao.updateData(id, Model3DStore.encode(model.copy(id = row.id, name = row.name)))
            converted++
        }
        prefs.edit().putInt(FORMAT_KEY, FORMAT_GLB).apply()
        if (converted > 0) AppLog.i("Model3D", "$converted 3B model GLB biçimine çevrildi")
    }

    suspend fun load(id: String): Model3D? = withContext(Dispatchers.IO) {
        dao.get(id)?.let { row -> runCatching { Model3DStore.decode(row.data) }.getOrNull()?.copy(id = row.id, name = row.name) }
    }

    /** Modelin kayıtlı verisi (GLB). */
    suspend fun bytes(id: String): ByteArray? = withContext(Dispatchers.IO) { dao.get(id)?.data }

    /** Modeli kaydeder (yoksa ekler); kimliği döner. */
    suspend fun save(model: Model3D): String = withContext(Dispatchers.Default) {
        val now = System.currentTimeMillis()
        val old = dao.get(model.id)
        upsert(model, old?.source ?: Model3DEntity.USER, old?.createdAt ?: now, now)
        model.id
    }

    /** Yeni kimlikle kopya ("Farklı kaydet"). */
    suspend fun saveCopy(model: Model3D, name: String): Model3D {
        val copy = model.copy(id = UUID.randomUUID().toString(), name = name)
        save(copy)
        return copy
    }

    suspend fun rename(id: String, name: String) = dao.rename(id, name, System.currentTimeMillis())

    suspend fun delete(ids: List<String>) = dao.delete(ids)

    private suspend fun upsert(model: Model3D, source: String, created: Long, updated: Long) {
        val data = Model3DStore.encode(model)
        val thumb = runCatching { thumbnail(model) }.getOrNull()
        dao.upsert(Model3DEntity(model.id, model.name, data, thumb, created, updated, source))
    }

    // ------------------------------------------------------------------------------------------ hazır şekiller

    /** Hazır şeklin yeni kimlikli kopyası (düzenleyicide yeni model olarak açılır; kaydedilince 3B Şekillerim'e). */
    fun fromCatalog(catalogId: String): Model3D? =
        Catalog3D.byId(catalogId)?.build()?.let { it.copy(id = UUID.randomUUID().toString()) }

    // ------------------------------------------------------------------------------------------ glTF / GLB

    /**
     * Başka programın (ya da uygulamanın) glTF/GLB dosyasını içe aktarır: üçgen sayısı [maxTriangles]'a düşürülür,
     * dokular 256 piksele küçültülüp saklanır. Model kaydedilmez (düzenleyici ya da çağıran kaydeder).
     */
    suspend fun importGlb(data: ByteArray, name: String, maxTriangles: Int): GltfImport = withContext(Dispatchers.Default) {
        val result = Gltf3.import(data, name, maxTriangles, ::decodeImage)
        result.textures.forEach { t -> textures.saveImported(t.id, t.name, t.texture) }
        result
    }

    /** Dışa aktarma: dokular gömülü GLB (başka programlarda açılır; uygulamaya geri alınınca kayıpsız). */
    suspend fun exportGlb(model: Model3D): ByteArray {
        val ids = model.parts.flatMap { p -> listOfNotNull(p.material.texture) + p.faceMaterials.values.mapNotNull { it.texture } }.toSet()
        textures.preload(ids)
        return withContext(Dispatchers.Default) {
            Gltf3.write(model, GltfWriteOptions(embedTextures = true, textures = textures::cached))
        }
    }

    private fun decodeImage(bytes: ByteArray, mime: String?): Texture? =
        BitmapCodec.pixels(bytes)?.let { (w, h, px) -> Texture(w, h, px) }

    // ------------------------------------------------------------------------------------------ resimler

    /** Küçük resim (saydam PNG). */
    suspend fun thumbnail(model: Model3D, size: Int = THUMB): ByteArray {
        val bmp = render(model, size, size, ground = false)
        try {
            return BitmapCodec.encode(bmp, opaque = false)
        } finally {
            bmp.recycle()
        }
    }

    /**
     * Modelin resmi: sahnedeki ışık, gölge ve kenar ayarlarıyla, saydam arka planlı, modele sığdırılmış ([margin]).
     * Dokular önceden belleğe alınır.
     */
    suspend fun render(model: Model3D, width: Int, height: Int, ground: Boolean = false, margin: Float = 0.06f): Bitmap {
        textures.preload(model.parts.flatMap { p -> listOfNotNull(p.material.texture) + p.faceMaterials.values.mapNotNull { it.texture } }.toSet())
        return withContext(Dispatchers.Default) {
            val view = View3.fit(model, model.camera, width, height, margin)
            val frame = Renderer.render(model, view, RenderOptions(textures = textures::cached, supersample = 2, ground = ground, background = 0))
            BitmapCodec.bitmap(frame.color, frame.width, frame.height)
        }
    }

    companion object {
        const val THUMB = 192
        private const val SAMPLES_KEY = "samples_version"
        private const val FORMAT_KEY = "format_version"
        private const val FORMAT_GLB = 2

        /** İçe aktarmada ayrıntı seçenekleri (üçgen sayısı): az, orta, çok. */
        val DETAIL_LEVELS = intArrayOf(2_000, 5_000, 12_000)
    }
}
