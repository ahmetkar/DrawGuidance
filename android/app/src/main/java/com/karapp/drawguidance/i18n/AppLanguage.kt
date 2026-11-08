package com.karapp.drawguidance.i18n

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.telephony.TelephonyManager
import java.util.Locale

/** v34 uygulamanın dilleri (Türkçe varsayılan). [nativeName] dil seçicide dilin kendi adıyla yazılır. */
enum class AppLanguage(val code: String, val nativeName: String, val locale: Locale, val rtl: Boolean = false) {
    TR("tr", "Türkçe", Locale("tr", "TR")),
    EN("en", "English", Locale("en", "US")),
    ZH("zh", "中文（简体）", Locale("zh", "CN")),
    JA("ja", "日本語", Locale("ja", "JP")),
    RU("ru", "Русский", Locale("ru", "RU")),
    KO("ko", "한국어", Locale("ko", "KR")),
    AR("ar", "العربية", Locale("ar"), rtl = true);

    companion object {
        fun of(code: String?): AppLanguage = entries.firstOrNull { it.code == code?.lowercase(Locale.ROOT) } ?: TR

        fun byCode(code: String?): AppLanguage? = entries.firstOrNull { it.code == code?.lowercase(Locale.ROOT) }

        /** Ülkeden dil (sistem dili desteklenmiyorsa). */
        private val COUNTRIES: Map<String, AppLanguage> = buildMap {
            put("TR", TR); put("CY", TR)
            listOf("CN", "TW", "HK", "MO", "SG").forEach { put(it, ZH) }
            put("JP", JA)
            listOf("RU", "BY", "KZ", "KG").forEach { put(it, RU) }
            listOf("KR", "KP").forEach { put(it, KO) }
            listOf("SA", "AE", "EG", "IQ", "JO", "KW", "LB", "LY", "MA", "OM", "QA", "SY", "TN", "YE", "BH", "DZ", "SD", "PS", "MR")
                .forEach { put(it, AR) }
            listOf("US", "GB", "AU", "CA", "NZ", "IE").forEach { put(it, EN) }
        }

        /**
         * İlk açılışta dil: önce sistemin dili bu dillerden biriyse o; değilse sistemin (ya da SIM kartın/şebekenin)
         * ülkesinin dili; ikisi de eşleşmezse İngilizce.
         */
        fun detect(systemLanguage: String?, vararg countries: String?): AppLanguage {
            val lang = systemLanguage?.lowercase(Locale.ROOT)?.substringBefore('-')?.substringBefore('_')
            byCode(lang)?.let { return it }
            for (c in countries) {
                val country = c?.trim()?.uppercase(Locale.ROOT)?.takeIf { it.length == 2 } ?: continue
                COUNTRIES[country]?.let { return it }
            }
            return EN
        }
    }
}

/**
 * v34 dilin uygulanması: seçili dil ayar dosyasında da tutulur (veritabanındaki `language_settings` satırının
 * aynası) çünkü etkinlik ve uygulama bağlamı veritabanı açılmadan önce (`attachBaseContext`) kurulur.
 */
object LanguageManager {
    private const val PREFS = "dg_language"
    const val KEY_CODE = "code"
    const val KEY_AUTO = "auto"
    const val KEY_SYS_LANG = "system_language"
    const val KEY_SYS_COUNTRY = "system_country"

    /** Testler: ilk açılışta algılama yerine bu dil (null: sistemden algıla). */
    @Volatile
    var initialOverride: String? = null

    /** Sistemin (uygulamanın değil) dili ve ülkesi. */
    fun systemLocale(): Locale {
        val config = Resources.getSystem().configuration
        @Suppress("DEPRECATION")
        return if (Build.VERSION.SDK_INT >= 24) config.locales[0] ?: Locale.getDefault() else config.locale ?: Locale.getDefault()
    }

    private fun simCountries(context: Context): List<String?> = runCatching {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        listOf(tm?.simCountryIso, tm?.networkCountryIso)
    }.getOrDefault(emptyList())

    /**
     * Etkin dil: ayar dosyasında yoksa (ilk açılış) sistemden algılanır ve yazılır. Veritabanına uygulama açılınca
     * [com.karapp.drawguidance.data.settings.LanguageRepository.ensureStored] yazar.
     */
    fun current(context: Context): AppLanguage {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_CODE, null)?.let { return AppLanguage.of(it) }
        val sys = systemLocale()
        val detected = initialOverride?.let { AppLanguage.of(it) } ?: AppLanguage.detect(sys.language, sys.country, *simCountries(context).toTypedArray())
        prefs.edit()
            .putString(KEY_CODE, detected.code).putBoolean(KEY_AUTO, true)
            .putString(KEY_SYS_LANG, sys.language).putString(KEY_SYS_COUNTRY, sys.country)
            .commit()
        return detected
    }

    /** Ayar dosyasındaki kayıt (veritabanına aktarmak için). */
    data class Stored(val code: String, val auto: Boolean, val systemLanguage: String?, val systemCountry: String?)

    fun stored(context: Context): Stored {
        val lang = current(context)
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Stored(lang.code, p.getBoolean(KEY_AUTO, true), p.getString(KEY_SYS_LANG, null), p.getString(KEY_SYS_COUNTRY, null))
    }

    /** Ayar dosyasına yazar (seçim kullanıcıdansa auto = false). */
    fun write(context: Context, code: String, auto: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_CODE, AppLanguage.of(code).code).putBoolean(KEY_AUTO, auto).commit()
    }

    /**
     * Etkinliğin üstüne yazılan yapılandırma (`applyOverrideConfiguration`): yalnız dil ve yön tanımlı, öteki
     * alanlar (yazı boyutu, yön, gece modu) sistemden gelir. Etkinlik kendi yapılandırma değişikliklerini işlese de
     * dil korunur.
     */
    fun overrideConfiguration(lang: AppLanguage): Configuration = Configuration().apply {
        fontScale = 0f
        setLocale(lang.locale)
        setLayoutDirection(lang.locale)
    }

    /** Bağlamın yapılandırmasına dil (ve sağdan sola yön) uygulanır; koddaki metin tablosu da seçilir. */
    fun wrap(base: Context): Context {
        val lang = current(base)
        I18n.setLanguage(lang.code)
        val config = Configuration(base.resources.configuration)
        config.setLocale(lang.locale)
        config.setLayoutDirection(lang.locale)
        return base.createConfigurationContext(config)
    }
}
