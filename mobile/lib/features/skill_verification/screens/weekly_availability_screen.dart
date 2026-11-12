import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/features/job_proposals/data/proposal_repository.dart';
import 'package:cani/features/job_proposals/models/proposal_models.dart';

class WeeklyAvailabilityScreen extends ConsumerStatefulWidget {
  const WeeklyAvailabilityScreen({super.key});

  @override
  ConsumerState<WeeklyAvailabilityScreen> createState() => _WeeklyAvailabilityScreenState();
}

class _DayConfig {
  final int dayOfWeek; // 1=T2 ... 6=T7, 0=CN
  final String label;
  bool enabled = false;
  String startTime;
  String endTime;

  _DayConfig({
    required this.dayOfWeek,
    required this.label,
    this.startTime = '18:00',
    this.endTime = '23:00',
  });
}

class _WeeklyAvailabilityScreenState extends ConsumerState<WeeklyAvailabilityScreen> {
  late List<_DayConfig> _days;
  bool _isLoading = true;
  bool _isSaving = false;

  @override
  void initState() {
    super.initState();
    _initDefaultDays();
    _loadCurrentSchedule();
  }

  void _initDefaultDays() {
    _days = [
      _DayConfig(dayOfWeek: 1, label: 'Thứ Hai'),
      _DayConfig(dayOfWeek: 2, label: 'Thứ Ba'),
      _DayConfig(dayOfWeek: 3, label: 'Thứ Tư'),
      _DayConfig(dayOfWeek: 4, label: 'Thứ Năm'),
      _DayConfig(dayOfWeek: 5, label: 'Thứ Sáu'),
      _DayConfig(dayOfWeek: 6, label: 'Thứ Bảy', startTime: '08:00', endTime: '22:00'),
      _DayConfig(dayOfWeek: 0, label: 'Chủ Nhật', startTime: '08:00', endTime: '22:00'),
    ];
  }

  Future<void> _loadCurrentSchedule() async {
    final slots = await ref.read(proposalRepositoryProvider).fetchAvailability();
    if (!mounted) return;
    setState(() {
      for (final d in _days) {
        d.enabled = false;
      }
      for (final slot in slots) {
        final idx = _days.indexWhere((d) => d.dayOfWeek == slot.dayOfWeek);
        if (idx != -1 && slot.isAvailable) {
          _days[idx].enabled = true;
          _days[idx].startTime = slot.startTime;
          _days[idx].endTime = slot.endTime;
        }
      }
      _isLoading = false;
    });
  }

  void _applyPreset(String type) {
    setState(() {
      if (type == 'EVENINGS') {
        for (final d in _days) {
          if (d.dayOfWeek >= 1 && d.dayOfWeek <= 5) {
            d.enabled = true;
            d.startTime = '18:00';
            d.endTime = '23:00';
          } else {
            d.enabled = false;
          }
        }
      } else if (type == 'WEEKENDS') {
        for (final d in _days) {
          if (d.dayOfWeek == 6 || d.dayOfWeek == 0) {
            d.enabled = true;
            d.startTime = '08:00';
            d.endTime = '22:00';
          } else {
            d.enabled = false;
          }
        }
      } else if (type == 'FULL_WEEK') {
        for (final d in _days) {
          d.enabled = true;
          d.startTime = '08:00';
          d.endTime = '22:00';
        }
      }
    });
  }

  Future<void> _pickTime(_DayConfig day, bool isStart) async {
    final currentStr = isStart ? day.startTime : day.endTime;
    final parts = currentStr.split(':');
    final initial = TimeOfDay(
      hour: int.tryParse(parts[0]) ?? 8,
      minute: parts.length > 1 ? (int.tryParse(parts[1]) ?? 0) : 0,
    );

    final picked = await showTimePicker(
      context: context,
      initialTime: initial,
      helpText: isStart ? 'Chọn giờ bắt đầu rảnh' : 'Chọn giờ kết thúc rảnh',
    );

    if (picked != null) {
      final formatted =
          '${picked.hour.toString().padLeft(2, '0')}:${picked.minute.toString().padLeft(2, '0')}';
      setState(() {
        if (isStart) {
          day.startTime = formatted;
        } else {
          day.endTime = formatted;
        }
      });
    }
  }

  Future<void> _saveSchedule() async {
    setState(() => _isSaving = true);
    final payload = _days
        .where((d) => d.enabled)
        .map(
          (d) => WorkerAvailabilitySlot(
            dayOfWeek: d.dayOfWeek,
            startTime: d.startTime,
            endTime: d.endTime,
            isAvailable: true,
          ),
        )
        .toList();

    await ref.read(proposalRepositoryProvider).updateAvailability(payload);
    if (mounted) {
      setState(() => _isSaving = false);
      Navigator.pop(context, payload);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Đã lưu lịch rảnh cố định (${payload.length} ngày/tuần)!'),
          backgroundColor: const Color(0xFF0D9488),
        ),
      );
    }
  }

  Widget _buildPresetChip(String label, IconData icon, VoidCallback onTap) {
    return ActionChip(
      avatar: Icon(icon, size: 16, color: const Color(0xFF0066FF)),
      label: Text(
        label,
        style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: Color(0xFF1E293B)),
      ),
      backgroundColor: const Color(0xFFEFF6FF),
      side: const BorderSide(color: Color(0xFFBFDBFE)),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
      onPressed: onTap,
    );
  }

  Widget _buildShiftQuickButton(_DayConfig day, String label, String start, String end) {
    final isSelected = day.startTime == start && day.endTime == end;
    return InkWell(
      onTap: () {
        setState(() {
          day.startTime = start;
          day.endTime = end;
        });
      },
      borderRadius: BorderRadius.circular(8),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
        decoration: BoxDecoration(
          color: isSelected ? const Color(0xFF0066FF) : const Color(0xFFF1F5F9),
          borderRadius: BorderRadius.circular(8),
        ),
        child: Text(
          label,
          style: TextStyle(
            fontSize: 11.5,
            fontWeight: FontWeight.w700,
            color: isSelected ? Colors.white : const Color(0xFF475569),
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final activeCount = _days.where((d) => d.enabled).length;

    return Scaffold(
      backgroundColor: const Color(0xFFF8FAFC),
      appBar: AppBar(
        backgroundColor: Colors.white,
        elevation: 0,
        title: const Text(
          'Thiết Lập Lịch Rảnh Tuần',
          style: TextStyle(fontWeight: FontWeight.w800, fontSize: 18, color: Color(0xFF0F172A)),
        ),
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : Column(
              children: [
                Expanded(
                  child: ListView(
                    padding: const EdgeInsets.all(16),
                    children: [
                      // Banner giải thích cơ chế Matching
                      Container(
                        padding: const EdgeInsets.all(14),
                        decoration: BoxDecoration(
                          gradient: const LinearGradient(
                            colors: [Color(0xFF0066FF), Color(0xFF0D9488)],
                          ),
                          borderRadius: BorderRadius.circular(16),
                        ),
                        child: const Row(
                          children: [
                            Icon(Icons.calendar_month_rounded, color: Colors.white, size: 32),
                            SizedBox(width: 12),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    'Lịch Rảnh Cố Định Hàng Tuần (UC-25)',
                                    style: TextStyle(
                                      color: Colors.white,
                                      fontWeight: FontWeight.w800,
                                      fontSize: 14,
                                    ),
                                  ),
                                  SizedBox(height: 4),
                                  Text(
                                    'Hệ thống tự động ưu tiên ghép bạn vào các sự kiện tương lai khớp với khung giờ rảnh bạn đã đăng ký.',
                                    style: TextStyle(color: Colors.white70, fontSize: 12, height: 1.35),
                                  ),
                                ],
                              ),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(height: 16),

                      // Chọn nhanh mẫu lịch rảnh
                      const Text(
                        'Chọn mẫu lịch nhanh (Presets):',
                        style: TextStyle(fontWeight: FontWeight.w700, fontSize: 13, color: Color(0xFF475569)),
                      ),
                      const SizedBox(height: 8),
                      Wrap(
                        spacing: 8,
                        runSpacing: 8,
                        children: [
                          _buildPresetChip(
                            'Tối T2 - T6 (18h-23h)',
                            Icons.nights_stay_outlined,
                            () => _applyPreset('EVENINGS'),
                          ),
                          _buildPresetChip(
                            'Cuối tuần T7 - CN',
                            Icons.weekend_outlined,
                            () => _applyPreset('WEEKENDS'),
                          ),
                          _buildPresetChip(
                            'Rảnh cả tuần (08h-22h)',
                            Icons.bolt_rounded,
                            () => _applyPreset('FULL_WEEK'),
                          ),
                        ],
                      ),
                      const SizedBox(height: 16),

                      // Danh sách 7 ngày trong tuần
                      ..._days.map((day) {
                        return Container(
                          margin: const EdgeInsets.only(bottom: 10),
                          decoration: BoxDecoration(
                            color: Colors.white,
                            borderRadius: BorderRadius.circular(14),
                            border: Border.all(
                              color: day.enabled ? const Color(0xFF93C5FD) : const Color(0xFFE2E8F0),
                              width: day.enabled ? 1.4 : 1.0,
                            ),
                          ),
                          child: Column(
                            children: [
                              SwitchListTile(
                                value: day.enabled,
                                activeThumbColor: const Color(0xFF0066FF),
                                title: Text(
                                  day.label,
                                  style: TextStyle(
                                    fontWeight: FontWeight.w700,
                                    fontSize: 14.5,
                                    color: day.enabled ? const Color(0xFF0F172A) : const Color(0xFF64748B),
                                  ),
                                ),
                                subtitle: Text(
                                  day.enabled
                                      ? 'Rảnh từ ${day.startTime} đến ${day.endTime}'
                                      : 'Bận / Không nhận lịch cố định',
                                  style: TextStyle(
                                    fontSize: 12,
                                    color: day.enabled ? const Color(0xFF0D9488) : const Color(0xFF94A3B8),
                                    fontWeight: day.enabled ? FontWeight.w600 : FontWeight.normal,
                                  ),
                                ),
                                onChanged: (val) => setState(() => day.enabled = val),
                              ),
                              if (day.enabled)
                                Padding(
                                  padding: const EdgeInsets.fromLTRB(16, 0, 16, 14),
                                  child: Column(
                                    crossAxisAlignment: CrossAxisAlignment.start,
                                    children: [
                                      const Divider(height: 12),
                                      Wrap(
                                        spacing: 6,
                                        runSpacing: 6,
                                        children: [
                                          _buildShiftQuickButton(day, 'Ca Sáng (07-12h)', '07:00', '12:00'),
                                          _buildShiftQuickButton(day, 'Ca Chiều (13-18h)', '13:00', '18:00'),
                                          _buildShiftQuickButton(day, 'Ca Tối (18-23h)', '18:00', '23:00'),
                                          _buildShiftQuickButton(day, 'Cả ngày (08-22h)', '08:00', '22:00'),
                                        ],
                                      ),
                                      const SizedBox(height: 10),
                                      Row(
                                        children: [
                                          Expanded(
                                            child: OutlinedButton.icon(
                                              onPressed: () => _pickTime(day, true),
                                              icon: const Icon(Icons.schedule, size: 16),
                                              label: Text('Từ: ${day.startTime}', style: const TextStyle(fontSize: 12.5, fontWeight: FontWeight.w700)),
                                              style: OutlinedButton.styleFrom(
                                                visualDensity: VisualDensity.compact,
                                                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                                              ),
                                            ),
                                          ),
                                          const Padding(
                                            padding: EdgeInsets.symmetric(horizontal: 8),
                                            child: Icon(Icons.arrow_forward, size: 16, color: Color(0xFF64748B)),
                                          ),
                                          Expanded(
                                            child: OutlinedButton.icon(
                                              onPressed: () => _pickTime(day, false),
                                              icon: const Icon(Icons.schedule, size: 16),
                                              label: Text('Đến: ${day.endTime}', style: const TextStyle(fontSize: 12.5, fontWeight: FontWeight.w700)),
                                              style: OutlinedButton.styleFrom(
                                                visualDensity: VisualDensity.compact,
                                                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                                              ),
                                            ),
                                          ),
                                        ],
                                      ),
                                    ],
                                  ),
                                ),
                            ],
                          ),
                        );
                      }),
                    ],
                  ),
                ),

                // Thanh lưu dưới cùng
                Container(
                  padding: const EdgeInsets.fromLTRB(16, 12, 16, 20),
                  decoration: BoxDecoration(
                    color: Colors.white,
                    boxShadow: [
                      BoxShadow(
                        color: Colors.black.withValues(alpha: 0.06),
                        blurRadius: 10,
                        offset: const Offset(0, -3),
                      ),
                    ],
                  ),
                  child: SafeArea(
                    top: false,
                    child: SizedBox(
                      width: double.infinity,
                      height: 48,
                      child: FilledButton.icon(
                        onPressed: _isSaving ? null : _saveSchedule,
                        icon: _isSaving
                            ? const SizedBox(
                                width: 18,
                                height: 18,
                                child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                              )
                            : const Icon(Icons.check_circle_outline),
                        label: Text(
                          _isSaving
                              ? 'Đang lưu lịch rảnh...'
                              : 'Lưu Lịch Rảnh Tuần ($activeCount ngày)',
                          style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 14.5),
                        ),
                        style: FilledButton.styleFrom(
                          backgroundColor: const Color(0xFF0066FF),
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                        ),
                      ),
                    ),
                  ),
                ),
              ],
            ),
    );
  }
}

