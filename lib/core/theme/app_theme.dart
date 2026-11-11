import 'package:material_ui/material_ui.dart';

/// Palette del brand: viola elettrico, menta e corallo su fondo notte.
abstract final class AppColors {
  static const Color brandViolet = Color(0xFF7C6CFF);
  static const Color brandMint = Color(0xFF2EE6C5);
  static const Color brandCoral = Color(0xFFFF6B9A);
  static const Color brandAmber = Color(0xFFFFC46B);

  static const Color darkBackground = Color(0xFF070A12);
  static const Color darkSurface = Color(0xFF111726);
  static const Color darkSurfaceHigh = Color(0xFF1A2235);
  static const Color darkOutline = Color(0xFF2B3550);
  static const Color darkTextSecondary = Color(0xFF9BA8C7);

  static const Color lightBackground = Color(0xFFF5F6FC);
  static const Color lightSurface = Color(0xFFFFFFFF);
  static const Color lightSurfaceHigh = Color(0xFFEDF0FA);
  static const Color lightOutline = Color(0xFFDCE2F1);
  static const Color lightTextSecondary = Color(0xFF5A6786);

  static const LinearGradient brandGradient = LinearGradient(
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
    colors: [brandViolet, brandMint],
  );
}

abstract final class AppSpacing {
  static const double xs = 4;
  static const double sm = 8;
  static const double md = 16;
  static const double lg = 24;
  static const double xl = 32;

  /// Margine delle schermate; il fondo lascia spazio alla barra di navigazione.
  static const EdgeInsets pagePadding = EdgeInsets.fromLTRB(20, 8, 20, 120);
}

abstract final class AppTheme {
  static ThemeData get dark => _build(Brightness.dark);
  static ThemeData get light => _build(Brightness.light);

  static ThemeData _build(Brightness brightness) {
    final bool isDark = brightness == Brightness.dark;
    final ColorScheme base = ColorScheme.fromSeed(
      seedColor: AppColors.brandViolet,
      brightness: brightness,
    );
    final ColorScheme scheme = base.copyWith(
      primary: isDark ? AppColors.brandViolet : const Color(0xFF5B49E8),
      onPrimary: Colors.white,
      secondary: isDark ? AppColors.brandMint : const Color(0xFF067F6D),
      onSecondary: isDark ? const Color(0xFF00201B) : Colors.white,
      tertiary: AppColors.brandCoral,
      surface: isDark ? AppColors.darkSurface : AppColors.lightSurface,
      onSurface: isDark ? const Color(0xFFF3F5FF) : const Color(0xFF111827),
      onSurfaceVariant: isDark
          ? AppColors.darkTextSecondary
          : AppColors.lightTextSecondary,
      surfaceContainerHighest: isDark
          ? AppColors.darkSurfaceHigh
          : AppColors.lightSurfaceHigh,
      outline: isDark ? AppColors.darkOutline : AppColors.lightOutline,
    );

    final BorderRadius radius = BorderRadius.circular(22);

    return ThemeData(
      useMaterial3: true,
      brightness: brightness,
      colorScheme: scheme,
      scaffoldBackgroundColor: isDark
          ? AppColors.darkBackground
          : AppColors.lightBackground,
      textTheme: _textTheme(scheme),
      appBarTheme: const AppBarTheme(
        backgroundColor: Colors.transparent,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        centerTitle: false,
      ),
      cardTheme: CardThemeData(
        color: scheme.surface,
        elevation: 0,
        margin: EdgeInsets.zero,
        shape: RoundedRectangleBorder(
          borderRadius: radius,
          side: BorderSide(color: scheme.outline.withValues(alpha: 0.7)),
        ),
      ),
      navigationBarTheme: NavigationBarThemeData(
        backgroundColor: Colors.transparent,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        height: 70,
        indicatorColor: scheme.primary.withValues(alpha: 0.18),
        labelBehavior: NavigationDestinationLabelBehavior.alwaysShow,
        labelTextStyle: const WidgetStatePropertyAll<TextStyle>(
          TextStyle(fontSize: 11, fontWeight: FontWeight.w600, letterSpacing: 0.1),
        ),
        iconTheme: WidgetStateProperty.resolveWith<IconThemeData>((states) {
          final bool selected = states.contains(WidgetState.selected);
          return IconThemeData(
            color: selected ? scheme.primary : scheme.onSurfaceVariant,
            size: 24,
          );
        }),
      ),
      chipTheme: ChipThemeData(
        backgroundColor: scheme.surfaceContainerHighest,
        side: BorderSide(color: scheme.outline.withValues(alpha: 0.7)),
        labelStyle: TextStyle(
          color: scheme.onSurface,
          fontWeight: FontWeight.w600,
          fontSize: 12,
        ),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(999)),
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      ),
      dividerTheme: DividerThemeData(
        color: scheme.outline.withValues(alpha: 0.6),
        thickness: 1,
        space: 1,
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
          padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 14),
          textStyle: const TextStyle(fontWeight: FontWeight.w700),
        ),
      ),
      snackBarTheme: SnackBarThemeData(
        behavior: SnackBarBehavior.floating,
        backgroundColor: scheme.surfaceContainerHighest,
        contentTextStyle: TextStyle(color: scheme.onSurface),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
      ),
    );
  }

  static TextTheme _textTheme(ColorScheme scheme) {
    final TextTheme base = ThemeData(brightness: scheme.brightness).textTheme;
    return base
        .copyWith(
          headlineMedium: base.headlineMedium?.copyWith(
            fontWeight: FontWeight.w800,
            letterSpacing: -0.8,
          ),
          headlineSmall: base.headlineSmall?.copyWith(
            fontWeight: FontWeight.w800,
            letterSpacing: -0.6,
          ),
          titleLarge: base.titleLarge?.copyWith(
            fontWeight: FontWeight.w700,
            letterSpacing: -0.3,
          ),
          titleMedium: base.titleMedium?.copyWith(
            fontWeight: FontWeight.w700,
            letterSpacing: -0.2,
          ),
          titleSmall: base.titleSmall?.copyWith(fontWeight: FontWeight.w700),
          bodyMedium: base.bodyMedium?.copyWith(height: 1.45),
          bodySmall: base.bodySmall?.copyWith(height: 1.4),
        )
        .apply(bodyColor: scheme.onSurface, displayColor: scheme.onSurface);
  }
}
