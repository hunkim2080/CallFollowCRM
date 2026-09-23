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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.detailline.callfollowcrm.CallFollowCrmApplication
import com.detailline.callfollowcrm.presentation.theme.AppShape
import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.theme.AppType
import com.detailline.callfollowcrm.presentation.util.ForceDialogResize

/**
 * 접수서·견적서를 처음 만들 때 딱 한 번 — "고객에게 뭐라고 보일까요?" (2026-09-23 사장님)
 *
 * 왜 여기서 묻나: **설정에 미리 채우게 하면 아무도 안 채운다.** 쓰는 그 자리에서 물어야 채운다.
 *   (사장님이 전에 정하신 "설정 없이 문자하다 되는 앱" 원칙)
 *
 * 왜 필요한가: 상호가 비면 문서 맨 위가 사장님 전화번호로 나간다. 되긴 하지만 아쉽다.
 *   전엔 "상호 미설정" / "RING-GO 시공"(없어진 옛 이름)이 찍혔고, 그 값이 고객 개인정보
 *   동의문의 **수집·이용 주체**로도 들어갔다.
 *
 * ⚠️ 상호만 필수. **대표·사업자번호는 선택**이고, 사업자번호 칸엔 "없으면 비워두세요" 라고
 *    적어둔다 — 없는 게 잘못이 아니라는 신호. 간이과세·미등록으로 시작하는 분이 많다.
 *    빈 칸은 문서에서 그 줄이 통째로 빠진다.
 */
@Composable
fun BizIdentitySheet(
    onDone: () -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val prefs = remember {
        (ctx.applicationContext as CallFollowCrmApplication).container.preferences
    }
    var name by remember { mutableStateOf(prefs.bizName) }
    var owner by remember { mutableStateOf(prefs.bizOwner) }
    var bizNo by remember { mutableStateOf(prefs.bizNo) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = AppShape.lg, color = AppTheme.colors.surface, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                ForceDialogResize()
                Text("고객에게 뭐라고 보일까요?", style = AppType.title, color = AppTheme.colors.text)
                Spacer(Modifier.height(6.dp))
                Text(
                    "접수서·견적서 맨 위에 이 이름이 찍혀요.\n한 번만 정하면 다음부터 안 물어요.",
                    style = AppType.caption, color = AppTheme.colors.textSub,
                )
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("상호 (또는 이름)") },
                    placeholder = { Text("예: 디테일라인 / 김준호 줄눈", color = AppTheme.colors.textHint) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = owner,
                    onValueChange = { owner = it },
                    label = { Text("대표 이름 (선택)") },
                    placeholder = { Text("예: 김준호", color = AppTheme.colors.textHint) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = bizNo,
                    onValueChange = { bizNo = it },
                    label = { Text("사업자등록번호 (선택)") },
                    // 없는 게 잘못이 아니라는 신호. 간이·미등록으로 시작하는 분이 많다.
                    placeholder = { Text("없으면 비워두세요", color = AppTheme.colors.textHint) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "비워둔 칸은 문서에 아예 안 나와요.",
                    style = AppType.caption, color = AppTheme.colors.textHint,
                )

                Spacer(Modifier.height(18.dp))
                val ready = name.isNotBlank()
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(AppShape.md)
                        .background(if (ready) AppTheme.colors.primary else AppTheme.colors.surfaceMuted)
                        .clickable(enabled = ready) {
                            prefs.bizName = name.trim()
                            prefs.bizOwner = owner.trim()
                            prefs.bizNo = bizNo.trim()
                            onDone()
                        }
                        .padding(vertical = 15.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (ready) "이걸로 할게요" else "상호를 적어주세요",
                        style = AppType.headline,
                        color = if (ready) AppTheme.colors.textOnPrimary else AppTheme.colors.textHint,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.width(0.dp))
                    Text(
                        "나중에 할래요",
                        style = AppType.label, color = AppTheme.colors.textHint,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(AppShape.sm)
                            .clickable { onDone() }   // 안 적어도 발행은 막지 않는다 — 번호로 나간다.
                            .padding(vertical = 10.dp),
                    )
                }
            }
        }
    }
}
