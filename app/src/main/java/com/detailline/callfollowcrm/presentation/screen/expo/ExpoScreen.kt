package com.detailline.callfollowcrm.presentation.screen.expo

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.data.AppContainer

/**
 * 박람회 모드 (2026-07-21 사장님) — 박람회 시공 팀 전용 창구.
 *   시공막내 안의 **완전 별개 공간**: 하단 탭바 없이 풀스크린, 카카오톡 느낌(노랑·둥근사각)으로 격리.
 *   어르신 시공자·고객도 "카톡이네" 하고 바로 쓰게. Phase 1(오늘): 현장 만들기·목록.
 *   다음: QR 계약서·공유 계약함·분배·일정 (상당 부분 서버=cowork, docs/PLAN_expo_team.md).
 *
 * ⚠️ 이 화면은 앱 나머지(토스 스타일)와 디자인이 일부러 다르다. "박람회는 별세계" 컨셉.
 */

private val Kk = Color(0xFFFEE500)          // 카카오 옐로
private val KkInk = Color(0xFF1A1A1A)       // 옐로 위 글자
private val ExpoBg = Color(0xFFEDEFF2)      // 페이지 배경(약간 푸른 회색)
private val Panel = Color(0xFFFFFFFF)
private val T1 = Color(0xFF1A1A1A)
private val T2 = Color(0xFF5F666D)
private val T3 = Color(0xFF9AA0A6)
private val Field = Color(0xFFF1F3F5)

@Composable
fun ExpoScreen(
    container: AppContainer,
    onExit: () -> Unit
) {
    val sites = remember { mutableStateListOf<String>().apply { addAll(container.preferences.expoSites) } }
    var showCreate by remember { mutableStateOf(false) }

    fun persist() { container.preferences.expoSites = sites.toList() }

    Column(
        Modifier
            .fillMaxSize()
            .background(ExpoBg)
    ) {
        // ── 카카오풍 상단 바 (노랑) ──
        Column(Modifier.background(Kk).statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 6.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("박람회", fontSize = 21.sp, fontWeight = FontWeight.Black, color = KkInk)
                    Text("팀으로 상담·계약·분배를 한 번에", fontSize = 11.5.sp, color = Color(0xFF6B5A00), fontWeight = FontWeight.SemiBold)
                }
                Box(
                    Modifier.size(34.dp).clickable { onExit() },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Close, "시공막내로 나가기", tint = KkInk, modifier = Modifier.size(22.dp)) }
            }
        }

        if (sites.isEmpty()) {
            // 빈 상태
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(28.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("🎪", fontSize = 44.sp)
                Spacer(Modifier.height(14.dp))
                Text("아직 박람회 현장이 없어요", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = T1)
                Spacer(Modifier.height(7.dp))
                Text(
                    "박람회에서 팀이 뭉친 부스 하나가 '현장'이에요.\n현장을 만들고 팀원을 초대해 시작해보세요.",
                    fontSize = 13.sp, color = T3, fontWeight = FontWeight.Medium,
                    lineHeight = 20.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sites) { name ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(Panel, RoundedCornerShape(16.dp))
                            .clickable { /* 다음 단계: 현장 상세(공유 계약함) 진입 */ }
                            .padding(13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(46.dp).background(Color(0xFF5B7CFA), RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) { Text(name.take(1), fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color.White) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(name, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = T1, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("팀원 초대·계약 받기 준비 중", fontSize = 11.5.sp, color = T3, fontWeight = FontWeight.Medium)
                        }
                        Text("준비중", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFB58A00),
                            modifier = Modifier.background(Color(0xFFFFF6D6), RoundedCornerShape(7.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                }
            }
        }

        // ── 새 현장 만들기 (카카오 옐로 버튼) ──
        Box(Modifier.background(ExpoBg).navigationBarsPadding().padding(14.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Kk, RoundedCornerShape(15.dp))
                    .clickable { showCreate = true }
                    .padding(vertical = 15.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Add, null, tint = KkInk, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("새 현장 만들기", fontSize = 15.sp, fontWeight = FontWeight.Black, color = KkInk)
            }
        }
    }

    if (showCreate) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("새 박람회 현장", fontWeight = FontWeight.ExtraBold) },
            text = {
                Column {
                    Text("박람회(부스) 이름을 적어주세요.", fontSize = 13.sp, color = T2)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = { Text("예: 2026 봄 홈리모델링박람회") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = name.trim().isNotEmpty(),
                    onClick = {
                        sites.add(0, name.trim())
                        persist()
                        showCreate = false
                    }
                ) { Text("만들기", fontWeight = FontWeight.ExtraBold, color = Color(0xFFB58A00)) }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("취소", color = T3) } }
        )
    }
}
