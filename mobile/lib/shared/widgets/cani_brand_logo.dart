import 'package:flutter/material.dart';

/// CANi Official Brand Logo Widget cho Flutter Mobile.
/// Sử dụng tệp tài nguyên hình ảnh: assets/cani_logo.png
/// Hỗ trợ co giãn tự động theo 80% chiều rộng màn hình (responsive).
class CaniBrandLogo extends StatelessWidget {
  final double? height;
  final double? width;
  final bool isWorker;
  final bool showSlogan;

  const CaniBrandLogo({
    super.key,
    this.height,
    this.width,
    this.isWorker = true,
    this.showSlogan = false,
  });

  @override
  Widget build(BuildContext context) {
    final screenWidth = MediaQuery.of(context).size.width;
    // Mặc định tự động co giãn bằng 80% chiều ngang màn hình
    // Sử dụng clamp (200 -> 340dp) để giao diện chuẩn đẹp trên cả Mobile lẫn Windows Desktop
    final effectiveWidth = width ?? (height == null ? (screenWidth * 0.8).clamp(200.0, 340.0) : null);

    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        Image.asset(
          'assets/cani_logo.png',
          width: effectiveWidth,
          height: height,
          fit: BoxFit.contain,
          errorBuilder: (context, error, stackTrace) {
            return Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  'CANi',
                  style: TextStyle(
                    fontSize: (height ?? 64) * 0.45,
                    fontWeight: FontWeight.w900,
                    color: const Color(0xFF0077FF),
                    letterSpacing: 1.5,
                  ),
                ),
                Text(
                  'EVENT PLATFORM',
                  style: TextStyle(
                    fontSize: (height ?? 64) * 0.16,
                    fontWeight: FontWeight.w800,
                    letterSpacing: 2.0,
                    color: const Color(0xFF0B1B3D),
                  ),
                ),
              ],
            );
          },
        ),
      ],
    );
  }
}
