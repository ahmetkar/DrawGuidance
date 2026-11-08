package com.karapp.drawguidance.content.model3d

/**
 * v23 hazır örnek 3B modeller (3B Şekillerim'de ilk açılışta görünür; düzenlenebilir ve silinebilir). Hepsi hazır
 * cisimlerden kurulur: parçaların konumu, döndürmesi, ölçeği ve malzemesi (renk/doku) örnektir.
 */
object Samples3D {

    const val VERSION = 1

    private class B(val prefix: String) {
        val parts = ArrayList<Part>()
        fun add(
            kind: PrimitiveKind, name: String, pos: V3, scale: V3, color: Int,
            rot: V3 = V3.ZERO, texture: String? = null, texScale: Float = 1f, kindM: MaterialKind = MaterialKind.PLASTIC,
            shine: Float = 0.4f, opacity: Float = 1f, spec: PrimitiveSpec = Primitives.defaultSpec(kind),
        ) {
            parts += Part(
                id = "$prefix-${parts.size}", name = name, mesh = Primitives.build(spec), primitive = spec,
                position = pos, rotation = rot, scale = scale,
                material = Material(color, texture, texScale, opacity, kindM, shine),
                smooth = kind.smoothByDefault, uv = kind.uvByDefault,
            )
        }
    }

    private fun model(id: String, name: String, camera: Camera3 = Camera3(), build: B.() -> Unit): Model3D {
        val b = B(id).apply(build)
        return Model3D(id, name, b.parts, camera = camera)
    }

    private const val WHITE = 0xFFFFFFFF.toInt()

    val all: List<Model3D> by lazy {
        listOf(
            model("sample-table", "Masa") {
                add(PrimitiveKind.CUBE, "Tabla", V3(0f, 0.78f, 0f), V3(1.6f, 0.08f, 0.9f), WHITE, texture = "wood.oak", texScale = 0.8f)
                for ((i, p) in listOf(V3(-0.7f, 0.37f, -0.35f), V3(0.7f, 0.37f, -0.35f), V3(-0.7f, 0.37f, 0.35f), V3(0.7f, 0.37f, 0.35f)).withIndex()) {
                    add(PrimitiveKind.CYLINDER, "Ayak ${i + 1}", p, V3(0.08f, 0.74f, 0.08f), WHITE, texture = "wood.walnut", texScale = 0.5f)
                }
            },
            model("sample-house", "Ev", Camera3(yaw = 30f, pitch = 18f, target = V3(0f, 0.7f, 0f))) {
                add(PrimitiveKind.CUBE, "Duvarlar", V3(0f, 0.45f, 0f), V3(1.3f, 0.9f, 1f), WHITE, texture = "stone.brick", texScale = 0.6f, kindM = MaterialKind.MATTE)
                add(PrimitiveKind.PYRAMID, "Çatı", V3(0f, 1.2f, 0f), V3(1.55f, 0.6f, 1.2f), 0xFFB23A2E.toInt(), kindM = MaterialKind.MATTE)
                add(PrimitiveKind.CUBE, "Kapı", V3(0f, 0.3f, 0.5f), V3(0.28f, 0.6f, 0.04f), WHITE, texture = "wood.planks", texScale = 0.4f)
                add(PrimitiveKind.CUBE, "Pencere 1", V3(-0.42f, 0.55f, 0.5f), V3(0.26f, 0.24f, 0.03f), 0xFF9FD3F2.toInt(), kindM = MaterialKind.GLASS, opacity = 0.7f)
                add(PrimitiveKind.CUBE, "Pencere 2", V3(0.42f, 0.55f, 0.5f), V3(0.26f, 0.24f, 0.03f), 0xFF9FD3F2.toInt(), kindM = MaterialKind.GLASS, opacity = 0.7f)
                add(PrimitiveKind.CUBE, "Baca", V3(0.38f, 1.35f, -0.15f), V3(0.16f, 0.4f, 0.16f), WHITE, texture = "stone.brick", texScale = 0.3f, kindM = MaterialKind.MATTE)
            },
            model("sample-snowman", "Kardan adam", Camera3(yaw = 20f, pitch = 12f, target = V3(0f, 0.75f, 0f))) {
                add(PrimitiveKind.SPHERE, "Gövde", V3(0f, 0.36f, 0f), V3(0.72f, 0.72f, 0.72f), 0xFFF7FAFC.toInt(), kindM = MaterialKind.MATTE)
                add(PrimitiveKind.SPHERE, "Göğüs", V3(0f, 0.9f, 0f), V3(0.52f, 0.52f, 0.52f), 0xFFF7FAFC.toInt(), kindM = MaterialKind.MATTE)
                add(PrimitiveKind.SPHERE, "Baş", V3(0f, 1.3f, 0f), V3(0.36f, 0.36f, 0.36f), 0xFFF7FAFC.toInt(), kindM = MaterialKind.MATTE)
                add(PrimitiveKind.CONE, "Burun", V3(0f, 1.3f, 0.26f), V3(0.07f, 0.22f, 0.07f), 0xFFF28C28.toInt(), rot = V3(90f, 0f, 0f))
                add(PrimitiveKind.SPHERE, "Göz 1", V3(-0.07f, 1.37f, 0.16f), V3(0.05f, 0.05f, 0.05f), 0xFF1E1E1E.toInt())
                add(PrimitiveKind.SPHERE, "Göz 2", V3(0.07f, 1.37f, 0.16f), V3(0.05f, 0.05f, 0.05f), 0xFF1E1E1E.toInt())
                add(PrimitiveKind.CYLINDER, "Şapka kenarı", V3(0f, 1.47f, 0f), V3(0.42f, 0.03f, 0.42f), 0xFF22252B.toInt())
                add(PrimitiveKind.CYLINDER, "Şapka", V3(0f, 1.62f, 0f), V3(0.26f, 0.28f, 0.26f), 0xFF22252B.toInt())
                add(PrimitiveKind.TORUS, "Atkı", V3(0f, 1.12f, 0f), V3(0.42f, 0.42f, 0.42f), 0xFFD7263D.toInt(), texture = "fabric.knit", texScale = 0.15f)
            },
            model("sample-rocket", "Roket", Camera3(yaw = 40f, pitch = 15f, target = V3(0f, 0.9f, 0f))) {
                add(PrimitiveKind.CYLINDER, "Gövde", V3(0f, 0.85f, 0f), V3(0.42f, 1.1f, 0.42f), 0xFFECEFF1.toInt(), kindM = MaterialKind.METAL, shine = 0.7f)
                add(PrimitiveKind.CONE, "Burun", V3(0f, 1.65f, 0f), V3(0.42f, 0.5f, 0.42f), 0xFFE53935.toInt())
                add(PrimitiveKind.TORUS, "Pencere çerçevesi", V3(0f, 1.05f, 0.21f), V3(0.2f, 0.2f, 0.2f), 0xFF90A4AE.toInt(), rot = V3(90f, 0f, 0f), kindM = MaterialKind.METAL)
                add(PrimitiveKind.SPHERE, "Pencere", V3(0f, 1.05f, 0.2f), V3(0.13f, 0.13f, 0.06f), 0xFF64B5F6.toInt(), kindM = MaterialKind.GLASS, opacity = 0.8f)
                for (i in 0 until 3) {
                    val a = Math.toRadians(120.0 * i)
                    add(
                        PrimitiveKind.WEDGE, "Kanat ${i + 1}", V3((0.27 * kotlin.math.sin(a)).toFloat(), 0.36f, (0.27 * kotlin.math.cos(a)).toFloat()),
                        V3(0.04f, 0.42f, 0.3f), 0xFFE53935.toInt(), rot = V3(0f, (120f * i), 0f),
                    )
                }
                add(PrimitiveKind.CONE, "Motor", V3(0f, 0.22f, 0f), V3(0.3f, 0.18f, 0.3f), 0xFF546E7A.toInt(), kindM = MaterialKind.METAL, spec = PrimitiveSpec(PrimitiveKind.CONE, 32, 1, top = 0.6f))
            },
            model("sample-tree", "Ağaç", Camera3(yaw = 30f, pitch = 14f, target = V3(0f, 0.9f, 0f))) {
                add(PrimitiveKind.CYLINDER, "Gövde", V3(0f, 0.4f, 0f), V3(0.22f, 0.8f, 0.22f), WHITE, texture = "nature.bark", texScale = 0.35f, kindM = MaterialKind.MATTE,
                    spec = PrimitiveSpec(PrimitiveKind.CYLINDER, 16, 1, top = 0.75f))
                add(PrimitiveKind.CONE, "Yapraklar 1", V3(0f, 1.0f, 0f), V3(1.1f, 0.7f, 1.1f), 0xFF2E7D32.toInt(), kindM = MaterialKind.MATTE)
                add(PrimitiveKind.CONE, "Yapraklar 2", V3(0f, 1.38f, 0f), V3(0.85f, 0.6f, 0.85f), 0xFF388E3C.toInt(), kindM = MaterialKind.MATTE)
                add(PrimitiveKind.CONE, "Yapraklar 3", V3(0f, 1.72f, 0f), V3(0.6f, 0.5f, 0.6f), 0xFF43A047.toInt(), kindM = MaterialKind.MATTE)
            },
            model("sample-mug", "Kupa", Camera3(yaw = 25f, pitch = 22f, target = V3(0f, 0.35f, 0f))) {
                add(PrimitiveKind.TUBE, "Gövde", V3(0f, 0.35f, 0f), V3(0.6f, 0.7f, 0.6f), 0xFF1E88E5.toInt(), shine = 0.8f,
                    spec = PrimitiveSpec(PrimitiveKind.TUBE, 40, 1, thickness = 0.12f))
                add(PrimitiveKind.CYLINDER, "Taban", V3(0f, 0.04f, 0f), V3(0.6f, 0.08f, 0.6f), 0xFF1E88E5.toInt(), shine = 0.8f)
                add(PrimitiveKind.TORUS, "Kulp", V3(0.36f, 0.38f, 0f), V3(0.34f, 0.34f, 0.34f), 0xFF1E88E5.toInt(), rot = V3(90f, 0f, 0f), shine = 0.8f,
                    spec = PrimitiveSpec(PrimitiveKind.TORUS, 24, 12, thickness = 0.35f))
            },
        )
    }
}
