package com.detailline.callfollowcrm.presentation.screen.settings

import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.presentation.component.AddressSearchDialog
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary
import com.detailline.callfollowcrm.widget.TodayWidgetProvider

/**
 * 집 주소 설정 — 다른 설정과 **같은 줄 카드**(LockRow)로. 누르면 **주소 검색**(Daum 우편번호 AddressSearchDialog)으로
 *   골라 넣는다(생 타이핑 X). (2026-11-05 사장님 "주소 찾는 api 있는데 이렇게 할거니")
 *   위젯 '집으로 길찾기'를 **주소 미등록**으로 누르면 설정 열리며 **주소 검색이 바로** 뜬다
 *   (반응형 신호 navEvents.openHomeAddress — 앱이 이미 켜져 있어도 뜬다).
 */
@Composable
internal fun HomeAddressSetting(container: AppContainer) {
    val context = LocalContext.current
    var address by remember { mutableStateOf(container.preferences.homeAddress) }
    var searching by remember { mutableStateOf(false) }
    // 반응형: 위젯 집 버튼(주소 미등록) 신호가 오면 — 앱이 켜져 있든 꺼져 있든 — 검색창을 띄운다.
    val openReq by container.navEvents.openHomeAddress.collectAsState()
    LaunchedEffect(openReq) {
        if (openReq) { searching = true; container.navEvents.clearOpenHomeAddress() }
    }
    LockRow(Icons.Filled.Home, TossGrayBg, TossTextTertiary, "집 주소",
        address.ifBlank { "눌러서 집 주소 검색 — 집으로 길찾기에 사용" }) { searching = true }
    if (searching) {
        AddressSearchDialog(
            onPicked = { picked ->
                searching = false
                runCatching {
                    container.preferences.homeAddress = picked
                    address = picked.trim()
                    TodayWidgetProvider.refresh(context)
                    // 저장됐다는 걸 보여준다 — 전엔 말없이 설정으로 나가 "된 건가?" 싶었다. (2026-11-05 사장님)
                    Toast.makeText(context, "집 주소 저장했어요 · 위젯 '집으로 길찾기'가 여기로 안내해요", Toast.LENGTH_LONG).show()
                }.onFailure {
                    Toast.makeText(context, "집 주소를 저장하지 못했어요. 다시 해주세요.", Toast.LENGTH_LONG).show()
                }
            },
            onDismiss = { searching = false }
        )
    }
}
