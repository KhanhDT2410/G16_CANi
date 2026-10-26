import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/features/auth/providers/auth_provider.dart';
import 'package:cani/features/auth/widgets/auth_text_field.dart';
import 'package:cani/shared/widgets/cani_brand_logo.dart';

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

  // 6 ô nhập mã OTP tách rời (Auto-advance)
  final List<TextEditingController> _otpControllers =
      List.generate(6, (_) => TextEditingController());
  final List<FocusNode> _otpFocusNodes = List.generate(6, (_) => FocusNode());

  bool _obscurePassword = true;
  bool _obscureConfirmPassword = true;
  bool _agreeTerms = true;

  bool _isSendingOtp = false;
  bool _otpSent = false;
  int _countdown = 0;
  Timer? _timer;

  @override
  void dispose() {
    _timer?.cancel();
    _fullNameController.dispose();
    _emailController.dispose();
    _phoneController.dispose();
    _passwordController.dispose();
    _confirmPasswordController.dispose();
    for (final c in _otpControllers) {
      c.dispose();
    }
    for (final f in _otpFocusNodes) {
      f.dispose();
    }
    super.dispose();
  }

  String get _combinedOtpCode {
    return _otpControllers.map((c) => c.text.trim()).join();
  }

  void _startCountdown() {
    setState(() {
      _countdown = 30;
      _otpSent = true;
    });
    _timer?.cancel();
    _timer = Timer.periodic(const Duration(seconds: 1), (timer) {
      if (!mounted) return;
      if (_countdown > 0) {
        setState(() => _countdown--);
      } else {
        timer.cancel();
      }
    });
  }

  Future<void> _handleSendOtp() async {
    final email = _emailController.text.trim();
    if (email.isEmpty) {
      _showToast('Vui lòng nhập địa chỉ email trước khi nhận mã OTP', isError: true);
      return;
    }
    final emailRegex = RegExp(r'^[^@]+@[^@]+\.[^@]+$');
    if (!emailRegex.hasMatch(email)) {
      _showToast('Định dạng email không hợp lệ', isError: true);
      return;
    }

    setState(() => _isSendingOtp = true);
    final success = await ref.read(authStateProvider.notifier).sendOtp(email);
    if (!mounted) return;
    setState(() => _isSendingOtp = false);

    if (success) {
      _startCountdown();
      _showToast('Mã OTP 6 số đã được gửi tới email của bạn!', isError: false);
      _otpFocusNodes[0].requestFocus();
    }
  }

  Future<void> _handleGoogleRegister() async {
    final success = await ref.read(authStateProvider.notifier).loginWithGoogle();
    if (!mounted) return;
    if (success) {
      _showToast('Đăng ký qua Google thành công!', isError: false);
      Navigator.of(context).pop();
    }
  }

  Future<void> _submitRegister() async {
    if (!(_formKey.currentState?.validate() ?? false)) return;

    if (!_agreeTerms) {
      _showToast('Vui lòng đồng ý với Điều khoản dịch vụ & Chính sách bảo mật', isError: true);
      return;
    }

    final otp = _combinedOtpCode;
    if (otp.length != 6) {
      _showToast('Vui lòng nhập đầy đủ 6 chữ số mã xác thực OTP', isError: true);
      return;
    }

    final success = await ref.read(authStateProvider.notifier).register(
          email: _emailController.text.trim(),
          password: _passwordController.text,
          fullName: _fullNameController.text.trim(),
          otpCode: otp,
          phone: _phoneController.text.trim().isEmpty ? null : _phoneController.text.trim(),
        );

    if (!mounted) return;
    if (success) {
      _showToast('Tạo tài khoản thành công! Chào mừng bạn đến với CANi.', isError: false);
      Navigator.of(context).pop();
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
                const SizedBox(height: 8),
                _buildBrandHeader(screenWidth),
                const SizedBox(height: 18),
                _buildGoogleFastTrack(authState),
                const SizedBox(height: 16),
                _buildDivider(),
                const SizedBox(height: 16),
                if (authState.errorMessage != null) ...[
                  _buildErrorAlert(authState.errorMessage!),
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
      child: InkWell(
        onTap: () {
          ref.read(authStateProvider.notifier).clearError();
          Navigator.of(context).pop();
        },
        borderRadius: BorderRadius.circular(20),
        child: Container(
          width: 40,
          height: 40,
          decoration: BoxDecoration(
            color: const Color(0xFFF0F7FF),
            shape: BoxShape.circle,
            border: Border.all(color: Colors.white),
            boxShadow: [
              BoxShadow(
                color: const Color(0xFF0077FF).withValues(alpha: 0.08),
                blurRadius: 10,
                offset: const Offset(0, 4),
              ),
            ],
          ),
          child: const Icon(
            Icons.arrow_back_ios_new,
            size: 16,
            color: Color(0xFF1E293B),
          ),
        ),
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

  Widget _buildErrorAlert(String errorMessage) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: const Color(0xFFFEF2F2),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: const Color(0xFFFECACA)),
      ),
      child: Row(
        children: [
          const Icon(Icons.error_outline, color: Color(0xFFDC2626), size: 18),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              errorMessage,
              style: const TextStyle(color: Color(0xFFDC2626), fontSize: 13),
            ),
          ),
        ],
      ),
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

        // Email + Nút gửi OTP inline (Suffix Button)
        AuthTextField(
          controller: _emailController,
          label: 'Địa chỉ email · xác minh OTP',
          hintText: 'worker@cani.vn',
          keyboardType: TextInputType.emailAddress,
          prefixIcon: const Icon(Icons.email_outlined, color: Color(0xFF0077FF), size: 20),
          suffixIcon: _buildOtpPillButton(),
          validator: (val) =>
              val == null || val.trim().isEmpty ? 'Vui lòng nhập địa chỉ email' : null,
        ),
        const SizedBox(height: 14),

        // 6 ô nhập mã OTP tách rời
        _buildOtpBoxesSection(screenWidth),
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

  Widget _buildOtpPillButton() {
    return Padding(
      padding: const EdgeInsets.only(right: 6, top: 5, bottom: 5),
      child: InkWell(
        onTap: (_isSendingOtp || _countdown > 0) ? null : _handleSendOtp,
        borderRadius: BorderRadius.circular(10),
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
          decoration: BoxDecoration(
            color: _countdown > 0
                ? const Color(0xFF10B981).withValues(alpha: 0.12)
                : const Color(0xFF0077FF).withValues(alpha: 0.10),
            borderRadius: BorderRadius.circular(10),
            border: Border.all(
              color: _countdown > 0
                  ? const Color(0xFF10B981).withValues(alpha: 0.3)
                  : const Color(0xFF0077FF).withValues(alpha: 0.2),
            ),
          ),
          child: _isSendingOtp
              ? const SizedBox(
                  width: 14,
                  height: 14,
                  child: CircularProgressIndicator(
                    strokeWidth: 2,
                    color: Color(0xFF0077FF),
                  ),
                )
              : Text(
                  _countdown > 0
                      ? 'Gửi lại (${_countdown}s)'
                      : (_otpSent ? 'Gửi lại mã' : 'Gửi mã OTP'),
                  style: TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.bold,
                    color: _countdown > 0
                        ? const Color(0xFF059669)
                        : const Color(0xFF0077FF),
                  ),
                ),
        ),
      ),
    );
  }

  Widget _buildOtpBoxesSection(double screenWidth) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            const Text(
              'Mã xác thực OTP (6 chữ số)',
              style: TextStyle(
                fontSize: 13,
                fontWeight: FontWeight.w600,
                color: Color(0xFF1E293B),
              ),
            ),
            if (_otpSent)
              const Text(
                'Đã gửi tới email ✓',
                style: TextStyle(
                  fontSize: 11.5,
                  fontWeight: FontWeight.bold,
                  color: Color(0xFF10B981),
                ),
              ),
          ],
        ),
        const SizedBox(height: 8),
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: List.generate(6, (index) {
            return SizedBox(
              width: (screenWidth > 380) ? 46 : 42,
              height: 52,
              child: TextFormField(
                controller: _otpControllers[index],
                focusNode: _otpFocusNodes[index],
                keyboardType: TextInputType.number,
                textAlign: TextAlign.center,
                maxLength: 1,
                style: const TextStyle(
                  fontSize: 20,
                  fontWeight: FontWeight.bold,
                  color: Color(0xFF0B1B3D),
                ),
                decoration: InputDecoration(
                  counterText: '',
                  contentPadding: EdgeInsets.zero,
                  filled: true,
                  fillColor: const Color(0xFFF8FAFC),
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(12),
                    borderSide: const BorderSide(color: Color(0xFFE2E8F0), width: 1.5),
                  ),
                  enabledBorder: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(12),
                    borderSide: const BorderSide(color: Color(0xFFE2E8F0), width: 1.5),
                  ),
                  focusedBorder: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(12),
                    borderSide: const BorderSide(color: Color(0xFF0077FF), width: 2),
                  ),
                ),
                onChanged: (val) {
                  if (val.isNotEmpty && index < 5) {
                    _otpFocusNodes[index + 1].requestFocus();
                  } else if (val.isEmpty && index > 0) {
                    _otpFocusNodes[index - 1].requestFocus();
                  }
                },
              ),
            );
          }),
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
        onPressed: authState.isLoading ? null : _submitRegister,
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
                    'Tạo Tài Khoản',
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
