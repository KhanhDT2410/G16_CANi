package com.cani.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
public class GeoMapService {

    private static final double EARTH_RADIUS_KM = 6371.0;

    // Cơ sở dữ liệu tọa độ các trung tâm hội nghị, sự kiện lớn tại Việt Nam
    private static final Map<String, Coordinates> KNOWN_VENUES = Map.of(
            "secc", new Coordinates(10.7303, 106.7218, "SECC - Trung tâm Hội chợ & Triển lãm Sài Gòn"),
            "gem center", new Coordinates(10.7876, 106.7024, "GEM Center - Quận 1, TP.HCM"),
            "white palace", new Coordinates(10.8032, 106.6784, "White Palace Hoàng Văn Thụ, TP.HCM"),
            "ncc", new Coordinates(21.0067, 105.7877, "Trung tâm Hội nghị Quốc gia NCC, Hà Nội"),
            "jw marriott", new Coordinates(21.0076, 105.7818, "JW Marriott Hotel Hanoi"),
            "da nang exhibition", new Coordinates(16.0354, 108.2238, "Trung tâm Hội chợ Triển lãm Đà Nẵng")
    );

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Coordinates {
        private Double latitude;
        private Double longitude;
        private String formattedAddress;
    }

    /**
     * WBS 340.0: Phân giải địa chỉ thành tọa độ địa lý (Geocoding)
     */
    public Coordinates geocodeAddress(String address) {
        if (address == null || address.isBlank()) {
            return new Coordinates(10.7769, 106.7009, "TP. Hồ Chí Minh (Mặc định)");
        }

        String lower = address.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, Coordinates> entry : KNOWN_VENUES.entrySet()) {
            if (lower.contains(entry.getKey())) {
                log.info("Phân giải địa chỉ '{}' thành công khớp với địa điểm đã biết: {}", address, entry.getValue().getFormattedAddress());
                return entry.getValue();
            }
        }

        // Nhận diện theo thành phố
        if (lower.contains("hà nội") || lower.contains("ha noi") || lower.contains("hanoi")) {
            return new Coordinates(21.0285, 105.8542, address);
        } else if (lower.contains("đà nẵng") || lower.contains("da nang") || lower.contains("danang")) {
            return new Coordinates(16.0544, 108.2022, address);
        } else {
            // Mặc định TP.HCM
            return new Coordinates(10.7769, 106.7009, address);
        }
    }

    /**
     * WBS 340.0: Tính toán ma trận khoảng cách Haversine giữa 2 tọa độ (km)
     */
    public double calculateDistanceKm(Double lat1, Double lon1, Double lat2, Double lon2) {
        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) {
            return Double.MAX_VALUE;
        }
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double rLat1 = Math.toRadians(lat1);
        double rLat2 = Math.toRadians(lat2);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(rLat1) * Math.cos(rLat2) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    /**
     * WBS 340.0: Đánh giá hàng rào địa lý (Geofencing evaluation) phục vụ điểm danh
     */
    public boolean isWithinGeofence(Double venueLat, Double venueLng, Double userLat, Double userLng, double radiusKm) {
        double dist = calculateDistanceKm(venueLat, venueLng, userLat, userLng);
        return dist <= radiusKm;
    }
}
