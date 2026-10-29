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
    final hasImage = bytes != null;

    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(14),
      child: Container(
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(
            color: hasImage ? const Color(0xFF10B981) : const Color(0xFFE2E8F0),
            width: 1.5,
          ),
          boxShadow: [
            BoxShadow(
              color: const Color(0xFF000000).withValues(alpha: 0.04),
              blurRadius: 8,
              offset: const Offset(0, 2),
            ),
          ],
        ),
        child: Row(
          children: [
            ClipRRect(
              borderRadius: BorderRadius.circular(10),
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
                      color: const Color(0xFFEFF6FF),
                      child: Icon(
                        icon,
                        color: const Color(0xFF0077FF),
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
                            horizontal: 7, vertical: 2.5),
                        decoration: BoxDecoration(
                          color: const Color(0xFFEFF6FF),
                          borderRadius: BorderRadius.circular(6),
                          border: Border.all(
                            color: const Color(0xFFDBEAFE),
                            width: 1,
                          ),
                        ),
                        child: Text(
                          'Bước $step',
                          style: const TextStyle(
                            color: Color(0xFF0077FF),
                            fontSize: 11,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          title,
                          style: const TextStyle(
                            fontWeight: FontWeight.w700,
                            fontSize: 14.5,
                            color: Color(0xFF0B1B3D),
                            letterSpacing: -0.2,
                          ),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 5),
                  Text(
                    hasImage ? 'Đã chọn: $fileName' : subtitle,
                    style: TextStyle(
                      fontSize: 12,
                      color: hasImage
                          ? const Color(0xFF10B981)
                          : const Color(0xFF64748B),
                      fontWeight:
                          hasImage ? FontWeight.w700 : FontWeight.w400,
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                ],
              ),
            ),
            Icon(
              hasImage ? Icons.check_circle : Icons.camera_alt,
              color: hasImage ? const Color(0xFF10B981) : const Color(0xFF0077FF),
            ),
          ],
        ),
      ),
    );
  }
}

