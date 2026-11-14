import 'package:flutter/foundation.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:nexus_social/core/services/push_device_registry.dart';
import 'package:nexus_social/core/services/push_notifications.dart';

import '../support/fake_push_backend.dart';

/// F8 · Registrazione del dispositivo per le notifiche push.
/// Solo Firebase simulato in memoria: nessun collegamento a servizi reali.
void main() {
  const String fid1 = 'fidPROVA00000001';
  const String fid2 = 'fidPROVA00000002';
  late FakePushBackend backend;

  Future<void> settle() async {
    for (int i = 0; i < 20; i++) {
      await Future<void>.delayed(Duration.zero);
    }
  }

  setUp(() async {
    debugDefaultTargetPlatformOverride = TargetPlatform.android;
    PushNotifications.installationId.value = null;
    backend = FakePushBackend();
  });

  tearDown(() async {
    await PushDeviceRegistry.debugReset();
    PushNotifications.installationId.value = null;
    await backend.close();
    debugDefaultTargetPlatformOverride = null;
  });

  test('F8 · percorso del documento: profiles/{uid}/devices/{fid}', () {
    expect(
      PushDeviceRegistry.devicePath('utente', 'dispositivo'),
      'profiles/utente/devices/dispositivo',
    );
  });

  test('F8 · Firebase non configurato: nessuna chiamata, stato idle', () async {
    await PushDeviceRegistry.debugReset(backend: backend, firebaseEnabled: false);
    PushNotifications.installationId.value = fid1;

    await PushDeviceRegistry.start();
    await settle();

    expect(PushDeviceRegistry.state.value, PushDeviceLinkState.idle);
    expect(backend.authListened, isFalse);
    expect(backend.calls, isEmpty);
    expect(backend.devices, isEmpty);
  });

  test('F8 · utente non entrato: in attesa, nessuna scrittura', () async {
    await PushDeviceRegistry.debugReset(backend: backend, firebaseEnabled: true);
    PushNotifications.installationId.value = fid1;

    await PushDeviceRegistry.start();
    backend.signIn(null);
    await settle();

    expect(PushDeviceRegistry.state.value, PushDeviceLinkState.waitingForUser);
    expect(backend.calls, isEmpty);
  });

  test('F8 · utente entrato senza identificativo: in attesa del FID', () async {
    await PushDeviceRegistry.debugReset(backend: backend, firebaseEnabled: true);

    await PushDeviceRegistry.start();
    backend.signIn('utenteA');
    await settle();

    expect(PushDeviceRegistry.state.value, PushDeviceLinkState.waitingForFid);
    expect(backend.calls, isEmpty);
  });

  test('F8 · utente e FID: un solo documento, stato collegato', () async {
    await PushDeviceRegistry.debugReset(backend: backend, firebaseEnabled: true);
    PushNotifications.installationId.value = fid1;

    await PushDeviceRegistry.start();
    backend.signIn('utenteA');
    await settle();

    expect(PushDeviceRegistry.state.value, PushDeviceLinkState.linked);
    expect(backend.devices, <String>{'profiles/utenteA/devices/$fid1'});
    expect(backend.calls, <String>['link']);
  });

  test('F8 · cambio di FID per lo stesso utente: vecchio rimosso, nuovo creato', () async {
    await PushDeviceRegistry.debugReset(backend: backend, firebaseEnabled: true);
    PushNotifications.installationId.value = fid1;
    await PushDeviceRegistry.start();
    backend.signIn('utenteA');
    await settle();

    PushNotifications.installationId.value = fid2;
    await settle();

    expect(PushDeviceRegistry.state.value, PushDeviceLinkState.linked);
    expect(backend.devices, <String>{'profiles/utenteA/devices/$fid2'});
    expect(backend.calls, <String>['link', 'unlink', 'link']);
  });

  test('F8 · errore di rete: stato non riuscito, poi «Riprova» collega', () async {
    await PushDeviceRegistry.debugReset(backend: backend, firebaseEnabled: true);
    PushNotifications.installationId.value = fid1;
    backend.failNextLink = true;

    await PushDeviceRegistry.start();
    backend.signIn('utenteA');
    await settle();

    expect(PushDeviceRegistry.state.value, PushDeviceLinkState.failed);
    expect(backend.devices, isEmpty);

    await PushDeviceRegistry.retry();
    await settle();

    expect(PushDeviceRegistry.state.value, PushDeviceLinkState.linked);
    expect(backend.devices, <String>{'profiles/utenteA/devices/$fid1'});
  });

  test('F8 · uscita sicura: il dispositivo viene scollegato prima del logout', () async {
    await PushDeviceRegistry.debugReset(backend: backend, firebaseEnabled: true);
    PushNotifications.installationId.value = fid1;
    await PushDeviceRegistry.start();
    backend.signIn('utenteA');
    await settle();

    await PushDeviceRegistry.signOut();
    await settle();

    expect(backend.devices, isEmpty);
    expect(backend.calls, <String>['link', 'unlink', 'signOut']);
    expect(PushDeviceRegistry.state.value, PushDeviceLinkState.waitingForUser);
  });
}
