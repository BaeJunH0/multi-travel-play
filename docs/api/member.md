> 공통 사항(Base URL, 에러 응답 형식 등)은 [README.md](./README.md) 참고

# 멤버 (Member)

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
