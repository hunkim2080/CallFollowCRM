// ════════════════════════════════════════════════════════════════════════
//  브랜드가 도로 흩어지는 것을 빌드에서 막는다. (2026-09-21 사장님)
//
//  왜: 2026-09-21 에 브랜드 북 v5 를 확정하고 앱·프로토·서버 문구를 다 맞췄다.
//      그런데 **막아두지 않으면 몇 달 뒤 또 흩어진다.** 디자인 감시와 같은 이유다.
//
//  어떻게: 화면 문구(문자열)에 **쓰면 안 되는 말**이 들어오면 빌드를 실패시킨다.
//      기준선(개수 세기) 방식이 아니라 **0개 유지** 방식이다 — 지금 전부 0곳이고,
//      여섯 가지 다 "이건 그냥 틀린 말"이라 예외를 둘 이유가 없기 때문.
//      정말 예외가 필요하면 tools/brand_allow.txt 에 그 줄을 적는다.
//
//  ⚠️ 감시 범위를 넓히지 말 것. 말투 전체를 기계로 검사하려 들면 감시가 개발을 막는다.
//     근거: 같은 날 만든 디자인 감시(style_guard)는 **세 가지만** 본다.
//     그 결과 ① 새로 만든 AppTabs.kt 의 실제 위반을 바로 잡아냈고
//            ② 그날 돌린 빌드 여섯 번 동안 헛경보 0건이었다.
//     (그 이상 긴 기간의 근거는 아직 없다 — 둘 다 2026-09-21 에 만들었다.)
//
//  급할 때 건너뛰기:  gradlew assembleRelease -PskipBrandCheck=true
// ════════════════════════════════════════════════════════════════════════

val brandRoot = file("$projectDir/src/main/java/com/detailline/callfollowcrm")
val brandAllowFile = file("$rootDir/tools/brand_allow.txt")

// expo/ = 박람회(별세계) — 사장님 지시로 손대지 않는다.
val brandSkip = listOf("/screen/expo/")

/** 쓰면 안 되는 말: (정규식, 왜 안 되는지, 대신 뭘 쓰는지) */
val brandRules = listOf(
    Triple(Regex("""막내\s*비서"""), "'비서'는 사무실 단어라 현장 브랜드와 안 맞고, 비서는 '똑똑함'을 전제하지 않는다", "막내 / 우리 막내"),
    Triple(Regex("""RING-?GO""", RegexOption.IGNORE_CASE), "RING-GO 는 코드 안에서만 쓰는 옛 이름이다. 화면에 나오면 안 된다", "시공막내"),
    Triple(Regex("""\bLv\.|\bXP\b"""), "레벨·XP 는 '막내가 점점 똑똑해진다'는 뜻이라 확정 인격(이미 똑똑한 막내)과 어긋난다", "손발 (첫날 / 손발 맞는 중 / 척하면 척)"),
    Triple(Regex("""일잘러|레전드|새내기"""), "게임 칭호. 미수금·매출 옆에 있을 말이 아니다", "손발 3단계"),
    Triple(Regex("""\.\.\."""), "말줄임표는 점 세 개가 아니라 한 글자다", "…"),
    Triple(Regex("""해\s주세요|해\s보세요"""), "앱 전체가 붙여 쓴다", "해주세요 / 해보세요")
)

/** 주석이 아닌 줄의 문자열 리터럴만 본다 — 주석 속 옛 이름까지 잡으면 감시가 시끄러워진다. */
val brandLiteral = Regex(""""([^"\\]{1,200})"""")

data class BrandHit(val where: String, val line: Int, val text: String, val why: String, val instead: String)

fun scanBrandHits(): List<BrandHit> {
    val allow = if (brandAllowFile.exists())
        brandAllowFile.readLines().filter { it.isNotBlank() && !it.startsWith("#") }.map { it.trim() }.toSet()
    else emptySet()

    val hits = mutableListOf<BrandHit>()
    if (!brandRoot.exists()) return hits
    brandRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
        val path = f.invariantSeparatorsPath
        if (brandSkip.any { path.contains(it) }) return@forEach
        val short = path.substringAfter("callfollowcrm/")
        f.readLines().forEachIndexed { i, raw ->
            val line = raw.trim()
            if (line.startsWith("//") || line.startsWith("*") || line.startsWith("/*")) return@forEachIndexed
            if (line.contains("android.util.Log.")) return@forEachIndexed   // 로그는 사장님이 볼 글이 아니다
            brandLiteral.findAll(raw).forEach { m ->
                val text = m.groupValues[1]
                if (allow.contains(text)) return@forEach
                brandRules.forEach { (rx, why, instead) ->
                    if (rx.containsMatchIn(text)) hits += BrandHit(short, i + 1, text, why, instead)
                }
            }
        }
    }
    return hits
}

tasks.register("checkBrandCopy") {
    group = "verification"
    description = "화면 문구에 브랜드에 어긋난 말이 들어왔는지 본다"
    doLast {
        if (project.hasProperty("skipBrandCheck")) {
            println("[brand] 건너뜀 (-PskipBrandCheck)")
            return@doLast
        }
        val hits = scanBrandHits()
        if (hits.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("브랜드에 어긋난 문구가 ${hits.size}곳 있습니다. (브랜드 북 v5 확정본)")
                    appendLine()
                    hits.groupBy { it.why }.forEach { (why, g) ->
                        appendLine("  ※ $why")
                        appendLine("    → 대신: ${g.first().instead}")
                        g.take(8).forEach { appendLine("      ${it.where}:${it.line}  \"${it.text}\"") }
                        if (g.size > 8) appendLine("      … 외 ${g.size - 8}곳")
                        appendLine()
                    }
                    appendLine("정말 그 말이어야 하면 tools/brand_allow.txt 에 그 문구를 한 줄로 적으세요.")
                    appendLine("지금 당장 넘어가려면:  -PskipBrandCheck=true")
                }
            )
        }
        println("[brand] 화면 문구 이상 없음 (금지어 ${brandRules.size}종 감시 중)")
    }
}

// 폰에 넣는 빌드에서만 건다. 개발 중 디버그 빌드는 막지 않는다.
tasks.matching { it.name == "assembleRelease" }.configureEach {
    dependsOn("checkBrandCopy")
}
