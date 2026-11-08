# v36 koddaki Türkçe adların çevirileri: "tr|en|zh|ja|ru|ko|ar" (generate_v36.py tsv tablolarına yazar).
# Yalnız tabloda olmayan ya da yanlış anlama gelen adlar burada; var olan doğru çeviriler yinelenmez.

OBJECTS = """
Nesneler|Objects|物品|オブジェクト|Предметы|사물|أشياء
Günlük eşyalar|Everyday items|日常用品|日用品|Повседневные вещи|일상용품|أغراض يومية
Mobilya ve ev|Furniture and home|家具与家居|家具と家|Мебель и дом|가구와 집|الأثاث والمنزل
Yiyecek ve içecek|Food and drink|食物与饮料|食べ物と飲み物|Еда и напитки|음식과 음료|الطعام والشراب
Ulaşım|Transport|交通|乗り物|Транспорт|교통|المواصلات
Giyim ve aksesuar|Clothing and accessories|服饰与配件|服とアクセサリー|Одежда и аксессуары|의류와 액세서리|الملابس والإكسسوارات
Araç ve el aletleri|Tools and hand tools|工具与手工具|道具と工具|Инструменты|도구와 공구|الأدوات والعدد اليدوية
Fantastik|Fantasy|奇幻|ファンタジー|Фэнтези|판타지|خيالي
Dedektif ve polisiye|Detective and crime|侦探与犯罪|探偵とミステリー|Детектив и криминал|탐정과 범죄|التحري والجريمة
Bilim kurgu|Science fiction|科幻|SF|Научная фантастика|공상 과학|خيال علمي
Şehir ve yapı|City and buildings|城市与建筑|街と建物|Город и постройки|도시와 건물|المدينة والمباني
Müzik ve sanat|Music and art|音乐与艺术|音楽とアート|Музыка и искусство|음악과 미술|الموسيقى والفن
Anime|Anime|动漫|アニメ|Аниме|애니메|أنمي
Gerçekçi|Realistic|写实|リアル|Реалистичный|사실적|واقعي
Telefon|Phone|手机|スマートフォン|Телефон|휴대전화|هاتف
Tablet|Tablet|平板电脑|タブレット|Планшет|태블릿|جهاز لوحي
Bilgisayar|Computer|电脑|パソコン|Компьютер|컴퓨터|حاسوب
Kitap|Book|书|本|Книга|책|كتاب
Defter|Notebook|笔记本|ノート|Тетрадь|공책|دفتر
Kamera|Camera|相机|カメラ|Фотоаппарат|카메라|كاميرا
Çanta|Bag|包|バッグ|Сумка|가방|حقيبة
Valiz|Suitcase|行李箱|スーツケース|Чемодан|여행 가방|حقيبة سفر
Kask|Helmet|头盔|ヘルメット|Шлем|헬멧|خوذة
Maske|Mask|面具|仮面|Маска|가면|قناع
Pelerin|Cape|斗篷|マント|Плащ|망토|عباءة
Kolye|Necklace|项链|ネックレス|Ожерелье|목걸이|قلادة
Yüzük|Ring|戒指|指輪|Кольцо|반지|خاتم
Hançer|Dagger|匕首|短剣|Кинжал|단검|خنجر
Mızrak|Spear|长矛|槍|Копьё|창|رمح
Ok yayı|Bow|弓|弓|Лук|활|قوس
Asa|Staff|法杖|杖|Посох|지팡이|عصا
İksir|Potion|药水|ポーション|Зелье|물약|جرعة سحرية
Kristal küre|Crystal ball|水晶球|水晶玉|Хрустальный шар|수정 구슬|كرة بلورية
Altın sikke|Gold coin|金币|金貨|Золотая монета|금화|عملة ذهبية
Antik anahtar|Ancient key|古钥匙|古代の鍵|Старинный ключ|고대 열쇠|مفتاح قديم
Tılsım|Amulet|护身符|お守り|Амулет|부적|تميمة
Ejderha yumurtası|Dragon egg|龙蛋|ドラゴンの卵|Яйцо дракона|용의 알|بيضة تنين
Dürbün|Binoculars|双筒望远镜|双眼鏡|Бинокль|쌍안경|منظار
Parmak izi|Fingerprint|指纹|指紋|Отпечаток пальца|지문|بصمة إصبع
Delil poşeti|Evidence bag|证物袋|証拠品袋|Пакет для улик|증거물 봉투|كيس أدلة
Not defteri|Notepad|记事本|メモ帳|Блокнот|메모장|مفكرة
Gizli mektup|Secret letter|密信|秘密の手紙|Тайное письмо|비밀 편지|رسالة سرية
Kilit|Padlock|挂锁|南京錠|Замок|자물쇠|قفل
Çelik kasa|Safe|保险箱|金庫|Сейф|금고|خزنة
Kelepçe|Handcuffs|手铐|手錠|Наручники|수갑|أصفاد
Telsiz|Walkie-talkie|对讲机|トランシーバー|Рация|무전기|جهاز لاسلكي
Güvenlik kamerası|Security camera|监控摄像头|防犯カメラ|Камера наблюдения|보안 카메라|كاميرا مراقبة
Robot|Robot|机器人|ロボット|Робот|로봇|روبوت
Robot kolu|Robot arm|机械臂|ロボットアーム|Робот-манипулятор|로봇 팔|ذراع روبوتية
Drone|Drone|无人机|ドローン|Дрон|드론|طائرة مسيّرة
Hologram ekranı|Hologram screen|全息屏幕|ホログラム画面|Голографический экран|홀로그램 화면|شاشة هولوغرام
Enerji çekirdeği|Energy core|能量核心|エネルギーコア|Энергетическое ядро|에너지 코어|نواة طاقة
Lazer silahı|Laser gun|激光枪|レーザー銃|Лазерный пистолет|레이저 총|مسدس ليزر
Uzay kaskı|Space helmet|太空头盔|宇宙ヘルメット|Космический шлем|우주 헬멧|خوذة فضاء
Jetpack|Jetpack|喷气背包|ジェットパック|Реактивный ранец|제트팩|حقيبة نفاثة
Bilgisayar terminali|Computer terminal|计算机终端|コンピューター端末|Компьютерный терминал|컴퓨터 단말기|طرفية حاسوب
Devre kartı|Circuit board|电路板|回路基板|Печатная плата|회로 기판|لوحة دوائر
Çip|Chip|芯片|チップ|Чип|칩|شريحة
Uydu|Satellite|卫星|人工衛星|Спутник|인공위성|قمر صناعي
Portal cihazı|Portal device|传送门装置|ポータル装置|Портальное устройство|포털 장치|جهاز بوابة
Koltuk|Armchair|扶手椅|ひじ掛け椅子|Кресло|안락의자|كرسي بذراعين
Raf|Shelf|架子|棚|Полка|선반|رف
Lamba|Lamp|台灯|ランプ|Лампа|램프|مصباح
Ayna|Mirror|镜子|鏡|Зеркало|거울|مرآة
Buzdolabı|Refrigerator|冰箱|冷蔵庫|Холодильник|냉장고|ثلاجة
Fincan|Cup|杯子|カップ|Чашка|찻잔|فنجان
Tabak|Plate|盘子|皿|Тарелка|접시|طبق
Pizza|Pizza|披萨|ピザ|Пицца|피자|بيتزا
Hamburger|Hamburger|汉堡|ハンバーガー|Гамбургер|햄버거|همبرغر
Sandviç|Sandwich|三明治|サンドイッチ|Сэндвич|샌드위치|شطيرة
Pasta|Cake|蛋糕|ケーキ|Торт|케이크|كعكة
Süt şişesi|Milk bottle|牛奶瓶|牛乳瓶|Бутылка молока|우유병|زجاجة حليب
Kahve kupası|Coffee mug|咖啡杯|コーヒーマグ|Кофейная кружка|커피 머그|كوب قهوة
Kaya|Rock|岩石|岩|Скала|바위|صخرة
Yağmur damlası|Raindrop|雨滴|雨粒|Капля дождя|빗방울|قطرة مطر
Pense|Pliers|钳子|ペンチ|Плоскогубцы|펜치|كماشة
Testere|Saw|锯|のこぎり|Пила|톱|منشار
Seyyar merdiven|Ladder|梯子|はしご|Стремянка|사다리|سلم نقال
İp|Yarn|线|毛糸|Нитки|실|خيط
Halat|Rope|绳索|ロープ|Канат|밧줄|حبل
Boya fırçası|Paintbrush|画笔|絵筆|Кисть|붓|فرشاة رسم
Boya kovası|Paint bucket|油漆桶|ペンキ缶|Ведро краски|페인트 통|دلو طلاء
Futbol kalesi|Soccer goal|足球门|サッカーゴール|Футбольные ворота|축구 골대|مرمى كرة القدم
Pota|Basketball hoop|篮筐|バスケットゴール|Баскетбольное кольцо|농구 골대|سلة كرة السلة
Paten|Roller skate|旱冰鞋|ローラースケート|Ролик|롤러스케이트|حذاء تزلج
Satranç tahtası|Chessboard|棋盘|チェス盤|Шахматная доска|체스판|رقعة شطرنج
Zar|Dice|骰子|サイコロ|Игральный кубик|주사위|نرد
Oyun kumandası|Game controller|游戏手柄|ゲームコントローラー|Геймпад|게임 컨트롤러|يد تحكم
Ödül kupası|Trophy|奖杯|トロフィー|Кубок|트로피|كأس الجائزة
Piyano|Piano|钢琴|ピアノ|Пианино|피아노|بيانو
Keman|Violin|小提琴|バイオリン|Скрипка|바이올린|كمان
Boya paleti|Paint palette|调色板|パレット|Палитра|팔레트|لوحة ألوان
Boya tüpü|Paint tube|颜料管|絵の具チューブ|Тюбик краски|물감 튜브|أنبوب ألوان
Şövale|Easel|画架|イーゼル|Мольберт|이젤|حامل رسم
Otomobil|Car|汽车|自動車|Автомобиль|자동차|سيارة
Taksi|Taxi|出租车|タクシー|Такси|택시|سيارة أجرة
Gemi|Ship|轮船|船|Корабль|배|سفينة
Sokak lambası|Street lamp|路灯|街灯|Уличный фонарь|가로등|مصباح شارع
Çöp kutusu|Trash can|垃圾桶|ゴミ箱|Мусорный бак|쓰레기통|سلة مهملات
Sokak tabelası|Street sign|路牌|道路標識|Уличный указатель|도로 표지판|لافتة شارع
Bariyer|Barrier|路障|バリケード|Барьер|차단기|حاجز
Yangın musluğu|Fire hydrant|消防栓|消火栓|Пожарный гидрант|소화전|صنبور إطفاء
Otomat|Vending machine|自动售货机|自動販売機|Торговый автомат|자판기|آلة بيع
Reklam panosu|Billboard|广告牌|看板|Рекламный щит|광고판|لوحة إعلانات
"""

EFFECTS = """
Aksiyon|Action|动作|アクション|Экшен|액션|حركة
Büyü ve enerji|Magic and energy|魔法与能量|魔法とエネルギー|Магия и энергия|마법과 에너지|سحر وطاقة
Duygu|Emotion|情绪|感情|Эмоции|감정|مشاعر
Atmosfer|Atmosphere|氛围|雰囲気|Атмосфера|분위기|أجواء
Ses|Sound|音效|音|Звук|소리|صوت
Darbe|Impact|冲击|衝撃|Удар|충격|صدمة
Çarpışma|Collision|碰撞|衝突|Столкновение|충돌|اصطدام
Patlama|Explosion|爆炸|爆発|Взрыв|폭발|انفجار
Parçalanma|Shatter|碎裂|粉砕|Осколки|산산조각|تحطم
Toz bulutu|Dust cloud|尘云|土煙|Облако пыли|먼지 구름|سحابة غبار
Yumruk hareket izi|Punch trail|拳头轨迹|パンチの軌跡|След удара|주먹 궤적|أثر اللكمة
Alev|Flame|火焰|炎|Пламя|불꽃|لهب
Enerji aurası|Energy aura|能量光环|エネルギーオーラ|Энергетическая аура|에너지 오라|هالة طاقة
Büyü çemberi|Magic circle|魔法阵|魔法陣|Магический круг|마법진|دائرة سحرية
Işık huzmesi|Light beam|光束|光の筋|Луч света|빛줄기|شعاع ضوء
Portal parıltısı|Portal glow|传送门光辉|ポータルの輝き|Сияние портала|포털의 빛|توهج البوابة
Gözyaşları|Tears|泪水|涙|Слёзы|눈물|دموع
Ter damlaları|Sweat drops|汗滴|汗|Капли пота|땀방울|قطرات عرق
Öfke işareti|Anger mark|生气符号|怒りマーク|Знак злости|분노 표시|علامة غضب
Titreme çizgileri|Tremble lines|颤抖线|震え線|Линии дрожи|떨림 선|خطوط ارتجاف
Yağmur|Rain|雨|雨|Дождь|비|مطر
Kar|Snow|雪|雪|Снег|눈|ثلج
Sis|Fog|雾|霧|Туман|안개|ضباب
Duman|Smoke|烟|煙|Дым|연기|دخان
Kıvılcımlar|Sparks|火花|火花|Искры|불티|شرر
Parlama|Glow|光芒|輝き|Сияние|광채|وهج
Noktacık dokusu|Halftone dots|网点纹理|網点|Растровые точки|망점 무늬|نقاط نصف لونية
"""

SCENES = """
Sınıf|Classroom|教室|教室|Класс|교실|فصل دراسي
Okul|School|学校|学校|Школа|학교|مدرسة
Ofis|Office|办公室|オフィス|Офис|사무실|مكتب
Kafe|Cafe|咖啡馆|カフェ|Кафе|카페|مقهى
Hastane|Hospital|医院|病院|Больница|병원|مستشفى
Park|Park|公园|公園|Парк|공원|حديقة
Şehir|City|城市|街|Город|도시|مدينة
Sokak|Street|街道|通り|Улица|거리|شارع
Gece şehri|City at night|夜晚城市|夜の街|Ночной город|밤의 도시|المدينة ليلًا
Neon sokak|Neon street|霓虹街道|ネオン街|Неоновая улица|네온 거리|شارع النيون
Laboratuvar|Laboratory|实验室|研究室|Лаборатория|실험실|مختبر
Uzay gemisi|Spaceship|宇宙飞船|宇宙船|Космический корабль|우주선|سفينة فضاء
Yabancı gezegen|Alien planet|外星球|異星|Чужая планета|외계 행성|كوكب غريب
Büyülü orman|Enchanted forest|魔法森林|魔法の森|Волшебный лес|마법의 숲|غابة مسحورة
Mağara|Cave|洞穴|洞窟|Пещера|동굴|كهف
Zindan|Dungeon|地牢|地下牢|Подземелье|지하 감옥|زنزانة
Terk edilmiş ev|Abandoned house|废弃房屋|廃屋|Заброшенный дом|버려진 집|منزل مهجور
Karanlık koridor|Dark corridor|黑暗走廊|暗い廊下|Тёмный коридор|어두운 복도|ممر مظلم
Ev ve okul|Home and school|家与学校|家と学校|Дом и школа|집과 학교|المنزل والمدرسة
Şehir ve doğa|City and nature|城市与自然|街と自然|Город и природа|도시와 자연|المدينة والطبيعة
Gizem ve korku|Mystery and horror|悬疑与恐怖|ミステリーとホラー|Тайна и ужасы|미스터리와 공포|غموض ورعب
Gündüz|Day|白天|昼|День|낮|نهار
Gece|Night|夜晚|夜|Ночь|밤|ليل
Yağmurlu|Rainy|雨天|雨|Дождь|비|ممطر
Karlı|Snowy|雪天|雪|Снег|눈|مثلج
Sisli|Foggy|雾天|霧|Туман|안개|ضبابي
"""


CHARACTERS = """
Öğrenci|Student|学生|学生|Ученик|학생|طالب
Ofis çalışanı|Office worker|上班族|会社員|Офисный работник|회사원|موظف مكتب
Yaşlı|Elder|老人|お年寄り|Пожилой|노인|مسن
Öğretmen|Teacher|老师|先生|Учитель|선생님|معلم
Doktor|Doctor|医生|医者|Врач|의사|طبيب
Polis|Police officer|警察|警察官|Полицейский|경찰|شرطي
Dedektif|Detective|侦探|探偵|Детектив|탐정|محقق
Aşçı|Chef|厨师|シェフ|Повар|요리사|طاهٍ
Sporcu|Athlete|运动员|アスリート|Спортсмен|운동선수|رياضي
Süper kahraman|Superhero|超级英雄|スーパーヒーロー|Супергерой|슈퍼히어로|بطل خارق
Sakin|Calm|平静|落ち着き|Спокойный|차분함|هادئ
Mutlu|Happy|开心|うれしい|Радостный|행복|سعيد
Üzgün|Sad|难过|悲しい|Грустный|슬픔|حزين
Kızgın|Angry|生气|怒り|Сердитый|화남|غاضب
Şaşkın|Surprised|惊讶|驚き|Удивлённый|놀람|مندهش
Korkmuş|Scared|害怕|怖がり|Испуганный|겁먹음|خائف
Düşünceli|Thoughtful|沉思|考え中|Задумчивый|생각 중|متأمل
Utangaç|Shy|害羞|照れ|Смущённый|수줍음|خجول
Ayakta|Standing|站立|立つ|Стоит|서 있기|واقف
Yürüyor|Walking|走路|歩く|Идёт|걷기|يمشي
Koşuyor|Running|奔跑|走る|Бежит|달리기|يركض
Oturuyor|Sitting|坐着|座る|Сидит|앉기|جالس
El sallıyor|Waving|挥手|手を振る|Машет рукой|손 흔들기|يلوّح
İşaret ediyor|Pointing|指着|指さす|Указывает|가리키기|يشير
Kollar kavuşturmuş|Arms crossed|双臂交叉|腕組み|Скрестил руки|팔짱 끼기|ذراعان متشابكتان
Zıplıyor|Jumping|跳跃|ジャンプ|Прыгает|점프|يقفز
Düşünüyor|Thinking|思考|考える|Думает|생각하기|يفكر
"""


def rows(block):
    out = []
    for raw in block.strip().split("\n"):
        parts = raw.split("|")
        if len(parts) != 7:
            raise SystemExit(f"eksik çeviri: {raw}")
        out.append(tuple(parts))
    return out


# v36 şablon adları ve grupları (Temel, Aksiyon, Fantastik, Bilim kurgu, Manga önceden çevrili)
TEMPLATES = """
Webtoon|Webtoon|条漫|縦読みマンガ|Вебтун|웹툰|ويبتون
Türler|Genres|类型|ジャンル|Жанры|장르|الأنواع
Tek büyük panel|Single large panel|单个大分格|大きな1コマ|Одна большая панель|큰 컷 하나|لوحة كبيرة واحدة
İki panel (üst üste)|Two panels (stacked)|两格（上下）|2コマ（上下）|Две панели (друг над другом)|두 컷 (위아래)|لوحتان (فوق بعض)
İki panel (yan yana)|Two panels (side by side)|两格（左右）|2コマ（左右）|Две панели (рядом)|두 컷 (나란히)|لوحتان (جنبًا إلى جنب)
Üç panel (şerit)|Three panels (strips)|三格（横条）|3コマ（帯）|Три панели (полосы)|세 컷 (가로띠)|ثلاث لوحات (شرائط)
Üç panel (büyük üstte)|Three panels (large on top)|三格（上方大格）|3コマ（上が大）|Три панели (большая сверху)|세 컷 (위에 큰 컷)|ثلاث لوحات (الكبيرة في الأعلى)
Üç panel (gazete şeridi)|Three panels (newspaper strip)|三格（报纸条漫）|3コマ（新聞連載）|Три панели (газетный стрип)|세 컷 (신문 연재)|ثلاث لوحات (شريط صحيفة)
Dört panel (ızgara)|Four panels (grid)|四格（网格）|4コマ（格子）|Четыре панели (сетка)|네 컷 (격자)|أربع لوحات (شبكة)
Dört panel (geniş alt)|Four panels (wide bottom)|四格（下方宽格）|4コマ（下が横長）|Четыре панели (широкая снизу)|네 컷 (아래 넓게)|أربع لوحات (عريضة في الأسفل)
Altı panel (ızgara)|Six panels (grid)|六格（网格）|6コマ（格子）|Шесть панелей (сетка)|여섯 컷 (격자)|ست لوحات (شبكة)
Altı panel (karışık)|Six panels (mixed)|六格（混合）|6コマ（ミックス）|Шесть панелей (смешанные)|여섯 컷 (혼합)|ست لوحات (مختلطة)
Dikey webtoon|Vertical webtoon|竖版条漫|縦スクロール|Вертикальный вебтун|세로 웹툰|ويبتون عمودي
Webtoon geniş sahne|Webtoon wide scene|条漫宽场景|縦読み ワイドシーン|Вебтун: широкая сцена|웹툰 와이드 장면|ويبتون: مشهد عريض
Webtoon akış|Webtoon flow|条漫节奏|縦読み 流れ|Вебтун: поток|웹툰 흐름|ويبتون: تدفّق
Manga klasik|Classic manga|经典日漫|クラシック漫画|Классическая манга|클래식 망가|مانغا كلاسيكية
Manga aksiyon|Action manga|日漫动作|アクション漫画|Манга-экшен|액션 망가|مانغا حركة
4-koma (yonkoma)|Yonkoma (4-panel strip)|四格漫画|4コマ漫画|Ёнкома (4 кадра)|4컷 만화 (욘코마)|يونكوما (4 لوحات)
Komedi|Comedy|喜剧|コメディ|Комедия|코미디|كوميديا
Romantizm|Romance|爱情|恋愛|Романтика|로맨스|رومانسية
Korku|Horror|恐怖|ホラー|Ужасы|공포|رعب
"""

def entries():
    return rows(OBJECTS) + rows(EFFECTS) + rows(SCENES) + rows(CHARACTERS) + rows(TEMPLATES)
