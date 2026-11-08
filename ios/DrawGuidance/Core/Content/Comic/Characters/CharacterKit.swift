import Foundation



/*
 * v37 karakter çizim takımı: anime / çizgi roman stili. Karakterler özgündür (hiçbir hazır karakteri taklit etmez);
 * tasarım birimi ayakta duran bir yetişkinin boyu ~196 (ayak tabanı y=196). Çizim üç katmandan oluşur:
 * - İskelet ([Skel]): poz açıları (ya da hedef noktası; ör. el çenede) ve vücut ölçülerinden eklem noktaları. Aynı poz
 *   her vücut tipinde (çocuk, kahraman, robot) çalışır; en alttaki nokta (taban, diz) zemine oturtulur.
 * - Vücut ve giysi ([Fig], CharacterFig.kt): dolgulu kapsül uzuvlar (iki geçiş: önce kalın kenar, sonra kenarsız
 *   dolgu; birleşimde iç çizgi kalmaz), cel gölgeler (kenarsız koyu ton), inceltilmiş mürekkep çizgileri.
 * - Baş ([Fig.headPart], CharacterHead.kt): büyük anime gözleri (iris, göz bebeği, parıltılar, kalın kirpik çizgisi),
 *   tutam tutam saç, ifadeye göre kaş/ağız ve duygu işaretleri.
 * Kenar kalınlıkları tasarım biriminin katıdır (uygulamada elemanın kenar kalınlığı 1 birim).
 */

/** Çene biçimi: çocuk yuvarlak, kadın yumuşak, anime erkek sivri, çizgi roman kahramanı köşeli, yaşlı. */
enum Jaw: CaseIterable { case ROUND, SOFT, SHARP, SQUARE, OLD }

/**
 * Vücut ölçüleri (tasarım birimi). Yarı genişlikler gövde çizgisinindir; eklem aralıkları ([shoulderJ], [hipJ]) uzuvların
 * başladığı noktalar. [muscle] kas çizgileri (0..1), [bust] kadın göğüs çizgisi.
 */
struct Body {
    let headR: Float
    let jaw: Jaw
    let neckLen: Float
    let neckR: Float
    let torsoLen: Float
    let shoulderJ: Float
    let shoulderOut: Float
    let chestW: Float
    let waistW: Float
    let hipW: Float
    let hipJ: Float
    let upperArm: Float
    let foreArm: Float
    let armR0: Float
    let armR1: Float
    let armR2: Float
    let handR: Float
    let thigh: Float
    let shin: Float
    let legR0: Float
    let legR1: Float
    let legR2: Float
    let footLen: Float
    let muscle: Float
    let female: Bool
    let bust: Float
    init(_ headR: Float, _ jaw: Jaw, _ neckLen: Float, _ neckR: Float, _ torsoLen: Float, _ shoulderJ: Float, _ shoulderOut: Float, _ chestW: Float, _ waistW: Float, _ hipW: Float, _ hipJ: Float, _ upperArm: Float, _ foreArm: Float, _ armR0: Float, _ armR1: Float, _ armR2: Float, _ handR: Float, _ thigh: Float, _ shin: Float, _ legR0: Float, _ legR1: Float, _ legR2: Float, _ footLen: Float, muscle: Float = 0.0, female: Bool = false, bust: Float = 0.0) {
        self.headR = headR
        self.jaw = jaw
        self.neckLen = neckLen
        self.neckR = neckR
        self.torsoLen = torsoLen
        self.shoulderJ = shoulderJ
        self.shoulderOut = shoulderOut
        self.chestW = chestW
        self.waistW = waistW
        self.hipW = hipW
        self.hipJ = hipJ
        self.upperArm = upperArm
        self.foreArm = foreArm
        self.armR0 = armR0
        self.armR1 = armR1
        self.armR2 = armR2
        self.handR = handR
        self.thigh = thigh
        self.shin = shin
        self.legR0 = legR0
        self.legR1 = legR1
        self.legR2 = legR2
        self.footLen = footLen
        self.muscle = muscle
        self.female = female
        self.bust = bust
    }

            /** Çocuk (~3.8 baş boyu). */
        static let CHILD = Body(
            18.0, Jaw.ROUND, 14.0, 3.6, 33.0, 11.0, 15.5, 13.5, 12.5, 13.0, 6.5,
            21.0, 19.0, 4.3, 3.7, 3.1, 3.9, 31.0, 30.0, 6.3, 4.7, 3.6, 10.0
        )
        /** Genç kız (öğrenci). */
        static let TEEN_F = Body(
            16.5, Jaw.SOFT, 16.5, 3.6, 43.0, 13.0, 17.5, 15.0, 11.5, 15.5, 8.0,
            28.0, 26.0, 4.5, 3.7, 2.9, 3.9, 43.0, 42.0, 7.2, 5.0, 3.5, 11.0,
            female: true, bust: 0.35
        )
        /** Yetişkin kadın. */
        static let ADULT_F = Body(
            16.0, Jaw.SOFT, 17.0, 3.7, 46.0, 13.5, 18.5, 16.0, 12.0, 16.5, 8.5,
            29.0, 27.0, 4.6, 3.8, 3.0, 4.0, 45.0, 44.0, 7.6, 5.2, 3.6, 11.5,
            female: true, bust: 0.6
        )
        /** Yetişkin erkek (~5.6 baş boyu). */
        static let ADULT_M = Body(
            16.5, Jaw.SHARP, 17.5, 4.4, 50.0, 16.5, 22.5, 19.0, 15.0, 15.5, 8.0,
            32.0, 30.0, 5.4, 4.4, 3.4, 4.6, 46.0, 45.0, 8.2, 5.9, 4.2, 13.0
        )
        /** Sporcu / polis / şövalye: geniş omuz, kaslı. */
        static let ATHLETIC = Body(
            16.0, Jaw.SQUARE, 17.5, 4.8, 52.0, 19.0, 26.0, 22.0, 15.0, 16.0, 8.5,
            32.0, 30.0, 6.4, 5.0, 3.8, 5.0, 47.0, 46.0, 8.8, 6.4, 4.4, 13.5,
            muscle: 0.6
        )
        /** Çizgi roman kahramanı (~6 baş boyu, V gövde). */
        static let HEROIC = Body(
            15.5, Jaw.SQUARE, 17.0, 5.2, 56.0, 22.0, 30.0, 26.0, 15.5, 17.0, 9.0,
            34.0, 31.0, 7.6, 6.0, 4.4, 5.4, 50.0, 48.0, 9.6, 7.0, 4.8, 14.0,
            muscle: 1.0
        )
        /** Yaşlı (biraz kısa, göbekli). */
        static let ELDER = Body(
            16.5, Jaw.OLD, 15.0, 4.2, 46.0, 15.0, 20.5, 18.0, 17.0, 16.5, 8.0,
            29.0, 27.0, 5.0, 4.2, 3.3, 4.4, 42.0, 41.0, 7.6, 5.6, 4.0, 12.5
        )
        /** Büyücü (uzun yaşlı). */
        static let SAGE = Body(
            16.5, Jaw.OLD, 16.0, 4.2, 50.0, 15.5, 21.0, 18.0, 15.5, 16.0, 8.0,
            31.0, 29.0, 5.0, 4.2, 3.3, 4.4, 45.0, 44.0, 7.6, 5.6, 4.0, 12.5
        )
        /** Robot (mekanik, kalın uzuvlar). */
        static let MECHA = Body(
            16.0, Jaw.SQUARE, 19.0, 4.6, 52.0, 21.0, 28.0, 23.0, 14.0, 17.0, 9.0,
            31.0, 30.0, 5.0, 5.6, 5.4, 5.8, 46.0, 46.0, 7.0, 7.4, 6.6, 15.0,
            muscle: 0.0
        )
    
}

/** Saç biçimi (her türe özgü). */
enum Hair: CaseIterable { case SPIKY_KID, PONYTAIL, PARTED, TUFTS, BUN, WAVY, UNDER_CAP, FORELOCK, CROPPED, SPIKY_UP, SWEPT_BACK, BOB, KNIGHT, LONG, NONE }

/** Göz çizimi: kadın (uzun kirpik), erkek, çocuk (iri, yuvarlak), çizgi roman (keskin, küçük iris), yaşlı. */
enum EyeStyle: CaseIterable { case FEMALE, MALE, KID, COMIC, ELDER, SCREEN }

/** Üst giysi (her tür kendi kıyafetini giyer). */
enum Outfit: CaseIterable { case TEE, SAILOR, SUIT, CARDIGAN, BLOUSE, LABCOAT, UNIFORM, TRENCH, CHEF, JERSEY, HEROSUIT, SPACESUIT, MECHA, ARMOR, ROBE }

/** Alt giysi. */
enum Legwear: CaseIterable { case PANTS, SHORTS, SKIRT, PENCIL, TIGHTS, MECHA, ROBE }

/** Ayakkabı. */
enum Shoe: CaseIterable { case SNEAKER, SHOE, LOAFER, BOOT, HERO_BOOT, SPACE, MECHA, SABATON }

/** Baş, yüz ve gövde eklentileri. */
enum Extra: CaseIterable { case GLASSES, ROUND_GLASSES, MUSTACHE, CHEF_MUSTACHE, BEARD, TIE, STETHOSCOPE, BADGE, POLICE_CAP, FEDORA, CHEF_HAT,
    HEADBAND, CAPE, MASK, SPACE_HELMET, BACKPACK, WIZARD_HAT, NECKERCHIEF, SCARF, BANDAID, FRECKLES, RIBBON, HAIRCLIP,
    WRISTBANDS, APRON }

/** Elde tutulan eşya. */
enum Prop: CaseIterable { case SWORD, STAFF, CANE, BRIEFCASE, BOOK, POINTER, CLIPBOARD, BATON, MAGNIFIER, LADLE, PAN, BALL, BAG }

/** Pozun eşya yuvası: sakin (ayakta, yürürken), aksiyon (saldırı, zafer), işaret (gösterirken). */
enum PropSlot: CaseIterable { case CALM, ACTION, POINTER }

/**
 * Karakterin görünüşü. Renkler: [main] üst giysi, [second] alt giysi, [accent] vurgu (kurdele, kravat, pelerin),
 * [trim] süs/şerit. [props] poz yuvasına göre elde tutulan eşya.
 */
struct Look {
    let body: Body
    let skin: String
    let hair: Hair
    let hairColor: String
    let eyeColor: String
    let eyes: EyeStyle
    let outfit: Outfit
    let main: String
    let second: String
    let legwear: Legwear
    let shoe: Shoe
    let shoeColor: String
    let extras: Set<Extra>
    let accent: String
    let trim: String
    let props: [PropSlot: Prop]
    let aura: String
    init(_ body: Body, _ skin: String, _ hair: Hair, _ hairColor: String, _ eyeColor: String, _ eyes: EyeStyle, _ outfit: Outfit, _ main: String, _ second: String, _ legwear: Legwear, _ shoe: Shoe, _ shoeColor: String, _ extras: Set<Extra> = [], accent: String = "#E0483C", trim: String = "#F4F1EA", props: [PropSlot: Prop] = [:], aura: String = "#7FD8FF") {
        self.body = body
        self.skin = skin
        self.hair = hair
        self.hairColor = hairColor
        self.eyeColor = eyeColor
        self.eyes = eyes
        self.outfit = outfit
        self.main = main
        self.second = second
        self.legwear = legwear
        self.shoe = shoe
        self.shoeColor = shoeColor
        self.extras = extras
        self.accent = accent
        self.trim = trim
        self.props = props
        self.aura = aura
    }
}

// ------------------------------------------------------------------ poz

/** Elin biçimi. */
enum Hand: CaseIterable { case RELAXED, FIST, OPEN, POINT, CLAW, VSIGN, FLAT, HIDDEN }

/** Kolun çizim katmanı: gövdenin arkası, gövdenin önü, başın önü. */
enum ArmLayer: CaseIterable { case BACK, MID, TOP }

/** Kol hedefi (el bu noktaya uzanır; dirsek ters kinematikle bulunur). */
enum Mark: CaseIterable { case CHIN, MOUTH, EYE_L, EYE_R, BROW_R, HEAD_TOP, CHEST, BELLY, HIP_L, HIP_R, KNEE_L, KNEE_R, NAPE }

/**
 * Kol: [upper] ve [lower] mutlak açılar (derece; 0 aşağı, 90 ekranın sağı, 180 yukarı, -90 sol). [reach] verilirse
 * el o noktaya ([dx],[dy] kaydırmasıyla) uzanır, dirsek [bend] yönünde (+1 saat yönü) kırılır.
 */
struct ArmSpec {
    let upper: Float
    let lower: Float
    let reach: Mark?
    let dx: Float
    let dy: Float
    let bend: Float
    let upperK: Float
    init(_ upper: Float, _ lower: Float, _ reach: Mark? = nil, _ dx: Float = 0.0, _ dy: Float = 0.0, _ bend: Float = 1.0, _ upperK: Float = 1.0) {
        self.upper = upper
        self.lower = lower
        self.reach = reach
        self.dx = dx
        self.dy = dy
        self.bend = bend
        self.upperK = upperK
    }
}

/** Bacak: uyluk ve baldır mutlak açıları (kol ile aynı açı düzeni). */
struct LegSpec {
    let thigh: Float
    let shin: Float
    init(_ thigh: Float, _ shin: Float) {
        self.thigh = thigh
        self.shin = shin
    }
}

/** Hareket ve duygu efektleri (pozla gelir). */
enum PoseFx: CaseIterable { case SPEED, DUST, IMPACT_HAND, IMPACT_FOOT, SWISH_KICK, SWISH_ARM, SHOUT, SPARKLE, AURA, WIND, JUMP_LINES, DIZZY, PAIN,
    SWEAT, LAUGH_MARKS, THOUGHT, WAVE_ARCS, BLOCK_ARCS, FALL_LINES, POINT_LINES, SOB, TREMBLE, BRAWL, MAGIC }

/** Pozun yüze etkisi (ağız/göz ifadenin üstüne yazılır). */
enum FaceMod: CaseIterable { case NONE, SHOUT, LAUGH, CRY, EFFORT, FALL, HURT, WINK }

/**
 * Poz tanımı (tablo verisi; bkz. ComicCharacters.poses). [lean] gövde eğimi (+ üst gövde sağa), [headTilt] başın ek
 * eğimi, [turn] baş ve gövdenin dönüşü (-1 sol .. 1 sağ; yüz o yana bakar), [tilt] bütün vücut (düşerken), [lift]
 * zeminden yükseklik (zıplama, uçma). [crossed] kollar göğüste kavuşturulmuş, [seat] tabureye oturmuş.
 */
struct PoseSpec {
    let armL: ArmSpec
    let armR: ArmSpec
    let legL: LegSpec
    let legR: LegSpec
    let lean: Float
    let headTilt: Float
    let turn: Float
    let handL: Hand
    let handR: Hand
    let layerL: ArmLayer
    let layerR: ArmLayer
    let lift: Float
    let tilt: Float
    let seat: Bool
    let crossed: Bool
    let fx: Set<PoseFx>
    let mod: FaceMod
    let prop: PropSlot?
    let propRight: Bool
    let footL: Float?
    let footR: Float?
    init(armL: ArmSpec = ArmSpec(-9.0, -4.0), armR: ArmSpec = ArmSpec(9.0, 4.0), legL: LegSpec = LegSpec(-4.0, -2.0), legR: LegSpec = LegSpec(4.0, 2.0), lean: Float = 0.0, headTilt: Float = 0.0, turn: Float = 0.0, handL: Hand = .RELAXED, handR: Hand = .RELAXED, layerL: ArmLayer = .MID, layerR: ArmLayer = .MID, lift: Float = 0.0, tilt: Float = 0.0, seat: Bool = false, crossed: Bool = false, fx: Set<PoseFx> = [], mod: FaceMod = .NONE, prop: PropSlot? = nil, propRight: Bool = true, footL: Float? = nil, footR: Float? = nil) {
        self.armL = armL
        self.armR = armR
        self.legL = legL
        self.legR = legR
        self.lean = lean
        self.headTilt = headTilt
        self.turn = turn
        self.handL = handL
        self.handR = handR
        self.layerL = layerL
        self.layerR = layerR
        self.lift = lift
        self.tilt = tilt
        self.seat = seat
        self.crossed = crossed
        self.fx = fx
        self.mod = mod
        self.prop = prop
        self.propRight = propRight
        self.footL = footL
        self.footR = footR
    }
}

// ------------------------------------------------------------------ yüz ifadesi

/** Göz biçimi. */
enum EyeShape: CaseIterable { case OPEN, WIDE, HAPPY, CLOSED, SQUINT, STAR, TINY, HALF }

/** Ağız biçimi. */
enum Mouth: CaseIterable { case SMILE, LINE, OPEN_SMILE, FROWN, O, WAVY, GRIT, SHOUT, SMIRK, LAUGH, WAIL, JAWDROP, OUCH, POUT }

/**
 * İfadenin çizim tarifi. Kaş: [browIn]/[browOut] iç ve dış ucun kayması (eksi yukarı, baş yarıçapı biriminde).
 * Göz kapağı: [lidIn]/[lidOut] üst kapağın iç/dış uçta inişi (0..1), [lowerLid] alt kapağın kalkması. [gaze] bakış
 * yönü (x, y; iris kayması), [iris] iris ölçeği.
 */
struct FaceSpec {
    var eye: EyeShape
    var browIn: Float
    var browOut: Float
    var lidIn: Float
    var lidOut: Float
    var lowerLid: Float
    var gazeX: Float
    var gazeY: Float
    var iris: Float
    var mouth: Mouth
    var blush: Bool
    var vein: Bool
    var sweat: Bool
    var gloom: Bool
    var tear: Bool
    var sparkle: Bool
    var browRaiseR: Float
    var eyeR: EyeShape?
    var streams: Bool
    var bandage: Bool
    init(eye: EyeShape = EyeShape.OPEN, browIn: Float = 0.0, browOut: Float = 0.0, lidIn: Float = 0.0, lidOut: Float = 0.0, lowerLid: Float = 0.0, gazeX: Float = 0.0, gazeY: Float = 0.0, iris: Float = 1.0, mouth: Mouth = Mouth.SMILE, blush: Bool = false, vein: Bool = false, sweat: Bool = false, gloom: Bool = false, tear: Bool = false, sparkle: Bool = false, browRaiseR: Float = 0.0, eyeR: EyeShape? = nil, streams: Bool = false, bandage: Bool = false) {
        self.eye = eye
        self.browIn = browIn
        self.browOut = browOut
        self.lidIn = lidIn
        self.lidOut = lidOut
        self.lowerLid = lowerLid
        self.gazeX = gazeX
        self.gazeY = gazeY
        self.iris = iris
        self.mouth = mouth
        self.blush = blush
        self.vein = vein
        self.sweat = sweat
        self.gloom = gloom
        self.tear = tear
        self.sparkle = sparkle
        self.browRaiseR = browRaiseR
        self.eyeR = eyeR
        self.streams = streams
        self.bandage = bandage
    }

    /** Pozun yüze etkisi: ağız ve gözler ifadenin üstüne yazılır (kaşlar ifadede kalır). */
    func with(_ mod: FaceMod) -> FaceSpec {
        let cheerful = mouth == Mouth.OPEN_SMILE || mouth == Mouth.LAUGH || mouth == Mouth.SMILE || eye == EyeShape.STAR
        var f = self
        switch mod {
        case .NONE: break
        case .SHOUT: f.mouth = cheerful ? .LAUGH : .SHOUT
        case .LAUGH:
            f.eye = (eye == .TINY || eye == .WIDE) ? eye : .HAPPY
            f.mouth = .LAUGH; f.tear = true; f.lidIn = 0; f.lidOut = 0
        case .CRY: f.streams = true; f.mouth = cheerful ? .OPEN_SMILE : .WAIL; f.tear = false
        case .EFFORT: f.mouth = (mouth == .SHOUT || mouth == .LAUGH) ? mouth : .GRIT; f.sweat = true
        case .FALL: f.eye = .SQUINT; f.eyeR = nil; f.mouth = .OUCH; f.sweat = true; f.sparkle = false
        case .HURT: f.eyeR = .SQUINT; f.bandage = true; f.sweat = true; f.mouth = cheerful ? .SMIRK : .GRIT
        case .WINK: f.eyeR = (eye == .OPEN || eye == .STAR || eye == .HALF) ? .HAPPY : eyeR
        }
        return f
    }
}

// ------------------------------------------------------------------ renk

enum CK {
    static let INK = "#1D1A26"
    static let WHITE = "#FFFFFF"
    static let EYE_WHITE = "#FCFCFF"
    static let PUPIL = "#16121F"
    static let LASH = "#221A28"
    static let MOUTH = "#6E1F2C"
    static let TONGUE = "#E8707E"
    static let TEETH = "#FFFFFF"
    static let BLUSH = "#F27A8C"
    static let TEAR = "#7FD0FF"
    static let STEEL = "#C9D2DC"
    static let STEEL_D = "#7E8896"
    static let SCREEN = "#16202E"
    static let GLOW = "#53E0FF"
    static let GOLD = "#F2C14E"
    static let GOLD_D = "#B7862A"
    static let RED = "#E0483C"
    static let ANGER = "#E23A3A"
    static let GLOOM = "#6E66B8"

    static func rgb(_ h: String) -> [Int] {
        let hex = h.hasPrefix("#") ? String(h.dropFirst()) : h
        let v = Int(hex.prefix(6), radix: 16) ?? 0
        return [v >> 16 & 255, v >> 8 & 255, v & 255]
    }

    static func mix(_ a: String, _ b: String, _ t: Float) -> String {
        let x = rgb(a); let y = rgb(b)
        func ch(_ i: Int) -> Int { (Float(x[i]) + Float(y[i] - x[i]) * t).toInt().coerceIn(0, 255) }
        return String(format: "#%02X%02X%02X", ch(0), ch(1), ch(2))
    }

    /** Cel gölge tonu (soğuk mor-lacivert karışım). */
    static func shade(_ hex: String) -> String { mix(hex, "#2A1F48", 0.3) }
    /** Daha koyu gölge (saçın alt katmanı, kıvrım). */
    static func deep(_ hex: String) -> String { mix(hex, "#1A1430", 0.5) }
    static func light(_ hex: String) -> String { mix(hex, "#FFFFFF", 0.38) }
    /** Ten gölgesi (sıcak, pembemsi). */
    static func skinShade(_ hex: String) -> String { mix(hex, "#B04A5C", 0.3) }
}

// ------------------------------------------------------------------ geometri

/** Açıdan birim yön: 0 aşağı, 90 sağ, 180 yukarı. */
func dirOf(_ deg: Float) -> P {
    let r = toRadians(deg.toDouble())
    return P(kSin(r).toFloat(), kCos(r).toFloat())
}

/** [dirOf] tersi. */
func angleOf(_ v: P) -> Float { toDegrees(kAtan2(v.x.toDouble(), v.y.toDouble())).toFloat() }

/** Vektörü ekranda saat yönünde [deg] derece döndürür ([com.karapp.drawguidance.content.vector.Affine.rotate] ile aynı). */
func rotV(_ v: P, _ deg: Float) -> P {
    let r = toRadians(deg.toDouble())
    let c = kCos(r).toFloat(); let s = kSin(r).toFloat()
    return P(c * v.x - s * v.y, s * v.x + c * v.y)
}

func dist(_ a: P, _ b: P) -> Float { kHypot(b.x - a.x, b.y - a.y) }
func lerpP(_ a: P, _ b: P, _ t: Float) -> P { P(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t) }

/** İki kemikli ters kinematik: omuzdan [t] hedefine; dirsek ve el (hedef erişilmezse kol düz uzanır). */
func ik(_ s: P, _ t: P, _ a: Float, _ b: Float, _ bend: Float) -> (P, P) {
    let d = t - s
    let len0 = max(1e-3, kHypot(d.x, d.y))
    let u = P(d.x / len0, d.y / len0)
    let len = len0.coerceIn(abs(a - b) + 0.5, a + b - 0.05)
    let cosA = ((a * a + len * len - b * b) / (2.0 * a * len)).coerceIn(-1.0, 1.0)
    let ang = toDegrees(kAcos(cosA).toDouble()).toFloat()
    let elbow = s + rotV(u, ang * kSign(bend)) * a
    return (elbow, s + u * len)
}

/**
 * Pozun eklem noktaları (tasarım biriminde, zemine oturtulmuş). Önce leğen (60, 0) alınır; gövde, baş ve uzuvlar
 * açılardan (ya da hedeften) bulunur; sonra bütün vücut [PoseSpec.tilt] kadar döndürüldüğünde en alttaki nokta
 * y = 196 − lift olacak biçimde kaydırılır.
 */
final class Skel {
    let body: Body
    let pose: PoseSpec
    let headAngle: Float
    /** Gövde dönüşü (baş tam döner, gövde daha az). */
    let bodyTurn: Float
    let widthK: Float
    let ground: Float

    let pelvis: P; let chest: P; let neckTop: P; let head: P
    let shoulderL: P; let shoulderR: P; let elbowL: P; let elbowR: P; let handL: P; let handR: P
    let hipL: P; let hipR: P; let kneeL: P; let kneeR: P; let ankleL: P; let ankleR: P

    init(_ body: Body, _ pose: PoseSpec) {
        self.body = body
        self.pose = pose
        let headAngle = pose.lean + pose.headTilt
        let bodyTurn = pose.turn * 0.6
        let widthK = 1 - 0.22 * abs(bodyTurn)
        let ground = 196 - pose.lift
        self.headAngle = headAngle; self.bodyTurn = bodyTurn; self.widthK = widthK; self.ground = ground
        let b = body
        let p0 = P(60.0, 0.0)
        let ch = p0 + rotV(P(bodyTurn * 2.0, -b.torsoLen), pose.lean)
        let sL = ch + rotV(P(-b.shoulderJ * widthK, 0.0), pose.lean)
        let sR = ch + rotV(P(b.shoulderJ * widthK, 0.0), pose.lean)
        let nt = ch + rotV(P(0.0, -b.neckLen), pose.lean)
        let hd = nt + rotV(P(0.0, -0.72 * b.headR), headAngle)
        let hipTilt = pose.lean * 0.3
        let hL = p0 + rotV(P(-b.hipJ * widthK, 2.0), hipTilt)
        let hR = p0 + rotV(P(b.hipJ * widthK, 2.0), hipTilt)
        let kL = hL + dirOf(pose.legL.thigh) * b.thigh
        let kR = hR + dirOf(pose.legR.thigh) * b.thigh
        let aL = kL + dirOf(pose.legL.shin) * b.shin
        let aR = kR + dirOf(pose.legR.shin) * b.shin

        // Kol hedefleri (ham koordinatlarda)
        func headPt(_ x: Float, _ y: Float) -> P { hd + rotV(P(x * b.headR, y * b.headR), headAngle) }
        func torsoPt(_ x: Float, _ y: Float) -> P { p0 + rotV(P(x, y), pose.lean) }
        func mark(_ m: Mark) -> P {
            switch m {
            case .CHIN: return headPt(pose.turn * 0.12, 1.12)
            case .MOUTH: return headPt(pose.turn * 0.15, 0.8)
            case .EYE_L: return headPt(-0.42 + pose.turn * 0.15, 0.22)
            case .EYE_R: return headPt(0.42 + pose.turn * 0.15, 0.22)
            case .BROW_R: return headPt(0.55, -0.2)
            case .HEAD_TOP: return headPt(0.0, -1.05)
            case .NAPE: return headPt(0.0, 0.2)
            case .CHEST: return torsoPt(0.0, -b.torsoLen * 0.62)
            case .BELLY: return torsoPt(0.0, -b.torsoLen * 0.3)
            case .HIP_L: return torsoPt(-b.waistW * widthK - 1.0, -b.torsoLen * 0.2)
            case .HIP_R: return torsoPt(b.waistW * widthK + 1.0, -b.torsoLen * 0.2)
            case .KNEE_L: return kL + P(0.0, -b.legR1 - 2.0)
            case .KNEE_R: return kR + P(0.0, -b.legR1 - 2.0)
            }
        }
        func arm(_ s: P, _ spec: ArmSpec) -> (P, P) {
            if let target = spec.reach {
                return ik(s, mark(target) + P(spec.dx, spec.dy), b.upperArm * spec.upperK, b.foreArm, spec.bend)
            }
            let e = s + dirOf(spec.upper) * (b.upperArm * spec.upperK)
            return (e, e + dirOf(spec.lower) * b.foreArm)
        }
        let (eL, wL) = arm(sL, pose.armL)
        let (eR, wR) = arm(sR, pose.armR)

        // Zemin: tabanlar, dizler (diz çökme) ve eller (yere dayanma); bütün vücut dönüşü hesaba katılır
        let sole = b.legR2 + 2.4
        let candidates = [
            aL + P(0.0, sole), aR + P(0.0, sole), kL + P(0.0, b.legR1), kR + P(0.0, b.legR1), p0 + P(0.0, b.legR0),
            wL + P(0.0, b.handR * 1.6), wR + P(0.0, b.handR * 1.6)
        ]
        var lowest = -Float.greatestFiniteMagnitude
        for c in candidates { lowest = max(lowest, (p0 + rotV(c - p0, pose.tilt)).y) }
        let sh = P(0.0, ground - lowest)
        pelvis = p0 + sh; chest = ch + sh; neckTop = nt + sh; head = hd + sh
        shoulderL = sL + sh; shoulderR = sR + sh; elbowL = eL + sh; elbowR = eR + sh; handL = wL + sh; handR = wR + sh
        hipL = hL + sh; hipR = hR + sh; kneeL = kL + sh; kneeR = kR + sh; ankleL = aL + sh; ankleR = aR + sh
    }
}
