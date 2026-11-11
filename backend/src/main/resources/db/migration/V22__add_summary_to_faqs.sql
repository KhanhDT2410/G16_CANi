-- V22: Thêm cột summary (Tóm tắt câu hỏi thường gặp) cho bảng faqs (SRS UC-110 / ADM-08)

ALTER TABLE faqs ADD COLUMN IF NOT EXISTS summary TEXT;

