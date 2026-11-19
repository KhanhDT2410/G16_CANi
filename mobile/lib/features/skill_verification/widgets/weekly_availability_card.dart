import 'package:flutter/material.dart';

class TimeSlotConfig {
  final String id;
  String name; // 'Ca Sáng', 'Ca Chiều', 'Ca Tối', 'Cả ngày', 'Tùy chỉnh'
  String startTime;
  String endTime;

  TimeSlotConfig({
    required this.id,
    required this.name,
    required this.startTime,
    required this.endTime,
  });

  TimeSlotConfig copyWith({
    String? id,
    String? name,
    String? startTime,
    String? endTime,
  }) {
    return TimeSlotConfig(
      id: id ?? this.id,
      name: name ?? this.name,
      startTime: startTime ?? this.startTime,
      endTime: endTime ?? this.endTime,
    );
  }
}

class DayScheduleConfig {
  final int dayOfWeek; // 1=T2 ... 6=T7, 0=CN
  final String label;
  bool enabled;
  List<TimeSlotConfig> slots;

  DayScheduleConfig({
    required this.dayOfWeek,
    required this.label,
    this.enabled = false,
    List<TimeSlotConfig>? slots,
  }) : slots = slots ?? [];
}

class WeeklyAvailabilityCard extends StatelessWidget {
  final List<DayScheduleConfig> days;
  final void Function(String preset) onApplyPreset;
  final void Function(DayScheduleConfig day, TimeSlotConfig slot, bool isStart) onPickTime;
  final void Function(DayScheduleConfig day, String shiftName, String start, String end) onToggleShiftPeriod;
  final void Function(DayScheduleConfig day, TimeSlotConfig slot) onRemoveSlot;
  final void Function(DayScheduleConfig day) onAddCustomSlot;
  final void Function(DayScheduleConfig day, bool enabled) onToggleDay;

  const WeeklyAvailabilityCard({
    super.key,
    required this.days,
    required this.onApplyPreset,
    required this.onPickTime,
    required this.onToggleShiftPeriod,
    required this.onRemoveSlot,
    required this.onAddCustomSlot,
    required this.onToggleDay,
  });

  @override
  Widget build(BuildContext context) {
    final activeCount = days.where((d) => d.enabled && d.slots.isNotEmpty).length;
    final totalSlotsCount = days.where((d) => d.enabled).fold<int>(0, (sum, d) => sum + d.slots.length);

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: const Color(0xFFE2E8F0)),
        boxShadow: const [
          BoxShadow(
            color: Color(0x06000000),
            blurRadius: 10,
            offset: Offset(0, 4),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // ---------------------------------------------------------------
          // 1. HEADER
          // ---------------------------------------------------------------
          Row(
            children: [
              Container(
                padding: const EdgeInsets.all(7),
                decoration: BoxDecoration(
                  color: const Color(0xFFF0FDFA),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: const Icon(Icons.calendar_month_rounded, color: Color(0xFF0D9488), size: 18),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      'Lịch rảnh cố định hàng tuần',
                      style: TextStyle(
                        fontWeight: FontWeight.w800,
                        fontSize: 15,
                        color: Color(0xFF0F172A),
                      ),
                    ),
                    Text(
                      activeCount > 0
                          ? 'Đã bật $activeCount ngày ($totalSlotsCount ca/tuần)'
                          : 'Đang ở chế độ linh hoạt (nhận mọi ca)',
                      style: TextStyle(
                        fontSize: 12,
                        color: activeCount > 0 ? const Color(0xFF0D9488) : const Color(0xFF64748B),
                        fontWeight: activeCount > 0 ? FontWeight.w700 : FontWeight.normal,
                      ),
                    ),
                  ],
                ),
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                decoration: BoxDecoration(
                  color: activeCount > 0 ? const Color(0xFFF0FDFA) : const Color(0xFFF1F5F9),
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(
                    color: activeCount > 0 ? const Color(0xFF99F6E4) : const Color(0xFFCBD5E1),
                  ),
                ),
                child: Text(
                  '$activeCount/7 ngày',
                  style: TextStyle(
                    fontWeight: FontWeight.w800,
                    fontSize: 12,
                    color: activeCount > 0 ? const Color(0xFF0D9488) : const Color(0xFF64748B),
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 14),

          // ---------------------------------------------------------------
          // 2. MẪU LỊCH CHỌN NHANH (PRESETS)
          // ---------------------------------------------------------------
          const Text(
            'Mẫu lịch nhanh:',
            style: TextStyle(fontWeight: FontWeight.w700, fontSize: 12.5, color: Color(0xFF475569)),
          ),
          const SizedBox(height: 6),
          SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            child: Row(
              children: [
                _buildPresetButton(
                  icon: Icons.nights_stay_outlined,
                  label: 'Tối T2-T6 (18h-23h)',
                  onTap: () => onApplyPreset('EVENINGS'),
                ),
                const SizedBox(width: 6),
                _buildPresetButton(
                  icon: Icons.weekend_outlined,
                  label: 'Cuối tuần T7-CN',
                  onTap: () => onApplyPreset('WEEKENDS'),
                ),
                const SizedBox(width: 6),
                _buildPresetButton(
                  icon: Icons.bolt_rounded,
                  label: 'Cả tuần (08h-22h)',
                  onTap: () => onApplyPreset('FULL_WEEK'),
                ),
                const SizedBox(width: 6),
                _buildPresetButton(
                  icon: Icons.clear_all_rounded,
                  label: 'Linh hoạt (Xóa trắng)',
                  onTap: () => onApplyPreset('CLEAR_ALL'),
                  isNeutral: true,
                ),
              ],
            ),
          ),
          const SizedBox(height: 14),
          const Divider(height: 1, color: Color(0xFFF1F5F9)),
          const SizedBox(height: 12),

          // ---------------------------------------------------------------
          // 3. DANH SÁCH 7 NGÀY (HỖ TRỢ NHIỀU CA TRONG NGÀY)
          // ---------------------------------------------------------------
          ...days.map((day) => _buildDayTile(day)),
        ],
      ),
    );
  }

  Widget _buildPresetButton({
    required IconData icon,
    required String label,
    required VoidCallback onTap,
    bool isNeutral = false,
  }) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(8),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
        decoration: BoxDecoration(
          color: isNeutral ? const Color(0xFFF8FAFC) : const Color(0xFFEFF6FF),
          borderRadius: BorderRadius.circular(8),
          border: Border.all(
            color: isNeutral ? const Color(0xFFE2E8F0) : const Color(0xFFBFDBFE),
          ),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              icon,
              size: 14,
              color: isNeutral ? const Color(0xFF64748B) : const Color(0xFF0066FF),
            ),
            const SizedBox(width: 4),
            Text(
              label,
              style: TextStyle(
                fontSize: 11.5,
                fontWeight: FontWeight.w700,
                color: isNeutral ? const Color(0xFF475569) : const Color(0xFF0066FF),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildDayTile(DayScheduleConfig day) {
    String summaryText;
    if (!day.enabled) {
      summaryText = 'Linh hoạt / Bận (Không nhận lịch cố định)';
    } else if (day.slots.isEmpty) {
      summaryText = 'Đang bật - Chưa chọn ca rảnh';
    } else {
      summaryText = '${day.slots.length} ca: ${day.slots.map((s) => '${s.name} (${s.startTime}-${s.endTime})').join(', ')}';
    }

    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      decoration: BoxDecoration(
        color: day.enabled ? const Color(0xFFF8FAFC) : Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: day.enabled ? const Color(0xFF93C5FD) : const Color(0xFFE2E8F0),
          width: day.enabled ? 1.3 : 1.0,
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
            child: Row(
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        day.label,
                        style: TextStyle(
                          fontWeight: FontWeight.w800,
                          fontSize: 13.5,
                          color: day.enabled ? const Color(0xFF0F172A) : const Color(0xFF64748B),
                        ),
                      ),
                      Text(
                        summaryText,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(
                          fontSize: 11.5,
                          color: day.enabled && day.slots.isNotEmpty ? const Color(0xFF0D9488) : const Color(0xFF94A3B8),
                          fontWeight: day.enabled && day.slots.isNotEmpty ? FontWeight.w600 : FontWeight.normal,
                        ),
                      ),
                    ],
                  ),
                ),
                Switch.adaptive(
                  value: day.enabled,
                  activeTrackColor: const Color(0xFF0066FF),
                  onChanged: (val) => onToggleDay(day, val),
                ),
              ],
            ),
          ),
          if (day.enabled) ...[
            const Divider(height: 1, indent: 12, endIndent: 12, color: Color(0xFFE2E8F0)),
            Padding(
              padding: const EdgeInsets.fromLTRB(12, 8, 12, 10),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    'Chọn các ca rảnh trong ngày (có thể chọn nhiều ca):',
                    style: TextStyle(fontSize: 11.5, fontWeight: FontWeight.w600, color: Color(0xFF475569)),
                  ),
                  const SizedBox(height: 6),

                  // CÁC CA ĐƯỢC CHỌN ĐỘC LẬP / ĐỒNG THỜI (MULTI-SELECT CHIPS)
                  SingleChildScrollView(
                    scrollDirection: Axis.horizontal,
                    child: Row(
                      children: [
                        _buildShiftPeriodToggle(day, 'Ca Sáng', '07:00', '12:00'),
                        const SizedBox(width: 4),
                        _buildShiftPeriodToggle(day, 'Ca Chiều', '13:00', '18:00'),
                        const SizedBox(width: 4),
                        _buildShiftPeriodToggle(day, 'Ca Tối', '18:00', '23:00'),
                        const SizedBox(width: 4),
                        _buildShiftPeriodToggle(day, 'Cả ngày', '08:00', '22:00'),
                      ],
                    ),
                  ),

                  // DANH SÁCH CHI TIẾT TỪNG KHUNG GIỜ ĐÃ CHỌN
                  if (day.slots.isNotEmpty) ...[
                    const SizedBox(height: 10),
                    ...day.slots.map((slot) => _buildSlotRow(day, slot)),
                  ],

                  // Nút thêm khung giờ tùy chỉnh
                  Align(
                    alignment: Alignment.centerLeft,
                    child: TextButton.icon(
                      onPressed: () => onAddCustomSlot(day),
                      icon: const Icon(Icons.add_circle_outline_rounded, size: 15, color: Color(0xFF0066FF)),
                      label: const Text(
                        'Thêm khung giờ khác...',
                        style: TextStyle(fontSize: 11.5, fontWeight: FontWeight.w700, color: Color(0xFF0066FF)),
                      ),
                      style: TextButton.styleFrom(
                        padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
                        visualDensity: VisualDensity.compact,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildShiftPeriodToggle(DayScheduleConfig day, String shiftName, String start, String end) {
    final isSelected = day.slots.any((s) => s.startTime == start && s.endTime == end);
    return InkWell(
      onTap: () => onToggleShiftPeriod(day, shiftName, start, end),
      borderRadius: BorderRadius.circular(6),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 5),
        decoration: BoxDecoration(
          color: isSelected ? const Color(0xFF0066FF) : const Color(0xFFF1F5F9),
          borderRadius: BorderRadius.circular(6),
          border: Border.all(
            color: isSelected ? const Color(0xFF0066FF) : const Color(0xFFCBD5E1),
          ),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              isSelected ? Icons.check_circle_rounded : Icons.add_rounded,
              size: 13,
              color: isSelected ? Colors.white : const Color(0xFF64748B),
            ),
            const SizedBox(width: 4),
            Text(
              '$shiftName (${start.substring(0, 2)}-${end.substring(0, 2)}h)',
              style: TextStyle(
                fontSize: 11,
                fontWeight: FontWeight.w700,
                color: isSelected ? Colors.white : const Color(0xFF334155),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildSlotRow(DayScheduleConfig day, TimeSlotConfig slot) {
    return Container(
      margin: const EdgeInsets.only(bottom: 6),
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: const Color(0xFFE2E8F0)),
      ),
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
            decoration: BoxDecoration(
              color: const Color(0xFFEFF6FF),
              borderRadius: BorderRadius.circular(4),
            ),
            child: Text(
              slot.name,
              style: const TextStyle(
                fontSize: 10.5,
                fontWeight: FontWeight.w800,
                color: Color(0xFF0066FF),
              ),
            ),
          ),
          const SizedBox(width: 8),

          // Picker: Giờ bắt đầu
          Expanded(
            child: _buildTimeChip(
              label: 'Từ: ${slot.startTime}',
              onTap: () => onPickTime(day, slot, true),
            ),
          ),
          const Padding(
            padding: EdgeInsets.symmetric(horizontal: 4),
            child: Icon(Icons.arrow_forward_rounded, size: 12, color: Color(0xFF94A3B8)),
          ),
          // Picker: Giờ kết thúc
          Expanded(
            child: _buildTimeChip(
              label: 'Đến: ${slot.endTime}',
              onTap: () => onPickTime(day, slot, false),
            ),
          ),

          // Nút Xóa Slot này
          InkWell(
            onTap: () => onRemoveSlot(day, slot),
            borderRadius: BorderRadius.circular(12),
            child: const Padding(
              padding: EdgeInsets.all(4),
              child: Icon(Icons.close_rounded, size: 16, color: Color(0xFFEF4444)),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildTimeChip({
    required String label,
    required VoidCallback onTap,
  }) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(6),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 5),
        alignment: Alignment.center,
        decoration: BoxDecoration(
          color: const Color(0xFFF8FAFC),
          borderRadius: BorderRadius.circular(6),
          border: Border.all(color: const Color(0xFFCBD5E1)),
        ),
        child: Text(
          label,
          style: const TextStyle(
            fontSize: 11,
            fontWeight: FontWeight.w700,
            color: Color(0xFF0F172A),
          ),
        ),
      ),
    );
  }
}
