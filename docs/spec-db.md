# 여행 플래너 — DB 스키마

## 테이블

### users
```sql
id          UUID PRIMARY KEY
email       VARCHAR(255) UNIQUE NOT NULL
nickname    VARCHAR(100) NOT NULL
provider    VARCHAR(50) NOT NULL   -- GOOGLE | KAKAO
created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
```

### trips
```sql
id           UUID PRIMARY KEY
owner_id     UUID NOT NULL REFERENCES users(id)
title        VARCHAR(255) NOT NULL
destination  VARCHAR(255) NOT NULL
start_date   DATE NOT NULL
end_date     DATE NOT NULL
share_token  VARCHAR(100) UNIQUE
version      BIGINT NOT NULL DEFAULT 0
created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
```

### schedule_blocks
```sql
id            UUID PRIMARY KEY
trip_id       UUID NOT NULL REFERENCES trips(id)
day_number    INT NOT NULL                          -- 1, 2, 3 ...
position      DOUBLE NOT NULL                       -- Fractional Indexing
block_type    ENUM('HOTEL','FOOD','CAFE','PLACE','TRANSPORT') NOT NULL
place_name    VARCHAR(255) NOT NULL
lat           DOUBLE
lng           DOUBLE
start_time    TIME
duration_min  INT
cost          INT
memo          TEXT
version       BIGINT NOT NULL DEFAULT 0
locked_by     UUID REFERENCES users(id)             -- NULL = 잠금 해제 상태
created_by    UUID NOT NULL REFERENCES users(id)
created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
```

### trip_members
```sql
id         UUID PRIMARY KEY
trip_id    UUID NOT NULL REFERENCES trips(id)
user_id    UUID NOT NULL REFERENCES users(id)
role       ENUM('OWNER','EDITOR','VIEWER') NOT NULL
joined_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
UNIQUE (trip_id, user_id)
```

### budget_items
```sql
id        UUID PRIMARY KEY
trip_id   UUID NOT NULL REFERENCES trips(id)
category  ENUM('FLIGHT','HOTEL','FOOD','TRANSPORT','ETC') NOT NULL
amount    INT NOT NULL
memo      VARCHAR(255)
```

---

## position 규칙 (Fractional Indexing)
- 블록 순서를 정수가 아닌 소수로 관리해 이동 시 DB 업데이트를 1건으로 유지
- 두 블록 사이 삽입: `newPosition = (prevPosition + nextPosition) / 2`
- 맨 앞 삽입: `newPosition = prevPosition - 1.0`
- 맨 뒤 삽입: `newPosition = lastPosition + 1.0`
- `position` 값 간격이 `1e-9` 미만으로 좁아지면 `/api/trips/{tripId}/blocks/reorder` 로 전체 재정규화 (1.0, 2.0, 3.0 ...)
