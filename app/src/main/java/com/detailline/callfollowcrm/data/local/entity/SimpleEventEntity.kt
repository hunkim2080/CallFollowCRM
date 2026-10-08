package com.detailline.callfollowcrm.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 간단 일정 — 전화번호 없이 달력에만 적는 메모형 일정. (DB v51, 2026-09-16 사장님)
 *
 *   "이 앱을 쓰다 이 캘린더가 편해서 다른 일정도 입력하는 사람들이 생길 것 같은데,
 *    지금은 폰번호를 입력해야 하잖아. 그냥 일정에 메모처럼 간단하게 등록하고 싶을 수도 있잖아."
 *
 * 고객(CustomerEntity)과 **일부러 분리**한다. 여기 들어온 건
 *   · 정산·통계에 안 잡히고 (돈이 아니다)
 *   · 고객 목록·상담함에 안 뜨고 (사람이 아니다)
 *   · D-day 태그도 안 붙는다
 * 달력에만 보이는 메모다. 자재 받는 날·차 정비·개인 약속 같은 것.
 *
 *  - dayStartMs: 그 날 00:00 epoch ms.
 *  - minutes: 자정부터 분(9시 = 540). **null = 하루 종일** (CustomerEntity.scheduledWorkMinutes 와 같은 규칙).
 *  - days: 며칠짜리(제주도 여행처럼 17~23). 1 = 하루. 날짜만 찍으면(시작·끝) 기간이 정해진다. (v64, 2026-10-08 사장님)
 *  - calendarEventId: 구글 캘린더에 올린 이벤트 id. null = 아직 안 올렸거나 연동 안 함.
 */
@Entity(
    tableName = "simple_events",
    indices = [Index(value = ["dayStartMs"], name = "idx_simple_events_dayStartMs")]
)
data class SimpleEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val dayStartMs: Long,
    val minutes: Int? = null,
    /** 며칠짜리. 1 = 하루. (v64, 2026-10-08 사장님 — 여러 날 간단 일정) */
    val days: Int = 1,
    val memo: String = "",
    val calendarEventId: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)
