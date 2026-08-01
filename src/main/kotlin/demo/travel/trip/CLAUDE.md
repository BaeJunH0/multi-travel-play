# trip 패키지

여행(Trip) 엔티티의 CRUD와 멤버십 관리를 담당하는 패키지.

---

## API 엔드포인트

| Method   | Path                | 설명          | 최소 권한  |
|----------|---------------------|---------------|-----------|
| `GET`    | `/api/trips`        | 내 여행 목록 조회 | 로그인    |
| `POST`   | `/api/trips`        | 여행 생성       | 로그인    |
| `GET`    | `/api/trips/{tripId}` | 여행 상세 조회 | VIEWER   |
| `PATCH`  | `/api/trips/{tripId}` | 여행 수정      | EDITOR   |
| `DELETE` | `/api/trips/{tripId}` | 여행 삭제      | OWNER    |

> "로그인"은 JWT 인증만 필요하고 TripMember 멤버십은 불필요한 경우를 의미한다.
> 상세·수정·삭제는 `TripMember` 레코드가 없으면 `403`을 반환한다.

---

## 엔티티

### Trip (`trips` 테이블)

| 필드          | 타입            | 설명                               |
|--------------|-----------------|-------------------------------------|
| `id`         | UUID (PK)       | 자동 생성                           |
| `owner`      | User (FK)       | 여행을 최초 생성한 사용자            |
| `title`      | String          | 여행 제목                           |
| `destination`| String          | 목적지                             |
| `startDate`  | LocalDate       | 출발일                             |
| `endDate`    | LocalDate       | 귀국일 (inclusive)                 |
| `shareToken` | String? (unique)| 초대 링크 토큰, 없으면 null         |
| `version`    | Long            | 낙관적 잠금용 (`@Version`)          |
| `createdAt`  | LocalDateTime   | 생성 시각 (불변)                    |

### TripMember (`trip_members` 테이블)

| 필드       | 타입       | 설명                         |
|-----------|------------|-------------------------------|
| `id`      | UUID (PK)  | 자동 생성                     |
| `trip`    | Trip (FK)  |                               |
| `user`    | User (FK)  |                               |
| `role`    | TripRole   | 멤버 권한                     |
| `joinedAt`| LocalDateTime | 참여 시각 (불변)            |

`(trip_id, user_id)` 복합 유니크 제약이 있다.

### TripRole

```
OWNER > EDITOR > VIEWER
```

- `OWNER`: 여행 삭제, 멤버 권한 변경·제거 가능
- `EDITOR`: 여행 수정, 블록·예산·초대 링크 관리 가능
- `VIEWER`: 조회만 가능

---

## 핵심 비즈니스 로직

### 여행 생성 시 OWNER 자동 등록

`TripService.create`에서 `Trip`을 저장한 직후 생성자를 `TripRole.OWNER`로 `TripMember`에 등록한다. 별도 초대 없이 생성자가 곧 소유자가 된다.

```kotlin
tripMemberRepository.save(TripMember(trip = trip, user = user, role = TripRole.OWNER))
```

### days 자동 계산 (응답 전용)

`TripDetailResponse.of`에서 `startDate ~ endDate` 범위를 `ChronoUnit.DAYS`로 계산해 `DayResponse` 리스트를 생성한다. DB에 저장되지 않으며 조회·생성·수정 응답마다 서버에서 재계산된다.

```kotlin
(0..startDate.until(endDate, ChronoUnit.DAYS).toInt())
    .map { i -> DayResponse(i + 1, startDate.plusDays(i.toLong())) }
```

### 권한 체크 방식

- **멤버 여부 확인**: `findMemberOrThrow` — `TripMember`가 없으면 `403`
- **EDITOR 이상 요구**: `requireEditorOrAbove` — `VIEWER`이면 `403`
- **OWNER 전용**: `delete`에서 `role != OWNER`이면 직접 `403`

### 수정 (PATCH) — 부분 업데이트

`UpdateTripRequest`의 모든 필드는 nullable이며, null이 아닌 필드만 엔티티에 반영한다. `@Transactional` 컨텍스트 내 dirty checking으로 저장된다.

---

## 주요 Repository 메서드

| 메서드 | 설명 |
|--------|------|
| `findAllWithTripByUserId` | `JOIN FETCH`로 Trip을 함께 로드 (N+1 방지) |
| `countByTripId` | 멤버 수 집계 (목록·상세 응답에서 사용) |
| `findByTripIdAndUserId` | 권한 체크 전용 |
| `findByShareToken` | 초대 수락 시 Trip 조회 |
