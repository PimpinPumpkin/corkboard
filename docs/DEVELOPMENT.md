# Developing Corkboard

A native Android client for craigslist: Material 3, dark mode, real filters, no Google services.
See README.md for what it does and how it talks to the site.

## Rules that are checked, not remembered

- **No trailers.** No `Co-Authored-By` trailer and no "generated with" line in any commit, PR or
  release note: GitHub turns a trailer into a listed contributor. `scripts/check-writing.sh`
  enforces it locally (run `bash scripts/install-hooks.sh` once per clone) and in CI.
- **US English** in code, comments, UI text and commit messages.
- **No em dashes** anywhere.
- Commit subjects are the user-facing changelog: release notes are built from them verbatim, so
  write them as plain sentences about what changed for the person using the app.

## Rules about the network

- **Every request goes through `net/Http.kt`**, and so through Cronet. Never add OkHttp,
  HttpURLConnection or a WebView fetch for craigslist hosts: a second network stack is a second
  fingerprint. Images included (`net/Images.kt` replaces Coil's fetcher).
- **The app is Chrome on Android, at the bundled Cronet's version.** The user agent and client
  hints derive from `corkboard.cronetVersion` in gradle.properties. Do not claim another platform:
  the TCP stack underneath is Android's and a mismatch is worse than honesty.
- **No server, no shared anything.** Each phone talks to craigslist by itself at the pace its user
  taps. Saved-search checks run every few hours with jitter, one search at a time.
- **Follow the site's own request sequence** (`data/SearchSession.kt`): quick 360, then the full
  digest list, then detail batches of 1080. The API rejects parameters it does not know.
- **`cc` and `lang` are the visitor's**, set once in `Store.applyLocale` from the phone's country and
  the language setting. The site translates filter labels, attributes and prices itself; do not
  translate them in the app. Units (mi/km, ft/m) come from each search response.
- **A postal code the site does not know is ignored silently**, and one that exists elsewhere moves
  the search there. The results header always shows the place the site answered for.
- **Maps are craigslist's own tiles** (`map0`..`map9.craigslist.org`, `ui/components/MiniMap.kt`), the
  ones its listing page uses. Do not add another tile server: that is a new party seeing every listing
  the user opens.
- **Location lookups are the site's too**: `geo.craigslist.org` redirects to the nearest site by network
  address (used once, on first launch), `rapi/locations` turns coordinates into a site and postal code,
  and a postal code is resolved by running a search. Coordinates are rounded to two decimals first.
- **The country gate is client-side** (`Store.countryGate`): rows are dropped by their site's country,
  so a border search fetches several batches to fill a screen. Odometer units are per listing.
- **The archive** (`data/Archive.kt`) keeps the raw response of every listing opened (newest 400) and,
  for kept listings, their photos, fetched one at a time. A 404 from `rapi/postings` means gone;
  the listing screen then shows the saved copy. `repostOf` in a listing names the posting it replaces;
  that link comes from craigslist, the app does no matching of its own. A repost of something the user
  kept is added beside the old one, never in place of it.
- **One request leaves craigslist's hosts:** `calibration.json` from this repository, once a day
  (`data/Calibration.kt`). It can retune the API version, batch sizes and row tags and show a notice;
  it cannot change hosts, and the app only accepts it signed. To ship a fix without an app update:
  edit the value, bump `version`, run `scripts/sign-calibration.sh`, and commit `calibration.json`
  with `calibration.json.sig` to main. The signing key lives outside the repository; a unit test
  fails the build when the two files do not match.
- **Replying, posting and accounts go to the browser.** They sit behind the site's own checks.
- The name and icon never use "craigslist" as a brand. The app says it is unaffiliated.

## Releases

There is one channel. A push to `main` that changes something a user can see is built, tested,
signed and published as the next release (`.github/workflows/ci.yml`). Commits whose subject
starts with `Docs:`, or that only touch documentation or comments, build and test but publish
nothing. Versions count releases: `0.1.0` is code 10000, `0.1.1` is 10001.

So `main` is what users get. Test before pushing: the unit tests, the release build on an
emulator, and `scripts/self-check.sh`.

## Building

`./gradlew assembleDebug` (JDK 17, Android SDK platform 37.0). The first build downloads the Cronet
AAR. Local builds are version code 1, below anything CI publishes. Release signing reads
`CORKBOARD_KEYSTORE_PATH`, `CORKBOARD_KEYSTORE_PASSWORD` and `CORKBOARD_KEY_ALIAS` from the
environment and falls back to the debug key without them.

Debug builds take `--es theme light|dark`, `--ez self_check true`, take `--ez check_alerts true` to run the saved-search
check at once instead of hours later, and log every request as `CorkboardHttp` in logcat.

## Chores

- **Cronet updates itself.** `cronet.yml` runs weekly: when Chrome for Android stable moves past
  `corkboard.cronetVersion`, it fetches or packs that Cronet, builds and tests the app with it, and
  runs the self-check on an emulator. A pass is committed to main and released; a failure opens one
  issue and commits nothing.
- **The app checks itself daily.** `health.yml` runs `data/SelfCheck.kt` against the live site and
  opens one issue when a step breaks, closing it when the check passes again. `scripts/self-check.sh
  <debug.apk>` runs the same check on any connected emulator or phone. When it fails on a value in
  `calibration.json`, fix that file and bump its `version`; otherwise the parsers need a change.
- **The API is undocumented.** If parsing breaks, capture a fresh response, trim it, replace the
  listing text, and put it in `app/src/test/resources` before touching `data/Parsers.kt`.
