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
     * 본문에서 가장 그럴듯한 주소 1개 추출. 없으면 null.
     * 패턴 1 > 2 > 3 순으로 시도.
     */
    fun extractOne(body: String): String? {
        if (body.length < 5) return null
        pattern1.find(body)?.let { return it.value.trim() }
        pattern2.find(body)?.let { return it.value.trim() }
        pattern3.find(body)?.let { return it.value.trim() }
        return null
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
