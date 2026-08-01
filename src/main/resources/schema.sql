use travel;

CREATE TABLE IF NOT EXISTS users
(
    id         CHAR(36)     NOT NULL PRIMARY KEY,
    email      VARCHAR(255) NOT NULL UNIQUE,
    nickname   VARCHAR(100) NOT NULL,
    provider   VARCHAR(50)  NOT NULL,
    password   VARCHAR(255),
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS trips
(
    id          CHAR(36)     NOT NULL PRIMARY KEY,
    owner_id    CHAR(36)     NOT NULL,
    title       VARCHAR(255) NOT NULL,
    destination VARCHAR(255) NOT NULL,
    start_date  DATE         NOT NULL,
    end_date    DATE         NOT NULL,
    share_token VARCHAR(100) UNIQUE,
    version     BIGINT       NOT NULL DEFAULT 0,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (owner_id) REFERENCES users (id)
);

CREATE TABLE IF NOT EXISTS trip_members
(
    id        CHAR(36)  NOT NULL PRIMARY KEY,
    trip_id   CHAR(36)  NOT NULL,
    user_id   CHAR(36)  NOT NULL,
    role      VARCHAR(20) NOT NULL,
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (trip_id, user_id),
    FOREIGN KEY (trip_id) REFERENCES trips (id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE IF NOT EXISTS schedule_blocks
(
    id           CHAR(36)     NOT NULL PRIMARY KEY,
    trip_id      CHAR(36)     NOT NULL,
    day_number   INT          NOT NULL,
    position     DOUBLE       NOT NULL,
    block_type   VARCHAR(20)  NOT NULL,
    place_name   VARCHAR(255) NOT NULL,
    lat          DOUBLE,
    lng          DOUBLE,
    start_time   TIME,
    duration_min INT,
    cost         INT,
    memo         TEXT,
    version      BIGINT       NOT NULL DEFAULT 0,
    locked_by    CHAR(36),
    created_by   CHAR(36)     NOT NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (trip_id) REFERENCES trips (id) ON DELETE CASCADE,
    FOREIGN KEY (locked_by) REFERENCES users (id),
    FOREIGN KEY (created_by) REFERENCES users (id)
);

CREATE TABLE IF NOT EXISTS budget_items
(
    id       CHAR(36)    NOT NULL PRIMARY KEY,
    trip_id  CHAR(36)    NOT NULL,
    category VARCHAR(20) NOT NULL,
    amount   INT         NOT NULL,
    memo     VARCHAR(255),
    FOREIGN KEY (trip_id) REFERENCES trips (id) ON DELETE CASCADE
);
