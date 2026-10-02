#!/system/bin/sh
# verify.sh - read-only evidence: Gboard presence plus recent hook activity.
LOG=/data/adb/gboardcasual/run/verify.log
mkdir -p /data/adb/gboardcasual/run
{
echo "=== $(date) GboardCasual verify ==="
echo "-- packages --"
pm list packages 2>/dev/null | grep -E "gboardcasual|inputmethod.latin" || echo "(neither installed?)"
echo "-- gboard version --"
dumpsys package com.google.android.inputmethod.latin 2>/dev/null | grep -E "versionName" | head -n 2
echo "-- live config --"
cat /data/local/tmp/gboardcasual.conf 2>/dev/null || echo "(defaults: mode=1 style=kamu slang=0)"
echo "-- hook log (open Gboard translate first) --"
grep -h "GboardCasual" /data/adb/lspd/log/modules_*.log 2>/dev/null | tail -n 15 || echo "(empty - translate something with Gboard first)"
} 2>&1 | tee "$LOG"
