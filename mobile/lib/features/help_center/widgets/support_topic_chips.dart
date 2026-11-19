import 'package:flutter/material.dart';

class SupportTopicChips extends StatelessWidget {
  final ValueChanged<String> onSelectTopic;

  const SupportTopicChips({
    super.key,
    required this.onSelectTopic,
  });

  static const List<String> quickTopics = [
    'Đối soát thù lao ca làm',
    'Sự cố quét mã QR điểm danh',
    'Thẩm định tài khoản eKYC',
    'Kháng cáo điểm uy tín',
  ];

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 44,
      padding: const EdgeInsets.symmetric(vertical: 6),
      decoration: const BoxDecoration(
        color: Colors.white,
        border: Border(bottom: BorderSide(color: Color(0xFFE2E8F0))),
      ),
      child: ListView.separated(
        padding: const EdgeInsets.symmetric(horizontal: 16),
        scrollDirection: Axis.horizontal,
        itemCount: quickTopics.length,
        separatorBuilder: (context, index) => const SizedBox(width: 8),
        itemBuilder: (context, index) {
          final topic = quickTopics[index];
          return ActionChip(
            label: Text(
              topic,
              style: const TextStyle(fontSize: 11.5, color: Color(0xFF334155), fontWeight: FontWeight.w600),
            ),
            backgroundColor: const Color(0xFFF1F5F9),
            side: const BorderSide(color: Color(0xFFE2E8F0)),
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
            padding: const EdgeInsets.symmetric(horizontal: 6),
            onPressed: () => onSelectTopic(topic),
          );
        },
      ),
    );
  }
}
