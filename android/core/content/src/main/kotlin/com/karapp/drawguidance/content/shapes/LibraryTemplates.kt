package com.karapp.drawguidance.content.shapes

import com.karapp.drawguidance.content.vector.P
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * v37 kütüphaneden üretilen şablonlar: v36 nesne kütüphanesinin nesneleri ("obje."; eski genel "nesne." şablonlarından ayrı), çizgi roman karakterleri
 * ("roman.karakter.<tür>") ve çizgi roman efektleri ("roman.efekt.<anahtar>"). Kütüphane çizimlerinin çizgi hali
 * (dış çizgileri, en önemli parçaları) bilgisayarda çıkarılır ve uygulamayla gelen kaynak dosyasında durur
 * (`/shapes/library_templates.bin`; üretim: testlerdeki `LibraryTemplateExport`, bkz. `ModeNetRetrain`). Böylece
 * cihazda çizim kodu çalıştırılmaz ve şablonlar modların sinir ağlarıyla aynı sürümde kalır.
 *
 * Kapsam ([ModeCatalog]): nesneler resim ve çizgi roman modlarında (not alma modunda yalnız not kategorilerindekiler),
 * karakterler ve efektler yalnız çizgi roman modunda; çocuk modunda yoktur. [ShapeTemplate] dışında her kaydın
 * kütüphanedeki karşılığı ([libraryId]) da saklanır: uygulama öneriyi kabul edince isterse kütüphane çizimini koyar.
 */
object LibraryTemplates {
    const val RESOURCE = "/shapes/library_templates.bin"

    /** Nesne şablonlarının ön eki (moda özel: genel aramada yok, resim/çizgi roman/not kapsamında). */
    const val OBJECT_PREFIX = "obje."
    const val CHARACTER_PREFIX = "roman.karakter."
    const val EFFECT_PREFIX = "roman.efekt."

    /** Kaydın şablonu ve kütüphanedeki şeklin kimliği (`obj.phone.comic`, `chr.hero.calm.stand`, `fx.flame`). */
    class Entry(val template: ShapeTemplate, val libraryId: String)

    val entries: List<Entry> by lazy {
        runCatching { LibraryTemplates::class.java.getResourceAsStream(RESOURCE)?.use { decode(it.readBytes()) } }.getOrNull().orEmpty()
    }

    val all: List<ShapeTemplate> get() = entries.map { it.template }

    private val byKey: Map<String, String> by lazy { entries.associate { it.template.key to it.libraryId } }

    /** Şablonun kütüphanedeki karşılığı (kütüphaneden üretilmemişse null). */
    fun libraryId(templateKey: String): String? = byKey[templateKey]

    fun isLibraryKey(key: String): Boolean =
        key.startsWith(OBJECT_PREFIX) || key.startsWith(CHARACTER_PREFIX) || key.startsWith(EFFECT_PREFIX)

    private const val MAGIC = 0x4C425450 // "LBTP"
    private const val FORMAT = 1

    /** Koordinatlar 1/10000 birimde kısa tamsayı (birim kutunun -3..+3 aralığı). */
    fun encode(list: List<Entry>): ByteArray {
        val bos = ByteArrayOutputStream()
        DataOutputStream(bos).use { d ->
            d.writeInt(MAGIC); d.writeInt(FORMAT); d.writeInt(list.size)
            for (e in list) {
                val t = e.template
                d.writeUTF(t.key); d.writeUTF(t.name); d.writeUTF(t.category); d.writeUTF(t.group); d.writeUTF(t.description)
                d.writeUTF(e.libraryId)
                d.writeShort(t.strokes.size)
                for (s in t.strokes) {
                    d.writeShort(s.size)
                    for (p in s) { d.writeShort(q(p.x)); d.writeShort(q(p.y)) }
                }
            }
        }
        return bos.toByteArray()
    }

    fun decode(bytes: ByteArray): List<Entry>? = runCatching {
        DataInputStream(bytes.inputStream()).use { d ->
            if (d.readInt() != MAGIC || d.readInt() != FORMAT) return null
            List(d.readInt()) {
                val key = d.readUTF(); val name = d.readUTF(); val category = d.readUTF(); val group = d.readUTF()
                val description = d.readUTF(); val lib = d.readUTF()
                val strokes = List(d.readUnsignedShort()) { List(d.readUnsignedShort()) { P(d.readShort() / 10000f, d.readShort() / 10000f) } }
                Entry(ShapeTemplate(key, name, category, group, strokes, description), lib)
            }
        }
    }.getOrNull()

    private fun q(v: Float): Int = Math.round(v * 10000f).coerceIn(-32000, 32000)
}
