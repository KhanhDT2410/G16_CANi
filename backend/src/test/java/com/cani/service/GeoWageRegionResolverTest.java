package com.cani.service;

import com.cani.entity.enums.City;
import com.cani.entity.enums.WageRegionCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GeoWageRegionResolverTest {

    private final GeoWageRegionResolver resolver = new GeoWageRegionResolver();

    @Test
    @DisplayName("Normal - Phân giải chính xác 4 vùng toàn quốc theo địa chỉ")
    void resolve_DetectsAllFourRegionsFromAddress() {
        var hanoi = resolver.resolve(null, null, "Trung tâm Hội nghị Quốc gia, Nam Từ Liêm, Hà Nội", null, null);
        assertEquals(WageRegionCode.REGION_1, hanoi.regionCode());
        assertEquals("Vùng I (Hà Nội)", hanoi.regionLabel());

        var danang = resolver.resolve(null, null, "Cung thể thao Tiên Sơn, Hải Châu, Đà Nẵng", null, null);
        assertEquals(WageRegionCode.REGION_2, danang.regionCode());
        assertEquals("Vùng II (Đà Nẵng)", danang.regionLabel());

        var camau = resolver.resolve(null, null, "Quảng trường Hùng Vương, TP. Cà Mau", null, null);
        assertEquals(WageRegionCode.REGION_3, camau.regionCode());

        var hagiang = resolver.resolve(null, null, "Quảng trường 26/3, TP. Hà Giang", null, null);
        assertEquals(WageRegionCode.REGION_4, hagiang.regionCode());
    }

    @Test
    @DisplayName("Boundary - Ưu tiên explicitRegion và tọa độ GPS trước fallback mặc định")
    void resolve_PrefersExplicitAndGpsCoordinates() {
        var explicit = resolver.resolve(WageRegionCode.REGION_3, City.TP_HCM, "Quận 1, TP.HCM", 10.77, 106.70);
        assertEquals(WageRegionCode.REGION_3, explicit.regionCode());

        var gpsDaNang = resolver.resolve(null, null, null, 16.0321, 108.2154);
        assertEquals(WageRegionCode.REGION_2, gpsDaNang.regionCode());

        var fallback = resolver.resolve(null, null, null, null, null);
        assertEquals(WageRegionCode.REGION_1, fallback.regionCode());
        assertEquals("Vùng I (TP. Hồ Chí Minh)", fallback.regionLabel());
    }
}
