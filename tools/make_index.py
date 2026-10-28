#!/usr/bin/env python3
"""Builds index.json (what the app shows) from the repo's GitHub Releases.

Each daily batch is a release tagged YYYY-MM-DD whose assets are the photos (.png/.jpg)
and reels (.mp4). A video's poster frame is an asset named <video-stem>.thumb.jpg.
The release tagged "app" holds the latest APK; its notes carry "versionCode: N".

Usage: GITHUB_TOKEN=... python3 tools/make_index.py owner/repo > index.json
"""
import json
import os
import re
import sys
import urllib.request
from datetime import datetime, timezone

PHOTO_EXT = (".png", ".jpg", ".jpeg", ".webp")
DATE_TAG = re.compile(r"^\d{4}-\d{2}-\d{2}$")


def api(path):
    req = urllib.request.Request("https://api.github.com" + path)
    req.add_header("Accept", "application/vnd.github+json")
    token = os.environ.get("GITHUB_TOKEN") or os.environ.get("GH_TOKEN")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    with urllib.request.urlopen(req) as r:
        return json.load(r)


def releases(repo):
    page = 1
    while True:
        batch = api(f"/repos/{repo}/releases?per_page=100&page={page}")
        if not batch:
            return
        yield from batch
        page += 1


def title_for(name):
    stem = name.rsplit(".", 1)[0]
    stem = re.sub(r"^(photo|video|reel)\d*[-_]", "", stem)
    words = re.split(r"[-_]+", stem)
    return " ".join(words).strip().capitalize() or name


def main(repo):
    days, app = [], None
    for rel in releases(repo):
        if rel.get("draft"):
            continue
        tag = rel["tag_name"]
        assets = {a["name"]: a["browser_download_url"] for a in rel["assets"]}
        if tag == "app":
            apk = next((u for n, u in assets.items() if n.endswith(".apk")), None)
            m = re.search(r"versionCode:\s*(\d+)", rel.get("body") or "")
            if apk and m:
                app = {"versionCode": int(m.group(1)), "url": apk}
            continue
        if not DATE_TAG.match(tag):
            continue
        photos, videos = [], []
        for name in sorted(assets):
            low = name.lower()
            if low.endswith(".thumb.jpg"):
                continue
            if low.endswith(".mp4"):
                thumb = assets.get(name.rsplit(".", 1)[0] + ".thumb.jpg", "")
                videos.append({"type": "video", "file": name, "title": title_for(name),
                               "url": assets[name], "thumb": thumb})
            elif low.endswith(PHOTO_EXT):
                photos.append({"type": "photo", "file": name, "title": title_for(name),
                               "url": assets[name], "thumb": assets[name]})
        if photos or videos:
            days.append({"date": tag, "items": photos + videos})
    days.sort(key=lambda d: d["date"], reverse=True)
    out = {"updated": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"), "days": days}
    if app:
        out["app"] = app
    json.dump(out, sys.stdout, ensure_ascii=False, indent=1)
    sys.stdout.write("\n")


if __name__ == "__main__":
    main(sys.argv[1])
