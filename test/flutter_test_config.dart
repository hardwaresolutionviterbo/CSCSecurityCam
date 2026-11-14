import 'dart:async';
import 'dart:io';

/// Configurazione comune a tutti i test (Flutter la carica da sola).
///
/// Isolamento (regola F8): i test non devono mai contattare servizi reali.
/// Qualunque tentativo di aprire una connessione HTTP fuori dai test di
/// interfaccia fa fallire il test. Nei test di interfaccia (`testWidgets`)
/// Flutter sostituisce già la rete con risposte finte, senza uscire dal computer.
Future<void> testExecutable(FutureOr<void> Function() testMain) async {
  HttpOverrides.global = _NoNetworkOverrides();
  await testMain();
}

class _NoNetworkOverrides extends HttpOverrides {
  @override
  HttpClient createHttpClient(SecurityContext? context) {
    throw StateError(
      'Connessione di rete vietata nei test: usare simulazioni o emulatori locali.',
    );
  }
}
