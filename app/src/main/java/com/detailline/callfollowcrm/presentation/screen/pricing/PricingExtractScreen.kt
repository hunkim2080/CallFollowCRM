@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.detailline.callfollowcrm.presentation.screen.pricing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.data.local.entity.PricingItemEntity
import com.detailline.callfollowcrm.data.repository.PricingItemRepository
import com.detailline.callfollowcrm.presentation.component.ThousandsCommaEditTransformation
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossBlueSoft
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary

/**
 * 문자에서 가격 불러오기 — 2단계 확인 화면. ViewModel = PricingExtractViewModel.
 *   1단계(ITEMS): "이런 항목 파시는 거 맞아요?" 이름 수정/삭제.
 *   2단계(PRICES): "제가 분석한 가격 봐주세요" 가격+기준 수정.
 * 프로토엔 없는 신규 흐름(사장님 2026-07-02 요청). 저장 후 onDone.
 */
@Composable
fun PricingExtractScreen(
    viewModel: PricingExtractViewModel,
    onBack: () -> Unit,
    onDone: () -> Unit,
    preferences: com.detailline.callfollowcrm.data.preferences.AppPreferences
) {
    val ui by viewModel.ui.collectAsState()
    val phase = ui.phase

    val title = when (phase) {
        PricingExtractViewModel.Phase.ITEMS -> "판매 항목 확인"
        PricingExtractViewModel.Phase.PRICES -> "가격 확인"
        else -> "문자에서 가격 불러오기"
    }

    Scaffold(
        containerColor = TossGrayBg,
        // 2026-08-24 사장님: 하단 입력칸(줄눈 평당 등) 고치려 터치하면 키보드가 가리던 버그 —
        //   nav+ime union 으로 콘텐츠(리스트+담기버튼)를 키보드 위로 밀어 포커스 칸이 보이게. (ChatScreen 패턴)
        contentWindowInsets = WindowInsets.ime.union(WindowInsets.navigationBars),
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (phase == PricingExtractViewModel.Phase.PRICES) viewModel.backToItems() else onBack()
                    }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (phase) {
                PricingExtractViewModel.Phase.LOADING -> CenterStatus(loading = true, message = ui.message)
                PricingExtractViewModel.Phase.CONSENT -> ConsentBody(onAgree = viewModel::agreeAndStart, onBack = onBack)
                PricingExtractViewModel.Phase.ITEMS -> ItemsBody(ui, viewModel)
                PricingExtractViewModel.Phase.PRICES -> PricesBody(ui, viewModel)
                PricingExtractViewModel.Phase.EMPTY -> CenterStatus(
                    loading = false, message = ui.message, actionLabel = "직접 입력하기", onAction = onBack
                )
                // 서버 분석 실패(EMPTY=뽑을 게 없음과 구분) → 막힌 자리에서 바로 진단. (2026-07-29 사장님)
                PricingExtractViewModel.Phase.ERROR -> CenterStatus(
                    loading = false, message = ui.message, actionLabel = "직접 입력하기", onAction = onBack,
                    diag = {
                        com.detailline.callfollowcrm.presentation.component.InlineDiagPrompt(
                            prefs = preferences,
                            tag = "가격표-자동생성 실패",
                            prompt = "계속 안 되나요?",
                            buildExtra = { "가격표 자동생성 실패(분석 서버 응답 없음/오류) · 후보 문자 ${ui.candidateCount}개" }
                        )
                    }
                )
                PricingExtractViewModel.Phase.DONE -> CenterStatus(
                    loading = false,
                    message = "가격표에 ${ui.savedCount}개 항목을 담았어요!\n가격표 관리에서 언제든 고칠 수 있어요.",
                    actionLabel = "완료", onAction = onDone
                )
            }
        }
    }
}

@Composable
private fun CenterStatus(
    loading: Boolean,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    diag: (@Composable () -> Unit)? = null
) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (loading) {
            CircularProgressIndicator(color = TossBlue)
            Spacer(Modifier.height(20.dp))
        }
        Text(message, color = TossTextSecondary, fontSize = 15.sp, textAlign = TextAlign.Center, lineHeight = 22.sp)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = TossBlue),
                shape = RoundedCornerShape(12.dp)
            ) { Text(actionLabel, color = Color.White, fontWeight = FontWeight.Bold) }
        }
        if (diag != null) {
            Spacer(Modifier.height(14.dp))
            diag()
        }
    }
}

@Composable
private fun ConsentBody(onAgree: () -> Unit, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("문자에서 가격을 불러올까요?", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
        Spacer(Modifier.height(16.dp))
        Text(
            "그동안 고객에게 보낸 견적 문자를 읽어서, 사장님이 어떤 항목을 얼마에 파는지 자동으로 가격표를 만들어 드려요.\n\n" +
                "· 사장님이 '보낸' 문자만 봐요 (받은 문자·연락처는 안 봐요)\n" +
                "· 가격 항목만 뽑고, 고객 정보는 저장하지 않아요\n" +
                "· 오래된 폰도 느리지 않게 최근 문자만 조용히 읽어요",
            fontSize = 14.sp, color = TossTextSecondary, lineHeight = 21.sp
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onAgree,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TossBlue),
            shape = RoundedCornerShape(14.dp)
        ) { Text("동의하고 불러오기", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp)
        ) { Text("나중에 할게요", color = TossTextSecondary) }
    }
}

@Composable
private fun ItemsBody(ui: PricingExtractViewModel.UiState, vm: PricingExtractViewModel) {
    Column(Modifier.fillMaxSize()) {
        HeaderCard(
            emoji = "🔎",
            title = "AI가 판단한 판매 항목이에요",
            subtitle = "이름이 정확하지 않을 수 있으니 수정이 필요하면 수정해주세요.\n판매 항목이 아니라고 생각되면 X를 눌러주세요!"
        )
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(ui.rows, key = { it.key }) { row ->
                Row(
                    Modifier.fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CategoryTag(row.category)
                    EditField(
                        value = row.title,
                        onChange = { vm.editTitle(row.key, it) },
                        hint = "항목 이름",
                        modifier = Modifier.weight(1f),
                        bold = true
                    )
                    IconButton(onClick = { vm.removeRow(row.key) }) {
                        Icon(Icons.Filled.Close, contentDescription = "빼기", tint = TossTextTertiary)
                    }
                }
            }
        }
        BottomBar {
            Button(
                onClick = vm::goToPrices,
                enabled = ui.rows.any { it.title.isNotBlank() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TossBlue),
                shape = RoundedCornerShape(14.dp)
            ) { Text("네, 가격도 볼게요", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        }
    }
}

@Composable
private fun PricesBody(ui: PricingExtractViewModel.UiState, vm: PricingExtractViewModel) {
    Column(Modifier.fillMaxSize()) {
        HeaderCard(
            emoji = "💰",
            title = "제가 분석한 가격이에요",
            subtitle = "맞는지 봐주세요. 가격이나 기준을 바로 고칠 수 있어요."
        )
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(ui.rows, key = { it.key }) { row ->
                Column(
                    Modifier.fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CategoryTag(row.category)
                        Text(
                            row.title,
                            fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 커서를 중간에 놓고 고칠 수 있게 로컬 TextFieldValue 로 관리(끝으로 튀는 문제 fix).
                        // 박스 전체가 터치돼 키패드가 열리도록 fillMaxWidth. (2026-07-02 사장님)
                        var priceField by remember(row.key) {
                            mutableStateOf(TextFieldValue(if (row.priceWon > 0) row.priceWon.toString() else ""))
                        }
                        Box(
                            Modifier.weight(1f)
                                .background(TossGrayBg, RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            BasicTextField(
                                value = priceField,
                                onValueChange = { nv ->
                                    val d = nv.text.filter { it.isDigit() }
                                    priceField = if (d == nv.text && d.length <= 9) nv
                                    else TextFieldValue(d.take(9), TextRange(d.take(9).length))
                                    vm.editPrice(row.key, priceField.text.toLongOrNull() ?: 0L)
                                },
                                textStyle = TextStyle(fontFamily = com.detailline.callfollowcrm.presentation.theme.Pretendard, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary),
                                singleLine = true,
                                cursorBrush = SolidColor(TossBlue),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                visualTransformation = ThousandsCommaEditTransformation,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Spacer(Modifier.size(6.dp))
                        Text(
                            "원  (${PricingItemRepository.formatWon(row.priceWon)}" +
                                (if (row.unit == PricingItemEntity.UNIT_PYEONG) " · 평당)" else ")"),
                            fontSize = 13.sp, color = TossTextSecondary
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier.fillMaxWidth()
                            .background(TossBlueSoft, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        EditField(
                            value = row.basisText,
                            onChange = { vm.editBasis(row.key, it) },
                            hint = "가격 기준 (예: 오염도에 따라 다름)",
                            modifier = Modifier.fillMaxWidth(),
                            bold = false
                        )
                    }
                }
            }
        }
        BottomBar {
            Button(
                onClick = vm::save,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TossBlue),
                shape = RoundedCornerShape(14.dp)
            ) { Text("이대로 가격표에 담기", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        }
    }
}

@Composable
private fun HeaderCard(emoji: String, title: String, subtitle: String) {
    Column(
        Modifier.fillMaxWidth().background(Color.White).padding(20.dp)
    ) {
        Text("$emoji  $title", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, fontSize = 13.sp, color = TossTextSecondary, lineHeight = 19.sp)
    }
}

@Composable
private fun BottomBar(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color.White).padding(16.dp)) { content() }
}

@Composable
private fun CategoryTag(category: String) {
    val label = when (category) { "NEW" -> "신축"; "OLD" -> "구축"; else -> null } ?: return
    Text(
        "[$label]",
        fontSize = 12.sp, color = TossTextTertiary, fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(end = 6.dp)
    )
}

@Composable
private fun EditField(
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    bold: Boolean
) {
    // 로컬 TextFieldValue 로 커서 유지(글자 중간 수정해도 끝으로 안 튐). 첫 값만 seed, 이후엔 사용자 편집이 소스.
    var tfv by remember { mutableStateOf(TextFieldValue(value)) }
    Box(modifier) {
        if (tfv.text.isEmpty()) {
            Text(hint, color = TossTextTertiary, fontSize = if (bold) 15.sp else 14.sp)
        }
        BasicTextField(
            value = tfv,
            onValueChange = { tfv = it; onChange(it.text) },
            textStyle = TextStyle(
                fontFamily = com.detailline.callfollowcrm.presentation.theme.Pretendard,
                fontSize = if (bold) 15.sp else 14.sp,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                color = TossTextPrimary
            ),
            singleLine = true,
            cursorBrush = SolidColor(TossBlue),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
