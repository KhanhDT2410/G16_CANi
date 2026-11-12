// CANi · tab "Tài khoản" — adaptive theme extension tokens (Light & Dark).
// Consumer widgets only ever read Theme.of(context).profileX — automatically adapts.

import 'package:flutter/material.dart';

/// Exposes the token set as a typed bag off any active ThemeData.
extension ProfileTokensX on ThemeData {
  ProfileTokens get profileX => brightness == Brightness.dark
      ? const ProfileTokens.dark()
      : const ProfileTokens.light();
}

/// Immutable tokens supporting both Clean Sky Light and Slate Dark palettes.
class ProfileTokens {
  const ProfileTokens.light()
      : canvas = const Color(0xFFF4FCFE),
        surface = const Color(0xFFFFFFFF),
        text = const Color(0xFF03045E),
        muted = const Color(0xFF5B6B85),
        border = const Color(0xFF90E0EF),
        accent = const Color(0xFF00B4D8),
        accentSoft = const Color(0xFFE4F7FB),
        brand = const Color(0xFF3B82F6),
        brandSoft = const Color(0xFFEAF1FE),
        accentDeep = const Color(0xFF0066FF),
        success = const Color(0xFF16A34A),
        danger = const Color(0xFFDC2626),
        skeleton = const Color(0xFFDCE7F0),
        chipSoft = const Color(0xFFFDF6DE),
        shadowColor = const Color(0x261E4A6B);

  const ProfileTokens.dark()
      : canvas = const Color(0xFF0F172A),
        surface = const Color(0xFF1E293B),
        text = const Color(0xFFF8FAFC),
        muted = const Color(0xFF94A3B8),
        border = const Color(0xFF334155),
        accent = const Color(0xFF38BDF8),
        accentSoft = const Color(0xFF0C4A6E),
        brand = const Color(0xFF3B82F6),
        brandSoft = const Color(0xFF1E3A8A),
        accentDeep = const Color(0xFF60A5FA),
        success = const Color(0xFF22C55E),
        danger = const Color(0xFFEF4444),
        skeleton = const Color(0xFF334155),
        chipSoft = const Color(0xFF334155),
        shadowColor = const Color(0x66000000);

  final Color canvas;
  final Color surface;
  final Color text;
  final Color muted;
  final Color border;
  final Color accent;
  final Color accentSoft;
  final Color brand;
  final Color brandSoft;
  final Color accentDeep;
  final Color success;
  final Color danger;
  final Color skeleton;
  final Color chipSoft;
  final Color shadowColor;

  /// Comfortable density: card radius + shared paddings/gaps.
  double get radius => 18;
  double get cardPadding => 20;
  double get blockGap => 24;
}
