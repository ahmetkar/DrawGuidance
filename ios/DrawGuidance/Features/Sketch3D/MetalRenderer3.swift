import Foundation
import Metal
import MetalKit
import SwiftUI

/// v56 3B Çizim'in ekran kartı çizicisi (Android `GlRenderer3` karşılığı, sade): izdüşüm ve yüz ışığı işlemcide
/// (`View3` ile, yazılım çiziciyle aynı açı), üçgenleme ve derinlik sınaması Metal'de. Cisimler düz gölgeli, çizgiler
/// nokta başına kalınlıklı şerit ve yuvarlak eklemlerle; saydam çizgiler ayrı geçişte karıştırılır.
/// Metal açılamazsa ([MetalRenderer3.init] nil) yazılım çiziciye (`Renderer3D`) düşülür.
final class MetalRenderer3 {
    /// Köşe başına 7 sayı: konum (NDC x, y, derinlik) ve renk (r, g, b, a).
    static let FLOATS_PER_VERTEX = 7

    private static let SHADER = """
    #include <metal_stdlib>
    using namespace metal;
    struct Vtx { packed_float3 pos; packed_float4 color; };
    struct Out { float4 position [[position]]; float4 color; };
    vertex Out sk3_vertex(const device Vtx* v [[buffer(0)]], uint vid [[vertex_id]]) {
        Out o;
        o.position = float4(float3(v[vid].pos), 1.0);
        o.color = float4(v[vid].color);
        return o;
    }
    fragment float4 sk3_fragment(Out in [[stage_in]]) { return in.color; }
    """

    let device: MTLDevice
    private let queue: MTLCommandQueue
    private let pipeline: MTLRenderPipelineState
    private let depthWrite: MTLDepthStencilState
    private let depthRead: MTLDepthStencilState
    let sampleCount: Int

    init?(pixelFormat: MTLPixelFormat = .bgra8Unorm, sampleCount: Int = 4) {
        guard let device = MTLCreateSystemDefaultDevice(), let queue = device.makeCommandQueue() else { return nil }
        self.device = device
        self.queue = queue
        let samples = device.supportsTextureSampleCount(sampleCount) ? sampleCount : 1
        self.sampleCount = samples
        do {
            let lib = try device.makeLibrary(source: Self.SHADER, options: nil)
            let d = MTLRenderPipelineDescriptor()
            d.vertexFunction = lib.makeFunction(name: "sk3_vertex")
            d.fragmentFunction = lib.makeFunction(name: "sk3_fragment")
            d.colorAttachments[0].pixelFormat = pixelFormat
            d.colorAttachments[0].isBlendingEnabled = true
            d.colorAttachments[0].rgbBlendOperation = .add
            d.colorAttachments[0].alphaBlendOperation = .add
            d.colorAttachments[0].sourceRGBBlendFactor = .sourceAlpha
            d.colorAttachments[0].destinationRGBBlendFactor = .oneMinusSourceAlpha
            d.colorAttachments[0].sourceAlphaBlendFactor = .one
            d.colorAttachments[0].destinationAlphaBlendFactor = .oneMinusSourceAlpha
            d.depthAttachmentPixelFormat = .depth32Float
            d.rasterSampleCount = samples
            pipeline = try device.makeRenderPipelineState(descriptor: d)
        } catch {
            AppLog.e("Metal3D", "Metal çizici kurulamadı", error)
            return nil
        }
        let w = MTLDepthStencilDescriptor()
        w.depthCompareFunction = .lessEqual
        w.isDepthWriteEnabled = true
        let r = MTLDepthStencilDescriptor()
        r.depthCompareFunction = .lessEqual
        r.isDepthWriteEnabled = false
        guard let dw = device.makeDepthStencilState(descriptor: w), let dr = device.makeDepthStencilState(descriptor: r) else { return nil }
        depthWrite = dw
        depthRead = dr
    }

    /// Sahne: köşeler (opak ve saydam) ve arka plan rengi.
    struct Scene {
        var opaque: [Float] = []
        var transparent: [Float] = []
        var clear: MTLClearColor = MTLClearColor(red: 1, green: 1, blue: 1, alpha: 1)
    }

    /// Karede çizer; hata olursa false (çağıran yazılım çiziciye düşer).
    @discardableResult
    func draw(_ scene: Scene, in view: MTKView) -> Bool {
        guard let pass = view.currentRenderPassDescriptor, let drawable = view.currentDrawable,
              let cmd = queue.makeCommandBuffer() else { return false }
        pass.colorAttachments[0].clearColor = scene.clear
        pass.colorAttachments[0].loadAction = .clear
        pass.depthAttachment.clearDepth = 1
        pass.depthAttachment.loadAction = .clear
        guard let enc = cmd.makeRenderCommandEncoder(descriptor: pass) else { return false }
        enc.setRenderPipelineState(pipeline)
        enc.setCullMode(.none)
        func submit(_ v: [Float], _ depth: MTLDepthStencilState) {
            let n = v.count / Self.FLOATS_PER_VERTEX
            guard n >= 3 else { return }
            enc.setDepthStencilState(depth)
            let bytes = v.count * MemoryLayout<Float>.stride
            if bytes <= 4096 {
                v.withUnsafeBytes { enc.setVertexBytes($0.baseAddress!, length: bytes, index: 0) }
            } else if let buf = v.withUnsafeBytes({ device.makeBuffer(bytes: $0.baseAddress!, length: bytes, options: .storageModeShared) }) {
                enc.setVertexBuffer(buf, offset: 0, index: 0)
            } else { return }
            enc.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: n - n % 3)
        }
        submit(scene.opaque, depthWrite)
        submit(scene.transparent, depthRead)
        enc.endEncoding()
        cmd.present(drawable)
        cmd.commit()
        return true
    }

    // MARK: sahne kurma (saf; testlerde sınanır)

    /// Modelin ekran köşeleri: cisimler düz gölgeli üçgenler, çizgiler kalınlıklı şeritler. [selected] cisimler vurgulanır.
    static func build(_ model: Model3D, _ view: View3, selected: Set<String> = [], highlight: Int32 = Int32(bitPattern: 0xFF2F_80ED),
                      lineScale: Float = 1, style: Int = RenderStyle.SOFT) -> Scene {
        var sc = Scene()
        let w = Float(max(view.width, 1)), h = Float(max(view.height, 1))
        let light = model.scene.light
        let ldir = light.direction.normalized()
        let ambient = light.ambient.coerceIn(0, 1)
        // Derinlik ölçeği: sahnenin boyuna göre (kamera ekseninde zc büyüdükçe yakın)
        var radius: Float = 4
        if let b = ModelEdit.modelBounds(model) { radius = max(radius, b.radius * 4 + (b.center - model.camera.target).length * 2) }
        func depth(_ zc: Float) -> Float { (0.5 - zc / (2 * radius)).coerceIn(0.0005, 0.9995) }
        var s = [Float](repeating: 0, count: 4)
        var out = [Float](repeating: 0, count: 3)

        for part in model.parts where part.visible {
            let t = Transform3.of(part)
            let mesh = part.mesh
            let fn = mesh.faceNormals
            let tris = mesh.triangles
            let isSel = selected.contains(part.id)
            for f in 0..<mesh.faceCount {
                let mat = part.materialOf(f)
                t.normal(fn[3 * f], fn[3 * f + 1], fn[3 * f + 2], &out, 0)
                let n = V3(out[0], out[1], out[2])
                let lambert = abs(n.dot(ldir))
                let shade0 = ambient + (1 - ambient) * lambert * light.intensity
                // v56 aşama 12 toon: ışık üç banda yuvarlanır; sadece çizgi: yüzeyler beyaz
                let shade = style == RenderStyle.TOON ? RenderStyle.toonBand(shade0) : shade0
                var (r, g, b, _) = style == RenderStyle.LINES && mat.kind != .GLOW ? (Float(1) / max(shade, 0.05), Float(1) / max(shade, 0.05), Float(1) / max(shade, 0.05), Float(1)) : rgba(mat.color)
                if isSel { let (hr, hg, hb, _) = rgba(highlight); r = r * 0.6 + hr * 0.4; g = g * 0.6 + hg * 0.4; b = b * 0.6 + hb * 0.4 }
                let glow = mat.kind == .GLOW
                let k: Float = glow ? 1.05 : shade
                let col = (min(1, r * k), min(1, g * k), min(1, b * k), mat.opacity.coerceIn(0, 1))
                let face = mesh.faces[f]
                let tri = tris[f]
                let toTransparent = mat.transparent
                var i = 0
                while i + 2 < tri.count {
                    var ok = true
                    var vs: [Float] = []
                    vs.reserveCapacity(21)
                    for k3 in 0..<3 {
                        let vi = face[tri[i + k3]]
                        let p = t.apply(mesh.vertex(vi))
                        view.project(p.x, p.y, p.z, &s)
                        if s[3] <= 0 { ok = false; break }
                        vs += [s[0] / w * 2 - 1, 1 - s[1] / h * 2, depth(s[2]), col.0, col.1, col.2, col.3]
                    }
                    if ok { if toTransparent { sc.transparent += vs } else { sc.opaque += vs } }
                    i += 3
                }
            }
        }

        if style != RenderStyle.SOFT { sc.opaque += inkEdges(model, view, lineScale: lineScale, depth: depth) }

        for st in model.strokes where st.count > 0 {
            let (r, g, b, _) = rgba(st.color)
            let a = st.opacity.coerceIn(0, 1)
            let col = (r, g, b, a)
            var buf: [Float] = []
            var pts: [(Float, Float, Float, Float)] = []
            pts.reserveCapacity(st.count)
            for i in 0..<st.count {
                view.project(st.points[3 * i], st.points[3 * i + 1], st.points[3 * i + 2], &s)
                // (ekran x, y, derinlik, yarıçap px); görünmeyen nokta yarıçapı < 0
                let rad = s[3] > 0 ? max(0.6, st.widthAt(i) * 0.5 * view.scale * s[3] * lineScale) : -1
                pts.append((s[0], s[1], depth(s[2]) - 0.0008, rad))
            }
            func ndc(_ x: Float, _ y: Float, _ z: Float) -> [Float] { [x / w * 2 - 1, 1 - y / h * 2, z, col.0, col.1, col.2, col.3] }
            func disc(_ p: (Float, Float, Float, Float)) {
                guard p.3 > 0 else { return }
                let seg = p.3 > 6 ? 12 : 8
                for k in 0..<seg {
                    let a0 = Float(k) / Float(seg) * 2 * PI_F, a1 = Float(k + 1) / Float(seg) * 2 * PI_F
                    buf += ndc(p.0, p.1, p.2)
                    buf += ndc(p.0 + cos(a0) * p.3, p.1 + sin(a0) * p.3, p.2)
                    buf += ndc(p.0 + cos(a1) * p.3, p.1 + sin(a1) * p.3, p.2)
                }
            }
            disc(pts[0])
            if pts.count > 1 {
                for i in 1..<pts.count {
                    let p = pts[i - 1], q = pts[i]
                    guard p.3 > 0, q.3 > 0 else { continue }
                    let dx = q.0 - p.0, dy = q.1 - p.1
                    let len = (dx * dx + dy * dy).squareRoot()
                    if len > 1e-4 {
                        let nx = -dy / len, ny = dx / len
                        let a1 = ndc(p.0 + nx * p.3, p.1 + ny * p.3, p.2), a2 = ndc(p.0 - nx * p.3, p.1 - ny * p.3, p.2)
                        let b1 = ndc(q.0 + nx * q.3, q.1 + ny * q.3, q.2), b2 = ndc(q.0 - nx * q.3, q.1 - ny * q.3, q.2)
                        buf += a1; buf += a2; buf += b1
                        buf += a2; buf += b2; buf += b1
                    }
                    // Yarı saydam çizgide eklem üst üste binip koyulaşmasın diye yalnız opakta yuvarlak eklem
                    if a >= 0.999 || i == pts.count - 1 { disc(q) }
                }
            }
            if a >= 0.999 { sc.opaque += buf } else { sc.transparent += buf }
        }
        let bg = model.scene.background
        if bg != 0 {
            let (r, g, b, _) = rgba(bg)
            sc.clear = MTLClearColor(red: Double(r), green: Double(g), blue: Double(b), alpha: 1)
        }
        return sc
    }

    /// v56 aşama 12 toon ve sadece çizgi tarzının mürekkep kenarları: açık kenarlar, keskin kıvrımlar ve siluet
    /// (komşu yüzlerden biri ekrana dönük, öteki değil) ekranda kalınlıklı şerit olarak, yüzeylerin biraz önünde.
    static func inkEdges(_ model: Model3D, _ view: View3, lineScale: Float, depth: (Float) -> Float) -> [Float] {
        var out: [Float] = []
        let w = Float(max(view.width, 1)), h = Float(max(view.height, 1))
        let (ir, ig, ib, _) = rgba(Int32(bitPattern: RenderStyle.INK))
        let half = max(0.8, 1.1 * lineScale)
        var s = [Float](repeating: 0, count: 4)
        for part in model.parts where part.visible {
            let t = Transform3.of(part)
            let mesh = part.mesh
            let n = mesh.pos.count / 3
            var scr = [Float](repeating: 0, count: n * 4)
            for i in 0..<n {
                let p = t.apply(mesh.vertex(i))
                view.project(p.x, p.y, p.z, &s)
                scr[4 * i] = s[0]; scr[4 * i + 1] = s[1]; scr[4 * i + 2] = depth(s[2]) - 0.0012; scr[4 * i + 3] = s[3]
            }
            // Yüzün ekrana dönüklüğü: izdüşen ilk üçgenin dolanım yönü
            func front(_ f: Int) -> Bool {
                let face = mesh.faces[f], tri = mesh.triangles[f]
                guard tri.count >= 3 else { return true }
                let a = face[tri[0]], b = face[tri[1]], c = face[tri[2]]
                let cross = (scr[4 * b] - scr[4 * a]) * (scr[4 * c + 1] - scr[4 * a + 1]) - (scr[4 * b + 1] - scr[4 * a + 1]) * (scr[4 * c] - scr[4 * a])
                return cross < 0
            }
            let fn = mesh.faceNormals
            let e = mesh.edges
            var i = 0
            while i + 3 < e.count {
                let va = e[i], vb = e[i + 1], f1 = e[i + 2], f2 = e[i + 3]
                i += 4
                var draw = f2 < 0
                if !draw {
                    let dot = fn[3 * f1] * fn[3 * f2] + fn[3 * f1 + 1] * fn[3 * f2 + 1] + fn[3 * f1 + 2] * fn[3 * f2 + 2]
                    draw = front(f1) != front(f2) || (dot < 0.82 && (!part.smooth || dot < 0.7))
                }
                guard draw, scr[4 * va + 3] > 0, scr[4 * vb + 3] > 0 else { continue }
                let ax = scr[4 * va], ay = scr[4 * va + 1], az = scr[4 * va + 2]
                let bx = scr[4 * vb], by = scr[4 * vb + 1], bz = scr[4 * vb + 2]
                let dx = bx - ax, dy = by - ay
                let len = (dx * dx + dy * dy).squareRoot()
                guard len > 1e-4 else { continue }
                let nx = -dy / len * half, ny = dx / len * half
                func v(_ x: Float, _ y: Float, _ z: Float) -> [Float] { [x / w * 2 - 1, 1 - y / h * 2, z, ir, ig, ib, 1] }
                let a1 = v(ax + nx, ay + ny, az), a2 = v(ax - nx, ay - ny, az), b1 = v(bx + nx, by + ny, bz), b2 = v(bx - nx, by - ny, bz)
                out += a1; out += a2; out += b1
                out += a2; out += b2; out += b1
            }
        }
        return out
    }

    /// ARGB tamsayıdan 0…1 bileşenler.
    static func rgba(_ c: Int32) -> (Float, Float, Float, Float) {
        let u = UInt32(bitPattern: c)
        return (Float(u >> 16 & 0xFF) / 255, Float(u >> 8 & 0xFF) / 255, Float(u & 0xFF) / 255, Float(u >> 24 & 0xFF) / 255)
    }
}

/// MTKView sarmalayıcı: sahne değişince bir kare çizer (sürekli döngü yok). Metal açılamazsa [onFail] çağrılır.
struct Metal3DView: UIViewRepresentable {
    let model: Model3D
    let selected: Set<String>
    let clear: Color
    let tick: Int
    var style: Int = RenderStyle.SOFT
    let onFail: () -> Void

    final class Coordinator: NSObject, MTKViewDelegate {
        var renderer: MetalRenderer3?
        var model: Model3D?
        var selected: Set<String> = []
        var style = RenderStyle.SOFT
        var clear = MTLClearColor(red: 1, green: 1, blue: 1, alpha: 1)
        var onFail: (() -> Void)?
        private var failed = false

        func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) { view.setNeedsDisplay() }

        func draw(in view: MTKView) {
            guard let r = renderer, let m = model, !failed else { return }
            let w = Int(view.drawableSize.width), h = Int(view.drawableSize.height)
            guard w > 0, h > 0 else { return }
            // Çizici ekran noktasında çalışır; çizim yüzeyi piksel: ölçek görünüm ölçeğiyle aynı oranda büyür
            let v = View3.editor(m.camera, w, h)
            var sc = MetalRenderer3.build(m, v, selected: selected, lineScale: 1, style: style)
            if m.scene.background == 0 { sc.clear = clear }
            if !r.draw(sc, in: view) {
                failed = true
                let cb = onFail
                DispatchQueue.main.async { cb?() }
            }
        }
    }

    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeUIView(context: Context) -> MTKView {
        let v = MTKView()
        let c = context.coordinator
        guard let r = MetalRenderer3(pixelFormat: .bgra8Unorm, sampleCount: Budget3D.tier == .LOW ? 1 : 4) else {
            DispatchQueue.main.async { onFail() }
            return v
        }
        c.renderer = r
        v.device = r.device
        v.colorPixelFormat = .bgra8Unorm
        v.depthStencilPixelFormat = .depth32Float
        v.sampleCount = r.sampleCount
        v.enableSetNeedsDisplay = true
        v.isPaused = true
        v.framebufferOnly = true
        v.isUserInteractionEnabled = false
        v.backgroundColor = .clear
        v.delegate = c
        return v
    }

    func updateUIView(_ v: MTKView, context: Context) {
        let c = context.coordinator
        c.model = model
        c.selected = selected
        c.style = style
        c.onFail = onFail
        let ui = UIColor(clear).resolvedColor(with: v.traitCollection)
        var r: CGFloat = 1, g: CGFloat = 1, b: CGFloat = 1, a: CGFloat = 1
        ui.getRed(&r, green: &g, blue: &b, alpha: &a)
        c.clear = MTLClearColor(red: Double(r), green: Double(g), blue: Double(b), alpha: 1)
        _ = tick
        v.setNeedsDisplay()
    }
}
