# Reword the digital widget description

**Status:** completed

## Files to be changed

- `app/src/main/res/values/strings.xml` — change one string.
- `app/src/main/res/values-ml/strings.xml` — change the matching Malayalam string.

No code changes.

## What the issue is

`widget_digital_clock_description` currently reads:

> A clean digital time, seconds, and date readout.

"Clean" is design jargon. It is meant to say the design is uncluttered, but it is attached to
the wrong noun — it reads as if the *time* is clean. A time cannot be clean or unclean. The
sentence does not make plain sense.

The Malayalam version copies the same problem. It uses `വൃത്തിയുള്ള` ("clean, neat"), which
lands the same way in Malayalam.

This string is shown in the widget picker, from
`res/xml/digital_clock_widget_info.xml` line 3.

## The plan for the fix

Change the English string to the wording you chose:

```xml
<string name="widget_digital_clock_description">A simple digital readout of time, seconds, and date.</string>
```

Change the Malayalam string to match the new meaning, dropping `വൃത്തിയുള്ള`:

```xml
<string name="widget_digital_clock_description">സമയം, സെക്കൻഡ്, തീയതി എന്നിവയുടെ ലളിതമായ ഡിജിറ്റൽ കാഴ്ച.</string>
```

`ലളിതമായ` means "simple". This matches the new English rather than repeating the old
"clean" idea.

## Open question — the analog description

I am not changing the analog widget description in this plan, because you did not pick a
wording for it. It currently reads:

> The signature Chronos watch face on your home screen.

"Signature" is marketing language rather than plain English. If you want it changed too, the
plain version would be:

> The Chronos watch face on your home screen.

Malayalam would then drop `സ്വന്തം` and become:

> നിങ്ങളുടെ ഹോം സ്ക്രീനിൽ Chronos വാച്ച് ഫേസ്.

Say the word and I will add it to this plan. Otherwise the analog strings stay as they are.

## How to check it worked

1. Build the app.
2. Open the widget picker and read the digital widget description.
3. Set the phone language to Malayalam and read it again.
