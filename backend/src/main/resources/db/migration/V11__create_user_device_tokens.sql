-- ====================================================================
-- Migration V11: Tạo bảng quản lý FCM Device Tokens cho đa thiết bị
-- Hỗ trợ Push Notification thời gian thực cho Mobile Worker
-- ====================================================================

CREATE TABLE IF NOT EXISTS public.user_device_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    fcm_token VARCHAR(512) NOT NULL UNIQUE,
    device_type VARCHAR(20) NOT NULL, -- 'ANDROID', 'IOS', 'WEB'
    device_name VARCHAR(100),         -- vd: 'Samsung Galaxy S23', 'Pixel 8'
    app_version VARCHAR(30),          -- vd: '1.0.0+1'
    is_active BOOLEAN NOT NULL DEFAULT true,
    last_active_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Chỉ mục tối ưu hóa tốc độ truy vấn khi bắn push notification theo user_id
CREATE INDEX IF NOT EXISTS idx_user_device_tokens_user_id ON public.user_device_tokens(user_id) WHERE is_active = true;
CREATE INDEX IF NOT EXISTS idx_user_device_tokens_fcm_token ON public.user_device_tokens(fcm_token);
