import 'dart:async';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:geolocator/geolocator.dart';
import 'package:latlong2/latlong.dart';

class _DistrictHub {
  final String fullName;
  final String shortName;
  final double lat;
  final double lng;
  const _DistrictHub(this.fullName, this.shortName, this.lat, this.lng);
}

class _AddressSuggestion {
  final String title;
  final String fullAddress;
  final double lat;
  final double lng;
  const _AddressSuggestion({
    required this.title,
    required this.fullAddress,
    required this.lat,
    required this.lng,
  });
}

class JobPreferencesCard extends StatefulWidget {
  final double maxDistanceKm;
  final ValueChanged<double> onDistanceChanged;
  final double minWage; // Luôn lưu trữ theo đ/giờ
  final ValueChanged<double> onWageChanged;
  final bool isWagePerShift; // false: Theo giờ, true: Theo ca (4h)
  final ValueChanged<bool> onWageUnitChanged;
  final String baseAddress;
  final double? baseLat;
  final double? baseLng;
  final void Function(String address, double? lat, double? lng) onLocationChanged;

  const JobPreferencesCard({
    super.key,
    required this.maxDistanceKm,
    required this.onDistanceChanged,
    required this.minWage,
    required this.onWageChanged,
    required this.isWagePerShift,
    required this.onWageUnitChanged,
    required this.baseAddress,
    this.baseLat,
    this.baseLng,
    required this.onLocationChanged,
  });

  @override
  State<JobPreferencesCard> createState() => _JobPreferencesCardState();
}

class _JobPreferencesCardState extends State<JobPreferencesCard> {
  late TextEditingController _wageController;
  late TextEditingController _addressController;
  late final MapController _mapController;
  bool _isGettingGps = false;
  bool _isSearchingAddress = false;
  List<_AddressSuggestion> _suggestions = [];
  Timer? _debounceTimer;

  final List<double> _distancePresets = const [5, 10, 15, 20, 30];
  final List<double> _hourlyPresets = const [30000, 35000, 50000, 75000, 100000];
  final List<double> _shiftPresets = const [120000, 150000, 200000, 300000, 400000];

  // Danh sách các quận lớn: Hà Nội đưa lên trước, tiếp đến TP. Hồ Chí Minh
  final List<_DistrictHub> _districts = const [
    // Hà Nội
    _DistrictHub('Quận Cầu Giấy, Hà Nội', 'Cầu Giấy', 21.0362, 105.7906),
    _DistrictHub('Quận Ba Đình, Hà Nội', 'Ba Đình', 21.0341, 105.8244),
    _DistrictHub('Quận Đống Đa, Hà Nội', 'Đống Đa', 21.0181, 105.8256),
    _DistrictHub('Quận Hoàn Kiếm, Hà Nội', 'Hoàn Kiếm', 21.0285, 105.8542),
    _DistrictHub('Quận Nam Từ Liêm, Hà Nội', 'Nam Từ Liêm', 21.0173, 105.7640),
    _DistrictHub('Quận Thanh Xuân, Hà Nội', 'Thanh Xuân', 20.9937, 105.8122),
    _DistrictHub('Quận Hai Bà Trưng, Hà Nội', 'Hai Bà Trưng', 21.0063, 105.8504),
    _DistrictHub('Quận Tây Hồ, Hà Nội', 'Tây Hồ', 21.0706, 105.8202),
    _DistrictHub('Quận Hà Đông, Hà Nội', 'Hà Đông', 20.9723, 105.7766),
    _DistrictHub('Quận Bắc Từ Liêm, Hà Nội', 'Bắc Từ Liêm', 21.0628, 105.7601),
    _DistrictHub('Huyện Đông Anh, Hà Nội', 'Đông Anh', 21.1389, 105.8473),
    _DistrictHub('Huyện Gia Lâm, Hà Nội', 'Gia Lâm', 21.0235, 105.9395),
    _DistrictHub('Huyện Thạch Thất (Hòa Lạc), Hà Nội', 'Hòa Lạc', 21.0128, 105.5256),

    // TP. Hồ Chí Minh
    _DistrictHub('Quận 1, TP. Hồ Chí Minh', 'Quận 1', 10.7769, 106.7009),
    _DistrictHub('Quận 3, TP. Hồ Chí Minh', 'Quận 3', 10.7844, 106.6844),
    _DistrictHub('Quận 7, TP. Hồ Chí Minh', 'Quận 7', 10.7303, 106.7196),
    _DistrictHub('Quận 10, TP. Hồ Chí Minh', 'Quận 10', 10.7719, 106.6672),
    _DistrictHub('TP. Thủ Đức, TP. Hồ Chí Minh', 'Thủ Đức', 10.8494, 106.7717),
    _DistrictHub('Quận Bình Thạnh, TP. Hồ Chí Minh', 'Bình Thạnh', 10.8106, 106.7091),
    _DistrictHub('Quận Tân Bình, TP. Hồ Chí Minh', 'Tân Bình', 10.8015, 106.6534),
    _DistrictHub('Quận Gò Vấp, TP. Hồ Chí Minh', 'Gò Vấp', 10.8388, 106.6653),
    _DistrictHub('Quận Phú Nhuận, TP. Hồ Chí Minh', 'Phú Nhuận', 10.7995, 106.6784),
  ];

  LatLng get _currentCenter {
    if (widget.baseLat != null && widget.baseLng != null) {
      return LatLng(widget.baseLat!, widget.baseLng!);
    }
    // Mặc định: Quận Hoàn Kiếm, Hà Nội
    return const LatLng(21.0285, 105.8542);
  }

  @override
  void initState() {
    super.initState();
    _mapController = MapController();
    _wageController = TextEditingController();
    _addressController = TextEditingController(text: widget.baseAddress);
    _syncControllerText();

    // Tự động phân giải địa chỉ cụ thể nếu trước đó chỉ lưu chuỗi tọa độ kinh/vĩ độ
    if (widget.baseLat != null && widget.baseLng != null) {
      final addr = widget.baseAddress.trim();
      final isRawCoords = addr.isEmpty || RegExp(r'^-?\d+(\.\d+)?[,\s]+-?\d+(\.\d+)?$').hasMatch(addr);
      if (isRawCoords) {
        _resolveFriendlyAddress(widget.baseLat!, widget.baseLng!);
      }
    }
  }

  @override
  void didUpdateWidget(covariant JobPreferencesCard oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.minWage != widget.minWage || oldWidget.isWagePerShift != widget.isWagePerShift) {
      _syncControllerText();
    }
    if (oldWidget.baseAddress != widget.baseAddress && _addressController.text != widget.baseAddress) {
      _addressController.text = widget.baseAddress;
    }
    if (widget.baseLat != null && widget.baseLng != null) {
      if (oldWidget.baseLat != widget.baseLat || oldWidget.baseLng != widget.baseLng) {
        try {
          _mapController.move(LatLng(widget.baseLat!, widget.baseLng!), 13.5);
        } catch (_) {}
      }
    }
  }

  @override
  void dispose() {
    _debounceTimer?.cancel();
    _wageController.dispose();
    _addressController.dispose();
    _mapController.dispose();
    super.dispose();
  }

  void _syncControllerText() {
    final displayAmount = widget.isWagePerShift ? (widget.minWage * 4).round() : widget.minWage.round();
    final formatted = _formatNumber(displayAmount);
    if (_wageController.text != formatted) {
      _wageController.value = TextEditingValue(
        text: formatted,
        selection: TextSelection.collapsed(offset: formatted.length),
      );
    }
  }

  String _formatNumber(num val) {
    return val.toInt().toString().replaceAllMapped(
          RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'),
          (m) => '${m[1]}.',
        );
  }

  void _onWageTextChanged(String raw) {
    final cleanDigits = raw.replaceAll(RegExp(r'[^\d]'), '');
    if (cleanDigits.isEmpty) return;
    final parsed = double.tryParse(cleanDigits) ?? 0.0;
    if (parsed <= 0) return;

    final hourly = widget.isWagePerShift ? (parsed / 4.0) : parsed;
    widget.onWageChanged(hourly);
  }

  Future<void> _resolveFriendlyAddress(double lat, double lng) async {
    final resolved = await _reverseGeocode(lat, lng);
    if (resolved != null && resolved.isNotEmpty && mounted) {
      _addressController.text = resolved;
      widget.onLocationChanged(resolved, lat, lng);
    }
  }

  // Tra cứu địa chỉ thực tế từ tọa độ GPS (Reverse Geocoding)
  Future<String?> _reverseGeocode(double lat, double lng) async {
    try {
      final dio = Dio();
      final res = await dio.get(
        'https://nominatim.openstreetmap.org/reverse',
        queryParameters: {
          'format': 'json',
          'lat': lat,
          'lon': lng,
          'zoom': 18,
          'addressdetails': 1,
        },
        options: Options(
          headers: {
            'Accept-Language': 'vi,en;q=0.8',
            'User-Agent': 'CANiEventStaffingApp/1.0',
          },
          receiveTimeout: const Duration(seconds: 4),
          sendTimeout: const Duration(seconds: 4),
        ),
      );

      if (res.data != null && res.data is Map) {
        final map = res.data as Map<String, dynamic>;
        final address = map['address'] as Map<String, dynamic>?;

        if (address != null) {
          final building = address['building'] ??
              address['amenity'] ??
              address['residential'] ??
              address['office'] ??
              address['commercial'];
          final road = address['road'] ?? address['pedestrian'] ?? address['street'];
          final houseNumber = address['house_number'];
          final suburb = address['suburb'] ?? address['quarter'] ?? address['neighbourhood'] ?? address['village'];
          final district = address['city_district'] ?? address['district'] ?? address['county'];
          final city = address['city'] ?? address['state'] ?? address['province'];

          final parts = <String>[];
          if (building != null && building.toString().isNotEmpty) {
            parts.add(building.toString());
          }
          if (houseNumber != null && road != null) {
            parts.add('$houseNumber $road');
          } else if (road != null) {
            parts.add('$road');
          }
          if (suburb != null) parts.add('$suburb');
          if (district != null) parts.add('$district');
          if (city != null) parts.add('$city');

          if (parts.isNotEmpty) {
            return parts.join(', ');
          }
        }

        if (map['display_name'] != null) {
          return map['display_name'].toString();
        }
      }
    } catch (_) {}
    return null;
  }

  // Tìm kiếm gợi ý địa chỉ tự động khi người dùng gõ
  void _onAddressInputChanged(String query) {
    widget.onLocationChanged(query, widget.baseLat, widget.baseLng);
    _debounceTimer?.cancel();
    if (query.trim().length < 3) {
      if (_suggestions.isNotEmpty) {
        setState(() => _suggestions = []);
      }
      return;
    }

    _debounceTimer = Timer(const Duration(milliseconds: 600), () async {
      setState(() => _isSearchingAddress = true);
      try {
        final dio = Dio();
        final res = await dio.get(
          'https://nominatim.openstreetmap.org/search',
          queryParameters: {
            'format': 'json',
            'q': query.trim(),
            'countrycodes': 'vn',
            'limit': 5,
            'addressdetails': 1,
          },
          options: Options(
            headers: {
              'Accept-Language': 'vi,en;q=0.8',
              'User-Agent': 'CANiEventStaffingApp/1.0',
            },
            receiveTimeout: const Duration(seconds: 4),
          ),
        );

        if (mounted && res.data != null && res.data is List) {
          final list = (res.data as List).map((item) {
            final m = item as Map<String, dynamic>;
            final displayName = m['display_name']?.toString() ?? query;
            final shortTitle = m['name']?.toString() ?? displayName.split(',').first;
            return _AddressSuggestion(
              title: shortTitle,
              fullAddress: displayName,
              lat: double.tryParse(m['lat'].toString()) ?? 0.0,
              lng: double.tryParse(m['lon'].toString()) ?? 0.0,
            );
          }).toList();

          setState(() {
            _suggestions = list;
            _isSearchingAddress = false;
          });
          return;
        }
      } catch (_) {}
      if (mounted) setState(() => _isSearchingAddress = false);
    });
  }

  // Lấy vị trí GPS thực tế từ máy điện thoại và chuyển thành địa chỉ rõ ràng
  Future<void> _captureGpsLocation() async {
    setState(() => _isGettingGps = true);
    try {
      bool serviceEnabled = await Geolocator.isLocationServiceEnabled();
      if (!serviceEnabled) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Vui lòng bật định vị GPS trên thiết bị')),
          );
        }
        return;
      }

      LocationPermission permission = await Geolocator.checkPermission();
      if (permission == LocationPermission.denied) {
        permission = await Geolocator.requestPermission();
        if (permission == LocationPermission.denied) {
          if (mounted) {
            ScaffoldMessenger.of(context).showSnackBar(
              const SnackBar(content: Text('Ứng dụng cần quyền vị trí để xác định tọa độ')),
            );
          }
          return;
        }
      }

      final position = await Geolocator.getCurrentPosition(
        locationSettings: const LocationSettings(
          accuracy: LocationAccuracy.high,
          timeLimit: Duration(seconds: 10),
        ),
      );

      // Tra cứu địa chỉ chi tiết (Tên đường, phường, quận)
      final resolvedAddress = await _reverseGeocode(position.latitude, position.longitude);
      final finalAddress = (resolvedAddress != null && resolvedAddress.isNotEmpty)
          ? resolvedAddress
          : 'Khu vực gần vị trí hiện tại';

      _addressController.text = finalAddress;
      widget.onLocationChanged(finalAddress, position.latitude, position.longitude);
      try {
        _mapController.move(LatLng(position.latitude, position.longitude), 14.0);
      } catch (_) {}

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Đã nhận diện: $finalAddress'),
            backgroundColor: const Color(0xFF0D9488),
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Không thể lấy vị trí hiện tại: $e')),
        );
      }
    } finally {
      if (mounted) setState(() => _isGettingGps = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final currentDisplayWage = widget.isWagePerShift ? (widget.minWage * 4).round() : widget.minWage.round();
    final currentPresets = widget.isWagePerShift ? _shiftPresets : _hourlyPresets;

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: const Color(0xFFE2E8F0)),
        boxShadow: const [
          BoxShadow(
            color: Color(0x06000000),
            blurRadius: 10,
            offset: Offset(0, 4),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // ---------------------------------------------------------------
          // 1. TIÊU ĐỀ KHỐI
          // ---------------------------------------------------------------
          Row(
            children: [
              Container(
                padding: const EdgeInsets.all(7),
                decoration: BoxDecoration(
                  color: const Color(0xFFEFF6FF),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: const Icon(Icons.tune_rounded, color: Color(0xFF0066FF), size: 18),
              ),
              const SizedBox(width: 10),
              const Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Tiêu chí tìm việc và ghép ca',
                      style: TextStyle(
                        fontWeight: FontWeight.w800,
                        fontSize: 15,
                        color: Color(0xFF0F172A),
                      ),
                    ),
                    Text(
                      'Định hình phạm vi địa lý và mức thù lao nhận việc',
                      style: TextStyle(fontSize: 12, color: Color(0xFF64748B)),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),
          const Divider(height: 1, color: Color(0xFFF1F5F9)),
          const SizedBox(height: 16),

          // ---------------------------------------------------------------
          // 2. BÁN KÍNH NHẬN VIỆC (SLIDER + PRESETS)
          // ---------------------------------------------------------------
          Row(
            children: [
              const Icon(Icons.radar_rounded, color: Color(0xFF0066FF), size: 18),
              const SizedBox(width: 6),
              const Expanded(
                child: Text(
                  'Bán kính nhận việc tối đa',
                  style: TextStyle(fontWeight: FontWeight.w700, fontSize: 13.5, color: Color(0xFF1E293B)),
                ),
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                decoration: BoxDecoration(
                  color: const Color(0xFFEFF6FF),
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: const Color(0xFFBFDBFE)),
                ),
                child: Text(
                  '${widget.maxDistanceKm.round()} km',
                  style: const TextStyle(
                    fontWeight: FontWeight.w800,
                    fontSize: 13,
                    color: Color(0xFF0066FF),
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 6),
          SliderTheme(
            data: SliderTheme.of(context).copyWith(
              activeTrackColor: const Color(0xFF0066FF),
              inactiveTrackColor: const Color(0xFFE2E8F0),
              thumbColor: const Color(0xFF0066FF),
              overlayColor: const Color(0x220066FF),
              trackHeight: 4,
            ),
            child: Slider(
              value: widget.maxDistanceKm,
              min: 1,
              max: 50,
              divisions: 49,
              onChanged: widget.onDistanceChanged,
            ),
          ),
          // Bán kính presets
          SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            child: Row(
              children: _distancePresets.map((dist) {
                final isSelected = widget.maxDistanceKm.round() == dist.round();
                return Padding(
                  padding: const EdgeInsets.only(right: 6),
                  child: InkWell(
                    onTap: () => widget.onDistanceChanged(dist),
                    borderRadius: BorderRadius.circular(8),
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                      decoration: BoxDecoration(
                        color: isSelected ? const Color(0xFF0066FF) : const Color(0xFFF8FAFC),
                        borderRadius: BorderRadius.circular(8),
                        border: Border.all(
                          color: isSelected ? const Color(0xFF0066FF) : const Color(0xFFE2E8F0),
                        ),
                      ),
                      child: Text(
                        '${dist.round()}km',
                        style: TextStyle(
                          fontSize: 12,
                          fontWeight: FontWeight.w700,
                          color: isSelected ? Colors.white : const Color(0xFF475569),
                        ),
                      ),
                    ),
                  ),
                );
              }).toList(),
            ),
          ),

          const SizedBox(height: 20),
          const Divider(height: 1, color: Color(0xFFF1F5F9)),
          const SizedBox(height: 16),

          // ---------------------------------------------------------------
          // 3. MỨC THÙ LAO TỐI THIỂU (THEO GIỜ / THEO CA + TỰ NHẬP / CHỌN SẴN)
          // ---------------------------------------------------------------
          Row(
            children: [
              const Icon(Icons.payments_outlined, color: Color(0xFF16A34A), size: 18),
              const SizedBox(width: 6),
              const Expanded(
                child: Text(
                  'Mức thù lao tối thiểu',
                  style: TextStyle(fontWeight: FontWeight.w700, fontSize: 13.5, color: Color(0xFF1E293B)),
                ),
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: const Color(0xFFF0FDF4),
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(color: const Color(0xFFBBF7D0)),
                ),
                child: Text(
                  widget.isWagePerShift ? 'Theo ca (4 giờ)' : 'Theo giờ (đ/giờ)',
                  style: const TextStyle(
                    fontWeight: FontWeight.w700,
                    fontSize: 11.5,
                    color: Color(0xFF16A34A),
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),

          // Bộ chuyển đổi đơn vị toàn chiều rộng (Không icon thừa)
          Container(
            width: double.infinity,
            decoration: BoxDecoration(
              color: const Color(0xFFF1F5F9),
              borderRadius: BorderRadius.circular(10),
            ),
            padding: const EdgeInsets.all(3),
            child: Row(
              children: [
                Expanded(
                  child: _buildUnitTogglePill(
                    label: 'Theo giờ (đ/giờ)',
                    isSelected: !widget.isWagePerShift,
                    onTap: () => widget.onWageUnitChanged(false),
                  ),
                ),
                const SizedBox(width: 4),
                Expanded(
                  child: _buildUnitTogglePill(
                    label: 'Theo ca (4 giờ)',
                    isSelected: widget.isWagePerShift,
                    onTap: () => widget.onWageUnitChanged(true),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 12),

          // Ô nhập tự do mức tiền
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 4),
            decoration: BoxDecoration(
              color: const Color(0xFFF8FAFC),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: const Color(0xFFCBD5E1)),
            ),
            child: Row(
              children: [
                const Text(
                  '₫',
                  style: TextStyle(
                    fontSize: 18,
                    fontWeight: FontWeight.w800,
                    color: Color(0xFF16A34A),
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: TextFormField(
                    controller: _wageController,
                    keyboardType: TextInputType.number,
                    inputFormatters: [
                      FilteringTextInputFormatter.digitsOnly,
                    ],
                    onChanged: _onWageTextChanged,
                    style: const TextStyle(
                      fontSize: 17,
                      fontWeight: FontWeight.w800,
                      color: Color(0xFF0F172A),
                    ),
                    decoration: InputDecoration(
                      border: InputBorder.none,
                      hintText: widget.isWagePerShift ? 'Nhập mức thù lao theo ca...' : 'Nhập mức thù lao theo giờ...',
                      hintStyle: const TextStyle(fontSize: 13, color: Color(0xFF94A3B8), fontWeight: FontWeight.normal),
                      suffixText: widget.isWagePerShift ? 'đ / ca' : 'đ / giờ',
                      suffixStyle: const TextStyle(
                        fontSize: 12.5,
                        fontWeight: FontWeight.w700,
                        color: Color(0xFF16A34A),
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 8),

          // Ghi chú tương đương nếu chọn theo ca
          if (widget.isWagePerShift)
            Padding(
              padding: const EdgeInsets.only(left: 4, bottom: 8),
              child: Text(
                '≈ ${_formatNumber(widget.minWage)} đ/giờ (Quy đổi cho ca tiêu chuẩn 4 giờ)',
                style: const TextStyle(
                  fontSize: 11.5,
                  fontWeight: FontWeight.w600,
                  color: Color(0xFF0D9488),
                ),
              ),
            ),

          // Mức tiền gợi ý
          Wrap(
            spacing: 6,
            runSpacing: 6,
            children: currentPresets.map((preset) {
              final isSelected = currentDisplayWage == preset.round();
              final label = widget.isWagePerShift
                  ? '${_formatNumber(preset)}đ/ca'
                  : '${_formatNumber(preset)}đ/h';

              return InkWell(
                onTap: () {
                  final hourly = widget.isWagePerShift ? (preset / 4.0) : preset;
                  widget.onWageChanged(hourly);
                },
                borderRadius: BorderRadius.circular(8),
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                  decoration: BoxDecoration(
                    color: isSelected ? const Color(0xFF16A34A) : const Color(0xFFF1F5F9),
                    borderRadius: BorderRadius.circular(8),
                    border: Border.all(
                      color: isSelected ? const Color(0xFF16A34A) : const Color(0xFFE2E8F0),
                    ),
                  ),
                  child: Text(
                    label,
                    style: TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.w700,
                      color: isSelected ? Colors.white : const Color(0xFF334155),
                    ),
                  ),
                ),
              );
            }).toList(),
          ),

          const SizedBox(height: 20),
          const Divider(height: 1, color: Color(0xFFF1F5F9)),
          const SizedBox(height: 16),

          // ---------------------------------------------------------------
          // 4. VỊ TRÍ NEO NHẬN VIỆC (Đã bỏ Tiếng Anh, bỏ icon thừa, bỏ nút tọa độ xanh)
          // ---------------------------------------------------------------
          Row(
            children: [
              const Icon(Icons.location_on_rounded, color: Color(0xFF0D9488), size: 18),
              const SizedBox(width: 6),
              const Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Vị trí neo nhận việc',
                      style: TextStyle(fontWeight: FontWeight.w700, fontSize: 13.5, color: Color(0xFF1E293B)),
                    ),
                    Text(
                      'Địa chỉ xuất phát để hệ thống tính bán kính nhận việc',
                      style: TextStyle(fontSize: 11, color: Color(0xFF64748B)),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),

          // Ô nhập địa chỉ cụ thể (Khu tập thể, Chung cư, Tên đường...)
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
            decoration: BoxDecoration(
              color: const Color(0xFFF8FAFC),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: const Color(0xFFCBD5E1)),
            ),
            child: Row(
              children: [
                const Icon(Icons.search_rounded, size: 18, color: Color(0xFF64748B)),
                const SizedBox(width: 8),
                Expanded(
                  child: TextFormField(
                    controller: _addressController,
                    style: const TextStyle(fontSize: 13.5, fontWeight: FontWeight.w700, color: Color(0xFF0F172A)),
                    decoration: const InputDecoration(
                      border: InputBorder.none,
                      hintText: 'Ví dụ: Chung cư Vinhomes, Khu tập thể Thành Công...',
                      hintStyle: TextStyle(fontSize: 12, color: Color(0xFF94A3B8), fontWeight: FontWeight.normal),
                    ),
                    onChanged: _onAddressInputChanged,
                  ),
                ),
                if (_isSearchingAddress)
                  const SizedBox(
                    width: 14,
                    height: 14,
                    child: CircularProgressIndicator(strokeWidth: 2, color: Color(0xFF0066FF)),
                  ),
              ],
            ),
          ),

          // Danh sách kết quả gợi ý địa chỉ tự động (Autocomplete)
          if (_suggestions.isNotEmpty) ...[
            const SizedBox(height: 6),
            Container(
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(10),
                border: Border.all(color: const Color(0xFFE2E8F0)),
                boxShadow: const [
                  BoxShadow(color: Color(0x0C000000), blurRadius: 6, offset: Offset(0, 2)),
                ],
              ),
              child: Column(
                children: _suggestions.map((sug) {
                  return InkWell(
                    onTap: () {
                      _addressController.text = sug.fullAddress;
                      widget.onLocationChanged(sug.fullAddress, sug.lat, sug.lng);
                      setState(() => _suggestions = []);
                      try {
                        _mapController.move(LatLng(sug.lat, sug.lng), 14.0);
                      } catch (_) {}
                    },
                    child: Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                      child: Row(
                        children: [
                          const Icon(Icons.place_outlined, size: 16, color: Color(0xFF0D9488)),
                          const SizedBox(width: 8),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  sug.title,
                                  style: const TextStyle(fontSize: 12.5, fontWeight: FontWeight.w700, color: Color(0xFF0F172A)),
                                ),
                                Text(
                                  sug.fullAddress,
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                  style: const TextStyle(fontSize: 11, color: Color(0xFF64748B)),
                                ),
                              ],
                            ),
                          ),
                        ],
                      ),
                    ),
                  );
                }).toList(),
              ),
            ),
          ],
          const SizedBox(height: 8),

          // Nút lấy vị trí GPS hiện tại (Giao diện sạch sẽ, không emoji thừa)
          SizedBox(
            width: double.infinity,
            height: 38,
            child: OutlinedButton.icon(
              onPressed: _isGettingGps ? null : _captureGpsLocation,
              icon: _isGettingGps
                  ? const SizedBox(
                      width: 14,
                      height: 14,
                      child: CircularProgressIndicator(strokeWidth: 2, color: Color(0xFF0066FF)),
                    )
                  : const Icon(Icons.my_location_rounded, size: 16, color: Color(0xFF0066FF)),
              label: Text(
                _isGettingGps ? 'Đang xác định vị trí...' : 'Lấy vị trí hiện tại',
                style: const TextStyle(fontSize: 12.5, fontWeight: FontWeight.w700, color: Color(0xFF0066FF)),
              ),
              style: OutlinedButton.styleFrom(
                backgroundColor: const Color(0xFFEFF6FF),
                side: const BorderSide(color: Color(0xFFBFDBFE)),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
              ),
            ),
          ),
          const SizedBox(height: 12),

          // Chọn nhanh theo các quận lớn: Hà Nội đưa lên trước, tiếp đến TP. Hồ Chí Minh
          const Text(
            'Chọn nhanh theo quận huyện:',
            style: TextStyle(fontSize: 11.5, fontWeight: FontWeight.w600, color: Color(0xFF475569)),
          ),
          const SizedBox(height: 6),
          SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            child: Row(
              children: _districts.map((d) {
                final isSelected = widget.baseAddress == d.fullName ||
                    (widget.baseLat != null &&
                        (widget.baseLat! - d.lat).abs() < 0.01 &&
                        widget.baseLng != null &&
                        (widget.baseLng! - d.lng).abs() < 0.01);

                return Padding(
                  padding: const EdgeInsets.only(right: 6),
                  child: InkWell(
                    onTap: () {
                      _addressController.text = d.fullName;
                      widget.onLocationChanged(d.fullName, d.lat, d.lng);
                      try {
                        _mapController.move(LatLng(d.lat, d.lng), 13.5);
                      } catch (_) {}
                    },
                    borderRadius: BorderRadius.circular(8),
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                      decoration: BoxDecoration(
                        color: isSelected ? const Color(0xFF0D9488) : const Color(0xFFF1F5F9),
                        borderRadius: BorderRadius.circular(8),
                        border: Border.all(
                          color: isSelected ? const Color(0xFF0D9488) : const Color(0xFFCBD5E1),
                        ),
                      ),
                      child: Text(
                        d.shortName,
                        style: TextStyle(
                          fontSize: 11.5,
                          fontWeight: FontWeight.w700,
                          color: isSelected ? Colors.white : const Color(0xFF334155),
                        ),
                      ),
                    ),
                  ),
                );
              }).toList(),
            ),
          ),
          const SizedBox(height: 12),

          // ---------------------------------------------------------------
          // BẢN ĐỒ TRỰC QUAN ĐIỂM NEO VÀ BÁN KÍNH NHẬN VIỆC (Interactive Map)
          // Tương tự VenueLocationPicker bên Web EO & JobRadarMapWidget
          // ---------------------------------------------------------------
          ClipRRect(
            borderRadius: BorderRadius.circular(12),
            child: Container(
              height: 180,
              decoration: BoxDecoration(
                border: Border.all(color: const Color(0xFFCBD5E1)),
                borderRadius: BorderRadius.circular(12),
              ),
              child: Stack(
                children: [
                  FlutterMap(
                    mapController: _mapController,
                    options: MapOptions(
                      initialCenter: _currentCenter,
                      initialZoom: 13.0,
                      onTap: (tapPosition, point) async {
                        try {
                          _mapController.move(point, 13.5);
                        } catch (_) {}
                        final resolved = await _reverseGeocode(point.latitude, point.longitude);
                        final displayAddr = (resolved != null && resolved.isNotEmpty)
                            ? resolved
                            : 'Vị trí đã chọn trên bản đồ';
                        _addressController.text = displayAddr;
                        widget.onLocationChanged(displayAddr, point.latitude, point.longitude);
                      },
                    ),
                    children: [
                      TileLayer(
                        urlTemplate: 'https://mt{s}.google.com/vt/lyrs=m&x={x}&y={y}&z={z}&hl=vi',
                        subdomains: const ['0', '1', '2', '3'],
                        fallbackUrl: 'https://a.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}@2x.png',
                        maxZoom: 20,
                      ),
                      CircleLayer(
                        circles: [
                          CircleMarker(
                            point: _currentCenter,
                            radius: widget.maxDistanceKm * 1000,
                            useRadiusInMeter: true,
                            color: const Color(0x180066FF),
                            borderColor: const Color(0xFF0066FF),
                            borderStrokeWidth: 1.5,
                          ),
                        ],
                      ),
                      MarkerLayer(
                        markers: [
                          Marker(
                            point: _currentCenter,
                            width: 36,
                            height: 36,
                            child: const Icon(
                              Icons.location_on_rounded,
                              color: Color(0xFFEA4335),
                              size: 36,
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                  // Banner hướng dẫn chạm bản đồ
                  Positioned(
                    top: 8,
                    left: 8,
                    right: 8,
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                      decoration: BoxDecoration(
                        color: Colors.white.withValues(alpha: 0.92),
                        borderRadius: BorderRadius.circular(8),
                        boxShadow: const [
                          BoxShadow(color: Color(0x15000000), blurRadius: 4, offset: Offset(0, 1)),
                        ],
                      ),
                      child: Row(
                        children: [
                          const Icon(Icons.touch_app_rounded, size: 14, color: Color(0xFF0066FF)),
                          const SizedBox(width: 6),
                          Expanded(
                            child: Text(
                              'Chạm bản đồ để đổi vị trí neo (Bán kính: ${widget.maxDistanceKm.round()} km)',
                              style: const TextStyle(
                                fontSize: 11,
                                fontWeight: FontWeight.w600,
                                color: Color(0xFF334155),
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                  // Nút căn giữa vị trí neo
                  Positioned(
                    bottom: 8,
                    right: 8,
                    child: InkWell(
                      onTap: () {
                        try {
                          _mapController.move(_currentCenter, 13.5);
                        } catch (_) {}
                      },
                      borderRadius: BorderRadius.circular(20),
                      child: Container(
                        padding: const EdgeInsets.all(8),
                        decoration: const BoxDecoration(
                          color: Colors.white,
                          shape: BoxShape.circle,
                          boxShadow: [
                            BoxShadow(color: Color(0x20000000), blurRadius: 4, offset: Offset(0, 2)),
                          ],
                        ),
                        child: const Icon(Icons.my_location_rounded, size: 18, color: Color(0xFF0066FF)),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildUnitTogglePill({
    required String label,
    required bool isSelected,
    required VoidCallback onTap,
  }) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 7),
        alignment: Alignment.center,
        decoration: BoxDecoration(
          color: isSelected ? Colors.white : Colors.transparent,
          borderRadius: BorderRadius.circular(8),
          boxShadow: isSelected
              ? const [
                  BoxShadow(
                    color: Color(0x10000000),
                    blurRadius: 4,
                    offset: Offset(0, 2),
                  ),
                ]
              : null,
        ),
        child: Text(
          label,
          textAlign: TextAlign.center,
          style: TextStyle(
            fontSize: 12,
            fontWeight: isSelected ? FontWeight.w800 : FontWeight.w600,
            color: isSelected ? const Color(0xFF0F172A) : const Color(0xFF64748B),
          ),
        ),
      ),
    );
  }
}
