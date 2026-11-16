#!/usr/bin/env python3
"""v54 metinleri: tek tablodan 7 dilin Android kaynakları (values*/strings_v54.xml) üretilir.

iOS tabloları bunlardan ios/tools/strings-export/convert.py ile çıkarılır.
Kullanım: python3 strings_v54.py <android/app/src/main/res>
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from strings_v53 import LANGS, DIRS, esc  # noqa: E402

S = {
    # Şekil kalıbı: seçili şekil/resim yokken yönlendirme
    "v54_template_choose_message": ["Kalıp için seçili bir şekil ya da resim yok. Çizim yardımcılarından ya da şekillerden birini seç; istersen tuvaldeki bir şekle de dokunabilirsin.", "Nothing is selected for the template. Pick one of the drawing helpers or shapes, or tap a shape on the canvas.", "لا يوجد شكل أو صورة محددة للقالب. اختر أحد مساعدات الرسم أو الأشكال، أو المس شكلًا على اللوحة.", "型にする図形や画像が選択されていません。描画ヘルパーか図形から選ぶか、キャンバス上の図形をタップしてください。", "틀로 쓸 도형이나 그림이 선택되지 않았습니다. 그리기 도우미나 도형 중 하나를 고르거나 캔버스의 도형을 누르세요.", "Для шаблона ничего не выбрано. Выберите помощник рисования или фигуру либо коснитесь фигуры на холсте.", "没有选中用作模板的形状或图片。请从绘图助手或形状中选择一个，或点击画布上的形状。"],
    "v54_template_choose_shapes": ["Şekiller", "Shapes", "الأشكال", "図形", "도형", "Фигуры", "形状"],
    "v54_template_choose_canvas": ["Tuvalden seç", "Pick on canvas", "اختر من اللوحة", "キャンバスから選ぶ", "캔버스에서 고르기", "Выбрать на холсте", "从画布选择"],
    "v54_template_helper_hint": ["Seçtiğin çizim yardımcısının çizgileri şekil kalıbı olur; kenarına yakın çizerek izleyebilirsin.", "The lines of the helper you pick become the shape template; draw near them to trace.", "تصبح خطوط المساعد الذي تختاره قالب الشكل؛ ارسم بالقرب منها لتتبعها.", "選んだヘルパーの線が図形の型になります。線の近くに描くとなぞれます。", "고른 도우미의 선이 도형 틀이 됩니다. 선 가까이 그려 따라 그리세요.", "Линии выбранного помощника станут шаблоном; рисуйте рядом с ними, чтобы обвести.", "所选助手的线条将成为形状模板；靠近线条绘制即可描摹。"],
    # v54 şifreli paylaşım (eşleşme şifresi)
    "v54_share_pair_title": ["Eşleşme şifresi", "Pairing code", "رمز الاقتران", "ペアリングコード", "페어링 코드", "Код сопряжения", "配对码"],
    "v54_share_pair_show": ["%1$s bağlanmak istiyor. Bu şifreyi o cihaza gir:", "%1$s wants to connect. Enter this code on that device:", "يريد %1$s الاتصال. أدخل هذا الرمز على ذلك الجهاز:", "%1$s が接続しようとしています。このコードをその端末に入力してください：", "%1$s 기기가 연결하려고 해요. 이 코드를 그 기기에 입력하세요:", "%1$s хочет подключиться. Введите этот код на том устройстве:", "%1$s 想要连接。请在该设备上输入此代码："],
    "v54_share_pair_show_note": ["Şifre yalnız bu bağlantı içindir; aktarım şifrelenir.", "The code is for this connection only; the transfer is encrypted.", "الرمز لهذا الاتصال فقط؛ ويتم تشفير النقل.", "コードはこの接続専用です。転送は暗号化されます。", "코드는 이번 연결에만 쓰이며 전송은 암호화돼요.", "Код действует только для этого подключения; передача шифруется.", "此代码仅用于本次连接；传输内容已加密。"],
    "v54_share_pair_enter": ["%1$s cihazının ekranındaki 6 haneli eşleşme şifresini gir.", "Enter the 6-digit pairing code shown on %1$s.", "أدخل رمز الاقتران المكون من 6 أرقام الظاهر على %1$s.", "%1$s に表示されている6桁のペアリングコードを入力してください。", "%1$s 화면에 표시된 6자리 페어링 코드를 입력하세요.", "Введите 6-значный код сопряжения с экрана %1$s.", "请输入 %1$s 屏幕上显示的 6 位配对码。"],
    "v54_share_pair_connect": ["Bağlan", "Connect", "اتصال", "接続", "연결", "Подключить", "连接"],
    "v54_share_pair_wrong": ["Eşleşme şifresi yanlış; yeniden dene.", "Wrong pairing code; try again.", "رمز الاقتران غير صحيح؛ حاول مرة أخرى.", "ペアリングコードが違います。もう一度お試しください。", "페어링 코드가 맞지 않아요. 다시 시도하세요.", "Неверный код сопряжения; попробуйте снова.", "配对码错误，请重试。"],
    "v54_share_pair_renew": ["Eşleşme yenilenmeli; yeniden bağlan ve şifreyi gir.", "Pairing must be renewed; connect again and enter the code.", "يجب تجديد الاقتران؛ اتصل مجددًا وأدخل الرمز.", "ペアリングの更新が必要です。再接続してコードを入力してください。", "페어링을 다시 해야 해요. 다시 연결해 코드를 입력하세요.", "Нужно обновить сопряжение: подключитесь снова и введите код.", "需要重新配对；请重新连接并输入代码。"],
    "v54_share_pair_old": ["Karşı cihazdaki uygulama güncel değil; şifreli paylaşım için güncelleyin.", "The app on the other device is out of date; update it for encrypted sharing.", "التطبيق على الجهاز الآخر قديم؛ حدّثه للمشاركة المشفرة.", "相手の端末のアプリが古いため、暗号化共有のために更新してください。", "상대 기기의 앱이 오래됐어요. 암호화 공유를 위해 업데이트하세요.", "Приложение на другом устройстве устарело; обновите его для зашифрованной передачи.", "对方设备上的应用版本过旧，请更新以使用加密分享。"],
    # v54 resim sınırları ve güvenlik
    "v54_image_err_too_large": ["Dosya çok büyük: %1$s. En fazla 10 MB olabilir.", "File is too large: %1$s. The maximum is 10 MB.", "الملف كبير جدًا: %1$s. الحد الأقصى 10 MB.", "ファイルが大きすぎます：%1$s。上限は10 MBです。", "파일이 너무 커요: %1$s. 최대 10 MB까지 가능해요.", "Файл слишком большой: %1$s. Максимум — 10 MB.", "文件过大：%1$s。最大不能超过 10 MB。"],
    "v54_image_err_dimensions": ["Çözünürlük çok yüksek (%1$d×%2$d). Kenarlar en fazla 8192 px, toplam en fazla 64 MP olmalı.", "Resolution is too high (%1$d×%2$d). Each side can be at most 8192 px, with a total of at most 64 MP.", "الدقة مرتفعة جدًا (%1$d×%2$d). يجب ألا يزيد طول الحواف عن 8192 px، وألا يتجاوز المجموع 64 MP.", "解像度が高すぎます（%1$d×%2$d）。各辺は最大8192 px、合計は最大64 MPにしてください。", "해상도가 너무 높아요(%1$d×%2$d). 각 변은 최대 8192 px, 전체는 최대 64 MP여야 해요.", "Слишком высокое разрешение (%1$d×%2$d). Стороны — не более 8192 px, общий размер — не более 64 MP.", "分辨率过高（%1$d×%2$d）。边长最大 8192 px，总像素最多 64 MP。"],
    "v54_image_warn_near_limit": ["Dosya 10 MB sınırına yakın (%1$s).", "File is close to the 10 MB limit (%1$s).", "الملف قريب من حد 10 MB (%1$s).", "ファイルが10 MBの上限に近づいています（%1$s）。", "파일이 10 MB 제한에 가까워요(%1$s).", "Размер файла близок к пределу в 10 MB (%1$s).", "文件接近 10 MB 上限（%1$s）。"],
    "v54_image_err_suspicious": ["Bu dosya güvenli bir resim değil: içinde kod ya da başka bir dosya var. Eklenmedi.", "This file is not a safe image: it contains code or another file. It was not added.", "هذا الملف ليس صورة آمنة: يحتوي على شيفرة أو ملف آخر. لم تتم إضافته.", "このファイルは安全な画像ではありません（コードや別のファイルが含まれています）。追加しませんでした。", "안전한 그림 파일이 아니에요. 코드나 다른 파일이 들어 있어 추가하지 않았어요.", "Это небезопасное изображение: внутри код или другой файл. Не добавлено.", "此文件不是安全的图片：其中包含代码或其他文件，未添加。"],
    # v54 yeni çizim yardımcıları
    "v53_sg_human_run": ["İnsan (koşan)", "Human (running)", "إنسان (يركض)", "人物（走る）", "사람(달리기)", "Человек (бег)", "人体（奔跑）"],
    "v53_sg_human_sit": ["İnsan (oturan)", "Human (sitting)", "إنسان (جالس)", "人物（座る）", "사람(앉은)", "Человек (сидя)", "人体（坐姿）"],
    "v53_sg_human_3q": ["İnsan (yarım yan)", "Human (three-quarter)", "إنسان (ثلاثة أرباع)", "人物（斜め）", "사람(3/4 각도)", "Человек (вполоборота)", "人体（四分之三侧）"],
    "v53_sg_face_3q": ["Yüz (yarım yan)", "Face (three-quarter)", "وجه (ثلاثة أرباع)", "顔（斜め）", "얼굴(3/4 각도)", "Лицо (вполоборота)", "脸（四分之三侧）"],
    "v53_sg_hand_open": ["Açık el", "Open hand", "يد مفتوحة", "開いた手", "편 손", "Открытая ладонь", "张开的手"],
    "v53_sg_cat_walk": ["Kedi (yürüyen)", "Cat (walking)", "قطة (تمشي)", "猫（歩く）", "고양이(걷는)", "Кошка (идёт)", "猫（行走）"],
    "v53_sg_dog_sit": ["Köpek (oturan)", "Dog (sitting)", "كلب (جالس)", "犬（座り）", "개(앉은)", "Собака (сидит)", "狗（坐姿）"],
    "v53_sg_bird_fly": ["Kuş (uçan)", "Bird (flying)", "طائر (يطير)", "鳥（飛ぶ）", "새(나는)", "Птица (в полёте)", "鸟（飞翔）"],
    "v53_sg_rabbit": ["Tavşan", "Rabbit", "أرنب", "ウサギ", "토끼", "Кролик", "兔子"],
    "v53_sg_cow": ["İnek", "Cow", "بقرة", "牛", "소", "Корова", "奶牛"],
    "v53_sg_elephant": ["Fil", "Elephant", "فيل", "ゾウ", "코끼리", "Слон", "大象"],
    "v53_sg_butterfly": ["Kelebek", "Butterfly", "فراشة", "チョウ", "나비", "Бабочка", "蝴蝶"],
    "v53_sg_dragon": ["Ejderha", "Dragon", "تنين", "ドラゴン", "용", "Дракон", "龙"],
    "v53_sg_rose": ["Gül", "Rose", "وردة", "バラ", "장미", "Роза", "玫瑰"],
    "v53_sg_box_1p": ["Kutu (tek kaçış noktası)", "Box (one-point)", "صندوق (نقطة تلاشٍ واحدة)", "箱（一点透視）", "상자(1점 투시)", "Коробка (1 точка)", "盒子（一点透视）"],
    "v53_sg_street_1p": ["Sokak (tek kaçış noktası)", "Street (one-point)", "شارع (نقطة تلاشٍ واحدة)", "街並み（一点透視）", "거리(1점 투시)", "Улица (1 точка)", "街道（一点透视）"],
    "v53_sg_house_2p": ["Ev (iki kaçış noktası)", "House (two-point)", "منزل (نقطتا تلاشٍ)", "家（二点透視）", "집(2점 투시)", "Дом (2 точки)", "房子（两点透视）"],
    "v53_sg_cylinder_lying": ["Yatık silindir", "Lying cylinder", "أسطوانة أفقية", "横たわる円柱", "누운 원기둥", "Лежащий цилиндр", "横放圆柱"],
    "v53_sg_bicycle": ["Bisiklet", "Bicycle", "دراجة", "自転車", "자전거", "Велосипед", "自行车"],
    "v53_sg_airplane": ["Uçak", "Airplane", "طائرة", "飛行機", "비행기", "Самолёт", "飞机"],
    "v53_sg_ship": ["Gemi", "Ship", "سفينة", "船", "배", "Корабль", "轮船"],
    "v53_sg_guitar": ["Gitar", "Guitar", "غيتار", "ギター", "기타", "Гитара", "吉他"],
    "v53_sg_tower": ["Kule", "Tower", "برج", "塔", "탑", "Башня", "塔楼"],
    "v53_sg_teapot": ["Çaydanlık", "Teapot", "إبريق شاي", "ティーポット", "찻주전자", "Чайник", "茶壶"],
    "v53_sg_wine_glass": ["Kadeh", "Stemmed glass", "كأس", "グラス", "유리잔", "Бокал", "高脚杯"],
    "v53_sg_backpack": ["Sırt çantası", "Backpack", "حقيبة ظهر", "リュック", "배낭", "Рюкзак", "背包"],
    "v53_sg_sofa": ["Kanepe", "Sofa", "أريكة", "ソファ", "소파", "Диван", "沙发"],
    "v53_sg_umbrella": ["Şemsiye", "Umbrella", "مظلة", "傘", "우산", "Зонт", "雨伞"],
    "v53_sg_clock": ["Saat", "Clock", "ساعة", "時計", "시계", "Часы", "时钟"],
    "v53_sg_headphones": ["Kulaklık", "Headphones", "سماعات", "ヘッドホン", "헤드폰", "Наушники", "耳机"],
    "v53_sg_laptop": ["Dizüstü bilgisayar", "Laptop", "حاسوب محمول", "ノートパソコン", "노트북", "Ноутбук", "笔记本电脑"],
    "v53_sg_camera": ["Fotoğraf makinesi", "Camera", "كاميرا", "カメラ", "카메라", "Фотоаппарат", "相机"],
}


def main(res):
    for i, lang in enumerate(LANGS):
        d = os.path.join(res, DIRS[lang])
        lines = ['<?xml version="1.0" encoding="utf-8"?>', "<!-- v54: tools/i18n-v53/strings_v54.py ile üretildi; elle değiştirmeyin -->", "<resources>"]
        for k in sorted(S):
            v = S[k]
            assert len(v) == 7, k
            fmt = ' formatted="true"' if "%1$" in v[i] else ""
            lines.append(f'    <string name="{k}"{fmt}>{esc(v[i])}</string>')
        lines.append("</resources>")
        open(os.path.join(d, "strings_v54.xml"), "w", encoding="utf-8").write("\n".join(lines) + "\n")
    print(len(S), "anahtar")


if __name__ == "__main__":
    main(sys.argv[1])
