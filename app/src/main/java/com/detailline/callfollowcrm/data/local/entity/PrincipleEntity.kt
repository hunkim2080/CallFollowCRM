package com.detailline.callfollowcrm.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * "막내가 알아낸 사장님 원칙" (2026-06-17 사장님). 말투/사례 위에 얹는 3번째 층 = 판단/의도.
 *   - 발견 카드(추천≠실제답)에서 ⭕ 받은 것, 또는 사장님이 직접 추가한 것.
 *   - enabled 인 것만 prepare-reply 에 보내져 AI 답변의 "판단 기준"으로 주입됨.
 *   - 폰별 분리는 prefs.deviceId 로(이 DB 자체가 이 폰 것이라 테이블엔 deviceId 불필요).
 */
@Entity(tableName = "principles")
data class PrincipleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 한 줄 원칙. 예: "신축 문의엔 즉답 견적 대신 방문 견적을 먼저 권한다." */
    val text: String,
    /** 켜짐만 AI 에 주입. 사장님이 잠시 끄고 싶을 때 toggle. */
    val enabled: Boolean = true,
    /** "discovered"(발견 카드 ⭕) | "manual"(직접 추가). */
    val source: String = "discovered",
    val createdAt: Long,
    val updatedAt: Long
)
