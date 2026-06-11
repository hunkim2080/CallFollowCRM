package com.detailline.callfollowcrm.presentation.component

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 33+ 는 READ_MEDIA_IMAGES, 그 이하(갤S9 등)는 READ_EXTERNAL_STORAGE. */
private fun mediaReadPermission(): String =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
    else Manifest.permission.READ_EXTERNAL_STORAGE

private fun hasMediaPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, mediaReadPermission()) == PackageManager.PERMISSION_GRANTED

/** 최근 추가순 기기 사진 URI. LIMIT 은 sortOrder 에 넣지 않음(OneUI 빈결과 함정) — 루프에서 cap. */
private suspend fun queryRecentImages(context: Context, limit: Int = 300): List<Uri> =
    withContext(Dispatchers.IO) {
        val out = ArrayList<Uri>(limit)
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val proj = arrayOf(MediaStore.Images.Media._ID)
        val sort = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        runCatching {
            context.contentResolver.query(collection, proj, null, null, sort)?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while (c.moveToNext() && out.size < limit) {
                    out.add(ContentUris.withAppendedId(collection, c.getLong(idCol)))
                }
            }
        }
        out
    }

/**
 * 카톡식 사진 첨부 바텀시트 — 아래에서 올라오는 자체 갤러리(시스템 "내 파일" 대신).
 *   기기 사진을 그리드로 보여주고 다중 선택(번호 배지) → [첨부]. 권한 없으면 요청, 거부 시 "파일에서" fallback.
 *   읽기 전용 + 입력칸 없음이라 바텀시트 키보드 이슈 무관.
 *
 * @param maxSelectable 추가로 더 고를 수 있는 최대 장수(이미 첨부된 것 제외).
 * @param onOpenFiles 시스템 사진/파일 피커로 빠지는 fallback(권한 거부·구형 기기 안전망).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoPickerSheet(
    maxSelectable: Int,
    onConfirm: (List<Uri>) -> Unit,
    onDismiss: () -> Unit,
    onOpenFiles: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var granted by remember { mutableStateOf(hasMediaPermission(context)) }
    var images by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val selected = remember { mutableStateListOf<Uri>() }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { ok -> granted = ok }

    LaunchedEffect(granted) {
        if (granted) images = queryRecentImages(context)
        else permLauncher.launch(mediaReadPermission())
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Color.White) {
        Column(
            // navigationBarsPadding = 내비바 위로(에지투에지 케이스), + 추가 여백으로 어느 기기든 버튼이 바에 안 붙게.
            Modifier.fillMaxWidth().navigationBarsPadding()
                .padding(horizontal = 14.dp).padding(bottom = 22.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("사진 첨부", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = TossTextPrimary)
                Spacer(Modifier.weight(1f))
                // 권한 거부/구형 기기 안전망 — 기존 시스템 피커로.
                Text(
                    "📁 파일에서", color = TossBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onOpenFiles() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            when {
                !granted -> Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("사진을 보려면 접근 권한이 필요해요", color = TossTextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.size(10.dp))
                        Text(
                            "권한 허용", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(TossBlue)
                                .clickable { permLauncher.launch(mediaReadPermission()) }
                                .padding(horizontal = 18.dp, vertical = 10.dp)
                        )
                    }
                }
                images.isEmpty() -> Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                    Text("기기에 사진이 없어요", color = TossTextTertiary, fontSize = 13.sp)
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        items(images, key = { it.toString() }) { uri ->
                            val idx = selected.indexOf(uri)
                            val isSel = idx >= 0
                            Box(
                                Modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp)).clickable {
                                    if (isSel) selected.remove(uri)
                                    else if (selected.size < maxSelectable) selected.add(uri)
                                }
                            ) {
                                AsyncImage(
                                    model = uri, contentDescription = null,
                                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                                )
                                if (isSel) {
                                    Box(Modifier.fillMaxSize().background(Color(0x553182F6)))
                                    Box(
                                        Modifier.align(Alignment.TopEnd).padding(5.dp).size(20.dp)
                                            .clip(CircleShape).background(TossBlue),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("${idx + 1}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.size(10.dp))
                    val n = selected.size
                    Text(
                        if (n > 0) "첨부 ($n)" else "사진을 선택하세요",
                        color = if (n > 0) Color.White else TossTextTertiary,
                        fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(if (n > 0) TossBlue else TossGrayBg)
                            .clickable(enabled = n > 0) { onConfirm(selected.toList()) }
                            .padding(vertical = 14.dp)
                    )
                }
            }
        }
    }
}
