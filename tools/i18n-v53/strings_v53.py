#!/usr/bin/env python3
"""v53 metinleri: tek tablodan 7 dilin Android kaynakları (values*/strings_v53.xml) üretilir.

iOS tabloları bunlardan ios/tools/strings-export/convert.py ile çıkarılır.
Kullanım: python3 strings_v53.py <android/app/src/main/res>
"""
import os
import sys

LANGS = ["tr", "en", "ar", "ja", "ko", "ru", "zh"]
DIRS = {"tr": "values", "en": "values-en", "ar": "values-ar", "ja": "values-ja", "ko": "values-ko", "ru": "values-ru", "zh": "values-zh"}

S = {
    # v53 araç yardımı (basılı tutunca açıklama)
    "v53_help_structure": ["Canlı, nesne ve eşyaların çizim yardımcısını (iskelet, orantı ve perspektif çizgileri) tuvale yerleştirir; üzerinden çizersin.", "Places a structure guide (skeleton and proportion lines) for living things and objects on the canvas so you can draw over it.", "يضع دليل بنية (هيكل وخطوط تناسب) للكائنات والأشياء على اللوحة لترسم فوقه.", "生き物や物の構造ガイド（骨格・比率線）をキャンバスに置き、その上に描けます。", "생물과 사물의 구조 안내선(뼈대, 비율선)을 캔버스에 놓아 그 위에 그릴 수 있습니다.", "Размещает на холсте направляющую структуры (каркас и линии пропорций) для существ и предметов, чтобы рисовать поверх.", "在画布上放置生物和物体的结构参考（骨架、比例线），可在其上作画。"],
    "v53_help_quick_text": ["Hızlı not kutusu açar: klavyeyle yaz, bitince arka plansız yazı olur ve hemen yenisi açılır.", "Opens a quick note box: type with the keyboard; when done it becomes plain text and a new box opens.", "يفتح مربع ملاحظة سريعة: اكتب بلوحة المفاتيح، وعند الانتهاء يصبح نصًا بلا خلفية ويفتح مربع جديد.", "クイックメモ欄を開きます。キーボードで入力し、完了すると背景のない文字になり、次の欄が開きます。", "빠른 메모 상자를 엽니다. 키보드로 입력하고 끝나면 배경 없는 글자가 되며 새 상자가 열립니다.", "Открывает поле быстрой заметки: печатайте, по завершении текст остаётся без фона и открывается новое поле.", "打开快速笔记框：用键盘输入，完成后变为无背景文字，并自动打开新框。"],
    "v53_help_patterns": ["Çizgi roman desenleri (çiçek, parıltı, kasvet çizgileri…) ekler ya da seçili kapalı şekli onunla doldurur.", "Adds comic patterns (flowers, sparkles, gloom lines…) or fills the selected closed shape with one.", "يضيف أنماط قصص مصورة (زهور، بريق، خطوط كآبة…) أو يملأ الشكل المغلق المحدد بأحدها.", "漫画の模様（花、キラキラ、縦線…）を追加するか、選択した閉じた図形を塗ります。", "만화 무늬(꽃, 반짝임, 우울 선…)를 추가하거나 선택한 닫힌 도형을 채웁니다.", "Добавляет комиксные узоры (цветы, блёстки, линии уныния…) или заливает ими выбранную замкнутую фигуру.", "添加漫画图案（花朵、闪光、阴郁线…）或用其填充所选闭合形状。"],
    "v53_help_tones": ["Ekran tonu (nokta, çizgi, tarama) ekler ya da seçili kapalı şekle gölge tonu verir; sıklık, ölçek ve açı ayarlanır.", "Adds a screentone (dots, lines, hatching) or tones the selected closed shape; density, scale and angle can be set.", "يضيف تدرجًا شبكيًا (نقاط، خطوط، تظليل) أو يظلل الشكل المغلق المحدد؛ يمكن ضبط الكثافة والحجم والزاوية.", "スクリーントーン（網点・線・カケアミ）を追加するか、選択した閉じた図形に貼ります。濃さ・大きさ・角度を調整できます。", "스크린톤(망점, 선, 해칭)을 추가하거나 선택한 닫힌 도형에 톤을 줍니다. 밀도, 크기, 각도를 조정합니다.", "Добавляет скринтон (точки, линии, штриховка) или тонирует выбранную замкнутую фигуру; настраиваются плотность, масштаб и угол.", "添加网点（点、线、排线）或为所选闭合形状加网点；可调整密度、比例和角度。"],
    "v53_help_lines": ["Hız, odak ve patlama çizgileri ekler; seçili kare varsa onun içine.", "Adds speed, focus and burst lines; inside the selected panel if there is one.", "يضيف خطوط سرعة وتركيز وانفجار؛ داخل اللوحة المحددة إن وُجدت.", "スピード線・集中線・爆発線を追加します。コマを選択していればその中に。", "속도선, 집중선, 폭발선을 추가합니다. 선택한 칸이 있으면 그 안에 넣습니다.", "Добавляет линии скорости, фокуса и взрыва; внутрь выбранного кадра, если он есть.", "添加速度线、集中线和爆炸线；若选中分格则加在其中。"],
    # Başlık ve menüler
    "v53_save": ["Kaydet", "Save", "حفظ", "保存", "저장", "Сохранить", "保存"],
    "v53_draw3d": ["3B çizim", "3D drawing", "رسم ثلاثي الأبعاد", "3D描画", "3D 그리기", "3D-рисование", "3D绘图"],
    "v53_ungroup": ["Grubu çöz", "Ungroup", "فك التجميع", "グループ解除", "그룹 해제", "Разгруппировать", "取消分组"],
    "v53_edit_selected": ["Seçileni düzenle", "Edit selected", "تعديل المحدد", "選択を編集", "선택 편집", "Изменить выбранное", "编辑所选"],
    "v53_done": ["Bitti", "Done", "تم", "完了", "완료", "Готово", "完成"],
    "v53_reset": ["Sıfırla", "Reset", "إعادة ضبط", "リセット", "초기화", "Сбросить", "重置"],
    "v53_cancel": ["Vazgeç", "Cancel", "إلغاء", "キャンセル", "취소", "Отмена", "取消"],
    "v53_change": ["Değiştir", "Change", "تغيير", "変更", "변경", "Изменить", "更改"],
    # Ayar şeridi ipuçları
    "v53_hint_eyedropper": ["Rengini almak için tuvalde bir yere dokun.", "Tap the canvas to pick a colour.", "المس اللوحة لالتقاط لون.", "キャンバスをタップして色を取得します。", "캔버스를 눌러 색을 가져오세요.", "Коснитесь холста, чтобы взять цвет.", "点击画布取色。"],
    "v53_hint_pen": ["Dokunarak köşe, sürükleyerek eğri ekle. İlk noktaya dokununca kapanır.", "Tap for corners, drag for curves. Tap the first point to close.", "المس لإضافة زاوية واسحب لإضافة منحنى. المس النقطة الأولى للإغلاق.", "タップで角、ドラッグで曲線。最初の点をタップで閉じます。", "눌러서 모서리, 끌어서 곡선. 첫 점을 누르면 닫힙니다.", "Касание — угол, перетаскивание — кривая. Коснитесь первой точки, чтобы замкнуть.", "点按添加角点，拖动添加曲线。点按第一个点闭合。"],
    "v53_hint_paint": ["Boyamak için bir şeklin içine dokun.", "Tap inside a shape to paint it.", "المس داخل شكل لتلوينه.", "図形の内側をタップして塗ります。", "도형 안을 눌러 칠하세요.", "Коснитесь внутри фигуры, чтобы закрасить.", "点击形状内部进行填色。"],
    "v53_hint_area_select": ["Seçmek için parmağınla alanı çiz.", "Draw around the area to select it.", "ارسم حول المنطقة لتحديدها.", "範囲を指でなぞって選択します。", "선택할 영역을 손가락으로 그리세요.", "Обведите область, чтобы выделить.", "用手指圈出要选择的区域。"],
    "v53_hint_crop": ["Kenarları sürükleyerek kırpma alanını ayarla.", "Drag the edges to set the crop.", "اسحب الحواف لضبط القص.", "端をドラッグして切り抜き範囲を決めます。", "가장자리를 끌어 자를 영역을 정하세요.", "Перетаскивайте края, чтобы задать обрезку.", "拖动边缘设置裁剪范围。"],
    "v53_hint_text": ["Yazmak istediğin yere dokun.", "Tap where you want to type.", "المس المكان الذي تريد الكتابة فيه.", "入力したい場所をタップします。", "입력할 곳을 누르세요.", "Коснитесь места, где хотите писать.", "点击要输入文字的位置。"],
    "v53_hint_add": ["Üst menüden eklenecek öğeyi seç ya da tuvale dokun.", "Choose an item above or tap the canvas.", "اختر عنصرًا من الأعلى أو المس اللوحة.", "上のメニューから選ぶか、キャンバスをタップします。", "위에서 항목을 고르거나 캔버스를 누르세요.", "Выберите элемент выше или коснитесь холста.", "从上方选择要添加的项目，或点击画布。"],
    "v53_hint_shape": ["Şekli yerleştirmek için tuvale dokun ya da sürükle.", "Tap or drag on the canvas to place the shape.", "المس اللوحة أو اسحب لوضع الشكل.", "タップまたはドラッグして図形を配置します。", "캔버스를 누르거나 끌어 도형을 놓으세요.", "Коснитесь или потяните по холсту, чтобы разместить фигуру.", "点击或拖动以放置形状。"],
    "v53_hint_diagram": ["Diyagramı yerleştirmek için tuvale dokun ya da sürükle.", "Tap or drag to place the diagram.", "المس أو اسحب لوضع المخطط.", "タップまたはドラッグして図を配置します。", "눌러서 또는 끌어서 다이어그램을 놓으세요.", "Коснитесь или потяните, чтобы разместить диаграмму.", "点击或拖动放置图表。"],
    "v53_hint_formula": ["Formülü yerleştirmek için tuvale dokun.", "Tap the canvas to place the formula.", "المس اللوحة لوضع الصيغة.", "キャンバスをタップして数式を配置します。", "캔버스를 눌러 수식을 놓으세요.", "Коснитесь холста, чтобы разместить формулу.", "点击画布放置公式。"],
    "v53_hint_guide": ["Kılavuzu ayarla, bitince Bitti'ye dokun.", "Adjust the guide, then tap Done.", "اضبط الدليل ثم المس تم.", "ガイドを調整して完了をタップします。", "안내선을 조정한 뒤 완료를 누르세요.", "Настройте направляющие и нажмите «Готово».", "调整参考线后点击完成。"],
    "v53_hint_expand": ["Kâğıdı genişletmek için kenarları sürükle.", "Drag the edges to enlarge the paper.", "اسحب الحواف لتوسيع الورقة.", "端をドラッグして用紙を広げます。", "가장자리를 끌어 종이를 넓히세요.", "Тяните края, чтобы увеличить лист.", "拖动边缘扩大纸张。"],
    "v53_hint_move": ["Seçileni sürükleyerek taşı.", "Drag to move the selection.", "اسحب لنقل المحدد.", "ドラッグして選択を移動します。", "끌어서 선택 항목을 옮기세요.", "Перетащите, чтобы переместить выделение.", "拖动以移动所选内容。"],
    "v53_hint_rotate": ["Döndürmek için sürükle.", "Drag to rotate.", "اسحب للتدوير.", "ドラッグして回転します。", "끌어서 회전하세요.", "Тяните, чтобы повернуть.", "拖动以旋转。"],
    "v53_hint_scale": ["Ölçeklemek için tutamakları sürükle.", "Drag the handles to scale.", "اسحب المقابض لتغيير الحجم.", "ハンドルをドラッグして拡大縮小します。", "핸들을 끌어 크기를 바꾸세요.", "Тяните маркеры, чтобы масштабировать.", "拖动控制点缩放。"],
    "v53_hint_image_edit": ["Resim için bir araç seç.", "Choose a tool for the image.", "اختر أداة للصورة.", "画像のツールを選びます。", "이미지 도구를 고르세요.", "Выберите инструмент для изображения.", "为图片选择工具。"],
    "v53_hint_diagram_edit": ["Diyagramın bir parçasına dokunarak düzenle.", "Tap a part of the diagram to edit it.", "المس جزءًا من المخطط لتعديله.", "図の部分をタップして編集します。", "다이어그램의 부분을 눌러 편집하세요.", "Коснитесь части диаграммы, чтобы изменить.", "点击图表的部分进行编辑。"],
    "v53_hint_edit_tool": ["Bu araçla düzenlenecek şekle dokun.", "Tap the shape to edit with this tool.", "المس الشكل لتعديله بهذه الأداة.", "このツールで編集する図形をタップします。", "이 도구로 편집할 도형을 누르세요.", "Коснитесь фигуры для правки этим инструментом.", "点击要用此工具编辑的形状。"],
    "v53_hint_edit_pick": ["Düzenlemek istediğin elemana dokun.", "Tap the element you want to edit.", "المس العنصر الذي تريد تعديله.", "編集したい要素をタップします。", "편집할 요소를 누르세요.", "Коснитесь элемента, который хотите изменить.", "点击要编辑的元素。"],
    # Panel
    "v53_group_n": ["Grup %1$d", "Group %1$d", "مجموعة %1$d", "グループ %1$d", "그룹 %1$d", "Группа %1$d", "组 %1$d"],
    "v53_layer_add": ["Katman ekle", "Add layer", "إضافة طبقة", "レイヤーを追加", "레이어 추가", "Добавить слой", "添加图层"],
    "v53_layer_move_here": ["Seçileni bu katmana taşı", "Move selection to this layer", "نقل المحدد إلى هذه الطبقة", "選択をこのレイヤーへ移動", "선택 항목을 이 레이어로 이동", "Переместить выделение в этот слой", "将所选内容移到此图层"],
    "v53_properties_empty": ["Bu aracın ayarı yok. Bir eleman seçince ya da fırça, silgi veya yazı aracı seçiliyken ayarları burada görünür.", "This tool has no settings. Select an element, or pick the brush, eraser or text tool to see settings here.", "لا إعدادات لهذه الأداة. حدّد عنصرًا أو اختر الفرشاة أو الممحاة أو أداة النص لرؤية الإعدادات هنا.", "このツールには設定がありません。要素を選ぶか、ブラシ・消しゴム・文字ツールを選ぶとここに設定が表示されます。", "이 도구에는 설정이 없습니다. 요소를 선택하거나 브러시, 지우개, 글자 도구를 고르면 여기에 설정이 보입니다.", "У этого инструмента нет настроек. Выберите элемент или кисть, ластик или текст — настройки появятся здесь.", "此工具没有设置。选择元素，或选择画笔、橡皮擦或文字工具，即可在此看到设置。"],
    "v53_selected_props": ["Seçili elemanın özellikleri", "Selected element", "خصائص العنصر المحدد", "選択した要素", "선택한 요소", "Выбранный элемент", "所选元素"],
    "v53_selected_count": ["%1$d parça seçili", "%1$d parts selected", "%1$d أجزاء محددة", "%1$d 個選択中", "%1$d개 선택됨", "Выбрано частей: %1$d", "已选 %1$d 个部分"],
    "v53_line_section": ["Çizgi", "Outline", "الحد", "線", "선", "Контур", "线条"],
    "v53_line_width": ["Çizgi kalınlığı", "Line width", "سماكة الخط", "線の太さ", "선 굵기", "Толщина линии", "线宽"],
    "v53_fill_section": ["Dolgu", "Fill", "التعبئة", "塗り", "채우기", "Заливка", "填充"],
    "v53_fill_on": ["Dolgulu", "Filled", "مملوء", "塗りつぶし", "채움", "С заливкой", "填充"],
    "v53_corner": ["Köşe yuvarlaklığı", "Corner radius", "تدوير الزوايا", "角の丸み", "모서리 둥글기", "Скругление углов", "圆角"],
    "v53_thinner": ["İncelt", "Thinner", "أرفع", "細く", "가늘게", "Тоньше", "变细"],
    "v53_thicker": ["Kalınlaştır", "Thicker", "أسمك", "太く", "굵게", "Толще", "变粗"],
    "v53_smooth": ["Çizgiyi düzelt", "Smooth line", "تنعيم الخط", "線をなめらかに", "선 다듬기", "Сгладить линию", "平滑线条"],
    "v53_image_section": ["Resim", "Image", "الصورة", "画像", "이미지", "Изображение", "图片"],
    "v53_text_section": ["Yazı", "Text", "النص", "文字", "글자", "Текст", "文字"],
    "v53_font": ["Yazı tipi", "Font", "الخط", "フォント", "글꼴", "Шрифт", "字体"],
    "v53_text_size": ["Yazı boyutu", "Text size", "حجم النص", "文字サイズ", "글자 크기", "Размер текста", "字号"],
    "v53_font_group_sans": ["Sade", "Sans", "بلا زوائد", "ゴシック", "고딕", "Без засечек", "无衬线"],
    "v53_font_group_serif": ["Tırnaklı", "Serif", "بزوائد", "明朝", "명조", "С засечками", "衬线"],
    "v53_font_group_hand": ["El yazısı", "Handwriting", "خط اليد", "手書き", "손글씨", "Рукописные", "手写"],
    "v53_font_group_display": ["Başlık", "Display", "عرض", "見出し", "제목", "Заголовочные", "标题"],
    "v53_font_group_mono": ["Eş aralıklı", "Monospace", "أحادي المسافة", "等幅", "고정폭", "Моноширинные", "等宽"],
    "v53_font_group_comic": ["Çizgi roman", "Comic", "قصص مصورة", "コミック", "만화", "Комикс", "漫画"],
    "v53_brush_section": ["Fırça", "Brush", "الفرشاة", "ブラシ", "브러시", "Кисть", "画笔"],
    "v53_brush_settings_in_properties": ["Boyut, akış ve örtücülük Özellikler panelinde.", "Size, flow and opacity are in the Properties panel.", "الحجم والتدفق والعتامة في لوحة الخصائص.", "サイズ・流量・不透明度はプロパティパネルにあります。", "크기, 흐름, 불투명도는 속성 패널에 있습니다.", "Размер, поток и непрозрачность — на панели «Свойства».", "大小、流量和不透明度在属性面板中。"],
    # Ekle
    "v53_add_sfx": ["Efekt yazısı", "Sound effect", "مؤثر صوتي", "効果音", "효과음", "Звукоподражание", "拟声词"],
    "v53_patterns": ["Desenler", "Patterns", "الأنماط", "パターン", "패턴", "Узоры", "图案"],
    "v53_tones": ["Ekran tonları", "Screentones", "التظليل النقطي", "スクリーントーン", "스크린톤", "Скринтоны", "网点"],
    "v53_quick_text": ["Yazı kutusu", "Text box", "مربع نص", "テキストボックス", "글상자", "Текстовое поле", "文本框"],
    "v53_quick_text_hint": ["Yaz, Enter ile sonraki satıra geç", "Type, press Enter for the next line", "اكتب ثم اضغط Enter للسطر التالي", "入力して Enter で次へ", "입력 후 Enter로 다음 줄", "Пишите, Enter — следующая строка", "输入后按回车进入下一行"],
    "v53_quick_text_move": ["Yazı kutusunu taşı", "Move text box", "نقل مربع النص", "テキストボックスを移動", "글상자 이동", "Переместить поле", "移动文本框"],
    "v53_quick_text_next": ["Yazıyı ekle ve alta geç", "Add text and continue below", "أضف النص وتابع أسفل", "追加して下へ続ける", "추가하고 아래로 계속", "Добавить и продолжить ниже", "添加并在下方继续"],
    "v53_balloon_hint": ["Balonu seç; eklenince yazıyı doğrudan balonun içine yaz.", "Pick a balloon, then type straight into it.", "اختر فقاعة ثم اكتب داخلها مباشرة.", "フキダシを選び、そのまま中に入力します。", "말풍선을 고른 뒤 바로 안에 입력하세요.", "Выберите баллон и сразу пишите в нём.", "选择气泡后直接在其中输入文字。"],
    "v53_balloon_speakers": ["Konuşan", "Speakers", "المتحدثون", "話者", "화자", "Говорящие", "说话者"],
    "v53_balloon_speakers_1": ["1 kişi", "1 speaker", "شخص واحد", "1人", "1명", "1 персонаж", "1 人"],
    "v53_balloon_speakers_2": ["2 kişi", "2 speakers", "شخصان", "2人", "2명", "2 персонажа", "2 人"],
    "v53_balloon_speakers_3": ["3 kişi", "3 speakers", "3 أشخاص", "3人", "3명", "3 персонажа", "3 人"],
    "v53_templates_hint": ["Panel düzenini seç; paneller sayfaya yerleşir.", "Choose a panel layout for the page.", "اختر تخطيط لوحات للصفحة.", "ページのコマ割りを選びます。", "페이지 칸 배치를 고르세요.", "Выберите раскладку панелей.", "为页面选择分格布局。"],
    "v53_panels_n": ["%1$d panel", "%1$d panels", "%1$d لوحات", "%1$d コマ", "%1$d칸", "Панелей: %1$d", "%1$d 格"],
    "v53_effects_hint": ["Aksiyon, büyü ve duygu efektleri.", "Action, magic and emotion effects.", "مؤثرات الحركة والسحر والمشاعر.", "アクション・魔法・感情の効果。", "액션, 마법, 감정 효과.", "Эффекты действия, магии и эмоций.", "动作、魔法和情绪特效。"],
    "v53_sfx_hint": ["Ses yazısını seç; sonra boyutunu ve rengini değiştirebilirsin.", "Pick a sound word; you can resize and recolour it later.", "اختر كلمة صوتية؛ يمكنك تغيير حجمها ولونها لاحقًا.", "効果音を選び、あとで大きさや色を変えられます。", "효과음을 고르면 나중에 크기와 색을 바꿀 수 있습니다.", "Выберите звук; размер и цвет можно изменить позже.", "选择拟声词，之后可调整大小和颜色。"],
    "v53_tones_hint": ["Ekran tonları gölge, ışık ve doku verir. Bir şekil ya da panel seçiliyse ona uygulanır; değilse yeni bir tonlu alan eklenir.", "Screentones add shading, light and texture. Apply to a selected shape or panel, or insert a new toned area.", "يضيف التظليل النقطي الظل والضوء والملمس. طبّقه على شكل أو لوحة محددة أو أضف منطقة جديدة.", "スクリーントーンで陰影・光・質感を加えます。選択中の図形やコマに貼るか、新しい範囲を追加します。", "스크린톤으로 그림자, 빛, 질감을 줍니다. 선택한 도형이나 칸에 적용하거나 새 영역을 추가하세요.", "Скринтоны дают тень, свет и фактуру. Примените к выбранной фигуре или панели либо добавьте новую область.", "网点用于阴影、光线和质感。可应用于所选形状或分格，或插入新的网点区域。"],
    "v53_patterns_hint": ["Desenler duyguyu (çiçek, parıltı, ip ağı) ya da giysi ve zemini (puantiye, ekose, tuğla) anlatır.", "Patterns show mood (flowers, sparkles, ropes) or clothing and surfaces (polka dots, plaid, brick).", "تعبّر الأنماط عن المشاعر (زهور، بريق، حبال) أو الملابس والأسطح (منقط، مربعات، طوب).", "パターンで気分（花・キラキラ・縄網）や服・床（水玉・チェック・レンガ）を表します。", "패턴으로 감정(꽃, 반짝임, 그물)이나 옷과 바닥(물방울, 체크, 벽돌)을 표현합니다.", "Узоры передают настроение (цветы, блёстки, сетка) или ткань и поверхности (горошек, клетка, кирпич).", "图案可表达情绪（花朵、闪光、绳网）或服装与表面（波点、格纹、砖墙）。"],
    "v53_tone_settings": ["Ayarlar", "Settings", "الإعدادات", "設定", "설정", "Настройки", "设置"],
    "v53_tone_density": ["Koyuluk", "Density", "الكثافة", "濃度", "농도", "Плотность", "浓度"],
    "v53_tone_scale": ["Sıklık", "Scale", "المقياس", "線数", "크기", "Масштаб", "疏密"],
    "v53_tone_angle": ["Açı", "Angle", "الزاوية", "角度", "각도", "Угол", "角度"],
    "v53_tone_insert": ["Alan ekle", "Insert area", "إضافة منطقة", "範囲を追加", "영역 추가", "Добавить область", "插入区域"],
    "v53_tone_apply": ["Seçiliye uygula", "Apply to selection", "تطبيق على المحدد", "選択に適用", "선택에 적용", "Применить к выбранному", "应用到所选"],
    "v53_tone_apply_hint": ["Panel ya da kapalı bir şekil seçiliyken \"Seçiliye uygula\" tonu onun içine yerleştirir.", "With a panel or closed shape selected, \"Apply to selection\" fills it with the tone.", "عند تحديد لوحة أو شكل مغلق، يملؤه \"تطبيق على المحدد\" بالتظليل.", "コマや閉じた図形を選ぶと「選択に適用」でその中にトーンを貼ります。", "칸이나 닫힌 도형을 선택하면 \"선택에 적용\"으로 그 안에 톤을 넣습니다.", "Если выбрана панель или замкнутая фигура, «Применить к выбранному» заполнит её.", "选中分格或闭合形状时，“应用到所选”会在其内部填充。"],
    "v53_tone_remove": ["Tonu kaldır", "Remove tone", "إزالة التظليل", "トーンを外す", "톤 제거", "Убрать тон", "移除网点"],
    # Yapı kılavuzları
    # v54 "Yapı kılavuzları" adı "Çizim yardımcıları" oldu
    "v53_struct_short": ["Çizim yardımcısı", "Drawing helper", "مساعد الرسم", "描画ヘルパー", "그리기 도우미", "Помощник рисования", "绘图助手"],
    "v53_struct_title": ["Çizim yardımcıları", "Drawing helpers", "مساعدات الرسم", "描画ヘルパー", "그리기 도우미", "Помощники рисования", "绘图助手"],
    "v53_struct_hint": ["Canlı, nesne ve eşyaların yapı çizgileri tuvalin üstünde görünür; tuvale yazılmaz. Köşedeki tutamaklarla taşı ve boyutlandır, üzerinden çiz.", "Construction lines for living things, objects and items appear over the canvas without being drawn on it. Move and resize with the corner handles, then trace over.", "تظهر خطوط بناء الكائنات والأشياء فوق اللوحة دون أن تُرسم عليها. حرّكها وغيّر حجمها بالمقابض ثم ارسم فوقها.", "生き物・物・道具の構造線がキャンバスの上に表示されます（描画はされません）。角のハンドルで移動・拡大縮小し、なぞって描きます。", "생물, 물체, 물건의 구조선이 캔버스 위에 보입니다(그려지지 않음). 모서리 핸들로 옮기고 크기를 바꾼 뒤 따라 그리세요.", "Каркасные линии существ, предметов и вещей видны поверх холста и не рисуются на нём. Двигайте и масштабируйте маркерами, обводите.", "生物、物体和物品的结构线显示在画布上方，不会画到画布上。用角上的控制点移动和缩放，然后描画。"],
    "v53_struct_group_living": ["Canlılar", "Living things", "الكائنات الحية", "生き物", "생물", "Существа", "生物"],
    "v53_struct_group_object": ["Nesneler", "Objects", "الأشياء", "物体", "물체", "Предметы", "物体"],
    "v53_struct_group_item": ["Eşyalar", "Items", "الأدوات", "道具", "물건", "Вещи", "物品"],
    "v53_struct_settings": ["Kılavuz ayarları", "Guide settings", "إعدادات الدليل", "ガイド設定", "가이드 설정", "Настройки каркаса", "参考设置"],
    "v53_struct_opacity": ["Görünürlük", "Visibility", "الوضوح", "濃さ", "선명도", "Видимость", "可见度"],
    "v53_struct_flip": ["Aynala", "Mirror", "عكس", "反転", "좌우 반전", "Отразить", "镜像"],
    "v53_struct_remove": ["Kılavuzu kaldır", "Remove guide", "إزالة الدليل", "ガイドを外す", "가이드 제거", "Убрать каркас", "移除参考"],
    "v53_sg_human_front": ["İnsan (önden, 8 baş)", "Human (front, 8 heads)", "إنسان (أمامي، 8 رؤوس)", "人物（正面・8頭身）", "사람(정면, 8등신)", "Человек (анфас, 8 голов)", "人体（正面，8头身）"],
    "v53_sg_human_side": ["İnsan (yandan)", "Human (side)", "إنسان (جانبي)", "人物（横）", "사람(측면)", "Человек (профиль)", "人体（侧面）"],
    "v53_sg_face_front": ["Yüz (önden)", "Face (front)", "وجه (أمامي)", "顔（正面）", "얼굴(정면)", "Лицо (анфас)", "脸（正面）"],
    "v53_sg_face_side": ["Yüz (profil)", "Face (profile)", "وجه (جانبي)", "顔（横顔）", "얼굴(옆모습)", "Лицо (профиль)", "脸（侧面）"],
    "v53_sg_hand": ["El", "Hand", "يد", "手", "손", "Кисть руки", "手"],
    "v53_sg_cat": ["Kedi (oturan)", "Cat (sitting)", "قطة (جالسة)", "猫（座り）", "고양이(앉은)", "Кошка (сидит)", "猫（坐姿）"],
    "v53_sg_dog": ["Köpek (yandan)", "Dog (side)", "كلب (جانبي)", "犬（横）", "개(측면)", "Собака (сбоку)", "狗（侧面）"],
    "v53_sg_bird": ["Kuş", "Bird", "طائر", "鳥", "새", "Птица", "鸟"],
    "v53_sg_horse": ["At (yandan)", "Horse (side)", "حصان (جانبي)", "馬（横）", "말(측면)", "Лошадь (сбоку)", "马（侧面）"],
    "v53_sg_fish": ["Balık", "Fish", "سمكة", "魚", "물고기", "Рыба", "鱼"],
    "v53_sg_tree": ["Ağaç", "Tree", "شجرة", "木", "나무", "Дерево", "树"],
    "v53_sg_box": ["Kutu (iki nokta perspektif)", "Box (two-point perspective)", "صندوق (منظور بنقطتين)", "箱（二点透視）", "상자(2점 투시)", "Коробка (2 точки схода)", "盒子（两点透视）"],
    "v53_sg_cylinder": ["Silindir", "Cylinder", "أسطوانة", "円柱", "원기둥", "Цилиндр", "圆柱"],
    "v53_sg_sphere": ["Küre", "Sphere", "كرة", "球", "구", "Сфера", "球体"],
    "v53_sg_cone": ["Koni", "Cone", "مخروط", "円錐", "원뿔", "Конус", "圆锥"],
    "v53_sg_pyramid": ["Piramit", "Pyramid", "هرم", "角錐", "피라미드", "Пирамида", "棱锥"],
    "v53_sg_house": ["Ev", "House", "منزل", "家", "집", "Дом", "房子"],
    "v53_sg_car": ["Araba", "Car", "سيارة", "車", "자동차", "Машина", "汽车"],
    "v53_sg_cup": ["Fincan", "Cup", "فنجان", "カップ", "컵", "Чашка", "杯子"],
    "v53_sg_bottle": ["Şişe", "Bottle", "زجاجة", "瓶", "병", "Бутылка", "瓶子"],
    "v53_sg_chair": ["Sandalye", "Chair", "كرسي", "椅子", "의자", "Стул", "椅子"],
    "v53_sg_table": ["Masa", "Table", "طاولة", "テーブル", "탁자", "Стол", "桌子"],
    "v53_sg_lamp": ["Lamba", "Lamp", "مصباح", "ランプ", "램프", "Лампа", "台灯"],
    "v53_sg_vase": ["Vazo", "Vase", "مزهرية", "花瓶", "꽃병", "Ваза", "花瓶"],
    "v53_sg_book": ["Kitap", "Book", "كتاب", "本", "책", "Книга", "书"],
    "v53_sg_shoe": ["Ayakkabı", "Shoe", "حذاء", "靴", "신발", "Ботинок", "鞋"],
    "v53_sg_phone": ["Telefon", "Phone", "هاتف", "スマートフォン", "휴대폰", "Телефон", "手机"],
}

# Ekran tonları ve desenler: ad ve çizgi romandaki kullanım
TONES = {
    "skin": (["Ten tonu", "Skin tone", "لون البشرة", "肌トーン", "피부 톤", "Тон кожи", "肤色网点"],
             ["İnce nokta: yüz ve ten gölgesi", "Fine dots: face and skin shading", "نقاط دقيقة: ظل الوجه والبشرة", "細かい網点：顔や肌の影", "가는 점: 얼굴과 피부 그림자", "Мелкие точки: тени лица и кожи", "细网点：脸部与皮肤阴影"]),
    "soft_shadow": (["Yumuşak gölge", "Soft shadow", "ظل ناعم", "やわらかい影", "부드러운 그림자", "Мягкая тень", "柔和阴影"],
                    ["%30 nokta: giysi ve nesne gölgesi", "30% dots: clothes and object shadows", "نقاط 30%: ظلال الملابس والأشياء", "30%網点：服や物の影", "30% 점: 옷과 물체 그림자", "Точки 30%: тени одежды и предметов", "30%网点：衣服和物体阴影"]),
    "deep_shadow": (["Koyu gölge", "Deep shadow", "ظل داكن", "濃い影", "짙은 그림자", "Глубокая тень", "深色阴影"],
                    ["%55 nokta: sert ışık, dram", "55% dots: hard light, drama", "نقاط 55%: ضوء قاسٍ ودراما", "55%網点：強い光、ドラマ", "55% 점: 강한 빛, 극적 장면", "Точки 55%: жёсткий свет, драма", "55%网点：强光、戏剧性"]),
    "night": (["Gece", "Night", "ليل", "夜", "밤", "Ночь", "夜晚"],
              ["Koyu ton: gece göğü, karanlık oda", "Dark tone: night sky, dark rooms", "تظليل داكن: سماء الليل والغرف المظلمة", "暗いトーン：夜空、暗い部屋", "어두운 톤: 밤하늘, 어두운 방", "Тёмный тон: ночное небо, тёмные комнаты", "深色：夜空、暗室"]),
    "sky": (["Gökyüzü geçişi", "Sky gradient", "تدرج السماء", "空のグラデーション", "하늘 그라데이션", "Градиент неба", "天空渐变"],
            ["Yukarıdan aşağı açılan nokta: gök, ışık geçişi", "Dots fading downward: sky, light falloff", "نقاط تتلاشى للأسفل: السماء وتدرج الضوء", "下へ薄くなる網点：空、光の減衰", "아래로 옅어지는 점: 하늘, 빛 감쇠", "Точки, светлеющие вниз: небо, спад света", "向下渐淡的网点：天空、光线过渡"]),
    "glow": (["Parlama", "Glow", "توهج", "光", "빛남", "Сияние", "光晕"],
             ["Ortası açık dairesel ton: ışık, odak, büyü", "Radial tone with a light centre: light, focus, magic", "تظليل دائري بمركز فاتح: ضوء وتركيز وسحر", "中心が明るい円形トーン：光、注目、魔法", "가운데가 밝은 원형 톤: 빛, 초점, 마법", "Радиальный тон со светлым центром: свет, фокус, магия", "中心明亮的放射网点：光、焦点、魔法"]),
    "sand": (["Kum tonu", "Sand tone", "تظليل رملي", "砂目トーン", "모래 톤", "Песочный тон", "砂目网点"],
             ["Gürültü dokusu: yumuşak gölge, duygu, toz", "Noise texture: soft shading, mood, dust", "ملمس عشوائي: ظل ناعم ومشاعر وغبار", "ノイズ質感：やわらかい影、感情、ほこり", "노이즈 질감: 부드러운 그림자, 감정, 먼지", "Шумовая фактура: мягкая тень, настроение, пыль", "噪点质感：柔和阴影、情绪、灰尘"]),
    "lines": (["Çizgi tonu", "Line tone", "تظليل خطي", "線トーン", "선 톤", "Линейный тон", "线条网点"],
              ["Paralel çizgi: gölge, yağmur, hareket", "Parallel lines: shading, rain, motion", "خطوط متوازية: ظل ومطر وحركة", "平行線：影、雨、動き", "평행선: 그림자, 비, 움직임", "Параллельные линии: тень, дождь, движение", "平行线：阴影、雨、动感"]),
    "hatch": (["Çapraz tarama", "Cross-hatching", "تظليل متقاطع", "クロスハッチ", "크로스 해칭", "Перекрёстная штриховка", "交叉排线"],
              ["Kesişen çizgi: koyu gölge, eski üslup", "Crossing lines: deep shade, classic style", "خطوط متقاطعة: ظل داكن وأسلوب كلاسيكي", "交差する線：濃い影、古典的な表現", "교차선: 짙은 그림자, 고전적 표현", "Пересекающиеся линии: глубокая тень, классика", "交叉线条：深色阴影、经典画风"]),
    "kakeami": (["Demet tarama", "Kakeami", "تظليل حزمي", "カケアミ", "카케아미", "Какэами", "排线网"],
                ["Çizgi demetleri: arka plan dokusu, gerilim", "Bundled strokes: background texture, tension", "حزم خطوط: ملمس الخلفية والتوتر", "線の束：背景の質感、緊張感", "선 묶음: 배경 질감, 긴장감", "Пучки штрихов: фактура фона, напряжение", "线束：背景质感、紧张感"]),
    "flowers": (["Çiçekler", "Flowers", "زهور", "花", "꽃", "Цветы", "花朵"],
                ["Romantik, mutlu anlar (shoujo)", "Romantic, happy moments (shoujo)", "لحظات رومانسية وسعيدة", "ロマンチックで幸せな場面（少女漫画）", "로맨틱하고 행복한 장면(순정)", "Романтичные, счастливые моменты (сёдзё)", "浪漫、幸福的时刻（少女漫画）"]),
    "sparkle": (["Parıltı", "Sparkles", "بريق", "キラキラ", "반짝임", "Блёстки", "闪光"],
                ["Hayranlık, sevinç, güzellik (kirakira)", "Admiration, joy, beauty (kirakira)", "إعجاب وفرح وجمال", "憧れ・喜び・美しさ（キラキラ）", "동경, 기쁨, 아름다움(키라키라)", "Восхищение, радость, красота (кира-кира)", "倾慕、喜悦、美丽"]),
    "bubbles": (["Baloncuklar", "Bubbles", "فقاعات", "泡", "거품", "Пузыри", "泡泡"],
                ["Hayal, neşe, hafiflik", "Daydreams, cheer, lightness", "أحلام ومرح وخفة", "夢見心地、明るさ、軽やかさ", "공상, 즐거움, 가벼움", "Мечты, веселье, лёгкость", "幻想、欢快、轻盈"]),
    "nawaami": (["İp ağı", "Rope net", "شبكة حبال", "縄網（ナワアミ）", "그물(나와아미)", "Верёвочная сетка", "绳网"],
                ["Endişe, kaygı, sıkıntı", "Worry, anxiety, unease", "قلق وتوتر وضيق", "不安・心配・焦り", "걱정, 불안, 초조", "Тревога, беспокойство", "担忧、焦虑、不安"]),
    "gloom": (["Dikey çizgiler", "Gloom lines", "خطوط عمودية", "縦線（ガーン）", "세로선", "Вертикальные линии", "竖线"],
              ["Moral bozukluğu, şok, utanç", "Gloom, shock, embarrassment", "إحباط وصدمة وإحراج", "落ち込み・ショック・気まずさ", "낙담, 충격, 민망함", "Уныние, шок, смущение", "沮丧、震惊、尴尬"]),
    "stars": (["Yıldızlar", "Stars", "نجوم", "星", "별", "Звёзды", "星星"],
              ["Gece, büyü, hayal", "Night, magic, dreams", "ليل وسحر وأحلام", "夜・魔法・夢", "밤, 마법, 꿈", "Ночь, магия, мечты", "夜晚、魔法、梦想"]),
    "hearts": (["Kalpler", "Hearts", "قلوب", "ハート", "하트", "Сердечки", "爱心"],
               ["Aşk, hayranlık", "Love, crush", "حب وإعجاب", "恋・ときめき", "사랑, 설렘", "Любовь, влюблённость", "爱情、心动"]),
    "zigzag": (["Zikzak", "Zigzag", "متعرج", "ジグザグ", "지그재그", "Зигзаг", "锯齿"],
               ["Enerji, gerginlik, elektrik", "Energy, tension, electricity", "طاقة وتوتر وكهرباء", "エネルギー・緊張・電気", "에너지, 긴장, 전기", "Энергия, напряжение, электричество", "能量、紧张、电流"]),
    "polka": (["Puantiye", "Polka dots", "منقط", "水玉", "물방울", "Горошек", "波点"],
              ["Giysi, perde, neşeli zemin", "Clothes, curtains, cheerful backgrounds", "ملابس وستائر وخلفيات مرحة", "服・カーテン・明るい背景", "옷, 커튼, 밝은 배경", "Одежда, шторы, весёлый фон", "衣服、窗帘、欢快背景"]),
    "stripes": (["Çizgili", "Stripes", "مخطط", "ストライプ", "줄무늬", "Полоски", "条纹"],
                ["Giysi, bayrak, tente", "Clothes, flags, awnings", "ملابس وأعلام ومظلات", "服・旗・日よけ", "옷, 깃발, 차양", "Одежда, флаги, навесы", "衣服、旗帜、遮阳篷"]),
    "plaid": (["Ekose", "Plaid", "مربعات اسكتلندية", "チェック", "체크", "Клетка (шотландка)", "格纹"],
              ["Okul eteği, gömlek, battaniye", "School skirts, shirts, blankets", "تنانير مدرسية وقمصان وبطانيات", "制服のスカート・シャツ・毛布", "교복 치마, 셔츠, 담요", "Школьные юбки, рубашки, пледы", "校服裙、衬衫、毯子"]),
    "check": (["Dama", "Checks", "شطرنجي", "市松", "바둑판", "Шахматка", "棋盘格"],
              ["Zemin, masa örtüsü, bayrak", "Floors, tablecloths, flags", "أرضيات ومفارش وأعلام", "床・テーブルクロス・旗", "바닥, 식탁보, 깃발", "Пол, скатерти, флаги", "地板、桌布、旗帜"]),
    "brick": (["Tuğla", "Brick", "طوب", "レンガ", "벽돌", "Кирпич", "砖墙"],
              ["Duvar, sokak, bina", "Walls, streets, buildings", "جدران وشوارع ومبانٍ", "壁・路地・建物", "벽, 거리, 건물", "Стены, улицы, здания", "墙壁、街道、建筑"]),
    "wood": (["Tahta", "Wood", "خشب", "木目", "나뭇결", "Дерево", "木纹"],
             ["Zemin, masa, kapı", "Floors, tables, doors", "أرضيات وطاولات وأبواب", "床・机・扉", "바닥, 책상, 문", "Пол, столы, двери", "地板、桌子、门"]),
}
for k, (name, use) in TONES.items():
    S["v53_tone_" + k] = name
    S["v53_tone_" + k + "_use"] = use

# Foto düzenleme (v53)
S.update({
    "v53_photo_tab_mask": ["Maske", "Mask", "القناع", "マスク", "마스크", "Маска", "蒙版"],
    "v53_photo_sub_basic": ["Temel", "Basic", "أساسي", "基本", "기본", "Основные", "基本"],
    "v53_photo_sub_tones": ["Tonlar", "Tones", "الدرجات", "トーン", "톤", "Тона", "色调"],
    "v53_photo_sub_detail": ["Ayrıntı", "Detail", "التفاصيل", "ディテール", "디테일", "Детали", "细节"],
    "v53_photo_sub_hsl": ["HSL", "HSL", "HSL", "HSL", "HSL", "HSL", "HSL"],
    "v53_photo_sub_balance": ["Renk dengesi", "Color balance", "توازن الألوان", "カラーバランス", "색상 균형", "Цветовой баланс", "色彩平衡"],
    "v53_photo_sub_comic": ["Çizgi roman", "Comic", "قصص مصورة", "コミック", "만화", "Комикс", "漫画"],
    "v53_photo_sub_blur": ["Bulanıklık", "Blur", "التمويه", "ぼかし", "흐림", "Размытие", "模糊"],
    "v53_photo_sub_vignette": ["Vinyet ve gren", "Vignette & grain", "التظليل والحبيبات", "周辺減光と粒子", "비네트와 그레인", "Виньетка и зерно", "暗角与颗粒"],
    "v53_photo_sub_bg": ["Arka plan", "Background", "الخلفية", "背景", "배경", "Фон", "背景"],
    "v53_photo_sub_clip": ["Kırpma maskesi", "Clipping mask", "قناع القص", "クリッピングマスク", "클리핑 마스크", "Обтравочная маска", "剪贴蒙版"],
    "v53_photo_hsl_hint": ["Bir renk aralığı seç; yalnız o renkteki pikseller değişir.", "Pick a colour range; only pixels of that colour change.", "اختر نطاق لون؛ تتغير بكسلات ذلك اللون فقط.", "色域を選ぶと、その色の画素だけが変わります。", "색 범위를 고르면 그 색의 픽셀만 바뀝니다.", "Выберите цветовой диапазон — изменятся только его пиксели.", "选择一个颜色范围，只改变该颜色的像素。"],
    "v53_photo_hsl_hue": ["Ton", "Hue", "تدرج اللون", "色相", "색조", "Тон", "色相"],
    "v53_photo_hsl_sat": ["Doygunluk", "Saturation", "التشبع", "彩度", "채도", "Насыщенность", "饱和度"],
    "v53_photo_hsl_light": ["Açıklık", "Lightness", "الإضاءة", "輝度", "밝기", "Яркость", "明度"],
    "v53_photo_hsl_reset": ["HSL'i sıfırla", "Reset HSL", "إعادة ضبط HSL", "HSLをリセット", "HSL 초기화", "Сбросить HSL", "重置 HSL"],
    "v53_photo_range_0": ["Kırmızılar", "Reds", "الأحمر", "レッド", "빨강", "Красные", "红色"],
    "v53_photo_range_1": ["Turuncular", "Oranges", "البرتقالي", "オレンジ", "주황", "Оранжевые", "橙色"],
    "v53_photo_range_2": ["Sarılar", "Yellows", "الأصفر", "イエロー", "노랑", "Жёлтые", "黄色"],
    "v53_photo_range_3": ["Yeşiller", "Greens", "الأخضر", "グリーン", "초록", "Зелёные", "绿色"],
    "v53_photo_range_4": ["Camgöbekleri", "Aquas", "السماوي", "アクア", "청록", "Бирюзовые", "青色"],
    "v53_photo_range_5": ["Maviler", "Blues", "الأزرق", "ブルー", "파랑", "Синие", "蓝色"],
    "v53_photo_range_6": ["Morlar", "Purples", "البنفسجي", "パープル", "보라", "Фиолетовые", "紫色"],
    "v53_photo_range_7": ["Macentalar", "Magentas", "الأرجواني", "マゼンタ", "자홍", "Пурпурные", "洋红"],
    "v53_photo_bal_shadows": ["Gölgeler", "Shadows", "الظلال", "シャドウ", "그림자", "Тени", "阴影"],
    "v53_photo_bal_midtones": ["Orta tonlar", "Midtones", "الدرجات المتوسطة", "中間調", "중간톤", "Средние тона", "中间调"],
    "v53_photo_bal_highlights": ["Vurgular", "Highlights", "الإضاءات", "ハイライト", "하이라이트", "Светлые", "高光"],
    "v53_photo_bal_cr": ["Camgöbeği ↔ Kırmızı", "Cyan ↔ Red", "سماوي ↔ أحمر", "シアン ↔ レッド", "청록 ↔ 빨강", "Голубой ↔ Красный", "青 ↔ 红"],
    "v53_photo_bal_mg": ["Macenta ↔ Yeşil", "Magenta ↔ Green", "أرجواني ↔ أخضر", "マゼンタ ↔ グリーン", "자홍 ↔ 초록", "Пурпурный ↔ Зелёный", "洋红 ↔ 绿"],
    "v53_photo_bal_yb": ["Sarı ↔ Mavi", "Yellow ↔ Blue", "أصفر ↔ أزرق", "イエロー ↔ ブルー", "노랑 ↔ 파랑", "Жёлтый ↔ Синий", "黄 ↔ 蓝"],
    "v53_photo_bal_preserve": ["Parlaklığı koru", "Preserve luminosity", "الحفاظ على السطوع", "明るさを保持", "밝기 유지", "Сохранять яркость", "保持明度"],
    "v53_photo_clip_on": ["Kırpma maskesi", "Clipping mask", "قناع القص", "クリッピングマスク", "클리핑 마스크", "Обтравочная маска", "剪贴蒙版"],
    "v53_photo_clip_hint": ["Dolgu yalnız resmin görünen kısmına uygulanır; arka planı kaldırınca nesnenin şeklini doldurur.", "The fill only covers the visible part of the image; after removing the background it fills the subject's shape.", "تُطبّق التعبئة على الجزء المرئي فقط؛ بعد إزالة الخلفية تملأ شكل العنصر.", "塗りは画像の見えている部分だけにかかります。背景を消すと被写体の形を塗ります。", "채우기는 이미지의 보이는 부분에만 적용됩니다. 배경을 지우면 대상의 모양을 채웁니다.", "Заливка ложится только на видимую часть; после удаления фона она заполняет силуэт объекта.", "填充只作用于图片可见部分；移除背景后会填满主体形状。"],
    "v53_photo_clip_color": ["Renk", "Colour", "لون", "単色", "단색", "Цвет", "纯色"],
    "v53_photo_clip_gradient": ["Gradyan", "Gradient", "تدرج", "グラデーション", "그라데이션", "Градиент", "渐变"],
    "v53_photo_clip_color1": ["Renk 1", "Colour 1", "اللون 1", "色 1", "색 1", "Цвет 1", "颜色 1"],
    "v53_photo_clip_color2": ["Renk 2", "Colour 2", "اللون 2", "色 2", "색 2", "Цвет 2", "颜色 2"],
    "v53_photo_clip_angle": ["Gradyan açısı", "Gradient angle", "زاوية التدرج", "グラデーションの角度", "그라데이션 각도", "Угол градиента", "渐变角度"],
    "v53_photo_clip_opacity": ["Örtücülük", "Opacity", "العتامة", "不透明度", "불투명도", "Непрозрачность", "不透明度"],
    "v53_photo_clip_blend": ["Karışım kipi", "Blend mode", "وضع المزج", "描画モード", "혼합 모드", "Режим наложения", "混合模式"],
    "v53_photo_blend_normal": ["Normal", "Normal", "عادي", "通常", "표준", "Обычный", "正常"],
    "v53_photo_blend_multiply": ["Çarp", "Multiply", "ضرب", "乗算", "곱하기", "Умножение", "正片叠底"],
    "v53_photo_blend_screen": ["Ekran", "Screen", "شاشة", "スクリーン", "스크린", "Экран", "滤色"],
    "v53_photo_blend_overlay": ["Bindirme", "Overlay", "تراكب", "オーバーレイ", "오버레이", "Перекрытие", "叠加"],
    "v53_photo_blend_soft_light": ["Yumuşak ışık", "Soft light", "ضوء ناعم", "ソフトライト", "소프트 라이트", "Мягкий свет", "柔光"],
    "v53_photo_blend_color": ["Renk", "Color", "لون", "カラー", "색상", "Цветность", "颜色"],
})


def esc(s):
    s = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    s = s.replace("'", "\\'").replace('"', '\\"')
    return s


def main(res):
    for i, lang in enumerate(LANGS):
        d = os.path.join(res, DIRS[lang])
        os.makedirs(d, exist_ok=True)
        lines = ['<?xml version="1.0" encoding="utf-8"?>', "<!-- v53: tools/i18n-v53/strings_v53.py ile üretildi; elle değiştirmeyin -->", "<resources>"]
        for k in sorted(S):
            v = S[k]
            assert len(v) == 7, k
            fmt = ' formatted="true"' if "%1$" in v[i] else ""
            lines.append(f'    <string name="{k}"{fmt}>{esc(v[i])}</string>')
        lines.append("</resources>")
        open(os.path.join(d, "strings_v53.xml"), "w", encoding="utf-8").write("\n".join(lines) + "\n")
    print(len(S), "anahtar")


if __name__ == "__main__":
    main(sys.argv[1])
