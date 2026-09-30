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
    // ⑥ 서버가 받아주는 사진 크기 — 앵이 **두 곳에** 적어두고 있었다. (2026-09-30 사장님)
    //   "두벌로 되는거 있는지체크했니."
    //   서버 한도가 바뀌면 **한쪽만 고치게 된다** — 그럼 사진이 조용히 안 올라간다.
    DupRule(
        id = "upload-cap",
        regex = Regex("""1_400_000"""),
        instead = "ImageEncoder.CAP_CHARS",
        homes = listOf("/util/ImageEncoder.kt")
    ),
    // ① 번호 맞추기 — 2026-09-27 상담함 사고의 정체.
    DupRule(
        id = "phone-key",
        regex = Regex("""takeLast\(\s*8\s*\)"""),
        instead = "PhoneKey.of(번호) / PhoneKey.same(a, b) / PhoneKey.mapBy(목록) { 번호 }",
        homes = listOf("/util/PhoneKey.kt")
    ),
    // ② 키보드 여백 — 2026-09-26 에 18곳 고치고 2026-09-27 에 12곳이 또 나왔다.
    DupRule(
        id = "keyboard-inset",
        regex = Regex("""\.imePadding\(\)"""),
        instead = "Modifier.keyboardPadding()  (util/SystemBars.kt)",
        homes = listOf("/presentation/util/SystemBars.kt")
    ),
    // ④ 창 색 — 화면마다 각자 적어서 87곳 중 55곳이 회색이었다. (2026-09-28 사장님)
    //   "입력창 뜨면 흰색 도 있는데 회색도 적지않게나와"
    //   안 적으면 안드로이드 기본값(회색 덧칠)이 그대로 나온다.
    DupRule(
        id = "dialog-white",
        regex = Regex("""AlertDialog\((?![\s\S]{0,2500}?containerColor)"""),
        instead = "AlertDialog(containerColor = Color.White, tonalElevation = 0.dp, …)",
        homes = listOf()
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
