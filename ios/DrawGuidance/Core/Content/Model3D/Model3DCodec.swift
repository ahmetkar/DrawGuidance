import Foundation

/// v23 "D3" ikili 3B model biçimi (Android `Model3DCodec`): "D3" + sürüm + ham DEFLATE gövde.
/// v56 sürüm 2 (3B Çizim): parça başına kilit, katman, grup ve doku koordinatları; çizgi başına zaman, nokta kalınlıkları,
/// örtücülük, fırça, düzlem ve katman; malzemede gradyan; sonda katmanlar, son düzlem ve gruplar. Sürüm 1 okunur.
/// Android ve iOS aynı ham gövde baytlarını yazar ([rawBody]; ortak SHA-256 sınaması).
enum Model3DCodec {
    private static let M0: UInt8 = 0x44, M1: UInt8 = 0x33, VERSION: UInt8 = 2

    struct FormatError: Error, CustomStringConvertible { let description: String }

    static func encode(_ model: Model3D) -> Data { Data([M0, M1, VERSION]) + ContentCodec.deflate(rawBody(model)) }

    /// Sıkıştırılmamış gövde (Android ve iOS'un aynı baytları yazdığını sınamak için).
    static func rawBody(_ model: Model3D) -> Data {
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
            w.b((p.smooth ? 1 : 0) | (p.visible ? 2 : 0) | (p.locked ? 4 : 0))
            w.b(p.uv.rawValue)
            w.str(p.layer ?? ""); w.str(p.group ?? "")
            w.u(p.mesh.vertexCount)
            for v in p.mesh.pos { w.f(v) }
            w.u(p.mesh.faceCount)
            for face in p.mesh.faces { w.u(face.count); for v in face { w.u(v) } }
            if let uvs = p.mesh.uvs {
                w.b(1)
                for uv in uvs { w.u(uv.count); for x in uv { w.f(x) } }
            } else { w.b(0) }
        }
        w.u(model.strokes.count)
        for st in model.strokes {
            w.str(st.id); w.i(st.color); w.f(st.width); w.u(st.count)
            for v in st.points { w.f(v) }
            let t = UInt64(bitPattern: st.time)
            w.i(Int32(bitPattern: UInt32(truncatingIfNeeded: t >> 32))); w.i(Int32(bitPattern: UInt32(truncatingIfNeeded: t)))
            w.f(st.opacity); w.b(st.brush); w.str(st.plane ?? ""); w.str(st.layer ?? "")
            if let ws = st.widths, ws.count == st.count {
                w.b(1); for x in ws { w.f(x) }
            } else { w.b(0) }
        }
        w.u(model.layers.count)
        for l in model.layers { w.str(l.id); w.str(l.name); w.b((l.visible ? 1 : 0) | (l.locked ? 2 : 0)) }
        if let pl = model.plane {
            w.b(1); w.v3(pl.origin); w.v3(pl.u); w.v3(pl.v); w.str(pl.id)
        } else { w.b(0) }
        w.u(model.groups.count)
        for g in model.groups { w.str(g.id); w.str(g.name) }
        return w.data
    }

    static func decode(_ data: Data) throws -> Model3D {
        let d = [UInt8](data)
        guard d.count >= 3, d[0] == M0, d[1] == M1 else { throw FormatError(description: "3B model verisi değil") }
        let version = d[2]
        guard version >= 1, version <= VERSION else { throw FormatError(description: "Desteklenmeyen 3B model sürümü: \(d[2])") }
        let v2 = version >= 2
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
            let mat = try material(&r, v2)
            var faceMats: [Int: Material] = [:]
            for _ in 0..<(try r.u()) { let k = try r.u(); faceMats[k] = try material(&r, v2) }
            let flags = try r.b()
            let uv = UvMode(rawValue: try r.b()) ?? .BOX
            var layer: String? = nil, group: String? = nil
            if v2 {
                let l = try r.str(); layer = l.isEmpty ? nil : l
                let g = try r.str(); group = g.isEmpty ? nil : g
            }
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
            var uvs: [[Float]]? = nil
            if v2, try r.b() == 1 {
                var all: [[Float]] = []
                for _ in 0..<faces.count {
                    let k = try r.u()
                    var uvf: [Float] = []
                    for _ in 0..<k { uvf.append(try r.f()) }
                    all.append(uvf)
                }
                uvs = all
            }
            parts.append(Part(pid, pname, Mesh(p, faces, uvs), prim, position: pos, rotation: rot, scale: scale, material: mat,
                              faceMaterials: faceMats, smooth: flags & 1 != 0, uv: uv, visible: flags & 2 != 0,
                              group: group, locked: flags & 4 != 0, layer: layer))
        }
        var strokes: [Stroke3] = []
        for _ in 0..<(try r.u()) {
            let sid = try r.str(), color = try r.i(), width = try r.f(), n = try r.u()
            var pts: [Float] = []
            pts.reserveCapacity(n * 3)
            for _ in 0..<(n * 3) { pts.append(try r.f()) }
            if !v2 { strokes.append(Stroke3(sid, pts, color, width)); continue }
            let hi = UInt64(UInt32(bitPattern: try r.i())), lo = UInt64(UInt32(bitPattern: try r.i()))
            let time = Int64(bitPattern: hi << 32 | lo)
            let opacity = try r.f(), brush = try r.b()
            let pl = try r.str(), ly = try r.str()
            var widths: [Float]? = nil
            if try r.b() == 1 {
                var ws: [Float] = []
                for _ in 0..<n { ws.append(try r.f()) }
                widths = ws
            }
            strokes.append(Stroke3(sid, pts, color, width, time: time, widths: widths, opacity: opacity, brush: brush,
                                   plane: pl.isEmpty ? nil : pl, layer: ly.isEmpty ? nil : ly))
        }
        var layers: [Layer3] = [], plane: Plane3? = nil, groups: [Group3] = []
        if v2 {
            for _ in 0..<(try r.u()) {
                let lid = try r.str(), ln = try r.str(), f = try r.b()
                layers.append(Layer3(lid, ln, visible: f & 1 != 0, locked: f & 2 != 0))
            }
            if try r.b() == 1 {
                let o = try r.v3(), u = try r.v3(), v = try r.v3()
                plane = Plane3(o, u, v, id: try r.str())
            }
            for _ in 0..<(try r.u()) { let gid = try r.str(); groups.append(Group3(id: gid, name: try r.str())) }
        }
        return Model3D(id, name, parts, strokes: strokes,
                       scene: SceneSettings(light: light, shadow: shadow, edges: edges, edgeColor: edgeColor, background: bg, ground: ground),
                       camera: camera, groups: groups, layers: layers, plane: plane)
    }

    private static func material(_ w: inout W, _ m: Material) {
        w.i(m.color); w.str(m.texture ?? ""); w.f(m.textureScale); w.f(m.opacity); w.b(m.kind.rawValue); w.f(m.shine)
        if let g = m.gradient { w.b(1); w.i(g); w.b(m.gradientAxis) } else { w.b(0) }
    }

    private static func material(_ r: inout R, _ v2: Bool) throws -> Material {
        let color = try r.i(); let tex = try r.str(); let scale = try r.f(); let opacity = try r.f()
        let kind = MaterialKind(rawValue: try r.b()) ?? .PLASTIC
        let shine = try r.f()
        var gradient: Int32? = nil, axis = 1
        if v2, try r.b() == 1 { gradient = try r.i(); axis = try r.b() }
        return Material(color: color, texture: tex.isEmpty ? nil : tex, textureScale: scale, opacity: opacity, kind: kind, shine: shine,
                        gradient: gradient, gradientAxis: axis)
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
