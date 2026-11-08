import Foundation

/// glTF okunamadı (bozuk dosya ya da desteklenmeyen özellik).
struct GltfException: Error, CustomStringConvertible {
    let message: String
    init(_ message: String) { self.message = message }
    var description: String { message }
}

/// GLB yazma ayarları: dokular PNG olarak gömülsün mü (dışa aktarma) ve doku kimliğinden doku.
struct GltfWriteOptions {
    var embedTextures: Bool = false
    var textures: (String) -> Texture? = { _ in nil }
}

/// İçe aktarılan modelin gömülü dokusu (uygulamada "imp.…" kimliğiyle saklanır).
struct ImportedTexture { let id: String; let name: String; let texture: Texture }

/// İçe aktarma sonucu: model, dokuları, kaynak ve sonuç üçgen sayıları.
struct GltfImport { let model: Model3D; let textures: [ImportedTexture]; let sourceTriangles: Int; let triangles: Int }

/// Küçük uçlu bayt okuyucu (Java `ByteBuffer` dilimi).
struct LEBytes {
    let b: [UInt8]
    let off: Int
    let limit: Int
    var cursor = 0

    init(_ b: [UInt8], _ off: Int, _ len: Int) { self.b = b; self.off = off; self.limit = len }

    func u8(_ p: Int) -> UInt8 { b[off + p] }
    func i32(_ p: Int) -> Int32 {
        let o = off + p
        return Int32(bitPattern: UInt32(b[o]) | UInt32(b[o + 1]) << 8 | UInt32(b[o + 2]) << 16 | UInt32(b[o + 3]) << 24)
    }
    func i16(_ p: Int) -> Int16 { Int16(bitPattern: UInt16(b[off + p]) | UInt16(b[off + p + 1]) << 8) }
    func f32(_ p: Int) -> Float { Float(bitPattern: UInt32(bitPattern: i32(p))) }

    mutating func nextInt() throws -> Int32 {
        if cursor + 4 > limit { throw GltfException("Bozuk veri") }
        defer { cursor += 4 }
        return i32(cursor)
    }
    mutating func nextFloat() throws -> Float { Float(bitPattern: UInt32(bitPattern: try nextInt())) }
    var bytes: [UInt8] { Array(b[off..<off + limit]) }
}

/// Küçük uçlu bayt yazıcı.
struct LEWriter {
    var d: [UInt8] = []
    mutating func int(_ v: Int32) { let u = UInt32(bitPattern: v); d += [UInt8(u & 0xFF), UInt8(u >> 8 & 0xFF), UInt8(u >> 16 & 0xFF), UInt8(u >> 24)] }
    mutating func int(_ v: Int) { int(Int32(truncatingIfNeeded: v)) }
    mutating func short(_ v: Int) { let u = UInt16(truncatingIfNeeded: v); d += [UInt8(u & 0xFF), UInt8(u >> 8)] }
    mutating func float(_ f: Float) { int(Int32(bitPattern: f.bitPattern)) }
}

/// v24 3B modellerin glTF 2.0 / GLB biçimi (Android `Gltf3`; dış kütüphane yok). Uygulamaya özgü düzenleme bilgileri
/// "extras" alanında ve ayrı tampon görünümünde durur: başka programlar dosyayı normal GLB olarak açar, uygulama kayıpsız
/// geri okur. Başka programların dosyaları `importModel` ile okunur.
enum Gltf3 {

    static let MIME = "model/gltf-binary"
    private static let MAGIC: Int32 = 0x4654_6C67
    private static let JSON_CHUNK: Int32 = 0x4E4F_534A
    private static let BIN_CHUNK: Int32 = 0x004E_4942
    private static let FLOAT = 5126, UBYTE = 5121, USHORT = 5123, UINT = 5125, BYTE = 5120, SHORT = 5122
    private static let ARRAY_BUFFER = 34962, ELEMENT_ARRAY_BUFFER = 34963
    private static let DG_VERSION = 1

    static func isGlb(_ data: [UInt8]) -> Bool { data.count >= 12 && le(data, 0) == MAGIC }
    static func isGlb(_ data: Data) -> Bool { isGlb([UInt8](data.prefix(12))) }

    // MARK: yazma

    static func write(_ model: Model3D, _ options: GltfWriteOptions = GltfWriteOptions()) -> [UInt8] { Writer(model, options).build() }

    private struct VKey: Hashable { let v: Int, nx: UInt32, ny: UInt32, nz: UInt32, u: UInt32, w: UInt32 }

    private final class Writer {
        let model: Model3D, options: GltfWriteOptions
        var bin: [UInt8] = []
        var views: [JV] = [], accessors: [JV] = [], materials: [JV] = []
        var materialIndex: [Material: Int] = [:]
        var images: [JV] = [], textures: [JV] = []
        var textureIndex: [String: Int] = [:]
        var meshes: [JV] = [], nodes: [JV] = []
        var unlit = false

        init(_ model: Model3D, _ options: GltfWriteOptions) { self.model = model; self.options = options }

        func view(_ bytes: [UInt8], _ target: Int?) -> Int {
            while bin.count % 4 != 0 { bin.append(0) }
            let offset = bin.count
            bin += bytes
            var m: [(String, JV)] = [("buffer", .i(0)), ("byteOffset", .i(offset)), ("byteLength", .i(bytes.count))]
            if let t = target { m.append(("target", .i(t))) }
            views.append(.obj(m))
            return views.count - 1
        }

        func floats(_ data: [Float]) -> [UInt8] { var w = LEWriter(); w.d.reserveCapacity(data.count * 4); for f in data { w.float(f) }; return w.d }

        func accessor(_ view: Int, _ componentType: Int, _ count: Int, _ type: String, _ min: [Float]? = nil, _ max: [Float]? = nil) -> Int {
            var m: [(String, JV)] = [("bufferView", .i(view)), ("componentType", .i(componentType)), ("count", .i(count)), ("type", .s(type))]
            if let mn = min { m.append(("min", .arr(mn.map { .f($0) }))) }
            if let mx = max { m.append(("max", .arr(mx.map { .f($0) }))) }
            accessors.append(.obj(m))
            return accessors.count - 1
        }

        func vec3(_ data: [Float], _ withBounds: Bool) -> Int {
            let n = data.count / 3
            var mn: [Float]? = nil, mx: [Float]? = nil
            if withBounds {
                var a: [Float] = [.greatestFiniteMagnitude, .greatestFiniteMagnitude, .greatestFiniteMagnitude]
                var b: [Float] = [-.greatestFiniteMagnitude, -.greatestFiniteMagnitude, -.greatestFiniteMagnitude]
                for i in 0..<n { for k in 0...2 { a[k] = Swift.min(a[k], data[3 * i + k]); b[k] = Swift.max(b[k], data[3 * i + k]) } }
                if n == 0 { a = [0, 0, 0]; b = [0, 0, 0] }
                mn = a; mx = b
            }
            return accessor(view(floats(data), ARRAY_BUFFER), FLOAT, n, "VEC3", mn, mx)
        }

        func indices(_ idx: [Int], _ vertexCount: Int) -> Int {
            var w = LEWriter()
            if vertexCount <= 65535 {
                for i in idx { w.short(i) }
                return accessor(view(w.d, ELEMENT_ARRAY_BUFFER), USHORT, idx.count, "SCALAR")
            }
            for i in idx { w.int(i) }
            return accessor(view(w.d, ELEMENT_ARRAY_BUFFER), UINT, idx.count, "SCALAR")
        }

        func textureFor(_ id: String) -> Int? {
            if !options.embedTextures { return nil }
            if let t = textureIndex[id] { return t }
            guard let t = options.textures(id) else { return nil }
            let png = [UInt8](PngWriter.encode(t))
            let img = view(png, nil)
            images.append(.obj([("bufferView", .i(img)), ("mimeType", .s("image/png")), ("name", .s(id))]))
            textures.append(.obj([("source", .i(images.count - 1)), ("sampler", .i(0))]))
            textureIndex[id] = textures.count - 1
            return textures.count - 1
        }

        func material(_ mat: Material) -> Int {
            if let i = materialIndex[mat] { return i }
            let r = Float(mat.color >> 16 & 0xFF) / 255, g = Float(mat.color >> 8 & 0xFF) / 255, b = Float(mat.color & 0xFF) / 255
            let alpha = mat.kind == .GLASS ? (mat.opacity * 0.5).coerceIn(0.1, 1) : mat.opacity
            var pbr: [(String, JV)] = [("baseColorFactor", .arr([toLinear(r), toLinear(g), toLinear(b), alpha].map { .f($0) })),
                                       ("metallicFactor", .f(mat.kind == .METAL ? 1 : 0))]
            let rough: Float
            switch mat.kind {
            case .MATTE: rough = 1
            case .PLASTIC: rough = 1 - 0.7 * mat.shine
            case .METAL: rough = 0.6 - 0.5 * mat.shine
            case .GLASS: rough = 0.05
            case .GLOW: rough = 1
            }
            pbr.append(("roughnessFactor", .f(rough)))
            if let id = mat.texture, let ti = textureFor(id) { pbr.append(("baseColorTexture", .obj([("index", .i(ti))]))) }
            var m: [(String, JV)] = [("name", .s(mat.texture ?? String(format: "#%06X", Int(mat.color) & 0xFFFFFF))), ("pbrMetallicRoughness", .obj(pbr))]
            if alpha < 0.999 { m.append(("alphaMode", .s("BLEND"))) }
            if mat.kind == .GLOW { m.append(("emissiveFactor", .arr([toLinear(r), toLinear(g), toLinear(b)].map { .f($0) }))) }
            m.append(("extras", .obj([("dg", materialExtras(mat))])))
            materials.append(.obj(m))
            materialIndex[mat] = materials.count - 1
            return materials.count - 1
        }

        func part(_ p: Part) {
            let mesh = p.mesh
            var primitives: [JV] = []
            if mesh.faceCount > 0 {
                let uvs = UvMapper.compute(p)
                let corners = p.smooth ? mesh.cornerNormals(0.7) : nil
                let fn = mesh.faceNormals
                var order: [Int] = [], groups: [Int: [Int]] = [:]
                for f in 0..<mesh.faceCount {
                    let mi = material(p.materialOf(f))
                    if groups[mi] == nil { order.append(mi); groups[mi] = [] }
                    groups[mi]!.append(f)
                }
                for mi in order {
                    let faces = groups[mi]!
                    let mat = p.materialOf(faces[0])
                    let ts = 1 / mat.textureScale.coerceAtLeast(0.01)
                    var keys: [VKey: Int] = [:]
                    var pos: [Float] = [], nrm: [Float] = [], tex: [Float] = [], idx: [Int] = []
                    for f in faces {
                        let face = mesh.faces[f]
                        for t in mesh.triangles[f] {
                            let v = face[t]
                            let nx: Float, ny: Float, nz: Float
                            if let c = corners { nx = c[f][3 * t]; ny = c[f][3 * t + 1]; nz = c[f][3 * t + 2] } else { nx = fn[3 * f]; ny = fn[3 * f + 1]; nz = fn[3 * f + 2] }
                            let u = uvs[f][2 * t] * ts, w = uvs[f][2 * t + 1] * ts
                            let key = VKey(v: v, nx: nx.ktBits, ny: ny.ktBits, nz: nz.ktBits, u: u.ktBits, w: w.ktBits)
                            if let e = keys[key] { idx.append(e); continue }
                            let k = pos.count / 3
                            pos += [mesh.pos[3 * v], mesh.pos[3 * v + 1], mesh.pos[3 * v + 2]]
                            nrm += [nx, ny, nz]
                            tex += [u, w]
                            keys[key] = k
                            idx.append(k)
                        }
                    }
                    if idx.isEmpty { continue }
                    let n = pos.count / 3
                    let attrs: [(String, JV)] = [("POSITION", .i(vec3(pos, true))), ("NORMAL", .i(vec3(nrm, false))),
                                                 ("TEXCOORD_0", .i(accessor(view(floats(tex), ARRAY_BUFFER), FLOAT, n, "VEC2")))]
                    primitives.append(.obj([("attributes", .obj(attrs)), ("indices", .i(indices(idx, n))), ("material", .i(mi)), ("mode", .i(4))]))
                }
            }
            var node: [(String, JV)] = [("name", .s(p.name))]
            if !primitives.isEmpty {
                meshes.append(.obj([("name", .s(p.name)), ("primitives", .arr(primitives))]))
                node.append(("mesh", .i(meshes.count - 1)))
            }
            node.append(("translation", .arr([p.position.x, p.position.y, p.position.z].map { .f($0) })))
            node.append(("rotation", .arr(Gltf3.quaternion(p.rotation).map { .f($0) })))
            node.append(("scale", .arr([p.scale.x, p.scale.y, p.scale.z].map { .f($0) })))
            node.append(("extras", .obj([("dg", partExtras(p, view(polygonData(mesh), nil)))])))
            nodes.append(.obj(node))
        }

        func partExtras(_ p: Part, _ polyView: Int) -> JV {
            var m: [(String, JV)] = [("id", .s(p.id)), ("name", .s(p.name))]
            if let s = p.primitive {
                m.append(("prim", .obj([("kind", .s("\(s.kind)")), ("segments", .i(s.segments)), ("rings", .i(s.rings)),
                                        ("top", .f(s.top)), ("thickness", .f(s.thickness))])))
            }
            m += [("position", v3(p.position)), ("rotation", v3(p.rotation)), ("scale", v3(p.scale))]
            m.append(("material", .i(material(p.material))))
            if !p.faceMaterials.isEmpty {
                m.append(("faceMaterials", .arr(p.faceMaterials.keys.sorted().map { f in .arr([.i(f), .i(material(p.faceMaterials[f]!))]) })))
            }
            m += [("smooth", .b(p.smooth)), ("uv", .s("\(p.uv)")), ("visible", .b(p.visible))]
            if let g = p.group { m.append(("group", .s(g))) }
            if let b = p.brush { m.append(("brush", brushJson(b))) }
            m.append(("poly", .i(polyView)))
            return .obj(m)
        }

        func stroke(_ s: Stroke3) {
            if s.count < 2 { return }
            unlit = true
            let r = Float(s.color >> 16 & 0xFF) / 255, g = Float(s.color >> 8 & 0xFF) / 255, b = Float(s.color & 0xFF) / 255
            materials.append(.obj([
                ("name", .s("Çizgi")),
                ("pbrMetallicRoughness", .obj([("baseColorFactor", .arr([toLinear(r), toLinear(g), toLinear(b), 1].map { .f($0) })), ("metallicFactor", .f(0))])),
                ("extensions", .obj([("KHR_materials_unlit", .obj([]))])),
            ]))
            let mi = materials.count - 1
            let pa = vec3(s.points, true)
            meshes.append(.obj([("name", .s("Çizgi")), ("primitives", .arr([.obj([("attributes", .obj([("POSITION", .i(pa))])), ("material", .i(mi)), ("mode", .i(3))])]))]))
            let st: JV = .obj([("id", .s(s.id)), ("color", .i(s.color)), ("width", .f(s.width)), ("points", .i(pa)), ("t", .l(s.time))])
            nodes.append(.obj([("name", .s("Çizgi")), ("mesh", .i(meshes.count - 1)), ("extras", .obj([("dg", .obj([("stroke", st)]))]))]))
        }

        func build() -> [UInt8] {
            model.parts.forEach { part($0) }
            model.strokes.forEach { stroke($0) }
            var root: [(String, JV)] = [("asset", .obj([("version", .s("2.0")), ("generator", .s("DrawGuidance")), ("extras", .obj([("dg", modelExtras(model))]))]))]
            if unlit { root.append(("extensionsUsed", .arr([.s("KHR_materials_unlit")]))) }
            root.append(("scene", .i(0)))
            root.append(("scenes", .arr([.obj([("name", .s(model.name)), ("nodes", .arr(nodes.indices.map { .i($0) }))])])))
            root.append(("nodes", .arr(nodes)))
            if !meshes.isEmpty { root.append(("meshes", .arr(meshes))) }
            if !materials.isEmpty { root.append(("materials", .arr(materials))) }
            if !textures.isEmpty {
                root.append(("textures", .arr(textures)))
                root.append(("images", .arr(images)))
                root.append(("samplers", .arr([.obj([("magFilter", .i(9729)), ("minFilter", .i(9987)), ("wrapS", .i(10497)), ("wrapT", .i(10497))])])))
            }
            if !accessors.isEmpty { root.append(("accessors", .arr(accessors))) }
            while bin.count % 4 != 0 { bin.append(0) }
            if !views.isEmpty { root.append(("bufferViews", .arr(views))) }
            root.append(("buffers", .arr([.obj([("byteLength", .i(bin.count))])])))
            let jsonBytes = Array(JV.obj(root).text.utf8)
            let jsonPad = (4 - jsonBytes.count % 4) % 4
            let total = 12 + 8 + jsonBytes.count + jsonPad + 8 + bin.count
            var out = LEWriter()
            out.d.reserveCapacity(total)
            out.int(MAGIC); out.int(2); out.int(total)
            out.int(jsonBytes.count + jsonPad); out.int(JSON_CHUNK); out.d += jsonBytes; out.d += [UInt8](repeating: 0x20, count: jsonPad)
            out.int(bin.count); out.int(BIN_CHUNK); out.d += bin
            return out.d
        }
    }

    /// Çokgen ağ (düzenleme için kayıpsız): köşe sayısı, yüz sayısı, konumlar, yüz boyları, sıralar, doku koordinatları.
    private static func polygonData(_ mesh: Mesh) -> [UInt8] {
        var w = LEWriter()
        w.int(mesh.vertexCount); w.int(mesh.faceCount)
        for f in mesh.pos { w.float(f) }
        for f in mesh.faces { w.int(f.count) }
        for f in mesh.faces { for v in f { w.int(v) } }
        w.int(mesh.uvs != nil ? 1 : 0)
        if let uv = mesh.uvs { for f in uv { for x in f { w.float(x) } } }
        return w.d
    }

    private static func readPolygon(_ data: LEBytes) throws -> Mesh {
        var d = data
        let nv = Int(try d.nextInt()), nf = Int(try d.nextInt())
        guard (0...5_000_000).contains(nv), (0...5_000_000).contains(nf) else { throw GltfException("Bozuk çokgen verisi") }
        if nv * 12 + nf * 4 > d.limit { throw GltfException("Bozuk çokgen verisi") }
        var pos = [Float](repeating: 0, count: nv * 3)
        for i in 0..<pos.count { pos[i] = try d.nextFloat() }
        var sizes = [Int](repeating: 0, count: nf)
        for i in 0..<nf { sizes[i] = Int(try d.nextInt()); if sizes[i] < 0 { throw GltfException("Bozuk çokgen verisi") } }
        var faces: [[Int]] = []
        faces.reserveCapacity(nf)
        for f in 0..<nf {
            var face = [Int](repeating: 0, count: sizes[f])
            for k in 0..<sizes[f] { face[k] = Int(try d.nextInt()) }
            faces.append(face)
        }
        let hasUv = try d.nextInt() == 1
        var uvs: [[Float]]? = nil
        if hasUv {
            var u: [[Float]] = []
            for f in 0..<nf {
                var a = [Float](repeating: 0, count: sizes[f] * 2)
                for k in 0..<a.count { a[k] = try d.nextFloat() }
                u.append(a)
            }
            uvs = u
        }
        return Mesh(pos, faces, uvs)
    }

    private static func v3(_ v: V3) -> JV { .arr([.f(v.x), .f(v.y), .f(v.z)]) }

    private static func materialExtras(_ m: Material) -> JV {
        var o: [(String, JV)] = [("color", .i(m.color))]
        if let t = m.texture { o.append(("texture", .s(t))) }
        o += [("textureScale", .f(m.textureScale)), ("opacity", .f(m.opacity)), ("kind", .s("\(m.kind)")), ("shine", .f(m.shine))]
        if let g = m.gradient { o += [("gradient", .i(g)), ("gradientAxis", .i(m.gradientAxis))] }
        return .obj(o)
    }

    private static func modelExtras(_ model: Model3D) -> JV {
        let s = model.scene, l = s.light, c = model.camera
        return .obj([
            ("v", .i(DG_VERSION)), ("id", .s(model.id)), ("name", .s(model.name)),
            ("scene", .obj([
                ("light", .obj([("yaw", .f(l.yaw)), ("pitch", .f(l.pitch)), ("intensity", .f(l.intensity)), ("ambient", .f(l.ambient)), ("color", .i(l.color))])),
                ("shadow", .b(s.shadow)), ("edges", .b(s.edges)), ("edgeColor", .i(s.edgeColor)), ("background", .i(s.background)), ("ground", .b(s.ground)),
            ])),
            ("camera", .obj([("yaw", .f(c.yaw)), ("pitch", .f(c.pitch)), ("persp", .f(c.persp)), ("zoom", .f(c.zoom)), ("target", v3(c.target))])),
            ("groups", .arr(model.groups.map { .arr([.s($0.id), .s($0.name)]) })),
        ])
    }

    /// v26 fırça kaynağı (parçanın yerel uzayında iz ve ayarlar).
    private static func brushJson(_ b: BrushSource) -> JV {
        let sp = b.spec
        var m: [(String, JV)] = [("kind", .s("\(sp.kind)")), ("w", .f(sp.width)), ("h", .f(sp.height)), ("bulge", .f(sp.bulge)), ("taper", .f(sp.taper)),
                                 ("smooth", .f(sp.smoothing)), ("pressure", .b(sp.pressure)), ("sides", .i(sp.sides)), ("max", .i(sp.maxSamples)),
                                 ("pts", .arr(b.points.map { .f($0) }))]
        if let pr = b.pressures { m.append(("prs", .arr(pr.map { .f($0) }))) }
        m += [("n", v3(b.normal)), ("up", v3(b.up))]
        if let c = b.closed { m.append(("closed", .b(c))) }
        if let a = b.axis { m.append(("axis", v3(a))) }
        m.append(("t", .l(b.time)))
        if let g = b.sig { m.append(("sig", .arr([.i(g.vertices), .i(g.faces), .d(g.sum)]))) }
        return .obj(m)
    }

    private static func brushFrom(_ o: JV) -> BrushSource? {
        guard let kind = Brush3Kind.allCases.first(where: { "\($0)" == o["kind"]?.str }) else { return nil }
        guard let pts = o["pts"]?.floats, pts.count >= 6, pts.count % 3 == 0 else { return nil }
        func vec(_ key: String) -> V3? { guard let f = o[key]?.floats, f.count == 3 else { return nil }; return V3(f[0], f[1], f[2]) }
        func fl(_ k: String) -> Float? { o[k].flatMap { $0.isPrimitive ? $0.float : nil } }
        func ii(_ k: String) -> Int? { o[k].flatMap { $0.isPrimitive ? $0.int : nil } }
        let spec = BrushShapeSpec(kind: kind, width: fl("w") ?? 0.05, height: fl("h") ?? 0.5, bulge: fl("bulge") ?? 0.8, taper: fl("taper") ?? 0.3,
                                  smoothing: fl("smooth") ?? 0.4, pressure: o["pressure"]?.bool ?? true, sides: ii("sides") ?? 10, maxSamples: ii("max") ?? 160)
        var sig: MeshSig? = nil
        if let a = o["sig"]?.array, a.count == 3, let v = a[0].int, let f = a[1].int, let s = a[2].double { sig = MeshSig(vertices: v, faces: f, sum: s) }
        let prs = o["prs"]?.floats.flatMap { $0.count == pts.count / 3 ? $0 : nil }
        return BrushSource(spec, pts, prs, vec("n") ?? .UP, vec("up") ?? V3(0, 0, -1), closed: o["closed"]?.bool, axis: vec("axis"),
                           time: o["t"]?.long ?? 0, sig: sig)
    }

    /// Euler (derece; önce X, sonra Y, sonra Z) → dördey (x, y, z, w).
    static func quaternion(_ deg: V3) -> [Float] {
        func q(_ axis: Int, _ d: Float) -> [Double] {
            let h = toRadians(Double(d)) / 2
            let s = sin(h), c = cos(h)
            switch axis { case 0: return [s, 0, 0, c]; case 1: return [0, s, 0, c]; default: return [0, 0, s, c] }
        }
        func mul(_ a: [Double], _ b: [Double]) -> [Double] {
            let x = a[3] * b[0] + a[0] * b[3] + a[1] * b[2] - a[2] * b[1]
            let y = a[3] * b[1] - a[0] * b[2] + a[1] * b[3] + a[2] * b[0]
            let z = a[3] * b[2] + a[0] * b[1] - a[1] * b[0] + a[2] * b[3]
            let w = a[3] * b[3] - a[0] * b[0] - a[1] * b[1] - a[2] * b[2]
            return [x, y, z, w]
        }
        return mul(mul(q(2, deg.z), q(1, deg.y)), q(0, deg.x)).map { Float($0) }
    }

    private static func toLinear(_ c: Float) -> Float { c <= 0.04045 ? c / 12.92 : Float(pow(Double((c + 0.055) / 1.055), 2.4)) }
    private static func toSrgb(_ c: Float) -> Float { c <= 0.0031308 ? c * 12.92 : Float(1.055 * pow(Double(c), 1 / 2.4) - 0.055) }

    // MARK: okuma (ortak)

    private final class Doc {
        let root: JV
        let views: [JV], accessors: [JV]
        let buffers: [[UInt8]?]

        init(_ root: JV, _ bin: [UInt8]?) throws {
            self.root = root
            views = try root["bufferViews"].map { try reqArr($0) } ?? []
            accessors = try root["accessors"].map { try reqArr($0) } ?? []
            let bs = try root["buffers"].map { try reqArr($0) } ?? []
            buffers = bs.enumerated().map { i, b in
                guard let uri = b["uri"]?.content else { return i == 0 ? bin : nil }
                return uri.hasPrefix("data:") ? Gltf3.decodeDataUri(uri) : nil
            }
        }

        func viewBytes(_ index: Int) throws -> LEBytes {
            guard index >= 0, index < views.count else { throw GltfException("Eksik bufferView \(index)") }
            let v = views[index]
            let bi = v["buffer"]?.int ?? 0
            guard bi >= 0, bi < buffers.count, let buf = buffers[bi] else { throw GltfException("Harici .bin dosyası desteklenmiyor (GLB kullanın)") }
            let off = v["byteOffset"]?.int ?? 0, len = v["byteLength"]?.int ?? 0
            if off < 0 || len < 0 || off + len > buf.count { throw GltfException("Bozuk bufferView") }
            return LEBytes(buf, off, len)
        }

        /// Erişimciyi ondalık diziye okur (normalize bayrağına göre); sayı × bileşen.
        func read(_ index: Int) throws -> ([Float], Int) {
            guard index >= 0, index < accessors.count else { throw GltfException("Eksik accessor \(index)") }
            let a = accessors[index]
            let comps: Int
            switch a["type"]?.str { case "SCALAR": comps = 1; case "VEC2": comps = 2; case "VEC3": comps = 3; case "VEC4": comps = 4; case "MAT4": comps = 16; default: comps = 1 }
            let count = a["count"]?.int ?? 0
            if count < 0 || count > 20_000_000 { throw GltfException("Çok büyük accessor") }
            let type = a["componentType"]?.int ?? FLOAT
            let norm = a["normalized"]?.bool == true
            var out = [Float](repeating: 0, count: count * comps)
            guard let viewIndex = a["bufferView"]?.int else { return (out, comps) }
            let bb = try viewBytes(viewIndex)
            let base = a["byteOffset"]?.int ?? 0
            let size: Int
            switch type { case FLOAT, UINT: size = 4; case USHORT, SHORT: size = 2; default: size = 1 }
            let stride = views[viewIndex]["byteStride"]?.int ?? (size * comps)
            for i in 0..<count { for c in 0..<comps {
                let p = base + i * stride + c * size
                if p < 0 || p + size > bb.limit { throw GltfException("Accessor tamponun dışında") }
                let v: Float
                switch type {
                case FLOAT: v = bb.f32(p)
                case UBYTE: let x = Int(bb.u8(p)); v = norm ? Float(x) / 255 : Float(x)
                case BYTE: let x = Int(Int8(bitPattern: bb.u8(p))); v = norm ? Swift.max(Float(x) / 127, -1) : Float(x)
                case USHORT: let x = Int(UInt16(bitPattern: bb.i16(p))); v = norm ? Float(x) / 65535 : Float(x)
                case SHORT: let x = Int(bb.i16(p)); v = norm ? Swift.max(Float(x) / 32767, -1) : Float(x)
                case UINT: v = Float(UInt32(bitPattern: bb.i32(p)))
                default: v = 0
                }
                out[i * comps + c] = v
            } }
            return (out, comps)
        }

        func readInts(_ index: Int) throws -> [Int] {
            guard index >= 0, index < accessors.count else { throw GltfException("Eksik accessor \(index)") }
            let a = accessors[index]
            let count = a["count"]?.int ?? 0
            if count < 0 || count > 60_000_000 { throw GltfException("Çok büyük accessor") }
            let type = a["componentType"]?.int ?? UINT
            guard let viewIndex = a["bufferView"]?.int else { return [Int](repeating: 0, count: count) }
            let bb = try viewBytes(viewIndex)
            let base = a["byteOffset"]?.int ?? 0
            let size: Int
            switch type { case UINT: size = 4; case USHORT: size = 2; default: size = 1 }
            let stride = views[viewIndex]["byteStride"]?.int ?? size
            var out = [Int](repeating: 0, count: count)
            for i in 0..<count {
                let p = base + i * stride
                if p < 0 || p + size > bb.limit { throw GltfException("Dizin tamponun dışında") }
                switch type { case UINT: out[i] = Int(bb.i32(p)); case USHORT: out[i] = Int(UInt16(bitPattern: bb.i16(p))); default: out[i] = Int(bb.u8(p)) }
            }
            return out
        }
    }

    private static func reqArr(_ e: JV) throws -> [JV] { guard let a = e.array else { throw GltfException("glTF JSON okunamadı") }; return a }
    private static func reqObj(_ e: JV) throws -> JV { guard e.object != nil else { throw GltfException("glTF JSON okunamadı") }; return e }
    private static func intList(_ e: JV?) throws -> [Int]? { try e.map { try reqArr($0).compactMap { $0.int } } }

    private static func decodeDataUri(_ uri: String) -> [UInt8]? {
        guard let comma = uri.firstIndex(of: ","), uri[..<comma].hasSuffix(";base64") else { return nil }
        return Data(base64Encoded: String(uri[uri.index(after: comma)...])).map { [UInt8]($0) }
    }

    private static func parse(_ data: [UInt8]) throws -> Doc {
        if isGlb(data) {
            let total = Int(le(data, 8))
            if total > data.count || total < 20 { throw GltfException("Bozuk GLB") }
            var p = 12
            var jsonBytes: [UInt8]? = nil, bin: [UInt8]? = nil
            while p + 8 <= total {
                let len = Int(le(data, p)), type = le(data, p + 4)
                if len < 0 || p + 8 + len > total { throw GltfException("Bozuk GLB parçası") }
                if type == JSON_CHUNK && jsonBytes == nil { jsonBytes = Array(data[p + 8..<p + 8 + len]) }
                else if type == BIN_CHUNK && bin == nil { bin = Array(data[p + 8..<p + 8 + len]) }
                p += 8 + len
            }
            guard let jb = jsonBytes else { throw GltfException("glTF JSON okunamadı") }
            guard let root = try? JV.parse(jb), root.object != nil else { throw GltfException("glTF JSON okunamadı") }
            return try Doc(root, bin)
        }
        var i = 0
        if data.count >= 3, data[0] == 0xEF, data[1] == 0xBB, data[2] == 0xBF { i = 3 }
        while i < data.count, data[i] == 0x20 || data[i] == 0x0A || data[i] == 0x0D || data[i] == 0x09 { i += 1 }
        if i >= data.count || data[i] != UInt8(ascii: "{") { throw GltfException("glTF/GLB dosyası değil") }
        guard let root = try? JV.parse(Array(data[i...])), root.object != nil else { throw GltfException("glTF JSON okunamadı") }
        return try Doc(root, nil)
    }

    private static func le(_ d: [UInt8], _ o: Int) -> Int32 {
        Int32(bitPattern: UInt32(d[o]) | UInt32(d[o + 1]) << 8 | UInt32(d[o + 2]) << 16 | UInt32(d[o + 3]) << 24)
    }

    private static func checkSupported(_ doc: Doc) throws {
        let required = try doc.root["extensionsRequired"].map { try reqArr($0).compactMap { $0.content } } ?? []
        let compressed = required.filter { $0 == "KHR_draco_mesh_compression" || $0 == "EXT_meshopt_compression" || $0 == "KHR_meshopt_compression" }
        if !compressed.isEmpty { throw GltfException("Sıkıştırılmış glTF desteklenmiyor (\(compressed.joined(separator: ", ")))") }
    }

    // MARK: uygulamanın kendi GLB'si

    /// Uygulamanın yazdığı GLB'yi kayıpsız okur ("extras" bilgileriyle). Başka bir programın dosyasıysa nil.
    static func read(_ data: [UInt8]) throws -> Model3D? {
        let doc = try parse(data)
        guard let dg = doc.root["asset"]?["extras"]?["dg"], dg.object != nil else { return nil }
        let materials = try doc.root["materials"].map { try reqArr($0).map { try materialFrom(reqObj($0)) } } ?? []
        var parts: [Part] = []
        var strokes: [Stroke3] = []
        let sceneIndex = doc.root["scene"]?.int ?? 0
        let scenes = try doc.root["scenes"].map { try reqArr($0) }
        let sceneNodes = try scenes.flatMap { sceneIndex >= 0 && sceneIndex < $0.count ? $0[sceneIndex] : nil }.flatMap { try intList($0["nodes"]) }
        let nodes = try doc.root["nodes"].map { try reqArr($0).map { try reqObj($0) } } ?? []
        for ni in sceneNodes ?? Array(nodes.indices) {
            guard ni >= 0, ni < nodes.count else { continue }
            let node = nodes[ni]
            guard let ex = node["extras"]?["dg"], ex.object != nil else { continue }
            if let s = ex["stroke"], s.object != nil, let acc = s["points"]?.int {
                let (pts, comps) = try doc.read(acc)
                if comps == 3 {
                    strokes.append(Stroke3(s["id"]?.str ?? "s\(strokes.count)", pts, s["color"]?.int32 ?? Int32(bitPattern: 0xFF26_3238),
                                           s["width"]?.float ?? 0.02, time: s["t"]?.long ?? 0))
                }
            }
            guard let polyView = ex["poly"]?.int else { continue }
            let mesh = try readPolygon(doc.viewBytes(polyView))
            var prim: PrimitiveSpec? = nil
            if let p = ex["prim"], p.object != nil {
                prim = PrimitiveSpec(PrimitiveKind.allCases.first { "\($0)" == p["kind"]?.str } ?? .CUBE, p["segments"]?.int ?? 24, p["rings"]?.int ?? 10,
                                     top: p["top"]?.float ?? 1, thickness: p["thickness"]?.float ?? 0.3)
            }
            func vec(_ key: String, _ def: V3) -> V3 { guard let f = ex[key]?.floats, f.count == 3 else { return def }; return V3(f[0], f[1], f[2]) }
            var faceMats: [Int: Material] = [:]
            if let fm = ex["faceMaterials"] {
                for e in try reqArr(fm) {
                    let a = try reqArr(e)
                    guard let f = a.first?.int, a.count > 1, let mi = a[1].int else { continue }
                    if mi >= 0 && mi < materials.count { faceMats[f] = materials[mi] }
                }
            }
            let mat = ex["material"]?.int.flatMap { $0 >= 0 && $0 < materials.count ? materials[$0] : nil } ?? Material()
            parts.append(Part(
                ex["id"]?.str ?? "p\(parts.count)", ex["name"]?.str ?? node["name"]?.str ?? "Parça \(parts.count + 1)", mesh, prim,
                position: vec("position", .ZERO), rotation: vec("rotation", .ZERO), scale: vec("scale", .ONE),
                material: mat, faceMaterials: faceMats, smooth: ex["smooth"]?.bool ?? false,
                uv: UvMode.allCases.first { "\($0)" == ex["uv"]?.str } ?? .BOX, visible: ex["visible"]?.bool ?? true,
                group: ex["group"]?.str, brush: ex["brush"].flatMap { $0.object != nil ? brushFrom($0) : nil }))
        }
        let sc = dg["scene"].flatMap { $0.object != nil ? $0 : nil }
        let l = sc?["light"].flatMap { $0.object != nil ? $0 : nil }
        let light = Light(yaw: l?["yaw"]?.float ?? 35, pitch: l?["pitch"]?.float ?? 55, intensity: l?["intensity"]?.float ?? 1,
                          ambient: l?["ambient"]?.float ?? 0.38, color: l?["color"]?.int32 ?? -1)
        let scene = SceneSettings(light: light, shadow: sc?["shadow"]?.bool ?? true, edges: sc?["edges"]?.bool ?? true,
                                  edgeColor: sc?["edgeColor"]?.int32 ?? Int32(bitPattern: 0xFF26_3238), background: sc?["background"]?.int32 ?? 0,
                                  ground: sc?["ground"]?.bool ?? true)
        let c = dg["camera"].flatMap { $0.object != nil ? $0 : nil }
        var target = V3(0, 0.5, 0)
        if let t = c?["target"]?.floats, t.count == 3 { target = V3(t[0], t[1], t[2]) }
        let camera = Camera3(yaw: c?["yaw"]?.float ?? 35, pitch: c?["pitch"]?.float ?? 24, persp: c?["persp"]?.float ?? 0.35, zoom: c?["zoom"]?.float ?? 1, target: target)
        let groups: [Group3] = dg["groups"]?.array?.compactMap { e in
            guard let a = e.array, let id = a.first?.content else { return nil }
            return Group3(id: id, name: (a.count > 1 ? a[1].content : nil) ?? "Grup")
        } ?? []
        return Model3D(dg["id"]?.str ?? "model", dg["name"]?.str ?? "3B şekil", parts, strokes: strokes, scene: scene, camera: camera, groups: groups)
    }

    private static func materialFrom(_ m: JV) -> Material {
        if let ex = m["extras"]?["dg"], ex.object != nil {
            return Material(color: ex["color"]?.int32 ?? Material.DEFAULT_COLOR, texture: ex["texture"]?.str, textureScale: ex["textureScale"]?.float ?? 1,
                            opacity: ex["opacity"]?.float ?? 1, kind: MaterialKind.allCases.first { "\($0)" == ex["kind"]?.str } ?? .PLASTIC,
                            shine: ex["shine"]?.float ?? 0.5, gradient: ex["gradient"]?.int32, gradientAxis: (ex["gradientAxis"]?.int ?? 1).coerceIn(0, 2))
        }
        return pbrMaterial(m, nil)
    }

    /// Başka programın PBR malzemesi → uygulamanın malzemesi.
    private static func pbrMaterial(_ m: JV, _ texture: String?) -> Material {
        let pbr = m["pbrMetallicRoughness"].flatMap { $0.object != nil ? $0 : nil }
        var f: [Float] = [1, 1, 1, 1]
        if let b = pbr?["baseColorFactor"]?.floats, b.count >= 3 { f = b }
        let alpha: Float = f.count >= 4 ? f[3] : 1
        func ch(_ v: Float) -> Int { Renderer3D.ktIntF(toSrgb(v.coerceIn(0, 1)) * 255 + 0.5) }
        let r = ch(f[0]), g = ch(f[1]), b = ch(f[2])
        let metallic = pbr?["metallicFactor"]?.float ?? 1
        let rough = pbr?["roughnessFactor"]?.float ?? 1
        let emissive: Bool = { guard let e = m["emissiveFactor"]?.floats else { return false }; return e.count >= 3 && (e[0] + e[1] + e[2]) > 0.3 }()
        let blend = m["alphaMode"]?.str == "BLEND"
        let kind: MaterialKind
        if emissive { kind = .GLOW }
        else if metallic > 0.5 && pbr?["metallicRoughnessTexture"] == nil { kind = .METAL }
        else if rough > 0.85 { kind = .MATTE }
        else { kind = .PLASTIC }
        return Material(color: Int32(truncatingIfNeeded: 0xFF << 24 | r << 16 | g << 8 | b), texture: texture, textureScale: 1,
                        opacity: blend ? alpha.coerceIn(0.1, 1) : 1, kind: kind, shine: (1 - rough).coerceIn(0, 1))
    }

    // MARK: içe aktarma

    private struct Raw { let name: String; let material: Int?; let pos: [Float]; let tris: [Int]; let uv: [Float]? }

    /// Başka bir programın glTF/GLB dosyasını içe aktarır: sahnedeki tüm üçgen ilkelleri (düğüm dönüşümleriyle) parça
    /// olur; toplam üçgen sayısı `maxTriangles`'a düşürülür; model 1,6 birim boyuna ölçeklenip zemine oturtulur; temel
    /// renk dokuları `decodeImage` ile çözülüp en çok `textureSize` piksele küçültülür. Uygulamanın kendi dosyası kayıpsız okunur.
    static func importModel(_ data: [UInt8], _ name: String, _ maxTriangles: Int, _ decodeImage: ([UInt8], String?) -> Texture?,
                            newId: () -> String = { UUID().uuidString.lowercased() }, textureSize: Int = 256) throws -> GltfImport {
        if let own = (try? read(data)) ?? nil {
            var m = own
            m.id = newId()
            m.name = name.trimmingCharacters(in: .whitespaces).isEmpty ? own.name : name
            return GltfImport(model: m, textures: [], sourceTriangles: m.triangleCount, triangles: m.triangleCount)
        }
        let doc = try parse(data)
        try checkSupported(doc)
        let nodes = try doc.root["nodes"].map { try reqArr($0).map { try reqObj($0) } } ?? []
        let meshes = try doc.root["meshes"].map { try reqArr($0).map { try reqObj($0) } } ?? []
        let materials = try doc.root["materials"].map { try reqArr($0).map { try reqObj($0) } } ?? []
        let sceneIndex = doc.root["scene"]?.int ?? 0
        let scenes = try doc.root["scenes"].map { try reqArr($0) }
        var roots: [Int]
        if let sn = try scenes.flatMap({ sceneIndex >= 0 && sceneIndex < $0.count ? $0[sceneIndex] : nil }).flatMap({ try intList($0["nodes"]) }) {
            roots = sn
        } else {
            var children = Set<Int>()
            for n in nodes { for c in try intList(n["children"]) ?? [] { children.insert(c) } }
            roots = nodes.indices.filter { !children.contains($0) }
        }
        var raws: [Raw] = []
        func addMesh(_ meshIndex: Int, _ world: [Float], _ nodeName: String?) throws {
            guard meshIndex >= 0, meshIndex < meshes.count else { return }
            let mesh = meshes[meshIndex]
            guard let primsJ = mesh["primitives"] else { return }
            for pe in try reqArr(primsJ) {
                let p = try reqObj(pe)
                let mode = p["mode"]?.int ?? 4
                if !(4...6).contains(mode) { continue }
                guard let attrs = p["attributes"], attrs.object != nil, let posAcc = attrs["POSITION"]?.int else { continue }
                let (pos0, pc) = try doc.read(posAcc)
                if pc != 3 { continue }
                let n = pos0.count / 3
                var pos = [Float](repeating: 0, count: n * 3)
                for i in 0..<n {
                    let x = pos0[3 * i], y = pos0[3 * i + 1], z = pos0[3 * i + 2]
                    pos[3 * i] = world[0] * x + world[4] * y + world[8] * z + world[12]
                    pos[3 * i + 1] = world[1] * x + world[5] * y + world[9] * z + world[13]
                    pos[3 * i + 2] = world[2] * x + world[6] * y + world[10] * z + world[14]
                }
                let idx0 = try p["indices"]?.int.map { try doc.readInts($0) } ?? Array(0..<n)
                var tris: [Int]
                switch mode {
                case 4: tris = Array(idx0.prefix(idx0.count / 3 * 3))
                case 5:
                    tris = [Int](repeating: 0, count: Swift.max(0, idx0.count - 2) * 3)
                    if idx0.count > 2 { for i in 0..<(idx0.count - 2) {
                        if i % 2 == 0 { tris[3 * i] = idx0[i]; tris[3 * i + 1] = idx0[i + 1] } else { tris[3 * i] = idx0[i + 1]; tris[3 * i + 1] = idx0[i] }
                        tris[3 * i + 2] = idx0[i + 2]
                    } }
                default:
                    tris = [Int](repeating: 0, count: Swift.max(0, idx0.count - 2) * 3)
                    if idx0.count > 2 { for i in 1..<(idx0.count - 1) { tris[3 * (i - 1)] = idx0[0]; tris[3 * (i - 1) + 1] = idx0[i]; tris[3 * (i - 1) + 2] = idx0[i + 1] } }
                }
                if tris.contains(where: { $0 < 0 || $0 >= n }) { continue }
                let det = world[0] * (world[5] * world[10] - world[9] * world[6]) - world[4] * (world[1] * world[10] - world[9] * world[2]) + world[8] * (world[1] * world[6] - world[5] * world[2])
                if det < 0 { for t in 0..<(tris.count / 3) { tris.swapAt(3 * t + 1, 3 * t + 2) } }
                var uv: [Float]? = nil
                if let a = attrs["TEXCOORD_0"]?.int {
                    let (u0, uc) = try doc.read(a)
                    if uc == 2 {
                        var out = [Float](repeating: 0, count: tris.count * 2)
                        for k in tris.indices { out[2 * k] = u0[2 * tris[k]]; out[2 * k + 1] = u0[2 * tris[k] + 1] }
                        uv = out
                    }
                }
                raws.append(Raw(name: nodeName ?? mesh["name"]?.str ?? "Parça", material: p["material"]?.int, pos: pos, tris: tris, uv: uv))
            }
        }
        func visit(_ index: Int, _ parent: [Float], _ depth: Int) throws {
            if depth > 64 { return }
            guard index >= 0, index < nodes.count else { return }
            let node = nodes[index]
            let world = Mat4.mul(parent, localMatrix(node))
            if let m = node["mesh"]?.int { try addMesh(m, world, node["name"]?.str) }
            for c in try intList(node["children"]) ?? [] { try visit(c, world, depth + 1) }
        }
        if roots.isEmpty && !meshes.isEmpty { for i in meshes.indices { try addMesh(i, Mat4.identity(), nil) } }
        else { for r in roots { try visit(r, Mat4.identity(), 0) } }
        if raws.isEmpty { throw GltfException("Dosyada üçgen ağ bulunamadı") }
        // Çok parçalı modeller malzemeye göre birleştirilir (Kotlin groupBy: ilk görülme sırası)
        var grouped: [Raw]
        if raws.count <= MAX_PARTS { grouped = raws } else {
            var order: [Int?] = [], byMat: [Int?: [Raw]] = [:]
            for r in raws { if byMat[r.material] == nil { order.append(r.material); byMat[r.material] = [] }; byMat[r.material]!.append(r) }
            grouped = order.map { mat in
                let list = byMat[mat]!
                var pos: [Float] = [], tris: [Int] = [], uv: [Float] = []
                let withUv = list.allSatisfy { $0.uv != nil }
                var off = 0
                for r in list {
                    pos += r.pos
                    tris += r.tris.map { $0 + off }
                    if withUv { uv += r.uv! }
                    off += r.pos.count / 3
                }
                return Raw(name: list[0].name, material: mat, pos: pos, tris: tris, uv: withUv ? uv : nil)
            }
            grouped = Array(grouped.prefix(MAX_PARTS))
        }
        let sourceTris = grouped.reduce(0) { $0 + $1.tris.count / 3 }
        let budget = maxTriangles.coerceAtLeast(64)
        var textureIds: [Int: String?] = [:]
        var imported: [ImportedTexture] = []
        func textureOf(_ matIndex: Int?) -> String? {
            guard let mi = matIndex else { return nil }
            if let t = textureIds[mi] { return t }
            let r: String? = {
                guard mi >= 0, mi < materials.count else { return nil }
                let m = materials[mi]
                guard let texIndex = m["pbrMetallicRoughness"]?["baseColorTexture"]?["index"]?.int else { return nil }
                guard let texs = doc.root["textures"]?.array, texIndex >= 0, texIndex < texs.count, texs[texIndex].object != nil else { return nil }
                guard let src = texs[texIndex]["source"]?.int else { return nil }
                guard let imgs = doc.root["images"]?.array, src >= 0, src < imgs.count, imgs[src].object != nil else { return nil }
                let img = imgs[src]
                var bytes: [UInt8]? = nil
                if let v = img["bufferView"]?.int { bytes = (try? doc.viewBytes(v))?.bytes }
                if bytes == nil, let uri = img["uri"]?.str, uri.hasPrefix("data:") { bytes = decodeDataUri(uri) }
                guard let bs = bytes, let decoded = decodeImage(bs, img["mimeType"]?.str) else { return nil }
                let small = TextureResize.toPowerOfTwo(decoded, textureSize)
                let id = "imp." + newId()
                imported.append(ImportedTexture(id: id, name: img["name"]?.str ?? m["name"]?.str ?? name, texture: small))
                return id
            }()
            textureIds[mi] = .some(r)
            return r
        }
        // Parçalar: ayrıntı düşürülür (bütçe üçgen sayısıyla orantılı paylaşılır)
        let simplified: [(Raw, TriMesh)] = grouped.map { r in
            let share = Int(Int64(budget) * Int64(r.tris.count / 3) / Int64(sourceTris.coerceAtLeast(1))).coerceAtLeast(16)
            return (r, MeshSimplifier.simplify(TriMesh(pos: r.pos, tris: r.tris, uv: r.uv), share))
        }.filter { $0.1.triangleCount > 0 }
        if simplified.isEmpty { throw GltfException("Dosyada üçgen ağ bulunamadı") }
        var ax = Float.infinity, ay = Float.infinity, az = Float.infinity
        var bx = -Float.infinity, by = -Float.infinity, bz = -Float.infinity
        for s in simplified {
            let p = s.1.pos
            for i in 0..<(p.count / 3) {
                ax = Swift.min(ax, p[3 * i]); ay = Swift.min(ay, p[3 * i + 1]); az = Swift.min(az, p[3 * i + 2])
                bx = Swift.max(bx, p[3 * i]); by = Swift.max(by, p[3 * i + 1]); bz = Swift.max(bz, p[3 * i + 2])
            }
        }
        let extent = Swift.max(bx - ax, Swift.max(by - ay, bz - az)).coerceAtLeast(1e-6)
        let k = TARGET_EXTENT / extent
        let cx = (ax + bx) / 2, cz = (az + bz) / 2
        var parts: [Part] = []
        for (i, s) in simplified.enumerated() {
            let p = s.1.pos
            var world = [Float](repeating: 0, count: p.count)
            for j in p.indices {
                switch j % 3 { case 0: world[j] = (p[j] - cx) * k; case 1: world[j] = (p[j] - ay) * k; default: world[j] = (p[j] - cz) * k }
            }
            var px0 = Float.infinity, py0 = Float.infinity, pz0 = Float.infinity, px1 = -Float.infinity, pz1 = -Float.infinity
            for v in 0..<(world.count / 3) {
                px0 = Swift.min(px0, world[3 * v]); py0 = Swift.min(py0, world[3 * v + 1]); pz0 = Swift.min(pz0, world[3 * v + 2])
                px1 = Swift.max(px1, world[3 * v]); pz1 = Swift.max(pz1, world[3 * v + 2])
            }
            let origin = V3((px0 + px1) / 2, py0, (pz0 + pz1) / 2)
            var local = world
            for v in 0..<(local.count / 3) { local[3 * v] -= origin.x; local[3 * v + 1] -= origin.y; local[3 * v + 2] -= origin.z }
            let tris = s.1.tris
            let faces: [[Int]] = (0..<(tris.count / 3)).map { (t: Int) -> [Int] in [tris[3 * t], tris[3 * t + 1], tris[3 * t + 2]] }
            let texId = textureOf(s.0.material)
            var uvs: [[Float]]? = nil
            if texId != nil, let u = s.1.uv { uvs = (0..<faces.count).map { (t: Int) -> [Float] in Array(u[(6 * t)..<(6 * t + 6)]) } }
            var mat = s.0.material.flatMap { mi in mi >= 0 && mi < materials.count ? pbrMaterial(materials[mi], texId) : nil }
                ?? Material(color: ModelEdit.palette[i % ModelEdit.palette.count])
            if mat.texture != nil && uvs == nil { mat.texture = nil }
            let nm = s.0.name.trimmingCharacters(in: .whitespaces).isEmpty ? "Parça \(i + 1)" : s.0.name
            parts.append(Part(newId(), String(nm.utf16.count > 40 ? ktTake(nm, 40) : nm), Mesh(local, faces, uvs), nil, position: origin, material: mat,
                              smooth: true, uv: uvs != nil ? .MESH : .BOX))
        }
        let box = parts.map { ModelEdit.worldBounds($0) }.reduce(ModelEdit.worldBounds(parts[0])) { $0.union($1) }
        let modelName = name.trimmingCharacters(in: .whitespaces).isEmpty ? "İçe aktarılan model" : name
        let camera = Camera3(yaw: 35, pitch: 20, persp: 0.35, zoom: (View3.REFERENCE_EXTENT / (box.radius * 1.15)).coerceIn(0.1, 30), target: box.center)
        let model = Model3D(newId(), modelName, parts, camera: camera)
        return GltfImport(model: model, textures: imported.filter { t in parts.contains { $0.material.texture == t.id } },
                          sourceTriangles: sourceTris, triangles: model.triangleCount)
    }

    private static let MAX_PARTS = 48
    private static let TARGET_EXTENT: Float = 1.6

    /// Düğümün yerel matrisi (sütun düzeninde 4×4): "matrix" ya da öteleme · döndürme · ölçek.
    private static func localMatrix(_ node: JV) -> [Float] {
        if let m = node["matrix"]?.floats, m.count == 16 { return m }
        let t = node["translation"]?.floats.flatMap { $0.count == 3 ? $0 : nil } ?? [0, 0, 0]
        let r = node["rotation"]?.floats.flatMap { $0.count == 4 ? $0 : nil } ?? [0, 0, 0, 1]
        let s = node["scale"]?.floats.flatMap { $0.count == 3 ? $0 : nil } ?? [1, 1, 1]
        return Mat4.trs(t, r, s)
    }
}

/// Kotlin `String.take(n)` (UTF-16 birimiyle).
func ktTake(_ s: String, _ n: Int) -> String {
    let u = Array(s.utf16)
    if u.count <= n { return s }
    return String(decoding: u[0..<n], as: UTF16.self)
}

extension Float {
    /// Kotlin `Float.toBits()` (NaN'lar tek biçime indirgenir).
    var ktBits: UInt32 { isNaN ? 0x7FC0_0000 : bitPattern }
}

/// Sütun düzeninde 4×4 matris yardımcıları (glTF ve ekran kartı çizimi).
enum Mat4 {
    static func identity() -> [Float] { [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1] }

    static func mul(_ a: [Float], _ b: [Float]) -> [Float] {
        var o = [Float](repeating: 0, count: 16)
        for c in 0...3 { for r in 0...3 {
            var s: Float = 0
            for k in 0...3 { s += a[k * 4 + r] * b[c * 4 + k] }
            o[c * 4 + r] = s
        } }
        return o
    }

    /// Öteleme · döndürme (dördey x, y, z, w) · ölçek.
    static func trs(_ t: [Float], _ q: [Float], _ s: [Float]) -> [Float] {
        let x = q[0], y = q[1], z = q[2], w = q[3]
        let xx = x * x, yy = y * y, zz = z * z, xy = x * y, xz = x * z, yz = y * z, wx = w * x, wy = w * y, wz = w * z
        let c0: [Float] = [(1 - 2 * (yy + zz)) * s[0], (2 * (xy + wz)) * s[0], (2 * (xz - wy)) * s[0], 0]
        let c1: [Float] = [(2 * (xy - wz)) * s[1], (1 - 2 * (xx + zz)) * s[1], (2 * (yz + wx)) * s[1], 0]
        let c2: [Float] = [(2 * (xz + wy)) * s[2], (2 * (yz - wx)) * s[2], (1 - 2 * (xx + yy)) * s[2], 0]
        return c0 + c1 + c2 + [t[0], t[1], t[2], 1]
    }

    /// Parçanın dönüşümü (sütun düzeninde 4×4).
    static func of(_ t: Transform3) -> [Float] {
        let m = t.m
        return [m[0], m[4], m[8], 0, m[1], m[5], m[9], 0, m[2], m[6], m[10], 0, m[3], m[7], m[11], 1]
    }
}

/// Doku küçültme (içe aktarma): kutu süzgeciyle en çok `max` piksele ve 2'nin kuvvetine.
enum TextureResize {
    static func toPowerOfTwo(_ t: Texture, _ max: Int) -> Texture {
        func pot(_ v: Int) -> Int { var p = 1; while p * 2 <= v && p * 2 <= max { p *= 2 }; return Swift.max(p, 1) }
        let w = pot(t.w), h = pot(t.h)
        if w == t.w && h == t.h { return t }
        var out = [UInt32](repeating: 0, count: w * h)
        for y in 0..<h { for x in 0..<w {
            let x0 = x * t.w / w, x1 = Swift.max((x + 1) * t.w / w, x0 + 1)
            let y0 = y * t.h / h, y1 = Swift.max((y + 1) * t.h / h, y0 + 1)
            var a = 0, r = 0, g = 0, b = 0, n = 0
            for yy in y0..<Swift.min(y1, t.h) { for xx in x0..<Swift.min(x1, t.w) {
                let c = t.px[yy * t.w + xx]
                a += Int(c >> 24 & 0xFF); r += Int(c >> 16 & 0xFF); g += Int(c >> 8 & 0xFF); b += Int(c & 0xFF); n += 1
            } }
            if n > 0 {
                let ca = UInt32(a / n) << 24, cr = UInt32(r / n) << 16, cg = UInt32(g / n) << 8
                out[y * w + x] = ca | cr | cg | UInt32(b / n)
            }
        } }
        return Texture(w, h, out)
    }
}
