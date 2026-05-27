package com.detailline.callfollowcrm.category

import com.detailline.callfollowcrm.data.local.entity.CategoryEntity

/**
 * 갤메시지 식 카테고리 자동 분류 — 1차 휴리스틱.
 *
 * 정책:
 *  - 사장님이 이미 categoryId 박은 고객은 절대 덮어쓰지 않음 (사장님 의지 보존).
 *  - 카테고리가 없으면 분류 안 함.
 *  - 카테고리 이름의 단어들이 대화 본문에 등장하면 매칭 점수 ↑.
 *    예: "마곡 신축" 카테고리 → 메시지에 "마곡" 또는 "신축" 들어가면 점수 ↑↑.
 *  - 점수 동률이면 첫 번째 카테고리 우선 (사장님이 displayOrder 작게 둔 게 우선).
 *  - 점수 0 이면 매칭 안 함 (미분류 유지).
 *
 * 한계:
 *  - LLM 추론 아니라 단순 substring. "마곡 신축" vs "마곡 구축" 구분 X.
 *  - 동의어/맥락 이해 X. 예: "이번 주" 만 가지고 "5월 시공" 카테고리 매칭 불가.
 *
 * 다음 세션: 서버 endpoint `POST /api/category-classify` 도입 후 이 휴리스틱은 fallback.
 *  서버 명세는 RINGGO_SERVER_P0P1P2_UPGRADE.md §11 참조 (예정).
 */
object CategoryAutoClassifier {

    /**
     * 메시지 본문 합쳐서 카테고리들 중 가장 잘 맞는 것을 반환. 없으면 null.
     *
     * @param conversationText 최근 메시지 본문을 공백으로 join 한 한 문자열 (소문자 정규화 안 됨 — 함수가 처리)
     * @param categories 후보 카테고리들
     */
    fun classify(
        conversationText: String,
        categories: List<CategoryEntity>
    ): CategoryEntity? {
        if (categories.isEmpty()) return null
        if (conversationText.isBlank()) return null

        val haystack = conversationText.lowercase()
        var best: CategoryEntity? = null
        var bestScore = 0

        for (cat in categories) {
            val keywords = cat.name.lowercase().split(' ', '/', ',', '·')
                .map { it.trim() }
                .filter { it.length >= 2 }  // 1글자 키워드는 false positive 너무 많음
            if (keywords.isEmpty()) continue

            val score = keywords.sumOf { kw ->
                // 등장 횟수만큼 가산. 같은 단어 여러 번 등장하면 점수 ↑.
                countOccurrences(haystack, kw)
            }
            if (score > bestScore) {
                bestScore = score
                best = cat
            }
        }
        return best  // bestScore == 0 이면 best 도 null (초기값)
    }

    private fun countOccurrences(text: String, needle: String): Int {
        if (needle.isEmpty()) return 0
        var count = 0
        var idx = text.indexOf(needle)
        while (idx >= 0) {
            count++
            idx = text.indexOf(needle, idx + needle.length)
        }
        return count
    }
}
