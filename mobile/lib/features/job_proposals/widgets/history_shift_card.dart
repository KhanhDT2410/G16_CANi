import 'package:flutter/material.dart';
import 'package:cani/app/cani_tokens.dart';
import '../models/proposal_models.dart';

class HistoryShiftCard extends StatelessWidget {
  final ShiftAssignmentItem assignment;
  final VoidCallback onTap;
  final VoidCallback onConfirmPayment;
  final VoidCallback onRateOrganizer;

  const HistoryShiftCard({
    super.key,
    required this.assignment,
    required this.onTap,
    required this.onConfirmPayment,
    required this.onRateOrganizer,
  });

  static String _formatVnd(num amount) {
    final int val = amount.toInt();
    return '${val.toString().replaceAllMapped(RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'), (m) => '${m[1]}.')} đ';
  }

  static String _formatTime(DateTime dt) {
    return '${dt.hour.toString().padLeft(2, '0')}:${dt.minute.toString().padLeft(2, '0')}';
  }

  static String _formatDate(DateTime dt) {
    return '${dt.day.toString().padLeft(2, '0')}/${dt.month.toString().padLeft(2, '0')}/${dt.year}';
  }

  @override
  Widget build(BuildContext context) {
    final a = assignment;
    final cani = Theme.of(context).cani;
    final isDark = Theme.of(context).brightness == Brightness.dark;

    final isCompleted = a.status == 'COMPLETED';
    final isCancelled = a.status == 'CANCELLED';
    final isNoShow = a.status == 'NO_SHOW' || a.attendanceStatus == 'NO_SHOW';

    final isDeclared = a.paymentStatus == 'DECLARED_PENDING_WORKER';
    final isConfirmedFull = a.paymentStatus == 'CONFIRMED_FULL';
    final isDisputed = a.paymentStatus == 'DISPUTED';

    final displayAmount = (a.declaredAmount > 0) ? a.declaredAmount : a.totalPay;

    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      decoration: BoxDecoration(
        color: Theme.of(context).cardColor,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: isDark ? const Color(0xFF334155) : const Color(0xFFE2E8F0),
        ),
        boxShadow: [
          BoxShadow(
            color: isDark ? Colors.black.withValues(alpha: 0.12) : cani.brand.withValues(alpha: 0.03),
            blurRadius: 8,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      clipBehavior: Clip.antiAlias,
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: onTap,
          child: Padding(
            padding: const EdgeInsets.all(14),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // 1. Header: Trạng thái ca & Ngày làm việc
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    _buildShiftStatusBadge(isCompleted, isCancelled, isNoShow),
                    Text(
                      _formatDate(a.startTime),
                      style: TextStyle(
                        fontSize: 12,
                        fontWeight: FontWeight.w600,
                        color: isDark ? const Color(0xFF94A3B8) : const Color(0xFF64748B),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 10),

                // 2. Tên sự kiện & Vị trí
                Text(
                  a.eventName,
                  style: TextStyle(
                    fontSize: 15,
                    fontWeight: FontWeight.w700,
                    color: isDark ? Colors.white : const Color(0xFF0F172A),
                    letterSpacing: -0.2,
                  ),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
                const SizedBox(height: 4),
                Row(
                  children: [
                    Icon(Icons.work_outline_rounded, size: 14, color: cani.brand),
                    const SizedBox(width: 4),
                    Text(
                      a.jobType,
                      style: TextStyle(
                        fontSize: 12.5,
                        fontWeight: FontWeight.w600,
                        color: cani.brand,
                      ),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Text(
                        '${_formatTime(a.startTime)} - ${_formatTime(a.endTime)}',
                        style: TextStyle(
                          fontSize: 12,
                          color: isDark ? const Color(0xFF94A3B8) : const Color(0xFF64748B),
                        ),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 10),

                // 3. Thông tin thực tế & Thù lao quyết toán
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
                  decoration: BoxDecoration(
                    color: isDark ? const Color(0xFF1E293B) : const Color(0xFFF8FAFC),
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            isCompleted
                                ? (a.hoursWorked != null && a.hoursWorked! > 0
                                    ? 'Thực làm: ${a.hoursWorked!.toStringAsFixed(1)} giờ'
                                    : 'Đã hoàn thành ca')
                                : (isCancelled ? 'Ca đã bị hủy' : 'Vắng mặt'),
                            style: TextStyle(
                              fontSize: 11.5,
                              fontWeight: FontWeight.w500,
                              color: isDark ? const Color(0xFFCBD5E1) : const Color(0xFF475569),
                            ),
                          ),
                          const SizedBox(height: 2),
                          _buildPaymentBadge(a, isConfirmedFull, isDeclared, isDisputed),
                        ],
                      ),
                      Text(
                        _formatVnd(displayAmount),
                        style: TextStyle(
                          fontSize: 14.5,
                          fontWeight: FontWeight.w800,
                          color: isConfirmedFull
                              ? const Color(0xFF10B981)
                              : (isDeclared ? const Color(0xFFD97706) : cani.brand),
                        ),
                      ),
                    ],
                  ),
                ),
                if (a.eoRating != null && a.eoRating! > 0) ...[
                  const SizedBox(height: 8),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                    decoration: BoxDecoration(
                      color: isDark ? const Color(0xFF1E293B) : const Color(0xFFF0FDF4),
                      borderRadius: BorderRadius.circular(8),
                      border: Border.all(color: const Color(0xFF86EFAC).withValues(alpha: 0.5)),
                    ),
                    child: Row(
                      children: [
                        const Icon(Icons.star_rounded, size: 14, color: Color(0xFFF59E0B)),
                        const SizedBox(width: 4),
                        Text('${a.eoRating}★', style: const TextStyle(fontSize: 11.5, fontWeight: FontWeight.bold, color: Color(0xFF15803D))),
                        const SizedBox(width: 6),
                        Expanded(
                          child: Text(
                            a.eoReviewComment != null && a.eoReviewComment!.isNotEmpty
                                ? 'BTC: "${a.eoReviewComment}"'
                                : 'Ban tổ chức đã chấm điểm ca này.',
                            style: TextStyle(
                              fontSize: 11,
                              fontStyle: FontStyle.italic,
                              color: isDark ? const Color(0xFFCBD5E1) : const Color(0xFF166534),
                            ),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
                const SizedBox(height: 10),

                // 4. Action bar: Đánh giá, Xác nhận tiền, Chi tiết
                Row(
                  mainAxisAlignment: MainAxisAlignment.end,
                  children: [
                    if (isDeclared) ...[
                      OutlinedButton(
                        onPressed: onConfirmPayment,
                        style: OutlinedButton.styleFrom(
                          foregroundColor: const Color(0xFFD97706),
                          side: const BorderSide(color: Color(0xFFF59E0B)),
                          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                          minimumSize: const Size(0, 32),
                        ),
                        child: const Text('Xác nhận tiền', style: TextStyle(fontSize: 11.5, fontWeight: FontWeight.w700)),
                      ),
                      const SizedBox(width: 8),
                    ],
                    if (isCompleted && a.hasWorkerReviewed != true) ...[
                      OutlinedButton(
                        onPressed: onRateOrganizer,
                        style: OutlinedButton.styleFrom(
                          foregroundColor: cani.brand,
                          side: BorderSide(color: cani.brand.withValues(alpha: 0.5)),
                          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                          minimumSize: const Size(0, 32),
                        ),
                        child: const Text('Đánh giá BTC', style: TextStyle(fontSize: 11.5, fontWeight: FontWeight.w600)),
                      ),
                      const SizedBox(width: 8),
                    ],
                    TextButton(
                      onPressed: onTap,
                      style: TextButton.styleFrom(
                        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                        minimumSize: const Size(0, 32),
                      ),
                      child: const Text('Chi tiết', style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600)),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildShiftStatusBadge(bool isCompleted, bool isCancelled, bool isNoShow) {
    Color bg;
    Color fg;
    String label;

    if (isCompleted) {
      bg = const Color(0xFFECFDF5);
      fg = const Color(0xFF059669);
      label = 'Hoàn thành';
    } else if (isCancelled) {
      bg = const Color(0xFFFEF2F2);
      fg = const Color(0xFFDC2626);
      label = 'Đã hủy';
    } else if (isNoShow) {
      bg = const Color(0xFFFEF2F2);
      fg = const Color(0xFFDC2626);
      label = 'Vắng mặt';
    } else {
      bg = const Color(0xFFF1F5F9);
      fg = const Color(0xFF64748B);
      label = 'Kết thúc';
    }

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(color: bg, borderRadius: BorderRadius.circular(6)),
      child: Text(label, style: TextStyle(fontSize: 11, fontWeight: FontWeight.w700, color: fg)),
    );
  }

  Widget _buildPaymentBadge(ShiftAssignmentItem a, bool isFull, bool isDeclared, bool isDisputed) {
    if (isFull) {
      return const Text('Đã nhận đủ thù lao', style: TextStyle(fontSize: 11, color: Color(0xFF059669), fontWeight: FontWeight.w600));
    }
    if (isDeclared) {
      return const Text('Chờ bạn xác nhận nhận tiền', style: TextStyle(fontSize: 11, color: Color(0xFFD97706), fontWeight: FontWeight.w600));
    }
    if (isDisputed) {
      return const Text('Đang khiếu nại thù lao', style: TextStyle(fontSize: 11, color: Color(0xFFDC2626), fontWeight: FontWeight.w600));
    }
    return const Text('Chờ ban tổ chức quyết toán', style: TextStyle(fontSize: 11, color: Color(0xFF64748B)));
  }
}
