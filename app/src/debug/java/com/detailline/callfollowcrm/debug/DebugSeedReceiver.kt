package com.detailline.callfollowcrm.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.detailline.callfollowcrm.CallFollowCrmApplication
import com.detailline.callfollowcrm.data.local.entity.CallSummaryEntity
import com.detailline.callfollowcrm.data.repository.SmsRepository
import com.detailline.callfollowcrm.domain.model.CallType
import com.detailline.callfollowcrm.domain.model.HandledStatus
import com.detailline.callfollowcrm.domain.model.MessageStatus
import com.detailline.callfollowcrm.util.DateTimeUtils
import com.detailline.callfollowcrm.util.PhoneKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 디버그 전용 — Play 스토어 스크린샷용 **마케팅 더미데이터 시드.** release 엔 포함 안 됨(src/debug).
 *   사용: adb shell am broadcast -a com.detailline.callfollowcrm.DEBUG_SEED -n com.detailline.callfollowcrm/.debug.DebugSeedReceiver
 *   ⚠️ 업종 중립(1인 시공인 범용) — "줄눈" 등 특정 업종 단어 안 씀. 현장/시공/견적/일정 일반어.
 *   멱등: 같은 번호로 upsert 하므로 여러 번 돌려도 고객이 안 늘어난다.
 */
class DebugSeedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SEED) return
        val app = context.applicationContext as? CallFollowCrmApplication ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                seedCustomers(app)
                seedScheduleFill(app)
                syncJobs(app)
                seedConversations(app)
                Log.i(TAG, "demo seed done")
            } catch (t: Throwable) {
                Log.e(TAG, "seed failed", t)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun seedCustomers(app: CallFollowCrmApplication) {
        val repo = app.container.customerRepository
        val day = 24L * 60 * 60 * 1000
        val today = DateTimeUtils.startOfDay(System.currentTimeMillis())
        val now = System.currentTimeMillis()

        // 1) 오늘 시공 — 진행 중 (홈 '오늘 할 일' 띠)
        repo.upsertByPhone("010-2456-7801", "김민수").let { c ->
            repo.updateMemo(c.id, "현관·주방 시공. 주차는 지하 2층.")
            repo.updateAddress(c.id, "경기 성남시 분당구 정자일로 95 101동 1203호")
            repo.updateScheduledWorkDate(c.id, today)
            repo.updateScheduledWorkTiming(c.id, 14 * 60, 2)   // 2일 시공(오늘~내일) — 날짜별 완료 확인용
            repo.updateTotalAmount(c.id, 1_350_000)
            repo.updateDepositAmount(c.id, 400_000); repo.updateDepositPaidAt(c.id, now - 3 * day)
        }
        // 2) 오늘 A/S (홈 '오늘 일정' A/S 띠)
        repo.upsertByPhone("010-3677-2410", "이서연").let { c ->
            repo.updateAddress(c.id, "서울 서초구 반포대로 45 112동 2504호")
            repo.updateAsSchedule(c.id, today, 1)
            repo.updateMemo(c.id, "마감 부위 재점검 요청.")
        }
        // 3) 시공 대기 (다음 주)
        repo.upsertByPhone("010-5120-8834", "박지훈").let { c ->
            repo.updateAddress(c.id, "경기 성남시 수정구 산성대로 300 202동 804호")
            repo.updateScheduledWorkDate(c.id, today + 4 * day)
            repo.updateScheduledWorkTiming(c.id, 10 * 60, 1)
            repo.updateTotalAmount(c.id, 850_000)
            repo.updateDepositAmount(c.id, 200_000); repo.updateDepositPaidAt(c.id, now - 1 * day)
        }
        // 4) 완료 + 완납
        repo.upsertByPhone("010-4892-1157", "최유나").let { c ->
            repo.updateAddress(c.id, "경기 화성시 동탄대로 123 105동 1502호")
            repo.updateScheduledWorkDate(c.id, today - 5 * day)
            repo.updateTotalAmount(c.id, 1_200_000)
            repo.updateDepositAmount(c.id, 400_000); repo.updateDepositPaidAt(c.id, now - 10 * day)
            repo.updateBalanceAmount(c.id, 800_000); repo.updateBalancePaidAt(c.id, now - 4 * day)
            repo.updateWorkCompletedAt(c.id, now - 4 * day)
        }
        // 5) 완료, 잔금 미수
        repo.upsertByPhone("010-7314-6620", "정우성").let { c ->
            repo.updateAddress(c.id, "경기 하남시 미사강변대로 200 303동 1104호")
            repo.updateScheduledWorkDate(c.id, today - 2 * day)
            repo.updateTotalAmount(c.id, 950_000)
            repo.updateDepositAmount(c.id, 300_000); repo.updateDepositPaidAt(c.id, now - 7 * day)
            repo.updateBalanceAmount(c.id, 650_000)   // 미수
            repo.updateWorkCompletedAt(c.id, now - 2 * day)
        }
        // 6) 보류 손님
        repo.upsertByPhone("010-6205-9943", "강도윤").let { c ->
            repo.updateAddress(c.id, "서울 강동구 천호대로 1000 1동 503호")
            repo.updateTotalAmount(c.id, 1_100_000)
            repo.updateDepositAmount(c.id, 300_000); repo.updateDepositPaidAt(c.id, now - 6 * day)
            repo.setOnHold(c.id, now - 5 * day, "고객이 일정 다시 잡기로 — 다시 연락 예정")
        }
    }

    private data class SchedSeed(
        val phone: String, val name: String, val addr: String,
        val dateMs: Long, val min: Int, val won: Long
    )

    /**
     * 📅 일정 탭을 가득 채우는 랜덤 주소 현장 + 오늘 현장 보강(홈 띠·위젯 풍성).
     *   지난 날짜 = 완납(현금흐름·순이익 채움) · 오늘/미래 = 계약금만(시공 대기). 멱등(고정 번호).
     */
    private suspend fun seedScheduleFill(app: CallFollowCrmApplication) {
        val repo = app.container.customerRepository
        val day = 24L * 60 * 60 * 1000
        val now = System.currentTimeMillis()
        val monthStart = DateTimeUtils.startOfMonth(now)
        val today = DateTimeUtils.startOfDay(now)
        fun d(n: Int) = monthStart + (n - 1) * day

        val list = listOf(
            // 오늘 현장 보강 (홈 띠·위젯 풍성)
            SchedSeed("010-8100-0001", "윤도현", "서울 송파구 올림픽로 240 103동 1802호", today, 9 * 60, 780_000),
            SchedSeed("010-8100-0002", "한서우", "경기 성남시 분당구 돌마로 42 201동 905호", today, 11 * 60, 1_050_000),
            SchedSeed("010-8100-0003", "배준영", "인천 연수구 송도과학로 70 6단지 1403호", today, 15 * 60, 640_000),
            // 이달 곳곳 — 랜덤 주소
            SchedSeed("010-8001-3001", "김영호", "경기 수원시 영통구 광교중앙로 145 102동 1203호", d(1), 10 * 60, 900_000),
            SchedSeed("010-8002-3002", "이준호", "경기 용인시 수지구 풍덕천로 88 203동 702호", d(2), 9 * 60, 1_200_000),
            SchedSeed("010-8003-3003", "박민재", "서울 강서구 공항대로 230 3동 801호", d(3), 13 * 60, 1_500_000),
            SchedSeed("010-8004-3004", "최수빈", "경기 고양시 일산동구 중앙로 1200 8단지 1102호", d(4), 10 * 60, 780_000),
            SchedSeed("010-8005-3005", "정하은", "인천 미추홀구 경인로 400 6단지 1402호", d(5), 11 * 60, 1_350_000),
            SchedSeed("010-8006-3006", "강태현", "경기 부천시 원미구 길주로 100 5동 1205호", d(6), 9 * 60, 1_100_000),
            SchedSeed("010-8007-3007", "윤서준", "서울 마포구 월드컵북로 400 2동 604호", d(7), 14 * 60, 960_000),
            SchedSeed("010-8008-3008", "임도윤", "경기 안양시 동안구 시민대로 230 110동 1801호", d(8), 10 * 60, 890_000),
            SchedSeed("010-8009-3009", "한지우", "경기 광명시 철산로 20 3동 704호", d(9), 13 * 60, 1_250_000),
            SchedSeed("010-8010-3010", "오현우", "경기 남양주시 다산중앙로 90 201동 905호", d(10), 9 * 60, 820_000),
            SchedSeed("010-8011-3011", "서예린", "경기 김포시 김포한강10로 133 4단지 1103호", d(11), 11 * 60, 970_000),
            SchedSeed("010-8013-3013", "문준서", "서울 강남구 테헤란로 500 A동 1504호", d(13), 10 * 60, 1_800_000),
            SchedSeed("010-8014-3014", "배하준", "경기 파주시 와동로 55 102동 603호", d(14), 13 * 60, 760_000),
            SchedSeed("010-8015-3015", "공지후", "서울 노원구 동일로 1400 7동 1802호", d(15), 9 * 60, 1_050_000),
            SchedSeed("010-8016-3016", "손시우", "경기 시흥시 배곧4로 60 205동 1402호", d(16), 11 * 60, 880_000),
            SchedSeed("010-8017-3017", "현예준", "서울 영등포구 여의대로 108 B동 2203호", d(17), 14 * 60, 1_600_000),
            SchedSeed("010-8018-3018", "전지안", "경기 의왕시 경수대로 300 1동 505호", d(18), 10 * 60, 990_000),
            SchedSeed("010-8019-3019", "조도현", "경기 군포시 산본로 90 110동 1203호", d(19), 13 * 60, 840_000),
            SchedSeed("010-8020-3020", "남우진", "경기 평택시 평택로 150 4단지 802호", d(20), 9 * 60, 1_120_000),
            SchedSeed("010-8021-3021", "유하람", "서울 성동구 왕십리로 300 2동 1604호", d(21), 11 * 60, 1_380_000),
            SchedSeed("010-8024-3024", "강라온", "경기 안산시 단원구 고잔로 44 103동 905호", d(24), 10 * 60, 910_000),
            SchedSeed("010-8026-3026", "송채운", "경기 성남시 수정구 산성대로 88 4동 1203호", d(26), 13 * 60, 1_070_000),
            SchedSeed("010-8028-3028", "차서진", "서울 구로구 디지털로 300 5동 1402호", d(28), 11 * 60, 930_000)
        )
        for (e in list) {
            val c = repo.upsertByPhone(e.phone, e.name)
            repo.updateAddress(c.id, e.addr)
            repo.updateScheduledWorkDate(c.id, e.dateMs)
            repo.updateScheduledWorkTiming(c.id, e.min, 1)
            repo.updateTotalAmount(c.id, e.won)
            val deposit = e.won / 3
            repo.updateDepositAmount(c.id, deposit)
            if (e.dateMs < today) {
                // 지난 현장 = 완납 (현금흐름·순이익 채움)
                repo.updateDepositPaidAt(c.id, e.dateMs)
                repo.updateBalanceAmount(c.id, e.won - deposit)
                repo.updateBalancePaidAt(c.id, e.dateMs)   // 자동 완료 처리됨
            } else {
                // 오늘/미래 = 계약금만 받음(시공 대기)
                repo.updateDepositPaidAt(c.id, e.dateMs - 2 * day)
            }
        }
    }

    /** 일정이 잡힌 모든 고객에게 **대표 건**을 만들어 캘린더(건 기반)에 뜨게 한다. */
    private suspend fun syncJobs(app: CallFollowCrmApplication) {
        // 📅 캘린더 금액 토글 ON — 현장당 금액이 바로 보이게. (2026-11-11 사장님)
        app.container.preferences.scheduleShowMoney = true
        val now = System.currentTimeMillis()
        app.container.customerRepository.allOnce().forEach { c ->
            if (c.scheduledWorkDate != null) {
                runCatching { app.container.jobRepository.syncRepresentativeFromCustomer(c.id, now) }
            }
        }
        // 🔑 오늘 현장 "현장 메모"(공동현관 비번 등) — 위젯이 보여주는 건 **건 메모**라 건에 넣는다.
        //   (고객 메모에만 넣으면 건 메모 ""가 가려 위젯 큰카드 모드로 가 메모가 안 보인다.)
        val memos = mapOf(
            "010-2456-7801" to "공동현관 #1204* · 주차 지하 2층",
            "010-8100-0001" to "공동현관 비밀번호 0909* · 엘리베이터 왼쪽 끝집",
            "010-8100-0002" to "공동현관 #5678 · 화물 엘리베이터 이용",
            "010-8100-0003" to "공동현관 2580* · 자재는 1층 경비실에 맡겨요"
        )
        memos.forEach { (phone, memo) ->
            val c = app.container.customerRepository.findByPhone(phone) ?: return@forEach
            app.container.customerRepository.updateMemo(c.id, memo)
            runCatching {
                app.container.jobRepository.representativeJobId(c.id, now)?.let { jid ->
                    app.container.jobRepository.updateMemo(jid, memo, now)
                }
            }
        }
    }

    /**
     * 💬 상담함(home)을 **진짜 대화**로 채운다 — 통화요약 카드 · 부재중 자동문자 배너 · "답장 안 함" 딱지 · 답한 대화.
     *   상담함 리스트는 sms_contacts_cache + call_records 를 읽는다(고객/일정과 별개). 이걸 안 채우면 빈 화면(막내).
     *   thread_buckets 엔 아무것도 안 넣음 = 미분류 = 상담함(CONSULT)으로 뜸. 업종 중립 문구. 멱등(고정 번호).
     */
    private suspend fun seedConversations(app: CallFollowCrmApplication) {
        val h = 60L * 60 * 1000
        val day = 24 * h
        val now = System.currentTimeMillis()
        fun sfx(phone: String) = PhoneKey.of(phone.filter { it.isDigit() })

        // 1) 통화요약 카드 2건 (통화-only 번호 → 리스트에 통화요약 한 줄이 붙는다)
        suspend fun seedCallSummary(
            phone: String, atMs: Long, durSec: Long,
            title: String, timeline: String, tags: String,
            category: String? = null, todos: String? = null, segments: String? = null
        ) {
            val cid = app.container.callRecordRepository.create(
                phoneNumber = phone, callType = CallType.INCOMING, duration = durSec,
                startedAt = atMs, endedAt = atMs + durSec * 1000,
                handledStatus = HandledStatus.SAVED
            )
            app.container.callSummaryRepository.upsert(
                CallSummaryEntity(
                    // ⚠️ 요약 DAO 는 번호를 LIKE '%'+suffix 로만 찾고 하이픈을 안 지운다(통화기록 DAO 와 달리).
                    //   그래서 요약 번호는 **숫자만**으로 저장해야 대화방 카드에 붙는다. (2026-11-12)
                    customerId = null, callRecordId = cid, phoneNumber = phone.filter { it.isDigit() },
                    recordedAt = atMs, title = title, summaryText = timeline,
                    tagsJson = tags, category = category, todosJson = todos,
                    transcriptSegmentsJson = segments, sourceType = "AI_SERVER",
                    createdAt = now, updatedAt = now
                )
            )
        }
        seedCallSummary(
            "010-4457-2231", now - 25 * 60 * 1000, 148,
            "욕실 리모델링 견적 상담",
            "0:00-0:22|손님|욕실 전체 리모델링 견적 문의\n" +
                "0:22-0:51|나|방문 실측 후 견적 안내드린다고 설명\n" +
                "0:51-1:30|손님|이번 주말 방문 가능한지 문의\n" +
                "1:30-2:28|나|토요일 오전 10시로 일정 조율",
            "[\"욕실\",\"리모델링\"]"
        )
        seedCallSummary(
            "010-6690-3317", now - 3 * h, 125,
            "주방 바닥 누수 보수 문의",
            "0:00-0:18|손님|주방 바닥 누수 보수 문의\n" +
                "0:18-0:40|나|부분 보수 가능하다고 안내\n" +
                "0:40-1:12|손님|비용과 소요 시간 질문\n" +
                "1:12-2:05|나|예상 비용·반나절 소요 안내 후 일정 협의",
            "[\"누수\",\"보수\"]"
        )
        // 프로토 ① 형식(설명형 제목 one_line + 깔끔 불릿 + 분류 + 꼭 챙겨야 할 일) 확인용 — summaryText 첫 줄 = one_line
        seedCallSummary(
            "010-5523-7788", now - 50 * 60 * 1000, 322,
            "화장실 보수 상담",
            "부분 보수 30만원 안내 · 경과 보고 전체 보수 결정하기로\n" +
                "들뜬 부분 **그라인더 작업** 후 비슷한 색으로 보수 추천\n" +
                "양쪽 화장실 **최소 30만원** 안내\n" +
                "**24일 입주** 예정이라 남편과 상의 후 일정 연락하기로\n" +
                "가장자리 제외 **보수 조건**·타일 상태 설명\n" +
                "이중 지출 우려로 **한 달 경과 후 전체 보수** 추천",
            "[]",
            category = "시공 상담",
            todos = "[\"고객에게 백화 제거제 구매 링크 전달하기\"]",
            segments = "[" +
                "{\"speaker\":\"손님\",\"text\":\"화장실 바닥 보수하고 싶은데 들뜬 데가 많아서요\"}," +
                "{\"speaker\":\"나\",\"text\":\"들뜬 부분만 그라인더로 긁어내고 비슷한 색으로 채우면 깔끔해져요\"}," +
                "{\"speaker\":\"손님\",\"text\":\"양쪽 화장실 다 하면 비용이 얼마나 나올까요?\"}," +
                "{\"speaker\":\"나\",\"text\":\"최소 30만원부터 보시면 되고, 상태 봐서 조금 더 나올 수 있어요\"}," +
                "{\"speaker\":\"손님\",\"text\":\"24일에 입주라 남편이랑 상의하고 다시 연락드릴게요\"}," +
                "{\"speaker\":\"나\",\"text\":\"네, 결정되시면 편하게 연락 주세요\"}" +
                "]"
        )

        // 2) 부재중 + 자동문자 배너 (sms 없음 → 배너 유지 · '답장 안 함' 부재중으로도 뜸)
        app.container.callRecordRepository.create(
            phoneNumber = "010-3382-9014", callType = CallType.MISSED, duration = 0,
            startedAt = now - 40 * 60 * 1000, endedAt = now - 40 * 60 * 1000,
            handledStatus = HandledStatus.UNHANDLED
        )
        app.container.messageHistoryRepository.recordAutoSend(
            "010-3382-9014", null, null,
            "안녕하세요, 지금 현장이라 전화를 못 받았어요. 메시지 남겨주시면 바로 연락드릴게요!",
            MessageStatus.AUTO_SENT
        )

        // 3) 문자 대화 — 답장 안 함(lastSent=false) · 답한 대화(lastSent=true) 섞어서
        suspend fun seedSms(
            phone: String, body: String, atMs: Long,
            lastSent: Boolean, answered: Boolean, firstMs: Long = atMs
        ) {
            app.container.smsContactCacheRepository.upsertOne(
                SmsRepository.SmsContact(
                    address = phone, normalizedSuffix = sfx(phone),
                    lastBody = body, lastDateMs = atMs,
                    lastSent = lastSent, hasOwnerReply = answered,
                    firstDateMsInScan = firstMs
                )
            )
        }
        // 답장 안 함 (신규 문의)
        seedSms("010-2240-5567", "화장실 타일 견적 받아볼 수 있을까요? 32평이에요.", now - 70 * 60 * 1000,
            lastSent = false, answered = false)
        seedSms("010-7788-1120", "현관·주방 바닥 시공 문의드려요. 평일 방문 가능할까요?", now - 2 * h,
            lastSent = false, answered = false)
        // 답한 대화 (기존 고객 번호 재사용 → 이름·고객카드까지 붙음)
        seedSms("010-2456-7801", "네 사장님, 내일 오전 9시에 뵙겠습니다 🙂", now - 5 * h,
            lastSent = true, answered = true, firstMs = now - 12 * day)
        seedSms("010-4892-1157", "네 감사합니다! 하자 있으면 언제든 연락주세요.", now - 26 * h,
            lastSent = true, answered = true, firstMs = now - 20 * day)

        // 4) 주소 등록 데모용 — 주소가 든 받은 문자(대화방에서 주소 톡 → 일정 등록 확인창).
        //    systemId<0 로 로컬 보존되어 시스템 SMS 재조회에도 안 지워진다.
        app.container.cachedMessageRepository.persistLocalSent(
            sfx("010-2240-5567"),
            SmsRepository.SmsMessage(
                id = -770011L, address = "010-2240-5567",
                body = "안녕하세요, 화장실 타일 시공 문의드려요. 주소는 서울 강남구 테헤란로 521 샘플아파트 103동 1402호 입니다. 견적 부탁드려요!",
                dateMs = now - 68 * 60 * 1000, sent = false,
                imageUris = emptyList(), videoUris = emptyList()
            )
        )
    }

    companion object {
        const val ACTION_SEED = "com.detailline.callfollowcrm.DEBUG_SEED"
        private const val TAG = "DebugSeedReceiver"
    }
}
