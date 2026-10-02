# GboardCasual

[![license](https://img.shields.io/badge/license-MIT-green)](LICENSE)
[![root](https://img.shields.io/badge/root-KernelSU%20%2B%20LSPosed-orange)](#requirements)
[![gboard](https://img.shields.io/badge/tested-Gboard%2018.3.2-blue)](#requirements)

Gboard Translate always sounds like a textbook. **GboardCasual** loosens it
into everyday speech right where you chat, built for Indonesians talking
to the world: `Anda` → `kamu` (or `lu`), `Saya` → `aku` (or `gw`),
`I am` → `I'm`, `going to` → `gonna`, plus custom pairs for any other
language.

> **Honest scope:** translation itself still comes from Google servers, so
> a misread source (slang input like `gw`/`njir` misunderstood upstream)
> cannot be repaired here. This module restyles the outgoing text on your
> device. True context-aware paraphrase would need an LLM pass, see
> [Limitations](#limitations).

---

## Table of contents

- [Features](#features)
- [How it works](#how-it-works)
- [Repository layout](#repository-layout)
- [Requirements](#requirements)
- [Install](#install)
- [Settings app](#settings-app)
- [Verify](#verify)
- [Tuning without reboot](#tuning-without-reboot)
- [Test matrix](#test-matrix)
- [Compatibility](#compatibility)
- [Limitations](#limitations)
- [Troubleshooting](#troubleshooting)
- [Build from source](#build-from-source)
- [Contributing](#contributing)
- [Credits](#credits)
- [License](#license)

---

## Features

- 🇮🇩 **Indonesian-first voice**: `kamu`/`aku` by default, `lu`/`gw`
  with `style=lu`; street pass (`nggak`, `banget`, `aja`, `udah`, `kalo`)
  behind `slang=1`.
- 🇬🇧 **English relaxer**: safe contractions always on (`I'm`,
  `don't`, `we'll`), casual phrases opt-in (`gonna`, `wanna`, `kinda`).
- 🌍 **Any other language** via custom `Formal=casual` pairs (one per
  line, no rebuild), which always win over built-ins.
- 🛡️ **Polite by default**: formal mail (`dengan hormat`, `hereby`,
  `dear sir`, …) is detected per message and left untouched; links,
  emails and `@handles` are never rewritten.
- 📱 **On-device settings app** (dark) + KernelSU WebUI, no terminal needed
  needed for daily tuning.
- 🔍 **Spy mode**: the hook logs which views carry Gboard text, so new
  Gboard versions can be re-targeted in one iteration (see
  [`docs/SOURCES.md`](docs/SOURCES.md)).

## How it works

Two layers, each doing one job:

```text
Gboard translate --result--> [ LSPosed hook (GboardCasual APK) ] --casual--> chat app
                                     ^ scoped by
                              LSPosed Manager (Gboard only)
                              [ KernelSU module: one-flash installer ]
                              [ + settings app + WebUI Status/Verify/logs ]
```

- **`lsposed-app/` (the actual fix).** Scoped to
  `com.google.android.inputmethod.latin` only. Two nets:
  - *Commit gates*: Gboard funnels every insert (typing and translate
    results alike) through its own helpers, found by disassembling Gboard
    18.3.2: static `ouo.f` / `ouo.h` (text arrives as a parameter) and the
    async `ouf.run` (text rides in field `e`, rewritten via reflection).
    The hook relaxes the text there, so the casual version is what lands
    in chat. These are obfuscated names and can shift on any Gboard
    update. Re-run [Verify](#verify) after big Gboard updates.
  - *Display watch*: `TextView.setText` (what Gboard shows) plus the
    framework `commitText` / `setComposingText` as fallback.
  - The rewrite engine is word-boundary, case-preserving and per-chunk:
    long phrases first (`Anda sekalian` → `kalian`), custom pairs
    longest-first, mid-sentence capitals avoided (`I don't know`, not
    `I Don't know`).
- **`ksu-module/` (companion).** One-flash install of the hook APK, Status
  / Verify / hook-log WebUI, Action button, and `bin/ftune` for reboot-free
  tuning. No props, no HAL writes, no network interception.

## Repository layout

```text
GboardCasual/
├── AGENTS.md                  # agent handoff (conventions, lessons, commands)
├── README.md
├── LICENSE
├── update.json                # OTA info for KernelSU Manager
├── build-deploy.sh            # one-shot: build APK+zip, deploy live (no reboot inside)
├── ksu-module/                # KernelSU companion (installer + verify + WebUI)
│   ├── module.prop customize.sh service.sh post-fs-data.sh action.sh
│   ├── system.prop sepolicy.rule
│   ├── bin/{gboardcasual,verify.sh,ftune}
│   ├── webroot/index.html     # generated: vite build output (git-ignored)
│   ├── webui/                 # Vue 3 + Vite source (HyperDL convention)
│   └── releases/              # built zips (git-ignored)
├── lsposed-app/               # the hook + settings app (built to GboardCasual.apk)
│   ├── AndroidManifest.xml  assets/xposed_init
│   ├── res/{layout,drawable,values, mipmap-anydpi-v26}/  # settings UI + icon
│   ├── src/com/gboardcasual/{Hook,ConfigActivity}.java
│   ├── stub/                  # compile-only Xposed API stubs (never packaged)
│   ├── libs/android.jar       # local only, git-ignored (see build docs)
│   └── build-apk.sh
└── docs/
    └── SOURCES.md             # upstream refs + Gboard commit-gate forensics
```

## Requirements

- Root via **KernelSU** (or fork) with a working **Zygisk** provider
  (ReZygisk / ZygiskNext).
- **LSPosed framework** installed and working (`org.lsposed.manager`).
- **Gboard** (`com.google.android.inputmethod.latin`) as the active
  keyboard, with commit gates verified against **18.3.2**.
- Android 10+ (minSdk 29). arm64.

## Install

> Flashing is gated: without Android 10+, a Zygisk provider, LSPosed, or
> the bundled hook APK, the flash aborts with a clear reason instead of
> leaving a half-working module behind.

1. Flash `GboardCasual-v0.1.0.zip` in the KernelSU Manager. The hook APK
   (`com.gboardcasual`) installs automatically during the flash.
2. **LSPosed Manager → Modules → GboardCasual → enable**, scope: check
   **only Gboard** (`com.google.android.inputmethod.latin`). Never check
   System Framework. (The hook double-checks the package itself, so a
   wrong scope stays idle instead of misfiring.)
3. **Reboot once**, since LSPosed/ART keeps the old dex cached otherwise.
   (APK/dex updates always need this; parameter changes never do.)
4. Open the **GboardCasual** app once and allow root in the KernelSU
   Superuser tab, so Save / Kill Gboard work from the UI.
5. Translate with Gboard in any chat and insert. The casual version lands.

## Settings app

The hook APK is also a launcher app (**GboardCasual**, dark theme). It
reads `/data/local/tmp/gboardcasual.conf` directly and saves through su,
then kills Gboard so the change applies instantly:

| Control | Key |
|---|---|
| Enabled switch | `mode=1/0` |
| Voice (`kamu/aku` vs `lu/gw`) | `style=kamu/lu` |
| Street slang | `slang=0/1` |
| Formal-mail guard + link shield | `auto=1/0` |
| Verbose hook log | `spy=1/0` |
| Custom pairs box (`Formal=casual`, one per line) | any language |
| LLM pass + endpoint + target (reserved) | `llm`, `endpoint`, `target` |
| Save / Refresh / Kill Gboard / **Check root** | on-device actions |

Status line shows `hook=` version, `gboard=` version, `root=ok/denied`
and `conf=live/defaults`. The KernelSU WebUI mirrors Status / Verify /
hook-log for the same module.

## Verify

1. Translate something with Gboard (e.g. test matrix no. 2 below).
2. Run the module Action button, or:

   ```sh
   su -c 'sh /data/adb/modules/gboardcasual/bin/verify.sh'
   ```

3. Confirm the hook lines:

   ```sh
   su -c "grep -h 'GboardCasual' /data/adb/lspd/log/modules_*.log | tail -n 10"
   ```

   Expected: `warming up for com.google.android.inputmethod.latin`,
   `watching commit gates ouo.f/ouo.h`, `hooks live`, plus
   `'formal…' -> 'casual…'` rewrite lines.

## Tuning without reboot

The hook re-reads `/data/local/tmp/gboardcasual.conf` on every Gboard
process start (app sandboxes cannot reach `/data/adb`, hence this path).
Change it via the app, the WebUI-less `ftune`, kill Gboard, translate
again, no reboot, no rebuild:

```sh
su -c '/data/adb/modules/gboardcasual/bin/ftune "mode=1\nstyle=lu\nslang=1"'
su -c '/data/adb/modules/gboardcasual/bin/ftune "mode=1\nstyle=kamu\nslang=0\nVous=tu\nUsted=tú"'
```

| Key | Default | Effect |
|---|---|---|
| `mode` | `1` | `1` / `0` (`off`) master switch |
| `style` | `kamu` | `kamu` (kamu/aku) or `lu` (lu/gw) |
| `slang` | `0` | `1` adds the street pass (`nggak`/`banget`/`gonna`/`wanna`/…) |
| `auto` | `1` | `1` guards formal mail per message + shields links/handles; `0` rewrites everything |
| `spy` | `1` | verbose view/gate logging for re-targeting new Gboard versions |
| `gate` | n/a | escape hatch: `gate=Class:method` lines hook extra commit helpers (CharSequence args rewritten), so a Gboard update that renames `ouo`/`ouf` can be fixed with config alone, no rebuild |
| `llm` | `0` | reserved: asks for the future out-of-band flexible pass (v0.1 still returns the dictionary result instantly and never blocks the keyboard) |
| `endpoint` | empty | reserved: local paraphrase URL for the flexible pass |
| `target` | empty | reserved: target-language hint (e.g. `en`) |
| `Formal=casual` | n/a | custom pair per line, any language, longest-first, wins over built-ins |

## Test matrix

Source Indonesian, target English unless noted (`style=kamu`, `slang=0`
unless stated):

| # | Type | Input (ID) | Server (typical) | Hooked |
|---|---|---|---|---|
| 1 | contraction | `Saya tidak tahu di mana dia berada` | `I do not know where he is` | `I don't know where he's` |
| 2 | contraction | `Anda sangat baik, kami akan segera datang` | `You are very kind, we will come soon` | `You're very kind, we'll come soon` |
| 3 | contraction | `Saya akan pergi ke sana` | `I will go there` | `I'll go there` |
| 4 | slang (`slang=1`) | `Apakah Anda ingin pergi? Terima kasih banyak` | `Do you want to go? Thank you very much` | `Wanna go? Thanks a lot` |
| 5 | guard (unchanged) | `Dengan hormat, mohon maaf… Cek https://example.com/x @budi` | same in, same out | untouched, link/handle intact |
| 6 | server limit | `njir gw ditikung temen…` | often mistranslated upstream | tone softened, meaning as-is |

## Compatibility

Commit gates are verified against **Gboard 18.3** (forensics on 18.3.2,
see [`docs/SOURCES.md`](docs/SOURCES.md)). The settings app reads your
installed Gboard version and reports one of:

| State | Meaning |
|---|---|
| `Supported` | same `major.minor` as tested, gates expected to hold |
| `Unverified` | different Gboard, rewrites may silently stop working |
| `Not installed` | Gboard missing, hook stays idle |

Any Gboard update can rename the obfuscated helpers (`ouo`, `ouf`, `f`,
`h`). After every Gboard update: translate once, then check the hook log
for `gate watch skipped`. If the gates moved, new ones can be attached
without a rebuild via `gate=Class:method` config lines (CharSequence
arguments get rewritten); only async field-carriers like `ouf` still need
a code change.

## Limitations

- **Server still decides meaning.** If Gboard misreads slang input, the
  target sentence may already be wrong before the hook sees it. Workaround:
  type the source a touch cleaner and let the hook casualize the output.
- **No true context model.** This is regex with word boundaries, not an
  LLM, so it cannot tell jokes from office mail. `mode=0` bypasses instantly
  for formal mail.
- **Gboard updates** can rename the obfuscated commit gates; re-run
  [Verify](#verify) afterwards. A `gate watch skipped` log line means the
  forensics in [`docs/SOURCES.md`](docs/SOURCES.md) must be redone against
  the new APK.
- **arm64 only.** Built and tested on arm64.

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| No rewrite, no log | Wrong LSPosed scope (must be Gboard only); reboot after APK update; confirm `hooks live` in log. |
| `gate watch skipped (ouo/ouf)` | Gboard renamed the gates. Redo `docs/SOURCES.md` forensics, update the three names in `Hook.java`. |
| Formal in Gboard bar, casual in chat | Bar is a display cache; the gate rewrote the committed text. Kill Gboard to refresh the bar. |
| Too aggressive (`saja` → `aja` in formal mail) | Set `slang=0`, or `mode=0` temporarily. |
| Save/Kill failed in the app | App lacks root. Allow **GboardCasual** in KernelSU Superuser; status shows `root=denied` until then. |
| `lspd` crash after enabling | Bad APK packaging. Rebuild with `build-apk.sh` (asserts `dex` magic twice). Uninstall APK + reboot to recover the daemon. |

## Build from source

In Termux:

```sh
pkg install d8 aapt2 apksigner openjdk-21 unzip nodejs   # javac via openjdk
cd lsposed-app
mkdir -p libs && cp ~/android-sdk/platforms/android-34/android.jar libs/android.jar
./build-apk.sh GboardCasual.apk        # aapt2 + javac + d8, asserts dex magic, signs
cp -f GboardCasual.apk ../ksu-module/gboardcasual.apk

cd ../ksu-module
./build.sh                             # rebuilds webui if stale, packs the zip
```

`stub/` holds minimal compile-only Xposed signatures (matching
`de.robv.android.xposed`). They are classpath-only and **never packaged**
The real classes come from LSPosed at runtime.

## Contributing

Issues and pull requests are welcome, especially Gboard-update breakage
reports and new language pairs. The full guide lives in
[`docs/CONTRIBUTING.md`](docs/CONTRIBUTING.md): what a good bug report
contains (Gboard version + hook log + one test sentence), PR rules, and
the test checklist.

## Credits

- Hook patterns and module conventions adapted from the author's
  [VideoModeFix](https://github.com/itswill00/VideoModeFix) template.
- Gboard commit-gate forensics: apktool decode of Gboard 18.3.2 (see
  [`docs/SOURCES.md`](docs/SOURCES.md)).
- In-app credit: `@noticesa` (Telegram).

## License

[MIT](LICENSE).
