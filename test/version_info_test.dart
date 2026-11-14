import 'package:material_ui/material_ui.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:nexus_social/app.dart';
import 'package:nexus_social/core/config/app_config.dart';

/// Regola R1: ogni APK riporta versione, numero di build, commit e ramo.
void main() {
  test('la versione completa contiene build, commit abbreviato e ramo', () {
    expect(
      AppInfo.formatVersion(
        version: '0.1.0',
        buildNumber: '1202',
        gitCommit: 'a1b2c3d4e5f60718293a4b5c6d7e8f9012345678',
        gitBranch: 'fase1/blocco1',
      ),
      '0.1.0 (1202) · a1b2c3d · fase1/blocco1',
    );
  });

  test('senza dati della build la riga lo dichiara, senza inventare valori', () {
    expect(
      AppInfo.formatVersion(
        version: '0.1.0',
        buildNumber: '',
        gitCommit: '',
        gitBranch: '',
      ),
      '0.1.0 (build locale) · commit n/d · ramo n/d',
    );
  });

  // In CI i test ricevono gli stessi --dart-define dell'APK; in locale il caso è saltato.
  test(
    'nella build di GitHub Actions gli identificativi sono tutti presenti',
    () {
    expect(int.tryParse(AppInfo.buildNumber), isNotNull);
    expect(AppInfo.gitCommit.length, greaterThanOrEqualTo(7));
    expect(AppInfo.gitBranch, isNotEmpty);
    expect(AppInfo.versionLabel, contains(AppInfo.version));
    expect(AppInfo.versionLabel, contains('(${AppInfo.buildNumber})'));
    expect(AppInfo.versionLabel, contains(AppInfo.gitCommit.substring(0, 7)));
    expect(AppInfo.versionLabel, endsWith(AppInfo.gitBranch));
    },
    skip: AppInfo.buildNumber.isEmpty
        ? 'Esecuzione locale: nessun identificativo di build.'
        : false,
  );

  testWidgets('Profilo → Informazioni mostra la riga Versione completa', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(const NexusApp());
    await tester.pumpAndSettle();

    await tester.tap(
      find.descendant(
        of: find.byType(NavigationBar),
        matching: find.text('Profilo'),
      ),
    );
    await tester.pumpAndSettle();
    await tester.drag(find.text('Stato dei servizi'), const Offset(0, -400));
    await tester.pumpAndSettle();

    expect(find.text('Versione'), findsOneWidget);
    expect(find.text(AppInfo.versionLabel), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
