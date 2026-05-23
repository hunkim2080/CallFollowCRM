plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.detailline.callfollowcrm"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.detailline.callfollowcrm"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }
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
}
