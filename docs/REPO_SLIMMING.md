# 저장소 용량 다이어트 런북 (맥미니에서 실행)

> 작성: cowork · 목적: 워크스페이스/`.git` 용량 줄이기
> 현재 상태: 워크스페이스 전체 **487MB** = `.git` **258MB** + `app/` 182MB(대부분 빌드찌꺼기) + 나머지

비유: 용량은 두 군데에 낌.
1. **설거지거리(빌드 찌꺼기)** — 앱 빌드할 때마다 생기는 임시물. 지워도 다음 빌드 때 자동 복구. **안전.**
2. **창고(.git)** — 지난 모든 버전을 다 기억. 옛날 실수로 커밋된 앱 설치파일(.aab 2개 ≈ 40MB)이 영영 박혀 있음. 작업폴더에서 파일을 지워도 창고엔 남아서, 창고를 "다시 써야" 빠짐 → 조심.

---

## A. 빌드 찌꺼기 청소 (안전 · 즉시 · git 영향 없음)

```bash
cd ~/paperclip-company/workspaces/CallFollowCRM
rm -rf app/build .gradle build server/.venv
```

- `app/build` `.gradle` `build` : 빌드 캐시. 다음 `./gradlew` 빌드 때 자동 재생성.
- `server/.venv` : 리포 안 개발용 가상환경. **실서버는 `~/ringgo-server` 에서 따로 도니 영향 없음.** 나중에 서버 코드 로컬 실행할 때만 재생성하면 됨.
- 예상 확보: **약 150MB+**.

---

## B. `.git` 다이어트 — .aab 40MB 히스토리에서 제거

> ⚠️ 이건 **창고 기록을 통째로 다시 쓰는** 작업입니다.
> 데스크탑(안드로이드)과 맥미니 두 대가 같은 창고를 공유하므로,
> 끝나면 **양쪽 다 새로 맞춰야(re-clone 권장)** 합니다.
> 진행 중이던 브랜치/작업이 꼬일 수 있으니 반드시 아래 0번부터.

### 0. 두 대 다 저장 후 멈춤
- 데스크탑·맥미니 각각: 하던 작업 `git add . && git commit && git push` 로 다 올리고, 작업 중단.

### 1. 백업 (맥미니)
```bash
cd ~/paperclip-company/workspaces
cp -R CallFollowCRM CallFollowCRM_backup_$(date +%Y%m%d)
```

### 2. 도구 설치
```bash
brew install git-filter-repo     # 안 되면:  pip3 install git-filter-repo
```

### 3. .aab / .apk 를 히스토리 전체에서 제거
```bash
cd ~/paperclip-company/workspaces/CallFollowCRM
git filter-repo --path-glob '*.aab' --path-glob '*.apk' --invert-paths --force
```
- `--invert-paths` = "이 파일들만 빼고 나머지는 그대로 유지".

### 4. 재발 방지 — .gitignore 에 규칙 추가
```bash
printf '\n# 릴리즈 번들/설치파일은 git에 올리지 않음\n*.aab\n*.apk\n' >> .gitignore
git add .gitignore
git commit -m "chore: ignore .aab/.apk (repo slimming)"
```

### 5. 창고 압축 (찌꺼기 실제로 비우기)
```bash
git reflog expire --expire=now --all
git gc --prune=now --aggressive
```

### 6. 리모트 다시 연결 + 강제 push
> filter-repo 는 안전상 `origin` 을 자동으로 떼어냄. 다시 붙여야 함.
```bash
git remote add origin https://github.com/hunkim2080/CallFollowCRM.git
git push origin --force --all
git push origin --force --tags
```

### 7. 데스크탑(안드로이드 쪽) 맞추기
히스토리가 통째로 바뀌었으므로 **새로 clone 이 가장 안전**:
```bash
# 기존 폴더는 백업만 해두고
git clone https://github.com/hunkim2080/CallFollowCRM.git
```
급하면(주의해서):
```bash
git fetch origin && git reset --hard origin/main
```

---

## 예상 효과 / 주의
- `.git` 258MB → **약 210~215MB** (.aab 40MB + gc 압축분). 극적이진 않지만 확실히 줄어듦.
- `main.py`(2MB짜리 큰 파일)가 커밋마다 통째로 쌓인 것도 `.git` 을 키우는 요인이나, 이걸 더 줄이려면 히스토리를 더 공격적으로 재작성해야 해서 **비용 대비 실익이 적음 → 권장 안 함.**
- filter-repo 후 옛 커밋 해시가 바뀌므로, GitHub 의 옛 커밋 링크/PR 참조는 깨질 수 있음.
- A(빌드 찌꺼기)만 해도 눈에 보이는 디스크는 크게 줄어듦. B 는 "창고 정리"라 급하지 않으면 두 대 여유 있을 때.
