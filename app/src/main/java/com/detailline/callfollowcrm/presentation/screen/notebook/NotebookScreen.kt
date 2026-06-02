package com.detailline.callfollowcrm.presentation.screen.notebook

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.detailline.callfollowcrm.data.local.entity.NotebookContactEntity
import com.detailline.callfollowcrm.presentation.component.TossCard
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossBlueSoft
import com.detailline.callfollowcrm.presentation.theme.TossDivider
import com.detailline.callfollowcrm.presentation.theme.TossError
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossSuccess
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary
import com.detailline.callfollowcrm.util.DateTimeUtils
import com.detailline.callfollowcrm.util.MoneyFormatter
import com.detailline.callfollowcrm.util.PhoneNumberFormatter

/**
 * 수첩 (2026-06-01) — 일당/거래처 한 곳 관리. 설정에서 진입.
 *   탭 [일당][거래처] + 카드(이름·분류·번호·[전화][문자]) + FAB 추가 + 탭→편집/삭제.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookScreen(
    viewModel: NotebookViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val tab by viewModel.tabState.collectAsState()
    val workers by viewModel.workers.collectAsState()
    val vendors by viewModel.vendors.collectAsState()
    val workerPhrases by viewModel.workerPhrases.collectAsState()
    val vendorPhrases by viewModel.vendorPhrases.collectAsState()
    val sitesByWorker by viewModel.sitesByWorker.collectAsState()
    val list = if (tab == NotebookTab.WORKER) workers else vendors

    // 편집 대상: null=닫힘, id=0 추가, id>0 수정.
    var editing by remember { mutableStateOf<NotebookContactEntity?>(null) }
    // 문자 보낼 대상 (자주 쓰는 문구 시트).
    var smsTarget by remember { mutableStateOf<NotebookContactEntity?>(null) }
    // 함께한 현장 보기 대상.
    var sitesTarget by remember { mutableStateOf<NotebookContactEntity?>(null) }

    Scaffold(
        containerColor = TossGrayBg,
        topBar = {
            TopAppBar(
                title = { Text("수첩", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "뒤로", tint = TossTextPrimary) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TossGrayBg)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    editing = NotebookContactEntity(kind = tab.kind, name = "", createdAt = 0, updatedAt = 0)
                },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("${tab.label} 추가", fontWeight = FontWeight.SemiBold) },
                containerColor = TossBlue, contentColor = Color.White
            )
        }
    ) { inner ->
        Column(Modifier.padding(inner).fillMaxSize().background(TossGrayBg)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NotebookTab.values().forEach { t ->
                    val sel = t == tab
                    val cnt = if (t == NotebookTab.WORKER) workers.size else vendors.size
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                            .background(if (sel) TossTextPrimary else Color.White)
                            .clickable { viewModel.setTab(t) }.padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // 프로토 book-seg: "일당·알바 N" / "거래처 N"
                        Text("${t.label} $cnt", style = MaterialTheme.typography.titleSmall,
                            color = if (sel) Color.White else TossTextSecondary, fontWeight = FontWeight.Bold)
                    }
                }
            }
            // 프로토 수첩 info-note 안내 배너
            val noteText = if (tab == NotebookTab.WORKER)
                "필요할 때 부르는 일당·알바를 모아두는 곳이에요. 분류로 등록해두면 필요한 사람만 골라 부르기 쉬워요."
            else
                "자재·협력·장비 등 자주 거래하는 곳을 모아두세요. 일정 등록 때 탭 한 번이면 번호가 자동 연결돼요."
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 0.dp)
                    .clip(RoundedCornerShape(12.dp)).background(TossBlueSoft)
                    .padding(horizontal = 13.dp, vertical = 11.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(Icons.Default.Person, null, tint = TossBlue, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(7.dp))
                Text(noteText, fontSize = 12.5.sp, color = TossBlue, fontWeight = FontWeight.Medium, lineHeight = 17.sp)
            }
            Spacer(Modifier.height(10.dp))
            if (list.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📓", fontSize = 32.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("${tab.label}이 아직 없어요", color = TossTextSecondary,
                            style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text("아래 '+ ${tab.label} 추가'로 등록하세요", color = TossTextTertiary,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(list, key = { it.id }) { c ->
                        ContactCard(
                            c,
                            siteCount = if (c.kind == NotebookContactEntity.KIND_WORKER) sitesByWorker[c.id]?.size ?: 0 else 0,
                            onEdit = { editing = c },
                            onSms = { smsTarget = c },
                            onSites = { sitesTarget = c }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    editing?.let { target ->
        ContactDialog(
            target = target,
            onSave = { name, phone, tag, memo, wage, wageType ->
                if (target.id > 0) viewModel.update(target.id, name, phone, tag, memo, wage, wageType)
                else viewModel.add(target.kind, name, phone, tag, memo, wage, wageType)
                editing = null
            },
            onDelete = if (target.id > 0) { { viewModel.delete(target.id); editing = null } } else null,
            onDismiss = { editing = null }
        )
    }

    smsTarget?.let { target ->
        val phrases = if (target.kind == NotebookContactEntity.KIND_WORKER) workerPhrases else vendorPhrases
        PhraseSheet(
            target = target,
            phrases = phrases,
            onPick = { body ->
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${target.phone}"))
                    .putExtra("sms_body", body)
                runCatching { context.startActivity(intent) }
                smsTarget = null
            },
            onAddPhrase = { viewModel.addPhrase(target.kind, it) },
            onDeletePhrase = { idx -> viewModel.deletePhrase(target.kind, idx) },
            onDismiss = { smsTarget = null }
        )
    }

    sitesTarget?.let { target ->
        val sites = sitesByWorker[target.id].orEmpty()
        AlertDialog(
            onDismissRequest = { sitesTarget = null },
            title = { Text("${target.name} · 함께한 현장 ${sites.size}회", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    if (sites.isEmpty()) {
                        Text("아직 배정 기록이 없어요", color = TossTextTertiary)
                    } else {
                        sites.forEach { s ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(s.customerName, style = MaterialTheme.typography.bodyMedium,
                                        color = TossTextPrimary, fontWeight = FontWeight.SemiBold)
                                    Text(DateTimeUtils.formatKoreanDate(s.dayStartMs),
                                        style = MaterialTheme.typography.labelSmall, color = TossTextTertiary)
                                }
                                Text(MoneyFormatter.won(s.wage), style = MaterialTheme.typography.bodyMedium,
                                    color = TossError, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { sitesTarget = null }) { Text("닫기", color = TossTextSecondary) } }
        )
    }
}

@Composable
private fun PhraseSheet(
    target: NotebookContactEntity,
    phrases: List<String>,
    onPick: (String) -> Unit,
    onAddPhrase: (String) -> Unit,
    onDeletePhrase: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var editMode by remember { mutableStateOf(false) }
    var newPhrase by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${target.name}에게 문자", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(if (editMode) "완료" else "문구 편집",
                    style = MaterialTheme.typography.labelMedium, color = TossBlue, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { editMode = !editMode })
            }
        },
        text = {
            Column {
                phrases.forEachIndexed { i, p ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                .background(TossGrayBg)
                                .clickable(enabled = !editMode) { onPick(p) }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(p, style = MaterialTheme.typography.bodyMedium, color = TossTextPrimary)
                        }
                        if (editMode) {
                            Spacer(Modifier.width(6.dp))
                            Text("✕", color = TossError, fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { onDeletePhrase(i) }.padding(8.dp))
                        }
                    }
                }
                if (editMode) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        com.detailline.callfollowcrm.presentation.component.SheetTextField(
                            newPhrase, { newPhrase = it }, placeholder = "새 문구", modifier = Modifier.weight(1f)
                        )
                        Text("추가", color = if (newPhrase.isNotBlank()) TossBlue else TossTextTertiary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable {
                                if (newPhrase.isNotBlank()) { onAddPhrase(newPhrase); newPhrase = "" }
                            }.padding(horizontal = 12.dp))
                    }
                } else {
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .clickable { onPick("") }.padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("빈 문자로 열기", color = TossTextSecondary, style = MaterialTheme.typography.labelLarge) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기", color = TossTextSecondary) } }
    )
}

@Composable
private fun ContactCard(
    c: NotebookContactEntity,
    siteCount: Int,
    onEdit: () -> Unit,
    onSms: () -> Unit,
    onSites: () -> Unit
) {
    val context = LocalContext.current
    TossCard(onClick = onEdit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(c.name, style = MaterialTheme.typography.titleMedium,
                        color = TossTextPrimary, fontWeight = FontWeight.SemiBold)
                    if (c.tag.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Box(Modifier.clip(RoundedCornerShape(999.dp)).background(TossBlueSoft)
                            .padding(horizontal = 8.dp, vertical = 2.dp)) {
                            Text(c.tag, style = MaterialTheme.typography.labelSmall, color = TossBlue)
                        }
                    }
                }
                if (c.kind == NotebookContactEntity.KIND_WORKER && (c.wage ?: 0L) > 0L) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${if (c.wageType == NotebookContactEntity.WAGE_HOURLY) "시급" else "일당"} ${c.wage!! / 10_000L}만원",
                        style = MaterialTheme.typography.labelMedium, color = TossError, fontWeight = FontWeight.Bold
                    )
                }
                if (c.phone.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(PhoneNumberFormatter.format(c.phone),
                        style = MaterialTheme.typography.bodyMedium, color = TossTextSecondary)
                }
                if (c.memo.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(c.memo, style = MaterialTheme.typography.bodySmall, color = TossTextTertiary, maxLines = 1)
                }
                if (siteCount > 0) {
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier.clip(RoundedCornerShape(999.dp)).background(TossBlueSoft)
                            .clickable { onSites() }.padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text("함께한 현장 ${siteCount}회 ›", style = MaterialTheme.typography.labelSmall,
                            color = TossBlue, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (c.phone.isNotBlank()) {
                ActionPill("전화", TossSuccess) {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${c.phone}")))
                    }
                }
                Spacer(Modifier.width(6.dp))
                ActionPill("문자", TossBlue) { onSms() }
            }
        }
    }
}

@Composable
private fun ActionPill(label: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(999.dp)).background(color.copy(alpha = 0.12f))
            .clickable { onClick() }.padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Bold)
    }
}

private val WORKER_TAGS = listOf("줄눈", "실리콘", "코킹", "타일", "도배", "목공", "전기", "설비", "철거", "보조", "운전")
private val VENDOR_TAGS = listOf("자재", "협력", "장비")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContactDialog(
    target: NotebookContactEntity,
    onSave: (name: String, phone: String, tag: String, memo: String, wage: Long?, wageType: String) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isWorker = target.kind == NotebookContactEntity.KIND_WORKER
    val kindLabel = if (isWorker) "일당" else "거래처"
    val tagList = if (isWorker) WORKER_TAGS else VENDOR_TAGS
    var name by remember { mutableStateOf(target.name) }
    var phone by remember { mutableStateOf(target.phone) }
    var tag by remember { mutableStateOf(target.tag) }
    var memo by remember { mutableStateOf(target.memo) }
    var wageType by remember { mutableStateOf(target.wageType) }
    var wageManwon by remember { mutableStateOf(target.wage?.takeIf { it > 0 }?.let { (it / 10_000L).toString() } ?: "") }
    var customTagOpen by remember { mutableStateOf(target.tag.isNotBlank() && target.tag !in tagList) }

    // 연락처에서 불러오기 — ACTION_PICK Phone (READ_CONTACTS 권한 불필요, 선택한 1건만 접근).
    val pickContact = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            res.data?.data?.let { uri ->
                runCatching {
                    context.contentResolver.query(
                        uri,
                        arrayOf(
                            ContactsContract.CommonDataKinds.Phone.NUMBER,
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                        ), null, null, null
                    )?.use { cur ->
                        if (cur.moveToFirst()) {
                            val num = cur.getString(0) ?: ""
                            val nm = cur.getString(1) ?: ""
                            if (nm.isNotBlank()) name = nm
                            phone = PhoneNumberFormatter.formatProgressive(num)
                        }
                    }
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (target.id > 0) "$kindLabel 수정" else "$kindLabel 추가", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                // 연락처에서 불러오기 (추가일 때만)
                if (target.id == 0L) {
                    Row(
                        Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(12.dp))
                            .background(TossBlueSoft)
                            .clickable {
                                runCatching {
                                    pickContact.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
                                }
                            },
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Person, null, tint = TossBlue, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("연락처에서 불러오기", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TossBlue)
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f).height(1.dp).background(TossDivider))
                        Text("또는 직접 입력", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TossTextTertiary,
                            modifier = Modifier.padding(horizontal = 10.dp))
                        Box(Modifier.weight(1f).height(1.dp).background(TossDivider))
                    }
                }
                DialogField("이름", name) { name = it }
                DialogField("전화번호", phone, KeyboardType.Phone) { phone = PhoneNumberFormatter.formatProgressive(it) }

                // 분류 칩
                Column(Modifier.padding(vertical = 4.dp)) {
                    com.detailline.callfollowcrm.presentation.component.SheetFieldLabel("분류")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        tagList.forEach { tg ->
                            NbChip(tg, selected = !customTagOpen && tag == tg) { tag = tg; customTagOpen = false }
                        }
                        NbChip("직접", selected = customTagOpen) {
                            customTagOpen = true
                            if (tag in tagList) tag = ""
                        }
                    }
                    if (customTagOpen) {
                        Spacer(Modifier.height(8.dp))
                        com.detailline.callfollowcrm.presentation.component.SheetTextField(
                            tag, { tag = it }, placeholder = "분류 직접 입력"
                        )
                    }
                }

                // 단가 (일당만)
                if (isWorker) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        com.detailline.callfollowcrm.presentation.component.SheetFieldLabel("단가 (선택)")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NbChip("일당", selected = wageType == NotebookContactEntity.WAGE_DAILY) { wageType = NotebookContactEntity.WAGE_DAILY }
                            NbChip("시급", selected = wageType == NotebookContactEntity.WAGE_HOURLY) { wageType = NotebookContactEntity.WAGE_HOURLY }
                        }
                        Spacer(Modifier.height(8.dp))
                        com.detailline.callfollowcrm.presentation.component.SheetTextField(
                            wageManwon, { wageManwon = it.filter { c -> c.isDigit() } },
                            placeholder = "만원 (예: 18)", keyboardType = KeyboardType.Number
                        )
                    }
                }

                DialogField("메모", memo) { memo = it }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val wage = wageManwon.toLongOrNull()?.takeIf { it > 0 }?.let { it * 10_000L }
                    onSave(name, phone, tag, memo, wage, wageType)
                },
                enabled = name.isNotBlank()
            ) {
                Text("저장", color = if (name.isNotBlank()) TossBlue else TossTextTertiary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("삭제", color = TossError) }
                    Spacer(Modifier.width(4.dp))
                }
                TextButton(onClick = onDismiss) { Text("취소", color = TossTextSecondary) }
            }
        }
    )
}

/** 분류·단가 선택 칩. */
@Composable
private fun NbChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(999.dp))
            .background(if (selected) TossBlue else TossGrayBg)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else TossTextSecondary)
    }
}

@Composable
private fun DialogField(label: String, value: String, keyboard: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) {
    Column(Modifier.padding(vertical = 4.dp)) {
        com.detailline.callfollowcrm.presentation.component.SheetFieldLabel(label)
        com.detailline.callfollowcrm.presentation.component.SheetTextField(
            value, onChange, placeholder = "", keyboardType = keyboard
        )
    }
}
