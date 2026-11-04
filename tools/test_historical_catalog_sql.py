"""Host SQLite checks for the production archive schema and historical day query.

Run with: python tools/test_historical_catalog_sql.py
Uses a disposable in-memory archive; never opens an installed app database.
Android SQLite/WAL integration and handset timings still need device validation.
"""

import datetime as dt
import json
from pathlib import Path
import re
import sqlite3


ROOT = Path(__file__).resolve().parents[1]
SOURCE = (ROOT / "app/src/main/java/cz/misa/quakedeck/data/ReportArchiveStore.kt").read_text(
    encoding="utf-8"
)


def kotlin_strings(expression):
    return "".join(json.loads(s) for s in re.findall(r'"(?:[^"\\]|\\.)*"', expression))


def check_archive():
    create = re.search(r'"""\s*(CREATE TABLE reports \(.*?)"""', SOURCE, re.S).group(1)
    migration = re.search(r'db.execSQL\("(ALTER TABLE reports ADD COLUMN catalog_json TEXT)"\)', SOURCE).group(1)
    index = kotlin_strings(re.search(
        r'db.execSQL\(("CREATE INDEX reports_catalog_pending_idx.*?)\)\s*\}', SOURCE, re.S
    ).group(1))
    query_block = SOURCE.split("// Ranges use reports_code_time_idx", 1)[1].split("args =", 1)[0]
    day_selection = kotlin_strings(query_block.split("selection =", 1)[1])
    assert "raw_json" not in day_selection

    db = sqlite3.connect(":memory:")
    # Recreate the prior v2 schema from the production CREATE TABLE, then apply
    # the actual additive migration. No original column is rewritten.
    db.execute(create.replace("catalog_json TEXT,", ""))
    db.execute("CREATE INDEX reports_code_time_idx ON reports(code, source_time, received_at)")
    expected = []
    boundaries = (dt.date(2026, 10, 31), dt.date(2026, 11, 1), dt.date(2026, 12, 31),
                  dt.date(2027, 1, 1), dt.date(2028, 2, 28), dt.date(2028, 2, 29), dt.date(2028, 3, 1))
    for number in range(1_000):
        code = (551, 552, 554, 556, 9611)[number % 5]
        date = dt.date(2026, 10, 1) + dt.timedelta(days=number % 200)
        if number < 70:
            date = boundaries[(number // 2) % len(boundaries)]
        stamp = f"{date} 12:38:00"
        if number % 2:
            stamp = stamp.replace("-", "/")
        raw = json.dumps({"code": code, "points": [{"name": "original station"}],
                          "time": stamp, "earthquake": {"time": stamp}}, ensure_ascii=False)
        values = (f"key-{number}", f"semantic-{number}", str(number), code, stamp,
                  number, "DetailScale", f"quake:{stamp}", "fixture", len(raw.encode()), raw)
        db.execute("INSERT INTO reports VALUES (?,?,?,?,?,?,?,?,?,?,?)", values)
        expected.append(values)
    db.commit()
    db.execute(migration)
    db.execute(index)
    pending = "catalog_json IS NULL AND code IN (551, 556)"
    assert db.execute(f"SELECT COUNT(*) FROM reports WHERE {pending}").fetchone()[0] == 400
    plan = db.execute(f"EXPLAIN QUERY PLAN SELECT archive_key FROM reports WHERE {pending} LIMIT 64").fetchall()
    assert any("reports_catalog_pending_idx" in row[-1] for row in plan), plan

    def convert_batch():
        batch = db.execute(f"SELECT archive_key, raw_json FROM reports WHERE {pending} LIMIT 64").fetchall()
        with db:
            for key, raw in batch:
                original = json.loads(raw)
                compact = json.dumps({k: v for k, v in original.items() if k != "points"})
                db.execute("UPDATE reports SET catalog_json=? WHERE archive_key=? AND catalog_json IS NULL",
                           (compact, key))
        return len(batch)

    # An interrupted first-open conversion resumes without rewriting completed rows.
    assert convert_batch() == 64
    assert db.execute(f"SELECT COUNT(*) FROM reports WHERE {pending}").fetchone()[0] == 336
    while convert_batch():
        pass
    assert db.execute("SELECT COUNT(*) FROM reports WHERE code IN (551,556) AND catalog_json IS NOT NULL").fetchone()[0] == 400
    actual = db.execute("SELECT archive_key,semantic_key,upstream_id,code,source_time,received_at,"
                        "issue_type,event_key,source,payload_bytes,raw_json FROM reports ORDER BY rowid").fetchall()
    assert actual == expected, "Migration/conversion changed original archive data"
    # Existing semantic deduplication remains enforced after conversion.
    db.execute("INSERT OR IGNORE INTO reports (archive_key,semantic_key,upstream_id,code,source_time,"
               "received_at,issue_type,event_key,source,payload_bytes,raw_json) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
               ("new-wrapper", *expected[0][1:]))
    assert db.execute("SELECT COUNT(*) FROM reports").fetchone()[0] == 1_000

    old = "code IN (552,554,556,9611) AND (source_time LIKE ? OR source_time LIKE ?)"
    for date in (dt.date(2026, 10, 16), dt.date(2026, 10, 31), dt.date(2026, 12, 31), dt.date(2028, 2, 29)):
        dashed = str(date)
        slashed = dashed.replace("-", "/")
        prefix_end = dashed[:-1] + chr(ord(dashed[-1]) + 1)
        args = (dashed, prefix_end, slashed, prefix_end.replace("-", "/"))
        before = db.execute("SELECT archive_key FROM reports WHERE " + old + " ORDER BY archive_key",
                            (dashed + "%", slashed + "%")).fetchall()
        after = db.execute("SELECT archive_key FROM reports WHERE " + day_selection + " ORDER BY archive_key", args).fetchall()
        assert before == after, date
        plan = db.execute("EXPLAIN QUERY PLAN SELECT * FROM reports WHERE " + day_selection, args).fetchall()
        ranges = [row[-1] for row in plan if "reports_code_time_idx" in row[-1]]
        assert len(ranges) == 2 and all("source_time>? AND source_time<?" in row for row in ranges), plan
    db.execute("DELETE FROM reports")
    assert db.execute(f"SELECT COUNT(*) FROM reports WHERE {pending}").fetchone()[0] == 0
    # A fresh v3 archive exposes the projection column immediately as well.
    fresh = sqlite3.connect(":memory:")
    fresh.execute(create)
    fresh.execute(index)
    assert "catalog_json" in {row[1] for row in fresh.execute("PRAGMA table_info(reports)")}
    fresh.close()
    db.close()
    print("PASS: v2-to-v3 migration, resumable batches, 1,000 unchanged original rows, semantic deduplication,")
    print("      fresh schema, clear, both timestamp formats, date rollovers and indexed day-range query plans.")


if __name__ == "__main__":
    check_archive()
