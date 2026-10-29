import 'package:flutter/material.dart';

/// RegistrationStepper - Thanh chỉ báo tiến trình 3 bước đăng ký Worker CANi.
/// Luồng:
///   Bước 1: Thông tin (Cơ bản)
///   Bước 2: Xác thực (OTP Email)
///   Bước 3: Định danh (eKYC CCCD)
///
/// Trạng thái:
/// - Đã hoàn thành: Vòng tròn xanh lá (#10B981) + Dấu tích trắng + Chữ xanh lá
/// - Đang thực hiện: Vòng tròn xanh dương CANi (#0077FF) + Số trắng + Chữ xanh dương
/// - Chưa thực hiện: Vòng tròn xám nhạt (#E2E8F0) + Số xám (#64748B) + Chữ xám
class RegistrationStepper extends StatelessWidget {
  final int currentStep; // 1, 2, hoặc 3

  const RegistrationStepper({
    super.key,
    required this.currentStep,
  });

  static const List<String> _stepTitles = [
    'Thông tin',
    'Xác thực',
    'Định danh',
  ];

  @override
  Widget build(BuildContext context) {
    return Center(
      child: FittedBox(
        fit: BoxFit.scaleDown,
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 8.0, horizontal: 4.0),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              for (int i = 0; i < _stepTitles.length; i++) ...[
                _buildStepItem(stepNumber: i + 1, title: _stepTitles[i]),
                if (i < _stepTitles.length - 1) _buildSeparator(),
              ],
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildStepItem({required int stepNumber, required String title}) {
    final bool isCompleted = stepNumber < currentStep;
    final bool isActive = stepNumber == currentStep;

    final Color primaryColor = isCompleted
        ? const Color(0xFF10B981)
        : (isActive ? const Color(0xFF0077FF) : const Color(0xFF64748B));

    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        // Vòng tròn số thứ tự / dấu tích hoàn thành
        Container(
          width: 28,
          height: 28,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            color: isCompleted
                ? const Color(0xFF10B981)
                : (isActive ? const Color(0xFF0077FF) : const Color(0xFFE2E8F0)),
            boxShadow: isActive
                ? [
                    BoxShadow(
                      color: const Color(0xFF0077FF).withValues(alpha: 0.28),
                      blurRadius: 8,
                      offset: const Offset(0, 3),
                    ),
                  ]
                : null,
          ),
          child: Center(
            child: isCompleted
                ? const Icon(
                    Icons.check,
                    size: 16,
                    color: Colors.white,
                  )
                : Text(
                    '$stepNumber',
                    style: TextStyle(
                      fontSize: 13,
                      fontWeight: FontWeight.w700,
                      color: isActive ? Colors.white : const Color(0xFF64748B),
                    ),
                  ),
          ),
        ),
        const SizedBox(width: 7),
        // Nhãn tiêu đề bước
        Text(
          title,
          style: TextStyle(
            fontSize: 14,
            fontWeight: (isActive || isCompleted) ? FontWeight.w700 : FontWeight.w500,
            color: primaryColor,
            letterSpacing: -0.2,
          ),
        ),
      ],
    );
  }

  Widget _buildSeparator() {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 10),
      child: Container(
        width: 10,
        height: 2.5,
        decoration: BoxDecoration(
          color: const Color(0xFFCBD5E1),
          borderRadius: BorderRadius.circular(1.5),
        ),
      ),
    );
  }
}

