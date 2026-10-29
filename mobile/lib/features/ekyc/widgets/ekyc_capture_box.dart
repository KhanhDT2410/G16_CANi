import 'dart:typed_data';
import 'package:flutter/material.dart';

/// EkycCaptureBox - Hộp chụp/tải ảnh CCCD mặt trước, mặt sau và chân dung
class EkycCaptureBox extends StatelessWidget {
  final String step;
  final String title;
  final String subtitle;
  final IconData icon;
  final Uint8List? bytes;
  final String? fileName;
  final VoidCallback onTap;

  const EkycCaptureBox({
    super.key,
    required this.step,
    required this.title,
    required this.subtitle,
    required this.icon,
    required this.bytes,
    required this.fileName,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final hasImage = bytes != null;

    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(12),
      child: Container(
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: theme.colorScheme.surface,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(
            color: hasImage ? Colors.green : theme.colorScheme.outlineVariant,
            width: hasImage ? 2 : 1,
          ),
        ),
        child: Row(
          children: [
            ClipRRect(
              borderRadius: BorderRadius.circular(8),
              child: hasImage
                  ? Image.memory(
                      bytes!,
                      width: 60,
                      height: 60,
                      fit: BoxFit.cover,
                    )
                  : Container(
                      width: 60,
                      height: 60,
                      color: theme.colorScheme.primaryContainer
                          .withValues(alpha: 0.5),
                      child: Icon(
                        icon,
                        color: theme.colorScheme.primary,
                        size: 28,
                      ),
                    ),
            ),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Container(
                        padding: const EdgeInsets.symmetric(
                            horizontal: 6, vertical: 2),
                        decoration: BoxDecoration(
                          color: theme.colorScheme.primary,
                          borderRadius: BorderRadius.circular(4),
                        ),
                        child: Text(
                          'Bước $step',
                          style: const TextStyle(
                            color: Colors.white,
                            fontSize: 10,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          title,
                          style: const TextStyle(
                            fontWeight: FontWeight.bold,
                            fontSize: 14,
                          ),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 4),
                  Text(
                    hasImage ? 'Đã chọn: $fileName' : subtitle,
                    style: TextStyle(
                      fontSize: 12,
                      color: hasImage
                          ? Colors.green
                          : theme.colorScheme.onSurfaceVariant,
                      fontWeight:
                          hasImage ? FontWeight.bold : FontWeight.normal,
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                ],
              ),
            ),
            Icon(
              hasImage ? Icons.check_circle : Icons.camera_alt,
              color: hasImage ? Colors.green : theme.colorScheme.primary,
            ),
          ],
        ),
      ),
    );
  }
}
