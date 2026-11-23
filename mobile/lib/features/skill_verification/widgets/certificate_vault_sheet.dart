import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/features/skill_verification/models/skill_models.dart';
import 'package:cani/features/skill_verification/providers/skill_verification_provider.dart';
import 'skill_hub_theme.dart';

class CertificateVaultSheet extends ConsumerWidget {
  const CertificateVaultSheet({super.key});

  static Future<void> show(BuildContext context) {
    return showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (context) => const CertificateVaultSheet(),
    );
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(skillVerificationProvider);
    final historyList = state.history;

    return Container(
      height: MediaQuery.of(context).size.height * 0.75,
      decoration: const BoxDecoration(
        color: SkillHubColors.canvas,
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      padding: const EdgeInsets.fromLTRB(20, 12, 20, 20),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Drag handle
          Center(
            child: Container(
              width: 38,
              height: 4,
              decoration: BoxDecoration(
                color: SkillHubColors.borderSoft,
                borderRadius: BorderRadius.circular(2),
              ),
            ),
          ),
          const SizedBox(height: 14),

          // Header
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              const Text(
                'Kho Chứng Chỉ & Lịch Sử',
                style: TextStyle(
                  fontSize: 16,
                  fontWeight: FontWeight.w800,
                  color: SkillHubColors.textPrimary,
                ),
              ),
              IconButton(
                icon: const Icon(Icons.close, size: 20),
                onPressed: () => Navigator.of(context).pop(),
              ),
            ],
          ),
          const SizedBox(height: 10),

          // List of certificates
          Expanded(
            child: historyList.isEmpty
                ? const Center(
                    child: Text(
                      'Chưa có chứng chỉ nào trong kho của bạn.\nNhấn "Tải chứng chỉ" để gửi hồ sơ xác minh đầu tiên!',
                      textAlign: TextAlign.center,
                      style: TextStyle(color: SkillHubColors.textSecondary, fontSize: 13, height: 1.5),
                    ),
                  )
                : ListView.separated(
                    itemCount: historyList.length,
                    separatorBuilder: (context, index) => const SizedBox(height: 12),
                    itemBuilder: (context, index) {
                      final item = historyList[index];
                      return _buildVaultCard(item);
                    },
                  ),
          ),
        ],
      ),
    );
  }

  Widget _buildVaultCard(SkillVerificationResult item) {
    final statusUpper = item.status.toUpperCase();
    final isApproved = statusUpper == 'APPROVED';
    final isRevoked = statusUpper == 'REVOKED';
    final isExpired = statusUpper == 'EXPIRED';
    final isNeedsInfo = statusUpper == 'NEEDS_MORE_INFO';
    final isFailed = statusUpper == 'VERIFICATION_FAILED';
    final isLegacyPending = statusUpper == 'PENDING_REVIEW';
    final isUnsupported = !isApproved &&
        !isRevoked &&
        !isExpired &&
        !isNeedsInfo &&
        !isFailed &&
        (item.verificationSource == 'UNSUPPORTED_SOURCE' ||
            item.routingReason.contains('Chưa thể xác minh tự động'));
    final statusColor = isApproved
        ? SkillHubColors.successText
        : ((isFailed || isNeedsInfo || isLegacyPending) ? SkillHubColors.warningText : SkillHubColors.dangerText);
    final statusText = isApproved
        ? 'Đã xác minh (+${item.spAwarded} SP)'
        : (isRevoked
            ? 'Đã thu hồi'
            : (isExpired
                ? 'Hết hạn'
                : (isNeedsInfo
                    ? 'Cần nộp lại'
                    : (isFailed
                        ? 'Lỗi tạm thời — thử lại'
                        : (isLegacyPending
                            ? 'Chưa xác minh tự động'
                            : (isUnsupported ? 'Chưa thể xác minh tự động' : 'Không hợp lệ'))))));

    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: SkillHubColors.surface,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: SkillHubColors.borderSoft),
        boxShadow: const [
          BoxShadow(
            color: Color(0x0803045E),
            blurRadius: 8,
            offset: Offset(0, 2),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Expanded(
                child: Text(
                  item.skillName.isNotEmpty ? item.skillName : item.jobType,
                  style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 13.5, color: SkillHubColors.textPrimary),
                ),
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: statusColor.withValues(alpha: 0.12),
                  borderRadius: BorderRadius.circular(6),
                ),
                child: Text(
                  statusText,
                  style: TextStyle(fontSize: 10.5, fontWeight: FontWeight.w700, color: statusColor),
                ),
              ),
            ],
          ),
          const SizedBox(height: 6),
          if (item.institution != null && item.institution!.isNotEmpty)
            Text(
              'Đơn vị cấp: ${item.institution}',
              style: const TextStyle(fontSize: 11.5, color: SkillHubColors.textSecondary),
            ),
          const SizedBox(height: 4),
          Text(
            item.routingReason.isNotEmpty
                ? item.routingReason
                : 'Chưa thể xác minh tự động. Vui lòng nộp chứng chỉ có nguồn tra cứu hợp lệ.',
            style: const TextStyle(fontSize: 11, fontStyle: FontStyle.italic, color: Colors.grey),
          ),
          if (item.createdAt != null) ...[
            const SizedBox(height: 6),
            Align(
              alignment: Alignment.centerRight,
              child: Text(
                'Đã nộp: ${item.createdAt!.split("T").first}',
                style: const TextStyle(fontSize: 10.5, color: Colors.grey),
              ),
            ),
          ],
        ],
      ),
    );
  }
}
