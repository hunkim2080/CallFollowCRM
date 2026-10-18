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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.detailline.callfollowcrm.ai.SharedSiteRepository
import com.detailline.callfollowcrm.domain.collab.CollabPartnerGroups
import com.detailline.callfollowcrm.domain.ui.AvatarInitial
import com.detailline.callfollowcrm.presentation.component.AvatarTints
import com.detailline.callfollowcrm.presentation.theme.AppShape
import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.theme.AppType
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary
import com.detailline.callfollowcrm.util.DateTimeUtils

/**
 * 🤝 **「내가 공유한 현장」** — 공유받은 현장과 **같은 틀**: [업체별] / [현장순] 세그먼트 + 업체 드릴다운(달별·월 접기). (2026-10-17 사장님 "짝 맞추기")
 *
 *   · 업체별 = **내가 부른 사장님별**([CollabPartnerGroups.groupByPartner] — 상대 번호가 없어 상호로만 묶는다. 수락 전은 「수락 대기」 한 묶음).
 *     줄은 공유받은 쪽과 같은 [PartnerRow](색+글자 아바타 · N곳 · 최근) — 금액 줄은 없다(내가 **준** 일당이라 「받은 일당」이 아니다).
 *   · 현장순 = 달별 + 월 접기([rememberMonthFold]) + [MySharedDetailRow] — 전과 같다.
 *   · 업체 하나 → 「○○ 사장님과 함께한 현장 N곳」 머리 카드 + 그 사장님과 한 현장 달별(공유받은 `openPartner` 와 같은 모양).
 *   상태(myListView · myPartner)는 SharedSiteScreen 이 들고 있다 — 공유받은의 listView/bizPartner 와 **따로**. 뒤로가기·상단 제목도 거기서.
 *
 *   이 파일이 따로 있는 이유: SharedSiteScreen.kt 는 size_guard 가 더 못 키우게 한다(§12-F — 잎은 옆 파일로).
 *   값은 전부 토큰(AppType · AppShape · AppTheme.colors) — style_guard 0.
 */
@Composable
internal fun MySharedArea(
    sites: List<SharedSiteRepository.SharedSite>,
    loading: Boolean,
    loadedOnce: Boolean,
    noBizPhone: Boolean,
    listView: String,
    onListView: (String) -> Unit,
    partnerGroups: List<CollabPartnerGroups.Group>,
    openPartner: CollabPartnerGroups.Group?,
    onPickPartner: (String) -> Unit,
    onOpen: (SharedSiteRepository.SharedSite) -> Unit,
    onDelete: (SharedSiteRepository.SharedSite) -> Unit
) {
    val c = AppTheme.colors
    when {
        noBizPhone -> EmptyCard(
            "먼저 사업자 전화를 등록해주세요",
            "더보기 → 견적서·사업자 정보에서 전화번호를 넣으면, 내 현장을 다른 사장님께 공유할 수 있어요."
        )
        sites.isEmpty() && loading -> EmptyCard("불러오는 중…", "")
        // 🔌 한 번도 못 받아왔는데 비어 있으면 = "없음" 아니라 "못 불러옴". (2026-10-19 사장님: 둘 다 빈 창)
        sites.isEmpty() && !loadedOnce -> EmptyCard(
            "불러오는 중…",
            "연결이 느려요 — 아래로 당겨 새로고침 해보세요."
        )
        sites.isEmpty() -> EmptyCard(
            "내가 공유한 현장이 없어요",
            "고객 화면의 '협업 현장으로 공유', 일정 화면의 '전문가 배정'에서 부르면, 그 현장을 함께할 사장님과 여기에 모여요."
        )
        // 업체별 → 사장님 한 명 선택: 그 사장님과 한 현장 달별(공유받은 openPartner 와 같은 모양, 금액 줄은 없음)
        openPartner != null -> {
            Column(
                Modifier.fillMaxWidth().clip(AppShape.lg).background(CollabPurpleSoft).padding(15.dp)
            ) {
                Text(
                    if (openPartner.pending) "수락을 기다리는 현장" else "${openPartner.name} 사장님과 함께한 현장",
                    style = AppType.label, fontWeight = FontWeight.ExtraBold, color = CollabPurple
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (openPartner.pending) "아직 아무도 수락하지 않은 현장 ${openPartner.count}곳" else "함께한 현장 ${openPartner.count}곳",
                    style = AppType.caption, fontWeight = FontWeight.Medium, color = c.textSub
                )
            }
            Spacer(Modifier.height(10.dp))
            MySharedMonths(openPartner.sites, onOpen, onDelete)
            Text(
                if (openPartner.pending) "상대가 수락하면 그 사장님 이름으로 옮겨가요." else "누르면 현장 상세·한마디, 밀면 '삭제'로 내려요.",
                style = AppType.caption, color = TossTextTertiary, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            )
        }
        else -> {
            SegTabs(listView, onListView)
            Spacer(Modifier.height(12.dp))
            if (listView == "date") {
                Text(
                    "내가 다른 사장님께 공유한 현장이에요. 누가 함께하는지·어디까지 진행됐는지 한눈에 보여요.",
                    style = AppType.caption, color = TossTextTertiary, modifier = Modifier.padding(start = 2.dp, bottom = 8.dp)
                )
                MySharedMonths(sites, onOpen, onDelete)
                Text("누르면 현장 상세·한마디, 밀면 '삭제'로 내려요. 함께하는 사장님 이름·진행상태는 상대가 수락하면 채워져요.",
                    style = AppType.caption, color = TossTextTertiary, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            } else {
                Text(
                    "내가 부른 사장님별로 모았어요. 아직 수락 전인 현장은 「수락 대기」에 모여요.",
                    style = AppType.caption, color = TossTextTertiary, modifier = Modifier.padding(start = 2.dp, bottom = 8.dp)
                )
                partnerGroups.forEach { g ->
                    PartnerRow(g, onClick = { onPickPartner(g.key) }, showWage = false)
                    Spacer(Modifier.height(9.dp))
                }
                Text("업체를 누르면 그 사장님과 한 현장이 달별로 나와요.",
                    style = AppType.caption, color = TossTextTertiary, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            }
        }
    }
}

/**
 * 내가 공유한 현장 **달별 목록** — 현장순 전체와 업체 드릴다운이 같이 쓴다(두 벌 X).
 *   달 머리는 [CollabMonthHeader] 한 곳(공유받은 업체 내역과 같은 얼굴), 묶기는 [CollabPartnerGroups.byMonth] 재사용, 최신 달이 위.
 *   달 머리를 누르면 접힘/펼침 — 기본은 최근 달만 펼침. 판단은 업체 내역과 같은 MonthFold 한 곳. (2026-10-17 사장님)
 */
@Composable
private fun MySharedMonths(
    sites: List<SharedSiteRepository.SharedSite>,
    onOpen: (SharedSiteRepository.SharedSite) -> Unit,
    onDelete: (SharedSiteRepository.SharedSite) -> Unit
) {
    val months = remember(sites) { CollabPartnerGroups.byMonth(sites) }
    val fold = rememberMonthFold(months)
    months.forEach { month ->
        val open = fold.isOpen(month.ym)
        CollabMonthHeader(month.ym, "${month.count}곳", open = open, onToggle = { fold.toggle(month.ym) })
        if (open) month.sites.forEach { site ->
            MySharedSwipeBox(onDelete = { onDelete(site) }) {
                MySharedDetailRow(site, onClick = { onOpen(site) })
            }
            Spacer(Modifier.height(9.dp))
        }
        Spacer(Modifier.height(4.dp))
    }
}

/**
 * 업체별 행 — 색+글자 동그라미 · 사장님 이름 · 함께한 현장 N곳 · 최근 / (받은 일당 합계). 공유받은·내가 공유한 **둘이 같이 쓴다**.
 *   [showWage] = 공유받은(내가 받은 일당)만 true. 내가 공유한 쪽은 내가 **준** 일당이라 「받은 일당」이 아니니 금액 줄을 안 그린다.
 *   [CollabPartnerGroups.Group.pending] 묶음(「수락 대기」)은 글자 대신 시계 아이콘·회색 — 업체가 아니라 「아직 없음」이다.
 */
@Composable
internal fun PartnerRow(g: CollabPartnerGroups.Group, onClick: () -> Unit, showWage: Boolean = true) {
    val c = AppTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(AppShape.md).background(c.surface)
            .border(1.dp, c.surfaceMuted, AppShape.md)
            .clickable { onClick() }.padding(13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 🎨 손잡기 아이콘 대신 **색 동그라미 + 브랜드 첫 글자** (프로토 확정 2026-10-17) — 업체가 한눈에 갈린다.
        //   글자 = 상호의 브랜드 부분 첫 글자(「김사장, 하우스픽」이면 「하」), 색 = 이름 해시로 늘 같은 색.
        //   셈은 AvatarInitial(domain/ui)·색표는 AvatarTints.PALETTE(component) **한 곳** — 팀·연락처·검색과 같은 5색.
        if (g.pending) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(c.surfaceMuted), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Schedule, null, tint = c.textHint, modifier = Modifier.size(18.dp))
            }
        } else {
            val (avBg, avFg) = AvatarTints.forName(g.name)
            val initial = AvatarInitial.brand(g.name)
            Box(Modifier.size(38.dp).clip(CircleShape).background(avBg), contentAlignment = Alignment.Center) {
                if (initial.isBlank()) {
                    // 글자를 못 뽑는 이름(기호뿐 등) — 「사람」 아이콘으로. 업체 하나를 가리키는 자리라 손잡기(협업 표식)가 아니다. (2026-10-17)
                    Icon(Icons.Filled.Person, null, tint = avFg, modifier = Modifier.size(18.dp))
                } else {
                    Text(initial, style = AppType.headline, fontWeight = FontWeight.ExtraBold, color = avFg)
                }
            }
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(g.name, style = AppType.headline, fontWeight = FontWeight.ExtraBold, color = if (g.pending) c.textSub else TossTextPrimary)
            Spacer(Modifier.height(2.dp))
            Text(
                (if (g.pending) "기다리는 현장 ${g.count}곳" else "함께한 현장 ${g.count}곳") +
                    (if (g.recentMs > 0L) " · 최근 ${DateTimeUtils.formatMonthDay(g.recentMs)}" else ""),
                style = AppType.caption, color = TossTextTertiary, fontWeight = FontWeight.Medium
            )
        }
        if (showWage) {
            Column(horizontalAlignment = Alignment.End) {
                Text("${g.wageSum}만원", style = AppType.label, fontWeight = FontWeight.ExtraBold, color = CollabPurple)
                Text("받은 일당", style = AppType.caption, color = TossTextTertiary)
            }
            Spacer(Modifier.width(8.dp))
        }
        Text("›", style = AppType.title, fontWeight = FontWeight.Normal, color = TossTextTertiary)
    }
}
