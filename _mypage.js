
function esc(s){return String(s==null?'':s).replace(/[&<>"']/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c];});}
function ago(ms){var d=Date.now()-ms;var m=Math.floor(d/60000);if(m<1)return '방금 전';if(m<60)return m+'분 전';var h=Math.floor(m/60);if(h<24)return h+'시간 전';return Math.floor(h/24)+'일 전';}
function fmtPhone(p){p=(p||'').replace(/[^0-9]/g,'');if(p.length===11)return p.slice(0,3)+'-'+p.slice(3,7)+'-'+p.slice(7);return p;}
var ME=null;
function load(){
  fetch('/api/web/me').then(function(r){if(r.status===401){location.href='/web/login';throw 0;}return r.json();}).then(function(d){
    ME=d; render();
  }).catch(function(){});
}
function render(){
  var nm=ME.name||'사장님', ini=(nm[0]||'·');
  document.getElementById('pcName').textContent=nm;
  document.getElementById('pcAv').textContent=ini;
  document.getElementById('pcPhone').textContent='📱 '+fmtPhone(ME.owner_phone);
  // 기기
  var h=''; (ME.sessions||[]).forEach(function(s){
    var isPhone=s.device.indexOf('앱')>=0;
    h+='<div class="dev"><div class="dic">'+(isPhone?'📱':'💻')+'</div>'
      +'<div class="dm"><div class="dt">'+esc(s.device)+(s.is_current?'<span class="cur">지금</span>':'')+'</div>'
      +'<div class="dd">'+(s.is_current?'이 기기 · ':'')+ago(s.last_active_ms)+(isPhone?' · 이 폰이 열쇠':'')+'</div></div>'
      +(s.is_current?'':'<button class="out" onclick="logoutSid(\''+s.sid+'\')">로그아웃</button>')+'</div>';
  });
  document.getElementById('devList').innerHTML=h||'<div class="dd" style="padding:12px 0">로그인된 기기가 없어요.</div>';
  var n=(ME.sessions||[]).length;
  // 평소엔 겁주지 않기 — 배지는 항상 '이 기기 안전'. 실제 기기 목록은 [내 활동 기록 보기]에서 확인.
  document.getElementById('secBadge').textContent='이 기기 안전';
  document.getElementById('secBadge').className='badge on';
  var _ac=document.getElementById('actCnt'); if(_ac)_ac.textContent = n>1 ? ('· 기기 '+n) : '';
  // 키
  if(ME.key&&ME.key.connected){
    document.getElementById('keyBadge').textContent='연결됨'; document.getElementById('keyBadge').className='badge on';
    document.getElementById('keyState').style.display='flex';
    document.getElementById('keyMask').textContent='AIzaSy••••••••••••'+(ME.key.last4||'');
    document.getElementById('keyIn').value='';
  } else {
    document.getElementById('keyBadge').textContent='미연결'; document.getElementById('keyBadge').className='badge off';
    document.getElementById('keyState').style.display='none';
  }
  // 업체명 (글에 쓸)
  (function(){
    var bz=ME.biz||{}, inp=document.getElementById('bizIn'); if(!inp)return;
    var configured=(bz.value!=null);
    inp.value = configured ? bz.value : (bz.suggested||'');
    var badge=document.getElementById('bizBadge'), hint=document.getElementById('bizHint');
    if(bz.effective){ badge.textContent='설정됨 ✓'; badge.className='badge on'; }
    else { badge.textContent = configured ? '안 넣음' : '미설정'; badge.className='badge off'; }
    if(hint){
      if(!configured && bz.suggested){ hint.style.display='block'; hint.innerHTML='💡 <b>앱에 등록한 상호</b>예요. 맞으면 <b>[저장]</b>으로 확정, 다르면 고쳐서 저장하세요.'; }
      else if(bz.effective){ hint.style.display='block'; hint.innerHTML='✍️ 글에 <b>'+esc(bz.effective)+'</b> 이(가) 들어가요.'; }
      else { hint.style.display='none'; }
    }
  })();
  // F-7 스타일 학습 — 플랫폼별 실제 학습 여부(tone_styles)로 뱃지
  var st=ME.tone_styles||{}; var learned=0;
  ['bl','ig','th'].forEach(function(p){
    var on=!!st[p]; if(on)learned++;
    var e=document.getElementById('stb-'+p); if(e){e.textContent=on?'학습됨 ✓':'미설정';e.style.color=on?'var(--green)':'var(--ink3)';}
  });
  document.getElementById('toneBadge').textContent = learned?('플랫폼 '+learned+'개 학습'):'미설정';
  document.getElementById('toneBadge').className = learned?'badge on':'badge off';
  spTab(SP_CUR);
}
function _rjson(r){return r.json().catch(function(){return {ok:false,detail:'서버 오류 ('+r.status+') · 잠시 후 다시 시도해 주세요'};});}
function saveKey(){var k=document.getElementById('keyIn').value.trim();if(!k){alert('키를 붙여넣어 주세요');return;}
  var b=document.querySelector('#s2 .kbtn');if(b){b.textContent='연결 중…';b.style.opacity=.6;}
  fetch('/api/web/gemini-key',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({key:k})})
  .then(_rjson).then(function(d){if(d.ok){toast('키 저장됨 ✓');load();}else{alert(d.detail||'저장 실패');}})
  .catch(function(){alert('네트워크 오류 · 연결 상태를 확인해 주세요');})
  .then(function(){if(b){b.textContent='연결';b.style.opacity=1;}});}
function testKey(){toast('연결 확인 중…');fetch('/api/web/gemini-key/test').then(_rjson).then(function(d){toast(d.ok?'잘 돼요 ✓':('안 돼요 · '+(d.detail||'키 다시 확인')));}).catch(function(){toast('네트워크 오류');});}
function delKey(){if(!confirm('저장된 Gemini 키를 삭제할까요?'))return;fetch('/api/web/gemini-key',{method:'DELETE'}).then(_rjson).then(function(){toast('키 삭제됨');load();}).catch(function(){toast('네트워크 오류');});}
function saveBiz(){
  var v=document.getElementById('bizIn').value.trim();
  var b=document.querySelector('#s-biz .kbtn'); if(b){b.textContent='저장 중…';b.style.opacity=.6;}
  fetch('/api/web/biz-name',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({biz_name:v})})
  .then(_rjson).then(function(d){if(d.ok){toast(v?'업체명 저장됨 ✓':'업체명 안 넣기로 저장됨');load();}else{alert(d.detail||'저장 실패');}})
  .catch(function(){alert('네트워크 오류 · 연결 상태를 확인해 주세요');})
  .then(function(){if(b){b.textContent='저장';b.style.opacity=1;}});
}
/* ===== F-7 내 스타일 학습 ===== */
var SP_CUR='bl';
function spTab(p){SP_CUR=p;
  ['bl','ig','th'].forEach(function(x){var t=document.getElementById('spt-'+x);if(t)t.classList.toggle('on',x===p);});
  var isBl=(p==='bl');
  document.getElementById('spRefLabel').textContent = isBl?'따라할 블로그 글 주소':(p==='ig'?'따라할 인스타 계정 또는 캡션':'따라할 스레드 글');
  document.getElementById('spUrl').style.display=isBl?'block':'none';
  document.getElementById('spText').style.display=isBl?'none':'block';
  document.getElementById('spUrl').placeholder=isBl?'따라할 블로그 글 주소 (예: blog.naver.com/…/22301…)':'';
  document.getElementById('spText').placeholder=(p==='ig'?'따라하고 싶은 인스타 캡션을 그대로 붙여넣기…':'따라하고 싶은 스레드 글을 붙여넣기…');
  // 저장된 리포트 있으면 프리필 + 리포트 열기
  var st=(ME&&ME.tone_styles)||{}; var r=st[p];
  if(r){ fillReport(r); document.getElementById('spReport').style.display='block'; }
  else { document.getElementById('spReport').style.display='none'; }
  document.getElementById('spLoad').style.display='none';
  var sn=document.getElementById('spName');if(sn)sn.value='';
  renderToneLib(p);
}
function fillReport(r){
  document.getElementById('rp-persona').textContent=r.persona||'';
  document.getElementById('rp-summary').textContent=r.summary||'';
  document.getElementById('rp-flow').textContent=r.flow||'';
  document.getElementById('rp-endings').textContent=r.endings||'';
  document.getElementById('rp-tone').textContent=r.tone||'';
  document.getElementById('rp-reactions').textContent=r.reactions||'';
  var _rf=document.getElementById('rp-format');if(_rf)_rf.textContent=r.format||'';
}
function spAnalyze(){
  var url=document.getElementById('spUrl').value.trim();
  var text=document.getElementById('spText').value.trim();
  if(SP_CUR==='bl'&&!url){alert('따라할 글 주소를 넣어주세요');return;}
  if(SP_CUR!=='bl'&&!text){alert('따라할 캡션·글을 붙여넣어 주세요');return;}
  var ld=document.getElementById('spLoad');ld.style.display='block';document.getElementById('spReport').style.display='none';
  var steps=['따라할 글을 꼼꼼히 읽는 중','문장을 한 줄씩 뜯어보는 중','글이 흘러가는 순서를 파악하는 중','말투·어미 습관을 배우는 중','사장님 스타일로 정리하는 중'];var i=0;
  document.getElementById('spLoadT').textContent=steps[0]+'…';
  var iv=setInterval(function(){i++;document.getElementById('spLoadT').textContent=steps[i%steps.length]+'…';},900);
  fetch('/api/web/tone-analyze',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({platform:SP_CUR,url:url,text:text})})
  .then(_rjson).then(function(d){clearInterval(iv);ld.style.display='none';
    if(!d||d.ok!==true){alert((d&&d.detail)||'분석 실패');
      if(d&&d.need_paste){var tx=document.getElementById('spText');if(tx){tx.style.display='block';tx.focus();}var lb=document.getElementById('spRefLabel');if(lb)lb.textContent='또는 글 본문을 복사해 붙여넣기';}
      return;}
    fillReport(d.report||{});document.getElementById('spReport').style.display='block';toast('분석 완료 ✓ 필요한 부분만 고치고 저장하세요');
  }).catch(function(){clearInterval(iv);ld.style.display='none';alert('네트워크 오류 · 잠시 후 다시 시도해 주세요');});
}
function spSave(){
  var rep={persona:document.getElementById('rp-persona').innerText,summary:document.getElementById('rp-summary').innerText,
    flow:document.getElementById('rp-flow').innerText,endings:document.getElementById('rp-endings').innerText,
    tone:document.getElementById('rp-tone').innerText,reactions:document.getElementById('rp-reactions').innerText,
    format:(document.getElementById('rp-format')||{}).innerText||''};
  var nm=((document.getElementById('spName')||{}).value||'').trim();
  fetch('/api/web/tone-save',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({platform:SP_CUR,report:rep,name:nm})})
  .then(_rjson).then(function(d){if(d&&d.ok){var sn=document.getElementById('spName');if(sn)sn.value='';toast('저장했어요 ✓ 라이브러리에 추가·바로 사용중');load();}else{alert((d&&d.detail)||'저장 실패');}}).catch(function(){alert('네트워크 오류');});}
function renderToneLib(p){
  var box=document.getElementById('spLib'); if(!box)return;
  var lib=((ME&&ME.tone_library)||{})[p]||[];
  if(!lib.length){box.innerHTML='';return;}
  var rows=lib.map(function(s){
    var rb=s.active?'border:1px solid var(--violet);background:rgba(110,95,199,.07)':'border:1px solid var(--line)';
    return '<div style="display:flex;align-items:center;gap:10px;padding:10px 12px;border-radius:11px;margin-bottom:7px;'+rb+'">'
      +'<div style="flex:1;min-width:0"><div style="font-size:13.5px;font-weight:800;color:'+(s.active?'var(--violet)':'var(--ink)')+';white-space:nowrap;overflow:hidden;text-overflow:ellipsis">'+(s.active?'✓ ':'')+esc(s.name)+'</div><div style="font-size:11px;color:var(--ink3);margin-top:1px">'+ago(s.created_at_ms)+'</div></div>'
      +(s.active?'<span style="font-size:11px;font-weight:800;color:var(--violet);background:rgba(110,95,199,.12);border:1px solid var(--violet);border-radius:7px;padding:3px 9px;flex:none">사용중</span>':'<button onclick="spActivate('+s.id+')" style="font-size:12px;font-weight:800;color:#fff;background:var(--violet);border:none;border-radius:8px;padding:6px 12px;cursor:pointer;flex:none">사용하기</button>')
      +'<button onclick="spDelLib('+s.id+')" title="삭제" style="font-size:13px;background:none;border:none;cursor:pointer;opacity:.5;flex:none;padding:2px">🗑</button></div>';
  }).join('');
  box.innerHTML='<div class="flabel" style="margin-top:14px">📚 저장된 내 스타일 <span style="color:var(--ink3);font-weight:700">· 눌러서 골라 쓰기</span></div>'+rows;
}
function spActivate(id){fetch('/api/web/tone-activate',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({platform:SP_CUR,id:id})})
  .then(_rjson).then(function(d){if(d&&d.ok){toast('이 스타일로 바꿨어요 ✓ 이제 글이 이 톤으로 나와요');load();}else{alert((d&&d.detail)||'실패');}}).catch(function(){alert('네트워크 오류');});}
function spDelLib(id){if(!confirm('이 스타일을 삭제할까요?'))return;fetch('/api/web/tone-lib?id='+id,{method:'DELETE'})
  .then(_rjson).then(function(d){if(d&&d.ok){toast('삭제했어요');load();}else{alert('실패');}}).catch(function(){alert('네트워크 오류');});}
function toggleAct(){var b=document.getElementById('actBody'),a=document.getElementById('actArw');var on=b.style.display==='none';b.style.display=on?'block':'none';if(a)a.style.transform=on?'rotate(90deg)':'';}
function logoutSid(sid){if(!confirm('이 기기를 로그아웃할까요?'))return;fetch('/api/web/logout-session?sid='+encodeURIComponent(sid),{method:'POST'}).then(function(){toast('로그아웃했어요');load();});}
function logoutAll(){if(!confirm('모든 기기에서 로그아웃할까요? (이 PC 포함)'))return;fetch('/api/web/logout-all?owner_phone='+encodeURIComponent(ME.owner_phone),{method:'POST'}).then(function(){location.href='/web/login';});}
function thisLogout(){var cur=(ME&&ME.sessions||[]).filter(function(s){return s.is_current;})[0];if(cur){fetch('/api/web/logout-session?sid='+encodeURIComponent(cur.sid),{method:'POST'}).then(function(){location.href='/web/login';});}else{location.href='/web/login';}}
document.querySelectorAll('.nav a[href]').forEach(function(a){a.onclick=function(){document.querySelectorAll('.nav a').forEach(function(x){x.classList.remove('on')});a.classList.add('on');};});
var tt;function toast(m){var el=document.createElement('div');el.textContent=m;el.style.cssText='position:fixed;left:50%;bottom:34px;transform:translateX(-50%);background:rgba(24,29,39,.94);color:#fff;font-size:13px;font-weight:700;padding:11px 20px;border-radius:999px;z-index:99';document.body.appendChild(el);clearTimeout(tt);tt=setTimeout(function(){el.remove();},1500);}
load();
