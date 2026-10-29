#!/usr/bin/env python3
"""Builds feed.json (what the app shows) from the repo's GitHub Releases.

index.json is the feed of app versions before 7; it now lists no days and only points
those old installs to the new APK.

Each daily batch is a release tagged YYYY-MM-DD whose assets are the photos (.png/.jpg)
and reels (.mp4). A video's poster frame is an asset named <video-stem>.thumb.jpg.
The release tagged "app" holds the latest APK; its notes carry "versionCode: N".

Usage: GITHUB_TOKEN=... python3 tools/make_index.py owner/repo   (writes feed.json and index.json)
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


ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")


def settings():
    """app_settings.json: "show_from" hides older batches (releases are kept),
    "min_version" makes older app versions ask for an update."""
    try:
        with open(os.path.join(ROOT, "app_settings.json")) as f:
            return json.load(f)
    except FileNotFoundError:
        return {}


def write(name, data):
    with open(os.path.join(ROOT, name), "w") as f:
        json.dump(data, f, ensure_ascii=False, indent=1)
        f.write("\n")


def main(repo):
    days, app = [], None
    cfg = settings()
    start = cfg.get("show_from", "")
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
        if not DATE_TAG.match(tag) or tag < start:
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
    updated = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    feed = {"updated": updated, "min_version": cfg.get("min_version", 0), "days": days}
    if app:
        feed["app"] = app
    write("feed.json", feed)
    legacy = {"updated": updated, "days": []}
    if app:
        legacy["app"] = {"versionCode": 999999, "url": app["url"]}
    write("index.json", legacy)


if __name__ == "__main__":
    main(sys.argv[1])
