import 'package:material_ui/material_ui.dart';

import '../../core/theme/app_theme.dart';
import '../../core/widgets/nexus_ui.dart';

class ListingsScreen extends StatelessWidget {
  const ListingsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return const PhasePreview(
      icon: Icons.swap_horiz_rounded,
      phase: 5,
      title: 'Scambia vicino a te',
      description:
          'Offri o cerchi un oggetto nella tua zona: foto, categoria e contatto '
          'diretto, gratis.',
      features: [
        'Due schede: «Offro» e «Cerco»',
        'Foto, categorie e tasto Contatta',
        'Avviso antitruffa sempre visibile e segnalazione degli annunci',
        'Accordi in chat: l\'app non gestisce i soldi',
        'Feedback a stelle dopo lo scambio concluso',
      ],
      accent: AppColors.brandOrange,
    );
  }
}
