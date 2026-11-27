import 'package:flutter/material.dart';

import 'package:cani/app/cani_tokens.dart';

/// Phân loại sắc thái thông báo cuối màn hình của CANi.
enum CaniNoticeTone {
  success,
  error,
  warning,
  info,
}

/// Thông báo nổi chung nền trung tính theo theme sáng/tối, bo góc vừa phải.
/// Phân biệt trạng thái bằng biểu tượng và chữ rõ ràng, không che nút chính.
class CaniSnackBar {
  CaniSnackBar._();

  static void show(
    BuildContext context, {
    required String message,
    CaniNoticeTone tone = CaniNoticeTone.info,
    String? actionLabel,
    VoidCallback? onAction,
    Duration duration = const Duration(seconds: 3),
    double bottomOffset = 12.0,
  }) {
    final messenger = ScaffoldMessenger.maybeOf(context);
    if (messenger == null) return;

    messenger
      ..removeCurrentSnackBar()
      ..showSnackBar(
        build(
          context,
          message: message,
          tone: tone,
          actionLabel: actionLabel,
          onAction: onAction,
          duration: duration,
          bottomOffset: bottomOffset,
        ),
      );
  }

  static SnackBar build(
    BuildContext context, {
    required String message,
    CaniNoticeTone tone = CaniNoticeTone.info,
    String? actionLabel,
    VoidCallback? onAction,
    Duration duration = const Duration(seconds: 3),
    double bottomOffset = 12.0,
  }) {
    final theme = Theme.of(context);
    final cani = theme.cani;
    final isDark = theme.brightness == Brightness.dark;
    final meta = _resolveMeta(tone, cani);

    return SnackBar(
      behavior: SnackBarBehavior.floating,
      backgroundColor: Colors.transparent,
      elevation: 0,
      padding: EdgeInsets.zero,
      margin: EdgeInsets.fromLTRB(12, 0, 12, bottomOffset),
      duration: duration,
      content: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
        decoration: BoxDecoration(
          color: isDark ? const Color(0xFF1E293B) : Colors.white,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(
            color: isDark ? cani.borderStrong : const Color(0xFFCBD5E1),
          ),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: isDark ? 0.32 : 0.08),
              blurRadius: 12,
              offset: const Offset(0, 4),
            ),
          ],
        ),
        child: Row(
          children: [
            Container(
              width: 28,
              height: 28,
              decoration: BoxDecoration(
                color: meta.color.withValues(alpha: isDark ? 0.2 : 0.12),
                borderRadius: BorderRadius.circular(8),
              ),
              child: Icon(meta.icon, size: 17, color: meta.color),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: Text(
                message,
                maxLines: 3,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(
                  fontSize: 12.8,
                  fontWeight: FontWeight.w600,
                  color: cani.textMain,
                  height: 1.35,
                ),
              ),
            ),
            if (actionLabel != null && onAction != null) ...[
              const SizedBox(width: 8),
              TextButton(
                onPressed: () {
                  ScaffoldMessenger.maybeOf(context)?.hideCurrentSnackBar();
                  onAction();
                },
                style: TextButton.styleFrom(
                  foregroundColor: cani.brand,
                  backgroundColor: cani.brandSoft,
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                  minimumSize: Size.zero,
                  tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(8),
                  ),
                ),
                child: Text(
                  actionLabel,
                  maxLines: 1,
                  style: TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.w800,
                    color: cani.brand,
                  ),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }

  static ({IconData icon, Color color}) _resolveMeta(
    CaniNoticeTone tone,
    CaniTokens cani,
  ) {
    return switch (tone) {
      CaniNoticeTone.success => (
          icon: Icons.check_circle_outline_rounded,
          color: cani.success,
        ),
      CaniNoticeTone.error => (
          icon: Icons.error_outline_rounded,
          color: cani.danger,
        ),
      CaniNoticeTone.warning => (
          icon: Icons.warning_amber_rounded,
          color: cani.warning,
        ),
      CaniNoticeTone.info => (
          icon: Icons.info_outline_rounded,
          color: cani.brand,
        ),
    };
  }
}
