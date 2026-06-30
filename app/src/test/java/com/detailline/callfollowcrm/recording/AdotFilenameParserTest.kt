package com.detailline.callfollowcrm.recording

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * AdotFilenameParser 단위테스트 — 사장님 폰(Recordings/TPhoneCallRecords)에서 실제로 뽑은 파일명들.
 *   핵심: 옛 파서는 "번호로 시작" 만 봐서 `상민이_010…` 처럼 **연락처 이름이 앞에 붙은 파일을 통째로 놓쳤음**.
 *   이 테스트가 그 회귀를 막는다.
 */
class AdotFilenameParserTest {

    private fun expectedMs(yyyymmddhhmmss: String): Long =
        SimpleDateFormat("yyyyMMddHHmmss", Locale.KOREA).parse(yyyymmddhhmmss)!!.time

    // ── A) 에이닷/T전화 — 이름 없는 (번호로 시작) ──
    @Test fun adot_bareNumber() {
        val p = AdotFilenameParser.parse("01023013219_20260512162559.m4a")
        assertNotNull(p)
        assertEquals("01023013219", p!!.phoneNumber)
        assertEquals(expectedMs("20260512162559"), p.recordedAt)
    }

    // ── A) 에이닷/T전화 — 연락처 이름 접두어 (옛 파서가 놓치던 케이스) ──
    @Test fun adot_namePrefix_korean() {
        val p = AdotFilenameParser.parse("상민이_01024227744_20260521205004.m4a")
        assertNotNull("이름 붙은 파일도 파싱돼야 함", p)
        assertEquals("01024227744", p!!.phoneNumber)
    }

    @Test fun adot_namePrefix_emoji() {
        val p = AdotFilenameParser.parse("💘사랑하는둘도셋도넷도없는와이프💘_01048052630_20260513101135.m4a")
        assertNotNull(p)
        assertEquals("01048052630", p!!.phoneNumber)
    }

    @Test fun adot_namePrefix_longBusinessName() {
        val p = AdotFilenameParser.parse("줄눈시공탄성코트의시작디테일라인_01080056674_20260515090523.m4a")
        assertNotNull(p)
        assertEquals("01080056674", p!!.phoneNumber)
        assertEquals(expectedMs("20260515090523"), p.recordedAt)
    }

    // ── 다양한 번호 길이 ──
    @Test fun adot_landline10() {
        val p = AdotFilenameParser.parse("화성도시공사동부공원관리사무소_0312671286_20260512160452.m4a")
        assertNotNull(p)
        assertEquals("0312671286", p!!.phoneNumber)
    }

    @Test fun adot_safeNumber0507() {
        val p = AdotFilenameParser.parse("회선생낭만포차_050713457734_20260520201012.m4a")
        assertNotNull(p)
        assertEquals("050713457734", p!!.phoneNumber)
    }

    @Test fun adot_eightDigitBusiness() {
        val p = AdotFilenameParser.parse("16009854_20260514142750.m4a")
        assertNotNull(p)
        assertEquals("16009854", p!!.phoneNumber)
    }

    @Test fun adot_070() {
        val p = AdotFilenameParser.parse("07044377153_20260520094937.m4a")
        assertNotNull(p)
        assertEquals("07044377153", p!!.phoneNumber)
    }

    // ── .txt (Download/A.phone 통화내용) 도 같은 규칙 ──
    @Test fun adot_txtExtension() {
        val p = AdotFilenameParser.parse("상민이_01024227744_20260521205004.txt")
        assertNotNull(p)
        assertEquals("01024227744", p!!.phoneNumber)
    }

    // ── B) 삼성 통화녹음 (6+6 날짜) ──
    @Test fun samsung_basic() {
        val p = AdotFilenameParser.parse("통화 녹음 01025918978_260424_190911.m4a")
        assertNotNull(p)
        assertEquals("01025918978", p!!.phoneNumber)
        assertEquals(expectedMs("20260424190911"), p.recordedAt)
    }

    // ── 거부돼야 하는 것들 (통화녹음 아님) ──
    @Test fun reject_music() {
        assertNull(AdotFilenameParser.parse("좋은날_아이유.mp3"))
    }

    @Test fun reject_slackNotification() {
        assertNull(AdotFilenameParser.parse("Slack - Incoming call.mp3"))
    }

    @Test fun reject_noTimestamp() {
        assertNull(AdotFilenameParser.parse("01024227744.m4a"))
    }

    @Test fun reject_wrongExtension() {
        assertNull(AdotFilenameParser.parse("상민이_01024227744_20260521205004.jpg"))
    }

    @Test fun reject_garbage() {
        assertNull(AdotFilenameParser.parse("randomfile.m4a"))
    }

    // ── full path 도 파일명만 떼서 파싱 ──
    @Test fun handlesFullPath() {
        val p = AdotFilenameParser.parse("/storage/emulated/0/Recordings/TPhoneCallRecords/상민이_01024227744_20260521205004.m4a")
        assertNotNull(p)
        assertEquals("01024227744", p!!.phoneNumber)
    }
}
