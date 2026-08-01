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
| `category`| BudgetCategory   | 지출 카테고리                |
| `amount`  | Int              | 금액 (0 이상)                |
| `memo`    | String?          | 메모 (nullable, 최대 255자)  |

**BudgetCategory enum**

```
FLIGHT, HOTEL, FOOD, TRANSPORT, ETC
```

---

## DTO

- **`CreateBudgetRequest`**: `category` (NotNull), `amount` (Min 0), `memo?`
- **`UpdateBudgetRequest`**: 세 필드 모두 nullable — 전달된 필드만 덮어씀
- **`BudgetItemResponse`**: `id`, `category`, `amount`, `memo`  
  — `BudgetItemResponse.of(item)` 정적 팩토리로 변환

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
