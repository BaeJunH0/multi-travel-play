#!/usr/bin/env python3
"""
Read-only MySQL query runner.

Usage:
    python3 query.py "SELECT * FROM users LIMIT 10"
    python3 query.py --format json "SHOW TABLES"
    python3 query.py --env-file /path/to/.env "SELECT COUNT(*) FROM orders"

Reads connection info from environment variables (loaded from a .env file
if present). Two formats are supported:

  1) MYSQL_* style:
       MYSQL_HOST      (default: 127.0.0.1)
       MYSQL_PORT      (default: 3306)
       MYSQL_USER      (required)
       MYSQL_PASSWORD  (required)
       MYSQL_DATABASE  (optional - omit to connect without selecting a DB)
       MYSQL_SSL_CA    (optional - path to CA cert if the server requires TLS)

  2) Spring Boot / JDBC style (auto-detected if MYSQL_* vars are absent):
       DB_USERNAME=root
       DB_PASSWORD=qwerty1234
       DB_URL=jdbc:mysql://localhost:3306/travel?serverTimezone=Asia/Seoul&characterEncoding=UTF-8

     The host/port/database are parsed out of DB_URL automatically.

Only SELECT / SHOW / DESCRIBE / EXPLAIN / WITH (read-only) statements are
allowed. Anything else is rejected before a connection is even made.
"""
import argparse
import csv
import json
import os
import re
import sys
from pathlib import Path
from urllib.parse import urlparse

READ_ONLY_PREFIXES = ("select", "show", "describe", "desc", "explain", "with")

# Statements that must never run through this tool, even if smuggled inside
# a CTE, subquery, or multi-statement string.
FORBIDDEN_KEYWORDS = (
    "insert", "update", "delete", "drop", "alter", "create", "truncate",
    "replace", "grant", "revoke", "call", "lock", "unlock", "set ",
    "commit", "rollback", "into outfile", "into dumpfile", "load_file",
)


def load_env_file(path: Path) -> None:
    """Minimal .env loader (KEY=VALUE per line, no external deps)."""
    if not path.exists():
        return
    for line in path.read_text().splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, _, value = line.partition("=")
        key = key.strip()
        value = value.strip().strip('"').strip("'")
        os.environ.setdefault(key, value)


def validate_read_only(sql: str) -> None:
    stripped = sql.strip().rstrip(";").strip()
    if not stripped:
        raise ValueError("Empty query.")

    # Reject multiple statements stacked with ';' (basic stacked-query guard)
    if ";" in sql.strip().rstrip(";"):
        raise ValueError(
            "Multiple statements detected. Run one read-only statement at a time."
        )

    lowered = stripped.lower()
    if not lowered.startswith(READ_ONLY_PREFIXES):
        raise ValueError(
            f"Only read-only statements are allowed ({', '.join(READ_ONLY_PREFIXES)}). "
            f"Got: {stripped.split()[0]!r}"
        )

    for kw in FORBIDDEN_KEYWORDS:
        if re.search(rf"\b{re.escape(kw.strip())}\b", lowered):
            raise ValueError(f"Query contains a forbidden keyword: {kw.strip()!r}")


def parse_jdbc_mysql_url(jdbc_url: str):
    """
    Parse a Spring-style JDBC MySQL URL into (host, port, database).
    Example:
      jdbc:mysql://localhost:3306/travel?serverTimezone=Asia/Seoul&characterEncoding=UTF-8
    """
    # Strip the leading "jdbc:" so urlparse can handle the rest as a normal URL
    stripped = re.sub(r"^jdbc:", "", jdbc_url.strip())
    parsed = urlparse(stripped)
    if parsed.scheme != "mysql":
        raise ValueError(f"Not a mysql JDBC URL: {jdbc_url!r}")
    host = parsed.hostname or "127.0.0.1"
    port = parsed.port or 3306
    database = parsed.path.lstrip("/") or None
    return host, port, database


def get_connection():
    try:
        import pymysql
    except ImportError:
        sys.exit(
            "pymysql is not installed. Run:\n"
            "    pip install pymysql --break-system-packages"
        )

    if os.environ.get("MYSQL_USER") or os.environ.get("MYSQL_HOST"):
        # Standard MYSQL_* style
        host = os.environ.get("MYSQL_HOST", "127.0.0.1")
        port = int(os.environ.get("MYSQL_PORT", "3306"))
        user = os.environ.get("MYSQL_USER")
        password = os.environ.get("MYSQL_PASSWORD")
        database = os.environ.get("MYSQL_DATABASE") or None
    elif os.environ.get("DB_URL"):
        # Spring Boot / JDBC style: DB_USERNAME, DB_PASSWORD, DB_URL
        try:
            host, port, database = parse_jdbc_mysql_url(os.environ["DB_URL"])
        except ValueError as e:
            sys.exit(str(e))
        user = os.environ.get("DB_USERNAME")
        password = os.environ.get("DB_PASSWORD")
    else:
        host, port, user, password, database = "127.0.0.1", 3306, None, None, None

    ssl_ca = os.environ.get("MYSQL_SSL_CA")

    if not user or not password:
        sys.exit(
            "No usable DB credentials found. Set either MYSQL_USER/MYSQL_PASSWORD "
            "or DB_USERNAME/DB_PASSWORD/DB_URL (via .env or the environment). "
            "See references/env_setup.md."
        )

    ssl_kwargs = {"ssl": {"ca": ssl_ca}} if ssl_ca else {}

    try:
        conn = pymysql.connect(
            host=host,
            port=port,
            user=user,
            password=password,
            database=database,
            connect_timeout=10,
            cursorclass=pymysql.cursors.DictCursor,
            **ssl_kwargs,
        )
    except Exception as e:
        sys.exit(f"Failed to connect to MySQL at {host}:{port} — {e}")

    return conn


def run_query(sql: str, row_limit: int | None):
    validate_read_only(sql)
    conn = get_connection()
    try:
        with conn.cursor() as cur:
            cur.execute(sql)
            rows = cur.fetchmany(row_limit) if row_limit else cur.fetchall()
            truncated = row_limit is not None and cur.rownumber < cur.rowcount if hasattr(cur, "rowcount") else False
            columns = [d[0] for d in cur.description] if cur.description else []
        return columns, rows
    finally:
        conn.close()


def print_table(columns, rows):
    if not rows:
        print("(0 rows)")
        return
    widths = [len(c) for c in columns]
    str_rows = [[str(r[c]) if r[c] is not None else "NULL" for c in columns] for r in rows]
    for row in str_rows:
        for i, val in enumerate(row):
            widths[i] = max(widths[i], len(val))
    header = " | ".join(c.ljust(widths[i]) for i, c in enumerate(columns))
    print(header)
    print("-+-".join("-" * w for w in widths))
    for row in str_rows:
        print(" | ".join(v.ljust(widths[i]) for i, v in enumerate(row)))
    print(f"\n({len(rows)} row{'s' if len(rows) != 1 else ''})")


def print_json(columns, rows):
    print(json.dumps(rows, default=str, ensure_ascii=False, indent=2))


def print_csv(columns, rows):
    writer = csv.DictWriter(sys.stdout, fieldnames=columns)
    writer.writeheader()
    for row in rows:
        writer.writerow(row)


def main():
    parser = argparse.ArgumentParser(description="Run a read-only MySQL query.")
    parser.add_argument("sql", help="SQL statement (SELECT/SHOW/DESCRIBE/EXPLAIN/WITH only)")
    parser.add_argument("--env-file", default=".env", help="Path to .env file (default: ./.env)")
    parser.add_argument("--format", choices=["table", "json", "csv"], default="table")
    parser.add_argument("--limit", type=int, default=200, help="Max rows to fetch (default: 200, 0 = no limit)")
    args = parser.parse_args()

    load_env_file(Path(args.env_file))

    try:
        columns, rows = run_query(args.sql, args.limit if args.limit > 0 else None)
    except ValueError as e:
        sys.exit(f"Rejected: {e}")

    if args.format == "json":
        print_json(columns, rows)
    elif args.format == "csv":
        print_csv(columns, rows)
    else:
        print_table(columns, rows)


if __name__ == "__main__":
    main()
