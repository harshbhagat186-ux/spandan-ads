# Spandan Ads

Android app + hosting for Spandan Hospital's daily ad batch (5 posters 1080x1350 + 2 reels 1080x1920).

- Each day is a GitHub Release tagged `YYYY-MM-DD`; its assets are the original full-HD files.
- `index.json` (built by `.github/workflows/index.yml` from the releases) is what the app reads.
- The app (`app/`) lists the days, previews posters and reels, and saves the original files straight
  into the phone gallery (Pictures/Spandan, Movies/Spandan).
- `.github/workflows/build-apk.yml` builds a signed APK on every change under `app/` and publishes it
  to the release tagged `app`. The app shows an update banner when a newer build exists.

Publish a day: `tools/publish_day.sh /path/to/daily-ads/2026-10-28` (pushes branch `upload/<day>`; `.github/workflows/publish.yml` turns it into the release).

Install the app: open `https://github.com/<owner>/<repo>/releases/download/app/SpandanAds.apk` on the
phone, allow "install unknown apps" for the browser when asked, then tap Install.
