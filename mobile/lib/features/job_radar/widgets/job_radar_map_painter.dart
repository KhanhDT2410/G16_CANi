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

  void _recenter() {
    _mapController.move(LatLng(widget.userLat, widget.userLng), 13.0);
    widget.onRecenterGps?.call();
  }

  @override
  Widget build(BuildContext context) {
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
            // Lớp gạch bản đồ CartoDB Voyager mượt mà, không bị chặn bởi nhà mạng Việt Nam
            TileLayer(
              urlTemplate: 'https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}.png',
              subdomains: const ['a', 'b', 'c', 'd'],
              userAgentPackageName: 'com.cani.cani',
              maxZoom: 19,
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
                  color: (widget.isOnline ? const Color(0xFF0077FF) : Colors.grey).withValues(alpha: 0.10),
                  borderColor: (widget.isOnline ? const Color(0xFF0077FF) : Colors.grey).withValues(alpha: 0.5),
                  borderStrokeWidth: 1.8,
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
                            color: (widget.isOnline ? const Color(0xFF0077FF) : Colors.grey).withValues(alpha: 0.25),
                          ),
                        ),
                        Container(
                          width: 24,
                          height: 24,
                          decoration: BoxDecoration(
                            shape: BoxShape.circle,
                            color: widget.isOnline ? const Color(0xFF0066FF) : Colors.grey.shade700,
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
                              color: isSelected ? const Color(0xFF0B1B3D) : Colors.white,
                              borderRadius: BorderRadius.circular(12),
                              border: Border.all(
                                color: isSelected ? const Color(0xFF00D2FF) : const Color(0xFFCBD5E1),
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
                                color: isSelected ? Colors.white : const Color(0xFF0B1B3D),
                              ),
                            ),
                          ),
                          // Icon Pin chỉ vị trí
                          Icon(
                            Icons.location_on_rounded,
                            size: isSelected ? 36 : 28,
                            color: isSelected ? const Color(0xFF0066FF) : const Color(0xFF2563EB),
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

        // 2. Cột nút Zoom & Recenter tiện lợi ở góc phải
        Positioned(
          right: 14,
          bottom: 90, // Cách đáy hợp lý để không che sliding card
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              FloatingActionButton.small(
                heroTag: 'map_recenter',
                backgroundColor: Colors.white,
                foregroundColor: const Color(0xFF0066FF),
                elevation: 3,
                onPressed: _recenter,
                tooltip: 'Về vị trí GPS của tôi',
                child: const Icon(Icons.my_location),
              ),
              const SizedBox(height: 8),
              FloatingActionButton.small(
                heroTag: 'map_zoom_in',
                backgroundColor: Colors.white,
                foregroundColor: const Color(0xFF1E293B),
                elevation: 3,
                onPressed: () {
                  final zoom = _mapController.camera.zoom + 1;
                  _mapController.move(_mapController.camera.center, zoom);
                },
                child: const Icon(Icons.add),
              ),
              const SizedBox(height: 8),
              FloatingActionButton.small(
                heroTag: 'map_zoom_out',
                backgroundColor: Colors.white,
                foregroundColor: const Color(0xFF1E293B),
                elevation: 3,
                onPressed: () {
                  final zoom = _mapController.camera.zoom - 1;
                  _mapController.move(_mapController.camera.center, zoom);
                },
                child: const Icon(Icons.remove),
              ),
            ],
          ),
        ),

        // 3. Thông báo trạng thái khi Tắt chế độ tìm việc (Offline) - Đã bọc chống tràn
        if (!widget.isOnline)
          Positioned(
            top: 10,
            left: 14,
            right: 14,
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
              decoration: BoxDecoration(
                color: const Color(0xFF0B1B3D).withValues(alpha: 0.90),
                borderRadius: BorderRadius.circular(20),
                boxShadow: [
                  BoxShadow(
                    color: Colors.black.withValues(alpha: 0.15),
                    blurRadius: 8,
                    offset: const Offset(0, 3),
                  ),
                ],
              ),
              child: const Row(
                children: [
                  Icon(Icons.visibility_off_outlined, color: Color(0xFFF59E0B), size: 16),
                  SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      'Đang tắt chế độ tìm việc. Gạt công tắc trên để bắt đầu.',
                      style: TextStyle(
                        color: Colors.white,
                        fontSize: 11,
                        fontWeight: FontWeight.w600,
                      ),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                ],
              ),
            ),
          ),
      ],
    );
  }
}
