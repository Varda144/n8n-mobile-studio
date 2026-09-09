import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class AppTheme {
  // Design System Colors (UI/UX Pro Max - OLED Dark Mode)
  static const Color primaryColor = Color(0xFF16A34A);       // Accent/CTA - running green
  static const Color primaryDark = Color(0xFF0F172A);        // Primary - deep navy
  static const Color secondaryColor = Color(0xFF1E293B);     // Secondary
  static const Color background = Color(0xFF020617);         // Background - true OLED black
  static const Color surface = Color(0xFF0E1223);            // Card
  static const Color surfaceAlt = Color(0xFF1A1E2F);         // Muted
  static const Color foreground = Color(0xFFF8FAFC);         // Foreground - near white
  static const Color mutedFg = Color(0xFF94A3B8);            // Muted foreground - slate
  static const Color border = Color(0xFF334155);             // Border
  static const Color successColor = Color(0xFF16A34A);       // Running/success
  static const Color errorColor = Color(0xFFDC2626);         // Failed/destructive
  static const Color warningColor = Color(0xFFF59E0B);       // Queued/amber
  static const Color infoColor = Color(0xFF3B82F6);          // Info blue
  static const Color canvasBg = Color(0xFF0F172A);           // Editor canvas
  static const Color nodeBg = Color(0xFF1E293B);             // Node background
  static const Color connectionColor = Color(0xFF475569);    // Connections
  static const Color glowColor = Color(0xFF16A34A);          // Glow accent

  static ThemeData get darkTheme {
    return ThemeData(
      useMaterial3: true,
      brightness: Brightness.dark,
      scaffoldBackgroundColor: background,
      colorScheme: ColorScheme.dark(
        primary: primaryColor,
        secondary: secondaryColor,
        surface: surface,
        error: errorColor,
        onPrimary: const Color(0xFF0F172A),
        onSecondary: Colors.white,
        onSurface: foreground,
      ),
      appBarTheme: const AppBarTheme(
        backgroundColor: background,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        centerTitle: false,
        systemOverlayStyle: SystemUiOverlayStyle.light,
        titleTextStyle: TextStyle(
          color: foreground,
          fontSize: 20,
          fontWeight: FontWeight.w700,
          letterSpacing: -0.5,
        ),
      ),
      cardTheme: CardTheme(
        color: surface,
        elevation: 0,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(14),
          side: const BorderSide(color: border, width: 1),
        ),
      ),
      navigationBarTheme: NavigationBarThemeData(
        backgroundColor: const Color(0xFF0A0E1A),
        surfaceTintColor: Colors.transparent,
        indicatorColor: primaryColor.withOpacity(0.15),
        elevation: 8,
        shadowColor: Colors.black.withOpacity(0.5),
        labelTextStyle: WidgetStateProperty.resolveWith((states) {
          if (states.contains(WidgetState.selected)) {
            return const TextStyle(fontSize: 11, fontWeight: FontWeight.w600, color: primaryColor);
          }
          return TextStyle(fontSize: 11, color: mutedFg);
        }),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: const Color(0xFF111827),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: const BorderSide(color: border),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: const BorderSide(color: border),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: const BorderSide(color: primaryColor, width: 2),
        ),
        hintStyle: const TextStyle(color: Color(0xFF475569)),
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
      ),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          backgroundColor: primaryColor,
          foregroundColor: const Color(0xFF0F172A),
          elevation: 0,
          shadowColor: primaryColor.withOpacity(0.4),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 14),
          textStyle: const TextStyle(fontWeight: FontWeight.w700, fontSize: 14),
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          foregroundColor: foreground,
          side: const BorderSide(color: border),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 14),
        ),
      ),
      chipTheme: ChipThemeData(
        backgroundColor: const Color(0xFF111827),
        selectedColor: primaryColor.withOpacity(0.2),
        side: const BorderSide(color: border),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
        labelStyle: const TextStyle(fontSize: 12),
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      ),
      switchTheme: SwitchThemeData(
        thumbColor: WidgetStateProperty.resolveWith((states) {
          if (states.contains(WidgetState.selected)) return primaryColor;
          return const Color(0xFF475569);
        }),
        trackColor: WidgetStateProperty.resolveWith((states) {
          if (states.contains(WidgetState.selected)) return primaryColor.withOpacity(0.3);
          return const Color(0xFF1E293B);
        }),
      ),
      popupMenuTheme: PopupMenuThemeData(
        color: const Color(0xFF111827),
        surfaceTintColor: Colors.transparent,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(12),
          side: const BorderSide(color: border),
        ),
      ),
      snackBarTheme: SnackBarThemeData(
        backgroundColor: const Color(0xFF111827),
        contentTextStyle: const TextStyle(color: foreground),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
        behavior: SnackBarBehavior.floating,
      ),
      dividerTheme: const DividerThemeData(
        color: border,
        thickness: 1,
        space: 1,
      ),
      bottomSheetTheme: const BottomSheetThemeData(
        backgroundColor: Color(0xFF111827),
        surfaceTintColor: Colors.transparent,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
        ),
      ),
    );
  }

  static ThemeData get lightTheme {
    return ThemeData(
      useMaterial3: true,
      brightness: Brightness.light,
      scaffoldBackgroundColor: const Color(0xFFF8FAFC),
      colorScheme: ColorScheme.light(
        primary: primaryColor,
        secondary: secondaryColor,
        surface: Colors.white,
        error: errorColor,
      ),
      appBarTheme: const AppBarTheme(
        backgroundColor: Colors.white,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        centerTitle: false,
        foregroundColor: Color(0xFF0F172A),
        titleTextStyle: TextStyle(
          color: Color(0xFF0F172A),
          fontSize: 20,
          fontWeight: FontWeight.w700,
        ),
      ),
      cardTheme: CardTheme(
        color: Colors.white,
        elevation: 0,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(14),
          side: const BorderSide(color: Color(0xFFE2E8F0)),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: const Color(0xFFF1F5F9),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: const BorderSide(color: Color(0xFFE2E8F0)),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: const BorderSide(color: Color(0xFFE2E8F0)),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: const BorderSide(color: primaryColor, width: 2),
        ),
      ),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          backgroundColor: primaryColor,
          foregroundColor: Colors.white,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 14),
        ),
      ),
    );
  }

  // Glow effect for dark mode
  static List<BoxShadow> glowShadow(Color color, {double blur = 20, double spread = 0}) {
    return [
      BoxShadow(
        color: color.withOpacity(0.3),
        blurRadius: blur,
        spreadRadius: spread,
      ),
    ];
  }

  // Status color helper
  static Color statusColor(String status) {
    switch (status.toLowerCase()) {
      case 'active': case 'running': case 'success': return successColor;
      case 'error': case 'failed': case 'inactive': return errorColor;
      case 'warning': case 'queued': case 'draft': return warningColor;
      default: return infoColor;
    }
  }

  // Node type color for editor
  static Color nodeTypeColor(String type) {
    switch (type) {
      case 'trigger': return const Color(0xFFF59E0B);
      case 'action': return const Color(0xFF3B82F6);
      case 'condition': return const Color(0xFF8B5CF6);
      case 'output': return primaryColor;
      case 'http': return const Color(0xFF06B6D4);
      case 'database': return const Color(0xFFEC4899);
      default: return primaryColor;
    }
  }
}
