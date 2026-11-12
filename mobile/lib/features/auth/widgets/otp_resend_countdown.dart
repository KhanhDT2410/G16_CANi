import 'package:flutter/material.dart';

/// OtpResendCountdown - Bộ đếm ngược 60s và nút gửi lại OTP
class OtpResendCountdown extends StatelessWidget {
  final int countdown;
  final bool isSendingOtp;
  final VoidCallback onResend;

  const OtpResendCountdown({
    super.key,
    required this.countdown,
    required this.isSendingOtp,
    required this.onResend,
  });

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        const Text(
          'Không nhận được mã? ',
          style: TextStyle(fontSize: 13, color: Color(0xFF64748B)),
        ),
        if (countdown > 0)
          Text(
            'Gửi lại sau ${countdown}s',
            style: const TextStyle(
              fontSize: 13,
              fontWeight: FontWeight.w700,
              color: Color(0xFF059669),
            ),
          )
        else
          InkWell(
            onTap: isSendingOtp ? null : onResend,
            borderRadius: BorderRadius.circular(6),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
              child: isSendingOtp
                  ? const SizedBox(
                      width: 14,
                      height: 14,
                      child: CircularProgressIndicator(
                        strokeWidth: 2,
                        color: Color(0xFF0077FF),
                      ),
                    )
                  : const Text(
                      'Gửi lại ngay',
                      style: TextStyle(
                        fontSize: 13,
                        fontWeight: FontWeight.w700,
                        color: Color(0xFF0077FF),
                      ),
                    ),
            ),
          ),
      ],
    );
  }
}

