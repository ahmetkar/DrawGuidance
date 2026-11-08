import Foundation

/// Vektörleştirme ayarları (Android `TraceOptions`).
struct TraceOptions: Equatable {
    var colors: Int = 8
    var detail: Float = 0.5
    var smooth: Bool = true
    var maxSide: Int = 480
    var minRegion: Float = 0.0003
    var ignoreTransparent: Bool = true
    var outline: Bool = false
}

/// Vektörleştirme sonucu: birim kutuda (0..1) arkadan öne kapalı dolgulu parçalar, kullanılan renkler, izleme çözünürlüğü.
struct TraceResult {
    let paths: [VPath]
    let palette: [String]
    let width: Int
    let height: Int
}

/// Bitmap → renkli vektör şekiller (Android `Vectorizer` ile birebir): küçültme → gürültü süzgeci → Lab k-means
/// renk nicemleme → çoğunluk süzgeci → bölgeler → piksel kenarı takibi → Bézier/RDP → ressam sırasıyla kapalı yollar.
enum Vectorizer {
    static let MAX_PARTS = 1500
    static let MAX_POINTS = 60_000
    private static let SOFT_DE: Float = 22

    /// argb: satır satır 0xAARRGGBB. cancel true dönerse TraceCancelled fırlatılır. Arka planda çağrılmalıdır.
    static func trace(_ argb: [UInt32], _ width: Int, _ height: Int, options: TraceOptions = TraceOptions(),
                      cancel: () -> Bool = { false }) throws -> TraceResult {
        precondition(width > 0 && height > 0 && argb.count >= width * height)
        try checkCancel(cancel)
        let prm = Params(options)
        let img0 = try Downscale.run(argb, width, height, prm.maxSide, options.ignoreTransparent, cancel)
        try checkCancel(cancel)
        let img = try traceDenoise(img0, prm.denoiseThreshold, prm.denoisePasses, cancel)
        let q = try Quantizer.run(img, prm.colors, prm.mergeDE, cancel)
        if q.paletteRgb.isEmpty { return TraceResult(paths: [], palette: [], width: img.w, height: img.h) }
        try checkCancel(cancel)
        var labels = q.labels
        let soft = traceSoftPairs(q.paletteRgb, SOFT_DE)
        for _ in 0..<prm.modePasses {
            labels = traceModeFilter(labels, img.w, img.h, q.paletteRgb.count, prm.modeMaxOwn, prm.modeMinBest, soft)
            try checkCancel(cancel)
        }
        let minArea = prm.minRegion * Double(img.w) * Double(img.h)
        let map = try Regions.build(labels, img.w, img.h, q.paletteRgb, minArea, prm.thinRatio, MAX_PARTS, cancel)
        let tracer = Tracer(map)
        try tracer.traceAll(cancel)

        // Ressam sırası: dış sınır alanı büyükten küçüğe
        let order = (0..<map.count).filter { tracer.outer[$0] != nil }
            .sorted { tracer.outer[$0]!.area != tracer.outer[$1]!.area ? tracer.outer[$0]!.area > tracer.outer[$1]!.area : $0 < $1 }

        var scale: Float = 1
        var polys: [(Int, [Float])] = []
        for _ in 0..<8 {
            try tracer.fit(FitParams(eps: prm.eps * scale, smooth: options.smooth, cornerCos: prm.cornerCos, cornerScale: prm.cornerScale,
                                     flatTol: prm.flatTol * scale), cancel)
            polys = order.compactMap { r -> (Int, [Float])? in
                let outer = tracer.polygon(tracer.outer[r]!)
                if outer.count < 6 { return nil }
                let holes = tracer.holes[r]?.map { tracer.polygon($0) }
                let poly = holes == nil || holes!.isEmpty ? outer : traceBridgeHoles(outer, holes!)
                return abs(traceSignedArea(outer)) < 0.25 ? nil : (r, poly)
            }
            let total = polys.reduce(0) { $0 + $1.1.count / 2 }
            if total <= MAX_POINTS { break }
            scale *= 1.6
        }
        // Son çare: en küçük parçalar atılır
        if polys.reduce(0, { $0 + $1.1.count / 2 }) > MAX_POINTS {
            var kept: [(Int, [Float])] = []
            var sum = 0
            for pp in polys where sum + pp.1.count / 2 <= MAX_POINTS { kept.append(pp); sum += pp.1.count / 2 }
            polys = kept
        }
        try checkCancel(cancel)

        let iw = 1 / Float(img.w), ih = 1 / Float(img.h)
        let hex = q.paletteRgb.map { String(format: "#%06X", $0 & 0xFFFFFF) }
        let dark = q.paletteRgb.map { c -> String in
            let r = Float((c >> 16) & 255) * 0.62, g = Float((c >> 8) & 255) * 0.62, b = Float(c & 255) * 0.62
            return String(format: "#%02X%02X%02X", Int(r), Int(g), Int(b))
        }
        let paths = polys.map { (r, poly) -> VPath in
            let lab = map.label[r]
            let pts = (0..<(poly.count / 2)).map { i in P(min(max(poly[i * 2] * iw, 0), 1), min(max(poly[i * 2 + 1] * ih, 0), 1)) }
            return VPath(points: pts, closed: true, fill: hex[lab], stroke: options.outline ? dark[lab] : hex[lab],
                         width: options.outline ? 0.35 : 0.25)
        }
        let used = Set(polys.map { map.label[$0.0] })
        let palette = hex.indices.filter { used.contains($0) }.map { hex[$0] }
        return TraceResult(paths: paths, palette: palette, width: img.w, height: img.h)
    }

    /// Ayarlardan türetilen iç parametreler.
    private struct Params {
        let colors: Int, detail: Float, coarse: Float, maxSide: Int, eps: Float, flatTol: Float
        let denoiseThreshold = 40
        let denoisePasses: Int, cornerCos: Float, cornerScale: Float, minRegion: Double, thinRatio: Float, mergeDE: Float
        let modePasses: Int, modeMaxOwn: Int, modeMinBest: Int

        init(_ o: TraceOptions) {
            colors = min(max(o.colors, 2), 24)
            detail = o.detail.isNaN ? 0.5 : min(max(o.detail, 0), 1)
            coarse = 1 - detail
            maxSide = min(max(o.maxSide, 16), 4096)
            eps = 0.9 + 1.4 * coarse
            flatTol = 0.12 + 0.2 * coarse
            denoisePasses = detail < 0.75 ? 2 : 1
            cornerCos = Float(cos((40.0 + 15.0 * Double(coarse)) * 0.017453292519943295))
            cornerScale = 2 + coarse
            let mr: Float = o.minRegion.isNaN ? 0 : min(max(o.minRegion, 0), 0.2)
            minRegion = Double(mr) * pow(2.0, 1.0 - 2.0 * Double(detail))
            thinRatio = 0.56 + 0.3 * coarse
            mergeDE = 2.5 + 5 * coarse
            modePasses = detail < 0.35 ? 3 : detail < 0.8 ? 2 : 1
            modeMaxOwn = detail < 0.35 ? 3 : detail < 0.8 ? 2 : 1
            modeMinBest = detail < 0.35 ? 5 : 4
        }
    }
}
