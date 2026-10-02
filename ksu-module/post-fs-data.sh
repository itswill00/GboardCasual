#!/system/bin/sh
# post-fs-data.sh - keep minimal and safe
MODDIR=${0%/*}
mkdir -p /data/adb/gboardcasual/run
echo "$(date +%F_%T) post-fs-data" >> /data/adb/gboardcasual/run/boot.log
