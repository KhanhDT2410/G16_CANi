// CANi · tab "Tài khoản" — Block 6 · Security (Bảo mật).
// Chưa có backend → rows (Đổi mật khẩu / SSO / Phiên) ghi chú "chưa kết nối API".

import 'package:flutter/material.dart';

import 'package:cani/features/profile/widgets/profile_tokens.dart';

class ProfileSecurityBlock extends StatelessWidget {
  const ProfileSecurityBlock({super.key});

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).profileX;
    return Container(
      margin: const EdgeInsets.all(16),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: t.surface,
        borderRadius: BorderRadius.circular(t.radius),
        border: Border.all(color: t.border),
      ),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(children: [
          Text(
            'Bảo mật',
            style: Theme.of(context).textTheme.titleSmall?.copyWith(
                  color: t.text,
                  fontWeight: FontWeight.bold,
                ),
          ),
          const Spacer(),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
            decoration: BoxDecoration(color: t.chipSoft, borderRadius: BorderRadius.circular(99)),
            child: Text(
              'Sắp ra mắt',
              style: Theme.of(context).textTheme.labelSmall?.copyWith(
                    color: const Color(0xFFB45309),
                    fontWeight: FontWeight.w600,
                  ),
            ),
          ),
        ]),
        const SizedBox(height: 10),
        _Row(icon: Icons.lock_outline, label: 'Đổi mật khẩu'),
        _Row(icon: Icons.link, label: 'Tài khoản liên kết (SSO)'),
        _Row(icon: Icons.device_hub, label: 'Phiên hoạt động'),
        const SizedBox(height: 6),
        Text('Mục này chưa kết nối API — chức năng sẽ bật khi có backend.',
            style: Theme.of(context).textTheme.bodySmall?.copyWith(color: t.muted)),
      ]),
    );
  }
}

class _Row extends StatelessWidget {
  const _Row({required this.icon, required this.label});
  final IconData icon;
  final String label;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).profileX;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 7),
      child: Row(children: [
        Icon(icon, size: 18, color: t.accentDeep),
        const SizedBox(width: 10),
        Expanded(
          child: Text(
            label,
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                  color: t.text,
                  fontWeight: FontWeight.w500,
                ),
          ),
        ),
        Icon(Icons.chevron_right, size: 18, color: t.muted),
      ]),
    );
  }
}
