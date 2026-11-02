// CANi · tab "Tài khoản" — Block 6 · Security (Bảo mật).

import 'package:flutter/material.dart';

import 'package:cani/features/profile/widgets/profile_tokens.dart';

class ProfileSecurityBlock extends StatelessWidget {
  const ProfileSecurityBlock({
    this.onChangePassword,
    super.key,
  });

  final VoidCallback? onChangePassword;

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
          Row(
            children: [
              Text(
                'Bảo mật',
                style: Theme.of(context).textTheme.titleSmall?.copyWith(
                      color: t.text,
                      fontWeight: FontWeight.bold,
                    ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          _Row(
            icon: Icons.lock_outline,
            label: 'Đổi mật khẩu',
            onTap: onChangePassword,
          ),
          const _Row(
            icon: Icons.link,
            label: 'Tài khoản liên kết (SSO)',
            isComingSoon: true,
          ),
          const _Row(
            icon: Icons.device_hub,
            label: 'Phiên hoạt động',
            isComingSoon: true,
          ),
        ],
      ),
    );
  }
}

class _Row extends StatelessWidget {
  const _Row({
    required this.icon,
    required this.label,
    this.onTap,
    this.isComingSoon = false,
  });

  final IconData icon;
  final String label;
  final VoidCallback? onTap;
  final bool isComingSoon;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).profileX;
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: isComingSoon ? null : onTap,
        borderRadius: BorderRadius.circular(8),
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 9, horizontal: 4),
          child: Row(
            children: [
              Icon(icon, size: 20, color: isComingSoon ? t.muted : t.accentDeep),
              const SizedBox(width: 12),
              Expanded(
                child: Text(
                  label,
                  style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                        color: isComingSoon ? t.muted : t.text,
                        fontWeight: FontWeight.w500,
                      ),
                ),
              ),
              if (isComingSoon)
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                  decoration: BoxDecoration(
                    color: t.chipSoft,
                    borderRadius: BorderRadius.circular(99),
                  ),
                  child: Text(
                    'Sắp có',
                    style: Theme.of(context).textTheme.labelSmall?.copyWith(
                          color: const Color(0xFFB45309),
                          fontSize: 10,
                          fontWeight: FontWeight.w600,
                        ),
                  ),
                )
              else
                Icon(Icons.chevron_right_rounded, size: 20, color: t.muted),
            ],
          ),
        ),
      ),
    );
  }
}
