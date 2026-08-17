# SERVER HANDOFF — 접수서 계약금 안내문 + 마케팅 설문 되돌리기

작성: android Claude (2026-07-03) · 대상: cowork(Mac mini, `server/`)
출처: 사장님이 실제 접수서(`api.si0in.kr/q/…`) 스샷 보며 지시. **§0 프로토=실전 스펙** 원칙대로 문구는 아래 verbatim 사용.

전부 **웹 접수서(서버 렌더링)** 변경이라 앱은 안 건드림. 앱 쪽은 이미 정액 계약금 기본값 10만원만 바꿔 배포함(무관).

---

## 1) 계약금 안내문 — "시공 끝난 뒤 입금" 안내로 변경

### 지금 (`server/main.py` `_build_deposit_html`, line ~13872)
```python
def _build_deposit_html(deposit_mode, deposit_amount_krw, deposit_ratio_pct):
    if deposit_mode == "none" or not deposit_amount_krw:
        return ""                                   # ← 계약금 없으면 아무것도 안 보임
    amount = _format_won(deposit_amount_krw)
    suffix = ""
    if deposit_mode == "ratio" and deposit_ratio_pct:
        suffix = f" (총액의 {int(deposit_ratio_pct)}%)"
    return (f'<div class="q-deposit">계약금 {amount}원{suffix}'
            f' · 입금 계좌는 확정 후 안내드려요</div>')
```
접수서 = **예약금(계약금) 이미 받은 고객**에게 나가는 폼(앱 help: "예약금 받은 고객에게").
그래서 고객이 궁금한 건 "**시공 끝나고 얼마를, 어떻게 내나**"임. 사장님 지시:

- **계약금 있음** → `시공이 끝난 뒤 계약금 {N}만원을 제외하고 입금해주시면 됩니다!`
- **계약금 없음(none/0)** → `시공이 끝난 뒤 입금해주세요!`  (지금은 박스 자체가 안 뜸 → **떠야 함**)

### 요청 구현 (사장님 결정 반영 · 2026-07-04)
**사장님 확정: 금액은 "만원" 표기** (딱 만원 안 떨어지면 원 표기로 폴백). 앱 쪽(견적서/문자)도 동일 규칙으로 이미 반영함(`formatWon`: round→만원, else→원). 서버도 맞춰줘.

```python
def _man_or_won(krw: int) -> str:
    """만원 딱 떨어지면 '10만원', 아니면 '100,000원'. (앱 formatWon 과 동일 규칙)"""
    if krw >= 10000 and krw % 10000 == 0:
        return f"{krw // 10000}만원"
    return f"{_format_won(krw)}원"

def _build_deposit_html(deposit_mode, deposit_amount_krw, deposit_ratio_pct):
    # 계약금 없음 → 잔금 안내만 (지금은 박스 자체가 안 떴음 → 떠야 함)
    if deposit_mode == "none" or not deposit_amount_krw:
        return '<div class="q-deposit">시공이 끝난 뒤 입금해주세요!</div>'
    amount = _format_won(deposit_amount_krw)                 # 예: "100,000" (기존 계약금 표시용, 그대로)
    label = _man_or_won(deposit_amount_krw)                  # 예: "10만원" (잔금 안내용, 사장님 만원 표기)
    suffix = ""
    if deposit_mode == "ratio" and deposit_ratio_pct:
        suffix = f" (총액의 {int(deposit_ratio_pct)}%)"
    # 1줄차 = 받은 계약금 표시 / 2줄차 = 잔금 안내 (사장님 verbatim, 만원 표기)
    return (
        f'<div class="q-deposit">계약금 {amount}원{suffix}'
        f' · 입금 계좌는 확정 후 안내드려요<br>'
        f'시공이 끝난 뒤 계약금 {label}을 제외하고 입금해주시면 됩니다!</div>'
    )
```

참고(남은 미세 결정, 애매하면 SYNC 로): "입금 계좌는 확정 후 안내드려요" 는 **남김**(계좌 아직 필요). 빼길 원하면 그 조각만 제거.

`_format_won` 위치/이름 그대로 사용. 호출부(견적 카드 line ~13536 `{deposit_html}`)는 그대로 두면 됨.

---

## 2) "마케팅에 도움돼요" 설문 — 잘못 눌러도 되돌릴 수 있게

### 문제 (사장님 원문)
> "마케팅에 도움돼요 이건 한번 누르면 뒤로가기가 없어서 실수로 누르는 사람들도 있을듯."

`renderSurvey()` (line ~13637) 는 단방향 상태머신. 한 번 칩/버튼 누르면(`surveyBusy`,
`surveySource`, `surveyCategory` …) 다음 단계로만 가고 **되돌리는 UI가 없음**. 특히:
- `[지금은 바빠요]`(surveyBusy) → `busy=true` 종착 → "괜찮아요! …감사합니다 🙏" 로 **영구 고정**.
- 소스칩(네이버/인스타 등) 잘못 누르면 이전 질문으로 못 감.
- `done` 종착도 마찬가지로 못 고침.

즉 실수 탭이 되돌려지지 않음.

### 요청 (되돌리기/다시선택 추가)
각 단계 + 종착(busy/done)에 **작게 "← 다시 선택" 링크**를 붙여, 직전 선택을 취소하고
한 단계 전으로 돌아가게 해줘. 최소 구현(권장, 서버 JS만):

```js
// 종착/각 단계에 붙일 되돌리기 — 마지막으로 채운 필드 하나를 비우고 다시 렌더
function surveyBack() {
  var s = quoteSurvey;
  if (s.done)            { s.done = false;
                           if (s.etc) s.etc = '';
                           else if (s.category) s.category = null;
                           else if (s.keyword) s.keyword = '';
                           else if (s.source) s.source = null;
                           else s.asked = false; }
  else if (s.busy)       { s.busy = false; }          // "지금은 바빠요" 취소
  else if (s.etc)        { s.etc = ''; }
  else if (s.category)   { s.category = null; }
  else if (s.keyword)    { s.keyword = ''; }
  else if (s.source)     { s.source = null; }
  else if (s.asked)      { s.asked = false; }
  renderSurvey();
}
```
그리고 `renderSurvey()` 의 각 분기 `h` 뒤(맨 처음 `!s.asked` 질문 화면 제외해도 됨)에
`+ '<div class="qs-back" onclick="surveyBack()">← 다시 선택할래요</div>'` 한 줄 추가.
특히 **`busy`(지금은 바빠요) 종착과 `done` 종착에는 반드시** 붙여서, 오탭한 고객이 되돌아올 수 있게.

CSS `.qs-back` = 작고 회색(예: `font-size:12px;color:#8B95A1;margin-top:10px;cursor:pointer`).
문구는 사장님이 다른 걸 원하면 교체 — 일단 "← 다시 선택할래요" 제안.

**대안(더 간단):** busy/done 종착 문구 옆에 "잘못 누르셨나요? 다시 하기" 만 붙여도 됨.
어느 쪽이든 **한 번 탭 = 영구 고정**만 없애면 사장님 요구 충족.

---

## 배포 후
- SYNC.md 에 `## … cowork` 블록으로 커밋 해시 + 위 "결정 필요" 답(계약금 문구 만원/원 확정) 남겨줘.
- 접수서 링크 하나로 사장님이 폰에서 눈으로 확인 → 문구/되돌리기 동작 OK 받으면 종료.
