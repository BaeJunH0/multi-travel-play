> 공통 사항(Base URL, 에러 응답 형식 등)은 [README.md](./README.md) 참고

# 블록 (Block)

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
**응답** `200 OK` — position이 바뀐 Block 객체 배열 (바뀐 블록이 없으면 `[]`)
- 같은 day의 인접 `position` 간격이 `1e-4` 미만으로 좁아졌을 때 호출
- 각 day 내 블록을 `1.0, 2.0, 3.0 ...` 으로 재정규화한다. position이 바뀐 블록은 `version`도 오른다
- 응답 블록으로 화면을 교체해야 다음 수정·이동이 `409 VERSION_CONFLICT`로 실패하지 않는다
- position이 바뀐 블록마다 `MOVE` SSE 이벤트를 발행한다
- 최소 권한: EDITOR

---

## 블록 잠금 (Lock)

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
