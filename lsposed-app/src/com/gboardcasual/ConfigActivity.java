package com.gboardcasual;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

// On-device settings screen. Reads the live config directly (world-readable)
// and writes it back through su, because the file lives outside app storage.
public class ConfigActivity extends Activity {
  static final String CONF = "/data/local/tmp/gboardcasual.conf";
  static final String GBOARD = "com.google.android.inputmethod.latin";

  TextView status, compat;
  Switch swEnabled, swSlang, swAuto, swSpy, swLlm;
  RadioGroup rgStyle;
  RadioButton rbKamu, rbLu;
  EditText etCustom, etEndpoint, etTarget;

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getWindow().setStatusBarColor(0xFF121212);
    getWindow().setNavigationBarColor(0xFF121212);
    setContentView(R.layout.activity_config);
    status = findViewById(R.id.status);
    compat = findViewById(R.id.compat);
    swEnabled = findViewById(R.id.sw_enabled);
    rgStyle = findViewById(R.id.rg_style);
    rbKamu = findViewById(R.id.rb_kamu);
    rbLu = findViewById(R.id.rb_lu);
    swSlang = findViewById(R.id.sw_slang);
    swAuto = findViewById(R.id.sw_auto);
    swSpy = findViewById(R.id.sw_spy);
    swLlm = findViewById(R.id.sw_llm);
    etCustom = findViewById(R.id.et_custom);
    etEndpoint = findViewById(R.id.et_endpoint);
    etTarget = findViewById(R.id.et_target);

    findViewById(R.id.btn_save).setOnClickListener(new View.OnClickListener() {
      @Override public void onClick(View v) { saveAsync(); }
    });
    findViewById(R.id.btn_refresh).setOnClickListener(new View.OnClickListener() {
      @Override public void onClick(View v) { loadAsync(); }
    });
    findViewById(R.id.btn_kill).setOnClickListener(new View.OnClickListener() {
      @Override public void onClick(View v) { killAsync(); }
    });
    findViewById(R.id.btn_root).setOnClickListener(new View.OnClickListener() {
      @Override public void onClick(View v) { rootAsync(); }
    });
    loadAsync();
  }

  static boolean isKnownKey(String k) {
    return k.equals("mode") || k.equals("style") || k.equals("slang")
        || k.equals("auto") || k.equals("spy") || k.equals("llm")
        || k.equals("endpoint") || k.equals("target") || k.equals("gate");
  }

  static String shortVer(String v) {
    if (v == null) return "?";
    java.util.regex.Matcher m =
        java.util.regex.Pattern.compile("^(\\d+)\\.(\\d+)").matcher(v);
    if (m.find()) return m.group(1) + "." + m.group(2);
    return "?";
  }

  static String compatState() {
    if (sGboardVer.equals("?") || sGboardVer.equals("missing")) return "Not installed";
    if (shortVer(sGboardVer).equals(TESTED_GBOARD)) return "Supported";
    return "Unverified";
  }

  void loadAsync() {
    status.setText("loading...");
    new Thread(new Runnable() {
      @Override public void run() {
        boolean enabled = true;
        String style = "kamu";
        boolean slang = false, auto = true, spy = true, llm = false;
        String endpoint = "", target = "";
        final List<String> customs = new ArrayList<String>();
        try (BufferedReader br = new BufferedReader(new FileReader(CONF))) {
          String line;
          while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.startsWith("mode=")) {
              String v = line.substring(5).trim();
              enabled = !(v.equals("0") || v.equalsIgnoreCase("off"));
            } else if (line.startsWith("style=")) {
              style = line.substring(6).trim().toLowerCase();
            } else if (line.startsWith("slang=")) {
              slang = line.endsWith("1");
            } else if (line.startsWith("auto=")) {
              auto = !line.endsWith("0");
            } else if (line.startsWith("spy=")) {
              spy = !line.endsWith("0");
            } else if (line.startsWith("llm=")) {
              llm = line.endsWith("1");
            } else if (line.startsWith("endpoint=")) {
              endpoint = line.substring(9).trim();
            } else if (line.startsWith("target=")) {
              target = line.substring(7).trim();
            } else if (line.contains("=")) {
              customs.add(line);
            }
          }
        } catch (Throwable ignored) {
          // missing file just means defaults
        }
        final boolean fEnabled = enabled, fSlang = slang, fAuto = auto;
        final boolean fSpy = spy, fLlm = llm;
        final String fStyle = style, fEndpoint = endpoint, fTarget = target;
        StringBuilder probe = new StringBuilder();
        int rc = execSu("id", probe);
        sRootState = (rc == 0 && probe.toString().contains("uid=0")) ? "ok" : "denied";
        runOnUiThread(new Runnable() {
          @Override public void run() {
            swEnabled.setChecked(fEnabled);
            if (fStyle.equals("lu")) rbLu.setChecked(true);
            else rbKamu.setChecked(true);
            swSlang.setChecked(fSlang);
            swAuto.setChecked(fAuto);
            swSpy.setChecked(fSpy);
            swLlm.setChecked(fLlm);
            etEndpoint.setText(fEndpoint);
            etTarget.setText(fTarget);
            StringBuilder sb = new StringBuilder();
            for (String c : customs) sb.append(c).append('\n');
            etCustom.setText(sb.toString().trim());
            status.setText(statusLine());
            String cs = compatState();
            if (cs.equals("Supported")) {
              compat.setText("Gboard " + shortVer(sGboardVer)
                  + ", Supported (gates tested on " + TESTED_GBOARD + ")");
            } else if (cs.equals("Not installed")) {
              compat.setText("Gboard not installed, hook stays idle");
            } else {
              compat.setText("Gboard " + shortVer(sGboardVer)
                  + ", Unverified (tested on " + TESTED_GBOARD
                  + "). Rewrites may stop; check the hook log.");
            }
          }
        });
      }
    }).start();
  }

  String statusLine() {
    String hook = "?";
    try {
      PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
      hook = pi.versionName;
    } catch (PackageManager.NameNotFoundException ignored) {
      // label stays unknown
    }
    String gboard = "missing";
    try {
      PackageInfo pi = getPackageManager().getPackageInfo(GBOARD, 0);
      gboard = String.valueOf(pi.versionName);
      sGboardVer = gboard;
      if (gboard.length() > 12) gboard = gboard.substring(0, 12) + "...";
    } catch (PackageManager.NameNotFoundException ignored) {
      // Gboard not installed, hook stays idle
      sGboardVer = "missing";
    }
    File f = new File(CONF);
    String conf = f.exists() ? "conf=live" : "conf=defaults";
    return "hook=" + hook + " gboard=" + gboard + " root=" + sRootState + " " + conf;
  }

  String collectConf() {
    StringBuilder sb = new StringBuilder();
    sb.append("# GboardCasual live config (written by the settings app)\n");
    sb.append("mode=").append(swEnabled.isChecked() ? 1 : 0).append('\n');
    int checked = rgStyle.getCheckedRadioButtonId();
    sb.append("style=").append(checked == R.id.rb_lu ? "lu" : "kamu").append('\n');
    sb.append("slang=").append(swSlang.isChecked() ? 1 : 0).append('\n');
    sb.append("auto=").append(swAuto.isChecked() ? 1 : 0).append('\n');
    sb.append("spy=").append(swSpy.isChecked() ? 1 : 0).append('\n');
    sb.append("llm=").append(swLlm.isChecked() ? 1 : 0).append('\n');
    String endpoint = etEndpoint.getText().toString().trim();
    String target = etTarget.getText().toString().trim();
    if (!endpoint.isEmpty()) sb.append("endpoint=").append(endpoint).append('\n');
    if (!target.isEmpty()) sb.append("target=").append(target).append('\n');
    for (String line : etCustom.getText().toString().split("\n")) {
      line = line.trim();
      if (line.isEmpty() || line.startsWith("#")) continue;
      if (!line.contains("=")) continue;
      if (line.startsWith("gate=")) {
        // escape hatch survives round-trips through the box
        if (line.contains(":") && line.length() < 80) sb.append(line).append('\n');
        continue;
      }
      String k = line.substring(0, line.indexOf('=')).trim();
      if (!k.isEmpty() && !isKnownKey(k)) sb.append(line).append('\n');
    }
    return sb.toString();
  }

  static String sRootState = "?";
  static String sGboardVer = "?";
  static final String TESTED_GBOARD = "18.3";

  static int execSu(String cmd, StringBuilder out) {
    try {
      ProcessBuilder pb = new ProcessBuilder("su", "-c", cmd);
      pb.redirectErrorStream(true);
      Process p = pb.start();
      byte[] buf = new byte[4096];
      int n;
      java.io.InputStream in = p.getInputStream();
      while ((n = in.read(buf)) > 0 && out.length() < 2000) {
        out.append(new String(buf, 0, n));
      }
      p.waitFor();
      return p.exitValue();
    } catch (Throwable t) {
      out.append(t.getMessage());
      return 99;
    }
  }

  static String failHint(StringBuilder out) {
    String s = out.toString().trim().toLowerCase();
    if (s.isEmpty() || s.contains("denied") || s.contains("permission")
        || s.contains("not allowed") || s.contains("unauthorized")) {
      return "root denied - allow GboardCasual in KernelSU superuser";
    }
    String raw = out.toString().trim();
    return raw.length() > 100 ? raw.substring(0, 100) : raw;
  }

  void toastOnUi(final String msg) {
    final String cut = msg.length() > 140 ? msg.substring(0, 140) : msg;
    runOnUiThread(new Runnable() {
      @Override public void run() { Toast.makeText(ConfigActivity.this, cut, Toast.LENGTH_LONG).show(); }
    });
  }

  void saveAsync() {
    final String body = collectConf();
    new Thread(new Runnable() {
      @Override public void run() {
        try {
          File tmp = new File(getCacheDir(), "pending.conf");
          FileWriter w = new FileWriter(tmp);
          w.write(body);
          w.close();
          StringBuilder out = new StringBuilder();
          int code = execSu("cat '" + tmp.getAbsolutePath() + "' > '" + CONF
              + "' && chmod 644 '" + CONF + "'", out);
          if (code != 0) {
            toastOnUi("save failed (" + failHint(out) + ")");
            return;
          }
          StringBuilder kill = new StringBuilder();
          execSu("am force-stop " + GBOARD, kill);
          toastOnUi("saved, Gboard restarted");
          loadAsync();
        } catch (Throwable t) {
          toastOnUi("save failed: " + t.getMessage());
        }
      }
    }).start();
  }

  void killAsync() {
    new Thread(new Runnable() {
      @Override public void run() {
        StringBuilder out = new StringBuilder();
        int code = execSu("am force-stop " + GBOARD, out);
        toastOnUi(code == 0 ? "Gboard restarted" : "kill failed (" + failHint(out) + ")");
      }
    }).start();
  }

  void rootAsync() {
    new Thread(new Runnable() {
      @Override public void run() {
        StringBuilder out = new StringBuilder();
        int code = execSu("id", out);
        sRootState = (code == 0 && out.toString().contains("uid=0")) ? "ok" : "denied";
        final String detail = out.toString().trim();
        runOnUiThread(new Runnable() {
          @Override public void run() { status.setText(statusLine()); }
        });
        if (sRootState.equals("ok")) {
          toastOnUi("root ok: " + (detail.length() > 60 ? detail.substring(0, 60) : detail));
        } else {
          toastOnUi("root denied - allow GboardCasual in KernelSU superuser");
        }
      }
    }).start();
  }
}
