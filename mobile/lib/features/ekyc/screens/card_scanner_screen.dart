import 'dart:io';
import 'dart:typed_data';
import 'package:camera/camera.dart';
import 'package:flutter/material.dart';

enum ScanType {
  cccdFront,
  cccdBack,
  selfie,
}

class CardScanResult {
  final Uint8List bytes;
  final String fileName;

  CardScanResult({required this.bytes, required this.fileName});
}

class CardScannerScreen extends StatefulWidget {
  final ScanType scanType;

  const CardScannerScreen({
    super.key,
    required this.scanType,
  });

  @override
  State<CardScannerScreen> createState() => _CardScannerScreenState();
}

class _CardScannerScreenState extends State<CardScannerScreen>
    with SingleTickerProviderStateMixin {
  CameraController? _controller;
  List<CameraDescription> _cameras = [];
  int _selectedCameraIndex = 0;
  bool _isCameraInitialized = false;
  bool _isFlashOn = false;
  bool _isCapturing = false;
  Uint8List? _capturedBytes;

  late AnimationController _animController;
  late Animation<double> _laserAnimation;

  @override
  void initState() {
    super.initState();
    _animController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 2000),
    )..repeat(reverse: true);

    _laserAnimation = Tween<double>(begin: 0.05, end: 0.95).animate(
      CurvedAnimation(parent: _animController, curve: Curves.easeInOut),
    );

    _initCamera();
  }

  Future<void> _initCamera() async {
    try {
      _cameras = await availableCameras();
      if (_cameras.isEmpty) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Không tìm thấy camera trên thiết bị')),
          );
        }
        return;
      }

      // Với Selfie thì ưu tiên camera trước, với CCCD thì ưu tiên camera sau
      final targetLens = widget.scanType == ScanType.selfie
          ? CameraLensDirection.front
          : CameraLensDirection.back;

      int initialIndex = _cameras.indexWhere((c) => c.lensDirection == targetLens);
      if (initialIndex == -1) initialIndex = 0;

      _selectedCameraIndex = initialIndex;
      await _setupCameraController(_cameras[_selectedCameraIndex]);
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Lỗi khởi động camera: $e')),
        );
      }
    }
  }

  Future<void> _setupCameraController(CameraDescription cameraDescription) async {
    if (_controller != null) {
      await _controller!.dispose();
    }

    _controller = CameraController(
      cameraDescription,
      ResolutionPreset.high,
      enableAudio: false,
      imageFormatGroup: ImageFormatGroup.jpeg,
    );

    try {
      await _controller!.initialize();
      if (mounted) {
        setState(() {
          _isCameraInitialized = true;
          _isFlashOn = false;
        });
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Không thể truy cập camera: $e')),
        );
      }
    }
  }

  Future<void> _toggleFlash() async {
    if (_controller == null || !_isCameraInitialized) return;
    try {
      final newFlash = !_isFlashOn;
      await _controller!.setFlashMode(newFlash ? FlashMode.torch : FlashMode.off);
      setState(() {
        _isFlashOn = newFlash;
      });
    } catch (_) {}
  }

  Future<void> _switchCamera() async {
    if (_cameras.length < 2) return;
    _selectedCameraIndex = (_selectedCameraIndex + 1) % _cameras.length;
    setState(() {
      _isCameraInitialized = false;
    });
    await _setupCameraController(_cameras[_selectedCameraIndex]);
  }

  Future<void> _capturePhoto() async {
    if (_controller == null || !_isCameraInitialized || _isCapturing) return;

    setState(() {
      _isCapturing = true;
    });

    try {
      final file = await _controller!.takePicture();
      final bytes = await File(file.path).readAsBytes();

      setState(() {
        _capturedBytes = bytes;
        _isCapturing = false;
      });
    } catch (e) {
      setState(() {
        _isCapturing = false;
      });
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Chụp ảnh thất bại: $e')),
        );
      }
    }
  }

  void _retake() {
    setState(() {
      _capturedBytes = null;
    });
  }

  void _confirmPhoto() {
    if (_capturedBytes == null) return;
    final prefix = widget.scanType == ScanType.cccdFront
        ? 'cccd_front'
        : (widget.scanType == ScanType.cccdBack ? 'cccd_back' : 'selfie');
    final fileName = '${prefix}_${DateTime.now().millisecondsSinceEpoch}.jpg';

    Navigator.of(context).pop(CardScanResult(
      bytes: _capturedBytes!,
      fileName: fileName,
    ));
  }

  @override
  void dispose() {
    _animController.dispose();
    _controller?.dispose();
    super.dispose();
  }

  String get _title {
    switch (widget.scanType) {
      case ScanType.cccdFront:
        return 'Chụp Mặt Trước CCCD';
      case ScanType.cccdBack:
        return 'Chụp Mặt Sau CCCD';
      case ScanType.selfie:
        return 'Chụp Chân Dung Khuôn Mặt';
    }
  }

  String get _guideText {
    switch (widget.scanType) {
      case ScanType.cccdFront:
        return 'Đưa mặt trước CCCD vào khung chữ nhật';
      case ScanType.cccdBack:
        return 'Đưa mặt sau CCCD vào khung chữ nhật';
      case ScanType.selfie:
        return 'Giữ khuôn mặt chính diện trong khung hình';
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      body: SafeArea(
        child: Stack(
          children: [
            // Camera Preview hoặc Preview ảnh đã chụp
            if (_capturedBytes != null)
              Positioned.fill(
                child: Image.memory(_capturedBytes!, fit: BoxFit.contain),
              )
            else if (_isCameraInitialized && _controller != null)
              Positioned.fill(
                child: AspectRatio(
                  aspectRatio: _controller!.value.aspectRatio,
                  child: CameraPreview(_controller!),
                ),
              )
            else
              const Center(
                child: CircularProgressIndicator(color: Colors.white),
              ),

            // Lớp Overlay hướng dẫn và khung căn chỉnh (chỉ hiện khi chưa chụp ảnh)
            if (_capturedBytes == null && _isCameraInitialized)
              Positioned.fill(
                child: LayoutBuilder(
                  builder: (context, constraints) {
                    final isSelfie = widget.scanType == ScanType.selfie;
                    final cardWidth = constraints.maxWidth * 0.88;
                    // Tỉ lệ thẻ ID-1 chuẩn ISO/IEC 7810: 85.6mm x 53.98mm (~1.58)
                    final cardHeight = isSelfie ? cardWidth * 1.25 : cardWidth / 1.58;
                    final left = (constraints.maxWidth - cardWidth) / 2;
                    final top = (constraints.maxHeight - cardHeight) / 2 - 20;
                    final cardRect = Rect.fromLTWH(left, top, cardWidth, cardHeight);

                    return Stack(
                      children: [
                        // Custom Painter vẽ lớp mờ xung quanh và 4 góc bo
                        CustomPaint(
                          size: Size(constraints.maxWidth, constraints.maxHeight),
                          painter: _CardOverlayPainter(
                            rect: cardRect,
                            isSelfie: isSelfie,
                          ),
                        ),

                        // Tia Laser quét lên xuống
                        AnimatedBuilder(
                          animation: _laserAnimation,
                          builder: (context, child) {
                            final laserTop = cardRect.top + cardRect.height * _laserAnimation.value;
                            return Positioned(
                              left: cardRect.left + 8,
                              top: laserTop,
                              width: cardRect.width - 16,
                              child: Container(
                                height: 2.5,
                                decoration: BoxDecoration(
                                  gradient: const LinearGradient(
                                    colors: [
                                      Colors.transparent,
                                      Color(0xFF00E676),
                                      Color(0xFF69F0AE),
                                      Color(0xFF00E676),
                                      Colors.transparent,
                                    ],
                                  ),
                                  boxShadow: [
                                    BoxShadow(
                                      color: const Color(0xFF00E676).withValues(alpha: 0.6),
                                      blurRadius: 8,
                                      spreadRadius: 2,
                                    ),
                                  ],
                                ),
                              ),
                            );
                          },
                        ),

                        // Hướng dẫn căn chỉnh phía trên khung
                        Positioned(
                          top: cardRect.top - 60,
                          left: 20,
                          right: 20,
                          child: Column(
                            children: [
                              Container(
                                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
                                decoration: BoxDecoration(
                                  color: Colors.black.withValues(alpha: 0.7),
                                  borderRadius: BorderRadius.circular(20),
                                  border: Border.all(color: Colors.white24),
                                ),
                                child: Text(
                                  _guideText,
                                  style: const TextStyle(
                                    color: Colors.white,
                                    fontSize: 13,
                                    fontWeight: FontWeight.w600,
                                  ),
                                  textAlign: TextAlign.center,
                                ),
                              ),
                            ],
                          ),
                        ),

                        // Hướng dẫn bổ sung phía dưới khung
                        Positioned(
                          top: cardRect.bottom + 16,
                          left: 24,
                          right: 24,
                          child: const Text(
                            'Giữ chắc tay, tránh chói sáng và không cắt góc',
                            style: TextStyle(
                              color: Colors.white70,
                              fontSize: 12,
                            ),
                            textAlign: TextAlign.center,
                          ),
                        ),
                      ],
                    );
                  },
                ),
              ),

            // Top Bar: Back button, Title, Flash, Switch Camera
            Positioned(
              top: 8,
              left: 8,
              right: 8,
              child: Row(
                children: [
                  IconButton(
                    icon: const Icon(Icons.arrow_back, color: Colors.white),
                    onPressed: () => Navigator.of(context).pop(),
                  ),
                  Expanded(
                    child: Text(
                      _title,
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 16,
                        fontWeight: FontWeight.bold,
                      ),
                      textAlign: TextAlign.center,
                    ),
                  ),
                  if (_capturedBytes == null) ...[
                    IconButton(
                      icon: Icon(
                        _isFlashOn ? Icons.flash_on : Icons.flash_off,
                        color: _isFlashOn ? Colors.amber : Colors.white,
                      ),
                      onPressed: _toggleFlash,
                    ),
                    IconButton(
                      icon: const Icon(Icons.flip_camera_ios, color: Colors.white),
                      onPressed: _switchCamera,
                    ),
                  ] else
                    const SizedBox(width: 48),
                ],
              ),
            ),

            // Bottom Actions: Nút chụp hoặc xác nhận ảnh
            Positioned(
              bottom: 24,
              left: 20,
              right: 20,
              child: _capturedBytes != null
                  ? Row(
                      mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                      children: [
                        OutlinedButton.icon(
                          onPressed: _retake,
                          icon: const Icon(Icons.refresh, color: Colors.white),
                          label: const Text('Chụp lại', style: TextStyle(color: Colors.white)),
                          style: OutlinedButton.styleFrom(
                            side: const BorderSide(color: Colors.white),
                            padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
                            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                          ),
                        ),
                        FilledButton.icon(
                          onPressed: _confirmPhoto,
                          icon: const Icon(Icons.check, color: Colors.white),
                          label: const Text('Dùng ảnh này', style: TextStyle(fontWeight: FontWeight.bold)),
                          style: FilledButton.styleFrom(
                            backgroundColor: const Color(0xFF00E676),
                            foregroundColor: Colors.black,
                            padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
                            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                          ),
                        ),
                      ],
                    )
                  : Center(
                      child: GestureDetector(
                        onTap: _isCapturing ? null : _capturePhoto,
                        child: Container(
                          width: 76,
                          height: 76,
                          padding: const EdgeInsets.all(4),
                          decoration: BoxDecoration(
                            shape: BoxShape.circle,
                            border: Border.all(color: Colors.white, width: 3.5),
                          ),
                          child: Container(
                            decoration: BoxDecoration(
                              shape: BoxShape.circle,
                              color: _isCapturing ? Colors.grey : const Color(0xFF00E676),
                            ),
                            child: _isCapturing
                                ? const Padding(
                                    padding: EdgeInsets.all(16.0),
                                    child: CircularProgressIndicator(color: Colors.white, strokeWidth: 2),
                                  )
                                : const Icon(Icons.camera_alt, color: Colors.black, size: 32),
                          ),
                        ),
                      ),
                    ),
            ),
          ],
        ),
      ),
    );
  }
}

class _CardOverlayPainter extends CustomPainter {
  final Rect rect;
  final bool isSelfie;

  _CardOverlayPainter({required this.rect, required this.isSelfie});

  @override
  void paint(Canvas canvas, Size size) {
    final backgroundPaint = Paint()
      ..color = Colors.black.withValues(alpha: 0.68)
      ..style = PaintingStyle.fill;

    final backgroundPath = Path()
      ..addRect(Rect.fromLTWH(0, 0, size.width, size.height));

    final rrect = RRect.fromRectAndRadius(
      rect,
      Radius.circular(isSelfie ? rect.width / 2 : 16),
    );
    final cardHolePath = Path()..addRRect(rrect);

    // Khoét lỗ trong suốt ở giữa
    final finalPath = Path.combine(PathOperation.difference, backgroundPath, cardHolePath);
    canvas.drawPath(finalPath, backgroundPaint);

    // Vẽ 4 góc định vị (Corner Guides) nổi bật
    final cornerPaint = Paint()
      ..color = const Color(0xFF00E676)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 4.0
      ..strokeCap = StrokeCap.round;

    const cornerLength = 28.0;
    const radius = 16.0;

    // Top-Left Corner
    final tlPath = Path()
      ..moveTo(rect.left, rect.top + cornerLength)
      ..lineTo(rect.left, rect.top + radius)
      ..arcToPoint(Offset(rect.left + radius, rect.top), radius: const Radius.circular(radius))
      ..lineTo(rect.left + cornerLength, rect.top);
    canvas.drawPath(tlPath, cornerPaint);

    // Top-Right Corner
    final trPath = Path()
      ..moveTo(rect.right - cornerLength, rect.top)
      ..lineTo(rect.right - radius, rect.top)
      ..arcToPoint(Offset(rect.right, rect.top + radius), radius: const Radius.circular(radius))
      ..lineTo(rect.right, rect.top + cornerLength);
    canvas.drawPath(trPath, cornerPaint);

    // Bottom-Left Corner
    final blPath = Path()
      ..moveTo(rect.left, rect.bottom - cornerLength)
      ..lineTo(rect.left, rect.bottom - radius)
      ..arcToPoint(Offset(rect.left + radius, rect.bottom), radius: const Radius.circular(radius))
      ..lineTo(rect.left + cornerLength, rect.bottom);
    canvas.drawPath(blPath, cornerPaint);

    // Bottom-Right Corner
    final brPath = Path()
      ..moveTo(rect.right - cornerLength, rect.bottom)
      ..lineTo(rect.right - radius, rect.bottom)
      ..arcToPoint(Offset(rect.right, rect.bottom - radius), radius: const Radius.circular(radius))
      ..lineTo(rect.right, rect.bottom - cornerLength);
    canvas.drawPath(brPath, cornerPaint);
  }

  @override
  bool shouldRepaint(covariant _CardOverlayPainter oldDelegate) {
    return oldDelegate.rect != rect || oldDelegate.isSelfie != isSelfie;
  }
}
