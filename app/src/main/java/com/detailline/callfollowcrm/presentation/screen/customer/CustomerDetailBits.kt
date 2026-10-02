package com.detailline.callfollowcrm.presentation.screen.customer

import com.detailline.callfollowcrm.presentation.util.bottomBarClearance
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Add
import com.detailline.callfollowcrm.presentation.theme.AppShape
import com.detailline.callfollowcrm.presentation.theme.AppSpace
import com.detailline.callfollowcrm.presentation.theme.AppType
import androidx.compose.material.icons.filled.Check
import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.theme.LightColors
import com.detailline.callfollowcrm.util.copyToClip
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Send
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.data.local.entity.RecordingAttachmentEntity
import com.detailline.callfollowcrm.presentation.component.CelebrationOverlay
import com.detailline.callfollowcrm.presentation.component.SectionLabel
import com.detailline.callfollowcrm.presentation.component.TossCard
import com.detailline.callfollowcrm.presentation.component.tossCardShadow
import com.detailline.callfollowcrm.presentation.component.pressScale
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.detailline.callfollowcrm.presentation.component.TossChip
import com.detailline.callfollowcrm.presentation.component.TossPrimaryButton
import com.detailline.callfollowcrm.presentation.component.TossSecondaryButton
import com.detailline.callfollowcrm.presentation.component.vibrateCelebration
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossBlueDark
import com.detailline.callfollowcrm.presentation.theme.TossBlueSoft
import com.detailline.callfollowcrm.presentation.theme.TossDivider
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossError
import com.detailline.callfollowcrm.presentation.theme.TossSuccess
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary
import com.detailline.callfollowcrm.util.DateTimeUtils
import com.detailline.callfollowcrm.util.PhoneNumberFormatter
import com.detailline.callfollowcrm.util.splitSiteAddress
import kotlinx.coroutines.launch
import com.detailline.callfollowcrm.presentation.util.keyboardPadding
import com.detailline.callfollowcrm.util.PhoneKey
import androidx.compose.ui.graphics.graphicsLayer

// ======================================================================
//  🍃 고객 정보의 작은 조각 — 주소 고치기 창·접수서 확인 창·칸·딱지
//
//  CustomerDetailScreen.kt 에서 **그대로 옮겨온** 조각들이다. (2026-10-02)
//  옮긴 기준: 그 파일의 **다른 private 이름을 하나도 안 쓰는** 잎(leaf) 선언.
//  같은 패키지라 `private` → `internal` 한 글자만 바뀌었고 **부르는 곳은 그대로**다.
//  (Fable 점검 ✋ — 큰 화면을 통째로 쪼개는 건 위험하니 **잎부터** 떼어낸다)
// ======================================================================

/** 「내가 보냄   9월 25일 10:12」 한 줄. 이름 칸 폭을 맞춰 두 줄이 나란히 읽히게. */
@Composable
internal fun IssuedKv(label: String, value: String, valueColor: Color) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(
            label, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = TossTextTertiary,
            modifier = Modifier.width(74.dp)
        )
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = valueColor)
    }
}

/** 그때그때 할 일 한 개. primary = 진한 파랑(지금 해야 하는 일). */
@Composable
internal fun IssuedBtn(label: String, primary: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(if (primary) TossBlue else AppTheme.colors.surface)
            .then(
                if (primary) Modifier
                else Modifier.border(1.dp, AppTheme.colors.line, RoundedCornerShape(10.dp))
            )
            .clickable { onClick() }.padding(vertical = 10.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Text(
            label, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
            color = if (primary) Color.White else TossBlue
        )
    }
}

/** 시공접수서 발행 이력 다시 보기 — 보낸 내용 요약(확인 개념) + 링크 열기/복사. 고객이 채운 주소·상세는 링크(서버)에서. */
@Composable
internal fun IntakeReviewDialog(
    doc: com.detailline.callfollowcrm.data.local.entity.IssuedDocEntity,
    onCopyLink: (String) -> Unit,
    onOpenLink: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val url = doc.url?.takeIf { it.isNotBlank() }
    AlertDialog(
        containerColor = Color.White,
        tonalElevation = 0.dp,
        onDismissRequest = onDismiss,
        title = { Text("시공접수서", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                val dateStr = doc.workDateMs
                    ?.let { java.text.SimpleDateFormat("M월 d일 (E)", java.util.Locale.KOREA).format(java.util.Date(it)) }
                    ?: "협의 후 확정"
                Text("· 시공 예정일 : $dateStr", fontSize = 13.sp, color = TossTextSecondary, lineHeight = 22.sp)
                doc.itemsText?.takeIf { it.isNotBlank() }?.let {
                    Text("· 견적 내용 : $it", fontSize = 13.sp, color = TossTextSecondary, lineHeight = 22.sp)
                }
                if (doc.totalWon > 0L) {
                    Text("· 금액 : ${java.text.NumberFormat.getNumberInstance(java.util.Locale.KOREA).format(doc.totalWon)}원",
                        fontSize = 13.sp, color = TossTextSecondary, lineHeight = 22.sp)
                }
                doc.memo?.takeIf { it.isNotBlank() }?.let {
                    Text("· 비고 : $it", fontSize = 13.sp, color = TossTextSecondary, lineHeight = 22.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text("고객이 작성한 주소·상세는 아래 링크에서 확인돼요.", fontSize = 11.5.sp, color = TossTextTertiary, lineHeight = 16.sp)
            }
        },
        confirmButton = {
            if (url != null) TextButton(onClick = { onOpenLink(url) }) {
                Text("링크 열기", color = TossBlue, fontWeight = FontWeight.Bold)
            } else TextButton(onClick = onDismiss) { Text("닫기", color = TossTextSecondary) }
        },
        dismissButton = {
            if (url != null) TextButton(onClick = { onCopyLink(url) }) {
                Text("링크 복사", color = TossTextSecondary)
            }
        }
    )
}

/** 작은 썸네일 가로 나열. 한 줄에 최대 3장, 더 많으면 줄바꿈. 탭 = onImageTap. */
@Composable
internal fun ImageThumbnailRow(uris: List<android.net.Uri>, onTap: (android.net.Uri) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        uris.chunked(3).forEach { rowUris ->
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                rowUris.forEach { uri ->
                    coil.compose.AsyncImage(
                        model = uri,
                        contentDescription = "첨부 사진",
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                            .background(TossGrayBg)
                            .clickable { onTap(uri) }
                    )
                }
            }
        }
    }
}

/**
 * 2026-05-29 킬러콘텐츠 5단계 — 고객 페르소나 카드.
 *
 * cowork 의 prepare-reply 가 Haiku 4.5 로 자동 생성/24h 캐시. 안드는 GET 으로 표시만.
 * null 또는 isEmpty 면 호출처에서 숨김.
 *
 * 사장님 가치: 고객 다시 안 만나도 "어떤 사람" 한눈에 — 답변/응대 톤 맞추기 용이.
 */
/** 상단 탭에 내용이 없을 때 보여줄 담백한 안내 카드. (2026-07-18 탭 재배치) */
@Composable
internal fun DetailTabEmpty(text: String) {
    TossCard {
        Text(
            text, fontSize = 13.sp, color = TossTextTertiary, lineHeight = 20.sp,
            modifier = Modifier.padding(vertical = 8.dp)
        )
    }
}

@Composable
internal fun PersonaLine(emoji: String, text: String?) {
    if (text.isNullOrBlank()) return
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalAlignment = androidx.compose.ui.Alignment.Top
    ) {
        Text(
            emoji,
            fontSize = 13.sp,
            modifier = Modifier.padding(end = 8.dp, top = 1.dp)
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = TossTextPrimary
        )
    }
}

/**
 * 카드 제목 왼쪽 아이콘 칩. (2026-09-20 사장님)
 *   전엔 👤 📍 💰 📷 이모지였다. 이모지는 **폰마다 그림이 다르고**, 특히 📍 는 새빨간 압정이라
 *   담긴 내용("주소 없음")보다 이모지가 더 튀었다. 앱이 직접 그리면 어느 폰에서나 같다.
 */
@Composable
internal fun CdTitleIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, palette: String) {
    val (bg, fg) = when (palette) {
        "blue" -> AppTheme.colors.primaryBg to AppTheme.colors.primaryText
        "amber" -> AppTheme.colors.cautionBg to AppTheme.colors.cautionText
        "green" -> AppTheme.colors.doneBg to AppTheme.colors.doneText
        else -> AppTheme.colors.surfaceMuted to AppTheme.colors.textSub
    }
    androidx.compose.foundation.layout.Box(
        Modifier.size(24.dp).clip(RoundedCornerShape(7.dp)).background(bg),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        androidx.compose.material3.Icon(icon, null, tint = fg, modifier = Modifier.size(12.dp))
    }
}

/**
 * 한 줄 = 이름(왼쪽) · 값(오른쪽) · 고치는 단추(같은 크기). (2026-09-20 사장님)
 *   예약·돈이 **같은 줄 모양**이라 눈이 오른쪽 끝을 따라 내려간다.
 */
@Composable
internal fun CdKv(
    label: String,
    value: String,
    valueColor: Color,
    trailing: androidx.compose.ui.graphics.vector.ImageVector? = null,
    divider: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    Column {
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
                .padding(vertical = 9.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text(label, fontSize = 13.sp, color = TossTextSecondary)
            Spacer(Modifier.weight(1f))
            Text(
                value, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = valueColor,
                textAlign = androidx.compose.ui.text.style.TextAlign.End
            )
            if (trailing != null) {
                Spacer(Modifier.width(9.dp))
                androidx.compose.foundation.layout.Box(
                    Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(AppTheme.colors.surfaceMuted),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    androidx.compose.material3.Icon(
                        trailing, null, tint = AppTheme.colors.textSub, modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
        if (divider) androidx.compose.foundation.layout.Box(
            Modifier.fillMaxWidth().height(1.dp).background(AppTheme.colors.line)
        )
    }
}

/**
 * 숫자 입력 → 표시는 천단위 콤마. 커서 매핑은 단순(끝으로 고정)으로 처리.
 * 사장님이 직접 타이핑하기 보다 칩으로 가산하는 경우가 많아 단순 매핑이 적합.
 */
internal val ThousandsSeparatorTransformation = androidx.compose.ui.text.input.VisualTransformation { text ->
    val raw = text.text
    if (raw.isEmpty()) return@VisualTransformation androidx.compose.ui.text.input.TransformedText(text, androidx.compose.ui.text.input.OffsetMapping.Identity)
    val number = raw.toLongOrNull() ?: return@VisualTransformation androidx.compose.ui.text.input.TransformedText(text, androidx.compose.ui.text.input.OffsetMapping.Identity)
    val formatted = java.text.NumberFormat.getNumberInstance(java.util.Locale.KOREA).format(number)
    androidx.compose.ui.text.input.TransformedText(
        androidx.compose.ui.text.AnnotatedString(formatted),
        object : androidx.compose.ui.text.input.OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = formatted.length
            override fun transformedToOriginal(offset: Int): Int = raw.length
        }
    )
}

/** 분류 타일 한 칸 — 큰 이모지 + 이름(두 줄까지) + 몇 명. (2026-09-19 사장님) */
@Composable
internal fun CategoryTile(
    emoji: String,
    name: String,
    count: Int,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) TossBlueSoft else Color.White)
            .border(
                if (selected) 1.5.dp else 1.dp,
                if (selected) TossBlue else TossDivider,
                RoundedCornerShape(14.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 13.dp, horizontal = 9.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
    ) {
        Text(emoji, fontSize = 21.sp)
        Spacer(Modifier.height(5.dp))
        Text(
            name, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
            color = if (selected) TossBlueDark else TossTextPrimary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            // 두 줄까지 — "인테리어 업체" 가 잘리면 타일의 의미가 없다.
            maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            lineHeight = 17.sp
        )
        if (count > 0) {
            Spacer(Modifier.height(2.dp))
            Text("${count}명", fontSize = 11.sp, color = TossTextTertiary)
        }
    }
}

/**
 * "📍 현장 주소" 카드 탭 시 뜨는 입력 다이얼로그 (2026-05-28, DB v15).
 *   - currentAddress: 현재 저장된 수동 주소 (있으면 초기값으로 prefill)
 *   - extractedSuggestion: 메시지 자동 추출 결과 (currentAddress 와 다르면 칩으로 제안 — 한 탭에 input 박힘)
 *   - onCopyExisting: 기존 표시 주소 복사 (옛 UX 보존, displayAddr 있을 때만)
 *   사장님 의도: 자동 추출이 부정확할 때 사장님이 직접 박을 수 있게. 신뢰 데이터는 사장님이.
 */
@Composable
internal fun AddressEditDialog(
    currentAddress: String?,
    extractedSuggestion: String?,
    onSave: (String?) -> Unit,
    onCopyExisting: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    // 2026-05-28 사장님 통점: 다이얼로그 입력 도중 [뒤로]/홈/잠금/전화 → 입력 날아감.
    //   composer 임시저장과 같은 원칙. remember → rememberSaveable 로 변경 → Bundle 저장 → recompose/destroy 살아남음.
    //   currentAddress 가 바뀌면 (다른 고객의 다이얼로그) 시드 새로 = key 로 분리.
    // 저장된 주소를 도로명(base) + 동·호수(detail)로 분리해서 다시 채운다.
    //   (2026-06-11 사장님 통점: 동·호수만 고치려 해도 전체가 주소칸에 들어가 처음부터 재검색 + "11동 22동" 중복 누적.)
    val (initBase, initDetail) = remember(currentAddress) { splitSiteAddress(currentAddress.orEmpty()) }
    var text by androidx.compose.runtime.saveable.rememberSaveable(currentAddress) {
        mutableStateOf(initBase)
    }
    // 동·호수(상세주소) 별도 입력 — 주소 검색은 도로명/지번까지만 주므로 검색 후 여기에 이어 적는다.
    //   (2026-06-10 사장님 통점: 검색 후 동/호수 칸이 따로 없어 같은 칸에 우겨넣어야 해 불편.)
    var detail by androidx.compose.runtime.saveable.rememberSaveable(currentAddress) {
        mutableStateOf(initDetail)
    }
    val detailFocus = remember { FocusRequester() }
    var focusDetail by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(focusDetail) {
        if (focusDetail) {
            runCatching { detailFocus.requestFocus() }
            focusDetail = false
        }
    }
    // 주소 검색(Daum 우편번호 WebView) — 선택 시 도로명주소 채우고 동/호수 칸으로 자동 포커스.
    var showSearch by remember { mutableStateOf(false) }
    if (showSearch) {
        com.detailline.callfollowcrm.presentation.component.AddressSearchDialog(
            onPicked = { picked -> text = picked; showSearch = false; focusDetail = true },
            onDismiss = { showSearch = false }
        )
    }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
            color = Color.White,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                com.detailline.callfollowcrm.presentation.util.ForceDialogResize()
                Text(
                    "현장 주소 등록",
                    style = MaterialTheme.typography.titleLarge,
                    color = TossTextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "직접 입력한 주소가 메시지 자동 인식보다 우선해요. 길찾기에도 이 주소를 써요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TossTextSecondary
                )
                Spacer(Modifier.height(14.dp))
                // 주소는 직접 타이핑 대신 반드시 검색으로 — 정확한 정규화 도로명주소 확보.
                //   (2026-06-10 사장님: 자유입력 칸 없애고 무조건 주소검색 한 번 하게.) 동/호수만 수동.
                //   2026-06-11 UI 개선: 회색 박스 + 검색 버튼이 둘 다 "검색 열기"라 헷갈림 →
                //     비었을 땐 검색 버튼 하나만, 고르면 주소 카드 + [변경] + 동·호수 (단계식).
                // 앱 안 모든 주소 칸이 같은 부품을 쓴다. (2026-09-23 사장님)
                val (dDong, dHo) = com.detailline.callfollowcrm.util.splitDongHo(detail)
                com.detailline.callfollowcrm.presentation.component.SiteAddressField(
                    address = text,
                    dong = dDong,
                    ho = dHo,
                    onSearch = { showSearch = true },
                    onDong = { detail = com.detailline.callfollowcrm.util.joinDongHo(it, dHo) },
                    onHo = { detail = com.detailline.callfollowcrm.util.joinDongHo(dDong, it) },
                )
                // 도로명 **직접 고치기** — 접어둔다. 평소엔 검색만 쓰지만, 문자에서 자동으로 뽑은
                //   주소에 잡텍스트가 끼는 일이 있어 사장님이 요청해 만든 기능이라 없애지 않는다.
                //   (2026-06-14 사장님: 읽기전용이라 "아직 뮥바음" 같은 걸 못 지웠음)
                if (text.isNotBlank()) {
                    var editRoad by androidx.compose.runtime.saveable.rememberSaveable(currentAddress) {
                        mutableStateOf(false)
                    }
                    Spacer(Modifier.height(8.dp))
                    if (!editRoad) {
                        Text(
                            "주소 글자 직접 고치기",
                            color = TossBlue, fontWeight = FontWeight.Bold, fontSize = 12.5.sp,
                            modifier = Modifier
                                .clip(AppShape.sm)
                                .clickable { editRoad = true }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    } else {
                        androidx.compose.material3.OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            label = { Text("도로명 주소") },
                            leadingIcon = { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Place, null, tint = TossTextSecondary, modifier = Modifier.size(17.dp)) },
                            singleLine = false,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                // 자동 추출 후보 — 사장님 한 탭에 input 박힘.
                if (extractedSuggestion != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "메시지에서 인식된 주소",
                        style = MaterialTheme.typography.labelSmall,
                        color = TossTextTertiary
                    )
                    Spacer(Modifier.height(6.dp))
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                            .background(TossBlueSoft)
                            .clickable { text = extractedSuggestion }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text(
                            "$extractedSuggestion",
                            style = MaterialTheme.typography.bodySmall,
                            color = TossBlue,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    if (onCopyExisting != null) {
                        androidx.compose.material3.TextButton(onClick = onCopyExisting) {
                            Text("복사", color = TossTextSecondary)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    androidx.compose.material3.TextButton(onClick = onDismiss) {
                        Text("취소", color = TossTextSecondary)
                    }
                    if (!currentAddress.isNullOrBlank()) {
                        androidx.compose.material3.TextButton(onClick = { onSave(null) }) {
                            Text("삭제", color = TossTextSecondary)
                        }
                    }
                    val combined = (text.trim() + if (detail.isBlank()) "" else " " + detail.trim()).trim()
                    androidx.compose.material3.TextButton(
                        onClick = { onSave(combined.takeIf { it.isNotEmpty() }) },
                        enabled = combined != currentAddress.orEmpty().trim()
                    ) {
                        Text("저장", color = TossBlue, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/**
 * 건(件) 탭 — 한 고객의 시공을 크롬 새 탭처럼 옆으로. (2026-09-17 사장님 "B안이 괜찮다")
 *
 * 왜 탭인가: 한 번에 **한 건만** 보여주면 화면이 짧다.
 *   (같이 본 A안 = 카드를 위아래로 쌓는 목록형. 사장님이 B안을 골랐다.)
 *
 * 차수는 **오래된 것이 1차**다. 지난 건들 다음이 지금 건.
 * 건이 하나뿐이어도 그린다 — 「＋ 새 시공」이 이 줄에만 있어서, 안 그리면 2번째 시공을 잡을 길이 없다. (2026-09-17)
 */
/**
 * 이 건은 **마무리됐나** — 잔금을 받았으면 마무리. (2026-09-18 사장님 확정 · 프로토 `closed()`)
 *   잔금이 애초에 없는 건은 완료 표시만으로 마무리로 본다.
 */
internal fun jobClosed(j: com.detailline.callfollowcrm.data.local.entity.JobEntity): Boolean =
    j.balancePaidAt != null || (j.workCompletedAt != null && (j.balanceAmount ?: 0L) <= 0L)

/** '이 사람은 [고객 아님][고객]' 알약 하나. 고른 쪽만 파랗게. (2026-09-17) */
@Composable
internal fun CustomerKindPill(text: String, on: Boolean, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Box(
        Modifier.clip(RoundedCornerShape(999.dp))
            .background(if (on) TossBlue else TossGrayBg)
            .clickable { onClick() }
            .padding(horizontal = 13.dp, vertical = 6.dp)
    ) {
        Text(
            text, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
            color = if (on) Color.White else TossTextSecondary
        )
    }
}

@Composable
internal fun JobTab(
    nth: String,
    sub: String,
    on: Boolean,
    dashed: Boolean = false,
    /** '지난 건' 묶음 — 진행 중인 건과 구분되게 회색으로. (2026-09-18 프로토 `.chip.past`) */
    muted: Boolean = false,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 12.dp, bottomEnd = 12.dp)
    Column(
        Modifier
            .clip(shape)
            .background(
                when {
                    muted && on -> Color(0xFF4E5968)
                    on -> Color.White
                    else -> TossGrayBg
                }
            )
            .then(
                if (on && !muted) Modifier.border(1.5.dp, TossBlue, shape)
                else if (dashed) Modifier.border(1.dp, TossBlue.copy(alpha = 0.45f), shape)
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 9.dp)
    ) {
        Text(
            nth,
            fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold,
            color = when {
                muted && on -> Color.White
                dashed -> TossBlue
                on -> TossTextPrimary
                else -> TossTextTertiary
            }
        )
        Spacer(Modifier.height(2.dp))
        Text(
            sub, fontSize = 10.5.sp, fontWeight = FontWeight.Bold,
            color = if (muted && on) Color(0xFFD6DBE1) else TossTextTertiary
        )
    }
}
