package com.detailline.callfollowcrm.presentation.component

import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.detailline.callfollowcrm.data.local.entity.CustomerNoteEntity
import com.detailline.callfollowcrm.presentation.theme.AppShape
import com.detailline.callfollowcrm.presentation.theme.AppSpace
import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.theme.AppType
import com.detailline.callfollowcrm.util.DateTimeUtils

/**
 * 📝 **쪽지 목록** — 손님 메모(👤)와 현장 메모(📍)가 **같은 얼굴**로 쓰는 부품. (2026-09-27 사장님)
 *
 * 사장님: "메모란에 적으니까 **언제 내가 작성했는지 모르겠고** 눈에 안 들어오는 것 같은데."
 *         "줄이 무한대로 늘어나면 화면이 너무 길어질 듯. 2줄? 3줄? 이상은 접혀 있는 게."
 *
 * 접는 기준(맡겨주셔서 정함):
 *   · 📌 **고정은 전부** 보인다 — 계좌·비번은 늘 봐야 하니 접으면 뜻이 없다.
 *   · 나머지는 **최신 3장**까지. 한 장이 두 줄쯤이니 3장 ≈ 여섯 줄 — 지금 메모칸 높이와 비슷하다.
 *     2장이면 「오늘·어제」뿐이라 앞뒤가 안 보이고, 4장부터는 카드가 화면 절반을 먹는다.
 *   · 한 쪽지 본문은 **2줄**까지. 길면 말줄임 — **그 쪽지를 누르면** 전부 펼쳐진다.
 *
 * 두 화면이 같은 부품을 쓰는 이유: 따로 만들면 또 어긋난다. (차수 셈이 세 번 어긋났던 것과 같은 자리)
 */
private const val COLLAPSED_COUNT = 3

@Composable
fun NoteList(
    notes: List<CustomerNoteEntity>,
    onAdd: (String) -> Unit,
    onEdit: (CustomerNoteEntity) -> Unit,
    onTogglePin: (CustomerNoteEntity) -> Unit,
    onDelete: (CustomerNoteEntity) -> Unit,
    placeholder: String = "들은 거 한 줄로 — 저장하면 시각이 붙어요",
    modifier: Modifier = Modifier
) {
    var input by remember { mutableStateOf("") }
    var expandedAll by remember(notes.size) { mutableStateOf(false) }
    var openNoteId by remember { mutableStateOf<Long?>(null) }
    var menuFor by remember { mutableStateOf<CustomerNoteEntity?>(null) }

    val pinned = notes.filter { it.pinned }
    val rest = notes.filterNot { it.pinned }
    val shownRest = if (expandedAll) rest else rest.take(COLLAPSED_COUNT)
    val hiddenCount = rest.size - shownRest.size

    Column(modifier.fillMaxWidth()) {
        if (notes.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().clip(AppShape.md)
                    .background(AppTheme.colors.surface)
            ) {
                (pinned + shownRest).forEachIndexed { i, n ->
                    if (i > 0) {
                        Box(
                            Modifier.fillMaxWidth().height(1.dp)
                                .background(AppTheme.colors.line)
                        )
                    }
                    NoteRow(
                        note = n,
                        open = openNoteId == n.id,
                        onTap = { openNoteId = if (openNoteId == n.id) null else n.id },
                        onLongPress = { menuFor = n }
                    )
                }
            }
            // 🗂️ 나머지는 접어 둔다 — 쌓일수록 화면이 끝없이 길어진다.
            if (hiddenCount > 0) {
                Text(
                    "지난 메모 ${hiddenCount}장 더 보기",
                    style = AppType.caption, fontWeight = FontWeight.Bold,
                    color = AppTheme.colors.primary,
                    modifier = Modifier.fillMaxWidth().clip(AppShape.sm)
                        .clickable { expandedAll = true }
                        .padding(vertical = 9.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            } else if (expandedAll && rest.size > COLLAPSED_COUNT) {
                Text(
                    "접기",
                    style = AppType.caption, fontWeight = FontWeight.Bold,
                    color = AppTheme.colors.textHint,
                    modifier = Modifier.fillMaxWidth().clip(AppShape.sm)
                        .clickable { expandedAll = false }
                        .padding(vertical = 9.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            Spacer(Modifier.height(AppSpace.s8))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            SheetTextField(
                input, { input = it },
                placeholder = placeholder,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(7.dp))
            val can = input.isNotBlank()
            Box(
                Modifier.clip(AppShape.md)
                    .background(if (can) AppTheme.colors.primary else AppTheme.colors.surfaceMuted)
                    .clickable(enabled = can) { onAdd(input.trim()); input = "" }
                    .padding(horizontal = 16.dp, vertical = 13.dp)
            ) {
                Text(
                    "적기", style = AppType.label, fontWeight = FontWeight.Bold,
                    color = if (can) androidx.compose.ui.graphics.Color.White
                    else AppTheme.colors.textHint
                )
            }
        }
        if (notes.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                "꾹 누르면 고치기 · 📌 고정 · 지우기",
                style = AppType.caption, color = AppTheme.colors.textHint,
                modifier = Modifier.padding(start = 2.dp)
            )
        }
    }

    menuFor?.let { n ->
        NoteActionSheet(
            note = n,
            onClose = { menuFor = null },
            onEdit = { menuFor = null; onEdit(n) },
            onTogglePin = { menuFor = null; onTogglePin(n) },
            onDelete = { menuFor = null; onDelete(n) }
        )
    }
}

/** 쪽지 한 줄 — 왼쪽에 **언제**, 오른쪽에 **무슨 말**. 시각이 주인공이라 왼쪽에 둔다. */
@Composable
private fun NoteRow(
    note: CustomerNoteEntity,
    open: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth()
            .background(if (note.pinned) AppTheme.colors.cautionBg else androidx.compose.ui.graphics.Color.Transparent)
            .noteTapAndHold(onTap = onTap, onLongPress = onLongPress)
            .padding(horizontal = 11.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            whenLabel(note),
            style = AppType.caption, fontWeight = FontWeight.Bold,
            color = if (note.pinned) AppTheme.colors.cautionText else AppTheme.colors.textHint,
            modifier = Modifier.width(58.dp)
        )
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            if (note.source == CustomerNoteEntity.SOURCE_CALL) {
                Text(
                    "통화 중", style = AppType.micro,
                    color = AppTheme.colors.primary,
                    modifier = Modifier.clip(AppShape.sm)
                        .background(AppTheme.colors.primaryBg)
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                )
                Spacer(Modifier.height(3.dp))
            }
            Text(
                note.body,
                style = AppType.body, color = AppTheme.colors.text,
                maxLines = if (open) Int.MAX_VALUE else 2,
                overflow = if (open) TextOverflow.Clip else TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 언제 적었나 — 두 줄로 짧게.
 *   · 오늘이면 「오늘 / 11:20」, 다른 날이면 「9/25 / 14:08」
 *   · 옛 메모(시각 모름)는 「예전에 / 적음」
 */
private fun whenLabel(n: CustomerNoteEntity): String {
    if (n.pinned) return "고정"
    if (n.createdAt <= 0L) return "예전에\n적음"
    val today = DateTimeUtils.startOfDay(System.currentTimeMillis())
    val head = if (n.createdAt >= today) "오늘" else DateTimeUtils.formatMonthDay(n.createdAt)
    return head + "\n" + DateTimeUtils.formatTime(n.createdAt)
}

/**
 * 톡 치면 펼치고, **꾹 누르면** 메뉴. 한 군데서 정의해 두 손짓이 서로 안 잡아먹게 한다.
 *   (`clickable` 과 `pointerInput` 을 겹쳐 놓으면 길게 눌러도 탭으로 먹히는 일이 있다)
 */
private fun Modifier.noteTapAndHold(
    onTap: () -> Unit,
    onLongPress: () -> Unit
): Modifier = this.then(
    Modifier.pointerInput(Unit) {
        detectTapGestures(
            onTap = { onTap() },
            onLongPress = { onLongPress() }
        )
    }
)

/** 꾹 누르면 뜨는 창 — 고치기 · 📌 고정 · 지우기. 세 가지뿐이라 시트보다 창이 빠르다. */
@Composable
private fun NoteActionSheet(
    note: CustomerNoteEntity,
    onClose: () -> Unit,
    onEdit: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onClose,
        containerColor = AppTheme.colors.surface,
        tonalElevation = 0.dp,
        title = {
            Text(
                note.body.take(40) + if (note.body.length > 40) "…" else "",
                style = AppType.headline, color = AppTheme.colors.text
            )
        },
        text = {
            Column {
                NoteAction(if (note.pinned) "고정 풀기" else "맨 위에 고정", onTogglePin)
                NoteAction("고쳐 적기", onEdit)
                NoteAction("지우기", onDelete, danger = true)
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onClose) {
                Text("닫기", color = AppTheme.colors.textHint, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun NoteAction(label: String, onClick: () -> Unit, danger: Boolean = false) {
    Text(
        label,
        style = AppType.body, fontWeight = FontWeight.Bold,
        color = if (danger) AppTheme.colors.unpaid else AppTheme.colors.text,
        modifier = Modifier.fillMaxWidth().clip(AppShape.sm)
            .clickable { onClick() }
            .padding(vertical = 13.dp, horizontal = 4.dp)
    )
}

/** 쪽지 고쳐 적기 — **적은 시각은 안 바꾼다.** 언제 들은 얘기인지가 정보다. */
@Composable
fun NoteEditDialog(
    note: CustomerNoteEntity,
    onClose: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember(note.id) { mutableStateOf(note.body) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onClose,
        containerColor = AppTheme.colors.surface,
        tonalElevation = 0.dp,
        title = { Text("메모 고치기", style = AppType.headline, color = AppTheme.colors.text) },
        text = {
            Column {
                Text(
                    whenLabel(note).replace("\n", " "),
                    style = AppType.caption, color = AppTheme.colors.textHint
                )
                Spacer(Modifier.height(8.dp))
                SheetTextField(text, { text = it }, placeholder = "내용", modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { onSave(text) }) {
                Text("저장", color = AppTheme.colors.primary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onClose) {
                Text("취소", color = AppTheme.colors.textHint, fontWeight = FontWeight.Bold)
            }
        }
    )
}
