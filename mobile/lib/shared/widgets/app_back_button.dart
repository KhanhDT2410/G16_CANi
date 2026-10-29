import 'package:flutter/material.dart';

/// AppBackButton - Nút điều hướng quay lại dạng hình tròn chuẩn nhận diện CANi.
/// Thiết kế chuẩn UI/UX:
/// - Hình tròn đường kính 40px
/// - Nền xanh nhạt dịu mắt (Color(0xFFEFF6FF))
/// - Biểu tượng mũi tên chevron màu xanh thương hiệu CANi (Color(0xFF0077FF))
/// - Viền mờ tinh tế (Color(0xFFDBEAFE))
class AppBackButton extends StatelessWidget {
  final VoidCallback? onTap;
  final Color? backgroundColor;
  final Color? iconColor;
  final double size;

  const AppBackButton({
    super.key,
    this.onTap,
    this.backgroundColor,
    this.iconColor,
    this.size = 40.0,
  });

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap ?? () => Navigator.of(context).maybePop(),
        borderRadius: BorderRadius.circular(size / 2),
        child: Container(
          width: size,
          height: size,
          decoration: BoxDecoration(
            color: backgroundColor ?? const Color(0xFFEFF6FF),
            shape: BoxShape.circle,
            border: Border.all(
              color: const Color(0xFFDBEAFE).withValues(alpha: 0.8),
              width: 1.0,
            ),
          ),
          child: Center(
            child: Icon(
              Icons.arrow_back_ios_new,
              size: 16,
              color: iconColor ?? const Color(0xFF0077FF),
            ),
          ),
        ),
      ),
    );
  }
}

