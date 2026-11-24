export const MAX_VENUE_ACCURACY_METERS = 200;
export const MAX_APPROXIMATE_ACCURACY_METERS = 2000;
const POSITION_TIMEOUT_MS = 10000;

export function requestVenuePosition(geolocation, signal) {
  return new Promise((resolve, reject) => {
    signal.throwIfAborted();
    let bestAccuracy = Infinity;
    let bestCoords = null;
    let finished = false;

    const finish = (complete, value) => {
      if (finished) return;
      finished = true;
      clearTimeout(timeoutId);
      geolocation.clearWatch(watchId);
      signal.removeEventListener('abort', abort);
      complete(value);
    };
    const abort = () => finish(reject, signal.reason);

    const watchId = geolocation.watchPosition(
      ({ coords }) => {
        if (
          !coords
          || !Number.isFinite(coords.latitude)
          || !Number.isFinite(coords.longitude)
          || !Number.isFinite(coords.accuracy)
        ) {
          return;
        }
        if (coords.accuracy < bestAccuracy) {
          bestAccuracy = coords.accuracy;
          bestCoords = coords;
        }
        if (coords.accuracy <= MAX_VENUE_ACCURACY_METERS) {
          finish(resolve, coords);
        }
      },
      ({ code }) => {
        if (code === 1) {
          finish(reject, new Error('Hãy cấp quyền vị trí cho trang web hoặc chọn địa điểm trên bản đồ.'));
          return;
        }
        if ((code === 2 || code === 3) && bestCoords && bestAccuracy <= MAX_APPROXIMATE_ACCURACY_METERS) {
          finish(resolve, bestCoords);
        }
      },
      { enableHighAccuracy: true, maximumAge: 0 }
    );

    const timeoutId = setTimeout(() => {
      if (bestCoords && bestAccuracy <= MAX_APPROXIMATE_ACCURACY_METERS) {
        finish(resolve, bestCoords);
        return;
      }
      const message = Number.isFinite(bestAccuracy)
        ? `Vị trí thiết bị chưa đủ chính xác (sai số khoảng ${Math.ceil(bestAccuracy).toLocaleString('vi-VN')} m). Hãy tìm địa chỉ hoặc chọn ghim trên bản đồ.`
        : 'Chưa lấy được vị trí. Hãy bật định vị trên thiết bị hoặc chọn địa điểm trên bản đồ.';
      finish(reject, new Error(message));
    }, POSITION_TIMEOUT_MS);

    signal.addEventListener('abort', abort, { once: true });
  });
}
