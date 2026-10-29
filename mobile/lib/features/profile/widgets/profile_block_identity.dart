// CANi · tab "Tài khoản" — Block 1 · Identity Header.
// avatarUrl thật, fallback initials; name / email / role chip. Màu: profileX.

import 'package:flutter/material.dart';

import 'package:cani/features/auth/models/user_dto.dart';
import 'package:cani/features/profile/widgets/profile_states.dart';
import 'package:cani/features/profile/widgets/profile_tokens.dart';

class ProfileIdentityHeader extends StatelessWidget {
  const ProfileIdentityHeader({required this.user, super.key});

  final UserDto? user;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).profileX;

    return BlockCard(
      child: user == null
          ? BlockSkeleton(rows: 2)
          : Row(
              children: [
                CircleAvatar(
                  radius: 28,
                  backgroundColor: t.accentSoft,
                  child: user!.avatarUrl == null
                      ? Text(
                          _initials(user!.fullName),
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
                              user!.fullName,
                              style: Theme.of(context)
                                  .textTheme
                                  .titleMedium
                                  ?.copyWith(color: t.text, fontWeight: FontWeight.bold),
                            ),
                          ),
                          if (user!.role.isNotEmpty) ...[
                            const SizedBox(width: 6),
                            Container(
                              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                              decoration: BoxDecoration(
                                color: t.accentSoft,
                                borderRadius: BorderRadius.circular(99),
                              ),
                              child: Text(
                                user!.role,
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
                      Text(user!.email,
                          style: Theme.of(context).textTheme.bodySmall?.copyWith(color: t.muted)),
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
