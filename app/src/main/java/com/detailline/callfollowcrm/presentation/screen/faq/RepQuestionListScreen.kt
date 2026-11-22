package com.detailline.callfollowcrm.presentation.screen.faq

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.detailline.callfollowcrm.data.local.entity.MessageTemplateEntity
import com.detailline.callfollowcrm.domain.faq.RepQuestionRules
import com.detailline.callfollowcrm.presentation.component.MascotEmptyState
import com.detailline.callfollowcrm.presentation.component.TossPrimaryButton
import com.detailline.callfollowcrm.presentation.theme.AppShape
import com.detailline.callfollowcrm.presentation.theme.AppSpace
import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.theme.AppType
import com.detailline.callfollowcrm.presentation.util.keyboardOrNavPadding

/** 프로토 ② 안내문 — 글자 그대로. 빈 화면 부제도 앞 문장을 그대로 쓴다. */
private const val HINT_REDUCE = "자주 묻는 질문 3개만 등록해도 반복 문의가 줄어요."

/** 손님 FAQ 페이지 설정과 공개 문구를 관리한다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepQuestionListScreen(
    viewModel: RepQuestionListViewModel,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onNew: () -> Unit,
    onEditBizIntro: () -> Unit = {}
) {
    val photoByTemplate by viewModel.photoByTemplate.collectAsState()
    val questions by viewModel.questions.collectAsState()
    val published by viewModel.published.collectAsState()
    val faqUrl by viewModel.faqUrl.collectAsState()
    val publishing by viewModel.publishing.collectAsState()
    val context = LocalContext.current
    // 🗣 순서·삭제가 실패하면 사장님께 말한다.
    val toast by viewModel.toast.collectAsState()
    LaunchedEffect(toast) {
        toast?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.consumeToast()
        }
    }
    var deleteTarget by remember { mutableStateOf<MessageTemplateEntity?>(null) }

    Scaffold(
        containerColor = AppTheme.colors.surface,
        topBar = {
            TopAppBar(
                title = { Text("손님 FAQ 관리", style = AppType.title, color = AppTheme.colors.text) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "뒤로", tint = AppTheme.colors.text)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppTheme.colors.surface)
            )
        },
        bottomBar = {
            // 프로토 .bigbtn — 바닥 고정. 네비바에 안 가리게 공용 여백.
            Column(
                Modifier.fillMaxWidth().background(AppTheme.colors.surface).keyboardOrNavPadding()
                    .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = AppSpace.s16)
            ) {
                TossPrimaryButton(text = "저장한 말 추가", onClick = onNew)
            }
        }
    ) { inner ->
        val list = questions
        Box(Modifier.padding(inner).fillMaxSize()) {
            if (list == null) {
                // 읽는 중 — 빈 화면을 잠깐 잘못 보여주지 않는다(로컬 DB 라 곧 온다).
                Spacer(Modifier.height(1.dp))
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)
                ) {
                    FaqPublishCard(
                        published = published,
                        faqUrl = faqUrl,
                        publishing = publishing,
                        onToggle = viewModel::setPublished,
                        onRepublish = viewModel::republish,
                        onPreview = { openUrl(context, faqUrl) },
                        onCopy = { copyUrl(context, faqUrl) },
                        onShare = { shareUrl(context, faqUrl) }
                    )
                    Spacer(Modifier.height(AppSpace.s12))
                    BizIntroEntryRow(onClick = onEditBizIntro)
                    Spacer(Modifier.height(AppSpace.s12))
                    // ✨ AI로 대표질문 채우기 — 문자 읽고 자주 묻는 질문·답 초안.
                    AiFillEntryCard(onClick = viewModel::openAiFill)
                    Spacer(Modifier.height(AppSpace.s16))
                    if (list.isEmpty()) {
                        MascotEmptyState(speech = "공개한 문구가 없어요", sub = "저장한 말에서 손님 FAQ에 공개를 켜주세요")
                    } else {
                        Text(HINT_REDUCE, style = AppType.body, color = AppTheme.colors.textHint)
                        Spacer(Modifier.height(AppSpace.s12))
                        PublicQuestions(
                            list = list,
                            onEdit = onEdit,
                            onLongPress = { deleteTarget = it },
                            photoByTemplate = photoByTemplate
                        )
                    }
                }
            }
        }
    }

    // ✨ AI로 대표질문 채우기 시트 — 열려 있을 때만 그려진다(내부 상태).
    AiFillSheet(viewModel)

    // 지우기 확인 — 앱 기조: 지우는 건 한 번 묻는다.
    deleteTarget?.let { t ->
        AlertDialog(
            containerColor = Color.White,
            tonalElevation = 0.dp,
            onDismissRequest = { deleteTarget = null },
            title = { Text("이 저장한 말을 지울까요?", fontWeight = FontWeight.Bold, color = AppTheme.colors.text) },
            text = { Text("「${t.title}」 문구가 저장한 말과 FAQ 목록에서 삭제돼요.", color = AppTheme.colors.textSub) },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(t); deleteTarget = null }) {
                    Text("삭제", color = AppTheme.colors.unpaid, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("취소", color = AppTheme.colors.textSub) }
            }
        )
    }
}

/** 공개 문구 목록. 편집은 저장한 말 편집기를 사용한다. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PublicQuestions(
    list: List<MessageTemplateEntity>,
    onEdit: (Long) -> Unit,
    onLongPress: (MessageTemplateEntity) -> Unit,
    photoByTemplate: Map<Long, String>
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpace.s12)) {
        list.forEach { row ->
            key(row.id) {
                Column(
                    Modifier.fillMaxWidth().clip(AppShape.lg)
                        .background(AppTheme.colors.surface)
                        .border(1.dp, AppTheme.colors.line, AppShape.lg)
                        .combinedClickable(onClick = { onEdit(row.id) }, onLongClick = { onLongPress(row) })
                        .padding(AppSpace.s16)
                ) {
                    if (row.category.isNotBlank()) {
                        Text(row.category, style = AppType.caption, color = AppTheme.colors.primaryText)
                    }
                    Text(row.title, style = AppType.headline, color = AppTheme.colors.text,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(AppSpace.s8))
                    Text(RepQuestionRules.previewLine(row.body, photoByTemplate[row.id] != null),
                        style = AppType.caption, color = AppTheme.colors.textHint,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** 하나도 없을 때 — 관리 화면과 채팅 대표질문 시트가 **같은 빈 화면**을 쓴다(공용 막내 틀). */
@Composable
internal fun RepQuestionEmptyState() {
    MascotEmptyState(speech = "아직 등록한 대표질문이 없어요", sub = HINT_REDUCE)
}

/** 손님 공개 FAQ 페이지 — 공개 on/off · 고정 링크(복사·공유·미리보기) · 최신 내용 올리기. 프로토 7U7dnG1GFEoD2RMmd7gRmk ②. */
@Composable
private fun FaqPublishCard(
    published: Boolean,
    faqUrl: String,
    publishing: Boolean,
    onToggle: (Boolean) -> Unit,
    onRepublish: () -> Unit,
    onPreview: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().clip(AppShape.lg).background(AppTheme.colors.primaryBg).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (published) "대표질문 공개 중" else "대표질문 숨김",
                    style = AppType.headline, fontWeight = FontWeight.ExtraBold, color = AppTheme.colors.primaryText
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (published) "손님이 아래 링크로 질문을 볼 수 있어요." else "지금은 손님에게 보이지 않아요.",
                    style = AppType.caption, color = AppTheme.colors.textSub
                )
            }
            Switch(
                checked = published,
                onCheckedChange = { onToggle(it) },
                enabled = !publishing,
                // 연한 파란 카드 위에서도 또렷하게 — 꺼짐은 회색 트랙+흰 손잡이, 켜짐은 파란 트랙+흰 손잡이.
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = AppTheme.colors.primary,
                    checkedBorderColor = AppTheme.colors.primary,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = AppTheme.colors.neutral,
                    uncheckedBorderColor = AppTheme.colors.neutral
                )
            )
        }
        if (published && faqUrl.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().clip(AppShape.md).background(AppTheme.colors.surface)
                    .border(1.dp, AppTheme.colors.line, AppShape.md).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    faqUrl.removePrefix("https://").removePrefix("http://"),
                    style = AppType.caption, color = AppTheme.colors.textSub,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                )
                Text(
                    "복사", style = AppType.label, color = AppTheme.colors.primaryText,
                    modifier = Modifier.clip(AppShape.sm).clickable { onCopy() }.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Spacer(Modifier.height(AppSpace.s8))
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpace.s8)) {
                PillButton("미리보기", Modifier.weight(1f), onPreview)
                PillButton("공유", Modifier.weight(1f), onShare)
            }
            Spacer(Modifier.height(AppSpace.s8))
            PillButton(if (publishing) "올리는 중…" else "최신 내용으로 올리기", Modifier.fillMaxWidth(), onRepublish)
        } else if (published) {
            Spacer(Modifier.height(10.dp))
            Text("링크 발급 중… 서버 연결 후 여기 표시돼요.", style = AppType.caption, color = AppTheme.colors.textHint)
        }
    }
}

@Composable
private fun PillButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(AppShape.md).background(AppTheme.colors.surface)
            .border(1.dp, AppTheme.colors.line, AppShape.md).clickable { onClick() }.padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = AppType.label, color = AppTheme.colors.primaryText)
    }
}

/** 관리 화면 → 업체 소개 편집 입구. */
@Composable
private fun BizIntroEntryRow(onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(AppShape.lg).background(AppTheme.colors.surface)
            .border(1.dp, AppTheme.colors.line, AppShape.lg).clickable { onClick() }
            .padding(horizontal = 15.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("업체 소개 편집", style = AppType.headline, fontWeight = FontWeight.ExtraBold, color = AppTheme.colors.text)
            Spacer(Modifier.height(2.dp))
            Text("손님 페이지 맨 위에 보일 소개·대표 사진·경력", style = AppType.caption, color = AppTheme.colors.textHint)
        }
        Text("›", style = AppType.title, color = AppTheme.colors.neutral)
    }
}

private fun openUrl(context: Context, url: String) {
    if (url.isBlank()) return
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { Toast.makeText(context, "브라우저를 열지 못했어요", Toast.LENGTH_SHORT).show() }
}

private fun copyUrl(context: Context, url: String) {
    if (url.isBlank()) return
    runCatching {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("대표질문 링크", url))
        Toast.makeText(context, "링크를 복사했어요", Toast.LENGTH_SHORT).show()
    }.onFailure { Toast.makeText(context, "복사하지 못했어요", Toast.LENGTH_SHORT).show() }
}

private fun shareUrl(context: Context, url: String) {
    if (url.isBlank()) return
    runCatching {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
        context.startActivity(Intent.createChooser(send, "대표질문 링크 공유").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { Toast.makeText(context, "공유하지 못했어요", Toast.LENGTH_SHORT).show() }
}
