# 모아셈 백엔드 컨벤션

작성일 2026-08-09 · 대상: 백엔드 팀 전원
기준 문서: `모아셈 최종 기획안 v1.0`

이 문서는 프로젝트 착수 시점에 정한 기준이며, 팀 논의를 거쳐 계속 갱신한다.
변경 시 이 문서를 먼저 고치고 PR로 리뷰받은 뒤 반영한다.

---

## 1. 기술 스택

| 영역 | 선택 | 비고 |
|---|---|---|
| 언어 | Kotlin 2.2.x | |
| 프레임워크 | Spring Boot 4.1.x | Spring Framework 7 기반, Java 17+ 필수 |
| 빌드 도구 | Gradle (Kotlin DSL, `build.gradle.kts`) | |
| DB | PostgreSQL | 기획안 14장 기준, 팀 논의 후 최종 확정 |
| ORM | Spring Data JPA + Hibernate | |
| 캐시 | Redis | |
| 인증 | OAuth2 (Google) + JWT | 자체 회원가입 없음 |
| 파일 저장소 | AWS S3 (예정) | 증빙 이미지 |
| 배포 | Docker Compose, AWS EC2 | V_O와 동일한 구조 사용 예정 |

Java 8/11 스타일 라이브러리를 그대로 가져오지 않는다. Spring Boot 4.x는 `spring-boot-starter-web` 대신
`spring-boot-starter-webmvc`, `com.fasterxml.jackson.module` 대신 `tools.jackson.module` 좌표를 쓴다.
의존성 추가 전 Spring Boot 4.x 기준으로 groupId가 바뀌었는지 먼저 확인한다.

---

## 2. 패키지 구조

엔티티 단위가 아니라 **도메인(기능 묶음) 단위**로 패키지를 나눈다. 기획안의 5개 핵심 흐름을 그대로 도메인 경계로 사용한다.

```
com.moasem.backend
├── MoasemApplication.kt
├── domain
│   ├── auth        # 구글 로그인, 토큰 발급/재발급
│   ├── group        # 모임 생성, 가입 코드, 멤버십
│   ├── event         # 행사, 최초예산, 예산추가, 마감
│   ├── spending     # 지출 신청, 승인/반려, 증빙
│   └── report        # 결산 수치 계산, AI 분석 연동, PDF/CSV 생성
└── global
    ├── config          # Security, JPA, 외부 API 등 전역 설정
    ├── error            # 공통 예외, GlobalExceptionHandler
    ├── security       # JWT 필터, OAuth2 관련 공통 컴포넌트
    ├── response      # 공통 API 응답 포맷
    └── util               # 공통 유틸리티
```

각 도메인 내부는 기술 레이어 기준으로 6개 폴더를 고정한다.

```
domain/{도메인명}
├── controller     # Controller (요청 검증 후 service 호출)
├── service          # 비즈니스 로직, 트랜잭션 경계
├── dto                # Request/Response DTO
├── converter     # Entity <-> DTO 변환
├── entity           # JPA 엔티티
└── repository    # Spring Data JPA Repository 인터페이스
```

**의존 방향**: `controller → service → repository/entity`, `converter`는 `service`나 `controller`에서 필요할 때 사용.
컨트롤러는 요청 검증(`@Valid`) 후 service 호출만 하고 비즈니스 로직을 직접 두지 않는다.

**도메인 간 참조**: 다른 도메인의 `service`를 직접 호출하는 건 허용한다.
단, 다른 도메인의 `entity`를 직접 수정하지 않고 그 도메인의 `service` 메서드를 통해서만 접근한다.
예: `spending`이 `event`의 잔여 예산을 갱신할 때 `EventService`를 통해서만 접근한다.

같은 도메인 안에 엔티티가 여러 개 있는 경우(예: `event` 도메인의 `Event`, `BudgetAddition`)에는
`entity`, `repository` 폴더 안에서 파일명으로 구분한다 (`Event.kt`, `BudgetAddition.kt` 등).
도메인이 지나치게 커지면(엔티티 4개 이상 + 서로 다른 흐름) 그때 팀 논의 후 하위 도메인 분리를 검토한다.

---

## 3. 네이밍 컨벤션

### 3.1 클래스

| 유형 | 규칙 | 예시 |
|---|---|---|
| 엔티티 | 단수 명사 | `Event`, `Spending`, `BudgetAddition` |
| Repository 인터페이스 | `{엔티티}Repository` | `SpendingRepository` |
| 유스케이스 서비스 | `{도메인}{동작}Service` 또는 `{도메인}Service` | `SpendingApprovalService`, `EventService` |
| Controller | `{도메인}Controller` | `SpendingController` |
| Request DTO | `{동작}{도메인}Request` | `CreateEventRequest`, `ApproveSpendingRequest` |
| Response DTO | `{도메인}Response` / `{도메인}{용도}Response` | `EventDetailResponse`, `SpendingSummaryResponse` |
| 커스텀 예외 | `{상황}Exception` | `InvalidSpendingStateException` |

### 3.2 함수 / 변수

- 함수명은 동사로 시작: `approve()`, `calculateRemainingBudget()`
- Repository 조회 메서드는 Spring Data JPA 네이밍 규칙(`findBy...`, `existsBy...`)을 우선 사용하고,
  복잡한 조건은 `@Query` 또는 QueryDSL로 분리한다 (QueryDSL 도입 여부는 팀 논의 후 결정).
- boolean 반환 함수/필드는 `is`, `has` 접두사: `isClosed`, `hasPendingSpending`

### 3.3 상수 / Enum

기획안에 정의된 enum은 아래 표기를 그대로 코드에 반영한다. **화면 표시용 한글 라벨과 DB 저장 코드를 분리**한다.

```kotlin
enum class SpendingTag(val label: String) {
    MEAL("식비"),
    ACCOMMODATION("숙박비"),
    TRANSPORTATION("교통비"),
    VENUE("대관비"),
    SUPPLIES("물품비"),
    OTHER("기타")
}

enum class SpendingStatus {
    PENDING, APPROVED, REJECTED
}

enum class EventStatus {
    ACTIVE, CLOSED
}

enum class GroupRole {
    OWNER, MEMBER
}
```

---

## 4. Git 컨벤션

### 4.1 브랜치 전략

`main` (배포) ← `develop` (통합) ← `feature/*`, `fix/*` (작업 브랜치)

- `main`: 항상 배포 가능한 상태. 직접 push 금지.
- `develop`: 기능 통합 브랜치. PR 머지만 허용.
- `feature/{도메인}-{작업명}`: 예) `feature/spending-approval-api`
- `fix/{도메인}-{버그명}`: 예) `fix/event-budget-calc`
- `refactor/{내용}`, `docs/{내용}`, `chore/{내용}` 도 동일 규칙

### 4.2 커밋 메시지

[Conventional Commits](https://www.conventionalcommits.org/) 기반, 한글 사용.

```
{type}: {변경 요약}

{본문 (선택, 왜 바꿨는지)}
```

| type | 용도 |
|---|---|
| feat | 새 기능 |
| fix | 버그 수정 |
| refactor | 동작 변화 없는 구조 개선 |
| test | 테스트 추가/수정 |
| docs | 문서 |
| chore | 빌드, 설정, 의존성 등 |
| style | 포맷팅, 세미콜론 등 (로직 변경 없음) |

예: `feat: 지출 승인 시 잔여 예산 재계산 로직 추가`

### 4.3 PR 규칙

- 하나의 PR은 하나의 기능/이슈 단위로 작게 유지한다.
- PR 제목은 커밋 메시지와 같은 형식.
- 최소 1인 이상 리뷰 승인 후 머지. Squash merge 사용.
- `.github/PULL_REQUEST_TEMPLATE.md` 체크리스트 채우기.

### 4.4 이슈 규칙

- 작업 시작 전 이슈 먼저 생성 (`.github/ISSUE_TEMPLATE` 사용).
- 이슈에 도메인 라벨(`domain:auth`, `domain:event` 등) 부착 (라벨은 GitHub 조직 생성 후 설정).
- PR 본문에 `Closes #이슈번호`로 연결.

---

## 5. 코드 스타일

- Kotlin 공식 스타일 가이드(ktlint 기본 룰) 따름. 들여쓰기 4칸(Gradle 파일은 탭).
- 데이터 클래스는 필요한 곳(DTO)에만 쓰고, **JPA 엔티티는 `data class`로 만들지 않는다**
  (equals/hashCode가 전체 필드 기준이라 프록시/지연로딩과 충돌 위험).
- 엔티티는 기본 생성자를 외부에 노출하지 않고, 정적 팩토리 메서드나 별도 생성자로 생성 규칙을 강제한다.
- 모든 금액 필드는 `Long` (원 단위 정수), 절대 `Double`/`Float` 사용 금지 — 기획안 8.2 원칙.
- API 응답은 공통 포맷(`global/response`)으로 통일한다. (형식은 1주차 API 명세 작업 시 확정)
- 컨트롤러는 요청 검증(`@Valid`) 후 바로 application 서비스 호출만 하고, 비즈니스 로직을 두지 않는다.
- 백엔드 권한 검사는 프론트 노출 여부와 무관하게 서버에서 항상 재검증한다 (기획안 9장 원칙).
  모임장 전용 API는 서비스 레이어에서 `Membership.role == OWNER` 확인을 빠짐없이 넣는다.

---

## 6. 테스트

- 신규 API는 최소 unit test(서비스 레이어) 하나 이상 동반한다.
- 상태 전이(PENDING→APPROVED/REJECTED), 예산 계산, 음수 잔액, 마감 조건은 기획안 11장 기준으로
  반드시 테스트 케이스를 둔다.
- 테스트 프레임워크: JUnit5 + MockK + SpringMockK (Kotlin 친화적 목킹).

---
