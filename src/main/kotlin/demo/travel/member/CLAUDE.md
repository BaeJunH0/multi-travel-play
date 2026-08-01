# member 패키지

여행(Trip)에 속한 멤버의 목록 조회, 권한 변경, 제거를 담당하는 패키지.

---

## API 엔드포인트

| Method | Path | 설명 | 최소 권한 |
|--------|------|------|-----------|
| `GET` | `/api/trips/{tripId}/members` | 멤버 목록 조회 | VIEWER |
| `PATCH` | `/api/trips/{tripId}/members/{userId}` | 멤버 권한 변경 | OWNER |
| `DELETE` | `/api/trips/{tripId}/members/{userId}` | 멤버 제거 | OWNER |

---

## 권한 체계

`TripRole` 열거값: `OWNER` > `EDITOR` > `VIEWER`

- **OWNER**: 멤버 권한 변경 및 제거 가능. Trip당 반드시 1명 존재.
- **EDITOR**: 블록·예산 등 여행 내용 편집 가능. 멤버 관리 불가.
- **VIEWER**: 조회만 가능. 초대 수락 시 기본 부여되는 역할.

역할 변경 시 `role` 필드에 `EDITOR` 또는 `VIEWER`만 허용. `OWNER`는 요청 불가.

---

## 핵심 비즈니스 로직

### 권한 변경 (`updateRole`)
- 요청자가 해당 Trip의 멤버가 아니면 `403`.
- 요청자의 역할이 `OWNER`가 아니면 `403`.
- `newRole == OWNER` 이면 `400` — OWNER는 직접 양도 불가.
- 대상 멤버의 현재 역할이 `OWNER` 이면 `403` — OWNER의 역할은 변경 불가.

### 멤버 제거 (`removeMember`)
- 요청자가 해당 Trip의 멤버가 아니면 `403`.
- 요청자의 역할이 `OWNER`가 아니면 `403`.
- 대상 멤버의 역할이 `OWNER` 이면 `403` — OWNER는 제거 불가.

> OWNER를 교체하려면 별도의 양도 흐름이 필요하다 (현재 미구현). 직접 `role = OWNER` 세팅이나 기존 OWNER 제거는 서비스 레이어에서 모두 차단된다.

---

## 응답 DTO

`MemberResponse` — `userId`, `nickname`, `role` 세 필드만 반환.  
`avatarColor`는 스펙 문서에 명시되어 있으나 **현재 구현에서는 포함되지 않는다** (`TripMember` → `User`에서 매핑되지 않음). 추가 시 `MemberResult.of()`(엔티티 → Result 변환)와 `TripMember` 엔티티를 함께 수정해야 한다.
