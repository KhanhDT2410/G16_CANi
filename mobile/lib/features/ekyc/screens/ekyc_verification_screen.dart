import 'dart:typed_data';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/app/home_screen.dart';
import 'package:cani/shared/widgets/app_back_button.dart';
import 'package:cani/shared/widgets/registration_stepper.dart';
import '../providers/ekyc_provider.dart';
import '../widgets/ekyc_capture_box.dart';
import '../widgets/ekyc_confirmed_info_card.dart';
import '../widgets/ekyc_qr_scan_banner.dart';
import '../widgets/ekyc_result_card.dart';
import '../widgets/ekyc_status_header.dart';
import 'card_scanner_screen.dart';
import 'cccd_confirm_dialog.dart';
import 'cccd_qr_scanner_screen.dart';

/// EkycVerificationScreen - Màn hình định danh điện tử eKYC cho Worker (UC-01)
class EkycVerificationScreen extends ConsumerStatefulWidget {
  final bool isOnboarding;

  const EkycVerificationScreen({
    super.key,
    this.isOnboarding = false,
  });

  @override
  ConsumerState<EkycVerificationScreen> createState() =>
      _EkycVerificationScreenState();
}

class _EkycVerificationScreenState
    extends ConsumerState<EkycVerificationScreen> {
  @override
  void initState() {
    super.initState();
    if (widget.isOnboarding) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        ref.read(ekycProvider.notifier).resetForm();
      });
    }
  }

  Future<void> _pickImage(Function(Uint8List, String) onPicked) async {
    try {
      final result = await FilePickerPlatform.instance.pickFiles(
        type: FileType.custom,
        allowedExtensions: ['jpg', 'jpeg', 'png'],
      );

      if (result.isNotEmpty) {
        final file = result.first;
        final bytes = await file.readAsBytes();
        onPicked(bytes, file.name);
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Không thể chọn ảnh: $e')),
        );
      }
    }
  }

  Future<void> _openQrScanner() async {
    final notifier = ref.read(ekycProvider.notifier);
    final result = await Navigator.of(context).push<CccdCardInfo>(
      MaterialPageRoute(
        builder: (_) => const CccdQrScannerScreen(),
      ),
    );

    if (result != null && mounted) {
      notifier.setConfirmedCccdInfo(
        cccdNumber: result.cccdNumber,
        fullName: result.fullName,
        dob: result.dob,
        gender: result.gender,
        residenceAddress: result.residenceAddress,
        issueDate: result.issueDate,
      );

      _openConfirmDialog();
    }
  }

  void _openConfirmDialog() {
    final state = ref.read(ekycProvider);
    final notifier = ref.read(ekycProvider.notifier);

    CccdConfirmDialog.show(
      context,
      state: state,
      onConfirm: notifier.setConfirmedCccdInfo,
    );
  }

  Future<void> _selectImageSource({
    required BuildContext context,
    required String title,
    required ScanType scanType,
    required Function(Uint8List, String) onPicked,
  }) async {
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.white,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (ctx) {
        return SafeArea(
          child: Padding(
            padding: const EdgeInsets.symmetric(vertical: 22, horizontal: 16),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text(
                  title,
                  style: const TextStyle(
                    fontSize: 17,
                    fontWeight: FontWeight.w800,
                    color: Color(0xFF0B1B3D),
                    letterSpacing: -0.3,
                  ),
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 18),
                if (scanType == ScanType.cccdFront) ...[
                  ListTile(
                    leading: Container(
                      padding: const EdgeInsets.all(10),
                      decoration: const BoxDecoration(
                        color: Color(0xFFF5F3FF),
                        shape: BoxShape.circle,
                      ),
                      child: const Icon(Icons.qr_code_scanner,
                          color: Color(0xFF7C3AED)),
                    ),
                    title: const Text(
                      'Quét mã QR góc thẻ CCCD',
                      style: TextStyle(
                        fontWeight: FontWeight.w700,
                        fontSize: 14.5,
                        color: Color(0xFF0B1B3D),
                      ),
                    ),
                    subtitle: const Text(
                      'Đọc đúng 100% dữ liệu Bộ Công An (không dùng AI)',
                      style: TextStyle(
                        fontSize: 12,
                        color: Color(0xFF64748B),
                      ),
                    ),
                    trailing: const Icon(Icons.arrow_forward_ios,
                        size: 14, color: Color(0xFF94A3B8)),
                    onTap: () {
                      Navigator.of(ctx).pop();
                      _openQrScanner();
                    },
                  ),
                  const Divider(color: Color(0xFFF1F5F9), height: 16),
                ],
                ListTile(
                  leading: Container(
                    padding: const EdgeInsets.all(10),
                    decoration: const BoxDecoration(
                      color: Color(0xFFECFDF5),
                      shape: BoxShape.circle,
                    ),
                    child: const Icon(Icons.camera_alt, color: Color(0xFF10B981)),
                  ),
                  title: const Text(
                    'Chụp trực tiếp bằng Camera',
                    style: TextStyle(
                      fontWeight: FontWeight.w700,
                      fontSize: 14.5,
                      color: Color(0xFF0B1B3D),
                    ),
                  ),
                  subtitle: const Text(
                    'Có khung căn chỉnh thẻ CCCD & tự động hướng dẫn',
                    style: TextStyle(
                      fontSize: 12,
                      color: Color(0xFF64748B),
                    ),
                  ),
                  trailing: const Icon(Icons.arrow_forward_ios,
                      size: 14, color: Color(0xFF94A3B8)),
                  onTap: () async {
                    Navigator.of(ctx).pop();
                    final result =
                        await Navigator.of(context).push<CardScanResult>(
                      MaterialPageRoute(
                        builder: (_) => CardScannerScreen(scanType: scanType),
                      ),
                    );
                    if (result != null) {
                      onPicked(result.bytes, result.fileName);
                    }
                  },
                ),
                const Divider(color: Color(0xFFF1F5F9), height: 16),
                ListTile(
                  leading: Container(
                    padding: const EdgeInsets.all(10),
                    decoration: const BoxDecoration(
                      color: Color(0xFFEFF6FF),
                      shape: BoxShape.circle,
                    ),
                    child: const Icon(Icons.photo_library,
                        color: Color(0xFF0077FF)),
                  ),
                  title: const Text(
                    'Chọn ảnh từ Thư viện máy',
                    style: TextStyle(
                      fontWeight: FontWeight.w700,
                      fontSize: 14.5,
                      color: Color(0xFF0B1B3D),
                    ),
                  ),
                  subtitle: const Text(
                    'Tải ảnh đã chụp sẵn trong bộ nhớ thiết bị',
                    style: TextStyle(
                      fontSize: 12,
                      color: Color(0xFF64748B),
                    ),
                  ),
                  trailing: const Icon(Icons.arrow_forward_ios,
                      size: 14, color: Color(0xFF94A3B8)),
                  onTap: () {
                    Navigator.of(ctx).pop();
                    _pickImage(onPicked);
                  },
                ),
              ],
            ),
          ),
        );
      },
    );
  }

  void _navigateToHome() {
    Navigator.of(context).pushAndRemoveUntil(
      MaterialPageRoute(builder: (_) => const HomeScreen()),
      (route) => false,
    );
  }

  Future<void> _handlePostponeOrExit() async {
    if (!widget.isOnboarding) {
      Navigator.of(context).maybePop();
      return;
    }

    final shouldLeave = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        backgroundColor: Colors.white,
        title: const Row(
          children: [
            Icon(Icons.info_outline, color: Color(0xFF0077FF)),
            SizedBox(width: 8),
            Text(
              'Tạm dừng định danh?',
              style: TextStyle(
                fontSize: 18,
                fontWeight: FontWeight.w700,
                color: Color(0xFF0B1B3D),
              ),
            ),
          ],
        ),
        content: const Text(
          'Bạn có thể hoàn tất định danh eKYC bất cứ lúc nào trong mục Hồ sơ cá nhân để kích hoạt tài khoản và nhận ca làm việc.',
          style: TextStyle(
            fontSize: 14,
            color: Color(0xFF64748B),
            height: 1.4,
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(false),
            child: const Text(
              'Ở lại tiếp tục',
              style: TextStyle(
                fontWeight: FontWeight.w700,
                color: Color(0xFF0077FF),
              ),
            ),
          ),
          ElevatedButton(
            onPressed: () => Navigator.of(ctx).pop(true),
            style: ElevatedButton.styleFrom(
              backgroundColor: const Color(0xFFEFF6FF),
              elevation: 0,
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(10),
              ),
            ),
            child: const Text(
              'Để sau (Về trang chủ)',
              style: TextStyle(
                fontWeight: FontWeight.w700,
                color: Color(0xFF1E293B),
              ),
            ),
          ),
        ],
      ),
    );

    if (shouldLeave == true && mounted) {
      _navigateToHome();
    }
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(ekycProvider);
    final notifier = ref.read(ekycProvider.notifier);

    final scaffold = Scaffold(
      backgroundColor: Colors.white,
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              _buildTopHeader(context),
              if (widget.isOnboarding) ...[
                const SizedBox(height: 10),
                const RegistrationStepper(currentStep: 3),
                const SizedBox(height: 12),
                const Text(
                  'Bước cuối: Xác thực căn cước công dân để bắt đầu nhận ca làm việc',
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    fontSize: 13,
                    fontWeight: FontWeight.w500,
                    color: Color(0xFF64748B),
                  ),
                ),
              ],
              const SizedBox(height: 16),
              EkycStatusHeader(item: state.result),
              const SizedBox(height: 16),
              EkycQrScanBanner(
                onScanQr: _openQrScanner,
                onEditInfo: _openConfirmDialog,
              ),
              const SizedBox(height: 16),
              if (state.hasConfirmedInfo) ...[
                EkycConfirmedInfoCard(
                  state: state,
                  onEditInfo: _openConfirmDialog,
                ),
                const SizedBox(height: 16),
              ],
              EkycCaptureBox(
                step: '1',
                title: 'Mặt trước CCCD',
                subtitle: 'Chụp rõ nét, đủ 4 góc, không lóa sáng',
                icon: Icons.credit_card,
                bytes: state.frontBytes,
                fileName: state.frontFileName,
                onTap: () => _selectImageSource(
                  context: context,
                  title: 'Mặt Trước Thẻ CCCD',
                  scanType: ScanType.cccdFront,
                  onPicked: notifier.setFrontImage,
                ),
              ),
              const SizedBox(height: 12),
              EkycCaptureBox(
                step: '2',
                title: 'Mặt sau CCCD',
                subtitle: 'Chụp rõ đặc điểm nhận dạng và ngày cấp',
                icon: Icons.flip_to_back,
                bytes: state.backBytes,
                fileName: state.backFileName,
                onTap: () => _selectImageSource(
                  context: context,
                  title: 'Mặt Sau Thẻ CCCD',
                  scanType: ScanType.cccdBack,
                  onPicked: notifier.setBackImage,
                ),
              ),
              const SizedBox(height: 12),
              EkycCaptureBox(
                step: '3',
                title: 'Ảnh Chân Dung Selfie',
                subtitle: 'Chụp chính diện, rõ mặt, không đeo kính râm/khẩu trang',
                icon: Icons.face_retouching_natural,
                bytes: state.selfieBytes,
                fileName: state.selfieFileName,
                onTap: () => _selectImageSource(
                  context: context,
                  title: 'Ảnh Chân Dung Selfie',
                  scanType: ScanType.selfie,
                  onPicked: notifier.setSelfieImage,
                ),
              ),

              const SizedBox(height: 24),
              _buildSubmitButton(state, notifier),
              if (state.result != null) ...[
                const SizedBox(height: 24),
                EkycResultCard(item: state.result!),
              ],
              const SizedBox(height: 16),
            ],
          ),
        ),
      ),
    );

    if (widget.isOnboarding) {
      return PopScope(
        canPop: false,
        onPopInvokedWithResult: (didPop, _) {
          if (!didPop) {
            _handlePostponeOrExit();
          }
        },
        child: scaffold,
      );
    }

    return scaffold;
  }

  Widget _buildTopHeader(BuildContext context) {
    return Row(
      children: [
        AppBackButton(
          onTap: _handlePostponeOrExit,
        ),
        const SizedBox(width: 14),
        const Expanded(
          child: Text(
            'Định danh eKYC',
            style: TextStyle(
              fontSize: 21,
              fontWeight: FontWeight.w800,
              color: Color(0xFF0B1B3D),
              letterSpacing: -0.3,
            ),
          ),
        ),
        if (widget.isOnboarding)
          TextButton(
            onPressed: _handlePostponeOrExit,
            child: const Text(
              'Hoàn thiện sau',
              style: TextStyle(
                fontWeight: FontWeight.w700,
                fontSize: 13.5,
                color: Color(0xFF0077FF),
              ),
            ),
          ),
      ],
    );
  }

  Widget _buildSubmitButton(EkycState state, EkycNotifier notifier) {
    final isReady = state.isReadyToSubmit;

    return Container(
      height: 52,
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(12),
        gradient: isReady
            ? const LinearGradient(
                colors: [Color(0xFF00D2FF), Color(0xFF0066FF)],
                begin: Alignment.centerLeft,
                end: Alignment.centerRight,
              )
            : null,
        color: isReady ? null : const Color(0xFFE2E8F0),
        boxShadow: isReady
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
        onPressed: isReady
            ? () async {
                final success = await notifier.submitVerification();
                if (success && mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(
                      content: Text('Đã gửi hồ sơ eKYC thẩm định thành công!'),
                      backgroundColor: Color(0xFF10B981),
                    ),
                  );
                  if (widget.isOnboarding) {
                    _navigateToHome();
                  }
                }
              }
            : null,
        style: ElevatedButton.styleFrom(
          backgroundColor: Colors.transparent,
          disabledBackgroundColor: Colors.transparent,
          shadowColor: Colors.transparent,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
          ),
        ),
        child: state.isLoading
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
                  Icon(
                    Icons.verified_user,
                    color: isReady ? Colors.white : const Color(0xFF94A3B8),
                    size: 20,
                  ),
                  const SizedBox(width: 8),
                  Text(
                    state.isLoading ? 'Đang gửi hồ sơ xác thực...' : 'Gửi Hồ Sơ Xác Thực eKYC',
                    style: TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.w700,
                      color: isReady ? Colors.white : const Color(0xFF94A3B8),
                    ),
                  ),
                ],
              ),
      ),
    );
  }
}
