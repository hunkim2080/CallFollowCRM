plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("com.google.devtools.ksp") version "1.9.22-1.0.17" apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
    // 2026-07-19 크래시 자동 수집(블랙박스) — 지인 폰에서 앱이 죽으면 자동 보고. (프로덕션 감사 by Fable 5)
    id("com.google.firebase.crashlytics") version "3.0.2" apply false
}
