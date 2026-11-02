// CANi · tab "Tài khoản" — Block 5 · Settings (Cài đặt).

import 'package:flutter/material.dart';

import 'package:cani/features/profile/widgets/profile_tokens.dart';

class ProfileSettingsBlock extends StatelessWidget {
  const ProfileSettingsBlock({
    required this.notificationsEnabled,
    required this.onToggleNotifications,
    required this.onTapTermsAndPolicies,
    super.key,
  });

  final bool notificationsEnabled;
  final ValueChanged<bool> onToggleNotifications;
  final VoidCallback onTapTermsAndPolicies;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).profileX;
    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: t.surface,
        borderRadius: BorderRadius.circular(t.radius),
        border: Border.all(color: t.border),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Cài đặt',
            style: Theme.of(context).textTheme.titleSmall?.copyWith(
                  color: t.text,
                  fontWeight: FontWeight.bold,
                ),
          ),
          const SizedBox(height: 10),
          _NotificationRow(
            enabled: notificationsEnabled,
            onChanged: onToggleNotifications,
          ),
          const SizedBox(height: 4),
          _ActionRow(
            icon: Icons.gavel_outlined,
            label: 'Quy chế & Điều khoản',
            subtitle: 'Điều khoản sử dụng & Quy chế điểm tín nhiệm',
            onTap: onTapTermsAndPolicies,
          ),
        ],
      ),
    );
  }
}

class _NotificationRow extends StatelessWidget {
  const _NotificationRow({
    required this.enabled,
    required this.onChanged,
  });

  final bool enabled;
  final ValueChanged<bool> onChanged;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).profileX;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4, horizontal: 4),
      child: Row(
        children: [
          Icon(Icons.notifications_active_outlined, size: 20, color: t.accentDeep),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Thông báo việc làm & Ca trực',
                  style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                        color: t.text,
                        fontWeight: FontWeight.w500,
                      ),
                ),
                Text(
                  enabled
                      ? 'Đang nhận thông báo ca khẩn & đề xuất việc'
                      : 'Đã tạm tắt thông báo ứng dụng',
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(
                        color: t.muted,
                        fontSize: 11,
                      ),
                ),
              ],
            ),
          ),
          Switch(
            value: enabled,
            onChanged: onChanged,
            activeThumbColor: t.accent,
            inactiveTrackColor: t.skeleton,
          ),
        ],
      ),
    );
  }
}

class _ActionRow extends StatelessWidget {
  const _ActionRow({
    required this.icon,
    required this.label,
    required this.subtitle,
    required this.onTap,
  });

  final IconData icon;
  final String label;
  final String subtitle;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).profileX;
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(8),
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 4),
          child: Row(
            children: [
              Icon(icon, size: 20, color: t.accentDeep),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      label,
                      style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                            color: t.text,
                            fontWeight: FontWeight.w500,
                          ),
                    ),
                    Text(
                      subtitle,
                      style: Theme.of(context).textTheme.bodySmall?.copyWith(
                            color: t.muted,
                            fontSize: 11,
                          ),
                    ),
                  ],
                ),
              ),
              Icon(Icons.chevron_right_rounded, size: 20, color: t.muted),
            ],
          ),
        ),
      ),
    );
  }
}
