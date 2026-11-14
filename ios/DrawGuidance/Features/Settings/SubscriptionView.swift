import StoreKit
import SwiftUI

/// Abonelik sayfası (Android `SubscriptionScreen`): mevcut plan, Premium'un hakları, satın alma, geri yükleme ve yönetme.
/// Android'de (Google Play) alınmış abonelik aynı hesapla burada da görünür; yönetimi Google Play'dedir.
struct SubscriptionView: View {
    @ObservedObject var store: SubscriptionStore = .shared
    @State private var manageOpen = false
    @Environment(\.openURL) private var openURL

    var body: some View {
        List {
            Section(L("v39_sub_current")) {
                Text(store.isPremium ? L("v39_sub_current_premium") : L("v39_sub_current_free")).font(.headline)
                    .accessibilityIdentifier("sub_current")
                if store.isPremium {
                    if let e = store.expiresAt { Text(L("v39_sub_expiry", e.formatted(date: .abbreviated, time: .omitted))) }
                    Text(store.willRenew ? L("v39_sub_renew_on") : L("v39_sub_renew_off")).foregroundStyle(.secondary)
                    if store.managedOnGooglePlay {
                        Text(NSLocalizedString("ios.sub.from_play", comment: "")).foregroundStyle(.secondary)
                    }
                }
            }
            Section("Premium") {
                ForEach(["v41_sub_f_everything_free", "v41_sub_f_no_ads", "v41_sub_f_bg_removal", "v41_sub_f_comic",
                         "v41_sub_f_shapes", "v41_sub_f_ai", "v41_sub_f_kids"], id: \.self) { key in
                    Label(L(key), systemImage: "checkmark.seal")
                }
            }
            Section {
                if !store.isPremium {
                    Button {
                        Task { await store.purchase() }
                    } label: {
                        HStack {
                            Text(L("v39_sub_upgrade")).bold()
                            Spacer()
                            if store.busy { ProgressView() } else if let p = store.product { Text(L("v39_sub_per_month", p.displayPrice)) }
                        }
                    }
                    .disabled(store.busy)
                    .accessibilityIdentifier("sub_upgrade")
                } else if store.managedOnGooglePlay {
                    Button(NSLocalizedString("ios.sub.manage_play", comment: "")) {
                        openURL(URL(string: "https://play.google.com/store/account/subscriptions")!)
                    }
                    .accessibilityIdentifier("sub_manage_play")
                } else {
                    Button(NSLocalizedString("ios.sub.manage", comment: "")) { manageOpen = true }
                        .accessibilityIdentifier("sub_manage")
                }
                Button(NSLocalizedString("ios.sub.restore", comment: "")) { Task { await store.restore() } }
                    .accessibilityIdentifier("sub_restore")
            }
            if let text = messageText {
                Section { Text(text).foregroundStyle(.secondary) }
            }
            Section {
                Text(NSLocalizedString("ios.sub.cross_platform", comment: "")).font(.footnote).foregroundStyle(.secondary)
                Text(NSLocalizedString("ios.sub.legal", comment: "")).font(.footnote).foregroundStyle(.secondary)
                // App Store 3.1.2: abonelik sayfasında kullanım koşulları (EULA) ve gizlilik politikası bağlantıları
                Link(NSLocalizedString("ios.sub.terms", comment: ""),
                     destination: URL(string: "https://www.apple.com/legal/internet-services/itunes/dev/stdeula/")!)
                    .font(.footnote)
                Link(NSLocalizedString("ios.sub.privacy", comment: ""), destination: Self.privacyUrl).font(.footnote)
            }
        }
        .navigationTitle(L("v39_sub_title"))
        .manageSubscriptionsSheet(isPresented: $manageOpen)
        .onChange(of: manageOpen) { _, open in
            // Yönetim sayfası kapanınca (iptal, plan değişikliği) hak yeniden okunur
            if !open { Task { await store.refresh(); await store.refreshServer() } }
        }
        .task {
            if store.product == nil { await store.loadProduct() }
            await store.refreshServer()
        }
    }

    /// Web sitesindeki gizlilik politikası (site yalnız Türkçe ve İngilizce).
    static var privacyUrl: URL {
        let lang = Locale.preferredLanguages.first.map { String($0.prefix(2)) } ?? "en"
        let l = lang == "tr" ? "tr" : "en"
        return URL(string: "https://drawguidance.ahmetkar.com/\(l)/privacy")!
    }

    private var messageText: String? {
        switch store.message {
        case .none: return nil
        case .storeUnavailable: return NSLocalizedString("ios.sub.store_unavailable", comment: "")
        case .productUnavailable: return L("v39_sub_msg_product")
        case .canceled: return L("v39_sub_msg_canceled")
        case .pending: return L("v39_sub_msg_pending")
        case .verified: return L("v39_sub_msg_verified")
        case .failed: return L("v39_sub_msg_verify_failed")
        case .restored: return L("v39_sub_msg_restored")
        case .nothingToRestore: return L("v39_sub_msg_nothing")
        case .linked: return NSLocalizedString("ios.sub.linked", comment: "")
        case .serverUnavailable: return L("v39_sub_msg_server")
        case .otherAccount: return NSLocalizedString("ios.sub.other_account", comment: "")
        case .offline: return L("v39_sub_msg_offline")
        }
    }
}

/// Premium gerektiren özellikte gösterilen pencere (Android `PremiumRequiredSheet`).
struct PremiumRequiredSheet: View {
    @Environment(\.dismiss) private var dismiss
    @State private var showPlans = false

    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                Image(systemName: "crown.fill").font(.system(size: 44)).foregroundStyle(.yellow)
                Text(L("v39_premium_required")).font(.title2.bold())
                Text(L("v39_premium_required_body")).multilineTextAlignment(.center).foregroundStyle(.secondary)
                NavigationLink(L("v39_premium_see_plans")) { SubscriptionView() }
                    .buttonStyle(.borderedProminent)
                    .accessibilityIdentifier("premium_go_subscription")
                Button(L("v39_premium_not_now")) { dismiss() }
            }
            .padding(24)
        }
        .presentationDetents([.medium])
    }
}
