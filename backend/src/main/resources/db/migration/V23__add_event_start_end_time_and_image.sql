-- V22: Bổ sung thời gian bắt đầu, kết thúc và ảnh banner cho sự kiện (events)
ALTER TABLE events ADD COLUMN IF NOT EXISTS start_time TIMESTAMPTZ;
ALTER TABLE events ADD COLUMN IF NOT EXISTS end_time TIMESTAMPTZ;
ALTER TABLE events ADD COLUMN IF NOT EXISTS image_url TEXT;

