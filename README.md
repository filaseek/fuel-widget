# Fuel Widget — Tank ONO prices on your home screen

A tiny Android widget that shows **Natural 95** and **Diesel** prices for the Tank ONO station you pick.
Prices come from the official price list at https://www.tank-ono.cz/cz/index.php?page=cenik.

## Build and install (about 10 minutes the first time)

1. Install **Android Studio** (free): https://developer.android.com/studio
2. Unzip this folder, then in Android Studio: **File → Open** → select the `FuelWidget` folder.
   Let it sync (it downloads Gradle and the Android SDK the first time). If it offers to upgrade the
   Android Gradle Plugin, it's fine to accept.
3. On your phone: **Settings → About phone → tap "Build number" 7×** to enable Developer options,
   then **Settings → Developer options → USB debugging: on**. Connect the phone by USB.
4. Press the green **Run ▶** button. The app installs and opens a list of all Tank ONO stations.

   *No cable?* Use **Build → Build App Bundle(s)/APK(s) → Build APK(s)**, copy
   `app/build/outputs/apk/debug/app-debug.apk` to the phone and open it (allow "install unknown apps").

## Add the widget

Long-press the home screen → **Widgets** → **Fuel Widget** → drag it out → tap your station.

- **Tap the widget** to refresh right away. It also refreshes on its own every few hours.
- **Change station:** long-press the widget → reconfigure (Android 12+), or remove it and add it again.
- The bottom line shows which price list it's showing ("ceník od 14.10.") and when it last updated.
  If the phone is offline it keeps showing the last prices it fetched.

## Files

| File | What it does |
|---|---|
| `PriceFetcher.kt` | Downloads the price list and reads N95 + Diesel per station |
| `FuelWidget.kt` | The home-screen widget (draws prices, handles tap-to-refresh) |
| `ConfigActivity.kt` | Station picker (also opens from the app icon) |
| `res/layout/widget_fuel.xml` | Widget look: colours, text sizes |

If Tank ONO ever redesigns their price page and the widget shows "–", `PriceFetcher.parse()` is the
only thing that needs adjusting.
