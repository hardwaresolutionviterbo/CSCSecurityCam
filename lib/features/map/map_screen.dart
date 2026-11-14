import 'package:material_ui/material_ui.dart';

import '../../core/theme/app_theme.dart';
import '../../core/widgets/nexus_ui.dart';

class MapScreen extends StatefulWidget {
  const MapScreen({super.key});

  @override
  State<MapScreen> createState() => _MapScreenState();
}

class _MapScreenState extends State<MapScreen> {
  int _radiusKm = 10;

  @override
  Widget build(BuildContext context) {
    return PhasePreview(
      icon: Icons.map_outlined,
      phase: 3,
      accent: AppColors.brandCyan,
      title: 'Mappa Sociale',
      description:
          'Qui vedrai le persone, gli annunci e le promozioni intorno a te, '
          'su una mappa 3D centrata sulla tua zona.',
      features: const [
        'Posizione aggiornata solo ogni poche centinaia di metri, per non consumare batteria',
        'La posizione esatta non è mai visibile: gli annunci compaiono in una zona approssimata',
        'Annunci, promozioni, eventi di gruppo e stato «Disponibile ora»',
        'Tocca un avatar per aprire la scheda e contattare la persona',
      ],
      footer: _RadiusSelector(
        radiusKm: _radiusKm,
        onChanged: (int value) => setState(() => _radiusKm = value),
      ),
    );
  }
}

class _RadiusSelector extends StatelessWidget {
  const _RadiusSelector({required this.radiusKm, required this.onChanged});

  final int radiusKm;
  final ValueChanged<int> onChanged;

  static const List<int> _options = <int>[1, 5, 10, 30];

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    final ColorScheme scheme = theme.colorScheme;

    return SectionCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(Icons.radar_rounded, size: 18, color: scheme.primary),
              const SizedBox(width: AppSpacing.sm),
              Text('Raggio degli annunci', style: theme.textTheme.titleSmall),
              const Spacer(),
              Text(
                '$radiusKm km',
                style: theme.textTheme.labelLarge?.copyWith(
                  color: scheme.primary,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ],
          ),
          const SizedBox(height: AppSpacing.md),
          Wrap(
            spacing: AppSpacing.sm,
            runSpacing: AppSpacing.sm,
            children: [
              for (final int option in _options)
                ChoiceChip(
                  label: Text('$option km'),
                  selected: option == radiusKm,
                  onSelected: (_) => onChanged(option),
                ),
            ],
          ),
          const SizedBox(height: AppSpacing.md),
          Text(
            'Parte da 10 km e arriva fino a 30 km: chi è a Milano non vede gli '
            'annunci di Torino. La posizione mostrata resta approssimata.',
            style: theme.textTheme.bodySmall?.copyWith(
              color: scheme.onSurfaceVariant,
            ),
          ),
        ],
      ),
    );
  }
}
