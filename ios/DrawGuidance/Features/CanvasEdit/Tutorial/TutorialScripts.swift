import Foundation

/// v32 eğitimlerin içeriği (Android `TutorialScripts`): her tuval türünde (çizim, not alma, çizgi roman) araç grupları ve
/// adımları. Her grup boş bir sayfada başlar ve adım adım bitmiş, profesyonel görünen bir çizim ortaya çıkarır: gün batımı
/// manzarası, fizik ders notu, çizgi roman sayfası. Yapay zekâ gruplarında gerçek öneriler (şekil düzeltme, şablon, tümünü
/// düzelt, el yazısı) gösterilir; öneri gelmezse aynı sonuç doğrudan çizilir.
@MainActor
enum TutorialScripts {

    static func groups(_ kind: CanvasKind) -> [TutorialGroup] {
        switch kind {
        // v40/v46/v48 her gelişmiş türde sürümün yenilikleri ayrı grupta
        case .ART: return [artBasics, artAdd, artAi, TutorialV40.art, TutorialV46.art, TutorialV48.advanced(kind), TutorialV50.advanced(kind)]
        case .NOTES: return [notesWrite, notesFormula, notesAi, TutorialV40.notes, TutorialV46.notes, TutorialV48.advanced(kind)]
        case .COMIC: return [comicPanels, comicText, comicAi, TutorialV40.comic, TutorialV46.comic, TutorialV48.advanced(kind), TutorialV50.advanced(kind)]
        // v40 basit resim ve basit notta kısa, sade dilli tanıtım
        case .SIMPLE_ART: return [TutorialV40.simpleArt, TutorialV46.simpleArt, TutorialV48.simple(kind), TutorialV50.simpleArt]
        case .SIMPLE_NOTES: return [TutorialV40.simpleNotes, TutorialV46.simpleNotes, TutorialV48.simple(kind)]
        // v37 çocuklar için çizimde maskot Boyacık araçları tanıtır ve örnek resim yapar
        case .KIDS: return [TutorialKids.intro, TutorialKids.whatsNew, TutorialV48.kids, TutorialV50.kids]
        }
    }

    /// v40 eğitimlerin sürümü: eski sürümün eğitimini bitirmiş kullanıcıya [whatsNew] bir kez gösterilir (v48).
    static let VERSION = 50

    /// Bu türde sürümün yenilikleri (v48: yeni menü sistemi).
    static func whatsNew(_ kind: CanvasKind) -> TutorialGroup? {
        switch kind {
        // v50 çizim modlarında "Resimden çizim" (vektörleştirme ve kalıpla çizme), not türlerinde v48 menüleri
        case .ART, .COMIC: return TutorialV50.advanced(kind)
        case .NOTES: return TutorialV48.advanced(kind)
        case .SIMPLE_ART: return TutorialV50.simpleArt
        case .SIMPLE_NOTES: return TutorialV48.simple(kind)
        case .KIDS: return TutorialV50.kids
        }
    }

    // MARK: - ortak

    static let INK = "#2B2F4A"
    static let PENCIL_GREY = "#37474F"
    static let NOTE_INK = "#1C1B19"
    static let BALLPOINT = "#1E3A8A"
    static let COMIC_INK = "#111111"

    /// Kontrol noktalarından geçen kırık çizginin x'teki yüksekliği (oran).
    nonisolated static func yAt(_ points: [(Float, Float)], _ x: Float) -> Float {
        for i in 0..<(points.count - 1) {
            let (x0, y0) = points[i], (x1, y1) = points[i + 1]
            if x >= x0 && x <= x1 { return y0 + (y1 - y0) * ((x - x0) / max(x1 - x0, 1e-4)) }
        }
        return points[points.count - 1].1
    }

    /// Çemberin iki yayı (v38 çizgi önerisi örneği: iki çizgi, uçları birleşir).
    nonisolated static func arcs(_ c: P, _ r: Float, seed: Int64) -> [[P]] {
        let full = TutorialArt.roughCircle(c, r, seed: seed, overshoot: 1)
        let half = full.count / 2
        return [Array(full[0...half]), Array(full[(half + 2)...])]
    }

    /// Dört ayrı kenar (köşelerde küçük boşluk ve taşmayla; v38 çizgi önerisi köşeleri birleştirir).
    nonisolated static func sides(_ l: Float, _ t: Float, _ r: Float, _ b: Float, _ gap: Float) -> [[P]] {
        let pairs: [(P, P)] = [
            (P(l + gap, t), P(r - gap * 0.4, t + gap * 0.3)),
            (P(r + gap * 0.2, t + gap), P(r, b - gap * 0.6)),
            (P(r - gap, b + gap * 0.2), P(l + gap * 0.5, b)),
            (P(l - gap * 0.3, b - gap), P(l, t + gap * 0.7)),
        ]
        return pairs.enumerated().map { i, ab in TutorialArt.roughPoly([ab.0, ab.1], seed: 70 + Int64(i), closed: false, amp: gap * 0.25) }
    }

    nonisolated static func triangle(_ c: P, _ hw: Float, _ hh: Float) -> [P] { [P(c.x, c.y - hh), P(c.x + hw, c.y + hh), P(c.x - hw, c.y + hh)] }

    /// Kuş: iki kanat yayı.
    nonisolated static func bird(_ c: P, _ s: Float) -> [P] {
        TutorialArt.smooth([P(c.x - s, c.y + 0.1 * s), P(c.x - 0.55 * s, c.y - 0.38 * s), P(c.x, c.y + 0.12 * s),
                            P(c.x + 0.55 * s, c.y - 0.38 * s), P(c.x + s, c.y + 0.1 * s)], samples: 8)
    }

    // MARK: - Çizim: araçlar (gün batımı manzarası)

    static let ridge: [(Float, Float)] = [(0, 0.60), (0.10, 0.50), (0.18, 0.54), (0.30, 0.38), (0.40, 0.50), (0.50, 0.44), (0.62, 0.30),
                                          (0.74, 0.46), (0.86, 0.40), (1, 0.50)]
    static let hillLine: [(Float, Float)] = [(0, 0.74), (0.18, 0.68), (0.36, 0.72), (0.55, 0.66), (0.75, 0.71), (1, 0.66)]

    static let artBasics = TutorialGroup(
        id: "art_basics", kind: .ART, title: "tut_art_basics", summary: "tut_art_basics_text", icon: "paintbrush.pointed", ai: false,
        steps: [
            TutorialStep(TutorialAnchor.BRUSH, "tut_art_b1", "tut_art_b1_text") { d in
                let line = d.u * 0.0065
                await d.stroke(TutorialArt.smooth(TutorialArt.wobble(d.path(ridge), amp: d.u * 0.0018, seed: 1)), .INK_PEN, INK, line)
                await d.stroke(TutorialArt.smooth(d.path([(0.26, 0.43), (0.29, 0.405), (0.31, 0.44), (0.345, 0.41)])), .INK_PEN, INK, line * 0.7, speed: 0.8)
                await d.stroke(TutorialArt.smooth(d.path([(0.58, 0.355), (0.61, 0.335), (0.63, 0.37), (0.66, 0.345)])), .INK_PEN, INK, line * 0.7, speed: 0.8)
                await d.stroke(TutorialArt.smooth(TutorialArt.wobble(d.path(hillLine), amp: d.u * 0.0015, seed: 2)), .INK_PEN, INK, line)
            },
            TutorialStep(TutorialAnchor.BRUSH_SETTINGS, "tut_art_b2", "tut_art_b2_text") { d in
                // Geniş, yarı saydam marker: dağlar ve tepeler açıktan koyuya katman katman
                let mountain = ["#A595DA", "#9483CF", "#8371C4", "#715FB6", "#624FA6", "#544394", "#473881", "#3B2F6E", "#30275D"]
                for (i, color) in mountain.enumerated() {
                    let dd = 0.02 + Float(i) * 0.034
                    let pts: [(Float, Float)] = (0...24).map { k in let x = Float(k) / 24; return (x, min(yAt(ridge, x) + dd, yAt(hillLine, x) - 0.004)) }
                    await d.stroke(TutorialArt.smooth(d.path(pts), samples: 4), .MARKER, color, d.u * 0.056, opacity: 0.5, speed: 2.6, taper: false)
                }
                let hills = ["#8FD49A", "#78C487", "#62B275", "#4FA165", "#418F58", "#357D4C", "#2B6B41", "#235B38", "#1C4C2F", "#173F28"]
                for (i, color) in hills.enumerated() {
                    let dd = 0.018 + Float(i) * 0.036
                    let pts: [(Float, Float)] = (0...24).map { k in let x = Float(k) / 24; return (x, min(yAt(hillLine, x) + dd, 0.985)) }
                    await d.stroke(TutorialArt.smooth(d.path(pts), samples: 4), .MARKER, color, d.u * 0.07, opacity: 0.58, speed: 2.6, taper: false)
                }
            },
            TutorialStep(TutorialAnchor.FILL, "tut_art_b3", "tut40_art_b3_text") { d in
                await d.tap(d.p(0.5, 0.14))
                d.host.setPaperGradient(GradientSpec(colors: ["#2E2A6B", "#C2457A", "#FFB066"], angle: 90))
                await d.pause(500)
            },
            TutorialStep(TutorialAnchor.ERASER, "tut_art_b4", "tut_art_b4_text") { d in
                let river = TutorialArt.smooth(d.path([(0.54, 0.665), (0.5, 0.74), (0.56, 0.82), (0.47, 0.91), (0.51, 1)]), samples: 10)
                await d.stroke(river, .ERASER, "#000000", d.u * 0.032, speed: 0.8, taper: false)
            },
            TutorialStep(TutorialAnchor.PEN, "tut_art_b5", "tut_art_b5_text") { d in
                await d.stroke(bird(d.p(0.2, 0.2), d.u * 0.035), .INK_PEN, INK, d.u * 0.005, speed: 0.6)
                await d.stroke(bird(d.p(0.29, 0.15), d.u * 0.026), .INK_PEN, INK, d.u * 0.0045, speed: 0.6)
                await d.stroke(bird(d.p(0.35, 0.23), d.u * 0.02), .INK_PEN, INK, d.u * 0.004, speed: 0.6)
            },
            TutorialStep(TutorialAnchor.UNDO, "tut_art_b6", "tut_art_b6_text") { d in
                await d.undoRedo()
            },
            TutorialStep(TutorialAnchor.LAYERS, "tut_art_b7", "tut_art_b7_text") { d in
                await d.tapAnchor(TutorialAnchor.LAYERS)
            },
        ])

    // MARK: - Çizim: şekil, yazı, düzenleme (vektör manzara)

    static let artAdd = TutorialGroup(
        id: "art_add", kind: .ART, title: "tut_art_add", summary: "tut_art_add_text", icon: "square.on.circle", ai: false,
        steps: [
            TutorialStep(TutorialAnchor.mode(.ADD), "tut_art_a1", "tut_art_a1_text", mode: .ADD) { d in
                await d.tapAnchor(TutorialAnchor.mode(.ADD))
            },
            TutorialStep(TutorialAnchor.add("shape"), "tut_art_a2", "tut_art_a2_text", mode: .ADD) { d in
                d.host.setPaperGradient(GradientSpec(colors: ["#4B3A8C", "#C8578E", "#FF9F7A", "#FFD39A"], angle: 90))
                await d.tapAnchor(TutorialAnchor.add("shape"))
                let w = d.u * 0.003
                await d.drag(d.p(0.38, 0.22), d.p(0.62, 0.46)) { a, b in
                    tutList(TutorialArt.shape("CIRCLE", a, b, tutShapeStyle("#FFB74D", w, fill: "#FFB74D",
                                                                           gradient: GradientSpec(colors: ["#FFF3B0", "#FFB74D", "#FF7043"]))))
                }
                // Dağlar: gradyanlı üçgenler ve tepelerinde aynı açıyla kar
                let back = GradientSpec(colors: ["#A897DD", "#6A58AE", "#3E3275"])
                let front = GradientSpec(colors: ["#8B78CC", "#4F3F93", "#2B2358"])
                let peaks: [(P, P, GradientSpec)] = [(P(-0.05, 0.36), P(0.55, 0.78), back), (P(0.30, 0.27), P(0.98, 0.78), front),
                                                     (P(0.70, 0.42), P(1.12, 0.78), back)]
                for (tl, br, g) in peaks {
                    await d.drag(d.p(tl.x, tl.y), d.p(br.x, br.y), durationMs: 420) { a, b2 in
                        tutList(TutorialArt.shape("TRIANGLE", a, b2, tutShapeStyle("#3A2F6B", w, fill: g.colors[1], gradient: g)))
                    }
                    let cx = (tl.x + br.x) / 2, hw = (br.x - tl.x) / 2, hh = br.y - tl.y
                    await d.place(nil, tutList(TutorialArt.shape("TRIANGLE", d.p(cx - hw * 0.24, tl.y), d.p(cx + hw * 0.24, tl.y + hh * 0.24),
                                                                 tutShapeStyle("#FFFFFF", w * 0.5, fill: "#F7F4FF"))))
                }
                // Tepeler: gradyanlı elipsler
                await d.drag(d.p(-0.25, 0.66), d.p(0.75, 1.3), durationMs: 450) { a, b2 in
                    tutList(TutorialArt.shape("ELLIPSE", a, b2, tutShapeStyle("#2E6B45", w, fill: "#5FAE6E",
                                                                             gradient: GradientSpec(colors: ["#9BDBA0", "#4E9E63", "#2A6A43"]))))
                }
                await d.drag(d.p(0.25, 0.7), d.p(1.3, 1.32), durationMs: 450) { a, b2 in
                    tutList(TutorialArt.shape("ELLIPSE", a, b2, tutShapeStyle("#24563A", w, fill: "#3E8E5A",
                                                                             gradient: GradientSpec(colors: ["#7CC98A", "#3E8E5A", "#1F4D33"]))))
                }
                await d.drag(d.p(0.06, 0.10), d.p(0.32, 0.22), durationMs: 450) { a, b in
                    tutList(TutorialArt.shape("CLOUD", a, b, tutShapeStyle("#FFFFFF", w, fill: "#FFFFFF", fillOpacity: 0.85)))
                }
                await d.drag(d.p(0.66, 0.14), d.p(0.86, 0.23), durationMs: 450) { a, b in
                    tutList(TutorialArt.shape("CLOUD", a, b, tutShapeStyle("#FFFFFF", w, fill: "#FFFFFF", fillOpacity: 0.7)))
                }
                await d.drag(d.p(0.10, 0.70), d.p(0.19, 0.92), durationMs: 380) { a, b in
                    tutList(TutorialArt.shape("PINE", a, b, tutShapeStyle("#16382A", w, fill: "#1F4D3A")))
                }
                await d.drag(d.p(0.80, 0.68), d.p(0.90, 0.90), durationMs: 380) { a, b in
                    tutList(TutorialArt.shape("PINE", a, b, tutShapeStyle("#16382A", w, fill: "#1F4D3A")))
                }
                await d.drag(d.p(0.42, 0.16), d.p(0.50, 0.20), durationMs: 300) { a, b in
                    tutList(TutorialArt.shape("BIRD", a, b, tutShapeStyle(INK, w * 1.4)))
                }
            },
            TutorialStep(TutorialAnchor.add("text"), "tut_art_a3", "tut_art_a3_text", mode: .ADD) { d in
                await d.tapAnchor(TutorialAnchor.add("text"))
                let size = d.u * 0.075
                await d.place(d.p(0.08, 0.05), [TutorialArt.text("Gün Batımı", d.w * 0.06, d.h * 0.035, size, "#FFFFFF", font: .PLAYFAIR, bold: true)])
                await d.pause(300)
            },
            TutorialStep(TutorialAnchor.mode(.SELECT), "tut_art_a4", "tut_art_a4_text", mode: .SELECT) { d in
                guard let sun = d.firstShape("CIRCLE") else { return }
                await d.tap(d.p(0.5, 0.34))
                d.host.select([sun.id])
                await d.pause(600)
            },
            TutorialStep(TutorialAnchor.mode(.EDIT_PICK), "tut_art_a5", "tut40_art_a5_text", mode: .MOVE) { d in
                guard let sun = d.firstShape("CIRCLE") else { return }
                d.host.select([sun.id])
                let total = d.h * 0.14
                let n = d.fast ? 1 : 24
                for _ in 0..<n { d.host.moveSelected(0, total / Float(n)); await d.pause(30) }
                await d.pause(300)
            },
            TutorialStep(TutorialAnchor.mode(.EDIT_PICK), "tut_art_a6", "tut_art_a6_text", mode: .EDIT_PICK) { d in
                guard let sun = d.firstShape("CIRCLE") else { return }
                d.host.select([sun.id])
                let n = d.fast ? 1 : 16
                let step = Float(pow(1.3, 1.0 / Double(n)))
                for _ in 0..<n { d.host.scaleSelected(step); await d.pause(35) }
                await d.pause(300)
            },
        ])

    // MARK: - Çizim: yapay zekâ

    static let artAi = TutorialGroup(
        id: "art_ai", kind: .ART, title: "tut_art_ai", summary: "tut40_art_ai_text", icon: "sparkles", ai: true,
        steps: [
            TutorialStep(TutorialAnchor.MORE, "tut_ai_more", "tut_ai_more_text_v38", ai: true) { d in
                await d.tapAnchor(TutorialAnchor.MORE)
            },
            TutorialStep(TutorialAnchor.CANVAS, "tut_art_ai2_v38", "tut40_art_ai2_text", ai: true) { d in
                await d.aiStrokes(arcs(d.p(0.78, 0.2), d.u * 0.085, seed: 7), .PENCIL, PENCIL_GREY, d.u * 0.005)
            },
            TutorialStep(TutorialAnchor.CANVAS, "tut_art_ai3", "tut_art_ai3_text_v38", ai: true) { d in
                await d.aiTemplate("yapi.ev.besik", d.p(0.3, 0.58), d.u * 0.32, .PENCIL, PENCIL_GREY, d.u * 0.005, seed: 21)
            },
            TutorialStep(TutorialAnchor.CANVAS, "tut_lines_v38", "tut_art_ai4_text_v38", ai: true) { d in
                let hw = d.w * 0.055, hh = d.h * 0.075
                let trees = [d.p(0.70, 0.76), d.p(0.80, 0.74), d.p(0.90, 0.77)].enumerated().map { i, c in
                    TutorialArt.roughPoly(triangle(c, hw, hh), seed: 40 + Int64(i), amp: d.u * 0.004)
                }
                await d.aiStrokes(trees, .PENCIL, "#2E5E3E", d.u * 0.005)
            },
            // v39 tek ama büyük çizgiye de öneri gelir
            TutorialStep(TutorialAnchor.CANVAS, "tut40_single", "tut40_single_text", ai: true) { d in
                await d.aiSingle(d.p(0.5, 0.24), d.u * 0.15, .PENCIL, "#E07A1F", d.u * 0.005, seed: 31)
            },
            TutorialStep(TutorialAnchor.mode(.SELECT), "tut_ai_select", "tut_ai_select_text_v38", ai: true),
        ])

    // MARK: - Not alma: yazı ve kalemler (fizik notu)

    static let notesWrite = TutorialGroup(
        id: "notes_write", kind: .NOTES, title: "tut_notes_write", summary: "tut_notes_write_text", icon: "square.and.pencil", ai: false,
        steps: [
            TutorialStep(TutorialAnchor.add("text"), "tut_notes_w1", "tut_notes_w1_text", mode: .ADD) { d in
                await d.tapAnchor(TutorialAnchor.add("text"))
                let size = d.u * 0.06
                let title = "Newton'un Hareket Yasaları"
                await d.place(d.p(0.1, 0.07), [TutorialArt.text(title, d.w * 0.07, d.h * 0.05, size, NOTE_INK, font: .MERRIWEATHER, bold: true)])
                let (_, th) = TutorialArt.measure(title, size, font: .MERRIWEATHER, bold: true)
                await d.pause(250)
                await d.place(nil, [TutorialArt.text("Fizik · Kuvvet ve hareket", d.w * 0.07, d.h * 0.05 + th + d.u * 0.015, d.u * 0.032, "#6E6A63",
                                                     font: .LORA, italic: true)])
                await d.pause(300)
            },
            TutorialStep(TutorialAnchor.BRUSH, "tut_notes_w2", "tut_notes_w2_text") { d in
                let top = d.h * 0.05 + d.u * 0.16
                let word = TutorialArt.handwriting("kütle", seed: 5, box: FRect(d.w * 0.08, top, d.w * 0.6, top + d.u * 0.075))
                for s in word { await d.stroke(s, .NOTE_BALLPOINT, BALLPOINT, d.u * 0.0045, speed: 1.6) }
                let flat = word.flatMap { $0 }
                if let ys = flat.map(\.y).max(), let x0 = flat.map(\.x).min(), let x1 = flat.map(\.x).max() {
                    await d.stroke(TutorialArt.wobble([P(x0, ys + d.u * 0.012), P(x1, ys + d.u * 0.016)], amp: d.u * 0.001, seed: 3),
                                   .NOTE_BALLPOINT, BALLPOINT, d.u * 0.004)
                }
                let word2 = TutorialArt.handwriting("ivme", seed: 2, box: FRect(d.w * 0.08, top + d.u * 0.11, d.w * 0.6, top + d.u * 0.11 + d.u * 0.075))
                for s in word2 { await d.stroke(s, .NOTE_BALLPOINT, BALLPOINT, d.u * 0.0045, speed: 1.6) }
            },
            TutorialStep(TutorialAnchor.BRUSH, "tut_notes_w3", "tut_notes_w3_text") { d in
                let size = d.u * 0.06
                let (tw, th) = TutorialArt.measure("Newton'un Hareket Yasaları", size, font: .MERRIWEATHER, bold: true)
                let y = d.h * 0.05 + th * 0.62
                await d.stroke([P(d.w * 0.06, y), P(d.w * 0.07 + tw + d.u * 0.01, y + d.u * 0.002)], .NOTE_HIGHLIGHTER, "#FFE066", d.u * 0.05,
                               opacity: 0.55, speed: 0.9, taper: false)
            },
            TutorialStep(TutorialAnchor.PAGES, "tut_notes_w4", "tut40_notes_w4_text") { d in
                await d.tapAnchor(TutorialAnchor.PAGES)
            },
        ])

    static let notesFormula = TutorialGroup(
        id: "notes_formula", kind: .NOTES, title: "tut_notes_formula", summary: "tut_notes_formula_text", icon: "function", ai: false,
        steps: [
            TutorialStep(TutorialAnchor.mode(.ADD), "tut_notes_f1", "tut_notes_f1_text", mode: .ADD) { d in
                await d.tapAnchor(TutorialAnchor.mode(.ADD))
            },
            TutorialStep(TutorialAnchor.add("formula"), "tut_notes_f2", "tut_notes_f2_text", mode: .ADD) { d in
                await d.tapAnchor(TutorialAnchor.add("formula"))
                await d.place(d.p(0.3, 0.1), tutList(TutorialArt.formula("physics.newton2", d.w * 0.1, d.h * 0.06, d.u * 0.09, NOTE_INK)))
                await d.pause(350)
                await d.place(d.p(0.3, 0.2), tutList(TutorialArt.formula("physics.velocity", d.w * 0.1, d.h * 0.06 + d.u * 0.16, d.u * 0.07, "#2F6BD8")))
                await d.pause(300)
            },
            TutorialStep(TutorialAnchor.add("diagram"), "tut_notes_f3", "tut_notes_f3_text", mode: .ADD) { d in
                await d.tapAnchor(TutorialAnchor.add("diagram"))
                let top = d.h * 0.06 + d.u * 0.34
                let rect = FRect(d.w * 0.08, top, d.w * 0.92, top + min(d.h * 0.42, d.u * 0.75))
                let style = tutShapeStyle(NOTE_INK, d.u * 0.003)
                await d.drag(rect.topLeft, rect.bottomRight, durationMs: 700) { a, b in
                    [TutorialArt.diagram(.FLOW, FRect(a.x, a.y, b.x, b.y), style, DiagramThemes.EDITORIAL,
                                         texts: ["Kuvvet uygulanır", "İvme oluşur", "Hız değişir", "Cisim hareket eder", "Sonuç"])]
                }
            },
            TutorialStep(TutorialAnchor.add("shape"), "tut_notes_f4", "tut_notes_f4_text", mode: .ADD) { d in
                await d.tapAnchor(TutorialAnchor.add("shape"))
                // Hız denkleminin altından diyagramın ilk adımına
                let strokes = d.strokes
                let formulaBottom = strokes.filter { $0.brushType == .FORMULA }.flatMap(\.points).map(\.y).max() ?? d.h * 0.25
                let diagram = strokes.first { $0.brushType == .DIAGRAM }
                let dTop = diagram?.points.map(\.y).min() ?? formulaBottom + d.u * 0.12
                let dCx = diagram.map { s in ((s.points.map(\.x).min() ?? 0) + (s.points.map(\.x).max() ?? 0)) / 2 } ?? d.w * 0.5
                let from = P(d.w * 0.24, formulaBottom + d.u * 0.02)
                let to = P(dCx - d.u * 0.13, dTop + d.u * 0.035)
                let w = d.u * 0.006
                await d.drag(from, to, durationMs: 600) { a, b in tutList(TutorialArt.shape("ARROW", a, b, tutShapeStyle("#D9572B", w))) }
            },
        ])

    static let notesAi = TutorialGroup(
        id: "notes_ai", kind: .NOTES, title: "tut_notes_ai", summary: "tut40_notes_ai_text", icon: "sparkles", ai: true,
        steps: [
            TutorialStep(TutorialAnchor.CANVAS, "tut_notes_ai1", "tut_notes_ai1_text", ai: true) { d in
                let box = FRect(d.w * 0.1, d.h * 0.2, d.w * 0.7, d.h * 0.2 + d.u * 0.085)
                var ids = Set<String>()
                for s in TutorialArt.handwriting("kuvvet", seed: 3, box: box) {
                    let made = await d.stroke(s, .NOTE_BALLPOINT, BALLPOINT, d.u * 0.0045, suggest: true, speed: 1.8)
                    ids.insert(made.id)
                }
                await d.aiText("tut_hint_text") {
                    let t = TutorialArt.text("kuvvet", box.left, box.top, box.height, BALLPOINT)
                    d.host.mutate { list in list.filter { !ids.contains($0.id) } + [t] }
                }
            },
            TutorialStep(TutorialAnchor.CANVAS, "tut_lines_v38", "tut_notes_ai2_text_v38", ai: true) { d in
                let l = d.w * 0.1, t = d.h * 0.2 + d.u * 0.2, r = d.w * 0.62, b = t + d.u * 0.16
                await d.aiStrokes(sides(l, t, r, b, d.u * 0.012), .NOTE_FELT, NOTE_INK, d.u * 0.005)
            },
            TutorialStep(TutorialAnchor.CANVAS, "tut_lines_v38", "tut_notes_ai3_text_v38", ai: true) { d in
                let y = d.h * 0.2 + d.u * 0.56
                let r = d.u * 0.07
                let rings = [d.w * 0.26, d.w * 0.26 + r * 1.7, d.w * 0.26 + r * 3.4].enumerated().map { i, x in
                    TutorialArt.roughCircle(P(x, y), r, seed: 50 + Int64(i))
                }
                await d.aiStrokes(rings, .NOTE_FELT, "#2F6BD8", d.u * 0.005)
            },
            TutorialStep(TutorialAnchor.CANVAS, "tut40_single", "tut40_single_text", ai: true) { d in
                await d.aiSingle(P(d.w * 0.5, d.h * 0.82), d.u * 0.16, .NOTE_FELT, "#C2410C", d.u * 0.005, seed: 32)
            },
            TutorialStep(TutorialAnchor.MORE, "tut_ai_more", "tut_ai_more_text_v38", ai: true) { d in
                await d.tapAnchor(TutorialAnchor.MORE)
            },
        ])

    // MARK: - Çizgi roman

    static let comicPanels = TutorialGroup(
        id: "comic_panels", kind: .COMIC, title: "tut_comic_panels", summary: "tut_comic_panels_text", icon: "rectangle.split.3x3", ai: false,
        steps: [
            TutorialStep(TutorialAnchor.mode(.ADD), "tut_comic_p1", "tut40_comic_p1_text", mode: .ADD) { d in
                await d.tapAnchor(TutorialAnchor.mode(.ADD))
            },
            // v37 panel düzenleri Şablonlar'da
            TutorialStep(TutorialAnchor.add("templates"), "tut_comic_p2", "tut_comic_p2_text", mode: .ADD) { d in
                await d.tapAnchor(TutorialAnchor.add("templates"))
                for s in d.panels("big_top") { await d.place(nil, [s]); await d.pause(260) }
            },
            TutorialStep(TutorialAnchor.BRUSH, "tut_comic_p3", "tut_comic_p3_text") { d in
                let top = d.panelRects("big_top").first ?? FRect(0, 0, d.w, d.h * 0.5)
                await d.hero(P(top.center.x, top.center.y + top.height * 0.04), top.height * 0.26)
            },
            TutorialStep(TutorialAnchor.add("effects"), "tut_comic_p4", "tut_comic_p4_text", mode: .ADD) { d in
                await d.tapAnchor(TutorialAnchor.add("effects"))
                guard let top = d.panelRects("big_top").first else { return }
                let inner = top.deflate(d.u * 0.012)
                await d.place(nil, d.host.comicLines(.FOCUS, density: 0.7, area: inner, ink: COMIC_INK))
                await d.pause(300)
            },
        ])

    static let comicText = TutorialGroup(
        id: "comic_text", kind: .COMIC, title: "tut_comic_text", summary: "tut_comic_text_text", icon: "bubble.left", ai: false,
        steps: [
            TutorialStep(TutorialAnchor.add("balloon"), "tut_comic_t1", "tut_comic_t1_text", mode: .ADD) { d in
                // Sahne: paneller ve kahraman hazır gelir
                if d.strokes.isEmpty {
                    d.host.commitElements(d.panels("big_top"))
                    if let top = d.panelRects("big_top").first {
                        let wasFast = d.fast
                        await d.hero(P(top.center.x - top.width * 0.12, top.center.y + top.height * 0.04), top.height * 0.24)
                        if !wasFast { await d.pause(200) }
                    }
                }
                await d.tapAnchor(TutorialAnchor.add("balloon"))
                let top = d.panelRects("big_top").first ?? FRect(0, 0, d.w, d.h * 0.5)
                let at = P(top.left + top.width * 0.74, top.top + top.height * 0.24)
                await d.place(at, tutList(d.host.balloon(.SPEECH, speakers: 1, center: at, text: "Bu şehir güvende!")))
                await d.pause(300)
            },
            TutorialStep(TutorialAnchor.add("effects"), "tut_comic_t2", "tut_comic_t2_text", mode: .ADD) { d in
                await d.tapAnchor(TutorialAnchor.add("effects"))
                let top = d.panelRects("big_top").first ?? FRect(0, 0, d.w, d.h * 0.5)
                let at = P(top.left + top.width * 0.78, top.top + top.height * 0.72)
                let preset = ComicSfx.presets.first { $0.text == "POW!" } ?? ComicSfx.presets[0]
                await d.place(at, tutList(d.host.sfx(preset, center: at, size: d.u * 0.11)))
                await d.pause(300)
            },
            TutorialStep(TutorialAnchor.add("balloon"), "tut_comic_t3", "tut_comic_t3_text", mode: .ADD) { d in
                await d.tapAnchor(TutorialAnchor.add("balloon"))
                let rects = d.panelRects("big_top")
                let second = rects.count > 1 ? rects[1] : FRect(0, d.h * 0.6, d.w * 0.5, d.h)
                let at = P(second.center.x, second.top + second.height * 0.2)
                await d.place(at, tutList(d.host.balloon(.NARRATION, speakers: 0, center: at, text: "Bir gece, şehrin üstünde…")))
                await d.pause(300)
            },
        ])

    static let comicAi = TutorialGroup(
        id: "comic_ai", kind: .COMIC, title: "tut_comic_ai", summary: "tut40_comic_ai_text", icon: "sparkles", ai: true,
        steps: [
            TutorialStep(TutorialAnchor.CANVAS, "tut_comic_ai1", "tut_comic_ai1_text", ai: true) { d in
                await d.aiTemplate("roman.konusma", d.p(0.5, 0.2), d.u * 0.36, .INK_PEN, COMIC_INK, d.u * 0.005, seed: 4)
            },
            TutorialStep(TutorialAnchor.CANVAS, "tut_comic_ai2", "tut_comic_ai2_text", ai: true) { d in
                await d.aiTemplate("yuz.ifade.gulen", d.p(0.3, 0.55), d.u * 0.3, .INK_PEN, COMIC_INK, d.u * 0.005, seed: 5)
            },
            TutorialStep(TutorialAnchor.CANVAS, "tut_lines_v38", "tut_comic_ai3_text_v38", ai: true) { d in
                let r = d.u * 0.05
                let y = d.h * 0.55
                let bubbles = [d.w * 0.64, d.w * 0.64 + r * 1.7, d.w * 0.64 + r * 3.4].enumerated().map { i, x in
                    TutorialArt.roughCircle(P(x, y - Float(i) * r * 0.6), r * (1 - Float(i) * 0.12), seed: 60 + Int64(i))
                }
                await d.aiStrokes(bubbles, .INK_PEN, "#29B6F6", d.u * 0.005)
            },
            TutorialStep(TutorialAnchor.CANVAS, "tut40_single", "tut40_single_text", ai: true) { d in
                await d.aiSingle(P(d.w * 0.5, d.h * 0.84), d.u * 0.14, .INK_PEN, COMIC_INK, d.u * 0.005, seed: 33)
            },
            TutorialStep(TutorialAnchor.MORE, "tut_ai_more", "tut_ai_more_text_v38", ai: true) { d in
                await d.tapAnchor(TutorialAnchor.MORE)
            },
        ])
}

/// Boş olmayan elemanı tek elemanlı listeye çevirir (Kotlin `listOfNotNull`).
func tutList(_ s: Stroke?) -> [Stroke] { s.map { [$0] } ?? [] }

// MARK: - Senaryoların ortak gösterimleri

extension TutorialDemo {
    var strokes: [Stroke] { host.tutorialCanvas()?.strokes ?? [] }
    func firstShape(_ kind: String) -> Stroke? { strokes.first { $0.shape?.kind == kind } }
    func firstOf(_ type: BrushType) -> Stroke? { strokes.first { $0.brushType == type } }
    func path(_ points: [(Float, Float)]) -> [P] { points.map { p($0.0, $0.1) } }

    /// Geri al ve yinele düğmelerine dokunulur.
    func undoRedo() async {
        await tapAnchor(TutorialAnchor.UNDO)
        host.undo()
        await pause(700)
        await tapAnchor(TutorialAnchor.REDO)
        host.redo()
        await pause(400)
    }

    /// Çizimin düzgün geometrisi (öneri gelmediyse).
    func fixStroke(_ s: Stroke) {
        guard let f = host.fit(s) else { return }
        host.mutate { list in list.map { var x = $0; if x.id == s.id { x.points = f.points }; return x } }
    }

    /// v38 birden çok çizgi: çizgi önerisi (her çizgi düzgün haline, yakın uçlar birleşir; tek düğmeyle hepsi). Öneri
    /// gelmezse her çizgi kendi düzgün geometrisine.
    func aiStrokes(_ paths: [[P]], _ brush: BrushType, _ color: String, _ width: Float, speed: Float = 1.8) async {
        var drawn: [Stroke] = []
        for s in paths { let made = await stroke(s, brush, color, width, suggest: true, speed: speed); drawn.append(made) }
        let ids = Set(drawn.map(\.id))
        await aiLines("tut_hint_lines", accept: { sug in sug.strokeIds.count >= 2 && sug.strokeIds.allSatisfy { ids.contains($0) } },
                      fallback: { for s in drawn { fixStroke(s) } })
    }

    /// v39 tek ama büyük çizgi (elle çizilmiş çember): tek çizgi önerisi; gelmezse çizgi kendi düzgün geometrisine.
    func aiSingle(_ c: P, _ r: Float, _ brush: BrushType, _ color: String, _ width: Float, seed: Int64) async {
        let drawn = await stroke(TutorialArt.roughCircle(c, r, seed: seed), brush, color, width, suggest: true, speed: 1.4)
        await aiLines("tut40_hint_single", accept: { $0.strokeIds.contains(drawn.id) }, fallback: { fixStroke(drawn) })
    }

    /// Birkaç çizgiyle çizilen nesne: şablon önerisi ([key]); gelmezse şablonun düzgün çizgileri konur.
    func aiTemplate(_ key: String, _ center: P, _ size: Float, _ brush: BrushType, _ color: String, _ width: Float, seed: Int64) async {
        var drawn: [Stroke] = []
        for s in TutorialArt.handDrawnTemplate(key, center, size, seed: seed) {
            let made = await stroke(s, brush, color, width, suggest: true, speed: 1.6)
            drawn.append(made)
        }
        guard let base = drawn.first else { return }
        let ids = Set(drawn.map(\.id))
        let host = self.host
        await aiShape("tut_hint_template",
                      accept: { sug in sug.templates.contains { $0.key == key } },
                      apply: { sug in host.acceptTemplate(sug.templates.firstIndex { $0.key == key } ?? 0) },
                      fallback: {
                          let clean = TutorialArt.cleanTemplate(key, center, size)
                          let group = UUID().uuidString
                          let made: [Stroke] = clean.map { pts in
                              var s = base
                              s.id = UUID().uuidString; s.points = pts.map { StrokePoint($0.x, $0.y) }; s.groupId = group
                              return s
                          }
                          host.mutate { list in list.filter { !ids.contains($0.id) } + made }
                      })
    }

    /// Panel düzeninin kareleri (tuval koordinatı).
    func panelRects(_ layoutId: String) -> [FRect] {
        guard let layout = ComicPanels.byId(layoutId) else { return [] }
        return ComicPanels.rects(layout, w, h).map { FRect($0[0], $0[1], $0[2], $0[3]) }
    }

    func panels(_ layoutId: String) -> [Stroke] {
        let line = min(max(u * 0.006, 2), 14)
        return panelRects(layoutId).compactMap { r in
            TutorialArt.shape("RECTANGLE", r.topLeft, r.bottomRight, tutShapeStyle(TutorialScripts.COMIC_INK, line, fill: "#FFFFFF"))
        }
    }

    /// Kahraman (pelerinli, yumruğu havada) — mürekkep kalemiyle.
    func hero(_ c: P, _ s: Float) async {
        let ink = TutorialScripts.COMIC_INK
        let wl = u * 0.006
        func o(_ x: Float, _ y: Float) -> P { P(c.x + x * s, c.y + y * s) }
        func circle(_ cx: Float, _ cy: Float, _ rx: Float, _ ry: Float, _ n: Int = 40) -> [P] {
            (0...n).map { i in
                let a = Float(i) / Float(n) * 2 * Float.pi - Float.pi / 2
                return o(cx + rx * cosf(a), cy + ry * sinf(a))
            }
        }
        // Pelerin (arkada)
        await stroke(TutorialArt.smooth([o(-0.35, -0.25), o(-0.75, 0.55), o(-0.95, 1.25), o(-0.3, 1.05), o(0.25, 1.2), o(0.75, 1.1),
                                         o(0.42, 0.4), o(0.35, -0.25)]), .INK_PEN, "#C62828", wl * 1.1)
        // Baş, saç, gözler, ağız
        await stroke(circle(0, -0.75, 0.42, 0.46), .INK_PEN, ink, wl)
        await stroke(TutorialArt.smooth([o(-0.43, -0.82), o(-0.4, -1.1), o(-0.26, -1.0), o(-0.17, -1.24), o(-0.05, -1.04), o(0.06, -1.28),
                                         o(0.17, -1.05), o(0.3, -1.2), o(0.34, -1.0), o(0.45, -1.06), o(0.43, -0.8)], samples: 5),
                     .INK_PEN, ink, wl, speed: 0.9)
        await stroke(circle(-0.16, -0.78, 0.07, 0.1, 16), .INK_PEN, ink, wl * 0.9, speed: 0.6)
        await stroke(circle(0.16, -0.78, 0.07, 0.1, 16), .INK_PEN, ink, wl * 0.9, speed: 0.6)
        await stroke(TutorialArt.smooth([o(-0.18, -0.55), o(0, -0.45), o(0.18, -0.55)], samples: 8), .INK_PEN, ink, wl, speed: 0.6)
        // Gövde ve göğüs amblemi
        await stroke([o(-0.3, -0.3), o(-0.34, 0.55), o(0.34, 0.55), o(0.3, -0.3), o(-0.3, -0.3)], .INK_PEN, ink, wl)
        await stroke([o(0, -0.12), o(0.13, 0.08), o(0, 0.28), o(-0.13, 0.08), o(0, -0.12)], .INK_PEN, "#FBC02D", wl * 1.2, speed: 0.7)
        // Kollar: sağ yumruk havada, sol bel
        await stroke(TutorialArt.smooth([o(0.3, -0.25), o(0.55, -0.7), o(0.62, -1.1)], samples: 6), .INK_PEN, ink, wl)
        await stroke(circle(0.63, -1.2, 0.1, 0.1, 16), .INK_PEN, ink, wl, speed: 0.6)
        await stroke(TutorialArt.smooth([o(-0.3, -0.25), o(-0.58, 0.1), o(-0.32, 0.35)], samples: 6), .INK_PEN, ink, wl)
        // Bacaklar
        await stroke([o(-0.2, 0.55), o(-0.28, 1.25), o(-0.42, 1.3)], .INK_PEN, ink, wl)
        await stroke([o(0.2, 0.55), o(0.28, 1.25), o(0.42, 1.3)], .INK_PEN, ink, wl)
    }
}
