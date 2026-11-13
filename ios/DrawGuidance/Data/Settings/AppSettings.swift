import Foundation
import SwiftUI

/// v40 tema seçimi (Android `ThemeMode`): sistem, açık, koyu.
enum ThemeMode: Int, CaseIterable {
    case SYSTEM = 0, LIGHT, DARK
    static let KEY = "theme_mode"
    var labelKey: String { ["v40_theme_mode_system", "v40_theme_mode_light", "v40_theme_mode_dark"][rawValue] }
    var scheme: ColorScheme? { self == .LIGHT ? .light : self == .DARK ? .dark : nil }
}

/// Kalem (Apple Pencil) tercihleri (Android `StylusSettings`): basınç duyarlılığı ve kalemle çizerken parmağın kaydırması.
enum StylusPrefs {
    static let PRESSURE = "stylus_pressure", FINGER_PANS = "stylus_finger_pans"
    private static func b(_ k: String) -> Bool { UserDefaults.standard.object(forKey: k) as? Bool ?? true }
    static var pressure: Bool { get { b(PRESSURE) } set { UserDefaults.standard.set(newValue, forKey: PRESSURE) } }
    static var fingerPans: Bool { get { b(FINGER_PANS) } set { UserDefaults.standard.set(newValue, forKey: FINGER_PANS) } }
}

/// v37 Ayarlar → Depolama (Android `StorageManager`): bölüm bölüm kullanılan yer ve önbelleği temizleme. Tuvaller ve
/// dışa aktarılanlar buradan silinmez.
enum StorageArea: CaseIterable { case DRAWINGS, EXPORTS, CACHE
    var labelKey: String { self == .DRAWINGS ? "storage_drawings" : self == .EXPORTS ? "storage_exports" : "storage_cache" }
}

enum StorageUsage {
    private static var fm: FileManager { .default }
    private static func dirs(_ a: StorageArea) -> [URL] {
        switch a {
        case .DRAWINGS: return [fm.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]]
        case .EXPORTS: return [fm.urls(for: .documentDirectory, in: .userDomainMask)[0]]
        case .CACHE: return [fm.urls(for: .cachesDirectory, in: .userDomainMask)[0], fm.temporaryDirectory]
        }
    }

    /// Bölümlerin boyutu (bayt; dosya sistemini dolaşır, ana iş parçacığında çağrılmamalı).
    static func measure() -> [StorageArea: Int64] {
        Dictionary(uniqueKeysWithValues: StorageArea.allCases.map { a in (a, dirs(a).reduce(Int64(0)) { $0 + size($1) }) })
    }

    /// Bütün önbellek silinir; boşalan yer (bayt).
    static func clearCache() -> Int64 {
        let before = dirs(.CACHE).reduce(Int64(0)) { $0 + size($1) }
        for d in dirs(.CACHE) {
            for f in (try? fm.contentsOfDirectory(at: d, includingPropertiesForKeys: nil)) ?? [] { try? fm.removeItem(at: f) }
        }
        return max(before - dirs(.CACHE).reduce(Int64(0)) { $0 + size($1) }, 0)
    }

    static func size(_ url: URL) -> Int64 {
        guard let e = fm.enumerator(at: url, includingPropertiesForKeys: [.totalFileAllocatedSizeKey, .isRegularFileKey]) else { return 0 }
        var total: Int64 = 0
        for case let f as URL in e {
            guard let v = try? f.resourceValues(forKeys: [.totalFileAllocatedSizeKey, .isRegularFileKey]), v.isRegularFile == true else { continue }
            total += Int64(v.totalFileAllocatedSize ?? 0)
        }
        return total
    }

    static func format(_ bytes: Int64) -> String { ByteCountFormatter.string(fromByteCount: bytes, countStyle: .file) }
}
