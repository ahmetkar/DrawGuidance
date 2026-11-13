#!/usr/bin/env python3
"""v50 Yenilikler yazısı: "Resimden çizim" (vektörleştirme ve kalıpla çizme). posts.json'a eklenecek kayıt üretir."""
import json, sys

D = "/blog/resimden-cizim"
def img(lang, n, name): return f"{D}/{lang}-{n}-{name}.webp"

T = {
"tr": dict(title="Yeni: Resimden çizim — vektörleştir, kalıp yap, üstünden çiz",
 summary="Çizim modlarına yeni bir eğitim geldi: bir resmi tek dokunuşla vektör çizgilere çevir, bu çizgileri şekil kalıbı yap ve kalıbın kenarından titremeyen çizgilerle kopyala.",
 body=f"""Çizim Rehberi'nin resim, çizgi roman ve basit resim modlarında yeni bir adım adım eğitim var: **Resimden çizim**. Bir fotoğrafı, eskizi ya da hazır bir çizimi düzenlenebilir vektör çizgilere çevirmeyi, sonra bu çizgileri **şekil kalıbı** olarak kullanıp üstünden çizmeyi altı kısa adımda gösteriyor. Çocuk modunda da kalıpla çizmeyi anlatan iki adımlık sade bir sürümü var.

## Eğitimi nasıl açarım?

Bir resim, çizgi roman ya da basit resim tuvali aç, sağ üstteki **⋮** menüsünden **Eğitim modu**'na dokun ve **Resimden çizim**'i seç. Uygulamayı güncellediysen eğitim ilk girişte kendiliğinden önerilir. Eğitim boş bir sayfada çalışır; bitince ya da iptal edince tuvalin eski haline döner, hiçbir şey kaydedilmez.

## 1. Resmi vektörleştir

Ekle menüsünden bir resim ekle, seç ve **Düzenle**'ye geç. Üst satırda üç tür var:

- **Otomatik vektörleştir**: resme en uygun yöntemi kendisi seçer.
- **Kenarları vektörleştir**: fotoğraflardaki dış hatları ve renk sınırlarını alır.
- **Çizgileri vektörleştir**: kalemle çizilmiş eskizlerin orta çizgisini izler.

Ayrıntı, yumuşatma ve kısa çizgileri atma ayarları alttaki pencerededir. Sonuç, noktaları tek tek düzenlenebilen bir vektör şekildir; resmi silince yalnız çizgiler kalır.

![Demlik resmi vektörleştirildi: resim soluk, üstünde siyah vektör çizgiler]({img("tr", 1, "vektorlestir")})

## 2. Vektörü kalıp yap

Çiz menüsündeki **Şekil kalıbı**'na dokun ve vektör çizgilere dokun: çizim yarı saydam bir kalıp olur. Sol üstteki tutamakla taşı, sağ alttakiyle büyüt ya da küçült. **Kilitle** ile kalıp yerinde kalır.

## 3. Kalıbın kenarından çiz

Kalıbın kenarına yakın çizdiğin her çizgi kenarı izler; elin titrese bile çizgin düzgün çıkar. Böylece bir resmi kendi fırçanla, istediğin renkte ve boyda yeniden çizebilirsin.

![Vektör kalıp yana taşındı ve mavi kalemle kenarlarından çizildi]({img("tr", 2, "kalipla-ciz")})

## 4. Kütüphaneden hazır kalıplar

Kalıp açıkken **Kütüphaneden**'e dokun: kedi, yıldız, kalp, araba, ev ve yüzlerce hazır şekil kalıp olarak gelir. **Bitir** ile normal çizime dönersin.

![Kütüphaneden seçilen kedi kalıbı pembe kalemle çizildi]({img("tr", 3, "kutuphane-kalibi")})

![Bitmiş sayfa: vektörleştirilmiş demlik, kalıpla çizilmiş kopyası ve kedi]({img("tr", 4, "resimden-cizim")})

## Çocuk modunda kalıp

Çocuk modunda **Kalıp** düğmesine dokunup bir şekil seçmek yeterli. Şekil tuvalde silik görünür; çocuk kenarına yakın çizdikçe kalem çizgiyi kendiliğinden izler.

## iPhone ve iPad

Aynı eğitim ve Resmi düzenle satırındaki vektörleştirme düğmeleri iOS sürümüne de eklendi.

Hemen denemek için [uygulamayı indir](/tr/download) ve bir resim tuvalinde **⋮ → Eğitim modu → Resimden çizim**'i aç."""),
"en": dict(title="New: Drawing from images — vectorize, make a stencil, trace over it",
 summary="A new tutorial for the drawing modes: turn an image into vector lines with one tap, use those lines as a shape stencil and copy them with steady, shake-free strokes.",
 body=f"""DrawGuidance's paint, comic and simple paint modes now have a new step-by-step tutorial: **Drawing from images**. In six short steps it shows how to turn a photo, sketch or ready-made drawing into editable vector lines, and then use those lines as a **shape stencil** to draw over. Kids mode gets a simpler two-step version about drawing with stencils.

## How do I open the tutorial?

Open a paint, comic or simple paint canvas, tap **⋮** at the top right, choose **Tutorial mode** and pick **Drawing from images**. After updating, the tutorial is also offered automatically the first time you open a canvas. It runs on a blank page; when you finish or cancel, your canvas goes back to how it was and nothing is saved.

## 1. Vectorize the image

Add an image from the Add menu, select it and switch to **Edit**. The top row has three types:

- **Auto-vectorize** picks the best method for the image.
- **Vectorize edges** keeps outlines and color borders, ideal for photos.
- **Vectorize lines** follows the center lines of pen sketches.

Detail, smoothing and short-line filtering are in the sheet below. The result is a vector shape whose points you can edit one by one; delete the image and only the lines remain.

![The teapot image vectorized: the photo is faded with black vector lines on top]({img("en", 1, "vektorlestir")})

## 2. Turn the vector into a stencil

Tap **Shape template** in the Draw menu and tap the vector lines: the drawing becomes a see-through stencil. Move it with the top-left handle and resize it with the bottom-right one. **Lock** keeps it in place.

## 3. Trace along the edge

Every line you draw close to the stencil's edge follows that edge, so your line stays smooth even if your hand shakes. Redraw any image with your own brush, in any color and size.

![The vector stencil moved aside and traced along its edges with a blue pen]({img("en", 2, "kalipla-ciz")})

## 4. Ready-made stencils from the library

While the stencil is open, tap **From library**: a cat, a star, a heart, a car, a house and hundreds of other shapes become stencils. Tap **Finish** to go back to normal drawing.

![A cat stencil from the library traced with a pink pen]({img("en", 3, "kutuphane-kalibi")})

![The finished page: the vectorized teapot, its traced copy and the cat]({img("en", 4, "resimden-cizim")})

## Stencils in kids mode

In kids mode, kids just tap **Stencil** and pick a shape. It appears faintly on the canvas, and the pen follows the line by itself as they draw near its edge.

## iPhone and iPad

The same tutorial, plus the vectorize buttons in the Edit image row, are coming to the iOS version too.

Want to try it? [Download the app](/en/download) and open **⋮ → Tutorial mode → Drawing from images** on a paint canvas."""),
}

# Öteki diller: başlık, özet ve kısa gövde (görseller İngilizce arayüzden değil, kendi dillerinden)
SHORT = {
"ar": ("جديد: الرسم من الصور — حوّل إلى متجهات، واصنع قالبًا، وارسم فوقه",
 "درس جديد لأوضاع الرسم: حوّل الصورة إلى خطوط متجهة بلمسة واحدة، واجعلها قالبًا، وانسخها بخطوط ثابتة.",
 ["في أوضاع الرسم والقصص المصوّرة والرسم البسيط في دليل الرسم درس جديد خطوة بخطوة: **الرسم من الصور**. يعرض في ست خطوات قصيرة كيف تحوّل صورة أو رسمًا تخطيطيًا إلى خطوط متجهة قابلة للتعديل، ثم تستخدمها **قالبًا** وترسم فوقه. وفي وضع الأطفال نسخة مبسّطة من خطوتين.",
  "## كيف أفتح الدرس؟", "افتح لوحة رسم أو قصة مصوّرة أو رسم بسيط، واضغط **⋮** ثم **وضع التعليم** واختر **الرسم من الصور**. يعمل الدرس على صفحة فارغة ولا يُحفظ شيء.",
  "## 1. حوّل الصورة إلى متجهات", "أضف صورة وحدّدها وانتقل إلى **تعديل**. في الصف العلوي ثلاثة أنواع: **تحويل تلقائي إلى متجهات**، و**تحويل الحواف إلى متجهات** للصور الفوتوغرافية، و**تحويل الخطوط إلى متجهات** للرسوم بالقلم.",
  "IMG1", "## 2. اجعل المتجه قالبًا", "اضغط **قالب الشكل** ثم المس الخطوط: يصبح الرسم قالبًا شفافًا. حرّكه وغيّر حجمه بالمقابض، واضغط **قفل** لتثبيته.",
  "## 3. ارسم على الحافة", "كل خط ترسمه قرب حافة القالب يتبعها، فيبقى خطك ثابتًا حتى لو اهتزت يدك.", "IMG2",
  "## 4. قوالب جاهزة من المكتبة", "اضغط **من المكتبة** واختر قطة أو نجمة أو قلبًا أو مئات الأشكال الأخرى. اضغط **إنهاء** للعودة إلى الرسم العادي.", "IMG3", "IMG4",
  "الدرس نفسه وأزرار التحويل إلى متجهات متوفرة أيضًا في إصدار iOS. [حمّل التطبيق](/ar/download) وجرّبه الآن."]),
"ja": ("新機能：画像から描く — ベクター化して、テンプレートにして、なぞる",
 "描画モードに新しいチュートリアル。ワンタップで画像をベクター線に変え、図形テンプレートにして、ぶれない線でなぞれます。",
 ["お絵かきガイドのペイント、マンガ、シンプルペイントの各モードに新しいステップ解説 **画像から描く** が加わりました。写真やスケッチを編集できるベクター線に変え、その線を **図形テンプレート** にしてなぞる方法を6つのステップで紹介します。キッズモードには2ステップの簡単版があります。",
  "## チュートリアルの開き方", "ペイント、マンガ、シンプルペイントのキャンバスを開き、右上の **⋮** から **チュートリアルモード** を選んで **画像から描く** をタップします。空のページで動くので、何も保存されません。",
  "## 1. 画像をベクター化", "画像を追加して選択し、**編集** に切り替えます。上の列に **自動ベクター化**、写真向けの **エッジをベクター化**、ペン画向けの **線をベクター化** の3種類があります。",
  "IMG1", "## 2. ベクターをテンプレートに", "**図形テンプレート** をタップして線に触れると、半透明のテンプレートになります。ハンドルで移動・拡大縮小し、**ロック** で固定できます。",
  "## 3. 縁をなぞる", "テンプレートの縁の近くを描くと、線が縁に沿います。手がふるえてもきれいな線になります。", "IMG2",
  "## 4. ライブラリのテンプレート", "**ライブラリから** をタップすると、ネコ、星、ハートなど数百の図形をテンプレートにできます。**完了** で通常の描画に戻ります。", "IMG3", "IMG4",
  "同じチュートリアルとベクター化ボタンは iOS 版にも追加されます。[アプリをダウンロード](/ja/download)して試してみてください。"]),
"ko": ("새 기능: 이미지로 그리기 — 벡터화하고, 템플릿으로 만들고, 따라 그리기",
 "그리기 모드에 새 튜토리얼이 추가되었습니다. 한 번의 탭으로 이미지를 벡터 선으로 바꾸고, 템플릿으로 만들어 흔들림 없이 따라 그려 보세요.",
 ["그리기 가이드의 그림, 만화, 쉬운 그림 모드에 새로운 단계별 튜토리얼 **이미지로 그리기**가 생겼습니다. 사진이나 스케치를 편집 가능한 벡터 선으로 바꾸고, 그 선을 **도형 템플릿**으로 삼아 따라 그리는 방법을 여섯 단계로 보여 줍니다. 어린이 모드에는 두 단계의 쉬운 버전이 있습니다.",
  "## 튜토리얼 여는 방법", "그림, 만화 또는 쉬운 그림 캔버스를 열고 오른쪽 위 **⋮**에서 **튜토리얼 모드**를 고른 뒤 **이미지로 그리기**를 선택하세요. 빈 페이지에서 진행되며 아무것도 저장되지 않습니다.",
  "## 1. 이미지 벡터화", "이미지를 추가해 선택하고 **편집**으로 이동하세요. 위쪽 줄에 **자동 벡터화**, 사진에 알맞은 **가장자리 벡터화**, 펜 스케치에 알맞은 **선 벡터화**가 있습니다.",
  "IMG1", "## 2. 벡터를 템플릿으로", "**도형 템플릿**을 탭하고 선을 누르면 반투명 템플릿이 됩니다. 핸들로 옮기고 크기를 바꾸며, **잠금**으로 고정할 수 있습니다.",
  "## 3. 가장자리 따라 그리기", "템플릿 가장자리 가까이 그리면 선이 가장자리를 따라가서, 손이 떨려도 깔끔한 선이 됩니다.", "IMG2",
  "## 4. 라이브러리 템플릿", "**라이브러리에서**를 탭하면 고양이, 별, 하트 등 수백 가지 도형을 템플릿으로 쓸 수 있습니다. **완료**를 누르면 일반 그리기로 돌아갑니다.", "IMG3", "IMG4",
  "같은 튜토리얼과 벡터화 버튼은 iOS 버전에도 추가됩니다. [앱을 다운로드](/ko/download)하고 지금 사용해 보세요."]),
"ru": ("Новое: рисование по картинке — векторизуйте, сделайте шаблон, обведите",
 "Новый урок для режимов рисования: одно касание превращает картинку в векторные линии, которые становятся шаблоном для ровной обводки.",
 ["В режимах рисования, комиксов и простого рисования в Гиде рисования появился новый пошаговый урок — **Рисование по картинке**. За шесть коротких шагов он показывает, как превратить фото или эскиз в редактируемые векторные линии и затем рисовать по ним как по **шаблону фигуры**. В детском режиме есть упрощённая версия из двух шагов.",
  "## Как открыть урок?", "Откройте холст рисования, комикса или простого рисования, нажмите **⋮** вверху справа, выберите **Режим обучения** и **Рисование по картинке**. Урок идёт на пустой странице и ничего не сохраняет.",
  "## 1. Векторизуйте изображение", "Добавьте изображение, выберите его и перейдите в **Правку**. В верхнем ряду три вида: **Автовекторизация**, **Векторизовать края** для фотографий и **Векторизовать линии** для эскизов пером.",
  "IMG1", "## 2. Вектор как шаблон", "Нажмите **Шаблон фигуры** и коснитесь линий — рисунок станет полупрозрачным шаблоном. Перемещайте и масштабируйте его маркерами, **Закрепить** фиксирует его на месте.",
  "## 3. Обводите по краю", "Каждая линия рядом с краем шаблона следует за ним, поэтому даже дрожащая рука рисует ровно.", "IMG2",
  "## 4. Готовые шаблоны из библиотеки", "Нажмите **Из библиотеки** и выберите кошку, звезду, сердце или сотни других фигур. **Готово** возвращает к обычному рисованию.", "IMG3", "IMG4",
  "Этот урок и кнопки векторизации появятся и в версии для iOS. [Скачайте приложение](/ru/download) и попробуйте."]),
"zh": ("新功能：从图片绘画——矢量化、做成模板、沿边描画",
 "绘画模式新增教程：轻点一下把图片变成矢量线条，再把线条做成形状模板，用稳定不抖的线条描出来。",
 ["绘画向导的绘画、漫画和简易绘画模式新增了分步教程 **从图片绘画**。它用六个简短步骤演示如何把照片或草图变成可编辑的矢量线条，再把这些线条当作 **形状模板** 来描画。儿童模式还有两步的简化版本。",
  "## 如何打开教程？", "打开绘画、漫画或简易绘画画布，点按右上角的 **⋮**，选择 **教程模式**，再选 **从图片绘画**。教程在空白页面上运行，不会保存任何内容。",
  "## 1. 矢量化图片", "添加一张图片，选中后切换到 **编辑**。顶部一行有三种方式：**自动矢量化**、适合照片的 **矢量化边缘**，以及适合钢笔草图的 **矢量化线条**。",
  "IMG1", "## 2. 把矢量做成模板", "点按 **形状模板** 再点按线条，画作就变成半透明模板。用控制柄移动和缩放，点按 **锁定** 固定位置。",
  "## 3. 沿边描画", "靠近模板边缘画的每一笔都会贴着边走，即使手抖线条也很平滑。", "IMG2",
  "## 4. 形状库中的现成模板", "点按 **从形状库**，选择猫、星星、爱心等数百种形状作为模板。点按 **完成** 回到普通绘画。", "IMG3", "IMG4",
  "同样的教程和矢量化按钮也将加入 iOS 版本。[下载应用](/zh/download) 立即试试吧。"]),
}
ALTS = {
 "ar": ["صورة إبريق الشاي بعد تحويلها إلى متجهات", "القالب المتجه منقول إلى الجانب ومرسوم بقلم أزرق", "قالب قطة من المكتبة مرسوم بقلم وردي", "الصفحة النهائية"],
 "ja": ["ベクター化したティーポットの画像", "横に移動したテンプレートを青いペンでなぞった様子", "ライブラリのネコのテンプレートをピンクのペンでなぞった様子", "完成したページ"],
 "ko": ["벡터화한 찻주전자 이미지", "옆으로 옮긴 템플릿을 파란 펜으로 따라 그린 모습", "라이브러리 고양이 템플릿을 분홍 펜으로 그린 모습", "완성된 페이지"],
 "ru": ["Векторизованное изображение чайника", "Шаблон сдвинут в сторону и обведён синим пером", "Шаблон кошки из библиотеки, обведённый розовым пером", "Готовая страница"],
 "zh": ["矢量化后的茶壶图片", "移到一旁并用蓝色钢笔描边的矢量模板", "用粉色钢笔描出的形状库猫模板", "完成的页面"],
}
NAMES = ["vektorlestir", "kalipla-ciz", "kutuphane-kalibi", "resimden-cizim"]
for lang, (title, summary, parts) in SHORT.items():
    body = []
    for p in parts:
        if p.startswith("IMG"):
            n = int(p[3:])
            body.append(f"![{ALTS[lang][n-1]}]({img(lang, n, NAMES[n-1])})")
        else:
            body.append(p)
    T[lang] = dict(title=title, summary=summary, body="\n\n".join(body))

post = {"slug": "resimden-cizim-vektorlestirme-ve-kalip", "cover": f"{D}/kapak.webp", "published": True,
        "publishedAt": "2026-11-14T09:00:00.000Z", "translations": {l: T[l] for l in ["tr", "en", "zh", "ja", "ko", "ru", "ar"]}}
json.dump(post, open(sys.argv[1], "w", encoding="utf-8"), ensure_ascii=False, indent=2)
print("ok", len(json.dumps(post)))
