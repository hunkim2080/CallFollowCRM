/* Buildless, offline UI. All demo data is fictional. Source map: SOURCES.md. */
'use strict';
const icons = {
  chat:'<path d="M20 11a8 8 0 0 1-8 8H4l-2 3V11a9 9 0 0 1 18 0Z"/><path d="M7 9h8M7 13h5"/>',
  calendar:'<rect x="3" y="5" width="18" height="16" rx="3"/><path d="M7 2v6m10-6v6M3 11h18M7 15h3m4 0h3"/>',
  money:'<rect x="2" y="5" width="20" height="15" rx="3"/><circle cx="12" cy="12" r="3"/><path d="M5 12h1m12 0h1"/>',
  record:'<path d="M4 20V9m8 11V3m8 17v-7"/>',
  more:'<circle cx="5" cy="12" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="19" cy="12" r="1"/>',
  search:'<circle cx="10" cy="10" r="6"/><path d="m15 15 6 6"/>',
  bell:'<path d="M5 16V9a7 7 0 0 1 14 0v7l2 2H3l2-2Zm4 5h6"/>',
  phone:'<path d="m5 3 4 5-3 3a20 20 0 0 0 7 7l3-3 5 4c-1 5-5 4-8 2C7 18 2 11 2 6l3-3Z"/>',
  back:'<path d="m15 4-8 8 8 8"/>',
  person:'<circle cx="12" cy="7" r="4"/><path d="M4 22v-4a8 8 0 0 1 16 0v4"/>',
  pin:'<path d="M19 9c0 6-7 13-7 13S5 15 5 9a7 7 0 1 1 14 0Z"/><circle cx="12" cy="9" r="2"/>',
  check:'<path d="m4 12 5 5L20 6"/>',
  document:'<path d="M5 2h10l4 4v16H5ZM15 2v5h4M8 11h8M8 15h8M8 19h5"/>',
  plus:'<path d="M12 4v16M4 12h16"/>',
  clock:'<circle cx="12" cy="12" r="9"/><path d="M12 6v6l4 3"/>',
  qr:'<path d="M3 3h6v6H3Zm12 0h6v6h-6ZM3 15h6v6H3Zm12 0h2v2h-2Zm4 4h2v2h-2Zm-4 2v-3m4-5h2"/>'
};
function icon(name){return `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${icons[name]||icons.document}</svg>`;}
function head(title,sub='',compact=false){
  const isChat=title==='최민서 · 성수동';
  const right=compact?(isChat?icon('phone')+icon('person'):''):(title==='일정'?icon('plus'):title==='정산'?'':icon('bell')+icon('qr'));
  return `<div class="app-head ${compact?'compact':''}">${compact?icon('back'):''}<div ${compact?'style="flex:1"':''}><strong>${title}</strong>${sub?`<small>${sub}</small>`:''}</div><div class="head-icons">${right}</div></div>`;
}
function nav(active='상담함'){return `<div class="app-nav" aria-hidden="true">${[['chat','상담함'],['calendar','일정'],['money','정산'],['record','내 기록'],['more','더보기']].map(([i,t])=>`<span class="${active===t?'selected':''}">${icon(i)}${t}</span>`).join('')}</div>`;}
function phone(content,time){return `<div class="phone"><div class="status"><span>${time}</span><i class="camera"></i><div class="status-icons"><span class="signal">▂▄▆</span><span>LTE</span><i class="battery"></i></div></div>${content}</div>`;}
function action(label,i){return `<span class="app-action">${i?icon(i):''}${label}</span>`;}
function row(initial,name,message,time,unread=false){return `<div class="app-row" data-demo-row ${unread?'data-unanswered="true"':''}><span class="avatar ${unread?'unread':''}">${initial}</span><div class="row-copy"><b>${name}${unread?'<i class="read-dot"></i>':''}</b><p>${message}</p></div><div class="row-end">${time}${unread?'<br><button class="demo-confirm" type="button" aria-label="화면 예시에서 '+name+' 문의 확인">확인</button>':''}</div></div>`;}
function home(){return head('상담함','11월 5일 (목)')+`<div class="app-body"><div class="app-search">${icon('search')}이름·주소·금액·통화 내용까지</div><div class="app-chips"><button type="button" class="app-chip active demo-filter" data-filter="all" aria-pressed="true">전체</button><button type="button" class="app-chip demo-filter" data-filter="unanswered" aria-pressed="false">답장 안 함 <b class="unanswered-count">2</b></button><span class="app-chip">시공 대기</span></div><div class="app-card blue-card" data-home-extra><span class="app-caption">오늘 시공 · D-DAY</span><h4>08:00 · 박지현 고객님</h4><p>광진구 구의동 · 한빛아파트 102동</p><div class="app-actions">${action('길찾기','pin')}${action('전화','phone')}${action('완료','check')}</div></div><div class="mini-notice" data-home-extra><strong>부재중 전화에 자동 답장 보냄</strong>010-0000-2741 · 09:37 · 자동 인사 보냄</div><div class="app-label">최근 대화</div><div class="app-card" style="padding:0 12px">${row('최','최민서 · 성수동','토요일에 욕실 벽면 보수 가능할까요?','09:40',true)}${row('이','이준호 사장님','토요일 오전 시간 됩니다.','09:25',true)}${row('박','박지현 · 구의동','나: 8시까지 도착하겠습니다.','07:32')}</div><p class="demo-helper">화면 예시 · ‘답장 안 함’과 ‘확인’을 눌러보세요.</p><div class="demo-toast" role="status" hidden></div><button type="button" class="demo-reset" hidden>예시 처음으로</button></div>`+nav();}
function schedule(){
  const labels={2:['성수',''],4:['구의',''],5:['구의 · 송파',''],7:['성수',''],9:['마포',''],12:['강동',''],14:['성수','collab'],18:['용산',''],21:['성동','']};
  const cells=Array.from({length:35},(_,i)=>{const n=i+1;const display=n>30?n-30:n;return `<div class="calendar-cell ${n===5?'today':''} ${n>30?'dim':''}"><span>${display}</span>${labels[n]?`<small class="${labels[n][1]}">${labels[n][0]}</small>`:''}</div>`;}).join('');
  return head('일정','11월 5일 (목)')+`<div class="app-body"><div class="calendar"><div class="calendar-head"><span>‹ &nbsp; 2026년 11월 &nbsp; ›</span><small>금액 ○</small></div><div class="calendar-grid">${['일','월','화','수','목','금','토'].map(d=>`<span class="weekday">${d}</span>`).join('')}${cells}</div><div class="calendar-legend"><span><i></i>시공</span><span><i class="purple"></i>협업</span><span>● 요청</span></div></div><div class="app-label">11월 5일 목요일</div><div class="app-card"><div class="time-inline">08:00 <span class="app-pill" style="float:right">시공</span></div><h4>박지현 · 구의동</h4><p>한빛아파트 102동 803호<br>욕실 벽면 타일 보수</p><div class="app-actions">${action('길찾기','pin')}${action('전화','phone')}</div></div><div class="app-card"><div class="time-inline" style="color:#6246ea">14:00 <span class="app-pill purple" style="float:right">협업</span></div><h4>정다운시공 · 송파</h4><p>송파구 문정동 · 현장 마감 지원</p><div class="app-actions">${action('길찾기','pin')}${action('전화','phone')}</div></div></div>`+nav('일정');
}
function inquiry(){return head('최민서 · 성수동','010-0000-2741',true)+`<div class="app-body"><div class="chat-date">2026년 11월 5일 목요일</div><div class="mini-notice gray" style="margin-bottom:15px">${icon('phone')}<strong>부재중 전화 · 09:37</strong>자동 인사 보냄</div><div class="bubble out">안녕하세요, 바른시공입니다.<br>지금 작업 중이라 전화를 못 받았습니다. 시공 지역과 작업 내용을 남겨주시면 연락드릴게요.</div><div class="bubble-time right">오전 9:37</div><div class="bubble">성수동인데요. 욕실 벽면 타일이 들떠서요. 토요일에 보수 가능할까요?</div><div class="bubble-time">오전 9:40</div><div class="app-card" style="border:1px solid #dce7fb;margin-top:18px"><div class="app-caption" style="color:#1b64da">막내 추천 답변</div><p style="font-size:11px">안녕하세요! 들뜬 부분과 욕실 전체 사진을 보내주시면 확인 후 작업 범위와 일정을 안내드리겠습니다.</p><div class="app-note">내용을 확인하고 고쳐 보내세요.</div></div></div><div class="chat-compose"><span>⊕</span><span class="compose-box">메시지를 입력하세요</span><span class="send">↑</span></div>`;}
function call(){return head('최민서 · 성수동','통화 기록',true)+`<div class="app-body"><div class="app-row" style="padding-top:3px;padding-bottom:16px"><span class="avatar">최</span><div class="row-copy"><b>최민서 고객님</b><small>11.5 (목) 11:20 · 수신 통화</small></div></div><div class="app-card" style="padding:15px"><div class="summary-head">${icon('phone')}통화 요약 <small>3분 42초</small></div><div class="summary-segment"><span>0:00–0:47</span><i class="speaker-dot"></i><p>욕실 샤워기 쪽 벽면 타일이 들뜸. 전체 교체보다 들뜬 부분 보수를 원함.</p></div><div class="summary-segment"><span>0:48–1:35</span><i class="speaker-dot"></i><p>기존과 비슷한 무광 아이보리 타일로 요청. 사진과 현장 상태 확인 필요.</p></div><div class="summary-segment"><span>1:36–2:50</span><i class="speaker-dot"></i><p>11월 7일 토요일 오전 9시 방문 협의. 관리실에 공사 신고 예정.</p></div><div class="summary-segment"><span>2:51–3:42</span><i class="speaker-dot owner"></i><p>보수·자재 포함 115만원 안내. 계약금 30만원, 견적서를 문자로 보내기로 함.</p></div><div class="speaker-legend"><span><i class="speaker-dot"></i>손님</span><span><i class="speaker-dot owner"></i>나</span></div><div class="app-note">· AI 짐작이에요 (통화 전문에서 확인)</div></div><div class="mini-notice"><strong>약속을 다시 확인하는 자리</strong>시간 구간을 누르면 그 부분부터 재생할 수 있어요.<br>이 페이지는 재생되지 않는 화면 예시입니다.</div></div>`;}
function quote(){return head('견적서','미리보기',true)+`<div class="app-body" style="padding:9px"><div class="quote-paper"><div class="doc-heading"><div><h4>견적서</h4><span>ESTIMATE</span></div><div class="doc-company"><b>바른시공</b><small>대표 강현우</small><small>010-0000-1004</small></div></div><div class="doc-recipient">TO. 최민서 귀하</div><div class="doc-sub">아래와 같이 견적합니다.</div><table class="doc-table"><thead><tr><th>품목</th><th>규격</th><th>금액(원)</th></tr></thead><tbody><tr><td>욕실 벽면 보수</td><td>1식</td><td>900,000</td></tr><tr><td>자재·운반</td><td>1식</td><td>300,000</td></tr><tr><td>마감 청소</td><td>1식</td><td>서비스</td></tr><tr><td class="discount">할인</td><td></td><td class="discount">−50,000</td></tr></tbody></table><div class="doc-total">합계 &nbsp; 1,150,000원<small>부가세 포함</small></div><div class="doc-payment"><span>계약금 <b>300,000원</b></span><span>잔금 <b>850,000원</b></span></div><div class="app-note">· 시공 예정일 : 2026년 11월 7일<br>· 계약금 입금 시 시공일이 확정됩니다<br>· 유효기간 : 발행일로부터 14일</div><div class="doc-bottom">위와 같이 견적합니다. <span class="seal">바른<br>시공</span></div></div><span class="app-primary">문자에 첨부</span></div>`;}
function invite(){return head('같이 할 사장님','',true)+`<div class="app-body" style="background:white"><div class="form-field"><b>성수동 현장</b><br><small>11.7 (토) · 성동구 성수동 은솔빌라 302호</small></div><div class="form-label">누구를 부를까요</div><div class="selection-row"><span class="avatar purple">이</span><div><b>이준호 사장님</b><small>010-0000-3082 · 함께 2번</small></div><span class="check">✓</span></div><div class="form-link">목록에 없어요 · 번호로 부르기</div><div class="form-label">보낼 일당</div><div class="form-row"><span>이준호 사장님</span><div class="form-field">30</div><span>만원</span></div><p class="app-note">이 현장에서 보낼 일당이에요. 비워도 돼요.</p><div class="form-label">몇 시까지 오면 되나요</div><div class="app-chips"><span class="app-chip" style="background:#eef0f3">오전 7시</span><span class="app-chip" style="background:#eef0f3">오전 8시</span><span class="app-chip purple">오전 9시</span><span class="app-chip" style="background:#eef0f3">안 정함</span></div><div class="form-label">전해둘 말</div><div class="form-field">지하 B1 주차 가능해요.<br>무광 아이보리 타일은 제가 가져갑니다.</div><div class="mini-notice gray">주소·날짜·시간·사진만 보여요<br>고객 번호와 대화는 안 보여요</div><span class="app-primary purple">이준호 사장님에게 요청 보내기</span><p class="app-note" style="text-align:center">자동 발송 아님 · 상대가 수락해야 시작돼요</p><p class="screen-demo-note">화면 예시 · 실제 요청은 전송되지 않습니다</p></div>`;}
function partner(){return head('협업 현장','받은 요청',true)+`<div class="app-body"><div class="partner-title"><span class="avatar purple">바</span><h4>바른시공 사장님</h4><p>함께할 현장 요청이 왔어요</p></div><div class="app-card"><div class="app-inline-title"><h4>성수동 현장</h4><span class="app-pill amber">수락 대기</span></div><div class="detail-line">${icon('calendar')}<span>11월 7일 토요일<small>오전 9시</small></span></div><div class="detail-line">${icon('pin')}<span>성동구 성수동<small>은솔빌라 302호</small></span></div><div class="detail-line">${icon('money')}<span>그날 일당 <b style="color:#6246ea">30만원</b></span></div><div class="form-label">전해둘 말</div><div class="form-field">지하 B1 주차 가능해요.<br>무광 아이보리 타일은 제가 가져갑니다.</div><div class="app-actions"><span class="app-action" style="background:#eef0f3;color:#5a6472">거절</span><span class="app-action" style="background:#3182f6;color:white">수락</span></div></div><div class="mini-notice green"><strong>수락한 현장은 일정에서도</strong>날짜를 눌러 내 일정과 함께 확인하세요.</div><div class="mini-notice gray"><strong>현장에선 진행 상황을 나누세요</strong>출발·도착·작업 완료와 현장 사진·메모로<br>서로의 진행을 확인합니다.</div><p class="screen-demo-note">협업 요청을 받는 쪽의 화면 예시</p></div>`;}
function field(){return head('박지현','고객 정보',true)+`<div class="app-body"><div class="app-card"><div class="app-inline-title"><h4>11.5 (목) · 1차</h4><span class="app-pill">진행 중</span></div><div class="detail-line">${icon('pin')}<span>광진구 구의동 한빛아파트<br>102동 803호</span></div><div class="app-actions">${action('길찾기 시작','pin')}</div><div class="money-lines"><span>총액 <b>115만원</b></span><span>계약금 <b style="color:#08803c">30만원 받음</b></span><span>잔금 <b class="unpaid">85만원</b></span></div><div class="app-label">현장 메모　⌄</div><div class="form-field memo-text">공동현관 #1020#<br>세대 비번 2580*<br>지하 2층 주차 · 관리실에 803호 방문<br>남은 타일은 베란다 오른쪽에 보관</div><div class="app-label">현장 사진 <span style="float:right;color:#1b64da">＋ 추가</span></div><div class="photo-grid"><div class="photo-tile"><img src="assets/site-example.svg" alt="욕실 작업 전 상태를 나타낸 예시 일러스트" width="120" height="104" loading="lazy"><span>시공 전</span></div><div class="photo-tile"><img src="assets/site-example.svg" alt="욕실 타일 보수 부위에 빨간 원을 표시한 예시 일러스트" width="120" height="104" loading="lazy"><i class="photo-mark"></i><span>표시한 사진</span></div></div><p class="app-note">사진 영역은 설명을 위한 일러스트입니다.</p><div class="field-footer"><span>협업 부르기</span><span>견적서 1장</span><span>A/S 잡기</span><span>더보기</span></div></div></div>`;}
function money(){return head('정산')+`<div class="app-body"><div class="app-card settle-top"><div class="settle-month">‹ &nbsp; 2026년 11월 &nbsp; ›</div><span class="app-caption">이번 달 받은 돈</span><div class="amount-big">230<small>만원</small></div><div class="settle-compare">▲ 전월 대비 15%</div><div class="settle-goal"><b>이번 달 목표 1,000만원</b><span>목표 수정</span><div class="settle-bar"><i></i></div><small>770만원 남았어요 · 23% · 25일 남음</small></div><div class="settle-outstanding">전체 아직 받을 돈 <b>85만원</b> · 미수 <b>1건</b></div></div><div class="app-label">받을 돈 · 미수 관리</div><div class="app-chips"><span class="app-chip">전체</span><span class="app-chip active">미수금</span><span class="app-chip">완료</span></div><div class="app-card"><div class="app-inline-title"><h4>박지현 · 구의동</h4><span class="app-pill pink">잔금 대기</span></div><p>시공 11.5</p><div class="money-lines"><span>총 <b>115만원</b></span><span>계약금 <b>30만원 받음</b></span><span>잔금 <b class="unpaid">85만원 남음</b></span></div><div class="app-actions">${action('문자','chat')}${action('전화','phone')}</div></div><div class="completion-example"><h4>전액 받았어요?</h4><p>박지현 님 잔금까지 받은 걸로 체크하면 완납 처리돼요. (총 115만원)</p><div class="app-actions"><span class="app-action" style="background:#eef0f3;color:#5a6472">취소</span><span class="app-action">네, 완납</span></div></div><p class="screen-demo-note">확인 창을 함께 보여주는 화면 예시</p></div>`+nav('정산');}
function statement(){return head('일당 정산서','',true)+`<div class="app-body" style="padding:9px"><div class="quote-paper" style="min-height:425px"><div class="statement-title"><h4>일당 정산서</h4><p>2026년 11월 · 완료된 현장</p></div><div class="statement-info">받는 분　<b>이준호 사장님</b><br>발행인　바른시공 · 강현우<br>발행일　2026.11.05</div><table class="doc-table"><thead><tr><th>시공일</th><th>현장</th><th>일당</th></tr></thead><tbody><tr><td>11.02</td><td>성수동</td><td>280,000</td></tr><tr><td>11.04</td><td>구의동</td><td>300,000</td></tr></tbody></table><div class="statement-total"><span>합계 · 2곳</span><b>580,000원</b></div><p class="app-note" style="margin-top:20px">완료된 현장 기준의 일당 정산 내역입니다.</p><div class="doc-bottom" style="margin-top:60px">발행인　바른시공</div><p class="screen-demo-note">1 / 1</p></div><div class="app-actions">${action('갤러리에 저장')}<span class="app-action" style="background:#3182f6;color:#fff">공유</span></div><div class="mini-notice gray">11월 7일 성수동 요청 건은 아직 완료 전.<br>이 정산서에는 포함되지 않습니다.</div></div>`;}

function heroChat(){return head('최민서 · 성수동','010-0000-2741',true)+`<div class="app-body hd-body"><div class="chat-date">오늘 · 상담 중</div><div class="bubble">성수동인데요, 욕실 벽면 타일이 들떠서요.<br>이번 주 토요일에 보수 가능할까요?</div><div class="bubble-time">오전 9:40</div><div class="hd-sent"><div class="bubble out">안녕하세요, 바른시공입니다. 네, 토요일 보수 가능합니다. 들뜬 부분이 잘 보이게 사진 한 장만 보내주시면, 작업 범위와 정확한 일정을 바로 안내드릴게요.</div><div class="bubble-time right">오전 9:41 · 보냄</div></div><div class="hd-cust2"><div class="bubble">사진 보냈어요! 그럼 <button type="button" class="tap-chip" data-reg>토요일 오전 9시</button>에 <button type="button" class="tap-chip" data-reg>성수동 은솔빌라 302호</button>로 와주시겠어요?</div><div class="bubble-time">오전 9:44</div><p class="hd-tip-tap">파란 글자를 톡 누르면 시공일로 등록돼요</p></div><div class="hd-sched"><div class="sched-chip">${icon('calendar')}<div class="sched-copy"><b>시공일 등록 완료</b><span>11월 7일 토요일 · 오전 9시<br>성수동 은솔빌라 302호</span></div><i class="sched-check">✓</i></div><p class="hd-tip-done">문자앱이었다면? 그냥 지나가는 글자였을 거예요.</p></div></div><div class="hd-foot"><div class="hd-draft"><p class="hd-tip">뭐라고 답하지… 싶을 때. 대충 쓰고 눌러보세요 ↓</p><div class="draft-box">네 토요일 되요 사진 ㄱ</div><button type="button" class="hd-polish-btn" data-polish>✨ 다듬기</button></div><div class="hd-polish"><span class="polish-tag">✨ 막내가 다듬었어요 · 보내기 전에 확인하세요</span><div class="polish-box">안녕하세요, 바른시공입니다. 네, 토요일 보수 가능합니다. 들뜬 부분이 잘 보이게 사진 한 장만 보내주시면, 작업 범위와 정확한 일정을 바로 안내드릴게요.</div><button type="button" class="hd-send-btn" data-send>이대로 보내기 ↑</button></div><div class="hd-restart"><button type="button" data-restart>처음부터 다시 보기 ↺</button></div></div>`;}

const screens={home,schedule,inquiry,call,quote,invite,partner,field,money,statement};
document.querySelectorAll('[data-screen]').forEach(mount=>{
  const render=screens[mount.dataset.screen];
  if(render){mount.innerHTML=phone(render(),mount.dataset.time||'09:41');mount.setAttribute('aria-label',({home:'상담함',schedule:'일정 달력',inquiry:'고객 문자와 추천 답장',call:'통화 요약',quote:'견적서',invite:'협업 요청 작성',partner:'협업 요청 수신',field:'현장 메모와 사진',money:'잔금 정산',statement:'일당 정산서'})[mount.dataset.screen]+' 화면 예시');}
});

const tabs=Array.from(document.querySelectorAll('[role="tab"][data-scene]'));
function selectScene(tab,focus=false){
  tabs.forEach(t=>{const selected=t===tab;t.setAttribute('aria-selected',String(selected));t.tabIndex=selected?0:-1;document.getElementById(t.getAttribute('aria-controls')).hidden=!selected;});
  if(focus)tab.focus();
}
tabs.forEach((tab,index)=>{
  tab.addEventListener('click',()=>selectScene(tab));
  tab.addEventListener('keydown',event=>{
    let next;
    if(event.key==='ArrowRight')next=(index+1)%tabs.length;
    if(event.key==='ArrowLeft')next=(index-1+tabs.length)%tabs.length;
    if(event.key==='Home')next=0;
    if(event.key==='End')next=tabs.length-1;
    if(next!==undefined){event.preventDefault();selectScene(tabs[next],true);}
  });
});

const guideSearch=document.getElementById('guide-search');
const guides=Array.from(document.querySelectorAll('[data-guide]'));
function filterGuide(){
  const terms=guideSearch.value.toLocaleLowerCase('ko').trim().split(/\s+/).filter(Boolean);
  let count=0;
  guides.forEach(guide=>{
    const text=(guide.dataset.guide+' '+guide.textContent).toLocaleLowerCase('ko');
    const match=terms.every(term=>text.includes(term));
    guide.hidden=!match;
    if(match)count++;
  });
  document.getElementById('guide-count').textContent=terms.length?`검색된 사용법 ${count}가지`:'8가지 사용법';
  document.getElementById('search-empty').hidden=count!==0;
}
guideSearch.addEventListener('input',filterGuide);
document.getElementById('clear-search').addEventListener('click',()=>{guideSearch.value='';filterGuide();guideSearch.focus();});

// Hero demo: the one thing the default SMS app can't do — polish a rough draft, tap a date to schedule.
const heroMount=document.querySelector('[data-hero-demo]');
if(heroMount){
  const reduce=window.matchMedia('(prefers-reduced-motion:reduce)').matches;
  const mountHero=()=>{
    heroMount.innerHTML=phone(heroChat(),'09:44');
    const root=heroMount.querySelector('.phone');
    const body=heroMount.querySelector('.hd-body');
    root.dataset.step='draft';
    const settle=()=>{if(body)requestAnimationFrame(()=>{body.scrollTop=body.scrollHeight;});};
    root.addEventListener('click',event=>{
      const polish=event.target.closest('[data-polish]');
      if(polish){
        if(reduce){root.dataset.step='polished';settle();}
        else{polish.disabled=true;polish.textContent='✨ 다듬는 중…';setTimeout(()=>{root.dataset.step='polished';settle();},700);}
        return;
      }
      if(event.target.closest('[data-send]')){root.dataset.step='sent';settle();return;}
      if(event.target.closest('[data-reg]')){root.dataset.step='scheduled';settle();return;}
      if(event.target.closest('[data-restart]'))mountHero();
    });
  };
  mountHero();
}

// Expand only the main manual for printing; preserve the reader's state afterwards.
let printState=[];
window.addEventListener('beforeprint',()=>{printState=guides.map(g=>({open:g.open,hidden:g.hidden}));guides.forEach(g=>{g.open=true;g.hidden=false;});});
window.addEventListener('afterprint',()=>{guides.forEach((g,i)=>{if(printState[i]){g.open=printState[i].open;g.hidden=printState[i].hidden;}});});
