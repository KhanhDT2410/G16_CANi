import 'dart:async';
import 'package:flutter/material.dart';

import '../models/proposal_models.dart';

class JobProposalInviteCard extends StatefulWidget {
  final JobProposalItem proposal;
  final VoidCallback onAccept;
  final VoidCallback onReject;

  const JobProposalInviteCard({
    super.key,
    required this.proposal,
    required this.onAccept,
    required this.onReject,
  });

  @override
  State<JobProposalInviteCard> createState() => _JobProposalInviteCardState();
}

class _JobProposalInviteCardState extends State<JobProposalInviteCard> {
  Timer? _timer;
  int _secondsLeft = 525; // Mặc định 08:45

  @override
  void initState() {
    super.initState();
    // Tạo thời gian đếm ngược giả lập demo nếu không có deadline cụ thể
    _secondsLeft = 525 + (widget.proposal.proposalId.hashCode % 300).abs();
    _timer = Timer.periodic(const Duration(seconds: 1), (_) {
      if (_secondsLeft > 0 && mounted) {
        setState(() => _secondsLeft--);
      }
    });
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  String _formatCountdown() {
    if (_secondsLeft <= 0) return 'Hết hạn';
    final m = (_secondsLeft ~/ 60).toString().padLeft(2, '0');
    final s = (_secondsLeft % 60).toString().padLeft(2, '0');
    return '$m:$s';
  }

  @override
  Widget build(BuildContext context) {
    final p = widget.proposal;

    return Container(
      margin: const EdgeInsets.only(bottom: 14),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(20),
        boxShadow: [
          BoxShadow(
            color: const Color(0xFF0077FF).withValues(alpha: 0.08),
            blurRadius: 18,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Header: Vị trí việc + Badge Khớp kỹ năng
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Expanded(
                child: Text(
                  p.jobType,
                  style: const TextStyle(
                    fontWeight: FontWeight.w800,
                    fontSize: 15,
                    color: Color(0xFF0B1B3D),
                    letterSpacing: -0.1,
                  ),
                ),
              ),
              const SizedBox(width: 8),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 4),
                decoration: BoxDecoration(
                  color: const Color(0xFF10B981).withValues(alpha: 0.10),
                  borderRadius: BorderRadius.circular(999),
                ),
                child: Text(
                  'Khớp ${p.matchScore.toInt()}% kỹ năng',
                  style: const TextStyle(
                    color: Color(0xFF059669),
                    fontSize: 10.5,
                    fontWeight: FontWeight.w800,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 5),

          // Tên sự kiện
          Text(
            p.eventName,
            style: const TextStyle(
              fontSize: 12.5,
              fontWeight: FontWeight.w700,
              color: Color(0xFF1E293B),
            ),
          ),
          const SizedBox(height: 10),

          // Địa chỉ & Thời gian
          Row(
            children: [
              const Icon(Icons.location_on, size: 15, color: Color(0xFF0077FF)),
              const SizedBox(width: 6),
              Expanded(
                child: Text(
                  p.venueAddress,
                  style: const TextStyle(fontSize: 11.5, color: Color(0xFF64748B), fontWeight: FontWeight.w600),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
            ],
          ),
          const SizedBox(height: 4),
          Row(
            children: [
              const Icon(Icons.access_time_rounded, size: 15, color: Color(0xFF0077FF)),
              const SizedBox(width: 6),
              Text(
                '${p.startTime.hour.toString().padLeft(2, '0')}:${p.startTime.minute.toString().padLeft(2, '0')} - ${p.endTime.hour.toString().padLeft(2, '0')}:${p.endTime.minute.toString().padLeft(2, '0')} (${p.startTime.day}/${p.startTime.month}/${p.startTime.year})',
                style: const TextStyle(fontSize: 11.5, color: Color(0xFF64748B), fontWeight: FontWeight.w600),
              ),
            ],
          ),
          const SizedBox(height: 12),

          // Divider mỏng
          const Divider(height: 1, color: Color(0xFFF0F7FF)),
          const SizedBox(height: 12),

          // Footer: Thù lao & Đồng hồ đếm ngược
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    '${p.totalEstimatedPay.toInt().toString().replaceAllMapped(RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'), (m) => '${m[1]}.')} đ',
                    style: const TextStyle(
                      fontWeight: FontWeight.w800,
                      fontSize: 16.5,
                      color: Color(0xFF059669),
                      letterSpacing: -0.2,
                    ),
                  ),
                  const Text(
                    '/ca làm việc',
                    style: TextStyle(fontSize: 9.5, fontWeight: FontWeight.w700, color: Color(0xFF94A3B8)),
                  ),
                ],
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                decoration: BoxDecoration(
                  color: const Color(0xFFF59E0B).withValues(alpha: 0.12),
                  borderRadius: BorderRadius.circular(999),
                ),
                child: Row(
                  children: [
                    const Icon(Icons.timer_outlined, size: 13, color: Color(0xFFB45309)),
                    const SizedBox(width: 4),
                    Text(
                      'Hết hạn sau ${_formatCountdown()}',
                      style: const TextStyle(
                        color: Color(0xFFB45309),
                        fontSize: 11,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 13),

          // 2 Nút: Từ chối & Nhận ca ngay
          Row(
            children: [
              Expanded(
                flex: 1,
                child: SizedBox(
                  height: 44,
                  child: OutlinedButton(
                    onPressed: widget.onReject,
                    style: OutlinedButton.styleFrom(
                      side: const BorderSide(color: Color(0xFFE2E8F0), width: 1.5),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                    ),
                    child: const Text(
                      'Từ chối',
                      style: TextStyle(
                        fontSize: 12.5,
                        fontWeight: FontWeight.w800,
                        color: Color(0xFF1E293B),
                      ),
                    ),
                  ),
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                flex: 1,
                child: SizedBox(
                  height: 44,
                  child: Container(
                    decoration: BoxDecoration(
                      gradient: const LinearGradient(
                        colors: [Color(0xFF00D2FF), Color(0xFF0066FF)],
                      ),
                      borderRadius: BorderRadius.circular(14),
                      boxShadow: const [
                        BoxShadow(
                          color: Color(0x500066FF),
                          blurRadius: 14,
                          offset: Offset(0, 4),
                        ),
                      ],
                    ),
                    child: ElevatedButton(
                      onPressed: widget.onAccept,
                      style: ElevatedButton.styleFrom(
                        backgroundColor: Colors.transparent,
                        shadowColor: Colors.transparent,
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                      ),
                      child: const Text(
                        'Nhận ca ngay',
                        style: TextStyle(
                          fontSize: 12.5,
                          fontWeight: FontWeight.w800,
                          color: Colors.white,
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}
