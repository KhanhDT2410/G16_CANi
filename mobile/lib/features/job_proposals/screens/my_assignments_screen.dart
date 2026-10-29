import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../data/proposal_repository.dart';
import '../models/proposal_models.dart';
import '../widgets/cancel_assignment_dialog.dart';
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

class _MyAssignmentsScreenState extends ConsumerState<MyAssignmentsScreen> with SingleTickerProviderStateMixin {
  late TabController _tabController;
  List<ShiftAssignmentItem> _assignments = [];
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 2, vsync: this);
    _loadData();
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  Future<void> _loadData() async {
    setState(() => _isLoading = true);
    try {
      final assigns = await ref.read(proposalRepositoryProvider).fetchAssignments();
      if (mounted) {
        setState(() {
          _assignments = assigns;
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

  Future<void> _confirmPayment(ShiftAssignmentItem item, String status) async {
    final isFull = status == 'RECEIVED_FULL' || status == 'CONFIRMED_FULL';
    final apiStatus = isFull ? 'RECEIVED_FULL' : 'NOT_RECEIVED';
    final localPaymentStatus = isFull ? 'CONFIRMED_FULL' : 'DISPUTED';
    final comment = apiStatus == 'NOT_RECEIVED' ? 'Chưa nhận được tiền lương từ Ban tổ chức' : '';
    try {
      await ref.read(proposalRepositoryProvider).confirmPayment(item.assignmentId, apiStatus, comment);
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
          );
        }
      });

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(isFull
                ? 'Đã xác nhận nhận đủ tiền lương!'
                : 'Đã gửi khiếu nại (Dispute). Đội ngũ CANi sẽ hỗ trợ bạn!'),
            backgroundColor: isFull ? const Color(0xFF16A34A) : const Color(0xFFEA580C),
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
      _loadData();
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

  @override
  Widget build(BuildContext context) {
    final upcomingList = _assignments.where((a) => a.status != 'COMPLETED' && a.checkoutTime == null).toList();
    final completedList = _assignments.where((a) => a.status == 'COMPLETED' || a.checkoutTime != null).toList();

    return Scaffold(
      backgroundColor: const Color(0xFFF8FAFC),
      appBar: AppBar(
        backgroundColor: Colors.white,
        elevation: 0,
        title: const Text(
          'Lịch Ca & Việc Của Tôi',
          style: TextStyle(fontWeight: FontWeight.w800, fontSize: 18, color: Color(0xFF0F172A)),
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh, color: Color(0xFF0066FF)),
            tooltip: 'Làm mới lịch ca',
            onPressed: _loadData,
          ),
        ],
        bottom: TabBar(
          controller: _tabController,
          indicatorColor: const Color(0xFF0066FF),
          labelColor: const Color(0xFF0066FF),
          unselectedLabelColor: const Color(0xFF64748B),
          labelStyle: const TextStyle(fontWeight: FontWeight.bold, fontSize: 13.5),
          tabs: [
            Tab(text: 'Sắp tới & Đang làm (${upcomingList.length})'),
            Tab(text: 'Đã hoàn thành (${completedList.length})'),
          ],
        ),
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : TabBarView(
              controller: _tabController,
              children: [
                _buildList(upcomingList, isUpcoming: true),
                _buildList(completedList, isUpcoming: false),
              ],
            ),
    );
  }

  Widget _buildList(List<ShiftAssignmentItem> list, {required bool isUpcoming}) {
    if (list.isEmpty) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24.0),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(
                isUpcoming ? Icons.event_available_outlined : Icons.check_circle_outline,
                size: 56,
                color: const Color(0xFF94A3B8),
              ),
              const SizedBox(height: 12),
              Text(
                isUpcoming ? 'Không có ca làm việc nào sắp tới' : 'Chưa có ca làm việc nào hoàn thành',
                style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 15, color: Color(0xFF1E293B)),
              ),
              const SizedBox(height: 6),
              Text(
                isUpcoming
                    ? 'Hãy bật Radar tìm việc ở Tab Bản đồ để nhận ca mới ngay nhé!'
                    : 'Các ca làm việc sau khi check-out sẽ xuất hiện tại đây.',
                textAlign: TextAlign.center,
                style: const TextStyle(color: Color(0xFF64748B), fontSize: 12.5),
              ),
            ],
          ),
        ),
      );
    }

    return RefreshIndicator(
      onRefresh: _loadData,
      child: ListView.builder(
        padding: const EdgeInsets.all(16),
        itemCount: list.length,
        itemBuilder: (context, index) {
          final item = list[index];
          final isCheckedIn = item.status == 'CHECKED_IN' || (item.checkinTime != null && item.checkoutTime == null);

          return ShiftAssignmentCard(
            assignment: item,
            onNavigate: () => _openNavigationSheet(item),
            onQrScan: () => _openQrScanner(item, !isCheckedIn),
            onConfirmPayment: (status) => _confirmPayment(item, status),
            onOpenChecklist: () => _openChecklistSheet(item),
            onCancelAssignment: () => _openCancelDialog(item),
            onRateOrganizer: () => _openRateDialog(item),
          );
        },
      ),
    );
  }
}
