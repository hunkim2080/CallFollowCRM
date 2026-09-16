package com.detailline.callfollowcrm.util

import com.detailline.callfollowcrm.domain.inbox.NonCustomerHeuristics

/**
 * 문자에서 **업종을 추측**한다. (2026-09-16 사장님)
 *
 *   "어차피 이 앱을 설치하는 순간 그간의 문자메세지를 한번 훑잖아. 이 사람이 어떤 업종인지
 *    대강 사이즈가 나오잖아. 그럼 추천답변을 처음 눌렀을 때 추측되는 업종을 몇 개 보여주는 거지."
 *
 * 왜 목록 31개를 그냥 안 보여주나: 고르는 게 일이 된다. **2~3개로 좁혀주면 탭 한 번**이다.
 *
 * ⚠️ 어떤 문자를 넣느냐가 전부다 (2026-09-16 실측):
 *   사장님 폰의 **시스템 문자함 105통**을 그대로 넣어보니 업종 단서가 "전기 7 · 시공 2 · 에어컨 1"뿐이었고,
 *   그 "전기" 7건은 전부 **한전·안전 안내 문자**였다. 나머지는 재난문자·인증번호·고속도로 통제.
 *   → 날것의 문자함을 넣으면 **"전기 사장님이시죠?"** 라고 묻게 된다. 안 묻느니만 못하다.
 *   그래서 넣어야 할 것은:
 *     ① **내가 보낸 문자** (견적·안내 — 제일 강한 단서, 가중치 [SENT_WEIGHT])
 *     ② **고객으로 분류된 대화**(상담함) 의 받은 문자
 *   광고·기계문자([Web발신]·인증번호·재난문자)는 [looksMachine] 로 한 번 더 걷어낸다.
 *
 * 그리고 **확신이 없으면 추측하지 않는다** ([MIN_SCORE]). 틀린 추측은 안 하느니만 못하다.
 */
object TradeGuesser {

    /** 이 점수를 넘는 업종만 보여준다. 못 넘으면 빈 리스트 → 화면은 그냥 목록을 보여줄 것. */
    const val MIN_SCORE = 3

    /** 보여줄 최대 개수. 더 늘리면 '고르는 일'이 된다. */
    const val MAX_SUGGESTIONS = 3

    /** 내가 보낸 문자는 남이 보낸 문자보다 몇 배 더 믿는다. */
    const val SENT_WEIGHT = 3

    /** 문자 한 통이 한 업종에 줄 수 있는 최대 점수 — 단어를 나열한 견적 한 통이 전체를 좌우하지 않게. */
    private const val PER_MESSAGE_CAP = 3

    data class Guess(val trade: String, val score: Int)

    /**
     * 문자 한 통. **보낸 사람 번호까지 같이** 받는다.
     *   사장님 2026-09-16: "0000-0000 번호가 8자리면 그냥 그 내용은 참고하지도 마. 그럼 일단 걸러지거든."
     *   맞는 말이다. 대표번호(1588 등)·짧은 코드에서 온 건 **사람이 쓴 문자가 아니다.**
     *   판단은 이미 있는 [NonCustomerHeuristics.isNonPersonalSender] 에 맡긴다(문자함 분류와 같은 자).
     *   address 를 모르면(빈 값) 본문 검사만으로 거른다.
     */
    data class Msg(val address: String?, val body: String)

    /**
     * 업종별 단서. **그 업종 사람만 쓰는 말**을 고른다.
     *   · "시공"·"견적"·"현장" 처럼 모든 업종이 쓰는 말은 넣지 않는다(변별력 0).
     *   · 두 업종이 같이 쓰는 말은 양쪽에 둔다 — 점수로 갈린다.
     * 값은 (단어, 무게). 무게 2 = 그 단어 하나면 거의 확정인 말.
     *
     * ⚠️ 업종 이름은 TradeSelectScreen.TRADE_GROUPS 와 **한 글자도 다르면 안 된다** —
     *    추측을 눌렀을 때 저장되는 값이라, 다르면 화면에서 선택 표시가 안 뜬다.
     */
    private val CLUES: Map<String, List<Pair<String, Int>>> = mapOf(
        "줄눈" to listOf("줄눈" to 2, "에폭시" to 1, "메지" to 2, "백시멘트" to 1, "탄성코트" to 1),
        "실리콘·코킹" to listOf("실리콘" to 2, "코킹" to 2, "바이오실리콘" to 2),
        "도배" to listOf("도배" to 2, "벽지" to 2, "실크벽지" to 2, "합지" to 2, "초배" to 2),
        "장판·마루" to listOf("장판" to 2, "강마루" to 2, "강화마루" to 2, "데코타일" to 1),
        "타일" to listOf("타일" to 2, "포세린" to 2, "덧방" to 2, "600각" to 2, "300각" to 2),
        "페인트·도색" to listOf("페인트" to 2, "도색" to 2, "수성페인트" to 2, "퍼티" to 1),
        "인테리어필름" to listOf("인테리어필름" to 2, "필름 시공" to 2, "시트지" to 2, "래핑" to 1),
        "욕실리모델링" to listOf("욕실리모델링" to 2, "양변기" to 1, "세면대 교체" to 1, "젠다이" to 2),
        "방수·누수" to listOf("누수" to 2, "방수" to 2, "우레탄방수" to 2, "결로" to 1),
        "미장" to listOf("미장" to 2, "셀프레벨링" to 2, "몰탈" to 2),
        "목공·몰딩" to listOf("몰딩" to 2, "목공" to 2, "걸레받이" to 2, "히든도어" to 1),
        "샷시·중문" to listOf("샷시" to 2, "새시" to 2, "중문" to 2, "이중창" to 2),
        "커튼·블라인드" to listOf("커튼" to 2, "블라인드" to 2, "암막" to 1, "롤스크린" to 2),
        "바닥(에폭시·폴리싱)" to listOf("폴리싱" to 2, "에폭시 바닥" to 2, "하드너" to 2, "주차장 바닥" to 1),
        "에어컨 설치·청소" to listOf("에어컨" to 2, "실외기" to 2, "냉매" to 2, "분해청소" to 1),
        "보일러 수리" to listOf("보일러" to 2, "인버터 보일러" to 2, "분배기" to 1),
        "조명·전기" to listOf("조명" to 2, "배선" to 2, "누전" to 2, "차단기" to 2, "콘센트" to 2, "등기구" to 2),
        "수전·배관설비" to listOf("수전" to 2, "샤워기 교체" to 1, "변기 막힘" to 2, "배관" to 1),
        "도어·잠금장치" to listOf("도어락" to 2, "디지털도어" to 2, "잠금장치" to 2),
        "가구 설치·조립" to listOf("가구 조립" to 2, "붙박이장" to 2, "가구 설치" to 2),
        "CCTV·인터폰" to listOf("cctv" to 2, "인터폰" to 2, "비디오폰" to 2, "녹화기" to 1),
        "방충망·방범창" to listOf("방충망" to 2, "방범창" to 2, "미세방충망" to 2),
        "가전 설치" to listOf("가전 설치" to 2, "벽걸이 tv" to 2, "빌트인" to 1),
        "입주청소" to listOf("입주청소" to 2, "입주 청소" to 2, "준공청소" to 2),
        "거주·정기청소" to listOf("정기청소" to 2, "거주청소" to 2, "가사도우미" to 1),
        "사업장청소" to listOf("사업장청소" to 2, "상가청소" to 2, "사무실 청소" to 2),
        "곰팡이 제거" to listOf("곰팡이 제거" to 2, "곰팡이" to 1, "피톤치드" to 1),
        "새집증후군" to listOf("새집증후군" to 2, "베이크아웃" to 2, "포름알데히드" to 2),
        "이사(가정·원룸)" to listOf("포장이사" to 2, "원룸이사" to 2, "이삿짐" to 2, "사다리차" to 1),
        "용달·운송" to listOf("용달" to 2, "퀵 배송" to 1),
        "철거·폐기" to listOf("철거" to 2, "폐기물" to 2, "마대" to 1),
        "광택·디테일링" to listOf("광택" to 2, "디테일링" to 2, "유리막" to 2, "ppf" to 2),
        "썬팅·필름" to listOf("썬팅" to 2, "틴팅" to 2, "열차단 필름" to 2),
        "차량정비" to listOf("엔진오일" to 2, "정비소" to 2, "타이어 교체" to 2, "브레이크 패드" to 2),
        "세차" to listOf("세차" to 2, "손세차" to 2, "스팀세차" to 2)
    )

    private val AGENCY_RX =
        Regex("\\[(행정안전부|기상청|경찰청|한국철도공사|[가-힣]{2,10}(청|부|공사|시청|군청|구청|경찰서|소방서))]")

    /**
     * 사람이 쓴 문자가 아닌 것 — 광고·인증번호·재난문자·공공 안내.
     * 앱의 광고 필터와 **별개로 여기서 한 번 더** 막는다. 한전 안내 문자의 "전기" 같은 오탐 때문.
     */
    fun looksMachine(body: String): Boolean {
        val b = body.trim()
        if (b.isEmpty()) return true
        if (b.contains("[Web발신]")) return true
        // "인증번호" 말고 "인증 코드"로 오는 것도 있다 (실측: Play Console 국제발신).
        if (b.contains("인증번호") || b.contains("인증 번호") || b.contains("인증 코드") || b.contains("인증코드")) return true
        if (b.contains("[국제발신]")) return true
        if (b.contains("(광고)") || b.contains("[광고]") || b.contains("무료거부") || b.contains("수신거부")) return true
        if (AGENCY_RX.containsMatchIn(b)) return true
        if (b.contains("훈련경보") || b.contains("실종") || b.contains("대피") || b.contains("안전수칙")) return true
        return false
    }

    /**
     * @param sent 내가 보낸 문자들 (가장 강한 단서)
     * @param received 받은 문자들
     * @return 점수 높은 순, [MIN_SCORE] 이상만. **빈 리스트 = 추측하지 말 것.**
     */
    fun guess(sent: List<Msg>, received: List<Msg>): List<Guess> {
        val scores = HashMap<String, Int>()

        fun scan(msgs: List<Msg>, weight: Int) {
            for (m in msgs) {
                // ① 보낸 사람이 사람이 아니면 **본문을 읽지도 않는다**. (2026-09-16 사장님)
                val addr = m.address
                if (!addr.isNullOrBlank() && NonCustomerHeuristics.isNonPersonalSender(addr)) continue
                // 재난문자 채널(#CMAS#…)은 번호가 아니라 이름으로 온다 — 숫자 규칙에 안 걸린다.
                //   실측(2026-09-16): 사장님 폰 105통 중 4통이 이 경로로 새어 들어왔다.
                if (addr != null && addr.contains("CMAS")) continue
                // ② 사람 번호에서 왔어도 광고·인증·재난문자면 뺀다.
                if (looksMachine(m.body)) continue
                val body = m.body.lowercase()
                for ((trade, clues) in CLUES) {
                    var hit = 0
                    for ((word, w) in clues) if (body.contains(word)) hit += w
                    if (hit > 0) scores[trade] = (scores[trade] ?: 0) + minOf(hit, PER_MESSAGE_CAP) * weight
                }
            }
        }
        scan(sent, SENT_WEIGHT)
        scan(received, 1)

        return scores.entries
            .filter { it.value >= MIN_SCORE }
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(MAX_SUGGESTIONS)
            .map { Guess(it.key, it.value) }
    }
}
