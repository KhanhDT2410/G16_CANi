import 'package:flutter/material.dart';
import 'package:cani/app/cani_tokens.dart';
import '../models/dispute_models.dart';
import 'shift_checklist_section.dart';

class DisputeChatBubble extends StatelessWidget {
  final DisputeChatMessageItem message;

  const DisputeChatBubble({super.key, required this.message});

  String _formatTime(DateTime? dt) {
    if (dt == null) return '';
    final local = dt.toLocal();
    final h = local.hour.toString().padLeft(2, '0');
    final m = local.minute.toString().padLeft(2, '0');
    return '$h:$m';
  }

  @override
  Widget build(BuildContext context) {
    final cani = Theme.of(context).cani;
    final isMe = message.isMe;
    final attUrl = message.attachmentUrl;
    final looksLikeImage = attUrl != null &&
        (attUrl.startsWith('/api/') ||
            attUrl.startsWith('http://') ||
            attUrl.startsWith('https://') ||
            attUrl.startsWith('data:image/'));

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 5),
      child: Row(
        mainAxisAlignment: isMe ? MainAxisAlignment.end : MainAxisAlignment.start,
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [
          if (!isMe) ...[
            Container(
              width: 32,
              height: 32,
              decoration: BoxDecoration(
                color: cani.brand.withValues(alpha: 0.12),
                shape: BoxShape.circle,
              ),
              child: Icon(Icons.gavel_rounded, size: 18, color: cani.brand),
            ),
            const SizedBox(width: 8),
          ],
          Flexible(
            child: Column(
              crossAxisAlignment: isMe ? CrossAxisAlignment.end : CrossAxisAlignment.start,
              children: [
                if (!isMe)
                  Padding(
                    padding: const EdgeInsets.only(left: 4, bottom: 4),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Text(
                          message.senderName.isNotEmpty ? message.senderName : 'Ban Quản Trị',
                          style: TextStyle(fontSize: 11.5, fontWeight: FontWeight.w700, color: cani.textMain),
                        ),
                        const SizedBox(width: 6),
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                          decoration: BoxDecoration(
                            color: cani.brand.withValues(alpha: 0.1),
                            borderRadius: BorderRadius.circular(4),
                          ),
                          child: Text(
                            'Trọng tài',
                            style: TextStyle(fontSize: 9.5, fontWeight: FontWeight.w700, color: cani.brand),
                          ),
                        ),
                      ],
                    ),
                  ),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                  decoration: BoxDecoration(
                    color: isMe ? cani.brand : Theme.of(context).cardColor,
                    borderRadius: BorderRadius.only(
                      topLeft: const Radius.circular(16),
                      topRight: const Radius.circular(16),
                      bottomLeft: Radius.circular(isMe ? 16 : 2),
                      bottomRight: Radius.circular(isMe ? 2 : 16),
                    ),
                    border: isMe ? null : Border.all(color: cani.border),
                    boxShadow: [
                      BoxShadow(
                        color: Colors.black.withValues(alpha: 0.03),
                        blurRadius: 4,
                        offset: const Offset(0, 1),
                      ),
                    ],
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        message.messageText,
                        style: TextStyle(
                          fontSize: 13.5,
                          color: isMe ? Colors.white : cani.textMain,
                          height: 1.4,
                        ),
                      ),
                      if (attUrl != null && attUrl.isNotEmpty) ...[
                        const SizedBox(height: 8),
                        if (looksLikeImage)
                          ClipRRect(
                            borderRadius: BorderRadius.circular(8),
                            child: buildResolvedImageWidget(
                              attUrl,
                              height: 120,
                              width: 180,
                              fit: BoxFit.cover,
                            ),
                          )
                        else
                          Text(
                            'Đính kèm: $attUrl',
                            style: TextStyle(
                              fontSize: 11,
                              color: isMe ? Colors.white70 : cani.textMuted,
                              decoration: TextDecoration.underline,
                            ),
                          ),
                      ],
                      const SizedBox(height: 4),
                      Row(
                        mainAxisSize: MainAxisSize.min,
                        mainAxisAlignment: MainAxisAlignment.end,
                        children: [
                          Text(
                            _formatTime(message.createdAt),
                            style: TextStyle(
                              fontSize: 10,
                              color: isMe ? Colors.white70 : cani.textMuted,
                            ),
                          ),
                        ],
                      ),
                    ],
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
