import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/app/cani_tokens.dart';
import 'package:cani/app/home_screen.dart';
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
      onApply: () {
        Navigator.pop(ctx);
        applyQuickForRadarShift(context, ref, shift);
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
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: const Text(
            'Ca này chưa ở chế độ khẩn cấp — hãy chờ lời mời việc (Matching) từ Ban tổ chức!',
          ),
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
    if (context.mounted) {
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
    if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Không thể ứng tuyển: ${e.toString().replaceAll("Exception: ", "")}'),
          backgroundColor: Theme.of(context).cani.danger,
        ),
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
                        color: shift.emergency
                            ? cani.dangerBg
                            : (isDark ? cani.surface : const Color(0xFFF1F5F9)),
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
}

class JobRadarUnifiedBottomCard extends ConsumerWidget {
  const JobRadarUnifiedBottomCard({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final shiftSlice = ref.watch(
      jobRadarProvider.select(
        (s) => (
          selectedShift: s.selectedShift,
          firstNearbyShift: s.nearbyShifts.isNotEmpty ? s.nearbyShifts.first : null,
        ),
      ),
    );
    final proposals = ref.watch(
      proposalsListProvider.select((asyncVal) => asyncVal.asData?.value ?? const <JobProposalItem>[]),
    );
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

    // 2. Ưu tiên lời mời đang chờ phản hồi và còn hiệu lực
    if (activeProposals.isNotEmpty) {
      final topInvite = activeProposals.first;
      return JobProposalInviteCard(
        compact: true,
        proposal: topInvite,
        totalPendingCount: activeProposals.length,
        onViewAll: activeProposals.length > 1 ? () => showJobRadarProposalsSheet(context) : null,
        onAccept: () => acceptProposalOnRadar(context, ref, topInvite),
        onReject: () => rejectProposalOnRadar(context, ref, topInvite),
      );
    }

    // 3. Không có lời mời hiệu lực -> hiển thị thẻ "Ca gần bạn" nếu có ca quanh bạn
    if (shiftSlice.firstNearbyShift != null) {
      final firstShift = shiftSlice.firstNearbyShift!;
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
    final activeProposals = (proposalsAsync.asData?.value ?? const <JobProposalItem>[])
        .where((p) => p.isActiveAt(now))
        .toList();

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
              Icon(Icons.cloud_off_rounded, size: 42, color: cani.danger),
              const SizedBox(height: 10),
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
