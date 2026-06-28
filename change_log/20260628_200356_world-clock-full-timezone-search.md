# World Clock — full timezone search, day/night, UTC offset

Implements plan [20260628_200356_world-clock-full-timezone-search.md](../plans/20260628_200356_world-clock-full-timezone-search.md).

## What changed

### `ui/ClockViewModel.kt`
- `CityZone`: renamed field `country` → `region`.
- Removed the 16 hardcoded `availableCities`. Replaced with a `by lazy` catalog
  built from `java.util.TimeZone.getAvailableIDs()` (API 1 — avoids `java.time`'s
  `ZoneId`, which needs API 26; minSdk here is 24 with no desugaring).
  - Filtered to real continent/ocean regions
    (`Africa, America, Antarctica, Arctic, Asia, Atlantic, Australia, Europe,
    Indian, Pacific`), dropping `Etc/*`, `SystemV/*`, `US/*`, and bare aliases.
  - City name = last `/` segment with `_`→space; region = first segment, plus the
    middle segment for 3-part IDs (e.g. "America · Argentina").
  - Sorted by current UTC offset, then city name (~430 zones).

### `WorldClockScreen.kt`
- `getZoneOffsetFormatted`: rewritten to render `GMT±H:MM` from minutes instead of
  truncating with integer hour division — fixes India showing "GMT+5" (now
  "GMT+5:30") and Nepal/other half/quarter-hour zones.
- Added `isDaytimeInZone(zoneId)` helper (daytime = local hour 06:00–17:59).
- `WorldClockItem`: added a sun/moon day-night icon (amber / indigo-grey) before
  the time.
- `LocationSearchDialog`: search now matches `region`; rows show `region` and a
  day/night icon next to the offset.

No DB/schema change — `WorldClock` (cityName + timezoneId) unchanged; the zone ID
remains the persisted key.

## Verification
- `./gradlew assembleDebug` succeeds; `app-debug.apk` produced.
- Manual checks (offsets, filtered cruft, day/night icons) per the plan remain for
  on-device confirmation.
