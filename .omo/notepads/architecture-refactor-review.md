# Architecture Refactor Review Notepad

## Goal

- User-visible deliverable: 현재 구조 문제, 레이어드 리팩토링 범위, 헥사고날 전환 구조를 코드 근거와 실행 순서로 비교한다.
- Tier: HEAVY. 멀티모듈 경계, 인증·외부 연동, DB/MQ/S3, 트랜잭션과 도메인 간 리팩토링을 함께 다룬다.
- Intent route: CLEAR. 검토 결과는 명확하지만 최종 실행 목표를 레이어드 정리로 할지 헥사고날 전환으로 할지는 owner decision이다.

## Skills

- `omo:ulw-plan`: 탐색 후 승인 게이트를 거쳐 결정 완료 계획을 만든다.
- `omo:refactor`: 의존 그래프, 영향 구역, 테스트 안전망을 기준으로 리팩토링 단계를 나눈다.
- `omo:ast-grep`: Java 코드 구조와 계층 침범 후보를 AST 기반으로 확인한다.

## Components ledger

| ID | Component | Outcome | Status | Evidence |
|----|-----------|---------|--------|----------|
| C1 | Current architecture diagnosis | 실제 모듈/패키지 의존과 위반 사례를 분류 | complete | Gradle graph, AST/rg imports, representative source inspection |
| C2 | Layered target | 현재 멀티모듈을 유지한 최소 리팩토링 구조 | complete | target graph, move/stay map, package rules, ArchUnit rules |
| C3 | Hexagonal target | 포트/어댑터 기반 변경 구조와 비용 | complete | transcoder, admin upload/outbox, member/Kakao/AI port maps |

## Binding success criteria

1. Current diagnosis: Gradle 모듈 의존 방향과 최소 5개의 구체적 계층 결합 사례가 파일 경로로 확인된다.
   - Evidence command: `rg -n '^\\s*(implementation|api) project' apps modules -g build.gradle`
   - PASS: 모든 project dependency가 표로 분류되고 순환 여부가 명시됨.
2. Layered target: 각 앱의 presentation/application/domain/infrastructure 책임과 이동 대상 파일군이 결정된다.
   - Evidence command: `rg -n '^import com\\.ott\\.(api_user|api_admin|transcoder|domain|infra|common)' apps modules -g '*.java'`
   - PASS: 금지 의존 규칙과 단계별 이동 범위가 경로 단위로 작성됨.
3. Hexagonal target: inbound port, outbound port, adapter, domain/application 경계와 모듈 재구성이 현재 핵심 흐름 3개에 매핑된다.
   - Scenarios: 사용자 인증, 관리자 업로드→outbox→MQ, transcoder 메시지→HLS→storage.
   - PASS: 각 흐름에 port/adapter 후보와 트랜잭션 경계가 누락 없이 정의됨.
4. Regression strategy: 현재 테스트 공백을 반영한 characterization/architecture test 순서와 명령이 정해진다.
   - Evidence command: `find apps -path '*/src/test/*' -name '*Test*.java'`
   - PASS: RED→GREEN 안전망, ArchUnit 또는 동등한 계층 검증, 실제 부팅/메시지 흐름 QA가 계획에 포함됨.
5. Scope safety: 기존 광범위한 사용자 변경을 되돌리거나 제품 코드를 수정하지 않는다.
   - Evidence command: `git status --short`
   - PASS: 이 planning turn의 파일 변경은 `.omo/` 아래로 한정됨.

## Risks

- dirty_worktree: 저장소 대부분이 이미 수정 상태. 계획은 경로와 이동 순서를 명확히 하되 기존 변경을 덮어쓰지 않아야 한다.
- Java LSP unavailable: `jdtls` 미설치. Gradle/AST/rg/codegraph claims를 직접 파일로 교차 검증한다.
- misleading_success_output: `api-user`/`api-admin` 테스트는 `ignoreFailures = true`; Gradle exit 0만 성공 근거로 쓰지 않는다.
- migration scope: 패키지 이동은 API 계약보다 import blast radius가 크며, 모듈 분리는 Spring component scanning과 configuration wiring을 바꾼다.

## Verified current findings

### Module graph

- Gradle project graph is acyclic by declared `project(...)` edges.
- Deployable apps:
  - `api-user -> domain, infra-db, infra-redis, common-web, common-security`
  - `api-admin -> domain, infra-db, infra-s3, infra-mq, infra-redis, common-web, common-security`
  - `transcoder -> domain, infra-db, infra-s3, infra-mq, common-web`
- `common-security -> domain` is declared but no `com.ott.domain` import exists.
- `infra-db -> domain` is declared but `infra-db` currently has no Java source. It is migration resources plus unused compile dependencies.
- No cross-imports between `api-user`, `api-admin`, and `transcoder` were found.

### Runtime composition

- All three Boot apps use `@ComponentScan(basePackages = "com.ott")`.
- Entity and repository scans are explicitly global to `com.ott.domain`.
- Result: Gradle edges are explicit, but bean inclusion is implicit and broader than each app package.

### Application-to-web leakage

- 29 service/reader/writer files import `common-web` exception types.
- 14 service/reader/writer files return `PageResponse`.
- `ErrorCode` owns `HttpStatus`; `BusinessException` therefore carries transport semantics into application and transcoder code.
- `PageResponse` and `PageInfo` contain Swagger annotations and are used below controllers.
- Multiple application services accept Spring Security `Authentication`.
- HTTP request/response DTOs are used directly as service inputs/outputs.

### Mixed infrastructure and transaction boundaries

- `UploadHelper` combines repository access, file policy, S3 presign/complete, application validation, nested result records, and web pagination.
- `BackOfficeContentsWriter.createContentsUpload` and `BackOfficeShortFormWriter.createShortFormUpload` call S3 presign/multipart initialization inside `@Transactional`.
- `MoodRefreshService.analyzeAndCreateRefreshCard` performs blocking AI and Gemini calls inside `@Transactional`.
- `MemberService.withdraw` performs Kakao unlink before local soft-delete work inside one DB transaction.
- Admin content/short-form services retain unused concrete Rabbit publisher dependencies while outbox polling is the actual publisher path.

### Persistence placement

- `modules/domain` contains JPA entities, 25 Spring Data repository interfaces, 13 QueryDSL repository implementations, one JDBC repository, and `QueryDslConfig`.
- This is acceptable for a pragmatic layered data/domain module, but the module does not express a domain-versus-persistence dependency boundary.
- For full hexagonal architecture, Spring Data interfaces, QueryDSL implementations, JDBC, and config are adapters and must leave the core boundary.

### Existing good seams

- Controllers generally delegate to services; only the non-prod `TestAuthController` directly injects a repository. `WatchHistoryController` has an unused repository import, not an injected dependency.
- Transcoder already has port-shaped interfaces: `MessageListener`, `VideoStorage`, `FfmpegExecutor`, `FfprobeExecutor`, `CommandPipeline`.
- Admin already uses an outbox and AFTER_COMMIT listeners.
- Domain does not import app, common-web, security, or infra packages.

## Preliminary comparison

### Layered refactor

- Keep three deployable apps and JPA-annotated domain entities.
- Make the package rule explicit:
  - presentation/controller -> application/service -> domain/repository
  - infrastructure clients/config are lower-layer implementations
  - composition root is the only place allowed to assemble concrete infrastructure
- Move HTTP-neutral errors and pagination out of `common-web`.
- Convert `Authentication` to application-owned actor values at controller boundary.
- Move QueryDSL/JDBC implementations and `QueryDslConfig` to `infra-db`; keep JPA entity/repository contracts in `domain`.
- Replace global component scan with app package scan plus explicit module configuration imports.
- Enforce with ArchUnit before broad moves.
- Cost: medium; preserves most service/repository shapes.

### Full hexagonal refactor

- Inbound adapters: REST controllers, Rabbit listeners, scheduled/event listeners.
- Inbound ports: use-case interfaces such as `WithdrawMemberUseCase`, `CompleteMediaUploadUseCase`, `TranscodeJobUseCase`.
- Application services depend only on domain and outbound ports.
- Outbound ports: member persistence, upload storage, AI, Kakao, transcode publishing, ingest status, media storage, FFmpeg, ffprobe.
- Outbound adapters: Spring Data/QueryDSL, S3, WebClient/RestTemplate, RabbitTemplate, ProcessBuilder.
- Composition roots wire ports to adapters.
- Full version also replaces `JpaRepository` contracts with application/domain-owned repository ports; pragmatic version keeps JPA entities but hides Spring Data repositories in adapters.
- Cost: high; extensive tests required before moving repository contracts.

## Recommended default

- Governing rule: the baseline architecture is layered; dependency inversion is mandatory only at external technology boundaries.
- Phase 1: strict layered cleanup and architecture tests across all apps.
- Phase 2: selective hexagonal extraction at S3, RabbitMQ, external HTTP/AI/Kakao, and transcoder process/storage boundaries.
- Pilot order:
  1. transcoder (`MessageListener`, `VideoStorage`, `FfmpegExecutor`, `FfprobeExecutor` already exist),
  2. admin upload/outbox/S3/MQ,
  3. user Kakao/AI flows.
- Do not begin with full repository-port extraction across every domain.
- Move QueryDSL/JDBC implementations and configuration to `persistence-jpa`.
- Move Spring Data interfaces to `persistence-jpa` when they remain technology-specific; introduce application/domain-owned repository ports only for selected use cases that need inversion.
- Preserve REST, error codes, Flyway SQL, `TranscodeMessage`, and S3 key contracts.
- Test strategy default: characterization tests first, then refactor, with ArchUnit rules failing the build.
- Treat transactional external-I/O and compensation/state-machine changes as a separate behavior-changing workstream after structural refactoring.

## Approval gate

- Status: awaiting owner approval before generating `.omo/plans/<slug>.md`.
- Recommended decisions:
  1. Architecture: layered baseline plus selective hexagonal boundaries.
  2. Persistence: extract technology-specific JPA/QueryDSL/JDBC code; add neutral repository ports incrementally.
  3. Consistency: record transaction/external-I/O findings, but schedule behavior changes as a separate phase.
  4. Tests: characterization tests and ArchUnit constraints before broad package/module moves.
- Alternative owner choices:
  - strict layered only, without new ports beyond existing seams;
  - full hexagonal conversion with application/domain repository ports and additional core modules.

## External primary-source basis

- Spring Modulith verification: module DAG, API-only access, allowed dependencies.
- ArchUnit: package/layer/slice/cycle checks using regular tests.
- Spring transaction-bound events: explicit BEFORE/AFTER commit phases.
- Cockburn Ports & Adapters: application use cases must not know external technology.

## Cleanup receipt

- No product code changes authorized.
- No servers, ports, tmux sessions, or temporary runtime processes started.
