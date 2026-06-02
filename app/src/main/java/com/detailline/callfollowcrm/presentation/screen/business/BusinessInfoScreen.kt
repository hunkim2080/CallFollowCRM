package com.detailline.callfollowcrm.presentation.screen.business

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import com.detailline.callfollowcrm.data.preferences.AppPreferences
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary

/**
 * 사업자정보 등록 (2026-06-01) — 견적서/시공접수서 발행에 들어갈 사장님 정보.
 *   상호·대표자·사업자등록번호·사업장 주소·연락처·견적 유효기간(기본 14일). SharedPreferences 저장.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    var phone by remember { mutableStateOf(prefs.bizPhone) }
    var validDays by remember { mutableStateOf(prefs.bizQuoteValidDays.toString()) }

    Scaffold(
        containerColor = TossGrayBg,
        topBar = {
            TopAppBar(
                title = { Text("사업자정보", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "뒤로", tint = TossTextPrimary) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TossGrayBg)
            )
        }
    ) { inner ->
        Column(
            Modifier
                .padding(inner)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                "견적서·시공접수서에 자동으로 들어가요. 한 번만 입력해두세요.",
                style = MaterialTheme.typography.bodySmall, color = TossTextSecondary
            )
            Spacer(Modifier.height(16.dp))

            Field("상호 (업체명)", name) { name = it }
            Field("대표자", owner) { owner = it }
            Field("사업자등록번호", bizNo, KeyboardType.Number) { bizNo = it }
            Field("사업장 주소", addr) { addr = it }
            Field("연락처", phone, KeyboardType.Phone) { phone = it }
            Field("견적 유효기간 (발행일로부터 N일)", validDays, KeyboardType.Number) {
                validDays = it.filter { c -> c.isDigit() }.take(3)
            }

            Spacer(Modifier.height(24.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(TossBlue)
                    .clickable {
                        prefs.bizName = name; prefs.bizOwner = owner; prefs.bizNo = bizNo
                        prefs.bizAddr = addr; prefs.bizPhone = phone
                        prefs.bizQuoteValidDays = validDays.toIntOrNull()?.coerceIn(1, 365) ?: 14
                        android.widget.Toast.makeText(context, "저장했어요", android.widget.Toast.LENGTH_SHORT).show()
                        onBack()
                    }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("저장", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun Field(label: String, value: String, keyboard: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) {
    // 프로토 .sheet-label + .sheet-input.
    Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TossTextTertiary,
        modifier = Modifier.padding(start = 2.dp, bottom = 7.dp, top = 12.dp))
    com.detailline.callfollowcrm.presentation.component.SheetTextField(
        value = value, onValueChange = onChange, placeholder = "", keyboardType = keyboard
    )
}
