import 'package:flutter/material.dart';
import '../models/ekyc_models.dart';

/// EkycResultCard - Card hiển thị chi tiết kết quả thẩm định eKYC và OCR
class EkycResultCard extends StatelessWidget {
  final EkycVerificationItem item;

  const EkycResultCard({
    super.key,
    required this.item,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Card(
      elevation: 2,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Icon(Icons.analytics_outlined, color: Colors.blueAccent),
                const SizedBox(width: 8),
                const Text(
                  'Chi Tiết Xác Thực eKYC',
                  style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
                ),
                const Spacer(),
                Container(
                  padding:
                      const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                  decoration: BoxDecoration(
                    color: item.isApproved ? Colors.green : Colors.orange,
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Text(
                    item.status,
                    style: const TextStyle(
                      color: Colors.white,
                      fontWeight: FontWeight.bold,
                      fontSize: 12,
                    ),
                  ),
                ),
              ],
            ),
            const Divider(height: 24),

            // Scores
            Row(
              children: [
                Expanded(
                  child: _buildMetricTile(
                    'Độ khớp khuôn mặt',
                    '${item.faceMatchScore.toStringAsFixed(1)}%',
                    item.faceMatchScore >= 80 ? Colors.green : Colors.orange,
                  ),
                ),
                Expanded(
                  child: _buildMetricTile(
                    'Độ tin cậy OCR',
                    '${(item.ocrConfidence * 100).toStringAsFixed(1)}%',
                    item.ocrConfidence >= 0.85 ? Colors.green : Colors.blue,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),

            // Extracted Information
            _buildInfoRow('Họ và tên:', item.fullName),
            _buildInfoRow('Số CCCD:', item.cccdNumberMasked),
            if (item.dob != null) _buildInfoRow('Ngày sinh:', item.dob!),
            if (item.residenceAddress != null)
              _buildInfoRow('Nơi thường trú:', item.residenceAddress!),
            if (item.expiryDate != null)
              _buildInfoRow('Hạn sử dụng:', item.expiryDate!),
            const SizedBox(height: 12),

            // Reason
            Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: theme.colorScheme.surfaceContainerHighest,
                borderRadius: BorderRadius.circular(8),
              ),
              child: Row(
                children: [
                  const Icon(Icons.info_outline,
                      size: 18, color: Colors.blueGrey),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      item.routingReason,
                      style: const TextStyle(fontSize: 12),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildMetricTile(String label, String value, Color color) {
    return Container(
      padding: const EdgeInsets.all(8),
      margin: const EdgeInsets.symmetric(horizontal: 4),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.1),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Column(
        children: [
          Text(
            value,
            style: TextStyle(
              fontWeight: FontWeight.bold,
              fontSize: 18,
              color: color,
            ),
          ),
          const SizedBox(height: 2),
          Text(
            label,
            style: const TextStyle(fontSize: 11, color: Colors.grey),
            textAlign: TextAlign.center,
          ),
        ],
      ),
    );
  }

  Widget _buildInfoRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4.0),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 120,
            child: Text(
              label,
              style: const TextStyle(color: Colors.grey, fontSize: 13),
            ),
          ),
          Expanded(
            child: Text(
              value,
              style: const TextStyle(
                fontWeight: FontWeight.w600,
                fontSize: 13,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

