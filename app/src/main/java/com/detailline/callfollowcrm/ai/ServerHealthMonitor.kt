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
    /** null = 첫 체크 전 / true = 살아있음 / false = 죽음. */
    val alive: StateFlow<Boolean?> = _alive.asStateFlow()

    private val _lastOkAtMs = MutableStateFlow<Long?>(null)
    val lastOkAtMs: StateFlow<Long?> = _lastOkAtMs.asStateFlow()

    fun start() {
        scope.launch {
            while (true) {
                val ok = phaseOneApiRepository.warmup().isSuccess
                _alive.value = ok
                if (ok) _lastOkAtMs.value = System.currentTimeMillis()
                delay(30_000)
            }
        }
    }

    /**
     * 강제 health check — pull-to-refresh 등 사용자 트리거 용.
     *   30초 polling 안 기다리고 즉시 ping.
     */
    fun refresh() {
        scope.launch {
            val ok = phaseOneApiRepository.warmup().isSuccess
            _alive.value = ok
            if (ok) _lastOkAtMs.value = System.currentTimeMillis()
        }
    }
}
