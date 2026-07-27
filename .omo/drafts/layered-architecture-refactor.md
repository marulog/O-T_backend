---
slug: layered-architecture-refactor
status: plan-complete
intent: clear
pending-action: 사용자가 `$omo:start-work` 실행 또는 선택 사항인 이중 고정밀 계획 리뷰를 선택
approach: 배포 앱 3개와 기존 인프라/도메인 모듈을 유지하고, 배포되지 않는 `common-core` 라이브러리 하나를 추가한다. 프레젠테이션 -> 애플리케이션 -> 도메인/영속성의 엄격한 레이어드 의존 방향을 강제하되, 애플리케이션 서비스가 하위 `infra-db` 리포지토리와 기존 구체 인프라 서비스에 직접 의존하는 것은 허용한다. 기술 종속 영속성 코드를 `infra-db`로 이동하고, 서비스에서 웹 응답 타입과 Spring Security `Authentication`을 제거하며, 전역 스캔을 명시적 모듈 설정으로 교체한다. 새 헥사고날 포트를 추가하거나 런타임 계약을 변경하지 않는다.
---

# 초안: layered-architecture-refactor

## 구성 요소(토폴로지 원장)
<!-- 세부사항보다 구조를 먼저 고정한다. 독립적으로 성공/실패할 수 있는 최상위 구성 요소마다 한 행을 둔다. -->
<!-- id | 결과(한 줄) | 상태: active|deferred | 증거 경로 -->
| id | 결과 | 상태 | 증거 경로 |
| --- | --- | --- | --- |
| C1 | 이동 전에 아키텍처 안전망과 기준 동작을 기록한다 | active | `build.gradle`, `apps/*/build.gradle`, `apps/*/src/test`의 기존 테스트 |
| C2 | 공통 오류, 페이징, 인증 사용자, DTO 매핑이 프레젠테이션/애플리케이션 경계를 준수한다 | active | `modules/common-web`, `apps/api-*` 아래 서비스 import |
| C3 | JPA/QueryDSL/JDBC 구현과 설정은 `infra-db`에 두고 엔티티는 `domain`에 유지한다 | active | `modules/domain/src/main/java/com/ott/domain/**/repository`, `modules/domain/src/main/java/com/ott/global/config/QueryDslConfig.java` |
| C4 | 각 Boot 앱이 모든 `com.ott` 패키지를 스캔하지 않고 필요한 모듈만 명시적으로 조립한다 | active | 세 `*Application.java` 파일 |
| C5 | API, 메시지, 마이그레이션, 스토리지 키, 관찰 가능한 동작 계약을 유지한다 | active | controller, `TranscodeMessage`, Flyway 마이그레이션, 기존 앱 테스트 |
| C6 | 트랜잭션/외부 I/O 동작 재설계와 새 포트 추가는 보류한다 | deferred | 아키텍처 검토 노트의 트랜잭션 발견사항 |

## 공개 가정(채택한 기본값)
<!-- 질문 대신 채택한 기본값을 기록해 사용자가 승인 단계에서 거부할 수 있게 한다. -->
<!-- 가정 | 채택한 기본값 | 근거 | 되돌릴 수 있는가 -->
| 가정 | 채택한 기본값 | 근거 | 되돌릴 수 있는가 |
| --- | --- | --- | --- |
| 모듈 토폴로지 | 기존 앱/모듈을 모두 유지하고 라이브러리 모듈 `modules:common-core` 하나만 추가한다. 새 배포 서비스는 추가하지 않는다 | 토폴로지 변경을 제한하면서 중립 오류/페이징을 Spring MVC/OpenAPI에서 분리한다 | 예 |
| 영속성 경계 | Spring Data 인터페이스, 커스텀 인터페이스/구현체, QueryDSL/JDBC 클래스, 프로젝션, `QueryDslConfig`를 `infra-db`로 이동하고 JPA 엔티티/값 enum은 `domain`에 유지한다 | 리포지토리 포트를 도입하지 않으면서 기술 종속 타입을 하위 영속성 계층으로 내린다 | 예 |
| 계층 의존 | 애플리케이션 서비스가 `infra-db` 리포지토리와 기존 구체 `common-security`/`infra-*` 서비스를 import할 수 있으며 역전 인터페이스는 추가하지 않는다 | 헥사고날이 아닌 전통적 레이어드 아키텍처다 | 예 |
| 공통 중립 타입 | `modules:common-core`, 패키지 루트 `com.ott.common.core`, JDK/Lombok만 의존하며 `BusinessException`, HTTP 비종속 `ErrorCode`, `PageResult`, `PageMetadata`를 소유한다. HTTP 상태/Swagger 응답 매핑은 `common-web`에 유지한다 | 서비스의 MVC/OpenAPI 타입 의존을 제거한다 | 예 |
| 인증 경계 | controller/handler 아래에서 Spring Security `Authentication`만 금지한다. api-admin 소유 `AdminActor(memberId, roleKeys)`를 추가하고 기존 `JwtTokenProvider` 서비스 의존은 유지한다 | 포트를 도입하지 않고 요청 보안 컨텍스트 누수를 제거한다 | 예 |
| 기능 DTO 경계 | 기존 기능 요청/응답 DTO는 이 작업에서 애플리케이션 DTO로 유지하고 `common-web` 페이징 타입만 분리한다 | 레이어드 아키텍처에 저장소 전체 DTO 재작성은 필요하지 않으며 구조 이동을 불명확하게 만든다 | 예 |
| 패키지 구성 | 각 앱의 feature-first 패키지를 유지하고 `controller`/`dto`는 프레젠테이션, `service`/애플리케이션 모델은 애플리케이션 계층으로 본다 | 계층을 강제하면서 저장소 전체 패키지 재작성을 피한다 | 예 |
| 테스트 전략 | 특성화 테스트를 먼저 추가하고 아키텍처 제약과 이동을 점진적으로 적용한다 | 현재 커버리지가 일부에 불과하고 두 앱 모듈이 테스트 실패를 무시한다 | 예 |
| 런타임 동작 | HTTP payload/상태, 보안 동작, MQ 스키마, DB 마이그레이션, S3 키 형식을 보존한다 | 동작 재설계가 아니라 구조 리팩토링이다 | 아니오, 계약 보존은 필수 |

## 발견사항(경로 근거)
- 선언된 Gradle 의존성은 비순환이지만 세 애플리케이션 모두 `@ComponentScan(basePackages = "com.ott")`를 사용해 런타임 조립 범위가 선언된 앱 소유권보다 넓다: `apps/api-user/src/main/java/com/ott/api_user/ApiUserApplication.java`, `apps/api-admin/src/main/java/com/ott/api_admin/ApiAdminApplication.java`, `apps/transcoder/src/main/java/com/ott/transcoder/TranscoderApplication.java`.
- `modules/domain`에는 Spring Data 리포지토리 인터페이스 25개, QueryDSL 리포지토리 구현체 13개, JDBC 리포지토리 1개, `com.ott.global.config.QueryDslConfig`가 있다. `modules/infra-db`에는 마이그레이션만 있고 Java 영속성 구현은 없다.
- 애플리케이션 서비스가 `common-web` 예외/응답 타입을 import하고 일부는 Spring Security `Authentication`을 받아 애플리케이션 로직이 전송/보안 프레임워크와 결합되어 있다.
- 기존 테스트는 일부 user/admin 서비스와 controller를 다루지만 `apps/api-user/build.gradle`, `apps/api-admin/build.gradle`에 `ignoreFailures = true`가 있어 Gradle 종료 성공만으로 테스트 통과를 증명할 수 없다.
- `ApiUserApplicationTests`, `ApiAdminApplicationTests`는 현재 컨텍스트가 인프라를 요구해 비활성화되어 있다. `TranscoderApplicationTests`는 활성화되어 있지만 필수 환경 속성을 요구한다.
- 공유 worktree에 광범위한 변경이 있다. 실행 시 변경 경로를 스냅샷으로 저장하고 무관한 사용자 변경을 덮어쓰지 않아야 한다.
- 현재 선언된 의존성에는 `api-admin -> infra-redis`가 없다. 실행 계획은 현재 `project(...)` 선언만 모듈 그래프의 근거로 사용한다.

## 결정사항과 근거
- 사용자 요청 “레이어드 구조로 전환 플랜 ㄱ”을 승인으로 기록했으며 추가 승인 문구는 필요하지 않다.
- 목표는 엄격한 레이어드 아키텍처로 한정한다. 새 인바운드/아웃바운드 유스케이스 포트, 리포지토리 포트, 어댑터 추상화를 도입하지 않는다.
- 계층 방향은 프레젠테이션 -> 애플리케이션 -> 도메인 및 영속성이다. 애플리케이션 서비스가 `infra-db` 리포지토리 인터페이스와 기존 구체 인프라 서비스에 하향 의존하는 것은 허용한다. `common-web`은 프레젠테이션 지원이며 `infra-*`는 하위 인프라 계층, Boot 애플리케이션은 조립 루트다.
- 전체 도메인 모델 재작성을 피하기 위해 영속성 기술 코드는 `infra-db`로 이동하고 JPA 엔티티는 `domain`에 유지한다.
- `domain`은 엔티티의 QueryDSL Q 타입 생성을 계속 담당하고, `infra-db`가 QueryDSL runtime/JPA/JDBC 의존성과 모든 리포지토리 코드를 소유한다.
- `ErrorCode`에서 `HttpStatus`를 제거하고 `common-web`이 모든 코드를 포괄하는 `ErrorHttpStatusMapper`를 소유한다. 기존 오류 코드, 메시지, 숫자 상태, JSON 형태, OpenAPI 응답 스키마는 fixture 테스트로 보존한다.
- 기존 `PageResponse`/`PageInfo`는 웹 DTO와 직렬화 어댑터로 유지한다. 서비스는 중립 `PageResult`/`PageMetadata`를 반환하고 controller가 `pageInfo`, `dataList`를 변경하지 않고 매핑한다.
- 각 재사용 모듈은 명시적 루트 설정 클래스 하나를 노출한다. 앱 조립 행렬:
  - `api-user`: common-core, common-web, common-security, domain, infra-db, infra-redis.
  - `api-admin`: common-core, common-web, common-security, domain, infra-db, infra-s3, infra-mq.
  - `transcoder`: common-core, domain, infra-db, infra-s3, infra-mq. 오류 이동 후 common-web 제거.
- 테스트는 전용 `test` 프로필을 사용한다. 필요한 곳에 Testcontainers MySQL/Redis/RabbitMQ를 사용하고 cloud/AI/Kakao/process 연동은 mock 처리한다. 테스트가 명시적으로 활성화하지 않는 scheduler/listener는 비활성화하며 context 테스트 skip은 0개여야 한다.
- `ignoreFailures = true`를 제거해 모든 테스트 작업이 정상적으로 실패를 전파하게 한다. XML 보고서 검사는 적대적 검증 단계로 유지한다.
- 외부 I/O 트랜잭션 경계, 보상, 재시도/상태 머신 변경, API 재설계는 명시적으로 별도 후속 작업이다.

## 포함 범위
- GREEN 특성화 테스트를 먼저 추가한다. 각 ArchUnit 규칙은 대응 이동 직전에 도입해 하나의 Todo 안에서 RED에서 GREEN으로 전환한다.
- `modules:common-core`를 추가하고 예외/페이징 계약을 웹 표현에서 분리한다.
- 서비스 시그니처의 `Authentication`을 `AdminActor`로 전환하고 기존 기능 DTO는 보존한다.
- Spring Data/QueryDSL/JDBC 리포지토리, 프로젝션, 영속성 설정을 `domain`에서 `infra-db`로 이동한다.
- 전역 component/repository scan을 앱별 명시적 import/configuration으로 교체한다.
- 이동 후 오래된 모듈 의존성과 사용되지 않는 구체 publisher/repository import를 제거한다.
- 결정론적 통합 테스트 인프라를 추가하고 Spring Data 커스텀 조각, QueryDSL/JDBC wiring, bean/listener 등록 수, 세 애플리케이션 컨텍스트, 실제 health endpoint를 검증한다.

## 제외 범위
- 헥사고날 유스케이스 포트, 아웃바운드 포트, 어댑터 인터페이스, 전체 도메인 리포지토리 추상화를 추가하지 않는다.
- 엔드포인트 경로, JSON 형태, HTTP 상태, 인증 정책, MQ payload, Flyway SQL, S3 키, 비즈니스 규칙을 변경하지 않는다.
- 기능 요청/응답 DTO를 저장소 전체에서 재작성하지 않고 앱 소유 애플리케이션 DTO로 유지한다.
- Kakao, AI/Gemini, S3, RabbitMQ, transcoder 처리의 트랜잭션 경계를 재설계하지 않는다.
- 무관한 정리, 의존성 업그레이드, 전체 포맷 정리, 기존 worktree 변경 되돌리기를 하지 않는다.
- 자동 git commit을 하지 않는다. 실행 중 논리 커밋을 stage하거나 커밋 메시지를 제시하는 것은 별도 승인 시에만 허용한다.

## 미결 질문
- 없음. 사용자가 레이어드 전용 대안을 선택하고 계획 생성을 승인했다.

## Metis 갭 분석 반영
- 신규 모듈은 `common-core` 하나만 명시적으로 허용해 모듈 토폴로지 모순을 해결했다.
- 모든 리포지토리 기술을 `infra-db`로 이동하고 앱 서비스의 하향 의존을 허용해 영속성 배치와 의존 방향을 확정했다.
- `Authentication`, 기능 DTO, 오류/상태 매핑, 페이징 어댑터, QueryDSL 소유권, 모듈 조립, 테스트 인프라 처리 방식을 구체적으로 정의했다.
- 리포지토리 패키지 이동과 repository scan 변경을 결합하고 커스텀 조각 통합 커버리지를 필수화했다.
- bean/listener/scheduler 등록 불변식, Wave별 dirty worktree 스냅샷, 계약 fixture, 정상 테스트 실패 전파, 결정론적 context/실제 표면 QA를 추가했다.
- 이전의 선택적 헥사고날 권장안은 이 계획에서 제외했다. 기존 transcoder 인터페이스는 유지하되 확장하지 않는다.

## 승인 게이트
status: approved
decision-source: 사용자 메시지 "레이어드 구조로 전환 플랜 ㄱ.내가 승인해야되나?"
approved-action: `.omo/plans/layered-architecture-refactor.md` 작성
completed-artifact: `.omo/plans/layered-architecture-refactor.md`
<!-- 탐색이 끝나고 미지수가 해소되면 status를 awaiting-approval로 설정한다. -->
<!-- 이 영속 기록은 반복 방지 장치다. 이후 턴에서는 다시 탐색하지 말고 이 파일을 읽어 승인 게이트에서 재개한다. -->
