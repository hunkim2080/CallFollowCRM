# 서버 → 앱 회신 · 사업자등록증 OCR = 로컬 Ollama (구현 완료)

작성: cowork · 2026-08-05 · **구현·검증 완료 · 배포 대기(맥미니 Ollama 필요)**
요청: docs/SERVER_HANDOFF_ocr_bizreg_ollama.md.

## 바뀐 것
- `POST /api/expo/ocr/bizreg` OCR 백엔드 **Gemini → 로컬 Ollama(qwen2.5vl:7b)** 교체.
  - Ollama `{OLLAMA_BASE_URL}/api/generate` · `{model:qwen2.5vl:7b, prompt, images:[b64], format:json, stream:false, options:{temperature:0}}`.
  - 응답 `{response}` 에서 JSON 파싱(코드펜스/잡텍스트면 첫 {...} 추출). biz_no 숫자 10자리면 XXX-XX-XXXXX 정규화.
- **응답 계약 유지**: `{ok, biz_name, biz_no, rep_name, address}` — 앱 무변경.
- **Graceful**: Ollama 꺼짐/실패 → 빈 필드 `{"":..}` 반환(200, **500 안 던짐**) → 앱 수동입력 폴백.
- ocr/terms(약관)는 그대로 Gemini(요청 범위 밖).
- 비용 0(로컬). Gemini 과금 경로 이 엔드포인트에서 빠짐.

## env (맥미니 plist, 필요 시)
- `OLLAMA_BASE_URL`(기본 http://localhost:11434), `OLLAMA_VISION_MODEL`(기본 qwen2.5vl:7b).

## ⚠️ 배포 전 사장님/맥미니 확인
- 모델 존재: `curl -s 127.0.0.1:11434/api/tags | grep qwen2.5vl` — 없으면 `ollama pull qwen2.5vl:7b`.
- ringgo-server 와 Ollama 둘 다 맥미니 → localhost 통신 OK.

## 검증
- TestClient: 번호정규화·JSON추출(코드펜스)·빈이미지400·Ollama실패시 200+빈필드(500 아님)·응답형태유지·ocr/terms 유지. ALL OK.
- 실제 사업자등록증 1장 OCR 정확도는 배포 후 사장님 확인(OCR 완벽X → 앱이 저장 전 검토).
- 미배포: bash server/deploy_phase1.sh
