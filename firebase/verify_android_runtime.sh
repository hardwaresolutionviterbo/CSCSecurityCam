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
adb shell am force-stop "${package}" || true

marker=""
services=""
push=""
fcm=""
detail=""

# La registrazione FIS dell'emulatore può fallire in modo transitorio. Se dopo
# il primo avvio FCM non è registrato, riavvia l'app e riprova una volta.
for launch in 1 2; do
  adb logcat -c || true
  adb shell am start -n "${package}/.MainActivity"

  marker=""
  services=""
  push=""
  fcm=""
  detail=""

  for _ in $(seq 1 36); do
    sleep 5
    marker=$(adb logcat -d -s flutter:I | grep -a "NEXUS_BOOTSTRAP" | tail -1 || true)
    services=$(adb logcat -d -s flutter:I | grep -a "NEXUS_SERVICES" | tail -1 || true)
    push=$(adb logcat -d -s flutter:I | grep -a "NEXUS_PUSH" | tail -1 || true)
    fcm=$(adb logcat -d -s flutter:I | grep -a "NEXUS_FCM" | grep -av "detail=" | tail -1 || true)
    detail=$(adb logcat -d -s flutter:I | grep -a "NEXUS_FCM detail=" | tail -1 || true)
    if [ -n "${marker}" ] && [ -n "${services}" ] &&
      [ -n "${push}" ] && [ -n "${fcm}" ]; then
      break
    fi
  done

  if grep -q "registered=true" <<<"${fcm}"; then
    break
  fi

  echo "Tentativo ${launch}: FCM non ancora registrato, riavvio l'app."
  adb shell am force-stop "${package}" || true
  sleep 5
done

echo "Runtime: ${marker:-nessun marker}"
echo "Servizi: ${services:-nessun marker}"
echo "Push: ${push:-nessun marker}"
echo "FCM: ${fcm:-nessun marker}"
if [ -n "${detail}" ]; then
  echo "Dettaglio FCM: ${detail}"
fi

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
