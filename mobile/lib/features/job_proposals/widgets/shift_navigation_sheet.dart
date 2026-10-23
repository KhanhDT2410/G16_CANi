import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'package:geolocator/geolocator.dart';
import 'package:url_launcher/url_launcher.dart';

import '../models/proposal_models.dart';

class ShiftNavigationSheet extends StatefulWidget {
  final ShiftAssignmentItem assignment;

  const ShiftNavigationSheet({super.key, required this.assignment});

  @override
  State<ShiftNavigationSheet> createState() => _ShiftNavigationSheetState();
}

class _ShiftNavigationSheetState extends State<ShiftNavigationSheet> {
  Position? _currentPosition;
  bool _isLoadingGps = true;
  String? _gpsError;
  double? _distanceKm;
  int? _estimatedMinutes;

  @override
  void initState() {
    super.initState();
    _fetchWorkerLocation();
  }

  Future<void> _fetchWorkerLocation() async {
    setState(() {
      _isLoadingGps = true;
      _gpsError = null;
    });

    try {
      bool serviceEnabled = await Geolocator.isLocationServiceEnabled();
      if (!serviceEnabled) {
        setState(() {
          _gpsError = 'Dịch vụ định vị GPS trên máy đang tắt.';
          _isLoadingGps = false;
        });
        return;
      }

      LocationPermission permission = await Geolocator.checkPermission();
      if (permission == LocationPermission.denied) {
        permission = await Geolocator.requestPermission();
        if (permission == LocationPermission.denied) {
          setState(() {
            _gpsError = 'Chưa cấp quyền truy cập vị trí cho ứng dụng.';
            _isLoadingGps = false;
          });
          return;
        }
      }

      final pos = await Geolocator.getCurrentPosition(
        locationSettings: const LocationSettings(
          accuracy: LocationAccuracy.high,
          timeLimit: Duration(seconds: 10),
        ),
      );

      _currentPosition = pos;

      // Tính khoảng cách đến địa điểm sự kiện
      final destLat = widget.assignment.venueLat ?? 10.7769;
      final destLng = widget.assignment.venueLng ?? 106.7009;

      final distMeters = Geolocator.distanceBetween(
        pos.latitude,
        pos.longitude,
        destLat,
        destLng,
      );

      _distanceKm = distMeters / 1000.0;
      // Ước tính di chuyển xe máy nội đô (30 km/h)
      _estimatedMinutes = math.max(5, ((_distanceKm! / 25.0) * 60).round());

      setState(() {
        _isLoadingGps = false;
      });
    } catch (e) {
      setState(() {
        _gpsError = 'Không bắt được GPS: $e';
        _isLoadingGps = false;
      });
    }
  }

  Future<void> _launchGoogleMapsNavigation() async {
    final destLat = widget.assignment.venueLat ?? 10.7769;
    final destLng = widget.assignment.venueLng ?? 106.7009;

    Uri googleMapsUri;
    if (_currentPosition != null) {
      googleMapsUri = Uri.parse(
        'https://www.google.com/maps/dir/?api=1&origin=${_currentPosition!.latitude},${_currentPosition!.longitude}&destination=$destLat,$destLng&travelmode=driving',
      );
    } else {
      googleMapsUri = Uri.parse(
        'https://www.google.com/maps/search/?api=1&query=$destLat,$destLng',
      );
    }

    try {
      final launched = await launchUrl(
        googleMapsUri,
        mode: LaunchMode.externalApplication,
      );
      if (!launched) {
        await launchUrl(googleMapsUri, mode: LaunchMode.platformDefault);
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Không thể mở Google Maps: $e'),
            backgroundColor: Colors.red,
          ),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final a = widget.assignment;
    final destLat = a.venueLat ?? 10.7769;
    final destLng = a.venueLng ?? 106.7009;

    return Container(
      decoration: const BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      padding: const EdgeInsets.fromLTRB(20, 12, 20, 24),
      child: SafeArea(
        top: false,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Thanh gạt
            Center(
              child: Container(
                width: 44,
                height: 4,
                decoration: BoxDecoration(
                  color: Colors.grey.shade300,
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
            ),
            const SizedBox(height: 16),

            // Tiêu đề
            Row(
              children: [
                Container(
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(
                    color: Colors.blue.shade50,
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Icon(Icons.directions, color: Colors.blue.shade700, size: 24),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text(
                        'Lộ Trình Chỉ Đường',
                        style: TextStyle(fontWeight: FontWeight.bold, fontSize: 18),
                      ),
                      Text(
                        a.eventName,
                        style: TextStyle(fontSize: 13, color: Colors.grey.shade600, fontWeight: FontWeight.w500),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ],
                  ),
                ),
                IconButton(
                  onPressed: () => Navigator.pop(context),
                  icon: const Icon(Icons.close),
                ),
              ],
            ),
            const SizedBox(height: 18),

            // Khung Lộ trình: Điểm đi -> Điểm đến
            Container(
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                color: Colors.grey.shade50,
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: Colors.grey.shade200),
              ),
              child: Column(
                children: [
                  // Điểm xuất phát
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Column(
                        children: [
                          Icon(Icons.my_location, color: Colors.blue.shade600, size: 20),
                          Container(width: 2, height: 32, color: Colors.blue.shade200),
                        ],
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Text(
                              'Vị trí của bạn (GPS hiện tại)',
                              style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: Colors.blue),
                            ),
                            const SizedBox(height: 2),
                            if (_isLoadingGps)
                              const Row(
                                children: [
                                  SizedBox(width: 12, height: 12, child: CircularProgressIndicator(strokeWidth: 2)),
                                  SizedBox(width: 6),
                                  Text('Đang định vị GPS...', style: TextStyle(fontSize: 12, color: Colors.grey)),
                                ],
                              )
                            else if (_currentPosition != null)
                              Text(
                                'Lat: ${_currentPosition!.latitude.toStringAsFixed(4)}, Lng: ${_currentPosition!.longitude.toStringAsFixed(4)}',
                                style: const TextStyle(fontSize: 12, color: Colors.black87),
                              )
                            else
                              Text(
                                _gpsError ?? 'Không xác định được vị trí GPS',
                                style: TextStyle(fontSize: 12, color: Colors.red.shade700),
                              ),
                          ],
                        ),
                      ),
                    ],
                  ),

                  // Điểm đến
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Icon(Icons.location_on, color: Colors.red.shade600, size: 20),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Text(
                              'Địa điểm làm việc',
                              style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: Colors.red),
                            ),
                            const SizedBox(height: 2),
                            Text(
                              a.venueAddress,
                              style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600),
                            ),
                            const SizedBox(height: 2),
                            Text(
                              'Tọa độ: $destLat, $destLng',
                              style: TextStyle(fontSize: 11, color: Colors.grey.shade600),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
            const SizedBox(height: 16),

            // Thẻ Thông số: Khoảng cách & Thời gian
            if (_distanceKm != null)
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    colors: [Colors.teal.shade50, Colors.blue.shade50],
                  ),
                  borderRadius: BorderRadius.circular(14),
                  border: Border.all(color: Colors.teal.shade200),
                ),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceAround,
                  children: [
                    Row(
                      children: [
                        Icon(Icons.route, color: Colors.teal.shade800, size: 20),
                        const SizedBox(width: 8),
                        Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Text('Khoảng cách', style: TextStyle(fontSize: 11, color: Colors.grey)),
                            Text(
                              '${_distanceKm!.toStringAsFixed(1)} km',
                              style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: Colors.teal.shade900),
                            ),
                          ],
                        ),
                      ],
                    ),
                    Container(width: 1, height: 30, color: Colors.teal.shade200),
                    Row(
                      children: [
                        Icon(Icons.two_wheeler, color: Colors.blue.shade800, size: 20),
                        const SizedBox(width: 8),
                        Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Text('Ước tính di chuyển', style: TextStyle(fontSize: 11, color: Colors.grey)),
                            Text(
                              '~$_estimatedMinutes phút',
                              style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: Colors.blue.shade900),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ],
                ),
              ),

            const SizedBox(height: 20),

            // Nút Mở Chỉ Đường Google Maps Ngoại Ứng Dụng
            FilledButton.icon(
              onPressed: _launchGoogleMapsNavigation,
              style: FilledButton.styleFrom(
                backgroundColor: Colors.blue.shade700,
                padding: const EdgeInsets.symmetric(vertical: 14),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
              ),
              icon: const Icon(Icons.navigation, color: Colors.white),
              label: const Text(
                'Mở Chỉ Đường Trực Tiếp Trên Google Maps',
                style: TextStyle(fontSize: 15, fontWeight: FontWeight.bold, color: Colors.white),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
