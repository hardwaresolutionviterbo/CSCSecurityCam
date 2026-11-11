import 'package:material_ui/material_ui.dart';

import '../../core/config/app_config.dart';
import '../../core/services/backend_bootstrap.dart';
import '../../core/theme/app_theme.dart';
import '../../core/widgets/nexus_ui.dart';

class ProfileScreen extends StatelessWidget {
  const ProfileScreen({
    super.key,
    this.backend = const BackendStatus.unconfigured(),
  });

  final BackendStatus backend;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    final ColorScheme scheme = theme.colorScheme;

    return ListView(
      padding: AppSpacing.pagePadding,
      children: [
        SectionCard(
          padding: const EdgeInsets.all(20),
          child: Row(
            children: [
              Container(
                width: 64,
                height: 64,
                decoration: const BoxDecoration(
                  shape: BoxShape.circle,
                  gradient: AppColors.brandGradient,
                ),
                child: const Icon(
                  Icons.person_rounded,
                  color: Colors.white,
                  size: 30,
                ),
              ),
              const SizedBox(width: AppSpacing.md),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Profilo non configurato',
                      style: theme.textTheme.titleMedium,
                    ),
                    const SizedBox(height: 4),
                    Text(
                      'Registrati con il numero di telefono per iniziare.',
                      style: theme.textTheme.bodySmall?.copyWith(
                        color: scheme.onSurfaceVariant,
                      ),
                    ),
                    const SizedBox(height: 10),
                    const PhaseChip(phase: 2),
                  ],
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
              Row(
                children: [
                  Icon(
                    Icons.cloud_queue_rounded,
                    size: 18,
                    color: scheme.primary,
                  ),
                  const SizedBox(width: AppSpacing.sm),
                  Text('Stato dei servizi', style: theme.textTheme.titleSmall),
                ],
              ),
              const SizedBox(height: 14),
              _ServiceRow(
                name: 'Supabase',
                detail: 'Annunci, distanza, foto, segnalazioni',
                state: backend.supabase,
              ),
              const Divider(height: 22),
              _ServiceRow(
                name: 'Firebase',
                detail: 'Accesso SMS, chat, chiamate, notifiche',
                state: backend.firebase,
              ),
              if (!backend.anyConnected) ...[
                const SizedBox(height: 14),
                Text(
                  'Aggiungi le chiavi in env.json (o nelle variabili di Codemagic) '
                  'per collegare i servizi.',
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: scheme.onSurfaceVariant,
                  ),
                ),
              ],
              if (backend.message != null) ...[
                const SizedBox(height: 10),
                Text(
                  backend.message!,
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: scheme.error,
                  ),
                ),
              ],
            ],
          ),
        ),
        const SizedBox(height: AppSpacing.md),
        SectionCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('Il tuo account', style: theme.textTheme.titleSmall),
              const SizedBox(height: 14),
              _AccountTypeRow(
                icon: Icons.person_outline_rounded,
                title: 'Privato',
                detail: 'Annunci, scambi, feedback e media delle stelle',
              ),
              const Divider(height: 22),
              _AccountTypeRow(
                icon: Icons.storefront_outlined,
                title: 'Azienda',
                detail: 'Badge verificato, promozioni e coupon (9,99 €/mese)',
              ),
            ],
          ),
        ),
        const SizedBox(height: AppSpacing.md),
        SectionCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('Informazioni', style: theme.textTheme.titleSmall),
              const SizedBox(height: 14),
              _InfoRow(label: 'Versione', value: '${AppInfo.version} (beta)'),
              const SizedBox(height: 10),
              _InfoRow(label: 'Fase', value: AppInfo.phaseLabel),
              const SizedBox(height: 10),
              _InfoRow(label: 'Privacy', value: 'Posizione approssimata'),
            ],
          ),
        ),
        const SizedBox(height: AppSpacing.md),
        Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(
              Icons.lock_outline_rounded,
              size: 14,
              color: scheme.onSurfaceVariant,
            ),
            const SizedBox(width: 6),
            Text(
              'Costruito una fase alla volta',
              style: theme.textTheme.bodySmall?.copyWith(
                color: scheme.onSurfaceVariant,
              ),
            ),
          ],
        ),
      ],
    );
  }
}

class _ServiceRow extends StatelessWidget {
  const _ServiceRow({
    required this.name,
    required this.detail,
    required this.state,
  });

  final String name;
  final String detail;
  final ServiceState state;

  String get _label => switch (state) {
    ServiceState.connected => 'Collegato',
    ServiceState.failed => 'Errore',
    ServiceState.notConfigured => 'Non configurato',
  };

  Color _color(ColorScheme scheme) => switch (state) {
    ServiceState.connected => AppColors.brandMint,
    ServiceState.failed => scheme.error,
    ServiceState.notConfigured => scheme.onSurfaceVariant,
  };

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    final ColorScheme scheme = theme.colorScheme;

    return Row(
      children: [
        Container(
          width: 8,
          height: 8,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            color: _color(scheme),
          ),
        ),
        const SizedBox(width: 10),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(name, style: theme.textTheme.bodyMedium?.copyWith(
                fontWeight: FontWeight.w600,
              )),
              Text(
                detail,
                style: theme.textTheme.bodySmall?.copyWith(
                  color: scheme.onSurfaceVariant,
                ),
              ),
            ],
          ),
        ),
        Text(
          _label,
          style: theme.textTheme.labelSmall?.copyWith(
            color: _color(scheme),
            fontWeight: FontWeight.w700,
          ),
        ),
      ],
    );
  }
}

class _AccountTypeRow extends StatelessWidget {
  const _AccountTypeRow({
    required this.icon,
    required this.title,
    required this.detail,
  });

  final IconData icon;
  final String title;
  final String detail;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    final ColorScheme scheme = theme.colorScheme;

    return Row(
      children: [
        Container(
          width: 38,
          height: 38,
          decoration: BoxDecoration(
            color: scheme.primary.withValues(alpha: 0.12),
            borderRadius: BorderRadius.circular(12),
          ),
          child: Icon(icon, size: 19, color: scheme.primary),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(title, style: theme.textTheme.bodyMedium?.copyWith(
                fontWeight: FontWeight.w600,
              )),
              Text(
                detail,
                style: theme.textTheme.bodySmall?.copyWith(
                  color: scheme.onSurfaceVariant,
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

class _InfoRow extends StatelessWidget {
  const _InfoRow({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    return Row(
      children: [
        Text(
          label,
          style: theme.textTheme.bodySmall?.copyWith(
            color: theme.colorScheme.onSurfaceVariant,
          ),
        ),
        const Spacer(),
        Text(value, style: theme.textTheme.bodySmall),
      ],
    );
  }
}
