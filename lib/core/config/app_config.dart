/// Configurazione compilata con `--dart-define` o `--dart-define-from-file=env.json`.
///
/// I valori non finiscono mai nel repository: le chiavi si passano in locale e nelle
/// variabili d'ambiente di Codemagic.
abstract final class AppConfig {
  static const String supabaseUrl = String.fromEnvironment('SUPABASE_URL');

  /// Chiave pubblica del progetto Supabase (anon o publishable): è pensata per stare
  /// nell'app, la sicurezza la fanno le policy RLS.
  static const String supabaseKey = String.fromEnvironment('SUPABASE_ANON_KEY');
  static const bool firebaseEnabled = bool.fromEnvironment('FIREBASE_ENABLED');

  /// Supabase è pronto solo con entrambi i valori.
  static bool get hasSupabase =>
      supabaseUrl.isNotEmpty && supabaseKey.isNotEmpty;

  /// Firebase si attiva quando i file di configurazione sono stati aggiunti.
  static bool get hasFirebase => firebaseEnabled;
}

/// Identità dell'app mostrata nell'interfaccia.
abstract final class AppInfo {
  static const String name = 'Nexus Social';
  static const String version = '0.1.0';
  static const int phase = 1;
  static const String phaseLabel = 'Fase 1 · Fondamenta';
}
