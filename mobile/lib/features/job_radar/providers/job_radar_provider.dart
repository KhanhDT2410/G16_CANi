import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:geolocator/geolocator.dart';

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
  final bool isLocationPermissionGranted;
  final bool isRealLocation;
  final String? locationName;

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
    this.isLocationPermissionGranted = false,
    this.isRealLocation = false,
    this.locationName,
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
    bool? isLocationPermissionGranted,
    bool? isRealLocation,
    String? locationName,
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
      isLocationPermissionGranted: isLocationPermissionGranted ?? this.isLocationPermissionGranted,
      isRealLocation: isRealLocation ?? this.isRealLocation,
      locationName: locationName ?? this.locationName,
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
    initLocationAndScan();
  }

  /// Khởi tạo lấy vị trí GPS thực tế từ máy điện thoại và hỏi quyền nếu cần
  Future<void> initLocationAndScan() async {
    state = state.copyWith(isLoading: true, errorMessage: null);
    try {
      bool serviceEnabled = await Geolocator.isLocationServiceEnabled();
      if (!serviceEnabled) {
        state = state.copyWith(
          errorMessage: 'GPS trên điện thoại đang tắt. Vui lòng bật Định vị vị trí.',
          isLocationPermissionGranted: false,
        );
        await scanNearbyShifts();
        return;
      }

      LocationPermission permission = await Geolocator.checkPermission();
      if (permission == LocationPermission.denied) {
        permission = await Geolocator.requestPermission();
        if (permission == LocationPermission.denied) {
          state = state.copyWith(
            errorMessage: 'Ứng dụng cần quyền vị trí để quét các việc làm quanh bạn.',
            isLocationPermissionGranted: false,
          );
          await scanNearbyShifts();
          return;
        }
      }

      if (permission == LocationPermission.deniedForever) {
        state = state.copyWith(
          errorMessage: 'Quyền vị trí bị từ chối vĩnh viễn. Vui lòng cấp quyền trong Cài đặt.',
          isLocationPermissionGranted: false,
        );
        await scanNearbyShifts();
        return;
      }

      // Đã có quyền: Lấy tọa độ GPS thực tế của điện thoại
      final position = await Geolocator.getCurrentPosition(
        locationSettings: const LocationSettings(
          accuracy: LocationAccuracy.high,
          timeLimit: Duration(seconds: 10),
        ),
      );

      state = state.copyWith(
        userLat: position.latitude,
        userLng: position.longitude,
        isLocationPermissionGranted: true,
        isRealLocation: true,
        locationName: 'Vị trí hiện tại (${position.latitude.toStringAsFixed(4)}, ${position.longitude.toStringAsFixed(4)})',
      );
    } catch (e) {
      // Fallback nếu không bắt được GPS
      state = state.copyWith(
        errorMessage: 'Không lấy được GPS chính xác ($e), đang dùng vị trí mặc định.',
      );
    } finally {
      await scanNearbyShifts();
    }
  }

  /// Nút GPS Recenter: Lấy lại vị trí GPS thực tế tức thì
  Future<void> refreshCurrentLocation() async {
    await initLocationAndScan();
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
