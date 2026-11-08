"""v42 çizgi roman efekt ve nesne sayfalarının elle verilen bilgileri.

Kaynak: sahibin verdiği effects.zip ve items.zip (manga/ ve comicbook|comic/ klasörleri). Aynı içeriğin ikinci kopyası
olan sayfalar (iki klasörde birden duran ya da pus zeminli yinelenen çizimler) alınmadı; alınanlar SHEETS'te.
Nesne sayfalarının manga ve çizgi roman klasörlerindeki kopyaları neredeyse aynı olduğundan her çiftten bir sayfa
alındı ve iki stilde de gösterilir.

Her sayfa: kısa ad, tür (effect/object), mod (alpha/black/light), stiller, satır başına kategori ve temalar,
ayırma ayarları; "drop": atılan parça sıraları (inceleme resimlerinde görülen bozuk/yarım/yinelenen parçalar).
"""

LANGS = ["tr", "en", "ar", "ja", "ko", "ru", "zh"]
BOTH = ["manga", "cizgi_roman"]
M, C = ["manga"], ["cizgi_roman"]

# v42 tema paketleri (karakterlerin v40 temalarına iki yeni tema eklendi). Her paketin karakterleri, nesneleri,
# efektleri ve mekân arka planları veritabanında tema-varlık bağlantı tablosuyla ilişkilendirilir.
NEW_THEMES = [
    ("macera", ["Macera", "Adventure", "مغامرة", "冒険", "모험", "Приключения", "冒险"]),
    ("kiyamet", ["Kıyamet Sonrası", "Post-Apocalyptic", "ما بعد الكارثة", "終末世界", "포스트 아포칼립스", "Постапокалипсис", "末日废土"]),
]

ALL_THEMES = ["okul", "gunluk", "meslek", "tarih", "askeri", "bilimkurgu", "fantastik", "kahraman", "spor", "vahsibati",
              "polisiye", "steampunk", "macera", "kiyamet"]

EFFECT_CATEGORIES = [
    ("enerji", ["Enerji ve aura", "Energy & aura", "طاقة وهالة", "エネルギー・オーラ", "에너지·오라", "Энергия и аура", "能量与光环"]),
    ("portal", ["Portal ve büyü", "Portals & magic", "بوابات وسحر", "ポータル・魔法", "포털·마법", "Порталы и магия", "传送门与魔法"]),
    ("patlama", ["Patlama ve ateş", "Explosions & fire", "انفجارات ونار", "爆発・炎", "폭발·불꽃", "Взрывы и огонь", "爆炸与火焰"]),
    ("silah", ["Atış ve namlu alevi", "Gunfire & muzzle flash", "إطلاق النار", "銃撃・マズルフラッシュ", "총격·총구 화염", "Выстрелы", "射击与枪口火焰"]),
    ("duman", ["Duman, toz ve darbe", "Smoke, dust & impact", "دخان وغبار وارتطام", "煙・砂塵・衝撃", "연기·먼지·충격", "Дым, пыль и удар", "烟尘与冲击"]),
    ("doga", ["Su, rüzgâr ve yaprak", "Water, wind & leaves", "ماء ورياح وأوراق", "水・風・葉", "물·바람·잎", "Вода, ветер и листья", "水、风与树叶"]),
    ("isik", ["Işıltı ve parlama", "Sparkles & flares", "بريق ووهج", "きらめき・閃光", "반짝임·섬광", "Блеск и вспышки", "闪光与光芒"]),
    ("simge", ["Duygu simgeleri ve çizgiler", "Emotion marks & lines", "رموز المشاعر والخطوط", "感情記号・効果線", "감정 기호·효과선", "Знаки эмоций и линии", "情绪符号与效果线"]),
]

OBJECT_CATEGORIES = [
    ("silah_zirh", ["Silah ve zırh", "Weapons & armor", "أسلحة ودروع", "武器・防具", "무기·갑옷", "Оружие и доспехи", "武器与盔甲"]),
    ("sancak_engel", ["Sancak, kalkan ve engel", "Banners, shields & barriers", "رايات ودروع وحواجز", "旗・盾・障害物", "깃발·방패·장애물", "Знамёна, щиты и заграждения", "旗帜、盾牌与障碍"]),
    ("isik", ["Fener ve ateş", "Lanterns & fire", "فوانيس ونار", "ランタン・火", "랜턴·불", "Фонари и огонь", "灯笼与火"]),
    ("belge", ["Belge ve dedektif", "Documents & detective", "وثائق ومحقق", "書類・探偵", "서류·탐정", "Документы и детектив", "文件与侦探"]),
    ("mobilya", ["Mobilya ve oda", "Furniture & rooms", "أثاث وغرف", "家具・部屋", "가구·방", "Мебель и комнаты", "家具与房间"]),
    ("teknoloji", ["Teknoloji", "Technology", "تقنية", "テクノロジー", "기술", "Технологии", "科技"]),
    ("yiyecek", ["Yiyecek ve içecek", "Food & drink", "طعام وشراب", "食べ物・飲み物", "음식·음료", "Еда и напитки", "食物与饮料"]),
    ("hazine", ["Hazine ve değerli eşya", "Treasure & valuables", "كنوز ونفائس", "財宝・貴重品", "보물·귀중품", "Сокровища", "宝藏与珍品"]),
    ("tuzak", ["Tuzak ve tahkimat", "Traps & fortifications", "فخاخ وتحصينات", "罠・防御設備", "함정·방어 시설", "Ловушки и укрепления", "陷阱与防御工事"]),
    ("buyulu_yapi", ["Büyülü yapılar", "Magical structures", "مبانٍ سحرية", "魔法の建造物", "마법 구조물", "Магические сооружения", "魔法建筑"]),
    ("buyu", ["Büyü eşyaları", "Magic items", "أدوات سحرية", "魔法アイテム", "마법 아이템", "Магические предметы", "魔法物品"]),
    ("bilimkurgu", ["Bilim kurgu yapı ve araç", "Sci-fi bases & vehicles", "قواعد ومركبات خيال علمي", "SF基地・乗り物", "SF 기지·탈것", "Фантастические базы и техника", "科幻基地与载具"]),
]

# Sayfalar: (ad, tür, mod, stiller, varsayılan kategori, varsayılan temalar, ayarlar)
# ayarlar: "rows": {satır sırası: (kategori, [temalar])}, "drop": [parça sırası], ayırma eşikleri (props.py)
SHEETS = [
    # --- Efektler: çizgi roman klasörü ---
    ("fx_c0", "effect", "alpha", BOTH, "portal", ["fantastik", "bilimkurgu", "kahraman"], {"drop": [0, 6, 7, 11, 12, 17, 35, 36, 39, 44, 45, 46], "cat": {1: "enerji", 2: "enerji", 3: "enerji", 4: "enerji", 5: "enerji", 8: "enerji", 9: "enerji", 10: "enerji", 37: "enerji", 38: "enerji", 40: "enerji", 41: "enerji", 42: "enerji", 43: "enerji"}}),
    ("fx_c1", "effect", "alpha", C, "simge", ALL_THEMES, {"big_min": 2600, "merge": 26, "drop": [2, 8, 9, 11, 15, 29, 30, 36, 53, 56, 57], "cat": {16: "duman", 18: "duman", 19: "duman", 48: "duman", 52: "duman", 54: "duman", 55: "duman"}}),
    ("fx_c2", "effect", "alpha", C, "enerji", ["fantastik", "kahraman"], {"close": 9, "big_min": 3200, "merge": 24, "drop": [3, 6, 36, 40]}),
    ("fx_c4", "effect", "alpha", C, "duman", ["kahraman", "fantastik", "askeri", "kiyamet"], {"lo": 0.25, "seg": 0.6, "close": 3, "big_min": 500, "merge": 10, "glow": 14, "drop": [0, 1, 4, 6, 12, 15, 16, 17, 19, 20, 21, 22, 25, 26, 29, 30, 33, 48, 49, 51, 53, 55, 57], "cat": {8: "enerji", 9: "enerji", 10: "enerji", 11: "enerji", 13: "enerji", 14: "enerji", 34: "enerji", 35: "enerji", 36: "enerji", 37: "enerji", 38: "enerji", 39: "enerji", 40: "enerji", 41: "enerji", 42: "enerji", 43: "enerji"}}),
    ("fx_c5", "effect", "alpha", C, "isik", ["fantastik", "kahraman", "gunluk", "okul"], {"lo": 0.5, "seg": 0.9, "close": 1, "big_min": 500, "merge": 10, "glow": 14, "drop": [22, 28, 39, 42, 46, 49, 51], "cat": {29: "enerji", 30: "enerji", 31: "enerji", 32: "enerji", 33: "enerji", 34: "enerji", 35: "enerji", 36: "enerji", 37: "enerji", 38: "enerji", 40: "enerji", 41: "enerji", 43: "enerji", 44: "enerji", 45: "enerji", 47: "enerji", 48: "enerji", 50: "enerji"}}),
    ("fx_c7", "effect", "black", C, "silah", ["askeri", "polisiye", "vahsibati", "kiyamet"], {"solid": True, "drop": [5, 7, 8, 12, 16, 17, 40, 46, 47], "cat": {45: "patlama", 52: "patlama", 48: "duman", 49: "duman", 50: "duman", 51: "duman", 53: "duman", 54: "duman"}}),
    ("fx_c8", "effect", "black", C, "patlama", ["fantastik", "kahraman", "askeri", "kiyamet"], {"drop": [6, 13, 14, 16, 19]}),
    ("fx_c9", "effect", "black", BOTH, "patlama", ["askeri", "kahraman", "bilimkurgu", "kiyamet"], {"solid": True, "drop": [0, 16, 17, 18]}),
    # --- Efektler: manga klasörü ---
    ("fx_m0", "effect", "alpha", M, "duman", ["askeri", "kahraman", "kiyamet", "vahsibati"], {"drop": [0, 5, 12, 16, 17, 22, 23, 31, 32, 33, 34, 35, 38, 41, 42, 44], "cat": {20: "patlama", 26: "patlama"}}),
    ("fx_m1", "effect", "alpha", M, "doga", ["fantastik", "macera", "gunluk"], {"keep": [3, 4, 8, 9, 12, 13, 14, 15, 16, 18, 26, 29, 31, 32, 35, 46, 47, 48, 52, 57, 61, 69, 74, 75, 76, 77, 82, 84, 100, 103, 106]}),
    ("fx_m5", "effect", "alpha", M, "simge", ALL_THEMES, {"lo": 0.25, "seg": 0.6, "close": 3, "big_min": 500, "merge": 10, "glow": 14, "keep": [0, 1, 2, 5, 6, 7, 8, 9, 10, 15, 17, 19, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 45, 46, 51, 52, 53, 54, 57, 58, 59, 60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 86, 87, 90, 94, 95, 97, 99, 100, 101, 103, 104, 105, 110, 111, 118]}),
    ("fx_m6", "effect", "light", M, "patlama", ["kahraman", "spor", "fantastik"], {"drop": [5, 7, 21, 22], "cat": {8: "enerji", 9: "enerji", 10: "enerji", 18: "enerji", 19: "enerji", 20: "enerji", 26: "enerji", 15: "duman", 16: "duman"}}),
    ("fx_m8", "effect", "alpha", M, "enerji", ["fantastik", "kahraman", "bilimkurgu"], {"drop": [0, 9, 11, 12, 13, 14, 15, 17, 20, 22, 24, 28, 30, 32, 33, 34, 35, 36, 37, 38, 46, 47, 48], "cat": {6: "patlama", 7: "patlama", 8: "patlama", 10: "patlama", 44: "patlama", 26: "doga", 27: "doga", 29: "doga", 31: "doga"}}),
    # --- Nesneler (iki klasördeki kopyaların birer tanesi; iki stilde) ---
    ("it_scifi", "object", "light", BOTH, "bilimkurgu", ["bilimkurgu", "askeri"], {}),
    ("it_medieval", "object", "alpha", BOTH, "sancak_engel", ["tarih", "fantastik"], {"drop": [15], "cat": {0: "silah_zirh", 1: "silah_zirh", 2: "silah_zirh", 3: "silah_zirh", 4: "silah_zirh", 5: "silah_zirh", 6: "silah_zirh", 7: "silah_zirh", 8: "silah_zirh", 9: "silah_zirh", 16: "silah_zirh", 17: "silah_zirh", 18: "silah_zirh", 19: "silah_zirh", 20: "silah_zirh"}}),
    ("it_light", "object", "light", BOTH, "isik", ["fantastik", "tarih", "gunluk", "macera"], {}),
    ("it_detective", "object", "alpha", BOTH, "belge", ["polisiye", "meslek", "macera"], {"lo": 0.6, "hi": 0.85, "drop": [13, 19, 29, 30, 33], "cat": {27: "hazine"}}),
    ("it_warroom", "object", "light", BOTH, "mobilya", ["askeri", "tarih", "polisiye"], {"erode": 0.035, "seed_min": 0.003, "big_min_frac": 0.004, "drop": [5, 13, 16]}),
    ("it_tech", "object", "light", BOTH, "teknoloji", ["bilimkurgu", "askeri", "polisiye"], {}),
    ("it_food", "object", "light", BOTH, "yiyecek", ["gunluk", "tarih", "fantastik"], {"drop": [8, 46]}),
    ("it_treasure", "object", "light", BOTH, "hazine", ["macera", "fantastik", "tarih"], {"drop": [55]}),
    ("it_traps", "object", "light", BOTH, "tuzak", ["tarih", "askeri", "macera", "kiyamet"], {"drop": [24, 26, 27, 31, 33, 36, 38]}),
    ("it_fantasy", "object", "alpha", BOTH, "buyulu_yapi", ["fantastik"], {"drop": [0, 1, 13]}),
    ("it_armory", "object", "alpha", BOTH, "silah_zirh", ["tarih", "fantastik", "askeri"], {}),
    ("it_magic", "object", "alpha", BOTH, "buyu", ["fantastik", "macera"], {"drop": [32, 39, 40], "cat": {46: "hazine", 49: "hazine", 50: "hazine", 51: "hazine", 52: "hazine"}}),
]

# Mod başına ayırma ayarları (sayfa ayarlarıyla birleşir)
DEFAULTS = {
    "alpha": {"lo": 0.10, "hi": 0.80, "seg": 0.35, "close": 5, "big_min": 700, "merge": 16, "glow": 18},
    "black": {"floor": 0.04, "gamma": 0.7, "seg": 0.15, "close": 9, "big_min": 900, "merge": 24, "glow": 22},
    "light": {"kind": "checker", "seg": 0.5, "close": 3, "big_min": 600, "merge": 10, "glow": 4, "watershed": True},
}
