package com.detailline.callfollowcrm.presentation.screen.customer

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.detailline.callfollowcrm.data.repository.DeviceGalleryRepository
import com.detailline.callfollowcrm.data.repository.DeviceGalleryRepository.GalleryPhoto
import com.detailline.callfollowcrm.domain.schedule.WorkDateRange
import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.util.bottomBarClearance
import com.detailline.callfollowcrm.util.PermissionHelper
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary
import com.detailline.callfollowcrm.util.AddressExtractor
import com.detailline.callfollowcrm.util.DateTimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * 📅 「시공한 날 사진만」 현장사진 선택창. (프로토 EBpuEujiPoUvxLdwXPsSd1 — docs/DESIGN_site_photo_date_picker.md)
 *
 *   시공한 날 찍은 사진만 자동으로 모아 보여준다(며칠 치 안 뒤지게). 위 토글로 전체 보기.
 *   권한 확인·요청은 **부른 쪽**(CustomerDetailJobCard)이 끝내고, 여기엔 **허용된 상태로** 들어온다.
 *   고른 사진 URI 는 기존 `addSitePhotos(uris, jobId)` 로 그대로 넘어간다(뒤쪽은 안 바뀜).
 */
/**
 * 📅 선택창 **열기 컨트롤러** — 권한 확인/요청 + 다이얼로그 렌더를 한 곳에. 반환값은 "열기" 람다.
 *   권한 있으면 바로 열고, 없으면 요청 → 허용되면 열고 거부면 [onFallbackSystemPicker](시스템 피커).
 *   (호출 화면 CustomerDetailJobCard 를 안 키우려 여기 둔다 — §12-F)
 */
@Composable
fun rememberSitePhotoPickerLaunch(
    customerName: String?,
    address: String?,
    scheduledWorkDate: Long?,
    scheduledWorkDays: Int,
    maxPick: Int,
    onFallbackSystemPicker: () -> Unit,
    onPicked: (List<Uri>) -> Unit,
): () -> Unit {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var show by remember { mutableStateOf(false) }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        if (PermissionHelper.hasGalleryImages(ctx)) show = true else onFallbackSystemPicker()
    }
    if (show) {
        SitePhotoDatePicker(
            customerName = customerName, address = address,
            scheduledWorkDate = scheduledWorkDate, scheduledWorkDays = scheduledWorkDays,
            maxPick = maxPick,
            onDismiss = { show = false },
            onPicked = { show = false; onPicked(it) }
        )
    }
    return {
        if (PermissionHelper.hasGalleryImages(ctx)) show = true
        else permLauncher.launch(PermissionHelper.galleryImagePermissions())
    }
}

@Composable
fun SitePhotoDatePicker(
    customerName: String?,
    address: String?,
    scheduledWorkDate: Long?,
    scheduledWorkDays: Int,
    maxPick: Int,
    onDismiss: () -> Unit,
    onPicked: (List<Uri>) -> Unit,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val workRange = remember(scheduledWorkDate, scheduledWorkDays) {
        WorkDateRange.of(scheduledWorkDate, scheduledWorkDays)
    }
    // 시공일이 없으면 거를 날이 없다 → 전체 보기로 연다. (프로토: 기본 시공한 날만)
    var dayMode by remember { mutableStateOf(workRange != null) }
    var loading by remember { mutableStateOf(true) }
    var all by remember { mutableStateOf<List<GalleryPhoto>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<Uri>>(emptySet()) }

    LaunchedEffect(dayMode) {
        loading = true
        val range = if (dayMode) workRange else null
        all = withContext(Dispatchers.Default) { DeviceGalleryRepository.query(ctx, range) }
        loading = false
    }

    // 섹션 = (머리글, 시공한날?, 사진들). 시공한 날만 모드는 한 섹션, 전체 보기는 날짜별로 묶는다.
    val sections = remember(all, dayMode, workRange) { buildSections(all, dayMode, workRange) }
    val workDateLabel = remember(scheduledWorkDate) {
        scheduledWorkDate?.let { DateTimeUtils.formatMonthDay(it) } ?: ""
    }

    val statusTop = androidx.compose.foundation.layout.WindowInsets.statusBars
        .asPaddingValues().calculateTopPadding()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(AppTheme.colors.bg)) {
            // 상단바 (상태바 밑으로)
            Row(
                Modifier.fillMaxWidth().background(AppTheme.colors.surface)
                    .padding(top = statusTop).padding(16.dp, 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("✕", fontSize = 22.sp, color = TossTextTertiary,
                    modifier = Modifier.clickable { onDismiss() }.padding(end = 10.dp))
                Text("현장 사진 올리기", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
            }
            // 맥락 배너
            val who = customerName?.takeIf { it.isNotBlank() }?.let { "$it 고객님" } ?: "현장"
            val where = AddressExtractor.tidyAddress(address).takeIf { it.isNotBlank() }
            Column(Modifier.fillMaxWidth().background(AppTheme.colors.surface).padding(16.dp, 0.dp, 16.dp, 12.dp)) {
                Text(who, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
                if (where != null) Text(where, fontSize = 12.5.sp, color = TossTextTertiary)
            }
            // 날짜 토글
            if (workRange != null) {
                Row(
                    Modifier.fillMaxWidth().background(AppTheme.colors.surface).padding(16.dp, 4.dp, 16.dp, 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SegButton("📅 시공한 날만 ($workDateLabel)", on = dayMode, modifier = Modifier.weight(1f)) { dayMode = true }
                    SegButton("전체 보기", on = !dayMode, modifier = Modifier.weight(1f)) { dayMode = false }
                }
                if (dayMode) Text(
                    "💡 $workDateLabel 시공한 날 찍은 사진만 모아봤어요 — 며칠 치 안 뒤져도 돼요.",
                    fontSize = 11.5.sp, color = TossTextTertiary,
                    modifier = Modifier.background(AppTheme.colors.surface).fillMaxWidth().padding(16.dp, 0.dp, 16.dp, 10.dp)
                )
            } else {
                Text(
                    "시공일이 아직 없어요 — 전체 사진에서 골라주세요.",
                    fontSize = 11.5.sp, color = TossTextTertiary,
                    modifier = Modifier.background(AppTheme.colors.surface).fillMaxWidth().padding(16.dp, 0.dp, 16.dp, 10.dp)
                )
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    sections.all { it.photos.isEmpty() } -> Text(
                        if (dayMode) "시공한 날 찍은 사진이 없어요.\n위 '전체 보기'로 골라주세요." else "사진이 없어요.",
                        fontSize = 13.sp, color = TossTextTertiary, textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp)
                    )
                    else -> LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
                    ) {
                        sections.forEach { sec ->
                            if (sec.photos.isEmpty()) return@forEach
                            item(span = { GridItemSpan(maxLineSpan) }) { DayHeader(sec.label, sec.isWork, sec.photos.size) }
                            items(sec.photos, key = { it.uri.toString() }) { p ->
                                PhotoTile(
                                    photo = p,
                                    selected = p.uri in selected,
                                    onClick = {
                                        selected = when {
                                            p.uri in selected -> selected - p.uri
                                            selected.size >= maxPick -> selected   // 자리 초과면 무시
                                            else -> selected + p.uri
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 하단바 (네비바 위로)
            Row(
                Modifier.fillMaxWidth().background(AppTheme.colors.surface)
                    .bottomBarClearance(0.dp).padding(16.dp, 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    buildString { append(selected.size); append("장 선택됨") },
                    fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TossTextSecondary
                )
                Box(Modifier.weight(1f))
                val can = selected.isNotEmpty()
                Box(
                    Modifier.clip(RoundedCornerShape(13.dp))
                        .background(if (can) AppTheme.colors.primary else AppTheme.colors.line)
                        .clickable(enabled = can) {
                            // 고른 순서가 아니라 화면 순서(최신순)대로 넘긴다.
                            val order = all.map { it.uri }.filter { it in selected }
                            onPicked(order)
                        }
                        .padding(horizontal = 22.dp, vertical = 13.dp)
                ) {
                    Text(
                        if (can) "${selected.size}장 올리기" else "올리기",
                        fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        color = if (can) Color.White else TossTextTertiary
                    )
                }
            }
        }
    }
}

private data class PhotoSection(val label: String, val isWork: Boolean, val photos: List<GalleryPhoto>)

/** 시공한 날만 모드 = 한 섹션. 전체 보기 = 날짜별 묶음(시공한 날 섹션이 맨 위). */
private fun buildSections(all: List<GalleryPhoto>, dayMode: Boolean, workRange: LongRange?): List<PhotoSection> {
    if (dayMode) {
        val label = workRange?.let { DateTimeUtils.formatDateLabel(it.first) } ?: ""
        return listOf(PhotoSection("$label · 시공한 날", true, all))
    }
    // 전체: 촬영일(startOfDay)별로 묶어 최신순. 시공 기간에 든 날은 '시공한 날' 표시.
    val byDay = all.groupBy { DateTimeUtils.startOfDay(it.takenMs) }
    return byDay.entries.sortedByDescending { it.key }.map { (day, photos) ->
        val isWork = workRange != null && day in workRange
        val label = DateTimeUtils.formatDateLabel(day) + if (isWork) " · 시공한 날" else ""
        PhotoSection(label, isWork, photos)
    }
}

@Composable
private fun SegButton(text: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(9.dp))
            .background(if (on) AppTheme.colors.surface else Color.Transparent)
            .then(if (on) Modifier.border(1.dp, AppTheme.colors.line, RoundedCornerShape(9.dp)) else Modifier)
            .clickable { onClick() }
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 13.5.sp, fontWeight = FontWeight.Bold,
            color = if (on) TossTextPrimary else TossTextTertiary)
    }
}

@Composable
private fun DayHeader(label: String, isWork: Boolean, count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(4.dp, 10.dp, 4.dp, 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape)
            .background(if (isWork) AppTheme.colors.primary else AppTheme.colors.line))
        Text("$label · ${count}장", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = TossTextTertiary)
    }
}

@Composable
private fun PhotoTile(photo: GalleryPhoto, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.aspectRatio(1f).clip(RoundedCornerShape(11.dp))
            .background(AppTheme.colors.line)
            .then(if (selected) Modifier.border(3.dp, AppTheme.colors.primary, RoundedCornerShape(11.dp)) else Modifier)
            .clickable { onClick() }
    ) {
        coil.compose.AsyncImage(
            model = photo.uri,
            contentDescription = "사진",
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        // 좌하단 촬영 시각(작게). SimpleDateFormat 안 씀(§12) — 시/분만 조립.
        Text(
            timeTag(photo.takenMs),
            fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White,
            modifier = Modifier.align(Alignment.BottomStart).padding(5.dp)
                .clip(RoundedCornerShape(5.dp)).background(Color.Black.copy(alpha = 0.4f)).padding(horizontal = 5.dp, vertical = 1.dp)
        )
        // 우상단 선택 동그라미
        Box(
            Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp).clip(CircleShape)
                .background(if (selected) AppTheme.colors.primary else Color.White.copy(alpha = 0.33f))
                .border(2.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (selected) Icon(Icons.Default.Check, null,
                tint = Color.White, modifier = Modifier.size(14.dp))
        }
    }
}

/** "H:mm" 시각표 — 타일 좌하단용. 날짜는 섹션이 말하니 시·분만(§12 SimpleDateFormat 안 씀). */
private fun timeTag(ms: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    return "%d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
}
