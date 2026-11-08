package com.karapp.drawguidance.data.repository

import com.karapp.drawguidance.data.local.CanvasTimeDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * v40 tuvalde geçirilen süre: düzenleyicinin [EditTimeTracker] ile ölçtüğü etkin süre buraya eklenir; Ana sayfa ve
 * Tuvallerim tuval kimliği → toplam ms eşlemini izler. Yazma uygulama ömrü boyunca süren kapsamda yapılır
 * (düzenleyici kapanırken başlayan yazma yarıda kalmasın).
 */
class CanvasTimeRepository(
    private val dao: CanvasTimeDao,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    /** Tuval kimliği → toplam etkin düzenleme süresi (ms). */
    fun observe(): Flow<Map<String, Long>> = dao.observeAll().map { rows -> rows.associate { it.canvasLocalId to it.timeSpentMs } }

    suspend fun add(canvasId: String, ms: Long, now: Long = System.currentTimeMillis()) {
        if (ms > 0) dao.add(canvasId, ms, now)
    }

    /** Beklemeden ekler (düzenleyiciden). */
    fun record(canvasId: String, ms: Long) {
        if (ms > 0) scope.launch { runCatching { add(canvasId, ms) } }
    }

    suspend fun prune() = dao.prune()
}
