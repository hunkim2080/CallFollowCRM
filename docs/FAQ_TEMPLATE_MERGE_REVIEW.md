# 저장한 말 · 손님 FAQ 통합 검수 기록

## 변경 내용

- 일반 문구와 FAQ의 저장 원본은 `message_templates` 하나다. 새 항목은 기본 비공개다.
- 문구 편집에 「손님 FAQ에 공개」 토글을 추가했다. 공개 시 질문·답·분류를 편집하고, 첫 첨부가 발행 사진이 된다.
- FAQ 관리 목록·발행·AI 초안 등록을 템플릿으로 연결했다. AI 등록은 공개 문구로 저장된다는 안내를 표시한다.
- FAQ의 업체 소개·커버·슬로건·페이지 발행 설정은 유지했다. 직접 작성한 새 항목은 공용 문구 편집기로 들어가며 공개 토글을 직접 켠다.
- 채팅 ⊕의 대표질문 버튼을 제거했다. 문구 버튼은 유지했다. 기존 FAQ의 웹 강조 표식은 문자 입력 시 공용 규칙으로 제거한다.
- 목록에 「FAQ 공개」 딱지를 표시한다. 더보기 진입 이름은 「저장한 말」·「손님 FAQ」다.
- 템플릿에는 순서 칸이 없어 FAQ 전용 끌기 정렬은 제거했다. 공개 목록과 발행은 같은 `observePublic()` 순서를 쓴다.
- 편집 저장·AI 일괄 등록은 Room 트랜잭션으로 묶었다. 저장 연타를 막고, 사용자 저장/사진 실패는 SaveGuard로 알린다.

## 비파괴 이사

- 사장님이 이미 추가한 v78 `ALTER TABLE message_templates ADD COLUMN isPublic INTEGER NOT NULL DEFAULT 0`·엔티티·DAO는 유지했다.
- `AppContainer` 시작 코루틴에서 `mergeRepQuestionsIntoTemplates()`를 실행한다. 완료 플래그 기본값은 false다.
- 질문→title, 답→body, 분류→category를 그대로 복사한다. `isDefault=false`, `isActive=true`, `isPublic=false`이며 두 시각은 이사 시각이다.
- 사진은 기존 `TemplateAttachmentRepository.add()`를 재사용한다. 새 templateId, 원본 URI, 이름 「사진」, `image/*`, 순서 0, 같은 생성시각으로 등록한다.
- 전체 복사가 성공해야 완료 플래그를 동기 기록한다. 실패는 로그를 남기고 앱 다음 실행에서 재시도한다.
- Room과 prefs 사이의 종료 시점도 처리한다. DB 커밋 전에 복사 ID·생성시각을 prefs에 기록하고, 재실행 시 그 행들이 실제 남아 있으면 완료 플래그만 재시도한다. 롤백되어 행이 없으면 다시 복사한다. 추가 DB 테이블/컬럼은 없다.
- 원본 `rep_questions`는 수정·삭제하지 않는다. 문구 편집에서 사진을 제거해도 원본 대표질문이 참조하는 사진 파일은 보존한다.
- 서버에 이미 발행된 페이지의 갱신은 기존과 같이 FAQ 관리의 「최신 내용으로 올리기」로 수행한다. 공개 토글을 저장하는 것만으로 원격 페이지를 자동 갱신하지 않는다.

## 검증

- 최종 빌드/시험: 진행 중. 완료 후 이 절을 갱신한다.
- 실제 SQLite로 v77 스키마를 만들고 코드의 77→78 ALTER를 실행해 생성된 v78 JSON의 모든 테이블 컬럼과 일치함을 확인했다.
- SQLite에서 기존 문구의 isPublic=0 기본값, 대표질문 원본/사진 URI 보존, 실제 observePublic SQL이 비공개를 제외하고 명시 공개 항목만 선택하는 것을 확인했다.
- 저장소에는 `MigrationInsertColumnsTest` 클래스가 없다. 실제 존재하는 `DbUpgradeChainTest`와 위 SQLite 대조 검증을 사용했다.
- 이사 회귀 시험은 실제 이사 함수와 저장소를 사용하며 DAO·트랜잭션 경계는 메모리 대역이다. Android 기기에서의 실제 Room 업그레이드/화면 조작 시험은 하지 않았다.
- 커밋·푸시·배포·실서버 발행·기기 설치는 하지 않았다. 서버/손님 페이지 소스 변경 없음.

## 빌드 명령

기본 요청 명령은 Gradle 다운로드가 네트워크 제한에 막혔다. 작업공간에 이미 있는 캐시를 사용하고 Kotlin 데몬의 작업공간 밖 쓰기를 피하기 위해 아래 로컬 옵션으로 검증했다. 가드 건너뛰기·기준선 변경은 없다.

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:GRADLE_USER_HOME='D:\dev\CallFollowCRM\.gradle\week-validation'
.\gradlew.bat assembleRelease :app:testReleaseUnitTest --tests 'com.detailline.callfollowcrm.data.repository.RepQuestionTemplateMergeTest' --tests 'com.detailline.callfollowcrm.data.local.DbUpgradeChainTest' --tests 'com.detailline.callfollowcrm.domain.faq.RepQuestionRulesTest' health -x :app:uploadCrashlyticsMappingFileRelease --console=plain --offline '-Pkotlin.compiler.execution.strategy=in-process' '-Dorg.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8'
```

## 변경 파일

최종 검증 후 목록을 갱신한다.
