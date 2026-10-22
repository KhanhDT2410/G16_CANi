import 'package:flutter/material.dart';
import '../data/proposal_repository.dart';
import '../models/proposal_models.dart';

class JobProposalsScreen extends StatefulWidget {
  const JobProposalsScreen({super.key});

  @override
  State<JobProposalsScreen> createState() => _JobProposalsScreenState();
}

class _JobProposalsScreenState extends State<JobProposalsScreen> with SingleTickerProviderStateMixin {
  late TabController _tabController;
  final ProposalRepository _repository = ProposalRepository();

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
    final props = await _repository.fetchProposals();
    final assigns = await _repository.fetchAssignments();
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
      await _repository.acceptProposal(item.proposalId);
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
    await _repository.rejectProposal(item.proposalId, 'Bận lịch cá nhân');
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

    await _repository.confirmPayment(item.assignmentId, status, comment);
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

        return Card(
          margin: const EdgeInsets.only(bottom: 14),
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
                      child: Text('${a.jobType} — ${a.eventName}', style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 15)),
                    ),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                      decoration: BoxDecoration(
                        color: Colors.green.withValues(alpha: 0.15),
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: Text(a.status, style: const TextStyle(color: Colors.green, fontWeight: FontWeight.bold, fontSize: 11)),
                    ),
                  ],
                ),
                const SizedBox(height: 6),
                Text('Địa điểm: ${a.venueAddress}', style: const TextStyle(fontSize: 12, color: Colors.grey)),
                const SizedBox(height: 4),
                Text('Thù lao: ${a.totalPay.toInt().toString().replaceAllMapped(RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'), (m) => '${m[1]}.')} đ', style: const TextStyle(fontWeight: FontWeight.bold, color: Colors.green, fontSize: 13)),
                const Divider(height: 18),

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
}
