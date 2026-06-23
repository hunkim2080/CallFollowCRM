package com.detailline.callfollowcrm.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 사장님이 미확인 카드를 좌→우 swipe 해서 "광고/스팸" 으로 마킹한 번호.
 *   key = phone number 의 끝 8자리 (suffix) — 번호 표기 다양("010-...", "+82 10...") 대응.
 *
 * 효과: HomeViewModel.unconfirmedSuffixes() 에서 spam suffix 는 제외 → 미확인 카테고리에서 영구 안 보임.
 *   전체 / 카테고리 탭에서는 그대로 표시 (사장님 결정 2026-05-25).
 *
 * Undo: Snackbar 에서 5초 안에 되돌리기 → 이 row delete.
 */
@Entity(tableName = "spam_phones")
data class SpamPhoneEntity(
    @PrimaryKey val phoneSuffix: String,
    val markedAt: Long,
    // 표시·복구용(2026-06-23 사장님 '스팸 목록'). 옛 데이터는 phoneNumber="" → 화면에서 suffix 로 대체.
    val phoneNumber: String = "",
    val displayName: String? = null,
    // "spam"=광고/스팸, "personal"=사생활(내 개인 연락처). 둘 다 RING-GO 목록·자동문자·AI 에서 제외. 목록만 따로 봄. (2026-06-23 사장님)
    val kind: String = "spam"
)
