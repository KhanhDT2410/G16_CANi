import 'dart:async';
import 'package:cani/app/cani_tokens.dart';
import 'package:flutter/material.dart';

import '../models/proposal_models.dart';
import 'eo_public_profile_sheet.dart';

class JobProposalInviteCard extends StatefulWidget {
  final JobProposalItem proposal;
  final VoidCallback onAccept;
  final VoidCallback onReject;
  final VoidCallback? onViewDetails;
  final bool compact;
  final int totalPendingCount;
  final VoidCallback? onViewAll;
  final int? carouselIndex;
  final int? carouselTotal;
  final VoidCallback? onPrev;
  final VoidCallback? onNext;

  const JobProposalInviteCard({
    super.key,
    required this.proposal,
    required this.onAccept,
    required this.onReject,
    this.onViewDetails,
    this.compact = false,
    this.totalPendingCount = 1,
    this.onViewAll,
    this.carouselIndex,
    this.carouselTotal,
    this.onPrev,
    this.onNext,
  });

  @override
  State<JobProposalInviteCard> createState() => _JobProposalInviteCardState();
}

class _JobProposalInviteCardState extends State<JobProposalInviteCard> {
  Timer? _timer;
  int _secondsLeft = 0;

  @override
  void initState() {
    super.initState();
    _syncSecondsLeft();
    _timer = Timer.periodic(const Duration(seconds: 1), (_) {
      if (mounted) {
        setState(_syncSecondsLeft);
      }
    });
  }

  @override
  void didUpdateWidget(covariant JobProposalInviteCard oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.proposal.proposalId != widget.proposal.proposalId ||
        oldWidget.proposal.expiresAt != widget.proposal.expiresAt) {
      _syncSecondsLeft();
    }
  }

  void _syncSecondsLeft() {
    final expiresAt = widget.proposal.expiresAt;
    if (expiresAt == null) {
      _secondsLeft = -1;
      return;
    }
    final diff = expiresAt.difference(DateTime.now()).inSeconds;
    _secondsLeft = diff > 0 ? diff : 0;
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  String _formatCountdown() {
    if (_secondsLeft < 0) return 'Đang mở';
    if (_secondsLeft == 0) return 'Đã hết hạn';
    final hours = _secondsLeft ~/ 3600;
    final mins = (_secondsLeft % 3600) ~/ 60;
    final secs = _secondsLeft % 60;
    if (hours > 0) {
      return '${hours.toString().padLeft(2, '0')}:${mins.toString().padLeft(2, '0')}:${secs.toString().padLeft(2, '0')}';
    }
    return '${mins.toString().padLeft(2, '0')}:${secs.toString().padLeft(2, '0')}';
  }

  String _formatMoney(double amount) {
    return amount.toInt().toString().replaceAllMapped(
          RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'),
          (m) => '${m[1]}.',
        );
  }

  String _formatTimeRange(JobProposalItem p) {
    final s = '${p.startTime.hour.toString().padLeft(2, '0')}:${p.startTime.minute.toString().padLeft(2, '0')}';
    final e = '${p.endTime.hour.toString().padLeft(2, '0')}:${p.endTime.minute.toString().padLeft(2, '0')}';
    return '$s - $e · ${p.startTime.day}/${p.startTime.month}';
  }

  @override
  Widget build(BuildContext context) {
    if (widget.compact) {
      return _buildCompactBottomCard(context);
    }
    return _buildFullCard(context);
  }

  Widget _buildCompactBottomCard(BuildContext context) {
    final p = widget.proposal;
    final cani = Theme.of(context).cani;
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final isExpired = _secondsLeft == 0;

    return Container(
      padding: const EdgeInsets.fromLTRB(14, 12, 14, 12),
      decoration: BoxDecoration(
        color: Theme.of(context).cardColor,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(
          color: cani.brand.withValues(alpha: isDark ? 0.45 : 0.28),
          width: 1.2,
        ),
        boxShadow: [
          BoxShadow(
            color: isDark
                ? Colors.black.withValues(alpha: 0.40)
                : cani.textMain.withValues(alpha: 0.14),
            blurRadius: 18,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Hàng tiêu đề: "Lời mời làm việc" + đếm ngược + "Xem tất cả (N)"
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
                        color: cani.brandSoft,
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: Text(
                        'Lời mời làm việc',
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(
                          fontSize: 11,
                          fontWeight: FontWeight.w800,
                          color: cani.brand,
                        ),
                      ),
                    ),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 3),
                      decoration: BoxDecoration(
                        color: cani.warning.withValues(alpha: 0.14),
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: Text(
                        _secondsLeft < 0 ? 'Đang mở' : _formatCountdown(),
                        style: TextStyle(
                          fontSize: 10.5,
                          fontWeight: FontWeight.w800,
                          color: isDark ? const Color(0xFFFBBF24) : const Color(0xFFB45309),
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
              ] else if (widget.totalPendingCount > 1 && widget.onViewAll != null) ...[
                const SizedBox(width: 6),
                InkWell(
                  onTap: widget.onViewAll,
                  borderRadius: BorderRadius.circular(8),
                  child: Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 4),
                    child: Text(
                      'Xem tất cả (${widget.totalPendingCount})',
                      style: TextStyle(
                        fontSize: 11.5,
                        fontWeight: FontWeight.w800,
                        color: cani.brand,
                      ),
                    ),
                  ),
                ),
              ],
            ],
          ),
          const SizedBox(height: 8),

          // Tên sự kiện, vị trí công việc & thù lao
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      p.eventName,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        fontSize: 14,
                        fontWeight: FontWeight.w800,
                        color: cani.textMain,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      p.jobType,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        fontSize: 12,
                        fontWeight: FontWeight.w700,
                        color: cani.brand,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 10),
              Column(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Text(
                    '${_formatMoney(p.totalEstimatedPay)} đ',
                    style: TextStyle(
                      fontSize: 14.5,
                      fontWeight: FontWeight.w800,
                      color: cani.success,
                    ),
                  ),
                  Text(
                    'ước tính',
                    style: TextStyle(fontSize: 10, fontWeight: FontWeight.w600, color: cani.textMuted),
                  ),
                ],
              ),
            ],
          ),
          const SizedBox(height: 6),

          // Giờ làm & Địa điểm
          Text(
            '${_formatTimeRange(p)}${p.venueAddress.trim().isNotEmpty ? ' · ${p.venueAddress}' : ''}',
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: TextStyle(fontSize: 11.5, fontWeight: FontWeight.w600, color: cani.textBody),
          ),
          if (p.isConflicting) ...[
            const SizedBox(height: 8),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
              decoration: BoxDecoration(
                color: cani.warningBg,
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: cani.warning.withValues(alpha: 0.45)),
              ),
              child: Text(
                p.conflictReason ?? 'Bạn đã có ca nhận rồi',
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(
                  fontSize: 11,
                  fontWeight: FontWeight.w700,
                  color: cani.warning,
                ),
              ),
            ),
          ],
          const SizedBox(height: 10),

          // Nút Xem chi tiết & Nhận ca (nút Từ chối nằm trong màn chi tiết)
          Row(
            children: [
              Expanded(
                child: SizedBox(
                  height: 40,
                  child: OutlinedButton(
                    onPressed: widget.onViewDetails ?? widget.onReject,
                    style: OutlinedButton.styleFrom(
                      padding: const EdgeInsets.symmetric(horizontal: 8),
                      side: BorderSide(color: isDark ? cani.borderStrong : cani.border, width: 1.3),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                    ),
                    child: Text(
                      widget.onViewDetails != null ? 'Xem chi tiết' : 'Từ chối',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        fontSize: 12.5,
                        fontWeight: FontWeight.w800,
                        color: cani.textBody,
                      ),
                    ),
                  ),
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                flex: 1,
                child: SizedBox(
                  height: 40,
                  child: Container(
                    decoration: BoxDecoration(
                      gradient: (isExpired || p.isConflicting) ? null : LinearGradient(colors: cani.brandGradient),
                      color: p.isConflicting
                          ? cani.warningBg
                          : (isExpired ? cani.skeleton : null),
                      borderRadius: BorderRadius.circular(12),
                      border: p.isConflicting
                          ? Border.all(color: cani.warning.withValues(alpha: 0.45))
                          : null,
                    ),
                    child: ElevatedButton(
                      onPressed: (isExpired || p.isConflicting)
                          ? (p.isConflicting
                              ? () {
                                  ScaffoldMessenger.of(context).showSnackBar(
                                    SnackBar(
                                      content: Text(p.conflictReason ?? 'Bạn đã có ca nhận rồi!'),
                                      backgroundColor: cani.warning,
                                    ),
                                  );
                                }
                              : null)
                          : widget.onAccept,
                      style: ElevatedButton.styleFrom(
                        backgroundColor: Colors.transparent,
                        shadowColor: Colors.transparent,
                        padding: const EdgeInsets.symmetric(horizontal: 10),
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                      ),
                      child: Text(
                        p.isConflicting
                            ? 'Bạn đã có ca nhận rồi'
                            : (isExpired ? 'Đã hết hạn' : 'Nhận ca'),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(
                          fontSize: 12,
                          fontWeight: FontWeight.w800,
                          color: p.isConflicting ? cani.warning : Colors.white,
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

  Widget _buildFullCard(BuildContext context) {
    final p = widget.proposal;
    final cani = Theme.of(context).cani;
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Container(
      margin: const EdgeInsets.only(bottom: 14),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Theme.of(context).cardColor,
        borderRadius: BorderRadius.circular(20),
        border: isDark ? Border.all(color: const Color(0xFF334155)) : null,
        boxShadow: [
          BoxShadow(
            color: isDark
                ? Colors.black.withValues(alpha: 0.20)
                : cani.brand.withValues(alpha: 0.08),
            blurRadius: 18,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Expanded(
                child: Text(
                  p.jobType,
                  style: TextStyle(
                    fontWeight: FontWeight.w800,
                    fontSize: 15,
                    color: isDark ? const Color(0xFFF8FAFC) : cani.textMain,
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
                  p.matchedSkillNames.isNotEmpty
                      ? 'Khớp ${p.matchedSkillNames.length} kỹ năng'
                      : 'Đề xuất cho bạn',
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

          Row(
            children: [
              Expanded(
                child: Text(
                  p.eventName,
                  style: TextStyle(
                    fontSize: 12.5,
                    fontWeight: FontWeight.w700,
                    color: isDark ? const Color(0xFFCBD5E1) : const Color(0xFF1E293B),
                  ),
                ),
              ),
              if (p.eoId != null && p.eoId!.isNotEmpty)
                InkWell(
                  onTap: () => EoPublicProfileSheet.show(
                    context,
                    eoId: p.eoId!,
                    fallbackName: p.eoName,
                  ),
                  borderRadius: BorderRadius.circular(8),
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                    decoration: BoxDecoration(
                      color: cani.brandSoft,
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(
                          p.eoVerified ? Icons.verified_rounded : Icons.apartment_rounded,
                          size: 13,
                          color: cani.brand,
                        ),
                        const SizedBox(width: 4),
                        ConstrainedBox(
                          constraints: const BoxConstraints(maxWidth: 120),
                          child: Text(
                            p.eoName ?? 'Hồ sơ BTC',
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: TextStyle(fontSize: 11, fontWeight: FontWeight.w700, color: cani.brand),
                          ),
                        ),
                        Icon(Icons.chevron_right_rounded, size: 14, color: cani.brand),
                      ],
                    ),
                  ),
                ),
            ],
          ),
          const SizedBox(height: 10),

          if (p.imageUrl != null && p.imageUrl!.trim().isNotEmpty) ...[
            ClipRRect(
              borderRadius: BorderRadius.circular(12),
              child: Stack(
                children: [
                  Image.network(
                    p.imageUrl!.trim(),
                    height: 110,
                    width: double.infinity,
                    fit: BoxFit.cover,
                    errorBuilder: (ctx, err, stack) => const SizedBox.shrink(),
                  ),
                  Positioned(
                    bottom: 6,
                    left: 6,
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                      decoration: BoxDecoration(
                        color: Colors.black.withValues(alpha: 0.65),
                        borderRadius: BorderRadius.circular(6),
                      ),
                      child: const Text(
                        'Đồng phục / Ca làm',
                        style: TextStyle(color: Colors.white, fontSize: 9.5, fontWeight: FontWeight.bold),
                      ),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 10),
          ],

          Row(
            children: [
              Icon(Icons.location_on, size: 15, color: cani.brand),
              const SizedBox(width: 6),
              Expanded(
                child: Text(
                  p.venueAddress,
                  style: TextStyle(fontSize: 11.5, color: cani.textMuted, fontWeight: FontWeight.w600),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
            ],
          ),
          const SizedBox(height: 4),
          Row(
            children: [
              Icon(Icons.access_time_rounded, size: 15, color: cani.brand),
              const SizedBox(width: 6),
              Expanded(
                child: Text(
                  '${p.startTime.hour.toString().padLeft(2, '0')}:${p.startTime.minute.toString().padLeft(2, '0')} - ${p.endTime.hour.toString().padLeft(2, '0')}:${p.endTime.minute.toString().padLeft(2, '0')} (${p.startTime.day}/${p.startTime.month}/${p.startTime.year})${p.arrivalTime != null ? ' · Mở điểm danh ${p.arrivalTime!.hour.toString().padLeft(2, '0')}:${p.arrivalTime!.minute.toString().padLeft(2, '0')}' : ''}',
                  style: TextStyle(fontSize: 11.5, color: cani.textMuted, fontWeight: FontWeight.w600),
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
            ],
          ),

          if (p.recommendationReasonVi != null && p.recommendationReasonVi!.trim().isNotEmpty)
            Padding(
              padding: const EdgeInsets.only(top: 6),
              child: Text(
                p.recommendationReasonVi!,
                style: TextStyle(
                  fontSize: 11,
                  fontWeight: FontWeight.w600,
                  color: isDark ? const Color(0xFF34D399) : const Color(0xFF047857),
                ),
              ),
            ),

          if (p.requiredSkillNames.isNotEmpty || p.preferredSkillNames.isNotEmpty)
            Padding(
              padding: const EdgeInsets.only(top: 6),
              child: Wrap(
                spacing: 6,
                runSpacing: 4,
                children: [
                  ...p.requiredSkillNames.map((n) => Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                    decoration: BoxDecoration(
                      color: isDark ? const Color(0xFF1E293B) : const Color(0xFFEFF6FF),
                      borderRadius: BorderRadius.circular(8),
                      border: Border.all(color: isDark ? const Color(0xFF334155) : const Color(0xFFBFDBFE)),
                    ),
                    child: Text('Bắt buộc: $n', style: TextStyle(fontSize: 10.5, color: isDark ? Colors.lightBlueAccent : const Color(0xFF1D4ED8), fontWeight: FontWeight.w600)),
                  )),
                  ...p.preferredSkillNames.map((n) => Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                    decoration: BoxDecoration(
                      color: isDark ? const Color(0xFF1E293B) : const Color(0xFFF0FDF4),
                      borderRadius: BorderRadius.circular(8),
                      border: Border.all(color: isDark ? const Color(0xFF334155) : const Color(0xFFBBF7D0)),
                    ),
                    child: Text('Ưu tiên: $n', style: TextStyle(fontSize: 10.5, color: isDark ? Colors.greenAccent : const Color(0xFF15803D), fontWeight: FontWeight.w600)),
                  )),
                ],
              ),
            ),
          const SizedBox(height: 12),

          Divider(height: 1, color: isDark ? const Color(0xFF334155) : const Color(0xFFF0F7FF)),
          const SizedBox(height: 12),

          SizedBox(
            width: double.infinity,
            child: Wrap(
              alignment: WrapAlignment.spaceBetween,
              crossAxisAlignment: WrapCrossAlignment.center,
              spacing: 8,
              runSpacing: 6,
              children: [
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(
                      '${_formatMoney(p.totalEstimatedPay)} đ',
                      style: const TextStyle(
                        fontWeight: FontWeight.w800,
                        fontSize: 16.5,
                        color: Color(0xFF059669),
                        letterSpacing: -0.2,
                      ),
                    ),
                    Text(
                      '/ca làm việc',
                      style: TextStyle(fontSize: 9.5, fontWeight: FontWeight.w700, color: cani.textMuted),
                    ),
                  ],
                ),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                  decoration: BoxDecoration(
                    color: cani.warning.withValues(alpha: 0.12),
                    borderRadius: BorderRadius.circular(999),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const Icon(Icons.timer_outlined, size: 13, color: Color(0xFFB45309)),
                      const SizedBox(width: 4),
                      Flexible(
                        child: Text(
                          _secondsLeft < 0
                              ? 'Đang mở nhận'
                              : (_secondsLeft == 0
                                  ? 'Đã hết hạn'
                                  : 'Hết hạn sau ${_formatCountdown()}'),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            color: Color(0xFFB45309),
                            fontSize: 11,
                            fontWeight: FontWeight.w800,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 13),

          Row(
            children: [
              Expanded(
                flex: 1,
                child: SizedBox(
                  height: 44,
                  child: OutlinedButton(
                    onPressed: widget.onReject,
                    style: OutlinedButton.styleFrom(
                      side: BorderSide(
                        color: isDark ? const Color(0xFF334155) : cani.border,
                        width: 1.5,
                      ),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                    ),
                    child: Text(
                      'Từ chối',
                      style: TextStyle(
                        fontSize: 12.5,
                        fontWeight: FontWeight.w800,
                        color: isDark ? const Color(0xFFCBD5E1) : const Color(0xFF1E293B),
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
                      gradient: p.isConflicting ? null : LinearGradient(colors: cani.brandGradient),
                      color: p.isConflicting ? cani.warningBg : null,
                      borderRadius: BorderRadius.circular(14),
                      border: p.isConflicting
                          ? Border.all(color: cani.warning.withValues(alpha: 0.45))
                          : null,
                      boxShadow: p.isConflicting
                          ? null
                          : [
                              BoxShadow(
                                color: cani.brand.withValues(alpha: 0x50 / 255),
                                blurRadius: 14,
                                offset: const Offset(0, 4),
                              ),
                            ],
                    ),
                    child: ElevatedButton(
                      onPressed: p.isConflicting
                          ? () {
                              ScaffoldMessenger.of(context).showSnackBar(
                                SnackBar(
                                  content: Text(p.conflictReason ?? 'Bạn đã có ca nhận rồi!'),
                                  backgroundColor: cani.warning,
                                ),
                              );
                            }
                          : widget.onAccept,
                      style: ElevatedButton.styleFrom(
                        backgroundColor: Colors.transparent,
                        shadowColor: Colors.transparent,
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                      ),
                      child: Text(
                        p.isConflicting ? 'Bạn đã có ca nhận rồi' : 'Nhận ca ngay',
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(
                          fontSize: 12.5,
                          fontWeight: FontWeight.w800,
                          color: p.isConflicting ? cani.warning : Colors.white,
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

class CarouselPagerChip extends StatelessWidget {
  final int index;
  final int total;
  final VoidCallback? onPrev;
  final VoidCallback? onNext;

  const CarouselPagerChip({
    super.key,
    required this.index,
    required this.total,
    this.onPrev,
    this.onNext,
  });

  @override
  Widget build(BuildContext context) {
    final cani = Theme.of(context).cani;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
      decoration: BoxDecoration(
        color: cani.brandSoft,
        borderRadius: BorderRadius.circular(999),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          InkWell(
            onTap: onPrev,
            borderRadius: BorderRadius.circular(999),
            child: Padding(
              padding: const EdgeInsets.all(2),
              child: Icon(Icons.chevron_left_rounded, size: 16, color: cani.brand),
            ),
          ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 4),
            child: Text(
              '${index + 1}/$total',
              style: TextStyle(
                fontSize: 11,
                fontWeight: FontWeight.w800,
                color: cani.brand,
              ),
            ),
          ),
          InkWell(
            onTap: onNext,
            borderRadius: BorderRadius.circular(999),
            child: Padding(
              padding: const EdgeInsets.all(2),
              child: Icon(Icons.chevron_right_rounded, size: 16, color: cani.brand),
            ),
          ),
        ],
      ),
    );
  }
}
