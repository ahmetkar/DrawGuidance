package com.karapp.drawguidance.ui.drawing

import com.karapp.drawguidance.content.graph.Expression

/**
 * Grafik formüllerinin ayrıştırıcısı ve hesaplayıcısı: `y = x^2 - 3`, `sin(x)/x`, `z = x*y`.
 * v37 kendi ayrıştırıcısı kaldırıldı; [Expression] kullanılır (aynı sözdiziminin üst kümesi: örtük çarpım, Unicode
 * yazımlar, daha çok fonksiyon). Geçersiz ifadede [parse] null döner (kullanıcıya uyarı gösterilebilir).
 */
object Formula {

    fun interface Expr {
        /** Tanımsız noktalarda NaN döner (grafik orada kesilir). */
        fun eval(x: Double, y: Double): Double
    }

    /** `y = ...` / `z = ...` gibi ön ekleri atar. */
    fun body(text: String): String = Expression.body(text)

    fun parse(text: String): Expr? = Expression.parse(text)?.let { e -> Expr(e::eval) }
}
