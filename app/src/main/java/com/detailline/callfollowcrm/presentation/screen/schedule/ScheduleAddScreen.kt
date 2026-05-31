package com.detailline.callfollowcrm.presentation.screen.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleAddScreen(
    viewModel: ScheduleAddViewModel,
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val toast by viewModel.toast.collectAsState()
    val saving by viewModel.saving.collectAsState()

    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var totalText by remember { mutableStateOf("") }
    var depositText by remember { mutableStateOf("") }
    var depositPaid by remember { mutableStateOf(false) }
    var dayMs by remember { mutableLongStateOf(DateTimeUtils.startOfDay(System.currentTimeMillis())) }
    var showDate by remember { mutableStateOf(false) }

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
            OutlinedTextField(name, { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("미입력 시 번호로 표시") })

            Spacer(Modifier.height(12.dp))
            FieldLabel("전화번호 *")
            OutlinedTextField(phone, { phone = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone),
                placeholder = { Text("010-0000-0000") })

            Spacer(Modifier.height(12.dp))
            FieldLabel("시공일 *")
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(Color.White).clickable { showDate = true }
                    .padding(horizontal = 14.dp, vertical = 14.dp)
            ) {
                Text(
                    DateTimeUtils.formatKoreanDate(dayMs) + " · " + DateTimeUtils.dDayLabel(dayMs),
                    color = TossTextPrimary, fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(12.dp))
            FieldLabel("현장 주소 (선택)")
            OutlinedTextField(address, { address = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("길찾기·확인용") })

            Spacer(Modifier.height(12.dp))
            FieldLabel("총 시공비 (선택)")
            OutlinedTextField(totalText, { totalText = it.filter { c -> c.isDigit() } }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                placeholder = { Text("원") })

            Spacer(Modifier.height(12.dp))
            FieldLabel("계약금 (선택)")
            OutlinedTextField(depositText, { depositText = it.filter { c -> c.isDigit() } }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                placeholder = { Text("원") })
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

            Spacer(Modifier.height(24.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(if (saving) TossTextTertiary else TossBlue)
                    .clickable(enabled = !saving) {
                        viewModel.submit(
                            name = name, phone = phone, dayMs = dayMs, address = address,
                            totalAmount = totalText.toLongOrNull(),
                            depositAmount = depositText.toLongOrNull(),
                            depositPaid = depositPaid,
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
    Text(text, style = MaterialTheme.typography.labelMedium, color = TossTextSecondary,
        fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp))
}
