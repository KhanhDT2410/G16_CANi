import assert from 'node:assert/strict';
import test from 'node:test';
import { requestVenuePosition } from '../src/features/event-creation/utils/requestVenuePosition.js';
import { getAddressFromCoordinates } from '../src/pages/eo/utils/geoUtils.js';

const hanoi = { latitude: 21.0278, longitude: 105.8342, accuracy: 65000 };
const ninhBinh = { latitude: 20.25, longitude: 105.97, accuracy: 35 };

function mockGeolocation(t) {
  let success;
  let error;
  const geolocation = {
    watchPosition: t.mock.fn((onSuccess, onError) => {
      success = onSuccess;
      error = onError;
      return 1;
    }),
    clearWatch: t.mock.fn(),
  };
  return {
    geolocation,
    position: (coords) => success({ coords }),
    deny: () => error({ code: 1 }),
  };
}

test('waits for a precise browser fix after an approximate city location', async (t) => {
  const { geolocation, position } = mockGeolocation(t);
  const result = requestVenuePosition(geolocation, new AbortController().signal);
  assert.deepEqual(geolocation.watchPosition.mock.calls[0].arguments[2], {
    enableHighAccuracy: true,
    maximumAge: 0,
  });

  position(hanoi);
  assert.equal(geolocation.clearWatch.mock.callCount(), 0);
  position(ninhBinh);
  assert.equal(await result, ninhBinh);
  assert.deepEqual(geolocation.clearWatch.mock.calls[0].arguments, [1]);
});

test('reports poor accuracy at timeout without returning the approximate coordinates', async (t) => {
  t.mock.timers.enable({ apis: ['setTimeout'] });
  const { geolocation, position } = mockGeolocation(t);
  const result = requestVenuePosition(geolocation, new AbortController().signal);
  const rejected = assert.rejects(result, /chưa đủ chính xác/);

  position(hanoi);
  t.mock.timers.tick(15000);
  await rejected;
  assert.equal(geolocation.clearWatch.mock.callCount(), 1);
});

test('falls back to best Wi-Fi/desktop fix within approximate threshold at timeout', async (t) => {
  t.mock.timers.enable({ apis: ['setTimeout'] });
  const { geolocation, position } = mockGeolocation(t);
  const coarseWifi = { latitude: 10.76, longitude: 106.66, accuracy: 1200 };
  const betterWifi = { latitude: 10.7626, longitude: 106.6602, accuracy: 381 };

  const result = requestVenuePosition(geolocation, new AbortController().signal);
  position(coarseWifi);
  position(betterWifi);
  assert.equal(geolocation.clearWatch.mock.callCount(), 0);

  t.mock.timers.tick(10000);
  assert.equal(await result, betterWifi);
  assert.equal(geolocation.clearWatch.mock.callCount(), 1);
});

test('stops watching when location permission is denied or the user selects a different venue', async (t) => {
  const { geolocation, deny } = mockGeolocation(t);
  const controller = new AbortController();

  const denied = requestVenuePosition(geolocation, controller.signal);
  deny();
  await assert.rejects(denied, /cấp quyền vị trí/);

  const cancelled = requestVenuePosition(geolocation, controller.signal);
  controller.abort();
  await assert.rejects(cancelled, { name: 'AbortError' });
  assert.equal(geolocation.clearWatch.mock.callCount(), 2);
});

test('uses a coordinate-based address when BigDataCloud returns an IP-based city', async (t) => {
  t.mock.method(globalThis, 'fetch', async (url) => {
    const endpoint = new URL(url);
    if (endpoint.hostname === 'api.bigdatacloud.net') {
      return Response.json({ lookupSource: 'ipGeolocation', city: 'Hà Nội' });
    }

    assert.equal(endpoint.searchParams.get('lat'), String(ninhBinh.latitude));
    assert.equal(endpoint.searchParams.get('lon'), String(ninhBinh.longitude));
    return Response.json({ display_name: 'Hoa Lư, Ninh Bình' });
  });

  assert.equal(await getAddressFromCoordinates(ninhBinh.latitude, ninhBinh.longitude), 'Hoa Lư, Ninh Bình');
});
