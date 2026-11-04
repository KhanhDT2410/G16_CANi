// CANi · tab "Tài khoản" — Worker profile screen.
// Composition of profile blocks wired to the real aggregated providers with live auto-refresh.

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/app/theme_provider.dart';
import 'package:cani/features/auth/providers/auth_provider.dart';
import 'package:cani/features/ekyc/providers/ekyc_provider.dart';
import 'package:cani/features/ekyc/screens/ekyc_verification_screen.dart';
import 'package:cani/features/profile/providers/profile_providers.dart';
import 'package:cani/features/profile/widgets/profile_block_cv.dart';
import 'package:cani/features/profile/widgets/profile_block_ekyc.dart';
import 'package:cani/features/profile/widgets/profile_block_identity.dart';
import 'package:cani/features/profile/widgets/profile_block_logout.dart';
import 'package:cani/features/profile/widgets/profile_block_public.dart';
import 'package:cani/features/profile/widgets/profile_block_security.dart';
import 'package:cani/features/profile/widgets/profile_block_settings.dart';
import 'package:cani/features/profile/widgets/profile_block_trust.dart';
import 'package:cani/features/profile/widgets/profile_tokens.dart';
import 'package:cani/features/skill_verification/providers/skill_verification_provider.dart';
import 'package:cani/features/skill_verification/screens/skill_hub_screen.dart';
import 'package:cani/features/skill_verification/widgets/skill_hub_theme.dart';

class WorkerProfileScreen extends ConsumerStatefulWidget {
  const WorkerProfileScreen({super.key});

  @override
  ConsumerState<WorkerProfileScreen> createState() => _WorkerProfileScreenState();
}

class _WorkerProfileScreenState extends ConsumerState<WorkerProfileScreen> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      // Tránh double-fetch: Constructor của notifiers đã tự fetch khi lazy-init.
      // Chỉ chủ động kích hoạt nếu state hiện đang rỗng và không trong quá trình tải.
      final skillState = ref.read(skillVerificationProvider);
      final ekycState = ref.read(ekycProvider);
      if (skillState.skillPoints.isEmpty && !skillState.isLoading) {
        ref.read(skillVerificationProvider.notifier).loadAll();
      }
      if (ekycState.result == null && !ekycState.isLoading) {
        ref.read(ekycProvider.notifier).checkStatus();
      }
    });
  }

  Future<void> _refreshProfileData() async {
    try {
      await Future.wait([
        ref.read(skillVerificationProvider.notifier).loadAll(),
        ref.read(ekycProvider.notifier).checkStatus(),
      ]);
    } catch (e) {
      debugPrint('Lỗi nạp dữ liệu hồ sơ cá nhân: $e');
    }
  }

  @override
  Widget build(BuildContext context) {
    final data = ref.watch(profileDataProvider);
    final t = Theme.of(context).profileX;

    if (data.user == null) {
      return Scaffold(
        backgroundColor: t.canvas,
        appBar: _appBar(context, ref),
        body: const Center(child: Text('Chưa có thông tin người dùng')),
      );
    }

    return Scaffold(
      backgroundColor: t.canvas,
      appBar: _appBar(context, ref),
      body: RefreshIndicator(
        onRefresh: _refreshProfileData,
        child: ListView(
          padding: const EdgeInsets.symmetric(vertical: 16),
          children: [
            ProfileIdentityHeader(user: data.user),
            ProfileTrustSkillStrip(
              totalSp: data.totalSp,
              tier: data.tier,
              avgRating: data.avgRating,
              nextThreshold: SkillHubColors.getNextTierThreshold(data.totalSp),
              nextTierLabel: SkillHubColors.getNextTierName(data.totalSp),
              onTap: () => Navigator.of(context).push(
                MaterialPageRoute(builder: (_) => const SkillHubScreen()),
              ),
            ),
            ProfileEkycCard(
              status: data.ekycResult,
              onTap: () => Navigator.of(context).push(
                MaterialPageRoute(builder: (_) => const EkycVerificationScreen()),
              ),
            ),
            const ProfileCvBlock(),
            const ProfileSettingsBlock(),
            const ProfileSecurityBlock(),
            const ProfilePublicProfileBlock(),
            ProfileLogoutFooter(
              onLogout: () => ref.read(authStateProvider.notifier).logout(),
            ),
          ],
        ),
      ),
    );
  }

  PreferredSizeWidget _appBar(BuildContext context, WidgetRef ref) {
    final t = Theme.of(context).profileX;
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return AppBar(
      title: Text(
        'Tài khoản',
        style: TextStyle(
          color: t.text,
          fontWeight: FontWeight.bold,
          fontSize: 22,
        ),
      ),
      backgroundColor: t.canvas,
      elevation: 0,
      centerTitle: false,
      actions: [
        IconButton(
          icon: Icon(
            isDark ? Icons.light_mode_rounded : Icons.dark_mode_outlined,
            color: isDark ? const Color(0xFFFBBF24) : t.accentDeep,
          ),
          tooltip: isDark ? 'Đang bật Chế độ Tối' : 'Đang bật Chế độ Sáng',
          onPressed: () => _showThemeBottomSheet(context, ref),
        ),
        const SizedBox(width: 8),
      ],
    );
  }

  void _showThemeBottomSheet(BuildContext context, WidgetRef ref) {
    final t = Theme.of(context).profileX;
    final currentMode = ref.read(themeModeProvider);

    showModalBottomSheet(
      context: context,
      backgroundColor: t.surface,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (ctx) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 20, horizontal: 16),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 8),
                child: Text(
                  'Giao diện ứng dụng',
                  style: Theme.of(context).textTheme.titleMedium?.copyWith(
                        color: t.text,
                        fontWeight: FontWeight.bold,
                      ),
                ),
              ),
              const SizedBox(height: 12),
              _buildThemeOption(
                context: context,
                ref: ref,
                ctx: ctx,
                icon: Icons.light_mode_outlined,
                title: 'Chế độ Sáng',
                subtitle: 'Giao diện sáng sủa, thanh lịch',
                mode: ThemeMode.light,
                isSelected: currentMode == ThemeMode.light,
              ),
              _buildThemeOption(
                context: context,
                ref: ref,
                ctx: ctx,
                icon: Icons.dark_mode_outlined,
                title: 'Chế độ Tối',
                subtitle: 'Dễ chịu cho mắt khi làm việc ban đêm',
                mode: ThemeMode.dark,
                isSelected: currentMode == ThemeMode.dark,
              ),
              _buildThemeOption(
                context: context,
                ref: ref,
                ctx: ctx,
                icon: Icons.settings_brightness_outlined,
                title: 'Tự động theo hệ thống',
                subtitle: 'Đồng bộ với cài đặt của thiết bị',
                mode: ThemeMode.system,
                isSelected: currentMode == ThemeMode.system,
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildThemeOption({
    required BuildContext context,
    required WidgetRef ref,
    required BuildContext ctx,
    required IconData icon,
    required String title,
    required String subtitle,
    required ThemeMode mode,
    required bool isSelected,
  }) {
    final t = Theme.of(context).profileX;
    return ListTile(
      leading: Container(
        padding: const EdgeInsets.all(8),
        decoration: BoxDecoration(
          color: isSelected ? t.accent.withValues(alpha: 0.15) : t.chipSoft,
          borderRadius: BorderRadius.circular(10),
        ),
        child: Icon(icon, color: isSelected ? t.accent : t.muted, size: 22),
      ),
      title: Text(
        title,
        style: TextStyle(
          fontWeight: isSelected ? FontWeight.bold : FontWeight.w500,
          color: isSelected ? t.accent : t.text,
        ),
      ),
      subtitle: Text(subtitle, style: TextStyle(fontSize: 12, color: t.muted)),
      trailing: isSelected
          ? Icon(Icons.check_circle_rounded, color: t.accent)
          : null,
      onTap: () async {
        Navigator.of(ctx).pop();
        await ref.read(themeModeProvider.notifier).setThemeMode(mode);
      },
    );
  }
}
