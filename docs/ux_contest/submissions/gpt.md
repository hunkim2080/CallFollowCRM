# RING-GO UI/UX 제안 GPT-5.5 Thinking

## Part A. 핵심 디자인 컨셉

### 1. 디자인 철학

**“통화가 끝난 뒤 5초 안에, 사장님이 해야 할 다음 행동을 손가락 하나로 끝내는 인터페이스.”**

RING-GO는 메시지 앱이 아니다.  
고객과 통화한 뒤 생기는 **후속 행동의 누락, 지연, 혼선**을 줄이는 **시공자 전용 세일즈 운영 OS**다.

따라서 새 UI의 중심은 예쁜 카드가 아니라 **다음 액션**이다.

- 지금 누구를 처리해야 하는가
- 이 고객에게 무슨 말을 해야 하는가
- 돈은 받았는가
- 일정은 잡혔는가
- 사장님이 놓치면 손해 보는 일이 무엇인가

이 5가지를 홈 화면에서 바로 보여줘야 한다.

---

### 2. 기존 토스 스타일 유지 여부

기존의 Toss 스타일은 **안전하지만 새롭지 않다.**  
RING-GO는 “금융 앱처럼 깔끔한 CRM”이 아니라 “현장에서 통화 직후 쓰는 업무 OS”이므로, 토스 화이트 카드 UI를 그대로 유지하면 차별성이 약하다.

제안 톤은 다음과 같다.

> **Dark-First Field OS**
>  
> 어두운 욕실, 차 안, 밤 시간에도 눈이 편하고, 카드마다 다음 행동이 선명하게 보이는 다크모드 우선 UI.

핵심 방향:

1. **다크모드 기본**
   - OLED 배터리와 야간 사용성에 유리
   - 욕실/차 안/침대 위 사용 환경과 맞음
   - 흰 배경보다 집중도가 높음

2. **액션 중심 카드**
   - 카드의 주인공은 고객명이 아니라 “다음 행동”
   - 예: “사진 요청 필요”, “견적 답장 대기”, “계약금 확인 필요”

3. **하단 액션 시트**
   - 작은 화면에서 모든 기능을 화면 안에 밀어 넣지 않음
   - 고객 카드 탭 → 하단에서 “답장 / 일정 / 입금 / 메모” 빠른 처리

4. **Glass Accent는 제한적으로**
   - PostCallCard와 Today Command Bar에만 반투명 유리 질감 사용
   - 전체 UI를 글래스모피즘으로 덮으면 현장 가독성이 떨어짐

---

### 3. 색 팔레트

| 토큰 | 색상 | 용도 |
|---|---:|---|
| `bg.base` | `#070A0F` | 앱 전체 배경 |
| `bg.elevated` | `#101620` | 카드/시트 배경 |
| `bg.soft` | `#161E2B` | 보조 카드/입력창 |
| `line.subtle` | `#263244` | 구분선 |
| `text.primary` | `#F4F7FB` | 주요 텍스트 |
| `text.secondary` | `#A7B0C0` | 보조 설명 |
| `text.tertiary` | `#6E7A8E` | 메타 정보 |
| `primary` | `#5B8CFF` | 주요 액션/선택 칩 |
| `primary.glow` | `#7EA2FF` | 강조 그라데이션 |
| `accent` | `#18D6A3` | 입금/완료/성공 |
| `warning` | `#FFB84D` | 확인 필요 |
| `danger` | `#FF5A6B` | 미확인/실패 |
| `glass` | `rgba(255,255,255,0.08)` | 오버레이/플로팅 바 |

색은 실제로는 2축만 쓴다.

- **Primary Blue**: 답장, 선택, 다음 행동
- **Mint Accent**: 입금, 완료, 일정 확정

빨강/노랑은 경고 상태에만 제한적으로 사용한다.

---

### 4. 타이포 시스템

Pretendard 기준.

| 스타일 | 크기 / 굵기 | 용도 |
|---|---|---|
| Display | 30sp / 800 | 오늘 처리할 숫자, 잔금 금액 |
| Screen Title | 22sp / 750 | 화면 제목 |
| Section Title | 18sp / 700 | 섹션 헤더 |
| Card Title | 16sp / 700 | 고객명/핵심 액션 |
| Body | 14sp / 500 | 요약, 답장 미리보기 |
| Caption | 12sp / 500 | 시간, 상태, 보조 정보 |
| Micro | 11sp / 600 | 뱃지, 태그 |

한글은 자간을 과하게 줄이면 가독성이 떨어진다.  
따라서 숫자와 영문은 살짝 타이트하게, 한글 본문은 기본 자간을 유지한다.

---

### 5. 핵심 아이디어 3개

#### 아이디어 1. HomeScreen을 “타임라인”이 아니라 “오늘의 지휘판”으로 바꾼다

현재 약점: 카드 정보 밀도 부족, 일정이 분리됨, 미확인 후속이 흐림.

해결:

- 상단에 **Today Command Bar** 배치
- 오늘 처리할 고객 수, 미확인, 입금 확인, 내일 시공을 한 줄로 압축
- 타임라인 카드는 고객 중심이 아니라 **상태 중심**으로 재설계

예:

- “김창수”보다 먼저 보이는 것: **사진 요청 필요**
- “010-xxxx”보다 먼저 보이는 것: **견적 답장 안 보냄**
- “5/28 14:52”보다 먼저 보이는 것: **두 번째 전화 — 첫 답장 미처리**

즉, 사장님이 사람을 찾는 것이 아니라 **앱이 일을 밀어준다.**

---

#### 아이디어 2. AI 추천 답변을 칩 3개가 아니라 “전략 카드 3장”으로 바꾼다

현재 약점: AI 추천 답변 칩 글자가 작고, 2줄 미리보기로 판단하기 어렵다.

해결:

- Composer 위에 작은 칩 대신 **Strategy Deck** 배치
- 각 답변은 “전략명 / 의도 / 예상 결과 / 메시지 본문”으로 구성
- 탭하면 확장, 오른쪽 스와이프하면 채택, 길게 누르기 제거

예:

- `[가격 먼저] 빠르게 견적 범위 제시`
- `[사진 요청] 정확도 높이고 상담 이어가기`
- `[일정 선점] 방문 가능 시간으로 전환`

길게 누르기는 현장 사용자에게 좋지 않다.  
손이 젖거나 장갑을 낀 상황에서는 명시적인 버튼이 낫다.

---

#### 아이디어 3. PostCallCard를 “자동 발송 카드”가 아니라 “5초 메모 + 안전 발송 카드”로 바꾼다

현재 약점: 자동 답장이 무섭고, 운전 중 잘 안 보이며, 카드가 큼.

해결:

- 통화 직후 전체 카드가 아니라 **Compact → Expanded 2단계**
- 처음 5초는 작은 플로팅 바:
  - 고객명/번호
  - “메모하기”
  - “답장 보류”
  - “빠른 답장”
- 자동 발송은 기본 OFF
- 대신 “10초 후 발송”이 아니라 **“보내기 전 확인”** 구조

자동화가 강할수록 사장님은 불안해진다.  
특히 고객에게 실제 문자가 발송되는 기능은 **통제감**이 먼저다.

---

## Part B. 텍스트 / ASCII 목업

---

### 1. HomeScreen — Today Command + Action Timeline

```
┌────────────────────────────────────┐
│ RING-GO                    ●  ⚙️   │
│ 오늘 처리할 일 7개                  │
├────────────────────────────────────┤
│ ┌────────────────────────────────┐ │
│ │  미확인 3   입금확인 1   내일시공 2 │ │
│ │  ━━━━━━━   ━━━       ━━━━━     │ │
│ │  지금 먼저 볼 것: 답장 안 보낸 고객 │ │
│ └────────────────────────────────┘ │
│                                    │
│ [전체] [미확인] [오늘신규] [입금]   │
│ [시공전] [완료]          [더보기⌄] │
├────────────────────────────────────┤
│ 오늘 · 5월 30일 토요일              │
│                                    │
│ ┌────────────────────────────────┐ │
│ │ 🔴 사진 요청 필요               │ │
│ │ 김창수 · 010-1234-5678          │ │
│ │ ✨ 욕실 줄눈 견적 문의. 평수만 물음 │ │
│ │ 14:52 · 두 번째 전화 · 미답장     │ │
│ │ [답장] [메모] [일정]             │ │
│ └────────────────────────────────┘ │
│                                    │
│ ┌────────────────────────────────┐ │
│ │ 🟡 계약금 확인 필요              │ │
│ │ 박미정 · 구축 화장실             │ │
│ │ 총 400,000 / 계약금 100,000      │ │
│ │ 잔금 300,000                    │ │
│ │ [입금확인] [문자]                │ │
│ └────────────────────────────────┘ │
│                                    │
│ ┌────────────────────────────────┐ │
│ │ 🟢 내일 시공 예정                │ │
│ │ 이현우 · 래미안상도3차           │ │
│ │ 5/31 09:00 · 현관+욕실           │ │
│ │ [길찾기] [확인문자]              │ │
│ └────────────────────────────────┘ │
│                                    │
│                   ┌────────────┐   │
│                   │ + 빠른입력 │   │
│                   └────────────┘   │
└────────────────────────────────────┘
```

#### 동작

- 카드 오른쪽 스와이프: 확인 처리
- 카드 왼쪽 스와이프: 보류 / 내일 다시 보기
- 카드 탭: 하단 Quick Sheet 열림
- 상단 KPI 탭: 해당 필터 즉시 적용
- `더보기` 탭: 카테고리 전체 Bottom Sheet

---

### 2. ChatScreen — Strategy Deck + Composer

```
┌────────────────────────────────────┐
│ ← 김창수               📞   ⋮      │
│ 010-1234-5678 · 미확인 후속         │
├────────────────────────────────────┤
│ ┌────────────────────────────────┐ │
│ │ ✨ 다음 액션                    │ │
│ │ 사진 1장 요청 후 욕실 크기 확인 │ │
│ │ 마지막 통화: 14:52 · 견적 문의  │ │
│ └────────────────────────────────┘ │
│                                    │
│ ┌────────────────────────────────┐ │
│ │ 🧠 고객 메모                    │ │
│ │ 가격을 먼저 궁금해함. 빠른 일정 선호 │ │
│ └────────────────────────────────┘ │
│                                    │
│             14:52                  │
│       ┌──────────────────────┐     │
│       │ 줄눈 한 평에 얼마예요? │     │
│       └──────────────────────┘     │
│                                    │
│ ┌────────────────────────────────┐ │
│ │ 전략 1 · 가격 먼저              │ │
│ │ 대략 범위를 바로 말하고 사진 요청 │ │
│ │ “욕실 기준 보통 12만원부터...”   │ │
│ │ [채택] [편집]                   │ │
│ └────────────────────────────────┘ │
│ ┌────────────────────────────────┐ │
│ │ 전략 2 · 신뢰 먼저              │ │
│ │ 사진 진단 → 정확 견적으로 유도   │ │
│ │ [채택] [편집]                   │ │
│ └────────────────────────────────┘ │
│                                    │
├────────────────────────────────────┤
│ [+]  메시지 입력...        ✨  ▶   │
└────────────────────────────────────┘
```

#### 동작

- 전략 카드는 가로 스와이프가 아니라 세로 2장 노출
- `채택`: Composer에 삽입
- `편집`: 카드가 Composer 확장 모드로 내려옴
- `✨`: 현재 입력 중인 문장을 사장님 톤으로 다듬기
- `▶`: 52dp 이상, 오른손 엄지 영역에 고정

---

### 3. CustomerDetailScreen — 고객 작업 파일

```
┌────────────────────────────────────┐
│ ← 고객 파일                         │
├────────────────────────────────────┤
│ ┌────────────────────────────────┐ │
│ │ 김창수                          │ │
│ │ 010-1234-5678                   │ │
│ │ 상태: 견적 상담중 · 미확인       │ │
│ └────────────────────────────────┘ │
│                                    │
│ ┌────────────────────────────────┐ │
│ │ 해야 할 일                      │ │
│ │ 1. 욕실 사진 요청               │ │
│ │ 2. 평수 확인                    │ │
│ │ 3. 가능 일정 제안               │ │
│ │ [바로 답장하기]                 │ │
│ └────────────────────────────────┘ │
│                                    │
│ ┌────────────────────────────────┐ │
│ │ 📍 현장 주소                    │ │
│ │ 아직 등록된 주소가 없어요        │ │
│ │ [주소 요청 문자] [직접 등록]     │ │
│ └────────────────────────────────┘ │
│                                    │
│ ┌────────────────────────────────┐ │
│ │ 💰 입금                         │ │
│ │ 총금액     400,000원            │ │
│ │ 계약금     100,000원            │ │
│ │ 잔금       300,000원            │ │
│ │ [계약금 받음] [잔금 받음]        │ │
│ └────────────────────────────────┘ │
│                                    │
│ ┌────────────────────────────────┐ │
│ │ 📅 시공 일정                    │ │
│ │ 아직 일정 없음                  │ │
│ │ [시공일 등록]                   │ │
│ └────────────────────────────────┘ │
│                                    │
│ [시공 대기 🔨] [단순 문의] [완료 ✅] │
│                                    │
│ [💬 문자 보내기]                   │
└────────────────────────────────────┘
```

#### 동작

- 이 화면은 “고객 상세”가 아니라 **고객 작업 파일**
- 최상단은 이름보다 “해야 할 일”
- 입금 카드는 숫자 위계를 크게 준다
- 주소가 없으면 “주소 요청 문자”를 바로 보낼 수 있어야 한다

---

### 4. PostCallCard 오버레이 — Compact First

#### 4-1. Compact 상태

```
┌────────────────────────────────────┐
│                                    │
│                                    │
│                                    │
│                                    │
│        ┌────────────────────┐      │
│        │ 방금 통화 종료      │      │
│        │ 김창수 · 2분 14초   │      │
│        │ [메모] [답장] [보류]│      │
│        └────────────────────┘      │
│                                    │
└────────────────────────────────────┘
```

#### 4-2. Expanded 상태

```
┌────────────────────────────────────┐
│ ┌────────────────────────────────┐ │
│ │ 방금 통화한 고객                │ │
│ │ 김창수 · 010-1234-5678          │ │
│ │ 수신통화 2분 14초               │ │
│ ├────────────────────────────────┤ │
│ │ 빠른 메모                       │ │
│ │ “욕실 사진 보내달라고 함...”     │ │
│ ├────────────────────────────────┤ │
│ │ 추천 후속                       │ │
│ │ 1. 사진 요청 문자               │ │
│ │ 2. 견적 범위 안내               │ │
│ │ 3. 내일 오전 가능 여부 확인      │ │
│ │                                │ │
│ │ [보내기 전 확인] [나중에]        │ │
│ └────────────────────────────────┘ │
└────────────────────────────────────┘
```

#### 핵심 변경

- 자동발송 카운트다운 기본 제거
- 운전 중 오작동 방지를 위해 “즉시 발송”보다 “보류/메모” 우선
- 오버레이 높이 최대 45% 제한
- 10초 후 자동 닫힘이 아니라 `보류 큐`로 자동 저장

---

### 5. ScheduleScreen — 주간 시공 보드

```
┌────────────────────────────────────┐
│ ← 시공 일정                         │
│ 이번 주 매출 예정 1,240,000원       │
├────────────────────────────────────┤
│ 5/30 토  5/31 일  6/1 월  6/2 화    │
│  오늘     D-1     D-2     D-3       │
├────────────────────────────────────┤
│ ┌────────────────────────────────┐ │
│ │ 내일 시공 2건                   │ │
│ │ 총 예정 금액 760,000원          │ │
│ └────────────────────────────────┘ │
│                                    │
│ 09:00                              │
│ ┌────────────────────────────────┐ │
│ │ 이현우 · 래미안상도3차          │ │
│ │ 욕실 바닥 + 현관 · 잔금 300,000 │ │
│ │ [길찾기] [확인문자]             │ │
│ └────────────────────────────────┘ │
│                                    │
│ 14:00                              │
│ ┌────────────────────────────────┐ │
│ │ 박미정 · 구축 화장실             │ │
│ │ 사진 5장 있음 · 주소 등록 완료  │ │
│ │ [길찾기] [고객파일]             │ │
│ └────────────────────────────────┘ │
│                                    │
│                   [+ 일정 추가]    │
└────────────────────────────────────┘
```

#### 동작

- 월간 달력보다 **이번 주 보드** 우선
- 실제 시공자는 “이번 주/내일/오늘”이 중요함
- 일정 카드 안에 잔금, 주소, 확인문자 액션 포함
- 월간 캘린더는 상단 접힘 영역으로 제공

---

## Part C. HTML/CSS 데모

아래 데모는 **HomeScreen** 기준이다.  
360×740 고정 화면, 칩 탭, 카드 펼침, 스와이프 느낌의 확인 처리 버튼을 포함한다.

```html
<!doctype html>
<html lang="ko">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>RING-GO Field OS Demo</title>
  <link rel="stylesheet" href="https://cdn.jsdelivr.net/gh/orioncactus/pretendard@latest/dist/web/static/pretendard.css">
  <style>
    :root {
      --bg-base: #070A0F;
      --bg-elevated: #101620;
      --bg-soft: #161E2B;
      --line: #263244;
      --text-primary: #F4F7FB;
      --text-secondary: #A7B0C0;
      --text-tertiary: #6E7A8E;
      --primary: #5B8CFF;
      --primary-glow: #7EA2FF;
      --accent: #18D6A3;
      --warning: #FFB84D;
      --danger: #FF5A6B;
      --glass: rgba(255,255,255,.08);
      --radius-xl: 24px;
      --radius-lg: 20px;
      --radius-md: 16px;
      --shadow-soft: 0 18px 50px rgba(0,0,0,.32);
    }

    * { box-sizing: border-box; }

    body {
      margin: 0;
      min-height: 100vh;
      display: grid;
      place-items: center;
      background: radial-gradient(circle at top, #1C2A44 0, #070A0F 46%, #030509 100%);
      font-family: Pretendard, -apple-system, BlinkMacSystemFont, system-ui, sans-serif;
      color: var(--text-primary);
    }

    .phone {
      width: 360px;
      height: 740px;
      overflow: hidden;
      border-radius: 32px;
      background:
        radial-gradient(circle at 82% 0%, rgba(91,140,255,.30), transparent 28%),
        linear-gradient(180deg, #0B1018 0%, var(--bg-base) 42%);
      border: 1px solid rgba(255,255,255,.08);
      box-shadow: 0 40px 100px rgba(0,0,0,.55);
      position: relative;
    }

    .safe {
      height: 100%;
      overflow-y: auto;
      padding: 18px 16px 96px;
      scrollbar-width: none;
    }

    .safe::-webkit-scrollbar { display: none; }

    .topbar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 18px;
    }

    .brand {
      font-size: 19px;
      font-weight: 800;
      letter-spacing: .02em;
    }

    .status {
      display: flex;
      gap: 10px;
      align-items: center;
      color: var(--text-secondary);
      font-size: 13px;
    }

    .dot {
      width: 8px;
      height: 8px;
      border-radius: 99px;
      background: var(--accent);
      box-shadow: 0 0 16px rgba(24,214,163,.75);
    }

    .hero-title {
      margin: 0 0 12px;
      font-size: 28px;
      line-height: 1.12;
      letter-spacing: -.03em;
      font-weight: 800;
    }

    .command {
      border: 1px solid rgba(255,255,255,.10);
      background: linear-gradient(135deg, rgba(255,255,255,.11), rgba(255,255,255,.045));
      backdrop-filter: blur(22px);
      border-radius: var(--radius-xl);
      padding: 16px;
      box-shadow: var(--shadow-soft);
      margin-bottom: 14px;
    }

    .metrics {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 8px;
      margin-bottom: 12px;
    }

    .metric {
      min-height: 58px;
      border-radius: 16px;
      background: rgba(255,255,255,.055);
      padding: 10px;
      cursor: pointer;
      transition: transform .18s ease, background .18s ease;
    }

    .metric:active { transform: scale(.97); }

    .metric strong {
      display: block;
      font-size: 21px;
      line-height: 1;
      font-weight: 800;
    }

    .metric span {
      display: block;
      margin-top: 6px;
      font-size: 11px;
      color: var(--text-secondary);
      font-weight: 600;
    }

    .next {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 8px;
      color: var(--text-secondary);
      font-size: 13px;
    }

    .next b {
      color: var(--text-primary);
      font-weight: 700;
    }

    .chips {
      display: flex;
      gap: 8px;
      overflow-x: auto;
      padding: 2px 0 14px;
      scrollbar-width: none;
    }

    .chips::-webkit-scrollbar { display: none; }

    .chip {
      border: 0;
      white-space: nowrap;
      height: 38px;
      min-width: 58px;
      padding: 0 14px;
      border-radius: 999px;
      background: var(--bg-soft);
      color: var(--text-secondary);
      font-family: inherit;
      font-weight: 700;
      cursor: pointer;
      transition: background .18s ease, color .18s ease, transform .18s ease;
    }

    .chip.active {
      background: var(--primary);
      color: white;
      box-shadow: 0 10px 26px rgba(91,140,255,.32);
    }

    .chip:active { transform: scale(.96); }

    .date-label {
      margin: 8px 2px 10px;
      color: var(--text-secondary);
      font-size: 13px;
      font-weight: 700;
    }

    .card {
      position: relative;
      border-radius: var(--radius-lg);
      background: linear-gradient(180deg, rgba(255,255,255,.065), rgba(255,255,255,.035));
      border: 1px solid rgba(255,255,255,.08);
      padding: 15px;
      margin-bottom: 12px;
      overflow: hidden;
      transition: transform .2s ease, border-color .2s ease, background .2s ease;
      cursor: pointer;
    }

    .card:hover {
      border-color: rgba(126,162,255,.35);
    }

    .card.confirmed {
      transform: translateX(72px);
      opacity: .44;
    }

    .card-head {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      gap: 10px;
    }

    .state {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      min-height: 26px;
      padding: 0 10px;
      border-radius: 999px;
      font-size: 12px;
      font-weight: 800;
      background: rgba(255,90,107,.13);
      color: #FF9AA5;
    }

    .state.pay {
      background: rgba(255,184,77,.13);
      color: #FFD28A;
    }

    .state.done {
      background: rgba(24,214,163,.13);
      color: #89F2D4;
    }

    .time {
      color: var(--text-tertiary);
      font-size: 12px;
      font-weight: 700;
    }

    .customer {
      margin-top: 11px;
      font-size: 17px;
      font-weight: 800;
      letter-spacing: -.02em;
    }

    .summary {
      margin: 6px 0 0;
      color: var(--text-secondary);
      font-size: 13px;
      line-height: 1.45;
      word-break: keep-all;
    }

    .meta {
      margin-top: 8px;
      color: var(--text-tertiary);
      font-size: 12px;
      font-weight: 650;
    }

    .actions {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 8px;
      max-height: 0;
      opacity: 0;
      transform: translateY(-4px);
      transition: max-height .26s ease, opacity .22s ease, transform .22s ease, margin-top .22s ease;
    }

    .card.open .actions {
      max-height: 54px;
      opacity: 1;
      transform: translateY(0);
      margin-top: 14px;
    }

    .action {
      height: 48px;
      border: 0;
      border-radius: 15px;
      background: var(--bg-soft);
      color: var(--text-primary);
      font-family: inherit;
      font-size: 13px;
      font-weight: 800;
      cursor: pointer;
    }

    .action.primary {
      background: linear-gradient(135deg, var(--primary), var(--primary-glow));
    }

    .action.accent {
      background: rgba(24,214,163,.16);
      color: #8AF2D4;
    }

    .money {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 8px;
      margin-top: 12px;
      max-height: 0;
      opacity: 0;
      transition: max-height .26s ease, opacity .22s ease;
    }

    .card.open .money {
      max-height: 80px;
      opacity: 1;
    }

    .money-box {
      background: rgba(255,255,255,.055);
      border-radius: 14px;
      padding: 10px;
    }

    .money-box span {
      display: block;
      color: var(--text-tertiary);
      font-size: 11px;
      font-weight: 700;
    }

    .money-box b {
      display: block;
      margin-top: 4px;
      font-size: 17px;
      font-weight: 850;
      letter-spacing: -.02em;
    }

    .fab {
      position: absolute;
      right: 16px;
      bottom: 22px;
      height: 56px;
      padding: 0 18px;
      border-radius: 19px;
      border: 1px solid rgba(255,255,255,.10);
      background: linear-gradient(135deg, var(--primary), #7B61FF);
      color: white;
      font-family: inherit;
      font-size: 15px;
      font-weight: 850;
      box-shadow: 0 18px 44px rgba(91,140,255,.36);
      cursor: pointer;
    }

    .toast {
      position: absolute;
      left: 16px;
      right: 16px;
      bottom: 88px;
      min-height: 46px;
      border-radius: 16px;
      background: rgba(16,22,32,.94);
      border: 1px solid rgba(255,255,255,.10);
      display: grid;
      place-items: center;
      color: var(--text-primary);
      font-size: 13px;
      font-weight: 750;
      opacity: 0;
      transform: translateY(12px);
      transition: opacity .2s ease, transform .2s ease;
      pointer-events: none;
    }

    .toast.show {
      opacity: 1;
      transform: translateY(0);
    }
  </style>
</head>
<body>
  <main class="phone">
    <section class="safe">
      <header class="topbar">
        <div class="brand">RING-GO</div>
        <div class="status"><span class="dot"></span><span>동기화 정상</span><span>⚙️</span></div>
      </header>

      <h1 class="hero-title">오늘 처리할 일<br>7개 남았어요</h1>

      <section class="command">
        <div class="metrics">
          <div class="metric" data-filter="미확인"><strong>3</strong><span>미확인</span></div>
          <div class="metric" data-filter="입금"><strong>1</strong><span>입금확인</span></div>
          <div class="metric" data-filter="시공전"><strong>2</strong><span>내일시공</span></div>
        </div>
        <div class="next">
          <span>지금 먼저 볼 것</span>
          <b>답장 안 보낸 고객</b>
        </div>
      </section>

      <nav class="chips">
        <button class="chip active">전체</button>
        <button class="chip">미확인</button>
        <button class="chip">오늘신규</button>
        <button class="chip">입금</button>
        <button class="chip">시공전</button>
        <button class="chip">완료</button>
      </nav>

      <div class="date-label">오늘 · 5월 30일 토요일</div>

      <article class="card open" data-name="김창수">
        <div class="card-head">
          <span class="state">🔴 사진 요청 필요</span>
          <span class="time">14:52</span>
        </div>
        <div class="customer">김창수 · 010-1234-5678</div>
        <p class="summary">✨ 욕실 줄눈 견적 문의. 평수만 물어봤고 아직 사진은 받지 못했어요.</p>
        <div class="meta">두 번째 전화 · 첫 답장 미처리</div>
        <div class="actions">
          <button class="action primary">답장</button>
          <button class="action">메모</button>
          <button class="action">일정</button>
        </div>
      </article>

      <article class="card" data-name="박미정">
        <div class="card-head">
          <span class="state pay">🟡 계약금 확인</span>
          <span class="time">13:10</span>
        </div>
        <div class="customer">박미정 · 구축 화장실</div>
        <p class="summary">총액 400,000원으로 안내 완료. 계약금 입금 확인 후 시공 대기 전환 필요.</p>
        <div class="money">
          <div class="money-box"><span>계약금</span><b>100,000</b></div>
          <div class="money-box"><span>잔금</span><b>300,000</b></div>
        </div>
        <div class="actions">
          <button class="action accent">입금확인</button>
          <button class="action">문자</button>
          <button class="action">보류</button>
        </div>
      </article>

      <article class="card" data-name="이현우">
        <div class="card-head">
          <span class="state done">🟢 내일 시공</span>
          <span class="time">09:00</span>
        </div>
        <div class="customer">이현우 · 래미안상도3차</div>
        <p class="summary">욕실 바닥 + 현관. 주소 등록 완료. 내일 오전 확인 문자 발송 권장.</p>
        <div class="meta">5/31 09:00 · 잔금 300,000원</div>
        <div class="actions">
          <button class="action primary">길찾기</button>
          <button class="action">확인문자</button>
          <button class="action">고객파일</button>
        </div>
      </article>
    </section>

    <button class="fab">+ 빠른입력</button>
    <div class="toast" id="toast">필터가 적용됐어요</div>
  </main>

  <script>
    const chips = document.querySelectorAll('.chip');
    const cards = document.querySelectorAll('.card');
    const toast = document.getElementById('toast');

    function showToast(text) {
      toast.textContent = text;
      toast.classList.add('show');
      clearTimeout(window.__toastTimer);
      window.__toastTimer = setTimeout(() => toast.classList.remove('show'), 1200);
    }

    chips.forEach(chip => {
      chip.addEventListener('click', () => {
        chips.forEach(c => c.classList.remove('active'));
        chip.classList.add('active');
        showToast(`'${chip.textContent}' 필터 적용`);
      });
    });

    document.querySelectorAll('.metric').forEach(metric => {
      metric.addEventListener('click', () => {
        const filter = metric.dataset.filter;
        chips.forEach(c => {
          c.classList.toggle('active', c.textContent === filter);
        });
        showToast(`'${filter}'만 볼게요`);
      });
    });

    cards.forEach(card => {
      card.addEventListener('click', (e) => {
        if (e.target.tagName === 'BUTTON') {
          showToast(`${e.target.textContent} 액션 선택`);
          return;
        }
        card.classList.toggle('open');
      });

      let startX = 0;
      card.addEventListener('touchstart', e => {
        startX = e.touches[0].clientX;
      }, { passive: true });

      card.addEventListener('touchend', e => {
        const endX = e.changedTouches[0].clientX;
        if (endX - startX > 72) {
          card.classList.add('confirmed');
          showToast(`${card.dataset.name} 확인 처리`);
        }
      });
    });
  </script>
</body>
</html>
```

---

## 자유 제안: 사장님이 미처 생각 못 했을 기능

### 1. Morning Recovery Mode

아침 첫 실행 시 전날 놓친 항목만 보여주는 모드.

```
어제 놓친 것 4개
1. 김창수: 사진 요청 안 보냄
2. 박미정: 계약금 확인 필요
3. 이현우: 내일 시공 확인문자 필요
4. 미등록 번호: 부재중 2회
```

홈 화면에 항상 많은 정보를 보여주기보다, 아침에는 정리 모드가 따로 있는 편이 낫다.

---

### 2. Voice Memo → Customer Action

시공 중 손을 못 쓸 때:

> “김창수 욕실 사진 요청했고 40만원 정도 말함”

이 음성 메모를 AI가 아래처럼 구조화한다.

- 고객: 김창수
- 메모: 욕실 사진 요청
- 견적: 40만원
- 다음 액션: 사진 도착 후 최종 견적 안내
- 카테고리: 견적 상담중

이 기능은 RING-GO의 현장성을 강하게 만든다.

---

### 3. Payment Confidence Badge

입금 문자를 완전히 자동 처리하는 것은 위험하다.  
대신 “확신도”를 보여준다.

- `확신 96%`: 박미정 계약금 100,000원으로 보임
- `확신 62%`: 이름 불일치. 직접 확인 필요

자동 분류보다 **자동 추천 + 사장님 승인**이 안전하다.

---

### 4. Portfolio Match

고객이 “베이지톤 욕실 줄눈”을 문의하면, 과거 시공 사진 중 유사한 현장을 추천한다.

- 고객 문의 → 비슷한 시공 사례 3개
- 사장님은 한 장 선택
- 메시지에 자동 첨부

이건 단순 CRM을 넘어 매출 전환율을 높이는 기능이다.

---

## 구현 가능성 메모: Compose 기준

### 핵심 컴포넌트

- `TodayCommandBar`
- `ActionCustomerCard`
- `StrategyReplyCard`
- `PostCallCompactOverlay`
- `PaymentSummaryCard`
- `ScheduleWeekStrip`
- `CustomerActionSheet`

### Compose 구현 방향

- 카드 확장: `AnimatedVisibility`, `animateContentSize`
- 칩 선택: `LazyRow` + `FilterChip` 커스텀
- 스와이프 확인: `SwipeToDismissBox` 또는 커스텀 `pointerInput`
- PostCallCard: Overlay Activity / SYSTEM_ALERT_WINDOW
- 다크모드: Material3 `darkColorScheme` 기반, 컴포넌트는 커스텀
- 48dp 터치: 모든 버튼 `Modifier.heightIn(min = 48.dp)` 고정

### 주의할 점

- 글래스모피즘은 Android 저사양 기기에서 blur 비용이 크다.
- 갤럭시 S9 기준으로는 실시간 blur보다 반투명 레이어 + 그라데이션으로 대체하는 편이 안전하다.
- 애니메이션은 180~260ms 안에서 끝내야 현장 업무 앱답다.
- 홈 화면 LazyColumn 카드가 많을 수 있으므로 shadow를 과하게 쓰면 안 된다.

---

## 최종 판단

RING-GO의 UI가 성공하려면 “예쁜 메신저”가 되면 안 된다.  
갤메시지와 카카오톡은 이미 메시지를 잘 보여준다.

RING-GO가 가져야 할 정체성은 이것이다.

> **통화 후 돈이 되는 일을 놓치지 않게 해주는 현장형 세일즈 OS.**

따라서 화면의 중심은 대화 내용이 아니라 **후속 행동, 입금, 일정, 미확인 리스크**여야 한다.

이번 제안의 핵심은 다음 3가지다.

1. 홈을 타임라인이 아니라 오늘의 지휘판으로 재설계
2. AI 답변을 작은 칩이 아니라 전략 카드로 재설계
3. 통화 후 오버레이를 자동 발송 중심이 아니라 안전한 메모/보류 중심으로 재설계

이 방향이면 기존 토스식 CRM보다 RING-GO만의 제품성이 훨씬 선명해진다.
