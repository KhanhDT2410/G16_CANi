import 'dart:math' as math;
import 'package:flutter/material.dart';

import 'package:cani/features/job_radar/models/job_radar_models.dart';

class JobRadarMapWidget extends StatefulWidget {
  final List<NearbyShiftItem> shifts;
  final NearbyShiftItem? selectedShift;
  final ValueChanged<NearbyShiftItem> onSelectShift;
  final double maxRadiusKm;
  final bool isOnline;

  const JobRadarMapWidget({
    super.key,
    required this.shifts,
    this.selectedShift,
    required this.onSelectShift,
    this.maxRadiusKm = 15.0,
    this.isOnline = true,
  });

  @override
  State<JobRadarMapWidget> createState() => _JobRadarMapWidgetState();
}

class _JobRadarMapWidgetState extends State<JobRadarMapWidget> with SingleTickerProviderStateMixin {
  late AnimationController _animController;

  @override
  void initState() {
    super.initState();
    _animController = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 4),
    )..repeat();
  }

  @override
  void dispose() {
    _animController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return LayoutBuilder(
      builder: (context, constraints) {
        final size = math.min(constraints.maxWidth, constraints.maxHeight);
        final center = Offset(size / 2, size / 2);
        final radarRadius = (size / 2) - 30;

        return Center(
          child: SizedBox(
            width: size,
            height: size,
            child: Stack(
              clipBehavior: Clip.none,
              children: [
                // 1. Radar background canvas với tia quét xoay
                AnimatedBuilder(
                  animation: _animController,
                  builder: (context, child) {
                    return CustomPaint(
                      size: Size(size, size),
                      painter: _RadarBackgroundPainter(
                        angle: _animController.value * 2 * math.pi,
                        theme: theme,
                        isOnline: widget.isOnline,
                        maxRadiusKm: widget.maxRadiusKm,
                      ),
                    );
                  },
                ),

                // 2. Chấm tâm vị trí người dùng (Worker)
                Positioned(
                  left: center.dx - 22,
                  top: center.dy - 22,
                  child: Container(
                    width: 44,
                    height: 44,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      color: widget.isOnline ? Colors.blue.shade600 : Colors.grey,
                      border: Border.all(color: Colors.white, width: 3),
                      boxShadow: [
                        BoxShadow(
                          color: (widget.isOnline ? Colors.blue : Colors.grey).withOpacity(0.4),
                          blurRadius: 12,
                          spreadRadius: 4,
                        ),
                      ],
                    ),
                    child: const Icon(Icons.person_pin_circle, color: Colors.white, size: 24),
                  ),
                ),

                // 3. Render các Pin công việc
                if (widget.isOnline)
                  ...widget.shifts.map((shift) {
                    final normalizedDist = (shift.distanceKm / widget.maxRadiusKm).clamp(0.15, 0.95);
                    // Dùng hash của id để tính góc phân bổ hiển thị trên radar
                    final hash = shift.shiftId.hashCode.abs();
                    final angle = (hash % 360) * (math.pi / 180);

                    final pinX = center.dx + (radarRadius * normalizedDist) * math.cos(angle);
                    final pinY = center.dy + (radarRadius * normalizedDist) * math.sin(angle);

                    final isSelected = widget.selectedShift?.shiftId == shift.shiftId;

                    return Positioned(
                      left: pinX - 24,
                      top: pinY - 24,
                      child: GestureDetector(
                        onTap: () => widget.onSelectShift(shift),
                        child: AnimatedScale(
                          scale: isSelected ? 1.25 : 1.0,
                          duration: const Duration(milliseconds: 200),
                          child: Column(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              Container(
                                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                                decoration: BoxDecoration(
                                  color: isSelected ? Colors.amber.shade700 : Colors.indigo.shade700,
                                  borderRadius: BorderRadius.circular(16),
                                  border: Border.all(color: Colors.white, width: 2),
                                  boxShadow: [
                                    BoxShadow(
                                      color: Colors.black.withOpacity(0.3),
                                      blurRadius: 8,
                                      offset: const Offset(0, 3),
                                    ),
                                  ],
                                ),
                                child: Row(
                                  mainAxisSize: MainAxisSize.min,
                                  children: [
                                    const Icon(Icons.bolt, color: Colors.amberAccent, size: 14),
                                    const SizedBox(width: 2),
                                    Text(
                                      '${(shift.hourlyWage / 1000).toStringAsFixed(0)}k/h',
                                      style: const TextStyle(
                                        color: Colors.white,
                                        fontWeight: FontWeight.bold,
                                        fontSize: 11,
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                              const SizedBox(height: 2),
                              Container(
                                width: 14,
                                height: 14,
                                decoration: BoxDecoration(
                                  shape: BoxShape.circle,
                                  color: isSelected ? Colors.amberAccent : Colors.white,
                                  border: Border.all(color: Colors.indigo.shade900, width: 2.5),
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                    );
                  }),
              ],
            ),
          ),
        );
      },
    );
  }
}

class _RadarBackgroundPainter extends CustomPainter {
  final double angle;
  final ThemeData theme;
  final bool isOnline;
  final double maxRadiusKm;

  _RadarBackgroundPainter({
    required this.angle,
    required this.theme,
    required this.isOnline,
    required this.maxRadiusKm,
  });

  @override
  void paint(Canvas canvas, Size size) {
    final center = Offset(size.width / 2, size.height / 2);
    final maxRadius = (size.width / 2) - 30;

    // Nền tối công nghệ radar
    final bgPaint = Paint()
      ..color = const Color(0xFF0F172A)
      ..style = PaintingStyle.fill;
    canvas.drawCircle(center, maxRadius, bgPaint);

    final linePaint = Paint()
      ..color = (isOnline ? Colors.tealAccent : Colors.grey).withOpacity(0.25)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1.0;

    // Vẽ các vòng tròn đồng tâm (1/3, 2/3, 100%)
    final step = maxRadius / 3;
    final textPainter = TextPainter(textDirection: TextDirection.ltr);

    for (int i = 1; i <= 3; i++) {
      final r = step * i;
      canvas.drawCircle(center, r, linePaint);

      final kmText = '${(maxRadiusKm * (i / 3)).toStringAsFixed(0)} km';
      textPainter.text = TextSpan(
        text: kmText,
        style: TextStyle(
          color: (isOnline ? Colors.tealAccent : Colors.grey).withOpacity(0.7),
          fontSize: 10,
          fontWeight: FontWeight.bold,
        ),
      );
      textPainter.layout();
      textPainter.paint(canvas, Offset(center.dx + 4, center.dy - r + 2));
    }

    // Vẽ trục chữ thập thập tự
    canvas.drawLine(Offset(center.dx - maxRadius, center.dy), Offset(center.dx + maxRadius, center.dy), linePaint);
    canvas.drawLine(Offset(center.dx, center.dy - maxRadius), Offset(center.dx, center.dy + maxRadius), linePaint);

    // Vẽ tia radar quét (chỉ khi online)
    if (isOnline) {
      final sweepPaint = Paint()
        ..shader = SweepGradient(
          center: Alignment.center,
          startAngle: 0.0,
          endAngle: math.pi / 2,
          colors: [
            Colors.tealAccent.withOpacity(0.4),
            Colors.tealAccent.withOpacity(0.0),
          ],
          transform: GradientRotation(angle),
        ).createShader(Rect.fromCircle(center: center, radius: maxRadius))
        ..style = PaintingStyle.fill;

      canvas.drawCircle(center, maxRadius, sweepPaint);

      // Tia quét sáng
      final beamPaint = Paint()
        ..color = Colors.tealAccent.withOpacity(0.8)
        ..strokeWidth = 2.0;
      final beamX = center.dx + maxRadius * math.cos(angle);
      final beamY = center.dy + maxRadius * math.sin(angle);
      canvas.drawLine(center, Offset(beamX, beamY), beamPaint);
    }
  }

  @override
  bool shouldRepaint(covariant _RadarBackgroundPainter oldDelegate) {
    return oldDelegate.angle != angle ||
        oldDelegate.isOnline != isOnline ||
        oldDelegate.maxRadiusKm != maxRadiusKm;
  }
}
