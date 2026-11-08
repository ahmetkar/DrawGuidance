package com.karapp.drawguidance.data.catalog

import android.util.Log
import com.karapp.drawguidance.content.catalog.CatalogCodec
import com.karapp.drawguidance.content.catalog.CatalogInfo
import com.karapp.drawguidance.content.catalog.CatalogVector
import com.karapp.drawguidance.content.library.Audience
import com.karapp.drawguidance.content.library.Fit
import com.karapp.drawguidance.data.local.CatalogVectorDao
import com.karapp.drawguidance.data.local.CatalogVectorEntity
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * v36 kütüphane şekillerinin cihaz veritabanındaki kaydı ([CatalogVectorEntity]): yeni eklenen ve sonradan eklenecek
 * bütün vektör şekiller hem uygulamanın veritabanına hem sunucuya (katalog tohumu ve `catalog_vectors`) yazılır.
 *
 * - [seedBundled]: uygulamayla gelen şekiller, katalog sürümü değiştiyse bir kez (eski yerleşik kayıtların yerine).
 * - [saveRemote]: sunucudan inen daha yeni katalog.
 * - [remoteVectors]: açılışta kütüphaneye uygulanacak sunucu kayıtları.
 */
class CatalogVectorRepository(private val dao: CatalogVectorDao, private val clock: () -> Long = System::currentTimeMillis) {

    /** Yerleşik şekiller veritabanında yoksa ya da eski katalogdansa yazılır; yazıldıysa true. */
    suspend fun seedBundled(vectors: () -> Sequence<CatalogVector> = CatalogCodec::bundledVectorSequence): Boolean {
        val stored = dao.version(CatalogVectorEntity.SOURCE_BUNDLED) ?: 0L
        if (stored >= CatalogInfo.VERSION && dao.count(CatalogVectorEntity.SOURCE_BUNDLED) > 0) return false
        val rows = rows(vectors(), CatalogVectorEntity.SOURCE_BUNDLED, CatalogInfo.VERSION)
        dao.replaceSource(CatalogVectorEntity.SOURCE_BUNDLED, rows)
        Log.i(TAG, "yerleşik katalog ${CatalogInfo.VERSION}: ${rows.size} şekil veritabanına yazıldı")
        return true
    }

    /** [packed] verilirse (v37 ikili katalog) yollar yeniden paketlenmez; sırası [vectors] ile aynı. */
    suspend fun saveRemote(vectors: List<CatalogVector>, version: Long, packed: List<ByteArray>? = null) {
        dao.replaceSource(CatalogVectorEntity.SOURCE_REMOTE, rows(vectors.asSequence(), CatalogVectorEntity.SOURCE_REMOTE, version, packed))
    }

    suspend fun remoteVectors(): List<CatalogVector> {
        val rows = dao.bySource(CatalogVectorEntity.SOURCE_REMOTE)
        val i = Inflater()
        try { return rows.mapNotNull { fromEntity(it, i) } } finally { i.end() }
    }

    /** Satırlar tek bir sıkıştırıcıyla (yerel zlib belleği her şekil için yeniden ayrılmaz) üretilir. */
    private fun rows(vectors: Sequence<CatalogVector>, source: String, version: Long, packed: List<ByteArray>? = null): List<CatalogVectorEntity> {
        val now = clock()
        val d = Deflater(Deflater.BEST_COMPRESSION)
        try {
            return vectors.mapIndexed { i, v -> toEntity(v, source, version, now, d, packed?.getOrNull(i)) }.toList()
        } finally { d.end() }
    }

    suspend fun count(source: String): Int = dao.count(source)

    companion object {
        private const val TAG = "CatalogVectors"

        fun toEntity(v: CatalogVector, source: String, version: Long, now: Long, deflater: Deflater? = null, packed: ByteArray? = null) = CatalogVectorEntity(
            id = v.id, title = v.title, categoryId = v.categoryId, categoryTitle = v.categoryTitle, section = v.section,
            audience = v.audience.name, fit = v.fit.name, aspect = v.aspect, tags = v.tags.joinToString("\n"),
            paths = deflate(packed ?: CatalogCodec.packPaths(v.paths), deflater),
            source = source, catalogVersion = version, updatedAt = now,
        )

        fun fromEntity(e: CatalogVectorEntity, inflater: Inflater? = null): CatalogVector? = runCatching {
            CatalogVector(
                id = e.id, title = e.title, categoryId = e.categoryId, categoryTitle = e.categoryTitle, section = e.section,
                audience = runCatching { Audience.valueOf(e.audience) }.getOrDefault(Audience.ART),
                fit = runCatching { Fit.valueOf(e.fit) }.getOrDefault(Fit.BOX), aspect = e.aspect,
                tags = if (e.tags.isEmpty()) emptyList() else e.tags.split('\n'),
                paths = unpack(inflate(e.paths, inflater)),
            )
        }.getOrNull()

        /** İkili yollar ([CatalogCodec.packPaths]); ilk sürümün JSON kaydı da okunur. */
        private fun unpack(bytes: ByteArray): List<com.karapp.drawguidance.content.vector.VPath> =
            if (bytes.firstOrNull() == '['.code.toByte()) CatalogCodec.decodePaths(String(bytes, Charsets.UTF_8)) else CatalogCodec.unpackPaths(bytes)

        /**
         * Yolların ikili biçimi Deflate ile (en iyi oran) sıkıştırılır.
         * [reuse] verilirse sıfırlanıp kullanılır ve kapatılmaz.
         */
        fun deflate(bytes: ByteArray, reuse: Deflater? = null): ByteArray {
            val d = reuse?.apply { reset() } ?: Deflater(Deflater.BEST_COMPRESSION)
            try {
                d.setInput(bytes); d.finish()
                val out = ByteArrayOutputStream(bytes.size / 3 + 16)
                val buf = ByteArray(8192)
                while (!d.finished()) { val n = d.deflate(buf); out.write(buf, 0, n) }
                return out.toByteArray()
            } finally { if (reuse == null) d.end() }
        }

        fun inflate(bytes: ByteArray, reuse: Inflater? = null): ByteArray {
            val i = reuse?.apply { reset() } ?: Inflater()
            try {
                i.setInput(bytes)
                val out = ByteArrayOutputStream(bytes.size * 4)
                val buf = ByteArray(8192)
                while (!i.finished()) {
                    val n = i.inflate(buf)
                    if (n == 0 && (i.needsInput() || i.needsDictionary())) break
                    out.write(buf, 0, n)
                }
                return out.toByteArray()
            } finally { if (reuse == null) i.end() }
        }
    }
}
