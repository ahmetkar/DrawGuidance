import Foundation

/// v56 hızlı fırça seçici (planın 2. bölümü, saf; Android `V56QuickBrush.kt` ile aynı kurallar ve kayıt biçimi):
/// son kullanılan en çok `MAX` fırça ve en çok `MAX` sabitlenmiş favori, mod başına ayrı.
/// Kayıt biçimi: `"recent=PENCIL,MARKER;fav=INK_PEN"`.
struct QuickBrushLists: Equatable {
    static let MAX = 6
    var recent: [BrushType] = []
    var favorites: [BrushType] = []

    /// `type` kullanıldı: başa alınır, en çok `MAX`. Silgi ve nesneler girmez.
    func used(_ type: BrushType) -> QuickBrushLists {
        if type == .ERASER || type.isObject || type == .LASSO_FILL { return self }
        var c = self
        c.recent = Array(([type] + recent.filter { $0 != type }).prefix(Self.MAX))
        return c
    }

    /// Sabitle / kaldır. Doluysa yeni favori eklenmez (ok = false).
    func toggleFavorite(_ type: BrushType) -> (QuickBrushLists, Bool) {
        var c = self
        if let i = favorites.firstIndex(of: type) { c.favorites.remove(at: i); return (c, true) }
        if favorites.count >= Self.MAX || type == .ERASER || type.isObject { return (self, false) }
        c.favorites.append(type)
        return (c, true)
    }

    /// İki parmakla dokunma: şu anki fırçadan önceki.
    func previous(_ current: BrushType) -> BrushType? { recent.first { $0 != current } }

    /// Şerit sırası: önce favoriler, sonra favori olmayan son kullanılanlar.
    func strip() -> [BrushType] { favorites + recent.filter { !favorites.contains($0) } }

    func encode() -> String {
        "recent=" + recent.map(\.rawValue).joined(separator: ",") + ";fav=" + favorites.map(\.rawValue).joined(separator: ",")
    }

    static func decode(_ text: String?) -> QuickBrushLists {
        guard let text, !text.trimmingCharacters(in: .whitespaces).isEmpty else { return QuickBrushLists() }
        func list(_ key: String) -> [BrushType] {
            guard let part = text.split(separator: ";").first(where: { $0.hasPrefix("\(key)=") }) else { return [] }
            var out: [BrushType] = []
            for n in part.dropFirst(key.count + 1).split(separator: ",") {
                if let t = BrushType(rawValue: String(n)), !out.contains(t) { out.append(t) }
            }
            return Array(out.prefix(MAX))
        }
        return QuickBrushLists(recent: list("recent"), favorites: list("fav"))
    }
}

/// Hızlı fırça düğmesinin mantıksal köşesi (START: LTR'de sol, RTL'de sağ).
enum QuickCorner: String, CaseIterable { case TOP_START, TOP_END, BOTTOM_START, BOTTOM_END }

/// v56 hızlı fırça yerleşim kuralı (Android ile aynı, testli).
enum QuickBrushLayout {
    static let BUTTON: Float = 44
    static let SLOT: Float = 60

    /// Yatayda mod menüsünün üst boşluğu: hızlı fırça menünün tarafındaki üst köşedeyse yuvanın altından başlar.
    static func railTopInset(landscape: Bool, quickCorner: QuickCorner?, rtl: Bool) -> Float? {
        landscape && quickCorner == .TOP_START ? SLOT : nil
    }

    static func pushBelowSlot(_ menuTop: Float, _ quickCorner: QuickCorner?) -> Float {
        quickCorner == .TOP_START && menuTop < SLOT ? SLOT : menuTop
    }

    /// Fiziksel köşe: (sağda mı, altta mı); mantıksal köşe RTL'de aynalanır.
    static func physical(_ corner: QuickCorner, rtl: Bool) -> (right: Bool, bottom: Bool) {
        let end = corner == .TOP_END || corner == .BOTTOM_END
        let bottom = corner == .BOTTOM_START || corner == .BOTTOM_END
        return (end != rtl, bottom)
    }

    static func nearest(_ x: Float, _ y: Float, _ w: Float, _ h: Float, rtl: Bool) -> QuickCorner {
        let end = (x > w / 2) != rtl
        let bottom = y > h / 2
        switch (end, bottom) {
        case (false, false): return .TOP_START
        case (true, false): return .TOP_END
        case (false, true): return .BOTTOM_START
        default: return .BOTTOM_END
        }
    }

    /// Kalem düğmeye yaklaşınca düğmenin kenar boyunca kayması.
    static func dodge(_ px: Float, _ py: Float, _ bx: Float, _ by: Float, _ r: Float, margin: Float = 12) -> Float {
        let dx = px - bx, dy = py - by, near = r + margin
        return dx * dx + dy * dy < near * near ? near * 2 : 0
    }
}

/// v56 damlalığın canlı renk dairesi yerleşimi (2D-5, saf).
enum EyedropLoupe {
    static func radius(kids: Bool) -> Float { kids ? 56 : 40 }
    static let LIFT: Float = 60

    static func center(_ x: Float, _ y: Float, _ w: Float, _ h: Float, _ r: Float, lift: Float = LIFT) -> (Float, Float) {
        let cx = min(max(x, r), max(w - r, r))
        var cy = y - lift
        if cy - r < 0 { cy = y + lift }
        cy = min(max(cy, r), max(h - r, r))
        return (cx, cy)
    }

    static func inside(_ x: Float, _ y: Float, _ pageW: Float, _ pageH: Float) -> Bool {
        x >= 0 && x <= pageW && y >= 0 && y <= pageH
    }
}
