#!/usr/bin/env bash
# Publishes one day's batch folder so it shows up in the app.
#   tools/publish_day.sh /mnt/project-files/daily-ads/2026-10-28
# Pushes the files to branch upload/<day>; the "Publish daily batch" workflow then makes the
# GitHub Release <day> (originals, full HD), deletes the branch and refreshes index.json.
# Re-running for the same day replaces changed files.
set -euo pipefail
dir="${1%/}"
day="$(basename "$dir")"
[[ "$day" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]] || { echo "folder name must be YYYY-MM-DD" >&2; exit 1; }
remote="$(git -C "$(dirname "$0")/.." remote get-url origin)"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
mkdir "$work/$day"
shopt -s nullglob
for f in "$dir"/*.png "$dir"/*.jpg "$dir"/*.jpeg "$dir"/*.webp "$dir"/*.mp4; do
  cp "$f" "$work/$day/"
done
[ -n "$(ls "$work/$day")" ] || { echo "no media in $dir" >&2; exit 1; }
git -C "$work" init -q -b "upload/$day"
git -C "$work" add -A
git -C "$work" -c user.name=spandan-bot -c user.email=spandan-bot@users.noreply.github.com \
  commit -qm "Batch $day"
git -C "$work" push -q --force "$remote" "upload/$day"
echo "pushed upload/$day ($(ls "$work/$day" | wc -l) files); the workflow publishes it in ~1 min"
