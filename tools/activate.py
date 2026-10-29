#!/usr/bin/env python3
"""Unlocks a phone's app for N days (default: the plan period) in subscription.json.

  python3 tools/activate.py ABCD-2345            # one period, extends any time left
  python3 tools/activate.py ABCD-2345 365
  python3 tools/activate.py ABCD-2345 forever    # owner's phone

Then commit and push subscription.json; the app picks it up on its next refresh.
"""
import json
import re
import sys
from datetime import date, datetime, timedelta, timezone
from pathlib import Path

path = Path(__file__).resolve().parent.parent / "subscription.json"
cfg = json.loads(path.read_text())
code = re.sub(r"[^A-Z0-9]", "", sys.argv[1].upper())
if len(code) != 8:
    sys.exit("app code must be 8 characters, like ABCD-2345")
members = cfg.setdefault("members", {})
arg = sys.argv[2] if len(sys.argv) > 2 else str(cfg.get("period_days", 30))
today = datetime.now(timezone(timedelta(hours=5, minutes=30))).date()
if arg == "forever":
    until = date(2099, 12, 31)
else:
    start = today
    if code in members:
        current = date.fromisoformat(members[code])
        if current >= today:
            start = current + timedelta(days=1)
    until = start + timedelta(days=int(arg) - 1)
members[code] = until.isoformat()
path.write_text(json.dumps(cfg, indent=1, ensure_ascii=False) + "\n")
print(f"{code[:4]}-{code[4:]} active until {until}")
