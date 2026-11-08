package com.karapp.drawguidance.data.library

import com.karapp.drawguidance.content.model3d.Model3D
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * v23 3B düzenleyiciden tuvale dönen sonuçlar: tuvaldeki bir 3B şekil ([strokeId]) düzenlendi ya da tuvale yeni bir
 * 3B şekil eklenmesi istendi ([strokeId] null). Çizim ekranı açıldığında (geri dönünce) kendi tuvalininkileri alır.
 */
class Model3DResults {
    data class Result(val canvasId: String, val strokeId: String?, val model: Model3D)

    private val _pending = MutableStateFlow<List<Result>>(emptyList())
    val pending: StateFlow<List<Result>> = _pending.asStateFlow()

    fun post(result: Result) = _pending.update { list -> list.filterNot { it.canvasId == result.canvasId && it.strokeId == result.strokeId && it.strokeId != null } + result }

    /** Tuvalin bekleyen sonuçlarını alır (listeden çıkarır). */
    fun take(canvasId: String): List<Result> {
        var taken = emptyList<Result>()
        _pending.update { list ->
            taken = list.filter { it.canvasId == canvasId }
            list - taken.toSet()
        }
        return taken
    }
}
