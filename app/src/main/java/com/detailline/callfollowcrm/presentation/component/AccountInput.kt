package com.detailline.callfollowcrm.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossDivider
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary

/**
 * 계좌 입력 공용 — 자동 하이픈 + 은행 클릭 선택.
 *   사업자정보·협업 일당 지급계좌 등 여러 화면에서 같은 UX 로 쓰려고 한 곳에 모음. (2026-06-14)
 */

/** 계좌번호 자동 하이픈 — 은행마다 형식이 달라 4자리마다 끊어 가독성만 높임 (숫자만, 최대 16자리). */
fun formatAccountNo(raw: String): String =
    raw.filter { it.isDigit() }.take(16).chunked(4).joinToString("-")

/** 자주 쓰는 은행/금융사 — 클릭 선택용. 위에서부터 사용 빈도 순. */
val BANK_LIST: List<String> = listOf(
    "국민은행", "신한은행", "우리은행", "하나은행", "NH농협은행", "농협(단위·축협)",
    "카카오뱅크", "토스뱅크", "케이뱅크", "IBK기업은행",
    "새마을금고", "우체국", "신협", "수협",
    "부산은행", "iM뱅크(대구)", "경남은행", "광주은행", "전북은행", "제주은행",
    "SC제일은행", "씨티은행", "산업은행(KDB)", "저축은행"
)

/**
 * 은행 클릭 선택 칸 — 탭하면 검색칸 + 칩 목록이 펼쳐짐. 한 글자만 쳐도 필터(이름 contains).
 *   목록에 없으면 '직접 입력' 칩으로 친 글자를 그대로 은행명으로. (오타 방지)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BankPickerField(
    label: String,
    bank: String,
    open: Boolean,
    query: String,
    onToggle: () -> Unit,
    onQuery: (String) -> Unit,
    onPick: (String) -> Unit
) {
    SheetFieldLabel(label)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(TossGrayBg)
            .border(1.5.dp, TossDivider, RoundedCornerShape(12.dp))
            .clickable { onToggle() }
            .padding(horizontal = 14.dp, vertical = 13.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                bank.ifBlank { "은행을 선택하세요" },
                fontSize = 15.sp,
                color = if (bank.isBlank()) TossTextTertiary else TossTextPrimary,
                modifier = Modifier.weight(1f)
            )
            Text(if (open) "▲" else "▼", fontSize = 12.sp, color = TossTextTertiary)
        }
    }
    if (open) {
        Spacer(Modifier.height(8.dp))
        SheetTextField(
            value = query,
            onValueChange = onQuery,
            placeholder = "한 글자만 쳐도 돼요 (예: 국)",
            keyboardType = KeyboardType.Text
        )
        Spacer(Modifier.height(10.dp))
        val q = query.trim()
        val matches = BANK_LIST.filter { q.isBlank() || it.contains(q) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            matches.forEach { b ->
                val isSel = bank == b
                Box(
                    Modifier
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (isSel) TossBlue else Color.White)
                        .border(1.dp, if (isSel) TossBlue else TossDivider, RoundedCornerShape(999.dp))
                        .clickable { onPick(b) }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        b, fontSize = 14.sp,
                        color = if (isSel) Color.White else TossTextSecondary,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
            if (q.isNotBlank() && matches.none { it == q }) {
                Box(
                    Modifier
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White)
                        .border(1.dp, TossBlue, RoundedCornerShape(999.dp))
                        .clickable { onPick(q) }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text("'$q' 직접 입력", fontSize = 14.sp, color = TossBlue, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
