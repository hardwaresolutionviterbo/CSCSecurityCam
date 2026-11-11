import 'package:material_ui/material_ui.dart';

import 'app.dart';
import 'core/services/backend_bootstrap.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final BackendStatus backend = await BackendBootstrap.initialize();
  runApp(NexusApp(backend: backend));
}
