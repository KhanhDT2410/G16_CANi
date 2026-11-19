import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/app/cani_tokens.dart';
import 'package:cani/features/auth/providers/auth_provider.dart';
import 'package:cani/features/ekyc/providers/ekyc_provider.dart';
import 'package:cani/features/ekyc/screens/ekyc_verification_screen.dart';
import 'package:cani/features/job_proposals/data/proposal_repository.dart';
import 'package:cani/features/job_proposals/models/proposal_models.dart';
import 'package:cani/features/job_proposals/widgets/job_proposal_invite_card.dart';
import 'package:cani/features/job_radar/data/job_radar_repository.dart';
import 'package:cani/features/job_radar/models/job_radar_models.dart';
import 'package:cani/features/job_radar/providers/job_radar_provider.dart';
import 'package:cani/features/job_radar/widgets/job_detail_bottom_sheet.dart';
import 'package:cani/features/job_radar/widgets/job_radar_map_painter.dart';
import 'package:cani/features/job_radar/widgets/radar_sliding_job_card.dart';
import 'package:cani/features/skill_verification/providers/skill_verification_provider.dart';

class JobRadarScreen extends ConsumerStatefulWidget {
  const JobRadarScreen({super.key});

  @override
  ConsumerState<JobRadarScreen> createState() => _JobRadarScreenState();
}

class _JobRadarScreenState extends ConsumerState<JobRadarScreen> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      ref.read(ekycProvider.notifier).checkStatus();
    });
  }

  Future<void> _acceptProposalOnHome(JobProposalItem item) async {
    try {
      await ref.read(proposalRepositoryProvider).acceptProposal(item.proposalId);
      ref.invalidate(proposalsListProvider);
      ref.read(jobRadarProvider.notifier).scanNearbyShifts();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Nhận ca "${item.jobType}" thành công! Đã chuyển vào mục Việc của tôi.'),
            backgroundColor: Theme.of(context).cani.success,
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Không thể nhận ca: ${e.toString().replaceAll("Exception: ", "")}'),
            backgroundColor: Theme.of(context).cani.danger,
          ),
        );
      }
    }
  }

  Future<void> _rejectProposalOnHome(JobProposalItem item) async {
    try {
      await ref.read(proposalRepositoryProvider).rejectProposal(item.proposalId, 'Bận lịch cá nhân');
      ref.invalidate(proposalsListProvider);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Đã bỏ qua lời mời làm việc này.')),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Lỗi khi từ chối lời mời: ${e.toString().replaceAll("Exception: ", "")}'),
            backgroundColor: Theme.of(context).cani.danger,
          ),
        );
      }
    }
  }

  Future<void> _openInviteCodeDialogOnHome() async {
    final controller = TextEditingController();
    final theme = Theme.of(context);
    final cani = theme.cani;
    final result = await showDialog<String>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Tham gia ca bằng Mã Mời', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              'Nhập mã mời do Ban tổ chức sự kiện cung cấp (ví dụ: CANI-8XK2PW) để vào ca làm ngay:',
              style: TextStyle(fontSize: 13, color: cani.textBody),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: controller,
              textCapitalization: TextCapitalization.characters,
              decoration: const InputDecoration(
                hintText: 'CANI-XXXXXX',
                border: OutlineInputBorder(),
                prefixIcon: Icon(Icons.confirmation_number_outlined),
              ),
            ),
          ],
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Hủy')),
          FilledButton(
            onPressed: () {
              final code = controller.text.trim();
              if (code.isNotEmpty) Navigator.pop(ctx, code);
            },
            style: FilledButton.styleFrom(backgroundColor: cani.brand),
            child: const Text('Tham Gia'),
          ),
        ],
      ),
    );

    if (result != null && result.isNotEmpty && mounted) {
      try {
        await ref.read(proposalRepositoryProvider).joinViaInviteCode(result);
        ref.invalidate(proposalsListProvider);
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: const Text('Tham gia ca làm việc thành công! Đã thêm vào Việc của tôi.'),
              backgroundColor: Theme.of(context).cani.success,
            ),
          );
        }
      } catch (err) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text('Lỗi: ${err.toString().replaceAll("Exception: ", "")}'),
              backgroundColor: Theme.of(context).cani.danger,
            ),
          );
        }
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(jobRadarProvider);
    final notifier = ref.read(jobRadarProvider.notifier);
    final authState = ref.watch(authStateProvider);
    final skillState = ref.watch(skillVerificationProvider);
    final ekycState = ref.watch(ekycProvider);

    final isEkycApproved = ekycState.result?.isApproved ?? false;

    final user = authState.user;
    final userName = user?.fullName.isNotEmpty == true ? user!.fullName : 'Nhân Sự';
    final initialChar = userName.isNotEmpty ? userName[0].toUpperCase() : 'N';
    final totalSp = skillState.skillPoints.fold<int>(0, (sum, p) => sum + p.totalSp);
    final currentTier = skillState.skillPoints.isNotEmpty ? skillState.skillPoints.first.tier : 'VÀNG';

    final selectedOrFirstShift = state.selectedShift ?? (state.nearbyShifts.isNotEmpty ? state.nearbyShifts.first : null);

    final isDark = Theme.of(context).brightness == Brightness.dark;
    final cani = Theme.of(context).cani;

    return Scaffold(
      backgroundColor: Theme.of(context).scaffoldBackgroundColor,
      body: SafeArea(
        child: Column(
          children: [
            // 1. Header chuẩn OpenDesign
            Container(
              padding: const EdgeInsets.fromLTRB(18, 10, 18, 12),
              color: Theme.of(context).cardColor,
              child: Row(
                children: [
                  // Avatar Gradient với chấm xanh online
                  Stack(
                    children: [
                      Container(
                        width: 44,
                        height: 44,
                        decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          gradient: LinearGradient(colors: cani.brandGradient),
                          boxShadow: [
                            BoxShadow(
                              color: cani.brand.withValues(alpha: 0.3),
                              blurRadius: 10,
                              offset: const Offset(0, 3),
                            ),
                          ],
                        ),
                        child: Center(
                          child: Text(
                            initialChar,
                            style: const TextStyle(
                              color: Colors.white,
                              fontWeight: FontWeight.w800,
                              fontSize: 17,
                            ),
                          ),
                        ),
                      ),
                      Positioned(
                        right: 0,
                        bottom: 0,
                        child: Container(
                          width: 12,
                          height: 12,
                          decoration: BoxDecoration(
                            shape: BoxShape.circle,
                            color: (isEkycApproved && state.isOnline) ? cani.success : cani.textMuted,
                            border: Border.all(
                              color: isDark ? cani.surface : Colors.white,
                              width: 2.2,
                            ),
                          ),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(width: 11),

                  // Lời chào & Cấp bậc
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                         Text(
                           'Chào buổi sáng, $userName!',
                           style: TextStyle(
                             fontWeight: FontWeight.w800,
                             fontSize: 15,
                             color: cani.textMain,
                             letterSpacing: -0.2,
                           ),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                        const SizedBox(height: 2),
                        Row(
                          children: [
                             Icon(Icons.star, size: 12, color: cani.warning),
                            const SizedBox(width: 4),
                            Text(
                              'Hạng $currentTier · $totalSp SP',
                              style: TextStyle(
                                   fontSize: 11.5,
                                   fontWeight: FontWeight.w600,
                                   color: cani.textMuted,
                                 ),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),

                  // Nút Bật/Tắt Sẵn sàng nhận việc (Yêu cầu đã eKYC)
                  InkWell(
                    onTap: () {
                      if (!isEkycApproved) {
                        _showEkycRequiredDialog(context);
                        return;
                      }
                      notifier.toggleOnlineStatus();
                    },
                    borderRadius: BorderRadius.circular(999),
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                      decoration: BoxDecoration(
                        color: (isEkycApproved && state.isOnline)
                            ? cani.successBg
                            : (isDark ? cani.border : cani.skeleton),
                        borderRadius: BorderRadius.circular(999),
                        border: Border.all(
                          color: (isEkycApproved && state.isOnline)
                              ? cani.success
                              : (isDark ? cani.borderStrong : cani.border),
                        ),
                      ),
                      child: Row(
                        children: [
                          Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                !isEkycApproved
                                    ? 'Chưa eKYC'
                                    : (state.isOnline ? 'Đang nhận việc' : 'Tạm nghỉ'),
                                 style: TextStyle(
                                   fontSize: 10.5,
                                   fontWeight: FontWeight.w800,
                                   color: !isEkycApproved
                                       ? cani.warning
                                       : (state.isOnline ? cani.success : cani.textMuted),
                                 ),
                              ),
                              Text(
                                !isEkycApproved
                                    ? 'Chưa kích hoạt'
                                    : (state.isOnline ? 'GPS bật' : 'GPS tắt'),
                                 style: TextStyle(
                                   fontSize: 8.5,
                                   color: cani.textMuted,
                                   fontWeight: FontWeight.w600,
                                 ),
                              ),
                            ],
                          ),
                          const SizedBox(width: 8),
                          Container(
                            width: 32,
                            height: 18,
                             decoration: BoxDecoration(
                               borderRadius: BorderRadius.circular(99),
                               color: (isEkycApproved && state.isOnline) ? cani.success : cani.borderStrong,
                             ),
                            child: AnimatedAlign(
                              duration: const Duration(milliseconds: 200),
                              alignment: (isEkycApproved && state.isOnline)
                                  ? Alignment.centerRight
                                  : Alignment.centerLeft,
                              child: Container(
                                width: 14,
                                height: 14,
                                margin: const EdgeInsets.symmetric(horizontal: 2),
                                decoration: const BoxDecoration(
                                  shape: BoxShape.circle,
                                  color: Colors.white,
                                ),
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 10),

            // Banner cảnh báo eKYC nếu tài khoản chưa được phê duyệt eKYC
            if (!isEkycApproved)
              _buildEkycWarningBanner(context, isDark, ekycState),

            // 2. Section "Lời mời làm việc": toàn bộ lời mời đang chờ phản hồi
            if (isEkycApproved)
              _buildInviteSection(ref.watch(proposalsListProvider), cani),

            // 3. Thanh Bán Kính & Chuyển Đổi Bản Đồ / Danh Sách (Chỉ hiển thị khi đã hoàn tất eKYC)
            if (isEkycApproved)
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
                child: SingleChildScrollView(
                  scrollDirection: Axis.horizontal,
                  physics: const BouncingScrollPhysics(),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Text(
                        'Bán kính:',
                        style: TextStyle(
                          fontSize: 12,
                          fontWeight: FontWeight.w800,
                          color: cani.textMain,
                        ),
                      ),
                      const SizedBox(width: 8),
                      ...[5.0, 10.0, 15.0].map((r) {
                        final isSel = state.searchRadiusKm == r;
                        return Padding(
                          padding: const EdgeInsets.only(right: 6),
                          child: InkWell(
                            onTap: () => notifier.setRadius(r),
                            borderRadius: BorderRadius.circular(999),
                            child: Container(
                              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                              decoration: BoxDecoration(
                                color: isSel ? cani.brand : (isDark ? cani.surface : Colors.white),
                                borderRadius: BorderRadius.circular(999),
                                border: Border.all(
                                  color: isSel ? cani.brand : cani.border,
                                ),
                                boxShadow: isSel
                                    ? [BoxShadow(color: cani.brand.withValues(alpha: 0.25), blurRadius: 6, offset: const Offset(0, 2))]
                                    : null,
                              ),
                              child: Text(
                                '${r.toInt()} km',
                                style: TextStyle(
                                  fontSize: 11,
                                  fontWeight: FontWeight.w800,
                                  color: isSel ? Colors.white : cani.textMuted,
                                ),
                              ),
                            ),
                          ),
                        );
                      }),
                      const SizedBox(width: 4),
                      InkWell(
                        onTap: _openInviteCodeDialogOnHome,
                        borderRadius: BorderRadius.circular(10),
                        child: Container(
                          height: 30,
                          padding: const EdgeInsets.symmetric(horizontal: 10),
                          decoration: BoxDecoration(
                            color: isDark ? cani.surface : cani.brandSoft,
                            borderRadius: BorderRadius.circular(10),
                            border: Border.all(color: cani.border),
                          ),
                          child: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              Icon(Icons.confirmation_number_outlined, size: 14, color: isDark ? cani.accent : cani.brand),
                              const SizedBox(width: 4),
                              Text(
                                'Mã mời',
                                style: TextStyle(
                                  fontSize: 11.5,
                                  fontWeight: FontWeight.w800,
                                  color: isDark ? cani.accent : cani.brand,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                      const SizedBox(width: 8),
                      InkWell(
                        onTap: () => notifier.toggleRadarView(),
                        borderRadius: BorderRadius.circular(999),
                        child: Container(
                          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                          decoration: BoxDecoration(
                            color: isDark ? cani.surface : Colors.white,
                            borderRadius: BorderRadius.circular(999),
                            border: Border.all(
                              color: cani.border,
                            ),
                          ),
                          child: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              Icon(
                                state.isRadarView ? Icons.list_alt : Icons.map_outlined,
                                size: 14,
                                color: isDark ? cani.accent : cani.brand,
                              ),
                              const SizedBox(width: 4),
                              Text(
                                state.isRadarView ? 'Danh sách' : 'Bản đồ',
                                style: TextStyle(
                                  fontSize: 11,
                                  fontWeight: FontWeight.w700,
                                  color: isDark ? cani.accent : cani.brand,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            if (isEkycApproved) const SizedBox(height: 6),

            // 4. Nội Dung Chính: Khóa eKYC hoặc Bản Đồ Radar / Danh Sách
            Expanded(
              child: !isEkycApproved
                  ? _buildEkycLockedState(context, isDark, ekycState)
                  : (state.isLoading
                      ? const Center(child: CircularProgressIndicator())
                      : state.isRadarView
                          ? Stack(
                              children: [
                                // Bản đồ Radar Map
                                Positioned.fill(
                                  child: Container(
                                    margin: const EdgeInsets.fromLTRB(14, 0, 14, 6),
                                     decoration: BoxDecoration(
                                       borderRadius: BorderRadius.circular(24),
                                       boxShadow: [
                                         BoxShadow(
                                           color: Theme.of(context).cani.brand.withValues(alpha: 0.08),
                                           blurRadius: 18,
                                         ),
                                       ],
                                     ),
                                    clipBehavior: Clip.antiAlias,
                                    child: JobRadarMapWidget(
                                      shifts: state.nearbyShifts,
                                      selectedShift: state.selectedShift,
                                      maxRadiusKm: state.searchRadiusKm,
                                      isOnline: state.isOnline,
                                      userLat: state.userLat,
                                      userLng: state.userLng,
                                      onRecenterGps: () => notifier.refreshCurrentLocation(),
                                      onSelectShift: (shift) {
                                        notifier.selectShift(shift);
                                      },
                                    ),
                                  ),
                                ),

                                // Badge số ca tìm thấy ở góc trên bản đồ (Chỉ hiện khi Đang bật nhận việc)
                                if (state.isOnline)
                                  Positioned(
                                    top: 10,
                                    right: 24,
                                    child: Container(
                                      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                                       decoration: BoxDecoration(
                                         color: (isDark ? cani.surface : Colors.white).withValues(alpha: 0.94),
                                         borderRadius: BorderRadius.circular(999),
                                         boxShadow: [
                                           BoxShadow(
                                             color: isDark
                                                 ? Colors.black.withValues(alpha: 0.35)
                                                 : const Color(0xFF0B1B3D).withValues(alpha: 0.12),
                                             blurRadius: 8,
                                           ),
                                         ],
                                       ),
                                       child: Text(
                                         '${state.nearbyShifts.length} ca trong bán kính ${state.searchRadiusKm.toInt()} km',
                                         style: TextStyle(
                                           fontSize: 10.5,
                                           fontWeight: FontWeight.w800,
                                           color: isDark ? Colors.white : cani.textBody,
                                         ),
                                      ),
                                    ),
                                  ),

                                // Sliding Job Card ở dưới đáy bản đồ
                                if (selectedOrFirstShift != null)
                                  Positioned(
                                    left: 0,
                                    right: 0,
                                    bottom: 0,
                                    child: RadarSlidingJobCard(
                                      shift: selectedOrFirstShift,
                                      onViewDetails: () => _showShiftDetails(context, selectedOrFirstShift),
                                      onApply: () => _applyQuick(context, selectedOrFirstShift),
                                    ),
                                  ),
                              ],
                            )
                          : _buildListView(context, state, notifier)),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildInviteSection(AsyncValue<List<JobProposalItem>> proposals, CaniTokens cani) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    Widget emptyState = Container(
      margin: const EdgeInsets.fromLTRB(16, 0, 16, 8),
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      decoration: BoxDecoration(
        color: isDark ? cani.surface : Colors.white,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: cani.border),
      ),
      child: Row(
        children: [
          Icon(Icons.inbox_outlined, size: 18, color: isDark ? cani.accent : cani.brand),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              'Hiện không có lời mời làm việc nào. Bật "Sẵn sàng nhận việc" ở phía trên để nhận ca gần bạn.',
              style: TextStyle(
                fontSize: 11.5,
                color: isDark ? cani.textMuted : cani.textBody,
                fontWeight: FontWeight.w500,
              ),
            ),
          ),
        ],
      ),
    );

    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
      child: proposals.when(
        loading: () => emptyState,
        error: (e, _) => emptyState,
        data: (items) => items.isEmpty
            ? emptyState
            : Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    'Lời mời làm việc',
                    style: TextStyle(fontSize: 13, fontWeight: FontWeight.w800),
                  ),
                  const SizedBox(height: 8),
                  ListView.builder(
                    shrinkWrap: true,
                    physics: const NeverScrollableScrollPhysics(),
                    itemCount: items.length,
                    itemBuilder: (context, index) {
                      final item = items[index];
                      return Padding(
                        padding: const EdgeInsets.only(bottom: 10),
                        child: JobProposalInviteCard(
                          proposal: item,
                          onAccept: () => _acceptProposalOnHome(item),
                          onReject: () => _rejectProposalOnHome(item),
                        ),
                      );
                    },
                  ),
                ],
              ),
      ),
    );
  }

  Widget _buildListView(BuildContext context, JobRadarState state, JobRadarNotifier notifier) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final cani = Theme.of(context).cani;

    if (state.nearbyShifts.isEmpty) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.location_off_outlined, size: 48, color: Colors.grey.shade400),
            const SizedBox(height: 12),
            Text(
              'Không có ca làm việc nào trong khu vực này',
              style: TextStyle(
                fontWeight: FontWeight.w700,
                color: cani.textMain,
              ),
            ),
            const SizedBox(height: 8),
            TextButton(
              onPressed: () => notifier.setRadius(15.0),
              child: const Text('Mở rộng bán kính 15 km'),
            ),
          ],
        ),
      );
    }

    return ListView.builder(
      padding: const EdgeInsets.fromLTRB(16, 6, 16, 80),
      itemCount: state.nearbyShifts.length,
      itemBuilder: (context, index) {
        final shift = state.nearbyShifts[index];
        return Card(
          margin: const EdgeInsets.only(bottom: 12),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(16),
            side: isDark ? BorderSide(color: cani.border) : BorderSide.none,
          ),
          elevation: 1,
          child: InkWell(
            borderRadius: BorderRadius.circular(16),
            onTap: () {
              notifier.selectShift(shift);
              _showShiftDetails(context, shift);
            },
            child: Padding(
              padding: const EdgeInsets.all(14),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Expanded(
                         child: Text(
                           shift.eventName,
                           style: TextStyle(
                             fontWeight: FontWeight.w800,
                             fontSize: 14.5,
                             color: cani.textMain,
                           ),
                         ),
                      ),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                        decoration: BoxDecoration(
                          color: isDark ? const Color(0xFF064E3B).withValues(alpha: 0.4) : const Color(0xFFF0FDF4),
                          borderRadius: BorderRadius.circular(8),
                          border: Border.all(
                            color: isDark ? const Color(0xFF059669) : const Color(0xFFBBF7D0),
                          ),
                        ),
                        child: Text(
                          '${shift.distanceKm.toStringAsFixed(1)} km',
                          style: TextStyle(
                            fontSize: 11,
                            fontWeight: FontWeight.bold,
                            color: isDark ? const Color(0xFF34D399) : const Color(0xFF16A34A),
                          ),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 5),
                   Text(
                     shift.jobType,
                     style: TextStyle(fontWeight: FontWeight.w700, color: cani.brand, fontSize: 13),
                   ),
                   const SizedBox(height: 4),
                   Text(
                     shift.locationAddress,
                     style: TextStyle(
                       fontSize: 12,
                       color: cani.textMuted,
                     ),
                     maxLines: 1,
                     overflow: TextOverflow.ellipsis,
                   ),
                   Divider(height: 18, color: isDark ? cani.border : cani.border),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text(
                        '${shift.hourlyWage.toInt().toString().replaceAllMapped(RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'), (m) => '${m[1]}.')} đ/h',
                        style: TextStyle(fontWeight: FontWeight.w800, fontSize: 15, color: cani.success),
                      ),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                        decoration: BoxDecoration(
                          color: shift.emergency
                              ? cani.dangerBg
                              : (isDark ? cani.surface : const Color(0xFFF1F5F9)),
                          borderRadius: BorderRadius.circular(8),
                          border: Border.all(
                            color: shift.emergency
                                ? const Color(0xFFFCA5A5)
                                : (isDark ? cani.border : const Color(0xFFCBD5E1)),
                          ),
                        ),
                        child: Text(
                          shift.emergency ? '🚨 Khẩn cấp (Mở ứng tuyển)' : 'Chờ lời mời (Matching)',
                          style: TextStyle(
                            fontSize: 10.5,
                            fontWeight: FontWeight.w700,
                            color: shift.emergency ? cani.danger : cani.textMuted,
                          ),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
        );
      },
    );
  }

  void _showShiftDetails(BuildContext context, NearbyShiftItem shift) {
    final state = ref.read(jobRadarProvider);
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => JobDetailBottomSheet(
        shift: shift,
        userLat: state.userLat,
        userLng: state.userLng,
        onApply: () {
          Navigator.pop(ctx);
          _applyQuick(context, shift);
        },
      ),
    );
  }

  Future<void> _applyQuick(BuildContext ctx, NearbyShiftItem shift) async {
    if (!shift.emergency) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: const Text('Ca này chưa ở chế độ khẩn cấp — hãy chờ lời mời việc (Matching) từ Ban tổ chức!'),
            backgroundColor: Theme.of(context).cani.warning,
          ),
        );
      }
      return;
    }
    try {
      await ref.read(jobRadarRepositoryProvider).applyForShift(shift.shiftId);
      ref.read(jobRadarProvider.notifier).scanNearbyShifts();
      ref.invalidate(proposalsListProvider);
      ref.invalidate(assignmentsListProvider);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Ứng tuyển thành công ca: ${shift.jobType} tại ${shift.eventName}!'),
            backgroundColor: Theme.of(context).cani.success,
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Không thể ứng tuyển: ${e.toString().replaceAll("Exception: ", "")}'),
            backgroundColor: Theme.of(context).cani.danger,
          ),
        );
      }
    }
  }

  void _showEkycRequiredDialog(BuildContext context) {
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

  Widget _buildEkycWarningBanner(BuildContext context, bool isDark, EkycState ekycState) {
    final isPending = ekycState.result?.isPending ?? false;
    final isRejected = ekycState.result?.isRejected ?? false;
    final cani = Theme.of(context).cani;

    final Color bannerBg = isPending
        ? (isDark ? const Color(0xFF1E3A8A).withValues(alpha: 0.3) : const Color(0xFFEFF6FF))
        : (isRejected
            ? (isDark ? const Color(0xFF7F1D1D).withValues(alpha: 0.3) : cani.dangerBg)
            : (isDark ? const Color(0xFF78350F).withValues(alpha: 0.3) : cani.warningBg));

    final Color bannerBorder = isPending
        ? (isDark ? const Color(0xFF1D4ED8) : const Color(0xFFBFDBFE))
        : (isRejected
            ? (isDark ? const Color(0xFFB91C1C) : const Color(0xFFFECACA))
            : (isDark ? const Color(0xFFB45309) : const Color(0xFFFDE68A)));

    final Color primaryColor = isPending
        ? cani.brand
        : (isRejected ? cani.danger : const Color(0xFFD97706));

    final String message = isPending
        ? 'Hồ sơ eKYC đang được xét duyệt. Radar sẽ mở khóa ngay khi hoàn tất.'
        : (isRejected
            ? 'Hồ sơ eKYC trước đó bị từ chối. Vui lòng xác thực lại.'
            : 'Chưa xác thực CCCD: Hoàn tất eKYC để bật nhận việc & mở khóa Radar.');

    final String ctaLabel = isPending ? 'Chi tiết' : (isRejected ? 'Chụp lại' : 'Xác thực');

    return Container(
      margin: const EdgeInsets.fromLTRB(16, 2, 16, 8),
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: bannerBg,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: bannerBorder),
      ),
      child: Row(
        children: [
          Icon(
            isPending ? Icons.hourglass_top_rounded : (isRejected ? Icons.error_outline : Icons.shield_outlined),
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

  Widget _buildEkycLockedState(BuildContext context, bool isDark, EkycState ekycState) {
    final isPending = ekycState.result?.isPending ?? false;
    final isRejected = ekycState.result?.isRejected ?? false;
    final cani = Theme.of(context).cani;

    return Center(
      child: SingleChildScrollView(
        padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
        child: Container(
          padding: const EdgeInsets.all(24),
          decoration: BoxDecoration(
            color: isDark ? cani.surface : Colors.white,
            borderRadius: BorderRadius.circular(24),
            border: Border.all(
              color: isDark ? cani.border : cani.border,
            ),
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
              // Icon khiên khóa nổi bật
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
                  boxShadow: [
                    BoxShadow(
                      color: (isPending
                              ? const Color(0xFF3B82F6)
                              : (isRejected ? const Color(0xFFEF4444) : const Color(0xFFF59E0B)))
                          .withValues(alpha: 0.35),
                      blurRadius: 18,
                      offset: const Offset(0, 6),
                    ),
                  ],
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

              // Tiêu đề trạng thái
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

              // Nội dung mô tả
              Text(
                isPending
                    ? 'Hồ sơ CCCD của bạn đã được gửi thành công và đang được hệ thống đối soát tự động. Ngay khi được phê duyệt, tính năng Radar tìm việc sẽ tự động kích hoạt.'
                    : (isRejected
                        ? 'Hồ sơ xác thực CCCD trước đó không đạt yêu cầu: ${ekycState.result?.routingReason ?? "Hình ảnh mờ hoặc thông tin không trùng khớp"}. Vui lòng tiến hành chụp lại.'
                        : 'Theo quy chuẩn bảo vệ quyền lợi và an toàn ca làm việc tại CANi, tài khoản cần xác thực căn cước công dân (eKYC) trước khi có thể tìm và nhận việc gần bạn.'),
                textAlign: TextAlign.center,
                style: TextStyle(
                  fontSize: 13,
                  height: 1.5,
                  color: isDark ? const Color(0xFF94A3B8) : const Color(0xFF64748B),
                ),
              ),
              const SizedBox(height: 20),

              // Danh sách quyền lợi sau khi eKYC
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
                      icon: Icons.radar_rounded,
                      title: 'Mở khóa bản đồ Job Radar & quét việc làm quanh bạn',
                      isDark: isDark,
                    ),
                    const SizedBox(height: 8),
                    _buildBenefitRow(
                      icon: Icons.toggle_on_rounded,
                      title: 'Bật chế độ Sẵn sàng nhận việc để nhận ca tức thì',
                      isDark: isDark,
                    ),
                    const SizedBox(height: 8),
                    _buildBenefitRow(
                      icon: Icons.payments_outlined,
                      title: 'Thanh toán lương minh bạch và bảo hiểm ca trực',
                      isDark: isDark,
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 24),

              // Nút CTA hành động
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
                     backgroundColor: isPending
                         ? cani.brand
                         : (isRejected ? cani.danger : cani.brand),
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

  Widget _buildBenefitRow({required IconData icon, required String title, required bool isDark}) {
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
