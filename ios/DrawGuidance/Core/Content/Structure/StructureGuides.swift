import Foundation

/// v53 canlı, nesne ve eşya yapı kılavuzları (Android `content.structure.StructureGuides`). Veri
/// `structure_guides.txt` dosyasından okunur (iki platformda aynı dosya). Birim: kutu yüksekliği 1, genişliği [aspect].
enum StructureGroup: String, CaseIterable { case LIVING, OBJECT, ITEM }

enum StructurePrim: Equatable {
    case measure(Int)
    case axis([Float])
    case line([Float])
    case poly([Float])
    case polygon([Float])
    case circle(Float, Float, Float)
    case ellipse(Float, Float, Float, Float, Float)
    case joint(Float, Float)
    case vanish(Float, Float)
}

struct StructureGuide: Identifiable, Equatable {
    let id: String
    let group: StructureGroup
    let nameKey: String
    let aspect: Float
    let prims: [StructurePrim]
}

enum StructureGuides {
    static let FILE = "structure_guides.txt"

    /// Kılavuzlar ilk kullanımda bir kez okunur (küçük metin dosyası).
    nonisolated(unsafe) private static var cached: [StructureGuide]?

    static var all: [StructureGuide] {
        if let c = cached { return c }
        let text = BundledResources.data(FILE).flatMap { String(data: $0, encoding: .utf8) } ?? ""
        let parsed = parse(text)
        cached = parsed
        return parsed
    }

    static func byGroup(_ g: StructureGroup) -> [StructureGuide] { all.filter { $0.group == g } }
    static func find(_ id: String) -> StructureGuide? { all.first { $0.id == id } }

    static func parse(_ text: String) -> [StructureGuide] {
        var out: [StructureGuide] = []
        var head: (String, StructureGroup, String, Float)?
        var prims: [StructurePrim] = []
        func flush() {
            if let h = head { out.append(StructureGuide(id: h.0, group: h.1, nameKey: h.2, aspect: h.3, prims: prims)) }
            head = nil; prims = []
        }
        for raw in text.split(separator: "\n", omittingEmptySubsequences: false) {
            let line = raw.trimmingCharacters(in: .whitespaces)
            if line.isEmpty || line.hasPrefix("#") { continue }
            let tag = line.prefix(1)
            let rest = line.dropFirst(1).trimmingCharacters(in: .whitespaces)
            if tag == "G" {
                flush()
                let f = rest.split(separator: "|").map(String.init)
                guard f.count == 4, let g = StructureGroup(rawValue: f[1]), let a = Float(f[3]) else { continue }
                head = (f[0], g, f[2], a)
                continue
            }
            let n = rest.split(separator: " ").compactMap { Float($0) }
            switch tag {
            case "M": if let k = n.first { prims.append(.measure(Int(k))) }
            case "A": if n.count >= 4 { prims.append(.axis(n)) }
            case "L": if n.count >= 4 { prims.append(.line(n)) }
            case "P": if n.count >= 4 { prims.append(.poly(n)) }
            case "Q": if n.count >= 6 { prims.append(.polygon(n)) }
            case "C": if n.count >= 3 { prims.append(.circle(n[0], n[1], n[2])) }
            case "E": if n.count >= 5 { prims.append(.ellipse(n[0], n[1], n[2], n[3], n[4])) }
            case "K": if n.count >= 2 { prims.append(.joint(n[0], n[1])) }
            case "V": if n.count >= 2 { prims.append(.vanish(n[0], n[1])) }
            default: break
            }
        }
        flush()
        return out
    }
}
