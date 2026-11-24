import 'package:cani/app/cani_tokens.dart';
import 'package:flutter/material.dart';

import '../models/proposal_models.dart';
import 'shift_workflow_stepper.dart';

class ShiftAssignmentCard extends StatelessWidget {
  final ShiftAssignmentItem assignment;
  final VoidCallback onNavigate;
  final VoidCallback onQrScan;
  final VoidCallback onConfirmPayment;
  final VoidCallback? onOpenChecklist;
  final VoidCallback? onCancelAssignment;
  final VoidCallback? onRateOrganizer;
  final VoidCallback? onTap;

  const ShiftAssignmentCard({
    super.key,
    required this.assignment,
    required this.onNavigate,
    required this.onQrScan,
    required this.onConfirmPayment,
    this.onOpenChecklist,
    this.onCancelAssignment,
    this.onRateOrganizer,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final a = assignment;
    final cani = Theme.of(context).cani;
    final isCheckedIn = a.status == 'CHECKED_IN' || (a.checkinTime != null && a.checkoutTime == null);
    final isCompleted = a.status == 'COMPLETED' || a.checkoutTime != null;
    final isOverdueCheckout = !isCompleted && isCheckedIn && DateTime.now().isAfter(a.endTime);
    final isCancelledOrNoShow = a.status == 'CANCELLED' || a.status == 'NO_SHOW' || a.attendanceStatus == 'NO_SHOW';
    final isExpired = !isCheckedIn && !isCompleted && (isCancelledOrNoShow || DateTime.now().isAfter(a.endTime));
    final isNotStarted = !isCheckedIn && !isCompleted && !isExpired;
    final isPaymentConfirmed = isAssignmentPaymentConfirmed(a);

    final isDeclared = a.paymentStatus == 'DECLARED_PENDING_WORKER';
    final isConfirmedFull = a.paymentStatus == 'CONFIRMED_FULL';
    final isConfirmedPartial = a.paymentStatus == 'CONFIRMED_PARTIAL';
    final isDisputed = a.paymentStatus == 'DISPUTED';

    final barColor = isExpired
        ? cani.danger
        : (isCompleted
            ? const Color(0xFF7E22CE)
            : (isOverdueCheckout
                ? cani.warning
                : (isCheckedIn ? const Color(0xFF10B981) : cani.brand)));

    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Container(
      margin: const EdgeInsets.only(bottom: 14),
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
      clipBehavior: Clip.antiAlias,
      child: Stack(
        children: [
          // Vạch màu trang trí mép trái theo OpenDesign
          Positioned(
            left: 0,
            top: 0,
            bottom: 0,
            width: 4.5,
            child: Container(color: barColor),
          ),

          InkWell(
            onTap: onTap,
            child: Padding(
            padding: const EdgeInsets.fromLTRB(18, 15, 15, 15),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // Tag trạng thái
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                      decoration: BoxDecoration(
                        color: isExpired
                            ? const Color(0xFFFEF2F2)
                            : (isCompleted
                                ? const Color(0xFFF3E8FF)
                                : (isOverdueCheckout
                                    ? cani.warningBg
                                    : (isCheckedIn
                                        ? const Color(0xFF10B981).withValues(alpha: 0.12)
                                        : cani.brand.withValues(alpha: 0.10)))),
                        borderRadius: BorderRadius.circular(999),
                      ),
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Container(
                            width: 6,
                            height: 6,
                            decoration: BoxDecoration(
                              shape: BoxShape.circle,
                              color: isExpired
                                  ? cani.danger
                                  : (isCompleted
                                      ? const Color(0xFF7E22CE)
                                      : (isOverdueCheckout
                                          ? cani.warning
                                          : (isCheckedIn ? const Color(0xFF10B981) : cani.brand))),
                            ),
                          ),
                          const SizedBox(width: 6),
                          Text(
                            isExpired
                                ? (a.status == 'CANCELLED'
                                    ? 'ĐÃ HỦY'
                                    : (a.status == 'NO_SHOW' ? 'VẮNG MẶT' : 'HẾT HẠN ĐIỂM DANH'))
                                : (isCompleted
                                    ? 'ĐÃ HOÀN THÀNH'
                                    : (isOverdueCheckout
                                        ? 'QUÁ GIỜ · CHỜ KẾT CA'
                                        : (isCheckedIn ? 'ĐANG TRONG CA' : 'CHỜ CHECK-IN'))),
                            style: TextStyle(
                              fontSize: 10,
                              fontWeight: FontWeight.w800,
                              letterSpacing: 0.5,
                              color: isExpired
                                  ? cani.danger
                                  : (isCompleted
                                      ? const Color(0xFF7E22CE)
                                      : (isOverdueCheckout
                                          ? cani.warning
                                          : (isCheckedIn ? const Color(0xFF059669) : cani.brand))),
                            ),
                          ),
                        ],
                      ),
                    ),
                    InkWell(
                      onTap: onNavigate,
                      borderRadius: BorderRadius.circular(8),
                       child: Padding(
                         padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 3),
                         child: Row(
                           children: [
                             Icon(Icons.directions, size: 14, color: cani.brand),
                             const SizedBox(width: 4),
                             Text(
                               'Chỉ đường',
                               style: TextStyle(
                                 fontSize: 11.5,
                                 fontWeight: FontWeight.w700,
                                 color: cani.brand,
                               ),
                             ),
                           ],
                         ),
                       ),
                    ),
                  ],
                ),
                const SizedBox(height: 9),

                // Tên ca & Sự kiện
                Text(
                  '${a.jobType} · ${a.eventName}',
                  style: TextStyle(
                    fontWeight: FontWeight.w800,
                    fontSize: 14.5,
                     color: isDark ? const Color(0xFFF8FAFC) : cani.textMain,
                    letterSpacing: -0.1,
                  ),
                ),
                if (a.imageUrl != null && a.imageUrl!.trim().isNotEmpty) ...[
                  const SizedBox(height: 8),
                  ClipRRect(
                    borderRadius: BorderRadius.circular(10),
                    child: Image.network(
                      a.imageUrl!.trim(),
                      height: 100,
                      width: double.infinity,
                      fit: BoxFit.cover,
                      errorBuilder: (ctx, err, stack) => const SizedBox.shrink(),
                    ),
                  ),
                ],
                const SizedBox(height: 4),

                // Thời gian & Địa điểm
                Row(
                  children: [
                    Icon(Icons.access_time_rounded, size: 14, color: cani.brand),
                    const SizedBox(width: 6),
                    Expanded(
                      child: Text(
                        '${a.startTime.hour.toString().padLeft(2, '0')}:${a.startTime.minute.toString().padLeft(2, '0')} - ${a.endTime.hour.toString().padLeft(2, '0')}:${a.endTime.minute.toString().padLeft(2, '0')} · ${a.startTime.day.toString().padLeft(2, '0')}/${a.startTime.month.toString().padLeft(2, '0')} · ${a.venueAddress}',
                        style: TextStyle(
                          fontSize: 11.5,
                          color: cani.textMuted,
                          fontWeight: FontWeight.w600,
                        ),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                  ],
                ),

                // Giờ yêu cầu có mặt / mở điểm danh (nếu khác giờ bắt đầu)
                if (a.arrivalTime != null &&
                    a.startTime.difference(a.arrivalTime!).inMinutes.abs() >= 1)
                  Padding(
                    padding: const EdgeInsets.only(top: 4),
                    child: Row(
                      children: [
                        Icon(Icons.fact_check_rounded, size: 14, color: isDark ? Colors.amberAccent : Colors.amber.shade800),
                        const SizedBox(width: 6),
                        Expanded(
                          child: Text(
                            a.arrivalTime!.isBefore(a.startTime)
                                ? 'Có mặt từ ${a.arrivalTime!.hour.toString().padLeft(2, '0')}:${a.arrivalTime!.minute.toString().padLeft(2, '0')} (trước ca ${a.startTime.difference(a.arrivalTime!).inMinutes}p)'
                                : 'Mở điểm danh từ ${a.arrivalTime!.hour.toString().padLeft(2, '0')}:${a.arrivalTime!.minute.toString().padLeft(2, '0')}',
                            style: TextStyle(fontSize: 11, color: isDark ? Colors.amberAccent : Colors.amber.shade800, fontWeight: FontWeight.w600),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                      ],
                    ),
                  ),

                // Kỹ năng chính & kỹ năng bắt buộc (chips)
                if (a.confirmedPerformedSkillName != null || a.performedSkillName != null || a.requiredSkillNames.isNotEmpty)
                  Padding(
                    padding: const EdgeInsets.only(top: 6),
                    child: Wrap(
                      spacing: 6,
                      runSpacing: 4,
                      children: [
                        if ((a.confirmedPerformedSkillName ?? a.performedSkillName) != null)
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                            decoration: BoxDecoration(
                              color: isDark ? const Color(0xFF0F2922) : const Color(0xFFECFDF5),
                              borderRadius: BorderRadius.circular(8),
                              border: Border.all(color: isDark ? const Color(0xFF065F46) : const Color(0xFFA7F3D0)),
                            ),
                            child: Text(
                              'Kỹ năng chính: ${a.confirmedPerformedSkillName ?? a.performedSkillName}',
                              style: TextStyle(
                                fontSize: 11,
                                color: isDark ? Colors.greenAccent : const Color(0xFF047857),
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                          ),
                        ...a.requiredSkillNames.map((n) => Container(
                          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                          decoration: BoxDecoration(
                            color: isDark ? const Color(0xFF1E293B) : const Color(0xFFEFF6FF),
                            borderRadius: BorderRadius.circular(8),
                            border: Border.all(color: isDark ? const Color(0xFF334155) : const Color(0xFFBFDBFE)),
                          ),
                          child: Text(n, style: TextStyle(fontSize: 11, color: isDark ? Colors.lightBlueAccent : const Color(0xFF1D4ED8), fontWeight: FontWeight.w600)),
                        )),
                      ],
                    ),
                  ),

                // Nút Chi tiết & Checklist công việc nổi bật nhất (48px Brand Gradient) khi ca còn hiệu lực
                if (!isCompleted && !isExpired && (onOpenChecklist != null || onTap != null)) ...[
                  const SizedBox(height: 12),
                  SizedBox(
                    width: double.infinity,
                    height: 48,
                    child: Container(
                      decoration: BoxDecoration(
                        gradient: LinearGradient(
                          colors: cani.brandGradient,
                          begin: Alignment.centerLeft,
                          end: Alignment.centerRight,
                        ),
                        borderRadius: BorderRadius.circular(14),
                        boxShadow: [
                          BoxShadow(
                            color: cani.brand.withValues(alpha: 0.24),
                            blurRadius: 12,
                            offset: const Offset(0, 4),
                          ),
                        ],
                      ),
                      child: ElevatedButton.icon(
                        onPressed: onOpenChecklist ?? onTap,
                        icon: const Icon(
                          Icons.fact_check_rounded,
                          size: 19,
                          color: Colors.white,
                        ),
                        label: const Text(
                          'Chi tiết & Checklist công việc',
                          style: TextStyle(
                            fontSize: 13.5,
                            fontWeight: FontWeight.w800,
                            color: Colors.white,
                          ),
                        ),
                        style: ElevatedButton.styleFrom(
                          backgroundColor: Colors.transparent,
                          shadowColor: Colors.transparent,
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                        ),
                      ),
                    ),
                  ),
                ],

                // Hàng nút phụ: Quét QR Check-in / Thanh toán / Check-out (hoặc Chi tiết nếu đã kết thúc/hết hạn), Hủy nhận ca, Đánh giá BTC
                if ((!isCompleted && !isExpired) ||
                    ((isCompleted || isExpired) && onOpenChecklist != null) ||
                    (isNotStarted && onCancelAssignment != null) ||
                    (isCompleted && onRateOrganizer != null && a.hasWorkerReviewed != true)) ...[
                  const SizedBox(height: 8),
                  Row(
                    children: [
                      if (!isCompleted && !isExpired)
                        Expanded(
                          child: TextButton.icon(
                            onPressed: !isCheckedIn
                                ? onQrScan
                                : (!a.eoConfirmed
                                    ? (onOpenChecklist ?? onTap)
                                    : (!isPaymentConfirmed ? onConfirmPayment : onQrScan)),
                            icon: Icon(
                              !isCheckedIn || (a.eoConfirmed && isPaymentConfirmed)
                                  ? Icons.qr_code_scanner_rounded
                                  : (!a.eoConfirmed ? Icons.hourglass_top_rounded : Icons.payments_outlined),
                              size: 16,
                              color: cani.brand,
                            ),
                            label: Text(
                              !isCheckedIn
                                  ? 'Quét QR Check-in'
                                  : (!a.eoConfirmed
                                      ? 'Chờ BTC duyệt công'
                                      : (!isPaymentConfirmed
                                          ? 'Thanh toán thù lao (Bước 4)'
                                          : 'Quét QR Check-out (Bước 5)')),
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: cani.brand),
                            ),
                            style: TextButton.styleFrom(
                              backgroundColor: cani.brandSoft,
                              padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 6),
                              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                            ),
                          ),
                        )
                      else if (onOpenChecklist != null)
                        Expanded(
                          child: TextButton.icon(
                            onPressed: onOpenChecklist,
                            icon: Icon(Icons.fact_check_outlined, size: 16, color: cani.brand),
                            label: Text(
                              'Chi tiết & Đầu việc',
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: cani.brand),
                            ),
                            style: TextButton.styleFrom(
                              backgroundColor: cani.brandSoft,
                              padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 6),
                              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                            ),
                          ),
                        ),
                      if (((!isCompleted && !isExpired) || ((isCompleted || isExpired) && onOpenChecklist != null)) &&
                          ((isNotStarted && onCancelAssignment != null) ||
                              (isCompleted && onRateOrganizer != null && a.hasWorkerReviewed != true)))
                        const SizedBox(width: 8),
                      if (isNotStarted && onCancelAssignment != null)
                        Expanded(
                          child: TextButton.icon(
                            onPressed: onCancelAssignment,
                            icon: Icon(Icons.cancel_outlined, size: 16, color: cani.danger),
                            label: Text(
                              'Hủy nhận ca',
                              style: TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: cani.danger),
                            ),
                            style: TextButton.styleFrom(
                              backgroundColor: isDark
                                  ? const Color(0xFF7F1D1D).withValues(alpha: 0.35)
                                  : const Color(0xFFFEF2F2),
                              padding: const EdgeInsets.symmetric(vertical: 8),
                              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                            ),
                          ),
                        ),
                      if (isCompleted && onRateOrganizer != null && a.hasWorkerReviewed != true)
                        Expanded(
                          child: TextButton.icon(
                            onPressed: onRateOrganizer,
                            icon: const Icon(Icons.star_rounded, size: 17, color: Color(0xFFD97706)),
                            label: const Text(
                              'Đánh giá BTC',
                              style: TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: Color(0xFFD97706)),
                            ),
                            style: TextButton.styleFrom(
                              backgroundColor: isDark
                                  ? const Color(0xFF78350F).withValues(alpha: 0.35)
                                  : const Color(0xFFFEF3C7),
                              padding: const EdgeInsets.symmetric(vertical: 8),
                              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                            ),
                          ),
                        ),
                    ],
                  ),
                ],

                const SizedBox(height: 12),

                // Khung Thù Lao & Nút Xác Nhận (CANi Clean Surface)
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 9),
                  decoration: BoxDecoration(
                    color: isDark ? cani.canvas : const Color(0xFFF8FAFC),
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: cani.border),
                  ),
                  child: Row(
                    children: [
                      Icon(
                        Icons.payments_outlined,
                        size: 16,
                        color: isConfirmedFull ? cani.success : cani.brand,
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text.rich(
                          TextSpan(
                            text: isDeclared
                                ? (a.declaredAmount > 0 && a.declaredAmount < a.totalPay
                                    ? 'BTC khai báo: '
                                    : 'BTC đã thanh toán: ')
                                : 'Thù lao: ',
                            style: TextStyle(fontSize: 11.5, color: cani.textMuted, fontWeight: FontWeight.w600),
                            children: [
                              TextSpan(
                                text: isDeclared && a.declaredAmount > 0 && a.declaredAmount < a.totalPay
                                    ? '${a.declaredAmount.toInt().toString().replaceAllMapped(RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'), (m) => '${m[1]}.')} đ / ${a.totalPay.toInt().toString().replaceAllMapped(RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'), (m) => '${m[1]}.')} đ'
                                    : '${(isDeclared && a.declaredAmount > 0 ? a.declaredAmount : a.totalPay).toInt().toString().replaceAllMapped(RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'), (m) => '${m[1]}.')} đ',
                                style: TextStyle(fontWeight: FontWeight.w800, color: cani.textMain),
                              ),
                              if (isDeclared && a.paymentMethod != null)
                                TextSpan(
                                  text: ' · ${a.paymentMethod == 'BANK_TRANSFER' ? 'CK' : 'Tiền mặt'}',
                                  style: TextStyle(
                                    fontSize: 10.5,
                                    color: cani.textMuted,
                                  ),
                                ),
                            ],
                          ),
                        ),
                      ),
                      const SizedBox(width: 6),
                      if (isConfirmedFull)
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                          decoration: BoxDecoration(
                            color: cani.successBg,
                            borderRadius: BorderRadius.circular(999),
                          ),
                          child: Text(
                            'Đã nhận đủ',
                            style: TextStyle(color: cani.success, fontSize: 10.5, fontWeight: FontWeight.w700),
                          ),
                        )
                      else if (isConfirmedPartial)
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                          decoration: BoxDecoration(
                            color: cani.warningBg,
                            borderRadius: BorderRadius.circular(999),
                          ),
                          child: Text(
                            'Nhận 1 phần',
                            style: TextStyle(color: cani.warning, fontSize: 10.5, fontWeight: FontWeight.w700),
                          ),
                        )
                      else if (isDisputed)
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                          decoration: BoxDecoration(
                            color: cani.dangerBg,
                            borderRadius: BorderRadius.circular(999),
                          ),
                          child: Text(
                            'Đang khiếu nại',
                            style: TextStyle(color: cani.danger, fontSize: 10.5, fontWeight: FontWeight.w700),
                          ),
                        )
                      else if (isDeclared)
                        InkWell(
                          onTap: onConfirmPayment,
                          borderRadius: BorderRadius.circular(10),
                          child: Container(
                            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                            decoration: BoxDecoration(
                              color: cani.brandSoft,
                              borderRadius: BorderRadius.circular(10),
                              border: Border.all(color: cani.brand.withValues(alpha: 0.35)),
                            ),
                            child: Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                Icon(Icons.fact_check_outlined, size: 13, color: cani.brand),
                                const SizedBox(width: 4),
                                Text(
                                  'Xác nhận',
                                  style: TextStyle(color: cani.brand, fontSize: 10.5, fontWeight: FontWeight.w800),
                                ),
                              ],
                            ),
                          ),
                        )
                      else
                        Text(
                          'Trả sau ca',
                          style: TextStyle(
                            fontSize: 10.5,
                            color: cani.textMuted,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                    ],
                  ),
                ),
              ],
            ),
          ),
          ),
        ],
      ),
    );
  }
}
