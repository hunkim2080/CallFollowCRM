// ════════════════════════════════════════════════════════════════════════
//  📏 **화면 파일이 더 커지지 못하게** 막는다. (2026-10-02 사장님)
//
//  2026-10-02 Fable 코드 점검:
//    "화면 파일 4개가 앱 전체 코드의 1/5 이고, 한 화면 함수 하나가 2천 줄이다.
//     그래서 고칠 때마다 옆의 물건이 쓰러진다."
//
//  **지금 쪼개는 건 더 위험하다**(Fable ✋1) — 상태 100개·다이얼로그 48개가 얽혀 있고
//  30일에 80번 바뀌는 파일이라 리팩터 브랜치는 매일 충돌한다.
//  그래서 쪼개지 않는다. 대신 **더 커지는 것만 막는다.**
//  죽은 코드를 치우고 기능 단위로 떼어내면 **저절로 줄어든다.**
//
//  방식은 style_guard·dup_guard 와 같다 — 지금 줄 수를 기준선에 적어두고
//  **늘어나면 실패**. 줄이면 `gradlew sizeBaselineUpdate` 로 다시 적는다.
//
//  ⚠️ 기준선은 **천장이 아니라 지금 위치**다. 기능을 더하려면 **그만큼 떼어내야** 한다 —
//     그게 이 가드의 목적이다(「넣는 만큼 꺼낸다」).
//
//  건너뛰기: gradlew assembleRelease -PskipSizeCheck=true   ← 사장님이 시킬 때만
// ════════════════════════════════════════════════════════════════════════

val sizeSrc = File(rootDir, "app/src/main/java/com/detailline/callfollowcrm")
val sizeBaseline = File(rootDir, "tools/size_baseline.txt")

/** 이 줄 수를 넘는 파일만 지킨다. 작은 파일은 자유롭게 자라도 된다. */
val sizeWatchFrom = 1200

/** 늘어난 걸 봐주는 폭 — 주석 몇 줄 더하는 건 막지 않는다. */
val sizeSlack = 40

fun scanBigFiles(): Map<String, Int> {
    if (!sizeSrc.exists()) return emptyMap()
    val out = sortedMapOf<String, Int>()
    sizeSrc.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
        val lines = f.readLines().size
        if (lines >= sizeWatchFrom) {
            out[f.invariantSeparatorsPath.substringAfter("/com/detailline/callfollowcrm/")] = lines
        }
    }
    return out
}

fun readSizeBaseline(): Map<String, Int> {
    if (!sizeBaseline.exists()) return emptyMap()
    return sizeBaseline.readLines()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .mapNotNull { line ->
            val i = line.lastIndexOf(' ')
            if (i <= 0) null else line.substring(0, i).trim() to (line.substring(i + 1).trim().toIntOrNull() ?: 0)
        }.toMap()
}

tasks.register("sizeBaselineUpdate") {
    group = "verification"
    description = "지금 큰 파일 줄 수를 tools/size_baseline.txt 에 다시 적는다 (줄였을 때만)"
    doLast {
        val now = scanBigFiles()
        sizeBaseline.parentFile.mkdirs()
        sizeBaseline.writeText(buildString {
            appendLine("# 📏 ${sizeWatchFrom}줄 넘는 파일의 **지금 줄 수**. 늘어나면 빌드 실패(여유 ${sizeSlack}줄).")
            appendLine("# 천장이 아니라 **지금 위치**다 — 기능을 더하려면 그만큼 떼어내라.")
            appendLine("# 다시 적기: gradlew sizeBaselineUpdate   (줄였을 때만 하세요)")
            appendLine("# 큰 파일 ${now.size}개 / 합 ${now.values.sum()}줄")
            now.forEach { (k, v) -> appendLine("$k $v") }
        })
        println("파일 크기 기준선 갱신: ${now.size}개 / 합 ${now.values.sum()}줄")
    }
}

tasks.register("checkFileSize") {
    group = "verification"
    description = "큰 화면 파일이 더 커졌는지 본다"
    doLast {
        if (project.hasProperty("skipSizeCheck")) {
            println("[size] 건너뜀 (-PskipSizeCheck)")
            return@doLast
        }
        val base = readSizeBaseline()
        if (base.isEmpty()) {
            println("[size] 기준선이 없습니다 → gradlew sizeBaselineUpdate 먼저 돌리세요")
            return@doLast
        }
        val now = scanBigFiles()
        val bad = mutableListOf<String>()
        now.forEach { (path, lines) ->
            val b = base[path]
            if (b == null) {
                if (lines >= sizeWatchFrom + sizeSlack) {
                    bad += "  · $path : ${lines}줄 — **새로 ${sizeWatchFrom}줄을 넘었습니다**"
                }
            } else if (lines > b + sizeSlack) {
                bad += "  · $path : $b → $lines (+${lines - b}줄)"
            }
        }
        if (bad.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("화면 파일이 더 커졌습니다.")
                    appendLine("큰 파일은 고칠 때마다 **옆의 것이 쓰러진다** — 그래서 더 키우지 않습니다.")
                    appendLine()
                    bad.forEach { appendLine(it) }
                    appendLine()
                    appendLine("할 일 — 셋 중 하나:")
                    appendLine("  ① 더한 만큼 **떼어낸다** (그 파일에서 안 쓰는 것·따로 둘 수 있는 묶음을 빼기)")
                    appendLine("  ② 새 기능을 **다른 파일**에 만든다 (화면 조각이면 presentation/component/)")
                    appendLine("  ③ 정말 이 파일이어야 하면 사장님께 말하고:  gradlew sizeBaselineUpdate")
                    appendLine()
                    appendLine("지금 당장 넘어가려면:  -PskipSizeCheck=true  (사장님이 시킬 때만)")
                }
            )
        }
        val total = now.values.sum()
        val baseTotal = base.values.sum()
        println("[size] 큰 파일 ${now.size}개 / 합 ${total}줄 (기준선 ${baseTotal}줄)" +
            if (total < baseTotal) " — ${baseTotal - total}줄 줄었습니다 👍" else " — 늘어난 곳 없음")
    }
}

tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }.configureEach {
    dependsOn("checkFileSize")
}
