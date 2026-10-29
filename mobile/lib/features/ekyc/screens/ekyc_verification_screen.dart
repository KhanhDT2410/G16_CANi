import 'dart:typed_data';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/app/home_screen.dart';
import '../models/ekyc_models.dart';
import '../providers/ekyc_provider.dart';
import 'card_scanner_screen.dart';
import 'cccd_qr_scanner_screen.dart';
import 'cccd_confirm_dialog.dart';

class EkycVerificationScreen extends ConsumerStatefulWidget {
  final bool isOnboarding;

  const EkycVerificationScreen({
    super.key,
    this.isOnboarding = false,
  });

  @override
  ConsumerState<EkycVerificationScreen> createState() => _EkycVerificationScreenState();
}

class _EkycVerificationScreenState extends ConsumerState<EkycVerificationScreen> {

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
                  style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
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
                      child: const Icon(Icons.qr_code_scanner, color: Colors.purple),
                    ),
                    title: const Text('Quét mã QR góc thẻ CCCD', style: TextStyle(fontWeight: FontWeight.w600)),
                    subtitle: const Text('Đọc đúng 100% dữ liệu Bộ Công An (không dùng AI)'),
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
                  title: const Text('Chụp trực tiếp bằng Camera', style: TextStyle(fontWeight: FontWeight.w600)),
                  subtitle: const Text('Có khung căn chỉnh thẻ CCCD & tự động hướng dẫn'),
                  trailing: const Icon(Icons.arrow_forward_ios, size: 14),
                  onTap: () async {
                    Navigator.of(ctx).pop();
                    final result = await Navigator.of(context).push<CardScanResult>(
                      MaterialPageRoute(
                        builder: (_) => CardScannerScreen(scanType: scanType),
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
                  title: const Text('Chọn ảnh từ Thư viện máy', style: TextStyle(fontWeight: FontWeight.w600)),
                  subtitle: const Text('Tải ảnh đã chụp sẵn trong bộ nhớ thiết bị'),
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

    return Scaffold(
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
            // Status Header Banner
            _buildStatusHeader(context, state.result),
            const SizedBox(height: 16),

            // Banner Hướng dẫn quét QR & Chỉnh sửa thông tin
            _buildQrScanBanner(context, state),
            const SizedBox(height: 16),

            // Card hiển thị thông tin CCCD đã xác nhận (nếu có)
            if (state.hasConfirmedInfo) ...[
              _buildConfirmedCard(context, state),
              const SizedBox(height: 16),
            ],

            if (state.errorMessage != null)
              Container(
                margin: const EdgeInsets.only(bottom: 16),
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: Colors.red.withValues(alpha: 0.1),
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: Colors.red.withValues(alpha: 0.3)),
                ),
                child: Text(
                  state.errorMessage!,
                  style: const TextStyle(color: Colors.red, fontWeight: FontWeight.bold),
                ),
              ),

            // Step 1: Mặt trước CCCD
            _buildCaptureBox(
              context: context,
              step: '1',
              title: 'Mặt Trước Thẻ CCCD',
              subtitle: 'Chụp rõ 4 góc, rõ số thẻ và chân dung',
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

            // Step 2: Mặt sau CCCD
            _buildCaptureBox(
              context: context,
              step: '2',
              title: 'Mặt Sau Thẻ CCCD',
              subtitle: 'Chụp rõ đặc điểm nhân dạng và ngày cấp',
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

            // Step 3: Ảnh Selfie khuôn mặt
            _buildCaptureBox(
              context: context,
              step: '3',
              title: 'Ảnh Chân Dung Selfie',
              subtitle: 'Chụp chính diện, không đeo khẩu trang/kính râm',
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

            // Submit Button
            FilledButton.icon(
              onPressed: state.isReadyToSubmit
                  ? () async {
                      final success = await notifier.submitVerification();
                      if (success && context.mounted) {
                        ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(
                            content: Text('Đã gửi hồ sơ eKYC thẩm định thành công!'),
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
                      child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                    )
                  : const Icon(Icons.verified_user),
              label: Text(
                state.isLoading ? 'Đang gửi hồ sơ xác thực...' : 'Gửi Hồ Sơ Xác Thực eKYC',
                style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
              ),
              style: FilledButton.styleFrom(
                padding: const EdgeInsets.symmetric(vertical: 16),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
              ),
            ),

            // Verification Result Card
            if (state.result != null) ...[
              const SizedBox(height: 24),
              _buildResultCard(context, state.result!),
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildStatusHeader(BuildContext context, EkycVerificationItem? item) {
    final theme = Theme.of(context);
    final isApproved = item != null && item.isApproved;
    final isPending = item != null && item.isPending;

    final bgColor = isApproved
        ? Colors.green.withValues(alpha: 0.1)
        : (isPending ? Colors.orange.withValues(alpha: 0.1) : theme.colorScheme.surfaceContainerHighest);
    final borderColor = isApproved
        ? Colors.green
        : (isPending ? Colors.orange : theme.colorScheme.outlineVariant);
    final iconColor = isApproved
        ? Colors.green
        : (isPending ? Colors.orange : Colors.grey);

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: bgColor,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: borderColor, width: 1.5),
      ),
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: iconColor.withValues(alpha: 0.15),
              shape: BoxShape.circle,
            ),
            child: Icon(
              isApproved
                  ? Icons.check_circle
                  : (isPending ? Icons.pending : Icons.fingerprint),
              color: iconColor,
              size: 32,
            ),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  isApproved
                      ? 'ĐÃ ĐỊNH DANH eKYC (TÍCH XANH)'
                      : (isPending ? 'ĐANG CHỜ THẨM ĐỊNH' : 'CHƯA ĐỊNH DANH eKYC'),
                  style: TextStyle(
                    fontWeight: FontWeight.bold,
                    fontSize: 14,
                    color: iconColor,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  isApproved
                      ? 'Tài khoản của bạn đã được đối soát CCCD hợp lệ. Bạn được ưu tiên nhận việc.'
                      : (isPending
                          ? 'Hồ sơ đang được hệ thống thẩm định.'
                          : 'Vui lòng hoàn thành 3 bước bên dưới để xác thực danh tính.'),
                  style: TextStyle(
                    fontSize: 12,
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildCaptureBox({
    required BuildContext context,
    required String step,
    required String title,
    required String subtitle,
    required IconData icon,
    required Uint8List? bytes,
    required String? fileName,
    required VoidCallback onTap,
  }) {
    final theme = Theme.of(context);
    final hasImage = bytes != null;

    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(12),
      child: Container(
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: theme.colorScheme.surface,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(
            color: hasImage ? Colors.green : theme.colorScheme.outlineVariant,
            width: hasImage ? 2 : 1,
          ),
        ),
        child: Row(
          children: [
            // Preview or Step Icon
            ClipRRect(
              borderRadius: BorderRadius.circular(8),
              child: hasImage
                  ? Image.memory(
                      bytes,
                      width: 60,
                      height: 60,
                      fit: BoxFit.cover,
                    )
                  : Container(
                      width: 60,
                      height: 60,
                      color: theme.colorScheme.primaryContainer.withValues(alpha: 0.5),
                      child: Icon(icon, color: theme.colorScheme.primary, size: 28),
                    ),
            ),
            const SizedBox(width: 14),
            // Texts
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                        decoration: BoxDecoration(
                          color: theme.colorScheme.primary,
                          borderRadius: BorderRadius.circular(4),
                        ),
                        child: Text(
                          'Bước $step',
                          style: const TextStyle(
                            color: Colors.white,
                            fontSize: 10,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          title,
                          style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 4),
                  Text(
                    hasImage ? 'Đã chọn: $fileName' : subtitle,
                    style: TextStyle(
                      fontSize: 12,
                      color: hasImage ? Colors.green : theme.colorScheme.onSurfaceVariant,
                      fontWeight: hasImage ? FontWeight.bold : FontWeight.normal,
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                ],
              ),
            ),
            // Action Icon
            Icon(
              hasImage ? Icons.check_circle : Icons.camera_alt,
              color: hasImage ? Colors.green : theme.colorScheme.primary,
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildResultCard(BuildContext context, EkycVerificationItem item) {
    final theme = Theme.of(context);

    return Card(
      elevation: 2,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Icon(Icons.analytics_outlined, color: Colors.blueAccent),
                const SizedBox(width: 8),
                const Text(
                  'Chi Tiết Xác Thực eKYC',
                  style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
                ),
                const Spacer(),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                  decoration: BoxDecoration(
                    color: item.isApproved ? Colors.green : Colors.orange,
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Text(
                    item.status,
                    style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 12),
                  ),
                ),
              ],
            ),
            const Divider(height: 24),

            // Scores
            Row(
              children: [
                Expanded(
                  child: _buildMetricTile(
                    'Độ khớp khuôn mặt',
                    '${item.faceMatchScore.toStringAsFixed(1)}%',
                    item.faceMatchScore >= 80 ? Colors.green : Colors.orange,
                  ),
                ),
                Expanded(
                  child: _buildMetricTile(
                    'Độ tin cậy OCR',
                    '${(item.ocrConfidence * 100).toStringAsFixed(1)}%',
                    item.ocrConfidence >= 0.85 ? Colors.green : Colors.blue,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),

            // Extracted Information
            _buildInfoRow('Họ và tên:', item.fullName),
            _buildInfoRow('Số CCCD:', item.cccdNumberMasked),
            if (item.dob != null) _buildInfoRow('Ngày sinh:', item.dob!),
            if (item.residenceAddress != null) _buildInfoRow('Nơi thường trú:', item.residenceAddress!),
            if (item.expiryDate != null) _buildInfoRow('Hạn sử dụng:', item.expiryDate!),
            const SizedBox(height: 12),

            // Reason
            Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: theme.colorScheme.surfaceContainerHighest,
                borderRadius: BorderRadius.circular(8),
              ),
              child: Row(
                children: [
                  const Icon(Icons.info_outline, size: 18, color: Colors.blueGrey),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      item.routingReason,
                      style: const TextStyle(fontSize: 12),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildMetricTile(String label, String value, Color color) {
    return Container(
      padding: const EdgeInsets.all(8),
      margin: const EdgeInsets.symmetric(horizontal: 4),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.1),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Column(
        children: [
          Text(value, style: TextStyle(fontWeight: FontWeight.bold, fontSize: 18, color: color)),
          const SizedBox(height: 2),
          Text(label, style: const TextStyle(fontSize: 11, color: Colors.grey), textAlign: TextAlign.center),
        ],
      ),
    );
  }

  Widget _buildInfoRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4.0),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 120,
            child: Text(label, style: const TextStyle(color: Colors.grey, fontSize: 13)),
          ),
          Expanded(
            child: Text(value, style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 13)),
          ),
        ],
      ),
    );
  }

  Widget _buildQrScanBanner(BuildContext context, EkycState state) {
    final theme = Theme.of(context);

    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: theme.colorScheme.primaryContainer.withValues(alpha: 0.35),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: theme.colorScheme.primary.withValues(alpha: 0.3)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              Container(
                padding: const EdgeInsets.all(8),
                decoration: BoxDecoration(
                  color: theme.colorScheme.primary,
                  borderRadius: BorderRadius.circular(8),
                ),
                child: const Icon(Icons.qr_code_scanner, color: Colors.white, size: 20),
              ),
              const SizedBox(width: 12),
              const Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Bóc Tách Thông Tin Thẻ Chuẩn 100%',
                      style: TextStyle(fontWeight: FontWeight.bold, fontSize: 14),
                    ),
                    SizedBox(height: 2),
                    Text(
                      'Quét QR góc thẻ hoặc tự chỉnh sửa thông tin (Không dùng AI)',
                      style: TextStyle(fontSize: 11, color: Colors.grey),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              Expanded(
                child: FilledButton.tonalIcon(
                  onPressed: _openQrScanner,
                  icon: const Icon(Icons.qr_code, size: 18),
                  label: const Text('Quét Mã QR CCCD', style: TextStyle(fontSize: 13, fontWeight: FontWeight.bold)),
                  style: FilledButton.styleFrom(
                    padding: const EdgeInsets.symmetric(vertical: 10),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                  ),
                ),
              ),
              const SizedBox(width: 8),
              OutlinedButton.icon(
                onPressed: _openConfirmDialog,
                icon: const Icon(Icons.edit, size: 16),
                label: const Text('Sửa Thông Tin', style: TextStyle(fontSize: 13)),
                style: OutlinedButton.styleFrom(
                  padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 12),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildConfirmedCard(BuildContext context, EkycState state) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Colors.green.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: Colors.green.withValues(alpha: 0.5), width: 1.5),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Icon(Icons.verified, color: Colors.green, size: 20),
              const SizedBox(width: 8),
              const Expanded(
                child: Text(
                  'Thông Tin CCCD Đã Xác Nhận',
                  style: TextStyle(fontWeight: FontWeight.bold, color: Colors.green, fontSize: 14),
                ),
              ),
              InkWell(
                onTap: _openConfirmDialog,
                borderRadius: BorderRadius.circular(6),
                child: const Padding(
                  padding: EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(Icons.edit_outlined, size: 14, color: Colors.green),
                      SizedBox(width: 4),
                      Text('Sửa', style: TextStyle(fontSize: 12, color: Colors.green, fontWeight: FontWeight.bold)),
                    ],
                  ),
                ),
              ),
            ],
          ),
          const Divider(height: 16),
          if (state.confirmedCccd != null)
            _buildInfoRow('Số CCCD:', state.confirmedCccd!),
          if (state.confirmedFullName != null)
            _buildInfoRow('Họ và tên:', state.confirmedFullName!),
          if (state.confirmedDob != null)
            _buildInfoRow('Ngày sinh:', state.confirmedDob!),
          if (state.confirmedGender != null)
            _buildInfoRow('Giới tính:', state.confirmedGender!),
          if (state.confirmedResidenceAddress != null)
            _buildInfoRow('Thường trú:', state.confirmedResidenceAddress!),
          if (state.confirmedIssueDate != null && state.confirmedIssueDate!.isNotEmpty)
            _buildInfoRow('Ngày cấp:', state.confirmedIssueDate!),
        ],
      ),
    );
  }
}

