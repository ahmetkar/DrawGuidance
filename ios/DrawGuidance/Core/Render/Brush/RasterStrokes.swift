import CoreGraphics
import Foundation

/// v51 piksel tabanlı fırça izlerinin (boya ve yardımcı fırçalar) çizimi (Android `RasterStrokes`): izin altındaki aynı
/// katman içeriği bir bitmap'e çizilir, `RasterBrush` ile işlenir, sonuç bağlama "maskeyle sil + sonucu ekle" olarak konur.
/// Sonuçlar çizim ve altındaki içerikle birlikte önbelleğe alınır: yakınlaştırma/kaydırmada yeniden hesaplanmaz.
enum RasterStrokes {
    /// Bir iz için hesaplanan en çok piksel sayısı (ölçek buna göre düşer).
    static let MAX_PIXELS: Float = 2_500_000
    static let MAX_SCALE: Float = 2

    private final class Entry {
        let stroke: Stroke
        let context: [Stroke]?
        var paper: UInt32 = 0
        let rect: CGRect
        let color: CGImage
        let mask: CGImage
        init(stroke: Stroke, context: [Stroke]?, rect: CGRect, color: CGImage, mask: CGImage) {
            self.stroke = stroke; self.context = context; self.rect = rect; self.color = color; self.mask = mask
        }
    }

    /// v52 düzenleyicideki sayfanın kağıt rengi (ARGB; 0 = yok). Yardımcı fırçalar alttaki içeriği kağıdın üstünde okur:
    /// boş kağıttan klonlanan yer kağıt rengini alır, karıştırıcı kağıtla karışır (eskiden kağıt saydam okunuyordu).
    nonisolated(unsafe) static var paperArgb: UInt32 = 0
    private static let paperKey = "dg.raster.paper"
    /// Dışa aktarma/küçük resim çiziminde o çizimin kağıdı (iş parçacığına özel).
    static func withPaper<T>(_ paper: UInt32, _ body: () -> T) -> T {
        let d = Thread.current.threadDictionary
        let old = d[paperKey]
        d[paperKey] = NSNumber(value: paper)
        defer { d[paperKey] = old }
        return body()
    }
    private static func paperNow() -> UInt32 {
        (Thread.current.threadDictionary[paperKey] as? NSNumber)?.uint32Value ?? paperArgb
    }

    private static let cache: NSCache<NSString, Entry> = {
        let c = NSCache<NSString, Entry>()
        c.totalCostLimit = 64 * 1024 * 1024
        return c
    }()

    /// İzi çizer. [context] izden önce çizilen aynı katman içeriği (nil: bağlam yok, ör. fırça önizlemesi ya da bekleyen
    /// çizim): boya fırçaları boş zemine çizilir, yardımcı fırçalar hiçbir şey çizmez.
    static func draw(_ c: CGContext, _ stroke: Stroke, context: [Stroke]?) {
        if stroke.points.isEmpty { return }
        if context == nil && stroke.brushType.isHelper { return }
        guard let e = entryFor(stroke, context) else { return }
        c.saveGState()
        if context != nil {
            c.setBlendMode(.destinationOut)
            Bitmaps.drawUpright(c, e.mask, in: e.rect)
            c.setBlendMode(.plusLighter)
            Bitmaps.drawUpright(c, e.color, in: e.rect)
        } else {
            Bitmaps.drawUpright(c, e.color, in: e.rect)
        }
        c.restoreGState()
    }

    /// İzin okuduğu ve yazdığı bölge (tuval birimi).
    static func region(_ stroke: Stroke) -> CGRect? {
        guard let b = StrokeRenderer.paintBounds(stroke), !b.isNull else { return nil }
        let width = stroke.brushType.inkWidth(stroke.sizePx, strength: stroke.strength)
        let margin = CGFloat(RasterBrush.readMargin(stroke.brushType, width, stroke.strength))
        var r = b.insetBy(dx: -margin, dy: -margin)
        if let off = RasterBrush.sourceOffset(stroke.brushType, stroke.params, width) {
            r = r.union(b.offsetBy(dx: CGFloat(off.0), dy: CGFloat(off.1)))
        }
        return r
    }

    private static func entryFor(_ stroke: Stroke, _ context: [Stroke]?) -> Entry? {
        guard let region = region(stroke) else { return nil }
        let relevant = context?.filter { s in
            guard !s.hidden, let b = StrokeRenderer.paintBounds(s) else { return false }
            return b.intersects(region)
        }
        let paper: UInt32 = (relevant == nil || !stroke.brushType.isHelper) ? 0 : paperNow()
        let key = stroke.id as NSString
        let cached = cache.object(forKey: key)
        if let e = cached, e.stroke == stroke, e.context == relevant, e.paper == paper { return e }

        let rw = Float(region.width), rh = Float(region.height)
        if rw <= 0 || rh <= 0 { return nil }
        let scale = min(MAX_SCALE, (MAX_PIXELS / (rw * rh)).squareRoot())
        let w = max(1, Int(ceil(rw * scale))), h = max(1, Int(ceil(rh * scale)))
        // v52 donma düzeltmesi: ana iş parçacığında büyük hesap yapılmaz; arka planda hesaplanır, o sırada eski sonuç
        // (varsa) çizilir, bitince `didUpdate` bildirimiyle yeniden çizim istenir. Küçük izler (fırça önizlemesi) hemen.
        if Thread.isMainThread && w * h > SYNC_PIXELS && !isTesting {
            let token = "\(stroke.id)|\(stroke.points.count)|\(relevant?.count ?? -1)|\(paper)"
            lock.lock()
            let fresh = inFlight.insert(token).inserted
            lock.unlock()
            if fresh {
                worker.async {
                    _ = compute(stroke, region, relevant, paper, stroke.id, scale, w, h)
                    lock.lock(); inFlight.remove(token); lock.unlock()
                    DispatchQueue.main.async { NotificationCenter.default.post(name: didUpdate, object: nil) }
                }
            }
            return cached
        }
        return compute(stroke, region, relevant, paper, stroke.id, scale, w, h)
    }

    /// Arka planda bir iz hesaplanıp önbelleğe girince gönderilir (düzenleyici yeniden çizer).
    static let didUpdate = Notification.Name("dg.raster.updated")
    /// Ana iş parçacığında hemen hesaplanan en çok piksel sayısı.
    private static let SYNC_PIXELS = 160_000
    private static let worker = DispatchQueue(label: "dg.raster.worker", qos: .userInitiated)
    private static let lock = NSLock()
    nonisolated(unsafe) private static var inFlight = Set<String>()
    private static let isTesting = ProcessInfo.processInfo.environment["XCTestConfigurationFilePath"] != nil

    private static func compute(_ stroke: Stroke, _ region: CGRect, _ relevant: [Stroke]?, _ paper: UInt32, _ id: String,
                                _ scale: Float, _ w: Int, _ h: Int) -> Entry? {
        let left = Float(floor(region.minX)), top = Float(floor(region.minY))
        let patch = RasterBrush.Patch(left: left, top: top, scale: scale, w: w, h: h)
        if let relevant, !relevant.isEmpty || (paper >> 24) != 0 { renderBase(relevant, patch, paper: paper) }
        let xs = stroke.points.map(\.x), ys = stroke.points.map(\.y)
        let d = BrushPainter.dynamicsOf(stroke.points)
        let result = RasterBrush.render(patch, type: stroke.brushType, color: ArgbColor.hex(stroke.colorHex), sizePx: stroke.sizePx,
                                        opacity: stroke.opacity, flow: stroke.flow, strength: stroke.strength,
                                        seed: stroke.id.javaHashCode, xs: xs, ys: ys, count: xs.count,
                                        pp: d.pressures, pt: d.tilts, po: d.orients, pr: d.rolls, pb: d.barrels, params: stroke.params)
        let n = w * h
        var colors = [UInt8](repeating: 0, count: n * 4)
        var masks = [UInt8](repeating: 0, count: n * 4)
        let col = result.color, m = result.mask
        @inline(__always) func byte(_ v: Float) -> UInt8 { UInt8(min(max(Int(v * 255 + 0.5), 0), 255)) }
        for i in 0..<n {
            let mk = min(max(m[i], 0), 1)
            if mk <= 0 { continue }
            masks[i * 4 + 3] = byte(mk)
            let o = i * 4
            let a = min(max(col[o + 3], 0), 1)
            if a <= 0 { continue }
            // Ön çarpımlı renk × maske (renk kanalı alfayı aşmasın)
            colors[o] = byte(min(col[o], a) * mk)
            colors[o + 1] = byte(min(col[o + 1], a) * mk)
            colors[o + 2] = byte(min(col[o + 2], a) * mk)
            colors[o + 3] = byte(a * mk)
        }
        guard let colorImage = image(colors, w, h), let maskImage = image(masks, w, h) else { return nil }
        let rect = CGRect(x: CGFloat(left), y: CGFloat(top), width: CGFloat(Float(w) / scale), height: CGFloat(Float(h) / scale))
        let e = Entry(stroke: stroke, context: relevant, rect: rect, color: colorImage, mask: maskImage)
        e.paper = paper
        cache.setObject(e, forKey: id as NSString, cost: n * 8)
        return e
    }

    private static func image(_ bytes: [UInt8], _ w: Int, _ h: Int) -> CGImage? {
        guard let provider = CGDataProvider(data: Data(bytes) as CFData) else { return nil }
        return CGImage(width: w, height: h, bitsPerComponent: 8, bitsPerPixel: 32, bytesPerRow: w * 4, space: Bitmaps.space,
                       bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedLast.rawValue),
                       provider: provider, decode: nil, shouldInterpolate: true, intent: .defaultIntent)
    }

    /// Alttaki içeriği (aynı katmanın önceki çizimleri) bölgenin pikseline çizer ve ön çarpımlı RGBA'ya (0..1) çevirir.
    private static func renderBase(_ strokes: [Stroke], _ patch: RasterBrush.Patch, paper: UInt32 = 0) {
        let w = patch.w, h = patch.h
        guard let ctx = CGContext(data: nil, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4, space: Bitmaps.space,
                                  bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return }
        // v52 kağıt rengi zemin (yardımcı fırçalar boş kağıdı da okur)
        if (paper >> 24) != 0 {
            ctx.setFillColor(ArgbColor.cgColor(paper))
            ctx.fill(CGRect(x: 0, y: 0, width: w, height: h))
        }
        ctx.translateBy(x: 0, y: CGFloat(h))
        ctx.scaleBy(x: 1, y: -1)
        ctx.scaleBy(x: CGFloat(patch.scale), y: CGFloat(patch.scale))
        ctx.translateBy(x: CGFloat(-patch.left), y: CGFloat(-patch.top))
        StrokeRenderer.drawStrokeList(ctx, strokes)
        guard let data = ctx.data else { return }
        let rowBytes = ctx.bytesPerRow
        let px = data.bindMemory(to: UInt8.self, capacity: rowBytes * h)
        patch.rgba.withUnsafeMutableBufferPointer { out in
            for y in 0..<h {
                let row = y * rowBytes
                for x in 0..<w {
                    let s = row + x * 4
                    let a = px[s + 3]
                    if a == 0 { continue }
                    let o = (y * w + x) * 4
                    out[o] = Float(px[s]) / 255
                    out[o + 1] = Float(px[s + 1]) / 255
                    out[o + 2] = Float(px[s + 2]) / 255
                    out[o + 3] = Float(a) / 255
                }
            }
        }
    }

    /// Test ve bellek baskısı için.
    static func clear() { cache.removeAllObjects() }
}
