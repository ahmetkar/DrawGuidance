import Foundation

/// v24 3B modellerin saklama biçimi: GLB (`Gltf3`; düzenleme bilgileri "extras"ta). v23'ün "D3" ikili biçimi
/// (`Model3DCodec`) yalnız okunur (eski kayıtlar ve tuvallerdeki eski 3B şekiller).
enum Model3DStore {

    static let MIME = Gltf3.MIME

    /// Eski (v23) tuval 3B şekil verisinin türü.
    static let LEGACY_MIME = "application/x-dg-model3d"

    static func encode(_ model: Model3D) -> Data { Data(Gltf3.write(model)) }

    static func decode(_ data: Data) throws -> Model3D {
        let bytes = [UInt8](data)
        if Gltf3.isGlb(bytes) {
            guard let m = try Gltf3.read(bytes) else { throw GltfException("Uygulamanın 3B modeli değil") }
            return m
        }
        return try Model3DCodec.decode(data)
    }

    /// Veri eski biçimde mi (yeniden yazılmalı).
    static func isLegacy(_ data: Data) -> Bool { !Gltf3.isGlb(data) }
}
