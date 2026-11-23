import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/app/cani_tokens.dart';
import 'package:cani/features/auth/providers/auth_provider.dart';
import 'package:cani/features/ekyc/providers/ekyc_provider.dart';
import 'package:cani/features/ekyc/screens/ekyc_verification_screen.dart';

void showJobRadarEkycRequiredDialog(BuildContext context, WidgetRef ref) {
  final isDark = Theme.of(context).brightness == Brightness.dark;
  showDialog(
    context: context,
    builder: (ctx) => AlertDialog(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
      backgroundColor: isDark ? const Color(0xFF1E293B) : Colors.white,
      title: Row(
        children: [
          Container(
            padding: const EdgeInsets.all(8),
            decoration: BoxDecoration(
              color: const Color(0xFFF59E0B).withValues(alpha: 0.15),
              shape: BoxShape.circle,
            ),
            child: const Icon(Icons.shield_outlined, color: Color(0xFFF59E0B), size: 24),
          ),
          const SizedBox(width: 12),
          const Expanded(
            child: Text(
              'Yêu Cầu Xác Thực eKYC',
              style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
            ),
          ),
        ],
      ),
      content: const Text(
        'Để bật trạng thái nhận việc và quét các ca làm gần bạn trên Job Radar, bạn cần hoàn tất xác thực danh tính (CCCD) theo quy định an toàn của CANi.',
        style: TextStyle(fontSize: 13.5, height: 1.4),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(ctx),
          child: const Text('Để sau', style: TextStyle(color: Color(0xFF64748B))),
        ),
        ElevatedButton(
          onPressed: () {
            Navigator.pop(ctx);
            Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const EkycVerificationScreen()),
            ).then((_) {
              ref.read(ekycProvider.notifier).checkStatus();
            });
          },
          style: ElevatedButton.styleFrom(
            backgroundColor: Theme.of(context).cani.brand,
            foregroundColor: Colors.white,
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
          ),
          child: const Text('Xác thực ngay', style: TextStyle(fontWeight: FontWeight.bold)),
        ),
      ],
    ),
  );
}

class JobRadarEkycWarningBanner extends ConsumerWidget {
  const JobRadarEkycWarningBanner({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final isAuthenticated = ref.watch(
      authStateProvider.select((s) => s.isAuthenticated),
    );
    final ekycSummary = ref.watch(
      ekycProvider.select(
        (s) => (
          isApproved: s.result?.isApproved ?? false,
          isPending: s.result?.isPending ?? false,
          isTechnicalFailure: s.result?.isTechnicalFailure ?? false,
          isRejected: s.result?.isRejected ?? false,
        ),
      ),
    );

    if (!isAuthenticated || ekycSummary.isApproved) {
      return const SizedBox.shrink();
    }

    final isDark = Theme.of(context).brightness == Brightness.dark;
    final cani = Theme.of(context).cani;
    final isPending = ekycSummary.isPending;
    final isTechnicalFailure = ekycSummary.isTechnicalFailure;
    final isRejected = ekycSummary.isRejected;

    final Color bannerBg = isPending
        ? (isDark ? const Color(0xFF1E3A8A).withValues(alpha: 0.3) : const Color(0xFFEFF6FF))
        : (isTechnicalFailure
            ? (isDark ? const Color(0xFF7C2D12).withValues(alpha: 0.3) : const Color(0xFFFFF7ED))
            : (isRejected
                ? (isDark ? const Color(0xFF7F1D1D).withValues(alpha: 0.3) : cani.dangerBg)
                : (isDark ? const Color(0xFF78350F).withValues(alpha: 0.3) : cani.warningBg)));

    final Color bannerBorder = isPending
        ? (isDark ? const Color(0xFF1D4ED8) : const Color(0xFFBFDBFE))
        : (isTechnicalFailure
            ? (isDark ? const Color(0xFFC2410C) : const Color(0xFFFED7AA))
            : (isRejected
                ? (isDark ? const Color(0xFFB91C1C) : const Color(0xFFFECACA))
                : (isDark ? const Color(0xFFB45309) : const Color(0xFFFDE68A))));

    final Color primaryColor = isPending
        ? cani.brand
        : (isTechnicalFailure
            ? const Color(0xFFEA580C)
            : (isRejected ? cani.danger : const Color(0xFFD97706)));

    final String message = isPending
        ? 'Hồ sơ eKYC đang được xét duyệt. Radar sẽ mở khóa ngay khi hoàn tất.'
        : (isTechnicalFailure
            ? 'Xác minh eKYC gặp lỗi kết nối tạm thời. Vui lòng mở để thử lại.'
            : (isRejected
                ? 'Hồ sơ eKYC trước đó bị từ chối. Vui lòng xác thực lại.'
                : 'Chưa xác thực CCCD: Hoàn tất eKYC để bật nhận việc & mở khóa Radar.'));

    final String ctaLabel = isPending
        ? 'Chi tiết'
        : (isTechnicalFailure ? 'Thử lại' : (isRejected ? 'Chụp lại' : 'Xác thực'));

    return Container(
      margin: const EdgeInsets.fromLTRB(12, 4, 12, 6),
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: bannerBg,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: bannerBorder),
      ),
      child: Row(
        children: [
          Icon(
            isPending
                ? Icons.hourglass_top_rounded
                : (isRejected ? Icons.error_outline : Icons.shield_outlined),
            size: 18,
            color: primaryColor,
          ),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              message,
              style: TextStyle(fontSize: 11.5, color: primaryColor, fontWeight: FontWeight.w600),
            ),
          ),
          const SizedBox(width: 8),
          InkWell(
            onTap: () {
              Navigator.push(
                context,
                MaterialPageRoute(builder: (_) => const EkycVerificationScreen()),
              ).then((_) {
                ref.read(ekycProvider.notifier).checkStatus();
              });
            },
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
              decoration: BoxDecoration(
                color: primaryColor,
                borderRadius: BorderRadius.circular(8),
              ),
              child: Text(
                ctaLabel,
                style: const TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: Colors.white),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class JobRadarEkycLockedState extends ConsumerWidget {
  const JobRadarEkycLockedState({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final ekycSummary = ref.watch(
      ekycProvider.select(
        (s) => (
          isPending: s.result?.isPending ?? false,
          isRejected: s.result?.isRejected ?? false,
          routingReason: s.result?.routingReason,
        ),
      ),
    );

    final isDark = Theme.of(context).brightness == Brightness.dark;
    final cani = Theme.of(context).cani;
    final isPending = ekycSummary.isPending;
    final isRejected = ekycSummary.isRejected;
    final routingReason = ekycSummary.routingReason;

    return Center(
      child: SingleChildScrollView(
        padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
        child: Container(
          padding: const EdgeInsets.all(24),
          decoration: BoxDecoration(
            color: isDark ? cani.surface : Colors.white,
            borderRadius: BorderRadius.circular(24),
            border: Border.all(color: cani.border),
            boxShadow: [
              BoxShadow(
                color: Colors.black.withValues(alpha: isDark ? 0.3 : 0.05),
                blurRadius: 16,
                offset: const Offset(0, 4),
              ),
            ],
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(
                width: 76,
                height: 76,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  gradient: LinearGradient(
                    colors: isPending
                        ? [const Color(0xFF3B82F6), const Color(0xFF1D4ED8)]
                        : (isRejected
                            ? [const Color(0xFFEF4444), const Color(0xFFB91C1C)]
                            : [const Color(0xFFF59E0B), const Color(0xFFD97706)]),
                  ),
                ),
                child: Center(
                  child: Icon(
                    isPending
                        ? Icons.hourglass_top_rounded
                        : (isRejected ? Icons.gpp_bad_rounded : Icons.lock_outline_rounded),
                    color: Colors.white,
                    size: 38,
                  ),
                ),
              ),
              const SizedBox(height: 18),
              Text(
                isPending
                    ? 'Hồ Sơ Đang Chờ Phê Duyệt'
                    : (isRejected ? 'Định Danh eKYC Bị Từ Chối' : 'Tính Năng Radar Tìm Việc Đang Khóa'),
                textAlign: TextAlign.center,
                style: TextStyle(
                  fontSize: 17,
                  fontWeight: FontWeight.w800,
                  color: cani.textMain,
                  letterSpacing: -0.3,
                ),
              ),
              const SizedBox(height: 10),
              Text(
                isPending
                    ? 'Hồ sơ CCCD của bạn đã được gửi thành công và đang được hệ thống đối soát tự động. Ngay khi được phê duyệt, tính năng Radar tìm việc sẽ tự động kích hoạt.'
                    : (isRejected
                        ? 'Hồ sơ xác thực CCCD trước đó không đạt yêu cầu: ${routingReason ?? "Hình ảnh mờ hoặc thông tin không trùng khớp"}. Vui lòng tiến hành chụp lại.'
                        : 'Theo quy chuẩn bảo vệ quyền lợi và an toàn ca làm việc tại CANi, tài khoản cần xác thực căn cước công dân (eKYC) trước khi có thể tìm và nhận việc gần bạn.'),
                textAlign: TextAlign.center,
                style: TextStyle(
                  fontSize: 13,
                  height: 1.5,
                  color: isDark ? const Color(0xFF94A3B8) : const Color(0xFF64748B),
                ),
              ),
              const SizedBox(height: 20),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                decoration: BoxDecoration(
                  color: isDark ? const Color(0xFF0F172A) : const Color(0xFFF8FAFC),
                  borderRadius: BorderRadius.circular(16),
                  border: Border.all(
                    color: isDark ? const Color(0xFF334155) : const Color(0xFFE2E8F0),
                  ),
                ),
                child: Column(
                  children: [
                    _buildBenefitRow(
                      context: context,
                      icon: Icons.radar_rounded,
                      title: 'Mở khóa bản đồ Job Radar & quét việc làm quanh bạn',
                      isDark: isDark,
                    ),
                    const SizedBox(height: 8),
                    _buildBenefitRow(
                      context: context,
                      icon: Icons.toggle_on_rounded,
                      title: 'Bật chế độ Sẵn sàng nhận việc để nhận ca tức thì',
                      isDark: isDark,
                    ),
                    const SizedBox(height: 8),
                    _buildBenefitRow(
                      context: context,
                      icon: Icons.payments_outlined,
                      title: 'Thanh toán lương minh bạch và bảo hiểm ca trực',
                      isDark: isDark,
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 24),
              SizedBox(
                width: double.infinity,
                height: 48,
                child: ElevatedButton.icon(
                  onPressed: () {
                    Navigator.push(
                      context,
                      MaterialPageRoute(builder: (_) => const EkycVerificationScreen()),
                    ).then((_) {
                      ref.read(ekycProvider.notifier).checkStatus();
                    });
                  },
                  icon: Icon(
                    isPending
                        ? Icons.visibility_outlined
                        : (isRejected ? Icons.refresh_rounded : Icons.verified_user_rounded),
                    size: 18,
                  ),
                  label: Text(
                    isPending
                        ? 'XEM CHI TIẾT TIẾN ĐỘ'
                        : (isRejected ? 'CHỤP LẠI CCCD & KHUÔN MẶT' : 'XÁC THỰC CCCD NGAY (1 - 2 PHÚT)'),
                    style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 13.5),
                  ),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: isRejected ? cani.danger : cani.brand,
                    foregroundColor: Colors.white,
                    elevation: 2,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(14),
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildBenefitRow({
    required BuildContext context,
    required IconData icon,
    required String title,
    required bool isDark,
  }) {
    final cani = Theme.of(context).cani;
    return Row(
      children: [
        Icon(icon, size: 16, color: cani.brand),
        const SizedBox(width: 10),
        Expanded(
          child: Text(
            title,
            style: TextStyle(
              fontSize: 12,
              fontWeight: FontWeight.w600,
              color: isDark ? const Color(0xFFCBD5E1) : const Color(0xFF334155),
            ),
          ),
        ),
      ],
    );
  }
}
