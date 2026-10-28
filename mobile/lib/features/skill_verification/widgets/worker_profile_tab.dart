import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/features/auth/auth.dart';
import 'package:cani/features/ekyc/screens/ekyc_verification_screen.dart';
import 'package:cani/features/job_proposals/data/proposal_repository.dart';
import 'package:cani/features/job_proposals/models/proposal_models.dart';
import 'package:cani/features/skill_verification/providers/skill_verification_provider.dart';
import '../screens/weekly_availability_screen.dart';
import '../screens/worker_settings_screen.dart';

class WorkerProfileTab extends ConsumerStatefulWidget {
  const WorkerProfileTab({super.key});

  @override
  ConsumerState<WorkerProfileTab> createState() => _WorkerProfileTabState();
}

class _WorkerProfileTabState extends ConsumerState<WorkerProfileTab> {
  WorkerIncomeSummary _income = const WorkerIncomeSummary();
  List<WorkerAvailabilitySlot> _availability = [];
  WorkerPreferencesModel _prefs = const WorkerPreferencesModel();
  String _bio =
      'Nhân viên sự kiện năng động, trách nhiệm cao, có kinh nghiệm phục vụ tiệc cưới, hội nghị và pha chế đồ uống. Luôn đúng giờ và tuân thủ quy chuẩn dịch vụ.';
  String _experience = '1 năm';

  @override
  void initState() {
    super.initState();
    _loadProfileData();
  }

  Future<void> _loadProfileData() async {
    final repo = ref.read(proposalRepositoryProvider);
    try {
      final results = await Future.wait([
        repo.fetchIncomeSummary().catchError((_) => const WorkerIncomeSummary()),
        repo.fetchAvailability().catchError((_) => <WorkerAvailabilitySlot>[]),
        repo.fetchPreferences().catchError((_) => const WorkerPreferencesModel()),
      ]);
      if (!mounted) return;
      setState(() {
        _income = results[0] as WorkerIncomeSummary;
        _availability = results[1] as List<WorkerAvailabilitySlot>;
        _prefs = results[2] as WorkerPreferencesModel;
      });
    } catch (_) {
      // Giữ default state an toàn khi mất kết nối, tránh unhandled exception làm sập màn hình
    }
  }

  String _formatMoney(double val) {
    return val.toInt().toString().replaceAllMapped(
          RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'),
          (m) => '${m[1]}.',
        );
  }

  String _dayShortName(int day) {
    switch (day) {
      case 1:
        return 'T2';
      case 2:
        return 'T3';
      case 3:
        return 'T4';
      case 4:
        return 'T5';
      case 5:
        return 'T6';
      case 6:
        return 'T7';
      case 0:
        return 'CN';
      default:
        return '';
    }
  }

  void _openEditCvSheet() {
    final bioCtrl = TextEditingController(text: _bio);
    final expCtrl = TextEditingController(text: _experience);

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.white,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(22)),
      ),
      builder: (ctx) => Padding(
        padding: EdgeInsets.only(
          left: 20,
          right: 20,
          top: 20,
          bottom: MediaQuery.of(ctx).viewInsets.bottom + 24,
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Cập Nhật Thông Tin CV (UC-06)',
              style: TextStyle(fontWeight: FontWeight.w800, fontSize: 17, color: Color(0xFF0F172A)),
            ),
            const SizedBox(height: 14),
            TextField(
              controller: expCtrl,
              decoration: InputDecoration(
                labelText: 'Kinh nghiệm làm việc',
                border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
              ),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: bioCtrl,
              maxLines: 4,
              decoration: InputDecoration(
                labelText: 'Giới thiệu bản thân (Bio)',
                border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
              ),
            ),
            const SizedBox(height: 16),
            SizedBox(
              width: double.infinity,
              height: 46,
              child: FilledButton(
                onPressed: () {
                  setState(() {
                    _experience = expCtrl.text.trim().isNotEmpty ? expCtrl.text.trim() : _experience;
                    _bio = bioCtrl.text.trim().isNotEmpty ? bioCtrl.text.trim() : _bio;
                  });
                  Navigator.pop(ctx);
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(
                      content: Text('Đã lưu thông tin giới thiệu hồ sơ (CV)!'),
                      backgroundColor: Color(0xFF0D9488),
                    ),
                  );
                },
                style: FilledButton.styleFrom(
                  backgroundColor: const Color(0xFF0066FF),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                ),
                child: const Text('Lưu Thay Đổi', style: TextStyle(fontWeight: FontWeight.w700)),
              ),
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final authState = ref.watch(authStateProvider);
    final skillState = ref.watch(skillVerificationProvider);
    final user = authState.user;
    final theme = Theme.of(context);

    if (user == null) {
      return const Center(child: Text('Chưa có thông tin người dùng'));
    }

    final totalSp = skillState.skillPoints.fold<int>(0, (sum, p) => sum + p.totalSp);
    final currentTier = skillState.skillPoints.isNotEmpty ? skillState.skillPoints.first.tier : 'STARTER';

    return RefreshIndicator(
      onRefresh: _loadProfileData,
      child: SingleChildScrollView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.all(16.0),
        child: Column(
          children: [
            // 1. Thẻ thông tin cá nhân
            Card(
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
              elevation: 0,
              color: Colors.white,
              child: Padding(
                padding: const EdgeInsets.all(20.0),
                child: Row(
                  children: [
                    CircleAvatar(
                      radius: 34,
                      backgroundColor: theme.colorScheme.primaryContainer,
                      child: Text(
                        user.fullName.isNotEmpty ? user.fullName[0].toUpperCase() : 'W',
                        style: theme.textTheme.headlineSmall?.copyWith(
                          color: theme.colorScheme.onPrimaryContainer,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),
                    const SizedBox(width: 14),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            user.fullName,
                            style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 17, color: Color(0xFF0F172A)),
                          ),
                          const SizedBox(height: 3),
                          Text(
                            user.email,
                            style: const TextStyle(fontSize: 12.5, color: Color(0xFF64748B)),
                          ),
                          const SizedBox(height: 8),
                          Row(
                            children: [
                              Container(
                                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                                decoration: BoxDecoration(
                                  color: const Color(0xFFEFF6FF),
                                  borderRadius: BorderRadius.circular(6),
                                ),
                                child: Text(
                                  'Hạng: $currentTier',
                                  style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w800, color: Color(0xFF0066FF)),
                                ),
                              ),
                              const SizedBox(width: 6),
                              Container(
                                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                                decoration: BoxDecoration(
                                  color: const Color(0xFFF0FDF4),
                                  borderRadius: BorderRadius.circular(6),
                                ),
                                child: Text(
                                  'Uy tín: ${_income.reliabilityScore}/100',
                                  style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w800, color: Color(0xFF16A34A)),
                                ),
                              ),
                            ],
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            ),

            const SizedBox(height: 14),

            // 2. Thẻ Thống Kê Thu Nhập & Ví Làm Việc (UC-60 / UC-64)
            Container(
              padding: const EdgeInsets.all(18),
              decoration: BoxDecoration(
                gradient: const LinearGradient(
                  colors: [Color(0xFF0F172A), Color(0xFF1E3A8A)],
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                ),
                borderRadius: BorderRadius.circular(20),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      const Row(
                        children: [
                          Icon(Icons.account_balance_wallet_outlined, color: Color(0xFF38BDF8), size: 20),
                          SizedBox(width: 8),
                          Text(
                            'Thống Kê Thu Nhập Cá Nhân',
                            style: TextStyle(color: Colors.white70, fontSize: 12.5, fontWeight: FontWeight.w600),
                          ),
                        ],
                      ),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                        decoration: BoxDecoration(
                          color: Colors.white.withValues(alpha: 0.12),
                          borderRadius: BorderRadius.circular(12),
                        ),
                        child: Text(
                          '★ ${_income.avgRating.toStringAsFixed(1)}',
                          style: const TextStyle(color: Color(0xFFFBBF24), fontWeight: FontWeight.w800, fontSize: 12),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 10),
                  Text(
                    '${_formatMoney(_income.totalEarnings)} đ',
                    style: const TextStyle(
                      color: Colors.white,
                      fontWeight: FontWeight.w900,
                      fontSize: 24,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    'Đang chờ xác nhận: ${_formatMoney(_income.pendingEarnings)} đ',
                    style: const TextStyle(color: Color(0xFF93C5FD), fontSize: 12, fontWeight: FontWeight.w600),
                  ),
                  const Divider(color: Colors.white24, height: 22),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceAround,
                    children: [
                      Column(
                        children: [
                          Text(
                            '${_income.completedShiftsCount} ca',
                            style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w800, fontSize: 15),
                          ),
                          const SizedBox(height: 2),
                          const Text('Đã hoàn thành', style: TextStyle(color: Colors.white60, fontSize: 11.5)),
                        ],
                      ),
                      Container(width: 1, height: 28, color: Colors.white24),
                      Column(
                        children: [
                          Text(
                            '${_income.totalHoursWorked.toStringAsFixed(1)} giờ',
                            style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w800, fontSize: 15),
                          ),
                          const SizedBox(height: 2),
                          const Text('Tổng giờ làm', style: TextStyle(color: Colors.white60, fontSize: 11.5)),
                        ],
                      ),
                      Container(width: 1, height: 28, color: Colors.white24),
                      Column(
                        children: [
                          Text(
                            '$totalSp SP',
                            style: const TextStyle(color: Color(0xFF38BDF8), fontWeight: FontWeight.w800, fontSize: 15),
                          ),
                          const SizedBox(height: 2),
                          const Text('Điểm kỹ năng', style: TextStyle(color: Colors.white60, fontSize: 11.5)),
                        ],
                      ),
                    ],
                  ),
                ],
              ),
            ),

            const SizedBox(height: 14),

            // 3. Thẻ Lịch Rảnh Cố Định Hàng Tuần (UC-25)
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(18),
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
                          Icon(Icons.calendar_month_rounded, color: Color(0xFF0066FF), size: 20),
                          SizedBox(width: 8),
                          Text(
                            'Lịch Rảnh Cố Định Tuần',
                            style: TextStyle(fontWeight: FontWeight.w800, fontSize: 15, color: Color(0xFF0F172A)),
                          ),
                        ],
                      ),
                      TextButton.icon(
                        onPressed: () async {
                          final updated = await Navigator.push<List<WorkerAvailabilitySlot>>(
                            context,
                            MaterialPageRoute(builder: (_) => const WeeklyAvailabilityScreen()),
                          );
                          if (updated != null && mounted) {
                            setState(() => _availability = updated);
                          }
                        },
                        icon: const Icon(Icons.edit_calendar_rounded, size: 16),
                        label: const Text('Thiết lập', style: TextStyle(fontWeight: FontWeight.w700, fontSize: 12.5)),
                      ),
                    ],
                  ),
                  const SizedBox(height: 6),
                  if (_availability.isEmpty)
                    const Text(
                      'Chưa đăng ký lịch rảnh cố định. Hãy thiết lập để hệ thống tự động ghép ca phù hợp cho bạn!',
                      style: TextStyle(fontSize: 12.5, color: Color(0xFF64748B)),
                    )
                  else
                    Wrap(
                      spacing: 6,
                      runSpacing: 6,
                      children: _availability.map((slot) {
                        return Container(
                          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                          decoration: BoxDecoration(
                            color: const Color(0xFFF0FDFA),
                            borderRadius: BorderRadius.circular(8),
                            border: Border.all(color: const Color(0xFF99F6E4)),
                          ),
                          child: Text(
                            '${_dayShortName(slot.dayOfWeek)}: ${slot.startTime}-${slot.endTime}',
                            style: const TextStyle(
                              fontSize: 12,
                              fontWeight: FontWeight.w700,
                              color: Color(0xFF0F766E),
                            ),
                          ),
                        );
                      }).toList(),
                    ),
                ],
              ),
            ),

            const SizedBox(height: 14),

            // 4. Thẻ eKYC Định danh Căn cước
            Card(
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
              elevation: 0,
              color: theme.colorScheme.primaryContainer.withValues(alpha: 0.35),
              child: ListTile(
                contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
                leading: Container(
                  padding: const EdgeInsets.all(8),
                  decoration: BoxDecoration(
                    color: theme.colorScheme.primary,
                    shape: BoxShape.circle,
                  ),
                  child: const Icon(Icons.verified_user, color: Colors.white, size: 22),
                ),
                title: const Text(
                  'Xác Thực Định Danh eKYC (CCCD)',
                  style: TextStyle(fontWeight: FontWeight.bold, fontSize: 14),
                ),
                subtitle: const Text(
                  'Quét căn cước & khuôn mặt AI để nhận huy hiệu Verified',
                  style: TextStyle(fontSize: 12),
                ),
                trailing: const Icon(Icons.arrow_forward_ios, size: 15),
                onTap: () {
                  Navigator.of(context).push(
                    MaterialPageRoute(builder: (_) => const EkycVerificationScreen()),
                  );
                },
              ),
            ),

            const SizedBox(height: 14),

            // 5. CV & Thông tin hồ sơ sự kiện
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(18),
                border: Border.all(color: const Color(0xFFE2E8F0)),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      const Text(
                        'Thông Tin CV & Tiêu Chí Nghề Nghiệp',
                        style: TextStyle(fontWeight: FontWeight.w800, fontSize: 15, color: Color(0xFF0F172A)),
                      ),
                      IconButton(
                        icon: const Icon(Icons.edit_outlined, size: 20, color: Color(0xFF0066FF)),
                        tooltip: 'Chỉnh sửa CV',
                        onPressed: _openEditCvSheet,
                      ),
                    ],
                  ),
                  ListTile(
                    dense: true,
                    contentPadding: EdgeInsets.zero,
                    leading: const Icon(Icons.history, color: Colors.teal, size: 20),
                    title: const Text('Kinh nghiệm làm việc'),
                    trailing: Text(_experience, style: const TextStyle(fontWeight: FontWeight.bold)),
                  ),
                  ListTile(
                    dense: true,
                    contentPadding: EdgeInsets.zero,
                    leading: const Icon(Icons.payments_outlined, color: Colors.green, size: 20),
                    title: const Text('Thù lao tối thiểu mong muốn'),
                    trailing: Text(
                      '${_formatMoney(_prefs.minWage)} đ / giờ',
                      style: const TextStyle(fontWeight: FontWeight.bold, color: Color(0xFF16A34A)),
                    ),
                  ),
                  ListTile(
                    dense: true,
                    contentPadding: EdgeInsets.zero,
                    leading: const Icon(Icons.radar_rounded, color: Color(0xFF0066FF), size: 20),
                    title: const Text('Bán kính nhận việc tối đa'),
                    trailing: Text(
                      '${_prefs.maxDistanceKm.round()} km (${_prefs.baseAddress})',
                      style: const TextStyle(fontWeight: FontWeight.bold),
                    ),
                    onTap: () async {
                      await Navigator.push(
                        context,
                        MaterialPageRoute(builder: (_) => const WorkerSettingsScreen()),
                      );
                      _loadProfileData();
                    },
                  ),
                  const Divider(),
                  const SizedBox(height: 4),
                  const Text('Giới thiệu bản thân (Bio):', style: TextStyle(fontSize: 12, color: Colors.grey)),
                  const SizedBox(height: 6),
                  Text(
                    _bio,
                    style: const TextStyle(fontSize: 13, height: 1.4, color: Color(0xFF334155)),
                  ),
                ],
              ),
            ),

            const SizedBox(height: 24),
          ],
        ),
      ),
    );
  }
}
