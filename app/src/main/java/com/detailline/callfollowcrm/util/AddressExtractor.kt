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

    /** 광역시·특별시 짧은 이름 — roughSite 가 "구"를 우선 쓸지 판단. */
    private val METRO_CITIES = listOf("서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종")
    /**
     * 아파트·건물 꼬리말. **한 곳에서만 관리**한다(roughSite 와 추출 패턴이 따로 놀지 않게).
     *
     * 2026-09-16 사장님 "주소 캐치를 어떤 방식으로 하길래 이렇게 못하지?" → 실측해보니
     *   목록에 없는 브랜드(e편한세상·래미안·아이파크·더샵…)면 통째로 못 잡고 있었다.
     *   사장님 고객은 대부분 "○○아파트 101동 1502호" 로 말하는데 거기가 제일 약했다.
     */
    private const val BRAND =
        "(?:아파트|빌라|연립|맨션|오피스텔|타워|팰리스|푸르지오|자이|힐스테이트|엠밸리|파크|캐슬|" +
        "리버|뷰|빌딩|e편한세상|이편한세상|래미안|아이파크|더샵|센트레빌|롯데캐슬|해링턴|스카이|" +
        "베르디움|리슈빌|한라비발디|더휴|데시앙|어울림|꿈에그린|하이페리온|트라팰리스|" +
        "마을|단지|주공|빌리지|시티|프라자|스퀘어)"

    private val BUILDING_RX = Regex("[가-힣A-Za-z0-9]{1,15}$BRAND")
    private val SI_GUN_RX = Regex("([가-힣]{2,5})(?:시|군)(?=\\s|\\)|$)")
    private val GU_RX = Regex("([가-힣]{1,4})구(?=\\s|\\)|$)")

    /**
     * 아주 짧은 현장 표시 — "시(또는 구) + 아파트/건물명"까지만. "대충 어디" 용. (2026-06-23 사장님)
     *   예: "경기 수원시 장안구 대평로39번길 8 (꽃뫼노을마을 대동아파트)" → "수원 대동아파트".
     *       "서울 강남구 역삼동 래미안아파트" → "강남 래미안아파트".
     *   지역: 광역시면 구, 그 외엔 시/군(없으면 구). 건물: 아파트 등 단지명(마지막 매칭).
     *   둘 다 못 뽑으면 [siteLabel] 로 fallback(빈 것보단 긴 거라도). 주소 없으면 "".
     */
    fun roughSite(raw: String?): String {
        val s = tidyAddress(raw)
        if (s.isBlank()) return ""
        val bldg = BUILDING_RX.findAll(s).lastOrNull()?.value?.trim()
        val metro = METRO_CITIES.firstOrNull { s.contains(it) }
        val region = if (metro != null) {
            GU_RX.find(s)?.groupValues?.get(1) ?: metro
        } else {
            SI_GUN_RX.find(s)?.groupValues?.get(1) ?: GU_RX.find(s)?.groupValues?.get(1)
        }
        val out = listOfNotNull(region, bldg).joinToString(" ").trim()
        return out.ifBlank { siteLabel(raw) }
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

    /**
     * 번지 — "63" · "398-3" · "34번길 63" · "171번길9".
     *   ⚠️ '번길' 뒤에서만 두 번째 숫자를 받는다. 조건 없이 받으면 "우사단로10길 84 **1**층" 의 1,
     *      "거모동 1656 **8** 유성사우나" 의 8 을 주워온다. (2026-09-16 사장님 실제 문자에서 확인)
     */
    private const val BUNJI = "\\d{1,5}(?:\\s*번?길\\s*\\d{1,5})?(?:-\\d{1,5})?(?:번지)?"

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
        // 앞의 "(시/군)" 은 옵션 — "화성시 만세구 새솔동6" 에서 '화성시'가 빠지던 것. (2026-09-16 실측)
        "(?:$SIGUNGU\\s*)?$SIGUNGU\\s*$EUPMYEONDONG(?:\\s*\\d{1,4}길)?\\s*$BUNJI"
    )

    /**
     * 패턴 3: 아파트 단지명 + 단지/동/호
     *   "마곡엠밸리 7단지 705동 1203호"
     *   "한강푸르지오 1234호"
     */
    /**
     * 브랜드 **앞에 붙는 말** — "동탄역 포레너스 아파트" 처럼 공백으로 떨어진 단어 2개까지 끌어온다.
     *   단, 한국어 조사·어미로 끝나는 말은 제외 — "**보넸고요** 현대아파트 102동2504호" 에서
     *   '보넸고요'가 주소에 딸려 들어오던 것. (2026-09-16 사장님 실제 문자에서 확인)
     */
    private const val PREFIX_WORD =
        "(?:[가-힣A-Za-z0-9]{1,15}(?<![요다까죠네음함고서만도은는이가을를에의])\\s+)"

    private val pattern3 = Regex(
        // 앞말은 **있어도 되고 없어도 된다**. 전엔 {2,15} 라 "힐스테이트 1502호" 처럼
        //   브랜드가 문장 맨 앞에 오면 못 잡았다. (2026-09-16 실측)
        "(?:$PREFIX_WORD){0,2}[가-힣A-Za-z0-9]{0,15}$BRAND" +
            "(?:\\s*\\d{1,2}차)?(?:\\s*\\d{1,3}단지)?\\s*$APT_DONG_HO"
    )

    /**
     * 패턴 2b: **번지 숫자가 없는** "지역 + 건물 + 동호수". (2026-09-16 신설)
     *   "송파구 잠실엘스 101동 1503호" · "천호동 래미안 101동 1502호"
     *   패턴2 는 번지(숫자)를 **반드시** 요구해서 이런 흔한 형태를 통째로 놓쳤다.
     *   대신 여기선 "{N}동 {N}호" 를 반드시 요구한다 — 그게 "이건 주소다" 의 증거라 헛다리를 안 짚는다.
     */
    private val pattern2b = Regex(
        "(?:$SIGUNGU|$EUPMYEONDONG)\\s*(?:[가-힣A-Za-z0-9]{1,15}\\s*){0,2}?\\d{1,4}동\\s*\\d{1,5}호"
    )

    /**
     * 2026-05-30 사장님 #6 통점 fix — 동호수 캡처.
     *   pattern1/2 매칭 후 본문 뒤 부분에 "{N}동 {N}호" 또는 "{N}호" 가 가까이 (40자 이내) 있으면 합쳐서 반환.
     *   예: "송파구 잠실엘스 101동 1503호" → pattern2 가 "송파구 잠실엘스..." 매칭 후 뒤에 "101동 1503호" 합침.
     *   pattern3 (아파트) 는 이미 동호수 포함이라 후속 매칭 시도 안 함 (중복 방지).
     */
    private val DONG_HO_TAIL = Regex("^\\s*(?:\\d{1,4}동\\s*)?\\d{1,5}호")

    /**
     * 매칭 뒤 40자 **어디에든** 있는 동호수. (2026-09-16 사장님 실제 문자)
     *   전엔 매칭 **바로 뒤**에만 붙은 걸 찾아서, 사이에 단지명이 끼면 통째로 놓쳤다:
     *     "서울 영등포구 국제금융로 39 **브라이튼여의도** 103동 1210호" → "…국제금융로 39" 에서 끝.
     *   실제 문자 25건 중 6건이 이 모양이었다(사장님 고객은 대부분 단지명을 같이 적는다).
     */
    private val DONG_HO_ANYWHERE = Regex("(\\d{1,4}동\\s*\\d{1,5}호|\\d{1,4}동(?!\\d)|\\d{1,5}호)")

    /** 사이에 낀 말이 '주소다운 글자'인가 — 문장이 통째로 딸려오는 걸 막는 안전장치. */
    private val GAP_OK = Regex("^[가-힣A-Za-z0-9\\s,\\-]{0,20}$")

    /**
     * 찾은 주소 + **본문에서의 위치**. 위치가 필요한 이유(2026-09-16 사장님):
     *   채팅 말풍선에서 그 부분만 **파란 밑줄**로 칠하고, 탭하면 "이 주소로 등록할까요?" 를 띄운다.
     *   (전화번호·날짜가 이미 그렇게 동작 중 — 주소만 한 종류 더 얹는 것)
     */
    data class Found(val text: String, val start: Int, val end: Int)

    /**
     * 본문에서 가장 그럴듯한 주소 1개 추출. 없으면 null.
     * 패턴 1 > 2 > 2b > 3 순으로 시도.
     */
    fun extractOne(body: String): String? = findOne(body)?.text

    /** [extractOne] 과 같은 규칙이되 **위치까지** 준다. */
    fun findOne(body: String): Found? {
        if (body.length < 5) return null
        // 패턴 1·2 매칭 시 매칭 뒤 동호수 자동 합치기 (사장님 #6 통점).
        for (p in listOf(pattern1, pattern2)) {
            p.find(body)?.let { m ->
                val base = m.value.trim()
                val merged = appendDongHo(body, m.range, base)
                // 동호수를 이어붙였으면 끝 위치도 그만큼 늘어난다(밑줄이 주소 전체를 덮게).
                val end = if (merged.length > base.length) {
                    val extra = merged.length - base.length
                    (m.range.last + 1 + extra).coerceAtMost(body.length)
                } else m.range.last + 1
                return Found(merged, m.range.first, end)
            }
        }
        // 번지 없는 "지역 + 건물 + 동호수" → 그 다음. (2026-09-16)
        pattern2b.find(body)?.let {
            return Found(it.value.trim(), it.range.first, it.range.last + 1)
        }
        // 패턴 3 (아파트) 는 이미 동호수 포함이라 그대로 반환.
        pattern3.find(body)?.let {
            return Found(it.value.trim(), it.range.first, it.range.last + 1)
        }
        return null
    }

    /** 주소를 "앞부분 / 동 / 호" 로 쪼갠 것. 등록 시트가 칸을 자동으로 채우는 데 쓴다. */
    data class Parts(val base: String, val dong: String?, val ho: String?)

    private val DONG_RX = Regex("(\\d{1,4})동")
    private val HO_RX = Regex("(\\d{1,5})호")

    /**
     * 고객이 동·호수까지 적어 보냈으면 그걸 따로 뽑는다. (2026-09-16 사장님)
     *   "천호동 래미안 101동 1502호" → base="천호동 래미안", dong="101", ho="1502"
     *   ⚠️ "천호동" 처럼 숫자 없는 행정동은 동으로 치지 않는다(숫자+동 만).
     *   안 적어 보냈으면 null → 시트에서 사장님이 직접 넣는다.
     */
    fun splitDongHo(raw: String?): Parts {
        val s = tidyAddress(raw)
        if (s.isBlank()) return Parts("", null, null)
        val dong = DONG_RX.find(s)?.groupValues?.get(1)
        val ho = HO_RX.find(s)?.groupValues?.get(1)
        var base = s
        if (dong != null) base = base.replace(Regex("\\s*" + dong + "동"), "")
        if (ho != null) base = base.replace(Regex("\\s*" + ho + "호"), "")
        return Parts(base.trim().trim(','), dong, ho)
    }

    /** 매칭 뒤 40자 안에 동호수 있으면 본문 주소에 이어붙임. */
    private fun appendDongHo(body: String, range: IntRange, base: String): String {
        if (range.last + 1 >= body.length) return base
        val tail = body.substring(range.last + 1).take(40)
        // "강남구 역삼동 502호" — 502 가 번지로 먼저 먹혀 '호' 만 남던 것. 바로 뒤가 '호' 면 붙인다.
        //   (2026-05-29 부터 TODO 로 적혀만 있던 것 — 2026-09-16 처리)
        if (tail.startsWith("호") && base.lastOrNull()?.isDigit() == true) {
            return base + "호"
        }
        // 사이에 단지명이 끼어 있어도 동호수까지 끌어온다 — 단, 사이에 낀 말이 주소다울 때만.
        val m = DONG_HO_ANYWHERE.find(tail) ?: return base
        val gap = tail.substring(0, m.range.first)
        if (!GAP_OK.matches(gap)) return base
        return ("$base " + (gap + m.value).trim()).replace(Regex("\\s+"), " ").trim()
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
