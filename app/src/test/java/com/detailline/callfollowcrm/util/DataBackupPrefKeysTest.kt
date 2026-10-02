package com.detailline.callfollowcrm.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 💾 **되살릴 때 가져오면 안 되는 설정칸** — 시험이 없었다. (2026-10-03)
 *
 *   백업은 설정칸(SharedPreferences)도 담는다. 협업 연결·스팸 목록·자동문자 문구·업체정보 같은 건
 *   **되살려야** 하고, **이 폰에서만 뜻이 있는 것**은 되살리면 안 된다.
 *
 *   ⚠️ 여기서 틀려서 **실제로 두 번 사고가 났다**:
 *     · 2026-09-14 — 구글 캘린더 「연결됨」 표시가 되살아나 앱은 연결됐다는데 **인증은 없었다.**
 *       [동기화]가 계속 실패했고, **표시가 거짓말을 하니 원인을 못 찾았다**
 *     · 2026-09-18 — 「통화기록 첫 가져오기 끝냄」 표시가 되살아나 **새 폰에서 7일치를 영영 안 가져왔다.**
 *       9/15~9/18 통화·요약이 하나도 안 쌓였다 (사장님 "통화요약됐던 것도 다 날아가는 듯")
 *
 *   그래서 이 셈은 **글자 하나 바뀌면 같은 사고가 다시 난다.**
 */
class DataBackupPrefKeysTest {

    // ── 되살리면 안 되는 것 ────────────────────────────────────────

    @Test
    fun `구글 캘린더 연결은 못 옮긴다 — 폰과 구글계정과 앱서명에 묶여 있다`() {
        assertTrue(DataBackup.skipPrefKey("google_calendar_id"))
        assertTrue(DataBackup.skipPrefKey("google_calendar_connected"))
        assertTrue(DataBackup.skipPrefKey("calendar_connected"))
        // 대문자로 적혀 있어도 같게 본다
        assertTrue(DataBackup.skipPrefKey("GOOGLE_CALENDAR_ID"))
    }

    @Test
    fun `통화기록 첫 가져오기 표시는 못 옮긴다 — 2026-09-18 사고`() {
        // 🔴 이게 되살아나면 **새 폰이 7일치를 건너뛴다**
        assertTrue(DataBackup.skipPrefKey("initial_call_log_imported"))
        assertTrue(DataBackup.skipPrefKey("INITIAL_CALL_LOG_IMPORTED"))
    }

    @Test
    fun `기기에 묶인 것들 — 토큰·푸시·폴더 주소`() {
        assertTrue(DataBackup.skipPrefKey("session_token"))
        assertTrue(DataBackup.skipPrefKey("fcm_token"))
        assertTrue(DataBackup.skipPrefKey("gcm_id"))
        assertTrue(DataBackup.skipPrefKey("recording_folder_uri"))
    }

    // ── 되살려야 하는 것 ──────────────────────────────────────────

    @Test
    fun `사장님이 적어둔 것은 되살린다`() {
        // 🔴 이게 막히면 재설치했을 때 **자동문자 문구·업체정보·스팸목록이 다 사라진다**
        assertFalse(DataBackup.skipPrefKey("auto_reply_text"))
        assertFalse(DataBackup.skipPrefKey("biz_name"))
        assertFalse(DataBackup.skipPrefKey("biz_phone"))
        assertFalse(DataBackup.skipPrefKey("spam_prefixes"))
        assertFalse(DataBackup.skipPrefKey("collab_assignments"))
        assertFalse(DataBackup.skipPrefKey("monthly_goal_manwon"))
        assertFalse(DataBackup.skipPrefKey("start_addr"))
    }

    @Test
    fun `달력이라는 말이 들어가도 구글 연결이 아니면 되살린다`() {
        // 「calendar_connected」 와 「google_calendar…」 만 막는다
        assertFalse(DataBackup.skipPrefKey("calendar_first_day"))
        assertFalse(DataBackup.skipPrefKey("show_calendar_badge"))
    }

    @Test
    fun `빈 이름은 막지 않는다`() {
        assertFalse(DataBackup.skipPrefKey(""))
    }

    // ── 고객 목록 CSV — 사장님이 엑셀로 여는 그 파일 ──────────────────

    @Test
    fun `보통 글자는 따옴표로 감싸기만 한다`() {
        assertEquals("\"홍길동\"", DataBackup.csv("홍길동"))
    }

    @Test
    fun `칸 안의 따옴표는 두 번 적는다 — 안 그러면 칸이 밀린다`() {
        assertEquals("\"그는 \"\"대표\"\" 라고 했다\"", DataBackup.csv("그는 \"대표\" 라고 했다"))
    }

    @Test
    fun `쉼표가 들어가도 한 칸으로 남는다`() {
        // 주소에 쉼표가 흔하다 — 감싸지 않으면 엑셀에서 **칸이 쪼개진다**
        assertEquals("\"서울 강서구, 마곡동\"", DataBackup.csv("서울 강서구, 마곡동"))
    }

    @Test
    fun `줄바꿈이 있어도 감싸진 채로 나간다`() {
        assertEquals("\"첫줄\n둘째줄\"", DataBackup.csv("첫줄\n둘째줄"))
    }

    @Test
    fun `빈 칸도 빈 칸으로 나간다`() {
        assertEquals("\"\"", DataBackup.csv(""))
    }
}
