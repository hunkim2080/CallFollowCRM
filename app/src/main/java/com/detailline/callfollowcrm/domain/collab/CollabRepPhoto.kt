package com.detailline.callfollowcrm.domain.collab

import com.detailline.callfollowcrm.ai.SharedSiteRepository.SharedPhoto

/** 협업 대표사진: 직접 고른 사진, 없거나 삭제됐으면 서버 목록의 첫 사진. */
object CollabRepPhoto {
    fun pick(photos: List<SharedPhoto>, chosenPhotoId: Long?): SharedPhoto? =
        photos.firstOrNull { it.photoId == chosenPhotoId } ?: photos.firstOrNull()
}
