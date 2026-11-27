import 'package:flutter/material.dart';

import 'package:cani/app/cani_tokens.dart';
import '../models/dispute_models.dart';
import 'dispute_card.dart';
import 'shift_checklist_section.dart';

/// Bảng chi tiết hồ sơ khiếu nại & tranh chấp.
/// Hiển thị đầy đủ nội dung mô tả, danh sách minh chứng (kèm xem ảnh phóng to)
/// và phán quyết/quyết định từ CSKH hoặc Admin.
class DisputeDetailSheet extends StatelessWidget {
  final WorkerDisputeItem item;
  final VoidCallback onOpenChat;

  const DisputeDetailSheet({
    super.key,
    required this.item,
    required this.onOpenChat,
  });

  static Future<void> show(
    BuildContext context, {
    required WorkerDisputeItem item,
    required VoidCallback onOpenChat,
  }) {
    return showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => DisputeDetailSheet(
        item: item,
        onOpenChat: onOpenChat,
      ),
    );
  }

  bool _looksLikeImageUrl(String url) {
    final trimmed = url.trim().toLowerCase();
    return trimmed.startsWith('/api/') ||
        trimmed.startsWith('http://') ||
        trimmed.startsWith('https://') ||
        trimmed.startsWith('data:image/');
  }

  void _showEvidenceImageDialog(BuildContext context, String imageUrl, String title) {
    final cani = Theme.of(context).cani;
    showDialog<void>(
      context: context,
      builder: (ctx) => Dialog(
        backgroundColor: cani.surface,
        insetPadding: const EdgeInsets.all(16),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Row(
                children: [
                  Expanded(
                    child: Text(
                      title,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        fontSize: 14,
                        fontWeight: FontWeight.w800,
                        color: cani.textMain,
                      ),
                    ),
                  ),
                  IconButton(
                    onPressed: () => Navigator.pop(ctx),
                    icon: Icon(Icons.close_rounded, color: cani.textMuted),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              ClipRRect(
                borderRadius: BorderRadius.circular(12),
                child: InteractiveViewer(
                  child: buildResolvedImageWidget(
                    imageUrl,
                    height: 320,
                    width: double.infinity,
                    fit: BoxFit.contain,
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final cani = theme.cani;
    final isDark = theme.brightness == Brightness.dark;
    final statusColor = DisputeCard.statusColorFor(item.status, cani);
    final evidences = item.allEvidences;
    final decisionText = item.effectiveResolutionText;
    final canChat = item.isOpen;
    final chatLabel = canChat ? 'Trao đổi với CSKH' : 'Xem trao đổi';

    final shiftContextParts = <String>[
      if (item.shiftTitle != null && item.shiftTitle!.trim().isNotEmpty) item.shiftTitle!.trim(),
      if (item.eventTitle != null && item.eventTitle!.trim().isNotEmpty) item.eventTitle!.trim(),
      if (item.shiftStartTime != null) DisputeCard.formatDate(item.shiftStartTime!),
    ];

    return SafeArea(
      top: false,
      child: Container(
        constraints: BoxConstraints(
          maxHeight: MediaQuery.sizeOf(context).height * 0.86,
        ),
        decoration: BoxDecoration(
          color: cani.surface,
          borderRadius: const BorderRadius.vertical(top: Radius.circular(22)),
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const SizedBox(height: 10),
            Container(
              width: 40,
              height: 4,
              decoration: BoxDecoration(
                color: cani.borderStrong,
                borderRadius: BorderRadius.circular(99),
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 12, 8, 8),
              child: Row(
                children: [
                  Expanded(
                    child: Text(
                      'Chi tiết hồ sơ khiếu nại',
                      style: TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.w800,
                        color: cani.textMain,
                      ),
                    ),
                  ),
                  IconButton(
                    onPressed: () => Navigator.pop(context),
                    icon: Icon(Icons.close_rounded, color: cani.textMuted),
                  ),
                ],
              ),
            ),
            Divider(height: 1, color: cani.border),
            Flexible(
              child: SingleChildScrollView(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    // 1. Nhãn loại & Trạng thái
                    Wrap(
                      spacing: 8,
                      runSpacing: 6,
                      children: [
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 4),
                          decoration: BoxDecoration(
                            color: cani.brandSoft,
                            borderRadius: BorderRadius.circular(8),
                          ),
                          child: Text(
                            item.typeLabel,
                            style: TextStyle(
                              fontSize: 11.5,
                              fontWeight: FontWeight.w700,
                              color: cani.brand,
                            ),
                          ),
                        ),
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 4),
                          decoration: BoxDecoration(
                            color: statusColor.withValues(alpha: isDark ? 0.2 : 0.12),
                            borderRadius: BorderRadius.circular(8),
                          ),
                          child: Text(
                            item.statusLabel,
                            style: TextStyle(
                              fontSize: 11.5,
                              fontWeight: FontWeight.w800,
                              color: statusColor,
                            ),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 12),

                    // 2. Tiêu đề
                    Text(
                      item.title,
                      style: TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.w800,
                        color: cani.textMain,
                        height: 1.35,
                      ),
                    ),

                    // 3. Ca làm & Thời hạn xử lý
                    const SizedBox(height: 12),
                    Container(
                      width: double.infinity,
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: cani.canvas,
                        borderRadius: BorderRadius.circular(12),
                        border: Border.all(color: cani.border),
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          if (shiftContextParts.isNotEmpty)
                            Row(
                              children: [
                                Icon(Icons.event_note_rounded, size: 15, color: cani.brand),
                                const SizedBox(width: 6),
                                Expanded(
                                  child: Text(
                                    shiftContextParts.join(' · '),
                                    style: TextStyle(
                                      fontSize: 12.5,
                                      fontWeight: FontWeight.w700,
                                      color: cani.textMain,
                                    ),
                                  ),
                                ),
                              ],
                            ),
                          if (item.respondentName != null && item.respondentName!.trim().isNotEmpty) ...[
                            const SizedBox(height: 6),
                            Text(
                              'Đối tác liên quan: ${item.respondentName}',
                              style: TextStyle(fontSize: 12, color: cani.textBody),
                            ),
                          ],
                          if (item.createdAt != null) ...[
                            const SizedBox(height: 6),
                            Text(
                              'Ngày gửi hồ sơ: ${DisputeCard.formatDate(item.createdAt!)}',
                              style: TextStyle(fontSize: 12, color: cani.textMuted),
                            ),
                          ],
                          if (item.slaDeadline != null) ...[
                            const SizedBox(height: 6),
                            Text(
                              'Hạn xử lý: ${DisputeCard.formatDate(item.slaDeadline!)}',
                              style: TextStyle(
                                fontSize: 12,
                                fontWeight: FontWeight.w700,
                                color: cani.brand,
                              ),
                            ),
                          ],
                        ],
                      ),
                    ),

                    // 4. Mô tả chi tiết
                    const SizedBox(height: 16),
                    Text(
                      'Nội dung phản ánh',
                      style: TextStyle(
                        fontSize: 13.5,
                        fontWeight: FontWeight.w800,
                        color: cani.textMain,
                      ),
                    ),
                    const SizedBox(height: 6),
                    Container(
                      width: double.infinity,
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: cani.canvas,
                        borderRadius: BorderRadius.circular(12),
                        border: Border.all(color: cani.border),
                      ),
                      child: Text(
                        item.description.trim().isNotEmpty
                            ? item.description
                            : 'Không có mô tả chi tiết.',
                        style: TextStyle(
                          fontSize: 13,
                          color: cani.textBody,
                          height: 1.45,
                        ),
                      ),
                    ),

                    // 5. Minh chứng đính kèm
                    const SizedBox(height: 16),
                    Text(
                      'Minh chứng đính kèm (${evidences.length})',
                      style: TextStyle(
                        fontSize: 13.5,
                        fontWeight: FontWeight.w800,
                        color: cani.textMain,
                      ),
                    ),
                    const SizedBox(height: 8),
                    if (evidences.isEmpty)
                      Container(
                        width: double.infinity,
                        padding: const EdgeInsets.all(12),
                        decoration: BoxDecoration(
                          color: cani.canvas,
                          borderRadius: BorderRadius.circular(12),
                          border: Border.all(color: cani.border),
                        ),
                        child: Text(
                          'Chưa có minh chứng đính kèm.',
                          style: TextStyle(fontSize: 12.5, color: cani.textMuted),
                        ),
                      )
                    else
                      ...evidences.map((ev) {
                        final url = ev.fileUrl;
                        final isImage = _looksLikeImageUrl(url);
                        return Padding(
                          padding: const EdgeInsets.only(bottom: 10),
                          child: isImage
                              ? InkWell(
                                  onTap: () => _showEvidenceImageDialog(
                                    context,
                                    url,
                                    ev.label ?? item.title,
                                  ),
                                  borderRadius: BorderRadius.circular(12),
                                  child: Column(
                                    crossAxisAlignment: CrossAxisAlignment.start,
                                    children: [
                                      ClipRRect(
                                        borderRadius: BorderRadius.circular(12),
                                        child: buildResolvedImageWidget(
                                          url,
                                          height: 150,
                                          width: double.infinity,
                                          fit: BoxFit.cover,
                                          errorText: null,
                                        ),
                                      ),
                                      const SizedBox(height: 4),
                                      Row(
                                        children: [
                                          Icon(Icons.zoom_in_rounded, size: 15, color: cani.brand),
                                          const SizedBox(width: 4),
                                          Expanded(
                                            child: Text(
                                              ev.label?.trim().isNotEmpty == true
                                                  ? ev.label!
                                                  : 'Chạm để xem ảnh minh chứng rõ hơn',
                                              style: TextStyle(
                                                fontSize: 11.5,
                                                fontWeight: FontWeight.w600,
                                                color: cani.brand,
                                              ),
                                            ),
                                          ),
                                        ],
                                      ),
                                    ],
                                  ),
                                )
                              : Container(
                                  padding: const EdgeInsets.all(10),
                                  decoration: BoxDecoration(
                                    color: cani.canvas,
                                    borderRadius: BorderRadius.circular(10),
                                    border: Border.all(color: cani.border),
                                  ),
                                  child: Row(
                                    children: [
                                      Icon(Icons.attachment_rounded, size: 16, color: cani.brand),
                                      const SizedBox(width: 8),
                                      Expanded(
                                        child: Text(
                                          'Bằng chứng: $url',
                                          style: TextStyle(fontSize: 12.5, color: cani.textBody),
                                        ),
                                      ),
                                    ],
                                  ),
                                ),
                        );
                      }),

                    // 6. Lý do kháng nghị (nếu đang kháng nghị)
                    if (item.escalationReason != null && item.escalationReason!.trim().isNotEmpty) ...[
                      const SizedBox(height: 8),
                      Container(
                        width: double.infinity,
                        padding: const EdgeInsets.all(12),
                        decoration: BoxDecoration(
                          color: cani.warningBg,
                          borderRadius: BorderRadius.circular(12),
                          border: Border.all(color: cani.warning.withValues(alpha: 0.35)),
                        ),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              'Nội dung kháng nghị lên Admin',
                              style: TextStyle(
                                fontSize: 12.5,
                                fontWeight: FontWeight.w800,
                                color: cani.warning,
                              ),
                            ),
                            const SizedBox(height: 4),
                            Text(
                              item.escalationReason!,
                              style: TextStyle(fontSize: 12.5, color: cani.textMain, height: 1.4),
                            ),
                          ],
                        ),
                      ),
                    ],

                    // 7. Quyết định / Kết quả phân xử
                    if (decisionText != null || item.outcomeLabel.isNotEmpty) ...[
                      const SizedBox(height: 12),
                      Container(
                        width: double.infinity,
                        padding: const EdgeInsets.all(12),
                        decoration: BoxDecoration(
                          color: cani.successBg,
                          borderRadius: BorderRadius.circular(12),
                          border: Border.all(color: cani.success.withValues(alpha: 0.35)),
                        ),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Row(
                              children: [
                                Icon(Icons.verified_rounded, size: 16, color: cani.success),
                                const SizedBox(width: 6),
                                Expanded(
                                  child: Text(
                                    item.outcomeLabel.isNotEmpty
                                        ? 'Quyết định: ${item.outcomeLabel}'
                                        : 'Quyết định phân xử',
                                    style: TextStyle(
                                      fontSize: 13,
                                      fontWeight: FontWeight.w800,
                                      color: cani.success,
                                    ),
                                  ),
                                ),
                              ],
                            ),
                            if (decisionText != null) ...[
                              const SizedBox(height: 6),
                              Text(
                                decisionText,
                                style: TextStyle(
                                  fontSize: 12.8,
                                  color: cani.textMain,
                                  height: 1.45,
                                ),
                              ),
                            ],
                          ],
                        ),
                      ),
                    ],
                  ],
                ),
              ),
            ),
            Container(
              padding: const EdgeInsets.fromLTRB(16, 10, 16, 14),
              decoration: BoxDecoration(
                color: cani.surface,
                border: Border(top: BorderSide(color: cani.border)),
              ),
              child: SizedBox(
                width: double.infinity,
                height: 46,
                child: FilledButton.icon(
                  onPressed: () {
                    Navigator.pop(context);
                    onOpenChat();
                  },
                  icon: Icon(
                    canChat ? Icons.forum_outlined : Icons.history_rounded,
                    size: 17,
                  ),
                  label: Text(
                    chatLabel,
                    style: const TextStyle(fontSize: 13.5, fontWeight: FontWeight.w800),
                  ),
                  style: FilledButton.styleFrom(
                    backgroundColor: cani.brand,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12),
                    ),
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
