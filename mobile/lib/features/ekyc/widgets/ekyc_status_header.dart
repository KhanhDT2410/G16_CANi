import 'package:flutter/material.dart';
import '../models/ekyc_models.dart';

/// EkycStatusHeader - Header trạng thái định danh eKYC của tài khoản
class EkycStatusHeader extends StatelessWidget {
  final EkycVerificationItem? item;

  const EkycStatusHeader({
    super.key,
    required this.item,
  });

  @override
  Widget build(BuildContext context) {
    final isApproved = item != null && item!.isApproved;
    final isPending = item != null && item!.isPending;

    final bgColor = isApproved
        ? const Color(0xFFECFDF5)
        : (isPending
            ? const Color(0xFFFFFBEB)
            : const Color(0xFFF8FAFC));
    final borderColor = isApproved
        ? const Color(0xFFA7F3D0)
        : (isPending ? const Color(0xFFFDE68A) : const Color(0xFFE2E8F0));
    final iconColor = isApproved
        ? const Color(0xFF10B981)
        : (isPending ? const Color(0xFFF59E0B) : const Color(0xFF0077FF));
    final textColor = isApproved
        ? const Color(0xFF047857)
        : (isPending ? const Color(0xFFB45309) : const Color(0xFF0B1B3D));
    final subtitleColor = isApproved
        ? const Color(0xFF065F46)
        : (isPending ? const Color(0xFF92400E) : const Color(0xFF64748B));

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: bgColor,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: borderColor, width: 1.5),
      ),
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: iconColor.withValues(alpha: 0.12),
              shape: BoxShape.circle,
            ),
            child: Icon(
              isApproved
                  ? Icons.check_circle
                  : (isPending ? Icons.pending : Icons.fingerprint),
              color: iconColor,
              size: 30,
            ),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  isApproved
                      ? 'ĐÃ ĐỊNH DANH eKYC (TÍCH XANH)'
                      : (isPending
                          ? 'ĐANG CHỜ THẨM ĐỊNH'
                          : 'CHƯA ĐỊNH DANH eKYC'),
                  style: TextStyle(
                    fontWeight: FontWeight.w700,
                    fontSize: 14,
                    color: textColor,
                    letterSpacing: -0.2,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  isApproved
                      ? 'Tài khoản của bạn đã được đối soát CCCD hợp lệ. Bạn được ưu tiên nhận việc.'
                      : (isPending
                          ? 'Hồ sơ đang được hệ thống thẩm định.'
                          : 'Vui lòng hoàn thành 3 bước bên dưới để xác thực danh tính.'),
                  style: TextStyle(
                    fontSize: 12.5,
                    color: subtitleColor,
                    height: 1.35,
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
