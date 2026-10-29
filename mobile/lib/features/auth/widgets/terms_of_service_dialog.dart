import 'package:flutter/material.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

class TermsOfServiceDialog extends StatefulWidget {
  final String userId;
  final VoidCallback onAccepted;

  const TermsOfServiceDialog({
    super.key,
    required this.userId,
    required this.onAccepted,
  });

  /// Ghi nhận người dùng đã chấp thuận điều khoản (ví dụ: đã tick chọn khi đăng ký)
  static Future<void> markAccepted(String userId) async {
    const storage = FlutterSecureStorage();
    await storage.write(key: 'cani_terms_accepted_$userId', value: 'true');
  }

  static Future<void> checkAndShow(BuildContext context, String userId) async {
    const storage = FlutterSecureStorage();
    final accepted = await storage.read(key: 'cani_terms_accepted_$userId');
    if (accepted != 'true' && context.mounted) {
      showDialog(
        context: context,
        barrierDismissible: false, // Bắt buộc phải đồng ý, không bấm ra ngoài để tắt
        builder: (ctx) => PopScope(
          canPop: false, // Không cho phép nút Back vật lý thoát
          child: TermsOfServiceDialog(
            userId: userId,
            onAccepted: () async {
              await storage.write(key: 'cani_terms_accepted_$userId', value: 'true');
              if (ctx.mounted) {
                Navigator.of(ctx).pop();
              }
            },
          ),
        ),
      );
    }
  }

  @override
  State<TermsOfServiceDialog> createState() => _TermsOfServiceDialogState();
}

class _TermsOfServiceDialogState extends State<TermsOfServiceDialog> {
  bool _isAgreed = false;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Dialog(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
      insetPadding: const EdgeInsets.symmetric(horizontal: 20, vertical: 24),
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 500, maxHeight: 620),
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              // 1. Icon & Tiêu đề
              Row(
                children: [
                  Container(
                    padding: const EdgeInsets.all(10),
                    decoration: BoxDecoration(
                      color: theme.colorScheme.primary.withValues(alpha: 0.12),
                      shape: BoxShape.circle,
                    ),
                    child: Icon(Icons.gavel_rounded, color: theme.colorScheme.primary, size: 28),
                  ),
                  const SizedBox(width: 14),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text(
                          'Điều Khoản Sử Dụng',
                          style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
                        ),
                        Text(
                          'Quy tắc & Cam kết thành viên CANi',
                          style: TextStyle(fontSize: 12, color: Colors.grey.shade600),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 16),
              const Divider(height: 1),
              const SizedBox(height: 12),

              // 2. Nội dung điều khoản cuộn được
              Expanded(
                child: Scrollbar(
                  thumbVisibility: true,
                  child: SingleChildScrollView(
                    padding: const EdgeInsets.only(right: 8),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        _buildSection(
                          '1. Phạm vi áp dụng & Tư cách thành viên',
                          'CANi là nền tảng công nghệ kết nối Nhân sự Sự kiện (Worker) và Nhà tổ chức sự kiện (Event Organizer). Bằng việc đăng ký tài khoản, bạn cam kết cung cấp thông tin trung thực và chịu trách nhiệm pháp lý về các hoạt động của mình.',
                        ),
                        _buildSection(
                          '2. Cam kết thực hiện ca làm việc & Chống bỏ ca (No-Show)',
                          '• Khi đã bấm "Xác nhận nhận việc", bạn có nghĩa vụ có mặt đúng giờ, mặc trang phục theo quy định và hoàn thành đầy đủ thời gian ca làm việc.\n• Trường hợp bất khả kháng cần hủy ca, phải thông báo qua ứng dụng tối thiểu trước 12 tiếng.\n• Hành vi bỏ ca không lý do (No-Show) sẽ bị trừ 30-50 điểm SP uy tín và tạm khóa quyền nhận việc theo quy chế.',
                        ),
                        _buildSection(
                          '3. Tiêu chuẩn Định danh eKYC & Giấy tờ Căn cước',
                          '• Mỗi Worker chỉ được liên kết DUY NHẤT một Căn cước công dân (CCCD) chính chủ.\n• Nghiêm cấm mượn danh tính, tải lên giấy tờ giả mạo hoặc thuê người khác đi làm thay tài khoản của mình.',
                        ),
                        _buildSection(
                          '4. Thù lao & Bảo đảm tài chính',
                          '• Tiền công ca làm việc được tính theo mức thù lao đã thỏa thuận và tự động giải ngân sau khi Nhà tổ chức nghiệm thu ca làm việc.\n• Mọi tranh chấp về công việc và chấm công sẽ được đội ngũ CSKH CANi hỗ trợ hòa giải theo nhật trình Check-in/Check-out GPS.',
                        ),
                        _buildSection(
                          '5. Bảo vệ Dữ liệu Cá nhân (Nghị định 13/2023/NĐ-CP)',
                          'CANi cam kết mã hóa và bảo mật tuyệt đối ảnh CCCD, số điện thoại và thông tin sinh trắc học của bạn. Thông tin chỉ được sử dụng cho mục đích thẩm định và đối soát ca làm việc.',
                        ),
                      ],
                    ),
                  ),
                ),
              ),

              const SizedBox(height: 12),
              const Divider(height: 1),
              const SizedBox(height: 8),

              // 3. Checkbox Tôi đồng ý
              InkWell(
                borderRadius: BorderRadius.circular(8),
                onTap: () => setState(() => _isAgreed = !_isAgreed),
                child: Padding(
                  padding: const EdgeInsets.symmetric(vertical: 4),
                  child: Row(
                    children: [
                      Checkbox(
                        value: _isAgreed,
                        activeColor: theme.colorScheme.primary,
                        onChanged: (val) => setState(() => _isAgreed = val ?? false),
                      ),
                      const Expanded(
                        child: Text(
                          'Tôi đã đọc, hiểu rõ và cam kết tuân thủ Điều khoản sử dụng của CANi.',
                          style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600),
                        ),
                      ),
                    ],
                  ),
                ),
              ),

              const SizedBox(height: 12),

              // 4. Nút bấm Xác nhận
              FilledButton(
                onPressed: _isAgreed ? widget.onAccepted : null,
                style: FilledButton.styleFrom(
                  padding: const EdgeInsets.symmetric(vertical: 14),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                ),
                child: const Text(
                  'Tôi đồng ý & Bắt đầu làm việc',
                  style: TextStyle(fontWeight: FontWeight.bold, fontSize: 15),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildSection(String title, String body) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            title,
            style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 13, color: Colors.indigo),
          ),
          const SizedBox(height: 4),
          Text(
            body,
            style: TextStyle(fontSize: 12, color: Colors.grey.shade800, height: 1.45),
          ),
        ],
      ),
    );
  }
}
