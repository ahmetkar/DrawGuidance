import Foundation

/// v28 editoryal diyagram türlerinin hazır örnekleri ve nicel türlerin yerleşimi (Android `DiagramCatalog`).
/// Nicel türlerde değerler adımların yazısındaki sayılardır ("Ocak 120", "Kadro +64", "Toplam =", "Tasarım 1-3").
enum DiagramCatalog {
    struct Template {
        let nodes: [DiagramNode]
        let edges: [DiagramEdge]
        let manual: Bool
        init(_ nodes: [DiagramNode], _ edges: [DiagramEdge], manual: Bool) { self.nodes = nodes; self.edges = edges; self.manual = manual }
    }

    /// Değerleri yazıdan okunan türler.
    static let dataKinds: Set<DiagramKind> = [.BAR, .WATERFALL, .LINE, .SCATTER, .RADAR, .POLAR, .TREEMAP, .HEATMAP, .DP_SECURITY,
                                              .SANKEY, .GANTT, .JOURNEY, .LAYERS, .LOOP, .SEQUENCE, .EXPLODED]

    static func isData(_ kind: DiagramKind) -> Bool { dataKinds.contains(kind) }

    private static func ordinal(_ k: DiagramKind) -> Int { DiagramKind.allCases.firstIndex(of: k)! }

    /// v28 türlerinden elle yerleşimli olanlar.
    static func isManual(_ kind: DiagramKind) -> Bool { ordinal(kind) >= ordinal(.SEQUENCE) && !isData(kind) }

    /// v28 türü mü.
    static func isNew(_ kind: DiagramKind) -> Bool { ordinal(kind) >= ordinal(.SEQUENCE) }

    /// Oklar düz çizilen türler.
    static let straightKinds: Set<DiagramKind> = [.RELATION, .VENN, .MIND_MAP, .CYCLE, .LOOP, .WARDLEY, .LINE, .SCATTER, .JOURNEY,
                                                  .SEQUENCE, .SANKEY, .MEDALLION, .RADAR, .POLAR, .FISHBONE]

    private static func n(_ id: String, _ text: String, _ x: Float, _ y: Float, _ w: Float, _ h: Float,
                          _ box: DiagramBox = .ROUNDED, _ role: String? = nil) -> DiagramNode {
        DiagramNode(id: id, text: text, x: x, y: y, w: w, h: h, box: box, role: role)
    }

    private static func n(_ id: String, _ text: String, _ x: Float, _ y: Float, _ w: Float, _ h: Float,
                          _ box: DiagramBox = .ROUNDED, role: String?) -> DiagramNode {
        DiagramNode(id: id, text: text, x: x, y: y, w: w, h: h, box: box, role: role)
    }

    private static func e(_ from: String, _ to: String, label: String = "", dashed: Bool = false, arrow: Bool = true, role: String? = nil,
                          fromSide: DiagramSide = .AUTO, toSide: DiagramSide = .AUTO, fromLabel: String? = nil, toLabel: String? = nil) -> DiagramEdge {
        DiagramEdge(from: from, to: to, label: label, dashed: dashed, arrow: arrow, fromSide: fromSide, toSide: toSide, role: role,
                    fromLabel: fromLabel, toLabel: toLabel)
    }

    private static let R = DiagramSide.RIGHT
    private static let L = DiagramSide.LEFT
    private static let T = DiagramSide.TOP
    private static let B = DiagramSide.BOTTOM

    /// Türün hazır örneği (v28 türleri; eskiler için nil).
    static func template(_ kind: DiagramKind) -> Template? {
        switch kind {
        case .SEQUENCE: return Template(
            [
                n("a", "İstemci", 0, 0, 0, 0, .RECT), n("b", "API", 0, 0, 0, 0, .RECT, "focal"),
                n("c", "Önbellek", 0, 0, 0, 0, .RECT), n("d", "Veritabanı", 0, 0, 0, 0, .RECT, "store"),
            ],
            [
                e("a", "b", label: "GET /ürün"), e("b", "c", label: "oku"), e("c", "b", label: "ıska", dashed: true),
                e("b", "d", label: "SELECT"), e("d", "b", label: "satırlar", dashed: true), e("b", "c", label: "yaz", dashed: true, role: "async"),
                e("b", "a", label: "200 OK", role: "accent"),
            ],
            manual: false
        )
        case .ER: return Template(
            [
                n("cus", "Müşteri\n# id\nad\ne-posta", 0.02, 0.04, 0.26, 0.34, .TABLE),
                n("ord", "Sipariş\n# id\n→ müşteri_id\ntarih\ntoplam", 0.42, 0.04, 0.28, 0.40, .TABLE, "focal"),
                n("itm", "Sipariş kalemi\n# id\n→ sipariş_id\n→ ürün_id\nadet", 0.42, 0.58, 0.28, 0.40, .TABLE),
                n("prd", "Ürün\n# id\nad\nfiyat", 0.80, 0.60, 0.18, 0.34, .TABLE),
            ],
            [
                e("cus", "ord", label: "verir", arrow: false, fromSide: R, toSide: L, fromLabel: "1", toLabel: "N"),
                e("ord", "itm", label: "içerir", arrow: false, fromSide: B, toSide: T, fromLabel: "1", toLabel: "N"),
                e("prd", "itm", label: "", arrow: false, fromSide: L, toSide: R, fromLabel: "1", toLabel: "N"),
            ],
            manual: true
        )
        case .UML_CLASS: return Template(
            [
                n("ent", "Varlık\n--\n# id: UUID\n--\n+ eşit(o): Bool", 0.06, 0.04, 0.30, 0.30, .TABLE),
                n("rep", "«arayüz»\nDepo\n--\n--\n+ bul(id): T\n+ kaydet(t: T)", 0.62, 0.04, 0.32, 0.30, .TABLE),
                n("usr", "Kullanıcı\n--\n- ad: String\n- e-posta: String\n--\n+ giriş(): Bool", 0.06, 0.56, 0.30, 0.40, .TABLE, "focal"),
                n("urp", "KullanıcıDeposu\n--\n--\n+ bul(id): Kullanıcı\n+ sil(id)", 0.62, 0.56, 0.32, 0.36, .TABLE),
            ],
            [
                e("usr", "ent", role: "inherit", fromSide: T, toSide: B),
                e("urp", "rep", dashed: true, role: "realize", fromSide: T, toSide: B),
                e("urp", "usr", label: "kullanır", dashed: true, role: "depend", fromSide: L, toSide: R),
            ],
            manual: true
        )
        case .DB_SCHEMA: return Template(
            [
                n("users", "public.users\nid : uuid : PK\nemail : text : UQ\ncreated_at : timestamptz", 0.04, 0.06, 0.36, 0.34, .TABLE),
                n("orders", "public.orders\nid : uuid : PK\nuser_id : uuid : FK\ntotal : numeric(12,2)\nstatus : text : NN", 0.58, 0.06, 0.38, 0.40, .TABLE, "focal"),
                n("items", "public.order_items\nid : uuid : PK\norder_id : uuid : FK\nqty : int", 0.58, 0.60, 0.38, 0.34, .TABLE),
            ],
            [
                e("orders", "users", label: "RESTRICT", fromSide: L, toSide: R),
                e("items", "orders", label: "ON DELETE CASCADE", role: "accent", fromSide: T, toSide: B),
            ],
            manual: true
        )
        case .SWIMLANE: return Template(
            [
                n("l1", "MÜŞTERİ", 0, 0.02, 1, 0.31, .LANE),
                n("l2", "DESTEK", 0, 0.345, 1, 0.31, .LANE),
                n("l3", "MÜHENDİSLİK", 0, 0.67, 1, 0.31, .LANE),
                n("s1", "Talep açar", 0.20, 0.10, 0.18, 0.15),
                n("s2", "Sınıflandırır", 0.20, 0.425, 0.18, 0.15),
                n("s3", "Hatayı düzeltir", 0.44, 0.75, 0.18, 0.15, role: "focal"),
                n("s4", "Çözümü sunar", 0.66, 0.425, 0.18, 0.15),
                n("s5", "Onaylar", 0.80, 0.10, 0.16, 0.15),
            ],
            [
                e("s1", "s2", fromSide: B, toSide: T), e("s2", "s3", label: "HATA", role: "accent", fromSide: B, toSide: L),
                e("s3", "s4", fromSide: R, toSide: B), e("s4", "s5", fromSide: T, toSide: B),
            ],
            manual: true
        )
        case .KANBAN: return Template(
            [
                n("c1", "Bekleyen · 3", 0.01, 0.02, 0.235, 0.96, .COLUMN),
                n("c2", "Sürüyor · 4/3", 0.258, 0.02, 0.235, 0.96, .COLUMN),
                n("c3", "İnceleme · 2/3", 0.506, 0.02, 0.235, 0.96, .COLUMN),
                n("c4", "Bitti · 2", 0.754, 0.02, 0.235, 0.96, .COLUMN),
                n("k1", "Giriş sayfası\nWEB-12 · ayşe", 0.02, 0.13, 0.215, 0.13, .CARD),
                n("k2", "Arama filtresi\nWEB-15 · can", 0.02, 0.29, 0.215, 0.13, .CARD),
                n("k3", "Ödeme API\nPAY-4 · ece", 0.268, 0.13, 0.215, 0.13, .CARD, "blocked"),
                n("k4", "Önbellek\nAPI-9 · mert", 0.268, 0.29, 0.215, 0.13, .CARD),
                n("k5", "Bildirimler\nAPP-3 · dila", 0.268, 0.45, 0.215, 0.13, .CARD, "waiting"),
                n("k6", "Profil\nAPP-7 · ali", 0.516, 0.13, 0.215, 0.13, .CARD),
                n("k7", "Tema\nAPP-1 · su", 0.764, 0.13, 0.215, 0.13, .CARD, "done"),
            ],
            [],
            manual: true
        )
        case .STORY_MAP: return Template(
            [
                n("a1", "[ETKİNLİK 1]\nVeriyi bul", 0.16, 0.02, 0.19, 0.13, .CARD, "activity"),
                n("a2", "[ETKİNLİK 2]\nRapor kur", 0.37, 0.02, 0.19, 0.13, .CARD, "activity"),
                n("a3", "[ETKİNLİK 3]\nPaylaş", 0.58, 0.02, 0.19, 0.13, .CARD, "activity"),
                n("a4", "[ETKİNLİK 4]\nGüven", 0.79, 0.02, 0.19, 0.13, .CARD, "activity"),
                n("r1", "MVP", 0, 0.19, 1, 0.26, .LANE, "focal"),
                n("r2", "SÜRÜM 2", 0, 0.47, 1, 0.26, .LANE),
                n("r3", "SONRA", 0, 0.75, 1, 0.23, .LANE),
                n("u1", "Katalog arama\nRPT-101 · 3p", 0.16, 0.24, 0.19, 0.15, .CARD),
                n("u2", "Hazır şablon\nRPT-108 · 5p", 0.37, 0.24, 0.19, 0.15, .CARD),
                n("u3", "Bağlantı paylaş\nRPT-112 · 2p", 0.58, 0.24, 0.19, 0.15, .CARD),
                n("u4", "Satır izinleri\nRPT-114 · 8p", 0.79, 0.24, 0.19, 0.15, .CARD, "security"),
                n("u5", "Kayıtlı sorgular\nRPT-120 · 3p", 0.16, 0.52, 0.19, 0.15, .CARD),
                n("u6", "Grafik türleri\nRPT-131 · 5p", 0.37, 0.52, 0.19, 0.15, .CARD),
                n("u7", "Zamanlanmış e-posta\nRPT-140 · 5p", 0.58, 0.80, 0.19, 0.15, .CARD),
            ],
            [],
            manual: true
        )
        case .DEPLOYMENT: return Template(
            [
                n("z1", "KENAR", 0.02, 0.06, 0.24, 0.86, .GROUP, "boundary"),
                n("z2", "PROD / EU-WEST-1", 0.30, 0.06, 0.36, 0.86, .GROUP, "boundary"),
                n("z3", "VERİ", 0.70, 0.06, 0.28, 0.86, .GROUP, "boundary"),
                n("cdn", "[CDN]\nCloudFront\nstatik · v3", 0.04, 0.36, 0.20, 0.20),
                n("api", "[POD]\nAPI · x3\nv2.4.1", 0.34, 0.20, 0.28, 0.20),
                n("wrk", "[POD]\nÇalışan · x2\nv1.9.0", 0.34, 0.60, 0.28, 0.20),
                n("db", "[YÖNETİLEN]\nRDS birincil\npostgres 15.4", 0.73, 0.20, 0.22, 0.20, role: "focal"),
                n("db2", "[YÖNETİLEN]\nRDS yedek\npostgres 15.4", 0.73, 0.60, 0.22, 0.20, role: "store"),
            ],
            [
                e("cdn", "api", label: "HTTPS:443", role: "link", fromSide: R, toSide: L),
                e("api", "db", label: "TLS:5432", fromSide: R, toSide: L),
                e("wrk", "db", label: "", fromSide: R, toSide: L),
                e("api", "wrk", label: "KUYRUK", dashed: true, fromSide: B, toSide: T),
                e("db", "db2", label: "REPLİKA", dashed: true, role: "accent", fromSide: B, toSide: T),
            ],
            manual: true
        )
        case .NESTED: return Template(
            [
                n("o1", "KURULUŞ", 0.02, 0.02, 0.96, 0.96, .GROUP),
                n("o2", "PROJE", 0.10, 0.12, 0.80, 0.78, .GROUP),
                n("o3", "KLASÖR", 0.18, 0.22, 0.64, 0.60, .GROUP),
                n("o4", "DOSYA", 0.26, 0.32, 0.48, 0.42, .GROUP, "focal"),
            ],
            [],
            manual: true
        )
        case .LAYERS: return Template(
            [
                n("l1", "Sunum\nHTML · CSS", 0, 0, 0, 0, .RECT),
                n("l2", "Uygulama\nKotlin · Compose", 0, 0, 0, 0, .RECT),
                n("l3", "Etki alanı\niş kuralları", 0, 0, 0, 0, .RECT, "focal"),
                n("l4", "Veri\nRoom · SQLite", 0, 0, 0, 0, .RECT),
                n("l5", "Altyapı\nAndroid · JVM", 0, 0, 0, 0, .RECT),
            ],
            [],
            manual: false
        )
        case .LOOP: return Template(
            [
                n("s0", "Topla\nsinyaller", 0, 0, 0, 0), n("s1", "Araştır\nkanıt", 0, 0, 0, 0),
                n("s2", "Karar\ninsan onaylar", 0, 0, 0, 0, role: "focal"), n("s3", "Uygula\niş çıkar", 0, 0, 0, 0),
                n("s4", "Ölç\nsonuçlar", 0, 0, 0, 0), n("s5", "Öğren\nyöntem güncellenir", 0, 0, 0, 0),
                n("hub", "Ortak hafıza\ntek kayıt, her tur", 0, 0, 0, 0, role: "hub"),
            ],
            (0..<6).map { i in e("s\(i)", "s\((i + 1) % 6)") } +
                [e("s0", "hub", label: "SİNYAL", dashed: true, role: "spoke"), e("s3", "hub", label: "SONUÇ", dashed: true, role: "spoke")],
            manual: false
        )
        case .QUADRANT: return Template(
            [
                n("ay", "ETKİ", 0.40, 0.0, 0.20, 0.06, .LABEL, "axis"),
                n("ax", "ÇABA", 0.86, 0.47, 0.14, 0.06, .LABEL, "axis"),
                n("i1", "Önbellek", 0.14, 0.16, 0.26, 0.06, .DOT, "focal"),
                n("i2", "Yeniden yazım", 0.62, 0.20, 0.30, 0.06, .DOT),
                n("i3", "Uçtan uca test", 0.22, 0.34, 0.26, 0.06, .DOT),
                n("i4", "Koyu tema", 0.58, 0.66, 0.26, 0.06, .DOT),
                n("i5", "Belgeler", 0.12, 0.76, 0.26, 0.06, .DOT),
                n("i6", "Yeni marka", 0.70, 0.82, 0.26, 0.06, .DOT),
            ],
            [],
            manual: true
        )
        case .WARDLEY: return Template(
            [
                n("w1", "Kullanıcı", 0.58, 0.02, 0.20, 0.10, .DOT),
                n("w2", "Asistan", 0.50, 0.20, 0.20, 0.10, .DOT),
                n("w3", "Ajan düzeni", 0.18, 0.40, 0.22, 0.10, .DOT, "moving"),
                n("w4", "Bilgi tabanı", 0.36, 0.56, 0.22, 0.10, .DOT),
                n("w5", "Model API", 0.64, 0.54, 0.20, 0.10, .DOT),
                n("w6", "Bulut", 0.82, 0.74, 0.16, 0.10, .DOT),
            ],
            [
                e("w1", "w2", arrow: false), e("w2", "w3", arrow: false), e("w2", "w5", arrow: false),
                e("w3", "w4", arrow: false), e("w5", "w6", arrow: false), e("w4", "w6", arrow: false),
            ],
            manual: true
        )
        case .JOURNEY: return Template(
            ["Kayıt", "İlk proje", "Davet", "Limite takıldı", "Ödeme"].enumerated().map { i, t in n("h\(i)", "[AŞAMA \(i + 1)]\n\(t)", 0, 0, 0, 0, .LABEL, "stage") } +
                [4, 3, 4, 1, 3].enumerated().map { i, v in n("m\(i)", "\(v)", 0, 0, 0, 0, .DOT, "mood") } +
                ["Formu doldurur", "Şablon seçer", "Ekip çağırır", "Uyarıyla durur", "Planı yükseltir"]
                    .enumerated().map { i, t in n("x\(i)", t, 0, 0, 0, 0, .LABEL, "action") } +
                [n("p0", "Sınır belirsiz", 0, 0, 0, 0, .CARD, "pain")],
            [],
            manual: false
        )
        case .DEPENDENCY: return Template(
            [
                n("app", "app\nuygulama", 0.38, 0.02, 0.24, 0.13),
                n("api", "api", 0.14, 0.30, 0.24, 0.13),
                n("ui", "ui", 0.62, 0.30, 0.24, 0.13),
                n("auth", "auth", 0.02, 0.58, 0.24, 0.13),
                n("db", "db-istemci", 0.38, 0.58, 0.24, 0.13, role: "focal"),
                n("log", "günlük", 0.74, 0.58, 0.24, 0.13, role: "store"),
                n("pg", "pg\nv3.23 · npm", 0.38, 0.84, 0.24, 0.13, role: "external"),
                n("cr", "crypto\nv1.4 · npm", 0.02, 0.84, 0.24, 0.13, role: "external"),
            ],
            [
                e("app", "api", fromSide: B, toSide: T), e("app", "ui", fromSide: B, toSide: T),
                e("api", "auth", fromSide: B, toSide: T), e("api", "db", fromSide: B, toSide: T), e("ui", "db", fromSide: B, toSide: T),
                e("ui", "log", fromSide: B, toSide: T), e("auth", "db", fromSide: R, toSide: L), e("db", "pg", fromSide: B, toSide: T),
                e("auth", "cr", fromSide: B, toSide: T), e("db", "auth", label: "DÖNGÜ", dashed: true, role: "cycle", fromSide: B, toSide: B),
            ],
            manual: true
        )
        case .IT_STATE: return Template(
            [
                n("z1", "TOPLAMA", 0.01, 0.08, 0.30, 0.90, .GROUP),
                n("z2", "İŞLEME", 0.35, 0.08, 0.30, 0.90, .GROUP),
                n("z3", "YAYIN", 0.69, 0.08, 0.30, 0.90, .GROUP),
                n("c1", "Anket sistemi\nCAPI · PostgreSQL", 0.03, 0.18, 0.26, 0.17),
                n("c2", "Eski uygulamalar\nyönetim portalları", 0.03, 0.44, 0.26, 0.17),
                n("c3", "Nüfus kaydı\ndış kaynak", 0.03, 0.70, 0.26, 0.17, role: "external"),
                n("c4", "Ortak sürücü\nsürüm denetimi yok", 0.37, 0.18, 0.26, 0.19, role: "focal"),
                n("c5", "Analist makineleri\nSPSS · Excel", 0.37, 0.46, 0.26, 0.17),
                n("c6", "SQL Server\nşirket içi", 0.37, 0.72, 0.26, 0.17, role: "store"),
                n("c7", "Eski portal\nelle darboğaz", 0.71, 0.18, 0.26, 0.19, role: "focal"),
                n("c8", "Web sitesi\nstatik sayfalar", 0.71, 0.46, 0.26, 0.17),
                n("c9", "Bakanlıklar\n~6 kurum", 0.71, 0.72, 0.26, 0.17, role: "external"),
            ],
            [
                e("c1", "c4", label: "CSV", fromSide: R, toSide: L), e("c2", "c4", label: "E-POSTA", fromSide: R, toSide: L),
                e("c3", "c5", label: "EXCEL", dashed: true, fromSide: R, toSide: L), e("c4", "c5", label: "KOPYALA", dashed: true, role: "accent", fromSide: B, toSide: T),
                e("c5", "c6", label: "YÜKLE", fromSide: B, toSide: T), e("c4", "c7", label: "EXCEL", role: "accent", fromSide: R, toSide: L),
                e("c7", "c8", label: "WEB", fromSide: B, toSide: T), e("c8", "c9", label: "CSV", dashed: true, role: "link", fromSide: B, toSide: T),
            ],
            manual: true
        )
        case .HIGH_LEVEL: return Template(
            [
                n("b0", "KAYNAKLAR", 0.0, 0.0, 0.2, 0.06, .LABEL, "chevron"),
                n("b1", "ALIM", 0.2, 0.0, 0.2, 0.06, .LABEL, "chevron"),
                n("b2", "DEPOLAMA", 0.4, 0.0, 0.2, 0.06, .LABEL, "chevron"),
                n("b3", "DÖNÜŞÜM", 0.6, 0.0, 0.2, 0.06, .LABEL, "chevron"),
                n("b4", "GÖRSEL", 0.8, 0.0, 0.2, 0.06, .LABEL, "chevron"),
                n("src", "DIŞ", 0.005, 0.09, 0.19, 0.66, .GROUP, "boundary"),
                n("cl", "KUBERNETES KÜMESİ", 0.21, 0.09, 0.785, 0.66, .GROUP),
                n("s1", "[DIŞ]\nPostgreSQL", 0.015, 0.16, 0.17, 0.13, role: "external"),
                n("s2", "[DIŞ]\nSFTP", 0.015, 0.36, 0.17, 0.13, role: "external"),
                n("s3", "[DIŞ]\nWeb formları", 0.015, 0.56, 0.17, 0.13, role: "external"),
                n("orc", "Airflow\nzamanlayıcı", 0.23, 0.15, 0.745, 0.08, .CARD, "store"),
                n("n1", "NiFi\nalım", 0.23, 0.34, 0.15, 0.16),
                n("n2", "MinIO\nnesne deposu", 0.42, 0.34, 0.16, 0.16, role: "focal"),
                n("n3", "Trino\nSQL", 0.42, 0.56, 0.16, 0.14),
                n("n4", "Defterler\nPython", 0.62, 0.34, 0.16, 0.16),
                n("n5", "Superset\npanolar", 0.81, 0.34, 0.16, 0.16),
                n("x1", "Kimlik · Keycloak · LDAP · OIDC", 0.005, 0.80, 0.99, 0.08, .CARD, "security"),
                n("x2", "İzleme · Prometheus · Grafana", 0.005, 0.90, 0.99, 0.08, .CARD, "store"),
            ],
            [
                e("s1", "n1", fromSide: R, toSide: L), e("s2", "n1", fromSide: R, toSide: L), e("s3", "n1", fromSide: R, toSide: L),
                e("n1", "n2", role: "accent", fromSide: R, toSide: L), e("n2", "n4", fromSide: R, toSide: L),
                e("n2", "n3", fromSide: B, toSide: T), e("n4", "n5", fromSide: R, toSide: L),
                e("n3", "n5", label: "SORGU", dashed: true, fromSide: R, toSide: B),
            ],
            manual: true
        )
        case .MEDALLION: return Template(
            [
                n("t0", "[HAM]\nham-kova\nCSV · Parquet\nVeri mühendisi", 0.01, 0.30, 0.185, 0.62, .CARD, "outer"),
                n("t1", "[ANONİM]\nanonim-kova\nIceberg\nVeri mühendisi", 0.207, 0.30, 0.185, 0.62, .CARD),
                n("t2", "[HAZIRLIK]\nhazırlık-kova\ntemiz · ağırlıklı\nVeri bilimci", 0.404, 0.30, 0.185, 0.62, .CARD),
                n("t3", "[TOPLU]\ntoplu-kova\ngöstergeler\nVeri bilimci", 0.601, 0.30, 0.185, 0.62, .CARD, "focal"),
                n("t4", "[ARŞİV]\narşiv-kova\nsoğuk katman\nYönetici", 0.798, 0.30, 0.185, 0.62, .CARD, "cold"),
            ],
            [
                e("t0", "t1", label: "PII SİL", role: "arc"), e("t1", "t2", label: "TEMİZLE", role: "arc"),
                e("t2", "t3", label: "TOPLA", role: "arc"), e("t3", "t4", label: "YAŞAM DÖNGÜSÜ", dashed: true, role: "arc"),
            ],
            manual: true
        )
        case .DATA_FLOW: return Template(
            [
                n("l0", "YÖNETİCİ", 0, 0.08, 1, 0.22, .LANE),
                n("l1", "MÜHENDİS", 0, 0.31, 1, 0.22, .LANE),
                n("l2", "BİLİMCİ", 0, 0.54, 1, 0.22, .LANE),
                n("l3", "TÜKETİCİ", 0, 0.77, 1, 0.22, .LANE),
                n("h0", "1 · TOPLA", 0.17, 0, 0.15, 0.06, .LABEL, "step"),
                n("h1", "2 · DEPOLA", 0.34, 0, 0.15, 0.06, .LABEL, "step"),
                n("h2", "3 · DÖNÜŞTÜR", 0.51, 0, 0.15, 0.06, .LABEL, "step"),
                n("h3", "4 · ÇÖZÜMLE", 0.68, 0, 0.15, 0.06, .LABEL, "focal"),
                n("h4", "5 · YAYINLA", 0.85, 0, 0.15, 0.06, .LABEL, "step"),
                n("a", "[YÖN]\nProje kurulumu", 0.17, 0.11, 0.15, 0.16),
                n("b", "[YÖN]\nErişim denetimi", 0.34, 0.11, 0.15, 0.16, role: "security"),
                n("c", "[MÜH]\nKaynak alımı\nWB → DB", 0.175, 0.34, 0.14, 0.16),
                n("d", "[MÜH]\nHam depo\nDB → DB", 0.345, 0.34, 0.14, 0.16),
                n("f", "[MÜH]\nTemizle\nDB → TB", 0.515, 0.34, 0.14, 0.16),
                n("g", "[BİL]\nKeşfet ve modelle\nTB → FL", 0.68, 0.57, 0.15, 0.16, role: "focal"),
                n("h", "[BİL]\nBulguları yayınla", 0.85, 0.57, 0.15, 0.16),
                n("i", "[TÜK]\nSorgula", 0.85, 0.80, 0.15, 0.16),
            ],
            [
                e("a", "c", dashed: true, fromSide: B, toSide: T), e("b", "d", dashed: true, fromSide: B, toSide: T),
                e("c", "d", fromSide: R, toSide: L), e("d", "f", fromSide: R, toSide: L),
                e("f", "g", label: "ANONİM VERİ", role: "accent", fromSide: R, toSide: T), e("g", "h", fromSide: R, toSide: L),
                e("h", "i", role: "link", fromSide: B, toSide: T),
            ],
            manual: true
        )
        case .DP_INTEGRATION: return Template(
            [
                n("s1", "Veritabanları\nJDBC", 0.01, 0.08, 0.15, 0.13, role: "input"),
                n("s2", "SFTP\ndosya", 0.01, 0.26, 0.15, 0.13, role: "input"),
                n("s3", "E-posta\nIMAP", 0.01, 0.44, 0.15, 0.13, role: "input"),
                n("zone", "VERİ PLATFORMU", 0.21, 0.04, 0.58, 0.66, .GROUP),
                n("tr", "Trino\nbirleşik SQL", 0.23, 0.10, 0.54, 0.11, .CARD, "focal"),
                n("nf", "NiFi\nalım", 0.23, 0.28, 0.16, 0.16),
                n("mn", "MinIO\nmadalyon depo", 0.42, 0.28, 0.16, 0.16, role: "focal"),
                n("jp", "JupyterLab\ndefter", 0.61, 0.28, 0.16, 0.16),
                n("af", "Airflow\nDAG", 0.23, 0.52, 0.54, 0.10, .CARD, "store"),
                n("c1", "Masaüstü\nODBC", 0.84, 0.08, 0.15, 0.13),
                n("c2", "Raporlar\nJDBC", 0.84, 0.26, 0.15, 0.13),
                n("c3", "Web sitesi\nHTTPS", 0.84, 0.44, 0.15, 0.13),
                n("ad", "Active Directory · LDAP · SSO", 0.01, 0.82, 0.98, 0.10, .CARD, "security"),
            ],
            [
                e("s1", "nf", fromSide: R, toSide: L), e("s2", "nf", fromSide: R, toSide: L), e("s3", "nf", fromSide: R, toSide: L),
                e("s1", "tr", dashed: true, role: "link", fromSide: R, toSide: L),
                e("nf", "mn", role: "accent", fromSide: R, toSide: L), e("mn", "jp", fromSide: R, toSide: L),
                e("tr", "c1", role: "accent", fromSide: R, toSide: L), e("tr", "c2", role: "accent", fromSide: R, toSide: L),
                e("tr", "c3", role: "accent", fromSide: R, toSide: L),
                e("ad", "zone", label: "AUTH", dashed: true, role: "accent", fromSide: T, toSide: B),
            ],
            manual: true
        )
        case .ARCH_DELTA: return Template(
            [
                n("pb", "ÖNCE", 0.01, 0.06, 0.35, 0.92, .GROUP),
                n("pa", "SONRA", 0.64, 0.06, 0.35, 0.92, .GROUP),
                n("b1", "Mağaza", 0.06, 0.14, 0.25, 0.14),
                n("b2", "Sipariş API\ndoğrudan gönderim", 0.06, 0.40, 0.25, 0.16, role: "changed"),
                n("b3", "Gönderici", 0.06, 0.70, 0.25, 0.14, role: "removed"),
                n("a1", "Mağaza", 0.69, 0.14, 0.25, 0.14),
                n("a2", "Sipariş API\nkuyruğa yazar", 0.69, 0.40, 0.25, 0.16, role: "changed"),
                n("a3", "Sipariş kuyruğu", 0.69, 0.70, 0.25, 0.14, role: "added"),
                n("g1", "Δ Sipariş API\ndoğrudan → kuyruk", 0.39, 0.26, 0.22, 0.12, .LABEL, "ledger"),
                n("g2", "+ Sipariş kuyruğu\neklendi", 0.39, 0.44, 0.22, 0.12, .LABEL, "ledger"),
                n("g3", "− Gönderici\nkaldırıldı", 0.39, 0.62, 0.22, 0.12, .LABEL, "ledger"),
            ],
            [
                e("b1", "b2", fromSide: B, toSide: T), e("b2", "b3", fromSide: B, toSide: T),
                e("a1", "a2", fromSide: B, toSide: T), e("a2", "a3", role: "accent", fromSide: B, toSide: T),
            ],
            manual: true
        )
        case .BAR: return Template(
            ["Oca 120", "Şub 150", "Mar 132", "Nis 188", "May 164", "Haz 210"].enumerated().map { i, t in
                n("v\(i)", t, 0, 0, 0, 0, .BAR, (i == 3) ? "focal" : nil)
            },
            [], manual: false
        )
        case .WATERFALL: return Template(
            ["Başlangıç 240", "Kadro +64", "Yazılım +32", "Tasarruf -38", "Vergi -22", "Son ="].enumerated().map { i, t in
                n("v\(i)", t, 0, 0, 0, 0, .BAR, (i == 1) ? "focal" : nil)
            },
            [], manual: false
        )
        case .LINE: return Template(
            ["Oca 12", "Şub 18", "Mar 15", "Nis 24", "May 30", "Haz 27", "Tem 36", "Ağu 41"].enumerated().map { i, t in
                n("p\(i)", t, 0, 0, 0, 0, .DOT, (i == 7) ? "focal" : nil)
            },
            [],
            manual: false
        )
        case .SCATTER: return Template(
            [
                "A 12 30", "B 20 42", "C 28 38", "D 35 55", "E 44 50", "F 52 66", "G 60 62", "H 70 80", "Kampanya 78 52", "J 86 92",
            ].enumerated().map { i, t in n("p\(i)", t, 0, 0, 0, 0, .DOT, (i == 8) ? "focal" : nil) },
            [], manual: false
        )
        case .RADAR: return Template(
            ["Hız 9 6", "Maliyet 6 8", "Ölçek 8 5", "Güven 9 7", "Kolaylık 7 9"].enumerated().map { i, t in
                n("a\(i)", t, 0, 0, 0, 0, .LABEL)
            },
            [], manual: false
        )
        case .POLAR: return Template(
            ["00–03 32", "03–06 18", "06–09 24", "09–12 58", "12–15 100", "15–18 82", "18–21 76", "21–24 45"].enumerated().map { i, t in
                n("c\(i)", t, 0, 0, 0, 0, .LABEL, (i == 4) ? "focal" : nil)
            },
            [], manual: false
        )
        case .TREEMAP: return Template(
            ["Asya 59", "Afrika 18", "Avrupa 9", "Güney Amerika 6", "Kuzey Amerika 5", "Okyanusya 3"].enumerated().map { i, t in
                n("t\(i)", t, 0, 0, 0, 0, .RECT, (i == 1) ? "focal" : nil)
            },
            [], manual: false
        )
        case .HEATMAP:
            let cols = ["S1", "S2", "S3", "S4", "S5"]
            let rows = ["giriş", "ödeme", "arama", "profil"]
            let values = [[4, 6, 3, 8, 5], [9, 14, 22, 47, 18], [2, 3, 5, 6, 4], [7, 5, 9, 12, 10]]
            return Template(
                cols.enumerated().map { i, c in n("c\(i)", c, 0, 0, 0, 0, .LABEL, "col") } +
                    rows.enumerated().map { i, r in n("r\(i)", r, 0, 0, 0, 0, .LABEL, "row") } +
                    values.enumerated().flatMap { r, row in row.enumerated().map { c, v in n("v\(r)_\(c)", "\(v)", 0, 0, 0, 0, .CELL, (r == 1 && c == 3) ? "focal" : nil) } },
                [], manual: false
            )
        case .DP_SECURITY:
            let cols = ["Yöneticiler", "Mühendisler", "Bilimciler", "Tüketiciler"]
            let rows = ["Keycloak", "MinIO · ham", "MinIO · toplu", "Trino · ham", "Trino · toplu", "JupyterHub"]
            let values = [
                ["Yönetici", "Giriş", "Giriş", "Giriş"], ["Tam", "Oku/Yaz", "Yok", "Yok"], ["Tam", "Oku/Yaz", "Okuma", "Yok"],
                ["Tam", "Oku/Yaz", "Yok", "Yok"], ["Tam", "Oku/Yaz", "SELECT", "Yalnız SELECT"], ["Yönetici", "Oku/Yaz", "Oku/Yaz", "Yok"],
            ]
            return Template(
                cols.enumerated().map { i, c in n("c\(i)", c, 0, 0, 0, 0, .LABEL, "col") } +
                    rows.enumerated().map { i, r in n("r\(i)", r, 0, 0, 0, 0, .LABEL, "row") } +
                    values.enumerated().flatMap { r, row in row.enumerated().map { c, v in n("v\(r)_\(c)", v, 0, 0, 0, 0, .CELL, (r == 4 && c == 3) ? "focal" : nil) } },
                [], manual: false
            )
        case .SANKEY: return Template(
            [
                n("a", "CI dakikası 12000", 0, 0, 0, 0, .BAR, "c0"),
                n("b", "Birim test 5200", 0, 0, 0, 0, .BAR, "c1"),
                n("c", "Uçtan uca 4000", 0, 0, 0, 0, .BAR, "c1"),
                n("d", "Derleme 2000", 0, 0, 0, 0, .BAR, "c1"),
                n("f", "Geçti 9400", 0, 0, 0, 0, .BAR, "c2"),
                n("g", "Kaldı 1600", 0, 0, 0, 0, .BAR, "c2"),
                n("h", "Kararsız 1000", 0, 0, 0, 0, .BAR, "c2"),
            ],
            [
                e("a", "b", label: "5200", arrow: false), e("a", "c", label: "4000", arrow: false), e("a", "d", label: "2000", arrow: false),
                e("b", "f", label: "4800", arrow: false), e("b", "g", label: "400", arrow: false), e("c", "f", label: "2600", arrow: false),
                e("c", "g", label: "600", arrow: false), e("c", "h", label: "800", arrow: false, role: "accent"), e("d", "f", label: "2000", arrow: false),
            ],
            manual: false
        )
        case .GANTT: return Template(
            ["Araştırma 1-3", "Tasarım 2-5", "API 4-9", "Uygulama 5-10", "Test 8-11", "Yayın 11-12"].enumerated().map { i, t in
                n("g\(i)", t, 0, 0, 0, 0, .BAR, (i == 2) ? "focal" : nil)
            },
            [], manual: false
        )
        case .EXPLODED: return Template(
            [
                n("p0", "Ekran\ncam · OLED", 0, 0, 0, 0, .SLAB),
                n("p1", "Ana kart\nişlemci · bellek", 0, 0, 0, 0, .SLAB, "focal"),
                n("p2", "Pil\n4500 mAh", 0, 0, 0, 0, .SLAB),
                n("p3", "Kasa\nalüminyum", 0, 0, 0, 0, .SLAB),
            ],
            [], manual: false
        )
        case .AXONOMETRIC: return Template(
            [
                n("k1", "Depo\n2 kat", 0.06, 0.40, 0.30, 0.30, .SLAB),
                n("k2", "Ofis\n4 kat", 0.38, 0.16, 0.26, 0.40, .SLAB, "focal"),
                n("k3", "Otopark\n1 kat", 0.62, 0.52, 0.32, 0.26, .SLAB),
            ],
            [e("k1", "k2", dashed: true, arrow: false)],
            manual: true
        )        default: return nil
        }
    }
}
