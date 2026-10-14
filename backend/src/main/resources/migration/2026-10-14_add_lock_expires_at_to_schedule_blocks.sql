-- schema.sql은 CREATE TABLE IF NOT EXISTS라 테이블이 이미 존재하는 기존 DB에는 반영되지 않는다.
-- 블록 잠금에 만료 시각(안전망 TTL)을 두면서 추가된 컬럼(#67). 기존 DB에 수동으로 적용할 것.
-- 만료 시각이 없는 잠금은 만료된 것으로 취급되므로, 남아 있던 잠금(연결이 끊겨 무기한 남은 잠금 포함)은 함께 해제한다.
ALTER TABLE schedule_blocks
    ADD COLUMN lock_expires_at TIMESTAMP NULL AFTER locked_by;

UPDATE schedule_blocks
SET locked_by = NULL
WHERE locked_by IS NOT NULL;
