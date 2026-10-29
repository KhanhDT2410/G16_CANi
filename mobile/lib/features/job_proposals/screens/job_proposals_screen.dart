import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../data/proposal_repository.dart';
import '../models/proposal_models.dart';
import '../widgets/shift_navigation_sheet.dart';
import 'attendance_qr_scanner_screen.dart';

class JobProposalsScreen extends ConsumerStatefulWidget {
  const JobProposalsScreen({super.key});

  @override
  ConsumerState<JobProposalsScreen> createState() => _JobProposalsScreenState();
}

class _JobProposalsScreenState extends ConsumerState<JobProposalsScreen> with SingleTickerProviderStateMixin {
  late TabController _tabController;

  List<JobProposalItem> _proposals = [];
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
        title: const Text('Xác nhận nhận việc'),
        content: Text('Bạn có chắc chắn muốn nhận ca làm việc: ${item.jobType} tại sự kiện "${item.eventName}"?'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Hủy')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Nhận Ca')),
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
      });

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Nhận việc thành công! Ca làm đã được thêm vào lịch của bạn.'), backgroundColor: Colors.green),
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
    String comment = '';
    if (status == 'NOT_RECEIVED') {
      comment = 'Chưa nhận được tiền lương từ Ban tổ chức';
    } else if (status == 'RECEIVED_PARTIAL') {
      comment = 'Nhận thiếu thù lao ca làm';
    }

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
              : (status == 'NOT_RECEIVED'
                  ? 'Đã gửi khiếu nại (Dispute). CSKH sẽ hỗ trợ bạn!'
                  : 'Đã ghi nhận tình trạng nhận thiếu!')),
          backgroundColor: status == 'CONFIRMED_FULL' ? Colors.green : Colors.orange,
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Việc Làm & Lịch Phân Công'),
        bottom: TabBar(
          controller: _tabController,
          tabs: [
            Tab(text: 'Lời Mời Việc (${_proposals.length})'),
            Tab(text: 'Ca Đã Nhận (${_assignments.length})'),
          ],
        ),
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : TabBarView(
              controller: _tabController,
              children: [
                // Tab 1: Proposals
                _buildProposalsList(theme),
                // Tab 2: Assignments
                _buildAssignmentsList(theme),
              ],
            ),
    );
  }

  Widget _buildProposalsList(ThemeData theme) {
    if (_proposals.isEmpty) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24.0),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(Icons.inbox_outlined, size: 64, color: theme.colorScheme.outline),
              const SizedBox(height: 12),
              const Text('Hiện không có lời mời việc làm nào', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
              const SizedBox(height: 6),
              Text('Hãy bật chế độ "Bắt đầu kiếm việc" trên Radar để hệ thống phân bổ ca phù hợp!', textAlign: TextAlign.center, style: TextStyle(color: theme.colorScheme.onSurfaceVariant, fontSize: 13)),
            ],
          ),
        ),
      );
    }

    return ListView.builder(
      padding: const EdgeInsets.all(16),
      itemCount: _proposals.length,
      itemBuilder: (context, index) {
        final p = _proposals[index];
        return Card(
          margin: const EdgeInsets.only(bottom: 14),
          elevation: 2,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Expanded(
                      child: Text(
                        p.jobType,
                        style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
                      ),
                    ),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                      decoration: BoxDecoration(
                        color: Colors.blue.withValues(alpha: 0.15),
                        borderRadius: BorderRadius.circular(20),
                        border: Border.all(color: Colors.blue.withValues(alpha: 0.5)),
                      ),
                      child: Text('Khớp ${p.matchScore.toInt()}%', style: const TextStyle(color: Colors.blue, fontWeight: FontWeight.bold, fontSize: 12)),
                    ),
                  ],
                ),
                const SizedBox(height: 4),
                Text(p.eventName, style: TextStyle(color: theme.colorScheme.primary, fontWeight: FontWeight.w600, fontSize: 13)),
                const SizedBox(height: 10),
                Row(
                  children: [
                    const Icon(Icons.location_on_outlined, size: 16, color: Colors.grey),
                    const SizedBox(width: 4),
                    Expanded(child: Text(p.venueAddress, style: const TextStyle(fontSize: 12, color: Colors.grey), maxLines: 1, overflow: TextOverflow.ellipsis)),
                  ],
                ),
                const SizedBox(height: 4),
                Row(
                  children: [
                    const Icon(Icons.access_time, size: 16, color: Colors.grey),
                    const SizedBox(width: 4),
                    Text(
                      '${p.startTime.hour.toString().padLeft(2, '0')}:${p.startTime.minute.toString().padLeft(2, '0')} - ${p.endTime.hour.toString().padLeft(2, '0')}:${p.endTime.minute.toString().padLeft(2, '0')} (${p.startTime.day}/${p.startTime.month})',
                      style: const TextStyle(fontSize: 12, color: Colors.grey),
                    ),
                  ],
                ),
                const Divider(height: 20),
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text('Thù lao ca:', style: TextStyle(fontSize: 11, color: Colors.grey)),
                        Text('${p.totalEstimatedPay.toInt().toString().replaceAllMapped(RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'), (m) => '${m[1]}.')} đ', style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16, color: Colors.green)),
                      ],
                    ),
                    Row(
                      children: [
                        OutlinedButton(
                          onPressed: () => _rejectProposal(p),
                          style: OutlinedButton.styleFrom(visualDensity: VisualDensity.compact),
                          child: const Text('Từ chối'),
                        ),
                        const SizedBox(width: 8),
                        FilledButton(
                          onPressed: () => _acceptProposal(p),
                          style: FilledButton.styleFrom(visualDensity: VisualDensity.compact),
                          child: const Text('Nhận việc'),
                        ),
                      ],
                    ),
                  ],
                ),
              ],
            ),
          ),
        );
      },
    );
  }

  Widget _buildAssignmentsList(ThemeData theme) {
    if (_assignments.isEmpty) {
      return const Center(child: Text('Bạn chưa có ca làm việc nào đã nhận.'));
    }

    return ListView.builder(
      padding: const EdgeInsets.all(16),
      itemCount: _assignments.length,
      itemBuilder: (context, index) {
        final a = _assignments[index];
        final isDeclared = a.paymentStatus == 'DECLARED_PENDING_WORKER';
        final isConfirmed = a.paymentStatus == 'CONFIRMED_FULL';

        final isCheckedIn = a.status == 'CHECKED_IN' || (a.checkinTime != null && a.checkoutTime == null);
        final isCompleted = a.status == 'COMPLETED' || a.checkoutTime != null;
        final isNotStarted = !isCheckedIn && !isCompleted;

        return Card(
          margin: const EdgeInsets.only(bottom: 14),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // Header: Tên ca & Trạng thái
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Expanded(
                      child: Text('${a.jobType} — ${a.eventName}', style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 15)),
                    ),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                      decoration: BoxDecoration(
                        color: isCompleted
                            ? Colors.purple.shade50
                            : (isCheckedIn ? Colors.green.shade50 : Colors.blue.shade50),
                        borderRadius: BorderRadius.circular(12),
                        border: Border.all(
                          color: isCompleted
                              ? Colors.purple.shade200
                              : (isCheckedIn ? Colors.green.shade300 : Colors.blue.shade200),
                        ),
                      ),
                      child: Text(
                        isCompleted
                            ? 'ĐÃ HOÀN THÀNH'
                            : (isCheckedIn ? 'ĐANG TRONG CA' : 'CHỜ CHECK-IN'),
                        style: TextStyle(
                          color: isCompleted
                              ? Colors.purple.shade800
                              : (isCheckedIn ? Colors.green.shade800 : Colors.blue.shade800),
                          fontWeight: FontWeight.bold,
                          fontSize: 10,
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 6),
                Row(
                  children: [
                    const Icon(Icons.location_on_outlined, size: 14, color: Colors.grey),
                    const SizedBox(width: 4),
                    Expanded(
                      child: Text('Địa điểm: ${a.venueAddress}', style: const TextStyle(fontSize: 12, color: Colors.grey), maxLines: 1, overflow: TextOverflow.ellipsis),
                    ),
                  ],
                ),
                const SizedBox(height: 4),
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(
                      'Thù lao: ${a.totalPay.toInt().toString().replaceAllMapped(RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'), (m) => '${m[1]}.')} đ',
                      style: TextStyle(fontWeight: FontWeight.bold, color: Colors.green.shade800, fontSize: 13),
                    ),
                    if (a.hoursWorked != null)
                      Text(
                        'Đã làm: ${a.hoursWorked!.toStringAsFixed(1)}h',
                        style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: Colors.teal.shade800),
                      ),
                  ],
                ),

                // Lịch sử Check-in / Check-out nếu có
                if (a.checkinTime != null || a.checkoutTime != null) ...[
                  const SizedBox(height: 8),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                    decoration: BoxDecoration(
                      color: Colors.grey.shade100,
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Row(
                      children: [
                        Icon(Icons.schedule, size: 14, color: Colors.grey.shade700),
                        const SizedBox(width: 6),
                        Expanded(
                          child: Text(
                            '${a.checkinTime != null ? "Vào: ${a.checkinTime!.hour.toString().padLeft(2, '0')}:${a.checkinTime!.minute.toString().padLeft(2, '0')}" : ""}${a.checkoutTime != null ? " | Ra: ${a.checkoutTime!.hour.toString().padLeft(2, '0')}:${a.checkoutTime!.minute.toString().padLeft(2, '0')}" : ""}',
                            style: TextStyle(fontSize: 11, color: Colors.grey.shade800, fontWeight: FontWeight.w500),
                          ),
                        ),
                      ],
                    ),
                  ),
                ],

                const SizedBox(height: 12),

                // Nút Hành Động: Chỉ đường & Quét QR Điểm Danh
                Row(
                  children: [
                    // Nút Chỉ đường
                    Expanded(
                      flex: 4,
                      child: OutlinedButton.icon(
                        onPressed: () => _openNavigationSheet(a),
                        icon: const Icon(Icons.directions, size: 18),
                        label: const Text('Chỉ đường', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold)),
                        style: OutlinedButton.styleFrom(
                          padding: const EdgeInsets.symmetric(vertical: 10),
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                        ),
                      ),
                    ),
                    const SizedBox(width: 8),

                    // Nút Quét QR Check-in hoặc Check-out
                    if (isNotStarted) ...[
                      Expanded(
                        flex: 5,
                        child: FilledButton.icon(
                          onPressed: () => _openQrScanner(a, true),
                          icon: const Icon(Icons.qr_code_scanner, size: 18),
                          label: const Text('QR Check-in', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold)),
                          style: FilledButton.styleFrom(
                            backgroundColor: Colors.teal.shade700,
                            padding: const EdgeInsets.symmetric(vertical: 10),
                            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                          ),
                        ),
                      ),
                    ] else if (isCheckedIn) ...[
                      Expanded(
                        flex: 5,
                        child: FilledButton.icon(
                          onPressed: () => _openQrScanner(a, false),
                          icon: const Icon(Icons.exit_to_app, size: 18),
                          label: const Text('QR Check-out', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold)),
                          style: FilledButton.styleFrom(
                            backgroundColor: Colors.deepOrange.shade700,
                            padding: const EdgeInsets.symmetric(vertical: 10),
                            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                          ),
                        ),
                      ),
                    ],
                  ],
                ),

                const Divider(height: 20),

                // Khung xác nhận nhận lương trực tiếp (Mục 12 SRS)
                Container(
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(
                    color: isConfirmed
                        ? Colors.green.withValues(alpha: 0.1)
                        : (isDeclared ? Colors.amber.withValues(alpha: 0.1) : Colors.grey.withValues(alpha: 0.08)),
                    borderRadius: BorderRadius.circular(10),
                    border: Border.all(
                      color: isConfirmed
                          ? Colors.green
                          : (isDeclared ? Colors.amber : Colors.transparent),
                    ),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          const Text('Thanh toán tiền lương:', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 12)),
                          Text(
                            isConfirmed
                                ? '✓ Đã nhận đủ tiền'
                                : (isDeclared ? 'EO đã khai báo trả lương' : 'Chờ EO thanh toán'),
                            style: TextStyle(
                              color: isConfirmed ? Colors.green : (isDeclared ? Colors.orange : Colors.grey),
                              fontWeight: FontWeight.bold,
                              fontSize: 12,
                            ),
                          ),
                        ],
                      ),
                      if (isDeclared) ...[
                        const SizedBox(height: 6),
                        Text(
                          'EO đã khai báo chi trả: ${a.declaredAmount.toInt()} đ (${a.paymentMethod == 'BANK_TRANSFER' ? 'Chuyển khoản' : 'Tiền mặt'}). Vui lòng kiểm tra tài khoản và xác nhận:',
                          style: const TextStyle(fontSize: 11),
                        ),
                        const SizedBox(height: 8),
                        Row(
                          children: [
                            Expanded(
                              child: FilledButton.tonal(
                                onPressed: () => _confirmPayment(a, 'CONFIRMED_FULL'),
                                style: FilledButton.styleFrom(visualDensity: VisualDensity.compact),
                                child: const Text('✓ Đã nhận đủ', style: TextStyle(fontSize: 11)),
                              ),
                            ),
                            const SizedBox(width: 6),
                            Expanded(
                              child: OutlinedButton(
                                onPressed: () => _confirmPayment(a, 'NOT_RECEIVED'),
                                style: OutlinedButton.styleFrom(visualDensity: VisualDensity.compact),
                                child: const Text('✕ Chưa nhận', style: TextStyle(fontSize: 11, color: Colors.red)),
                              ),
                            ),
                          ],
                        ),
                      ],
                    ],
                  ),
                ),
              ],
            ),
          ),
        );
      },
    );
  }

  void _openNavigationSheet(ShiftAssignmentItem a) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => ShiftNavigationSheet(assignment: a),
    );
  }

  Future<void> _openQrScanner(ShiftAssignmentItem a, bool isCheckin) async {
    final success = await Navigator.push<bool>(
      context,
      MaterialPageRoute(
        builder: (ctx) => AttendanceQrScannerScreen(
          assignment: a,
          isCheckin: isCheckin,
        ),
      ),
    );

    if (success == true) {
      _loadData();
    }
  }
}
