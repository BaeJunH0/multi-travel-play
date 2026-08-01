# 여행 플래너 — API 스펙

## 공통

- Base URL: `/api`
- 인증: `Authorization: Bearer {accessToken}` (명시된 예외 제외 모든 엔드포인트)
- Content-Type: `application/json`
- 에러 응답 형식:
  ```json
  { "code": "ERROR_CODE", "message": "설명" }
  ```

---

## 1. 인증 (Auth)

### 카카오 소셜 로그인 시작
```
GET /api/auth/oauth2/kakao
```
- 카카오 인증 URL로 `302 redirect`
- 인증 불필요

### 카카오 OAuth 콜백
```
GET /api/auth/oauth2/kakao/callback?code={code}
```
- 카카오로부터 전달받은 code로 토큰 교환 및 유저 upsert
- 성공 시 refreshToken 쿠키(HttpOnly) 설정 후 `/` 로 redirect
- 인증 불필요

### 구글 소셜 로그인 시작
```
GET /api/auth/oauth2/google
```
- 구글 인증 URL로 `302 redirect`
- 인증 불필요

### 구글 OAuth 콜백
```
GET /api/auth/oauth2/google/callback?code={code}
```
- 구글로부터 전달받은 code로 토큰 교환 및 유저 upsert
- 성공 시 refreshToken 쿠키(HttpOnly) 설정 후 `/` 로 redirect
- 인증 불필요

### 이메일 회원가입
```
POST /api/auth/signup
```
**요청**
```json
{ "email": "string", "password": "string", "nickname": "string" }
```
**응답** `201 Created`
```json
{ "accessToken": "string" }
```
**에러**: 이메일 중복 시 `409 CONFLICT`
- 인증 불필요

### 이메일 로그인
```
POST /api/auth/login
```
**요청**
```json
{ "email": "string", "password": "string" }
```
**응답** + `Set-Cookie: refreshToken=...; HttpOnly; Path=/api/auth`
```json
{ "accessToken": "string" }
```
**에러**: 이메일/비밀번호 불일치 시 `401 UNAUTHORIZED`
- 인증 불필요

### 토큰 갱신
```
POST /api/auth/refresh
```
- 쿠키의 `refreshToken` 으로 새 accessToken 발급
- **응답**
```json
{ "accessToken": "string" }
```
**에러**: refreshToken 없거나 만료 시 `401 UNAUTHORIZED`
- 인증 불필요

### 내 정보 조회
```
GET /api/auth/me
```
**응답**
```json
{ "id": "uuid", "email": "string", "nickname": "string", "avatarColor": "string", "provider": "LOCAL | KAKAO | GOOGLE" }
```

### 로그아웃
```
POST /api/auth/logout
```
**응답** `204 No Content`
- Redis에서 refreshToken 삭제 + refreshToken 쿠키 만료
- 인증 불필요

### 비밀번호 재설정 코드 발송
```
POST /api/auth/password-reset/request
```
**요청**
```json
{ "email": "string" }
```
**응답** `200 OK`
- 이메일로 6자리 인증 코드 발송, Redis에 15분간 보관
- 소셜 로그인 계정(Kakao/Google) 미지원
- 인증 불필요

### 비밀번호 재설정 확인
```
POST /api/auth/password-reset/confirm
```
**요청**
```json
{ "email": "string", "token": "string (6자리 코드)", "newPassword": "string (8자 이상)" }
```
**응답**
```json
{ "accessToken": "string" }
```
**에러**: 코드 불일치/만료 시 `400 INVALID_RESET_TOKEN`
- 인증 불필요

---

## 2. 여행 (Trip)

### 여행 목록 조회
```
GET /api/trips
```
**응답**
```json
[
  {
    "id": "uuid",
    "title": "string",
    "destination": "string",
    "startDate": "YYYY-MM-DD",
    "endDate": "YYYY-MM-DD",
    "memberCount": 3,
    "myRole": "OWNER | EDITOR | VIEWER"
  }
]
```

### 여행 생성
```
POST /api/trips
```
**요청**
```json
{
  "title": "string",
  "destination": "string",
  "startDate": "YYYY-MM-DD",
  "endDate": "YYYY-MM-DD"
}
```
**응답** `201 Created` — TripDetail 객체 (아래 참고)

### 여행 상세 조회
```
GET /api/trips/{tripId}
```
**응답**
```json
{
  "id": "uuid",
  "title": "string",
  "destination": "string",
  "startDate": "YYYY-MM-DD",
  "endDate": "YYYY-MM-DD",
  "memberCount": 3,
  "myRole": "OWNER | EDITOR | VIEWER",
  "days": [
    { "dayNumber": 1, "date": "YYYY-MM-DD" },
    { "dayNumber": 2, "date": "YYYY-MM-DD" }
  ]
}
```
> `days` 는 `startDate ~ endDate` 범위에서 서버가 자동 계산하여 반환

### 여행 수정
```
PATCH /api/trips/{tripId}
```
**요청**: `title`, `destination`, `startDate`, `endDate` 부분 수정 가능
- 최소 권한: EDITOR

### 여행 삭제
```
DELETE /api/trips/{tripId}
```
**응답** `204 No Content`
- OWNER만 가능

---

## 3. 블록 (Block)

### 블록 목록 조회
```
GET /api/trips/{tripId}/blocks
```
**응답**
```json
[
  {
    "id": "uuid",
    "dayNumber": 1,
    "position": 1.0,
    "blockType": "HOTEL | FOOD | CAFE | PLACE | TRANSPORT",
    "placeName": "string",
    "lat": "number | null",
    "lng": "number | null",
    "startTime": "HH:mm | null",
    "durationMin": "number | null",
    "cost": "number | null",
    "memo": "string | null",
    "lockedBy": "uuid | null",
    "lockedByNickname": "string | null",
    "version": 1
  }
]
```

### 블록 추가
```
POST /api/trips/{tripId}/blocks
```
**요청**
```json
{
  "dayNumber": 1,
  "blockType": "PLACE",
  "placeName": "string",
  "startTime": "HH:mm | null",
  "durationMin": "number | null",
  "cost": "number | null",
  "memo": "string | null"
}
```
**응답** `201 Created` — Block 객체 (`position` 은 해당 day 마지막 + 1.0 자동 설정)
- 최소 권한: EDITOR

### 블록 수정
```
PATCH /api/trips/{tripId}/blocks/{blockId}
```
**요청**: Block 필드 부분 수정. `version` 필수 (낙관적 잠금용)
```json
{
  "placeName": "string",
  "startTime": "HH:mm",
  "durationMin": 60,
  "cost": 1000,
  "memo": "string",
  "version": 3
}
```
**에러**: version 불일치 시 `409`
```json
{ "code": "VERSION_CONFLICT", "message": "string", "currentBlock": { } }
```
- 최소 권한: EDITOR

### 블록 이동 (DnD)
```
PATCH /api/trips/{tripId}/blocks/{blockId}/move
```
**요청**
```json
{
  "dayNumber": 2,
  "position": 1.5,
  "version": 3
}
```
**에러**: version 불일치 시 `409 VERSION_CONFLICT`
- 최소 권한: EDITOR

### 블록 삭제
```
DELETE /api/trips/{tripId}/blocks/{blockId}
```
**응답** `204 No Content`
- 최소 권한: EDITOR

### Position 재정규화
```
POST /api/trips/{tripId}/blocks/reorder
```
**응답** `204 No Content`
- `position` 간격이 `1e-9` 미만으로 좁아졌을 때 호출
- 각 day 내 블록을 `1.0, 2.0, 3.0 ...` 으로 재정규화
- 최소 권한: EDITOR

---

## 4. 블록 잠금 (Lock)

### 잠금 획득 (편집 모달 열릴 때)
```
POST /api/trips/{tripId}/blocks/{blockId}/lock
```
**응답** `200 OK` — Block 객체
**에러**: 다른 사용자가 잠금 중이면 `423 ALREADY_LOCKED`
- 최소 권한: EDITOR

### 잠금 해제 (모달 닫힐 때)
```
DELETE /api/trips/{tripId}/blocks/{blockId}/lock
```
**응답** `204 No Content`
- 본인이 걸어둔 잠금만 해제됨

---

## 5. 초대 (Invite)

### 초대 링크 생성
```
POST /api/trips/{tripId}/invite
```
**응답**
```json
{ "shareToken": "string", "inviteUrl": "string" }
```
- 재호출 시 기존 토큰 덮어씀
- 최소 권한: EDITOR

### 초대 정보 조회
```
GET /api/invite/{shareToken}
```
**응답**
```json
{
  "tripId": "uuid",
  "tripTitle": "string",
  "destination": "string",
  "startDate": "YYYY-MM-DD",
  "endDate": "YYYY-MM-DD",
  "memberCount": 3,
  "inviterNickname": "string"
}
```
- 인증 불필요

### 초대 수락
```
POST /api/invite/{shareToken}/accept
```
**응답** `201 Created`
```json
{ "tripId": "uuid" }
```
- VIEWER 권한으로 trip_members 등록
- 이미 멤버인 경우 tripId만 반환 (멱등)

---

## 6. 멤버 (Member)

### 멤버 목록 조회
```
GET /api/trips/{tripId}/members
```
**응답**
```json
[
  {
    "userId": "uuid",
    "nickname": "string",
    "avatarColor": "string",
    "role": "OWNER | EDITOR | VIEWER"
  }
]
```
- 최소 권한: VIEWER

### 멤버 권한 변경
```
PATCH /api/trips/{tripId}/members/{userId}
```
**요청**
```json
{ "role": "EDITOR | VIEWER" }
```
**응답** `200 OK`
- OWNER만 가능

### 멤버 제거
```
DELETE /api/trips/{tripId}/members/{userId}
```
**응답** `204 No Content`
- OWNER만 가능

---

## 7. 예산 (Budget)

### 예산 항목 목록 조회
```
GET /api/trips/{tripId}/budget
```
**응답**
```json
[
  {
    "id": "uuid",
    "category": "string",
    "description": "string",
    "amount": 50000,
    "paidBy": "uuid | null"
  }
]
```
- 최소 권한: VIEWER

### 예산 항목 추가
```
POST /api/trips/{tripId}/budget
```
**요청**
```json
{
  "category": "string",
  "description": "string",
  "amount": 50000,
  "paidBy": "uuid | null"
}
```
**응답** `201 Created` — BudgetItem 객체
- 최소 권한: EDITOR

### 예산 항목 수정
```
PATCH /api/trips/{tripId}/budget/{itemId}
```
**요청**: 부분 수정 가능
- 최소 권한: EDITOR

### 예산 항목 삭제
```
DELETE /api/trips/{tripId}/budget/{itemId}
```
**응답** `204 No Content`
- 최소 권한: EDITOR

---

## 8. AI 일정 생성

### AI 일정 생성 요청
```
POST /api/trips/{tripId}/ai/generate
```
**요청**
```json
{
  "tags": ["HOTEL", "FOOD", "CAFE", "PLACE", "TRANSPORT"],
  "targetDays": [1, 2, 3],
  "style": "맛집 위주 | 관광 위주 | 쇼핑 위주 | 휴양 위주",
  "intensity": "여유롭게 | 보통 | 빡빡하게",
  "extraRequest": "string | null"
}
```
**응답**
```json
{
  "generationId": "uuid",
  "blocks": [
    {
      "tempId": "temp-1",
      "blockType": "FOOD",
      "placeName": "string",
      "startTime": "HH:mm | null",
      "durationMin": "number | null",
      "cost": "number | null",
      "suggestedDay": 1,
      "memo": "string | null"
    }
  ]
}
```
- 생성 결과는 Redis에 10분간 보관
- 최소 권한: EDITOR

### AI 일정 적용
```
POST /api/trips/{tripId}/ai/apply
```
**요청**
```json
{
  "generationId": "uuid",
  "selectedBlocks": [
    { "tempId": "temp-1", "dayNumber": 1, "position": 2.0 },
    { "tempId": "temp-2", "dayNumber": 2, "position": 1.0 }
  ]
}
```
**응답**
```json
{
  "addedBlocks": [
    {
      "tempId": "temp-1",
      "block": { }
    }
  ]
}
```
- `generationId` 만료(10분) 시 `404 NOT_FOUND`
- Google Places API로 `lat/lng` 자동 보강 (실패 시 null 허용)
- 최소 권한: EDITOR

---

## 9. WebSocket (STOMP)

### 연결
```
SockJS endpoint: /ws
구독 destination: /topic/trip.{tripId}
Presence 발행:   /app/trip.{tripId}.presence
```

### 수신 이벤트 형식
```json
{
  "action": "ADD | UPDATE | MOVE | DELETE | LOCK | UNLOCK | CONFLICT | PRESENCE",
  "tripId": "uuid",
  "payload": { }
}
```

### 이벤트별 payload

| action | 발생 시점 | payload |
|--------|---------|---------|
| `ADD` | 블록 추가 | Block 객체 전체 |
| `UPDATE` | 블록 수정 | `{ blockId, block }` |
| `MOVE` | 블록 이동 | `{ blockId, dayNumber, position, version }` |
| `DELETE` | 블록 삭제 | `{ blockId }` |
| `LOCK` | 잠금 획득 | `{ blockId, lockedBy, lockedByNickname }` |
| `UNLOCK` | 잠금 해제 | `{ blockId }` |
| `CONFLICT` | version 충돌 | `{ blockId, currentBlock }` |
| `PRESENCE` | 접속자 변경 | `{ users: [{ userId, nickname, avatarColor, activeDay }] }` |

### Presence 발행 형식
```json
{
  "userId": "uuid",
  "nickname": "string",
  "avatarColor": "string",
  "activeDay": 2
}
```

### 충돌 처리
- 블록마다 `version` 관리
- `version` 불일치 시 REST `409` 응답 + `CONFLICT` 이벤트 브로드캐스트
- 클라이언트는 Optimistic Update 후 `CONFLICT` 수신 시 서버 상태로 롤백

---

## 10. 에러 코드

| HTTP | code | 상황 |
|------|------|------|
| 400 | `INVALID_REQUEST` | 요청 형식 오류 |
| 401 | `UNAUTHORIZED` | 인증 토큰 없음 / 만료 |
| 403 | `FORBIDDEN` | 권한 없음 (VIEWER가 편집 시도 등) |
| 404 | `NOT_FOUND` | 리소스 없음 |
| 409 | `VERSION_CONFLICT` | 블록 version 불일치 |
| 423 | `ALREADY_LOCKED` | 다른 사용자가 블록 잠금 중 |
| 400 | `INVALID_RESET_TOKEN` | 비밀번호 재설정 코드 불일치 또는 만료 |
| 403 | `SOCIAL_ACCOUNT` | 소셜 로그인 계정은 비밀번호 변경 불가 |
| 500 | `INTERNAL_ERROR` | 서버 오류 |
