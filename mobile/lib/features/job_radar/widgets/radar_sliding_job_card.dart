import 'package:cani/app/cani_tokens.dart';
import 'package:cani/features/job_proposals/widgets/job_proposal_invite_card.dart';
import 'package:flutter/material.dart';

import 'package:cani/features/job_radar/models/job_radar_models.dart';

class RadarSlidingJobCard extends StatefulWidget {
  final NearbyShiftItem shift;
  final VoidCallback onViewDetails;
  final VoidCallback onApply;
  final int activeInviteCount;
  final VoidCallback? onViewInvites;
  final int? carouselIndex;
  final int? carouselTotal;
  final VoidCallback? onPrev;
  final VoidCallback? onNext;

  const RadarSlidingJobCard({
    super.key,
    required this.shift,
    required this.onViewDetails,
    required this.onApply,
    this.activeInviteCount = 0,
    this.onViewInvites,
    this.carouselIndex,
    this.carouselTotal,
    this.onPrev,
    this.onNext,
  });

  @override
  State<RadarSlidingJobCard> createState() => _RadarSlidingJobCardState();
}

class _RadarSlidingJobCardState extends State<RadarSlidingJobCard> {
  bool _isCollapsed = false;

  @override
  void didUpdateWidget(covariant RadarSlidingJobCard oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.shift.shiftId != widget.shift.shiftId) {
      _isCollapsed = false;
    }
  }

  String _formatWage(double wage) {
    return wage.toInt().toString().replaceAllMapped(
          RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'),
          (m) => '${m[1]}.',
        );
  }

  String? _formatTime(NearbyShiftItem s) {
    if (s.startTime == null || s.endTime == null) return null;
    final st = '${s.startTime!.hour.toString().padLeft(2, '0')}:${s.startTime!.minute.toString().padLeft(2, '0')}';
    final et = '${s.endTime!.hour.toString().padLeft(2, '0')}:${s.endTime!.minute.toString().padLeft(2, '0')}';
    return '$st - $et · ${s.startTime!.day}/${s.startTime!.month}';
  }

  @override
  Widget build(BuildContext context) {
    final s = widget.shift;
    final cani = Theme.of(context).cani;
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return GestureDetector(
      onVerticalDragEnd: (details) {
        if (details.primaryVelocity != null) {
          if (details.primaryVelocity! > 120) {
            setState(() => _isCollapsed = true);
          } else if (details.primaryVelocity! < -120) {
            setState(() => _isCollapsed = false);
          }
        }
      },
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 240),
        curve: Curves.easeOutCubic,
        padding: _isCollapsed
            ? const EdgeInsets.symmetric(horizontal: 14, vertical: 8)
            : const EdgeInsets.fromLTRB(14, 10, 14, 12),
        decoration: BoxDecoration(
          color: Theme.of(context).cardColor,
          borderRadius: BorderRadius.circular(_isCollapsed ? 16 : 20),
          border: Border.all(color: isDark ? cani.border : cani.border.withValues(alpha: 0.7)),
          boxShadow: [
            BoxShadow(
              color: isDark
                  ? Colors.black.withValues(alpha: 0.35)
                  : cani.textMain.withValues(alpha: 0.14),
              blurRadius: 18,
              offset: const Offset(0, 4),
            ),
          ],
        ),
        child: _isCollapsed
            ? _buildCollapsedView(cani, isDark, s)
            : _buildExpandedView(cani, isDark, s),
      ),
    );
  }

  Widget _buildCollapsedView(CaniTokens cani, bool isDark, NearbyShiftItem s) {
    return InkWell(
      onTap: () => setState(() => _isCollapsed = false),
      borderRadius: BorderRadius.circular(12),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 36,
            height: 4,
            decoration: BoxDecoration(
              color: isDark ? const Color(0xFF475569) : cani.border,
              borderRadius: BorderRadius.circular(99),
            ),
          ),
          const SizedBox(height: 6),
          Row(
            children: [
              Container(
                padding: const EdgeInsets.all(5),
                decoration: BoxDecoration(
                  color: cani.brand.withValues(alpha: 0.12),
                  shape: BoxShape.circle,
                ),
                child: Icon(Icons.location_on, size: 15, color: cani.brand),
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
                        color: isDark ? const Color(0xFFF8FAFC) : cani.textMain,
                      ),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                    Text(
                      s.isConflicting
                          ? (s.conflictReason ?? 'Bạn đã có ca nhận rồi')
                          : 'Cách bạn ${s.distanceKm.toStringAsFixed(1)} km · ${_formatWage(s.hourlyWage)} đ/h',
                      style: TextStyle(
                        fontSize: 10.5,
                        color: s.isConflicting ? cani.warning : cani.textMuted,
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
                  color: cani.brand.withValues(alpha: 0.1),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(
                      'Xem',
                      style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: cani.brand),
                    ),
                    const SizedBox(width: 2),
                    Icon(Icons.keyboard_arrow_up, size: 16, color: cani.brand),
                  ],
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildExpandedView(CaniTokens cani, bool isDark, NearbyShiftItem s) {
    final timeStr = _formatTime(s);

    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // Hàng đầu: "Ca gần bạn" + trạng thái khẩn cấp/matching + nút thu gọn + về lời mời (nếu có)
        Row(
          crossAxisAlignment: CrossAxisAlignment.center,
          children: [
            Expanded(
              child: Wrap(
                spacing: 6,
                runSpacing: 4,
                crossAxisAlignment: WrapCrossAlignment.center,
                children: [
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                    decoration: BoxDecoration(
                      color: isDark
                          ? const Color(0xFF064E3B).withValues(alpha: 0.45)
                          : const Color(0xFFECFDF5),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Text(
                      'Ca gần bạn',
                      style: TextStyle(
                        fontSize: 11,
                        fontWeight: FontWeight.w800,
                        color: isDark ? const Color(0xFF34D399) : const Color(0xFF047857),
                      ),
                    ),
                  ),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 3),
                    decoration: BoxDecoration(
                      color: s.isConflicting
                          ? cani.warningBg
                          : (s.emergency
                              ? cani.dangerBg
                              : (isDark ? cani.canvas : const Color(0xFFF1F5F9))),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Text(
                      s.isConflicting
                          ? 'Bạn đã có ca nhận rồi'
                          : (s.emergency ? 'Khẩn cấp' : 'Matching'),
                      style: TextStyle(
                        fontSize: 10.5,
                        fontWeight: FontWeight.w700,
                        color: s.isConflicting
                            ? cani.warning
                            : (s.emergency ? cani.danger : cani.textMuted),
                      ),
                    ),
                  ),
                ],
              ),
            ),
            if ((widget.carouselTotal ?? 0) > 1 && widget.carouselIndex != null) ...[
              const SizedBox(width: 4),
              CarouselPagerChip(
                index: widget.carouselIndex!,
                total: widget.carouselTotal!,
                onPrev: widget.onPrev,
                onNext: widget.onNext,
              ),
            ] else if (widget.activeInviteCount > 0 && widget.onViewInvites != null) ...[
              const SizedBox(width: 6),
              InkWell(
                onTap: widget.onViewInvites,
                borderRadius: BorderRadius.circular(8),
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 3),
                  child: Text(
                    'Lời mời (${widget.activeInviteCount})',
                    style: TextStyle(
                      fontSize: 11,
                      fontWeight: FontWeight.w800,
                      color: cani.brand,
                    ),
                  ),
                ),
              ),
            ],
            const SizedBox(width: 4),
            InkWell(
              onTap: () => setState(() => _isCollapsed = true),
              borderRadius: BorderRadius.circular(8),
              child: Padding(
                padding: const EdgeInsets.all(2),
                child: Icon(Icons.keyboard_arrow_down_rounded, size: 20, color: cani.textMuted),
              ),
            ),
          ],
        ),
        const SizedBox(height: 8),

        // Thông tin chính: Sự kiện, Vị trí & Lương
        Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    s.eventName,
                    style: TextStyle(
                      fontWeight: FontWeight.w800,
                      fontSize: 14,
                      color: isDark ? const Color(0xFFF8FAFC) : cani.textMain,
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  const SizedBox(height: 2),
                  Text(
                    '${s.jobType} · Cách ${s.distanceKm.toStringAsFixed(1)} km · Cần ${s.remainingWorkers} người',
                    style: TextStyle(
                      fontSize: 11.5,
                      fontWeight: FontWeight.w600,
                      color: cani.textMuted,
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  if (timeStr != null) ...[
                    const SizedBox(height: 2),
                    Text(
                      timeStr,
                      style: TextStyle(
                        fontSize: 11,
                        fontWeight: FontWeight.w600,
                        color: cani.textBody,
                      ),
                    ),
                  ],
                ],
              ),
            ),
            const SizedBox(width: 10),
            Column(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                Text(
                  '${_formatWage(s.hourlyWage)} đ',
                  style: TextStyle(
                    fontWeight: FontWeight.w800,
                    fontSize: 14,
                    color: isDark ? const Color(0xFF34D399) : const Color(0xFF059669),
                  ),
                ),
                Text(
                  '/giờ',
                  style: TextStyle(
                    fontSize: 9.5,
                    fontWeight: FontWeight.w700,
                    color: cani.textMuted,
                  ),
                ),
              ],
            ),
          ],
        ),
        const SizedBox(height: 8),

        if (s.isConflicting) ...[
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
            margin: const EdgeInsets.only(bottom: 8),
            decoration: BoxDecoration(
              color: cani.warningBg,
              borderRadius: BorderRadius.circular(8),
              border: Border.all(color: cani.warning.withValues(alpha: 0.45)),
            ),
            child: Row(
              children: [
                Icon(Icons.event_busy_rounded, size: 15, color: cani.warning),
                const SizedBox(width: 6),
                Expanded(
                  child: Text(
                    s.conflictReason ?? 'Bạn đã có ca nhận rồi',
                    style: TextStyle(
                      fontSize: 11,
                      fontWeight: FontWeight.w700,
                      color: cani.warning,
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
              ],
            ),
          ),
        ],

        // 2 Action Buttons: Xem chi tiết & Ứng tuyển / Trạng thái
        Row(
          children: [
            Expanded(
              child: SizedBox(
                height: 40,
                child: OutlinedButton(
                  onPressed: widget.onViewDetails,
                  style: OutlinedButton.styleFrom(
                    padding: const EdgeInsets.symmetric(horizontal: 8),
                    side: BorderSide(
                      color: isDark ? cani.borderStrong : cani.border,
                      width: 1.3,
                    ),
                    backgroundColor: isDark ? cani.canvas : const Color(0xFFF8FAFC),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                  ),
                  child: Text(
                    'Xem chi tiết',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.w800,
                      color: isDark ? const Color(0xFFF8FAFC) : const Color(0xFF1E293B),
                    ),
                  ),
                ),
              ),
            ),
            const SizedBox(width: 8),
            Expanded(
              child: SizedBox(
                height: 40,
                child: ElevatedButton.icon(
                  onPressed: s.isConflicting
                      ? () {
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(
                              content: Text(s.conflictReason ?? 'Bạn đã có ca nhận rồi!'),
                              backgroundColor: cani.warning,
                            ),
                          );
                        }
                      : (s.emergency
                          ? widget.onApply
                          : () {
                              ScaffoldMessenger.of(context).showSnackBar(
                                SnackBar(
                                  content: const Text(
                                      'Ca này đang ghép đôi tự động (Matching). Ứng tuyển trực tiếp chỉ mở khi Ban tổ chức kích hoạt điều phối khẩn cấp!'),
                                  backgroundColor: cani.warning,
                                ),
                              );
                            }),
                  icon: Icon(
                    s.isConflicting
                        ? Icons.event_busy_rounded
                        : (s.emergency ? Icons.flash_on_rounded : Icons.schedule_send_rounded),
                    size: 15,
                    color: (s.isConflicting || !s.emergency) ? cani.textMuted : Colors.white,
                  ),
                  label: Text(
                    s.isConflicting
                        ? 'Bạn đã có ca nhận rồi'
                        : (s.emergency ? 'Nhận ca' : 'Chờ lời mời'),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.w800,
                      color: (s.isConflicting || !s.emergency) ? cani.textMuted : Colors.white,
                    ),
                  ),
                  style: ElevatedButton.styleFrom(
                    elevation: 0,
                    backgroundColor: (s.isConflicting || !s.emergency)
                        ? (isDark ? cani.canvas : cani.skeleton)
                        : cani.danger,
                    padding: const EdgeInsets.symmetric(horizontal: 8),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12),
                      side: (s.isConflicting || !s.emergency)
                          ? BorderSide(color: cani.borderStrong)
                          : BorderSide.none,
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
