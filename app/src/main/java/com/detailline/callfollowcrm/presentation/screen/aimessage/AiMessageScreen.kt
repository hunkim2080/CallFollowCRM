package com.detailline.callfollowcrm.presentation.screen.aimessage

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.util.SmsIntentHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiMessageScreen(
    viewModel: AiMessageViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var priceExpanded by remember { mutableStateOf(false) }
    var dateExpanded by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = TossGrayBg,
        topBar = {
            TopAppBar(
                title = { Text("AI 문자함", color = TossTextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "뒤로") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TossGrayBg)
            )
        }
    ) { inner ->
        Column(
            Modifier
                .padding(inner)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column {
                com.detailline.callfollowcrm.presentation.component.SheetFieldLabel("고객 번호")
                com.detailline.callfollowcrm.presentation.component.SheetTextField(
                    state.customerPhone, viewModel::onPhoneChange, placeholder = "010-0000-0000",
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone)
            }
            Column {
                com.detailline.callfollowcrm.presentation.component.SheetFieldLabel("고객 문의")
                com.detailline.callfollowcrm.presentation.component.SheetTextField(
                    state.customerMessage, viewModel::onMessageChange, placeholder = "고객 문의 내용",
                    singleLine = false, minHeightDp = 56)
            }
            Button(onClick = viewModel::loadSuggestions, enabled = !state.loading) {
                Text(if (state.loading) "생성 중..." else "답변 3개 생성")
            }
            if (state.intents.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.intents.forEach { label ->
                        Text(
                            text = label,
                            color = TossBlue,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
            state.replies.forEachIndexed { index, reply ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (state.selectedReply == reply) TossBlue.copy(alpha = 0.12f) else Color.White,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { viewModel.selectReply(reply) }
                        .padding(12.dp)
                ) {
                    Text("${index + 1}", fontWeight = FontWeight.Bold, color = TossBlue)
                    Spacer(Modifier.height(6.dp))
                    Text(reply, color = TossTextPrimary)
                }
            }

            Column {
                com.detailline.callfollowcrm.presentation.component.SheetFieldLabel("전송 전 편집")
                com.detailline.callfollowcrm.presentation.component.SheetTextField(
                    state.selectedReply, viewModel::onReplyEdit, placeholder = "전송 전 편집",
                    singleLine = false, minHeightDp = 76)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column {
                    Text(
                        text = if (state.selectedPrice.isBlank()) "가격 선택" else state.selectedPrice,
                        color = TossBlue,
                        modifier = Modifier.clickable { priceExpanded = true }
                    )
                    DropdownMenu(expanded = priceExpanded, onDismissRequest = { priceExpanded = false }) {
                        state.priceCandidates.forEach {
                            DropdownMenuItem(
                                text = { Text(it) },
                                onClick = {
                                    viewModel.selectPrice(it)
                                    priceExpanded = false
                                }
                            )
                        }
                    }
                }
                Column {
                    Text(
                        text = if (state.selectedDate.isBlank()) "날짜 선택" else state.selectedDate,
                        color = TossBlue,
                        modifier = Modifier.clickable { dateExpanded = true }
                    )
                    DropdownMenu(expanded = dateExpanded, onDismissRequest = { dateExpanded = false }) {
                        state.dateCandidates.forEach {
                            DropdownMenuItem(
                                text = { Text(it) },
                                onClick = {
                                    viewModel.selectDate(it)
                                    dateExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    openSms(
                        context = context,
                        phone = state.customerPhone,
                        body = state.selectedReply
                    )
                },
                enabled = state.selectedReply.isNotBlank() && state.customerPhone.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("문자앱에서 전송하기")
            }
        }
    }
}

private fun openSms(context: Context, phone: String, body: String) {
    SmsIntentHelper.openSmsCompose(context, phone, body)
}
