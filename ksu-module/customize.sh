#!/system/bin/sh
# customize.sh - install-time gates, works on KernelSU and Magisk.
# Rather than installing half-working, stop early with a clear reason.
SKIPMOUNT=false
PROPFILE=false
POSTFSDATA=true
LATESTARTSERVICE=true

VER=$(grep '^version=' "$MODPATH/module.prop" 2>/dev/null | cut -d= -f2)
ui_print "- GboardCasual ${VER:-unknown}"
ui_print "- target: casual Indonesian for Gboard Translate output"

mod_enabled() {
  [ -d "/data/adb/modules/$1" ] || return 1
  [ -e "/data/adb/modules/$1/disable" ] && return 1
  [ -e "/data/adb/modules/$1/remove" ] && return 1
  return 0
}

API=$(getprop ro.build.version.sdk 2>/dev/null || echo 0)
if [ "$API" -lt 29 ]; then
  abort "! Android 10+ required (this device: sdk $API)."
fi

if ! mod_enabled rezygisk && ! mod_enabled zygisk_next && ! mod_enabled zygisknext; then
  abort "! No enabled Zygisk provider found (need ReZygisk or ZygiskNext)."
fi
n=0
mod_enabled rezygisk && n=$((n + 1))
mod_enabled zygisk_next && n=$((n + 1))
mod_enabled zygisknext && n=$((n + 1))
[ "$n" -gt 1 ] && ui_print "! warning: $n Zygisk providers enabled; keep only one to avoid conflicts."

if ! mod_enabled zygisk_lsposed; then
  abort "! LSPosed framework not found/enabled. Install its Zygisk module, reboot, then flash this."
fi

if [ ! -f "$MODPATH/gboardcasual.apk" ]; then
  abort "! gboardcasual.apk missing from this zip - broken package, re-download."
fi

if ! pm list packages 2>/dev/null | grep -q "^package:com.google.android.inputmethod.latin$"; then
  ui_print "! warning: Gboard not found (com.google.android.inputmethod.latin)."
  ui_print "  Flash anyway, hook stays idle until Gboard is installed."
fi

ui_print "- installing hook APK..."
if pm install -r "$MODPATH/gboardcasual.apk" 2>&1 | grep -qi "success"; then
  ui_print "- hook APK installed."
else
  abort "! pm install failed. Install $MODPATH/gboardcasual.apk manually, then re-flash."
fi

CONF=/data/adb/gboardcasual/config
mkdir -p /data/adb/gboardcasual
[ -f "$CONF" ] || cat > "$CONF" <<'EOF'
# GboardCasual seed (informational; live tuning lives in
# /data/local/tmp/gboardcasual.conf - see README "Tuning without reboot")
# mode=1 style=kamu slang=0
# custom pairs, one per line: Formal=casual
packages=com.google.android.inputmethod.latin
mode=translate-rewrite
EOF
ui_print "- config: $CONF"

set_perm_recursive $MODPATH/bin 0 0 0755 0755
set_perm_recursive $MODPATH/webroot 0 0 0755 0644
set_perm $MODPATH/service.sh 0 0 0755
set_perm $MODPATH/post-fs-data.sh 0 0 0755
set_perm $MODPATH/action.sh 0 0 0755
set_perm $MODPATH/bin/gboardcasual 0 0 0755
set_perm $MODPATH/bin/verify.sh 0 0 0755
set_perm $MODPATH/bin/ftune 0 0 0755

ui_print "- NEXT (one reboot total):"
ui_print "  1. LSPosed Manager > Modules > GboardCasual > enable."
ui_print "  2. Scope: check ONLY Gboard (never System Framework)."
ui_print "  3. Reboot once, then translate and Verify."
