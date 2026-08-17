# MMS 처리 방식 검토 회신 (Fable/cowork → android) · 2026-07-02

> `MMS 처리 방식 (검토용)` 문서에 대한 검토. §8/§11 질문 순서대로 답하고,
> 끝에 우선순위 로드맵. 이미 실패한 시도(replace, 빈-결과 가드)는 재제안하지 않음.

---

## 총평

**현재 구조는 과하지 않다. 제약(기본앱 아님 + OneUI 쿼크 + 8,900건)을 감안하면 각 단계가 전부 실제 사고(문제 A~D)로 "번 돈"이다.** 특히 "MMS 캐시는 어디서도 replace 하지 않는다 = merge-only" 는 이 시스템의 **핵심 불변식(invariant)** 으로 승격해서 코드 주석/테스트로 못박길 권장. 이 불변식 덕분에 아래 개선들(특히 thread 직접 조회)이 **구조적으로 안전**해진다 — 이게 이 검토의 가장 중요한 결론.

성능의 진짜 병목은 "전역 2000행" 자체보다 **행마다 addr round-trip** (최대 2000회). 이걸 먼저 없애는 게 가성비 최고다 (→ Q1-a 로스터 캐시).

---

## Q1. thread_id 직접 조회로 가도 안전한가?

**결론: 간다. 단, 2단계로. 그리고 merge-only 덕분에 안전성은 "검증"이 아니라 "구조"로 보장된다.**

핵심 논리: 현재도 새 thread 의 발견은 suffix 매칭(전역 스캔)이 하고 있다. thread 직접 조회는 **"이미 발견한 thread 의 과거를 깊게 파는"** 용도로만 쓰면, 결과는 순수하게 **추가(additive)** 다. merge-only 캐시에서 추가는 아무것도 부수지 못한다. 즉 thread 집합이 불완전해도 **오늘보다 나빠질 수 있는 경로가 없다.** (placeholder 로 놓치는 케이스 = 오늘도 Pass3 시드 없으면 놓치는 케이스와 동일. 직접 조회가 새 유실을 만들지 않음.)

### 1-a. 1단계 (먼저, 저위험 고효율): thread 로스터 캐시 — addr round-trip 제거

- Room 테이블 신설: `mms_thread_roster(thread_id PK, addressesCsv, updated_at)`.
- `getMmsAddresses(mmsId)` 결과를 그 행의 thread_id 로스터에 **union 누적** (`insert-address-token` 은 저장 제외).
- 스캔 시: 행의 thread_id 가 로스터에 있으면 **addr 조회 없이** 로스터 주소로 suffix 매칭. 없는 thread 만 addr 조회 → 로스터에 기록.
- 근거: thread 의 수신자 집합은 사실상 불변 (thread 자체가 수신자 집합으로 정의됨). 한 대화의 어떤 행은 placeholder 여도 다른 행에서 실주소를 얻으면 로스터가 채워짐 → placeholder 복원력이 지금보다 **좋아짐**.
- 효과: addr round-trip 이 **행 수 → 신규 thread 수**로 감소. 2000행이 ~수백 thread 라면 첫 스캔부터 수 배, 워밍업 후엔 거의 0. 매칭 로직 자체는 동일 데이터 기반이라 회귀 위험 최소.

### 1-b. 2단계: 깊은 스캔을 thread 직접 조회로 교체

- Room 테이블: `contact_threads(suffix, thread_id)` — 지금까지 그 번호로 매칭된 thread_id 들 (SMS 시드 ∪ MMS addr 매칭 = 현행 Pass2/3 결과를 저장만 하면 됨).
- 첫 진입 깊은 스캔(현행 전역 2000) → `content://mms WHERE thread_id IN (…) ORDER BY date DESC` 직접 조회로 교체.
- **보너스**: 전역 2000행 밖으로 밀린 **그 대화의 진짜 옛 사진까지 전부** 가져옴 — 현행보다 커버리지가 오히려 넓어짐.
- 얕은 스캔(80, 전역)은 **신규 thread 발견용으로 유지** (새 번호/새 thread 는 최근에 오므로 80 안에 잡힘).
- 검증: 출시 직후엔 debug 빌드에서 "직접 조회 결과 개수 vs 전역 2000 결과 개수" 비교 로그만 심어 1~2주 관찰. 불일치 나와도 merge-only 라 사용자 피해는 0, 로그로 원인만 수집.
- `Threads.getOrCreateThreadId()` 는 **쓰지 말 것** — 없는 thread 를 생성하는 부작용. thread 수집은 위처럼 "관측된 것 저장"으로 충분.

---

## Q2. 감지 — 프로세스 생존 의존 탈출

**WorkManager 의 content URI 트리거를 쓰면 프로세스가 죽어 있어도 OS 가 깨워준다.** (JobScheduler TriggerContentUri 의 WorkManager 포장. API 24+, minSdk 26 OK.)

```kotlin
val constraints = Constraints.Builder()
    .addContentUriTrigger(Uri.parse("content://mms"), true /*descendants*/)
    .build()
val work = OneTimeWorkRequestBuilder<MmsSyncWorker>()
    .setConstraints(constraints)
    // setTriggerContentUpdateDelay / MaxDelay 로 debounce (예: 5s / 30s)
    .build()
```

- content 트리거는 **OneTime 전용** → Worker 끝에서 **자기 자신을 재등록** (표준 패턴).
- Worker 는 가볍게: "마지막으로 본 max(_id)" 를 prefs 에 저장 → `_id > lastSeen` 인 행 있나만 확인 → 있으면 syncMmsContacts + (필요시) 알림.
- 배터리: 이벤트 구동이라 15분 폴링보다 오히려 쌈. 15분 주기 백스톱은 **불필요** — content 트리거가 놓치는 케이스가 드물고, 앱 복귀 시 sync 가 이미 안전망.
- 포그라운드의 기존 3-URI observer + 60초 폴링은 그대로 (더 빠른 반응).

---

## Q3. 캐시 성장 — 지금은 trim 하지 말 것

- **수치로 보면 겁낼 크기가 아님**: 캐시 행 = 메타데이터+URI 문자열 (~수백 byte). 이미지 바이트는 시스템 provider 에 있고 Coil 이 그때그때 로드. 10,000행 ≈ 수 MB. 사장님 폰 전체(8,900 MMS)를 다 넣어도 문제없는 규모.
- **더 중요한 이유**: 현행 전역-스캔 체제에서 trim 으로 지운 옛 행은 **재획득 불가** (전역 2000 밖) → trim = 사진 유실과 동일. 문제 B/D 를 다시 만드는 것.
- **순서**: Q1-b (thread 직접 조회) 가 들어와 "깊은 스캔이 대화 전체를 다시 채울 수 있게 된 후"에만 trim 도입. 그때 정책: 대화별 최근 1,000행 유지 + 90일 이내는 무조건 보존, 일 1회 배치. 그 전까지는 debug 화면에 행 수 카운터만 노출해 관찰.

## Q4. retry(3×50ms)

- 유지하되 **지수 백오프**로: 50/150/450ms (총 ~650ms 상한). 균등 50ms 3연발은 경합이 살아있는 동안 또 부딪힐 확률이 높음.
- merge-only 전환 후 retry 의 역할이 "유실 방지"에서 "지연 단축"으로 바뀌었음을 인지 — 실패해도 다음 스캔이 주워담으므로 retry 를 늘릴 이유는 없음. 3회면 충분.

## Q5. 날짜 단위

- ×1000 처리 일관되면 OK. 한 가지: 같은 초에 여러 MMS(연속 사진 전송) 는 date 동률 → 정렬 키를 `(date, _id)` 복합으로. (MMS `_id` 는 단조 증가라 안전한 tie-breaker.)

## Q6. 전체 구조

- 3-stage + 깊은/얕은 분기 + merge = **유지.** 각 조각이 실제 사고의 해답이고, 제거하면 그 사고가 돌아옴.
- 단순화는 Q1-b 이후 자연스럽게 옴: 깊은 스캔이 thread 직접 조회가 되면 scanLimit 휴리스틱(2000/500/80)이 "대화는 직접 조회 + 발견용 소형 전역 스캔" 으로 수렴 — 더 단순해지면서 더 빨라짐. 지금 미리 단순화하지 말 것.

---

## 놓친 함정 (질문 6번 답)

1. **RCS (제일 큼)** — 삼성 "채팅+"(RCS) 로 온 사진은 `content://mms` 에 **아예 안 들어옴** (삼성 전용 DB, 접근 불가). 상대가 RCS 로 보내면 앱엔 사진이 영영 안 뜸. 공개 API 해법 없음. 대응: (a) 이 한계를 알고 있기, (b) 사장님 폰은 메시지 설정에서 채팅+ 끄면 MMS 로 수신됨, (c) "사진이 안 보여요" CS 오면 1순위로 RCS 의심.
2. **그룹 MMS** — addr suffix 매칭은 그 번호가 낀 **단체 MMS** 도 1:1 챗스크린에 끌어옴 (그룹 thread_id 는 1:1 과 다르지만 Pass2 주소 매칭엔 걸림). 의도인지 확인. 아니면 addr 의 수신자 행 수 >1 인 thread 는 표시 제외 또는 "단체" 라벨.
3. **msg_box 4/5 (OUTBOX/FAILED)** — 현재 INBOX/SENT 만 → 전송 실패한 사진은 앱에서 안 보임. 사장님이 "보냈는데 앱에 없다" 할 수 있음. 추후 FAILED 를 "전송실패" 라벨로 표시 고려.
4. **기기 이사/복원** — 새 폰에서 systemId·part URI 가 전부 바뀜 → 옛 캐시 행의 이미지 URI 가 죽은 링크. Coil 로드 실패가 반복되는 행은 무효 표시(또는 그 폰의 첫 깊은 스캔 후 재매핑) 필요. 당장은 낮은 우선순위, 알고만 있기.
5. 멀티심(sub_id)·part text charset 은 현행 방식으로 문제없음.

---

## 권장 로드맵 (우선순위)

| 순위 | 작업 | 효과 | 위험 |
|---|---|---|---|
| P0 (출시 후 첫 cycle) | Q1-a thread 로스터 캐시 | 스캔 수 배~수십 배 가속 (addr round-trip 제거) | 낮음 (동일 데이터, memoization) |
| P0 | Q2 WorkManager content 트리거 | 백그라운드 감지 (프로세스 생존 의존 탈출) | 낮음 |
| P1 | Q1-b thread 직접 조회 (debug 비교 로그와 함께) | 첫 진입 수 초 → 사실상 즉시 + 옛 사진 커버리지 확대 | merge-only 덕에 구조적 안전 |
| P1 | 정렬 키 (date,_id), retry 백오프 | 미세 견고성 | 없음 |
| P2 | trim (Q1-b 이후에만), FAILED 표시, 그룹 MMS 정책 | 위생 | Q1-b 선행 필수 |
| 상시 | RCS 한계 인지 (문서/CS) | 유령 버그 예방 | — |

**출시 2일 전인 지금은 아무것도 건드리지 말 것.** 현 구조는 출시 감당 가능. 위는 전부 출시 후.

— cowork/Fable (2026-07-02)
