import Foundation
import os
#if canImport(TalsecRuntime)
import TalsecRuntime
#endif

/// Android v49 TalsecGuard karşılığı: freeRASP (Talsec, ücretsiz sürüm) çalışma zamanı koruması.
///
/// iOS'ta R8 gibi bir karartma aracı yok; yayın derlemesinde semboller silinir (project.yml) ve App Store
/// ikili dosyayı FairPlay ile şifreler. freeRASP bunlara jailbreak, hata ayıklayıcı, hook (Frida vb.),
/// imza/kurcalama, simülatör ve resmi olmayan mağaza denetimlerini ekler.
///
/// Politika (Android ile aynı): imza bozulması ve çalışma zamanı kurcalaması (hook) algılanınca uygulama
/// kapatılır; ötekiler yalnız günlüğe yazılır. Kapatma yalnız Info.plist'te `DGRaspEnforce = YES` ve takım
/// kimliği doluyken çalışır. Device Farm .ipa'yı kendi sertifikasıyla yeniden imzaladığından test
/// derlemelerinde kapatma kapalıdır (aksi halde uygulama açılır açılmaz kapanırdı).
enum RaspGuard {
    static let bundleId = "com.karapp.drawguidance"
    static let watcherMail = "ahmetkar.1258@gmail.com"
    private static let log = Logger(subsystem: bundleId, category: "Talsec")
    nonisolated(unsafe) private static var started = false

    static var teamId: String {
        (Bundle.main.object(forInfoDictionaryKey: "DGTeamId") as? String)?
            .trimmingCharacters(in: .whitespaces) ?? ""
    }

    static var enforce: Bool {
        let v = Bundle.main.object(forInfoDictionaryKey: "DGRaspEnforce") as? String ?? "NO"
        return v.uppercased() == "YES" && !teamId.isEmpty
    }

    static func start() {
        if started { return }
        started = true
        // Birim/arayüz testlerinde çalıştırma
        if ProcessInfo.processInfo.environment["XCTestConfigurationFilePath"] != nil { return }
        if ProcessInfo.processInfo.arguments.contains("-uitest") { return }
        #if canImport(TalsecRuntime)
        #if DEBUG
        let prod = false
        #else
        let prod = true
        #endif
        let config = TalsecConfig(appBundleIds: [bundleId], appTeamId: teamId.isEmpty ? "UNSIGNED" : teamId,
                                  watcherMailAddress: watcherMail, isProd: prod)
        Talsec.start(config: config)
        #endif
    }

    /// Algılanan tehdit (adıyla). İmza ve kurcalama ölümcül, ötekiler günlüğe.
    static func handle(_ name: String) {
        switch name {
        case "signature", "runtimeManipulation":
            log.error("Güvenlik tehdidi: \(name, privacy: .public)")
            #if !DEBUG
            if enforce { exit(0) }
            #endif
        default:
            log.warning("\(name, privacy: .public)")
        }
    }
}

#if canImport(TalsecRuntime)
extension SecurityThreatCenter: @retroactive SecurityThreatHandler {
    public func threatDetected(_ securityThreat: SecurityThreat) {
        RaspGuard.handle(securityThreat.rawValue)
    }
}
#endif
