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
