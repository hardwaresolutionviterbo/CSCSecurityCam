#!/usr/bin/env bash
# Verifica che l'APK debug inizializzi davvero Firebase Core, Auth e Firestore.
# Va eseguito sull'emulatore con l'APK già disponibile (default: apk/app-debug.apk).
set -euo pipefail

apk="${1:-apk/app-debug.apk}"
package="com.nexussocial.app"

# Logcat pulito: i marker devono provenire da questo avvio, non da esecuzioni precedenti.
adb logcat -c || true

adb install -r "${apk}"
adb shell am start -n "${package}/.MainActivity"

marker=""
services=""
for _ in $(seq 1 24); do
  sleep 5
  marker=$(adb logcat -d -s flutter:I | grep -a "NEXUS_BOOTSTRAP" | tail -1 || true)
  services=$(adb logcat -d -s flutter:I | grep -a "NEXUS_SERVICES" | tail -1 || true)
  if [ -n "${marker}" ] && [ -n "${services}" ]; then
    break
  fi
done

echo "Runtime: ${marker:-nessun marker}"
echo "Servizi: ${services:-nessun marker}"

if [ -z "${marker}" ] || [ -z "${services}" ]; then
  echo "--- logcat (app/Firebase) ---"
  adb logcat -d | grep -aE "flutter|Firebase|nexussocial" | tail -80 || true
fi

test -n "${marker}"
grep -q "firebase=connected" <<<"${marker}"
grep -q "auth=ready" <<<"${services}"
grep -q "firestore=ready" <<<"${services}"
echo "Firebase Core, Auth e Firestore inizializzati nell'app."
