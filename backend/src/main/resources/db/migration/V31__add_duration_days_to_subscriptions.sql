-- V31: Bổ sung cột duration_days cho bảng subscriptions để khớp với entity Subscription và V1
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS duration_days INT NOT NULL DEFAULT 30;
