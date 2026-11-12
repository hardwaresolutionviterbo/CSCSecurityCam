import 'dart:async';

import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/foundation.dart';

import 'push_notifications.dart';

/// Predispone Auth, Firestore e Messaging dopo l'inizializzazione di Firebase Core.
///
/// In Fase 1 non scrive dati e non modifica le regole di sicurezza: verifica solo che i
/// plugin siano registrati e che i servizi siano pronti per le fasi successive.
abstract final class FirebaseServices {
  /// Va registrato prima di `runApp`, come richiede FCM per i messaggi in background.
  static void registerBackgroundHandler() {
    PushNotifications.registerBackgroundHandler();
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

    await PushNotifications.initialize();
  }
}
