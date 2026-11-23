import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/app/cani_tokens.dart';
import 'package:cani/features/ekyc/providers/ekyc_provider.dart';
import 'package:cani/features/job_proposals/data/proposal_repository.dart';
import 'package:cani/features/job_proposals/models/proposal_models.dart';
import 'package:cani/features/job_radar/providers/job_radar_provider.dart';
import 'package:cani/features/job_radar/widgets/job_radar_ekyc_banners.dart';
import 'package:cani/features/job_radar/widgets/job_radar_filter_bar.dart';
import 'package:cani/features/job_radar/widgets/job_radar_map_painter.dart';
import 'package:cani/features/job_radar/widgets/job_radar_proposals_sheet.dart';
import 'package:cani/features/job_radar/widgets/job_radar_shift_card.dart';

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
      if (mounted) {
        ref.read(ekycProvider.notifier).checkStatus();
      }
    });
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

    final isEkycApproved = ref.watch(
      ekycProvider.select((s) => s.result?.isApproved ?? false),
    );
    final isRadarView = ref.watch(
      jobRadarProvider.select((s) => s.isRadarView),
    );

    return Scaffold(
      backgroundColor: Theme.of(context).scaffoldBackgroundColor,
      body: SafeArea(
        bottom: false,
        child: Column(
          children: [
            const JobRadarCompactHeader(),
            if (!isEkycApproved) const JobRadarEkycWarningBanner(),
            if (isEkycApproved) const JobRadarFilterBar(),
            if (isEkycApproved) const JobRadarProposalsExpiryWatcher(),
            Expanded(
              child: !isEkycApproved
                  ? const JobRadarEkycLockedState()
                  : isRadarView
                      ? const _JobRadarMapSection()
                      : const JobRadarShiftListView(),
            ),
          ],
        ),
      ),
    );
  }
}

class _JobRadarMapSection extends ConsumerWidget {
  const _JobRadarMapSection();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final mapSlice = ref.watch(
      jobRadarProvider.select(
        (s) => (
          nearbyShifts: s.nearbyShifts,
          selectedShift: s.selectedShift,
          searchRadiusKm: s.searchRadiusKm,
          isOnline: s.isOnline,
          userLat: s.userLat,
          userLng: s.userLng,
          isLoading: s.isLoading,
          hasLoadedShifts: s.hasLoadedShifts,
          radarLoadError: s.radarLoadError,
        ),
      ),
    );
    final proposalsAsync = ref.watch(proposalsListProvider);
    final notifier = ref.read(jobRadarProvider.notifier);
    final cani = Theme.of(context).cani;

    final now = DateTime.now();
    final hasActiveProposals = (proposalsAsync.asData?.value ?? const <JobProposalItem>[])
        .any((p) => p.isActiveAt(now));

    final isLoadingAny = mapSlice.isLoading || proposalsAsync.isLoading;
    final hasAnyError = mapSlice.radarLoadError != null;
    final hasAnyVisibleData = hasActiveProposals || mapSlice.nearbyShifts.isNotEmpty;

    final showTopOverlay =
        (hasAnyError && !isLoadingAny) ||
        isLoadingAny ||
        (mapSlice.hasLoadedShifts && !hasAnyVisibleData);
    final showBottomOverlay =
        mapSlice.selectedShift != null ||
        hasActiveProposals ||
        mapSlice.nearbyShifts.isNotEmpty;

    return Container(
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
        shifts: mapSlice.nearbyShifts,
        selectedShift: mapSlice.selectedShift,
        maxRadiusKm: mapSlice.searchRadiusKm,
        isOnline: mapSlice.isOnline,
        userLat: mapSlice.userLat,
        userLng: mapSlice.userLng,
        onRecenterGps: () => notifier.refreshCurrentLocation(),
        onSelectShift: (shift) => notifier.selectShift(shift),
        topOverlay: showTopOverlay ? const JobRadarMapTopOverlay() : null,
        bottomOverlay: showBottomOverlay ? const JobRadarUnifiedBottomCard() : null,
      ),
    );
  }
}
