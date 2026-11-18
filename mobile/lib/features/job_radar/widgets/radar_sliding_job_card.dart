import 'package:flutter/material.dart';

import 'package:cani/features/job_radar/models/job_radar_models.dart';

class RadarSlidingJobCard extends StatefulWidget {
  final NearbyShiftItem shift;
  final VoidCallback onViewDetails;
  final VoidCallback onApply;

  const RadarSlidingJobCard({
    super.key,
    required this.shift,
    required this.onViewDetails,
    required this.onApply,
  });

  @override
  State<RadarSlidingJobCard> createState() => _RadarSlidingJobCardState();
}

class _RadarSlidingJobCardState extends State<RadarSlidingJobCard> {
  bool _isCollapsed = false;

  @override
  void didUpdateWidget(covariant RadarSlidingJobCard oldWidget) {
    super.didUpdateWidget(oldWidget);
    // Khi người dùng chạm vào marker ca khác trên bản đồ, tự động mở bung thẻ
    if (oldWidget.shift.shiftId != widget.shift.shiftId) {
      _isCollapsed = false;
    }
  }

  @override
  Widget build(BuildContext context) {
    final s = widget.shift;
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return GestureDetector(
      onVerticalDragEnd: (details) {
        if (details.primaryVelocity != null) {
          if (details.primaryVelocity! > 120) {
            // Vuốt xuống -> Thu nhỏ gập xuống đáy
            setState(() => _isCollapsed = true);
          } else if (details.primaryVelocity! < -120) {
            // Vuốt lên -> Mở rộng
            setState(() => _isCollapsed = false);
          }
        }
      },
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 260),
        curve: Curves.easeOutCubic,
        margin: const EdgeInsets.fromLTRB(14, 0, 14, 10),
        padding: _isCollapsed
            ? const EdgeInsets.symmetric(horizontal: 14, vertical: 8)
            : const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: Theme.of(context).cardColor,
          borderRadius: BorderRadius.circular(_isCollapsed ? 16 : 22),
          border: isDark ? Border.all(color: const Color(0xFF334155)) : null,
          boxShadow: [
            BoxShadow(
              color: isDark
                  ? Colors.black.withValues(alpha: 0.35)
                  : const Color(0xFF0B1B3D).withValues(alpha: 0.14),
              blurRadius: 18,
              offset: const Offset(0, 4),
            ),
          ],
        ),
        child: _isCollapsed
            ? _buildCollapsedView(isDark, s)
            : _buildExpandedView(isDark, s),
      ),
    );
  }

  Widget _buildCollapsedView(bool isDark, NearbyShiftItem s) {
    return InkWell(
      onTap: () => setState(() => _isCollapsed = false),
      borderRadius: BorderRadius.circular(12),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          // Drag handle
          Container(
            width: 36,
            height: 4,
            decoration: BoxDecoration(
              color: isDark ? const Color(0xFF475569) : const Color(0xFFE2E8F0),
              borderRadius: BorderRadius.circular(99),
            ),
          ),
          const SizedBox(height: 6),
          Row(
            children: [
              Container(
                padding: const EdgeInsets.all(5),
                decoration: BoxDecoration(
                  color: const Color(0xFF0077FF).withValues(alpha: 0.12),
                  shape: BoxShape.circle,
                ),
                child: const Icon(Icons.location_on, size: 15, color: Color(0xFF0077FF)),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      '${s.jobType} · ${s.eventName}',
                      style: TextStyle(
                        fontWeight: FontWeight.bold,
                        fontSize: 12.5,
                        color: isDark ? const Color(0xFFF8FAFC) : const Color(0xFF0B1B3D),
                      ),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                    Text(
                      s.isConflicting
                          ? '⚠️ ${s.conflictReason ?? "Trùng thời gian ca khác"}'
                          : 'Cách bạn ${s.distanceKm.toStringAsFixed(1)} km · ${s.hourlyWage.toInt().toString().replaceAllMapped(RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'), (m) => '${m[1]}.')} đ/h',
                      style: TextStyle(
                        fontSize: 10.5,
                        color: s.isConflicting
                            ? const Color(0xFFDC2626)
                            : (isDark ? const Color(0xFF94A3B8) : const Color(0xFF64748B)),
                        fontWeight: FontWeight.w600,
                      ),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 6),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                decoration: BoxDecoration(
                  color: const Color(0xFF0077FF).withValues(alpha: 0.1),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: const Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(
                      'Xem',
                      style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: Color(0xFF0077FF)),
                    ),
                    SizedBox(width: 2),
                    Icon(Icons.keyboard_arrow_up, size: 16, color: Color(0xFF0077FF)),
                  ],
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildExpandedView(bool isDark, NearbyShiftItem s) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        // Drag handle & Nút gạt xuống
        InkWell(
          onTap: () => setState(() => _isCollapsed = true),
          child: Padding(
            padding: const EdgeInsets.only(bottom: 6),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                const SizedBox(width: 24),
                Container(
                  width: 38,
                  height: 4,
                  decoration: BoxDecoration(
                    color: isDark ? const Color(0xFF475569) : const Color(0xFFE2E8F0),
                    borderRadius: BorderRadius.circular(99),
                  ),
                ),
                const SizedBox(width: 8),
                Icon(
                  Icons.keyboard_arrow_down,
                  size: 18,
                  color: isDark ? const Color(0xFF94A3B8) : const Color(0xFF64748B),
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 6),

        // Top Info Row
        Row(
          children: [
            // Job Icon Box
            Container(
              width: 44,
              height: 44,
              decoration: BoxDecoration(
                color: isDark
                    ? const Color(0xFF064E3B).withValues(alpha: 0.4)
                    : const Color(0xFF10B981).withValues(alpha: 0.12),
                borderRadius: BorderRadius.circular(14),
              ),
              child: Icon(
                Icons.work_outline_rounded,
                size: 22,
                color: isDark ? const Color(0xFF34D399) : const Color(0xFF059669),
              ),
            ),
            const SizedBox(width: 11),

            // Title and Subtitle
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    '${s.jobType} · ${s.eventName}',
                    style: TextStyle(
                      fontWeight: FontWeight.w800,
                      fontSize: 13.5,
                      color: isDark ? const Color(0xFFF8FAFC) : const Color(0xFF0B1B3D),
                      letterSpacing: -0.1,
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  const SizedBox(height: 2),
                  Text(
                    'Cách bạn ${s.distanceKm.toStringAsFixed(1)} km · Cần ${s.remainingWorkers} người',
                    style: TextStyle(
                      fontSize: 11,
                      fontWeight: FontWeight.w600,
                      color: isDark ? const Color(0xFF94A3B8) : const Color(0xFF64748B),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(width: 8),

            // Wage
            Column(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                Text(
                  '${s.hourlyWage.toInt().toString().replaceAllMapped(RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'), (m) => '${m[1]}.')} đ',
                  style: TextStyle(
                    fontWeight: FontWeight.w800,
                    fontSize: 13.5,
                    color: isDark ? const Color(0xFF34D399) : const Color(0xFF059669),
                  ),
                ),
                const Text(
                  '/giờ',
                  style: TextStyle(
                    fontSize: 9,
                    fontWeight: FontWeight.w700,
                    color: Color(0xFF94A3B8),
                  ),
                ),
              ],
            ),
          ],
        ),
        const SizedBox(height: 12),

        // Cảnh báo trùng ca làm việc (nếu có)
        if (s.isConflicting) ...[
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 7),
            margin: const EdgeInsets.only(bottom: 12),
            decoration: BoxDecoration(
              color: isDark ? const Color(0xFF450A0A) : const Color(0xFFFEF2F2),
              borderRadius: BorderRadius.circular(8),
              border: Border.all(color: const Color(0xFFFCA5A5)),
            ),
            child: Row(
              children: [
                const Icon(Icons.warning_amber_rounded, size: 16, color: Color(0xFFDC2626)),
                const SizedBox(width: 6),
                Expanded(
                  child: Text(
                    s.conflictReason ?? 'Trùng thời gian với ca bạn đã nhận',
                    style: const TextStyle(
                      fontSize: 11,
                      fontWeight: FontWeight.w700,
                      color: Color(0xFFDC2626),
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
              ],
            ),
          ),
        ],

        // 2 Action Buttons
        Row(
          children: [
            Expanded(
              flex: 1,
              child: SizedBox(
                height: 42,
                child: OutlinedButton.icon(
                  onPressed: widget.onViewDetails,
                  icon: Icon(
                    Icons.info_outline,
                    size: 16,
                    color: isDark ? const Color(0xFFF8FAFC) : const Color(0xFF1E293B),
                  ),
                  label: Text(
                    'Chi tiết',
                    style: TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.w800,
                      color: isDark ? const Color(0xFFF8FAFC) : const Color(0xFF1E293B),
                    ),
                  ),
                  style: OutlinedButton.styleFrom(
                    side: BorderSide(
                      color: isDark ? const Color(0xFF334155) : const Color(0xFFE2E8F0),
                      width: 1.5,
                    ),
                    backgroundColor: isDark ? const Color(0xFF0F172A) : const Color(0xFFF8FAFC),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                  ),
                ),
              ),
            ),
            const SizedBox(width: 8),
            Expanded(
              flex: 1,
              child: SizedBox(
                height: 42,
                child: Container(
                  decoration: BoxDecoration(
                    gradient: (s.isConflicting || !s.emergency)
                        ? null
                        : const LinearGradient(
                            colors: [Color(0xFFEF4444), Color(0xFFDC2626)],
                          ),
                    color: (s.isConflicting || !s.emergency)
                        ? (isDark ? const Color(0xFF1E293B) : const Color(0xFFF1F5F9))
                        : null,
                    border: (s.isConflicting || !s.emergency)
                        ? Border.all(color: isDark ? const Color(0xFF334155) : const Color(0xFFCBD5E1))
                        : null,
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: ElevatedButton.icon(
                    onPressed: s.isConflicting
                        ? () {
                            ScaffoldMessenger.of(context).showSnackBar(
                              SnackBar(
                                content: Text(s.conflictReason ?? 'Bạn đã có ca khác trùng khung giờ này!'),
                                backgroundColor: const Color(0xFFDC2626),
                              ),
                            );
                          }
                        : (s.emergency
                            ? widget.onApply
                            : () {
                                ScaffoldMessenger.of(context).showSnackBar(
                                  const SnackBar(
                                    content: Text('Ca này đang ghép đôi tự động (Matching). Ứng tuyển trực tiếp chỉ mở khi Ban tổ chức kích hoạt điều phối khẩn cấp!'),
                                    backgroundColor: Color(0xFFF59E0B),
                                  ),
                                );
                              }),
                    icon: Icon(
                      s.isConflicting
                          ? Icons.block
                          : (s.emergency ? Icons.flash_on_rounded : Icons.schedule_send_rounded),
                      size: 16,
                      color: (s.isConflicting || !s.emergency) ? const Color(0xFF64748B) : Colors.white,
                    ),
                    label: Text(
                      s.isConflicting
                          ? 'Trùng lịch ca'
                          : (s.emergency ? 'Ứng tuyển ngay' : 'Chờ lời mời (Matching)'),
                      style: TextStyle(
                        fontSize: 11.5,
                        fontWeight: FontWeight.w800,
                        color: (s.isConflicting || !s.emergency) ? const Color(0xFF64748B) : Colors.white,
                      ),
                      overflow: TextOverflow.ellipsis,
                    ),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: Colors.transparent,
                      shadowColor: Colors.transparent,
                      padding: const EdgeInsets.symmetric(horizontal: 8),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                    ),
                  ),
                ),
              ),
            ),
          ],
        ),
      ],
    );
  }
}
