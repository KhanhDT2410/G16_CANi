import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/app/cani_tokens.dart';
import 'package:cani/core/utils/app_error_formatter.dart';
import 'package:cani/shared/widgets/cani_snackbar.dart';
import '../data/proposal_repository.dart';
import '../models/dispute_models.dart';
import '../models/proposal_models.dart';
import '../widgets/create_dispute_sheet.dart';
import '../widgets/dispute_card.dart';
import '../widgets/dispute_detail_sheet.dart';
import 'dispute_chat_screen.dart';

enum DisputeListFilter {
  all,
  inProgress,
  hasResult,
}

class MyDisputesScreen extends ConsumerStatefulWidget {
  final ShiftAssignmentItem? assignment;

  const MyDisputesScreen({super.key, this.assignment});

  @override
  ConsumerState<MyDisputesScreen> createState() => _MyDisputesScreenState();
}

class _MyDisputesScreenState extends ConsumerState<MyDisputesScreen> {
  static const List<({String value, String label, IconData icon})> _disputeTypes = [
    (value: 'PAYMENT', label: 'Thù lao', icon: Icons.payments_outlined),
    (value: 'WORK_QUALITY', label: 'Công việc', icon: Icons.assignment_late_outlined),
    (value: 'ATTENDANCE', label: 'Điểm danh', icon: Icons.schedule_outlined),
    (value: 'CONDUCT', label: 'Ứng xử', icon: Icons.report_problem_outlined),
    (value: 'OTHER', label: 'Khác', icon: Icons.more_horiz_rounded),
  ];

  DisputeListFilter _selectedFilter = DisputeListFilter.all;

  Future<void> _refresh() async {
    ref.invalidate(myDisputesListProvider);
  }

  String _formatTime(DateTime dt) {
    final l = dt.toLocal();
    return '${l.hour.toString().padLeft(2, '0')}:${l.minute.toString().padLeft(2, '0')}';
  }

  String _formatDate(DateTime dt) => DisputeCard.formatDate(dt);

  String _formatCurrency(num value) {
    return '${value.toInt().toString().replaceAllMapped(RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'), (m) => '${m[1]}.')} đ';
  }

  List<WorkerDisputeItem> _sortAndFilter(List<WorkerDisputeItem> items) {
    final filtered = switch (_selectedFilter) {
      DisputeListFilter.all => List<WorkerDisputeItem>.from(items),
      DisputeListFilter.inProgress => items.where((d) => d.isInProgress).toList(),
      DisputeListFilter.hasResult => items.where((d) => d.hasResult).toList(),
    };

    // Trong mục Tất cả, đưa hồ sơ đang xử lý lên đầu
    filtered.sort((a, b) {
      if (a.isInProgress != b.isInProgress) {
        return a.isInProgress ? -1 : 1;
      }
      final aTime = a.createdAt;
      final bTime = b.createdAt;
      if (aTime != null && bTime != null) {
        return bTime.compareTo(aTime);
      }
      return 0;
    });
    return filtered;
  }

  void _openDisputeChat(WorkerDisputeItem item) {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => DisputeChatScreen(
          disputeId: item.id,
          disputeTitle: item.title,
          initialStatus: item.status,
        ),
      ),
    );
  }

  void _openDisputeDetail(WorkerDisputeItem item) {
    DisputeDetailSheet.show(
      context,
      item: item,
      onOpenChat: () => _openDisputeChat(item),
    );
  }

  Future<void> _showCreateDisputeDialog() async {
    final repo = ref.read(proposalRepositoryProvider);

    List<DisputeEligibleAssignmentItem> eligibleOptions = const [];
    String? selectedAssignmentId = widget.assignment?.assignmentId;

    if (selectedAssignmentId == null || selectedAssignmentId.isEmpty) {
      try {
        eligibleOptions = await repo.fetchEligibleAssignmentsForDispute();
      } catch (_) {
        eligibleOptions = const [];
      }
      if (!mounted) return;
      if (eligibleOptions.isEmpty) {
        CaniSnackBar.show(
          context,
          message: 'Khiếu nại gắn theo ca làm việc. Vui lòng vào chi tiết một ca đã nhận để tạo khiếu nại.',
          tone: CaniNoticeTone.warning,
        );
        return;
      }
      selectedAssignmentId = eligibleOptions.first.assignmentId;
    }

    if (!mounted) return;

    final cani = Theme.of(context).cani;
    final isDark = Theme.of(context).brightness == Brightness.dark;

    final created = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => CreateDisputeSheet(
        repository: repo,
        disputeTypes: _disputeTypes,
        eligibleOptions: eligibleOptions,
        initialAssignmentId: selectedAssignmentId,
        formatDate: _formatDate,
        shiftReferenceCard: widget.assignment != null
            ? _buildShiftReferenceCard(widget.assignment!, cani, isDark)
            : null,
      ),
    );

    if (created == true && mounted) {
      ref.invalidate(myDisputesListProvider);
      ref.invalidate(assignmentsListProvider);
      CaniSnackBar.show(
        context,
        message: 'Đã gửi khiếu nại thành công! CSKH sẽ phản hồi trong thời gian sớm nhất.',
        tone: CaniNoticeTone.success,
      );
    }
  }

  Widget _buildShiftReferenceCard(ShiftAssignmentItem a, CaniTokens cani, bool isDark) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF0F172A) : const Color(0xFFF0F9FF),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: cani.brand.withValues(alpha: 0.28)),
      ),
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.all(9),
            decoration: BoxDecoration(
              color: cani.brand.withValues(alpha: 0.14),
              borderRadius: BorderRadius.circular(10),
            ),
            child: Icon(Icons.work_history_rounded, color: cani.brand, size: 20),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  '${a.jobType} • ${a.eventName}',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(fontSize: 13.5, fontWeight: FontWeight.w800, color: cani.textMain),
                ),
                const SizedBox(height: 3),
                Text(
                  '${_formatDate(a.startTime)} (${_formatTime(a.startTime)} - ${_formatTime(a.endTime)}) • ${_formatCurrency(a.totalPay)}',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(fontSize: 11.5, color: cani.textMuted, fontWeight: FontWeight.w600),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildFilterBar(
    List<WorkerDisputeItem> scopedDisputes,
    CaniTokens cani,
    bool isDark,
  ) {
    final inProgressCount = scopedDisputes.where((d) => d.isInProgress).length;
    final hasResultCount = scopedDisputes.where((d) => d.hasResult).length;

    final options = <({DisputeListFilter filter, String label, int count})>[
      (filter: DisputeListFilter.all, label: 'Tất cả', count: scopedDisputes.length),
      (filter: DisputeListFilter.inProgress, label: 'Đang xử lý', count: inProgressCount),
      (filter: DisputeListFilter.hasResult, label: 'Đã có kết quả', count: hasResultCount),
    ];

    return Container(
      width: double.infinity,
      color: cani.surface,
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 12),
      child: Row(
        children: options.map((opt) {
          final isSelected = _selectedFilter == opt.filter;
          return Expanded(
            child: Padding(
              padding: EdgeInsets.only(right: opt.filter != DisputeListFilter.hasResult ? 6 : 0),
              child: InkWell(
                borderRadius: BorderRadius.circular(10),
                onTap: () => setState(() => _selectedFilter = opt.filter),
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 8),
                  decoration: BoxDecoration(
                    color: isSelected
                        ? cani.brand
                        : (isDark ? const Color(0xFF0F172A) : const Color(0xFFF1F5F9)),
                    borderRadius: BorderRadius.circular(10),
                    border: Border.all(
                      color: isSelected ? cani.brand : cani.border,
                    ),
                  ),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Flexible(
                        child: Text(
                          opt.label,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(
                            fontSize: 11.8,
                            fontWeight: isSelected ? FontWeight.w800 : FontWeight.w600,
                            color: isSelected ? Colors.white : cani.textBody,
                          ),
                        ),
                      ),
                      if (opt.count > 0) ...[
                        const SizedBox(width: 4),
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 1),
                          decoration: BoxDecoration(
                            color: isSelected
                                ? Colors.white.withValues(alpha: 0.22)
                                : cani.borderStrong,
                            borderRadius: BorderRadius.circular(99),
                          ),
                          child: Text(
                            '${opt.count}',
                            style: TextStyle(
                              fontSize: 10,
                              fontWeight: FontWeight.w800,
                              color: isSelected ? Colors.white : cani.textMain,
                            ),
                          ),
                        ),
                      ],
                    ],
                  ),
                ),
              ),
            ),
          );
        }).toList(),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final cani = Theme.of(context).cani;
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final disputesAsync = ref.watch(myDisputesListProvider);
    final shiftAssignment = widget.assignment;

    final allDisputes = disputesAsync.valueOrNull ?? const <WorkerDisputeItem>[];
    final scopedDisputes = shiftAssignment == null
        ? allDisputes
        : allDisputes.where((d) => d.assignmentId == shiftAssignment.assignmentId).toList();
    final hasActiveDisputeForShift = shiftAssignment != null && scopedDisputes.any((d) => d.isOpen);
    final isShiftCancelledBeforeWork = shiftAssignment != null &&
        shiftAssignment.status == 'CANCELLED' &&
        shiftAssignment.checkinTime == null;
    final canCreateDispute = !hasActiveDisputeForShift && !isShiftCancelledBeforeWork;

    return Scaffold(
      backgroundColor: cani.canvas,
      appBar: AppBar(
        title: Column(
          children: [
            const Text(
              'Khiếu Nại & Tranh Chấp',
              style: TextStyle(fontWeight: FontWeight.w800, fontSize: 17),
            ),
            if (shiftAssignment != null)
              Text(
                '${shiftAssignment.jobType} · ${shiftAssignment.eventName}',
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(fontSize: 11.5, color: cani.textMuted, fontWeight: FontWeight.w600),
              ),
          ],
        ),
        centerTitle: true,
        elevation: 0.5,
        backgroundColor: cani.surface,
      ),
      body: RefreshIndicator(
        onRefresh: _refresh,
        child: disputesAsync.when(
          loading: () => Center(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const CircularProgressIndicator(),
                const SizedBox(height: 12),
                Text(
                  'Đang tải danh sách hồ sơ...',
                  style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600, color: cani.textMuted),
                ),
              ],
            ),
          ),
          error: (err, _) => ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.all(24),
            children: [
              const SizedBox(height: 40),
              Center(
                child: Column(
                  children: [
                    Icon(Icons.error_outline_rounded, size: 48, color: cani.danger),
                    const SizedBox(height: 12),
                    Text(
                      'Không thể tải danh sách khiếu nại',
                      textAlign: TextAlign.center,
                      style: TextStyle(fontSize: 16, fontWeight: FontWeight.w800, color: cani.textMain),
                    ),
                    const SizedBox(height: 6),
                    Text(
                      AppErrorFormatter.format(err, fallback: 'Đã xảy ra lỗi kết nối. Vui lòng thử lại.'),
                      textAlign: TextAlign.center,
                      style: TextStyle(fontSize: 13, color: cani.textMuted),
                    ),
                    const SizedBox(height: 16),
                    FilledButton.icon(
                      onPressed: _refresh,
                      icon: const Icon(Icons.refresh_rounded, size: 17),
                      label: const Text('Thử lại'),
                      style: FilledButton.styleFrom(backgroundColor: cani.brand),
                    ),
                  ],
                ),
              ),
            ],
          ),
          data: (_) {
            final filteredDisputes = _sortAndFilter(scopedDisputes);

            return Column(
              children: [
                if (shiftAssignment != null)
                  Container(
                    color: cani.surface,
                    padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
                    child: Column(
                      children: [
                        _buildShiftReferenceCard(shiftAssignment, cani, isDark),
                        if (hasActiveDisputeForShift) ...[
                          const SizedBox(height: 8),
                          Container(
                            width: double.infinity,
                            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                            decoration: BoxDecoration(
                              color: cani.brandSoft,
                              borderRadius: BorderRadius.circular(10),
                              border: Border.all(color: cani.brand.withValues(alpha: 0.3)),
                            ),
                            child: Row(
                              children: [
                                Icon(Icons.info_outline_rounded, size: 16, color: cani.brand),
                                const SizedBox(width: 8),
                                Expanded(
                                  child: Text(
                                    'Ca làm này đang có hồ sơ khiếu nại chờ xử lý.',
                                    style: TextStyle(
                                      fontSize: 11.5,
                                      fontWeight: FontWeight.w600,
                                      color: cani.brand,
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
                _buildFilterBar(scopedDisputes, cani, isDark),
                Divider(height: 1, color: cani.border),
                Expanded(
                  child: scopedDisputes.isEmpty
                      ? _buildEmptyAllState(shiftAssignment, cani)
                      : filteredDisputes.isEmpty
                          ? _buildEmptyFilterState(cani)
                          : ListView.separated(
                              physics: const AlwaysScrollableScrollPhysics(),
                              padding: const EdgeInsets.fromLTRB(16, 14, 16, 24),
                              itemCount: filteredDisputes.length,
                              separatorBuilder: (_, _) => const SizedBox(height: 12),
                              itemBuilder: (ctx, idx) {
                                final item = filteredDisputes[idx];
                                return DisputeCard(
                                  item: item,
                                  onViewDetail: () => _openDisputeDetail(item),
                                  onOpenChat: () => _openDisputeChat(item),
                                );
                              },
                            ),
                ),
              ],
            );
          },
        ),
      ),
      bottomNavigationBar: _buildPrimaryCreateBar(
        cani: cani,
        isDark: isDark,
        canCreateDispute: canCreateDispute,
        hasActiveDisputeForShift: hasActiveDisputeForShift,
      ),
    );
  }

  Widget _buildEmptyAllState(ShiftAssignmentItem? shiftAssignment, CaniTokens cani) {
    return ListView(
      physics: const AlwaysScrollableScrollPhysics(),
      padding: const EdgeInsets.all(24),
      children: [
        const SizedBox(height: 28),
        Center(
          child: Container(
            width: 76,
            height: 76,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: cani.brand.withValues(alpha: 0.1),
            ),
            child: Icon(Icons.verified_user_rounded, size: 40, color: cani.brand),
          ),
        ),
        const SizedBox(height: 16),
        Text(
          shiftAssignment != null
              ? 'Chưa có khiếu nại cho ca làm này'
              : 'Chưa có hồ sơ khiếu nại nào',
          textAlign: TextAlign.center,
          style: TextStyle(fontSize: 17, fontWeight: FontWeight.w800, color: cani.textMain),
        ),
        const SizedBox(height: 8),
        Text(
          'Nếu phát sinh vấn đề về thù lao, giờ công hoặc ứng xử trong ca làm việc, bạn có thể gửi khiếu nại kèm bằng chứng trong vòng 14 ngày để CSKH hỗ trợ phân xử.',
          textAlign: TextAlign.center,
          style: TextStyle(fontSize: 13, color: cani.textMuted, height: 1.45),
        ),
      ],
    );
  }

  Widget _buildEmptyFilterState(CaniTokens cani) {
    final groupName = _selectedFilter == DisputeListFilter.inProgress
        ? 'đang xử lý'
        : 'đã có kết quả';
    return ListView(
      physics: const AlwaysScrollableScrollPhysics(),
      padding: const EdgeInsets.all(24),
      children: [
        const SizedBox(height: 36),
        Icon(Icons.filter_list_off_rounded, size: 44, color: cani.textMuted),
        const SizedBox(height: 12),
        Text(
          'Không có hồ sơ $groupName',
          textAlign: TextAlign.center,
          style: TextStyle(fontSize: 15.5, fontWeight: FontWeight.w800, color: cani.textMain),
        ),
        const SizedBox(height: 6),
        Text(
          'Chuyển sang mục Tất cả để xem toàn bộ hồ sơ khiếu nại của bạn.',
          textAlign: TextAlign.center,
          style: TextStyle(fontSize: 12.5, color: cani.textMuted),
        ),
        const SizedBox(height: 14),
        Center(
          child: TextButton.icon(
            onPressed: () => setState(() => _selectedFilter = DisputeListFilter.all),
            icon: const Icon(Icons.list_alt_rounded, size: 16),
            label: const Text('Xem tất cả hồ sơ', style: TextStyle(fontWeight: FontWeight.w700)),
          ),
        ),
      ],
    );
  }

  Widget _buildPrimaryCreateBar({
    required CaniTokens cani,
    required bool isDark,
    required bool canCreateDispute,
    required bool hasActiveDisputeForShift,
  }) {
    return Container(
      padding: const EdgeInsets.fromLTRB(16, 10, 16, 14),
      decoration: BoxDecoration(
        color: cani.surface,
        border: Border(top: BorderSide(color: cani.border)),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: isDark ? 0.24 : 0.05),
            blurRadius: 10,
            offset: const Offset(0, -3),
          ),
        ],
      ),
      child: SafeArea(
        top: false,
        child: SizedBox(
          width: double.infinity,
          height: 48,
          child: FilledButton.icon(
            onPressed: canCreateDispute ? _showCreateDisputeDialog : null,
            icon: const Icon(Icons.gavel_rounded, size: 18),
            label: Text(
              hasActiveDisputeForShift
                  ? 'Đang có khiếu nại chờ xử lý'
                  : 'Tạo khiếu nại',
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(fontSize: 13.5, fontWeight: FontWeight.w800),
            ),
            style: FilledButton.styleFrom(
              backgroundColor: cani.brand,
              disabledBackgroundColor: isDark ? const Color(0xFF334155) : const Color(0xFFE2E8F0),
              disabledForegroundColor: cani.textMuted,
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(12),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
