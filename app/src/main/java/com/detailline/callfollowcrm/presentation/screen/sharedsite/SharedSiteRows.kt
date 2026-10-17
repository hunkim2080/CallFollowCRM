package com.detailline.callfollowcrm.presentation.screen.sharedsite

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.detailline.callfollowcrm.ai.SharedSiteRepository
import com.detailline.callfollowcrm.ai.siteDisplayName
import com.detailline.callfollowcrm.presentation.theme.AppShape
import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.theme.AppType
import com.detailline.callfollowcrm.util.AddressExtractor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 현장 한 줄의 **두 모양** — 같은 줄(`SiteRow`, SharedSiteScreen.kt)을 모드로 가른다. 복사해서 두 벌 만들지 않는다(§12-D).
 *   · [Feed]          현장순 목록 — 손잡기 · 제목 · 업체명 · 날짜 · 시공 · 출근시간 (전과 같음)
 *   · [PartnerDetail] 업체 내역(업체 눌러 들어간 달별 목록) — 날짜 기둥 · 단지명/도로명 2줄 · 상태 · 일당 (프로토 확정 2026-10-17)
 *
 *   이 파일이 따로 있는 이유: SharedSiteScreen.kt 는 1,994줄이라 size_guard 가 더 못 키우게 한다(§12-F — 잎은 옆 파일로).
 *   값은 전부 토큰(AppType · AppShape · AppTheme.colors) — 손으로 적은 크기·색이 0 이어야 style_guard 를 통과한다.
 */
internal enum class SiteRowMode { Feed, PartnerDetail }

/**
 * [SiteRowMode.PartnerDetail] — 업체 내역(달별) 한 줄. 프로토 확정본 1:1 (2026-10-17 사장님).
 *   [ M.d ]  단지명(진하게)                         [상태 딱지]
 *   [ 요일 ]  도로명 · 동호수(연하게·회색·말줄임)        [N만]
 *   손잡기 아이콘 없음 · 업체명 없음(이미 그 업체 화면) · 출근시간 없음.
 *   주소 2줄 가르기는 [AddressExtractor.twoLine] **한 곳**(여기서만 쓴다 — 다른 화면 제목은 siteLabel 그대로).
 *   딱지 글자는 `collabStatusTag` 와 같은 한 곳, 색만 위계로 — 완료=회색으로 가라앉힘, **지남=amber 로 살림**.
 */
@Composable
internal fun PartnerDetailSiteRow(site: SharedSiteRepository.SharedSite, onClick: () -> Unit) {
    val (line1, line2) = remember(site.addr, site.title) {
        val two = AddressExtractor.twoLine(site.addr)
        if (two.first.isNotBlank()) two else siteDisplayName(site) to null
    }
    val (stTxt, stBg0, stFg0) = collabStatusTag(site)
    val c = AppTheme.colors
    val (stBg, stFg) = when (stTxt) {
        "완료" -> c.neutralBg to c.neutralText
        "지남" -> c.cautionBg to c.cautionText
        else -> stBg0 to stFg0
    }
    Row(
        Modifier.fillMaxWidth().clip(AppShape.md).background(c.surface)
            .border(1.dp, c.surfaceMuted, AppShape.md)
            .clickable { onClick() }.padding(horizontal = 13.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DatePillar(site.scheduledAtMs)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(line1, style = AppType.headline, fontWeight = FontWeight.ExtraBold, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!line2.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(line2, style = AppType.caption, fontWeight = FontWeight.Medium, color = c.textHint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Box(Modifier.clip(AppShape.pill).background(stBg).padding(horizontal = 8.dp, vertical = 2.dp)) {
                Text(stTxt, style = AppType.micro, color = stFg)
            }
            site.dailyWage?.let {
                Spacer(Modifier.height(4.dp))
                Text("${it}만", style = AppType.headline, fontWeight = FontWeight.ExtraBold, color = c.text)
            }
        }
    }
}

/** 날짜 기둥 — [PartnerDetailSiteRow]·[MySharedDetailRow] 공용. "10.14" 두 자리도 안 잘리게 56dp. (2026-10-17 사장님 "날짜가 잘려서") */
@Composable
private fun DatePillar(scheduledAtMs: Long) {
    val c = AppTheme.colors
    val hasDate = scheduledAtMs > 0L
    val dayText = if (hasDate) SimpleDateFormat("M.d", Locale.KOREA).format(Date(scheduledAtMs)) else "미정"
    val dowText = if (hasDate) SimpleDateFormat("E", Locale.KOREA).format(Date(scheduledAtMs)).take(1) else ""
    Column(Modifier.width(56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(dayText, style = AppType.headline.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.ExtraBold, color = c.text, maxLines = 1, softWrap = false)
        if (dowText.isNotBlank()) {
            Text(dowText, style = AppType.caption, fontWeight = FontWeight.Medium, color = c.textHint)
        }
    }
}

/**
 * 「내가 공유한 현장」 한 줄 — 공유받은 쪽과 **같은 날짜 기둥**. 손잡기 아이콘·출근시간 없음(§12-D 복붙 금지, 날짜 기둥 재사용).
 *   [ M.d ]  현장명(진하게)              [상태]
 *   [ 요일 ]  누구랑(보라)
 *   (2026-10-17 사장님 "내가 공유한 현장도 정리")
 */
@Composable
internal fun MySharedDetailRow(site: SharedSiteRepository.SharedSite, onClick: () -> Unit) {
    val c = AppTheme.colors
    val (stTxt, stBg, stFg) = collabStatusTag(site)
    val who = site.partnerName?.let { "$it 사장님과 함께" }
        ?: if (site.status == "pending") "수락 대기 중" else "함께하는 사장님과"
    Row(
        Modifier.fillMaxWidth().clip(AppShape.md).background(c.surface)
            .border(1.dp, c.surfaceMuted, AppShape.md)
            .clickable { onClick() }.padding(horizontal = 13.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DatePillar(site.scheduledAtMs)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(siteDisplayName(site), style = AppType.headline, fontWeight = FontWeight.ExtraBold, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(who, style = AppType.caption, fontWeight = FontWeight.Bold, color = CollabPurple, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.clip(AppShape.pill).background(stBg).padding(horizontal = 8.dp, vertical = 2.dp)) {
            Text(stTxt, style = AppType.micro, color = stFg)
        }
    }
}
