import 'package:flutter/material.dart';

import 'package:cani/app/cani_tokens.dart';
import '../models/dispute_models.dart';

/// Thẻ tóm tắt hồ sơ khiếu nại & tranh chấp của Worker.
/// Ưu tiên hiển thị tiêu đề, ca/sự kiện liên quan, trạng thái và hạn xử lý (SLA).
/// Chi tiết mô tả dài, minh chứng và phán quyết đầy đủ được mở qua "Xem chi tiết".
class DisputeCard extends StatelessWidget {
  final WorkerDisputeItem item;
  final VoidCallback onViewDetail;
  final VoidCallback onOpenChat;

  const DisputeCard({
    super.key,
    required this.item,
    required this.onViewDetail,
    required this.onOpenChat,
  });

  static Color statusColorFor(String status, CaniTokens cani) {
    switch (status.trim().toUpperCase()) {
      case 'OPEN':
      case 'PENDING':
        return cani.warning;
      case 'INVESTIGATING':
      case 'IN_REVIEW':
        return cani.brand;
      case 'ESCALATED':
      case 'APPEAL_REQUESTED':
      case 'APPEALING':
      case 'UNDER_APPEAL':
        return const Color(0xFF8B5CF6);
      case 'RESOLVED':
      case 'FINAL_RESOLVED':
        return cani.success;
      case 'UNVERIFIED':
      case 'REJECTED':
        return cani.danger;
      default:
        return cani.textMuted;
    }
  }

  static String formatDate(DateTime dt) {
    final l = dt.toLocal();
    return '${l.day.toString().padLeft(2, '0')}/${l.month.toString().padLeft(2, '0')}/${l.year}';
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final cani = theme.cani;
    final isDark = theme.brightness == Brightness.dark;
    final statusColor = statusColorFor(item.status, cani);
    final canChat = item.isOpen;
    final chatLabel = canChat ? 'Trao đổi với CSKH' : 'Xem trao đổi';
    final shiftContextParts = <String>[
      if (item.shiftTitle != null && item.shiftTitle!.trim().isNotEmpty) item.shiftTitle!.trim(),
      if (item.eventTitle != null && item.eventTitle!.trim().isNotEmpty) item.eventTitle!.trim(),
      if (item.shiftStartTime != null) formatDate(item.shiftStartTime!),
    ];

    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: cani.surface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: cani.border),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: isDark ? 0.16 : 0.03),
            blurRadius: 10,
            offset: const Offset(0, 3),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // 1. Nhãn phân loại & Trạng thái hồ sơ
          Row(
            children: [
              Flexible(
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                  decoration: BoxDecoration(
                    color: cani.brandSoft,
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Text(
                    item.typeLabel,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      fontSize: 11,
                      fontWeight: FontWeight.w700,
                      color: cani.brand,
                    ),
                  ),
                ),
              ),
              const SizedBox(width: 8),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 4),
                decoration: BoxDecoration(
                  color: statusColor.withValues(alpha: isDark ? 0.18 : 0.12),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Container(
                      width: 6,
                      height: 6,
                      decoration: BoxDecoration(
                        color: statusColor,
                        shape: BoxShape.circle,
                      ),
                    ),
                    const SizedBox(width: 5),
                    Text(
                      item.statusLabel,
                      style: TextStyle(
                        fontSize: 11,
                        fontWeight: FontWeight.w800,
                        color: statusColor,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),

          // 2. Tiêu đề hồ sơ
          Text(
            item.title,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: TextStyle(
              fontSize: 14.5,
              fontWeight: FontWeight.w800,
              color: cani.textMain,
              height: 1.3,
            ),
          ),

          // 3. Ca hoặc sự kiện liên quan
          if (shiftContextParts.isNotEmpty) ...[
            const SizedBox(height: 8),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
              decoration: BoxDecoration(
                color: cani.canvas,
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: cani.border),
              ),
              child: Row(
                children: [
                  Icon(Icons.event_note_rounded, size: 14, color: cani.textMuted),
                  const SizedBox(width: 6),
                  Expanded(
                    child: Text(
                      shiftContextParts.join(' · '),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        fontSize: 11.5,
                        fontWeight: FontWeight.w600,
                        color: cani.textBody,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ],

          // 4. Hạn xử lý (nếu có) & Tóm tắt kết quả / ngày gửi
          const SizedBox(height: 10),
          Wrap(
            spacing: 12,
            runSpacing: 4,
            crossAxisAlignment: WrapCrossAlignment.center,
            children: [
              if (item.slaDeadline != null)
                Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(Icons.schedule_rounded, size: 13.5, color: cani.brand),
                    const SizedBox(width: 4),
                    Text(
                      'Hạn xử lý: ${formatDate(item.slaDeadline!)}',
                      style: TextStyle(
                        fontSize: 11.5,
                        fontWeight: FontWeight.w700,
                        color: cani.brand,
                      ),
                    ),
                  ],
                ),
              if (item.createdAt != null)
                Text(
                  'Đã gửi: ${formatDate(item.createdAt!)}',
                  style: TextStyle(
                    fontSize: 11.2,
                    color: cani.textMuted,
                    fontWeight: FontWeight.w500,
                  ),
                ),
              if (item.hasResult && item.outcomeLabel.isNotEmpty)
                Text(
                  'Kết quả: ${item.outcomeLabel}',
                  style: TextStyle(
                    fontSize: 11.2,
                    fontWeight: FontWeight.w700,
                    color: cani.success,
                  ),
                ),
            ],
          ),

          const SizedBox(height: 12),

          // 5. Cụm nút thao tác: Xem chi tiết & Trao đổi với CSKH / Xem trao đổi
          Row(
            children: [
              Expanded(
                child: OutlinedButton.icon(
                  onPressed: onViewDetail,
                  icon: Icon(Icons.article_outlined, size: 15, color: cani.textBody),
                  label: Text(
                    'Xem chi tiết',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.w700,
                      color: cani.textMain,
                    ),
                  ),
                  style: OutlinedButton.styleFrom(
                    side: BorderSide(color: cani.borderStrong),
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 9),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(10),
                    ),
                  ),
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: OutlinedButton.icon(
                  onPressed: onOpenChat,
                  icon: Icon(
                    canChat ? Icons.forum_outlined : Icons.history_rounded,
                    size: 15,
                    color: cani.brand,
                  ),
                  label: Text(
                    chatLabel,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.w700,
                      color: cani.brand,
                    ),
                  ),
                  style: OutlinedButton.styleFrom(
                    backgroundColor: cani.brandSoft,
                    side: BorderSide(color: cani.brand.withValues(alpha: 0.35)),
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 9),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(10),
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
