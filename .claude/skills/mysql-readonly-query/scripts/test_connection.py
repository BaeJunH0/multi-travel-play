#!/usr/bin/env python3
"""
Quick connectivity check.

Usage:
    python3 test_connection.py [--env-file .env]

Prints the MySQL server version and current database on success, or a
clear error message on failure.
"""
import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from query import get_connection, load_env_file  # noqa: E402


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--env-file", default=".env")
    args = parser.parse_args()

    load_env_file(Path(args.env_file))
    conn = get_connection()
    try:
        with conn.cursor() as cur:
            cur.execute("SELECT VERSION() AS version, DATABASE() AS db")
            row = cur.fetchone()
        print("Connected successfully.")
        print(f"  MySQL version : {row['version']}")
        print(f"  Current DB    : {row['db'] or '(none selected)'}")
    finally:
        conn.close()


if __name__ == "__main__":
    main()
