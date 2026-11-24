/**
 * Geocoding helpers: OpenStreetMap Nominatim + Photon fallback
 * Trả về địa chỉ tiếng Việt chi tiết & tọa độ ổn định khi Nominatim chậm/throttle.
 */

function formatNominatimAddress(data) {
  if (!data) return '';
  const addr = data.address || {};
  const parts = [];
  const houseRoad = [addr.house_number, addr.road || addr.pedestrian || addr.footway]
    .filter(Boolean)
    .join(' ');
  const venueName = data.name && !houseRoad.includes(data.name) ? data.name : '';
  if (venueName) parts.push(venueName);
  if (houseRoad) parts.push(houseRoad);
  const ward = addr.quarter || addr.suburb || addr.neighbourhood || addr.village;
  if (ward) parts.push(ward);
  const district = addr.city_district || addr.district || addr.county || addr.town;
  if (district && !parts.includes(district)) parts.push(district);
  const city = addr.city || addr.state || addr.province;
  if (city && !parts.includes(city)) parts.push(city);
  if (parts.length >= 2) {
    return parts.join(', ');
  }
  return data.display_name || '';
}

function formatPhotonFeatureAddress(props = {}) {
  const parts = [];
  if (props.name) parts.push(props.name);
  const street = [props.housenumber, props.street].filter(Boolean).join(' ');
  if (street && !parts.includes(street)) parts.push(street);
  if (props.district && !parts.includes(props.district)) parts.push(props.district);
  if (props.city && !parts.includes(props.city)) parts.push(props.city);
  if (props.state && !parts.includes(props.state)) parts.push(props.state);
  return parts.join(', ');
}

async function fetchWithTimeout(url, options = {}, timeoutMs = 4500) {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), timeoutMs);
  try {
    return await fetch(url, { ...options, signal: controller.signal });
  } finally {
    clearTimeout(timeoutId);
  }
}

export async function getAddressFromCoordinates(lat, lng) {
  if (lat == null || lng == null || isNaN(Number(lat)) || isNaN(Number(lng))) {
    return '';
  }
  const numLat = Number(lat);
  const numLng = Number(lng);

  const matchedPopular = POPULAR_VENUES.find(
    (v) => Math.abs(v.lat - numLat) < 0.002 && Math.abs(v.lng - numLng) < 0.002
  );
  if (matchedPopular) {
    return `${matchedPopular.name}, ${matchedPopular.displayName}`;
  }

  // 1. BigDataCloud reverse geocoding (Hỗ trợ CORS: * đầy đủ cho trình duyệt, độ chính xác cao tại Việt Nam)
  try {
    const res = await fetchWithTimeout(
      `https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=${numLat}&longitude=${numLng}&localityLanguage=vi`,
      {},
      4000
    );
    if (res.ok) {
      const data = await res.json();
      const parts = [];
      if (data.locality) parts.push(data.locality);
      if (data.city && !parts.includes(data.city)) parts.push(data.city);
      if (data.principalSubdivision && !parts.includes(data.principalSubdivision)) {
        parts.push(data.principalSubdivision);
      }
      if (data.countryName && !parts.includes(data.countryName)) parts.push(data.countryName);
      if (parts.length > 0) {
        return parts.join(', ');
      }
    }
  } catch (err) {
    console.warn('BigDataCloud reverse geocode error:', err);
  }

  // 2. OpenStreetMap Nominatim reverse geocoding (Fallback)
  try {
    const res = await fetchWithTimeout(
      `https://nominatim.openstreetmap.org/reverse?format=json&lat=${numLat}&lon=${numLng}&zoom=18&addressdetails=1`,
      { headers: { 'Accept-Language': 'vi' } },
      3000
    );
    if (res.ok) {
      const data = await res.json();
      const formatted = formatNominatimAddress(data);
      if (formatted) return formatted;
    }
  } catch {
    // Chuyển sang fallback Photon
  }

  // 3. Fallback: Photon (Komoot OSM) reverse geocoding
  try {
    const res = await fetchWithTimeout(
      `https://photon.komoot.io/reverse?lat=${numLat}&lon=${numLng}`,
      {},
      3000
    );
    if (res.ok) {
      const data = await res.json();
      const first = data?.features?.[0]?.properties;
      const formatted = formatPhotonFeatureAddress(first);
      if (formatted) return formatted;
    }
  } catch {
    // Bỏ qua lỗi mạng
  }

  // 4. Fallback tối thượng: Ghi rõ tọa độ GPS để không bao giờ bị rỗng ô nhập liệu
  return `Vị trí GPS (${numLat.toFixed(5)}, ${numLng.toFixed(5)})`;
}

/**
 * Danh sách gợi ý các địa điểm sự kiện phổ biến tại Việt Nam (Fallback offline an toàn)
 */
export const POPULAR_VENUES = [
  { name: 'Trung tâm Hội chợ & Triển lãm Sài Gòn (SECC)', displayName: '799 Nguyễn Văn Linh, Tân Phú, Quận 7, TP.HCM', lat: 10.7328, lng: 106.7214 },
  { name: 'Sân vận động Quốc gia Mỹ Đình', displayName: 'Đường Lê Đức Thọ, Mỹ Đình 1, Nam Từ Liêm, Hà Nội', lat: 21.0205, lng: 105.7641 },
  { name: 'Trung tâm Hội nghị Quốc gia Việt Nam', displayName: 'Đại lộ Thăng Long, Mễ Trì, Nam Từ Liêm, Hà Nội', lat: 21.0068, lng: 105.7865 },
  { name: 'Trung tâm Hội nghị tỉnh Ninh Bình', displayName: 'Đường Tràng An, Tân Thành, TP. Ninh Bình, Ninh Bình', lat: 20.2543, lng: 105.9754 },
  { name: 'Khu du lịch Sinh thái Tràng An', displayName: 'Tràng An, Trường Yên, Hoa Lư, Ninh Bình', lat: 20.2528, lng: 105.9142 },
  { name: 'Quần thể Chùa Bái Đính', displayName: 'Gia Sinh, Gia Viễn, Ninh Bình', lat: 20.2721, lng: 105.8569 },
  { name: 'Cố đô Hoa Lư', displayName: 'Trường Yên, Hoa Lư, Ninh Bình', lat: 20.2847, lng: 105.9083 },
  { name: 'Trung tâm Sự kiện GEM Center', displayName: '8 Nguyễn Bỉnh Khiêm, Đa Kao, Quận 1, TP.HCM', lat: 10.7877, lng: 106.7027 },
  { name: 'Trung tâm Hội nghị White Palace', displayName: '194 Hoàng Văn Thụ, Phường 9, Phú Nhuận, TP.HCM', lat: 10.7995, lng: 106.6713 },
  { name: 'Nhà thi đấu Thể dục Thể thao Phú Thọ', displayName: '1 Lữ Gia, Phường 15, Quận 11, TP.HCM', lat: 10.7686, lng: 106.6575 },
  { name: 'Cung Điền kinh Trong nhà Hà Nội', displayName: 'Trần Hữu Dực, Cầu Diễn, Nam Từ Liêm, Hà Nội', lat: 21.0253, lng: 105.7618 },
  { name: 'Trung tâm Hội nghị Quốc tế Almaz Hà Nội', displayName: 'Hoa Lan, Khu đô thị Vinhomes Riverside, Long Biên, Hà Nội', lat: 21.0422, lng: 105.9084 },
];

/**
 * Search location suggestions using Nominatim + Photon fallback
 */
export async function searchAddressSuggestions(query) {
  if (!query || !query.trim()) return [];
  const trimmed = query.trim();
  const qLower = trimmed.toLowerCase();

  const localMatches = POPULAR_VENUES.filter(
    (v) => v.name.toLowerCase().includes(qLower) || v.displayName.toLowerCase().includes(qLower)
  );

  let apiResults = [];
  try {
    const res = await fetchWithTimeout(
      `https://nominatim.openstreetmap.org/search?format=json&q=${encodeURIComponent(trimmed)}&countrycodes=vn&limit=5&addressdetails=1`,
      { headers: { 'Accept-Language': 'vi' } },
      6500
    );
    if (res.ok) {
      const data = await res.json();
      if (Array.isArray(data) && data.length > 0) {
        apiResults = data.map((item) => ({
          lat: parseFloat(parseFloat(item.lat).toFixed(6)),
          lng: parseFloat(parseFloat(item.lon).toFixed(6)),
          displayName: formatNominatimAddress(item) || item.display_name,
          name: item.name || item.display_name.split(',')[0],
        }));
      }
    }
  } catch {
    // Fallback sang Photon bên dưới
  }

  if (apiResults.length === 0) {
    try {
      const res = await fetchWithTimeout(
        `https://photon.komoot.io/api/?q=${encodeURIComponent(trimmed + ', Vietnam')}&limit=5`,
        {},
        5500
      );
      if (res.ok) {
        const data = await res.json();
        const features = Array.isArray(data?.features) ? data.features : [];
        apiResults = features
          .filter((f) => Array.isArray(f?.geometry?.coordinates))
          .map((f) => {
            const [lon, lat] = f.geometry.coordinates;
            const formatted = formatPhotonFeatureAddress(f.properties);
            return {
              lat: parseFloat(Number(lat).toFixed(6)),
              lng: parseFloat(Number(lon).toFixed(6)),
              displayName: formatted || f.properties?.name || trimmed,
              name: f.properties?.name || formatted.split(',')[0] || trimmed,
            };
          });
      }
    } catch {
      // Giữ localMatches
    }
  }

  const combined = [...localMatches];
  for (const item of apiResults) {
    if (!combined.some((c) => Math.abs(c.lat - item.lat) < 0.001 && Math.abs(c.lng - item.lng) < 0.001)) {
      combined.push(item);
    }
  }
  return combined.slice(0, 6);
}

/**
 * Forward geocoding helper (Nominatim + Photon fallback)
 */
export async function getCoordinatesFromAddress(address) {
  if (!address || !address.trim()) return null;
  const trimmed = address.trim();
  const qLower = trimmed.toLowerCase();

  const foundLocal = POPULAR_VENUES.find(
    (v) => v.name.toLowerCase().includes(qLower) || v.displayName.toLowerCase().includes(qLower)
  );
  if (foundLocal) {
    return { lat: foundLocal.lat, lng: foundLocal.lng, displayName: foundLocal.displayName };
  }

  try {
    const res = await fetchWithTimeout(
      `https://nominatim.openstreetmap.org/search?format=json&q=${encodeURIComponent(trimmed)}&countrycodes=vn&limit=1&addressdetails=1`,
      { headers: { 'Accept-Language': 'vi' } },
      6000
    );
    if (res.ok) {
      const data = await res.json();
      if (Array.isArray(data) && data.length > 0) {
        return {
          lat: parseFloat(parseFloat(data[0].lat).toFixed(6)),
          lng: parseFloat(parseFloat(data[0].lon).toFixed(6)),
          displayName: formatNominatimAddress(data[0]) || data[0].display_name,
        };
      }
    }
  } catch {
    // Thử Photon fallback
  }

  try {
    const res = await fetchWithTimeout(
      `https://photon.komoot.io/api/?q=${encodeURIComponent(trimmed + ', Vietnam')}&limit=1`,
      {},
      5500
    );
    if (res.ok) {
      const data = await res.json();
      const first = data?.features?.[0];
      if (Array.isArray(first?.geometry?.coordinates)) {
        const [lon, lat] = first.geometry.coordinates;
        return {
          lat: parseFloat(Number(lat).toFixed(6)),
          lng: parseFloat(Number(lon).toFixed(6)),
          displayName: formatPhotonFeatureAddress(first.properties) || trimmed,
        };
      }
    }
  } catch {
    // Bỏ qua lỗi mạng
  }

  return null;
}
