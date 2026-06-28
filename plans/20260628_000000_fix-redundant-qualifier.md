# Fix redundant qualifier in AppDatabase.kt

**Status:** completed

## Issue
IDE lint warning at [AppDatabase.kt:95](app/src/main/java/in/sreerajp/chronotune_smart_clock/data/AppDatabase.kt#L95):
"Remove redundant qualifier name". The callback is constructed as
`object : RoomDatabase.Callback()`. Because `AppDatabase` already extends
`RoomDatabase()`, the `RoomDatabase.` qualifier on the nested `Callback` type is
redundant.

## Files to change
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/data/AppDatabase.kt`

## Fix
Change `object : RoomDatabase.Callback()` to `object : Callback()` on line 95.
No behavioral change — purely removes the redundant qualifier the lint flags.
