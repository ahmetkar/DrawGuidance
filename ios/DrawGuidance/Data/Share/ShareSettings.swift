import Foundation
import UIKit

/// v51 paylaşım ayarları (Android `ShareSettings`): diğer cihazlarda görünen ad, görünürlük (uygulama açıkken
/// kendiliğinden keşif), gelenleri sormadan alma ve yayın akıcılığı.
@MainActor
final class ShareSettings: ObservableObject {
    static let shared = ShareSettings()

    private let defaults: UserDefaults
    let instanceId: String
    @Published private(set) var deviceName: String
    @Published private(set) var askBeforeReceive: Bool
    @Published private(set) var discoverable: Bool
    @Published private(set) var liveSmooth: Bool
    /// v52 canlı yayın daveti için onay (tuval/resim alma ayarından ayrı).
    @Published private(set) var askBeforeLive: Bool
    /// v52 katılınan yayın kendiliğinden açılsın.
    @Published private(set) var liveAutoOpen: Bool

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        if let id = defaults.string(forKey: Self.KEY_ID), !id.isEmpty { instanceId = id } else {
            instanceId = UUID().uuidString.lowercased()
            defaults.set(instanceId, forKey: Self.KEY_ID)
        }
        let n = defaults.string(forKey: Self.KEY_NAME)?.trimmingCharacters(in: .whitespaces) ?? ""
        deviceName = n.isEmpty ? Self.defaultName() : n
        askBeforeReceive = defaults.object(forKey: Self.KEY_ASK) as? Bool ?? true
        discoverable = defaults.object(forKey: Self.KEY_VISIBLE) as? Bool ?? true
        liveSmooth = defaults.object(forKey: Self.KEY_SMOOTH) as? Bool ?? true
        askBeforeLive = defaults.object(forKey: Self.KEY_ASK_LIVE) as? Bool ?? true
        liveAutoOpen = defaults.object(forKey: Self.KEY_LIVE_OPEN) as? Bool ?? true
    }

    func setDeviceName(_ name: String) {
        let n = String(name.trimmingCharacters(in: .whitespacesAndNewlines).prefix(40))
        deviceName = n.isEmpty ? Self.defaultName() : n
        defaults.set(n, forKey: Self.KEY_NAME)
    }
    func setAskBeforeReceive(_ on: Bool) { askBeforeReceive = on; defaults.set(on, forKey: Self.KEY_ASK) }
    func setDiscoverable(_ on: Bool) { discoverable = on; defaults.set(on, forKey: Self.KEY_VISIBLE) }
    func setLiveSmooth(_ on: Bool) { liveSmooth = on; defaults.set(on, forKey: Self.KEY_SMOOTH) }
    func setAskBeforeLive(_ on: Bool) { askBeforeLive = on; defaults.set(on, forKey: Self.KEY_ASK_LIVE) }
    func setLiveAutoOpen(_ on: Bool) { liveAutoOpen = on; defaults.set(on, forKey: Self.KEY_LIVE_OPEN) }

    /// Yayın karelerinin aralığı (saniye).
    var liveInterval: TimeInterval { liveSmooth ? 0.25 : 0.9 }

    static let KEY_ID = "share.instance_id"
    static let KEY_NAME = "share.device_name"
    static let KEY_ASK = "share.ask_before_receive"
    static let KEY_VISIBLE = "share.discoverable"
    static let KEY_SMOOTH = "share.live_smooth"
    static let KEY_ASK_LIVE = "share.ask_before_live"
    static let KEY_LIVE_OPEN = "share.live_auto_open"

    /// iOS 16'dan beri cihaz adı izinsiz okunamaz ("iPhone"); model adı kullanılır.
    static func defaultName() -> String {
        let model = UIDevice.current.model
        return model.isEmpty ? "iPhone" : model
    }
}

/// Alınan bir tuvalin kaynağı.
struct SharedCanvasInfo: Codable, Equatable {
    var from: String
    var platform: String = ""
    var at: Int64 = 0
    var live = false

    init(from: String, platform: String, at: Int64, live: Bool = false) {
        self.from = from; self.platform = platform; self.at = at; self.live = live
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        from = try c.decodeIfPresent(String.self, forKey: .from) ?? ""
        platform = try c.decodeIfPresent(String.self, forKey: .platform) ?? ""
        at = try c.decodeIfPresent(Int64.self, forKey: .at) ?? 0
        live = try c.decodeIfPresent(Bool.self, forKey: .live) ?? false
    }
}

/// v51 alınan tuvallerin kaydı (tuval kimliği → kaynak); tuval kartında "Paylaşılan" işareti bundan gelir.
/// Tuval kaydına dokunulmaz.
@MainActor
final class SharedCanvasRegistry: ObservableObject {
    static let shared = SharedCanvasRegistry()

    private let defaults: UserDefaults
    @Published private(set) var all: [String: SharedCanvasInfo] = [:]
    private static let KEY = "share.registry"

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        if let d = defaults.data(forKey: Self.KEY), let m = try? JSONDecoder().decode([String: SharedCanvasInfo].self, from: d) { all = m }
    }

    func get(_ canvasId: String) -> SharedCanvasInfo? { all[canvasId] }

    func mark(_ canvasId: String, _ info: SharedCanvasInfo) { all[canvasId] = info; persist() }

    func forget(_ canvasId: String) { all[canvasId] = nil; persist() }

    private func persist() {
        if let d = try? JSONEncoder().encode(all) { defaults.set(d, forKey: Self.KEY) }
    }
}
