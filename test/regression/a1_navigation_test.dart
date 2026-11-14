import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:nexus_social/app.dart';
import 'package:nexus_social/core/theme/app_theme.dart';

/// Decisioni A1 e «schermata iniziale»: ordine della barra, Scambia al centro in
/// arancio, apertura su Chat. Nessuna voce Home o Community.
void main() {
  testWidgets('A1 · ordine: Chat, Mappa, Scambia, Vendita, Profilo', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(const NexusApp());
    await tester.pumpAndSettle();

    final NavigationBar bar = tester.widget<NavigationBar>(
      find.byType(NavigationBar),
    );
    final List<String> labels = <String>[
      for (final Widget destination in bar.destinations)
        (destination as NavigationDestination).label,
    ];
    expect(labels, <String>['Chat', 'Mappa', 'Scambia', 'Vendita', 'Profilo']);
    expect(find.text('Home'), findsNothing);
    expect(find.text('Community'), findsNothing);
  });

  testWidgets('A1 · all\'apertura l\'app mostra Chat', (WidgetTester tester) async {
    await tester.pumpWidget(const NexusApp());
    await tester.pumpAndSettle();

    final NavigationBar bar = tester.widget<NavigationBar>(
      find.byType(NavigationBar),
    );
    expect(bar.selectedIndex, 0);
    expect(find.text('Chat e chiamate'), findsOneWidget);
    expect(find.text('Mappa Sociale'), findsNothing);
  });

  for (final Brightness brightness in Brightness.values) {
    testWidgets('A1 · Scambia è il pulsante centrale arancione (${brightness.name})', (
      WidgetTester tester,
    ) async {
      tester.platformDispatcher.platformBrightnessTestValue = brightness;
      addTearDown(tester.platformDispatcher.clearPlatformBrightnessTestValue);
      await tester.pumpWidget(const NexusApp());
      await tester.pumpAndSettle();

      final Finder orangeCircle = find.descendant(
        of: find.byType(NavigationBar),
        matching: find.byWidgetPredicate((Widget widget) {
          if (widget is! DecoratedBox) return false;
          final Decoration decoration = widget.decoration;
          return decoration is BoxDecoration &&
              decoration.shape == BoxShape.circle &&
              decoration.gradient == AppColors.orangeGradient;
        }),
      );
      expect(orangeCircle, findsOneWidget);
      expect(
        find.descendant(of: orangeCircle, matching: find.byIcon(Icons.swap_horiz_rounded)),
        findsOneWidget,
      );

      await tester.tap(find.descendant(of: find.byType(NavigationBar), matching: find.text('Scambia')));
      await tester.pumpAndSettle();
      expect(
        tester.widget<NavigationBar>(find.byType(NavigationBar)).selectedIndex,
        2,
      );
      expect(find.text('Scambia vicino a te'), findsOneWidget);
      expect(tester.takeException(), isNull);
    });
  }
}
