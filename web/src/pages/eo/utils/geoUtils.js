/**
 * Reverse geocoding helper using OpenStreetMap Nominatim
 */
export async function getAddressFromCoordinates(lat, lng) {
  try {
    const res = await fetch(
      `https://nominatim.openstreetmap.org/reverse?format=json&lat=${lat}&lon=${lng}&zoom=18&addressdetails=1`,
      { headers: { 'Accept-Language': 'vi' } }
    );
    const data = await res.json();
    return data.display_name || `Vị trí GPS (${lat}, ${lng})`;
  } catch {
    return `Tọa độ GPS (${lat}, ${lng})`;
  }
}

