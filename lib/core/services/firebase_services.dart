import 'dart:async';

import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/foundation.dart';

/// Predispone Auth, Firestore e Messaging dopo l'inizializzazione di Firebase Core.
///
/// In Fase 1 non scrive dati e non modifica le regole di sicurezza: verifica solo che i
/// plugin siano registrati e che i servizi siano pronti per le fasi successive.
abstract final class FirebaseServices {
  /// Va registrato prima di `runApp`, come richiede FCM per i messaggi in background.
  static void registerBackgroundHandler() {
    FirebaseMessaging.onBackgroundMessage(_onBackgroundMessage);
  }

  /// Riscaldamento non bloccante: l'app si apre anche se un servizio opzionale fallisce.
  static Future<void> warmUp() async {
    try {
      FirebaseAuth.instance;
      FirebaseFirestore.instance;
      debugPrint('NEXUS_SERVICES auth=ready firestore=ready');
    } catch (error) {
      debugPrint(
        'NEXUS_SERVICES auth=failed firestore=failed (${error.runtimeType})',
      );
    }

    await _prepareMessaging();
  }

  static Future<void> _prepareMessaging() async {
    final FirebaseMessaging messaging = FirebaseMessaging.instance;
    StreamSubscription<String>? subscription;
    try {
      await messaging.setAutoInitEnabled(true);

      // firebase_messaging 17 usa la Firebase Installation ID: register() avvia
      // la registrazione e onRegistered la conferma. In Fase 4 il backend userà
      // questo identificativo per inviare le notifiche.
      final Completer<void> registered = Completer<void>();
      subscription = messaging.onRegistered.listen((String _) {
        if (!registered.isCompleted) registered.complete();
      });
      await messaging.register();
      await registered.future.timeout(const Duration(seconds: 10));
      debugPrint('NEXUS_FCM registered=true');
    } catch (error) {
      // Firebase Core resta attivo: un problema di Messaging non deve bloccare l'app.
      debugPrint('NEXUS_FCM error=${error.runtimeType}');
    } finally {
      await subscription?.cancel();
    }
  }
}

@pragma('vm:entry-point')
Future<void> _onBackgroundMessage(RemoteMessage message) async {
  // Fase 4: qui verranno gestite le notifiche con contenuti cifrati.
}
