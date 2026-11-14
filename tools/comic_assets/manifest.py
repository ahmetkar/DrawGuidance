"""v40 çizgi roman varlık kataloğunun elle verilen bilgileri: karakter kimlikleri, adları (7 dil), temaları, stilleri,
kaynak sayfaları ve sayfa başına ayırma ayarları; arka planların kategorileri.

Kaynak dosya adları sahibin verdiği zip dosyalarındaki adlardır (karakter.zip / background.zip).
"""

LANGS = ["tr", "en", "ar", "ja", "ko", "ru", "zh"]

THEMES = [
    ("okul", ["Okul ve gençlik", "School & youth", "المدرسة والشباب", "学園・青春", "학교·청춘", "Школа и юность", "校园青春"]),
    ("gunluk", ["Şehir ve ev", "City & home", "المدينة والمنزل", "街と家", "도시·집", "Город и дом", "城市与家"]),
    ("meslek", ["Uzman karakterler", "Specialists", "شخصيات متخصصة", "専門家キャラ", "전문가 캐릭터", "Специалисты", "专业人士"]),
    ("tarih", ["Tarihî dönem", "Historical era", "حقبة تاريخية", "歴史の時代", "역사 시대", "Историческая эпоха", "历史时代"]),
    ("askeri", ["Savaş ve ordu", "War & army", "الحرب والجيش", "戦争・軍隊", "전쟁·군대", "Война и армия", "战争与军队"]),
    ("bilimkurgu", ["Uzay ve gelecek", "Space & future", "الفضاء والمستقبل", "宇宙・未来", "우주·미래", "Космос и будущее", "太空与未来"]),
    ("fantastik", ["Büyü ve fantastik", "Magic & fantasy", "السحر والفانتازيا", "魔法・ファンタジー", "마법·판타지", "Магия и фэнтези", "魔法与奇幻"]),
    ("kahraman", ["Süper kahramanlar", "Superheroes", "أبطال خارقون", "スーパーヒーロー", "슈퍼히어로", "Супергерои", "超级英雄"]),
    ("spor", ["Spor ve dövüş", "Sports & fighting", "الرياضة والقتال", "スポーツ・格闘", "스포츠·격투", "Спорт и бои", "运动与格斗"]),
    ("vahsibati", ["Vahşi Batı", "Wild West", "الغرب الأمريكي", "西部劇", "서부극", "Дикий Запад", "西部"]),
    ("polisiye", ["Suç ve dedektif", "Crime & detective", "الجريمة والتحقيق", "犯罪・探偵", "범죄·탐정", "Преступление и детектив", "犯罪与侦探"]),
    ("steampunk", ["Buhar ve icat (steampunk)", "Steam & invention (steampunk)", "البخار والاختراع", "蒸気と発明（スチームパンク）", "증기·발명 (스팀펑크)", "Пар и изобретения (стимпанк)", "蒸汽与发明（蒸汽朋克）"]),
]

# v40 karakter etiketleri (tr, en, ar, ja, ko, ru, zh): sahibin isteğiyle kişi adı yok; meslek, yaş ya da rol ve
# gerekirse ayırt edici bir özellik. Kimlikler (ör. "kerem") yalnız iç anahtardır, gösterilmez.
LABELS_SRC = {
    "maskeli_hirsiz": ["Maskeli hırsız", "Masked thief", "لص مقنّع", "仮面の怪盗", "가면 도둑", "Вор в маске", "蒙面怪盗"],
    "ates_yumruk": ["Ateş yumruklu dövüşçü", "Fire-fist brawler", "مقاتل قبضة النار", "炎の拳の格闘家", "불꽃 주먹 격투가", "Боец с огненным кулаком", "火拳斗士"],
    "gezgin_kilicci": ["Gezgin kılıççı (kırmızı atkılı)", "Wandering swordsman (red scarf)", "مبارز رحّال (وشاح أحمر)", "流浪の剣士（赤いマフラー）", "방랑 검객 (빨간 목도리)", "Странствующий мечник (красный шарф)", "流浪剑客（红围巾）"],
    "siborg_kaya": ["Siborg savaşçı", "Cyborg fighter", "مقاتل سايبورغ", "サイボーグ戦士", "사이보그 전사", "Киборг-боец", "赛博格战士"],
    "nisanci_tolga": ["Nişancı (kırmızı pelerinli)", "Sharpshooter (red cloak)", "قنّاص (عباءة حمراء)", "狙撃手（赤いマント）", "저격수 (빨간 망토)", "Снайпер (красный плащ)", "狙击手（红斗篷）"],
    "kerem": ["Sakallı genç adam", "Young man with stubble", "شاب ملتحٍ", "無精ひげの青年", "수염 난 청년", "Молодой человек с щетиной", "留胡茬的青年"],
    "dedektif_selin": ["Dedektif (kadın, trençkotlu)", "Detective (woman, trench coat)", "محققة (معطف واقٍ من المطر)", "女性探偵（トレンチコート）", "여성 탐정 (트렌치코트)", "Детектив (женщина, тренч)", "女侦探（风衣）"],
    "boksor_emre": ["Boksör", "Boxer", "ملاكم", "ボクサー", "복서", "Боксёр", "拳击手"],
    "golge_ninja": ["Gölge ninja", "Shadow ninja", "نينجا الظل", "影の忍者", "그림자 닌자", "Теневой ниндзя", "影忍者"],
    "uzayli_buyucu": ["Uzaylı büyücü", "Alien mage", "ساحر فضائي", "異星の魔術師", "외계 마법사", "Инопланетный маг", "外星法师"],
    "gladyator": ["Gladyatör", "Gladiator", "مجالد", "剣闘士", "검투사", "Гладиатор", "角斗士"],
    "profesor_nuri": ["Yaşlı profesör (bilim insanı)", "Old professor (scientist)", "بروفيسور مسنّ (عالِم)", "老教授（科学者）", "노교수 (과학자)", "Пожилой профессор (учёный)", "老教授（科学家）"],
    "ege": ["Genç (kaykaycı)", "Teenage boy (skater)", "فتى مراهق (متزلّج)", "少年（スケーター）", "십대 소년 (스케이터)", "Подросток (скейтер)", "少年（滑板）"],
    "ogrenci_arda": ["Öğrenci erkek (lise)", "Schoolboy (high school)", "طالب (ثانوي)", "男子高校生", "남고생", "Старшеклассник", "男高中生"],
    "hasan_dede": ["Yaşlı dede", "Old grandpa", "جدّ مسنّ", "おじいさん", "할아버지", "Дедушка", "老爷爷"],
    "muhabir_deniz": ["Muhabir (fotoğrafçı)", "Reporter (photographer)", "مراسل (مصوّر)", "記者（カメラマン）", "기자 (사진기자)", "Репортёр (фотограф)", "记者（摄影师）"],
    "buyucu_efe": ["Genç büyücü (asalı)", "Young wizard (staff)", "ساحر شاب (عصا)", "若い魔法使い（杖）", "젊은 마법사 (지팡이)", "Юный волшебник (посох)", "年轻魔法师（法杖）"],
    "ogrenci_ece": ["Öğrenci kız (lise)", "Schoolgirl (high school)", "طالبة (ثانوي)", "女子高校生", "여고생", "Старшеклассница", "女高中生"],
    "zeynep": ["Ofis çalışanı (kadın)", "Office worker (woman)", "موظفة مكتب", "女性会社員", "여성 회사원", "Офисная сотрудница", "女职员"],
    "kovboy_riza": ["Kovboy", "Cowboy", "راعي بقر", "カウボーイ", "카우보이", "Ковбой", "牛仔"],
    "fatma_nine": ["Yaşlı nine", "Old grandma", "جدّة مسنّة", "おばあさん", "할머니", "Бабушка", "老奶奶"],
    "robotcu_mert": ["Robot mühendisi", "Robotics engineer", "مهندس روبوتات", "ロボット技師", "로봇 공학자", "Робототехник", "机器人工程师"],
    "mucit_volkan": ["Steampunk mucit", "Steampunk inventor", "مخترع ستيمبانك", "スチームパンクの発明家", "스팀펑크 발명가", "Изобретатель-стимпанк", "蒸汽朋克发明家"],
    "tamirci_ali": ["Tamirci", "Mechanic", "ميكانيكي", "整備士", "정비사", "Механик", "机修工"],
    "sovalye_aras": ["Şövalye", "Knight", "فارس", "騎士", "기사", "Рыцарь", "骑士"],
    "doktor_emir": ["Doktor (erkek)", "Doctor (man)", "طبيب", "男性医師", "남자 의사", "Врач (мужчина)", "男医生"],
    "dansci_bora": ["Sokak dansçısı", "Street dancer", "راقص شارع", "ストリートダンサー", "스트리트 댄서", "Уличный танцор", "街舞舞者"],
    "astronot_atlas": ["Astronot (uzay giysili)", "Astronaut (spacesuit)", "رائد فضاء (بذلة فضاء)", "宇宙飛行士（宇宙服）", "우주비행사 (우주복)", "Астронавт (скафандр)", "宇航员（宇航服）"],
    "akinci_tugrul": ["Osmanlı akıncısı (okçu)", "Ottoman akinji (archer)", "فارس أقنجي عثماني (رامٍ)", "オスマンのアクンジュ（弓兵）", "오스만 아큰즈 (궁수)", "Османский акынджи (лучник)", "奥斯曼阿金哲骑兵（弓箭手）"],
    "samuray_kenji": ["Samuray (erkek)", "Samurai (man)", "ساموراي (رجل)", "侍（男性）", "사무라이 (남성)", "Самурай (мужчина)", "武士（男）"],
    "buyucu_leyla": ["Büyücü kadın (mor)", "Sorceress (purple)", "ساحرة (بنفسجية)", "魔女（紫）", "마녀 (보라)", "Чародейка (в фиолетовом)", "女巫（紫色）"],
    "kilicci_doruk": ["Kılıççı (beyaz saçlı)", "Swordsman (white hair)", "مبارز (شعر أبيض)", "剣士（白髪）", "검객 (흰 머리)", "Мечник (седые волосы)", "剑客（白发）"],
    # v46 Lunara (elf büyücü kadın) kaldırıldı
    # v46 sahibin karakterler.zip sayfaları (add_v46_chars.py): anime stili; premium/ klasörü Premium, free/ Free
    "elf_buyucu_erkek": ["Elf büyücü (erkek)", "Elf mage (man)", "ساحر إلف (رجل)", "エルフの魔法使い（男性）", "엘프 마법사 (남성)", "Эльф-маг (мужчина)", "精灵法师（男）"],
    "elf_buyucu_kadin": ["Elf büyücü (kadın)", "Elf mage (woman)", "ساحرة إلف (امرأة)", "エルフの魔法使い（女性）", "엘프 마법사 (여성)", "Эльфийка-маг (женщина)", "精灵法师（女）"],
    "suikastci_kadin": ["Kukuletalı suikastçı (kadın)", "Hooded assassin (woman)", "قاتلة مقنّعة بقلنسوة", "フードの暗殺者（女性）", "후드 쓴 암살자 (여성)", "Убийца в капюшоне (женщина)", "兜帽刺客（女）"],
    "suikastci_erkek": ["Kukuletalı suikastçı (erkek)", "Hooded assassin (man)", "قاتل مقنّع بقلنسوة", "フードの暗殺者（男性）", "후드 쓴 암살자 (남성)", "Убийца в капюшоне (мужчина)", "兜帽刺客（男）"],
    "kutsal_sovalye_kadin": ["Kutsal şövalye (kadın)", "Paladin (woman)", "فارسة مقدسة", "聖騎士（女性）", "성기사 (여성)", "Паладин (женщина)", "圣骑士（女）"],
    "kutsal_sovalye_erkek": ["Kutsal şövalye (erkek)", "Paladin (man)", "فارس مقدس", "聖騎士（男性）", "성기사 (남성)", "Паладин (мужчина)", "圣骑士（男）"],
    "orman_okcusu_erkek": ["Orman okçusu (erkek)", "Forest archer (man)", "رامي الغابة", "森の弓使い（男性）", "숲의 궁수 (남성)", "Лесной лучник (мужчина)", "森林弓手（男）"],
    "orman_okcusu_kadin": ["Orman okçusu (kadın)", "Forest archer (woman)", "رامية الغابة", "森の弓使い（女性）", "숲의 궁수 (여성)", "Лесная лучница (женщина)", "森林弓手（女）"],
    "olu_cagiran_erkek": ["Ölü çağıran büyücü (erkek)", "Necromancer (man)", "مستحضر الأرواح", "ネクロマンサー（男性）", "네크로맨서 (남성)", "Некромант (мужчина)", "死灵法师（男）"],
    "olu_cagiran_kadin": ["Ölü çağıran büyücü (kadın)", "Necromancer (woman)", "مستحضرة الأرواح", "ネクロマンサー（女性）", "네크로맨서 (여성)", "Некромантка (женщина)", "死灵法师（女）"],
    "simyaci_kadin": ["Simyacı (kadın, gözlüklü)", "Alchemist (woman, goggles)", "خيميائية (نظارات)", "錬金術師（女性・ゴーグル）", "연금술사 (여성, 고글)", "Алхимик (женщина, очки)", "炼金术士（女，护目镜）"],
    "simyaci_erkek": ["Simyacı (erkek, gözlüklü)", "Alchemist (man, goggles)", "خيميائي (نظارات)", "錬金術師（男性・ゴーグル）", "연금술사 (남성, 고글)", "Алхимик (мужчина, очки)", "炼金术士（男，护目镜）"],
    "orman_ozani": ["Orman ozanı (druid)", "Forest bard (druid)", "شاعر الغابة (درويد)", "森の吟遊詩人（ドルイド）", "숲의 음유시인 (드루이드)", "Лесной бард (друид)", "森林吟游诗人（德鲁伊）"],
    "elf_okcu_silvan": ["Elf okçu", "Elf archer", "رامي إلف", "エルフの弓使い", "엘프 궁수", "Эльф-лучник", "精灵弓手"],
    "buz_buyucu_ayaz": ["Buz büyücüsü", "Ice mage", "ساحر الجليد", "氷の魔術師", "얼음 마법사", "Ледяной маг", "冰霜法师"],
    "kasif_cem": ["Kâşif (şapkalı)", "Explorer (hat)", "مستكشف (قبعة)", "探検家（帽子）", "탐험가 (모자)", "Исследователь (в шляпе)", "探险家（帽子）"],
    "samuray_aiko": ["Samuray (kadın)", "Samurai (woman)", "ساموراي (امرأة)", "女侍", "여성 사무라이", "Самурай (женщина)", "女武士"],
    "komando_baris": ["Komando", "Commando", "جندي كوماندوز", "コマンドー", "특공대원", "Коммандос", "突击队员"],
    "kilicci_lale": ["Kılıççı (kadın)", "Swordswoman", "مبارِزة", "女剣士", "여검객", "Мечница", "女剑客"],
    "hacker_ozan": ["Hacker", "Hacker", "مخترق", "ハッカー", "해커", "Хакер", "黑客"],
    "astronot_kaan": ["Astronot (beyaz tulumlu)", "Astronaut (white jumpsuit)", "رائد فضاء (بذلة بيضاء)", "宇宙飛行士（白いつなぎ）", "우주비행사 (흰 작업복)", "Астронавт (белый комбинезон)", "宇航员（白色连体服）"],
    "doktor_ayse": ["Doktor (kadın)", "Doctor (woman)", "طبيبة", "女性医師", "여자 의사", "Врач (женщина)", "女医生"],
    "demirci_emi": ["Demirci (genç kadın)", "Blacksmith (young woman)", "حدّادة شابة", "鍛冶屋（若い女性）", "대장장이 (젊은 여성)", "Кузнец (молодая женщина)", "铁匠（年轻女子）"],
    "ajan_nova": ["Gizli ajan (kadın)", "Secret agent (woman)", "عميلة سرية", "女性エージェント", "여성 요원", "Секретный агент (женщина)", "女特工"],
    "arkeolog_kemal": ["Arkeolog (gözlüklü)", "Archaeologist (glasses)", "عالم آثار (بنظارة)", "考古学者（眼鏡）", "고고학자 (안경)", "Археолог (в очках)", "考古学家（眼镜）"],
    "tufekci_yeniceri": ["Tüfekli yeniçeri", "Janissary musketeer", "إنكشاري بالبندقية", "イェニチェリ銃兵", "예니체리 총병", "Янычар с мушкетом", "近卫军火枪手"],
    "mogol_atli": ["Moğol atlı", "Mongol horseman", "فارس مغولي", "モンゴル騎兵", "몽골 기병", "Монгольский всадник", "蒙古骑兵"],
    "asker_mert": ["Asker (piyade)", "Soldier (infantry)", "جندي مشاة", "歩兵", "보병", "Солдат (пехотинец)", "步兵"],
    "korsan_kaptan": ["Korsan kaptan", "Pirate captain", "قبطان قراصنة", "海賊船長", "해적 선장", "Капитан пиратов", "海盗船长"],
    "kaptan_hilal": ["Süper kahraman (hilal amblemli)", "Superhero (crescent emblem)", "بطل خارق (شعار الهلال)", "スーパーヒーロー（三日月の紋章）", "슈퍼히어로 (초승달 문장)", "Супергерой (эмблема полумесяца)", "超级英雄（新月徽章）"],
    "dedektif_cemal": ["Dedektif (erkek, fötr şapkalı)", "Detective (man, fedora)", "محقق (قبعة فيدورا)", "男性探偵（中折れ帽）", "남성 탐정 (중절모)", "Детектив (мужчина, федора)", "男侦探（软呢帽）"],
    "serif": ["Şerif", "Sheriff", "شريف", "保安官", "보안관", "Шериф", "警长"],
    "polis_murat": ["Polis memuru", "Police officer", "شرطي", "警察官", "경찰관", "Полицейский", "警察"],
    "pilot_cengiz": ["Pilot (eski tip)", "Vintage pilot", "طيار بطراز قديم", "昔の飛行士", "옛날 비행사", "Пилот старой школы", "老式飞行员"],
    "can": ["Genç adam (şehirli)", "Young man (city)", "شاب من المدينة", "都会の青年", "도시 청년", "Молодой горожанин", "都市青年"],
    "elif": ["Genç kadın (şehirli)", "Young woman (city)", "شابة من المدينة", "都会の若い女性", "도시 여성", "Молодая горожанка", "都市年轻女子"],
    "yeniceri_osman": ["Yeniçeri (kılıçlı)", "Janissary (sword)", "إنكشاري (بسيف)", "イェニチェリ（剣）", "예니체리 (검)", "Янычар (с саблей)", "近卫军（佩剑）"],
}


LABELS = {cid: dict(zip(LANGS, v)) for cid, v in LABELS_SRC.items()}


# Kaynak dosya kısaltmaları (sahibin zip'indeki adlar)
P = {
    "P00": "053dd7c6-17f8-4e93-b4a5-a5803a2538b3.png", "P01": "07b891e7-a55a-466d-9327-60837109bf3b.png",
    "P02": "09a052c7-cba5-48db-afe7-4b2a1b4ec986.png", "P03": "0d62b4f1-1a01-40a2-9001-0114cbd0cb1a.png",
    "P04": "16e1a713-de7a-4554-8ebd-16807c78ba2c.png", "P05": "33e43896-1d29-4de3-b39c-afb08f97c931.png",
    "P06": "440be5da-f2d3-4c42-9806-7cc28d043d67.png", "P07": "82b67a8a-5fd6-4739-a509-c490ec1c787c.png",
    "P08": "8eb77d6c-4233-4f74-b35f-dcd634f59706.png", "P09": "92246495-47d1-469a-b558-cc9ef7091695.png",
    "P10": "9ceef725-6c09-430e-9345-56274966b194.png", "P11": "9fb682cb-b468-41ae-bc10-e1c5fd46ad75.png",
    "P12": "Flux2-Klein_00001_.png", "P13": "Flux2-Klein_00002_.png", "P14": "Flux2-Klein_00003_.png",
    "P15": "Flux2-Klein_00005_.png", "P16": "Flux2-Klein_00006_ (1).png", "P17": "Flux2-Klein_00006_.png",
    "P18": "Flux2-Klein_00007_ (1).png", "P19": "Flux2-Klein_00007_.png", "P20": "Flux2-Klein_00008_.png",
    "P21": "Flux2-Klein_00009_.png", "P22": "Flux2-Klein_00010_ (1).png", "P23": "Flux2-Klein_00010_.png",
    "P24": "Flux2-Klein_00011_.png", "P25": "Flux2-Klein_00012_ (1).png", "P26": "Flux2-Klein_00012_.png",
    "P27": "Flux2-Klein_00013_.png", "P28": "Flux2-Klein_00014_.png", "P29": "Flux2-Klein_00015_.png",
    "P30": "Flux2-Klein_00018_.png", "P31": "Flux2-Klein_00019_ (1).png", "P32": "Flux2-Klein_00019_.png",
    "P33": "Flux2-Klein_00020_ (1).png", "P34": "Flux2-Klein_00020_.png", "P35": "Flux2-Klein_00021_.png",
    "P36": "Flux2-Klein_00022_.png", "P37": "Flux2-Klein_00025_.png", "P38": "Flux2-Klein_00027_.png",
    "P39": "Flux2-Klein_00029_.png", "P40": "Flux2-Klein_00033_.png", "P41": "Flux2-Klein_00034_.png",
    "P42": "Flux2-Klein_00035_.png", "P43": "Flux2-Klein_00036_.png", "P44": "Flux2-Klein_00037_.png",
    "P45": "Flux2-Klein_00038_.png", "P46": "Flux2-Klein_00039_.png", "P47": "Flux2-Klein_00042_.png",
    "P48": "Flux2-Klein_00044_.png", "P49": "Flux2-Klein_00045_.png", "P50": "Flux2-Klein_00046_.png",
    "P51": "Flux2-Klein_00047_.png", "P52": "Flux2-Klein_00048_.png", "P53": "Flux2-Klein_00050_.png",
    "P54": "Flux2-Klein_00053_.png", "P55": "Flux2-Klein_00054_.png", "P56": "Flux2-Klein_00055_.png",
    "P57": "Flux2-Klein_00056_.png", "P58": "Flux2-Klein_00057_.png", "P59": "Flux2-Klein_00069_.png",
    "P60": "Flux2-Klein_00070_.png", "P61": "Flux2-Klein_00075_.png", "P62": "af421b61-f1d9-43ef-b6c2-cf2d2860fde2.png",
    "P63": "c45c4355-8276-4872-9b25-3b5aebc50b0b.png", "P64": "cf8a67cd-7a89-47a7-9eeb-d71b44e79720.png",
    "P65": "d1d60aa1-1e5f-4c09-b261-b4aeb8be5d8f.png", "P66": "d5dc1257-dd67-4232-b294-7fa27137bda6.png",
    "P67": "d75a47a8-3d0a-4b0f-8004-040a94c9c10f.png", "P68": "da839172-1607-4b46-a3ea-6e13025779e3.png",
    "P69": "e27eab6b-545e-4b74-b751-c6e69619f563.png", "P70": "e98d65ec-47d7-4a79-a442-a914beeb7285.png",
    "P71": "ec7e49b5-ce6a-4798-8bde-a0e58f543b8d.png", "P72": "f219b078-ee85-4eec-9d34-28abe224b40c.png",
    "P73": "f2a7ec0c-6be2-4dbc-8e81-e383e488c56b.png", "P74": "f81688bd-c7dd-4e13-b0ba-7b2eea100356.png",
    "J00": "WhatsApp Image 2026-10-121223 at 19.09.10.jpeg", "J01": "WhatsApp Image 2026-10-2123 at 19.09.09.jpeg",
    "J02": "WhatsApp Image 2026-10-23 11212at 19.09.10.jpeg", "J03": "WhatsApp Image 2026-10-23 54at 19.09.09.jpeg",
    "J04": "WhatsApp Image 2026-10-23 at 1129.09.09.jpeg", "J05": "WhatsApp Image 2026-10-23 at 1129.09.10.jpeg",
    "J06": "WhatsApp Image 2026-10-23 at 19.01219.10.jpeg", "J07": "WhatsApp Image 2026-10-23 at 19.0129.09.jpeg",
    "J08": "WhatsApp Image 2026-10-23 at 19.09.013.jpeg", "J09": "WhatsApp Image 2026-10-23 at 19.09.02.jpeg",
    "J10": "WhatsApp Image 2026-10-23 at 19.09.03.jpeg", "J11": "WhatsApp Image 2026-10-23 at 19.09.04.jpeg",
    "J12": "WhatsApp Image 2026-10-23 at 19.09.08.jpeg", "J13": "WhatsApp Image 2026-10-23 at 19.09.508.jpeg",
    "J14": "WhatsApp Image 2026-10-23 at 19.0912.09.jpeg", "J15": "WhatsApp Image 2026-10-23 at 19.094.08.jpeg",
    "J16": "WhatsApp Image 2026-10-23 at 19.121209.10.jpeg", "J17": "WhatsApp Image 2026-10-23 at 19.121979709.10.jpeg",
    "J18": "WhatsApp Image 2026-10-23 at 56519.09.09.jpeg", "J19": "WhatsApp Image 2026-10-241543 at 19.09.09.jpeg",
}

# Telif nedeniyle dışarıda bırakılan sayfalar (yayımlanmaz)
EXCLUDED = {
    "P11": "Beyaz karate kıyafeti, kırmızı alın bandı ve kırmızı eldivenler: Street Fighter'daki Ryu'nun neredeyse aynısı.",
    "J06": "Mavi-kırmızı tulum, kırmızı pelerin ve göğüste altın kanatlı yıldız amblemi: Marvel'ın Captain Marvel amblemine/kostümüne çok yakın.",
}

# Sayfa türü: "mixed" (karışık; sezgisel sınıflama) ya da tek grup (ifade/poz/aksiyon/gunluk)
M, I, PZ, A, G = "mixed", "ifade", "poz", "aksiyon", "gunluk"

# (kimlik, stil, tema, [(sayfa, sayfa türü), ...]); etiketler LABELS içinde
CHARACTERS = [
    # --- Manga (boyalı anime sayfaları) ---
    ("maskeli_hirsiz", "manga", "polisiye", [("P00", M)]),
    ("ates_yumruk", "manga", "fantastik", [("P01", M)]),
    ("gezgin_kilicci", "manga", "fantastik", [("P02", M)]),
    ("siborg_kaya", "manga", "bilimkurgu", [("P03", M)]),
    ("nisanci_tolga", "manga", "bilimkurgu", [("P04", M)]),
    ("kerem", "manga", "gunluk", [("P05", M)]),
    ("dedektif_selin", "manga", "polisiye", [("P71", M), ("P06", M)]),
    ("boksor_emre", "manga", "spor", [("P07", M)]),
    ("golge_ninja", "manga", "tarih", [("P08", M)]),
    ("uzayli_buyucu", "manga", "bilimkurgu", [("P09", M)]),
    ("gladyator", "manga", "tarih", [("P10", M)]),
    ("profesor_nuri", "manga", "meslek", [("P62", M)]),
    ("ege", "manga", "gunluk", [("P63", M)]),
    ("ogrenci_arda", "manga", "okul", [("P64", M)]),
    ("hasan_dede", "manga", "gunluk", [("P65", M)]),
    ("muhabir_deniz", "manga", "meslek", [("P66", M)]),
    ("buyucu_efe", "manga", "fantastik", [("P67", M)]),
    ("ogrenci_ece", "manga", "okul", [("P68", M)]),
    ("zeynep", "manga", "meslek", [("P69", M)]),
    ("kovboy_riza", "manga", "vahsibati", [("P70", M)]),
    ("fatma_nine", "manga", "gunluk", [("P72", M)]),
    ("robotcu_mert", "manga", "bilimkurgu", [("P73", M)]),
    ("mucit_volkan", "manga", "steampunk", [("P74", M)]),
    ("tamirci_ali", "manga", "meslek", [("J00", M)]),
    ("sovalye_aras", "manga", "fantastik", [("J01", M)]),
    ("doktor_emir", "manga", "meslek", [("J02", M)]),
    ("dansci_bora", "manga", "spor", [("J03", M)]),
    ("astronot_atlas", "manga", "bilimkurgu", [("J04", M)]),
    ("akinci_tugrul", "manga", "tarih", [("J05", M)]),
    ("samuray_kenji", "manga", "tarih", [("J07", M)]),
    ("buyucu_leyla", "manga", "fantastik", [("J09", M), ("J08", M)]),
    ("kilicci_doruk", "manga", "fantastik", [("J10", M)]),
    # v46 ("elf_lunara", "manga", "fantastik", [("J11", M)]) kaldırıldı
    ("elf_okcu_silvan", "manga", "fantastik", [("J12", M)]),
    ("buz_buyucu_ayaz", "manga", "fantastik", [("J13", M)]),
    ("kasif_cem", "manga", "meslek", [("J14", M)]),
    ("samuray_aiko", "manga", "tarih", [("J15", M)]),
    ("komando_baris", "manga", "askeri", [("J16", M), ("J17", M)]),
    ("kilicci_lale", "manga", "fantastik", [("J18", M)]),
    ("hacker_ozan", "manga", "bilimkurgu", [("J19", M)]),
    # --- Çizgi roman (düz renkli batı tarzı sayfalar) ---
    ("astronot_kaan", "cizgi_roman", "bilimkurgu", [("P12", I), ("P26", I), ("P32", I), ("P13", PZ), ("P14", PZ), ("P34", PZ)]),
    ("doktor_ayse", "cizgi_roman", "meslek", [("P15", I), ("P16", PZ), ("P18", G)]),
    ("demirci_emi", "manga", "meslek", [("P17", I), ("P19", G)]),
    ("ajan_nova", "cizgi_roman", "bilimkurgu", [("P20", I), ("P21", A)]),
    ("arkeolog_kemal", "cizgi_roman", "meslek", [("P22", I), ("P25", G), ("P24", G)]),
    ("tufekci_yeniceri", "cizgi_roman", "tarih", [("P23", I), ("P30", A)]),
    ("mogol_atli", "cizgi_roman", "tarih", [("P27", I), ("P28", A), ("P29", A)]),
    ("asker_mert", "cizgi_roman", "askeri", [("P31", I), ("P36", PZ), ("P33", A), ("P35", A)]),
    ("korsan_kaptan", "cizgi_roman", "tarih", [("P37", I), ("P39", PZ), ("P38", A)]),
    ("kaptan_hilal", "cizgi_roman", "kahraman", [("P40", I), ("P41", A), ("P42", A)]),
    ("dedektif_cemal", "cizgi_roman", "polisiye", [("P43", I), ("P44", A), ("P45", G)]),
    ("serif", "cizgi_roman", "vahsibati", [("P46", I)]),
    ("polis_murat", "cizgi_roman", "polisiye", [("P47", I), ("P48", A), ("P49", A)]),
    ("pilot_cengiz", "cizgi_roman", "askeri", [("P50", I), ("P52", PZ), ("P51", A)]),
    ("can", "cizgi_roman", "gunluk", [("P53", I), ("P54", G), ("P55", G)]),
    ("elif", "cizgi_roman", "gunluk", [("P56", I), ("P57", G), ("P58", G)]),
    ("yeniceri_osman", "cizgi_roman", "tarih", [("P59", I), ("P60", A), ("P61", A)]),
]

# Sayfa başına ayırma ayarları (segment.py): kind, cuts (normalize kesme çizgileri), erase, merge, ...
# ve "cls": {parça sırası: grup} elle düzeltmeler, "drop": [parça sırası] (sahne küçük resmi ya da bozuk parça).
SHEET_OPTS = {f"P{i:02d}": {"kind": "flat"} for i in range(12, 62)}
# v40: P58 (çerçeve çizgili dörtlü pano) çizgi silmede figürleri de kesiyordu; Elif'in öteki sayfaları yeterli.
SHEET_OPTS["P58"]["skip"] = True


def _opt(key, cls=None, drop=None, skip=False, nsplit=None):
    o = SHEET_OPTS.setdefault(key, {})
    if nsplit:  # {parça sırası: kaç büst} yapışık büst satırını bu kadar parçaya böl
        o.setdefault("nsplit", {}).update(nsplit)
    if cls:
        o.setdefault("cls", {}).update(cls)
    if drop:
        o.setdefault("drop", []).extend(drop)
    if skip:
        o["skip"] = True


def _same(group, *idx):
    return {i: group for i in idx}


# v40: İnceleme sayfalarına (önce/sonra) bakılarak verilen elle düzeltmeler. Sıra numaraları ayırma çıktısındaki
# parça sıralarıdır (inceleme sayfasında "P05#13" gibi yazılır).
_opt("P00", cls={**_same(PZ, 4, 7), 5: A})
_opt("P02", cls={1: I, 2: PZ, 3: G})
_opt("P04", cls={1: I, 2: PZ})
_opt("P05", cls=_same(G, 13, 14, 16, 17, 18, 19, 20, 21))
_opt("P06", skip=True)  # P71'in koyu zeminli kopyası; kesimler bozuk
_opt("P08", cls={1: PZ})
_opt("P09", drop=[7, 8])  # birden çok figür yapışık
_opt("P10", cls={2: PZ}, drop=[3])
_opt("P62", cls={**_same(I, 0, 1, 2, 3, 4, 6, 11), 7: G, 8: PZ, **_same(A, 5, 9, 10)})
_opt("P63", cls={**_same(I, *range(8)), **_same(PZ, 8, 9, 10, 11, 12), **_same(A, 13, 14),
                 **_same(G, 15, 16, 17, 18, 19, 20, 21)})
_opt("P64", cls={**_same(I, *range(10)), **_same(PZ, 10, 11, 12, 13, 18, 19), **_same(G, 14, 15, 20, 21, 22),
                 **_same(A, 16, 17, 23)})
_opt("P65", cls={**_same(I, *range(9)), **_same(PZ, *range(9, 15)), 15: G}, drop=list(range(16, 22)))
_opt("P66", cls={0: A, 1: G, 2: PZ, 3: G, 4: I})
_opt("P67", cls={**_same(I, 0, 1, 2, 3), 4: PZ, **_same(A, 5, 6, 7, 8)}, drop=[9])
_opt("P68", cls={**_same(I, *range(8), 8, 20), **_same(PZ, *range(9, 16)), **_same(G, 16, 17, 18, 19, 21, 22)})
_opt("P69", cls={**_same(I, *range(9)), **_same(PZ, 9, 10, 11, 12, 13, 15), **_same(G, 14, 16)}, drop=list(range(17, 23)))
_opt("P70", cls={1: I, 2: PZ, 3: G, 0: A, 4: A})
_opt("P71", drop=[10])
_opt("P72", cls={**_same(I, *range(8)), **_same(PZ, *range(8, 15)), 15: G}, drop=list(range(16, 22)))
_opt("P73", cls={0: I}, drop=[6])
_opt("P74", cls={0: I, 3: PZ}, drop=[2])  # P74#2: yalnız çizim kâğıdı
_opt("J00", cls=_same(PZ, 5, 7), drop=[6, 8])
_opt("J01", cls={5: PZ})
_opt("J02", cls={5: PZ})
_opt("J04", cls={5: PZ, 7: G})
_opt("J05", cls={5: PZ})
_opt("J07", cls={5: PZ})
_opt("J08", skip=True)  # koyu zeminli kopya; parçalar yapışık
_opt("J09", drop=[4, 5])
_opt("J10", cls={5: PZ}, drop=[6])
_opt("J11", cls={5: PZ, 7: G}, drop=[4])
_opt("J12", cls={3: I, 5: PZ})
_opt("J13", cls={5: PZ})
_opt("J14", cls={5: PZ})
_opt("J15", cls={5: PZ})
_opt("J16", cls={8: PZ, 12: A})
_opt("J18", cls={5: PZ})
_opt("J19", cls={5: PZ, 8: G}, drop=[7])  # J19#7: yalnız hologram ekranı
_opt("P13", drop=[1, 4])  # kask/çanta ve yalnız bacak parçası
_opt("P18", cls=_same(I, 0, 1, 2))
_opt("P29", cls={0: PZ})
_opt("P25", cls={1: PZ, 2: PZ})
_opt("P31", nsplit={0: 2})
_opt("P22", nsplit={0: 4, 1: 4})
_opt("P37", nsplit={0: 4, 1: 3})
_opt("P43", nsplit={0: 4, 1: 2})

# --- Arka planlar ---
BG_CATEGORIES = [
    ("sehir", ["Şehir", "City", "المدينة", "街", "도시", "Город", "城市"]),
    ("sokak", ["Sokak", "Street", "الشارع", "通り", "거리", "Улица", "街道"]),
    ("doga_park", ["Doğa/Park", "Nature/Park", "الطبيعة/الحديقة", "自然・公園", "자연/공원", "Природа/Парк", "自然/公园"]),
    ("okul", ["Okul ve gençlik", "School & youth", "المدرسة والشباب", "学園・青春", "학교·청춘", "Школа и юность", "校园青春"]),
    ("gece_yagmur", ["Gece/Yağmur", "Night/Rain", "الليل/المطر", "夜・雨", "밤/비", "Ночь/Дождь", "夜晚/雨天"]),
    ("kis", ["Kış", "Winter", "الشتاء", "冬", "겨울", "Зима", "冬天"]),
    ("yikim", ["Yıkım", "Ruins", "الدمار", "廃墟", "폐허", "Руины", "废墟"]),
    ("ic_mekan", ["İç Mekan", "Interior", "داخلي", "屋内", "실내", "Интерьер", "室内"]),
]

# Arka plan adları (tr, en, ar, ja, ko, ru, zh)
BGN = {
    "kar_nehir": ["Karlı nehir kıyısı", "Snowy riverside", "ضفة نهر مثلجة", "雪の川辺", "눈 덮인 강변", "Заснеженная набережная", "雪中河畔"],
    "kar_nehir2": ["Karlı yürüyüş yolu", "Snowy promenade", "ممشى مثلج", "雪の遊歩道", "눈 덮인 산책로", "Заснеженный бульвар", "雪中步道"],
    "okul_koridor": ["Okul koridoru", "School hallway", "ممر المدرسة", "学校の廊下", "학교 복도", "Школьный коридор", "学校走廊"],
    "okul_koridor2": ["Lise koridoru", "High school hallway", "ممر الثانوية", "高校の廊下", "고등학교 복도", "Коридор старшей школы", "高中走廊"],
    "sinif": ["Sınıf", "Classroom", "الفصل", "教室", "교실", "Класс", "教室"],
    "okul_cati": ["Okul çatısı", "School rooftop", "سطح المدرسة", "学校の屋上", "학교 옥상", "Крыша школы", "学校天台"],
    "okul_cati2": ["Sonbaharda okul çatısı", "School rooftop in autumn", "سطح المدرسة في الخريف", "秋の学校の屋上", "가을의 학교 옥상", "Крыша школы осенью", "秋天的学校天台"],
    "carsi_manga": ["Çarşı sokağı", "Shopping street", "شارع التسوق", "商店街", "상점가", "Торговая улица", "商业街"],
    "yikik_sehir": ["Yıkılmış şehir", "Ruined city", "مدينة مدمرة", "崩壊した街", "파괴된 도시", "Разрушенный город", "被毁的城市"],
    "yikik_kopru": ["Yıkılmış köprü ve şehir", "Ruined bridge and city", "جسر ومدينة مدمرة", "崩れた橋と街", "무너진 다리와 도시", "Разрушенный мост и город", "被毁的桥与城市"],
    "yikik_bina": ["Yıkık bina içi", "Ruined building interior", "داخل مبنى مدمر", "廃ビルの中", "폐건물 내부", "Внутри разрушенного здания", "废弃建筑内部"],
    "sakura_sokak": ["Sakura sokağı", "Cherry blossom street", "شارع أزهار الكرز", "桜の通り", "벚꽃 거리", "Улица в цветущей сакуре", "樱花街道"],
    "firtina_teras": ["Fırtınalı teras", "Stormy rooftop terrace", "شرفة في عاصفة", "嵐のテラス", "폭풍우 치는 테라스", "Терраса в грозу", "暴风雨中的露台"],
    "firtina_teras2": ["Şimşekli teras", "Terrace with lightning", "شرفة مع برق", "稲妻のテラス", "번개 치는 테라스", "Терраса с молниями", "闪电下的露台"],
    "yagmur_gece_manga": ["Yağmurlu gece sokağı", "Rainy night alley", "زقاق ليلي ممطر", "雨の夜の路地", "비 오는 밤 골목", "Дождливый ночной переулок", "雨夜小巷"],
    "yagmur_cadde": ["Yağmurlu cadde", "Rainy avenue", "شارع ممطر", "雨の大通り", "비 오는 대로", "Дождливый проспект", "雨中大道"],
    "avlu": ["Yeşil avlu", "Green courtyard", "فناء أخضر", "緑の中庭", "초록 안뜰", "Зелёный двор", "绿色庭院"],
    "avlu2": ["Çiçekli avlu", "Courtyard with flowers", "فناء مزهر", "花の中庭", "꽃 핀 안뜰", "Двор с цветами", "花园庭院"],
    "kopru_alti": ["Köprü altı (yağmur)", "Under the bridge (rain)", "تحت الجسر (مطر)", "橋の下（雨）", "다리 밑 (비)", "Под мостом (дождь)", "桥下（雨）"],
    "kopru_alti2": ["Köprü altı yolu", "Path under the bridge", "ممر تحت الجسر", "橋の下の道", "다리 밑 길", "Дорожка под мостом", "桥下小路"],
    "gunbatimi_cadde": ["Gün batımında cadde", "Avenue at sunset", "شارع عند الغروب", "夕暮れの大通り", "노을 진 대로", "Проспект на закате", "日落大道"],
    "depo": ["Eski depo", "Old warehouse", "مستودع قديم", "古い倉庫", "낡은 창고", "Старый склад", "旧仓库"],
    "nehir_park": ["Nehir kıyısı parkı", "Riverside park", "حديقة على ضفة النهر", "川沿いの公園", "강변 공원", "Парк у реки", "河畔公园"],
    "sonbahar_park": ["Sonbahar parkı", "Autumn park", "حديقة الخريف", "秋の公園", "가을 공원", "Осенний парк", "秋日公园"],
    "sonbahar_park2": ["Sonbahar yürüyüş yolu", "Autumn promenade", "ممشى الخريف", "秋の遊歩道", "가을 산책로", "Осенний бульвар", "秋日步道"],
    "sehir_manga": ["Şehir caddesi", "City avenue", "شارع المدينة", "街の大通り", "도시 대로", "Городской проспект", "城市大道"],
    "cati_teras": ["Çatı terası", "Rooftop terrace", "شرفة السطح", "屋上テラス", "옥상 테라스", "Терраса на крыше", "屋顶露台"],
    "cati_teras2": ["Güneşli çatı terası", "Sunny rooftop terrace", "شرفة سطح مشمسة", "晴れた屋上テラス", "맑은 옥상 테라스", "Солнечная терраса на крыше", "晴朗的屋顶露台"],
    "neon_ara_sokak": ["Neonlu ara sokak", "Neon back alley", "زقاق خلفي بالنيون", "ネオンの裏路地", "네온 뒷골목", "Неоновый переулок", "霓虹小巷"],
    "neon_cadde": ["Neonlu gece caddesi", "Neon night street", "شارع ليلي بالنيون", "ネオンの夜の通り", "네온 밤거리", "Неоновая ночная улица", "霓虹夜街"],
    "liman": ["Liman", "Harbor", "الميناء", "港", "항구", "Гавань", "港口"],
    "yagmur_cadde_manga": ["Yağmurlu gece caddesi", "Rainy night avenue", "شارع ليلي ممطر", "雨の夜の大通り", "비 오는 밤 대로", "Дождливый ночной проспект", "雨夜大道"],
    "carsi": ["Çarşı sokağı (renkli)", "Shopping street (color)", "شارع التسوق (ملون)", "商店街（カラー）", "상점가 (컬러)", "Торговая улица (цвет)", "商业街（彩色）"],
}

# (kaynak, kimlik, stil, kategori)
# v40 neredeyse aynı çiftler (cati_teras/2, kar_nehir/2, firtina_teras/2, kopru_alti/2, avlu/2, sonbahar_park/2): ikisi de
# renkli; sahibin isteğiyle "2" kopyası siyah-beyaz tramlı manga sürümüne çevrilip Manga'ya konur ("tone").
BACKGROUNDS = [
    ("00d23a2d-20ff-4e7c-90af-586aa105c8ff.png", "kar_nehir", "cizgi_roman", "kis"),
    ("09e711c9-bf6e-4e53-82c6-43fd89266356.png", "okul_koridor", "cizgi_roman", "okul"),
    ("0f844444-b78c-4ae6-9e03-e223797ae81e.png", "carsi_manga", "manga", "sokak"),
    ("12037c43-4be3-4617-9e38-93e3a0e79d56.png", "yikik_sehir", "cizgi_roman", "yikim"),
    ("13d58898-8294-4fe9-984e-4cadb926f75b.png", "sakura_sokak", "cizgi_roman", "sokak"),
    ("1aac425f-c3c7-4111-a20f-08c836ac15fb.png", "firtina_teras", "cizgi_roman", "gece_yagmur"),
    ("27ffe372-5a0d-4dd0-bf9b-a69fe0a06b6c.png", "yagmur_gece_manga", "manga", "gece_yagmur"),
    ("28a1f5fb-16f7-466b-bc8b-5150e406627c.png", "yagmur_cadde", "cizgi_roman", "gece_yagmur"),
    ("29808167-071a-4619-8cec-7280724c7dfd.png", "avlu", "cizgi_roman", "doga_park"),
    ("31f849f8-c98a-4e15-90f2-b641a67a3671.png", "kopru_alti", "cizgi_roman", "gece_yagmur"),
    ("3b8da024-c92b-457d-bdeb-68f0cb752ba1.png", "kar_nehir2", "manga", "kis", "tone"),
    ("3eada434-832b-4229-8115-268a31f1437e.png", "gunbatimi_cadde", "cizgi_roman", "sehir"),
    ("47e32adf-47ea-4fa4-8ea4-fbb63d80b25e.png", "depo", "cizgi_roman", "ic_mekan"),
    ("4ad1ad86-ffc8-40ca-b132-167e89939fd5.png", "nehir_park", "cizgi_roman", "doga_park"),
    # Sahibinin isteğiyle çıkarıldı (tescilli logo/yazı): ("4cf7abbf-cb38-4d7b-adbd-1f2867175b7a.png", "yikik_kopru", "cizgi_roman", "yikim"),
    ("5620f558-5fd5-4162-8dde-e3ec132fb44d.png", "sonbahar_park", "cizgi_roman", "doga_park"),
    ("655640f5-f4ec-4a39-bc2e-1af30f7a3a6f.png", "kopru_alti2", "manga", "gece_yagmur", "tone"),
    ("6ad6998b-3b0e-41e1-9291-ce7f946c2821.png", "avlu2", "manga", "doga_park", "tone"),
    ("6d2a2d57-0a08-48c0-8e5c-5c440b878aa4.png", "sehir_manga", "manga", "sehir"),
    # Sahibinin isteğiyle çıkarıldı (tescilli logo/yazı): ("6f59b6d2-5c90-4e69-b80a-03c1e75317d0.png", "okul_cati2", "cizgi_roman", "okul"),
    ("75e02853-e8c7-44ef-92e0-fcd79b4fa747.png", "sinif", "cizgi_roman", "okul"),
    ("87fc5787-ae4a-4c3b-844c-39e318dd3abc.png", "cati_teras", "cizgi_roman", "sehir"),
    ("8be22138-3031-46db-a7fb-42168785fc92.png", "cati_teras2", "manga", "sehir", "tone"),
    ("a2966685-3812-4cce-8914-9ff0029179e9.png", "firtina_teras2", "manga", "gece_yagmur", "tone"),
    ("b3793cf6-e709-4d34-85d4-b874e6e87cd6.png", "okul_koridor2", "cizgi_roman", "okul"),
    ("b682901c-1309-4abb-ba92-4d7794741abd.png", "neon_ara_sokak", "cizgi_roman", "gece_yagmur"),
    ("b68e1125-ae0d-43a5-a8ee-c806732eb874.png", "neon_cadde", "cizgi_roman", "gece_yagmur"),
    ("c87f5d84-1bf0-427d-ad3c-2f3376988237.png", "liman", "cizgi_roman", "sehir"),
    ("d366acac-eb73-451a-9f0b-3fc4f193e596.png", "yikik_bina", "cizgi_roman", "yikim"),
    ("d87fbb72-d770-4967-878e-79db9b9c8bdd.png", "okul_cati", "cizgi_roman", "okul"),
    ("e3ff0825-f606-464d-a2d8-20dbcda4ef05.png", "yagmur_cadde_manga", "manga", "gece_yagmur"),
    ("e7ebbd8c-9912-40ff-99cc-4d1c8702908b.png", "carsi", "cizgi_roman", "sokak"),
    ("fecc7cab-eff7-47eb-b876-b0f5bdc69ecb.png", "sonbahar_park2", "manga", "doga_park", "tone"),
]

# --- v42 yeni mekân arka planları (backgrounds2.zip; klasör = stil) ---
BG_CATEGORIES += [
    ("orman", ["Orman/Cangıl", "Forest/Jungle", "غابة/أدغال", "森・ジャングル", "숲/정글", "Лес/Джунгли", "森林/丛林"]),
    ("sahil", ["Sahil/Kıyı", "Coast/Beach", "ساحل/شاطئ", "海岸・浜辺", "해안/해변", "Побережье/Пляж", "海岸/海滩"]),
    ("col", ["Çöl/Kanyon", "Desert/Canyon", "صحراء/وادٍ", "砂漠・峡谷", "사막/협곡", "Пустыня/Каньон", "沙漠/峡谷"]),
]

BGN.update({
    "orman_harabe": ["Ormanda tapınak harabesi", "Jungle temple ruins", "أطلال معبد في الأدغال", "ジャングルの神殿遺跡", "정글 신전 유적", "Руины храма в джунглях", "丛林神庙遗迹"],
    "orman_ussu": ["Orman nehri karakolu", "Jungle river outpost", "موقع عند نهر الأدغال", "ジャングルの川の前哨基地", "정글 강변 초소", "Застава у реки в джунглях", "丛林河畔哨所"],
    "kiyi_siginak": ["Fırtınalı kıyı sığınağı", "Stormy coastal bunker", "ملجأ ساحلي عاصف", "嵐の海岸の要塞", "폭풍우 치는 해안 벙커", "Береговой бункер в шторм", "暴风雨中的海岸碉堡"],
    "kiyi_siginak_ton": ["Kıyı sığınağı (tramlı)", "Coastal bunker (screentone)", "ملجأ ساحلي (تظليل)", "海岸の要塞（トーン）", "해안 벙커 (스크린톤)", "Береговой бункер (растр)", "海岸碉堡（网点）"],
    "ucurum_kale": ["Uçurum kalesi", "Cliffside fortress", "قلعة على الجرف", "崖の上の城塞", "절벽 요새", "Крепость на утёсе", "悬崖要塞"],
    "vadi_karakol": ["Vadi gözetleme kulesi", "Valley watchtower", "برج مراقبة في الوادي", "谷の見張り塔", "계곡 감시탑", "Сторожевая вышка в долине", "山谷瞭望塔"],
    "fabrika": ["Terk edilmiş fabrika", "Abandoned factory", "مصنع مهجور", "廃工場", "버려진 공장", "Заброшенный завод", "废弃工厂"],
    "yikik_merdiven": ["Yıkık bina merdiveni", "Ruined stairwell", "درج مبنى مدمر", "崩れた建物の階段", "무너진 건물 계단", "Лестница в руинах", "废墟楼梯间"],
    "kutup_us": ["Kutup araştırma üssü", "Arctic research base", "قاعدة أبحاث قطبية", "極地研究基地", "극지 연구 기지", "Полярная исследовательская база", "极地科考站"],
    "deniz_feneri": ["Yıkık deniz feneri ve iskele", "Ruined lighthouse and pier", "منارة مهدمة ورصيف", "崩れた灯台と桟橋", "무너진 등대와 부두", "Разрушенный маяк и пирс", "废弃灯塔与码头"],
    "tropik_sahil": ["Tropik sahil karakolu", "Tropical beach outpost", "موقع على شاطئ استوائي", "南国の浜辺の前哨基地", "열대 해변 초소", "Застава на тропическом пляже", "热带海滩哨所"],
    "col_karakol": ["Çöl karakolu", "Desert outpost", "موقع صحراوي", "砂漠の前哨基地", "사막 초소", "Пустынная застава", "沙漠哨所"],
})
# stil sonekli kimlikler (aynı mekânın manga ve çizgi roman sürümleri)
for _k in list(BGN):
    if _k in ("orman_harabe", "orman_ussu", "ucurum_kale", "fabrika", "yikik_merdiven", "kutup_us", "tropik_sahil", "col_karakol"):
        BGN[_k + "_m"] = BGN[_k]
for _k in ("kiyi_siginak", "vadi_karakol", "deniz_feneri"):
    BGN[_k + "_m"] = BGN[_k]

BACKGROUNDS += [
    ("bg2_manga_00.jpeg", "orman_harabe_m", "manga", "orman"),
    ("bg2_manga_01.jpeg", "orman_ussu_m", "manga", "orman"),
    ("bg2_manga_02.jpeg", "kiyi_siginak_m", "manga", "sahil"),
    # neredeyse aynı ikinci kopya: siyah-beyaz tramlı manga sürümü
    ("bg2_manga_03.jpeg", "kiyi_siginak_ton", "manga", "sahil", "tone"),
    ("bg2_manga_04.jpeg", "ucurum_kale_m", "manga", "sahil"),
    ("bg2_manga_05.jpeg", "vadi_karakol_m", "manga", "doga_park"),
    ("bg2_manga_06.jpeg", "fabrika_m", "manga", "ic_mekan"),
    ("bg2_manga_07.jpeg", "yikik_merdiven_m", "manga", "yikim"),
    ("bg2_manga_08.jpeg", "kutup_us_m", "manga", "kis"),
    ("bg2_manga_09.jpeg", "deniz_feneri_m", "manga", "sahil"),
    ("bg2_manga_10.jpeg", "tropik_sahil_m", "manga", "sahil"),
    ("bg2_manga_11.jpeg", "col_karakol_m", "manga", "col"),
    ("bg2_comic_00.jpeg", "ucurum_kale", "cizgi_roman", "sahil"),
    ("bg2_comic_01.jpeg", "kutup_us", "cizgi_roman", "kis"),
    ("bg2_comic_02.jpeg", "orman_harabe", "cizgi_roman", "orman"),
    ("bg2_comic_03.jpeg", "tropik_sahil", "cizgi_roman", "sahil"),
    ("bg2_comic_04.jpeg", "fabrika", "cizgi_roman", "ic_mekan"),
    ("bg2_comic_05.jpeg", "yikik_merdiven", "cizgi_roman", "yikim"),
    ("bg2_comic_06.jpeg", "orman_ussu", "cizgi_roman", "orman"),
    ("bg2_comic_07.jpeg", "col_karakol", "cizgi_roman", "col"),
]

# --- v42 tema paketleri ---
# Yeni temalar (v40'ın 12 temasına ek)
THEMES += [
    ("macera", ["Keşif ve macera", "Exploration & adventure", "الاستكشاف والمغامرة", "探検・冒険", "탐험·모험", "Исследования и приключения", "探险与冒险"]),
    ("kiyamet", ["Kıyamet sonrası", "Post-apocalypse", "ما بعد نهاية العالم", "終末後", "종말 이후", "Постапокалипсис", "末日之后"]),
]

# Karakterlerin ana temasına ek tema bağlantıları (çoka-çok)
CHAR_EXTRA_THEMES = {
    "kasif_cem": ["macera"], "arkeolog_kemal": ["macera"], "korsan_kaptan": ["macera"], "kovboy_riza": ["macera"],
    "komando_baris": ["kiyamet"], "asker_mert": ["kiyamet"], "siborg_kaya": ["kiyamet"], "hacker_ozan": ["kiyamet"],
    "nisanci_tolga": ["kiyamet", "askeri"], "elf_okcu_silvan": ["macera"], "sovalye_aras": ["tarih"],
    "samuray_kenji": ["fantastik"], "samuray_aiko": ["fantastik"], "golge_ninja": ["fantastik"],
    "ogrenci_arda": ["gunluk"], "ogrenci_ece": ["gunluk"], "ege": ["okul"], "dedektif_selin": ["meslek"],
    "dedektif_cemal": ["meslek"], "polis_murat": ["meslek"], "doktor_emir": ["gunluk"], "doktor_ayse": ["gunluk"],
    "astronot_kaan": ["macera"], "astronot_atlas": ["macera"], "mucit_volkan": ["bilimkurgu"],
}

# Mekân arka planlarının temaları (stil sonekinden bağımsız; "_m"/"_ton" kimlikleri aynı temaları alır)
BG_THEMES = {
    "kar_nehir": ["gunluk"], "kar_nehir2": ["gunluk"],
    "okul_koridor": ["okul"], "okul_koridor2": ["okul"], "sinif": ["okul"], "okul_cati": ["okul", "gunluk"], "okul_cati2": ["okul"],
    "carsi_manga": ["gunluk"], "carsi": ["gunluk"],
    "yikik_sehir": ["kiyamet", "kahraman", "askeri"], "yikik_kopru": ["kiyamet"], "yikik_bina": ["kiyamet", "kahraman"],
    "sakura_sokak": ["gunluk", "okul"],
    "firtina_teras": ["kahraman", "gunluk"], "firtina_teras2": ["kahraman", "gunluk"],
    "yagmur_gece_manga": ["polisiye", "gunluk"], "yagmur_cadde": ["polisiye", "gunluk"], "yagmur_cadde_manga": ["polisiye", "gunluk"],
    "avlu": ["gunluk", "okul"], "avlu2": ["gunluk", "okul"],
    "kopru_alti": ["polisiye", "gunluk"], "kopru_alti2": ["polisiye", "gunluk"],
    "gunbatimi_cadde": ["gunluk"], "depo": ["polisiye", "askeri"],
    "nehir_park": ["gunluk", "spor"], "sonbahar_park": ["gunluk", "spor"], "sonbahar_park2": ["gunluk", "spor"],
    "sehir_manga": ["gunluk", "meslek"], "cati_teras": ["gunluk", "kahraman"], "cati_teras2": ["gunluk", "kahraman"],
    "neon_ara_sokak": ["bilimkurgu", "polisiye"], "neon_cadde": ["bilimkurgu", "polisiye"], "liman": ["gunluk", "polisiye", "meslek"],
    "orman_harabe": ["macera", "fantastik", "tarih"], "orman_ussu": ["askeri", "macera"],
    "kiyi_siginak": ["askeri", "tarih"], "ucurum_kale": ["tarih", "fantastik", "macera"], "vadi_karakol": ["askeri", "vahsibati"],
    "fabrika": ["kiyamet", "askeri", "steampunk"], "yikik_merdiven": ["kiyamet", "polisiye"],
    "kutup_us": ["bilimkurgu", "askeri", "macera"], "deniz_feneri": ["macera", "gunluk"],
    "tropik_sahil": ["macera", "askeri"], "col_karakol": ["askeri", "vahsibati", "kiyamet"],
}


def bg_themes(bid: str):
    base = bid
    for suf in ("_ton", "_m"):
        if base.endswith(suf):
            base = base[: -len(suf)]
            break
    return BG_THEMES.get(base) or BG_THEMES.get(bid) or ["gunluk"]


def char_themes(cid: str, primary: str):
    return [primary] + [t for t in CHAR_EXTRA_THEMES.get(cid, []) if t != primary]
