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
  final Widget? topOverlay;
  final Widget? bottomOverlay;

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
    this.topOverlay,
    this.bottomOverlay,
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
    if (widget.selectedShift != null && widget.selectedShift?.shiftId != oldWidget.selectedShift?.shiftId) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) _fitSelectedRoute();
      });
    } else if (oldWidget.userLat != widget.userLat || oldWidget.userLng != widget.userLng) {
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
          padding: EdgeInsets.only(
            top: 88,
            bottom: widget.bottomOverlay != null ? 210 : 80,
            left: 56,
            right: 56,
          ),
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

    return LayoutBuilder(
      builder: (context, constraints) {
        final maxBottomCardHeight = (constraints.maxHeight - 144).clamp(100.0, constraints.maxHeight);
        return Stack(
          children: [
        FlutterMap(
          mapController: _mapController,
          options: MapOptions(
            initialCenter: userPos,
            initialZoom: 13.0,
            minZoom: 8.0,
            maxZoom: 19.0,
          ),
          children: [
            TileLayer(
              urlTemplate: 'https://mt{s}.google.com/vt/lyrs=m&x={x}&y={y}&z={z}&hl=vi',
              subdomains: const ['0', '1', '2', '3'],
              fallbackUrl: 'https://a.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}@2x.png',
              maxZoom: 20,
              errorTileCallback: (tile, error, stackTrace) {},
            ),
            CircleLayer(
              circles: [
                CircleMarker(
                  point: userPos,
                  radius: widget.maxRadiusKm * 1000,
                  useRadiusInMeter: true,
                  color: (widget.isOnline ? cani.brand : Colors.grey).withValues(alpha: 0.10),
                  borderColor: (widget.isOnline ? cani.brand : Colors.grey).withValues(alpha: 0.5),
                  borderStrokeWidth: 1.8,
                ),
              ],
            ),
            if (widget.selectedShift != null)
              PolylineLayer(
                polylines: [
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
            MarkerLayer(
              markers: [
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
                ...widget.shifts.map((shift) {
                  final isSelected = widget.selectedShift?.shiftId == shift.shiftId;
                  final shiftPos = LatLng(shift.locationLat, shift.locationLng);

                  return Marker(
                    point: shiftPos,
                    width: 88,
                    height: 68,
                    child: GestureDetector(
                      onTap: () => widget.onSelectShift(shift),
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
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
                            child: FittedBox(
                              fit: BoxFit.scaleDown,
                              child: Text(
                                '${(shift.hourlyWage / 1000).toInt()}k/h',
                                maxLines: 1,
                                softWrap: false,
                                style: TextStyle(
                                  fontSize: 10.5,
                                  fontWeight: FontWeight.w800,
                                  color: isSelected ? Colors.white : cani.textMain,
                                ),
                              ),
                            ),
                          ),
                          Icon(
                            Icons.location_on_rounded,
                            size: isSelected ? 34 : 26,
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

        // Vùng thông báo gọn / lộ trình ở mép trên bản đồ
        if (widget.topOverlay != null || widget.selectedShift != null)
          Positioned(
            top: 10,
            left: 10,
            right: 10,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                if (widget.topOverlay != null) widget.topOverlay!,
                if (widget.topOverlay != null && widget.selectedShift != null) const SizedBox(height: 6),
                if (widget.selectedShift != null) _buildSelectedRouteBanner(cani),
              ],
            ),
          ),

        // Cụm nút định vị/zoom đặt ngay phía trên thẻ đáy để không bao giờ bị che khi thẻ đổi chiều cao
        Positioned(
          left: 10,
          right: 10,
          bottom: 10,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              _buildMapControls(cani),
              if (widget.bottomOverlay != null) ...[
                const SizedBox(height: 6),
                ConstrainedBox(
                  constraints: BoxConstraints(maxHeight: maxBottomCardHeight),
                  child: SingleChildScrollView(
                    physics: const ClampingScrollPhysics(),
                    child: SizedBox(
                      width: double.infinity,
                      child: widget.bottomOverlay!,
                    ),
                  ),
                ),
              ],
            ],
          ),
        ),
      ],
    );
      },
    );
  }

  Widget _buildMapControls(CaniTokens cani) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(
          decoration: BoxDecoration(
            color: cani.surface,
            borderRadius: BorderRadius.circular(12),
            border: Border.all(color: cani.border),
            boxShadow: [
              BoxShadow(
                color: Colors.black.withValues(alpha: 0.12),
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
        const SizedBox(height: 6),
        Container(
          decoration: BoxDecoration(
            color: cani.surface,
            borderRadius: BorderRadius.circular(12),
            border: Border.all(color: cani.border),
            boxShadow: [
              BoxShadow(
                color: Colors.black.withValues(alpha: 0.12),
                blurRadius: 8,
                offset: const Offset(0, 2),
              ),
            ],
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              IconButton(
                constraints: const BoxConstraints.tightFor(width: 38, height: 34),
                padding: EdgeInsets.zero,
                icon: Icon(Icons.add_rounded, size: 19, color: cani.textMain),
                tooltip: 'Phóng to bản đồ',
                onPressed: () {
                  final zoom = _mapController.camera.zoom + 1;
                  _mapController.move(_mapController.camera.center, zoom);
                },
              ),
              Container(width: 24, height: 1, color: cani.border),
              IconButton(
                constraints: const BoxConstraints.tightFor(width: 38, height: 34),
                padding: EdgeInsets.zero,
                icon: Icon(Icons.remove_rounded, size: 19, color: cani.textMain),
                tooltip: 'Thu nhỏ bản đồ',
                onPressed: () {
                  final zoom = _mapController.camera.zoom - 1;
                  _mapController.move(_mapController.camera.center, zoom);
                },
              ),
            ],
          ),
        ),
      ],
    );
  }

  Widget _buildSelectedRouteBanner(CaniTokens cani) {
    final shift = widget.selectedShift!;
    final estMins = ((shift.distanceKm / 25.0) * 60).round().clamp(5, 120);
    return Material(
      color: Colors.transparent,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
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
              padding: const EdgeInsets.all(6),
              decoration: BoxDecoration(
                color: cani.brandSoft,
                borderRadius: BorderRadius.circular(10),
              ),
              child: Icon(Icons.route_rounded, color: cani.brand, size: 17),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    shift.eventName,
                    style: TextStyle(fontWeight: FontWeight.w800, fontSize: 12.5, color: cani.textMain),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  Text(
                    'Lộ trình: ${shift.distanceKm.toStringAsFixed(1)} km (~$estMins phút đi xe máy)',
                    style: TextStyle(fontSize: 11, color: cani.brand, fontWeight: FontWeight.w600),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
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
    );
  }
}
