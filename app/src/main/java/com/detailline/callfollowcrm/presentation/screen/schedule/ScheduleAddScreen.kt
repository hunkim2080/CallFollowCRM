package com.detailline.callfollowcrm.presentation.screen.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import com.detailline.callfollowcrm.data.local.entity.NotebookContactEntity
import com.detailline.callfollowcrm.presentation.component.AddressSearchDialog
import com.detailline.callfollowcrm.presentation.component.SheetTextField
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossBlueDark
import com.detailline.callfollowcrm.presentation.theme.TossBlueSoft
import com.detailline.callfollowcrm.presentation.theme.TossDivider
import com.detailline.callfollowcrm.presentation.theme.TossError
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary
import com.detailline.callfollowcrm.util.DateTimeUtils
import com.detailline.callfollowcrm.util.PhoneNumberFormatter
import java.util.Calendar

/**
 * 셀프 일정 등록 화면 — 프로토 renderAddSchedule 1:1.
 *   [내 고객 / 거래처 일감] 토글 · 통화·문자 고객 불러오기 · 인라인 미니 달력 ·
 *   시공 시간/기간 칩 · 총 금액(만원) · 계약금 "받았어요" 체크→펼침.
 *   주소 검색은 아직 미구현 → 자유입력 유지. 금액은 만원 입력 → 저장 시 ×10000(원).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScheduleAddScreen(
    viewModel: ScheduleAddViewModel,
    onDone: () -> Unit,
    onBack: () -> Unit,
    /** 일정에서 누른 날(ms). 시공일·달력 기본값으로. null/<=0 = 오늘. (2026-06-18 사장님) */
    initialDayMs: Long? = null
) {
    val context = LocalContext.current
    val toast by viewModel.toast.collectAsState()
    val saving by viewModel.saving.collectAsState()
    val workers by viewModel.workers.collectAsState()
    val vendors by viewModel.vendors.collectAsState()
    val recentContacts by viewModel.recentContacts.collectAsState()
    val contactHints by viewModel.contactHints.collectAsState()
    val selectedWorkerIds = remember { mutableStateListOf<Long>() }
    var crewWageText by remember { mutableStateOf("") }

    var mode by remember { mutableStateOf("mine") } // mine | partner
    var name by remember { mutableStateOf("") }
    // 전화번호 = TextFieldValue + 커서 항상 끝 → formatProgressive 재포맷 시 숫자 순서 꼬임 방지(앱 공통 패턴).
    //   2026-06-20 사장님: "010" 미리채움 제거 — 빈칸이어야 '뒷번호 일부만 쳐서 찾기'를 사람들이 인지함.
    var phoneField by remember {
        mutableStateOf(androidx.compose.ui.text.input.TextFieldValue("", selection = androidx.compose.ui.text.TextRange(0)))
    }
    var selectedVendor by remember { mutableStateOf<NotebookContactEntity?>(null) }
    var address by remember { mutableStateOf("") }
    var addrDetail by remember { mutableStateOf("") }   // 동·호수(별도) — 주소 검색은 도로명까지만 줌.
    var totalManwon by remember { mutableStateOf("") }
    var depositManwon by remember { mutableStateOf("") }
    var depositReceived by remember { mutableStateOf(false) }
    // 일정에서 누른 날을 시공일 기본값으로(없으면 오늘). 달력도 그 달로 연다. (2026-06-18 사장님)
    val seedDayMs = remember(initialDayMs) {
        initialDayMs?.takeIf { it > 0L }?.let { DateTimeUtils.startOfDay(it) }
            ?: DateTimeUtils.startOfDay(System.currentTimeMillis())
    }
    var dayMs by remember { mutableLongStateOf(seedDayMs) }
    var monthAnchor by remember { mutableLongStateOf(monthAnchorOf(seedDayMs)) }
    var workMinutes by remember { mutableStateOf(9 * 60) } // 프로토 기본 오전 9시 (미정 없음)
    var workDays by remember { mutableStateOf(1) }

    var showNewVendor by remember { mutableStateOf(false) }
    var showAddrSearch by remember { mutableStateOf(false) }
    var showTimeCustom by remember { mutableStateOf(false) }
    var showDaysCustom by remember { mutableStateOf(false) }

    toast?.let {
        android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
        viewModel.consumeToast()
    }

    // 프로토 바텀시트 — "일정" 목록 위로 올라오는 시트(손잡이+둥근 상단+제목 "일정 직접 등록").
    //   2026-06-03: 전체화면 앱바 → 시트 모양으로 교체 (프로토 1:1). 위 빈 영역(scrim) 탭하면 닫힘.
    val sheetSwallow = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x59000000))
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) { onBack() }
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
            Box(
                Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(TossDivider))
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp)
            ) {
                // 프로토 시트 제목
                Text("일정 직접 등록", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold,
                    color = TossTextPrimary, letterSpacing = (-0.4).sp)
                Spacer(Modifier.height(4.dp))
                // 프로토 sh-sub
                Text("전화번호만, 또는 거래처를 탭 한 번으로.", fontSize = 12.5.sp, color = TossTextTertiary,
                    modifier = Modifier.padding(bottom = 14.dp))
            // ── 모드 토글 (.fchips) ──
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FChip("내 고객", mode == "mine") { mode = "mine" }
                FChip("거래처 일감", mode == "partner") { mode = "partner" }
            }
            Spacer(Modifier.height(13.dp))

            if (mode == "mine") {
                // 순서: 전화번호 → 이름 → (주소·시공일). "저장된"→"한번이라도 연락했던"으로 풀어씀. (2026-06-21 사장님)
                FieldLabel("고객 전화번호")
                Text("한번이라도 연락했던 고객은 번호 일부만(뒷 4자리·중간) 쳐도 떠요. 탭하면 채워져요.",
                    fontSize = 11.5.sp, color = TossTextTertiary, modifier = Modifier.padding(start = 2.dp, bottom = 6.dp))
                SheetTextField(
                    value = phoneField,
                    onValueChange = { tfv ->
                        val f = PhoneNumberFormatter.formatProgressive(tfv.text)
                        phoneField = androidx.compose.ui.text.input.TextFieldValue(f, selection = androidx.compose.ui.text.TextRange(f.length))
                    },
                    placeholder = "번호 입력 · 일부만 쳐도 찾아져요",
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone
                )
                // 번호 일부(중간·뒷자리)만 쳐도 저장된 고객을 바로 찾아 보여줌 → 탭하면 채워짐. (2026-06-20 사장님)
                //   "불러오기" 목록은 안 쓰고 외운 뒷번호/중간번호로 치는 사장님 흐름에 맞춤.
                val typedDigits = phoneField.text.filter { it.isDigit() }
                if (typedDigits.length in 3..10) {
                    val matches = recentContacts.asSequence()
                        .filter { c ->
                            val d = c.phoneNumber.filter { ch -> ch.isDigit() }
                            d.length >= 8 && d.contains(typedDigits) && d != typedDigits
                        }
                        .distinctBy { it.phoneNumber.filter { ch -> ch.isDigit() }.takeLast(8) }
                        .take(6).toList()
                    if (matches.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TossGrayBg)) {
                            matches.forEachIndexed { i, c ->
                                val cn = c.name?.takeIf { it.isNotBlank() && it.count { ch -> ch.isDigit() } < 9 }
                                Row(
                                    Modifier.fillMaxWidth().clickable {
                                        val f = PhoneNumberFormatter.format(c.phoneNumber)
                                        phoneField = androidx.compose.ui.text.input.TextFieldValue(f, selection = androidx.compose.ui.text.TextRange(f.length))
                                        if (cn != null && name.isBlank()) name = cn
                                    }.padding(horizontal = 14.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(cn ?: PhoneNumberFormatter.format(c.phoneNumber),
                                            fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
                                        if (cn != null) Text(PhoneNumberFormatter.format(c.phoneNumber),
                                            fontSize = 12.sp, color = TossTextTertiary)
                                    }
                                    Text("넣기", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = TossBlueDark)
                                }
                                if (i < matches.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White))
                            }
                        }
                        Text("번호 일부(중간·뒷자리)만 쳐도 찾아드려요. 탭하면 채워져요.",
                            fontSize = 11.5.sp, color = TossTextTertiary, modifier = Modifier.padding(top = 6.dp, start = 2.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
                FieldLabel("고객 이름 (선택)")
                SheetTextField(name, { name = it }, placeholder = "안 적어도 돼요 · 예: 강동 서사장")
            } else {
                FieldLabel("거래처 (자주 일 주는 곳)")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    vendors.forEach { v ->
                        FChip(v.name, selectedVendor?.id == v.id) { selectedVendor = v }
                    }
                    FChip("+ 새 거래처", false) { showNewVendor = true }
                }
                Spacer(Modifier.height(4.dp))
                Text("탭 한 번이면 번호·정산처가 자동 연결돼요.",
                    fontSize = 12.sp, color = TossTextTertiary, modifier = Modifier.padding(start = 2.dp))
            }

            Spacer(Modifier.height(12.dp))
            FieldLabel("현장 주소")
            // 프로토 as-addr-display — 탭하면 주소 검색(Daum). 채워지면 핀+주소.
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TossGrayBg)
                    .border(1.5.dp, TossDivider, RoundedCornerShape(12.dp))
                    .clickable { showAddrSearch = true }
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (address.isBlank()) Icons.Default.Search else Icons.Default.Place, null,
                    tint = if (address.isBlank()) TossTextTertiary else TossBlue, modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    address.ifBlank { "주소 검색 (탭)" },
                    fontSize = 15.sp, color = if (address.isBlank()) TossTextTertiary else TossTextPrimary
                )
            }
            // 동·호수 — 주소(도로명) 고른 뒤에만. 고객정보 화면과 동일 흐름. (2026-06-11)
            if (address.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                FieldLabel("동·호수 (선택)")
                SheetTextField(addrDetail, { addrDetail = it }, placeholder = "예: 101동 1502호")
            }

            Spacer(Modifier.height(12.dp))
            FieldLabel("시공일")
            InlineMonthCalendar(
                monthAnchor = monthAnchor,
                selectedDayMs = dayMs,
                onShiftMonth = { monthAnchor = shiftMonth(monthAnchor, it) },
                onSelect = { dayMs = it }
            )

            Spacer(Modifier.height(12.dp))
            FieldLabel("시공 시간")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WORK_TIME_OPTIONS.forEach { (label, mins) ->
                    SelectChip(label, workMinutes == mins) { workMinutes = mins }
                }
                val timeCustom = WORK_TIME_OPTIONS.none { it.second == workMinutes }
                SelectChip(if (timeCustom) DateTimeUtils.formatWorkMinutes(workMinutes) else "직접", timeCustom) { showTimeCustom = true }
            }

            Spacer(Modifier.height(12.dp))
            FieldLabel("시공 기간")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WORK_DAYS_OPTIONS.forEach { (label, days) ->
                    SelectChip(label, workDays == days) { workDays = days }
                }
                val daysCustom = WORK_DAYS_OPTIONS.none { it.second == workDays }
                SelectChip(if (daysCustom) "${workDays}일" else "직접", daysCustom) { showDaysCustom = true }
            }

            Spacer(Modifier.height(12.dp))
            FieldLabel("총 금액 (만원)")
            SheetTextField(totalManwon, { totalManwon = it.filter { c -> c.isDigit() } }, placeholder = "예: 40",
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                visualTransformation = com.detailline.callfollowcrm.presentation.component.ThousandsCommaTransformation)

            // .sheet-check — 계약금을 받았어요 → 체크 시 펼침
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { depositReceived = !depositReceived }
                    .padding(top = 8.dp, bottom = 12.dp, start = 2.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(22.dp).clip(RoundedCornerShape(7.dp))
                        .background(if (depositReceived) TossBlue else TossGrayBg)
                        .border(2.dp, if (depositReceived) TossBlue else TossDivider, RoundedCornerShape(7.dp)),
                    contentAlignment = Alignment.Center
                ) { if (depositReceived) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(13.dp)) }
                Spacer(Modifier.width(9.dp))
                Text("계약금을 받았어요", fontSize = 14.sp, color = TossTextPrimary)
            }
            if (depositReceived) {
                FieldLabel("계약금 (만원)")
                SheetTextField(depositManwon, { depositManwon = it.filter { c -> c.isDigit() } }, placeholder = "예: 10",
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                    visualTransformation = com.detailline.callfollowcrm.presentation.component.ThousandsCommaTransformation)
            }

            // 일당 배정(앱 추가 기능) — 수첩 일당 있으면.
            if (workers.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                FieldLabel("일당 배정 (선택) — 정산 현금흐름에 자동 −지출")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    workers.forEach { w ->
                        val sel = selectedWorkerIds.contains(w.id)
                        FChip(w.name, sel) {
                            if (sel) selectedWorkerIds.remove(w.id) else selectedWorkerIds.add(w.id)
                        }
                    }
                }
                if (selectedWorkerIds.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    FieldLabel("1인 일당 (원)")
                    SheetTextField(crewWageText, { crewWageText = it.filter { c -> c.isDigit() } },
                        placeholder = "예: 200000",
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                        visualTransformation = com.detailline.callfollowcrm.presentation.component.ThousandsCommaTransformation)
                }
            }

            // .sheet-cta — 일정 등록
            Spacer(Modifier.height(20.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(if (saving) TossTextTertiary else TossBlue)
                    .clickable(enabled = !saving) {
                        val submitName: String
                        val submitPhone: String
                        if (mode == "partner") {
                            val v = selectedVendor
                            if (v == null) { android.widget.Toast.makeText(context, "거래처를 선택해주세요", android.widget.Toast.LENGTH_SHORT).show(); return@clickable }
                            if (v.phone.filter { it.isDigit() }.length < 8) { android.widget.Toast.makeText(context, "이 거래처는 번호가 없어요. 수첩에서 번호를 추가해주세요", android.widget.Toast.LENGTH_SHORT).show(); return@clickable }
                            submitName = "${v.name} (거래처)"; submitPhone = v.phone
                        } else { submitName = name; submitPhone = phoneField.text }
                        val combinedAddr = (address.trim() + if (addrDetail.isBlank()) "" else " " + addrDetail.trim()).trim()
                        viewModel.submit(
                            name = submitName, phone = submitPhone, dayMs = dayMs,
                            workMinutes = workMinutes, workDays = workDays, address = combinedAddr,
                            totalAmount = totalManwon.toLongOrNull()?.let { it * 10_000 },
                            depositAmount = if (depositReceived) depositManwon.toLongOrNull()?.let { it * 10_000 } else null,
                            depositPaid = depositReceived,
                            crewWorkers = workers.filter { selectedWorkerIds.contains(it.id) },
                            crewWage = crewWageText.toLongOrNull() ?: 0L,
                            onDone = onDone
                        )
                    }
                    .padding(vertical = 15.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(if (saving) "저장 중..." else "일정 등록", color = Color.White,
                    fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
                Spacer(Modifier.height(40.dp))
            }
        }
    }

    if (showNewVendor) {
        NewVendorDialog(
            onAdd = { nm, ph -> viewModel.addVendor(nm, ph) { showNewVendor = false } },
            onDismiss = { showNewVendor = false }
        )
    }

    if (showAddrSearch) {
        AddressSearchDialog(
            onPicked = { address = it; showAddrSearch = false },
            onDismiss = { showAddrSearch = false }
        )
    }

    if (showTimeCustom) {
        TimeCustomDialog(
            initialMinutes = workMinutes,
            onConfirm = { workMinutes = it; showTimeCustom = false },
            onDismiss = { showTimeCustom = false }
        )
    }
    if (showDaysCustom) {
        DaysCustomDialog(
            initialDays = workDays,
            onConfirm = { workDays = it; showDaysCustom = false },
            onDismiss = { showDaysCustom = false }
        )
    }
}

/** 프로토 schedTimeCustom — 시간 직접 입력 (HH:MM). */
@Composable
private fun TimeCustomDialog(initialMinutes: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var txt by remember { mutableStateOf("${initialMinutes / 60}:${(initialMinutes % 60).toString().padStart(2, '0')}") }
    var err by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).padding(20.dp)) {
            Text("시간 직접 입력", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
            Spacer(Modifier.height(4.dp))
            Text("24시 기준 · 예: 14:30", fontSize = 12.sp, color = TossTextTertiary)
            Spacer(Modifier.height(12.dp))
            SheetTextField(txt, { txt = it; err = false }, placeholder = "HH:MM")
            if (err) {
                Spacer(Modifier.height(4.dp))
                Text("예: 14:30", fontSize = 12.sp, color = com.detailline.callfollowcrm.presentation.theme.TossError)
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text("취소", color = TossTextSecondary, fontWeight = FontWeight.Medium,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onDismiss() }.padding(horizontal = 14.dp, vertical = 10.dp))
                Spacer(Modifier.width(4.dp))
                Text("확인", color = TossBlue, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                        val m = Regex("(\\d{1,2})\\s*[:시]?\\s*(\\d{1,2})?").find(txt)
                        val hh = m?.groupValues?.getOrNull(1)?.toIntOrNull()
                        val mm = m?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 0
                        if (hh == null || hh !in 0..23 || mm !in 0..59) { err = true }
                        else onConfirm(hh * 60 + mm)
                    }.padding(horizontal = 14.dp, vertical = 10.dp))
            }
        }
    }
}

/** 프로토 setSchedDaysCustom — 시공 기간 직접 입력 (일). */
@Composable
private fun DaysCustomDialog(initialDays: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var txt by remember { mutableStateOf(initialDays.toString()) }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).padding(20.dp)) {
            Text("시공 기간", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
            Spacer(Modifier.height(4.dp))
            Text("며칠 동안 하나요?", fontSize = 12.sp, color = TossTextTertiary)
            Spacer(Modifier.height(12.dp))
            SheetTextField(txt, { txt = it.filter { c -> c.isDigit() } }, placeholder = "예: 10",
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text("취소", color = TossTextSecondary, fontWeight = FontWeight.Medium,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onDismiss() }.padding(horizontal = 14.dp, vertical = 10.dp))
                Spacer(Modifier.width(4.dp))
                Text("확인", color = TossBlue, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                        onConfirm((txt.toIntOrNull() ?: 1).coerceAtLeast(1))
                    }.padding(horizontal = 14.dp, vertical = 10.dp))
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    // 프로토 .sheet-label — 12px w700 t3.
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TossTextTertiary,
        modifier = Modifier.padding(start = 2.dp, bottom = 7.dp))
}

/** 프로토 .fchip — 알약 토글/선택칩. on=파랑 채움, off=흰색+그림자 느낌(여기선 흰 배경). */
@Composable
private fun FChip(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(999.dp))
            .background(if (on) TossBlue else Color.White)
            .clickable { onClick() }
            .padding(horizontal = 15.dp, vertical = 8.dp)
    ) {
        Text(label, color = if (on) Color.White else TossTextSecondary,
            fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

/** 프로토 .or-div — 가운데 글자 + 좌우 선. */
@Composable
private fun OrDivider(text: String) {
    Row(
        Modifier.fillMaxWidth().padding(top = 15.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(TossDivider))
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TossTextTertiary,
            modifier = Modifier.padding(horizontal = 10.dp))
        Box(Modifier.weight(1f).height(1.dp).background(TossDivider))
    }
}

/** 시공 시간/기간 선택 알약 칩. */
@Composable
private fun SelectChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(999.dp))
            .background(if (selected) TossBlue else Color.White)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(label, color = if (selected) Color.White else TossTextSecondary,
            fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
    }
}

/** 프로토 시트 달력 nav — 흰색 둥근 사각 버튼. */
@Composable
private fun SheetCalNav(icon: androidx.compose.ui.graphics.vector.ImageVector, cd: String, onClick: () -> Unit) {
    Box(
        Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(Color.White).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, cd, tint = TossTextSecondary, modifier = Modifier.size(18.dp))
    }
}

/** 프로토 .mini-cal — 인라인 월 달력(선택형). 회색 카드 + 월 nav + 6주 그리드. */
@Composable
private fun InlineMonthCalendar(
    monthAnchor: Long,
    selectedDayMs: Long,
    onShiftMonth: (Int) -> Unit,
    onSelect: (Long) -> Unit
) {
    val todayStart = remember { DateTimeUtils.startOfDay(System.currentTimeMillis()) }
    val cells = remember(monthAnchor) { buildSelectCells(monthAnchor, todayStart) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(TossGrayBg)
            .padding(start = 11.dp, end = 11.dp, top = 11.dp, bottom = 9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            SheetCalNav(Icons.Default.ChevronLeft, "이전 달") { onShiftMonth(-1) }
            Text(DateTimeUtils.formatMonthHeader(monthAnchor), modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium, color = TossTextPrimary,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            SheetCalNav(Icons.Default.ChevronRight, "다음 달") { onShiftMonth(1) }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("일", "월", "화", "수", "목", "금", "토").forEachIndexed { i, label ->
                Text(label, modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = when (i) { 0 -> TossError; 6 -> TossBlue; else -> TossTextSecondary },
                    fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            }
        }
        repeat(6) { week ->
            Row(Modifier.fillMaxWidth()) {
                cells.subList(week * 7, week * 7 + 7).forEach { cell ->
                    val isSel = DateTimeUtils.startOfDay(selectedDayMs) == cell.dayStartMs
                    val bg = when { isSel -> TossBlue; cell.isToday -> TossBlueSoft; else -> Color.Transparent }
                    val fg = when {
                        isSel -> Color.White
                        !cell.inMonth -> TossTextTertiary
                        cell.dow == Calendar.SUNDAY -> TossError
                        cell.dow == Calendar.SATURDAY -> TossBlue
                        else -> TossTextPrimary
                    }
                    Box(
                        Modifier.weight(1f).aspectRatio(1f).padding(2.dp).clip(CircleShape)
                            .background(bg).clickable { onSelect(cell.dayStartMs) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(cell.dom.toString(), color = fg, fontSize = 13.sp,
                            fontWeight = if (cell.isToday || isSel) FontWeight.Bold else FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/** 통화·문자한 고객 picker (프로토 openSchedPeoplePick). */
@Composable
private fun ContactImportDialog(
    contacts: List<CustomerEntity>,
    hints: Map<String, String>,
    onPick: (CustomerEntity) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White)
                .padding(vertical = 16.dp)
        ) {
            Text("통화·문자한 고객", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            // 번호만으론 누군지 모르니 ✨요약/메모/주소/최근연락을 힌트로 같이 보여줌. (2026-06-11 사장님)
            Text("번호 아래 힌트로 누군지 확인하고 골라요.", fontSize = 12.sp, color = TossTextTertiary,
                modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(8.dp))
            if (contacts.isEmpty()) {
                Text("불러올 고객이 아직 없어요", fontSize = 14.sp, color = TossTextTertiary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp))
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
                    items(contacts, key = { it.id }) { c ->
                        val hasName = c.name?.isNotBlank() == true
                        val suffix = c.phoneNumber.filter { it.isDigit() }.takeLast(8)
                        // 힌트 우선순위: ✨AI요약 > 메모 > 짧은주소 > 최근 연락 N일 전.
                        val hint = hints[suffix]
                            ?: c.memo?.takeIf { it.isNotBlank() }
                            ?: c.address?.takeIf { it.isNotBlank() }
                            ?: lastContactHint(c.updatedAt)
                        Column(
                            Modifier.fillMaxWidth().clickable { onPick(c) }
                                .padding(horizontal = 20.dp, vertical = 11.dp)
                        ) {
                            Text(
                                if (hasName) c.name!! else PhoneNumberFormatter.format(c.phoneNumber),
                                fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary,
                                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(2.dp))
                            // 이름 있으면: 번호 · 힌트 / 이름 없으면: 힌트만(번호는 위에).
                            Text(
                                if (hasName) PhoneNumberFormatter.format(c.phoneNumber) + (hint?.let { " · $it" } ?: "")
                                else (hint ?: PhoneNumberFormatter.format(c.phoneNumber)),
                                fontSize = 12.sp, color = TossTextTertiary,
                                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/** updatedAt → "오늘 연락 / 어제 연락 / N일 전 연락" (번호만 있는 고객 식별 힌트). */
private fun lastContactHint(updatedAt: Long): String {
    val today = DateTimeUtils.startOfDay(System.currentTimeMillis())
    val days = ((today - DateTimeUtils.startOfDay(updatedAt)) / DateTimeUtils.DAY_MS).toInt()
    return when {
        days <= 0 -> "오늘 연락"
        days == 1 -> "어제 연락"
        days in 2..30 -> "${days}일 전 연락"
        else -> DateTimeUtils.formatDateOnly(updatedAt) + " 연락"
    }
}

/** 새 거래처 추가 다이얼로그 (프로토 addPartner). */
@Composable
private fun NewVendorDialog(
    onAdd: (name: String, phone: String) -> Unit,
    onDismiss: () -> Unit
) {
    var nm by remember { mutableStateOf("") }
    var ph by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White)
                .padding(20.dp)
        ) {
            Text("새 거래처", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
            Spacer(Modifier.height(14.dp))
            FieldLabel("거래처 이름")
            SheetTextField(nm, { nm = it }, placeholder = "예: 시흥 인테리어")
            Spacer(Modifier.height(10.dp))
            FieldLabel("전화번호")
            SheetTextField(ph, { ph = it.filter { c -> c.isDigit() } }, placeholder = "010-0000-0000",
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone,
                visualTransformation = com.detailline.callfollowcrm.presentation.component.PhoneHyphenTransformation)
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text("취소", color = TossTextSecondary, fontWeight = FontWeight.Medium,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onDismiss() }.padding(horizontal = 14.dp, vertical = 10.dp))
                Spacer(Modifier.width(4.dp))
                Text("추가", color = TossBlue, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { if (nm.isNotBlank()) onAdd(nm, ph) }.padding(horizontal = 14.dp, vertical = 10.dp))
            }
        }
    }
}

// ── 인라인 달력 셀 ──
private data class SelCell(val dayStartMs: Long, val dom: Int, val dow: Int, val inMonth: Boolean, val isToday: Boolean)

private fun monthAnchorOf(anyMs: Long): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = anyMs
        set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}

private fun shiftMonth(anchorMs: Long, delta: Int): Long {
    val cal = Calendar.getInstance().apply { timeInMillis = anchorMs; add(Calendar.MONTH, delta); set(Calendar.DAY_OF_MONTH, 1) }
    return cal.timeInMillis
}

private fun buildSelectCells(monthAnchor: Long, todayStart: Long): List<SelCell> {
    val cal = Calendar.getInstance().apply { timeInMillis = monthAnchor }
    val targetMonth = cal.get(Calendar.MONTH)
    val firstDow = cal.get(Calendar.DAY_OF_WEEK)
    cal.add(Calendar.DAY_OF_MONTH, -(firstDow - 1))
    val cells = ArrayList<SelCell>(42)
    repeat(42) {
        val dayStart = DateTimeUtils.startOfDay(cal.timeInMillis)
        cells += SelCell(
            dayStartMs = dayStart,
            dom = cal.get(Calendar.DAY_OF_MONTH),
            dow = cal.get(Calendar.DAY_OF_WEEK),
            inMonth = cal.get(Calendar.MONTH) == targetMonth,
            isToday = dayStart == todayStart
        )
        cal.add(Calendar.DAY_OF_MONTH, 1)
    }
    return cells
}

/** 시공 시작 시각 빠른 선택 (라벨 → 자정부터 분). */
private val WORK_TIME_OPTIONS: List<Pair<String, Int>> = listOf(
    "오전 8시" to 8 * 60, "오전 9시" to 9 * 60, "오전 10시" to 10 * 60, "오전 11시" to 11 * 60,
    "오후 1시" to 13 * 60, "오후 2시" to 14 * 60, "오후 3시" to 15 * 60, "오후 4시" to 16 * 60
)

/** 시공 기간 빠른 선택 (라벨 → 일수). 프로토 schedDaysChips: 당일/2/3/4/5/일주일+직접. */
private val WORK_DAYS_OPTIONS: List<Pair<String, Int>> = listOf(
    "당일" to 1, "2일" to 2, "3일" to 3, "4일" to 4, "5일" to 5, "일주일" to 7
)
