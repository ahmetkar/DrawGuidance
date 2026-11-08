# DrawGuidance sunucusu (v30): Docker'da MongoDB + Spring Boot API'yi baslatir, durdurur ya da durumunu gosterir.
param([ValidateSet('baslat', 'durdur', 'durum')][string]$Komut = 'baslat')
. (Join-Path $PSScriptRoot 'common.ps1')
Start-Transcript -Path (Join-Path $Logs 'sunucu.log') -Force | Out-Null
$exitCode = 0
try {
    switch ($Komut) {
        'baslat' {
            Write-Step 'Sunucu (Docker: MongoDB + API)'
            if (-not (Start-Backend)) { throw 'Sunucu baslatilamadi.' }
            $ip = Get-LanIPv4
            if ($ip) { Write-Ok "Telefondan: http://${ip}:$BackendPort/  (uygulamada Ayarlar > Agda ara)" }
            Write-Ok "MongoDB (bu bilgisayardan): mongodb://127.0.0.1:27017/?directConnection=true (kullanici ve parola: backend\docker\.env)"
        }
        'durdur' { Write-Step 'Sunucu durduruluyor'; Stop-Backend }
        'durum' {
            $h = "http://127.0.0.1:$BackendPort/api/health"
            try { Write-Ok ((Invoke-WebRequest -Uri $h -UseBasicParsing -TimeoutSec 3).Content) } catch { Write-Warn 'Sunucu calismiyor.' }
        }
    }
}
catch {
    $exitCode = 1
    Write-Host "HATA: $($_.Exception.Message)" -ForegroundColor Red
}
finally { Stop-Transcript | Out-Null }
exit $exitCode
