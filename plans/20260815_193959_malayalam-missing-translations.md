# Add the five missing Malayalam translations

**Status:** completed

## Files to be changed

- `app/src/main/res/values-ml/strings.xml` — add five `<string>` entries.

No other file changes. No code changes.

## What the issue is

The Malayalam string file has 23 strings. The default English file has 28. Five strings
have no Malayalam version:

| String name | English text |
|---|---|
| `app_name` | Clock |
| `widget_digital_clock_label` | Chronos Digital |
| `widget_digital_clock_description` | A clean digital time, seconds, and date readout. |
| `widget_analog_clock_label` | Chronos Analog |
| `widget_analog_clock_description` | The signature Chronos watch face on your home screen. |

Where each one is used:

- `app_name` — `AndroidManifest.xml` lines 45 and 52 (app icon label and activity label).
- `widget_digital_clock_label` — `AndroidManifest.xml` line 96 (widget picker name).
- `widget_analog_clock_label` — `AndroidManifest.xml` line 108, and
  `res/layout/widget_analog_clock.xml` line 13 (content description for screen readers).
- `widget_digital_clock_description` — `res/xml/digital_clock_widget_info.xml` line 3.
- `widget_analog_clock_description` — `res/xml/analog_clock_widget_info.xml` line 3.

This is not a crash or a bug. Android falls back to the default English text when a string is
missing from `values-ml`. So a phone set to Malayalam shows everything in Malayalam except
these five, which stay English. It is a polish gap.

There is no lint suppression in the project for `MissingTranslation`, so a release build may
report these as warnings.

## The plan for the fix

Add the five strings to `app/src/main/res/values-ml/strings.xml`, grouped in a new block at
the top of the file with a comment, so it matches the order of the English file.

Translation choices:

1. **`app_name` → `ക്ലോക്ക്`** — the plain Malayalam word for "clock". This is the app label
   under the icon, so a Malayalam word reads better than "Clock".
2. **`widget_digital_clock_label` → `Chronos ഡിജിറ്റൽ`** — keep "Chronos" in Latin script
   because it is the product name, and translate only the "Digital" part.
3. **`widget_analog_clock_label` → `Chronos അനലോഗ്`** — same reason.
4. **`widget_digital_clock_description` →**
   `വൃത്തിയുള്ള ഡിജിറ്റൽ സമയം, സെക്കൻഡ്, തീയതി എന്നിവ കാണിക്കുന്നു.`
5. **`widget_analog_clock_description` →**
   `നിങ്ങളുടെ ഹോം സ്ക്രീനിൽ Chronos-ന്റെ സ്വന്തം വാച്ച് ഫേസ്.`

Planned text to add at the top of `values-ml/strings.xml`:

```xml
<string name="app_name">ക്ലോക്ക്</string>
<string name="widget_digital_clock_label">Chronos ഡിജിറ്റൽ</string>
<string name="widget_digital_clock_description">വൃത്തിയുള്ള ഡിജിറ്റൽ സമയം, സെക്കൻഡ്, തീയതി എന്നിവ കാണിക്കുന്നു.</string>
<string name="widget_analog_clock_label">Chronos അനലോഗ്</string>
<string name="widget_analog_clock_description">നിങ്ങളുടെ ഹോം സ്ക്രീനിൽ Chronos-ന്റെ സ്വന്തം വാച്ച് ഫേസ്.</string>
```

## Point to decide before starting

If you would rather keep the app name and the two widget names in English (many apps keep
brand names untranslated), say so and I will add only the two descriptions and leave
`app_name`, `widget_digital_clock_label`, and `widget_analog_clock_label` out. The English
fallback would then still apply to those three.

## How to check it worked

1. Build the app.
2. Set the phone language to Malayalam.
3. Check the app icon label on the home screen.
4. Open the widget picker and check both widget names and their descriptions.
