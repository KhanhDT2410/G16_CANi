-- V73: Tạo PostgreSQL Sequence sinh order_code an toàn, chống đụng độ UNIQUE cho PayOS (PAY-001)
CREATE SEQUENCE IF NOT EXISTS payos_order_code_seq
    START WITH 1000000000
    INCREMENT BY 1;

SELECT setval(
    'payos_order_code_seq',
    GREATEST(COALESCE((SELECT MAX(order_code) FROM payos_orders), 1000000000), 1000000000),
    true
);
