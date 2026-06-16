import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
}

// 릴리즈 서명 — keystore.properties (git 제외) 가 있으면 정식 키로 서명. 없으면 미서명(unsigned).
//   ⚠️ ringgo-release.jks + keystore.properties 분실 시 앱 업데이트 불가 → 반드시 백업.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties()
if (keystorePropsFile.exists()) {
    keystorePropsFile.inputStream().use { keystoreProps.load(it) }
}

// 자동 버전 (2026-06-16 사장님) — 매번 수동으로 안 올려도 되게.
//   versionCode = git 커밋 수(커밋마다 +1, 단조 증가) → 폰이 "새 버전"으로 인식하는 진짜 번호.
//   versionName = "0.2-beta" (화면에 보이는 라벨. 마케팅용으로 가끔 손으로 0.3-beta 식으로만 올림).
//     → 안드 설정 화면엔 "0.2-beta (574)" 처럼 versionCode 가 괄호로 같이 보여 빌드 구분 가능.
//   BUILD_TIMESTAMP = 빌드 mtime(ms) → 앱이 서버 /api/download/version 의 mtime_ms 와 비교해 새 버전 감지.
val buildTimeMs = System.currentTimeMillis()
fun gitCommitCount(): Int = try {
    ProcessBuilder("git", "rev-list", "--count", "HEAD")
        .redirectErrorStream(true).start()
        .inputStream.bufferedReader().use { it.readText() }.trim().toIntOrNull() ?: 1
} catch (e: Exception) { 1 }

android {
    namespace = "com.detailline.callfollowcrm"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.detailline.callfollowcrm"
        minSdk = 26
        targetSdk = 34
        versionCode = gitCommitCount()
        versionName = "0.2-beta"
        buildConfigField("long", "BUILD_TIMESTAMP", "${buildTimeMs}L")
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    // 릴리즈 APK 파일명을 항상 shigongmagne.apk 로 고정 (베타 배포 링크가 이 이름을 가리킴). (2026-06-17 사장님)
    //   안드로이드 설치/업데이트는 패키지명+versionCode 로 판단 → 파일명은 배포 경로 일치용일 뿐.
    applicationVariants.all {
        val variant = this
        variant.outputs.all {
            if (variant.buildType.name == "release") {
                (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName = "shigongmagne.apk"
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true; buildConfig = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.10" }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    // 2024.02.00 (Compose UI 1.6.1) 에서는 갤럭시에서 "ACTION_HOVER_EXIT event was not cleared"
    // 크래시 발생 (Samsung 디바이스 hover 이벤트 처리 버그). Compose UI 1.6.6+ 에서 패치됨.
    // 2024.06.00 = Compose 1.6.8 로 업그레이드.
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Firebase Cloud Messaging — 즉시 푸시(협업 요청/진행). google-services.json 으로 설정. (2026-06-12)
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-messaging-ktx")

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.navigation:navigation-compose:2.7.7")

    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.documentfile:documentfile:1.0.1")

    // 시간 기반 알림(시공 D-1·잔금 미수·마감 브리핑) 스케줄러 — 앱 종료 상태에서도 동작.
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // 현장 도착 안내 — 지오펜싱(시공 현장 5km 진입 감지). FusedLocation + Geofencing.
    implementation("com.google.android.gms:play-services-location:21.3.0")
    // 내 번호 자동입력 — 구글 전화번호 힌트(한 번 탭). (2026-06-12)
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    // 템플릿 첨부 사진 썸네일 로딩
    implementation("io.coil-kt:coil-compose:2.5.0")

    // 예약 확정 시 팡파레 애니메이션
    implementation("nl.dionsegijn:konfetti-compose:2.0.4")

    // 사진 첨부 MMS 직접 발송 — 우리 앱 안에서 끝내기 위함 (chooser/수신인 선택 화면 안 뜨게).
    // 정확한 owner = klinker41 (klinkerapps 가 아님). jitpack 빌드 가능한 최신 = 5.2.5.
    // 실패 시 SmsSender.sendMms() 가 false 반환 → ChatScreen 자동 fallback (갤럭시 메시지 인텐트).
    implementation("com.github.klinker41:android-smsmms:5.2.5")

    // 자체 서버(맥미니 Ollama, Tailnet 100.86.114.49:11434) 호출용.
    // 호출 1개(/api/chat)뿐이라 Retrofit 생략하고 OkHttp 만 사용. JSON 은 android 내장 org.json.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
    // Repository / DAO 를 가짜로 만들어 ViewModel/Repository 로직 테스트하기 위함.
    //   2026-05-30 D 코스 — 자동 회귀 테스트 인프라 구축.
    testImplementation("org.mockito:mockito-core:5.10.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    // suspend 함수 (Room DAO) 테스트용 — runTest, TestDispatcher.
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}
