import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:cani/app/cani_tokens.dart';
import '../data/proposal_repository.dart';
import '../models/proposal_models.dart';
import '../widgets/cancel_assignment_dialog.dart';
import '../widgets/payment_confirm_dialog.dart';
import '../widgets/rate_eo_dialog.dart';
import '../widgets/shift_assignment_card.dart';
import '../widgets/shift_navigation_sheet.dart';
import 'attendance_qr_scanner_screen.dart';
import 'shift_detail_execution_screen.dart';

class MyAssignmentsScreen extends ConsumerStatefulWidget {
  const MyAssignmentsScreen({super.key});

  @override
  ConsumerState<MyAssignmentsScreen> createState() => _MyAssignmentsScreenState();
}

class _MyAssignmentsScreenState extends ConsumerState<MyAssignmentsScreen> {
  Future<void> _openPaymentDialog(ShiftAssignmentItem a) async {
    final result = await showDialog<Map<String, String>>(
      context: context,
      builder: (_) => PaymentConfirmDialog(assignment: a),
    );
    if (result != null && mounted) {
      final status = result['status']!;
      final comment = result['comment'] ?? '';
      await _confirmPayment(a, status, comment: comment);
    }
  }

  Future<void> _confirmPayment(ShiftAssignmentItem item, String status, {String comment = ''}) async {
    final t = Theme.of(context).cani;
    final isFull = status == 'RECEIVED_FULL' || status == 'CONFIRMED_FULL';
    final isPartial = status == 'RECEIVED_PARTIAL' || status == 'CONFIRMED_PARTIAL';

    try {
      await ref.read(proposalRepositoryProvider).confirmPayment(item.assignmentId, status, comment);
      ref.invalidate(assignmentsListProvider);

      if (mounted) {
        String msg = 'Đã xác nhận nhận đủ tiền thù lao!';
        Color color = t.success;
        if (isPartial) {
          msg = 'Đã ghi nhận tình trạng nhận một phần thù lao.';
          color = t.warning;
        } else if (!isFull && !isPartial) {
          msg = 'Đã gửi khiếu nại (Dispute). Đội ngũ CSKH CANi sẽ liên hệ hỗ trợ bạn!';
          color = t.danger;
        }

        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(msg),
            backgroundColor: color,
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Lỗi khi gửi xác nhận thanh toán: ${e.toString().replaceAll("Exception: ", "")}'),
            backgroundColor: Theme.of(context).cani.danger,
          ),
        );
      }
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
    final success = await Navigator.push<bool>(
      context,
      MaterialPageRoute(
        builder: (_) => AttendanceQrScannerScreen(
          assignment: a,
          isCheckin: isCheckin,
        ),
      ),
    );
    if (success == true) {
      ref.invalidate(assignmentsListProvider);
    }
  }

  // UC-73: Nhập mã mời ca làm việc (Screen 092)
  Future<void> _openInviteCodeDialog() async {
    final t = Theme.of(context).cani;
    final controller = TextEditingController();
    final result = await showDialog<String>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Nhập Mã Mời Tham Gia Ca', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 18)),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Nhập mã mời do Ban tổ chức sự kiện cung cấp (ví dụ: CANI-8XK2PW) để vào ca làm ngay:',
              style: TextStyle(fontSize: 13, color: Color(0xFF475569)),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: controller,
              textCapitalization: TextCapitalization.characters,
              decoration: const InputDecoration(
                hintText: 'CANI-XXXXXX',
                border: OutlineInputBorder(),
                prefixIcon: Icon(Icons.confirmation_number_outlined),
              ),
            ),
          ],
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Hủy')),
          FilledButton(
            onPressed: () {
              final code = controller.text.trim();
              if (code.isNotEmpty) Navigator.pop(ctx, code);
            },
            style: FilledButton.styleFrom(backgroundColor: t.brand),
            child: const Text('Tham Gia'),
          ),
        ],
      ),
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
        }
      } catch (err) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text('Lỗi: $err'),
              backgroundColor: t.danger,
            ),
          );
        }
      }
    }
  }

  void _handleHeaderQrScan(List<ShiftAssignmentItem> assignments) {
    if (assignments.isEmpty) {
      _openInviteCodeDialog();
      return;
    }
    final active = assignments.firstWhere(
      (a) => a.status == 'CHECKED_IN' || a.status == 'CONFIRMED',
      orElse: () => assignments.first,
    );
    final isCheckedIn = active.status == 'CHECKED_IN';
    _openQrScanner(active, !isCheckedIn);
  }

  Future<void> _openExecutionScreen(ShiftAssignmentItem a) async {
    final updated = await Navigator.push<bool>(
      context,
      MaterialPageRoute(
        builder: (_) => ShiftDetailExecutionScreen(assignment: a),
      ),
    );
    if (updated == true) {
      ref.invalidate(assignmentsListProvider);
    }
  }

  Future<void> _openCancelDialog(ShiftAssignmentItem a) async {
    final cancelled = await showDialog<bool>(
      context: context,
      builder: (_) => CancelAssignmentDialog(assignment: a),
    );
    if (cancelled == true) {
      ref.invalidate(assignmentsListProvider);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: const Text('Đã hủy nhận ca làm việc. Hệ thống đang tìm người thay thế.'),
            backgroundColor: Theme.of(context).cani.danger,
          ),
        );
      }
    }
  }

  Future<void> _openRateDialog(ShiftAssignmentItem a) async {
    final rated = await showDialog<bool>(
      context: context,
      builder: (_) => RateEoDialog(assignment: a),
    );
    if (rated == true && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: const Text('Cảm ơn bạn đã gửi đánh giá cho Ban tổ chức!'),
          backgroundColor: Theme.of(context).cani.success,
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).cani;

    final data = ref.watch(assignmentsListProvider);
    final isLoading = data.isLoading;
    final list = data.valueOrNull ?? const <ShiftAssignmentItem>[];
    final sorted = List<ShiftAssignmentItem>.from(list)
      ..sort((a, b) => a.startTime.compareTo(b.startTime));

    return Scaffold(
      backgroundColor: Theme.of(context).scaffoldBackgroundColor,
      body: SafeArea(
        child: Column(
          children: [
            // Header: tiêu đề + nút quét QR / nhập mã mời
            Container(
              padding: const EdgeInsets.fromLTRB(20, 14, 20, 14),
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
                        onTap: () => _handleHeaderQrScan(sorted),
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
            ),

            // Danh sách ca đã nhận (sắp theo ca sớm nhất)
            Expanded(
              child: isLoading
                  ? const Center(child: CircularProgressIndicator())
                  : RefreshIndicator(
                      onRefresh: () async => ref.invalidate(assignmentsListProvider),
                      child: _buildAssignmentsFeed(sorted, t),
                    ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildAssignmentsFeed(List<ShiftAssignmentItem> assignments, CaniTokens t) {
    if (assignments.isEmpty) {
      return ListView(
        padding: const EdgeInsets.all(24),
        children: [
          const SizedBox(height: 40),
          Center(
            child: Icon(Icons.assignment_outlined, size: 56, color: t.textMuted),
          ),
          const SizedBox(height: 12),
          Center(
            child: Text(
              'Bạn chưa có ca làm việc nào đã nhận',
              style: TextStyle(
                fontWeight: FontWeight.w800,
                fontSize: 15,
                color: t.textMain,
              ),
            ),
          ),
          const SizedBox(height: 6),
          Center(
            child: Text(
              'Khi bạn đồng ý một lời mời việc, ca làm sẽ xuất hiện tại đây để bạn quét QR điểm danh.',
              textAlign: TextAlign.center,
              style: TextStyle(
                color: t.textMuted,
                fontSize: 12.5,
              ),
            ),
          ),
        ],
      );
    }

    return ListView.builder(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 80),
      itemCount: assignments.length,
      itemBuilder: (context, index) {
        final item = assignments[index];
        final isCheckedIn = item.status == 'CHECKED_IN' || (item.checkinTime != null && item.checkoutTime == null);

        return ShiftAssignmentCard(
          assignment: item,
          onTap: () => _openExecutionScreen(item),
          onNavigate: () => _openNavigationSheet(item),
          onQrScan: () => _openQrScanner(item, !isCheckedIn),
          onConfirmPayment: () => _openPaymentDialog(item),
          onOpenChecklist: () => _openExecutionScreen(item),
          onCancelAssignment: () => _openCancelDialog(item),
          onRateOrganizer: () => _openRateDialog(item),
        );
      },
    );
  }
}
