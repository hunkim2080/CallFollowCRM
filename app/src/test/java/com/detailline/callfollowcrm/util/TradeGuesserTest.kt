package com.detailline.callfollowcrm.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 문자로 업종 추측하기. (2026-09-16 사장님 아이디어)
 *
 * 여기서 제일 중요한 건 "맞히는 것"이 아니라 **틀린 걸 안 내미는 것**이다.
 * 사장님 실제 문자함 105통에는 업종 단서가 사실상 없었고(재난문자·인증번호가 대부분),
 * 그걸 그대로 넣으면 "전기 사장님이시죠?"(한전 문자 때문)가 나왔다.
 * 아래 '기계 문자' 테스트들이 그 사고를 막는 그물이다.
 */
class TradeGuesserTest {

    /** 번호를 안 적으면 평범한 개인 휴대폰에서 온 걸로 본다. */
    private fun g(sent: List<String> = emptyList(), received: List<String> = emptyList()) =
        TradeGuesser.guess(
            sent.map { TradeGuesser.Msg("01012345678", it) },
            received.map { TradeGuesser.Msg("01098765432", it) }
        )

    // ── 맞혀야 하는 것 ─────────────────────────────────────────

    @Test
    fun `내가 보낸 견적 문자 한 통이면 업종이 나온다`() {
        // 보낸 문자는 가중치가 높아 한 통으로도 기준을 넘는다
        val r = g(sent = listOf("안녕하세요 사장님, 양쪽 화장실 바닥 줄눈 시공 105만원입니다."))
        assertEquals("줄눈", r.first().trade)
    }

    @Test
    fun `받은 문자만 있어도 여러 통 쌓이면 나온다`() {
        val r = g(
            received = listOf(
                "거실 도배 견적 얼마인가요",
                "실크벽지로 하면 얼마나 차이나요?",
                "안방 벽지도 같이 부탁드려요"
            )
        )
        assertEquals("도배", r.first().trade)
    }

    @Test
    fun `비슷한 업종이 섞여도 더 많이 나온 쪽이 위로`() {
        val r = g(
            sent = listOf("화장실 타일 덧방 시공 견적입니다", "포세린 타일로 진행할게요"),
            received = listOf("줄눈도 같이 되나요?")
        )
        assertEquals("타일", r.first().trade)
        // 고객이 지나가듯 한 번 물어본 "줄눈"은 후보로 올리지 않는다 —
        //   한 번 언급됐다고 업종 후보에 끼우면, 문의가 다양한 사장님일수록 엉뚱한 게 뜬다.
        assertTrue("스쳐간 한마디는 후보가 아니다", r.none { it.trade == "줄눈" })
    }

    @Test
    fun `후보는 최대 3개까지만`() {
        val r = g(
            sent = listOf(
                "줄눈 시공", "도배 벽지", "타일 덧방", "에어컨 실외기", "보일러 분배기", "썬팅 틴팅"
            )
        )
        assertTrue("고르는 게 일이 되면 안 된다", r.size <= TradeGuesser.MAX_SUGGESTIONS)
    }

    // ── 절대 틀리면 안 되는 것 ─────────────────────────────────

    @Test
    fun `재난문자와 인증번호만 있으면 아무 추측도 안 한다`() {
        // 사장님 테스트폰에서 실제로 나온 문자들 그대로
        val real = listOf(
            "서울세계불꽃축제 관련 고객 안전을 위해 1호선 노량진역 축구장 및 야구장 인근 9번 출구 이용이 제한될 수 있으니 참고하시기 바랍니다. [한국철도공사]",
            "물놀이 등 수상 안전사고 예방을 위해 준비운동 구명조끼 착용 위험지역 접근금지 음주입수 금지 등 안전수칙 준수 바랍니다.[행정안전부]",
            "[Web발신] [네이버] 인증번호[224287] 타인에게 절대 알려주지 마세요.",
            "화성시 팔탄면에서 실종된 박성주씨(남,80세)를 찾습니다. [경기남부경찰청]",
            "14:20 훈련경보 해제. 국민 여러분께서는 일상으로 돌아가시기 바람. [행정안전부]"
        )
        assertTrue("기계가 보낸 문자로 업종을 추측하면 안 된다", g(received = real).isEmpty())
    }

    @Test
    fun `한전 안내 문자의 전기를 업종으로 착각하지 않는다`() {
        val r = g(received = listOf("정전 예고 안내: 전기 설비 점검으로 09:00~11:00 정전 예정입니다. [한국전력공사]"))
        assertTrue(r.isEmpty())
    }

    @Test
    fun `광고 문자는 세지 않는다`() {
        val r = g(
            received = listOf(
                "(광고) 에어컨 청소 반값 이벤트! 실외기까지 무료거부 080-000-0000",
                "[광고] 포장이사 최저가 이삿짐 견적 무료 수신거부"
            )
        )
        assertTrue(r.isEmpty())
    }

    @Test
    fun `단서가 한두 개뿐이면 추측하지 않는다`() {
        // 손님이 지나가듯 한 번 말한 정도로는 업종을 단정하지 않는다
        assertTrue(g(received = listOf("혹시 곰팡이도 봐주시나요?")).isEmpty())
    }

    @Test
    fun `문자가 하나도 없어도 안전하다`() {
        assertTrue(g().isEmpty())
    }

    @Test
    fun `단어를 잔뜩 나열한 문자 한 통이 전체를 좌우하지 않는다`() {
        // 견적서처럼 품목을 나열한 문자 하나로 확정되면, 한 번의 우연이 업종을 정해버린다
        val one = g(sent = listOf("줄눈 에폭시 메지 백시멘트 탄성코트 전부 가능합니다"))
        val two = g(sent = listOf("줄눈 시공합니다", "줄눈 견적 드려요"))
        assertTrue("한 통의 힘은 제한된다", one.first().score <= two.first().score)
    }

    @Test
    fun `추천하는 업종 이름은 선택 화면의 이름과 같아야 한다`() {
        // 다르면 눌러도 화면에서 선택 표시가 안 뜬다 — 눈으로는 알아채기 어려운 사고
        val known = setOf(
            "줄눈", "실리콘·코킹", "도배", "장판·마루", "타일", "페인트·도색", "인테리어필름",
            "욕실리모델링", "방수·누수", "미장", "목공·몰딩", "샷시·중문", "커튼·블라인드",
            "바닥(에폭시·폴리싱)", "에어컨 설치·청소", "보일러 수리", "조명·전기", "수전·배관설비",
            "도어·잠금장치", "가구 설치·조립", "CCTV·인터폰", "방충망·방범창", "가전 설치",
            "입주청소", "거주·정기청소", "사업장청소", "곰팡이 제거", "새집증후군",
            "이사(가정·원룸)", "용달·운송", "철거·폐기", "광택·디테일링", "썬팅·필름", "차량정비", "세차"
        )
        val samples = listOf(
            "줄눈 시공", "도배 벽지", "타일 덧방", "에어컨 실외기 냉매", "보일러 분배기",
            "썬팅 틴팅", "손세차 스팀세차", "포장이사 이삿짐", "입주청소 준공청소", "도어락 잠금장치",
            "cctv 인터폰", "방충망 방범창", "몰딩 걸레받이", "샷시 중문", "커튼 블라인드",
            "폴리싱 하드너", "누전 차단기 콘센트", "변기 막힘 수전", "곰팡이 제거 피톤치드",
            "새집증후군 베이크아웃", "철거 폐기물", "광택 유리막", "엔진오일 타이어 교체",
            "미장 몰탈", "실리콘 코킹", "강마루 장판", "페인트 도색", "시트지 래핑", "젠다이 양변기",
            "우레탄방수 누수", "가구 조립 붙박이장", "벽걸이 tv 빌트인", "상가청소 사무실 청소",
            "정기청소 가사도우미", "용달 퀵 배송"
        )
        for (s in samples) {
            val msgs = listOf(TradeGuesser.Msg("01012345678", s), TradeGuesser.Msg("01012345678", s))
            for (guess in TradeGuesser.guess(msgs, emptyList())) {
                assertTrue("선택 화면에 없는 업종: ${guess.trade}", guess.trade in known)
            }
        }
    }
    // ── 보낸 사람 번호로 거르기 (2026-09-16 사장님) ─────────────

    @Test
    fun `8자리 대표번호에서 온 문자는 내용을 보지도 않는다`() {
        // 사장님: "0000-0000 번호가 8자리면 그냥 그 내용은 참고하지도 마. 그럼 일단 걸러지거든."
        val r = TradeGuesser.guess(
            emptyList(),
            listOf(
                TradeGuesser.Msg("15881588", "줄눈 에폭시 시공 문의 주세요"),
                TradeGuesser.Msg("16441644", "도배 벽지 상담 안내드립니다"),
                TradeGuesser.Msg("18990000", "타일 덧방 포세린 행사 중")
            )
        )
        assertTrue("대표번호 문자는 업종 근거가 될 수 없다", r.isEmpty())
    }

    @Test
    fun `짧은 번호도 거른다`() {
        val r = TradeGuesser.guess(
            emptyList(),
            listOf(
                TradeGuesser.Msg("1004", "줄눈 줄눈 줄눈"),
                TradeGuesser.Msg("114", "도배 도배 도배")
            )
        )
        assertTrue(r.isEmpty())
    }

    @Test
    fun `같은 내용이라도 개인 휴대폰에서 오면 센다`() {
        // 거르는 기준이 '내용'이 아니라 '누가 보냈나' 임을 못박는다
        val fromBiz = TradeGuesser.guess(
            emptyList(), listOf(TradeGuesser.Msg("15881588", "도배 벽지 실크벽지 견적요"))
        )
        val fromPerson = TradeGuesser.guess(
            emptyList(), listOf(TradeGuesser.Msg("010-1234-5678", "도배 벽지 실크벽지 견적요"))
        )
        assertTrue(fromBiz.isEmpty())
        assertEquals("도배", fromPerson.first().trade)
    }

    @Test
    fun `번호를 모르면 본문 검사만으로 판단한다`() {
        // MMS 등 발신번호가 비는 경우 — 무작정 버리면 멀쩡한 단서까지 날아간다
        val r = TradeGuesser.guess(
            listOf(TradeGuesser.Msg(null, "화장실 줄눈 시공 견적 보내드립니다")), emptyList()
        )
        assertEquals("줄눈", r.first().trade)
    }

    @Test
    fun `Web발신이 붙은 문자는 개인번호에서 와도 안 본다`() {
        // 사장님: "[web 발신] 이런 키워드가 담긴 문자도 보지도마."
        val r = TradeGuesser.guess(
            emptyList(),
            listOf(TradeGuesser.Msg("01012345678", "[Web발신] 타일 포세린 덧방 특가 안내"))
        )
        assertTrue(r.isEmpty())
    }
    @Test
    fun `실측에서 새던 구멍 세 개를 막는다`() {
        // 2026-09-16 사장님 폰 105통을 실제로 돌려보고 찾은 누수 경로들.
        // 지금은 업종 단어가 없어 무해했지만, 남의 폰에서는 여기로 엉뚱한 추측이 샌다.
        val leaks = listOf(
            // ① 재난문자 채널 — 발신자가 번호가 아니라 이름이라 숫자 규칙에 안 걸렸다
            TradeGuesser.Msg("#CMAS#Severe", "평택시흥고속도로 2차로 긴급 교통통제 중, 우회도로 이용바랍니다.[제이서해안고속도로(주)]"),
            // ② "인증번호" 가 아니라 "인증 코드" 로 온 국제발신
            TradeGuesser.Msg("00614664531", "[국제발신] Play Console 인증 코드: 569134")
        )
        assertTrue(TradeGuesser.guess(emptyList(), leaks).isEmpty())
    }

    @Test
    fun `재난문자 채널은 업종 단어가 들어 있어도 안 본다`() {
        val r = TradeGuesser.guess(
            emptyList(),
            listOf(TradeGuesser.Msg("#CMAS#Severe", "정전으로 조명·배선 복구 중. 누전 주의 [○○시청]"))
        )
        assertTrue(r.isEmpty())
    }
}
