// CANi · tab "Tài khoản" — Block 6 · Security (Bảo mật tài khoản).
// Chỉ giữ lại các tính năng thực tế (Zero dead-code / Zero hardcode):
// 1. Đổi mật khẩu tài khoản (ForgotPasswordScreen qua Supabase OTP)
// 2. Quản lý danh sách chặn (BlockedUsersScreen qua Backend REST API)

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/features/auth/providers/auth_provider.dart';
import 'package:cani/features/auth/screens/forgot_password_screen.dart';
import 'package:cani/features/profile/screens/blocked_users_screen.dart';
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
        boxShadow: [
          BoxShadow(
            color: t.shadowColor,
            blurRadius: 8,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Header: Tiêu đề khối bảo mật
          Row(
            children: [
              Icon(Icons.security_rounded, size: 20, color: t.accentDeep),
              const SizedBox(width: 8),
              Text(
                'Bảo mật & Đăng nhập',
                style: Theme.of(context).textTheme.titleSmall?.copyWith(
                      color: t.text,
                      fontWeight: FontWeight.bold,
                    ),
              ),
            ],
          ),
          const SizedBox(height: 12),

          // 1. Đổi mật khẩu tài khoản (Kết nối trực tiếp ForgotPasswordScreen)
          InkWell(
            onTap: () {
              Navigator.of(context).push(
                MaterialPageRoute(
                  builder: (_) => ForgotPasswordScreen(initialEmail: user?.email),
                ),
              );
            },
            borderRadius: BorderRadius.circular(10),
            child: Padding(
              padding: const EdgeInsets.symmetric(vertical: 8),
              child: Row(
                children: [
                  Container(
                    padding: const EdgeInsets.all(8),
                    decoration: BoxDecoration(
                      color: t.accentDeep.withValues(alpha: 0.1),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Icon(Icons.lock_reset_rounded, size: 20, color: t.accentDeep),
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
                          style: TextStyle(fontSize: 11.5, color: t.muted),
                        ),
                      ],
                    ),
                  ),
                  Icon(Icons.chevron_right_rounded, size: 20, color: t.muted),
                ],
              ),
            ),
          ),

          Divider(height: 16, thickness: 1, color: t.border.withValues(alpha: 0.5)),

          // 2. Danh sách chặn (Kết nối trực tiếp BlockedUsersScreen / API /v1/blocks)
          InkWell(
            onTap: () {
              Navigator.of(context).push(
                MaterialPageRoute(
                  builder: (_) => const BlockedUsersScreen(),
                ),
              );
            },
            borderRadius: BorderRadius.circular(10),
            child: Padding(
              padding: const EdgeInsets.symmetric(vertical: 8),
              child: Row(
                children: [
                  Container(
                    padding: const EdgeInsets.all(8),
                    decoration: BoxDecoration(
                      color: const Color(0xFFFEE2E2),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: const Icon(Icons.block_outlined, size: 20, color: Color(0xFFDC2626)),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'Danh sách chặn',
                          style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                                color: t.text,
                                fontWeight: FontWeight.w600,
                              ),
                        ),
                        const SizedBox(height: 2),
                        Text(
                          'Quản lý danh sách Ban tổ chức và tài khoản bạn đã chặn',
                          style: TextStyle(fontSize: 11.5, color: t.muted),
                        ),
                      ],
                    ),
                  ),
                  Icon(Icons.chevron_right_rounded, size: 20, color: t.muted),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
