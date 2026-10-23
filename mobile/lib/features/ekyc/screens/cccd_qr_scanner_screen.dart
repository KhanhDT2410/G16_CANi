import 'package:flutter/material.dart';
import 'package:mobile_scanner/mobile_scanner.dart';

class CccdCardInfo {
  final String cccdNumber;
  final String? oldIdNumber;
  final String fullName;
  final String dob;
  final String gender;
  final String residenceAddress;
  final String issueDate;

  CccdCardInfo({
    required this.cccdNumber,
    this.oldIdNumber,
    required this.fullName,
    required this.dob,
    required this.gender,
    required this.residenceAddress,
    required this.issueDate,
  });

  static CccdCardInfo? fromQrString(String raw) {
    // Định dạng mã QR trên CCCD gắn chip (Bộ Công An ban hành):
    // Số_CCCD|Số_CMND_cũ|Họ_và_tên|Ngày_sinh(ddMMyyyy)|Giới_tính|Địa_chỉ_thường_trú|Ngày_cấp(ddMMyyyy)
    final parts = raw.split('|');
    if (parts.length >= 6) {
      String formatDob(String d) {
        if (d.length == 8) {
          return '${d.substring(0, 2)}/${d.substring(2, 4)}/${d.substring(4)}';
        }
        return d;
      }

      return CccdCardInfo(
        cccdNumber: parts[0].trim(),
        oldIdNumber: parts[1].trim().isNotEmpty ? parts[1].trim() : null,
        fullName: parts[2].trim().toUpperCase(),
        dob: formatDob(parts[3].trim()),
        gender: parts[4].trim(),
        residenceAddress: parts[5].trim(),
        issueDate: parts.length > 6 ? formatDob(parts[6].trim()) : '',
      );
    }
    return null;
  }
}

class CccdQrScannerScreen extends StatefulWidget {
  const CccdQrScannerScreen({super.key});

  @override
  State<CccdQrScannerScreen> createState() => _CccdQrScannerScreenState();
}

class _CccdQrScannerScreenState extends State<CccdQrScannerScreen> {
  final MobileScannerController _controller = MobileScannerController(
    detectionSpeed: DetectionSpeed.noDuplicates,
    formats: const [BarcodeFormat.qrCode],
  );

  bool _isProcessing = false;
  bool _isTorchOn = false;

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _onDetect(BarcodeCapture capture) {
    if (_isProcessing) return;

    for (final barcode in capture.barcodes) {
      final rawValue = barcode.rawValue;
      if (rawValue != null && rawValue.isNotEmpty) {
        final info = CccdCardInfo.fromQrString(rawValue);
        if (info != null) {
          setState(() {
            _isProcessing = true;
          });
          _controller.stop();
          Navigator.of(context).pop(info);
          return;
        }
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        foregroundColor: Colors.white,
        title: const Text('Quét Mã QR Trên CCCD', style: TextStyle(fontWeight: FontWeight.bold)),
        actions: [
          IconButton(
            icon: Icon(
              _isTorchOn ? Icons.flash_on : Icons.flash_off,
              color: _isTorchOn ? Colors.amber : Colors.white,
            ),
            onPressed: () async {
              try {
                await _controller.toggleTorch();
                setState(() => _isTorchOn = !_isTorchOn);
              } catch (_) {}
            },
          ),
          IconButton(
            icon: const Icon(Icons.flip_camera_ios, color: Colors.white),
            onPressed: () => _controller.switchCamera(),
          ),
        ],
      ),
      body: Stack(
        children: [
          // Scanner Camera View
          MobileScanner(
            controller: _controller,
            onDetect: _onDetect,
          ),

          // Dark Overlay with Target Cutout
          ColorFiltered(
            colorFilter: ColorFilter.mode(
              Colors.black.withValues(alpha: 0.65),
              BlendMode.srcOut,
            ),
            child: Stack(
              fit: StackFit.expand,
              children: [
                Container(
                  decoration: const BoxDecoration(
                    color: Colors.black,
                    backgroundBlendMode: BlendMode.dstOut,
                  ),
                ),
                Center(
                  child: Container(
                    width: 260,
                    height: 260,
                    decoration: BoxDecoration(
                      color: Colors.white,
                      borderRadius: BorderRadius.circular(16),
                    ),
                  ),
                ),
              ],
            ),
          ),

          // Target Box Frame
          Center(
            child: Container(
              width: 260,
              height: 260,
              decoration: BoxDecoration(
                border: Border.all(color: Colors.greenAccent, width: 2.5),
                borderRadius: BorderRadius.circular(16),
              ),
              child: Stack(
                children: [
                  // Corner indicators
                  _buildCorner(Alignment.topLeft),
                  _buildCorner(Alignment.topRight),
                  _buildCorner(Alignment.bottomLeft),
                  _buildCorner(Alignment.bottomRight),
                ],
              ),
            ),
          ),

          // Instructions at Bottom
          Positioned(
            bottom: 40,
            left: 20,
            right: 20,
            child: Container(
              padding: const EdgeInsets.symmetric(vertical: 14, horizontal: 16),
              decoration: BoxDecoration(
                color: Colors.black87,
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: Colors.white24),
              ),
              child: const Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Icon(Icons.qr_code_scanner, color: Colors.greenAccent, size: 20),
                      SizedBox(width: 8),
                      Text(
                        'Hướng camera vào mã QR góc phải CCCD',
                        style: TextStyle(
                          color: Colors.white,
                          fontSize: 14,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ],
                  ),
                  SizedBox(height: 6),
                  Text(
                    'Mã QR chứa dữ liệu chuẩn của Bộ Công An, bóc tách chính xác 100% không dùng AI.',
                    style: TextStyle(color: Colors.white70, fontSize: 11),
                    textAlign: TextAlign.center,
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildCorner(Alignment alignment) {
    return Align(
      alignment: alignment,
      child: Container(
        width: 24,
        height: 24,
        decoration: BoxDecoration(
          border: Border(
            top: alignment == Alignment.topLeft || alignment == Alignment.topRight
                ? const BorderSide(color: Colors.greenAccent, width: 4)
                : BorderSide.none,
            bottom: alignment == Alignment.bottomLeft || alignment == Alignment.bottomRight
                ? const BorderSide(color: Colors.greenAccent, width: 4)
                : BorderSide.none,
            left: alignment == Alignment.topLeft || alignment == Alignment.bottomLeft
                ? const BorderSide(color: Colors.greenAccent, width: 4)
                : BorderSide.none,
            right: alignment == Alignment.topRight || alignment == Alignment.bottomRight
                ? const BorderSide(color: Colors.greenAccent, width: 4)
                : BorderSide.none,
          ),
        ),
      ),
    );
  }
}
