# DrawGuidance'i USB ile bagli telefona yukleyip acar; telefon ayni Wi-Fi'dan bilgisayardaki backend'e baglanir.
. (Join-Path $PSScriptRoot 'common.ps1')
Start-Transcript -Path (Join-Path $Logs 'telefonda-ac.log') -Force | Out-Null
$exitCode = 0
try {
    Write-Step 'Sunucu (Docker: MongoDB + API)'
    $backendUp = Start-Backend

    Write-Step 'Bilgisayarin Wi-Fi adresi'
    $ip = Get-LanIPv4
    if (-not $ip) { throw 'Bilgisayarin ag adresi bulunamadi. Wi-Fi/Ethernet bagli mi?' }
    $serverUrl = "http://${ip}:$BackendPort/"
    Write-Ok $serverUrl
    if ($backendUp -and -not (Test-Http "${serverUrl}api/health")) {
        Write-Warn 'Sunucu bu adresten cevap vermedi; Windows Guvenlik Duvari engelliyor olabilir.'
    }

    Write-Step 'Telefon araniyor (USB)'
    Start-AdbServer
    $phone = $null
    $hinted = $false
    for ($i = 0; $i -lt 60 -and -not $phone; $i++) {
        $devices = @(Get-AdbDevices | Where-Object { $_.Serial -notlike 'emulator-*' })
        $phone = $devices | Where-Object { $_.State -eq 'device' } | Select-Object -First 1
        if ($phone) { break }
        if (-not $hinted) {
            if ($devices | Where-Object { $_.State -eq 'unauthorized' }) {
                Write-Warn 'Telefon bulundu ama izin bekliyor: telefonda "USB hata ayiklamaya izin ver" sorusunu onaylayin.'
            } else {
                Write-Warn 'Bagli telefon yok. Kontrol listesi:'
                Write-Info '  1) Telefonu USB kablosuyla baglayin (sadece sarj degil, veri aktaran kablo)'
                Write-Info '  2) Ayarlar > Telefon hakkinda > "Yapim numarasi"na 7 kez dokunun (Gelistirici secenekleri acilir)'
                Write-Info '  3) Ayarlar > Gelistirici secenekleri > "USB hata ayiklama"yi acin'
                Write-Info '  4) Telefonda cikan "USB hata ayiklamaya izin ver" sorusunu onaylayin'
                Write-Info '  Bekleniyor (2 dakika)...'
            }
            $hinted = $true
        }
        Start-Sleep -Seconds 2
    }
    if (-not $phone) { throw 'Telefon bulunamadi. Telefon gorunmuyorsa telefon markasinin USB surucusu gerekebilir.' }
    $model = (Invoke-Native $Adb "-s $($phone.Serial) shell getprop ro.product.model" 20).Output
    Write-Ok "Telefon: $model ($($phone.Serial))"

    Install-AndLaunch -Serial $phone.Serial -ServerUrl $serverUrl

    Write-Host ''
    Write-Host 'Hazir! Telefon ve bilgisayar ayni Wi-Fi aginda olmali.' -ForegroundColor Green
    Write-Host 'Sunucu baglantisi: uygulamada Ayarlar > "Baglantiyi Test Et" ya da "Agda ara".'
    Write-Host 'Windows ilk seferde Docker icin guvenlik duvari izni sorarsa "Ozel aglar" icin izin verin.'
    'ok' | Set-Content -Path (Join-Path $Logs 'telefonda-ac.status.txt')
}
catch {
    $exitCode = 1
    Write-Host ''
    Write-Host "HATA: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host 'Claude''a "telefonda acilmadi" yazmaniz yeterli; loglari okuyup cozecek.'
    "error: $($_.Exception.Message)" | Set-Content -Path (Join-Path $Logs 'telefonda-ac.status.txt')
}
finally {
    Stop-Transcript | Out-Null
}
exit $exitCode
