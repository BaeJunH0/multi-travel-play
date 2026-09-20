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

## 블록 잠금 (Lock)

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
