# World Clock — full timezone search, day/night, UTC offset

**Status:** completed

## Issue

The World Clock "Add Location" picker only offers **16 hardcoded cities**
(`availableCities` in [ClockViewModel.kt:58](../app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/ClockViewModel.kt#L58)).
Users can't add most cities. We want:

1. **Full timezone search** — every zone, not a curated 16.
2. **Day/night indicator** per city.
3. **Correct UTC offset** per city.

Two existing problems to fix while we're here:

- **Offset bug:** `getZoneOffsetFormatted` ([WorldClockScreen.kt:559](../app/src/main/java/in/sreerajp/chronotune_smart_clock/WorldClockScreen.kt#L559))
  does integer division `offsetMs / (3600*1000)`, so India (+5:30) shows as
  "GMT+5" and Nepal (+5:45) as "GMT+5". Half/quarter-hour offsets are wrong.
- **API level:** minSdk is **24** with **no core-library desugaring**.
  `ZoneId.getAvailableZoneIds()` / `ZonedDateTime` are `java.time` (API 26+) and
  would crash on API 24–25. We must use **`java.util.TimeZone.getAvailableIDs()`**
  (API 1), which returns the same IANA zone IDs and matches the `TimeZone`-based
  helpers already in this file.

## Approach

### 1. Build the city catalog at runtime (no hardcoding) — `ClockViewModel.kt`

Replace the hardcoded `availableCities` list with a lazily-built list derived from
`TimeZone.getAvailableIDs()`, filtered to clean **Region/City** form.

- Extend `CityZone` to carry the region label:
  `data class CityZone(val cityName: String, val timezoneId: String, val region: String)`
  (renames `country` → `region`; update the one usage in the dialog).
- Filtering rule — keep only zones whose **first segment** is a real
  continent/ocean region:
  `Africa, America, Antarctica, Arctic, Asia, Atlantic, Australia, Europe, Indian, Pacific`.
  This drops the database cruft (`Etc/GMT+5`, `SystemV/*`, `US/*`, single-segment
  aliases like `GMT`, `UTC`, `Egypt`, `Cuba`). Leaves ~430 zones, fully global.
- Derive display fields from the ID:
  - **cityName** = text after the last `/`, `_` → space
    (`America/Argentina/Buenos_Aires` → "Buenos Aires").
  - **region** = first segment (`America/Argentina/Buenos_Aires` → "America"). For
    3-segment IDs, append the middle segment for disambiguation
    (→ "America · Argentina").
- Build once via `by lazy` (≈430 entries, cheap), sorted by current UTC offset then
  city name so the picker reads naturally.

### 2. Correct UTC offset formatting — `WorldClockScreen.kt`

Rewrite `getZoneOffsetFormatted` to render hours **and** minutes:

- `offsetMs = tz.getOffset(System.currentTimeMillis())` (DST-correct, live).
- Format as `GMT±H:MM` (e.g. `GMT+5:30`, `GMT-4:00`, `GMT+0:00`), computing minutes
  from the remainder instead of truncating.

### 3. Day/night indicator — `WorldClockScreen.kt`

Add a small helper `isDaytimeInZone(zoneId): Boolean`:
- `Calendar.getInstance(TimeZone.getTimeZone(zoneId))`, read `HOUR_OF_DAY`.
- Daytime if hour in `6..17` (06:00–17:59), else night.

Show it in:
- **`WorldClockItem`** — a sun (`Icons.Default.WbSunny`) / moon
  (`Icons.Default.DarkMode` or `Bedtime`) icon next to the time, tinted
  amber for day / indigo-grey for night.
- **`LocationSearchDialog`** rows — same small icon so users see local day/night
  while picking.

### 4. Search dialog — `WorldClockScreen.kt`

- `filteredCities` filters the runtime catalog by **cityName OR region**
  (`contains`, ignoreCase). With a blank query, show the full sorted list
  (lazy column handles ~430 rows fine).
- Update the field rename: rows show `city.region` instead of `city.country`.

## Files to change

- `app/src/main/java/in/sreerajp/chronotune_smart_clock/ui/ClockViewModel.kt`
  — replace hardcoded `availableCities` with runtime-built, filtered catalog;
  update `CityZone` (`country` → `region`).
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/WorldClockScreen.kt`
  — fix `getZoneOffsetFormatted`; add `isDaytimeInZone` + day/night icons in
  `WorldClockItem` and `LocationSearchDialog`; use `region` field.

No DB/schema change — `WorldClock` (cityName + timezoneId) is unchanged, and the
zone ID stays the stable persisted key.

## Verification

- Build (`./gradlew assembleDebug`).
- Search "Kolkata"/"India" → appears, offset shows **GMT+5:30** (not +5).
- Search "Kathmandu" → **GMT+5:45**.
- Confirm `Etc/*`, `SystemV/*`, `US/*`, bare `UTC` are absent from results.
- Add a zone whose local time is night → moon icon; one in daytime → sun icon.
- Verify on / reason about API 24 (no java.time used).
