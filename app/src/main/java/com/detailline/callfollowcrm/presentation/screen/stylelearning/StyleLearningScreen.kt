package com.detailline.callfollowcrm.presentation.screen.stylelearning

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StyleLearningScreen(
    viewModel: StyleLearningViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state.toast) { if (state.toast != null) viewModel.consumeToast() }

    Scaffold(
        containerColor = TossGrayBg,
        topBar = {
            TopAppBar(
                title = { Text("내 말투 학습", color = TossTextPrimary) },
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
                .background(TossGrayBg)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("STEP 1 · 가져올 기간")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 3, 6).forEach { m ->
                    FilterChip(
                        selected = state.periodMonths == m,
                        onClick = { viewModel.selectPeriod(m) },
                        label = { Text("최근 ${m}개월") }
                    )
                }
            }
            Button(onClick = viewModel::learnFromSamples, enabled = !state.loading) {
                Text(if (state.loading) "분석 중..." else "SMS 가져오기 + 학습")
            }

            Text("STEP 2 · 분석")
            LinearProgressIndicator(
                progress = { state.progress / 100f },
                modifier = Modifier.fillMaxWidth()
            )
            Text("${state.progress}%")

            Text("STEP 3 · 프로파일")
            Text("분석 문자: ${state.sampleCount}개")
            Text("친절도: ${state.kindness}")
            Text("평균 길이: ${state.avgLength}자")
            Text("이모티콘 빈도: ${"%.1f".format(state.emojiPerMessage)}")
            Spacer(Modifier.height(8.dp))
            Button(onClick = {}, modifier = Modifier.fillMaxWidth(), enabled = state.progress == 100) {
                Text("학습 완료 · 저장")
            }
        }
    }
}
