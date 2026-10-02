// ════════════════════════════════════════════════════════════════════════
//  🩺 **품질 계기판** — 사장님이 코드를 안 봐도 **상태를 숫자로** 보시게. (2026-10-02 사장님)
//
//  사장님: *"어떻게 해야 상위권보다 더 높은 평을 받을 수 있어?"*
//  → 마지막 한 걸음은 **품질이 「의견」이 아니라 「측정값」이 되는 것**이다.
//
//  지금까지는 제가 "괜찮습니다 / 지저분합니다" 라고 **말**했다. 그건 검사할 수가 없다.
//  이제 **빌드가 직접 재서 한 줄로 적는다** — 그러면
//    · 사장님은 **느낌이 아니라 숫자**로 보시고
//    · 저는 **거짓 보고를 할 수가 없다**(숫자가 파일에 남는다)
//
//  돌리기:  gradlew health          → 화면에 뿌리고 docs/HEALTH.md 에 한 줄 쌓는다
//           gradlew health -Pquiet=1 → 파일에만 쌓는다
//
//  ⚠️ 이건 **가드가 아니다.** 아무것도 막지 않는다 — 재서 적기만 한다.
//     막는 건 가드 7개(compose·brand·dup·style·rules·save·size)가 한다.
// ════════════════════════════════════════════════════════════════════════

val hSrc = File(rootDir, "app/src/main/java/com/detailline/callfollowcrm")
val hTest = File(rootDir, "app/src/test")
val hOut = File(rootDir, "docs/HEALTH.md")

fun hCount(root: File, suffix: String, rx: Regex): Int {
    if (!root.exists()) return 0
    return root.walkTopDown().filter { it.isFile && it.name.endsWith(suffix) }
        .sumOf { rx.findAll(it.readText()).count() }
}

fun hLines(root: File): Int {
    if (!root.exists()) return 0
    return root.walkTopDown().filter { it.isFile && it.extension == "kt" }.sumOf { it.readLines().size }
}

fun hBaselineTotal(name: String): Int {
    val f = File(rootDir, "tools/$name")
    if (!f.exists()) return -1
    return f.readLines().filter { it.isNotBlank() && !it.startsWith("#") }
        .sumOf { it.substringAfterLast(' ').trim().toIntOrNull() ?: 0 }
}

fun hBaselineRows(name: String): Int {
    val f = File(rootDir, "tools/$name")
    if (!f.exists()) return -1
    return f.readLines().count { it.isNotBlank() && !it.startsWith("#") }
}

tasks.register("health") {
    group = "verification"
    description = "🩺 품질 계기판 — 숫자를 재서 docs/HEALTH.md 에 한 줄 쌓는다"
    doLast {
        val files = if (hSrc.exists()) hSrc.walkTopDown().count { it.isFile && it.extension == "kt" } else 0
        val lines = hLines(hSrc)
        val big = if (hSrc.exists()) hSrc.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }.count { it.readLines().size >= 2000 } else 0
        val tests = hCount(hTest, ".kt", Regex("""^\s*@Test""", RegexOption.MULTILINE))
        val testFiles = if (hTest.exists()) hTest.walkTopDown().count { it.isFile && it.extension == "kt" } else 0
        val runCatch = hCount(hSrc, ".kt", Regex("""runCatching\s*\{"""))
        val saveGuard = hCount(hSrc, ".kt", Regex("""SaveGuard\.run"""))
        val silent = hBaselineRows("save_baseline.txt")
        val dup = hBaselineTotal("dup_baseline.txt")
        val style = hBaselineTotal("style_baseline.txt")
        val shared = hBaselineRows("shared_baseline.txt")
        val guards = File(rootDir, "tools").listFiles()
            ?.count { it.name.endsWith("_guard.gradle.kts") } ?: 0
        val ciTests = File(rootDir, ".github/workflows/play-deploy.yml")
            .takeIf { it.exists() }?.readText()?.contains("testReleaseUnitTest") == true
        val schemas = File(rootDir, "app/schemas").walkTopDown().count { it.isFile && it.extension == "json" }
        val day = java.text.SimpleDateFormat("yyyy-MM-dd").format(java.util.Date())

        val rows = listOf(
            "앱 코드" to "$files 파일 · ${"%,d".format(lines)}줄",
            "2,000줄 넘는 파일" to "$big 개",
            "자동 시험" to "$tests 개 ($testFiles 파일)",
            "배포할 때 시험이 도나" to if (ciTests) "✅ 돈다" else "❌ 안 돈다",
            "빌드가 막는 가드" to "$guards 개",
            "같은 걸 두 번 적은 곳" to "$dup 곳",
            "손으로 적은 값(색·글자·모서리)" to "$style 곳",
            "공용 목록" to "$shared 개",
            "조용한 저장(아직 안 본 것)" to "$silent 곳",
            "실패를 말하는 저장" to "$saveGuard 곳",
            "runCatching 전체" to "$runCatch 곳",
            "DB 설계도 파일" to "$schemas 개"
        )

        if (!project.hasProperty("quiet")) {
            println()
            println("🩺 품질 계기판 · $day")
            println("─".repeat(46))
            rows.forEach { (k, v) -> println("  %-26s %s".format(k, v)) }
            println("─".repeat(46))
            println("  (막는 건 가드가 한다 — 이건 재서 적기만 한다)")
            println()
        }

        hOut.parentFile.mkdirs()
        if (!hOut.exists()) {
            hOut.writeText(
                "# 🩺 품질 계기판 — 주마다 쌓는 숫자\n\n" +
                    "**빌드가 직접 재서 적는다**(`gradlew health`). 제가 \"괜찮습니다\" 라고 말하는 대신\n" +
                    "**숫자가 파일에 남는다** — 사장님은 느낌이 아니라 숫자로 보시고, 저는 거짓 보고를 할 수 없다.\n" +
                    "(2026-10-02 사장님 \"어떻게 해야 상위권보다 더 높은 평을 받을 수 있어?\")\n\n" +
                    "⚠️ 이건 **가드가 아니다** — 아무것도 막지 않는다. 막는 건 가드 7개가 한다.\n\n" +
                    "## 무엇을 보나\n" +
                    "- **2,000줄 넘는 파일** · **같은 걸 두 번 적은 곳** → 줄어야 좋다\n" +
                    "- **자동 시험** · **공용 목록** · **실패를 말하는 저장** → 늘어야 좋다\n" +
                    "- **조용한 저장(아직 안 본 것)** → 0 이 목표\n" +
                    "- **배포할 때 시험이 도나** → 늘 ✅ 여야 한다\n\n"
            )
        }
        hOut.appendText(
            "\n## $day\n\n| 무엇 | 지금 |\n|---|---|\n" +
                rows.joinToString("\n") { "| ${it.first} | ${it.second} |" } + "\n"
        )
        println("[health] docs/HEALTH.md 에 $day 줄을 쌓았습니다")
    }
}
