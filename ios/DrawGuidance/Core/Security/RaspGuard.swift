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
        let args = ProcessInfo.processInfo.arguments
        if args.contains("-uitest") || args.contains("-uiTesting") { return }
        // Simülatörde çalıştırılmaz: kanca taraması (isAppHooked) simülatörde dakikalarca sürüyor
        #if targetEnvironment(simulator)
        return
        #endif
        #if canImport(TalsecRuntime)
        #if DEBUG
        let prod = false
        #else
        let prod = true
        #endif
        let config = TalsecConfig(appBundleIds: [bundleId], appTeamId: teamId.isEmpty ? "UNSIGNED" : teamId,
                                  watcherMailAddress: watcherMail, isProd: prod)
        // İlk denetimler eşzamanlı (bellek bölgelerini tarar): açılışı ve ana iş parçacığını bekletmemesi için arka planda
        DispatchQueue.global(qos: .utility).async { Talsec.start(config: config) }
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
