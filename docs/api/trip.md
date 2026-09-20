> 공통 사항(Base URL, 에러 응답 형식 등)은 [README.md](./README.md) 참고

# 여행 (Trip)

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
