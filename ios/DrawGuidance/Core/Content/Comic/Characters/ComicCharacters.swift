import Foundation

/// v36 karakter türleri (15). Adlar ve etiketler aramada kullanılır. v37: hepsi anime / çizgi roman stilinde yeniden çizildi.
enum CharKind: String, CaseIterable {
    case KID, STUDENT, OFFICE, ELDER, TEACHER, DOCTOR, POLICE, DETECTIVE, CHEF, ATHLETE, HERO, ASTRONAUT, ROBOT, KNIGHT, WIZARD

    var id: String { rawValue.lowercased() }
    var title: String {
        switch self {
        case .KID: return "Çocuk"
        case .STUDENT: return "Öğrenci"
        case .OFFICE: return "Ofis çalışanı"
        case .ELDER: return "Yaşlı"
        case .TEACHER: return "Öğretmen"
        case .DOCTOR: return "Doktor"
        case .POLICE: return "Polis"
        case .DETECTIVE: return "Dedektif"
        case .CHEF: return "Aşçı"
        case .ATHLETE: return "Sporcu"
        case .HERO: return "Süper kahraman"
        case .ASTRONAUT: return "Astronot"
        case .ROBOT: return "Robot"
        case .KNIGHT: return "Şövalye"
        case .WIZARD: return "Büyücü"
        }
    }
    var tags: [String] {
        switch self {
        case .KID: return ["çocuk", "küçük", "oğlan"]
        case .STUDENT: return ["öğrenci", "okul", "genç", "kız"]
        case .OFFICE: return ["ofis", "iş", "takım elbise", "memur"]
        case .ELDER: return ["yaşlı", "dede", "büyükbaba"]
        case .TEACHER: return ["öğretmen", "okul", "sınıf", "kadın"]
        case .DOCTOR: return ["doktor", "hastane", "sağlık", "hekim"]
        case .POLICE: return ["polis", "güvenlik", "üniforma"]
        case .DETECTIVE: return ["dedektif", "polisiye", "gizem", "şapka"]
        case .CHEF: return ["aşçı", "şef", "mutfak", "yemek"]
        case .ATHLETE: return ["sporcu", "spor", "koşucu", "futbol"]
        case .HERO: return ["kahraman", "pelerin", "maske", "süper"]
        case .ASTRONAUT: return ["astronot", "uzay", "kask"]
        case .ROBOT: return ["robot", "makine", "bilim kurgu"]
        case .KNIGHT: return ["şövalye", "zırh", "kale", "orta çağ"]
        case .WIZARD: return ["büyücü", "sihirbaz", "fantastik", "sakal"]
        }
    }
}

/// Yüz ifadeleri: v36'nın 8'i ve v37'de eklenen 5 (öfkeli bağırma, kararlı, alaycı, şok olmuş, heyecanlı).
enum CharExpression: String, CaseIterable {
    case CALM, HAPPY, SAD, ANGRY, SURPRISED, SCARED, THINKING, SHY, FURIOUS, DETERMINED, SMUG, SHOCKED, EXCITED

    var id: String { rawValue.lowercased() }
    var title: String {
        switch self {
        case .CALM: return "Sakin"
        case .HAPPY: return "Mutlu"
        case .SAD: return "Üzgün"
        case .ANGRY: return "Kızgın"
        case .SURPRISED: return "Şaşkın"
        case .SCARED: return "Korkmuş"
        case .THINKING: return "Düşünceli"
        case .SHY: return "Utangaç"
        case .FURIOUS: return "Öfkeli bağırma"
        case .DETERMINED: return "Kararlı"
        case .SMUG: return "Alaycı"
        case .SHOCKED: return "Şok olmuş"
        case .EXCITED: return "Heyecanlı"
        }
    }
}

/// Pozlar: v36'nın 9'u (kimlikleri değişmez) ve v37'de eklenen 17 aksiyon/duygu pozu.
enum CharPose: String, CaseIterable {
    case STAND, WALK, RUN, SIT, WAVE, POINT, CROSSED, JUMP, THINK, ATTACK, PUNCH, KICK, STANCE, FIGHT, SHOUT, BLOCK, FALL, HURT,
         VICTORY, CRY, LAUGH, FLY, CHARGE, VSIGN, COWER, SALUTE

    var id: String { rawValue.lowercased() }
    var title: String {
        switch self {
        case .STAND: return "Ayakta"
        case .WALK: return "Yürüyor"
        case .RUN: return "Koşuyor"
        case .SIT: return "Oturuyor"
        case .WAVE: return "El sallıyor"
        case .POINT: return "İşaret ediyor"
        case .CROSSED: return "Kollar kavuşturmuş"
        case .JUMP: return "Zıplıyor"
        case .THINK: return "Düşünüyor"
        case .ATTACK: return "Saldırıyor"
        case .PUNCH: return "Yumruk atıyor"
        case .KICK: return "Tekme atıyor"
        case .STANCE: return "Dövüş duruşu"
        case .FIGHT: return "Kavga ediyor"
        case .SHOUT: return "Bağırıyor"
        case .BLOCK: return "Savunuyor"
        case .FALL: return "Yere düşüyor"
        case .HURT: return "Yaralı"
        case .VICTORY: return "Zafer"
        case .CRY: return "Ağlıyor"
        case .LAUGH: return "Gülüyor"
        case .FLY: return "Uçuyor"
        case .CHARGE: return "Güç topluyor"
        case .VSIGN: return "V işareti"
        case .COWER: return "Korkup siniyor"
        case .SALUTE: return "Selam veriyor"
        }
    }
}

/// Tek karakter: tür, ifade ve poz. Şekil kimliği `chr.<tür>.<ifade>.<poz>` (kayıtta `lib.` önekiyle saklanır;
/// değişmemeli). Çizim tasarım biriminde yapılır ve sıkı sınır kutusuna yerleştirilir; [bounds] tasarım birimindeki
/// kutu (eklemede bütün karakterler aynı ölçekte görünsün diye).
final class CharacterDef {
    let kind: CharKind
    let expression: CharExpression
    let pose: CharPose
    let mirrored: Bool

    init(_ kind: CharKind, _ expression: CharExpression, _ pose: CharPose, _ mirrored: Bool = false) {
        self.kind = kind
        self.expression = expression
        self.pose = pose
        self.mirrored = mirrored
    }

    var id: String { ComicCharacters.ID_PREFIX + "\(kind.id).\(expression.id).\(pose.id)" + (mirrored ? ".m" : "") }

    /// Aynadaki hali (sola bakan / sağa bakan).
    func mirror() -> CharacterDef { ComicCharacters.def(kind, expression, pose, !mirrored) }

    /// Tasarım birimindeki sınır kutusu: sol, üst, en, boy (aynalı halde de aynı kutu).
    private(set) lazy var bounds: [Float] = {
        if mirrored { return ComicCharacters.def(kind, expression, pose).bounds }
        let vb = VectorBuilder(resolution: 0.5)
        drawRaw(vb)
        var l = Float.greatestFiniteMagnitude, t = Float.greatestFiniteMagnitude, r = -Float.greatestFiniteMagnitude, bt = -Float.greatestFiniteMagnitude
        for p in vb.paths { for q in p.points { l = Swift.min(l, q.x); t = Swift.min(t, q.y); r = Swift.max(r, q.x); bt = Swift.max(bt, q.y) } }
        let pad: Float = 2
        return [l - pad, t - pad, r - l + pad * 2, bt - t + pad * 2]
    }()

    func drawRaw(_ vb: VectorBuilder) {
        let p = ComicCharacters.pose(pose)
        Fig(vb, ComicCharacters.look(kind), p, ComicCharacters.face(expression).with(p.mod)).draw()
    }

    func paths(resolution: Float = 512) -> [VPath] {
        let bx = bounds
        let x = bx[0], y = bx[1], w = bx[2], h = bx[3]
        let vb = VectorBuilder(resolution: resolution)
        if mirrored {
            vb.withTransform(Affine(a: -1.0 / w, d: 1.0 / h, e: (x + w) / w, f: -y / h)) { drawRaw(vb) }
        } else {
            vb.inBox(-x / w, -y / h, 1.0 / w, 1.0 / h) { drawRaw(vb) }
        }
        return vb.paths
    }

    func shape() -> LibShape {
        let bx = bounds
        let tr = Locale(identifier: "tr_TR")
        return LibShape(id: id, title: kind.title, fit: .BOX, aspect: bx[2] / bx[3],
                        tags: kind.tags + [expression.title.lowercased(with: tr), pose.title.lowercased(with: tr)], paths: paths())
    }
}

/// Karakter kütüphanesi (Karakterler penceresi): 15 tür × 13 ifade × 26 poz = 5070 özgün karakter. Kütüphane
/// listesinde ve sunucu kataloğunda yer almaz (kimlikten üretilir, ShapeLibrary.find bulur); pencerede arama ve
/// süzgeçle seçilir. Bütün çizimler bu dosyadaki tablolarla ve CharacterKit/Fig/Head çizim takımıyla üretilir.
enum ComicCharacters {
    static let ID_PREFIX = "chr."

    /// Ayakta duran yetişkinin tasarım birimindeki boyu (eklemede ölçek).
    static let STANDING_HEIGHT: Float = 200

    private static let lock = NSLock()
    private static var shapes: [String: LibShape] = [:]
    private static var defs: [String: CharacterDef] = [:]

    static let all: [CharacterDef] = CharKind.allCases.flatMap { k in
        return CharExpression.allCases.flatMap { e in CharPose.allCases.map { p in def(k, e, p) } }
    }

    static func def(_ kind: CharKind, _ expression: CharExpression, _ pose: CharPose, _ mirrored: Bool = false) -> CharacterDef {
        let key = "\(kind.id).\(expression.id).\(pose.id)" + (mirrored ? ".m" : "")
        return lock.withLock {
            if let d = defs[key] { return d }
            let d = CharacterDef(kind, expression, pose, mirrored)
            defs[key] = d
            return d
        }
    }

    /// Kimlikten karakter ("lib." önekli ya da öneksiz; aynalı ".m" ekli); değilse nil.
    static func parse(_ id: String) -> CharacterDef? {
        let raw = id.hasPrefix(ShapeLibrary.PREFIX) ? String(id.dropFirst(ShapeLibrary.PREFIX.count)) : id
        guard raw.hasPrefix(ID_PREFIX) else { return nil }
        let parts = raw.dropFirst(ID_PREFIX.count).split(separator: ".", omittingEmptySubsequences: false).map(String.init)
        if parts.count != 3 && !(parts.count == 4 && parts[3] == "m") { return nil }
        guard let k = CharKind.allCases.first(where: { $0.id == parts[0] }),
              let e = CharExpression.allCases.first(where: { $0.id == parts[1] }),
              let p = CharPose.allCases.first(where: { $0.id == parts[2] }) else { return nil }
        return def(k, e, p, parts.count == 4)
    }

    /// Kütüphane şekli (önbellekli).
    static func shape(_ id: String) -> LibShape? {
        guard let d = parse(id) else { return nil }
        if let s = lock.withLock({ shapes[d.id] }) { return s }
        let s = d.shape()
        lock.withLock { shapes[d.id] = s }
        return s
    }

    /// Arama ve süzgeç: [query] sözcüklerinin hepsi türün adında/etiketlerinde, ifadenin ya da pozun adında geçmeli
    /// ([translate] adların seçili dildeki karşılığı; ikisi de aranır). Süzgeç nil ise o eksen serbest.
    static func search(_ query: String, kind: CharKind? = nil, expression: CharExpression? = nil, pose: CharPose? = nil,
                       translate: (String) -> String = { $0 }) -> [CharacterDef] {
        let tr = Locale(identifier: "tr_TR")
        let words = query.lowercased(with: tr).split(whereSeparator: { $0 == " " || $0 == "," })
            .map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
        func text(_ s: [String]) -> String { s.flatMap { [$0, translate($0)] }.joined(separator: " ").lowercased(with: tr) }
        let kinds = CharKind.allCases.filter { kind == nil || $0 == kind }
        let exprs = CharExpression.allCases.filter { expression == nil || $0 == expression }
        let poses = CharPose.allCases.filter { pose == nil || $0 == pose }
        var kindText: [CharKind: String] = [:]; for k in kinds { kindText[k] = text([k.title] + k.tags) }
        var exprText: [CharExpression: String] = [:]; for e in exprs { exprText[e] = text([e.title]) }
        var poseText: [CharPose: String] = [:]; for p in poses { poseText[p] = text([p.title]) }
        var out: [CharacterDef] = []
        for k in kinds { for e in exprs { for p in poses {
            let all = kindText[k]! + " " + exprText[e]! + " " + poseText[p]!
            if words.allSatisfy({ all.contains($0) }) { out.append(def(k, e, p)) }
        } } }
        return out
    }

    // ------------------------------------------------------------------ görünüşler (her tür ayrı tasarım)

    static func look(_ kind: CharKind) -> Look { looks[kind]! }

    static let looks: [CharKind: Look] = [// Diken saçlı, çilli, burnu yara bantlı enerjik çocuk: sarı tişört (mavi göğüs bandı, yıldız), kot şort
            CharKind.KID: Look(
                Body.CHILD, "#F5CBA7", Hair.SPIKY_KID, "#9A4421", "#D0711E", EyeStyle.KID, Outfit.TEE, "#F7C548", "#3D6FB6",
                Legwear.SHORTS, Shoe.SNEAKER, "#E0483C", [Extra.BANDAID, Extra.FRECKLES], accent: "#2F6DB5", aura: "#FFD23F"
            ), // Yüksek at kuyruklu, denizci yakalı okul üniformalı genç kız
            CharKind.STUDENT: Look(
                Body.TEEN_F, "#F8D7C2", Hair.PONYTAIL, "#2E3159", "#5B6BD8", EyeStyle.FEMALE, Outfit.SAILOR, "#F4F5FA", "#26355E",
                Legwear.SKIRT, Shoe.LOAFER, "#4A2E20", [Extra.RIBBON], accent: "#D8344A", props: [PropSlot.CALM: Prop.BAG], aura: "#FF9BC8"
            ), // Yandan ayrık saçlı, koyu takım elbiseli, evrak çantalı beyaz yakalı
            CharKind.OFFICE: Look(
                Body.ADULT_M, "#EDC29C", Hair.PARTED, "#1E1E28", "#6B4A32", EyeStyle.MALE, Outfit.SUIT, "#3E4452", "#363B48",
                Legwear.PANTS, Shoe.SHOE, "#1F1A1A", [Extra.TIE], accent: "#C23A3A", trim: "#F4F6F8",
                props: [PropSlot.CALM: Prop.BRIEFCASE]
            ), // Kel tepeli, kabarık ak bıyıklı, yuvarlak gözlüklü, bastonlu sevimli dede
            CharKind.ELDER: Look(
                Body.ELDER, "#F0C8A8", Hair.TUFTS, "#ECECF2", "#6B5A4A", EyeStyle.ELDER, Outfit.CARDIGAN, "#8A6A4A", "#6B6B5A",
                Legwear.PANTS, Shoe.SHOE, "#5B3A2A", [Extra.ROUND_GLASSES, Extra.MUSTACHE], trim: "#F1E6CF",
                props: [PropSlot.CALM: Prop.CANE, PropSlot.ACTION: Prop.CANE], aura: "#B6F09C"
            ), // Topuzlu, kırmızı çerçeveli gözlüklü, kitaplı öğretmen (işaret ederken çubuk)
            CharKind.TEACHER: Look(
                Body.ADULT_F, "#8D5A3B", Hair.BUN, "#1F1A24", "#5A3420", EyeStyle.FEMALE, Outfit.BLOUSE, "#2FA7A0", "#6E4FB8",
                Legwear.PENCIL, Shoe.LOAFER, "#3B2A2A", [Extra.GLASSES], accent: "#B03A48", trim: "#F3EBDD",
                props: [PropSlot.CALM: Prop.BOOK, PropSlot.POINTER: Prop.POINTER], aura: "#9CF0E6"
            ), // Omuz boyu dalgalı sarı saçlı, önlüklü, steteskoplu doktor
            CharKind.DOCTOR: Look(
                Body.ADULT_F, "#F7D9C4", Hair.WAVY, "#E2B85C", "#3E9E6E", EyeStyle.FEMALE, Outfit.LABCOAT, "#F7F9FC", "#7FB7E0",
                Legwear.PANTS, Shoe.SNEAKER, "#5A7FB0", [Extra.STETHOSCOPE, Extra.HAIRCLIP], accent: "#E0483C",
                props: [PropSlot.CALM: Prop.CLIPBOARD], aura: "#9CF0C0"
            ), // Kasketli, rozetli, geniş omuzlu polis (aksiyonda cop)
            CharKind.POLICE: Look(
                Body.ATHLETIC, "#B9784F", Hair.UNDER_CAP, "#1A1A22", "#3A6BA8", EyeStyle.COMIC, Outfit.UNIFORM, "#22305A", "#1E2640",
                Legwear.PANTS, Shoe.BOOT, "#16161C", [Extra.POLICE_CAP, Extra.BADGE], accent: "#151C33",
                props: [PropSlot.ACTION: Prop.BATON], aura: "#7FB8FF"
            ), // Fötr şapkalı, trençkotlu, kırmızı atkılı, büyüteçli dedektif (alnına düşen kızıl perçem)
            CharKind.DETECTIVE: Look(
                Body.ADULT_M, "#EFC6A2", Hair.FORELOCK, "#8A3B22", "#B06A1E", EyeStyle.MALE, Outfit.TRENCH, "#C8A97A", "#4A4A52",
                Legwear.PANTS, Shoe.SHOE, "#3B2A20", [Extra.FEDORA, Extra.SCARF], accent: "#8E2B2B",
                props: [PropSlot.CALM: Prop.MAGNIFIER], aura: "#FFB36B"
            ), // Uzun şef külahlı, burma bıyıklı, önlüklü aşçı (sakinken kepçe, aksiyonda tava)
            CharKind.CHEF: Look(
                Body.ADULT_M, "#D9A07A", Hair.CROPPED, "#3B2A20", "#4A3020", EyeStyle.MALE, Outfit.CHEF, "#F7F7F2", "#59606C",
                Legwear.PANTS, Shoe.SHOE, "#22222A", [Extra.CHEF_HAT, Extra.NECKERCHIEF, Extra.CHEF_MUSTACHE, Extra.APRON], accent: "#E0483C",
                props: [PropSlot.CALM: Prop.LADLE, PropSlot.ACTION: Prop.PAN], aura: "#FFB36B"
            ), // Yukarı diken saçlı, alın bantlı, kaslı sporcu: kırmızı forma (7), bileklik, şort, uzun çorap
            CharKind.ATHLETE: Look(
                Body.ATHLETIC, "#7A4A30", Hair.SPIKY_UP, "#16161C", "#E0A030", EyeStyle.MALE, Outfit.JERSEY, "#E0483C", "#F4F4F4",
                Legwear.SHORTS, Shoe.SNEAKER, "#2F6DB5", [Extra.HEADBAND, Extra.WRISTBANDS], accent: "#2F6DB5",
                props: [PropSlot.CALM: Prop.BALL], aura: "#FFD23F"
            ), // Çizgi roman kahramanı (özgün): camgöbeği tulum, lacivert yan panel ve maske, altın eldiven/çizme/kemer,
            // kızıl pelerin, göğüste eşkenar dörtgen içinde çift köşeli ok arması
            CharKind.HERO: Look(
                Body.HEROIC, "#F2C7A0", Hair.SWEPT_BACK, "#1D1D24", "#2FB5A8", EyeStyle.COMIC, Outfit.HEROSUIT, "#16807A", "#1B2238",
                Legwear.TIGHTS, Shoe.HERO_BOOT, "#F2B533", [Extra.CAPE, Extra.MASK], accent: "#C8323C", trim: "#F2B533", aura: "#FFD23F"
            ), // Cam kasklı, küt saçlı kadın astronot: beyaz uzay giysisi, turuncu şeritler, sırt ünitesi
            CharKind.ASTRONAUT: Look(
                Body.ADULT_F, "#C68E66", Hair.BOB, "#3A2A22", "#8A5A2A", EyeStyle.FEMALE, Outfit.SPACESUIT, "#EEF1F5", "#E3E7EC",
                Legwear.PANTS, Shoe.SPACE, "#C9D1DA", [Extra.SPACE_HELMET, Extra.BACKPACK], accent: "#F28A2E", aura: "#9FE4FF"
            ), // Mekanik robot: açık gri zırh plakaları, turuncu omuzluklar, parlayan göğüs çekirdeği ve ekran yüz
            CharKind.ROBOT: Look(
                Body.MECHA, "#D5DCE4", Hair.NONE, "#000000", "#53E0FF", EyeStyle.SCREEN, Outfit.MECHA, "#D5DCE4", "#5B6573",
                Legwear.MECHA, Shoe.MECHA, "#5B6573", accent: "#F28A2E", aura: "#53E0FF"
            ), // Sarı saçlı genç şövalye: gümüş zırh, omuzluklar, kırmızı armalı önlük, kılıç
            CharKind.KNIGHT: Look(
                Body.ATHLETIC, "#F3CFAE", Hair.KNIGHT, "#E8C766", "#3B7BD8", EyeStyle.MALE, Outfit.ARMOR, "#C9D2DC", "#4A4E5A",
                Legwear.PANTS, Shoe.SABATON, "#B8C2CE", accent: "#A92E28", trim: "#F2C14E",
                props: [PropSlot.CALM: Prop.SWORD, PropSlot.ACTION: Prop.SWORD], aura: "#FFE07A"
            ), // Uzun ak saçlı ve sakallı büyücü: mor cüppe, altın şeritler, kıvrık uçlu yıldızlı şapka, kristal asa
            CharKind.WIZARD: Look(
                Body.SAGE, "#EBC5A5", Hair.LONG, "#ECECF2", "#5B7BD8", EyeStyle.ELDER, Outfit.ROBE, "#5B3E99", "#3B2A55",
                Legwear.ROBE, Shoe.SHOE, "#5A3A22", [Extra.WIZARD_HAT, Extra.BEARD], trim: "#F2C14E",
                props: [PropSlot.CALM: Prop.STAFF, PropSlot.ACTION: Prop.STAFF], aura: "#B57CFF"
            )]

    // ------------------------------------------------------------------ ifadeler

    static func face(_ e: CharExpression) -> FaceSpec { faces[e]! }

    static let faces: [CharExpression: FaceSpec] = [CharExpression.CALM: FaceSpec(mouth: Mouth.SMILE), CharExpression.HAPPY: FaceSpec(browIn: -0.06, browOut: -0.03, lowerLid: 0.22, mouth: Mouth.OPEN_SMILE, blush: true), CharExpression.SAD: FaceSpec(browIn: -0.15, browOut: 0.07, lidOut: 0.25, gazeY: 0.6, mouth: Mouth.FROWN, tear: true), CharExpression.ANGRY: FaceSpec(browIn: 0.15, browOut: -0.07, lidIn: 0.55, lidOut: 0.1, iris: 0.88, mouth: Mouth.GRIT, vein: true), CharExpression.SURPRISED: FaceSpec(eye: EyeShape.WIDE, browIn: -0.17, browOut: -0.12, mouth: Mouth.O), CharExpression.SCARED: FaceSpec(eye: EyeShape.WIDE, browIn: -0.15, browOut: 0.06, iris: 0.62, mouth: Mouth.WAVY, sweat: true, gloom: true), CharExpression.THINKING: FaceSpec(browIn: 0.03, lidIn: 0.15, lidOut: 0.1, gazeX: 0.7, gazeY: -0.6, mouth: Mouth.LINE, browRaiseR: 0.09), CharExpression.SHY: FaceSpec(browIn: -0.1, browOut: 0.03, lidIn: 0.25, lidOut: 0.25, gazeX: -0.6, gazeY: 0.5, mouth: Mouth.POUT, blush: true), CharExpression.FURIOUS: FaceSpec(browIn: 0.2, browOut: -0.1, lidIn: 0.6, lidOut: 0.15, iris: 0.62, mouth: Mouth.SHOUT, vein: true), CharExpression.DETERMINED: FaceSpec(browIn: 0.1, browOut: -0.04, lidIn: 0.3, lidOut: 0.12, mouth: Mouth.LINE, sparkle: true), CharExpression.SMUG: FaceSpec(eye: EyeShape.HALF, browIn: 0.05, gazeX: -0.5, mouth: Mouth.SMIRK, browRaiseR: 0.1), CharExpression.SHOCKED: FaceSpec(eye: EyeShape.TINY, browIn: -0.19, browOut: -0.1, mouth: Mouth.JAWDROP, sweat: true, gloom: true), CharExpression.EXCITED: FaceSpec(eye: EyeShape.STAR, browIn: -0.08, browOut: -0.06, mouth: Mouth.OPEN_SMILE, blush: true)]

    // ------------------------------------------------------------------ pozlar (açı tabloları; her vücut tipinde çalışır)

    static func pose(_ p: CharPose) -> PoseSpec { poses[p]! }

    static func arm(_ upper: Float, _ lower: Float, _ k: Float = 1.0) -> ArmSpec { ArmSpec(upper, lower, nil, 0, 0, 1, k) }
    static func reach(_ m: Mark, _ dx: Float = 0.0, _ dy: Float = 0.0, _ bend: Float = 1.0, _ k: Float = 1.0) -> ArmSpec { ArmSpec(0.0, 0.0, m, dx, dy, bend, k) }
    static func leg(_ thigh: Float, _ shin: Float) -> LegSpec { LegSpec(thigh, shin) }

    static let poses: [CharPose: PoseSpec] = [CharPose.STAND: PoseSpec(prop: PropSlot.CALM), CharPose.WALK: PoseSpec(armL: arm(-20.0, -10.0), armR: arm(22.0, 40.0), legL: leg(18.0, 6.0), legR: leg(-14.0, -40.0), lean: 3.0, turn: 0.35, fx: [PoseFx.DUST], prop: PropSlot.CALM), CharPose.RUN: PoseSpec(armL: arm(-55.0, 15.0), armR: arm(55.0, 150.0), legL: leg(62.0, -8.0), legR: leg(-28.0, -78.0), lean: 14.0, headTilt: -6.0, turn: 0.6, handL: Hand.FIST, handR: Hand.FIST, layerR: ArmLayer.BACK, fx: [PoseFx.SPEED, PoseFx.DUST]), CharPose.SIT: PoseSpec(armL: reach(Mark.KNEE_L, -2.0, 0.0, 1.0), armR: reach(Mark.KNEE_R, 0.0, 0.0, 1.0), legL: leg(80.0, 4.0), legR: leg(86.0, -4.0), lean: -2.0, turn: 0.45, seat: true), CharPose.WAVE: PoseSpec(armR: arm(150.0, 172.0), headTilt: 6.0, handR: Hand.OPEN, fx: [PoseFx.WAVE_ARCS], prop: PropSlot.CALM, propRight: false), CharPose.POINT: PoseSpec(armL: reach(Mark.HIP_L, -1.0, 0.0, 1.0), armR: arm(95.0, 92.0), legL: leg(-8.0, -3.0), legR: leg(10.0, 4.0), lean: 2.0, turn: 0.4, handL: Hand.FIST, handR: Hand.POINT, fx: [PoseFx.POINT_LINES], prop: PropSlot.POINTER), CharPose.CROSSED: PoseSpec(armL: arm(8.0, 84.0), armR: arm(-8.0, -96.0), legL: leg(-6.0, -3.0), legR: leg(6.0, 3.0), headTilt: -4.0, handL: Hand.HIDDEN, handR: Hand.FIST, crossed: true), CharPose.JUMP: PoseSpec(armL: arm(-138.0, -158.0), armR: arm(138.0, 158.0), legL: leg(-40.0, 12.0), legR: leg(40.0, -12.0), headTilt: -4.0, handL: Hand.OPEN, handR: Hand.OPEN, lift: 24.0, fx: [PoseFx.JUMP_LINES]), CharPose.THINK: PoseSpec(armL: arm(10.0, 82.0), armR: reach(Mark.CHIN, 0.0, 3.0, -1.0, 0.62), headTilt: -5.0, handL: Hand.FIST, handR: Hand.FIST, layerR: ArmLayer.TOP, fx: [PoseFx.THOUGHT]), CharPose.ATTACK: PoseSpec(armL: arm(80.0, 95.0), armR: arm(178.0, -150.0), legL: leg(62.0, 8.0), legR: leg(-48.0, -62.0), lean: 16.0, headTilt: -6.0, turn: 0.55, handL: Hand.CLAW, handR: Hand.FIST, fx: [PoseFx.SPEED, PoseFx.DUST, PoseFx.SWISH_ARM], prop: PropSlot.ACTION), CharPose.PUNCH: PoseSpec(armL: arm(-20.0, 160.0), armR: arm(90.0, 90.0), legL: leg(40.0, 6.0), legR: leg(-32.0, -24.0), lean: 8.0, headTilt: -4.0, turn: 0.5, handL: Hand.FIST, handR: Hand.FIST, layerR: ArmLayer.BACK, fx: [PoseFx.IMPACT_HAND]), CharPose.KICK: PoseSpec(armL: arm(-105.0, -75.0), armR: arm(20.0, 150.0), legL: leg(-6.0, 0.0), legR: leg(98.0, 92.0), lean: -18.0, headTilt: 8.0, turn: 0.5, handL: Hand.FIST, handR: Hand.FIST, fx: [PoseFx.IMPACT_FOOT, PoseFx.SWISH_KICK], footR: 92.0), CharPose.STANCE: PoseSpec(armL: arm(-25.0, 165.0), armR: arm(35.0, 155.0), legL: leg(-32.0, 6.0), legR: leg(36.0, -8.0), lean: 4.0, headTilt: -3.0, turn: 0.3, handL: Hand.FIST, handR: Hand.FIST, prop: PropSlot.ACTION), CharPose.FIGHT: PoseSpec(armL: arm(-70.0, -160.0), armR: arm(70.0, 88.0), legL: leg(38.0, 10.0), legR: leg(-30.0, -12.0), lean: 14.0, turn: 0.45, handL: Hand.FIST, handR: Hand.CLAW, layerR: ArmLayer.BACK, fx: [PoseFx.BRAWL, PoseFx.DUST, PoseFx.SWEAT], mod: FaceMod.EFFORT), CharPose.SHOUT: PoseSpec(armL: arm(-28.0, -18.0), armR: arm(28.0, 18.0), legL: leg(-16.0, -4.0), legR: leg(16.0, 4.0), lean: 3.0, headTilt: -8.0, handL: Hand.FIST, handR: Hand.FIST, fx: [PoseFx.SHOUT], mod: FaceMod.SHOUT), CharPose.BLOCK: PoseSpec(armL: reach(Mark.CHIN, 9.0, 7.0, 1.0, 0.7), armR: reach(Mark.CHIN, -9.0, 7.0, -1.0, 0.7), legL: leg(-24.0, -6.0), legR: leg(18.0, 12.0), lean: -6.0, turn: 0.15, handL: Hand.FIST, handR: Hand.FIST, layerL: ArmLayer.TOP, layerR: ArmLayer.TOP, fx: [PoseFx.BLOCK_ARCS, PoseFx.SWEAT]), CharPose.FALL: PoseSpec(armL: arm(-140.0, -110.0), armR: arm(150.0, 120.0), legL: leg(-10.0, 30.0), legR: leg(60.0, 40.0), headTilt: -10.0, handL: Hand.OPEN, handR: Hand.OPEN, lift: 14.0, tilt: -40.0, fx: [PoseFx.FALL_LINES], mod: FaceMod.FALL), CharPose.HURT: PoseSpec(armL: reach(Mark.KNEE_L, 0.0, -2.0, 1.0), armR: reach(Mark.CHEST, -14.0, -6.0, -1.0, 0.8), legL: leg(-82.0, -25.0), legR: leg(0.0, 92.0), lean: 6.0, headTilt: 10.0, turn: -0.25, handR: Hand.OPEN, fx: [PoseFx.PAIN], mod: FaceMod.HURT), CharPose.VICTORY: PoseSpec(armL: reach(Mark.HIP_L, -1.0, 0.0, 1.0), armR: arm(150.0, 160.0), legL: leg(-12.0, -6.0), legR: leg(12.0, 5.0), lean: -2.0, headTilt: -8.0, handL: Hand.FIST, handR: Hand.FIST, fx: [PoseFx.SPARKLE], prop: PropSlot.ACTION), CharPose.CRY: PoseSpec(armL: reach(Mark.EYE_L, -3.0, 11.0, 1.0, 0.65), armR: reach(Mark.EYE_R, 3.0, 11.0, -1.0, 0.65), legL: leg(6.0, -6.0), legR: leg(-6.0, 6.0), headTilt: 4.0, handL: Hand.FIST, handR: Hand.FIST, layerL: ArmLayer.TOP, layerR: ArmLayer.TOP, fx: [PoseFx.SOB], mod: FaceMod.CRY), CharPose.LAUGH: PoseSpec(armL: arm(-120.0, -160.0), armR: reach(Mark.BELLY, 6.0, 0.0, -1.0), legL: leg(-6.0, -2.0), legR: leg(8.0, 4.0), lean: -6.0, headTilt: -10.0, handL: Hand.OPEN, handR: Hand.OPEN, fx: [PoseFx.LAUGH_MARKS], mod: FaceMod.LAUGH), CharPose.FLY: PoseSpec(armL: arm(-18.0, -12.0), armR: arm(160.0, 160.0), legL: leg(-6.0, -14.0), legR: leg(4.0, -24.0), handL: Hand.FIST, handR: Hand.FIST, lift: 30.0, tilt: 28.0, fx: [PoseFx.WIND], footL: -14.0, footR: -24.0), CharPose.CHARGE: PoseSpec(armL: reach(Mark.HIP_L, -2.0, 4.0, 1.0), armR: reach(Mark.HIP_R, 2.0, 4.0, -1.0), legL: leg(-50.0, 0.0), legR: leg(50.0, 0.0), headTilt: 6.0, handL: Hand.FIST, handR: Hand.FIST, fx: [PoseFx.AURA], mod: FaceMod.EFFORT), CharPose.VSIGN: PoseSpec(armL: reach(Mark.HIP_L, -1.0, 0.0, 1.0), armR: reach(Mark.EYE_R, 10.0, 2.0, -1.0, 0.75), legL: leg(-3.0, -1.0), legR: leg(-2.0, 18.0), headTilt: 8.0, turn: -0.1, handL: Hand.FIST, handR: Hand.VSIGN, layerR: ArmLayer.TOP, fx: [PoseFx.SPARKLE], mod: FaceMod.WINK), CharPose.COWER: PoseSpec(armL: reach(Mark.HEAD_TOP, -8.0, 2.0, 1.0), armR: reach(Mark.HEAD_TOP, 8.0, 2.0, -1.0), legL: leg(-60.0, 20.0), legR: leg(60.0, -20.0), lean: 6.0, headTilt: 10.0, handL: Hand.FLAT, handR: Hand.FLAT, layerL: ArmLayer.TOP, layerR: ArmLayer.TOP, fx: [PoseFx.TREMBLE, PoseFx.SWEAT]), CharPose.SALUTE: PoseSpec(armL: arm(-4.0, -2.0), armR: reach(Mark.BROW_R, 2.0, -1.0, -1.0, 0.85), legL: leg(-2.0, -1.0), legR: leg(2.0, 1.0), handL: Hand.FLAT, handR: Hand.FLAT, layerR: ArmLayer.TOP)]
}

