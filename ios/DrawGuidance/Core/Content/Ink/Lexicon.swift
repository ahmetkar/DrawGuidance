import Foundation

/// v23 Türkçe dil modeli (el yazısı tanımada; Android `TurkishLexicon` ile aynı): sık sözcükler ve onlardan çıkarılan
/// harf ikilisi maliyetleri.
enum TurkishLexicon {
    private static let WORDS = """
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

    /// Sözlük (küçük harf, Türkçe kurallarıyla).
    static let words: Set<String> = Set(wordList)
    private static let wordList: [String] = {
        var seen = Set<String>()
        var out: [String] = []
        for w in WORDS.split(whereSeparator: { $0.isWhitespace }) {
            let l = lower(String(w))
            if l.count >= 2 && l.allSatisfy({ $0.isLetter }) && !seen.contains(l) { seen.insert(l); out.append(l) }
        }
        return out
    }()

    private static let ALPHA = Array("abcçdefgğhıijklmnoöprsştuüvyzqwx")
    private static let index: [Character: Int] = Dictionary(uniqueKeysWithValues: ALPHA.enumerated().map { ($0.element, $0.offset) })
    private static let n = ALPHA.count

    /// −ln P(sonraki | önceki) − ortalama; başlangıç ve bitiş için ayrı satır/sütun.
    private static let table: [Float] = {
        var counts = [[Float]](repeating: [Float](repeating: 0, count: n + 1), count: n + 1)
        for w in wordList {
            var prev = n
            for ch in w {
                guard let i = index[ch] else { continue }
                counts[prev][i] += 1
                prev = i
            }
            counts[prev][n] += 1
        }
        let alpha: Float = 0.4
        var out = [Float](repeating: 0, count: (n + 1) * (n + 1))
        var mean = 0.0
        var cnt = 0
        for a in 0...n {
            let row = counts[a]
            var rs: Float = 0
            for v in row { rs += v }
            let total = rs + alpha * Float(n + 1)
            for b in 0...n {
                let v = -Float(log(Double((row[b] + alpha) / total)))
                out[a * (n + 1) + b] = v
                if row[b] > 0 { mean += Double(v * row[b]); cnt += Int(row[b]) }
            }
        }
        let m = Float(mean / Double(max(cnt, 1)))
        for i in out.indices { out[i] = min(max(out[i] - m, -2), 4) }
        return out
    }()

    /// Türkçe küçük harf (I → ı, İ → i).
    static func lower(_ s: String) -> String { String(s.map(lower)) }

    static func lower(_ c: Character) -> Character {
        switch c {
        case "I": return "ı"
        case "İ": return "i"
        default:
            let l = c.lowercased()
            return l.count == 1 ? Character(l) : c
        }
    }

    /// Harf geçişinin maliyeti (prev nil: sözcük başı); harf değilse 0.
    static func cost(_ prev: Character?, _ next: Character) -> Float {
        guard let b = index[lower(next)] else { return 0 }
        let a: Int
        if let prev { guard let p = index[lower(prev)] else { return 0 }; a = p } else { a = n }
        return table[a * (n + 1) + b]
    }

    /// Sözcük sonu maliyeti.
    static func endCost(_ last: Character) -> Float {
        guard let a = index[lower(last)] else { return 0 }
        return table[a * (n + 1) + n]
    }

    static func contains(_ word: String) -> Bool { words.contains(lower(word)) }
}
