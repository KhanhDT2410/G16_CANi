import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/features/auth/providers/auth_provider.dart';
import 'package:cani/features/auth/widgets/auth_error_banner.dart';
import 'package:cani/features/auth/widgets/auth_text_field.dart';
import 'package:cani/features/ekyc/screens/ekyc_verification_screen.dart';
import 'package:cani/shared/widgets/cani_brand_logo.dart';

/// Màn hình Bắt buộc Bổ sung Thông tin sau khi Đăng nhập Google (Worker)
/// khi tài khoản còn thiếu Họ tên hoặc Số điện thoại liên hệ.
class CompleteProfileScreen extends ConsumerStatefulWidget {
  final bool nextToEkycOnboarding;

  const CompleteProfileScreen({
    super.key,
    this.nextToEkycOnboarding = false,
  });

  @override
  ConsumerState<CompleteProfileScreen> createState() => _CompleteProfileScreenState();
}

class _CompleteProfileScreenState extends ConsumerState<CompleteProfileScreen> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _fullNameController;
  late final TextEditingController _phoneController;

  @override
  void initState() {
    super.initState();
    final user = ref.read(authStateProvider).user;
    _fullNameController = TextEditingController(text: user?.fullName ?? '');
    _phoneController = TextEditingController(text: user?.phone ?? '');
  }

  @override
  void dispose() {
    _fullNameController.dispose();
    _phoneController.dispose();
    super.dispose();
  }

  Future<void> _submitProfile() async {
    if (!(_formKey.currentState?.validate() ?? false)) return;

    final success = await ref.read(authStateProvider.notifier).updateBasicProfile(
          fullName: _fullNameController.text.trim(),
          phone: _phoneController.text.trim().replaceAll(RegExp(r'\s+'), ''),
        );

    if (!mounted) return;
    if (success) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Cập nhật thông tin thành công!'),
          backgroundColor: Color(0xFF10B981),
        ),
      );
      if (widget.nextToEkycOnboarding) {
        Navigator.of(context).pushAndRemoveUntil(
          MaterialPageRoute(
            builder: (_) => const EkycVerificationScreen(isOnboarding: true),
          ),
          (route) => false,
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final authState = ref.watch(authStateProvider);
    final user = authState.user;

    return Scaffold(
      backgroundColor: Colors.white,
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 20),
          child: Form(
            key: _formKey,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                const SizedBox(height: 8),
                const Center(child: CaniBrandLogo(height: 56)),
                const SizedBox(height: 20),
                const Text(
                  'Bổ Sung Thông Tin',
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    fontSize: 22,
                    fontWeight: FontWeight.w800,
                    color: Color(0xFF0F172A),
                  ),
                ),
                const SizedBox(height: 8),
                Text(
                  'Bạn vừa đăng nhập qua Google. Vui lòng hoàn thiện các thông tin còn thiếu trước khi vào trang chính.',
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    fontSize: 13.5,
                    color: Colors.grey.shade600,
                    height: 1.4,
                  ),
                ),
                const SizedBox(height: 20),
                if (user != null)
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                    decoration: BoxDecoration(
                      color: const Color(0xFFF0FDF4),
                      borderRadius: BorderRadius.circular(12),
                      border: Border.all(color: const Color(0xFFBBF7D0)),
                    ),
                    child: Row(
                      children: [
                        const Icon(Icons.verified_user_rounded, color: Color(0xFF16A34A), size: 22),
                        const SizedBox(width: 10),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              const Text(
                                'Email Google đã liên kết',
                                style: TextStyle(fontSize: 11.5, color: Color(0xFF15803D), fontWeight: FontWeight.w700),
                              ),
                              Text(
                                user.email,
                                style: const TextStyle(fontSize: 13.5, fontWeight: FontWeight.w600, color: Color(0xFF0F172A)),
                                overflow: TextOverflow.ellipsis,
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),
                const SizedBox(height: 18),
                if (authState.errorMessage != null) ...[
                  AuthErrorBanner(errorMessage: authState.errorMessage!),
                  const SizedBox(height: 16),
                ],
                AuthTextField(
                  controller: _fullNameController,
                  label: 'Họ và tên *',
                  hintText: 'Nguyễn Văn A',
                  prefixIcon: const Icon(Icons.person_outline, color: Color(0xFF0077FF), size: 20),
                  validator: (val) {
                    if (val == null || val.trim().isEmpty) {
                      return 'Vui lòng nhập họ và tên của bạn';
                    }
                    return null;
                  },
                ),
                const SizedBox(height: 16),
                AuthTextField(
                  controller: _phoneController,
                  label: 'Số điện thoại liên hệ *',
                  hintText: '0912345678',
                  keyboardType: TextInputType.phone,
                  prefixIcon: const Icon(Icons.phone_outlined, color: Color(0xFF0077FF), size: 20),
                  validator: (val) {
                    final cleaned = val?.trim().replaceAll(RegExp(r'\s+'), '') ?? '';
                    if (cleaned.isEmpty) {
                      return 'Vui lòng nhập số điện thoại liên hệ';
                    }
                    if (!RegExp(r'^(\+84|0)[0-9]{8,10}$').hasMatch(cleaned)) {
                      return 'Số điện thoại không đúng định dạng (VD: 0912345678)';
                    }
                    return null;
                  },
                ),
                const SizedBox(height: 26),
                Container(
                  height: 52,
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(12),
                    gradient: const LinearGradient(
                      colors: [Color(0xFF00D2FF), Color(0xFF0066FF)],
                    ),
                  ),
                  child: ElevatedButton(
                    onPressed: authState.isLoading ? null : _submitProfile,
                    style: ElevatedButton.styleFrom(
                      backgroundColor: Colors.transparent,
                      shadowColor: Colors.transparent,
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                    ),
                    child: authState.isLoading
                        ? const SizedBox(
                            height: 22,
                            width: 22,
                            child: CircularProgressIndicator(strokeWidth: 2.5, color: Colors.white),
                          )
                        : const Text(
                            'Hoàn tất & Tiếp tục',
                            style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700, color: Colors.white),
                          ),
                  ),
                ),
                const SizedBox(height: 16),
                TextButton(
                  onPressed: authState.isLoading
                      ? null
                      : () => ref.read(authStateProvider.notifier).logout(),
                  child: Text(
                    'Đăng xuất & Đổi tài khoản khác',
                    style: TextStyle(
                      color: Colors.grey.shade600,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
