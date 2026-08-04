# spec-test.md — 테스트 전략 및 명세

> 작성 기준일: 2026-08-04

---

## 1. 테스트 범위

3개 계층을 다룬다.

| 계층 | 방식 | 목적 |
|------|------|------|
| Service (단위) | MockK + Kotest BehaviorSpec, Spring Context 없음 | 비즈니스 규칙, 권한 체크, 예외 조건 |
| Repository (통합) | `@DataJpaTest` + H2 in-memory + `SpringExtension` | 쿼리 메서드 정확성, 정렬, 필터링 |
| Domain/Entity (단위) | 순수 Kotest BehaviorSpec, 의존성 없음 | 엔티티 메서드, DTO 팩토리, 예외 구조 |

---

## 2. 의존성

```kotlin
// build.gradle.kts
testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
testImplementation("io.kotest:kotest-assertions-core:5.9.1")
testImplementation("io.kotest.extensions:kotest-extensions-spring:1.3.0")
testImplementation("io.mockk:mockk:1.13.12")
testRuntimeOnly("com.h2database:h2")
```

| 라이브러리 | 역할 |
|-----------|------|
| `kotest-runner-junit5` | Kotest Spec을 JUnit 5 플랫폼 위에서 실행 |
| `kotest-assertions-core` | `shouldBe`, `shouldThrow`, `shouldHaveSize` 등 단언문 |
| `kotest-extensions-spring` | `@DataJpaTest`와 Kotest BehaviorSpec 연동 |
| `mockk` | Kotlin 친화적 Mock 라이브러리 |
| `h2` | Repository 테스트용 in-memory DB |

---

## 3. 테스트 형식

### 3-1. Spec 스타일: `BehaviorSpec`

```kotlin
class FooServiceTest : BehaviorSpec({
    given("메서드명 또는 상황 설명") {
        `when`("구체적인 입력 조건") {
            then("기대 결과") {
                // 단언문
            }
        }
    }
})
```

`given → when → then` 3단 계층으로 **무엇을**, **어떤 조건에서**, **어떻게 동작하는지**를 분리한다.  
`when`은 Kotlin 예약어이므로 백틱(`` ` ``)으로 감싼다.

### 3-2. Service 단위 테스트 — Mock 생성 및 격리

```kotlin
class FooServiceTest : BehaviorSpec({
    val repository = mockk<FooRepository>()
    val service = FooService(repository)          // 직접 생성, DI 컨테이너 없음

    beforeEach { clearAllMocks() }                // 테스트 간 상태 격리
})
```

- Mock 객체는 Spec 블록 최상단에 `val`로 선언한다.
- `beforeEach { clearAllMocks() }` 로 각 `then` 블록 실행 전 모든 stub·verify 기록을 초기화한다.

### 3-3. Repository 통합 테스트 — `@DataJpaTest` + `SpringExtension`

```kotlin
@DataJpaTest
class FooRepositoryTest(
    private val fooRepository: FooRepository,
) : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    init {
        given("findByX") { ... }
    }
}
```

- `@DataJpaTest`는 JPA 관련 빈만 로드하고 웹·서비스 계층은 제외한다.
- `SpringExtension`이 Spring 컨텍스트와 Kotest BehaviorSpec을 연결한다.
- H2 설정은 `src/test/resources/application.yml`에 정의되어 있다.
- 테스트 간 격리는 고유한 이메일·suffix를 사용해 충돌을 방지한다. (`Transactional` 자동 롤백 없이 독립적 데이터 생성 방식 채택)

### 3-4. Domain 단위 테스트 — 순수 BehaviorSpec

```kotlin
class FooTest : BehaviorSpec({
    given("메서드 또는 팩토리") {
        `when`("조건") {
            then("결과") { ... }
        }
    }
})
```

Spring 컨텍스트, MockK 모두 불필요하다. 엔티티와 DTO 클래스를 직접 인스턴스화한다.

### 3-5. 픽스처 패턴

```kotlin
// 인라인 직접 생성
val user = User(email = "user@test.com", nickname = "유저", provider = AuthProvider.LOCAL)

// Repository 테스트용 헬퍼
private fun savedUser(suffix: String) = userRepository.save(User(email = "user$suffix@test.com", ...))

// 역할·상태 변형 헬퍼
fun memberWith(role: TripRole) = TripMember(trip = trip, user = user, role = role)
```

### 3-6. 단언문

```kotlin
// 값 비교
result.accessToken shouldBe accessToken

// 예외 타입 + 상태 코드
val ex = shouldThrow<ResponseStatusException> { service.method(...) }
ex.statusCode shouldBe HttpStatus.FORBIDDEN

// 커스텀 예외
val ex = shouldThrow<VersionConflictException> { service.updateBlock(...) }
ex.currentBlock.version shouldBe 1L

// 423 Locked (HttpStatus enum 미포함 코드)
ex.statusCode.value() shouldBe 423

// 컬렉션
result shouldHaveSize 3

// Mock 호출 검증
verify { repository.save(match { it.role == TripRole.OWNER }) }
verify(exactly = 0) { repository.delete(any()) }
```

---

## 4. H2 설정 (`src/test/resources/application.yml`)

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;MODE=MySQL;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.H2Dialect
```

- `MODE=MySQL`: H2가 MySQL 구문을 허용한다.
- `NON_KEYWORDS=VALUE`: `value` 컬럼명(`@Version` 필드 등) 예약어 충돌 방지.
- `DB_CLOSE_DELAY=-1`: JVM 종료 전까지 메모리 DB를 유지한다.

---

## 5. 테스트 파일 목록

### Service 단위 테스트

| 파일 | 대상 서비스 | 시나리오 수 |
|------|------------|------------|
| `auth/AuthServiceTest.kt` | `AuthService` | 13 |
| `trip/TripServiceTest.kt` | `TripService` | 10 |
| `block/BlockServiceTest.kt` | `BlockService` | 15 |
| `budget/BudgetServiceTest.kt` | `BudgetService` | 8 |
| `invite/InviteServiceTest.kt` | `InviteService` | 9 |
| `member/MemberServiceTest.kt` | `MemberService` | 9 |

### Repository 통합 테스트

| 파일 | 대상 | 시나리오 수 |
|------|------|------------|
| `user/UserRepositoryTest.kt` | `UserRepository` | 5 |
| `trip/TripRepositoryTest.kt` | `TripRepository`, `TripMemberRepository` | 8 |
| `block/BlockRepositoryTest.kt` | `BlockRepository` | 6 |
| `budget/BudgetRepositoryTest.kt` | `BudgetRepository` | 4 |

### Domain/Entity 단위 테스트

| 파일 | 대상 | 시나리오 수 |
|------|------|------------|
| `user/UserTest.kt` | `User.changePassword()` | 3 |
| `trip/TripResultTest.kt` | `TripResult.Detail.of()` | 3 |
| `block/BlockResultTest.kt` | `BlockResult.of()` | 5 |
| `common/ExceptionTest.kt` | `BusinessException`, `VersionConflictException` | 5 |
| `common/GlobalExceptionHandlerTest.kt` | `GlobalExceptionHandler.handleMessageNotReadable` | 1 |
| `budget/BlockBudgetSyncListenerTest.kt` | `BlockBudgetSyncListener` | 4 |

### 기타 통합 테스트

| 파일 | 대상 | 방식 |
|------|------|------|
| `config/WebSocketConfigTest.kt` | `WebSocketConfig`의 `/ws` origin 허용 목록 | `@SpringBootTest(RANDOM_PORT)` + 실제 HTTP 요청 |

---

## 6. 시나리오 명세

### 6-1. AuthService

| given | when | then |
|-------|------|------|
| signup | 이메일 중복 없음 | 유저 저장, TokenPair 반환 |
| signup | 이메일 중복 | 409 CONFLICT |
| login | 이메일·비밀번호 정상 | TokenPair 반환 |
| login | 이메일 미존재 | 401 UNAUTHORIZED |
| login | 비밀번호 불일치 | 401 UNAUTHORIZED |
| login | 소셜 계정 (password=null) | 401 UNAUTHORIZED |
| refresh | Redis 저장 토큰과 일치 | 새 accessToken 반환 |
| refresh | 토큰 파싱 실패 | 401 UNAUTHORIZED |
| refresh | Redis 저장 토큰과 불일치 | 401 UNAUTHORIZED |
| logout | 유효한 토큰 | Redis 키 삭제 |
| logout | 파싱 불가 토큰 | Redis 삭제 없이 종료 |
| kakaoLogin | 신규 사용자 | 유저 저장 후 TokenPair |
| kakaoLogin | 기존 사용자 | 저장 없이 TokenPair |

### 6-2. TripService

| given | when | then |
|-------|------|------|
| create | 정상 요청 | Trip 저장, OWNER TripMember 등록 |
| getList | 여행 2개 보유 | 2개 목록 반환 |
| getList | 여행 없음 | 빈 리스트 반환 |
| getDetail | 멤버인 경우 | 상세 반환 (days 포함) |
| getDetail | 멤버 아님 | 403 FORBIDDEN |
| getDetail | 여행 미존재 | 404 NOT_FOUND |
| update | EDITOR 이상 | 필드 수정 반영 |
| update | VIEWER | 403 FORBIDDEN |
| update | 멤버 아님 | 403 FORBIDDEN |
| delete | OWNER | 삭제 실행 |
| delete | EDITOR / VIEWER / 비멤버 | 403 FORBIDDEN |

### 6-3. BlockService

| given | when | then |
|-------|------|------|
| getBlocks | 멤버 | 블록 목록 반환 |
| getBlocks | 비멤버 | 403 FORBIDDEN |
| addBlock | EDITOR, 기존 블록 있음 | `lastPosition + 1.0`으로 저장 |
| addBlock | EDITOR, 첫 번째 블록 | `position = 1.0`으로 저장 |
| addBlock | VIEWER | 403 FORBIDDEN |
| updateBlock | 잠금 없음 + version 일치 | 필드 수정 반영 |
| updateBlock | version 불일치 | `VersionConflictException` (현재 블록 포함) |
| updateBlock | 타인이 잠금 | 423 LOCKED |
| updateBlock | 본인이 잠금 | 정상 수정 |
| updateBlock | 다른 trip의 blockId | 404 NOT_FOUND |
| moveBlock | version 일치 | dayNumber·position 변경 |
| moveBlock | version 불일치 | `VersionConflictException` |
| deleteBlock | EDITOR | 삭제 실행 |
| deleteBlock | VIEWER | 403 FORBIDDEN |
| lockBlock | 잠금 없음 | lockedBy = 본인 |
| lockBlock | 본인이 이미 잠금 | 정상 반환 |
| lockBlock | 타인이 잠금 | 423 LOCKED |
| unlockBlock | 본인 잠금 | lockedBy = null |
| unlockBlock | 타인 잠금 | lockedBy 유지 (무변경) |
| reorderBlocks | Day별 복수 블록 | Day 내 1.0, 2.0, 3.0 ... 재정규화 |

### 6-4. BudgetService

| given | when | then |
|-------|------|------|
| getItems | VIEWER 이상 멤버 | 목록 반환 |
| getItems | 비멤버 | 403 FORBIDDEN |
| create | EDITOR, 여행 존재 | 항목 저장 반환 |
| create | VIEWER | 403 FORBIDDEN |
| create | 여행 미존재 | 404 NOT_FOUND |
| update | EDITOR, 해당 trip 항목 | 필드 수정 반영 |
| update | 다른 trip 항목 | 404 NOT_FOUND |
| update | VIEWER | 403 FORBIDDEN |
| delete | EDITOR, 항목 존재 | 삭제 실행 |
| delete | 항목 미존재 | 404 NOT_FOUND |

### 6-5. InviteService

| given | when | then |
|-------|------|------|
| createInviteLink | EDITOR, 토큰 없음 | 16자리 토큰 신규 생성 |
| createInviteLink | EDITOR, 토큰 이미 존재 | 기존 토큰 재사용 |
| createInviteLink | VIEWER | 403 FORBIDDEN |
| createInviteLink | 비멤버 | 403 FORBIDDEN |
| getInviteInfo | 유효한 토큰 | 여행 미리보기 반환 |
| getInviteInfo | 미존재 토큰 | 404 NOT_FOUND |
| accept | 신규 사용자 | VIEWER TripMember 저장, tripId 반환 |
| accept | 이미 멤버 | 저장 없이 tripId 반환 (멱등) |
| accept | 미존재 토큰 | 404 NOT_FOUND |

### 6-6. MemberService

| given | when | then |
|-------|------|------|
| getMembers | 멤버 | 전체 목록 반환 |
| getMembers | 비멤버 | 403 FORBIDDEN |
| updateRole | OWNER → 대상 EDITOR | 역할 변경 |
| updateRole | OWNER → 대상 VIEWER 승격 | 역할 변경 |
| updateRole | 요청자 EDITOR | 403 FORBIDDEN |
| updateRole | 대상이 OWNER | 403 FORBIDDEN |
| updateRole | newRole = OWNER | 400 BAD_REQUEST |
| updateRole | 요청자 비멤버 | 403 FORBIDDEN |
| removeMember | OWNER → EDITOR 제거 | 삭제 실행 |
| removeMember | OWNER → VIEWER 제거 | 삭제 실행 |
| removeMember | OWNER 제거 시도 | 403 FORBIDDEN |
| removeMember | 요청자 EDITOR | 403 FORBIDDEN |
| removeMember | 대상 미존재 | 404 NOT_FOUND |

### 6-7. UserRepository

| given | when | then |
|-------|------|------|
| findByEmail | 이메일 존재 | User 반환, 필드 정확 |
| findByEmail | 이메일 미존재 | null 반환 |
| findByEmail | 소셜 로그인 사용자 | provider·password(null) 함께 반환 |
| existsByEmail | 이메일 존재 | true |
| existsByEmail | 이메일 미존재 | false |

### 6-8. TripRepository / TripMemberRepository

| given | when | then |
|-------|------|------|
| findByShareToken | 토큰 존재 | Trip 반환 |
| findByShareToken | 토큰 미존재 | null 반환 |
| findByTripIdAndUserId | 멤버 존재 | TripMember 반환 |
| findByTripIdAndUserId | 멤버 미존재 | null 반환 |
| findByTripIdAndRole | 해당 role 존재 | TripMember 반환 |
| findByTripIdAndRole | 해당 role 미존재 | null 반환 |
| countByTripId | 멤버 3명 | 3 반환 |
| findAllByTripId | 멤버 2명 | 리스트 2개 반환 |
| findAllWithTripByUserId | 2개 여행 소속 | Trip FETCH JOIN된 TripMember 2개 |

### 6-9. BlockRepository

| given | when | then |
|-------|------|------|
| findAllByTripIdOrderByDayNumberAscPositionAsc | 여러 day 블록 | dayNumber→position 오름차순 정렬 |
| findAllByTripIdOrderByDayNumberAscPositionAsc | 블록 없음 | 빈 리스트 |
| findAllByTripIdOrderByDayNumberAscPositionAsc | 다른 여행 블록 혼재 | 해당 여행 블록만 |
| findTopByTripIdAndDayNumberOrderByPositionDesc | 해당 day 블록 여러 개 | position 최대값 블록 |
| findTopByTripIdAndDayNumberOrderByPositionDesc | 블록 없음 | null 반환 |
| findTopByTripIdAndDayNumberOrderByPositionDesc | 다른 day 블록 혼재 | 지정 day만 대상 |

### 6-10. BudgetRepository

| given | when | then |
|-------|------|------|
| findAllByTripId | 항목 3개 | 3개 반환 |
| findAllByTripId | 항목 없음 | 빈 리스트 |
| findAllByTripId | 다른 여행 항목 혼재 | 해당 여행 항목만 |
| findAllByTripId | category·amount 검증 | 저장값 정확 반영 |

### 6-11. User.changePassword()

| given | when | then |
|-------|------|------|
| LOCAL 유저 | 인코딩된 비밀번호 전달 | password 필드 교체 |
| 소셜 유저 (password=null) | changePassword 호출 | password 새로 설정 |
| 기존 비밀번호 있는 유저 | 재호출 | 이전 값 덮어씀 |

### 6-12. TripResult.of()

| given | when | then |
|-------|------|------|
| of() | 3박4일 여행 | days 4개, dayNumber·date 정확 |
| of() | 당일치기 (start==end) | days 1개 |
| of() | 필드 매핑 전체 확인 | id·title·role·memberCount 정확 반영 |

### 6-13. BlockResult.of()

| given | when | then |
|-------|------|------|
| of() | 잠금 없음 | lockedBy·lockedByNickname null |
| of() | 타인이 잠금 | lockedBy UUID, lockedByNickname 닉네임 |
| of() | startTime 있음 | "HH:mm" 문자열 변환 |
| of() | startTime 없음 | null |
| of() | 전체 필드 매핑 | id·dayNumber·position·cost·memo·version 정확 |

### 6-14. BusinessException / VersionConflictException

| given | when | then |
|-------|------|------|
| BusinessException | code·message만 전달 | status = 400 BAD_REQUEST |
| BusinessException | status 명시 | 지정 HttpStatus |
| BusinessException | 404 NOT_FOUND | status = NOT_FOUND |
| VersionConflictException | currentBlock 전달 | 메시지 고정, currentBlock 포함 |
| VersionConflictException | 타입 확인 | RuntimeException 상속 |

### 6-15. GlobalExceptionHandlerTest

| given | when | then |
|-------|------|------|
| HttpMessageNotReadableException | 요청 바디 파싱 실패 | 400 + `ErrorResponse("INVALID_REQUEST", "요청 본문이 올바르지 않습니다.")` |

### 6-16. BlockBudgetSyncListenerTest

| given | when | then |
|-------|------|------|
| onCostChanged | 연동된 BudgetItem 없음 | blockId로 연결된 새 BudgetItem 생성 |
| onCostChanged | 연동된 BudgetItem 이미 있음 | category/amount/memo 갱신 (save 재호출 없음) |
| onBlockDeleted | 연동된 BudgetItem 있음 | 해당 BudgetItem 삭제 |
| onBlockDeleted | 연동된 BudgetItem 없음 | 아무 일도 일어나지 않음 |

### 6-17. WebSocketConfigTest

| given | when | then |
|-------|------|------|
| SockJS `/ws/info` 핸드셰이크 | 허용된 origin(`cors.allowed-origins`) | 200 |
| SockJS `/ws/info` 핸드셰이크 | 허용되지 않은 origin | 403 |

---

## 7. 주요 결정 사항

### Spring Context 미사용 (Service·Domain 계층)
`@SpringBootTest` 없이 Service를 직접 생성자로 인스턴스화한다. DB·Redis 연결이 불필요하므로 테스트 실행 속도가 빠르고, CI 환경에서 인프라 의존성이 없다.

### `@DataJpaTest` + H2 (Repository 계층)
전체 애플리케이션 컨텍스트 없이 JPA 빈만 로드한다. H2 `MODE=MySQL`로 MySQL 구문을 허용하면서도 외부 인프라 없이 쿼리 메서드를 검증한다.

### Repository 테스트 격리 전략
`@Transactional` 자동 롤백 대신 각 `then` 블록에서 고유한 이메일·suffix를 가진 새 엔티티를 생성한다. Kotest BehaviorSpec은 JUnit의 트랜잭션 롤백과 통합하기 복잡하므로 데이터 독립성으로 대신한다.

### MockK 선택 이유
Mockito는 Kotlin의 `final class` (data class 포함)를 기본 Mock할 수 없어 별도 설정이 필요하다. MockK는 Kotlin 친화적으로 설계되어 data class, object, extension function, companion object를 추가 설정 없이 Mock한다.

### `VersionConflictException` 타입 검증
`ResponseStatusException`이 아닌 `VersionConflictException`을 직접 `shouldThrow`로 잡아 `currentBlock` 필드를 검증한다. 이 예외는 `GlobalExceptionHandler`에서 409로 변환되며, 응답 body에 `currentBlock`을 포함하는 것이 핵심 계약이기 때문이다.

### 423 상태 코드 검증 방식
`HttpStatus` enum에 423(`LOCKED`)이 존재하지만, 서비스 코드가 `HttpStatus.valueOf(423)`로 생성하므로 테스트에서도 `.statusCode.value() shouldBe 423`으로 숫자 값을 직접 비교한다.

### `unlockBlock` 상태 직접 검증
`unlockBlock`은 반환값이 없다. 실행 후 블록 엔티티의 `lockedBy` 필드를 직접 읽어 상태 변이를 확인한다. `verify`로 Repository 호출을 검증하는 대신 엔티티 상태를 보는 것이 의도를 더 명확히 표현한다.

### `reorderBlocks` 픽스처 전략
Day별로 비정상적인 소수 position(`0.1`, `0.15`, `0.2`)을 가진 블록을 만들어, 서비스 실행 후 정수에 가까운 값(`1.0`, `2.0`, `3.0`)으로 재정규화되는지를 엔티티 필드 직접 비교로 확인한다.
