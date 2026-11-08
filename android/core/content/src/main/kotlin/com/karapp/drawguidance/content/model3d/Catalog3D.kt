package com.karapp.drawguidance.content.model3d

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Hazır 3B şekil: kimlik, ad, kategori, kısa açıklama ve modeli kuran işlev. */
class CatalogItem(val id: String, val name: String, val category: String, val description: String, val build: () -> Model3D)

/**
 * v24 hazır 3B şekiller: çizim çalışmalarında ve lise/üniversite derslerinde kullanılacak az ayrıntılı modeller
 * (geometri, fizik, kimya, biyoloji, astronomi, çizim). Modeller program içinde kurulur (dosya gerektirmez), seçilince
 * kopyası düzenlenir ya da tuvale eklenir.
 */
object Catalog3D {

    const val GEOMETRY = "Geometri"
    const val PHYSICS = "Fizik"
    const val CHEMISTRY = "Kimya"
    const val BIOLOGY = "Biyoloji"
    const val ASTRONOMY = "Astronomi"
    const val DRAWING = "Çizim"

    val categories = listOf(GEOMETRY, PHYSICS, CHEMISTRY, BIOLOGY, ASTRONOMY, DRAWING)

    /** Katalog kimliği öneki (düzenleyiciye "catalog:<kimlik>" ile açılır). */
    const val PREFIX = "catalog:"

    fun byId(id: String): CatalogItem? = all.firstOrNull { it.id == id.removePrefix(PREFIX) }

    // ------------------------------------------------------------------------------------------ renkler
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val PAPER = 0xFFF5F5F2.toInt()
    private const val GLASS = 0xFF9FD3F2.toInt()
    private const val RED = 0xFFE53935.toInt()
    private const val GREEN = 0xFF43A047.toInt()
    private const val BLUE = 0xFF1E88E5.toInt()
    private const val ORANGE = 0xFFF28C28.toInt()
    private const val YELLOW = 0xFFFDD835.toInt()
    private const val PURPLE = 0xFF8E24AA.toInt()
    private const val GRAY = 0xFF90A4AE.toInt()
    private const val DARK = 0xFF37474F.toInt()
    private const val TEAL = 0xFF26A69A.toInt()
    private const val BROWN = 0xFF8D6E63.toInt()
    private const val INK = 0xFF263238.toInt()

    // ------------------------------------------------------------------------------------------ kurucu

    private class B(val id: String) {
        val parts = ArrayList<Part>()
        val strokes = ArrayList<Stroke3>()

        /** Dünya koordinatındaki ağdan parça (konumu taban merkezi). */
        fun mesh(
            name: String, mesh: Mesh, color: Int, kind: MaterialKind = MaterialKind.PLASTIC, smooth: Boolean = false,
            opacity: Float = 1f, shine: Float = 0.4f, texture: String? = null, texScale: Float = 1f, uv: UvMode = UvMode.BOX,
        ) {
            if (mesh.faceCount == 0) return
            val b = mesh.bounds
            val origin = V3(b.center.x, b.min.y, b.center.z)
            parts += Part(
                id = "$id-${parts.size}", name = name, mesh = mesh.mapPositions { it - origin }, primitive = null, position = origin,
                material = Material(color, texture, texScale, opacity, kind, shine), smooth = smooth, uv = uv,
            )
        }

        /** Hazır cisim (ayarları sonradan değiştirilebilir). */
        fun prim(
            kind: PrimitiveKind, name: String, pos: V3, scale: V3, color: Int, rot: V3 = V3.ZERO,
            kindM: MaterialKind = MaterialKind.PLASTIC, opacity: Float = 1f, shine: Float = 0.4f, texture: String? = null, texScale: Float = 1f,
            spec: PrimitiveSpec = Primitives.defaultSpec(kind),
        ) {
            parts += Part(
                id = "$id-${parts.size}", name = name, mesh = Primitives.build(spec), primitive = spec, position = pos, rotation = rot, scale = scale,
                material = Material(color, texture, texScale, opacity, kindM, shine), smooth = kind.smoothByDefault, uv = kind.uvByDefault,
            )
        }

        fun line(points: List<V3>, color: Int = INK, width: Float = 0.015f) {
            if (points.size < 2) return
            val arr = FloatArray(points.size * 3)
            points.forEachIndexed { i, p -> arr[3 * i] = p.x; arr[3 * i + 1] = p.y; arr[3 * i + 2] = p.z }
            strokes += Stroke3("$id-s${strokes.size}", arr, color, width)
        }

        /** Kesikli çizgi. */
        fun dashed(a: V3, b: V3, color: Int = INK, width: Float = 0.012f, dashes: Int = 8) {
            for (i in 0 until dashes) {
                val t0 = i / dashes.toFloat(); val t1 = (i + 0.55f) / dashes
                line(listOf(a + (b - a) * t0, a + (b - a) * t1), color, width)
            }
        }
    }

    private fun model(id: String, name: String, yaw: Float = 35f, pitch: Float = 22f, persp: Float = 0.35f, build: B.() -> Unit): Model3D {
        val b = B(id).apply(build)
        val tmp = Model3D(id, name, b.parts, b.strokes)
        val box = ModelEdit.modelBounds(tmp) ?: Box3(V3.ZERO, V3.ONE)
        val zoom = (View3.REFERENCE_EXTENT / (box.radius * 1.12f).coerceAtLeast(0.05f)).coerceIn(0.1f, 30f)
        return tmp.copy(camera = Camera3(yaw = yaw, pitch = pitch, persp = persp, zoom = zoom, target = box.center))
    }

    private fun item(id: String, name: String, category: String, description: String, yaw: Float = 35f, pitch: Float = 22f, build: B.() -> Unit) =
        CatalogItem(id, name, category, description) { model(id, name, yaw, pitch, build = build) }

    private val G = Generators3D

    // ------------------------------------------------------------------------------------------ katalog

    val all: List<CatalogItem> by lazy { geometry() + physics() + chemistry() + biology() + astronomy() + drawing() }

    private fun geometry(): List<CatalogItem> = listOf(
        item("geo.cube_diag", "Küp ve köşegenleri", GEOMETRY, "Yüz köşegeni (kırmızı) ve cisim köşegeni (mavi)") {
            prim(PrimitiveKind.CUBE, "Küp", V3(0f, 0.5f, 0f), V3.ONE, GLASS, kindM = MaterialKind.GLASS, opacity = 0.55f)
            line(listOf(V3(-0.5f, 0f, 0.5f), V3(0.5f, 1f, 0.5f)), RED, 0.018f)
            line(listOf(V3(-0.5f, 0f, -0.5f), V3(0.5f, 1f, 0.5f)), BLUE, 0.02f)
        },
        item("geo.cuboid", "Dikdörtgenler prizması", GEOMETRY, "a × b × c boyutlu prizma") {
            prim(PrimitiveKind.CUBE, "Prizma", V3(0f, 0.4f, 0f), V3(1.4f, 0.8f, 0.7f), ORANGE)
        },
        item("geo.pyramid", "Kare piramit", GEOMETRY, "Tabanı kare, yüksekliği gösterilmiş piramit") {
            prim(PrimitiveKind.PYRAMID, "Piramit", V3(0f, 0.5f, 0f), V3.ONE, YELLOW, kindM = MaterialKind.GLASS, opacity = 0.6f)
            dashed(V3(0f, 0f, 0f), V3(0f, 1f, 0f), RED)
        },
        item("geo.tetra", "Düzgün dörtyüzlü", GEOMETRY, "Dört eşkenar üçgen yüz (tetrahedron)") {
            mesh("Dörtyüzlü", G.tetrahedron(0.6f), TEAL)
        },
        item("geo.octa", "Düzgün sekizyüzlü", GEOMETRY, "Sekiz eşkenar üçgen yüz (oktahedron)") {
            mesh("Sekizyüzlü", G.octahedron(0.6f), PURPLE)
        },
        item("geo.dodeca", "Düzgün onikiyüzlü", GEOMETRY, "On iki düzgün beşgen yüz (dodekahedron)") {
            mesh("Onikiyüzlü", G.dodecahedron(0.6f), BLUE)
        },
        item("geo.icosa", "Düzgün yirmiyüzlü", GEOMETRY, "Yirmi eşkenar üçgen yüz (ikosahedron)") {
            mesh("Yirmiyüzlü", G.icosahedron(0.6f), GREEN)
        },
        item("geo.frustum", "Kesik koni", GEOMETRY, "Alt ve üst tabanı farklı yarıçaplı") {
            prim(PrimitiveKind.CYLINDER, "Kesik koni", V3(0f, 0.45f, 0f), V3(1f, 0.9f, 1f), ORANGE, spec = PrimitiveSpec(PrimitiveKind.CYLINDER, 24, 1, top = 0.5f))
        },
        item("geo.frustum_pyr", "Kesik piramit", GEOMETRY, "Kare tabanlı kesik piramit") {
            prim(PrimitiveKind.PYRAMID, "Kesik piramit", V3(0f, 0.4f, 0f), V3(1f, 0.8f, 1f), TEAL, spec = PrimitiveSpec(PrimitiveKind.PYRAMID, 4, 1, top = 0.5f))
        },
        item("geo.cylinder", "Silindir (r ve h)", GEOMETRY, "Yarıçap ve yükseklik çizgileriyle") {
            prim(PrimitiveKind.CYLINDER, "Silindir", V3(0f, 0.6f, 0f), V3(1f, 1.2f, 1f), GLASS, kindM = MaterialKind.GLASS, opacity = 0.55f)
            line(listOf(V3(0f, 1.2f, 0f), V3(0.5f, 1.2f, 0f)), RED)
            dashed(V3(0f, 0f, 0f), V3(0f, 1.2f, 0f), BLUE)
        },
        item("geo.cone", "Koni (h ve ana doğru)", GEOMETRY, "Yükseklik, yarıçap ve ana doğru") {
            prim(PrimitiveKind.CONE, "Koni", V3(0f, 0.6f, 0f), V3(1f, 1.2f, 1f), GLASS, kindM = MaterialKind.GLASS, opacity = 0.55f)
            dashed(V3(0f, 0f, 0f), V3(0f, 1.2f, 0f), BLUE)
            line(listOf(V3(0f, 0f, 0f), V3(0.5f, 0f, 0f)), RED)
            line(listOf(V3(0.5f, 0f, 0f), V3(0f, 1.2f, 0f)), GREEN)
        },
        item("geo.sphere", "Küre (r ve büyük çember)", GEOMETRY, "Merkez, yarıçap ve ekvator") {
            prim(PrimitiveKind.SPHERE, "Küre", V3(0f, 0.6f, 0f), V3(1.2f, 1.2f, 1.2f), GLASS, kindM = MaterialKind.GLASS, opacity = 0.5f)
            line(G.circle(V3(0f, 0.6f, 0f), 0.6f, 40).let { it + it.first() }, RED, 0.012f)
            line(listOf(V3(0f, 0.6f, 0f), V3(0.6f, 0.6f, 0f)), BLUE)
        },
        item("geo.hemisphere", "Yarım küre", GEOMETRY, "Taban daireli yarım küre") {
            prim(PrimitiveKind.DOME, "Yarım küre", V3(0f, 0.25f, 0f), V3(1.2f, 1.2f, 1.2f), BLUE)
        },
        item("geo.prism5", "Beşgen prizma", GEOMETRY, "Düzgün beşgen tabanlı dik prizma") {
            mesh("Beşgen prizma", Primitives.revolveFrustum(5, 0.5f, 0.5f, 0f).mapPositions { it + V3(0f, 0.5f, 0f) }, GREEN)
        },
        item("geo.prism6", "Düzgün altıgen prizma", GEOMETRY, "Düzgün altıgen tabanlı dik prizma") {
            prim(PrimitiveKind.PRISM6, "Altıgen prizma", V3(0f, 0.5f, 0f), V3.ONE, ORANGE)
        },
        item("geo.prism3", "Düzgün üçgen prizma", GEOMETRY, "Eşkenar üçgen tabanlı dik prizma") {
            prim(PrimitiveKind.PRISM3, "Üçgen prizma", V3(0f, 0.5f, 0f), V3.ONE, PURPLE)
        },
        item("geo.torus", "Simit (torus)", GEOMETRY, "Çemberin bir eksen çevresinde dönmesiyle") {
            prim(PrimitiveKind.TORUS, "Simit", V3(0f, 0.15f, 0f), V3(1.2f, 1.2f, 1.2f), RED)
        },
        item("geo.axes", "Koordinat sistemi", GEOMETRY, "x (kırmızı), y (yeşil), z (mavi) eksenleri ve birim küp") {
            mesh("x ekseni", G.arrow(V3(0f, 0f, 0f), V3(1.4f, 0f, 0f), 0.025f), RED, smooth = true)
            mesh("y ekseni", G.arrow(V3(0f, 0f, 0f), V3(0f, 1.4f, 0f), 0.025f), GREEN, smooth = true)
            mesh("z ekseni", G.arrow(V3(0f, 0f, 0f), V3(0f, 0f, 1.4f), 0.025f), BLUE, smooth = true)
            mesh("Başlangıç", G.sphere(V3.ZERO, 0.05f), DARK, smooth = true)
            prim(PrimitiveKind.CUBE, "Birim küp", V3(0.5f, 0.5f, 0.5f), V3.ONE, GLASS, kindM = MaterialKind.GLASS, opacity = 0.25f)
        },
        item("geo.conic", "Koni kesitleri", GEOMETRY, "Çift koni ve eğik düzlem (elips)") {
            prim(PrimitiveKind.CONE, "Üst koni", V3(0f, 1.2f, 0f), V3(1f, 0.8f, 1f), ORANGE, rot = V3(180f, 0f, 0f))
            prim(PrimitiveKind.CONE, "Alt koni", V3(0f, 0.4f, 0f), V3(1f, 0.8f, 1f), ORANGE)
            prim(PrimitiveKind.PLANE, "Kesen düzlem", V3(0f, 0.55f, 0f), V3(1.6f, 1f, 1.6f), GLASS, rot = V3(25f, 0f, 0f), kindM = MaterialKind.GLASS, opacity = 0.5f)
        },
        item("geo.paraboloid", "Paraboloid", GEOMETRY, "y = x² + z² yüzeyi (çanak)") {
            mesh("Paraboloid", G.surface(16, 24, closeV = true) { u, v ->
                val r = u * 0.7f; val a = v * 2f * PI.toFloat()
                V3(r * cos(a), r * r * 1.6f, r * sin(a))
            }, BLUE, smooth = true)
        },
        item("geo.saddle", "Eyer yüzeyi", GEOMETRY, "y = x² − z² (hiperbolik paraboloid)") {
            mesh("Eyer", G.surface(18, 18) { u, v ->
                val x = (u - 0.5f) * 1.6f; val z = (v - 0.5f) * 1.6f
                V3(x, (x * x - z * z) * 0.45f + 0.35f, z)
            }, ORANGE, smooth = true)
        },
        item("geo.hyperboloid", "Tek yapraklı hiperboloid", GEOMETRY, "x² + z² − y² = 1 biçiminde dönel yüzey") {
            mesh("Hiperboloid", G.surface(24, 12, closeU = true) { u, v ->
                val a = u * 2f * PI.toFloat(); val t = (v - 0.5f) * 2f
                val r = 0.3f * sqrt(1f + 3f * t * t)
                V3(r * cos(a), v * 1.4f, r * sin(a))
            }, TEAL, smooth = true)
        },
        item("geo.mobius", "Möbius şeridi", GEOMETRY, "Tek yüzlü yüzey") {
            mesh("Möbius şeridi", G.surface(48, 4) { u, v ->
                val a = u * 2f * PI.toFloat(); val w = (v - 0.5f) * 0.45f
                val r = 0.6f + w * cos(a / 2)
                V3(r * cos(a), 0.5f + w * sin(a / 2), r * sin(a))
            }, PURPLE, smooth = true)
        },
        item("geo.helix", "Helis (sarmal)", GEOMETRY, "Silindir üzerinde sabit eğimli eğri") {
            val pts = List(120) { i -> val t = i / 119f * 4f * PI.toFloat(); V3(0.45f * cos(t), 0.05f + i / 119f * 1.4f, 0.45f * sin(t)) }
            mesh("Sarmal", G.tube(pts, 0.045f, 8), RED, smooth = true)
            prim(PrimitiveKind.CYLINDER, "Silindir", V3(0f, 0.75f, 0f), V3(0.9f, 1.5f, 0.9f), GLASS, kindM = MaterialKind.GLASS, opacity = 0.25f)
        },
        item("geo.knot", "Simit düğümü", GEOMETRY, "(2, 3) torus düğümü (yonca düğümü)") {
            val pts = List(150) { i ->
                val t = i / 150f * 2f * PI.toFloat()
                val r = 0.45f + 0.18f * cos(3f * t)
                V3(r * cos(2f * t), 0.45f + 0.18f * sin(3f * t), r * sin(2f * t))
            }
            mesh("Düğüm", G.tube(pts, 0.07f, 8, closed = true), ORANGE, smooth = true)
        },
        item("geo.wave", "Dalga yüzeyi", GEOMETRY, "y = sin x · cos z") {
            mesh("Dalga", G.surface(22, 22) { u, v ->
                val x = (u - 0.5f) * 2f; val z = (v - 0.5f) * 2f
                V3(x, 0.25f + 0.18f * sin(3f * x) * cos(3f * z), z)
            }, BLUE, smooth = true)
        },
    )

    private fun physics(): List<CatalogItem> = listOf(
        item("phy.incline", "Eğik düzlem", PHYSICS, "Eğik düzlemde blok; ağırlık (kırmızı) ve tepki kuvveti (mavi)", yaw = 70f) {
            prim(PrimitiveKind.WEDGE, "Eğik düzlem", V3(0f, 0.35f, 0f), V3(0.8f, 0.7f, 2f), 0xFFBCAAA4.toInt(), texture = "wood.pine", texScale = 0.6f)
            val n = V3(0f, 2f, 0.7f).normalized()
            val angle = Math.toDegrees(asin(n.z.toDouble())).toFloat()
            val c = V3(0f, 0.35f, 0f) + n * 0.16f
            prim(PrimitiveKind.CUBE, "Blok", c, V3(0.32f, 0.32f, 0.32f), BLUE, rot = V3(angle, 0f, 0f))
            mesh("Ağırlık", G.arrow(c, c + V3(0f, -0.6f, 0f), 0.02f), RED, smooth = true)
            mesh("Tepki kuvveti", G.arrow(c, c + n * 0.55f, 0.02f), 0xFF3949AB.toInt(), smooth = true)
        },
        item("phy.pulley", "Makara", PHYSICS, "Sabit makara ve iki kütle") {
            prim(PrimitiveKind.CUBE, "Tavan", V3(0f, 1.75f, 0f), V3(1.2f, 0.08f, 0.4f), GRAY)
            prim(PrimitiveKind.CUBE, "Askı", V3(0f, 1.6f, 0f), V3(0.06f, 0.24f, 0.06f), DARK)
            prim(PrimitiveKind.CYLINDER, "Makara", V3(0f, 1.45f, 0f), V3(0.5f, 0.1f, 0.5f), 0xFFB0BEC5.toInt(), rot = V3(90f, 0f, 0f), kindM = MaterialKind.METAL, shine = 0.6f)
            line(listOf(V3(-0.25f, 1.45f, 0f), V3(-0.25f, 0.75f, 0f)), INK, 0.012f)
            line(listOf(V3(0.25f, 1.45f, 0f), V3(0.25f, 0.45f, 0f)), INK, 0.012f)
            prim(PrimitiveKind.CUBE, "Kütle 1", V3(-0.25f, 0.62f, 0f), V3(0.24f, 0.26f, 0.24f), RED)
            prim(PrimitiveKind.CUBE, "Kütle 2", V3(0.25f, 0.29f, 0f), V3(0.3f, 0.32f, 0.3f), BLUE)
        },
        item("phy.pendulum", "Basit sarkaç", PHYSICS, "İp, kütle ve salınım yayı") {
            prim(PrimitiveKind.CUBE, "Taban", V3(0f, 0.03f, 0f), V3(1.2f, 0.06f, 0.5f), BROWN, texture = "wood.walnut", texScale = 0.5f)
            prim(PrimitiveKind.CYLINDER, "Direk", V3(-0.5f, 0.8f, 0f), V3(0.05f, 1.5f, 0.05f), GRAY, kindM = MaterialKind.METAL)
            prim(PrimitiveKind.CYLINDER, "Kol", V3(-0.15f, 1.53f, 0f), V3(0.04f, 0.7f, 0.04f), GRAY, rot = V3(0f, 0f, 90f), kindM = MaterialKind.METAL)
            val pivot = V3(0.15f, 1.53f, 0f)
            val a = Math.toRadians(20.0)
            val bob = pivot + V3((sin(a) * 1.1).toFloat(), (-cos(a) * 1.1).toFloat(), 0f)
            line(listOf(pivot, bob), INK, 0.01f)
            mesh("Kütle", G.sphere(bob, 0.09f), ORANGE, kind = MaterialKind.METAL, smooth = true, shine = 0.7f)
            dashed(pivot, pivot + V3(0f, -1.1f, 0f), GRAY, 0.008f)
            line(List(16) { i -> val t = Math.toRadians(-20.0 + 40.0 * i / 15); pivot + V3((sin(t) * 1.1).toFloat(), (-cos(t) * 1.1).toFloat(), 0f) }, BLUE, 0.01f)
        },
        item("phy.spring", "Yay ve kütle", PHYSICS, "Tavana asılı yay ve kütle (Hooke yasası)") {
            prim(PrimitiveKind.CUBE, "Tavan", V3(0f, 1.6f, 0f), V3(0.8f, 0.06f, 0.4f), GRAY)
            val pts = List(140) { i -> val t = i / 139f * 10f * 2f * PI.toFloat(); V3(0.12f * cos(t), 1.57f - i / 139f * 0.9f, 0.12f * sin(t)) }
            mesh("Yay", G.tube(pts, 0.018f, 6), 0xFFB0BEC5.toInt(), kind = MaterialKind.METAL, smooth = true, shine = 0.7f)
            prim(PrimitiveKind.CUBE, "Kütle", V3(0f, 0.5f, 0f), V3(0.36f, 0.36f, 0.36f), RED)
        },
        item("phy.lens", "İnce kenarlı mercek", PHYSICS, "Paralel ışınlar odakta toplanır") {
            val rs = listOf(0f, 0.14f, 0.26f, 0.36f, 0.43f, 0.48f, 0.5f)
            val prof = rs.map { r -> r to -0.07f * (1f - (r / 0.5f) * (r / 0.5f)) } + rs.reversed().drop(1).map { r -> r to 0.07f * (1f - (r / 0.5f) * (r / 0.5f)) }
            val lens = G.revolve(prof, 24, capBottom = false, capTop = false).let { G.place(it, V3(0f, 0.6f, 0f), V3.ONE, V3(90f, 0f, 0f)) }
            mesh("Mercek", lens, GLASS, kind = MaterialKind.GLASS, smooth = true, opacity = 0.6f)
            dashed(V3(0f, 0.6f, -1.4f), V3(0f, 0.6f, 1.4f), GRAY, 0.008f, 14)
            for (h in listOf(-0.3f, -0.15f, 0.15f, 0.3f)) {
                line(listOf(V3(0f, 0.6f + h, -1.3f), V3(0f, 0.6f + h, 0f), V3(0f, 0.6f, 0.9f)), ORANGE, 0.01f)
            }
            mesh("Odak", G.sphere(V3(0f, 0.6f, 0.9f), 0.035f), RED, smooth = true)
        },
        item("phy.prism", "Işık prizması", PHYSICS, "Beyaz ışığın renklere ayrılması", yaw = 10f, pitch = 30f) {
            prim(PrimitiveKind.PRISM3, "Prizma", V3(0f, 0.4f, 0f), V3(0.9f, 0.8f, 0.9f), GLASS, rot = V3(90f, 0f, 0f), kindM = MaterialKind.GLASS, opacity = 0.55f)
            line(listOf(V3(-1.3f, 0.25f, 0f), V3(-0.2f, 0.45f, 0f)), WHITE, 0.02f)
            val colors = listOf(RED, ORANGE, YELLOW, GREEN, BLUE, PURPLE)
            colors.forEachIndexed { i, c -> line(listOf(V3(0.2f, 0.45f, 0f), V3(1.3f, 0.75f - i * 0.12f, 0f)), c, 0.014f) }
        },
        item("phy.magnet", "U mıknatıs", PHYSICS, "Kuzey (kırmızı) ve güney (mavi) kutupları") {
            val left = ArrayList<V3>(); val right = ArrayList<V3>()
            for (i in 0..10) left += V3(-0.3f, 1.1f - i * 0.07f, 0f)
            for (i in 0..12) { val a = PI.toFloat() + i / 12f * PI.toFloat() / 2f; left += V3(0.3f * cos(a), 0.4f + 0.3f * sin(a), 0f) }
            for (i in 0..12) { val a = 1.5f * PI.toFloat() + i / 12f * PI.toFloat() / 2f; right += V3(0.3f * cos(a), 0.4f + 0.3f * sin(a), 0f) }
            for (i in 0..10) right += V3(0.3f, 0.4f + i * 0.07f, 0f)
            mesh("Kuzey (N)", G.tube(left, 0.1f, 8), RED, smooth = true)
            mesh("Güney (S)", G.tube(right, 0.1f, 8), BLUE, smooth = true)
            for (k in 1..3) line(List(17) { i -> val t = PI * i / 16; V3((-0.3 * cos(t) * (1 + k * 0.25)).toFloat(), (1.1 + 0.25 * k * sin(t)).toFloat(), 0f) }, GRAY, 0.008f)
        },
        item("phy.atom", "Atom modeli", PHYSICS, "Çekirdek (proton, nötron) ve yörüngelerde elektronlar") {
            val nucleus = ArrayList<Mesh>(); val neutrons = ArrayList<Mesh>()
            val offs = listOf(V3(0f, 0f, 0f), V3(0.08f, 0.05f, 0f), V3(-0.07f, 0.06f, 0.04f), V3(0.02f, -0.07f, 0.06f), V3(-0.05f, -0.04f, -0.07f), V3(0.06f, 0.02f, -0.07f))
            offs.forEachIndexed { i, o -> (if (i % 2 == 0) nucleus else neutrons) += G.sphere(V3(0f, 0.8f, 0f) + o, 0.07f, 10, 5) }
            mesh("Protonlar", Mesh.concat(nucleus), RED, smooth = true)
            mesh("Nötronlar", Mesh.concat(neutrons), GRAY, smooth = true)
            val rings = ArrayList<Mesh>(); val electrons = ArrayList<Mesh>()
            for (k in 0 until 3) {
                val tilt = V3(60f * (k - 1), 60f * k, 0f)
                val circle = G.circle(V3.ZERO, 0.6f, 48)
                val t = Transform3.of(V3(0f, 0.8f, 0f), tilt, V3.ONE)
                rings += G.tube(circle.map { t.apply(it) }, 0.008f, 4, closed = true)
                electrons += G.sphere(t.apply(circle[k * 16]), 0.045f, 10, 5)
            }
            mesh("Yörüngeler", Mesh.concat(rings), GRAY, smooth = true)
            mesh("Elektronlar", Mesh.concat(electrons), BLUE, kind = MaterialKind.GLOW, smooth = true)
        },
        item("phy.vectors", "Vektör toplama", PHYSICS, "a (kırmızı) + b (mavi) = a + b (mor), paralelkenar kuralı") {
            val a = V3(1.1f, 0f, 0.2f); val b = V3(0.3f, 0f, 0.9f)
            mesh("a", G.arrow(V3.ZERO, a, 0.022f), RED, smooth = true)
            mesh("b", G.arrow(V3.ZERO, b, 0.022f), BLUE, smooth = true)
            mesh("a + b", G.arrow(V3.ZERO, a + b, 0.026f), PURPLE, smooth = true)
            dashed(a, a + b, GRAY); dashed(b, a + b, GRAY)
            prim(PrimitiveKind.PLANE, "Düzlem", V3(0.7f, -0.002f, 0.55f), V3(2f, 1f, 1.5f), PAPER, kindM = MaterialKind.MATTE)
        },
        item("phy.circuit", "Basit elektrik devresi", PHYSICS, "Pil, ampul ve iletken teller") {
            prim(PrimitiveKind.CYLINDER, "Pil", V3(-0.6f, 0.12f, 0f), V3(0.24f, 0.5f, 0.24f), 0xFF424242.toInt(), rot = V3(0f, 0f, 90f))
            prim(PrimitiveKind.CYLINDER, "Pil ucu (+)", V3(-0.33f, 0.12f, 0f), V3(0.1f, 0.06f, 0.1f), 0xFFB0BEC5.toInt(), rot = V3(0f, 0f, 90f), kindM = MaterialKind.METAL)
            prim(PrimitiveKind.CYLINDER, "Duy", V3(0.6f, 0.1f, 0f), V3(0.16f, 0.2f, 0.16f), GRAY, kindM = MaterialKind.METAL)
            mesh("Ampul", G.sphere(V3(0.6f, 0.36f, 0f), 0.15f), 0xFFFFF59D.toInt(), kind = MaterialKind.GLOW, smooth = true)
            val wire1 = listOf(V3(-0.3f, 0.12f, 0f), V3(-0.1f, 0.12f, 0f), V3(-0.1f, 0.12f, 0.45f), V3(0.6f, 0.12f, 0.45f), V3(0.6f, 0.05f, 0.08f))
            val wire2 = listOf(V3(-0.86f, 0.12f, 0f), V3(-1f, 0.12f, 0f), V3(-1f, 0.12f, -0.45f), V3(0.6f, 0.12f, -0.45f), V3(0.6f, 0.05f, -0.08f))
            mesh("Kırmızı tel", G.tube(wire1, 0.015f, 6), RED, smooth = true)
            mesh("Siyah tel", G.tube(wire2, 0.015f, 6), DARK, smooth = true)
        },
    )

    // ------------------------------------------------------------------------------------------ kimya

    private class Mol(val b: B) {
        val atoms = LinkedHashMap<String, Pair<Int, MutableList<Mesh>>>()
        val bonds = ArrayList<Mesh>()
        fun atom(element: String, color: Int, p: V3, r: Float) { atoms.getOrPut(element) { color to ArrayList() }.second += G.sphere(p, r, 14, 7) }
        fun bond(a: V3, c: V3, r: Float = 0.035f) { bonds += G.cylinder(a, c, r, 8) }
        fun double(a: V3, c: V3, side: V3, r: Float = 0.026f) { val s = side.normalized() * 0.045f; bond(a + s, c + s, r); bond(a - s, c - s, r) }
        fun done() {
            atoms.forEach { (el, v) -> b.mesh(el, Mesh.concat(v.second), v.first, smooth = true, shine = 0.6f) }
            if (bonds.isNotEmpty()) b.mesh("Bağlar", Mesh.concat(bonds), 0xFFCFD8DC.toInt(), smooth = true)
        }
    }

    private fun molecule(id: String, name: String, description: String, build: Mol.() -> Unit) =
        item(id, name, CHEMISTRY, description) { Mol(this).apply(build).done() }

    private const val C_COLOR = 0xFF424242.toInt()
    private const val H_COLOR = 0xFFF5F5F5.toInt()
    private const val O_COLOR = 0xFFE53935.toInt()
    private const val N_COLOR = 0xFF3949AB.toInt()

    private fun chemistry(): List<CatalogItem> = listOf(
        molecule("chem.h2o", "Su (H₂O)", "Açısal molekül (104,5°)") {
            val o = V3(0f, 0.5f, 0f)
            val a = Math.toRadians(104.5 / 2)
            val h1 = o + V3((sin(a) * 0.45).toFloat(), (-cos(a) * 0.45).toFloat(), 0f)
            val h2 = o + V3((-sin(a) * 0.45).toFloat(), (-cos(a) * 0.45).toFloat(), 0f)
            atom("Oksijen", O_COLOR, o, 0.2f); atom("Hidrojen", H_COLOR, h1, 0.13f); atom("Hidrojen", H_COLOR, h2, 0.13f)
            bond(o, h1); bond(o, h2)
        },
        molecule("chem.co2", "Karbondioksit (CO₂)", "Doğrusal molekül, iki çift bağ") {
            val c = V3(0f, 0.3f, 0f); val o1 = c + V3(-0.55f, 0f, 0f); val o2 = c + V3(0.55f, 0f, 0f)
            atom("Karbon", C_COLOR, c, 0.18f); atom("Oksijen", O_COLOR, o1, 0.2f); atom("Oksijen", O_COLOR, o2, 0.2f)
            double(c, o1, V3(0f, 1f, 0f)); double(c, o2, V3(0f, 1f, 0f))
        },
        molecule("chem.ch4", "Metan (CH₄)", "Düzgün dörtyüzlü (109,5°)") {
            val c = V3(0f, 0.55f, 0f)
            atom("Karbon", C_COLOR, c, 0.18f)
            listOf(V3(1f, 1f, 1f), V3(1f, -1f, -1f), V3(-1f, 1f, -1f), V3(-1f, -1f, 1f)).forEach { d ->
                val h = c + d.normalized() * 0.5f
                atom("Hidrojen", H_COLOR, h, 0.12f); bond(c, h)
            }
        },
        molecule("chem.nh3", "Amonyak (NH₃)", "Üçgen piramit (107°)") {
            val n = V3(0f, 0.6f, 0f)
            atom("Azot", N_COLOR, n, 0.19f)
            for (k in 0 until 3) {
                val a = 2.0 * PI * k / 3
                val h = n + V3((0.45 * cos(a)).toFloat(), -0.2f, (0.45 * sin(a)).toFloat())
                atom("Hidrojen", H_COLOR, h, 0.12f); bond(n, h)
            }
        },
        molecule("chem.benzene", "Benzen (C₆H₆)", "Altıgen halka, ardışık çift bağlar") {
            val cs = List(6) { k -> val a = 2.0 * PI * k / 6; V3((0.5 * cos(a)).toFloat(), 0.15f, (0.5 * sin(a)).toFloat()) }
            cs.forEachIndexed { k, c ->
                atom("Karbon", C_COLOR, c, 0.13f)
                val h = c + V3(c.x, 0f, c.z).normalized() * 0.38f
                atom("Hidrojen", H_COLOR, h, 0.09f); bond(c, h, 0.03f)
                val next = cs[(k + 1) % 6]
                if (k % 2 == 0) double(c, next, V3(0f, 1f, 0f)) else bond(c, next)
            }
        },
        molecule("chem.ethanol", "Etanol (C₂H₅OH)", "İki karbonlu alkol") {
            val c1 = V3(-0.3f, 0.5f, 0f); val c2 = V3(0.3f, 0.6f, 0f); val o = V3(0.75f, 0.35f, 0f); val ho = o + V3(0.3f, 0.15f, 0.1f)
            atom("Karbon", C_COLOR, c1, 0.16f); atom("Karbon", C_COLOR, c2, 0.16f); atom("Oksijen", O_COLOR, o, 0.17f); atom("Hidrojen", H_COLOR, ho, 0.1f)
            bond(c1, c2); bond(c2, o); bond(o, ho)
            listOf(V3(-0.55f, 0.85f, 0.1f), V3(-0.55f, 0.35f, 0.3f), V3(-0.45f, 0.35f, -0.35f)).forEach { atom("Hidrojen", H_COLOR, it, 0.1f); bond(c1, it) }
            listOf(V3(0.35f, 0.95f, 0.15f), V3(0.35f, 0.7f, -0.35f)).forEach { atom("Hidrojen", H_COLOR, it, 0.1f); bond(c2, it) }
        },
        molecule("chem.nacl", "NaCl kristal örgüsü", "Yüzey merkezli kübik örgü: Na⁺ (mor), Cl⁻ (yeşil)") {
            val s = 0.42f
            for (i in 0..2) for (j in 0..2) for (k in 0..2) {
                val p = V3((i - 1) * s, 0.15f + j * s, (k - 1) * s)
                if ((i + j + k) % 2 == 0) atom("Klor (Cl⁻)", 0xFF66BB6A.toInt(), p, 0.15f) else atom("Sodyum (Na⁺)", 0xFF9575CD.toInt(), p, 0.1f)
                if (i < 2) bond(p, p + V3(s, 0f, 0f), 0.015f)
                if (j < 2) bond(p, p + V3(0f, s, 0f), 0.015f)
                if (k < 2) bond(p, p + V3(0f, 0f, s), 0.015f)
            }
        },
        item("chem.beaker", "Beher", CHEMISTRY, "Ölçekli cam kap ve sıvı") {
            prim(PrimitiveKind.TUBE, "Beher", V3(0f, 0.45f, 0f), V3(0.7f, 0.9f, 0.7f), GLASS, kindM = MaterialKind.GLASS, opacity = 0.45f, spec = PrimitiveSpec(PrimitiveKind.TUBE, 24, 1, thickness = 0.05f))
            prim(PrimitiveKind.CYLINDER, "Taban", V3(0f, 0.01f, 0f), V3(0.7f, 0.02f, 0.7f), GLASS, kindM = MaterialKind.GLASS, opacity = 0.45f)
            prim(PrimitiveKind.CYLINDER, "Sıvı", V3(0f, 0.27f, 0f), V3(0.66f, 0.5f, 0.66f), 0xFF4FC3F7.toInt(), kindM = MaterialKind.GLASS, opacity = 0.75f)
            for (k in 1..4) line(listOf(V3(0.36f, k * 0.18f, 0f), V3(0.36f, k * 0.18f, 0.12f)), INK, 0.008f)
        },
        item("chem.flask", "Erlenmeyer", CHEMISTRY, "Konik cam şişe ve sıvı") {
            val prof = listOf(0.4f to 0f, 0.4f to 0.05f, 0.12f to 0.75f, 0.12f to 1.0f, 0.14f to 1.03f)
            mesh("Erlenmeyer", G.revolve(prof, 24, capBottom = true, capTop = false), GLASS, kind = MaterialKind.GLASS, smooth = true, opacity = 0.45f)
            mesh("Sıvı", G.revolve(listOf(0.38f to 0.01f, 0.38f to 0.06f, 0.27f to 0.3f), 24), 0xFFFF8A65.toInt(), kind = MaterialKind.GLASS, smooth = true, opacity = 0.8f)
        },
        item("chem.testtube", "Deney tüpü", CHEMISTRY, "Yuvarlak dipli tüp ve sıvı") {
            val prof = ArrayList<Pair<Float, Float>>()
            for (k in 0..6) { val a = PI / 2 * k / 6; prof += (0.12 * sin(a)).toFloat() to (0.12 - 0.12 * cos(a)).toFloat() }
            prof += 0.12f to 1.0f
            mesh("Tüp", G.revolve(prof, 18, capBottom = false, capTop = false), GLASS, kind = MaterialKind.GLASS, smooth = true, opacity = 0.45f)
            val liquid = ArrayList<Pair<Float, Float>>()
            for (k in 0..6) { val a = PI / 2 * k / 6; liquid += (0.11 * sin(a)).toFloat() to (0.125 - 0.11 * cos(a)).toFloat() }
            liquid += 0.11f to 0.45f
            mesh("Sıvı", G.revolve(liquid, 18, capBottom = false, capTop = true), 0xFF66BB6A.toInt(), kind = MaterialKind.GLASS, smooth = true, opacity = 0.8f)
        },
    )

    private fun biology(): List<CatalogItem> = listOf(
        item("bio.dna", "DNA çift sarmalı", BIOLOGY, "İki şeker-fosfat zinciri ve baz çiftleri (A–T, G–C)") {
            val back1 = ArrayList<Mesh>(); val back2 = ArrayList<Mesh>(); val at = ArrayList<Mesh>(); val gc = ArrayList<Mesh>()
            val n = 20
            val p1 = ArrayList<V3>(); val p2 = ArrayList<V3>()
            for (i in 0 until n) {
                val t = i * 0.6f; val y = 0.05f + i * 0.085f
                val a = V3(0.35f * cos(t), y, 0.35f * sin(t)); val b = V3(0.35f * cos(t + PI.toFloat()), y, 0.35f * sin(t + PI.toFloat()))
                p1 += a; p2 += b
                back1 += G.sphere(a, 0.05f, 8, 4); back2 += G.sphere(b, 0.05f, 8, 4)
                val mid = (a + b) * 0.5f
                if (i % 2 == 0) at += G.cylinder(a, b, 0.02f, 6) else gc += G.cylinder(a, mid, 0.02f, 6) + G.cylinder(mid, b, 0.02f, 6)
            }
            mesh("Zincir 1", Mesh.concat(back1 + G.tube(p1, 0.02f, 6)), ORANGE, smooth = true)
            mesh("Zincir 2", Mesh.concat(back2 + G.tube(p2, 0.02f, 6)), BLUE, smooth = true)
            mesh("A–T bağları", Mesh.concat(at), GREEN, smooth = true)
            mesh("G–C bağları", Mesh.concat(gc), RED, smooth = true)
        },
        item("bio.cell", "Hayvan hücresi", BIOLOGY, "Zar, çekirdek, çekirdekçik, mitokondriler ve ribozomlar") {
            mesh("Hücre zarı", G.sphere(V3(0f, 0.7f, 0f), 0.7f, 24, 12), 0xFFF8BBD0.toInt(), kind = MaterialKind.GLASS, smooth = true, opacity = 0.35f)
            mesh("Çekirdek", G.sphere(V3(0.05f, 0.72f, 0.02f), 0.25f, 18, 9), PURPLE, smooth = true)
            mesh("Çekirdekçik", G.sphere(V3(0.12f, 0.8f, 0.12f), 0.08f), 0xFF4A148C.toInt(), smooth = true)
            val mito = listOf(V3(-0.35f, 0.55f, 0.2f) to V3(0f, 30f, 60f), V3(0.35f, 0.45f, -0.25f) to V3(40f, 0f, 20f), V3(-0.2f, 1.0f, -0.25f) to V3(0f, 70f, 80f))
            mito.forEachIndexed { i, (p, r) -> prim(PrimitiveKind.CAPSULE, "Mitokondri ${i + 1}", p, V3(0.18f, 0.3f, 0.18f), ORANGE, rot = r) }
            val ribo = ArrayList<Mesh>()
            val rnd = java.util.Random(7)
            repeat(26) {
                val d = V3(rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f).normalized() * (0.35f + rnd.nextFloat() * 0.25f)
                ribo += G.sphere(V3(0f, 0.7f, 0f) + d, 0.022f, 6, 3)
            }
            mesh("Ribozomlar", Mesh.concat(ribo), DARK, smooth = true)
        },
        item("bio.virus", "Virüs", BIOLOGY, "Yirmiyüzlü kapsit ve yüzey proteinleri") {
            val c = V3(0f, 0.7f, 0f)
            val ico = G.icosahedron(0.45f)
            mesh("Kapsit", G.place(ico, c, 1f), TEAL)
            val spikes = ArrayList<Mesh>(); val heads = ArrayList<Mesh>()
            for (i in 0 until ico.vertexCount) {
                val v = ico.vertex(i).normalized()
                spikes += G.cylinder(c + v * 0.42f, c + v * 0.62f, 0.02f, 6)
                heads += G.sphere(c + v * 0.65f, 0.045f, 8, 4)
            }
            mesh("Proteinler", Mesh.concat(spikes), GRAY, smooth = true)
            mesh("Uçlar", Mesh.concat(heads), RED, smooth = true)
        },
        item("bio.rbc", "Alyuvar", BIOLOGY, "İki yanı çukur disk biçiminde kan hücresi") {
            val prof = ArrayList<Pair<Float, Float>>()
            val rs = List(9) { 0.5f * it / 8f }
            fun h(r: Float): Float { val x = r / 0.5f; return 0.12f * sqrt((1f - x * x).coerceAtLeast(0f)) * (0.35f + 1.2f * x * x) }
            rs.forEach { r -> prof += r to -h(r) }
            rs.reversed().drop(1).forEach { r -> prof += r to h(r) }
            mesh("Alyuvar", G.revolve(prof, 28, capBottom = false, capTop = false).mapPositions { it + V3(0f, 0.3f, 0f) }, RED, smooth = true, shine = 0.6f)
        },
    )

    private fun astronomy(): List<CatalogItem> = listOf(
        item("astro.solar", "Güneş sistemi", ASTRONOMY, "Güneş ve gezegenler (ölçeksiz)", pitch = 30f) {
            mesh("Güneş", G.sphere(V3(0f, 0.3f, 0f), 0.3f, 18, 9), 0xFFFFB300.toInt(), kind = MaterialKind.GLOW, smooth = true)
            val planets = listOf(
                Triple("Merkür", 0.05f, 0xFF9E9E9E.toInt()), Triple("Venüs", 0.07f, 0xFFFFCC80.toInt()), Triple("Dünya", 0.075f, BLUE),
                Triple("Mars", 0.06f, 0xFFD84315.toInt()), Triple("Jüpiter", 0.16f, 0xFFD7B899.toInt()), Triple("Satürn", 0.13f, 0xFFE6C88C.toInt()),
                Triple("Uranüs", 0.1f, 0xFF80DEEA.toInt()), Triple("Neptün", 0.1f, 0xFF3F51B5.toInt()),
            )
            planets.forEachIndexed { i, (name, r, color) ->
                val d = 0.55f + i * 0.3f
                val a = i * 0.9f
                mesh(name, G.sphere(V3(d * cos(a), 0.3f, d * sin(a)), r, 14, 7), color, smooth = true)
                line(G.circle(V3(0f, 0.3f, 0f), d, 56).let { it + it.first() }, 0xFFB0BEC5.toInt(), 0.006f)
            }
        },
        item("astro.earth_moon", "Dünya ve Ay", ASTRONOMY, "Dünya'nın 23,5° eğik ekseni ve Ay'ın yörüngesi") {
            val e = V3(0f, 0.5f, 0f)
            mesh("Dünya", G.sphere(e, 0.4f, 20, 10), BLUE, smooth = true, shine = 0.6f)
            val tilt = Math.toRadians(23.5)
            val axis = V3(sin(tilt).toFloat(), cos(tilt).toFloat(), 0f)
            line(listOf(e - axis * 0.6f, e + axis * 0.6f), RED, 0.012f)
            line(G.circle(e, 1.2f, 56).let { it + it.first() }, GRAY, 0.008f)
            mesh("Ay", G.sphere(e + V3(1.2f, 0f, 0f), 0.11f), 0xFFBDBDBD.toInt(), smooth = true, kind = MaterialKind.MATTE)
        },
        item("astro.saturn", "Satürn", ASTRONOMY, "Gezegen ve halkaları") {
            val c = V3(0f, 0.6f, 0f)
            mesh("Satürn", G.sphere(c, 0.4f, 20, 10), 0xFFE6C88C.toInt(), smooth = true)
            prim(PrimitiveKind.TUBE, "Halkalar", c, V3(1.5f, 0.01f, 1.5f), 0xFFD7CCC8.toInt(), rot = V3(20f, 0f, 0f), opacity = 0.85f, spec = PrimitiveSpec(PrimitiveKind.TUBE, 40, 1, thickness = 0.35f))
        },
        item("astro.phases", "Ay'ın evreleri", ASTRONOMY, "Güneş ışığına göre Ay'ın dört konumu", pitch = 40f) {
            mesh("Dünya", G.sphere(V3(0f, 0.25f, 0f), 0.22f), BLUE, smooth = true)
            for (k in 0 until 4) {
                val a = PI / 2 * k
                mesh("Ay ${k + 1}", G.sphere(V3((0.9 * cos(a)).toFloat(), 0.25f, (0.9 * sin(a)).toFloat()), 0.09f), 0xFFBDBDBD.toInt(), smooth = true, kind = MaterialKind.MATTE)
            }
            line(G.circle(V3(0f, 0.25f, 0f), 0.9f, 48).let { it + it.first() }, GRAY, 0.006f)
            for (z in listOf(-0.6f, 0f, 0.6f)) mesh("Güneş ışığı", G.arrow(V3(-1.8f, 0.25f, z), V3(-1.25f, 0.25f, z), 0.015f), YELLOW, kind = MaterialKind.GLOW, smooth = true)
        },
    )

    private fun drawing(): List<CatalogItem> = listOf(
        item("draw.study", "Işık-gölge çalışması", DRAWING, "Beyaz geometrik cisimler: küp, küre, silindir, koni") {
            prim(PrimitiveKind.PLANE, "Zemin", V3(0f, 0f, 0f), V3(2.4f, 1f, 1.6f), PAPER, kindM = MaterialKind.MATTE)
            prim(PrimitiveKind.CUBE, "Küp", V3(-0.7f, 0.25f, -0.2f), V3(0.5f, 0.5f, 0.5f), WHITE, rot = V3(0f, 25f, 0f), kindM = MaterialKind.MATTE)
            prim(PrimitiveKind.SPHERE, "Küre", V3(0.05f, 0.22f, 0.3f), V3(0.44f, 0.44f, 0.44f), WHITE, kindM = MaterialKind.MATTE)
            prim(PrimitiveKind.CYLINDER, "Silindir", V3(0.25f, 0.35f, -0.35f), V3(0.36f, 0.7f, 0.36f), WHITE, kindM = MaterialKind.MATTE)
            prim(PrimitiveKind.CONE, "Koni", V3(0.85f, 0.3f, 0.15f), V3(0.42f, 0.6f, 0.42f), WHITE, kindM = MaterialKind.MATTE)
        },
        item("draw.mannequin", "Çizim mankeni", DRAWING, "Eklemleri belirgin ahşap insan figürü", yaw = 25f, pitch = 10f) {
            val wood = 0xFFE8C9A0.toInt()
            fun limb(name: String, a: V3, b: V3, r: Float) = mesh(name, G.cylinder(a, b, r, 10, r * 0.85f), wood, smooth = true, texture = "wood.pine", texScale = 0.3f)
            fun joint(name: String, p: V3, r: Float) = mesh(name, G.sphere(p, r, 10, 5), 0xFFD7B48A.toInt(), smooth = true)
            mesh("Baş", G.place(G.sphere(V3.ZERO, 0.5f, 14, 7), V3(0f, 1.62f, 0f), V3(0.2f, 0.26f, 0.22f)), wood, smooth = true)
            limb("Boyun", V3(0f, 1.42f, 0f), V3(0f, 1.52f, 0f), 0.04f)
            mesh("Göğüs", G.place(G.sphere(V3.ZERO, 0.5f, 14, 7), V3(0f, 1.25f, 0f), V3(0.36f, 0.34f, 0.22f)), wood, smooth = true)
            mesh("Kalça", G.place(G.sphere(V3.ZERO, 0.5f, 14, 7), V3(0f, 0.92f, 0f), V3(0.3f, 0.2f, 0.2f)), wood, smooth = true)
            limb("Bel", V3(0f, 0.98f, 0f), V3(0f, 1.12f, 0f), 0.06f)
            for (s in listOf(-1f, 1f)) {
                val side = if (s < 0) "sol" else "sağ"
                val sh = V3(0.21f * s, 1.36f, 0f); val el = V3(0.3f * s, 1.08f, 0.04f); val wr = V3(0.34f * s, 0.84f, 0.1f)
                joint("Omuz $side", sh, 0.055f); limb("Üst kol $side", sh, el, 0.04f); joint("Dirsek $side", el, 0.04f); limb("Ön kol $side", el, wr, 0.033f)
                mesh("El $side", G.place(G.sphere(V3.ZERO, 0.5f, 10, 5), wr + V3(0f, -0.06f, 0f), V3(0.06f, 0.11f, 0.04f)), wood, smooth = true)
                val hp = V3(0.1f * s, 0.86f, 0f); val kn = V3(0.11f * s, 0.48f, if (s < 0) 0.04f else -0.02f); val an = V3(0.11f * s, 0.08f, 0f)
                joint("Kalça eklemi $side", hp, 0.06f); limb("Uyluk $side", hp, kn, 0.055f); joint("Diz $side", kn, 0.045f); limb("Baldır $side", kn, an, 0.042f)
                mesh("Ayak $side", G.box(V3(0.11f * s - 0.045f, 0f, -0.04f), V3(0.11f * s + 0.045f, 0.06f, 0.16f)), wood, texture = "wood.pine", texScale = 0.3f)
            }
        },
        item("draw.chair", "Sandalye", DRAWING, "Ahşap sandalye (perspektif çalışması)") {
            prim(PrimitiveKind.CUBE, "Oturak", V3(0f, 0.45f, 0f), V3(0.5f, 0.05f, 0.5f), WHITE, texture = "wood.oak", texScale = 0.5f)
            for ((x, z) in listOf(-0.22f to -0.22f, 0.22f to -0.22f, -0.22f to 0.22f, 0.22f to 0.22f)) {
                prim(PrimitiveKind.CUBE, "Ayak", V3(x, 0.21f, z), V3(0.05f, 0.42f, 0.05f), WHITE, texture = "wood.walnut", texScale = 0.4f)
            }
            for (x in listOf(-0.22f, 0.22f)) prim(PrimitiveKind.CUBE, "Arka direk", V3(x, 0.75f, -0.22f), V3(0.05f, 0.56f, 0.05f), WHITE, texture = "wood.walnut", texScale = 0.4f)
            prim(PrimitiveKind.CUBE, "Arkalık", V3(0f, 0.9f, -0.22f), V3(0.44f, 0.18f, 0.04f), WHITE, texture = "wood.oak", texScale = 0.5f)
        },
        item("draw.books", "Kitap yığını", DRAWING, "Üst üste dört kitap") {
            val colors = listOf(RED, BLUE, GREEN, ORANGE)
            var y = 0f
            colors.forEachIndexed { i, c ->
                val h = 0.08f + (i % 2) * 0.03f
                prim(PrimitiveKind.CUBE, "Kitap ${i + 1}", V3(0.02f * i, y + h / 2, -0.02f * i), V3(0.7f - i * 0.05f, h, 0.5f - i * 0.03f), c, rot = V3(0f, -12f + i * 9f, 0f), kindM = MaterialKind.MATTE)
                prim(PrimitiveKind.CUBE, "Sayfalar ${i + 1}", V3(0.02f * i + 0.01f, y + h / 2, -0.02f * i), V3(0.66f - i * 0.05f, h * 0.8f, 0.49f - i * 0.03f), PAPER, rot = V3(0f, -12f + i * 9f, 0f), kindM = MaterialKind.MATTE)
                y += h
            }
        },
        item("draw.vase", "Vazo", DRAWING, "Döner profilli seramik vazo") {
            val prof = listOf(0.18f to 0f, 0.26f to 0.1f, 0.3f to 0.3f, 0.24f to 0.6f, 0.12f to 0.85f, 0.11f to 0.95f, 0.16f to 1.05f)
            mesh("Vazo", G.revolve(prof, 24, capBottom = true, capTop = false), 0xFF5C6BC0.toInt(), smooth = true, shine = 0.7f)
        },
        item("draw.bottle", "Şişe", DRAWING, "Cam şişe") {
            val prof = listOf(0.2f to 0f, 0.2f to 0.6f, 0.17f to 0.72f, 0.07f to 0.85f, 0.07f to 1.05f, 0.08f to 1.08f, 0.08f to 1.12f)
            mesh("Şişe", G.revolve(prof, 20), 0xFF2E7D32.toInt(), kind = MaterialKind.GLASS, smooth = true, opacity = 0.7f)
        },
        item("draw.glass", "Su bardağı", DRAWING, "Yarısı dolu cam bardak") {
            mesh("Bardak", G.revolve(listOf(0.18f to 0f, 0.22f to 0.55f), 22, capBottom = true, capTop = false), GLASS, kind = MaterialKind.GLASS, smooth = true, opacity = 0.4f)
            mesh("Su", G.revolve(listOf(0.17f to 0.01f, 0.195f to 0.3f), 22), 0xFF4FC3F7.toInt(), kind = MaterialKind.GLASS, smooth = true, opacity = 0.7f)
        },
        item("draw.apple", "Elma", DRAWING, "Sapı ve yaprağıyla elma") {
            val prof = ArrayList<Pair<Float, Float>>()
            for (k in 0..10) {
                val t = PI * k / 10
                val r = (0.32 * sin(t) * (1.0 + 0.12 * sin(t))).toFloat()
                val y = (0.3 - 0.29 * cos(t)).toFloat() + if (k == 10) -0.05f else 0f
                prof += (if (k == 0 || k == 10) 0f else r) to y
            }
            mesh("Elma", G.revolve(prof, 20, capBottom = false, capTop = false), RED, smooth = true, shine = 0.7f)
            mesh("Sap", G.cylinder(V3(0f, 0.52f, 0f), V3(0.03f, 0.68f, 0f), 0.015f, 6), BROWN, smooth = true)
            mesh("Yaprak", G.place(G.sphere(V3.ZERO, 0.5f, 10, 5), V3(0.1f, 0.64f, 0f), V3(0.16f, 0.02f, 0.07f), V3(0f, 0f, 25f)), GREEN, smooth = true)
        },
        item("draw.pear", "Armut", DRAWING, "Armut biçimli meyve") {
            val prof = listOf(0f to 0f, 0.18f to 0.04f, 0.26f to 0.15f, 0.25f to 0.3f, 0.17f to 0.45f, 0.11f to 0.58f, 0.08f to 0.68f, 0f to 0.72f)
            mesh("Armut", G.revolve(prof, 20, capBottom = false, capTop = false), 0xFFC0CA33.toInt(), smooth = true, shine = 0.5f)
            mesh("Sap", G.cylinder(V3(0f, 0.7f, 0f), V3(0.02f, 0.82f, 0f), 0.012f, 6), BROWN, smooth = true)
        },
        item("draw.stairs", "Merdiven", DRAWING, "Beş basamaklı merdiven") {
            for (i in 0 until 5) prim(PrimitiveKind.CUBE, "Basamak ${i + 1}", V3(0f, 0.1f + i * 0.2f, -i * 0.25f), V3(0.9f, 0.2f + i * 0.0f, 0.25f), WHITE, texture = "stone.concrete", texScale = 0.5f, kindM = MaterialKind.MATTE)
            for (i in 1 until 5) prim(PrimitiveKind.CUBE, "Dolgu ${i}", V3(0f, i * 0.1f, -i * 0.25f), V3(0.9f, i * 0.2f, 0.25f), WHITE, texture = "stone.concrete", texScale = 0.5f, kindM = MaterialKind.MATTE)
        },
        item("draw.car", "Araba", DRAWING, "Basit hatlı otomobil") {
            prim(PrimitiveKind.CUBE, "Gövde", V3(0f, 0.3f, 0f), V3(1.6f, 0.3f, 0.75f), RED, shine = 0.8f)
            prim(PrimitiveKind.CUBE, "Kabin", V3(-0.1f, 0.57f, 0f), V3(0.85f, 0.26f, 0.68f), RED, shine = 0.8f, spec = PrimitiveSpec(PrimitiveKind.CUBE, 4, 1))
            prim(PrimitiveKind.CUBE, "Ön cam", V3(0.32f, 0.57f, 0f), V3(0.02f, 0.22f, 0.6f), GLASS, kindM = MaterialKind.GLASS, opacity = 0.7f)
            for ((x, z) in listOf(-0.5f to 0.38f, 0.5f to 0.38f, -0.5f to -0.38f, 0.5f to -0.38f)) {
                prim(PrimitiveKind.CYLINDER, "Teker", V3(x, 0.16f, z), V3(0.32f, 0.12f, 0.32f), 0xFF212121.toInt(), rot = V3(90f, 0f, 0f), kindM = MaterialKind.MATTE)
            }
            for (z in listOf(-0.25f, 0.25f)) mesh("Far", G.sphere(V3(0.8f, 0.33f, z), 0.06f), YELLOW, kind = MaterialKind.GLOW, smooth = true)
        },
        item("draw.lamp", "Masa lambası", DRAWING, "Eklemli masa lambası") {
            prim(PrimitiveKind.CYLINDER, "Taban", V3(0f, 0.03f, 0f), V3(0.4f, 0.06f, 0.4f), DARK, kindM = MaterialKind.METAL)
            val j1 = V3(0f, 0.06f, 0f); val j2 = V3(0.15f, 0.55f, 0f); val j3 = V3(0.55f, 0.75f, 0f)
            mesh("Alt kol", G.cylinder(j1, j2, 0.025f, 8), GRAY, kind = MaterialKind.METAL, smooth = true)
            mesh("Üst kol", G.cylinder(j2, j3, 0.022f, 8), GRAY, kind = MaterialKind.METAL, smooth = true)
            mesh("Eklem", G.sphere(j2, 0.04f), DARK, smooth = true)
            mesh("Şapka", G.cylinder(j3 + V3(0.02f, 0.05f, 0f), j3 + V3(0.12f, -0.2f, 0f), 0.05f, 16, 0.16f), 0xFF00897B.toInt(), smooth = true)
            mesh("Ampul", G.sphere(j3 + V3(0.11f, -0.17f, 0f), 0.06f), 0xFFFFF59D.toInt(), kind = MaterialKind.GLOW, smooth = true)
        },
        item("draw.house", "Çatılı ev", DRAWING, "Beşik çatılı ev (iki nokta perspektifi)") {
            prim(PrimitiveKind.CUBE, "Duvarlar", V3(0f, 0.4f, 0f), V3(1.2f, 0.8f, 0.9f), 0xFFFFF3E0.toInt(), kindM = MaterialKind.MATTE)
            // Üçgen prizma yatırılır: ekseni X boyunca, sivri ucu yukarıda (sırt), tabanı duvarların üstünde
            prim(PrimitiveKind.PRISM3, "Çatı", V3(0f, 0.8f + 0.25f * 0.67f, 0f), V3(1.15f, 1.32f, 0.67f), 0xFFB23A2E.toInt(), rot = V3(-90f, -90f, 0f), kindM = MaterialKind.MATTE)
            prim(PrimitiveKind.CUBE, "Kapı", V3(0.2f, 0.22f, 0.455f), V3(0.24f, 0.44f, 0.02f), BROWN, texture = "wood.planks", texScale = 0.3f)
            prim(PrimitiveKind.CUBE, "Pencere", V3(-0.3f, 0.5f, 0.455f), V3(0.26f, 0.22f, 0.02f), GLASS, kindM = MaterialKind.GLASS, opacity = 0.7f)
        },
        item("draw.tree", "Yuvarlak ağaç", DRAWING, "Gövde ve küresel yaprak kümeleri") {
            prim(PrimitiveKind.CYLINDER, "Gövde", V3(0f, 0.35f, 0f), V3(0.16f, 0.7f, 0.16f), WHITE, texture = "nature.bark", texScale = 0.3f, kindM = MaterialKind.MATTE, spec = PrimitiveSpec(PrimitiveKind.CYLINDER, 12, 1, top = 0.7f))
            val leaves = listOf(V3(0f, 0.95f, 0f) to 0.38f, V3(0.25f, 0.85f, 0.1f) to 0.26f, V3(-0.22f, 0.82f, -0.1f) to 0.27f, V3(0.05f, 1.2f, -0.05f) to 0.26f)
            mesh("Yapraklar", Mesh.concat(leaves.map { (p, r) -> G.sphere(p, r, 12, 6) }), 0xFF43A047.toInt(), kind = MaterialKind.MATTE, smooth = true)
        },
    )

    @Suppress("unused")
    private fun unused() = atan(0.0)
}

private operator fun Mesh.plus(o: Mesh): Mesh = Mesh.concat(listOf(this, o))
