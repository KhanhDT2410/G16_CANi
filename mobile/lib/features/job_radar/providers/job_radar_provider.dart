import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/features/job_radar/data/job_radar_repository.dart';
import 'package:cani/features/job_radar/models/job_radar_models.dart';

class JobRadarState {
  final bool isOnline;
  final bool isLoading;
  final double userLat;
  final double userLng;
  final double searchRadiusKm;
  final List<NearbyShiftItem> nearbyShifts;
  final NearbyShiftItem? selectedShift;
  final String? errorMessage;
  final bool isRadarView;

  const JobRadarState({
    this.isOnline = true,
    this.isLoading = false,
    this.userLat = 10.7769,
    this.userLng = 106.7009,
    this.searchRadiusKm = 15.0,
    this.nearbyShifts = const [],
    this.selectedShift,
    this.errorMessage,
    this.isRadarView = true,
  });

  JobRadarState copyWith({
    bool? isOnline,
    bool? isLoading,
    double? userLat,
    double? userLng,
    double? searchRadiusKm,
    List<NearbyShiftItem>? nearbyShifts,
    NearbyShiftItem? selectedShift,
    String? errorMessage,
    bool? isRadarView,
  }) {
    return JobRadarState(
      isOnline: isOnline ?? this.isOnline,
      isLoading: isLoading ?? this.isLoading,
      userLat: userLat ?? this.userLat,
      userLng: userLng ?? this.userLng,
      searchRadiusKm: searchRadiusKm ?? this.searchRadiusKm,
      nearbyShifts: nearbyShifts ?? this.nearbyShifts,
      selectedShift: selectedShift,
      errorMessage: errorMessage,
      isRadarView: isRadarView ?? this.isRadarView,
    );
  }
}

final jobRadarProvider = StateNotifierProvider<JobRadarNotifier, JobRadarState>((ref) {
  final repo = ref.watch(jobRadarRepositoryProvider);
  return JobRadarNotifier(repo);
});

class JobRadarNotifier extends StateNotifier<JobRadarState> {
  final JobRadarRepository _repo;

  JobRadarNotifier(this._repo) : super(const JobRadarState()) {
    scanNearbyShifts();
  }

  Future<void> toggleOnlineStatus() async {
    final nextStatus = !state.isOnline;
    state = state.copyWith(isOnline: nextStatus);
    await _repo.setOnlineStatus(
      isOnline: nextStatus,
      lat: state.userLat,
      lng: state.userLng,
    );
    if (nextStatus) {
      scanNearbyShifts();
    }
  }

  Future<void> setRadius(double radiusKm) async {
    state = state.copyWith(searchRadiusKm: radiusKm);
    scanNearbyShifts();
  }

  void toggleViewMode(bool isRadar) {
    state = state.copyWith(isRadarView: isRadar);
  }

  void selectShift(NearbyShiftItem? shift) {
    state = state.copyWith(selectedShift: shift);
  }

  Future<void> scanNearbyShifts() async {
    state = state.copyWith(isLoading: true, errorMessage: null);
    try {
      final shifts = await _repo.getNearbyShifts(
        lat: state.userLat,
        lng: state.userLng,
        radiusKm: state.searchRadiusKm,
      );
      state = state.copyWith(
        isLoading: false,
        nearbyShifts: shifts,
      );
    } catch (e) {
      state = state.copyWith(
        isLoading: false,
        errorMessage: 'Không thể kết nối đến máy chủ radar: $e',
      );
    }
  }
}
