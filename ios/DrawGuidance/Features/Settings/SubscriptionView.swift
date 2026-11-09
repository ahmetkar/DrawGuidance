import StoreKit
import SwiftUI

/// Abonelik sayfası (Android `SubscriptionScreen`): mevcut plan, Premium'un hakları, satın alma, geri yükleme ve yönetme.
struct SubscriptionView: View {
    @ObservedObject var store: SubscriptionStore = .shared
    @State private var manageOpen = false

    var body: some View {
        List {
            Section(L("v39_sub_current")) {
                Text(store.isPremium ? L("v39_sub_current_premium") : L("v39_sub_current_free")).font(.headline)
                    .accessibilityIdentifier("sub_current")
                if store.isPremium {
                    if let e = store.expiresAt { Text(L("v39_sub_expiry", e.formatted(date: .abbreviated, time: .omitted))) }
                    Text(store.willRenew ? L("v39_sub_renew_on") : L("v39_sub_renew_off")).foregroundStyle(.secondary)
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
        }
        .navigationTitle(L("v39_sub_title"))
        .manageSubscriptionsSheet(isPresented: $manageOpen)
        .task { if store.product == nil { await store.loadProduct() } }
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
