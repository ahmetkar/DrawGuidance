package com.karapp.drawguidance.content.library.science

import com.karapp.drawguidance.content.library.LibCategory

/**
 * Fen ve geometri çizimleri (not tuvali): Fizik, Kimya, Biyoloji & Tıp, Uzay ("Fen" bölümü) ve Geometri
 * ("Geometri" bölümü). Şekil kimlikleri "sci.<alan>.<ad>" biçimindedir ve kayıtta saklanır: değiştirilmemeli.
 */
object ScienceLibrary {
    val categories: List<LibCategory> by lazy {
        listOf(
            Physics.category,
            Chemistry.category,
            Biology.category,
            Space.category,
            GeometryPlus.category,
        )
    }
}
