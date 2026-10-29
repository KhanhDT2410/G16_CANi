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

/// Màn loading skeleton toàn trang (trạng thái loading của userAsync).
class ProfileSkeletonScreen extends StatelessWidget {
  const ProfileSkeletonScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).profileX;
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        for (var i = 0; i < 8; i++) ...[
          Container(
            margin: const EdgeInsets.only(bottom: 16),
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: t.surface,
              borderRadius: BorderRadius.circular(t.radius),
              border: Border.all(color: t.border),
            ),
            child: BlockSkeleton(rows: i == 0 ? 2 : 3),
          ),
        ],
      ],
    );
  }
}

/// Thẻ lỗi + nút "Thử lại" — dùng cho từng block khi provider trả error.
class ProfileErrorCard extends StatelessWidget {
  const ProfileErrorCard({super.key, required this.message, required this.onRetry});
  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).profileX;
    return Container(
      margin: const EdgeInsets.all(16),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: t.surface,
        borderRadius: BorderRadius.circular(t.radius),
        border: Border.all(color: t.danger.withValues(alpha: 0.4)),
      ),
      child: Row(
        children: [
          Icon(Icons.error_outline, color: t.danger, size: 22),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  message,
                  style: Theme.of(context).textTheme.bodyMedium?.copyWith(color: t.text),
                ),
                const SizedBox(height: 8),
                TextButton(
                  onPressed: onRetry,
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const Icon(Icons.refresh, size: 15),
                      const SizedBox(width: 6),
                      const Text('Thử lại'),
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

/// Trạng thái trang-level error (dùng khi cả userAsync lỗi).
class ProfileErrorScreen extends StatelessWidget {
  const ProfileErrorScreen({super.key, required this.message, required this.onRetry});
  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).profileX;
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(Icons.error_outline, size: 40, color: t.danger),
          const SizedBox(height: 12),
          Text(
            message,
            style: Theme.of(context).textTheme.titleMedium?.copyWith(color: t.text),
          ),
          const SizedBox(height: 16),
          FilledButton.icon(
            onPressed: onRetry,
            icon: const Icon(Icons.refresh, size: 18),
            label: const Text('Thử lại'),
          ),
        ],
      ),
    );
  }
}

