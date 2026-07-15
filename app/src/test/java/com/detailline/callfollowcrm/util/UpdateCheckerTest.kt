package com.detailline.callfollowcrm.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 새 버전 배너 판단 검증.
 *
 * 사장님/테스터 신고(2026-07-15): "0.2.1000 에서 그대로 업데이트 하라고 하는데 다 된거 같은데 위에 계속 뜨네요"
 *   → 배너를 믿고 또 받고, 깔아도 또 뜨고 = 무한 재다운로드(다운로드 폴더에 shigongmagne-20.apk 까지).
 *   원인: prefs 에 저장된 available=true 가 업데이트 후에도 남고, 재체크는 10분 throttle.
 *   규칙: 서버가 최신 versionCode 를 알려줬으면 캐시 무시하고 그걸로 직접 비교.
 */
class UpdateCheckerTest {

    @Test fun `내 버전이 최신과 같으면 - 캐시가 true 여도 배너 안 뜬다`() {
        // 바로 이 버그: 1000 을 깐 직후인데 낡은 캐시(true)로 "0.2.1000 → 0.2.1000" 이 뜨던 것.
        assertFalse(UpdateChecker.shouldShowBanner(latestCode = 1000, myCode = 1000, cachedAvailable = true))
    }

    @Test fun `내 버전이 최신보다 높아도 - 배너 안 뜬다`() {
        // 사장님 데스크탑 빌드처럼 서버보다 앞선 버전을 깐 경우.
        assertFalse(UpdateChecker.shouldShowBanner(latestCode = 1000, myCode = 1001, cachedAvailable = true))
    }

    @Test fun `내 버전이 낮으면 - 배너 뜬다`() {
        assertTrue(UpdateChecker.shouldShowBanner(latestCode = 1000, myCode = 942, cachedAvailable = false))
    }

    @Test fun `내 버전이 낮으면 - 캐시가 false 여도 최신코드 우선`() {
        // 캐시는 낡을 수 있으므로 최신코드가 있으면 항상 그게 진실.
        assertTrue(UpdateChecker.shouldShowBanner(latestCode = 1001, myCode = 1000, cachedAvailable = false))
    }

    @Test fun `최신코드를 모르면 - 캐시(mtime 판단)로 폴백`() {
        assertTrue(UpdateChecker.shouldShowBanner(latestCode = 0, myCode = 1000, cachedAvailable = true))
        assertFalse(UpdateChecker.shouldShowBanner(latestCode = 0, myCode = 1000, cachedAvailable = false))
    }
}
