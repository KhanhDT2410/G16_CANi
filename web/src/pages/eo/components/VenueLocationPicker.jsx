import { useState, useEffect, useRef } from 'react';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import {
  MAX_VENUE_ACCURACY_METERS,
  requestVenuePosition,
} from '../../../features/event-creation/utils/requestVenuePosition';
import {
  searchAddressSuggestions,
  getAddressFromCoordinates,
  getCoordinatesFromAddress,
} from '../utils/geoUtils';
import './VenueLocationPicker.css';

const createGooglePinIcon = () =>
  L.divIcon({
    className: 'google-custom-pin',
    html: `
      <div style="position: relative; width: 34px; height: 34px; transform: translate(-50%, -100%); cursor: grab;">
        <svg viewBox="0 0 24 24" width="34" height="34" fill="#ea4335" style="filter: drop-shadow(0 3px 6px rgba(0,0,0,0.4));">
          <path d="M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm0 9.5c-1.38 0-2.5-1.12-2.5-2.5s1.12-2.5 2.5-2.5 2.5 1.12 2.5 2.5-1.12 2.5-2.5 2.5z"/>
        </svg>
        <div style="position: absolute; top: 7px; left: 12px; width: 10px; height: 10px; background: white; border-radius: 50%; box-shadow: 0 1px 2px rgba(0,0,0,0.3);"></div>
      </div>
    `,
    iconSize: [0, 0],
    iconAnchor: [0, 0],
  });

/**
 * VenueLocationPicker
 * Hỗ trợ:
 * 1. Gõ tìm kiếm + Autocomplete gợi ý, hoặc nhấn Enter / rời ô để định vị thẳng trên bản đồ
 * 2. Kéo thả ghim hoặc click bản đồ: cập nhật tọa độ & vùng lương tức thì + tự dò địa chỉ
 * 3. Định vị GPS thiết bị hiện tại
 */
export const VenueLocationPicker = ({
  address = '',
  lat = 10.7328,
  lng = 106.7214,
  coordinatesConfirmed = false,
  onLocationChange,
  required = true,
}) => {
  const [inputValue, setInputValue] = useState(address);
  const [suggestions, setSuggestions] = useState([]);
  const [showSuggestions, setShowSuggestions] = useState(false);
  const [searching, setSearching] = useState(false);
  const [resolvingAddress, setResolvingAddress] = useState(false);
  const [resolveFailedHint, setResolveFailedHint] = useState(false);
  const [mapType, setMapType] = useState('roadmap');
  const [locating, setLocating] = useState(false);
  const [locationError, setLocationError] = useState('');
  const [locationHint, setLocationHint] = useState('');

  const mapContainerRef = useRef(null);
  const mapInstanceRef = useRef(null);
  const markerRef = useRef(null);
  const tileLayerRef = useRef(null);
  const dropdownRef = useRef(null);
  const debounceTimerRef = useRef(null);
  const blurTimerRef = useRef(null);
  const locationControllerRef = useRef(null);
  const selectionVersionRef = useRef(0);

  // Giữ ref mới nhất để handler Leaflet không bị stale closure & không gọi setState cha trong updater
  const onLocationChangeRef = useRef(onLocationChange);
  onLocationChangeRef.current = onLocationChange;
  const latRef = useRef(lat);
  latRef.current = lat;
  const lngRef = useRef(lng);
  lngRef.current = lng;
  const lastGeocodedTextRef = useRef('');

  const createGoogleTileLayer = (type) => {
    const lyrs = type === 'satellite' ? 'y' : 'm';
    return L.tileLayer(`https://mt{s}.google.com/vt/lyrs=${lyrs}&x={x}&y={y}&z={z}&hl=vi`, {
      subdomains: ['0', '1', '2', '3'],
      maxZoom: 20,
      attribution: '© Google Maps',
    });
  };

  useEffect(() => {
    setInputValue(address);
    lastGeocodedTextRef.current = coordinatesConfirmed ? address.trim() : '';
    if (address) setResolveFailedHint(false);
  }, [address, coordinatesConfirmed]);

  const startLocationSelection = () => {
    locationControllerRef.current?.abort();
    locationControllerRef.current = null;
    clearTimeout(debounceTimerRef.current);
    clearTimeout(blurTimerRef.current);
    setLocating(false);
    setLocationError('');
    setLocationHint('');
    setSearching(false);
    setResolvingAddress(false);
    setResolveFailedHint(false);
    return ++selectionVersionRef.current;
  };

  const handlePinMoved = async (newLat, newLng, initialHint = '') => {
    const version = startLocationSelection();
    if (initialHint) setLocationHint(initialHint);
    const coordinateLabel = `Vị trí GPS (${newLat.toFixed(5)}, ${newLng.toFixed(5)})`;
    lastGeocodedTextRef.current = coordinateLabel;
    setInputValue(coordinateLabel);
    setShowSuggestions(false);
    setSuggestions([]);
    setResolvingAddress(true);

    // Cập nhật tọa độ ngay lập tức để Sidebar hiển thị đúng Vùng lương & GPS
    onLocationChangeRef.current?.({
      address: coordinateLabel,
      lat: newLat,
      lng: newLng,
      locationPicked: true,
      venueCoordinatesConfirmed: true,
    });

    const resolvedAddress = await getAddressFromCoordinates(newLat, newLng);
    if (version !== selectionVersionRef.current) return;
    setResolvingAddress(false);

    if (resolvedAddress) {
      lastGeocodedTextRef.current = resolvedAddress;
      setInputValue(resolvedAddress);
      onLocationChangeRef.current?.({
        address: resolvedAddress,
        lat: newLat,
        lng: newLng,
        locationPicked: true,
        venueCoordinatesConfirmed: true,
      });
    } else {
      setResolveFailedHint(true);
    }
  };

  const handlePinMovedRef = useRef(handlePinMoved);
  handlePinMovedRef.current = handlePinMoved;

  useEffect(() => {
    if (!mapContainerRef.current) return;

    const initialLat = latRef.current ?? 10.7328;
    const initialLng = lngRef.current ?? 106.7214;

    const map = L.map(mapContainerRef.current, {
      center: [initialLat, initialLng],
      zoom: 15,
      zoomControl: true,
      attributionControl: false,
    });

    const initialTileLayer = createGoogleTileLayer('roadmap');
    initialTileLayer.addTo(map);
    tileLayerRef.current = initialTileLayer;

    const marker = L.marker([initialLat, initialLng], {
      icon: createGooglePinIcon(),
      draggable: true,
      autoPan: true,
    }).addTo(map);

    marker.bindTooltip('Địa điểm tổ chức (Kéo để chỉnh)', {
      permanent: false,
      direction: 'top',
      offset: [0, -32],
    });

    marker.on('dragend', () => {
      const position = marker.getLatLng();
      const newLat = parseFloat(position.lat.toFixed(6));
      const newLng = parseFloat(position.lng.toFixed(6));
      handlePinMovedRef.current(newLat, newLng);
    });

    map.on('click', (e) => {
      const clickedLat = parseFloat(e.latlng.lat.toFixed(6));
      const clickedLng = parseFloat(e.latlng.lng.toFixed(6));
      marker.setLatLng([clickedLat, clickedLng]);
      map.panTo([clickedLat, clickedLng]);
      handlePinMovedRef.current(clickedLat, clickedLng);
    });

    mapInstanceRef.current = map;
    markerRef.current = marker;

    const resizeTimer = setTimeout(() => {
      map.invalidateSize();
    }, 250);

    return () => {
      clearTimeout(resizeTimer);
      clearTimeout(debounceTimerRef.current);
      clearTimeout(blurTimerRef.current);
      locationControllerRef.current?.abort();
      selectionVersionRef.current += 1;
      map.remove();
      mapInstanceRef.current = null;
      markerRef.current = null;
      tileLayerRef.current = null;
    };
  }, []);

  const handleSwitchMapType = (type) => {
    if (type === mapType || !mapInstanceRef.current) return;
    setMapType(type);

    if (tileLayerRef.current) {
      mapInstanceRef.current.removeLayer(tileLayerRef.current);
    }
    const newLayer = createGoogleTileLayer(type);
    newLayer.addTo(mapInstanceRef.current);
    tileLayerRef.current = newLayer;
  };

  useEffect(() => {
    if (mapInstanceRef.current && markerRef.current && lat != null && lng != null) {
      const currentPos = markerRef.current.getLatLng();
      if (Math.abs(currentPos.lat - lat) > 0.0001 || Math.abs(currentPos.lng - lng) > 0.0001) {
        markerRef.current.setLatLng([lat, lng]);
        mapInstanceRef.current.setView([lat, lng], 15, { animate: true });
        setTimeout(() => {
          mapInstanceRef.current?.invalidateSize();
        }, 120);
      }
    }
  }, [lat, lng]);

  useEffect(() => {
    const handleClickOutside = (e) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target)) {
        setShowSuggestions(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const movePinToCoords = (targetLat, targetLng, zoom = 16) => {
    if (mapInstanceRef.current && markerRef.current) {
      markerRef.current.setLatLng([targetLat, targetLng]);
      mapInstanceRef.current.setView([targetLat, targetLng], zoom, { animate: true });
    }
  };

  const commitTypedAddress = async (rawText) => {
    const trimmed = String(rawText || '').trim();
    if (trimmed.length < 3 || trimmed === lastGeocodedTextRef.current) return;
    const version = startLocationSelection();
    setSearching(true);
    try {
      const coords = await getCoordinatesFromAddress(trimmed);
      if (version === selectionVersionRef.current && coords?.lat != null && coords?.lng != null) {
        lastGeocodedTextRef.current = trimmed;
        movePinToCoords(coords.lat, coords.lng, 16);
        onLocationChangeRef.current?.({
          address: trimmed,
          lat: coords.lat,
          lng: coords.lng,
          locationPicked: true,
          venueCoordinatesConfirmed: true,
        });
      }
    } finally {
      if (version === selectionVersionRef.current) setSearching(false);
    }
  };

  const handleInputChange = (e) => {
    const val = e.target.value;
    const version = startLocationSelection();
    lastGeocodedTextRef.current = '';
    setInputValue(val);

    onLocationChangeRef.current?.({
      address: val,
      lat: latRef.current,
      lng: lngRef.current,
      locationPicked: Boolean(val.trim()),
      venueCoordinatesConfirmed: false,
    });

    if (!val || val.trim().length < 2) {
      setSuggestions([]);
      setShowSuggestions(false);
      setSearching(false);
      return;
    }

    setSearching(true);
    debounceTimerRef.current = setTimeout(async () => {
      try {
        const results = await searchAddressSuggestions(val);
        if (version !== selectionVersionRef.current) return;
        setSuggestions(results);
        setShowSuggestions(results.length > 0);
      } finally {
        if (version === selectionVersionRef.current) setSearching(false);
      }
    }, 400);
  };

  const handleSelectSuggestion = (item) => {
    startLocationSelection();
    lastGeocodedTextRef.current = item.displayName;
    setInputValue(item.displayName);
    setShowSuggestions(false);
    setSuggestions([]);

    onLocationChangeRef.current?.({
      address: item.displayName,
      lat: item.lat,
      lng: item.lng,
      locationPicked: true,
      venueCoordinatesConfirmed: true,
    });

    movePinToCoords(item.lat, item.lng, 16);
  };

  const handleClear = () => {
    startLocationSelection();
    lastGeocodedTextRef.current = '';
    setInputValue('');
    setSuggestions([]);
    setShowSuggestions(false);
    onLocationChangeRef.current?.({
      address: '',
      lat: latRef.current,
      lng: lngRef.current,
      locationPicked: false,
      venueCoordinatesConfirmed: false,
    });
  };

  const handleLocateButtonClick = async () => {
    startLocationSelection();
    setShowSuggestions(false);
    setSuggestions([]);

    if (!window.isSecureContext || !navigator.geolocation) {
      setLocationError(window.isSecureContext
        ? 'Trình duyệt không hỗ trợ định vị. Hãy tìm địa chỉ hoặc chọn ghim trên bản đồ.'
        : 'Hãy mở trang bằng HTTPS để dùng định vị hoặc chọn địa điểm trên bản đồ.');
      return;
    }

    const controller = new AbortController();
    locationControllerRef.current = controller;
    setLocating(true);
    let coords;
    try {
      coords = await requestVenuePosition(navigator.geolocation, controller.signal);
    } catch (error) {
      if (!controller.signal.aborted) {
        const msg = error?.code === 1
          ? 'Bạn chưa cấp quyền truy cập vị trí cho trình duyệt.'
          : error?.code === 3
            ? 'Không thể lấy vị trí do quá thời gian chờ. Vui lòng thử lại.'
            : 'Không thể xác định vị trí hiện tại. Vui lòng tìm địa chỉ hoặc chọn ghim trên bản đồ.';
        setLocationError(msg);
      }
      return;
    } finally {
      if (locationControllerRef.current === controller && !controller.signal.aborted) {
        locationControllerRef.current = null;
        setLocating(false);
      }
    }

    if (controller.signal.aborted) return;
    const newLat = Number(coords.latitude.toFixed(6));
    const newLng = Number(coords.longitude.toFixed(6));
    const isApproximate = Number.isFinite(coords.accuracy) && coords.accuracy > MAX_VENUE_ACCURACY_METERS;
    const approximateHint = isApproximate
      ? `Đã đưa ghim về gần vị trí hiện tại (sai số khoảng ${Math.ceil(coords.accuracy).toLocaleString('vi-VN')} m). Hãy kéo ghim hoặc tìm địa chỉ để chốt đúng địa điểm.`
      : '';
    movePinToCoords(newLat, newLng, isApproximate ? 15 : 16);
    handlePinMoved(newLat, newLng, approximateHint);
  };

  return (
    <div className="venue-picker-container" ref={dropdownRef}>
      <div className="venue-picker-header">
        <label className="venue-picker-label">
          Địa Điểm Tổ Chức Sự Kiện {required && '*'}
        </label>
        <button
          type="button"
          className="venue-picker-gps-btn"
          onClick={handleLocateButtonClick}
          disabled={locating}
          title="Lấy vị trí hiện tại của thiết bị"
        >
          <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2">
            <circle cx="12" cy="12" r="7" />
            <line x1="12" y1="1" x2="12" y2="4" />
            <line x1="12" y1="20" x2="12" y2="23" />
            <line x1="1" y1="12" x2="4" y2="12" />
            <line x1="20" y1="12" x2="23" y2="12" />
          </svg>
          <span>{locating ? 'Đang định vị...' : 'Vị trí hiện tại'}</span>
        </button>
      </div>

      <div className="venue-search-wrapper">
        <div className="venue-search-input-box">
          <span className="venue-search-icon">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2">
              <circle cx="11" cy="11" r="8" />
              <line x1="21" y1="21" x2="16.65" y2="16.65" />
            </svg>
          </span>
          <input
            type="text"
            required={required}
            className="venue-search-input"
            placeholder="Nhập tên địa điểm (VD: ĐH Sư phạm Hà Nội, SECC, Mỹ Đình...)"
            value={inputValue}
            onChange={handleInputChange}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                e.preventDefault();
                setShowSuggestions(false);
                commitTypedAddress(inputValue);
              }
            }}
            onBlur={() => {
              clearTimeout(blurTimerRef.current);
              blurTimerRef.current = setTimeout(() => {
                if (!showSuggestions && inputValue.trim().length >= 3) {
                  commitTypedAddress(inputValue);
                }
              }, 180);
            }}
            onFocus={() => {
              if (suggestions.length > 0) setShowSuggestions(true);
            }}
          />
          {inputValue && (
            <button
              type="button"
              className="venue-search-clear-btn"
              onClick={handleClear}
              title="Xóa tìm kiếm"
            >
              ✕
            </button>
          )}
        </div>

        {showSuggestions && (
          <ul className="venue-suggestions-dropdown">
            {searching && (
              <li className="venue-suggestions-loading">Đang tìm kiếm địa điểm...</li>
            )}
            {suggestions.map((item, index) => (
              <li
                key={`${item.lat}-${item.lng}-${index}`}
                className="venue-suggestion-item"
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => handleSelectSuggestion(item)}
              >
                <span className="venue-suggestion-icon">
                  <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor">
                    <path d="M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm0 9.5c-1.38 0-2.5-1.12-2.5-2.5s1.12-2.5 2.5-2.5 2.5 1.12 2.5 2.5-1.12 2.5-2.5 2.5z" />
                  </svg>
                </span>
                <div className="venue-suggestion-content">
                  <span className="venue-suggestion-name">{item.name}</span>
                  <span className="venue-suggestion-address">{item.displayName}</span>
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>

      <div className="venue-map-wrapper">
        <div ref={mapContainerRef} className="venue-leaflet-map" />

        <div className="venue-map-type-toggle">
          <button
            type="button"
            className={`venue-map-type-btn ${mapType === 'roadmap' ? 'active' : ''}`}
            onClick={() => handleSwitchMapType('roadmap')}
            title="Xem bản đồ đường sá Google Maps"
          >
            Bản đồ
          </button>
          <button
            type="button"
            className={`venue-map-type-btn ${mapType === 'satellite' ? 'active' : ''}`}
            onClick={() => handleSwitchMapType('satellite')}
            title="Xem hình ảnh vệ tinh thực tế Google Earth"
          >
            Vệ tinh
          </button>
        </div>
      </div>

      <div className="venue-map-statusbar">
        <span className="venue-map-hint" role="status">
          {locationError || (resolvingAddress
            ? 'Đang tự động dò tên đường từ vị trí ghim...'
            : locationHint
            ? locationHint
            : resolveFailedHint
            ? 'Đã ghim tọa độ mới — chưa dò được tên đường tự động, vui lòng gõ địa chỉ cụ thể ở trên.'
            : 'Click hoặc kéo thả ghim trên bản đồ (hoặc nhấn Enter khi gõ) để định vị đúng địa điểm')}
        </span>
        <span className="venue-gps-badge" title={inputValue || 'Địa chỉ tổ chức cụ thể'}>
          {inputValue
            ? `Địa chỉ: ${inputValue.slice(0, 38)}${inputValue.length > 38 ? '...' : ''}`
            : lat && lng
            ? `Ghim: ${Number(lat).toFixed(4)}, ${Number(lng).toFixed(4)}`
            : 'Chưa nhập địa chỉ cụ thể'}
        </span>
      </div>
    </div>
  );
};

export default VenueLocationPicker;
