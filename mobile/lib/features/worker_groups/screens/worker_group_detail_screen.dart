import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/features/worker_groups/models/worker_group_models.dart';
import 'package:cani/features/worker_groups/providers/worker_group_provider.dart';

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
      ref
          .read(workerGroupsProvider.notifier)
          .loadGroupMembers(widget.group.groupId);
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
                  ],
                ),
              ),

              const SizedBox(height: 20),

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
