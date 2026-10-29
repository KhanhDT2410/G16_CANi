import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/features/auth/data/auth_repository.dart';
import 'package:cani/features/auth/widgets/auth_error_banner.dart';
import 'package:cani/features/auth/widgets/auth_text_field.dart';
import 'package:cani/features/auth/widgets/otp_pin_input_row.dart';
import 'package:cani/features/auth/widgets/otp_resend_countdown.dart';
import 'package:cani/features/auth/widgets/password_strength_checklist.dart';
import 'package:cani/shared/widgets/app_back_button.dart';

/// ForgotPasswordScreen - Luồng khôi phục mật khẩu 2 bước (Spec P-01 / A-03)
class ForgotPasswordScreen extends ConsumerStatefulWidget {
  final String? initialEmail;

  const ForgotPasswordScreen({
    super.key,
    this.initialEmail,
  });

  @override
  ConsumerState<ForgotPasswordScreen> createState() =>
      _ForgotPasswordScreenState();
}

class _ForgotPasswordScreenState extends ConsumerState<ForgotPasswordScreen> {
  final PageController _pageController = PageController();
  int _currentPage = 0;

  // Step 1 Controllers
  final TextEditingController _emailCtrl = TextEditingController();
  final GlobalKey<FormState> _emailFormKey = GlobalKey<FormState>();

  // Step 2 Controllers
  final List<TextEditingController> _otpCtrls =
      List.generate(6, (_) => TextEditingController());
  final List<FocusNode> _otpNodes = List.generate(6, (_) => FocusNode());
  final TextEditingController _passwordCtrl = TextEditingController();
  final TextEditingController _confirmPasswordCtrl = TextEditingController();
  final GlobalKey<FormState> _resetFormKey = GlobalKey<FormState>();

  bool _obscurePassword = true;
  bool _obscureConfirmPassword = true;
  bool _isLoading = false;
  bool _isSendingOtp = false;
  String? _errorMessage;

  // Countdown timer
  Timer? _countdownTimer;
  int _countdown = 0;

  @override
  void initState() {
    super.initState();
    if (widget.initialEmail != null && widget.initialEmail!.isNotEmpty) {
      _emailCtrl.text = widget.initialEmail!;
    }
  }

  @override
  void dispose() {
    _countdownTimer?.cancel();
    _pageController.dispose();
    _emailCtrl.dispose();
    for (final c in _otpCtrls) {
      c.dispose();
    }
    for (final n in _otpNodes) {
      n.dispose();
    }
    _passwordCtrl.dispose();
    _confirmPasswordCtrl.dispose();
    super.dispose();
  }

  void _startCountdown() {
    _countdownTimer?.cancel();
    setState(() => _countdown = 60);
    _countdownTimer = Timer.periodic(const Duration(seconds: 1), (timer) {
      if (_countdown > 1) {
        setState(() => _countdown--);
      } else {
        timer.cancel();
        setState(() => _countdown = 0);
      }
    });
  }

  String get _enteredOtp =>
      _otpCtrls.map((c) => c.text.trim()).join();

  Future<void> _handleSendOtp() async {
    if (!(_emailFormKey.currentState?.validate() ?? false)) return;
    FocusScope.of(context).unfocus();

    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      final email = _emailCtrl.text.trim();
      await ref.read(authRepositoryProvider).sendResetPasswordOtp(email);

      if (!mounted) return;
      setState(() => _isLoading = false);

      _startCountdown();
      _pageController.animateToPage(
        1,
        duration: const Duration(milliseconds: 350),
        curve: Curves.easeInOut,
      );
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _isLoading = false;
        _errorMessage = e.toString().replaceFirst('Exception: ', '');
      });
    }
  }

  Future<void> _handleResendOtp() async {
    if (_countdown > 0 || _isSendingOtp) return;

    setState(() {
      _isSendingOtp = true;
      _errorMessage = null;
    });

    try {
      final email = _emailCtrl.text.trim();
      await ref.read(authRepositoryProvider).sendResetPasswordOtp(email);

      if (!mounted) return;
      _startCountdown();
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Mã OTP mới đã được gửi đến email của bạn!'),
          backgroundColor: Color(0xFF10B981),
        ),
      );
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _errorMessage = e.toString().replaceFirst('Exception: ', '');
      });
    } finally {
      if (mounted) setState(() => _isSendingOtp = false);
    }
  }

  Future<void> _handleConfirmReset() async {
    if (_enteredOtp.length < 6) {
      setState(() => _errorMessage = 'Vui lòng nhập đủ 6 chữ số mã OTP.');
      return;
    }
    if (!(_resetFormKey.currentState?.validate() ?? false)) return;

    final password = _passwordCtrl.text;
    final checklist = PasswordStrengthChecklist(password: password);
    if (!checklist.isStrongEnough) {
      setState(() => _errorMessage =
          'Mật khẩu chưa đáp ứng đủ 4 tiêu chí độ mạnh yêu cầu.');
      return;
    }
    if (password != _confirmPasswordCtrl.text) {
      setState(() => _errorMessage = 'Mật khẩu xác nhận không trùng khớp.');
      return;
    }

    FocusScope.of(context).unfocus();
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      await ref.read(authRepositoryProvider).confirmResetPassword(
            email: _emailCtrl.text.trim(),
            otp: _enteredOtp,
            newPassword: password,
          );

      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Đặt lại mật khẩu thành công! Vui lòng đăng nhập.'),
          backgroundColor: Color(0xFF10B981),
        ),
      );

      Navigator.of(context).pop(_emailCtrl.text.trim());
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _isLoading = false;
        _errorMessage = e.toString().replaceFirst('Exception: ', '');
      });
    }
  }

  void _handleBack() {
    if (_currentPage > 0) {
      _pageController.previousPage(
        duration: const Duration(milliseconds: 300),
        curve: Curves.easeInOut,
      );
    } else {
      Navigator.of(context).pop();
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.white,
      appBar: AppBar(
        backgroundColor: Colors.white,
        elevation: 0,
        leading: Padding(
          padding: const EdgeInsets.only(left: 16.0),
          child: Center(
            child: AppBackButton(onTap: _handleBack),
          ),
        ),
      ),
      body: SafeArea(
        child: PageView(
          controller: _pageController,
          physics: const NeverScrollableScrollPhysics(),
          onPageChanged: (page) => setState(() => _currentPage = page),
          children: [
            _buildStep1Email(context),
            _buildStep2ResetPassword(context),
          ],
        ),
      ),
    );
  }

  Widget _buildStep1Email(BuildContext context) {
    return SingleChildScrollView(
      padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
      child: Form(
        key: _emailFormKey,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Center(
              child: Container(
                width: 80,
                height: 80,
                decoration: BoxDecoration(
                  color: const Color(0xFFE0F2FE),
                  borderRadius: BorderRadius.circular(20),
                ),
                child: const Icon(
                  Icons.lock_reset_rounded,
                  size: 42,
                  color: Color(0xFF0077FF),
                ),
              ),
            ),
            const SizedBox(height: 24),
            const Text(
              'Quên mật khẩu?',
              textAlign: TextAlign.center,
              style: TextStyle(
                fontSize: 22,
                fontWeight: FontWeight.w800,
                color: Color(0xFF0B1B3D),
              ),
            ),
            const SizedBox(height: 8),
            const Text(
              'Nhập địa chỉ email đã đăng ký. Chúng tôi sẽ gửi mã OTP 6 chữ số để khôi phục mật khẩu.',
              textAlign: TextAlign.center,
              style: TextStyle(
                fontSize: 13,
                color: Color(0xFF64748B),
                height: 1.4,
              ),
            ),
            const SizedBox(height: 28),
            if (_errorMessage != null) ...[
              AuthErrorBanner(errorMessage: _errorMessage!),
              const SizedBox(height: 16),
            ],
            AuthTextField(
              controller: _emailCtrl,
              label: 'Địa chỉ email',
              hintText: 'worker@cani.vn',
              keyboardType: TextInputType.emailAddress,
              prefixIcon: const Icon(
                Icons.email_outlined,
                color: Color(0xFF0077FF),
                size: 20,
              ),
              validator: (val) {
                if (val == null || val.trim().isEmpty) {
                  return 'Vui lòng nhập địa chỉ email';
                }
                if (!val.contains('@') || !val.contains('.')) {
                  return 'Email không đúng định dạng';
                }
                return null;
              },
            ),
            const SizedBox(height: 24),
            _buildGradientButton(
              text: 'GỬI MÃ XÁC THỰC',
              isLoading: _isLoading,
              onPressed: _isLoading ? null : _handleSendOtp,
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildStep2ResetPassword(BuildContext context) {
    final screenWidth = MediaQuery.sizeOf(context).width;

    return SingleChildScrollView(
      padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
      child: Form(
        key: _resetFormKey,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const Text(
              'Thiết Lập Mật Khẩu Mới',
              textAlign: TextAlign.center,
              style: TextStyle(
                fontSize: 22,
                fontWeight: FontWeight.w800,
                color: Color(0xFF0B1B3D),
              ),
            ),
            const SizedBox(height: 8),
            Text(
              'Mã OTP 6 số đã được gửi đến ${_emailCtrl.text.trim()}. Nhập mã và mật khẩu mới bên dưới.',
              textAlign: TextAlign.center,
              style: const TextStyle(
                fontSize: 13,
                color: Color(0xFF64748B),
                height: 1.4,
              ),
            ),
            const SizedBox(height: 20),
            if (_errorMessage != null) ...[
              AuthErrorBanner(errorMessage: _errorMessage!),
              const SizedBox(height: 16),
            ],
            OtpPinInputRow(
              controllers: _otpCtrls,
              focusNodes: _otpNodes,
              screenWidth: screenWidth,
            ),
            const SizedBox(height: 12),
            OtpResendCountdown(
              countdown: _countdown,
              isSendingOtp: _isSendingOtp,
              onResend: _handleResendOtp,
            ),
            const SizedBox(height: 20),
            AuthTextField(
              controller: _passwordCtrl,
              label: 'Mật khẩu mới',
              hintText: 'Tối thiểu 8 ký tự',
              obscureText: _obscurePassword,
              prefixIcon: const Icon(
                Icons.lock_outline,
                color: Color(0xFF0077FF),
                size: 20,
              ),
              suffixIcon: IconButton(
                icon: Icon(
                  _obscurePassword
                      ? Icons.visibility_outlined
                      : Icons.visibility_off_outlined,
                  color: Colors.grey.shade600,
                  size: 20,
                ),
                onPressed: () =>
                    setState(() => _obscurePassword = !_obscurePassword),
              ),
              onChanged: (_) => setState(() {}),
              validator: (val) {
                if (val == null || val.isEmpty) {
                  return 'Vui lòng nhập mật khẩu mới';
                }
                return null;
              },
            ),
            const SizedBox(height: 12),
            PasswordStrengthChecklist(password: _passwordCtrl.text),
            const SizedBox(height: 16),
            AuthTextField(
              controller: _confirmPasswordCtrl,
              label: 'Xác nhận mật khẩu mới',
              hintText: 'Nhập lại mật khẩu mới',
              obscureText: _obscureConfirmPassword,
              prefixIcon: const Icon(
                Icons.lock_outline,
                color: Color(0xFF0077FF),
                size: 20,
              ),
              suffixIcon: IconButton(
                icon: Icon(
                  _obscureConfirmPassword
                      ? Icons.visibility_outlined
                      : Icons.visibility_off_outlined,
                  color: Colors.grey.shade600,
                  size: 20,
                ),
                onPressed: () => setState(() =>
                    _obscureConfirmPassword = !_obscureConfirmPassword),
              ),
              validator: (val) {
                if (val == null || val.isEmpty) {
                  return 'Vui lòng xác nhận mật khẩu';
                }
                if (val != _passwordCtrl.text) {
                  return 'Mật khẩu xác nhận không khớp';
                }
                return null;
              },
            ),
            const SizedBox(height: 24),
            _buildGradientButton(
              text: 'ĐỔI MẬT KHẨU & ĐĂNG NHẬP',
              isLoading: _isLoading,
              onPressed: _isLoading ? null : _handleConfirmReset,
            ),
            const SizedBox(height: 16),
          ],
        ),
      ),
    );
  }

  Widget _buildGradientButton({
    required String text,
    required bool isLoading,
    required VoidCallback? onPressed,
  }) {
    final isEnabled = onPressed != null;

    return Container(
      height: 52,
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(12),
        gradient: isEnabled
            ? const LinearGradient(
                colors: [Color(0xFF00D2FF), Color(0xFF0066FF)],
                begin: Alignment.centerLeft,
                end: Alignment.centerRight,
              )
            : null,
        color: isEnabled ? null : const Color(0xFFE2E8F0),
        boxShadow: isEnabled
            ? [
                BoxShadow(
                  color: const Color(0xFF0077FF).withValues(alpha: 0.35),
                  blurRadius: 14,
                  offset: const Offset(0, 5),
                ),
              ]
            : null,
      ),
      child: ElevatedButton(
        onPressed: onPressed,
        style: ElevatedButton.styleFrom(
          backgroundColor: Colors.transparent,
          disabledBackgroundColor: Colors.transparent,
          shadowColor: Colors.transparent,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
          ),
        ),
        child: isLoading
            ? const SizedBox(
                height: 22,
                width: 22,
                child: CircularProgressIndicator(
                  strokeWidth: 2.5,
                  color: Colors.white,
                ),
              )
            : Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Text(
                    text,
                    style: TextStyle(
                      fontSize: 15,
                      fontWeight: FontWeight.w700,
                      color: isEnabled ? Colors.white : const Color(0xFF94A3B8),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Icon(
                    Icons.arrow_forward_rounded,
                    color: isEnabled ? Colors.white : const Color(0xFF94A3B8),
                    size: 20,
                  ),
                ],
              ),
      ),
    );
  }
}

