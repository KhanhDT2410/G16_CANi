import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/features/worker_groups/models/worker_group_models.dart';
import 'package:cani/features/worker_groups/providers/worker_group_provider.dart';
import 'package:cani/features/worker_groups/screens/worker_group_detail_screen.dart';
import 'package:cani/features/worker_groups/widgets/create_group_bottom_sheet.dart';

class WorkerGroupsScreen extends ConsumerStatefulWidget {
  const WorkerGroupsScreen({super.key});

  @override
  ConsumerState<WorkerGroupsScreen> createState() => _WorkerGroupsScreenState();
}

class _WorkerGroupsScreenState extends ConsumerState<WorkerGroupsScreen>
    with SingleTickerProviderStateMixin {
  late TabController _tabController;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 2, vsync: this);
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  void _openCreateGroupSheet(BuildContext context) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => const CreateGroupBottomSheet(),
    );
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(workerGroupsProvider);

    return Scaffold(
      backgroundColor: const Color(0xFFF8FAFC),
      appBar: AppBar(
        title: const Text(
          'Đội Nhóm Của Tôi',
          style: TextStyle(fontWeight: FontWeight.bold, fontSize: 17),
        ),
        backgroundColor: Colors.white,
        foregroundColor: const Color(0xFF0F172A),
        elevation: 0.5,
        bottom: TabBar(
          controller: _tabController,
          labelColor: const Color(0xFF0D9488),
          unselectedLabelColor: const Color(0xFF64748B),
          indicatorColor: const Color(0xFF0D9488),
          indicatorWeight: 3,
          labelStyle: const TextStyle(fontWeight: FontWeight.w700, fontSize: 13.5),
          tabs: [
            Tab(text: 'Đội của tôi (${state.myGroups.length})'),
            Tab(
              child: Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const Text('Lời mời gia nhập'),
                  if (state.pendingInvites.isNotEmpty) ...[
                    const SizedBox(width: 6),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                      decoration: BoxDecoration(
                        color: Colors.red,
                        borderRadius: BorderRadius.circular(10),
                      ),
                      child: Text(
                        '${state.pendingInvites.length}',
                        style: const TextStyle(color: Colors.white, fontSize: 11, fontWeight: FontWeight.bold),
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ],
        ),
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _openCreateGroupSheet(context),
        backgroundColor: const Color(0xFF0D9488),
        foregroundColor: Colors.white,
        icon: const Icon(Icons.group_add_rounded),
        label: const Text(
          'Lập đội',
          style: TextStyle(fontWeight: FontWeight.bold),
        ),
      ),
      body: TabBarView(
        controller: _tabController,
        children: [
          // TAB 1: ĐỘI CỦA TÔI
          RefreshIndicator(
            onRefresh: () async {
              await ref.read(workerGroupsProvider.notifier).loadMyGroups();
              await ref.read(workerGroupsProvider.notifier).loadMyPendingInvites();
            },
            child: state.isLoading
                ? const Center(
                    child: CircularProgressIndicator(color: Color(0xFF0D9488)),
                  )
                : state.myGroups.isEmpty
                    ? _buildEmptyState(context)
                    : ListView.separated(
                        padding: const EdgeInsets.fromLTRB(16, 16, 16, 80),
                        itemCount: state.myGroups.length,
                        separatorBuilder: (_, _) => const SizedBox(height: 12),
                        itemBuilder: (ctx, idx) {
                          final group = state.myGroups[idx];
                          return _buildGroupCard(context, group);
                        },
                      ),
          ),

          // TAB 2: LỜI MỜI GIA NHẬP
          RefreshIndicator(
            onRefresh: () async {
              await ref.read(workerGroupsProvider.notifier).loadMyPendingInvites();
            },
            child: state.pendingInvites.isEmpty
                ? _buildEmptyInvitesState()
                : ListView.separated(
                    padding: const EdgeInsets.fromLTRB(16, 16, 16, 80),
                    itemCount: state.pendingInvites.length,
                    separatorBuilder: (_, _) => const SizedBox(height: 12),
                    itemBuilder: (ctx, idx) {
                      final group = state.pendingInvites[idx];
                      return _buildInviteCard(context, group);
                    },
                  ),
          ),
        ],
      ),
    );
  }

  Widget _buildEmptyState(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              padding: const EdgeInsets.all(20),
              decoration: BoxDecoration(
                color: const Color(0xFFF0FDFA),
                shape: BoxShape.circle,
                border: Border.all(color: const Color(0xFFCCFBF1)),
              ),
              child: const Icon(
                Icons.groups_rounded,
                size: 56,
                color: Color(0xFF0D9488),
              ),
            ),
            const SizedBox(height: 16),
            const Text(
              'Chưa tham gia đội nào',
              style: TextStyle(
                fontSize: 17,
                fontWeight: FontWeight.bold,
                color: Color(0xFF0F172A),
              ),
            ),
            const SizedBox(height: 8),
            const Text(
              'Lập đội cùng bạn bè hoặc đồng nghiệp để dễ dàng nhận các ca làm lớn, tăng thu nhập và nhận ưu tiên từ nhà tổ chức!',
              textAlign: TextAlign.center,
              style: TextStyle(
                fontSize: 13,
                color: Color(0xFF64748B),
                height: 1.4,
              ),
            ),
            const SizedBox(height: 24),
            ElevatedButton.icon(
              onPressed: () => _openCreateGroupSheet(context),
              icon: const Icon(Icons.add_rounded),
              label: const Text('Lập đội ngay'),
              style: ElevatedButton.styleFrom(
                backgroundColor: const Color(0xFF0D9488),
                foregroundColor: Colors.white,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
                padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildEmptyInvitesState() {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              padding: const EdgeInsets.all(20),
              decoration: BoxDecoration(
                color: const Color(0xFFF1F5F9),
                shape: BoxShape.circle,
                border: Border.all(color: const Color(0xFFE2E8F0)),
              ),
              child: const Icon(
                Icons.mail_outline_rounded,
                size: 56,
                color: Color(0xFF94A3B8),
              ),
            ),
            const SizedBox(height: 16),
            const Text(
              'Không có lời mời nào',
              style: TextStyle(
                fontSize: 17,
                fontWeight: FontWeight.bold,
                color: Color(0xFF0F172A),
              ),
            ),
            const SizedBox(height: 8),
            const Text(
              'Khi các trưởng nhóm gửi lời mời bạn vào đội, danh sách sẽ hiển thị tại đây để bạn xem xét và chấp nhận.',
              textAlign: TextAlign.center,
              style: TextStyle(
                fontSize: 13,
                color: Color(0xFF64748B),
                height: 1.4,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildInviteCard(BuildContext context, WorkerGroupModel group) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: const Color(0xFFCCFBF1), width: 1.5),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.04),
            blurRadius: 8,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  group.name,
                  style: const TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.bold,
                    color: Color(0xFF0F172A),
                  ),
                  overflow: TextOverflow.ellipsis,
                ),
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: const Color(0xFFF0FDFA),
                  borderRadius: BorderRadius.circular(99),
                  border: Border.all(color: const Color(0xFFCCFBF1)),
                ),
                child: Text(
                  group.skillFocus ?? 'Đa kỹ năng',
                  style: const TextStyle(
                    color: Color(0xFF0D9488),
                    fontSize: 11,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
            ],
          ),
          if (group.description != null && group.description!.isNotEmpty) ...[
            const SizedBox(height: 6),
            Text(
              group.description!,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(fontSize: 12.5, color: Color(0xFF64748B)),
            ),
          ],
          const SizedBox(height: 14),
          Row(
            children: [
              const Icon(Icons.people_outline_rounded, size: 16, color: Color(0xFF64748B)),
              const SizedBox(width: 4),
              Text(
                '${group.memberCount} thành viên',
                style: const TextStyle(fontSize: 12, color: Color(0xFF64748B)),
              ),
              const SizedBox(width: 16),
              const Icon(Icons.shield_outlined, size: 15, color: Color(0xFF0D9488)),
              const SizedBox(width: 3),
              Text(
                '${group.reliabilityScore} RS',
                style: const TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.bold,
                  color: Color(0xFF0D9488),
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),
          SizedBox(
            width: double.infinity,
            child: FilledButton.icon(
              icon: const Icon(Icons.check_circle_outline, size: 18),
              label: const Text('CHẤP NHẬN GIA NHẬP ĐỘI'),
              style: FilledButton.styleFrom(
                backgroundColor: const Color(0xFF0D9488),
                padding: const EdgeInsets.symmetric(vertical: 12),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
              ),
              onPressed: () async {
                final success = await ref
                    .read(workerGroupsProvider.notifier)
                    .acceptInvite(group.groupId);
                if (context.mounted) {
                  if (success) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(
                        content: Text('Đã gia nhập nhóm "${group.name}" thành công!'),
                        backgroundColor: Colors.green,
                      ),
                    );
                    _tabController.animateTo(0);
                  } else {
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(
                        content: Text('Chấp nhận lời mời thất bại. Vui lòng thử lại.'),
                        backgroundColor: Colors.red,
                      ),
                    );
                  }
                }
              },
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildGroupCard(BuildContext context, WorkerGroupModel group) {
    return InkWell(
      onTap: () {
        Navigator.of(context).push(
          MaterialPageRoute(
            builder: (_) => WorkerGroupDetailScreen(group: group),
          ),
        );
      },
      borderRadius: BorderRadius.circular(16),
      child: Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: const Color(0xFFE2E8F0)),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.03),
              blurRadius: 8,
              offset: const Offset(0, 2),
            ),
          ],
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    group.name,
                    style: const TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.bold,
                      color: Color(0xFF0F172A),
                    ),
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                  decoration: BoxDecoration(
                    color: const Color(0xFFF0FDFA),
                    borderRadius: BorderRadius.circular(99),
                    border: Border.all(color: const Color(0xFFCCFBF1)),
                  ),
                  child: Text(
                    group.skillFocus ?? 'Đa kỹ năng',
                    style: const TextStyle(
                      color: Color(0xFF0D9488),
                      fontSize: 11,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
              ],
            ),
            if (group.description != null && group.description!.isNotEmpty) ...[
              const SizedBox(height: 6),
              Text(
                group.description!,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(fontSize: 12.5, color: Color(0xFF64748B)),
              ),
            ],
            const SizedBox(height: 14),
            Row(
              children: [
                const Icon(Icons.people_outline_rounded, size: 16, color: Color(0xFF64748B)),
                const SizedBox(width: 4),
                Text(
                  '${group.memberCount} thành viên',
                  style: const TextStyle(fontSize: 12, color: Color(0xFF64748B)),
                ),
                const SizedBox(width: 16),
                const Icon(Icons.star_rounded, size: 16, color: Colors.amber),
                const SizedBox(width: 2),
                Text(
                  '${group.avgRating}',
                  style: const TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.w600,
                    color: Color(0xFF334155),
                  ),
                ),
                const SizedBox(width: 16),
                const Icon(Icons.shield_outlined, size: 15, color: Color(0xFF0D9488)),
                const SizedBox(width: 3),
                Text(
                  '${group.reliabilityScore} RS',
                  style: const TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.bold,
                    color: Color(0xFF0D9488),
                  ),
                ),
                const Spacer(),
                const Icon(Icons.chevron_right_rounded, color: Color(0xFF94A3B8)),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
