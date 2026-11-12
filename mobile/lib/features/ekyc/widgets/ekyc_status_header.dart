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
    final isRejected = item != null && !isApproved && !isPending;

    final bgColor = isApproved
        ? const Color(0xFFECFDF5)
        : (isPending
            ? const Color(0xFFFFFBEB)
            : (isRejected ? const Color(0xFFFEF2F2) : const Color(0xFFF8FAFC)));
    final borderColor = isApproved
        ? const Color(0xFFA7F3D0)
        : (isPending
            ? const Color(0xFFFDE68A)
            : (isRejected ? const Color(0xFFFECACA) : const Color(0xFFE2E8F0)));
    final iconColor = isApproved
        ? const Color(0xFF10B981)
        : (isPending
            ? const Color(0xFFF59E0B)
            : (isRejected ? const Color(0xFFEF4444) : const Color(0xFF0077FF)));
    final textColor = isApproved
        ? const Color(0xFF047857)
        : (isPending
            ? const Color(0xFFB45309)
            : (isRejected ? const Color(0xFFB91C1C) : const Color(0xFF0B1B3D)));
    final subtitleColor = isApproved
        ? const Color(0xFF065F46)
        : (isPending
            ? const Color(0xFF92400E)
            : (isRejected ? const Color(0xFF991B1B) : const Color(0xFF64748B)));
    final icon = isApproved
        ? Icons.check_circle
        : (isPending
            ? Icons.pending
            : (isRejected ? Icons.error_outline : Icons.fingerprint));

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
              icon,
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
                          ? 'HỒ SƠ ĐANG CHỜ DUYỆT eKYC'
                          : (isRejected
                              ? 'ĐỊNH DANH CHƯA THÀNH CÔNG'
                              : 'CHƯA ĐỊNH DANH eKYC')),
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
                      ? 'Tài khoản của bạn đã được đối soát CCCD hợp lệ và tự động phê duyệt ngay. Bạn được ưu tiên nhận việc.'
                      : (isPending
                          ? 'Hồ sơ eKYC của bạn đang được nhân viên vận hành thẩm định thủ công. Bạn vẫn có thể tiếp tục sử dụng ứng dụng.'
                          : (isRejected
                              ? 'Hồ sơ chưa đạt tiêu chuẩn nhận diện. Vui lòng kiểm tra lý do chi tiết bên dưới và thử chụp lại.'
                              : 'Vui lòng hoàn thành 3 bước bên dưới để hệ thống tự động xác thực và cấp tích xanh ngay.')),
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

