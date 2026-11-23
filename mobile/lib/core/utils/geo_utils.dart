import 'dart:async';
import 'package:dio/dio.dart';

class AddressSuggestion {
  final String title;
  final String fullAddress;
  final double lat;
  final double lng;

  const AddressSuggestion({
    required this.title,
    required this.fullAddress,
    required this.lat,
    required this.lng,
  });
}

/// Tiện ích phân giải địa chỉ và tọa độ đa tầng (Multi-tier Geocoding & Autocomplete)
/// Tương thích chuẩn kiến trúc của Web (geoUtils.js)
/// Tầng 1: OpenStreetMap Nominatim
/// Tầng 2: Photon Komoot (Elasticsearch geocoder, không bị DNS NXDOMAIN tại Việt Nam)
class GeoUtils {
  static final Dio _dio = Dio(
    BaseOptions(
      connectTimeout: const Duration(seconds: 4),
      receiveTimeout: const Duration(seconds: 4),
      sendTimeout: const Duration(seconds: 4),
      headers: {
        'Accept-Language': 'vi,en;q=0.8',
        'User-Agent': 'CANiEventStaffingApp/1.0',
      },
    ),
  );

  /// 1. Tra cứu địa chỉ thực tế từ tọa độ GPS (Reverse Geocoding)
  /// Giúp nhận diện chính xác "Goldmark City, Phú Diễn, Hà Nội" ngay khi bấm "Lấy vị trí hiện tại"
  static Future<String?> reverseGeocode(double lat, double lng) async {
    // Tầng 1: Thử Nominatim
    try {
      final res = await _dio.get(
        'https://nominatim.openstreetmap.org/reverse',
        queryParameters: {
          'format': 'json',
          'lat': lat,
          'lon': lng,
          'zoom': 18,
          'addressdetails': 1,
        },
      );
      if (res.data is Map) {
        final formatted = _formatNominatimAddress(res.data as Map<String, dynamic>);
        if (formatted.isNotEmpty) return formatted;
      }
    } catch (_) {
      // Khi DNS/nhà mạng VN chặn Nominatim, tự động failover sang Photon bên dưới
    }

    // Tầng 2: Fallback Photon Komoot (cực kỳ ổn định và nhanh tại VN)
    try {
      final res = await _dio.get(
        'https://photon.komoot.io/reverse',
        queryParameters: {
          'lat': lat,
          'lon': lng,
        },
      );
      if (res.data is Map && res.data['features'] is List) {
        final features = res.data['features'] as List;
        if (features.isNotEmpty) {
          final first = features[0];
          if (first is Map && first['properties'] is Map) {
            final formatted = _formatPhotonAddress(first['properties'] as Map<String, dynamic>);
            if (formatted.isNotEmpty) return formatted;
          }
        }
      }
    } catch (_) {}

    return null;
  }

  /// 2. Gợi ý địa chỉ tự động khi gõ (Autocomplete)
  /// Ưu tiên Photon Komoot để tìm kiếm cấp xã/thị trấn (ví dụ: "Khánh Nhạc") chỉ trong ~300ms
  static Future<List<AddressSuggestion>> searchAddressSuggestions(String query) async {
    final trimmed = query.trim();
    if (trimmed.length < 2) return [];

    // Tầng 1: Photon Komoot (Elasticsearch)
    try {
      final res = await _dio.get(
        'https://photon.komoot.io/api/',
        queryParameters: {
          'q': '$trimmed, Vietnam',
          'limit': 5,
        },
      );

      if (res.data is Map && res.data['features'] is List) {
        final features = res.data['features'] as List;
        final list = <AddressSuggestion>[];
        for (final item in features) {
          if (item is Map && item['properties'] is Map && item['geometry'] is Map) {
            final props = item['properties'] as Map<String, dynamic>;
            final geom = item['geometry'] as Map<String, dynamic>;
            final coords = geom['coordinates'];
            if (coords is List && coords.length >= 2) {
              final lng = (coords[0] as num).toDouble();
              final lat = (coords[1] as num).toDouble();
              final formatted = _formatPhotonAddress(props);
              final shortTitle = props['name']?.toString() ?? formatted.split(',').first;
              list.add(AddressSuggestion(
                title: shortTitle,
                fullAddress: formatted.isNotEmpty ? formatted : shortTitle,
                lat: lat,
                lng: lng,
              ));
            }
          }
        }
        if (list.isNotEmpty) return list;
      }
    } catch (_) {
      // Fallback sang Nominatim
    }

    // Tầng 2: Fallback Nominatim
    try {
      final res = await _dio.get(
        'https://nominatim.openstreetmap.org/search',
        queryParameters: {
          'format': 'json',
          'q': trimmed,
          'countrycodes': 'vn',
          'limit': 5,
          'addressdetails': 1,
        },
      );

      if (res.data is List) {
        final list = <AddressSuggestion>[];
        for (final item in res.data as List) {
          if (item is Map<String, dynamic>) {
            final displayName = _formatNominatimAddress(item);
            final finalDisplay = displayName.isNotEmpty ? displayName : (item['display_name']?.toString() ?? trimmed);
            final shortTitle = item['name']?.toString() ?? finalDisplay.split(',').first;
            list.add(AddressSuggestion(
              title: shortTitle,
              fullAddress: finalDisplay,
              lat: double.tryParse(item['lat'].toString()) ?? 0.0,
              lng: double.tryParse(item['lon'].toString()) ?? 0.0,
            ));
          }
        }
        if (list.isNotEmpty) return list;
      }
    } catch (_) {}

    return [];
  }

  static String _formatPhotonAddress(Map<String, dynamic> props) {
    final parts = <String>[];
    final name = props['name']?.toString();
    if (name != null && name.isNotEmpty) parts.add(name);

    final housenumber = props['housenumber']?.toString();
    final street = props['street']?.toString();
    if (housenumber != null && street != null) {
      final houseRoad = '$housenumber $street';
      if (!parts.contains(houseRoad)) parts.add(houseRoad);
    } else if (street != null && !parts.contains(street)) {
      parts.add(street);
    }

    final district = props['district']?.toString();
    if (district != null && district.isNotEmpty && !parts.contains(district)) {
      parts.add(district);
    }

    final city = props['city']?.toString();
    if (city != null && city.isNotEmpty && !parts.contains(city)) {
      parts.add(city);
    }

    final state = props['state']?.toString();
    if (state != null && state.isNotEmpty && !parts.contains(state)) {
      parts.add(state);
    }

    return parts.join(', ');
  }

  static String _formatNominatimAddress(Map<String, dynamic> data) {
    final addr = data['address'] as Map<String, dynamic>?;
    if (addr == null) {
      return data['display_name']?.toString() ?? '';
    }

    final building = addr['building'] ??
        addr['amenity'] ??
        addr['residential'] ??
        addr['office'] ??
        addr['commercial'];
    final road = addr['road'] ?? addr['pedestrian'] ?? addr['street'];
    final houseNumber = addr['house_number'];
    final suburb = addr['suburb'] ?? addr['quarter'] ?? addr['neighbourhood'] ?? addr['village'];
    final district = addr['city_district'] ?? addr['district'] ?? addr['county'];
    final city = addr['city'] ?? addr['state'] ?? addr['province'];

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
    return data['display_name']?.toString() ?? '';
  }
}
