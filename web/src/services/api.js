import axios from 'axios';
import { normalizeAxiosError } from '../utils/errorFormatter';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

export const apiClient = axios.create({
  baseURL: `${API_BASE_URL}/api`,
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Quản lý hàng đợi khi đang làm mới token (tránh gọi refresh nhiều lần đồng thời)
let isRefreshing = false;
let failedQueue = [];

const processQueue = (error, token = null) => {
  failedQueue.forEach((prom) => {
    if (error) {
      prom.reject(error);
    } else {
      prom.resolve(token);
    }
  });
  failedQueue = [];
};

const handleForceLogout = () => {
  localStorage.removeItem('cani_token');
  localStorage.removeItem('cani_refresh_token');
  localStorage.removeItem('cani_user');
  localStorage.removeItem('cani_eo_avatar');
  const pathname = window.location.pathname;
  const isPublicPath =
    pathname === '/' ||
    pathname === '' ||
    pathname.startsWith('/login') ||
    pathname.startsWith('/register') ||
    pathname.startsWith('/support') ||
    pathname.startsWith('/account-locked') ||
    pathname.startsWith('/auth');

  if (!isPublicPath) {
    window.location.href = '/login';
  }
};

// Request interceptor: tự động đính kèm Bearer token từ localStorage
apiClient.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('cani_token');
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Response interceptor: Tự động làm mới token ngầm (Silent Refresh) khi gặp lỗi 401
apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;

    // Nếu không phải lỗi 401 hoặc không có request config, chuẩn hóa thông báo lỗi tiếng Việt
    if (error.response?.status !== 401 || !originalRequest) {
      return Promise.reject(normalizeAxiosError(error));
    }

    // Nếu chính endpoint auth hoặc request đã retry 1 lần mà vẫn 401 -> force logout
    if (
      originalRequest.url?.includes('/auth/refresh') ||
      originalRequest.url?.includes('/auth/login') ||
      originalRequest._retry
    ) {
      handleForceLogout();
      return Promise.reject(normalizeAxiosError(error));
    }

    const refreshToken = localStorage.getItem('cani_refresh_token');
    if (!refreshToken) {
      handleForceLogout();
      return Promise.reject(normalizeAxiosError(error));
    }

    // Nếu đang có 1 request khác tiến hành refresh token, xếp request này vào hàng đợi
    if (isRefreshing) {
      return new Promise((resolve, reject) => {
        failedQueue.push({ resolve, reject });
      })
        .then((token) => {
          originalRequest.headers.Authorization = `Bearer ${token}`;
          return apiClient(originalRequest);
        })
        .catch((err) => Promise.reject(err));
    }

    originalRequest._retry = true;
    isRefreshing = true;

    try {
      // Dùng axios cơ bản độc lập (không qua interceptor của apiClient) để gọi API refresh
      const res = await axios.post(`${API_BASE_URL}/api/auth/refresh`, {
        refreshToken,
      });

      const { token: newToken, refreshToken: newRefreshToken, user } = res.data?.data || {};

      if (!newToken) {
        throw new Error('Không nhận được token mới từ hệ thống');
      }

      localStorage.setItem('cani_token', newToken);
      if (newRefreshToken) {
        localStorage.setItem('cani_refresh_token', newRefreshToken);
      }
      if (user) {
        localStorage.setItem('cani_user', JSON.stringify(user));
      }

      apiClient.defaults.headers.common.Authorization = `Bearer ${newToken}`;
      originalRequest.headers.Authorization = `Bearer ${newToken}`;

      processQueue(null, newToken);
      return apiClient(originalRequest);
    } catch (refreshErr) {
      processQueue(refreshErr, null);
      handleForceLogout();
      return Promise.reject(normalizeAxiosError(refreshErr));
    } finally {
      isRefreshing = false;
    }
  }
);
