package com.detailline.callfollowcrm.presentation.screen.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import com.detailline.callfollowcrm.presentation.component.SheetTextField
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossDivider
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary
import com.detailline.callfollowcrm.util.DateTimeUtils

/**
 * 셀프 일정 등록 화면 (2026-06-01). 일정 화면 FAB 에서 진입.
 *   고객명·번호·시공일·주소·총금액·계약금(+받음) 입력 → 저장.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScheduleAddScreen(
    viewModel: ScheduleAddViewModel,
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val toast by viewModel.toast.collectAsState()
    val saving by viewModel.saving.collectAsState()
    val workers by viewModel.workers.collectAsState()
    val selectedWorkerIds = remember { mutableStateListOf<Long>() }
    var crewWageText by remember { mutableStateOf("") }

    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var totalText by remember { mutableStateOf("") }
    var depositText by remember { mutableStateOf("") }
    var depositPaid by remember { mutableStateOf(false) }
    var dayMs by remember { mutableLongStateOf(DateTimeUtils.startOfDay(System.currentTimeMillis())) }
    var showDate by remember { mutableStateOf(false) }
    // 시공 시간(자정부터 분, null=미정) + 기간(일). DB v24.
    var workMinutes by remember { mutableStateOf<Int?>(null) }
    var workDays by remember { mutableStateOf(1) }

    toast?.let {
        android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
        viewModel.consumeToast()
    }

    Scaffold(
        containerColor = TossGrayBg,
        topBar = {
            TopAppBar(
                title = { Text("시공 일정 등록", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "뒤로", tint = TossTextPrimary) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TossGrayBg)
            )
        }
    ) { inner ->
        Column(
            Modifier
                .padding(inner)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            FieldLabel("고객 이름 (선택)")
            SheetTextField(name, { name = it }, placeholder = "미입력 시 번호로 표시")

            Spacer(Modifier.height(12.dp))
            FieldLabel("전화번호 *")
            SheetTextField(phone, { phone = it }, placeholder = "010-0000-0000",
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone)

            Spacer(Modifier.height(12.dp))
            FieldLabel("시공일 *")
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(TossGrayBg)
                    .border(1.5.dp, TossDivider, RoundedCornerShape(12.dp))
                    .clickable { showDate = true }
                    .padding(horizontal = 14.dp, vertical = 13.dp)
            ) {
                Text(
                    DateTimeUtils.formatKoreanDate(dayMs) + " · " + DateTimeUtils.dDayLabel(dayMs),
                    color = TossTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(12.dp))
            FieldLabel("시공 시간 (선택)")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectChip("미정", workMinutes == null) { workMinutes = null }
                WORK_TIME_OPTIONS.forEach { (label, mins) ->
                    SelectChip(label, workMinutes == mins) { workMinutes = mins }
                }
            }

            Spacer(Modifier.height(12.dp))
            FieldLabel("시공 기간 (선택)")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WORK_DAYS_OPTIONS.forEach { (label, days) ->
                    SelectChip(label, workDays == days) { workDays = days }
                }
            }

            Spacer(Modifier.height(12.dp))
            FieldLabel("현장 주소 (선택)")
            SheetTextField(address, { address = it }, placeholder = "길찾기·확인용")

            Spacer(Modifier.height(12.dp))
            FieldLabel("총 시공비 (선택)")
            SheetTextField(totalText, { totalText = it.filter { c -> c.isDigit() } }, placeholder = "원",
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)

            Spacer(Modifier.height(12.dp))
            FieldLabel("계약금 (선택)")
            SheetTextField(depositText, { depositText = it.filter { c -> c.isDigit() } }, placeholder = "원",
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { depositPaid = !depositPaid }
                    .padding(vertical = 4.dp)) {
                Box(
                    Modifier.size(22.dp).clip(CircleShape)
                        .background(if (depositPaid) TossBlue else TossDivider),
                    contentAlignment = Alignment.Center
                ) { if (depositPaid) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
                Spacer(Modifier.width(8.dp))
                Text("계약금 이미 받음", color = TossTextSecondary, fontWeight = FontWeight.Medium)
            }

            if (workers.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                FieldLabel("일당 배정 (선택) — 정산 현금흐름에 자동 −지출")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    workers.forEach { w ->
                        val sel = selectedWorkerIds.contains(w.id)
                        Box(
                            Modifier.clip(RoundedCornerShape(999.dp))
                                .background(if (sel) TossBlue else Color.White)
                                .clickable {
                                    if (sel) selectedWorkerIds.remove(w.id) else selectedWorkerIds.add(w.id)
                                }
                                .padding(horizontal = 14.dp, vertical = 9.dp)
                        ) {
                            Text(w.name, color = if (sel) Color.White else TossTextSecondary,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                if (selectedWorkerIds.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    FieldLabel("1인 일당 (원)")
                    SheetTextField(crewWageText, { crewWageText = it.filter { c -> c.isDigit() } },
                        placeholder = "예: 200000",
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                }
            }

            Spacer(Modifier.height(24.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(if (saving) TossTextTertiary else TossBlue)
                    .clickable(enabled = !saving) {
                        viewModel.submit(
                            name = name, phone = phone, dayMs = dayMs,
                            workMinutes = workMinutes, workDays = workDays, address = address,
                            totalAmount = totalText.toLongOrNull(),
                            depositAmount = depositText.toLongOrNull(),
                            depositPaid = depositPaid,
                            crewWorkers = workers.filter { selectedWorkerIds.contains(it.id) },
                            crewWage = crewWageText.toLongOrNull() ?: 0L,
                            onDone = onDone
                        )
                    }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(if (saving) "저장 중..." else "일정 등록", color = Color.White,
                    fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(40.dp))
        }
    }

    if (showDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = dayMs)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { dayMs = DateTimeUtils.startOfDay(it) }
                    showDate = false
                }) { Text("확인", color = TossBlue, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("취소", color = TossTextSecondary) } }
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    // 프로토 .sheet-label — 12px w700 t3.
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TossTextTertiary,
        modifier = Modifier.padding(start = 2.dp, bottom = 7.dp))
}

/** 시공 시간/기간 선택 알약 칩. 선택 시 파랑 채움. */
@Composable
private fun SelectChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(999.dp))
            .background(if (selected) TossBlue else Color.White)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(
            label,
            color = if (selected) Color.White else TossTextSecondary,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/** 시공 시작 시각 빠른 선택 (라벨 → 자정부터 분). 프로토 schedTimeChips 벤치마킹. */
private val WORK_TIME_OPTIONS: List<Pair<String, Int>> = listOf(
    "오전 8시" to 8 * 60, "오전 9시" to 9 * 60, "오전 10시" to 10 * 60, "오전 11시" to 11 * 60,
    "오후 1시" to 13 * 60, "오후 2시" to 14 * 60, "오후 3시" to 15 * 60, "오후 4시" to 16 * 60
)

/** 시공 기간 빠른 선택 (라벨 → 일수). */
private val WORK_DAYS_OPTIONS: List<Pair<String, Int>> = listOf(
    "당일" to 1, "2일" to 2, "3일" to 3, "5일" to 5, "일주일" to 7
)
