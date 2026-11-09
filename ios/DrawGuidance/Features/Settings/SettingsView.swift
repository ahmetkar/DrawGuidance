import AuthenticationServices
import SwiftUI
import UserMessagingPlatform

/// Ayarlar (Android `SettingsScreen` temel kısmı): hesap (giriş şimdilik kapalı), abonelik, çizim önerileri,
/// otomatik kayıt, reklam gizlilik seçenekleri ve sürüm.
struct SettingsView: View {
    @ObservedObject var prefs: EditorPrefsStore
    @ObservedObject var subscription: SubscriptionStore = .shared

    /// Giriş (Google / Apple) eklendi ama Ahmet'in isteğiyle şimdilik kapalı.
    static let signInEnabled = false

    var body: some View {
        Form {
            Section(NSLocalizedString("ios.signin.title", comment: "")) {
                SignInWithAppleButton(.signIn) { _ in } onCompletion: { _ in }
                    .frame(height: 44)
                    .disabled(!Self.signInEnabled)
                    .opacity(Self.signInEnabled ? 1 : 0.4)
                Button { } label: { Label("Google", systemImage: "g.circle") }
                    .disabled(!Self.signInEnabled)
                if !Self.signInEnabled { Text(NSLocalizedString("ios.signin.soon", comment: "")).font(.footnote).foregroundStyle(.secondary) }
            }
            Section(L("v39_sub_title")) {
                NavigationLink { SubscriptionView() } label: {
                    HStack { Text(subscription.isPremium ? L("v39_sub_current_premium") : L("v39_sub_current_free")); Spacer() }
                }
                .accessibilityIdentifier("settings_subscription")
            }
            Section(L("settings_tab_drawing")) {
                Toggle("Handwriting", isOn: Binding(get: { prefs.handwriting }, set: { prefs.setHandwriting($0) }))
                Toggle("Text", isOn: Binding(get: { prefs.textSuggest }, set: { prefs.setTextSuggest($0) }))
                Toggle("Shapes", isOn: Binding(get: { prefs.shapeSuggest }, set: { prefs.setShapeSuggest($0) }))
                Toggle("Lines", isOn: Binding(get: { prefs.lineSuggest }, set: { prefs.setLineSuggest($0) }))
                Toggle("Selection", isOn: Binding(get: { prefs.selectionSuggest }, set: { prefs.setSelectionSuggest($0) }))
            }
            Section(L("settings_tab_saving")) {
                Toggle("Autosave", isOn: Binding(get: { prefs.autosave }, set: { prefs.setAutosave($0) }))
            }
            if UMPConsentInformation.sharedInstance.privacyOptionsRequirementStatus == .required {
                Section {
                    Button("Privacy options") {
                        guard let vc = UIApplication.shared.connectedScenes.compactMap({ ($0 as? UIWindowScene)?.keyWindow?.rootViewController }).first
                        else { return }
                        UMPConsentForm.presentPrivacyOptionsForm(from: vc) { _ in }
                    }
                }
            }
            Section {
                LabeledContent("Version", value: "\(Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "") (\(Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? ""))")
            }
        }
        .navigationTitle(NSLocalizedString("ios.settings.title", comment: ""))
    }
}
