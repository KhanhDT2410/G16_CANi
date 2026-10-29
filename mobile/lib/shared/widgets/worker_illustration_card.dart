import 'package:flutter/material.dart';

/// WorkerIllustrationCard
/// Card hình minh hoạ nhân sự sự kiện năng động, cầm điện thoại thông minh,
/// với hậu cảnh sân khấu festival ánh sáng xanh dịu mát chuẩn mockup Image 2.
class WorkerIllustrationCard extends StatelessWidget {
  const WorkerIllustrationCard({super.key});

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 175,
      width: double.infinity,
      margin: const EdgeInsets.only(bottom: 20),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(20),
        gradient: const LinearGradient(
          colors: [Color(0xFFE0F2FE), Color(0xFFEFF6FF), Color(0xFFF0FDF4)],
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
        ),
        boxShadow: [
          BoxShadow(
            color: const Color(0xFF0077FF).withValues(alpha: 0.06),
            blurRadius: 16,
            offset: const Offset(0, 6),
          ),
        ],
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(20),
        child: Stack(
          alignment: Alignment.bottomCenter,
          children: [
            // Ánh sáng sân khấu (Spotlights)
            Positioned(
              top: -20,
              left: 20,
              child: Container(
                width: 90,
                height: 140,
                decoration: BoxDecoration(
                  gradient: RadialGradient(
                    colors: [
                      const Color(0xFF38BDF8).withValues(alpha: 0.35),
                      Colors.transparent,
                    ],
                  ),
                ),
              ),
            ),
            Positioned(
              top: -20,
              right: 20,
              child: Container(
                width: 90,
                height: 140,
                decoration: BoxDecoration(
                  gradient: RadialGradient(
                    colors: [
                      const Color(0xFF0077FF).withValues(alpha: 0.25),
                      Colors.transparent,
                    ],
                  ),
                ),
              ),
            ),

            // Sân khấu mờ ảo phía sau
            Positioned(
              bottom: 0,
              left: 0,
              right: 0,
              child: Container(
                height: 60,
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    colors: [
                      Colors.transparent,
                      const Color(0xFF38BDF8).withValues(alpha: 0.12),
                    ],
                    begin: Alignment.topCenter,
                    end: Alignment.bottomCenter,
                  ),
                ),
              ),
            ),

            // Nhân sự sự kiện CANi (Vector Art)
            Positioned(
              bottom: -4,
              child: CustomPaint(
                size: const Size(180, 160),
                painter: _WorkerAvatarPainter(),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _WorkerAvatarPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final cx = size.width / 2;

    // 1. Áo Polo sự kiện CANi (Xanh Navy / Cobalt Blue)
    final shirtPaint = Paint()
      ..shader = const LinearGradient(
        colors: [Color(0xFF1E3A8A), Color(0xFF1D4ED8), Color(0xFF2563EB)],
        begin: Alignment.topCenter,
        end: Alignment.bottomCenter,
      ).createShader(Rect.fromLTWH(cx - 50, 80, 100, 80));

    final shirtPath = Path()
      ..moveTo(cx - 30, 80)
      ..lineTo(cx + 30, 80)
      ..lineTo(cx + 55, 160)
      ..lineTo(cx - 55, 160)
      ..close();
    canvas.drawPath(shirtPath, shirtPaint);

    // Cổ áo Polo
    final collarPaint = Paint()..color = const Color(0xFF1E40AF);
    final collarPath = Path()
      ..moveTo(cx - 18, 80)
      ..lineTo(cx, 98)
      ..lineTo(cx + 18, 80)
      ..close();
    canvas.drawPath(collarPath, collarPaint);

    // Dây đeo thẻ và thẻ sự kiện (Lanyard & Badge)
    final lanyardPaint = Paint()
      ..color = const Color(0xFF38BDF8)
      ..strokeWidth = 2.5
      ..style = PaintingStyle.stroke;
    canvas.drawLine(Offset(cx - 8, 90), Offset(cx - 4, 120), lanyardPaint);
    canvas.drawLine(Offset(cx + 8, 90), Offset(cx + 4, 120), lanyardPaint);

    // Thẻ tên (Badge card)
    final badgePaint = Paint()..color = Colors.white;
    canvas.drawRRect(
      RRect.fromRectAndRadius(Rect.fromLTWH(cx - 9, 120, 18, 22), const Radius.circular(3)),
      badgePaint,
    );
    final badgeDotPaint = Paint()..color = const Color(0xFF0077FF);
    canvas.drawCircle(Offset(cx, 131), 4, badgeDotPaint);

    // Cổ
    final skinPaint = Paint()..color = const Color(0xFFFFD1B3);
    canvas.drawRect(Rect.fromLTWH(cx - 10, 68, 20, 18), skinPaint);

    // Khuôn mặt (Face)
    canvas.drawCircle(Offset(cx, 55), 20, skinPaint);

    // Mắt & Nụ cười
    final eyePaint = Paint()..color = const Color(0xFF1E293B);
    canvas.drawCircle(Offset(cx - 7, 54), 2.2, eyePaint);
    canvas.drawCircle(Offset(cx + 7, 54), 2.2, eyePaint);

    final smilePaint = Paint()
      ..color = const Color(0xFFE11D48)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1.8;
    canvas.drawArc(
      Rect.fromLTWH(cx - 6, 56, 12, 8),
      0.2,
      2.7,
      false,
      smilePaint,
    );

    // Tai
    canvas.drawCircle(Offset(cx - 20, 56), 4.5, skinPaint);
    canvas.drawCircle(Offset(cx + 20, 56), 4.5, skinPaint);

    // Tóc
    final hairPaint = Paint()..color = const Color(0xFF0F172A);
    final hairPath = Path()
      ..moveTo(cx - 20, 50)
      ..cubicTo(cx - 20, 32, cx + 20, 32, cx + 20, 50)
      ..close();
    canvas.drawPath(hairPath, hairPaint);

    // Mũ lưỡi trai (Cap) màu xanh
    final capPaint = Paint()..color = const Color(0xFF1E40AF);
    canvas.drawArc(
      Rect.fromLTWH(cx - 22, 28, 44, 30),
      3.14,
      3.14,
      true,
      capPaint,
    );
    // Vành mũ
    final brimPath = Path()
      ..moveTo(cx + 8, 42)
      ..lineTo(cx + 34, 46)
      ..lineTo(cx + 14, 50)
      ..close();
    canvas.drawPath(brimPath, Paint()..color = const Color(0xFF1D4ED8));

    // Tay cầm Smartphone
    final phonePaint = Paint()..color = const Color(0xFF0F172A);
    canvas.drawRRect(
      RRect.fromRectAndRadius(Rect.fromLTWH(cx + 28, 92, 16, 30), const Radius.circular(3)),
      phonePaint,
    );
    final screenPaint = Paint()..color = const Color(0xFF38BDF8);
    canvas.drawRect(Rect.fromLTWH(cx + 30, 95, 12, 22), screenPaint);

    // Bàn tay
    canvas.drawCircle(Offset(cx + 36, 114), 6, skinPaint);
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}
