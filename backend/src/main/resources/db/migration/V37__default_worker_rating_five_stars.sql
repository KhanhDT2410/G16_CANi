-- =============================================================================
-- Migration V37: Thiết lập rating mặc định cho Worker mới tạo là 5.00 sao (SEP490)
-- =============================================================================

ALTER TABLE public.workers ALTER COLUMN rating_avg SET DEFAULT 5.00;

-- Cập nhật rating 5.00 cho các worker mới chưa có lượt đánh giá nào
UPDATE public.workers
SET rating_avg = 5.00
WHERE (rating_count = 0 OR rating_count IS NULL) AND (rating_avg = 0.00 OR rating_avg IS NULL);
