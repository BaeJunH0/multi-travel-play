> 공통 사항(Base URL, 에러 응답 형식 등)은 [README.md](./README.md) 참고

# 예산 (Budget)

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
**응답** `201 Created` — BudgetItem 객체 (`blockId`는 항상 `null`)
- 최소 권한: EDITOR

### 예산 항목 수정
```
PATCH /api/trips/{tripId}/budget/{itemId}
```
**요청**: `category`, `amount`, `memo` 부분 수정 가능
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
