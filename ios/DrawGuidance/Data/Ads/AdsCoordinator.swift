import Foundation
import GoogleMobileAds
import UIKit
import UserMessagingPlatform

/// v39 geçiş reklamları (Android `AdsCoordinator` + `AdMobGateway`): açılışta UMP onayı alınır, kütüphane başlatılır ve bir
/// reklam önceden yüklenir; tuval kapatılınca [AdsPolicy]'ye göre hazır reklam varsa gösterilir.
/// Şimdilik Google'ın test reklam kimlikleri kullanılır (Info.plist `DGInterstitialUnitId` ile değiştirilir).
@MainActor
final class AdsCoordinator: NSObject, GADFullScreenContentDelegate {
    static let shared = AdsCoordinator()
    static let TEST_INTERSTITIAL = "ca-app-pub-3940256099942544/4411468910"

    private let policy = AdsPolicy()
    private let defaults = UserDefaults.standard
    private var interstitial: GADInterstitialAd?
    private var started = false
    private var loading = false
    private(set) var lastDecision: AdDecision?

    private var unitId: String {
        (Bundle.main.object(forInfoDictionaryKey: "DGInterstitialUnitId") as? String).flatMap { $0.isEmpty ? nil : $0 } ?? Self.TEST_INTERSTITIAL
    }
    private var consent: Bool { UMPConsentInformation.sharedInstance.canRequestAds }
    private var lastShownAt: Int64? { let v = defaults.object(forKey: "ads.last") as? Int64; return v.flatMap { $0 > 0 ? $0 : nil } }
    private static func now() -> Int64 { Int64(Date().timeIntervalSince1970 * 1000) }
    /// UI testlerinde reklam kapalı.
    private var disabled: Bool { ProcessInfo.processInfo.arguments.contains("-uiTesting") }

    /// Uygulamaya giriş (soğuk başlangıç): onay, başlatma, ön yükleme; ilk açılış atlanır.
    func onAppEntry() {
        if disabled { return }
        let first = !defaults.bool(forKey: "ads.launched")
        defaults.set(true, forKey: "ads.launched")
        requestConsent { [weak self] in
            guard let self else { return }
            self.start()
            Task { @MainActor in
                let premium = await self.premiumState()
                self.show(.APP_ENTRY, premium: premium, kind: nil, firstLaunch: first)
            }
        }
    }

    /// Tuval kapatıldı.
    func onCanvasClosed(_ kind: CanvasKind?) {
        if disabled { return }
        Task { @MainActor in
            let premium = await premiumState()
            show(.CANVAS_CLOSED, premium: premium, kind: kind, firstLaunch: false)
        }
    }

    private func premiumState() async -> Bool? {
        let s = SubscriptionStore.shared
        await s.refresh()
        return !s.showsAds
    }

    private func requestConsent(_ done: @escaping () -> Void) {
        let params = UMPRequestParameters()
        params.tagForUnderAgeOfConsent = false
        UMPConsentInformation.sharedInstance.requestConsentInfoUpdate(with: params) { _ in
            Task { @MainActor in
                if let vc = Self.topController() {
                    UMPConsentForm.loadAndPresentIfRequired(from: vc) { _ in Task { @MainActor in done() } }
                } else { done() }
            }
        }
    }

    private func start() {
        guard !started, consent else { return }
        started = true
        GADMobileAds.sharedInstance().start(completionHandler: nil)
        preload()
    }

    private func preload() {
        guard started, interstitial == nil, !loading else { return }
        loading = true
        GADInterstitialAd.load(withAdUnitID: unitId, request: GADRequest()) { [weak self] ad, _ in
            Task { @MainActor in
                guard let self else { return }
                self.loading = false
                self.interstitial = ad
                ad?.fullScreenContentDelegate = self
            }
        }
    }

    private func show(_ trigger: AdTrigger, premium: Bool?, kind: CanvasKind?, firstLaunch: Bool) {
        let d = policy.decide(trigger, premium: premium, child: false, canvasKind: kind, firstLaunch: firstLaunch, consent: consent,
                              lastShownAt: lastShownAt, now: Self.now(), available: started)
        lastDecision = d
        guard d == .SHOW else { if d != .PREMIUM { preload() }; return }
        guard let ad = interstitial, let vc = Self.topController() else { preload(); return }
        interstitial = nil
        defaults.set(Self.now(), forKey: "ads.last")
        ad.present(fromRootViewController: vc)
    }

    nonisolated func adDidDismissFullScreenContent(_ ad: GADFullScreenPresentingAd) { Task { @MainActor in self.preload() } }
    nonisolated func ad(_ ad: GADFullScreenPresentingAd, didFailToPresentFullScreenContentWithError error: Error) {
        Task { @MainActor in self.preload() }
    }

    private static func topController() -> UIViewController? {
        let scene = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first { $0.activationState == .foregroundActive }
            ?? UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first
        var vc = scene?.windows.first { $0.isKeyWindow }?.rootViewController ?? scene?.windows.first?.rootViewController
        while let p = vc?.presentedViewController { vc = p }
        return vc
    }
}
