# 여행 플래너 — API 스펙

> 이 문서는 [docs/api/](../api/)를 한 파일로 합친 사본이다. 내용이 다르면 `docs/api/`가 기준이다.

## 공통

- Base URL: `/api`
- 인증: `Authorization: Bearer {accessToken}` (명시된 예외 제외 모든 엔드포인트)
- Content-Type: `application/json`
- CORS: `cors.allowed-origins`에 등록된 origin만 허용 (credentials 포함). 허용되지 않은 origin의 preflight는 `403`
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
- 성공 시 refreshToken 쿠키(HttpOnly, `Path=/api/auth`, 7일) + accessToken 쿠키(HttpOnly, `Path=/`, 1시간) 설정 후 `/` 로 redirect
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
- 성공 시 refreshToken 쿠키(HttpOnly, `Path=/api/auth`, 7일) + accessToken 쿠키(HttpOnly, `Path=/`, 1시간) 설정 후 `/` 로 redirect
- 인증 불필요

### 이메일 회원가입
```
POST /api/auth/signup
```
**요청**
```json
{ "email": "string", "password": "string (8자 이상)", "nickname": "string" }
```
**응답** `201 Created` + `Set-Cookie: refreshToken=...; HttpOnly; Path=/api/auth`
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
{ "id": "uuid", "email": "string", "nickname": "string", "provider": "LOCAL | KAKAO | GOOGLE" }
```

### 로그아웃
```
POST /api/auth/logout
```
**응답** `204 No Content`
- 서버에 저장된 refreshToken 삭제 + refreshToken 쿠키 만료
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
- 이메일로 6자리 인증 코드 발송, 서버 메모리에 15분간 보관 (서버 재시작 시 사라짐)
- LOCAL 계정에만 코드를 발송한다. 가입되지 않은 이메일이나 소셜 로그인 계정(Kakao/Google)이면 코드 저장·메일 발송 없이 똑같이 `200 OK` (이메일 열거 방지)
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
- 가입되지 않은 이메일, 소셜 로그인 계정도 같은 `400 INVALID_RESET_TOKEN`으로 응답한다 (가입 여부·가입 경로 비노출)
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
**요청**: `title`, `destination`, `startDate`, `endDate` 부분 수정 가능 (전달된 필드만 반영)

**응답** — TripDetail 객체
- 최소 권한: EDITOR

### 여행 삭제
```
DELETE /api/trips/{tripId}
```
**응답** `204 No Content`
- OWNER만 가능

---

## 3. 블록 / 잠금 (Block / Lock)

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
- `blockType`은 `HOTEL | FOOD | CAFE | PLACE | TRANSPORT`만 허용. budget 전용 카테고리(`FLIGHT`, `ETC`)는 `400 INVALID_REQUEST`
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
**응답** — 수정된 Block 객체

**에러**
- version 불일치 시 `409` (같은 여행 접속자에게 `CONFLICT` 이벤트도 브로드캐스트)
```json
{ "code": "VERSION_CONFLICT", "message": "string", "currentBlock": { } }
```
- 다른 사용자가 잠금 중이면 `423 ALREADY_LOCKED`
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
**응답** — 이동된 Block 객체

**에러**: version 불일치 시 `409 VERSION_CONFLICT` (+ `CONFLICT` 이벤트 브로드캐스트)
- 다른 사용자가 잠금 중이면 `423 ALREADY_LOCKED`
- 최소 권한: EDITOR

### 블록 삭제
```
DELETE /api/trips/{tripId}/blocks/{blockId}
```
**응답** `204 No Content`
**에러**: 다른 사용자가 잠금 중이면 `423 ALREADY_LOCKED`
- version은 확인하지 않음
- 최소 권한: EDITOR

### Position 재정규화
```
POST /api/trips/{tripId}/blocks/reorder
```
**응답** `204 No Content`
- `position` 간격이 `1e-9` 미만으로 좁아졌을 때 호출
- 각 day 내 블록을 `1.0, 2.0, 3.0 ...` 으로 재정규화
- SSE 이벤트를 발행하지 않음
- 최소 권한: EDITOR

---

### 블록 잠금 (Lock)

### 잠금 획득 (편집 모달 열릴 때)
```
POST /api/trips/{tripId}/blocks/{blockId}/lock
```
**응답** `200 OK` — Block 객체
**에러**: 다른 사용자가 잠금 중이면 `423 ALREADY_LOCKED`
- 잠금 만료 시각을 지금부터 3분 뒤로 둔다. 만료된 잠금은 없는 것으로 보고 새로 잡을 수 있다
- 최소 권한: EDITOR

### 잠금 해제 (모달 닫힐 때)
```
DELETE /api/trips/{tripId}/blocks/{blockId}/lock
```
**응답** `204 No Content`
- 본인이 걸어둔 잠금만 해제됨 (다른 사용자의 잠금이면 아무 변화 없이 `204`)
- 멤버 여부·역할 확인 없음 (블록이 해당 trip에 없으면 `404`)

### 잠금 자동 해제
잠금은 편집 모달을 닫을 때 해제하는 것이 기본이고, 아래 두 경우에는 서버가 해제한 뒤 `UNLOCK` 이벤트를 브로드캐스트한다.
- **연결 종료**: 잠금자의 해당 trip 마지막 SSE 연결이 끊기면, 그 유저가 그 trip에서 잡고 있던 잠금을 즉시 모두 해제한다
- **만료 (안전망 TTL 3분)**: 잠금마다 만료 시각이 있다. 잠금자의 SSE 연결이 살아 있으면 heartbeat(30초)마다 만료를 지금부터 3분 뒤로 연장한다. 연결 없이 만료가 지난 잠금은 heartbeat 때 해제한다
- 잠금 확인(수정 · 이동 · 삭제 · 잠금 획득)에서는 만료된 잠금을 없는 것으로 취급한다

---

## 4. 초대 (Invite)

### 초대 링크 생성
```
POST /api/trips/{tripId}/invite
```
**응답**
```json
{ "shareToken": "string", "inviteUrl": "string" }
```
- 이미 토큰이 있으면 기존 값을 그대로 사용 (재호출해도 덮어쓰지 않음)
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

## 5. 멤버 (Member)

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
**응답** `200 OK` (본문 없음)
- OWNER만 가능
- `role: OWNER` 요청 시 `400` (OWNER 양도 불가)
- 대상이 OWNER면 `403`, 대상이 멤버가 아니면 `404`

### 멤버 제거
```
DELETE /api/trips/{tripId}/members/{userId}
```
**응답** `204 No Content`
- OWNER만 가능
- 대상이 OWNER면 `403`, 대상이 멤버가 아니면 `404`

---

## 6. 예산 (Budget)

### 예산 항목 목록 조회
```
GET /api/trips/{tripId}/budget
```
**응답**
```json
[
  {
    "id": "uuid",
    "blockId": "uuid | null",
    "category": "HOTEL | FOOD | CAFE | PLACE | TRANSPORT | FLIGHT | ETC",
    "amount": 50000,
    "memo": "string | null"
  }
]
```
- `blockId`가 채워진 항목은 블록 비용 변경에 연동되어 자동 생성/갱신된 항목 (아래 "블록 비용 자동 연동" 참고), `null`이면 이 API로 직접 등록한 수동 항목
- 최소 권한: VIEWER

### 예산 항목 추가
```
POST /api/trips/{tripId}/budget
```
**요청**
```json
{
  "category": "HOTEL | FOOD | CAFE | PLACE | TRANSPORT | FLIGHT | ETC",
  "amount": 50000,
  "memo": "string | null"
}
```
- `amount`는 0 이상

**응답** `201 Created` — BudgetItem 객체 (`blockId`는 항상 `null`)
- 최소 권한: EDITOR

### 예산 항목 수정
```
PATCH /api/trips/{tripId}/budget/{itemId}
```
**요청**: `category`, `amount`, `memo` 부분 수정 가능 (전달된 필드만 반영)

**응답** — BudgetItem 객체
- 최소 권한: EDITOR

### 예산 항목 삭제
```
DELETE /api/trips/{tripId}/budget/{itemId}
```
**응답** `204 No Content`
- 최소 권한: EDITOR

### 블록 비용 자동 연동
- 블록(Block)에 `cost`가 채워지면 서버가 해당 블록에 연동된 예산 항목(`blockId`로 매칭)을 자동으로 생성하거나 `category`/`amount`/`memo`를 갱신한다.
- 블록이 삭제되면 연동된 예산 항목도 함께 삭제된다.
- block/budget 도메인은 서로 직접 호출하지 않고 이벤트로만 연결되며, block 저장 트랜잭션 커밋 후 별도 트랜잭션에서 처리된다(최종적 일관성).

---

## 7. AI 일정 생성

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
- 서버는 생성 결과를 저장하지 않음 (stateless) — 프론트가 들고 있다가 `apply`에 그대로 실어 보냄
- AI 응답이 잘렸거나(`stop_reason`이 `end_turn`이 아님, 예: `max_tokens`) 텍스트가 없거나 JSON으로 해석할 수 없으면 `502 AI_RESPONSE_INVALID`
- Anthropic API가 일시적으로 실패하면(429·5xx·529·시간 초과·연결 실패) `503 AI_UNAVAILABLE`, 그 외 4xx로 거부하면 `502 AI_REQUEST_FAILED`. 서버는 자동 재시도하지 않는다
- 생성에는 수십 초가 걸릴 수 있다 (서버 read timeout 120초)
- 최소 권한: EDITOR

### AI 일정 적용
```
POST /api/trips/{tripId}/ai/apply
```
**요청**
```json
{
  "selectedBlocks": [
    {
      "tempId": "temp-1",
      "blockType": "FOOD",
      "placeName": "string",
      "startTime": "HH:mm | null",
      "durationMin": "number | null",
      "cost": "number | null",
      "memo": "string | null",
      "dayNumber": 1,
      "position": 2.0
    }
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
- `selectedBlocks`의 내용을 서버가 그대로 저장 (재검증 없음)
- `tempId`, `blockType`, `placeName`, `dayNumber`, `position`은 필수 — 누락 시 `400 INVALID_REQUEST`
- `blockType`이 block용(`HOTEL | FOOD | CAFE | PLACE | TRANSPORT`)이 아닌 항목이 하나라도 있으면 아무것도 저장하지 않고 `400 INVALID_REQUEST`
- Google Places API로 `lat/lng` 자동 보강 (실패 시 null 허용)
- 최소 권한: EDITOR

---

## 8. 실시간 이벤트 (SSE)

### 이벤트 구독
```
GET /api/trips/{tripId}/events
Accept: text/event-stream
Authorization: Bearer {accessToken}
```
**응답** `200 OK` `Content-Type: text/event-stream` — 연결을 유지한 채 이벤트를 계속 보낸다
- 헤더 인증이 필요하므로 브라우저 `EventSource` 대신 fetch 기반 SSE 클라이언트를 쓴다 (토큰을 URL 쿼리에 넣지 않는다)
- 인증 실패 `401`, 여행 멤버가 아니면 `403` (VIEWER도 구독 가능). 이 엔드포인트의 에러 응답은 상태 코드만 의미가 있다 (`Accept: text/event-stream`이라 JSON 본문을 보장하지 않음)
- 연결 시간 제한 없음. 서버가 스트림을 닫으면(재시작 등) 클라이언트가 다시 연결한다
- 단일 서버 메모리에 연결을 보관한다 (수평 확장 미지원)

### 이벤트 형식
각 이벤트는 `data:` 한 줄에 JSON으로 온다 (이벤트 이름 없음).
```
data: {"action":"MOVE","tripId":"uuid","payload":{ }}
```
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
| `UNLOCK` | 잠금 해제 (직접 해제 · 연결 종료 · 만료) | `{ blockId }` |
| `CONFLICT` | 블록 수정·이동 시 version 충돌 | `{ blockId, currentBlock }` |
| `PRESENCE` | 접속자 변경 | `{ users: [{ userId, nickname, avatarColor, activeDay }] }` |

- 블록 이벤트(`ADD`~`DELETE`, `LOCK`)는 REST 요청이 성공한 뒤에만 발행된다. `CONFLICT`는 수정·이동 요청이 version 충돌로 실패했을 때 발행된다. `reorder`는 이벤트를 발행하지 않는다.
- `UNLOCK`은 잠금 해제 API 외에, 잠금자의 마지막 연결이 끊기거나 잠금이 만료되어 서버가 해제할 때도 발행된다 (블록 문서의 "잠금 자동 해제" 참고).

### Heartbeat
- 서버는 30초마다 모든 연결에 SSE 주석(`:heartbeat`)을 보낸다. 클라이언트에는 이벤트로 전달되지 않는다
- 전송에 실패한 연결은 끊긴 것으로 보고 정리한다 (아래 연결 종료 처리)

### Presence
- 연결하면 접속자로 자동 등록되고 해당 trip의 전체 접속자 목록이 `PRESENCE`로 브로드캐스트된다
  - `nickname`은 유저 정보, `avatarColor`는 userId로 서버가 계산한다 (같은 유저는 항상 같은 색). 처음 `activeDay`는 `null`
  - 같은 유저가 이미 다른 탭으로 접속 중이면 기존 정보를 유지한다
- 연결이 끊기면(탭 닫기·네트워크 끊김 포함) 그 유저의 해당 trip **마지막 연결**일 때만 목록에서 빠지고 `PRESENCE`가 브로드캐스트된다

### 활성 Day 변경
```
POST /api/trips/{tripId}/presence
```
**요청**
```json
{ "activeDay": 2 }
```
**응답** `204 No Content`
- 접속 중인 유저의 `activeDay`를 바꾸고 `PRESENCE`를 브로드캐스트한다
- 이벤트 구독(연결) 중이 아니면 아무것도 바뀌지 않는다 (`204`). 그래서 클라이언트는 연결이 열린 직후 현재 활성 Day를 한 번 보낸다

### 연결 종료 처리
유저의 해당 trip 마지막 연결이 끊기면:
1. 접속자 목록에서 제거 → `PRESENCE`
2. 그 유저가 그 trip에서 잡고 있던 블록 잠금을 모두 해제 → 블록마다 `UNLOCK`

### 충돌 처리
- 블록마다 `version` 관리
- `version` 불일치 시 REST `409 VERSION_CONFLICT` 응답(body에 `currentBlock` 포함) + `CONFLICT` 이벤트 브로드캐스트
- 클라이언트는 Optimistic Update 후 `409` 또는 `CONFLICT` 수신 시 `currentBlock`(서버 상태)으로 롤백

---

## 9. 에러 코드

| HTTP | code | 상황 |
|------|------|------|
| 400 | `INVALID_REQUEST` | 요청 형식 오류 |
| 401 | `UNAUTHORIZED` | 인증 토큰 없음 / 만료 |
| 403 | `FORBIDDEN` | 권한 없음 (VIEWER가 편집 시도 등) |
| 404 | `NOT_FOUND` | 리소스 없음 |
| 409 | `CONFLICT` | 리소스 중복 (예: 이메일 중복 가입) |
| 409 | `VERSION_CONFLICT` | 블록 version 불일치 |
| 423 | `ALREADY_LOCKED` | 다른 사용자가 블록 잠금 중 |
| 400 | `INVALID_RESET_TOKEN` | 비밀번호 재설정 코드 불일치 또는 만료 (미가입·소셜 계정 포함) |
| 502 | `AI_RESPONSE_INVALID` | AI 응답을 해석할 수 없음 (응답 잘림·텍스트 없음·JSON 형식 오류) |
| 502 | `AI_REQUEST_FAILED` | Anthropic API가 요청을 거부함 (키·모델·요청 문제 등 429를 제외한 4xx). 상세는 서버 로그 |
| 503 | `AI_UNAVAILABLE` | Anthropic API 일시 오류 (429·5xx·529 overloaded·시간 초과·연결 실패). 잠시 후 다시 시도 |
| 500 | `INTERNAL_ERROR` | 서버 오류 |
