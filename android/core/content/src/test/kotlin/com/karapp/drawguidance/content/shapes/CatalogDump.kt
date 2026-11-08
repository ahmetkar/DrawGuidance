package com.karapp.drawguidance.content.shapes

import org.junit.Assume
import org.junit.Test
import java.io.File

/** Yardımcı: şablon kataloğunu (anahtar, ad, kategori, çizgi sayısı) dosyaya döker (yalnız CATALOG_DUMP verilince). */
class CatalogDump {
    @Test fun dump() {
        val out = System.getenv("CATALOG_DUMP") ?: return Assume.assumeTrue(false)
        File(out).writeText(ShapeTemplates.catalog.joinToString("\n") { "${it.key}\t${it.name}\t${it.category}\t${it.strokeCount}" })
    }
}
