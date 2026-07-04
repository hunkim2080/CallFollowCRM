package com.detailline.callfollowcrm.presentation.screen.business

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.detailline.callfollowcrm.presentation.component.BankPickerField
import com.detailline.callfollowcrm.presentation.component.SheetTextField
import com.detailline.callfollowcrm.presentation.component.formatAccountNo
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossDivider
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary

/**
 * 견적서·사업자 정보 — 프로토 openBizInfo 바텀시트 1:1.
 *   2026-06-03: 전체화면 → 시트 모양으로 교체 (그립 + 7필드 + 하단 고정 "저장"). 위 영역(scrim) 탭하면 닫힘.
 *   상호·대표자·사업자등록번호·주소·전화·직인문구·유효기간(기본 14일). SharedPreferences 저장.
 */
@OptIn(ExperimentalLayoutApi::class)
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
    // 숫자만 보관(표시는 PhoneHyphenTransformation 으로 하이픈). FormattedTextField 의 버퍼 재작성이 S23U IME 자리바뀜을 일으켜 교체. (2026-06-22)
    // 문서 표시용 대표번호 — 로그인/푸시용 bizPhone 과 분리(개인 휴대폰 노출 방지). 비면 로그인 번호로 시작. (2026-07-04 사장님)
    var phone by remember { mutableStateOf(prefs.bizRepPhone.ifBlank { prefs.bizPhone }.filter { it.isDigit() }) }
    var seal by remember { mutableStateOf(prefs.bizSeal) }
    var validDays by remember { mutableStateOf(prefs.bizQuoteValidDays.toString()) }
    var bank by remember { mutableStateOf(prefs.bizBank) }
    var bankOpen by remember { mutableStateOf(false) }
    var bankQuery by remember { mutableStateOf("") }
    var accountNo by remember { mutableStateOf(prefs.bizAccountNo) }
    var accountHolder by remember { mutableStateOf(prefs.bizAccountHolder) }

    // ── 사업자등록증 스캔 → 온디바이스 OCR 자동 입력 (2026-07-04 사장님) ──
    val scope = rememberCoroutineScope()
    var scanBusy by remember { mutableStateOf(false) }
    var scanChooser by remember { mutableStateOf(false) }
    // 고른/찍은 사진을 OCR 전에 크게 확인 — 맞는지 보고 채우기. (2026-07-04 사장님)
    var previewUri by remember { mutableStateOf<android.net.Uri?>(null) }
    // 카메라 촬영 임시 파일(FileProvider) — cacheDir/shared/ 는 file_paths.xml 에 공유 등록됨.
    val cameraUri = remember {
        val dir = java.io.File(context.cacheDir, "shared").apply { mkdirs() }
        androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", java.io.File(dir, "bizcert.jpg")
        )
    }
    fun runBizOcr(uri: android.net.Uri) {
        scope.launch {
            scanBusy = true
            val text = com.detailline.callfollowcrm.util.BizCertOcr.recognize(context, uri)
            scanBusy = false
            val f = text?.let { com.detailline.callfollowcrm.util.BizCertOcr.parse(it) }
            if (f == null || !f.any) {
                android.widget.Toast.makeText(context, "글자를 잘 못 읽었어요. 밝은 곳에서 반듯하게 다시 찍어주세요", android.widget.Toast.LENGTH_LONG).show()
                return@launch
            }
            val filled = mutableListOf<String>()
            f.name?.let { name = it; filled += "상호" }
            f.owner?.let { owner = it; filled += "대표자" }
            f.bizNo?.let { bizNo = formatBizNo(it); filled += "사업자번호" }
            f.addr?.let { addr = it; filled += "주소" }
            android.widget.Toast.makeText(context, "${filled.joinToString("·")} 자동 입력했어요 · 확인 후 저장하세요", android.widget.Toast.LENGTH_LONG).show()
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) previewUri = uri }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok -> if (ok) previewUri = cameraUri }
    if (scanChooser) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { scanChooser = false },
            title = { Text("사업자등록증 불러오기", fontWeight = FontWeight.Bold) },
            text = { Text("등록증을 촬영하거나 앨범에서 골라주세요. 사업자번호·상호·대표자·주소를 자동으로 채워드려요. (사진은 저장 안 하고 글자만 읽어요)") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    scanChooser = false
                    runCatching { cameraLauncher.launch(cameraUri) }
                        .onFailure { android.widget.Toast.makeText(context, "카메라를 열 수 없어요. 앨범에서 선택해주세요", android.widget.Toast.LENGTH_SHORT).show() }
                }) { Text("사진 촬영", fontWeight = FontWeight.Bold, color = TossBlue) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    scanChooser = false
                    galleryLauncher.launch(
                        androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }) { Text("앨범에서 선택", color = TossTextSecondary) }
            }
        )
    }
    // 사진 확인 미리보기 — 크게 보고 맞으면 채우기. 바깥 탭으론 안 닫힘, 뒤로가기/버튼으로만. (2026-07-04 사장님)
    previewUri?.let { uri ->
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { previewUri = null },
            properties = androidx.compose.ui.window.DialogProperties(
                dismissOnClickOutside = false, dismissOnBackPress = true, usePlatformDefaultWidth = false
            )
        ) {
            Column(Modifier.fillMaxSize().background(Color(0xFF0E1116))) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("이 사진이 맞나요?", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                        Text("아래로 넘겨 확인한 뒤 '자동 채우기'를 눌러요", color = Color(0xFFAEB6C2), fontSize = 12.sp)
                    }
                    Text("✕", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { previewUri = null }.padding(8.dp))
                }
                // 전체폭 이미지 — 세로 스크롤로 긴 등록증도 훑어봄(스크롤해도 안 닫힘).
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                    coil.compose.AsyncImage(
                        model = uri, contentDescription = "사업자등록증 미리보기",
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = androidx.compose.ui.layout.ContentScale.FillWidth
                    )
                }
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(13.dp)).background(Color(0xFF2A2F37))
                            .clickable { previewUri = null; scanChooser = true }.padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("다시 선택", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                    Box(
                        Modifier.weight(1.6f).clip(RoundedCornerShape(13.dp)).background(TossBlue)
                            .clickable { val u = uri; previewUri = null; runBizOcr(u) }.padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("이 사진으로 자동 채우기", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold) }
                }
            }
        }
    }

    // 전화번호 자동 채움 — 비어 있으면 유심에서 내 번호 읽기 시도(한국은 빈 값 자주 옴 → 실패해도 무해).
    LaunchedEffect(Unit) {
        if (phone.isBlank()) {
            val sim = com.detailline.callfollowcrm.util.DevicePhoneNumber.readSimNumber(context)
            if (sim.isNotBlank()) phone = sim.filter { it.isDigit() }
        }
    }
    // "내 번호 불러오기" — 구글 전화번호 힌트(한 번 탭). 자동 읽기 실패 보강.
    val hintLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            runCatching {
                val num = com.google.android.gms.auth.api.identity.Identity
                    .getSignInClient(context).getPhoneNumberFromIntent(result.data)
                val norm = com.detailline.callfollowcrm.util.DevicePhoneNumber.normalizeKorean(num)
                if (norm.isNotBlank()) phone = norm.filter { it.isDigit() }
            }
        }
    }
    val launchPhoneHint = {
        runCatching {
            val req = com.google.android.gms.auth.api.identity.GetPhoneNumberHintIntentRequest.builder().build()
            com.google.android.gms.auth.api.identity.Identity.getSignInClient(context)
                .getPhoneNumberHintIntent(req)
                .addOnSuccessListener { pi ->
                    runCatching {
                        hintLauncher.launch(
                            androidx.activity.result.IntentSenderRequest.Builder(pi.intentSender).build()
                        )
                    }
                }
                .addOnFailureListener {
                    android.widget.Toast.makeText(context, "번호를 자동으로 못 불러왔어요. 직접 입력해주세요", android.widget.Toast.LENGTH_SHORT).show()
                }
        }
        Unit
    }

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
                    .imePadding()
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

                // 사업자등록증 스캔 → OCR 자동 입력 (2026-07-04 사장님)
                BizCertScanCard(busy = scanBusy) { scanChooser = true }
                Spacer(Modifier.height(14.dp))

                Field("상호 (업체명)", name, placeholder = "예: ○○ 줄눈") { name = it }
                Field("대표자 이름", owner, placeholder = "예: 정민수") { owner = it }
                FormattedField("사업자등록번호 (선택)", bizNo, ::formatBizNo, KeyboardType.Number, "123-45-67890") { bizNo = it }
                Field("주소 (선택)", addr, placeholder = "예: 서울 강동구") { addr = it }
                // 대표 전화번호 — 견적서·접수서에 표시(고객에게 나감). 로그인/푸시용 개인번호와 분리. (2026-07-04 사장님)
                FieldLabel("대표 전화번호")
                SheetTextField(
                    value = phone,
                    onValueChange = { phone = it.filter { c -> c.isDigit() }.take(11) },
                    placeholder = "010-0000-0000",
                    keyboardType = KeyboardType.Phone,
                    visualTransformation = com.detailline.callfollowcrm.presentation.component.PhoneHyphenTransformation
                )
                Text(
                    "견적서·접수서에 표시돼요. 개인 번호가 부담되면 대표(안내) 번호를 넣으세요. (로그인 번호는 안 바뀌어요)",
                    fontSize = 11.5.sp, color = TossTextTertiary, lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 5.dp, start = 2.dp)
                )
                // 직접 입력 대신 한 번 탭으로 내 폰 번호 불러오기.
                Text(
                    "📱 내 번호 불러오기",
                    fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = TossBlue,
                    modifier = Modifier
                        .padding(top = 6.dp, start = 2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { launchPhoneHint() }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                )
                Field("직인 문구 (도장에 들어갈 글자)", seal, placeholder = "예: ○○ 줄눈") { seal = it }

                // ── 입금 계좌 (협업 현장 정산용) ──
                Spacer(Modifier.height(8.dp))
                Text("입금 계좌 (협업 정산용)", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = TossTextSecondary,
                    modifier = Modifier.padding(start = 2.dp, top = 6.dp))
                Spacer(Modifier.height(2.dp))
                Text("다른 사장님과 협업한 현장을 끝내면, 완료 알림에 이 계좌가 같이 전달돼요.",
                    fontSize = 12.sp, color = TossTextTertiary, modifier = Modifier.padding(start = 2.dp))
                // 은행 — 클릭 선택(오타 방지). 한 글자만 쳐도 필터. 목록에 없으면 직접 입력. (공용 컴포넌트)
                BankPickerField(
                    label = "은행",
                    bank = bank,
                    open = bankOpen,
                    query = bankQuery,
                    onToggle = { bankOpen = !bankOpen; bankQuery = "" },
                    onQuery = { bankQuery = it },
                    onPick = { bank = it; bankOpen = false; bankQuery = "" }
                )
                FormattedField("계좌번호", accountNo, ::formatAccountNo, KeyboardType.Number, "예: 1234-5678-9012") { accountNo = it }
                Field("예금주 (선택)", accountHolder, placeholder = "비우면 대표자 이름") { accountHolder = it }

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
            //   navigationBarsPadding: 안드로이드15+ edge-to-edge 강제(예: 갤S23U/안드16)에서 저장 버튼이
            //   홈버튼(네비바)에 가리던 것 방지. S9 등 옛 기기는 navbar inset 0 라 영향 없음. (2026-06-22 사장님)
            Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp)) {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(TossBlue)
                        .clickable {
                            prefs.bizName = name; prefs.bizOwner = owner; prefs.bizNo = bizNo
                            prefs.bizAddr = addr; prefs.bizRepPhone = formatPhoneInput(phone)
                            prefs.bizSeal = seal.ifBlank { name }
                            prefs.bizQuoteValidDays = validDays.toIntOrNull()?.coerceIn(1, 365) ?: 14
                            prefs.bizBank = bank; prefs.bizAccountNo = accountNo; prefs.bizAccountHolder = accountHolder
                            // 번호 저장 즉시 FCM 토큰 서버 등록 — 앱 재시작 안 기다리게(즉시 푸시용). (2026-06-12)
                            runCatching {
                                val appCtx = context.applicationContext as? com.detailline.callfollowcrm.CallFollowCrmApplication
                                val ph = phone.filter { it.isDigit() }
                                if (appCtx != null && ph.length >= 9) {
                                    com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                                        .addOnSuccessListener { token ->
                                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                                runCatching { appCtx.container.pushRegisterRepository.register(ph, token) }
                                            }
                                        }
                                }
                            }
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

/** 사업자등록증 스캔 카드 — 탭하면 촬영/앨범 선택 → 온디바이스 OCR 자동 입력. (2026-07-04 사장님) */
@Composable
private fun BizCertScanCard(busy: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFEFF4FF))
            .clickable(enabled = !busy) { onClick() }.padding(13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(Color.White), contentAlignment = Alignment.Center) {
            Text("📷", fontSize = 19.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(if (busy) "등록증 읽는 중…" else "사업자등록증으로 자동 채우기",
                fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary)
            Spacer(Modifier.height(2.dp))
            Text("찍으면 사업자번호·상호·대표자·주소를 자동 입력 (로컬 처리)",
                fontSize = 11.5.sp, color = TossTextTertiary)
        }
        Spacer(Modifier.width(8.dp))
        if (busy) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = TossBlue)
        else Text("›", fontSize = 18.sp, color = TossTextTertiary)
    }
}

@Composable
private fun Field(label: String, value: String, keyboard: KeyboardType = KeyboardType.Text, placeholder: String = "", onChange: (String) -> Unit) {
    // 프로토 .sheet-label + .sheet-input.
    FieldLabel(label)
    SheetTextField(value = value, onValueChange = onChange, placeholder = placeholder, keyboardType = keyboard)
}

/** 자동 하이픈(전화/사업자번호) 칸 — 커서 꼬임 방지 FormattedTextField 사용. */
@Composable
private fun FormattedField(
    label: String, value: String, format: (String) -> String,
    keyboard: KeyboardType, placeholder: String, onChange: (String) -> Unit
) {
    FieldLabel(label)
    com.detailline.callfollowcrm.presentation.component.FormattedTextField(
        value = value, onValueChange = onChange, format = format,
        placeholder = placeholder, keyboardType = keyboard
    )
}

/** 사업자등록번호 자동 하이픈 — XXX-XX-XXXXX (숫자만, 최대 10자리). 입력 중 자동 삽입. */
private fun formatBizNo(raw: String): String {
    val d = raw.filter { it.isDigit() }.take(10)
    return buildString {
        for (i in d.indices) {
            if (i == 3 || i == 5) append('-')
            append(d[i])
        }
    }
}

/** 전화번호 자동 하이픈 — 휴대폰 010-XXXX-XXXX / 서울 02-XXXX-XXXX / 그 외 3-3(4)-4. 입력 중 자동. */
private fun formatPhoneInput(raw: String): String {
    val d = raw.filter { it.isDigit() }.take(11)
    return when {
        d.startsWith("02") -> when {
            d.length <= 2 -> d
            d.length <= 5 -> "${d.take(2)}-${d.drop(2)}"
            d.length <= 9 -> "${d.take(2)}-${d.substring(2, d.length - 4)}-${d.takeLast(4)}"
            else -> "${d.take(2)}-${d.substring(2, 6)}-${d.takeLast(4)}"
        }
        d.length <= 3 -> d
        d.length <= 7 -> "${d.take(3)}-${d.drop(3)}"
        d.length <= 10 -> "${d.take(3)}-${d.substring(3, d.length - 4)}-${d.takeLast(4)}"
        else -> "${d.take(3)}-${d.substring(3, 7)}-${d.takeLast(4)}"
    }
}

