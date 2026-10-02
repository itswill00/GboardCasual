package com.gboardcasual;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// GboardCasual hook - loosens Gboard Translate output into everyday speech.
// Gboard calls home for the real translation so the server reply stays
// stiff. This hook rewrites the text Gboard is about to show or commit,
// one sentence at a time: formal mail is left alone, chat gets relaxed,
// links and handles are never touched. Indonesian is the first-class voice
// (kamu/aku or lu/gw). English gets safe contractions plus an opt-in slang
// pass. Every other language rides on custom pairs so no tongue is locked out.
public class Hook implements IXposedHookLoadPackage {
  static final String TAG = "GboardCasual";
  static final String CONF = "/data/local/tmp/gboardcasual.conf";
  static final String TARGET = "com.google.android.inputmethod.latin";

  static boolean cfgEnabled = true;
  static String cfgStyle = "kamu";
  static boolean cfgSlang = false;
  static boolean cfgAuto = true;
  static boolean cfgSpy = true;
  static final java.util.List<String> sGateSpecs = new java.util.ArrayList<String>();
  static final String TESTED_GBOARD = "18.3";
  static boolean cfgLlm = false;
  static String cfgEndpoint = "";
  static String cfgTarget = "";

  static final Map<String, String> sBase = new LinkedHashMap<String, String>();
  static final Map<String, String> sSlang = new LinkedHashMap<String, String>();
  static final Map<String, String> sCustom = new LinkedHashMap<String, String>();
  static final Map<String, String> sEnBase = new LinkedHashMap<String, String>();
  static final Map<String, String> sEnSlang = new LinkedHashMap<String, String>();
  static final Map<String, String> sWorld = new LinkedHashMap<String, String>();
  static final Map<String, String> sIdLong = new LinkedHashMap<String, String>();
  static final Map<String, String> sEnTrim = new LinkedHashMap<String, String>();

  // Replacements are stored lowercase-first on purpose. matchCase lifts the
  // first letter when the hit starts a sentence, so mid-sentence stays low.
  // Only the I-led forms keep their capital because English demands it.
  static {
    sIdLong.put("anda sekalian", "kalian");
    sIdLong.put("anda semua", "kalian");
    sIdLong.put("tidak perlu", "nggak usah");
    sIdLong.put("tidak usah", "nggak usah");
    sIdLong.put("terima kasih banyak", "makasih banyak");
    sIdLong.put("bagaimana kabar", "gimana kabar");
    sIdLong.put("sampai jumpa", "dadah");
    sIdLong.put("selamat pagi", "pagi");
    sIdLong.put("selamat malam", "malem");
    sBase.put("Anda", "kamu");
    sBase.put("Saya", "aku");
    sBase.put("Saudara", "kamu");
    sSlang.put("tidak", "nggak");
    sSlang.put("sangat", "banget");
    sSlang.put("saja", "aja");
    sSlang.put("sudah", "udah");
    sSlang.put("telah", "udah");
    sSlang.put("akan", "bakal");
    sSlang.put("mengapa", "kenapa");
    sSlang.put("apabila", "kalo");
    sSlang.put("kalau", "kalo");
    sEnTrim.put("i am writing to inform you that", "");
    sEnTrim.put("please kindly", "please");
    sEnTrim.put("thank you for your attention", "thanks");
    sEnTrim.put("do not hesitate to", "feel free to");
    sEnBase.put("i am", "I'm");
    sEnBase.put("you are", "you're");
    sEnBase.put("we are", "we're");
    sEnBase.put("they are", "they're");
    sEnBase.put("he is", "he's");
    sEnBase.put("she is", "she's");
    sEnBase.put("it is", "it's");
    sEnBase.put("that is", "that's");
    sEnBase.put("there is", "there's");
    sEnBase.put("what is", "what's");
    sEnBase.put("where is", "where's");
    sEnBase.put("who is", "who's");
    sEnBase.put("do not", "don't");
    sEnBase.put("does not", "doesn't");
    sEnBase.put("did not", "didn't");
    sEnBase.put("cannot", "can't");
    sEnBase.put("could not", "couldn't");
    sEnBase.put("should not", "shouldn't");
    sEnBase.put("i will", "I'll");
    sEnBase.put("you will", "you'll");
    sEnBase.put("we will", "we'll");
    sEnBase.put("i have", "I've");
    sEnBase.put("you have", "you've");
    sEnBase.put("let us", "let's");
    sEnSlang.put("would you like to", "wanna");
    sEnSlang.put("i would like to", "i wanna");
    sEnSlang.put("do you want to", "wanna");
    sEnSlang.put("going to", "gonna");
    sEnSlang.put("got to", "gotta");
    sEnSlang.put("want to", "wanna");
    sEnSlang.put("kind of", "kinda");
    sEnSlang.put("sort of", "sorta");
    sEnSlang.put("how are you", "how're you");
    sEnSlang.put("thank you very much", "thanks a lot");
    sEnSlang.put("hello", "hey");
    // polite second person in other tongues, kept tight on purpose.
    // German Sie stays out: case alone tells Sie apart and this pass is blind to it.
    sWorld.put("Usted", "tu");
  }

  static final Pattern PROTECT =
      Pattern.compile("(https?://\\S+|www\\.\\S+|[\\w.+-]+@[\\w-]+\\.[\\w.]+|@[A-Za-z0-9_]+)");
  static final String[] FORMAL_MARKS = {
    "dengan hormat", "mohon maaf", "terima kasih atas perhatian",
    "hormat saya", "hereby", "please kindly", "dear sir", "dear madam"
  };

  static int sRewrites = 0;

  static void readConfig() {
    cfgEnabled = true;
    cfgStyle = "kamu";
    cfgSlang = false;
    cfgAuto = true;
    cfgSpy = true;
    sGateSpecs.clear();
    cfgLlm = false;
    cfgEndpoint = "";
    cfgTarget = "";
    sCustom.clear();
    try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(CONF))) {
      String line;
      while ((line = br.readLine()) != null) {
        line = line.trim();
        if (line.isEmpty() || line.startsWith("#")) continue;
        if (line.startsWith("mode=")) {
          String v = line.substring(5).trim();
          cfgEnabled = !(v.equals("0") || v.equalsIgnoreCase("off"));
        } else if (line.startsWith("style=")) {
          String v = line.substring(6).trim().toLowerCase();
          if (v.equals("lu") || v.equals("kamu")) cfgStyle = v;
        } else if (line.startsWith("slang=")) {
          cfgSlang = line.endsWith("1");
        } else if (line.startsWith("auto=")) {
          cfgAuto = !line.endsWith("0");
        } else if (line.startsWith("spy=")) {
          cfgSpy = !line.endsWith("0");
        } else if (line.startsWith("gate=")) {
          // escape hatch for Gboard updates: Class:method pairs whose
          // CharSequence args get rewritten, no rebuild needed.
          String spec = line.substring(5).trim();
          if (spec.contains(":") && sGateSpecs.size() < 20
              && !sGateSpecs.contains(spec)) {
            sGateSpecs.add(spec);
          }
        } else if (line.startsWith("llm=")) {
          cfgLlm = line.endsWith("1");
        } else if (line.startsWith("endpoint=")) {
          cfgEndpoint = line.substring(9).trim();
        } else if (line.startsWith("target=")) {
          cfgTarget = line.substring(7).trim();
        } else if (line.contains("=")) {
          int eq = line.indexOf('=');
          String k = line.substring(0, eq).trim();
          String v = line.substring(eq + 1).trim();
          if (!k.isEmpty() && !v.isEmpty()
              && !k.equals("mode") && !k.equals("style") && !k.equals("slang")
              && !k.equals("llm") && !k.equals("endpoint") && !k.equals("target")
              && !k.equals("auto") && !k.equals("gate")) {
            sCustom.put(k, v);
          }
        }
      }
    } catch (Throwable ignored) {
      // missing file just means defaults
    }
  }

  static String matchCase(String template, String replacement) {
    if (template.equals(template.toUpperCase()) && template.length() > 1) {
      return replacement.toUpperCase();
    }
    if (Character.isUpperCase(template.charAt(0))) {
      return Character.toUpperCase(replacement.charAt(0)) + replacement.substring(1);
    }
    return replacement;
  }

  static String swapOne(String input, String formal, String casual) {
    try {
      Pattern p = Pattern.compile("\\b" + Pattern.quote(formal) + "\\b",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
      Matcher m = p.matcher(input);
      StringBuffer sb = new StringBuffer();
      boolean hit = false;
      while (m.find()) {
        hit = true;
        m.appendReplacement(sb, Matcher.quoteReplacement(matchCase(m.group(), casual)));
      }
      if (!hit) return input;
      m.appendTail(sb);
      return sb.toString();
    } catch (Throwable t) {
      return input;
    }
  }

  static String styleTarget(String formalDefault) {
    if (cfgStyle.equals("lu")) {
      if (formalDefault.equalsIgnoreCase("kamu")) return "lu";
      if (formalDefault.equalsIgnoreCase("aku")) return "gw";
    }
    return formalDefault;
  }

  static boolean looksFormal(String sentence) {
    String low = sentence.toLowerCase();
    for (String mark : FORMAL_MARKS) {
      if (low.contains(mark)) return true;
    }
    return false;
  }

  static String preview(String s) {
    if (s == null) return "null";
    String oneLine = s.replace('\n', ' ').replace('\r', ' ');
    if (oneLine.length() > 80) return oneLine.substring(0, 80) + "...";
    return oneLine;
  }

  static final java.util.Set<String> sSeenViews = new java.util.HashSet<String>();

  static void spyView(Object viewObj, CharSequence text) {
    if (!cfgSpy || viewObj == null) return;
    try {
      String cls = viewObj.getClass().getName();
      if (sSeenViews.size() < 60 && sSeenViews.add(cls)) {
        String t = text == null ? "null" : preview(text.toString());
        XposedBridge.log(TAG + ": view[" + sSeenViews.size() + "] " + cls + " <- '" + t + "'");
      }
    } catch (Throwable ignored) {
      // spying never breaks the keyboard
    }
  }
  static String tidySpaces(String s) {
    String out = s.replaceAll("[ \\t]{2,}", " ").replaceAll(" \\.", ".").trim();
    return out.replaceAll("\\.(\\S)", ". $1");
  }

  static String relaxChunk(String chunk) {
    String out = chunk;
    java.util.List<Map.Entry<String, String>> customs =
        new java.util.ArrayList<Map.Entry<String, String>>(sCustom.entrySet());
    java.util.Collections.sort(customs, new java.util.Comparator<Map.Entry<String, String>>() {
      @Override public int compare(Map.Entry<String, String> a, Map.Entry<String, String> b) {
        return b.getKey().length() - a.getKey().length();
      }
    });
    for (Map.Entry<String, String> e : customs) {
      out = swapOne(out, e.getKey(), e.getValue());
    }
    if (cfgAuto && looksFormal(out)) return out;
    for (Map.Entry<String, String> e : sIdLong.entrySet()) {
      out = swapOne(out, e.getKey(), e.getValue());
    }
    for (Map.Entry<String, String> e : sBase.entrySet()) {
      out = swapOne(out, e.getKey(), styleTarget(e.getValue()));
    }
    if (cfgStyle.equals("lu")) {
      out = swapOne(out, "kamu", "lu");
      out = swapOne(out, "aku", "gw");
    }
    for (Map.Entry<String, String> e : sEnTrim.entrySet()) {
      out = swapOne(out, e.getKey(), e.getValue());
    }
    for (Map.Entry<String, String> e : sEnBase.entrySet()) {
      out = swapOne(out, e.getKey(), e.getValue());
    }
    for (Map.Entry<String, String> e : sWorld.entrySet()) {
      out = swapOne(out, e.getKey(), e.getValue());
    }
    if (cfgSlang) {
      for (Map.Entry<String, String> e : sSlang.entrySet()) {
        out = swapOne(out, e.getKey(), e.getValue());
      }
      for (Map.Entry<String, String> e : sEnSlang.entrySet()) {
        out = swapOne(out, e.getKey(), e.getValue());
      }
    }
    return out;
  }

  public static String rewrite(String input) {
    if (input == null || input.isEmpty() || !cfgEnabled) return input;
    // links, mails and handles pass through untouched
    Matcher guard = PROTECT.matcher(input);
    StringBuilder done = new StringBuilder();
    int at = 0;
    while (guard.find()) {
      done.append(relaxChunk(input.substring(at, guard.start())));
      done.append(guard.group());
      at = guard.end();
    }
    done.append(relaxChunk(input.substring(at)));
    return tidySpaces(done.toString());
  }

  static CharSequence rewriteSeq(CharSequence seq) {
    if (seq == null || !cfgEnabled) return seq;
    String s = seq.toString();
    if (s.trim().isEmpty()) return seq;
    String fixed = rewrite(s);
    if (fixed.equals(s) || fixed.trim().isEmpty()) return seq;
    sRewrites++;
    XposedBridge.log(TAG + ": '" + preview(s) + "' -> '" + preview(fixed) + "'");
    return fixed;
  }

  static void hookTextView(Method hookAll) {
    try {
      final Class<?> tv = Class.forName("android.widget.TextView");
      hookAll.invoke(null, tv, "setText", new XC_MethodHook() {
        @Override protected void beforeHookedMethod(MethodHookParam p) {
          try {
            if (p.args != null && p.args.length > 0 && p.args[0] instanceof CharSequence) {
              spyView(p.thisObject, (CharSequence) p.args[0]);
              CharSequence fixed = rewriteSeq((CharSequence) p.args[0]);
              if (fixed != p.args[0]) p.args[0] = fixed;
            } else {
              spyView(p.thisObject, null);
            }
          } catch (Throwable t) {
            XposedBridge.log(TAG + ": TextView hook trouble: " + t.getMessage());
          }
        }
      });
      XposedBridge.log(TAG + ": watching TextView.setText");
    } catch (Throwable t) {
      XposedBridge.log(TAG + ": TextView watch skipped: " + t.getMessage());
    }
  }

  static void hookWebView(Method hookAll) {
    // Gboard Translate may render results in a WebView instead of TextViews.
    // Read-only watch: log that web content flowed, never alter it here.
    String[] methods = {"loadData", "loadDataWithBaseURL"};
    try {
      final Class<?> wv = Class.forName("android.webkit.WebView");
      for (String m : methods) {
        try {
          hookAll.invoke(null, wv, m, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
              try {
                if (!cfgSpy) return;
                String data = (p.args != null && p.args.length > 0) ? String.valueOf(p.args[0]) : "";
                XposedBridge.log(TAG + ": web[" + p.method.getName() + "] len=" + data.length());
              } catch (Throwable ignored) {
                // spying never breaks the keyboard
              }
            }
          });
        } catch (Throwable ignored) {
          // overload set differs per ROM, one working watch is enough
        }
      }
      XposedBridge.log(TAG + ": watching WebView loads");
    } catch (Throwable t) {
      XposedBridge.log(TAG + ": WebView watch skipped: " + t.getMessage());
    }
  }

  static void rewriteCharSequenceArgs(Object[] args) {
    if (args == null) return;
    for (int i = 0; i < args.length; i++) {
      if (args[i] instanceof CharSequence) {
        CharSequence fixed = rewriteSeq((CharSequence) args[i]);
        if (fixed != args[i]) args[i] = fixed;
      }
    }
  }

  static void hookCommitGates(Method hookAll) {
    // Gboard funnels every commit (typing AND translate inserts) through two
    // helpers found in its own code: ouo.f/h take the text as a parameter,
    // ouf carries it in field e and posts to a handler. Hooking the gates
    // beats chasing the framework connection object underneath.
    try {
      final Class<?> ouo = Class.forName("ouo");
      XC_MethodHook gate = new XC_MethodHook() {
        @Override protected void beforeHookedMethod(MethodHookParam p) {
          try {
            rewriteCharSequenceArgs(p.args);
          } catch (Throwable t) {
            XposedBridge.log(TAG + ": gate hook trouble: " + t.getMessage());
          }
        }
      };
      hookAll.invoke(null, ouo, "f", gate);
      hookAll.invoke(null, ouo, "h", gate);
      XposedBridge.log(TAG + ": watching commit gates ouo.f/ouo.h");
    } catch (Throwable t) {
      XposedBridge.log(TAG + ": gate watch skipped (ouo): " + t.getMessage());
    }
    try {
      final Class<?> ouf = Class.forName("ouf");
      hookAll.invoke(null, ouf, "run", new XC_MethodHook() {
        @Override protected void beforeHookedMethod(MethodHookParam p) {
          try {
            for (java.lang.reflect.Field f : p.thisObject.getClass().getDeclaredFields()) {
              if (CharSequence.class.isAssignableFrom(f.getType())) {
                f.setAccessible(true);
                Object v = f.get(p.thisObject);
                if (v instanceof CharSequence) {
                  CharSequence fixed = rewriteSeq((CharSequence) v);
                  if (fixed != v) f.set(p.thisObject, fixed);
                }
              }
            }
          } catch (Throwable t) {
            XposedBridge.log(TAG + ": async gate trouble: " + t.getMessage());
          }
        }
      });
      XposedBridge.log(TAG + ": watching async gate ouf.run");
    } catch (Throwable t) {
      XposedBridge.log(TAG + ": gate watch skipped (ouf): " + t.getMessage());
    }
    for (String spec : sGateSpecs) {
      try {
        int sep = spec.indexOf(':');
        final Class<?> cls = Class.forName(spec.substring(0, sep).trim());
        String method = spec.substring(sep + 1).trim();
        if (method.isEmpty()) continue;
        hookAll.invoke(null, cls, method, new XC_MethodHook() {
          @Override protected void beforeHookedMethod(MethodHookParam p) {
            try {
              rewriteCharSequenceArgs(p.args);
            } catch (Throwable t) {
              XposedBridge.log(TAG + ": custom gate trouble: " + t.getMessage());
            }
          }
        });
        XposedBridge.log(TAG + ": watching custom gate " + spec);
      } catch (Throwable t) {
        XposedBridge.log(TAG + ": custom gate skipped (" + spec + "): " + t.getMessage());
      }
    }
  }
  static void hookInputConnection(Method hookAll, String className, String method) {
    try {
      final Class<?> cls = Class.forName(className);
      hookAll.invoke(null, cls, method, new XC_MethodHook() {
        @Override protected void beforeHookedMethod(MethodHookParam p) {
          try {
            if (p.args != null && p.args.length > 0 && p.args[0] instanceof CharSequence) {
              CharSequence fixed = rewriteSeq((CharSequence) p.args[0]);
              if (fixed != p.args[0]) p.args[0] = fixed;
            }
          } catch (Throwable t) {
            XposedBridge.log(TAG + ": commit hook trouble: " + t.getMessage());
          }
        }
      });
    } catch (Throwable t) {
      XposedBridge.log(TAG + ": commit watch skipped (" + className + "#" + method + "): " + t.getMessage());
    }
  }

  @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
    if (!TARGET.equals(lp.packageName)) return;
    readConfig();
    sRewrites = 0;
    XposedBridge.log(TAG + ": warming up for " + lp.packageName
        + " style=" + cfgStyle + " slang=" + (cfgSlang ? 1 : 0)
        + " auto=" + (cfgAuto ? 1 : 0) + " spy=" + (cfgSpy ? 1 : 0)
        + " llm=" + (cfgLlm ? 1 : 0) + " customs=" + sCustom.size()
        + " gates=" + sGateSpecs.size());
    if (cfgLlm) {
      // v0.1 stays dictionary-only on purpose: a blocking network call inside
      // commitText would stall the keyboard. The llm/target/endpoint keys are
      // reserved so the flexible pass can ride out-of-band next.
      XposedBridge.log(TAG + ": llm pass requested, holding dictionary result");
    }
    try {
      Method hookAll = XposedBridge.class.getMethod("hookAllMethods",
          Class.class, String.class, XC_MethodHook.class);
      hookTextView(hookAll);
      hookCommitGates(hookAll);
      hookWebView(hookAll);
      hookInputConnection(hookAll,
          "android.view.inputmethod.BaseInputConnection", "commitText");
      hookInputConnection(hookAll,
          "android.view.inputmethod.BaseInputConnection", "setComposingText");
      hookInputConnection(hookAll,
          "com.android.internal.view.InputConnectionWrapper", "commitText");
      hookInputConnection(hookAll,
          "com.android.internal.view.InputConnectionWrapper", "setComposingText");
      XposedBridge.log(TAG + ": hooks live for " + lp.packageName);
    } catch (Throwable t) {
      XposedBridge.log(t);
    }
  }
}
