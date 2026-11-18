import { apiClient } from '../../../services/api';

/**
 * Notification API Service (SRS UC-117: In-App Notifications)
 * Tương tác với backend endpoint /api/v1/notifications
 */
export const notificationApi = {
  /**
   * Lấy danh sách thông báo của người dùng hiện tại (sắp xếp mới nhất trước)
   */
  getMyNotifications: async () => {
    const response = await apiClient.get('/v1/notifications');
    return response.data?.data || [];
  },

  /**
   * Lấy số lượng thông báo chưa đọc để hiển thị badge quả chuông
   */
  getUnreadCount: async () => {
    const response = await apiClient.get('/v1/notifications/unread-count');
    return response.data?.data ?? 0;
  },

  /**
   * Đánh dấu một thông báo cụ thể là đã đọc
   */
  markAsRead: async (notificationId) => {
    const response = await apiClient.patch(`/v1/notifications/${notificationId}/read`);
    return response.data;
  },

  /**
   * Đánh dấu toàn bộ thông báo là đã đọc
   */
  markAllAsRead: async () => {
    const response = await apiClient.patch('/v1/notifications/read-all');
    return response.data;
  },
};

