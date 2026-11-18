// CANi · tab "Tài khoản" — Block 4 · Hồ Sơ Năng Lực & Kinh Nghiệm
// Kết nối trực tiếp với profileDataProvider (Skill Points, Tier, eKYC status) & workerExperienceProvider (Bio, Years of Experience)

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/features/profile/providers/profile_providers.dart';
import 'package:cani/features/profile/providers/worker_experience_provider.dart';
import 'package:cani/features/profile/widgets/profile_tokens.dart';
import 'package:cani/features/profile/widgets/worker_experience_edit_sheet.dart';
import 'package:cani/features/skill_verification/screens/skill_hub_screen.dart';

class ProfileCvBlock extends ConsumerWidget {
  const ProfileCvBlock({super.key});

  void _openEditSheet(BuildContext context, WidgetRef ref, {bool focusBio = false}) {
    final exp = ref.read(workerExperienceProvider).experience;
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Theme.of(context).cardColor,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (_) => WorkerExperienceEditSheet(
        initialExperience: exp,
        focusBio: focusBio,
      ),
    );
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final t = Theme.of(context).profileX;
    final data = ref.watch(profileDataProvider);
    final expState = ref.watch(workerExperienceProvider);
    final exp = expState.experience;
    final user = data.user;

    // Phân định chính xác 4 trạng thái eKYC
    final ekycStatusText = data.isEkycApproved
        ? 'Đã xác thực hợp lệ'
        : (data.isEkycPending
            ? 'Đang chờ duyệt'
            : (data.isEkycRejected ? 'Cần bổ sung / Bị từ chối' : 'Chưa gửi hồ sơ'));

    final ekycStatusColor = data.isEkycApproved
        ? const Color(0xFF059669)
        : (data.isEkycPending
            ? const Color(0xFFD97706)
            : (data.isEkycRejected ? const Color(0xFFDC2626) : const Color(0xFF64748B)));

    final yearsExpText = '${exp?.yearsExperience ?? 0} năm kinh nghiệm';

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
                'Hồ sơ năng lực & Kinh nghiệm',
                style: Theme.of(context).textTheme.titleSmall?.copyWith(
                      color: t.text,
                      fontWeight: FontWeight.bold,
                    ),
              ),
              const Spacer(),
              InkWell(
                onTap: () => _openEditSheet(context, ref),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(Icons.edit_outlined, size: 14, color: t.accentDeep),
                    const SizedBox(width: 4),
                    Text(
                      'Chỉnh sửa',
                      style: TextStyle(
                        fontSize: 12,
                        fontWeight: FontWeight.w700,
                        color: t.accentDeep,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 12),
              InkWell(
                onTap: () {
                  Navigator.of(context).push(
                    MaterialPageRoute(builder: (_) => const SkillHubScreen()),
                  );
                },
                child: Text(
                  'Skill Hub →',
                  style: TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.w700,
                    color: t.accentDeep,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 14),

          // Lưới thông tin năng lực thực tế
          _InfoRow(
            icon: Icons.military_tech_outlined,
            label: 'Hạng nhân sự & Điểm uy tín',
            value: '${data.tier} • ${data.totalSp} SP',
            isHighlight: true,
          ),
          const Divider(height: 14, color: Color(0xFFF1F5F9)),

          _InfoRow(
            icon: Icons.history_edu_outlined,
            label: 'Kinh nghiệm sự kiện',
            value: yearsExpText,
            isHighlight: (exp?.yearsExperience ?? 0) > 0,
            onTap: () => _openEditSheet(context, ref, focusBio: false),
          ),
          const Divider(height: 14, color: Color(0xFFF1F5F9)),

          _InfoRow(
            icon: Icons.star_border_rounded,
            label: 'Đánh giá từ nhà tổ chức',
            value: data.avgRating > 0 ? '${data.avgRating.toStringAsFixed(1)} / 5.0' : 'Chưa có lượt đánh giá',
          ),
          const Divider(height: 14, color: Color(0xFFF1F5F9)),

          _InfoRow(
            icon: Icons.badge_outlined,
            label: 'Xác thực căn cước công dân',
            value: ekycStatusText,
            valueColor: ekycStatusColor,
          ),
          const Divider(height: 14, color: Color(0xFFF1F5F9)),

          _InfoRow(
            icon: Icons.phone_outlined,
            label: 'Số điện thoại liên hệ',
            value: user?.phone != null && user!.phone!.isNotEmpty ? user.phone! : 'Chưa liên kết',
          ),
          const Divider(height: 14, color: Color(0xFFF1F5F9)),

          _InfoRow(
            icon: Icons.email_outlined,
            label: 'Email tài khoản',
            value: user?.email ?? 'Chưa cập nhật',
          ),
          const Divider(height: 14, color: Color(0xFFF1F5F9)),

          // Giới thiệu bản thân (Bio) - Cho phép chạm trực tiếp để chỉnh sửa
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Icon(Icons.notes_rounded, size: 18, color: t.accentDeep),
                  const SizedBox(width: 10),
                  Text(
                    'Giới thiệu bản thân',
                    style: Theme.of(context).textTheme.bodySmall?.copyWith(
                          color: t.text,
                          fontWeight: FontWeight.w500,
                        ),
                  ),
                  const Spacer(),
                  InkWell(
                    onTap: () => _openEditSheet(context, ref, focusBio: true),
                    borderRadius: BorderRadius.circular(4),
                    child: Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(Icons.edit_outlined, size: 13, color: t.accentDeep),
                          const SizedBox(width: 4),
                          Text(
                            'Chỉnh sửa',
                            style: TextStyle(
                              fontSize: 12,
                              fontWeight: FontWeight.w600,
                              color: t.accentDeep,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 6),
              InkWell(
                onTap: () => _openEditSheet(context, ref, focusBio: true),
                borderRadius: BorderRadius.circular(8),
                child: Container(
                  width: double.infinity,
                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                  decoration: BoxDecoration(
                    color: t.surface.withValues(alpha: 0.5),
                    borderRadius: BorderRadius.circular(8),
                    border: Border.all(
                      color: (exp?.bio == null || exp!.bio!.trim().isEmpty)
                          ? const Color(0xFF0066FF).withValues(alpha: 0.3)
                          : t.border.withValues(alpha: 0.5),
                    ),
                  ),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Expanded(
                        child: Text(
                          (exp?.bio != null && exp!.bio!.trim().isNotEmpty)
                              ? exp.bio!.trim()
                              : 'Chưa cập nhật giới thiệu bản thân (Chạm để nhập)...',
                          style: TextStyle(
                            fontSize: 12.5,
                            fontStyle: (exp?.bio == null || exp!.bio!.trim().isEmpty) ? FontStyle.italic : FontStyle.normal,
                            color: (exp?.bio == null || exp!.bio!.trim().isEmpty) ? const Color(0xFF0066FF) : t.text,
                            height: 1.4,
                          ),
                        ),
                      ),
                      const SizedBox(width: 8),
                      Icon(
                        Icons.edit_note_rounded,
                        size: 18,
                        color: t.accentDeep.withValues(alpha: 0.7),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _InfoRow extends StatelessWidget {
  const _InfoRow({
    required this.icon,
    required this.label,
    required this.value,
    this.isHighlight = false,
    this.valueColor,
    this.onTap,
  });

  final IconData icon;
  final String label;
  final String value;
  final bool isHighlight;
  final Color? valueColor;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).profileX;

    final row = Row(
      children: [
        Icon(icon, size: 18, color: t.accentDeep),
        const SizedBox(width: 10),
        Text(
          label,
          style: Theme.of(context).textTheme.bodySmall?.copyWith(
                color: t.text,
                fontWeight: FontWeight.w500,
              ),
        ),
        const SizedBox(width: 8),
        Expanded(
          child: Text(
            value,
            textAlign: TextAlign.end,
            overflow: TextOverflow.ellipsis,
            maxLines: 1,
            style: TextStyle(
              fontSize: 12.5,
              fontWeight: isHighlight ? FontWeight.w700 : FontWeight.w600,
              color: valueColor ?? (isHighlight ? const Color(0xFF0066FF) : t.text),
            ),
          ),
        ),
        if (onTap != null) ...[
          const SizedBox(width: 4),
          Icon(Icons.edit_outlined, size: 13, color: t.accentDeep.withValues(alpha: 0.8)),
        ],
      ],
    );

    if (onTap != null) {
      return InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(4),
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 2),
          child: row,
        ),
      );
    }

    return row;
  }
}
