import 'package:material_ui/material_ui.dart';

import '../../core/services/backend_bootstrap.dart';
import '../../core/widgets/nexus_ui.dart';
import '../chat/chat_screen.dart';
import '../listings/listings_screen.dart';
import '../map/map_screen.dart';
import '../profile/profile_screen.dart';
import '../sale/sale_screen.dart';

/// Impalcatura dell'app: barra in alto con marchio e notifiche, cinque sezioni in basso.
class AppShell extends StatefulWidget {
  const AppShell({super.key, this.backend = const BackendStatus.unconfigured()});

  final BackendStatus backend;

  @override
  State<AppShell> createState() => _AppShellState();
}

class _AppShellState extends State<AppShell> {
  static const List<String> _titles = <String>[
    'Mappa',
    'Chat',
    'Scambia',
    'Vendita',
    'Profilo',
  ];

  late final List<Widget> _screens = <Widget>[
    const MapScreen(),
    const ChatScreen(),
    const ListingsScreen(),
    const SaleScreen(),
    ProfileScreen(backend: widget.backend),
  ];

  int _index = 0;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    final ColorScheme scheme = theme.colorScheme;
    final bool isDark = theme.brightness == Brightness.dark;

    return Scaffold(
      extendBody: true,
      appBar: AppBar(
        titleSpacing: 20,
        title: Row(
          children: [
            const NexusMark(size: 32),
            const SizedBox(width: 10),
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  'Nexus Social',
                  style: theme.textTheme.titleSmall?.copyWith(
                    fontWeight: FontWeight.w800,
                  ),
                ),
                Text(
                  _titles[_index],
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: scheme.onSurfaceVariant,
                    fontSize: 11,
                  ),
                ),
              ],
            ),
          ],
        ),
        actions: const [NotificationBell(), SizedBox(width: 8)],
      ),
      body: Stack(
        children: [
          const _Backdrop(),
          SafeArea(
            top: false,
            child: IndexedStack(index: _index, children: _screens),
          ),
        ],
      ),
      bottomNavigationBar: SafeArea(
        top: false,
        minimum: const EdgeInsets.fromLTRB(16, 0, 16, 12),
        child: DecoratedBox(
          decoration: BoxDecoration(
            color: scheme.surface,
            borderRadius: BorderRadius.circular(26),
            border: Border.all(color: scheme.outline.withValues(alpha: 0.7)),
            boxShadow: [
              BoxShadow(
                color: Colors.black.withValues(alpha: isDark ? 0.45 : 0.08),
                blurRadius: 28,
                offset: const Offset(0, 10),
              ),
            ],
          ),
          child: ClipRRect(
            borderRadius: BorderRadius.circular(26),
            child: NavigationBar(
              selectedIndex: _index,
              onDestinationSelected: (int value) =>
                  setState(() => _index = value),
              destinations: const [
                NavigationDestination(
                  icon: Icon(Icons.map_outlined),
                  selectedIcon: Icon(Icons.map_rounded),
                  label: 'Mappa',
                ),
                NavigationDestination(
                  icon: Icon(Icons.chat_bubble_outline_rounded),
                  selectedIcon: Icon(Icons.chat_bubble_rounded),
                  label: 'Chat',
                ),
                NavigationDestination(
                  icon: Icon(Icons.swap_horiz_rounded),
                  selectedIcon: Icon(Icons.swap_horiz_rounded),
                  label: 'Scambia',
                ),
                NavigationDestination(
                  icon: Icon(Icons.sell_outlined),
                  selectedIcon: Icon(Icons.sell_rounded),
                  label: 'Vendita',
                ),
                NavigationDestination(
                  icon: Icon(Icons.person_outline_rounded),
                  selectedIcon: Icon(Icons.person_rounded),
                  label: 'Profilo',
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// Sfumatura di fondo: viola in alto a sinistra, menta in basso a destra.
class _Backdrop extends StatelessWidget {
  const _Backdrop();

  @override
  Widget build(BuildContext context) {
    final ColorScheme scheme = Theme.of(context).colorScheme;
    return DecoratedBox(
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [
            scheme.primary.withValues(alpha: 0.10),
            Colors.transparent,
            scheme.secondary.withValues(alpha: 0.06),
          ],
          stops: const [0, 0.45, 1],
        ),
      ),
      child: const SizedBox.expand(),
    );
  }
}
