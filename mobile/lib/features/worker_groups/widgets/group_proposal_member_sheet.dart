import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/features/worker_groups/models/group_proposal_models.dart';
import 'package:cani/features/worker_groups/providers/worker_group_provider.dart';

/// Bottom sheet READ-ONLY: Xem chi tiết trạng thái sẵn sàng của từng thành viên trong đội
/// phục vụ mô hình Squad Pack (Toàn bộ cùng nhận ca hoặc cùng không).
class GroupProposalMemberSheet extends ConsumerStatefulWidget {
  final String groupId;
  final GroupProposalModel proposal;

  const GroupProposalMemberSheet({
    super.key,
    required this.groupId,
    required this.proposal,
  });

  @override
  ConsumerState<GroupProposalMemberSheet> createState() =>
      _GroupProposalMemberSheetState();
}

class _GroupProposalMemberSheetState
    extends ConsumerState<GroupProposalMemberSheet> {
  @override
  void initState() {
    super.initState();
    // Re-check điều kiện (kết quả tính mới) từ backend endpoint eligible.
    Future.microtask(() => ref
        .read(workerGroupsProvider.notifier)
        .loadEligibleMembers(widget.groupId, widget.proposal.proposalId));
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colorScheme = theme.colorScheme;

    final state = ref.watch(workerGroupsProvider);
    final fresh = state.groupProposals
        .where((p) => p.proposalId == widget.proposal.proposalId)
        .firstOrNull;
    final proposal = fresh ?? widget.proposal;
    final members = proposal.eligibleMembers;
    final packSize = proposal.claimableSlots ?? members.length;
    final eligibleCount = members.where((m) => m.eligible).length;

    return Container(
      constraints: const BoxConstraints(maxHeight: 560),
      padding: const EdgeInsets.fromLTRB(16, 20, 16, 20),
      decoration: BoxDecoration(
        color: colorScheme.surface,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(20)),
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Center(
            child: Container(
              width: 36,
              height: 4,
              decoration: BoxDecoration(
                color: colorScheme.outlineVariant,
                borderRadius: BorderRadius.circular(2),
              ),
            ),
          ),
          const SizedBox(height: 16),
          Row(
            children: [
              Icon(Icons.groups_rounded, color: colorScheme.primary, size: 22),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  'Trạng thái đội ($eligibleCount/$packSize sẵn sàng)',
                  style: theme.textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 6),
          Text(
            widget.proposal.eventName?.isNotEmpty == true
                ? '${widget.proposal.eventName} — ${widget.proposal.jobType ?? "Ca làm việc"}'
                : (widget.proposal.jobType ?? 'Ca làm việc'),
            style: theme.textTheme.bodyMedium?.copyWith(
              color: colorScheme.onSurfaceVariant,
            ),
          ),
          const SizedBox(height: 12),
          Container(
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(
              color: colorScheme.primaryContainer.withAlpha(50),
              borderRadius: BorderRadius.circular(10),
              border: Border.all(
                color: colorScheme.primaryContainer,
              ),
            ),
            child: Row(
              children: [
                Icon(Icons.info_outline_rounded,
                    size: 18, color: colorScheme.primary),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    'Toàn bộ $packSize thành viên ACTIVE sẽ nhận ca cùng nhau. 1 người vướng → cả đội không nhận được.',
                    style: theme.textTheme.bodySmall?.copyWith(
                      color: colorScheme.onSurface,
                      fontWeight: FontWeight.w500,
                    ),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 12),
          if (members.isEmpty)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 24),
              child: Center(
                child: Text(
                  'Đang kiểm tra trạng thái thành viên...',
                  style: theme.textTheme.bodyMedium?.copyWith(
                    color: colorScheme.onSurfaceVariant,
                  ),
                ),
              ),
            )
          else
            Flexible(
              child: ListView.separated(
                shrinkWrap: true,
                itemCount: members.length,
                separatorBuilder: (context, index) => const Divider(height: 1),
                itemBuilder: (ctx, idx) {
                  final m = members[idx];
                  return Padding(
                    padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 4),
                    child: Row(
                      children: [
                        CircleAvatar(
                          radius: 16,
                          backgroundColor: m.eligible
                              ? Colors.green.withAlpha(30)
                              : Colors.redAccent.withAlpha(30),
                          child: Icon(
                            m.eligible
                                ? Icons.check_circle_rounded
                                : Icons.cancel_rounded,
                            size: 18,
                            color: m.eligible ? Colors.green : Colors.redAccent,
                          ),
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                m.fullName,
                                style: theme.textTheme.bodyMedium?.copyWith(
                                  fontWeight: FontWeight.w600,
                                ),
                              ),
                              if (!m.eligible)
                                Text(
                                  groupEligibilityReasonLabel(m.reasonCode),
                                  style: theme.textTheme.bodySmall?.copyWith(
                                    color: Colors.redAccent,
                                  ),
                                ),
                            ],
                          ),
                        ),
                        Container(
                          padding: const EdgeInsets.symmetric(
                              horizontal: 8, vertical: 3),
                          decoration: BoxDecoration(
                            color: m.eligible
                                ? Colors.green.withAlpha(20)
                                : Colors.redAccent.withAlpha(20),
                            borderRadius: BorderRadius.circular(6),
                          ),
                          child: Text(
                            m.eligible ? 'Sẵn sàng' : 'Chưa đủ ĐK',
                            style: TextStyle(
                              fontSize: 11,
                              fontWeight: FontWeight.bold,
                              color:
                                  m.eligible ? Colors.green : Colors.redAccent,
                            ),
                          ),
                        ),
                      ],
                    ),
                  );
                },
              ),
            ),
          const SizedBox(height: 16),
          FilledButton(
            style: FilledButton.styleFrom(
              minimumSize: const Size.fromHeight(44),
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(10),
              ),
            ),
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('Đóng'),
          ),
        ],
      ),
    );
  }
}
