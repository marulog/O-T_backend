# layered-architecture-refactor - 작업 계획

## TL;DR (For humans)
<!-- 상세 계획을 작성한 뒤 마지막에 채워 실제 계획을 요약한다. -->
<!-- 비개발자도 이해할 수 있는 한국어로 작성하며 파일 경로, Todo 번호, Wave/에이전트/도구 이름은 제외한다. -->

**결과물:** 기존 세 서비스를 명시적인 프레젠테이션, 애플리케이션, 도메인, 인프라스트럭처 경계로 재구성한다. 공통 오류와 페이징 모델은 웹 기술에 독립적으로 만들고, 데이터베이스 코드는 도메인 모듈 밖으로 이동하며, 각 애플리케이션은 선언된 모듈만 로드한다.

**이 접근을 선택한 이유:** 현재 비즈니스 모델과 배포 구성을 유지하면서 의존 방향을 강제할 수 있다. 전통적인 하위 인프라 의존은 허용하므로 헥사고날 전환에 따른 비용과 불필요한 추상화 증가를 피한다.

**하지 않는 것:** API, DB 마이그레이션, 메시지 형식, 트랜잭션, 외부 호출 순서, 비즈니스 규칙을 변경하지 않는다. 리포지토리/유스케이스 포트를 추가하거나 외부 연동을 재설계하지 않는다.

**예상 규모:** XL
**위험도:** 높음 - 영속성 패키지와 공통 계약이 세 애플리케이션 모두에 영향을 주며, 범위 내 파일 대부분에 이미 커밋되지 않은 사용자 변경이 있다.
**확인할 핵심 결정:** 배포되지 않는 `common-core` 모듈 하나를 추가한다. 애플리케이션 서비스의 하위 인프라 계층 직접 의존을 허용한다. 기능 DTO는 공통 페이징과 인증 사용자 경계를 제외하고 유지한다.

다음 선택: 이 계획으로 실행을 시작하거나, 먼저 선택 사항인 이중 고정밀 계획 리뷰를 요청한다. 아래에 전체 실행 세부사항이 이어진다.

---

> TL;DR (기계용): XL/고위험 레이어드 리팩토링. common-core 추가, 영속성 코드를 infra-db로 이동, 웹/보안 요청 컨텍스트 누수 제거, 전역 스캔 교체, 외부 계약 보존.

## Scope
### 반드시 포함
- 배포 애플리케이션 3개와 기존 모듈을 모두 유지하고, 배포되지 않는 라이브러리 모듈 `:modules:common-core` 하나만 추가한다.
- 목표 의존 방향:
  - 프레젠테이션(`controller`, API 문서, 웹 응답 매핑) -> 애플리케이션(`service`, reader/writer, 기능 DTO) -> `domain` 엔티티와 하위 `infra-*` 서비스/리포지토리.
  - 애플리케이션 서비스가 `infra-db` 리포지토리와 기존 구체 `common-security`/`infra-*` 서비스를 직접 참조하는 전통적 하향 의존은 허용한다.
  - `common-core`는 JDK/Lombok에만 의존하고, `common-web`과 `common-security`는 `common-core`에 의존할 수 있다.
- 모든 JPA 엔티티, 엔티티 열거형/값 타입, QueryDSL 생성 Q 타입은 `modules/domain`에 유지한다.
- 직접 작성한 모든 Spring Data 리포지토리 인터페이스, 커스텀 조각, 구현체, 쿼리 프로젝션/행 모델, JDBC 리포지토리, `QueryDslConfig`를 `modules/domain`에서 `modules/infra-db`로 이동한다.
- `BusinessException`, HTTP 비종속 `ErrorCode`, `PageResult<T>`, `PageMetadata`를 `modules/common-core`로 이동한다.
- `ErrorResponse`, `GlobalExceptionHandler`, `PageResponse`, `PageInfo`는 `common-web`에 유지하고, 모든 오류 코드를 포괄하는 HTTP 상태 및 페이지 응답 매퍼를 추가한다.
- 애플리케이션 service/reader/writer 시그니처에서 Spring Security `Authentication`을 제거한다. 컨트롤러가 이를 `api-admin` 소유의 `AdminActor(memberId, roleKeys)`로 변환한다. 인증 서비스 내부의 기존 `JwtTokenProvider` 사용은 허용한다.
- 기존 기능 요청/응답 DTO는 앱 소유 애플리케이션 DTO로 유지한다. 저장소 전체 DTO 재작성을 하지 않는다.
- 세 애플리케이션의 `@ComponentScan(basePackages = "com.ott")`를 앱 로컬 기본 스캔과 재사용 모듈 설정의 명시적 import로 교체한다.
- 결정론적인 JUnit/MockMvc/ArchUnit/Testcontainers 검증을 추가하고, 비활성화된 앱 컨텍스트 테스트를 활성화하며, 모든 Gradle 테스트 실패가 빌드를 실패시키게 한다.
- REST 경로/메서드, JSON 필드, HTTP 숫자 상태, 오류 코드/메시지, 인가 동작, `TranscodeMessage`, Flyway 파일, S3 키 형식, 스케줄 주기, 리스너 ID, 트랜잭션 애너테이션, 외부 호출 순서를 보존한다.

### 반드시 제외
- 새로운 배포 애플리케이션, DB 스키마/마이그레이션, 엔드포인트, 메시지 필드, 공개 설정 계약을 추가하지 않는다.
- 유스케이스 포트, 리포지토리 포트, 아웃바운드 어댑터 인터페이스를 추가하거나 기존 transcoder의 포트 형태 인터페이스를 확장하지 않는다.
- 트랜잭션 경계, 보상, 재시도, outbox, Kakao, AI/Gemini, S3, RabbitMQ, FFmpeg, 상태 머신 동작을 재설계하지 않는다.
- 패키지 모양만 맞추기 위한 기능 DTO 이동을 하지 않는다.
- 의존성/프레임워크 버전 업그레이드, 전체 포맷 정리, 무관한 정리, 생성 파일 수동 편집을 하지 않는다.
- `git reset`, `git checkout`, `git clean`, `git stash`를 사용하거나 기존 사용자 변경을 되돌리지 않는다.
- 자동 커밋하지 않는다. 아래 커밋 문구는 사용자가 별도로 커밋을 승인하기 전까지 체크포인트용 초안이다.

## Verification strategy
> 사람의 수동 개입 없이 모든 검증을 에이전트가 실행한다.
- 테스트 전략: JUnit 5, Mockito/MockMvc, ArchUnit, Testcontainers를 사용하는 특성화 우선 TDD.
- 모든 구조 변경 Todo는 다음 순서를 따른다.
  1. 가장 작은 관련 특성화/아키텍처 단언을 추가하거나 강화하고 RED를 기록한다.
  2. 소스/패키지/빌드 변경을 적용한다.
  3. 같은 단언을 실행해 GREEN을 기록한다.
  4. 영향 모듈 테스트를 실행하고 JUnit XML을 검사한다.
- 모든 Gradle 테스트 명령 뒤에 실행할 표준 JUnit 게이트:
  `bash .omo/qa/assert-junit-xml.sh <module>/build/test-results/test`
  - PASS: `TEST-*.xml`이 하나 이상 존재하고 `<failure>`, `<error>`, 승인되지 않은 skip 테스트가 없다.
  - FAIL: XML이 없거나 failure/error 요소가 있거나 컨텍스트/아키텍처 테스트가 skip되었다.
- 테스트 인프라:
  - JPA/Flyway/컨텍스트 테스트에는 Testcontainers MySQL을 사용한다.
  - `api-user` 조립 테스트에는 범용 Redis 컨테이너를 사용한다.
  - RabbitMQ 컨테이너는 MQ wiring 테스트에서만 사용하고, 일반 단위 테스트에서는 리스너를 비활성화한다.
  - 더미 AWS/OAuth/JWT/AI 설정과 mock 클라우드/Kakao/AI/프로세스 클라이언트를 사용하며 실제 외부 API를 호출하지 않는다.
- 실제 표면 게이트: `docker compose`로 `mysql`, `redis`, `rabbitmq`, `api-user`, `api-admin`, `transcoder`를 빌드·실행한 뒤 각 actuator health 엔드포인트를 `curl -fsS`로 호출한다.
- 증거 루트: `.omo/evidence/task-<N>-layered-architecture-refactor/`.
- dirty worktree 규칙: 각 Wave 전에 `git status --porcelain=v1 -z`, 바이너리 unstaged/staged 패치, 해당 Wave 경로의 SHA-256 해시를 저장한다. 변경된 파일을 쓰기 전에 다시 읽고 병합하며, 파일 전체를 덮어쓰지 않는다.

## Execution strategy
### 병렬 실행 Wave
> Wave당 Todo 5~8개를 목표로 한다. 최종 Wave를 제외하고 3개 미만이면 과소 분할이다.
- Wave 1, 안전망과 재현 가능한 테스트: Todo 1이 증거 도구를 만든 뒤 Todo 2~5를 병렬 실행할 수 있다.
- Wave 2, 공통/애플리케이션 경계: Todo 6~10. Todo 6이 7~10을 막으며, 이후 Todo 8~10은 병렬 실행할 수 있다.
- Wave 3, 영속성 이동: Todo 11~16. Todo 11이 12~16을 막고, 서로 다른 경로를 소유하는 리포지토리 묶음 12~15를 병렬 실행한 뒤 Todo 16에서 합친다.
- Wave 4, 명시적 조립과 규칙 강제: Todo 17~22. Todo 17~18을 병렬 실행하고, 이어서 Todo 19~21을 병렬 실행한 뒤 Todo 22에서 합친다.
- 핵심 경로: `1 -> 2-5 -> 6 -> 7-10 -> 11 -> 12-15 -> 16 -> 17-18 -> 19-21 -> 22 -> F1-F4`.

### 의존성 행렬
| Todo | 선행 Todo | 후속 차단 | 병렬 가능 |
| --- | --- | --- | --- |
| 1 | 없음 | 2-22 | 없음 |
| 2 | 1 | 6-22 | 3-5 |
| 3 | 1 | 6, 22 | 2, 4, 5 |
| 4 | 1 | 7-10, 22 | 2, 3, 5 |
| 5 | 1 | 11, 22 | 2-4 |
| 6 | 2-4 | 7-10, 17, 19-21 | 없음 |
| 7 | 6 | 8, 9, 17, 19, 20 | 10 |
| 8 | 6, 7 | 19, 22 | 9, 10 |
| 9 | 6, 7 | 20, 22 | 8, 10 |
| 10 | 6 | 21, 22 | 7-9 |
| 11 | 2, 5 | 12-16 | 없음 |
| 12 | 11 | 16, 19, 20 | 13-15 |
| 13 | 11 | 16, 19-21 | 12, 14, 15 |
| 14 | 11 | 16, 19, 20 | 12, 13, 15 |
| 15 | 11 | 16, 19-21 | 12-14 |
| 16 | 12-15 | 19-22 | 없음 |
| 17 | 6, 7 | 19, 20 | 18 |
| 18 | 11, 16 | 19-21 | 17 |
| 19 | 8, 12-18 | 22 | 20, 21 |
| 20 | 9, 12-18 | 22 | 19, 21 |
| 21 | 10, 13, 15-18 | 22 | 19, 20 |
| 22 | 19-21 | F1-F4 | 없음 |

## Todos
> 구현과 테스트는 하나의 Todo로 묶고 분리하지 않는다.
<!-- 이 줄 아래 Todo 묶음만 edit/apply_patch로 추가하고 위 헤더는 다시 작성하지 않는다. -->

### Wave 1 - 안전망과 재현 가능한 테스트

- [ ] 1. dirty worktree 기준선을 기록하고 증거 게이트 구성
  - 작업 내용:
    - JUnit XML이 반드시 존재해야 하며 `<failure>`, `<error>`, skip된 `*ApplicationTests`/`*ArchitectureTest`가 있으면 실패하는 `.omo/qa/assert-junit-xml.sh`를 만든다.
    - `git status --porcelain=v1 -z`, `git diff --binary`, `git diff --cached --binary`, 현재 Gradle 프로젝트 간선, REST 매핑 애너테이션, 스케줄/리스너 애너테이션, 마이그레이션 해시, 범위 내 모든 경로의 SHA-256 해시를 기록한다.
    - 실제 `project(...)` 선언에서 현재 모듈 그래프를 기록하고, 오래된 `api-admin -> infra-redis` 주장을 재사용하지 않는다.
  - 금지 사항: 이 Todo에서는 제품/빌드 소스를 수정하지 않는다. 증거 기록이 불완전하면 실패한다.
  - 병렬화: Wave 1 | 선행: 없음 | 차단: 2-22.
  - 참조:
    - `settings.gradle`
    - `apps/api-user/build.gradle`
    - `apps/api-admin/build.gradle`
    - `apps/transcoder/build.gradle`
    - `.omo/drafts/layered-architecture-refactor.md`
  - 인수 조건:
    ```bash
    bash .omo/qa/capture-wave-baseline.sh wave-1 apps modules build.gradle settings.gradle &&
    test -f .omo/evidence/task-1-layered-architecture-refactor/status-before.z &&
    test -s .omo/evidence/task-1-layered-architecture-refactor/module-edges.txt &&
    test -s .omo/evidence/task-1-layered-architecture-refactor/source-hashes.sha256 &&
    bash -n .omo/qa/assert-junit-xml.sh .omo/qa/capture-wave-baseline.sh
    ```
  - QA 시나리오:
    - 정상, shell: 위 명령을 실행한다. 모든 증거 파일이 존재하고 두 스크립트의 구문이 유효하면 PASS.
    - 실패, shell: `bash .omo/qa/assert-junit-xml.sh .omo/evidence/does-not-exist`를 실행한다. “no JUnit XML”과 함께 0이 아닌 종료 코드면 PASS.
    - 증거: `.omo/evidence/task-1-layered-architecture-refactor/`.
  - 커밋: N | `chore(qa): add refactor evidence gates`

- [ ] 2. 테스트 실패를 치명적으로 처리하고 결정론적 컨텍스트 테스트 인프라 구성
  - 작업 내용:
    - `rg -n 'ignoreFailures\\s*=\\s*true|@Disabled' apps/api-user apps/api-admin`으로 RED를 기록한다.
    - 두 앱 빌드에서 `ignoreFailures = true`를 제거한다.
    - 기존 버전을 올리지 않고 ArchUnit과 Testcontainers MySQL/Redis/RabbitMQ 공통 테스트 의존성을 추가한다.
    - 앱별 `application-test.yml`을 추가하고 `ApiUserApplicationTests`, `ApiAdminApplicationTests`를 활성화한다. 컨테이너/더미 설정을 구성하고 실제 리스너를 비활성화하며 AWS/Kakao/AI/FFmpeg 클라이언트를 mock 처리한다.
    - 운영 기본값과 스케줄/리스너 동작은 유지한다.
  - 금지 사항: 테스트 skip/비활성화, 단언 약화, 개발자 소유 `.env` 요구.
  - 병렬화: Wave 1 | 선행: 1 | 차단: 6-22.
  - 참조:
    - `build.gradle`
    - `apps/api-user/build.gradle`
    - `apps/api-admin/build.gradle`
    - `apps/api-user/src/test/java/com/ott/api_user/ApiUserApplicationTests.java`
    - `apps/api-admin/src/test/java/com/ott/api_admin/ApiAdminApplicationTests.java`
    - `apps/transcoder/src/test/java/com/ott/transcoder/TranscoderApplicationTests.java`
    - 세 앱의 모든 `application.yml`.
  - 인수 조건:
    ```bash
    ! rg -n 'ignoreFailures\\s*=\\s*true|@Disabled' \
      apps/api-user/build.gradle apps/api-admin/build.gradle \
      apps/api-user/src/test/java/com/ott/api_user/ApiUserApplicationTests.java \
      apps/api-admin/src/test/java/com/ott/api_admin/ApiAdminApplicationTests.java &&
    docker info >/dev/null &&
    ./gradlew :apps:api-user:test :apps:api-admin:test :apps:transcoder:test \
      --tests '*ApplicationTests' --rerun-tasks &&
    bash .omo/qa/assert-junit-xml.sh apps/api-user/build/test-results/test &&
    bash .omo/qa/assert-junit-xml.sh apps/api-admin/build/test-results/test &&
    bash .omo/qa/assert-junit-xml.sh apps/transcoder/build/test-results/test
    ```
  - QA 시나리오:
    - 정상, Gradle/Testcontainers: 위 명령을 그대로 실행한다. 세 컨텍스트가 모두 로드되고 XML에 실패가 없으면 PASS.
    - 실패, Gradle: `./gradlew :apps:api-user:test --tests '*DoesNotExist' --rerun-tasks`; Gradle이 0이 아닌 코드로 종료하면 PASS.
    - 증거: `.omo/evidence/task-2-layered-architecture-refactor/`.
  - 커밋: N | `build(test): make context failures deterministic and fatal`

- [ ] 3. 모든 오류 코드, HTTP 매핑, 보안 오류 응답 특성화
  - 작업 내용:
    - 현재 모든 `ErrorCode` 상수의 enum 이름, 코드, 메시지, HTTP 숫자 상태를 파라미터화 fixture로 추가한다.
    - `BusinessException`, validation, 잘못된 JSON, 본문 누락, 허용되지 않은 메서드, 미인증, 권한 없음, 잘못된 토큰, 만료 토큰에 대한 MockMvc/handler 테스트를 추가한다.
    - 현재 JSON 필드 `success`, `code`, `message`, `status`, `timestamp`, `errors`, `detail`을 기록한다.
  - 금지 사항: 이 Todo에서 클래스 이동 또는 상태 매핑 변경.
  - 병렬화: Wave 1 | 선행: 1 | 차단: 6, 22.
  - 참조:
    - `modules/common-web/src/main/java/com/ott/common/web/exception/ErrorCode.java`
    - `BusinessException.java`
    - `ErrorResponse.java`
    - `GlobalExceptionHandler.java`
    - `modules/common-security/src/main/java/com/ott/common/security/handler/JwtAuthenticationEntryPoint.java`
    - `JwtAccessDeniedHandler.java`.
  - 인수 조건:
    ```bash
    ./gradlew :modules:common-web:test :modules:common-security:test \
      --tests '*ErrorContractTest' --tests '*SecurityErrorContractTest' --rerun-tasks &&
    bash .omo/qa/assert-junit-xml.sh modules/common-web/build/test-results/test &&
    bash .omo/qa/assert-junit-xml.sh modules/common-security/build/test-results/test
    ```
  - QA 시나리오:
    - 정상, JUnit/MockMvc: `BusinessException(ErrorCode.CONTENTS_NOT_FOUND)`가 기존 `B101`/404 payload를 생성한다.
    - 실패, JUnit/MockMvc: 잘못된 JSON은 기존 `C005`/400을 생성하고 `C999`를 생성하지 않는다.
    - 증거: `.omo/evidence/task-3-layered-architecture-refactor/`.
  - 커밋: N | `test(errors): characterize error and security contracts`

- [ ] 4. 페이징 및 관리자 사용자 인가 경계 특성화
  - 작업 내용:
    - 데이터가 있는 페이지와 빈 페이지를 포함해 `PageResponse`/`PageInfo` JSON fixture를 추가한다.
    - 현재 `Authentication`을 받는 모든 관리자 메서드에 대해 ADMIN, 소유자인 EDITOR, 소유자가 아닌 EDITOR 서비스/컨트롤러 테스트를 추가한다.
    - 모든 service/reader/writer의 `PageResponse`, `PageInfo`, `Authentication` import를 목록화한다.
  - 금지 사항: 이 Todo에서 시그니처 또는 매핑 변경.
  - 병렬화: Wave 1 | 선행: 1 | 차단: 7-10, 22.
  - 참조:
    - `modules/common-web/src/main/java/com/ott/common/web/response/PageResponse.java`
    - `PageInfo.java`
    - `apps/api-admin/src/main/java/com/ott/api_admin/shortform/service/BackOfficeShortFormReader.java`
    - `BackOfficeShortFormService.java`
    - `BackOfficeShortFormWriter.java`
    - `apps/api-admin/src/main/java/com/ott/api_admin/ingest_job/service/BackOfficeIngestJobService.java`.
  - 인수 조건:
    ```bash
    ./gradlew :modules:common-web:test :apps:api-admin:test \
      --tests '*PageResponseContractTest' --tests '*ActorAuthorizationContractTest' --rerun-tasks &&
    bash .omo/qa/assert-junit-xml.sh modules/common-web/build/test-results/test &&
    bash .omo/qa/assert-junit-xml.sh apps/api-admin/build/test-results/test &&
    rg -n 'PageResponse|PageInfo|Authentication' \
      apps/api-user/src/main/java apps/api-admin/src/main/java \
      -g '*Service.java' -g '*Reader.java' -g '*Writer.java' \
      > .omo/evidence/task-4-layered-architecture-refactor/leakage-before.txt
    ```
  - QA 시나리오:
    - 정상, JUnit/Jackson: 데이터가 있는 페이지가 `pageInfo.currentPage`, `totalPage`, `pageSize`, `dataList`를 유지한다.
    - 실패, JUnit: 소유자가 아닌 EDITOR는 현재 `FORBIDDEN` 코드/상태로 거부된다.
    - 증거: `.omo/evidence/task-4-layered-architecture-refactor/`.
  - 커밋: N | `test(boundaries): characterize paging and actor authorization`

- [ ] 5. 변경 불가 API, 영속성, MQ, 스토리지, 런타임 등록 계약 스냅샷
  - 작업 내용:
    - 컨트롤러 메서드/경로 애너테이션, 오류 fixture 데이터, OpenAPI 페이징 스키마, `TranscodeMessage` 직렬화 필드 순서, Flyway 파일 13개, S3 object key 결과, 트랜잭션 애너테이션, 스케줄 메서드, 이벤트/리스너 애너테이션, Rabbit 리스너 ID/큐를 스냅샷으로 저장한다.
    - `TranscodeMessage` 직렬화와 `UploadHelper` 키 생성기에 대한 집중 테스트를 추가한다.
  - 금지 사항: Flyway 파일, 메시지 record, 애너테이션, 키 생성 코드 변경.
  - 병렬화: Wave 1 | 선행: 1 | 차단: 11, 22.
  - 참조:
    - `modules/infra-mq/src/main/java/com/ott/infra/mq/TranscodeMessage.java`
    - `modules/infra-db/src/main/resources/db/migration/`
    - `apps/api-admin/src/main/java/com/ott/api_admin/upload/support/UploadHelper.java`
    - `apps/api-admin/src/main/java/com/ott/api_admin/outbox/poller/OutboxPoller.java`
    - `apps/transcoder/src/main/java/com/ott/transcoder/queue/rabbit/`
    - 모든 controller 패키지.
  - 인수 조건:
    ```bash
    ./gradlew :modules:infra-mq:test :apps:api-admin:test \
      --tests '*TranscodeMessageContractTest' --tests '*UploadHelperTest' --rerun-tasks &&
    bash .omo/qa/assert-junit-xml.sh modules/infra-mq/build/test-results/test &&
    bash .omo/qa/assert-junit-xml.sh apps/api-admin/build/test-results/test &&
    test "$(find modules/infra-db/src/main/resources/db/migration -type f | wc -l)" -eq 13 &&
    sha256sum modules/infra-db/src/main/resources/db/migration/*.sql \
      > .omo/evidence/task-5-layered-architecture-refactor/flyway.sha256
    ```
  - QA 시나리오:
    - 정상, JUnit/Jackson: 대표 `TranscodeMessage`가 동일한 이름/순서로 직렬화된다.
    - 실패, shell: fixture의 순서를 의도적으로 바꾼 복사본과 비교한다. `cmp`가 0이 아닌 코드로 종료하면 PASS.
    - 증거: `.omo/evidence/task-5-layered-architecture-refactor/`.
  - 커밋: N | `test(contracts): snapshot external and runtime contracts`

### Wave 2 - 공통 및 애플리케이션 경계

- [ ] 6. `common-core` 추가 및 중립 오류와 HTTP 상태 매핑 분리
  - 작업 내용:
    - `com.ott.common.core..`와 서비스가 Spring Web에 의존할 수 없다는 ArchUnit 규칙을 추가해 RED를 기록한다.
    - `settings.gradle`과 `modules/common-core/build.gradle`에 `:modules:common-core`를 추가한다.
    - `BusinessException`과 `ErrorCode`를 `com.ott.common.core.error`로 이동하고 `ErrorCode`에서 `HttpStatus`를 제거한다.
    - `common-web`에 모든 오류 코드를 포괄하는 `ErrorHttpStatusMapper`를 추가하고 `ErrorResponse`, `GlobalExceptionHandler`, common-security handler를 갱신한다.
    - `common-web`, `common-security`가 `common-core`에 의존하게 하고, 사용되지 않는 `common-security -> domain` 의존성을 제거한다.
  - 금지 사항: enum 코드/메시지/상태 또는 보안 응답 본문 변경.
  - 병렬화: Wave 2 | 선행: 2-4 | 차단: 7-10, 17, 19-21.
  - 참조:
    - `settings.gradle`
    - `modules/common-web/build.gradle`
    - `modules/common-security/build.gradle`
    - common-web 예외 파일
    - common-security JWT/filter/handler 파일.
  - 인수 조건:
    ```bash
    ./gradlew :modules:common-core:compileJava :modules:common-web:test \
      :modules:common-security:test --rerun-tasks &&
    bash .omo/qa/assert-junit-xml.sh modules/common-web/build/test-results/test &&
    bash .omo/qa/assert-junit-xml.sh modules/common-security/build/test-results/test &&
    ! rg -n 'org\\.springframework|io\\.swagger|jakarta\\.servlet' \
      modules/common-core/src/main/java &&
    ! rg -n 'HttpStatus' modules/common-core/src/main/java
    ```
  - QA 시나리오:
    - 정상, JUnit: 모든 ErrorCode fixture가 기존과 동일한 숫자 상태와 본문으로 매핑된다.
    - 실패, JUnit: `ErrorHttpStatusMapper`에서 enum 상수 하나를 누락하면 포괄 매퍼 테스트가 실패한다.
    - 증거: `.omo/evidence/task-6-layered-architecture-refactor/`.
  - 커밋: N | `refactor(errors): separate core errors from HTTP mapping`

- [ ] 7. 중립 페이징 계약과 웹 어댑터 추가
  - 작업 내용:
    - `common-core`에 `PageMetadata(currentPage,totalPage,pageSize)`와 `PageResult<T>(pageMetadata,dataList)`를 추가한다.
    - 기존 `PageInfo`/`PageResponse`로 변환하는 `PageResponseMapper`를 `common-web`에 추가한다.
    - 기존 직렬화 필드 이름과 OpenAPI 애너테이션은 `common-web`에 유지한다.
  - 금지 사항: 컨트롤러에서 `PageResult` 직접 반환 또는 `PageResponse` JSON 변경.
  - 병렬화: Wave 2 | 선행: 6 | 차단: 8, 9, 17, 19, 20 | 병렬 가능: 10.
  - 참조:
    - `modules/common-web/src/main/java/com/ott/common/web/response/PageInfo.java`
    - `PageResponse.java`
    - Todo 4의 페이징 fixture.
  - 인수 조건:
    ```bash
    ./gradlew :modules:common-core:test :modules:common-web:test \
      --tests '*Page*Test' --rerun-tasks &&
    bash .omo/qa/assert-junit-xml.sh modules/common-core/build/test-results/test &&
    bash .omo/qa/assert-junit-xml.sh modules/common-web/build/test-results/test
    ```
  - QA 시나리오:
    - 정상, JUnit/Jackson: `PageResult`가 기준 JSON과 바이트 단위로 동일하게 매핑된다.
    - 실패, JUnit/Jackson: 빈 `dataList`는 `null`이 아니라 `[]`로 유지된다.
    - 증거: `.omo/evidence/task-7-layered-architecture-refactor/`.
  - 커밋: N | `refactor(paging): add core result and web adapter`

- [ ] 8. API-user 페이징 서비스를 `PageResult`로 전환
  - 작업 내용:
    - service 패키지의 `common.web.response` import를 거부하도록 api-user ArchUnit 규칙을 강화하고 RED를 기록한다.
    - bookmark, comment, playlist, radar preference, search, series, shortform의 페이징 반환 서비스를 `PageResult`로 전환한다.
    - 기존 `PageResponse`로의 매핑은 컨트롤러에서만 수행한다.
    - 기존 결과 단언을 약화하지 않고 테스트를 갱신한다.
  - 금지 사항: 페이징과 무관한 기능 DTO 또는 비즈니스 쿼리 재작성.
  - 병렬화: Wave 2 | 선행: 6, 7 | 차단: 19, 22 | 병렬 가능: 9, 10.
  - 참조:
    - `apps/api-user/src/main/java/com/ott/api_user/bookmark/service/BookmarkService.java`
    - `comment/service/CommentService.java`
    - `playlist/service/PlaylistService.java`
    - `playlist/service/PlaylistStrategyService.java`
    - `radar_preference/service/RadarPreferenceService.java`
    - `search/service/SearchService.java`
    - `series/service/SeriesService.java`
    - `shortform/service/ShortFormFeedService.java`.
  - 인수 조건:
    ```bash
    ./gradlew :apps:api-user:test --rerun-tasks &&
    bash .omo/qa/assert-junit-xml.sh apps/api-user/build/test-results/test &&
    ! rg -n '^import com\\.ott\\.common\\.web\\.response' \
      apps/api-user/src/main/java -g '*Service.java' -g '*Reader.java' -g '*Writer.java'
    ```
  - QA 시나리오:
    - 정상, MockMvc: 대표 search/playlist/shortform 응답이 Todo 4 fixture와 일치한다.
    - 실패, MockMvc: 범위를 벗어난 페이지가 기존과 동일한 빈 목록과 메타데이터 동작을 반환한다.
    - 증거: `.omo/evidence/task-8-layered-architecture-refactor/`.
  - 커밋: N | `refactor(api-user): remove web paging from services`

- [ ] 9. API-admin 페이징 및 `Authentication` 서비스 시그니처 전환
  - 작업 내용:
    - `com.ott.api_admin.common.application.AdminActor(Long memberId, Set<String> roleKeys)`를 추가한다.
    - `Authentication`을 `AdminActor`로 변환하는 프레젠테이션 매퍼를 추가한다.
    - 컨트롤러 아래 계층의 `Authentication`/`common.web.response` 사용을 거부하도록 ArchUnit 규칙을 강화하고 RED를 기록한다.
    - content, ingest-job, member, series, shortform, upload 페이징을 `PageResult`로 전환한다. 모든 admin service/reader/writer의 `Authentication` 파라미터를 `AdminActor`로 전환한다.
    - 컨트롤러는 Spring Security 타입을 유지하고 진입 지점에서 한 번만 매핑한다.
  - 금지 사항: ADMIN/EDITOR 소유권 결정 변경 또는 보안 포트 도입.
  - 병렬화: Wave 2 | 선행: 6, 7 | 차단: 20, 22 | 병렬 가능: 8, 10.
  - 참조:
    - admin shortform service/reader/writer 및 controller
    - `apps/api-admin/src/main/java/com/ott/api_admin/ingest_job/service/BackOfficeIngestJobService.java`
    - `content/service/`
    - `member/service/`
    - `series/service/`
    - Todo 4의 사용자 fixture.
  - 인수 조건:
    ```bash
    ./gradlew :apps:api-admin:test --rerun-tasks &&
    bash .omo/qa/assert-junit-xml.sh apps/api-admin/build/test-results/test &&
    ! rg -n 'org\\.springframework\\.security\\.core\\.Authentication|com\\.ott\\.common\\.web\\.response' \
      apps/api-admin/src/main/java -g '*Service.java' -g '*Reader.java' -g '*Writer.java'
    ```
  - QA 시나리오:
    - 정상, JUnit/MockMvc: ADMIN과 소유자인 EDITOR가 기준 응답을 받는다.
    - 실패, JUnit/MockMvc: 소유자가 아닌 EDITOR는 동일한 코드/상태로 계속 거부된다.
    - 증거: `.omo/evidence/task-9-layered-architecture-refactor/`.
  - 커밋: N | `refactor(api-admin): isolate paging and authenticated actor`

- [ ] 10. transcoder/공통 애플리케이션의 웹 누수 제거 및 경계 규칙 고정
  - 작업 내용:
    - transcoder와 컨트롤러가 아닌 앱 클래스의 common-web 오류 사용을 common-core 오류로 변경한다.
    - 인증 서비스는 `JwtTokenProvider`와 기존 구체 하위 계층 서비스를 계속 사용할 수 있다. 프레젠테이션 아래에서 금지되는 것은 요청 컨텍스트/웹 응답 타입이다.
    - 다음 ArchUnit 규칙을 추가한다.
      - service/reader/writer는 Spring Web, `Authentication`, `common-web`을 import할 수 없다.
      - controller는 `JpaRepository`/`infra-db` 리포지토리를 직접 주입할 수 없다.
      - `common-core`는 Spring/Swagger에 의존할 수 없다.
      - 앱 모듈은 다른 앱을 import할 수 없다.
  - 금지 사항: transcoder 리스너, 재시도, 프로세스, 스토리지, 예외 동작 변경.
  - 병렬화: Wave 2 | 선행: 6 | 차단: 21, 22 | 병렬 가능: 7-9.
  - 참조:
    - `apps/transcoder/src/main/java/com/ott/transcoder/command/CommandExtractor.java`
    - `job/IngestJobStatusManager.java`
    - `pipeline/thumbnail/ThumbnailCommandPipeline.java`
    - `apps/api-user/src/main/java/com/ott/api_user/auth/service/AuthService.java`
    - `apps/api-admin/src/main/java/com/ott/api_admin/auth/service/AdminAuthService.java`.
  - 인수 조건:
    ```bash
    ./gradlew :apps:api-user:test :apps:api-admin:test :apps:transcoder:test \
      --tests '*ArchitectureTest' --tests '*TranscoderApplicationTests' --rerun-tasks &&
    bash .omo/qa/assert-junit-xml.sh apps/api-user/build/test-results/test &&
    bash .omo/qa/assert-junit-xml.sh apps/api-admin/build/test-results/test &&
    bash .omo/qa/assert-junit-xml.sh apps/transcoder/build/test-results/test &&
    ! rg -n '^import com\\.ott\\.common\\.web' apps/transcoder/src/main/java
    ```
  - QA 시나리오:
    - 정상, JUnit: 기존 transcoder 예외 코드/메시지 동작이 유지된다.
    - 실패, ArchUnit fixture: `PageResponse`를 import하는 서비스가 거부된다.
    - 증거: `.omo/evidence/task-10-layered-architecture-refactor/`.
  - 커밋: N | `test(architecture): enforce application transport boundaries`

### Wave 3 - 영속성 이동

- [ ] 11. `infra-db` 소유권, Q 타입 생성, 명시적 영속성 설정 확립
  - 작업 내용:
    - Spring Data/JDBC 및 직접 작성한 QueryDSL runtime import를 금지하는 domain ArchUnit 규칙으로 RED를 기록한다.
    - 엔티티 애너테이션과 QueryDSL APT/Q 타입 생성은 `domain`에 유지한다. 넓은 Spring Data starter는 엔티티/생성 Q 타입에 필요한 최소 JPA/QueryDSL 컴파일 의존성으로 교체한다.
    - `infra-db`에 Spring Data JPA, QueryDSL JPA runtime, JDBC, MySQL runtime 의존성을 추가한다.
    - `QueryDslConfig`를 `com.ott.infra.db.config`로 이동한다.
    - 엔티티 및 리포지토리 패키지용 타입 안전 marker class를 사용하는 `InfraDbConfiguration`을 추가한다. 이 설정이 `@EntityScan`, `@EnableJpaRepositories`, JPA auditing, `JPAQueryFactory`를 소유한다.
  - 금지 사항: 엔티티/Q 타입 이동 또는 리포지토리 포트 추가.
  - 병렬화: Wave 3 | 선행: 2, 5 | 차단: 12-16.
  - 참조:
    - `modules/domain/build.gradle`
    - `modules/infra-db/build.gradle`
    - `modules/domain/src/main/java/com/ott/global/config/QueryDslConfig.java`
    - 현재 세 애플리케이션의 scan 애너테이션.
  - 인수 조건:
    ```bash
    ./gradlew :modules:domain:compileJava :modules:infra-db:test \
      --tests '*InfraDbConfigurationTest' --rerun-tasks &&
    bash .omo/qa/assert-junit-xml.sh modules/infra-db/build/test-results/test &&
    test -n "$(find modules/domain/build/generated -type f -name 'Q*.java' -print -quit)" &&
    ! rg -n 'JdbcTemplate|JPAQueryFactory|org\\.springframework\\.data' \
      modules/domain/src/main/java -g '*.java' --glob '!**/repository/**'
    ```
  - QA 시나리오:
    - 정상, Testcontainers MySQL: `InfraDbConfiguration`이 entity manager와 `JPAQueryFactory`를 생성한다.
    - 실패, context runner: marker 패키지 밖의 리포지토리는 발견되지 않는다.
    - 증거: `.omo/evidence/task-11-layered-architecture-refactor/`.
  - 커밋: N | `build(infra-db): establish persistence-layer ownership`

- [ ] 12. 사용자 및 분류 체계 리포지토리를 `infra-db`로 이동
  - 작업 내용:
    - `member`, `member_radar_preference`, `category`, `tag`, `preferred_tag`, `mood_category`, `mood_tag`, `media_mood_tag`, `media_tag`의 리포지토리 인터페이스/조각/구현체/프로젝션을 `com.ott.infra.db.<feature>.repository`로 이동한다.
    - 영향받는 user/admin 서비스와 테스트 import를 갱신한다.
    - 모든 메서드 시그니처, 쿼리 애너테이션, 커스텀 postfix, 반환 타입을 보존한다.
  - 금지 사항: 엔티티 패키지 또는 쿼리 의미 변경.
  - 병렬화: Wave 3 | 선행: 11 | 차단: 16, 19, 20 | 병렬 가능: 13-15.
  - 참조: `modules/domain/src/main/java/com/ott/domain/*/repository` 아래 해당 디렉터리와 import 사용처.
  - 인수 조건:
    ```bash
    ./gradlew :modules:infra-db:test :apps:api-user:test :apps:api-admin:test \
      --tests '*IdentityPersistenceIntegrationTest' --tests '*TaxonomyPersistenceIntegrationTest' \
      --rerun-tasks &&
    bash .omo/qa/assert-junit-xml.sh modules/infra-db/build/test-results/test &&
    test -z "$(find modules/domain/src/main/java/com/ott/domain \
      \( -path '*/member/repository' -o -path '*/category/repository' -o -path '*/tag/repository' \) \
      -type d -print -quit)"
    ```
  - QA 시나리오:
    - 정상, Testcontainers MySQL: member 저장/조회와 커스텀 media-tag 쿼리 하나를 실행한다.
    - 실패, JUnit: 중복/잘못된 영속성 제약이 현재 예외 동작을 유지한다.
    - 증거: `.omo/evidence/task-12-layered-architecture-refactor/`.
  - 커밋: N | `refactor(infra-db): move identity and taxonomy persistence`

- [ ] 13. 미디어 카탈로그 리포지토리를 `infra-db`로 이동
  - 작업 내용:
    - `media`, `contents`, `series`, `short_form` 리포지토리 인터페이스, 커스텀 조각, 구현체, 프로젝션을 이동한다.
    - 세 앱과 테스트의 import를 갱신한다.
    - QueryDSL 표현식, 정렬, 페이징, 노출 필터, 커스텀 구현체 명명 규칙을 보존한다.
  - 금지 사항: 쿼리 로직 또는 생성 Q 타입 재작성.
  - 병렬화: Wave 3 | 선행: 11 | 차단: 16, 19-21 | 병렬 가능: 12, 14, 15.
  - 참조:
    - `modules/domain/src/main/java/com/ott/domain/media/repository/`
    - `contents/repository/`
    - `series/repository/`
    - `short_form/repository/`.
  - 인수 조건:
    ```bash
    ./gradlew :modules:infra-db:test :apps:api-user:test :apps:api-admin:test \
      --tests '*MediaCatalogPersistenceIntegrationTest' \
      --tests '*BackOfficeContentsServiceTest' --tests '*ShortFormFeedServiceTest' \
      --rerun-tasks &&
    bash .omo/qa/assert-junit-xml.sh modules/infra-db/build/test-results/test &&
    bash .omo/qa/assert-junit-xml.sh apps/api-user/build/test-results/test &&
    bash .omo/qa/assert-junit-xml.sh apps/api-admin/build/test-results/test
    ```
  - QA 시나리오:
    - 정상, Testcontainers MySQL: 이동한 각 구현체 계열에서 커스텀 QueryDSL 메서드를 하나 이상 호출한다.
    - 실패, JUnit: 숨김/미완료 미디어가 기존과 동일하게 제외된다.
    - 증거: `.omo/evidence/task-13-layered-architecture-refactor/`.
  - 커밋: N | `refactor(infra-db): move media catalog persistence`

- [ ] 14. 참여, 재생, 시청 기록 리포지토리를 `infra-db`로 이동
  - 작업 내용:
    - `RecentWatchProjection`, `TagRankingProjection`, `TagViewCountProjection`을 포함해 `bookmark`, `likes`, `comment`, `click_event`, `playback`, `watch_history` 영속성 코드를 이동한다.
    - user/admin import와 테스트를 갱신한다.
    - soft-delete bulk update 플래그, 순위 계산, 페이징, 프로젝션 생성자를 보존한다.
  - 금지 사항: 회원 탈퇴 작업 순서 변경 또는 bulk update 의미 변경.
  - 병렬화: Wave 3 | 선행: 11 | 차단: 16, 19, 20 | 병렬 가능: 12, 13, 15.
  - 참조: 해당 domain 리포지토리 디렉터리와 `apps/api-user/src/main/java/com/ott/api_user/member/service/MemberService.java`.
  - 인수 조건:
    ```bash
    ./gradlew :modules:infra-db:test :apps:api-user:test :apps:api-admin:test \
      --tests '*EngagementPersistenceIntegrationTest' \
      --tests '*PlaybackPersistenceIntegrationTest' \
      --tests '*WatchHistoryServiceTest' --tests '*PlaybackServiceTest' \
      --rerun-tasks &&
    bash .omo/qa/assert-junit-xml.sh modules/infra-db/build/test-results/test &&
    bash .omo/qa/assert-junit-xml.sh apps/api-user/build/test-results/test
    ```
  - QA 시나리오:
    - 정상, Testcontainers MySQL: 커스텀 bookmark/playback/history 메서드가 기준 프로젝션을 반환한다.
    - 실패, JUnit: 권한 없는 comment/update와 비활성 history 필터링이 유지된다.
    - 증거: `.omo/evidence/task-14-layered-architecture-refactor/`.
  - 커밋: N | `refactor(infra-db): move engagement and playback persistence`

- [ ] 15. ingest, outbox, metrics, mood-refresh 리포지토리를 `infra-db`로 이동
  - 작업 내용:
    - `ingest_command`, `ingest_job`, `outbox`, `media_metrics`, `moodrefresh` 영속성 코드를 이동한다.
    - `MediaMetricsJdbcRepository`, 프로젝션/행 record, SQL 문자열은 패키지/import 변경을 제외하고 바이트 단위로 유지한다.
    - admin, user batch, transcoder의 import와 테스트를 갱신한다.
    - 잠금/쿼리 애너테이션과 outbox polling 동작을 보존한다.
  - 금지 사항: SQL, 트랜잭션 phase, outbox 상태, heartbeat, 재시도 의미 변경.
  - 병렬화: Wave 3 | 선행: 11 | 차단: 16, 19-21 | 병렬 가능: 12-14.
  - 참조:
    - 해당 domain 리포지토리 디렉터리
    - `apps/api-admin/src/main/java/com/ott/api_admin/outbox/`
    - `apps/api-user/src/main/java/com/ott/api_user/media_metrics/batch/`
    - `apps/transcoder/src/main/java/com/ott/transcoder/job/`.
  - 인수 조건:
    ```bash
    ./gradlew :modules:infra-db:test :apps:api-user:test :apps:api-admin:test \
      :apps:transcoder:test --tests '*IngestPersistenceIntegrationTest' \
      --tests '*MetricsJdbcIntegrationTest' --tests '*MoodRefreshServiceTest' \
      --rerun-tasks &&
    bash .omo/qa/assert-junit-xml.sh modules/infra-db/build/test-results/test &&
    bash .omo/qa/assert-junit-xml.sh apps/transcoder/build/test-results/test
    ```
  - QA 시나리오:
    - 정상, Testcontainers MySQL: outbox/ingest 커스텀 쿼리가 동작하고 JDBC metric upsert가 성공한다.
    - 실패, JUnit: 중복 outbox/ingest 제약과 잘못된 상태 전이가 기준 동작을 유지한다.
    - 증거: `.omo/evidence/task-15-layered-architecture-refactor/`.
  - 커밋: N | `refactor(infra-db): move ingest and metrics persistence`

- [ ] 16. 영속성 경계를 닫고 모든 커스텀 조각 검증
  - 작업 내용:
    - import 이동 후 기존 `domain/**/repository` 디렉터리를 모두 제거한다.
    - `domain` main 소스에 Spring Data, JDBC, repository stereotype, 직접 작성한 QueryDSL runtime import가 없는지 확인한다.
    - 리포지토리 bean 25개를 모두 resolve하고 13개 `*RepositoryImpl` 계열별 커스텀 메서드를 하나 이상 호출하는 파라미터화 통합 테스트를 추가한다.
    - `domain`, `infra-db`의 모듈/패키지 ArchUnit 규칙을 추가한다.
  - 금지 사항: 누락된 커스텀 조각을 mock으로 숨기기.
  - 병렬화: Wave 3 | 선행: 12-15 | 차단: 19-22.
  - 참조:
    - 이동한 모든 리포지토리 패키지
    - `modules/domain/build.gradle`
    - `modules/infra-db/build.gradle`.
  - 인수 조건:
    ```bash
    ./gradlew :modules:domain:compileJava :modules:infra-db:test \
      :apps:api-user:compileJava :apps:api-admin:compileJava \
      :apps:transcoder:compileJava --rerun-tasks &&
    bash .omo/qa/assert-junit-xml.sh modules/infra-db/build/test-results/test &&
    test -z "$(find modules/domain/src/main/java -type d -name repository -print -quit)" &&
    ! rg -n '^import com\\.ott\\.domain\\..*\\.repository|org\\.springframework\\.data|JdbcTemplate|JPAQueryFactory' \
      apps modules/domain/src/main/java -g '*.java'
    ```
  - QA 시나리오:
    - 정상, Testcontainers MySQL: 예상한 모든 리포지토리 bean/커스텀 조각이 resolve된다.
    - 실패, Spring context: 테스트 전용 조각의 postfix 하나를 바꾸면 fixture의 discovery가 실패한다.
    - 증거: `.omo/evidence/task-16-layered-architecture-refactor/`.
  - 커밋: N | `test(infra-db): prove complete repository relocation`

### Wave 4 - 명시적 조립과 규칙 강제

- [ ] 17. 명시적 `common-web`, `common-security` 설정 노출
  - 작업 내용:
    - 각 모듈의 bean만 명시적으로 import하거나 좁게 scan하는 루트 marker/configuration class를 추가한다.
    - `CommonWebConfiguration`이 MVC, Swagger, 응답 매핑, 예외 advice를 소유한다.
    - `CommonSecurityConfiguration`이 JWT provider/filter/handler/cookie utility를 소유한다.
    - 필수 bean이 하나씩 존재하고 앱 소유 bean이 제외됨을 증명하는 context runner 테스트를 추가한다.
  - 금지 사항: `com.ott` 전체 scan 또는 앱 패키지 의존.
  - 병렬화: Wave 4 | 선행: 6, 7 | 차단: 19, 20 | 병렬 가능: 18.
  - 참조: `modules/common-web`, `modules/common-security` 아래 모든 소스 파일.
  - 인수 조건:
    ```bash
    ./gradlew :modules:common-web:test :modules:common-security:test \
      --tests '*ConfigurationTest' --rerun-tasks &&
    bash .omo/qa/assert-junit-xml.sh modules/common-web/build/test-results/test &&
    bash .omo/qa/assert-junit-xml.sh modules/common-security/build/test-results/test &&
    ! rg -n '@ComponentScan\\([^)]*com\\.ott["\\)]' modules/common-web modules/common-security
    ```
  - QA 시나리오:
    - 정상, ApplicationContextRunner: 각 필수 bean의 개수가 1이다.
    - 실패, ApplicationContextRunner: 앱 controller가 두 모듈 컨텍스트 모두에 존재하지 않는다.
    - 증거: `.omo/evidence/task-17-layered-architecture-refactor/`.
  - 커밋: N | `feat(config): expose web and security module composition`

- [ ] 18. 명시적 S3, MQ, Redis, DB 설정 노출
  - 작업 내용:
    - `InfraDbConfiguration`을 완성한다.
    - 타입 안전 marker/좁은 import를 사용하는 `InfraS3Configuration`, `InfraMqConfiguration`, `InfraRedisConfiguration`을 추가한다.
    - 기존 bean 이름, 메시지 converter, 클라이언트 속성, 설정 기본값을 보존한다.
    - 더미 속성/컨테이너로 각 모듈을 독립 테스트하고 bean 개수가 1인지 단언한다.
  - 금지 사항: 실제 AWS 연결 또는 MQ/Redis payload/직렬화 변경.
  - 병렬화: Wave 4 | 선행: 11, 16 | 차단: 19-21 | 병렬 가능: 17.
  - 참조:
    - `modules/infra-s3/src/main/java/com/ott/infra/s3/config/`
    - `modules/infra-s3/src/main/java/com/ott/infra/s3/service/S3PresignService.java`
    - `modules/infra-mq/src/main/java/com/ott/infra/mq/config/MqMessageConfig.java`
    - `modules/infra-redis/src/main/java/com/ott/infra/redis/config/RedisConfig.java`
    - `modules/infra-db/src/main/java/com/ott/infra/db/config/`.
  - 인수 조건:
    ```bash
    ./gradlew :modules:infra-db:test :modules:infra-s3:test \
      :modules:infra-mq:test :modules:infra-redis:test \
      --tests '*ConfigurationTest' --rerun-tasks &&
    bash .omo/qa/assert-junit-xml.sh modules/infra-db/build/test-results/test &&
    bash .omo/qa/assert-junit-xml.sh modules/infra-s3/build/test-results/test &&
    bash .omo/qa/assert-junit-xml.sh modules/infra-mq/build/test-results/test &&
    bash .omo/qa/assert-junit-xml.sh modules/infra-redis/build/test-results/test
    ```
  - QA 시나리오:
    - 정상, context test: 각 모듈이 의도한 bean만 정확히 노출한다.
    - 실패, context test: 더미 AWS/JWT/속성이 누락되면 네트워크 호출이 아니라 명확한 binding 오류로 실패한다.
    - 증거: `.omo/evidence/task-18-layered-architecture-refactor/`.
  - 커밋: N | `feat(infra): expose explicit module configurations`

- [ ] 19. API-user 전역 스캔을 명시적 조립으로 교체
  - 작업 내용:
    - `ApiUserApplication`에서 전역 `@ComponentScan`, 직접 entity scan, 직접 repository scan을 제거한다.
    - `com.ott.api_user` 기본 스캔은 유지한다.
    - `CommonWebConfiguration`, `CommonSecurityConfiguration`, `InfraDbConfiguration`, `InfraRedisConfiguration`을 import한다.
    - 소스 import에 필요한 경우에만 `domain`과 모듈 직접 의존을 유지하고 오래된 의존성을 제거한다.
    - security filter, exception advice, Redis, 리포지토리, scheduler, async listener가 존재하고 admin/transcoder bean이 0개인지 단언한다.
  - 금지 사항: scheduler 주기, OAuth/JWT 동작, 엔드포인트 매핑 변경.
  - 병렬화: Wave 4 | 선행: 8, 12-18 | 차단: 22 | 병렬 가능: 20, 21.
  - 참조:
    - `apps/api-user/src/main/java/com/ott/api_user/ApiUserApplication.java`
    - `apps/api-user/build.gradle`
    - `TrendingCacheService.java`
    - `MediaMetricsBatchScheduler.java`
    - `MoodRefreshEventListener.java`.
  - 인수 조건:
    ```bash
    docker info >/dev/null &&
    ./gradlew :apps:api-user:test --tests '*ApiUserApplicationTests' \
      --tests '*ApiUserCompositionTest' --tests '*ArchitectureTest' --rerun-tasks &&
    bash .omo/qa/assert-junit-xml.sh apps/api-user/build/test-results/test &&
    ! rg -n '@ComponentScan\\([^)]*com\\.ott|@EnableJpaRepositories|@EntityScan' \
      apps/api-user/src/main/java/com/ott/api_user/ApiUserApplication.java
    ```
  - QA 시나리오:
    - 정상, Testcontainers/Spring context: 필수 user bean이 정확히 하나씩 존재하고 컨텍스트가 시작된다.
    - 실패, context fixture: `InfraDbConfiguration`을 누락하면 예상한 repository bean 누락 오류가 발생한다.
    - 증거: `.omo/evidence/task-19-layered-architecture-refactor/`.
  - 커밋: N | `refactor(api-user): compose layered modules explicitly`

- [ ] 20. API-admin 전역 스캔을 명시적 조립으로 교체
  - 작업 내용:
    - `ApiAdminApplication`에서 전역/직접 스캔을 제거한다.
    - `CommonWebConfiguration`, `CommonSecurityConfiguration`, `InfraDbConfiguration`, `InfraS3Configuration`, `InfraMqConfiguration`을 import한다.
    - 현재 선언된 모듈 그래프에 없는 `infra-redis`는 추가하지 않는다.
    - security, web advice, 리포지토리, S3, publisher/converter, outbox poller, async/event listener가 존재하고 user/transcoder bean이 0개인지 단언한다.
  - 금지 사항: outbox 주기, 이벤트 phase, S3 동작, Rabbit payload 변경.
  - 병렬화: Wave 4 | 선행: 9, 12-18 | 차단: 22 | 병렬 가능: 19, 21.
  - 참조:
    - `apps/api-admin/src/main/java/com/ott/api_admin/ApiAdminApplication.java`
    - `apps/api-admin/build.gradle`
    - `outbox/poller/OutboxPoller.java`
    - `tagging/service/AITaggingAsyncService.java`
    - `trending/event/TrendingCacheInvalidationListener.java`.
  - 인수 조건:
    ```bash
    docker info >/dev/null &&
    ./gradlew :apps:api-admin:test --tests '*ApiAdminApplicationTests' \
      --tests '*ApiAdminCompositionTest' --tests '*ArchitectureTest' --rerun-tasks &&
    bash .omo/qa/assert-junit-xml.sh apps/api-admin/build/test-results/test &&
    ! rg -n '@ComponentScan\\([^)]*com\\.ott|@EnableJpaRepositories|@EntityScan' \
      apps/api-admin/src/main/java/com/ott/api_admin/ApiAdminApplication.java &&
    ! rg -n "project\\(':modules:infra-redis'\\)" apps/api-admin/build.gradle
    ```
  - QA 시나리오:
    - 정상, Testcontainers/Spring context: 필수 admin bean이 정확히 하나씩 존재한다.
    - 실패, context fixture: api-user controller/service가 존재하지 않는다.
    - 증거: `.omo/evidence/task-20-layered-architecture-refactor/`.
  - 커밋: N | `refactor(api-admin): compose layered modules explicitly`

- [ ] 21. transcoder 전역 스캔 교체 및 웹 의존 제거
  - 작업 내용:
    - `TranscoderApplication`에서 전역/직접 스캔을 제거한다.
    - `InfraDbConfiguration`, `InfraS3Configuration`, `InfraMqConfiguration`을 import하고 common-core 오류를 직접 사용한다.
    - `apps/transcoder/build.gradle`에서 `common-web`을 제거한다.
    - 로컬 스토리지, Rabbit 리스너 시작 비활성화, 더미 프로세스 경로를 사용하는 테스트 프로필을 구성한다.
    - 두 Rabbit 리스너 bean, 스토리지/프로세스 구현체, 리포지토리 bean이 존재하고 API/web advice bean이 0개인지 단언한다.
  - 금지 사항: 리스너 ID/큐, 재시도/fatal 분류, FFmpeg 명령 구성, 스토리지 키 변경.
  - 병렬화: Wave 4 | 선행: 10, 13, 15-18 | 차단: 22 | 병렬 가능: 19, 20.
  - 참조:
    - `apps/transcoder/src/main/java/com/ott/transcoder/TranscoderApplication.java`
    - `apps/transcoder/build.gradle`
    - `queue/rabbit/RabbitTranscodeListener.java`
    - `RabbitDeadLetterListener.java`
    - `storage/`
    - `ffmpeg/execution/`
    - `inspection/probe/execution/`.
  - 인수 조건:
    ```bash
    docker info >/dev/null &&
    ./gradlew :apps:transcoder:test --tests '*TranscoderApplicationTests' \
      --tests '*TranscoderCompositionTest' --tests '*ArchitectureTest' --rerun-tasks &&
    bash .omo/qa/assert-junit-xml.sh apps/transcoder/build/test-results/test &&
    ! rg -n '@ComponentScan\\([^)]*com\\.ott|@EnableJpaRepositories|@EntityScan' \
      apps/transcoder/src/main/java/com/ott/transcoder/TranscoderApplication.java &&
    ! rg -n "project\\(':modules:common-web'\\)|^import com\\.ott\\.common\\.web" \
      apps/transcoder
    ```
  - QA 시나리오:
    - 정상, Testcontainers/Spring context: transcoder 컨텍스트와 예상 리스너/스토리지/프로세스 bean이 한 번씩 로드된다.
    - 실패, JUnit: 잘못된 명령/메시지가 외부 처리를 시작하지 않고 기존 오류로 매핑된다.
    - 증거: `.omo/evidence/task-21-layered-architecture-refactor/`.
  - 커밋: N | `refactor(transcoder): compose infra without web coupling`

- [ ] 22. 모든 계약, 아키텍처 규칙, 빌드, 의존 그래프 재검증
  - 작업 내용:
    - 모든 모듈 테스트/빌드를 실행한다.
    - Todo 3~5 fixture의 오류/상태, 페이징 JSON/OpenAPI, REST 매핑, 메시지 스키마, Flyway 해시, S3 키, 트랜잭션/scheduler/listener 애너테이션을 바이트 단위로 비교한다.
    - 최종 ArchUnit/Gradle 규칙을 강제하고, 오래된 것으로 입증된 프로젝트 의존/import만 제거한다.
    - 변경 후 상태/해시를 기록하고 무관한 dirty 경로가 Wave 기준선과 바이트 단위로 동일함을 증명한다.
  - 금지 사항: XML 검사 없이 Gradle 종료 코드 0만 성공으로 인정하거나, 테스트 실행 없이 grep 결과만 증거로 인정.
  - 병렬화: Wave 4 | 선행: 19-21 | 차단: F1-F4.
  - 참조: 이전 모든 Todo와 `.omo/evidence/task-1-layered-architecture-refactor/`.
  - 인수 조건:
    ```bash
    ./gradlew clean compileJava test bootJar --rerun-tasks &&
    for d in apps/*/build/test-results/test modules/*/build/test-results/test; do
      test ! -d "$d" || bash .omo/qa/assert-junit-xml.sh "$d"
    done &&
    ./gradlew projects > .omo/evidence/task-22-layered-architecture-refactor/gradle-projects.txt &&
    sha256sum modules/infra-db/src/main/resources/db/migration/*.sql \
      > .omo/evidence/task-22-layered-architecture-refactor/flyway.sha256 &&
    ! rg -n '@ComponentScan\\([^)]*com\\.ott|^import com\\.ott\\.domain\\..*\\.repository' \
      apps modules -g '*.java' &&
    cmp .omo/evidence/task-5-layered-architecture-refactor/flyway.sha256 \
      .omo/evidence/task-22-layered-architecture-refactor/flyway.sha256
    ```
  - QA 시나리오:
    - 정상, Gradle/ArchUnit/fixture 비교: 전체 빌드가 GREEN이고 모든 변경 불가 fixture가 일치한다.
    - 실패, shell: 의도적으로 변경한 fixture 복사본과 비교한다. 비교가 이를 거부하면 PASS.
    - 증거: `.omo/evidence/task-22-layered-architecture-refactor/`.
  - 커밋: N | `test(architecture): verify layered refactor end to end`

## Final verification wave
> 모든 Todo 완료 후 병렬 실행한다. 네 검증이 모두 승인해야 하며, 결과를 제시하고 사용자의 명시적 확인을 받은 뒤 완료를 선언한다.
- [ ] F1. 계획 준수 감사
  - 도구/호출:
    ```bash
    ./gradlew test --tests '*ArchitectureTest' --rerun-tasks &&
    ! rg -n '@ComponentScan\\([^)]*com\\.ott|^import com\\.ott\\.domain\\..*\\.repository' \
      apps modules -g '*.java' &&
    ! rg -n 'org\\.springframework\\.security\\.core\\.Authentication|com\\.ott\\.common\\.web\\.response' \
      apps -g '*Service.java' -g '*Reader.java' -g '*Writer.java'
    ```
  - PASS: 모든 아키텍처 테스트가 GREEN이고 세 금지 검색에 결과가 없다.
  - 증거: `.omo/evidence/final-f1-layered-architecture-refactor/`.

- [ ] F2. 코드 품질 및 빌드 리뷰
  - 도구/호출:
    ```bash
    ./gradlew clean compileJava test bootJar --rerun-tasks &&
    for d in apps/*/build/test-results/test modules/*/build/test-results/test; do
      test ! -d "$d" || bash .omo/qa/assert-junit-xml.sh "$d"
    done
    ```
  - PASS: 종료 코드 0, 생성된 모든 JUnit XML에 실패 없음, 비활성/skip 테스트 없음, 리뷰어의 정확성/보안 지적 없음.
  - 증거: `.omo/evidence/final-f2-layered-architecture-refactor/`.

- [ ] F3. HTTP health 표면을 통한 실제 수동 QA
  - 준비: 로컬 전용 MySQL/Redis/Rabbit/JWT/AWS/OAuth/AI/transcoder 더미 값으로 `.omo/qa/layered.env`를 만든다. 운영 secret을 사용하지 않는다.
  - 도구/호출:
    ```bash
    docker compose --env-file .omo/qa/layered.env up --build -d \
      mysql redis rabbitmq api-user api-admin transcoder &&
    timeout 240 bash .omo/qa/wait-http.sh http://localhost:8080/actuator/health &&
    timeout 240 bash .omo/qa/wait-http.sh http://localhost:8081/actuator/health &&
    timeout 240 bash .omo/qa/wait-http.sh http://localhost:8082/actuator/health &&
    curl -i http://localhost:8080/actuator/health \
      > .omo/evidence/final-f3-layered-architecture-refactor/api-user.http &&
    curl -i http://localhost:8081/actuator/health \
      > .omo/evidence/final-f3-layered-architecture-refactor/api-admin.http &&
    curl -i http://localhost:8082/actuator/health \
      > .omo/evidence/final-f3-layered-architecture-refactor/transcoder.http
    ```
  - PASS: 각 증거 파일에 `HTTP/1.1 200`과 `"status":"UP"`가 포함된다.
  - 실패 시나리오: `curl -i http://localhost:8080/definitely-missing`; 기존 구조화된 404 계약을 반환하면 PASS.
  - 정리:
    `docker compose --env-file .omo/qa/layered.env down -v --remove-orphans`
    이후 `docker ps --filter name=ott- --format '{{.Names}}'` 출력이 없어야 한다.
  - 증거: `.omo/evidence/final-f3-layered-architecture-refactor/`.

- [ ] F4. 범위 충실도 및 dirty worktree 감사
  - 도구/호출:
    ```bash
    cmp .omo/evidence/task-5-layered-architecture-refactor/flyway.sha256 \
      .omo/evidence/task-22-layered-architecture-refactor/flyway.sha256 &&
    bash .omo/qa/compare-contract-fixtures.sh \
      .omo/evidence/task-3-layered-architecture-refactor \
      .omo/evidence/task-4-layered-architecture-refactor \
      .omo/evidence/task-5-layered-architecture-refactor \
      .omo/evidence/task-22-layered-architecture-refactor &&
    bash .omo/qa/audit-unrelated-paths.sh \
      .omo/evidence/task-1-layered-architecture-refactor/source-hashes.sha256
    ```
  - PASS: 변경 불가 계약이 일치하고, 트랜잭션/리스너/scheduler 애너테이션이 유지되며, 새 포트/어댑터 인터페이스가 없고, 무관한 dirty 파일이 기준 해시와 일치한다.
  - 증거: `.omo/evidence/final-f4-layered-architecture-refactor/`.

## Commit strategy
- 각 Todo에 적힌 Conventional Commit 초안으로 논리 체크포인트 하나를 구성한다.
- staging 전에 각 후보 파일을 Wave 기준선과 비교하고 이 계획의 hunk만 분리한다. 기존 변경을 안전하게 분리할 수 없으면 해당 파일을 stage/commit하지 않는다.
- 저장소 전체 `git add .`를 사용하지 않고 명시적 경로만 stage한다.
- 사용자 승인 없이 커밋을 만들지 않는다.
- 이후 커밋이 승인되면 각 커밋은 독립적으로 컴파일되고 영향 테스트를 통과해야 하며 다음 footer를 포함한다.
  `Plan: .omo/plans/layered-architecture-refactor.md`

## Success criteria
- `:modules:common-core`가 유일한 신규 모듈이며 Spring/Swagger/Servlet 의존성이 없다.
- 서비스가 더 이상 `common-web` 응답 타입에 의존하지 않으면서 오류 코드/메시지/상태와 페이징 JSON/OpenAPI 계약은 유지된다.
- 어떤 애플리케이션 service/reader/writer도 Spring Security `Authentication`을 받지 않는다.
- 직접 작성한 모든 리포지토리, 커스텀 조각/구현체, 프로젝션/행 모델, JDBC 코드, 영속성 설정이 `infra-db`에 있고, 엔티티와 생성 Q 타입은 `domain`에 유지된다.
- 리포지토리 bean 25개와 커스텀 구현체 계열 13개가 모두 Testcontainers MySQL에서 실행된다.
- 어떤 애플리케이션도 `com.ott`를 전역 스캔하지 않고 승인된 모듈 설정 행렬만 import한다.
- `api-admin`에는 `infra-redis` 의존성이 추가되지 않고 transcoder는 더 이상 `common-web`에 의존하지 않는다.
- Gradle 테스트가 정상적으로 실패를 전파하고, 비활성 앱 컨텍스트 테스트가 활성화되며, 모든 JUnit XML이 깨끗하고 전체 build/bootJar가 성공한다.
- REST, 보안, MQ, Flyway, S3, 트랜잭션, scheduler, listener, 외부 호출 순서가 기준 fixture와 일치한다.
- F1~F4가 모두 승인되고 Docker/임시 QA 상태가 완전히 정리된다.
