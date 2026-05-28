package com.detailline.callfollowcrm.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * SMS 연락처 캐시 — HomeScreen 카드 생성을 위한 phone-level summary.
 *
 * 2026-05-28 사장님 통점 fix: SmsRepository.observeContacts 가 매번 17000건 풀스캔 → 느림.
 *   해결: 풀스캔은 한 번만 + 그 후 Room cache 만 observe → instant 갱신.
 *
 * primary key = normalizedSuffix (phone 끝 8자리) — phone format 표준화.
 *   같은 번호의 "010-1234-5678" vs "01012345678" 등이 다 같은 row.
 *
 * 갱신 정책:
 *   - 앱 첫 실행: SmsRepository 풀스캔 → rebuildFromFullScan
 *   - 새 SMS 도착: SmsReceiver → upsertOne (그 phone 만)
 *   - 사장님 발신: 알림 빠른답장/ChatScreen send → upsertOne
 *   - 정기 (백그라운드, 추후): 전체 rebuild 로 정합성 보장
 *
 * 이 테이블 자체는 빠른 표시 용도. 진짜 SMS 본문/이력은 시스템 provider 가 정본.
 */
@Entity(tableName = "sms_contacts_cache")
data class SmsContactCacheEntity(
    @PrimaryKey val normalizedSuffix: String,
    /** 표시용 raw phone (예: "010-1234-5678"). */
    val address: String,
    val lastBody: String,
    val lastDateMs: Long,
    /** 마지막 메시지를 사장님이 보냈는지 (= true) 또는 고객이 (= false). */
    val lastSent: Boolean,
    /**
     * 스캔 범위 안에 사장님이 이 phone 으로 sent SMS 가 1건이라도 있는지.
     * 미확인 KPI 부재중 통화 분기에서 사용.
     */
    val hasOwnerReply: Boolean,
    /** 스캔 범위 안 이 phone 의 가장 오래된 메시지 시각. */
    val firstDateMsInScan: Long,
    /** 캐시 row 갱신 시각 — 디버그/정합성 체크용. */
    val updatedAt: Long
)
