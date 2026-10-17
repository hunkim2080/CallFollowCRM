package com.detailline.callfollowcrm.presentation.screen.sharedsite

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.detailline.callfollowcrm.CallFollowCrmApplication
import com.detailline.callfollowcrm.domain.collab.CollabPartnerGroups
import com.detailline.callfollowcrm.domain.collab.SettlementStatement
import com.detailline.callfollowcrm.presentation.theme.AppShape
import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.theme.AppType
import com.detailline.callfollowcrm.util.RecordShot
import com.detailline.callfollowcrm.util.SettlementShot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 🧾 **「정산서 이미지로 저장·공유」** — 업체 내역(openPartner) 위쪽 버튼 + 달 고르기 + 미리보기 창. (2026-10-17 사장님)
 *
 *   누르면: **「어느 달 뽑을까요?」**([SettlementMonthPickDialog] — 완료 건이 있는 달마다 체크 줄, 기본은 최근 달 하나, 여러 달 가능,
 *   「전체 선택/해제」, 하나도 안 고르면 만들기 비활성) → [정산서 만들기] → 고른 달의 완료 건으로 서류 내용을 만들고
 *   ([SettlementStatement.of] 의 `months`) → 그림으로 그려([SettlementShot], IO 스레드) → 미리보기 창 → 「갤러리에 저장」/「공유」.
 *   달 목록·합계는 [SettlementStatement.pickableMonths](= `CollabPartnerGroups.byMonth` 위) — 달 머리의 숫자와 같다.
 *
 *   **여러 장**(2026-10-17 "A4처럼"): 완료 건이 [SettlementStatement.ROWS_PER_PAGE] 를 넘으면 장이 여럿 — 미리보기는 세로로 쭉,
 *   저장은 장마다 파일 하나(「정산서_업체_기간_1-2」), 공유는 여러 장을 한 번에(ACTION_SEND_MULTIPLE).
 *
 *   §13: ① 저장·공유가 실패하면 **화면에 말한다**(토스트 — 몇 장 중 몇 장인지). ③ 만드는 동안·저장하는 동안 **연타 막기**(busy/saving).
 *   완료 건이 0이면 달 고르기 창도 안 열고 이유를 말한다.
 *
 *   이 파일이 따로 있는 이유: SharedSiteScreen.kt 는 size_guard 가 더 못 키우게 한다(§12-F — 잎은 옆 파일로).
 *   값은 전부 토큰(AppType · AppShape · AppTheme.colors) — style_guard 0.
 */
@Composable
internal fun SettlementStatementButton(partner: CollabPartnerGroups.Group) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { (context.applicationContext as CallFollowCrmApplication).container.preferences }
    val c = AppTheme.colors

    var busy by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<Pair<SettlementStatement.Doc, List<Bitmap>>?>(null) }
    // 고를 수 있는 달(완료 건 있는 달만) — 셈은 SettlementStatement 한 곳. 완료 건 수는 그 합.
    val options = remember(partner.sites) { SettlementStatement.pickableMonths(partner.sites) }
    val doneCount = remember(options) { options.sumOf { it.count } }

    // 꽉 찬 보라 버튼 — 이 화면 톤(협업)에 맞춘 진짜 '액션' 버튼. (2026-10-17 사장님 "버튼이 앱이랑 달라 보인다")
    val canMake = !busy && doneCount > 0
    Box(
        Modifier.fillMaxWidth().clip(AppShape.md)
            .background(if (canMake) CollabPurple else c.surfaceMuted)
            .clickable(enabled = !busy) {
                if (busy) return@clickable   // 연타 막기 — 두 번 만들지 않는다
                if (doneCount == 0) {
                    android.widget.Toast.makeText(context, "완료된 현장이 있어야 정산서를 만들 수 있어요", android.widget.Toast.LENGTH_SHORT).show()
                    return@clickable
                }
                picking = true
            }
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Icon(
                Icons.Filled.Description, null,
                tint = if (canMake) androidx.compose.ui.graphics.Color.White else c.textHint,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(if (busy) "정산서 만드는 중…" else "정산서 이미지 저장·공유",
                style = AppType.label, fontWeight = FontWeight.ExtraBold,
                color = if (canMake) androidx.compose.ui.graphics.Color.White else c.textHint)
        }
    }
    Spacer(Modifier.height(6.dp))
    Text(
        when {
            doneCount == 0 -> "완료된 현장이 생기면 만들 수 있어요"
            options.size > 1 -> "완료된 현장 ${doneCount}건 · 뽑을 달을 골라요"
            else -> "완료된 현장 ${doneCount}건 · ${options.first().label}"
        },
        style = AppType.caption, color = c.textHint, modifier = Modifier.padding(start = 2.dp)
    )

    if (picking) {
        SettlementMonthPickDialog(
            options = options,
            busy = busy,
            onDismiss = { if (!busy) picking = false },   // 만드는 중엔 닫히지 않는다 — 결과(미리보기/토스트)가 올 때까지
            onMake = { months ->
                if (busy) return@SettlementMonthPickDialog   // 연타 막기
                busy = true
                scope.launch {
                    val doc = SettlementStatement.of(
                        partnerName = partner.name,
                        sites = partner.sites,
                        issuerName = prefs.bizName,
                        issuerOwner = prefs.bizOwner,
                        issuerPhone = prefs.displayPhone,
                        issuedAtMs = System.currentTimeMillis(),
                        months = months
                    )
                    val pages = withContext(Dispatchers.IO) { runCatching { SettlementShot.render(context, doc) }.getOrNull() }
                    busy = false
                    if (pages.isNullOrEmpty()) {
                        android.widget.Toast.makeText(context, "정산서 이미지를 만들지 못했어요", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        picking = false
                        preview = doc to pages
                    }
                }
            }
        )
    }

    preview?.let { (doc, pages) ->
        SettlementPreviewDialog(doc, pages, onDismiss = { preview = null })
    }
}

/** 장 수만 미리 세려고 쓰는 빈 줄 — 그림을 그리기 전에 「N장」을 버튼에 적기 위해. 내용은 안 쓴다. */
private val PLACEHOLDER_LINE = SettlementStatement.Line(dateMs = 0L, site = "", siteSub = null, wageWon = 0L)

/**
 * 「어느 달 뽑을까요?」 — 완료 건이 있는 달마다 체크 줄(「2026년 10월 · 2곳 · 50만원」), 최신 달이 위.
 *   기본 = 가장 최근 달 하나([SettlementStatement.defaultPick]). 여러 달 가능. 「전체 선택/해제」 한 줄.
 *   하나도 안 고르면 [정산서 만들기] 비활성. 고른 상태는 이 창이 들고 있다(`remember` — 창을 닫으면 잊는다, 다음엔 다시 최근 달).
 *   만드는 중([busy])엔 버튼이 「만드는 중…」으로 잠기고 창도 안 닫힌다(§13③).
 */
@Composable
private fun SettlementMonthPickDialog(
    options: List<SettlementStatement.MonthOption>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onMake: (Set<String>) -> Unit
) {
    val c = AppTheme.colors
    var picked by remember(options) { mutableStateOf(SettlementStatement.defaultPick(options)) }
    val allPicked = picked.size == options.size && options.isNotEmpty()
    val pickedCount = remember(picked, options) { options.filter { it.ym in picked }.sumOf { it.count } }
    val pageCount = remember(pickedCount) { SettlementStatement.paginate(List(pickedCount) { PLACEHOLDER_LINE }).size }
    val canMake = picked.isNotEmpty() && !busy

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth(0.94f).clip(AppShape.lg).background(c.surface).padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("어느 달 뽑을까요?", style = AppType.headline, fontWeight = FontWeight.ExtraBold, color = c.text, modifier = Modifier.weight(1f))
                Text("닫기", style = AppType.label, fontWeight = FontWeight.Bold, color = c.textHint,
                    modifier = Modifier.clickable(enabled = !busy) { onDismiss() }.padding(6.dp))
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("완료된 현장이 있는 달만 보여요", style = AppType.caption, color = c.textHint, modifier = Modifier.weight(1f))
                Text(if (allPicked) "전체 해제" else "전체 선택", style = AppType.caption, fontWeight = FontWeight.Bold, color = c.primary,
                    modifier = Modifier.clip(AppShape.sm).clickable(enabled = !busy) {
                        picked = if (allPicked) emptySet() else options.map { it.ym }.toSet()
                    }.padding(horizontal = 6.dp, vertical = 4.dp))
            }
            Spacer(Modifier.height(6.dp))
            // 달이 쌓이면 길어진다 — 창 안에서 스크롤.
            Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                options.forEach { opt ->
                    val on = opt.ym in picked
                    Row(
                        Modifier.fillMaxWidth().clip(AppShape.sm)
                            .clickable(enabled = !busy) { picked = if (on) picked - opt.ym else picked + opt.ym }
                            .padding(horizontal = 6.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(22.dp).clip(AppShape.sm)
                                .background(if (on) c.primary else c.surface)
                                .border(1.5.dp, if (on) c.primary else c.lineCard, AppShape.sm),
                            contentAlignment = Alignment.Center
                        ) {
                            if (on) Icon(Icons.Default.Check, null, tint = c.textOnPrimary, modifier = Modifier.size(15.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(opt.label, style = AppType.label, fontWeight = FontWeight.Bold,
                            color = if (on) c.text else c.textSub, modifier = Modifier.weight(1f))
                        Text("${opt.count}곳 · ${opt.wageMan}만원", style = AppType.caption, color = c.textHint)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier.fillMaxWidth().clip(AppShape.md).background(if (canMake) c.primary else c.surfaceMuted)
                    .clickable(enabled = canMake) { onMake(picked) }
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    when {
                        busy -> "정산서 만드는 중…"
                        picked.isEmpty() -> "달을 골라주세요"
                        pageCount > 1 -> "${pickedCount}건 · ${pageCount}장으로 정산서 만들기"
                        else -> "${pickedCount}건으로 정산서 만들기"
                    },
                    style = AppType.label, fontWeight = FontWeight.Bold, color = if (canMake) c.textOnPrimary else c.textHint
                )
            }
        }
    }
}

/**
 * 미리보기 — 장을 세로로 쭉 + 「갤러리에 저장」 · 「공유」.
 *   저장은 [RecordShot.save] 를 **장마다**(파일 이름 `_1-2` 식), 공유는 [RecordShot.shareAll](여러 장 한 번에). 둘 다 그대로 재사용.
 */
@Composable
private fun SettlementPreviewDialog(doc: SettlementStatement.Doc, pages: List<Bitmap>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = AppTheme.colors
    var saving by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    val total = pages.size
    val names = remember(doc, total) { (1..total).map { SettlementShot.fileName(doc, page = it, total = total) } }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth(0.94f).clip(AppShape.lg).background(c.surface).padding(16.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (total > 1) "일당 정산서 · ${total}장" else "일당 정산서",
                        style = AppType.headline, fontWeight = FontWeight.ExtraBold, color = c.text)
                    // 어느 달 서류인지 — 기간 글자는 서류 머리와 같은 것(SettlementStatement.period 한 곳).
                    doc.period?.let { Text(it, style = AppType.caption, color = c.textHint) }
                }
                Text("닫기", style = AppType.label, fontWeight = FontWeight.Bold, color = c.textHint,
                    modifier = Modifier.clickable { onDismiss() }.padding(6.dp))
            }
            Spacer(Modifier.height(10.dp))
            // 서류는 길 수 있다 — 창 안에서 스크롤. 여러 장이면 세로로 이어서(장 사이 회색 틈).
            Column(
                Modifier.fillMaxWidth().weight(1f, fill = false).clip(AppShape.sm)
                    .background(c.surfaceMuted)
                    .border(1.dp, c.surfaceMuted, AppShape.sm)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                pages.forEachIndexed { i, bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = if (total > 1) "일당 정산서 미리보기 ${i + 1}/$total" else "일당 정산서 미리보기",
                        modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.weight(1f).clip(AppShape.md).background(c.surfaceMuted)
                        .clickable(enabled = !saving) {
                            if (saving) return@clickable   // 연타 막기 — 같은 그림이 두 번 저장되지 않게
                            saving = true
                            scope.launch {
                                // 장마다 저장 — 순서대로. 하나라도 못 하면 몇 장 중 몇 장인지 말한다(§13①).
                                var ok = 0
                                pages.forEachIndexed { i, bmp ->
                                    if (RecordShot.save(context, bmp, names[i]) != null) ok++
                                }
                                saving = false
                                when {
                                    ok == 0 -> android.widget.Toast.makeText(context, "갤러리에 저장하지 못했어요 — [공유]로 보내보세요", android.widget.Toast.LENGTH_SHORT).show()
                                    ok < total -> android.widget.Toast.makeText(context, "${total}장 중 ${ok}장만 저장됐어요 — 다시 눌러보거나 [공유]로 보내보세요", android.widget.Toast.LENGTH_LONG).show()
                                    else -> {
                                        saved = true
                                        android.widget.Toast.makeText(context, if (total > 1) "갤러리에 ${total}장 저장했어요" else "갤러리에 저장했어요", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        when { saving -> "저장 중…"; saved -> "저장됨 ✓"; total > 1 -> "갤러리에 ${total}장 저장"; else -> "갤러리에 저장" },
                        style = AppType.label, fontWeight = FontWeight.Bold, color = c.text
                    )
                }
                Box(
                    Modifier.weight(1f).clip(AppShape.md).background(c.primary)
                        .clickable {
                            if (!RecordShot.shareAll(context, pages, names)) {
                                android.widget.Toast.makeText(context, "공유 창을 열지 못했어요", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (total > 1) "${total}장 공유" else "공유", style = AppType.label, fontWeight = FontWeight.Bold, color = c.textOnPrimary)
                }
            }
        }
    }
}
