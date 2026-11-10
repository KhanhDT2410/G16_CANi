// CANi · tab "Tài khoản" — Block 1 · Identity Header.
// avatarUrl thật, fallback initials; name / email / role chip. Màu: profileX.

import 'package:flutter/material.dart';

import 'package:cani/features/auth/models/user_dto.dart';
import 'package:cani/features/profile/widgets/profile_states.dart';
import 'package:cani/features/profile/widgets/profile_tokens.dart';

class ProfileIdentityHeader extends StatefulWidget {
  const ProfileIdentityHeader({required this.user, super.key});

  final UserDto? user;

  @override
  State<ProfileIdentityHeader> createState() => _ProfileIdentityHeaderState();
}

class _ProfileIdentityHeaderState extends State<ProfileIdentityHeader> {
  bool _showEmail = false;

  String _maskEmail(String email) {
    if (email.isEmpty) return '';
    final parts = email.split('@');
    if (parts.length != 2) return email;
    final name = parts[0];
    final domain = parts[1];
    if (name.length <= 4) {
      return '${name[0]}***@$domain';
    }
    final prefix = name.substring(0, 3);
    final suffix = name.substring(name.length - 1);
    return '$prefix****$suffix@$domain';
  }

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).profileX;
    final user = widget.user;

    return BlockCard(
      child: user == null
          ? BlockSkeleton(rows: 2)
          : Row(
              children: [
                CircleAvatar(
                  radius: 28,
                  backgroundColor: t.accentSoft,
                  child: user.avatarUrl == null
                      ? Text(
                          _initials(user.fullName),
                          style: Theme.of(context)
                              .textTheme
                              .titleMedium
                              ?.copyWith(color: t.accentDeep, fontWeight: FontWeight.bold),
                        )
                      : null,
                ),
                const SizedBox(width: 14),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Expanded(
                            child: Text(
                              user.fullName,
                              style: Theme.of(context)
                                  .textTheme
                                  .titleMedium
                                  ?.copyWith(color: t.text, fontWeight: FontWeight.bold),
                            ),
                          ),
                          if (user.role.isNotEmpty) ...[
                            const SizedBox(width: 6),
                            Container(
                              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                              decoration: BoxDecoration(
                                color: t.accentSoft,
                                borderRadius: BorderRadius.circular(99),
                              ),
                              child: Text(
                                user.role,
                                style: Theme.of(context).textTheme.labelSmall?.copyWith(
                                      color: t.accentDeep,
                                      fontWeight: FontWeight.w700,
                                    ),
                              ),
                            ),
                          ],
                        ],
                      ),
                      const SizedBox(height: 4),
                      Row(
                        children: [
                          Text(
                            _showEmail ? user.email : _maskEmail(user.email),
                            style: Theme.of(context).textTheme.bodySmall?.copyWith(color: t.muted),
                          ),
                          const SizedBox(width: 6),
                          InkWell(
                            onTap: () => setState(() => _showEmail = !_showEmail),
                            borderRadius: BorderRadius.circular(12),
                            child: Padding(
                              padding: const EdgeInsets.all(2),
                              child: Icon(
                                _showEmail ? Icons.visibility_off_outlined : Icons.visibility_outlined,
                                size: 14,
                                color: t.muted,
                              ),
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              ],
            ),
    );
  }

  String _initials(String name) {
    final parts = name.trim().split(RegExp(r'\s+'));
    return parts.isNotEmpty && parts.first.isNotEmpty ? parts.first[0].toUpperCase() : '?';
  }
}
