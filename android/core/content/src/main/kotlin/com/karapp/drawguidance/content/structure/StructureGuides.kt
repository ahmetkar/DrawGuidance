package com.karapp.drawguidance.content.structure

/**
 * v53 canlı, nesne ve eşya yapı kılavuzları: `structure_guides.txt` (iOS ile aynı dosya). Birim: kutu yüksekliği 1,
 * genişliği [StructureGuide.aspect]. Tuvale yazılmaz; yalnız çizerken görünür.
 */
enum class StructureGroup { LIVING, OBJECT, ITEM }

sealed interface StructurePrim {
    data class Measure(val n: Int) : StructurePrim
    data class Axis(val pts: FloatArray) : StructurePrim
    data class Line(val pts: FloatArray) : StructurePrim
    data class Poly(val pts: FloatArray) : StructurePrim
    data class Polygon(val pts: FloatArray) : StructurePrim
    data class Circle(val x: Float, val y: Float, val r: Float) : StructurePrim
    data class Ellipse(val x: Float, val y: Float, val rx: Float, val ry: Float, val deg: Float) : StructurePrim
    data class Joint(val x: Float, val y: Float) : StructurePrim
    data class Vanish(val x: Float, val y: Float) : StructurePrim
}

data class StructureGuide(val id: String, val group: StructureGroup, val nameKey: String, val aspect: Float, val prims: List<StructurePrim>)

object StructureGuides {
    const val FILE = "structure_guides.txt"

    /** İlk kullanımda bir kez okunur (küçük metin). */
    val all: List<StructureGuide> by lazy {
        val text = StructureGuides::class.java.classLoader?.getResourceAsStream(FILE)?.use { it.readBytes().decodeToString() } ?: ""
        parse(text)
    }

    fun byGroup(g: StructureGroup): List<StructureGuide> = all.filter { it.group == g }
    fun find(id: String): StructureGuide? = all.firstOrNull { it.id == id }

    fun parse(text: String): List<StructureGuide> {
        val out = ArrayList<StructureGuide>()
        var head: StructureGuide? = null
        var prims = ArrayList<StructurePrim>()
        fun flush() {
            head?.let { out += it.copy(prims = prims.toList()) }
            head = null
            prims = ArrayList()
        }
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val tag = line.first()
            val rest = line.drop(1).trim()
            if (tag == 'G') {
                flush()
                val f = rest.split('|')
                val g = f.getOrNull(1)?.let { runCatching { StructureGroup.valueOf(it) }.getOrNull() }
                val a = f.getOrNull(3)?.toFloatOrNull()
                if (f.size == 4 && g != null && a != null) head = StructureGuide(f[0], g, f[2], a, emptyList())
                continue
            }
            val n = rest.split(' ').mapNotNull { it.toFloatOrNull() }
            val arr = n.toFloatArray()
            when (tag) {
                'M' -> n.firstOrNull()?.let { prims += StructurePrim.Measure(it.toInt()) }
                'A' -> if (n.size >= 4) prims += StructurePrim.Axis(arr)
                'L' -> if (n.size >= 4) prims += StructurePrim.Line(arr)
                'P' -> if (n.size >= 4) prims += StructurePrim.Poly(arr)
                'Q' -> if (n.size >= 6) prims += StructurePrim.Polygon(arr)
                'C' -> if (n.size >= 3) prims += StructurePrim.Circle(n[0], n[1], n[2])
                'E' -> if (n.size >= 5) prims += StructurePrim.Ellipse(n[0], n[1], n[2], n[3], n[4])
                'K' -> if (n.size >= 2) prims += StructurePrim.Joint(n[0], n[1])
                'V' -> if (n.size >= 2) prims += StructurePrim.Vanish(n[0], n[1])
            }
        }
        flush()
        return out
    }
}
