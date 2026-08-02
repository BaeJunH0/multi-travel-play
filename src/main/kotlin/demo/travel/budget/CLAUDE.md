# budget 패키지

여행(Trip)에 속한 예산 항목(BudgetItem)을 생성·조회·수정·삭제하는 패키지.

---

## API 엔드포인트

Base path: `/api/trips/{tripId}/budget`  
모든 엔드포인트는 `Authorization: Bearer {accessToken}` 필요.

| Method   | Path          | 설명            | 최소 권한 |
|----------|---------------|-----------------|-----------|
| `GET`    | `/`           | 예산 항목 목록 조회 | VIEWER    |
| `POST`   | `/`           | 예산 항목 추가    | EDITOR    |
| `PATCH`  | `/{itemId}`   | 예산 항목 부분 수정 | EDITOR    |
| `DELETE` | `/{itemId}`   | 예산 항목 삭제    | EDITOR    |

---

## BudgetItem 엔티티

테이블명: `budget_items`

| 필드       | 타입             | 설명                        |
|-----------|------------------|-----------------------------|
| `id`      | UUID             | PK (자동 생성)               |
| `trip`    | Trip (ManyToOne) | 소속 여행 (LAZY fetch)       |
| `blockId` | UUID?            | 연동된 block의 id. null이면 수동 입력 항목 |
| `category`| TripCategory     | 지출 카테고리 (block과 공용 enum) |
| `amount`  | Int              | 금액 (0 이상)                |
| `memo`    | String?          | 메모 (nullable, 최대 255자)  |

**TripCategory enum** (`demo.travel.common`, block과 공용)

```
HOTEL, FOOD, CAFE, PLACE, TRANSPORT, FLIGHT, ETC
```

---

## DTO

- **`BudgetRequest.Create`**: `category` (NotNull), `amount` (Min 0), `memo?`
- **`BudgetRequest.Update`**: 세 필드 모두 nullable — 전달된 필드만 덮어씀
- **`BudgetResult`**: `id`, `blockId`, `category`, `amount`, `memo` — `BudgetResult.of(item)` 정적 팩토리로 엔티티 변환
- **`BudgetResponse`**: `id`, `blockId`, `category`, `amount`, `memo` — `BudgetResponse.of(result)` 정적 팩토리로 변환

---

## 핵심 비즈니스 로직

### 권한 검증

`BudgetService`는 `TripMemberRepository`로 호출자의 멤버십을 직접 확인한다.

- **`requireMember`** — `trip_members` 행이 없으면 `403 FORBIDDEN`  
  → `getItems`에서 사용 (VIEWER 포함 전체 멤버 허용)

- **`requireEditorOrAbove`** — 멤버가 없거나 `TripRole.VIEWER`이면 `403 FORBIDDEN`  
  → `create`, `update`, `delete`에서 사용

### 항목 소속 검증

`update` / `delete` 시 `item.trip.id != tripId` 이면 `404 NOT_FOUND`를 던져 다른 Trip의 항목에 접근하는 것을 차단한다.

### Partial Update

`UpdateBudgetRequest`의 각 필드는 nullable이며, non-null인 필드만 `let` 블록으로 엔티티에 반영한다. 별도의 save 호출 없이 `@Transactional` 범위 내 dirty checking으로 반영된다.

### block 연동 (blockId)

block에 cost가 채워지면 자동으로 연동 `BudgetItem`이 생성/갱신/삭제된다. block/budget 도메인은 서로 직접 참조하지 않고
Spring 이벤트로만 연결된다 — block 쪽 설계는 [block/CLAUDE.md](../block/CLAUDE.md)의 "budget 동기화" 참고.

- **`BlockBudgetSyncListener`**(`demo.travel.budget.application`)가 `BlockCostChangedEvent`/`BlockDeletedEvent`를 `@TransactionalEventListener(AFTER_COMMIT)`로 구독한다.
- `blockId`로 기존 `BudgetItem`을 찾아 있으면 category/amount/memo를 갱신하고, 없으면 새로 생성한다 (`memo`는 block의 `placeName`).
- block이 삭제되면 연동된 `BudgetItem`도 함께 삭제된다.
- `blockId`가 null인 항목(수동 입력)은 이 동기화 대상이 아니며, 기존 `BudgetController` API로 자유롭게 생성/수정/삭제할 수 있다.
- block 트랜잭션 커밋 후 별도 트랜잭션(`REQUIRES_NEW`)에서 처리되므로 block 저장과 budget 반영은 최종적 일관성(eventual consistency)을 가진다 — budget 쪽 실패가 block 저장에 영향을 주지 않는다.
