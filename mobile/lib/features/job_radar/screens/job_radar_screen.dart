import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/app/cani_tokens.dart';
import 'package:cani/app/home_screen.dart';
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

class JobRadarScreen extends ConsumerStatefulWidget {
  const JobRadarScreen({super.key});

  @override
  ConsumerState<JobRadarScreen> createState() => _JobRadarScreenState();
}

class _JobRadarScreenState extends ConsumerState<JobRadarScreen> {
  Timer? _expiryTimer;
  DateTime? _scheduledExpiryAt;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) {
        ref.read(ekycProvider.notifier).checkStatus();
      }
    });
  }

  @override
  void dispose() {
    _expiryTimer?.cancel();
    super.dispose();
  }

  void _syncExpiryTimer(List<JobProposalItem> activeProposals) {
    DateTime? earliest;
    for (final p in activeProposals) {
      final exp = p.expiresAt;
      if (exp != null && (earliest == null || exp.isBefore(earliest))) {
        earliest = exp;
      }
    }

    if (earliest == null) {
      _expiryTimer?.cancel();
      _expiryTimer = null;
      _scheduledExpiryAt = null;
      return;
    }

    if (_scheduledExpiryAt == earliest && (_expiryTimer?.isActive ?? false)) {
      return;
    }

    _expiryTimer?.cancel();
    _scheduledExpiryAt = earliest;
    final delay = earliest.difference(DateTime.now()) + const Duration(milliseconds: 300);
    _expiryTimer = Timer(delay.isNegative ? Duration.zero : delay, () {
      if (!mounted) return;
      _scheduledExpiryAt = null;
      setState(() {});
      ref.invalidate(proposalsListProvider);
    });
  }

  void _retryAll(JobRadarNotifier notifier) {
    ref.invalidate(proposalsListProvider);
    notifier.scanNearbyShifts();
  }

  Future<void> _acceptProposalOnHome(JobProposalItem item) async {
    try {
      await ref.read(proposalRepositoryProvider).acceptProposal(item.proposalId);
      if (!mounted) return;
      final currentSelected = ref.read(jobRadarProvider).selectedShift;
      if (currentSelected?.shiftId == item.shiftId) {
        ref.read(jobRadarProvider.notifier).clearSelectedShift();
      }
      ref.invalidate(proposalsListProvider);
      ref.invalidate(assignmentsListProvider);
      ref.invalidate(incomeSummaryProvider);
      ref.read(assignmentsSubTabProvider.notifier).state = 0;
      ref.read(jobRadarProvider.notifier).scanNearbyShifts();
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Nhận ca "${item.jobType}" thành công! Đã chuyển vào mục Ca làm.'),
          backgroundColor: Theme.of(context).cani.success,
          action: SnackBarAction(
            label: 'Xem ca',
            textColor: Colors.white,
            onPressed: () {
              ref.read(homeTabIndexProvider.notifier).state = 1;
              ref.read(assignmentsSubTabProvider.notifier).state = 0;
            },
          ),
        ),
      );
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
      if (!mounted) return;
      final currentSelected = ref.read(jobRadarProvider).selectedShift;
      if (currentSelected?.shiftId == item.shiftId) {
        ref.read(jobRadarProvider.notifier).clearSelectedShift();
      }
      ref.invalidate(proposalsListProvider);
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Đã bỏ qua lời mời làm việc này.')),
      );
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
    final cani = Theme.of(context).cani;
    final result = await showDialog<String>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Tham gia ca bằng Mã Mời', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
        content: SingleChildScrollView(
          child: Column(
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
    controller.dispose();

    if (result != null && result.isNotEmpty && mounted) {
      try {
        await ref.read(proposalRepositoryProvider).joinViaInviteCode(result);
        if (!mounted) return;
        ref.invalidate(proposalsListProvider);
        ref.invalidate(assignmentsListProvider);
        ref.invalidate(incomeSummaryProvider);
        ref.read(assignmentsSubTabProvider.notifier).state = 0;
        ref.read(jobRadarProvider.notifier).scanNearbyShifts();
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: const Text('Tham gia ca làm việc thành công! Đã thêm vào Ca làm.'),
            backgroundColor: Theme.of(context).cani.success,
            action: SnackBarAction(
              label: 'Xem ca',
              textColor: Colors.white,
              onPressed: () {
                ref.read(homeTabIndexProvider.notifier).state = 1;
                ref.read(assignmentsSubTabProvider.notifier).state = 0;
              },
            ),
          ),
        );
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

  void _openAllInvitesSheet(BuildContext context) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (sheetCtx) => Consumer(
        builder: (context, sheetRef, _) {
          final proposalsAsync = sheetRef.watch(proposalsListProvider);
          final now = DateTime.now();
          final active = (proposalsAsync.asData?.value ?? const <JobProposalItem>[])
              .where((p) => p.isActiveAt(now))
              .toList();
          final cani = Theme.of(context).cani;

          return SafeArea(
            top: false,
            child: Container(
              constraints: BoxConstraints(
                maxHeight: MediaQuery.of(context).size.height * 0.78,
              ),
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 16),
              decoration: BoxDecoration(
                color: Theme.of(context).scaffoldBackgroundColor,
                borderRadius: const BorderRadius.vertical(top: Radius.circular(24)),
              ),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Container(
                    width: 40,
                    height: 4,
                    decoration: BoxDecoration(
                      color: cani.borderStrong,
                      borderRadius: BorderRadius.circular(99),
                    ),
                  ),
                  const SizedBox(height: 12),
                  Row(
                    children: [
                      Expanded(
                        child: Text(
                          'Lời mời làm việc (${active.length})',
                          style: TextStyle(
                            fontSize: 16,
                            fontWeight: FontWeight.w800,
                            color: cani.textMain,
                          ),
                        ),
                      ),
                      IconButton(
                        onPressed: () => Navigator.pop(sheetCtx),
                        icon: Icon(Icons.close_rounded, color: cani.textMuted),
                      ),
                    ],
                  ),
                  const SizedBox(height: 6),
                  if (active.isEmpty)
                    Padding(
                      padding: const EdgeInsets.symmetric(vertical: 28),
                      child: Text(
                        'Không còn lời mời nào đang chờ phản hồi.',
                        style: TextStyle(color: cani.textMuted, fontWeight: FontWeight.w600),
                      ),
                    )
                  else
                    Flexible(
                      child: ListView.builder(
                        shrinkWrap: true,
                        itemCount: active.length,
                        itemBuilder: (_, idx) {
                          final item = active[idx];
                          return JobProposalInviteCard(
                            proposal: item,
                            onAccept: () async {
                              Navigator.pop(sheetCtx);
                              await _acceptProposalOnHome(item);
                            },
                            onReject: () async {
                              await _rejectProposalOnHome(item);
                              if (active.length <= 1 && sheetCtx.mounted) {
                                Navigator.pop(sheetCtx);
                              }
                            },
                          );
                        },
                      ),
                    ),
                ],
              ),
            ),
          );
        },
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    ref.listen<String?>(
      jobRadarProvider.select((s) => s.errorMessage),
      (previous, next) {
        if (next != null && next.isNotEmpty && next != previous && mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(next),
              backgroundColor: Theme.of(context).cani.danger,
              behavior: SnackBarBehavior.floating,
            ),
          );
        }
      },
    );

    final state = ref.watch(jobRadarProvider);
    final notifier = ref.read(jobRadarProvider.notifier);
    final authState = ref.watch(authStateProvider);
    final ekycState = ref.watch(ekycProvider);
    final proposalsAsync = ref.watch(proposalsListProvider);

    final isEkycApproved = ekycState.result?.isApproved ?? false;

    final user = authState.user;
    final userName = user?.fullName.trim().isNotEmpty == true ? user!.fullName.trim() : 'Nhân Sự';
    final initialChar = userName.isNotEmpty ? userName[0].toUpperCase() : 'N';

    final now = DateTime.now();
    final activeProposals = (proposalsAsync.asData?.value ?? const <JobProposalItem>[])
        .where((p) => p.isActiveAt(now))
        .toList();

    if (isEkycApproved) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) _syncExpiryTimer(activeProposals);
      });
    }

    final isDark = Theme.of(context).brightness == Brightness.dark;
    final cani = Theme.of(context).cani;

    final Widget? bottomCard = _buildUnifiedBottomCard(
      context: context,
      state: state,
      notifier: notifier,
      activeProposals: activeProposals,
    );

    final Widget? topOverlay = _buildMapTopOverlay(
      state: state,
      proposalsAsync: proposalsAsync,
      activeProposals: activeProposals,
      notifier: notifier,
      cani: cani,
    );

    return Scaffold(
      backgroundColor: Theme.of(context).scaffoldBackgroundColor,
      body: SafeArea(
        bottom: false,
        child: Column(
          children: [
            _buildCompactHeader(
              context: context,
              userName: userName,
              initialChar: initialChar,
              isEkycApproved: isEkycApproved,
              state: state,
              notifier: notifier,
              cani: cani,
              isDark: isDark,
            ),

            if (!isEkycApproved) _buildEkycWarningBanner(context, isDark, ekycState),

            if (isEkycApproved) _buildFilterBar(state, notifier, cani),

            Expanded(
              child: !isEkycApproved
                  ? _buildEkycLockedState(context, isDark, ekycState)
                  : state.isRadarView
                      ? Container(
                          margin: const EdgeInsets.fromLTRB(12, 0, 12, 8),
                          decoration: BoxDecoration(
                            borderRadius: BorderRadius.circular(20),
                            border: Border.all(color: cani.border),
                            boxShadow: [
                              BoxShadow(
                                color: cani.brand.withValues(alpha: 0.06),
                                blurRadius: 14,
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
                            onSelectShift: (shift) => notifier.selectShift(shift),
                            topOverlay: topOverlay,
                            bottomOverlay: bottomCard,
                          ),
                        )
                      : _buildListView(
                          context: context,
                          state: state,
                          notifier: notifier,
                          proposalsAsync: proposalsAsync,
                          activeProposals: activeProposals,
                        ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildCompactHeader({
    required BuildContext context,
    required String userName,
    required String initialChar,
    required bool isEkycApproved,
    required JobRadarState state,
    required JobRadarNotifier notifier,
    required CaniTokens cani,
    required bool isDark,
  }) {
    final isReceiving = isEkycApproved && state.isOnline;

    return Container(
      padding: const EdgeInsets.fromLTRB(14, 8, 14, 8),
      color: Theme.of(context).cardColor,
      child: Row(
        children: [
          Stack(
            children: [
              Container(
                width: 40,
                height: 40,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  gradient: LinearGradient(colors: cani.brandGradient),
                ),
                child: Center(
                  child: Text(
                    initialChar,
                    style: const TextStyle(
                      color: Colors.white,
                      fontWeight: FontWeight.w800,
                      fontSize: 16,
                    ),
                  ),
                ),
              ),
              Positioned(
                right: 0,
                bottom: 0,
                child: Container(
                  width: 11,
                  height: 11,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    color: isReceiving ? cani.success : cani.textMuted,
                    border: Border.all(
                      color: isDark ? cani.surface : Colors.white,
                      width: 2,
                    ),
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(width: 10),

          Expanded(
            child: Text(
              userName,
              style: TextStyle(
                fontWeight: FontWeight.w800,
                fontSize: 16,
                color: cani.textMain,
                letterSpacing: -0.2,
              ),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
          ),
          const SizedBox(width: 8),

          Semantics(
            button: true,
            toggled: isReceiving,
            label: 'Nhận việc',
            child: Material(
              color: Colors.transparent,
              child: InkWell(
                onTap: () {
                  if (!isEkycApproved) {
                    _showEkycRequiredDialog(context);
                    return;
                  }
                  notifier.toggleOnlineStatus();
                },
                borderRadius: BorderRadius.circular(999),
                child: ConstrainedBox(
                  constraints: const BoxConstraints(minHeight: 44),
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                    decoration: BoxDecoration(
                      color: isReceiving
                          ? cani.successBg
                          : (isDark ? cani.canvas : cani.skeleton.withValues(alpha: 0.65)),
                      borderRadius: BorderRadius.circular(999),
                      border: Border.all(
                        color: isReceiving ? cani.success : cani.borderStrong,
                      ),
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Column(
                          mainAxisSize: MainAxisSize.min,
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              'Nhận việc',
                              style: TextStyle(
                                fontSize: 11,
                                fontWeight: FontWeight.w800,
                                color: !isEkycApproved
                                    ? cani.warning
                                    : (isReceiving ? cani.success : cani.textMain),
                              ),
                            ),
                            Text(
                              !isEkycApproved
                                  ? 'Chưa eKYC'
                                  : (isReceiving ? 'Đang bật' : 'Tạm tắt'),
                              style: TextStyle(
                                fontSize: 9.5,
                                fontWeight: FontWeight.w600,
                                color: cani.textMuted,
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(width: 7),
                        Container(
                          width: 32,
                          height: 18,
                          decoration: BoxDecoration(
                            borderRadius: BorderRadius.circular(99),
                            color: isReceiving ? cani.success : cani.borderStrong,
                          ),
                          child: AnimatedAlign(
                            duration: const Duration(milliseconds: 180),
                            alignment: isReceiving ? Alignment.centerRight : Alignment.centerLeft,
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
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildFilterBar(JobRadarState state, JobRadarNotifier notifier, CaniTokens cani) {
    final areaLabel = state.hasPreferredLocation
        ? (state.usingPreferenceLocation
            ? (state.preferredAddress?.trim().isNotEmpty == true
                ? state.preferredAddress!.trim()
                : 'Khu vực ưu tiên')
            : 'Vị trí GPS')
        : (state.isRealLocation ? 'Vị trí GPS' : 'Khu vực');

    return Padding(
      padding: const EdgeInsets.fromLTRB(12, 4, 12, 6),
      child: Row(
        children: [
          // 1. Khu vực
          Expanded(
            flex: 4,
            child: InkWell(
              onTap: () {
                if (state.hasPreferredLocation) {
                  notifier.switchCenterMode(usePreference: !state.usingPreferenceLocation);
                } else {
                  notifier.refreshCurrentLocation();
                }
              },
              borderRadius: BorderRadius.circular(10),
              child: Container(
                height: 36,
                padding: const EdgeInsets.symmetric(horizontal: 7),
                decoration: BoxDecoration(
                  color: (state.hasPreferredLocation && state.usingPreferenceLocation)
                      ? cani.brandSoft
                      : cani.surface,
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(
                    color: (state.hasPreferredLocation && state.usingPreferenceLocation)
                        ? cani.brand.withValues(alpha: 0.35)
                        : cani.border,
                  ),
                ),
                child: Row(
                  children: [
                    Icon(
                      state.usingPreferenceLocation ? Icons.tune_rounded : Icons.my_location_rounded,
                      size: 14,
                      color: cani.brand,
                    ),
                    const SizedBox(width: 4),
                    Expanded(
                      child: Text(
                        areaLabel,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(
                          fontSize: 11.5,
                          fontWeight: FontWeight.w700,
                          color: cani.brand,
                        ),
                      ),
                    ),
                    if (state.hasPreferredLocation) ...[
                      const SizedBox(width: 2),
                      Icon(Icons.swap_horiz_rounded, size: 13, color: cani.brand),
                    ],
                  ],
                ),
              ),
            ),
          ),
          const SizedBox(width: 5),

          // 2. Bán kính
          Flexible(
            flex: 3,
            child: PopupMenuButton<double>(
              initialValue: state.searchRadiusKm,
              onSelected: (r) => notifier.setRadius(r),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
              itemBuilder: (_) => [5.0, 10.0, 15.0, 20.0, 25.0, 30.0]
                  .map(
                    (r) => PopupMenuItem<double>(
                      value: r,
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Text('Bán kính ${r.toInt()} km',
                              style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 13)),
                          if (state.searchRadiusKm == r)
                            Icon(Icons.check_rounded, size: 16, color: cani.brand),
                        ],
                      ),
                    ),
                  )
                  .toList(),
              child: Container(
                height: 36,
                padding: const EdgeInsets.symmetric(horizontal: 7),
                decoration: BoxDecoration(
                  color: cani.surface,
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(color: cani.border),
                ),
                child: FittedBox(
                  fit: BoxFit.scaleDown,
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(Icons.radar_rounded, size: 13.5, color: cani.brand),
                      const SizedBox(width: 3),
                      Text(
                        '${state.searchRadiusKm.toInt()} km',
                        maxLines: 1,
                        softWrap: false,
                        style: TextStyle(fontSize: 11, fontWeight: FontWeight.w700, color: cani.textMain),
                      ),
                      Icon(Icons.keyboard_arrow_down_rounded, size: 15, color: cani.textMuted),
                    ],
                  ),
                ),
              ),
            ),
          ),
          const SizedBox(width: 5),

          // 3. Mã mời
          Flexible(
            flex: 3,
            child: InkWell(
              onTap: _openInviteCodeDialogOnHome,
              borderRadius: BorderRadius.circular(10),
              child: Container(
                height: 36,
                padding: const EdgeInsets.symmetric(horizontal: 7),
                decoration: BoxDecoration(
                  color: cani.brandSoft,
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(color: cani.brand.withValues(alpha: 0.2)),
                ),
                child: FittedBox(
                  fit: BoxFit.scaleDown,
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(Icons.confirmation_number_outlined, size: 13.5, color: cani.brand),
                      const SizedBox(width: 3),
                      Text(
                        'Mã mời',
                        maxLines: 1,
                        softWrap: false,
                        style: TextStyle(fontSize: 11, fontWeight: FontWeight.w700, color: cani.brand),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
          const SizedBox(width: 5),

          // 4. Danh sách / Bản đồ
          Flexible(
            flex: 3,
            child: InkWell(
              onTap: () => notifier.toggleRadarView(),
              borderRadius: BorderRadius.circular(10),
              child: Container(
                height: 36,
                padding: const EdgeInsets.symmetric(horizontal: 7),
                decoration: BoxDecoration(
                  color: cani.surface,
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(color: cani.border),
                ),
                child: FittedBox(
                  fit: BoxFit.scaleDown,
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(
                        state.isRadarView ? Icons.view_list_rounded : Icons.map_outlined,
                        size: 15,
                        color: cani.textMain,
                      ),
                      const SizedBox(width: 3),
                      Text(
                        state.isRadarView ? 'DS (${state.nearbyShifts.length})' : 'Bản đồ',
                        maxLines: 1,
                        softWrap: false,
                        style: TextStyle(fontSize: 11, fontWeight: FontWeight.w700, color: cani.textMain),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget? _buildUnifiedBottomCard({
    required BuildContext context,
    required JobRadarState state,
    required JobRadarNotifier notifier,
    required List<JobProposalItem> activeProposals,
  }) {
    // 1. Nếu Worker chủ động chạm một marker hợp lệ trên bản đồ -> cập nhật thẻ theo ca đó
    if (state.selectedShift != null) {
      final selected = state.selectedShift!;
      return RadarSlidingJobCard(
        shift: selected,
        activeInviteCount: activeProposals.length,
        onViewInvites: activeProposals.isNotEmpty ? () => notifier.clearSelectedShift() : null,
        onViewDetails: () => _showShiftDetails(context, selected),
        onApply: () => _applyQuick(context, selected),
      );
    }

    // 2. Ưu tiên lời mời đang chờ phản hồi và còn hiệu lực
    if (activeProposals.isNotEmpty) {
      final topInvite = activeProposals.first;
      return JobProposalInviteCard(
        compact: true,
        proposal: topInvite,
        totalPendingCount: activeProposals.length,
        onViewAll: activeProposals.length > 1 ? () => _openAllInvitesSheet(context) : null,
        onAccept: () => _acceptProposalOnHome(topInvite),
        onReject: () => _rejectProposalOnHome(topInvite),
      );
    }

    // 3. Không có lời mời hiệu lực -> hiển thị thẻ "Ca gần bạn" nếu có ca quanh bạn
    if (state.nearbyShifts.isNotEmpty) {
      final firstShift = state.nearbyShifts.first;
      return RadarSlidingJobCard(
        shift: firstShift,
        onViewDetails: () => _showShiftDetails(context, firstShift),
        onApply: () => _applyQuick(context, firstShift),
      );
    }

    return null;
  }

  Widget? _buildMapTopOverlay({
    required JobRadarState state,
    required AsyncValue<List<JobProposalItem>> proposalsAsync,
    required List<JobProposalItem> activeProposals,
    required JobRadarNotifier notifier,
    required CaniTokens cani,
  }) {
    final isLoadingAny = state.isLoading || proposalsAsync.isLoading;
    final hasAnyError = proposalsAsync.hasError || state.radarLoadError != null;
    final hasAnyVisibleData = activeProposals.isNotEmpty || state.nearbyShifts.isNotEmpty;

    if (hasAnyError && !isLoadingAny) {
      return Center(
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 7),
          decoration: BoxDecoration(
            color: cani.surface,
            borderRadius: BorderRadius.circular(999),
            border: Border.all(color: cani.danger.withValues(alpha: 0.45)),
            boxShadow: [
              BoxShadow(
                color: Colors.black.withValues(alpha: 0.08),
                blurRadius: 8,
                offset: const Offset(0, 2),
              ),
            ],
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(Icons.cloud_off_rounded, size: 15, color: cani.danger),
              const SizedBox(width: 6),
              Flexible(
                child: Text(
                  hasAnyVisibleData ? 'Tải cập nhật thất bại' : 'Không thể tải dữ liệu Radar',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(fontSize: 11.5, fontWeight: FontWeight.w700, color: cani.danger),
                ),
              ),
              const SizedBox(width: 8),
              InkWell(
                onTap: () => _retryAll(notifier),
                borderRadius: BorderRadius.circular(999),
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                  child: Text(
                    'Thử lại',
                    style: TextStyle(
                      fontSize: 11.5,
                      fontWeight: FontWeight.w800,
                      color: cani.brand,
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
      );
    }

    if (isLoadingAny) {
      return Center(
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
          decoration: BoxDecoration(
            color: cani.surface.withValues(alpha: 0.95),
            borderRadius: BorderRadius.circular(999),
            border: Border.all(color: cani.border),
            boxShadow: [
              BoxShadow(
                color: Colors.black.withValues(alpha: 0.06),
                blurRadius: 6,
                offset: const Offset(0, 2),
              ),
            ],
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              SizedBox(
                width: 12,
                height: 12,
                child: CircularProgressIndicator(strokeWidth: 2, color: cani.brand),
              ),
              const SizedBox(width: 7),
              Flexible(
                child: Text(
                  'Đang tải ca quanh bạn...',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(fontSize: 11.5, fontWeight: FontWeight.w700, color: cani.textBody),
                ),
              ),
            ],
          ),
        ),
      );
    }

    if (state.hasLoadedShifts && !hasAnyVisibleData) {
      return Center(
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
          decoration: BoxDecoration(
            color: cani.surface.withValues(alpha: 0.94),
            borderRadius: BorderRadius.circular(999),
            border: Border.all(color: cani.border),
            boxShadow: [
              BoxShadow(
                color: Colors.black.withValues(alpha: 0.06),
                blurRadius: 6,
                offset: const Offset(0, 2),
              ),
            ],
          ),
          child: Text(
            'Đang tìm ca quanh bạn',
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: TextStyle(
              fontSize: 11.5,
              fontWeight: FontWeight.w700,
              color: cani.textBody,
            ),
          ),
        ),
      );
    }

    return null;
  }

  Widget _buildListView({
    required BuildContext context,
    required JobRadarState state,
    required JobRadarNotifier notifier,
    required AsyncValue<List<JobProposalItem>> proposalsAsync,
    required List<JobProposalItem> activeProposals,
  }) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final cani = Theme.of(context).cani;
    final isLoadingAny = state.isLoading || proposalsAsync.isLoading;
    final hasAnyError = proposalsAsync.hasError || state.radarLoadError != null;
    final hasAnyVisibleData = activeProposals.isNotEmpty || state.nearbyShifts.isNotEmpty;

    if (hasAnyError && !hasAnyVisibleData && !isLoadingAny) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(Icons.cloud_off_rounded, size: 42, color: cani.danger),
              const SizedBox(height: 10),
              Text(
                'Tải dữ liệu thất bại',
                style: TextStyle(fontWeight: FontWeight.w800, color: cani.textMain),
              ),
              const SizedBox(height: 8),
              FilledButton(
                onPressed: () => _retryAll(notifier),
                style: FilledButton.styleFrom(backgroundColor: cani.brand),
                child: const Text('Thử lại'),
              ),
            ],
          ),
        ),
      );
    }

    if (!isLoadingAny && state.hasLoadedShifts && !hasAnyVisibleData) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(Icons.radar_rounded, size: 44, color: cani.brand.withValues(alpha: 0.7)),
              const SizedBox(height: 10),
              Text(
                'Đang tìm ca quanh bạn',
                style: TextStyle(fontWeight: FontWeight.w700, color: cani.textMain),
              ),
              const SizedBox(height: 8),
              TextButton(
                onPressed: () => notifier.setRadius(15.0),
                child: const Text('Mở rộng bán kính 15 km'),
              ),
            ],
          ),
        ),
      );
    }

    return RefreshIndicator(
      onRefresh: () async {
        ref.invalidate(proposalsListProvider);
        await notifier.scanNearbyShifts();
      },
      child: ListView(
        padding: const EdgeInsets.fromLTRB(16, 6, 16, 80),
        children: [
          if (hasAnyError)
            Container(
              margin: const EdgeInsets.only(bottom: 10),
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              decoration: BoxDecoration(
                color: cani.dangerBg,
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: cani.danger.withValues(alpha: 0.35)),
              ),
              child: Row(
                children: [
                  Expanded(
                    child: Text(
                      'Không thể làm mới toàn bộ dữ liệu.',
                      style: TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: cani.danger),
                    ),
                  ),
                  TextButton(
                    onPressed: () => _retryAll(notifier),
                    child: const Text('Thử lại'),
                  ),
                ],
              ),
            ),

          if (activeProposals.isNotEmpty) ...[
            Text(
              'Lời mời làm việc (${activeProposals.length})',
              style: TextStyle(fontSize: 13.5, fontWeight: FontWeight.w800, color: cani.textMain),
            ),
            const SizedBox(height: 8),
            ...activeProposals.map(
              (item) => JobProposalInviteCard(
                proposal: item,
                onAccept: () => _acceptProposalOnHome(item),
                onReject: () => _rejectProposalOnHome(item),
              ),
            ),
            const SizedBox(height: 6),
          ],

          if (state.nearbyShifts.isNotEmpty) ...[
            Text(
              'Ca quanh bạn (${state.nearbyShifts.length})',
              style: TextStyle(fontSize: 13.5, fontWeight: FontWeight.w800, color: cani.textMain),
            ),
            const SizedBox(height: 8),
            ...state.nearbyShifts.map(
              (shift) => _buildNearbyShiftListCard(context, shift, notifier, cani, isDark),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildNearbyShiftListCard(
    BuildContext context,
    NearbyShiftItem shift,
    JobRadarNotifier notifier,
    CaniTokens cani,
    bool isDark,
  ) {
    final wageFormatted = shift.hourlyWage
        .toInt()
        .toString()
        .replaceAllMapped(RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'), (m) => '${m[1]}.');

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
                      style: TextStyle(fontWeight: FontWeight.w800, fontSize: 14.5, color: cani.textMain),
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
                style: TextStyle(fontSize: 12, color: cani.textMuted),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
              Divider(height: 18, color: cani.border),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Flexible(
                    child: Text(
                      '$wageFormatted đ/h',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(fontWeight: FontWeight.w800, fontSize: 15, color: cani.success),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Flexible(
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                      decoration: BoxDecoration(
                        color: shift.emergency ? cani.dangerBg : (isDark ? cani.surface : const Color(0xFFF1F5F9)),
                        borderRadius: BorderRadius.circular(8),
                        border: Border.all(
                          color: shift.emergency ? const Color(0xFFFCA5A5) : cani.borderStrong,
                        ),
                      ),
                      child: Text(
                        shift.emergency ? 'Khẩn cấp (Mở ứng tuyển)' : 'Chờ lời mời (Matching)',
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(
                          fontSize: 10.5,
                          fontWeight: FontWeight.w700,
                          color: shift.emergency ? cani.danger : cani.textMuted,
                        ),
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
      ref.invalidate(incomeSummaryProvider);
      ref.read(assignmentsSubTabProvider.notifier).state = 0;
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Ứng tuyển thành công ca: ${shift.jobType} tại ${shift.eventName}!'),
            backgroundColor: Theme.of(context).cani.success,
            action: SnackBarAction(
              label: 'Xem ca',
              textColor: Colors.white,
              onPressed: () {
                ref.read(homeTabIndexProvider.notifier).state = 1;
                ref.read(assignmentsSubTabProvider.notifier).state = 0;
              },
            ),
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
    final isTechnicalFailure = ekycState.result?.isTechnicalFailure ?? false;
    final isRejected = ekycState.result?.isRejected ?? false;
    final cani = Theme.of(context).cani;

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
