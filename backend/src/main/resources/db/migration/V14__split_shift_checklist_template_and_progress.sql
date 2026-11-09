-- ====================================================================
-- Migration V12: Tách Checklist Template (Shift) vs Tiến Độ Thực Hiện (ShiftAssignment)
-- UC-45: Độc lập hóa tiến độ đầu việc của từng Worker, chống ghi đè chéo
-- ====================================================================

-- 1. Thêm cột checklist_progress vào shift_assignments để lưu tiến độ riêng từng worker
ALTER TABLE shift_assignments
  ADD COLUMN IF NOT EXISTS checklist_progress JSONB DEFAULT NULL;

-- 2. Backfill: Chuẩn hóa dữ liệu shifts.checklist cũ nếu đang lưu mảng string ["A", "B"]
--    Chuyển đổi thành mảng object có id bền vững [{"id": "chk_1", "title": "A"}]
UPDATE shifts
SET checklist = (
  SELECT jsonb_agg(
    jsonb_build_object(
      'id', 'chk_' || (idx)::text,
      'title', elem
    )
  )
  FROM jsonb_array_elements_text(checklist) WITH ORDINALITY AS t(elem, idx)
)
WHERE checklist IS NOT NULL 
  AND jsonb_typeof(checklist) = 'array'
  AND jsonb_array_length(checklist) > 0
  AND jsonb_typeof(checklist->0) = 'string';
