/**
 * Reverse geocoding helper using OpenStreetMap Nominatim
 */
export async function getAddressFromCoordinates(lat, lng) {
  try {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 3000);
    const res = await fetch(
      `https://nominatim.openstreetmap.org/reverse?format=json&lat=${lat}&lon=${lng}&zoom=18&addressdetails=1`,
      { headers: { 'Accept-Language': 'vi' }, signal: controller.signal }
    );
    clearTimeout(timeoutId);
    const data = await res.json();
    return data.display_name || `Vị trí GPS (${lat}, ${lng})`;
  } catch {
    return `Tọa độ GPS (${lat}, ${lng})`;
  }
}

/**
 * Forward geocoding helper using OpenStreetMap Nominatim
 */
export async function getCoordinatesFromAddress(address) {
  if (!address || !address.trim()) return null;
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

