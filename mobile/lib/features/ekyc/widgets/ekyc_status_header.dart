import 'package:flutter/material.dart';
import '../models/ekyc_models.dart';

/// EkycStatusHeader - Header trạng thái định danh eKYC của tài khoản (hỗ trợ đầy đủ 6 trạng thái backend)
class EkycStatusHeader extends StatelessWidget {
  final EkycVerificationItem? item;

  const EkycStatusHeader({
    super.key,
    required this.item,
  });

  @override
  Widget build(BuildContext context) {
    final isApproved = item != null && item!.isApproved;
    final isPendingReview = item != null && item!.isPendingReview;
    final isProcessing = item != null && item!.isProcessing;
    final isTechnicalFailure = item != null && item!.isTechnicalFailure;
    final isRejected = item != null && item!.isRejected;

    final Color bgColor;
    final Color borderColor;
    final Color iconColor;
    final Color textColor;
    final Color subtitleColor;
    final IconData icon;
    final String title;
    final String subtitle;

    if (isApproved) {
      bgColor = const Color(0xFFECFDF5);
      borderColor = const Color(0xFFA7F3D0);
      iconColor = const Color(0xFF10B981);
      textColor = const Color(0xFF047857);
      subtitleColor = const Color(0xFF065F46);
      icon = Icons.check_circle;
      title = 'ĐÃ ĐỊNH DANH eKYC (TÍCH XANH)';
      subtitle = 'Tài khoản của bạn đã được xác minh danh tính thành công qua VNPT eKYC. Bạn có thể bật nhận việc ngay.';
    } else if (isPendingReview) {
      bgColor = const Color(0xFFFFFBEB);
      borderColor = const Color(0xFFFDE68A);
      iconColor = const Color(0xFFD97706);
      textColor = const Color(0xFFB45309);
      subtitleColor = const Color(0xFF92400E);
      icon = Icons.hourglass_top_rounded;
      title = 'HỒ SƠ ĐANG CHỜ ADMIN KIỂM TRA';
      subtitle = 'Hồ sơ định danh đã được tiếp nhận và đang chờ Bộ phận CSKH / Admin thẩm định thủ công. Trạng thái sẽ cập nhật ngay khi có kết quả.';
    } else if (isProcessing) {
      bgColor = const Color(0xFFEFF6FF);
      borderColor = const Color(0xFFBFDBFE);
      iconColor = const Color(0xFF0077FF);
      textColor = const Color(0xFF1D4ED8);
      subtitleColor = const Color(0xFF1E40AF);
      icon = Icons.sync;
      title = 'ĐANG XÁC MINH HỒ SƠ eKYC';
      subtitle = 'Hệ thống đang gửi và đối chiếu thông tin với cổng VNPT eKYC. Vui lòng chờ trong giây lát.';
    } else if (isTechnicalFailure) {
      bgColor = const Color(0xFFFFF7ED);
      borderColor = const Color(0xFFFED7AA);
      iconColor = const Color(0xFFEA580C);
      textColor = const Color(0xFFC2410C);
      subtitleColor = const Color(0xFF9A3412);
      icon = Icons.cloud_off_rounded;
      title = 'SỰ CỐ KỸ THUẬT TẠM THỜI';
      subtitle = 'Không thể hoàn tất kết nối tới cổng VNPT eKYC. Hồ sơ của bạn KHÔNG bị từ chối — vui lòng bấm Thử lại.';
    } else if (isRejected) {
      bgColor = const Color(0xFFFEF2F2);
      borderColor = const Color(0xFFFECACA);
      iconColor = const Color(0xFFEF4444);
      textColor = const Color(0xFFB91C1C);
      subtitleColor = const Color(0xFF991B1B);
      icon = Icons.error_outline;
      title = 'XÁC MINH DANH TÍNH CHƯA THÀNH CÔNG';
      subtitle = 'Hồ sơ chưa đáp ứng tiêu chuẩn xác minh VNPT eKYC. Vui lòng xem lý do chi tiết bên dưới và chụp lại ảnh rõ nét.';
    } else {
      bgColor = const Color(0xFFF8FAFC);
      borderColor = const Color(0xFFE2E8F0);
      iconColor = const Color(0xFF0077FF);
      textColor = const Color(0xFF0B1B3D);
      subtitleColor = const Color(0xFF64748B);
      icon = Icons.fingerprint;
      title = 'CHƯA ĐỊNH DANH eKYC';
      subtitle = 'Hoàn thành 3 bước chụp CCCD mặt trước, mặt sau và ảnh chân dung selfie để xác minh qua VNPT eKYC.';
    }

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
                  title,
                  style: TextStyle(
                    fontWeight: FontWeight.w700,
                    fontSize: 14,
                    color: textColor,
                    letterSpacing: -0.2,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  subtitle,
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
