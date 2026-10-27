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

    final barColor = isCompleted
        ? const Color(0xFF7E22CE)
        : (isCheckedIn ? const Color(0xFF10B981) : const Color(0xFF0077FF));

    return Container(
      margin: const EdgeInsets.only(bottom: 14),
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

          Padding(
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
                        color: isCompleted
                            ? const Color(0xFFF3E8FF)
                            : (isCheckedIn
                                ? const Color(0xFF10B981).withValues(alpha: 0.12)
                                : const Color(0xFF0077FF).withValues(alpha: 0.10)),
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
                              color: isCompleted
                                  ? const Color(0xFF7E22CE)
                                  : (isCheckedIn ? const Color(0xFF10B981) : const Color(0xFF0077FF)),
                            ),
                          ),
                          const SizedBox(width: 6),
                          Text(
                            isCompleted
                                ? 'ĐÃ HOÀN THÀNH'
                                : (isCheckedIn ? 'ĐANG TRONG CA' : 'CHỜ CHECK-IN'),
                            style: TextStyle(
                              fontSize: 10,
                              fontWeight: FontWeight.w800,
                              letterSpacing: 0.5,
                              color: isCompleted
                                  ? const Color(0xFF7E22CE)
                                  : (isCheckedIn ? const Color(0xFF059669) : const Color(0xFF0077FF)),
                            ),
                          ),
                        ],
                      ),
                    ),
                    InkWell(
                      onTap: onNavigate,
                      borderRadius: BorderRadius.circular(8),
                      child: const Padding(
                        padding: EdgeInsets.symmetric(horizontal: 6, vertical: 3),
                        child: Row(
                          children: [
                            Icon(Icons.directions, size: 14, color: Color(0xFF0066FF)),
                            SizedBox(width: 4),
                            Text(
                              'Chỉ đường',
                              style: TextStyle(
                                fontSize: 11.5,
                                fontWeight: FontWeight.w700,
                                color: Color(0xFF0066FF),
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
                  style: const TextStyle(
                    fontWeight: FontWeight.w800,
                    fontSize: 14.5,
                    color: Color(0xFF0B1B3D),
                    letterSpacing: -0.1,
                  ),
                ),
                const SizedBox(height: 4),

                // Thời gian & Địa điểm
                Row(
                  children: [
                    const Icon(Icons.access_time_rounded, size: 14, color: Color(0xFF0077FF)),
                    const SizedBox(width: 6),
                    Expanded(
                      child: Text(
                        '${a.startTime.hour.toString().padLeft(2, '0')}:${a.startTime.minute.toString().padLeft(2, '0')} - ${a.endTime.hour.toString().padLeft(2, '0')}:${a.endTime.minute.toString().padLeft(2, '0')} · ${a.venueAddress}',
                        style: const TextStyle(fontSize: 11.5, color: Color(0xFF64748B), fontWeight: FontWeight.w600),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                  ],
                ),

                // Nút Quét QR Lớn cao 52px màu Teal nếu ca đang chạy hoặc chờ
                if (!isCompleted) ...[
                  const SizedBox(height: 12),
                  SizedBox(
                    width: double.infinity,
                    height: 50,
                    child: Container(
                      decoration: BoxDecoration(
                        gradient: const LinearGradient(
                          colors: [Color(0xFF0D9488), Color(0xFF0F766E)],
                        ),
                        borderRadius: BorderRadius.circular(14),
                        boxShadow: const [
                          BoxShadow(
                            color: Color(0x550D9488),
                            blurRadius: 16,
                            offset: Offset(0, 5),
                          ),
                        ],
                      ),
                      child: ElevatedButton.icon(
                        onPressed: onQrScan,
                        icon: const Icon(Icons.qr_code_scanner, size: 19, color: Colors.white),
                        label: Text(
                          isCheckedIn ? 'Quét QR Check-out Thực Địa' : 'Quét QR Check-in Thực Địa',
                          style: const TextStyle(
                            fontSize: 13,
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

                const SizedBox(height: 12),

                // Khung Thù Lao & Nút Xác Nhận
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 11, vertical: 9),
                  decoration: BoxDecoration(
                    color: const Color(0xFF10B981).withValues(alpha: 0.08),
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: const Color(0xFF10B981).withValues(alpha: 0.30)),
                  ),
                  child: Row(
                    children: [
                      const Icon(Icons.monetization_on_outlined, size: 16, color: Color(0xFF059669)),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text.rich(
                          TextSpan(
                            text: isDeclared ? 'Ban tổ chức đã thanh toán: ' : 'Thù lao: ',
                            style: const TextStyle(fontSize: 11, color: Color(0xFF065F46), fontWeight: FontWeight.w600),
                            children: [
                              TextSpan(
                                text: '${a.totalPay.toInt().toString().replaceAllMapped(RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'), (m) => '${m[1]}.')} đ',
                                style: const TextStyle(fontWeight: FontWeight.w800, color: Color(0xFF059669)),
                              ),
                              if (isDeclared)
                                TextSpan(
                                  text: ' (${a.paymentMethod == 'BANK_TRANSFER' ? 'Chuyển khoản' : 'Tiền mặt'})',
                                  style: const TextStyle(fontSize: 10.5, color: Color(0xFF065F46)),
                                ),
                            ],
                          ),
                        ),
                      ),
                      const SizedBox(width: 6),
                      if (isConfirmed)
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                          decoration: BoxDecoration(
                            color: const Color(0xFF059669),
                            borderRadius: BorderRadius.circular(10),
                          ),
                          child: const Text(
                            'Đã nhận đủ ✓',
                            style: TextStyle(color: Colors.white, fontSize: 10.5, fontWeight: FontWeight.w800),
                          ),
                        )
                      else if (isDeclared)
                        InkWell(
                          onTap: () => onConfirmPayment('CONFIRMED_FULL'),
                          borderRadius: BorderRadius.circular(10),
                          child: Container(
                            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                            decoration: BoxDecoration(
                              color: Colors.white,
                              borderRadius: BorderRadius.circular(10),
                              border: Border.all(color: const Color(0xFF10B981)),
                            ),
                            child: const Text(
                              'Xác nhận nhận tiền',
                              style: TextStyle(color: Color(0xFF059669), fontSize: 10.5, fontWeight: FontWeight.w800),
                            ),
                          ),
                        )
                      else
                        Text(
                          'Sẽ trả sau ca',
                          style: TextStyle(fontSize: 10.5, color: Colors.grey.shade600, fontWeight: FontWeight.w700),
                        ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
