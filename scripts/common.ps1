# DrawGuidance - Windows yardimci fonksiyonlari (Telefonda-Ac / Emulatorde-Ac tarafindan kullanilir).
# Sistem ayarlarina dokunmaz: her sey DrawGuidance klasoru icinde calisir.

$ErrorActionPreference = 'Stop'
$ProgressPreference    = 'SilentlyContinue'

$Root        = Split-Path -Parent $PSScriptRoot
$Logs        = Join-Path $Root 'logs'
$Sdk         = Join-Path $Root 'android-sdk'
$Adb         = Join-Path $Sdk 'platform-tools\adb.exe'
$Emulator    = Join-Path $Sdk 'emulator\emulator.exe'
$Apk         = Join-Path $Root 'DrawGuidance-debug.apk'
# v30: sunucu Docker'da (MongoDB + Spring Boot API): backend\docker\docker-compose.yml
$BackendDir  = Join-Path $Root 'backend'
$ComposeFile = Join-Path $BackendDir 'docker\docker-compose.yml'
$BackendPort = 4747
$AppPackage  = 'com.karapp.drawguidance'
$AppActivity = 'com.karapp.drawguidance/.MainActivity'

New-Item -ItemType Directory -Force -Path $Logs | Out-Null

function Write-Step([string]$Text) { Write-Host ''; Write-Host "==> $Text" -ForegroundColor Cyan }
function Write-Ok([string]$Text)   { Write-Host "    $Text" -ForegroundColor Green }
function Write-Info([string]$Text) { Write-Host "    $Text" }
function Write-Warn([string]$Text) { Write-Host "    $Text" -ForegroundColor Yellow }

# Surekli calisan alt surec baslatmayan komutlari calistirir; cikti ve cikis kodunu dondurur.
function Invoke-Native([string]$File, [string]$Arguments, [int]$TimeoutSec = 600) {
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $File
    $psi.Arguments = $Arguments
    $psi.UseShellExecute = $false
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.CreateNoWindow = $true
    $proc = [System.Diagnostics.Process]::Start($psi)
    $out = $proc.StandardOutput.ReadToEndAsync()
    $err = $proc.StandardError.ReadToEndAsync()
    if (-not $proc.WaitForExit($TimeoutSec * 1000)) {
        try { $proc.Kill() } catch { }
        return [pscustomobject]@{ ExitCode = -1; Output = "zaman asimi ($TimeoutSec sn)" }
    }
    $proc.WaitForExit()
    return [pscustomobject]@{ ExitCode = $proc.ExitCode; Output = ($out.Result + $err.Result).Trim() }
}

# Arka planda kalan (daemon) surecleri baslatan komutlar icin: ciktiyi borulamaz, sadece surecin kendisini bekler.
function Invoke-Detached([string]$File, [string]$Arguments) {
    $proc = Start-Process -FilePath $File -ArgumentList $Arguments -WindowStyle Hidden -PassThru
    $proc.WaitForExit()
    return $proc.ExitCode
}

function Test-Http([string]$Url, [int]$TimeoutSec = 3) {
    try {
        $r = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec $TimeoutSec
        return $r.StatusCode -eq 200
    } catch {
        return $false
    }
}

function Find-Docker {
    $cmd = Get-Command docker.exe -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $candidate = Join-Path $env:ProgramFiles 'Docker\Docker\resources\bin\docker.exe'
    if (Test-Path $candidate) { return $candidate }
    return $null
}

# Bilgisayarin Wi-Fi/Ethernet IPv4 adresi (varsayilan agi olan, acik baglanti).
function Get-LanIPv4 {
    try {
        $configs = Get-NetIPConfiguration -ErrorAction Stop |
            Where-Object { $_.IPv4DefaultGateway -and $_.NetAdapter.Status -eq 'Up' -and $_.IPv4Address }
        foreach ($c in $configs) {
            $ip = @($c.IPv4Address)[0].IPAddress
            if ($ip -and $ip -notlike '169.254.*') { return $ip }
        }
    } catch { }
    $fallback = [System.Net.Dns]::GetHostAddresses($env:COMPUTERNAME) |
        Where-Object { $_.AddressFamily -eq 'InterNetwork' -and $_.IPAddressToString -notlike '169.254.*' -and $_.IPAddressToString -ne '127.0.0.1' } |
        Select-Object -First 1
    if ($fallback) { return $fallback.IPAddressToString }
    return $null
}

# Docker Desktop calismiyorsa baslatir ve hazir olmasini bekler (en cok 2 dakika).
function Wait-Docker([string]$Docker) {
    $r = Invoke-Native $Docker 'info --format "{{.ServerVersion}}"' 30
    if ($r.ExitCode -eq 0) { return $true }
    $desktop = Join-Path $env:ProgramFiles 'Docker\Docker\Docker Desktop.exe'
    if (Test-Path $desktop) {
        Write-Info 'Docker Desktop baslatiliyor...'
        Start-Process -FilePath $desktop -WindowStyle Minimized | Out-Null
    }
    for ($i = 0; $i -lt 60; $i++) {
        Start-Sleep -Seconds 2
        $r = Invoke-Native $Docker 'info --format "{{.ServerVersion}}"' 30
        if ($r.ExitCode -eq 0) { return $true }
    }
    return $false
}

# Ilk kurulumda docker\.env olusturulur (parolalar rastgele; dosya bu bilgisayarda kalir).
function Initialize-DockerEnv {
    $dockerDir = Join-Path $BackendDir 'docker'
    $envFile = Join-Path $dockerDir '.env'
    if (Test-Path $envFile) { return }
    $example = Join-Path $dockerDir '.env.example'
    $text = Get-Content $example -Raw
    $text = $text -replace 'degistir-yonetici-parolasi', ([guid]::NewGuid().ToString('N'))
    $text = $text -replace 'degistir-uygulama-parolasi', ([guid]::NewGuid().ToString('N'))
    Set-Content -Path $envFile -Value $text -Encoding ASCII
    Write-Info 'Sunucu ayarlari olusturuldu (backend\docker\.env)'
}

# v30 sunucu: Docker'da MongoDB kopya kumesi + Spring Boot API. Docker yoksa uygulama sunucusuz calisir.
function Start-Backend {
    $health = "http://127.0.0.1:$BackendPort/api/health"
    if (Test-Http $health) { Write-Ok "Sunucu zaten calisiyor (port $BackendPort)"; return $true }
    $docker = Find-Docker
    if (-not $docker) {
        Write-Warn 'Docker Desktop bulunamadi; uygulama sunucusuz (yalniz telefonda) calisir.'
        Write-Info 'Sunucu icin Docker Desktop kurulmali: https://www.docker.com/products/docker-desktop/'
        return $false
    }
    if (-not (Wait-Docker $docker)) { Write-Warn 'Docker Desktop hazir olmadi; sunucu baslatilamadi.'; return $false }
    Initialize-DockerEnv
    Write-Info 'MongoDB ve API kapsayicilari baslatiliyor (ilk sefer derleme birkac dakika surebilir)...'
    $log = Join-Path $Logs 'docker-compose.log'
    $r = Invoke-Native $docker "compose -f `"$ComposeFile`" up -d --build" 1800
    $r.Output | Set-Content -Path $log -Encoding UTF8
    if ($r.ExitCode -ne 0) { Write-Warn "docker compose basarisiz (logs\docker-compose.log)"; return $false }
    for ($i = 0; $i -lt 90; $i++) {
        Start-Sleep -Seconds 2
        if (Test-Http $health) { Write-Ok "Sunucu calisiyor (port $BackendPort)"; return $true }
    }
    Write-Warn 'Sunucu cevap vermedi (docker compose logs api ile bakilabilir).'
    return $false
}

function Stop-Backend {
    $docker = Find-Docker
    if (-not $docker) { return }
    $r = Invoke-Native $docker "compose -f `"$ComposeFile`" down" 300
    $r.Output | Set-Content -Path (Join-Path $Logs 'docker-compose-down.log') -Encoding UTF8
    Write-Ok 'Sunucu durduruldu (veriler Docker biriminde korunur)'
}

function Start-AdbServer {
    $null = Invoke-Detached $Adb 'start-server'
}

# 'adb devices' ciktisini [serial, state] listesine cevirir.
function Get-AdbDevices {
    $r = Invoke-Native $Adb 'devices' 30
    $list = @()
    foreach ($line in ($r.Output -split "`r?`n")) {
        if ($line -match '^(\S+)\s+(device|unauthorized|offline|no permissions)') {
            $list += [pscustomobject]@{ Serial = $Matches[1]; State = $Matches[2] }
        }
    }
    return $list
}

function Install-AndLaunch([string]$Serial, [string]$ServerUrl) {
    if (-not (Test-Path $Apk)) { throw "APK bulunamadi: $Apk" }
    Write-Step 'DrawGuidance yukleniyor'
    $r = Invoke-Native $Adb "-s $Serial install -r `"$Apk`"" 300
    $r.Output | Set-Content -Path (Join-Path $Logs 'adb-install.txt') -Encoding UTF8
    if ($r.Output -notmatch 'Success') {
        Write-Warn $r.Output
        throw 'APK yuklenemedi. Telefonda "USB ile yukle" izni istenmis olabilir; izin verip tekrar deneyin.'
    }
    Write-Ok 'Yuklendi'

    Write-Step "Uygulama aciliyor (sunucu: $ServerUrl)"
    $null = Invoke-Native $Adb "-s $Serial logcat -c" 30
    $r = Invoke-Native $Adb "-s $Serial shell am start -S -n $AppActivity --es server_url $ServerUrl" 60
    $r.Output | Set-Content -Path (Join-Path $Logs 'adb-launch.txt') -Encoding UTF8
    if ($r.Output -match 'Error') { Write-Warn $r.Output; throw 'Uygulama acilamadi.' }
    Start-Sleep -Seconds 6
    $log = Invoke-Native $Adb "-s $Serial logcat -d -v time DrawGuidance:I AndroidRuntime:E *:S" 30
    $log.Output | Set-Content -Path (Join-Path $Logs 'logcat.txt') -Encoding UTF8
    if ($log.Output -match 'FATAL EXCEPTION') { Write-Warn 'Uygulama coktu, ayrinti: logs\logcat.txt'; throw 'Uygulama coktu.' }
    Write-Ok 'DrawGuidance acildi'
}
