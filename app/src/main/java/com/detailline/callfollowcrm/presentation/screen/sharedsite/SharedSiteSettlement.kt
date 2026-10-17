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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
 * 🧾 **「정산서 이미지로 저장·공유」** — 업체 내역(openPartner) 위쪽 버튼 + 미리보기 창. (2026-10-17 사장님)
 *
 *   누르면: 완료 건으로 서류 내용을 만들고([SettlementStatement] 한 곳) → 그림으로 그려([SettlementShot], IO 스레드)
 *   → 미리보기 창 → 「갤러리에 저장」([RecordShot.save] 장마다) / 「공유」([RecordShot.shareAll] — 여러 장이면 한 번에).
 *
 *   **여러 장**(2026-10-17 "A4처럼"): 완료 건이 [SettlementStatement.ROWS_PER_PAGE] 를 넘으면 장이 여럿 — 미리보기는 세로로 쭉,
 *   저장은 장마다 파일 하나(「정산서_업체_날짜_1-2」), 공유는 여러 장을 한 번에(ACTION_SEND_MULTIPLE).
 *
 *   §13: ① 저장·공유가 실패하면 **화면에 말한다**(토스트 — 몇 장 중 몇 장인지). ③ 만드는 동안·저장하는 동안 **연타 막기**(busy/saving).
 *   완료 건이 0이면 그림을 안 만들고 이유를 말한다.
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
    var preview by remember { mutableStateOf<Pair<SettlementStatement.Doc, List<Bitmap>>?>(null) }
    val doneCount = remember(partner.sites) { SettlementStatement.eligible(partner.sites).size }
    val pageCount = remember(doneCount) { SettlementStatement.paginate(List(doneCount) { PLACEHOLDER_LINE }).size }

    Row(
        Modifier.fillMaxWidth().clip(AppShape.md).background(c.surface)
            .border(1.dp, c.surfaceMuted, AppShape.md)
            .clickable(enabled = !busy) {
                if (busy) return@clickable   // 연타 막기 — 두 번 만들지 않는다
                if (doneCount == 0) {
                    android.widget.Toast.makeText(context, "완료된 현장이 있어야 정산서를 만들 수 있어요", android.widget.Toast.LENGTH_SHORT).show()
                    return@clickable
                }
                busy = true
                scope.launch {
                    val doc = SettlementStatement.of(
                        partnerName = partner.name,
                        sites = partner.sites,
                        issuerName = prefs.bizName,
                        issuerOwner = prefs.bizOwner,
                        issuerPhone = prefs.displayPhone,
                        issuedAtMs = System.currentTimeMillis()
                    )
                    val pages = withContext(Dispatchers.IO) { runCatching { SettlementShot.render(context, doc) }.getOrNull() }
                    busy = false
                    if (pages.isNullOrEmpty()) {
                        android.widget.Toast.makeText(context, "정산서 이미지를 만들지 못했어요", android.widget.Toast.LENGTH_SHORT).show()
                    } else preview = doc to pages
                }
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(if (busy) "정산서 만드는 중…" else "정산서 이미지로 저장·공유",
                style = AppType.label, fontWeight = FontWeight.ExtraBold, color = if (busy) c.textHint else c.text)
            Text(
                when {
                    doneCount == 0 -> "완료된 현장이 생기면 만들 수 있어요"
                    pageCount > 1 -> "완료된 현장 ${doneCount}건을 ${pageCount}장의 정산서로 만들어요"
                    else -> "완료된 현장 ${doneCount}건을 한 장의 정산서로 만들어요"
                },
                style = AppType.caption, color = c.textHint
            )
        }
        Text("›", style = AppType.headline, fontWeight = FontWeight.Bold, color = c.textHint)
    }

    preview?.let { (doc, pages) ->
        SettlementPreviewDialog(doc, pages, onDismiss = { preview = null })
    }
}

/** 장 수만 미리 세려고 쓰는 빈 줄 — 그림을 그리기 전에 「N장」을 버튼 부제에 적기 위해. 내용은 안 쓴다. */
private val PLACEHOLDER_LINE = SettlementStatement.Line(dateMs = 0L, site = "", siteSub = null, wageWon = 0L)

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
                Text(if (total > 1) "일당 정산서 · ${total}장" else "일당 정산서",
                    style = AppType.headline, fontWeight = FontWeight.ExtraBold, color = c.text, modifier = Modifier.weight(1f))
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
