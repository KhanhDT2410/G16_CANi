import 'package:flutter/material.dart';
import '../providers/ekyc_provider.dart';

/// EkycConfirmedInfoCard - Thẻ hiển thị các thông tin CCCD đã được người dùng xác nhận
class EkycConfirmedInfoCard extends StatelessWidget {
  final EkycState state;
  final VoidCallback onEditInfo;

  const EkycConfirmedInfoCard({
    super.key,
    required this.state,
    required this.onEditInfo,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Colors.green.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: Colors.green.withValues(alpha: 0.5),
          width: 1.5,
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Icon(Icons.verified, color: Colors.green, size: 20),
              const SizedBox(width: 8),
              const Expanded(
                child: Text(
                  'Thông Tin CCCD Đã Xác Nhận',
                  style: TextStyle(
                    fontWeight: FontWeight.bold,
                    color: Colors.green,
                    fontSize: 14,
                  ),
                ),
              ),
              InkWell(
                onTap: onEditInfo,
                borderRadius: BorderRadius.circular(6),
                child: const Padding(
                  padding: EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(Icons.edit_outlined, size: 14, color: Colors.green),
                      SizedBox(width: 4),
                      Text(
                        'Sửa',
                        style: TextStyle(
                          fontSize: 12,
                          color: Colors.green,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
          const Divider(height: 16, color: Color(0xFFDCFCE7)),
          if (state.confirmedCccd != null)
            _buildInfoRow('Số CCCD:', state.confirmedCccd!),
          if (state.confirmedFullName != null)
            _buildInfoRow('Họ và tên:', state.confirmedFullName!),
          if (state.confirmedDob != null)
            _buildInfoRow('Ngày sinh:', state.confirmedDob!),
          if (state.confirmedGender != null)
            _buildInfoRow('Giới tính:', state.confirmedGender!),
          if (state.confirmedResidenceAddress != null)
            _buildInfoRow('Thường trú:', state.confirmedResidenceAddress!),
          if (state.confirmedIssueDate != null &&
              state.confirmedIssueDate!.isNotEmpty)
            _buildInfoRow('Ngày cấp:', state.confirmedIssueDate!),
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
