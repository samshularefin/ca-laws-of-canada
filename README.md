# Laws of Canada — Android App

Full-text browsable/searchable database of all Canadian federal Acts and
Regulations, sourced directly from Justice Canada's official maintained
XML repository (`github.com/justicecanada/laws-lois-xml`) — no scraping.

## What's in this delivery

- **ca-laws-backend.tar.gz** — the ingestion pipeline (`pipeline/`) and a
  reference Express API (`api/`). The pipeline clones the Justice Canada
  XML repo and produces `ca_laws.db`, a SQLite file with FTS5 full-text
  search over every Act, Regulation, and section.
- **ca-laws-android.tar.gz** — the native Java Android app (package
  `com.diasoft.calaws`). Browses Acts/Regulations, searches by title or
  full text, and reads sections with heading breadcrumbs.
- **ca_laws.db.gz** (68MB) — the actual compiled database, current to
  2026-06-21. Host this file (and `version.txt`) somewhere with a stable
  URL (Cloudflare R2, a GitHub Release asset, S3, etc.) — the app
  downloads and decompresses it on first launch.
- **version.txt** — a one-line version marker the app polls to know when
  to silently re-download an updated DB.

## Why this architecture

The DB is 296MB uncompressed (971 Acts + 4,878 Regulations, 122,019
searchable sections) — too large to bundle in the APK. Instead the app
ships thin and downloads the gzipped DB (68MB) once on first launch,
then works fully offline. This matches your existing no-API offline app
pattern (RideProfit, PillTime) while still keeping the legal data itself
authoritative and periodically refreshable.

Justice Canada updates the source repo roughly every two weeks. Re-run
the pipeline on that cadence, re-upload `ca_laws.db.gz` + bump
`version.txt`, and every installed app will pick up the update silently
on next launch.

## Rebuilding the database

```bash
# 1. Clone the official source (~138MB)
git clone --depth 1 https://github.com/justicecanada/laws-lois-xml.git

# 2. Install pipeline deps
cd pipeline && npm install

# 3. Build the DB (English; pass 'fra' as 3rd arg for French)
node build_db.js /path/to/ca_laws.db eng

# 4. Compress for hosting
gzip -9 -k /path/to/ca_laws.db
```

This produced, in testing: **5,849 documents, 122,019 sections, 0 parse
failures**, in about 53 seconds.

## Before shipping the app

1. **Host the DB**: upload `ca_laws.db.gz` and `version.txt` somewhere
   with a permanent URL. Update the two placeholder constants in
   `DatabaseInstaller.java`:
   ```java
   private static final String DB_URL = "https://cdn.example.com/ca-laws/ca_laws.db.gz";
   private static final String VERSION_URL = "https://cdn.example.com/ca-laws/version.txt";
   ```
2. **AdMob**: replace the placeholder app ID in `AndroidManifest.xml`
   (`ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY`) with your real one, and
   wire up ad units (e.g. a banner in `MainActivity`, interstitials
   between document reads) the same way as your other apps.
3. **Launcher icon**: I added a placeholder maple-leaf vector icon.
   Swap in real mipmap assets (or a proper adaptive icon) before release.
4. **French edition**: the source repo has a parallel `fra/` tree with
   identical structure — rerun `build_db.js` with `fra` to produce a
   French DB, and either ship a locale-aware download or a language
   toggle in-app.

## App structure

- `data/DatabaseInstaller.java` — first-launch download + gunzip +
  version-check/update logic
- `data/LawsDbHelper.java` — read-only SQLite query layer (list, get,
  title search, full-text search)
- `ui/SplashActivity` — handles the download flow with a progress bar
- `ui/MainActivity` — tabbed Acts/Regulations browser with pagination
- `ui/SearchActivity` — title search and full-text section search
- `ui/DocumentActivity` + `SectionAdapter` — reader view with heading
  breadcrumbs per section (e.g. "Offences Against the Person > Assaults")

## Data notes

The XML source encodes real legislative structure Claude verified during
ingestion — e.g. repealed sections show correctly (`[Repealed, 2018,
c. 21, s. 14]` for Criminal Code s.253, since impaired-driving offences
moved to Part VIII.1 in 2018), and each document carries `in_force_start`,
`last_amended`, and `current_to` dates pulled straight from the official
consolidation metadata.
