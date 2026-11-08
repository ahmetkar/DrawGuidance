package com.karapp.drawguidance.content.comic

import com.karapp.drawguidance.content.library.Fit
import com.karapp.drawguidance.content.library.LibShape
import com.karapp.drawguidance.content.library.ShapeLibrary
import com.karapp.drawguidance.content.vector.VectorBuilder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

/** v36 karakter türleri (15). Adlar ve etiketler aramada kullanılır. v37: hepsi anime / çizgi roman stilinde yeniden çizildi. */
enum class CharKind(val id: String, val title: String, val tags: List<String>) {
    KID("kid", "Çocuk", listOf("çocuk", "küçük", "oğlan")),
    STUDENT("student", "Öğrenci", listOf("öğrenci", "okul", "genç", "kız")),
    OFFICE("office", "Ofis çalışanı", listOf("ofis", "iş", "takım elbise", "memur")),
    ELDER("elder", "Yaşlı", listOf("yaşlı", "dede", "büyükbaba")),
    TEACHER("teacher", "Öğretmen", listOf("öğretmen", "okul", "sınıf", "kadın")),
    DOCTOR("doctor", "Doktor", listOf("doktor", "hastane", "sağlık", "hekim")),
    POLICE("police", "Polis", listOf("polis", "güvenlik", "üniforma")),
    DETECTIVE("detective", "Dedektif", listOf("dedektif", "polisiye", "gizem", "şapka")),
    CHEF("chef", "Aşçı", listOf("aşçı", "şef", "mutfak", "yemek")),
    ATHLETE("athlete", "Sporcu", listOf("sporcu", "spor", "koşucu", "futbol")),
    HERO("hero", "Süper kahraman", listOf("kahraman", "pelerin", "maske", "süper")),
    ASTRONAUT("astronaut", "Astronot", listOf("astronot", "uzay", "kask")),
    ROBOT("robot", "Robot", listOf("robot", "makine", "bilim kurgu")),
    KNIGHT("knight", "Şövalye", listOf("şövalye", "zırh", "kale", "orta çağ")),
    WIZARD("wizard", "Büyücü", listOf("büyücü", "sihirbaz", "fantastik", "sakal")),
}

/** Yüz ifadeleri: v36'nın 8'i ve v37'de eklenen 5 (öfkeli bağırma, kararlı, alaycı, şok olmuş, heyecanlı). */
enum class CharExpression(val id: String, val title: String) {
    CALM("calm", "Sakin"),
    HAPPY("happy", "Mutlu"),
    SAD("sad", "Üzgün"),
    ANGRY("angry", "Kızgın"),
    SURPRISED("surprised", "Şaşkın"),
    SCARED("scared", "Korkmuş"),
    THINKING("thinking", "Düşünceli"),
    SHY("shy", "Utangaç"),
    FURIOUS("furious", "Öfkeli bağırma"),
    DETERMINED("determined", "Kararlı"),
    SMUG("smug", "Alaycı"),
    SHOCKED("shocked", "Şok olmuş"),
    EXCITED("excited", "Heyecanlı"),
}

/**
 * Pozlar: v36'nın 9'u (kimlikleri değişmez) ve v37'de eklenen 17 aksiyon/duygu pozu (saldırı, yumruk, tekme, dövüş
 * duruşu, kavga, bağırma, savunma, düşme, yaralı, zafer, ağlama, gülme, uçma, güç toplama, V işareti, sinme, selam).
 */
enum class CharPose(val id: String, val title: String) {
    STAND("stand", "Ayakta"),
    WALK("walk", "Yürüyor"),
    RUN("run", "Koşuyor"),
    SIT("sit", "Oturuyor"),
    WAVE("wave", "El sallıyor"),
    POINT("point", "İşaret ediyor"),
    CROSSED("crossed", "Kollar kavuşturmuş"),
    JUMP("jump", "Zıplıyor"),
    THINK("think", "Düşünüyor"),
    ATTACK("attack", "Saldırıyor"),
    PUNCH("punch", "Yumruk atıyor"),
    KICK("kick", "Tekme atıyor"),
    STANCE("stance", "Dövüş duruşu"),
    FIGHT("fight", "Kavga ediyor"),
    SHOUT("shout", "Bağırıyor"),
    BLOCK("block", "Savunuyor"),
    FALL("fall", "Yere düşüyor"),
    HURT("hurt", "Yaralı"),
    VICTORY("victory", "Zafer"),
    CRY("cry", "Ağlıyor"),
    LAUGH("laugh", "Gülüyor"),
    FLY("fly", "Uçuyor"),
    CHARGE("charge", "Güç topluyor"),
    VSIGN("vsign", "V işareti"),
    COWER("cower", "Korkup siniyor"),
    SALUTE("salute", "Selam veriyor"),
}

/**
 * Tek karakter: tür, ifade ve poz. Şekil kimliği `chr.<tür>.<ifade>.<poz>` (kayıtta `lib.` önekiyle saklanır;
 * değişmemeli). Çizim tasarım biriminde yapılır ve sıkı sınır kutusuna yerleştirilir; [bounds] tasarım birimindeki
 * kutu (eklemede bütün karakterler aynı ölçekte görünsün diye).
 */
class CharacterDef(val kind: CharKind, val expression: CharExpression, val pose: CharPose, val mirrored: Boolean = false) {
    val id: String get() = ComicCharacters.ID_PREFIX + "${kind.id}.${expression.id}.${pose.id}" + if (mirrored) ".m" else ""

    /** Aynadaki hali (sola bakan / sağa bakan). */
    fun mirror(): CharacterDef = ComicCharacters.def(kind, expression, pose, !mirrored)

    /** Tasarım birimindeki sınır kutusu: sol, üst, en, boy (aynalı halde de aynı kutu). */
    val bounds: FloatArray by lazy {
        if (mirrored) return@lazy ComicCharacters.def(kind, expression, pose).bounds
        val paths = VectorBuilder(0.5f).apply { drawRaw() }.paths
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var bt = -Float.MAX_VALUE
        for (p in paths) for (q in p.points) { l = min(l, q.x); t = min(t, q.y); r = max(r, q.x); bt = max(bt, q.y) }
        val pad = 2f
        floatArrayOf(l - pad, t - pad, r - l + pad * 2, bt - t + pad * 2)
    }

    internal fun VectorBuilder.drawRaw() {
        val p = ComicCharacters.pose(pose)
        Fig(this, ComicCharacters.look(kind), p, ComicCharacters.face(expression).with(p.mod)).draw()
    }

    fun shape(): LibShape {
        val (x, y, w, h) = bounds.let { listOf(it[0], it[1], it[2], it[3]) }
        return LibShape(id, kind.title, Fit.BOX, aspect = w / h, tags = kind.tags + expression.title.lowercase() + pose.title.lowercase()) {
            if (mirrored) withTransform(com.karapp.drawguidance.content.vector.Affine(a = -1f / w, d = 1f / h, e = (x + w) / w, f = -y / h)) { drawRaw() }
            else inBox(-x / w, -y / h, 1f / w, 1f / h) { drawRaw() }
        }
    }
}

/**
 * Karakter kütüphanesi (Karakterler penceresi): 15 tür × 13 ifade × 26 poz = 5070 özgün karakter. Kütüphane
 * listesinde ve sunucu kataloğunda yer almaz (kimlikten üretilir, [ShapeLibrary.find] bulur); pencerede arama ve
 * süzgeçle seçilir. Telif: bütün çizimler bu dosyadaki tablolarla ve CharacterKit/Fig/Head çizim takımıyla üretilir,
 * hiçbir hazır karaktere, logoya ya da markaya dayanmaz.
 */
object ComicCharacters {
    const val ID_PREFIX = "chr."

    /** Ayakta duran yetişkinin tasarım birimindeki boyu (eklemede ölçek). */
    const val STANDING_HEIGHT = 200f

    private val shapes = ConcurrentHashMap<String, LibShape>()
    private val defs = ConcurrentHashMap<String, CharacterDef>()

    val all: List<CharacterDef> by lazy {
        CharKind.entries.flatMap { k -> CharExpression.entries.flatMap { e -> CharPose.entries.map { p -> def(k, e, p) } } }
    }

    fun def(kind: CharKind, expression: CharExpression, pose: CharPose, mirrored: Boolean = false): CharacterDef =
        defs.getOrPut("${kind.id}.${expression.id}.${pose.id}" + if (mirrored) ".m" else "") { CharacterDef(kind, expression, pose, mirrored) }

    /** Kimlikten karakter ("lib." önekli ya da öneksiz; aynalı ".m" ekli); değilse null. */
    fun parse(id: String): CharacterDef? {
        val raw = id.removePrefix(ShapeLibrary.PREFIX)
        if (!raw.startsWith(ID_PREFIX)) return null
        val parts = raw.removePrefix(ID_PREFIX).split('.')
        if (parts.size != 3 && !(parts.size == 4 && parts[3] == "m")) return null
        val k = CharKind.entries.firstOrNull { it.id == parts[0] } ?: return null
        val e = CharExpression.entries.firstOrNull { it.id == parts[1] } ?: return null
        val p = CharPose.entries.firstOrNull { it.id == parts[2] } ?: return null
        return def(k, e, p, parts.size == 4)
    }

    /** Kütüphane şekli (önbellekli). */
    fun shape(id: String): LibShape? {
        val d = parse(id) ?: return null
        return shapes.getOrPut(d.id) { d.shape() }
    }

    /**
     * Arama ve süzgeç: [query] sözcüklerinin hepsi türün adında/etiketlerinde, ifadenin ya da pozun adında geçmeli
     * ([translate] adların seçili dildeki karşılığı; ikisi de aranır). Süzgeç null ise o eksen serbest.
     */
    fun search(
        query: String,
        kind: CharKind? = null,
        expression: CharExpression? = null,
        pose: CharPose? = null,
        translate: (String) -> String = { it },
    ): List<CharacterDef> {
        val tr = java.util.Locale.forLanguageTag("tr-TR")
        val words = query.lowercase(tr).split(' ', ',').map { it.trim() }.filter { it.isNotEmpty() }
        fun text(vararg s: String) = s.flatMap { listOf(it, translate(it)) }.joinToString(" ").lowercase(tr)
        val kinds = CharKind.entries.filter { kind == null || it == kind }
        val exprs = CharExpression.entries.filter { expression == null || it == expression }
        val poses = CharPose.entries.filter { pose == null || it == pose }
        val kindText = kinds.associateWith { text(it.title, *it.tags.toTypedArray()) }
        val exprText = exprs.associateWith { text(it.title) }
        val poseText = poses.associateWith { text(it.title) }
        val out = ArrayList<CharacterDef>()
        for (k in kinds) for (e in exprs) for (p in poses) {
            val all = kindText.getValue(k) + " " + exprText.getValue(e) + " " + poseText.getValue(p)
            if (words.all { it in all }) out += def(k, e, p)
        }
        return out
    }

    // ------------------------------------------------------------------ görünüşler (her tür ayrı tasarım)

    internal fun look(kind: CharKind): Look = looks.getValue(kind)

    private val looks: Map<CharKind, Look> by lazy {
        mapOf(
            // Diken saçlı, çilli, burnu yara bantlı enerjik çocuk: sarı tişört (mavi göğüs bandı, yıldız), kot şort
            CharKind.KID to Look(
                Body.CHILD, "#F5CBA7", Hair.SPIKY_KID, "#9A4421", "#D0711E", EyeStyle.KID, Outfit.TEE, "#F7C548", "#3D6FB6",
                Legwear.SHORTS, Shoe.SNEAKER, "#E0483C", setOf(Extra.BANDAID, Extra.FRECKLES), accent = "#2F6DB5", aura = "#FFD23F",
            ),
            // Yüksek at kuyruklu, denizci yakalı okul üniformalı genç kız
            CharKind.STUDENT to Look(
                Body.TEEN_F, "#F8D7C2", Hair.PONYTAIL, "#2E3159", "#5B6BD8", EyeStyle.FEMALE, Outfit.SAILOR, "#F4F5FA", "#26355E",
                Legwear.SKIRT, Shoe.LOAFER, "#4A2E20", setOf(Extra.RIBBON), accent = "#D8344A", props = mapOf(PropSlot.CALM to Prop.BAG), aura = "#FF9BC8",
            ),
            // Yandan ayrık saçlı, koyu takım elbiseli, evrak çantalı beyaz yakalı
            CharKind.OFFICE to Look(
                Body.ADULT_M, "#EDC29C", Hair.PARTED, "#1E1E28", "#6B4A32", EyeStyle.MALE, Outfit.SUIT, "#3E4452", "#363B48",
                Legwear.PANTS, Shoe.SHOE, "#1F1A1A", setOf(Extra.TIE), accent = "#C23A3A", trim = "#F4F6F8",
                props = mapOf(PropSlot.CALM to Prop.BRIEFCASE),
            ),
            // Kel tepeli, kabarık ak bıyıklı, yuvarlak gözlüklü, bastonlu sevimli dede
            CharKind.ELDER to Look(
                Body.ELDER, "#F0C8A8", Hair.TUFTS, "#ECECF2", "#6B5A4A", EyeStyle.ELDER, Outfit.CARDIGAN, "#8A6A4A", "#6B6B5A",
                Legwear.PANTS, Shoe.SHOE, "#5B3A2A", setOf(Extra.ROUND_GLASSES, Extra.MUSTACHE), trim = "#F1E6CF",
                props = mapOf(PropSlot.CALM to Prop.CANE, PropSlot.ACTION to Prop.CANE), aura = "#B6F09C",
            ),
            // Topuzlu, kırmızı çerçeveli gözlüklü, kitaplı öğretmen (işaret ederken çubuk)
            CharKind.TEACHER to Look(
                Body.ADULT_F, "#8D5A3B", Hair.BUN, "#1F1A24", "#5A3420", EyeStyle.FEMALE, Outfit.BLOUSE, "#2FA7A0", "#6E4FB8",
                Legwear.PENCIL, Shoe.LOAFER, "#3B2A2A", setOf(Extra.GLASSES), accent = "#B03A48", trim = "#F3EBDD",
                props = mapOf(PropSlot.CALM to Prop.BOOK, PropSlot.POINTER to Prop.POINTER), aura = "#9CF0E6",
            ),
            // Omuz boyu dalgalı sarı saçlı, önlüklü, steteskoplu doktor
            CharKind.DOCTOR to Look(
                Body.ADULT_F, "#F7D9C4", Hair.WAVY, "#E2B85C", "#3E9E6E", EyeStyle.FEMALE, Outfit.LABCOAT, "#F7F9FC", "#7FB7E0",
                Legwear.PANTS, Shoe.SNEAKER, "#5A7FB0", setOf(Extra.STETHOSCOPE, Extra.HAIRCLIP), accent = "#E0483C",
                props = mapOf(PropSlot.CALM to Prop.CLIPBOARD), aura = "#9CF0C0",
            ),
            // Kasketli, rozetli, geniş omuzlu polis (aksiyonda cop)
            CharKind.POLICE to Look(
                Body.ATHLETIC, "#B9784F", Hair.UNDER_CAP, "#1A1A22", "#3A6BA8", EyeStyle.COMIC, Outfit.UNIFORM, "#22305A", "#1E2640",
                Legwear.PANTS, Shoe.BOOT, "#16161C", setOf(Extra.POLICE_CAP, Extra.BADGE), accent = "#151C33",
                props = mapOf(PropSlot.ACTION to Prop.BATON), aura = "#7FB8FF",
            ),
            // Fötr şapkalı, trençkotlu, kırmızı atkılı, büyüteçli dedektif (alnına düşen kızıl perçem)
            CharKind.DETECTIVE to Look(
                Body.ADULT_M, "#EFC6A2", Hair.FORELOCK, "#8A3B22", "#B06A1E", EyeStyle.MALE, Outfit.TRENCH, "#C8A97A", "#4A4A52",
                Legwear.PANTS, Shoe.SHOE, "#3B2A20", setOf(Extra.FEDORA, Extra.SCARF), accent = "#8E2B2B",
                props = mapOf(PropSlot.CALM to Prop.MAGNIFIER), aura = "#FFB36B",
            ),
            // Uzun şef külahlı, burma bıyıklı, önlüklü aşçı (sakinken kepçe, aksiyonda tava)
            CharKind.CHEF to Look(
                Body.ADULT_M, "#D9A07A", Hair.CROPPED, "#3B2A20", "#4A3020", EyeStyle.MALE, Outfit.CHEF, "#F7F7F2", "#59606C",
                Legwear.PANTS, Shoe.SHOE, "#22222A", setOf(Extra.CHEF_HAT, Extra.NECKERCHIEF, Extra.CHEF_MUSTACHE, Extra.APRON), accent = "#E0483C",
                props = mapOf(PropSlot.CALM to Prop.LADLE, PropSlot.ACTION to Prop.PAN), aura = "#FFB36B",
            ),
            // Yukarı diken saçlı, alın bantlı, kaslı sporcu: kırmızı forma (7), bileklik, şort, uzun çorap
            CharKind.ATHLETE to Look(
                Body.ATHLETIC, "#7A4A30", Hair.SPIKY_UP, "#16161C", "#E0A030", EyeStyle.MALE, Outfit.JERSEY, "#E0483C", "#F4F4F4",
                Legwear.SHORTS, Shoe.SNEAKER, "#2F6DB5", setOf(Extra.HEADBAND, Extra.WRISTBANDS), accent = "#2F6DB5",
                props = mapOf(PropSlot.CALM to Prop.BALL), aura = "#FFD23F",
            ),
            // Çizgi roman kahramanı (özgün): camgöbeği tulum, lacivert yan panel ve maske, altın eldiven/çizme/kemer,
            // kızıl pelerin, göğüste eşkenar dörtgen içinde çift köşeli ok arması
            CharKind.HERO to Look(
                Body.HEROIC, "#F2C7A0", Hair.SWEPT_BACK, "#1D1D24", "#2FB5A8", EyeStyle.COMIC, Outfit.HEROSUIT, "#16807A", "#1B2238",
                Legwear.TIGHTS, Shoe.HERO_BOOT, "#F2B533", setOf(Extra.CAPE, Extra.MASK), accent = "#C8323C", trim = "#F2B533", aura = "#FFD23F",
            ),
            // Cam kasklı, küt saçlı kadın astronot: beyaz uzay giysisi, turuncu şeritler, sırt ünitesi
            CharKind.ASTRONAUT to Look(
                Body.ADULT_F, "#C68E66", Hair.BOB, "#3A2A22", "#8A5A2A", EyeStyle.FEMALE, Outfit.SPACESUIT, "#EEF1F5", "#E3E7EC",
                Legwear.PANTS, Shoe.SPACE, "#C9D1DA", setOf(Extra.SPACE_HELMET, Extra.BACKPACK), accent = "#F28A2E", aura = "#9FE4FF",
            ),
            // Mekanik robot: açık gri zırh plakaları, turuncu omuzluklar, parlayan göğüs çekirdeği ve ekran yüz
            CharKind.ROBOT to Look(
                Body.MECHA, "#D5DCE4", Hair.NONE, "#000000", "#53E0FF", EyeStyle.SCREEN, Outfit.MECHA, "#D5DCE4", "#5B6573",
                Legwear.MECHA, Shoe.MECHA, "#5B6573", accent = "#F28A2E", aura = "#53E0FF",
            ),
            // Sarı saçlı genç şövalye: gümüş zırh, omuzluklar, kırmızı armalı önlük, kılıç
            CharKind.KNIGHT to Look(
                Body.ATHLETIC, "#F3CFAE", Hair.KNIGHT, "#E8C766", "#3B7BD8", EyeStyle.MALE, Outfit.ARMOR, "#C9D2DC", "#4A4E5A",
                Legwear.PANTS, Shoe.SABATON, "#B8C2CE", accent = "#A92E28", trim = "#F2C14E",
                props = mapOf(PropSlot.CALM to Prop.SWORD, PropSlot.ACTION to Prop.SWORD), aura = "#FFE07A",
            ),
            // Uzun ak saçlı ve sakallı büyücü: mor cüppe, altın şeritler, kıvrık uçlu yıldızlı şapka, kristal asa
            CharKind.WIZARD to Look(
                Body.SAGE, "#EBC5A5", Hair.LONG, "#ECECF2", "#5B7BD8", EyeStyle.ELDER, Outfit.ROBE, "#5B3E99", "#3B2A55",
                Legwear.ROBE, Shoe.SHOE, "#5A3A22", setOf(Extra.WIZARD_HAT, Extra.BEARD), trim = "#F2C14E",
                props = mapOf(PropSlot.CALM to Prop.STAFF, PropSlot.ACTION to Prop.STAFF), aura = "#B57CFF",
            ),
        )
    }

    // ------------------------------------------------------------------ ifadeler

    internal fun face(e: CharExpression): FaceSpec = faces.getValue(e)

    private val faces: Map<CharExpression, FaceSpec> by lazy {
        mapOf(
            CharExpression.CALM to FaceSpec(mouth = Mouth.SMILE),
            CharExpression.HAPPY to FaceSpec(browIn = -0.06f, browOut = -0.03f, lowerLid = 0.22f, mouth = Mouth.OPEN_SMILE, blush = true),
            CharExpression.SAD to FaceSpec(browIn = -0.15f, browOut = 0.07f, lidOut = 0.25f, gazeY = 0.6f, mouth = Mouth.FROWN, tear = true),
            CharExpression.ANGRY to FaceSpec(browIn = 0.15f, browOut = -0.07f, lidIn = 0.55f, lidOut = 0.1f, iris = 0.88f, mouth = Mouth.GRIT, vein = true),
            CharExpression.SURPRISED to FaceSpec(eye = EyeShape.WIDE, browIn = -0.17f, browOut = -0.12f, mouth = Mouth.O),
            CharExpression.SCARED to FaceSpec(eye = EyeShape.WIDE, iris = 0.62f, browIn = -0.15f, browOut = 0.06f, mouth = Mouth.WAVY, sweat = true, gloom = true),
            CharExpression.THINKING to FaceSpec(gazeX = 0.7f, gazeY = -0.6f, browRaiseR = 0.09f, browIn = 0.03f, lidIn = 0.15f, lidOut = 0.1f, mouth = Mouth.LINE),
            CharExpression.SHY to FaceSpec(gazeX = -0.6f, gazeY = 0.5f, lidIn = 0.25f, lidOut = 0.25f, browIn = -0.1f, browOut = 0.03f, mouth = Mouth.POUT, blush = true),
            CharExpression.FURIOUS to FaceSpec(browIn = 0.2f, browOut = -0.1f, lidIn = 0.6f, lidOut = 0.15f, iris = 0.62f, mouth = Mouth.SHOUT, vein = true),
            CharExpression.DETERMINED to FaceSpec(browIn = 0.1f, browOut = -0.04f, lidIn = 0.3f, lidOut = 0.12f, mouth = Mouth.LINE, sparkle = true),
            CharExpression.SMUG to FaceSpec(eye = EyeShape.HALF, gazeX = -0.5f, browRaiseR = 0.1f, browIn = 0.05f, mouth = Mouth.SMIRK),
            CharExpression.SHOCKED to FaceSpec(eye = EyeShape.TINY, browIn = -0.19f, browOut = -0.1f, mouth = Mouth.JAWDROP, gloom = true, sweat = true),
            CharExpression.EXCITED to FaceSpec(eye = EyeShape.STAR, browIn = -0.08f, browOut = -0.06f, mouth = Mouth.OPEN_SMILE, blush = true),
        )
    }

    // ------------------------------------------------------------------ pozlar (açı tabloları; her vücut tipinde çalışır)

    internal fun pose(p: CharPose): PoseSpec = poses.getValue(p)

    private fun arm(upper: Float, lower: Float, k: Float = 1f) = ArmSpec(upper, lower, upperK = k)
    private fun reach(m: Mark, dx: Float = 0f, dy: Float = 0f, bend: Float = 1f, k: Float = 1f) = ArmSpec(0f, 0f, m, dx, dy, bend, k)
    private fun leg(thigh: Float, shin: Float) = LegSpec(thigh, shin)

    private val poses: Map<CharPose, PoseSpec> by lazy {
        mapOf(
            CharPose.STAND to PoseSpec(prop = PropSlot.CALM),
            CharPose.WALK to PoseSpec(
                armL = arm(-20f, -10f), armR = arm(22f, 40f), legL = leg(18f, 6f), legR = leg(-14f, -40f),
                lean = 3f, turn = 0.35f, prop = PropSlot.CALM, fx = setOf(PoseFx.DUST),
            ),
            CharPose.RUN to PoseSpec(
                armL = arm(-55f, 15f), armR = arm(55f, 150f), legL = leg(62f, -8f), legR = leg(-28f, -78f),
                lean = 14f, headTilt = -6f, turn = 0.6f, handL = Hand.FIST, handR = Hand.FIST, layerR = Layer.BACK,
                fx = setOf(PoseFx.SPEED, PoseFx.DUST),
            ),
            CharPose.SIT to PoseSpec(
                armL = reach(Mark.KNEE_L, -2f, 0f, 1f), armR = reach(Mark.KNEE_R, 0f, 0f, 1f), legL = leg(80f, 4f), legR = leg(86f, -4f),
                lean = -2f, turn = 0.45f, seat = true,
            ),
            CharPose.WAVE to PoseSpec(
                armR = arm(150f, 172f), handR = Hand.OPEN, headTilt = 6f, fx = setOf(PoseFx.WAVE_ARCS), prop = PropSlot.CALM, propRight = false,
            ),
            CharPose.POINT to PoseSpec(
                armL = reach(Mark.HIP_L, -1f, 0f, 1f), armR = arm(95f, 92f), handL = Hand.FIST, handR = Hand.POINT,
                lean = 2f, turn = 0.4f, legL = leg(-8f, -3f), legR = leg(10f, 4f), fx = setOf(PoseFx.POINT_LINES), prop = PropSlot.POINTER,
            ),
            CharPose.CROSSED to PoseSpec(
                armL = arm(8f, 84f), armR = arm(-8f, -96f), handL = Hand.HIDDEN, handR = Hand.FIST, crossed = true,
                headTilt = -4f, legL = leg(-6f, -3f), legR = leg(6f, 3f),
            ),
            CharPose.JUMP to PoseSpec(
                armL = arm(-138f, -158f), armR = arm(138f, 158f), handL = Hand.OPEN, handR = Hand.OPEN,
                legL = leg(-40f, 12f), legR = leg(40f, -12f), lift = 24f, headTilt = -4f, fx = setOf(PoseFx.JUMP_LINES),
            ),
            CharPose.THINK to PoseSpec(
                armL = arm(10f, 82f), armR = reach(Mark.CHIN, 0f, 3f, -1f, 0.62f), handL = Hand.FIST, handR = Hand.FIST, layerR = Layer.TOP,
                headTilt = -5f, fx = setOf(PoseFx.THOUGHT),
            ),
            CharPose.ATTACK to PoseSpec(
                armL = arm(80f, 95f), armR = arm(178f, -150f), handL = Hand.CLAW, handR = Hand.FIST,
                legL = leg(62f, 8f), legR = leg(-48f, -62f), lean = 16f, turn = 0.55f, headTilt = -6f,
                fx = setOf(PoseFx.SPEED, PoseFx.DUST, PoseFx.SWISH_ARM), prop = PropSlot.ACTION,
            ),
            CharPose.PUNCH to PoseSpec(
                armL = arm(-20f, 160f), armR = arm(90f, 90f), handL = Hand.FIST, handR = Hand.FIST, layerR = Layer.BACK,
                legL = leg(40f, 6f), legR = leg(-32f, -24f), lean = 8f, turn = 0.5f, headTilt = -4f, fx = setOf(PoseFx.IMPACT_HAND),
            ),
            CharPose.KICK to PoseSpec(
                armL = arm(-105f, -75f), armR = arm(20f, 150f), handL = Hand.FIST, handR = Hand.FIST,
                legL = leg(-6f, 0f), legR = leg(98f, 92f), footR = 92f, lean = -18f, headTilt = 8f, turn = 0.5f,
                fx = setOf(PoseFx.IMPACT_FOOT, PoseFx.SWISH_KICK),
            ),
            CharPose.STANCE to PoseSpec(
                armL = arm(-25f, 165f), armR = arm(35f, 155f), handL = Hand.FIST, handR = Hand.FIST,
                legL = leg(-32f, 6f), legR = leg(36f, -8f), lean = 4f, turn = 0.3f, headTilt = -3f, prop = PropSlot.ACTION,
            ),
            CharPose.FIGHT to PoseSpec(
                armL = arm(-70f, -160f), armR = arm(70f, 88f), handL = Hand.FIST, handR = Hand.CLAW, layerR = Layer.BACK,
                legL = leg(38f, 10f), legR = leg(-30f, -12f), lean = 14f, turn = 0.45f, mod = FaceMod.EFFORT,
                fx = setOf(PoseFx.BRAWL, PoseFx.DUST, PoseFx.SWEAT),
            ),
            CharPose.SHOUT to PoseSpec(
                armL = arm(-28f, -18f), armR = arm(28f, 18f), handL = Hand.FIST, handR = Hand.FIST,
                legL = leg(-16f, -4f), legR = leg(16f, 4f), lean = 3f, headTilt = -8f, mod = FaceMod.SHOUT, fx = setOf(PoseFx.SHOUT),
            ),
            CharPose.BLOCK to PoseSpec(
                armL = reach(Mark.CHIN, 9f, 7f, 1f, 0.7f), armR = reach(Mark.CHIN, -9f, 7f, -1f, 0.7f), handL = Hand.FIST, handR = Hand.FIST,
                layerL = Layer.TOP, layerR = Layer.TOP, legL = leg(-24f, -6f), legR = leg(18f, 12f), lean = -6f, turn = 0.15f,
                fx = setOf(PoseFx.BLOCK_ARCS, PoseFx.SWEAT),
            ),
            CharPose.FALL to PoseSpec(
                armL = arm(-140f, -110f), armR = arm(150f, 120f), handL = Hand.OPEN, handR = Hand.OPEN,
                legL = leg(-10f, 30f), legR = leg(60f, 40f), tilt = -40f, lift = 14f, headTilt = -10f, mod = FaceMod.FALL,
                fx = setOf(PoseFx.FALL_LINES),
            ),
            CharPose.HURT to PoseSpec(
                armL = reach(Mark.KNEE_L, 0f, -2f, 1f), armR = reach(Mark.CHEST, -14f, -6f, -1f, 0.8f), handR = Hand.OPEN,
                legL = leg(-82f, -25f), legR = leg(0f, 92f), lean = 6f, headTilt = 10f, turn = -0.25f, mod = FaceMod.HURT,
                fx = setOf(PoseFx.PAIN),
            ),
            CharPose.VICTORY to PoseSpec(
                armL = reach(Mark.HIP_L, -1f, 0f, 1f), armR = arm(150f, 160f), handL = Hand.FIST, handR = Hand.FIST,
                legL = leg(-12f, -6f), legR = leg(12f, 5f), lean = -2f, headTilt = -8f, fx = setOf(PoseFx.SPARKLE), prop = PropSlot.ACTION,
            ),
            CharPose.CRY to PoseSpec(
                armL = reach(Mark.EYE_L, -3f, 11f, 1f, 0.65f), armR = reach(Mark.EYE_R, 3f, 11f, -1f, 0.65f), handL = Hand.FIST, handR = Hand.FIST,
                layerL = Layer.TOP, layerR = Layer.TOP, legL = leg(6f, -6f), legR = leg(-6f, 6f), headTilt = 4f, mod = FaceMod.CRY,
                fx = setOf(PoseFx.SOB),
            ),
            CharPose.LAUGH to PoseSpec(
                armL = arm(-120f, -160f), armR = reach(Mark.BELLY, 6f, 0f, -1f), handL = Hand.OPEN, handR = Hand.OPEN,
                legL = leg(-6f, -2f), legR = leg(8f, 4f), lean = -6f, headTilt = -10f, mod = FaceMod.LAUGH, fx = setOf(PoseFx.LAUGH_MARKS),
            ),
            CharPose.FLY to PoseSpec(
                armL = arm(-18f, -12f), armR = arm(160f, 160f), handL = Hand.FIST, handR = Hand.FIST,
                legL = leg(-6f, -14f), legR = leg(4f, -24f), footL = -14f, footR = -24f, lift = 30f, tilt = 28f, fx = setOf(PoseFx.WIND),
            ),
            CharPose.CHARGE to PoseSpec(
                armL = reach(Mark.HIP_L, -2f, 4f, 1f), armR = reach(Mark.HIP_R, 2f, 4f, -1f), handL = Hand.FIST, handR = Hand.FIST,
                legL = leg(-50f, 0f), legR = leg(50f, 0f), headTilt = 6f, mod = FaceMod.EFFORT, fx = setOf(PoseFx.AURA),
            ),
            CharPose.VSIGN to PoseSpec(
                armL = reach(Mark.HIP_L, -1f, 0f, 1f), armR = reach(Mark.EYE_R, 10f, 2f, -1f, 0.75f), handL = Hand.FIST, handR = Hand.VSIGN,
                layerR = Layer.TOP, legL = leg(-3f, -1f), legR = leg(-2f, 18f), headTilt = 8f, turn = -0.1f, mod = FaceMod.WINK,
                fx = setOf(PoseFx.SPARKLE),
            ),
            CharPose.COWER to PoseSpec(
                armL = reach(Mark.HEAD_TOP, -8f, 2f, 1f), armR = reach(Mark.HEAD_TOP, 8f, 2f, -1f), handL = Hand.FLAT, handR = Hand.FLAT,
                layerL = Layer.TOP, layerR = Layer.TOP, legL = leg(-60f, 20f), legR = leg(60f, -20f), lean = 6f, headTilt = 10f,
                fx = setOf(PoseFx.TREMBLE, PoseFx.SWEAT),
            ),
            CharPose.SALUTE to PoseSpec(
                armL = arm(-4f, -2f), armR = reach(Mark.BROW_R, 2f, -1f, -1f, 0.85f), handL = Hand.FLAT, handR = Hand.FLAT,
                layerR = Layer.TOP, legL = leg(-2f, -1f), legR = leg(2f, 1f),
            ),
        )
    }
}
