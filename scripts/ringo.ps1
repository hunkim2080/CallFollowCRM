<#
RING-GO 폰 자동 확인 도구

사장님이 폰에서 동작 하나 하면 → 제(Claude)가 결과 자동 확인하는 보조 스크립트.
사장님이 직접 쓰셔도 됨 (예: 빠른 진단).

## 사전 준비 (1회)
1. 갤S9 USB 디버깅 ON: 설정 → 휴대전화 정보 → 빌드번호 7번 탭 → 개발자 옵션 진입 → "USB 디버깅" 켜기
2. USB 케이블로 PC 연결
3. 폰에 "이 컴퓨터의 디버깅을 허용하시겠습니까?" 뜨면 "항상 허용" 체크 후 확인

## 명령어 모음

# 1) 폰 연결 확인
.\scripts\ringo.ps1 status

# 2) 폰 화면 캡처 → PNG 저장 + Claude 가 볼 수 있는 경로
.\scripts\ringo.ps1 screen

# 3) 앱 logcat 실시간 (Ctrl+C 로 중단)
.\scripts\ringo.ps1 log

# 4) 앱 logcat — ANR / crash 만
.\scripts\ringo.ps1 log -filter crash

# 5) 앱 logcat — SMS 송수신 흐름
.\scripts\ringo.ps1 log -filter sms

# 6) 앱 강제 종료
.\scripts\ringo.ps1 kill

# 7) 앱 다시 시작
.\scripts\ringo.ps1 start

# 8) 가짜 SMS broadcast (테스트용 — Default SMS 앱이 아닌 경우만 유효)
.\scripts\ringo.ps1 fakesms -from "01012345678" -body "테스트 메시지"

# 9) 앱 DB 덤프 — 최근 10건 통화 기록
.\scripts\ringo.ps1 db calls

# 10) 앱 DB 덤프 — 카테고리
.\scripts\ringo.ps1 db cats

# 11) 알림 dump (현재 띄워진 알림 보기)
.\scripts\ringo.ps1 notif
#>

param(
    [Parameter(Position=0)] [string]$Command = "status",
    [Parameter(Position=1)] [string]$Sub = "",
    [string]$Filter = "",
    [string]$From = "",
    [string]$Body = ""
)

# ----- 설정 -----

$AdbPath = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$AppId = "com.detailline.callfollowcrm"
$ScreenDir = "$PSScriptRoot\screens"

if (-not (Test-Path $AdbPath)) {
    Write-Host "❌ adb 를 못 찾았어요: $AdbPath" -ForegroundColor Red
    Write-Host "   Android Studio 가 깔려있는지 확인하세요."
    exit 1
}

# ----- 헬퍼 -----

function Test-PhoneConnected {
    $devices = & $AdbPath devices | Where-Object { $_ -match "device$" }
    return $devices.Count -gt 0
}

# ----- 명령 분기 -----

switch ($Command) {

    "status" {
        Write-Host "📱 폰 연결 상태 확인..." -ForegroundColor Cyan
        & $AdbPath devices -l
        if (Test-PhoneConnected) {
            Write-Host "✅ 폰 연결됨" -ForegroundColor Green
            # 앱 설치 여부
            $installed = & $AdbPath shell pm list packages $AppId
            if ($installed) {
                Write-Host "✅ RING-GO 앱 설치됨" -ForegroundColor Green
            } else {
                Write-Host "❌ RING-GO 앱 설치 안 됨 — Android Studio 에서 Run 한번 누르세요" -ForegroundColor Yellow
            }
        } else {
            Write-Host "❌ 폰 연결 안 됨 — USB 케이블 + USB 디버깅 허용 확인" -ForegroundColor Red
        }
    }

    "screen" {
        if (-not (Test-Path $ScreenDir)) {
            New-Item -ItemType Directory -Path $ScreenDir -Force | Out-Null
        }
        $ts = Get-Date -Format "yyyyMMdd_HHmmss"
        $localPath = "$ScreenDir\screen_$ts.png"
        Write-Host "📸 캡처 중..." -ForegroundColor Cyan
        & $AdbPath shell screencap -p /sdcard/screen.png
        & $AdbPath pull /sdcard/screen.png $localPath 2>&1 | Out-Null
        & $AdbPath shell rm /sdcard/screen.png 2>&1 | Out-Null
        if (Test-Path $localPath) {
            Write-Host "✅ 저장됨: $localPath" -ForegroundColor Green
            Write-Host "   Claude 가 보려면 Read tool 로 위 경로 열기"
        } else {
            Write-Host "❌ 캡처 실패" -ForegroundColor Red
        }
    }

    "log" {
        Write-Host "📜 logcat 실시간 — Ctrl+C 로 중단" -ForegroundColor Cyan
        & $AdbPath logcat -c  # 옛 로그 비움
        switch ($Filter) {
            "crash" {
                & $AdbPath logcat AndroidRuntime:E ActivityManager:I "*:S"
            }
            "sms" {
                & $AdbPath logcat -s SmsReceiver:V MmsReceiver:V SmsReplyReceiver:V SmsSender:V MmsDownloadService:V
            }
            "anr" {
                & $AdbPath logcat ActivityManager:I "ANR*:V" "*:S"
            }
            default {
                # 우리 앱만 (pid 기반). $pid 는 PowerShell 예약어라 다른 이름 사용.
                $appPid = (& $AdbPath shell pidof $AppId).Trim()
                if ($appPid) {
                    & $AdbPath logcat --pid=$appPid
                } else {
                    Write-Host "⚠️  앱이 실행 중이 아님 — 전체 logcat 보여줌" -ForegroundColor Yellow
                    & $AdbPath logcat
                }
            }
        }
    }

    "kill" {
        Write-Host "🛑 앱 종료..." -ForegroundColor Cyan
        & $AdbPath shell am force-stop $AppId
        Write-Host "✅ 종료됨" -ForegroundColor Green
    }

    "start" {
        Write-Host "🚀 앱 시작..." -ForegroundColor Cyan
        & $AdbPath shell monkey -p $AppId -c android.intent.category.LAUNCHER 1
        Write-Host "✅ 시작됨" -ForegroundColor Green
    }

    "fakesms" {
        if (-not $From -or -not $Body) {
            Write-Host "사용법: ringo.ps1 fakesms -from '01012345678' -body '메시지'" -ForegroundColor Yellow
            exit 1
        }
        Write-Host "📨 가짜 SMS broadcast 발송..." -ForegroundColor Cyan
        # 주의: Default SMS App 이면 SMS_DELIVER, 아니면 SMS_RECEIVED 로 와야 함
        & $AdbPath emu sms send $From $Body 2>$null
        # emulator 명령이 안 되면 broadcast 로 시도 (실기기 한정)
        Write-Host "ℹ️  emulator 가 아니면 동작 안 함. 실기기에서는 다른 폰으로 실제 보내야 함." -ForegroundColor Yellow
    }

    "db" {
        # 폰의 sqlite3 는 Android 10 에서 제거됨 → PC 의 sqlite3 사용.
        # debug 빌드만 가능 (run-as 명령으로 DB 읽기).
        $sql = switch ($Sub) {
            "calls"   { "SELECT id, phoneNumber, callType, datetime(endedAt/1000, 'unixepoch', 'localtime') FROM call_records ORDER BY endedAt DESC LIMIT 10;" }
            "cats"    { "SELECT id, name, emoji, displayOrder FROM categories ORDER BY displayOrder;" }
            "customers" { "SELECT id, phoneNumber, name, categoryId, depositAmount, balanceAmount, totalAmount FROM customers ORDER BY updatedAt DESC LIMIT 10;" }
            "version" { "PRAGMA user_version;" }
            "tables"  { ".tables" }
            default {
                Write-Host "사용법: ringo.ps1 db [calls|cats|customers|version|tables]" -ForegroundColor Yellow
                exit 1
            }
        }
        Write-Host "🗄️  DB 조회: $Sub" -ForegroundColor Cyan
        $sqlite = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\sqlite3.exe"
        $tmpDir = "$PSScriptRoot\.dbpull"
        if (-not (Test-Path $tmpDir)) { New-Item -ItemType Directory -Path $tmpDir -Force | Out-Null }
        $localDb = "$tmpDir\call_follow_crm.db"
        # exec-out 으로 cat 결과를 파일로. base64 안 거치는 게 깔끔.
        & $AdbPath exec-out "run-as $AppId cat databases/call_follow_crm.db" > $localDb 2>$null
        if ((Get-Item $localDb).Length -lt 100) {
            Write-Host "❌ DB 끌어오기 실패 — debug 빌드인지 확인" -ForegroundColor Red
            exit 1
        }
        if ($sql -eq ".tables") {
            & $sqlite $localDb ".tables"
        } else {
            & $sqlite $localDb -header -column $sql
        }
    }

    "notif" {
        Write-Host "🔔 현재 알림 (RING-GO 만)" -ForegroundColor Cyan
        & $AdbPath shell dumpsys notification --noredact 2>&1 | Select-String -Pattern $AppId -Context 0,5
    }

    "ux" {
        # UX 공모전 비교 페이지 열기 (로컬 웹서버 + 브라우저 자동).
        $uxDir = "$PSScriptRoot\..\docs\ux_contest"
        if (-not (Test-Path $uxDir)) {
            Write-Host "❌ docs\ux_contest 폴더가 없어요" -ForegroundColor Red; exit 1
        }
        $port = 8765
        Write-Host "🌐 로컬 웹서버 시작 (port $port)..." -ForegroundColor Cyan
        Write-Host "   브라우저 자동으로 열림. 끄려면 Ctrl+C." -ForegroundColor Gray
        Start-Sleep -Milliseconds 500
        Start-Process "http://localhost:$port/compare.html"
        Push-Location $uxDir
        try {
            python -m http.server $port
        } finally {
            Pop-Location
        }
    }

    "help" {
        Get-Content $PSCommandPath | Select-Object -First 50
    }

    default {
        Write-Host "❓ 알 수 없는 명령: $Command" -ForegroundColor Red
        Write-Host "사용법: .\scripts\ringo.ps1 [status|screen|log|kill|start|fakesms|db|notif|help]"
    }
}
