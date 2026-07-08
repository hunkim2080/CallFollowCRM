package com.detailline.callfollowcrm.util

/**
 * 상담함 광고문자 자동감지 (2026-07-08 사장님) — "광고를 사장님이 하나하나 밀지 말고 앱이 걸러준다".
 *   적대적 검증(고객 문자 오탐 0 겨냥)으로 만든 보수적 휴리스틱. precision 최우선: 애매하면 고객으로 남김.
 *   ⚠️ 여기선 '문자 내용'만 본다. "저장된 고객/이미 답장한 번호엔 적용 안 함"은 호출부(HomeScreen)에서 가드.
 *   앞자리(070 등) 스팸은 이미 SpamPrefix 가 담당 — 역할 분리.
 */
/**
 * 뻔한 광고/스팸 문자 판정 (보수적 = precision 최우선).
 *
 * 절대 원칙: 진짜 고객 문의를 광고로 오탐하면 치명적(사장님이 일감 놓침).
 *   → 조금이라도 고객일 여지가 있으면 false(고객으로 남김). 광고 몇 개 놓쳐도 OK(=미탐 허용).
 *
 * 설계 요약:
 *   - "발신유형 헤더([Web발신]/[국제발신]) 단독"은 절대 근거로 안 씀 —
 *     은행 입금알림·택배·인증번호에도 붙고, 실제로 고객이 그 헤더 달린 계좌알림을 포워딩하거나
 *     해외 로밍 중에 보낼 수 있음. 반드시 '광고 신호'와 결합해야만 판정.
 *   - 발신번호(15xx/16xx/18xx 대표번호) '단독'도 안 씀 — 은행/인증 정상 알림이 그 번호로 옴.
 *   - '대출/카드/보험/이벤트/무료/광고' 같은 단어는 고객이 우연히 씀 → 반드시 광고 전용 조합으로만.
 *   - 도박/성인/리딩방처럼 시공과 어휘가 완전히 다른 것만 단독 허용.
 *
 * @param body    문자 본문
 * @param address 발신번호(원본 문자열, 하이픈/+82 등 섞여 있어도 됨)
 * @return true = 확실한 광고. 애매하면 false.
 */
fun isLikelyAd(body: String, address: String): Boolean {

    // ---------- 0) 발신번호 정규화 ----------
    // 숫자만 뽑고 +82 → 0 으로. (한국 개인 고객은 010 으로 시작)
    val rawDigits = address.filter { it.isDigit() }
    val addrDigits = if (rawDigits.startsWith("82")) "0" + rawDigits.removePrefix("82") else rawDigits

    // ---------- 공통 신호 헬퍼 ----------
    // 링크 존재 여부. 단, naver.me / kakao 지도링크 등은 고객도 위치 공유로 씀 → 링크는 '약신호'로만 사용.
    val hasLink = Regex("(?i)https?://|\\bwww\\.[a-z]").containsMatchIn(body) ||
        Regex("(?i)\\b[a-z0-9-]{2,}\\.(?:kr|com|net|io|link|shop|ly|gg|xyz|top|vip|club|me2)\\b").containsMatchIn(body)

    // 법정 수신거부 표기. 개인 고객은 절대 안 씀 → 강신호. 안전하게 080/번호/거부동사 동반 형태로만.
    val hasOptOut =
        Regex("무료\\s*수신\\s*거부").containsMatchIn(body) ||
        body.contains("무료거부") ||
        Regex("수신\\s*거부\\s*[:：]?\\s*080").containsMatchIn(body) ||
        (body.contains("수신거부") && Regex("080[-\\s]?\\d{3,4}").containsMatchIn(body)) ||
        Regex("수신\\s*동의\\s*철회").containsMatchIn(body)

    // 발신유형 헤더([Web발신]/[국제발신]/[국외발신]/[웹발신]). 단독으로는 안 쓰고 결합 판정용.
    val hasSenderHeader = Regex(
        "[\\[(【]\\s*(?:web|웹|국제|국외)\\s*발신\\s*[\\])】]",
        RegexOption.IGNORE_CASE
    ).containsMatchIn(body)

    // 법정 광고 라벨 (광고)/[광고]. 괄호로 감싼 것만 신뢰(‘광고 보고 연락’ 같은 고객 표현 배제).
    val hasAdLabel = Regex("[\\[(【]\\s*광고\\s*[\\])】]").containsMatchIn(body)

    // 홍보 유인어(그 자체론 약함, 광고 신호와 결합용).
    val hasPromoLure = Regex(
        "클릭|바로가기|지금\\s*(?:신청|가입|접속|참여)|선착순|사은품|기프티콘|무료\\s*상담|혜택\\s*(?:받|드)|쿠폰|이벤트\\s*(?:참여|응모)"
    ).containsMatchIn(body)

    // ---------- 1) 절대 확정 신호 (단독으로 광고) ----------

    // 1-a) 법정 수신거부 표기 → 광고 확정. (오탐 위험 사실상 0)
    if (hasOptOut) return true

    // 1-b) 대량발송 법정 상용구.
    if (Regex(
            "본\\s*(?:문자|메시지)는.*(?:광고|정보통신망|무료\\s*거부)|" +
            "정보통신망법.*광고|" +
            "광고성\\s*정보\\s*수신|광고성정보\\s*수신|" +
            "마케팅\\s*정보\\s*수신|마케팅정보\\s*수신|" +
            "수신에\\s*동의|수신동의(?:를|하)"
        ).containsMatchIn(body)
    ) return true

    // 1-c) 도박/카지노/토토 — 시공과 어휘 완전 무관, 오탐 위험 거의 0.
    if (Regex(
            "카지노|바카라|슬롯|토토|사설\\s*(?:놀이터|배팅)|먹튀\\s*검증|먹튀검증|첫충|매충|롤링|꽁머니|파워볼|안전놀이터"
        ).containsMatchIn(body)
    ) return true

    // 1-d) 성인/유흥 유인 — 시공과 무관.
    if (Regex("조건만남|성인바카라|섹파|애인대행|출장샵|휴게텔|오피걸|야동").containsMatchIn(body)) return true

    // ---------- 2) 조합 신호 (2개 이상 결합해야 광고) ----------

    // 2-a) 발신헤더/국제발신 + (링크 or 홍보유인어).
    //   헤더 단독은 은행·인증에도 붙어 절대 안 됨. 반드시 홍보/링크 결합.
    //   주의: 은행 입금알림·인증번호는 링크·홍보유인어가 없으므로 여기서 통과(고객으로 남음).
    if (hasSenderHeader && (hasLink || hasPromoLure) &&
        // 계좌 입금/인증번호 같은 정상 알림 키워드가 있으면 제외(고객이 포워딩했을 수도).
        !Regex("입금\\s*(?:완료|되|했|확인)|잔액|인증\\s*번호|인증번호|배송\\s*(?:출발|완료|시작)|택배").containsMatchIn(body)
    ) return true

    // 2-b) 대출/금융 스팸 — '대출' 단독은 금지(고객 "대출 갚느라"). 대출관용구 + 금융어 2개 이상.
    val loanIdiom = Regex(
        "저금리|무직자|무서류|무방문|한도\\s*조회|당일\\s*대출|대출\\s*한도|" +
        "정부\\s*지원\\s*대출|채무\\s*통합|대환\\s*대출|연체자\\s*가능|신용\\s*회복|디딤돌|버팀목"
    ).containsMatchIn(body)
    val financeWordCount = Regex("대출|한도|금리|승인|상환|대환|자금\\s*지원").findAll(body).count()
    if (loanIdiom && financeWordCount >= 2) return true

    // 2-c) 카드 발급 광고 — '카드' 단독 금지(고객 "카드결제 되나요"). 발급/사은품/연회비 계열만.
    if (body.contains("카드") &&
        Regex("발급|연회비|캐시백|한도\\s*상향|신규\\s*발급|카드\\s*신청|청구\\s*할인|승인율").containsMatchIn(body)
    ) return true

    // 2-d) 보험 광고 — '보험' + 광고 전용어. 고객 "보험사에서 보상받았는데"는 아래 전용어 없어 통과.
    if (body.contains("보험") &&
        Regex("무료\\s*상담|무료\\s*점검|보장\\s*분석|비교\\s*견적|실비|암보험|보험료\\s*(?:절약|비교)|가입\\s*(?:하|시)").containsMatchIn(body)
    ) return true

    // 2-e) 투자/주식/코인 리딩방 — 핵심어 + 유입맥락(초대/입장/수익/추천). 고객 "코인 리딩방에서 나왔어요"는
    //   유입맥락(입장/초대/추천/수익률/급등)이 없으면 통과.
    val invadeCore = Regex("리딩방|종목\\s*추천|급등주|세력주|수익\\s*인증|해외\\s*선물|매수\\s*타점|매도\\s*타점|무료\\s*체험방|입장\\s*코드|무료\\s*픽").containsMatchIn(body)
    val invadeLure = Regex("입장|초대|모집|수익률|급등|상한가|오픈\\s*(?:채팅|톡)|텔레\\s*그램|접속|무료\\s*픽").containsMatchIn(body)
    if (invadeCore && invadeLure) return true

    // 2-f) 경품/당첨 광고 — 당첨/경품/추첨 + 수령/확인링크 계열. '이벤트' 단독 금지.
    if (Regex("당첨(?:되|됐|자)|경품\\s*(?:추첨|증정|수령)|추첨\\s*(?:결과|당첨)").containsMatchIn(body) &&
        Regex("축하|선정|수령|기프티콘|확인\\s*하기|바로가기|클릭|당첨되셨").containsMatchIn(body)
    ) return true

    // 2-g) (광고) 라벨 + (링크 or 홍보유인어). 라벨만으론 안 씀(혹시 고객이 인용).
    if (hasAdLabel && (hasLink || hasPromoLure)) return true

    // 2-h) 단축링크 + 홍보유인어. 단축도메인이 핵심 스팸 신호.
    if (Regex("(?i)\\b(?:bit\\.ly|me2\\.kr|vvd\\.bz|han\\.gl|buly\\.kr|url\\.kr|goo\\.gl|tinyurl|t\\.me)\\b").containsMatchIn(body) &&
        (hasPromoLure || Regex("할인|이벤트|무료|가입|신청").containsMatchIn(body))
    ) return true

    // 2-i) 장식 특수문자 떡칠(3개+) + 홍보 키워드.
    val decoCount = Regex("[★▶◀◆■●【】◈☆♥♡▷◁✔✅✨🎁🔥※]").findAll(body).count()
    if (decoCount >= 3 &&
        Regex("대출|카드\\s*발급|사은품|이벤트|코인|리딩|카지노|보험\\s*(?:상담|가입)|한도\\s*조회|당일\\s*(?:대출|가능)|주식|선착순|무료\\s*상담|투자|수익\\s*보장").containsMatchIn(body)
    ) return true

    // ---------- 3) 애매하면 고객으로 남긴다 ----------
    return false
}

