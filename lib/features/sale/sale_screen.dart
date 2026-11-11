import 'package:material_ui/material_ui.dart';

import '../../core/theme/app_theme.dart';
import '../../core/widgets/nexus_ui.dart';

class SaleScreen extends StatelessWidget {
  const SaleScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return const PhasePreview(
      icon: Icons.sell_outlined,
      phase: 5,
      title: 'Vendita tra privati',
      description:
          'Pubblica un oggetto con foto, titolo e prezzo; gli accordi si chiudono '
          'in chat, senza pagamenti dentro l\'app.',
      features: [
        'Foto, titolo, prezzo e categoria',
        'Consegna solo ritiro a mano o spedizione',
        'Segna come concluso: solo dopo si può lasciare il feedback',
        'Media delle stelle sul profilo dei privati',
        'Segnalazione rapida di annunci sospetti',
      ],
      accent: AppColors.brandCoral,
    );
  }
}
