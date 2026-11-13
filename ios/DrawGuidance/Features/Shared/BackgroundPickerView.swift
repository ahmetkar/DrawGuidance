import CoreGraphics
import PhotosUI
import SwiftUI

/// v23 arka plan seçimi (Android `BackgroundPicker`): gradyan, desen (türe göre hazır desenler; rengi, ölçeği ve
/// örtücülüğü) ve resim (hazır resimler ve galeriden; yerleşimi ve örtücülüğü). Tuval formunda ve çizim ekranında aynı.
struct BackgroundPickerView: View {
    let usage: PaperUsage
    let paperHex: String
    @Binding var spec: BackgroundSpec
    /// v42 çizgi romanda resim bölümü yok.
    var showImages = true
    /// Galeriden resim seçildi (bayt): çağıran resmi ekler ve [spec]'i günceller.
    var onGallery: ((Data) -> Void)?
    /// Galeriden seçilmiş, henüz eklenmemiş resmin önizlemesi (formda).
    var galleryPreview: CGImage?
    @State private var photo: PhotosPickerItem?

    static let patternColors = ["#7C89A6", "#7C9CC9", "#6FA287", "#D9935B", "#E57373", "#9C7CC9", "#8C95A3", "#3D4A5C", "#C9A227", "#FFFFFF"]

    private var paper: UInt32 { ArgbColor.parse(paperHex) ?? 0xFFFF_FFFF }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            title(L("gradient_title"))
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    tile(L("bg_none"), spec.gradient == nil, id: "bg_gradient_none") { noneIcon } action: { spec.gradient = nil }
                    ForEach(Array(Gradients.presets.enumerated()), id: \.offset) { i, g in
                        tile("", spec.gradient == g, id: "bg_gradient_\(i)") {
                            LinearGradient(colors: g.colors.compactMap { Color(hex: $0) }, startPoint: .top, endPoint: .bottom)
                        } action: { spec.gradient = g }
                    }
                }
            }
            title(L("bg_pattern"))
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    tile(L("bg_none"), spec.patternId == nil, id: "bg_pattern_none") { noneIcon } action: { spec.patternId = nil }
                    ForEach(PaperCatalog.of(usage), id: \.id) { p in
                        tile(I18n.tr(p.name), spec.patternId == p.id, id: "bg_pattern_\(p.id)") {
                            PatternThumb(pattern: p, colorHex: spec.patternColorHex, paper: paper)
                        } action: { spec.patternId = p.id }
                    }
                }
            }
            .accessibilityIdentifier("bg_patterns")
            if spec.patternId != nil {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(Self.patternColors, id: \.self) { hex in
                            let chosen = spec.patternColorHex?.caseInsensitiveCompare(hex) == .orderedSame
                                || (spec.patternColorHex == nil && hex == Self.patternColors[0])
                            Button { spec.patternColorHex = hex } label: {
                                Circle().fill(Color(hex: hex) ?? .gray).frame(width: 22, height: 22)
                                    .padding(4)
                                    .overlay(Circle().stroke(chosen ? Color.accentColor : Color.secondary.opacity(0.4), lineWidth: chosen ? 3 : 1))
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel(hex)
                            .accessibilityAddTraits(chosen ? .isSelected : [])
                        }
                    }
                }
                slider(L("bg_scale"), value: $spec.patternScale, 0.4...3)
                slider(L("bg_opacity"), value: $spec.patternOpacity, 0.1...1)
            }
            if showImages {
                title(L("bg_image"))
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 10) {
                        tile(L("bg_none"), spec.imageId == nil, id: "bg_image_none") { noneIcon } action: { spec.imageId = nil }
                        if onGallery != nil {
                            PhotosPicker(selection: $photo, matching: .images) {
                                tileBody(L("bg_gallery"), spec.assetId != nil || (galleryPreview != nil && spec.imageId == BackgroundPickerView.GALLERY_PENDING)) {
                                    if let galleryPreview, spec.imageId == BackgroundPickerView.GALLERY_PENDING {
                                        Image(decorative: galleryPreview, scale: 1).resizable().scaledToFill()
                                    } else if let id = spec.assetId, let img = PaperLibrary.assetImage?(id) {
                                        Image(decorative: img, scale: 1).resizable().scaledToFill()
                                    } else {
                                        Image(systemName: "photo.on.rectangle").font(.title2).foregroundStyle(Color.accentColor)
                                    }
                                }
                            }
                            .buttonStyle(.plain)
                            .accessibilityIdentifier("bg_gallery")
                        }
                        ForEach(PaperImages.all.filter { $0.usage == usage && !$0.id.hasPrefix("scene.") }, id: \.id) { item in
                            tile(I18n.tr(item.name), spec.paperImageId == item.id, id: "bg_image_\(item.id)") {
                                PaperImageThumb(id: item.id)
                            } action: {
                                spec.imageId = BackgroundSpec.PAPER + item.id
                                spec.imageFit = item.layout.kind == ImageLayout.TILE ? .TILE : .COVER
                            }
                        }
                    }
                }
                .accessibilityIdentifier("bg_images")
            }
            if spec.imageId != nil {
                Picker("", selection: $spec.imageFit) {
                    Text(L("fit_cover")).tag(BoxFit.COVER)
                    Text(L("fit_tile")).tag(BoxFit.TILE)
                    Text(L("fit_stretch")).tag(BoxFit.STRETCH)
                }
                .pickerStyle(.segmented)
                slider(L("bg_image_opacity"), value: $spec.imageOpacity, 0.1...1)
            }
        }
        .onChange(of: photo) { _, item in
            guard let item else { return }
            Task {
                if let data = try? await item.loadTransferable(type: Data.self) { onGallery?(data) }
                photo = nil
            }
        }
    }

    /// Formda galeriden seçilen, tuval oluşturulunca eklenecek resmin geçici kimliği (Android `GALLERY_PENDING`).
    static let GALLERY_PENDING = "pending:gallery"

    private var noneIcon: some View { Image(systemName: "nosign").font(.title3).foregroundStyle(.secondary) }

    private func title(_ s: String) -> some View { Text(s).font(.subheadline.weight(.semibold)) }

    private func slider(_ label: String, value: Binding<Float>, _ range: ClosedRange<Float>) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack {
                Text(label).font(.subheadline)
                Spacer()
                Text("\(Int((value.wrappedValue * 100).rounded()))%").font(.caption.weight(.semibold)).foregroundStyle(Color.accentColor)
            }
            Slider(value: Binding(get: { Double(value.wrappedValue) }, set: { value.wrappedValue = Float($0) }),
                   in: Double(range.lowerBound)...Double(range.upperBound))
        }
    }

    private func tile<C: View>(_ name: String, _ selected: Bool, id: String, @ViewBuilder _ content: () -> C,
                               action: @escaping () -> Void) -> some View {
        Button(action: action) { tileBody(name, selected, content) }
            .buttonStyle(.plain)
            .accessibilityIdentifier(id)
    }

    private func tileBody<C: View>(_ name: String, _ selected: Bool, @ViewBuilder _ content: () -> C) -> some View {
        VStack(spacing: 4) {
            ZStack { Color(uiColor: .secondarySystemBackground); content() }
                .frame(width: 64, height: 64)
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(selected ? Color.accentColor : Color.secondary.opacity(0.3), lineWidth: selected ? 3 : 1))
            if !name.isEmpty {
                Text(name).font(.caption2).lineLimit(1).frame(width: 70)
                    .foregroundStyle(selected ? Color.accentColor : Color.primary)
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel(name)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

/// Desenin küçük önizlemesi: kağıt renginde, sayfanın ~300 birimlik bir köşesi (Android `PatternThumb`).
struct PatternThumb: View {
    let pattern: PaperPattern
    let colorHex: String?
    let paper: UInt32

    var body: some View {
        Canvas { g, size in
            g.fill(Path(CGRect(origin: .zero, size: size)), with: .color(Color(argb: Int32(bitPattern: paper))))
            let main = BackgroundRenderer.parseColor(colorHex, pattern.color)
            let w = Float(size.width), h = Float(size.height)
            g.withCGContext { cg in
                BackgroundRenderer.drawPattern(cg, pattern, PatternColors(main, paper, pattern.accent), 0, 0, w, h, scale: w / 300, vector: true)
            }
        }
    }
}

/// Hazır arka plan resminin küçük önizlemesi (ilk istekte üretilir).
struct PaperImageThumb: View {
    let id: String
    @State private var image: CGImage?

    var body: some View {
        ZStack {
            if let image { Image(decorative: image, scale: 1).resizable().scaledToFill() } else { ProgressView().controlSize(.small) }
        }
        .task(id: id) {
            let key = id
            image = await Task.detached(priority: .utility) { PaperLibrary.image(key) }.value
        }
    }
}
