import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/app/cani_tokens.dart';
import 'package:cani/app/home_screen.dart';
import 'package:cani/core/utils/app_error_formatter.dart';
import 'package:cani/features/ekyc/providers/ekyc_provider.dart';
import 'package:cani/features/job_proposals/job_proposals.dart';
import 'package:cani/features/job_proposals/widgets/job_proposal_invite_card.dart';
import 'package:cani/features/job_radar/providers/job_radar_provider.dart';
import 'package:cani/shared/widgets/cani_snackbar.dart';

Future<void> acceptProposalOnRadar(
  BuildContext context,
  WidgetRef ref,
  JobProposalItem item,
) async {
  try {
    await ref.read(proposalRepositoryProvider).acceptProposal(item.proposalId);
    if (!context.mounted) return;
    final radarNotifier = ref.read(jobRadarProvider.notifier);
    radarNotifier.removeShiftFromRadar(item.shiftId);
    ref.invalidate(proposalsListProvider);
    ref.invalidate(assignmentsListProvider);
    ref.invalidate(incomeSummaryProvider);
    ref.read(assignmentsSubTabProvider.notifier).state = 0;
    unawaited(radarNotifier.scanNearbyShifts(silent: true, force: true));
    CaniSnackBar.show(
      context,
      message: 'Đã nhận ca "${item.jobType}". Xem trong mục Ca làm.',
      tone: CaniNoticeTone.success,
      actionLabel: 'Xem ca',
      bottomOffset: 76,
      onAction: () {
        ref.read(homeTabIndexProvider.notifier).state = 1;
        ref.read(assignmentsSubTabProvider.notifier).state = 0;
      },
    );
  } catch (e) {
    if (context.mounted) {
      final reason = AppErrorFormatter.format(e, fallback: 'Vui lòng thử lại.');
      CaniSnackBar.show(
        context,
        message: 'Chưa nhận được ca "${item.jobType}". $reason',
        tone: CaniNoticeTone.error,
        bottomOffset: 76,
      );
    }
  }
}

Future<void> rejectProposalOnRadar(
  BuildContext context,
  WidgetRef ref,
  JobProposalItem item,
) async {
  try {
    await ref.read(proposalRepositoryProvider).rejectProposal(item.proposalId, 'Bận lịch cá nhân');
    if (!context.mounted) return;
    final currentSelected = ref.read(jobRadarProvider).selectedShift;
    if (currentSelected?.shiftId == item.shiftId) {
      ref.read(jobRadarProvider.notifier).clearSelectedShift();
    }
    ref.invalidate(proposalsListProvider);
    CaniSnackBar.show(
      context,
      message: 'Đã bỏ qua lời mời làm việc này.',
      tone: CaniNoticeTone.info,
      bottomOffset: 76,
    );
  } catch (e) {
    if (context.mounted) {
      final reason = AppErrorFormatter.format(e, fallback: 'Vui lòng thử lại.');
      CaniSnackBar.show(
        context,
        message: 'Chưa từ chối được lời mời. $reason',
        tone: CaniNoticeTone.error,
        bottomOffset: 76,
      );
    }
  }
}

Future<void> showJobRadarInviteCodeDialog(BuildContext context, WidgetRef ref) async {
  final result = await InviteCodeDialog.show(context);

  if (result != null && result.isNotEmpty && context.mounted) {
    try {
      await ref.read(proposalRepositoryProvider).joinViaInviteCode(result);
      if (!context.mounted) return;
      ref.invalidate(proposalsListProvider);
      ref.invalidate(assignmentsListProvider);
      ref.invalidate(incomeSummaryProvider);
      ref.read(assignmentsSubTabProvider.notifier).state = 0;
      ref.read(jobRadarProvider.notifier).scanNearbyShifts();
      CaniSnackBar.show(
        context,
        message: 'Đã nhận ca qua mã mời. Xem trong mục Ca làm.',
        tone: CaniNoticeTone.success,
        actionLabel: 'Xem ca',
        bottomOffset: 76,
        onAction: () {
          ref.read(homeTabIndexProvider.notifier).state = 1;
          ref.read(assignmentsSubTabProvider.notifier).state = 0;
        },
      );
    } catch (err) {
      if (context.mounted) {
        final reason = AppErrorFormatter.format(err, fallback: 'Vui lòng thử lại.');
        CaniSnackBar.show(
          context,
          message: 'Chưa tham gia được ca bằng mã mời. $reason',
          tone: CaniNoticeTone.error,
          bottomOffset: 76,
        );
      }
    }
  }
}

void showJobRadarProposalsSheet(BuildContext context) {
  showModalBottomSheet(
    context: context,
    isScrollControlled: true,
    backgroundColor: Colors.transparent,
    builder: (_) => const JobRadarProposalsSheet(),
  );
}

class JobRadarProposalsSheet extends ConsumerWidget {
  const JobRadarProposalsSheet({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final proposalsAsync = ref.watch(proposalsListProvider);
    final now = DateTime.now();
    final active = (proposalsAsync.asData?.value ?? const <JobProposalItem>[])
        .where((p) => p.isActiveAt(now))
        .toList();
    final cani = Theme.of(context).cani;

    return SafeArea(
      top: false,
      child: Container(
        constraints: BoxConstraints(
          maxHeight: MediaQuery.of(context).size.height * 0.78,
        ),
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 16),
        decoration: BoxDecoration(
          color: Theme.of(context).scaffoldBackgroundColor,
          borderRadius: const BorderRadius.vertical(top: Radius.circular(24)),
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 40,
              height: 4,
              decoration: BoxDecoration(
                color: cani.borderStrong,
                borderRadius: BorderRadius.circular(99),
              ),
            ),
            const SizedBox(height: 12),
            Row(
              children: [
                Expanded(
                  child: Text(
                    'Lời mời làm việc (${active.length})',
                    style: TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.w800,
                      color: cani.textMain,
                    ),
                  ),
                ),
                IconButton(
                  onPressed: () => Navigator.pop(context),
                  icon: Icon(Icons.close_rounded, color: cani.textMuted),
                ),
              ],
            ),
            const SizedBox(height: 6),
            if (active.isEmpty)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 28),
                child: Text(
                  'Không còn lời mời nào đang chờ phản hồi.',
                  style: TextStyle(color: cani.textMuted, fontWeight: FontWeight.w600),
                ),
              )
            else
              Flexible(
                child: ListView.builder(
                  shrinkWrap: true,
                  itemCount: active.length,
                  itemBuilder: (_, idx) {
                    final item = active[idx];
                    return JobProposalInviteCard(
                      proposal: item,
                      onAccept: () async {
                        Navigator.pop(context);
                        await acceptProposalOnRadar(context, ref, item);
                      },
                      onReject: () async {
                        await rejectProposalOnRadar(context, ref, item);
                        if (active.length <= 1 && context.mounted) {
                          Navigator.pop(context);
                        }
                      },
                    );
                  },
                ),
              ),
          ],
        ),
      ),
    );
  }
}

/// Widget không hiển thị giao diện, chuyên theo dõi thời điểm hết hạn sớm nhất
/// của danh sách lời mời đang hoạt động để tự động làm mới [proposalsListProvider].
class JobRadarProposalsExpiryWatcher extends ConsumerStatefulWidget {
  const JobRadarProposalsExpiryWatcher({super.key});

  @override
  ConsumerState<JobRadarProposalsExpiryWatcher> createState() =>
      _JobRadarProposalsExpiryWatcherState();
}

class _JobRadarProposalsExpiryWatcherState
    extends ConsumerState<JobRadarProposalsExpiryWatcher> {
  Timer? _expiryTimer;
  DateTime? _scheduledExpiryAt;

  @override
  void dispose() {
    _expiryTimer?.cancel();
    super.dispose();
  }

  void _syncExpiryTimer(List<JobProposalItem> activeProposals) {
    DateTime? earliest;
    for (final p in activeProposals) {
      final exp = p.expiresAt;
      if (exp != null && (earliest == null || exp.isBefore(earliest))) {
        earliest = exp;
      }
    }

    if (earliest == null) {
      _expiryTimer?.cancel();
      _expiryTimer = null;
      _scheduledExpiryAt = null;
      return;
    }

    if (_scheduledExpiryAt == earliest && (_expiryTimer?.isActive ?? false)) {
      return;
    }

    _expiryTimer?.cancel();
    _scheduledExpiryAt = earliest;
    final delay = earliest.difference(DateTime.now()) + const Duration(milliseconds: 300);
    _expiryTimer = Timer(delay.isNegative ? Duration.zero : delay, () {
      if (!mounted) return;
      _scheduledExpiryAt = null;
      ref.invalidate(proposalsListProvider);
    });
  }

  @override
  Widget build(BuildContext context) {
    final isEkycApproved = ref.watch(
      ekycProvider.select((s) => s.result?.isApproved ?? false),
    );
    final proposals = ref.watch(
      proposalsListProvider.select((asyncVal) => asyncVal.asData?.value ?? const <JobProposalItem>[]),
    );

    if (isEkycApproved) {
      final now = DateTime.now();
      final activeProposals = proposals.where((p) => p.isActiveAt(now)).toList();
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) _syncExpiryTimer(activeProposals);
      });
    }

    return const SizedBox.shrink();
  }
}
