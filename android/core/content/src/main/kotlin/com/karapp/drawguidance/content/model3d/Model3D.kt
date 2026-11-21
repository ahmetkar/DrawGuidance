package com.karapp.drawguidance.content.model3d

import kotlin.math.sqrt

/**
 * v23 3B modelin veri yapıları (saf Kotlin, Android'e bağımlı değil). Dünya koordinatında Y yukarı, X sağa, Z bakana
 * doğru; zemin y = 0 düzlemidir. Bir model parçalardan (küp, küre, silindir… ya da köşe/yüz düzenlemesiyle değişmiş
 * ağlar), 3B düzlemlere çizilmiş çizgilerden, sahne ayarlarından (ışık, gölge, kenar çizgisi) ve kameradan oluşur.
 */
data class V3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: V3) = V3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: V3) = V3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = V3(x * s, y * s, z * s)
    operator fun unaryMinus() = V3(-x, -y, -z)
    infix fun dot(o: V3) = x * o.x + y * o.y + z * o.z
    infix fun cross(o: V3) = V3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun mul(o: V3) = V3(x * o.x, y * o.y, z * o.z)
    val length: Float get() = sqrt(x * x + y * y + z * z)

    fun normalized(): V3 {
        val l = length
        return if (l < 1e-9f) this else V3(x / l, y / l, z / l)
    }

    fun get(axis: Int): Float = when (axis) { 0 -> x; 1 -> y; else -> z }

    fun with(axis: Int, v: Float): V3 = when (axis) { 0 -> copy(x = v); 1 -> copy(y = v); else -> copy(z = v) }

    companion object {
        val ZERO = V3(0f, 0f, 0f)
        val ONE = V3(1f, 1f, 1f)
        val UP = V3(0f, 1f, 0f)
    }
}

/** Hazır cisimler (parça eklerken seçilir; köşe/yüz düzenlemesinden sonra ağ serbestçe değişir). */
enum class PrimitiveKind {
    CUBE, SPHERE, CYLINDER, CONE, PYRAMID, PRISM3, PRISM6, TORUS, PLANE, DOME, CAPSULE, TUBE, WEDGE, STAR;

    /** Varsayılan olarak yumuşak gölgelenen (eğri yüzeyli) cisimler. */
    val smoothByDefault: Boolean get() = this == SPHERE || this == CYLINDER || this == CONE || this == TORUS ||
        this == DOME || this == CAPSULE || this == TUBE

    /** Doku eşlemesinin varsayılanı. */
    val uvByDefault: UvMode
        get() = when (this) {
            SPHERE, DOME, CAPSULE -> UvMode.SPHERE
            CYLINDER, CONE, TUBE, TORUS -> UvMode.CYLINDER
            else -> UvMode.BOX
        }
}

/**
 * Cismin üretim ayarları: [segments] çevredeki dilim sayısı, [rings] enlem/halka sayısı, [top] üst yarıçapın alt
 * yarıçapa oranı (kesik koni), [thickness] halka ve boru kalınlığı (0..1).
 */
data class PrimitiveSpec(
    val kind: PrimitiveKind,
    val segments: Int = 24,
    val rings: Int = 12,
    val top: Float = 1f,
    val thickness: Float = 0.3f,
)

/** Malzeme türü: ışığı nasıl yansıttığı. */
enum class MaterialKind { MATTE, PLASTIC, METAL, GLASS, GLOW }

/**
 * Yüzey: [color] ARGB (doku varsa onu tonlar; beyaz dokunun kendi rengi), [texture] doku kimliği, [textureScale] bir
 * dünya biriminde dokunun kaç kez tekrarlanacağının tersi (büyük = iri doku), [opacity] 0..1, [shine] parlaklık 0..1.
 */
data class Material(
    val color: Int = DEFAULT_COLOR,
    val texture: String? = null,
    val textureScale: Float = 1f,
    val opacity: Float = 1f,
    val kind: MaterialKind = MaterialKind.PLASTIC,
    val shine: Float = 0.5f,
    /**
     * v27 gradyan: ikinci renk (ARGB; null: tek renk). Renk [color]'dan buna parçanın kendi ekseni [gradientAxis]
     * (0 X, 1 Y, 2 Z) boyunca geçer; gradyanda doku kullanılmaz.
     */
    val gradient: Int? = null,
    val gradientAxis: Int = 1,
) {
    val transparent: Boolean get() = opacity < 0.995f || kind == MaterialKind.GLASS

    companion object {
        const val DEFAULT_COLOR = 0xFF4F8BD6.toInt()
    }
}

/**
 * Doku eşlemesi: kutu (yüzün baktığı eksene göre), silindir, küre ya da düzlem (X–Z); v24 [MESH] ağın kendi doku
 * koordinatları (içe aktarılan modellerde; ağda yoksa kutu).
 */
enum class UvMode { BOX, CYLINDER, SPHERE, PLANAR, MESH }

/**
 * Modelin bir parçası: ağ ([mesh], nesne uzayında), konum, döndürme (derece; X, sonra Y, sonra Z), ölçek ve malzeme.
 * [faceMaterials] yüz başına farklı malzeme (yüz boyama). [primitive] null ise ağ elle düzenlenmiştir (cisim ayarları
 * değiştirilince ağ yeniden üretilmez).
 */
data class Part(
    val id: String,
    val name: String,
    val mesh: Mesh,
    val primitive: PrimitiveSpec?,
    val position: V3 = V3.ZERO,
    val rotation: V3 = V3.ZERO,
    val scale: V3 = V3.ONE,
    val material: Material = Material(),
    val faceMaterials: Map<Int, Material> = emptyMap(),
    val smooth: Boolean = false,
    val uv: UvMode = UvMode.BOX,
    val visible: Boolean = true,
    /** v26 parçanın grubu ([Model3D.groups] kimliği): gruptaki parçalar birlikte seçilir ve taşınır. */
    val group: String? = null,
    /** v56 kilitli cisim (eski 3B Şekillerim kaydından çevrilen parça): taşınır, üstüne çizilir, ağı düzenlenmez. */
    val locked: Boolean = false,
    /** v56 3B katmanı ([Model3D.layers] kimliği; null: ilk katman). */
    val layer: String? = null,
) {
    fun materialOf(face: Int): Material = faceMaterials[face] ?: material
}

/** v26 parça grubu: kimlik ve ad (Parçalar panelinde başlık). */
data class Group3(val id: String, val name: String)

/**
 * 3B düzleme çizilmiş çizgi: dünya noktaları (x, y, z…), renk ve kalınlık (dünya birimi). v26 [time] çizildiği an
 * ("Son çizimi sil" için; silgiyle bölünen parçalar aynı anı taşır).
 */
class Stroke3(
    val id: String,
    val points: FloatArray,
    val color: Int,
    val width: Float,
    val time: Long = 0L,
    /** v56 nokta başına kalınlık çarpanı (kalem basıncı; 0..1+, null: düz kalınlık). Boyu [count] olur. */
    val widths: FloatArray? = null,
    /** v56 örtücülük 0..1. */
    val opacity: Float = 1f,
    /** v56 fırça türü ([Brush3D] sırası). */
    val brush: Int = 0,
    /** v56 çizildiği düzlemin ya da yüzün kimliği (bilgi; yapıştırmada kullanılır). */
    val plane: String? = null,
    /** v56 3B katmanı ([Model3D.layers] kimliği; null: ilk katman). */
    val layer: String? = null,
) {
    val count: Int get() = points.size / 3

    /** [i]. noktanın kalınlığı (dünya birimi). */
    fun widthAt(i: Int): Float = width * (widths?.getOrNull(i) ?: 1f)

    fun copy(
        points: FloatArray = this.points, color: Int = this.color, width: Float = this.width, widths: FloatArray? = this.widths,
        opacity: Float = this.opacity, layer: String? = this.layer, id: String = this.id,
    ) = Stroke3(id, points, color, width, time, widths, opacity, brush, plane, layer)

    override fun equals(other: Any?): Boolean =
        other is Stroke3 && id == other.id && color == other.color && width == other.width && points.contentEquals(other.points) &&
            (widths?.contentEquals(other.widths ?: FloatArray(0)) ?: (other.widths == null)) && opacity == other.opacity &&
            brush == other.brush && plane == other.plane && layer == other.layer

    override fun hashCode(): Int = (id.hashCode() * 31 + points.contentHashCode()) * 31 + color
}

/** v56 3B Çizim fırçaları (kalem, mürekkep, markör, geniş boya). */
enum class Brush3D { PEN, INK, MARKER, WIDE }

/** v56 3B katmanı: ad, görünür, kilitli. */
data class Layer3(val id: String, val name: String, val visible: Boolean = true, val locked: Boolean = false)

/**
 * v56 çizim düzlemi: merkez [origin], düzlem içindeki birim eksenler [u] (sağ) ve [v] (yukarı). Normal u × v'dir.
 * [id] yapıştırıldığı yüzün ya da hazır yönün adı (bilgi).
 */
data class Plane3(val origin: V3, val u: V3, val v: V3, val id: String = "front") {
    val normal: V3 get() = (u cross v).normalized()

    /** Dünya noktasının düzlemdeki (u, v) koordinatı. */
    fun local(p: V3): Pair<Float, Float> { val d = p - origin; return (d dot u) to (d dot v) }

    fun world(a: Float, b: Float): V3 = origin + u * a + v * b
}

/** Işık: yatay ve dikey açı (derece; ışığın geldiği yön), güç, ortam ışığı ve rengi. */
data class Light(
    val yaw: Float = 35f,
    val pitch: Float = 55f,
    val intensity: Float = 1f,
    val ambient: Float = 0.38f,
    val color: Int = 0xFFFFFFFF.toInt(),
) {
    /** Işığa doğru birim yön. */
    val direction: V3
        get() {
            val y = Math.toRadians(yaw.toDouble()); val p = Math.toRadians(pitch.toDouble())
            return V3((kotlin.math.cos(p) * kotlin.math.sin(y)).toFloat(), kotlin.math.sin(p).toFloat(), (kotlin.math.cos(p) * kotlin.math.cos(y)).toFloat())
        }
}

/**
 * Sahne ayarları: ışık, zemine düşen gölge, kenar çizgileri (rengiyle), arka plan rengi (0: saydam) ve düzenleyicide
 * zemin ızgarası.
 */
data class SceneSettings(
    val light: Light = Light(),
    val shadow: Boolean = true,
    val edges: Boolean = true,
    val edgeColor: Int = 0xFF263238.toInt(),
    val background: Int = 0,
    val ground: Boolean = true,
)

/**
 * Kamera: hedef noktanın çevresinde yatay dönüş ([yaw]) ve dikey eğim ([pitch], −89..89), perspektif (0 paralel … 1
 * güçlü) ve yakınlık.
 */
data class Camera3(
    val yaw: Float = 35f,
    val pitch: Float = 24f,
    val persp: Float = 0.35f,
    val zoom: Float = 1f,
    val target: V3 = V3(0f, 0.5f, 0f),
)

/** 3B model. */
data class Model3D(
    val id: String,
    val name: String,
    val parts: List<Part>,
    val strokes: List<Stroke3> = emptyList(),
    val scene: SceneSettings = SceneSettings(),
    val camera: Camera3 = Camera3(),
    /** v26 parça grupları. */
    val groups: List<Group3> = emptyList(),
    /** v56 3B katmanları (boşsa tek örtük katman). */
    val layers: List<Layer3> = emptyList(),
    /** v56 son kullanılan çizim düzlemi (null: ön düzlem). */
    val plane: Plane3? = null,
) {
    val isEmpty: Boolean get() = parts.none { it.visible } && strokes.isEmpty()

    /** v56 görünmez ya da kilitli katman. */
    fun layerHidden(id: String?): Boolean = layers.firstOrNull { it.id == (id ?: layers.firstOrNull()?.id) }?.visible == false
    fun layerLocked(id: String?): Boolean = layers.firstOrNull { it.id == (id ?: layers.firstOrNull()?.id) }?.locked == true

    /** v56 çizimde görünenler: gizli katmanlardaki parça ve çizgiler çıkarılır. */
    fun visibleLayers(): Model3D = if (layers.none { !it.visible }) this
        else copy(parts = parts.map { if (layerHidden(it.layer)) it.copy(visible = false) else it }, strokes = strokes.filterNot { layerHidden(it.layer) })

    fun part(id: String): Part? = parts.firstOrNull { it.id == id }

    fun replacePart(part: Part): Model3D = copy(parts = parts.map { if (it.id == part.id) part else it })

    /** Toplam üçgen sayısı (bilgi satırı için). */
    val triangleCount: Int get() = parts.sumOf { p -> p.mesh.faces.sumOf { (it.size - 2).coerceAtLeast(0) } }

    /** v24 görünür parçaların toplam üçgen sayısı. */
    val visibleTriangles: Int get() = parts.filter { it.visible }.sumOf { p -> p.mesh.faces.sumOf { (it.size - 2).coerceAtLeast(0) } }
}

/** Dünya kutusu: en küçük ve en büyük köşe. */
data class Box3(val min: V3, val max: V3) {
    val center: V3 get() = (min + max) * 0.5f
    val size: V3 get() = max - min
    val radius: Float get() = size.length * 0.5f

    fun union(o: Box3) = Box3(
        V3(kotlin.math.min(min.x, o.min.x), kotlin.math.min(min.y, o.min.y), kotlin.math.min(min.z, o.min.z)),
        V3(kotlin.math.max(max.x, o.max.x), kotlin.math.max(max.y, o.max.y), kotlin.math.max(max.z, o.max.z)),
    )
}
