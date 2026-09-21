
var _ndm=new Date(); var ym=_ndm.getFullYear()+'-'+(_ndm.getMonth()<9?'0':'')+(_ndm.getMonth()+1);   /* 로컬(KST) 현재 월 — toISOString(UTC)은 월초 새벽에 지난달이 떠서. (2026-08-24 사장님) */
var sites=[], curCd=null, curCust=null, photos=[], sel={}, filter='all', lbi=0, mTab='photos', MAT=null, partsEditMode=false;
var HAS_KEY=("__SGM_HASKEY__"==="true");   /* serve 시점 주입(web_viewer). 키 없으면 글쓰기 대신 등록 카드 */
var PARTS_KEY='web_parts_v1', SELPART_KEY='web_sel_part', PT_KEY='web_partof_v1', BA_KEY='web_ba_v1';
var photoSort='work';   /* 사진 정렬: work=시공순(시공전-밑작업-시공후) / upload=올린순 */
var DEFAULT_PARTS=['거실화장실','안방화장실','거실타일','베란다','다용도실','현관','기타'];
function esc(s){return String(s==null?'':s).replace(/[&<>"']/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c];});}
function pad(n){return (n<10?'0':'')+n;}
var _TODAY=(function(){var d=new Date();return d.getFullYear()+'-'+pad(d.getMonth()+1)+'-'+pad(d.getDate());})();   /* 로컬 오늘 'YYYY-MM-DD' — 접속 시 오늘로 시작. (2026-08-24 사장님) */
function digits(s){return String(s||'').replace(/[^0-9]/g,'');}
function getParts(){try{var v=JSON.parse(localStorage.getItem(PARTS_KEY));if(Array.isArray(v)&&v.length)return v;}catch(e){}return DEFAULT_PARTS.slice();}
function setParts(a){localStorage.setItem(PARTS_KEY,JSON.stringify(a));}
function selPart(){return localStorage.getItem(SELPART_KEY)||'';}
function getPt(){try{return JSON.parse(localStorage.getItem(PT_KEY))||{};}catch(e){return {};}}
function partFor(id){return getPt()[id]||'';}
function saveTag(id){var p=getPt()[id]||'',b=getBa()[id]||'',r=getRot()[id]||0;fetch('/api/web/tag',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({photo_id:parseInt(id,10),part:p,ba:b,rot:r})}).catch(function(){});}
var ROT_KEY='web_rot_v1';
function getRot(){try{return JSON.parse(localStorage.getItem(ROT_KEY))||{};}catch(e){return {};}}
function rotFor(p){var m=getRot(),id=p.photo_id;return (m[id]!=null?m[id]:(p.rot||0));}
function photoSrc(p,base){var r=rotFor(p);base=base||p.thumb_url;return r?(base+(base.indexOf('?')<0?'?':'&')+'rot='+r):base;}
function rotatePhoto(id){var pp=photos.filter(function(x){return x.photo_id===id;})[0];var cur=pp?rotFor(pp):(getRot()[id]||0);var nw=(cur+90)%360;var m=getRot();if(nw)m[id]=nw;else delete m[id];localStorage.setItem(ROT_KEY,JSON.stringify(m));saveTag(id);try{lbShow();}catch(e){}renderPhotos();if(GEN&&genP==='bl')drawGen();}
function getBa(){try{return JSON.parse(localStorage.getItem(BA_KEY))||{};}catch(e){return {};}}
function baFor(p){var m=getBa();return m[p.photo_id]||p.ba_guess;}
function baLabel(v){return v==='mid'?'밑작업':(v==='after'?'시공 후':'시공 전');}
function baNext(v){return v==='before'?'mid':(v==='mid'?'after':'before');}   // 시공전 → 밑작업 → 시공후 → …
function baCls(v){return v==='after'?'post':(v==='mid'?'mid':'');}
function flipBa(id){var m=getBa(),cur=m[id];if(!cur){var f=photos.filter(function(x){return x.photo_id===id;})[0];cur=f?f.ba_guess:'before';}m[id]=baNext(cur);localStorage.setItem(BA_KEY,JSON.stringify(m));saveTag(id);}
function toast(msg){var t=document.getElementById('wtoast');if(!t){t=document.createElement('div');t.id='wtoast';t.style.cssText='position:fixed;left:50%;bottom:30px;transform:translateX(-50%);background:rgba(11,15,25,.92);color:#fff;padding:11px 18px;border-radius:11px;font-size:13px;font-weight:700;z-index:99999;opacity:0;transition:opacity .2s;box-shadow:0 8px 24px rgba(0,0,0,.3)';document.body.appendChild(t);}t.textContent=msg;t.style.opacity='1';clearTimeout(t._h);t._h=setTimeout(function(){t.style.opacity='0';},1900);}

function moveMonth(d){var p=ym.split('-');var dt=new Date(+p[0],+p[1]-1+d,1);ym=dt.getFullYear()+'-'+pad(dt.getMonth()+1);load();}
function load(){
  var yy=ym.split('-')[0], mm=+ym.split('-')[1];
  document.getElementById('calM').textContent=yy+'년 '+mm+'월';
  document.getElementById('ymChip').textContent='📅 '+yy+'년 '+mm+'월';
  fetch('/api/web/calendar?month='+ym).then(function(r){if(r.status===401){location.href='/web/login';throw 0;}return r.json();}).then(function(cal){
    fetch('/api/web/sites?month='+ym).then(function(r){return r.json();}).then(function(s){
      sites=s.sites||[]; renderCal(cal.days||[]); renderDayList();
      if(sites.length){var _ts=sites.filter(function(x){return x.work_date===_TODAY;})[0];openSite((_ts||sites[0]).customer_digits);} else {curCd=null;curCust=null;photos=[];document.getElementById('colM').innerHTML='<div class="empty">이 달에는 시공 현장이 없어요.</div>';renderRight();}
    });
  }).catch(function(){});
}
function renderCal(days){
  var map={}; days.forEach(function(d){map[d.date]=d;});
  var p=ym.split('-'),y=+p[0],m=+p[1];
  var first=new Date(y,m-1,1).getDay(), last=new Date(y,m,0).getDate();
  var selDay=(curCust&&curCust.work_date)?curCust.work_date:'';
  var h='';
  for(var i=0;i<first;i++)h+='<div class="d"></div>';
  for(var dd=1;dd<=last;dd++){
    var ds=ym+'-'+pad(dd), info=map[ds];
    var cls='d'+(info?' has':'')+(info&&info.hasPhoto?' pic':'')+(ds===selDay?' sel':'')+(ds===_TODAY?' today':'');
    var oc=info?(' onclick="jumpDay(\''+ds+'\')"'):'';
    h+='<div class="'+cls+'"'+oc+'>'+dd+(info?'<span class="dt"></span>':'')+'</div>';
  }
  var cal=document.getElementById('cal');
  cal.querySelectorAll('.d').forEach(function(e){e.remove();});
  cal.insertAdjacentHTML('beforeend',h);
}
function jumpDay(ds){var s=sites.filter(function(x){return x.work_date===ds;});if(s.length)openSite(s[0].customer_digits);}
function filteredSites(){var q=(document.getElementById('q').value||'').trim();if(!q)return sites;return sites.filter(function(s){return (s.apartment+' '+s.name).indexOf(q)>=0;});}
function renderDayList(){
  var list=filteredSites();
  document.getElementById('dlcnt').textContent=list.length+'곳 · 최근순';
  var h='';
  list.forEach(function(s){
    var mo=(s.work_date||'').slice(5,7), dy=(s.work_date||'').slice(8,10);
    var st=s.completed?'<span class="st done">완료</span>':'<span class="st go">진행중</span>';
    var cam=s.photo_count?('📷 '+s.photo_count):'';
    var pb=s.has_post?'<span class="st wrote">✍️ 글씀</span>':'';
    h+='<div class="site'+(s.customer_digits===curCd?' on':'')+'" onclick="openSite(\''+s.customer_digits+'\')">'
      +'<div class="dd"><div class="mm">'+(mo?(+mo)+'월':'')+'</div><div class="n">'+(dy?(+dy):'-')+'</div></div>'
      +'<div class="mid"><div class="nm">'+esc(s.apartment||s.name||'현장')+'</div><div class="sub">'+esc((s.name||'')+(s.category?' · '+s.category:'')+(cam?' · '+cam:''))+'</div></div>'
      +'<div class="stcol">'+pb+st+'</div></div>';
  });
  document.getElementById('drows').innerHTML=h||'<div style="color:var(--ink3);font-size:12px;padding:8px 2px">현장이 없어요.</div>';
}
function openSite(cd){
  curCd=cd; sel={}; filter='all'; mTab='photos'; MAT=null; GEN=null; genP='bl';
  fetch('/api/web/site/'+encodeURIComponent(cd)).then(function(r){if(r.status===401){location.href='/web/login';throw 0;}return r.json();}).then(function(d){
    curCust=d.customer||{}; photos=d.photos||[];
    sel={}; photos.forEach(function(p){sel[p.photo_id]=1;});   /* 기본 전체선택 — 어차피 다 넣으니 뺄 것만 빼기(사장님 2026-08-19) */
    var _m=getPt(),_b=getBa(),_ch=false;photos.forEach(function(p){if(p.part){_m[p.photo_id]=p.part;_ch=true;}if(p.ba){_b[p.photo_id]=p.ba;_ch=true;}});if(_ch){localStorage.setItem(PT_KEY,JSON.stringify(_m));localStorage.setItem(BA_KEY,JSON.stringify(_b));}
    GEN=null; genOrder=[]; renderMid(); renderDayList(); renderRight(); loadONote(); loadLastPost(); loadPostHist(cd);
    renderCal_fromCurrent();
  }).catch(function(){});
}
function renderCal_fromCurrent(){ fetch('/api/web/calendar?month='+ym).then(function(r){return r.json();}).then(function(cal){renderCal(cal.days||[]);}); }
function counts(){var c={all:photos.length,owner:0,team:0,partner:0};photos.forEach(function(p){c[p.uploader_kind]=(c[p.uploader_kind]||0)+1;});return c;}
/* ===== 가운데 (사진 / 이 현장은) ===== */
function renderMid(){
  var c=curCust;
  var head=''
   +'<div class="mh">'+esc((c.apartment||c.name||'현장')+(c.category?' · '+c.category+' 시공':''))+'</div>'
   +'<div class="msub">'+esc((c.name?'고객 '+c.name+' · ':'')+(c.completed?'시공 완료':'진행중'))+'</div>'
   +'<div class="mchips">'+(c.work_date?'<span class="c">📅 시공일 '+esc(c.work_date)+'</span>':'')+'<span class="c'+(c.completed?' g':'')+'">'+(c.completed?'✅ 시공 완료':'⏳ 진행중')+'</span>'+(c.dong_ho?'<span class="c">🏠 '+esc(c.dong_ho)+'</span>':'')+'</div>'
   +'<div class="mtabs">'
   +'<button class="mtab'+(mTab==='photos'?' on':'')+'" id="mt-photos" onclick="mtab(\'photos\')">📷 사진 '+photos.length+'</button>'
   +'<button class="mtab'+(mTab==='story'?' on':'')+'" id="mt-story" onclick="mtab(\'story\')">🧭 이 현장은</button>'
   +'</div>'
   +'<div class="mpane" id="mp-photos"'+(mTab==='photos'?'':' style="display:none"')+'></div>'
   +'<div class="mpane" id="mp-story"'+(mTab==='story'?'':' style="display:none"')+'></div>';
  document.getElementById('colM').innerHTML=head;
  renderPhotosPane();
  if(mTab==='story')renderStory();
}
function mtab(w){mTab=w;
  document.getElementById('mp-photos').style.display=(w==='photos')?'block':'none';
  document.getElementById('mp-story').style.display=(w==='story')?'block':'none';
  document.getElementById('mt-photos').classList.toggle('on',w==='photos');
  document.getElementById('mt-story').classList.toggle('on',w==='story');
  if(w==='story')renderStory();
}
function renderPhotosPane(){
  var cc=counts();
  var h=''
   +'<div class="viewonly">👁️ <b>보기 전용</b> — 보고·다운로드만. 사진 수정·삭제는 폰 앱에서만. 부위 태그는 다운로드 이름에만.</div>'
   +'<div class="chips">'
   +'<span class="chipf'+(filter==='all'?' on':'')+'" onclick="setFilter(\'all\')">전체 '+cc.all+'</span>'
   +'<span class="chipf'+(filter==='owner'?' on':'')+'" onclick="setFilter(\'owner\')"><i class="owner"></i>사장님 '+(cc.owner||0)+'</span>'
   +'<span class="chipf'+(filter==='team'?' on':'')+'" onclick="setFilter(\'team\')"><i class="team"></i>팀원 '+(cc.team||0)+'</span>'
   +'<span class="chipf'+(filter==='partner'?' on':'')+'" onclick="setFilter(\'partner\')"><i class="partner"></i>협업 사장 '+(cc.partner||0)+'</span>'
   +'</div>'
   +'<div class="seg"><span>시공 전</span> · <span>밑작업</span> · <span class="on">시공 후</span> — 자동 구분, 태그 탭으로 바꿔요</div>'
   +'<div class="chips" style="margin-top:8px;align-items:center"><span class="chipf'+(photoSort==='work'?' on':'')+'" onclick="setSort(\'work\')">🔀 시공순</span><span class="chipf'+(photoSort==='upload'?' on':'')+'" onclick="setSort(\'upload\')">올린순</span><span style="font-size:11px;color:var(--ink3);margin-left:2px">시공전→밑작업→시공후 순으로</span></div>'
   +'<div class="grid" id="photos"></div>';
  if(photos.length){
    h+='<div class="partpick"><div class="h">📌 <b>부위 찍는 법</b> — ① 위에서 <b>사진을 클릭</b>해 고르고 → ② 아래 <b>부위를 클릭</b>하면 찍혀요 (다운로드 파일명에 들어가요)<br>💡 부위가 <b>자세할수록 원고 퀄이 올라가요</b> (예: "안방 화장실 바닥 케라폭시") · 목록은 사장님이 직접 정해요</div>'
      +'<div class="editnote" id="editnote" style="display:none">✏️ <b>편집 중</b> — 부위마다 <b>✕</b> 로 빼기 · <b>[✓ 완료]</b> 누르면 끝</div>'
      +'<div class="parts" id="parts"></div></div>'
      +'<div class="dlbar"><div class="fname" id="fname"></div>'
      +'<button class="dlbtn ghost2" onclick="selectAll()">전체 선택</button>'
      +'<button class="dlbtn" onclick="download()" id="dlbtn">📥 다운로드</button></div>'
      +'<div class="dlmore">여러 장 = zip · 한 장은 바로 · 파일명 = <b>시공일자_아파트명_부위_번호</b></div>';
  }
  document.getElementById('mp-photos').innerHTML=h;
  renderPhotos();
  if(photos.length){renderParts();updBar();}
}
function setFilter(f){filter=f;renderPhotosPane();}
function setSort(s){photoSort=s;renderPhotosPane();}
function renderPhotos(){
  var g=''; var list=photos.filter(function(p){return filter==='all'||p.uploader_kind===filter;});
  if(photoSort==='work'){var _bo={before:0,mid:1,after:2};list=list.slice().sort(function(a,b){var da=(_bo[baFor(a)]==null?9:_bo[baFor(a)]),db=(_bo[baFor(b)]==null?9:_bo[baFor(b)]);return da!==db?da-db:(photos.indexOf(a)-photos.indexOf(b));});}
  list.forEach(function(p){
    var up=p.uploader_kind, upc=up==='owner'?'owner':(up==='partner'?'partner':'team');
    var _un=(p.uploader_name||'').trim();
    var uptxt=up==='owner'?'👤 사장님':(up==='partner'?(!_un||/협업/.test(_un)?'🤝 협업 사장':'🤝 협업·'+esc(_un)):'👤 '+esc(_un));
    var _v=baFor(p), ba=baLabel(_v);
    g+='<div class="ph'+(sel[p.photo_id]?' on':'')+'" onclick="tog('+p.photo_id+')" ondblclick="lbOpen('+p.photo_id+')" title="클릭=선택 · 더블클릭=크게보기">'
      +'<div class="im"><img loading="lazy" src="'+photoSrc(p,p.thumb_url)+'">'
      +'<span class="pick'+(sel[p.photo_id]?' on':'')+'" onclick="event.stopPropagation();tog('+p.photo_id+')">✓</span>'
      +'<span class="up '+upc+'">'+uptxt+'</span>'
      +'<span class="tag '+baCls(_v)+'" onclick="event.stopPropagation();flipBa('+p.photo_id+');renderPhotos();updBar();" title="탭 = 시공전 → 밑작업 → 시공후">'+ba+' 🔄</span>'
      +'<span class="dl" onclick="event.stopPropagation();dl1('+p.photo_id+')">⬇</span></div>'
      +'<div class="cap">'+(partFor(p.photo_id)?'<span class="part">'+esc(partFor(p.photo_id))+'</span>':'')+'<span class="fn">'+esc(fnPreview(p,0))+'</span></div></div>';
  });
  document.getElementById('photos').innerHTML=g||'<div class="empty" style="grid-column:1/-1">사진이 아직 없어요.</div>';
}
function fnPreview(p,idx){
  var ymd=digits(curCust.work_date).slice(0,8)||'00000000';
  var apt=(curCust.apartment||'현장').replace(/[\/:*?"<>|]/g,'')
    .replace(/[0-9A-Za-z]+\s*동\s*[0-9]+\s*호/g,'')
    .replace(/[0-9]+\s*층\s*[0-9]+\s*호/g,'')
    .replace(/[0-9]+\s*[-/]\s*[0-9]+\s*호/g,'')
    .replace(/[0-9]+\s*동(?!\s*로)/g,'')
    .replace(/[0-9]+\s*호/g,'')
    .replace(/\s{2,}/g,' ').trim()||'현장';
  var part=(p&&p.photo_id)?partFor(p.photo_id):'';
  return ymd+'_'+apt+(part?'_'+part:'')+'_'+pad(idx||1)+'.jpg';
}
function tog(id){if(sel[id])delete sel[id];else sel[id]=1;renderPhotos();updBar();}
function selectAll(){var list=photos.filter(function(p){return filter==='all'||p.uploader_kind===filter;});var allsel=list.every(function(p){return sel[p.photo_id];});list.forEach(function(p){if(allsel)delete sel[p.photo_id];else sel[p.photo_id]=1;});renderPhotos();updBar();}
function updBar(){
  var ids=Object.keys(sel), n=ids.length;
  var _pp=ids.map(partFor).filter(Boolean), _pt=(_pp.length&&_pp.every(function(x){return x===_pp[0];}))?_pp[0]:'', _apt=(curCust.apartment||'현장').replace(/[\/:*?"<>|]/g,''), ex=(digits(curCust.work_date).slice(0,8)||'00000000')+'_'+_apt+(_pt?'_'+_pt:'')+'_01.jpg', mb=(n*0.4).toFixed(1);
  var fn=document.getElementById('fname'); if(fn)fn.innerHTML='☑ <b>'+n+'장 선택</b> · <b>'+esc(ex)+'</b> 처럼 · 약 '+mb+'MB';
  var db=document.getElementById('dlbtn'); if(db)db.textContent=n>1?('📥 선택 '+n+'장 다운로드'):'📥 다운로드';
  updateGenHint();
}
function renderParts(){
  var _sids=Object.keys(sel), _sp=_sids.map(partFor), sp=(_sids.length&&_sp.every(function(x){return x&&x===_sp[0];}))?_sp[0]:'', parts=getParts(), h='';
  if(partsEditMode){
    parts.forEach(function(p,i){h+='<span class="pchip ed">'+esc(p)+'<span class="x" onclick="delPart('+i+')" title="빼기">✕</span></span>';});
    h+='<span class="pchip add" onclick="addPart()">＋ 추가</span><span class="pchip done" onclick="toggleEdit()">✓ 완료</span>';
  } else {
    parts.forEach(function(p,i){h+='<span class="pchip'+(p===sp?' on':'')+'" onclick="pickPart('+i+')">'+esc(p)+'</span>';});
    h+='<span class="pchip add" onclick="addPart()">＋ 부위 추가</span><span class="pchip add" onclick="toggleEdit()">✏️ 편집</span>';
  }
  document.getElementById('parts').innerHTML=h;
}
function toggleEdit(){ partsEditMode=!partsEditMode; var note=document.getElementById('editnote'); if(note)note.style.display=partsEditMode?'block':'none'; renderParts(); }
function pickPart(i){var ids=Object.keys(sel),v=getParts()[i];if(!ids.length){toast('먼저 사진을 골라주세요 — 사진을 클릭하면 선택돼요');return;}var m=getPt(),same=ids.every(function(id){return m[id]===v;});ids.forEach(function(id){if(same)delete m[id];else m[id]=v;});localStorage.setItem(PT_KEY,JSON.stringify(m));ids.forEach(saveTag);sel={};renderParts();renderPhotos();updBar();toast(same?('「'+v+'」 부위 해제 ('+ids.length+'장)'):('선택한 '+ids.length+'장에 「'+v+'」 부위 찍음 ✓'));}
function addPart(){var n=(prompt('추가할 부위 이름')||'').trim();if(!n)return;var p=getParts();if(p.indexOf(n)<0){p.push(n);setParts(p);}renderParts();}
function delPart(i){var p=getParts(),v=p[i];if(!confirm('"'+v+'" 부위를 지울까요?'))return;p.splice(i,1);setParts(p);if(selPart()===v)localStorage.removeItem(SELPART_KEY);renderParts();renderPhotos();updBar();}
function dlUrl(ids){var ps=ids.map(function(id){return partFor(id);});return '/api/web/download?ids='+ids.join(',')+'&parts='+encodeURIComponent(ps.join('|'));}
function dl1(id){location.href=dlUrl([id]);}
function download(){var ids=Object.keys(sel);if(!ids.length){alert('내려받을 사진을 골라주세요.');return;}location.href=dlUrl(ids);}
/* ===== 이 현장은 ===== */
function renderStory(){
  var el=document.getElementById('mp-story'); if(!el)return;
  el.innerHTML='<div style="color:var(--ink3);font-size:13px;padding:8px 2px">재료 불러오는 중…</div>';
  if(MAT){drawStory(MAT);return;}
  fetch('/api/web/materials?customer_digits='+encodeURIComponent(curCd)).then(function(r){return r.json();}).then(function(m){MAT=m;drawStory(m);}).catch(function(){el.innerHTML='<div style="color:var(--ink3);font-size:13px;padding:8px 2px">재료를 불러오지 못했어요.</div>';});
}
function drawStory(m){
  var el=document.getElementById('mp-story'); if(!el)return;
  var per=(GEN&&GEN.persona)?GEN.persona:'오른쪽에서 [글 만들기]를 누르면, 이 현장의 고민→상담→시공 흐름을 한 줄로 정리해 드려요.';
  function mat(icon,title,val,sub){
    var has=val&&val.trim();
    return '<div class="mat"><div class="mt">'+icon+' '+title+(sub?' <span style="font-size:10px;font-weight:700;color:var(--ink3)">'+sub+'</span>':'')+'</div>'
      +'<div class="mb'+(has?'':' empty')+'">'+(has?esc(val):'아직 없어요.')+'</div></div>';
  }
  var callVal=m.call&&m.call.trim()?m.call:'', callSub=callVal?'':'(앱 연동 예정)';
  var h='<div class="persona-big"><span class="ic">🧭</span><div><div class="k">이 현장은</div><div class="tx">'+esc(per)+'</div></div></div>'
   +'<div class="matlabel">글의 재료 (이걸로 오른쪽에서 글이 나와요)</div>'
   +mat('📞','통화 요약',callVal,callSub)
   +mat('📝','내 메모',m.memo,'(고객상세에서)')
   +mat('💬','문자 대화',m.convo,'')
   +'<div class="mattip">🔒 글로 나올 땐 이름·전화·동/호수·정확 주소 자동 제거("'+esc(m.region||'지역')+'" 수준). · ✨ 재료가 문장이 아니어도(단편·키워드) AI가 이해해서 반영해요.</div>';
  el.innerHTML=h;
}
/* ===== 오른쪽: 글 생성 (플랫폼 탭) ===== */
var GEN=null, genP='bl';
var PMETA={bl:{cls:'bl',name:'블로그',meta:'📝 블로그 · 네이버 · 길게',btn:'✨ 블로그 글 만들기',load:'✍️ 블로그 글 쓰는 중…',steps:['재료 읽는 중 (메모·문자)','내 톤에 맞추는 중','초안 쓰는 중']},
  ig:{cls:'ig',name:'인스타',meta:'📷 인스타그램 · 짧게 + 해시태그 15',btn:'✨ 인스타 캡션 만들기',load:'✍️ 인스타 캡션 쓰는 중…',steps:['재료 읽는 중','감성 톤·이모지 맞추는 중','해시태그 뽑는 중']},
  th:{cls:'th',name:'스레드',meta:'🧵 스레드 · 대화체 + 해시태그 1~2',btn:'✨ 스레드 글 만들기',load:'✍️ 스레드 글 쓰는 중…',steps:['재료 읽는 중','대화체 톤 맞추는 중','초안 쓰는 중']}};
function ptab(p){genP=p;['bl','ig','th'].forEach(function(x){document.getElementById('pt-'+x).classList.toggle('on',x===p);});renderRight();}
/* ═══ 뷰어에서 바로 스타일(톤) 골라 바꾸기 — 마이페이지 라이브러리 재활용 ═══ */
var TONELIB={};
function loadToneLib(){fetch('/api/web/tone-list').then(function(r){return r.json();}).then(function(j){TONELIB=(j&&!j.error)?j:{};if(curCd)renderRight();}).catch(function(){});}
function toneActiveName(p){var a=(TONELIB[p]||[]).filter(function(s){return s.active;})[0];return a?a.name:'';}
function toggleTonePop(e){e.stopPropagation();var pop=document.getElementById('tonePop');if(!pop)return;if(pop.classList.contains('on')){pop.classList.remove('on');return;}renderTonePop();pop.classList.add('on');}
function renderTonePop(){var pop=document.getElementById('tonePop');if(!pop)return;
  var list=TONELIB[genP]||[];
  var rows=list.map(function(s){return '<div class="tprow'+(s.active?' on':'')+'" onclick="pickTone('+s.id+')">'+(s.active?'✓ ':'')+esc(s.name)+(s.active?'<span class="tpb">사용중</span>':'')+'</div>';}).join('')||'<div class="tpempty">저장된 스타일이 없어요<br>마이페이지에서 학습해요</div>';
  pop.innerHTML=rows+'<div class="tpmng" onclick="openStyleModal()">✏️ 새 스타일 학습 · 관리</div>';}
function pickTone(id){fetch('/api/web/tone-activate',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({platform:genP,id:id})}).then(function(r){return r.json();}).then(function(j){if(j&&j.ok){(TONELIB[genP]||[]).forEach(function(s){s.active=(s.id===id);});renderRight();toast('이 스타일로 바꿨어요 ✓ 이제 이 톤으로 나와요');}}).catch(function(){});}
document.addEventListener('click',function(e){var pop=document.getElementById('tonePop');if(pop&&pop.classList.contains('on')&&!pop.contains(e.target))pop.classList.remove('on');});
/* ═══ 이전 원고 히스토리 (재생성해도 쌓임 · 최근 20) ═══ */
var POSTHIST=[];
function ago(ms){if(!ms)return '방금 전';var d=Date.now()-ms;var m=Math.floor(d/60000);if(m<1)return '방금 전';if(m<60)return m+'분 전';var h=Math.floor(m/60);if(h<24)return h+'시간 전';return Math.floor(h/24)+'일 전';}
function loadPostHist(cd){if(!cd)return;var jc=cd;fetch('/api/web/post-history?customer_digits='+encodeURIComponent(cd)).then(function(r){return r.json();}).then(function(j){POSTHIST=(j&&j.items)||[];if(curCd===jc)renderRight();}).catch(function(){});}
function toggleHistPop(e){e.stopPropagation();var p=document.getElementById('histPop');if(!p)return;if(p.classList.contains('on')){p.classList.remove('on');return;}
  p.innerHTML=POSTHIST.map(function(h,i){return '<div class="histrow" onclick="loadPostItem('+i+')"><div class="histt">'+esc(h.title||'(제목 없음)')+'</div><div class="histm">'+ago(h.created_at_ms)+' · '+(h.chars||0)+'자</div></div>';}).join('')||'<div class="histempty">아직 없어요</div>';
  p.classList.add('on');}
function loadPostItem(i){var h=POSTHIST[i];if(!h)return;
  GEN={restored:true,persona:'',region:'',blog:{title:h.title||'',body:h.draft||'',chars:(h.draft||'').length},photos:h.photos||[],instagram:null,threads:null};
  genOrder=(h.photos||[]).map(function(p){return p.photo_id;}).filter(Boolean);
  var pp=document.getElementById('histPop');if(pp)pp.classList.remove('on');
  drawGen();var b=document.querySelector('#rbody .genbig');if(b)b.textContent='↻ 다시 만들기';
  fetch('/api/web/save-draft',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({customer_digits:curCd,draft:h.draft||'',title:h.title||''})}).catch(function(){});
  toast('이전 원고를 불러왔어요');}
document.addEventListener('click',function(e){var p=document.getElementById('histPop');if(p&&p.classList.contains('on')&&!p.contains(e.target))p.classList.remove('on');});
/* ═══ 새 스타일 학습 모달 (뷰어 안에서 · 마이페이지 안 가도 됨) ═══ */
function openStyleModal(){var pp=document.getElementById('tonePop');if(pp)pp.classList.remove('on');
  document.getElementById('smUrl').value='';document.getElementById('smText').value='';document.getElementById('smText').style.display='none';
  var nm=document.getElementById('smName');if(nm)nm.value='';
  document.getElementById('smReport').style.display='none';document.getElementById('smLoad').style.display='none';
  var pl=document.getElementById('smPlat');if(pl)pl.textContent=(genP==='ig'?'인스타':genP==='th'?'스레드':'블로그');
  document.getElementById('styleModal').classList.add('on');setTimeout(function(){var u=document.getElementById('smUrl');if(u)u.focus();},60);}
function closeStyleModal(){document.getElementById('styleModal').classList.remove('on');}
function smFill(r){['persona','summary','flow','endings','tone','reactions','format'].forEach(function(k){var el=document.getElementById('sm-'+k);if(el)el.textContent=r[k]||'';});}
function smAnalyze(){
  var url=document.getElementById('smUrl').value.trim(),text=document.getElementById('smText').value.trim();
  if(!url&&!text){alert('따라할 글 주소를 넣어주세요');return;}
  var ld=document.getElementById('smLoad');ld.style.display='block';document.getElementById('smReport').style.display='none';
  var steps=['따라할 글을 꼼꼼히 읽는 중','문장을 한 줄씩 뜯어보는 중','글 흐름을 파악하는 중','말투·형식을 배우는 중','정리하는 중'],i=0;
  document.getElementById('smLoadT').textContent=steps[0]+'…';
  var iv=setInterval(function(){i++;document.getElementById('smLoadT').textContent=steps[i%steps.length]+'…';},900);
  fetch('/api/web/tone-analyze',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({platform:genP,url:url,text:text})})
  .then(function(r){return r.json();}).then(function(d){clearInterval(iv);ld.style.display='none';
    if(!d||d.ok!==true){alert((d&&d.detail)||'분석 실패');if(d&&d.need_paste){var tx=document.getElementById('smText');if(tx){tx.style.display='block';tx.focus();}}return;}
    smFill(d.report||{});var rp=document.getElementById('smReport');rp.style.display='block';rp.classList.remove('slidein');void rp.offsetWidth;rp.classList.add('slidein');
  }).catch(function(){clearInterval(iv);ld.style.display='none';alert('네트워크 오류 · 잠시 후 다시');});}
function smSave(){
  var rep={};['persona','summary','flow','endings','tone','reactions','format'].forEach(function(k){var el=document.getElementById('sm-'+k);rep[k]=el?el.innerText:'';});
  var nm=(document.getElementById('smName').value||'').trim();
  fetch('/api/web/tone-save',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({platform:genP,report:rep,name:nm})})
  .then(function(r){return r.json();}).then(function(d){if(d&&d.ok){closeStyleModal();loadToneLib();toast('스타일 저장 ✓ 바로 사용중이에요');}else{alert((d&&d.detail)||'저장 실패');}}).catch(function(){alert('네트워크 오류');});}
var ONOTE='';
function onoteChanged(){ var ta=document.getElementById('onoteTa'); if(ta)ONOTE=ta.value; saveONoteSoon(); }
var _onoteT;
function saveONoteSoon(){ clearTimeout(_onoteT); _onoteT=setTimeout(saveONoteNow,900); }
function saveONoteNow(){ if(!curCd)return; fetch('/api/web/owner-note',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({customer_digits:curCd, note:ONOTE})}).catch(function(){}); }
function loadONote(){ ONOTE=''; var ta=document.getElementById('onoteTa'); if(ta)ta.value=''; if(!curCd)return;
  fetch('/api/web/owner-note?customer_digits='+encodeURIComponent(curCd)).then(function(r){return r.json();}).then(function(j){ ONOTE=(j&&j.note)||''; var t=document.getElementById('onoteTa'); if(t)t.value=ONOTE; }).catch(function(){}); }
/* 현장 열 때 '최근 만든 블로그 글' 복원(실수로 닫아도 다시 안 만들게) */
function loadLastPost(){
  if(!curCd)return; var jobCd=curCd;
  fetch('/api/web/last-post?customer_digits='+encodeURIComponent(curCd)).then(function(r){return r.json();}).then(function(j){
    if(curCd!==jobCd||!j||!j.has)return;
    GEN={restored:true, persona:'', region:'', blog:{title:j.title||'', body:j.draft||'', chars:(j.draft||'').length}, photos:j.photos||[], instagram:null, threads:null};
    genOrder=(j.photos||[]).map(function(p){return p.photo_id;}).filter(Boolean);
    var b=document.querySelector('#rbody .genbig'); if(b)b.textContent='↻ 다시 만들기';
    if(genP==='bl')drawGen();
  }).catch(function(){});
}
function updateGenHint(){
  var el=document.getElementById('genHint'); if(!el)return;
  var done=!!(curCust&&curCust.completed);
  if(GEN||!done){ el.style.display='none'; return; }
  var n=Object.keys(sel).length;
  if(n===0){ el.className='genhint pick'; el.innerHTML='☑ 사진을 다 뺐어요 <span class="gh2">(사진을 눌러 다시 넣거나 · 사진 없이 글만 만들 수 있어요)</span>'; }
  else { el.className='genhint ok'; el.innerHTML='☑ 사진 <b>'+n+'장</b> 다 넣을 거예요 · <b>뺄 사진은 눌러서 빼세요</b> — 아래 <b>[블로그 글 만들기]</b>'; }
  el.style.display='block';
}
function goMypage(){location.href='/mypage';}
function renderRight(){
  var rb=document.getElementById('rbody'); if(!rb)return;
  if(!curCd){rb.innerHTML='<div class="empty">왼쪽에서 현장을 먼저 골라주세요.</div>';return;}
  var pm=PMETA[genP];
  var _tn=toneActiveName(genP);
  var tone=_tn?('<span class="tl">🎯 따라할 톤: <b>'+esc(_tn)+' ✓</b></span>'):'<span class="tl">🎯 따라할 톤: <span style="color:var(--ink3)">기본 (미설정)</span></span>';
  var done=!!(curCust&&curCust.completed);
  if(!done){
    rb.innerHTML='<div class="lockcard">'
      +'<div class="lk-hd"><div class="lk-ic">🔒</div><div class="lk-t">완료하면 글쓰기가 열려요<small>이 현장은 아직 진행중이에요</small></div></div>'
      +'<div class="lk-body">시공을 <b>완료</b>하면 이 현장의 사진·후기로 <b>블로그·인스타·스레드</b> 글이 여기서 바로 만들어져요.</div>'
      +'<div class="lk-how"><div class="lk-how-l">📱 여는 법</div><div class="lk-step">앱에서 이 고객을 <span class="lk-chip">잔금 받음</span> 또는 <span class="lk-chip">시공 완료</span>로 처리하면 → <b>자동으로 열려요</b></div><div class="lk-note">앱을 한 번 열면 웹에 반영돼요 · 이 화면은 새로고침</div></div>'
      +'<div class="lk-why">💡 <b>왜 완료 후에요?</b> 완성된 시공 사진·후기가 있어야 진짜 글이 나오거든요.</div>'
      +'</div>'
      +'<div class="lk-teaser"><div class="lk-teaser-l">완료되면 만들 수 있어요</div><div class="lk-teaser-row"><div class="lk-tt">📝 블로그</div><div class="lk-tt">📷 인스타</div><div class="lk-tt">🧵 스레드</div></div></div>';
    return;
  }
  if(!HAS_KEY){
    rb.innerHTML='<div class="lockcard">'
      +'<div class="lk-hd"><div class="lk-ic">🔑</div><div class="lk-t">글을 만들려면 "AI 키"를 먼저 등록하세요<small>한 번만 하면 계속 써요</small></div></div>'
      +'<div class="lk-body">시공막내가 글 쓸 때 사장님의 <b>Gemini AI 키</b>를 써요. 무료로 발급받아 마이페이지에 붙여넣으면 끝이에요.</div>'
      +'<button class="lk-cta" onclick="goMypage()">🔑 마이페이지에서 키 등록하기 →</button>'
      +'<div class="lk-why" style="margin-top:12px">💡 <b>왜 내 키가 필요해요?</b> 사장님 이름으로 글이 만들어져서, 품질도 사용량도 온전히 사장님 거예요.</div>'
      +'</div>';
    return;
  }
  var btn = done
    ? '<button class="genbig '+pm.cls+'" onclick="genContent()">'+(GEN?'↻ 다시 만들기':pm.btn)+'</button>'
    : '<button class="genbig '+pm.cls+'" style="opacity:.5;cursor:not-allowed" disabled>'+pm.btn+'</button>'
      +'<div class="viewonly" style="margin-bottom:12px">✅ <b>시공 완료 후</b> 글을 만들 수 있어요. 진행중 현장은 아직 시공후 사진·후기 재료가 없어요.</div>';
  var h='<div class="tonebar" style="position:relative">'+tone+'<span class="te" onclick="toggleTonePop(event)">바꾸기 ▾</span><div class="tonepop" id="tonePop"></div></div>'
   +'<div class="metaline">'+pm.meta+'</div>'
   +(done?'<div class="onote"><div class="onote-l">✍️ 이 글에 넣고 싶은 것 <span>(선택 · 사진 보다 떠오른 것)</span></div><textarea class="onote-ta" id="onoteTa" placeholder="예: 고객이 아기 있어 무독성 강조 · 이 집 뷰 좋았음 · 재방문 고객" oninput="onoteChanged()"></textarea></div>':'')
   +'<div class="genhint" id="genHint"></div>'
   +btn
   +((genP==='bl'&&POSTHIST.length)?'<div class="histbar"><span class="histe" onclick="toggleHistPop(event)">📚 이전에 만든 원고 '+POSTHIST.length+'개 ▾</span><div class="histpop" id="histPop"></div></div>':'')
   +'<div class="genload" id="genload"><div class="gblob"></div><div class="gWho">🧑‍🔧 <b>시공막내</b>가 정성껏 쓰는 중이에요</div><div class="gStage" id="gStage">현장 사진 살펴보는 중…</div><div class="gSub" id="gSub"></div><div class="gbar"><span id="gBarFill"></span></div><div class="gElapsed" id="gElapsed"></div></div>'
   +'<div id="genOut"></div>';
  rb.innerHTML=h;
  updateGenHint();
  var _ot=document.getElementById('onoteTa'); if(_ot)_ot.value=ONOTE;
  if(GEN&&done)drawGen();
}
var genOrder=[];   /* 번호매기기: 클릭한 순서대로 photo_id (genOrder[0]=글의 [1]) */
function gPhoto(id){for(var i=0;i<photos.length;i++)if(photos[i].photo_id===id)return photos[i];return null;}
function selIds(){return photos.filter(function(p){return sel[p.photo_id];}).map(function(p){return p.photo_id;});}
/* 진입점 — '글 만들기' 버튼. 처음/사진 바뀜 → 확인 오버레이. 재생성(같은 순서)이면 바로. */
function genContent(){
  if(!curCd)return;
  var ids=selIds();
  var ordOk=genOrder.length>0 && genOrder.every(function(id){return sel[id];}) && ids.length===genOrder.length;
  if(GEN && ordOk){ doGenerate(); return; }   /* ↻ 다시 만들기 = 같은 사진·순서로 재생성 */
  openOrderConfirm(ids);
}
/* ② 확인 오버레이 */
function openOrderConfirm(ids){
  var fan=document.getElementById('ordFan'), t=document.getElementById('ordConfN'),
      z=document.getElementById('ordConf0'), y=document.getElementById('ordConfY');
  if(!ids.length){                    /* 0장 — 더는 조용히 자동6장 X, 명시적 선택 */
    z.style.display='block'; y.style.display='none'; fan.innerHTML=''; t.textContent='넣을 사진이 없어요';
  }else{
    z.style.display='none'; y.style.display='block';
    t.innerHTML='사진 <b>'+ids.length+'장</b>을 선택하셨어요!';
    fan.innerHTML=''; var shown=0;
    ids.forEach(function(id){ if(shown>=5)return; var p=gPhoto(id); if(!p)return;
      var d=document.createElement('div'); d.className='ordfanit'; d.style.backgroundImage='url('+p.thumb_url+')';
      d.style.transform='rotate('+((shown-2)*7)+'deg) translateY('+(Math.abs(shown-2)*3)+'px)'; fan.appendChild(d); shown++; });
    if(ids.length>5){ var m=document.createElement('span'); m.className='ordfanmore'; m.textContent='+'+(ids.length-5); fan.appendChild(m); }
  }
  document.getElementById('ordConfirm').classList.add('on');
}
function ordConfClose(){document.getElementById('ordConfirm').classList.remove('on');}
function ordAuto(){ordConfClose();genOrder=[];doGenerate();}                 /* 사진 없이/자동 */
function ordPick(){ordConfClose();toast('가운데 사진에서 쓸 사진을 눌러 골라주세요 ☑');}
/* ③ 번호 매기기 오버레이 */
function startOrder(){
  ordConfClose();
  genOrder=genOrder.filter(function(id){return sel[id];});   /* 이전 순서 유지(선택된 것만) */
  buildOrderGrid(); document.getElementById('ordNumber').classList.add('on'); renderOrder(null);
}
function buildOrderGrid(){
  var g=document.getElementById('ordGrid'), h='';
  photos.filter(function(p){return sel[p.photo_id];}).forEach(function(p){
    var _v=baFor(p), batx=baLabel(_v), part=partFor(p.photo_id);
    h+='<div class="ordph" id="ordph'+p.photo_id+'" onclick="toggleOrder('+p.photo_id+')">'
      +'<div class="ordim" style="background-image:url('+p.thumb_url+')"></div>'
      +'<span class="ordnum" hidden></span>'
      +'<span class="ordba '+baCls(_v)+'">'+batx+'</span>'
      +(part?'<span class="ordpart">'+esc(part)+'</span>':'')+'</div>';
  });
  g.innerHTML=h;
}
function toggleOrder(id){var i=genOrder.indexOf(id);if(i>=0)genOrder.splice(i,1);else genOrder.push(id);renderOrder(i<0?id:null);}
function resetOrder(){if(!genOrder.length)return;genOrder=[];renderOrder(null);toast('순서를 초기화했어요 — 처음부터 다시 눌러주세요');}
function renderOrder(added){
  photos.filter(function(p){return sel[p.photo_id];}).forEach(function(p){
    var el=document.getElementById('ordph'+p.photo_id); if(!el)return;
    var num=el.querySelector('.ordnum'), idx=genOrder.indexOf(p.photo_id);
    if(idx<0){num.hidden=true;el.classList.remove('on');}
    else{num.hidden=false;num.textContent=idx+1;el.classList.add('on');
      if(p.photo_id===added){num.classList.remove('pop');void num.offsetWidth;num.classList.add('pop');}}
  });
  var total=selIds().length, done=genOrder.length, s='';
  for(var i=0;i<total;i++){ if(i<done){var p=gPhoto(genOrder[i]);s+='<div class="ordslot f" style="background-image:url('+(p?p.thumb_url:'')+')"><span>'+(i+1)+'</span></div>';}
    else s+='<div class="ordslot"><span>'+(i+1)+'</span></div>'; }
  document.getElementById('ordTray').innerHTML=s;
  document.getElementById('ordCnt').innerHTML='<b>'+done+'</b> / '+total+' 순서 정함';
  document.getElementById('ordGo').classList.toggle('dim',!(done===total&&total>0));
}
function ordNumClose(){document.getElementById('ordNumber').classList.remove('on');}
function orderGo(){
  var total=selIds().length;
  if(total===0||genOrder.length<total){var go=document.getElementById('ordGo');go.classList.remove('shake');void go.offsetWidth;go.classList.add('shake');toast('아직 번호 안 매긴 사진이 있어요 — 남은 사진도 눌러주세요');return;}
  ordNumClose(); openKwStep();   /* 순서 확정 → SEO 키워드 스텝 → 거기서 doGenerate */
}
/* ═══ SEO 키워드 스텝 (순서 확정 후 미니 오버레이 · 최대 3개 칩) — 제목 필수+본문 4회 반복 ═══ */
var KW=[];
function kwRender(){
  var c=document.getElementById('kwChips'); if(!c)return;
  c.innerHTML=KW.map(function(k,i){return '<span class="kwChip">'+esc(k)+'<button onclick="kwDel('+i+')" title="빼기">✕</button></span>';}).join('');
  var inp=document.getElementById('kwInput'), cnt=document.getElementById('kwCnt'), mx=document.getElementById('kwMax'), go=document.getElementById('kwGo');
  if(cnt)cnt.textContent=KW.length+'/3';
  var full=KW.length>=3;
  if(inp){inp.readOnly=full; inp.placeholder=full?'3개 모두 채웠어요 · 빼려면 ✕':'예: 수원 줄눈시공 — 입력 후 Enter';}
  if(mx)mx.hidden=!full;
  if(go){var has=KW.length>0; go.classList.toggle('dim',!has); go.textContent=has?'✨ 원고 작성 시작하기':'키워드를 1개 이상 넣어주세요';}
}
function kwAdd(raw){var t=(raw||'').trim().replace(/,+$/,'').replace(/\s+/g,' ');if(!t||KW.indexOf(t)>=0||KW.length>=3)return false;KW.push(t);kwRender();return true;}
function kwDel(i){KW.splice(i,1);kwRender();}
function getKws(){return KW.slice();}
function kwKey(e){var inp=e.target;
  if(e.key==='Enter'||e.key===','){e.preventDefault();if(kwAdd(inp.value))inp.value='';}
  else if(e.key==='Backspace'&&!inp.value&&KW.length){kwDel(KW.length-1);}
}
function openKwStep(){document.getElementById('ordKw').classList.add('on');KW=[];kwRender();setTimeout(function(){var i=document.getElementById('kwInput');if(i)i.focus();},60);}
function kwClose(){document.getElementById('ordKw').classList.remove('on');document.getElementById('ordNumber').classList.add('on');}
function startWrite(){
  var inp=document.getElementById('kwInput'); if(inp&&inp.value.trim()){kwAdd(inp.value);inp.value='';}
  if(KW.length===0){var g=document.getElementById('kwGo');g.classList.remove('shake');void g.offsetWidth;g.classList.add('shake');return;}
  document.getElementById('ordKw').classList.remove('on'); doGenerate();
}
/* ═══ 로딩 씬 — 사진 읽기 → 본문 짜기 → 글·사진 맞추기 (긴 대기를 살아있게) ═══ */
function loadStages(){
  if(genP==='ig')return {st:['현장 사진 살펴보는 중…','감성 캡션 짓는 중…','해시태그 고르는 중…'],sb:[['어떤 현장인지 보는 중…','시공 전·후를 알아보는 중…'],['첫 줄로 마음 잡는 중…','이모지를 얹는 중…','문장을 다듬는 중…'],['지역·부위 태그를 뽑는 중…']]};
  if(genP==='th')return {st:['현장 사진 살펴보는 중…','대화하듯 풀어내는 중…','짧게 다듬는 중…'],sb:[['어떤 현장인지 보는 중…'],['말투를 낮추는 중…','핵심만 남기는 중…'],['해시태그를 고르는 중…']]};
  return {st:['현장 사진 살펴보는 중…','글 쓰는 중…','사진 자리 맞추는 중…'],sb:[['어떤 현장인지 보는 중…','시공 전·후를 알아보는 중…'],['고민 → 상담 → 시공 흐름을 잡는 중…','사장님 말투를 입히는 중…','문장을 자연스럽게 다듬는 중…','제목을 고르는 중…'],['사진 자리를 맞추는 중…','마지막으로 다듬는 중…']]};
}
function startGenLoad(intro){
  var stg=document.getElementById('gStage'),sub=document.getElementById('gSub'),bar=document.getElementById('gBarFill'),elx=document.getElementById('gElapsed');
  var S=loadStages(), STG=S.st, SUB=S.sb;
  var t0=Date.now(), stage=-1, si=0, prog=6, tick=0, done=false;
  function swapSub(txt){ if(!sub)return; sub.style.opacity=0; setTimeout(function(){sub.textContent=txt;sub.style.opacity=1;},160); }
  function setStage(s){ stage=s; si=0; if(stg)stg.textContent=STG[s]; swapSub(SUB[s][0]); }
  setStage(0);
  if(intro&&stg){ stg.textContent=intro; }   // 키워드 확인 멘트로 시작(6초 뒤 다음 단계로 교체)
  var iv=setInterval(function(){
    if(done)return;
    var e=(Date.now()-t0)/1000;
    var want=e<6?0:1; if(want!==stage)setStage(want);
    var target=stage===0?18:88; prog+=(target-prog)*0.06; if(prog>90)prog=90; if(bar)bar.style.width=prog.toFixed(1)+'%';
    tick+=0.7; if(tick>=2.6){ tick=0; si=(si+1)%SUB[stage].length; swapSub(SUB[stage][si]); }
    if(elx)elx.textContent = e>9 ? ('시공막내가 정성껏 '+Math.floor(e)+'초째… 조금만요!') : '';
  },700);
  return {
    fail:function(){ done=true; clearInterval(iv); },
    finish:function(cb){ done=true; clearInterval(iv); if(stg)stg.textContent=STG[2]; swapSub('다 됐어요! ✨'); if(elx)elx.textContent='';
      var p=prog, fv=setInterval(function(){ p+=(100-p)*0.3; if(p>=99.5){p=100;clearInterval(fv);} if(bar)bar.style.width=p.toFixed(1)+'%'; },50);
      setTimeout(cb,900); }
  };
}
/* 실제 생성 — job_id 받고 폴링 + 로딩 씬 연동(게이트웨이 타임아웃 없음) */
function doGenerate(){
  if(!curCd)return;
  var jobCd=curCd;   // 생성 시작 현장 — 도중 딴 현장 가면 그쪽 화면엔 안 그림(서버엔 저장됨, 돌아와 다시 만들기)
  var gl=document.getElementById('genload'); var out=document.getElementById('genOut'); out.innerHTML='';
  gl.classList.add('on');
  var _kws=getKws();
  var _intro=_kws.length?('알겠어요! '+_kws.map(function(k){return '‘'+k+'’';}).join(' · ')+' 검색에 잘 노출되게 원고 작성해볼게요! ✍️'):null;
  var L=startGenLoad(_intro);
  function fail(msg){L.fail();if(curCd!==jobCd)return;gl.classList.remove('on');out.innerHTML='<div style="padding:14px;color:#F0436A;font-size:13px">'+esc(msg)+'</div>';}
  function done(j){if(curCd!==jobCd){L.fail();return;}L.finish(function(){gl.classList.remove('on');GEN=j;MAT=null;if(mTab==='story')renderStory();var b=document.querySelector('#rbody .genbig');if(b)b.textContent='↻ 다시 만들기';drawGen();loadPostHist(jobCd);});}
  var chosen=genOrder.map(function(id){var p=gPhoto(id);return {photo_id:id, part:partFor(id), ba:p?baFor(p):'before'};});
  fetch('/api/web/generate-content',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({customer_digits:curCd, photos:chosen, keywords:_kws})})
  .then(function(r){return r.json().then(function(j){return {s:r.status,j:j};});}).then(function(o){
    if(o.s!==200||!o.j.job_id){ fail((o.j&&o.j.detail)||'생성 실패'); return; }
    pollGen(o.j.job_id, fail, done);
  }).catch(function(){ fail('네트워크 오류 · 다시 시도해 주세요'); });
}
/* 2초마다 상태 폴링 — done/error 까지(최대 ~200초). 폴링은 즉답이라 게이트웨이 타임아웃 안 남. */
function pollGen(job, fail, done){
  var tries=0;
  var iv=setInterval(function(){
    tries++;
    if(tries>100){ clearInterval(iv); fail('생성이 너무 오래 걸려요 · 다시 시도해 주세요'); return; }
    fetch('/api/web/generate-status?job='+encodeURIComponent(job))
    .then(function(r){return r.json();}).then(function(j){
      if(!j||j.status==='pending') return;
      if(j.status==='unknown'){ clearInterval(iv); fail('생성 작업을 찾지 못했어요 · 다시 시도해 주세요'); return; }
      clearInterval(iv);
      if(j.status==='done') done(j); else fail(j.detail||'생성 실패');
    }).catch(function(){});
  }, 2000);
}
/* ═══ 생성 블로그 → 사진 배치 + 서식 렌더 (프로토 e049d59b) ═══ */
function renderBlogDoc(g){
  var photos=(g.photos||[]);
  function pByIdx(n){for(var i=0;i<photos.length;i++)if(photos[i].index===n)return photos[i];return null;}
  function pimg(pm){
    var p=gPhoto(pm.photo_id), thumb=p?photoSrc(p,p.thumb_url):'', _v=(pm.ba||'before'), batx=baLabel(_v), part=pm.part||'';
    return '<div class="pimg" draggable="true" data-idx="'+pm.index+'" data-part="'+esc(part)+'" data-ba="'+esc(_v)+'"><div class="im">'
      +(thumb?'<img loading="lazy" draggable="false" src="'+thumb+'">':'')+'<span class="n">'+pm.index+'</span>'
      +(part?'<span class="part">'+esc(part)+'</span>':'')+'</div><div class="pcap">'+batx+' · '+pm.index+'번</div><span class="pg">⠿</span></div>';
  }
  function prow(nums){
    var cells='',part='';
    nums.forEach(function(n){var pm=pByIdx(n);if(pm){cells+=pimg(pm);if(!part)part=pm.part||'';}});
    if(!cells)return '';
    return '<div class="prow" draggable="true"><span class="grip">⠿ 드래그</span>'+(part?'<span class="matchtip">🎯 '+esc(part)+' 매칭</span>':'')+cells+'</div>';
  }
  function inl(s){return esc(s).replace(/\*\*([^*]+)\*\*/g,'<b>$1</b>');}
  var h='<div class="doc" id="genText"><div class="dropline" id="dropline"></div><div class="t" contenteditable="true">'+esc(g.blog.title||'')+'</div>';
  (g.blog.body||'').split(/\n\n+/).forEach(function(blk){
    blk=(blk||'').replace(/\r/g,'').trim(); if(!blk)return;
    var nums=[]; var tx=blk.replace(/\[(\d+)\]/g,function(_m,d){nums.push(parseInt(d,10));return ' ';}).replace(/\s+/g,' ').trim();
    if(tx){
      if(/^---+$/.test(tx)) h+='<div class="hr"></div>';
      else if(tx.slice(0,3)==='## ') h+='<p class="h" contenteditable="true">'+inl(tx.slice(3).trim())+'</p>';
      else if(tx.slice(0,2)==='> '){var _q=tx.slice(2).trim().replace(/^['"‘’“”]+|['"‘’“”]+$/g,'').trim();h+='<div class="quote" contenteditable="true">'+inl(_q)+'</div>';}
      else if(tx.charAt(0)==='#'&&tx.charAt(1)!=='#') h+='<p class="tags" contenteditable="true">'+esc(tx)+'</p>';
      else h+='<p contenteditable="true">'+inl(tx)+'</p>';
    }
    if(nums.length) h+=prow(nums);
  });
  return h+'</div>';
}
/* ═══ 사진 드래그 재배치 (줄 통째 + 개별) → 바뀐 [n] 배치 저장 ═══ */
var genDragEl=null, genDragKind=null;
function bindGenDrag(){
  var doc=document.getElementById('genText'); if(!doc)return;
  var dl=document.getElementById('dropline');
  doc.querySelectorAll('.prow').forEach(function(el){ if(el.__b)return; el.__b=1;
    el.addEventListener('dragstart',function(e){ if(genDragKind==='photo'){e.preventDefault();return;} genDragKind='row';genDragEl=el;el.classList.add('drag'); try{e.dataTransfer.setData('text/plain','row');}catch(_e){} });
    el.addEventListener('dragend',function(){ el.classList.remove('drag'); endGenDrag(); });
  });
  doc.querySelectorAll('.pimg').forEach(function(el){ if(el.__b)return; el.__b=1;
    var unpress=function(){ el.classList.remove('press'); };
    el.addEventListener('pointerdown',function(){ el.classList.add('press'); });
    el.addEventListener('pointerup',unpress); el.addEventListener('pointercancel',unpress); el.addEventListener('mouseleave',unpress);
    el.addEventListener('dragstart',function(e){ e.stopPropagation();genDragKind='photo';genDragEl=el;el.classList.remove('press');el.classList.add('drag'); try{e.dataTransfer.setData('text/plain','photo');}catch(_e){} });
    el.addEventListener('dragend',function(){ el.classList.remove('drag');el.classList.remove('press'); endGenDrag(); });
  });
  if(doc.__dragBound)return; doc.__dragBound=1;
  doc.addEventListener('input', saveDraftSoon);
  doc.addEventListener('dragover',function(e){
    e.preventDefault();
    if(genDragKind==='row'&&genDragEl){
      var after=[].slice.call(doc.children).filter(function(c){return c!==genDragEl&&c.id!=='dropline';}).find(function(c){var r=c.getBoundingClientRect();return e.clientY<r.top+r.height/2;});
      if(after)doc.insertBefore(genDragEl,after);else doc.appendChild(genDragEl); return;
    }
    if(genDragKind==='photo'&&genDragEl){
      var row=e.target.closest?e.target.closest('.prow'):null;
      doc.querySelectorAll('.prow').forEach(function(x){x.classList.remove('over');});
      if(row){ var cnt=row.querySelectorAll('.pimg').length, same=row===genDragEl.parentNode; if(same||cnt<4){row.classList.add('over');dl.style.display='none';return;} }
      var blocks=[].slice.call(doc.children).filter(function(c){return c.id!=='dropline';});
      var before=blocks.find(function(c){var r=c.getBoundingClientRect();return e.clientY<r.top+r.height/2;});
      var y, dr=doc.getBoundingClientRect();
      if(before){y=before.getBoundingClientRect().top-dr.top-8;} else {var last=blocks[blocks.length-1];y=last?last.getBoundingClientRect().bottom-dr.top+4:0;}
      dl.style.top=y+'px';dl.style.display='block';dl.dataset.beforeIdx=before?blocks.indexOf(before):-1;
    }
  });
  doc.addEventListener('drop',function(e){
    e.preventDefault(); if(genDragKind!=='photo'||!genDragEl)return;
    var src=genDragEl.parentNode, row=e.target.closest?e.target.closest('.prow'):null;
    if(row&&(row===src||row.querySelectorAll('.pimg').length<4)){
      var kids=[].slice.call(row.querySelectorAll('.pimg')).filter(function(k){return k!==genDragEl;});
      var beforeK=kids.find(function(k){var r=k.getBoundingClientRect();return e.clientX<r.left+r.width/2;});
      if(beforeK)row.insertBefore(genDragEl,beforeK);else row.appendChild(genDragEl);
    }else if(row){ genToast('한 줄엔 4장까지 나란히 놓을 수 있어요'); genCleanup(src); return; }
    else{
      var blocks=[].slice.call(doc.children).filter(function(c){return c.id!=='dropline';});
      var idx=parseInt(dl.dataset.beforeIdx,10);
      var nr=document.createElement('div');nr.className='prow';nr.setAttribute('draggable','true');
      nr.innerHTML='<span class="grip">⠿ 드래그</span><span class="matchtip"></span>';
      nr.appendChild(genDragEl);
      if(idx>=0)doc.insertBefore(nr,blocks[idx]);else doc.appendChild(nr);
      bindGenDrag();
    }
    genCleanup(src);
  });
}
function genCleanup(src){ if(src&&src.classList&&src.classList.contains('prow')&&!src.querySelector('.pimg'))src.remove(); }
function endGenDrag(){ genDragEl=null;genDragKind=null; var dl=document.getElementById('dropline'); if(dl)dl.style.display='none';
  document.querySelectorAll('#genText .prow').forEach(function(x){x.classList.remove('over');}); refreshGenTips(); saveDraftSoon(); }
function refreshGenTips(){
  document.querySelectorAll('#genText .prow').forEach(function(row){
    var tip=row.querySelector('.matchtip'); if(!tip)return;
    var parts=[].slice.call(row.querySelectorAll('.pimg')).map(function(p){return p.getAttribute('data-part');}).filter(Boolean);
    var uniq=parts.filter(function(v,i){return parts.indexOf(v)===i;});
    if(uniq.length===1&&uniq[0]) tip.textContent='🎯 '+uniq[0]+' 매칭';
    else if(uniq.length>1) tip.textContent='📷 '+uniq.join(' · ');
    else tip.textContent='📷 사진';
  });
}
function genToast(m){ if(typeof toast==='function')toast(m); }
function domToDraft(){
  var doc=document.getElementById('genText'); if(!doc)return null;
  var title='', out=[];
  [].slice.call(doc.children).forEach(function(el){
    if(el.id==='dropline')return;
    if(el.classList.contains('t')){ title=el.textContent.trim(); return; }
    if(el.classList.contains('prow')){
      var nums=[].slice.call(el.querySelectorAll('.pimg')).map(function(p){return p.getAttribute('data-idx');}).filter(Boolean);
      if(nums.length)out.push(nums.map(function(n){return '['+n+']';}).join(' ')); return;
    }
    if(el.classList.contains('hr')){ out.push('---'); return; }
    var t=genBlockText(el);
    if(el.classList.contains('h')) out.push('## '+t);
    else if(el.classList.contains('quote')) out.push('> '+t);
    else if(t) out.push(t);
  });
  return {title:title, draft:out.join('\n\n')};
}
function genBlockText(el){
  var x=el.innerHTML.replace(/<b>(.*?)<\/b>/gi,'**$1**');
  var tmp=document.createElement('div'); tmp.innerHTML=x;
  return (tmp.textContent||'').replace(/\s+/g,' ').trim();
}
var _saveDraftT;
function saveDraftSoon(){ clearTimeout(_saveDraftT); _saveDraftT=setTimeout(saveDraftNow,800); }
function saveDraftNow(){
  var d=domToDraft(); if(!d||!curCd)return;
  fetch('/api/web/save-draft',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({customer_digits:curCd, draft:d.draft, title:d.title})})
  .then(function(r){return r.json();}).then(function(j){ if(j&&j.ok)genToast('배치 저장됨 ✓'); }).catch(function(){});
}
/* ═══ 네이버 넣기 — 새 탭 + 3단계 안내 시트 ═══ */
var NAVER_WRITE='https://blog.naver.com/GoBlogWrite.naver';
function openNaverTab(){var w=null;try{w=window.open(NAVER_WRITE,'_blank');}catch(_e){}return !!w;}
function sgmExt(){try{return document.documentElement.getAttribute('data-sgm-ext')==='1';}catch(_e){return false;}}
function goNaver(){
  var ext=sgmExt();
  if(ext){   // 자동삽입 예약 — 로그인된 '검증 번호'(whoami)를 실어보내 오타·허위 차단
    fetch('/api/web/whoami').then(function(r){return r.json();}).then(function(j){
      try{window.postMessage({__sgm:'naver-insert', phone:(j&&j.phone)||''},'*');}catch(_e){}
    }).catch(function(){try{window.postMessage({__sgm:'naver-insert'},'*');}catch(_e){}});
  }
  var ok=openNaverTab();
  var t=document.getElementById('shTitle'),s=document.getElementById('shSub'),s1=document.getElementById('step1'),sn1=document.getElementById('sn1'),ob=document.getElementById('openBtn'),st2=document.getElementById('step2'),nud=document.getElementById('shNudge');
  if(ok){s1.classList.add('done');sn1.textContent='✓';ob.style.display='none';}
  else{s1.classList.remove('done');sn1.textContent='1';ob.style.display='inline-block';}
  if(ext){
    t.textContent = ok?'네이버 창을 열었어요 — 자동으로 넣는 중! ✨':'네이버 글쓰기 창을 열어주세요';
    s.textContent = ok?'제목·글·사진이 알아서 들어가요. 확인하고 발행만 직접!':'열리면 자동으로 글·사진이 들어가요.';
    if(st2)st2.style.display='none';
    if(nud)nud.style.display='none';
  } else {
    t.textContent = ok?'네이버 글쓰기를 새 탭으로 열었어요':'네이버 글쓰기 창을 열어주세요';
    s.textContent = ok?'이제 딱 한 번만 누르면 돼요.':'새 탭이 차단됐어요 — 아래 버튼으로 여세요.';
    if(st2)st2.style.display='';
    if(nud)nud.style.display='block';
  }
  document.getElementById('naverSheet').classList.add('on');
}
function closeSheet(){document.getElementById('naverSheet').classList.remove('on');}
/* 확장이 네이버 삽입 완료하면(bridge.js 중계) 안내 시트를 친근한 완료로 갱신 + 다시 띄움(다른 탭 갔다 와도 보이게) */
window.addEventListener('message',function(ev){
  if(ev.source!==window||!ev.data||ev.data.__sgm!=='insert-done')return;
  var t=document.getElementById('shTitle'),s=document.getElementById('shSub');
  if(t)t.textContent='다 넣었어요! 😎 저 시공막내, 일 좀 하죠?';
  if(s)s.textContent='제목·글·사진까지 싹 들어갔어요 — 네이버에서 확인하고 발행만 누르면 끝이에요!';
  var st2=document.getElementById('step2'),nud=document.getElementById('shNudge'),s1=document.getElementById('step1'),sn1=document.getElementById('sn1');
  if(st2)st2.style.display='none'; if(nud)nud.style.display='none';
  if(s1)s1.classList.add('done'); if(sn1)sn1.textContent='✓';
  var sh=document.getElementById('naverSheet'); if(sh)sh.classList.add('on');
  try{toast('네이버에 다 넣었어요! ✅ 확인하고 발행만 하세요');}catch(_e){}
});
function copyPlainBlog(){
  if(!GEN)return; var out=[GEN.blog.title||''];
  (GEN.blog.body||'').split(/\n\n+/).forEach(function(b){
    var t=b.replace(/\[(\d+)\]/g,'').replace(/\*\*/g,'').replace(/^#+\s*/,'').replace(/^>\s*/,'').replace(/^---+$/,'').replace(/\s+/g,' ').trim();
    if(t)out.push(t);
  });
  if(navigator.clipboard&&navigator.clipboard.writeText)navigator.clipboard.writeText(out.join('\n\n'));
  toast('본문만 복사했어요 (서식·사진은 확장으로 넣어야 들어가요)');
}
function drawGen(){
  var out=document.getElementById('genOut'); if(!out||!GEN)return; var g=GEN, body='';
  var _pd=(genP==='bl')?g.blog:(genP==='ig')?g.instagram:g.threads;
  if(!_pd){ out.innerHTML='<div style="padding:34px 14px;text-align:center;color:var(--ink3);font-size:13px;line-height:1.7">이 플랫폼 글은 아직이에요.<br>위 버튼을 눌러 만들어요.</div>'; return; }
  if(genP==='bl'){
    body=renderBlogDoc(g)
      +'<div class="privnote">✏️ 글·제목 클릭하면 바로 수정돼요 · 🔒 이름·연락처·동/호수는 자동 제거</div>'
      +'<div class="gfoot"><button class="ghostbtn" onclick="genContent()">↻ 다시 만들기</button>'
      +'<button class="naverbtn" onclick="goNaver()">📝 네이버에 넣기</button></div>';
  } else if(genP==='ig'){
    body='<div id="genText" class="gen">'+esc(g.instagram.caption)+'</div>'
      +'<div class="lchips" id="igtags">'+g.instagram.hashtags.map(function(t){return '<span class="lchip">'+esc(t)+'</span>';}).join('')+'</div>'
      +'<div class="genacts"><span class="gpill" onclick="genContent()">↻ 다시</span><span class="gpill" onclick="copyGen(\'igtags\',this)"># 태그</span><span class="gpill p" onclick="copyGen(\'genText\',this)">📋 본문</span></div>';
  } else {
    body='<div id="genText" class="gen">'+esc(g.threads.body)+(g.threads.hashtags.length?('\n\n'+esc(g.threads.hashtags.join(' '))):'')+'</div>'
      +'<div class="genacts"><span class="gpill" onclick="genContent()">↻ 다시</span><span class="gpill p" onclick="copyGen(\'genText\',this)">📋 복사</span></div>';
  }
  out.innerHTML=body+(genP==='bl'?'':'<div class="rnote">🔒 이름·전화·동/호·정확주소는 자동 제거("'+esc(g.region||'지역')+'" 수준). 사진은 가운데서 골라 첨부.</div>');
  if(genP==='bl'){bindGenDrag();refreshGenTips();}
}
function copyGen(id,btn){var t=document.getElementById(id).innerText;if(navigator.clipboard)navigator.clipboard.writeText(t);var o=btn.textContent;btn.textContent='복사됨 ✓';setTimeout(function(){btn.textContent=o;},1400);}
/* ===== 라이트박스 ===== */
function lbList(){return photos.filter(function(p){return filter==='all'||p.uploader_kind===filter;});}
function lbOpen(id){var l=lbList();lbi=l.findIndex(function(p){return p.photo_id===id;});if(lbi<0)lbi=0;lbShow();document.getElementById('lb').classList.add('on');}
function lbShow(){var l=lbList();if(!l.length)return;var p=l[lbi];
  document.getElementById('lbimg').src=photoSrc(p,p.url);
  document.getElementById('lbcnt').textContent=(lbi+1)+' / '+l.length;
  var up=p.uploader_kind;var _un=(p.uploader_name||'').trim();var uptxt=up==='owner'?'👤 사장님':(up==='partner'?(!_un||/협업/.test(_un)?'🤝 협업 사장':'🤝 협업 · '+_un):'👤 '+_un);
  document.getElementById('lbtitle').textContent=(partFor(p.photo_id)||curCust.category||'사진')+' · '+baLabel(baFor(p));
  document.getElementById('lbup').textContent=uptxt;
  var dt=new Date(p.uploaded_at_ms); document.getElementById('lbtime').textContent=(dt.getMonth()+1)+'/'+dt.getDate()+' '+pad(dt.getHours())+':'+pad(dt.getMinutes());
  document.getElementById('lbba').innerHTML=baLabel(baFor(p))+' <span onclick="flipBa('+p.photo_id+');lbShow();renderPhotos();" style="color:var(--blue);font-weight:700;cursor:pointer;margin-left:8px">🔄 단계</span> <span onclick="rotatePhoto('+p.photo_id+')" title="90°씩 회전 — 다운로드·블로그까지 반영" style="color:var(--violet);font-weight:700;cursor:pointer;margin-left:8px">🔃 회전</span>';
  document.getElementById('lbdl').onclick=function(){dl1(p.photo_id);};
}
function lbNav(d){var l=lbList();lbi=(lbi+d+l.length)%l.length;lbShow();}
function lbClose(){document.getElementById('lb').classList.remove('on');}
document.getElementById('lb').addEventListener('click',function(e){if(e.target.id==='lb')lbClose();});
document.addEventListener('keydown',function(e){if(!document.getElementById('lb').classList.contains('on'))return;if(e.key==='ArrowLeft')lbNav(-1);else if(e.key==='ArrowRight')lbNav(1);else if(e.key==='Escape')lbClose();});
document.getElementById('q').addEventListener('input',renderDayList);
load(); loadToneLib();
