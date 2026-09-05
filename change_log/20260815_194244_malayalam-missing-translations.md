# Added the five missing Malayalam translations

Implements plan `plans/20260815_193959_malayalam-missing-translations.md`.

## What changed

One file: `app/src/main/res/values-ml/strings.xml`.

Added five `<string>` entries at the top of the file, in the same order as the default
English file:

| String name | English | Malayalam added |
|---|---|---|
| `app_name` | Clock | ക്ലോക്ക് |
| `widget_digital_clock_label` | Chronos Digital | Chronos ഡിജിറ്റൽ |
| `widget_digital_clock_description` | A clean digital time, seconds, and date readout. | വൃത്തിയുള്ള ഡിജിറ്റൽ സമയം, സെക്കൻഡ്, തീയതി എന്നിവ കാണിക്കുന്നു. |
| `widget_analog_clock_label` | Chronos Analog | Chronos അനലോഗ് |
| `widget_analog_clock_description` | The signature Chronos watch face on your home screen. | നിങ്ങളുടെ ഹോം സ്ക്രീനിൽ Chronos-ന്റെ സ്വന്തം വാച്ച് ഫേസ്. |

"Chronos" was kept in Latin script because it is the product name. Only the words around it
were translated.

No code changes. No changes to the default English strings.

## Why

These five strings had no Malayalam version. Android fell back to the English text, so a
phone set to Malayalam saw English for the app icon label, both widget names in the widget
picker, and both widget descriptions. The project has no `MissingTranslation` lint
suppression, so a release build could also warn about them.

## Check done

Compared the string names in both files. Both now have 28 strings, and no string name exists
in the default file that is missing from the Malayalam file.

Not yet done: a real build and an on-device check with the phone language set to Malayalam.
