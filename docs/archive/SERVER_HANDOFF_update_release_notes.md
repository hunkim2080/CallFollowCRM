# 서버 핸드오프 — 업데이트 배너 '변경 내역'(release notes) (android → cowork, 2026-07-18)

## 사장님 요청
> "업데이트를 하면 '업데이트 하세요'만 뜨는 게 아니라, **어떤 부분이 업데이트 됐는지** 볼 수 있으면 좋겠다. 그래야 다운받을 때 안심하고 받는다."

## 앱은 이미 다 됨 (이 커밋)
- `UpdateChecker.checkUpdate()` 가 `/api/download/version` 응답에서 **`notes`** 를 파싱(배열 또는 줄바꿈 텍스트 둘 다 허용, 앞의 `-·•` 정리, 최대 8줄).
- `AppPreferences.latestReleaseNotes` 저장 → `HomeViewModel.latestReleaseNotes` 노출 → **홈 업데이트 배너에 "이번 업데이트 내용" 불릿으로 표시**(최대 5줄 + "…외 N건").
- **notes 가 없으면** 배너는 지금과 동일(문구만). = 서버 미구현이어도 안전.

## 서버가 할 것 — `/api/download/version` 응답에 `notes` 추가

현재 응답:
```json
{"available":true,"size_bytes":...,"version":"v0.3-beta","version_code":1047,"mtime_iso":"..."}
```
추가:
```json
{ ..., "notes": ["통화요약이 이름으로 저장한 파일도 자동으로 찾아요", "가격표가 새로 설치하면 빈 표로 시작해요", "고객 정보 화면이 탭으로 깔끔해졌어요"] }
```

### 어디서 읽나 (권장: 파일)
- APK 옆에 **`apk/release_notes.txt`** 파일 — **한 줄에 변경 하나**. 안드로이드(제가) 가 새 APK 올릴 때 이 파일도 같이 scp 로 올림.
- 서버는 `/api/download/version` 처리에서 그 파일을 읽어 줄 단위로 `notes` 배열에 넣어주면 됨(없으면 `notes` 생략/빈 배열).
  ```python
  notes_path = os.path.join(APK_DIR, "release_notes.txt")
  notes = []
  if os.path.exists(notes_path):
      with open(notes_path, encoding="utf-8") as f:
          notes = [ln.strip() for ln in f if ln.strip()]
  # 응답 dict 에 notes 추가 (있을 때만)
  if notes: resp["notes"] = notes
  ```
- ⚠️ `release_notes.txt` 는 **항상 현재 올라간 APK 기준**(배포할 때마다 갱신) → 버전 키 매핑 불필요. (안드로이드가 배포 시 APK 와 함께 최신본으로 덮어씀)

## 배포 워크플로우 변경(안드로이드 = 나)
- release APK scp 할 때 `release_notes.txt` 도 같이 scp. (예: 이번 것 아래 §샘플)
- 문구는 **사장님/테스터가 읽을 한국어 한 줄씩**. 기술 용어 X(초등학생도 이해).

## §샘플 (이번 0.2.1048 배포용 release_notes.txt)
```
통화 요약: 이름으로 저장되는 녹음 파일도 자동으로 찾아요
문구 넣기: 붙여둔 사진이 목록에 보이고, 저장할 때 사진도 같이 저장돼요
가격표: 새로 설치하면 빈 표로 시작해요 (남의 가격 안 뜸)
고객 정보 화면을 탭으로 깔끔하게 정리했어요
협업 완료를 누르면 확인 소리가 나요
```

## 검증
- cowork 반영 후 `curl https://si0in.kr/api/download/version` 응답에 `notes` 배열이 있는지.
- 앱: 구버전 폰에서 홈 배너에 "이번 업데이트 내용" 불릿이 뜨는지.

## SYNC
- 시작 전 `git pull --rebase` + `tail -100 docs/SYNC.md`. 끝난 후 SYNC append + commit + push.

— android (데스크탑 Claude Code)
