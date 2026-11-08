# DrawGuidance - Asset disa aktarma + Google Cloud Storage esitleme (ASCII; PowerShell 5.1 uyumlu)
# ------------------------------------------------------------------------------------------------
# Ne yapar:
#  1) Su anki Android surumunun pencere resimlerini sunucunun taban-paket yapisina gore bir klasore
#     cikarir (test: V43BasePackExport, -PbasePackOut).
#  2) Sunucu CLI (taban-paket --sadece-yukle) ile klasoru Cloud Storage'a (gs://dgpackets/base/...)
#     yukler. ARTIMLI: her dosyanin MD5'i kovadakiyle kiyaslanir; yalniz DEGISEN/YENI dosyalar gider.
#     Mongo'ya YAZMAZ; bundled-manifest.json uretir, paketleri sunucu acilista kaydeder.
#
# Kullanim:
#   Asset-GCS-Esitle.bat             -> once KURU (onizleme)
#   Asset-GCS-Esitle.bat -Upload     -> gercek yukleme
#
# JDK: Gradle 8.14.3 JDK 17/21 ister (JDK 25 ile patlar). Script uygun JDK'yi bulur; ya da:
#   set DG_JDK=C:\Program Files\Java\jdk-21.0.13.0.3
#   Asset-GCS-Esitle.bat

[CmdletBinding()]
param(
  [switch]$Upload,
  [switch]$SkipExport,   # 1. adimi (disa aktarma) atla; var olan tools\base_packages ciktisini kullan
  [string]$OutDir
)

$ErrorActionPreference = "Stop"

# ----------------------------------------------------------------------- Proje koku (android+backend)
function Find-Root([string]$start) {
  try { $d = (Resolve-Path $start).Path } catch { return $null }
  for ($i = 0; $i -lt 8; $i++) {
    if ((Test-Path (Join-Path $d 'android')) -and (Test-Path (Join-Path $d 'backend'))) { return $d }
    $parent = Split-Path $d -Parent
    if ([string]::IsNullOrEmpty($parent) -or $parent -eq $d) { break }
    $d = $parent
  }
  return $null
}
$Root = Find-Root $PSScriptRoot
if (-not $Root) { $Root = Find-Root (Get-Location).Path }
if (-not $Root) { throw "Proje koku bulunamadi: icinde 'android' ve 'backend' olan klasor yok. Scripti DrawGuidance klasorune koy." }
$Android = Join-Path $Root "android"
$Backend = Join-Path $Root "backend"
Write-Host "Proje koku: $Root" -ForegroundColor DarkGray

# ----------------------------------------------------------------------- Ayarlar
$env:GCS_BUCKET               = "dgpackets"
$env:GCS_SERVICE_ACCOUNT_JSON = Join-Path $Root "yayin-anahtari\gcs-service-account.json"
if (-not $OutDir) { $OutDir = Join-Path $Root "tools\base_packages" }

# ----------------------------------------------------------------------- JDK 17/21 bul (once 21)
# Surumu java.exe calistirmadan JDK'nin 'release' dosyasindan okuruz (stderr sorunu olmasin).
function Jdk-Major([string]$jdkHome) {
  if (-not (Test-Path (Join-Path $jdkHome 'bin\java.exe'))) { return $null }
  $rel = Join-Path $jdkHome 'release'
  if (Test-Path $rel) {
    foreach ($l in (Get-Content $rel -ErrorAction SilentlyContinue)) {
      if ($l -match 'JAVA_VERSION="?(\d+)') { return $matches[1] }
    }
  }
  # yedek: klasor adindan (jdk-21..., jbr-21...)
  if ((Split-Path $jdkHome -Leaf) -match '(?<![0-9])(\d{2})(?![0-9])') { return $matches[1] }
  return $null
}
function Find-Jdk {
  $cands = New-Object System.Collections.Generic.List[string]
  if ($env:DG_JDK)    { $cands.Add($env:DG_JDK) }
  if ($env:JAVA_HOME) { $cands.Add($env:JAVA_HOME) }
  $cands.Add("$env:ProgramFiles\Android\Android Studio\jbr")
  $cands.Add("$env:LOCALAPPDATA\Programs\Android Studio\jbr")
  foreach ($base in @("$env:ProgramFiles\Java", "$env:ProgramFiles\Eclipse Adoptium",
                      "$env:ProgramFiles\Microsoft", "$env:ProgramFiles\Zulu", "$env:ProgramFiles\Amazon Corretto")) {
    if (Test-Path $base) {
      Get-ChildItem $base -Directory -ErrorAction SilentlyContinue | ForEach-Object { $cands.Add($_.FullName) }
    }
  }
  if (Test-Path "$env:USERPROFILE\scoop\apps") {
    Get-ChildItem "$env:USERPROFILE\scoop\apps" -Directory -ErrorAction SilentlyContinue |
      Where-Object { $_.Name -match 'jdk|temurin|zulu|corretto|openjdk' } | ForEach-Object {
        $c = Join-Path $_.FullName 'current'; if (Test-Path $c) { $cands.Add($c) } else { $cands.Add($_.FullName) } }
  }
  foreach ($want in @('21', '17')) {
    foreach ($c in $cands) {
      if ((Jdk-Major $c) -eq $want) { return [pscustomobject]@{ Home = $c; Major = $want } }
    }
  }
  return $null
}
$jdk = Find-Jdk
if (-not $jdk) {
  throw "JDK 17/21 bulunamadi. Gradle 8.14.3 JDK 25 ile calismaz. Bir JDK 17/21 kur ya da yolunu ver:  set DG_JDK=C:\Program Files\Java\jdk-21.0.13.0.3"
}
$env:JAVA_HOME = $jdk.Home
$env:PATH = (Join-Path $jdk.Home 'bin') + ';' + $env:PATH
Write-Host ("JDK: " + $jdk.Home + "  [JDK " + $jdk.Major + "]") -ForegroundColor DarkGray
if ($jdk.Major -eq '17') { Write-Host "UYARI: Yalniz JDK 17 bulundu; backend (Java 21) jar derlemesi gerekirse basarisiz olabilir. JDK 21 onerilir." -ForegroundColor Yellow }

# ----------------------------------------------------------------------- Android SDK (yalniz disa aktarma icin)
# Kendi yolunu zorlamak icin:  set ANDROID_HOME=C:\Users\ahmet\AppData\Local\Android\Sdk
function Find-AndroidSdk {
  $list = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT,
            "$env:LOCALAPPDATA\Android\Sdk", "$env:USERPROFILE\AppData\Local\Android\Sdk",
            "$env:ProgramFiles\Android\android-sdk", "$env:ProgramFiles(x86)\Android\android-sdk")
  foreach ($p in $list) {
    if ($p -and (Test-Path $p) -and ((Test-Path (Join-Path $p 'platform-tools')) -or (Test-Path (Join-Path $p 'platforms')))) { return $p }
  }
  $lp = Join-Path $Android 'local.properties'
  if (Test-Path $lp) {
    foreach ($l in (Get-Content $lp -ErrorAction SilentlyContinue)) {
      if ($l -match '^\s*sdk\.dir\s*=\s*(.+)$') {
        $d = ($matches[1].Trim() -replace '\\\\', '\')
        if (Test-Path $d) { return $d }
      }
    }
  }
  return $null
}
if (-not $SkipExport) {
  $sdk = Find-AndroidSdk
  if (-not $sdk) {
    throw "Android SDK bulunamadi. Android Studio'nun SDK yolunu ver:  set ANDROID_HOME=C:\Users\ahmet\AppData\Local\Android\Sdk  (ya da disa aktarmayi atla: -SkipExport)"
  }
  $env:ANDROID_HOME = $sdk
  $env:ANDROID_SDK_ROOT = $sdk
  Write-Host ("Android SDK: " + $sdk) -ForegroundColor DarkGray
}

function Step($m) { Write-Host ("`n==== " + $m + " ====") -ForegroundColor Cyan }

# 0) On denetim
if ($Upload -and -not (Test-Path $env:GCS_SERVICE_ACCOUNT_JSON)) {
  throw ("GCS hizmet hesabi anahtari bulunamadi: " + $env:GCS_SERVICE_ACCOUNT_JSON + " . Yolu scriptin basinda duzelt.")
}

# 1) Disa aktar (ya da atla)
if ($SkipExport) {
  Step "1/3  Disa aktarma ATLANDI (-SkipExport)"
  if (-not (Test-Path (Join-Path $OutDir "manifest.json"))) {
    throw ("Atlanamaz: onceki cikti yok -> " + (Join-Path $OutDir 'manifest.json') + " . Once -SkipExport'suz bir kez calistir.")
  }
  Write-Host ("Var olan cikti kullanilacak: " + $OutDir) -ForegroundColor Green
} else {
  Step ("1/3  Android assetlerini disa aktar  (V43BasePackExport -> " + $OutDir + ")")
  Push-Location $Android
  try {
    & .\gradlew.bat --no-daemon "-Dorg.gradle.java.home=$($jdk.Home)" :app:testDebugUnitTest --tests "*V43BasePackExport*" "-PbasePackOut=$OutDir" --rerun-tasks
    if ($LASTEXITCODE -ne 0) { throw "Disa aktarma basarisiz (gradle)." }
  } finally { Pop-Location }
  if (-not (Test-Path (Join-Path $OutDir "manifest.json"))) { throw ("manifest.json uretilmedi: " + $OutDir) }
  Write-Host ("Disa aktarildi: " + $OutDir) -ForegroundColor Green
}

# 2) Sunucu jar'i
Step "2/3  Sunucu jar"
$jar = Get-ChildItem (Join-Path $Backend "build\libs") -Filter "drawguidance-server*.jar" -ErrorAction SilentlyContinue |
       Where-Object { $_.Name -notlike "*plain*" } | Select-Object -First 1
# v49.1: jar her seferinde (artimli) yeniden derlenir; eski jar generatedAt yazmazsa sunucu acilista paketleri guncellemez.
if ($true) {
  Write-Host "Sunucu jar derleniyor (bootJar, guncel kodla)..."
  Push-Location $Backend
  try { & .\gradlew.bat --no-daemon "-Dorg.gradle.java.home=$($jdk.Home)" bootJar; if ($LASTEXITCODE -ne 0) { throw "bootJar basarisiz." } }
  finally { Pop-Location }
  $jar = Get-ChildItem (Join-Path $Backend "build\libs") -Filter "drawguidance-server*.jar" |
         Where-Object { $_.Name -notlike "*plain*" } | Select-Object -First 1
}
Write-Host ("Jar: " + $jar.FullName) -ForegroundColor Green

# 3) Cloud Storage esitleme
Step "3/3  Cloud Storage esitleme"
$seedArgs = @("-jar", $jar.FullName, "cli", "taban-paket", "--klasor", $OutDir)
if ($Upload) {
  $seedArgs += "--sadece-yukle"
  Write-Host ("GERCEK yukleme (Mongo'suz): degisen/yeni dosyalar gs://" + $env:GCS_BUCKET + "/base/... adresine gidecek.") -ForegroundColor Yellow
} else {
  $seedArgs += "--kuru"
  Write-Host "KURU calisma: hicbir sey yuklenmez; yalniz paket/oge sayilari gosterilir." -ForegroundColor Yellow
  Write-Host "Gercek yukleme icin:  Asset-GCS-Esitle.bat -Upload"
}
& java @seedArgs
if ($LASTEXITCODE -ne 0) { throw "Esitleme basarisiz (CLI)." }

if ($Upload) {
  $bundled = Join-Path $OutDir "bundled-manifest.json"
  $dest    = Join-Path $Backend "src\main\resources\base-packages\manifest.json"
  if (Test-Path $bundled) {
    New-Item -ItemType Directory -Force -Path (Split-Path $dest) | Out-Null
    Copy-Item $bundled $dest -Force
    Write-Host ("`nbundled-manifest.json -> " + $dest) -ForegroundColor Green
    Write-Host "Bunu commit edip sunucuyu yeniden dagit: acilista taban paketleri kaydeder." -ForegroundColor Green
  }
  Write-Host "`nBitti. Degisen dosyalar kovaya yuklendi." -ForegroundColor Green
} else {
  Write-Host "`nKuru calisma bitti." -ForegroundColor Green
}
