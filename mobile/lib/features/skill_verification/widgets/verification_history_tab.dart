import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/features/skill_verification/models/skill_models.dart';
import 'package:cani/features/skill_verification/providers/skill_verification_provider.dart';

class VerificationHistoryTab extends ConsumerWidget {
  const VerificationHistoryTab({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(skillVerificationProvider);
    final theme = Theme.of(context);

    if (state.isLoading) {
      return const Center(child: CircularProgressIndicator());
    }

    final historyList = state.history;

    if (historyList.isEmpty) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(32.0),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(Icons.history_edu_outlined, size: 64, color: theme.colorScheme.outlineVariant),
              const SizedBox(height: 16),
              const Text(
                'Chưa có lịch sử thẩm định nào',
                style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
              ),
              const SizedBox(height: 8),
              const Text(
                'Các chứng chỉ hoặc hồ sơ bạn nộp thẩm định qua AI sẽ xuất hiện tại đây.',
                textAlign: TextAlign.center,
                style: TextStyle(color: Colors.grey, fontSize: 13),
              ),
            ],
          ),
        ),
      );
    }

    return RefreshIndicator(
      onRefresh: () => ref.read(skillVerificationProvider.notifier).loadAll(),
      child: ListView.separated(
        padding: const EdgeInsets.all(20),
        itemCount: historyList.length,
        separatorBuilder: (context, index) => const SizedBox(height: 14),
        itemBuilder: (context, index) {
          final item = historyList[index];
          return _buildHistoryCard(context, item);
        },
      ),
    );
  }

  Widget _buildHistoryCard(BuildContext context, SkillVerificationResult item) {
    final theme = Theme.of(context);
    final isApproved = item.status == 'APPROVED';
    final isPending = item.status == 'PENDING_REVIEW';

    final Color statusColor = isApproved
        ? Colors.green
        : isPending
            ? Colors.orange
            : Colors.red;

    final String statusText = isApproved
        ? 'Đã Phê Duyệt (+${item.spAwarded} SP)'
        : isPending
            ? 'Đang Chờ Admin Duyệt'
            : 'Đã Từ Chối';

    return Card(
      elevation: 1,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Expanded(
                  child: Text(
                    item.skillName.isNotEmpty ? item.skillName : item.jobType,
                    style: theme.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.bold),
                  ),
                ),
                Chip(
                  label: Text(statusText, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 11)),
                  backgroundColor: statusColor.withOpacity(0.12),
                  labelStyle: TextStyle(color: statusColor),
                  padding: EdgeInsets.zero,
                  visualDensity: VisualDensity.compact,
                ),
              ],
            ),
            const SizedBox(height: 8),
            if (item.institution != null && item.institution!.isNotEmpty)
              Text('Đơn vị cấp: ${item.institution}', style: const TextStyle(fontSize: 12.5, color: Colors.black87)),
            if (item.recipientName != null && item.recipientName!.isNotEmpty)
              Text('Người dùng: ${item.recipientName}', style: const TextStyle(fontSize: 12.5, color: Colors.black87)),
            const SizedBox(height: 8),
            Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: theme.colorScheme.surfaceVariant.withOpacity(0.3),
                borderRadius: BorderRadius.circular(8),
              ),
              child: Row(
                children: [
                  Icon(
                    item.hasSealOrSignature ? Icons.verified : Icons.help_outline,
                    size: 16,
                    color: item.hasSealOrSignature ? Colors.green : Colors.grey,
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      item.routingReason.isNotEmpty ? item.routingReason : 'Thẩm định qua AI Pipeline',
                      style: const TextStyle(fontSize: 12, fontStyle: FontStyle.italic),
                    ),
                  ),
                ],
              ),
            ),
            if (item.createdAt != null) ...[
              const SizedBox(height: 8),
              Align(
                alignment: Alignment.centerRight,
                child: Text(
                  'Ngày gửi: ${item.createdAt!.split("T").first}',
                  style: const TextStyle(fontSize: 11, color: Colors.grey),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
