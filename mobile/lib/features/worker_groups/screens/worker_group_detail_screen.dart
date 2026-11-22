import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/features/auth/auth.dart';
import 'package:cani/features/worker_groups/models/group_proposal_models.dart';
import 'package:cani/features/worker_groups/models/worker_group_models.dart';
import 'package:cani/features/worker_groups/providers/worker_group_provider.dart';
import 'package:cani/features/worker_groups/widgets/group_proposal_member_sheet.dart';

class WorkerGroupDetailScreen extends ConsumerStatefulWidget {
  final WorkerGroupModel group;

  const WorkerGroupDetailScreen({super.key, required this.group});

  @override
  ConsumerState<WorkerGroupDetailScreen> createState() =>
      _WorkerGroupDetailScreenState();
}

class _WorkerGroupDetailScreenState
    extends ConsumerState<WorkerGroupDetailScreen> {
  @override
  void initState() {
    super.initState();
    Future.microtask(() {
      final notifier = ref.read(workerGroupsProvider.notifier);
      notifier.loadGroupMembers(widget.group.groupId);
      notifier.loadGroupProposals(widget.group.groupId);
    });
  }

  void _showInviteDialog() {
    final workerIdController = TextEditingController();
    final roleTitleController = TextEditingController();

    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        title: const Row(
          children: [
            Icon(Icons.person_add_rounded, color: Color(0xFF0D9488)),
            SizedBox(width: 8),
            Text(
              'Mời thành viên mới',
              style: TextStyle(fontSize: 17, fontWeight: FontWeight.bold),
            ),
          ],
        ),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Mã số nhân sự',
              style: TextStyle(fontSize: 12.5, fontWeight: FontWeight.w600),
            ),
            const SizedBox(height: 6),
            TextField(
              controller: workerIdController,
              decoration: InputDecoration(
                hintText: 'Nhập mã định danh nhân sự',
                hintStyle:
                    const TextStyle(fontSize: 12, color: Color(0xFF94A3B8)),
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(10),
                ),
                contentPadding:
                    const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
              ),
            ),
            const SizedBox(height: 12),
            const Text(
              'Chức danh trong nhóm (Tùy chọn)',
              style: TextStyle(fontSize: 12.5, fontWeight: FontWeight.w600),
            ),
            const SizedBox(height: 6),
            TextField(
              controller: roleTitleController,
              decoration: InputDecoration(
                hintText: 'VD: Phó nhóm, Bếp chính, Phụ trách kỹ thuật...',
                hintStyle:
                    const TextStyle(fontSize: 12, color: Color(0xFF94A3B8)),
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(10),
                ),
                contentPadding:
                    const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
              ),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(),
            child: const Text('Hủy', style: TextStyle(color: Color(0xFF64748B))),
          ),
          ElevatedButton(
            style: ElevatedButton.styleFrom(
              backgroundColor: const Color(0xFF0D9488),
              foregroundColor: Colors.white,
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(8),
              ),
            ),
            onPressed: () async {
              final id = workerIdController.text.trim();
              if (id.isEmpty) return;
              Navigator.of(ctx).pop();

              final success = await ref
                  .read(workerGroupsProvider.notifier)
                  .inviteMember(
                    groupId: widget.group.groupId,
                    workerId: id,
                    roleTitle: roleTitleController.text.trim(),
                  );

              if (mounted) {
                ScaffoldMessenger.of(context).showSnackBar(
                  SnackBar(
                    content: Text(success
                        ? 'Đã gửi lời mời tham gia nhóm!'
                        : 'Mời thành viên thất bại. Vui lòng kiểm tra lại ID!'),
                    backgroundColor:
                        success ? const Color(0xFF0D9488) : Colors.redAccent,
                  ),
                );
              }
            },
            child: const Text('Gửi lời mời'),
          ),
        ],
      ),
    );
  }

  // ==================== Task 7: Lời mời ca cho đội (chỉ leader) ====================

  bool _isCurrentUserLeader(WorkerGroupsState state) {
    final authUser = ref.watch(authStateProvider).user;
    if (authUser == null) return false;
    // Compare workerId: find current user's workerId from group members, then check if they're leader
    final myMembership = state.currentGroupMembers
        .where((m) => m.userId == authUser.id)
        .firstOrNull;
    if (myMembership == null) return false;
    return myMembership.isLeader;
  }

  void _rejectProposal(GroupProposalModel proposal) {
    final reasonController = TextEditingController();
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        title: const Text('Từ chối lời mời ca',
            style: TextStyle(fontSize: 17, fontWeight: FontWeight.bold)),
        content: TextField(
          controller: reasonController,
          decoration: InputDecoration(
            hintText: 'Lý do (tùy chọn)',
            hintStyle: const TextStyle(fontSize: 12, color: Color(0xFF94A3B8)),
            border: OutlineInputBorder(
              borderRadius: BorderRadius.circular(10),
            ),
            contentPadding: const EdgeInsets.symmetric(
                horizontal: 12, vertical: 10),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(),
            child:
                const Text('Hủy', style: TextStyle(color: Color(0xFF64748B))),
          ),
          ElevatedButton(
            style: ElevatedButton.styleFrom(
              backgroundColor: Colors.redAccent,
              foregroundColor: Colors.white,
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(8),
              ),
            ),
            onPressed: () async {
              final reason = reasonController.text.trim();
              Navigator.of(ctx).pop();
              final success = await ref
                  .read(workerGroupsProvider.notifier)
                  .rejectGroupProposal(
                    widget.group.groupId,
                    proposal.proposalId,
                    reason,
                  );
              if (mounted) {
                ScaffoldMessenger.of(context).showSnackBar(
                  SnackBar(
                    content: Text(success
                        ? 'Đã từ chối lời mời ca'
                        : 'Từ chối thất bại. Vui lòng thử lại!'),
                    backgroundColor:
                        success ? const Color(0xFF0D9488) : Colors.redAccent,
                  ),
                );
              }
            },
            child: const Text('Từ chối'),
          ),
        ],
      ),
    );
  }

  void _confirmFullSquadProposal(GroupProposalModel proposal) {
    final packSize = proposal.claimableSlots ?? proposal.eligibleMembers.length;
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        title: const Row(
          children: [
            Icon(Icons.groups_rounded, color: Color(0xFF0D9488)),
            SizedBox(width: 8),
            Text('Nhận ca trọn đội', style: TextStyle(fontSize: 17, fontWeight: FontWeight.bold)),
          ],
        ),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Đội của bạn sẽ nhận trọn gói ca "${proposal.jobType ?? 'Công việc'}" (${proposal.eventName ?? 'Sự kiện'}).',
              style: const TextStyle(fontSize: 13.5, color: Color(0xFF334155)),
            ),
            const SizedBox(height: 12),
            Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: const Color(0xFFF0FDFA),
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: const Color(0xFFCCFBF1)),
              ),
              child: Text(
                'Toàn bộ $packSize thành viên ACTIVE sẽ nhận ca cùng nhau. 1 người vướng → cả đội không nhận được.',
                style: const TextStyle(fontSize: 12.5, fontWeight: FontWeight.w600, color: Color(0xFF0F766E)),
              ),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(),
            child: const Text('Hủy', style: TextStyle(color: Color(0xFF64748B))),
          ),
          FilledButton(
            style: FilledButton.styleFrom(
              backgroundColor: const Color(0xFF0D9488),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
            ),
            onPressed: () async {
              Navigator.of(ctx).pop();
              final result = await ref
                  .read(workerGroupsProvider.notifier)
                  .acceptGroupProposal(widget.group.groupId, proposal.proposalId);
              if (mounted) {
                if (result.success) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(
                      content: Text('Đội của bạn đã nhận ca trọn đội thành công!'),
                      backgroundColor: Color(0xFF0D9488),
                    ),
                  );
                } else if (result.statusCode == 409) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(
                      content: Text('Ca vừa đủ người trước khi đội xác nhận'),
                      backgroundColor: Colors.redAccent,
                    ),
                  );
                } else {
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(
                      content: Text(result.message ??
                          'Không thể nhận ca. Ca có thể đã đủ người hoặc có thành viên không đủ điều kiện.'),
                      backgroundColor: Colors.redAccent,
                    ),
                  );
                }
              }
            },
            child: const Text('Xác nhận nhận ca'),
          ),
        ],
      ),
    );
  }

  void _openMemberSheet(GroupProposalModel p) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => GroupProposalMemberSheet(
        groupId: widget.group.groupId,
        proposal: p,
      ),
    );
  }

  Widget _buildProposalCard(GroupProposalModel p) {
    final isPending = p.status.toUpperCase() == 'PENDING';
    return Container(
      margin: const EdgeInsets.only(bottom: 10),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: const Color(0xFFE2E8F0)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              if (p.emergency) ...[
                const Text('🚨 GẤP ',
                    style: TextStyle(fontSize: 13, fontWeight: FontWeight.bold)),
                const SizedBox(width: 4),
              ],
              Expanded(
                child: Text(
                  p.eventName?.isNotEmpty == true
                      ? p.eventName!
                      : (p.jobType ?? 'Lời mời ca'),
                  style: const TextStyle(
                    fontSize: 14.5,
                    fontWeight: FontWeight.bold,
                    color: Color(0xFF0F172A),
                  ),
                  overflow: TextOverflow.ellipsis,
                ),
              ),
            ],
          ),
          if (p.jobType != null &&
              p.eventName?.isNotEmpty == true &&
              p.jobType != p.eventName)
            Text(
              p.jobType!,
              style: const TextStyle(fontSize: 12, color: Color(0xFF64748B)),
            ),
          const SizedBox(height: 10),
          Row(
            children: [
              Container(
                padding:
                    const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                decoration: BoxDecoration(
                  color: const Color(0xFFF0FDFA),
                  border: Border.all(color: const Color(0xFFCCFBF1)),
                  borderRadius: BorderRadius.circular(99),
                ),
                child: Text(
                  'Còn ${p.remainingWorkers ?? 0} slot',
                  style: const TextStyle(
                    fontSize: 11.5,
                    fontWeight: FontWeight.w700,
                    color: Color(0xFF0D9488),
                  ),
                ),
              ),
              const SizedBox(width: 8),
              Container(
                padding:
                    const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                decoration: BoxDecoration(
                  color: const Color(0xFFF0FDFA),
                  border: Border.all(color: const Color(0xFFCCFBF1)),
                  borderRadius: BorderRadius.circular(99),
                ),
                child: Text(
                  '${p.eligibleCount ?? 0} thành viên đủ điều kiện',
                  style: const TextStyle(
                    fontSize: 11.5,
                    fontWeight: FontWeight.w600,
                    color: Color(0xFF0F766E),
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          Row(
            children: [
              const Icon(Icons.schedule_rounded,
                  size: 14, color: Color(0xFF94A3B8)),
              const SizedBox(width: 4),
              Text(
                '${formatHm(p.startTime)} - ${formatHm(p.endTime)}'
                '${p.startTime != null ? ' (${p.startTime!.day}/${p.startTime!.month})' : ''}',
                style: const TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.w600,
                  color: Color(0xFF64748B),
                ),
              ),
            ],
          ),
          const SizedBox(height: 4),
          Row(
            children: [
              const Icon(Icons.attach_money_rounded,
                  size: 14, color: Color(0xFF94A3B8)),
              const SizedBox(width: 4),
              Text(
                p.hourlyWage != null
                    ? '${formatWage(p.hourlyWage)} đ/giờ'
                    : 'Thỏa thuận lương',
                style: const TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.w600,
                  color: Color(0xFF64748B),
                ),
              ),
            ],
          ),
          if (p.expiresAt != null) ...[
            const SizedBox(height: 4),
            Row(
              children: [
                const Icon(Icons.hourglass_bottom_rounded,
                    size: 14, color: Color(0xFF94A3B8)),
                const SizedBox(width: 4),
                Text(
                  p.status.toUpperCase() == 'EXPIRED'
                      ? 'Đã hết hạn'
                      : 'Hết hạn: ${formatHm(p.expiresAt)}',
                  style: const TextStyle(
                    fontSize: 12,
                    color: Color(0xFF94A3B8),
                  ),
                ),
              ],
            ),
          ],
          const SizedBox(height: 10),
          Container(
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(
              color: const Color(0xFFF0FDFA),
              borderRadius: BorderRadius.circular(8),
              border: Border.all(color: const Color(0xFFCCFBF1)),
            ),
            child: Row(
              children: [
                const Icon(Icons.info_outline_rounded,
                    size: 16, color: Color(0xFF0D9488)),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    'Toàn bộ ${p.claimableSlots ?? p.eligibleMembers.length} thành viên ACTIVE sẽ nhận ca cùng nhau. 1 người vướng → cả đội không nhận được.',
                    style: const TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.w500,
                      color: Color(0xFF0F766E),
                    ),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 4),
          Align(
            alignment: Alignment.centerRight,
            child: TextButton.icon(
              onPressed: () => _openMemberSheet(p),
              icon: const Icon(Icons.visibility_outlined, size: 14),
              label: const Text('Xem trạng thái đội',
                  style: TextStyle(fontSize: 12)),
              style: TextButton.styleFrom(
                foregroundColor: const Color(0xFF0D9488),
                visualDensity: VisualDensity.compact,
              ),
            ),
          ),
          if (isPending) ...[
            const SizedBox(height: 8),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton.icon(
                    onPressed: () => _rejectProposal(p),
                    icon: const Icon(Icons.close_rounded, size: 15),
                    label:
                        const Text('Từ chối', style: TextStyle(fontSize: 12.5)),
                    style: OutlinedButton.styleFrom(
                      foregroundColor: Colors.redAccent,
                      side: const BorderSide(color: Colors.redAccent),
                      padding:
                          const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(8),
                      ),
                    ),
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: FilledButton.icon(
                    onPressed: () => _confirmFullSquadProposal(p),
                    icon: const Icon(Icons.check_rounded, size: 16),
                    label: const Text(
                      'Nhận ca trọn đội',
                      style: TextStyle(
                          fontSize: 12.5, fontWeight: FontWeight.bold),
                    ),
                    style: FilledButton.styleFrom(
                      backgroundColor: const Color(0xFF0D9488),
                      foregroundColor: Colors.white,
                      padding: const EdgeInsets.symmetric(
                          horizontal: 10, vertical: 8),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(10),
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildProposalsSection(WorkerGroupsState state) {
    final header = Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(
          'Lời mời ca cho đội (${state.groupProposals.length})',
          style: const TextStyle(
            fontSize: 16,
            fontWeight: FontWeight.bold,
            color: Color(0xFF0F172A),
          ),
        ),
        IconButton(
          icon: const Icon(Icons.refresh_rounded,
              color: Color(0xFF0D9488), size: 18),
          tooltip: 'Tải lại',
          onPressed: () => ref
              .read(workerGroupsProvider.notifier)
              .loadGroupProposals(widget.group.groupId),
        ),
      ],
    );

    if (state.isProposalLoading) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          header,
          const SizedBox(height: 8),
          const Center(
            child: Padding(
              padding: EdgeInsets.all(20),
              child:
                  CircularProgressIndicator(color: Color(0xFF0D9488)),
            ),
          ),
        ],
      );
    }

    if (state.groupProposals.isEmpty) {
      return Container(
        width: double.infinity,
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: const Color(0xFFE2E8F0)),
        ),
        child: Column(
          children: [
            const Icon(Icons.event_busy_rounded,
                size: 32, color: Color(0xFF94A3B8)),
            const SizedBox(height: 6),
            const Text(
              'Chưa có lời mời ca nào cho đội',
              style: TextStyle(color: Color(0xFF64748B), fontSize: 13),
            ),
          ],
        ),
      );
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        header,
        const SizedBox(height: 8),
        ...state.groupProposals.map((p) => _buildProposalCard(p)),
      ],
    );
  }

  void _showTransferLeaderSheet() {
    final state = ref.read(workerGroupsProvider);
    if (state.currentGroupMembers.isEmpty) return;
    final currentLeader = state.currentGroupMembers.firstWhere(
          (m) => m.isLeader,
          orElse: () => state.currentGroupMembers.first,
        );
    final candidates = state.currentGroupMembers
        .where((m) => m.status.toUpperCase() == 'ACTIVE')
        .where((m) => m.workerId != currentLeader.workerId)
        .toList();

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => Container(
        padding: const EdgeInsets.all(16),
        decoration: const BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 36,
              height: 4,
              decoration: BoxDecoration(
                color: const Color(0xFFE2E8F0),
                borderRadius: BorderRadius.circular(2),
              ),
            ),
            const SizedBox(height: 14),
            const Text(
              'Chuyển giao đội trưởng',
              style: TextStyle(
                fontSize: 16,
                fontWeight: FontWeight.bold,
                color: Color(0xFF0F172A),
              ),
            ),
            const SizedBox(height: 4),
            const Text(
              'Chọn thành viên tiếp quản (đề xuất ca đang chờ sẽ hết hạn)',
              style: TextStyle(fontSize: 12, color: Color(0xFF64748B)),
            ),
            const SizedBox(height: 12),
            if (candidates.isEmpty)
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 12),
                child: Text(
                  'Không có thành viên ACTIVE nào khác',
                  style: TextStyle(color: Color(0xFF64748B), fontSize: 13),
                ),
              )
            else
              ...candidates.map(
                (m) => ListTile(
                  dense: true,
                  leading: CircleAvatar(
                    radius: 18,
                    backgroundColor: const Color(0xFFCCFBF1),
                    child: Text(
                      m.fullName.isNotEmpty ? m.fullName[0].toUpperCase() : 'W',
                      style: const TextStyle(
                        fontWeight: FontWeight.bold,
                        color: Color(0xFF0D9488),
                      ),
                    ),
                  ),
                  title: Text(m.fullName,
                      style: const TextStyle(
                          fontSize: 14, fontWeight: FontWeight.w600)),
                  subtitle: Text(
                    m.roleTitle ?? 'Thành viên',
                    style: const TextStyle(
                        fontSize: 12, color: Color(0xFF64748B)),
                  ),
                  onTap: () async {
                    final messenger = ScaffoldMessenger.of(context);
                    Navigator.of(context).pop();
                    final success = await ref
                        .read(workerGroupsProvider.notifier)
                        .transferLeader(widget.group.groupId, m.workerId);
                    if (success) {
                      await ref
                          .read(workerGroupsProvider.notifier)
                          .loadGroupMembers(widget.group.groupId);
                    }
                    if (mounted) {
                      messenger.showSnackBar(
                        SnackBar(
                          content: Text(success
                              ? 'Đã chuyển giao đội trưởng cho ${m.fullName}'
                              : 'Chuyển giao thất bại. Vui lòng thử lại!'),
                          backgroundColor: success
                              ? const Color(0xFF0D9488)
                              : Colors.redAccent,
                        ),
                      );
                    }
                  },
                ),
              ),
          ],
        ),
      ),
    );
  }

  void _confirmRemoveMember(WorkerGroupMemberModel member) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        title: Text(member.isLeader ? 'Rời nhóm' : 'Xóa thành viên'),
        content: Text(
          member.isLeader
              ? 'Bạn có chắc chắn muốn rời khỏi nhóm này không?'
              : 'Bạn có chắc muốn xóa "${member.fullName}" khỏi đội nhóm?',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(),
            child: const Text('Hủy', style: TextStyle(color: Color(0xFF64748B))),
          ),
          ElevatedButton(
            style: ElevatedButton.styleFrom(
              backgroundColor: Colors.redAccent,
              foregroundColor: Colors.white,
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(8),
              ),
            ),
            onPressed: () async {
              Navigator.of(ctx).pop();
              final success = await ref
                  .read(workerGroupsProvider.notifier)
                  .removeMember(
                    groupId: widget.group.groupId,
                    workerId: member.workerId,
                  );

              if (mounted) {
                ScaffoldMessenger.of(context).showSnackBar(
                  SnackBar(
                    content: Text(success
                        ? 'Đã cập nhật trạng thái thành viên!'
                        : 'Thao tác thất bại!'),
                    backgroundColor:
                        success ? const Color(0xFF0D9488) : Colors.redAccent,
                  ),
                );
                if (member.isLeader && success) {
                  Navigator.of(context).pop();
                }
              }
            },
            child: const Text('Xác nhận'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(workerGroupsProvider);

    return Scaffold(
      backgroundColor: const Color(0xFFF8FAFC),
      appBar: AppBar(
        title: Text(
          widget.group.name,
          style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 17),
        ),
        backgroundColor: Colors.white,
        foregroundColor: const Color(0xFF0F172A),
        elevation: 0.5,
        actions: [
          IconButton(
            icon: const Icon(Icons.person_add_alt_1_rounded,
                color: Color(0xFF0D9488)),
            tooltip: 'Mời thành viên',
            onPressed: _showInviteDialog,
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: () async {
          await ref
              .read(workerGroupsProvider.notifier)
              .loadGroupMembers(widget.group.groupId);
        },
        child: SingleChildScrollView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Group Hero Card
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  gradient: const LinearGradient(
                    colors: [Color(0xFF0D9488), Color(0xFF0F766E)],
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                  ),
                  borderRadius: BorderRadius.circular(16),
                  boxShadow: [
                    BoxShadow(
                      color: const Color(0xFF0D9488).withValues(alpha: 0.25),
                      blurRadius: 12,
                      offset: const Offset(0, 4),
                    ),
                  ],
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Container(
                          padding: const EdgeInsets.symmetric(
                              horizontal: 10, vertical: 4),
                          decoration: BoxDecoration(
                            color: Colors.white.withValues(alpha: 0.2),
                            borderRadius: BorderRadius.circular(99),
                          ),
                          child: Text(
                            widget.group.skillFocus ?? 'Đa kỹ năng',
                            style: const TextStyle(
                              color: Colors.white,
                              fontSize: 12,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                        ),
                        const Spacer(),
                        const Icon(Icons.shield_rounded,
                            color: Colors.amberAccent, size: 16),
                        const SizedBox(width: 4),
                        Text(
                          '${widget.group.reliabilityScore} RS',
                          style: const TextStyle(
                            color: Colors.white,
                            fontWeight: FontWeight.bold,
                            fontSize: 12.5,
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 12),
                    Text(
                      widget.group.name,
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 20,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    if (widget.group.description != null &&
                        widget.group.description!.isNotEmpty) ...[
                      const SizedBox(height: 6),
                      Text(
                        widget.group.description!,
                        style: TextStyle(
                          color: Colors.white.withValues(alpha: 0.9),
                          fontSize: 13,
                        ),
                      ),
                    ],
                    const SizedBox(height: 16),
                    Row(
                      children: [
                        _buildHeroStat(
                          icon: Icons.people_outline_rounded,
                          label: 'Thành viên',
                          value:
                              '${state.currentGroupMembers.length}/${widget.group.maxSize}',
                        ),
                        const SizedBox(width: 16),
                        _buildHeroStat(
                          icon: Icons.star_rounded,
                          label: 'Đánh giá',
                          value: '${widget.group.avgRating}',
                        ),
                        const SizedBox(width: 16),
                        _buildHeroStat(
                          icon: Icons.military_tech_rounded,
                          label: 'Trưởng nhóm',
                          value: widget.group.leaderName ?? 'Tôi',
                        ),
                      ],
                    ),
                    if (widget.group.inviteCode != null &&
                        widget.group.inviteCode!.isNotEmpty) ...[
                      const SizedBox(height: 16),
                      Container(
                        padding: const EdgeInsets.symmetric(
                            horizontal: 12, vertical: 8),
                        decoration: BoxDecoration(
                          color: Colors.white.withValues(alpha: 0.15),
                          borderRadius: BorderRadius.circular(10),
                          border: Border.all(
                              color: Colors.white.withValues(alpha: 0.3)),
                        ),
                        child: Row(
                          children: [
                            const Icon(Icons.tag_rounded,
                                size: 16, color: Colors.white70),
                            const SizedBox(width: 6),
                            Text(
                              'Mã mời: ${widget.group.inviteCode}',
                              style: const TextStyle(
                                color: Colors.white,
                                fontWeight: FontWeight.bold,
                                fontSize: 13,
                                letterSpacing: 0.5,
                              ),
                            ),
                            const Spacer(),
                            InkWell(
                              onTap: () {
                                Clipboard.setData(ClipboardData(
                                    text: widget.group.inviteCode!));
                                ScaffoldMessenger.of(context).showSnackBar(
                                  const SnackBar(
                                    content: Text(
                                        'Đã sao chép mã mời vào bộ nhớ tạm!'),
                                    backgroundColor: Colors.green,
                                    duration: Duration(seconds: 2),
                                  ),
                                );
                              },
                              child: Container(
                                padding: const EdgeInsets.symmetric(
                                    horizontal: 8, vertical: 4),
                                decoration: BoxDecoration(
                                  color: Colors.white,
                                  borderRadius: BorderRadius.circular(6),
                                ),
                                child: const Row(
                                  mainAxisSize: MainAxisSize.min,
                                  children: [
                                    Icon(Icons.copy_rounded,
                                        size: 13, color: Color(0xFF0D9488)),
                                    SizedBox(width: 4),
                                    Text(
                                      'Sao chép',
                                      style: TextStyle(
                                        color: Color(0xFF0D9488),
                                        fontSize: 11,
                                        fontWeight: FontWeight.bold,
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ],
                ),
              ),

              const SizedBox(height: 20),

              // Task 7: Section lời mời ca + chuyển giao đội trưởng (chỉ leader)
              if (_isCurrentUserLeader(state)) ...[
                OutlinedButton.icon(
                  onPressed: _showTransferLeaderSheet,
                  icon: const Icon(Icons.account_balance_rounded, size: 15),
                  label: const Text(
                    'Chuyển giao đội trưởng',
                    style: TextStyle(fontSize: 12.5, fontWeight: FontWeight.w600),
                  ),
                  style: OutlinedButton.styleFrom(
                    foregroundColor: const Color(0xFF0D9488),
                    side: const BorderSide(color: Color(0xFFCCFBF1)),
                    backgroundColor: const Color(0xFFF0FDFA),
                    padding:
                        const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(8),
                    ),
                  ),
                ),
                const SizedBox(height: 8),
                _buildProposalsSection(state),
                const SizedBox(height: 12),
              ],

              // Title Thành viên
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    'Thành viên (${state.currentGroupMembers.length})',
                    style: const TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.bold,
                      color: Color(0xFF0F172A),
                    ),
                  ),
                  OutlinedButton.icon(
                    onPressed: _showInviteDialog,
                    icon: const Icon(Icons.person_add_rounded, size: 15),
                    label: const Text(
                      'Mời thành viên',
                      style: TextStyle(fontSize: 12.5, fontWeight: FontWeight.w600),
                    ),
                    style: OutlinedButton.styleFrom(
                      foregroundColor: const Color(0xFF0D9488),
                      side: const BorderSide(color: Color(0xFFCCFBF1)),
                      backgroundColor: const Color(0xFFF0FDFA),
                      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(8),
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 8),

              // Members List
              if (state.isMemberLoading)
                const Center(
                  child: Padding(
                    padding: EdgeInsets.all(32),
                    child: CircularProgressIndicator(color: Color(0xFF0D9488)),
                  ),
                )
              else if (state.currentGroupMembers.isEmpty)
                Container(
                  width: double.infinity,
                  padding: const EdgeInsets.all(24),
                  decoration: BoxDecoration(
                    color: Colors.white,
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: const Color(0xFFE2E8F0)),
                  ),
                  child: Column(
                    children: [
                      const Icon(Icons.people_outline_rounded,
                          size: 40, color: Color(0xFF94A3B8)),
                      const SizedBox(height: 8),
                      const Text(
                        'Chưa có thành viên nào trong nhóm',
                        style: TextStyle(
                            color: Color(0xFF64748B), fontSize: 13.5),
                      ),
                      const SizedBox(height: 12),
                      OutlinedButton.icon(
                        onPressed: _showInviteDialog,
                        icon: const Icon(Icons.person_add_rounded, size: 16),
                        label: const Text('Mời đồng nghiệp gia nhập'),
                        style: OutlinedButton.styleFrom(
                          foregroundColor: const Color(0xFF0D9488),
                          side: const BorderSide(color: Color(0xFF0D9488)),
                        ),
                      ),
                    ],
                  ),
                )
              else
                ListView.separated(
                  shrinkWrap: true,
                  physics: const NeverScrollableScrollPhysics(),
                  itemCount: state.currentGroupMembers.length,
                  separatorBuilder: (_, _) => const SizedBox(height: 8),
                  itemBuilder: (ctx, idx) {
                    final member = state.currentGroupMembers[idx];
                    return Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: Colors.white,
                        borderRadius: BorderRadius.circular(12),
                        border: Border.all(color: const Color(0xFFE2E8F0)),
                      ),
                      child: Row(
                        children: [
                          CircleAvatar(
                            radius: 20,
                            backgroundColor: const Color(0xFFCCFBF1),
                            backgroundImage: member.avatarUrl != null
                                ? NetworkImage(member.avatarUrl!)
                                : null,
                            child: member.avatarUrl == null
                                ? Text(
                                    member.fullName.isNotEmpty
                                        ? member.fullName[0].toUpperCase()
                                        : 'W',
                                    style: const TextStyle(
                                      fontWeight: FontWeight.bold,
                                      color: Color(0xFF0D9488),
                                    ),
                                  )
                                : null,
                          ),
                          const SizedBox(width: 12),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Row(
                                  children: [
                                    Flexible(
                                      child: Text(
                                        member.fullName,
                                        style: const TextStyle(
                                          fontWeight: FontWeight.bold,
                                          fontSize: 14,
                                          color: Color(0xFF0F172A),
                                        ),
                                        overflow: TextOverflow.ellipsis,
                                      ),
                                    ),
                                    const SizedBox(width: 6),
                                    if (member.isLeader)
                                      Container(
                                        padding: const EdgeInsets.symmetric(
                                            horizontal: 6, vertical: 2),
                                        decoration: BoxDecoration(
                                          color: const Color(0xFFFEF3C7),
                                          borderRadius:
                                              BorderRadius.circular(6),
                                        ),
                                        child: const Text(
                                          'Trưởng nhóm',
                                          style: TextStyle(
                                            fontSize: 10,
                                            fontWeight: FontWeight.bold,
                                            color: Color(0xFFB45309),
                                          ),
                                        ),
                                      ),
                                  ],
                                ),
                                const SizedBox(height: 3),
                                Text(
                                  member.roleTitle ??
                                      (member.isLeader
                                          ? 'Điều hành nhóm'
                                          : 'Thành viên'),
                                  style: const TextStyle(
                                    fontSize: 12,
                                    color: Color(0xFF64748B),
                                  ),
                                ),
                              ],
                            ),
                          ),
                          Column(
                            crossAxisAlignment: CrossAxisAlignment.end,
                            children: [
                              Row(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  const Icon(Icons.star_rounded,
                                      color: Colors.amber, size: 14),
                                  const SizedBox(width: 2),
                                  Text(
                                    '${member.avgRating}',
                                    style: const TextStyle(
                                      fontSize: 12,
                                      fontWeight: FontWeight.w600,
                                    ),
                                  ),
                                ],
                              ),
                              const SizedBox(height: 2),
                              Text(
                                '${member.totalShiftsCompleted} ca hoàn thành',
                                style: const TextStyle(
                                  fontSize: 10.5,
                                  color: Color(0xFF94A3B8),
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(width: 8),
                          PopupMenuButton<String>(
                            icon: const Icon(Icons.more_vert,
                                size: 18, color: Color(0xFF94A3B8)),
                            onSelected: (val) {
                              if (val == 'remove') {
                                _confirmRemoveMember(member);
                              }
                            },
                            itemBuilder: (ctx) => [
                              PopupMenuItem(
                                value: 'remove',
                                child: Text(
                                  member.isLeader ? 'Rời nhóm' : 'Xóa khỏi nhóm',
                                  style: const TextStyle(
                                    color: Colors.redAccent,
                                    fontSize: 13,
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ],
                      ),
                    );
                  },
                ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildHeroStat({
    required IconData icon,
    required String label,
    required String value,
  }) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, color: Colors.white70, size: 13),
            const SizedBox(width: 4),
            Text(
              label,
              style: const TextStyle(color: Colors.white70, fontSize: 11),
            ),
          ],
        ),
        const SizedBox(height: 2),
        Text(
          value,
          style: const TextStyle(
            color: Colors.white,
            fontWeight: FontWeight.bold,
            fontSize: 13,
          ),
        ),
      ],
    );
  }
}
