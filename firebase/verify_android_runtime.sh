#!/usr/bin/env bash
# Verifica che l'APK debug inizializzi Firebase Core, Auth, Firestore e FCM.
# Va eseguito sull'emulatore con l'APK già disponibile (default: apk/app-debug.apk).
# Non stampa mai il Firebase Installation ID o altri identificativi.
set -euo pipefail

apk="${1:-apk/app-debug.apk}"
package="com.nexussocial.app"

# Logcat pulito: i marker devono provenire da questo avvio, non da esecuzioni precedenti.
adb logcat -c || true

adb install -r "${apk}"
# Android 13+: equivalente al consenso dell'utente, così il test non resta bloccato
# sul dialog di POST_NOTIFICATIONS.
adb shell pm grant "${package}" android.permission.POST_NOTIFICATIONS
adb shell am start -n "${package}/.MainActivity"

marker=""
services=""
push=""
fcm=""
for _ in $(seq 1 24); do
  sleep 5
  marker=$(adb logcat -d -s flutter:I | grep -a "NEXUS_BOOTSTRAP" | tail -1 || true)
  services=$(adb logcat -d -s flutter:I | grep -a "NEXUS_SERVICES" | tail -1 || true)
  push=$(adb logcat -d -s flutter:I | grep -a "NEXUS_PUSH" | tail -1 || true)
  fcm=$(adb logcat -d -s flutter:I | grep -a "NEXUS_FCM" | tail -1 || true)
  if [ -n "${marker}" ] && [ -n "${services}" ] &&
    [ -n "${push}" ] && [ -n "${fcm}" ]; then
    break
  fi
done

echo "Runtime: ${marker:-nessun marker}"
echo "Servizi: ${services:-nessun marker}"
echo "Push: ${push:-nessun marker}"
echo "FCM: ${fcm:-nessun marker}"

if [ -z "${marker}" ] || [ -z "${services}" ] ||
  [ -z "${push}" ] || [ -z "${fcm}" ]; then
  echo "--- marker flutter ---"
  adb logcat -d -s flutter:I | tail -40 || true
fi

test -n "${marker}"
grep -q "firebase=connected" <<<"${marker}"
grep -q "auth=ready" <<<"${services}"
grep -q "firestore=ready" <<<"${services}"
grep -q "channel=ready" <<<"${push}"
grep -q "permission=granted" <<<"${push}"
grep -q "registered=true" <<<"${fcm}"
echo "Firebase Core, Auth, Firestore e FCM inizializzati; canale notifiche pronto."
