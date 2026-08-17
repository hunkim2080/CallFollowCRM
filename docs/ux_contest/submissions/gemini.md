<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
<title>RING-GO ChatScreen Demo</title>
<link rel="stylesheet" href="https://cdn.jsdelivr.net/gh/orioncactus/pretendard@latest/dist/web/static/pretendard.css">
<style>
  :root {
    --bg-dark: #121212;
    --surface: #1E1E1E;
    --surface-light: #2A2A2A;
    --primary: #3A86FF;
    --primary-dark: #2667CF;
    --text-primary: #F8F9FA;
    --text-secondary: #ADB5BD;
    --accent: #FFBE0B;
    --success: #18BE5C;
  }
  
  * { box-sizing: border-box; margin: 0; padding: 0; font-family: 'Pretendard', sans-serif; }
  
  body {
    background-color: #000;
    display: flex;
    justify-content: center;
    align-items: center;
    height: 100vh;
  }
  
  .device-frame {
    width: 360px;
    height: 740px;
    background-color: var(--bg-dark);
    border-radius: 24px;
    border: 8px solid #333;
    display: flex;
    flex-direction: column;
    overflow: hidden;
    position: relative;
    box-shadow: 0 20px 40px rgba(0,0,0,0.5);
  }

  /* Header */
  .header {
    background-color: var(--surface);
    padding: 16px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    border-bottom: 1px solid #333;
    z-index: 10;
  }
  .header-left { display: flex; align-items: center; gap: 12px; color: var(--text-primary); font-size: 18px; font-weight: 700; }
  .header-icons { display: flex; gap: 16px; color: var(--primary); font-size: 20px; }
  
  /* Smart Dashboard (Sticky Context) */
  .smart-dashboard {
    background-color: var(--surface);
    padding: 12px 16px;
    font-size: 13px;
    color: var(--text-secondary);
    border-bottom: 1px solid #333;
    cursor: pointer;
  }
  .dashboard-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
  .status-badge { background: rgba(58, 134, 255, 0.2); color: var(--primary); padding: 4px 8px; border-radius: 6px; font-weight: 600; font-size: 12px; }
  .progress-bar { width: 100%; height: 6px; background-color: var(--surface-light); border-radius: 3px; overflow: hidden; margin-top: 8px; }
  .progress-fill { width: 33%; height: 100%; background-color: var(--accent); }
  .balance-text { display: flex; justify-content: space-between; margin-top: 4px; font-weight: 600; color: var(--text-primary); }

  /* Chat Area */
  .chat-area {
    flex: 1;
    padding: 16px;
    overflow-y: auto;
    display: flex;
    flex-direction: column;
    gap: 16px;
  }
  .msg-bubble {
    max-width: 80%;
    padding: 12px 16px;
    border-radius: 16px;
    font-size: 15px;
    line-height: 1.4;
  }
  .msg-customer { background-color: var(--surface-light); color: var(--text-primary); align-self: flex-start; border-bottom-left-radius: 4px; }
  .msg-owner { background-color: var(--primary); color: #fff; align-self: flex-end; border-bottom-right-radius: 4px; }
  
  /* AI Actions Box */
  .ai-actions-wrapper {
    background-color: var(--bg-dark);
    padding: 12px 16px 0;
    border-top: 1px solid #333;
  }
  .ai-title { color: var(--accent); font-size: 13px; font-weight: 600; margin-bottom: 12px; display: flex; align-items: center; gap: 6px; }
  
  .ai-card {
    background-color: var(--surface);
    border: 1px solid #333;
    border-radius: 12px;
    padding: 14px;
    margin-bottom: 10px;
    cursor: pointer;
    min-height: 56px; /* Touch target size */
    transition: all 0.2s;
  }
  .ai-card:active { background-color: var(--surface-light); transform: scale(0.98); border-color: var(--primary); }
  .ai-card-title { font-weight: 700; color: var(--primary); font-size: 14px; margin-bottom: 6px; }
  .ai-card-text { font-size: 14px; color: var(--text-primary); line-height: 1.4; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }

  /* Composer */
  .composer {
    display: flex;
    align-items: center;
    padding: 12px 16px 16px;
    gap: 10px;
    background-color: var(--bg-dark);
  }
  .input-box {
    flex: 1;
    background-color: var(--surface);
    border: none;
    border-radius: 24px;
    padding: 14px 16px;
    color: var(--text-primary);
    font-size: 15px;
    outline: none;
    min-height: 48px;
  }
  .input-box::placeholder { color: var(--text-secondary); }
  .btn-icon {
    width: 48px; height: 48px;
    border-radius: 24px;
    background-color: var(--surface);
    border: none;
    color: var(--text-secondary);
    font-size: 20px;
    display: flex; justify-content: center; align-items: center;
  }
  .btn-send {
    background-color: var(--primary);
    color: #fff;
  }
</style>
</head>
<body>

<div class="device-frame">
  <!-- Header -->
  <div class="header">
    <div class="header-left">
      <span>←</span>
      <span>김수원 고객님</span>
    </div>
    <div class="header-icons">
      <span>📞</span>
      <span>⋮</span>
    </div>
  </div>

  <!-- Smart Dashboard -->
  <div class="smart-dashboard" onclick="alert('고객 상세 정보로 이동합니다.')">
    <div class="dashboard-header">
      <span style="color: var(--text-primary); font-weight: 600;">📍 수원 광교자이 34평 거실/욕실</span>
      <span class="status-badge">시공 대기 🔨</span>
    </div>
    <div>총 견적 30만 원 중 계약금 10만 원 완료</div>
    <div class="progress-bar"><div class="progress-fill"></div></div>
    <div class="balance-text">
      <span>수납 33%</span>
      <span style="color: var(--accent)">잔금 200,000원 대기</span>
    </div>
  </div>

  <!-- Chat Area -->
  <div class="chat-area">
    <div class="msg-bubble msg-owner">
      안녕하세요! 하우스픽입니다.<br>문의하신 34평 욕실 줄눈 견적은 30만원입니다. 팀장 직접 시공으로 진행됩니다.
    </div>
    <div class="msg-bubble msg-customer">
      네 확인했습니다. 혹시 이번주 토요일 오후에 시공 가능할까요? 그리고 A/S 기간도 궁금해요.
    </div>
  </div>

  <!-- AI Actions -->
  <div class="ai-actions-wrapper">
    <div class="ai-title">✨ AI 추천 답변 (터치하여 바로 전송)</div>
    
    <div class="ai-card" onclick="document.querySelector('.input-box').value = '네, 토요일 오후 2시 시공 가능합니다. 하우스픽은 5년 무상 A/S를 보장해 드리고 있습니다. 일정 확정해 드릴까요?'">
      <div class="ai-card-title">1️⃣ 일정 수락 + A/S 5년 강조</div>
      <div class="ai-card-text">네, 토요일 오후 2시 시공 가능합니다. 하우스픽은 5년...</div>
    </div>
    
    <div class="ai-card" onclick="document.querySelector('.input-box').value = '토요일 오후는 마감되었습니다. 일요일 오전은 어떠신가요? 시공 후 5년간 꼼꼼하게 A/S 보장해 드립니다.'">
      <div class="ai-card-title">2️⃣ 대안 일정 제시 + 신뢰 구축</div>
      <div class="ai-card-text">토요일 오후는 마감되었습니다. 일요일 오전은 어떠신...</div>
    </div>
  </div>

  <!-- Composer -->
  <div class="composer">
    <button class="btn-icon">➕</button>
    <input type="text" class="input-box" placeholder="메시지 입력...">
    <button class="btn-icon btn-send">▶</button>
  </div>
</div>

<script>
  // Simple interaction: update input box from AI cards
  document.querySelectorAll('.ai-card').forEach(card => {
    card.addEventListener('click', function() {
      // visual feedback
      this.style.borderColor = '#3A86FF';
      setTimeout(() => { this.style.borderColor = '#333'; }, 300);
    });
  });
</script>

</body>
</html>