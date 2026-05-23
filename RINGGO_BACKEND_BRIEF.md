# RING-GO 안드로이드 앱 — 백엔드 연동 브리핑
## 프로젝트 개요
RING-GO 는 한국어 문장을 자연스럽게 다듬어주는 안드로이드 앱이다. LLM 추론은 집에 있는 Mac mini 위의 Ollama 가 담당하고, 폰과 Mac mini 는 Tailscale 사설 네트워크로 연결돼 있다.
## 백엔드 정보 (셋업 완료, 즉시 호출 가능)

⚠️ 100.86.114.49 는 Tailnet IP 다. 폰과 Mac mini 모두 같은 Tailscale 계정에 로그인돼 있어야만 도달한다. 일반 인터넷에서는 안 보인다.
## 동작 확인된 호출 예시
### /api/chat (권장 — 시스템 프롬프트 + 멀티턴 가능)
curl http://100.86.114.49:11434/api/chat \

  -H "Content-Type: application/json" \

  -d '{

    "model": "gpt-oss:20b",

    "stream": false,

    "messages": [

      {"role": "system", "content": "너는 한국어 문장을 자연스럽게 다듬어주는 도우미다. 결과만 짧게 답해."},

      {"role": "user",   "content": "이 문장 다듬어줘: 나는 오늘 학교에 가요"}

    ]

  }'
### 응답 스키마 (stream=false)
{

  "model": "gpt-oss:20b",

  "created_at": "2026-05-20T08:17:31.072538Z",

  "message": {

    "role": "assistant",

    "content": "안녕",

    "thinking": "The user says: ..."   // ← gpt-oss 의 내부 추론. UI 에는 절대 노출하지 말 것.

  },

  "done": true,

  "done_reason": "stop",

  "total_duration": 9348339917,

  "load_duration": 5275142417,

  "prompt_eval_count": 74,

  "prompt_eval_duration": 388002292,

  "eval_count": 95,

  "eval_duration": 3602702540

}

핵심:

message.content 만 사용자에게 보여준다.
message.thinking 은 디버깅 외엔 무시한다.
모든 *_duration 은 나노초 단위.
### 스트리밍 ("stream": true)
응답이 NDJSON (줄마다 JSON) 형태로 흘러나온다. SSE 가 아니라 일반 chunked response 이므로, OkHttp 의 ResponseBody.source() 로 \n 단위로 끊어 파싱하면 된다.

각 청크 형태:

{"model":"gpt-oss:20b","message":{"role":"assistant","content":"안"},"done":false}

{"model":"gpt-oss:20b","message":{"role":"assistant","content":"녕"},"done":false}

{"model":"gpt-oss:20b","message":{"role":"assistant","content":""},"done":true,"done_reason":"stop", ...}

누적해서 화면에 붙여나가면 타자 치는 효과가 된다.
## 안드로이드 측 필수 설정
### AndroidManifest.xml
<uses-permission android:name="android.permission.INTERNET" />

<application

    android:usesCleartextTraffic="true"   <!-- HTTP 라서 필수 -->

    ...>

더 안전하게 가려면 res/xml/network_security_config.xml 만들고 100.86.114.49 만 허용:

<?xml version="1.0" encoding="utf-8"?>

<network-security-config>

    <domain-config cleartextTrafficPermitted="true">

        <domain includeSubdomains="false">100.86.114.49</domain>

    </domain-config>

</network-security-config>

이후 application 태그에 android:networkSecurityConfig="@xml/network_security_config" 추가.
### Tailscale 의존성
폰에 Tailscale 안드로이드 앱이 설치되어 있고 로그인되어 있어야 함.
배터리 최적화 예외 처리 권장 (Tailscale 백그라운드 유지).
앱 시작 시 BASE_URL 에 /api/tags 한 번 ping 해서 도달 가능한지 체크 → 실패 시 "Tailscale 연결을 확인하세요" 안내.
## 권장 아키텍처
[Activity / Compose UI]

        ↓

[ViewModel]  ←─ StateFlow 로 응답 토큰 emit

        ↓

[RefineRepository]            ← LLM 추상화 인터페이스

        ↓

[OllamaClient (현재)]   ←→  [GeminiClient (미래)]

        ↓

   OkHttp + Moshi/kotlinx.serialization

중요: RefineRepository 를 인터페이스로 두고 구현체를 갈아 끼울 수 있게 설계할 것. 나중에 Gemini API 로 마이그레이션 가능성이 있다 (사용자가 품질 부족 느끼면 전환 예정).

인터페이스 시그니처 예시:

interface RefineRepository {

    suspend fun refine(input: String, system: String? = null): Result<String>

    fun refineStream(input: String, system: String? = null): Flow<String>

}
## 추천 라이브러리
네트워크: OkHttp + Retrofit (혹은 Ktor Client)
JSON: kotlinx.serialization (또는 Moshi)
비동기: Kotlin Coroutines + Flow
DI: Hilt
UI: Jetpack Compose
## 첫 마일스톤 제안
빈 Compose 프로젝트 + 네트워크/DI 셋업
OllamaClient 로 /api/chat non-stream 호출 → 입력 EditText / 결과 Text 의 가장 간단한 화면
작동 확인 후 스트리밍 버전으로 업그레이드
에러 처리 (Tailscale 안 붙어있을 때, 타임아웃, 모델 로딩 중)
시스템 프롬프트 튜닝 (한국어 다듬기 결과 품질 올리기)
## 향후 마이그레이션 메모
백엔드가 Gemini API 로 바뀌면 OllamaClient → GeminiClient 만 교체.
그때는 API 키 보안을 위해 Mac mini 를 프록시로 둘 수 있음 (BASE_URL 만 바꾸면 됨).
두 구현체 모두 같은 RefineRepository 인터페이스를 따르도록 설계 유지.

| 항목 | 값 |
| --- | --- |
| BASE_URL | http://100.86.114.49:11434 |
| 프로토콜 | HTTP (HTTPS 아님 — Tailscale 내부망) |
| 모델 | gpt-oss:20b (OpenAI open-weights, 20.9B MoE, MXFP4) |
| 채팅 엔드포인트 | POST /api/chat |
| 단건 생성 엔드포인트 | POST /api/generate |
| 모델 목록 확인 | GET /api/tags |
| 응답 시간 | 첫 호출 10초 (모델 로드), 이후 35초 |
