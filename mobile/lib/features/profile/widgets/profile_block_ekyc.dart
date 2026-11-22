// CANi · tab "Tài khoản" — Block 3 · eKYC / Định danh Card.
// Trạng thái verified / pending / unverified + CTA tới EkycVerificationScreen.

import 'package:flutter/material.dart';

import 'package:cani/features/ekyc/models/ekyc_models.dart';
import 'package:cani/features/profile/widgets/profile_tokens.dart';

class ProfileEkycCard extends StatelessWidget {
  const ProfileEkycCard({required this.status, required this.onTap, super.key});

  final EkycVerificationItem? status;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).profileX;
    final item = status;
    final approved = item != null && item.isApproved;
    final pendingReview = item != null && item.isPendingReview;
    final processing = item != null && item.isProcessing;
    final technicalFailure = item != null && item.isTechnicalFailure;
    final rejected = item != null && item.isRejected;

    final (label, color, icon) = approved
        ? ('ĐÃ XÁC THỰC', t.success, Icons.verified)
        : pendingReview
            ? ('CHỜ ADMIN DUYỆT', const Color(0xFFD97706), Icons.hourglass_top)
            : processing
                ? ('ĐANG XỬ LÝ', t.accent, Icons.sync)
                : technicalFailure
                    ? ('LỖI KỸ THUẬT', const Color(0xFFEA580C), Icons.cloud_off_rounded)
                    : rejected
                        ? ('BỊ TỪ CHỐI', t.danger, Icons.error_outline)
                        : ('CHƯA XÁC THỰC', t.danger, Icons.gps_off);

    final String subtitle;
    if (approved) {
      subtitle = 'Xác thực thành công ngày ${item.createdAt.toIso8601String().split('T').first}.';
    } else if (pendingReview) {
      subtitle = 'Hồ sơ đã được tiếp nhận và đang chờ Admin / CSKH kiểm tra.';
    } else if (processing) {
      subtitle = 'Hồ sơ đang được xác minh qua cổng VNPT eKYC.';
    } else if (technicalFailure) {
      subtitle = 'Kết nối VNPT eKYC tạm gián đoạn. Bấm để thử lại (hồ sơ chưa bị từ chối).';
    } else if (rejected) {
      subtitle = 'Xác minh chưa đạt yêu cầu. Vui lòng chụp lại ảnh rõ nét.';
    } else {
      subtitle = 'Hoàn thiện định danh để mở khoá mức tin cậy cao hơn.';
    }

    return Container(
      margin: const EdgeInsets.all(16),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: t.surface,
        borderRadius: BorderRadius.circular(t.radius),
        border: Border.all(color: t.border),
      ),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(children: [
          Icon(icon, size: 20, color: color),
          const SizedBox(width: 8),
          Text('ĐỊNH DANH (eKYC)',
              style: Theme.of(context).textTheme.labelLarge?.copyWith(color: t.muted)),
          const Spacer(),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
            decoration:
                BoxDecoration(color: color.withValues(alpha: 0.15), borderRadius: BorderRadius.circular(99)),
            child: Text(label,
                style: Theme.of(context)
                    .textTheme
                    .labelSmall
                    ?.copyWith(color: color, fontWeight: FontWeight.w600)),
          ),
        ]),
        const SizedBox(height: 8),
        Text(
          subtitle,
          style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                color: t.muted,
                height: 1.4,
              ),
        ),
        const SizedBox(height: 12),
        SizedBox(
          width: double.infinity,
          child: FilledButton.icon(
            style: FilledButton.styleFrom(
              backgroundColor: t.accent,
              foregroundColor: Colors.white,
              textStyle: const TextStyle(fontWeight: FontWeight.bold),
            ),
            onPressed: onTap,
            icon: const Icon(Icons.verified_user, size: 16),
            label: Text(approved ? 'Quản lý định danh' : 'Xác thực ngay'),
          ),
        ),
      ]),
    );
  }
}
