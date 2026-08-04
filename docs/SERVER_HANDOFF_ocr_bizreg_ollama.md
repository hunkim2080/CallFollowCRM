# 코워크 핸드오프 — 사업자등록증 OCR을 로컬 LLM(Ollama)으로

작성: 2026-08-05 (android Claude) · 결정: 사장님 ("사업자등록증 OCR은 로컬 LLM으로 돌리자")

## 목표
사업자등록증 사진 → **로컬 비전 LLM(Ollama `qwen2.5vl:7b`)** 으로 필드 추출.
기존 서버 Vision(Gemini) 대신. **무료·오프라인**, 맥미니에 이미 설치된 모델 재사용.

## 이미 있는 것 (건드릴 필요 최소)
- **앱 배선(그대로 둠):** `ExpoRepository.ocrBizReg(imageDataUrl)` → `POST /api/expo/ocr/bizreg`
  - 요청: `{ "image": "data:image/jpeg;base64,...." }`
  - 응답(계약, **유지**): `{ "biz_name": "...", "biz_no": "...", "rep_name": "...", "address": "..." }`
  - 앱은 이걸 `bizName / bizNo / bizOwner(=rep_name) / bizAddr` prefs에 채움.
- **로컬 OCR 레퍼런스(재사용):** `~/Workspace/naver-blog-bot/src/ocr.js` 의 `imageToText()`
  - Ollama `http://127.0.0.1:11434` `POST /api/generate`
  - `{ model: "qwen2.5vl:7b", prompt, images:[b64], stream:false, options:{temperature:0} }` → `{response}`

## 바꿀 것 (서버 = 코워크, 맥미니)
`/api/expo/ocr/bizreg` 핸들러의 **OCR 백엔드만 Gemini → Ollama로 교체**. (응답 JSON 형태는 그대로 유지 → 앱 무변경)

1. 입력 `image` (dataURL) 에서 base64 본문만 분리: `,` 뒤 부분 (`data:image/...;base64,` 프리픽스 제거).
2. Ollama 호출 (127.0.0.1:11434, `qwen2.5vl:7b`). ringgo-server와 Ollama 둘 다 맥미니라 localhost OK.
3. 프롬프트(권장): 사업자등록증에서 아래를 **JSON만** 출력하게.
   ```
   이 사업자등록증 이미지에서 다음을 JSON으로만 출력하라(설명·군더더기 금지):
   {"biz_name":"상호(법인/상호명)","biz_no":"사업자등록번호 XXX-XX-XXXXX 형식","rep_name":"대표자 성명","address":"사업장 소재지 전체"}
   못 읽은 항목은 빈 문자열. JSON 외 다른 텍스트 출력 금지.
   ```
   `options:{temperature:0}`. 응답에서 JSON 파싱(코드펜스/잡텍스트 있으면 `{...}` 구간만 추출).
4. `biz_no` 는 숫자 10자리면 `XXX-XX-XXXXX` 로 포맷(정규화) 권장.
5. **Graceful:** Ollama 꺼짐/실패 시 빈 필드 `{"biz_name":"","biz_no":"","rep_name":"","address":""}` 반환(앱이 수동 입력으로 폴백). 500 던지지 말 것.
6. (선택) 로컬 OCR이라 비용 0 — 기존 Gemini 사용량/과금 코드는 이 경로에서 빼도 됨.

## 검증
- `qwen2.5vl:7b` 존재: `curl -s 127.0.0.1:11434/api/tags | grep qwen2.5vl` (없으면 `ollama pull qwen2.5vl:7b`).
- 실제 사업자등록증 1장으로 `/api/expo/ocr/bizreg` 호출 → 4개 필드 채워지는지.

## 앱 쪽(android Claude가 별도로 함, 서버와 독립)
- 사업자정보 편집 화면에 **"📷 사업자등록증 사진으로 자동입력"** 버튼 추가 → 사진 선택/촬영 → `ocrBizReg` → `bizName/bizOwner/bizNo/bizAddr` 자동 채움 **+ 저장 전 사장님이 눈으로 확인·수정**(OCR은 완벽하지 않으니 검토 필수).
- 응답 계약 안 바꾸므로 앱-서버 순서 무관(서버 먼저 켜지면 앱이 바로 활용).
