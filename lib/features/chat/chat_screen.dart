import 'package:material_ui/material_ui.dart';

import '../../core/widgets/nexus_ui.dart';

class ChatScreen extends StatelessWidget {
  const ChatScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return const PhasePreview(
      icon: Icons.forum_outlined,
      phase: 4,
      title: 'Chat e chiamate',
      description:
          'Messaggi uno a uno e di gruppo, vocali, foto, video e chiamate, '
          'con crittografia end-to-end.',
      features: [
        'Crittografia end-to-end con un protocollo collaudato',
        'Stato inviato, consegnato e letto, risposte, inoltri e reazioni',
        'Richieste di messaggio dagli sconosciuti: il numero non si vede mai',
        'Chiamate vocali e videochiamate, anche di gruppo',
        'Notifiche con anteprima decifrata solo sul telefono',
        'Condividi l\'incontro con una persona fidata prima di vederti con uno sconosciuto',
      ],
    );
  }
}
