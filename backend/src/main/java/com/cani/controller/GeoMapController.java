package com.cani.controller;

import com.cani.dto.response.ApiResponse;
import com.cani.service.GeoMapService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/maps")
@RequiredArgsConstructor
@Tag(name = "Maps & Geocoding", description = "Dịch vụ bản đồ, định vị và đánh giá hàng rào địa lý (WBS 340.0)")
public class GeoMapController {

    private final GeoMapService geoMapService;

    /**
     * WBS 340.0: Phân giải địa chỉ thành tọa độ địa lý
     */
    @Operation(summary = "Phân giải tọa độ địa chỉ", description = "Chuyển đổi địa chỉ địa điểm tổ chức thành tọa độ (Latitude, Longitude).")
    @GetMapping("/geocode")
    public ResponseEntity<ApiResponse<GeoMapService.Coordinates>> geocode(@RequestParam String address) {
        GeoMapService.Coordinates coords = geoMapService.geocodeAddress(address);
        return ResponseEntity.ok(ApiResponse.ok(coords, "Phân giải tọa độ địa chỉ thành công"));
    }

    /**
     * WBS 340.0: Tính khoảng cách giữa hai tọa độ
     */
    @Operation(summary = "Tính khoảng cách địa lý", description = "Tính khoảng cách chim bay theo công thức Haversine giữa hai điểm GPS.")
    @GetMapping("/distance")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDistance(
            @RequestParam Double lat1,
            @RequestParam Double lon1,
            @RequestParam Double lat2,
            @RequestParam Double lon2
    ) {
        double distanceKm = geoMapService.calculateDistanceKm(lat1, lon1, lat2, lon2);
        return ResponseEntity.ok(ApiResponse.ok(
                Map.of("distanceKm", Math.round(distanceKm * 100.0) / 100.0),
                "Tính khoảng cách thành công"
        ));
    }

    /**
     * WBS 340.0: Đánh giá vị trí có nằm trong hàng rào địa lý (Geofence) của sự kiện hay không
     */
    @Operation(summary = "Kiểm tra hàng rào địa lý", description = "Đánh giá xem tọa độ của Worker có nằm trong bán kính cho phép của địa điểm tổ chức hay không.")
    @GetMapping("/geofence")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkGeofence(
            @RequestParam Double venueLat,
            @RequestParam Double venueLng,
            @RequestParam Double userLat,
            @RequestParam Double userLng,
            @RequestParam(defaultValue = "0.5") Double radiusKm
    ) {
        boolean within = geoMapService.isWithinGeofence(venueLat, venueLng, userLat, userLng, radiusKm);
        double currentDistance = geoMapService.calculateDistanceKm(venueLat, venueLng, userLat, userLng);
        return ResponseEntity.ok(ApiResponse.ok(
                Map.of(
                        "isWithinGeofence", within,
                        "currentDistanceKm", Math.round(currentDistance * 1000.0) / 1000.0,
                        "allowedRadiusKm", radiusKm
                ),
                within ? "Vị trí hợp lệ trong phạm vi sự kiện" : "Vị trí nằm ngoài phạm vi cho phép của sự kiện"
        ));
    }
}
