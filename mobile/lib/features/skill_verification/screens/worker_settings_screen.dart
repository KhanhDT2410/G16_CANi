import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/features/job_proposals/data/proposal_repository.dart';
import 'package:cani/features/job_proposals/models/proposal_models.dart';
import 'package:cani/features/job_radar/providers/job_radar_provider.dart';
import 'package:cani/features/skill_verification/widgets/job_preferences_card.dart';
import 'package:cani/features/skill_verification/widgets/weekly_availability_card.dart';

/// Màn hình Hợp nhất: Tiêu chí Tìm việc & Lịch rảnh nhận ca (Hỗ trợ nhiều ca trong ngày)
class WorkerSettingsScreen extends ConsumerStatefulWidget {
  const WorkerSettingsScreen({super.key});

  @override
  ConsumerState<WorkerSettingsScreen> createState() => _WorkerSettingsScreenState();
}

class _WorkerSettingsScreenState extends ConsumerState<WorkerSettingsScreen> {
  // State: Tiêu chí tìm việc (Preferences)
  double _maxDistanceKm = 15.0;
  double _minWage = 35000; // đ/giờ
  bool _isWagePerShift = false; // false: đ/giờ, true: đ/ca (4h)
  String _baseAddress = 'TP. Hồ Chí Minh';
  double? _baseLat;
  double? _baseLng;

  // State: Lịch rảnh hàng tuần (Weekly Availability - Multi-slot)
  late List<DayScheduleConfig> _days;

  bool _isLoading = true;
  bool _isSaving = false;

  @override
  void initState() {
    super.initState();
    _initDefaultDays();
    _loadAllSettings();
  }

  void _initDefaultDays() {
    _days = [
      DayScheduleConfig(dayOfWeek: 1, label: 'Thứ Hai'),
      DayScheduleConfig(dayOfWeek: 2, label: 'Thứ Ba'),
      DayScheduleConfig(dayOfWeek: 3, label: 'Thứ Tư'),
      DayScheduleConfig(dayOfWeek: 4, label: 'Thứ Năm'),
      DayScheduleConfig(dayOfWeek: 5, label: 'Thứ Sáu'),
      DayScheduleConfig(dayOfWeek: 6, label: 'Thứ Bảy'),
      DayScheduleConfig(dayOfWeek: 0, label: 'Chủ Nhật'),
    ];
  }

  String _inferSlotName(String start, String end) {
    if (start.startsWith('07') && end.startsWith('12')) return 'Ca Sáng';
    if (start.startsWith('13') && end.startsWith('18')) return 'Ca Chiều';
    if (start.startsWith('18') && end.startsWith('23')) return 'Ca Tối';
    if (start.startsWith('08') && end.startsWith('22')) return 'Cả ngày';
    return 'Khung giờ';
  }

  Future<void> _loadAllSettings() async {
    setState(() => _isLoading = true);
    final repo = ref.read(proposalRepositoryProvider);

    try {
      final results = await Future.wait([
        repo.fetchPreferences(),
        repo.fetchAvailability(),
      ]);

      if (!mounted) return;

      final prefs = results[0] as WorkerPreferencesModel;
      final slots = results[1] as List<WorkerAvailabilitySlot>;

      setState(() {
        // 1. Áp dụng Preferences
        _maxDistanceKm = prefs.maxDistanceKm.clamp(1.0, 50.0);
        _minWage = prefs.minWage > 0 ? prefs.minWage : 35000;
        _baseAddress = prefs.baseAddress.isNotEmpty ? prefs.baseAddress : 'TP. Hồ Chí Minh';
        _baseLat = prefs.baseLat;
        _baseLng = prefs.baseLng;

        // 2. Áp dụng Availability (Hỗ trợ nạp nhiều ca trong cùng 1 ngày)
        for (final d in _days) {
          d.enabled = false;
          d.slots.clear();
        }
        for (final slot in slots) {
          if (!slot.isAvailable) continue;
          final d = _days.firstWhere(
            (day) => day.dayOfWeek == slot.dayOfWeek,
            orElse: () => _days.first,
          );
          d.enabled = true;
          d.slots.add(TimeSlotConfig(
            id: UniqueKey().toString(),
            name: _inferSlotName(slot.startTime, slot.endTime),
            startTime: slot.startTime,
            endTime: slot.endTime,
          ));
        }
        _isLoading = false;
      });
    } catch (_) {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  void _applyAvailabilityPreset(String type) {
    setState(() {
      if (type == 'EVENINGS') {
        for (final d in _days) {
          if (d.dayOfWeek >= 1 && d.dayOfWeek <= 5) {
            d.enabled = true;
            d.slots = [
              TimeSlotConfig(
                id: UniqueKey().toString(),
                name: 'Ca Tối',
                startTime: '18:00',
                endTime: '23:00',
              ),
            ];
          } else {
            d.enabled = false;
            d.slots.clear();
          }
        }
      } else if (type == 'WEEKENDS') {
        for (final d in _days) {
          if (d.dayOfWeek == 6 || d.dayOfWeek == 0) {
            d.enabled = true;
            d.slots = [
              TimeSlotConfig(
                id: UniqueKey().toString(),
                name: 'Ca Sáng',
                startTime: '08:00',
                endTime: '12:00',
              ),
              TimeSlotConfig(
                id: UniqueKey().toString(),
                name: 'Ca Chiều',
                startTime: '13:00',
                endTime: '18:00',
              ),
              TimeSlotConfig(
                id: UniqueKey().toString(),
                name: 'Ca Tối',
                startTime: '18:00',
                endTime: '22:00',
              ),
            ];
          } else {
            d.enabled = false;
            d.slots.clear();
          }
        }
      } else if (type == 'FULL_WEEK') {
        for (final d in _days) {
          d.enabled = true;
          d.slots = [
            TimeSlotConfig(
              id: UniqueKey().toString(),
              name: 'Cả ngày',
              startTime: '08:00',
              endTime: '22:00',
            ),
          ];
        }
      } else if (type == 'CLEAR_ALL') {
        for (final d in _days) {
          d.enabled = false;
          d.slots.clear();
        }
      }
    });
  }

  void _onToggleShiftPeriod(DayScheduleConfig day, String shiftName, String start, String end) {
    setState(() {
      final existingIndex = day.slots.indexWhere((s) => s.startTime == start && s.endTime == end);
      if (existingIndex != -1) {
        day.slots.removeAt(existingIndex);
      } else {
        day.slots.add(TimeSlotConfig(
          id: UniqueKey().toString(),
          name: shiftName,
          startTime: start,
          endTime: end,
        ));
      }
    });
  }

  void _onRemoveSlot(DayScheduleConfig day, TimeSlotConfig slot) {
    setState(() {
      day.slots.removeWhere((s) => s.id == slot.id);
    });
  }

  void _onAddCustomSlot(DayScheduleConfig day) {
    setState(() {
      day.slots.add(TimeSlotConfig(
        id: UniqueKey().toString(),
        name: 'Tùy chỉnh',
        startTime: '09:00',
        endTime: '14:00',
      ));
    });
  }

  Future<void> _pickTime(DayScheduleConfig day, TimeSlotConfig slot, bool isStart) async {
    final currentStr = isStart ? slot.startTime : slot.endTime;
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
          slot.startTime = formatted;
        } else {
          slot.endTime = formatted;
        }
      });
    }
  }

  void _onToggleDay(DayScheduleConfig day, bool enabled) {
    setState(() {
      day.enabled = enabled;
      if (enabled && day.slots.isEmpty) {
        // Tự động gán 1 ca mặc định khi bật ngày
        day.slots.add(TimeSlotConfig(
          id: UniqueKey().toString(),
          name: 'Ca Tối',
          startTime: '18:00',
          endTime: '23:00',
        ));
      }
    });
  }

  Future<void> _saveAllSettings() async {
    setState(() => _isSaving = true);
    final repo = ref.read(proposalRepositoryProvider);

    try {
      // 1. Chuẩn bị payload Tiêu chí tìm việc
      final prefModel = WorkerPreferencesModel(
        baseAddress: _baseAddress,
        baseLat: _baseLat,
        baseLng: _baseLng,
        minWage: _minWage,
        maxDistanceKm: _maxDistanceKm,
      );

      // 2. Chuẩn bị payload Lịch rảnh tuần (Gửi tất cả các ca trong ngày)
      final List<WorkerAvailabilitySlot> availSlots = [];
      for (final d in _days) {
        if (!d.enabled) continue;
        for (final slot in d.slots) {
          availSlots.add(WorkerAvailabilitySlot(
            dayOfWeek: d.dayOfWeek,
            startTime: slot.startTime,
            endTime: slot.endTime,
            isAvailable: true,
          ));
        }
      }

      // 3. Thực hiện lưu đồng thời
      await Future.wait([
        repo.updatePreferences(prefModel),
        repo.updateAvailability(availSlots),
      ]);

      // 4. Đồng bộ lại Preferences (tâm khu vực + bán kính + danh sách đề xuất) với Job Radar
      await ref.read(jobRadarProvider.notifier).reloadPreferences();
      ref.invalidate(proposalsListProvider);

      if (mounted) {
        setState(() => _isSaving = false);
        Navigator.pop(context, true);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Row(
              children: [
                const Icon(Icons.check_circle_rounded, color: Colors.white, size: 20),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    'Đã lưu tiêu chí & lịch rảnh (${availSlots.length} ca rảnh/tuần)!',
                    style: const TextStyle(fontWeight: FontWeight.w700),
                  ),
                ),
              ],
            ),
            backgroundColor: const Color(0xFF0D9488),
            behavior: SnackBarBehavior.floating,
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        setState(() => _isSaving = false);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Lỗi khi lưu cài đặt: $e'),
            backgroundColor: const Color(0xFFDC2626),
          ),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF8FAFC),
      appBar: AppBar(
        backgroundColor: Colors.white,
        elevation: 0,
        scrolledUnderElevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back_rounded, color: Color(0xFF0F172A)),
          onPressed: () => Navigator.pop(context),
        ),
        title: const Text(
          'Tiêu Chí & Lịch Nhận Việc',
          style: TextStyle(
            fontWeight: FontWeight.w800,
            fontSize: 17,
            color: Color(0xFF0F172A),
          ),
        ),
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : Column(
              children: [
                Expanded(
                  child: ListView(
                    padding: const EdgeInsets.fromLTRB(16, 12, 16, 20),
                    children: [
                      // Banner giới thiệu cơ chế Matching thông minh (Đã loại bỏ chữ CANi Dispatch Engine)
                      _buildHeroBanner(),
                      const SizedBox(height: 14),

                      // Card 1: Tiêu chí tìm việc (Bán kính, Thù lao tối thiểu, Khu vực & Điểm neo GPS)
                      JobPreferencesCard(
                        maxDistanceKm: _maxDistanceKm,
                        onDistanceChanged: (val) => setState(() => _maxDistanceKm = val),
                        minWage: _minWage,
                        onWageChanged: (val) => setState(() => _minWage = val),
                        isWagePerShift: _isWagePerShift,
                        onWageUnitChanged: (val) => setState(() => _isWagePerShift = val),
                        baseAddress: _baseAddress,
                        baseLat: _baseLat,
                        baseLng: _baseLng,
                        onLocationChanged: (addr, lat, lng) => setState(() {
                          _baseAddress = addr;
                          _baseLat = lat;
                          _baseLng = lng;
                        }),
                      ),
                      const SizedBox(height: 14),

                      // Card 2: Lịch rảnh cố định hàng tuần (Hỗ trợ nhiều ca trong ngày)
                      WeeklyAvailabilityCard(
                        days: _days,
                        onApplyPreset: _applyAvailabilityPreset,
                        onPickTime: _pickTime,
                        onToggleShiftPeriod: _onToggleShiftPeriod,
                        onRemoveSlot: _onRemoveSlot,
                        onAddCustomSlot: _onAddCustomSlot,
                        onToggleDay: _onToggleDay,
                      ),
                    ],
                  ),
                ),

                // Thanh điều hướng lưu cố định ở đáy (Sticky Bottom Action)
                _buildStickyBottomBar(),
              ],
            ),
    );
  }

  Widget _buildHeroBanner() {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        gradient: const LinearGradient(
          colors: [Color(0xFF0066FF), Color(0xFF0D9488)],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        borderRadius: BorderRadius.circular(16),
        boxShadow: const [
          BoxShadow(
            color: Color(0x220066FF),
            blurRadius: 10,
            offset: Offset(0, 4),
          ),
        ],
      ),
      child: const Row(
        children: [
          Icon(Icons.auto_awesome_rounded, color: Colors.white, size: 28),
          SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Tối ưu ghép ca thông minh',
                  style: TextStyle(
                    color: Colors.white,
                    fontWeight: FontWeight.w800,
                    fontSize: 14,
                  ),
                ),
                SizedBox(height: 3),
                Text(
                  'Hệ thống tự động ưu tiên đề xuất các ca làm việc phù hợp với bán kính, mức lương và lịch rảnh của bạn.',
                  style: TextStyle(color: Colors.white70, fontSize: 11.5, height: 1.35),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildStickyBottomBar() {
    return Container(
      padding: const EdgeInsets.fromLTRB(16, 10, 16, 16),
      decoration: BoxDecoration(
        color: Colors.white,
        border: Border(top: BorderSide(color: const Color(0xFFE2E8F0))),
        boxShadow: const [
          BoxShadow(
            color: Color(0x08000000),
            blurRadius: 8,
            offset: Offset(0, -3),
          ),
        ],
      ),
      child: SafeArea(
        top: false,
        child: SizedBox(
          width: double.infinity,
          height: 48,
          child: FilledButton.icon(
            onPressed: _isSaving ? null : _saveAllSettings,
            icon: _isSaving
                ? const SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                  )
                : const Icon(Icons.check_circle_outline_rounded, size: 19),
            label: Text(
              _isSaving ? 'Đang lưu cài đặt...' : 'Lưu Tiêu Chí & Lịch Rảnh',
              style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 14),
            ),
            style: FilledButton.styleFrom(
              backgroundColor: const Color(0xFF0066FF),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
            ),
          ),
        ),
      ),
    );
  }
}
