package com.karapp.drawguidance.ui.screens.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karapp.drawguidance.diagnostics.AppLog
import com.karapp.drawguidance.diagnostics.LogEntry
import com.karapp.drawguidance.diagnostics.LogLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Günlük süzgeci: tümü ya da en az bu önemdekiler. */
enum class LogFilter(val minimum: LogLevel?) { ALL(null), ERRORS(LogLevel.ERROR), WARNINGS(LogLevel.WARN), INFO(LogLevel.INFO) }

/** Geliştirici modu günlük ekranı: kayıtları arka planda okur, önem ve metne göre süzer. */
class LogsViewModel(private val app: android.content.Context) : ViewModel() {
    private val _crash = MutableStateFlow<String?>(null)
    /** Son çökme kaydı (dosyadan arka planda okunur). */
    val crash: StateFlow<String?> = _crash.asStateFlow()

    fun clearCrash() {
        _crash.value = null
        viewModelScope.launch(Dispatchers.IO) { com.karapp.drawguidance.diagnostics.Diagnostics.clearLastCrash(app) }
    }

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _filter = MutableStateFlow(LogFilter.ALL)
    val filter: StateFlow<LogFilter> = _filter.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** Süzülmüş kayıtlar, en yenisi en üstte. */
    val visible: StateFlow<List<LogEntry>> = combine(_entries, _filter, _query) { entries, filter, query ->
        entries.asReversed().filter { e ->
            (filter.minimum == null || e.level.ordinal >= filter.minimum.ordinal) &&
                (query.isBlank() || e.message.contains(query, ignoreCase = true) || e.tag.contains(query, ignoreCase = true))
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Önem sayıları (süzgeç çiplerinde). */
    val counts: StateFlow<Map<LogFilter, Int>> = combine(_entries, _query) { entries, _ ->
        LogFilter.entries.associateWith { f -> entries.count { f.minimum == null || it.level.ordinal >= f.minimum.ordinal } }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    init { refresh() }

    fun refresh() {
        _loading.value = true
        viewModelScope.launch {
            _entries.value = withContext(Dispatchers.IO) { AppLog.collect() }
            _crash.value = withContext(Dispatchers.IO) { com.karapp.drawguidance.diagnostics.Diagnostics.lastCrash(app) }
            _loading.value = false
        }
    }

    fun setFilter(value: LogFilter) { _filter.value = value }
    fun setQuery(value: String) { _query.value = value }

    fun clear() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { AppLog.clear() }
            _entries.value = emptyList()
        }
    }

    /** Görünen kayıtların düz metni (kopyala/paylaş). */
    fun exportText(): String = AppLog.format(visible.value.asReversed())
}
