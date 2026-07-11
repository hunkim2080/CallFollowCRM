package com.detailline.callfollowcrm.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 대화(번호 끝 8자리) 단위 분류 결과 — 상담함(CONSULT) vs 문자함(GENERAL). (2026-07-11 사장님)
 *
 * 왜 sms_contacts_cache 컬럼이 아니라 별도 테이블인가:
 *   캐시는 rebuildFromFullScan() 이 deleteAll 후 재삽입한다(정본은 SMS provider). 캐시에 분류를 실으면
 *   정합성 리빌드 때마다 분류가 전멸한다. spam_phones 처럼 suffix 키 사이드 테이블이 옳다.
 *
 * 규칙:
 *   - 행이 없는 suffix = 미분류 = 상담함(fail-open). 진짜 고객을 문자함에 숨기는 오탐이 치명적이라 안전 방향.
 *   - source 신뢰도: OWNER(사장님 이동) > HAIKU(서버 2차) > LOCAL(폰 1차). 우선순위는 BucketPolicy 가 강제.
 *   - OWNER 결정은 어떤 자동 분류도 못 뒤집는다(영구 보호).
 */
@Entity(tableName = "thread_buckets")
data class ThreadBucketEntity(
    @PrimaryKey val suffix: String,          // 끝 8자리 (phoneSuffix 규칙과 동일: digits.takeLast(8), 8미만이면 전체)
    val bucket: String,                      // "CONSULT" | "GENERAL"
    val source: String,                      // "LOCAL" | "HAIKU" | "OWNER"
    val reason: String? = null,              // "대표번호(1522)", "광고", "사장님 이동" — 복구 UI 문구·디버그용
    val decidedAt: Long,
    val classifiedBodyHash: Int? = null      // 마지막 분류한 본문 해시 — 같은 내용 재분류(=Haiku 재호출) 방지
)
