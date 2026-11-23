const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

/**
 * Chuẩn hóa URL ảnh / tệp đính kèm (URL tương đối /api/..., Data URI, Blob, hoặc URL tuyệt đối).
 */
export function resolveAssetUrl(url) {
  if (!url) return '';
  const trimmed = String(url).trim();
  if (
    trimmed.startsWith('data:') ||
    trimmed.startsWith('http://') ||
    trimmed.startsWith('https://') ||
    trimmed.startsWith('blob:')
  ) {
    return trimmed;
  }
  if (trimmed.startsWith('/api/')) {
    return `${API_BASE_URL}${trimmed}`;
  }
  return `${API_BASE_URL}/${trimmed.replace(/^\/+/, '')}`;
}

/**
 * Kiểm tra xem đường dẫn có phải là hình ảnh hay không.
 */
export function isImageUrl(url) {
  if (!url) return false;
  const lower = String(url).toLowerCase().split('?')[0];
  if (lower.startsWith('data:image/')) return true;
  return (
    lower.endsWith('.png') ||
    lower.endsWith('.jpg') ||
    lower.endsWith('.jpeg') ||
    lower.endsWith('.webp') ||
    lower.endsWith('.gif') ||
    lower.endsWith('.svg')
  );
}

/**
 * Lấy định dạng mở rộng hoặc loại tệp để hiển thị badge.
 */
export function getFileExtension(url) {
  if (!url) return 'TỆP';
  if (String(url).startsWith('data:image/')) return 'ẢNH';
  if (String(url).startsWith('data:application/pdf')) return 'PDF';
  const clean = String(url).split('?')[0].split('#')[0];
  const parts = clean.split('.');
  if (parts.length > 1) {
    const ext = parts.pop().toUpperCase();
    if (ext.length <= 5) return ext;
  }
  return 'TỆP';
}

/**
 * Trích xuất tên tệp từ URL hoặc chuỗi đường dẫn.
 */
export function getFileName(url) {
  if (!url) return 'Tệp đính kèm';
  if (String(url).startsWith('data:')) return 'Tệp đính kèm';
  const clean = String(url).split('?')[0].split('#')[0];
  const parts = clean.split('/');
  return decodeURIComponent(parts.pop() || 'Tệp đính kèm');
}
