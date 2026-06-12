package com.detailline.callfollowcrm.util

/**
 * 한국어 SMS 본문에서 시공 현장 주소 추출 — 1차 정규식 휴리스틱.
 *
 * 사장님 통점 (2026-05-25): 시공자는 현장 주소를 가장 자주 복사 (네비, 동료 공유, 견적 작성 등).
 * 고객 메시지에서 주소가 언급되면 자동 추출 → CustomerDetail 에 표시 → 한 탭 복사.
 *
 * 한계:
 *  - 정규식 기반 → 광역시도 + 시군구 + 동/도로명 패턴만 매칭. "여의도" "마곡" 같은 동만 있는 케이스 누락.
 *  - 다음 세션: 서버 conversation-summary endpoint 에 `extracted_address` 필드 추가 (LLM).
 *    클라이언트 추출은 fallback.
 *
 * 추출 우선순위:
 *  1. 광역시도 + 시군구 + 동/로/길 + (선택 번지/숫자) — "서울 강서구 마곡동 740"
 *  2. 시군구 + 동/로/길 + 번지 — "강서구 마곡동 740-1"
 *  3. 동/로/길 + 번지 — "마곡로 27길 30" (도로명만 있는 케이스)
 *  4. 아파트 + 동/호 — "마곡엠밸리 7단지 705동 1203호"
 */
object AddressExtractor {

    /**
     * 주소 표시 정제 — 문장형으로 저장된 주소의 군더더기(어미·안내어)만 보수적으로 제거. (2026-06-07)
     *   예: "경기 시흥시 가마길 2 입니다" → "경기 시흥시 가마길 2". 정상 주소 토큰은 절대 안 건드림.
     *   표시 직전에만 적용(저장값은 유지). 바깥에서 안전하게 호출.
     */
    fun tidyAddress(raw: String?): String {
        var s = (raw ?: "").trim()
        if (s.isEmpty()) return s
        // 선행 안내어
        for (p in listOf("주소는", "주소:", "주소 :", "현장은", "현장:", "현장 :", "위치는", "위치:")) {
            if (s.startsWith(p)) { s = s.removePrefix(p).trim(); break }
        }
        // 말미 종결 어미(2자 이상만 — 'ㅇ요' 단독 등 위험한 1자는 제외). 한 번만.
        for (t in listOf("입니다.", "입니다", "이에요.", "이에요", "예요.", "예요", "이예요", "입니당",
                "이고요", "이구요", "에요.", "에요", "이요.", "이요", "이라고요", "이라네요")) {
            if (s.endsWith(t)) { s = s.dropLast(t.length).trim(); break }
        }
        return s.trim()
    }

    /**
     * 협업 현장 표시 제목 — 주소에서 상세 호수(동·호·층)를 떼고 "지역명 + 아파트(건물)명"까지만. (2026-06-13 사장님)
     *   예: "강동구 천호동 래미안강동팰리스 101동 1502호" → "강동구 천호동 래미안강동팰리스".
     *   "천호동" 처럼 숫자 없는 행정동은 보존(\d+동 만 제거). 주소 없으면 "" → 호출부가 이름 등으로 fallback.
     */
    fun siteLabel(raw: String?): String {
        var s = tidyAddress(raw)
        if (s.isBlank()) return ""
        s = s.replace(Regex("\\s*제?\\s*\\d{1,4}동(?:\\s*\\d{1,5}호)?.*$"), "")  // "101동 1502호…" / "101동"
            .replace(Regex("\\s*\\d{1,5}호$"), "")                              // 동 없이 "1502호"
            .replace(Regex("\\s*(?:지하|B)\\s*\\d+.*$"), "")                    // 지하 N
            .trim()
        return s
    }

    /**
     * 광역시도 정확 매칭. 자주 쓰는 줄임형 + 전체형.
     */
    private const val SIDO =
        "(?:서울특별시|서울시|서울|부산광역시|부산시|부산|대구광역시|대구시|대구|" +
        "인천광역시|인천시|인천|광주광역시|광주시|광주|대전광역시|대전시|대전|" +
        "울산광역시|울산시|울산|세종특별자치시|세종시|세종|" +
        "경기도|경기|강원도|강원특별자치도|강원|" +
        "충청북도|충북|충청남도|충남|전라북도|전북|전라남도|전남|" +
        "경상북도|경북|경상남도|경남|제주특별자치도|제주도|제주)"

    /** 시·군·구. 한글 2~6자 + 시/군/구. */
    private const val SIGUNGU = "[가-힣]{1,8}(?:시|군|구)"

    /** 동·읍·면·로·길. 숫자/한글 혼합 허용. */
    private const val EUPMYEONDONG = "[가-힣\\d]{1,15}(?:동|읍|면|로|길)"

    /** 번지·번 — 숫자 + 옵션 -숫자 + 옵션 번. */
    private const val BUNJI = "\\d{1,5}(?:-\\d{1,5})?(?:번지?)?"

    /** 아파트 단지 / 동 / 호 패턴. */
    private const val APT_DONG_HO = "(?:\\d{1,4}동\\s*)?\\d{1,5}호"

    /**
     * 패턴 1: 광역시도 + 시군구 + 동/로/길 + 옵션 번지
     *   "서울 강서구 마곡동 740"
     */
    private val pattern1 = Regex(
        "$SIDO\\s*$SIGUNGU\\s*$EUPMYEONDONG(?:\\s*$BUNJI)?"
    )

    /**
     * 패턴 2: 시군구 + 동/로/길 + 번지 (광역시도 생략된 케이스)
     *   "강서구 마곡로 27길 30"
     */
    private val pattern2 = Regex(
        "$SIGUNGU\\s*$EUPMYEONDONG(?:\\s*\\d{1,4}길)?\\s*$BUNJI"
    )

    /**
     * 패턴 3: 아파트 단지명 + 단지/동/호
     *   "마곡엠밸리 7단지 705동 1203호"
     *   "한강푸르지오 1234호"
     */
    private val pattern3 = Regex(
        "[가-힣A-Za-z]{2,15}(?:아파트|빌라|오피스텔|타워|팰리스|푸르지오|자이|힐스테이트|엠밸리|파크|캐슬|리버|뷰)" +
        "(?:\\s*\\d{1,3}단지)?\\s*$APT_DONG_HO"
    )

    /**
     * 2026-05-30 사장님 #6 통점 fix — 동호수 캡처.
     *   pattern1/2 매칭 후 본문 뒤 부분에 "{N}동 {N}호" 또는 "{N}호" 가 가까이 (40자 이내) 있으면 합쳐서 반환.
     *   예: "송파구 잠실엘스 101동 1503호" → pattern2 가 "송파구 잠실엘스..." 매칭 후 뒤에 "101동 1503호" 합침.
     *   pattern3 (아파트) 는 이미 동호수 포함이라 후속 매칭 시도 안 함 (중복 방지).
     */
    private val DONG_HO_TAIL = Regex("^\\s*(?:\\d{1,4}동\\s*)?\\d{1,5}호")

    /**
     * 본문에서 가장 그럴듯한 주소 1개 추출. 없으면 null.
     * 패턴 1 > 2 > 3 순으로 시도.
     */
    fun extractOne(body: String): String? {
        if (body.length < 5) return null
        // 패턴 1·2 매칭 시 매칭 뒤 동호수 자동 합치기 (사장님 #6 통점).
        pattern1.find(body)?.let { m ->
            return appendDongHo(body, m.range, m.value.trim())
        }
        pattern2.find(body)?.let { m ->
            return appendDongHo(body, m.range, m.value.trim())
        }
        // 패턴 3 (아파트) 는 이미 동호수 포함이라 그대로 반환.
        pattern3.find(body)?.let { return it.value.trim() }
        return null
    }

    /** 매칭 뒤 40자 안에 동호수 있으면 본문 주소에 이어붙임. */
    private fun appendDongHo(body: String, range: IntRange, base: String): String {
        if (range.last + 1 >= body.length) return base
        val tail = body.substring(range.last + 1).take(40)
        val dongHo = DONG_HO_TAIL.find(tail)?.value?.trim() ?: return base
        return "$base $dongHo"
    }

    /**
     * 메시지 리스트에서 추출. 가장 최근 메시지 (List 의 처음) 부터 훑어 첫 매칭 반환.
     * 최근 대화의 주소가 = 현재 시공 대상 주소일 가능성 ↑.
     *
     * @param messages 최신순 정렬된 본문 리스트
     */
    fun extractFromMessages(messages: List<String>): String? {
        for (body in messages) {
            extractOne(body)?.let { return it }
        }
        return null
    }
}
