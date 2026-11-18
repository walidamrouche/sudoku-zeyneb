#!/usr/bin/env bash
# Test de fumée sur émulateur : lance l'appli, ouvre une partie killer, fait des captures d'écran.
set -u
APK="$1"; OUT="$2"; mkdir -p "$OUT"
adb install -r "$APK"
adb logcat -c
shot() { adb exec-out screencap -p > "$OUT/$1.png"; }
tap_text() {  # touche le premier élément dont le texte vaut $1
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb pull /sdcard/ui.xml "$OUT/ui.xml" >/dev/null 2>&1
  b=$(python3 - "$1" "$OUT/ui.xml" <<'PY'
import re, sys
xml = open(sys.argv[2], encoding="utf-8").read()
for m in re.finditer(r'<node [^>]*>', xml):
    n = m.group(0)
    t = re.search(r' text="([^"]*)"', n); bd = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
    if t and bd and t.group(1) == sys.argv[1]:
        x1, y1, x2, y2 = map(int, bd.groups()); print((x1 + x2) // 2, (y1 + y2) // 2); break
PY
)
  [ -n "$b" ] && adb shell input tap $b || echo "texte introuvable : $1"
}
adb shell am start -W -n com.zeyneb.sudoku/.MainActivity
sleep 0.2; shot 1-chargement
sleep 3; shot 2-accueil
tap_text "Killer"
for k in $(seq 1 30); do sleep 2; adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb shell cat /sdcard/ui.xml | grep -q "Preparing" || break; done
sleep 1; shot 3-partie-killer
# quelques touches : sélection d'une case puis saisie d'un chiffre (rien de grave s'il est faux)
W=$(adb shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1); WX=${W%x*}
adb shell input tap $((WX/2)) $((WX*3/4)); sleep 0.3
tap_text "5"; sleep 1; shot 4-chiffre-saisi
sleep 5; shot 5-apres-delai
adb shell input keyevent KEYCODE_BACK; sleep 1; shot 6-retour-accueil
tap_text "Classic"; for k in $(seq 1 30); do sleep 2; adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb shell cat /sdcard/ui.xml | grep -q "Preparing" || break; done
sleep 1; shot 7-partie-classique
adb logcat -d > "$OUT/logcat.txt"
if grep -q "FATAL EXCEPTION" "$OUT/logcat.txt"; then grep -A 30 "FATAL EXCEPTION" "$OUT/logcat.txt" | head -60; exit 1; fi
echo "aucun plantage"
