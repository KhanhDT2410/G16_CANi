import 'package:cani/app/cani_tokens.dart';
import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:latlong2/latlong.dart';

import 'package:cani/features/job_radar/models/job_radar_models.dart';

class JobRadarMapWidget extends StatefulWidget {
  final List<NearbyShiftItem> shifts;
  final NearbyShiftItem? selectedShift;
  final ValueChanged<NearbyShiftItem> onSelectShift;
  final double maxRadiusKm;
  final bool isOnline;
  final double userLat;
  final double userLng;
  final VoidCallback? onRecenterGps;
  final bool hasBottomCard;

  const JobRadarMapWidget({
    super.key,
    required this.shifts,
    this.selectedShift,
    required this.onSelectShift,
    this.maxRadiusKm = 15.0,
    this.isOnline = true,
    this.userLat = 10.7769,
    this.userLng = 106.7009,
    this.onRecenterGps,
    this.hasBottomCard = false,
  });

  @override
  State<JobRadarMapWidget> createState() => _JobRadarMapWidgetState();
}

class _JobRadarMapWidgetState extends State<JobRadarMapWidget> with SingleTickerProviderStateMixin {
  late final MapController _mapController;
  late final AnimationController _pulseController;
  late final Animation<double> _pulseAnimation;

  @override
  void initState() {
    super.initState();
    _mapController = MapController();
    _pulseController = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 2),
    )..repeat(reverse: true);

    _pulseAnimation = Tween<double>(begin: 0.8, end: 1.2).animate(
      CurvedAnimation(parent: _pulseController, curve: Curves.easeInOut),
    );
  }

  @override
  void dispose() {
    _pulseController.dispose();
    _mapController.dispose();
    super.dispose();
  }

  @override
  void didUpdateWidget(covariant JobRadarMapWidget oldWidget) {
    super.didUpdateWidget(oldWidget);
    // Khi chọn ca làm việc mới -> tự động căn chỉnh camera bao quát cả vị trí Worker và Ca làm việc
    if (widget.selectedShift != null && widget.selectedShift?.shiftId != oldWidget.selectedShift?.shiftId) {
      WidgetsBinding.instance.addPostFrameCallback((_) => _fitSelectedRoute());
    } else if (oldWidget.userLat != widget.userLat || oldWidget.userLng != widget.userLng) {
      // Khi GPS cập nhật vị trí mới từ thiết bị và chưa chọn ca nào -> chỉ lia camera, không gọi lại GPS loop
      if (widget.selectedShift == null) {
        _mapController.move(LatLng(widget.userLat, widget.userLng), 13.0);
      }
    }
  }

  void _recenter() {
    _mapController.move(LatLng(widget.userLat, widget.userLng), 13.0);
    widget.onRecenterGps?.call();
  }

  void _fitSelectedRoute() {
    if (widget.selectedShift == null) return;
    final userPos = LatLng(widget.userLat, widget.userLng);
    final shiftPos = LatLng(widget.selectedShift!.locationLat, widget.selectedShift!.locationLng);
    try {
      _mapController.fitCamera(
        CameraFit.bounds(
          bounds: LatLngBounds.fromPoints([userPos, shiftPos]),
          padding: const EdgeInsets.only(top: 80, bottom: 120, left: 60, right: 60),
        ),
      );
    } catch (_) {
      _mapController.move(shiftPos, 14.0);
    }
  }

  @override
  Widget build(BuildContext context) {
    final cani = Theme.of(context).cani;
    final userPos = LatLng(widget.userLat, widget.userLng);

    return Stack(
      children: [
        // 1. Bản đồ vệ tinh/đường phố CartoDB Voyager CDN phân giải toàn cầu (thay thế OSM bị lỗi DNS)
        FlutterMap(
          mapController: _mapController,
          options: MapOptions(
            initialCenter: userPos,
            initialZoom: 13.0,
            minZoom: 8.0,
            maxZoom: 19.0,
          ),
          children: [
            // Lớp gạch bản đồ Google Maps tiếng Việt chuẩn CDN (fallback CartoDB Voyager CDN, thay thế OSM bị lỗi DNS tại VN)
            TileLayer(
              urlTemplate: 'https://mt{s}.google.com/vt/lyrs=m&x={x}&y={y}&z={z}&hl=vi',
              subdomains: const ['0', '1', '2', '3'],
              fallbackUrl: 'https://a.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}@2x.png',
              maxZoom: 20,
              errorTileCallback: (tile, error, stackTrace) {
                // Fallback im lặng khi mất mạng
              },
            ),

            // Lớp vòng tròn quét bán kính tìm việc
            CircleLayer(
              circles: [
                  CircleMarker(
                    point: userPos,
                    radius: widget.maxRadiusKm * 1000, // Đổi sang mét
                    useRadiusInMeter: true,
                    color: (widget.isOnline ? cani.brand : Colors.grey).withValues(alpha: 0.10),
                    borderColor: (widget.isOnline ? cani.brand : Colors.grey).withValues(alpha: 0.5),
                    borderStrokeWidth: 1.8,
                  ),
              ],
            ),

            // Lớp vẽ đường lộ trình dẫn đường khi chọn ca làm việc (Route Polyline)
            if (widget.selectedShift != null)
              PolylineLayer(
                polylines: [
                  // Đường viền đổ bóng nổi bật
                  Polyline(
                    points: [
                      userPos,
                      LatLng(widget.selectedShift!.locationLat, widget.selectedShift!.locationLng),
                    ],
                      strokeWidth: 6.0,
                      color: cani.brand.withValues(alpha: 0.3),
                      strokeCap: StrokeCap.round,
                      strokeJoin: StrokeJoin.round,
                    ),
                    // Đường lộ trình chính
                    Polyline(
                      points: [
                        userPos,
                        LatLng(widget.selectedShift!.locationLat, widget.selectedShift!.locationLng),
                      ],
                      strokeWidth: 3.5,
                      color: cani.brand,
                    strokeCap: StrokeCap.round,
                    strokeJoin: StrokeJoin.round,
                  ),
                ],
              ),

            // Lớp Marker: Vị trí của Worker & Các ca làm việc xung quanh
            MarkerLayer(
              markers: [
                // Ghim vị trí của Worker (Tâm phát sóng)
                Marker(
                  point: userPos,
                  width: 60,
                  height: 60,
                  child: AnimatedBuilder(
                    animation: _pulseAnimation,
                    builder: (context, child) {
                      return Transform.scale(
                        scale: _pulseAnimation.value,
                        child: child,
                      );
                    },
                    child: Stack(
                      alignment: Alignment.center,
                      children: [
                          Container(
                            width: 44,
                            height: 44,
                            decoration: BoxDecoration(
                              shape: BoxShape.circle,
                              color: (widget.isOnline ? cani.brand : Colors.grey).withValues(alpha: 0.25),
                            ),
                          ),
                          Container(
                            width: 24,
                            height: 24,
                            decoration: BoxDecoration(
                              shape: BoxShape.circle,
                              color: widget.isOnline ? cani.brand : Colors.grey.shade700,
                            border: Border.all(color: Colors.white, width: 3),
                            boxShadow: [
                              BoxShadow(
                                color: Colors.black.withValues(alpha: 0.2),
                                blurRadius: 6,
                                offset: const Offset(0, 2),
                              ),
                            ],
                          ),
                          child: const Icon(Icons.navigation, color: Colors.white, size: 12),
                        ),
                      ],
                    ),
                  ),
                ),

                // Ghim từng Ca làm việc (Job Wage Pins)
                ...widget.shifts.map((shift) {
                  final isSelected = widget.selectedShift?.shiftId == shift.shiftId;
                  final shiftPos = LatLng(shift.locationLat, shift.locationLng);

                  return Marker(
                    point: shiftPos,
                    width: 72,
                    height: 62,
                    child: GestureDetector(
                      onTap: () => widget.onSelectShift(shift),
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          // Badge giá lương / ca
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2.5),
                            decoration: BoxDecoration(
                              color: isSelected ? cani.textMain : Colors.white,
                              borderRadius: BorderRadius.circular(12),
                              border: Border.all(
                                color: isSelected ? cani.accent : const Color(0xFFCBD5E1),
                                width: isSelected ? 2 : 1,
                              ),
                              boxShadow: [
                                BoxShadow(
                                  color: Colors.black.withValues(alpha: 0.15),
                                  blurRadius: 5,
                                  offset: const Offset(0, 2),
                                ),
                              ],
                            ),
                            child: Text(
                              '${(shift.hourlyWage / 1000).toInt()}k/h',
                              style: TextStyle(
                                fontSize: 10.5,
                                fontWeight: FontWeight.w800,
                                color: isSelected ? Colors.white : cani.textMain,
                              ),
                            ),
                          ),
                          // Icon Pin chỉ vị trí
                          Icon(
                            Icons.location_on_rounded,
                            size: isSelected ? 36 : 28,
                            color: isSelected ? cani.brand : const Color(0xFF2563EB),
                            shadows: [
                              Shadow(color: Colors.black.withValues(alpha: 0.25), blurRadius: 4),
                            ],
                          ),
                        ],
                      ),
                    ),
                  );
                }),
              ],
            ),
          ],
        ),

        // 2. Cụm nút Recenter & Zoom gọn gàng ở cạnh phải (tự nâng vị trí khi có Bottom Card)
        Positioned(
          right: 12,
          bottom: widget.hasBottomCard ? 190 : 16,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(
                decoration: BoxDecoration(
                  color: cani.surface,
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: cani.border),
                  boxShadow: [
                    BoxShadow(
                      color: Colors.black.withValues(alpha: 0.10),
                      blurRadius: 8,
                      offset: const Offset(0, 2),
                    ),
                  ],
                ),
                child: IconButton(
                  constraints: const BoxConstraints.tightFor(width: 38, height: 38),
                  padding: EdgeInsets.zero,
                  icon: Icon(Icons.my_location_rounded, size: 19, color: cani.brand),
                  tooltip: 'Về vị trí GPS của tôi',
                  onPressed: _recenter,
                ),
              ),
              const SizedBox(height: 8),
              Container(
                decoration: BoxDecoration(
                  color: cani.surface,
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: cani.border),
                  boxShadow: [
                    BoxShadow(
                      color: Colors.black.withValues(alpha: 0.10),
                      blurRadius: 8,
                      offset: const Offset(0, 2),
                    ),
                  ],
                ),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    IconButton(
                      constraints: const BoxConstraints.tightFor(width: 38, height: 36),
                      padding: EdgeInsets.zero,
                      icon: Icon(Icons.add_rounded, size: 19, color: cani.textMain),
                      onPressed: () {
                        final zoom = _mapController.camera.zoom + 1;
                        _mapController.move(_mapController.camera.center, zoom);
                      },
                    ),
                    Container(width: 24, height: 1, color: cani.border),
                    IconButton(
                      constraints: const BoxConstraints.tightFor(width: 38, height: 36),
                      padding: EdgeInsets.zero,
                      icon: Icon(Icons.remove_rounded, size: 19, color: cani.textMain),
                      onPressed: () {
                        final zoom = _mapController.camera.zoom - 1;
                        _mapController.move(_mapController.camera.center, zoom);
                      },
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),

        // 3. Banner thông tin lộ trình dẫn đường khi chọn ca làm việc (Không bị đè bởi chip nào)
        if (widget.selectedShift != null)
          Positioned(
            top: 12,
            left: 12,
            right: 12,
            child: Material(
              color: Colors.transparent,
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 9),
                decoration: BoxDecoration(
                  color: cani.surface,
                  borderRadius: BorderRadius.circular(14),
                  boxShadow: [
                    BoxShadow(
                      color: Colors.black.withValues(alpha: 0.10),
                      blurRadius: 10,
                      offset: const Offset(0, 3),
                    ),
                  ],
                  border: Border.all(color: cani.brand.withValues(alpha: 0.35)),
                ),
                child: Row(
                  children: [
                    Container(
                      padding: const EdgeInsets.all(7),
                      decoration: BoxDecoration(
                        color: cani.brandSoft,
                        borderRadius: BorderRadius.circular(10),
                      ),
                      child: Icon(Icons.route_rounded, color: cani.brand, size: 18),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Text(
                            widget.selectedShift!.eventName,
                            style: TextStyle(fontWeight: FontWeight.w800, fontSize: 12.5, color: cani.textMain),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                          const SizedBox(height: 1),
                          Text(
                            'Lộ trình: ${widget.selectedShift!.distanceKm.toStringAsFixed(1)} km (~${((widget.selectedShift!.distanceKm / 25.0) * 60).round().clamp(5, 120)} phút đi xe máy)',
                            style: TextStyle(fontSize: 11, color: cani.brand, fontWeight: FontWeight.w600),
                          ),
                        ],
                      ),
                    ),
                    IconButton(
                      visualDensity: VisualDensity.compact,
                      icon: Icon(Icons.center_focus_strong_rounded, size: 19, color: cani.textMuted),
                      tooltip: 'Căn chỉnh toàn bộ lộ trình',
                      onPressed: _fitSelectedRoute,
                    ),
                  ],
                ),
              ),
            ),
          ),
      ],
    );
  }
}
