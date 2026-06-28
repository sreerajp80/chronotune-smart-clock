# Change log: Fix redundant qualifier in AppDatabase.kt

Implements plan `plans/20260628_000000_fix-redundant-qualifier.md`.

## Change
- `app/src/main/java/in/sreerajp/chronotune_smart_clock/data/AppDatabase.kt:95`
  Changed `object : RoomDatabase.Callback()` to `object : Callback()`.

`Callback` is a nested type inherited from the `RoomDatabase` superclass that
`AppDatabase` extends, so the `RoomDatabase.` qualifier was redundant (the IDE
lint warning "Remove redundant qualifier name"). The `RoomDatabase` import is
retained — it is still used as the class supertype. No behavioral change.
