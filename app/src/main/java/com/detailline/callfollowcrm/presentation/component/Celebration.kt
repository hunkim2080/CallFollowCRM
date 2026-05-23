package com.detailline.callfollowcrm.presentation.component

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import kotlinx.coroutines.delay
import nl.dionsegijn.konfetti.compose.KonfettiView
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter
import java.util.concurrent.TimeUnit

/**
 * 예약 확정 등 마일스톤 전환 시 띄우는 축하 오버레이.
 *  - 콘페티 파티클 (양 쪽 위에서 쏟아짐)
 *  - 가운데 큰 이모지 + "예약 확정!" 문구
 *  - 2.5초 후 자동 사라짐
 *  - 진동 한 번 (호출부에서 별도)
 */
@Composable
fun CelebrationOverlay(
    title: String = "예약 확정!",
    subtitle: String = "축하해요 🎉",
    onFinished: () -> Unit
) {
    val parties = remember {
        listOf(
            // 좌측 상단에서 쏟아지는 콘페티
            Party(
                speed = 10f,
                maxSpeed = 30f,
                damping = 0.9f,
                spread = 60,
                angle = 60,
                colors = listOf(0xfce18a, 0xff726d, 0xf4306d, 0xb48def, 0x3182F6, 0x18BE5C),
                emitter = Emitter(duration = 700, TimeUnit.MILLISECONDS).max(120),
                position = Position.Relative(0.0, 0.15)
            ),
            // 우측 상단에서 쏟아지는 콘페티
            Party(
                speed = 10f,
                maxSpeed = 30f,
                damping = 0.9f,
                spread = 60,
                angle = 120,
                colors = listOf(0xfce18a, 0xff726d, 0xf4306d, 0xb48def, 0x3182F6, 0x18BE5C),
                emitter = Emitter(duration = 700, TimeUnit.MILLISECONDS).max(120),
                position = Position.Relative(1.0, 0.15)
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.25f)),
        contentAlignment = Alignment.Center
    ) {
        KonfettiView(
            modifier = Modifier.fillMaxSize(),
            parties = parties
        )
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            tonalElevation = 8.dp,
            modifier = Modifier.padding(horizontal = 40.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("🎉", fontSize = 56.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = TossBlue,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TossTextSecondary
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(2500)
        onFinished()
    }
}

/**
 * 축하 진동 — 짧게-짧게-길게 패턴 (party feel).
 * minSdk 26 이상에서만 사용되므로 VibrationEffect 안전.
 */
fun vibrateCelebration(context: Context) {
    runCatching {
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(VibratorManager::class.java)
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        // 타이밍(ms): [대기, 진동, 대기, 진동, 대기, 진동] — 짧·짧·길
        val timings = longArrayOf(0, 80, 60, 80, 60, 300)
        val amplitudes = intArrayOf(0, 180, 0, 180, 0, 255)
        vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
    }
}
