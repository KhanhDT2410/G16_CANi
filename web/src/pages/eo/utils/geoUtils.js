/**
 * Reverse geocoding helper using OpenStreetMap Nominatim
 * Trả về địa chỉ cụ thể (Số nhà, đường, phường/xã, quận/huyện, tỉnh/thành phố) - KHÔNG trả về chuỗi tọa độ GPS.
 */
export async function getAddressFromCoordinates(lat, lng) {
  // Kiểm tra nhanh nếu trùng khớp với các địa điểm sự kiện phổ biến
  const matchedPopular = POPULAR_VENUES.find(
    (v) => Math.abs(v.lat - lat) < 0.002 && Math.abs(v.lng - lng) < 0.002
  );
  if (matchedPopular) {
    return `${matchedPopular.name}, ${matchedPopular.displayName}`;
  }

  try {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 3500);
    const res = await fetch(
      `https://nominatim.openstreetmap.org/reverse?format=json&lat=${lat}&lon=${lng}&zoom=18&addressdetails=1`,
      { headers: { 'Accept-Language': 'vi' }, signal: controller.signal }
    );
    clearTimeout(timeoutId);
    const data = await res.json();
    if (data && data.display_name) {
      return data.display_name;
    }
    return '';
  } catch {
    return '';
  }
}

/**
 * Danh sách gợi ý các địa điểm sự kiện phổ biến tại Việt Nam (Fallback offline an toàn)
 */
export const POPULAR_VENUES = [
  { name: 'Trung tâm Hội chợ & Triển lãm Sài Gòn (SECC)', displayName: '799 Nguyễn Văn Linh, Tân Phú, Quận 7, TP.HCM', lat: 10.7328, lng: 106.7214 },
  { name: 'Sân vận động Quốc gia Mỹ Đình', displayName: 'Đường Lê Đức Thọ, Mỹ Đình 1, Nam Từ Liêm, Hà Nội', lat: 21.0205, lng: 105.7641 },
  { name: 'Trung tâm Hội nghị Quốc gia Việt Nam', displayName: 'Đại lộ Thăng Long, Mễ Trì, Nam Từ Liêm, Hà Nội', lat: 21.0068, lng: 105.7865 },
  { name: 'Trung tâm Sự kiện GEM Center', displayName: '8 Nguyễn Bỉnh Khiêm, Đa Kao, Quận 1, TP.HCM', lat: 10.7877, lng: 106.7027 },
  { name: 'Trung tâm Hội nghị White Palace', displayName: '194 Hoàng Văn Thụ, Phường 9, Phú Nhuận, TP.HCM', lat: 10.7995, lng: 106.6713 },
  { name: 'Nhà thi đấu Thể dục Thể thao Phú Thọ', displayName: '1 Lữ Gia, Phường 15, Quận 11, TP.HCM', lat: 10.7686, lng: 106.6575 },
  { name: 'Cung Điền kinh Trong nhà Hà Nội', displayName: 'Trần Hữu Dực, Cầu Diễn, Nam Từ Liêm, Hà Nội', lat: 21.0253, lng: 105.7618 },
  { name: 'Trung tâm Hội nghị Quốc tế Almaz Hà Nội', displayName: 'Hoa Lan, Khu đô thị Vinhomes Riverside, Long Biên, Hà Nội', lat: 21.0422, lng: 105.9084 },
];

/**
 * Search location suggestions using OpenStreetMap Nominatim with fallback
 */
export async function searchAddressSuggestions(query) {
  if (!query || !query.trim()) return [];
  const qLower = query.trim().toLowerCase();

  // Tìm nhanh trong danh sách địa điểm sự kiện phổ biến trước
  const localMatches = POPULAR_VENUES.filter(
    (v) => v.name.toLowerCase().includes(qLower) || v.displayName.toLowerCase().includes(qLower)
  );

  try {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 3500);
    const res = await fetch(
      `https://nominatim.openstreetmap.org/search?format=json&q=${encodeURIComponent(query.trim())}&countrycodes=vn&limit=5&addressdetails=1`,
      { headers: { 'Accept-Language': 'vi' }, signal: controller.signal }
    );
    clearTimeout(timeoutId);
    const data = await res.json();
    if (Array.isArray(data) && data.length > 0) {
      const apiResults = data.map((item) => ({
        lat: parseFloat(parseFloat(item.lat).toFixed(6)),
        lng: parseFloat(parseFloat(item.lon).toFixed(6)),
        displayName: item.display_name,
        name: item.name || item.display_name.split(',')[0],
      }));

      // Gộp và loại trừ trùng lặp
      const combined = [...localMatches];
      for (const item of apiResults) {
        if (!combined.some((c) => Math.abs(c.lat - item.lat) < 0.001 && Math.abs(c.lng - item.lng) < 0.001)) {
          combined.push(item);
        }
      }
      return combined.slice(0, 6);
    }
  } catch {
    // Nếu mạng chậm hoặc timeout, trả về kết quả local
  }

  return localMatches;
}

/**
 * Forward geocoding helper using OpenStreetMap Nominatim
 */
export async function getCoordinatesFromAddress(address) {
  if (!address || !address.trim()) return null;
  const qLower = address.trim().toLowerCase();

  const foundLocal = POPULAR_VENUES.find(
    (v) => v.name.toLowerCase().includes(qLower) || v.displayName.toLowerCase().includes(qLower)
  );
  if (foundLocal) {
    return { lat: foundLocal.lat, lng: foundLocal.lng, displayName: foundLocal.displayName };
  }

  try {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 3000);
    const res = await fetch(
      `https://nominatim.openstreetmap.org/search?format=json&q=${encodeURIComponent(address.trim())}&countrycodes=vn&limit=1`,
      { headers: { 'Accept-Language': 'vi' }, signal: controller.signal }
    );
    clearTimeout(timeoutId);
    const data = await res.json();
    if (data && data.length > 0) {
      return {
        lat: parseFloat(parseFloat(data[0].lat).toFixed(6)),
        lng: parseFloat(parseFloat(data[0].lon).toFixed(6)),
        displayName: data[0].display_name,
      };
    }
    return null;
  } catch {
    return null;
  }
}

