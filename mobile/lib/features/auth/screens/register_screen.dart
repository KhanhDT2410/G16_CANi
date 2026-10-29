import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/features/auth/providers/auth_provider.dart';
import 'package:cani/features/auth/screens/verify_email_screen.dart';
import 'package:cani/features/auth/widgets/auth_error_banner.dart';
import 'package:cani/features/auth/widgets/auth_text_field.dart';
import 'package:cani/features/auth/widgets/terms_of_service_dialog.dart';
import 'package:cani/features/ekyc/screens/ekyc_verification_screen.dart';
import 'package:cani/shared/widgets/app_back_button.dart';
import 'package:cani/shared/widgets/cani_brand_logo.dart';
import 'package:cani/shared/widgets/registration_stepper.dart';

/// RegisterScreen - Màn hình Đăng ký tài khoản Worker CANi
/// Thiết kế đồng bộ 100% Visual DNA với LoginScreen theo bản prototype OpenDesign:
/// - Hệ màu Gradient Ocean Cyan-Blue (#00D2FF -> #0066FF)
/// - Nút gửi OTP inline tích hợp trong trường Email kèm vòng đếm ngược 30s
/// - Hàng 6 ô nhập mã xác thực OTP tự động nhảy focus
/// - Nút Đăng ký nhanh với Google chuẩn nhận diện
class RegisterScreen extends ConsumerStatefulWidget {
  const RegisterScreen({super.key});

  @override
  ConsumerState<RegisterScreen> createState() => _RegisterScreenState();
}

class _RegisterScreenState extends ConsumerState<RegisterScreen> {
  final _formKey = GlobalKey<FormState>();

  final _fullNameController = TextEditingController();
  final _emailController = TextEditingController();
  final _phoneController = TextEditingController();
  final _passwordController = TextEditingController();
  final _confirmPasswordController = TextEditingController();

  bool _obscurePassword = true;
  bool _obscureConfirmPassword = true;
  bool _agreeTerms = true;

  @override
  void dispose() {
    _fullNameController.dispose();
    _emailController.dispose();
    _phoneController.dispose();
    _passwordController.dispose();
    _confirmPasswordController.dispose();
    super.dispose();
  }

  Future<void> _handleGoogleRegister() async {
    final success = await ref.read(authStateProvider.notifier).loginWithGoogle();
    if (!mounted) return;
    if (success) {
      final user = ref.read(authStateProvider).user;
      if (user != null) {
        await TermsOfServiceDialog.markAccepted(user.id);
      }
      if (!mounted) return;
      _showToast('Đăng ký qua Google thành công! Bắt đầu định danh CCCD.', isError: false);
      Navigator.of(context).pushAndRemoveUntil(
        MaterialPageRoute(
          builder: (_) => const EkycVerificationScreen(isOnboarding: true),
        ),
        (route) => false,
      );
    }
  }

  Future<void> _proceedToVerifyEmail() async {
    if (!(_formKey.currentState?.validate() ?? false)) return;

    if (!_agreeTerms) {
      _showToast('Vui lòng đồng ý với Điều khoản dịch vụ & Chính sách bảo mật', isError: true);
      return;
    }

    final email = _emailController.text.trim();
    final success = await ref.read(authStateProvider.notifier).sendOtp(email);
    if (!mounted) return;

    if (success) {
      _showToast('Mã OTP 6 số đã được gửi tới email của bạn.', isError: false);
      Navigator.of(context).push(
        MaterialPageRoute(
          builder: (_) => VerifyEmailScreen(
            email: email,
            password: _passwordController.text,
            fullName: _fullNameController.text.trim(),
            phone: _phoneController.text.trim().isEmpty ? null : _phoneController.text.trim(),
          ),
        ),
      );
    }
  }

  void _showToast(String message, {required bool isError}) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(message),
        backgroundColor: isError ? const Color(0xFFDC2626) : const Color(0xFF10B981),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final authState = ref.watch(authStateProvider);
    final screenWidth = MediaQuery.of(context).size.width;

    return Scaffold(
      backgroundColor: Colors.white,
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
          child: Form(
            key: _formKey,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                _buildTopNavigation(context),
                const SizedBox(height: 10),
                const RegistrationStepper(currentStep: 1),
                const SizedBox(height: 14),
                _buildBrandHeader(screenWidth),
                const SizedBox(height: 18),
                _buildGoogleFastTrack(authState),
                const SizedBox(height: 16),
                _buildDivider(),
                const SizedBox(height: 16),
                if (authState.errorMessage != null) ...[
                  AuthErrorBanner(errorMessage: authState.errorMessage!),
                  const SizedBox(height: 16),
                ],
                _buildFormInputs(screenWidth),
                const SizedBox(height: 14),
                _buildTermsCheckbox(),
                const SizedBox(height: 22),
                _buildGradientSubmitButton(authState),
                const SizedBox(height: 20),
                _buildFooterSwitcher(context),
                const SizedBox(height: 12),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildTopNavigation(BuildContext context) {
    return Align(
      alignment: Alignment.centerLeft,
      child: AppBackButton(
        onTap: () {
          ref.read(authStateProvider.notifier).clearError();
          Navigator.of(context).pop();
        },
      ),
    );
  }

  Widget _buildBrandHeader(double screenWidth) {
    return Column(
      children: [
        CaniBrandLogo(
          width: (screenWidth * 0.7).clamp(180.0, 260.0),
        ),
        const SizedBox(height: 6),
        const Text(
          'Đăng Ký Tài Khoản',
          textAlign: TextAlign.center,
          style: TextStyle(
            fontSize: 21,
            fontWeight: FontWeight.w800,
            color: Color(0xFF0B1B3D),
            letterSpacing: -0.3,
          ),
        ),
        const SizedBox(height: 4),
        const Text(
          'Nhận ca làm sự kiện linh hoạt, thu nhập hấp dẫn',
          textAlign: TextAlign.center,
          style: TextStyle(
            fontSize: 13,
            fontWeight: FontWeight.w500,
            color: Color(0xFF64748B),
          ),
        ),
      ],
    );
  }

  Widget _buildGoogleFastTrack(AuthState authState) {
    return OutlinedButton.icon(
      onPressed: authState.isLoading ? null : _handleGoogleRegister,
      icon: Image.network(
        'https://www.gstatic.com/images/branding/product/2x/googleg_48dp.png',
        height: 20,
        width: 20,
        errorBuilder: (_, _, _) => const Icon(
          Icons.g_mobiledata,
          size: 26,
          color: Colors.blue,
        ),
      ),
      label: Text(
        authState.isLoading ? 'Đang kết nối Google...' : 'Đăng ký nhanh với Google',
        style: const TextStyle(
          fontWeight: FontWeight.w600,
          fontSize: 14.5,
          color: Color(0xFF1E293B),
        ),
      ),
      style: OutlinedButton.styleFrom(
        padding: const EdgeInsets.symmetric(vertical: 13),
        side: BorderSide(color: Colors.grey.shade300),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
        backgroundColor: Colors.white,
      ),
    );
  }

  Widget _buildDivider() {
    return Row(
      children: [
        Expanded(child: Divider(color: Colors.grey.shade300)),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 14),
          child: Text(
            'Hoặc điền thông tin bên dưới',
            style: TextStyle(fontSize: 12, color: Colors.grey.shade500),
          ),
        ),
        Expanded(child: Divider(color: Colors.grey.shade300)),
      ],
    );
  }

  Widget _buildFormInputs(double screenWidth) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        // Họ và tên
        AuthTextField(
          controller: _fullNameController,
          label: 'Họ và tên',
          hintText: 'Nguyễn Văn A',
          prefixIcon: const Icon(Icons.person_outline, color: Color(0xFF0077FF), size: 20),
          validator: (val) =>
              val == null || val.trim().isEmpty ? 'Vui lòng nhập họ và tên' : null,
        ),
        const SizedBox(height: 14),

        // Email
        AuthTextField(
          controller: _emailController,
          label: 'Địa chỉ email',
          hintText: 'worker@cani.vn',
          keyboardType: TextInputType.emailAddress,
          prefixIcon: const Icon(Icons.email_outlined, color: Color(0xFF0077FF), size: 20),
          validator: (val) {
            if (val == null || val.trim().isEmpty) {
              return 'Vui lòng nhập địa chỉ email';
            }
            final emailRegex = RegExp(r'^[^@]+@[^@]+\.[^@]+$');
            if (!emailRegex.hasMatch(val.trim())) {
              return 'Định dạng email không hợp lệ';
            }
            return null;
          },
        ),
        const SizedBox(height: 14),

        // Số điện thoại
        AuthTextField(
          controller: _phoneController,
          label: 'Số điện thoại liên hệ',
          hintText: '0912 345 678',
          keyboardType: TextInputType.phone,
          prefixIcon: const Icon(Icons.phone_outlined, color: Color(0xFF0077FF), size: 20),
        ),
        const SizedBox(height: 14),

        // Mật khẩu
        AuthTextField(
          controller: _passwordController,
          label: 'Đặt mật khẩu',
          hintText: 'Tối thiểu 8 ký tự, có 1 chữ in hoa',
          obscureText: _obscurePassword,
          prefixIcon: const Icon(Icons.lock_outline, color: Color(0xFF0077FF), size: 20),
          suffixIcon: IconButton(
            icon: Icon(
              _obscurePassword ? Icons.visibility_outlined : Icons.visibility_off_outlined,
              color: Colors.grey.shade600,
              size: 20,
            ),
            onPressed: () => setState(() => _obscurePassword = !_obscurePassword),
          ),
          validator: (val) {
            if (val == null || val.isEmpty) return 'Vui lòng nhập mật khẩu';
            if (val.length < 8) return 'Mật khẩu phải có tối thiểu 8 ký tự';
            if (!RegExp(r'[A-Z]').hasMatch(val)) {
              return 'Mật khẩu phải chứa ít nhất 1 chữ cái in hoa (A-Z)';
            }
            return null;
          },
        ),
        const SizedBox(height: 14),

        // Xác nhận mật khẩu
        AuthTextField(
          controller: _confirmPasswordController,
          label: 'Xác nhận mật khẩu',
          hintText: '••••••••',
          obscureText: _obscureConfirmPassword,
          prefixIcon: const Icon(Icons.lock_outline, color: Color(0xFF0077FF), size: 20),
          suffixIcon: IconButton(
            icon: Icon(
              _obscureConfirmPassword
                  ? Icons.visibility_outlined
                  : Icons.visibility_off_outlined,
              color: Colors.grey.shade600,
              size: 20,
            ),
            onPressed: () =>
                setState(() => _obscureConfirmPassword = !_obscureConfirmPassword),
          ),
          validator: (val) {
            if (val == null || val.isEmpty) return 'Vui lòng xác nhận mật khẩu';
            if (val != _passwordController.text) return 'Mật khẩu xác nhận không khớp';
            return null;
          },
        ),
      ],
    );
  }

  Widget _buildTermsCheckbox() {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        SizedBox(
          width: 24,
          height: 24,
          child: Checkbox(
            value: _agreeTerms,
            activeColor: const Color(0xFF0077FF),
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(4)),
            onChanged: (val) => setState(() => _agreeTerms = val ?? false),
          ),
        ),
        const SizedBox(width: 8),
        Expanded(
          child: GestureDetector(
            onTap: () => setState(() => _agreeTerms = !_agreeTerms),
            child: Text.rich(
              TextSpan(
                text: 'Tôi đồng ý với ',
                style: const TextStyle(fontSize: 12.5, color: Color(0xFF1E293B)),
                children: const [
                  TextSpan(
                    text: 'Điều khoản dịch vụ',
                    style: TextStyle(
                      color: Color(0xFF0077FF),
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  TextSpan(text: ' & '),
                  TextSpan(
                    text: 'Chính sách bảo mật',
                    style: TextStyle(
                      color: Color(0xFF0077FF),
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  TextSpan(text: ' của CANi'),
                ],
              ),
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildGradientSubmitButton(AuthState authState) {
    return Container(
      height: 52,
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(12),
        gradient: const LinearGradient(
          colors: [Color(0xFF00D2FF), Color(0xFF0066FF)],
          begin: Alignment.centerLeft,
          end: Alignment.centerRight,
        ),
        boxShadow: [
          BoxShadow(
            color: const Color(0xFF0077FF).withValues(alpha: 0.35),
            blurRadius: 14,
            offset: const Offset(0, 5),
          ),
        ],
      ),
      child: ElevatedButton(
        onPressed: authState.isLoading ? null : _proceedToVerifyEmail,
        style: ElevatedButton.styleFrom(
          backgroundColor: Colors.transparent,
          shadowColor: Colors.transparent,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
          ),
        ),
        child: authState.isLoading
            ? const SizedBox(
                height: 22,
                width: 22,
                child: CircularProgressIndicator(
                  strokeWidth: 2.5,
                  color: Colors.white,
                ),
              )
            : const Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Text(
                    'Tiếp tục (Nhận mã OTP)',
                    style: TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.w700,
                      color: Colors.white,
                    ),
                  ),
                  SizedBox(width: 8),
                  Icon(Icons.arrow_forward_rounded, color: Colors.white, size: 20),
                ],
              ),
      ),
    );
  }

  Widget _buildFooterSwitcher(BuildContext context) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        const Text(
          'Bạn đã có tài khoản? ',
          style: TextStyle(fontSize: 13.5, color: Color(0xFF1E293B)),
        ),
        GestureDetector(
          onTap: () {
            ref.read(authStateProvider.notifier).clearError();
            Navigator.of(context).pop();
          },
          child: const Text(
            'Đăng nhập ngay',
            style: TextStyle(
              fontSize: 13.5,
              fontWeight: FontWeight.w700,
              color: Color(0xFF0077FF),
            ),
          ),
        ),
      ],
    );
  }
}
