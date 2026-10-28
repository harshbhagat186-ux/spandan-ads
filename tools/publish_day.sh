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
repo="$(cd "$(dirname "$0")/.." && pwd)"

# The branch must sit on top of main so it carries the publish workflow.
git -C "$repo" fetch -q origin main
work="$(mktemp -d)/wt"
git -C "$repo" worktree add -q --detach "$work" origin/main
trap 'git -C "$repo" worktree remove --force "$work"' EXIT
mkdir -p "$work/$day"
shopt -s nullglob
for f in "$dir"/*.png "$dir"/*.jpg "$dir"/*.jpeg "$dir"/*.webp "$dir"/*.mp4; do
  case "$f" in *.thumb.jpg) continue ;; esac
  cp "$f" "$work/$day/"
  if [[ "$f" == *.mp4 ]]; then
    # Poster frame for the app's grid, taken 1.5s in (past the logo intro).
    ffmpeg -loglevel error -y -ss 1.5 -i "$f" -frames:v 1 -vf scale=540:-2 -q:v 4 \
      "$work/$day/$(basename "${f%.mp4}").thumb.jpg"
  fi
done
[ -n "$(ls "$work/$day")" ] || { echo "no media in $dir" >&2; exit 1; }
git -C "$work" add -A "$day"
git -C "$work" -c user.name=spandan-bot -c user.email=spandan-bot@users.noreply.github.com \
  commit -qm "Batch $day"
git -C "$work" push -q --force origin "HEAD:refs/heads/upload/$day"
echo "pushed upload/$day ($(ls "$work/$day" | wc -l) files); the workflow publishes it in ~1 min"
