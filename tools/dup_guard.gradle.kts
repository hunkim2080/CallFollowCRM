// ════════════════════════════════════════════════════════════════════════
//  🧬 **같은 걸 두 벌 만드는 것**을 빌드에서 막는다. (2026-09-28 사장님)
//
//  사장님: "모양은 같고 코드는 둘인거? 뭐 그런게 좀 있는것 같아.
//          그래서 계속 반복된 오류? 버그? 가 생기는것같아. 인정해?"
//
//  맞다. 2026-09-27 하루에만 이것 때문에 터진 게 일곱 건이었다 —
//    · 딱지를 두 곳에서 그려 **글자 없는 상자**가 남았다
//    · 번호를 한쪽은 끝 8자리로, 한쪽은 글자 그대로 맞춰 **손님 카드가 안 붙었다**
//    · 키보드 여백을 두 표기로 적어 18곳을 고쳤는데 **12곳이 그대로 남았다**
//    · 끌기 셈을 두 곳에 복사해 뒀다(내가 직접)
//
//  진짜 원인은 "고칠 때 몇 군데인지 안 세고 시작한다" 이고,
//  그건 **사람 기억으로는 안 고쳐진다.** 그래서 빌드에 건다.
//
//  방식은 style_guard 와 같다 — 지금 개수를 기준선에 적어두고 **늘어나면 실패**.
//  한 번에 다 고칠 수는 없으니 **더 나빠지는 것만** 막는다. 줄이는 건 언제나 환영.
//
//  기준선 다시 적기(정리해서 줄였을 때):  gradlew dupBaselineUpdate
//  급할 때 건너뛰기:                       gradlew assembleRelease -PskipDupCheck=true
// ════════════════════════════════════════════════════════════════════════

val dupRoot = file("$projectDir/src/main/java/com/detailline/callfollowcrm")
val dupBaselineFile = file("$rootDir/tools/dup_baseline.txt")

/**
 * 규칙 = (이름, 찾는 것, 대신 쓸 것, 이 파일은 빼고).
 *   「빼고」에는 **그 개념의 집** 을 적는다 — 거기엔 당연히 그 코드가 있어야 한다.
 */
data class DupRule(
    val id: String,
    val regex: Regex,
    val instead: String,
    val homes: List<String>
)

val dupRules = listOf(
    // ⑦ ⌨️ 키보드 여백 — **세 번째로 터진 자리.** (2026-10-02 사장님 "이것도 두벌이다 뭐다 하는거 아니니")
    //   9/26 에 18곳을 고쳤는데 다른 표기 12곳이 남아 하루 만에 다시 났고,
    //   10/2 엔 `keyboardPadding()` 만 고쳤더니 `windowInsetsPadding(keyboardClearance)` 16곳이
    //   그대로 남아 **하단 탭바가 있는 화면에서 또** 났다(S23U 빈 띠 313px).
    //   셈이 여러 벌이면 **한쪽만 고쳐진다** — 그래서 입구를 둘로 묶고 나머지를 막는다.
    //     · 흐르는 내용   → `Modifier.keyboardPadding()`
    //     · 바닥에 붙는 것 → `Modifier.keyboardOrNavPadding()`
    //   (Scaffold 의 contentWindowInsets 자리만 keyboardClearance 를 쓴다 — 거긴 Modifier 를 못 받는다)
    DupRule(
        id = "keyboard-pad",
        regex = Regex("""\.imePadding\(\)|windowInsetsPadding\(\s*(keyboardClearance|WindowInsets\.ime)"""),
        instead = "Modifier.keyboardPadding() / Modifier.keyboardOrNavPadding()",
        homes = listOf("/util/SystemBars.kt")
    ),
    // ⑦ 하루의 시작(오늘 0시) — **공용을 쓰는 곳이 175곳인데 손으로 적은 게 20곳** 남아 있었다.
    //   (2026-09-30 사장님 "두벌로 되는거 있는지체크했니." — 세어보니 그렇다)
    //   날짜 경계는 **오늘이 언제 시작하느냐**를 정하는 자리다 —
    //   한 곳이 어긋나면 「오늘 시공」이 어제로 넘어가거나 알림이 하루 밀린다.
    //   지금 있는 20곳은 그대로 두고(기준선), **늘어나는 것만** 막는다.
    DupRule(
        id = "day-start",
        regex = Regex("""HOUR_OF_DAY,\s*0\s*\)"""),
        instead = "DateTimeUtils.startOfDay(ms)",
        homes = listOf("/util/DateTimeUtils.kt")
    ),
    // ⑥ 서버가 받아주는 사진 크기 — 앵이 **두 곳에** 적어두고 있었다. (2026-09-30 사장님)
    //   "두벌로 되는거 있는지체크했니."
    //   서버 한도가 바뀌면 **한쪽만 고치게 된다** — 그럼 사진이 조용히 안 올라간다.
    DupRule(
        id = "upload-cap",
        regex = Regex("""1_400_000"""),
        instead = "ImageEncoder.CAP_CHARS",
        homes = listOf("/util/ImageEncoder.kt")
    ),
    // ☎️ 전화 걸기 — **번호로 거는 길은 PhoneDialer 하나**여야 한다. (2026-10-02 Fable 점검)
    //   번호 없이 다이얼러 앱만 여는 것(ACTION_DIAL 단독)은 **다른 일**이다 —
    //   그래서 ACTION_DIAL 이 아니라 **번호가 붙는 `"tel:` 만** 본다.
    //   지금 PhoneDialer 밖엔 0곳 — 새로 생기면 바로 막힌다.
    DupRule(
        id = "dial-tel",
        regex = Regex(""""tel:"""),
        instead = "PhoneDialer.open(context, 번호)  (util/PhoneDialer.kt)",
        homes = listOf("/util/PhoneDialer.kt")
    ),
    // ⑧ 📅 달의 시작·달 넘기기 — **글자가 여덟 가지로 흩어져 있었다.** (2026-10-02 Fable 점검 🧹4)
    //     monthStartOf · monthAnchorOf · monthAnchor · estMonthAnchor · miniMonthAnchor ·
    //     shiftMonth · shiftMonthMs · estShiftMonth · miniShiftMonth · 그리고 손으로 적은 것들.
    //   정산·통계·내기록·일정·마감브리핑·리포트·챗 견적달력이 **각자** 달 경계를 셌다 —
    //   한쪽만 고치면 **같은 달인데 화면마다 합계가 다르게** 나온다.
    //   이제 `DateTimeUtils.startOfMonth / shiftMonth` 한 곳이고 단위테스트 13개가 지킨다.
    DupRule(
        id = "month-start",
        regex = Regex("""set\(\s*(?:java\.util\.)?Calendar\.DAY_OF_MONTH,\s*1\s*\)"""),
        instead = "DateTimeUtils.startOfMonth(ms) / DateTimeUtils.shiftMonth(anchor, delta)",
        homes = listOf("/util/DateTimeUtils.kt")
    ),
    // 🏷️ 대표 건 고르기 — 고객 카드가 가리키는 그 하나. (2026-10-03)
    //   같은 규칙이 **세 곳에 조금씩 다른 글자**로 있었다(`?: jobs.lastOrNull()` · `?: jobs.last()`).
    //   한쪽만 고치면 **고객 카드와 정산이 서로 다른 건을 가리킨다** —
    //   2026-09-17 잘못된 미수 알람, 2026-09-18 1차 잔금 기록 사라짐이 그 모양이다.
    DupRule(
        id = "rep-job",
        regex = Regex("""firstOrNull \{[^}]*>=\s*today[^}]*\}"""),
        instead = "RepresentativeJob.pick(jobs, todayMs)  (domain/job/RepresentativeJob.kt)",
        homes = listOf("/domain/job/RepresentativeJob.kt")
    ),
    // ① 번호 맞추기 — 2026-09-27 상담함 사고의 정체.
    DupRule(
        id = "phone-key",
        regex = Regex("""takeLast\(\s*8\s*\)"""),
        instead = "PhoneKey.of(번호) / PhoneKey.same(a, b) / PhoneKey.mapBy(목록) { 번호 }",
        homes = listOf("/util/PhoneKey.kt")
    ),
    // ④ 창 색 — 화면마다 각자 적어서 87곳 중 55곳이 회색이었다. (2026-09-28 사장님)
    //   ⚠️ **이 규칙은 오탐을 낸다.** 「`AlertDialog(` 뒤 2500자 안에 containerColor 가 없으면」
    //      이라는 어림이라, 인자가 긴 창은 **이미 흰색인데도 걸린다.**
    //      2026-10-03 에 걸린 8곳 중 **진짜는 1곳**뿐이었다(StatsScreen 출발지 창).
    //      → 고치기 전에 **괄호를 세서 그 창의 인자 안만** 봐야 한다:
    //        `scratchpad/dialogscan.py` 가 아니라 **괄호 세는 쪽**(SYNC 2026-10-03 참고).
    //      기준선에 남은 수는 그래서 「고칠 곳」이 아니라 「늘지만 마라」는 뜻이다.
    //   "입력창 뜨면 흰색 도 있는데 회색도 적지않게나와"
    //   안 적으면 안드로이드 기본값(회색 덧칠)이 그대로 나온다.
    DupRule(
        id = "dialog-white",
        regex = Regex("""AlertDialog\((?![\s\S]{0,2500}?containerColor)"""),
        instead = "AlertDialog(containerColor = Color.White, tonalElevation = 0.dp, …)",
        homes = listOf()
    ),
    // 💵 잔금 받음 도장 — **카드와 건을 같이 찍는 곳은 WorkCompletionManager.setBalancePaid 하나.** (2026-10-15 사장님)
    //   홈 미수 카드·정산·완료 팝업·채팅이 **고객 카드에만** 찍고 건은 안 건드려, 카드는 「200만 · 다 받았어요」인데
    //   건은 「금액 미입력」 + 「완료」 로 보였다("완납 안 눌렀는데 완료"). 고객 카드 칸을 직접 찍는 줄이 새로 생기면 막는다.
    DupRule(
        id = "balance-paid-stamp",
        regex = Regex("""customerRepository\.updateBalancePaidAt\("""),
        instead = "workCompletionManager.setBalancePaid(customerId, at, jobId)  (data/repository/WorkCompletionManager.kt)",
        homes = listOf("/repository/WorkCompletionManager.kt")
    ),
    // 📥 상담함 「광고」 판정을 화면에서 **직접 조합**하기. (2026-10-15 사장님 "왜 1이 떠 있을까?")
    //   `customer == null && lastSent != true && 허용목록 아님 && isLikelyAd(lastBody…)` 네 줄이 HomeScreen 에
    //   숫자용·목록용 **두 벌**로 있었다 → 10-14 한쪽만 고쳐져 「답장 안 함 1 인데 열면 빈 목록」.
    //   이제 `isInboxAd` / `inboxUnanswered`(HomeScreenHelpers) 한 곳이고, 화면에서 isLikelyAd 를 lastBody 로 직접 부르면 막는다.
    //   (InboxClassifier 는 **받는 순간** 문자함으로 가를 때 쓰는 다른 문이라 집에 넣는다.)
    DupRule(
        id = "inbox-ad-inline",
        regex = Regex("""isLikelyAd\([^)]*lastBody"""),
        instead = "isInboxAd(item, adAllowlist) / inboxUnanswered(all, adAllowlist)  (presentation/screen/home/HomeScreenHelpers.kt)",
        homes = listOf("/home/HomeScreenHelpers.kt", "/domain/inbox/")
    ),
    // 🤝 협업을 건에 붙이는 셈 — **「대표 건 날짜」 폴백**과 **건 날짜 집합 손계산**. (2026-10-15 사장님 "1차에 협업사장이 지워졌던데")
    //   날짜 없는 협업 줄을 대표 건 날짜로 폴백시키니 1차·2차 둘 다 완료면 대표=2차라 1차 협업이 2차 카드로 갔다.
    //   이제 떠돌이는 어디에도 자동으로 안 붙고(coversDay/forJob 에 representativeDay 없음), 건 날짜 집합은
    //   CollabAssignmentLine.daysOfJob 한 곳. ScheduleScreen 에 남은 둘(미승인 변경 중이라 못 건드림)은 기준선 — 늘면 막는다.
    DupRule(
        id = "collab-rep-fallback",
        regex = Regex("""representativeDay\s*=|until\s+\w+\.scheduledWorkDays\.coerceAtLeast\(1\)\)\.map"""),
        instead = "CollabAssignmentLine.forJob(raw, customerId, jobDayStart) / .daysOfJob(jobDayStart, nDays)  (domain/collab/CollabAssignmentLine.kt)",
        homes = listOf("/domain/collab/")
    ),
    // 🤝 협업 진행 알림(출발·도착·완료·수락·거절)을 **채널로 직접 쏘기**. (2026-10-15 사장님 "출발이 왜 2번 찍히지")
    //   같은 이벤트를 FCM 과 폴링이 각각 띄워 센터에 두 줄씩 찍혔다 → 문을 showCollabEvent 하나로 모으고
    //   그 안의 게이트(CollabNotifyDedup)가 거른다. 협업 진행 채널을 밖에서 직접 쓰면 게이트를 비켜가는 **둘째 문**이다.
    //   지금 NotificationHelper 밖엔 0곳 — 새로 생기면 바로 막힌다.
    DupRule(
        id = "collab-event-notify",
        regex = Regex("""CHANNEL_COLLAB_(DEPARTED|ARRIVED|COMPLETED|ACCEPTED|DECLINED)"""),
        instead = "NotificationHelper.showCollabEvent(…)  (게이트 포함 · 셈은 domain/collab/CollabNotifyDedup.kt)",
        homes = listOf("/service/NotificationHelper.kt")
    ),
    // ③ 끌어서 자리 바꾸기 — 가격표와 접수서에 같은 셈이 두 벌이었다.
    DupRule(
        id = "drag-reorder",
        regex = Regex("""add\(\s*i\s*[-+]\s*1\s*,\s*removeAt\(\s*i\s*\)\s*\)"""),
        instead = "DragReorder.step(order, id, dy, upH, dnH)  (util/DragReorder.kt)",
        homes = listOf("/util/DragReorder.kt")
    ),
    // ⑤ 손님 번호를 **통일 안 하고** 저장하는 곳. (2026-09-29 사장님)
    //   "우리 앱에서는 번호가 두 갈래 세 갈래로 나뉘면 안 돼. 무조건 한 번호로 통일해야 흩어지지 않지."
    //   "이걸 개선하면 다음에도 이런 일이 안 생겨야 하는데"
    //
    //   같은 손님이 두 줄로 갈라지는 길은 **손님을 만드는 곳**과 **백업을 되돌리는 곳** 둘뿐이었다.
    //   둘 다 PhoneKey.normalize 를 거치게 막아뒀다. 이 규칙은 **셋째 길이 생기는 것**을 잡는다 —
    //   customers 테이블에 직접 넣는 코드가 새로 생기면 빌드가 실패한다.
    DupRule(
        id = "customer-insert-raw",
        regex = Regex("INSERT\\s+(?:OR\\s+REPLACE\\s+)?INTO\\s+`?customers`?"),
        instead = "CustomerRepository.upsertByPhone(...) — 번호를 PhoneKey.normalize 로 통일해 넣는다",
        homes = listOf("/util/DataBackup.kt", "/data/local/AppDatabase.kt")
    ),
    // ⑪ 「함께 N번」을 **폰에 쌓인 배정 기록으로** 세는 것. (2026-10-03 테스트폰에서 발견)
    //   같은 사장님이 일정 쪽에선 「함께 4번」, 고객정보 쪽에선 「함께 2번」으로 나왔다
    //   (디테일라인은 22번 ↔ 1번). 사장님이 **일당을 정할 때 제일 큰 참고**인 숫자가
    //   화면마다 다르면 믿을 수가 없다. 서버 집계가 사장님이 확인한 자료다.
    //   ⚠️ `collabAssignments` 자체는 shareId 지도로도 쓰인다 — **세는 것만** 막는다.
    // ⑬ 서버에 말 거는 길을 **손으로 새로 만들기**. (2026-10-03)
    //   `Net.builder()` 로 만들면 [SessionAuthInterceptor] 가 물려 **로그인 토큰이 자동으로 붙는다.**
    //   손으로 `OkHttpClient.Builder()` 를 만들면 **토큰이 안 붙는다** — 지금은 서버가
    //   토큰을 안 보니까 아무 일 없지만, **`AUTH_ENFORCE=1` 을 켜는 날 그 길만 401** 이 된다.
    //   그러면 그 화면만 조용히 빈 채로 뜬다. 켜기 전엔 아무도 못 알아챈다.
    //   ⚠️ 면제(homes)는 **우리 서버의 보호 경로를 안 부르는 것들**이다 —
    //     로그인 자체(/api/auth) · 진단(/api/diagnostics) · 박람회(/api/expo) ·
    //     구글 캘린더 · Ollama(다른 호스트) · 버전 확인(si0in.kr).
    //     여기 새로 추가하기 전에 **그 경로가 _AUTH_PROTECT_PREFIXES 에 없는지** 확인할 것.
    DupRule(
        id = "okhttp-raw",
        regex = Regex("""(?:okhttp3\.)?OkHttpClient\.Builder\(\)"""),
        instead = "Net.builder()  (ai/Net.kt) — 로그인 토큰이 자동으로 붙는다",
        homes = listOf(
            "/ai/Net.kt",
            "/ai/AuthRepository.kt",
            "/ai/ExpoRepository.kt",
            "/ai/OllamaRefineRepository.kt",
            "/data/AppContainer.kt",
            "/util/DiagnosticsReporter.kt",
            "/util/UpdateChecker.kt"
        )
    ),
    // ⑫ 문자앱을 **손으로 열기**. (2026-10-03)
    //   네 곳이 Intent 를 직접 만들었고, 그중 둘은 못 열려도 **아무 말도 안 했다** —
    //   사장님은 「링크 다시 보냈다」고 생각하는데 손님은 아무것도 못 받는다(§13①).
    //   공용은 FLAG_ACTIVITY_NEW_TASK 를 붙이고 **실패를 화면에 말한다.**
    //   ⚠️ MainActivity 의 `Intent.ACTION_SENDTO ->` 는 **받는 쪽**이라 안 걸린다(만들지 않으니까).
    DupRule(
        id = "sms-intent-raw",
        regex = Regex("""Intent\(\s*(?:android\.content\.)?Intent\.ACTION_SENDTO"""),
        instead = "SmsIntentHelper.openSmsCompose(context, 번호, 본문)  (util/SmsIntentHelper.kt)",
        homes = listOf("/util/SmsIntentHelper.kt")
    ),
    DupRule(
        id = "collab-freq-local",
        regex = Regex("""collabAssignments[\s\S]{0,400}?eachCount\(\)"""),
        instead = "sharedSiteRepository.partners(bizPhone) 의 Partner.count — 일정·고객정보가 같은 자료를 본다",
        homes = listOf()
    ),
    // ⑮ 📮 서버로 **직접 쏘기** 금지 — 우체통으로. (2026-10-04 오프라인 아웃박스 1단계 a·b)
    //   협업(reschedule·updateAddress·endCollab·cancel)·사진(uploadOwnerPhoto)은 오프라인에서
    //   runCatching 에 삼켜져 **조용히 사라졌다**(주소가 두 달째 404 였던 게 그 실물 §1-D). 이제 Outbox 가
    //   넣고 길 뚫리면 마저 보낸다. **집(data/outbox/handler)만** 이 함수들을 직접 부른다 — 화면·매니저는 enqueue.
    //   ⚠️ 사진의 pendingUpload(serverUploadedAt IS NULL) 스캔은 **먹여주는 쪽**(OwnerPhotoUploadManager)이
    //      여전히 쓴다 — 그건 「보낼 목록」이 아니라 「아직 안 올린 것 찾기」다. 막는 건 **보내기**(uploadOwnerPhoto)다.
    DupRule(
        id = "outbox-direct-send",
        regex = Regex("""\.(reschedule|updateAddress|endCollab|cancel)\(\s*(?:share|site|sid|shareId)|\.uploadOwnerPhoto\(|(?:repo|sharedSiteRepository)\.(?:progress|postComment)\("""),
        instead = "container.outbox.enqueue(...) (data/outbox/) — 화면·매니저는 enqueue, 보내기는 handler",
        homes = listOf("/data/outbox/")
    ),

    // 📨 상담함/문자함 서버 정정(Haiku)은 **문이 하나**다 — 수신 1건만 묻고 스캔·백필은 안 묻는 비용 가드가 거기 있다. (2026-10-15)
    //   엔드포인트 주소를 다른 파일에 적는 순간 그 가드를 비켜간 두 번째 길이 생긴다(통화요약 신규설치 burst 가 그렇게 났다).
    DupRule(
        id = "thread-classify-raw",
        regex = Regex("""api/thread/classify"""),
        instead = "container.threadServerClassifier.refineIfUnsure(...) (data/repository/ThreadServerClassifier.kt) — 서버 호출은 ai/ThreadClassifyServerRepository.kt 한 곳",
        homes = listOf("/ai/ThreadClassifyServerRepository.kt")
    ),

    // 🎨 아바타 색표 — `AV_TINTS` 가 **일곱 파일**에 각각 있었다(한 색 3벌 · 5색 3벌 · 주황 섞인 5색 1벌). (2026-10-17 협업 업체 동그라미 만들며 셈)
    //   같은 이름인데 값이 달라 "색은 상태에만"(2026-09-20)이 세 화면에만 먹었다. 이제 AvatarTints.SINGLE / .PALETTE 한 곳.
    //   글자(이모지 건너뛰기·브랜드 첫 글자) 셈은 domain/ui/AvatarInitial.
    DupRule(
        id = "avatar-tints",
        regex = Regex("""val\s+\w*_TINTS\s*=\s*listOf|fun\s+avatarInitial\s*\("""),
        instead = "AvatarTints.SINGLE / .PALETTE / .forName(name) (presentation/component/AvatarTints.kt) · 글자는 AvatarInitial.of / .brand (domain/ui/AvatarInitial.kt)",
        homes = listOf("/component/AvatarTints.kt", "/domain/ui/AvatarInitial.kt")
    )
)

fun scanDupCounts(): Map<String, Int> {
    val out = sortedMapOf<String, Int>()
    if (!dupRoot.exists()) return out
    dupRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
        val path = f.invariantSeparatorsPath
        val short = path.substringAfter("/com/detailline/callfollowcrm/")
        val txt = f.readText()
        dupRules.forEach { rule ->
            if (rule.homes.none { path.contains(it) }) {
                val n = rule.regex.findAll(txt).count()
                if (n > 0) out["${rule.id}|$short"] = n
            }
        }
    }
    return out
}

fun readDupBaseline(): Map<String, Int> {
    if (!dupBaselineFile.exists()) return emptyMap()
    return dupBaselineFile.readLines()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .mapNotNull { line ->
            val i = line.lastIndexOf(' ')
            if (i <= 0) null else line.substring(0, i).trim() to (line.substring(i + 1).trim().toIntOrNull() ?: 0)
        }.toMap()
}

tasks.register("dupBaselineUpdate") {
    group = "verification"
    description = "지금 개수를 tools/dup_baseline.txt 에 다시 적는다 (합쳐서 줄였을 때)"
    doLast {
        val now = scanDupCounts()
        dupBaselineFile.parentFile.mkdirs()
        dupBaselineFile.writeText(buildString {
            appendLine("# 같은 걸 두 벌 만든 곳 — 늘어나면 빌드 실패. (2026-09-28)")
            appendLine("# 다시 적기: gradlew dupBaselineUpdate   (합쳐서 줄였을 때만 하세요)")
            appendLine("# 줄 모양: <규칙>|<파일> <개수>")
            appendLine("# 총 ${now.values.sum()}곳 / ${now.size}줄")
            now.forEach { (k, v) -> appendLine("$k $v") }
        })
        println("기준선 갱신: ${now.values.sum()}곳 / ${now.size}줄")
    }
}

tasks.register("checkDuplication") {
    group = "verification"
    description = "같은 걸 두 벌 만든 곳이 늘었는지 본다"
    doLast {
        if (project.hasProperty("skipDupCheck")) {
            println("[dup] 건너뜀 (-PskipDupCheck)")
            return@doLast
        }
        val base = readDupBaseline()
        if (base.isEmpty()) {
            println("[dup] 기준선이 없습니다 → gradlew dupBaselineUpdate 먼저 돌리세요")
            return@doLast
        }
        val now = scanDupCounts()
        val bad = mutableListOf<String>()
        now.forEach { (key, n) ->
            val b = base[key]
            val ruleId = key.substringBefore("|")
            val file = key.substringAfter("|")
            val rule = dupRules.firstOrNull { it.id == ruleId }
            if (b == null) {
                bad += "  [$ruleId] 새로 생김 — $file : ${n}곳\n        대신: ${rule?.instead}"
            } else if (n > b) {
                bad += "  [$ruleId] $file : $b → $n (${n - b} 늘어남)\n        대신: ${rule?.instead}"
            }
        }
        if (bad.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("같은 걸 두 벌 만든 곳이 늘었습니다.")
                    appendLine("한 곳에서만 하도록 공용으로 옮겨 주세요 — 두 벌이면 한쪽만 고쳐져 또 터집니다.")
                    appendLine()
                    bad.forEach { appendLine(it) }
                    appendLine()
                    appendLine("정말 필요해서 늘린 것이면:  gradlew dupBaselineUpdate")
                    appendLine("지금 당장 넘어가려면:       -PskipDupCheck=true")
                }
            )
        }
        val total = now.values.sum()
        val baseTotal = base.values.sum()
        println("[dup] 두 벌로 적힌 곳 ${total}곳 (기준선 ${baseTotal}곳) — 늘어난 곳 없음")
    }
}

// 폰에 넣는 빌드와 플레이에 올리는 빌드 둘 다 건다 — style_guard 와 같은 이유.
tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }.configureEach {
    dependsOn("checkDuplication")
}
