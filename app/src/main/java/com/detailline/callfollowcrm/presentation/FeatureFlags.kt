package com.detailline.callfollowcrm.presentation

/**
 * 화면 노출 플래그 — 백엔드는 그대로 두고 UI 만 껐다 켰다. (2026-07-18)
 */
object FeatureFlags {
    /**
     * 팀원(서버 비즈니스 요금제) 기능 노출 여부. false = 숨김. (2026-07-18 사장님: 사용자는 일당사장(=협업 사장)만 씀)
     *   true 로 바꾸면 부활 — 배정 시트 '👷 팀원' 섹션 + 인원관리 '팀원' 탭이 다시 나온다.
     *   TeamRepository·TeamScreen 등 백엔드/화면 코드는 그대로 살아있음.
     */
    const val SHOW_TEAM_MEMBERS = false
}
