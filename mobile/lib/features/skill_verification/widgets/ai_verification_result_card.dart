import 'package:flutter/material.dart';

import 'package:cani/features/skill_verification/models/skill_models.dart';

class AiVerificationResultCard extends StatelessWidget {
  final SkillVerificationResult result;

  const AiVerificationResultCard({
    super.key,
    required this.result,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isAutoApprove = result.routingDecision == 'AUTO_APPROVE';
    final isAdminReview = result.routingDecision == 'ADMIN_REVIEW';

    final Color statusColor = isAutoApprove
        ? Colors.green
        : isAdminReview
            ? Colors.orange
            : Colors.red;

    final String statusText = isAutoApprove
        ? 'TỰ ĐỘNG PHÊ DUYỆT (AUTO_APPROVE)'
        : isAdminReview
            ? 'CHỜ ADMIN DUYỆT (ADMIN_REVIEW)'
            : 'TỪ CHỐI (REJECT)';

    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: statusColor.withOpacity(0.06),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: statusColor.withOpacity(0.4), width: 1.5),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(
                isAutoApprove ? Icons.verified : Icons.admin_panel_settings,
                color: statusColor,
                size: 28,
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Text(
                  'Kết Quả Thẩm Định Vision AI',
                  style: theme.textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.bold,
                    color: statusColor,
                  ),
                ),
              ),
              Chip(
                label: Text(statusText, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 11)),
                backgroundColor: statusColor.withOpacity(0.15),
                labelStyle: TextStyle(color: statusColor),
              ),
            ],
          ),
          const Divider(height: 24),

          // Điểm thưởng & Hạng bậc
          Row(
            children: [
              Expanded(
                child: _buildMetricTile(
                  label: 'Điểm SP Thưởng',
                  value: '+${result.spAwarded} SP',
                  valueColor: statusColor,
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: _buildMetricTile(
                  label: 'Độ Tin Cậy AI',
                  value: '${(result.overallConfidence * 100).toStringAsFixed(0)}%',
                  valueColor: Colors.blueAccent,
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: _buildMetricTile(
                  label: 'Hạng Kỹ Năng',
                  value: result.currentTier,
                  valueColor: Colors.purple,
                ),
              ),
            ],
          ),

          const SizedBox(height: 16),

          // Chi tiết bóc tách
          if (result.recipientName != null || result.institution != null) ...[
            Text('Thông tin trích xuất từ tài liệu:', style: theme.textTheme.labelMedium?.copyWith(fontWeight: FontWeight.bold)),
            const SizedBox(height: 8),
            if (result.recipientName != null)
              Text('• Người dùng: ${result.recipientName}', style: const TextStyle(fontSize: 13)),
            if (result.institution != null)
              Text('• Đơn vị cấp bằng: ${result.institution}', style: const TextStyle(fontSize: 13)),
            if (result.issueDate != null)
              Text('• Ngày cấp: ${result.issueDate}', style: const TextStyle(fontSize: 13)),
            Text(
              '• Con dấu / Chữ ký xác nhận: ${result.hasSealOrSignature ? "Đã phát hiện ✓" : "Không phát hiện"}',
              style: TextStyle(fontSize: 13, color: result.hasSealOrSignature ? Colors.green : Colors.grey),
            ),
            const SizedBox(height: 12),
          ],

          // Kỹ năng Taxonomy khớp
          if (result.extractedSkills.isNotEmpty) ...[
            Text('Kỹ năng đối chiếu chuẩn Taxonomy CANi:', style: theme.textTheme.labelMedium?.copyWith(fontWeight: FontWeight.bold)),
            const SizedBox(height: 6),
            ...result.extractedSkills.map(
              (s) => Container(
                margin: const EdgeInsets.only(bottom: 6),
                padding: const EdgeInsets.all(8),
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Row(
                  children: [
                    const Icon(Icons.check_circle_outline, size: 16, color: Colors.green),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        '${s.skillName} -> Vai trò: ${s.taxonomyRole}',
                        style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 13),
                      ),
                    ),
                    Text(
                      '${(s.confidenceScore * 100).toStringAsFixed(0)}%',
                      style: const TextStyle(fontWeight: FontWeight.bold, color: Colors.blueAccent),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 12),
          ],

          // Lý do từ AI
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: Colors.white.withOpacity(0.8),
              borderRadius: BorderRadius.circular(8),
            ),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Icon(Icons.info_outline, size: 16, color: Colors.blueGrey),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    result.routingReason,
                    style: const TextStyle(fontSize: 13, fontStyle: FontStyle.italic),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildMetricTile({
    required String label,
    required String value,
    required Color valueColor,
  }) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: Colors.grey.shade200),
      ),
      child: Column(
        children: [
          Text(label, style: const TextStyle(fontSize: 12, color: Colors.grey), textAlign: TextAlign.center),
          const SizedBox(height: 4),
          Text(
            value,
            style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: valueColor),
            textAlign: TextAlign.center,
          ),
        ],
      ),
    );
  }
}
