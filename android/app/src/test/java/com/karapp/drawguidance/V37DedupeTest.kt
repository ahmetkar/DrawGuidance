package com.karapp.drawguidance

import com.karapp.drawguidance.content.graph.Expression
import com.karapp.drawguidance.ui.drawing.Formula
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * v37 (6) tekrar eden kodlar: grafik formülleri tek ayrıştırıcıyla ([Expression]) hesaplanır; alt sayfalar ortak
 * kaptan ([com.karapp.drawguidance.ui.components.NeuSheet]) açılır.
 */
class V37DedupeTest {
    @Test
    fun formulaUsesSharedParser() {
        // Eski sözdizimi aynı sonucu verir
        assertEquals(4.0, Formula.parse("y = x^2")!!.eval(2.0, 0.0), 1e-9)
        assertEquals(1.5, Formula.parse("1,5")!!.eval(0.0, 0.0), 1e-9)
        assertNull(Formula.parse("bilinmeyen(3)"))
        // Ortak ayrıştırıcının yazımları da çalışır (örtük çarpım, Unicode)
        listOf("2πx", "x² + 1", "sin x", "√x", "|x - 3|").forEach { f ->
            assertEquals(f, Expression.parse(f)!!.eval(4.0, 0.0), Formula.parse(f)!!.eval(4.0, 0.0), 1e-9)
        }
    }

    @Test
    fun sheetsUseSharedContainer() {
        val src = File("src/main/java").takeIf { it.exists() } ?: File("app/src/main/java")
        val repeated = src.walkTopDown().filter { it.extension == "kt" && it.name != "NeuSheet.kt" }.filter { f ->
            Regex("""ModalBottomSheet\(\s*onDismissRequest[^)]*?skipPartiallyExpanded = true\),\s*containerColor = [\w.]+\.background,\s*contentColor = [\w.]+\.content,\s*\)""")
                .containsMatchIn(f.readText())
        }.map { it.name }.toList()
        assertFalse("ortak kap yerine yinelenen alt sayfa: $repeated", repeated.isNotEmpty())
    }
}
