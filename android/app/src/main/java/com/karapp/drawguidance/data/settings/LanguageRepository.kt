package com.karapp.drawguidance.data.settings

import android.content.Context
import com.karapp.drawguidance.data.local.LanguageSettingsDao
import com.karapp.drawguidance.data.local.LanguageSettingsEntity
import com.karapp.drawguidance.i18n.AppLanguage
import com.karapp.drawguidance.i18n.I18n
import com.karapp.drawguidance.i18n.LanguageManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * v34 dil ayarı: veritabanındaki `language_settings` satırı esastır; ayar dosyası yalnız açılışta (veritabanı
 * açılmadan) okunabilsin diye aynasıdır.
 */
class LanguageRepository(
    private val context: Context,
    private val dao: LanguageSettingsDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Etkin dil (bu oturumda uygulanmış olan). */
    val active: AppLanguage get() = AppLanguage.of(I18n.language)

    val settings: Flow<LanguageSettingsEntity?> = dao.observe()
    val language: Flow<AppLanguage> = settings.map { AppLanguage.of(it?.languageCode ?: I18n.language) }

    /**
     * Açılışta: veritabanında satır yoksa (ilk açılış ya da v13'e geçiş) algılanan/seçili dil yazılır. Satır varsa
     * ve ayar dosyasından farklıysa (ör. yedekten dönüş) veritabanındaki kullanılır: ayar dosyası güncellenir, sonraki
     * açılışta uygulanır. Dönüş: veritabanındaki dil.
     */
    suspend fun ensureStored(): AppLanguage {
        val stored = LanguageManager.stored(context)
        val row = dao.get()
        if (row == null) {
            dao.upsert(LanguageSettingsEntity(languageCode = stored.code, auto = stored.auto, systemLanguage = stored.systemLanguage, systemCountry = stored.systemCountry, updatedAt = clock()))
            return AppLanguage.of(stored.code)
        }
        if (row.languageCode != stored.code) LanguageManager.write(context, row.languageCode, row.auto)
        return AppLanguage.of(row.languageCode)
    }

    /** Kullanıcının seçimi: veritabanına ve ayar dosyasına yazılır (uygulama yeniden başlatılınca her yere uygulanır). */
    suspend fun select(language: AppLanguage) {
        val row = dao.get()
        val sys = LanguageManager.systemLocale()
        dao.upsert(
            LanguageSettingsEntity(
                languageCode = language.code, auto = false,
                systemLanguage = row?.systemLanguage ?: sys.language, systemCountry = row?.systemCountry ?: sys.country,
                updatedAt = clock(),
            ),
        )
        LanguageManager.write(context, language.code, auto = false)
    }
}
