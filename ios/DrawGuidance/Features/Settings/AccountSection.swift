import AuthenticationServices
import SwiftUI

/// Ayarlar > Genel'deki hesap bölümü (Android `AccountCard` / `AccountScreen`): Apple ve Google ile giriş, giriş
/// yapılmışsa ad, e-posta, giriş yöntemi, çıkış ve hesabı silme.
struct AccountSection: View {
    @ObservedObject var account: AccountStore = .shared
    @Environment(\.colorScheme) private var scheme
    @State private var confirmDelete = false
    @State private var deletePhrase = ""

    var body: some View {
        Section {
            if let u = account.user {
                signedIn(u)
            } else {
                signedOut
            }
            if account.busy {
                Text(L("account_busy")).font(.footnote).foregroundStyle(.tint)
            }
            if let m = account.message {
                Text(L(m) + (account.detail.map { "\n" + I18n.tr($0) } ?? ""))
                    .font(.footnote).foregroundStyle(.secondary)
                    .accessibilityIdentifier("account_message")
            }
        } header: {
            Text(L("account_title"))
        }
        .sheet(item: Binding(get: { account.pendingBirth.map(PendingBirth.init) }, set: { if $0 == nil, account.pendingBirth != nil { account.cancelBirth() } })) { p in
            BirthDateSheet(account: account, name: p.account.givenName ?? p.account.displayName ?? "")
        }
        .alert(L("account_delete_title"), isPresented: $confirmDelete) {
            TextField(L("account_delete_input_label"), text: $deletePhrase)
                .textInputAutocapitalization(.never)
            Button(L("account_delete_confirm"), role: .destructive) {
                guard deletePhrase.trimmingCharacters(in: .whitespaces).lowercased() == L("account_delete_phrase").lowercased() else {
                    account.message = "account_delete_mismatch"; return
                }
                Task { _ = await account.deleteAccount() }
            }
            Button(L("cancel_button"), role: .cancel) {}
        } message: {
            Text(L("account_delete_short") + "\n\n" + L("account_delete_type") + "\n\"" + L("account_delete_phrase") + "\"")
        }
    }

    @ViewBuilder private var signedOut: some View {
        Text(L("account_signin_text")).font(.subheadline).foregroundStyle(.secondary)
        if !account.available {
            Text(NSLocalizedString("ios.signin.not_configured", comment: "")).font(.footnote).foregroundStyle(.red)
        }
        // Apple'ın kendi düğmesi (App Store kuralı 4.8: Google ile giriş varsa Apple ile giriş de sunulmalı)
        Button {
            Task { await account.signIn(.apple) }
        } label: {
            SignInWithAppleButtonLabel(dark: scheme == .dark)
                .allowsHitTesting(false)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .frame(height: 46)
        .disabled(account.busy || !account.auth.appleAvailable)
        .opacity(account.auth.appleAvailable ? 1 : 0.4)
        .accessibilityIdentifier("account_sign_in_apple")

        Button {
            Task { await account.signIn(.google) }
        } label: {
            HStack(spacing: 10) {
                GoogleMark().frame(width: 20, height: 20)
                    .padding(6).background(Circle().fill(Color.white))
                Text(L("account_signin_google")).font(.headline).foregroundStyle(.white)
            }
            .frame(maxWidth: .infinity, minHeight: 46)
            .background(Capsule().fill(LinearGradient(colors: [Color(red: 0.36, green: 0.42, blue: 0.95), Color(red: 0.62, green: 0.36, blue: 0.93)], startPoint: .leading, endPoint: .trailing)))
        }
        .buttonStyle(.plain)
        .disabled(account.busy || !account.auth.googleAvailable)
        .opacity(account.auth.googleAvailable ? 1 : 0.4)
        .accessibilityIdentifier("account_sign_in_google")
    }

    @ViewBuilder private func signedIn(_ u: LocalUser) -> some View {
        HStack(spacing: 12) {
            AsyncImage(url: u.photoUrl.flatMap { URL(string: $0) }) { img in
                img.resizable().scaledToFill()
            } placeholder: {
                Text(String(u.name.prefix(1)).uppercased()).font(.title2.bold()).foregroundStyle(.white)
                    .frame(maxWidth: .infinity, maxHeight: .infinity).background(Color.accentColor)
            }
            .frame(width: 48, height: 48).clipShape(Circle())
            VStack(alignment: .leading, spacing: 2) {
                Text(u.name).font(.headline).lineLimit(1).accessibilityIdentifier("account_name")
                if let e = u.email { Text(e).font(.footnote).foregroundStyle(.secondary).lineLimit(1) }
                Label(u.method == .apple ? "Apple" : "Google", systemImage: u.method == .apple ? "apple.logo" : "g.circle")
                    .font(.caption).foregroundStyle(.secondary)
            }
        }
        Button(L("account_sign_out")) { account.signOut() }
            .disabled(account.busy)
            .accessibilityIdentifier("account_sign_out")
        Button(L("account_delete"), role: .destructive) { deletePhrase = ""; confirmDelete = true }
            .disabled(account.busy)
            .accessibilityIdentifier("account_delete")
    }
}

private struct PendingBirth: Identifiable {
    let account: AuthAccount
    var id: String { account.uid }
}

/// Apple'ın giriş düğmesi görünümü (dokunma `Button`'da; akışı `AccountStore` yürütür).
private struct SignInWithAppleButtonLabel: UIViewRepresentable {
    let dark: Bool
    func makeUIView(context: Context) -> ASAuthorizationAppleIDButton {
        let b = ASAuthorizationAppleIDButton(type: .signIn, style: dark ? .white : .black)
        b.cornerRadius = 23
        b.isUserInteractionEnabled = false
        return b
    }
    func updateUIView(_ uiView: ASAuthorizationAppleIDButton, context: Context) {}
}

/// Google "G" işareti (dört renk).
struct GoogleMark: View {
    var body: some View {
        Canvas { ctx, size in
            let s = min(size.width, size.height)
            let rect = CGRect(x: s * 0.1, y: s * 0.1, width: s * 0.8, height: s * 0.8)
            let w = s * 0.18
            func arc(_ from: Double, _ to: Double, _ c: Color) {
                var p = Path()
                p.addArc(center: CGPoint(x: rect.midX, y: rect.midY), radius: rect.width / 2, startAngle: .degrees(from), endAngle: .degrees(to), clockwise: false)
                ctx.stroke(p, with: .color(c), lineWidth: w)
            }
            arc(-40, 45, Color(red: 0.26, green: 0.52, blue: 0.96))
            arc(45, 135, Color(red: 0.20, green: 0.66, blue: 0.33))
            arc(135, 200, Color(red: 0.98, green: 0.74, blue: 0.02))
            arc(200, 320, Color(red: 0.92, green: 0.26, blue: 0.21))
            var bar = Path()
            bar.addRect(CGRect(x: rect.midX, y: rect.midY - w / 2, width: rect.width / 2, height: w))
            ctx.fill(bar, with: .color(Color(red: 0.26, green: 0.52, blue: 0.96)))
        }
    }
}

/// İlk girişte doğum tarihi ve kullanım şartları / KVKK onayı (Android `BirthDateDialog` + onay kutuları).
private struct BirthDateSheet: View {
    @ObservedObject var account: AccountStore
    let name: String
    @Environment(\.dismiss) private var dismiss
    @State private var date = Calendar.current.date(byAdding: .year, value: -18, to: Date()) ?? Date()
    @State private var terms = false
    @State private var kvkk = false
    @State private var legal: String?

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Text(L("birth_date_text", name))
                    DatePicker(L("birth_date_label"), selection: $date, in: ...Date(), displayedComponents: .date)
                        .accessibilityIdentifier("birth_date_picker")
                }
                Section {
                    Toggle(isOn: $terms) { Text(L("consent_terms_text", L("consent_terms_link"))) }
                    Button(L("consent_terms_link")) { legal = "terms" }
                    Toggle(isOn: $kvkk) { Text(L("consent_kvkk_text", L("consent_kvkk_link"))) }
                    Button(L("consent_kvkk_link")) { legal = "kvkk" }
                } footer: {
                    if !(terms && kvkk) { Text(L("consent_required")) }
                }
            }
            .navigationTitle(L("birth_date_title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(L("cancel_button")) { account.cancelBirth(); dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(L("consent_create_account")) { Task { await account.completeBirth(date) } }
                        .disabled(!(terms && kvkk))
                        .accessibilityIdentifier("birth_date_confirm")
                }
            }
            .sheet(item: Binding(get: { legal.map(LegalDoc.init) }, set: { legal = $0?.name })) { doc in
                NavigationStack {
                    ScrollView { Text(doc.text).padding() }
                        .toolbar { ToolbarItem(placement: .confirmationAction) { Button(L("legal_close")) { legal = nil } } }
                }
            }
        }
        .interactiveDismissDisabled()
    }
}

/// Uygulamayla gelen hukuki metin (Android `assets/legal`; Türkçe dışında İngilizce).
private struct LegalDoc: Identifiable {
    let name: String
    var id: String { name }
    var text: String {
        let lang = I18n.language == "tr" ? "tr" : "en"
        guard let url = Bundle.main.url(forResource: "\(name)_\(lang)", withExtension: "txt", subdirectory: "legal"),
              let s = try? String(contentsOf: url, encoding: .utf8) else { return "" }
        return s
    }
}
