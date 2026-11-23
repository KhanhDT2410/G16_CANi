import React, { useState, useEffect, useRef } from 'react';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
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
  onLocationChange,
  onGetCurrentLocation,
  locating = false,
  required = true,
}) => {
  const [inputValue, setInputValue] = useState(address);
  const [suggestions, setSuggestions] = useState([]);
  const [showSuggestions, setShowSuggestions] = useState(false);
  const [searching, setSearching] = useState(false);
  const [resolvingAddress, setResolvingAddress] = useState(false);
  const [resolveFailedHint, setResolveFailedHint] = useState(false);
  const [mapType, setMapType] = useState('roadmap');
  const [localLocating, setLocalLocating] = useState(false);

  const mapContainerRef = useRef(null);
  const mapInstanceRef = useRef(null);
  const markerRef = useRef(null);
  const tileLayerRef = useRef(null);
  const dropdownRef = useRef(null);
  const debounceTimerRef = useRef(null);

  // Giữ ref mới nhất để handler Leaflet không bị stale closure & không gọi setState cha trong updater
  const onLocationChangeRef = useRef(onLocationChange);
  onLocationChangeRef.current = onLocationChange;
  const inputValueRef = useRef(inputValue);
  inputValueRef.current = inputValue;
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
    if (address) setResolveFailedHint(false);
  }, [address]);

  const handlePinMoved = async (newLat, newLng) => {
    setResolvingAddress(true);
    setResolveFailedHint(false);

    // Cập nhật tọa độ ngay lập tức để Sidebar hiển thị đúng Vùng lương & GPS
    onLocationChangeRef.current?.({
      address: inputValueRef.current,
      lat: newLat,
      lng: newLng,
      locationPicked: true,
    });

    const resolvedAddress = await getAddressFromCoordinates(newLat, newLng);
    setResolvingAddress(false);

    if (resolvedAddress) {
      lastGeocodedTextRef.current = resolvedAddress;
      setInputValue(resolvedAddress);
      onLocationChangeRef.current?.({
        address: resolvedAddress,
        lat: newLat,
        lng: newLng,
        locationPicked: true,
      });
    } else {
      setResolveFailedHint(true);
    }
  };

  const handlePinMovedRef = useRef(handlePinMoved);
  handlePinMovedRef.current = handlePinMoved;

  useEffect(() => {
    if (!mapContainerRef.current) return;

    const initialLat = latRef.current || 10.7328;
    const initialLng = lngRef.current || 106.7214;

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
    if (mapInstanceRef.current && markerRef.current && lat && lng) {
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
    setSearching(true);
    try {
      const coords = await getCoordinatesFromAddress(trimmed);
      if (coords && coords.lat && coords.lng) {
        lastGeocodedTextRef.current = trimmed;
        movePinToCoords(coords.lat, coords.lng, 16);
        onLocationChangeRef.current?.({
          address: trimmed,
          lat: coords.lat,
          lng: coords.lng,
          locationPicked: true,
        });
      }
    } finally {
      setSearching(false);
    }
  };

  const handleInputChange = (e) => {
    const val = e.target.value;
    setInputValue(val);
    setResolveFailedHint(false);

    onLocationChangeRef.current?.({
      address: val,
      lat: latRef.current,
      lng: lngRef.current,
      locationPicked: Boolean(val.trim()),
    });

    if (debounceTimerRef.current) {
      clearTimeout(debounceTimerRef.current);
    }

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
        setSuggestions(results);
        setShowSuggestions(results.length > 0);
      } finally {
        setSearching(false);
      }
    }, 400);
  };

  const handleSelectSuggestion = (item) => {
    lastGeocodedTextRef.current = item.displayName;
    setInputValue(item.displayName);
    setShowSuggestions(false);
    setSuggestions([]);
    setResolveFailedHint(false);

    onLocationChangeRef.current?.({
      address: item.displayName,
      lat: item.lat,
      lng: item.lng,
      locationPicked: true,
    });

    movePinToCoords(item.lat, item.lng, 16);
  };

  const handleClear = () => {
    lastGeocodedTextRef.current = '';
    setInputValue('');
    setSuggestions([]);
    setShowSuggestions(false);
    setResolveFailedHint(false);
    onLocationChangeRef.current?.({
      address: '',
      lat: latRef.current,
      lng: lngRef.current,
      locationPicked: false,
    });
  };

  const handleLocateButtonClick = () => {
    if (onGetCurrentLocation) {
      onGetCurrentLocation();
      return;
    }
    if (!navigator.geolocation) return;
    setLocalLocating(true);
    const onCoords = (pos) => {
      const newLat = parseFloat(pos.coords.latitude.toFixed(6));
      const newLng = parseFloat(pos.coords.longitude.toFixed(6));
      setLocalLocating(false);
      movePinToCoords(newLat, newLng, 16);
      handlePinMovedRef.current(newLat, newLng);
    };
    navigator.geolocation.getCurrentPosition(
      onCoords,
      (err) => {
        if (err.code === err.TIMEOUT || err.code === err.POSITION_UNAVAILABLE) {
          navigator.geolocation.getCurrentPosition(
            onCoords,
            () => setLocalLocating(false),
            { enableHighAccuracy: false, timeout: 8000, maximumAge: 60000 }
          );
        } else {
          setLocalLocating(false);
        }
      },
      { enableHighAccuracy: true, timeout: 5000, maximumAge: 30000 }
    );
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
          disabled={locating || localLocating}
          title="Định vị tọa độ GPS thiết bị hiện tại"
        >
          <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2">
            <circle cx="12" cy="12" r="7" />
            <line x1="12" y1="1" x2="12" y2="4" />
            <line x1="12" y1="20" x2="12" y2="23" />
            <line x1="1" y1="12" x2="4" y2="12" />
            <line x1="20" y1="12" x2="23" y2="12" />
          </svg>
          <span>{locating || localLocating ? 'Đang định vị...' : 'Vị trí hiện tại'}</span>
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
              setTimeout(() => {
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
        <span className="venue-map-hint">
          {resolvingAddress
            ? 'Đang tự động dò tên đường từ vị trí ghim...'
            : resolveFailedHint
            ? 'Đã ghim tọa độ mới — chưa dò được tên đường tự động, vui lòng gõ địa chỉ cụ thể ở trên.'
            : 'Click hoặc kéo thả ghim trên bản đồ (hoặc nhấn Enter khi gõ) để định vị đúng địa điểm'}
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
