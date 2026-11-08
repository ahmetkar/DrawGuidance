package com.karapp.drawguidance.content.ink

/**
 * v37 el yazısının yazı sistemi: yazı ağı ([HandwritingNet]) her biri için ayrı çıktı katmanı (baş) taşır.
 *
 * - [LATIN]: Latin harfleri (Türkçe dahil), rakamlar ve matematik simgeleri (mevcut çözücü + ağ).
 * - [CYRILLIC]: Rus alfabesi (33 harf; biçimi aynı küçük/büyük harfler tek sınıf, boya göre ayrılır).
 * - [ARABIC]: Arap harflerinin yalın (ayrık) biçimleri ve Hint-Arap rakamları; sağdan sola. Bitişik (el yazısı)
 *   Arapça desteklenmez: harfler ayrı ayrı yazılmalıdır.
 * - [JAPANESE]: hiragana, katakana (sesli/yarı sesli işaretli biçimler dahil) ve sık kullanılan küçük bir kanji kümesi.
 * - [KOREAN]: tek başına yazılan jamo ve sık kullanılan hece blokları (bkz. ağın eğitim notları).
 */
enum class InkScript(val rtl: Boolean = false) {
    LATIN, CYRILLIC, ARABIC(rtl = true), JAPANESE, KOREAN;

    companion object {
        /**
         * Uygulamanın arayüz dilinden (BCP-47: "tr", "en", "ru", "ar", "ja", "ko", "zh"…) ya da yazı sistemi adından
         * ("LATIN", "cyrillic"…) yazı sistemi; bilinmiyorsa (ör. "zh") null: kendiliğinden bulunur.
         */
        fun fromHint(hint: String?): InkScript? {
            if (hint.isNullOrBlank()) return null
            entries.firstOrNull { it.name.equals(hint, ignoreCase = true) }?.let { return it }
            return when (hint.lowercase().substringBefore('-').substringBefore('_')) {
                "tr", "en", "de", "fr", "es", "it", "pt", "nl", "az" -> LATIN
                "ru", "uk", "be", "bg", "sr", "kk" -> CYRILLIC
                "ar", "fa", "ur" -> ARABIC
                "ja" -> JAPANESE
                "ko" -> KOREAN
                else -> null
            }
        }
    }
}
