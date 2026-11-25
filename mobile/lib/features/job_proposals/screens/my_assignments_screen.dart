import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/app/cani_tokens.dart';
import 'package:cani/core/utils/app_error_formatter.dart';
import '../data/proposal_repository.dart';
import '../models/proposal_models.dart';
import '../widgets/cancel_assignment_dialog.dart';
import '../widgets/history_kpi_bar.dart';
import '../widgets/history_shift_card.dart';
import '../widgets/invite_code_dialog.dart';
import '../widgets/shift_assignment_card.dart';
import '../widgets/shift_navigation_sheet.dart';
import '../widgets/shift_workflow_stepper.dart';
import 'attendance_qr_scanner_screen.dart';
import 'invite_qr_scanner_screen.dart';
import 'rate_eo_screen.dart';
import 'shift_detail_execution_screen.dart';
import 'shift_payment_screen.dart';

class MyAssignmentsScreen extends ConsumerStatefulWidget {
  const MyAssignmentsScreen({super.key});

  @override
  ConsumerState<MyAssignmentsScreen> createState() => _MyAssignmentsScreenState();
}

class _MyAssignmentsScreenState extends ConsumerState<MyAssignmentsScreen> {
  String _historyFilter = 'ALL'; // ALL, COMPLETED, PENDING_PAYMENT, CONFIRMED_PAYMENT, CANCELLED

  Future<void> _openPaymentScreen(ShiftAssignmentItem a) async {
    final updated = await Navigator.push<bool>(
      context,
      MaterialPageRoute(builder: (_) => ShiftPaymentScreen(assignment: a)),
    );
    if (updated == true && mounted) {
      ref.invalidate(assignmentsListProvider);
      ref.invalidate(incomeSummaryProvider);
    }
  }

  void _openNavigationSheet(ShiftAssignmentItem a) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => ShiftNavigationSheet(assignment: a),
    );
  }

  Future<void> _openQrScanner(ShiftAssignmentItem a, bool isCheckin) async {
    if (isCheckin) {
      final gate = a.arrivalTime ?? a.startTime;
      if (DateTime.now().isBefore(gate)) {
        final gateStr =
            '${gate.hour.toString().padLeft(2, '0')}:${gate.minute.toString().padLeft(2, '0')} ${gate.day.toString().padLeft(2, '0')}/${gate.month.toString().padLeft(2, '0')}';
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Chưa đến giờ điểm danh ca làm (Mở lúc $gateStr). Vui lòng quay lại đúng giờ!'),
            backgroundColor: Theme.of(context).cani.warning,
          ),
        );
        return;
      }
    }
    final success = await Navigator.push<bool>(
      context,
      MaterialPageRoute(
        builder: (_) => AttendanceQrScannerScreen(assignment: a, isCheckin: isCheckin),
      ),
    );
    if (success == true && mounted) {
      ref.invalidate(assignmentsListProvider);
      ref.invalidate(incomeSummaryProvider);
    }
  }

  Future<void> _openInviteCodeDialog() async {
    final t = Theme.of(context).cani;
    final result = await InviteCodeDialog.show(
      context,
      title: 'Nhập Mã Mời Tham Gia Ca',
      titleFontSize: 18,
    );

    if (result != null && result.isNotEmpty && mounted) {
      try {
        await ref.read(proposalRepositoryProvider).joinViaInviteCode(result);
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: const Text('Tham gia ca làm việc thành công!'),
              backgroundColor: t.success,
            ),
          );
          ref.invalidate(assignmentsListProvider);
          ref.invalidate(incomeSummaryProvider);
          ref.read(assignmentsSubTabProvider.notifier).state = 0;
        }
      } catch (err) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(AppErrorFormatter.format(err, fallback: 'Không thể tham gia ca làm.')),
              backgroundColor: t.danger,
            ),
          );
        }
      }
    }
  }

  Future<void> _openInviteQrScanner() async {
    final t = Theme.of(context).cani;
    final joined = await Navigator.push<bool>(
      context,
      MaterialPageRoute(
        builder: (_) => InviteQrScannerScreen(
          onManualEntryRequested: () {
            if (mounted) {
              _openInviteCodeDialog();
            }
          },
        ),
      ),
    );
    if (joined == true && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: const Text('Tham gia ca làm việc thành công!'),
          backgroundColor: t.success,
        ),
      );
      ref.invalidate(assignmentsListProvider);
      ref.invalidate(incomeSummaryProvider);
      ref.read(assignmentsSubTabProvider.notifier).state = 0;
    }
  }

  void _handleHeaderQrScan(List<ShiftAssignmentItem> activeAssignments) {
    final now = DateTime.now();
    final scannable = activeAssignments.where((a) {
      if (a.status == 'CHECKED_IN') return true;
      if (a.status == 'CONFIRMED') return !now.isAfter(a.endTime);
      return false;
    }).toList();
    if (scannable.isEmpty) {
      _openInviteQrScanner();
      return;
    }
    final active = scannable.first;
    final isCheckedIn = active.status == 'CHECKED_IN';
    if (isCheckedIn) {
      if (!active.eoConfirmed) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: const Text('Cần hoàn tất đầu việc & chờ BTC Duyệt công (Bước 3) trước khi Check-out.'),
            backgroundColor: Theme.of(context).cani.warning,
          ),
        );
        _openExecutionScreen(active);
        return;
      }
      if (!isAssignmentPaymentConfirmed(active)) {
        _openPaymentScreen(active);
        return;
      }
    }
    _openQrScanner(active, !isCheckedIn);
  }

  Future<void> _openExecutionScreen(ShiftAssignmentItem a) async {
    final updated = await Navigator.push<bool>(
      context,
      MaterialPageRoute(builder: (_) => ShiftDetailExecutionScreen(assignment: a)),
    );
    if (updated == true && mounted) {
      ref.invalidate(assignmentsListProvider);
      ref.invalidate(incomeSummaryProvider);
    }
  }

  Future<void> _openCancelDialog(ShiftAssignmentItem a) async {
    final cancelled = await showDialog<bool>(
      context: context,
      builder: (_) => CancelAssignmentDialog(assignment: a),
    );
    if (cancelled == true && mounted) {
      ref.invalidate(assignmentsListProvider);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: const Text('Đã hủy nhận ca làm việc. Hệ thống đang tìm người thay thế.'),
          backgroundColor: Theme.of(context).cani.danger,
        ),
      );
    }
  }

  Future<void> _openRateScreen(ShiftAssignmentItem a) async {
    final rated = await Navigator.push<bool>(
      context,
      MaterialPageRoute(builder: (_) => RateEoScreen(assignment: a)),
    );
    if (rated == true && mounted) {
      ref.invalidate(assignmentsListProvider);
      ref.invalidate(incomeSummaryProvider);
    }
  }

  List<ShiftAssignmentItem> _applyHistoryFilter(List<ShiftAssignmentItem> history) {
    switch (_historyFilter) {
      case 'COMPLETED':
        return history.where((a) => a.status == 'COMPLETED').toList();
      case 'PENDING_PAYMENT':
        return history
            .where((a) => a.paymentStatus == 'DECLARED_PENDING_WORKER' || a.paymentStatus == 'NOT_DECLARED')
            .toList();
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
    final currentSubTab = ref.watch(assignmentsSubTabProvider);

    final isLoading = data.isLoading;
    final allList = data.valueOrNull ?? const <ShiftAssignmentItem>[];

    // 1. Phân loại ca làm việc: Đang hoạt động vs Lịch sử quá khứ
    final activeList = allList
        .where((a) =>
            a.status != 'COMPLETED' &&
            a.status != 'CANCELLED' &&
            a.status != 'NO_SHOW' &&
            a.checkoutTime == null)
        .toList()
      ..sort((a, b) => a.startTime.compareTo(b.startTime));

    final historyList = allList
        .where((a) =>
            a.status == 'COMPLETED' ||
            a.status == 'CANCELLED' ||
            a.status == 'NO_SHOW' ||
            a.checkoutTime != null)
        .toList()
      ..sort((a, b) => b.startTime.compareTo(a.startTime)); // Mới nhất lên đầu

    final filteredHistory = _applyHistoryFilter(historyList);

    // Tính toán summary fallback nếu API summary đang load
    final incomeSummary = summaryAsync.valueOrNull ??
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
      backgroundColor: Theme.of(context).scaffoldBackgroundColor,
      body: SafeArea(
        child: Column(
          children: [
            // Header Top Bar
            _buildHeader(t, activeList),

            // Sub-tab Navigation (Đang làm & Sắp tới vs Lịch sử ca làm)
            _buildSubTabs(t, isDark, currentSubTab, activeList.length, historyList.length),

            // Content Feed theo Sub-tab
            Expanded(
              child: isLoading
                  ? const Center(child: CircularProgressIndicator())
                  : (data.hasError && allList.isEmpty)
                      ? Center(
                          child: Padding(
                            padding: const EdgeInsets.all(24),
                            child: Column(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                Icon(Icons.cloud_off_rounded, size: 52, color: t.danger),
                                const SizedBox(height: 14),
                                Text(
                                  'Không thể tải danh sách ca làm',
                                  style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: t.textMain),
                                ),
                                const SizedBox(height: 6),
                                Text(
                                  'Vui lòng kéo xuống để làm mới hoặc bấm nút thử lại bên dưới.',
                                  style: TextStyle(fontSize: 13, color: t.textMuted),
                                  textAlign: TextAlign.center,
                                ),
                                if (data.error != null) ...[
                                  const SizedBox(height: 10),
                                  Container(
                                    padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                                    decoration: BoxDecoration(
                                      color: isDark ? const Color(0xFF1E293B) : const Color(0xFFFEE2E2),
                                      borderRadius: BorderRadius.circular(10),
                                      border: Border.all(color: t.danger.withValues(alpha: 0.3)),
                                    ),
                                    child: Text(
                                      '${data.error}',
                                      style: TextStyle(fontSize: 11, color: isDark ? const Color(0xFFFCA5A5) : const Color(0xFFDC2626)),
                                      textAlign: TextAlign.center,
                                      maxLines: 4,
                                      overflow: TextOverflow.ellipsis,
                                    ),
                                  ),
                                ],
                                const SizedBox(height: 16),
                                FilledButton.icon(
                                  onPressed: () {
                                    ref.invalidate(assignmentsListProvider);
                                    ref.invalidate(incomeSummaryProvider);
                                  },
                                  icon: const Icon(Icons.refresh_rounded, size: 16),
                                  label: const Text('Thử lại ngay'),
                                  style: FilledButton.styleFrom(backgroundColor: t.brand),
                                ),
                              ],
                            ),
                          ),
                        )
                      : RefreshIndicator(
                          onRefresh: () async {
                            await Future.wait([
                              () async {
                                try {
                                  final _ = await ref.refresh(assignmentsListProvider.future);
                                } catch (_) {}
                              }(),
                              () async {
                                try {
                                  final _ = await ref.refresh(incomeSummaryProvider.future);
                                } catch (_) {}
                              }(),
                            ]);
                          },
                          child: currentSubTab == 0
                              ? _buildActiveFeed(activeList, t)
                              : _buildHistoryFeed(
                                  historyList,
                                  filteredHistory,
                                  incomeSummary,
                                  t,
                                  isDark,
                                  activeCount: activeList.length,
                                ),
                        ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildHeader(CaniTokens t, List<ShiftAssignmentItem> activeList) {
    return Container(
      padding: const EdgeInsets.fromLTRB(20, 14, 20, 10),
      color: t.surface,
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(
            'Ca Làm',
            style: TextStyle(
              fontFamily: 'Be Vietnam Pro',
              fontWeight: FontWeight.w800,
              fontSize: 20,
              color: t.textMain,
              letterSpacing: -0.2,
            ),
          ),
          Row(
            children: [
              IconButton(
                icon: Icon(Icons.confirmation_number_outlined, color: t.brand, size: 22),
                tooltip: 'Nhập mã mời ca làm',
                onPressed: _openInviteCodeDialog,
              ),
              const SizedBox(width: 4),
              InkWell(
                onTap: () => _handleHeaderQrScan(activeList),
                borderRadius: BorderRadius.circular(999),
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 7),
                  decoration: BoxDecoration(
                    color: t.brand,
                    borderRadius: BorderRadius.circular(999),
                    boxShadow: [
                      BoxShadow(
                        color: t.brand.withValues(alpha: 0.28),
                        blurRadius: 6,
                        offset: const Offset(0, 2),
                      ),
                    ],
                  ),
                  child: const Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(Icons.qr_code_scanner_rounded, size: 15, color: Colors.white),
                      SizedBox(width: 5),
                      Text(
                        'Quét QR',
                        style: TextStyle(
                          fontSize: 12,
                          fontWeight: FontWeight.w700,
                          color: Colors.white,
                          letterSpacing: -0.2,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildSubTabs(CaniTokens t, bool isDark, int currentSubTab, int activeCount, int historyCount) {
    return Container(
      width: double.infinity,
      color: t.surface,
      padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
      child: Container(
        padding: const EdgeInsets.all(4),
        decoration: BoxDecoration(
          color: isDark ? const Color(0xFF1E293B) : const Color(0xFFF1F5F9),
          borderRadius: BorderRadius.circular(12),
        ),
        child: Row(
          children: [
            Expanded(
              child: _TabButton(
                title: 'Sắp tới & Đang làm',
                badgeCount: activeCount,
                isSelected: currentSubTab == 0,
                onTap: () => ref.read(assignmentsSubTabProvider.notifier).state = 0,
              ),
            ),
            const SizedBox(width: 4),
            Expanded(
              child: _TabButton(
                title: 'Lịch sử ca làm',
                badgeCount: historyCount,
                isSelected: currentSubTab == 1,
                onTap: () => ref.read(assignmentsSubTabProvider.notifier).state = 1,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildActiveFeed(List<ShiftAssignmentItem> activeList, CaniTokens t) {
    if (activeList.isEmpty) {
      return ListView(
        padding: const EdgeInsets.all(24),
        children: [
          const SizedBox(height: 50),
          Center(child: Icon(Icons.assignment_outlined, size: 56, color: t.textMuted)),
          const SizedBox(height: 12),
          Center(
            child: Text(
              'Không có ca làm việc sắp tới',
              style: TextStyle(fontWeight: FontWeight.w800, fontSize: 15, color: t.textMain),
            ),
          ),
          const SizedBox(height: 6),
          Center(
            child: Text(
              'Khi bạn nhận một công việc mới hoặc quét mã mời, ca làm sẽ xuất hiện tại đây để bạn chuẩn bị và điểm danh.',
              textAlign: TextAlign.center,
              style: TextStyle(color: t.textMuted, fontSize: 12.5),
            ),
          ),
        ],
      );
    }

    return ListView.builder(
      padding: const EdgeInsets.fromLTRB(16, 14, 16, 80),
      itemCount: activeList.length,
      itemBuilder: (context, index) {
        final item = activeList[index];
        final isCheckedIn = item.status == 'CHECKED_IN' || (item.checkinTime != null && item.checkoutTime == null);

        return ShiftAssignmentCard(
          assignment: item,
          onTap: () => _openExecutionScreen(item),
          onNavigate: () => _openNavigationSheet(item),
          onQrScan: () => _openQrScanner(item, !isCheckedIn),
          onConfirmPayment: () => _openPaymentScreen(item),
          onOpenChecklist: () => _openExecutionScreen(item),
          onCancelAssignment: () => _openCancelDialog(item),
          onRateOrganizer: item.hasWorkerReviewed == true ? null : () => _openRateScreen(item),
        );
      },
    );
  }

  Widget _buildHistoryFeed(
    List<ShiftAssignmentItem> rawHistory,
    List<ShiftAssignmentItem> filteredList,
    WorkerIncomeSummary summary,
    CaniTokens t,
    bool isDark, {
    int activeCount = 0,
  }) {
    if (rawHistory.isEmpty) {
      return ListView(
        padding: const EdgeInsets.only(bottom: 80),
        children: [
          HistoryKpiBar(summary: summary),
          _buildZeroHistoryEmptyState(t, isDark, activeCount: activeCount),
        ],
      );
    }

    return ListView(
      padding: const EdgeInsets.only(bottom: 80),
      children: [
        HistoryKpiBar(summary: summary),
        _buildFilterChips(rawHistory.length, isDark),
        if (filteredList.isEmpty) ...[
          _buildEmptyFilterState(rawHistory.length, t),
        ] else ...[
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Column(
              children: filteredList.map((item) {
                return HistoryShiftCard(
                  assignment: item,
                  onTap: () => _openExecutionScreen(item),
                  onConfirmPayment: () => _openPaymentScreen(item),
                  onRateOrganizer: () => _openRateScreen(item),
                );
              }).toList(),
            ),
          ),
        ],
      ],
    );
  }

  Widget _buildZeroHistoryEmptyState(CaniTokens t, bool isDark, {int activeCount = 0}) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 32, vertical: 36),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 72,
            height: 72,
            decoration: BoxDecoration(
              color: t.brand.withValues(alpha: 0.08),
              shape: BoxShape.circle,
            ),
            child: Icon(Icons.work_history_outlined, size: 36, color: t.brand),
          ),
          const SizedBox(height: 18),
          Text(
            'Chưa có lịch sử ca làm',
            textAlign: TextAlign.center,
            style: TextStyle(
              fontWeight: FontWeight.w800,
              fontSize: 16,
              color: t.textMain,
              letterSpacing: -0.2,
            ),
          ),
          const SizedBox(height: 8),
          Text(
            'Sau khi bạn nhận ca, hoàn thành công việc và điểm danh ra về, toàn bộ lịch sử điểm danh, thù lao và đánh giá từ Ban tổ chức sẽ được lưu trữ tại đây.',
            textAlign: TextAlign.center,
            style: TextStyle(
              color: t.textMuted,
              fontSize: 13,
              height: 1.45,
            ),
          ),
          const SizedBox(height: 22),
          if (activeCount > 0)
            FilledButton.icon(
              onPressed: () {
                ref.read(assignmentsSubTabProvider.notifier).state = 0;
              },
              icon: const Icon(Icons.arrow_forward_rounded, size: 16),
              label: Text('Bạn có $activeCount ca sắp tới • Xem ngay', style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w700)),
              style: FilledButton.styleFrom(
                backgroundColor: t.brand,
                padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 11),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(99)),
              ),
            )
          else
            OutlinedButton.icon(
              onPressed: () {
                ref.read(assignmentsSubTabProvider.notifier).state = 0;
              },
              icon: const Icon(Icons.calendar_month_outlined, size: 16),
              label: const Text('Xem ca sắp tới & đang làm', style: TextStyle(fontSize: 13, fontWeight: FontWeight.w700)),
              style: OutlinedButton.styleFrom(
                foregroundColor: t.brand,
                side: BorderSide(color: t.brand.withValues(alpha: 0.4)),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(99)),
                padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 10),
              ),
            ),
        ],
      ),
    );
  }

  Widget _buildEmptyFilterState(int totalCount, CaniTokens t) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 32, vertical: 36),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(Icons.filter_list_off_rounded, size: 44, color: t.textMuted),
          const SizedBox(height: 14),
          Text(
            'Không có ca làm nào trong mục này',
            textAlign: TextAlign.center,
            style: TextStyle(
              fontWeight: FontWeight.w700,
              fontSize: 15,
              color: t.textMain,
            ),
          ),
          const SizedBox(height: 6),
          Text(
            'Thử chọn bộ lọc khác hoặc quay lại danh sách tất cả các ca làm.',
            textAlign: TextAlign.center,
            style: TextStyle(
              color: t.textMuted,
              fontSize: 12.5,
              height: 1.4,
            ),
          ),
          const SizedBox(height: 16),
          TextButton.icon(
            onPressed: () => setState(() => _historyFilter = 'ALL'),
            icon: const Icon(Icons.refresh_rounded, size: 16),
            label: Text('Quay lại tất cả ($totalCount ca)', style: const TextStyle(fontWeight: FontWeight.w700)),
          ),
        ],
      ),
    );
  }

  Widget _buildFilterChips(int totalHistoryCount, bool isDark) {
    final chips = [
      {'key': 'ALL', 'label': 'Tất cả ($totalHistoryCount)'},
      {'key': 'COMPLETED', 'label': 'Đã hoàn thành'},
      {'key': 'PENDING_PAYMENT', 'label': 'Chờ quyết toán'},
      {'key': 'CONFIRMED_PAYMENT', 'label': 'Đã nhận tiền'},
      {'key': 'CANCELLED', 'label': 'Hủy / Vắng mặt'},
    ];

    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
      child: Row(
        children: chips.map((chip) {
          final isSelected = _historyFilter == chip['key'];
          return Padding(
            padding: const EdgeInsets.only(right: 8),
            child: ChoiceChip(
              showCheckmark: false,
              label: Text(chip['label']!),
              selected: isSelected,
              onSelected: (selected) {
                if (selected) setState(() => _historyFilter = chip['key']!);
              },
              labelStyle: TextStyle(
                fontSize: 12,
                fontWeight: isSelected ? FontWeight.w700 : FontWeight.w500,
                color: isSelected ? Colors.white : (isDark ? const Color(0xFFCBD5E1) : const Color(0xFF475569)),
              ),
              selectedColor: Theme.of(context).cani.brand,
              backgroundColor: isDark ? const Color(0xFF1E293B) : const Color(0xFFF1F5F9),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
              side: BorderSide.none,
              padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
            ),
          );
        }).toList(),
      ),
    );
  }
}

class _TabButton extends StatelessWidget {
  final String title;
  final int badgeCount;
  final bool isSelected;
  final VoidCallback onTap;

  const _TabButton({
    required this.title,
    required this.badgeCount,
    required this.isSelected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final cani = Theme.of(context).cani;
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(10),
        child: Container(
          padding: const EdgeInsets.symmetric(vertical: 8),
          decoration: BoxDecoration(
            color: isSelected ? (isDark ? const Color(0xFF334155) : Colors.white) : Colors.transparent,
            borderRadius: BorderRadius.circular(10),
            boxShadow: isSelected
                ? [
                    BoxShadow(
                      color: Colors.black.withValues(alpha: 0.05),
                      blurRadius: 4,
                      offset: const Offset(0, 1),
                    )
                  ]
                : null,
          ),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Text(
                title,
                style: TextStyle(
                  fontSize: 12.5,
                  fontWeight: isSelected ? FontWeight.w700 : FontWeight.w500,
                  color: isSelected
                      ? (isDark ? Colors.white : const Color(0xFF0F172A))
                      : (isDark ? const Color(0xFF94A3B8) : const Color(0xFF64748B)),
                ),
              ),
              if (badgeCount > 0) ...[
                const SizedBox(width: 5),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 5.5, vertical: 1.5),
                  decoration: BoxDecoration(
                    color: isSelected ? cani.brand : (isDark ? const Color(0xFF475569) : const Color(0xFFCBD5E1)),
                    borderRadius: BorderRadius.circular(99),
                  ),
                  child: Text(
                    '$badgeCount',
                    style: const TextStyle(
                      fontSize: 10,
                      fontWeight: FontWeight.w700,
                      color: Colors.white,
                    ),
                  ),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}
