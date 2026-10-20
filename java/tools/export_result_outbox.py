#!/usr/bin/env python3
"""Read-only legacy SQLite -> bounded NDJSON export for the portable Java result spool."""
import argparse
import json
from pathlib import Path
import sqlite3


def export(source, destination):
    source = Path(source).resolve(strict=True)
    destination = Path(destination)
    if destination.exists():
        raise ValueError("export destination already exists")
    # Read-only URI prevents a typo from creating an empty database.
    with sqlite3.connect(source.as_uri() + "?mode=ro", uri=True) as connection:
        rows = connection.execute("SELECT event_id, server_id, payload FROM round_results ORDER BY rowid")
        with destination.open("x", encoding="ascii", newline="\n") as output:
            for identifier, server, payload in rows:
                event = json.loads(payload)
                if event.get("event_id") != identifier or event.get("server_id") != server:
                    raise ValueError("stored result identity mismatch")
                encoded = json.dumps(event, separators=(",", ":"), ensure_ascii=True)
                if len(encoded) > 256 * 1024:
                    raise ValueError("legacy result exceeds event size limit")
                output.write(encoded + "\n")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("sqlite", type=Path)
    parser.add_argument("ndjson", type=Path)
    args = parser.parse_args()
    export(args.sqlite, args.ndjson)
