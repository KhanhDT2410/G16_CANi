import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/features/help_center/models/help_center_models.dart';
import 'package:cani/features/help_center/providers/help_center_providers.dart';

class MyTicketsCard extends ConsumerWidget {
  final VoidCallback onViewAll;
  final VoidCallback onCreateTicket;
  final ValueChanged<SupportTicketModel> onSelectTicket;

  const MyTicketsCard({
    super.key,
    required this.onViewAll,
    required this.onCreateTicket,
    required this.onSelectTicket,
  });

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final ticketsAsync = ref.watch(myTicketsProvider);
    final pendingCount = ref.watch(pendingTicketsCountProvider);

    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: const Color(0xFFE2E8F0)),
        boxShadow: const [
          BoxShadow(
            color: Color(0x06000000),
            blurRadius: 6,
            offset: Offset(0, 2),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Tiêu đề & Nút Xem tất cả
          Row(
            children: [
              const Icon(Icons.confirmation_number_outlined, color: Color(0xFF0066FF), size: 18),
              const SizedBox(width: 8),
              const Flexible(
                child: Text(
                  'Yêu cầu hỗ trợ của tôi',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.w800,
                    color: Color(0xFF0F172A),
                  ),
                ),
              ),
              if (pendingCount > 0) ...[
                const SizedBox(width: 6),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                  decoration: BoxDecoration(
                    color: const Color(0xFFEFF6FF),
                    borderRadius: BorderRadius.circular(10),
                    border: Border.all(color: const Color(0xFFBFDBFE)),
                  ),
                  child: Text(
                    '$pendingCount đang xử lý',
                    style: const TextStyle(
                      fontSize: 10.5,
                      fontWeight: FontWeight.w700,
                      color: Color(0xFF0066FF),
                    ),
                  ),
                ),
              ],
              const Spacer(),
              InkWell(
                onTap: onViewAll,
                borderRadius: BorderRadius.circular(6),
                child: const Padding(
                  padding: EdgeInsets.symmetric(horizontal: 4, vertical: 2),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Text(
                        'Xem tất cả',
                        style: TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: Color(0xFF0066FF)),
                      ),
                      Icon(Icons.chevron_right_rounded, size: 16, color: Color(0xFF0066FF)),
                    ],
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),

          // Tóm tắt ticket gần nhất hoặc placeholder
          ticketsAsync.when(
            data: (tickets) {
              if (tickets.isEmpty) {
                return Container(
                  width: double.infinity,
                  padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 12),
                  decoration: BoxDecoration(
                    color: const Color(0xFFF8FAFC),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: const Text(
                    'Hiện tại bạn không có yêu cầu khiếu nại hoặc hỗ trợ nào đang mở.',
                    style: TextStyle(fontSize: 12, color: Color(0xFF64748B)),
                  ),
                );
              }
              final latest = tickets.first;
              return InkWell(
                onTap: () => onSelectTicket(latest),
                borderRadius: BorderRadius.circular(8),
                child: Container(
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(
                    color: const Color(0xFFF8FAFC),
                    borderRadius: BorderRadius.circular(8),
                    border: Border.all(color: const Color(0xFFE2E8F0)),
                  ),
                  child: Row(
                    children: [
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              latest.referenceNo,
                              style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w700, color: Color(0xFF64748B)),
                            ),
                            const SizedBox(height: 2),
                            Text(
                              latest.subject,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(fontSize: 12.5, fontWeight: FontWeight.w700, color: Color(0xFF0F172A)),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(width: 8),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                        decoration: BoxDecoration(
                          color: latest.statusBgColor,
                          borderRadius: BorderRadius.circular(6),
                        ),
                        child: Text(
                          latest.statusLabel,
                          style: TextStyle(
                            fontSize: 11,
                            fontWeight: FontWeight.w700,
                            color: latest.statusColor,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              );
            },
            loading: () => const Center(
              child: Padding(
                padding: EdgeInsets.all(12),
                child: SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2)),
              ),
            ),
            error: (err, stack) => const SizedBox.shrink(),
          ),
          const SizedBox(height: 10),

          // Nút tạo yêu cầu mới
          SizedBox(
            width: double.infinity,
            height: 36,
            child: OutlinedButton.icon(
              onPressed: onCreateTicket,
              icon: const Icon(Icons.add_rounded, size: 16, color: Color(0xFF0066FF)),
              label: const Text(
                'Gửi yêu cầu hỗ trợ mới',
                style: TextStyle(fontSize: 12.5, fontWeight: FontWeight.w700, color: Color(0xFF0066FF)),
              ),
              style: OutlinedButton.styleFrom(
                backgroundColor: const Color(0xFFEFF6FF),
                side: const BorderSide(color: Color(0xFFBFDBFE)),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
