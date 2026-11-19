// CANi app-wide design tokens (single source of truth, mirrors web/src/index.css).
// Consumer widgets only ever read Theme.of(context).cani — automatically adapts.

import 'package:flutter/material.dart';

/// Exposes the token set as a typed bag off any active ThemeData.
extension CaniTokensX on ThemeData {
  CaniTokens get cani => brightness == Brightness.dark
      ? const CaniTokens.dark()
      : const CaniTokens.light();
}

/// Immutable tokens supporting both CANi Light and Slate Dark palettes.
class CaniTokens {
  const CaniTokens.light()
      : brand = const Color(0xFF5B86E5),
        brandHover = const Color(0xFF4A75D4),
        accent = const Color(0xFF36D1DC),
        accentSoft = const Color(0xFFEAF9FB),
        brandSoft = const Color(0xFFEEF4FD),
        canvas = const Color(0xFFF8FAFC),
        surface = const Color(0xFFFFFFFF),
        border = const Color(0xFFE2E8F0),
        borderStrong = const Color(0xFFCBD5E1),
        textMain = const Color(0xFF0F172A),
        textBody = const Color(0xFF334155),
        textMuted = const Color(0xFF64748B),
        success = const Color(0xFF10B981),
        successBg = const Color(0xFFECFDF5),
        warning = const Color(0xFFF59E0B),
        warningBg = const Color(0xFFFFFBEB),
        danger = const Color(0xFFEF4444),
        dangerBg = const Color(0xFFFEF2F2),
        skeleton = const Color(0xFFE2E8F0),
        brandGradient = const [Color(0xFF5B86E5), Color(0xFF36D1DC)];

  const CaniTokens.dark()
      : brand = const Color(0xFF60A5FA),
        brandHover = const Color(0xFF3B64C3),
        accent = const Color(0xFF36D1DC),
        accentSoft = const Color(0xFF164E63),
        brandSoft = const Color(0xFF1E3A8A),
        canvas = const Color(0xFF0F172A),
        surface = const Color(0xFF1E293B),
        border = const Color(0xFF334155),
        borderStrong = const Color(0xFF475569),
        textMain = const Color(0xFFF8FAFC),
        textBody = const Color(0xFFCBD5E1),
        textMuted = const Color(0xFF94A3B8),
        success = const Color(0xFF22C55E),
        successBg = const Color(0x66064E3B),
        warning = const Color(0xFFF59E0B),
        warningBg = const Color(0x4D78350F),
        danger = const Color(0xFFEF4444),
        dangerBg = const Color(0x4D7F1D1D),
        skeleton = const Color(0xFF334155),
        brandGradient = const [Color(0xFF5B86E5), Color(0xFF36D1DC)];

  final Color brand;
  final Color brandHover;
  final Color accent;
  final Color accentSoft;
  final Color brandSoft;
  final Color canvas;
  final Color surface;
  final Color border;
  final Color borderStrong;
  final Color textMain;
  final Color textBody;
  final Color textMuted;
  final Color success;
  final Color successBg;
  final Color warning;
  final Color warningBg;
  final Color danger;
  final Color dangerBg;
  final Color skeleton;
  final List<Color> brandGradient;

  /// Comfortable density: card radius + shared paddings.
  double get radius => 18.0;
  double get cardPadding => 20.0;
}
