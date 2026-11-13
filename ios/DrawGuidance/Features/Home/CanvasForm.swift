import SwiftUI

/// v40 özel boyut birimi.
enum SizeUnit: String, CaseIterable { case PX = "px", IN = "in", CM = "cm" }

/// v40 tuval türüne uygun hazır boyut.
struct SizePreset: Equatable { let key: String; let label: String; let width: Int; let height: Int }

enum SizeIssue { case TOO_SMALL, TOO_LARGE, MEMORY }

/// v40 yeni tuval boyutları (Android `CanvasSizing`): türün hazır boyutları ve px / in / cm özel boyut.
enum CanvasSizing {
    static let MIN_SIDE = 16
    static let MAX_SIDE = 8192  // FrameController.MAX_CANVAS
    static let DEFAULT_DPI = 300, MIN_DPI = 36, MAX_DPI = 1200
    static let CUSTOM = "custom"

    private static let square = SizePreset(key: "square", label: "v40_size_square", width: 1080, height: 1080)
    private static let portrait = SizePreset(key: "portrait", label: "v40_size_portrait", width: 1080, height: 1920)
    private static let landscape = SizePreset(key: "landscape", label: "v40_size_landscape", width: 1920, height: 1080)
    private static let wallpaper = SizePreset(key: "wallpaper", label: "v40_size_wallpaper", width: 1080, height: 2400)
    private static let a4Print = SizePreset(key: "a4_300", label: "v40_size_a4_300", width: 2480, height: 3508)
    private static let a4 = SizePreset(key: "a4", label: "v40_size_a4", width: 1240, height: 1754)
    private static let a5 = SizePreset(key: "a5", label: "v40_size_a5", width: 874, height: 1240)
    private static let letter = SizePreset(key: "letter", label: "v40_size_letter", width: 1275, height: 1650)
    private static let a4Landscape = SizePreset(key: "a4_landscape", label: "v40_size_a4_landscape", width: 1754, height: 1240)
    private static let comicPage = SizePreset(key: "comic_page", label: "v40_size_comic_page", width: 1200, height: 1800)
    private static let usComic = SizePreset(key: "us_comic", label: "v40_size_us_comic", width: 1988, height: 3075)
    private static let mangaB5 = SizePreset(key: "manga_b5", label: "v40_size_manga_b5", width: 2150, height: 3035)
    private static let webtoon = SizePreset(key: "webtoon", label: "v40_size_webtoon", width: 800, height: 1280)

    static func presets(_ kind: CanvasKind) -> [SizePreset] {
        switch kind {
        case .ART: return [square, portrait, landscape, wallpaper, a4Print]
        case .NOTES: return [a4, a5, letter, a4Landscape]
        case .COMIC: return [comicPage, usComic, mangaB5, webtoon]
        case .SIMPLE_ART: return [square, portrait, landscape]
        case .SIMPLE_NOTES: return [a4, a5]
        case .KIDS: return [square, landscape]
        }
    }

    static func match(_ kind: CanvasKind, _ w: Int, _ h: Int) -> String { presets(kind).first { $0.width == w && $0.height == h }?.key ?? CUSTOM }

    static func toPx(_ v: Double, _ unit: SizeUnit, _ dpi: Int) -> Int {
        switch unit {
        case .PX: return Int(v.rounded())
        case .IN: return Int((v * Double(dpi)).rounded())
        case .CM: return Int((v / 2.54 * Double(dpi)).rounded())
        }
    }

    static func fromPx(_ px: Int, _ unit: SizeUnit, _ dpi: Int) -> Double {
        switch unit {
        case .PX: return Double(px)
        case .IN: return Double(px) / Double(dpi)
        case .CM: return Double(px) / Double(dpi) * 2.54
        }
    }

    static func format(_ v: Double, _ unit: SizeUnit) -> String {
        if unit == .PX { return String(Int(v.rounded())) }
        var s = String(format: "%.2f", v)
        while s.hasSuffix("0") { s.removeLast() }
        if s.hasSuffix(".") { s.removeLast() }
        return s
    }

    static func parse(_ text: String) -> Double? {
        guard let v = Double(text.trimmingCharacters(in: .whitespaces).replacingOccurrences(of: ",", with: ".")), v > 0, v.isFinite else { return nil }
        return v
    }

    /// Bellekte güvenle açılabilecek en çok piksel (bir katmanın ARGB görüntüsü belleğin 1/16'sını aşmaz).
    static func maxPixels(_ memory: UInt64 = ProcessInfo.processInfo.physicalMemory) -> Int64 {
        min(Int64(MAX_SIDE) * Int64(MAX_SIDE), Int64(memory / 4 / 16))
    }

    static func validate(_ w: Int, _ h: Int, _ maxPixels: Int64 = maxPixels()) -> SizeIssue? {
        if w < MIN_SIDE || h < MIN_SIDE { return .TOO_SMALL }
        if w > MAX_SIDE || h > MAX_SIDE { return .TOO_LARGE }
        if Int64(w) * Int64(h) > maxPixels { return .MEMORY }
        return nil
    }
}

extension CanvasKind {
    var labelKey: String {
        switch self {
        case .ART: return "canvas_kind_art"
        case .NOTES: return "canvas_kind_notes"
        case .COMIC: return "canvas_kind_comic"
        case .SIMPLE_ART: return "canvas_kind_simple_art"
        case .SIMPLE_NOTES: return "canvas_kind_simple_notes"
        case .KIDS: return "canvas_kind_kids"
        }
    }
    var label: String { L(labelKey) }
    var hint: String { L(labelKey + "_hint") }
}

/// Formun sonucu (Android `CanvasFormResult`).
struct CanvasFormResult {
    var name: String
    var width: Int
    var height: Int
    var paperHex: String
    var aiEnabled: Bool
    var kind: CanvasKind
    var pageCount = 1
    var background = BackgroundSpec()
    /// Galeriden seçilen arka plan resmi (tuval oluşturulunca eklenir).
    var galleryData: Data?
    var templateId: String?
}

/// Yeni Tuval Oluştur / Tuval Ayarları formu (Android `CanvasFormModal`): tür, ad, v46 şablon, v40 boyut, kâğıt rengi,
/// v23 arka plan, sayfa sayısı ve yapay zekâ anahtarı.
struct CanvasFormSheet: View {
    let isEditMode: Bool
    let kindChoices: [CanvasKind]
    let onSubmit: (CanvasFormResult) -> Void
    @Environment(\.dismiss) private var dismiss

    @State private var name: String
    @State private var kind: CanvasKind
    @State private var paperHex: String
    @State private var aiEnabled: Bool
    @State private var pageCount = 1
    @State private var background: BackgroundSpec
    @State private var templateId: String?
    @State private var galleryData: Data?
    @State private var galleryPreview: CGImage?
    // Boyut
    @State private var sizeKey: String
    @State private var unit: SizeUnit = .PX
    @State private var dpiText = String(CanvasSizing.DEFAULT_DPI)
    @State private var wText: String
    @State private var hText: String

    static let paperSwatches = ["#FFFFFF", "#F6F1E7", "#E8EEF6", "#1E1E24", "#FDE7C8", "#D9F0E3"]

    init(isEditMode: Bool = false, kind: CanvasKind, kindChoices: [CanvasKind], name: String = "", width: Int? = nil, height: Int? = nil,
         paperHex: String = "#FFFFFF", aiEnabled: Bool = true, background: BackgroundSpec = BackgroundSpec(),
         onSubmit: @escaping (CanvasFormResult) -> Void) {
        self.isEditMode = isEditMode
        self.kindChoices = kindChoices
        self.onSubmit = onSubmit
        let w = width ?? kind.defaultSize.width, h = height ?? kind.defaultSize.height
        _name = State(initialValue: name)
        _kind = State(initialValue: kind)
        _paperHex = State(initialValue: paperHex)
        _aiEnabled = State(initialValue: aiEnabled)
        _background = State(initialValue: background)
        _sizeKey = State(initialValue: CanvasSizing.match(kind, w, h))
        _wText = State(initialValue: String(w))
        _hText = State(initialValue: String(h))
    }

    private var dpi: Int { min(max(Int(dpiText) ?? CanvasSizing.DEFAULT_DPI, CanvasSizing.MIN_DPI), CanvasSizing.MAX_DPI) }
    private var preset: SizePreset? { CanvasSizing.presets(kind).first { $0.key == sizeKey } }
    private var width: Int { preset?.width ?? CanvasSizing.parse(wText).map { CanvasSizing.toPx($0, unit, dpi) } ?? 0 }
    private var height: Int { preset?.height ?? CanvasSizing.parse(hText).map { CanvasSizing.toPx($0, unit, dpi) } ?? 0 }
    private var issue: SizeIssue? { CanvasSizing.validate(width, height) }
    private var templates: [TemplateEntry] {
        isEditMode || !CanvasTemplates.KINDS.contains(kind) ? [] : CanvasTemplates.forKind(kind)
    }
    private var fromTemplate: Bool { templateId != nil }

    var body: some View {
        NavigationStack {
            Form {
                Section(L("canvas_kind_label")) {
                    if isEditMode {
                        Text(kind.label)
                    } else {
                        ForEach(kindChoices, id: \.self) { k in
                            Button { selectKind(k) } label: {
                                HStack(spacing: 12) {
                                    Image(systemName: k.systemImage).font(.title3).frame(width: 28)
                                        .foregroundStyle(kind == k ? Color.accentColor : .secondary)
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(k.label).font(.subheadline.weight(.semibold)).foregroundStyle(kind == k ? Color.accentColor : .primary)
                                        Text(k.hint).font(.caption).foregroundStyle(.secondary)
                                    }
                                    Spacer()
                                    if kind == k { Image(systemName: "checkmark").foregroundStyle(Color.accentColor) }
                                }
                            }
                            .accessibilityIdentifier("form_kind_\(k.rawValue)")
                        }
                    }
                }
                Section {
                    TextField(L("canvas_name_placeholder"), text: $name).accessibilityIdentifier("canvas_form_name")
                } header: { Text(L("canvas_name_label")) }
                if !templates.isEmpty { templateSection }
                if !fromTemplate {
                    sizeSection
                    Section(L("canvas_background_color_label")) {
                        HStack(spacing: 12) {
                            ForEach(Self.paperSwatches, id: \.self) { hex in
                                let on = hex.caseInsensitiveCompare(paperHex) == .orderedSame
                                Button { paperHex = hex } label: {
                                    Circle().fill(Color(hex: hex) ?? .white).frame(width: 30, height: 30)
                                        .overlay(Circle().stroke(on ? Color.accentColor : Color.secondary.opacity(0.35), lineWidth: on ? 3 : 1))
                                }
                                .buttonStyle(.plain)
                                .accessibilityLabel(L("background_color_cd", hex))
                                .accessibilityAddTraits(on ? .isSelected : [])
                            }
                        }
                    }
                    if kind != .KIDS {
                        Section {
                            BackgroundPickerView(usage: kind.isNotes ? .NOTE : .ART, paperHex: paperHex, spec: $background,
                                                 showImages: kind != .COMIC,
                                                 onGallery: { data in
                                                     galleryData = data
                                                     galleryPreview = CanvasImageCache.decode(data, maxSide: 256)
                                                     background.imageId = BackgroundPickerView.GALLERY_PENDING
                                                     background.imageFit = .COVER
                                                 },
                                                 galleryPreview: galleryPreview)
                        }
                    }
                    if kind.paged && !isEditMode {
                        Section {
                            Picker(L("page_count_label"), selection: $pageCount) {
                                ForEach([1, 2, 4, 8, 16].filter { $0 <= kind.maxPages }, id: \.self) { Text("\($0)").tag($0) }
                            }
                            .pickerStyle(.segmented)
                        } header: { Text(L("page_count_label")) } footer: { Text(L("page_limit_hint", kind.maxPages)) }
                    }
                }
                Section {
                    Toggle(isOn: $aiEnabled) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(L("ai_switch_label"))
                            Text(L("ai_switch_hint")).font(.caption).foregroundStyle(.secondary)
                        }
                    }
                    .accessibilityIdentifier("canvas_form_ai")
                }
            }
            .navigationTitle(L(isEditMode ? "canvas_settings_title" : "new_canvas_title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(L("cancel_button")) { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(L(isEditMode ? "update_button" : "create_button")) { submit() }
                        .disabled(issue != nil && !fromTemplate)
                        .accessibilityIdentifier("canvas_form_submit")
                }
            }
        }
    }

    private func selectKind(_ k: CanvasKind) {
        guard k != kind else { return }
        kind = k
        templateId = nil
        if sizeKey != CanvasSizing.CUSTOM { sizeKey = CanvasSizing.presets(k)[0].key }
        if k == .COMIC && galleryData != nil { galleryData = nil; galleryPreview = nil; background.imageId = nil }
    }

    private func submit() {
        var bg = background
        if fromTemplate || bg.imageId == BackgroundPickerView.GALLERY_PENDING && galleryData == nil { bg.imageId = nil }
        let trimmed = name.trimmingCharacters(in: .whitespaces)
        onSubmit(CanvasFormResult(
            name: trimmed.isEmpty ? I18n.tr("Adsız Tuval") : trimmed,
            width: min(max(width, CanvasSizing.MIN_SIDE), CanvasSizing.MAX_SIDE),
            height: min(max(height, CanvasSizing.MIN_SIDE), CanvasSizing.MAX_SIDE),
            paperHex: paperHex, aiEnabled: aiEnabled, kind: kind,
            pageCount: kind.paged ? min(max(pageCount, 1), kind.maxPages) : 1,
            background: bg, galleryData: fromTemplate ? nil : galleryData, templateId: templateId))
        dismiss()
    }

    private var templateSection: some View {
        Section {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 12) {
                    templateCard(L("v46_template_blank"), templateId == nil, "template_card_blank") {
                        Image(systemName: "plus").font(.title2).foregroundStyle(.secondary)
                    } action: { templateId = nil }
                    ForEach(templates) { t in
                        templateCard(CanvasTemplates.name(t), templateId == t.id, "template_card_\(t.id)") {
                            TemplateCover(id: t.id)
                        } action: { templateId = t.id }
                    }
                }
                .padding(.vertical, 4)
            }
            .accessibilityIdentifier("template_picker")
        } header: { Text(L("v46_template_section")) } footer: {
            let hint = templateId.flatMap { id in templates.first { $0.id == id } }.map { CanvasTemplates.hint($0) } ?? ""
            Text(hint.isEmpty ? L("v46_template_blank_hint") : L("v46_template_selected_hint", hint)).accessibilityIdentifier("template_hint")
        }
    }

    private func templateCard<C: View>(_ title: String, _ selected: Bool, _ id: String, @ViewBuilder _ cover: () -> C,
                                       action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 6) {
                ZStack { Color.secondary.opacity(0.08); cover() }
                    .frame(width: 96, height: 133)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(selected ? Color.accentColor : Color.secondary.opacity(0.35), lineWidth: selected ? 3 : 1))
                Text(title).font(.caption).lineLimit(2).multilineTextAlignment(.center).frame(width: 100, height: 32, alignment: .top)
                    .foregroundStyle(selected ? Color.accentColor : .primary)
            }
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(id)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }

    private var sizeSection: some View {
        Section {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(CanvasSizing.presets(kind), id: \.key) { p in
                        chip(L(p.label), sizeKey == p.key, "size_preset_\(p.key)") { sizeKey = p.key }
                    }
                    chip(L("v40_size_custom"), sizeKey == CanvasSizing.CUSTOM, "size_preset_custom") {
                        if sizeKey != CanvasSizing.CUSTOM {
                            wText = CanvasSizing.format(CanvasSizing.fromPx(max(width, 1), unit, dpi), unit)
                            hText = CanvasSizing.format(CanvasSizing.fromPx(max(height, 1), unit, dpi), unit)
                        }
                        sizeKey = CanvasSizing.CUSTOM
                    }
                }
            }
            if let preset {
                Text(L("v40_size_result", preset.width, preset.height)).font(.caption).foregroundStyle(.secondary)
                    .accessibilityIdentifier("size_result")
            } else {
                Picker("", selection: Binding(get: { unit }, set: { u in
                    if u != unit {
                        if width > 0 { wText = CanvasSizing.format(CanvasSizing.fromPx(width, u, dpi), u) }
                        if height > 0 { hText = CanvasSizing.format(CanvasSizing.fromPx(height, u, dpi), u) }
                        unit = u
                    }
                })) { ForEach(SizeUnit.allCases, id: \.self) { Text($0.rawValue).tag($0) } }
                    .pickerStyle(.segmented)
                    .accessibilityIdentifier("size_units")
                HStack {
                    TextField(L("v40_size_width", unit.rawValue), text: $wText).keyboardType(unit == .PX ? .numberPad : .decimalPad)
                        .accessibilityIdentifier("size_width")
                    TextField(L("v40_size_height", unit.rawValue), text: $hText).keyboardType(unit == .PX ? .numberPad : .decimalPad)
                        .accessibilityIdentifier("size_height")
                    if unit != .PX {
                        TextField(L("v40_size_dpi"), text: $dpiText).keyboardType(.numberPad).frame(maxWidth: 70)
                            .accessibilityIdentifier("size_dpi")
                    }
                }
                Group {
                    switch issue {
                    case nil: Text(L("v40_size_result", width, height)).foregroundStyle(.secondary)
                    case .TOO_SMALL: Text(L("v40_size_error_min", CanvasSizing.MIN_SIDE)).foregroundStyle(.red)
                    case .TOO_LARGE: Text(L("v40_size_error_max", CanvasSizing.MAX_SIDE)).foregroundStyle(.red)
                    case .MEMORY: Text(L("v40_size_error_memory")).foregroundStyle(.red)
                    }
                }
                .font(.caption)
                .accessibilityIdentifier(issue == nil ? "size_result" : "size_error")
            }
        } header: { Text(L("canvas_size_label")) }
    }

    private func chip(_ label: String, _ on: Bool, _ id: String, _ run: @escaping () -> Void) -> some View {
        Button(action: run) {
            Text(label).font(.subheadline.weight(on ? .semibold : .regular)).lineLimit(1)
                .padding(.horizontal, 12).padding(.vertical, 7)
                .foregroundStyle(on ? Color.accentColor : Color.primary)
                .background(Capsule().fill(on ? Color.accentColor.opacity(0.18) : Color.primary.opacity(0.06)))
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(id)
    }
}

/// Şablonun kapak resmi.
struct TemplateCover: View {
    let id: String
    @State private var image: CGImage?

    var body: some View {
        ZStack { if let image { Image(decorative: image, scale: 1).resizable().scaledToFill() } }
            .task(id: id) {
                let key = id
                image = await Task.detached(priority: .utility) { CanvasTemplates.coverBytes(key).flatMap { CanvasImageCache.decode($0, maxSide: 320) } }.value
            }
    }
}
