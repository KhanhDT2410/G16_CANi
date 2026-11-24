import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/app/cani_tokens.dart';
import 'package:cani/features/auth/providers/auth_provider.dart';
import 'package:cani/features/ekyc/providers/ekyc_provider.dart';
import 'package:cani/features/job_proposals/data/proposal_repository.dart';
import 'package:cani/features/job_proposals/models/proposal_models.dart';
import 'package:cani/features/job_radar/providers/job_radar_provider.dart';
import 'package:cani/features/job_radar/widgets/job_radar_ekyc_banners.dart';
import 'package:cani/features/job_radar/widgets/job_radar_proposals_sheet.dart';

void retryAllJobRadarData(WidgetRef ref) {
  ref.invalidate(proposalsListProvider);
  ref.read(jobRadarProvider.notifier).scanNearbyShifts();
}

class JobRadarCompactHeader extends ConsumerWidget {
  const JobRadarCompactHeader({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final userName = ref.watch(
      authStateProvider.select((s) {
        final raw = s.user?.fullName.trim() ?? '';
        return raw.isNotEmpty ? raw : 'Nhân Sự';
      }),
    );
    final isEkycApproved = ref.watch(
      ekycProvider.select((s) => s.result?.isApproved ?? false),
    );
    final isOnline = ref.watch(
      jobRadarProvider.select((s) => s.isOnline),
    );
    final notifier = ref.read(jobRadarProvider.notifier);

    final initialChar = userName.isNotEmpty ? userName[0].toUpperCase() : 'N';
    final isReceiving = isEkycApproved && isOnline;
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final cani = Theme.of(context).cani;

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
                    showJobRadarEkycRequiredDialog(context, ref);
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
                            alignment:
                                isReceiving ? Alignment.centerRight : Alignment.centerLeft,
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
}

class JobRadarFilterBar extends ConsumerWidget {
  const JobRadarFilterBar({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final filterSlice = ref.watch(
      jobRadarProvider.select(
        (s) => (
          hasPreferredLocation: s.hasPreferredLocation,
          usingPreferenceLocation: s.usingPreferenceLocation,
          preferredAddress: s.preferredAddress,
          isRealLocation: s.isRealLocation,
          searchRadiusKm: s.searchRadiusKm,
          isRadarView: s.isRadarView,
          nearbyShiftsCount: s.nearbyShifts.length,
        ),
      ),
    );
    final notifier = ref.read(jobRadarProvider.notifier);
    final cani = Theme.of(context).cani;

    final areaLabel = filterSlice.hasPreferredLocation
        ? (filterSlice.usingPreferenceLocation
            ? (filterSlice.preferredAddress?.trim().isNotEmpty == true
                ? filterSlice.preferredAddress!.trim()
                : 'Khu vực ưu tiên')
            : 'Vị trí GPS')
        : (filterSlice.isRealLocation ? 'Vị trí GPS' : 'Khu vực');

    return Padding(
      padding: const EdgeInsets.fromLTRB(12, 4, 12, 6),
      child: Row(
        children: [
          // 1. Khu vực
          Expanded(
            flex: 4,
            child: InkWell(
              onTap: () {
                if (filterSlice.hasPreferredLocation) {
                  notifier.switchCenterMode(
                    usePreference: !filterSlice.usingPreferenceLocation,
                  );
                } else {
                  notifier.refreshCurrentLocation();
                }
              },
              borderRadius: BorderRadius.circular(10),
              child: Container(
                height: 36,
                padding: const EdgeInsets.symmetric(horizontal: 7),
                decoration: BoxDecoration(
                  color: (filterSlice.hasPreferredLocation &&
                          filterSlice.usingPreferenceLocation)
                      ? cani.brandSoft
                      : cani.surface,
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(
                    color: (filterSlice.hasPreferredLocation &&
                            filterSlice.usingPreferenceLocation)
                        ? cani.brand.withValues(alpha: 0.35)
                        : cani.border,
                  ),
                ),
                child: Row(
                  children: [
                    Icon(
                      filterSlice.usingPreferenceLocation
                          ? Icons.tune_rounded
                          : Icons.my_location_rounded,
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
                    if (filterSlice.hasPreferredLocation) ...[
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
              initialValue: filterSlice.searchRadiusKm,
              onSelected: (r) => notifier.setRadius(r),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
              itemBuilder: (_) => [5.0, 10.0, 15.0, 20.0, 25.0, 30.0]
                  .map(
                    (r) => PopupMenuItem<double>(
                      value: r,
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Text(
                            'Bán kính ${r.toInt()} km',
                            style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 13),
                          ),
                          if (filterSlice.searchRadiusKm == r)
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
                        '${filterSlice.searchRadiusKm.toInt()} km',
                        maxLines: 1,
                        softWrap: false,
                        style: TextStyle(
                          fontSize: 11,
                          fontWeight: FontWeight.w700,
                          color: cani.textMain,
                        ),
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
              onTap: () => showJobRadarInviteCodeDialog(context, ref),
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
                        style: TextStyle(
                          fontSize: 11,
                          fontWeight: FontWeight.w700,
                          color: cani.brand,
                        ),
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
                        filterSlice.isRadarView ? Icons.view_list_rounded : Icons.map_outlined,
                        size: 15,
                        color: cani.textMain,
                      ),
                      const SizedBox(width: 3),
                      Text(
                        filterSlice.isRadarView
                            ? 'DS (${filterSlice.nearbyShiftsCount})'
                            : 'Bản đồ',
                        maxLines: 1,
                        softWrap: false,
                        style: TextStyle(
                          fontSize: 11,
                          fontWeight: FontWeight.w700,
                          color: cani.textMain,
                        ),
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
}

class JobRadarMapTopOverlay extends ConsumerWidget {
  const JobRadarMapTopOverlay({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final overlaySlice = ref.watch(
      jobRadarProvider.select(
        (s) => (
          isOnline: s.isOnline,
          isLoading: s.isLoading,
          hasLoadedShifts: s.hasLoadedShifts,
          radarLoadError: s.radarLoadError,
          hasNearbyShifts: s.nearbyShifts.isNotEmpty,
        ),
      ),
    );
    final proposalsAsync = ref.watch(proposalsListProvider);
    final cani = Theme.of(context).cani;

    final now = DateTime.now();
    final hasActiveProposals = overlaySlice.isOnline &&
        (proposalsAsync.asData?.value ?? const <JobProposalItem>[])
            .any((p) => p.isActiveAt(now));

    final isLoadingAny = overlaySlice.isLoading || proposalsAsync.isLoading;
    final hasAnyError = overlaySlice.radarLoadError != null;
    final hasAnyVisibleData = hasActiveProposals || overlaySlice.hasNearbyShifts;

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
                  hasAnyVisibleData
                      ? 'Tải cập nhật thất bại'
                      : (overlaySlice.radarLoadError != null && overlaySlice.radarLoadError!.isNotEmpty
                          ? overlaySlice.radarLoadError!
                          : 'Không thể tải dữ liệu Radar'),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                    fontSize: 11.5,
                    fontWeight: FontWeight.w700,
                    color: cani.danger,
                  ),
                ),
              ),
              const SizedBox(width: 8),
              InkWell(
                onTap: () => retryAllJobRadarData(ref),
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
                  style: TextStyle(
                    fontSize: 11.5,
                    fontWeight: FontWeight.w700,
                    color: cani.textBody,
                  ),
                ),
              ),
            ],
          ),
        ),
      );
    }

    if (overlaySlice.hasLoadedShifts && !hasAnyVisibleData) {
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
            !overlaySlice.isOnline
                ? 'Đang tạm tắt nhận việc · Bật Nhận việc để tìm ca'
                : 'Đang tìm ca quanh bạn',
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

    return const SizedBox.shrink();
  }
}
