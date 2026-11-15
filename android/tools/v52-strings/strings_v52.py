#!/usr/bin/env python3
"""v52 metinleri (7 dil): android/app/src/main/res/values*/strings_v52.xml üretir.
Kullanım: python3 android/tools/v52-strings/strings_v52.py  (sonra iOS için ios/tools/strings-export/convert.py)"""
import os, sys
from xml.sax.saxutils import escape

LANGS = ["tr", "en", "ar", "ja", "ko", "ru", "zh"]
DIRS = {"tr": "values", "en": "values-en", "ar": "values-ar", "ja": "values-ja", "ko": "values-ko", "ru": "values-ru", "zh": "values-zh"}

S = {}
def s(key, tr, en, ar, ja, ko, ru, zh):
    S[key] = dict(tr=tr, en=en, ar=ar, ja=ja, ko=ko, ru=ru, zh=zh)

# --- Madde 2: basit resimde vektör düzenleme ayarları
s("v52_vector_fine_nodes", "Ayrıntılı noktalar", "Detailed points", "نقاط مفصلة", "詳細なポイント", "세부 점", "Подробные точки", "详细节点")
# --- Madde 4: tuval paylaşımı ve canlı yayın ayrı
s("v52_share_send_title", "Tuval paylaş", "Share canvas", "مشاركة اللوحة", "キャンバスを共有", "캔버스 공유", "Поделиться холстом", "分享画布")
s("v52_share_live_title", "Canlı tuval yayını", "Live canvas broadcast", "بث اللوحة المباشر", "キャンバスのライブ配信", "캔버스 라이브 방송", "Трансляция холста", "画布直播")
s("v52_share_send_how", "İki cihaz aynı Wi-Fi ağında olmalı. Uygulama açıkken cihazlar birbirini kendiliğinden bulur; bir cihaza tuval ya da resim gönderebilirsiniz.",
  "Both devices must be on the same Wi-Fi network. While the app is open they find each other automatically; you can send a canvas or an image to a device.",
  "يجب أن يكون الجهازان على شبكة Wi-Fi نفسها. أثناء فتح التطبيق يجد كل منهما الآخر تلقائيًا، ويمكنك إرسال لوحة أو صورة إلى جهاز.",
  "2台の端末が同じWi-Fiに接続している必要があります。アプリを開いている間は自動的に見つかり、キャンバスや画像を送信できます。",
  "두 기기가 같은 Wi-Fi에 있어야 합니다. 앱이 열려 있으면 서로를 자동으로 찾고, 캔버스나 이미지를 보낼 수 있습니다.",
  "Оба устройства должны быть в одной сети Wi-Fi. Пока приложение открыто, они находят друг друга сами; можно отправить холст или изображение.",
  "两台设备需连接同一 Wi-Fi。应用打开时会自动发现彼此，可以向设备发送画布或图片。")
s("v52_share_live_how", "İki cihaz aynı Wi-Fi ağında olmalı. Yayın başlattığınızda seçtiğiniz tuvalde yaptığınız her değişiklik karşı cihazda canlı görünür.",
  "Both devices must be on the same Wi-Fi network. When you start a broadcast, every change on the chosen canvas appears live on the other device.",
  "يجب أن يكون الجهازان على شبكة Wi-Fi نفسها. عند بدء البث يظهر كل تغيير على اللوحة المختارة مباشرة على الجهاز الآخر.",
  "2台の端末が同じWi-Fiに接続している必要があります。配信を始めると、選んだキャンバスの変更が相手の端末にライブで表示されます。",
  "두 기기가 같은 Wi-Fi에 있어야 합니다. 방송을 시작하면 선택한 캔버스의 모든 변경이 상대 기기에 실시간으로 보입니다.",
  "Оба устройства должны быть в одной сети Wi-Fi. Во время трансляции каждое изменение выбранного холста сразу видно на другом устройстве.",
  "两台设备需连接同一 Wi-Fi。开始直播后，所选画布上的每次修改都会实时显示在另一台设备上。")
s("v52_share_send_hint", "Aynı Wi-Fi ağındaki cihazlara (Android, iPhone, iPad) tuval ve resim gönderin", "Send canvases and images to devices on the same Wi-Fi (Android, iPhone, iPad)",
  "أرسل اللوحات والصور إلى الأجهزة على شبكة Wi-Fi نفسها (Android وiPhone وiPad)", "同じWi-Fiの端末（Android、iPhone、iPad）にキャンバスや画像を送信", "같은 Wi-Fi의 기기(Android, iPhone, iPad)로 캔버스와 이미지 보내기",
  "Отправляйте холсты и изображения на устройства в той же сети Wi-Fi (Android, iPhone, iPad)", "向同一 Wi-Fi 下的设备（Android、iPhone、iPad）发送画布和图片")
s("v52_share_send_open", "Paylaşım sayfasını aç", "Open sharing page", "فتح صفحة المشاركة", "共有ページを開く", "공유 페이지 열기", "Открыть страницу обмена", "打开分享页面")
s("v52_share_ask_hint", "Kapalıysa bağlı cihazlardan gelen tuval ve resimler sormadan alınır", "When off, canvases and images from connected devices are received without asking",
  "عند الإيقاف تُستقبل اللوحات والصور من الأجهزة المتصلة دون سؤال", "オフにすると、接続した端末からのキャンバスと画像を確認なしで受け取ります", "끄면 연결된 기기의 캔버스와 이미지를 묻지 않고 받습니다",
  "Если выключено, холсты и изображения с подключённых устройств принимаются без вопроса", "关闭后，将直接接收已连接设备发送的画布和图片")
s("v52_share_live_settings_title", "Yayın ayarları", "Broadcast settings", "إعدادات البث", "配信の設定", "방송 설정", "Настройки трансляции", "直播设置")
s("v52_share_live_hint", "Bir tuvali aynı Wi-Fi ağındaki cihazlara canlı yayınlayın ya da başkasının yayınını izleyin", "Broadcast a canvas live to devices on the same Wi-Fi or watch someone else's broadcast",
  "بث لوحة مباشرة إلى الأجهزة على شبكة Wi-Fi نفسها أو شاهد بث شخص آخر", "同じWi-Fiの端末にキャンバスをライブ配信したり、他の人の配信を見たりできます", "같은 Wi-Fi의 기기로 캔버스를 실시간 방송하거나 다른 사람의 방송을 보세요",
  "Транслируйте холст на устройства в той же сети Wi-Fi или смотрите чужую трансляцию", "向同一 Wi-Fi 下的设备直播画布，或观看他人的直播")
s("v52_share_live_open", "Yayın sayfasını aç", "Open broadcast page", "فتح صفحة البث", "配信ページを開く", "방송 페이지 열기", "Открыть страницу трансляции", "打开直播页面")
s("v52_live_ask", "Yayın davetlerini sor", "Ask before joining broadcasts", "السؤال قبل الانضمام إلى البث", "配信への参加前に確認", "방송 참여 전에 묻기", "Спрашивать перед подключением к трансляции", "加入直播前询问")
s("v52_live_ask_hint", "Kapalıysa bağlı cihazların başlattığı yayına sormadan katılınır", "When off, broadcasts started by connected devices are joined without asking",
  "عند الإيقاف يتم الانضمام إلى البث الذي تبدؤه الأجهزة المتصلة دون سؤال", "オフにすると、接続した端末が始めた配信に確認なしで参加します", "끄면 연결된 기기가 시작한 방송에 묻지 않고 참여합니다",
  "Если выключено, к трансляциям подключённых устройств вы подключаетесь без вопроса", "关闭后，将直接加入已连接设备开始的直播")
s("v52_live_auto_open", "Yayını kendiliğinden aç", "Open broadcast automatically", "فتح البث تلقائيًا", "配信を自動で開く", "방송 자동으로 열기", "Открывать трансляцию автоматически", "自动打开直播")
s("v52_live_auto_open_hint", "Kapalıysa katılınan yayın Canlı tuval yayını sayfasındaki karttan açılır", "When off, a joined broadcast is opened from the card on the Live canvas broadcast page",
  "عند الإيقاف يُفتح البث من البطاقة في صفحة بث اللوحة المباشر", "オフにすると、参加した配信はライブ配信ページのカードから開きます", "끄면 참여한 방송은 라이브 방송 페이지의 카드에서 엽니다",
  "Если выключено, трансляция открывается с карточки на странице трансляции", "关闭后，从画布直播页面的卡片打开已加入的直播")

# --- Madde 7: ana sayfadan alıştırmalara doğrudan geçiş
s("v52_home_practice", "Alıştırmalar", "Practices", "التمارين", "練習", "연습", "Упражнения", "练习")
s("v52_home_practice_simple_hint", "Basit çizimle adım adım çizim alıştırmaları", "Step-by-step drawing practices with simple drawing",
  "تمارين رسم خطوة بخطوة بالرسم البسيط", "シンプル描画でステップごとの練習", "간단한 그리기로 단계별 연습", "Пошаговые упражнения в простом рисовании", "用简单绘图逐步练习")
s("v52_home_practice_advanced_hint", "Gelişmiş çizimde tüm alıştırmalar", "All practices with advanced drawing",
  "جميع التمارين في الرسم المتقدم", "高度な描画ですべての練習", "고급 그리기의 모든 연습", "Все упражнения в расширенном рисовании", "高级绘图中的全部练习")

# --- Not almada şekli gelişmiş tuvalde düzenleme, yayına ara verme
s("v52_temp_shape_name", "Şekil düzenleme (geçici)", "Shape editing (temporary)", "تحرير الشكل (مؤقت)", "図形の編集（一時）", "도형 편집(임시)", "Редактирование фигуры (временно)", "编辑形状（临时）")
s("v52_temp_shape_hint", "Şekli gelişmiş çizim modunda düzenliyorsun. Kaydet'e dokununca tuvaldeki her şey nottaki şeklin yerine geçer; kaydetmeden çıkarsan değişiklikler atılır.",
  "You are editing the shape in advanced drawing mode. Tap Save to put everything on this canvas in place of the shape in your note; leave without saving to discard changes.",
  "أنت تحرر الشكل في وضع الرسم المتقدم. اضغط حفظ ليحل كل ما في هذه اللوحة محل الشكل في ملاحظتك؛ وإذا خرجت دون حفظ تُلغى التغييرات.",
  "高度な描画モードで図形を編集中です。保存をタップするとこのキャンバスの内容がノートの図形と置き換わります。保存せずに戻ると変更は破棄されます。",
  "고급 그리기 모드에서 도형을 편집 중입니다. 저장을 누르면 이 캔버스의 모든 것이 노트의 도형을 대신합니다. 저장하지 않고 나가면 변경이 취소됩니다.",
  "Вы редактируете фигуру в расширенном режиме. Нажмите «Сохранить», и всё на этом холсте заменит фигуру в заметке; без сохранения изменения отменятся.",
  "正在高级绘图模式中编辑形状。点“保存”后，此画布上的全部内容将替换笔记中的形状；不保存就退出则放弃更改。")
s("v52_temp_shape_save", "Kaydet ve nota aktar", "Save and return to note", "حفظ وإعادة إلى الملاحظة", "保存してノートに戻す", "저장하고 노트로 돌려보내기", "Сохранить и вернуть в заметку", "保存并放回笔记")
s("v52_temp_shape_saved", "Şekil nota aktarıldı", "Shape returned to the note", "أُعيد الشكل إلى الملاحظة", "図形をノートに戻しました", "도형을 노트로 돌려보냈습니다", "Фигура возвращена в заметку", "形状已放回笔记")
s("v52_temp_edit_failed", "Düzenlemeye açılamadı", "Couldn't open for editing", "تعذر الفتح للتحرير", "編集用に開けませんでした", "편집용으로 열 수 없습니다", "Не удалось открыть для редактирования", "无法打开进行编辑")
s("v52_live_paused", "Yayına ara verildi: gönderen düzenliyor", "Broadcast paused: the sender is editing", "البث متوقف مؤقتًا: المرسل يحرر", "配信を一時停止中：送信者が編集中", "방송 일시 중지: 보내는 사람이 편집 중", "Трансляция на паузе: отправитель редактирует", "直播已暂停：发送方正在编辑")
s("v52_live_paused_badge", "Yayına ara verildi", "Broadcast paused", "البث متوقف مؤقتًا", "配信を一時停止中", "방송 일시 중지", "Трансляция на паузе", "直播已暂停")

# --- Madde 11: yeni çizim yardımcıları
s("v52_helpers_title", "Çizim yardımcıları", "Drawing helpers", "مساعدات الرسم", "描画アシスト", "그리기 도우미", "Помощники рисования", "绘图辅助")
s("v52_helpers_short", "Yardımcılar", "Helpers", "المساعدات", "アシスト", "도우미", "Помощники", "辅助")
s("v52_helper_composition", "Kompozisyon kılavuzu", "Composition guide", "دليل التكوين", "構図ガイド", "구도 가이드", "Сетка композиции", "构图参考线")
s("v52_helper_composition_hint", "Ana öğeyi çizgilerin ve kesişim noktalarının üstüne yerleştir; kâğıda yazılmaz.", "Place the main subject on the lines and their crossings; not saved on the paper.",
  "ضع العنصر الرئيسي على الخطوط وتقاطعاتها؛ لا يُحفظ على الورقة.", "主役を線と交点に置きましょう。用紙には保存されません。", "주요 대상을 선과 교차점 위에 두세요. 종이에는 저장되지 않습니다.", "Размещайте главный объект на линиях и их пересечениях; на бумаге не сохраняется.", "把主体放在线及其交点上；不会保存到纸上。")
s("v52_helper_none", "Kapalı", "Off", "إيقاف", "オフ", "끔", "Выкл.", "关闭")
s("v52_helper_thirds", "Üçler kuralı", "Rule of thirds", "قاعدة الأثلاث", "三分割法", "3분할 법칙", "Правило третей", "三分法")
s("v52_helper_golden", "Altın oran", "Golden ratio", "النسبة الذهبية", "黄金比", "황금비", "Золотое сечение", "黄金比例")
s("v52_helper_diagonals", "Köşegenler", "Diagonals", "الأقطار", "対角線", "대각선", "Диагонали", "对角线")
s("v52_helper_center", "Merkez", "Center", "المركز", "中心", "중심", "Центр", "中心")
s("v52_helper_preview", "Kontrol önizlemesi", "Check preview", "معاينة التحقق", "チェック用プレビュー", "확인 미리보기", "Контрольный просмотр", "检查预览")
s("v52_helper_preview_hint", "Köşede küçük önizleme: çizimi farklı gözle görüp hataları yakala.", "A small preview in the corner: see your drawing with fresh eyes and catch mistakes.",
  "معاينة صغيرة في الزاوية: انظر إلى رسمك بعين جديدة واكتشف الأخطاء.", "隅の小さなプレビューで、新鮮な目で見て間違いを見つけます。", "모서리의 작은 미리보기로 새로운 눈으로 보고 실수를 찾으세요.", "Маленький просмотр в углу: посмотрите на рисунок свежим взглядом и найдите ошибки.", "角落的小预览：换个角度看画，发现错误。")
s("v52_helper_mirror", "Aynalı bakış", "Mirrored view", "عرض معكوس", "左右反転表示", "좌우 반전 보기", "Зеркальный вид", "镜像视图")
s("v52_helper_mirror_hint", "Yatay çevrilmiş görüntü oran ve simetri hatalarını belirginleştirir.", "A horizontally flipped view makes proportion and symmetry errors stand out.",
  "الصورة المقلوبة أفقيًا تُبرز أخطاء النسب والتماثل.", "左右反転するとバランスや対称のずれが目立ちます。", "좌우로 뒤집으면 비율과 대칭 오류가 잘 보입니다.", "Отражённое изображение выявляет ошибки пропорций и симметрии.", "水平翻转后，比例和对称错误会更明显。")
s("v52_helper_gray", "Gri ton (değer)", "Grayscale (values)", "تدرج رمادي (القيم)", "グレースケール（明暗）", "회색조(명암)", "Оттенки серого (тон)", "灰度（明暗）")
s("v52_helper_gray_hint", "Renkler olmadan açık-koyu dengesini kontrol et.", "Check the balance of lights and darks without colour.",
  "تحقق من توازن الفاتح والداكن دون ألوان.", "色を除いて明暗のバランスを確認します。", "색 없이 밝고 어두움의 균형을 확인하세요.", "Проверьте баланс светлого и тёмного без цвета.", "去掉颜色检查明暗平衡。")
s("v52_helper_preview_both", "Aynalı · gri ton", "Mirrored · grayscale", "معكوس · رمادي", "反転・グレー", "반전 · 회색조", "Зеркально · серый", "镜像 · 灰度")

s("v52_share_links_title", "Paylaşım ve canlı yayın", "Sharing and live broadcast", "المشاركة والبث المباشر", "共有とライブ配信", "공유와 실시간 방송", "Обмен и трансляция", "分享与直播")
s("v52_share_links_hint", "Aynı Wi-Fi'deki cihazlara tuval gönder ya da çizim, not ve çizgi roman tuvallerini canlı yayınla. Ayarları Bağlantı sekmesinde.",
  "Send canvases to devices on the same Wi-Fi or broadcast drawing, note and comic canvases live. Settings are in the Connection tab.",
  "أرسل اللوحات إلى الأجهزة على شبكة Wi-Fi نفسها أو بث لوحات الرسم والملاحظات والقصص المصورة مباشرة. الإعدادات في تبويب الاتصال.",
  "同じWi-Fiの端末にキャンバスを送ったり、描画・ノート・コミックのキャンバスをライブ配信したりできます。設定は「接続」タブにあります。",
  "같은 Wi-Fi의 기기로 캔버스를 보내거나 그림, 노트, 만화 캔버스를 실시간 방송하세요. 설정은 연결 탭에 있습니다.",
  "Отправляйте холсты на устройства в той же сети Wi-Fi или транслируйте холсты рисунков, заметок и комиксов. Настройки — во вкладке «Подключение».",
  "向同一 Wi-Fi 下的设备发送画布，或直播绘图、笔记和漫画画布。设置在“连接”标签中。")

# v52 grafik ve tablo
s("v52_add_graph", "Grafik ekle", "Add graph", "إضافة رسم بياني", "グラフを追加", "그래프 추가", "Добавить график", "添加图表")
s("v52_graph_short", "Grafik", "Graph", "رسم بياني", "グラフ", "그래프", "График", "图表")
s("v52_add_table", "Tablo ekle", "Add table", "إضافة جدول", "表を追加", "표 추가", "Добавить таблицу", "添加表格")
s("v52_table_short", "Tablo", "Table", "جدول", "表", "표", "Таблица", "表格")
s("v52_table_create_hint", "Satır ve sütun sayısını seç. Ekledikten sonra her hücreyi, her satırı, her sütunu ve tablonun tamamını düzenleyebilirsin.",
  "Choose the number of rows and columns. After adding, you can edit every cell, row, column and the whole table.",
  "اختر عدد الصفوف والأعمدة. بعد الإضافة يمكنك تعديل كل خلية وصف وعمود والجدول كله.",
  "行数と列数を選びます。追加後、各セル・各行・各列と表全体を編集できます。",
  "행과 열 수를 선택하세요. 추가한 뒤 모든 셀, 행, 열과 표 전체를 편집할 수 있습니다.",
  "Выберите число строк и столбцов. После добавления можно изменить каждую ячейку, строку, столбец и всю таблицу.",
  "选择行数和列数。添加后可以编辑每个单元格、每行、每列以及整个表格。")
s("v52_table_rows", "Satır", "Rows", "الصفوف", "行", "행", "Строки", "行")
s("v52_table_cols", "Sütun", "Columns", "الأعمدة", "列", "열", "Столбцы", "列")
s("v52_table_header_row", "Başlık satırı", "Header row", "صف العناوين", "見出し行", "머리글 행", "Строка заголовков", "标题行")
s("v52_table_header_col", "Başlık sütunu", "Header column", "عمود العناوين", "見出し列", "머리글 열", "Столбец заголовков", "标题列")
s("v52_table_zebra", "Dönüşümlü satır rengi", "Banded rows", "صفوف متناوبة الألوان", "縞模様の行", "줄무늬 행", "Чередование строк", "镶边行")
s("v52_table_borders", "Çizgiler", "Borders", "الحدود", "罫線", "테두리", "Границы", "边框")
s("v52_table_border_all", "Tüm çizgiler", "All borders", "كل الحدود", "すべての罫線", "모든 테두리", "Все границы", "所有边框")
s("v52_table_border_outer", "Dış çerçeve", "Outside only", "الإطار الخارجي", "外枠のみ", "바깥 테두리", "Только внешние", "仅外框")
s("v52_table_border_rows", "Yalnız satır çizgileri", "Row lines only", "خطوط الصفوف فقط", "行の線のみ", "행 선만", "Только линии строк", "仅行线")
s("v52_table_border_header", "Çerçeve ve başlık çizgisi", "Outside and header line", "الإطار وخط العناوين", "外枠と見出し線", "바깥 테두리와 머리글 선", "Внешние и под заголовком", "外框和标题线")
s("v52_table_border_none", "Çizgisiz", "No borders", "بلا حدود", "罫線なし", "테두리 없음", "Без границ", "无边框")
s("v52_table_style", "Görünüm", "Style", "النمط", "スタイル", "스타일", "Стиль", "样式")
s("v52_table_preset_classic", "Klasik", "Classic", "كلاسيكي", "クラシック", "클래식", "Классика", "经典")
s("v52_table_preset_blue", "Mavi", "Blue", "أزرق", "ブルー", "파랑", "Синий", "蓝色")
s("v52_table_preset_green", "Yeşil", "Green", "أخضر", "グリーン", "초록", "Зелёный", "绿色")
s("v52_table_preset_orange", "Turuncu", "Orange", "برتقالي", "オレンジ", "주황", "Оранжевый", "橙色")
s("v52_table_preset_purple", "Mor", "Purple", "بنفسجي", "パープル", "보라", "Фиолетовый", "紫色")
s("v52_table_preset_minimal", "Sade", "Minimal", "بسيط", "シンプル", "미니멀", "Минимализм", "简约")
s("v52_table_insert", "Tabloyu ekle", "Insert table", "إدراج الجدول", "表を挿入", "표 삽입", "Вставить таблицу", "插入表格")
s("v52_table_edit", "Tabloyu düzenle", "Edit table", "تعديل الجدول", "表を編集", "표 편집", "Изменить таблицу", "编辑表格")
s("v52_table_edit_hint", "Hücreye dokunup yaz. Satır numarasına ya da sütun harfine dokunarak o satırın ya da sütunun ayarlarını değiştir.",
  "Tap a cell to type. Tap a row number or column letter to change that row's or column's settings.",
  "اضغط على خلية للكتابة. اضغط على رقم الصف أو حرف العمود لتغيير إعداداته.",
  "セルをタップして入力します。行番号や列の文字をタップすると、その行・列の設定を変更できます。",
  "셀을 눌러 입력하세요. 행 번호나 열 문자를 누르면 그 행이나 열의 설정을 바꿀 수 있습니다.",
  "Нажмите на ячейку, чтобы писать. Нажмите на номер строки или букву столбца, чтобы изменить их настройки.",
  "点按单元格即可输入。点按行号或列字母可更改该行或该列的设置。")
s("v52_table_scope_all", "Hepsi", "All", "الكل", "すべて", "전체", "Все", "全部")
s("v52_table_scope_row", "Satır %1$d", "Row %1$d", "الصف %1$d", "%1$d 行目", "%1$d행", "Строка %1$d", "第 %1$d 行")
s("v52_table_scope_col", "Sütun %1$s", "Column %1$s", "العمود %1$s", "%1$s 列", "%1$s열", "Столбец %1$s", "%1$s 列")
s("v52_table_fill", "Dolgu rengi", "Fill color", "لون التعبئة", "塗りの色", "채우기 색", "Цвет заливки", "填充颜色")
s("v52_table_text_color", "Yazı rengi", "Text color", "لون النص", "文字の色", "글자 색", "Цвет текста", "文字颜色")
s("v52_table_border_color", "Çizgi rengi", "Border color", "لون الحدود", "罫線の色", "테두리 색", "Цвет границ", "边框颜色")
s("v52_table_header_fill", "Başlık rengi", "Header color", "لون العناوين", "見出しの色", "머리글 색", "Цвет заголовков", "标题颜色")
s("v52_table_header_text", "Başlık yazı rengi", "Header text color", "لون نص العناوين", "見出しの文字色", "머리글 글자 색", "Цвет текста заголовков", "标题文字颜色")
s("v52_table_zebra_fill", "İkinci satır rengi", "Band color", "لون الصف المتناوب", "縞の色", "줄무늬 색", "Цвет чередования", "镶边颜色")
s("v52_table_bg", "Hücre zemini", "Cell background", "خلفية الخلايا", "セルの背景", "셀 배경", "Фон ячеек", "单元格背景")
s("v52_table_bold", "Kalın yazı", "Bold text", "نص عريض", "太字", "굵은 글씨", "Жирный текст", "粗体")
s("v52_table_align", "Hizalama", "Alignment", "المحاذاة", "配置", "정렬", "Выравнивание", "对齐")
s("v52_table_align_l", "Sola", "Left", "يسار", "左", "왼쪽", "Слева", "左")
s("v52_table_align_c", "Ortaya", "Center", "وسط", "中央", "가운데", "По центру", "居中")
s("v52_table_align_r", "Sağa", "Right", "يمين", "右", "오른쪽", "Справа", "右")
s("v52_table_text_size", "Yazı boyu", "Text size", "حجم النص", "文字サイズ", "글자 크기", "Размер текста", "文字大小")
s("v52_table_height", "Yükseklik", "Height", "الارتفاع", "高さ", "높이", "Высота", "高度")
s("v52_table_width", "Genişlik", "Width", "العرض", "幅", "너비", "Ширина", "宽度")
s("v52_table_line_width", "Çizgi kalınlığı", "Border width", "سماكة الحدود", "罫線の太さ", "테두리 두께", "Толщина границ", "边框粗细")
s("v52_table_add_above", "Üste satır ekle", "Insert row above", "إدراج صف أعلى", "上に行を挿入", "위에 행 삽입", "Строка выше", "在上方插入行")
s("v52_table_add_below", "Alta satır ekle", "Insert row below", "إدراج صف أسفل", "下に行を挿入", "아래에 행 삽입", "Строка ниже", "在下方插入行")
s("v52_table_add_left", "Sola sütun ekle", "Insert column left", "إدراج عمود يسارًا", "左に列を挿入", "왼쪽에 열 삽입", "Столбец слева", "在左侧插入列")
s("v52_table_add_right", "Sağa sütun ekle", "Insert column right", "إدراج عمود يمينًا", "右に列を挿入", "오른쪽에 열 삽입", "Столбец справа", "在右侧插入列")
s("v52_table_delete_row", "Satırı sil", "Delete row", "حذف الصف", "行を削除", "행 삭제", "Удалить строку", "删除行")
s("v52_table_delete_col", "Sütunu sil", "Delete column", "حذف العمود", "列を削除", "열 삭제", "Удалить столбец", "删除列")
s("v52_table_clear", "Satırı/sütunu temizle", "Clear contents", "مسح المحتوى", "内容を消去", "내용 지우기", "Очистить", "清除内容")
s("v52_table_default", "Varsayılan", "Default", "الافتراضي", "既定", "기본값", "По умолчанию", "默认")
s("v52_table_reset_lines", "Satır ve sütun ayarlarını sıfırla", "Reset row and column settings", "إعادة ضبط إعدادات الصفوف والأعمدة", "行と列の設定をリセット", "행·열 설정 초기화", "Сбросить настройки строк и столбцов", "重置行列设置")

def xml_escape(t):
    t = escape(t).replace("'", "\\'").replace('"', '\\"')
    return t

def main():
    root = os.path.join(os.path.dirname(__file__), "..", "..", "app", "src", "main", "res")
    for lang in LANGS:
        d = os.path.join(root, DIRS[lang]); os.makedirs(d, exist_ok=True)
        with open(os.path.join(d, "strings_v52.xml"), "w", encoding="utf-8") as f:
            f.write('<?xml version="1.0" encoding="utf-8"?>\n<!-- v52 metinleri (android/tools/v52-strings/strings_v52.py ile üretilir) -->\n<resources>\n')
            for k, v in S.items():
                f.write(f'    <string name="{k}">{xml_escape(v[lang])}</string>\n')
            f.write('</resources>\n')
    print(len(S), "anahtar")

if __name__ == "__main__":
    main()
