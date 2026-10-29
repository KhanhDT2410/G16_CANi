// CANi · tab "Tài khoản" — Block 5 · Settings (Cài đặt).
// Chưa có backend → rows cố định (Thông báo / Ngôn ngữ / Múi giờ)
// + ghi chú "chưa kết nối API". Toggle & chọn giá trị chỉ là UI giả lập.

import 'package:flutter/material.dart';

import 'package:cani/features/profile/widgets/profile_tokens.dart';

class ProfileSettingsBlock extends StatelessWidget {
  const ProfileSettingsBlock({super.key});

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
            'Cài đặt',
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
        _Row(
          icon: Icons.notifications_outlined,
          label: 'Thông báo',
          trailing: Switch(
            value: true,
            onChanged: (_) {},
            activeThumbColor: t.accent,
            inactiveTrackColor: t.skeleton,
          ),
        ),
        const _Row(
          icon: Icons.language,
          label: 'Ngôn ngữ',
          value: 'Tiếng Việt',
        ),
        const _Row(
          icon: Icons.schedule,
          label: 'Múi giờ',
          value: 'Asia/Ho_Chi_Minh',
        ),
        const SizedBox(height: 6),
        Text('Mục này chưa kết nối API — lựa chọn chưa được lưu.',
            style: Theme.of(context).textTheme.bodySmall?.copyWith(color: t.muted)),
      ]),
    );
  }
}

class _Row extends StatelessWidget {
  const _Row({
    required this.icon,
    required this.label,
    this.value,
    this.trailing,
  });

  final IconData icon;
  final String label;
  final String? value;
  final Widget? trailing;

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
        if (trailing != null)
          trailing!
        else if (value != null)
          Text(value!,
              style:
                  Theme.of(context).textTheme.bodySmall?.copyWith(color: t.muted)),
      ]),
    );
  }
}
