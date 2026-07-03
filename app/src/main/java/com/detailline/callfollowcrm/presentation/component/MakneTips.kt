package com.detailline.callfollowcrm.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.detailline.callfollowcrm.presentation.navigation.Destinations
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 막내 팁 — 상담함 '최근 대화' 사이에 끼우는 기능 발견 카드 + 누르면 뜨는 사용법 안내판.
 *   프로토 ringgo-redesign.html 의 .mktip / .tg-card 1:1 이식. (2026-07-04 사장님 아이디어)
 *   무심코 스크롤할 때 안 써본 기능을 광고판처럼 하나씩 소개하고, 누르면 그 기능으로 바로 이동.
 */
data class MakneTip(
    val key: String,
    val emoji: String,
    val tag: String,          // "왜 추천" (💡 아직 안 써보셨어요 등) — 광고 아닌 '도움' 느낌
    val title: String,
    val desc: String,
    val route: String,        // 누르면 이동할 Destinations 라우트
    val guide: List<String>   // 안내판 1-2-3 단계
)

/**
 * 우선순위 순. 노출 대상(eligible) = 아직 안 눌러본(used) · 안 닫은(dismissed) 것.
 *   TODO(후속): 실제 상태 기반 targeting(가격표 비었나·미수금 있나 등)으로 더 똑똑하게.
 */
val MAKNE_TIPS: List<MakneTip> = listOf(
    MakneTip(
        "price", "📋", "아직 안 써보셨어요", "보낸 문자로 가격표 자동완성",
        "예전에 보낸 견적 문자를 읽어 품목·가격·기준을 알아서 채워드려요.",
        Destinations.PRICING_ITEMS,
        listOf(
            "가격표 화면 오른쪽 위 ✨ \"문자에서 불러오기\"를 눌러요.",
            "예전에 보낸 견적 문자를 막내가 읽어 품목·가격을 뽑아와요.",
            "맞는지 확인하고 저장하면 가격표 완성이에요!"
        )
    ),
    MakneTip(
        "quote", "📜", "이런 것도 돼요", "도장 찍힌 정식 견적서",
        "상호·대표·직인을 한 번 등록하면 견적서에 도장까지 자동으로 찍혀요.",
        Destinations.BUSINESS_INFO,
        listOf(
            "상호·대표님 이름·직인 문구를 한 번만 입력해요.",
            "저장하면 앞으로 견적서에 도장이 자동으로 찍혀요.",
            "채팅에서 \"견적 작성\"만 누르면 정식 견적서 완성!"
        )
    ),
    MakneTip(
        "recur", "🔁", "단골 관리", "단골에게 정기 문자 자동 발송",
        "명절·안부 문자를 한 번 정해두면 알아서 챙겨 보내드려요.",
        Destinations.RECURRING,
        listOf(
            "오른쪽 위 ＋로 보낼 문자와 날짜를 정해요.",
            "명절·안부 문자를 단골에게 자동으로 보내드려요.",
            "한 번 정해두면 막내가 알아서 챙겨요."
        )
    ),
    MakneTip(
        "autosms", "📩", "이런 것도 돼요", "시공 하루 전 자동 안내 문자",
        "부재중 응답·시공 D-1·도착 안내까지, 깜빡해도 자동으로 나가요.",
        Destinations.SETTINGS_AUTOSMS,
        listOf(
            "부재중 응답·시공 D-1·도착 안내 중 원하는 걸 켜요.",
            "정해둔 상황이 되면 문자가 자동으로 나가요.",
            "깜빡해도 고객 응대가 빠지지 않아요."
        )
    ),
    MakneTip(
        "settle", "💰", "미수금 챙기기", "못 받은 돈 한눈에",
        "계약금·잔금 누가 안 줬는지 자동 정리 — 떼일 걱정 줄여드려요.",
        Destinations.SETTLEMENT,
        listOf(
            "위 \"미수금\"만 눌러 아직 못 받은 돈을 모아봐요.",
            "계약금·잔금 받으면 \"받음\"으로 표시해요.",
            "누가 안 줬는지 한눈에 — 떼일 걱정이 줄어요."
        )
    ),
    MakneTip(
        "tpl", "💬", "답장 더 빠르게", "자주 쓰는 문구, 템플릿으로",
        "매번 치던 안내 문구를 저장해두고 한 번에 넣어요.",
        Destinations.TEMPLATE_LIST,
        listOf(
            "오른쪽 위 ＋로 자주 쓰는 안내 문구를 저장해요.",
            "채팅에서 \"문구 넣기\"로 한 번에 불러와요.",
            "매번 안 쳐도 돼서 답장이 훨씬 빨라져요."
        )
    ),
    MakneTip(
        "tone", "✨", "프로 기능", "나처럼 답하는 AI",
        "사장님 말투를 학습해 추천 답변이 점점 사장님처럼 바뀌어요.",
        Destinations.STYLE_LEARNING,
        listOf(
            "막내가 사장님의 지난 답장을 배워요.",
            "추천 답변이 점점 사장님 말투로 바뀌어요.",
            "쓸수록 더 사장님처럼 똑똑해져요."
        )
    ),
    MakneTip(
        "report", "📊", "장사 분석", "매출·전환율 리포트",
        "이번 달 매출·시공 전환율·추천 채택률을 한 장으로.",
        Destinations.REPORT,
        listOf(
            "이번 달 매출·문의·전환율을 한눈에 봐요.",
            "지난 기간과 비교해 장사 흐름을 알 수 있어요.",
            "어떤 시공이 많은지도 자동으로 정리돼요."
        )
    )
)

/**
 * 안내판 전역 표시 컨트롤러 — 팁 카드를 누르면(=기능 화면으로 이동) 그 화면 위에 안내판을 띄운다.
 *   AppRoot 가 pending 을 구독해 [MakneGuideOverlay] 를 최상단에 렌더. 프로토의 tg-card 오버레이와 동일.
 */
object MakneGuideController {
    private val _pending = MutableStateFlow<MakneTip?>(null)
    val pending: StateFlow<MakneTip?> = _pending.asStateFlow()
    fun show(tip: MakneTip) { _pending.value = tip }
    fun dismiss() { _pending.value = null }
}

private val TipBg = Color(0xFFF5F8FF)
private val TipBorder = Color(0xFFE4EBF7)
private val TipTint = Color(0xFFEEF4FF)
private val BlueDark = Color(0xFF1B64DA)

/** 상담함 최근 대화 사이 팁 카드 — 대화 줄과 비슷한 크기(광고 티 안 나게). 프로토 .mktip 1:1. */
@Composable
fun MakneTipCard(tip: MakneTip, onGo: () -> Unit, onDismiss: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(TipBg)
            .border(1.dp, TipBorder, RoundedCornerShape(14.dp))
            .clickable { onGo() }
            .padding(horizontal = 13.dp, vertical = 11.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 14.dp)) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Color.White),
                contentAlignment = Alignment.Center
            ) { Text(tip.emoji, fontSize = 18.sp) }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text("💡 ${tip.tag}", fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, color = BlueDark, maxLines = 1)
                Text(tip.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(tip.desc, fontSize = 13.sp, color = TossTextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            Text("써보기 ›", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TossBlue)
        }
        // × 그만 보기 — 오른쪽 위 작게. 자식 clickable 이 부모(onGo) 탭을 소비.
        Box(
            Modifier.align(Alignment.TopEnd).size(24.dp).clip(CircleShape).clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) { Text("×", fontSize = 15.sp, color = TossTextTertiary, fontWeight = FontWeight.Bold) }
    }
}

/** 기능 카드 누르면 그 화면 위에 뜨는 막내 안내판 — 1-2-3 사용법. 프로토 .tg-card 1:1. */
@Composable
fun MakneGuideOverlay(tip: MakneTip, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                Modifier.fillMaxWidth().padding(14.dp)
                    .clip(RoundedCornerShape(20.dp)).background(Color.White)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { /* 카드 탭은 닫지 않음 */ }
                    .padding(horizontal = 18.dp, vertical = 18.dp)
            ) {
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(15.dp)).background(TipTint).align(Alignment.CenterHorizontally),
                    contentAlignment = Alignment.Center
                ) { Text(tip.emoji, fontSize = 26.sp) }
                Spacer(Modifier.height(11.dp))
                Text("막내가 알려드릴게요 😊", fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = BlueDark,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Spacer(Modifier.height(3.dp))
                Text(tip.title, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Spacer(Modifier.height(15.dp))
                tip.guide.forEachIndexed { i, step ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 11.dp), verticalAlignment = Alignment.Top) {
                        Box(
                            Modifier.size(23.dp).clip(CircleShape).background(TipTint),
                            contentAlignment = Alignment.Center
                        ) { Text("${i + 1}", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TossBlue) }
                        Spacer(Modifier.width(10.dp))
                        Text(step, fontSize = 13.5.sp, color = TossTextSecondary, lineHeight = 20.sp,
                            modifier = Modifier.weight(1f).padding(top = 1.dp))
                    }
                }
                Spacer(Modifier.height(2.dp))
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(TossBlue)
                        .clickable { onDismiss() }.padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) { Text("알겠어요, 해볼게요", fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold, color = Color.White) }
                Spacer(Modifier.height(8.dp))
                Text("다음에 볼게요", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = TossTextTertiary,
                    modifier = Modifier.fillMaxWidth().clickable { onDismiss() }.padding(6.dp), textAlign = TextAlign.Center)
            }
        }
    }
}
