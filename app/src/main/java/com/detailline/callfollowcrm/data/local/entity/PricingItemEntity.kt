package com.detailline.callfollowcrm.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 견적서 작성기에서 사용하는 가격표 항목.
 * 사장님이 설정 → 가격표 관리 에서 직접 CRUD.
 *
 * pricing.md 와는 별개 — pricing.md = 서버 LLM prompt 용 단일 출처 (사장님 직접 편집).
 * 이 테이블 = 폰에서 사장님이 항목 체크하면서 견적서 합성하는 구조화된 데이터.
 * (둘이 별개라 동기화 필요. 1차 = 시드만 pricing.md 기반, 이후 분기되어도 OK.)
 */
@Entity(tableName = "pricing_items")
data class PricingItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 사장님이 보일 라벨. 예: "욕조 있는 화장실 바닥 1곳" */
    val title: String,
    /** 단가 (원). 곱셈은 수량(또는 평수) × price. */
    val price: Long,
    /**
     * 단가 단위 (2026-06-01, DB v22). "FLAT"=정액(개당), "PYEONG"=평당.
     * 견적 합산은 동일(price × 수량). PYEONG 이면 수량을 "평수"로 해석/표시.
     */
    val unit: String = UNIT_FLAT,
    /** "NEW" / "OLD" / "COMMON" — 신축/구축/공통. 견적서 다이얼로그에서 신축 모드 선택 시 NEW+COMMON 만 노출. */
    val category: String,
    /** 같은 카테고리 안 정렬 순서. 사장님 자주 쓰는 항목 위로. */
    val displayOrder: Int,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    /**
     * true = AI/업종 스타터가 대략값으로 자동 채운 '추정' 항목(가격표 온보딩, DB v37). 화면에 "추정" 배지 표시.
     *   사장님이 가격을 한 번이라도 고치면 false 로 = '내가 확인한 진짜 값'. (2026-07-02 사장님)
     */
    val isEstimated: Boolean = false
) {
    companion object {
        const val UNIT_FLAT = "FLAT"
        const val UNIT_PYEONG = "PYEONG"
    }
}
