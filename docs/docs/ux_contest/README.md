# RING-GO UX 공모전 사용 안내

## 1. 사장님이 다른 AI 들에게 명세서 던지기

`BRIEF.md` 전체 복사 → 아래 사이트들에 붙여넣고 응답 받기:

| AI | 사이트 | 모델 추천 |
|---|---|---|
| 🤖 GPT | chat.openai.com | GPT-5 또는 GPT-4o |
| 💎 Gemini | gemini.google.com | Gemini 2.5 Pro |
| 🤘 Grok | grok.com | Grok 3 |

붙여넣을 때 한 줄 추가하면 더 좋아요:
> "위 명세서를 읽고 제 사장님이 쓸 새 UI/UX 를 마크다운으로 제안해주세요. 9번 결과물 요구사항을 반드시 지켜주세요."

## 2. 받은 응답을 폴더에 저장

각 AI 가 마크다운으로 응답을 줍니다 (Part A + B + C).
응답 전체 복사 → 아래 파일에 저장:

- GPT 응답 → `submissions/gpt.md`
- Gemini 응답 → `submissions/gemini.md`
- Grok 응답 → `submissions/grok.md`

## 3. 비교 페이지 열기

`compare.html` 더블클릭 (브라우저로 열림).

- **단일 보기** : 탭으로 AI 하나씩 보기
- **좌우 비교** : 두 AI 응답 나란히 비교
- **명세서 보기** : 원본 BRIEF.md 다시 확인

각 AI 의 ` ```html ``` ` 코드 블록은 자동으로 **iframe 안에서 진짜 폰 화면처럼** 렌더링됩니다 (360×740 dp).

## 4. 평가

명세서 §10 의 평가 기준 (사장님 통점 해결도 30% / 첫 인상 20% / ...) 으로 직접 점수 매기시면 됩니다.

마음에 드는 부분은 메모해두시고, 다음 sprint 에서 Claude (저) 가 진짜 Compose 코드로 옮길 수 있어요.
