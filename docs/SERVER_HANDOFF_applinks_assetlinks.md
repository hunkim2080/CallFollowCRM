# SERVER HANDOFF — App Links 검증 파일 (assetlinks.json) 호스팅

작성: 2026-06-08 · 안드로이드 Claude → 맥미니 Claude
관련: 협업 현장 공유 링크(App Link). 앱 manifest 에 `autoVerify="true"` 박음.

## 왜
앱이 `https://api.si0in.kr/shared/{share_id}` (그리고 `si0in.kr`) 링크를 **브라우저 안 거치고 바로** 열려면,
안드로이드가 "이 도메인이 이 앱을 인증했나?"를 **`/.well-known/assetlinks.json`** 으로 확인한다.
이 파일이 없으면 링크 탭 시 브라우저로 열리거나 "어떤 앱으로 열까요?" 가 떠서 흐름이 깨진다.

## 맥미니가 할 일
**아래 JSON 을 두 경로에서 그대로 서빙** (Content-Type: `application/json`, 200, 리다이렉트 없이):
- `https://api.si0in.kr/.well-known/assetlinks.json`
- `https://si0in.kr/.well-known/assetlinks.json`  ← si0in.kr 루트 도메인을 Cloudflare Tunnel 에 추가한 뒤

내용 (= repo 의 `docs/assetlinks.json` 그대로):
```json
[
  {
    "relation": ["delegate_permission/common.handle_all_urls"],
    "target": {
      "namespace": "android_app",
      "package_name": "com.detailline.callfollowcrm",
      "sha256_cert_fingerprints": [
        "4B:C6:27:28:45:43:98:B8:9F:F9:D0:BD:41:02:9C:D6:6F:1D:39:7B:42:84:F0:61:5B:BD:26:71:86:4B:22:EE"
      ]
    }
  }
]
```
- 이 SHA256 = **릴리즈 키(ringgo-release.jks) 인증서 지문.** (debug 빌드로 테스트하면 debug 키 지문도 배열에 추가해야 함 — 아래)

FastAPI 예시:
```python
from fastapi.responses import JSONResponse
ASSETLINKS = [ ... 위 JSON ... ]
@app.get("/.well-known/assetlinks.json")
async def assetlinks():
    return JSONResponse(ASSETLINKS)
```
(정적 파일로 서빙해도 됨. 단 `application/json` + 200 + 본문 정확히 일치.)

## 검증 (배포 후)
- 브라우저로 `https://api.si0in.kr/.well-known/assetlinks.json` 열어 JSON 보이는지.
- 구글 검증기: `https://digitalassetlinks.googleapis.com/v1/statements:list?source.web.site=https://api.si0in.kr&relation=delegate_permission/common.handle_all_urls`
- 폰에서 release APK 설치 후 링크 탭 → RING-GO 가 바로 열리면 OK.

## 참고 (앱 측 — 이미 됨)
- manifest intent-filter(autoVerify) + MainActivity 가 `/shared/{share_id}` 파싱 → 협업 현장 화면 자동 열기.
- ⚠️ **debug 빌드로 테스트 시**: debug 키 SHA256 도 배열에 추가해야 autoVerify 됨. debug 지문:
  `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android | grep SHA256`
  (정식 배포는 release 지문만으로 충분.)
- ⚠️ App Link 자동검증은 **https + 실제 도메인**에서만. `INTAKE_PUBLIC_BASE_URL` 이 `https://api.si0in.kr` 여야 공유 링크가 App Link 가 됨(현재 기본값이 Tailnet IP 라면 prod env 에서 override 확인).
