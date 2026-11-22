import 'package:flutter/material.dart';
import '../models/ekyc_models.dart';

/// EkycResultCard - Card hiển thị trung thực kết quả xác minh VNPT eKYC (không suy đoán ngưỡng cứng)
class EkycResultCard extends StatelessWidget {
  final EkycVerificationItem item;

  const EkycResultCard({
    super.key,
    required this.item,
  });

  @override
  Widget build(BuildContext context) {
    final (badgeColor, badgeText) = _resolveBadge();

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
                const Expanded(
                  child: Text(
                    'Chi Tiết Xác Thực eKYC',
                    style: TextStyle(
                      fontWeight: FontWeight.w700,
                      fontSize: 16,
                      color: Color(0xFF0B1B3D),
                      letterSpacing: -0.2,
                    ),
                  ),
                ),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                  decoration: BoxDecoration(
                    color: badgeColor,
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Text(
                    badgeText,
                    style: const TextStyle(
                      color: Colors.white,
                      fontWeight: FontWeight.w700,
                      fontSize: 11.5,
                    ),
                  ),
                ),
              ],
            ),
            const Divider(height: 24, color: Color(0xFFF1F5F9)),

            if (item.isTechnicalFailure) ...[
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: const Color(0xFFFFF7ED),
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(color: const Color(0xFFFED7AA)),
                ),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Icon(Icons.wifi_off_rounded, size: 20, color: Color(0xFFEA580C)),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const Text(
                            'Sự cố kỹ thuật — cho phép thử lại',
                            style: TextStyle(
                              fontWeight: FontWeight.w700,
                              fontSize: 13.5,
                              color: Color(0xFFC2410C),
                            ),
                          ),
                          const SizedBox(height: 4),
                          Text(
                            item.routingReason.isNotEmpty
                                ? item.routingReason
                                : 'Không thể hoàn tất xác minh do lỗi kỹ thuật tạm thời từ cổng VNPT eKYC.',
                            style: const TextStyle(fontSize: 12.5, color: Color(0xFF9A3412), height: 1.4),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            ] else ...[
              // Trạng thái thẩm định từ backend (không hardcode tự phán đoán thang điểm)
              Row(
                children: [
                  Expanded(
                    child: _buildMetricTile(
                      'Kết quả xác minh',
                      _resolveVerificationSummaryLabel(),
                      badgeColor,
                    ),
                  ),
                  Expanded(
                    child: _buildMetricTile(
                      'Nhà cung cấp',
                      item.provider ?? 'VNPT eKYC',
                      const Color(0xFF0077FF),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 16),

              // Thông tin OCR trích xuất từ VNPT (nếu có)
              if (item.fullName.isNotEmpty) _buildInfoRow('Họ và tên:', item.fullName),
              if (item.cccdNumberMasked.isNotEmpty) _buildInfoRow('Số CCCD:', item.cccdNumberMasked),
              if (item.dob != null && item.dob!.isNotEmpty) _buildInfoRow('Ngày sinh:', item.dob!),
              if (item.residenceAddress != null && item.residenceAddress!.isNotEmpty)
                _buildInfoRow('Nơi thường trú:', item.residenceAddress!),
              if (item.expiryDate != null && item.expiryDate!.isNotEmpty)
                _buildInfoRow('Hạn sử dụng:', item.expiryDate!),
              const SizedBox(height: 12),

              // Lý do / ghi chú từ backend
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: const Color(0xFFF8FAFC),
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(color: const Color(0xFFE2E8F0)),
                ),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Icon(Icons.info_outline, size: 18, color: Color(0xFF0077FF)),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        item.routingReason.isNotEmpty
                            ? item.routingReason
                            : 'Hồ sơ đã được ghi nhận trên hệ thống.',
                        style: const TextStyle(fontSize: 12.5, color: Color(0xFF475569), height: 1.4),
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }

  (Color, String) _resolveBadge() {
    if (item.isApproved) return (const Color(0xFF10B981), 'APPROVED');
    if (item.isPendingReview) return (const Color(0xFFD97706), 'PENDING_REVIEW');
    if (item.isProcessing) return (const Color(0xFF0077FF), item.status);
    if (item.isTechnicalFailure) return (const Color(0xFFEA580C), 'TECHNICAL_FAILURE');
    return (const Color(0xFFEF4444), 'REJECTED');
  }

  String _resolveVerificationSummaryLabel() {
    if (item.isApproved) return 'Đã xác minh';
    if (item.isPendingReview) return 'Chờ Admin duyệt';
    if (item.isProcessing) return 'Đang xử lý';
    if (item.isTechnicalFailure) return 'Lỗi kỹ thuật';
    return 'Chưa đạt';
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
              fontSize: 15,
              color: color,
            ),
            textAlign: TextAlign.center,
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
