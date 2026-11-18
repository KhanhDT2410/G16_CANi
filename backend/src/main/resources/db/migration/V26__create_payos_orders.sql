-- V26: Tạo bảng lưu trữ thông tin đơn hàng thanh toán PayOS bền vững (BUG-A Fix)
-- Đảm bảo đơn hàng được lưu vào cơ sở dữ liệu thay vì lưu tạm trên bộ nhớ in-memory
CREATE TABLE IF NOT EXISTS payos_orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_code BIGINT NOT NULL UNIQUE,
    eo_id UUID NOT NULL,
    plan_type VARCHAR(50) NOT NULL,
    amount NUMERIC(15, 2) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_payos_orders_eo_id ON payos_orders(eo_id);
CREATE INDEX IF NOT EXISTS idx_payos_orders_order_code ON payos_orders(order_code);

