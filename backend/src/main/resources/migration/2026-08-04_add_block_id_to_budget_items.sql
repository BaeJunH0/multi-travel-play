-- schema.sql은 CREATE TABLE IF NOT EXISTS라 테이블이 이미 존재하는 기존 DB에는 반영되지 않는다.
-- budget_items.block_id 컬럼(#22)이 누락된 기존 DB에 수동으로 적용할 것. (#24)
ALTER TABLE budget_items
    ADD COLUMN block_id CHAR(36) UNIQUE AFTER trip_id,
    ADD FOREIGN KEY (block_id) REFERENCES schedule_blocks (id) ON DELETE CASCADE;
