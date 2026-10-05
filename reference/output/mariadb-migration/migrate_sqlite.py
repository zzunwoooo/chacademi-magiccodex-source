"""Back up the old SQLite stores and optionally import them into an empty MariaDB.

Run this only after the old server has stopped. Player PDC values (circle/mana) are
imported by the new bridge on each player's first login, not from these SQLite files.
"""
from __future__ import annotations

import argparse
import datetime as dt
import json
import sqlite3
import subprocess
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
CLIENT = HERE / "runtime" / "mariadb-11.8.9-winx64" / "bin" / "mariadb.exe"
CONFIG = HERE / "private" / "app.cnf"
FILES = {
    "friends": "MagicCodexBridge/friends.db",
    "school": "MagicCodexBridge/school.db",
    "discovery": "MagicDiscovery/discoveries.db",
}


def sql_text(value: object) -> str:
    if value is None:
        return "NULL"
    return "CONVERT(0x" + str(value).encode("utf-8").hex() + " USING utf8mb4)"


def statement(table: str, columns: str, row: tuple[object, ...], text_columns: set[int]) -> str:
    values = [sql_text(v) if i in text_columns else str(v) for i, v in enumerate(row)]
    return f"INSERT INTO {table} ({columns}) VALUES ({','.join(values)});"


def snapshot(source: Path, destination: Path) -> sqlite3.Connection:
    destination.parent.mkdir(parents=True, exist_ok=True)
    with sqlite3.connect(f"file:{source.as_posix()}?mode=ro", uri=True) as src:
        with sqlite3.connect(destination) as dst:
            src.backup(dst)
    return sqlite3.connect(destination)


def rows(db: sqlite3.Connection | None, sql: str) -> list[tuple[object, ...]]:
    return list(db.execute(sql)) if db is not None else []


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--plugins", type=Path, default=ROOT / "plugins")
    parser.add_argument("--config", type=Path, default=CONFIG, help="MariaDB client config")
    parser.add_argument("--client", type=Path, default=CLIENT, help="mariadb.exe path")
    parser.add_argument("--apply", action="store_true", help="Import into an empty database")
    args = parser.parse_args()
    stamp = dt.datetime.now().strftime("%Y%m%d-%H%M%S")
    backup_dir = HERE / "backups" / stamp
    dbs: dict[str, sqlite3.Connection | None] = {}
    for key, name in FILES.items():
        source = args.plugins / name
        dbs[key] = snapshot(source, backup_dir / name) if source.is_file() else None
    generated = ["SET NAMES utf8mb4;", "START TRANSACTION;"]
    counts: dict[str, int] = {}

    mappings = [
        ("friends", "SELECT owner,target,name,dorm FROM friends ORDER BY rowid", "codex_friends", "owner,target,name,dorm", {0, 1, 2, 3}),
        ("friends", "SELECT owner,delivered FROM first_friend_signal", "codex_first_friend_signal", "owner,delivered", {0}),
        ("school", "SELECT house,points FROM house_points", "codex_house_points", "house,points", set()),
        ("school", "SELECT spell,spell_name,donor,nickname,house,created FROM donations ORDER BY rowid", "codex_donations", "spell,spell_name,donor,nickname,house,created", {0, 1, 2, 3}),
        ("school", "SELECT player,house FROM houses", "codex_houses", "player,house", {0}),
        ("discovery", "SELECT player,key,value FROM progress", "codex_discovery_progress", "player,progress_key,value", {0, 1}),
        ("discovery", "SELECT token,player,spell,first,reward,notified FROM acquisitions ORDER BY token", "codex_discovery_acquisitions", "token,player,spell,first_discovery,reward,notified", {1, 2}),
        ("discovery", "SELECT spell,player,created FROM firsts", "codex_discovery_firsts", "spell,player,created", {0, 1}),
    ]
    for source, query, table, columns, text_columns in mappings:
        values = rows(dbs[source], query)
        counts[table] = len(values)
        if table == "codex_house_points":
            for house, points in values:
                generated.append(f"UPDATE codex_house_points SET points={int(points)} WHERE house={int(house)};")
        else:
            generated.extend(statement(table, columns, row, text_columns) for row in values)
    generated.append("COMMIT;")
    sql_file = backup_dir / "import.sql"
    sql_file.write_text("\n".join(generated) + "\n", encoding="utf-8")
    for db in dbs.values():
        if db is not None:
            db.close()
    print(json.dumps({"backup": str(backup_dir), "rows": counts, "applied": False}, ensure_ascii=False))
    if not args.apply:
        return
    if not args.client.is_file() or not args.config.is_file():
        raise SystemExit("MariaDB client or app.cnf is missing")
    command = [str(args.client), f"--defaults-extra-file={args.config}", "--protocol=tcp", "--skip-ssl", "--default-character-set=utf8mb4", "--batch", "--skip-column-names"]
    check = "SELECT COUNT(*) FROM (SELECT owner FROM codex_friends UNION ALL SELECT spell FROM codex_donations UNION ALL SELECT player FROM codex_discovery_acquisitions UNION ALL SELECT player FROM codex_player_state) AS existing;"
    existing = subprocess.run(command + ["-e", check], capture_output=True, text=True, check=True)
    if int(existing.stdout.strip()) != 0:
        raise SystemExit("Target DB contains live data. Import cancelled to avoid overwriting it.")
    with sql_file.open("rb") as script:
        subprocess.run(command, stdin=script, check=True)
    print(json.dumps({"applied": True, "rows": counts}, ensure_ascii=False))


if __name__ == "__main__":
    main()
