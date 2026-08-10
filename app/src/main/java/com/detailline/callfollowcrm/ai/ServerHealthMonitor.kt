package com.detailline.callfollowcrm.ai

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 맥미니 자체 서버 (공개 도메인 api.si0in.kr) 살아있음 모니터링.
 *
 * 30초마다 GET /health 호출 (PhaseOneApiRepository.warmup 활용).
 * HomeScreen 상단의 작은 ● indicator 가 이 상태를 구독.
 *
 * 사장님 의도 = "서버가 살아있는지 죽어있는지 사장님만 알아볼 수 있게 라도".
 * 폴링 주기 30초 = 배터리 부담 무시 가능 수준.
 */
class ServerHealthMonitor(
    private val phaseOneApiRepository: PhaseOneApiRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _alive = MutableStateFlow<Boolean?>(null)
    /** null = 확인 중(회색) / true = 살아있음(초록) / false = 연결 끊김 확정(빨강). */
    val alive: StateFlow<Boolean?> = _alive.asStateFlow()

    private val _lastOkAtMs = MutableStateFlow<Long?>(null)
    val lastOkAtMs: StateFlow<Long?> = _lastOkAtMs.asStateFlow()

    // 빨강(실패 확정)은 연속 2회 실패부터 — 앱 켜자마자 네트워크가 아직 안 올라와 한 번 삐끗한 걸
    //   '서버 죽음'으로 오인시키지 않기 위함. 그 전엔 회색(확인 중)/직전 초록 유지. (2026-06-22 사장님 UX)
    @Volatile private var consecutiveFails = 0

    /**
     * @param isForeground 앱이 화면에 보이는 동안만 ping. ⚡ 홈 상단 상태점(●) 하나를 위한 30초 GET /health 를
     *   백그라운드/화면 꺼짐엔 안 보내 배터리·라디오 낭비 제거. 복귀하면 다음 주기(≤30초)에 다시 확인. (2026-08-11 성능감사 rank4)
     */
    fun start(isForeground: () -> Boolean = { true }) {
        scope.launch {
            while (true) {
                if (isForeground()) applyResult(phaseOneApiRepository.warmup().isSuccess)
                // 첫 실패(아직 빨강 아님) 직후엔 2초 뒤 빨리 재확인 — 켜자마자 네트워크 준비 전 삐끗을 흡수.
                //   그 외(성공/빨강 확정)엔 30초 주기.
                delay(if (consecutiveFails == 1) 2_000L else 30_000L)
            }
        }
    }

    /**
     * 강제 health check — pull-to-refresh 등 사용자 트리거 용.
     *   30초 polling 안 기다리고 즉시 ping.
     */
    fun refresh() {
        scope.launch { applyResult(phaseOneApiRepository.warmup().isSuccess) }
    }

    /**
     * health 결과 반영.
     *   성공 = 즉시 초록 + 연속실패 리셋.
     *   실패 = 연속 2회부터 빨강 확정. 1회뿐이면 직전 상태(첫 켜짐=회색 / 직전=초록) 유지 → 일시적 끊김으로 빨강 안 띄움.
     */
    private fun applyResult(ok: Boolean) {
        if (ok) {
            consecutiveFails = 0
            _alive.value = true
            _lastOkAtMs.value = System.currentTimeMillis()
        } else {
            consecutiveFails++
            if (consecutiveFails >= 2) _alive.value = false
        }
    }
}
