package com.karapp.drawguidance.content.model3d

/**
 * v56 3B çizimlerin saklama biçimi: kompakt **D3 v2** ([Model3DCodec]). Eski kayıtlar GLB'dir ([Gltf3]; düzenleme
 * bilgileri "extras"ta); okunurken çevrilir ([fromLegacy]: parçalar kilitli cisim olur). Tuvaldeki 3B şeklin verisi
 * eski sürümlerle uyum için GLB olarak yazılır ([encodeShape]).
 */
object Model3DStore {

    const val MIME = Gltf3.MIME

    /** v56 3B çizimin (D3 v2) türü. */
    const val SKETCH_MIME = "application/x-dg-model3d"

    /** Eski (v23) tuval 3B şekil verisinin türü. */
    const val LEGACY_MIME = SKETCH_MIME

    /** 3B çizim kaydı (D3 v2). */
    fun encode(model: Model3D): ByteArray = Model3DCodec.encode(model)

    /** Tuvaldeki 3B şeklin verisi (GLB; eski sürümler de okur). */
    fun encodeShape(model: Model3D): ByteArray = Gltf3.write(model)

    fun decode(data: ByteArray): Model3D =
        if (Gltf3.isGlb(data)) Gltf3.read(data) ?: throw GltfException("Uygulamanın 3B modeli değil")
        else Model3DCodec.decode(data)

    /** Veri eski biçimde mi (GLB ya da D3 v1; 3B Çizim'de açılınca çevrilir). */
    fun isLegacy(data: ByteArray): Boolean = Gltf3.isGlb(data) || (data.size > 2 && data[2].toInt() < 2)

    /**
     * v56 eski 3B Şekillerim kaydının 3B Çizim'e çevrilmesi: modelleme parçaları (ağı elle düzenlenmiş ya da fırçayla
     * çizilmiş) kilitli cisim olur; hazır cisimler cisim olarak kalır. Çizgiler ve sahne korunur.
     */
    fun fromLegacy(model: Model3D): Model3D =
        model.copy(parts = model.parts.map { if (it.primitive == null) it.copy(locked = true) else it })
}
