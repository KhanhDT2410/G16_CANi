import 'package:flutter/material.dart';

import '../models/proposal_models.dart';

class ShiftAssignmentCard extends StatelessWidget {
  final ShiftAssignmentItem assignment;
  final VoidCallback onNavigate;
  final VoidCallback onQrScan;
  final void Function(String status) onConfirmPayment;

  const ShiftAssignmentCard({
    super.key,
    required this.assignment,
    required this.onNavigate,
    required this.onQrScan,
    required this.onConfirmPayment,
  });

  @override
  Widget build(BuildContext context) {
    final a = assignment;
    final isCheckedIn = a.status == 'CHECKED_IN' || (a.checkinTime != null && a.checkoutTime == null);
    final isCompleted = a.status == 'COMPLETED' || a.checkoutTime != null;
    final isNotStarted = !isCheckedIn && !isCompleted;

    final isDeclared = a.paymentStatus == 'DECLARED_PENDING_WORKER';
    final isConfirmed = a.paymentStatus == 'CONFIRMED_FULL';

    return Container(
      margin: const EdgeInsets.only(bottom: 14),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: isCheckedIn ? const Color(0xFF10B981).withValues(alpha: 0.6) : const Color(0xFFE2E8F0),
          width: isCheckedIn ? 1.5 : 1.0,
        ),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.04),
            blurRadius: 10,
            offset: const Offset(0, 3),
          ),
        ],
      ),
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Header: Tên ca & Badge trạng thái
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      a.jobType,
                      style: const TextStyle(
                        fontWeight: FontWeight.w800,
                        fontSize: 16,
                        color: Color(0xFF0F172A),
                      ),
                    ),
                    const SizedBox(height: 3),
                    Text(
                      a.eventName,
                      style: const TextStyle(
                        fontWeight: FontWeight.w600,
                        fontSize: 13.5,
                        color: Color(0xFF0066FF),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 8),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                decoration: BoxDecoration(
                  color: isCompleted
                      ? const Color(0xFFF3E8FF)
                      : (isCheckedIn ? const Color(0xFFD1FAE5) : const Color(0xFFE0F2FE)),
                  borderRadius: BorderRadius.circular(20),
                  border: Border.all(
                    color: isCompleted
                        ? const Color(0xFFD8B4FE)
                        : (isCheckedIn ? const Color(0xFF6EE7B7) : const Color(0xFFBAE6FD)),
                  ),
                ),
                child: Text(
                  isCompleted
                      ? 'ĐÃ HOÀN THÀNH'
                      : (isCheckedIn ? 'ĐANG TRONG CA' : 'CHỜ CHECK-IN'),
                  style: TextStyle(
                    color: isCompleted
                        ? const Color(0xFF7E22CE)
                        : (isCheckedIn ? const Color(0xFF065F46) : const Color(0xFF0369A1)),
                    fontWeight: FontWeight.w800,
                    fontSize: 10.5,
                    letterSpacing: 0.3,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),

          // Địa chỉ & Thời gian
          Row(
            children: [
              const Icon(Icons.location_on_outlined, size: 15, color: Color(0xFF64748B)),
              const SizedBox(width: 5),
              Expanded(
                child: Text(
                  a.venueAddress,
                  style: const TextStyle(fontSize: 12.5, color: Color(0xFF475569)),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
            ],
          ),
          const SizedBox(height: 6),
          Row(
            children: [
              const Icon(Icons.access_time_rounded, size: 15, color: Color(0xFF64748B)),
              const SizedBox(width: 5),
              Text(
                '${a.startTime.hour.toString().padLeft(2, '0')}:${a.startTime.minute.toString().padLeft(2, '0')} - ${a.endTime.hour.toString().padLeft(2, '0')}:${a.endTime.minute.toString().padLeft(2, '0')} • ${a.startTime.day}/${a.startTime.month}/${a.startTime.year}',
                style: const TextStyle(fontSize: 12.5, color: Color(0xFF475569), fontWeight: FontWeight.w500),
              ),
            ],
          ),
          const SizedBox(height: 8),

          // Thù lao & giờ làm
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                'Thù lao: ${a.totalPay.toInt().toString().replaceAllMapped(RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'), (m) => '${m[1]}.')} đ',
                style: const TextStyle(
                  fontWeight: FontWeight.w800,
                  color: Color(0xFF16A34A),
                  fontSize: 14.5,
                ),
              ),
              if (a.hoursWorked != null)
                Text(
                  'Đã làm: ${a.hoursWorked!.toStringAsFixed(1)}h',
                  style: const TextStyle(fontSize: 12.5, fontWeight: FontWeight.w700, color: Color(0xFF0D9488)),
                ),
            ],
          ),

          // Lịch sử Check-in / Out nếu có
          if (a.checkinTime != null || a.checkoutTime != null) ...[
            const SizedBox(height: 8),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
              decoration: BoxDecoration(
                color: const Color(0xFFF8FAFC),
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: const Color(0xFFE2E8F0)),
              ),
              child: Row(
                children: [
                  const Icon(Icons.schedule, size: 14, color: Color(0xFF64748B)),
                  const SizedBox(width: 6),
                  Expanded(
                    child: Text(
                      '${a.checkinTime != null ? "Vào ca: ${a.checkinTime!.hour.toString().padLeft(2, '0')}:${a.checkinTime!.minute.toString().padLeft(2, '0')}" : ""}${a.checkoutTime != null ? " | Ra ca: ${a.checkoutTime!.hour.toString().padLeft(2, '0')}:${a.checkoutTime!.minute.toString().padLeft(2, '0')}" : ""}',
                      style: const TextStyle(fontSize: 11.5, color: Color(0xFF334155), fontWeight: FontWeight.w600),
                    ),
                  ),
                ],
              ),
            ),
          ],

          const SizedBox(height: 14),

          // Hàng nút hành động: Chỉ đường & Quét QR
          Row(
            children: [
              Expanded(
                flex: 4,
                child: OutlinedButton.icon(
                  onPressed: onNavigate,
                  icon: const Icon(Icons.directions, size: 17, color: Color(0xFF0066FF)),
                  label: const Text('Chỉ đường', style: TextStyle(fontSize: 12.5, fontWeight: FontWeight.w700, color: Color(0xFF0066FF))),
                  style: OutlinedButton.styleFrom(
                    padding: const EdgeInsets.symmetric(vertical: 10),
                    side: const BorderSide(color: Color(0xFF0066FF)),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                  ),
                ),
              ),
              const SizedBox(width: 10),
              if (isNotStarted)
                Expanded(
                  flex: 5,
                  child: FilledButton.icon(
                    onPressed: onQrScan,
                    icon: const Icon(Icons.qr_code_scanner, size: 17),
                    label: const Text('QR Check-in', style: TextStyle(fontSize: 12.5, fontWeight: FontWeight.w700)),
                    style: FilledButton.styleFrom(
                      backgroundColor: const Color(0xFF0D9488),
                      padding: const EdgeInsets.symmetric(vertical: 10),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                    ),
                  ),
                )
              else if (isCheckedIn)
                Expanded(
                  flex: 5,
                  child: FilledButton.icon(
                    onPressed: onQrScan,
                    icon: const Icon(Icons.exit_to_app, size: 17),
                    label: const Text('QR Check-out', style: TextStyle(fontSize: 12.5, fontWeight: FontWeight.w700)),
                    style: FilledButton.styleFrom(
                      backgroundColor: const Color(0xFFEA580C),
                      padding: const EdgeInsets.symmetric(vertical: 10),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                    ),
                  ),
                ),
            ],
          ),

          const Divider(height: 22, color: Color(0xFFE2E8F0)),

          // Khung xác nhận thù lao (BR-12 / A-20)
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: isConfirmed
                  ? const Color(0xFFF0FDF4)
                  : (isDeclared ? const Color(0xFFFFFBEB) : const Color(0xFFF8FAFC)),
              borderRadius: BorderRadius.circular(10),
              border: Border.all(
                color: isConfirmed
                    ? const Color(0xFF86EFAC)
                    : (isDeclared ? const Color(0xFFFDE68A) : const Color(0xFFE2E8F0)),
              ),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    const Text(
                      'Thanh toán thù lao:',
                      style: TextStyle(fontWeight: FontWeight.w700, fontSize: 12, color: Color(0xFF1E293B)),
                    ),
                    Text(
                      isConfirmed
                          ? '✓ Đã nhận đủ tiền'
                          : (isDeclared ? 'Ban tổ chức đã thanh toán' : 'Chờ hoàn thành ca'),
                      style: TextStyle(
                        color: isConfirmed
                            ? const Color(0xFF16A34A)
                            : (isDeclared ? const Color(0xFFD97706) : const Color(0xFF64748B)),
                        fontWeight: FontWeight.w800,
                        fontSize: 11.5,
                      ),
                    ),
                  ],
                ),
                if (isDeclared) ...[
                  const SizedBox(height: 6),
                  Text(
                    'Đã chuyển: ${a.declaredAmount.toInt().toString().replaceAllMapped(RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'), (m) => '${m[1]}.')} đ (${a.paymentMethod == 'BANK_TRANSFER' ? 'Chuyển khoản' : 'Tiền mặt'}). Vui lòng kiểm tra và xác nhận:',
                    style: const TextStyle(fontSize: 11.5, color: Color(0xFF475569)),
                  ),
                  const SizedBox(height: 8),
                  Row(
                    children: [
                      Expanded(
                        child: FilledButton.tonal(
                          onPressed: () => onConfirmPayment('CONFIRMED_FULL'),
                          style: FilledButton.styleFrom(
                            backgroundColor: const Color(0xFFDCFCE7),
                            foregroundColor: const Color(0xFF166534),
                            visualDensity: VisualDensity.compact,
                          ),
                          child: const Text('✓ Đã nhận đủ', style: TextStyle(fontSize: 11.5, fontWeight: FontWeight.w700)),
                        ),
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: OutlinedButton(
                          onPressed: () => onConfirmPayment('NOT_RECEIVED'),
                          style: OutlinedButton.styleFrom(
                            visualDensity: VisualDensity.compact,
                            side: const BorderSide(color: Color(0xFFEF4444)),
                          ),
                          child: const Text('✕ Chưa nhận', style: TextStyle(fontSize: 11.5, fontWeight: FontWeight.w700, color: Color(0xFFEF4444))),
                        ),
                      ),
                    ],
                  ),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }
}
