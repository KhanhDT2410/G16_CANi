import 'package:flutter/material.dart';

/// CANi brand theme configuration.
///
/// Centralized ThemeData for consistent visual styling across the app.
/// Uses Material 3 design system with a blue seed color.
class AppTheme {
  AppTheme._();

  static const Color _seedColor = Color(0xFF3B82F6);

  static ThemeData get light => ThemeData(
        colorScheme: ColorScheme.fromSeed(
          seedColor: _seedColor,
          brightness: Brightness.light,
        ),
        useMaterial3: true,
      );

  static ThemeData get dark => ThemeData(
        colorScheme: ColorScheme.fromSeed(
          seedColor: _seedColor,
          brightness: Brightness.dark,
        ),
        useMaterial3: true,
      );
}
