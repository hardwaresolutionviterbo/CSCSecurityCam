import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:nexus_social/core/config/app_config.dart';
import 'package:nexus_social/features/profile/profile_screen.dart';

/// F6 · Diagnostica push con Firebase attivo (solo build di debug).
///
/// In GitHub Actions questo file viene eseguito una seconda volta con
/// `--dart-define=FIREBASE_ENABLED=true`: Firebase NON viene inizializzato,
/// si verifica soltanto che la voce compaia e apra la schermata.
void main() {
  testWidgets(
    'F6 · con Firebase attivo la voce compare e apre «Diagnostica push»',
    (WidgetTester tester) async {
      await tester.pumpWidget(
        const MaterialApp(home: Scaffold(body: ProfileScreen())),
      );
      await tester.pumpAndSettle();

      final Finder entry = find.text('Diagnostica push (debug)');
      expect(entry, findsOneWidget);
      await tester.tap(entry);
      await tester.pumpAndSettle();

      expect(find.text('Diagnostica push'), findsOneWidget);
      expect(find.text('Registrazione FCM'), findsOneWidget);
      expect(find.text('Riprova associazione'), findsOneWidget);
    },
    skip: !AppConfig.hasFirebase,
  );
}
