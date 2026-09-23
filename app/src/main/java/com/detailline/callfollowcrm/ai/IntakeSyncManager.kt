package com.detailline.callfollowcrm.ai

import android.content.Context
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.service.NotificationHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * 시공접수서 제출 동기화 (2026-06-03) — GET /api/quote/submissions 폴링.
 *   고객이 링크로 주소·시공일을 제출하면 → 그 고객 카드에 자동 반영(주소·시공일·메모) + 알림.
 *   이미 임포트한 token 은 prefs 로 추적해 중복 알림 방지. 사업자 번호 미설정이면 skip.
 */
class IntakeSyncManager(private val container: AppContainer) {

    suspend fun sync(context: Context) {
        val prefs = container.preferences
        val devicePhone = prefs.bizPhone
        if (devicePhone.isBlank()) return // 사업자정보 미설정 — 폴링 키(devicePhone) 없음
        val since = prefs.intakeSyncSinceMs
        val list = container.intakeFormRepository.submissions(devicePhone, since).getOrNull() ?: return
        if (list.isEmpty()) return

        // 🔴 첫 실행(재설치 포함) — 기준선만 세우고 이번 판은 가져오지 않는다. (2026-09-14 사장님 신고)
        //   since 기본값이 0 이라 "태초부터" 를 달라고 하게 되고, 서버에 쌓여 있던 옛 접수서가
        //   전부 '새 제출'로 들어와 고객이 새로 생기고 알림이 쏟아졌다("접수서가 엄청 쌓이네").
        //
        // 🔴🔴 그런데 **복원한 폰**에서 이게 접수서를 통째로 삼켰다. (2026-09-23 사장님 신고 — 4027)
        //   09-16 고객 제출 → 09-18 앱 삭제 → 복원(백업은 09-17 이전 것이라 그 건이 없음)
        //   → 복원 뒤 첫 폴링이 그 건까지 '이미 본 것'으로 찍어버려 **양쪽 어디에도 없게** 됐다.
        //   옛 주석의 "복원으로 들어온다" 는 가정이 틀렸다 — 복원은 **하루 한 번 뜨는 백업**이라
        //   백업 이후에 들어온 제출은 복원에도 없다.
        //
        //   → 첫 실행을 두 경우로 가른다:
        //     · 앱에 접수서 기록이 하나도 없다 = **진짜 새 설치** → 예전처럼 기준선만 (쏟아짐 방지 유지)
        //     · 기록이 있다 = **복원된 폰** → 앱에 없는 token 만 골라 정상 임포트, 나머지는 본 것으로.
        if (since <= 0L) {
            val known = runCatching { container.intakeEventRepository.allTokens() }.getOrDefault(emptySet())
            val seen = prefs.intakeImportedTokens.toMutableSet()
            var floor = System.currentTimeMillis()
            var gap = 0
            for (s in list) {
                s.submittedAtMs?.let { if (it > floor) floor = it }
                // 복원된 폰(known 이 비어있지 않다)에서 **앱에 없는 건**은 넘기지 않는다 — 그게 이번 사고다.
                if (known.isNotEmpty() && s.token !in known) { gap++; continue }
                seen.add(s.token)
            }
            prefs.intakeImportedTokens = seen
            if (gap > 0) {
                // 빠진 게 있으면 기준선을 올리지 않는다 — 바로 아래 평소 경로가 이번 판에서 가져간다.
                prefs.intakeSyncSinceMs = 1L
                println("[intake] 복원된 폰 — 앱에 없는 접수서 ${gap}건 발견, 이번 판에서 가져온다")
            } else {
                prefs.intakeSyncSinceMs = floor
                println("[intake] 첫 실행 — 기존 제출 ${list.size}건을 '이미 본 것'으로 기준선만 세움")
                return
            }
        }

        val imported = prefs.intakeImportedTokens.toMutableSet()
        var maxSubmitted = since
        var failedFloor = Long.MAX_VALUE   // 처리 '실패'한 건의 최소 제출시각 — 마커가 이 앞을 못 넘게(다음 폴링 재시도=유실 방지). (2026-07-30 버그감사)
        var changed = false
        for (s in list) {
            val submitted = s.submittedAtMs ?: continue
            if (submitted > maxSubmitted) maxSubmitted = submitted
            if (s.token in imported || s.customerPhone.isBlank()) continue

            // 제출 1건 처리 — 한 건이 예외나도 다른 건/폴링이 죽지 않게 격리(2026-06-21: 일정 자동등록 누락 디버깅).
            //   성공해야만 imported 에 넣어 다음 폴링서 재시도되게(반쪽 처리 방지).
            val processed = runCatching {
                val c = container.customerRepository.upsertByPhone(phoneNumber = s.customerPhone)
                val fullAddr = listOfNotNull(s.address, s.dong).joinToString(" ").trim()
                // 주소는 '기존 로컬 값이 비었을 때만' 채운다(총액·메모·계약금과 동일 가드). 예전엔 무조건 덮어써
                //   사장님이 통화로 확정해둔 주소를 접수서 제출이 조용히 갈아치웠음(무음 소실). (2026-08-11 데이터안전 감사 rank6)
                if (fullAddr.isNotBlank() && c.address.isNullOrBlank()) container.customerRepository.updateAddress(c.id, fullAddr)
                val workMs = workMsOf(s)
                android.util.Log.i(
                    "IntakeSync",
                    "tok=${s.token} cid=${c.id} y=${s.workYear} mo=${s.workMonth} d=${s.workDay} conf=${s.confirmedDateIso} => workMs=$workMs total=${s.total}"
                )
                // 시공예약일도 '기존 로컬 값이 없을 때만' 채운다 — 사장님이 통화로 잡아둔 시공일을 접수서가 덮지 않게. (2026-08-11 데이터안전 감사 rank6)
                if (c.scheduledWorkDate == null) workMs?.let {
                    container.customerRepository.updateScheduledWorkDate(c.id, it)
                    // 접수서로 잡힌 일정도 jobs 에 들어가야 달력에 뜬다. (2026-09-15 사장님)
                    runCatching {
                        container.jobRepository.syncRepresentativeFromCustomer(
                            c.id, System.currentTimeMillis()
                        )
                    }
                }
                android.util.Log.i(
                    "IntakeSync",
                    "after setDate cid=${c.id} readBack=${container.customerRepository.findById(c.id)?.scheduledWorkDate}"
                )
                if (!s.memo.isNullOrBlank() && c.memo.isNullOrBlank()) {
                    container.customerRepository.updateMemo(c.id, "접수: ${s.memo}")
                }
                // 견적금액도 고객 카드 총금액에 반영 (주소·시공일과 동일). s.total = 만원 → totalAmount = 원(×10,000).
                //   단, import 시점에 이미 수동 입력해둔 총금액이 있으면 존중(수동 우선). (2026-06-14 사장님)
                val totalWon = s.total.toLong() * 10_000L
                if (s.total > 0 && (c.totalAmount == null || c.totalAmount == 0L)) {
                    container.customerRepository.updateTotalAmount(c.id, totalWon)
                }
                // 계약금도 고객 카드에 반영. ratio → 총액 × %/100. 수동 입력분 존중. (2026-06-14 사장님)
                //   fixed → depositValue 를 그대로(원 단위). 2026-07-04(c16bc39)부터 서버가 fixed 계약금을 '원'으로
                //   저장/에코(_deposit_resolve_krw passthrough) → 여기서 ×10,000 하면 이중곱(10만원→10억)이 됨.
                //   (2026-07-09 사장님 "계약금 100,000만원" 버그 fix. send 만 바뀌고 이 read-back 이 안 바뀐 비대칭이 원인.)
                val depositWon = when (s.depositMode) {
                    "ratio" -> if (s.depositValue > 0 && totalWon > 0) totalWon * s.depositValue / 100 else 0L
                    "fixed" -> if (s.depositValue > 0) s.depositValue.toLong() else 0L
                    else -> 0L
                }
                if (depositWon > 0 && (c.depositAmount == null || c.depositAmount == 0L)) {
                    container.customerRepository.updateDepositAmount(c.id, depositWon)
                }

                val nm = s.customerName.ifBlank { s.customerPhone }
                val dateLabel = workMs?.let {
                    SimpleDateFormat("M/d(E)", Locale.KOREA).format(java.util.Date(it))
                }

                // 채팅 타임라인 카드용 이벤트 기록 — token unique IGNORE → 중복 방지.
                runCatching {
                    val suffix = s.customerPhone.filter { it.isDigit() }.takeLast(8)
                    container.intakeEventRepository.record(
                        com.detailline.callfollowcrm.data.local.entity.IntakeEventEntity(
                            phoneSuffix = suffix,
                            token = s.token,
                            customerName = nm,
                            submittedAtMs = submitted,
                            address = fullAddr.ifBlank { null },
                            dateLabel = dateLabel,
                            totalManwon = s.total.takeIf { it > 0 },
                            itemsText = s.estimateItems.joinToString(", ").takeIf { it.isNotBlank() },
                            customerMemo = s.memo?.takeIf { it.isNotBlank() },
                            createdAt = System.currentTimeMillis()
                        )
                    )
                }

                NotificationHelper.showIntakeSubmitted(
                    context, s.token, s.customerPhone, nm,
                    address = fullAddr.ifBlank { "주소 미입력" },
                    dateLabel = dateLabel, totalManwon = s.total
                )
            }.onFailure {
                android.util.Log.e("IntakeSync", "process token=${s.token} FAILED", it)
            }.isSuccess

            if (processed) {
                imported.add(s.token)
                changed = true
            } else {
                // 처리 예외(DB 오류 등) — 이 건을 잃지 않게 마커 전진을 이 시각 앞에서 멈춘다.
                if (submitted < failedFloor) failedFloor = submitted
            }
        }
        // 실패 건이 있으면 그 '직전'까지만 마커 전진 → 실패한 접수서(주소·시공일·계약금)가 다음 폴링서 다시 잡혀 재시도.
        //   성공/처리불가(빈 번호)만이면 정상 전진. 기존엔 실패해도 maxSubmitted 로 전진해 영구 유실됐음. (2026-07-30 버그감사)
        val newSince = if (failedFloor == Long.MAX_VALUE) maxSubmitted else minOf(maxSubmitted, failedFloor - 1)
        if (changed || newSince != since) {
            prefs.intakeImportedTokens = imported
            prefs.intakeSyncSinceMs = newSince.coerceAtLeast(since)
        }
    }

    /** confirmedDate(yyyy-MM-dd) 우선, 없으면 workYear/Month/Day → KST 0시 epoch. */
    private fun workMsOf(s: IntakeFormRepository.QuoteSubmission): Long? {
        s.confirmedDateIso?.let { iso ->
            runCatching {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).apply {
                    timeZone = TimeZone.getTimeZone("Asia/Seoul")
                }
                sdf.parse(iso)?.let { return it.time }
            }
        }
        if (s.workYear > 0 && s.workMonth in 1..12 && s.workDay in 1..31) {
            return Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul")).apply {
                set(s.workYear, s.workMonth - 1, s.workDay, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }
        return null
    }
}
