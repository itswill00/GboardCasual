# Sources & forensics

Upstream references consulted while building GboardCasual. The interesting
part is not the list but the commit-gate finding at the bottom.

## Framework & tooling

- [`LSPosed/LSPosed`](https://github.com/LSPosed/LSPosed): the hook
  runtime. Only `XposedBridge.log` + `hookAllMethods` are used; on some
  obfuscated runtimes `hookMethod(Member, …)` does not exist.
- [`topjohnwu/zygisk-module-sample`](https://github.com/topjohnwu/zygisk-module-sample)
  , a companion-module pattern reference (via the VideoModeFix template).
- [`Sable/android-platforms`](https://github.com/Sable/android-platforms):
  `android.jar` used for manual builds.
- `apktool 3.0.3` (Termux package): Gboard disassembly below.

## Gboard 18.3.2 forensics (the commit gates)

Target: `com.google.android.inputmethod.latin` 18.3.2
(`base.apk`, 4 dex files). Decoded with `apktool d -r` (smali only).

Named translate classes (kept through obfuscation):

| Class | Role |
|---|---|
| `…libs.translate.TranslateKeyboard` | Translate keyboard controller, owns the query box |
| `…libs.translate.TranslateLanguageBar` | Result/language bar (`FrameLayout`) |
| `…libs.translate.SystemTranslateProvider` | System translation backend |
| `…libraries.inputmethod.widgets.EditTextOnKeyboard` | Query box (plain `EditText` subclass, no `setText` override, which is why the generic `TextView.setText` hook fires on input) |

Commit-path finding: only **two** files in the whole APK invoke
`InputConnection.commitText`, so every insert (typing and translate)
funnels through them:

| Gate | Shape | Hook |
|---|---|---|
| `ouo.f(InputConnection, CharSequence, int)` | static commit helper | rewrite text arg |
| `ouo.h(InputConnection, CharSequence, int, Object)` | static composing helper | rewrite text arg |
| `ouf.run()` (field `e: CharSequence`) | async posted commit | rewrite field via reflection |

Supporting chain: `ozc` (Gboard's `InputMethodService`) does **not**
override `getCurrentInputConnection`, and `ouq.d()` delegates to the
`oum` facade, so the framework `InputConnectionWrapper` object is still
underneath, but hooking it never fired in practice. The gates above are
the reliable interception point.

Caveat: `ouo` / `ouf` / `f` / `h` are obfuscated names and can shift on
any Gboard update. If the hook log ever shows `gate watch skipped`, redo
this decode against the new APK and update the three names.
