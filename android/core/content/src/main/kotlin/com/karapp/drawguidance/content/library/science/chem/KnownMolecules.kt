package com.karapp.drawguidance.content.library.science.chem

import java.util.Locale

/**
 * Bilinen molekül: Türkçe [name] (şekilde gösterilir), İngilizce [en] ve diğer adlar ([aliases]; ada göre arama),
 * yapısı [smiles] (Kekulé yazımı). Aynı kapalı formüllü molekülerden tabloda önce gelen, kapalı formül yazılınca
 * seçilir (C2H6O → etanol).
 */
class KnownMolecule(val name: String, val en: String, val smiles: String, val aliases: List<String> = emptyList()) {
    private val mol: Molecule by lazy { Smiles.parse(smiles) }
    fun molecule(): Molecule = mol.copy()
    val hill: String by lazy { mol.hill() }
    val signature: String by lazy { mol.signature() }
}

object KnownMolecules {
    val all: List<KnownMolecule> = listOf(
        // --- elementler ve küçük inorganik moleküller
        KnownMolecule("hidrojen", "hydrogen", "[H][H]", listOf("h2")),
        KnownMolecule("oksijen", "oxygen", "O=O", listOf("o2")),
        KnownMolecule("azot", "nitrogen", "N#N", listOf("nitrojen", "n2")),
        KnownMolecule("klor", "chlorine", "ClCl", listOf("cl2")),
        KnownMolecule("flor", "fluorine", "FF"),
        KnownMolecule("ozon", "ozone", "[O-][O+]=O"),
        KnownMolecule("su", "water", "O", listOf("h2o")),
        KnownMolecule("hidrojen peroksit", "hydrogen peroxide", "OO", listOf("oksijenli su")),
        KnownMolecule("karbon dioksit", "carbon dioxide", "O=C=O", listOf("karbondioksit")),
        KnownMolecule("karbon monoksit", "carbon monoxide", "[C-]#[O+]", listOf("karbonmonoksit")),
        KnownMolecule("amonyak", "ammonia", "N"),
        KnownMolecule("metan", "methane", "C"),
        KnownMolecule("hidrojen klorür", "hydrogen chloride", "Cl", listOf("hidroklorik asit", "tuz ruhu")),
        KnownMolecule("hidrojen florür", "hydrogen fluoride", "F"),
        KnownMolecule("hidrojen sülfür", "hydrogen sulfide", "S"),
        KnownMolecule("hidrojen siyanür", "hydrogen cyanide", "C#N"),
        KnownMolecule("kükürt dioksit", "sulfur dioxide", "O=S=O"),
        KnownMolecule("kükürt trioksit", "sulfur trioxide", "O=S(=O)=O"),
        KnownMolecule("sülfürik asit", "sulfuric acid", "OS(=O)(=O)O", listOf("zaç yağı")),
        KnownMolecule("nitrik asit", "nitric acid", "O[N+](=O)[O-]", listOf("kezzap")),
        KnownMolecule("fosforik asit", "phosphoric acid", "OP(=O)(O)O"),
        KnownMolecule("karbonik asit", "carbonic acid", "OC(=O)O"),
        KnownMolecule("hidrazin", "hydrazine", "NN"),
        KnownMolecule("fosfin", "phosphine", "P"),
        KnownMolecule("silan", "silane", "[SiH4]"),
        KnownMolecule("bor triflorür", "boron trifluoride", "FB(F)F"),
        KnownMolecule("fosfor triklorür", "phosphorus trichloride", "ClP(Cl)Cl"),
        KnownMolecule("fosfor pentaklorür", "phosphorus pentachloride", "ClP(Cl)(Cl)(Cl)Cl"),
        KnownMolecule("karbon tetraklorür", "carbon tetrachloride", "ClC(Cl)(Cl)Cl"),
        KnownMolecule("kloroform", "chloroform", "ClC(Cl)Cl", listOf("triklorometan")),
        KnownMolecule("diklorometan", "dichloromethane", "ClCCl"),
        KnownMolecule("klorometan", "chloromethane", "CCl", listOf("metil klorür")),
        // --- iyonik bileşikler
        KnownMolecule("sodyum klorür", "sodium chloride", "[Na+].[Cl-]", listOf("yemek tuzu", "tuz")),
        KnownMolecule("potasyum klorür", "potassium chloride", "[K+].[Cl-]"),
        KnownMolecule("sodyum hidroksit", "sodium hydroxide", "[Na+].[OH-]", listOf("sud kostik")),
        KnownMolecule("kalsiyum klorür", "calcium chloride", "[Cl-].[Ca+2].[Cl-]"),
        KnownMolecule("magnezyum oksit", "magnesium oxide", "[Mg+2].[O-2]"),
        KnownMolecule("kalsiyum karbonat", "calcium carbonate", "[Ca+2].[O-]C(=O)[O-]", listOf("kireç taşı")),
        KnownMolecule("sodyum bikarbonat", "sodium bicarbonate", "[Na+].OC(=O)[O-]", listOf("karbonat", "kabartma tozu")),
        KnownMolecule("amonyum klorür", "ammonium chloride", "[NH4+].[Cl-]", listOf("nişadır")),
        // --- alkanlar
        KnownMolecule("etan", "ethane", "CC"),
        KnownMolecule("propan", "propane", "CCC"),
        KnownMolecule("bütan", "butane", "CCCC", listOf("butan", "n-bütan")),
        KnownMolecule("izobütan", "isobutane", "CC(C)C", listOf("izobutan", "2-metilpropan")),
        KnownMolecule("pentan", "pentane", "CCCCC"),
        KnownMolecule("hekzan", "hexane", "CCCCCC", listOf("heksan")),
        KnownMolecule("siklohekzan", "cyclohexane", "C1CCCCC1", listOf("sikloheksan")),
        // --- alkenler, alkinler
        KnownMolecule("eten", "ethylene", "C=C", listOf("etilen", "ethene")),
        KnownMolecule("propen", "propene", "C=CC", listOf("propilen")),
        KnownMolecule("1-büten", "1-butene", "C=CCC"),
        KnownMolecule("2-büten", "2-butene", "CC=CC"),
        KnownMolecule("etin", "acetylene", "C#C", listOf("asetilen", "ethyne")),
        KnownMolecule("propin", "propyne", "C#CC"),
        KnownMolecule("vinil klorür", "vinyl chloride", "C=CCl", listOf("kloroeten")),
        // --- alkoller, eterler
        KnownMolecule("metanol", "methanol", "CO", listOf("metil alkol", "odun alkolü")),
        KnownMolecule("etanol", "ethanol", "CCO", listOf("etil alkol", "alkol")),
        KnownMolecule("1-propanol", "1-propanol", "CCCO", listOf("propanol")),
        KnownMolecule("2-propanol", "isopropanol", "CC(O)C", listOf("izopropil alkol")),
        KnownMolecule("1-bütanol", "1-butanol", "CCCCO", listOf("bütanol")),
        KnownMolecule("etilen glikol", "ethylene glycol", "OCCO", listOf("glikol", "1,2-etandiol")),
        KnownMolecule("gliserol", "glycerol", "OCC(O)CO", listOf("gliserin")),
        KnownMolecule("dimetil eter", "dimethyl ether", "COC"),
        KnownMolecule("dietil eter", "diethyl ether", "CCOCC", listOf("eter")),
        // --- aldehitler, ketonlar
        KnownMolecule("formaldehit", "formaldehyde", "C=O", listOf("metanal")),
        KnownMolecule("asetaldehit", "acetaldehyde", "CC=O", listOf("etanal")),
        KnownMolecule("aseton", "acetone", "CC(=O)C", listOf("propanon")),
        // --- karboksilli asitler, esterler
        KnownMolecule("formik asit", "formic acid", "OC=O", listOf("metanoik asit", "karınca asidi")),
        KnownMolecule("asetik asit", "acetic acid", "CC(=O)O", listOf("etanoik asit", "sirke asidi")),
        KnownMolecule("propanoik asit", "propanoic acid", "CCC(=O)O", listOf("propiyonik asit")),
        KnownMolecule("oksalik asit", "oxalic acid", "OC(=O)C(=O)O"),
        KnownMolecule("laktik asit", "lactic acid", "CC(O)C(=O)O", listOf("süt asidi")),
        KnownMolecule("metil asetat", "methyl acetate", "CC(=O)OC"),
        KnownMolecule("etil asetat", "ethyl acetate", "CC(=O)OCC"),
        // --- azotlu bileşikler
        KnownMolecule("metilamin", "methylamine", "CN", listOf("metil amin")),
        KnownMolecule("etilamin", "ethylamine", "CCN"),
        KnownMolecule("üre", "urea", "NC(N)=O", listOf("ure", "karbamit")),
        KnownMolecule("asetamit", "acetamide", "CC(N)=O"),
        KnownMolecule("asetonitril", "acetonitrile", "CC#N"),
        KnownMolecule("glisin", "glycine", "NCC(=O)O"),
        KnownMolecule("alanin", "alanine", "CC(N)C(=O)O"),
        // --- aromatikler
        KnownMolecule("benzen", "benzene", "C1=CC=CC=C1"),
        KnownMolecule("toluen", "toluene", "CC1=CC=CC=C1", listOf("metilbenzen")),
        KnownMolecule("fenol", "phenol", "OC1=CC=CC=C1", listOf("hidroksibenzen")),
        KnownMolecule("anilin", "aniline", "NC1=CC=CC=C1", listOf("aminobenzen")),
        KnownMolecule("benzoik asit", "benzoic acid", "OC(=O)C1=CC=CC=C1"),
        KnownMolecule("klorobenzen", "chlorobenzene", "ClC1=CC=CC=C1"),
        KnownMolecule("nitrobenzen", "nitrobenzene", "[O-][N+](=O)C1=CC=CC=C1"),
        KnownMolecule("naftalin", "naphthalene", "C1=CC=C2C=CC=CC2=C1", listOf("naftalen")),
        // --- şekerler
        KnownMolecule("glikoz", "glucose", "OCC1OC(O)C(O)C(O)C1O", listOf("glukoz", "dekstroz", "üzüm şekeri")),
    )

    private fun key(s: String) = s.trim().lowercase(Locale.ROOT).replace('ı', 'i').replace('İ', 'i')
        .replace('ü', 'u').replace('ö', 'o').replace('ş', 's').replace('ç', 'c').replace('ğ', 'g')
        .replace(" ", "").replace("-", "")

    private val names: Map<String, KnownMolecule> by lazy {
        val m = HashMap<String, KnownMolecule>()
        for (k in all) for (n in listOf(k.name, k.en) + k.aliases) {
            // kapalı formül gibi görünen takma adlar ("h2o") ad tablosuna girmez: formül yolu büyük/küçük harfe duyarlı
            if (n.any { it.isDigit() } && n.length <= 4) continue
            m.putIfAbsent(key(n), k)
        }
        m
    }
    private val hills: Map<String, KnownMolecule> by lazy { LinkedHashMap<String, KnownMolecule>().apply { for (k in all) putIfAbsent(k.hill, this@apply.let { k }) } }
    private val signatures: Map<String, KnownMolecule> by lazy { HashMap<String, KnownMolecule>().apply { for (k in all) putIfAbsent(k.signature, k) } }

    fun byName(text: String): KnownMolecule? = names[key(text)]
    fun byHill(hill: String): KnownMolecule? = hills[hill]
    fun bySignature(m: Molecule): KnownMolecule? = signatures[m.signature()]
}
