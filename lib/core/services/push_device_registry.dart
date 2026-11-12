import 'dart:async';

import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/foundation.dart';

import '../config/app_config.dart';
import 'push_notifications.dart';

/// Stato dell'associazione tra account Firebase e dispositivo.
enum PushDeviceLinkState {
  idle,
  waitingForUser,
  waitingForFid,
  linking,
  linked,
  failed,
}

/// Associa il Firebase Installation ID all'utente autenticato.
///
/// Il documento vive in `profiles/{uid}/devices/{fid}` e le regole Firestore
/// consentono lettura e scrittura solo al proprietario del profilo. Il FID non
/// viene mai stampato nei log: qui vengono riportati soltanto gli stati e i
/// codici di errore Firebase.
abstract final class PushDeviceRegistry {
  static final ValueNotifier<PushDeviceLinkState> state =
      ValueNotifier<PushDeviceLinkState>(PushDeviceLinkState.idle);

  static User? _user;
  static String? _fid;
  static String? _linkedUid;
  static String? _linkedFid;
  static bool _started = false;
  static bool _syncing = false;
  static bool _syncRequested = false;

  /// Avvia l'ascolto dell'autenticazione e del FID.
  ///
  /// Va chiamato dopo `PushNotifications.initialize()` e dopo
  /// `Firebase.initializeApp()`.
  static Future<void> start() async {
    if (_started) return;
    _started = true;

    if (kIsWeb || !AppConfig.hasFirebase) {
      state.value = PushDeviceLinkState.idle;
      return;
    }

    _fid = PushNotifications.installationId.value;
    PushNotifications.installationId.addListener(_onInstallationIdChanged);

    try {
      FirebaseAuth.instance.authStateChanges().listen(
        _onAuthChanged,
        onError: (Object error) {
          state.value = PushDeviceLinkState.failed;
          debugPrint('NEXUS_DEVICE error=${_errorCode(error)}');
        },
      );
    } catch (error) {
      // Consenti a «Riprova associazione» di ritentare l'avvio.
      _started = false;
      state.value = PushDeviceLinkState.failed;
      debugPrint('NEXUS_DEVICE error=${_errorCode(error)}');
    }
  }

  /// Riprova dopo un errore di rete o dopo la pubblicazione delle regole.
  static Future<void> retry() async {
    if (!_started) {
      await start();
      return;
    }
    _fid = PushNotifications.installationId.value;
    await _sync();
  }

  static void _onInstallationIdChanged() {
    final String? fid = PushNotifications.installationId.value;
    if (fid == null || fid.isEmpty || fid == _fid) return;
    _fid = fid;
    unawaited(_sync());
  }

  static void _onAuthChanged(User? user) {
    _user = user;
    unawaited(_sync());
  }

  /// Serializza le richieste: auth e FID possono arrivare in ordine diverso.
  static Future<void> _sync() async {
    _syncRequested = true;
    if (_syncing) return;

    _syncing = true;
    try {
      while (_syncRequested) {
        _syncRequested = false;
        try {
          await _syncOnce();
        } catch (error) {
          state.value = PushDeviceLinkState.failed;
          debugPrint('NEXUS_DEVICE error=${_errorCode(error)}');
        }
      }
    } finally {
      _syncing = false;
    }
  }

  static Future<void> _syncOnce() async {
    final User? user = _user;
    final String? fid = _fid;

    // Se l'account è cambiato o è stato chiuso, rimuovi prima il vecchio
    // collegamento: un dispositivo non deve continuare a ricevere notifiche
    // dell'account precedente.
    if (_linkedUid != null &&
        _linkedFid != null &&
        (_linkedUid != user?.uid || _linkedFid != fid)) {
      final bool removed = await _unlink(_linkedUid!, _linkedFid!);
      if (!removed) return;
      _linkedUid = null;
      _linkedFid = null;
    }

    if (user == null) {
      state.value = PushDeviceLinkState.waitingForUser;
      return;
    }

    if (fid == null || fid.isEmpty) {
      state.value = PushDeviceLinkState.waitingForFid;
      return;
    }

    final String? platform = _platform;
    if (platform == null) {
      state.value = PushDeviceLinkState.idle;
      return;
    }

    if (!_isValidInstallationId(fid)) {
      state.value = PushDeviceLinkState.failed;
      debugPrint('NEXUS_DEVICE error=invalid_fid');
      return;
    }

    state.value = PushDeviceLinkState.linking;
    try {
      await _link(uid: user.uid, fid: fid, platform: platform);
      _linkedUid = user.uid;
      _linkedFid = fid;
      state.value = PushDeviceLinkState.linked;
      debugPrint('NEXUS_DEVICE linked=true');
    } catch (error) {
      state.value = PushDeviceLinkState.failed;
      debugPrint('NEXUS_DEVICE error=${_errorCode(error)}');
    }
  }

  static Future<void> _link({
    required String uid,
    required String fid,
    required String platform,
  }) async {
    final DocumentReference<Map<String, dynamic>> document = _document(
      uid,
      fid,
    );

    await FirebaseFirestore.instance.runTransaction((
      Transaction transaction,
    ) async {
      final DocumentSnapshot<Map<String, dynamic>> snapshot = await transaction
          .get(document);
      final Map<String, Object?> data = <String, Object?>{
        'platform': platform,
        'appVersion': AppInfo.version,
        'enabled': true,
        'updatedAt': FieldValue.serverTimestamp(),
        'lastSeenAt': FieldValue.serverTimestamp(),
      };
      if (!snapshot.exists) {
        data['createdAt'] = FieldValue.serverTimestamp();
      }
      transaction.set(document, data, SetOptions(merge: true));
    });
  }

  static Future<bool> _unlink(String uid, String fid) async {
    try {
      await _document(uid, fid).delete();
      return true;
    } catch (error) {
      state.value = PushDeviceLinkState.failed;
      debugPrint('NEXUS_DEVICE error=${_errorCode(error)}');
      return false;
    }
  }

  static DocumentReference<Map<String, dynamic>> _document(
    String uid,
    String fid,
  ) {
    return FirebaseFirestore.instance
        .collection('profiles')
        .doc(uid)
        .collection('devices')
        .doc(fid);
  }

  /// Il FID è un identificativo base64url di Firebase Installation.
  static bool _isValidInstallationId(String fid) {
    return RegExp(r'^[A-Za-z0-9_-]{10,128}$').hasMatch(fid);
  }

  static String? get _platform => switch (defaultTargetPlatform) {
    TargetPlatform.android => 'android',
    TargetPlatform.iOS => 'ios',
    _ => null,
  };

  static String _errorCode(Object error) {
    return error is FirebaseException
        ? error.code
        : error.runtimeType.toString();
  }
}
