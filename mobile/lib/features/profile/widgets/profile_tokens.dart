// CANi · tab "Tài khoản" — local theme extension (Direction A "Clean Sky").
// READ-ONLY extension on ThemeData: profile-local tokens, does NOT touch theme.dart
// global. Satisfies MUST "toàn bộ màu qua theme/extension tokens": consumer
// widgets only ever read Theme.of(context).profileX — no hardcoded colors in views.

import 'package:flutter/material.dart';

/// Exposes the Clean Sky token set as a typed bag off any active ThemeData.
extension ProfileTokensX on ThemeData {
  ProfileTokens get profileX => const ProfileTokens._();
}

/// Immutable Direction-A tokens. The one sanctioned place the palette lives.
class ProfileTokens {
  const ProfileTokens._()
      : canvas = const Color(0xFFF4FCFE),
        surface = const Color(0xFFFFFFFF),
        text = const Color(0xFF03045E),
        muted = const Color(0xFF5B6B85),
        border = const Color(0xFF90E0EF),
        accent = const Color(0xFF00B4D8),
        accentSoft = const Color(0xFFE4F7FB),
        brand = const Color(0xFF3B82F6), // brand blue — anchor (kept)
        brandSoft = const Color(0xFFEAF1FE),
        accentDeep = const Color(0xFF0066FF), // accent — anchor (kept)
        success = const Color(0xFF16A34A),
        danger = const Color(0xFFDC2626),
        skeleton = const Color(0xFFDCE7F0),
        chipSoft = const Color(0xFFFDF6DE),
        shadowColor = const Color(0x261E4A6B);

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
