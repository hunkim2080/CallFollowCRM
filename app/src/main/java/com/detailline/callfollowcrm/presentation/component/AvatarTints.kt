package com.detailline.callfollowcrm.presentation.component

import androidx.compose.ui.graphics.Color
import com.detailline.callfollowcrm.domain.ui.AvatarInitial
import com.detailline.callfollowcrm.presentation.theme.LightColors

/**
 * 🎨 **이름 동그라미(아바타)의 (바탕, 글자) 색표** — 한 곳. 글자·색 번호 셈은 [AvatarInitial](domain/ui).
 *
 *   전엔 `private val AV_TINTS` 가 **일곱 파일**에 각각 있었다(§12-B 로 셈, 2026-10-17):
 *     상담함·고객 목록·정산 = 한 색(2026-09-20 사장님 "색은 상태에만") / 검색·팀·연락처 = 프로토 avatarHtml 5색(연락처는 조금 다른 주황).
 *   같은 이름인데 값이 달라 한쪽만 고쳐졌다. 이제 두 벌만 남긴다 — 뜻이 다르니 둘이다(§12-I):
 *     · [SINGLE]  손님 — 색에 뜻이 없으니 **한 색**. 상태 불은 상담함 `Avatar` 의 status 가 따로 켠다.
 *     · [PALETTE] 사람·업체를 **서로 가르는** 자리(팀원·연락처·검색·협업 업체) — 이름 해시로 늘 같은 색.
 *   색은 전부 토큰(`LightColors`) — 손으로 적은 hex 는 style_guard 가 막는다.
 */
object AvatarTints {

    /** 손님 아바타 한 색 — 연한 파랑 면 + 파란 글자. */
    val SINGLE: List<Pair<Color, Color>> = listOf(
        LightColors.primaryBg to LightColors.primaryText,
    )

    /** 프로토 avatarHtml 5색 [bg, fg] — 파랑·초록·빨강·보라·노랑. 토큰의 (연한 면, 글자) 짝. */
    val PALETTE: List<Pair<Color, Color>> = listOf(
        LightColors.primaryBg to LightColors.primaryText,
        LightColors.doneBg to LightColors.doneText,
        LightColors.unpaidBg to LightColors.unpaidText,
        LightColors.categoryBg to LightColors.categoryText,
        LightColors.cautionBg to LightColors.cautionText,
    )

    /** 이름으로 고른 팔레트 색 — 같은 이름이면 언제나 같은 색([AvatarInitial.tintIndex]). */
    fun forName(name: String?): Pair<Color, Color> = PALETTE[AvatarInitial.tintIndex(name, PALETTE.size)]

    /** 번호(id 등)로 고른 팔레트 색 — 음수도 안전. */
    fun at(index: Long): Pair<Color, Color> = PALETTE[((index % PALETTE.size + PALETTE.size) % PALETTE.size).toInt()]
}
