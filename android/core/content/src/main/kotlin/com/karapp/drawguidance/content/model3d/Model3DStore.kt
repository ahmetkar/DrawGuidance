package com.karapp.drawguidance.content.model3d

/**
 * v24 3B modellerin saklama biçimi: GLB ([Gltf3]; düzenleme bilgileri "extras"ta). v23'ün "D3" ikili biçimi
 * ([Model3DCodec]) yalnız okunur (eski kayıtlar ve tuvallerdeki eski 3B şekiller).
 */
object Model3DStore {

    const val MIME = Gltf3.MIME

    /** Eski (v23) tuval 3B şekil verisinin türü. */
    const val LEGACY_MIME = "application/x-dg-model3d"

    fun encode(model: Model3D): ByteArray = Gltf3.write(model)

    fun decode(data: ByteArray): Model3D =
        if (Gltf3.isGlb(data)) Gltf3.read(data) ?: throw GltfException("Uygulamanın 3B modeli değil")
        else Model3DCodec.decode(data)

    /** Veri eski biçimde mi (yeniden yazılmalı). */
    fun isLegacy(data: ByteArray): Boolean = !Gltf3.isGlb(data)
}
