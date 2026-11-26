// CANi · tab "Tài khoản" — shared state widgets (skeleton / error+retry / coming-soon).
// Dùng lại cho mọi block: giữ mỗi block widget nhỏ, mỗi file < 200 LOC.
// Màu: Theme.of(context).profileX.

import 'package:flutter/material.dart';

import 'package:cani/features/profile/widgets/profile_tokens.dart';

/// Khung card dùng chung cho mọi block: surface + radius + border.
class BlockCard extends StatelessWidget {
  const BlockCard({super.key, required this.child, this.padding = 16});
  final Widget child;
  final double padding;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).profileX;
    return Container(
      margin: const EdgeInsets.all(16),
      padding: EdgeInsets.all(padding),
      decoration: BoxDecoration(
        color: t.surface,
        borderRadius: BorderRadius.circular(t.radius),
        border: Border.all(color: t.border),
      ),
      child: child,
    );
  }
}

/// Dải skeleton shimmer đơn giản (không hardcode màu — dùng token).
class BlockSkeleton extends StatelessWidget {
  const BlockSkeleton({super.key, this.rows = 3});
  final int rows;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).profileX;
    return Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          for (var i = 0; i < rows; i++) ...[
            if (i > 0) const SizedBox(height: 10),
            _ShimmerBar(width: i == 0 ? 140 : 220, color: t.skeleton),
          ],
        ],
      ),
    );
  }
}

class _ShimmerBar extends StatelessWidget {
  const _ShimmerBar({required this.width, required this.color});
  final double width;
  final Color color;

  @override
  Widget build(BuildContext context) =>
      Container(width: width, height: 12, decoration: BoxDecoration(color: color, borderRadius: BorderRadius.circular(6)));
}

