import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/features/job_radar/providers/job_radar_provider.dart';
import '../data/proposal_repository.dart';
import '../models/proposal_models.dart';
import '../widgets/cancel_assignment_dialog.dart';
import '../widgets/job_proposal_invite_card.dart';
import '../widgets/payment_confirm_dialog.dart';
import '../widgets/rate_eo_dialog.dart';
import '../widgets/shift_assignment_card.dart';
import '../widgets/shift_checklist_sheet.dart';
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
    try {
      final repo = ref.read(proposalRepositoryProvider);
      final results = await Future.wait([
        repo.fetchProposals().catchError((_) => <JobProposalItem>[]),
        repo.fetchAssignments(),
      ]);
      if (mounted) {
        setState(() {
          _proposals = results[0] as List<JobProposalItem>;
          _assignments = results[1] as List<ShiftAssignmentItem>;
          _isLoading = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() => _isLoading = false);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Không thể tải danh sách ca làm: ${e.toString().replaceAll("Exception: ", "")}'),
            backgroundColor: const Color(0xFFDC2626),
          ),
        );
      }
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
      try {
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
      } catch (e) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text('Lỗi khi nhận việc: ${e.toString().replaceAll("Exception: ", "")}'),
              backgroundColor: const Color(0xFFDC2626),
            ),
          );
        }
      }
    }
  }

  Future<void> _rejectProposal(JobProposalItem item) async {
    try {
      await ref.read(proposalRepositoryProvider).rejectProposal(item.proposalId, 'Bận lịch cá nhân');
      setState(() {
        _proposals.removeWhere((p) => p.proposalId == item.proposalId);
      });
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Đã từ chối lời mời. Hệ thống sẽ điều phối việc khác cho bạn.')),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Lỗi khi từ chối lời mời: ${e.toString().replaceAll("Exception: ", "")}'),
            backgroundColor: const Color(0xFFDC2626),
          ),
        );
      }
    }
  }

  Future<void> _openPaymentDialog(ShiftAssignmentItem a) async {
    final result = await showDialog<Map<String, String>>(
      context: context,
      builder: (_) => PaymentConfirmDialog(assignment: a),
    );
    if (result != null && mounted) {
      final status = result['status']!;
      final comment = result['comment'] ?? '';
      await _confirmPayment(a, status, comment: comment);
    }
  }

  Future<void> _confirmPayment(ShiftAssignmentItem item, String status, {String comment = ''}) async {
    final isFull = status == 'RECEIVED_FULL' || status == 'CONFIRMED_FULL';
    final isPartial = status == 'RECEIVED_PARTIAL' || status == 'CONFIRMED_PARTIAL';
    final localPaymentStatus = isFull
        ? 'CONFIRMED_FULL'
        : (isPartial ? 'CONFIRMED_PARTIAL' : 'DISPUTED');

    try {
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
            paymentStatus: localPaymentStatus,
            declaredAmount: item.declaredAmount,
            paymentMethod: item.paymentMethod,
            venueLat: item.venueLat,
            venueLng: item.venueLng,
            attendanceStatus: item.attendanceStatus,
            checkinTime: item.checkinTime,
            checkoutTime: item.checkoutTime,
            hoursWorked: item.hoursWorked,
          );
        }
      });

      if (mounted) {
        String msg = 'Đã xác nhận nhận đủ tiền thù lao!';
        Color color = const Color(0xFF16A34A);
        if (isPartial) {
          msg = 'Đã ghi nhận tình trạng nhận một phần thù lao.';
          color = const Color(0xFFD97706);
        } else if (!isFull && !isPartial) {
          msg = 'Đã gửi khiếu nại (Dispute). Đội ngũ CSKH CANi sẽ liên hệ hỗ trợ bạn!';
          color = const Color(0xFFDC2626);
        }

        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(msg),
            backgroundColor: color,
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Lỗi khi gửi xác nhận thanh toán: ${e.toString().replaceAll("Exception: ", "")}'),
            backgroundColor: const Color(0xFFDC2626),
          ),
        );
      }
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

  void _openChecklistSheet(ShiftAssignmentItem a) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => ShiftChecklistSheet(assignment: a),
    );
  }

  Future<void> _openCancelDialog(ShiftAssignmentItem a) async {
    final cancelled = await showDialog<bool>(
      context: context,
      builder: (_) => CancelAssignmentDialog(assignment: a),
    );
    if (cancelled == true) {
      setState(() {
        _assignments.removeWhere((item) => item.assignmentId == a.assignmentId);
      });
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Đã hủy nhận ca làm việc. Hệ thống đang tìm người thay thế.'),
            backgroundColor: Color(0xFFDC2626),
          ),
        );
      }
    }
  }

  Future<void> _openRateDialog(ShiftAssignmentItem a) async {
    final rated = await showDialog<bool>(
      context: context,
      builder: (_) => RateEoDialog(assignment: a),
    );
    if (rated == true && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Cảm ơn bạn đã gửi đánh giá cho Ban tổ chức!'),
          backgroundColor: Color(0xFF16A34A),
        ),
      );
    }
  }

  // UC-73: Nhập mã mời ca làm việc (Screen 092)
  Future<void> _openInviteCodeDialog() async {
    final controller = TextEditingController();
    final result = await showDialog<String>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Nhập Mã Mời Tham Gia Ca', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 18)),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Nhập mã mời do Ban tổ chức sự kiện cung cấp (ví dụ: CANI-8XK2PW) để vào ca làm ngay:',
              style: TextStyle(fontSize: 13, color: Color(0xFF475569)),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: controller,
              textCapitalization: TextCapitalization.characters,
              decoration: const InputDecoration(
                hintText: 'CANI-XXXXXX',
                border: OutlineInputBorder(),
                prefixIcon: Icon(Icons.confirmation_number_outlined),
              ),
            ),
          ],
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Hủy')),
          FilledButton(
            onPressed: () {
              final code = controller.text.trim();
              if (code.isNotEmpty) Navigator.pop(ctx, code);
            },
            style: FilledButton.styleFrom(backgroundColor: const Color(0xFF0066FF)),
            child: const Text('Tham Gia'),
          ),
        ],
      ),
    );

    if (result != null && result.isNotEmpty && mounted) {
      try {
        await ref.read(proposalRepositoryProvider).joinViaInviteCode(result);
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('🎉 Tham gia ca làm thành công qua mã mời!'),
              backgroundColor: Color(0xFF16A34A),
            ),
          );
          _loadData();
        }
      } catch (err) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text('Lỗi: $err'),
              backgroundColor: const Color(0xFFDC2626),
            ),
          );
        }
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final radarState = ref.watch(jobRadarProvider);
    final locationLabel = radarState.isRealLocation ? 'GPS Thực Tế' : 'Q.7, TP.HCM';
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Scaffold(
      backgroundColor: Theme.of(context).scaffoldBackgroundColor,
      body: SafeArea(
        child: Column(
          children: [
            // Header chuẩn OpenDesign Device 02
            Container(
              padding: const EdgeInsets.fromLTRB(20, 14, 20, 14),
              color: Theme.of(context).cardColor,
              child: Column(
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text(
                        'Việc Làm Của Tôi',
                        style: TextStyle(
                          fontFamily: 'Be Vietnam Pro',
                          fontWeight: FontWeight.w800,
                          fontSize: 20,
                          color: isDark ? const Color(0xFFF8FAFC) : const Color(0xFF0B1B3D),
                          letterSpacing: -0.2,
                        ),
                      ),
                      Row(
                        children: [
                          IconButton(
                            icon: const Icon(Icons.confirmation_number_outlined, color: Color(0xFF0077FF), size: 22),
                            tooltip: 'Nhập mã mời ca làm (Screen 092)',
                            onPressed: _openInviteCodeDialog,
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
                    ],
                  ),
                  const SizedBox(height: 14),

                  // Segmented Tabs bo góc 13px theo OpenDesign
                  Container(
                    height: 44,
                    padding: const EdgeInsets.all(4),
                    decoration: BoxDecoration(
                      color: isDark ? const Color(0xFF0F172A) : const Color(0xFFE8EEF7),
                      borderRadius: BorderRadius.circular(13),
                      border: isDark ? Border.all(color: const Color(0xFF334155)) : null,
                    ),
                    child: Row(
                      children: [
                        _buildSegmentButton(
                          index: 0,
                          label: 'Lời Mời Việc',
                          count: _proposals.length,
                          isDark: isDark,
                        ),
                        const SizedBox(width: 4),
                        _buildSegmentButton(
                          index: 1,
                          label: 'Ca Đã Nhận',
                          count: _assignments.length,
                          isDark: isDark,
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
                          ? _buildProposalsFeed(isDark)
                          : _buildAssignmentsFeed(isDark),
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
    required bool isDark,
  }) {
    final isSelected = _selectedTabIndex == index;

    return Expanded(
      child: GestureDetector(
        onTap: () => setState(() => _selectedTabIndex = index),
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 180),
          decoration: BoxDecoration(
            color: isSelected
                ? (isDark ? const Color(0xFF1E293B) : Colors.white)
                : Colors.transparent,
            borderRadius: BorderRadius.circular(10),
            border: isSelected && isDark ? Border.all(color: const Color(0xFF475569)) : null,
            boxShadow: isSelected
                ? [
                    BoxShadow(
                      color: isDark
                          ? Colors.black.withValues(alpha: 0.25)
                          : const Color(0xFF0B1B3D).withValues(alpha: 0.10),
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
                  color: isSelected
                      ? const Color(0xFF0077FF)
                      : (isDark ? const Color(0xFF1E3A8A).withValues(alpha: 0.5) : const Color(0xFF0077FF).withValues(alpha: 0.10)),
                  borderRadius: BorderRadius.circular(10),
                ),
                alignment: Alignment.center,
                child: Text(
                  '$count',
                  style: TextStyle(
                    fontSize: 10.5,
                    fontWeight: FontWeight.w800,
                    color: isSelected ? Colors.white : (isDark ? const Color(0xFF38BDF8) : const Color(0xFF0077FF)),
                  ),
                ),
              ),
              const SizedBox(width: 7),
              Text(
                label,
                style: TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.w800,
                  color: isSelected
                      ? (isDark ? const Color(0xFFF8FAFC) : const Color(0xFF0B1B3D))
                      : (isDark ? const Color(0xFF94A3B8) : const Color(0xFF64748B)),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildProposalsFeed(bool isDark) {
    if (_proposals.isEmpty) {
      return ListView(
        padding: const EdgeInsets.all(24),
        children: [
          const SizedBox(height: 40),
          const Center(
            child: Icon(Icons.inbox_outlined, size: 56, color: Color(0xFF94A3B8)),
          ),
          const SizedBox(height: 12),
          Center(
            child: Text(
              'Hiện không có lời mời việc làm nào',
              style: TextStyle(
                fontWeight: FontWeight.w800,
                fontSize: 15,
                color: isDark ? const Color(0xFFF8FAFC) : const Color(0xFF1E293B),
              ),
            ),
          ),
          const SizedBox(height: 6),
          Center(
            child: Text(
              'Hãy bật chế độ "Sẵn sàng nhận việc" ở Tab Bản đồ để thuật toán ghép nối ca cho bạn nhé!',
              textAlign: TextAlign.center,
              style: TextStyle(
                color: isDark ? const Color(0xFF94A3B8) : const Color(0xFF64748B),
                fontSize: 12.5,
              ),
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

  Widget _buildAssignmentsFeed(bool isDark) {
    if (_assignments.isEmpty) {
      return ListView(
        padding: const EdgeInsets.all(24),
        children: [
          const SizedBox(height: 40),
          const Center(
            child: Icon(Icons.assignment_outlined, size: 56, color: Color(0xFF94A3B8)),
          ),
          const SizedBox(height: 12),
          Center(
            child: Text(
              'Bạn chưa có ca làm việc nào đã nhận',
              style: TextStyle(
                fontWeight: FontWeight.w800,
                fontSize: 15,
                color: isDark ? const Color(0xFFF8FAFC) : const Color(0xFF1E293B),
              ),
            ),
          ),
          const SizedBox(height: 6),
          Center(
            child: Text(
              'Khi bạn đồng ý một lời mời việc, ca làm sẽ xuất hiện tại đây để bạn quét QR điểm danh.',
              textAlign: TextAlign.center,
              style: TextStyle(
                color: isDark ? const Color(0xFF94A3B8) : const Color(0xFF64748B),
                fontSize: 12.5,
              ),
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
          onConfirmPayment: () => _openPaymentDialog(item),
          onOpenChecklist: () => _openChecklistSheet(item),
          onCancelAssignment: () => _openCancelDialog(item),
          onRateOrganizer: () => _openRateDialog(item),
        );
      },
    );
  }
}
