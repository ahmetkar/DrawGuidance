# -*- coding: utf-8 -*-
import os, sys
RES = sys.argv[1]
keys = ["title","open","settings_hint","open_page","this_device","device_name","device_name_hint","rename","visible","off",
"nearby_title","nearby_empty","route_nearby","route_lan","connect","connecting","connected","disconnect",
"send_canvas","send_image","start_live","pick_canvas","pick_live","pick_image","no_canvases","no_images",
"permission_title","permission_text","permission_allow","request_title","request_text","request_code","accept","reject",
"offer_title","offer_canvas","offer_image","offer_live","offer_size","sending","receiving","received_canvas","received_image",
"open_canvas","close","sent","rejected","failed","disconnected","live_out","live_in","live_stop","live_leave","live_open",
"live_badge","live_ended","live_title","live_waiting","live_edit","settings_title","ask","ask_hint","folder","folder_hint",
"smooth","smooth_hint","shared_badge","platform_ios","platform_android","error","how","save","folder_name","shared_live_badge"]
T = {}
T["tr"] = ["Cihazlar arası paylaşım","Paylaşım","Yakındaki cihazlarla Wi-Fi Direct, Bluetooth ya da aynı Wi-Fi üzerinden tuval ve resim paylaşın, canlı yayın yapın","Paylaşım sayfasını aç","Bu cihaz","Cihaz adı","Diğer cihazlarda bu adla görünürsünüz","Adı değiştir","Görünür · yakındaki cihazlar aranıyor","Görünür değil",
"Yakındaki cihazlar","Uygulamayı kullanan yakındaki cihazlar burada görünür. Diğer cihazda da paylaşım sayfasını açın.","Wi-Fi Direct / Bluetooth","Aynı Wi-Fi ağı","Bağlan","Bağlanıyor…","Bağlı","Bağlantıyı kes",
"Tuval gönder","Resim gönder","Yayın başlat","Gönderilecek tuvali seçin","Yayın yapılacak tuvali seçin","Gönderilecek resmi seçin","Henüz tuval yok","Kaydedilmiş resim yok",
"Wi-Fi Direct ve Bluetooth izni","Yakındaki Android cihazları bulmak ve onlara Wi-Fi Direct ya da Bluetooth ile bağlanmak için izin gerekir. İzin vermezseniz yalnız aynı Wi-Fi ağındaki cihazlar görünür.","İzin ver","Bağlantı isteği","%1$s bu cihaza bağlanmak istiyor.","Doğrulama kodu: %1$s (iki cihazda aynı olmalı)","Kabul et","Reddet",
"Gelen paylaşım","%1$s size “%2$s” tuvalini göndermek istiyor.","%1$s size “%2$s” resmini göndermek istiyor.","%1$s “%2$s” tuvalini sizinle canlı yayınlamak istiyor. Yapılan değişiklikler bu cihazda anında görünür.","Boyut: %1$s","“%1$s” → %2$s gönderiliyor…","%1$s cihazından alınıyor…","“%1$s” tuvali alındı (%2$s) ve Tuvallerim'e kaydedildi.","“%1$s” resmi alındı (%2$s) ve Kaydedilenler'e eklendi.",
"Tuvali aç","Kapat","“%1$s” gönderildi: %2$s","%1$s paylaşımı reddetti","Paylaşım tamamlanamadı: %1$s","%1$s ile bağlantı kesildi","Yayında: “%1$s” → %2$s","%1$s yayını: “%2$s”","Yayını durdur","Yayından çık","Tuvali aç",
"Yayında","Yayın bitti. Tuval Tuvallerim'de kayıtlı.","Canlı yayın","Yayın bekleniyor…","Düzenle","Paylaşım ayarları","Gelenleri onaylayarak al","Kapalıysa bağlı cihazlardan gelen tuval, resim ve yayınlar sormadan alınır","Alınanları “Paylaşılanlar” klasörüne koy","Alınan tuvaller Tuvallerim'de ayrı bir klasörde toplanır",
"Akıcı yayın","Yayın sık güncellenir; yavaş Bluetooth bağlantısında kapatın","Paylaşılan · %1$s","iPhone / iPad","Android","Bağlantı uyarısı: %1$s","Android cihazlar birbirini Bluetooth ile bulur ve Wi-Fi Direct ile bağlanır. iPhone ya da iPad ile paylaşmak için iki cihaz aynı Wi-Fi ağında olmalı.","Kaydet","Paylaşılanlar","Canlı yayından · %1$s"]
T["en"] = ["Share between devices","Share","Share canvases and images with nearby devices over Wi-Fi Direct, Bluetooth or the same Wi-Fi, and broadcast live","Open the share page","This device","Device name","Other devices see you by this name","Rename","Visible · searching for nearby devices","Not visible",
"Nearby devices","Nearby devices using the app appear here. Open the share page on the other device too.","Wi-Fi Direct / Bluetooth","Same Wi-Fi network","Connect","Connecting…","Connected","Disconnect",
"Send canvas","Send image","Start broadcast","Choose a canvas to send","Choose a canvas to broadcast","Choose an image to send","No canvases yet","No saved images",
"Wi-Fi Direct and Bluetooth permission","Permission is needed to find nearby Android devices and connect to them over Wi-Fi Direct or Bluetooth. Without it, only devices on the same Wi-Fi network appear.","Allow","Connection request","%1$s wants to connect to this device.","Verification code: %1$s (must match on both devices)","Accept","Decline",
"Incoming share","%1$s wants to send you the canvas “%2$s”.","%1$s wants to send you the image “%2$s”.","%1$s wants to broadcast the canvas “%2$s” to you live. Changes appear on this device instantly.","Size: %1$s","Sending “%1$s” → %2$s…","Receiving from %1$s…","Canvas “%1$s” received (%2$s) and saved to My canvases.","Image “%1$s” received (%2$s) and added to Saved.",
"Open canvas","Close","“%1$s” sent: %2$s","%1$s declined the share","Share could not be completed: %1$s","Disconnected from %1$s","Live: “%1$s” → %2$s","%1$s is broadcasting “%2$s”","Stop broadcast","Leave broadcast","Open canvas",
"Live","The broadcast has ended. The canvas is saved in My canvases.","Live broadcast","Waiting for the broadcast…","Edit","Share settings","Confirm before receiving","When off, canvases, images and broadcasts from connected devices are received without asking","Put received canvases in a “Shared” folder","Received canvases are collected in their own folder in My canvases",
"Smooth broadcast","The broadcast updates often; turn off on slow Bluetooth connections","Shared · %1$s","iPhone / iPad","Android","Connection warning: %1$s","Android devices find each other over Bluetooth and connect over Wi-Fi Direct. To share with an iPhone or iPad, both devices must be on the same Wi-Fi network.","Save","Shared","From a live broadcast · %1$s"]
T["ar"] = ["المشاركة بين الأجهزة","مشاركة","شارك اللوحات والصور مع الأجهزة القريبة عبر Wi-Fi Direct أو البلوتوث أو شبكة Wi-Fi نفسها، وابثّ مباشرةً","افتح صفحة المشاركة","هذا الجهاز","اسم الجهاز","تراك الأجهزة الأخرى بهذا الاسم","تغيير الاسم","مرئي · جارٍ البحث عن الأجهزة القريبة","غير مرئي",
"الأجهزة القريبة","تظهر هنا الأجهزة القريبة التي تستخدم التطبيق. افتح صفحة المشاركة على الجهاز الآخر أيضًا.","Wi-Fi Direct / بلوتوث","شبكة Wi-Fi نفسها","اتصال","جارٍ الاتصال…","متصل","قطع الاتصال",
"إرسال لوحة","إرسال صورة","بدء البث","اختر لوحة لإرسالها","اختر لوحة لبثها","اختر صورة لإرسالها","لا توجد لوحات بعد","لا توجد صور محفوظة",
"إذن Wi-Fi Direct والبلوتوث","يلزم إذن للعثور على أجهزة Android القريبة والاتصال بها عبر Wi-Fi Direct أو البلوتوث. بدونه تظهر فقط الأجهزة الموجودة على شبكة Wi-Fi نفسها.","سماح","طلب اتصال","يريد %1$s الاتصال بهذا الجهاز.","رمز التحقق: %1$s (يجب أن يتطابق على الجهازين)","قبول","رفض",
"مشاركة واردة","يريد %1$s إرسال اللوحة «%2$s» إليك.","يريد %1$s إرسال الصورة «%2$s» إليك.","يريد %1$s بث اللوحة «%2$s» إليك مباشرةً. تظهر التغييرات على هذا الجهاز فورًا.","الحجم: %1$s","جارٍ إرسال «%1$s» ← %2$s…","جارٍ الاستلام من %1$s…","تم استلام اللوحة «%1$s» (%2$s) وحفظها في لوحاتي.","تم استلام الصورة «%1$s» (%2$s) وإضافتها إلى المحفوظات.",
"فتح اللوحة","إغلاق","تم إرسال «%1$s»: %2$s","رفض %1$s المشاركة","تعذّر إكمال المشاركة: %1$s","انقطع الاتصال مع %1$s","مباشر: «%1$s» ← %2$s","%1$s يبث «%2$s»","إيقاف البث","مغادرة البث","فتح اللوحة",
"مباشر","انتهى البث. اللوحة محفوظة في لوحاتي.","بث مباشر","في انتظار البث…","تعديل","إعدادات المشاركة","التأكيد قبل الاستلام","عند الإيقاف تُستلم اللوحات والصور والبثوث من الأجهزة المتصلة دون سؤال","ضع اللوحات المستلمة في مجلد «المشتركة»","تُجمع اللوحات المستلمة في مجلد خاص في لوحاتي",
"بث سلس","يتحدّث البث كثيرًا؛ أوقفه مع اتصالات البلوتوث البطيئة","مشتركة · %1$s","iPhone / iPad","Android","تحذير الاتصال: %1$s","تعثر أجهزة Android على بعضها عبر البلوتوث وتتصل عبر Wi-Fi Direct. للمشاركة مع iPhone أو iPad يجب أن يكون الجهازان على شبكة Wi-Fi نفسها.","حفظ","المشتركة","من بث مباشر · %1$s"]
T["ja"] = ["デバイス間で共有","共有","近くのデバイスと Wi-Fi Direct、Bluetooth、または同じ Wi-Fi でキャンバスや画像を共有し、ライブ配信できます","共有ページを開く","このデバイス","デバイス名","ほかのデバイスにはこの名前で表示されます","名前を変更","表示中 · 近くのデバイスを検索しています","非表示",
"近くのデバイス","アプリを使っている近くのデバイスがここに表示されます。相手のデバイスでも共有ページを開いてください。","Wi-Fi Direct / Bluetooth","同じ Wi-Fi ネットワーク","接続","接続中…","接続済み","切断",
"キャンバスを送る","画像を送る","配信を開始","送るキャンバスを選択","配信するキャンバスを選択","送る画像を選択","キャンバスはまだありません","保存した画像はありません",
"Wi-Fi Direct と Bluetooth の許可","近くの Android デバイスを見つけて Wi-Fi Direct や Bluetooth で接続するには許可が必要です。許可しない場合は同じ Wi-Fi ネットワーク上のデバイスのみ表示されます。","許可","接続リクエスト","%1$s がこのデバイスに接続しようとしています。","確認コード: %1$s(両方のデバイスで一致する必要があります)","許可する","拒否",
"共有の受信","%1$s がキャンバス「%2$s」を送ろうとしています。","%1$s が画像「%2$s」を送ろうとしています。","%1$s がキャンバス「%2$s」をライブ配信しようとしています。変更はこのデバイスにすぐ表示されます。","サイズ: %1$s","「%1$s」を %2$s に送信中…","%1$s から受信中…","キャンバス「%1$s」を受信しました(%2$s)。マイキャンバスに保存しました。","画像「%1$s」を受信しました(%2$s)。保存済みに追加しました。",
"キャンバスを開く","閉じる","「%1$s」を送信しました: %2$s","%1$s が共有を拒否しました","共有を完了できませんでした: %1$s","%1$s との接続が切れました","配信中: 「%1$s」→ %2$s","%1$s の配信: 「%2$s」","配信を停止","配信から退出","キャンバスを開く",
"配信中","配信は終了しました。キャンバスはマイキャンバスに保存されています。","ライブ配信","配信を待っています…","編集","共有の設定","受信前に確認する","オフにすると、接続中のデバイスからのキャンバス・画像・配信を確認なしで受信します","受信したキャンバスを「共有」フォルダーに入れる","受信したキャンバスはマイキャンバスの専用フォルダーにまとめられます",
"なめらかな配信","配信を頻繁に更新します。遅い Bluetooth 接続ではオフにしてください","共有 · %1$s","iPhone / iPad","Android","接続の警告: %1$s","Android デバイスは Bluetooth で互いを見つけ、Wi-Fi Direct で接続します。iPhone や iPad と共有するには、両方のデバイスが同じ Wi-Fi ネットワークにある必要があります。","保存","共有","ライブ配信から · %1$s"]
T["ko"] = ["기기 간 공유","공유","근처 기기와 Wi-Fi Direct, 블루투스 또는 같은 Wi-Fi로 캔버스와 이미지를 공유하고 실시간 방송하세요","공유 페이지 열기","이 기기","기기 이름","다른 기기에는 이 이름으로 표시됩니다","이름 변경","표시 중 · 근처 기기를 찾는 중","표시 안 함",
"근처 기기","앱을 사용하는 근처 기기가 여기에 표시됩니다. 상대 기기에서도 공유 페이지를 여세요.","Wi-Fi Direct / 블루투스","같은 Wi-Fi 네트워크","연결","연결 중…","연결됨","연결 끊기",
"캔버스 보내기","이미지 보내기","방송 시작","보낼 캔버스를 선택하세요","방송할 캔버스를 선택하세요","보낼 이미지를 선택하세요","아직 캔버스가 없습니다","저장된 이미지가 없습니다",
"Wi-Fi Direct 및 블루투스 권한","근처 Android 기기를 찾고 Wi-Fi Direct 또는 블루투스로 연결하려면 권한이 필요합니다. 허용하지 않으면 같은 Wi-Fi 네트워크의 기기만 표시됩니다.","허용","연결 요청","%1$s 기기가 이 기기에 연결하려고 합니다.","확인 코드: %1$s (두 기기에서 같아야 합니다)","수락","거절",
"받은 공유","%1$s 기기가 캔버스 “%2$s”을(를) 보내려고 합니다.","%1$s 기기가 이미지 “%2$s”을(를) 보내려고 합니다.","%1$s 기기가 캔버스 “%2$s”을(를) 실시간 방송하려고 합니다. 변경 사항이 이 기기에 즉시 표시됩니다.","크기: %1$s","“%1$s” → %2$s 보내는 중…","%1$s에서 받는 중…","캔버스 “%1$s”을(를) 받았습니다(%2$s). 내 캔버스에 저장했습니다.","이미지 “%1$s”을(를) 받았습니다(%2$s). 저장됨에 추가했습니다.",
"캔버스 열기","닫기","“%1$s” 보냄: %2$s","%1$s 기기가 공유를 거절했습니다","공유를 완료하지 못했습니다: %1$s","%1$s 기기와 연결이 끊겼습니다","방송 중: “%1$s” → %2$s","%1$s 방송: “%2$s”","방송 중지","방송 나가기","캔버스 열기",
"방송 중","방송이 끝났습니다. 캔버스는 내 캔버스에 저장되어 있습니다.","실시간 방송","방송을 기다리는 중…","편집","공유 설정","받기 전에 확인","끄면 연결된 기기의 캔버스, 이미지, 방송을 묻지 않고 받습니다","받은 캔버스를 “공유됨” 폴더에 넣기","받은 캔버스는 내 캔버스의 별도 폴더에 모입니다",
"부드러운 방송","방송을 자주 갱신합니다. 느린 블루투스 연결에서는 끄세요","공유됨 · %1$s","iPhone / iPad","Android","연결 경고: %1$s","Android 기기는 블루투스로 서로를 찾고 Wi-Fi Direct로 연결합니다. iPhone이나 iPad와 공유하려면 두 기기가 같은 Wi-Fi 네트워크에 있어야 합니다.","저장","공유됨","실시간 방송에서 · %1$s"]
T["ru"] = ["Обмен между устройствами","Обмен","Делитесь холстами и изображениями с устройствами поблизости через Wi-Fi Direct, Bluetooth или одну сеть Wi-Fi и ведите трансляцию","Открыть страницу обмена","Это устройство","Имя устройства","Под этим именем вас видят другие устройства","Переименовать","Видимо · идёт поиск устройств поблизости","Не видно",
"Устройства поблизости","Здесь появятся устройства поблизости с этим приложением. Откройте страницу обмена и на другом устройстве.","Wi-Fi Direct / Bluetooth","Та же сеть Wi-Fi","Подключить","Подключение…","Подключено","Отключить",
"Отправить холст","Отправить изображение","Начать трансляцию","Выберите холст для отправки","Выберите холст для трансляции","Выберите изображение для отправки","Холстов пока нет","Нет сохранённых изображений",
"Разрешение Wi-Fi Direct и Bluetooth","Разрешение нужно, чтобы находить устройства Android поблизости и подключаться к ним через Wi-Fi Direct или Bluetooth. Без него видны только устройства в той же сети Wi-Fi.","Разрешить","Запрос на подключение","%1$s хочет подключиться к этому устройству.","Код проверки: %1$s (должен совпадать на обоих устройствах)","Принять","Отклонить",
"Входящий обмен","%1$s хочет отправить вам холст «%2$s».","%1$s хочет отправить вам изображение «%2$s».","%1$s хочет транслировать вам холст «%2$s». Изменения сразу появятся на этом устройстве.","Размер: %1$s","Отправка «%1$s» → %2$s…","Получение от %1$s…","Холст «%1$s» получен (%2$s) и сохранён в «Мои холсты».","Изображение «%1$s» получено (%2$s) и добавлено в «Сохранённое».",
"Открыть холст","Закрыть","«%1$s» отправлен: %2$s","%1$s отклонил(а) обмен","Не удалось завершить обмен: %1$s","Соединение с %1$s разорвано","В эфире: «%1$s» → %2$s","%1$s транслирует «%2$s»","Остановить трансляцию","Выйти из трансляции","Открыть холст",
"В эфире","Трансляция завершена. Холст сохранён в «Мои холсты».","Трансляция","Ожидание трансляции…","Изменить","Настройки обмена","Подтверждать получение","Если выключено, холсты, изображения и трансляции от подключённых устройств принимаются без вопроса","Помещать полученные холсты в папку «Полученные»","Полученные холсты собираются в отдельной папке в «Мои холсты»",
"Плавная трансляция","Трансляция обновляется часто; выключите при медленном Bluetooth","Полученный · %1$s","iPhone / iPad","Android","Предупреждение соединения: %1$s","Устройства Android находят друг друга по Bluetooth и соединяются через Wi-Fi Direct. Чтобы обмениваться с iPhone или iPad, оба устройства должны быть в одной сети Wi-Fi.","Сохранить","Полученные","Из трансляции · %1$s"]
T["zh"] = ["设备间共享","共享","通过 Wi-Fi 直连、蓝牙或同一 Wi-Fi 与附近设备共享画布和图片，并进行直播","打开共享页面","本设备","设备名称","其他设备将以此名称看到你","重命名","可见 · 正在搜索附近设备","不可见",
"附近设备","使用本应用的附近设备会显示在这里。请在另一台设备上也打开共享页面。","Wi-Fi 直连 / 蓝牙","同一 Wi-Fi 网络","连接","正在连接…","已连接","断开连接",
"发送画布","发送图片","开始直播","选择要发送的画布","选择要直播的画布","选择要发送的图片","还没有画布","没有已保存的图片",
"Wi-Fi 直连和蓝牙权限","需要权限才能查找附近的 Android 设备并通过 Wi-Fi 直连或蓝牙连接。不授予权限时，只会显示同一 Wi-Fi 网络中的设备。","允许","连接请求","%1$s 想要连接到本设备。","验证码：%1$s（两台设备上应一致）","接受","拒绝",
"收到共享","%1$s 想向你发送画布“%2$s”。","%1$s 想向你发送图片“%2$s”。","%1$s 想向你直播画布“%2$s”。所做的更改会立即显示在本设备上。","大小：%1$s","正在发送“%1$s”→ %2$s…","正在从 %1$s 接收…","已收到画布“%1$s”（%2$s），已保存到我的画布。","已收到图片“%1$s”（%2$s），已添加到已保存。",
"打开画布","关闭","已发送“%1$s”：%2$s","%1$s 拒绝了共享","共享未能完成：%1$s","与 %1$s 的连接已断开","直播中：“%1$s”→ %2$s","%1$s 正在直播“%2$s”","停止直播","退出直播","打开画布",
"直播中","直播已结束。画布已保存在我的画布中。","直播","正在等待直播…","编辑","共享设置","接收前确认","关闭后，来自已连接设备的画布、图片和直播将直接接收","将收到的画布放入“已共享”文件夹","收到的画布会集中在我的画布中的单独文件夹里",
"流畅直播","直播会频繁更新；蓝牙较慢时请关闭","已共享 · %1$s","iPhone / iPad","Android","连接警告：%1$s","Android 设备通过蓝牙互相发现，并通过 Wi-Fi 直连连接。要与 iPhone 或 iPad 共享，两台设备必须连接同一 Wi-Fi 网络。","保存","已共享","来自直播 · %1$s"]

D = {lang: dict(zip(keys, vals)) for lang, vals in T.items()}
drop = ["route_nearby","permission_title","permission_text","permission_allow","request_title","request_text","request_code","connect"]
new = {
 "smooth_hint": {"tr":"Yayın sık güncellenir; yavaş Wi-Fi ağında kapatın","en":"The broadcast updates often; turn off on a slow Wi-Fi network","ar":"يتحدّث البث كثيرًا؛ أوقفه على شبكة Wi-Fi البطيئة","ja":"配信を頻繁に更新します。遅い Wi-Fi ではオフにしてください","ko":"방송을 자주 갱신합니다. 느린 Wi-Fi에서는 끄세요","ru":"Трансляция обновляется часто; выключите в медленной сети Wi-Fi","zh":"直播会频繁更新；Wi-Fi 较慢时请关闭"},
 "settings_hint": {"tr":"Aynı Wi-Fi ağındaki cihazlarla (Android, iPhone, iPad) tuval ve resim paylaşın, canlı yayın yapın","en":"Share canvases and images with devices on the same Wi-Fi network (Android, iPhone, iPad) and broadcast live","ar":"شارك اللوحات والصور مع الأجهزة على شبكة Wi-Fi نفسها (Android وiPhone وiPad) وابثّ مباشرةً","ja":"同じ Wi-Fi ネットワークのデバイス(Android、iPhone、iPad)とキャンバスや画像を共有し、ライブ配信できます","ko":"같은 Wi-Fi 네트워크의 기기(Android, iPhone, iPad)와 캔버스와 이미지를 공유하고 실시간 방송하세요","ru":"Делитесь холстами и изображениями с устройствами в той же сети Wi-Fi (Android, iPhone, iPad) и ведите трансляцию","zh":"与同一 Wi-Fi 网络中的设备（Android、iPhone、iPad）共享画布和图片，并进行直播"},
 "nearby_title": {"tr":"Aynı ağdaki cihazlar","en":"Devices on this network","ar":"الأجهزة على هذه الشبكة","ja":"このネットワークのデバイス","ko":"이 네트워크의 기기","ru":"Устройства в этой сети","zh":"此网络中的设备"},
 "nearby_empty": {"tr":"Aynı Wi-Fi ağında uygulamayı kullanan cihazlar burada kendiliğinden görünür.","en":"Devices on the same Wi-Fi network that use the app appear here automatically.","ar":"تظهر هنا تلقائيًا الأجهزة التي تستخدم التطبيق على شبكة Wi-Fi نفسها.","ja":"同じ Wi-Fi ネットワークでアプリを使っているデバイスがここに自動的に表示されます。","ko":"같은 Wi-Fi 네트워크에서 앱을 사용하는 기기가 여기에 자동으로 표시됩니다.","ru":"Устройства с этим приложением в той же сети Wi-Fi появляются здесь автоматически.","zh":"同一 Wi-Fi 网络中使用本应用的设备会自动显示在这里。"},
 "how": {"tr":"İki cihaz aynı Wi-Fi ağında olmalı. Uygulama açıkken cihazlar birbirini kendiliğinden bulur; gönder ya da yayın başlat dediğinizde bağlantı kendiliğinden kurulur.","en":"Both devices must be on the same Wi-Fi network. While the app is open, devices find each other automatically; the connection is made automatically when you send or start a broadcast.","ar":"يجب أن يكون الجهازان على شبكة Wi-Fi نفسها. أثناء فتح التطبيق تعثر الأجهزة على بعضها تلقائيًا، ويُنشأ الاتصال تلقائيًا عند الإرسال أو بدء البث.","ja":"2 台のデバイスが同じ Wi-Fi ネットワークにある必要があります。アプリを開いている間はデバイスが自動的に見つかり、送信や配信の開始時に自動で接続されます。","ko":"두 기기가 같은 Wi-Fi 네트워크에 있어야 합니다. 앱이 열려 있는 동안 기기가 서로를 자동으로 찾고, 보내기나 방송 시작 시 자동으로 연결됩니다.","ru":"Оба устройства должны быть в одной сети Wi-Fi. Пока приложение открыто, устройства находят друг друга автоматически, а соединение устанавливается само при отправке или начале трансляции.","zh":"两台设备必须连接同一 Wi-Fi 网络。应用打开时，设备会自动互相发现；发送或开始直播时会自动建立连接。"},
 "discoverable": {"tr":"Uygulama açıkken görünür ol","en":"Be visible while the app is open","ar":"كن مرئيًا أثناء فتح التطبيق","ja":"アプリを開いている間は表示する","ko":"앱이 열려 있는 동안 표시","ru":"Быть видимым, пока приложение открыто","zh":"应用打开时保持可见"},
 "discoverable_hint": {"tr":"Aynı Wi-Fi'deki cihazlar sizi kendiliğinden bulur. Kapalıysa yalnız paylaşım sayfası açıkken görünürsünüz","en":"Devices on the same Wi-Fi find you automatically. When off, you are visible only while the share page is open","ar":"تعثر عليك الأجهزة على شبكة Wi-Fi نفسها تلقائيًا. عند الإيقاف تكون مرئيًا فقط أثناء فتح صفحة المشاركة","ja":"同じ Wi-Fi のデバイスが自動的にあなたを見つけます。オフにすると共有ページを開いている間だけ表示されます","ko":"같은 Wi-Fi의 기기가 자동으로 찾습니다. 끄면 공유 페이지가 열려 있을 때만 표시됩니다","ru":"Устройства в той же сети Wi-Fi находят вас автоматически. Если выключено, вас видно только при открытой странице обмена","zh":"同一 Wi-Fi 中的设备会自动找到你。关闭后，仅在共享页面打开时可见"},
}
dirs = {"tr":"values","en":"values-en","ar":"values-ar","ja":"values-ja","ko":"values-ko","ru":"values-ru","zh":"values-zh"}
def esc(s):
    s = s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
    s = s.replace("\\","\\\\").replace("'","\\'").replace('"','\\"')
    if s.startswith("@") or s.startswith("?"): s = "\\" + s
    return s
import json
for lang in D:
    for k in drop: D[lang].pop(k, None)
    for k, v in new.items(): D[lang][k] = v[lang]
json.dump(D, open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "strings_v51.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
for lang, d in D.items():
    out = ['<?xml version="1.0" encoding="utf-8"?>', '<!-- v51 cihazlar arası tuval paylaşımı ve canlı yayın (aynı Wi-Fi ağı) -->', '<resources>']
    for k, v in d.items():
        out.append(f'    <string name="v51_share_{k}">{esc(v)}</string>')
    out.append('</resources>\n')
    open(os.path.join(RES, dirs[lang], "strings_v51_share.xml"), "w", encoding="utf-8").write("\n".join(out))
print("ok", len(D["tr"]))
