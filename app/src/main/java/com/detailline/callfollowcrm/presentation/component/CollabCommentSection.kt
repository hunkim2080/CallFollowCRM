package com.detailline.callfollowcrm.presentation.component

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.ai.SharedSiteRepository

private val CommentPurple = Color(0xFF7C5CFC)
private val CTextPrimary = Color(0xFF191F28)
private val CTextSecondary = Color(0xFF4E5968)
private val CTextTertiary = Color(0xFF8B95A1)

/**
 * 협업 현장 한 줄 댓글 — 협업 사장끼리 현장 논의(팀원 코멘트 스타일). (2026-07-01 사장님)
 *   협업 현장 화면·고객정보 협업 탭 둘 다 재사용. 내가 쓴 건 "나"(보라) + 상대는 상호명.
 *   오래된→최신(서버 ASC). 1000자 컷(서버 413 대비). 자동 새로고침(폴링)은 호출부에서.
 */
@Composable
fun CollabCommentSection(
    comments: List<SharedSiteRepository.SiteComment>,
    myPhone: String,
    busy: Boolean,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var input by remember { mutableStateOf("") }
    val fmt = remember { java.text.SimpleDateFormat("M/d HH:mm", java.util.Locale.KOREA) }
    Column(modifier.fillMaxWidth()) {
        Text("💬 현장 한마디" + (if (comments.isNotEmpty()) " (${comments.size})" else ""),
            fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CTextTertiary,
            modifier = Modifier.padding(start = 2.dp, bottom = 8.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White)
                .border(1.dp, Color(0xFFE9ECF0), RoundedCornerShape(14.dp)).padding(13.dp)
        ) {
            if (comments.isEmpty()) {
                Text("아직 남긴 말이 없어요. 현장에 대해 한마디 남겨보세요.",
                    fontSize = 13.sp, color = CTextTertiary, lineHeight = 19.sp)
            } else {
                comments.forEachIndexed { i, c ->
                    if (i > 0) Spacer(Modifier.height(11.dp))
                    val mine = c.authorPhone.isNotBlank() && c.authorPhone == myPhone
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (mine) "나" else c.authorName, fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                            color = if (mine) CommentPurple else CTextSecondary)
                        Spacer(Modifier.weight(1f))
                        Text(fmt.format(java.util.Date(c.createdAtMs)), fontSize = 10.5.sp, color = CTextTertiary)
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(c.body, fontSize = 13.5.sp, color = CTextPrimary, lineHeight = 19.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { if (it.length <= 1000) input = it },
                    placeholder = { Text("한마디 입력…", color = CTextTertiary, fontSize = 13.sp) },
                    singleLine = false,
                    maxLines = 3,
                    textStyle = TextStyle(fontFamily = com.detailline.callfollowcrm.presentation.theme.Pretendard, fontSize = 13.5.sp, color = CTextPrimary),
                    shape = RoundedCornerShape(11.dp),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                val canSend = input.isNotBlank() && !busy
                Box(
                    Modifier.clip(RoundedCornerShape(11.dp))
                        .background(if (canSend) CommentPurple else Color(0xFFCBD1DA))
                        .clickable(enabled = canSend) { onSend(input.trim()); input = "" }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) { Text("보내기", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
