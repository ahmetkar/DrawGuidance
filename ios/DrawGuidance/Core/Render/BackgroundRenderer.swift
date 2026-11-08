import CoreGraphics
import Foundation

/// v23 desenlerin CoreGraphics'e çizimi ([PatternPainter] yüzeyi; Android `AndroidPatternSurface`). Bağlamın y ekseni aşağı bakar.
final class CGPatternSurface: PatternSurface {
    let c: CGContext
    init(_ c: CGContext) { self.c = c }

    func save() { c.saveGState() }
    func restore() { c.restoreGState() }
    func translate(_ dx: Float, _ dy: Float) { c.translateBy(x: CGFloat(dx), y: CGFloat(dy)) }
    func scale(_ s: Float) { c.scaleBy(x: CGFloat(s), y: CGFloat(s)) }
    func clipRect(_ l: Float, _ t: Float, _ r: Float, _ b: Float) { c.clip(to: CGRect(x: CGFloat(l), y: CGFloat(t), width: CGFloat(r - l), height: CGFloat(b - t))) }

    private func strokeStyle(_ width: Float, _ color: UInt32, _ dash: Float) {
        c.setStrokeColor(ArgbColor.cgColor(color))
        c.setLineWidth(CGFloat(width))
        c.setLineJoin(.round)
        if dash > 0 {
            c.setLineCap(.butt)
            c.setLineDash(phase: 0, lengths: [CGFloat(dash), CGFloat(dash * 0.7)])
        } else {
            c.setLineCap(.round)
            c.setLineDash(phase: 0, lengths: [])
        }
    }

    func line(_ x1: Float, _ y1: Float, _ x2: Float, _ y2: Float, _ width: Float, _ color: UInt32, _ dash: Float) {
        strokeStyle(width, color, dash)
        c.beginPath()
        c.move(to: CGPoint(x: CGFloat(x1), y: CGFloat(y1)))
        c.addLine(to: CGPoint(x: CGFloat(x2), y: CGFloat(y2)))
        c.strokePath()
    }

    func rect(_ x: Float, _ y: Float, _ w: Float, _ h: Float, _ fill: Bool, _ width: Float, _ color: UInt32, _ dash: Float) {
        let r = CGRect(x: CGFloat(x), y: CGFloat(y), width: CGFloat(w), height: CGFloat(h))
        if fill { c.setFillColor(ArgbColor.cgColor(color)); c.fill(r) } else { strokeStyle(width, color, dash); c.stroke(r) }
    }

    func circle(_ cx: Float, _ cy: Float, _ r: Float, _ fill: Bool, _ width: Float, _ color: UInt32, _ dash: Float) {
        let e = CGRect(x: CGFloat(cx - r), y: CGFloat(cy - r), width: CGFloat(2 * r), height: CGFloat(2 * r))
        if fill { c.setFillColor(ArgbColor.cgColor(color)); c.fillEllipse(in: e) } else { strokeStyle(width, color, dash); c.strokeEllipse(in: e) }
    }

    func poly(_ xy: [Float], _ closed: Bool, _ fill: Bool, _ width: Float, _ color: UInt32, _ dash: Float) {
        if xy.count < 4 { return }
        c.beginPath()
        c.move(to: CGPoint(x: CGFloat(xy[0]), y: CGFloat(xy[1])))
        for i in 1..<(xy.count / 2) { c.addLine(to: CGPoint(x: CGFloat(xy[2 * i]), y: CGFloat(xy[2 * i + 1]))) }
        if closed { c.closePath() }
        if fill { c.setFillColor(ArgbColor.cgColor(color)); c.fillPath() } else { strokeStyle(width, color, dash); c.strokePath() }
    }
}

/// v23 desenlerin ve hazır resimlerin bellekteki kaydı (Android `PaperLibrary`). Hazır resimler istenince üretilip
/// önbellekte tutulur; galeriden eklenen resimler [assetImage] ile okunur.
enum PaperLibrary {
    static let KEY = "paper:"

    private static let images: NSCache<NSString, CGImage> = {
        let c = NSCache<NSString, CGImage>()
        c.totalCostLimit = 48 * 1024 * 1024
        return c
    }()
    private static let lock = NSLock()

    /// Galeriden eklenen resmin görüntüsü (kimlik "asset:" öneki olmadan). Resim deposu bağlar.
    static var assetImage: ((String) -> CGImage?)?

    static func pattern(_ id: String?) -> PaperPattern? { id.flatMap { PaperCatalog.byId($0) } }

    static func layout(_ id: String) -> ImageLayout { PaperImages.byId(id)?.layout ?? ImageLayout(kind: ImageLayout.COVER) }

    /// Hazır resim (ilk istekte üretilir).
    static func image(_ id: String) -> CGImage? {
        if let img = images.object(forKey: id as NSString) { return img }
        guard let def = PaperImages.byId(id) else { return nil }
        lock.lock(); defer { lock.unlock() }
        if let img = images.object(forKey: id as NSString) { return img }
        let img = Bitmaps.image(argb: def.generate(), def.w, def.h)
        images.setObject(img, forKey: id as NSString, cost: def.w * def.h * 4)
        return img
    }

    /// Resimleri arka planda önceden üretir (ilk çizim beklemesin).
    static func prefetch(_ ids: [String]) {
        let todo = ids.filter { images.object(forKey: $0 as NSString) == nil && PaperImages.byId($0) != nil }
        if todo.isEmpty { return }
        DispatchQueue.global(qos: .utility).async { for id in todo { _ = image(id) } }
    }
}

/// v23 arka plan çizimi (Android `BackgroundRenderer`): tuvalin arka plan resmi ve deseni (kağıt renginin üstünde,
/// çizimlerin altında) ve yazı kutularının arka planı. Ekranda desen karosu bir kez resme çizilip döşenir; dışa
/// aktarmada vektör çizilir.
enum BackgroundRenderer {
    private static let tiles: NSCache<NSString, CGImage> = {
        let c = NSCache<NSString, CGImage>()
        c.totalCostLimit = 8 * 1024 * 1024
        return c
    }()

    static func parseColor(_ hex: String?, _ fallback: UInt32) -> UInt32 { hex.flatMap { ArgbColor.parse($0) } ?? fallback }

    /// Arka plan resminin görüntüsü.
    static func backgroundImage(_ spec: BackgroundSpec) -> CGImage? {
        if let id = spec.paperImageId { return PaperLibrary.image(id) }
        if let id = spec.assetId { return PaperLibrary.assetImage?(id) }
        return nil
    }

    /// Tuvalin arka planı: [l]…[b] kağıdın bu bağlamdaki dikdörtgeni, [scale] tuval biriminin bu bağlamdaki boyu.
    /// [vector] true ise desen karoları tek tek çizilir; [density] bağlam biriminin piksel sayısı (karo keskinliği).
    static func drawCanvasBackground(_ c: CGContext, _ spec: BackgroundSpec, _ l: Float, _ t: Float, _ r: Float, _ b: Float,
                                     scale: Float, paper: UInt32, vector: Bool, density: Float = 1) {
        if spec.isEmpty || r <= l || b <= t { return }
        c.saveGState()
        defer { c.restoreGState() }
        c.clip(to: rect(l, t, r, b))
        Gradients.fill(c, spec.gradient, l: l, t: t, r: r, b: b)
        if let img = backgroundImage(spec) {
            let layout = spec.paperImageId.map { PaperLibrary.layout($0) }
            c.saveGState()
            if spec.hasImageTransform {
                c.translateBy(x: CGFloat(spec.imageOffsetX * (r - l)), y: CGFloat(spec.imageOffsetY * (b - t)))
                let px = CGFloat(l + r) / 2, py = CGFloat(t + b) / 2
                c.translateBy(x: px, y: py)
                c.scaleBy(x: CGFloat(spec.imageScale), y: CGFloat(spec.imageScale))
                c.translateBy(x: -px, y: -py)
            }
            drawImage(c, img, spec.imageFit, layout, l, t, r, b, scale: scale, opacity: spec.imageOpacity)
            c.restoreGState()
        }
        if let p = PaperLibrary.pattern(spec.patternId) {
            let colors = PatternColors(parseColor(spec.patternColorHex, p.color), paper, p.accent, min(max(spec.patternOpacity, 0), 1))
            drawPattern(c, p, colors, l, t, r, b, scale: spec.patternScale * scale, vector: vector, density: density)
        }
    }

    /// Deseni dikdörtgene çizer ([scale]: desenin bu bağlamdaki ölçeği).
    static func drawPattern(_ c: CGContext, _ p: PaperPattern, _ colors: PatternColors, _ l: Float, _ t: Float, _ r: Float, _ b: Float,
                            scale: Float, vector: Bool, density: Float = 1) {
        let surface = CGPatternSurface(c)
        let w = r - l, h = b - t
        let clip = c.boundingBoxOfClipPath
        let visible: [Float]? = clip.isNull || clip.isInfinite ? nil : [Float(clip.minX), Float(clip.minY), Float(clip.maxX), Float(clip.maxY)]
        guard !vector, p.hasTile, let tile = tileImage(p, colors, scale * density) else {
            PatternPainter.paint(p, surface, l, t, w, h, colors, scale: scale, visible: visible)
            return
        }
        if p.tint != 0 && p.tintAlpha > 0 {
            c.setFillColor(ArgbColor.cgColor(PatternColors.withAlpha(p.tint, p.tintAlpha * colors.opacity)))
            c.fill(rect(l, t, r, b))
        }
        let area = PatternPainter.tileArea(p, l, t, w, h)
        if area[2] > area[0] && area[3] > area[1] {
            c.saveGState()
            c.interpolationQuality = .high
            tileFill(c, tile, originX: area[0], originY: area[1], tileW: p.tileW * scale, tileH: p.tileH * scale,
                     in: rect(area[0], area[1], area[2], area[3]))
            c.restoreGState()
        }
        if !p.page.isEmpty { PatternPainter.paint(p, surface, l, t, w, h, colors, scale: scale, visible: visible, tiles: false, tint: false) }
    }

    /// Karo resmi: çözünürlük ölçeğin bir üst ikinin kuvvetine yuvarlanır (yakınlaştırırken her karede yeniden çizilmez).
    /// Çok büyük karolarda nil (vektör çizilir).
    static func tileImage(_ p: PaperPattern, _ colors: PatternColors, _ scale: Float) -> CGImage? {
        if scale <= 0 { return nil }
        let rs = min(max(Float(pow(2, ceil(log(Double(scale)) / log(2.0)))), 0.125), 8)
        let tw = p.tileW * rs, th = p.tileH * rs
        let bw = Int((Double(tw) + 0.5).rounded(.down)), bh = Int((Double(th) + 0.5).rounded(.down))
        if bw < 2 || bh < 2 || bw > 4096 || bh > 4096 || bw * bh > 1_500_000 { return nil }
        let key = "\(p.id)|\(colors.main)|\(colors.paper)|\(colors.accent)|\(colors.opacity)|\(rs)" as NSString
        if let img = tiles.object(forKey: key) { return img }
        let img = Bitmaps.draw(bw, bh) { ctx in
            ctx.scaleBy(x: CGFloat(Float(bw) / tw), y: CGFloat(Float(bh) / th))
            PatternPainter.paintTile(p, CGPatternSurface(ctx), colors, rs)
        }
        tiles.setObject(img, forKey: key, cost: bw * bh * 4)
        return img
    }

    /// Önbellekteki karoları boşaltır (bellek uyarısında).
    static func trim() { tiles.removeAllObjects() }

    /// [img]'yi ([originX], [originY]) noktasından başlayarak [tileW]×[tileH] karolarla [dst] içine döşer (dik kalır).
    static func tileFill(_ c: CGContext, _ img: CGImage, originX: Float, originY: Float, tileW: Float, tileH: Float, in dst: CGRect) {
        if tileW <= 0 || tileH <= 0 { return }
        c.saveGState()
        c.clip(to: dst)
        c.translateBy(x: CGFloat(originX), y: CGFloat(originY))
        c.scaleBy(x: 1, y: -1)
        c.draw(img, in: CGRect(x: 0, y: -CGFloat(tileH), width: CGFloat(tileW), height: CGFloat(tileH)), byTiling: true)
        c.restoreGState()
    }

    /// Resmi dikdörtgene yerleşimine göre çizer. [scale] resim pikselinin bu bağlamdaki boyu (döşeme, dokuz parça).
    static func drawImage(_ c: CGContext, _ img: CGImage, _ fit: BoxFit, _ layout: ImageLayout?, _ l: Float, _ t: Float, _ r: Float, _ b: Float,
                          scale: Float, opacity: Float) {
        c.saveGState()
        defer { c.restoreGState() }
        c.setAlpha(CGFloat(Float(ktRound(min(max(opacity, 0), 1) * 255)) / 255))
        c.interpolationQuality = .high
        let dst = rect(l, t, r, b)
        let iw = Float(img.width), ih = Float(img.height)
        switch fit {
        case .STRETCH:
            Bitmaps.drawUpright(c, img, in: dst)
        case .TILE:
            tileFill(c, img, originX: l, originY: t, tileW: iw * scale, tileH: ih * scale, in: dst)
        case .NINE_SLICE:
            if let lay = layout, lay.kind == ImageLayout.NINE { drawNine(c, img, lay, l, t, r, b, scale) } else { Bitmaps.drawUpright(c, img, in: dst) }
        case .COVER:
            let k = max((r - l) / iw, (b - t) / ih)
            let sw = (r - l) / k, sh = (b - t) / k
            let sx = (iw - sw) / 2, sy = (ih - sh) / 2
            let src = CGRect(x: ktRound(sx), y: ktRound(sy), width: ktRound(sx + sw) - ktRound(sx), height: ktRound(sy + sh) - ktRound(sy))
            if let part = img.cropping(to: src) { Bitmaps.drawUpright(c, part, in: dst) }
        }
    }

    /// Dokuz parçalı çizim: köşeler [scale] ölçeğinde aynı kalır; kenar ve orta parçalar esnetilir ya da (repeat) tam sayıda
    /// tekrarlanır (kopyalar hafifçe esnetilerek alanı tam doldurur).
    private static func drawNine(_ c: CGContext, _ img: CGImage, _ lay: ImageLayout, _ l: Float, _ t: Float, _ r: Float, _ b: Float, _ scale: Float) {
        let ins = lay.insets
        let iw = img.width, ih = img.height
        let sl = clampI(ins[0], 0, iw / 2), sr = clampI(ins[2], 0, iw - sl - 1)
        let st = clampI(ins[1], 0, ih / 2), sb = clampI(ins[3], 0, ih - st - 1)
        var k = scale
        let needW = Float(sl + sr) * k, needH = Float(st + sb) * k
        if needW > r - l { k *= (r - l) / needW }
        if needH > b - t { k = min(k, scale * (b - t) / needH) }
        let xs: [Float] = [l, l + Float(sl) * k, r - Float(sr) * k, r]
        let ys: [Float] = [t, t + Float(st) * k, b - Float(sb) * k, b]
        let sxs = [0, sl, iw - sr, iw], sys = [0, st, ih - sb, ih]
        for j in 0...2 {
            for i in 0...2 {
                let dl = xs[i], dr = xs[i + 1], dt = ys[j], db = ys[j + 1]
                if dr - dl < 0.01 || db - dt < 0.01 { continue }
                let srcW = sxs[i + 1] - sxs[i], srcH = sys[j + 1] - sys[j]
                if srcW <= 0 || srcH <= 0 { continue }
                guard let part = img.cropping(to: CGRect(x: sxs[i], y: sys[j], width: srcW, height: srcH)) else { continue }
                let nx = lay.repeat_ && i == 1 ? max(1, ktRound((dr - dl) / (Float(srcW) * k))) : 1
                let ny = lay.repeat_ && j == 1 ? max(1, ktRound((db - dt) / (Float(srcH) * k))) : 1
                let cw = (dr - dl) / Float(nx), ch = (db - dt) / Float(ny)
                for yy in 0..<ny {
                    for xx in 0..<nx {
                        // Kopyalar arasında yarım piksel örtüşme: kenar yumuşatması dikiş bırakmasın
                        let x0 = dl + Float(xx) * cw, y0 = dt + Float(yy) * ch
                        Bitmaps.drawUpright(c, part, in: rect(x0, y0, dl + Float(xx + 1) * cw + 0.5, dt + Float(yy + 1) * ch + 0.5))
                    }
                }
            }
        }
    }

    /// Yazı kutusunun resmi (hazır resim ya da galeriden).
    static func boxImage(_ box: TextBox) -> CGImage? {
        guard let id = box.imageId else { return nil }
        if id.hasPrefix(BackgroundSpec.ASSET) { return PaperLibrary.assetImage?(String(id.dropFirst(BackgroundSpec.ASSET.count))) }
        return PaperLibrary.image(id)
    }

    /// Hazır kutu resminin yazı için iç boşluğu (resim pikseli; yoksa nil).
    static func boxContent(_ box: TextBox) -> [Int]? {
        guard let id = box.imageId, !id.hasPrefix(BackgroundSpec.ASSET), box.imageFit == .NINE_SLICE else { return nil }
        let lay = PaperLibrary.layout(id)
        return lay.kind == ImageLayout.NINE ? lay.content : nil
    }

    /// Yazı kutusunun arka planı (kutu biriminde 0..[w], 0..[h]). [paper] kutunun altındaki kağıt rengi (desen tonları),
    /// [coverage] silinme denetimi için kutu tam opak çizilir.
    static func drawBox(_ c: CGContext, _ box: TextBox, _ w: Float, _ h: Float, paper: UInt32 = ArgbColor.white, alpha: Float, coverage: Bool) {
        guard box.hasBackground else { return }
        let rad = CGFloat(min(max(box.corner, 0), min(w, h) / 2))
        let r = CGRect(x: 0, y: 0, width: CGFloat(w), height: CGFloat(h))
        let path = CGPath(roundedRect: r, cornerWidth: rad, cornerHeight: rad, transform: nil)
        if coverage {
            c.setFillColor(ArgbColor.cgColor(ArgbColor.black)); c.addPath(path); c.fillPath(); return
        }
        c.saveGState()
        c.addPath(path); c.clip()
        if !Gradients.fill(c, box.fillGradient, l: 0, t: 0, r: w, b: h, alpha: alpha), let fill = box.fillHex {
            c.setFillColor(ArgbColor.cgColor(PatternColors.withAlpha(parseColor(fill, ArgbColor.white), alpha)))
            c.fill(r)
        }
        if let img = boxImage(box) {
            let layout = box.imageId.flatMap { $0.hasPrefix(BackgroundSpec.ASSET) ? nil : PaperLibrary.layout($0) }
            drawImage(c, img, box.imageFit, layout, 0, 0, w, h, scale: box.patternScale, opacity: alpha)
        }
        if let p = PaperLibrary.pattern(box.patternId) {
            let under = box.fillHex.map { parseColor($0, paper) } ?? paper
            let colors = PatternColors(parseColor(box.patternColorHex, p.color), under, p.accent, alpha)
            drawPattern(c, p, colors, 0, 0, w, h, scale: box.patternScale, vector: true)
        }
        c.restoreGState()
        if let border = box.borderHex {
            let bw = max(1, min(w, h) * 0.012)
            let inset = CGFloat(bw / 2)
            c.setStrokeColor(ArgbColor.cgColor(PatternColors.withAlpha(parseColor(border, 0xFF33_3333), alpha)))
            c.setLineWidth(CGFloat(bw))
            c.setLineDash(phase: 0, lengths: [])
            let r2 = max(0, rad - inset)
            c.addPath(CGPath(roundedRect: r.insetBy(dx: inset, dy: inset), cornerWidth: r2, cornerHeight: r2, transform: nil))
            c.strokePath()
        }
    }

    @inline(__always) private static func rect(_ l: Float, _ t: Float, _ r: Float, _ b: Float) -> CGRect {
        CGRect(x: CGFloat(l), y: CGFloat(t), width: CGFloat(r - l), height: CGFloat(b - t))
    }
}

/// v40 arka plan resminin taşınması ve boyutlandırılması (Android `BackgroundTransform`, tuval biriminde).
enum BackgroundTransform {
    static let MIN_SCALE: Float = 0.1
    static let MAX_SCALE: Float = 8
    static let TOP_LEFT = 0, TOP_RIGHT = 1, BOTTOM_RIGHT = 2, BOTTOM_LEFT = 3

    /// Resmin çerçevesi ([w]×[h] kağıtta): sol, üst, sağ, alt.
    static func frame(_ spec: BackgroundSpec, _ w: Float, _ h: Float) -> (l: Float, t: Float, r: Float, b: Float) {
        let cx = w / 2 + spec.imageOffsetX * w, cy = h / 2 + spec.imageOffsetY * h
        let hw = w * spec.imageScale / 2, hh = h * spec.imageScale / 2
        return (cx - hw, cy - hh, cx + hw, cy + hh)
    }

    static func corners(_ spec: BackgroundSpec, _ w: Float, _ h: Float) -> [P] {
        let f = frame(spec, w, h)
        return [P(f.l, f.t), P(f.r, f.t), P(f.r, f.b), P(f.l, f.b)]
    }

    private static func center(_ spec: BackgroundSpec, _ w: Float, _ h: Float) -> P {
        let f = frame(spec, w, h)
        return P((f.l + f.r) / 2, (f.t + f.b) / 2)
    }

    private static func with(_ spec: BackgroundSpec, _ w: Float, _ h: Float, _ center: P, _ scale: Float) -> BackgroundSpec {
        if w <= 0 || h <= 0 { return spec }
        // Çerçeve kağıttan tamamen kaçmasın
        let limit = 0.5 + scale / 2
        var s = spec
        s.imageOffsetX = clampF((center.x - w / 2) / w, -limit + 0.05, limit - 0.05)
        s.imageOffsetY = clampF((center.y - h / 2) / h, -limit + 0.05, limit - 0.05)
        s.imageScale = scale
        return s
    }

    /// Sürükleme ([pan]) ve iki parmakla yakınlaştırma ([zoom], [focus] çevresinde).
    static func pinch(_ spec: BackgroundSpec, _ w: Float, _ h: Float, pan: P, zoom: Float, focus: P) -> BackgroundSpec {
        let scale = clampF(spec.imageScale * zoom, MIN_SCALE, MAX_SCALE)
        let k = scale / spec.imageScale
        let c = focus + (center(spec, w, h) - focus) * k + pan
        return with(spec, w, h, c, scale)
    }

    static func moved(_ spec: BackgroundSpec, _ w: Float, _ h: Float, _ delta: P) -> BackgroundSpec {
        pinch(spec, w, h, pan: delta, zoom: 1, focus: P(0, 0))
    }

    /// Köşe tutamağı [corner] [point]'e sürüklendi: karşı köşe yerinde kalır, en-boy oranı korunur.
    static func corner(_ spec: BackgroundSpec, _ w: Float, _ h: Float, _ corner: Int, _ point: P) -> BackgroundSpec {
        let pts = corners(spec, w, h)
        let ci = clampI(corner, 0, 3)
        let dragged = pts[ci], opposite = pts[(ci + 2) % 4]
        let diag = dragged - opposite
        let len = hypotf(diag.x, diag.y)
        if len < 1e-3 { return spec }
        let along = ((point.x - opposite.x) * diag.x + (point.y - opposite.y) * diag.y) / len
        let scale = clampF(spec.imageScale * along / len, MIN_SCALE, MAX_SCALE)
        let k = scale / spec.imageScale
        let c = opposite + (center(spec, w, h) - opposite) * k
        return with(spec, w, h, c, scale)
    }

    static func reset(_ spec: BackgroundSpec) -> BackgroundSpec {
        var s = spec
        s.imageOffsetX = 0; s.imageOffsetY = 0; s.imageScale = 1
        return s
    }
}
