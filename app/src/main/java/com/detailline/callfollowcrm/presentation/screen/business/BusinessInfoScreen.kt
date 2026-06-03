package com.detailline.callfollowcrm.presentation.screen.business

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.data.preferences.AppPreferences
import com.detailline.callfollowcrm.presentation.component.SheetTextField
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossDivider
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary

/**
 * 견적서·사업자 정보 — 프로토 openBizInfo 바텀시트 1:1.
 *   2026-06-03: 전체화면 → 시트 모양으로 교체 (그립 + 7필드 + 하단 고정 "저장"). 위 영역(scrim) 탭하면 닫힘.
 *   상호·대표자·사업자등록번호·주소·전화·직인문구·유효기간(기본 14일). SharedPreferences 저장.
 */
@Composable
fun BusinessInfoScreen(
    prefs: AppPreferences,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(prefs.bizName) }
    var owner by remember { mutableStateOf(prefs.bizOwner) }
    var bizNo by remember { mutableStateOf(prefs.bizNo) }
    var addr by remember { mutableStateOf(prefs.bizAddr) }
    var phone by remember { mutableStateOf(prefs.bizPhone) }
    var seal by remember { mutableStateOf(prefs.bizSeal) }
    var validDays by remember { mutableStateOf(prefs.bizQuoteValidDays.toString()) }

    BackHandler(enabled = true) { onBack() }

    val sheetSwallow = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x59000000))
    ) {
        // scrim — 위 빈 영역 탭하면 닫힘
        Box(
            Modifier
                .fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onBack() }
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.94f)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Color.White)
                .clickable(interactionSource = sheetSwallow, indication = null) {}
        ) {
            // grip 손잡이
            Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(TossDivider))
            }

            // ── 스크롤 본문 ──
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp)
            ) {
                // 프로토 h3
                Text("견적서·사업자 정보", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold,
                    color = TossTextPrimary, letterSpacing = (-0.4).sp)
                Spacer(Modifier.height(4.dp))
                // 프로토 sh-sub
                Text("한 번 등록해두면 견적서·접수서에 자동으로 들어가요. 비워둔 칸은 견적서에 표시 안 돼요.",
                    fontSize = 12.5.sp, color = TossTextTertiary)
                Spacer(Modifier.height(14.dp))

                Field("상호 (업체명)", name, placeholder = "예: 디테일라인 줄눈") { name = it }
                Field("대표자 이름", owner, placeholder = "예: 정민수") { owner = it }
                Field("사업자등록번호 (선택)", bizNo, KeyboardType.Number, placeholder = "123-45-67890") { bizNo = it }
                Field("주소 (선택)", addr, placeholder = "예: 서울 강동구") { addr = it }
                Field("전화번호", phone, KeyboardType.Phone, placeholder = "010-0000-0000") { phone = it }
                Field("직인 문구 (도장에 들어갈 글자)", seal, placeholder = "예: 디테일라인 줄눈") { seal = it }

                // 견적서 유효기간 — 프로토: 작은 입력칸 + "일" + 안내
                FieldLabel("견적서 유효기간")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(140.dp)) {
                        SheetTextField(
                            value = validDays,
                            onValueChange = { validDays = it.filter { c -> c.isDigit() }.take(3) },
                            placeholder = "14",
                            keyboardType = KeyboardType.Number
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("일", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossTextSecondary)
                }
                Spacer(Modifier.height(7.dp))
                Text("발행일로부터 이 기간까지 유효", fontSize = 12.sp, color = TossTextTertiary)
                Spacer(Modifier.height(20.dp))
            }

            // ── 하단 고정 저장 (프로토 sheet-cta) ──
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(TossBlue)
                        .clickable {
                            prefs.bizName = name; prefs.bizOwner = owner; prefs.bizNo = bizNo
                            prefs.bizAddr = addr; prefs.bizPhone = phone
                            prefs.bizSeal = seal.ifBlank { name }
                            prefs.bizQuoteValidDays = validDays.toIntOrNull()?.coerceIn(1, 365) ?: 14
                            android.widget.Toast.makeText(context, "사업자 정보를 저장했어요 ✓ 견적서에 반영돼요", android.widget.Toast.LENGTH_SHORT).show()
                            onBack()
                        }
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("저장", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun FieldLabel(label: String) {
    // 프로토 .sheet-label
    Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TossTextTertiary,
        modifier = Modifier.padding(start = 2.dp, bottom = 7.dp, top = 12.dp))
}

@Composable
private fun Field(label: String, value: String, keyboard: KeyboardType = KeyboardType.Text, placeholder: String = "", onChange: (String) -> Unit) {
    // 프로토 .sheet-label + .sheet-input.
    FieldLabel(label)
    SheetTextField(value = value, onValueChange = onChange, placeholder = placeholder, keyboardType = keyboard)
}
