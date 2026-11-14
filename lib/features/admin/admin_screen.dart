import 'package:material_ui/material_ui.dart';

import '../../core/theme/app_theme.dart';
import '../../core/widgets/nexus_ui.dart';

class AdminScreen extends StatelessWidget {
  const AdminScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return const PhasePreview(
      icon: Icons.admin_panel_settings_outlined,
      phase: 7,
      title: 'Gestionale Admin',
      description:
          'La tua area riservata per controllare annunci, segnalazioni, utenti '
          'e verifiche delle aziende, dentro l\'app.',
      features: [
        'Annunci: ricerca, modifica, nascondi o elimina',
        'Coda delle segnalazioni con azioni di moderazione',
        'Ban temporaneo o definitivo e sblocco degli utenti',
        'Verifica partita IVA e assegnazione del badge alle aziende',
        'Abbonamenti, incassi e statistiche delle città più attive',
      ],
      accent: AppColors.brandViolet,
    );
  }
}
