import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:geolocator/geolocator.dart';
import 'package:mobile_scanner/mobile_scanner.dart';

import '../data/proposal_repository.dart';
import '../models/proposal_models.dart';

class AttendanceQrScannerScreen extends ConsumerStatefulWidget {
  final ShiftAssignmentItem assignment;
  final bool isCheckin; // true: Check-in, false: Check-out

  const AttendanceQrScannerScreen({
    super.key,
    required this.assignment,
    required this.isCheckin,
  });

  @override
  ConsumerState<AttendanceQrScannerScreen> createState() => _AttendanceQrScannerScreenState();
}

class _AttendanceQrScannerScreenState extends ConsumerState<AttendanceQrScannerScreen> {
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

    final rawValue = barcodes.first.rawValue;
    if (rawValue == null || rawValue.trim().isEmpty) return;

    setState(() {
      _isProcessing = true;
    });

    try {
      // 1. Tạm dừng scanner
      await _controller.stop();

      // 2. Lấy tọa độ GPS thực tế của thiết bị
      double? lat;
      double? lng;
      try {
        final pos = await Geolocator.getCurrentPosition(
          locationSettings: const LocationSettings(
            accuracy: LocationAccuracy.high,
            timeLimit: Duration(seconds: 8),
          ),
        );
        lat = pos.latitude;
        lng = pos.longitude;
      } catch (_) {
        // Fallback nếu không kịp bắt GPS
      }

      // 3. Gửi API đối soát điểm danh
      final repo = ref.read(proposalRepositoryProvider);
      AttendanceCheckResult result;
      if (widget.isCheckin) {
        result = await repo.checkin(
          assignmentId: widget.assignment.assignmentId,
          qrContent: rawValue.trim(),
          lat: lat,
          lng: lng,
        );
      } else {
        result = await repo.checkout(
          assignmentId: widget.assignment.assignmentId,
          qrContent: rawValue.trim(),
          lat: lat,
          lng: lng,
        );
      }

      if (!mounted) return;

      // 4. Hiển thị Dialog kết quả thành công
      await showDialog(
        context: context,
        barrierDismissible: false,
        builder: (ctx) => AlertDialog(
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
          title: Row(
            children: [
              Container(
                padding: const EdgeInsets.all(8),
                decoration: BoxDecoration(
                  color: widget.isCheckin ? Colors.green.shade50 : Colors.teal.shade50,
                  shape: BoxShape.circle,
                ),
                child: Icon(
                  widget.isCheckin ? Icons.check_circle : Icons.verified,
                  color: widget.isCheckin ? Colors.green : Colors.teal,
                  size: 28,
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Text(
                  widget.isCheckin ? 'Check-in Thành Công' : 'Check-out Thành Công',
                  style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 18),
                ),
              ),
            ],
          ),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                widget.assignment.eventName,
                style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 15),
              ),
              const SizedBox(height: 4),
              Text(
                widget.assignment.jobType,
                style: TextStyle(color: Colors.blue.shade800, fontWeight: FontWeight.w600, fontSize: 13),
              ),
              const Divider(height: 20),
              Text(result.message, style: const TextStyle(fontSize: 13, height: 1.4)),
              const SizedBox(height: 10),
              if (lat != null && lng != null)
                Row(
                  children: [
                    const Icon(Icons.gps_fixed, size: 14, color: Colors.grey),
                    const SizedBox(width: 4),
                    Text(
                      'Tọa độ điểm danh: ${lat.toStringAsFixed(4)}, ${lng.toStringAsFixed(4)}',
                      style: const TextStyle(fontSize: 11, color: Colors.grey),
                    ),
                  ],
                ),
              if (!widget.isCheckin && result.hoursWorked != null) ...[
                const SizedBox(height: 8),
                Container(
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(
                    color: Colors.amber.shade50,
                    borderRadius: BorderRadius.circular(10),
                    border: Border.all(color: Colors.amber.shade300),
                  ),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      const Text('Tổng số giờ làm việc:', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 12)),
                      Text(
                        '${result.hoursWorked!.toStringAsFixed(1)} giờ',
                        style: TextStyle(fontWeight: FontWeight.bold, color: Colors.amber.shade900, fontSize: 14),
                      ),
                    ],
                  ),
                ),
              ],
            ],
          ),
          actions: [
            FilledButton(
              onPressed: () {
                Navigator.pop(ctx);
                Navigator.pop(context, true); // Trả về true để refresh
              },
              child: const Text('Xác nhận & Hoàn tất'),
            ),
          ],
        ),
      );
    } catch (e) {
      if (!mounted) return;
      await showDialog(
        context: context,
        builder: (ctx) => AlertDialog(
          title: const Row(
            children: [
              Icon(Icons.error_outline, color: Colors.red),
              SizedBox(width: 8),
              Text('Lỗi điểm danh'),
            ],
          ),
          content: Text('Không thể xác thực điểm danh: $e'),
          actions: [
            TextButton(
              onPressed: () {
                Navigator.pop(ctx);
                setState(() {
                  _isProcessing = false;
                });
                _controller.start();
              },
              child: const Text('Thử lại'),
            ),
          ],
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final title = widget.isCheckin ? 'Quét QR Check-in Vào Ca' : 'Quét QR Check-out Kết Ca';

    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        backgroundColor: Colors.black,
        foregroundColor: Colors.white,
        title: Text(title, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
        actions: [
          IconButton(
            icon: Icon(_isFlashOn ? Icons.flash_on : Icons.flash_off),
            onPressed: () async {
              await _controller.toggleTorch();
              setState(() {
                _isFlashOn = !_isFlashOn;
              });
            },
          ),
          IconButton(
            icon: const Icon(Icons.flip_camera_ios),
            onPressed: () => _controller.switchCamera(),
          ),
        ],
      ),
      body: Stack(
        children: [
          // 1. Camera View Scanner
          MobileScanner(
            controller: _controller,
            onDetect: _handleBarcodeDetected,
          ),

          // 2. Scanner Overlay & Bounding Box
          Center(
            child: Container(
              width: 270,
              height: 270,
              decoration: BoxDecoration(
                border: Border.all(
                  color: widget.isCheckin ? Colors.greenAccent : Colors.amberAccent,
                  width: 3,
                ),
                borderRadius: BorderRadius.circular(20),
                boxShadow: [
                  BoxShadow(
                    color: (widget.isCheckin ? Colors.green : Colors.amber).withValues(alpha: 0.25),
                    blurRadius: 20,
                    spreadRadius: 4,
                  ),
                ],
              ),
              child: Stack(
                children: [
                  Positioned(
                    top: 12,
                    left: 12,
                    right: 12,
                    child: Container(
                      padding: const EdgeInsets.symmetric(vertical: 4, horizontal: 8),
                      decoration: BoxDecoration(
                        color: Colors.black54,
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: Text(
                        widget.isCheckin ? 'Đưa mã QR của Ban tổ chức vào ô' : 'Đưa mã QR kết thúc ca vào ô',
                        textAlign: TextAlign.center,
                        style: const TextStyle(color: Colors.white, fontSize: 11, fontWeight: FontWeight.bold),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),

          // 3. Thông tin ca làm việc ở phía dưới
          Positioned(
            left: 20,
            right: 20,
            bottom: 40,
            child: Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: Colors.black87,
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: Colors.white24),
              ),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                        decoration: BoxDecoration(
                          color: widget.isCheckin ? Colors.green : Colors.amber,
                          borderRadius: BorderRadius.circular(6),
                        ),
                        child: Text(
                          widget.isCheckin ? 'CHECK-IN' : 'CHECK-OUT',
                          style: const TextStyle(color: Colors.black, fontWeight: FontWeight.bold, fontSize: 11),
                        ),
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          widget.assignment.eventName,
                          style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 14),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 6),
                  Text(
                    'Ca: ${widget.assignment.jobType} | ${widget.assignment.venueAddress}',
                    style: const TextStyle(color: Colors.white70, fontSize: 12),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  if (_isProcessing) ...[
                    const SizedBox(height: 12),
                    const Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        SizedBox(width: 16, height: 16, child: CircularProgressIndicator(color: Colors.white, strokeWidth: 2)),
                        SizedBox(width: 10),
                        Text('Đang xác thực tọa độ GPS & ghi nhận ca...', style: TextStyle(color: Colors.white, fontSize: 12)),
                      ],
                    ),
                  ],
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
