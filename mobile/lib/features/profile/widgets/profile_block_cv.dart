// CANi · tab "Tài khoản" — Block 4 · Hồ Sơ Năng Lực & Liên Hệ Thụ Hưởng
// Dữ liệu thời gian thực từ Database (PostgreSQL / Spring Boot API).
// Đảm bảo tuyệt đối không hardcode, hỗ trợ mọi độ phân giải màn hình nhỏ (chống overflow).

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/core/constants/banks.dart';
import 'package:cani/features/ekyc/screens/ekyc_verification_screen.dart';
import 'package:cani/features/profile/providers/profile_providers.dart';
import 'package:cani/features/profile/providers/worker_bank_provider.dart';
import 'package:cani/features/profile/providers/worker_experience_provider.dart';
import 'package:cani/features/profile/screens/worker_bank_settings_screen.dart';
import 'package:cani/features/profile/widgets/profile_tokens.dart';
import 'package:cani/features/profile/widgets/worker_experience_edit_sheet.dart';

class ProfileCvBlock extends ConsumerWidget {
  const ProfileCvBlock({super.key});

  void _openEditSheet(BuildContext context, WidgetRef ref) {
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
        focusBio: true,
      ),
    );
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final t = Theme.of(context).profileX;
    final data = ref.watch(profileDataProvider);
    final expState = ref.watch(workerExperienceProvider);
    final bankState = ref.watch(workerBankProvider);

    final exp = expState.experience;
    final bank = bankState.bankAccount;
    final user = data.user;

    // Tính toán đánh giá thực tế từ DB, không dùng giá trị giả định (Zero-hardcode)
    final double realRating = (exp?.ratingAvg != null && exp!.ratingAvg > 0)
        ? exp.ratingAvg
        : data.avgRating;

    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: t.surface,
        borderRadius: BorderRadius.circular(t.radius),
        border: Border.all(color: t.border),
        boxShadow: [
          BoxShadow(
            color: t.shadowColor,
            blurRadius: 8,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // 1. Header: Tiêu đề & Nút chỉnh sửa
          Row(
            children: [
              Expanded(
                child: Row(
                  children: [
                    Icon(Icons.workspace_premium_rounded, size: 20, color: t.accentDeep),
                    const SizedBox(width: 8),
                    Flexible(
                      child: Text(
                        'Hồ sơ năng lực',
                        style: Theme.of(context).textTheme.titleSmall?.copyWith(
                              color: t.text,
                              fontWeight: FontWeight.bold,
                            ),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                  ],
                ),
              ),
              InkWell(
                onTap: () => _openEditSheet(context, ref),
                borderRadius: BorderRadius.circular(6),
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 4),
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
              ),
            ],
          ),
          const SizedBox(height: 14),

          // 2. Thẻ Thống kê Thực chiến (3 Cột chỉ số trực tiếp từ DB)
          Row(
            children: [
              Expanded(
                child: _MetricCard(
                  icon: Icons.assignment_turned_in_outlined,
                  value: '${exp?.totalShiftsCompleted ?? 0}',
                  suffix: ' ca',
                  label: 'Hoàn thành',
                  accentColor: const Color(0xFF10B981),
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: _MetricCard(
                  icon: Icons.shield_outlined,
                  value: '${exp?.reliabilityScore ?? 100}',
                  suffix: '/100',
                  label: 'Điểm uy tín',
                  accentColor: const Color(0xFF3B82F6),
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: _MetricCard(
                  icon: Icons.star_rounded,
                  value: realRating > 0 ? realRating.toStringAsFixed(1) : 'Chưa có',
                  suffix: realRating > 0 ? ' ★' : '',
                  label: 'Đánh giá',
                  accentColor: const Color(0xFFF59E0B),
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),

          // 3. Trạng thái xác thực CCCD (Co giãn an toàn trên màn hình nhỏ)
          _CccdStatusTile(
            isApproved: data.isEkycApproved,
            isPending: data.isEkycPending,
            isRejected: data.isEkycRejected,
            onTap: () => Navigator.of(context).push(
              MaterialPageRoute(builder: (_) => const EkycVerificationScreen()),
            ),
          ),
          const SizedBox(height: 14),

          // 4. Thông tin liên hệ & Tài khoản ngân hàng VietQR thụ hưởng
          _ContactAndBankSection(
            phone: user?.phone,
            email: user?.email,
            bankCode: bank?.bankCode,
            bankAccountNumber: bank?.bankAccountNumber,
            onBankTap: () => Navigator.of(context).push(
              MaterialPageRoute(builder: (_) => const WorkerBankSettingsScreen()),
            ),
          ),
          const SizedBox(height: 14),

          // 5. Giới thiệu bản thân (Personal Bio)
          _BioSection(
            bio: exp?.bio,
            onTap: () => _openEditSheet(context, ref),
          ),
        ],
      ),
    );
  }
}

class _MetricCard extends StatelessWidget {
  const _MetricCard({
    required this.icon,
    required this.value,
    required this.suffix,
    required this.label,
    required this.accentColor,
  });

  final IconData icon;
  final String value;
  final String suffix;
  final String label;
  final Color accentColor;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).profileX;

    return Container(
      padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 6),
      decoration: BoxDecoration(
        color: t.canvas,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: t.border.withValues(alpha: 0.6)),
      ),
      child: Column(
        children: [
          Icon(icon, size: 18, color: accentColor),
          const SizedBox(height: 4),
          FittedBox(
            fit: BoxFit.scaleDown,
            child: RichText(
              text: TextSpan(
                text: value,
                style: TextStyle(
                  fontSize: 14.5,
                  fontWeight: FontWeight.bold,
                  color: t.text,
                ),
                children: [
                  if (suffix.isNotEmpty)
                    TextSpan(
                      text: suffix,
                      style: TextStyle(
                        fontSize: 11,
                        fontWeight: FontWeight.w600,
                        color: t.muted,
                      ),
                    ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 2),
          Text(
            label,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: TextStyle(
              fontSize: 10.5,
              fontWeight: FontWeight.w500,
              color: t.muted,
            ),
          ),
        ],
      ),
    );
  }
}

class _CccdStatusTile extends StatelessWidget {
  const _CccdStatusTile({
    required this.isApproved,
    required this.isPending,
    required this.isRejected,
    required this.onTap,
  });

  final bool isApproved;
  final bool isPending;
  final bool isRejected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).profileX;

    final String statusText;
    final IconData statusIcon;
    final Color statusColor;

    if (isApproved) {
      statusText = 'Đã xác thực CCCD';
      statusIcon = Icons.check_circle_rounded;
      statusColor = const Color(0xFF10B981);
    } else if (isPending) {
      statusText = 'Đang chờ duyệt CCCD';
      statusIcon = Icons.hourglass_top_rounded;
      statusColor = const Color(0xFFF59E0B);
    } else if (isRejected) {
      statusText = 'Cần bổ sung CCCD';
      statusIcon = Icons.error_outline_rounded;
      statusColor = const Color(0xFFEF4444);
    } else {
      statusText = 'Chưa xác thực CCCD';
      statusIcon = Icons.verified_user_outlined;
      statusColor = const Color(0xFF64748B);
    }

    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(10),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
        decoration: BoxDecoration(
          color: statusColor.withValues(alpha: 0.08),
          borderRadius: BorderRadius.circular(10),
          border: Border.all(color: statusColor.withValues(alpha: 0.2)),
        ),
        child: Row(
          children: [
            Icon(statusIcon, size: 17, color: statusColor),
            const SizedBox(width: 8),
            Text(
              'CCCD',
              style: TextStyle(
                fontSize: 12,
                fontWeight: FontWeight.w600,
                color: t.text,
              ),
            ),
            const SizedBox(width: 8),
            Expanded(
              child: Text(
                statusText,
                textAlign: TextAlign.end,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(
                  fontSize: 11.5,
                  fontWeight: FontWeight.w700,
                  color: statusColor,
                ),
              ),
            ),
            const SizedBox(width: 4),
            Icon(Icons.chevron_right_rounded, size: 16, color: statusColor),
          ],
        ),
      ),
    );
  }
}

class _ContactAndBankSection extends StatelessWidget {
  const _ContactAndBankSection({
    required this.phone,
    required this.email,
    required this.bankCode,
    required this.bankAccountNumber,
    required this.onBankTap,
  });

  final String? phone;
  final String? email;
  final String? bankCode;
  final String? bankAccountNumber;
  final VoidCallback onBankTap;

  String _formatBankText() {
    if (bankAccountNumber == null || bankAccountNumber!.trim().isEmpty) {
      return 'Chưa cài đặt (Chạm để thêm)';
    }
    final bankInfo = kSupportedBanks.firstWhere(
      (b) => b.code.toUpperCase() == (bankCode ?? '').toUpperCase(),
      orElse: () => BankInfo(
        code: bankCode ?? '',
        name: bankCode ?? 'Ngân hàng',
        shortName: bankCode ?? 'Ngân hàng',
      ),
    );
    return '${bankInfo.shortName} • $bankAccountNumber';
  }

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).profileX;
    final hasBank = bankAccountNumber != null && bankAccountNumber!.trim().isNotEmpty;
    final hasPhone = phone != null && phone!.trim().isNotEmpty;

    return Container(
      decoration: BoxDecoration(
        color: t.canvas,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: t.border.withValues(alpha: 0.6)),
      ),
      child: Column(
        children: [
          // 1. Số điện thoại
          _InfoTile(
            icon: Icons.phone_outlined,
            label: 'Số điện thoại',
            value: hasPhone ? phone!.trim() : 'Chưa liên kết',
            valueColor: hasPhone ? t.text : t.muted,
          ),
          Divider(height: 1, thickness: 1, color: t.border.withValues(alpha: 0.4)),

          // 2. Email tài khoản
          _InfoTile(
            icon: Icons.email_outlined,
            label: 'Email tài khoản',
            value: email != null && email!.trim().isNotEmpty ? email!.trim() : 'Chưa cập nhật',
            valueColor: t.text,
          ),
          Divider(height: 1, thickness: 1, color: t.border.withValues(alpha: 0.4)),

          // 3. Tài khoản ngân hàng VietQR (Có thể bấm để mở cài đặt ngân hàng)
          InkWell(
            onTap: onBankTap,
            borderRadius: const BorderRadius.vertical(bottom: Radius.circular(12)),
            child: _InfoTile(
              icon: Icons.account_balance_outlined,
              label: 'VietQR thụ hưởng',
              value: _formatBankText(),
              valueColor: hasBank ? const Color(0xFF0066FF) : t.accentDeep,
              trailing: Icon(Icons.chevron_right_rounded, size: 16, color: t.muted),
            ),
          ),
        ],
      ),
    );
  }
}

class _InfoTile extends StatelessWidget {
  const _InfoTile({
    required this.icon,
    required this.label,
    required this.value,
    this.valueColor,
    this.trailing,
  });

  final IconData icon;
  final String label;
  final String value;
  final Color? valueColor;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).profileX;

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      child: Row(
        children: [
          Icon(icon, size: 16, color: t.accentDeep),
          const SizedBox(width: 8),
          Text(
            label,
            style: TextStyle(
              fontSize: 12,
              fontWeight: FontWeight.w500,
              color: t.muted,
            ),
          ),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              value,
              textAlign: TextAlign.end,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                fontSize: 12,
                fontWeight: FontWeight.w600,
                color: valueColor ?? t.text,
              ),
            ),
          ),
          if (trailing != null) ...[
            const SizedBox(width: 4),
            trailing!,
          ],
        ],
      ),
    );
  }
}

class _BioSection extends StatelessWidget {
  const _BioSection({
    required this.bio,
    required this.onTap,
  });

  final String? bio;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).profileX;
    final hasBio = bio != null && bio!.trim().isNotEmpty;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Icon(Icons.notes_rounded, size: 16, color: t.muted),
            const SizedBox(width: 6),
            Text(
              'Giới thiệu bản thân',
              style: TextStyle(
                fontSize: 12,
                fontWeight: FontWeight.w600,
                color: t.muted,
              ),
            ),
          ],
        ),
        const SizedBox(height: 6),
        InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(10),
          child: Container(
            width: double.infinity,
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: t.canvas,
              borderRadius: BorderRadius.circular(10),
              border: Border.all(
                color: hasBio ? t.border.withValues(alpha: 0.6) : t.accentDeep.withValues(alpha: 0.3),
              ),
            ),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  child: Text(
                    hasBio ? bio!.trim() : 'Chưa cập nhật giới thiệu bản thân (Chạm để nhập)...',
                    style: TextStyle(
                      fontSize: 12.5,
                      fontStyle: hasBio ? FontStyle.normal : FontStyle.italic,
                      color: hasBio ? t.text : t.accentDeep,
                      height: 1.45,
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
    );
  }
}
