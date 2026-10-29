import 'package:flutter/material.dart';

/// PasswordStrengthChecklist - Hiển thị 4 tiêu chí đánh giá độ mạnh của mật khẩu
class PasswordStrengthChecklist extends StatelessWidget {
  final String password;

  const PasswordStrengthChecklist({
    super.key,
    required this.password,
  });

  bool get hasMinLength => password.length >= 8;
  bool get hasUppercase => RegExp(r'[A-Z]').hasMatch(password);
  bool get hasDigit => RegExp(r'[0-9]').hasMatch(password);
  bool get hasSpecialChar =>
      RegExp(r'[!@#$%^&*(),.?":{}|<>]').hasMatch(password);

  bool get isStrongEnough =>
      hasMinLength && hasUppercase && hasDigit && hasSpecialChar;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: const Color(0xFFF8FAFC),
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: const Color(0xFFE2E8F0)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            'Yêu cầu độ mạnh mật khẩu:',
            style: TextStyle(
              fontSize: 12,
              fontWeight: FontWeight.w700,
              color: Color(0xFF475569),
            ),
          ),
          const SizedBox(height: 8),
          _buildCriterion('Tối thiểu 8 ký tự', hasMinLength),
          const SizedBox(height: 4),
          _buildCriterion('Có ít nhất 1 chữ in hoa (A-Z)', hasUppercase),
          const SizedBox(height: 4),
          _buildCriterion('Có ít nhất 1 chữ số (0-9)', hasDigit),
          const SizedBox(height: 4),
          _buildCriterion('Có ký tự đặc biệt (!@#\$%^&...)', hasSpecialChar),
        ],
      ),
    );
  }

  Widget _buildCriterion(String label, bool isSatisfied) {
    return Row(
      children: [
        Icon(
          isSatisfied ? Icons.check_circle_rounded : Icons.radio_button_unchecked,
          size: 15,
          color: isSatisfied ? const Color(0xFF10B981) : const Color(0xFF94A3B8),
        ),
        const SizedBox(width: 8),
        Text(
          label,
          style: TextStyle(
            fontSize: 12,
            fontWeight: isSatisfied ? FontWeight.w600 : FontWeight.normal,
            color: isSatisfied ? const Color(0xFF0F172A) : const Color(0xFF64748B),
          ),
        ),
      ],
    );
  }
}

