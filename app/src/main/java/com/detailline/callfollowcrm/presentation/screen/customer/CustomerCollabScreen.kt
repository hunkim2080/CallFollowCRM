package com.detailline.callfollowcrm.presentation.screen.customer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.detailline.callfollowcrm.ai.SharedSiteRepository
import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import com.detailline.callfollowcrm.data.local.entity.CustomerNoteEntity
import com.detailline.callfollowcrm.domain.collab.CollabPartnerGroups
import com.detailline.callfollowcrm.domain.collab.CollabTogether
import com.detailline.callfollowcrm.domain.collab.SettlementStatement
import com.detailline.callfollowcrm.domain.ui.AvatarInitial
import com.detailline.callfollowcrm.presentation.component.NoteList
import com.detailline.callfollowcrm.presentation.component.TossCard
import com.detailline.callfollowcrm.presentation.component.TossChip
import com.detailline.callfollowcrm.presentation.screen.sharedsite.CollabMonthHeader
import com.detailline.callfollowcrm.presentation.screen.sharedsite.CollabPillarRow
import com.detailline.callfollowcrm.presentation.screen.sharedsite.SettlementSheets
import com.detailline.callfollowcrm.presentation.screen.sharedsite.collabStatusTagQuiet
import com.detailline.callfollowcrm.presentation.screen.sharedsite.rememberMonthFold
import com.detailline.callfollowcrm.presentation.theme.AppShape
import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.theme.AppType
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.util.keyboardPadding
import com.detailline.callfollowcrm.util.AddressExtractor
import com.detailline.callfollowcrm.util.DateTimeUtils
import com.detailline.callfollowcrm.util.PhoneDialer
import com.detailline.callfollowcrm.util.PhoneNumberFormatter
import com.detailline.callfollowcrm.util.SmsIntentHelper

/**
 * 🤝 **협업 사장 전용 고객 상세** — 보통 고객 화면 대신 뜬다(시공막내로 한 번이라도 같이 일한 사람).
 *   판정·받음/준 묶기는 [CollabTogether] 한 곳. 화면 조각은 전부 **기존 공용 재사용**(§12):
 *   날짜 기둥 줄 `CollabPillarRow`·달 머리 `CollabMonthHeader`·달 접기 `rememberMonthFold`·정산서 `SettlementStatementButton`·
 *   메모 `NoteList`/`CdFoldRow`/`MemoPreviewLine`·칩 `TossChip`·전화 `PhoneDialer`·문자 `SmsIntentHelper`·색 토큰(받음=done·줌=caution·협업=category).
 *
 *   구성(사장님 확정 프로토 ②, 2026-10-27): 전화/문자 → **메모(최상단 카드)** → 함께한 현장(양방향·필터·달 접기·정산서).
 *   시공예약·금액·현장사진·계약금·분류 **없음**(협업 사장은 보통 고객 기능을 안 쓴다 — 사장님).
 */
/**
 * 정산서용 묶음 — 이 사장의 현장 **전부**를 한 묶음으로 넣는다(상호가 공유마다 갈려도 안 쪼갬).
 *   `CollabPartnerGroups.group().firstOrNull()` 은 번호+이름으로 갈라 묶음 하나만 줘서 옛 달이 빠졌다. 비면 null.
 *   정산서 버튼은 `name`·`sites` 만 쓴다(달 고르기·발행). recentMs 외 나머지는 안 쓰여 0.
 */
private fun settleGroupOf(name: String, sites: List<SharedSiteRepository.SharedSite>): CollabPartnerGroups.Group? {
    if (sites.isEmpty()) return null
    return CollabPartnerGroups.Group(
        key = "settle", phoneKey = "", name = name,
        count = sites.size, recentMs = sites.maxOf { it.scheduledAtMs }, wageSum = 0, sites = sites
    )
}

@Composable
internal fun CustomerCollabScreen(
    customer: CustomerEntity,
    view: CollabTogether.View,
    topPadding: Dp,
    notes: List<CustomerNoteEntity>,
    memoExpanded: Boolean,
    onToggleMemo: () -> Unit,
    onAddNote: (String) -> Unit,
    onEditNote: (CustomerNoteEntity) -> Unit,
    onTogglePin: (CustomerNoteEntity) -> Unit,
    onDeleteNote: (CustomerNoteEntity) -> Unit,
    onEditName: () -> Unit
) {
    val c = AppTheme.colors
    val ctx = LocalContext.current
    val phone = customer.phoneNumber
    val name = customer.name?.takeIf { it.isNotBlank() } ?: PhoneNumberFormatter.format(phone)

    // 방향 필터(null = 전체) — 받음/줌 둘 다 있을 때만 칩을 보인다.
    var filter by remember { mutableStateOf<CollabTogether.Dir?>(null) }
    val shown = remember(view, filter) {
        if (filter == null) view.entries else view.entries.filter { it.dir == filter }
    }
    val months = remember(shown) { CollabTogether.monthsOf(shown) }
    // 달 접기는 **전체 달** 기준으로(필터 바꿔도 최근 달이 계속 펼쳐지게). 판단은 rememberMonthFold → MonthFold 한 곳.
    val foldMonths = remember(view) {
        CollabTogether.monthsOf(view.entries).map { CollabPartnerGroups.Month(it.ym, it.entries.size, 0, emptyList()) }
    }
    val fold = rememberMonthFold(foldMonths)

    // 정산서는 방향별 2장(사장님 2026-10-27) — 받을/줄 안 섞음. 완료 건(일당 있는) 있는 방향만.
    //   ⚠️ 이 사장의 **모든** 현장을 한 묶음으로 넣는다 — group().firstOrNull() 로 묶음 하나만 쓰면 상호가 조금씩
    //   달라 쪼개진 묶음(옛 달)이 빠진다(2026-10-27 사장님 "7·8월이 정산서에 안 떠"). 받음/준 각각 전부.
    val recvGroup = remember(view, name) { settleGroupOf(name, view.receivedSites) }
    val giveGroup = remember(view, name) { settleGroupOf(name, view.givenSites) }
    val recvHasDone = remember(view) { SettlementStatement.eligible(view.receivedSites).isNotEmpty() }
    val giveHasDone = remember(view) { SettlementStatement.eligible(view.givenSites).isNotEmpty() }
    val recvDoneCount = remember(view) { SettlementStatement.pickableMonths(view.receivedSites).sumOf { it.count } }
    val giveDoneCount = remember(view) { SettlementStatement.pickableMonths(view.givenSites).sumOf { it.count } }
    // ②안(사장님 2026-10-28): 큰 버튼 2개 대신 "정산서 ›" 한 줄 → 받을/줄 고르기 시트 → 달 고르기(SettlementSheets).
    var settleChoose by remember { mutableStateOf(false) }
    var activeGroup by remember { mutableStateOf<CollabPartnerGroups.Group?>(null) }
    var settleOpen by remember { mutableStateOf(false) }

    Column(
        Modifier
            .padding(top = topPadding)
            .fillMaxSize()
            .keyboardPadding()
            .background(TossGrayBg)
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── 1. 이름 + 전화/문자
        TossCard {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(54.dp).clip(CircleShape).background(c.categoryBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(AvatarInitial.of(name), style = AppType.title, fontWeight = FontWeight.ExtraBold, color = c.categoryText)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                name, style = AppType.headline, fontWeight = FontWeight.ExtraBold, color = c.text,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(Modifier.width(6.dp))
                            Box(Modifier.clip(AppShape.pill).background(c.categoryBg).padding(horizontal = 7.dp, vertical = 2.dp)) {
                                Text("협업 사장님", style = AppType.micro, color = c.categoryText)
                            }
                        }
                        Spacer(Modifier.height(3.dp))
                        val sinceYm = view.entries.minByOrNull { it.site.scheduledAtMs }?.let { SharedSiteRepository.ymOf(it.site.scheduledAtMs) }
                        val since = sinceYm?.let { " · 함께 ${SharedSiteRepository.monthLabel(it)}부터" }.orEmpty()
                        Text(PhoneNumberFormatter.format(phone) + since, style = AppType.caption, color = c.textSub)
                    }
                    Spacer(Modifier.width(8.dp))
                    // 이름 바꾸기 — 보통 고객 화면 머리의 '수정'과 같은 창(onEditName → nameDialog). (2026-10-28 사장님 "이름 바꿀 데가 없더라")
                    Row(
                        Modifier.clip(AppShape.sm).background(c.surfaceMuted).clickable { onEditName() }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Edit, null, tint = c.textHint, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("수정", style = AppType.micro, fontWeight = FontWeight.Bold, color = c.textHint)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row {
                    Box(
                        Modifier.weight(1f).height(52.dp).clip(AppShape.md).background(c.primary)
                            .clickable { PhoneDialer.open(ctx, phone) },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Phone, null, tint = c.textOnPrimary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("전화", style = AppType.label, fontWeight = FontWeight.ExtraBold, color = c.textOnPrimary)
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(
                        Modifier.weight(1f).height(52.dp).clip(AppShape.md).background(c.surfaceMuted)
                            .clickable { SmsIntentHelper.openSmsCompose(ctx, phone, "") },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("문자", style = AppType.label, fontWeight = FontWeight.ExtraBold, color = c.textSub)
                    }
                }
            }
        }

        // ── 2. 이 사장님 메모 (최상단 카드 — 전화 바로 밑)
        TossCard(onClick = if (memoExpanded) null else onToggleMemo) {
            Column {
                CdFoldRow(
                    Icons.Filled.Person, "이 사장님 메모", notes.size, memoExpanded,
                    emptyAction = if (notes.isEmpty()) "＋ 적기" else null
                ) { onToggleMemo() }
                if (!memoExpanded) MemoPreviewLine(notes)
                if (memoExpanded) {
                    Spacer(Modifier.height(8.dp))
                    NoteList(
                        notes = notes,
                        onAdd = onAddNote,
                        onEdit = onEditNote,
                        onTogglePin = onTogglePin,
                        onDelete = onDeleteNote,
                        placeholder = "예) 믿을만하고 시간 잘 지킴, 화장실 전문, 일당 18만"
                    )
                }
            }
        }

        // ── 3. 함께한 현장 (양방향 · 필터 · 달 접기 · 정산서)
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // 머리: 곳수 + 받은/준 일당 + 최근  /  오른쪽 작은 "정산서 ›" (큰 버튼 2개 대신 — 사장님 ②안)
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("함께한 현장 ${view.total}곳", style = AppType.headline, fontWeight = FontWeight.ExtraBold, color = c.text)
                    Spacer(Modifier.height(3.dp))
                    val parts = buildList {
                        if (view.receivedWageMan > 0) add("받은 일당 ${view.receivedWageMan}만원")
                        if (view.givenWageMan > 0) add("준 일당 ${view.givenWageMan}만원")
                        if (view.recentMs > 0L) add("최근 ${DateTimeUtils.formatMonthDay(view.recentMs)}")
                    }
                    if (parts.isNotEmpty()) Text(parts.joinToString(" · "), style = AppType.caption, color = c.textSub)
                }
                if (recvHasDone || giveHasDone) {
                    Spacer(Modifier.width(8.dp))
                    Row(
                        Modifier.clip(AppShape.md).background(c.categoryBg)
                            .clickable {
                                when {
                                    recvHasDone && giveHasDone -> settleChoose = true
                                    recvHasDone -> { activeGroup = recvGroup; settleOpen = true }
                                    else -> { activeGroup = giveGroup; settleOpen = true }
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Description, null, tint = c.categoryText, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("정산서 ›", style = AppType.caption, fontWeight = FontWeight.ExtraBold, color = c.categoryText)
                    }
                }
            }

            // 필터 칩 (받음·줌 둘 다 있을 때만)
            if (view.receivedCount > 0 && view.givenCount > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TossChip("전체 ${view.total}", filter == null, onClick = { filter = null })
                    TossChip("받음 ${view.receivedCount}", filter == CollabTogether.Dir.RECEIVED, onClick = { filter = CollabTogether.Dir.RECEIVED })
                    TossChip("내가 줌 ${view.givenCount}", filter == CollabTogether.Dir.GIVEN, onClick = { filter = CollabTogether.Dir.GIVEN })
                }
            }

            // 달별 — 최근 달만 펼침, 지난 달은 접힘(한 줄)
            Column(Modifier.fillMaxWidth()) {
                months.forEach { m ->
                    val open = fold.isOpen(m.ym)
                    val monthWage = m.entries.filter { it.site.progress == SharedSiteRepository.Progress.COMPLETED }
                        .sumOf { it.site.dailyWage ?: 0 }
                    val right = "${m.entries.size}곳" + if (monthWage > 0) " · ${monthWage}만" else ""
                    CollabMonthHeader(m.ym, right = right, open = open, onToggle = { fold.toggle(m.ym) })
                    if (open) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            m.entries.forEach { e ->
                                val two = AddressExtractor.twoLine(e.site.addr)
                                val line1 = if (two.first.isNotBlank()) two.first else com.detailline.callfollowcrm.ai.siteDisplayName(e.site)
                                val line2 = if (two.first.isNotBlank()) two.second else null
                                val dirTag = if (e.dir == CollabTogether.Dir.RECEIVED)
                                    Triple("받음", c.doneBg, c.doneText)
                                else Triple("내가 줌", c.cautionBg, c.cautionText)
                                CollabPillarRow(
                                    scheduledAtMs = e.site.scheduledAtMs,
                                    line1 = line1,
                                    line2 = line2,
                                    line2Color = c.textHint,
                                    line2Weight = FontWeight.Medium,
                                    status = collabStatusTagQuiet(e.site),
                                    wageMan = e.site.dailyWage,
                                    onClick = {},
                                    dirTag = dirTag
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }

    // 정산서 방향 고르기(받을/줄 둘 다 있을 때) → 고른 방향으로 SettlementSheets(달 고르기+미리보기).
    if (settleChoose) {
        SettleDirectionDialog(
            recvCount = recvDoneCount,
            giveCount = giveDoneCount,
            onRecv = { settleChoose = false; activeGroup = recvGroup; settleOpen = true },
            onGive = { settleChoose = false; activeGroup = giveGroup; settleOpen = true },
            onDismiss = { settleChoose = false }
        )
    }
    activeGroup?.let { g ->
        SettlementSheets(g, settleOpen, { settleOpen = it }, onClosed = { activeGroup = null; settleOpen = false })
    }
}

/** 정산서 방향 고르기 — 받을/줄 둘 다 완료 건이 있을 때 "어느 걸 만들까요?". 완료 건 있는 방향만 보인다. */
@Composable
private fun SettleDirectionDialog(
    recvCount: Int,
    giveCount: Int,
    onRecv: () -> Unit,
    onGive: () -> Unit,
    onDismiss: () -> Unit
) {
    val c = AppTheme.colors
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(AppShape.lg).background(c.surface).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("어떤 정산서를 만들까요?", style = AppType.headline, fontWeight = FontWeight.ExtraBold, color = c.text)
            if (recvCount > 0) SettleDirOption("받을 일당 정산서", "내가 받을 일당 · 완료 ${recvCount}건", onRecv)
            if (giveCount > 0) SettleDirOption("줄 일당 정산서", "내가 줄 일당 · 완료 ${giveCount}건", onGive)
            Text(
                "취소", style = AppType.label, fontWeight = FontWeight.Bold, color = c.textHint,
                modifier = Modifier.align(Alignment.End).clip(AppShape.sm).clickable { onDismiss() }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun SettleDirOption(title: String, sub: String, onClick: () -> Unit) {
    val c = AppTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(AppShape.md).background(c.categoryBg).clickable { onClick() }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Description, null, tint = c.categoryText, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = AppType.label, fontWeight = FontWeight.ExtraBold, color = c.categoryText)
            Text(sub, style = AppType.micro, color = c.textSub)
        }
        Text("›", style = AppType.headline, fontWeight = FontWeight.ExtraBold, color = c.categoryText)
    }
}
