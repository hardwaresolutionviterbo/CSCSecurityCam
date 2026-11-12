import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:material_ui/material_ui.dart';

import '../../core/services/push_notifications.dart';
import '../../core/theme/app_theme.dart';
import '../../core/widgets/nexus_ui.dart';

/// Diagnostica FCM disponibile solo nelle build debug.
///
/// Mostra il Firebase Installation ID per poter inviare un messaggio di prova
/// dalla Firebase Console. Non stampa identificativi nei log.
class PushDiagnosticsScreen extends StatelessWidget {
  const PushDiagnosticsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    if (!kDebugMode) {
      return const Scaffold(
        body: Center(child: Text('Diagnostica disponibile solo in debug.')),
      );
    }

    final ThemeData theme = Theme.of(context);
    final ColorScheme scheme = theme.colorScheme;

    return Scaffold(
      appBar: AppBar(title: const Text('Diagnostica push')),
      body: ListView(
        padding: AppSpacing.pagePadding,
        children: [
          SectionCard(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Icon(
                      Icons.fingerprint_rounded,
                      size: 18,
                      color: scheme.primary,
                    ),
                    const SizedBox(width: AppSpacing.sm),
                    Text(
                      'Firebase Installation ID',
                      style: theme.textTheme.titleSmall,
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                ValueListenableBuilder<String?>(
                  valueListenable: PushNotifications.installationId,
                  builder: (BuildContext context, String? fid, _) {
                    return SelectableText(
                      fid ?? 'In attesa di registrazione…',
                      style: theme.textTheme.bodySmall?.copyWith(
                        fontFamily: 'monospace',
                      ),
                    );
                  },
                ),
                const SizedBox(height: 12),
                ValueListenableBuilder<String?>(
                  valueListenable: PushNotifications.installationId,
                  builder: (BuildContext context, String? fid, _) {
                    return OutlinedButton.icon(
                      onPressed: fid == null
                          ? null
                          : () => _copyFid(context, fid),
                      icon: const Icon(Icons.copy_rounded, size: 18),
                      label: const Text('Copia FID'),
                    );
                  },
                ),
                const SizedBox(height: 8),
                Text(
                  'Il FID resta solo in memoria e non viene scritto nei log. '
                  'Non incollarlo in chat o segnalazioni pubbliche.',
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: scheme.onSurfaceVariant,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: AppSpacing.md),
          SectionCard(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Stato', style: theme.textTheme.titleSmall),
                const SizedBox(height: 12),
                ValueListenableBuilder<String>(
                  valueListenable: PushNotifications.permission,
                  builder: (BuildContext context, String value, _) {
                    return _StatusRow(
                      label: 'Permesso notifiche',
                      value: value,
                    );
                  },
                ),
                const Divider(height: 20),
                ValueListenableBuilder<PushRegistrationState>(
                  valueListenable: PushNotifications.registration,
                  builder:
                      (BuildContext context, PushRegistrationState value, _) {
                        return _StatusRow(
                          label: 'Registrazione FCM',
                          value: switch (value) {
                            PushRegistrationState.idle => 'in attesa',
                            PushRegistrationState.registering => 'in corso',
                            PushRegistrationState.registered => 'registrata',
                            PushRegistrationState.failed => 'errore',
                          },
                        );
                      },
                ),
                const Divider(height: 20),
                _StatusRow(
                  label: 'Canale Android',
                  value: PushNotifications.channelId,
                ),
                const Divider(height: 20),
                ValueListenableBuilder<int>(
                  valueListenable: PushNotifications.foregroundMessages,
                  builder: (BuildContext context, int count, _) {
                    return _StatusRow(
                      label: 'Messaggi in foreground',
                      value: '$count',
                    );
                  },
                ),
                ValueListenableBuilder<String?>(
                  valueListenable: PushNotifications.lastForegroundTitle,
                  builder: (BuildContext context, String? value, _) {
                    if (value == null) return const SizedBox.shrink();
                    return Padding(
                      padding: const EdgeInsets.only(top: 8),
                      child: Text(
                        'Ultimo titolo: $value',
                        style: theme.textTheme.bodySmall?.copyWith(
                          color: scheme.onSurfaceVariant,
                        ),
                      ),
                    );
                  },
                ),
              ],
            ),
          ),
          const SizedBox(height: AppSpacing.md),
          SectionCard(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Azioni di prova', style: theme.textTheme.titleSmall),
                const SizedBox(height: 12),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    OutlinedButton.icon(
                      onPressed: PushNotifications.requestPermission,
                      icon: const Icon(Icons.notifications_active_outlined),
                      label: const Text('Richiedi permesso'),
                    ),
                    OutlinedButton.icon(
                      onPressed: PushNotifications.showLocalTest,
                      icon: const Icon(Icons.notifications_none_rounded),
                      label: const Text('Notifica locale'),
                    ),
                  ],
                ),
              ],
            ),
          ),
          const SizedBox(height: AppSpacing.md),
          SectionCard(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Messaggio di prova FCM',
                  style: theme.textTheme.titleSmall,
                ),
                const SizedBox(height: 8),
                Text(
                  '1. Apri Firebase Console → Messaging → Invia messaggio di prova.\n'
                  '2. Incolla il FID come destinatario.\n'
                  '3. Invia una notifica con titolo e testo.\n'
                  '4. Con l’app in foreground compare la notifica locale; '
                  'con l’app in background la mostra Android sul canale '
                  '“${PushNotifications.channelId}”.',
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: scheme.onSurfaceVariant,
                    height: 1.5,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Future<void> _copyFid(BuildContext context, String fid) async {
    await Clipboard.setData(ClipboardData(text: fid));
    if (!context.mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(
        content: Text('FID copiato. Non condividerlo pubblicamente.'),
      ),
    );
  }
}

class _StatusRow extends StatelessWidget {
  const _StatusRow({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    return Row(
      children: [
        Expanded(
          child: Text(
            label,
            style: theme.textTheme.bodySmall?.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
        ),
        const SizedBox(width: AppSpacing.sm),
        Flexible(
          child: Text(
            value,
            textAlign: TextAlign.end,
            style: theme.textTheme.bodySmall?.copyWith(
              fontWeight: FontWeight.w600,
            ),
          ),
        ),
      ],
    );
  }
}
