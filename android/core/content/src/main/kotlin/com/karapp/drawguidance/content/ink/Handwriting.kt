package com.karapp.drawguidance.content.ink

/**
 * Cihaz içi el yazısı tanıyıcı (saf Kotlin, ağ yok, ML kitaplığı yok): kalemle yazılmış rakam, harf (Türkçe dahil) ve
 * basit matematik simgelerini metne çevirir. Tek satır varsayılır (1–12 karakter; sözcük arası boşluklar korunur).
 *
 * Yöntem:
 * 1. Ön süzgeç ([looksLikeWriting]): sarmal/karalama (dolanma, kendini kesme), zikzak/tarama (keskin dönüşler), tek düz
 *    yatay çizgi, dikdörtgen çerçeve, üst üste yığılmış çok çizgi, çok satırlı mürekkep elenir.
 * 2. Satır eğimi (≥ 3 karakterde, taban noktalarına RANSAC doğru) düzeltilir.
 * 3. Çizgiler x'e göre sıralanır; ardışık en çok 5 çizgi aday karakter grubu olur. Grupta küçük işaretler (i/j/İ noktası,
 *    ü/ö noktaları ya da tek tire, ğ yayı, ç/ş çengeli, ! ? noktası, ÷ noktaları) gövdeden ayrılır.
 * 4. Gövde şekli: 8×8 ızgarada yön öznitelikleri (çizgi yönü/sırasından bağımsız) ile bozulmalarla çoğaltılmış
 *    şablonlara en yakın komşu + en iyi adaylarda nokta bulutu (chamfer) uzaklığı.
 * 5. Satır ölçüsü varsayımları (taban çizgisi + büyük harf yüksekliği) altında konum puanı (o/O/0, p/P, c/C, ı/l/1 ...),
 *    ışın aramalı dinamik programlama ile bölütleme + bağlam (rakam/harf/simge geçişleri) → en iyi okumalar.
 *
 * Bağlam kuralları: çarpı biçimli karakter iki rakamın arasındaysa "×" (3×4), aksi halde "x" (DIGITS kipinde hep "×");
 * "−" (eksi) bir yanında harf varsa "-" (tire) olur, LETTERS kipinde hep "-". Aynı biçimli karakterlerde (o/O/0, l/I/1,
 * c/C ...) karar boy/konum ve komşulara göre verilir; tek başına yazılmışsa diğerleri [Recognition.alternatives]'dedir.
 * Tek başına tek çizgilik daire ya da dik çizgi (0, O, 1, l) çizim de olabileceği için daha düşük güvenle döner.
 *
 * v23 dil modeli: dinamik programlamada harf geçişlerine Türkçe harf ikilisi maliyeti ([TurkishLexicon], sık
 * sözcüklerden çıkarılmış, Laplace düzeltmeli) eklenir; en iyi okumalar arasında sözlükte bulunan sözcükler öne alınır.
 * Rakam ve simgeler etkilenmez; tek başına yazılmış harfte dil modeli kullanılmaz. Uygulamada yazı önerisi şekil
 * önerisinden ayrı bir algoritmadır (bkz. uygulamadaki TextSuggester / ShapeSuggester).
 *
 * v37 yazı ağı ([HandwritingNet], not alma modunun yazı düzeltme ağı; şekil düzeltme ağından ayrı): Latin çözücüde
 * gövde şekline ağın olasılığı da eklenir; Kiril, Arapça (ayrık harfler), Japonca (kana + sık kanji) ve Korece
 * (bileşenlere ayrılmış hece tanıma) [ScriptDecoder] ile okunur. Yazı sistemi [language] ipucundan (arayüz dili:
 * "tr", "en", "ru", "ar", "ja", "ko" …; "zh" desteklenmez, kendiliğinden bulunur) ve ağın yazı sistemi başından
 * seçilir; ipucu yoksa Latin önceliklidir (eski davranış). DIGITS kipinde hep Latin çözücü kullanılır.
 */
object Handwriting {

    /** Bu kadar çizgiden fazlası metin sayılmaz. */
    const val MAX_STROKES = 40

    /** v37 Japonca/Korece (karakter başına çok çizgi): en çok çizgi. */
    const val MAX_STROKES_CJK = 120

    /** Bu güvenin altındaki okumalar [recognize] tarafından null döner. */
    const val MIN_CONFIDENCE = 0.35f

    /**
     * null: mürekkep metne benzemiyor (çizim, karalama, çok çizgi) ya da en iyi güven < [MIN_CONFIDENCE].
     * [language]: v37 isteğe bağlı dil/yazı sistemi ipucu (bkz. [InkScript.fromHint]); null = kendiliğinden bul.
     */
    fun recognize(strokes: List<InkStroke>, mode: InkMode = InkMode.MIXED, lexicon: Boolean = true, language: String? = null): Recognition? {
        val r = analyze(strokes, mode, lexicon, language) ?: return null
        return r.takeIf { it.confidence >= MIN_CONFIDENCE }
    }

    /**
     * Hızlı ön süzgeç: bu çizgiler el yazısı olabilir mi (boyut/oran/çizgi sayısı/karalama sezgileri)? v37: Japonca ve
     * Korece (ipucuyla ya da kendiliğinden bulunursa) çok çizgili karakterler için gevşek kurallar.
     */
    fun looksLikeWriting(strokes: List<InkStroke>, language: String? = null): Boolean {
        val polys = Prep.polys(strokes)
        if (polys.isEmpty()) return false
        val ps = polys.map { it.second }
        if (polys.size <= MAX_STROKES && Prep.looksLikeWriting(ps)) return true
        if (polys.size > MAX_STROKES_CJK) return false
        val net = HandwritingNet.active ?: return false
        val hint = InkScript.fromHint(language)
        val script = if (hint == InkScript.JAPANESE || hint == InkScript.KOREAN) hint else ScriptDecoder.detect(ps, net, hint)?.first
        return (script == InkScript.JAPANESE || script == InkScript.KOREAN) && Prep.looksLikeWriting(ps, script)
    }

    /**
     * v38 yazı önerisinin sinir ağı kapısı: birbirine yakın elemanların yazı olma olasılığı ([HandwritingNet.writingProb];
     * yazı ağı ya da kapısı yoksa null). Çizimleri (şekil, çizgi grupları) yazıdan ayırır; yazı önerisi
     * [looksLikeWriting] ve bu kapıdan geçerse tanınır.
     */
    fun writingScore(strokes: List<InkStroke>): Float? {
        val ps = Prep.polys(strokes).map { it.second }
        if (ps.isEmpty() || ps.size > MAX_STROKES_CJK) return null
        return HandwritingNet.active?.writingProb(ps)
    }

    /** v38 kapının eşiği: bu olasılığın altında yazı önerilmez (ayarı: TextGateTraining raporu). */
    const val GATE_MIN = 0.5f

    /** v37: mürekkebin yazı sistemi ([language] ipucu önseldir); yazı ağı yoksa null. */
    fun detectScript(strokes: List<InkStroke>, language: String? = null): InkScript? {
        val ps = Prep.polys(strokes).map { it.second }
        if (ps.size > MAX_STROKES_CJK) return null
        val net = HandwritingNet.active ?: return null
        return ScriptDecoder.detect(ps, net, InkScript.fromHint(language))?.first
    }

    /** Şablonları ve yazı ağını önceden hazırlar (uygulama açılışında arka planda çağrılabilir; ~100–300 ms). */
    fun warmUp() {
        ShapeModel.warmUp()
        HandwritingNet.active
    }

    /** Güven eşiği uygulanmadan tanıma (testler ve eşik ayarı için). */
    internal fun analyze(strokes: List<InkStroke>, mode: InkMode = InkMode.MIXED, lexicon: Boolean = true, language: String? = null): Recognition? {
        val polys = Prep.polys(strokes)
        // çok çizgili mürekkep (çizim) yazı ağına hiç sokulmaz
        if (polys.isEmpty() || polys.size > MAX_STROKES_CJK) return null
        val ps = polys.map { it.second }
        val net = HandwritingNet.active
        val script = when {
            mode == InkMode.DIGITS || net == null -> InkScript.LATIN
            else -> ScriptDecoder.detect(ps, net, InkScript.fromHint(language))?.first ?: InkScript.LATIN
        }
        if (script == InkScript.LATIN || net == null) {
            if (polys.size > MAX_STROKES) return null
            if (!Prep.looksLikeWriting(ps)) return null
            return Decoder(polys, mode, lexicon).decode()
        }
        if (!Prep.looksLikeWriting(ps, script)) return null
        return ScriptDecoder(polys, script, net).decode()
    }
}
