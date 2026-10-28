import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/features/auth/auth.dart';
import 'package:cani/features/ekyc/screens/ekyc_verification_screen.dart';
import 'package:cani/features/job_proposals/data/proposal_repository.dart';
import 'package:cani/features/job_proposals/models/proposal_models.dart';
import 'weekly_availability_screen.dart';

class WorkerSettingsScreen extends ConsumerStatefulWidget {
  const WorkerSettingsScreen({super.key});

  @override
  ConsumerState<WorkerSettingsScreen> createState() => _WorkerSettingsScreenState();
}

class _WorkerSettingsScreenState extends ConsumerState<WorkerSettingsScreen> {
  double _maxDistanceKm = 15.0;
  double _minWage = 35000;
  String _baseAddress = 'TP. Hồ Chí Minh';
  bool _pushNotifEnabled = true;
  bool _isLoadingPrefs = true;
  bool _isSavingPrefs = false;

  final List<double> _wagePresets = const [30000, 35000, 50000, 75000, 100000];
  final List<String> _cities = const ['TP. Hồ Chí Minh', 'Hà Nội', 'Đà Nẵng', 'Cần Thơ'];

  @override
  void initState() {
    super.initState();
    _loadPreferences();
  }

  Future<void> _loadPreferences() async {
    final prefs = await ref.read(proposalRepositoryProvider).fetchPreferences();
    if (!mounted) return;
    setState(() {
      _maxDistanceKm = prefs.maxDistanceKm.clamp(1.0, 50.0);
      _minWage = prefs.minWage > 0 ? prefs.minWage : 35000;
      _baseAddress = prefs.baseAddress.isNotEmpty ? prefs.baseAddress : 'TP. Hồ Chí Minh';
      _isLoadingPrefs = false;
    });
  }

  Future<void> _savePreferences() async {
    setState(() => _isSavingPrefs = true);
    final model = WorkerPreferencesModel(
      baseAddress: _baseAddress,
      minWage: _minWage,
      maxDistanceKm: _maxDistanceKm,
    );
    await ref.read(proposalRepositoryProvider).updatePreferences(model);
    if (mounted) {
      setState(() => _isSavingPrefs = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            'Đã lưu tiêu chí: Bán kính ${_maxDistanceKm.round()}km • Lương tối thiểu ${_formatMoney(_minWage)}đ/h',
          ),
          backgroundColor: const Color(0xFF0D9488),
        ),
      );
    }
  }

  String _formatMoney(double val) {
    return val.toInt().toString().replaceAllMapped(
          RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'),
          (m) => '${m[1]}.',
        );
  }

  Future<void> _confirmLogout() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(18)),
        title: const Text(
          'Đăng xuất tài khoản?',
          style: TextStyle(fontWeight: FontWeight.w800, fontSize: 17),
        ),
        content: const Text(
          'Bạn có chắc chắn muốn đăng xuất khỏi ứng dụng CANi Partner không?',
          style: TextStyle(fontSize: 13.5, color: Color(0xFF475569)),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Hủy', style: TextStyle(fontWeight: FontWeight.w700, color: Color(0xFF64748B))),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(ctx, true),
            style: FilledButton.styleFrom(backgroundColor: const Color(0xFFDC2626)),
            child: const Text('Đăng xuất', style: TextStyle(fontWeight: FontWeight.w700)),
          ),
        ],
      ),
    );

    if (confirmed == true && mounted) {
      Navigator.of(context).popUntil((route) => route.isFirst);
      ref.read(authStateProvider.notifier).logout();
    }
  }

  Widget _buildSectionTitle(String title) {
    return Padding(
      padding: const EdgeInsets.only(left: 4, bottom: 8, top: 8),
      child: Text(
        title.toUpperCase(),
        style: const TextStyle(
          fontSize: 11.5,
          fontWeight: FontWeight.w800,
          letterSpacing: 0.6,
          color: Color(0xFF64748B),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF8FAFC),
      appBar: AppBar(
        backgroundColor: Colors.white,
        elevation: 0,
        title: const Text(
          'Cài Đặt & Tùy Chỉnh',
          style: TextStyle(fontWeight: FontWeight.w800, fontSize: 18, color: Color(0xFF0F172A)),
        ),
      ),
      body: _isLoadingPrefs
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: const EdgeInsets.all(16),
              children: [
                // NHÓM 1: TIÊU CHÍ TÌM VIỆC & MATCHING (UC-26, UC-34)
                _buildSectionTitle('Tiêu chí tìm việc & ghép ca (UC-26 / UC-34)'),
                Container(
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color: Colors.white,
                    borderRadius: BorderRadius.circular(16),
                    border: Border.all(color: const Color(0xFFE2E8F0)),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          const Row(
                            children: [
                              Icon(Icons.radar_rounded, color: Color(0xFF0066FF), size: 20),
                              SizedBox(width: 8),
                              Text(
                                'Bán kính nhận việc tối đa',
                                style: TextStyle(fontWeight: FontWeight.w700, fontSize: 14, color: Color(0xFF0F172A)),
                              ),
                            ],
                          ),
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                            decoration: BoxDecoration(
                              color: const Color(0xFFEFF6FF),
                              borderRadius: BorderRadius.circular(20),
                            ),
                            child: Text(
                              '${_maxDistanceKm.round()} km',
                              style: const TextStyle(
                                fontWeight: FontWeight.w800,
                                fontSize: 13,
                                color: Color(0xFF0066FF),
                              ),
                            ),
                          ),
                        ],
                      ),
                      Slider(
                        value: _maxDistanceKm,
                        min: 1,
                        max: 50,
                        divisions: 49,
                        activeColor: const Color(0xFF0066FF),
                        label: '${_maxDistanceKm.round()} km',
                        onChanged: (v) => setState(() => _maxDistanceKm = v),
                      ),
                      const Divider(height: 20),
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          const Row(
                            children: [
                              Icon(Icons.payments_outlined, color: Color(0xFF16A34A), size: 20),
                              SizedBox(width: 8),
                              Text(
                                'Mức thù lao tối thiểu',
                                style: TextStyle(fontWeight: FontWeight.w700, fontSize: 14, color: Color(0xFF0F172A)),
                              ),
                            ],
                          ),
                          Text(
                            '${_formatMoney(_minWage)} đ/giờ',
                            style: const TextStyle(
                              fontWeight: FontWeight.w800,
                              fontSize: 13.5,
                              color: Color(0xFF16A34A),
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 10),
                      Wrap(
                        spacing: 6,
                        runSpacing: 6,
                        children: _wagePresets.map((wage) {
                          final selected = _minWage == wage;
                          return ChoiceChip(
                            label: Text(
                              '${_formatMoney(wage)}đ/h',
                              style: TextStyle(
                                fontSize: 12,
                                fontWeight: FontWeight.w700,
                                color: selected ? Colors.white : const Color(0xFF334155),
                              ),
                            ),
                            selected: selected,
                            selectedColor: const Color(0xFF16A34A),
                            backgroundColor: const Color(0xFFF1F5F9),
                            onSelected: (_) => setState(() => _minWage = wage),
                          );
                        }).toList(),
                      ),
                      const Divider(height: 24),
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          const Row(
                            children: [
                              Icon(Icons.location_city_outlined, color: Color(0xFF0D9488), size: 20),
                              SizedBox(width: 8),
                              Text(
                                'Khu vực hoạt động',
                                style: TextStyle(fontWeight: FontWeight.w700, fontSize: 14, color: Color(0xFF0F172A)),
                              ),
                            ],
                          ),
                          DropdownButton<String>(
                            value: _cities.contains(_baseAddress) ? _baseAddress : _cities.first,
                            underline: const SizedBox.shrink(),
                            style: const TextStyle(
                              fontWeight: FontWeight.w700,
                              fontSize: 13.5,
                              color: Color(0xFF0D9488),
                            ),
                            items: _cities
                                .map((c) => DropdownMenuItem(value: c, child: Text(c)))
                                .toList(),
                            onChanged: (val) {
                              if (val != null) setState(() => _baseAddress = val);
                            },
                          ),
                        ],
                      ),
                      const SizedBox(height: 12),
                      SizedBox(
                        width: double.infinity,
                        height: 44,
                        child: FilledButton.icon(
                          onPressed: _isSavingPrefs ? null : _savePreferences,
                          icon: _isSavingPrefs
                              ? const SizedBox(
                                  width: 16,
                                  height: 16,
                                  child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                                )
                              : const Icon(Icons.save_outlined, size: 18),
                          label: Text(
                            _isSavingPrefs ? 'Đang lưu...' : 'Lưu Tiêu Chí Tìm Việc',
                            style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 13.5),
                          ),
                          style: FilledButton.styleFrom(
                            backgroundColor: const Color(0xFF0066FF),
                            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                          ),
                        ),
                      ),
                    ],
                  ),
                ),

                const SizedBox(height: 16),

                // NHÓM 2: LỊCH RẢNH & XÁC THỰC HỒ SƠ
                _buildSectionTitle('Thiết lập lịch & định danh'),
                Container(
                  decoration: BoxDecoration(
                    color: Colors.white,
                    borderRadius: BorderRadius.circular(16),
                    border: Border.all(color: const Color(0xFFE2E8F0)),
                  ),
                  child: Column(
                    children: [
                      ListTile(
                        leading: Container(
                          padding: const EdgeInsets.all(8),
                          decoration: BoxDecoration(
                            color: const Color(0xFFEFF6FF),
                            borderRadius: BorderRadius.circular(10),
                          ),
                          child: const Icon(Icons.calendar_month_rounded, color: Color(0xFF0066FF), size: 20),
                        ),
                        title: const Text(
                          'Lịch rảnh cố định hàng tuần (UC-25)',
                          style: TextStyle(fontWeight: FontWeight.w700, fontSize: 14),
                        ),
                        subtitle: const Text(
                          'Đăng ký khung giờ rảnh từ Thứ 2 - Chủ Nhật',
                          style: TextStyle(fontSize: 12, color: Color(0xFF64748B)),
                        ),
                        trailing: const Icon(Icons.chevron_right_rounded),
                        onTap: () {
                          Navigator.push(
                            context,
                            MaterialPageRoute(builder: (_) => const WeeklyAvailabilityScreen()),
                          );
                        },
                      ),
                      const Divider(height: 1, indent: 56),
                      ListTile(
                        leading: Container(
                          padding: const EdgeInsets.all(8),
                          decoration: BoxDecoration(
                            color: const Color(0xFFF0FDFA),
                            borderRadius: BorderRadius.circular(10),
                          ),
                          child: const Icon(Icons.verified_user_outlined, color: Color(0xFF0D9488), size: 20),
                        ),
                        title: const Text(
                          'Xác thực định danh eKYC (CCCD)',
                          style: TextStyle(fontWeight: FontWeight.w700, fontSize: 14),
                        ),
                        subtitle: const Text(
                          'Quét căn cước công dân & xác thực khuôn mặt',
                          style: TextStyle(fontSize: 12, color: Color(0xFF64748B)),
                        ),
                        trailing: const Icon(Icons.chevron_right_rounded),
                        onTap: () {
                          Navigator.push(
                            context,
                            MaterialPageRoute(builder: (_) => const EkycVerificationScreen()),
                          );
                        },
                      ),
                    ],
                  ),
                ),

                const SizedBox(height: 16),

                // NHÓM 3: CÀI ĐẶT ỨNG DỤNG
                _buildSectionTitle('Cài đặt ứng dụng'),
                Container(
                  decoration: BoxDecoration(
                    color: Colors.white,
                    borderRadius: BorderRadius.circular(16),
                    border: Border.all(color: const Color(0xFFE2E8F0)),
                  ),
                  child: Column(
                    children: [
                      SwitchListTile(
                        value: _pushNotifEnabled,
                        activeThumbColor: const Color(0xFF0066FF),
                        secondary: Container(
                          padding: const EdgeInsets.all(8),
                          decoration: BoxDecoration(
                            color: const Color(0xFFFEF3C7),
                            borderRadius: BorderRadius.circular(10),
                          ),
                          child: const Icon(Icons.notifications_active_outlined, color: Color(0xFFD97706), size: 20),
                        ),
                        title: const Text(
                          'Thông báo đẩy (Push Notifications)',
                          style: TextStyle(fontWeight: FontWeight.w700, fontSize: 14),
                        ),
                        subtitle: const Text(
                          'Nhận thông báo ca làm khẩn cấp & xác nhận lương',
                          style: TextStyle(fontSize: 12, color: Color(0xFF64748B)),
                        ),
                        onChanged: (v) => setState(() => _pushNotifEnabled = v),
                      ),
                      const Divider(height: 1, indent: 56),
                      const ListTile(
                        leading: Icon(Icons.info_outline_rounded, color: Color(0xFF64748B)),
                        title: Text(
                          'Phiên bản ứng dụng',
                          style: TextStyle(fontWeight: FontWeight.w600, fontSize: 14),
                        ),
                        trailing: Text(
                          'CANi Partner v1.0.0 (Build 1)',
                          style: TextStyle(fontSize: 12.5, fontWeight: FontWeight.w700, color: Color(0xFF64748B)),
                        ),
                      ),
                    ],
                  ),
                ),

                const SizedBox(height: 24),

                // NHÓM 4: NÚT ĐĂNG XUẤT TÀI KHOẢN (ĐẶT TRONG MÀN CÀI ĐẶT)
                SizedBox(
                  width: double.infinity,
                  height: 48,
                  child: OutlinedButton.icon(
                    onPressed: _confirmLogout,
                    icon: const Icon(Icons.logout_rounded, color: Color(0xFFDC2626)),
                    label: const Text(
                      'Đăng Xuất Khỏi Tài Khoản',
                      style: TextStyle(
                        color: Color(0xFFDC2626),
                        fontWeight: FontWeight.w800,
                        fontSize: 14.5,
                      ),
                    ),
                    style: OutlinedButton.styleFrom(
                      backgroundColor: const Color(0xFFFEF2F2),
                      side: const BorderSide(color: Color(0xFFFCA5A5)),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                    ),
                  ),
                ),
                const SizedBox(height: 28),
              ],
            ),
    );
  }
}

