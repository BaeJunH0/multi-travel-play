---
name: mysql-readonly-query
description: Use this skill whenever the user wants to connect to a MySQL database, run a SQL query, look at tables/schema, or ask questions about data that lives in MySQL (e.g. "우리 MySQL DB에서 ~ 조회해줘", "이 테이블 스키마 좀 보여줘", "지난달 주문 건수 알려줘" when the data is in MySQL). This skill connects using credentials from a local .env file and runs read-only queries (SELECT/SHOW/DESCRIBE/EXPLAIN/WITH only) — it will refuse INSERT/UPDATE/DELETE/DROP/ALTER and other write statements. Trigger this any time MySQL, a database connection, or SQL querying against a live DB is mentioned, even if the user doesn't say "skill" or give exact table names.
---

# MySQL 읽기 전용 접속 스킬

MySQL 데이터베이스에 접속해서 조회(SELECT 계열) 쿼리만 안전하게 실행하는 스킬입니다.
쓰기/변경 쿼리는 스크립트 단계에서 차단됩니다.

## 처음 사용할 때 (1회성 설정)

1. **드라이버 설치 확인**: `pymysql`이 필요합니다.
   ```bash
   pip install pymysql --break-system-packages
   ```
2. **.env 파일 확인**: 사용자의 프로젝트에 `.env` 파일이 있는지 먼저 확인하세요
   (`ls .env` 또는 경로를 물어보기). 없다면 `references/env_setup.md`를 참고해서
   사용자가 직접 만들도록 안내하세요 — **비밀번호를 채팅창에 입력하도록 요청하지 마세요.**
   사용자가 이미 값을 채팅에 붙여넣었다면, 그 값으로 `.env` 파일을 대신 만들어줘도
   됩니다(로컬 파일에만 저장하고 절대 다른 곳으로 전송하지 않음).

   두 가지 `.env` 형식을 모두 지원합니다 — 이미 서버(Spring Boot 등)에서 쓰던
   `.env`가 있다면 그대로 재사용하면 됩니다:
   - `MYSQL_HOST` / `MYSQL_PORT` / `MYSQL_USER` / `MYSQL_PASSWORD` / `MYSQL_DATABASE`
   - `DB_USERNAME` / `DB_PASSWORD` / `DB_URL` (Spring Boot/JDBC 스타일,
     예: `DB_URL=jdbc:mysql://localhost:3306/travel?serverTimezone=Asia/Seoul&characterEncoding=UTF-8`)
     — host/port/database는 `DB_URL`에서 자동으로 파싱됩니다.
3. **연결 테스트**:
   ```bash
   python3 scripts/test_connection.py --env-file /path/to/.env
   ```
   성공하면 MySQL 버전과 현재 DB가 출력됩니다. 실패하면 에러 메시지를 사용자에게
   그대로 보여주고 (호스트/포트/방화벽/자격증명 문제일 가능성이 큼) 원인을 함께
   추정해주세요.

## 쿼리 실행

`scripts/query.py`를 사용합니다. SQL은 항상 이 스크립트를 통해서만 실행하세요 —
직접 다른 방식으로 MySQL에 접속하지 마세요.

```bash
python3 scripts/query.py "SELECT * FROM users ORDER BY created_at DESC LIMIT 20"
python3 scripts/query.py --format json "SHOW TABLES"
python3 scripts/query.py --format csv "SELECT * FROM orders WHERE status = 'paid'"
python3 scripts/query.py --env-file /path/to/.env "DESCRIBE orders"
```

옵션:
- `--env-file` : `.env` 파일 경로 (기본값: 현재 디렉터리의 `.env`)
- `--format`   : `table`(기본) / `json` / `csv`
- `--limit`    : 최대로 가져올 행 수 (기본값 200, `0`이면 제한 없음 — 대용량 테이블에
  `--limit 0`을 쓸 때는 사용자에게 미리 확인하는 것이 좋습니다)

## 허용되는 쿼리 / 차단되는 쿼리

- **허용**: `SELECT`, `SHOW`, `DESCRIBE`/`DESC`, `EXPLAIN`, `WITH` (CTE 이후 SELECT)
- **차단**: `INSERT`, `UPDATE`, `DELETE`, `DROP`, `ALTER`, `CREATE`, `TRUNCATE`,
  `REPLACE`, `GRANT`, `REVOKE`, `CALL`, `LOCK`/`UNLOCK`, `SET`, `COMMIT`, `ROLLBACK`,
  `INTO OUTFILE`/`DUMPFILE`, `LOAD_FILE`, 그리고 세미콜론으로 이어붙인 다중 쿼리

사용자가 데이터 변경(쓰기) 쿼리를 요청하면, 이 스킬은 조회 전용으로 설계되었다고
설명하고 실행하지 마세요. 정말 쓰기 작업이 필요하다면 별도로 안전장치가 있는
스킬/도구를 만들어야 한다고 안내하세요.

## 일반적인 작업 흐름

1. 사용자의 요청을 이해하고 필요한 SQL을 작성합니다 (테이블/컬럼명이 불확실하면
   먼저 `SHOW TABLES`, `DESCRIBE <table>`로 스키마를 확인하세요).
2. `scripts/query.py`로 실행합니다.
3. 결과를 사용자가 이해하기 쉬운 형태로 요약하거나 표로 정리해서 보여줍니다.
   (원시 JSON/CSV를 그대로 던지지 말고, 필요한 경우 표나 자연어 요약을 덧붙이세요.)
4. 결과가 너무 많으면(`--limit`에 걸린 경우) 그 사실을 알려주고, 더 볼지 물어보세요.

## 문제 해결

- **`pymysql is not installed`** → `pip install pymysql --break-system-packages` 실행
- **연결 실패 (timeout / refused)** → 호스트/포트가 맞는지, 방화벽·VPN·보안그룹에서
  이 환경의 IP를 허용했는지 확인하도록 안내
- **Access denied** → `MYSQL_USER`/`MYSQL_PASSWORD` 재확인, 필요하면
  `references/env_setup.md`의 읽기 전용 계정 생성 SQL 안내
- **SSL/TLS 필요 에러** → `.env`에 `MYSQL_SSL_CA` 경로 추가 안내 (`references/env_setup.md` 참고)

더 자세한 `.env` 설정 방법과 읽기 전용 계정 생성 방법은 `references/env_setup.md`를
참고하세요.
