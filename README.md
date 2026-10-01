<div align="center">

<img src="docs/logo.svg" width="120" alt="Corkboard logo">

# Corkboard

**craigslist, the way a phone app should be.**

Every filter, a real dark mode, saved lists and notes, and no Google services anywhere.

[![Stable release](https://img.shields.io/github/v/release/PimpinPumpkin/corkboard?label=stable&color=8B5000)](https://github.com/PimpinPumpkin/corkboard/releases/latest)
[![Build](https://img.shields.io/github/actions/workflow/status/PimpinPumpkin/corkboard/ci.yml?branch=main&label=build)](https://github.com/PimpinPumpkin/corkboard/actions/workflows/ci.yml)
[![License: GPL v3](https://img.shields.io/github/license/PimpinPumpkin/corkboard?color=blue)](LICENSE)
[![Stars](https://img.shields.io/github/stars/PimpinPumpkin/corkboard?style=flat&color=ffd43b)](https://github.com/PimpinPumpkin/corkboard/stargazers)

[Install](#install) · [What you get](#what-you-get) · [Privacy](#privacy) · [How it works](#how-it-works) · [Build](#building) · [Issues](https://github.com/PimpinPumpkin/corkboard/issues)

</div>

> [!warning]
> **Corkboard is new, so you may run into bugs.** If you do, open an issue. Nightly and canary
> builds are newer still and less tested than the weekly stable.

> [!note]
> Corkboard is an independent project. It is not affiliated with, endorsed by, or connected to
> craigslist.

A native Android client for craigslist: *what NewPipe is to YouTube, for the classifieds.* The
official app needs Google Play services and will not run on a de-Googled phone. Corkboard needs
nothing but the internet. It talks to craigslist straight from your phone, the same way the site's
own web page does, and draws what comes back as a proper Material 3 app.

## Screenshots

| Home | Results | Filters | Listing | Map and details |
|:-:|:-:|:-:|:-:|:-:|
| <img src="docs/screenshots/01-home.png" width="150"> | <img src="docs/screenshots/02-results.png" width="150"> | <img src="docs/screenshots/03-filters.png" width="150"> | <img src="docs/screenshots/04-listing.png" width="150"> | <img src="docs/screenshots/05-map.png" width="150"> |

| Saved lists | Where to look | Dark theme: home | Dark theme: results |
|:-:|:-:|:-:|:-:|
| <img src="docs/screenshots/06-lists.png" width="150"> | <img src="docs/screenshots/07-where.png" width="150"> | <img src="docs/screenshots/08-home-dark.png" width="150"> | <img src="docs/screenshots/09-results-dark.png" width="150"> |

*Pinned categories and saved searches on the home screen; photo-first results; the full set of
car filters; a listing with your own note on it; the map and the facts; lists you make yourself;
three ways to say where to look; and the dark theme.*

## Install

[<img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" alt="Get it on Obtainium" height="54">](https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/PimpinPumpkin/corkboard)

Obtainium tracks the **weekly stable** release. Turn on "include prereleases" and you get the
**nightly** channel instead. Or take an APK straight from
[Releases](https://github.com/PimpinPumpkin/corkboard/releases).

Requires Android 10 or later on a 64-bit ARM phone. No Google Play services, no account.

### Check you got the real thing

Every Corkboard APK, from any channel, is signed with the same key. Its certificate fingerprint is:

```
SHA-256  BB:9F:49:EC:3A:7D:EA:7E:20:BA:CE:33:AA:CB:0B:B4:C8:C3:D6:39:B1:35:30:93:4A:53:D4:0A:7D:82:F5:EB
```

Check a downloaded file against it before installing:

```bash
apksigner verify --print-certs corkboard-*.apk
```

On the phone, [App Verifier](https://github.com/soupslurpr/AppVerifier) checks the same thing. It
wants the package name on the first line and the fingerprint on the second:

```
app.corkboard
BB:9F:49:EC:3A:7D:EA:7E:20:BA:CE:33:AA:CB:0B:B4:C8:C3:D6:39:B1:35:30:93:4A:53:D4:0A:7D:82:F5:EB
```

Android enforces this for you after the first install: an update signed with a different key is
refused.

### Release channels

| Channel | What it is | How often |
| --- | --- | --- |
| **Stable** | The newest nightly, promoted | Weekly |
| **Nightly** | Built from `main` | Every day `main` changes |
| **Canary** | Built from the `canary` branch | Every push |

All three share one signing key and one rising version code, so moving between them is a plain
update.

## What you get

### Searching

- **Every filter the site has.** Cars get make and model, year, odometer, drive, transmission,
  cylinders, fuel, body type, paint color and title status. Housing gets bedrooms, bathrooms,
  square feet, pets, laundry and parking. craigslist describes each category's filters in its
  search responses and the app draws them from that, so nothing is hand-built per category and
  nothing goes missing.
- **Start from where you are.** The first launch opens on the site craigslist itself would pick
  for you. Change it with "Use my location", a postal code and a distance, or the full list of
  sites.
- **Search near a postal code**, within a distance you choose, or pick a sub-area. Sort by
  distance.
- **Hide other countries.** A distance search near a border otherwise returns the other side of
  it, in its currency and its odometer units. On by default.
- **Any craigslist site in the world.** Filters, labels and prices come back in your language (the
  fifteen the site is translated into), with kilometers and local currency where they apply. The
  app's own buttons and menus are in English for now.
- **Grid or list**, with photos, price, mileage or bedrooms, place and age at a glance.
- **Pin the categories you use** to the top of the home screen.

### Listings

- **Photos you can zoom**, with a pinch or a double tap.
- **A map on the listing itself** that you can drag and pinch, with no maps app needed.
- **When it was posted, and when the seller last changed it.** The updated time appears only for a
  real edit or renewal, so it means something. Reposts are marked.
- **Price changes.** If the price is different from the last time you looked, the old one is shown
  beside it.
- **Opens craigslist links.** Share a listing link to Corkboard, or choose it under "Open with".

### Keeping track

- **Favorites and your own lists.** Make as many lists as you like, share one as text, or save it
  as a spreadsheet.
- **Notes.** Write a private note on any listing. Every listing with a note is easy to find again,
  hearted or not.
- **Hide what you have seen enough of.** Press and hold a result and it is gone from every search,
  with an undo.
- **Deleted listings stay readable.** Every listing you open is saved on the phone, and the ones
  you keep have their photos saved too. When the seller deletes it, you still have what it said.
- **Reposts keep their history.** When the same thing is posted again, your heart, note and lists
  move to the new listing.
- **Saved searches with alerts.** The phone checks about every three hours and tells you what is
  new. No push service, no account. See [the small print](#alerts-the-small-print).

### The rest

- **Light, dark and wallpaper colors**, Material 3 throughout.
- **No Google services needed.** Built for GrapheneOS and other de-Googled phones.

Replying to a listing, posting, and account pages open in your browser. Those sit behind
craigslist's own checks, and that is where they belong.

### Alerts, the small print

Alerts are checked by the phone itself, the way a de-Googled alarm or reminder app has to work:
there is no server watching craigslist for you and no push message to wake the app.

- **Android decides when the check runs.** By default it may delay or skip background work to save
  battery, sometimes for many hours. For alerts you can rely on, open the app's page in system
  settings and set **App battery usage** to **Unrestricted**. The saved searches screen has a
  button that takes you there.
- **Still not instant.** Checks are about three hours apart on purpose, and can be later while the
  phone sits idle or battery saver is on. Something that sells in twenty minutes will be gone.
- **Needs a network** at the time of the check, and notification permission.
- **Some phones are stricter.** Makers that kill background apps aggressively may need the app
  allowed to run in the background as well; [dontkillmyapp.com](https://dontkillmyapp.com) lists
  what each one needs.
- **Light on the battery.** One small request per saved search per check, no constant connection,
  and nothing at all when no search has alerts on.

## Privacy

There is no Corkboard server. Nothing you do in the app is sent to anyone but craigslist, and
craigslist sees what it would see from a browser.

| What you do | Who hears about it |
| --- | --- |
| Open a category, search, change a filter | **craigslist**, as an anonymous visitor |
| Open a listing, look at its photos and map | **craigslist.** The map tiles are craigslist's own, the ones its web page uses |
| First launch | **craigslist**, once, to learn which site is nearest your network address |
| Tap "Use my location" | **craigslist**, with coordinates rounded to about a kilometer. Only when you tap it |
| Heart, hide, note, add to a list, save a search | **Nobody.** It stays in the app's private storage |
| Saved-search alerts | **craigslist**, the same search a few times a day. No push service in between |
| Reply to a listing | **Your browser** takes over |

No analytics, no crash reporting, no accounts, no advertising identifiers. The only cookie is the
one craigslist gives every browser, and Settings can clear it.

## How it works

- **The same requests as the web page.** Search results, listing details and make-and-model
  suggestions come from the JSON endpoints craigslist's own search page calls, in the same sequence
  the page uses.
- **Chromium's network stack.** Every request goes through Cronet, the network library inside
  Chrome, so the app is on the wire what it says it is: Chrome on Android, at the version of Cronet
  in the APK.
- **At your pace.** A request happens when you do something. Results are reused for as long as the
  site says they may be. Saved searches are checked a few times a day, one at a time.
- **One phone, one visitor.** Nothing is pooled, proxied or shared between users.

## Building

```bash
./gradlew assembleDebug
```

JDK 17 and Android SDK platform 37.0. The first build downloads a prebuilt Cronet (15 MB).

```bash
./gradlew testDebugUnitTest
```

runs the tests: the parsers against trimmed real responses, the request shapes, and the exports.

## Contributing

Issues and pull requests are welcome. Two house rules, both checked by `scripts/check-writing.sh`
and by CI: US English, and no em dashes. Commit subjects are used verbatim as release notes, so
write them as plain sentences about what changed for the person using the app.

If craigslist changes something and the app breaks, the most useful report is the category and
area you were in and what you tapped.

## License

GPL-3.0. Cronet is from the Chromium project under its BSD license. The typeface is
[Google Sans Flex](https://fonts.google.com/specimen/Google+Sans+Flex), OFL 1.1, see
[licenses/](licenses/GoogleSansFlex-OFL.txt). Map data is © OpenStreetMap contributors, served by
craigslist.

craigslist is a trademark of craigslist, Inc. Corkboard uses the name only to say what it works
with.
