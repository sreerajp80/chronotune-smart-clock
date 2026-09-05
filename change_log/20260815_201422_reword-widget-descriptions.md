# Reworded the digital widget description

Implements plan `plans/20260815_201340_reword-widget-descriptions.md`.

## What changed

Two files, one string each. No code changes.

`app/src/main/res/values/strings.xml`, line 4:

- Before: `A clean digital time, seconds, and date readout.`
- After: `A simple digital readout of time, seconds, and date.`

`app/src/main/res/values-ml/strings.xml`, line 4:

- Before: `വൃത്തിയുള്ള ഡിജിറ്റൽ സമയം, സെക്കൻഡ്, തീയതി എന്നിവ കാണിക്കുന്നു.`
- After: `സമയം, സെക്കൻഡ്, തീയതി എന്നിവയുടെ ലളിതമായ ഡിജിറ്റൽ കാഴ്ച.`

## Why

The old English text used "clean" as design jargon, meaning the layout is uncluttered. But the
word was attached to the time, so it read as if the time itself were clean, which makes no
sense. The Malayalam text copied the same problem with `വൃത്തിയുള്ള` ("clean, neat").

The new wording says "simple" instead, and puts the adjective on the readout rather than on
the time. The Malayalam now uses `ലളിതമായ` ("simple") to match.

This string appears in the widget picker, via `res/xml/digital_clock_widget_info.xml` line 3.

## Not changed

The analog widget description still reads "The signature Chronos watch face on your home
screen." The word "signature" is marketing language, but no replacement wording was chosen,
so it was left alone. The matching Malayalam string was left alone too.

## Check done

Read back both files and confirmed each holds the new text on line 4.

Not yet done: a real build, and reading the description in the widget picker on a device in
both languages.
