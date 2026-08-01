# 여행 플래너 백엔드

여러 명이 함께 여행 일정을 실시간으로 편집할 수 있는 협업 여행 플래너 서비스의 백엔드입니다.

## 소개

여행 계획은 보통 여러 사람이 함께 세우지만, 정작 편집은 한 명씩 돌아가며 하거나 메신저로 의견을 취합해 반영하는 경우가 많습니다. 
이 프로젝트는 하루 단위 "블록"(숙소/식사/카페/장소/이동)으로 일정을 구성하고, 여러 사용자가 동시에 접속해 실시간으로 같은 일정을 편집·조율할 수 있게 하는 것을 목표로 합니다.

### 주요 기능

- **실시간 협업 편집**: WebSocket(STOMP)으로 블록 추가/수정/이동/삭제가 접속 중인 모든 멤버에게 즉시 브로드캐스트됩니다. 접속자 presence(활성 Day 포함)도 함께 공유됩니다.
- **블록 기반 일정 관리**: Day별로 블록을 자유롭게 드래그 앤 드롭으로 재배치할 수 있으며, `position` 값 기반으로 순서를 관리하고 필요 시 재정규화합니다.
- **동시 편집 충돌 방지**: 블록 편집 시 잠금(lock)을 걸어 다른 사용자의 동시 수정을 막고, `version` 기반 낙관적 잠금으로 갱신 충돌을 감지해 `409 VERSION_CONFLICT`로 알려줍니다.
- **AI 일정 자동 생성**: OpenAI API로 목적지·기간·스타일·강도 등 조건에 맞는 일정 초안을 생성하고, 사용자가 선택한 블록만 골라 적용할 수 있습니다. Google Places API로 장소 좌표를 보강합니다.
- **인증/인가**: 이메일 회원가입·로그인 외에 카카오/구글 소셜 로그인을 지원하며, JWT(access) + Redis 기반 refresh token, 이메일 인증 코드 기반 비밀번호 재설정을 제공합니다.
- **권한 관리**: 여행별로 OWNER/EDITOR/VIEWER 역할을 두어 조회·편집·삭제 권한을 구분합니다.
- **초대 링크**: 공유 토큰 기반 초대 링크로 VIEWER 권한 멤버를 손쉽게 추가할 수 있습니다.
- **예산 관리**: 여행별 예산 항목(카테고리, 금액, 정산자)을 등록하고 관리합니다.

## 기술 스택

| 영역 | 기술 |
|------|------|
| Language / Framework | Kotlin + Spring Boot 4 |
| ORM | Spring Data JPA |
| Security | spring-security-crypto (BCrypt) + JWT (jjwt 0.12.6) |
| Database | MySQL + Redis |
| 실시간 | WebSocket + STOMP + SockJS |
| AI | OpenAI API (`gpt-4o`) |
| External | Google Places API, Kakao OAuth 2.0, Google OAuth 2.0 |
| HTTP Client | WebFlux WebClient |
| Mail | Spring Mail (SMTP) |

## 프로젝트 구조

```
src/main/kotlin/demo/travel/
├── TravelApplication.kt
│
├── ai/                          # AI 일정 생성
│   ├── AiController.kt
│   ├── AiService.kt
│   ├── GooglePlacesClient.kt    # 장소 좌표 조회
│   ├── OpenAiClient.kt          # OpenAI API 호출
│   └── dto/
│
├── auth/                        # 인증
│   ├── AuthController.kt
│   ├── AuthService.kt           # 회원가입, 로그인, Kakao/Google OAuth
│   ├── CurrentUser.kt           # @CurrentUser 어노테이션
│   ├── CurrentUserArgumentResolver.kt  # User 주입
│   ├── GoogleOAuthClient.kt     # 구글 API 호출
│   ├── GoogleProperties.kt
│   ├── JwtFilter.kt             # Bearer 토큰 추출 → request attribute
│   ├── JwtProvider.kt           # JWT 생성/검증
│   ├── KakaoOAuthClient.kt      # 카카오 API 호출
│   ├── KakaoProperties.kt
│   └── PasswordResetService.kt  # 이메일 인증 코드 기반 비밀번호 재설정
│
├── block/                       # 블록 도메인 + CRUD + 잠금
│   ├── BlockController.kt       # WebSocket 이벤트 발행도 담당
│   ├── BlockRepository.kt
│   ├── BlockService.kt
│   ├── ScheduleBlock.kt
│   └── dto/
│
├── budget/                      # 예산 도메인 + CRUD
│   ├── BudgetController.kt
│   ├── BudgetItem.kt
│   ├── BudgetRepository.kt
│   ├── BudgetService.kt
│   └── dto/
│
├── common/exception/
│   ├── BusinessException.kt
│   ├── GlobalExceptionHandler.kt
│   └── VersionConflictException.kt
│
├── config/
│   ├── WebConfig.kt             # ArgumentResolver 등록
│   └── WebSocketConfig.kt       # STOMP + SockJS 설정
│
├── invite/                      # 초대 링크
│   ├── InviteController.kt
│   ├── InviteService.kt
│   └── dto/
│
├── member/                      # 멤버 관리
│   ├── MemberController.kt
│   ├── MemberService.kt
│   └── dto/
│
├── trip/                        # 여행 도메인 + CRUD
│   ├── Trip.kt
│   ├── TripController.kt
│   ├── TripMember.kt
│   ├── TripRepository.kt        # TripRepository + TripMemberRepository
│   ├── TripService.kt
│   └── dto/
│
├── user/                        # 유저 도메인
│   ├── User.kt
│   └── UserRepository.kt
│
└── websocket/                   # 실시간 협업
    ├── PresenceController.kt    # presence 수신 → 브로드캐스트
    ├── PresenceStore.kt         # 접속자 in-memory 관리
    ├── TripEvent.kt             # 이벤트 타입 정의
    └── TripEventPublisher.kt    # /topic/trip.{tripId} 브로드캐스트
```

## 문서

| 문서 | 내용 |
|------|------|
| [docs/spec-api.md](docs/spec-api.md) | REST/WebSocket API 명세 |
| [docs/spec-db.md](docs/spec-db.md) | DB 테이블 스키마 |
| [docs/spec-prompt.md](docs/spec-prompt.md) | AI 일정 생성 프롬프트 명세 |
| [docs/spec-test.md](docs/spec-test.md) | 테스트 전략 및 명세 |

## 로컬 실행

```bash
# MySQL, Redis 실행 후
./gradlew bootRun
```

DB 테이블은 `src/main/resources/schema.sql` 참고.
