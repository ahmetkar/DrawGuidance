# DrawGuidance yerel ag sunucusu (v42, v43 duzeltme): Docker Desktop'ta MongoDB + hazir derlenmis API.
# Komutlar: baslat | durdur | durum | gunluk | sifirla | yonetim <komut...>
#   baslat  : Docker'i bekler, ilk seferde .env'yi rastgele parolalarla uretir, kapsayicilari baslatir, telefonda
#             yazilacak adresi gosterir
#   durdur  : kapsayicilari durdurur (veriler korunur)
#   durum   : sunucunun saglik yaniti ve adresler
#   gunluk  : API'nin son kayitlari (logs\api.log)
#   sifirla : kapsayicilari ve VERITABANINI siler (onay ister)
#   yonetim : v43 paket/bildirim/duyuru/surum yonetim komutlari (calisan sunucunun kapsayicisinda; Yonetim.bat)
# Windows ayarlarina dokunmaz: guvenlik duvari izni gerekiyorsa nasil verilecegi yazilir.
# Not: [Parameter] kullanilmaz (gelismis betik "--baslik" gibi argumanlari parametre sanar); kalanlar $args'ta
param([ValidateSet('baslat', 'durdur', 'durum', 'gunluk', 'sifirla', 'yonetim')][string]$Komut = 'baslat')
$Rest = @($args)
$ErrorActionPreference = 'Stop'
$Root = $PSScriptRoot
$Logs = Join-Path $Root 'logs'
New-Item -ItemType Directory -Force -Path $Logs | Out-Null
$Compose = Join-Path $Root 'docker-compose.yml'
$EnvFile = Join-Path $Root '.env'
$Jar = Join-Path $Root 'drawguidance-server.jar'
$Port = 4747
# v43 her calismanin ekran ciktisi logs\son-calisma.log dosyasina da yazilir (hata olursa buradan okunur)
try { Start-Transcript -Path (Join-Path $Logs 'son-calisma.log') -Force | Out-Null } catch { }

function Write-Step([string]$t) { Write-Host ''; Write-Host "==> $t" -ForegroundColor Cyan }
function Write-Ok([string]$t) { Write-Host "    $t" -ForegroundColor Green }
function Write-Info([string]$t) { Write-Host "    $t" }
function Write-Warn([string]$t) { Write-Host "    $t" -ForegroundColor Yellow }

function Find-Docker {
    $cmd = Get-Command docker -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    foreach ($p in @("$env:ProgramFiles\Docker\Docker\resources\bin\docker.exe", "$env:LOCALAPPDATA\Docker\resources\bin\docker.exe")) {
        if (Test-Path $p) { return $p }
    }
    return $null
}

function Invoke-Docker([string]$Docker, [string[]]$Arguments) {
    # v43 duzeltme: Windows PowerShell 5.1'de $ErrorActionPreference='Stop' iken docker'in stderr'e yazdigi her
    # satir (ornek: "Network ... Creating", "Pulling") 2>&1 ile NativeCommandError'a donup betigi durduruyordu.
    # Komut suresince 'Continue' kullanilir; ciktilar duz metne cevrilir, basari cikis koduyla anlasilir.
    $old = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $out = & $Docker @Arguments 2>&1 | ForEach-Object { if ($_ -is [System.Management.Automation.ErrorRecord]) { $_.Exception.Message } else { "$_" } } | Out-String
        $code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $old
    }
    return [pscustomobject]@{ Code = $code; Output = $out }
}

function Wait-Docker([string]$Docker) {
    for ($i = 0; $i -lt 90; $i++) {
        $r = Invoke-Docker $Docker @('info', '--format', '{{.ServerVersion}}')
        if ($r.Code -eq 0) { return $true }
        if ($i -eq 0) {
            Write-Info 'Docker Desktop baslatiliyor (acik degilse)...'
            $desk = "$env:ProgramFiles\Docker\Docker\Docker Desktop.exe"
            if (Test-Path $desk) { Start-Process $desk | Out-Null }
        }
        Start-Sleep -Seconds 2
    }
    return $false
}

function Test-Health {
    try { return (Invoke-WebRequest -Uri "http://127.0.0.1:$Port/api/health" -UseBasicParsing -TimeoutSec 3).Content } catch { return $null }
}

function Get-LanIPv4 {
    $ips = @()
    try {
        $ips = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction Stop |
            Where-Object { $_.IPAddress -match '^(192\.168\.|10\.|172\.(1[6-9]|2[0-9]|3[01])\.)' -and $_.InterfaceAlias -notmatch 'vEthernet|WSL|Docker|VirtualBox|VMware|Loopback' } |
            Sort-Object -Property @{ Expression = { if ($_.InterfaceAlias -match 'Wi-?Fi|Kablosuz|Wireless') { 0 } else { 1 } } } |
            Select-Object -ExpandProperty IPAddress
    } catch { }
    return $ips
}

function New-Secret([int]$Bytes = 24) {
    $b = New-Object byte[] $Bytes
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b)
    return (-join ($b | ForEach-Object { $_.ToString('x2') }))
}

function New-Base64Key {
    $b = New-Object byte[] 32
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b)
    return [Convert]::ToBase64String($b)
}

function Fill-Secret([string]$l) {
    if ($l -match '^MONGO_ROOT_PASSWORD=$') { return "MONGO_ROOT_PASSWORD=$(New-Secret)" }
    if ($l -match '^MONGO_APP_PASSWORD=$') { return "MONGO_APP_PASSWORD=$(New-Secret)" }
    if ($l -match '^IP_HASH_SALT=$') { return "IP_HASH_SALT=$(New-Secret 32)" }
    if ($l -match '^DEVICE_DATA_KEY=$') { return "DEVICE_DATA_KEY=$(New-Base64Key)" }
    return $l
}

function Initialize-Env {
    $utf8 = New-Object System.Text.UTF8Encoding($false)
    $template = Get-Content (Join-Path $Root '.env.ornek') -Encoding UTF8
    if (-not (Test-Path $EnvFile)) {
        $out = foreach ($l in $template) { Fill-Secret $l }
        # BOM'suz UTF-8 (docker compose BOM'lu dosyayi okuyamaz)
        [System.IO.File]::WriteAllLines($EnvFile, [string[]]$out, $utf8)
        Write-Ok '.env olusturuldu (rastgele parolalar; bu dosyayi kimseyle paylasma)'
        return
    }
    # v43 eski .env'de olmayan yeni ayarlar eklenir (mevcut parolalara dokunulmaz)
    $have = Get-Content $EnvFile -Encoding UTF8
    $names = @{}
    foreach ($l in $have) { if ($l -match '^([A-Z0-9_]+)=') { $names[$Matches[1]] = $true } }
    $add = @()
    foreach ($l in $template) {
        if ($l -match '^([A-Z0-9_]+)=' -and -not $names.ContainsKey($Matches[1])) { $add += (Fill-Secret $l) }
    }
    if ($add.Count -gt 0) {
        [System.IO.File]::WriteAllLines($EnvFile, [string[]]($have + $add), $utf8)
        Write-Ok (".env'ye yeni ayarlar eklendi: " + (($add | ForEach-Object { ($_ -split '=')[0] }) -join ', '))
    }
}

function Get-EnvValue([string]$Name) {
    foreach ($l in (Get-Content $EnvFile -Encoding UTF8)) { if ($l -match "^$Name=(.*)$") { return $Matches[1].Trim() } }
    return $null
}

function Initialize-Secrets {
    # v43 Cloud Storage hizmet hesabi: .env'deki GCS_KEY_SOURCE (goreli yol bu klasore gore) sirlar\gcs.json'a kopyalanir
    $dir = Join-Path $Root 'sirlar'
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    New-Item -ItemType Directory -Force -Path (Join-Path $Root 'yonetim') | Out-Null
    $src = Get-EnvValue 'GCS_KEY_SOURCE'
    if (-not $src) { return }
    $path = if ([System.IO.Path]::IsPathRooted($src)) { $src } else { Join-Path $Root $src }
    if (Test-Path $path) {
        Copy-Item -Path $path -Destination (Join-Path $dir 'gcs.json') -Force
        Write-Ok 'Cloud Storage hizmet hesabi hazir (sirlar\gcs.json)'
    } else {
        Write-Warn "Cloud Storage hizmet hesabi bulunamadi: $path (paket resimleri verilemez; sunucu yine calisir)"
    }
}

function Show-Addresses {
    $ips = Get-LanIPv4
    Write-Host ''
    if ($ips) {
        Write-Host '    Telefondaki uygulamada: Ayarlar > Baglanti > Sunucu (gecici) > Yerel ag' -ForegroundColor Cyan
        foreach ($ip in $ips) { Write-Host "      adres: ${ip}:$Port   (ya da 'Agda ara')" -ForegroundColor Green }
    } else {
        Write-Warn 'Yerel ag adresi bulunamadi (bilgisayar Wi-Fi ya da kabloyla aga bagli olmali).'
    }
    Write-Info 'Telefon baglanamiyorsa: Windows Guvenlik Duvari 4747 kapisina izin vermeli. Ilk acilista Windows'
    Write-Info '"Erisime izin ver" sorusu cikarsa "Ozel aglar"i isaretleyip izin ver. Elle eklemek icin (yonetici):'
    Write-Info "  netsh advfirewall firewall add rule name=`"DrawGuidance 4747`" dir=in action=allow protocol=TCP localport=$Port profile=private"
}

try {
    $docker = Find-Docker
    switch ($Komut) {
        'baslat' {
            Write-Step 'DrawGuidance yerel sunucu'
            if (-not (Test-Path $Jar)) { throw "drawguidance-server.jar bulunamadi ($Jar). Teslim paketindeki jar bu klasorde olmali." }
            if (-not $docker) { throw 'Docker Desktop bulunamadi. Kurun: https://www.docker.com/products/docker-desktop/ (kurulumdan sonra bilgisayari yeniden baslatin).' }
            if (-not (Wait-Docker $docker)) { throw 'Docker Desktop hazir olmadi. Docker Desktop''i acip tekrar deneyin.' }
            Initialize-Env
            Initialize-Secrets
            Write-Info 'MongoDB ve API baslatiliyor (ilk seferde imajlar indirilir, birkac dakika surebilir)...'
            $r = Invoke-Docker $docker @('compose', '-f', $Compose, '--env-file', $EnvFile, 'up', '-d')
            $r.Output | Set-Content -Path (Join-Path $Logs 'baslat.log') -Encoding UTF8
            if ($r.Code -ne 0) { Write-Warn $r.Output; throw 'docker compose basarisiz (logs\baslat.log)' }
            $ok = $null
            for ($i = 0; $i -lt 120; $i++) { Start-Sleep -Seconds 2; $ok = Test-Health; if ($ok) { break } }
            if (-not $ok) {
                (Invoke-Docker $docker @('compose', '-f', $Compose, '--env-file', $EnvFile, 'logs', '--tail', '200')).Output | Set-Content -Path (Join-Path $Logs 'api.log') -Encoding UTF8
                throw 'Sunucu cevap vermedi. Kayitlar: logs\api.log'
            }
            Write-Ok "Sunucu calisiyor: $ok"
            Show-Addresses
        }
        'durdur' {
            if (-not $docker) { throw 'Docker Desktop bulunamadi.' }
            Write-Step 'Yerel sunucu durduruluyor'
            $r = Invoke-Docker $docker @('compose', '-f', $Compose, '--env-file', $EnvFile, 'down')
            $r.Output | Set-Content -Path (Join-Path $Logs 'durdur.log') -Encoding UTF8
            Write-Ok 'Durduruldu (veritabani korunur)'
        }
        'durum' {
            Write-Step 'Yerel sunucu durumu'
            $h = Test-Health
            if ($h) { Write-Ok "Calisiyor: $h"; Show-Addresses } else { Write-Warn 'Sunucu calismiyor (Yerel-Sunucuyu-Baslat.bat).' }
        }
        'gunluk' {
            if (-not $docker) { throw 'Docker Desktop bulunamadi.' }
            $f = Join-Path $Logs 'api.log'
            (Invoke-Docker $docker @('compose', '-f', $Compose, '--env-file', $EnvFile, 'logs', '--tail', '300', 'api')).Output | Set-Content -Path $f -Encoding UTF8
            Write-Ok "Kayitlar: $f"
            Get-Content $f -Tail 40
        }
        'yonetim' {
            if (-not $docker) { throw 'Docker Desktop bulunamadi.' }
            if (-not (Test-Health)) { throw 'Sunucu calismiyor. Once Yerel-Sunucuyu-Baslat.bat.' }
            # Dosya isteyen komutlar (--dosya, --klasor) icin dosyalar yonetim\ klasorune konur; kapsayicida /data/yonetim
            $argsOut = foreach ($a in $Rest) { $a -replace '^yonetim[\\/]', '/data/yonetim/' }
            $cmd = @('compose', '-f', $Compose, '--env-file', $EnvFile, 'exec', '-T', 'api', 'java', '-jar', '/app/drawguidance-server.jar', 'cli') + $argsOut
            # Komut UTF-8 yazar (Turkce harfler)
            try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }
            $old = $ErrorActionPreference
            $ErrorActionPreference = 'Continue'
            try { & $docker @cmd 2>&1 | ForEach-Object { "$_" } } finally { $ErrorActionPreference = $old }
            $code = $LASTEXITCODE
            try { Stop-Transcript | Out-Null } catch { }
            exit $code
        }
        'sifirla' {
            if (-not $docker) { throw 'Docker Desktop bulunamadi.' }
            $a = Read-Host 'Yerel sunucunun VERITABANI silinecek. Emin misin? (evet yaz)'
            if ($a -ne 'evet') { Write-Info 'Vazgecildi.'; break }
            $r = Invoke-Docker $docker @('compose', '-f', $Compose, '--env-file', $EnvFile, 'down', '-v')
            $r.Output | Set-Content -Path (Join-Path $Logs 'sifirla.log') -Encoding UTF8
            Write-Ok 'Kapsayicilar ve veritabani silindi (.env korunur).'
        }
    }
    try { Stop-Transcript | Out-Null } catch { }
    exit 0
}
catch {
    Write-Host "HATA: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "    Ayrinti: logs\son-calisma.log" -ForegroundColor Yellow
    try { Stop-Transcript | Out-Null } catch { }
    exit 1
}
