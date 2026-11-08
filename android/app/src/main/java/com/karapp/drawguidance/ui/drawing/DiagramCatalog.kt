package com.karapp.drawguidance.ui.drawing

import com.karapp.drawguidance.data.model.DiagramBox
import com.karapp.drawguidance.data.model.DiagramEdge
import com.karapp.drawguidance.data.model.DiagramKind
import com.karapp.drawguidance.data.model.DiagramNode
import com.karapp.drawguidance.data.model.DiagramSide
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * v28 editoryal diyagram türlerinin (diagram-design galerisindeki 44 tür) hazır örnekleri ve nicel türlerin
 * yerleşimi. Örnekler 0..1 alanında, 4 birimlik ızgaraya yakın, sade yoğunlukta (en çok ~9 düğüm, 1–2 vurgu).
 *
 * - Yapı türleri elle yerleşimlidir ([isManual]): adımlar yerinde kalır, yeni adım odaktakinin yanına konur.
 * - Nicel türlerde ([isData]) değerler adımların yazısındaki sayılardır ("Ocak 120", "Kadro +64", "Toplam =",
 *   "Tasarım 1-3", "Ad 12 34"); yazı değişince yerleşim değerlerden yeniden hesaplanır.
 */
object DiagramCatalog {

    class Template(val nodes: List<DiagramNode>, val edges: List<DiagramEdge>, val manual: Boolean)

    /** Değerleri yazıdan okunan (yerleşimi değerlerden hesaplanan) türler. */
    val dataKinds: Set<DiagramKind> = setOf(
        DiagramKind.BAR, DiagramKind.WATERFALL, DiagramKind.LINE, DiagramKind.SCATTER, DiagramKind.RADAR, DiagramKind.POLAR,
        DiagramKind.TREEMAP, DiagramKind.HEATMAP, DiagramKind.DP_SECURITY, DiagramKind.SANKEY, DiagramKind.GANTT,
        DiagramKind.JOURNEY, DiagramKind.LAYERS, DiagramKind.LOOP, DiagramKind.SEQUENCE, DiagramKind.EXPLODED,
    )

    fun isData(kind: DiagramKind) = kind in dataKinds

    /** v28 türlerinden elle yerleşimli olanlar. */
    fun isManual(kind: DiagramKind): Boolean = kind.ordinal >= DiagramKind.SEQUENCE.ordinal && !isData(kind)

    /** v28 türü mü (eski türlerin yerleşimi [DiagramRenderer]'da). */
    fun isNew(kind: DiagramKind): Boolean = kind.ordinal >= DiagramKind.SEQUENCE.ordinal

    /** Oklar düz (yay, eğri, çoklu çizgi) çizilen türler: dik açılı yol kullanılmaz. */
    val straightKinds: Set<DiagramKind> = setOf(
        DiagramKind.RELATION, DiagramKind.VENN, DiagramKind.MIND_MAP, DiagramKind.CYCLE, DiagramKind.LOOP, DiagramKind.WARDLEY,
        DiagramKind.LINE, DiagramKind.SCATTER, DiagramKind.JOURNEY, DiagramKind.SEQUENCE, DiagramKind.SANKEY,
        DiagramKind.MEDALLION, DiagramKind.RADAR, DiagramKind.POLAR, DiagramKind.FISHBONE,
    )

    // ------------------------------------------------------------------------------------------ yardımcılar

    private fun n(
        id: String, text: String, x: Float, y: Float, w: Float, h: Float,
        box: DiagramBox = DiagramBox.ROUNDED, role: String? = null,
    ) = DiagramNode(id = id, text = text, x = x, y = y, w = w, h = h, box = box, role = role)

    private fun e(
        from: String, to: String, label: String = "", dashed: Boolean = false, arrow: Boolean = true, role: String? = null,
        fromSide: DiagramSide = DiagramSide.AUTO, toSide: DiagramSide = DiagramSide.AUTO, fromLabel: String? = null, toLabel: String? = null,
    ) = DiagramEdge(from, to, label, dashed, arrow, fromSide, toSide, role = role, fromLabel = fromLabel, toLabel = toLabel)

    private val R = DiagramSide.RIGHT
    private val L = DiagramSide.LEFT
    private val T = DiagramSide.TOP
    private val B = DiagramSide.BOTTOM

    /** Türün hazır örneği (v28 türleri; eskiler için null). */
    fun template(kind: DiagramKind): Template? = when (kind) {
        DiagramKind.SEQUENCE -> Template(
            listOf(
                n("a", "İstemci", 0f, 0f, 0f, 0f, DiagramBox.RECT), n("b", "API", 0f, 0f, 0f, 0f, DiagramBox.RECT, "focal"),
                n("c", "Önbellek", 0f, 0f, 0f, 0f, DiagramBox.RECT), n("d", "Veritabanı", 0f, 0f, 0f, 0f, DiagramBox.RECT, "store"),
            ),
            listOf(
                e("a", "b", "GET /ürün"), e("b", "c", "oku"), e("c", "b", "ıska", dashed = true),
                e("b", "d", "SELECT"), e("d", "b", "satırlar", dashed = true), e("b", "c", "yaz", dashed = true, role = "async"),
                e("b", "a", "200 OK", role = "accent"),
            ),
            manual = false,
        )
        DiagramKind.ER -> Template(
            listOf(
                n("cus", "Müşteri\n# id\nad\ne-posta", 0.02f, 0.04f, 0.26f, 0.34f, DiagramBox.TABLE),
                n("ord", "Sipariş\n# id\n→ müşteri_id\ntarih\ntoplam", 0.42f, 0.04f, 0.28f, 0.40f, DiagramBox.TABLE, "focal"),
                n("itm", "Sipariş kalemi\n# id\n→ sipariş_id\n→ ürün_id\nadet", 0.42f, 0.58f, 0.28f, 0.40f, DiagramBox.TABLE),
                n("prd", "Ürün\n# id\nad\nfiyat", 0.80f, 0.60f, 0.18f, 0.34f, DiagramBox.TABLE),
            ),
            listOf(
                e("cus", "ord", "verir", arrow = false, fromSide = R, toSide = L, fromLabel = "1", toLabel = "N"),
                e("ord", "itm", "içerir", arrow = false, fromSide = B, toSide = T, fromLabel = "1", toLabel = "N"),
                e("prd", "itm", "", arrow = false, fromSide = L, toSide = R, fromLabel = "1", toLabel = "N"),
            ),
            manual = true,
        )
        DiagramKind.UML_CLASS -> Template(
            listOf(
                n("ent", "Varlık\n--\n# id: UUID\n--\n+ eşit(o): Bool", 0.06f, 0.04f, 0.30f, 0.30f, DiagramBox.TABLE),
                n("rep", "«arayüz»\nDepo\n--\n--\n+ bul(id): T\n+ kaydet(t: T)", 0.62f, 0.04f, 0.32f, 0.30f, DiagramBox.TABLE),
                n("usr", "Kullanıcı\n--\n- ad: String\n- e-posta: String\n--\n+ giriş(): Bool", 0.06f, 0.56f, 0.30f, 0.40f, DiagramBox.TABLE, "focal"),
                n("urp", "KullanıcıDeposu\n--\n--\n+ bul(id): Kullanıcı\n+ sil(id)", 0.62f, 0.56f, 0.32f, 0.36f, DiagramBox.TABLE),
            ),
            listOf(
                e("usr", "ent", role = "inherit", fromSide = T, toSide = B),
                e("urp", "rep", role = "realize", dashed = true, fromSide = T, toSide = B),
                e("urp", "usr", "kullanır", role = "depend", dashed = true, fromSide = L, toSide = R),
            ),
            manual = true,
        )
        DiagramKind.DB_SCHEMA -> Template(
            listOf(
                n("users", "public.users\nid : uuid : PK\nemail : text : UQ\ncreated_at : timestamptz", 0.04f, 0.06f, 0.36f, 0.34f, DiagramBox.TABLE),
                n("orders", "public.orders\nid : uuid : PK\nuser_id : uuid : FK\ntotal : numeric(12,2)\nstatus : text : NN", 0.58f, 0.06f, 0.38f, 0.40f, DiagramBox.TABLE, "focal"),
                n("items", "public.order_items\nid : uuid : PK\norder_id : uuid : FK\nqty : int", 0.58f, 0.60f, 0.38f, 0.34f, DiagramBox.TABLE),
            ),
            listOf(
                e("orders", "users", "RESTRICT", fromSide = L, toSide = R),
                e("items", "orders", "ON DELETE CASCADE", role = "accent", fromSide = T, toSide = B),
            ),
            manual = true,
        )
        DiagramKind.SWIMLANE -> Template(
            listOf(
                n("l1", "MÜŞTERİ", 0f, 0.02f, 1f, 0.31f, DiagramBox.LANE),
                n("l2", "DESTEK", 0f, 0.345f, 1f, 0.31f, DiagramBox.LANE),
                n("l3", "MÜHENDİSLİK", 0f, 0.67f, 1f, 0.31f, DiagramBox.LANE),
                n("s1", "Talep açar", 0.20f, 0.10f, 0.18f, 0.15f),
                n("s2", "Sınıflandırır", 0.20f, 0.425f, 0.18f, 0.15f),
                n("s3", "Hatayı düzeltir", 0.44f, 0.75f, 0.18f, 0.15f, role = "focal"),
                n("s4", "Çözümü sunar", 0.66f, 0.425f, 0.18f, 0.15f),
                n("s5", "Onaylar", 0.80f, 0.10f, 0.16f, 0.15f),
            ),
            listOf(
                e("s1", "s2", fromSide = B, toSide = T), e("s2", "s3", "HATA", fromSide = B, toSide = L, role = "accent"),
                e("s3", "s4", fromSide = R, toSide = B), e("s4", "s5", fromSide = T, toSide = B),
            ),
            manual = true,
        )
        DiagramKind.KANBAN -> Template(
            listOf(
                n("c1", "Bekleyen · 3", 0.01f, 0.02f, 0.235f, 0.96f, DiagramBox.COLUMN),
                n("c2", "Sürüyor · 4/3", 0.258f, 0.02f, 0.235f, 0.96f, DiagramBox.COLUMN),
                n("c3", "İnceleme · 2/3", 0.506f, 0.02f, 0.235f, 0.96f, DiagramBox.COLUMN),
                n("c4", "Bitti · 2", 0.754f, 0.02f, 0.235f, 0.96f, DiagramBox.COLUMN),
                n("k1", "Giriş sayfası\nWEB-12 · ayşe", 0.02f, 0.13f, 0.215f, 0.13f, DiagramBox.CARD),
                n("k2", "Arama filtresi\nWEB-15 · can", 0.02f, 0.29f, 0.215f, 0.13f, DiagramBox.CARD),
                n("k3", "Ödeme API\nPAY-4 · ece", 0.268f, 0.13f, 0.215f, 0.13f, DiagramBox.CARD, "blocked"),
                n("k4", "Önbellek\nAPI-9 · mert", 0.268f, 0.29f, 0.215f, 0.13f, DiagramBox.CARD),
                n("k5", "Bildirimler\nAPP-3 · dila", 0.268f, 0.45f, 0.215f, 0.13f, DiagramBox.CARD, "waiting"),
                n("k6", "Profil\nAPP-7 · ali", 0.516f, 0.13f, 0.215f, 0.13f, DiagramBox.CARD),
                n("k7", "Tema\nAPP-1 · su", 0.764f, 0.13f, 0.215f, 0.13f, DiagramBox.CARD, "done"),
            ),
            emptyList(),
            manual = true,
        )
        DiagramKind.STORY_MAP -> Template(
            listOf(
                n("a1", "[ETKİNLİK 1]\nVeriyi bul", 0.16f, 0.02f, 0.19f, 0.13f, DiagramBox.CARD, "activity"),
                n("a2", "[ETKİNLİK 2]\nRapor kur", 0.37f, 0.02f, 0.19f, 0.13f, DiagramBox.CARD, "activity"),
                n("a3", "[ETKİNLİK 3]\nPaylaş", 0.58f, 0.02f, 0.19f, 0.13f, DiagramBox.CARD, "activity"),
                n("a4", "[ETKİNLİK 4]\nGüven", 0.79f, 0.02f, 0.19f, 0.13f, DiagramBox.CARD, "activity"),
                n("r1", "MVP", 0f, 0.19f, 1f, 0.26f, DiagramBox.LANE, "focal"),
                n("r2", "SÜRÜM 2", 0f, 0.47f, 1f, 0.26f, DiagramBox.LANE),
                n("r3", "SONRA", 0f, 0.75f, 1f, 0.23f, DiagramBox.LANE),
                n("u1", "Katalog arama\nRPT-101 · 3p", 0.16f, 0.24f, 0.19f, 0.15f, DiagramBox.CARD),
                n("u2", "Hazır şablon\nRPT-108 · 5p", 0.37f, 0.24f, 0.19f, 0.15f, DiagramBox.CARD),
                n("u3", "Bağlantı paylaş\nRPT-112 · 2p", 0.58f, 0.24f, 0.19f, 0.15f, DiagramBox.CARD),
                n("u4", "Satır izinleri\nRPT-114 · 8p", 0.79f, 0.24f, 0.19f, 0.15f, DiagramBox.CARD, "security"),
                n("u5", "Kayıtlı sorgular\nRPT-120 · 3p", 0.16f, 0.52f, 0.19f, 0.15f, DiagramBox.CARD),
                n("u6", "Grafik türleri\nRPT-131 · 5p", 0.37f, 0.52f, 0.19f, 0.15f, DiagramBox.CARD),
                n("u7", "Zamanlanmış e-posta\nRPT-140 · 5p", 0.58f, 0.80f, 0.19f, 0.15f, DiagramBox.CARD),
            ),
            emptyList(),
            manual = true,
        )
        DiagramKind.DEPLOYMENT -> Template(
            listOf(
                n("z1", "KENAR", 0.02f, 0.06f, 0.24f, 0.86f, DiagramBox.GROUP, "boundary"),
                n("z2", "PROD / EU-WEST-1", 0.30f, 0.06f, 0.36f, 0.86f, DiagramBox.GROUP, "boundary"),
                n("z3", "VERİ", 0.70f, 0.06f, 0.28f, 0.86f, DiagramBox.GROUP, "boundary"),
                n("cdn", "[CDN]\nCloudFront\nstatik · v3", 0.04f, 0.36f, 0.20f, 0.20f),
                n("api", "[POD]\nAPI · x3\nv2.4.1", 0.34f, 0.20f, 0.28f, 0.20f),
                n("wrk", "[POD]\nÇalışan · x2\nv1.9.0", 0.34f, 0.60f, 0.28f, 0.20f),
                n("db", "[YÖNETİLEN]\nRDS birincil\npostgres 15.4", 0.73f, 0.20f, 0.22f, 0.20f, role = "focal"),
                n("db2", "[YÖNETİLEN]\nRDS yedek\npostgres 15.4", 0.73f, 0.60f, 0.22f, 0.20f, role = "store"),
            ),
            listOf(
                e("cdn", "api", "HTTPS:443", role = "link", fromSide = R, toSide = L),
                e("api", "db", "TLS:5432", fromSide = R, toSide = L),
                e("wrk", "db", "", fromSide = R, toSide = L),
                e("api", "wrk", "KUYRUK", dashed = true, fromSide = B, toSide = T),
                e("db", "db2", "REPLİKA", dashed = true, role = "accent", fromSide = B, toSide = T),
            ),
            manual = true,
        )
        DiagramKind.NESTED -> Template(
            listOf(
                n("o1", "KURULUŞ", 0.02f, 0.02f, 0.96f, 0.96f, DiagramBox.GROUP),
                n("o2", "PROJE", 0.10f, 0.12f, 0.80f, 0.78f, DiagramBox.GROUP),
                n("o3", "KLASÖR", 0.18f, 0.22f, 0.64f, 0.60f, DiagramBox.GROUP),
                n("o4", "DOSYA", 0.26f, 0.32f, 0.48f, 0.42f, DiagramBox.GROUP, "focal"),
            ),
            emptyList(),
            manual = true,
        )
        DiagramKind.LAYERS -> Template(
            listOf(
                n("l1", "Sunum\nHTML · CSS", 0f, 0f, 0f, 0f, DiagramBox.RECT),
                n("l2", "Uygulama\nKotlin · Compose", 0f, 0f, 0f, 0f, DiagramBox.RECT),
                n("l3", "Etki alanı\niş kuralları", 0f, 0f, 0f, 0f, DiagramBox.RECT, "focal"),
                n("l4", "Veri\nRoom · SQLite", 0f, 0f, 0f, 0f, DiagramBox.RECT),
                n("l5", "Altyapı\nAndroid · JVM", 0f, 0f, 0f, 0f, DiagramBox.RECT),
            ),
            emptyList(),
            manual = false,
        )
        DiagramKind.LOOP -> Template(
            listOf(
                n("s0", "Topla\nsinyaller", 0f, 0f, 0f, 0f), n("s1", "Araştır\nkanıt", 0f, 0f, 0f, 0f),
                n("s2", "Karar\ninsan onaylar", 0f, 0f, 0f, 0f, role = "focal"), n("s3", "Uygula\niş çıkar", 0f, 0f, 0f, 0f),
                n("s4", "Ölç\nsonuçlar", 0f, 0f, 0f, 0f), n("s5", "Öğren\nyöntem güncellenir", 0f, 0f, 0f, 0f),
                n("hub", "Ortak hafıza\ntek kayıt, her tur", 0f, 0f, 0f, 0f, role = "hub"),
            ),
            (0 until 6).map { i -> e("s$i", "s${(i + 1) % 6}") } +
                listOf(e("s0", "hub", "SİNYAL", dashed = true, role = "spoke"), e("s3", "hub", "SONUÇ", dashed = true, role = "spoke")),
            manual = false,
        )
        DiagramKind.QUADRANT -> Template(
            listOf(
                n("ay", "ETKİ", 0.40f, 0.0f, 0.20f, 0.06f, DiagramBox.LABEL, "axis"),
                n("ax", "ÇABA", 0.86f, 0.47f, 0.14f, 0.06f, DiagramBox.LABEL, "axis"),
                n("i1", "Önbellek", 0.14f, 0.16f, 0.26f, 0.06f, DiagramBox.DOT, "focal"),
                n("i2", "Yeniden yazım", 0.62f, 0.20f, 0.30f, 0.06f, DiagramBox.DOT),
                n("i3", "Uçtan uca test", 0.22f, 0.34f, 0.26f, 0.06f, DiagramBox.DOT),
                n("i4", "Koyu tema", 0.58f, 0.66f, 0.26f, 0.06f, DiagramBox.DOT),
                n("i5", "Belgeler", 0.12f, 0.76f, 0.26f, 0.06f, DiagramBox.DOT),
                n("i6", "Yeni marka", 0.70f, 0.82f, 0.26f, 0.06f, DiagramBox.DOT),
            ),
            emptyList(),
            manual = true,
        )
        DiagramKind.WARDLEY -> Template(
            listOf(
                n("w1", "Kullanıcı", 0.58f, 0.02f, 0.20f, 0.10f, DiagramBox.DOT),
                n("w2", "Asistan", 0.50f, 0.20f, 0.20f, 0.10f, DiagramBox.DOT),
                n("w3", "Ajan düzeni", 0.18f, 0.40f, 0.22f, 0.10f, DiagramBox.DOT, "moving"),
                n("w4", "Bilgi tabanı", 0.36f, 0.56f, 0.22f, 0.10f, DiagramBox.DOT),
                n("w5", "Model API", 0.64f, 0.54f, 0.20f, 0.10f, DiagramBox.DOT),
                n("w6", "Bulut", 0.82f, 0.74f, 0.16f, 0.10f, DiagramBox.DOT),
            ),
            listOf(
                e("w1", "w2", arrow = false), e("w2", "w3", arrow = false), e("w2", "w5", arrow = false),
                e("w3", "w4", arrow = false), e("w5", "w6", arrow = false), e("w4", "w6", arrow = false),
            ),
            manual = true,
        )
        DiagramKind.JOURNEY -> Template(
            listOf("Kayıt", "İlk proje", "Davet", "Limite takıldı", "Ödeme").mapIndexed { i, t -> n("h$i", "[AŞAMA ${i + 1}]\n$t", 0f, 0f, 0f, 0f, DiagramBox.LABEL, "stage") } +
                listOf(4, 3, 4, 1, 3).mapIndexed { i, v -> n("m$i", "$v", 0f, 0f, 0f, 0f, DiagramBox.DOT, "mood") } +
                listOf("Formu doldurur", "Şablon seçer", "Ekip çağırır", "Uyarıyla durur", "Planı yükseltir")
                    .mapIndexed { i, t -> n("x$i", t, 0f, 0f, 0f, 0f, DiagramBox.LABEL, "action") } +
                listOf(n("p0", "Sınır belirsiz", 0f, 0f, 0f, 0f, DiagramBox.CARD, "pain")),
            emptyList(),
            manual = false,
        )
        DiagramKind.DEPENDENCY -> Template(
            listOf(
                n("app", "app\nuygulama", 0.38f, 0.02f, 0.24f, 0.13f),
                n("api", "api", 0.14f, 0.30f, 0.24f, 0.13f),
                n("ui", "ui", 0.62f, 0.30f, 0.24f, 0.13f),
                n("auth", "auth", 0.02f, 0.58f, 0.24f, 0.13f),
                n("db", "db-istemci", 0.38f, 0.58f, 0.24f, 0.13f, role = "focal"),
                n("log", "günlük", 0.74f, 0.58f, 0.24f, 0.13f, role = "store"),
                n("pg", "pg\nv3.23 · npm", 0.38f, 0.84f, 0.24f, 0.13f, role = "external"),
                n("cr", "crypto\nv1.4 · npm", 0.02f, 0.84f, 0.24f, 0.13f, role = "external"),
            ),
            listOf(
                e("app", "api", fromSide = B, toSide = T), e("app", "ui", fromSide = B, toSide = T),
                e("api", "auth", fromSide = B, toSide = T), e("api", "db", fromSide = B, toSide = T), e("ui", "db", fromSide = B, toSide = T),
                e("ui", "log", fromSide = B, toSide = T), e("auth", "db", fromSide = R, toSide = L), e("db", "pg", fromSide = B, toSide = T),
                e("auth", "cr", fromSide = B, toSide = T), e("db", "auth", "DÖNGÜ", dashed = true, role = "cycle", fromSide = B, toSide = B),
            ),
            manual = true,
        )
        DiagramKind.IT_STATE -> Template(
            listOf(
                n("z1", "TOPLAMA", 0.01f, 0.08f, 0.30f, 0.90f, DiagramBox.GROUP),
                n("z2", "İŞLEME", 0.35f, 0.08f, 0.30f, 0.90f, DiagramBox.GROUP),
                n("z3", "YAYIN", 0.69f, 0.08f, 0.30f, 0.90f, DiagramBox.GROUP),
                n("c1", "Anket sistemi\nCAPI · PostgreSQL", 0.03f, 0.18f, 0.26f, 0.17f),
                n("c2", "Eski uygulamalar\nyönetim portalları", 0.03f, 0.44f, 0.26f, 0.17f),
                n("c3", "Nüfus kaydı\ndış kaynak", 0.03f, 0.70f, 0.26f, 0.17f, role = "external"),
                n("c4", "Ortak sürücü\nsürüm denetimi yok", 0.37f, 0.18f, 0.26f, 0.19f, role = "focal"),
                n("c5", "Analist makineleri\nSPSS · Excel", 0.37f, 0.46f, 0.26f, 0.17f),
                n("c6", "SQL Server\nşirket içi", 0.37f, 0.72f, 0.26f, 0.17f, role = "store"),
                n("c7", "Eski portal\nelle darboğaz", 0.71f, 0.18f, 0.26f, 0.19f, role = "focal"),
                n("c8", "Web sitesi\nstatik sayfalar", 0.71f, 0.46f, 0.26f, 0.17f),
                n("c9", "Bakanlıklar\n~6 kurum", 0.71f, 0.72f, 0.26f, 0.17f, role = "external"),
            ),
            listOf(
                e("c1", "c4", "CSV", fromSide = R, toSide = L), e("c2", "c4", "E-POSTA", fromSide = R, toSide = L),
                e("c3", "c5", "EXCEL", dashed = true, fromSide = R, toSide = L), e("c4", "c5", "KOPYALA", role = "accent", dashed = true, fromSide = B, toSide = T),
                e("c5", "c6", "YÜKLE", fromSide = B, toSide = T), e("c4", "c7", "EXCEL", role = "accent", fromSide = R, toSide = L),
                e("c7", "c8", "WEB", fromSide = B, toSide = T), e("c8", "c9", "CSV", role = "link", dashed = true, fromSide = B, toSide = T),
            ),
            manual = true,
        )
        DiagramKind.HIGH_LEVEL -> Template(
            listOf(
                n("b0", "KAYNAKLAR", 0.0f, 0.0f, 0.2f, 0.06f, DiagramBox.LABEL, "chevron"),
                n("b1", "ALIM", 0.2f, 0.0f, 0.2f, 0.06f, DiagramBox.LABEL, "chevron"),
                n("b2", "DEPOLAMA", 0.4f, 0.0f, 0.2f, 0.06f, DiagramBox.LABEL, "chevron"),
                n("b3", "DÖNÜŞÜM", 0.6f, 0.0f, 0.2f, 0.06f, DiagramBox.LABEL, "chevron"),
                n("b4", "GÖRSEL", 0.8f, 0.0f, 0.2f, 0.06f, DiagramBox.LABEL, "chevron"),
                n("src", "DIŞ", 0.005f, 0.09f, 0.19f, 0.66f, DiagramBox.GROUP, "boundary"),
                n("cl", "KUBERNETES KÜMESİ", 0.21f, 0.09f, 0.785f, 0.66f, DiagramBox.GROUP),
                n("s1", "[DIŞ]\nPostgreSQL", 0.015f, 0.16f, 0.17f, 0.13f, role = "external"),
                n("s2", "[DIŞ]\nSFTP", 0.015f, 0.36f, 0.17f, 0.13f, role = "external"),
                n("s3", "[DIŞ]\nWeb formları", 0.015f, 0.56f, 0.17f, 0.13f, role = "external"),
                n("orc", "Airflow\nzamanlayıcı", 0.23f, 0.15f, 0.745f, 0.08f, DiagramBox.CARD, "store"),
                n("n1", "NiFi\nalım", 0.23f, 0.34f, 0.15f, 0.16f),
                n("n2", "MinIO\nnesne deposu", 0.42f, 0.34f, 0.16f, 0.16f, role = "focal"),
                n("n3", "Trino\nSQL", 0.42f, 0.56f, 0.16f, 0.14f),
                n("n4", "Defterler\nPython", 0.62f, 0.34f, 0.16f, 0.16f),
                n("n5", "Superset\npanolar", 0.81f, 0.34f, 0.16f, 0.16f),
                n("x1", "Kimlik · Keycloak · LDAP · OIDC", 0.005f, 0.80f, 0.99f, 0.08f, DiagramBox.CARD, "security"),
                n("x2", "İzleme · Prometheus · Grafana", 0.005f, 0.90f, 0.99f, 0.08f, DiagramBox.CARD, "store"),
            ),
            listOf(
                e("s1", "n1", fromSide = R, toSide = L), e("s2", "n1", fromSide = R, toSide = L), e("s3", "n1", fromSide = R, toSide = L),
                e("n1", "n2", role = "accent", fromSide = R, toSide = L), e("n2", "n4", fromSide = R, toSide = L),
                e("n2", "n3", fromSide = B, toSide = T), e("n4", "n5", fromSide = R, toSide = L),
                e("n3", "n5", "SORGU", dashed = true, fromSide = R, toSide = B),
            ),
            manual = true,
        )
        DiagramKind.MEDALLION -> Template(
            listOf(
                n("t0", "[HAM]\nham-kova\nCSV · Parquet\nVeri mühendisi", 0.01f, 0.30f, 0.185f, 0.62f, DiagramBox.CARD, "outer"),
                n("t1", "[ANONİM]\nanonim-kova\nIceberg\nVeri mühendisi", 0.207f, 0.30f, 0.185f, 0.62f, DiagramBox.CARD),
                n("t2", "[HAZIRLIK]\nhazırlık-kova\ntemiz · ağırlıklı\nVeri bilimci", 0.404f, 0.30f, 0.185f, 0.62f, DiagramBox.CARD),
                n("t3", "[TOPLU]\ntoplu-kova\ngöstergeler\nVeri bilimci", 0.601f, 0.30f, 0.185f, 0.62f, DiagramBox.CARD, "focal"),
                n("t4", "[ARŞİV]\narşiv-kova\nsoğuk katman\nYönetici", 0.798f, 0.30f, 0.185f, 0.62f, DiagramBox.CARD, "cold"),
            ),
            listOf(
                e("t0", "t1", "PII SİL", role = "arc"), e("t1", "t2", "TEMİZLE", role = "arc"),
                e("t2", "t3", "TOPLA", role = "arc"), e("t3", "t4", "YAŞAM DÖNGÜSÜ", dashed = true, role = "arc"),
            ),
            manual = true,
        )
        DiagramKind.DATA_FLOW -> Template(
            listOf(
                n("l0", "YÖNETİCİ", 0f, 0.08f, 1f, 0.22f, DiagramBox.LANE),
                n("l1", "MÜHENDİS", 0f, 0.31f, 1f, 0.22f, DiagramBox.LANE),
                n("l2", "BİLİMCİ", 0f, 0.54f, 1f, 0.22f, DiagramBox.LANE),
                n("l3", "TÜKETİCİ", 0f, 0.77f, 1f, 0.22f, DiagramBox.LANE),
                n("h0", "1 · TOPLA", 0.17f, 0f, 0.15f, 0.06f, DiagramBox.LABEL, "step"),
                n("h1", "2 · DEPOLA", 0.34f, 0f, 0.15f, 0.06f, DiagramBox.LABEL, "step"),
                n("h2", "3 · DÖNÜŞTÜR", 0.51f, 0f, 0.15f, 0.06f, DiagramBox.LABEL, "step"),
                n("h3", "4 · ÇÖZÜMLE", 0.68f, 0f, 0.15f, 0.06f, DiagramBox.LABEL, "focal"),
                n("h4", "5 · YAYINLA", 0.85f, 0f, 0.15f, 0.06f, DiagramBox.LABEL, "step"),
                n("a", "[YÖN]\nProje kurulumu", 0.17f, 0.11f, 0.15f, 0.16f),
                n("b", "[YÖN]\nErişim denetimi", 0.34f, 0.11f, 0.15f, 0.16f, role = "security"),
                n("c", "[MÜH]\nKaynak alımı\nWB → DB", 0.175f, 0.34f, 0.14f, 0.16f),
                n("d", "[MÜH]\nHam depo\nDB → DB", 0.345f, 0.34f, 0.14f, 0.16f),
                n("f", "[MÜH]\nTemizle\nDB → TB", 0.515f, 0.34f, 0.14f, 0.16f),
                n("g", "[BİL]\nKeşfet ve modelle\nTB → FL", 0.68f, 0.57f, 0.15f, 0.16f, role = "focal"),
                n("h", "[BİL]\nBulguları yayınla", 0.85f, 0.57f, 0.15f, 0.16f),
                n("i", "[TÜK]\nSorgula", 0.85f, 0.80f, 0.15f, 0.16f),
            ),
            listOf(
                e("a", "c", dashed = true, fromSide = B, toSide = T), e("b", "d", dashed = true, fromSide = B, toSide = T),
                e("c", "d", fromSide = R, toSide = L), e("d", "f", fromSide = R, toSide = L),
                e("f", "g", "ANONİM VERİ", role = "accent", fromSide = R, toSide = T), e("g", "h", fromSide = R, toSide = L),
                e("h", "i", role = "link", fromSide = B, toSide = T),
            ),
            manual = true,
        )
        DiagramKind.DP_INTEGRATION -> Template(
            listOf(
                n("s1", "Veritabanları\nJDBC", 0.01f, 0.08f, 0.15f, 0.13f, role = "input"),
                n("s2", "SFTP\ndosya", 0.01f, 0.26f, 0.15f, 0.13f, role = "input"),
                n("s3", "E-posta\nIMAP", 0.01f, 0.44f, 0.15f, 0.13f, role = "input"),
                n("zone", "VERİ PLATFORMU", 0.21f, 0.04f, 0.58f, 0.66f, DiagramBox.GROUP),
                n("tr", "Trino\nbirleşik SQL", 0.23f, 0.10f, 0.54f, 0.11f, DiagramBox.CARD, "focal"),
                n("nf", "NiFi\nalım", 0.23f, 0.28f, 0.16f, 0.16f),
                n("mn", "MinIO\nmadalyon depo", 0.42f, 0.28f, 0.16f, 0.16f, role = "focal"),
                n("jp", "JupyterLab\ndefter", 0.61f, 0.28f, 0.16f, 0.16f),
                n("af", "Airflow\nDAG", 0.23f, 0.52f, 0.54f, 0.10f, DiagramBox.CARD, "store"),
                n("c1", "Masaüstü\nODBC", 0.84f, 0.08f, 0.15f, 0.13f),
                n("c2", "Raporlar\nJDBC", 0.84f, 0.26f, 0.15f, 0.13f),
                n("c3", "Web sitesi\nHTTPS", 0.84f, 0.44f, 0.15f, 0.13f),
                n("ad", "Active Directory · LDAP · SSO", 0.01f, 0.82f, 0.98f, 0.10f, DiagramBox.CARD, "security"),
            ),
            listOf(
                e("s1", "nf", fromSide = R, toSide = L), e("s2", "nf", fromSide = R, toSide = L), e("s3", "nf", fromSide = R, toSide = L),
                e("s1", "tr", dashed = true, role = "link", fromSide = R, toSide = L),
                e("nf", "mn", role = "accent", fromSide = R, toSide = L), e("mn", "jp", fromSide = R, toSide = L),
                e("tr", "c1", role = "accent", fromSide = R, toSide = L), e("tr", "c2", role = "accent", fromSide = R, toSide = L),
                e("tr", "c3", role = "accent", fromSide = R, toSide = L),
                e("ad", "zone", "AUTH", dashed = true, role = "accent", fromSide = T, toSide = B),
            ),
            manual = true,
        )
        DiagramKind.ARCH_DELTA -> Template(
            listOf(
                n("pb", "ÖNCE", 0.01f, 0.06f, 0.35f, 0.92f, DiagramBox.GROUP),
                n("pa", "SONRA", 0.64f, 0.06f, 0.35f, 0.92f, DiagramBox.GROUP),
                n("b1", "Mağaza", 0.06f, 0.14f, 0.25f, 0.14f),
                n("b2", "Sipariş API\ndoğrudan gönderim", 0.06f, 0.40f, 0.25f, 0.16f, role = "changed"),
                n("b3", "Gönderici", 0.06f, 0.70f, 0.25f, 0.14f, role = "removed"),
                n("a1", "Mağaza", 0.69f, 0.14f, 0.25f, 0.14f),
                n("a2", "Sipariş API\nkuyruğa yazar", 0.69f, 0.40f, 0.25f, 0.16f, role = "changed"),
                n("a3", "Sipariş kuyruğu", 0.69f, 0.70f, 0.25f, 0.14f, role = "added"),
                n("g1", "Δ Sipariş API\ndoğrudan → kuyruk", 0.39f, 0.26f, 0.22f, 0.12f, DiagramBox.LABEL, "ledger"),
                n("g2", "+ Sipariş kuyruğu\neklendi", 0.39f, 0.44f, 0.22f, 0.12f, DiagramBox.LABEL, "ledger"),
                n("g3", "− Gönderici\nkaldırıldı", 0.39f, 0.62f, 0.22f, 0.12f, DiagramBox.LABEL, "ledger"),
            ),
            listOf(
                e("b1", "b2", fromSide = B, toSide = T), e("b2", "b3", fromSide = B, toSide = T),
                e("a1", "a2", fromSide = B, toSide = T), e("a2", "a3", fromSide = B, toSide = T, role = "accent"),
            ),
            manual = true,
        )
        DiagramKind.BAR -> Template(
            listOf("Oca 120", "Şub 150", "Mar 132", "Nis 188", "May 164", "Haz 210").mapIndexed { i, t ->
                n("v$i", t, 0f, 0f, 0f, 0f, DiagramBox.BAR, if (i == 3) "focal" else null)
            },
            emptyList(), manual = false,
        )
        DiagramKind.WATERFALL -> Template(
            listOf("Başlangıç 240", "Kadro +64", "Yazılım +32", "Tasarruf -38", "Vergi -22", "Son =").mapIndexed { i, t ->
                n("v$i", t, 0f, 0f, 0f, 0f, DiagramBox.BAR, if (i == 1) "focal" else null)
            },
            emptyList(), manual = false,
        )
        DiagramKind.LINE -> Template(
            listOf("Oca 12", "Şub 18", "Mar 15", "Nis 24", "May 30", "Haz 27", "Tem 36", "Ağu 41").mapIndexed { i, t ->
                n("p$i", t, 0f, 0f, 0f, 0f, DiagramBox.DOT, if (i == 7) "focal" else null)
            },
            emptyList(),
            manual = false,
        )
        DiagramKind.SCATTER -> Template(
            listOf(
                "A 12 30", "B 20 42", "C 28 38", "D 35 55", "E 44 50", "F 52 66", "G 60 62", "H 70 80", "Kampanya 78 52", "J 86 92",
            ).mapIndexed { i, t -> n("p$i", t, 0f, 0f, 0f, 0f, DiagramBox.DOT, if (i == 8) "focal" else null) },
            emptyList(), manual = false,
        )
        DiagramKind.RADAR -> Template(
            listOf("Hız 9 6", "Maliyet 6 8", "Ölçek 8 5", "Güven 9 7", "Kolaylık 7 9").mapIndexed { i, t ->
                n("a$i", t, 0f, 0f, 0f, 0f, DiagramBox.LABEL)
            },
            emptyList(), manual = false,
        )
        DiagramKind.POLAR -> Template(
            listOf("00–03 32", "03–06 18", "06–09 24", "09–12 58", "12–15 100", "15–18 82", "18–21 76", "21–24 45").mapIndexed { i, t ->
                n("c$i", t, 0f, 0f, 0f, 0f, DiagramBox.LABEL, if (i == 4) "focal" else null)
            },
            emptyList(), manual = false,
        )
        DiagramKind.TREEMAP -> Template(
            listOf("Asya 59", "Afrika 18", "Avrupa 9", "Güney Amerika 6", "Kuzey Amerika 5", "Okyanusya 3").mapIndexed { i, t ->
                n("t$i", t, 0f, 0f, 0f, 0f, DiagramBox.RECT, if (i == 1) "focal" else null)
            },
            emptyList(), manual = false,
        )
        DiagramKind.HEATMAP -> {
            val cols = listOf("S1", "S2", "S3", "S4", "S5")
            val rows = listOf("giriş", "ödeme", "arama", "profil")
            val values = listOf(listOf(4, 6, 3, 8, 5), listOf(9, 14, 22, 47, 18), listOf(2, 3, 5, 6, 4), listOf(7, 5, 9, 12, 10))
            Template(
                cols.mapIndexed { i, c -> n("c$i", c, 0f, 0f, 0f, 0f, DiagramBox.LABEL, "col") } +
                    rows.mapIndexed { i, r -> n("r$i", r, 0f, 0f, 0f, 0f, DiagramBox.LABEL, "row") } +
                    values.flatMapIndexed { r, row -> row.mapIndexed { c, v -> n("v${r}_$c", "$v", 0f, 0f, 0f, 0f, DiagramBox.CELL, if (r == 1 && c == 3) "focal" else null) } },
                emptyList(), manual = false,
            )
        }
        DiagramKind.DP_SECURITY -> {
            val cols = listOf("Yöneticiler", "Mühendisler", "Bilimciler", "Tüketiciler")
            val rows = listOf("Keycloak", "MinIO · ham", "MinIO · toplu", "Trino · ham", "Trino · toplu", "JupyterHub")
            val values = listOf(
                listOf("Yönetici", "Giriş", "Giriş", "Giriş"), listOf("Tam", "Oku/Yaz", "Yok", "Yok"), listOf("Tam", "Oku/Yaz", "Okuma", "Yok"),
                listOf("Tam", "Oku/Yaz", "Yok", "Yok"), listOf("Tam", "Oku/Yaz", "SELECT", "Yalnız SELECT"), listOf("Yönetici", "Oku/Yaz", "Oku/Yaz", "Yok"),
            )
            Template(
                cols.mapIndexed { i, c -> n("c$i", c, 0f, 0f, 0f, 0f, DiagramBox.LABEL, "col") } +
                    rows.mapIndexed { i, r -> n("r$i", r, 0f, 0f, 0f, 0f, DiagramBox.LABEL, "row") } +
                    values.flatMapIndexed { r, row -> row.mapIndexed { c, v -> n("v${r}_$c", v, 0f, 0f, 0f, 0f, DiagramBox.CELL, if (r == 4 && c == 3) "focal" else null) } },
                emptyList(), manual = false,
            )
        }
        DiagramKind.SANKEY -> Template(
            listOf(
                n("a", "CI dakikası 12000", 0f, 0f, 0f, 0f, DiagramBox.BAR, "c0"),
                n("b", "Birim test 5200", 0f, 0f, 0f, 0f, DiagramBox.BAR, "c1"),
                n("c", "Uçtan uca 4000", 0f, 0f, 0f, 0f, DiagramBox.BAR, "c1"),
                n("d", "Derleme 2000", 0f, 0f, 0f, 0f, DiagramBox.BAR, "c1"),
                n("f", "Geçti 9400", 0f, 0f, 0f, 0f, DiagramBox.BAR, "c2"),
                n("g", "Kaldı 1600", 0f, 0f, 0f, 0f, DiagramBox.BAR, "c2"),
                n("h", "Kararsız 1000", 0f, 0f, 0f, 0f, DiagramBox.BAR, "c2"),
            ),
            listOf(
                e("a", "b", "5200", arrow = false), e("a", "c", "4000", arrow = false), e("a", "d", "2000", arrow = false),
                e("b", "f", "4800", arrow = false), e("b", "g", "400", arrow = false), e("c", "f", "2600", arrow = false),
                e("c", "g", "600", arrow = false), e("c", "h", "800", arrow = false, role = "accent"), e("d", "f", "2000", arrow = false),
            ),
            manual = false,
        )
        DiagramKind.GANTT -> Template(
            listOf("Araştırma 1-3", "Tasarım 2-5", "API 4-9", "Uygulama 5-10", "Test 8-11", "Yayın 11-12").mapIndexed { i, t ->
                n("g$i", t, 0f, 0f, 0f, 0f, DiagramBox.BAR, if (i == 2) "focal" else null)
            },
            emptyList(), manual = false,
        )
        DiagramKind.EXPLODED -> Template(
            listOf(
                n("p0", "Ekran\ncam · OLED", 0f, 0f, 0f, 0f, DiagramBox.SLAB),
                n("p1", "Ana kart\nişlemci · bellek", 0f, 0f, 0f, 0f, DiagramBox.SLAB, "focal"),
                n("p2", "Pil\n4500 mAh", 0f, 0f, 0f, 0f, DiagramBox.SLAB),
                n("p3", "Kasa\nalüminyum", 0f, 0f, 0f, 0f, DiagramBox.SLAB),
            ),
            emptyList(), manual = false,
        )
        DiagramKind.AXONOMETRIC -> Template(
            listOf(
                n("k1", "Depo\n2 kat", 0.06f, 0.40f, 0.30f, 0.30f, DiagramBox.SLAB),
                n("k2", "Ofis\n4 kat", 0.38f, 0.16f, 0.26f, 0.40f, DiagramBox.SLAB, "focal"),
                n("k3", "Otopark\n1 kat", 0.62f, 0.52f, 0.32f, 0.26f, DiagramBox.SLAB),
            ),
            listOf(e("k1", "k2", dashed = true, arrow = false)),
            manual = true,
        )
        else -> null
    }

    // ------------------------------------------------------------------------------------------ değerler

    /** Yazının adı ve sondaki sayıları ([values]); [signed] işaretli (artış/azalış), [computed] "=" (hesaplanan toplam). */
    class Item(val label: String, val values: List<Float>, val signed: Boolean, val computed: Boolean)

    private val numberToken = Regex("^([+\\-−]?)(\\d+(?:[.,]\\d+)?)(%?)$")
    private val rangeToken = Regex("^(\\d+(?:[.,]\\d+)?)[-–](\\d+(?:[.,]\\d+)?)$")

    fun parse(text: String): Item {
        val tokens = text.replace('\n', ' ').trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val values = ArrayList<Float>()
        var signed = false
        var computed = false
        var end = tokens.size
        while (end > 0) {
            val t = tokens[end - 1]
            if (t == "=") { computed = true; end--; continue }
            val r = rangeToken.matchEntire(t)
            if (r != null) {
                values.add(0, r.groupValues[2].replace(',', '.').toFloat()); values.add(0, r.groupValues[1].replace(',', '.').toFloat())
                end--; continue
            }
            val m = numberToken.matchEntire(t) ?: break
            val v = m.groupValues[2].replace(',', '.').toFloatOrNull() ?: break
            val neg = m.groupValues[1] == "-" || m.groupValues[1] == "−"
            if (m.groupValues[1].isNotEmpty()) signed = true
            values.add(0, if (neg) -v else v)
            end--
        }
        // Yalnız sayıdan oluşan yazıda (ısı haritası hücresi) ad boş
        val label = tokens.take(end).joinToString(" ").removePrefix("=").trim()
        return Item(label, values, signed, computed)
    }

    /**
     * Grafiklerde gösterilen ad: yazının sonundaki değerler atılır (serpilmede iki, radarda hepsi, diğerlerinde bir
     * değer ya da aralık; şelalede "=").
     */
    fun label(kind: DiagramKind, text: String): String {
        val tokens = text.replace('\n', ' ').trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.toMutableList()
        val strip = when (kind) { DiagramKind.SCATTER -> 2; DiagramKind.RADAR -> Int.MAX_VALUE; else -> 1 }
        var n = 0
        while (tokens.isNotEmpty() && n < strip) {
            val t = tokens.last()
            if (t == "=" || numberToken.matches(t) || rangeToken.matches(t)) { tokens.removeAt(tokens.size - 1); n++ } else break
        }
        if (kind == DiagramKind.WATERFALL && tokens.lastOrNull() == "=") tokens.removeAt(tokens.size - 1)
        return tokens.joinToString(" ")
    }

    /** Başlık satırı ("[AŞAMA 1]") dışındaki ad. */
    fun nameOf(text: String): String = text.lines().firstOrNull { !(it.startsWith("[") && it.endsWith("]")) } ?: text

    // ------------------------------------------------------------------------------------------ yerleşim

    /** v28 türlerinin yerleşimi: nicel türlerde değerlerden; yapı türlerinde yerleşmemiş yeni adımlar yerleştirilir. */
    fun layout(kind: DiagramKind, nodes: List<DiagramNode>): List<DiagramNode> {
        if (nodes.isEmpty()) return nodes
        return when (kind) {
            DiagramKind.BAR -> layoutBars(nodes)
            DiagramKind.WATERFALL -> layoutWaterfall(nodes)
            DiagramKind.LINE -> layoutLine(nodes)
            DiagramKind.SCATTER -> layoutScatter(nodes)
            DiagramKind.RADAR, DiagramKind.POLAR -> layoutRing(nodes, 0.44f, 0.22f, 0.07f)
            DiagramKind.TREEMAP -> layoutTreemap(nodes)
            DiagramKind.HEATMAP, DiagramKind.DP_SECURITY -> layoutGrid(nodes, kind == DiagramKind.DP_SECURITY)
            DiagramKind.SANKEY -> layoutSankey(nodes)
            DiagramKind.GANTT -> layoutGantt(nodes)
            DiagramKind.JOURNEY -> layoutJourney(nodes)
            DiagramKind.LAYERS -> nodes.mapIndexed { i, node ->
                val h = (0.92f / nodes.size).coerceAtMost(0.2f)
                val top = (1f - h * nodes.size) / 2f
                node.copy(x = 0.12f, y = top + i * h, w = 0.86f, h = h, box = DiagramBox.RECT)
            }
            DiagramKind.LOOP -> layoutLoop(nodes)
            DiagramKind.SEQUENCE -> nodes.mapIndexed { i, node ->
                val m = nodes.size
                val w = min(0.22f, 0.9f / m)
                val slot = 0.96f / m
                node.copy(x = 0.02f + i * slot + (slot - w) / 2f, y = 0.02f, w = w, h = 0.1f, box = DiagramBox.RECT)
            }
            DiagramKind.EXPLODED -> nodes.mapIndexed { i, node ->
                val m = nodes.size
                val gap = 0.9f / m
                node.copy(x = 0.04f, y = 0.04f + i * gap, w = 0.56f, h = min(0.26f, gap * 1.1f), box = DiagramBox.SLAB)
            }
            else -> placeUnplaced(nodes)
        }
    }

    /** Henüz yeri olmayan (yeni eklenen) adımlar önceki adımın yanına; diğerleri yerinde kalır. */
    private fun placeUnplaced(nodes: List<DiagramNode>): List<DiagramNode> {
        val out = ArrayList<DiagramNode>(nodes.size)
        nodes.forEach { node ->
            val unplaced = (node.x == 0.5f && node.y == 0.5f && node.w == 0.3f && node.h == 0.12f) || (node.w <= 0f || node.h <= 0f)
            if (!unplaced) { out += node; return@forEach }
            val ref = out.lastOrNull { it.box != DiagramBox.GROUP && it.box != DiagramBox.LANE && it.box != DiagramBox.COLUMN }
            val w = ref?.w?.coerceIn(0.12f, 0.3f) ?: 0.24f
            val h = ref?.h?.coerceIn(0.06f, 0.2f) ?: 0.12f
            val candidates = if (ref == null) listOf(0.38f to 0.44f) else listOf(
                ref.x to ref.y + ref.h + 0.04f, ref.x + ref.w + 0.04f to ref.y, ref.x - w - 0.04f to ref.y, ref.x to ref.y - h - 0.04f,
            )
            fun free(x: Float, y: Float) = x >= -0.2f && y >= -0.2f && out.none { o ->
                o.box != DiagramBox.GROUP && o.box != DiagramBox.LANE && o.box != DiagramBox.COLUMN &&
                    x < o.x + o.w && x + w > o.x && y < o.y + o.h && y + h > o.y
            }
            val (x, y) = candidates.firstOrNull { (cx, cy) -> free(cx, cy) } ?: candidates.first()
            out += node.copy(x = x, y = y, w = w, h = h, box = if (node.box == DiagramBox.GROUP || node.box == DiagramBox.LANE) DiagramBox.ROUNDED else node.box)
        }
        return out
    }

    /** Grafik alanı (eksen yazıları dışında). */
    const val PLOT_L = 0.1f
    const val PLOT_R = 0.98f
    const val PLOT_T = 0.06f
    const val PLOT_B = 0.86f

    private fun niceMax(v: Float): Float {
        if (v <= 0f) return 1f
        val exp = Math.pow(10.0, kotlin.math.floor(kotlin.math.log10(v.toDouble()))).toFloat()
        val f = v / exp
        val nice = when { f <= 1f -> 1f; f <= 2f -> 2f; f <= 2.5f -> 2.5f; f <= 5f -> 5f; else -> 10f }
        return nice * exp
    }

    /** Çubuk grafiğinde ölçeğin üst değeri (eksen yazıları için de). */
    fun barScale(nodes: List<DiagramNode>): Float = niceMax(nodes.maxOfOrNull { parse(it.text).values.lastOrNull() ?: 0f } ?: 1f)

    private fun layoutBars(nodes: List<DiagramNode>): List<DiagramNode> {
        val max = barScale(nodes)
        val m = nodes.size
        val pitch = (PLOT_R - PLOT_L) / m
        val bw = pitch * 0.64f
        return nodes.mapIndexed { i, node ->
            val v = (parse(node.text).values.lastOrNull() ?: 0f).coerceAtLeast(0f)
            val h = (PLOT_B - PLOT_T) * (v / max)
            node.copy(x = PLOT_L + i * pitch + (pitch - bw) / 2f, y = PLOT_B - h, w = bw, h = max(h, 0.004f), box = DiagramBox.BAR)
        }
    }

    /** Şelalede her çubuğun alt ve üst düzeyi (çizim de bunu kullanır: bağlantı çizgileri). */
    fun waterfallLevels(nodes: List<DiagramNode>): List<Pair<Float, Float>> {
        var running = 0f
        return nodes.map { node ->
            val it = parse(node.text)
            val v = it.values.lastOrNull()
            when {
                it.computed || v == null -> 0f to running
                it.signed -> { val before = running; running += v; before to running }
                else -> { running = v; 0f to v }
            }
        }
    }

    fun waterfallScale(nodes: List<DiagramNode>): Float = niceMax(waterfallLevels(nodes).maxOfOrNull { max(it.first, it.second) } ?: 1f)

    private fun layoutWaterfall(nodes: List<DiagramNode>): List<DiagramNode> {
        val levels = waterfallLevels(nodes)
        val max = waterfallScale(nodes)
        val m = nodes.size
        val pitch = (PLOT_R - PLOT_L) / m
        val bw = pitch * 0.66f
        fun y(v: Float) = PLOT_B - (PLOT_B - PLOT_T) * (v.coerceAtLeast(0f) / max)
        return nodes.mapIndexed { i, node ->
            val (a, b) = levels[i]
            val top = y(max(a, b)); val bottom = y(min(a, b))
            node.copy(x = PLOT_L + i * pitch + (pitch - bw) / 2f, y = top, w = bw, h = max(bottom - top, 0.004f), box = DiagramBox.BAR)
        }
    }

    fun lineScale(nodes: List<DiagramNode>): Float = niceMax(nodes.maxOfOrNull { parse(it.text).values.lastOrNull() ?: 0f } ?: 1f)

    private fun layoutLine(nodes: List<DiagramNode>): List<DiagramNode> {
        val max = lineScale(nodes)
        val m = nodes.size
        val d = 0.04f
        return nodes.mapIndexed { i, node ->
            val v = (parse(node.text).values.lastOrNull() ?: 0f).coerceAtLeast(0f)
            val x = if (m == 1) (PLOT_L + PLOT_R) / 2f else PLOT_L + 0.03f + (PLOT_R - PLOT_L - 0.06f) * i / (m - 1)
            val y = PLOT_B - (PLOT_B - PLOT_T) * (v / max)
            node.copy(x = x - d / 2f, y = y - d / 2f, w = d, h = d, box = DiagramBox.DOT)
        }
    }

    fun scatterScale(nodes: List<DiagramNode>): Pair<Float, Float> {
        val items = nodes.map { parse(it.text).values }
        return niceMax(items.maxOfOrNull { it.getOrElse(it.size - 2) { 0f } } ?: 1f) to niceMax(items.maxOfOrNull { it.lastOrNull() ?: 0f } ?: 1f)
    }

    private fun layoutScatter(nodes: List<DiagramNode>): List<DiagramNode> {
        val (mx, my) = scatterScale(nodes)
        val d = 0.035f
        return nodes.map { node ->
            val v = parse(node.text).values
            val xv = v.getOrElse(v.size - 2) { 0f }; val yv = v.lastOrNull() ?: 0f
            val x = PLOT_L + (PLOT_R - PLOT_L) * (xv / mx).coerceIn(0f, 1f)
            val y = PLOT_B - (PLOT_B - PLOT_T) * (yv / my).coerceIn(0f, 1f)
            node.copy(x = x - d / 2f, y = y - d / 2f, w = d, h = d, box = DiagramBox.DOT)
        }
    }

    /** Radar/kutupsal: ad kutuları çemberin dışında (saat 12'den başlayıp saat yönünde). */
    private fun layoutRing(nodes: List<DiagramNode>, r: Float, w: Float, h: Float): List<DiagramNode> {
        val m = nodes.size
        return nodes.mapIndexed { i, node ->
            val a = -Math.PI / 2 + 2 * Math.PI * i / m
            val cx = 0.5f + r * cos(a).toFloat(); val cy = 0.5f + r * sin(a).toFloat()
            node.copy(x = (cx - w / 2f).coerceIn(0f, 1f - w), y = (cy - h / 2f).coerceIn(0f, 1f - h), w = w, h = h, box = DiagramBox.LABEL)
        }
    }

    /** Ağaç haritası: karelere yakın (squarified, Bruls ve ark. 2000) yerleşim, büyükten küçüğe. */
    private fun layoutTreemap(nodes: List<DiagramNode>): List<DiagramNode> {
        val vals = nodes.map { (parse(it.text).values.lastOrNull() ?: 1f).coerceAtLeast(0.01f) }
        val total = vals.sum()
        val order = vals.indices.sortedByDescending { vals[it] }
        val rects = arrayOfNulls<FloatArray>(nodes.size)
        var x = 0.02f; var y = 0.02f; var w = 0.96f; var h = 0.96f
        val area = w * h
        val gap = 0.008f
        var i = 0
        while (i < order.size) {
            val short = min(w, h)
            val row = ArrayList<Int>()
            var best = Float.MAX_VALUE
            while (i < order.size) {
                val candidate = row + order[i]
                val sum = candidate.sumOf { (vals[it] / total * area).toDouble() }.toFloat()
                val side = sum / short
                val worst = candidate.maxOf { k -> val a = vals[k] / total * area; val l = a / side; max(l / side, side / l) }
                if (worst > best && row.isNotEmpty()) break
                best = worst
                row += order[i]; i++
            }
            val sum = row.sumOf { (vals[it] / total * area).toDouble() }.toFloat()
            val thick = sum / short
            var off = 0f
            row.forEach { k ->
                val l = vals[k] / total * area / thick
                rects[k] = if (w >= h) floatArrayOf(x, y + off, thick, l) else floatArrayOf(x + off, y, l, thick)
                off += l
            }
            if (w >= h) { x += thick; w -= thick } else { y += thick; h -= thick }
        }
        return nodes.mapIndexed { k, node ->
            val r = rects[k] ?: floatArrayOf(0f, 0f, 0.1f, 0.1f)
            node.copy(x = r[0] + gap / 2f, y = r[1] + gap / 2f, w = max(r[2] - gap, 0.005f), h = max(r[3] - gap, 0.005f), box = DiagramBox.RECT)
        }
    }

    /** Isı haritası / erişim matrisi: sütun başlıkları, satır adları ve hücreler (satır satır). */
    private fun layoutGrid(nodes: List<DiagramNode>, matrix: Boolean): List<DiagramNode> {
        val cols = nodes.filter { it.role == "col" }
        val rows = nodes.filter { it.role == "row" }
        val cellCount = nodes.count { it.role != "col" && it.role != "row" }
        val nc = max(1, cols.size); val nr = max(max(1, rows.size), (cellCount + nc - 1) / nc)
        val left = if (matrix) 0.24f else 0.16f
        val top = if (matrix) 0.12f else 0.08f
        val cw = (0.99f - left) / nc; val rh = (0.98f - top) / nr
        var cell = 0
        return nodes.map { node ->
            when (node.role) {
                "col" -> { val i = cols.indexOf(node); node.copy(x = left + i * cw + 0.004f, y = 0f, w = cw - 0.008f, h = top - 0.016f, box = DiagramBox.LABEL) }
                "row" -> { val i = rows.indexOf(node); node.copy(x = 0f, y = top + i * rh + 0.004f, w = left - 0.016f, h = rh - 0.008f, box = DiagramBox.LABEL) }
                else -> {
                    val k = cell++
                    val r = k / nc; val c = k % nc
                    node.copy(x = left + c * cw + 0.004f, y = top + r * rh + 0.004f, w = cw - 0.008f, h = rh - 0.008f, box = DiagramBox.CELL)
                }
            }
        }
    }

    /** Sankey: üç sütun ("c0".."c2"), düğüm çubuğu boyu değerle orantılı; aralıklar eşit. */
    private fun layoutSankey(nodes: List<DiagramNode>): List<DiagramNode> {
        val columns = (0..2).map { c -> nodes.filter { (it.role ?: "c0") == "c$c" } }
        val totals = columns.map { col -> col.sumOf { (parse(it.text).values.lastOrNull() ?: 0f).toDouble() }.toFloat() }
        val maxTotal = totals.maxOrNull()?.coerceAtLeast(1e-3f) ?: 1f
        val gap = 0.04f
        val span = 0.86f
        val xs = listOf(0.24f, 0.49f, 0.74f)
        val placed = HashMap<String, DiagramNode>()
        columns.forEachIndexed { c, col ->
            val usable = span - gap * max(0, col.size - 1)
            val k = usable / maxTotal
            val colH = totals[c] * k + gap * max(0, col.size - 1)
            var y = 0.07f + (span - colH) / 2f
            col.forEach { node ->
                val h = max((parse(node.text).values.lastOrNull() ?: 0f) * k, 0.006f)
                placed[node.id] = node.copy(x = xs[c] - 0.01f, y = y, w = 0.02f, h = h, box = DiagramBox.BAR)
                y += h + gap
            }
        }
        return nodes.map { placed[it.id] ?: it }
    }

    /** Gantt: hafta aralığı yazıdan ("Tasarım 2-5"); ad sütunu 0..0.22, zaman alanı 0.22..0.98. */
    fun ganttWeeks(nodes: List<DiagramNode>): Int = nodes.maxOfOrNull { (parse(it.text).values.lastOrNull() ?: 1f).toInt() }?.coerceIn(1, 52) ?: 12

    const val GANTT_L = 0.24f

    private fun layoutGantt(nodes: List<DiagramNode>): List<DiagramNode> {
        val weeks = ganttWeeks(nodes)
        val pitch = (0.98f - GANTT_L) / weeks
        val rowH = min(0.12f, 0.86f / max(1, nodes.size))
        return nodes.mapIndexed { i, node ->
            val v = parse(node.text).values
            val a = (v.getOrElse(v.size - 2) { 1f }).coerceIn(1f, weeks.toFloat())
            val b = (v.lastOrNull() ?: a).coerceIn(a, weeks.toFloat())
            node.copy(x = GANTT_L + (a - 1f) * pitch, y = 0.12f + i * rowH + rowH * 0.2f, w = max((b - a + 1f) * pitch, pitch * 0.5f), h = rowH * 0.6f, box = DiagramBox.BAR)
        }
    }

    /** Yolculuk: aşama başlıkları, duygu noktaları (1..5), eylemler ve sıkıntı etiketi (en düşük noktanın altında). */
    private fun layoutJourney(nodes: List<DiagramNode>): List<DiagramNode> {
        val stages = nodes.filter { it.role == "stage" }
        val moods = nodes.filter { it.role == "mood" }
        val actions = nodes.filter { it.role == "action" || it.role == null }
        val m = max(1, max(stages.size, max(moods.size, actions.size)))
        val left = 0.1f
        val colW = (0.99f - left) / m
        fun cx(i: Int) = left + colW * i + colW / 2f
        val trough = moods.indices.minByOrNull { parse(moods[it].text).values.lastOrNull() ?: 3f } ?: 0
        return nodes.map { node ->
            when (node.role) {
                "stage" -> { val i = stages.indexOf(node); node.copy(x = cx(i) - colW * 0.46f, y = 0f, w = colW * 0.92f, h = 0.12f, box = DiagramBox.LABEL) }
                "mood" -> {
                    val i = moods.indexOf(node)
                    val v = (parse(node.text).values.lastOrNull() ?: 3f).coerceIn(1f, 5f)
                    val y = 0.18f + (5f - v) / 4f * 0.34f
                    node.copy(x = cx(i) - 0.018f, y = y - 0.018f, w = 0.036f, h = 0.036f, box = DiagramBox.DOT, role = "mood")
                }
                "action", null -> { val i = actions.indexOf(node); node.copy(x = cx(i) - colW * 0.46f, y = 0.62f, w = colW * 0.92f, h = 0.12f, box = DiagramBox.LABEL) }
                "pain" -> node.copy(x = cx(trough) - colW * 0.46f, y = 0.80f, w = colW * 0.92f, h = 0.1f, box = DiagramBox.CARD)
                else -> node
            }
        }
    }

    /** Göbekli döngü: istasyonlar çemberde (saat 12'den saat yönünde), göbek ortada. */
    private fun layoutLoop(nodes: List<DiagramNode>): List<DiagramNode> {
        val stations = nodes.filter { it.role != "hub" }
        val m = max(1, stations.size)
        val w = 0.22f; val h = 0.13f
        return nodes.map { node ->
            if (node.role == "hub") node.copy(x = 0.36f, y = 0.42f, w = 0.28f, h = 0.16f)
            else {
                val i = stations.indexOf(node)
                val a = -Math.PI / 2 + 2 * Math.PI * i / m
                node.copy(x = 0.5f + 0.38f * cos(a).toFloat() - w / 2f, y = 0.5f + 0.38f * sin(a).toFloat() - h / 2f, w = w, h = h)
            }
        }
    }

    // ------------------------------------------------------------------------------------------ düzenleme yardımcıları

    /** Okları kendiliğinden eklenmeyen türler (değerler yazıdan; yeni adım zincire bağlanmaz). */
    val noEdgeKinds: Set<DiagramKind> = dataKinds - setOf(DiagramKind.SEQUENCE, DiagramKind.LOOP, DiagramKind.SANKEY) +
        setOf(DiagramKind.QUADRANT, DiagramKind.KANBAN, DiagramKind.STORY_MAP, DiagramKind.NESTED, DiagramKind.TIMELINE)

    /** Taraf seçilmemiş okların da dik açılı (dirsekli) çizildiği türler. */
    fun elbow(kind: DiagramKind): Boolean = when (kind) {
        DiagramKind.ORG, DiagramKind.TREE, DiagramKind.CONCEPT_MAP, DiagramKind.HUB, DiagramKind.ARCHITECTURE -> true
        else -> isNew(kind) && kind !in straightKinds && kind != DiagramKind.AXONOMETRIC
    }

    /** Yerleşimi belirleyen (yeni adıma aktarılan, odak yapılamayan) roller. */
    private val structuralRoles = setOf("stage", "mood", "action", "row", "col", "c0", "c1", "c2", "activity", "chevron", "step", "ledger", "axis", "hub")

    fun isStructural(role: String?): Boolean = role != null && role in structuralRoles

    /** Yeni adımın rolü: odaktaki adımın yerleşim rolü (yolculukta aşama, ısı haritasında satır…). */
    fun inheritRole(role: String?): String? = role?.takeIf { it in structuralRoles && it != "hub" && it != "axis" }

    /** Nicel türde yeni adımın yazısı: [near] adımının değerleriyle (grafikte hemen görünsün). */
    fun newNodeText(kind: DiagramKind, index: Int, near: String?): String {
        val base = "Adım $index"
        if (!isData(kind) || near == null) return base
        val it = parse(near)
        if (it.values.isEmpty()) return base
        fun f(v: Float) = if (v == kotlin.math.floor(v)) v.toInt().toString() else v.toString()
        return when (kind) {
            DiagramKind.GANTT -> {
                val a = it.values.getOrElse(it.values.size - 2) { 1f }; val b = it.values.last()
                val len = (b - a).coerceAtLeast(0f)
                "Yeni iş ${f(b + 1)}-${f(b + 1 + len)}"
            }
            DiagramKind.WATERFALL -> "Kalem +10"
            else -> (listOf("Yeni") + it.values.map { v -> f(v) }).joinToString(" ")
        }
    }

    /** Yeni diyagramın en-boy oranı (genişlik / yükseklik); eski türler için null (önceki varsayılan). */
    fun aspect(kind: DiagramKind): Float? = when (kind) {
        DiagramKind.KANBAN, DiagramKind.STORY_MAP, DiagramKind.HIGH_LEVEL, DiagramKind.DATA_FLOW, DiagramKind.MEDALLION,
        DiagramKind.JOURNEY, DiagramKind.SANKEY, DiagramKind.LINE, DiagramKind.GANTT -> 1.6f
        DiagramKind.DP_INTEGRATION, DiagramKind.ARCH_DELTA, DiagramKind.SWIMLANE, DiagramKind.IT_STATE, DiagramKind.DEPLOYMENT,
        DiagramKind.BAR, DiagramKind.WATERFALL, DiagramKind.HEATMAP, DiagramKind.TREEMAP -> 1.45f
        DiagramKind.ER, DiagramKind.DB_SCHEMA, DiagramKind.SCATTER, DiagramKind.WARDLEY, DiagramKind.DP_SECURITY,
        DiagramKind.LAYERS, DiagramKind.AXONOMETRIC -> 1.3f
        DiagramKind.UML_CLASS, DiagramKind.DEPENDENCY, DiagramKind.NESTED, DiagramKind.SEQUENCE, DiagramKind.EXPLODED -> 1.15f
        DiagramKind.RADAR, DiagramKind.POLAR, DiagramKind.LOOP, DiagramKind.QUADRANT -> 1f
        else -> null
    }

    /** Sankey şeridi: kaynakta [y0], hedefte [y1] başlayan [h] kalınlığında (diyagram birimi 0..1). */
    class Ribbon(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val h: Float)

    /**
     * Sankey şeritleri (ok sırasına göre): kalınlık okun yazısındaki değerle orantılı (kaynağın çubuk boyu / değeri);
     * kaynakta ve hedefte şeritler karşı ucun yüksekliğine göre üst üste dizilir.
     */
    fun ribbons(nodes: List<DiagramNode>, edges: List<DiagramEdge>): Map<Int, Ribbon> {
        val byId = nodes.associateBy { it.id }
        val unit = HashMap<String, Float>()
        nodes.forEach { n ->
            val v = parse(n.text).values.lastOrNull() ?: 0f
            if (v > 0f) unit[n.id] = n.h / v
        }
        val k = unit.values.minOrNull() ?: return emptyMap()
        val thick = edges.map { (parse(it.label).values.lastOrNull() ?: 0f).coerceAtLeast(0f) * k }
        val outOffset = HashMap<String, Float>()
        val inOffset = HashMap<String, Float>()
        val out = HashMap<Int, Ribbon>()
        // Kaynaktan çıkanlar hedefin yüksekliğine, hedefe girenler kaynağın yüksekliğine göre sıralanır (kesişme azalır)
        val order = edges.indices.sortedWith(compareBy({ byId[edges[it].from]?.y ?: 0f }, { byId[edges[it].to]?.y ?: 0f }))
        val y1 = HashMap<Int, Float>()
        edges.indices.sortedWith(compareBy({ byId[edges[it].to]?.y ?: 0f }, { byId[edges[it].from]?.y ?: 0f })).forEach { i ->
            val b = byId[edges[i].to] ?: return@forEach
            val off = inOffset[b.id] ?: 0f
            y1[i] = b.y + off
            inOffset[b.id] = off + thick[i]
        }
        order.forEach { i ->
            val a = byId[edges[i].from] ?: return@forEach
            val b = byId[edges[i].to] ?: return@forEach
            val off = outOffset[a.id] ?: 0f
            outOffset[a.id] = off + thick[i]
            out[i] = Ribbon(a.x + a.w, a.y + off, b.x, y1[i] ?: b.y, thick[i])
        }
        return out
    }
}
