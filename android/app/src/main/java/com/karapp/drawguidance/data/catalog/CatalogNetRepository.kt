package com.karapp.drawguidance.data.catalog

import android.util.Log
import com.karapp.drawguidance.content.catalog.CatalogCodec
import com.karapp.drawguidance.content.catalog.CatalogInfo
import com.karapp.drawguidance.content.catalog.CatalogModeNet
import com.karapp.drawguidance.content.shapes.ShapeMode
import com.karapp.drawguidance.content.shapes.ShapeNet
import com.karapp.drawguidance.data.local.CatalogNetDao
import com.karapp.drawguidance.data.local.CatalogNetEntity

/**
 * v37 sinir ağlarının cihaz veritabanındaki kaydı ([CatalogNetEntity]; vektör şekiller gibi): modların şekil düzeltme
 * ağları ve not alma modunun yazı düzeltme ağı (`NOTES_TEXT`). Sunucuda aynı kayıtlar katalogun temel ağlar
 * bölümündedir; telefonla sunucu katalog eşitlemesiyle ([CatalogSyncer]) eşitlenir.
 *
 * - [seedBundled]: uygulamayla gelen ağlar, katalog sürümü değiştiyse bir kez.
 * - [saveRemote] / [remoteNets]: sunucudan inen daha yeni ağlar.
 * - [dropObsolete]: eski not alma ağının (yazı ve şekil düzeltmesi tek ağdaydı) kayıtları silinir.
 */
class CatalogNetRepository(private val dao: CatalogNetDao, private val clock: () -> Long = System::currentTimeMillis) {

    /** Yerleşik ağlar veritabanında yoksa ya da eski katalogdansa yazılır; yazıldıysa true. */
    suspend fun seedBundled(nets: () -> List<CatalogModeNet> = CatalogCodec::bundledModeNets): Boolean {
        dropObsolete()
        val stored = dao.version(CatalogNetEntity.SOURCE_BUNDLED) ?: 0L
        if (stored >= CatalogInfo.VERSION && dao.count(CatalogNetEntity.SOURCE_BUNDLED) > 0) return false
        val now = clock()
        val rows = nets().map { entity(it, CatalogNetEntity.SOURCE_BUNDLED, CatalogInfo.VERSION, now) }
        dao.replaceSource(CatalogNetEntity.SOURCE_BUNDLED, rows)
        Log.i(TAG, "yerleşik ağlar ${CatalogInfo.VERSION}: ${rows.joinToString { "${it.mode} v${it.version}" }}")
        return true
    }

    suspend fun saveRemote(net: CatalogModeNet, catalogVersion: Long) =
        dao.upsert(entity(net, CatalogNetEntity.SOURCE_REMOTE, catalogVersion, clock()))

    /** Sunucudan inmiş ağlar (eskimiş not alma ağı önce silinir). */
    suspend fun remoteNets(): List<CatalogModeNet> {
        dropObsolete()
        return dao.bySource(CatalogNetEntity.SOURCE_REMOTE).map { CatalogModeNet(it.mode, it.version, it.format, it.weights) }
    }

    suspend fun bundledNets(): List<CatalogModeNet> =
        dao.bySource(CatalogNetEntity.SOURCE_BUNDLED).map { CatalogModeNet(it.mode, it.version, it.format, it.weights) }

    /** Eski temel ağların ([ShapeNet.resetBelow]; v37 not alma) kayıtları silinir; silinen satır sayısı. */
    suspend fun dropObsolete(): Int = ShapeMode.entries.sumOf { m ->
        val below = ShapeNet.resetBelow(m)
        if (below > 0) dao.deleteOlder(m.name, below) else 0
    }

    private fun entity(n: CatalogModeNet, source: String, catalogVersion: Long, now: Long) =
        CatalogNetEntity(n.mode, source, n.version, n.format, n.weights, catalogVersion, now)

    private companion object {
        const val TAG = "CatalogNets"
    }
}
