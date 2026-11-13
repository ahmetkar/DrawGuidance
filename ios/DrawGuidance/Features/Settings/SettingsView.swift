import SwiftUI
import UserMessagingPlatform

/// Ayarlar (Android `SettingsScreen`): Genel (hesap, abonelik, dil, tema), Çizim (öneriler, kalem, eğitimler),
/// Kaydetme (otomatik kayıt, resim kaydetme, depolama), Gelişmiş (reklam gizliliği, sürüm). Hesap: Apple ve Google
/// ile giriş (`AccountSection`); eşitleme ve geri bildirim şimdilik yok.
struct SettingsView: View {
    @ObservedObject var prefs: EditorPrefsStore
    @ObservedObject var subscription: SubscriptionStore = .shared

    enum Tab: Int, CaseIterable {
        case GENERAL, DRAWING, SAVING, ADVANCED
        var labelKey: String { ["settings_tab_general", "settings_tab_drawing", "settings_tab_saving", "settings_tab_advanced"][rawValue] }
    }

    @State private var tab = Tab.GENERAL
    @AppStorage(ThemeMode.KEY) private var theme = ThemeMode.SYSTEM.rawValue
    @State private var export = ExportSettings.load()
    @State private var pressure = StylusPrefs.pressure
    @State private var fingerPans = StylusPrefs.fingerPans
    @State private var usage: [StorageArea: Int64]?
    @State private var message: String?

    var body: some View {
        Form {
            Section {
                Picker("", selection: $tab) {
                    ForEach(Tab.allCases, id: \.self) { Text(L($0.labelKey)).tag($0) }
                }
                .pickerStyle(.segmented)
                .accessibilityIdentifier("settings_tabs")
            }
            switch tab {
            case .GENERAL: general
            case .DRAWING: drawing
            case .SAVING: saving
            case .ADVANCED: advanced
            }
        }
        .navigationTitle(L("drawer_settings"))
        .overlay(alignment: .bottom) {
            if let message {
                Text(message).font(.subheadline).foregroundStyle(.white).padding(.horizontal, 16).padding(.vertical, 10)
                    .background(Capsule().fill(Color.black.opacity(0.78))).padding(.bottom, 24)
                    .onTapGesture { self.message = nil }
            }
        }
    }

    // MARK: Genel

    @ViewBuilder private var general: some View {
        AccountSection()
        Section(L("v39_sub_title")) {
            NavigationLink { SubscriptionView() } label: {
                HStack { Text(subscription.isPremium ? L("v39_sub_current_premium") : L("v39_sub_current_free")); Spacer() }
            }
            .accessibilityIdentifier("settings_subscription")
        }
        Section {
            // iOS'ta uygulamanın dili sistem Ayarlar'ından seçilir (uygulama başına dil).
            Button {
                if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
            } label: {
                LabeledContent(L("language_title"), value: Self.languageName(I18n.language))
            }
            .accessibilityIdentifier("settings_language")
        } header: { Text(L("language_title")) } footer: {
            Text(L("language_auto_hint", Locale.current.localizedString(forRegionCode: Locale.current.region?.identifier ?? "") ?? ""))
        }
        Section(L("v40_theme_mode_title")) {
            Picker(L("v40_theme_mode_title"), selection: $theme) {
                ForEach(ThemeMode.allCases, id: \.rawValue) { Text(L($0.labelKey)).tag($0.rawValue) }
            }
            .pickerStyle(.segmented)
            .accessibilityIdentifier("settings_theme")
        }
    }

    static func languageName(_ code: String) -> String {
        Locale(identifier: code).localizedString(forLanguageCode: code)?.capitalized(with: Locale(identifier: code)) ?? code
    }

    // MARK: Çizim

    @ViewBuilder private var drawing: some View {
        Section(L("suggestions_setting")) {
            toggle("suggestions_setting", prefs.handwriting, prefs.setHandwriting, id: "settings_suggest")
            toggle("text_suggest_setting", prefs.textSuggest, prefs.setTextSuggest, id: "settings_text_suggest")
            toggle("shape_suggest_setting", prefs.shapeSuggest, prefs.setShapeSuggest, id: "settings_shape_suggest")
            toggle("line_suggest_setting", prefs.lineSuggest, prefs.setLineSuggest, id: "settings_line_suggest")
            toggle("selection_suggest_setting", prefs.selectionSuggest, prefs.setSelectionSuggest, id: "settings_selection_suggest")
        }
        Section(L("stylus_title")) {
            toggle("stylus_pressure", pressure, { pressure = $0; StylusPrefs.pressure = $0 }, id: "settings_stylus_pressure")
            toggle("stylus_finger_pans", fingerPans, { fingerPans = $0; StylusPrefs.fingerPans = $0 }, id: "settings_stylus_finger_pans")
        }
        Section {
            Button {
                TutorialPrefs.shared.resetOffers()
                show(L("tut_settings_reset_done"))
            } label: {
                VStack(alignment: .leading, spacing: 2) {
                    Text(L("tut_settings_reset"))
                    Text(L("tut_settings_reset_hint")).font(.footnote).foregroundStyle(.secondary)
                }
            }
            .accessibilityIdentifier("settings_tutorial_reset")
        }
    }

    // MARK: Kaydetme

    @ViewBuilder private var saving: some View {
        Section {
            toggle("autosave", prefs.autosave, prefs.setAutosave, id: "settings_autosave")
        }
        Section {
            Picker(L("export_default_format"), selection: $export.defaultFormat) {
                ForEach(ImageFormat.allCases, id: \.self) { Text($0.label).tag($0) }
            }
            Picker(L("export_default_resolution"), selection: $export.defaultScale) {
                ForEach(ExportSettings.SCALES, id: \.self) { s in Text(s == s.rounded() ? "\(Int(s))×" : "\(s)×").tag(s) }
            }
            Toggle(L("export_use_gallery"), isOn: $export.saveToPhotos)
        } header: { Text(L("export_section_title")) } footer: { Text(L("export_section_description")) }
        .onChange(of: export) { _, s in s.save() }
        Section {
            if let usage {
                ForEach(StorageArea.allCases, id: \.self) { a in
                    LabeledContent(L(a.labelKey), value: StorageUsage.format(usage[a] ?? 0))
                }
                LabeledContent(L("storage_total", StorageUsage.format(usage.values.reduce(0, +))), value: "")
                Button(L("storage_clear_cache")) {
                    Task {
                        let freed = await Task.detached(priority: .utility) { StorageUsage.clearCache() }.value
                        show(L("storage_freed", StorageUsage.format(freed)))
                        await measure()
                    }
                }
                .accessibilityIdentifier("settings_clear_cache")
            } else {
                HStack { ProgressView(); Text(L("storage_measuring")).foregroundStyle(.secondary) }
            }
        } header: { Text(L("storage_title")) } footer: { Text(L("storage_hint")) }
        .task { await measure() }
    }

    private func measure() async {
        usage = await Task.detached(priority: .utility) { StorageUsage.measure() }.value
    }

    // MARK: Gelişmiş

    @ViewBuilder private var advanced: some View {
        if UMPConsentInformation.sharedInstance.privacyOptionsRequirementStatus == .required {
            Section {
                Button(NSLocalizedString("ios.privacy.options", comment: "")) {
                    guard let vc = UIApplication.shared.connectedScenes.compactMap({ ($0 as? UIWindowScene)?.keyWindow?.rootViewController }).first
                    else { return }
                    UMPConsentForm.presentPrivacyOptionsForm(from: vc) { _ in }
                }
            }
        }
        Section {
            LabeledContent(NSLocalizedString("ios.version", comment: ""),
                           value: "\(Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "") (\(Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? ""))")
        }
    }

    // MARK: Yardımcılar

    private func toggle(_ key: String, _ value: Bool, _ set: @escaping (Bool) -> Void, id: String) -> some View {
        Toggle(isOn: Binding(get: { value }, set: set)) {
            VStack(alignment: .leading, spacing: 2) {
                Text(L(key))
                let hint = L(key + "_hint")
                if hint != key + "_hint" { Text(hint).font(.footnote).foregroundStyle(.secondary) }
            }
        }
        .accessibilityIdentifier(id)
    }

    private func show(_ s: String) {
        message = s
        DispatchQueue.main.asyncAfter(deadline: .now() + 3) { if message == s { message = nil } }
    }
}
