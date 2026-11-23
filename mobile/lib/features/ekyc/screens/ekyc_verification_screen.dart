import 'dart:typed_data';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/app/home_screen.dart';
import 'package:cani/shared/widgets/app_back_button.dart';
import 'package:cani/shared/widgets/registration_stepper.dart';
import '../models/ekyc_models.dart';
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

  const EkycVerificationScreen({super.key, this.isOnboarding = false});

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
        ref.read(ekycProvider.notifier).clearLocalForm();
      });
    }
  }

  void _handleImageSelected(
    String? Function(Uint8List, String) setter,
    Uint8List bytes,
    String fileName,
  ) {
    final err = setter(bytes, fileName);
    if (err != null && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(err),
          backgroundColor: const Color(0xFFDC2626),
        ),
      );
    }
  }

  Future<void> _pickImage(Function(Uint8List, String) onPicked) async {
    try {
      final result = await FilePickerPlatform.instance.pickFiles(
        type: FileType.custom,
        allowedExtensions: ['jpg', 'jpeg', 'png', 'webp'],
      );

      if (result.isNotEmpty) {
        final file = result.first;
        final bytes = await file.readAsBytes();
        onPicked(bytes, file.name);
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('Không thể chọn ảnh: $e')));
      }
    }
  }

  Future<void> _openQrScanner() async {
    final notifier = ref.read(ekycProvider.notifier);
    final result = await Navigator.of(context).push<CccdCardInfo>(
      MaterialPageRoute(builder: (_) => const CccdQrScannerScreen()),
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
                      child: const Icon(
                        Icons.qr_code_scanner,
                        color: Color(0xFF7C3AED),
                      ),
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
                      style: TextStyle(fontSize: 12, color: Color(0xFF64748B)),
                    ),
                    trailing: const Icon(
                      Icons.arrow_forward_ios,
                      size: 14,
                      color: Color(0xFF94A3B8),
                    ),
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
                    child: const Icon(
                      Icons.camera_alt,
                      color: Color(0xFF10B981),
                    ),
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
                    style: TextStyle(fontSize: 12, color: Color(0xFF64748B)),
                  ),
                  trailing: const Icon(
                    Icons.arrow_forward_ios,
                    size: 14,
                    color: Color(0xFF94A3B8),
                  ),
                  onTap: () async {
                    Navigator.of(ctx).pop();
                    final result = await Navigator.of(context)
                        .push<CardScanResult>(
                          MaterialPageRoute(
                            builder: (_) =>
                                CardScannerScreen(scanType: scanType),
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
                    child: const Icon(
                      Icons.photo_library,
                      color: Color(0xFF0077FF),
                    ),
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
                    style: TextStyle(fontSize: 12, color: Color(0xFF64748B)),
                  ),
                  trailing: const Icon(
                    Icons.arrow_forward_ios,
                    size: 14,
                    color: Color(0xFF94A3B8),
                  ),
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
    final state = ref.read(ekycProvider);
    if (state.result != null) {
      if (widget.isOnboarding) {
        _navigateToHome();
      } else {
        Navigator.of(context).maybePop();
      }
      return;
    }

    if (!widget.isOnboarding) {
      Navigator.of(context).maybePop();
      return;
    }

    final shouldLeave = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        backgroundColor: Colors.white,
        titlePadding: const EdgeInsets.fromLTRB(22, 22, 22, 12),
        contentPadding: const EdgeInsets.symmetric(horizontal: 22),
        actionsPadding: const EdgeInsets.fromLTRB(20, 20, 20, 20),
        title: const Row(
          children: [
            Icon(Icons.info_outline, color: Color(0xFF0077FF), size: 24),
            SizedBox(width: 10),
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
            height: 1.45,
          ),
        ),
        actions: [
          Row(
            children: [
              Expanded(
                child: OutlinedButton(
                  onPressed: () => Navigator.of(ctx).pop(true),
                  style: OutlinedButton.styleFrom(
                    padding: const EdgeInsets.symmetric(vertical: 12),
                    side: const BorderSide(
                      color: Color(0xFFE2E8F0),
                      width: 1.5,
                    ),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12),
                    ),
                  ),
                  child: const FittedBox(
                    fit: BoxFit.scaleDown,
                    child: Text(
                      'Để sau (Về home)',
                      style: TextStyle(
                        fontWeight: FontWeight.w600,
                        fontSize: 13.5,
                        color: Color(0xFF64748B),
                      ),
                    ),
                  ),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: ElevatedButton(
                  onPressed: () => Navigator.of(ctx).pop(false),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: const Color(0xFF0077FF),
                    foregroundColor: Colors.white,
                    elevation: 0,
                    padding: const EdgeInsets.symmetric(vertical: 12),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12),
                    ),
                  ),
                  child: const FittedBox(
                    fit: BoxFit.scaleDown,
                    child: Text(
                      'Ở lại tiếp tục',
                      style: TextStyle(
                        fontWeight: FontWeight.w700,
                        fontSize: 13.5,
                        color: Colors.white,
                      ),
                    ),
                  ),
                ),
              ),
            ],
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
    final isResultView = state.result != null;

    final scaffold = Scaffold(
      backgroundColor: Colors.white,
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
          child: isResultView
              ? _buildResultView(context, state, notifier)
              : _buildFormView(context, state, notifier),
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

  Widget _buildTopHeader(BuildContext context, {bool isResultView = false}) {
    return Row(
      children: [
        AppBackButton(onTap: _handlePostponeOrExit),
        const SizedBox(width: 14),
        Expanded(
          child: Text(
            isResultView ? 'Kết quả định danh eKYC' : 'Định danh eKYC',
            style: const TextStyle(
              fontSize: 21,
              fontWeight: FontWeight.w800,
              color: Color(0xFF0B1B3D),
              letterSpacing: -0.3,
            ),
          ),
        ),
        if (widget.isOnboarding && !isResultView)
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

  Widget _buildFormView(
    BuildContext context,
    EkycState state,
    EkycNotifier notifier,
  ) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _buildTopHeader(context, isResultView: false),
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
        const EkycStatusHeader(item: null),
        const SizedBox(height: 16),
        EkycQrScanBanner(
          onScanQr: _openQrScanner,
          onEditInfo: _openConfirmDialog,
        ),
        const SizedBox(height: 16),
        if (state.hasConfirmedInfo) ...[
          EkycConfirmedInfoCard(state: state, onEditInfo: _openConfirmDialog),
          const SizedBox(height: 16),
        ],
        EkycCaptureBox(
          step: '1',
          title: 'Mặt trước CCCD',
          subtitle: 'Chụp rõ nét, đủ 4 góc, không lóa sáng (tối đa 5MB)',
          icon: Icons.credit_card,
          bytes: state.frontBytes,
          fileName: state.frontFileName,
          onTap: state.isLoading
              ? () {}
              : () => _selectImageSource(
                    context: context,
                    title: 'Mặt Trước Thẻ CCCD',
                    scanType: ScanType.cccdFront,
                    onPicked: (b, n) =>
                        _handleImageSelected(notifier.setFrontImage, b, n),
                  ),
        ),
        const SizedBox(height: 12),
        EkycCaptureBox(
          step: '2',
          title: 'Mặt sau CCCD',
          subtitle: 'Chụp rõ đặc điểm nhận dạng và ngày cấp (tối đa 5MB)',
          icon: Icons.flip_to_back,
          bytes: state.backBytes,
          fileName: state.backFileName,
          onTap: state.isLoading
              ? () {}
              : () => _selectImageSource(
                    context: context,
                    title: 'Mặt Sau Thẻ CCCD',
                    scanType: ScanType.cccdBack,
                    onPicked: (b, n) =>
                        _handleImageSelected(notifier.setBackImage, b, n),
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
          onTap: state.isLoading
              ? () {}
              : () => _selectImageSource(
                    context: context,
                    title: 'Ảnh Chân Dung Selfie',
                    scanType: ScanType.selfie,
                    onPicked: (b, n) =>
                        _handleImageSelected(notifier.setSelfieImage, b, n),
                  ),
        ),
        if (state.isLoading) ...[
          const SizedBox(height: 16),
          Container(
            padding: const EdgeInsets.all(14),
            decoration: BoxDecoration(
              color: const Color(0xFFEFF6FF),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: const Color(0xFFBFDBFE)),
            ),
            child: const Row(
              children: [
                SizedBox(
                  width: 20,
                  height: 20,
                  child: CircularProgressIndicator(
                    strokeWidth: 2.4,
                    color: Color(0xFF0077FF),
                  ),
                ),
                SizedBox(width: 12),
                Expanded(
                  child: Text(
                    'Đang gửi ảnh và xác minh danh tính với VNPT eKYC. Vui lòng không thoát ứng dụng...',
                    style: TextStyle(
                      fontSize: 12.5,
                      fontWeight: FontWeight.w600,
                      color: Color(0xFF1E40AF),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ] else if (state.errorMessage != null) ...[
          const SizedBox(height: 16),
          Container(
            padding: const EdgeInsets.all(14),
            decoration: BoxDecoration(
              color: state.isTechnicalError
                  ? const Color(0xFFFFF7ED)
                  : const Color(0xFFFEF2F2),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(
                color: state.isTechnicalError
                    ? const Color(0xFFFED7AA)
                    : const Color(0xFFFECACA),
              ),
            ),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Icon(
                  state.isTechnicalError
                      ? Icons.cloud_off_rounded
                      : Icons.error_outline,
                  color: state.isTechnicalError
                      ? const Color(0xFFEA580C)
                      : const Color(0xFFDC2626),
                  size: 20,
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(
                    state.errorMessage!,
                    style: TextStyle(
                      fontSize: 12.5,
                      fontWeight: FontWeight.w600,
                      color: state.isTechnicalError
                          ? const Color(0xFF9A3412)
                          : const Color(0xFF991B1B),
                      height: 1.4,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ],
        const SizedBox(height: 16),
        Container(
          padding: const EdgeInsets.all(12),
          decoration: BoxDecoration(
            color: const Color(0xFFF8FAFC),
            borderRadius: BorderRadius.circular(12),
            border: Border.all(color: const Color(0xFFE2E8F0)),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                state.consentPolicy.title,
                style: const TextStyle(
                  fontSize: 12.5,
                  fontWeight: FontWeight.w700,
                  color: Color(0xFF0F172A),
                ),
              ),
              const SizedBox(height: 4),
              Text(
                state.consentPolicy.summary,
                style: const TextStyle(
                  fontSize: 11.5,
                  color: Color(0xFF475569),
                  height: 1.4,
                ),
              ),
              const SizedBox(height: 6),
              CheckboxListTile(
                contentPadding: EdgeInsets.zero,
                controlAffinity: ListTileControlAffinity.leading,
                dense: true,
                value: state.consentAccepted,
                onChanged: state.isLoading
                    ? null
                    : (checked) => notifier.setConsentAccepted(checked ?? false),
                title: Text(
                  'Tôi đồng ý cho CANi xử lý ảnh CCCD & sinh trắc học qua VNPT eKYC (${state.consentPolicy.noticeVersion}).',
                  style: const TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.w600,
                    color: Color(0xFF1E293B),
                  ),
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 16),
        _buildSubmitButton(state, notifier),
        const SizedBox(height: 20),
      ],
    );
  }

  Widget _buildResultView(
    BuildContext context,
    EkycState state,
    EkycNotifier notifier,
  ) {
    final item = state.result!;
    final String onboardingSubtitle;
    if (item.isApproved) {
      onboardingSubtitle =
          'Chúc mừng! Danh tính của bạn đã được VNPT eKYC xác minh thành công.';
    } else if (item.isPendingReview) {
      onboardingSubtitle =
          'Hồ sơ trước đây chưa hoàn tất xác thực tự động. Vui lòng chụp và xác minh lại qua VNPT eKYC.';
    } else if (item.isProcessing) {
      onboardingSubtitle =
          'Hồ sơ đang được hệ thống VNPT eKYC xử lý. Vui lòng kiểm tra lại sau giây lát.';
    } else if (item.isTechnicalFailure) {
      onboardingSubtitle =
          'Kết nối tới VNPT eKYC đang tạm thời gián đoạn. Hồ sơ của bạn chưa bị từ chối, hãy thử lại.';
    } else {
      onboardingSubtitle =
          'Xác minh danh tính chưa thành công, vui lòng kiểm tra lý do và chụp lại ảnh rõ nét.';
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _buildTopHeader(context, isResultView: true),
        if (widget.isOnboarding) ...[
          const SizedBox(height: 10),
          const RegistrationStepper(currentStep: 3),
          const SizedBox(height: 12),
          Text(
            onboardingSubtitle,
            textAlign: TextAlign.center,
            style: const TextStyle(
              fontSize: 13,
              fontWeight: FontWeight.w600,
              color: Color(0xFF64748B),
            ),
          ),
        ],
        const SizedBox(height: 16),
        EkycStatusHeader(item: item),
        const SizedBox(height: 16),
        EkycResultCard(item: item),
        const SizedBox(height: 24),
        _buildResultActions(state, item, notifier),
        const SizedBox(height: 24),
      ],
    );
  }

  Widget _buildResultActions(
    EkycState state,
    EkycVerificationItem item,
    EkycNotifier notifier,
  ) {
    if (item.isApproved) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Container(
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
            child: ElevatedButton.icon(
              onPressed: () {
                if (widget.isOnboarding) {
                  _navigateToHome();
                } else {
                  Navigator.of(context).maybePop();
                }
              },
              icon: const Icon(
                Icons.rocket_launch,
                color: Colors.white,
                size: 20,
              ),
              label: Text(
                widget.isOnboarding
                    ? 'Bắt đầu nhận việc ngay'
                    : 'Hoàn tất & Về trang chủ',
                style: const TextStyle(
                  fontSize: 16,
                  fontWeight: FontWeight.w700,
                  color: Colors.white,
                ),
              ),
              style: ElevatedButton.styleFrom(
                backgroundColor: Colors.transparent,
                shadowColor: Colors.transparent,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
              ),
            ),
          ),
        ],
      );
    }

    if (item.isProcessing) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          ElevatedButton.icon(
            onPressed: state.isLoading ? null : () => notifier.checkStatus(),
            icon: const Icon(Icons.refresh, color: Colors.white, size: 20),
            label: const Text(
              'Kiểm tra lại trạng thái',
              style: TextStyle(
                fontSize: 16,
                fontWeight: FontWeight.w700,
                color: Colors.white,
              ),
            ),
            style: ElevatedButton.styleFrom(
              backgroundColor: const Color(0xFF0077FF),
              minimumSize: const Size.fromHeight(52),
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(12),
              ),
            ),
          ),
          const SizedBox(height: 12),
          OutlinedButton(
            onPressed: () {
              if (widget.isOnboarding) {
                _navigateToHome();
              } else {
                Navigator.of(context).maybePop();
              }
            },
            style: OutlinedButton.styleFrom(
              padding: const EdgeInsets.symmetric(vertical: 14),
              side: const BorderSide(color: Color(0xFFE2E8F0), width: 1.5),
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(12),
              ),
            ),
            child: const Text(
              'Về trang chủ',
              style: TextStyle(
                fontWeight: FontWeight.w600,
                fontSize: 14,
                color: Color(0xFF64748B),
              ),
            ),
          ),
        ],
      );
    }

    if (item.isPendingReview) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Container(
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
            child: ElevatedButton.icon(
              onPressed: () {
                if (widget.isOnboarding) {
                  _navigateToHome();
                } else {
                  Navigator.of(context).maybePop();
                }
              },
              icon: const Icon(
                Icons.check_circle_outline,
                color: Colors.white,
                size: 20,
              ),
              label: const Text(
                'Tôi đã hiểu & Về trang chủ',
                style: TextStyle(
                  fontSize: 16,
                  fontWeight: FontWeight.w700,
                  color: Colors.white,
                ),
              ),
              style: ElevatedButton.styleFrom(
                backgroundColor: Colors.transparent,
                shadowColor: Colors.transparent,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
              ),
            ),
          ),
          const SizedBox(height: 12),
          OutlinedButton(
            onPressed: () => notifier.clearLocalForm(),
            style: OutlinedButton.styleFrom(
              padding: const EdgeInsets.symmetric(vertical: 14),
              side: const BorderSide(color: Color(0xFFE2E8F0), width: 1.5),
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(12),
              ),
            ),
            child: const Text(
              'Chụp lại hồ sơ mới',
              style: TextStyle(
                fontWeight: FontWeight.w600,
                fontSize: 14,
                color: Color(0xFF64748B),
              ),
            ),
          ),
        ],
      );
    }

    if (item.isTechnicalFailure) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Container(
            height: 52,
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(12),
              gradient: const LinearGradient(
                colors: [Color(0xFFF97316), Color(0xFFEA580C)],
                begin: Alignment.centerLeft,
                end: Alignment.centerRight,
              ),
              boxShadow: [
                BoxShadow(
                  color: const Color(0xFFEA580C).withValues(alpha: 0.32),
                  blurRadius: 14,
                  offset: const Offset(0, 5),
                ),
              ],
            ),
            child: ElevatedButton.icon(
              onPressed: state.isLoading
                  ? null
                  : () async {
                      if (state.hasAllImages) {
                        await notifier.retryVerification();
                      } else {
                        notifier.clearLocalResultOnly();
                      }
                    },
              icon: state.isLoading
                  ? const SizedBox(
                      width: 18,
                      height: 18,
                      child: CircularProgressIndicator(
                        strokeWidth: 2.2,
                        color: Colors.white,
                      ),
                    )
                  : const Icon(Icons.refresh, color: Colors.white, size: 20),
              label: Text(
                state.isLoading
                    ? 'Đang thử lại xác minh...'
                    : (state.hasAllImages
                        ? 'Thử lại xác minh ngay'
                        : 'Chọn ảnh và thử lại'),
                style: const TextStyle(
                  fontSize: 16,
                  fontWeight: FontWeight.w700,
                  color: Colors.white,
                ),
              ),
              style: ElevatedButton.styleFrom(
                backgroundColor: Colors.transparent,
                shadowColor: Colors.transparent,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
              ),
            ),
          ),
          const SizedBox(height: 12),
          OutlinedButton(
            onPressed: () => notifier.clearLocalForm(),
            style: OutlinedButton.styleFrom(
              padding: const EdgeInsets.symmetric(vertical: 14),
              side: const BorderSide(color: Color(0xFFE2E8F0), width: 1.5),
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(12),
              ),
            ),
            child: const Text(
              'Chụp lại ảnh khác',
              style: TextStyle(
                fontWeight: FontWeight.w600,
                fontSize: 14,
                color: Color(0xFF64748B),
              ),
            ),
          ),
        ],
      );
    }

    // Rejected
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Container(
          height: 52,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(12),
            gradient: const LinearGradient(
              colors: [Color(0xFFEF4444), Color(0xFFDC2626)],
              begin: Alignment.centerLeft,
              end: Alignment.centerRight,
            ),
            boxShadow: [
              BoxShadow(
                color: const Color(0xFFEF4444).withValues(alpha: 0.35),
                blurRadius: 14,
                offset: const Offset(0, 5),
              ),
            ],
          ),
          child: ElevatedButton.icon(
            onPressed: () => notifier.clearLocalForm(),
            icon: const Icon(Icons.refresh, color: Colors.white, size: 20),
            label: const Text(
              'Thực hiện lại định danh',
              style: TextStyle(
                fontSize: 16,
                fontWeight: FontWeight.w700,
                color: Colors.white,
              ),
            ),
            style: ElevatedButton.styleFrom(
              backgroundColor: Colors.transparent,
              shadowColor: Colors.transparent,
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(12),
              ),
            ),
          ),
        ),
        const SizedBox(height: 12),
        OutlinedButton(
          onPressed: () {
            if (widget.isOnboarding) {
              _navigateToHome();
            } else {
              Navigator.of(context).maybePop();
            }
          },
          style: OutlinedButton.styleFrom(
            padding: const EdgeInsets.symmetric(vertical: 14),
            side: const BorderSide(color: Color(0xFFE2E8F0), width: 1.5),
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(12),
            ),
          ),
          child: const Text(
            'Để sau (Về trang chủ)',
            style: TextStyle(
              fontWeight: FontWeight.w600,
              fontSize: 14,
              color: Color(0xFF64748B),
            ),
          ),
        ),
      ],
    );
  }

  //// Nút gửi hồ sơ thẩm định eKYC
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
                if (!mounted) return;
                final currentState = ref.read(ekycProvider);
                final res = currentState.result;
                if (success && res != null) {
                  final (msg, bgColor) = res.isApproved
                      ? (
                          'Xác thực định danh VNPT eKYC thành công! Đã cấp tích xanh.',
                          const Color(0xFF10B981),
                        )
                      : res.isPendingReview
                          ? (
                              'Hồ sơ chưa hoàn tất xác thực tự động qua VNPT eKYC. Vui lòng chụp lại và gửi lại.',
                              const Color(0xFFD97706),
                            )
                          : res.isProcessing
                              ? (
                                  'Hồ sơ eKYC đang được xử lý tại hệ thống VNPT eKYC.',
                                  const Color(0xFF0077FF),
                                )
                              : res.isTechnicalFailure
                                  ? (
                                      res.routingReason.isNotEmpty
                                          ? res.routingReason
                                          : 'Lỗi kỹ thuật tạm thời khi kết nối VNPT eKYC. Bạn có thể thử lại ngay.',
                                      const Color(0xFFEA580C),
                                    )
                                  : (
                                      res.routingReason.isNotEmpty
                                          ? res.routingReason
                                          : 'Xác minh danh tính chưa đạt yêu cầu. Vui lòng chụp lại ảnh rõ nét.',
                                      const Color(0xFFDC2626),
                                    );
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(content: Text(msg), backgroundColor: bgColor),
                  );
                } else {
                  final err = currentState.errorMessage ??
                      'Không thể kết nối máy chủ eKYC. Vui lòng thử lại.';
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(
                      content: Text(err),
                      backgroundColor: currentState.isTechnicalError
                          ? const Color(0xFFEA580C)
                          : const Color(0xFFDC2626),
                    ),
                  );
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
            ? const Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  SizedBox(
                    height: 20,
                    width: 20,
                    child: CircularProgressIndicator(
                      strokeWidth: 2.4,
                      color: Colors.white,
                    ),
                  ),
                  SizedBox(width: 10),
                  Text(
                    'Đang xác minh VNPT eKYC...',
                    style: TextStyle(
                      fontSize: 15.5,
                      fontWeight: FontWeight.w700,
                      color: Color(0xFF475569),
                    ),
                  ),
                ],
              )
            : Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(
                    state.isTechnicalError ? Icons.refresh : Icons.verified_user,
                    color: isReady ? Colors.white : const Color(0xFF94A3B8),
                    size: 20,
                  ),
                  const SizedBox(width: 8),
                  Text(
                    state.isTechnicalError
                        ? 'Thử Lại Gửi Hồ Sơ eKYC'
                        : 'Gửi Hồ Sơ Xác Thực eKYC',
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
