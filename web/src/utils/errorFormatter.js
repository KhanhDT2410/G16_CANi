const TECHNICAL_ERROR_PATTERN =
  /(axioserror|network error|timeout of \d+ms exceeded|request failed with status code|err_network|ecconnaborted|econnaborted|econnrefused|typeerror|referenceerror|syntaxerror|unexpected token|failed to fetch)/i;

/**
 * Chuẩn hóa lỗi từ Axios, Supabase hoặc JavaScript thành thông báo tiếng Việt thân thiện.
 */
export function getErrorMessage(error, fallback = 'Đã xảy ra lỗi. Vui lòng thử lại sau.') {
  if (!error) return fallback;

  const responseData = error?.response?.data;
  if (responseData) {
    if (typeof responseData.message === 'string' && responseData.message.trim()) {
      const msg = responseData.message.trim();
      if (!TECHNICAL_ERROR_PATTERN.test(msg)) {
        return msg;
      }
    }
    if (responseData.data && typeof responseData.data === 'object') {
      const firstFieldError = Object.values(responseData.data).find(
        (v) => typeof v === 'string' && v.trim()
      );
      if (firstFieldError) {
        return firstFieldError.trim();
      }
    }
  }

  const code = error?.code;
  const rawMessage = typeof error === 'string' ? error : error?.message || '';

  if (code === 'ERR_NETWORK' || /network error|failed to fetch/i.test(rawMessage)) {
    return 'Không thể kết nối đến máy chủ. Vui lòng kiểm tra kết nối mạng.';
  }
  if (code === 'ECONNABORTED' || /timeout/i.test(rawMessage)) {
    return 'Yêu cầu quá thời gian chờ. Vui lòng thử lại.';
  }
  if (code === 'ERR_CANCELED') {
    return 'Yêu cầu đã được hủy.';
  }

  const status = error?.response?.status;
  if (status === 401) {
    return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.';
  }
  if (status === 403) {
    return 'Bạn không có quyền thực hiện thao tác này.';
  }
  if (status === 404) {
    return 'Không tìm thấy dữ liệu yêu cầu.';
  }
  if (status >= 500) {
    return 'Máy chủ đang bận hoặc gặp sự cố. Vui lòng thử lại sau.';
  }

  const trimmed = rawMessage.trim();
  if (!trimmed || TECHNICAL_ERROR_PATTERN.test(trimmed)) {
    return fallback;
  }

  return trimmed;
}

/**
 * Gán thông báo tiếng Việt thân thiện trực tiếp lên đối tượng AxiosError
 * để các hook/component đọc `err.response?.data?.message || err.message` không bị lộ lỗi tiếng Anh thô.
 */
export function normalizeAxiosError(error) {
  if (!error || typeof error !== 'object') return error;
  const friendly = getErrorMessage(error);
  if (!error.originalMessage && error.message) {
    error.originalMessage = error.message;
  }
  error.userMessage = friendly;
  error.message = friendly;
  return error;
}
