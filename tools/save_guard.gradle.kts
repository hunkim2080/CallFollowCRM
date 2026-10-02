// ════════════════════════════════════════════════════════════════════════
//  🤫 **사장님이 누른 저장이 조용히 실패하는 것**을 빌드에서 막는다. (2026-10-02 사장님)
//
//  2026-10-02 Fable 코드 점검 1순위가 이것이었다:
//    「받았어요」를 누르면 고객 카드엔 '받음' 이 찍히는데, **건 전표 저장이 조용히 실패**하면
//    정산은 '미수' 로 남아 다음 날 **잘못된 미수 알람**이 뜬다. (2026-09-17 과 같은 모양)
//
//  그날 10곳을 공용 `presentation/util/SaveGuard.kt` 로 고쳤다. 그런데 **새로 만드는
//  저장 버튼**은 또 `runCatching` 으로 덮일 것이다 — 사람 기억으로는 안 지켜진다.
//  (CLAUDE.md §13① · §12-5)
//
//  그래서 **지금 개수를 기준선에 적어두고 늘어나면 실패**한다. dup_guard 와 같은 방식.
//
//  무엇을 보나: `*ViewModel.kt` 안에서
//    · 이름이 **저장처럼 생긴** 함수 (balance·deposit·paid·complete·amount·memo·note·
//      address·schedule·record·save·update·insert·add·mark·set …)
//    · 그 안에 `runCatching` 이 있고
//    · **말하는 통로가 하나도 없다** (SaveGuard · _toast · snackbar · _message · _error)
//
//  ⚠️ 기준선에 남아 있는 것들은 **「괜찮다」가 아니라 「아직 안 봤다」**는 뜻이다.
//     대부분 주된 저장은 이미 드러나 있고 곁가지만 감싸였지만, 하나씩 확인해 줄여야 한다.
//     줄이면 `gradlew saveBaselineUpdate` 로 기준선을 다시 적는다.
//
//  건너뛰기: gradlew assembleRelease -PskipSaveCheck=true   ← 사장님이 시킬 때만
// ════════════════════════════════════════════════════════════════════════

val saveSrc = File(rootDir, "app/src/main/java/com/detailline/callfollowcrm")
val saveBaseline = File(rootDir, "tools/save_baseline.txt")

val saveNameHint = Regex(
    "(balance|deposit|paid|complete|amount|memo|note|address|schedule|record|save|update|insert|add|mark|set)",
    RegexOption.IGNORE_CASE
)
val saveTell = Regex("(SaveGuard|_toast|toastMessage|snackbar|_message|_error|showError|_tell)",
    RegexOption.IGNORE_CASE)
val saveFunHead = Regex("""^[ \t]*(?:suspend\s+)?fun ([A-Za-z0-9_]+)\(""", RegexOption.MULTILINE)

/** 조용히 삼키는 저장 후보를 센다 → "파일|함수" 집합. */
fun scanSilentSaves(): Set<String> {
    if (!saveSrc.exists()) return emptySet()
    val out = sortedSetOf<String>()
    saveSrc.walkTopDown()
        .filter { it.isFile && it.name.endsWith("ViewModel.kt") }
        .forEach { f ->
            val txt = f.readText()
            val short = f.invariantSeparatorsPath.substringAfter("/com/detailline/callfollowcrm/")
            val heads = saveFunHead.findAll(txt).toList()
            heads.forEachIndexed { i, m ->
                val body = txt.substring(m.range.first, heads.getOrNull(i + 1)?.range?.first ?: txt.length)
                val name = m.groupValues[1]
                if (!saveNameHint.containsMatchIn(name)) return@forEachIndexed
                if (!body.contains("runCatching")) return@forEachIndexed
                if (saveTell.containsMatchIn(body)) return@forEachIndexed
                out += "$short|$name"
            }
        }
    return out
}

tasks.register("saveBaselineUpdate") {
    group = "verification"
    description = "조용히 삼키는 저장 목록을 tools/save_baseline.txt 에 다시 적는다 (줄였을 때만)"
    doLast {
        val now = scanSilentSaves()
        saveBaseline.parentFile.mkdirs()
        saveBaseline.writeText(buildString {
            appendLine("# 🤫 아직 **실패를 화면에 말하지 않는** 저장 함수들. 늘어나면 빌드 실패.")
            appendLine("# 「괜찮다」가 아니라 **「아직 안 봤다」**는 뜻이다 — 하나씩 보고 줄인다.")
            appendLine("# 고치는 법: presentation/util/SaveGuard.kt 를 쓴다 (CLAUDE.md §13①)")
            appendLine("# 다시 적기: gradlew saveBaselineUpdate   (줄였을 때만 하세요)")
            appendLine("# 총 ${now.size}곳")
            now.forEach { appendLine(it) }
        })
        println("조용한 저장 기준선 갱신: ${now.size}곳")
    }
}

tasks.register("checkSilentSaves") {
    group = "verification"
    description = "사장님이 누른 저장이 조용히 실패하는 자리가 늘었는지 본다"
    doLast {
        if (project.hasProperty("skipSaveCheck")) {
            println("[save] 건너뜀 (-PskipSaveCheck)")
            return@doLast
        }
        if (!saveBaseline.exists()) {
            println("[save] 기준선이 없습니다 → gradlew saveBaselineUpdate 먼저 돌리세요")
            return@doLast
        }
        val base = saveBaseline.readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }.map { it.trim() }.toSet()
        val now = scanSilentSaves()
        val added = (now - base).sorted()
        if (added.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("**사장님이 누르는 저장인데 실패해도 화면이 아무 말도 안 하는 곳**이 생겼습니다.")
                    appendLine("조용히 실패하면 사장님은 저장된 줄 알고 넘어갑니다 — 돈·일정이 그렇게 사라집니다.")
                    appendLine()
                    added.forEach { k ->
                        appendLine("  · ${k.substringAfter('|')}   (${k.substringBefore('|')})")
                    }
                    appendLine()
                    appendLine("고치는 법 — 공용 SaveGuard 를 쓰세요:")
                    appendLine("    SaveGuard.run(\"무엇을\", _toast) { …저장… }")
                    appendLine("  (말하는 통로가 없는 ViewModel 이면 _toast 를 만들고 **화면에 배선까지** 하세요)")
                    appendLine()
                    appendLine("정말 조용해도 되는 것이면(캐시·임시값·곁가지) 주석에 **왜** 조용한지 적고:")
                    appendLine("    gradlew saveBaselineUpdate")
                    appendLine("지금 당장 넘어가려면:  -PskipSaveCheck=true  (사장님이 시킬 때만)")
                }
            )
        }
        val gone = (base - now).size
        println("[save] 조용한 저장 ${now.size}곳 (기준선 ${base.size}곳)" +
            if (gone > 0) " — ${gone}곳 고쳐졌습니다 👍 (saveBaselineUpdate 로 기준선을 줄이세요)" else " — 늘어난 곳 없음")
    }
}

tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }.configureEach {
    dependsOn("checkSilentSaves")
}
