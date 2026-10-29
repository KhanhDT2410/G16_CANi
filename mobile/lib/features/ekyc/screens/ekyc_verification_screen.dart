import 'dart:typed_data';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/app/home_screen.dart';
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
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (ctx) {
        return SafeArea(
          child: Padding(
            padding: const EdgeInsets.symmetric(vertical: 20, horizontal: 16),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text(
                  title,
                  style: const TextStyle(
                      fontSize: 16, fontWeight: FontWeight.bold),
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 16),
                if (scanType == ScanType.cccdFront) ...[
                  ListTile(
                    leading: Container(
                      padding: const EdgeInsets.all(10),
                      decoration: BoxDecoration(
                        color: Colors.purple.withValues(alpha: 0.15),
                        shape: BoxShape.circle,
                      ),
                      child: const Icon(Icons.qr_code_scanner,
                          color: Colors.purple),
                    ),
                    title: const Text('Quét mã QR góc thẻ CCCD',
                        style: TextStyle(fontWeight: FontWeight.w600)),
                    subtitle: const Text(
                        'Đọc đúng 100% dữ liệu Bộ Công An (không dùng AI)'),
                    trailing: const Icon(Icons.arrow_forward_ios, size: 14),
                    onTap: () {
                      Navigator.of(ctx).pop();
                      _openQrScanner();
                    },
                  ),
                  const Divider(),
                ],
                ListTile(
                  leading: Container(
                    padding: const EdgeInsets.all(10),
                    decoration: BoxDecoration(
                      color: Colors.green.withValues(alpha: 0.15),
                      shape: BoxShape.circle,
                    ),
                    child: const Icon(Icons.camera_alt, color: Colors.green),
                  ),
                  title: const Text('Chụp trực tiếp bằng Camera',
                      style: TextStyle(fontWeight: FontWeight.w600)),
                  subtitle: const Text(
                      'Có khung căn chỉnh thẻ CCCD & tự động hướng dẫn'),
                  trailing: const Icon(Icons.arrow_forward_ios, size: 14),
                  onTap: () async {
                    Navigator.of(ctx).pop();
                    final result =
                        await Navigator.of(context).push<CardScanResult>(
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
                const Divider(),
                ListTile(
                  leading: Container(
                    padding: const EdgeInsets.all(10),
                    decoration: BoxDecoration(
                      color: Colors.blue.withValues(alpha: 0.15),
                      shape: BoxShape.circle,
                    ),
                    child: const Icon(Icons.photo_library, color: Colors.blue),
                  ),
                  title: const Text('Chọn ảnh từ Thư viện máy',
                      style: TextStyle(fontWeight: FontWeight.w600)),
                  subtitle:
                      const Text('Tải ảnh đã chụp sẵn trong bộ nhớ thiết bị'),
                  trailing: const Icon(Icons.arrow_forward_ios, size: 14),
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

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(ekycProvider);
    final notifier = ref.read(ekycProvider.notifier);

    final scaffold = Scaffold(
      appBar: AppBar(
        title: const Text('Định Danh Điện Tử (eKYC)'),
        elevation: 0,
        leading: widget.isOnboarding
            ? IconButton(
                icon: const Icon(Icons.close),
                tooltip: 'Hoàn thiện sau',
                onPressed: _navigateToHome,
              )
            : null,
        actions: [
          if (widget.isOnboarding)
            TextButton(
              onPressed: _navigateToHome,
              child: const Text(
                'Hoàn thiện sau',
                style: TextStyle(
                  fontWeight: FontWeight.w700,
                  color: Color(0xFF0077FF),
                ),
              ),
            ),
          IconButton(
            icon: const Icon(Icons.edit_note),
            tooltip: 'Xem & sửa thông tin CCCD',
            onPressed: _openConfirmDialog,
          ),
        ],
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
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
            FilledButton.icon(
              onPressed: state.isReadyToSubmit
                  ? () async {
                      final success = await notifier.submitVerification();
                      if (success && context.mounted) {
                        ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(
                            content: Text(
                                'Định danh eKYC thành công! Tài khoản đã được cấp tích xanh tự động.'),
                            backgroundColor: Colors.green,
                          ),
                        );
                        if (widget.isOnboarding) {
                          _navigateToHome();
                        }
                      }
                    }
                  : null,
              icon: state.isLoading
                  ? const SizedBox(
                      width: 20,
                      height: 20,
                      child: CircularProgressIndicator(
                          strokeWidth: 2, color: Colors.white),
                    )
                  : const Icon(Icons.verified_user),
              label: Text(
                state.isLoading
                    ? 'Đang gửi hồ sơ xác thực...'
                    : 'Gửi Hồ Sơ Xác Thực eKYC',
                style: const TextStyle(
                    fontSize: 16, fontWeight: FontWeight.bold),
              ),
              style: FilledButton.styleFrom(
                padding: const EdgeInsets.symmetric(vertical: 16),
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(12)),
              ),
            ),
            if (state.result != null) ...[
              const SizedBox(height: 24),
              EkycResultCard(item: state.result!),
            ],
          ],
        ),
      ),
    );

    if (widget.isOnboarding) {
      return PopScope(
        canPop: false,
        onPopInvokedWithResult: (didPop, _) {
          if (!didPop) {
            _navigateToHome();
          }
        },
        child: scaffold,
      );
    }

    return scaffold;
  }
}
