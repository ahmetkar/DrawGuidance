import Foundation

/// v23 "D3" ikili 3B model biçimi (Android `Model3DCodec`): "D3" + sürüm + ham DEFLATE gövde.
enum Model3DCodec {
    private static let M0: UInt8 = 0x44, M1: UInt8 = 0x33, VERSION: UInt8 = 1

    struct FormatError: Error, CustomStringConvertible { let description: String }

    static func encode(_ model: Model3D) -> Data {
        var w = W()
        w.str(model.id); w.str(model.name)
        let s = model.scene
        w.f(s.light.yaw); w.f(s.light.pitch); w.f(s.light.intensity); w.f(s.light.ambient); w.i(s.light.color)
        w.b(s.shadow ? 1 : 0); w.b(s.edges ? 1 : 0); w.i(s.edgeColor); w.i(s.background); w.b(s.ground ? 1 : 0)
        let c = model.camera
        w.f(c.yaw); w.f(c.pitch); w.f(c.persp); w.f(c.zoom); w.v3(c.target)
        w.u(model.parts.count)
        for p in model.parts {
            w.str(p.id); w.str(p.name)
            if let prim = p.primitive {
                w.b(1); w.b(prim.kind.rawValue); w.u(prim.segments); w.u(prim.rings); w.f(prim.top); w.f(prim.thickness)
            } else { w.b(0) }
            w.v3(p.position); w.v3(p.rotation); w.v3(p.scale)
            material(&w, p.material)
            w.u(p.faceMaterials.count)
            for (f, m) in p.faceMaterials.sorted(by: { $0.key < $1.key }) { w.u(f); material(&w, m) }
            w.b((p.smooth ? 1 : 0) | (p.visible ? 2 : 0))
            w.b(p.uv.rawValue)
            w.u(p.mesh.vertexCount)
            for v in p.mesh.pos { w.f(v) }
            w.u(p.mesh.faceCount)
            for face in p.mesh.faces { w.u(face.count); for v in face { w.u(v) } }
        }
        w.u(model.strokes.count)
        for st in model.strokes {
            w.str(st.id); w.i(st.color); w.f(st.width); w.u(st.count)
            for v in st.points { w.f(v) }
        }
        return Data([M0, M1, VERSION]) + ContentCodec.deflate(w.data)
    }

    static func decode(_ data: Data) throws -> Model3D {
        let d = [UInt8](data)
        guard d.count >= 3, d[0] == M0, d[1] == M1 else { throw FormatError(description: "3B model verisi değil") }
        guard d[2] == VERSION else { throw FormatError(description: "Desteklenmeyen 3B model sürümü: \(d[2])") }
        var r = R(try [UInt8](ContentCodec.inflate(Data(d[3...]))))
        let id = try r.str(), name = try r.str()
        let light = Light(yaw: try r.f(), pitch: try r.f(), intensity: try r.f(), ambient: try r.f(), color: try r.i())
        let shadow = try r.b() == 1, edges = try r.b() == 1, edgeColor = try r.i(), bg = try r.i(), ground = try r.b() == 1
        let camera = Camera3(yaw: try r.f(), pitch: try r.f(), persp: try r.f(), zoom: try r.f(), target: try r.v3())
        var parts: [Part] = []
        for _ in 0..<(try r.u()) {
            let pid = try r.str(), pname = try r.str()
            var prim: PrimitiveSpec?
            if try r.b() == 1 {
                let kind = PrimitiveKind(rawValue: try r.b()) ?? .CUBE
                prim = PrimitiveSpec(kind, try r.u(), try r.u(), top: try r.f(), thickness: try r.f())
            }
            let pos = try r.v3(), rot = try r.v3(), scale = try r.v3()
            let mat = try material(&r)
            var faceMats: [Int: Material] = [:]
            for _ in 0..<(try r.u()) { let k = try r.u(); faceMats[k] = try material(&r) }
            let flags = try r.b()
            let uv = UvMode(rawValue: try r.b()) ?? .BOX
            let nv = try r.u()
            var p: [Float] = []
            p.reserveCapacity(nv * 3)
            for _ in 0..<(nv * 3) { p.append(try r.f()) }
            var faces: [[Int]] = []
            for _ in 0..<(try r.u()) {
                let k = try r.u()
                var face: [Int] = []
                for _ in 0..<k { face.append(try r.u()) }
                faces.append(face)
            }
            parts.append(Part(pid, pname, Mesh(p, faces), prim, position: pos, rotation: rot, scale: scale, material: mat,
                              faceMaterials: faceMats, smooth: flags & 1 != 0, uv: uv, visible: flags & 2 != 0))
        }
        var strokes: [Stroke3] = []
        for _ in 0..<(try r.u()) {
            let sid = try r.str(), color = try r.i(), width = try r.f(), n = try r.u()
            var pts: [Float] = []
            for _ in 0..<(n * 3) { pts.append(try r.f()) }
            strokes.append(Stroke3(sid, pts, color, width))
        }
        return Model3D(id, name, parts, strokes: strokes,
                       scene: SceneSettings(light: light, shadow: shadow, edges: edges, edgeColor: edgeColor, background: bg, ground: ground),
                       camera: camera)
    }

    private static func material(_ w: inout W, _ m: Material) {
        w.i(m.color); w.str(m.texture ?? ""); w.f(m.textureScale); w.f(m.opacity); w.b(m.kind.rawValue); w.f(m.shine)
    }

    private static func material(_ r: inout R) throws -> Material {
        let color = try r.i(); let tex = try r.str(); let scale = try r.f(); let opacity = try r.f()
        let kind = MaterialKind(rawValue: try r.b()) ?? .PLASTIC
        return Material(color: color, texture: tex.isEmpty ? nil : tex, textureScale: scale, opacity: opacity, kind: kind, shine: try r.f())
    }

    private struct W {
        var data = Data()
        mutating func b(_ v: Int) { data.append(UInt8(truncatingIfNeeded: v)) }
        mutating func i(_ v: Int32) { let u = UInt32(bitPattern: v); b(Int(u >> 24)); b(Int(u >> 16)); b(Int(u >> 8)); b(Int(u)) }
        mutating func f(_ v: Float) { i(Int32(bitPattern: v.bitPattern)) }
        mutating func v3(_ v: V3) { f(v.x); f(v.y); f(v.z) }
        mutating func u(_ value: Int) {
            var v = UInt64(UInt32(truncatingIfNeeded: value))
            while v & ~0x7F != 0 { b(Int((v & 0x7F) | 0x80)); v >>= 7 }
            b(Int(v))
        }
        mutating func str(_ s: String) { let bytes = Array(s.utf8); u(bytes.count); data.append(contentsOf: bytes) }
    }

    private struct R {
        let d: [UInt8]
        var p = 0
        init(_ d: [UInt8]) { self.d = d }
        mutating func b() throws -> Int {
            guard p < d.count else { throw FormatError(description: "Eksik 3B model verisi") }
            defer { p += 1 }
            return Int(d[p])
        }
        mutating func i() throws -> Int32 {
            let a = try b(), c = try b(), e = try b(), g = try b()
            return Int32(bitPattern: UInt32(a << 24 | c << 16 | e << 8 | g))
        }
        mutating func f() throws -> Float { Float(bitPattern: UInt32(bitPattern: try i())) }
        mutating func v3() throws -> V3 { V3(try f(), try f(), try f()) }
        mutating func u() throws -> Int {
            var shift = 0; var result: UInt64 = 0
            while true {
                let x = try b()
                if shift < 64 { result |= UInt64(x & 0x7F) << UInt64(shift) }
                if x & 0x80 == 0 { return Int(Int32(truncatingIfNeeded: result)) }
                shift += 7
            }
        }
        mutating func str() throws -> String {
            let n = try u()
            guard n >= 0, p + n <= d.count else { throw FormatError(description: "Eksik 3B model verisi") }
            let s = String(decoding: d[p..<(p + n)], as: UTF8.self)
            p += n
            return s
        }
    }
}
