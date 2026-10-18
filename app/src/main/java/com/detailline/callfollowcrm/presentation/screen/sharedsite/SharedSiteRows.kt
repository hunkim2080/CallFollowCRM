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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.detailline.callfollowcrm.ai.SharedSiteRepository
import com.detailline.callfollowcrm.ai.siteDisplayName
import com.detailline.callfollowcrm.domain.collab.CollabPartnerGroups
import com.detailline.callfollowcrm.domain.collab.MonthFold
import com.detailline.callfollowcrm.presentation.screen.collab.CollabRecordViewModel
import com.detailline.callfollowcrm.presentation.theme.AppShape
import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.theme.AppType
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary
import com.detailline.callfollowcrm.util.AddressExtractor
import com.detailline.callfollowcrm.util.DateTimeUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 협업 현장 **한 줄의 세 모양** — 틀은 [CollabPillarRow] **한 곳**이고, 세 줄은 거기에 글자만 넣는다. 복사해서 두 벌 만들지 않는다(§12-D).
 *   · [SiteRowMode.Feed]          현장순 목록 — 날짜 기둥 · 현장명 · 「○○ 사장님」(보라) · 상태 · 일당 (2026-10-17 사장님 "현장순도 날짜 기둥으로")
 *   · [SiteRowMode.PartnerDetail] 업체 내역(업체 눌러 들어간 달별 목록) — 날짜 기둥 · 단지명/도로명 2줄 · 상태 · 일당 (프로토 확정 2026-10-17)
 *   · [MySharedDetailRow]         내가 공유한 현장 — 날짜 기둥 · 현장명 · 누구랑(보라) · 상태
 *
 *   이 파일이 따로 있는 이유: SharedSiteScreen.kt 는 1,900줄대라 size_guard 가 더 못 키우게 한다(§12-F — 잎은 옆 파일로).
 *   값은 전부 토큰(AppType · AppShape · AppTheme.colors) — 손으로 적은 크기·색이 0 이어야 style_guard 를 통과한다.
 */
internal enum class SiteRowMode { Feed, PartnerDetail }

/** 현장 한 줄 — 같은 줄을 [SiteRowMode] 로 가른다. */
@Composable
internal fun SiteRow(site: SharedSiteRepository.SharedSite, onClick: () -> Unit, mode: SiteRowMode = SiteRowMode.Feed) {
    when (mode) {
        SiteRowMode.Feed -> FeedSiteRow(site, onClick)
        SiteRowMode.PartnerDetail -> PartnerDetailSiteRow(site, onClick)
    }
}

/**
 * 날짜 기둥 줄의 **틀 한 곳** — 세 줄([FeedSiteRow]·[PartnerDetailSiteRow]·[MySharedDetailRow])이 전부 이걸 부른다.
 *   [ M.d ]  line1(진하게)                     [상태 딱지]
 *   [ 요일 ]  line2(연하게 · line2Color)         [N만]  ← wageMan 이 null 이면 없음
 */
@Composable
internal fun CollabPillarRow(
    scheduledAtMs: Long,
    line1: String,
    line2: String?,
    line2Color: Color,
    line2Weight: FontWeight,
    status: Triple<String, Color, Color>,
    wageMan: Int?,
    onClick: () -> Unit
) {
    val c = AppTheme.colors
    val (stTxt, stBg, stFg) = status
    Row(
        Modifier.fillMaxWidth().clip(AppShape.md).background(c.surface)
            .border(1.dp, c.surfaceMuted, AppShape.md)
            .clickable { onClick() }.padding(horizontal = 13.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DatePillar(scheduledAtMs)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(line1, style = AppType.headline, fontWeight = FontWeight.ExtraBold, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!line2.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(line2, style = AppType.caption, fontWeight = line2Weight, color = line2Color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Box(Modifier.clip(AppShape.pill).background(stBg).padding(horizontal = 8.dp, vertical = 2.dp)) {
                Text(stTxt, style = AppType.micro, color = stFg)
            }
            wageMan?.let {
                Spacer(Modifier.height(4.dp))
                Text("${it}만", style = AppType.headline, fontWeight = FontWeight.ExtraBold, color = c.text)
            }
        }
    }
}

/**
 * 상태 딱지 — 글자는 `collabStatusTag` 한 곳, 색만 위계로 **가라앉힌 벌**: 완료=회색으로 가라앉힘, **지남=amber 로 살림**.
 *   업체 내역·현장순이 쓴다(목록에서 「완료」가 줄줄이 초록이면 아직 안 끝난 것이 안 보인다).
 *   「내가 공유한 현장」은 원래 색 그대로(`collabStatusTag`) — 보이던 것을 안 바꾼다.
 */
@Composable
internal fun collabStatusTagQuiet(site: SharedSiteRepository.SharedSite): Triple<String, Color, Color> {
    val (stTxt, stBg0, stFg0) = collabStatusTag(site)
    val c = AppTheme.colors
    return when (stTxt) {
        "완료" -> Triple(stTxt, c.neutralBg, c.neutralText)
        "지남" -> Triple(stTxt, c.cautionBg, c.cautionText)
        else -> Triple(stTxt, stBg0, stFg0)
    }
}

/**
 * [SiteRowMode.Feed] — 현장순 목록 한 줄. (2026-10-17 사장님 — 업체 내역과 같은 날짜 기둥으로)
 *   [ M.d ]  현장명(진하게)                [상태 딱지]
 *   [ 요일 ]  ○○ 사장님(보라)                [N만]
 *   손잡기 아이콘 없음. 2줄 = **주인 사장님** — 「내가 공유한 현장」의 「누구랑」과 같은 자리·같은 색.
 */
@Composable
internal fun FeedSiteRow(site: SharedSiteRepository.SharedSite, onClick: () -> Unit) {
    val owner = site.ownerName.trim()
    CollabPillarRow(
        scheduledAtMs = site.scheduledAtMs,
        line1 = siteDisplayName(site),
        line2 = if (owner.isBlank()) "사장님" else "$owner 사장님",
        line2Color = CollabPurple,
        line2Weight = FontWeight.Bold,
        status = collabStatusTagQuiet(site),
        wageMan = site.dailyWage,
        onClick = onClick
    )
}

/**
 * [SiteRowMode.PartnerDetail] — 업체 내역(달별) 한 줄. 프로토 확정본 1:1 (2026-10-17 사장님).
 *   [ M.d ]  단지명(진하게)                         [상태 딱지]
 *   [ 요일 ]  도로명 · 동호수(연하게·회색·말줄임)        [N만]
 *   손잡기 아이콘 없음 · 업체명 없음(이미 그 업체 화면) · 출근시간 없음.
 *   주소 2줄 가르기는 [AddressExtractor.twoLine] **한 곳**(여기서만 쓴다 — 다른 화면 제목은 siteLabel 그대로).
 */
@Composable
internal fun PartnerDetailSiteRow(site: SharedSiteRepository.SharedSite, onClick: () -> Unit) {
    val (line1, line2) = remember(site.addr, site.title) {
        val two = AddressExtractor.twoLine(site.addr)
        if (two.first.isNotBlank()) two else siteDisplayName(site) to null
    }
    CollabPillarRow(
        scheduledAtMs = site.scheduledAtMs,
        line1 = line1,
        line2 = line2,
        line2Color = AppTheme.colors.textHint,
        line2Weight = FontWeight.Medium,
        status = collabStatusTagQuiet(site),
        wageMan = site.dailyWage,
        onClick = onClick
    )
}

/**
 * 「내가 공유한 현장」 한 줄 — 공유받은 쪽과 **같은 날짜 기둥**(틀은 [CollabPillarRow]).
 *   [ M.d ]  현장명(진하게)              [상태]
 *   [ 요일 ]  누구랑(보라)
 *   (2026-10-17 사장님 "내가 공유한 현장도 정리")
 */
@Composable
internal fun MySharedDetailRow(site: SharedSiteRepository.SharedSite, onClick: () -> Unit) {
    val who = site.partnerName?.let { "$it 사장님과 함께" }
        ?: if (site.status == "pending") "수락 대기 중" else "함께하는 사장님과"
    CollabPillarRow(
        scheduledAtMs = site.scheduledAtMs,
        line1 = siteDisplayName(site),
        line2 = who,
        line2Color = CollabPurple,
        line2Weight = FontWeight.Bold,
        status = collabStatusTag(site),
        wageMan = null,
        onClick = onClick
    )
}

/** 날짜 기둥 — [CollabPillarRow] 전용. "10/14" 두 자리도 안 잘리게 56dp. (2026-10-17 사장님 "날짜가 잘려서")
 *  날짜 글자는 앱 표준 「10/8」 공용 한 곳 — 2026-10-18 UX 감사에서 협업만 점(「10.8」)이라 통일. 요일은 그대로 밑에. */
@Composable
private fun DatePillar(scheduledAtMs: Long) {
    val c = AppTheme.colors
    val hasDate = scheduledAtMs > 0L
    val dayText = if (hasDate) DateTimeUtils.formatMonthDay(scheduledAtMs) else "미정"
    val dowText = if (hasDate) SimpleDateFormat("E", Locale.KOREA).format(Date(scheduledAtMs)).take(1) else ""
    Column(Modifier.width(56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(dayText, style = AppType.headline.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.ExtraBold, color = c.text, maxLines = 1, softWrap = false)
        if (dowText.isNotBlank()) {
            Text(dowText, style = AppType.caption, fontWeight = FontWeight.Medium, color = c.textHint)
        }
    }
}

/**
 * 달 머리줄 **한 곳** — 「2026년 10월 ……… N곳(· 받은 일당 N만원) ▾」. 업체 내역·내가 공유한 현장이 같이 쓴다.
 *   달 이름은 [CollabRecordViewModel.monthLabel] 재사용(새로 안 셈). 묶기는 `CollabPartnerGroups.byMonth`.
 *   **머리 전체가 탭 자리** — 누르면 그 달이 접힘/펼침(▾ 펼침 · ▸ 접힘). 판단은 [rememberMonthFold] → `MonthFold` 한 곳. (2026-10-17 사장님)
 */
@Composable
internal fun CollabMonthHeader(ym: String, right: String, open: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(AppShape.sm).clickable { onToggle() }
            .padding(start = 2.dp, end = 2.dp, top = 8.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(CollabRecordViewModel.monthLabel(ym), style = AppType.label, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary)
        Spacer(Modifier.weight(1f))
        Text(right, style = AppType.caption, fontWeight = FontWeight.Medium, color = TossTextTertiary)
        Spacer(Modifier.width(6.dp))
        Text(if (open) "▾" else "▸", style = AppType.label, fontWeight = FontWeight.Bold, color = TossTextTertiary)
    }
}

/**
 * 📅 달 접기 상태 — 화면이 들고 다니는 건 「뒤집은 달」 집합 하나. 판단·뒤집기는 `MonthFold`(순수) 한 곳.
 *   `latestYm` 은 매 컴포지션에 새로 받는다 — 데이터가 새로 와서 최근 달이 바뀌면 그 달이 저절로 펼쳐진다.
 */
internal class MonthFoldState(private val latestYm: String?, private val toggled: MutableState<Set<String>>) {
    fun isOpen(ym: String): Boolean = MonthFold.isOpen(ym, latestYm, toggled.value)
    fun toggle(ym: String) { toggled.value = MonthFold.toggle(toggled.value, ym) }
}

/** 두 화면(업체 내역 · 내가 공유한 현장)이 같이 부른다 — 기본값은 **가장 최근 달만 펼침.** */
@Composable
internal fun rememberMonthFold(months: List<CollabPartnerGroups.Month>): MonthFoldState {
    val toggled = remember { mutableStateOf(emptySet<String>()) }
    val latest = remember(months) { MonthFold.latestOf(months.map { it.ym }) }
    return MonthFoldState(latest, toggled)
}
