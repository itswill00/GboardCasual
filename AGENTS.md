# GboardCasual: agent handoff

Read this first in any new session. Project facts, device truth, user rules,
and where things stand.

## User rules (standing, do not relax without asking)

- Project language is **full English**, public-friendly. No Indonesian in
  code, comments, README, or WebUI/app UI. Chat with the user in casual Indonesian.
- Module author is **itswill00** everywhere. In-app footer credit reads
  `by @noticesa · Telegram` (user's own handle, keep it).
- **No version bumps** until a real change exists. **No `git push`, no
  release tags, no release-asset updates without explicit user confirmation.**
- Audit before every git operation: names, English, stale refs, syntax
  (`sh -n`), build artifacts. Commits need signoff + Change-Id when the
  user asks for a squash.
- Code comments: human voice, never numbered lists (`# 1. …`), never AI slop.
- WebUI structure must mirror HyperDL/HyperCore: `webui/` Vue 3 + Vite +
  singlefile → `webroot/index.html`; `src/{App.vue,main.js,assets,components,helpers}`;
  `shell.js` bridge in the `ksu.exec(cmd, '{}', id)` callback style.
- **Never reboot / soft-reboot the user's device unilaterally.** Hand over
  the command (`su -c 'killall system_server'` or full reboot) and let the
  user run it when ready. (Lesson learned the hard way.)
- App settings UI is dark-only (`#121212`, light text). The user hates
  light themes.
- Think flexibly: gate installs with `abort`, consider all situations
  (missing deps, disabled modules, conflicting providers).

## What this project is

Casual-tone restyler for Gboard Translate output, aimed at Indonesian
speakers chatting outward. An LSPosed Java hook (`lsposed-app/`, package
`com.gboardcasual`) plus a KernelSU companion (`ksu-module/`, id
`gboardcasual`) that one-flash installs the bundled APK and offers Status /
Verify / hook-log WebUI. The hook APK is also a launcher app with a full
settings screen (dark). Repo: `https://github.com/itswill00/GboardCasual` (MIT).

## How the hook works (proven)

- Generic nets: `TextView.setText` (display), framework
  `commitText`/`setComposingText` (fallback), `WebView.loadData*` (spy only).
- Real interception: Gboard commit gates found by apktool forensics on
  Gboard 18.3.2: `ouo.f`/`ouo.h` (static, text is a parameter) and async
  `ouf.run` (text in field `e`, rewritten via reflection). See
  `docs/SOURCES.md`. Obfuscated names can shift per Gboard update.
- Rewrite engine (`Hook.java`): word-boundary, case-preserving, per-chunk.
  ID base (`Anda→kamu`, `Saya→aku`, `style=lu` for `lu`/`gw`), ID long
  phrases, EN contractions always on, EN slang + ID street pass behind
  `slang=1`, `sWorld` extras, custom `Formal=casual` pairs win. `auto=1`
  skips formal-mail sentences and shields links/handles. `gate=Class:method`
  conf lines attach extra commit helpers without a rebuild. `llm`/`endpoint`/
  `target` keys are reserved for a future out-of-band flexible pass. v0.1
  never blocks the keyboard with network calls.
- Settings app (dark cards) and WebUI both show Status + Gboard
  compatibility (`SUPPORTED`/`UNVERIFIED`/`NOT INSTALLED` vs tested 18.3).

## Hard lessons (do not rediscover)

- The hook long fired on the **query box only** (`EditTextOnKeyboard`
  never overrides `setText`) while results flowed untouched, so always verify
  against the committed text, not the log count.
- `rewriteSeq` must ignore whitespace-only inputs or the log budget drowns
  in `' ' -> ''` noise.
- EN replacements are stored lowercase-first; `matchCase` lifts the first
  letter on sentence starts (avoids mid-sentence `Don't` capitals; `I'm`
  keeps its capital by rule of English).
- App processes **cannot** write `/data/local/tmp` freely and other apps
  cannot read `/data/adb`, so live config lives at
  `/data/local/tmp/gboardcasual.conf` (read directly, written via su).
- The settings app needs a KernelSU superuser grant or Save/Kill fail. Status shows
  surface it as `root=ok/denied` in status, never a bare "failed".
- Only `XposedBridge.log` + `hookAllMethods` are safe on obfuscated LSPosed
  runtimes; never package `classes.zip` as `classes.dex` (segfaults
  `ObfuscationManager` and kills `lspd`. Rebuild with `build-apk.sh`).
- APK/dex updates need a (soft) reboot, but only the user triggers it.

## Current state

- `main` should be a clean tree: sources only (APKs, dex, keystore,
  `libs/android.jar`, `webroot/index.html`, `releases/`, `node_modules/`
  are all git-ignored). No release published yet; `update.json` points at
  the future `v0.1.0` asset path.
- Device truth: Redmi 24117RN76O, Helio G99, Android 15 (HyperOS),
  KernelSU + ReZygisk + LSPosed, Gboard 18.3.2. Hooks verified live in
  `/data/adb/lspd/log/modules_*.log`.

## Commands

- Full pipeline: `./build-deploy.sh -d` (build APK+zip, pm install, sync
  module, no reboot inside) from repo root.
- Reboot-free tune: `su -c '/data/adb/modules/gboardcasual/bin/ftune "mode=1\nstyle=lu\nslang=1"'`
  then kill Gboard (or the in-app Save button, which does both via su).
- Verify: Action button or `bin/verify.sh`; hook log:
  `grep -h GboardCasual /data/adb/lspd/log/modules_*.log`
