import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/features/auth/providers/auth_provider.dart';
import 'package:cani/features/job_proposals/data/proposal_repository.dart';
import 'package:cani/features/job_proposals/models/proposal_models.dart';
import 'package:cani/features/job_radar/models/job_radar_models.dart';
import 'package:cani/features/job_radar/providers/job_radar_provider.dart';
import 'package:cani/features/job_radar/widgets/job_detail_bottom_sheet.dart';
import 'package:cani/features/job_radar/widgets/job_proposal_dispatch_card.dart';
import 'package:cani/features/job_radar/widgets/job_radar_map_painter.dart';

class JobRadarScreen extends ConsumerStatefulWidget {
  const JobRadarScreen({super.key});

  @override
  ConsumerState<JobRadarScreen> createState() => _JobRadarScreenState();
}

class _JobRadarScreenState extends ConsumerState<JobRadarScreen> {
  List<JobProposalItem> _proposals = [];
  bool _isLoadingProposals = false;

  @override
  void initState() {
    super.initState();
    _loadProposals();
  }

  Future<void> _loadProposals() async {
    setState(() => _isLoadingProposals = true);
    final repo = ref.read(proposalRepositoryProvider);
    final list = await repo.fetchProposals();
    if (mounted) {
      setState(() {
        _proposals = list;
        _isLoadingProposals = false;
      });
    }
  }

  Future<void> _acceptProposal(JobProposalItem item) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Xác nhận nhận việc', style: TextStyle(fontWeight: FontWeight.bold)),
        content: Text('Bạn có chắc chắn muốn nhận ca làm: ${item.jobType} tại sự kiện "${item.eventName}"?'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Hủy')),
          FilledButton(
            onPressed: () => Navigator.pop(ctx, true),
            style: FilledButton.styleFrom(backgroundColor: const Color(0xFF0066FF)),
            child: const Text('Nhận Ca'),
          ),
        ],
      ),
    );

    if (confirmed == true) {
      await ref.read(proposalRepositoryProvider).acceptProposal(item.proposalId);
      setState(() {
        _proposals.removeWhere((p) => p.proposalId == item.proposalId);
      });

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Nhận việc thành công! Ca làm đã được thêm vào Lịch ca của bạn.'),
            backgroundColor: Color(0xFF16A34A),
          ),
        );
      }
    }
  }

  Future<void> _rejectProposal(JobProposalItem item) async {
    await ref.read(proposalRepositoryProvider).rejectProposal(item.proposalId, 'Bận lịch cá nhân');
    setState(() {
      _proposals.removeWhere((p) => p.proposalId == item.proposalId);
    });
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Đã từ chối lời mời. Hệ thống sẽ điều phối việc khác cho bạn.')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(jobRadarProvider);
    final notifier = ref.read(jobRadarProvider.notifier);
    final authState = ref.watch(authStateProvider);
    final userName = authState.user?.fullName ?? 'Bạn';

    return Scaffold(
      backgroundColor: const Color(0xFFF8FAFC),
      body: SafeArea(
        child: Column(
          children: [
            // 1. Header cá nhân hóa & Chuông thông báo
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
              child: Row(
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'Xin chào, $userName 👋',
                          style: const TextStyle(
                            fontSize: 18,
                            fontWeight: FontWeight.w900,
                            color: Color(0xFF0F172A),
                          ),
                        ),
                        const SizedBox(height: 2),
                        const Text(
                          'Sẵn sàng nhận các ca làm việc sự kiện gần bạn',
                          style: TextStyle(fontSize: 12, color: Color(0xFF64748B)),
                        ),
                      ],
                    ),
                  ),
                  if (_proposals.isNotEmpty)
                    Badge(
                      label: Text('${_proposals.length}'),
                      backgroundColor: const Color(0xFFEF4444),
                      child: IconButton(
                        icon: const Icon(Icons.notifications_active, color: Color(0xFF0066FF)),
                        onPressed: _loadProposals,
                        tooltip: 'Lời mời việc mới',
                      ),
                    ),
                ],
              ),
            ),

            // 2. Header Switch Bắt đầu kiếm việc (Online / Offline)
            Container(
              margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
              decoration: BoxDecoration(
                color: state.isOnline ? const Color(0xFFE8FDF3) : Colors.grey.shade100,
                borderRadius: BorderRadius.circular(14),
                border: Border.all(
                  color: state.isOnline ? const Color(0xFFB7F4D8) : Colors.grey.shade300,
                ),
              ),
              child: Row(
                children: [
                  Container(
                    width: 10,
                    height: 10,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      color: state.isOnline ? const Color(0xFF10B981) : Colors.grey,
                      boxShadow: state.isOnline
                          ? [const BoxShadow(color: Color(0xFF10B981), blurRadius: 6, spreadRadius: 1)]
                          : null,
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          state.isOnline ? 'CHẾ ĐỘ NHẬN VIỆC ĐANG BẬT' : 'CHẾ ĐỘ TẠM NGHỈ (OFFLINE)',
                          style: TextStyle(
                            fontWeight: FontWeight.w800,
                            fontSize: 12,
                            color: state.isOnline ? const Color(0xFF065F46) : Colors.grey.shade700,
                            letterSpacing: 0.3,
                          ),
                        ),
                        Text(
                          state.isOnline
                              ? 'Hệ thống tự động đề xuất việc trong bán kính ${state.searchRadiusKm.toInt()}km'
                              : 'Bật công tắc để quét và nhận lời mời việc',
                          style: TextStyle(
                            fontSize: 11,
                            color: state.isOnline ? const Color(0xFF047857) : Colors.grey.shade600,
                          ),
                        ),
                      ],
                    ),
                  ),
                  Switch(
                    value: state.isOnline,
                    activeColor: Colors.white,
                    activeTrackColor: const Color(0xFF10B981),
                    onChanged: (val) => notifier.toggleOnlineStatus(),
                  ),
                ],
              ),
            ),

            // 3. Thẻ Lời Mời Việc Độc Quyền (Push / Dispatch Model)
            if (_proposals.isNotEmpty)
              JobProposalDispatchCard(
                proposal: _proposals.first,
                onAccept: () => _acceptProposal(_proposals.first),
                onReject: () => _rejectProposal(_proposals.first),
              ),

            // 4. Thanh Bán Kính & Toggle Chế Độ Xem
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 6, 16, 6),
              child: Row(
                children: [
                  const Text('Bán kính:', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: Color(0xFF0F172A))),
                  const SizedBox(width: 8),
                  Wrap(
                    spacing: 6,
                    children: [5.0, 10.0, 15.0].map((r) {
                      final isSel = state.searchRadiusKm == r;
                      return InkWell(
                        onTap: () => notifier.setRadius(r),
                        borderRadius: BorderRadius.circular(8),
                        child: Container(
                          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                          decoration: BoxDecoration(
                            color: isSel ? const Color(0xFF0066FF) : const Color(0xFFF1F5F9),
                            borderRadius: BorderRadius.circular(8),
                            border: Border.all(
                              color: isSel ? const Color(0xFF0066FF) : const Color(0xFFE2E8F0),
                            ),
                          ),
                          child: Text(
                            '${r.toInt()}km',
                            style: TextStyle(
                              fontSize: 11.5,
                              fontWeight: isSel ? FontWeight.w800 : FontWeight.w600,
                              color: isSel ? Colors.white : const Color(0xFF475569),
                            ),
                          ),
                        ),
                      );
                    }).toList(),
                  ),
                  const Spacer(),
                  InkWell(
                    onTap: () => notifier.toggleRadarView(),
                    borderRadius: BorderRadius.circular(8),
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                      decoration: BoxDecoration(
                        color: const Color(0xFFF1F5F9),
                        borderRadius: BorderRadius.circular(8),
                        border: Border.all(color: const Color(0xFFE2E8F0)),
                      ),
                      child: Row(
                        children: [
                          Icon(
                            state.isRadarView ? Icons.list_alt : Icons.map_outlined,
                            size: 14,
                            color: const Color(0xFF0066FF),
                          ),
                          const SizedBox(width: 4),
                          Text(
                            state.isRadarView ? 'Danh sách' : 'Bản đồ',
                            style: const TextStyle(fontSize: 11.5, fontWeight: FontWeight.w700, color: Color(0xFF0066FF)),
                          ),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
            ),

            // 5. Body: Radar Map View hoặc List View
            Expanded(
              child: state.isLoading || _isLoadingProposals
                  ? const Center(child: CircularProgressIndicator())
                  : state.isRadarView
                      ? _buildRadarView(context, state, notifier)
                      : _buildListView(context, state, notifier),
            ),
          ],
        ),
      ),
      floatingActionButton: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          FloatingActionButton.small(
            heroTag: 'recenter_gps',
            onPressed: () => notifier.refreshCurrentLocation(),
            tooltip: 'Định vị GPS thực tế',
            backgroundColor: Colors.white,
            foregroundColor: const Color(0xFF0066FF),
            child: const Icon(Icons.my_location),
          ),
          const SizedBox(width: 10),
          FloatingActionButton.extended(
            heroTag: 'refresh_jobs',
            onPressed: () {
              notifier.scanNearbyShifts();
              _loadProposals();
            },
            icon: const Icon(Icons.refresh),
            label: const Text('Quét lại', style: TextStyle(fontWeight: FontWeight.bold)),
          ),
        ],
      ),
    );
  }

  Widget _buildRadarView(BuildContext context, JobRadarState state, JobRadarNotifier notifier) {
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
          child: Row(
            children: [
              const Icon(Icons.info_outline, size: 15, color: Color(0xFF64748B)),
              const SizedBox(width: 6),
              Text(
                'Tìm thấy ${state.nearbyShifts.length} ca làm việc phù hợp xung quanh',
                style: const TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: Color(0xFF334155)),
              ),
            ],
          ),
        ),
        Expanded(
          child: JobRadarMapWidget(
            shifts: state.nearbyShifts,
            selectedShift: state.selectedShift,
            maxRadiusKm: state.searchRadiusKm,
            isOnline: state.isOnline,
            userLat: state.userLat,
            userLng: state.userLng,
            onSelectShift: (shift) {
              notifier.selectShift(shift);
              _showShiftDetails(context, shift);
            },
          ),
        ),
      ],
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
            const Text('Không có ca làm việc nào trong khu vực này', style: TextStyle(fontWeight: FontWeight.w600)),
            const SizedBox(height: 8),
            TextButton(
              onPressed: () => notifier.setRadius(15.0),
              child: const Text('Mở rộng bán kính 15km'),
            ),
          ],
        ),
      );
    }

    return ListView.builder(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 80),
      itemCount: state.nearbyShifts.length,
      itemBuilder: (context, index) {
        final shift = state.nearbyShifts[index];
        return Card(
          margin: const EdgeInsets.only(bottom: 12),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
          elevation: 1,
          child: InkWell(
            borderRadius: BorderRadius.circular(14),
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
                          style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 15),
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
                  const SizedBox(height: 6),
                  Text(
                    shift.jobType,
                    style: const TextStyle(fontWeight: FontWeight.w600, color: Color(0xFF0066FF), fontSize: 13),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    shift.locationAddress,
                    style: TextStyle(fontSize: 12, color: Colors.grey.shade600),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  const Divider(height: 18),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text(
                        '${shift.hourlyWage.toStringAsFixed(0)} đ/h',
                        style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 15, color: Color(0xFF16A34A)),
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
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => JobDetailBottomSheet(
        shift: shift,
        onApply: () {
          Navigator.pop(ctx);
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text('Đã gửi yêu cầu ứng tuyển ca: ${shift.jobType}'),
              backgroundColor: const Color(0xFF16A34A),
            ),
          );
        },
      ),
    );
  }
}
