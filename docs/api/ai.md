> 공통 사항(Base URL, 에러 응답 형식 등)은 [README.md](./README.md) 참고

# AI 일정 생성

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
- Google Places API로 `lat/lng` 자동 보강 (실패 시 null 허용)
- 최소 권한: EDITOR
