import Foundation

/// v56 V11 belgeyi V10 bilen (eski) uygulamaya uygun hale getirir (Android `V11Flatten` ile aynı kural):
/// katman opaklığı elemanlara çarpılır, karışım/alfa kilidi/kırpma ve belge ek bilgileri kalkar; maske ve dalga kalkar,
/// bozma/çarpıtmada resim dörtgenin (ağın dış köşelerinin) köşelerine yerleşir; gren ve resimden fırça kalkar;
/// kement dolgu aynı renkte dolu kapalı şekle çevrilir.
enum V11Flatten {
    static func downgrade(_ doc: ContentCodec.Document) -> ContentCodec.Document {
        let layers = doc.layers.map { l -> Layer in
            var c = l
            c.opacity = 1; c.blend = .NORMAL; c.alphaLock = false; c.clipToBelow = false
            return c
        }
        var alpha: [String: Float] = [:]
        for l in doc.layers { alpha[l.id] = min(max(l.opacity, 0), 1) }
        let pages = doc.pages.map { page in page.map { s in stroke(s, alpha[Layers.of(s, doc.layers)] ?? 1) } }
        return ContentCodec.Document(pages: pages, layers: layers, extras: DocExtras())
    }

    private static func stroke(_ s0: Stroke, _ layerAlpha: Float) -> Stroke {
        var s = s0
        if layerAlpha < 1 { s.opacity = s.opacity * layerAlpha }
        s.mask = nil
        // 2E görünüm, bağlı kopya ve tekrar kalkar (kopyalar sıradan eleman olarak kalır)
        s.style2 = nil; s.cloneOf = nil; s.repeat = nil
        if let w = s.warp {
            var corners: [StrokePoint]? = nil
            if let q = w.quad, q.count == 4 { corners = q }
            else if let m = w.mesh, WarpMesh.sizeOf(m) > 0 { let n = WarpMesh.sizeOf(m); corners = [m[0], m[n - 1], m[n * n - 1], m[n * (n - 1)]] }
            s.warp = nil
            if s.brushType == .IMAGE, let corners { s.points = corners }
        }
        if var p = s.params, p.grain != nil || p.stampAssetId != nil {
            p.grain = nil; p.stampAssetId = nil
            s.params = p
        }
        if s.brushType == .LASSO_FILL {
            s.brushType = .SHAPE
            s.shape = ShapeSpec(
                kind: "lasso",
                subpaths: [SubPath(count: s.points.count, closed: true)],
                style: ShapeStyle(strokeColorHex: s.colorHex, strokeWidth: 0, fillColorHex: s.colorHex, fillOpacity: 1)
            )
        }
        return s
    }

    /// Eski uygulamaya gidecek içerik: gerekiyorsa düzleştirilip yeniden kodlanır. İkinci değer: düzleştirildi mi.
    static func forPeer(_ content: Data, _ peerCodec: Int) -> (Data, Bool) {
        if peerCodec >= ContentCodec.LATEST_VERSION { return (content, false) }
        guard let doc = try? ContentCodec.decodeDocument(content), ContentCodec.requiresV11(doc) else { return (content, false) }
        let flat = downgrade(doc)
        return (ContentCodec.encodePages(flat.pages, layers: flat.layers), true)
    }
}
