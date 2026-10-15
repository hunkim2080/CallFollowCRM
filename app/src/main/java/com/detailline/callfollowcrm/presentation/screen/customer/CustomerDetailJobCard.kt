package com.detailline.callfollowcrm.presentation.screen.customer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import com.detailline.callfollowcrm.data.local.entity.CustomerNoteEntity
import com.detailline.callfollowcrm.data.local.entity.IntakeEventEntity
import com.detailline.callfollowcrm.data.local.entity.IssuedDocEntity
import com.detailline.callfollowcrm.data.local.entity.JobEntity
import com.detailline.callfollowcrm.ai.SitePhotoServerRepository
import com.detailline.callfollowcrm.presentation.component.TossCard
import com.detailline.callfollowcrm.presentation.theme.AppShape
import com.detailline.callfollowcrm.presentation.theme.AppTheme
import com.detailline.callfollowcrm.presentation.theme.AppType
import com.detailline.callfollowcrm.presentation.theme.TossBlue
import com.detailline.callfollowcrm.presentation.theme.TossBlueDark
import com.detailline.callfollowcrm.presentation.theme.TossBlueSoft
import com.detailline.callfollowcrm.presentation.theme.TossGrayBg
import com.detailline.callfollowcrm.presentation.theme.TossSuccess
import com.detailline.callfollowcrm.presentation.theme.TossTextPrimary
import com.detailline.callfollowcrm.presentation.theme.TossTextSecondary
import com.detailline.callfollowcrm.presentation.theme.TossTextTertiary
import com.detailline.callfollowcrm.util.DateTimeUtils
import com.detailline.callfollowcrm.util.PhoneKey
import com.detailline.callfollowcrm.util.copyToClip
import kotlinx.coroutines.launch

/**
 * 🏗️ **한 건(件) = 한 카드.** 고객 상세의 시공 건 카드 — 프로토 artifact/32G2BkhhFkdE9J5Wj8u4JC 의 `.job`.
 *   (2026-10-15 사장님: '현재 카드'가 완료 건 탭보다 아래로 밀린다 → 작은 탭 줄을 없애고 **건마다 자기 카드**)
 *
 *   · 머리(`.jhead`) = ● 색점 · N차 · 날짜/시간 · D-day 칩 · [진행 중 / 완료] 딱지 — [JobCardHead]
 *     완료·취소한 건([jobFolded])은 **접힌 줄**로 시작(날짜 회색 · 「60만원 · 완납」 · ▾). 누르면 그 자리에서 펼친다.
 *   · 몸통(`.jbody`) = 주소·길찾기·예약·잔금·현장 메모·현장 사진·foot(총·계약금 / 협업 / 접수서 / ⋯더보기)
 *     — CustomerDetailScreen.kt 의 「일정 · 정산」 한 건 카드 본문을 **그대로** 옮긴 것(§12-F 잎 떼기, 2026-10-15).
 *     저장 경로(setJob* · setJobSchedule · addSitePhotos · Outbox · IssuedDocList …)는 전과 같다.
 *
 *   다이얼로그(날짜·금액·주소·A/S·예약 취소·건 삭제·사진 크게 보기)는 **화면에 한 벌**(CustomerDetailScreen)이다 —
 *   카드는 [JobCardHost] 의 콜백으로 「어느 건(jobId)인지」만 넘겨 연다. 카드마다 창을 또 만들지 않는다(§12).
 */
internal class JobCardHost(
    val viewModel: CustomerDetailViewModel,
    val container: AppContainer,
    val customer: CustomerEntity,
    val allJobs: List<JobEntity>,
    /** 고객 카드 날짜와 같은 날의 건(대표 건) id. 옛 사진(jobId 없음)은 이 건에 붙여 보여준다. */
    val repJobId: Long?,
    val repDay: Long?,
    /** 맨 처음 건(1차) id — 날짜 없는/안 맞는 떠돌이 문서(견적서 등)를 이 건 카드에 모은다. (2026-10-15 사장님) null = 1차 없음. */
    val earliestJobId: Long?,
    /** 시공이 둘 이상이면 "N차" 꼬리표를 붙인다. 하나뿐이면 안 붙인다(2026-09-18 프로토 — 95% 손님은 한 번). */
    val showNth: Boolean,
    val issuedDocs: List<IssuedDocEntity>,
    val intakeByToken: Map<String, IntakeEventEntity>,
    /** 협업 기록(prefs)은 비반응형 — 공유·해제 뒤 이 값이 바뀌어야 다시 읽는다. */
    val collabRefresh: Int,
    val onCollabRefresh: () -> Unit,
    /** 날짜 고치기 창 — (jobId, 지금 예약일, 기간, 시간). jobId null = 고객(대표) 경로. */
    val onEditDate: (jobId: Long?, start: Long?, days: Int, minutes: Int?) -> Unit,
    /** 금액 창 — (jobId, "total" | "deposit"). */
    val onEditAmount: (jobId: Long?, field: String) -> Unit,
    val onEditAddress: (jobId: Long?) -> Unit,
    val onAsDone: () -> Unit,
    val onAsPending: () -> Unit,
    val onAsPicker: () -> Unit,
    val onCancelBooking: () -> Unit,
    val onDeleteJob: (JobEntity?) -> Unit,
    val onEditNote: (CustomerNoteEntity) -> Unit,
    val onOpenPhoto: (uri: android.net.Uri, photoId: Long) -> Unit,
    val onOpenBitmap: (android.graphics.Bitmap) -> Unit,
    val onPicksToDelete: (Pair<Set<Long>, Set<Long>>) -> Unit,
    val onOpenQuoteDoc: (com.detailline.callfollowcrm.presentation.screen.chat.QuoteDocData) -> Unit,
    val onOpenIntakeDoc: (IssuedDocEntity) -> Unit,
    val onEditIssued: (issuedId: Long) -> Unit,
    val onRequestDeleteDoc: (IssuedDocEntity) -> Unit,
    val onOpenChat: () -> Unit
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun JobDetailCard(
    host: JobCardHost,
    /** 이 카드의 건. null = 건이 하나도 없는 고객(상담 단계·옛 데이터) → 고객 카드(c) 값으로 그린다(옛 editJobId=null 경로). */
    job: JobEntity?,
    /** 팀원·서버 현장 사진/메모는 **고객 단위**라 대표(기본) 건 카드에만 넘긴다 — 나머지 카드엔 빈 목록. */
    teamPhotos: List<SitePhotoServerRepository.RemotePhoto>,
    teamNotes: List<SitePhotoServerRepository.RemoteNote>,
    /** 대표(기본) 건 카드인가 — 고객 단위인 것(A/S 줄·서버 사진 되살리기)은 여기에만 그린다(전과 같은 거동). */
    isPrimary: Boolean
) {
    val c = host.customer
    val viewModel = host.viewModel
    val issuedDocs = host.issuedDocs
    val intakeByToken = host.intakeByToken
    val ctx = LocalContext.current
    val jobId: Long? = job?.id
    val folded = job != null && jobFolded(job)
    // 완료·취소한 건은 접힌 채로 시작. 누르면 그 자리에서 같은 상세가 펼쳐진다(프로토 `.job.folded`).
    var expanded by remember(jobId) { mutableStateOf(!folded) }
    // 차수 꼬리표 "2차 " — 시공이 둘 이상일 때만. 메모·사진 제목이 같이 쓴다. 셈은 jobNthOf(JobOrder) 한 곳. (2026-09-19 사장님)
    val jobNthPrefix = if (host.showNth && job != null) "${jobNthOf(host.allJobs, job)}차 " else ""
    // 머리의 "N차" — 취소한 건은 차수를 안 준다(안 한 시공에 번호를 붙이면 뒤가 다 밀린다. 2026-09-18).
    val nthLabel: String? =
        if (job != null && host.showNth && !jobCancelled(job)) "${jobNthOf(host.allJobs, job)}차" else null
    // 🔴 주소도 **그 건(job)** 것 — 2차를 보면 2차 주소. 없으면 고객 주소로 폴백. (2026-10-07)
    val displayAddr = (job?.address?.takeIf { it.isNotBlank() } ?: c.address)?.takeIf { it.isNotBlank() }
    // 어느 건이든 **그 건의 값**을 보여주고, 그 건에 쓴다. (2026-09-18 확정 프로토 ④)
    //   editJobId = null 이면 '지금 건'(고객 카드 경로), 아니면 그 건에 직접 쓴다.
    val editJobId: Long? = jobId
    val cShown = job?.let { com.detailline.callfollowcrm.domain.job.JobCardRow.rowOf(c, it) } ?: c
    val scheduled = cShown.scheduledWorkDate
    val totalWon = cShown.totalAmount ?: 0L
    val depositWon = cShown.depositAmount ?: 0L
    // 총금액 또는 계약금 중 하나라도 입력되면 정산 영역을 펼친다(계약금만 따로 넣는 경우 포함).
    val hasAmount = totalWon > 0L || depositWon > 0L
    // 잔금·완납은 정산 단일 출처(SettlementCalc)로 — 화면마다 잔금이 다르던 버그 통일. (2026-07-30)
    val settle = com.detailline.callfollowcrm.domain.settlement.SettlementCalc.rowOf(cShown)
    val balanceWon = settle.balanceAmount
    val depPaid = cShown.depositPaidAt != null
    val balPaid = cShown.balancePaidAt != null
    val allPaid = settle.isPaidOff
    // 머리에 날짜가 있고 눌러 고칠 수 있으면(진행 중인 건) 몸통의 「시공 예약」 줄은 안 그린다 — 같은 말 두 번 X.
    //   접힌 건(완료)은 머리 날짜가 펼침 손잡이라 몸통 줄로 고친다. 날짜 없는 건은 몸통 줄이 「눌러서 설정」 입구.
    val headHasDate = job != null && !folded && scheduled != null

    TossCard(onClick = if (folded && !expanded) ({ expanded = true }) else null) {
        Column {
            if (job != null) {
                JobCardHead(
                    job = job, nthLabel = nthLabel, folded = folded, expanded = expanded,
                    onToggle = { expanded = !expanded },
                    onEditDate = {
                        host.onEditDate(
                            jobId, job.scheduledWorkDate,
                            job.scheduledWorkDays.coerceAtLeast(1), job.scheduledWorkMinutes
                        )
                    }
                )
            } else {
                // 건이 없는 고객만 옛 「일정 · 정산」 머리 그대로.
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    CdTitleIcon(Icons.Filled.Payments, "amber")
                    Spacer(Modifier.width(8.dp))
                    Text("일정 · 정산", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TossTextTertiary)
                }
            }
            if (expanded) {
            Spacer(Modifier.height(12.dp))
            // ── ↓ 여기부터 몸통 — CustomerDetailScreen.kt 「일정 · 정산」 카드 본문 그대로(2026-10-15 이동) ──
                        // 📍 주소 — **이 건** 것(위 주소 카드에서 합쳐 들어옴). 건이 있을 때만.
                        if (job != null && !displayAddr.isNullOrBlank()) {
                            // 📍·✏️ 이모지 → 벡터. 「다른 건과 주소 다름」 딱지는 뺐다 — 스택 행 둘째 줄에 현장 라벨이
                            //   들어가면서 그 역할을 행이 하기 때문(§12-K 같은 정보 두 모양). (2026-10-15 Fable §4-1·4)
                            androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.Top) {
                                androidx.compose.material3.Icon(
                                    Icons.Filled.Place, null, tint = TossTextTertiary,
                                    modifier = Modifier.padding(top = 3.dp).size(14.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    displayAddr, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TossTextPrimary, lineHeight = 20.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("복사", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TossBlue,
                                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { ctx.copyToClip("주소", displayAddr) }.padding(horizontal = 8.dp, vertical = 4.dp))
                                androidx.compose.material3.Icon(
                                    Icons.Default.Edit, "주소 수정", tint = TossTextTertiary,
                                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { host.onEditAddress(jobId) }
                                        .padding(horizontal = 4.dp, vertical = 4.dp).size(22.dp).padding(4.dp)
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            androidx.compose.foundation.layout.Box(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(TossBlue)
                                    .clickable { startNavToAddress(ctx, displayAddr) }.padding(vertical = 12.dp),
                                contentAlignment = androidx.compose.ui.Alignment.Center
                            ) {
                                androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    androidx.compose.material3.Icon(Icons.Default.Navigation, null, tint = Color.White, modifier = Modifier.size(17.dp))
                                    Spacer(Modifier.width(7.dp))
                                    Text("길찾기 시작", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                        }
                        // 화면을 둘로 가르지 않는다 — 표가 이미 "아직 예약 안 됨" 같은 빈 값을 말할 줄 안다.
                        //   전엔 예약·금액이 하나도 없으면 버튼만 있는 **딴 화면**이 떴다. (2026-09-22 사장님)
                        if (scheduled == null && !hasAmount) {
                            // 2026-06-07 사장님 통점: 통화로 다 정해졌는데 일정 등록하려면 견적서 보내기밖에 없었음.
                            //   그 안내는 아무것도 없을 때만 한 줄로 남긴다.
                            Text(
                                "통화로 정해졌으면 여기서 바로 등록하세요. 고객에게 또 입력시키지 않아도 돼요.",
                                fontSize = 13.sp, color = TossTextSecondary, lineHeight = 20.sp
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                        if (!headHasDate) CdKv(
                                "시공 예약",
                                // 여러 날 시공이면 기간까지 — 전엔 시작 날짜만 보여 "3일 중 1일차"인 걸 알 수 없었다. (2026-09-15 사장님)
                                //   날짜는 짧게("9/16(수)") — 올해 일이면 연도는 정보가 아니다. (2026-09-20 사장님)
                                if (scheduled != null)
                                    DateTimeUtils.formatShortKoreanDate(scheduled) +
                                        (c.scheduledWorkMinutes?.let { " " + DateTimeUtils.formatWorkMinutes(it) } ?: "") +
                                        DateTimeUtils.workPeriodSuffix(scheduled, c.scheduledWorkDays)
                                else "아직 예약 안 됨 · 눌러서 설정",
                                valueColor = if (scheduled != null) AppTheme.colors.text else TossTextTertiary,
                                trailing = if (scheduled != null) Icons.Default.Edit else Icons.Default.Add,
                                onClick = {
                                    // 🔴 **그 건**을 고친다 — 돈처럼 날짜도 건별로. (2026-10-14 사장님: 2차 날짜 등록 안 됨)
                                    //   jobId 가 null(아직 건 없음)이면 아래 창이 고객(대표) 경로로 폴백.
                                    host.onEditDate(jobId, job?.scheduledWorkDate, (job?.scheduledWorkDays ?: 1).coerceAtLeast(1), job?.scheduledWorkMinutes)
                                }
                            )
                            // 🔧 **A/S 해줘야 하는 손님** — 날짜를 못 잡았어도 여기 뜬다. (2026-09-27 사장님)
                            //   "일정이 꽉 차 있어서 못 잡는 거야. 그래서 as 대기 고객으로 명단을 올려두고"
                            if (isPrimary && c.asPendingAt != null) {
                                CdKv(
                                    "A/S",
                                    if (c.asScheduledDate != null) "예약 잡힘 · 무료"
                                    else "해드릴 곳 · 날짜 미정",
                                    valueColor = AppTheme.colors.cautionText,
                                    trailing = Icons.Default.Check,
                                    onClick = { host.onAsDone() }
                                )
                            }
                            // 🔧 A/S 예약 — 시공과 별개, 무료. (2026-08-01 사장님)
                            //   **잡혀 있을 때만** 줄로 보여준다. A/S 는 가끔 있는 일인데
                            //   늘 「아직 없음」이 한 줄을 먹고 있었다 — 정보가 아니라 빈 줄이다.
                            //   안 잡혔을 땐 카드 맨 아래 작은 「A/S 잡기」로 물러난다. (2026-09-26 사장님)
                            if (isPrimary && c.asScheduledDate != null) {
                                CdKv(
                                    "A/S 예약",
                                    DateTimeUtils.formatShortKoreanDate(c.asScheduledDate!!) +
                                        (if (c.asScheduledDays > 1) " · ${c.asScheduledDays}일" else "") + " · 무료",
                                    valueColor = AppTheme.colors.text,
                                    trailing = Icons.Default.Edit,
                                    onClick = { host.onAsPicker() }
                                )
                            }
                            // 💰 돈은 **잔금 한 줄 크게**(프로토 `.moneyline` 18px) — 총·계약금은 카드 맨 아래 foot 에 작게.
                            //   큰 파란 버튼은 길찾기 **하나만**. 받음 확인은 잔금 옆 **작은 회색 칩**으로.
                            //   (2026-10-15 사장님 결정 — Fable §4-8 / §5-1 (가). 저장 경로는 전과 같다: setJob*Paid / set*Paid)
                            // ↩️ **완납 되돌리기는 「전액 완납」 바로 옆에** — 전엔 ⋯더보기 안에 숨어 있어 잘못 눌러도 돌아갈 길이 안 보였다.
                            //   (2026-10-15 사장님 "전액완납 눌렀는데 잘못 눌렀을 땐 돌아갈 수 없는 버그")
                            //   💰 돈은 물어보고 뒤집는다(2026-09-27 사장님) — 확인창은 그대로, 자리만 옮겼다. 창은 건마다 하나(remember(jobId)).
                            var unpaidConfirm by remember(jobId) { mutableStateOf(false) }
                            if (unpaidConfirm) {
                                androidx.compose.material3.AlertDialog(
                                    tonalElevation = 0.dp,
                                    onDismissRequest = { unpaidConfirm = false },
                                    title = {
                                        Text("잔금을 아직 못 받으신 걸로 되돌릴까요?",
                                            fontWeight = FontWeight.Bold, color = TossTextPrimary)
                                    },
                                    text = {
                                        Text(
                                            "이 손님이 상담함 [잔금 대기] 로 다시 올라오고, " +
                                                "정산에서도 못 받은 돈으로 잡혀요.",
                                            style = AppType.body, color = TossTextSecondary
                                        )
                                    },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            unpaidConfirm = false
                                            if (editJobId != null) viewModel.setJobBalancePaid(editJobId, false)
                                            else viewModel.setBalancePaid(false)
                                        }) {
                                            Text("되돌릴게요", color = TossBlue, fontWeight = FontWeight.Bold)
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { unpaidConfirm = false }) {
                                            Text("그냥 둘게요", color = TossTextSecondary)
                                        }
                                    },
                                    containerColor = Color.White
                                )
                            }
                            androidx.compose.foundation.layout.Row(
                                Modifier.fillMaxWidth().padding(vertical = 9.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Text(if (hasAmount) "잔금" else "금액", fontSize = 13.sp, color = TossTextSecondary)
                                Spacer(Modifier.weight(1f))
                                if (!hasAmount) {
                                    // 프로토 "아직 입력 안 함 ＋" — 탭하면 총금액 입력(옛 [총금액 입력] 버튼과 같은 창).
                                    Text(
                                        "아직 입력 안 함 ＋", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TossTextTertiary,
                                        modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                            .clickable { host.onEditAmount(editJobId, "total") }
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                } else {
                                    Text(
                                        if (allPaid) "전액 완납" else manwonLabel(balanceWon) + " 남음",
                                        fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp,
                                        color = if (allPaid) AppTheme.colors.doneText else AppTheme.colors.unpaidText
                                    )
                                    if (allPaid) {
                                        Spacer(Modifier.width(9.dp))
                                        CdUndoChip("완납 취소", danger = false) { unpaidConfirm = true }
                                    } else {
                                        Spacer(Modifier.width(9.dp))
                                        // 계약금이 아직이면 계약금부터, 아니면 잔금(또는 전액) — 옛 세 버튼의 순서 그대로.
                                        val paidLabel = if (depositWon > 0L && !depPaid) "계약금 받았어요" else "받았어요"
                                        CdUndoChip(paidLabel, danger = false, filled = true) {
                                            when {
                                                depositWon > 0L && !depPaid ->
                                                    if (editJobId != null) viewModel.setJobDepositPaid(editJobId, true)
                                                    else viewModel.setDepositPaid(true)
                                                depositWon > 0L ->
                                                    if (editJobId != null) viewModel.setJobBalancePaid(editJobId, true)
                                                    else {
                                                        if (c.balanceAmount == null && balanceWon > 0L) viewModel.setBalanceAmount(balanceWon)
                                                        viewModel.setBalancePaid(true)
                                                    }
                                                else ->
                                                    if (editJobId != null) viewModel.setJobBalancePaid(editJobId, true)
                                                    else {
                                                        if (c.balanceAmount == null && totalWon > 0L) viewModel.setBalanceAmount(totalWon)
                                                        viewModel.setBalancePaid(true)
                                                    }
                                            }
                                        }
                                    }
                                }
                            }
            // ↓ 현장 메모·사진도 **같은 한 건 카드 안**. Column·TossCard·run 닫기는 사진 뒤에서. (2026-10-15 사장님 "한 건=한 카드")

            // (입금 카드는 위 "일정 · 정산" 카드로 통합됨 — 2026-06-02 사장님 결정.)
            // (메모 카드는 프로토 순서대로 "일정 · 정산" 위로 이동 — 2026-06-03.)

            // 4. 옛 "문자" 카드 제거 (2026-05-28 사장님 보고):
            //    "📩 주고받은 문자" 접이식 섹션이 대화요약 아래로 올라옴 → 여기 중복.
            //    MessageRow/MessageRowView 는 dead code 가능성 → 다른 사용처 없으면 추후 정리.

            // 5·6. (제거) "통화 기록" + "에이닷 통화 요약" 카드 — 프로토 openCustomer 엔 없음.
            //   2026-06-04 사장님 결정 "프로토 100%". 통화기록/녹음/에이닷 요약 데이터 수집(backfill·
            //   AdotSummaryImporter)은 그대로 동작 — 고객정보 화면에서 카드로 노출만 안 함.

            // (이 고객 문서(차수 미지정)는 per-건 카드 밖 — 사진 뒤로 옮김. 고객 단위라 한 건 카드에 안 넣는다. 2026-10-15)

            // 📍 이 현장 메모 — **그 건에서만.** 손님 메모(👤)와 **같은 얼굴**(쪽지)로. (2026-09-27 사장님)
            //   "메모는 두 곳. 👤 이 손님 메모(현장이 바뀌어도 그대로인 것)와
            //    📍 이 현장 메모(그 건에서만)."
            //   둘 다 NoteList 하나를 쓴다 — 따로 만들면 또 어긋난다.
            if (jobId != null) {
                val jid = jobId
                val jobNotes by remember(jid) { viewModel.jobNotes(jid) }
                    .collectAsState(initial = emptyList())
                var memoJobExpanded by remember(jobId) { mutableStateOf(false) }
                CdCardDivider()
                // 고객 메모처럼 접기 — ② 공용 접힘 줄(CdFoldRow). (2026-10-15 사장님 "현장 메모는 고객메모처럼 접어두자")
                CdFoldRow(
                    Icons.Filled.EditNote,
                    if (jobNthPrefix.isNotEmpty()) "${jobNthPrefix}현장 메모" else "이 현장 메모",
                    jobNotes.size, memoJobExpanded,
                    emptyAction = if (jobNotes.isEmpty()) "＋ 적기" else null
                ) { memoJobExpanded = !memoJobExpanded }
                if (memoJobExpanded) Column {
                        Spacer(Modifier.height(4.dp))
                        Text("이 현장에서만 · 주차, 열쇠, 자재", fontSize = 11.5.sp, color = TossTextTertiary)
                        Spacer(Modifier.height(8.dp))
                        com.detailline.callfollowcrm.presentation.component.NoteList(
                            notes = jobNotes,
                            onAdd = { viewModel.addJobNote(jid, it) },
                            onEdit = { host.onEditNote(it) },
                            onTogglePin = { viewModel.togglePinNote(it.id) },
                            onDelete = { viewModel.deleteNote(it.id) },
                            placeholder = "이 현장에서 기억할 것"
                        )
                    }
            }

            // 6.5 현장 사진 (프로토 openCustomer) — 사장님이 갤러리에서 골라 올림(로컬 저장). 2026-06-04 활성화.
            //   ※ 팀원↔사장님 공유는 서버(team_site_photos) 보강 후 별도 연동 — docs/SERVER_HANDOFF 참조.
            val allSitePhotos by viewModel.sitePhotos.collectAsState()
            // 🔴 사진도 **그 건 것만.** 1차 사진이 2차 탭에 보이면 안 된다.
            //   (2026-09-18 사장님: "현장사진도 1차 2차 개별로 들어가야해")
            //   어느 건인지 안 붙은 옛 사진(jobId=null)은 **대표 건**에 붙여 보여준다 — 안 그러면
            //   지금까지 올린 사진이 통째로 안 보이게 된다.
            val sitePhotos = remember(allSitePhotos, jobId, host.repJobId) {
                allSitePhotos.filter { p ->
                    p.jobId == jobId || (p.jobId == null && jobId == host.repJobId)
                }
            }
            // 카톡식 사진 첨부 시트(아래→위) — 기존 시스템 "내 파일" 피커는 권한 거부/구형 fallback 으로만 유지. (2026-06-11)
            var showPhotoPicker by remember { mutableStateOf(false) }
            // 사진 첨부 = 시스템 Photo Picker(권한 없음, Play 정책 + 사진 우선 화면).
            //   기존 GetMultipleContents(ACTION_GET_CONTENT)는 삼성 "항목 선택" 폴더 탐색기라 연세 있으신 분들이 헤맴
            //   → PickMultipleVisualMedia(깔끔한 사진 그리드)로 교체. (2026-07-06 사장님)
            val photoPicker = rememberLauncherForActivityResult(
                // 2026-08-24 사장님: 본인 현장 사진도 한 번에 10 → 20장 (협업과 통일, 총량 sitePhotoMax=20).
                ActivityResultContracts.PickMultipleVisualMedia(20)
            ) { uris -> if (uris.isNotEmpty()) viewModel.addSitePhotos(uris, jobId) }
            val launchPhotoPicker = { showPhotoPicker = true }
            val photoMax = viewModel.sitePhotoMax
            val photoTotal = sitePhotos.size + teamPhotos.size
            // 📸 한도(20장)는 **내 사진만** — 협업 사장 사진은 그 사장의 20장(각 사장마다 따로). (2026-10-15 사장님)
            val photoMine = sitePhotos.size
            // 🏅 **대표 사진** — 그 현장에 제일 먼저 올린 사진. 인증샷에 이게 들어간다. (2026-09-25 사장님)
            //   목록은 최신이 먼저라 대표는 보통 **맨 끝 칸**에 있다 → 위치로는 알 수 없으니 딱지를 붙인다.
            // 🔒 인증샷과 **같은 규칙**으로 고른다(두 벌이면 서로 다른 사진을 가리킨다 — 2026-09-25 점검).
            //   observe 는 최신순이라, 규칙이 요구하는 '올린 순서'로 뒤집어 넘긴다.
            val repCustomerId = c.id
            val repPhotoId = remember(sitePhotos, jobId, repCustomerId) {
                com.detailline.callfollowcrm.data.repository.SitePhotoRepository.representativeOf(
                    sitePhotos.sortedBy { it.createdAt }, repCustomerId, jobId
                )?.id
            }
            LaunchedEffect(showPhotoPicker) {
                if (showPhotoPicker) {
                    showPhotoPicker = false
                    photoPicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            }
            // 사진이 많아도 카드가 화면을 통째로 먹지 않게 — 두 줄까지만 보이고 나머지는 접는다.
            var photosExpanded by remember(jobId) { mutableStateOf(false) }
            // [고르기] 를 눌렀을 때만 동그라미가 뜬다. 평소엔 사진을 눌러 크게 본다. (2026-09-22 사장님)
            var pickMode by remember(jobId) { mutableStateOf(false) }
            var pickedMine by remember(jobId) { mutableStateOf(setOf<Long>()) }
            var pickedTeam by remember(jobId) { mutableStateOf(setOf<Long>()) }
            val pickedCount = pickedMine.size + pickedTeam.size
            fun exitPickMode() {
                pickMode = false; pickedMine = emptySet(); pickedTeam = emptySet()
            }
            // 🛟 **서버에만 남은 내 사진** — 폰을 재설치하면 이렇게 된다. (2026-09-28 사장님)
            //   사장님: "그럼 직원폰에 그대로 보이게 해줄수 있는거아니야?"
            //   맞다 — 서버에 살아 있으니 내려받으면 된다. 보기만 하면 반쪽이라
            //   **되살려서** 내 기록·영상·백업에도 다시 들어가게 한다.
            val restorableAll by viewModel.restorable.collectAsState()
            // 서버에만 남은 사진은 고객 단위 — 대표 건 카드에서만 되살리기를 보여준다.
            val restorable = if (isPrimary) restorableAll else emptyList()
            if (restorable.isNotEmpty()) {
                CdCardDivider()
                Column(Modifier.padding(2.dp)) {
                        Text("서버에 이 현장 사진 ${restorable.size}장이 있어요",
                            style = AppType.headline, color = TossTextPrimary)
                        Spacer(Modifier.height(4.dp))
                        Text("폰을 다시 깔면서 사진이 빠졌어요. 되살리면 내 기록·영상에도 다시 들어가요.",
                            style = AppType.caption, color = TossTextTertiary)
                        Spacer(Modifier.height(11.dp))
                        Box(
                            Modifier.fillMaxWidth().clip(AppShape.md).background(TossBlue)
                                .clickable { viewModel.restorePhotosFromServer() }
                                .padding(vertical = 13.dp),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            Text("사진 ${restorable.size}장 되살리기",
                                style = AppType.label, color = Color.White)
                        }
                    }
                Spacer(Modifier.height(12.dp))
            }
            CdCardDivider()
            Column {
                    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        CdTitleIcon(Icons.Filled.PhotoCamera, "blue")
                        Spacer(Modifier.width(8.dp))
                        Text(
                            // 사진도 그 건 것만 보이므로 제목에 차수를 붙인다. (2026-09-19 사장님)
                            if (pickMode) "지울 사진 고르는 중"
                            // 「/20」은 뺀다 — 한도는 사장마다 따로라 내 것+협업 것을 합친 수에 /20 을 붙이면 틀리다. (2026-10-15 사장님)
                            else (if (jobNthPrefix.isNotEmpty()) "${jobNthPrefix}현장 사진" else "현장 사진") +
                                (if (photoTotal == 0) "" else " ${photoTotal}장"),
                            fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TossTextTertiary,
                            modifier = Modifier.weight(1f)
                        )
                        // 버튼은 **제목 줄에**. 전엔 사진이 늘수록 아래로 도망갔고, 20장이 차면
                        //   [올리기] 타일이 아예 사라져서 왜 못 올리는지도 안 보였다. (2026-09-22 사장님)
                        if (pickMode) {
                            PhotoHeadBtn("취소", filled = false) { exitPickMode() }
                        } else {
                            // 「사진 골라 지우기」는 foot 의 ⋯ 더보기 안으로 — 가끔 하는 일을 머리 줄에 늘 두지 않는다. (2026-10-15 Fable §4-7)
                            //   이름은 그대로 「골라 지우기」(2026-09-30 사장님: 「고르기」만으론 뭘 고르는지 모르고,
                            //   「지우기」만이면 다 지워질 것 같다). 색도 중립 그대로 — 이 버튼은 아무것도 안 지운다.
                            // 올리기는 아래 스트립 맨 앞 ＋ 타일로 (프로토 스타일). **내 사진**이 다 차면 안내만. (2026-10-15 사장님: 사장마다 20장 따로)
                            if (photoMine >= photoMax) {
                                Text(
                                    "내 사진 ${photoMax}장 다 찼어요",
                                    fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TossTextTertiary
                                )
                            }
                        }
                    }
                    // 프로토 스타일 — 컴팩트 **가로 스트립**(썸네일 + 이름표 + 점선 ＋). 긴 안내문 제거. (2026-10-15 사장님 "현장사진 UI도 프로토 스타일")
                    val cells: List<Any> = sitePhotos + teamPhotos
                    Spacer(Modifier.height(10.dp))
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // ＋ 사진 올리기 타일(점선) — 맨 앞. **내 사진**이 다 찼거나 고르는 중이면 숨김. (사장마다 20장 따로)
                        if (!pickMode && photoMine < photoMax) {
                            androidx.compose.foundation.layout.Box(
                                Modifier.size(64.dp).clip(AppShape.md)
                                    .border(1.3.dp, TossBlue.copy(alpha = 0.4f), AppShape.md)
                                    .clickable { launchPhotoPicker() },
                                contentAlignment = androidx.compose.ui.Alignment.Center
                            ) {
                                androidx.compose.material3.Icon(Icons.Default.Add, "사진 올리기", tint = TossBlue, modifier = Modifier.size(22.dp))
                            }
                        }
                        cells.forEach { cell ->
                                    androidx.compose.foundation.layout.Box(
                                        Modifier.size(64.dp).clip(AppShape.md).background(TossGrayBg)
                                    ) {
                                        val picked: Boolean
                                        when (cell) {
                                            is com.detailline.callfollowcrm.data.local.entity.SitePhotoEntity -> {
                                                picked = cell.id in pickedMine
                                                // 🖼️ 열쇠에 **파일이 바뀐 시각**을 붙인다 — 안 그러면 돌려도
                                                //   **여기 작은 사진만 옛 그림**으로 남는다. (2026-10-02 사장님)
                                                coil.compose.AsyncImage(
                                                    model = com.detailline.callfollowcrm.util.PhotoModel
                                                        .of(LocalContext.current, cell.filePath),
                                                    contentDescription = "현장 사진",
                                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                                    // 탭=크게 보기, **꾹=대표 사진으로**. (2026-10-15 사장님: 맨 먼저 올린 게 대표 되던 것 → 꾹 눌러 지정)
                                                    modifier = Modifier.fillMaxSize().combinedClickable(
                                                        onClick = {
                                                            if (pickMode) {
                                                                pickedMine =
                                                                    if (picked) pickedMine - cell.id else pickedMine + cell.id
                                                            } else {
                                                                host.onOpenPhoto(android.net.Uri.fromFile(java.io.File(cell.filePath)), cell.id)
                                                            }
                                                        },
                                                        onLongClick = {
                                                            if (!pickMode) viewModel.setRepresentativePhoto(cell.id, sitePhotos.map { it.id })
                                                        }
                                                    )
                                                )
                                                // 🏅 이게 인증샷에 들어가는 그 사진이다.
                                                if (cell.id == repPhotoId) {
                                                    androidx.compose.foundation.layout.Box(
                                                        Modifier.align(androidx.compose.ui.Alignment.TopStart).padding(5.dp)
                                                            .clip(AppShape.pill).background(TossBlue)
                                                            .padding(horizontal = 7.dp, vertical = 2.dp)
                                                    ) {
                                                        Text("대표", fontSize = 9.sp,
                                                            fontWeight = FontWeight.ExtraBold, color = Color.White)
                                                    }
                                                }
                                            }
                                            is com.detailline.callfollowcrm.ai.SitePhotoServerRepository.RemotePhoto -> {
                                                picked = cell.photoId in pickedTeam
                                                val bmp = cell.bitmap
                                                if (bmp != null) {
                                                    androidx.compose.foundation.Image(
                                                        bitmap = bmp.asImageBitmap(),
                                                        contentDescription = "현장 사진 (${cell.uploaderName})",
                                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize().clickable {
                                                            if (pickMode) {
                                                                pickedTeam =
                                                                    if (picked) pickedTeam - cell.photoId
                                                                    else pickedTeam + cell.photoId
                                                            } else host.onOpenBitmap(bmp)
                                                        }
                                                    )
                                                }
                                                // 업로더 이름표 — 팀원=파랑, 사장님=회색. 그대로 둔다.
                                                androidx.compose.foundation.layout.Box(
                                                    Modifier.align(androidx.compose.ui.Alignment.BottomStart).padding(4.dp)
                                                        .clip(AppShape.sm)
                                                        .background(if (cell.isOwner) Color.Black.copy(alpha = 0.5f) else TossBlue)
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        cell.uploaderName, fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold, color = Color.White
                                                    )
                                                }
                                            }
                                            else -> picked = false
                                        }
                                        // 고르는 중일 때만 동그라미. 평소엔 사진 위에 아무것도 안 얹는다.
                                        if (pickMode) {
                                            if (picked) {
                                                androidx.compose.foundation.layout.Box(
                                                    Modifier.fillMaxSize().background(TossBlue.copy(alpha = 0.22f))
                                                )
                                            }
                                            androidx.compose.foundation.layout.Box(
                                                Modifier.align(androidx.compose.ui.Alignment.TopEnd).padding(5.dp)
                                                    .size(19.dp).clip(CircleShape)
                                                    .background(if (picked) TossBlue else Color.Black.copy(alpha = 0.3f))
                                                    .border(2.dp, Color.White, CircleShape),
                                                contentAlignment = androidx.compose.ui.Alignment.Center
                                            ) {
                                                if (picked) androidx.compose.material3.Icon(
                                                    Icons.Default.Check, null, tint = Color.White,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
                                    }   // 사진 타일 Box 닫기
                        }   // cells.forEach 닫기
                    }   // 가로 스트립 Row 닫기
                    // 고른 장수 + [지우기] — 여러 장을 한 번에. 전엔 ✕ → 확인 을 장마다 반복해야 했다.
                    if (pickMode) {
                        Spacer(Modifier.height(11.dp))
                        androidx.compose.foundation.layout.Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text(
                                // 「고른 것만 지워요」 — 전체가 날아갈까 걱정 안 하시게. (2026-09-30 사장님)
                                if (pickedCount == 0) "지울 사진을 눌러 고르세요 · 고른 것만 지워요"
                                else "${pickedCount}장 고름 · 고른 것만 지워요",
                                fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                                color = if (pickedCount == 0) TossTextTertiary else TossTextSecondary,
                                modifier = Modifier.weight(1f)
                            )
                            if (pickedCount > 0) {
                                Text(
                                    "지우기",
                                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White,
                                    modifier = Modifier.clip(AppShape.sm).background(AppTheme.colors.unpaid)
                                        .clickable { host.onPicksToDelete(pickedMine to pickedTeam) }
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }

            // 6.52 지난 시공 이력 — **건 탭(2-0)으로 옮겼다.** (2026-09-17 B안)
            //   전에는 맨 아래에 "지난 시공 N건" 목록이 따로 있었는데,
            //   그러면 '지금 건'과 '지난 건'이 화면의 다른 층에 흩어져 한 고객의 이력이 안 보였다.

            // 6.6 팀원 현장 메모 — 직원이 링크 화면에서 보낸 특이사항(2026-06-06). 있을 때만 카드.
            if (teamNotes.isNotEmpty()) {
                // 한 건 카드 안 ② 접힘 줄로 — 전엔 카드 안에 카드가 또 있었다("네모 안 네모"). (2026-10-15 Fable 점검)
                //   답글 안 단 메모가 있으면 펼친 채로 시작 — 직원이 보낸 특이사항을 놓치지 않게.
                var teamNotesExpanded by remember(jobId, teamNotes.size) {
                    mutableStateOf(teamNotes.any { it.replyText.isNullOrBlank() })
                }
                CdCardDivider()
                CdFoldRow(Icons.Filled.EditNote, "팀원 현장 메모", teamNotes.size, teamNotesExpanded) { teamNotesExpanded = !teamNotesExpanded }
                if (teamNotesExpanded) Column {
                        Spacer(Modifier.height(10.dp))
                        teamNotes.forEachIndexed { idx, note ->
                            if (idx > 0) Spacer(Modifier.height(8.dp))
                            androidx.compose.foundation.layout.Box(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                    .background(AppTheme.colors.cautionBg).padding(12.dp)
                            ) {
                                Column {
                                    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                        Text(note.memberName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB8780A))
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            com.detailline.callfollowcrm.util.DateTimeUtils.formatShort(note.createdAtMs),
                                            fontSize = 11.sp, color = TossTextTertiary
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(note.text, fontSize = 13.5.sp, color = Color(0xFF5A3D00), lineHeight = 19.sp)

                                    // 답글 편집 상태 — 평소엔 닫힘(버튼만), 누르면 입력칸 열림.
                                    var editing by remember(note.eventId) { mutableStateOf(false) }
                                    var reply by remember(note.eventId) { mutableStateOf("") }
                                    val hasReply = !note.replyText.isNullOrBlank()

                                    // 내 답글(있으면) — 팀원 링크 화면에도 같이 보임. 편집 중엔 숨김.
                                    if (hasReply && !editing) {
                                        Spacer(Modifier.height(8.dp))
                                        androidx.compose.foundation.layout.Box(
                                            Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp))
                                                .background(TossBlueSoft).padding(horizontal = 10.dp, vertical = 8.dp)
                                        ) {
                                            Column {
                                                androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                                    Text("↳ 내 답글", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TossBlue)
                                                    Spacer(Modifier.weight(1f))
                                                    Text("수정", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TossTextTertiary,
                                                        modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                                            .clickable { reply = note.replyText.orEmpty(); editing = true }
                                                            .padding(horizontal = 6.dp, vertical = 2.dp))
                                                }
                                                Spacer(Modifier.height(2.dp))
                                                Text(note.replyText!!, fontSize = 13.sp, color = TossBlueDark, lineHeight = 18.sp)
                                            }
                                        }
                                    }

                                    // 답글 없고 편집도 아니면 → "답글 달기" 버튼만(접힘).
                                    if (!hasReply && !editing) {
                                        Spacer(Modifier.height(8.dp))
                                        androidx.compose.foundation.layout.Row(
                                            Modifier.clip(RoundedCornerShape(8.dp))
                                                .clickable { reply = ""; editing = true }
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                        ) {
                                            androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.Reply, null,
                                                tint = Color(0xFFB8780A), modifier = Modifier.size(15.dp))
                                            Spacer(Modifier.width(5.dp))
                                            Text("답글 달기", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB8780A))
                                        }
                                    }

                                    // 편집 중 → 입력칸 + 보내기/취소.
                                    if (editing) {
                                        Spacer(Modifier.height(8.dp))
                                        androidx.compose.foundation.layout.Box(
                                            Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(Color.White)
                                                .border(1.dp, Color(0xFFEAD9A8), RoundedCornerShape(9.dp))
                                                .padding(horizontal = 10.dp, vertical = 9.dp)
                                        ) {
                                            androidx.compose.foundation.text.BasicTextField(
                                                value = reply,
                                                onValueChange = { reply = it },
                                                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = com.detailline.callfollowcrm.presentation.theme.Pretendard, fontSize = 13.sp, color = TossTextPrimary),
                                                cursorBrush = androidx.compose.ui.graphics.SolidColor(TossBlue),
                                                modifier = Modifier.fillMaxWidth(),
                                                decorationBox = { inner ->
                                                    if (reply.isEmpty()) Text("팀원에게 답글…", fontSize = 13.sp, color = TossTextTertiary)
                                                    inner()
                                                }
                                            )
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        androidx.compose.foundation.layout.Row(
                                            Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
                                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                        ) {
                                            Text("취소", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TossTextTertiary,
                                                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                                    .clickable { editing = false; reply = "" }
                                                    .padding(horizontal = 14.dp, vertical = 9.dp))
                                            Spacer(Modifier.width(4.dp))
                                            androidx.compose.foundation.layout.Box(
                                                Modifier.clip(RoundedCornerShape(9.dp))
                                                    .background(if (reply.isBlank()) Color(0xFFE2E6EC) else TossBlue)
                                                    .clickable(enabled = reply.isNotBlank()) {
                                                        viewModel.replyToTeamNote(note.eventId, reply.trim())
                                                        editing = false
                                                    }
                                                    .padding(horizontal = 16.dp, vertical = 9.dp)
                                            ) {
                                                Text("보내기", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
            }   // if (teamNotes.isNotEmpty())
            // ── ④ foot — 프로토 `.foot`: **작은 회색 한 줄**에 선택사항을 모은다. (2026-10-15 사장님 "협업 줄이 너무 큰 느낌" / Fable §2 ④)
            //   [총 N · 계약금 N]  [🤝 협업]  [접수서 N장]  [⋯ 더보기]
            //   협업·접수서는 전용 줄(구분선 + 캐럿)을 없애고 여기로 — 부를 수도 안 부를 수도 있는 건 작게.
            //   자주 안 쓰는 일(A/S·취소·사진 골라 지우기·이 건 지우기)은 ⋯ 더보기 안 — 평소엔 안 보인다.
            //   넘치면 다음 줄로(FlowRow). 저장·해제·삭제 경로는 전과 같다(CollabAfterCard / IssuedDocList / 확인창).
            run {
                val hasDate = cShown.scheduledWorkDate != null
                val collabSiteTitle = com.detailline.callfollowcrm.util.AddressExtractor.siteLabel(displayAddr ?: "").takeIf { it.isNotBlank() }?.let { "$it 현장" }
                    ?: c.name?.takeIf { it.isNotBlank() && it.count { ch -> ch.isDigit() } < 9 }?.let { "$it 현장" }
                    ?: "이 현장"
                val jobDayStart: Long? = cShown.scheduledWorkDate?.let { DateTimeUtils.startOfDay(it) }
                val jobDays = remember(cShown.scheduledWorkDate, cShown.scheduledWorkDays) {
                    if (jobDayStart == null) emptySet<Long>()
                    else (0 until cShown.scheduledWorkDays.coerceAtLeast(1)).map { jobDayStart + it * 86_400_000L }.toSet()
                }
                // 이 건에 걸린 협업 사장만. days 있으면 그 날들·없으면(구버전) 대표 건 하루 — CollabAssignmentLine 규칙 재사용.
                val jobCollab = remember(c.id, host.collabRefresh, jobDayStart, host.repDay) {
                    if (jobDayStart == null) emptyList<com.detailline.callfollowcrm.domain.collab.CollabAssignmentLine>()
                    else host.container.preferences.collabAssignments.mapNotNull {
                        com.detailline.callfollowcrm.domain.collab.CollabAssignmentLine.parse(it)
                    }.filter { it.customerId == c.id && it.coversDay(jobDayStart, representativeDay = host.repDay) }
                        .distinctBy { it.shareId.ifBlank { PhoneKey.of(it.phone) } }
                }
                val showJobCollabShare = remember(c.id, jobId) { mutableStateOf(false) }
                var collabExpanded by remember(jobId) { mutableStateOf(false) }
                // 📋 이 건의 문서 — 시공예정일이 이 건 날짜와 같은 발행 문서 + (이 건이 **맨 처음 건**이면)
                //   날짜 없는/안 맞는 떠돌이 문서(견적서 등)까지. **보기 전용**, 보내기는 챗. (2026-10-15 사장님: 날짜 없는 문서는 1차로)
                val isEarliest = job != null && job.id == host.earliestJobId
                val docsForJob = remember(issuedDocs, jobDayStart, isEarliest) {
                    com.detailline.callfollowcrm.domain.customer.IssuedDocAssign.forJob(
                        issuedDocs, jobDayStart, isEarliest,
                        com.detailline.callfollowcrm.domain.customer.IssuedDocAssign.jobDaySet(host.allJobs)
                    )
                }
                // 1차엔 날짜 없는 **견적서**가 섞일 수 있어 「접수서」로만 쓰면 틀린다 — 종류에 맞춰 라벨. (2026-10-15)
                val docKindLabel = when {
                    docsForJob.all { it.kind == "intake" } -> "접수서"
                    docsForJob.all { it.kind == "quote" } -> "견적서"
                    else -> "문서"
                }
                var intakeExpanded by remember(jobId) { mutableStateOf(false) }
                val canDeleteJob = job != null && (jobCancelled(job) || jobBlank(job))
                // 🔧 A/S 잡을 수 있는 자리(고객 단위 → 대표 건 카드, 아직 A/S 안 잡힘). 완납·완료한 건이면 foot 에 바로, 아니면 더보기 안.
                val asCanBook = isPrimary && scheduled != null && c.asScheduledDate == null
                val jobDone = job?.workCompletedAt != null
                val asReadyHere = asCanBook && (allPaid || jobDone)
                // ⋯ 더보기는 **안에 칩이 하나라도 있을 때만** — 아래 펼침 블록의 조건과 같은 식. (완납 취소는 잔금 줄로 갔다. 2026-10-15)
                val showMore = (asCanBook && !asReadyHere) ||
                    (isPrimary && c.asPendingAt == null) ||
                    (scheduled != null && !allPaid) || canDeleteJob || (photoTotal > 0 && !pickMode)
                var moreActionsExpanded by remember(jobId) { mutableStateOf(false) }

                CdCardDivider()
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // 🔎 foot 의 행동은 전부 **같은 칩(CdUndoChip + 아이콘)** — 회색 글자만 덩그러니 있어 "보이지도 않고 낯설다"던 것.
                    //   (2026-10-15 사장님) 프로토 `.foot`(작은 선택지 모음) 뜻은 그대로 — 자리·순서 안 바꾸고 **누르는 것처럼 보이게만**.
                    // 총·계약금 — 탭하면 금액 창(계약금 → 총액 → 잔금 자동). 계약금은 총금액 없이도 따로 넣을 수 있다(2026-06-11).
                    if (hasAmount) {
                        CdUndoChip(
                            "총 " + (if (totalWon > 0L) manwonLabel(totalWon) else "미입력") +
                                " · 계약금 " + (if (depositWon > 0L) manwonLabel(depositWon) + (if (depPaid) " 받음" else "") else "미설정"),
                            danger = false, filled = true, icon = Icons.Filled.Payments
                        ) { host.onEditAmount(editJobId, if (totalWon > 0L) "deposit" else "total") }
                    } else {
                        CdUndoChip("계약금 넣기", danger = false, filled = true, icon = Icons.Filled.Add) {
                            host.onEditAmount(editJobId, "deposit")
                        }
                    }
                    // 🤝 협업 — 없으면 「협업 부르기」(탭 → 바로 부르기 시트), 있으면 보라 아이콘 + 이름(탭 → 펼침).
                    if (hasDate) {
                        CdUndoChip(
                            if (jobCollab.isEmpty()) "협업 부르기"
                            else jobCollab.joinToString(", ") { it.name.ifBlank { "협업 사장님" } },
                            danger = false, filled = true, icon = Icons.Filled.Handshake,
                            tint = if (jobCollab.isEmpty()) null else AppTheme.colors.categoryText
                        ) {
                            if (jobCollab.isEmpty()) showJobCollabShare.value = true
                            else collabExpanded = !collabExpanded
                        }
                    }
                    // 📋 접수서 — 발행된 게 있을 때만, 파랑(프로토 `.lk`). 탭 → 아래 펼침(보기 전용).
                    if (docsForJob.isNotEmpty()) {
                        CdUndoChip(
                            "$docKindLabel ${docsForJob.size}장", danger = false, filled = true,
                            icon = Icons.Filled.ReceiptLong, tint = TossBlue
                        ) { intakeExpanded = !intakeExpanded }
                    }
                    // 🔧 A/S 잡기 — **완납·완료한 건에선 foot 에 바로**. (2026-10-15 사장님 "완납하면 as 일정 항목을 찾아볼 수가 없네")
                    //   완납 손님이 A/S 를 제일 많이 신청하는데, 전엔 ⋯더보기 안에만 있어 접힌 카드를 펴고 더보기까지 열어야 보였다.
                    //   아직 시공 전인 건은 전처럼 더보기 안(A/S 는 시공 뒤의 일). A/S 는 고객 단위라 대표 건 카드에서만(isPrimary — 데이터 모양).
                    if (asReadyHere) {
                        CdUndoChip("A/S 잡기", danger = false, filled = true, icon = Icons.Filled.Build) { host.onAsPicker() }
                    }
                    // ⋯ 더보기 — A/S·취소·사진 골라 지우기·이 건 지우기. 자주 안 쓰니 평소엔 접어둔다. (2026-10-15 사장님 "버튼이 너무 많다")
                    if (showMore) {
                        CdUndoChip(
                            if (moreActionsExpanded) "닫기" else "더보기",
                            danger = false, filled = true, icon = Icons.Filled.MoreHoriz
                        ) { moreActionsExpanded = !moreActionsExpanded }
                    }
                }

                // 협업 펼침 — 이 건의 협업 사장 카드(해제 포함) + 더 부르기. 내용은 전과 같다.
                if (collabExpanded && jobCollab.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    jobCollab.forEach { line ->
                        CollabAfterCard(
                            partnerName = line.name.ifBlank { "협업 사장님" },
                            siteTitle = collabSiteTitle,
                            shareId = line.shareId,
                            onRelease = {
                                // 📮 협업 해제 → 우체통(B 에게 알림은 연결되면 간다). (설계 §1-A #3)
                                if (line.shareId.isNotBlank()) {
                                    host.container.applicationScope.launch {
                                        runCatching {
                                            host.container.outbox.enqueue(
                                                com.detailline.callfollowcrm.domain.outbox.OutboxKind.COLLAB_END,
                                                line.shareId,
                                                org.json.JSONObject().put("mode", "end_owner").toString()
                                            )
                                        }
                                        host.container.outbox.tryNow()
                                    }
                                }
                                // 그 한 건(shareId)만 뗀다 — 다른 건의 같은 사장님은 그대로.
                                host.container.preferences.collabAssignments = host.container.preferences.collabAssignments
                                    .filterNot { e ->
                                        val pl = com.detailline.callfollowcrm.domain.collab.CollabAssignmentLine.parse(e)
                                        pl != null && pl.customerId == c.id &&
                                            (if (line.shareId.isNotBlank()) pl.shareId == line.shareId
                                             else PhoneKey.of(pl.phone) == PhoneKey.of(line.phone))
                                    }.toSet()
                                host.onCollabRefresh()
                            }
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    Text(
                        "협업 사장님 더 부르기", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AppTheme.colors.categoryText,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .clickable { showJobCollabShare.value = true }.padding(vertical = 8.dp)
                    )
                }

                // 접수서 펼침 — 발행 접수서 목록(열기·수정·삭제) + 다시 보내기(챗으로). 내용은 전과 같다.
                if (intakeExpanded && docsForJob.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    IssuedDocList(
                        docs = docsForJob,
                        phone = c.phoneNumber,
                        intakeByToken = intakeByToken,
                        onOpenQuoteDoc = { host.onOpenQuoteDoc(it) },
                        onOpenIntakeDoc = { host.onOpenIntakeDoc(it) },
                        onEditIssued = { docId -> host.onEditIssued(docId) },
                        onRequestDelete = { host.onRequestDeleteDoc(it) },
                        onNewDoc = { host.onOpenChat() },
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "$docKindLabel 다시 보내기", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TossBlue,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .clickable { host.onOpenChat() }.padding(vertical = 8.dp)
                    )
                }

                // ⋯ 더보기 펼침 — 📐 넘치면 다음 줄로. (2026-09-28 사장님 "디자인이.. 왜저래")
                if (moreActionsExpanded) {
                    Spacer(Modifier.height(10.dp))
                    // 칩은 **왼쪽 정렬** — 위 foot(총·계약금·협업·더보기)도 왼쪽이라 시선이 좌↔우로 안 튄다.
                    //   (2026-10-15 사장님 "정렬 안 맞는 거 없는지" — 오른쪽 정렬이던 걸 맞춤)
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 🔧 A/S 가 아직 없을 때의 **조용한 길**(시공 전인 건). 완납·완료한 건은 foot 에 바로 보이니 여기엔 안 둔다(두 벌 X).
                        if (asCanBook && !asReadyHere) {
                            CdUndoChip("A/S 잡기", danger = false) { host.onAsPicker() }
                        }
                        // 🔧 날짜를 못 잡아도 **명단에는 올려둔다.** (2026-09-27 사장님) — 이름이 **하는 일**을 말하게.
                        if (isPrimary && c.asPendingAt == null) {
                            CdUndoChip("A/S 명단에 올리기", danger = false) { host.onAsPending() }
                        }
                        // (「완납 취소」는 잔금 줄의 「전액 완납」 옆으로 올라갔다 — 여기 두 벌로 두지 않는다. 2026-10-15)
                        // 🚫 **잔금까지 받은 일은 「예약 취소」가 말이 안 된다.** (2026-09-28 사장님) — [완납 취소]가 먼저다.
                        if (scheduled != null && !allPaid) {
                            CdUndoChip("시공 예약 취소", danger = true) { host.onCancelBooking() }
                        }
                        // 🖼 사진 골라 지우기 — 고르는 화면으로 갈 뿐 아무것도 안 지운다. 색 중립. (2026-09-30 사장님)
                        if (photoTotal > 0 && !pickMode) {
                            CdUndoChip("사진 골라 지우기", danger = false) { pickMode = true; moreActionsExpanded = false }
                        }
                        // 🗑 취소·빈 건만 지울 수 있다 — 옛 '지난 건' 목록에 있던 삭제 경로 그대로(확인창은 화면단 — host.onDeleteJob).
                        if (canDeleteJob) {
                            val jobToDelete = job
                            CdUndoChip("이 건 지우기", danger = true) { host.onDeleteJob(jobToDelete) }
                        }
                    }
                }

                if (showJobCollabShare.value) {
                    CollabShareSheet(
                        siteTitle = collabSiteTitle, addr = displayAddr, scheduledAtMs = cShown.scheduledWorkDate,
                        customerId = c.id, jobDays = jobDays,
                        requestedPhones = jobCollab.map { it.phone },
                        onShared = { host.onCollabRefresh() }, onDismiss = { showJobCollabShare.value = false }
                    )
                }
            }
            }   // if (expanded)
        }   // 한 건 카드 Column 닫기 (머리+상세+메모+사진+foot 한 카드) — 2026-10-15 사장님 "한 건=한 카드"
    }   // 한 건 카드 TossCard 닫기
}

/**
 * 카드 머리 — 프로토 `.jhead` 그대로:
 *   ● 색점(예정=초록 · 완료=회색 · 취소=없음) · N차(17) · 날짜/시간(14) · D-day 칩 · [진행 중 / 완료] 딱지(오른쪽 끝).
 *   접힌 건: 날짜 회색 · 「60만원 · 완납」(fold-sum) · ▾ 캐럿(펼치면 180°) — 머리 전체가 펼침 손잡이.
 *   진행 중인 건: 날짜를 누르면 그 건 날짜 고치기(프로토 `.jdate tap` → sheet('date')).
 */
@Composable
private fun JobCardHead(
    job: JobEntity,
    nthLabel: String?,
    folded: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onEditDate: () -> Unit
) {
    val cancelled = jobCancelled(job)
    // 완료된 건만 '완료'. 앞으로 잡힌 건은 D-day 로 — 예정인데 '완료'라고 쓰면 거짓말이다. (2026-09-17)
    //   잔금까지 받아 마무리된 건(jobClosed)도 접히니 같은 '완료' 옷.
    val done = job.workCompletedAt != null || jobClosed(job)
    // 「완료 · 금액 미입력」 — 돈이 하나도 없는데 완료만 찍힌 건은 딱지에서 바로 보이게. (2026-10-15 사장님 결정 Q1)
    //   전엔 몸통 「아직 입력 안 함」과 머리 「완료」가 따로 떠서 버그처럼 보였다. 완료 규칙은 그대로, **표시만**.
    //   판단은 SettlementCalc.hasMoney(건) 한 곳(총액·계약금·잔금 전부 없음).
    val doneNoMoney = done && !cancelled && !com.detailline.callfollowcrm.domain.settlement.SettlementCalc.hasMoney(job)
    val dateLabel = job.scheduledWorkDate?.let { d ->
        DateTimeUtils.formatDateLabel(d) + (
            if (folded) "" else
                (job.scheduledWorkMinutes?.let { " " + DateTimeUtils.formatWorkMinutes(it) } ?: "") +
                    DateTimeUtils.workPeriodSuffix(d, job.scheduledWorkDays)
        )
    } ?: if (job.cancelledAt != null) "예약 취소함" else "날짜 미정"
    // 접힌 건의 첫 줄 오른쪽 — 금액·완납(프로토 `.fold-sum` "60만원 · 완납"). (2026-10-15 사장님)
    val settle = com.detailline.callfollowcrm.domain.settlement.SettlementCalc.rowOf(job)
    val foldSum = if (folded && !cancelled && settle.total > 0L)
        manwonLabel(settle.total) + (if (settle.isPaidOff) " · 완납" else " · 잔금 " + manwonLabel(settle.outstanding))
    else null
    Row(
        Modifier.fillMaxWidth()
            .then(if (folded) Modifier.clip(AppShape.sm).clickable(onClick = onToggle) else Modifier),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        // 왼쪽 묶음(점·차수·날짜·D-day)이 남는 폭을 다 먹어 딱지는 늘 오른쪽 끝. 날짜가 길면 날짜만 줄인다(…).
        Row(Modifier.weight(1f), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            if (!cancelled) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(if (done) TossTextTertiary else TossSuccess))
                Spacer(Modifier.width(8.dp))
            }
            val nthText = nthLabel ?: if (cancelled) (if (job.cancelledAt != null) "취소한 건" else "빈 건") else null
            if (nthText != null) {
                Text(
                    nthText, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold,
                    color = if (cancelled) TossTextTertiary else TossTextPrimary
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                dateLabel, fontSize = 14.sp,
                fontWeight = if (folded) FontWeight.SemiBold else FontWeight.Bold,
                color = if (folded || job.scheduledWorkDate == null) TossTextTertiary else TossTextPrimary,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false).then(
                    if (folded) Modifier
                    else Modifier.clip(AppShape.sm).clickable(onClick = onEditDate).padding(horizontal = 2.dp)
                )
            )
            if (!folded && !done && job.scheduledWorkDate != null) {
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.clip(AppShape.sm).background(AppTheme.colors.primaryBg)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(DateTimeUtils.dDayLabel(job.scheduledWorkDate!!), style = AppType.micro, color = TossBlue)
                }
            }
        }
        if (foldSum != null) {
            Spacer(Modifier.width(8.dp))
            Text(foldSum, style = AppType.label, fontWeight = FontWeight.Bold, color = TossTextSecondary, maxLines = 1)
        }
        if (!cancelled) {
            Spacer(Modifier.width(9.dp))
            Box(
                Modifier.clip(AppShape.pill)
                    .background(if (done) AppTheme.colors.doneBg else AppTheme.colors.primaryBg)
                    .padding(horizontal = 11.dp, vertical = 4.dp)
            ) {
                Text(
                    if (doneNoMoney) "완료 · 금액 미입력" else if (done) "완료" else "진행 중", style = AppType.micro,
                    color = if (done) AppTheme.colors.doneText else TossBlue
                )
            }
        }
        if (folded) {
            Spacer(Modifier.width(6.dp))
            androidx.compose.material3.Icon(
                Icons.Default.KeyboardArrowDown, if (expanded) "접기" else "펼치기", tint = TossTextTertiary,
                modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = if (expanded) 180f else 0f }
            )
        }
    }
}
