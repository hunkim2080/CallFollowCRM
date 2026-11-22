package com.detailline.callfollowcrm.domain.collab

/**
 * 🤝 고객 상세 **그 건(N차)** 의 협업 사장 사진 묶기 — **업체(공유)별**로.
 *
 *   어느 shareId 가 이 건에 걸렸나는 [CollabAssignmentLine.shareIdsForJob] 가 정한다
 *   (그 건의 날짜에 연결된 share 만 = 같은 고객 1차·2차가 안 섞인다). 여기선 그 shareId 들의
 *   사진을 넘겨받아 **그 순서대로** 업체별 묶음으로 만들고 총 수를 센다.
 *
 *   사진 타입은 모른다(제네릭) — presentation 이 `SharedSiteRepository.SharedPhoto` 를 넣는다.
 *   순수 함수 — 시험은 `CollabJobPhotosTest`.
 */
object CollabJobPhotos {

    /** 한 업체(공유)의 사진 묶음 — 토글 안 「업체 칩」 하나. */
    data class Group<T>(val shareId: String, val partnerName: String, val photos: List<T>)

    /**
     * [jobShareIds] 에 든 share 의 사진만, **그 순서대로** 업체별 묶음(사진 없는 업체는 뺀다).
     *   @param photosByShare shareId → 그 share 의 사진들(없으면 빈 묶음 → 제외).
     *   @param names shareId → 보이는 업체명(비면 「협업」).
     */
    fun <T> group(
        jobShareIds: List<String>,
        photosByShare: Map<String, List<T>>,
        names: Map<String, String>,
    ): List<Group<T>> =
        jobShareIds.distinct().mapNotNull { sid ->
            val ps = photosByShare[sid].orEmpty()
            if (ps.isEmpty()) null
            else Group(sid, names[sid]?.trim()?.takeIf { it.isNotBlank() } ?: "협업", ps)
        }

    /** 토글 제목의 「협업 N」 — 묶음 전체 사진 수. */
    fun total(groups: List<Group<*>>): Int = groups.sumOf { it.photos.size }
}
