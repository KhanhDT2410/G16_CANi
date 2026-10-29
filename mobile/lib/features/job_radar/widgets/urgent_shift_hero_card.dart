import 'dart:async';
import 'package:flutter/material.dart';

import 'package:cani/features/job_proposals/models/proposal_models.dart';

class UrgentShiftHeroCard extends StatefulWidget {
  final ShiftAssignmentItem assignment;
  final VoidCallback onNavigate;
  final VoidCallback onQrScan;

  const UrgentShiftHeroCard({
    super.key,
    required this.assignment,
    required this.onNavigate,
    required this.onQrScan,
  });

  @override
  State<UrgentShiftHeroCard> createState() => _UrgentShiftHeroCardState();
}

class _UrgentShiftHeroCardState extends State<UrgentShiftHeroCard> {
  Timer? _timer;
  String _timeRemaining = '00:00:00';

  @override
  void initState() {
    super.initState();
    _updateRemainingTime();
    _timer = Timer.periodic(const Duration(seconds: 1), (_) => _updateRemainingTime());
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  void _updateRemainingTime() {
    final now = DateTime.now();
    final target = widget.assignment.startTime;
    final diff = target.difference(now);

    if (diff.isNegative) {
      if (mounted) setState(() => _timeRemaining = 'Đang diễn ra');
      return;
    }

    final hours = diff.inHours.toString().padLeft(2, '0');
    final minutes = (diff.inMinutes % 60).toString().padLeft(2, '0');
    final seconds = (diff.inSeconds % 60).toString().padLeft(2, '0');
    if (mounted) {
      setState(() => _timeRemaining = '$hours:$minutes:$seconds');
    }
  }

  @override
  Widget build(BuildContext context) {
    final a = widget.assignment;
    final isCheckedIn = a.status == 'CHECKED_IN' || (a.checkinTime != null && a.checkoutTime == null);

    return Container(
      margin: const EdgeInsets.fromLTRB(16, 0, 16, 14),
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 14),
      decoration: BoxDecoration(
        gradient: const LinearGradient(
          colors: [Color(0xFF00D2FF), Color(0xFF0066FF)],
          begin: Alignment.centerLeft,
          end: Alignment.centerRight,
        ),
        borderRadius: BorderRadius.circular(20),
        boxShadow: const [
          BoxShadow(
            color: Color(0x400066FF),
            blurRadius: 16,
            offset: Offset(0, 6),
          ),
        ],
      ),
      child: Stack(
        children: [
          // Hiệu ứng bong bóng trang trí góc phải
          Positioned(
            right: -25,
            top: -30,
            child: Container(
              width: 120,
              height: 120,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: Colors.white.withValues(alpha: 0.12),
              ),
            ),
          ),
          Positioned(
            right: 35,
            bottom: -40,
            child: Container(
              width: 90,
              height: 90,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: Colors.white.withValues(alpha: 0.08),
              ),
            ),
          ),

          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Tag Header
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                decoration: BoxDecoration(
                  color: Colors.white.withValues(alpha: 0.22),
                  borderRadius: BorderRadius.circular(999),
                  border: Border.all(color: Colors.white.withValues(alpha: 0.35)),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Container(
                      width: 7,
                      height: 7,
                      decoration: const BoxDecoration(
                        shape: BoxShape.circle,
                        color: Colors.white,
                      ),
                    ),
                    const SizedBox(width: 6),
                    Text(
                      isCheckedIn ? 'CA LÀM ĐANG DIỄN RA' : 'CA LÀM KẾ TIẾP HÔM NAY',
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 10.5,
                        fontWeight: FontWeight.w800,
                        letterSpacing: 0.4,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 9),

              // Tên Ca & Sự kiện
              Text(
                '${a.jobType} · ${a.eventName}',
                style: const TextStyle(
                  color: Colors.white,
                  fontWeight: FontWeight.w800,
                  fontSize: 15.5,
                  letterSpacing: -0.2,
                ),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
              const SizedBox(height: 3),

              // Địa điểm & Thời gian
              Text(
                '${a.startTime.hour.toString().padLeft(2, '0')}:${a.startTime.minute.toString().padLeft(2, '0')} - ${a.endTime.hour.toString().padLeft(2, '0')}:${a.endTime.minute.toString().padLeft(2, '0')} · ${a.venueAddress}',
                style: TextStyle(
                  color: Colors.white.withValues(alpha: 0.92),
                  fontSize: 12,
                  fontWeight: FontWeight.w600,
                ),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
              const SizedBox(height: 10),

              // Countdown badge
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                decoration: BoxDecoration(
                  color: const Color(0xFF0B1B3D).withValues(alpha: 0.3),
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(color: Colors.white.withValues(alpha: 0.25)),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const Icon(Icons.access_time_rounded, size: 12, color: Colors.white),
                    const SizedBox(width: 6),
                    Text(
                      isCheckedIn ? 'Trạng thái: Đã Check-in' : 'Còn ',
                      style: const TextStyle(color: Colors.white, fontSize: 11, fontWeight: FontWeight.w600),
                    ),
                    if (!isCheckedIn)
                      Text(
                        _timeRemaining,
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 12.5,
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                  ],
                ),
              ),
              const SizedBox(height: 12),

              // 2 Nút hành động
              Row(
                children: [
                  Expanded(
                    child: SizedBox(
                      height: 40,
                      child: ElevatedButton.icon(
                        onPressed: widget.onNavigate,
                        icon: const Icon(Icons.directions, size: 16, color: Color(0xFF0066FF)),
                        label: const Text(
                          'Chỉ đường',
                          style: TextStyle(
                            color: Color(0xFF0066FF),
                            fontWeight: FontWeight.w800,
                            fontSize: 12.5,
                          ),
                        ),
                        style: ElevatedButton.styleFrom(
                          backgroundColor: Colors.white,
                          elevation: 2,
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(width: 9),
                  Expanded(
                    child: SizedBox(
                      height: 40,
                      child: ElevatedButton.icon(
                        onPressed: widget.onQrScan,
                        icon: const Icon(Icons.qr_code_scanner, size: 16, color: Colors.white),
                        label: Text(
                          isCheckedIn ? 'Quét QR Check-out' : 'Quét QR Check-in',
                          style: const TextStyle(
                            color: Colors.white,
                            fontWeight: FontWeight.w800,
                            fontSize: 12.5,
                          ),
                        ),
                        style: ElevatedButton.styleFrom(
                          backgroundColor: const Color(0xFF0D9488),
                          elevation: 2,
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ],
      ),
    );
  }
}
