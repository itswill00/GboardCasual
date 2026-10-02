#!/system/bin/sh
# service.sh - late_start hook. Only records Gboard presence into a boot
# log; it never touches input or network.
MODDIR=${0%/*}
LOG=/data/adb/gboardcasual/run/service.log
mkdir -p /data/adb/gboardcasual/run

for i in $(seq 1 60); do
  [ "$(getprop sys.boot_completed)" = "1" ] && break
  sleep 2
done

{
echo "=== $(date) gboardcasual service ==="
echo "model=$(getprop ro.product.model) sdk=$(getprop ro.build.version.sdk)"
pm list packages 2>/dev/null | grep "inputmethod.latin" || echo "gboard=not-installed"
dumpsys package com.google.android.inputmethod.latin 2>/dev/null | grep -E "versionName" | head -n 2
} >> "$LOG" 2>&1
