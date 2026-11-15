import SwiftUI

/// v51 paylaşım sayfası (Android `ShareScreen`): bu cihazın adı ve görünürlüğü, aynı Wi-Fi ağındaki cihazlar
/// (tuval gönder, resim gönder, yayın başlat), aktarım durumu, yayın kartları ve paylaşım ayarları.
/// v52 sayfa türü: tuval/resim paylaşımı ya da canlı tuval yayını (ayrı sayfalar, ayrı ayarlar).
enum ShareMode { case send, live }

struct ShareView: View {
    var mode: ShareMode = .send
    @ObservedObject private var share = ShareManager.shared
    @ObservedObject private var settings = ShareSettings.shared
    @EnvironmentObject private var store: CanvasStore
    @State private var renaming = false
    @State private var newName = ""
    @State private var pick: Pick?

    enum PickKind { case canvas, live, image }
    struct Pick: Identifiable { let peer: SharePeer; let kind: PickKind; var id: String { peer.id + "\(kind)" } }

    var body: some View {
        List {
            Section {
                HStack(spacing: 12) {
                    Image(systemName: UIDevice.current.userInterfaceIdiom == .pad ? "ipad" : "iphone").font(.title2)
                        .foregroundStyle(Color.accentColor).frame(width: 36)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(settings.deviceName).font(.headline)
                        Text(L(share.active ? "v51_share_visible" : "v51_share_off")).font(.caption).foregroundStyle(.secondary)
                    }
                    Spacer()
                    Button(L("v51_share_rename")) { newName = settings.deviceName; renaming = true }
                        .buttonStyle(.borderless)
                        .accessibilityIdentifier("share_rename")
                }
                Text(L(mode == .live ? "v52_share_live_how" : "v52_share_send_how")).font(.footnote).foregroundStyle(.secondary)
                if let e = share.error {
                    Text(L("v51_share_error", e)).font(.footnote).foregroundStyle(.orange)
                        .onTapGesture { share.clearError() }
                }
            } header: { Text(L("v51_share_this_device")) }

            if let s = share.status {
                Section {
                    VStack(alignment: .leading, spacing: 8) {
                        Text(s.sending ? L("v51_share_sending", s.title, s.peerName) : L("v51_share_receiving", s.peerName)).font(.subheadline)
                        if let p = s.progress { ProgressView(value: p) } else { ProgressView().frame(maxWidth: .infinity, alignment: .leading) }
                    }
                    .accessibilityElement(children: .contain)
                    .accessibilityIdentifier("share_status")
                }
            }

            if mode == .live, let out = share.liveOut {
                Section {
                    LiveCard(text: L("v51_share_live_out", store.canvases.first { $0.localId == out.canvasId }?.name ?? "", out.peerName),
                             tint: .red) {
                        Button(L("v51_share_live_open")) { share.openCanvas = ShareOpenCanvas(id: out.canvasId) }
                        Button(L("v51_share_live_stop"), role: .destructive) { share.stopLive() }
                    }
                }
            }
            if mode == .live, let inn = share.liveIn {
                Section {
                    LiveCard(text: inn.ended ? L("v51_share_live_ended")
                                 : L("v51_share_live_in", inn.peerName, share.liveCanvas?.name ?? ""), tint: inn.ended ? .gray : .indigo) {
                        if !inn.ended { Button(L("v51_share_live_open")) { share.showLive = true } }
                        Button(L("v51_share_live_leave"), role: .destructive) { share.leaveLive() }
                    }
                }
            }

            Section {
                if share.peers.isEmpty {
                    HStack(spacing: 12) {
                        ProgressView()
                        Text(L("v51_share_nearby_empty")).font(.footnote).foregroundStyle(.secondary)
                    }
                    .padding(.vertical, 6)
                } else {
                    ForEach(share.peers) { p in
                        PeerRow(peer: p, mode: mode, onPick: { pick = Pick(peer: p, kind: $0) }, onDisconnect: { share.disconnect(p) })
                    }
                }
            } header: { Text(L("v51_share_nearby_title")) }

            if mode == .live { LiveSettingsSection(showOpenLink: false) } else { ShareSettingsSection(showOpenLink: false) }
        }
        .navigationTitle(L(mode == .live ? "v52_share_live_title" : "v52_share_send_title"))
        .onAppear { share.pageOpened() }
        .onDisappear { share.pageClosed() }
        .alert(L("v51_share_device_name"), isPresented: $renaming) {
            TextField(L("v51_share_device_name"), text: $newName)
            Button(L("v51_share_save")) { share.rename(newName) }
            Button(L("cancel_button"), role: .cancel) {}
        } message: { Text(L("v51_share_device_name_hint")) }
        .sheet(item: $pick) { p in
            switch p.kind {
            case .canvas, .live:
                CanvasPickSheet(title: L(p.kind == .live ? "v51_share_pick_live" : "v51_share_pick_canvas")) { c in
                    if p.kind == .live { share.startLive(p.peer.id, c) } else { share.sendCanvas(p.peer.id, c) }
                }
            case .image:
                if let repo = EditorDependencies.shared.images {
                    ImagePickSheet(repo: repo) { share.sendImage(p.peer.id, $0) }
                }
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier(mode == .live ? "share_live_screen" : "share_screen")
    }
}

private struct LiveCard<Actions: View>: View {
    let text: String
    let tint: Color
    @ViewBuilder let actions: () -> Actions

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Label(text, systemImage: "dot.radiowaves.left.and.right").foregroundStyle(tint).font(.subheadline.weight(.semibold))
            HStack { actions() }.buttonStyle(.bordered)
        }
        .padding(.vertical, 4)
    }
}

private struct PeerRow: View {
    let peer: SharePeer
    var mode: ShareMode = .send
    let onPick: (ShareView.PickKind) -> Void
    let onDisconnect: () -> Void

    private var stateText: String {
        switch peer.state {
        case .found: return L("v51_share_route_lan")
        case .connecting: return L("v51_share_connecting")
        case .connected: return L("v51_share_connected") + " · " + L("v51_share_route_lan")
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 12) {
                Image(systemName: peer.platform == "ios" ? "iphone" : "smartphone").font(.title3).frame(width: 32)
                    .foregroundStyle(peer.state == .connected ? Color.green : Color.accentColor)
                VStack(alignment: .leading, spacing: 2) {
                    Text(peer.name).font(.subheadline.weight(.semibold))
                    Text(L(peer.platform == "ios" ? "v51_share_platform_ios" : "v51_share_platform_android") + " · " + stateText)
                        .font(.caption).foregroundStyle(.secondary)
                }
                Spacer()
                if peer.state == .connecting { ProgressView() }
                if peer.state == .connected {
                    Button(action: onDisconnect) { Image(systemName: "xmark.circle") }
                        .buttonStyle(.borderless)
                        .accessibilityLabel(L("v51_share_disconnect"))
                        .accessibilityIdentifier("share_disconnect")
                }
            }
            // v52 gönderme sayfasında tuval/resim, yayın sayfasında yalnız yayın başlatma
            HStack(spacing: 8) {
                if mode == .send {
                    Button { onPick(.canvas) } label: { Label(L("v51_share_send_canvas"), systemImage: "square.and.arrow.up") }
                        .accessibilityIdentifier("share_send_canvas")
                    Button { onPick(.image) } label: { Label(L("v51_share_send_image"), systemImage: "photo") }
                        .accessibilityIdentifier("share_send_image")
                } else {
                    Button { onPick(.live) } label: { Label(L("v51_share_start_live"), systemImage: "dot.radiowaves.left.and.right") }
                        .accessibilityIdentifier("share_start_live")
                }
            }
            .buttonStyle(.bordered)
            .font(.caption)
            .labelStyle(.titleAndIcon)
        }
        .padding(.vertical, 4)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("share_peer_\(peer.id)")
    }
}

/// Paylaşım ayarları (paylaşım sayfasında ve Ayarlar > Genel'de).
struct ShareSettingsSection: View {
    @ObservedObject private var share = ShareManager.shared
    @ObservedObject private var settings = ShareSettings.shared
    var showOpenLink = true

    var body: some View {
        Section {
            if showOpenLink {
                NavigationLink { ShareView(mode: .send) } label: { Label(L("v52_share_send_open"), systemImage: "square.and.arrow.up") }
                    .accessibilityIdentifier("settings_share")
            }
            Toggle(isOn: Binding(get: { settings.discoverable }, set: { share.setDiscoverable($0) })) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(L("v51_share_discoverable"))
                    Text(L("v51_share_discoverable_hint")).font(.caption).foregroundStyle(.secondary)
                }
            }
            .accessibilityIdentifier("share_discoverable")
            Toggle(isOn: Binding(get: { settings.askBeforeReceive }, set: { settings.setAskBeforeReceive($0) })) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(L("v51_share_ask"))
                    Text(L("v52_share_ask_hint")).font(.caption).foregroundStyle(.secondary)
                }
            }
            .accessibilityIdentifier("share_ask")
        } header: { Text(L(showOpenLink ? "v52_share_send_title" : "v51_share_settings_title")) } footer: {
            if showOpenLink { Text(L("v52_share_send_hint")) }
        }
    }
}

/// v52 canlı tuval yayını ayarları (yayın sayfasında ve Ayarlar'da; tuval paylaşımından ayrı).
struct LiveSettingsSection: View {
    @ObservedObject private var share = ShareManager.shared
    @ObservedObject private var settings = ShareSettings.shared
    var showOpenLink = true

    var body: some View {
        Section {
            if showOpenLink {
                NavigationLink { ShareView(mode: .live) } label: { Label(L("v52_share_live_open"), systemImage: "dot.radiowaves.left.and.right") }
                    .accessibilityIdentifier("settings_live")
            }
            Toggle(isOn: Binding(get: { settings.discoverable }, set: { share.setDiscoverable($0) })) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(L("v51_share_discoverable"))
                    Text(L("v51_share_discoverable_hint")).font(.caption).foregroundStyle(.secondary)
                }
            }
            Toggle(isOn: Binding(get: { settings.askBeforeLive }, set: { settings.setAskBeforeLive($0) })) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(L("v52_live_ask"))
                    Text(L("v52_live_ask_hint")).font(.caption).foregroundStyle(.secondary)
                }
            }
            .accessibilityIdentifier("live_ask")
            Toggle(isOn: Binding(get: { settings.liveAutoOpen }, set: { settings.setLiveAutoOpen($0) })) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(L("v52_live_auto_open"))
                    Text(L("v52_live_auto_open_hint")).font(.caption).foregroundStyle(.secondary)
                }
            }
            .accessibilityIdentifier("live_auto_open")
            Toggle(isOn: Binding(get: { settings.liveSmooth }, set: { settings.setLiveSmooth($0) })) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(L("v51_share_smooth"))
                    Text(L("v51_share_smooth_hint")).font(.caption).foregroundStyle(.secondary)
                }
            }
            .accessibilityIdentifier("share_smooth")
        } header: { Text(L(showOpenLink ? "v52_share_live_title" : "v52_share_live_settings_title")) } footer: {
            if showOpenLink { Text(L("v52_share_live_hint")) }
        }
    }
}

private struct CanvasPickSheet: View {
    let title: String
    let onPick: (CanvasUi) -> Void
    @EnvironmentObject private var store: CanvasStore
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                if store.canvases.isEmpty { Text(L("v51_share_no_canvases")).foregroundStyle(.secondary) }
                ForEach(store.canvases) { c in
                    Button { dismiss(); onPick(c) } label: {
                        HStack(spacing: 12) {
                            CanvasThumbnail(canvas: c, side: 56).frame(width: 56, height: 56).clipShape(RoundedRectangle(cornerRadius: 8))
                            VStack(alignment: .leading, spacing: 2) {
                                Text(c.name).font(.subheadline.weight(.medium)).lineLimit(1)
                                Text(c.kind.label).font(.caption).foregroundStyle(.secondary)
                            }
                        }
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("share_pick_\(c.localId)")
                }
            }
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button(L("cancel_button")) { dismiss() } } }
        }
    }
}

private struct ImagePickSheet: View {
    let repo: ImagesRepository
    let onPick: (SavedImage) -> Void
    @ObservedObject private var images: SavedImagesStore
    @Environment(\.dismiss) private var dismiss

    init(repo: ImagesRepository, onPick: @escaping (SavedImage) -> Void) {
        self.repo = repo; self.onPick = onPick
        _images = ObservedObject(wrappedValue: repo.store)
    }

    var body: some View {
        NavigationStack {
            let list = images.images.filter(\.isImage)
            ScrollView {
                if list.isEmpty { Text(L("v51_share_no_images")).foregroundStyle(.secondary).padding(40) }
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 100), spacing: 10)], spacing: 10) {
                    ForEach(list) { img in
                        Button { dismiss(); onPick(img) } label: {
                            SavedThumb(url: repo.url(img), mime: img.mime).frame(height: 100).clipShape(RoundedRectangle(cornerRadius: 10))
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(img.title)
                    }
                }
                .padding(12)
            }
            .navigationTitle(L("v51_share_pick_image"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button(L("cancel_button")) { dismiss() } } }
        }
    }
}

/// Uygulama genelinde paylaşım pencereleri (HomeView'e bağlanır): gelen teklif, alınan içerik, bildirimler, yayın
/// görüntüleyici ve yayın/alınan tuval için düzenleyici.
struct ShareHostModifier: ViewModifier {
    @ObservedObject private var share = ShareManager.shared

    func body(content: Content) -> some View {
        content
            .alert(L("v51_share_offer_title"), isPresented: Binding(get: { share.offer != nil }, set: { if !$0 && share.offer != nil { share.answerOffer(false) } }),
                   presenting: share.offer) { _ in
                Button(L("v51_share_accept")) { share.answerOffer(true) }
                Button(L("v51_share_reject"), role: .cancel) { share.answerOffer(false) }
            } message: { o in
                let key = o.offer.type == ShareWire.TYPE_LIVE ? "v51_share_offer_live"
                    : o.offer.type == ShareWire.TYPE_IMAGE ? "v51_share_offer_image" : "v51_share_offer_canvas"
                Text(L(key, o.peerName, o.offer.title) + (o.offer.bytes > 0
                    ? "\n" + L("v51_share_offer_size", ByteCountFormatter.string(fromByteCount: o.offer.bytes, countStyle: .file)) : ""))
            }
            .alert("", isPresented: Binding(get: { share.received != nil }, set: { if !$0 { share.received = nil } }), presenting: share.received) { r in
                if let id = r.canvasId { Button(L("v51_share_open_canvas")) { share.received = nil; share.openCanvas = ShareOpenCanvas(id: id) } }
                Button(L("v51_share_close"), role: .cancel) { share.received = nil }
            } message: { r in
                Text(L(r.type == ShareWire.TYPE_IMAGE ? "v51_share_received_image" : "v51_share_received_canvas", r.title, r.from))
            }
            .overlay(alignment: .bottom) {
                if let t = share.toast {
                    Text(t).font(.subheadline).foregroundStyle(.white).padding(.horizontal, 16).padding(.vertical, 10)
                        .background(Capsule().fill(Color.black.opacity(0.78))).padding(.bottom, 90)
                        .onTapGesture { share.toast = nil }
                        .task(id: t) { try? await Task.sleep(nanoseconds: 3_500_000_000); if share.toast == t { share.toast = nil } }
                        .accessibilityIdentifier("share_toast")
                }
            }
            .fullScreenCover(isPresented: $share.showLive) { LiveCanvasView() }
            .fullScreenCover(item: $share.openCanvas) { c in
                NavigationStack { CanvasEditScreen(canvasLocalId: c.id) }
            }
    }
}

extension View {
    func shareHost() -> some View { modifier(ShareHostModifier()) }
}

/// Yayını alan tarafta canlı tuval (Android `LiveCanvasScreen`).
struct LiveCanvasView: View {
    @ObservedObject private var share = ShareManager.shared
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ZStack {
                Color(uiColor: .secondarySystemBackground).ignoresSafeArea()
                if let c = share.liveCanvas {
                    CanvasThumbnail(canvas: c, side: 1100)
                        .aspectRatio(CGFloat(max(c.width, 1)) / CGFloat(max(c.height, 1)), contentMode: .fit)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                        .shadow(color: .black.opacity(0.1), radius: 6, y: 2)
                        .padding(16)
                        .accessibilityIdentifier("live_canvas")
                } else {
                    VStack(spacing: 12) { ProgressView(); Text(L("v51_share_live_waiting")).foregroundStyle(.secondary) }
                }
            }
            .safeAreaInset(edge: .bottom) {
                if let inn = share.liveIn {
                    Text(inn.ended ? L("v51_share_live_ended") : inn.paused ? L("v52_live_paused") : L("v51_share_live_in", inn.peerName, share.liveCanvas?.name ?? ""))
                        .font(.footnote.weight(.semibold)).foregroundStyle(.white)
                        .padding(.horizontal, 14).padding(.vertical, 8)
                        .background(Capsule().fill(inn.ended ? Color.gray : Color.red))
                        .padding(.bottom, 8)
                }
            }
            .navigationTitle(share.liveCanvas?.name ?? L("v51_share_live_title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(L("v51_share_close")) { share.showLive = false; dismiss() }.accessibilityIdentifier("live_close")
                }
                ToolbarItem(placement: .primaryAction) {
                    Menu {
                        if let inn = share.liveIn {
                            Button(L("v51_share_live_edit")) {
                                let id = inn.canvasId
                                share.leaveLive()
                                share.showLive = false
                                DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { share.openCanvas = ShareOpenCanvas(id: id) }
                            }
                        }
                        Button(L("v51_share_live_leave"), role: .destructive) { share.leaveLive(); dismiss() }
                    } label: { Image(systemName: "ellipsis.circle") }
                    .accessibilityIdentifier("live_menu")
                }
            }
        }
    }
}

/// Yayın yapılan tuvalde düzenleyicinin altındaki kırmızı "Yayında" rozeti; tuvalin her değişikliği yayına gider.
struct LiveBroadcastBridge: View {
    @ObservedObject private var share = ShareManager.shared
    @ObservedObject var store: EditorStore
    let canvasLocalId: String
    @State private var confirmStop = false

    var body: some View {
        ZStack(alignment: .bottom) {
            Color.clear.frame(width: 1, height: 1).allowsHitTesting(false)
            if let out = share.liveOut, out.canvasId == canvasLocalId {
                Button { confirmStop = true } label: {
                    HStack(spacing: 6) {
                        Circle().fill(.white).frame(width: 8, height: 8)
                        Text(L("v51_share_live_badge") + " · " + out.peerName).lineLimit(1)
                        Image(systemName: "stop.fill")
                    }
                    .font(.caption.weight(.semibold)).foregroundStyle(.white)
                    .padding(.horizontal, 12).padding(.vertical, 7)
                    .background(Capsule().fill(Color.red))
                }
                .buttonStyle(.plain)
                .padding(.bottom, 6)
                .accessibilityIdentifier("live_badge")
                .confirmationDialog(L("v51_share_live_stop"), isPresented: $confirmStop) {
                    Button(L("v51_share_live_stop"), role: .destructive) { share.stopLive() }
                }
            }
        }
        .onReceive(store.$canvas) { c in
            if let c, share.isBroadcasting(c.localId) { share.publishLive(c) }
        }
        .onChange(of: share.liveOut) { _, out in
            // Yayın başlarken tuvalin o anki hali hemen gider
            if let out, out.canvasId == canvasLocalId, let c = store.canvas { share.publishLive(c) }
        }
    }
}
