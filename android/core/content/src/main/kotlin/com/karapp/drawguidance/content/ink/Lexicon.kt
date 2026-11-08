package com.karapp.drawguidance.content.ink

import kotlin.math.ln

/**
 * v23 Türkçe dil modeli (el yazısı tanımada): sık kullanılan sözcükler ve onlardan çıkarılan harf ikilisi olasılıkları.
 * Çözücü harf ardışıklarına ikili maliyeti ekler (Türkçede olası olmayan "qx", "ğğ" gibi geçişler pahalılaşır); en iyi
 * okumalar arasında sözlükte bulunan sözcükler öne alınır. Rakam ve simgelere dokunmaz.
 */
object TurkishLexicon {

    private const val WORDS = """
ve bir bu da de için ile çok daha ne gibi kadar ama ben sen o biz siz onlar var yok evet hayır şey her hiç en
gün gece sabah akşam öğle zaman yıl ay hafta saat dakika saniye bugün yarın dün şimdi sonra önce artık hep bazen
ev okul sınıf ders not kitap defter kalem silgi çanta sıra tahta masa sandalye kapı pencere oda mutfak bahçe
sınav ödev proje konu soru cevap örnek problem çözüm sonuç neden amaç özet tanım kural formül grafik tablo şekil
matematik fizik kimya biyoloji tarih coğrafya türkçe edebiyat ingilizce felsefe müzik resim beden din bilgi
sayı rakam toplam fark çarpım bölüm kesir oran yüzde ortalama eşit büyük küçük artı eksi çarpı bölü kare küp kök
açı üçgen dörtgen daire çember yarıçap çap alan hacim çevre kenar köşe yükseklik uzunluk genişlik derinlik nokta doğru
hız ivme kuvvet kütle ağırlık enerji güç iş basınç sıcaklık ısı ışık ses dalga elektrik akım gerilim direnç mıknatıs
madde atom molekül element bileşik karışım çözelti asit baz tuz su hava toprak ateş metal oksijen hidrojen karbon
hücre doku organ sistem kalp beyin kan kemik kas sinir bitki hayvan insan canlı besin yaprak kök gövde çiçek tohum
merhaba selam günaydın iyi akşamlar geceler teşekkür teşekkürler sağol lütfen tamam peki hoşça kal görüşürüz
anne baba kardeş abla abi dede nine teyze amca dayı hala kuzen aile çocuk bebek arkadaş öğretmen öğrenci müdür doktor
adam kadın erkek kız oğlan insan halk millet devlet ülke şehir köy kasaba mahalle sokak cadde yol köprü bina
türkiye istanbul ankara izmir bursa antalya adana konya trabzon samsun çorum erzurum eskişehir gaziantep diyarbakır
ali veli ahmet mehmet mustafa ayşe fatma zeynep elif emre can deniz ece ece cem selin burak mert şule ışık umut
yemek ekmek su çay kahve süt peynir yumurta et tavuk balık sebze meyve elma armut muz çilek üzüm portakal limon
domates patates soğan havuç biber salata çorba pilav makarna pasta kek şeker tuz yağ bal reçel zeytin kahvaltı
araba otobüs tren uçak gemi bisiklet motor taksi metro yol durak istasyon havalimanı liman bilet
güneş ay yıldız gök bulut yağmur kar rüzgar fırtına deniz göl nehir dağ tepe orman ağaç çiçek çimen taş kum ada
kedi köpek kuş balık at inek koyun keçi tavuk ördek tavşan fare aslan kaplan fil ayı kurt tilki arı kelebek yılan
renk kırmızı mavi yeşil sarı turuncu mor pembe siyah beyaz gri kahverengi lacivert
yeni eski güzel çirkin doğru yanlış kolay zor uzun kısa geniş dar yüksek alçak hızlı yavaş sıcak soğuk ılık
önemli gerekli mümkün farklı aynı benzer temiz kirli dolu boş açık kapalı ağır hafif kalın ince tatlı acı ekşi
mutlu üzgün yorgun hasta sağlıklı aç tok genç yaşlı zengin fakir güçlü zayıf akıllı çalışkan tembel
bir iki üç dört beş altı yedi sekiz dokuz on yirmi otuz kırk elli altmış yetmiş seksen doksan yüz bin milyon
birinci ikinci üçüncü dördüncü beşinci ilk son yarım çeyrek tam
pazartesi salı çarşamba perşembe cuma cumartesi pazar ocak şubat mart nisan mayıs haziran temmuz ağustos eylül ekim kasım aralık
kış ilkbahar yaz sonbahar mevsim
gel git yap et ol al ver gör bak yaz oku çiz dinle sor bil anla düşün çalış başla bitir koş yürü otur kalk dur
gel geldi gitti yaptı etti oldu aldı verdi gördü baktı yazdı okudu çizdi dinledi sordu bildi anladı düşündü çalıştı
geliyor gidiyor yapıyor ediyor oluyor alıyor veriyor görüyor bakıyor yazıyor okuyor çiziyor çalışıyor
gelmek gitmek yapmak etmek olmak almak vermek görmek bakmak yazmak okumak çizmek dinlemek sormak bilmek anlamak
düşünmek çalışmak başlamak bitirmek koşmak yürümek oturmak kalkmak sevmek istemek söylemek konuşmak beklemek
seviyorum istiyorum biliyorum anlıyorum düşünüyorum çalışıyorum geliyorum gidiyorum yapıyorum
sevgi saygı umut hayal düş rüya fikir düşünce duygu his mutluluk sevinç korku öfke özlem
dünya hayat yaşam ölüm doğa çevre bilim sanat spor oyun futbol basketbol voleybol yüzme koşu maç takım gol
telefon bilgisayar ekran klavye fare internet program uygulama dosya klasör resim fotoğraf video mesaj posta
para fiyat ucuz pahalı alışveriş market pazar dükkan mağaza banka hesap kredi kart
sağ sol ön arka üst alt iç dış orta yan karşı yakın uzak burada orada şurada nerede nasıl neden niçin kim hangi
kaç ne zaman çünkü ancak fakat yani belki mutlaka sadece bile hem ya veya ise eğer gerçek gerçekten
liste plan program hedef görev toplantı rapor sunum özet başlık paragraf cümle kelime harf hece metin yazı
soru1 cevap1 alıştırma test deneme sınavı dersi konusu ödevi kitabı defteri kalemi okulu evi annesi babası
"""

    /** Sözlük (küçük harf, Türkçe kurallarıyla). */
    val words: Set<String> by lazy {
        WORDS.split(Regex("\\s+")).map { lower(it.trim()) }.filter { it.length >= 2 && it.all { c -> c.isLetter() } }.toSet()
    }

    private const val ALPHA = "abcçdefgğhıijklmnoöprsştuüvyzqwx"
    private val index: Map<Char, Int> = ALPHA.withIndex().associate { it.value to it.index }
    private val n = ALPHA.length

    /** −ln P(sonraki | önceki) − ortalama (ortalama geçiş ~0 maliyet); başlangıç ve bitiş için ayrı satır/sütun. */
    private val table: FloatArray by lazy {
        val counts = Array(n + 1) { FloatArray(n + 1) }
        for (w in words) {
            var prev = n // başlangıç
            for (ch in w) {
                val i = index[ch] ?: continue
                counts[prev][i] += 1f
                prev = i
            }
            counts[prev][n] += 1f // bitiş
        }
        val alpha = 0.4f
        val out = FloatArray((n + 1) * (n + 1))
        var mean = 0.0; var cnt = 0
        for (a in 0..n) {
            val row = counts[a]
            val total = row.sum() + alpha * (n + 1)
            for (b in 0..n) {
                val v = -ln((row[b] + alpha) / total)
                out[a * (n + 1) + b] = v
                if (row[b] > 0f) { mean += v * row[b]; cnt += row[b].toInt() }
            }
        }
        val m = (mean / cnt.coerceAtLeast(1)).toFloat()
        for (i in out.indices) out[i] = (out[i] - m).coerceIn(-2f, 4f)
        out
    }

    /** Türkçe küçük harf (I → ı, İ → i). */
    fun lower(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) sb.append(lower(c))
        return sb.toString()
    }

    fun lower(c: Char): Char = when (c) {
        'I' -> 'ı'
        'İ' -> 'i'
        else -> c.lowercaseChar()
    }

    /** Harf geçişinin maliyeti ([prev] null: sözcük başı); harf değilse 0. */
    fun cost(prev: Char?, next: Char): Float {
        val b = index[lower(next)] ?: return 0f
        val a = if (prev == null) n else index[lower(prev)] ?: return 0f
        return table[a * (n + 1) + b]
    }

    /** Sözcük sonu maliyeti. */
    fun endCost(last: Char): Float {
        val a = index[lower(last)] ?: return 0f
        return table[a * (n + 1) + n]
    }

    fun contains(word: String): Boolean = lower(word) in words
}
