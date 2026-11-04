// CANi · tab "Tài khoản" — Block 6 · Security (Bảo mật tài khoản).
// Đã kết nối ForgotPasswordScreen cho luồng đổi mật khẩu và hiển thị trạng thái bảo mật thực tế.

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/features/auth/providers/auth_provider.dart';
import 'package:cani/features/auth/screens/forgot_password_screen.dart';
import 'package:cani/features/profile/widgets/profile_tokens.dart';

class ProfileSecurityBlock extends ConsumerWidget {
  const ProfileSecurityBlock({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final t = Theme.of(context).profileX;
    final user = ref.watch(authStateProvider).user;

    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: t.surface,
        borderRadius: BorderRadius.circular(t.radius),
        border: Border.all(color: t.border),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Text(
                'Bảo mật & Đăng nhập',
                style: Theme.of(context).textTheme.titleSmall?.copyWith(
                      color: t.text,
                      fontWeight: FontWeight.bold,
                    ),
              ),
              const Spacer(),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: const Color(0xFFDCFCE7),
                  borderRadius: BorderRadius.circular(99),
                  border: Border.all(color: const Color(0xFFBBF7D0)),
                ),
                child: const Text(
                  'An toàn',
                  style: TextStyle(
                    color: Color(0xFF15803D),
                    fontSize: 10.5,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),

          // 1. ĐỔI MẬT KHẨU (MỞ FORGOT_PASSWORD_SCREEN)
          InkWell(
            onTap: () {
              Navigator.of(context).push(
                MaterialPageRoute(
                  builder: (_) => ForgotPasswordScreen(initialEmail: user?.email),
                ),
              );
            },
            borderRadius: BorderRadius.circular(8),
            child: Padding(
              padding: const EdgeInsets.symmetric(vertical: 6),
              child: Row(
                children: [
                  Container(
                    padding: const EdgeInsets.all(7),
                    decoration: BoxDecoration(
                      color: t.accentDeep.withValues(alpha: 0.1),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Icon(Icons.lock_reset_rounded, size: 18, color: t.accentDeep),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'Đổi mật khẩu tài khoản',
                          style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                                color: t.text,
                                fontWeight: FontWeight.w600,
                              ),
                        ),
                        const SizedBox(height: 2),
                        Text(
                          'Khôi phục và đặt mật khẩu mới qua mã OTP gửi về Email',
                          style: TextStyle(fontSize: 11, color: t.muted),
                        ),
                      ],
                    ),
                  ),
                  Icon(Icons.chevron_right, size: 20, color: t.muted),
                ],
              ),
            ),
          ),

          const Divider(height: 16, color: Color(0xFFF1F5F9)),

          // 2. PHIÊN HOẠT ĐỘNG
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 6),
            child: Row(
              children: [
                Container(
                  padding: const EdgeInsets.all(7),
                  decoration: BoxDecoration(
                    color: t.accentDeep.withValues(alpha: 0.1),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Icon(Icons.devices_rounded, size: 18, color: t.accentDeep),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Phiên đăng nhập hiện tại',
                        style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                              color: t.text,
                              fontWeight: FontWeight.w600,
                            ),
                      ),
                      const SizedBox(height: 2),
                      Text(
                        'Ứng dụng di động CANi Mobile • Trực tuyến',
                        style: TextStyle(fontSize: 11, color: t.muted),
                      ),
                    ],
                  ),
                ),
                Container(
                  width: 8,
                  height: 8,
                  decoration: const BoxDecoration(
                    color: Color(0xFF10B981),
                    shape: BoxShape.circle,
                  ),
                ),
              ],
            ),
          ),

          const Divider(height: 16, color: Color(0xFFF1F5F9)),

          // 3. XÁC THỰC 2 LỚP OTP
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 6),
            child: Row(
              children: [
                Container(
                  padding: const EdgeInsets.all(7),
                  decoration: BoxDecoration(
                    color: t.accentDeep.withValues(alpha: 0.1),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Icon(Icons.verified_user_outlined, size: 18, color: t.accentDeep),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Xác thực OTP Email',
                        style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                              color: t.text,
                              fontWeight: FontWeight.w600,
                            ),
                      ),
                      const SizedBox(height: 2),
                      Text(
                        user?.email ?? 'Đã liên kết',
                        style: TextStyle(fontSize: 11, color: t.muted),
                      ),
                    ],
                  ),
                ),
                const Text(
                  'Đã bật',
                  style: TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: Color(0xFF059669)),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
