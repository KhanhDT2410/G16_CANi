import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/features/auth/providers/auth_provider.dart';
import 'package:cani/features/ekyc/screens/ekyc_verification_screen.dart';
import 'package:cani/shared/widgets/cani_brand_logo.dart';

/// VerifyEmailScreen - Màn hình Xác thực mã OTP qua Email riêng biệt (UC-01)
/// Nhận thông tin đăng ký từ RegisterScreen, cho phép nhập mã OTP 6 số,
/// gửi lại OTP kèm vòng đếm ngược 60s. Sau khi đăng ký thành công, tự động
/// đăng nhập và chuyển hướng sang luồng eKYC Onboarding.
class VerifyEmailScreen extends ConsumerStatefulWidget {
  final String email;
  final String password;
  final String fullName;
  final String? phone;

  const VerifyEmailScreen({
    super.key,
    required this.email,
    required this.password,
    required this.fullName,
    this.phone,
  });

  @override
  ConsumerState<VerifyEmailScreen> createState() => _VerifyEmailScreenState();
}

class _VerifyEmailScreenState extends ConsumerState<VerifyEmailScreen> {
  final List<TextEditingController> _otpControllers =
      List.generate(6, (_) => TextEditingController());
  final List<FocusNode> _otpFocusNodes = List.generate(6, (_) => FocusNode());

  int _countdown = 60;
  Timer? _timer;
  bool _isSendingOtp = false;

  @override
  void initState() {
    super.initState();
    _startCountdown();
  }

  @override
  void dispose() {
    _timer?.cancel();
    for (final c in _otpControllers) {
      c.dispose();
    }
    for (final f in _otpFocusNodes) {
      f.dispose();
    }
    super.dispose();
  }

  String get _combinedOtpCode =>
      _otpControllers.map((c) => c.text.trim()).join();

  void _startCountdown() {
    setState(() => _countdown = 60);
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

  Future<void> _handleResendOtp() async {
    setState(() => _isSendingOtp = true);
    final success =
        await ref.read(authStateProvider.notifier).sendOtp(widget.email);
    if (!mounted) return;
    setState(() => _isSendingOtp = false);

    if (success) {
      _startCountdown();
      _showToast('Mã xác thực mới đã được gửi tới email của bạn.', isError: false);
      _otpFocusNodes[0].requestFocus();
    }
  }

  Future<void> _submitVerification() async {
    final otp = _combinedOtpCode;
    if (otp.length != 6) {
      _showToast('Vui lòng nhập đủ 6 chữ số mã xác thực OTP.', isError: true);
      return;
    }

    // 1. Đăng ký tài khoản với backend
    final registered = await ref.read(authStateProvider.notifier).register(
          email: widget.email,
          password: widget.password,
          fullName: widget.fullName,
          otpCode: otp,
          phone: widget.phone,
        );

    if (!mounted) return;

    if (!registered) {
      // Giữ nguyên form tại VerifyEmailScreen để người dùng kiểm tra hoặc gửi lại OTP
      return;
    }

    // 2. Tự động đăng nhập để lưu JWT session token phục vụ luồng eKYC
    final loggedIn = await ref.read(authStateProvider.notifier).login(
          widget.email,
          widget.password,
        );

    if (!mounted) return;

    if (loggedIn) {
      _showToast('Xác thực email thành công! Bắt đầu định danh CCCD.', isError: false);
      // Chuyển hướng sang màn hình eKYC Onboarding (xóa stack auth)
      Navigator.of(context).pushAndRemoveUntil(
        MaterialPageRoute(
          builder: (_) => const EkycVerificationScreen(isOnboarding: true),
        ),
        (route) => false,
      );
    } else {
      // Trường hợp hiếm: tạo user thành công nhưng login có sự cố -> pop về LoginScreen
      _showToast('Tài khoản đã tạo thành công. Vui lòng đăng nhập.', isError: false);
      Navigator.of(context).popUntil((route) => route.isFirst);
    }
  }

  void _showToast(String message, {required bool isError}) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(message),
        backgroundColor:
            isError ? const Color(0xFFDC2626) : const Color(0xFF10B981),
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
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              _buildTopNavigation(context),
              const SizedBox(height: 12),
              _buildBrandHeader(screenWidth),
              const SizedBox(height: 24),
              _buildEmailBadge(),
              const SizedBox(height: 24),
              if (authState.errorMessage != null) ...[
                _buildErrorAlert(authState.errorMessage!),
                const SizedBox(height: 16),
              ],
              _buildOtpBoxesSection(screenWidth),
              const SizedBox(height: 24),
              _buildResendSection(),
              const SizedBox(height: 32),
              _buildGradientSubmitButton(authState),
            ],
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
          width: (screenWidth * 0.55).clamp(160.0, 220.0),
        ),
        const SizedBox(height: 12),
        const Text(
          'Xác Thực Email',
          textAlign: TextAlign.center,
          style: TextStyle(
            fontSize: 22,
            fontWeight: FontWeight.w800,
            color: Color(0xFF0B1B3D),
            letterSpacing: -0.3,
          ),
        ),
        const SizedBox(height: 6),
        const Text(
          'Vui lòng nhập mã OTP 6 chữ số vừa được gửi đến hòm thư:',
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

  Widget _buildEmailBadge() {
    return Center(
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
        decoration: BoxDecoration(
          color: const Color(0xFFF0F7FF),
          borderRadius: BorderRadius.circular(20),
          border: Border.all(color: const Color(0xFFDBEAFE)),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.email_outlined, size: 16, color: Color(0xFF0066FF)),
            const SizedBox(width: 8),
            Text(
              widget.email,
              style: const TextStyle(
                fontSize: 13.5,
                fontWeight: FontWeight.w700,
                color: Color(0xFF0066FF),
              ),
            ),
          ],
        ),
      ),
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

  Widget _buildOtpBoxesSection(double screenWidth) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: List.generate(6, (index) {
            return SizedBox(
              width: (screenWidth > 380) ? 46 : 42,
              height: 54,
              child: TextFormField(
                controller: _otpControllers[index],
                focusNode: _otpFocusNodes[index],
                keyboardType: TextInputType.number,
                textAlign: TextAlign.center,
                maxLength: 1,
                style: const TextStyle(
                  fontSize: 22,
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
                    borderSide:
                        const BorderSide(color: Color(0xFFE2E8F0), width: 1.5),
                  ),
                  enabledBorder: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(12),
                    borderSide:
                        const BorderSide(color: Color(0xFFE2E8F0), width: 1.5),
                  ),
                  focusedBorder: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(12),
                    borderSide:
                        const BorderSide(color: Color(0xFF0077FF), width: 2),
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

  Widget _buildResendSection() {
    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        const Text(
          'Không nhận được mã? ',
          style: TextStyle(fontSize: 13, color: Color(0xFF64748B)),
        ),
        if (_countdown > 0)
          Text(
            'Gửi lại sau ${_countdown}s',
            style: const TextStyle(
              fontSize: 13,
              fontWeight: FontWeight.w700,
              color: Color(0xFF059669),
            ),
          )
        else
          InkWell(
            onTap: _isSendingOtp ? null : _handleResendOtp,
            borderRadius: BorderRadius.circular(6),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
              child: _isSendingOtp
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
        onPressed: authState.isLoading ? null : _submitVerification,
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
                    'Xác Nhận & Hoàn Tất Đăng Ký',
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
}

