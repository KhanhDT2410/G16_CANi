import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/features/worker_groups/models/group_proposal_models.dart';
import 'package:cani/features/worker_groups/providers/worker_group_provider.dart';

/// Bottom sheet: leader chọn 1..m thành viên đủ điều kiện để nhận ca (accept).
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
  final Set<String> _selected = {};
  bool _submitting = false;

  @override
  void initState() {
    super.initState();
    // Re-check điều kiện (kết quả tính mới) trước khi leader accept.
    Future.microtask(() =>
        ref.read(workerGroupsProvider.notifier).loadEligibleMembers(
            widget.groupId, widget.proposal.proposalId));
  }

  @override
  Widget build(BuildContext context) {
    // Dùng dữ liệu mới nhất sau khi eligible re-check xong (fallback: snapshot).
    final state = ref.watch(workerGroupsProvider);
    final fresh = state.groupProposals
        .where((p) => p.proposalId == widget.proposal.proposalId)
        .firstOrNull;
    final proposal = fresh ?? widget.proposal;
    final members = proposal.eligibleMembers;
    final eligibleMembers = members.where((m) => m.eligible).toList();
    final maxSlots = proposal.claimableSlots ?? 0;
    // Re-check eligible có thể loại member đã tick: chỉ dùng phần còn hợp lệ.
    final eligibleIds = eligibleMembers.map((m) => m.workerId).toSet();
    final selected = _selected.where(eligibleIds.contains).toSet();

    return Container(
      constraints: const BoxConstraints(maxHeight: 560),
      padding: const EdgeInsets.fromLTRB(16, 20, 16, 20),
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
          Row(
            children: [
              const Icon(Icons.event_rounded, color: Color(0xFF0D9488), size: 20),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  widget.proposal.eventName?.isNotEmpty == true
                      ? widget.proposal.eventName!
                      : (widget.proposal.jobType ?? 'Lời mời ca cho đội'),
                  style: const TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.bold,
                    color: Color(0xFF0F172A),
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 6),
          Text(
            'Còn $maxSlots slot — cử 1..$maxSlots thành viên',
            style: const TextStyle(
              fontSize: 12.5,
              fontWeight: FontWeight.w600,
              color: Color(0xFF0D9488),
            ),
          ),
          const SizedBox(height: 12),
          if (members.isEmpty)
            const Padding(
              padding: EdgeInsets.symmetric(vertical: 12),
              child: Text(
                'Đội chưa có thành viên đủ điều kiện nào',
                style: TextStyle(color: Color(0xFF64748B), fontSize: 13),
              ),
            )
          else ...[
            Flexible(
              child: SizedBox(
                height: 220,
                child: ListView.builder(
                  itemCount: members.length,
                  itemBuilder: (ctx, idx) {
                    final m = members[idx];
                    final checked = selected.contains(m.workerId);
                    return CheckboxListTile(
                      dense: true,
                      controlAffinity: ListTileControlAffinity.leading,
                      value: checked,
                      onChanged: m.eligible
                          ? (v) {
                              if (v == true) _selected.add(m.workerId);
                              if (v == false) _selected.remove(m.workerId);
                              setState(() {});
                            }
                          : null,
                      activeColor: const Color(0xFF0D9488),
                      title: Text(
                        m.fullName,
                        style: TextStyle(
                          fontSize: 13.5,
                          fontWeight: FontWeight.w600,
                          color: m.eligible
                              ? const Color(0xFF0F172A)
                              : const Color(0xFF94A3B8),
                        ),
                      ),
                      subtitle: m.eligible
                          ? null
                          : Text(
                              groupEligibilityReasonLabel(m.reasonCode),
                              style: const TextStyle(
                                fontSize: 11.5,
                                color: Color(0xFF94A3B8),
                              ),
                            ),
                    );
                  },
                ),
              ),
            ),
          ],
          const SizedBox(height: 12),
          Row(
            children: [
              if (eligibleMembers.isNotEmpty && maxSlots > 0)
                OutlinedButton.icon(
                  onPressed: () {
                    final all = eligibleMembers
                        .map((m) => m.workerId)
                        .take(maxSlots)
                        .toList();
                    _selected
                      ..clear()
                      ..addAll(all);
                    setState(() {});
                  },
                  icon: const Icon(Icons.select_all, size: 15),
                  label: const Text('Tất cả',
                      style: TextStyle(fontSize: 12.5)),
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
              const SizedBox(width: 8),
              TextButton(
                onPressed: () => Navigator.of(context).pop(),
                child: const Text(
                  'Hủy',
                  style: TextStyle(color: Color(0xFF64748B)),
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          FilledButton.icon(
            style: FilledButton.styleFrom(
              backgroundColor: const Color(0xFF0D9488),
              foregroundColor: Colors.white,
              minimumSize: const Size.fromHeight(46),
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(10),
              ),
            ),
            onPressed:
                (selected.isEmpty || selected.length > maxSlots || _submitting)
                    ? null
                    : () => _submit(context, selected.toList()),
            icon: _submitting
                ? const SizedBox(
                    width: 16,
                    height: 16,
                    child: CircularProgressIndicator(
                        color: Colors.white, strokeWidth: 2),
                  )
                : const Icon(Icons.check_rounded, size: 18),
            label: Text(
              selected.isEmpty
                  ? 'Xác nhận cho đội'
                  : 'Xác nhận cho đội (${selected.length})',
              style: const TextStyle(
                  fontSize: 14, fontWeight: FontWeight.bold),
            ),
          ),
        ],
      ),
    );
  }

  Future<void> _submit(BuildContext context, List<String> workerIds) async {
    setState(() => _submitting = true);
    final success = await ref
        .read(workerGroupsProvider.notifier)
        .acceptGroupProposal(widget.groupId, widget.proposal.proposalId, workerIds);

    if (!mounted) return;
    final messenger = ScaffoldMessenger.of(context);
    Navigator.of(context).pop();
    messenger.showSnackBar(
      SnackBar(
        content: Text(success
            ? 'Đã cử ${workerIds.length} thành viên tham gia ca'
            : 'Ca vừa được fill hết hoặc không đủ điều kiện — mời chọn lại'),
        backgroundColor:
            success ? const Color(0xFF0D9488) : Colors.redAccent,
      ),
    );
  }
}
