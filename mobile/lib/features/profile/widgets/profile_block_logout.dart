// CANi · tab "Tài khoản" — Block 8 · Logout Footer.
// Nút destructive full-width, tách bằng divider, kèm confirm dialog.

import 'package:flutter/material.dart';

import 'package:cani/features/profile/widgets/profile_tokens.dart';

class ProfileLogoutFooter extends StatelessWidget {
  const ProfileLogoutFooter({super.key, this.onLogout});

  final VoidCallback? onLogout;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).profileX;
    return Column(children: [
      const Divider(height: 1, color: Colors.transparent),
      const SizedBox(height: 16),
      Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16),
        child: SizedBox(
          width: double.infinity,
          child: OutlinedButton(
            style: OutlinedButton.styleFrom(
              minimumSize: const Size(0, 50),
              side: BorderSide(color: t.danger),
              foregroundColor: t.danger,
              backgroundColor: t.danger.withValues(alpha: 0.06),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
            ),
            onPressed: () => _confirm(context),
            child: Row(mainAxisAlignment: MainAxisAlignment.center, children: [
              const Icon(Icons.logout, size: 18),
              const SizedBox(width: 8),
              Text('Đăng xuất', style: const TextStyle(fontWeight: FontWeight.w700)),
            ]),
          ),
        ),
      ),
      const SizedBox(height: 8),
      Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16),
        child: Text('Bạn sẽ cần đăng nhập lại để quay trở lại.',
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.bodySmall?.copyWith(color: t.muted)),
      ),
      const SizedBox(height: 20),
    ]);
  }

  Future<void> _confirm(BuildContext context) async {
    final t = Theme.of(context).profileX;
    final ok = await showDialog<bool>(
      context: context,
      builder: (c) => AlertDialog(
        title: const Text('Đăng xuất?'),
        content: const Text('Bạn có chắc muốn đăng xuất khỏi CANi?'),
        actions: [
          TextButton(onPressed: () => Navigator.of(c).pop(false), child: const Text('Hủy')),
          FilledButton(
            style:
                FilledButton.styleFrom(backgroundColor: t.danger, foregroundColor: Colors.white),
            onPressed: () => Navigator.of(c).pop(true),
            child: const Text('Đăng xuất'),
          ),
        ],
      ),
    );
    if (ok == true) onLogout?.call();
  }
}
