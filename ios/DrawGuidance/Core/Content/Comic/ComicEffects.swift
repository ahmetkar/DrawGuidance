import Foundation

/// v36 çizgi roman efektlerinin grupları (Android `EffectGroup`).
enum EffectGroup: String, CaseIterable {
    case ACTION = "fx_action", MAGIC = "fx_magic", EMOTION = "fx_emotion", ATMOSPHERE = "fx_atmosphere", SOUND = "fx_sound"
    var id: String { rawValue }
    var title: String {
        switch self {
        case .ACTION: return "Aksiyon"
        case .MAGIC: return "Büyü ve enerji"
        case .EMOTION: return "Duygu"
        case .ATMOSPHERE: return "Atmosfer"
        case .SOUND: return "Ses"
        }
    }
}

/// v36 efekt (Android `EffectDef`): çizimi uygulamayla gelen katalogda (`lib.fx.<anahtar>`); burada tasarım boyu,
/// varsayılan renk ve çizgi efekti olup olmadığı.
struct EffectDef: Identifiable, Equatable {
    let key: String
    let title: String
    let group: EffectGroup
    let w: Float
    let h: Float
    let color: String
    let lineOnly: Bool
    let tags: [String]
    var id: String { ComicEffects.ID_PREFIX + key }
}

/// v36 çizgi roman efektleri (Android `ComicEffects`).
enum ComicEffects {
    static let ID_PREFIX = "fx."

    static let all: [EffectDef] = [
        EffectDef(key: "speed", title: "Hız çizgileri", group: .ACTION, w: 200, h: 100, color: "#1D1D24", lineOnly: true, tags: ["hız", "koşu", "hareket", "speed lines"]),
        EffectDef(key: "impact", title: "Darbe", group: .ACTION, w: 120, h: 120, color: "#FFD23F", lineOnly: false, tags: ["darbe", "vuruş", "çarpma", "impact"]),
        EffectDef(key: "collision", title: "Çarpışma", group: .ACTION, w: 160, h: 120, color: "#FF7A2F", lineOnly: false, tags: ["çarpışma", "kaza", "çarpma", "collision"]),
        EffectDef(key: "explosion", title: "Patlama", group: .ACTION, w: 140, h: 130, color: "#FF6A1F", lineOnly: false, tags: ["patlama", "bomba", "ateş", "explosion"]),
        EffectDef(key: "shatter", title: "Parçalanma", group: .ACTION, w: 140, h: 120, color: "#9FD3EE", lineOnly: false, tags: ["parçalanma", "kırılma", "cam", "shatter"]),
        EffectDef(key: "dust", title: "Toz bulutu", group: .ACTION, w: 160, h: 90, color: "#D8C6A4", lineOnly: false, tags: ["toz", "bulut", "duman", "fren", "dust"]),
        EffectDef(key: "punch_trail", title: "Yumruk hareket izi", group: .ACTION, w: 180, h: 110, color: "#1D1D24", lineOnly: true, tags: ["yumruk", "hareket izi", "vuruş", "punch"]),
        EffectDef(key: "lightning", title: "Şimşek", group: .MAGIC, w: 80, h: 160, color: "#FFE14D", lineOnly: false, tags: ["şimşek", "yıldırım", "elektrik", "lightning"]),
        EffectDef(key: "flame", title: "Alev", group: .MAGIC, w: 100, h: 140, color: "#FF7A1A", lineOnly: false, tags: ["alev", "ateş", "yangın", "flame"]),
        EffectDef(key: "ice", title: "Buz", group: .MAGIC, w: 140, h: 120, color: "#7FD3F5", lineOnly: false, tags: ["buz", "kristal", "soğuk", "donma", "ice"]),
        EffectDef(key: "aura", title: "Enerji aurası", group: .MAGIC, w: 120, h: 160, color: "#53E0FF", lineOnly: false, tags: ["aura", "enerji", "güç", "ki", "energy aura"]),
        EffectDef(key: "magic_circle", title: "Büyü çemberi", group: .MAGIC, w: 140, h: 140, color: "#A86BFF", lineOnly: true, tags: ["büyü çemberi", "sihir", "rün", "magic circle"]),
        EffectDef(key: "light_beam", title: "Işık huzmesi", group: .MAGIC, w: 120, h: 160, color: "#FFF2A8", lineOnly: false, tags: ["ışık", "huzme", "spot", "ilahi", "light beam"]),
        EffectDef(key: "portal_glow", title: "Portal parıltısı", group: .MAGIC, w: 140, h: 140, color: "#9B5CFF", lineOnly: false, tags: ["portal", "geçit", "parıltı", "boyut", "portal glow"]),
        EffectDef(key: "hearts", title: "Kalpler", group: .EMOTION, w: 140, h: 110, color: "#FF4F81", lineOnly: false, tags: ["kalp", "aşk", "sevgi", "hearts"]),
        EffectDef(key: "tears", title: "Gözyaşları", group: .EMOTION, w: 100, h: 120, color: "#5EB7FF", lineOnly: false, tags: ["gözyaşı", "ağlama", "üzüntü", "tears"]),
        EffectDef(key: "sweat", title: "Ter damlaları", group: .EMOTION, w: 100, h: 120, color: "#8FD3FF", lineOnly: false, tags: ["ter", "endişe", "utanma", "sweat"]),
        EffectDef(key: "anger", title: "Öfke işareti", group: .EMOTION, w: 100, h: 100, color: "#FF2E2E", lineOnly: false, tags: ["öfke", "sinir", "kızgınlık", "damar", "anger"]),
        EffectDef(key: "surprise", title: "Şaşkınlık çizgileri", group: .EMOTION, w: 140, h: 100, color: "#1D1D24", lineOnly: true, tags: ["şaşkınlık", "sürpriz", "şok", "surprise"]),
        EffectDef(key: "tremble", title: "Titreme çizgileri", group: .EMOTION, w: 140, h: 100, color: "#1D1D24", lineOnly: true, tags: ["titreme", "korku", "üşüme", "tremble"]),
        EffectDef(key: "rain", title: "Yağmur", group: .ATMOSPHERE, w: 160, h: 160, color: "#4F8FD8", lineOnly: true, tags: ["yağmur", "fırtına", "hava", "rain"]),
        EffectDef(key: "snow", title: "Kar", group: .ATMOSPHERE, w: 160, h: 160, color: "#EAF6FF", lineOnly: false, tags: ["kar", "kış", "soğuk", "snow"]),
        EffectDef(key: "fog", title: "Sis", group: .ATMOSPHERE, w: 200, h: 100, color: "#C9D3DA", lineOnly: false, tags: ["sis", "pus", "duman", "fog"]),
        EffectDef(key: "smoke", title: "Duman", group: .ATMOSPHERE, w: 110, h: 160, color: "#9EA7AF", lineOnly: false, tags: ["duman", "baca", "buhar", "smoke"]),
        EffectDef(key: "sparks", title: "Kıvılcımlar", group: .ATMOSPHERE, w: 140, h: 140, color: "#FFB627", lineOnly: false, tags: ["kıvılcım", "ateş", "parıltı", "sparks"]),
        EffectDef(key: "glow", title: "Parlama", group: .ATMOSPHERE, w: 140, h: 140, color: "#FFE680", lineOnly: false, tags: ["parlama", "ışık", "parıltı", "glow"]),
        EffectDef(key: "dots", title: "Noktacık dokusu", group: .ATMOSPHERE, w: 160, h: 160, color: "#1D1D24", lineOnly: false, tags: ["noktacık", "tram", "doku", "halftone", "dots"]),
    ]

    static func byId(_ id: String) -> EffectDef? {
        let raw = id.hasPrefix("lib.") ? String(id.dropFirst(4)) : id
        return all.first { $0.id == raw }
    }

    static func of(_ group: EffectGroup) -> [EffectDef] { all.filter { $0.group == group } }
}
