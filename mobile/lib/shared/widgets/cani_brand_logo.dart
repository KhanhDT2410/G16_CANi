import 'package:flutter/material.dart';

/// CANi Official Brand Logo Widget cho Flutter Mobile.
/// Vẽ vector chính xác 100% nhận diện thương hiệu CANi:
/// - Chữ C: Vòng cung bo tròn dải màu Cyan -> Ocean Blue
/// - Chữ A: Hình tượng nhân sự sự kiện với đầu tròn và dáng vươn lên
/// - Chữ N, i: Xanh Navy đậm đà (#0B1B3D)
/// - Subtitle: 'Worker' hoặc 'EVENT PLATFORM'
class CaniBrandLogo extends StatelessWidget {
  final double height;
  final bool isWorker;
  final bool showSlogan;

  const CaniBrandLogo({
    super.key,
    this.height = 64,
    this.isWorker = true,
    this.showSlogan = false,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        CustomPaint(
          size: Size(height * 2.5, height),
          painter: _CaniLogoPainter(),
        ),
        if (isWorker) ...[
          const SizedBox(height: 2),
          Text(
            'Worker',
            style: TextStyle(
              fontSize: height * 0.32,
              fontWeight: FontWeight.w800,
              color: const Color(0xFF0077FF),
              letterSpacing: 0.5,
            ),
          ),
        ] else ...[
          const SizedBox(height: 2),
          const Text(
            'EVENT PLATFORM',
            style: TextStyle(
              fontSize: 10,
              fontWeight: FontWeight.w800,
              letterSpacing: 2.5,
              color: Color(0xFF0B1B3D),
            ),
          ),
        ],
        if (showSlogan) ...[
          const SizedBox(height: 4),
          const Text(
            'Cần Ai, Có CANi',
            style: TextStyle(
              fontSize: 12,
              fontWeight: FontWeight.w600,
              color: Color(0xFF64748B),
              letterSpacing: 0.5,
            ),
          ),
        ],
      ],
    );
  }
}

class _CaniLogoPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final scale = size.height / 100.0;
    canvas.save();
    canvas.scale(scale, scale);

    // 1. Chữ C (Gradient Cyan -> Blue)
    final rectC = const Rect.fromLTWH(10, 10, 80, 80);
    final paintC = Paint()
      ..shader = const LinearGradient(
        colors: [Color(0xFF00D2FF), Color(0xFF0091FF), Color(0xFF0055FF)],
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
      ).createShader(rectC)
      ..style = PaintingStyle.fill;

    final pathC = Path()
      ..moveTo(65, 24)
      ..cubicTo(42, 17, 14, 33, 14, 58)
      ..cubicTo(14, 84, 42, 100, 66, 92)
      ..cubicTo(78, 87, 84, 79, 82, 70)
      ..cubicTo(80, 62, 70, 61, 62, 65)
      ..cubicTo(48, 71, 34, 67, 32, 57)
      ..cubicTo(30, 46, 42, 37, 58, 41)
      ..cubicTo(66, 43, 74, 39, 74, 31)
      ..cubicTo(74, 24, 70, 22, 65, 24)
      ..close();
    canvas.drawPath(pathC, paintC);

    // 2. Chữ A - Đầu tròn nhân sự (Head dot)
    final paintHead = Paint()
      ..shader = const LinearGradient(
        colors: [Color(0xFF00B4D8), Color(0xFF0066FF)],
        begin: Alignment.topCenter,
        end: Alignment.bottomCenter,
      ).createShader(const Rect.fromLTWH(100, 8, 28, 28))
      ..style = PaintingStyle.fill;
    canvas.drawCircle(const Offset(114, 22), 13, paintHead);

    // 2. Chữ A - Thân người vòm cong (Arch body)
    final paintBodyA = Paint()
      ..shader = const LinearGradient(
        colors: [Color(0xFF00E5FF), Color(0xFF0077FF), Color(0xFF0052D4)],
        begin: Alignment.bottomLeft,
        end: Alignment.topRight,
      ).createShader(const Rect.fromLTWH(75, 45, 80, 55))
      ..style = PaintingStyle.fill;

    final pathA = Path()
      ..moveTo(78, 88)
      ..cubicTo(84, 66, 94, 48, 114, 48)
      ..cubicTo(134, 48, 144, 66, 150, 88)
      ..cubicTo(153, 95, 144, 100, 136, 96)
      ..cubicTo(131, 92, 128, 84, 126, 76)
      ..cubicTo(123, 68, 118, 64, 114, 64)
      ..cubicTo(110, 64, 105, 68, 102, 76)
      ..cubicTo(100, 84, 97, 92, 92, 96)
      ..cubicTo(84, 100, 75, 95, 78, 88)
      ..close();
    canvas.drawPath(pathA, paintBodyA);

    // 3. Chữ N (Xanh Navy đậm đà)
    final paintNavy = Paint()
      ..color = const Color(0xFF0B1B3D)
      ..style = PaintingStyle.fill;

    final pathN = Path()
      ..moveTo(165, 40)
      ..lineTo(182, 40)
      ..lineTo(210, 75)
      ..lineTo(210, 40)
      ..lineTo(228, 40)
      ..lineTo(228, 92)
      ..lineTo(211, 92)
      ..lineTo(183, 57)
      ..lineTo(183, 92)
      ..lineTo(165, 92)
      ..close();
    canvas.drawPath(pathN, paintNavy);

    // 4. Chữ i (Thân & Chấm đầu)
    canvas.drawCircle(const Offset(248, 42), 7, paintNavy);
    final rrectI = RRect.fromRectAndRadius(
      const Rect.fromLTWH(241, 54, 14, 38),
      const Radius.circular(4),
    );
    canvas.drawRRect(rrectI, paintNavy);

    canvas.restore();
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}
