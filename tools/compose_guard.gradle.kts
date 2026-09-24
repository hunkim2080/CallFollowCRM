// ════════════════════════════════════════════════════════════════════════
//  Composable 안의 **early return** 을 빌드에서 막는다. (2026-09-24)
//
//  왜: 이 함정을 **세 번** 밟았다.
//    · 2026-07-30  홈 화면에서 터짐 → 고침(if/else). 주석까지 남김.
//    · 2026-09-23  통계 화면에 "아직 그릴 게 없어요" 넣으면서 또 넣음
//                  → 2026-09-24 테스트폰에서 **통계 탭 누르면 앱이 꺼짐**(재현 100%).
//    · 2026-09-24  그걸 고치면서 만든 새 카드에 **또** 넣음 → 통계 화면이 통째로 빈 화면.
//
//  무엇이 문제인가: Compose 는 화면을 '슬롯 표' 에 순서대로 적는다.
//    중간에 return 으로 빠져나가면 **적다 만 자리**가 생기고,
//    다음에 다시 그릴 때(빈→로드) 표가 어긋나 터진다.
//        java.lang.ArrayIndexOutOfBoundsException: length=0; index=-5
//        at androidx.compose.runtime.SlotTableKt.key
//    스택에 우리 코드가 한 줄도 안 나와서 **원인을 찾기가 아주 어렵다.**
//
//  고치는 법: return 을 없애고 **if / else 로 감싼다.**
//        if (비었음) { 빈 안내 } else { 원래 내용 }
//
//  급할 때 건너뛰기: gradlew assembleRelease -PskipComposeCheck=true
// ════════════════════════════════════════════════════════════════════════

val composeRoot = file("$projectDir/src/main/java/com/detailline/callfollowcrm/presentation")

// Composable 안에서 쓰면 안 되는 것 — 레이아웃 블록에서 빠져나가는 return.
// (forEach/let/run 같은 **비-Composable** 람다의 return@ 는 괜찮으므로 대상에서 뺀다.)
val bannedReturns = listOf(
    "return@Column", "return@Row", "return@Box", "return@Card",
    "return@Scaffold", "return@Surface", "return@LazyColumn", "return@LazyRow"
)

fun scanComposeReturns(): List<String> {
    if (!composeRoot.exists()) return emptyList()
    val hits = mutableListOf<String>()
    composeRoot.walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .forEach { f ->
            f.readLines().forEachIndexed { i, raw ->
                val line = raw.trim()
                // 주석 줄은 건너뛴다 — 경고를 적어둔 줄까지 잡으면 안 된다.
                if (line.startsWith("//") || line.startsWith("*") || line.startsWith("/*")) return@forEachIndexed
                val code = line.substringBefore("//")
                bannedReturns.forEach { bad ->
                    if (code.contains(bad)) {
                        hits += "${f.relativeTo(rootDir).path}:${i + 1}  $bad"
                    }
                }
            }
        }
    return hits
}

val composeGuard = tasks.register("composeGuard") {
    group = "verification"
    description = "Composable 안 early return 검사 (앱이 꺼지는 원인)"
    doLast {
        if (project.hasProperty("skipComposeCheck")) {
            println("[compose] 건너뜀 (-PskipComposeCheck)")
            return@doLast
        }
        val hits = scanComposeReturns()
        if (hits.isEmpty()) {
            println("[compose] Composable 안 early return 없음")
            return@doLast
        }
        val msg = buildString {
            appendLine()
            appendLine("═══════════════════════════════════════════════════════")
            appendLine(" Composable 안에서 빠져나가는 return 을 찾았습니다.")
            appendLine(" 이대로 두면 그 화면이 **꺼지거나 빈 화면**이 됩니다.")
            appendLine("═══════════════════════════════════════════════════════")
            hits.forEach { appendLine("  · $it") }
            appendLine()
            appendLine(" 고치는 법 — return 을 없애고 if / else 로 감싸세요:")
            appendLine("     if (비었음) { 빈 안내 } else { 원래 내용 }")
            appendLine()
            appendLine(" (정말 필요하면: gradlew assembleRelease -PskipComposeCheck=true)")
            appendLine("═══════════════════════════════════════════════════════")
        }
        throw GradleException(msg)
    }
}

tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(composeGuard) }
