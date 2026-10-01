# Corkboard

A native Android app for browsing craigslist. Material 3, light and dark themes, and the filters
each category really has, on a phone with no Google services.

Corkboard is an independent project. It is not affiliated with, endorsed by, or connected to
craigslist.

- **Every filter the site has.** Cars get make and model, year, odometer, drive, transmission,
  cylinders, fuel, body type, paint color and title status. Housing gets bedrooms, bathrooms,
  square feet, pets, laundry and parking. The site describes each category's filters in its search
  responses and the app draws them from that, so nothing is hand-built per category.
- **Grid or list**, with photos, price, mileage or bedrooms, place and age at a glance.
- **Start from where you are.** The first launch opens on the site craigslist itself would pick for
  you. Change it with one tap on "Use my location", a postal code and a distance, or the full list.
- **Search near a postal code**, within a distance you choose, or pick a sub-area. Sort by distance.
- **Hide other countries.** A distance search near a border otherwise returns the other side of it,
  in its currency and odometer units. On by default.
- **Any craigslist site in the world.** Filters, labels and prices come back in your language
  (the fifteen the site is translated into), with kilometers and local currency where they apply.
  The app's own buttons and menus are in English for now.
- **Full listings**: photo gallery with pinch or double tap to zoom, attributes, description, map
  (your maps app, or OpenStreetMap in the browser if there is none).
- **A map on the listing itself** that you can drag and pinch, with no maps app needed, and when it
  was posted and, if the seller has edited it since, when.
- **Favorites, your own lists, notes and hidden listings**, kept on the phone. Share a list as text
  or save it as a spreadsheet. Press and hold a result to hide it.
- **Deleted listings stay readable.** Every listing you open is saved; the ones you keep have their
  photos saved too. If the seller deletes it, you still have what it said, and a price that changed
  since you last looked is shown beside the new one. A repost brings your heart and note along.
- **Opens craigslist links.** Share a listing link to Corkboard, or choose it under "Open with".
- **Pin the categories you use** to the top of the home screen.
- **Saved searches with alerts.** The phone checks every few hours and tells you what is new.
  No push service, no account.
- **Light, dark and wallpaper colors.**
- **No Google services needed.** Works on GrapheneOS and other de-Googled phones.

Replying to a listing, posting and account pages open in your browser.

Requires Android 10 or later on a 64-bit ARM phone.

## How it talks to craigslist

There is no Corkboard server. The app on your phone talks to craigslist directly, the same way
the site's own web page does, and only when you do something: open a category, change a filter,
scroll, open a listing.

- **The same requests as the web page.** Search results, listing details and make-and-model
  suggestions come from the JSON endpoints the site's own search page calls, in the same sequence
  the page uses.
- **Chromium's network stack.** Requests go through Cronet, the network library inside Chrome, so
  the app is on the wire what it claims to be: Chrome on Android, at the version of Cronet in the
  APK.
- **At your pace.** Results are cached for as long as the site says they may be. Saved searches
  are checked a few times a day, one at a time.
- **Nothing collected.** No analytics, no crash reporting, no accounts. Favorites, saved searches
  and settings stay in the app's private storage. The only cookie is the one craigslist gives
  every browser, and Settings can clear it.

## Building

```
./gradlew assembleDebug
```

JDK 17 and Android SDK platform 37.0. The first build downloads a prebuilt Cronet (15 MB).

## Release channels

- **Stable**: the newest nightly, promoted once a week.
- **Nightly**: built from `main` every day it changes.
- **Canary**: every push to the `canary` branch.

All three share one signing key and one rising version code, so moving between them is a plain
update.

## License

GPL-3.0. Cronet is from the Chromium project under its BSD license. The typeface is
[Google Sans Flex](https://fonts.google.com/specimen/Google+Sans+Flex), OFL 1.1, see
[licenses/](licenses/GoogleSansFlex-OFL.txt).
