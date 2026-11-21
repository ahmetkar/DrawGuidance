import Foundation

/// v56 3B çizimlerin saklama biçimi: kompakt **D3 v2** (`Model3DCodec`). Eski kayıtlar GLB'dir (`Gltf3`; düzenleme
/// bilgileri "extras"ta); okunurken çevrilir ([fromLegacy]: parçalar kilitli cisim olur). Tuvaldeki 3B şeklin verisi
/// eski sürümlerle uyum için GLB olarak yazılır ([encodeShape]). Android `Model3DStore` ile aynı.
enum Model3DStore {

    static let MIME = Gltf3.MIME

    /// v56 3B çizimin (D3 v2) türü.
    static let SKETCH_MIME = "application/x-dg-model3d"

    /// Eski (v23) tuval 3B şekil verisinin türü.
    static let LEGACY_MIME = SKETCH_MIME

    /// 3B çizim kaydı (D3 v2).
    static func encode(_ model: Model3D) -> Data { Model3DCodec.encode(model) }

    /// Tuvaldeki 3B şeklin verisi (GLB; eski sürümler de okur).
    static func encodeShape(_ model: Model3D) -> Data { Data(Gltf3.write(model)) }

    static func decode(_ data: Data) throws -> Model3D {
        let bytes = [UInt8](data)
        if Gltf3.isGlb(bytes) {
            guard let m = try Gltf3.read(bytes) else { throw GltfException("Uygulamanın 3B modeli değil") }
            return m
        }
        return try Model3DCodec.decode(data)
    }

    /// Veri eski biçimde mi (GLB ya da D3 v1; 3B Çizim'de açılınca çevrilir).
    static func isLegacy(_ data: Data) -> Bool {
        if Gltf3.isGlb(data) { return true }
        return data.count > 2 && data[data.startIndex + 2] < 2
    }

    /// v56 eski 3B Şekillerim kaydının çevrilmesi: modelleme parçaları kilitli cisim olur; hazır cisimler cisim kalır.
    static func fromLegacy(_ model: Model3D) -> Model3D {
        var c = model
        c.parts = model.parts.map { p in
            var q = p
            if p.primitive == nil { q.locked = true }
            return q
        }
        return c
    }
}
