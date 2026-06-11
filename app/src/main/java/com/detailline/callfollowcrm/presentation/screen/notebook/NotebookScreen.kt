package com.detailline.callfollowcrm.presentation.screen.notebook

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
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
    // 분류 필터 (탭 바뀌면 전체로 리셋).
    var filter by remember(tab) { mutableStateOf("all") }

    Scaffold(
        containerColor = TossGrayBg,
        topBar = {
            TopAppBar(
                title = { Text("수첩", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "뒤로", tint = TossTextPrimary) }
                },
                actions = {
                    // 프로토 우상단 + (흰 둥근 버튼)
                    Box(
                        Modifier.padding(end = 12.dp).size(38.dp).clip(RoundedCornerShape(11.dp))
                            .background(Color.White)
                            .clickable { editing = NotebookContactEntity(kind = tab.kind, name = "", createdAt = 0, updatedAt = 0) },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Add, "추가", tint = TossBlue, modifier = Modifier.size(20.dp)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TossGrayBg)
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
            // 프로토 invite — "+ 사람 추가" / "+ 거래처 추가" (점선)
            val inviteLabel = if (tab == NotebookTab.WORKER) "사람 추가" else "거래처 추가"
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(12.dp))
                    .clickable { editing = NotebookContactEntity(kind = tab.kind, name = "", createdAt = 0, updatedAt = 0) }
                    .drawBehind {
                        drawRoundRect(
                            color = TossDivider,
                            style = Stroke(width = 1.2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(11f, 8f), 0f)),
                            cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
                        )
                    }
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, null, tint = TossBlue, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(inviteLabel, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = TossBlue)
                }
            }
            // 프로토 cfilter — 분류 필터 (분류 2개 이상일 때)
            val cats = remember(list) { list.map { it.tag }.filter { it.isNotBlank() }.distinct() }
            val shown = if (filter == "all") list else list.filter { it.tag == filter }
            if (cats.size > 1) {
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    CFilterChip("전체", list.size, filter == "all") { filter = "all" }
                    cats.forEach { cat ->
                        CFilterChip(cat, list.count { it.tag == cat }, filter == cat) { filter = cat }
                    }
                }
            }
            // 프로토 sec-sub — 등록된 사람/거래처 N
            val secSub = if (filter == "all") {
                if (tab == NotebookTab.WORKER) "등록된 사람 ${list.size}명" else "등록된 거래처 ${list.size}곳"
            } else "$filter ${shown.size}${if (tab == NotebookTab.WORKER) "명" else "곳"}"
            Spacer(Modifier.height(12.dp))
            Text(secSub, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = TossTextSecondary,
                modifier = Modifier.padding(horizontal = 18.dp))
            Spacer(Modifier.height(8.dp))
            if (shown.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(vertical = 30.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (list.isEmpty()) "${tab.label}이 아직 없어요" else "이 분류엔 아직 없어요",
                        color = TossTextTertiary, style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(shown, key = { it.id }) { c ->
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
                com.detailline.callfollowcrm.presentation.util.ForceDialogResize()
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

private val AV_TINTS = listOf(
    Color(0xFFE8F0FE) to Color(0xFF3182F6),
    Color(0xFFE6F7EE) to Color(0xFF12B886),
    Color(0xFFFFF1E6) to Color(0xFFFB8C00),
    Color(0xFFF3ECFF) to Color(0xFF7C5CFC),
    Color(0xFFFFE9EF) to Color(0xFFF0436A),
)

@Composable
private fun ContactCard(
    c: NotebookContactEntity,
    siteCount: Int,
    onEdit: () -> Unit,
    onSms: () -> Unit,
    onSites: () -> Unit
) {
    val context = LocalContext.current
    val isWorker = c.kind == NotebookContactEntity.KIND_WORKER
    val tint = AV_TINTS[((c.id % AV_TINTS.size).toInt() + AV_TINTS.size) % AV_TINTS.size]
    TossCard {
        Column {
            // 프로토 wk-head — 아바타 + 이름·분류·번호·현장 + 단가
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(tint.first), contentAlignment = Alignment.Center) {
                    Text(c.name.take(1), color = tint.second, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(c.name, style = MaterialTheme.typography.titleMedium,
                            color = TossTextPrimary, fontWeight = FontWeight.Bold)
                        if (c.tag.isNotBlank()) {
                            Spacer(Modifier.width(6.dp))
                            Box(Modifier.clip(RoundedCornerShape(999.dp)).background(Color(0xFFFFF3D6))
                                .padding(horizontal = 8.dp, vertical = 2.dp)) {
                                Text(c.tag, style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFB07D00), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (c.phone.isNotBlank()) {
                            Text(PhoneNumberFormatter.format(c.phone),
                                style = MaterialTheme.typography.bodySmall, color = TossTextSecondary)
                        }
                        if (isWorker && siteCount > 0) {
                            if (c.phone.isNotBlank()) {
                                Text(" · ", style = MaterialTheme.typography.bodySmall, color = TossTextTertiary)
                            }
                            Text("함께한 현장 ${siteCount}회 ›", style = MaterialTheme.typography.labelSmall,
                                color = TossBlue, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable { onSites() })
                        }
                    }
                }
                if (isWorker && (c.wage ?: 0L) > 0L) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(if (c.wageType == NotebookContactEntity.WAGE_HOURLY) "시급" else "일당",
                            style = MaterialTheme.typography.labelSmall, color = TossTextTertiary)
                        Text("${c.wage!! / 10_000L}만원", style = MaterialTheme.typography.titleMedium,
                            color = TossTextPrimary, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
            // 프로토 wk-memo
            if (c.memo.isNotBlank()) {
                Spacer(Modifier.height(9.dp))
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(TossGrayBg)
                    .padding(horizontal = 11.dp, vertical = 9.dp)) {
                    Text("📝 ${c.memo}", style = MaterialTheme.typography.bodySmall,
                        color = TossTextSecondary, maxLines = 2)
                }
            }
            // 프로토 wk-acts — 전화 / 문자 / 단가·메모
            Spacer(Modifier.height(11.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                NbActBtn("전화", TossSuccess, Modifier.weight(1f), enabled = c.phone.isNotBlank()) {
                    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${c.phone}"))) }
                }
                NbActBtn("문자", TossBlue, Modifier.weight(1f), enabled = c.phone.isNotBlank()) { onSms() }
                NbActBtn(if (isWorker) "단가·메모" else "메모·분류", TossTextSecondary, Modifier.weight(1f), enabled = true) { onEdit() }
            }
        }
    }
}

@Composable
private fun NbActBtn(label: String, color: Color, modifier: Modifier = Modifier, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(10.dp)).background(TossGrayBg)
            .clickable(enabled = enabled) { onClick() }.padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            color = if (enabled) color else TossTextTertiary)
    }
}

@Composable
private fun CFilterChip(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(999.dp)).background(if (selected) TossBlue else Color.White)
            .clickable { onClick() }.padding(horizontal = 13.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else TossTextSecondary)
        Spacer(Modifier.width(5.dp))
        Text("$count", fontSize = 12.sp, fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else TossTextTertiary)
    }
}

private val WORKER_TAGS = listOf("줄눈", "실리콘", "코킹", "타일", "도배", "목공", "전기", "설비", "철거", "보조", "운전")
private val VENDOR_TAGS = listOf("자재", "협력", "장비", "운송", "기타")

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
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

    val isEdit = target.id > 0
    val title = if (isEdit) (if (isWorker) "${target.name} 정보" else target.name)
    else (if (isWorker) "일당·알바 추가" else "거래처 추가")
    val sub = when {
        isEdit && isWorker -> "다음에 부를 때 보고 판단할 메모예요."
        isEdit -> "자주 쓰는 거래처 메모예요."
        isWorker -> "번호 외울 필요 없어요. 연락처에서 골라 담으세요."
        else -> "자재상·협력업체·장비 렌탈 — 자주 쓰는 곳을 담아요."
    }
    val catHint = if (isWorker) "전기·목공처럼 하는 일로, 또는 보조·기공·반장처럼 본인 방식대로 나눠도 돼요."
    else "자재·협력·장비처럼 본인 방식대로 나눠도 돼요."
    val showMemo = !isWorker || isEdit // 프로토: 일당 추가엔 메모 없음(편집엔 있음), 거래처는 항상 있음
    val noRipple = remember { MutableInteractionSource() }
    BackHandler(enabled = true) { onDismiss() }

    // ModalBottomSheet(별도 윈도우)는 갤S9/안드10 에서 키보드가 입력칸을 가림(reference_modalbottomsheet_keyboard).
    //   → 액티비티 창 인라인 오버레이로 그려서 키보드 뜨면 카드가 위로 밀려 올라가게(imePadding). (2026-06-11 사장님 신고)
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))
            .clickable(interactionSource = noRipple, indication = null) { onDismiss() }
    ) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .background(Color.White)
                .clickable(interactionSource = noRipple, indication = null) { }
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 20.dp)
                .heightIn(max = 620.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // grip
            Box(
                Modifier.align(Alignment.CenterHorizontally).padding(bottom = 12.dp)
                    .width(38.dp).height(4.dp).clip(RoundedCornerShape(999.dp)).background(TossDivider)
            )
            Text(title, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(sub, fontSize = 13.sp, color = TossTextTertiary)
            Spacer(Modifier.height(14.dp))
            // 연락처에서 불러오기 (추가일 때만)
            if (!isEdit) {
                Row(
                    Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp))
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
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(1.dp).background(TossDivider))
                    Text("또는 직접 입력", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TossTextTertiary,
                        modifier = Modifier.padding(horizontal = 10.dp))
                    Box(Modifier.weight(1f).height(1.dp).background(TossDivider))
                }
            }
            // 이름 / 전화번호
            com.detailline.callfollowcrm.presentation.component.SheetFieldLabel(if (isWorker) "이름" else "상호 / 이름")
            com.detailline.callfollowcrm.presentation.component.SheetTextField(
                name, { name = it }, placeholder = if (isWorker) "예: 김반장" else "예: 대성 자재상"
            )
            Spacer(Modifier.height(10.dp))
            com.detailline.callfollowcrm.presentation.component.SheetFieldLabel("전화번호")
            com.detailline.callfollowcrm.presentation.component.FormattedTextField(
                phone, { phone = it }, PhoneNumberFormatter::formatProgressive,
                placeholder = "010-0000-0000", keyboardType = KeyboardType.Phone
            )
            Spacer(Modifier.height(10.dp))
            // 분류 + 편집 + hint
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                com.detailline.callfollowcrm.presentation.component.SheetFieldLabel("분류")
                Spacer(Modifier.width(7.dp))
                Text("편집", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TossBlue,
                    modifier = Modifier.clickable { customTagOpen = true; if (tag in tagList) tag = "" })
            }
            Text(catHint, fontSize = 12.sp, color = TossTextTertiary, lineHeight = 17.sp,
                modifier = Modifier.padding(bottom = 7.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tagList.forEach { tg ->
                    NbChip(tg, selected = !customTagOpen && tag == tg) { tag = tg; customTagOpen = false }
                }
                NbChip("+ 직접", selected = customTagOpen) {
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
            // 단가 (일당만)
            if (isWorker) {
                Spacer(Modifier.height(12.dp))
                com.detailline.callfollowcrm.presentation.component.SheetFieldLabel("단가 (선택)")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NbChip("일당", selected = wageType == NotebookContactEntity.WAGE_DAILY) { wageType = NotebookContactEntity.WAGE_DAILY }
                    NbChip("시급", selected = wageType == NotebookContactEntity.WAGE_HOURLY) { wageType = NotebookContactEntity.WAGE_HOURLY }
                }
                Spacer(Modifier.height(8.dp))
                com.detailline.callfollowcrm.presentation.component.SheetTextField(
                    wageManwon, { wageManwon = it.filter { c -> c.isDigit() } },
                    placeholder = "만원 (예: 18)", keyboardType = KeyboardType.Number,
                    visualTransformation = com.detailline.callfollowcrm.presentation.component.ThousandsCommaTransformation
                )
            }
            // 메모
            if (showMemo) {
                Spacer(Modifier.height(12.dp))
                com.detailline.callfollowcrm.presentation.component.SheetFieldLabel(if (isWorker) "메모" else "메모 (선택)")
                com.detailline.callfollowcrm.presentation.component.SheetTextField(
                    memo, { memo = it }, placeholder = "취급 품목·단가·외상 여부 / 잘하는 일·주의할 점"
                )
            }
            // CTA — 프로토 sheet-cta
            Spacer(Modifier.height(18.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(if (name.isNotBlank()) TossBlue else TossDivider)
                    .clickable(enabled = name.isNotBlank()) {
                        val wage = wageManwon.toLongOrNull()?.takeIf { it > 0 }?.let { it * 10_000L }
                        onSave(name, phone, tag, memo, wage, wageType)
                    }
                    .padding(vertical = 15.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(if (isEdit) "저장" else "수첩에 추가", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            if (onDelete != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    if (isWorker) "이 사람 빼기" else "이 거래처 빼기",
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TossError,
                    modifier = Modifier.fillMaxWidth().clickable { onDelete() }.padding(vertical = 2.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
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
