const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

/**
 * Chuẩn hóa URL ảnh chứng từ / sự kiện (tương đối /api/..., Data URI, hoặc URL tuyệt đối).
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
 * Tạo URL VietQR Napas 247 chuẩn từ thông tin tài khoản của Worker.
 */
export function buildWorkerVietQrUrl(item, overrideAmount) {
  const bankCode = item?.bankCode ? String(item.bankCode).trim().toUpperCase() : '';
  const accountNumber = item?.bankAccountNumber
    ? String(item.bankAccountNumber).replace(/\s+/g, '')
    : '';
  const accountName = (item?.bankAccountName || item?.workerName || '').trim().toUpperCase();
  const amount = Number(overrideAmount ?? item?.declaredAmount ?? item?.agreedWage ?? item?.agreedAmount ?? 0);

  if (item?.vietQrUrl && overrideAmount == null) {
    return item.vietQrUrl;
  }
  if (item?.workerVietQrUrl && overrideAmount == null) {
    return item.workerVietQrUrl;
  }
  if (!bankCode || !accountNumber) {
    return item?.vietQrUrl || item?.workerVietQrUrl || '';
  }

  const params = [];
  if (Number.isFinite(amount) && amount > 0) {
    params.push(`amount=${Math.round(amount)}`);
  }
  if (accountName) {
    params.push(`accountName=${encodeURIComponent(accountName)}`);
  }
  return `https://img.vietqr.io/image/${bankCode}-${accountNumber}-compact2.png${params.length ? '?' + params.join('&') : ''}`;
}
