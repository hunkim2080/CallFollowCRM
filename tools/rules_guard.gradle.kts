// ════════════════════════════════════════════════════════════════════════
//  📋 **룰 문서가 코드와 어긋나는 것**을 빌드에서 막는다. (2026-10-02 사장님)
//
//  사장님: "앱 업그레이드를 하면서 **반복 지속적으로 터지는**(두 벌 세 벌 만드는 것 등등)
//          문제를 방지하기 위해 CLAUDE.md 를 봐달라고 한 거거든."
//
//  2026-10-02 Fable 감수에서 나온 진짜 원인은 **룰의 글이 모자란 게 아니었다.**
//  §12 는 이렇게 적혀 있다 — "새 공용을 만들면 이 표에 한 줄 추가한다.
//  표에 없으면 다음 사람이 또 새로 만든다." 옳은 말이다. 그런데
//
//    **그 룰을 적은 당일에 세 번 안 지켰다** (SaveGuard · PhotoModel · QuoteMoney).
//
//  그리고 「절대 수정 금지」에 적힌 두 파일은 **이 repo 에 한 번도 없던 파일**이었고,
//  「빌드가 막아준다」고 적은 11줄 중 실제로 막히는 건 8줄이었다.
//
//  = 표를 **사람 기억으로 적는 한** 표는 계속 썩는다. 썩은 표는
//    "공용이 없네, 새로 만들자" 를 유발한다 → 두 벌 → 한쪽만 고쳐짐 → 또 터짐.
//
//  그래서 **표 자체를 빌드가 지킨다.** 검사 세 가지:
//
//    ① 유령 — 표·문서가 가리키는 파일 경로가 실제로 없으면 실패
//    ② 거짓 — dup_guard 의 규칙 목록과 CLAUDE.md 에 적힌 목록이 다르면 실패
//    ③ 누락 — **새로 공용이 된 파일**(여러 곳에서 쓰이기 시작한 것)이
//              §12 표에도 기준선에도 없으면 실패
//
//  ③ 이 핵심이다. 오늘 놓친 세 개가 정확히 ③ 이다.
//
//  건너뛰기:      gradlew assembleRelease -PskipRulesCheck=true   ← 사장님이 시킬 때만
//  기준선 갱신:   gradlew rulesBaselineUpdate                     ← 표에 적은 뒤에
// ════════════════════════════════════════════════════════════════════════

val rulesDoc = File(rootDir, "CLAUDE.md")
val rulesSrc = File(rootDir, "app/src/main/java/com/detailline/callfollowcrm")
val rulesBaseline = File(rootDir, "tools/shared_baseline.txt")
val dupGuardFile = File(rootDir, "tools/dup_guard.gradle.kts")

/** 공용으로 볼 자리 — 여기 있는 파일이 여러 곳에서 쓰이면 「공용」이다. */
val sharedDirs = listOf("util/", "domain/", "presentation/util/", "presentation/component/")

/** 몇 곳에서 쓰이면 공용으로 보나. 2곳은 우연일 수 있고, 3곳부터는 "셈이 흩어진다". */
val sharedThreshold = 3

/** 공용이 아닌 것(화면 전용 묶음·목록 등)은 이름으로 걸러낸다. */
val sharedIgnore = Regex("""(Screen|ScreenKt|ViewModel|Entity|Dao|Database|Module|Theme)$""")

data class SharedFile(val name: String, val path: String, val users: Int)

/** 공용 후보를 센다 — "`이름.`" 으로 쓰는 다른 파일 수. */
fun scanShared(): List<SharedFile> {
    if (!rulesSrc.exists()) return emptyList()
    val all = rulesSrc.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    val texts = all.associateWith { it.readText() }
    val out = mutableListOf<SharedFile>()
    all.forEach { f ->
        val short = f.invariantSeparatorsPath.substringAfter("/com/detailline/callfollowcrm/")
        if (sharedDirs.none { short.startsWith(it) }) return@forEach
        val name = f.nameWithoutExtension
        if (sharedIgnore.containsMatchIn(name)) return@forEach
        val use = Regex("""\b${Regex.escape(name)}\.""")
        val users = texts.count { (g, t) -> g != f && use.containsMatchIn(t) }
        if (users >= sharedThreshold) out += SharedFile(name, short, users)
    }
    return out.sortedByDescending { it.users }
}

fun readSharedBaseline(): Set<String> {
    if (!rulesBaseline.exists()) return emptySet()
    return rulesBaseline.readLines()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .map { it.trim().substringBefore(' ') }
        .toSet()
}

tasks.register("rulesBaselineUpdate") {
    group = "verification"
    description = "지금 공용 목록을 tools/shared_baseline.txt 에 다시 적는다 (CLAUDE.md §12 표에 적은 뒤에)"
    doLast {
        val now = scanShared()
        rulesBaseline.parentFile.mkdirs()
        rulesBaseline.writeText(buildString {
            appendLine("# 지금 「공용」인 파일들 — 새로 생기면 CLAUDE.md §12 표에 한 줄 적어야 빌드가 통과한다.")
            appendLine("# 다시 적기: gradlew rulesBaselineUpdate   (표에 적은 뒤에 하세요)")
            appendLine("# 줄 모양: <파일이름> <쓰는 파일 수> <경로>")
            appendLine("# 총 ${now.size}개 (${sharedThreshold}곳 이상에서 쓰이는 것)")
            now.forEach { appendLine("${it.name} ${it.users} ${it.path}") }
        })
        println("공용 기준선 갱신: ${now.size}개")
    }
}

tasks.register("checkRules") {
    group = "verification"
    description = "룰 문서(CLAUDE.md)가 코드와 어긋나지 않는지 본다"
    doLast {
        if (project.hasProperty("skipRulesCheck")) {
            println("[rules] 건너뜀 (-PskipRulesCheck)")
            return@doLast
        }
        if (!rulesDoc.exists()) {
            println("[rules] CLAUDE.md 가 없습니다 — 건너뜁니다")
            return@doLast
        }
        val doc = rulesDoc.readText()
        val bad = mutableListOf<String>()

        // ── ① 유령 — 문서가 가리키는 코드 경로가 실제로 있나 ───────────────
        //   「절대 수정 금지」에 없는 파일 두 개가 적혀 있었다(2026-10-02 발견).
        //   백틱 안에 적힌 .kt 경로만 본다 — 글 속의 파일명은 안 본다.
        val ghosts = Regex("""`([A-Za-z0-9_/.]+\.kt)`""").findAll(doc)
            .map { it.groupValues[1] }
            .filter { it.contains('/') }          // 경로 꼴만 (이름만 적은 건 못 찾아도 넘어감)
            .distinct()
            .filter { p ->
                val tail = p.removePrefix("app/src/main/java/com/detailline/callfollowcrm/")
                    .removePrefix("/")
                File(rulesSrc, tail).exists().not() && File(rootDir, p).exists().not()
            }.toList()
        if (ghosts.isNotEmpty()) {
            bad += "① 없는 파일을 가리킵니다 (유령):\n" +
                ghosts.joinToString("\n") { "     · $it" } +
                "\n     → 경로를 고치거나, 지워진 거면 그 줄을 지우세요."
        }

        // ── ② 거짓 — 「빌드가 막는다」고 적은 목록이 실제 가드와 같나 ───────
        //   11줄 중 8줄만 막히는데 전부 막히는 것처럼 읽혔다(2026-10-02 발견).
        if (dupGuardFile.exists()) {
            val real = Regex("""id\s*=\s*"([a-z-]+)"""").findAll(dupGuardFile.readText())
                .map { it.groupValues[1] }.toSortedSet()
            val listed = real.filter { doc.contains("`$it`") }.toSortedSet()
            val missing = real - listed
            if (missing.isNotEmpty()) {
                bad += "② 빌드가 막는 규칙인데 CLAUDE.md §12 에 안 적혀 있습니다:\n" +
                    missing.joinToString("\n") { "     · $it" } +
                    "\n     → 「빌드가 실제로 막는 것」 줄에 `규칙이름` 으로 적으세요."
            }
            val claimed = Regex("""(\d+)가지뿐""").find(doc)?.groupValues?.get(1)?.toIntOrNull()
            if (claimed != null && claimed != real.size) {
                bad += "② CLAUDE.md 는 가드가 ${claimed}가지라고 적었는데 실제는 ${real.size}가지입니다.\n" +
                    "     → 숫자를 ${real.size} 로 고치세요."
            }
        }

        // ── ③ 누락 — 새로 공용이 된 파일이 표에 없나 (이게 핵심) ───────────
        val base = readSharedBaseline()
        val now = scanShared()
        val newcomers = now.filter { it.name !in base && !doc.contains(it.name) }
        if (newcomers.isNotEmpty()) {
            bad += "③ **새로 공용이 됐는데 CLAUDE.md §12 표에 없습니다** " +
                "(여러 곳에서 쓰이기 시작한 것):\n" +
                newcomers.joinToString("\n") { "     · ${it.name}  — ${it.users}곳에서 씀  (${it.path})" } +
                "\n     → §12 「공용이 있는 자리」 표에 한 줄씩 적고," +
                "\n        `gradlew rulesBaselineUpdate` 로 기준선을 갱신하세요." +
                "\n     (표에 없으면 다음 사람이 똑같은 걸 또 만듭니다 — 그게 두 벌의 시작입니다)"
        }

        if (bad.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("룰 문서(CLAUDE.md)가 코드와 어긋났습니다.")
                    appendLine("문서가 썩으면 「공용이 없네, 새로 만들자」가 되고 — 그게 두 벌의 시작입니다.")
                    appendLine()
                    bad.forEach { appendLine("  $it"); appendLine() }
                    appendLine("지금 당장 넘어가려면:  -PskipRulesCheck=true  (사장님이 시킬 때만)")
                }
            )
        }
        println("[rules] 룰 문서와 코드가 맞습니다 (공용 ${now.size}개 감시 중)")
    }
}

// 폰에 넣는 빌드와 플레이에 올리는 빌드 둘 다 — 다른 가드와 같은 자리.
tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }.configureEach {
    dependsOn("checkRules")
}
