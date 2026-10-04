# ─────────────────────────────────────────────────────────────────────────────
# R8 (코드 줄이기·이름 줄이기) 규칙.  isMinifyEnabled = true 에서만 적용.
#
# 왜 켜나 (2026-10-03 사장님 "앱 뜯기 어렵게"):
#   ① 배포판에서 **코드가 줄고 이름이 뒤섞여** 뜯어보기 어려워진다(= 서버 주소·요청 모양 숨김).
#   ② 아래 로그 제거로 **배포판에 로그가 안 남는다** — 손님 번호가 폰 기록에 샐 길을 막는다.
#   ③ 앱이 작아진다.
#
# ⚠️ 우리 앱 위험 평가(실측):
#   · JSON 은 전부 org.json(글자 키 직접) → R8 이 **안 건드린다**(안전).
#   · Gson/Moshi/리플렉션 **0**.
#   · 위험은 **enum** — `CallType.valueOf("INCOMING")` 식으로 **이름으로 복원**한다.
#     R8 이 enum 상수 이름을 줄이면 깨진다(일부는 runCatching 도 안 감쌈). → 아래에서 통째로 지킨다.
#   · 알림음은 **자원 줄이기(shrinkResources)** 를 끄면 안전(getIdentifier 는 런타임).
# ─────────────────────────────────────────────────────────────────────────────

# ── Room (생성 클래스) ──
-keep class androidx.room.** { *; }
-dontwarn androidx.room.paging.**

# ── 🔴 우리 enum 은 상수 이름까지 통째로 지킨다 ──
#   `valueOf("...")`·`.name` 으로 DB·서버와 글자로 주고받기 때문. 이름이 줄면 복원이 깨진다.
-keepclassmembers enum com.detailline.callfollowcrm.** { *; }
-keep enum com.detailline.callfollowcrm.** { *; }

# ── Parcelable (MainActivity·SmsIntentHelper 등) — CREATOR 는 이름으로 찾는다 ──
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# ── @Keep 붙은 것은 손대지 않는다(androidx) ──
-keep @androidx.annotation.Keep class * { *; }
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

# ── enum 표준(안전망) ──
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ── 🔒 배포판에서 로그를 지운다 (보안) ──
#   debug·verbose·info 는 통째로 제거. 경고·에러(w/e)는 크래시 분석에 필요해 남긴다.
#   (우리는 util/LogRedact 로 번호를 가리지만, 아예 안 남기는 게 더 안전)
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
    public static int i(...);
}

# ── 줄 번호는 남긴다 — 크래시가 어디서 났는지 봐야 한다 ──
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
