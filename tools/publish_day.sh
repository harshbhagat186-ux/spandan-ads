#!/usr/bin/env bash
# Publishes one day's batch folder as a GitHub Release so it shows up in the app.
#   tools/publish_day.sh owner/repo /mnt/project-files/daily-ads/2026-10-28
# Re-running for the same day replaces changed files (upload --clobber).
set -euo pipefail
repo="$1"
dir="${2%/}"
day="$(basename "$dir")"
[[ "$day" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]] || { echo "folder name must be YYYY-MM-DD" >&2; exit 1; }

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
files=()
for f in "$dir"/*.png "$dir"/*.jpg "$dir"/*.jpeg "$dir"/*.webp "$dir"/*.mp4; do
  [[ -e "$f" ]] || continue
  case "$f" in *.thumb.jpg) continue ;; esac
  files+=("$f")
  if [[ "$f" == *.mp4 ]]; then
    # Poster frame for the app's grid, taken 1.5s in (past the logo intro).
    thumb="$work/$(basename "${f%.mp4}").thumb.jpg"
    ffmpeg -loglevel error -y -ss 1.5 -i "$f" -frames:v 1 -vf scale=540:-2 -q:v 4 "$thumb"
    files+=("$thumb")
  fi
done
(( ${#files[@]} )) || { echo "no media in $dir" >&2; exit 1; }

if gh release view "$day" --repo "$repo" >/dev/null 2>&1; then
  gh release upload "$day" "${files[@]}" --repo "$repo" --clobber
else
  gh release create "$day" "${files[@]}" --repo "$repo" --title "Spandan ads $day" \
    --notes "Daily batch for $day: posters 1080x1350, reels 1080x1920." --latest=false
fi
# Refresh index.json so the app sees the new day right away.
gh workflow run index.yml --repo "$repo"
echo "published $day (${#files[@]} files)"
