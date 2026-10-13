package com.detailline.callfollowcrm.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 🔔 인앱 알림 센터 한 줄. (2026-10-13 사장님: "신규 활동 한눈에 + 푸시 지워져도 뭐 왔었지")
 *
 * 알림을 **쏘는 순간**(NotificationHelper) 같이 한 줄 기록해둔다 → 푸시를 지워도 여기 남는다.
 * 탭하면 목적지로 이동하고 **읽음(회색)으로 남는다** — 삭제하면 "뭐 왔었지" 하고 까먹어서. (2026-10-13 사장님 재결정)
 *   전엔 탭 = 삭제였는데, 사장님: "사라지면 까먹을 듯. 회색으로 읽은 느낌을 주자."
 * 배지(🔔) 숫자 = **안 읽은 것만**. '모두 읽기' 로 한 번에 읽음 처리. 자동삭제 없음(상한 trimTo 만).
 */
@Entity(tableName = "app_notifications", indices = [Index("createdAt")])
data class AppNotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 분류·아이콘용 키. 예: new_inquiry · reply · missed_call · intake · reminder · recurring ·
     *   balance_due · daily_brief · install_d1 · call_summary · collab_* . (NotificationHelper 의 종류와 맞춤) */
    val type: String,
    /** 한 줄 제목(알림 제목 그대로). */
    val title: String,
    /** 부제/내용(알림 본문). 없을 수 있음. */
    val body: String? = null,
    // ── 탭하면 갈 목적지 — 푸시의 딥링크와 같은 재료. 없으면 탭해도 그냥 삭제만. ──
    /** 이동 종류. 예: "chat"(고객 대화) · "collab"(협업 현장) · "schedule" · "settlement" · "intake" · "home". */
    val navTarget: String? = null,
    val navPhone: String? = null,
    val navCustomerId: Long? = null,
    val navShareId: String? = null,
    val createdAt: Long,
    /** 읽은 시각(null=안 읽음). 탭하거나 '모두 읽기' 하면 찍힘 — 삭제 대신 **회색으로 남긴다**(까먹지 않게). (2026-10-13 사장님) */
    val readAt: Long? = null
)
