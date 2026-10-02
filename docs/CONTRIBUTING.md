# Contributing to GboardCasual

Issues and pull requests are welcome. This is a small, focused module, so
a few ground rules keep it healthy.

## Reporting a broken Gboard update (most valuable contribution)

Gboard updates can rename the obfuscated commit gates (`ouo`, `ouf`, `f`,
`h`) and silently stop rewrites. A good report contains:

1. Installed Gboard version (`versionName` from the app status or
   `dumpsys package com.google.android.inputmethod.latin`).
2. The hook log after one translate attempt:
   `grep -h GboardCasual /data/adb/lspd/log/modules_*.log`
   (look for `gate watch skipped`, which names the dead gate).
3. One test sentence: what you typed, what Gboard showed, what landed
   in chat, what you expected.

With those three, re-targeting is usually a one-line `gate=` addition or
a small smali re-check (see `docs/SOURCES.md` for the forensics method).

## Reporting a tone miss

Same three items as above, minus the version forensics: input, Gboard
output, inserted text, expected text, target language. New pairs land in
`Hook.java` (`sIdLong` for phrases, `sBase`/`sSlang` for words,
`sEnBase`/`sEnSlang` for English) or as your own custom `Formal=casual`
lines, so no rebuild needed for the latter.

## Pull requests

- One topic per PR, in English (code, comments, README, UI strings).
- Code comments in a human voice, no numbered lists, no AI filler.
- Match the existing structure: WebUI stays Vue 3 + Vite singlefile with
  the `ksu.exec(cmd, '{}', id)` bridge; hook code uses only
  `XposedBridge.log` + `hookAllMethods` (obfuscated LSPosed runtimes lack
  `hookMethod`).
- Never package `classes.zip` as `classes.dex`: use `build-apk.sh`,
  which asserts dex magic twice.
- Test checklist in the PR body: fresh flash or APK reinstall, one reboot,
  translate test matrix from `README.md`, hook-log excerpt.
- Sign off your commits (`git commit -s`).

## What help is wanted

- More language pairs from native speakers (built-ins, not just customs).
- Gate re-targeting reports for new Gboard versions.
- Real-world test sentences (formal leaks, slang misses) for the matrix.
