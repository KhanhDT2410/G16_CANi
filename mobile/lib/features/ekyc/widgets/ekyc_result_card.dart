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
    return Card(
      elevation: 0,
      color: Colors.white,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: const BorderSide(color: Color(0xFFE2E8F0), width: 1.5),
      ),
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Icon(Icons.analytics_outlined, color: Color(0xFF0077FF)),
                const SizedBox(width: 8),
                const Text(
                  'Chi Tiết Xác Thực eKYC',
                  style: TextStyle(
                    fontWeight: FontWeight.w700,
                    fontSize: 16,
                    color: Color(0xFF0B1B3D),
                    letterSpacing: -0.2,
                  ),
                ),
                const Spacer(),
                Container(
                  padding:
                      const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                  decoration: BoxDecoration(
                    color: item.isApproved
                        ? const Color(0xFF10B981)
                        : (item.isPending
                            ? const Color(0xFFF59E0B)
                            : const Color(0xFFEF4444)),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Text(
                    item.status,
                    style: const TextStyle(
                      color: Colors.white,
                      fontWeight: FontWeight.w700,
                      fontSize: 12,
                    ),
                  ),
                ),
              ],
            ),
            const Divider(height: 24, color: Color(0xFFF1F5F9)),

            // Scores
            Row(
              children: [
                Expanded(
                  child: _buildMetricTile(
                    'Độ khớp khuôn mặt',
                    '${item.faceMatchScore.toStringAsFixed(1)}%',
                    item.faceMatchScore >= 80 ? const Color(0xFF10B981) : const Color(0xFFF59E0B),
                  ),
                ),
                Expanded(
                  child: _buildMetricTile(
                    'Độ tin cậy OCR',
                    '${(item.ocrConfidence * 100).toStringAsFixed(1)}%',
                    item.ocrConfidence >= 0.85 ? const Color(0xFF10B981) : const Color(0xFF0077FF),
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
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: const Color(0xFFF8FAFC),
                borderRadius: BorderRadius.circular(10),
                border: Border.all(color: const Color(0xFFE2E8F0)),
              ),
              child: Row(
                children: [
                  const Icon(Icons.info_outline,
                      size: 18, color: Color(0xFF0077FF)),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      item.routingReason,
                      style: const TextStyle(fontSize: 12.5, color: Color(0xFF475569)),
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
      padding: const EdgeInsets.all(10),
      margin: const EdgeInsets.symmetric(horizontal: 4),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: color.withValues(alpha: 0.2)),
      ),
      child: Column(
        children: [
          Text(
            value,
            style: TextStyle(
              fontWeight: FontWeight.w800,
              fontSize: 18,
              color: color,
            ),
          ),
          const SizedBox(height: 2),
          Text(
            label,
            style: const TextStyle(fontSize: 11.5, color: Color(0xFF64748B)),
            textAlign: TextAlign.center,
          ),
        ],
      ),
    );
  }

  Widget _buildInfoRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4.5),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 120,
            child: Text(
              label,
              style: const TextStyle(color: Color(0xFF64748B), fontSize: 13),
            ),
          ),
          Expanded(
            child: Text(
              value,
              style: const TextStyle(
                fontWeight: FontWeight.w600,
                fontSize: 13.5,
                color: Color(0xFF0F172A),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

