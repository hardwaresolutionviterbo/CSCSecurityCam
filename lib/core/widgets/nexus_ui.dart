import 'package:material_ui/material_ui.dart';

import '../theme/app_theme.dart';

/// Monogramma del brand: N su gradiente viola→menta.
class NexusMark extends StatelessWidget {
  const NexusMark({super.key, this.size = 32});

  final double size;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      label: 'Nexus Social',
      child: Container(
        width: size,
        height: size,
        alignment: Alignment.center,
        decoration: BoxDecoration(
          gradient: AppColors.brandGradient,
          borderRadius: BorderRadius.circular(size * 0.32),
          boxShadow: [
            BoxShadow(
              color: AppColors.brandViolet.withValues(alpha: 0.35),
              blurRadius: size * 0.55,
              offset: Offset(0, size * 0.14),
            ),
          ],
        ),
        child: ExcludeSemantics(
          child: Text(
            'N',
            style: TextStyle(
              color: Colors.white,
              fontSize: size * 0.54,
              fontWeight: FontWeight.w800,
              height: 1,
            ),
          ),
        ),
      ),
    );
  }
}

/// Contenitore arrotondato usato per le sezioni delle schermate.
class SectionCard extends StatelessWidget {
  const SectionCard({
    super.key,
    required this.child,
    this.padding = const EdgeInsets.all(18),
  });

  final Widget child;
  final EdgeInsetsGeometry padding;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(padding: padding, child: child),
    );
  }
}

/// Etichetta «Fase N» che indica quando arriverà una funzione.
class PhaseChip extends StatelessWidget {
  const PhaseChip({super.key, required this.phase});

  final int phase;

  @override
  Widget build(BuildContext context) {
    final ColorScheme scheme = Theme.of(context).colorScheme;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
      decoration: BoxDecoration(
        color: scheme.primary.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(999),
        border: Border.all(color: scheme.primary.withValues(alpha: 0.35)),
      ),
      child: Text(
        'Fase $phase',
        style: Theme.of(context).textTheme.labelMedium?.copyWith(
          color: scheme.primary,
          fontWeight: FontWeight.w700,
        ),
      ),
    );
  }
}

/// Anteprima di una funzione futura: icona, titolo, descrizione ed elenco di cosa arriverà.
class PhasePreview extends StatelessWidget {
  const PhasePreview({
    super.key,
    required this.icon,
    required this.phase,
    required this.title,
    required this.description,
    this.features = const <String>[],
    this.footer,
    this.accent,
  });

  final IconData icon;
  final int phase;
  final String title;
  final String description;
  final List<String> features;
  final Widget? footer;
  final Color? accent;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    final ColorScheme scheme = theme.colorScheme;
    final Color color = accent ?? scheme.primary;

    return ListView(
      padding: AppSpacing.pagePadding,
      children: [
        Row(
          children: [
            Container(
              width: 58,
              height: 58,
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                  colors: [
                    color.withValues(alpha: 0.95),
                    color.withValues(alpha: 0.45),
                  ],
                ),
                borderRadius: BorderRadius.circular(20),
                boxShadow: [
                  BoxShadow(
                    color: color.withValues(alpha: 0.28),
                    blurRadius: 22,
                    offset: const Offset(0, 8),
                  ),
                ],
              ),
              child: Icon(icon, color: Colors.white, size: 28),
            ),
            const Spacer(),
            PhaseChip(phase: phase),
          ],
        ),
        const SizedBox(height: 22),
        Text(title, style: theme.textTheme.headlineSmall),
        const SizedBox(height: 8),
        Text(
          description,
          style: theme.textTheme.bodyMedium?.copyWith(
            color: scheme.onSurfaceVariant,
          ),
        ),
        if (features.isNotEmpty) ...[
          const SizedBox(height: AppSpacing.lg),
          SectionCard(
            child: Column(
              children: [
                for (int i = 0; i < features.length; i++) ...[
                  if (i > 0) const Divider(height: 22),
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Icon(
                        Icons.check_circle_rounded,
                        size: 18,
                        color: scheme.secondary,
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Text(
                          features[i],
                          style: theme.textTheme.bodyMedium,
                        ),
                      ),
                    ],
                  ),
                ],
              ],
            ),
          ),
        ],
        if (footer != null) ...[
          const SizedBox(height: AppSpacing.md),
          footer!,
        ],
        const SizedBox(height: AppSpacing.lg),
        Center(
          child: Text(
            'Disponibile nella Fase $phase',
            style: theme.textTheme.bodySmall?.copyWith(
              color: scheme.onSurfaceVariant,
            ),
          ),
        ),
      ],
    );
  }
}

/// Campanella con contatore e foglio delle notifiche.
class NotificationBell extends StatelessWidget {
  const NotificationBell({super.key, this.count = 0, this.onPressed});

  final int count;
  final VoidCallback? onPressed;

  static Future<void> showNotifications(BuildContext context) {
    return showModalBottomSheet<void>(
      context: context,
      showDragHandle: true,
      useSafeArea: true,
      backgroundColor: Theme.of(context).colorScheme.surface,
      builder: (_) => const _NotificationsSheet(),
    );
  }

  @override
  Widget build(BuildContext context) {
    return IconButton(
      tooltip: 'Notifiche',
      onPressed: onPressed ?? () => showNotifications(context),
      icon: Badge.count(
        count: count,
        isLabelVisible: count > 0,
        backgroundColor: AppColors.brandCoral,
        child: const Icon(Icons.notifications_none_rounded),
      ),
    );
  }
}

class _NotificationsSheet extends StatelessWidget {
  const _NotificationsSheet();

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.fromLTRB(24, 4, 24, 32),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Row(
            children: [
              Text('Notifiche', style: theme.textTheme.titleLarge),
              const Spacer(),
              const PhaseChip(phase: 4),
            ],
          ),
          const SizedBox(height: AppSpacing.lg),
          Container(
            width: 64,
            height: 64,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: theme.colorScheme.primary.withValues(alpha: 0.12),
            ),
            child: Icon(
              Icons.notifications_none_rounded,
              color: theme.colorScheme.primary,
              size: 30,
            ),
          ),
          const SizedBox(height: AppSpacing.md),
          Text('Nessuna notifica per ora', style: theme.textTheme.titleMedium),
          const SizedBox(height: 6),
          Text(
            'Qui arriveranno messaggi, richieste di contatto e avvisi importanti.',
            textAlign: TextAlign.center,
            style: theme.textTheme.bodySmall?.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
        ],
      ),
    );
  }
}
