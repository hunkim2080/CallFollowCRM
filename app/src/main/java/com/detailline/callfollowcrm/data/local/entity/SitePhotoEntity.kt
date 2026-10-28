package com.detailline.callfollowcrm.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 현장 사진 (로컬 전용, 2026-06-04) — 사장님이 고객 현장에 올린 사진.
 *   파일은 앱 내부 저장소(filesDir/site_photos/)에 복사 후 경로만 보관.
 *   팀원↔사장님 공유(서버)는 별개 — docs/SERVER_HANDOFF 의 team_site_photos 보강 후 연동 예정.
 */
@Entity(
    tableName = "site_photos",
    indices = [Index("customerId"), Index("jobId")]
)
data class SitePhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,
    /** 어느 시공 '건'의 사진인가. null = 아직 미분류(옛 데이터). (v50, 2026-09-14 사장님) */
    val jobId: Long? = null,
    /** 앱 내부 저장소에 복사한 파일 절대 경로. */
    val filePath: String,
    /** '시공 전'|'시공 중'|'시공 후'|'추가' — 지금은 **'시공 전'만** 쓴다([LABEL_BEFORE]).
     *   내 기록 영상 전/후 비교용으로, 그 건의 '시공 전' 대표 한 장을 표시한다. 한 건에 최대 1장. (2026-10-28 사장님) */
    val label: String? = null,
    val createdAt: Long,
    /**
     * 시공막내 웹(서버 team_site_photos)에 올린 시각(ms). null = 아직 안 올림(백필 대상).
     * -1 = 파일이 사라져 올릴 수 없음(재시도 제외용 표식). (2026-08-13 웹 뷰어)
     */
    val serverUploadedAt: Long? = null,
    /** 사장님이 **꾹 눌러 직접 고른 대표 사진**. (v67, 2026-10-15 사장님: "맨 먼저 올린 게 대표가 되던데 — 꾹 누르면 대표로")
     *   한 건에 최대 1장만 true. 없으면 맨 먼저 올린 사진이 대표(RepresentativeOf 폴백). */
    val isRepresentative: Boolean = false
) {
    companion object {
        /** '시공 전' 사진 표식 — [label] 에 이 값이면 그 건의 '시공 전' 대표.
         *   ⚠️ `SitePhotoDao` 의 markBefore/clearBefore 쿼리도 이 글자를 **인라인**으로 쓴다(Room 애너테이션은 상수를 못 끼운다 — 바꾸면 양쪽 같이). */
        const val LABEL_BEFORE = "시공 전"
    }
}
