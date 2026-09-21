// ════════════════════════════════════════════════════════════════════════
//  화면 코드에 값을 **손으로 적는 것**을 빌드에서 막는다. (2026-09-21 사장님)
//
//  왜: 2026-09-20 에 색 325가지·글자 38가지로 흩어진 것을 이름(디자인 시스템)으로 모았다.
//      그런데 **막아두지 않으면 몇 달 뒤 또 흩어진다.** 오늘 고친 게 도로 무너진다.
//
//  어떻게: 지금 있는 개수를 `tools/style_baseline.txt` 에 적어두고,
//      **늘어나면 빌드를 실패**시킨다(줄어드는 건 언제나 환영).
//      한 번에 3,000곳을 고칠 수는 없으니, **더 나빠지는 것만** 막는 방식이다.
//
//  고칠 때: 화면에서 Color(0x…) 대신 AppTheme.colors.X,
//           fontSize = 13.sp 대신 AppType.body,
//           RoundedCornerShape(12.dp) 대신 AppShape.md 를 쓴다.
//
//  기준선 다시 적기(정리해서 줄였을 때):  gradlew styleBaselineUpdate
//  급할 때 건너뛰기:                       gradlew assembleRelease -PskipStyleCheck=true
// ════════════════════════════════════════════════════════════════════════

val styleRoot = file("$projectDir/src/main/java/com/detailline/callfollowcrm/presentation")
val styleBaselineFile = file("$rootDir/tools/style_baseline.txt")

// theme/ = 값의 출처라 당연히 값이 있다. expo/ = 박람회(별세계) — 사장님 지시로 손대지 않는다.
val styleSkip = listOf("/presentation/theme/", "/presentation/screen/expo/")

fun scanStyleCounts(): Map<String, Int> {
    val rxColor = Regex("""Color\(0x[0-9A-Fa-f]{8}\)""")
    val rxSize = Regex("""fontSize\s*=\s*[0-9.]+\.sp""")
    val rxShape = Regex("""RoundedCornerShape\([0-9.]+\.dp\)""")
    val out = sortedMapOf<String, Int>()
    if (!styleRoot.exists()) return out
    styleRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
        val path = f.invariantSeparatorsPath
        if (styleSkip.none { path.contains(it) }) {
            val txt = f.readText()
            val n = rxColor.findAll(txt).count() + rxSize.findAll(txt).count() + rxShape.findAll(txt).count()
            if (n > 0) out[path.substringAfter("/presentation/")] = n
        }
    }
    return out
}

fun readStyleBaseline(): Map<String, Int> {
    if (!styleBaselineFile.exists()) return emptyMap()
    return styleBaselineFile.readLines()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .mapNotNull { line ->
            val i = line.lastIndexOf(' ')
            if (i <= 0) null else line.substring(0, i).trim() to (line.substring(i + 1).trim().toIntOrNull() ?: 0)
        }.toMap()
}

tasks.register("styleBaselineUpdate") {
    group = "verification"
    description = "지금 개수를 tools/style_baseline.txt 에 다시 적는다 (정리해서 줄였을 때)"
    doLast {
        val now = scanStyleCounts()
        styleBaselineFile.parentFile.mkdirs()
        styleBaselineFile.writeText(buildString {
            appendLine("# 화면 코드에 손으로 적은 값(색·글자크기·모서리) 개수 — 늘어나면 빌드 실패.")
            appendLine("# 다시 적기: gradlew styleBaselineUpdate   (줄었을 때만 하세요)")
            appendLine("# theme/ 와 screen/expo/(박람회)는 세지 않습니다.")
            appendLine("# 총 ${now.values.sum()}곳 / ${now.size}파일")
            now.forEach { (k, v) -> appendLine("$k $v") }
        })
        println("기준선 갱신: ${now.values.sum()}곳 / ${now.size}파일")
    }
}

tasks.register("checkHardcodedStyle") {
    group = "verification"
    description = "화면 코드에 값을 손으로 적은 게 늘었는지 본다"
    doLast {
        if (project.hasProperty("skipStyleCheck")) {
            println("[style] 건너뜀 (-PskipStyleCheck)")
            return@doLast
        }
        val base = readStyleBaseline()
        if (base.isEmpty()) {
            println("[style] 기준선이 없습니다 → gradlew styleBaselineUpdate 먼저 돌리세요")
            return@doLast
        }
        val now = scanStyleCounts()
        val bad = mutableListOf<String>()
        now.forEach { (file, n) ->
            val b = base[file]
            if (b == null) {
                bad += "  새 화면 $file : $n 곳 — 값을 이름으로 쓰세요(AppTheme.colors / AppType / AppShape)"
            } else if (n > b) {
                bad += "  $file : $b → $n (${n - b} 늘어남)"
            }
        }
        if (bad.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("화면 코드에 값을 손으로 적은 곳이 늘었습니다.")
                    appendLine()
                    bad.forEach { appendLine(it) }
                    appendLine()
                    appendLine("  색    Color(0xFF3182F6)      →  AppTheme.colors.primary")
                    appendLine("  글자  fontSize = 13.sp       →  style = AppType.body")
                    appendLine("  모서리 RoundedCornerShape(12.dp) →  AppShape.md")
                    appendLine()
                    appendLine("정말 필요해서 늘린 것이면:  gradlew styleBaselineUpdate")
                    appendLine("지금 당장 넘어가려면:       -PskipStyleCheck=true")
                }
            )
        }
        val total = now.values.sum()
        val baseTotal = base.values.sum()
        println("[style] 손으로 적은 값 ${total}곳 (기준선 ${baseTotal}곳) — 늘어난 곳 없음")
    }
}

// 폰에 넣는 빌드(assembleRelease)와 **플레이에 올리는 빌드(bundleRelease)** 둘 다 건다.
//   2026-09-21: assembleRelease 에만 걸려 있었다 — 폰 테스트를 건너뛰고 바로 태그를 찍으면
//   검사 없이 플레이로 나가는 구멍이 있었다. 정작 제일 중요한 문이 열려 있던 셈.
tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }.configureEach {
    dependsOn("checkHardcodedStyle")
}
