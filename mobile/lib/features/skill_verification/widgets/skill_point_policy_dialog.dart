import 'package:flutter/material.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import 'skill_hub_theme.dart';

class SkillPointPolicyDialog extends StatefulWidget {
  final String? userId;
  final VoidCallback onDismissed;

  const SkillPointPolicyDialog({
    super.key,
    this.userId,
    required this.onDismissed,
  });

  /// Open on-demand when tapping the info [!] button on AppBar
  static Future<void> show(BuildContext context, {String? userId}) {
    return showDialog(
      context: context,
      builder: (ctx) => SkillPointPolicyDialog(
        userId: userId,
        onDismissed: () => Navigator.of(ctx).pop(),
      ),
    );
  }

  /// Automatically check and show modal for users entering the Skill Hub for the first time
  static Future<void> checkAndShow(BuildContext context, String userId) async {
    const storage = FlutterSecureStorage();
    final seen = await storage.read(key: 'cani_skill_guide_seen_$userId');
    if (seen != 'true' && context.mounted) {
      showDialog(
        context: context,
        barrierDismissible: true,
        builder: (ctx) => SkillPointPolicyDialog(
          userId: userId,
          onDismissed: () async {
            await storage.write(key: 'cani_skill_guide_seen_$userId', value: 'true');
            if (ctx.mounted) {
              Navigator.of(ctx).pop();
            }
          },
        ),
      );
    }
  }

  @override
  State<SkillPointPolicyDialog> createState() => _SkillPointPolicyDialogState();
}

class _SkillPointPolicyDialogState extends State<SkillPointPolicyDialog> {
  bool _isUnderstood = true;

  @override
  Widget build(BuildContext context) {
    return Dialog(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
      insetPadding: const EdgeInsets.symmetric(horizontal: 20, vertical: 24),
      backgroundColor: Colors.white,
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 500, maxHeight: 620),
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              // 1. Icon & Header
              Row(
                children: [
                  Container(
                    padding: const EdgeInsets.all(10),
                    decoration: BoxDecoration(
                      color: SkillHubColors.ctaPrimary.withValues(alpha: 0.15),
                      shape: BoxShape.circle,
                    ),
                    child: const Icon(
                      Icons.workspace_premium,
                      color: SkillHubColors.ctaHover,
                      size: 26,
                    ),
                  ),
                  const SizedBox(width: 14),
                  const Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'Quy Chế Điểm Kỹ Năng (SP)',
                          style: TextStyle(
                            fontSize: 17,
                            fontWeight: FontWeight.w800,
                            color: SkillHubColors.textPrimary,
                          ),
                        ),
                        Text(
                          'Hướng dẫn cấp bậc hạng và quyền ưu tiên nhận ca',
                          style: TextStyle(fontSize: 11.5, color: SkillHubColors.textSecondary),
                        ),
                      ],
                    ),
                  ),
                  IconButton(
                    icon: const Icon(Icons.close, size: 20, color: Colors.grey),
                    onPressed: widget.onDismissed,
                    tooltip: 'Đóng',
                  ),
                ],
              ),
              const SizedBox(height: 12),
              const Divider(height: 1, color: SkillHubColors.borderSoft),
              const SizedBox(height: 12),

              // 2. Scrollable Policy Terms
              Expanded(
                child: Scrollbar(
                  thumbVisibility: true,
                  child: SingleChildScrollView(
                    padding: const EdgeInsets.only(right: 8),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        _buildSection(
                          '1. Điểm Kỹ Năng (SP) là gì?',
                          'Điểm Kỹ Năng (Skill Points - SP) định lượng năng lực chuyên môn và độ tin cậy của bạn trên nền tảng CANi. Điểm SP trực tiếp quyết định cấp bậc hạng tài khoản và quyền ưu tiên nhận các ca sự kiện có thù lao cao.',
                        ),
                        _buildSection(
                          '2. Cơ Chế Tính Điểm Minh Bạch Theo Từng Kỹ Năng',
                          'Tổng SP kỹ năng = SP Xác minh + SP Kinh nghiệm + SP Đánh giá\n\n'
                          '• SP Xác minh: +25 SP khi chứng chỉ hợp lệ được Admin / CSKH kiểm tra và phê duyệt thủ công; nâng lên +50 SP khi kỹ năng được chứng minh qua ca làm thực tế.\n'
                          '• SP Kinh nghiệm (+20 SP/ca): Tự động tích lũy cho đúng kỹ năng thực hiện chính sau mỗi ca hoàn thành và được Ban tổ chức xác nhận.\n'
                          '• SP Đánh giá (+15 SP cho 5 sao, +5 SP cho 4 sao): Chỉ tính từ đánh giá thật do Ban tổ chức gửi sau ca làm.',
                        ),
                        _buildSection(
                          '3. Phân Biệt Mức Phát Triển & Mức Xác Minh',
                          'Hai khái niệm này hoạt động độc lập cho từng kỹ năng:\n\n'
                          'A. 3 Mức phát triển trên giao diện (quy đổi từ 7 hạng SP):\n'
                          '• Mới bắt đầu (Khởi đầu 0-99 SP · Đồng 100-199 SP)\n'
                          '• Có kinh nghiệm (Bạc 200-349 SP · Vàng 350-499 SP · Bạch kim 500-699 SP)\n'
                          '• Thành thạo (Kim cương 700-899 SP · Tinh anh 900+ SP)\n\n'
                          'B. 3 Mức xác minh bằng chứng:\n'
                          '• Tự khai báo: Bạn tự thêm kỹ năng vào hồ sơ.\n'
                          '• Đã xác thực tài liệu: Chứng chỉ được Admin / CSKH kiểm tra và phê duyệt thủ công (hiệu lực tối đa 24 tháng).\n'
                          '• Đã chứng minh qua ca làm: Từ 3 ca hoàn thành trở lên, tỷ lệ hoàn thành ≥ 85%, điểm uy tín ≥ 70, tỷ lệ vắng mặt ≤ 5% và đánh giá thật ≥ 4.5 sao.',
                        ),
                        _buildSection(
                          '4. Chế Tài Kỷ Luật (Chính Sách Bỏ Ca - No-Show)',
                          '• Đã xác nhận ca nhưng vắng mặt không báo trước (No-Show) sẽ bị trừ 30 - 50 SP và có nguy cơ bị hạ bậc hạng.\n'
                          '• Đi muộn không lý do chính đáng hoặc vi phạm quy định trang phục sẽ làm giảm điểm đánh giá uy tín.',
                        ),
                        _buildSection(
                          '5. Tiêu Chuẩn Thẩm Định Chứng Chỉ',
                          'Chỉ chấp nhận các chứng chỉ hợp lệ, chính chủ (An toàn thực phẩm, Pha chế Bartender, Nghiệp vụ bàn/lễ tân, Ngoại ngữ...). Hành vi nộp tài liệu giả mạo sẽ dẫn tới khóa tài khoản vĩnh viễn.',
                        ),
                      ],
                    ),
                  ),
                ),
              ),

              const SizedBox(height: 12),
              const Divider(height: 1, color: SkillHubColors.borderSoft),
              const SizedBox(height: 8),

              // 3. Checkbox Confirmation
              InkWell(
                borderRadius: BorderRadius.circular(8),
                onTap: () => setState(() => _isUnderstood = !_isUnderstood),
                child: Padding(
                  padding: const EdgeInsets.symmetric(vertical: 4),
                  child: Row(
                    children: [
                       Checkbox(
                        value: _isUnderstood,
                        activeColor: SkillHubColors.ctaPrimary,
                        onChanged: (val) => setState(() => _isUnderstood = val ?? false),
                      ),
                      const Expanded(
                        child: Text(
                          'Tôi đã đọc, hiểu rõ quy chế điểm SP và lộ trình thăng hạng.',
                          style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: SkillHubColors.textPrimary),
                        ),
                      ),
                    ],
                  ),
                ),
              ),

              const SizedBox(height: 10),

              // 4. Action Button
              SizedBox(
                height: 44,
                child: FilledButton(
                  onPressed: _isUnderstood ? widget.onDismissed : null,
                  style: FilledButton.styleFrom(
                    backgroundColor: SkillHubColors.ctaPrimary,
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                  ),
                  child: const Text(
                    'Tôi đã hiểu & Bắt đầu',
                    style: TextStyle(fontWeight: FontWeight.bold, fontSize: 14, color: Colors.white),
                  ),
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
            style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 13, color: SkillHubColors.textPrimary),
          ),
          const SizedBox(height: 4),
          Text(
            body,
            style: const TextStyle(fontSize: 12, color: Color(0xFF334155), height: 1.45),
          ),
        ],
      ),
    );
  }
}
