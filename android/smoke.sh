#!/usr/bin/env bash
set -euo pipefail
adb install -r app-debug.apk
adb shell am start -W -n ai.victor.app/.MainActivity
adb shell uiautomator dump /sdcard/victor-ui.xml >/dev/null
adb shell cat /sdcard/victor-ui.xml > /tmp/victor-ui.xml
grep -q 'Your personal AI assistant' /tmp/victor-ui.xml
python3 - <<'PY'
import re, subprocess
xml=open('/tmp/victor-ui.xml').read()
m=re.search(r'text="CONNECT SERVICES[^\"]*"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',xml)
if not m: raise SystemExit('Welcome configuration button missing')
x1,y1,x2,y2=map(int,m.groups())
subprocess.run(['adb','shell','input','tap',str((x1+x2)//2),str((y1+y2)//2)],check=True)
PY
adb shell uiautomator dump /sdcard/victor-ui.xml >/dev/null
adb shell cat /sdcard/victor-ui.xml > /tmp/victor-ui.xml
grep -q 'Everything connected' /tmp/victor-ui.xml
if adb logcat -d -s AndroidRuntime:E | grep -q 'FATAL EXCEPTION'; then
  echo 'Android runtime crash detected'; exit 1
fi
echo 'PASS: APK installed, launched, first-run configuration opened, no runtime crash.'
