package com.karapp.drawguidance.data.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * v32 eğitim modu: hangi tuval türünde (çizim, not alma, çizgi roman) eğitimin önerildiği ve hangi araç gruplarının
 * eğitiminin bitirildiği. [autoOffer] kapalıysa (birim testleri) ilk girişte öneri penceresi açılmaz; ⋮ menüsünden
 * istenince yine açılır.
 */
class TutorialPrefs(context: Context, val autoOffer: Boolean = true) {
    private val prefs = context.getSharedPreferences("tutorials", Context.MODE_PRIVATE)

    private val _completed = MutableStateFlow(prefs.getStringSet(KEY_DONE, emptySet()).orEmpty().toSet())
    /** Bitirilen araç gruplarının kimlikleri. */
    val completed: StateFlow<Set<String>> = _completed.asStateFlow()

    /** Bu tuval türünde ilk girişte eğitim önerildi mi. */
    fun offered(kind: String): Boolean = prefs.getBoolean(KEY_OFFERED + kind, false)

    fun markOffered(kind: String) {
        prefs.edit().putBoolean(KEY_OFFERED + kind, true).apply()
    }

    /**
     * v40 bu tuval türünde en son önerilen eğitim sürümü (0: hiç; v40'tan önce önerildiyse 1). Eski eğitimi bitirmiş
     * kullanıcıya yeni sürümün yenilikleri bir kez gösterilir.
     */
    fun version(kind: String): Int = prefs.getInt(KEY_VERSION + kind, if (offered(kind)) 1 else 0)

    fun markVersion(kind: String, version: Int) {
        prefs.edit().putInt(KEY_VERSION + kind, version).apply()
    }

    fun markCompleted(groupId: String) {
        val next = _completed.value + groupId
        _completed.value = next
        prefs.edit().putStringSet(KEY_DONE, next).apply()
    }

    /** Eğitim önerileri ilk girişteki gibi yeniden gösterilir (Ayarlar). */
    fun reset() {
        _completed.value = emptySet()
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_OFFERED = "offered_"
        const val KEY_DONE = "completed"
        const val KEY_VERSION = "version_"
    }
}
