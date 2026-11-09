import Foundation

/// Bilinen molekül (Android `chem/KnownMolecules.kt`): Türkçe ad, İngilizce ad, takma adlar ve SMILES yapısı.
final class KnownMolecule {
    let name: String
    let en: String
    let smiles: String
    let aliases: [String]
    private let mol: Molecule
    let hill: String
    let signature: String

    init(_ name: String, _ en: String, _ smiles: String, _ aliases: [String] = []) {
        self.name = name; self.en = en; self.smiles = smiles; self.aliases = aliases
        mol = try! Smiles.parse(smiles)
        hill = mol.hill()
        signature = mol.signature()
    }

    func molecule() -> Molecule { mol.copy() }
}

enum KnownMolecules {
    static let all: [KnownMolecule] = [
        KnownMolecule("hidrojen", "hydrogen", "[H][H]", ["h2"]),
        KnownMolecule("oksijen", "oxygen", "O=O", ["o2"]),
        KnownMolecule("azot", "nitrogen", "N#N", ["nitrojen", "n2"]),
        KnownMolecule("klor", "chlorine", "ClCl", ["cl2"]),
        KnownMolecule("flor", "fluorine", "FF"),
        KnownMolecule("ozon", "ozone", "[O-][O+]=O"),
        KnownMolecule("su", "water", "O", ["h2o"]),
        KnownMolecule("hidrojen peroksit", "hydrogen peroxide", "OO", ["oksijenli su"]),
        KnownMolecule("karbon dioksit", "carbon dioxide", "O=C=O", ["karbondioksit"]),
        KnownMolecule("karbon monoksit", "carbon monoxide", "[C-]#[O+]", ["karbonmonoksit"]),
        KnownMolecule("amonyak", "ammonia", "N"),
        KnownMolecule("metan", "methane", "C"),
        KnownMolecule("hidrojen klorür", "hydrogen chloride", "Cl", ["hidroklorik asit", "tuz ruhu"]),
        KnownMolecule("hidrojen florür", "hydrogen fluoride", "F"),
        KnownMolecule("hidrojen sülfür", "hydrogen sulfide", "S"),
        KnownMolecule("hidrojen siyanür", "hydrogen cyanide", "C#N"),
        KnownMolecule("kükürt dioksit", "sulfur dioxide", "O=S=O"),
        KnownMolecule("kükürt trioksit", "sulfur trioxide", "O=S(=O)=O"),
        KnownMolecule("sülfürik asit", "sulfuric acid", "OS(=O)(=O)O", ["zaç yağı"]),
        KnownMolecule("nitrik asit", "nitric acid", "O[N+](=O)[O-]", ["kezzap"]),
        KnownMolecule("fosforik asit", "phosphoric acid", "OP(=O)(O)O"),
        KnownMolecule("karbonik asit", "carbonic acid", "OC(=O)O"),
        KnownMolecule("hidrazin", "hydrazine", "NN"),
        KnownMolecule("fosfin", "phosphine", "P"),
        KnownMolecule("silan", "silane", "[SiH4]"),
        KnownMolecule("bor triflorür", "boron trifluoride", "FB(F)F"),
        KnownMolecule("fosfor triklorür", "phosphorus trichloride", "ClP(Cl)Cl"),
        KnownMolecule("fosfor pentaklorür", "phosphorus pentachloride", "ClP(Cl)(Cl)(Cl)Cl"),
        KnownMolecule("karbon tetraklorür", "carbon tetrachloride", "ClC(Cl)(Cl)Cl"),
        KnownMolecule("kloroform", "chloroform", "ClC(Cl)Cl", ["triklorometan"]),
        KnownMolecule("diklorometan", "dichloromethane", "ClCCl"),
        KnownMolecule("klorometan", "chloromethane", "CCl", ["metil klorür"]),
        KnownMolecule("sodyum klorür", "sodium chloride", "[Na+].[Cl-]", ["yemek tuzu", "tuz"]),
        KnownMolecule("potasyum klorür", "potassium chloride", "[K+].[Cl-]"),
        KnownMolecule("sodyum hidroksit", "sodium hydroxide", "[Na+].[OH-]", ["sud kostik"]),
        KnownMolecule("kalsiyum klorür", "calcium chloride", "[Cl-].[Ca+2].[Cl-]"),
        KnownMolecule("magnezyum oksit", "magnesium oxide", "[Mg+2].[O-2]"),
        KnownMolecule("kalsiyum karbonat", "calcium carbonate", "[Ca+2].[O-]C(=O)[O-]", ["kireç taşı"]),
        KnownMolecule("sodyum bikarbonat", "sodium bicarbonate", "[Na+].OC(=O)[O-]", ["karbonat", "kabartma tozu"]),
        KnownMolecule("amonyum klorür", "ammonium chloride", "[NH4+].[Cl-]", ["nişadır"]),
        KnownMolecule("etan", "ethane", "CC"),
        KnownMolecule("propan", "propane", "CCC"),
        KnownMolecule("bütan", "butane", "CCCC", ["butan", "n-bütan"]),
        KnownMolecule("izobütan", "isobutane", "CC(C)C", ["izobutan", "2-metilpropan"]),
        KnownMolecule("pentan", "pentane", "CCCCC"),
        KnownMolecule("hekzan", "hexane", "CCCCCC", ["heksan"]),
        KnownMolecule("siklohekzan", "cyclohexane", "C1CCCCC1", ["sikloheksan"]),
        KnownMolecule("eten", "ethylene", "C=C", ["etilen", "ethene"]),
        KnownMolecule("propen", "propene", "C=CC", ["propilen"]),
        KnownMolecule("1-büten", "1-butene", "C=CCC"),
        KnownMolecule("2-büten", "2-butene", "CC=CC"),
        KnownMolecule("etin", "acetylene", "C#C", ["asetilen", "ethyne"]),
        KnownMolecule("propin", "propyne", "C#CC"),
        KnownMolecule("vinil klorür", "vinyl chloride", "C=CCl", ["kloroeten"]),
        KnownMolecule("metanol", "methanol", "CO", ["metil alkol", "odun alkolü"]),
        KnownMolecule("etanol", "ethanol", "CCO", ["etil alkol", "alkol"]),
        KnownMolecule("1-propanol", "1-propanol", "CCCO", ["propanol"]),
        KnownMolecule("2-propanol", "isopropanol", "CC(O)C", ["izopropil alkol"]),
        KnownMolecule("1-bütanol", "1-butanol", "CCCCO", ["bütanol"]),
        KnownMolecule("etilen glikol", "ethylene glycol", "OCCO", ["glikol", "1,2-etandiol"]),
        KnownMolecule("gliserol", "glycerol", "OCC(O)CO", ["gliserin"]),
        KnownMolecule("dimetil eter", "dimethyl ether", "COC"),
        KnownMolecule("dietil eter", "diethyl ether", "CCOCC", ["eter"]),
        KnownMolecule("formaldehit", "formaldehyde", "C=O", ["metanal"]),
        KnownMolecule("asetaldehit", "acetaldehyde", "CC=O", ["etanal"]),
        KnownMolecule("aseton", "acetone", "CC(=O)C", ["propanon"]),
        KnownMolecule("formik asit", "formic acid", "OC=O", ["metanoik asit", "karınca asidi"]),
        KnownMolecule("asetik asit", "acetic acid", "CC(=O)O", ["etanoik asit", "sirke asidi"]),
        KnownMolecule("propanoik asit", "propanoic acid", "CCC(=O)O", ["propiyonik asit"]),
        KnownMolecule("oksalik asit", "oxalic acid", "OC(=O)C(=O)O"),
        KnownMolecule("laktik asit", "lactic acid", "CC(O)C(=O)O", ["süt asidi"]),
        KnownMolecule("metil asetat", "methyl acetate", "CC(=O)OC"),
        KnownMolecule("etil asetat", "ethyl acetate", "CC(=O)OCC"),
        KnownMolecule("metilamin", "methylamine", "CN", ["metil amin"]),
        KnownMolecule("etilamin", "ethylamine", "CCN"),
        KnownMolecule("üre", "urea", "NC(N)=O", ["ure", "karbamit"]),
        KnownMolecule("asetamit", "acetamide", "CC(N)=O"),
        KnownMolecule("asetonitril", "acetonitrile", "CC#N"),
        KnownMolecule("glisin", "glycine", "NCC(=O)O"),
        KnownMolecule("alanin", "alanine", "CC(N)C(=O)O"),
        KnownMolecule("benzen", "benzene", "C1=CC=CC=C1"),
        KnownMolecule("toluen", "toluene", "CC1=CC=CC=C1", ["metilbenzen"]),
        KnownMolecule("fenol", "phenol", "OC1=CC=CC=C1", ["hidroksibenzen"]),
        KnownMolecule("anilin", "aniline", "NC1=CC=CC=C1", ["aminobenzen"]),
        KnownMolecule("benzoik asit", "benzoic acid", "OC(=O)C1=CC=CC=C1"),
        KnownMolecule("klorobenzen", "chlorobenzene", "ClC1=CC=CC=C1"),
        KnownMolecule("nitrobenzen", "nitrobenzene", "[O-][N+](=O)C1=CC=CC=C1"),
        KnownMolecule("naftalin", "naphthalene", "C1=CC=C2C=CC=CC2=C1", ["naftalen"]),
        KnownMolecule("glikoz", "glucose", "OCC1OC(O)C(O)C(O)C1O", ["glukoz", "dekstroz", "üzüm şekeri"]),
    ]

    private static func key(_ s: String) -> String {
        var t = s.trimmingCharacters(in: .whitespacesAndNewlines).lowercased(with: Locale(identifier: "en_US_POSIX"))
        for (a, b) in [("ı", "i"), ("İ", "i"), ("ü", "u"), ("ö", "o"), ("ş", "s"), ("ç", "c"), ("ğ", "g"), (" ", ""), ("-", "")] {
            t = t.replacingOccurrences(of: a, with: b)
        }
        return t
    }

    private static let names: [String: KnownMolecule] = {
        var m: [String: KnownMolecule] = [:]
        for k in all {
            for n in [k.name, k.en] + k.aliases {
                // kapalı formül gibi görünen takma adlar ("h2o") ad tablosuna girmez
                if n.contains(where: { $0.isNumber }) && n.count <= 4 { continue }
                let kk = key(n)
                if m[kk] == nil { m[kk] = k }
            }
        }
        return m
    }()
    private static let hills: [String: KnownMolecule] = {
        var m: [String: KnownMolecule] = [:]
        for k in all where m[k.hill] == nil { m[k.hill] = k }
        return m
    }()
    private static let signatures: [String: KnownMolecule] = {
        var m: [String: KnownMolecule] = [:]
        for k in all where m[k.signature] == nil { m[k.signature] = k }
        return m
    }()

    static func byName(_ text: String) -> KnownMolecule? { names[key(text)] }
    static func byHill(_ hill: String) -> KnownMolecule? { hills[hill] }
    static func bySignature(_ m: Molecule) -> KnownMolecule? { signatures[m.signature()] }
}
