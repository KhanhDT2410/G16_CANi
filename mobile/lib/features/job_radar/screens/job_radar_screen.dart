import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/features/auth/providers/auth_provider.dart';
import 'package:cani/features/job_proposals/data/proposal_repository.dart';
import 'package:cani/features/job_proposals/models/proposal_models.dart';
import 'package:cani/features/job_proposals/screens/attendance_qr_scanner_screen.dart';
import 'package:cani/features/job_proposals/widgets/shift_navigation_sheet.dart';
import 'package:cani/features/job_radar/data/job_radar_repository.dart';
import 'package:cani/features/job_radar/models/job_radar_models.dart';
import 'package:cani/features/job_radar/providers/job_radar_provider.dart';
import 'package:cani/features/job_radar/widgets/job_detail_bottom_sheet.dart';
import 'package:cani/features/job_radar/widgets/job_radar_map_painter.dart';
import 'package:cani/features/job_radar/widgets/radar_sliding_job_card.dart';
import 'package:cani/features/job_radar/widgets/urgent_shift_hero_card.dart';
import 'package:cani/features/skill_verification/providers/skill_verification_provider.dart';

class JobRadarScreen extends ConsumerStatefulWidget {
  const JobRadarScreen({super.key});

  @override
  ConsumerState<JobRadarScreen> createState() => _JobRadarScreenState();
}

class _JobRadarScreenState extends ConsumerState<JobRadarScreen> {
  ShiftAssignmentItem? _urgentShift;
  bool _isLoadingUrgent = false;

  @override
  void initState() {
    super.initState();
    _loadUrgentShift();
  }

  Future<void> _loadUrgentShift() async {
    setState(() => _isLoadingUrgent = true);
    try {
      final repo = ref.read(proposalRepositoryProvider);
      final assigns = await repo.fetchAssignments();
      final active = assigns.where((a) => a.status != 'COMPLETED' && a.checkoutTime == null).toList();
      if (mounted) {
        setState(() {
          _urgentShift = active.isNotEmpty ? active.first : null;
          _isLoadingUrgent = false;
        });
      }
    } catch (_) {
      if (mounted) setState(() => _isLoadingUrgent = false);
    }
  }

  void _openNavigation(ShiftAssignmentItem a) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => ShiftNavigationSheet(assignment: a),
    );
  }

  Future<void> _openQrScanner(ShiftAssignmentItem a) async {
    final isCheckedIn = a.status == 'CHECKED_IN' || (a.checkinTime != null && a.checkoutTime == null);
    final success = await Navigator.push<bool>(
      context,
      MaterialPageRoute(
        builder: (_) => AttendanceQrScannerScreen(
          assignment: a,
          isCheckin: !isCheckedIn,
        ),
      ),
    );
    if (success == true) {
      _loadUrgentShift();
    }
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(jobRadarProvider);
    final notifier = ref.read(jobRadarProvider.notifier);
    final authState = ref.watch(authStateProvider);
    final skillState = ref.watch(skillVerificationProvider);

    final user = authState.user;
    final userName = user?.fullName.isNotEmpty == true ? user!.fullName : 'Nhân Sự';
    final initialChar = userName.isNotEmpty ? userName[0].toUpperCase() : 'N';
    final totalSp = skillState.skillPoints.fold<int>(0, (sum, p) => sum + p.totalSp);
    final currentTier = skillState.skillPoints.isNotEmpty ? skillState.skillPoints.first.tier : 'VÀNG';

    final selectedOrFirstShift = state.selectedShift ?? (state.nearbyShifts.isNotEmpty ? state.nearbyShifts.first : null);

    return Scaffold(
      backgroundColor: const Color(0xFFF8FAFC),
      body: SafeArea(
        child: Column(
          children: [
            // 1. Header chuẩn OpenDesign
            Container(
              padding: const EdgeInsets.fromLTRB(18, 10, 18, 12),
              color: Colors.white,
              child: Row(
                children: [
                  // Avatar Gradient với chấm xanh online
                  Stack(
                    children: [
                      Container(
                        width: 44,
                        height: 44,
                        decoration: const BoxDecoration(
                          shape: BoxShape.circle,
                          gradient: LinearGradient(
                            colors: [Color(0xFF00D2FF), Color(0xFF0066FF)],
                          ),
                          boxShadow: [
                            BoxShadow(
                              color: Color(0x4D0077FF),
                              blurRadius: 10,
                              offset: Offset(0, 3),
                            ),
                          ],
                        ),
                        child: Center(
                          child: Text(
                            initialChar,
                            style: const TextStyle(
                              color: Colors.white,
                              fontWeight: FontWeight.w800,
                              fontSize: 17,
                            ),
                          ),
                        ),
                      ),
                      Positioned(
                        right: 0,
                        bottom: 0,
                        child: Container(
                          width: 12,
                          height: 12,
                          decoration: BoxDecoration(
                            shape: BoxShape.circle,
                            color: state.isOnline ? const Color(0xFF10B981) : const Color(0xFF94A3B8),
                            border: Border.all(color: Colors.white, width: 2.2),
                          ),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(width: 11),

                  // Lời chào & Cấp bậc
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'Chào buổi sáng, $userName!',
                          style: const TextStyle(
                            fontWeight: FontWeight.w800,
                            fontSize: 15,
                            color: Color(0xFF0B1B3D),
                            letterSpacing: -0.2,
                          ),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                        const SizedBox(height: 2),
                        Row(
                          children: [
                            const Icon(Icons.star, size: 12, color: Color(0xFFF59E0B)),
                            const SizedBox(width: 4),
                            Text(
                              'Hạng $currentTier · $totalSp SP',
                              style: const TextStyle(
                                fontSize: 11.5,
                                fontWeight: FontWeight.w600,
                                color: Color(0xFF64748B),
                              ),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),

                  // Nút Bật/Tắt Sẵn sàng nhận việc
                  InkWell(
                    onTap: () => notifier.toggleOnlineStatus(),
                    borderRadius: BorderRadius.circular(999),
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                      decoration: BoxDecoration(
                        color: state.isOnline ? const Color(0xFFE8FDF3) : const Color(0xFFF1F5F9),
                        borderRadius: BorderRadius.circular(999),
                        border: Border.all(
                          color: state.isOnline ? const Color(0xFFB7F4D8) : const Color(0xFFE2E8F0),
                        ),
                      ),
                      child: Row(
                        children: [
                          Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                state.isOnline ? 'Đang nhận việc' : 'Tạm nghỉ',
                                style: TextStyle(
                                  fontSize: 10.5,
                                  fontWeight: FontWeight.w800,
                                  color: state.isOnline ? const Color(0xFF059669) : const Color(0xFF64748B),
                                ),
                              ),
                              Text(
                                state.isOnline ? 'GPS bật' : 'GPS tắt',
                                style: const TextStyle(fontSize: 8.5, color: Color(0xFF94A3B8), fontWeight: FontWeight.w600),
                              ),
                            ],
                          ),
                          const SizedBox(width: 8),
                          Container(
                            width: 32,
                            height: 18,
                            decoration: BoxDecoration(
                              borderRadius: BorderRadius.circular(99),
                              color: state.isOnline ? const Color(0xFF10B981) : const Color(0xFFCBD5E1),
                            ),
                            child: AnimatedAlign(
                              duration: const Duration(milliseconds: 200),
                              alignment: state.isOnline ? Alignment.centerRight : Alignment.centerLeft,
                              child: Container(
                                width: 14,
                                height: 14,
                                margin: const EdgeInsets.symmetric(horizontal: 2),
                                decoration: const BoxDecoration(
                                  shape: BoxShape.circle,
                                  color: Colors.white,
                                ),
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 10),

            // 2. Thẻ Ca Làm Khẩn Cấp Kế Tiếp Hôm Nay (Hero Card nếu có)
            if (_urgentShift != null && !_isLoadingUrgent)
              UrgentShiftHeroCard(
                assignment: _urgentShift!,
                onNavigate: () => _openNavigation(_urgentShift!),
                onQrScan: () => _openQrScanner(_urgentShift!),
              ),

            // 3. Thanh Bán Kính & Chuyển Đổi Bản Đồ / Danh Sách
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
              child: Row(
                children: [
                  const Text(
                    'Bán kính:',
                    style: TextStyle(fontSize: 12, fontWeight: FontWeight.w800, color: Color(0xFF0F172A)),
                  ),
                  const SizedBox(width: 8),
                  Wrap(
                    spacing: 6,
                    children: [5.0, 10.0, 15.0].map((r) {
                      final isSel = state.searchRadiusKm == r;
                      return InkWell(
                        onTap: () => notifier.setRadius(r),
                        borderRadius: BorderRadius.circular(999),
                        child: Container(
                          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 5),
                          decoration: BoxDecoration(
                            color: isSel ? const Color(0xFF0077FF) : Colors.white,
                            borderRadius: BorderRadius.circular(999),
                            border: Border.all(
                              color: isSel ? const Color(0xFF0077FF) : const Color(0xFFE2E8F0),
                            ),
                            boxShadow: isSel
                                ? const [BoxShadow(color: Color(0x400077FF), blurRadius: 6, offset: Offset(0, 2))]
                                : null,
                          ),
                          child: Text(
                            '${r.toInt()} km',
                            style: TextStyle(
                              fontSize: 11,
                              fontWeight: FontWeight.w800,
                              color: isSel ? Colors.white : const Color(0xFF64748B),
                            ),
                          ),
                        ),
                      );
                    }).toList(),
                  ),
                  const Spacer(),
                  InkWell(
                    onTap: () => notifier.toggleRadarView(),
                    borderRadius: BorderRadius.circular(999),
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                      decoration: BoxDecoration(
                        color: Colors.white,
                        borderRadius: BorderRadius.circular(999),
                        border: Border.all(color: const Color(0xFFE2E8F0)),
                      ),
                      child: Row(
                        children: [
                          Icon(
                            state.isRadarView ? Icons.list_alt : Icons.map_outlined,
                            size: 14,
                            color: const Color(0xFF0077FF),
                          ),
                          const SizedBox(width: 4),
                          Text(
                            state.isRadarView ? 'Danh sách' : 'Bản đồ',
                            style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w700, color: Color(0xFF0077FF)),
                          ),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 6),

            // 4. Nội Dung Chính: Bản Đồ Radar Hoặc Danh Sách
            Expanded(
              child: state.isLoading
                  ? const Center(child: CircularProgressIndicator())
                  : state.isRadarView
                      ? Stack(
                          children: [
                            // Bản đồ Radar Map
                            Positioned.fill(
                              child: Container(
                                margin: const EdgeInsets.fromLTRB(14, 0, 14, 6),
                                decoration: BoxDecoration(
                                  borderRadius: BorderRadius.circular(24),
                                  boxShadow: [
                                    BoxShadow(
                                      color: const Color(0xFF0077FF).withValues(alpha: 0.08),
                                      blurRadius: 18,
                                    ),
                                  ],
                                ),
                                clipBehavior: Clip.antiAlias,
                                child: JobRadarMapWidget(
                                  shifts: state.nearbyShifts,
                                  selectedShift: state.selectedShift,
                                  maxRadiusKm: state.searchRadiusKm,
                                  isOnline: state.isOnline,
                                  userLat: state.userLat,
                                  userLng: state.userLng,
                                  onRecenterGps: () => notifier.refreshCurrentLocation(),
                                  onSelectShift: (shift) {
                                    notifier.selectShift(shift);
                                  },
                                ),
                              ),
                            ),

                            // Badge số ca tìm thấy ở góc trên bản đồ (Chỉ hiện khi Đang bật nhận việc)
                            if (state.isOnline)
                              Positioned(
                                top: 10,
                                right: 24,
                                child: Container(
                                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                                  decoration: BoxDecoration(
                                    color: Colors.white.withValues(alpha: 0.94),
                                    borderRadius: BorderRadius.circular(999),
                                    boxShadow: [
                                      BoxShadow(
                                        color: const Color(0xFF0B1B3D).withValues(alpha: 0.12),
                                        blurRadius: 8,
                                      ),
                                    ],
                                  ),
                                  child: Text(
                                    '${state.nearbyShifts.length} ca trong bán kính ${state.searchRadiusKm.toInt()} km',
                                    style: const TextStyle(
                                      fontSize: 10.5,
                                      fontWeight: FontWeight.w800,
                                      color: Color(0xFF1E293B),
                                    ),
                                  ),
                                ),
                              ),

                            // Sliding Job Card ở dưới đáy bản đồ
                            if (selectedOrFirstShift != null)
                              Positioned(
                                left: 0,
                                right: 0,
                                bottom: 0,
                                child: RadarSlidingJobCard(
                                  shift: selectedOrFirstShift,
                                  onViewDetails: () => _showShiftDetails(context, selectedOrFirstShift),
                                  onApply: () => _applyQuick(context, selectedOrFirstShift),
                                ),
                              ),
                          ],
                        )
                      : _buildListView(context, state, notifier),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildListView(BuildContext context, JobRadarState state, JobRadarNotifier notifier) {
    if (state.nearbyShifts.isEmpty) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.location_off_outlined, size: 48, color: Colors.grey.shade400),
            const SizedBox(height: 12),
            const Text('Không có ca làm việc nào trong khu vực này', style: TextStyle(fontWeight: FontWeight.w700)),
            const SizedBox(height: 8),
            TextButton(
              onPressed: () => notifier.setRadius(15.0),
              child: const Text('Mở rộng bán kính 15 km'),
            ),
          ],
        ),
      );
    }

    return ListView.builder(
      padding: const EdgeInsets.fromLTRB(16, 6, 16, 80),
      itemCount: state.nearbyShifts.length,
      itemBuilder: (context, index) {
        final shift = state.nearbyShifts[index];
        return Card(
          margin: const EdgeInsets.only(bottom: 12),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
          elevation: 1,
          child: InkWell(
            borderRadius: BorderRadius.circular(16),
            onTap: () {
              notifier.selectShift(shift);
              _showShiftDetails(context, shift);
            },
            child: Padding(
              padding: const EdgeInsets.all(14),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Expanded(
                        child: Text(
                          shift.eventName,
                          style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 14.5, color: Color(0xFF0F172A)),
                        ),
                      ),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                        decoration: BoxDecoration(
                          color: const Color(0xFFF0FDF4),
                          borderRadius: BorderRadius.circular(8),
                          border: Border.all(color: const Color(0xFFBBF7D0)),
                        ),
                        child: Text(
                          '${shift.distanceKm.toStringAsFixed(1)} km',
                          style: const TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: Color(0xFF16A34A)),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 5),
                  Text(
                    shift.jobType,
                    style: const TextStyle(fontWeight: FontWeight.w700, color: Color(0xFF0066FF), fontSize: 13),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    shift.locationAddress,
                    style: const TextStyle(fontSize: 12, color: Color(0xFF64748B)),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  const Divider(height: 18, color: Color(0xFFE2E8F0)),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text(
                        '${shift.hourlyWage.toInt().toString().replaceAllMapped(RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'), (m) => '${m[1]}.')} đ/h',
                        style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 15, color: Color(0xFF16A34A)),
                      ),
                      Text(
                        'Cần: ${shift.remainingWorkers}/${shift.requiredWorkers} người',
                        style: const TextStyle(fontSize: 12, color: Color(0xFFEF4444), fontWeight: FontWeight.bold),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
        );
      },
    );
  }

  void _showShiftDetails(BuildContext context, NearbyShiftItem shift) {
    final state = ref.read(jobRadarProvider);
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => JobDetailBottomSheet(
        shift: shift,
        userLat: state.userLat,
        userLng: state.userLng,
        onApply: () {
          Navigator.pop(ctx);
          _applyQuick(context, shift);
        },
      ),
    );
  }

  Future<void> _applyQuick(BuildContext context, NearbyShiftItem shift) async {
    try {
      await ref.read(jobRadarRepositoryProvider).applyForShift(shift.shiftId);
      ref.read(jobRadarProvider.notifier).scanNearbyShifts();
      _loadUrgentShift();
      if (mounted) {
        ScaffoldMessenger.of(this.context).showSnackBar(
          SnackBar(
            content: Text('Ứng tuyển thành công ca: ${shift.jobType} tại ${shift.eventName}!'),
            backgroundColor: const Color(0xFF16A34A),
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(this.context).showSnackBar(
          SnackBar(
            content: Text('Không thể ứng tuyển: ${e.toString().replaceAll("Exception: ", "")}'),
            backgroundColor: const Color(0xFFDC2626),
          ),
        );
      }
    }
  }
}
