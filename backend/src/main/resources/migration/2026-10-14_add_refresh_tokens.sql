-- schema.sql은 CREATE TABLE IF NOT EXISTS라 테이블이 이미 존재하는 기존 DB에는 반영되지 않는다.
-- Redis에 두던 refresh token을 DB로 옮기면서 추가된 테이블(#63). 기존 DB에 수동으로 적용할 것.
-- 적용 직후에는 저장된 refresh token이 없으므로 모든 사용자가 한 번 다시 로그인해야 한다.
CREATE TABLE IF NOT EXISTS refresh_tokens
(
    user_id CHAR(36)     NOT NULL PRIMARY KEY,
    token   VARCHAR(512) NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
