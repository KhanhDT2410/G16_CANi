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
    final theme = Theme.of(context);
    final isApproved = item != null && item!.isApproved;
    final isPending = item != null && item!.isPending;

    final bgColor = isApproved
        ? Colors.green.withValues(alpha: 0.1)
        : (isPending
            ? Colors.orange.withValues(alpha: 0.1)
            : theme.colorScheme.surfaceContainerHighest);
    final borderColor = isApproved
        ? Colors.green
        : (isPending ? Colors.orange : theme.colorScheme.outlineVariant);
    final iconColor = isApproved
        ? Colors.green
        : (isPending ? Colors.orange : Colors.grey);

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
              color: iconColor.withValues(alpha: 0.15),
              shape: BoxShape.circle,
            ),
            child: Icon(
              isApproved
                  ? Icons.check_circle
                  : (isPending ? Icons.pending : Icons.fingerprint),
              color: iconColor,
              size: 32,
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
                      : 'CHƯA ĐỊNH DANH eKYC',
                  style: TextStyle(
                    fontWeight: FontWeight.bold,
                    fontSize: 14,
                    color: iconColor,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  isApproved
                      ? 'Tài khoản của bạn đã được đối soát CCCD hợp lệ và tự động phê duyệt ngay. Bạn được ưu tiên nhận việc.'
                      : 'Vui lòng hoàn thành 3 bước bên dưới để hệ thống tự động xác thực và cấp tích xanh ngay.',
                  style: TextStyle(
                    fontSize: 12,
                    color: theme.colorScheme.onSurfaceVariant,
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

