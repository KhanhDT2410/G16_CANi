import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/app/cani_tokens.dart';
import 'package:cani/features/job_proposals/data/proposal_repository.dart';
import 'package:cani/features/job_proposals/models/proposal_models.dart';
import 'package:cani/features/job_proposals/screens/shift_detail_execution_screen.dart';
import 'package:cani/features/job_proposals/widgets/history_kpi_bar.dart';
import 'package:cani/features/job_proposals/widgets/history_shift_card.dart';
import 'package:cani/features/job_proposals/widgets/payment_confirm_dialog.dart';
import 'package:cani/features/job_proposals/widgets/rate_eo_dialog.dart';

/// Trang "Lịch sử làm việc" riêng trong tab Tài khoản (Profile),
/// giúp Worker xem nhanh toàn bộ các ca đã hoàn thành, thu nhập và đánh giá.
class WorkerWorkHistoryScreen extends ConsumerStatefulWidget {
  const WorkerWorkHistoryScreen({super.key});

  @override
  ConsumerState<WorkerWorkHistoryScreen> createState() => _WorkerWorkHistoryScreenState();
}

class _WorkerWorkHistoryScreenState extends ConsumerState<WorkerWorkHistoryScreen> {
  String _filter = 'COMPLETED'; // COMPLETED, ALL, CONFIRMED_PAYMENT, CANCELLED

  Future<void> _openExecutionScreen(ShiftAssignmentItem item) async {
    final updated = await Navigator.push<bool>(
      context,
      MaterialPageRoute(builder: (_) => ShiftDetailExecutionScreen(assignment: item)),
    );
    if (updated == true && mounted) {
      ref.invalidate(assignmentsListProvider);
      ref.invalidate(incomeSummaryProvider);
    }
  }

  Future<void> _openRateDialog(ShiftAssignmentItem item) async {
    final rated = await showDialog<bool>(
      context: context,
      builder: (_) => RateEoDialog(assignment: item),
    );
    if (rated == true && mounted) {
      ref.invalidate(assignmentsListProvider);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: const Text('Cảm ơn bạn đã gửi đánh giá cho Ban tổ chức!'),
          backgroundColor: Theme.of(context).cani.success,
        ),
      );
    }
  }

  Future<void> _openPaymentDialog(ShiftAssignmentItem item) async {
    final result = await showDialog<Map<String, String>>(
      context: context,
      builder: (_) => PaymentConfirmDialog(assignment: item),
    );
    if (result != null && mounted) {
      try {
        await ref.read(proposalRepositoryProvider).confirmPayment(
              item.assignmentId,
              result['status']!,
              result['comment'] ?? '',
            );
        ref.invalidate(assignmentsListProvider);
        ref.invalidate(incomeSummaryProvider);
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: const Text('Đã cập nhật xác nhận thanh toán thù lao.'),
              backgroundColor: Theme.of(context).cani.success,
            ),
          );
        }
      } catch (e) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text('Lỗi xác nhận thanh toán: ${e.toString().replaceAll("Exception: ", "")}'),
              backgroundColor: Theme.of(context).cani.danger,
            ),
          );
        }
      }
    }
  }

  List<ShiftAssignmentItem> _applyFilter(List<ShiftAssignmentItem> history) {
    switch (_filter) {
      case 'COMPLETED':
        return history.where((a) => a.status == 'COMPLETED').toList();
      case 'CONFIRMED_PAYMENT':
        return history.where((a) => a.paymentStatus == 'CONFIRMED_FULL').toList();
      case 'CANCELLED':
        return history
            .where((a) => a.status == 'CANCELLED' || a.status == 'NO_SHOW' || a.attendanceStatus == 'NO_SHOW')
            .toList();
      default:
        return history;
    }
  }

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).cani;
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final data = ref.watch(assignmentsListProvider);
    final summaryAsync = ref.watch(incomeSummaryProvider);

    final allList = data.valueOrNull ?? const <ShiftAssignmentItem>[];
    final historyList = allList
        .where((a) =>
            a.status == 'COMPLETED' ||
            a.status == 'CANCELLED' ||
            a.status == 'NO_SHOW' ||
            a.checkoutTime != null)
        .toList()
      ..sort((a, b) => b.startTime.compareTo(a.startTime));

    final filtered = _applyFilter(historyList);

    final summary = summaryAsync.valueOrNull ??
        WorkerIncomeSummary(
          completedShiftsCount: historyList.where((a) => a.status == 'COMPLETED').length,
          totalHoursWorked: historyList
              .where((a) => a.hoursWorked != null)
              .fold(0.0, (sum, a) => sum + (a.hoursWorked ?? 0)),
          totalEarnings: historyList
              .where((a) => a.paymentStatus == 'CONFIRMED_FULL')
              .fold(0.0, (sum, a) => sum + (a.declaredAmount > 0 ? a.declaredAmount : a.totalPay)),
          pendingEarnings: historyList
              .where((a) => a.paymentStatus == 'DECLARED_PENDING_WORKER')
              .fold(0.0, (sum, a) => sum + (a.declaredAmount > 0 ? a.declaredAmount : a.totalPay)),
        );

    return Scaffold(
      appBar: AppBar(
        title: const Text('Lịch sử làm việc', style: TextStyle(fontWeight: FontWeight.w800, fontSize: 17)),
        centerTitle: true,
      ),
      body: data.isLoading
          ? const Center(child: CircularProgressIndicator())
          : RefreshIndicator(
              onRefresh: () async {
                ref.invalidate(assignmentsListProvider);
                ref.invalidate(incomeSummaryProvider);
              },
              child: ListView(
                padding: const EdgeInsets.only(bottom: 32),
                children: [
                  HistoryKpiBar(summary: summary),
                  SingleChildScrollView(
                    scrollDirection: Axis.horizontal,
                    padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
                    child: Row(
                      children: [
                        _buildChip('COMPLETED', 'Đã hoàn thành', isDark, t),
                        const SizedBox(width: 8),
                        _buildChip('ALL', 'Tất cả (${historyList.length})', isDark, t),
                        const SizedBox(width: 8),
                        _buildChip('CONFIRMED_PAYMENT', 'Đã nhận tiền', isDark, t),
                        const SizedBox(width: 8),
                        _buildChip('CANCELLED', 'Đã hủy / Vắng', isDark, t),
                      ],
                    ),
                  ),
                  if (filtered.isEmpty)
                    Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 40),
                      child: Column(
                        children: [
                          Icon(Icons.work_history_outlined, size: 52, color: t.textMuted),
                          const SizedBox(height: 12),
                          Text(
                            'Chưa có ca làm nào trong mục này',
                            style: TextStyle(fontWeight: FontWeight.w700, fontSize: 15, color: t.textMain),
                          ),
                          const SizedBox(height: 6),
                          Text(
                            'Các ca làm sau khi bạn Check-out và hoàn tất sẽ được lưu trữ tại đây để bạn theo dõi thu nhập.',
                            textAlign: TextAlign.center,
                            style: TextStyle(fontSize: 12.5, color: t.textMuted),
                          ),
                        ],
                      ),
                    )
                  else
                    Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 16),
                      child: Column(
                        children: filtered
                            .map(
                              (item) => HistoryShiftCard(
                                assignment: item,
                                onTap: () => _openExecutionScreen(item),
                                onConfirmPayment: () => _openPaymentDialog(item),
                                onRateOrganizer: () => _openRateDialog(item),
                              ),
                            )
                            .toList(),
                      ),
                    ),
                ],
              ),
            ),
    );
  }

  Widget _buildChip(String key, String label, bool isDark, CaniTokens t) {
    final selected = _filter == key;
    return ChoiceChip(
      label: Text(
        label,
        style: TextStyle(
          fontSize: 12,
          fontWeight: FontWeight.w700,
          color: selected ? Colors.white : (isDark ? const Color(0xFFCBD5E1) : const Color(0xFF334155)),
        ),
      ),
      selected: selected,
      selectedColor: t.brand,
      backgroundColor: isDark ? const Color(0xFF1E293B) : const Color(0xFFF1F5F9),
      onSelected: (_) => setState(() => _filter = key),
    );
  }
}
