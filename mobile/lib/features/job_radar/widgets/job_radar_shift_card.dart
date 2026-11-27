import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/app/cani_tokens.dart';
import 'package:cani/app/home_screen.dart';
import 'package:cani/core/utils/app_error_formatter.dart';
import 'package:cani/features/job_proposals/data/proposal_repository.dart';
import 'package:cani/features/job_proposals/models/proposal_models.dart';
import 'package:cani/features/job_proposals/widgets/job_proposal_invite_card.dart';
import 'package:cani/features/job_radar/data/job_radar_repository.dart';
import 'package:cani/features/job_radar/models/job_radar_models.dart';
import 'package:cani/features/job_radar/providers/job_radar_provider.dart';
import 'package:cani/features/job_radar/widgets/job_detail_bottom_sheet.dart';
import 'package:cani/features/job_radar/widgets/job_radar_filter_bar.dart';
import 'package:cani/features/job_radar/widgets/job_radar_proposals_sheet.dart';
import 'package:cani/features/job_radar/widgets/radar_sliding_job_card.dart';
import 'package:cani/shared/widgets/cani_snackbar.dart';

void showJobRadarShiftDetails(
  BuildContext context,
  WidgetRef ref,
  NearbyShiftItem shift,
) {
  final state = ref.read(jobRadarProvider);
  showModalBottomSheet(
    context: context,
    isScrollControlled: true,
    backgroundColor: Colors.transparent,
    builder: (ctx) => JobDetailBottomSheet(
      shift: shift,
      userLat: state.userLat,
      userLng: state.userLng,
      onReject: () {
        Navigator.pop(ctx);
        ref.read(jobRadarProvider.notifier).dismissShiftFromRadar(shift.shiftId);
        if (context.mounted) {
          CaniSnackBar.show(
            context,
            message: 'Đã bỏ qua ca làm việc này.',
            tone: CaniNoticeTone.info,
            bottomOffset: 76,
          );
        }
      },
      onApply: () {
        Navigator.pop(ctx);
        applyQuickForRadarShift(context, ref, shift);
      },
    ),
  );
}

void showJobRadarProposalDetails(
  BuildContext context,
  WidgetRef ref,
  JobProposalItem proposal,
) {
  showModalBottomSheet(
    context: context,
    isScrollControlled: true,
    backgroundColor: Colors.transparent,
    builder: (ctx) => JobProposalDetailBottomSheet(
      proposal: proposal,
      onReject: () async {
        Navigator.pop(ctx);
        await rejectProposalOnRadar(context, ref, proposal);
      },
      onAccept: () async {
        Navigator.pop(ctx);
        await acceptProposalOnRadar(context, ref, proposal);
      },
    ),
  );
}

Future<void> applyQuickForRadarShift(
  BuildContext context,
  WidgetRef ref,
  NearbyShiftItem shift,
) async {
  if (!shift.emergency) {
    if (context.mounted) {
      CaniSnackBar.show(
        context,
        message: 'Ca này chưa ở chế độ khẩn cấp. Vui lòng chờ lời mời từ Ban tổ chức.',
        tone: CaniNoticeTone.warning,
        bottomOffset: 76,
      );
    }
    return;
  }
  try {
    await ref.read(jobRadarRepositoryProvider).applyForShift(shift.shiftId);
    final radarNotifier = ref.read(jobRadarProvider.notifier);
    radarNotifier.removeShiftFromRadar(shift.shiftId);
    unawaited(radarNotifier.scanNearbyShifts(silent: true, force: true));
    ref.invalidate(proposalsListProvider);
    ref.invalidate(assignmentsListProvider);
    ref.invalidate(incomeSummaryProvider);
    ref.read(assignmentsSubTabProvider.notifier).state = 0;
    if (context.mounted) {
      CaniSnackBar.show(
        context,
        message: 'Đã nhận ca "${shift.jobType}". Xem trong mục Ca làm.',
        tone: CaniNoticeTone.success,
        actionLabel: 'Xem ca',
        bottomOffset: 76,
        onAction: () {
          ref.read(homeTabIndexProvider.notifier).state = 1;
          ref.read(assignmentsSubTabProvider.notifier).state = 0;
        },
      );
    }
  } catch (e) {
    final msg = AppErrorFormatter.format(e, fallback: 'Vui lòng thử lại.');
    if (msg.contains('đã nhận hoặc ứng tuyển')) {
      final radarNotifier = ref.read(jobRadarProvider.notifier);
      radarNotifier.removeShiftFromRadar(shift.shiftId);
      unawaited(radarNotifier.scanNearbyShifts(silent: true, force: true));
    }
    if (context.mounted) {
      CaniSnackBar.show(
        context,
        message: 'Chưa nhận được ca "${shift.jobType}". $msg',
        tone: CaniNoticeTone.error,
        bottomOffset: 76,
      );
    }
  }
}

class JobRadarShiftCard extends ConsumerWidget {
  final NearbyShiftItem shift;

  const JobRadarShiftCard({
    super.key,
    required this.shift,
  });

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final cani = Theme.of(context).cani;
    final notifier = ref.read(jobRadarProvider.notifier);

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
          showJobRadarShiftDetails(context, ref, shift);
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
                      color: isDark
                          ? const Color(0xFF064E3B).withValues(alpha: 0.4)
                          : const Color(0xFFF0FDF4),
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
                      style: TextStyle(
                        fontWeight: FontWeight.w800,
                        fontSize: 15,
                        color: cani.success,
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Flexible(
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                      decoration: BoxDecoration(
                        color: shift.isConflicting
                            ? cani.warningBg
                            : (shift.emergency
                                ? cani.dangerBg
                                : (isDark ? cani.surface : const Color(0xFFF1F5F9))),
                        borderRadius: BorderRadius.circular(8),
                        border: Border.all(
                          color: shift.isConflicting
                              ? cani.warning.withValues(alpha: 0.45)
                              : (shift.emergency ? const Color(0xFFFCA5A5) : cani.borderStrong),
                        ),
                      ),
                      child: Text(
                        shift.isConflicting
                            ? 'Bạn đã có ca nhận rồi'
                            : (shift.emergency ? 'Khẩn cấp (Mở ứng tuyển)' : 'Chờ lời mời (Matching)'),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(
                          fontSize: 10.5,
                          fontWeight: FontWeight.w700,
                          color: shift.isConflicting
                              ? cani.warning
                              : (shift.emergency ? cani.danger : cani.textMuted),
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
}

class JobRadarUnifiedBottomCard extends ConsumerStatefulWidget {
  const JobRadarUnifiedBottomCard({super.key});

  @override
  ConsumerState<JobRadarUnifiedBottomCard> createState() => _JobRadarUnifiedBottomCardState();
}

class _JobRadarUnifiedBottomCardState extends ConsumerState<JobRadarUnifiedBottomCard> {
  int _carouselIndex = 0;

  void _stepCarousel(int delta, int total) {
    if (total <= 1) return;
    setState(() {
      _carouselIndex = (_carouselIndex + delta) % total;
      if (_carouselIndex < 0) _carouselIndex += total;
    });
  }

  @override
  Widget build(BuildContext context) {
    final shiftSlice = ref.watch(
      jobRadarProvider.select(
        (s) => (
          isOnline: s.isOnline,
          selectedShift: s.selectedShift,
          nearbyShifts: s.nearbyShifts,
        ),
      ),
    );
    final proposals = ref.watch(
      proposalsListProvider.select((asyncVal) => asyncVal.asData?.value ?? const <JobProposalItem>[]),
    );
    if (!shiftSlice.isOnline) {
      return const SizedBox.shrink();
    }
    final now = DateTime.now();
    final activeProposals = proposals.where((p) => p.isActiveAt(now)).toList();
    final notifier = ref.read(jobRadarProvider.notifier);

    // 1. Nếu Worker chủ động chạm một marker hợp lệ trên bản đồ -> cập nhật thẻ theo ca đó
    if (shiftSlice.selectedShift != null) {
      final selected = shiftSlice.selectedShift!;
      return RadarSlidingJobCard(
        shift: selected,
        activeInviteCount: activeProposals.length,
        onViewInvites: activeProposals.isNotEmpty ? () => notifier.clearSelectedShift() : null,
        onViewDetails: () => showJobRadarShiftDetails(context, ref, selected),
        onApply: () => applyQuickForRadarShift(context, ref, selected),
      );
    }

    // 2. Popup trượt từng thẻ (1/N) cho toàn bộ Lời mời làm việc & Ca khẩn cấp nhận được ngay
    final proposalShiftIds = activeProposals.map((p) => p.shiftId).toSet();
    final emergencyShifts = shiftSlice.isOnline
        ? shiftSlice.nearbyShifts
            .where((s) => s.emergency && !proposalShiftIds.contains(s.shiftId))
            .toList()
        : const <NearbyShiftItem>[];

    final popupEntries = <({JobProposalItem? proposal, NearbyShiftItem? shift})>[
      ...activeProposals.map((p) => (proposal: p, shift: null)),
      ...emergencyShifts.map((s) => (proposal: null, shift: s)),
    ];

    if (popupEntries.isNotEmpty) {
      final safeIndex = _carouselIndex.clamp(0, popupEntries.length - 1);
      final entry = popupEntries[safeIndex];
      final total = popupEntries.length;

      final Widget card = entry.proposal != null
          ? JobProposalInviteCard(
              key: ValueKey('prop-${entry.proposal!.proposalId}'),
              compact: true,
              proposal: entry.proposal!,
              totalPendingCount: activeProposals.length,
              carouselIndex: safeIndex,
              carouselTotal: total,
              onPrev: () => _stepCarousel(-1, total),
              onNext: () => _stepCarousel(1, total),
              onViewAll: activeProposals.length > 1 ? () => showJobRadarProposalsSheet(context) : null,
              onViewDetails: () => showJobRadarProposalDetails(context, ref, entry.proposal!),
              onAccept: () => acceptProposalOnRadar(context, ref, entry.proposal!),
              onReject: () => rejectProposalOnRadar(context, ref, entry.proposal!),
            )
          : RadarSlidingJobCard(
              key: ValueKey('shift-${entry.shift!.shiftId}'),
              shift: entry.shift!,
              carouselIndex: safeIndex,
              carouselTotal: total,
              onPrev: () => _stepCarousel(-1, total),
              onNext: () => _stepCarousel(1, total),
              onViewDetails: () => showJobRadarShiftDetails(context, ref, entry.shift!),
              onApply: () => applyQuickForRadarShift(context, ref, entry.shift!),
            );

      return GestureDetector(
        onHorizontalDragEnd: total > 1
            ? (details) {
                final v = details.primaryVelocity ?? 0;
                if (v < -120) {
                  _stepCarousel(1, total);
                } else if (v > 120) {
                  _stepCarousel(-1, total);
                }
              }
            : null,
        child: AnimatedSwitcher(
          duration: const Duration(milliseconds: 200),
          child: card,
        ),
      );
    }

    // 3. Nếu chỉ có ca Matching thường quanh bạn khi đang Bật nhận việc -> giữ thẻ "Ca gần bạn"
    if (shiftSlice.isOnline && shiftSlice.nearbyShifts.isNotEmpty) {
      final firstShift = shiftSlice.nearbyShifts.first;
      return RadarSlidingJobCard(
        shift: firstShift,
        onViewDetails: () => showJobRadarShiftDetails(context, ref, firstShift),
        onApply: () => applyQuickForRadarShift(context, ref, firstShift),
      );
    }

    return const SizedBox.shrink();
  }
}

class JobRadarShiftListView extends ConsumerWidget {
  const JobRadarShiftListView({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final listSlice = ref.watch(
      jobRadarProvider.select(
        (s) => (
          isOnline: s.isOnline,
          isLoading: s.isLoading,
          hasLoadedShifts: s.hasLoadedShifts,
          radarLoadError: s.radarLoadError,
          nearbyShifts: s.nearbyShifts,
        ),
      ),
    );
    final proposalsAsync = ref.watch(proposalsListProvider);
    final notifier = ref.read(jobRadarProvider.notifier);
    final cani = Theme.of(context).cani;

    final now = DateTime.now();
    final activeProposals = listSlice.isOnline
        ? (proposalsAsync.asData?.value ?? const <JobProposalItem>[])
            .where((p) => p.isActiveAt(now))
            .toList()
        : const <JobProposalItem>[];

    final isLoadingAny = listSlice.isLoading || proposalsAsync.isLoading;
    final hasAnyError = proposalsAsync.hasError || listSlice.radarLoadError != null;
    final hasAnyVisibleData = activeProposals.isNotEmpty || listSlice.nearbyShifts.isNotEmpty;

    if (hasAnyError && !hasAnyVisibleData && !isLoadingAny) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                'Tải dữ liệu thất bại',
                style: TextStyle(fontWeight: FontWeight.w800, color: cani.textMain),
              ),
              const SizedBox(height: 8),
              FilledButton(
                onPressed: () => retryAllJobRadarData(ref),
                style: FilledButton.styleFrom(backgroundColor: cani.brand),
                child: const Text('Thử lại'),
              ),
            ],
          ),
        ),
      );
    }

    if (!isLoadingAny && listSlice.hasLoadedShifts && !hasAnyVisibleData) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                listSlice.isOnline
                    ? 'Đang tìm ca quanh bạn'
                    : 'Bạn đang tạm tắt Nhận việc',
                style: TextStyle(fontWeight: FontWeight.w700, color: cani.textMain),
              ),
              const SizedBox(height: 8),
              if (listSlice.isOnline)
                TextButton(
                  onPressed: () => notifier.setRadius(15.0),
                  child: const Text('Mở rộng bán kính 15 km'),
                )
              else
                FilledButton(
                  onPressed: () => notifier.toggleOnlineStatus(),
                  style: FilledButton.styleFrom(backgroundColor: cani.brand),
                  child: const Text('Bật Nhận việc ngay'),
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
                      style: TextStyle(
                        fontSize: 12,
                        fontWeight: FontWeight.w700,
                        color: cani.danger,
                      ),
                    ),
                  ),
                  TextButton(
                    onPressed: () => retryAllJobRadarData(ref),
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
                onAccept: () => acceptProposalOnRadar(context, ref, item),
                onReject: () => rejectProposalOnRadar(context, ref, item),
              ),
            ),
            const SizedBox(height: 6),
          ],
          if (listSlice.nearbyShifts.isNotEmpty) ...[
            Text(
              'Ca quanh bạn (${listSlice.nearbyShifts.length})',
              style: TextStyle(fontSize: 13.5, fontWeight: FontWeight.w800, color: cani.textMain),
            ),
            const SizedBox(height: 8),
            ...listSlice.nearbyShifts.map(
              (shift) => JobRadarShiftCard(shift: shift),
            ),
          ],
        ],
      ),
    );
  }
}
