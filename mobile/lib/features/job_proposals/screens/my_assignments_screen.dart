import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/features/job_radar/providers/job_radar_provider.dart';
import '../data/proposal_repository.dart';
import '../models/proposal_models.dart';
import '../widgets/job_proposal_invite_card.dart';
import '../widgets/shift_assignment_card.dart';
import '../widgets/shift_navigation_sheet.dart';
import 'attendance_qr_scanner_screen.dart';

class MyAssignmentsScreen extends ConsumerStatefulWidget {
  const MyAssignmentsScreen({super.key});

  @override
  ConsumerState<MyAssignmentsScreen> createState() => _MyAssignmentsScreenState();
}

class _MyAssignmentsScreenState extends ConsumerState<MyAssignmentsScreen> {
  int _selectedTabIndex = 0; // 0: Lời mời việc, 1: Ca đã nhận
  List<JobProposalItem> _proposals = [];
  List<ShiftAssignmentItem> _assignments = [];
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    _loadAllData();
  }

  Future<void> _loadAllData() async {
    setState(() => _isLoading = true);
    final repo = ref.read(proposalRepositoryProvider);
    final props = await repo.fetchProposals();
    final assigns = await repo.fetchAssignments();
    if (mounted) {
      setState(() {
        _proposals = props;
        _assignments = assigns;
        _isLoading = false;
      });
    }
  }

  Future<void> _acceptProposal(JobProposalItem item) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Xác nhận nhận việc', style: TextStyle(fontWeight: FontWeight.bold)),
        content: Text('Bạn có chắc chắn muốn nhận ca: ${item.jobType} tại sự kiện "${item.eventName}"?'),
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
        _assignments.insert(
          0,
          ShiftAssignmentItem(
            assignmentId: 'assign-${DateTime.now().millisecondsSinceEpoch}',
            shiftId: item.shiftId,
            eventId: item.eventId,
            eventName: item.eventName,
            venueAddress: item.venueAddress,
            jobType: item.jobType,
            startTime: item.startTime,
            endTime: item.endTime,
            hourlyWage: item.hourlyWage,
            totalPay: item.totalEstimatedPay,
            status: 'CONFIRMED',
            paymentStatus: 'NOT_DECLARED',
            declaredAmount: 0,
          ),
        );
        _selectedTabIndex = 1; // Tự động chuyển sang Tab Ca đã nhận
      });

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Nhận ca thành công! Ca làm đã được thêm vào mục Ca Đã Nhận.'),
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

  Future<void> _confirmPayment(ShiftAssignmentItem item, String status) async {
    final comment = status == 'NOT_RECEIVED' ? 'Chưa nhận được tiền lương từ Ban tổ chức' : '';
    await ref.read(proposalRepositoryProvider).confirmPayment(item.assignmentId, status, comment);
    setState(() {
      final index = _assignments.indexWhere((a) => a.assignmentId == item.assignmentId);
      if (index != -1) {
        _assignments[index] = ShiftAssignmentItem(
          assignmentId: item.assignmentId,
          shiftId: item.shiftId,
          eventId: item.eventId,
          eventName: item.eventName,
          venueAddress: item.venueAddress,
          jobType: item.jobType,
          startTime: item.startTime,
          endTime: item.endTime,
          hourlyWage: item.hourlyWage,
          totalPay: item.totalPay,
          status: item.status,
          paymentStatus: status,
          declaredAmount: item.declaredAmount,
          paymentMethod: item.paymentMethod,
        );
      }
    });

    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(status == 'CONFIRMED_FULL'
              ? 'Đã xác nhận nhận đủ tiền lương!'
              : 'Đã gửi khiếu nại (Dispute). Đội ngũ CANi sẽ hỗ trợ bạn!'),
          backgroundColor: status == 'CONFIRMED_FULL' ? const Color(0xFF16A34A) : const Color(0xFFEA580C),
        ),
      );
    }
  }

  void _openNavigationSheet(ShiftAssignmentItem a) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => ShiftNavigationSheet(assignment: a),
    );
  }

  Future<void> _openQrScanner(ShiftAssignmentItem a, bool isCheckin) async {
    final success = await Navigator.push<bool>(
      context,
      MaterialPageRoute(
        builder: (_) => AttendanceQrScannerScreen(
          assignment: a,
          isCheckin: isCheckin,
        ),
      ),
    );
    if (success == true) {
      _loadAllData();
    }
  }

  @override
  Widget build(BuildContext context) {
    final radarState = ref.watch(jobRadarProvider);
    final locationLabel = radarState.isRealLocation ? 'GPS Thực Tế' : 'Q.7, TP.HCM';

    return Scaffold(
      backgroundColor: const Color(0xFFF8FAFC),
      body: SafeArea(
        child: Column(
          children: [
            // Header chuẩn OpenDesign Device 02
            Container(
              padding: const EdgeInsets.fromLTRB(20, 14, 20, 14),
              color: Colors.white,
              child: Column(
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      const Text(
                        'Việc Làm Của Tôi',
                        style: TextStyle(
                          fontFamily: 'Be Vietnam Pro',
                          fontWeight: FontWeight.w800,
                          fontSize: 20,
                          color: Color(0xFF0B1B3D),
                          letterSpacing: -0.2,
                        ),
                      ),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 11, vertical: 6),
                        decoration: BoxDecoration(
                          color: const Color(0xFFF0F7FF),
                          borderRadius: BorderRadius.circular(999),
                          border: Border.all(color: const Color(0xFFDBEAFE)),
                        ),
                        child: Row(
                          children: [
                            const Icon(Icons.location_on, size: 14, color: Color(0xFF0077FF)),
                            const SizedBox(width: 4),
                            Text(
                              locationLabel,
                              style: const TextStyle(
                                fontSize: 11,
                                fontWeight: FontWeight.w700,
                                color: Color(0xFF0077FF),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 14),

                  // Segmented Tabs bo góc 13px theo OpenDesign
                  Container(
                    height: 44,
                    padding: const EdgeInsets.all(4),
                    decoration: BoxDecoration(
                      color: const Color(0xFFE8EEF7),
                      borderRadius: BorderRadius.circular(13),
                    ),
                    child: Row(
                      children: [
                        _buildSegmentButton(
                          index: 0,
                          label: 'Lời Mời Việc',
                          count: _proposals.length,
                        ),
                        const SizedBox(width: 4),
                        _buildSegmentButton(
                          index: 1,
                          label: 'Ca Đã Nhận',
                          count: _assignments.length,
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),

            // Content Feed (Tab A hoặc Tab B)
            Expanded(
              child: _isLoading
                  ? const Center(child: CircularProgressIndicator())
                  : RefreshIndicator(
                      onRefresh: _loadAllData,
                      child: _selectedTabIndex == 0
                          ? _buildProposalsFeed()
                          : _buildAssignmentsFeed(),
                    ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildSegmentButton({
    required int index,
    required String label,
    required int count,
  }) {
    final isSelected = _selectedTabIndex == index;

    return Expanded(
      child: GestureDetector(
        onTap: () => setState(() => _selectedTabIndex = index),
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 180),
          decoration: BoxDecoration(
            color: isSelected ? Colors.white : Colors.transparent,
            borderRadius: BorderRadius.circular(10),
            boxShadow: isSelected
                ? [
                    BoxShadow(
                      color: const Color(0xFF0B1B3D).withValues(alpha: 0.10),
                      blurRadius: 8,
                      offset: const Offset(0, 2),
                    ),
                  ]
                : null,
          ),
          alignment: Alignment.center,
          child: Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Container(
                constraints: const BoxConstraints(minWidth: 20),
                height: 20,
                padding: const EdgeInsets.symmetric(horizontal: 5),
                decoration: BoxDecoration(
                  color: isSelected ? const Color(0xFF0077FF) : const Color(0xFF0077FF).withValues(alpha: 0.10),
                  borderRadius: BorderRadius.circular(10),
                ),
                alignment: Alignment.center,
                child: Text(
                  '$count',
                  style: TextStyle(
                    fontSize: 10.5,
                    fontWeight: FontWeight.w800,
                    color: isSelected ? Colors.white : const Color(0xFF0077FF),
                  ),
                ),
              ),
              const SizedBox(width: 7),
              Text(
                label,
                style: TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.w800,
                  color: isSelected ? const Color(0xFF0B1B3D) : const Color(0xFF64748B),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildProposalsFeed() {
    if (_proposals.isEmpty) {
      return ListView(
        padding: const EdgeInsets.all(24),
        children: const [
          SizedBox(height: 40),
          Center(
            child: Icon(Icons.inbox_outlined, size: 56, color: Color(0xFF94A3B8)),
          ),
          SizedBox(height: 12),
          Center(
            child: Text(
              'Hiện không có lời mời việc làm nào',
              style: TextStyle(fontWeight: FontWeight.w800, fontSize: 15, color: Color(0xFF1E293B)),
            ),
          ),
          SizedBox(height: 6),
          Center(
            child: Text(
              'Hãy bật chế độ "Sẵn sàng nhận việc" ở Tab Bản đồ để thuật toán ghép nối ca cho bạn nhé!',
              textAlign: TextAlign.center,
              style: TextStyle(color: Color(0xFF64748B), fontSize: 12.5),
            ),
          ),
        ],
      );
    }

    return ListView.builder(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 80),
      itemCount: _proposals.length,
      itemBuilder: (context, index) {
        final p = _proposals[index];
        return JobProposalInviteCard(
          proposal: p,
          onAccept: () => _acceptProposal(p),
          onReject: () => _rejectProposal(p),
        );
      },
    );
  }

  Widget _buildAssignmentsFeed() {
    if (_assignments.isEmpty) {
      return ListView(
        padding: const EdgeInsets.all(24),
        children: const [
          SizedBox(height: 40),
          Center(
            child: Icon(Icons.assignment_outlined, size: 56, color: Color(0xFF94A3B8)),
          ),
          SizedBox(height: 12),
          Center(
            child: Text(
              'Bạn chưa có ca làm việc nào đã nhận',
              style: TextStyle(fontWeight: FontWeight.w800, fontSize: 15, color: Color(0xFF1E293B)),
            ),
          ),
          SizedBox(height: 6),
          Center(
            child: Text(
              'Khi bạn đồng ý một lời mời việc, ca làm sẽ xuất hiện tại đây để bạn quét QR điểm danh.',
              textAlign: TextAlign.center,
              style: TextStyle(color: Color(0xFF64748B), fontSize: 12.5),
            ),
          ),
        ],
      );
    }

    return ListView.builder(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 80),
      itemCount: _assignments.length,
      itemBuilder: (context, index) {
        final item = _assignments[index];
        final isCheckedIn = item.status == 'CHECKED_IN' || (item.checkinTime != null && item.checkoutTime == null);

        return ShiftAssignmentCard(
          assignment: item,
          onNavigate: () => _openNavigationSheet(item),
          onQrScan: () => _openQrScanner(item, !isCheckedIn),
          onConfirmPayment: (status) => _confirmPayment(item, status),
        );
      },
    );
  }
}
