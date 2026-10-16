package com.detailline.callfollowcrm.presentation.screen.spam

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Abc
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.presentation.component.SheetTextField
import com.detailline.callfollowcrm.presentation.theme.AppShape
import com.detailline.callfollowcrm.presentation.theme.AppSpace
import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.theme.AppType
import com.detailline.callfollowcrm.presentation.util.keyboardPadding

/**
 * 「스팸·광고 거르기」 — 흩어져 있던 스팸 설정 **세 군데를 한 화면으로.** (2026-10-16 사장님 "스팸 항목이 왜 나뉘어져 있니")
 *   프로토: docs/PROTOTYPES.md 「스팸·광고 거르기」 `artifact/Xy86m2QdTsetr3pLvd8KKi` — 위에서 아래로 그대로.
 *
 *   ① AI 자동 거르기 — 스위치 하나(AppPreferences.aiSpamFilterEnabled). 꺼지면 ServerClassifyGate 가 서버에 안 묻는다.
 *   ①-b 광고·스팸은 알림 안 울리기 — 스위치(AppPreferences.adSilentPush, 기본 OFF). 판정은 DefiniteAd 한 곳(2026-10-16).
 *       프로토(Xy86m2Qd…)엔 없던 카드 — 사장님 A안 결정으로 문구를 받아 넣음. 인증·은행·택배·손님은 그대로 울린다.
 *   ② 광고·스팸 번호 앞자리 — 더보기 「자동으로 챙기기」 에 있던 칩·직접입력을 **이리로 옮겼다**(복제 아님, §12).
 *      저장은 AppPreferences.spamPrefixes 한 칸 그대로 — SmsReceiver·HomeViewModel 등 쓰는 쪽은 안 바뀐다.
 *   ③ 이 단어가 있으면 — 본문에 이 낱말이 든 **모르는 번호** 문자는 광고로 봐 문자함으로(2026-10-16).
 *      저장은 AppPreferences.spamKeywords, 셈은 SpamKeywordMatch, 거는 자리는 ThreadBucketRepository.classifyLocal 한 곳.
 *      칩+입력+추가는 ②와 같은 조각 [HubChipEditor].
 *   ④ 차단한 번호 — 상담함에서 밀어서 「스팸」 찍은 번호. 줄은 [SpamPhoneRows] 한 곳(지인 목록과 같이 씀).
 *
 *   「지인 번호」 는 스팸이 아니라 더보기에 그대로 둔다 — 여기 안 넣는다.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SpamHubScreen(
    container: AppContainer,
    onBack: () -> Unit
) {
    val prefs = container.preferences
    val context = LocalContext.current

    var aiFilterOn by remember { mutableStateOf(prefs.aiSpamFilterEnabled) }
    var adSilentOn by remember { mutableStateOf(prefs.adSilentPush) }
    var spamPrefixes by remember { mutableStateOf(prefs.spamPrefixes) }
    var newSpamPrefix by remember { mutableStateOf("") }
    var spamKeywords by remember { mutableStateOf(prefs.spamKeywords) }
    var newSpamKeyword by remember { mutableStateOf("") }

    val all by container.spamPhoneRepository.all.collectAsState(initial = emptyList())
    val blocked = all.filter { it.kind == "spam" }

    Scaffold(
        containerColor = AppTheme.colors.bg,
        topBar = {
            TopAppBar(
                title = { Text("스팸·광고 거르기", style = AppType.title, fontWeight = FontWeight.ExtraBold, color = AppTheme.colors.text) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", tint = AppTheme.colors.text)
                    }
                },
                // 앱의 다른 화면과 같은 회색 — 흰 띠면 바탕과 사이에 이음매가 생긴다. (2026-09-22 사장님)
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppTheme.colors.bg)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .keyboardPadding()
                .padding(horizontal = AppSpace.s16)
        ) {
            Text(
                "광고·스팸을 거르는 방법을 한 곳에 모았어요.",
                style = AppType.body, color = AppTheme.colors.textSub,
                modifier = Modifier.padding(start = AppSpace.s4, bottom = AppSpace.s12)
            )

            // ── ① AI 자동 거르기 ──
            HubCard {
                Row(verticalAlignment = Alignment.Top) {
                    HubTile(Icons.Filled.AutoAwesome, AppTheme.colors.categoryBg, AppTheme.colors.category)
                    Spacer(Modifier.width(AppSpace.s12))
                    Column(Modifier.weight(1f)) {
                        Text("AI 자동 거르기", style = AppType.headline, fontWeight = FontWeight.ExtraBold, color = AppTheme.colors.text)
                        Spacer(Modifier.height(AppSpace.s4))
                        Text(
                            "애매한 문자를 AI가 ‘광고·손님’으로 가려, 광고는 문자함으로 내려요. 조금이라도 손님 같으면 상담함에 둬요.",
                            style = AppType.label, color = AppTheme.colors.textSub
                        )
                    }
                    Spacer(Modifier.width(AppSpace.s8))
                    Switch(checked = aiFilterOn, onCheckedChange = {
                        aiFilterOn = it
                        prefs.aiSpamFilterEnabled = it
                    })
                }
            }

            // ── ①-b 광고·스팸은 알림 안 울리기 (2026-10-16 사장님 A안 — 기본 OFF, 옵트인) ──
            //   판정은 DefiniteAd 한 곳(앞자리·뻔한 광고 본문·적어둔 단어 중 하나 + 저장 고객 아님).
            //   인증번호·입금·택배는 DefiniteAd 가 절대 안 잡는다. 저장·분류·목록은 그대로 — 알림만 끈다.
            HubCard {
                Row(verticalAlignment = Alignment.Top) {
                    HubTile(Icons.Filled.NotificationsOff, AppTheme.colors.surfaceMuted, AppTheme.colors.textSub)
                    Spacer(Modifier.width(AppSpace.s12))
                    Column(Modifier.weight(1f)) {
                        Text("광고·스팸은 알림 안 울리기", style = AppType.headline, fontWeight = FontWeight.ExtraBold, color = AppTheme.colors.text)
                        Spacer(Modifier.height(AppSpace.s4))
                        Text(
                            "켜면 확실한 광고·스팸은 알림이 안 와요. 인증문자·은행·택배·손님 문자는 그대로 울려요.",
                            style = AppType.label, color = AppTheme.colors.textSub
                        )
                    }
                    Spacer(Modifier.width(AppSpace.s8))
                    Switch(checked = adSilentOn, onCheckedChange = {
                        adSilentOn = it
                        prefs.adSilentPush = it
                    })
                }
            }

            HubSectionLabel("번호로 거르기")

            // ── ② 광고·스팸 번호 앞자리 ──
            HubCard {
                Row(verticalAlignment = Alignment.Top) {
                    HubTile(Icons.Filled.Block, AppTheme.colors.unpaidBg, AppTheme.colors.unpaid)
                    Spacer(Modifier.width(AppSpace.s12))
                    Column(Modifier.weight(1f)) {
                        Text("광고·스팸 번호 앞자리", style = AppType.headline, fontWeight = FontWeight.ExtraBold, color = AppTheme.colors.text)
                        Spacer(Modifier.height(AppSpace.s4))
                        Text(
                            "이 앞자리로 시작하는 번호는 광고로 봐요 (자동답장·AI 추천 안 하고 신규 목록에서도 빼요).",
                            style = AppType.label, color = AppTheme.colors.textSub
                        )
                    }
                }
                // 칩(✕ 삭제)+직접입력+추가 — 아래 「이 단어가 있으면」 과 **같은 조각**(HubChipEditor). 저장은 그 자리에서(commit=즉시).
                HubChipEditor(
                    items = spamPrefixes,
                    chipBg = AppTheme.colors.unpaidBg,
                    chipText = AppTheme.colors.unpaidText,
                    emptyText = "아직 등록한 앞자리가 없어요",
                    input = newSpamPrefix,
                    onInputChange = { newSpamPrefix = it.filter { c -> c.isDigit() }.take(6) },
                    placeholder = "예: 070",
                    keyboardType = KeyboardType.Number,
                    onRemove = { p -> spamPrefixes = spamPrefixes - p; prefs.spamPrefixes = spamPrefixes },
                    onAdd = { p ->
                        // 저장 됐는지 사장님이 헷갈리던 통점(2026-06-16): 추가 결과를 토스트로 분명히 알림 + 중복 안내.
                        if (p in spamPrefixes) {
                            Toast.makeText(context, "‘$p’ 는 이미 등록돼 있어요", Toast.LENGTH_SHORT).show()
                        } else {
                            spamPrefixes = spamPrefixes + p
                            prefs.spamPrefixes = spamPrefixes   // .commit() = 즉시 저장
                            Toast.makeText(context, "‘$p’ 저장됐어요 — 앞자리에 추가됐어요", Toast.LENGTH_SHORT).show()
                        }
                        newSpamPrefix = ""
                    }
                )
            }

            HubSectionLabel("말로 거르기")

            // ── ③ 이 단어가 있으면 (2026-10-16 사장님) ──
            //   본문에 이 낱말이 든 **모르는 번호** 문자는 광고로 봐 문자함으로. 저장 고객·답장한 번호는 안 가린다.
            //   셈은 SpamKeywordMatch 한 곳, 거는 자리는 ThreadBucketRepository.classifyLocal 한 곳.
            HubCard {
                Row(verticalAlignment = Alignment.Top) {
                    HubTile(Icons.Filled.Abc, AppTheme.colors.cautionBg, AppTheme.colors.cautionText)
                    Spacer(Modifier.width(AppSpace.s12))
                    Column(Modifier.weight(1f)) {
                        Text("이 단어가 있으면", style = AppType.headline, fontWeight = FontWeight.ExtraBold, color = AppTheme.colors.text)
                        Spacer(Modifier.height(AppSpace.s4))
                        Text(
                            "이 단어가 들어간 문자는 광고로 봐서 문자함으로 내려요.",
                            style = AppType.label, color = AppTheme.colors.textSub
                        )
                    }
                }
                Spacer(Modifier.height(AppSpace.s12))
                // 프로토 `.warn` — 주황 주의 박스.
                Box(
                    Modifier.fillMaxWidth().clip(AppShape.md).background(AppTheme.colors.cautionBg)
                        .padding(horizontal = AppSpace.s12, vertical = AppSpace.s8)
                ) {
                    Text(
                        "⚠️ 흔한 한 단어(할인·대출)는 진짜 손님도 가려질 수 있어요. ‘손실 지급’처럼 딱 떨어지는 광고 문구로 넣으세요. (지우는 게 아니라 문자함으로 옮기기만 해요)",
                        style = AppType.caption, color = AppTheme.colors.cautionText
                    )
                }
                HubChipEditor(
                    items = spamKeywords,
                    chipBg = AppTheme.colors.cautionBg,
                    chipText = AppTheme.colors.cautionText,
                    emptyText = "아직 등록한 단어가 없어요",
                    input = newSpamKeyword,
                    onInputChange = { newSpamKeyword = it.take(40) },
                    placeholder = "예: 실시간 손실",
                    keyboardType = KeyboardType.Text,
                    onRemove = { k -> spamKeywords = spamKeywords - k; prefs.spamKeywords = spamKeywords },
                    onAdd = { raw ->
                        // 빈 글자·공백은 SpamKeywordMatch.normalize 가 거른다(저장 쪽과 같은 셈). 중복은 토스트로.
                        val k = com.detailline.callfollowcrm.domain.inbox.SpamKeywordMatch.normalize(raw)
                        when {
                            k == null -> Unit
                            k in spamKeywords -> Toast.makeText(context, "‘$k’ 는 이미 등록돼 있어요", Toast.LENGTH_SHORT).show()
                            else -> {
                                spamKeywords = spamKeywords + k
                                prefs.spamKeywords = spamKeywords   // .commit() = 즉시 저장
                                Toast.makeText(context, "‘$k’ 저장됐어요 — 이 단어가 든 모르는 번호 문자는 문자함으로 가요", Toast.LENGTH_SHORT).show()
                            }
                        }
                        newSpamKeyword = ""
                    }
                )
            }

            HubSectionLabel("차단한 번호")

            // ── ④ 차단한 번호 ──
            HubCard {
                Text(
                    "상담함에서 밀어서 「스팸」 찍은 번호예요. 풀면 다시 상담함으로 돌아와요.",
                    style = AppType.label, color = AppTheme.colors.textSub,
                    modifier = Modifier.padding(bottom = AppSpace.s4)
                )
                if (blocked.isEmpty()) {
                    Text(
                        "스팸으로 등록한 번호가 없어요",
                        style = AppType.label, color = AppTheme.colors.textHint,
                        modifier = Modifier.padding(top = AppSpace.s8, bottom = AppSpace.s4)
                    )
                } else {
                    SpamPhoneRows(
                        container = container,
                        entries = blocked,
                        actionLabel = "풀기",
                        undoToast = "스팸을 풀었어요 — 다시 보여요",
                        numberFirst = true,
                        outlinedAction = true
                    )
                }
            }

            Text(
                "* 「지인 번호」는 스팸이 아니라 그대로 더보기에 둬요 (문자함 [지인]).",
                style = AppType.caption, color = AppTheme.colors.textHint,
                modifier = Modifier.padding(start = AppSpace.s4, top = AppSpace.s4, bottom = AppSpace.s24)
            )
        }
    }
}

/**
 * 칩 목록(누르면 ✕ 삭제) + 직접입력 + 「추가」 — 프로토 `.chips` + `.inputrow`.
 *   앞자리(②)와 「이 단어가 있으면」(③)이 **같은 조각**을 쓴다(복제 X, §12). 색만 바꿔 받는다.
 *   「추가」는 입력이 비면 눌리지 않는다(빈 값 막기). 중복·저장 안내는 부르는 쪽 onAdd 가 토스트로.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HubChipEditor(
    items: Set<String>,
    chipBg: androidx.compose.ui.graphics.Color,
    chipText: androidx.compose.ui.graphics.Color,
    emptyText: String,
    input: String,
    onInputChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    onRemove: (String) -> Unit,
    onAdd: (String) -> Unit
) {
    Spacer(Modifier.height(AppSpace.s12))
    if (items.isEmpty()) {
        Box(
            Modifier.fillMaxWidth().clip(AppShape.md).background(AppTheme.colors.surfaceMuted)
                .padding(vertical = AppSpace.s12),
            contentAlignment = Alignment.Center
        ) { Text(emptyText, style = AppType.label, color = AppTheme.colors.textSub) }
    } else {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(AppSpace.s8),
            verticalArrangement = Arrangement.spacedBy(AppSpace.s8)
        ) {
            items.sorted().forEach { p ->
                Row(
                    Modifier.clip(AppShape.pill).background(chipBg)
                        .clickable { onRemove(p) }
                        .padding(horizontal = AppSpace.s12, vertical = AppSpace.s8),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(p, style = AppType.label, fontWeight = FontWeight.Bold, color = chipText)
                    Spacer(Modifier.width(AppSpace.s8))
                    Text("✕", style = AppType.caption, fontWeight = FontWeight.Bold, color = chipText.copy(alpha = 0.7f))
                }
            }
        }
    }
    Spacer(Modifier.height(AppSpace.s12))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpace.s8)) {
        Box(Modifier.weight(1f)) {
            SheetTextField(
                value = input,
                onValueChange = onInputChange,
                placeholder = placeholder,
                keyboardType = keyboardType
            )
        }
        val canAdd = input.isNotBlank()
        Box(
            Modifier.clip(AppShape.md)
                .background(if (canAdd) AppTheme.colors.primary else AppTheme.colors.surfaceMuted)
                .clickable(enabled = canAdd) { onAdd(input) }
                .padding(horizontal = AppSpace.s20, vertical = AppSpace.s12),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "추가", style = AppType.label, fontWeight = FontWeight.Bold,
                color = if (canAdd) AppTheme.colors.textOnPrimary else AppTheme.colors.textSub
            )
        }
    }
}

/** 흰 카드 한 장 — 프로토 `.card`(흰 바탕·둥근 모서리·안쪽 여백). */
@Composable
private fun HubCard(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(AppShape.lg).background(AppTheme.colors.surface)
            .padding(AppSpace.s16)
    ) { content() }
    Spacer(Modifier.height(AppSpace.s12))
}

/** 카드 제목 왼쪽의 아이콘 상자 — 프로토 `.tile`. */
@Composable
private fun HubTile(icon: ImageVector, bg: androidx.compose.ui.graphics.Color, tint: androidx.compose.ui.graphics.Color) {
    Box(
        Modifier.size(38.dp).clip(AppShape.md).background(bg),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(19.dp)) }
}

/** 카드 묶음 머리 — 프로토 `.seclbl`. */
@Composable
private fun HubSectionLabel(text: String) {
    Text(
        text, style = AppType.label, fontWeight = FontWeight.Bold, color = AppTheme.colors.textSub,
        modifier = Modifier.padding(start = AppSpace.s4, top = AppSpace.s4, bottom = AppSpace.s8)
    )
}
