# .env 설정 가이드

이 스킬은 MySQL 접속 정보를 `.env` 파일(환경변수)에서 읽습니다. 비밀번호 등 민감정보는
대화창에 직접 입력하지 말고, 사용자가 로컬에 `.env` 파일을 만들도록 안내하세요.

## .env 파일 형식

프로젝트 루트(또는 명령 실행 위치)에 `.env` 파일을 만들고 아래 내용을 채웁니다:

```
MYSQL_HOST=127.0.0.1
MYSQL_PORT=3306
MYSQL_USER=your_username
MYSQL_PASSWORD=your_password
MYSQL_DATABASE=your_database
# 서버가 TLS를 요구하는 경우에만:
# MYSQL_SSL_CA=/path/to/ca-cert.pem
```

- `MYSQL_HOST`, `MYSQL_PORT`는 생략 시 각각 `127.0.0.1`, `3306`이 기본값으로 사용됩니다.
- `MYSQL_USER`, `MYSQL_PASSWORD`는 필수입니다.
- `MYSQL_DATABASE`는 생략 가능합니다 (생략하면 DB를 선택하지 않은 채로 접속되며,
  쿼리에서 `db명.테이블명` 형식으로 명시해야 합니다).

## 대안: Spring Boot / JDBC 스타일 .env (이미 있는 파일 재사용)

서버 프로젝트에서 이미 아래와 같은 `.env`를 쓰고 있다면 그대로 사용해도 됩니다
(`MYSQL_*` 변수가 없으면 이 형식으로 자동 인식합니다):

```
DB_USERNAME=root
DB_PASSWORD=qwerty1234
DB_URL=jdbc:mysql://localhost:3306/travel?serverTimezone=Asia/Seoul&characterEncoding=UTF-8
```

`DB_URL`에서 host, port, database가 자동으로 파싱됩니다
(`serverTimezone`, `characterEncoding` 같은 쿼리 파라미터는 무시됩니다).
두 형식(`MYSQL_*` vs `DB_URL`)을 같은 `.env`에 동시에 넣으면 `MYSQL_*` 쪽이
우선 적용됩니다.

⚠️ 다만 이 방식은 보통 `root` 계정 정보를 그대로 재사용하게 되는데, 이 스킬은
읽기 전용이지만 계정 자체는 모든 권한을 가진 `root`입니다. 가능하면 아래처럼
조회 전용 계정을 별도로 만들고 그 계정 정보로 `DB_USERNAME`/`DB_PASSWORD`를
바꿔서 쓰는 것을 권장합니다.

## 다른 위치의 .env 파일 사용

기본 경로가 아닌 다른 `.env` 파일을 쓰려면 `--env-file` 옵션을 사용합니다:

```
python3 scripts/query.py --env-file /path/to/project/.env "SHOW TABLES"
```

## 보안 유의사항

- `.env` 파일은 절대 대화(채팅) 내용에 붙여넣지 않도록 안내하세요. 값은 파일 안에만
  두고, Claude는 그 파일의 *경로*만 알면 됩니다.
- `.env`는 git에 커밋하지 않도록 `.gitignore`에 추가하는 것을 권장하세요.
- 이 스킬의 쿼리 실행 스크립트(`scripts/query.py`)는 SELECT/SHOW/DESCRIBE/EXPLAIN/WITH
  구문만 허용하고, INSERT/UPDATE/DELETE/DROP 등은 실행 전에 거부합니다. 하지만 이는
  최소한의 안전장치이며, 근본적으로는 스킬에서 사용하는 MySQL 계정 자체를
  읽기 전용 권한(SELECT만 GRANT)으로 만들어 두는 것이 가장 안전합니다.

## 읽기 전용 계정 만들기 (권장)

DB 관리자 권한이 있다면, 아래처럼 조회 전용 계정을 별도로 만들어 이 스킬 전용으로
쓰는 것을 권장합니다:

```sql
CREATE USER 'claude_readonly'@'%' IDENTIFIED BY 'strong-password-here';
GRANT SELECT ON your_database.* TO 'claude_readonly'@'%';
FLUSH PRIVILEGES;
```
