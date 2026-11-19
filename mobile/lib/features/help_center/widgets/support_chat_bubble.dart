import 'package:flutter/material.dart';
import 'package:cani/features/help_center/models/help_center_models.dart';

class SupportChatBubble extends StatelessWidget {
  final SupportChatMessageModel message;

  const SupportChatBubble({
    super.key,
    required this.message,
  });

  String _formatTime(DateTime dt) {
    final hour = dt.hour.toString().padLeft(2, '0');
    final minute = dt.minute.toString().padLeft(2, '0');
    return '$hour:$minute';
  }

  @override
  Widget build(BuildContext context) {
    final isStaff = message.isFromStaff;

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        mainAxisAlignment: isStaff ? MainAxisAlignment.start : MainAxisAlignment.end,
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [
          if (isStaff) ...[
            Container(
              width: 32,
              height: 32,
              decoration: const BoxDecoration(
                color: Color(0xFFE0F2FE),
                shape: BoxShape.circle,
              ),
              child: const Icon(Icons.support_agent_rounded, size: 20, color: Color(0xFF0284C7)),
            ),
            const SizedBox(width: 8),
          ],
          Flexible(
            child: Column(
              crossAxisAlignment: isStaff ? CrossAxisAlignment.start : CrossAxisAlignment.end,
              children: [
                if (isStaff)
                  Padding(
                    padding: const EdgeInsets.only(left: 4, bottom: 4),
                    child: Text(
                      message.senderRole == 'SYSTEM' ? 'Thông báo hệ thống' : 'CSKH CANi',
                      style: const TextStyle(
                        fontSize: 11,
                        fontWeight: FontWeight.w700,
                        color: Color(0xFF64748B),
                      ),
                    ),
                  ),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                  decoration: BoxDecoration(
                    color: isStaff ? Colors.white : const Color(0xFF0066FF),
                    borderRadius: BorderRadius.only(
                      topLeft: const Radius.circular(16),
                      topRight: const Radius.circular(16),
                      bottomLeft: Radius.circular(isStaff ? 2 : 16),
                      bottomRight: Radius.circular(isStaff ? 16 : 2),
                    ),
                    border: isStaff ? Border.all(color: const Color(0xFFE2E8F0)) : null,
                    boxShadow: const [
                      BoxShadow(
                        color: Color(0x06000000),
                        blurRadius: 4,
                        offset: Offset(0, 1),
                      ),
                    ],
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      if (message.shiftName != null && message.shiftName!.isNotEmpty) ...[
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                          margin: const EdgeInsets.only(bottom: 6),
                          decoration: BoxDecoration(
                            color: isStaff ? const Color(0xFFF1F5F9) : const Color(0x22FFFFFF),
                            borderRadius: BorderRadius.circular(6),
                          ),
                          child: Text(
                            'Ca: ${message.shiftName}',
                            style: TextStyle(
                              fontSize: 10.5,
                              fontWeight: FontWeight.w600,
                              color: isStaff ? const Color(0xFF475569) : Colors.white,
                            ),
                          ),
                        ),
                      ],
                      Text(
                        message.messageText,
                        style: TextStyle(
                          fontSize: 13,
                          height: 1.35,
                          color: isStaff ? const Color(0xFF0F172A) : Colors.white,
                        ),
                      ),
                    ],
                  ),
                ),
                Padding(
                  padding: const EdgeInsets.only(top: 4, left: 4, right: 4),
                  child: Text(
                    _formatTime(message.createdAt),
                    style: const TextStyle(
                      fontSize: 10,
                      color: Color(0xFF94A3B8),
                    ),
                  ),
                ),
              ],
            ),
          ),
          if (!isStaff) const SizedBox(width: 4),
        ],
      ),
    );
  }
}
