package com.detailline.callfollowcrm.presentation.screen.tour

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.detailline.callfollowcrm.presentation.component.DemoWebPopup
import com.detailline.callfollowcrm.presentation.component.TossCard
import com.detailline.callfollowcrm.presentation.theme.AppShape
import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.theme.AppType

private class DemoItem(
    val icon: ImageVector,
    val title: String,
    val sub: String,
    val asset: String,
    val cta: () -> Unit
)

/**
 * 🎬 **막내가 이런 것도 해요 — 기능 둘러보기.** (2026-10-24 사장님 「가」, 전환율 둘러보기)
 *
 * 킬러 기능을 **보고 싶을 때(pull)** 보는 자리. 목록에서 누르면 **어두운 팝업 + 프로토 1:1(웹뷰)** 로 재생.
 *   대화 중 갑자기 막고 뜨는 팝업(push) 은 뺐다 — 일하려는 사람을 안 막으려고. (사장님 "어느 타이밍에 보여줄지가 난관")
 */
@Composable
fun FeatureTourScreen(
    onBack: () -> Unit,
    onOpenRecordingSettings: () -> Unit,
    onOpenCollab: () -> Unit,
    onOpenStats: () -> Unit
) {
    var open by remember { mutableStateOf<DemoItem?>(null) }

    val items = remember {
        listOf(
            DemoItem(Icons.Default.Call, "통화 요약", "전화 끊으면 시간순으로 자동 요약", "demos/callsummary.html", onOpenRecordingSettings),
            DemoItem(Icons.Default.Call, "부재중 자동문자", "못 받아도 고객에게 자동으로 문자", "demos/missedcall.html", onOpenRecordingSettings),
            DemoItem(Icons.Default.Person, "전화 미리보기", "받기 전에 누군지 화면 위 쪽지로", "demos/callerpreview.html", onOpenRecordingSettings),
            DemoItem(Icons.AutoMirrored.Filled.Message, "접수서 보내기", "고객이 날짜·주소 적으면 자동 캘린더", "demos/intake_demo.html") {},
            DemoItem(Icons.Default.Group, "협업 사장", "일손 급할 때 다른 사장님과 함께·일당 정산", "demos/collab.html", onOpenCollab),
            DemoItem(Icons.Default.Place, "내 기록", "완료 찍을수록 번호·지도·성과가 쌓여요", "demos/myrecord.html", onOpenStats)
        )
    }

    Scaffold(
        containerColor = AppTheme.colors.surfaceMuted,
        topBar = {
            Row(
                Modifier.fillMaxWidth().background(AppTheme.colors.surface).padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", tint = AppTheme.colors.text)
                }
                Text("막내가 이런 것도 해요", style = AppType.title, fontWeight = FontWeight.ExtraBold, color = AppTheme.colors.text)
            }
        }
    ) { inner ->
        Column(
            Modifier.padding(inner).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            Text(
                "전화·접수·협업… 막내가 뒤에서 챙기는 것들이에요. 눌러서 움직이는 데모로 보세요.",
                style = AppType.caption, color = AppTheme.colors.textSub
            )
            Spacer(Modifier.height(14.dp))

            items.forEach { item ->
                TossCard(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    onClick = { open = item }
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(38.dp).clip(AppShape.md).background(AppTheme.colors.primaryBg),
                            contentAlignment = Alignment.Center
                        ) { Icon(item.icon, null, tint = AppTheme.colors.primary, modifier = Modifier.size(20.dp)) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.title, style = AppType.label, fontWeight = FontWeight.ExtraBold, color = AppTheme.colors.text)
                            Spacer(Modifier.height(2.dp))
                            Text(item.sub, style = AppType.caption, color = AppTheme.colors.textSub)
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = AppTheme.colors.textHint, modifier = Modifier.size(20.dp))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    open?.let { item ->
        DemoWebPopup(
            assetPath = item.asset,
            onCta = { open = null; item.cta() },
            onDismiss = { open = null }
        )
    }
}
