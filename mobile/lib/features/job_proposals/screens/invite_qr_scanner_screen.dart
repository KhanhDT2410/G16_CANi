import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:mobile_scanner/mobile_scanner.dart';

import 'package:cani/core/utils/app_error_formatter.dart';
import '../data/proposal_repository.dart';

/// Chuẩn hóa mã mời từ chuỗi QR do Ban tổ chức phát hành.
/// Hỗ trợ các định dạng: `CANI_INVITE:CANI-XXXXXX`, `CANI-XXXXXX`, hoặc URL có query `code=...`.
/// Trả về `null` nếu mã QR thuộc luồng điểm danh (`CANI_CHECKIN:` / `CANI_CHECKOUT:`) hoặc không hợp lệ.
String? parseInviteCodeFromQr(String? rawValue) {
  if (rawValue == null) return null;
  final trimmed = rawValue.trim();
  if (trimmed.isEmpty) return null;

  final upper = trimmed.toUpperCase();
  if (upper.startsWith('CANI_CHECKIN:') || upper.startsWith('CANI_CHECKOUT:')) {
    return null;
  }

  if (upper.startsWith('CANI_INVITE:')) {
    final code = trimmed.substring('CANI_INVITE:'.length).trim();
    return code.isEmpty ? null : code.toUpperCase();
  }

  final uri = Uri.tryParse(trimmed);
  if (uri != null && uri.hasScheme && uri.queryParameters.containsKey('code')) {
    final code = uri.queryParameters['code']?.trim() ?? '';
    return code.isEmpty ? null : code.toUpperCase();
  }

  return upper;
}

class InviteQrScannerScreen extends ConsumerStatefulWidget {
  final VoidCallback? onManualEntryRequested;

  const InviteQrScannerScreen({
    super.key,
    this.onManualEntryRequested,
  });

  @override
  ConsumerState<InviteQrScannerScreen> createState() => _InviteQrScannerScreenState();
}

class _InviteQrScannerScreenState extends ConsumerState<InviteQrScannerScreen> {
  final MobileScannerController _controller = MobileScannerController(
    detectionSpeed: DetectionSpeed.noDuplicates,
    formats: const [BarcodeFormat.qrCode],
  );

  bool _isProcessing = false;
  bool _isFlashOn = false;

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  Future<void> _handleBarcodeDetected(BarcodeCapture capture) async {
    if (_isProcessing) return;
    final barcodes = capture.barcodes;
    if (barcodes.isEmpty) return;

    final rawValue = barcodes.first.rawValue?.trim();
    if (rawValue == null || rawValue.isEmpty) return;

    final upper = rawValue.toUpperCase();
    if (upper.startsWith('CANI_CHECKIN:') || upper.startsWith('CANI_CHECKOUT:')) {
      setState(() => _isProcessing = true);
      await _showRetryDialog(
        title: 'Sai Loại Mã QR',
        message: 'Đây là mã QR dùng để điểm danh Check-in/Check-out, không phải mã mời tham gia ca làm việc.',
      );
      return;
    }

    final code = parseInviteCodeFromQr(rawValue);
    if (code == null || code.isEmpty) {
      setState(() => _isProcessing = true);
      await _showRetryDialog(
        title: 'Mã QR Không Hợp Lệ',
        message: 'Không nhận diện được mã tham gia ca làm từ mã QR này. Vui lòng thử lại hoặc nhập mã thủ công.',
      );
      return;
    }

    setState(() => _isProcessing = true);
    try {
      await _controller.stop();
      await ref.read(proposalRepositoryProvider).joinViaInviteCode(code);
      if (!mounted) return;
      Navigator.of(context).pop(true);
    } catch (e) {
      if (!mounted) return;
      await _showRetryDialog(
        title: 'Tham Gia Ca Thất Bại',
        message: AppErrorFormatter.format(e, fallback: 'Không thể tham gia ca làm bằng mã mời này.'),
      );
    }
  }

  Future<void> _showRetryDialog({required String title, required String message}) async {
    if (!mounted) return;
    await showDialog<void>(
      context: context,
      barrierDismissible: false,
      builder: (ctx) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(18)),
        title: Text(title, style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 17)),
        content: Text(message, style: const TextStyle(fontSize: 14, height: 1.4)),
        actions: [
          FilledButton(
            onPressed: () {
              Navigator.of(ctx).pop();
              if (mounted) {
                setState(() => _isProcessing = false);
                _controller.start();
              }
            },
            child: const Text('Quét Lại'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        backgroundColor: Colors.black,
        foregroundColor: Colors.white,
        title: const Text(
          'Quét QR Tham Gia Ca',
          style: TextStyle(fontSize: 17, fontWeight: FontWeight.w700),
        ),
        actions: [
          IconButton(
            tooltip: _isFlashOn ? 'Tắt đèn pin' : 'Bật đèn pin',
            icon: Icon(_isFlashOn ? Icons.flash_on_rounded : Icons.flash_off_rounded),
            onPressed: () async {
              await _controller.toggleTorch();
              if (mounted) {
                setState(() => _isFlashOn = !_isFlashOn);
              }
            },
          ),
        ],
      ),
      body: Stack(
        children: [
          MobileScanner(
            controller: _controller,
            onDetect: _handleBarcodeDetected,
          ),
          Center(
            child: Container(
              width: 240,
              height: 240,
              decoration: BoxDecoration(
                border: Border.all(color: const Color(0xFF38BDF8), width: 3),
                borderRadius: BorderRadius.circular(20),
              ),
            ),
          ),
          Positioned(
            left: 24,
            right: 24,
            bottom: 40,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
                  decoration: BoxDecoration(
                    color: Colors.black.withValues(alpha: 0.72),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: const Text(
                    'Đưa mã QR mời tham gia ca của Ban tổ chức vào khung',
                    textAlign: TextAlign.center,
                    style: TextStyle(color: Colors.white, fontSize: 13.5, fontWeight: FontWeight.w500),
                  ),
                ),
                if (widget.onManualEntryRequested != null) ...[
                  const SizedBox(height: 12),
                  TextButton.icon(
                    onPressed: _isProcessing
                        ? null
                        : () {
                            Navigator.of(context).pop(false);
                            widget.onManualEntryRequested?.call();
                          },
                    icon: const Icon(Icons.keyboard_alt_outlined, color: Colors.white),
                    label: const Text(
                      'Nhập mã thủ công',
                      style: TextStyle(color: Colors.white, fontWeight: FontWeight.w600),
                    ),
                    style: TextButton.styleFrom(
                      backgroundColor: Colors.white.withValues(alpha: 0.16),
                      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(999)),
                    ),
                  ),
                ],
              ],
            ),
          ),
          if (_isProcessing)
            Container(
              color: Colors.black.withValues(alpha: 0.55),
              child: const Center(
                child: CircularProgressIndicator(color: Colors.white),
              ),
            ),
        ],
      ),
    );
  }
}
