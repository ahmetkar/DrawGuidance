# DrawGuidance'i Android emulatorunde acar; emulator bilgisayardaki backend'e 10.0.2.2 uzerinden baglanir.
. (Join-Path $PSScriptRoot 'common.ps1')
Start-Transcript -Path (Join-Path $Logs 'emulatorde-ac.log') -Force | Out-Null
$exitCode = 0
$AvdName = 'DrawGuidance_API34'
try {
    Write-Step 'Sunucu (Docker: MongoDB + API)'
    $null = Start-Backend

    Write-Step 'Emulator'
    $env:ANDROID_SDK_ROOT = $Sdk
    $env:ANDROID_HOME     = $Sdk
    $env:ANDROID_AVD_HOME = Join-Path $Root 'avd'
    Start-AdbServer
    $running = Get-Process -ErrorAction SilentlyContinue | Where-Object { $_.ProcessName -like 'qemu-system*' }
    if ($running) {
        Write-Ok 'Emulator zaten acik'
    } else {
        Write-Info 'Emulator baslatiliyor (ilk acilis birkac dakika surebilir)...'
        $emuLog = Join-Path $Logs 'emulator.txt'
        $cmdArgs = '/d /s /c ""' + $Emulator + '" -avd ' + $AvdName + ' -no-metrics -netdelay none -netspeed full > "' + $emuLog + '" 2>&1"'
        Start-Process -FilePath 'cmd.exe' -ArgumentList $cmdArgs -WindowStyle Minimized | Out-Null
    }

    Write-Step 'Android acilisi bekleniyor'
    $serial = $null
    for ($i = 0; $i -lt 180; $i++) {
        $emu = Get-AdbDevices | Where-Object { $_.Serial -like 'emulator-*' -and $_.State -eq 'device' } | Select-Object -First 1
        if ($emu) {
            $boot = Invoke-Native $Adb "-s $($emu.Serial) shell getprop sys.boot_completed" 20
            if ($boot.Output.Trim() -eq '1') { $serial = $emu.Serial; break }
        }
        if ($i -ge 12 -and -not (Get-Process -ErrorAction SilentlyContinue | Where-Object { $_.ProcessName -like 'qemu-system*' })) {
            throw 'Emulator kapandi (logs\emulator.txt).'
        }
        Start-Sleep -Seconds 5
    }
    if (-not $serial) { throw 'Emulator 15 dakikada acilamadi (logs\emulator.txt).' }
    Write-Ok "Android acildi ($serial)"

    Install-AndLaunch -Serial $serial -ServerUrl "http://10.0.2.2:$BackendPort/"

    Write-Host ''
    Write-Host 'Hazir! DrawGuidance emulatorde acik.' -ForegroundColor Green
    'ok' | Set-Content -Path (Join-Path $Logs 'emulatorde-ac.status.txt')
}
catch {
    $exitCode = 1
    Write-Host ''
    Write-Host "HATA: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host 'Claude''a "emulatorde acilmadi" yazmaniz yeterli; loglari okuyup cozecek.'
    "error: $($_.Exception.Message)" | Set-Content -Path (Join-Path $Logs 'emulatorde-ac.status.txt')
}
finally {
    Stop-Transcript | Out-Null
}
exit $exitCode
