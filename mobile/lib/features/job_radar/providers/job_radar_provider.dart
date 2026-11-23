import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:geolocator/geolocator.dart';

import 'package:cani/features/job_proposals/data/proposal_repository.dart';
import 'package:cani/features/job_proposals/models/proposal_models.dart';
import 'package:cani/features/job_radar/data/job_radar_repository.dart';
import 'package:cani/features/job_radar/models/job_radar_models.dart';

class JobRadarState {
  final bool isOnline;
  final bool isLoading;
  final bool hasLoadedShifts;
  final double userLat;
  final double userLng;
  final double searchRadiusKm;
  final List<NearbyShiftItem> nearbyShifts;
  final NearbyShiftItem? selectedShift;
  final String? errorMessage;
  final String? radarLoadError;
  final bool isRadarView;
  final bool isLocationPermissionGranted;
  final bool isRealLocation;
  final String? locationName;
  final bool usingPreferenceLocation;
  final double? preferredLat;
  final double? preferredLng;
  final String? preferredAddress;
  final double? gpsLat;
  final double? gpsLng;

  const JobRadarState({
    this.isOnline = false,
    this.isLoading = false,
    this.hasLoadedShifts = false,
    this.userLat = 21.0285,
    this.userLng = 105.8542,
    this.searchRadiusKm = 15.0,
    this.nearbyShifts = const [],
    this.selectedShift,
    this.errorMessage,
    this.radarLoadError,
    this.isRadarView = true,
    this.isLocationPermissionGranted = false,
    this.isRealLocation = false,
    this.locationName,
    this.usingPreferenceLocation = false,
    this.preferredLat,
    this.preferredLng,
    this.preferredAddress,
    this.gpsLat,
    this.gpsLng,
  });

  bool get hasPreferredLocation => preferredLat != null && preferredLng != null;

  JobRadarState copyWith({
    bool? isOnline,
    bool? isLoading,
    bool? hasLoadedShifts,
    double? userLat,
    double? userLng,
    double? searchRadiusKm,
    List<NearbyShiftItem>? nearbyShifts,
    NearbyShiftItem? selectedShift,
    bool clearSelectedShift = false,
    String? errorMessage,
    String? radarLoadError,
    bool clearRadarLoadError = false,
    bool? isRadarView,
    bool? isLocationPermissionGranted,
    bool? isRealLocation,
    String? locationName,
    bool? usingPreferenceLocation,
    double? preferredLat,
    double? preferredLng,
    String? preferredAddress,
    double? gpsLat,
    double? gpsLng,
  }) {
    return JobRadarState(
      isOnline: isOnline ?? this.isOnline,
      isLoading: isLoading ?? this.isLoading,
      hasLoadedShifts: hasLoadedShifts ?? this.hasLoadedShifts,
      userLat: userLat ?? this.userLat,
      userLng: userLng ?? this.userLng,
      searchRadiusKm: searchRadiusKm ?? this.searchRadiusKm,
      nearbyShifts: nearbyShifts ?? this.nearbyShifts,
      selectedShift: clearSelectedShift ? null : (selectedShift ?? this.selectedShift),
      errorMessage: errorMessage,
      radarLoadError: clearRadarLoadError ? null : (radarLoadError ?? this.radarLoadError),
      isRadarView: isRadarView ?? this.isRadarView,
      isLocationPermissionGranted: isLocationPermissionGranted ?? this.isLocationPermissionGranted,
      isRealLocation: isRealLocation ?? this.isRealLocation,
      locationName: locationName ?? this.locationName,
      usingPreferenceLocation: usingPreferenceLocation ?? this.usingPreferenceLocation,
      preferredLat: preferredLat ?? this.preferredLat,
      preferredLng: preferredLng ?? this.preferredLng,
      preferredAddress: preferredAddress ?? this.preferredAddress,
      gpsLat: gpsLat ?? this.gpsLat,
      gpsLng: gpsLng ?? this.gpsLng,
    );
  }
}

final jobRadarProvider = StateNotifierProvider<JobRadarNotifier, JobRadarState>((ref) {
  final repo = ref.watch(jobRadarRepositoryProvider);
  final proposalRepo = ref.watch(proposalRepositoryProvider);
  return JobRadarNotifier(repo, proposalRepo, ref);
});

class JobRadarNotifier extends StateNotifier<JobRadarState> {
  final JobRadarRepository _repo;
  final ProposalRepository _proposalRepo;
  final Ref _ref;
  bool _isTogglingStatus = false;
  bool _hasUserToggledOnline = false;

  JobRadarNotifier(this._repo, this._proposalRepo, this._ref) : super(const JobRadarState()) {
    initLocationAndScan();
  }

  /// Khởi tạo: đồng bộ Preferences (tọa độ ưu tiên + bán kính + trạng thái isOnline thật) & vị trí GPS thực tế
  Future<void> initLocationAndScan({bool preferGpsCenter = false}) async {
    state = state.copyWith(isLoading: true, errorMessage: null, clearRadarLoadError: true);

    WorkerPreferencesModel? prefs;
    try {
      prefs = await _proposalRepo.fetchPreferences();
      state = state.copyWith(
        searchRadiusKm: prefs.maxDistanceKm > 0 ? prefs.maxDistanceKm : state.searchRadiusKm,
        isOnline: _hasUserToggledOnline ? state.isOnline : (prefs.isOnline ?? state.isOnline),
        preferredLat: prefs.baseLat,
        preferredLng: prefs.baseLng,
        preferredAddress: prefs.baseAddress,
      );
    } catch (_) {
      // Bỏ qua nếu chưa lấy được preferences
    }

    final hasPrefCenter = prefs?.baseLat != null && prefs?.baseLng != null;
    if (!preferGpsCenter && hasPrefCenter) {
      state = state.copyWith(
        userLat: prefs!.baseLat!,
        userLng: prefs.baseLng!,
        usingPreferenceLocation: true,
        locationName: prefs.baseAddress.trim().isNotEmpty ? prefs.baseAddress.trim() : 'Khu vực ưu tiên',
      );
    }

    try {
      final serviceEnabled = await Geolocator.isLocationServiceEnabled().timeout(
        const Duration(seconds: 3),
        onTimeout: () => false,
      );
      if (!serviceEnabled) {
        if (preferGpsCenter || !hasPrefCenter) {
          state = state.copyWith(
            errorMessage: 'GPS trên điện thoại đang tắt. Vui lòng bật Định vị vị trí.',
            isLocationPermissionGranted: false,
          );
        }
        return;
      }

      LocationPermission permission = await Geolocator.checkPermission().timeout(
        const Duration(seconds: 3),
        onTimeout: () => LocationPermission.denied,
      );
      if (permission == LocationPermission.denied) {
        permission = await Geolocator.requestPermission();
        if (permission == LocationPermission.denied) {
          if (preferGpsCenter || !hasPrefCenter) {
            state = state.copyWith(
              errorMessage: 'Ứng dụng cần quyền vị trí để quét các việc làm quanh bạn.',
              isLocationPermissionGranted: false,
            );
          }
          return;
        }
      }

      if (permission == LocationPermission.deniedForever) {
        if (preferGpsCenter || !hasPrefCenter) {
          state = state.copyWith(
            errorMessage: 'Quyền vị trí bị từ chối vĩnh viễn. Vui lòng cấp quyền trong Cài đặt.',
            isLocationPermissionGranted: false,
          );
        }
        return;
      }

      final position = await Geolocator.getCurrentPosition(
        locationSettings: const LocationSettings(
          accuracy: LocationAccuracy.high,
          timeLimit: Duration(seconds: 10),
        ),
      );

      state = state.copyWith(
        gpsLat: position.latitude,
        gpsLng: position.longitude,
        isLocationPermissionGranted: true,
        isRealLocation: true,
      );

      if (preferGpsCenter || !hasPrefCenter) {
        state = state.copyWith(
          userLat: position.latitude,
          userLng: position.longitude,
          usingPreferenceLocation: false,
          locationName: 'Vị trí hiện tại (${position.latitude.toStringAsFixed(4)}, ${position.longitude.toStringAsFixed(4)})',
        );
      }
    } catch (e) {
      if (preferGpsCenter || !hasPrefCenter) {
        state = state.copyWith(
          errorMessage: 'Không lấy được GPS chính xác ($e).',
        );
      }
    } finally {
      await scanNearbyShifts();
    }
  }

  /// Chuyển đổi tâm bản đồ giữa Khu vực ưu tiên (Preferences) và Vị trí GPS hiện tại
  Future<void> switchCenterMode({required bool usePreference}) async {
    if (usePreference && state.preferredLat != null && state.preferredLng != null) {
      state = state.copyWith(
        userLat: state.preferredLat!,
        userLng: state.preferredLng!,
        usingPreferenceLocation: true,
        clearSelectedShift: true,
        locationName: (state.preferredAddress != null && state.preferredAddress!.trim().isNotEmpty)
            ? state.preferredAddress!.trim()
            : 'Khu vực ưu tiên',
      );
      await scanNearbyShifts();
    } else if (!usePreference) {
      await initLocationAndScan(preferGpsCenter: true);
    }
  }

  /// Tải lại tiêu chí tìm việc (Preferences) và quét lại Radar + cập nhật đề xuất tức thì
  Future<void> reloadPreferences() async {
    try {
      final prefs = await _proposalRepo.fetchPreferences();
      final hasPrefCenter = prefs.baseLat != null && prefs.baseLng != null;
      state = state.copyWith(
        searchRadiusKm: prefs.maxDistanceKm > 0 ? prefs.maxDistanceKm : state.searchRadiusKm,
        isOnline: prefs.isOnline ?? state.isOnline,
        preferredLat: prefs.baseLat,
        preferredLng: prefs.baseLng,
        preferredAddress: prefs.baseAddress,
        userLat: hasPrefCenter ? prefs.baseLat! : state.userLat,
        userLng: hasPrefCenter ? prefs.baseLng! : state.userLng,
        usingPreferenceLocation: hasPrefCenter ? true : state.usingPreferenceLocation,
        locationName: hasPrefCenter && prefs.baseAddress.trim().isNotEmpty
            ? prefs.baseAddress.trim()
            : state.locationName,
        clearSelectedShift: true,
      );
      await scanNearbyShifts();
      _ref.invalidate(proposalsListProvider);
    } catch (_) {}
  }

  /// Nút GPS Recenter: Lấy lại vị trí GPS thực tế và chuyển tâm sang GPS
  Future<void> refreshCurrentLocation() async {
    await initLocationAndScan(preferGpsCenter: true);
  }

  Future<void> toggleOnlineStatus() async {
    if (_isTogglingStatus) return;
    _isTogglingStatus = true;
    _hasUserToggledOnline = true;
    final prevStatus = state.isOnline;
    final nextStatus = !prevStatus;
    state = state.copyWith(isOnline: nextStatus, errorMessage: null);
    try {
      final confirmedStatus = await _repo.setOnlineStatus(
        isOnline: nextStatus,
        lat: state.usingPreferenceLocation ? state.preferredLat : (state.gpsLat ?? state.userLat),
        lng: state.usingPreferenceLocation ? state.preferredLng : (state.gpsLng ?? state.userLng),
      );
      if (mounted) {
        state = state.copyWith(isOnline: confirmedStatus, errorMessage: null);
      }
      if (confirmedStatus) {
        await scanNearbyShifts();
        _ref.invalidate(proposalsListProvider);
      }
    } catch (e) {
      final cleanError = e.toString().replaceFirst('Exception: ', '').trim();
      if (mounted) {
        state = state.copyWith(
          isOnline: prevStatus,
          errorMessage: cleanError.isNotEmpty ? cleanError : 'Không thể cập nhật trạng thái nhận việc',
        );
      }
    } finally {
      _isTogglingStatus = false;
    }
  }

  Future<void> setRadius(double radiusKm) async {
    state = state.copyWith(searchRadiusKm: radiusKm);
    await scanNearbyShifts();
  }

  void toggleViewMode(bool isRadar) {
    state = state.copyWith(isRadarView: isRadar);
  }

  void toggleRadarView() {
    state = state.copyWith(isRadarView: !state.isRadarView);
  }

  void selectShift(NearbyShiftItem? shift) {
    if (shift == null) {
      state = state.copyWith(clearSelectedShift: true);
    } else {
      state = state.copyWith(selectedShift: shift);
    }
  }

  void clearSelectedShift() {
    state = state.copyWith(clearSelectedShift: true);
  }

  Future<void> scanNearbyShifts({bool silent = false}) async {
    if (!silent) {
      state = state.copyWith(isLoading: true, clearRadarLoadError: true);
    }
    try {
      final shifts = await _repo.getNearbyShifts(
        lat: state.userLat,
        lng: state.userLng,
        radiusKm: state.searchRadiusKm,
      );
      if (!mounted) return;

      NearbyShiftItem? resolvedSelection;
      var clearSelection = false;
      final currentSelection = state.selectedShift;
      if (currentSelection != null) {
        final matchIdx = shifts.indexWhere((s) => s.shiftId == currentSelection.shiftId);
        if (matchIdx >= 0) {
          resolvedSelection = shifts[matchIdx];
        } else {
          clearSelection = true;
        }
      }

      state = state.copyWith(
        isLoading: false,
        hasLoadedShifts: true,
        nearbyShifts: shifts,
        selectedShift: resolvedSelection,
        clearSelectedShift: clearSelection,
        clearRadarLoadError: true,
      );
    } catch (e) {
      if (!mounted) return;
      final clean = e.toString().replaceFirst('Exception: ', '').trim();
      state = state.copyWith(
        isLoading: false,
        hasLoadedShifts: true,
        radarLoadError: clean.isNotEmpty ? clean : 'Không thể kết nối đến máy chủ radar.',
      );
    }
  }
}
