-- =============================================================================
-- V41: Đăng ký PostgreSQL Domain Aliases cho Support Ticket, Notification & Dispute
-- và đảm bảo danh mục kỹ năng (skills) luôn có dữ liệu tham chiếu chuẩn.
-- =============================================================================

DO $$ BEGIN
    -- TicketCategory
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'ticketcategory') THEN
        CREATE DOMAIN ticketcategory AS ticket_category;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'TicketCategory') THEN
        CREATE DOMAIN "TicketCategory" AS ticket_category;
    END IF;

    -- TicketPriority
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'ticketpriority') THEN
        CREATE DOMAIN ticketpriority AS ticket_priority;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'TicketPriority') THEN
        CREATE DOMAIN "TicketPriority" AS ticket_priority;
    END IF;

    -- TicketStatus
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'ticketstatus') THEN
        CREATE DOMAIN ticketstatus AS ticket_status;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'TicketStatus') THEN
        CREATE DOMAIN "TicketStatus" AS ticket_status;
    END IF;

    -- NotificationType (Hibernate dùng tên class NotificationType / notificationtype)
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'notificationtype') THEN
        CREATE DOMAIN notificationtype AS notif_type;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'NotificationType') THEN
        CREATE DOMAIN "NotificationType" AS notif_type;
    END IF;

    -- Dispute enums (phòng ngừa Hibernate typed cast khi lưu dispute)
    IF EXISTS (SELECT 1 FROM pg_type WHERE typname = 'dispute_status') THEN
        IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'disputestatus') THEN
            CREATE DOMAIN disputestatus AS dispute_status;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'DisputeStatus') THEN
            CREATE DOMAIN "DisputeStatus" AS dispute_status;
        END IF;
    END IF;

    IF EXISTS (SELECT 1 FROM pg_type WHERE typname = 'dispute_type') THEN
        IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'disputetype') THEN
            CREATE DOMAIN disputetype AS dispute_type;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'DisputeType') THEN
            CREATE DOMAIN "DisputeType" AS dispute_type;
        END IF;
    END IF;

    IF EXISTS (SELECT 1 FROM pg_type WHERE typname = 'dispute_outcome') THEN
        IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'disputeoutcome') THEN
            CREATE DOMAIN disputeoutcome AS dispute_outcome;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'DisputeOutcome') THEN
            CREATE DOMAIN "DisputeOutcome" AS dispute_outcome;
        END IF;
    END IF;
END $$;

-- Đảm bảo bảng skills có đầy đủ danh mục kỹ năng chuẩn ngành sự kiện
INSERT INTO skills (name, category, description, is_active) VALUES
('Phục vụ bàn', 'F&B', 'Phục vụ món ăn và thức uống tại bàn tiệc', true),
('Phụ bếp', 'F&B', 'Sơ chế nguyên liệu và hỗ trợ đầu bếp', true),
('Pha chế (Bartender/Barista)', 'F&B', 'Pha chế đồ uống, cocktail, cà phê', true),
('Thu ngân', 'Operations', 'Vận hành máy POS và thanh toán', true),
('Lễ tân / Check-in', 'Guest Services', 'Đón khách, phát thẻ và hướng dẫn chỗ ngồi', true),
('Điều phối sự kiện', 'Operations', 'Hỗ trợ quản lý timeline và điều phối nhân sự', true),
('Kỹ thuật âm thanh - ánh sáng', 'Technical', 'Vận hành thiết bị AV cơ bản cho sự kiện', true),
('Bảo vệ / An ninh', 'Security', 'Kiểm soát lối ra vào và duy trì trật tự sự kiện', true),
('Hậu cần / Setup sự kiện', 'Logistics', 'Khuân vác, lắp đặt bàn ghế, backdrop, dọn dẹp', true),
('PG / PB', 'Marketing', 'Quảng bá sản phẩm, chụp hình và tương tác với khách', true)
ON CONFLICT (name) DO NOTHING;
