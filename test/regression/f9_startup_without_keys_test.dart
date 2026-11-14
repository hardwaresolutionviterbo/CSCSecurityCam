import 'package:flutter_test/flutter_test.dart';
import 'package:nexus_social/app.dart';
import 'package:nexus_social/core/config/app_config.dart';
import 'package:nexus_social/core/services/backend_bootstrap.dart';
import 'package:nexus_social/core/services/push_device_registry.dart';

import '../support/app_helpers.dart';

/// F9 · Avvio dell'app senza chiavi: nessun errore, servizi «Non configurato».
void main() {
  test('F9 · i test girano senza chiavi dei servizi', () {
    expect(AppConfig.supabaseUrl, isEmpty);
    expect(AppConfig.supabaseKey, isEmpty);
    expect(AppConfig.hasSupabase, isFalse);
    expect(AppConfig.hasFirebase, isFalse);
  });

  test('F9 · l\'avvio dei servizi senza chiavi non fallisce', () async {
    final BackendStatus status = await BackendBootstrap.initialize();
    expect(status.firebase, ServiceState.notConfigured);
    expect(status.supabase, ServiceState.notConfigured);
    expect(status.message, isNull);
  });

  testWidgets('F9 · senza chiavi tutte le schede funzionano e le push non partono', (
    WidgetTester tester,
  ) async {
    final BackendStatus status = await tester.runAsync(
      BackendBootstrap.initialize,
    ) as BackendStatus;
    await tester.pumpWidget(NexusApp(backend: status));
    await tester.pumpAndSettle();

    for (final MapEntry<String, String> section in sectionMarkers.entries) {
      await openSection(tester, section.key);
      expect(find.text(section.value), findsOneWidget);
      expect(tester.takeException(), isNull);
    }
    expect(find.text('Non configurato'), findsNWidgets(2));
    expect(PushDeviceRegistry.state.value, PushDeviceLinkState.idle);
  });
}
