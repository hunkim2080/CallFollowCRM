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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.shadow
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
    // 🔴 선택은 **GalleryPhoto 로** 들고 있는다 — 모드를 바꿔도 다른 날 선택이 안 사라지게(P1).
    //   전엔 Uri Set 이라, 올릴 땐 '현재 모드 목록'으로 걸러 고른 수보다 적게 올라갔다. (코덱스 P1-1)
    var selected by remember { mutableStateOf<List<GalleryPhoto>>(emptyList()) }
    val selectedUris = remember(selected) { selected.map { it.uri }.toSet() }
    // 🔁 34+ '사진 더 고르기' 로 접근 범위가 바뀌면 다시 불러오게 하는 키.
    var reloadKey by remember { mutableStateOf(0) }
    val partial = remember(reloadKey) { PermissionHelper.isPartialGallery(ctx) }
    val morePhotosLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { reloadKey++ }

    LaunchedEffect(dayMode, reloadKey) {
        loading = true
        val range = if (dayMode) workRange else null
        all = withContext(Dispatchers.Default) { DeviceGalleryRepository.query(ctx, range) }
        loading = false
    }

    // 섹션 = 날짜별 묶음. 시공한 날만 모드는 범위 안 날들, 전체 보기는 전부(시공 기간 날은 '시공한 날' 표시).
    val sections = remember(all, workRange) { buildSections(all, workRange) }
    // 토글/안내 라벨 — 여러 날 시공이면 "11/11~11/13". (코덱스 P1-3)
    val workDateLabel = remember(scheduledWorkDate, scheduledWorkDays) {
        val s = scheduledWorkDate ?: return@remember ""
        val startMd = DateTimeUtils.formatMonthDay(s)
        if (scheduledWorkDays <= 1) startMd
        else startMd + "~" + DateTimeUtils.formatMonthDay(s + (scheduledWorkDays - 1).toLong() * DateTimeUtils.DAY_MS)
    }

    fun toggle(p: GalleryPhoto) {
        selected = when {
            p.uri in selectedUris -> selected.filterNot { it.uri == p.uri }
            selected.size >= maxPick -> selected   // 자리 초과면 무시(아래 하단바가 이유를 말한다)
            else -> selected + p
        }
    }

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(AppTheme.colors.bg)) {
            // 상단바 (상태바 밑으로)
            Row(
                Modifier.fillMaxWidth().background(AppTheme.colors.surface)
                    .padding(top = statusTop).padding(16.dp, 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(44.dp).clip(CircleShape).clickable { onDismiss() }, contentAlignment = Alignment.Center) {
                    Text("✕", fontSize = 22.sp, color = TossTextSecondary)
                }
                Spacer(Modifier.width(4.dp))
                Text("현장 사진 올리기", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(AppTheme.colors.line))
            // 맥락 배너
            val who = customerName?.takeIf { it.isNotBlank() }?.let { "$it 고객님" } ?: "현장"
            val where = AddressExtractor.tidyAddress(address).takeIf { it.isNotBlank() }
            Column(Modifier.fillMaxWidth().background(AppTheme.colors.surface).padding(16.dp, 0.dp, 16.dp, 12.dp)) {
                Text(who, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary)
                if (where != null) Text(where, fontSize = 13.5.sp, color = TossTextSecondary)
            }
            // 날짜 토글
            if (workRange != null) {
                // 날짜 토글 — 회색 알약 박스 안 세그먼트 (프로토 .seg: 눌린 쪽만 흰 칩으로 떠오름)
                Row(Modifier.fillMaxWidth().background(AppTheme.colors.surface).padding(16.dp, 6.dp, 16.dp, 10.dp)) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(AppTheme.colors.surfaceMuted).padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SegButton("📅 시공한 날만 ($workDateLabel)", on = dayMode, modifier = Modifier.weight(1f)) { dayMode = true }
                        SegButton("전체 보기", on = !dayMode, modifier = Modifier.weight(1f)) { dayMode = false }
                    }
                }
                if (dayMode) {
                    val tip = if (scheduledWorkDays > 1)
                        "💡 시공 기간($workDateLabel)에 찍은 사진을 모았어요 — 며칠 치 안 뒤져도 돼요."
                    else "💡 $workDateLabel 시공한 날 찍은 사진만 모아봤어요 — 며칠 치 안 뒤져도 돼요."
                    Text(tip, fontSize = 13.sp, color = TossTextSecondary,
                        modifier = Modifier.background(AppTheme.colors.surface).fillMaxWidth().padding(16.dp, 0.dp, 16.dp, 10.dp))
                }
            } else {
                Text(
                    "시공일이 아직 없어요 — 전체 사진에서 골라주세요.",
                    fontSize = 13.sp, color = TossTextSecondary,
                    modifier = Modifier.background(AppTheme.colors.surface).fillMaxWidth().padding(16.dp, 0.dp, 16.dp, 10.dp)
                )
            }
            // 🔒 34+ 부분 허용 안내 — '전체 보기'가 폰 전체가 아니라 허용한 사진만이라는 걸 알린다. (코덱스 P1-2)
            if (partial) PartialAccessBar {
                morePhotosLauncher.launch(PermissionHelper.galleryImagePermissions())
            }

            Box(Modifier.weight(1f).fillMaxWidth().background(AppTheme.colors.surface)) {
                when {
                    loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    sections.all { it.photos.isEmpty() } -> EmptyPhotos(dayMode, partial) {
                        morePhotosLauncher.launch(PermissionHelper.galleryImagePermissions())
                    }
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
                                PhotoTile(photo = p, selected = p.uri in selectedUris, onClick = { toggle(p) })
                            }
                        }
                    }
                }
            }

            // 하단바 — 위 구분선으로 '고정 바'임을 보이고, 네비바 위로 넉넉히 띄운다(S23U 짤림 방지).
            Box(Modifier.fillMaxWidth().height(1.dp).background(AppTheme.colors.line))
            Row(
                Modifier.fillMaxWidth().background(AppTheme.colors.surface)
                    .bottomBarClearance(extra = 16.dp).padding(16.dp, 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 몇 장 골랐나 + 한도 안내(한도에 닿으면 왜 더 안 되는지 말한다 — 코덱스 P2-6)
                Column {
                    Text("${selected.size}장 선택됨", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TossTextSecondary)
                    if (selected.size >= maxPick) Text("이 현장은 ${maxPick}장까지예요", fontSize = 11.5.sp, color = TossTextTertiary)
                }
                Box(Modifier.weight(1f))
                val can = selected.isNotEmpty()
                Box(
                    Modifier.clip(RoundedCornerShape(13.dp))
                        .background(if (can) AppTheme.colors.primary else AppTheme.colors.line)
                        .clickable(enabled = can) {
                            // 모드와 무관하게 **고른 것 전부**를 최신순으로 넘긴다(P1-1).
                            onPicked(selected.sortedByDescending { it.takenMs }.map { it.uri })
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

/** 날짜별 묶음(최신순). 시공 기간에 든 날은 '시공한 날' 표시. 여러 날 시공도 날짜별로 나뉜다(코덱스 P1-3). */
private fun buildSections(all: List<GalleryPhoto>, workRange: LongRange?): List<PhotoSection> {
    val byDay = all.groupBy { DateTimeUtils.startOfDay(it.takenMs) }
    return byDay.entries.sortedByDescending { it.key }.map { (day, photos) ->
        val isWork = workRange != null && day in workRange
        // 날짜 머리 = "11월 11일 (수)"(올해 아니면 연도까지) — 프로토 표기 복원(코덱스 P3). formatScheduledDate 재사용.
        val label = DateTimeUtils.formatScheduledDate(day) + if (isWork) " · 시공한 날" else ""
        PhotoSection(label, isWork, photos)
    }
}

/** 34+ 부분 허용 안내 띠 — '허용한 사진만 보여요' + '사진 더 고르기'. */
@Composable
private fun PartialAccessBar(onMore: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(AppTheme.colors.cautionBg).padding(16.dp, 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("허용한 사진만 보여요", fontSize = 13.sp, fontWeight = FontWeight.Bold,
            color = AppTheme.colors.cautionText, modifier = Modifier.weight(1f))
        Box(
            Modifier.clip(RoundedCornerShape(9.dp)).background(AppTheme.colors.caution)
                .clickable { onMore() }.padding(horizontal = 12.dp, vertical = 7.dp)
        ) { Text("사진 더 고르기", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color.White) }
    }
}

/** 빈 화면 — 시공한날/전체·부분허용을 구분해 안내 + 바로 행동 버튼. (코덱스 P2-8) */
@Composable
private fun EmptyPhotos(dayMode: Boolean, partial: Boolean, onMore: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val msg = when {
            partial && dayMode -> "허용한 사진 중엔 시공한 날 사진이 없어요."
            partial -> "허용한 사진이 없어요."
            dayMode -> "시공한 날 찍은 사진이 없어요."
            else -> "사진이 없어요."
        }
        Text(msg, fontSize = 14.sp, color = TossTextSecondary, textAlign = TextAlign.Center)
        if (partial) {
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier.clip(RoundedCornerShape(11.dp)).background(AppTheme.colors.primary)
                    .clickable { onMore() }.padding(horizontal = 18.dp, vertical = 11.dp)
            ) { Text("사진 더 고르기", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White) }
        }
    }
}

@Composable
private fun SegButton(text: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .then(if (on) Modifier.shadow(2.dp, RoundedCornerShape(8.dp)) else Modifier)
            .clip(RoundedCornerShape(8.dp))
            .background(if (on) AppTheme.colors.surface else Color.Transparent)
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
        Box(Modifier.size(7.dp).clip(CircleShape)
            .background(if (isWork) AppTheme.colors.primary else AppTheme.colors.line))
        Text("$label · ${count}장", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TossTextSecondary)
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
        // 좌하단 찍은 시각 — 「오후 4시 20분」 꼴(공용 DateTimeUtils.timeLabel). 「16:20」은 영상 길이로 보여 바꿈.
        Text(
            DateTimeUtils.timeLabel(photo.takenMs),
            fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White,
            modifier = Modifier.align(Alignment.BottomStart).padding(5.dp)
                .clip(RoundedCornerShape(5.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 6.dp, vertical = 2.dp)
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
