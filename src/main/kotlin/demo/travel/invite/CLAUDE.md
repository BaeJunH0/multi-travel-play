# invite 패키지

여행에 다른 사용자를 초대하는 링크 생성·조회·수락 흐름을 담당한다.

---

## API 엔드포인트

| Method | Path | 설명 | 인증 |
|--------|------|------|------|
| `POST` | `/api/trips/{tripId}/invite` | 초대 링크 생성 | 필요 (최소 EDITOR) |
| `GET` | `/api/invite/{shareToken}` | 초대 정보 조회 | 불필요 |
| `POST` | `/api/invite/{shareToken}/accept` | 초대 수락 | 필요 |

---

## 초대 흐름

1. **링크 생성** — EDITOR 이상 권한을 가진 멤버가 `POST /api/trips/{tripId}/invite` 호출
   - `shareToken`과 `inviteUrl`(`{baseUrl}/invite/{token}`) 반환
2. **링크 공유** — 수신자가 브라우저에서 초대 URL 접근
3. **초대 정보 조회** — 프론트가 `GET /api/invite/{shareToken}`으로 여행 미리보기 표시 (비인증)
4. **수락** — 로그인한 사용자가 `POST /api/invite/{shareToken}/accept` 호출
   - `TripMember`로 VIEWER 권한 등록, 응답으로 `{ tripId }` 반환

---

## shareToken 발급 방식 및 멱등성 처리

### 발급

```kotlin
val token = trip.shareToken ?: UUID.randomUUID().toString().replace("-", "").take(16)
trip.shareToken = token
```

- `Trip` 엔티티의 `shareToken` 컬럼에 저장된다.
- **이미 토큰이 존재하면 기존 값을 그대로 사용**한다(재호출 시 덮어쓰지 않음).
  - 스펙 문서에는 "재호출 시 기존 토큰 덮어씀"이라고 기재되어 있으나, 구현은 기존 토큰을 유지하는 방식이다.
- 토큰 형식: UUID에서 하이픈 제거 후 앞 16자리 (예: `a1b2c3d4e5f60708`).

### 수락 멱등성

```kotlin
val alreadyMember = tripMemberRepository.findByTripIdAndUserId(trip.id, user.id)
if (alreadyMember != null) return trip.id
```

- 이미 멤버인 경우 새 `TripMember`를 생성하지 않고 `tripId`만 반환한다.
- HTTP 상태는 항상 `201 Created`로 고정되어 있으므로 클라이언트는 멱등 응답과 신규 등록 응답을 구분하기 어렵다.

---

## 주요 의존 관계

- `TripRepository.findByShareToken(token)` — 토큰으로 여행 조회
- `TripMemberRepository.findByTripIdAndUserId` — 권한 확인 및 중복 가입 방지
- `TripMemberRepository.findByTripIdAndRole(tripId, OWNER)` — `inviterNickname` 조회 시 OWNER를 초대자로 사용
- `@CurrentUser` — Spring 인증 컨텍스트에서 `User` 객체 주입
