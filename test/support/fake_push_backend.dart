import 'dart:async';

import 'package:nexus_social/core/services/push_device_registry.dart';

/// Firebase simulato in memoria per i test della registrazione push (F8).
/// Nessuna connessione: utenti e documenti esistono solo in questa classe.
class FakePushBackend implements PushDeviceBackend {
  final StreamController<String?> _auth = StreamController<String?>.broadcast(
    onListen: () => authListened = true,
  );

  bool authListened = false;
  String? uid;
  bool failNextLink = false;

  /// Documenti presenti, come percorsi `profiles/{uid}/devices/{fid}`.
  final Set<String> devices = <String>{};

  /// Operazioni richieste, in ordine.
  final List<String> calls = <String>[];

  void signIn(String? newUid) {
    uid = newUid;
    _auth.add(newUid);
  }

  @override
  Stream<String?> authUidChanges() => _auth.stream;

  @override
  String? get currentUid => uid;

  @override
  Future<void> link({
    required String uid,
    required String fid,
    required String platform,
  }) async {
    calls.add('link');
    if (failNextLink) {
      failNextLink = false;
      throw StateError('rete non disponibile (simulata)');
    }
    devices.add(PushDeviceRegistry.devicePath(uid, fid));
  }

  @override
  Future<void> unlink({required String uid, required String fid}) async {
    calls.add('unlink');
    devices.remove(PushDeviceRegistry.devicePath(uid, fid));
  }

  @override
  Future<void> signOut() async {
    calls.add('signOut');
    signIn(null);
  }

  Future<void> close() => _auth.close();
}
